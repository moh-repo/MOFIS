import { Component, OnInit } from '@angular/core';
import { ConfirmationService, MessageService, SortEvent } from 'primeng/api';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { AccountingApiService, BudgetAppropriation, FeeType, FiscalYear } from '../accounting-api.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { sortRows } from '../../../service/table-sort.util';
import { TranslationService } from '../../../service/translation.service';
import { CodeListService } from '../../../service/code-list.service';

/**
 * Budget appropriations: the annual budget from MOF, by stream, next to what
 * has been allocated against it.
 *
 * The head of the fund chain. Each row is one (year, stream, currency, attaché);
 * an empty attaché is the ministry-wide figure, which every reader sees. An
 * approved figure is never overwritten — editing it marks it revised and opens
 * a new draft that goes through approval again, so the approved number stays
 * on record beside its replacement.
 */
@Component({
    selector: 'app-budget-appropriations',
    templateUrl: './budget-appropriations.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class BudgetAppropriationsComponent implements OnInit {

    loading = false;
    saving = false;

    fiscalYears: FiscalYear[] = [];
    selectedFiscalYearId: number | null = null;

    /** Opening a fiscal year (FISCAL_YEAR.ADD; W4-01). */
    yearDialogVisible = false;
    newYearNo: number | null = null;
    openingYear = false;

    feeTypes: FeeType[] = [];
    currencyOptions: LookupItem[] = [];
    countryOptions: LookupItem[] = [];

    rows: BudgetAppropriation[] = [];

    dialogVisible = false;
    editing: Partial<BudgetAppropriation> = {};

    /**
     * Set when Save is pressed, so the dialog can say which field is missing.
     * Stream, currency and amount were checked together and reported by one
     * toast that named none of them.
     */
    submitted = false;

    viewVisible = false;
    viewing: BudgetAppropriation | null = null;

    constructor(private api: AccountingApiService,
                private lookupService: LookupService,
                private messages: MessageService,
                private confirmation: ConfirmationService,
                private translate: TranslationService,
                private codeLists: CodeListService) {}

    /*
     * The catchError fallbacks make an empty lookup a normal outcome (a fresh
     * install has no fiscal years), not a hidden failure: none of these requests
     * is marked SUPPRESS_ERROR_TOAST, so a 403 still surfaces through the
     * ErrorInterceptor's toast.
     */
    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translate.getCurrentLanguage();
    }

    ngOnInit(): void {
        this.loading = true;
        forkJoin({
            years: this.api.fiscalYears().pipe(catchError(() => of([] as FiscalYear[]))),
            feeTypes: this.api.feeTypes().pipe(catchError(() => of([] as FeeType[]))),
            currencies: this.lookupService.getStudyCurrencies().pipe(catchError(() => of([] as LookupItem[]))),
            // The attaché is a study country; the home-country list reuses its ids for other countries (live QA H6).
            countries: this.lookupService.getStudyCountriesIn().pipe(catchError(() => of([] as LookupItem[])))
        }).subscribe(({ years, feeTypes, currencies, countries }) => {
            this.fiscalYears = years;
            this.feeTypes = feeTypes;
            this.currencyOptions = currencies;
            this.countryOptions = countries;
            this.selectedFiscalYearId = (years.find(y => y.status === 'OPEN') ?? years[0])?.id ?? null;
            this.loading = false;
            this.reload();
        });
    }

    reload(): void {
        if (this.selectedFiscalYearId == null) { this.rows = []; return; }
        this.loading = true;
        this.api.appropriations(this.selectedFiscalYearId)
            .pipe(finalize(() => this.loading = false))
            .subscribe({
                next: rows => this.rows = rows,
                error: () => this.toastError('ACCOUNTING.LOAD_FAILED')
            });
    }

    // ------------------------------------------------------------- display

    /** An empty attaché is the ministry-wide line, not a missing value. */
    /** Sorts by the names and labels the cells print, not by the ids behind them. */
    sortTable(event: SortEvent): void {
        sortRows(event, {
            feeType: row => this.feeTypeName(row.feeTypeId),
            currency: row => this.currencyName(row.currencyId),
            attache: row => this.countryName(row.studyCountryInId),
            status: row => this.codeLists.label('appropriation-status', row.status, this.currentLanguage)
        }, this.currentLanguage);
    }

    countryName(id?: number | null): string {
        if (id == null) return this.translate.translate('ACCOUNTING.MINISTRY_WIDE');
        return this.countryOptions.find(c => c.id === id)?.name ?? String(id);
    }

    currencyName(id?: number | null): string {
        if (id == null) return '—';
        return this.currencyOptions.find(c => c.id === id)?.name ?? String(id);
    }

    feeTypeName(id?: number | null): string {
        const f = this.feeTypes.find(t => t.id === id);
        if (!f) return id == null ? '—' : String(id);
        return this.isArabic() ? f.nameAr : f.nameEn;
    }

    /** True when the figure has been drawn beyond what was appropriated. */
    isOverAllocated(row: BudgetAppropriation): boolean {
        return row.status !== 'REVISED' && Number(row.remaining) < 0;
    }

    get amountTotal(): number {
        return this.live().reduce((s, r) => s + (Number(r.amount) || 0), 0);
    }

    get allocatedTotal(): number {
        return this.live().reduce((s, r) => s + (Number(r.allocated) || 0), 0);
    }

    get remainingTotal(): number {
        return this.live().reduce((s, r) => s + (Number(r.remaining) || 0), 0);
    }

    /** Revised rows are history; the totals are of what stands. */
    private live(): BudgetAppropriation[] {
        return this.rows.filter(r => r.status !== 'REVISED');
    }

    private isArabic(): boolean {
        return (this.translate.getCurrentLanguage() || 'en').startsWith('ar');
    }

    // ------------------------------------------------------------- editing

    /** Shown only when the user could file the figure in more than one place. */
    get needsAttacheChoice(): boolean {
        return !this.editing.id && this.countryOptions.length > 1;
    }

    openNew(): void {
        this.submitted = false;
        this.editing = {
            feeTypeId: this.feeTypes.find(f => f.feeTypeCode === 'STIPEND')?.id ?? null as unknown as number,
            currencyId: this.currencyOptions.length === 1 ? this.currencyOptions[0].id : null as unknown as number,
            studyCountryInId: this.countryOptions.length === 1 ? this.countryOptions[0].id : null,
            referenceNumber: '',
            notes: ''
        };
        this.dialogVisible = true;
    }

    /** A revised row is history and cannot be opened. */
    canEdit(row: BudgetAppropriation): boolean {
        return row.status !== 'REVISED';
    }

    openEdit(row: BudgetAppropriation): void {
        if (!this.canEdit(row)) { return; }
        this.submitted = false;
        this.editing = { ...row };
        this.dialogVisible = true;
    }

    /**
     * The whole appropriation. The list reads as a row of figures; the note and
     * the reference it was raised against have no column, and they are what says
     * where the number came from.
     */
    openView(row: BudgetAppropriation): void {
        this.viewing = row;
        this.viewVisible = true;
    }

    /** The dialog opens on the year after the last one there is. */
    openYearDialog(): void {
        const last = Math.max(0, ...this.fiscalYears.map(y => y.yearNo));
        this.newYearNo = last > 0 ? last + 1 : new Date().getFullYear();
        this.yearDialogVisible = true;
    }

    openYear(): void {
        if (!this.newYearNo) { return; }
        this.openingYear = true;
        this.api.openFiscalYear(this.newYearNo)
            .pipe(finalize(() => this.openingYear = false))
            .subscribe({
                next: year => {
                    this.yearDialogVisible = false;
                    this.fiscalYears = [...this.fiscalYears, year].sort((a, b) => a.yearNo - b.yearNo);
                    this.selectedFiscalYearId = year.id;
                    this.toastSuccess('ACCOUNTING.FISCAL_YEAR_OPENED');
                    this.reload();
                },
                error: err => this.toastServerError(err)
            });
    }

    save(): void {
        this.submitted = true;
        if (this.selectedFiscalYearId == null) { return; }
        if (this.editing.feeTypeId == null || this.editing.currencyId == null
            || !this.editing.amount || this.editing.amount <= 0) {
            this.toastError('ACCOUNTING.APPROPRIATION_REQUIRED');
            return;
        }
        this.saving = true;
        const done = {
            next: () => {
                this.saving = false;
                this.dialogVisible = false;
                this.toastSuccess('ACCOUNTING.SAVED');
                this.reload();
            },
            error: (err: { error?: { message?: string }; message?: string }) => {
                this.saving = false;
                this.toastServerError(err);
            }
        };
        if (this.editing.id) {
            this.api.updateAppropriation(this.editing.id, {
                amount: this.editing.amount,
                referenceNumber: this.editing.referenceNumber,
                notes: this.editing.notes
            }).subscribe(done);
        } else {
            this.api.createAppropriation({ ...this.editing, fiscalYearId: this.selectedFiscalYearId }).subscribe(done);
        }
    }

    /**
     * Approving fixes the figure the whole fund chain draws down, so the dialog
     * states what is about to be approved: amount, currency, stream, attaché and
     * fiscal year.
     */
    confirmApprove(row: BudgetAppropriation): void {
        const year = this.fiscalYears.find(y => y.id === row.fiscalYearId)?.yearNo
            ?? this.fiscalYears.find(y => y.id === this.selectedFiscalYearId)?.yearNo ?? '';
        const message = this.translate.translate('ACCOUNTING.CONFIRM_APPROVE_APPROPRIATION')
            .replace('{amount}', this.formatAmount(row.amount))
            .replace('{currency}', this.currencyName(row.currencyId))
            .replace('{feeType}', this.feeTypeName(row.feeTypeId))
            .replace('{attache}', this.countryName(row.studyCountryInId))
            .replace('{year}', String(year));
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

    approve(row: BudgetAppropriation): void {
        this.api.approveAppropriation(row.id).subscribe({
            next: () => { this.toastSuccess('ACCOUNTING.APPROPRIATION_APPROVED'); this.reload(); },
            error: err => this.toastServerError(err)
        });
    }

    // -------------------------------------------------------------- toasts

    private toastSuccess(key: string): void {
        this.messages.add({ severity: 'success', summary: this.translate.translate(key) });
    }

    private toastError(key: string): void {
        this.messages.add({ severity: 'error', summary: this.translate.translate(key) });
    }

    private toastServerError(err: { error?: { message?: string }; message?: string }): void {
        this.messages.add({
            severity: 'error',
            summary: this.translate.translate('ACCOUNTING.SAVE_FAILED'),
            detail: err?.error?.message || err?.message || '',
            life: 8000
        });
    }
}
