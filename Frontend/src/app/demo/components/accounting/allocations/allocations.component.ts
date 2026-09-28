import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfirmationService, MessageService, SortEvent } from 'primeng/api';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { AccountingApiService, AttacheAllocation, FeeType, FiscalYear } from '../accounting-api.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { MoheQuarterService } from '../../../service/mohe-quarter.service';
import { TranslationService } from '../../../service/translation.service';
import { CodeListService } from '../../../service/code-list.service';
import { QuarterOption, toIsoDate, toQuarterOptions, withFiscalYears } from '../accounting-shared';
import { sortRows } from '../../../service/table-sort.util';

/**
 * Attaché allocations: the quarterly transfers of budget to each cultural
 * attaché, and the two-step release of each one.
 *
 *     DRAFT --approve--> APPROVED --transfer--> TRANSFERRED
 *
 * Approving is a decision; transferring is cash leaving, and only the second
 * grows the attaché's advance. The officer who drafts an allocation cannot be
 * the one who approves it — the server refuses, whatever the buttons show.
 *
 * Country-scoped like every accounting page: an officer sees their own attaché's
 * allocations, the ministry sees every attaché's.
 */
@Component({
    selector: 'app-allocations',
    templateUrl: './allocations.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class AllocationsComponent implements OnInit {
    /** FE-04: currentLanguage$ never completes; the subscription ends with the component. */
    private readonly destroyRef = inject(DestroyRef);

    loading = false;
    saving = false;

    quarters: QuarterOption[] = [];
    selectedQuarterKey: number | null = null;

    countryOptions: LookupItem[] = [];
    currencyOptions: LookupItem[] = [];
    feeTypes: FeeType[] = [];

    rows: AttacheAllocation[] = [];

    dialogVisible = false;
    draft: Partial<AttacheAllocation> = {};

    /**
     * Set when Save is pressed, so the dialog can say which field is missing.
     * Attache, currency and amount were checked together and reported by one
     * toast that named none of them.
     */
    submitted = false;

    viewVisible = false;
    viewing: AttacheAllocation | null = null;

    transferVisible = false;
    transferTarget: AttacheAllocation | null = null;
    transferDate: Date | null = null;
    transferReference = '';
    transferSubmitted = false;

    /** Allocation types, from the allocation-type list. */
    types: Array<{ label: string; value: string }> = [];

    constructor(private api: AccountingApiService,
                private lookupService: LookupService,
                private quarterService: MoheQuarterService,
                private messages: MessageService,
                private confirmation: ConfirmationService,
                private translate: TranslationService,
                private codeLists: CodeListService) {}

    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translate.getCurrentLanguage();
    }

    ngOnInit(): void {
        this.types = this.codeLists.options('allocation-type');
        this.translate.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => { this.types = this.codeLists.options('allocation-type'); });
        this.loading = true;
        forkJoin({
            // The attaché is a study country; the home-country list reuses its ids for other countries (live QA H6).
            countries: this.lookupService.getStudyCountriesIn().pipe(catchError(() => of([] as LookupItem[]))),
            currencies: this.lookupService.getStudyCurrencies().pipe(catchError(() => of([] as LookupItem[]))),
            feeTypes: this.api.feeTypes().pipe(catchError(() => of([] as FeeType[]))),
            quarters: this.quarterService.getAll().pipe(catchError(() => of([]))),
            years: this.api.fiscalYears().pipe(catchError(() => of([] as FiscalYear[])))
        }).subscribe(({ countries, currencies, feeTypes, quarters, years }) => {
            this.countryOptions = countries;
            this.currencyOptions = currencies;
            this.feeTypes = feeTypes;
            const picked = withFiscalYears(toQuarterOptions(quarters, this.isArabic()), years,
                this.translate.translate('ACCOUNTING.NO_FISCAL_YEAR'));
            this.quarters = picked.options;
            this.selectedQuarterKey = picked.selected;
            this.loading = false;
            this.reload();
        });
    }

    reload(): void {
        if (this.selectedQuarterKey == null) { this.rows = []; return; }
        this.loading = true;
        this.api.allocations(this.selectedQuarterKey)
            .pipe(finalize(() => this.loading = false))
            .subscribe({
                next: rows => this.rows = rows,
                error: () => this.toastError('ACCOUNTING.LOAD_FAILED')
            });
    }

    // ------------------------------------------------------------- display

    /** Sorts by the names and labels the cells print, not by the ids behind them. */
    sortTable(event: SortEvent): void {
        sortRows(event, {
            attache: row => this.countryName(row.studyCountryInId),
            allocationType: row => this.translate.translate('ACCOUNTING.TYPE_' + row.allocationType),
            feeType: row => this.feeTypeName(row.feeTypeId),
            currency: row => this.currencyName(row.currencyId),
            status: row => this.codeLists.label('allocation-status', row.status, this.currentLanguage)
        }, this.currentLanguage);
    }

    countryName(id?: number | null): string {
        if (id == null) return '—';
        return this.countryOptions.find(c => c.id === id)?.name ?? String(id);
    }

    currencyName(id?: number | null): string {
        if (id == null) return '—';
        return this.currencyOptions.find(c => c.id === id)?.name ?? String(id);
    }

    /** Null means the allocation covers every stream, not that it has none. */
    feeTypeName(id?: number | null): string {
        if (id == null) return this.translate.translate('ACCOUNTING.ALL_STREAMS');
        const f = this.feeTypes.find(t => t.id === id);
        if (!f) return String(id);
        return this.isArabic() ? f.nameAr : f.nameEn;
    }

    get total(): number {
        return this.rows.reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
    }

    private isArabic(): boolean {
        return (this.translate.getCurrentLanguage() || 'en').startsWith('ar');
    }

    // ------------------------------------------------------------- create

    openNew(): void {
        this.submitted = false;
        this.draft = {
            allocationType: 'REGULAR',
            feeTypeId: null,
            // One attaché in scope means no choice to make; the server would stamp
            // it anyway, but showing it filled in says what is about to happen.
            studyCountryInId: this.countryOptions.length === 1 ? this.countryOptions[0].id : null,
            currencyId: this.currencyOptions.length === 1 ? this.currencyOptions[0].id : null as unknown as number,
            notes: ''
        };
        this.dialogVisible = true;
    }

    /**
     * The whole allocation: the maker and the checker, the transfer advice the
     * cash moved on, and the note - none of which the row has room to name.
     */
    openView(row: AttacheAllocation): void {
        this.viewing = row;
        this.viewVisible = true;
    }

    save(): void {
        this.submitted = true;
        if (this.selectedQuarterKey == null) { return; }
        if (!this.draft.amount || this.draft.amount <= 0
            || this.draft.studyCountryInId == null || this.draft.currencyId == null) {
            this.toastError('ACCOUNTING.AMOUNT_REQUIRED');
            return;
        }
        this.saving = true;
        this.api.createAllocation({ ...this.draft, moheQuarterKey: this.selectedQuarterKey })
            .pipe(finalize(() => this.saving = false))
            .subscribe({
                next: () => {
                    this.dialogVisible = false;
                    this.toastSuccess('ACCOUNTING.SAVED');
                    this.reload();
                },
                error: err => this.toastServerError(err)
            });
    }

    // ------------------------------------------------------------ approve

    /** Approval releases a transfer, so the dialog names the amount, attaché and quarter. */
    confirmApprove(row: AttacheAllocation): void {
        const quarter = this.quarters.find(q => q.key === row.moheQuarterKey)?.label ?? String(row.moheQuarterKey ?? '');
        const message = this.translate.translate('ACCOUNTING.CONFIRM_APPROVE_ALLOCATION')
            .replace('{amount}', this.formatAmount(row.amount))
            .replace('{currency}', this.currencyName(row.currencyId))
            .replace('{attache}', this.countryName(row.studyCountryInId))
            .replace('{quarter}', quarter);
        this.confirmation.confirm({
            header: this.translate.translate('ACCOUNTING.APPROVE'),
            message,
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: this.translate.translate('COMMON.YES'),
            rejectLabel: this.translate.translate('COMMON.NO'),
            accept: () => this.approve(row)
        });
    }

    private formatAmount(amount: number | string | null | undefined): string {
        const n = Number(amount) || 0;
        return n.toLocaleString(this.isArabic() ? 'ar-EG' : 'en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    approve(row: AttacheAllocation): void {
        this.api.approveAllocation(row.id).subscribe({
            next: () => { this.toastSuccess('ACCOUNTING.ALLOCATION_APPROVED'); this.reload(); },
            error: err => this.toastServerError(err)
        });
    }

    // ----------------------------------------------------------- transfer

    openTransfer(row: AttacheAllocation): void {
        this.transferTarget = row;
        this.transferDate = new Date();
        this.transferReference = '';
        this.transferSubmitted = false;
        this.transferVisible = true;
    }

    confirmTransfer(): void {
        if (!this.transferTarget) { return; }
        // The date is what the transfer is dated by, and the server has no
        // default for it; without it the advice is filed against nothing.
        this.transferSubmitted = true;
        if (!this.transferDate) { return; }
        this.saving = true;
        this.api.transferAllocation(this.transferTarget.id, toIsoDate(this.transferDate), this.transferReference || null)
            .pipe(finalize(() => this.saving = false))
            .subscribe({
                next: () => {
                    this.transferVisible = false;
                    this.toastSuccess('ACCOUNTING.ALLOCATION_TRANSFERRED');
                    this.reload();
                },
                error: err => this.toastServerError(err)
            });
    }

    // ------------------------------------------------------------- toasts

    private toastSuccess(key: string): void {
        this.messages.add({ severity: 'success', summary: this.translate.translate(key) });
    }

    private toastError(key: string): void {
        this.messages.add({ severity: 'error', summary: this.translate.translate(key) });
    }

    /** The server's message is shown verbatim: it says *why* (maker = checker, wrong status). */
    private toastServerError(err: { error?: { message?: string }; message?: string }): void {
        this.messages.add({
            severity: 'error',
            summary: this.translate.translate('ACCOUNTING.SAVE_FAILED'),
            detail: err?.error?.message || err?.message || '',
            life: 8000
        });
    }
}
