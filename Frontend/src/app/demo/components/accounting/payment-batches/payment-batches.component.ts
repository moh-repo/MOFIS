import { Component, OnInit } from '@angular/core';
import { ConfirmationService, MessageService, SortEvent } from 'primeng/api';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import {
    AccountingApiService, FeeType, FinanceCandidate, FiscalYear, PaymentBatchCreateRequest,
    PaymentBatchDetail, PaymentBatchLineRequest, PaymentBatchSummary
} from '../accounting-api.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { sortRows } from '../../../service/table-sort.util';
import { MoheQuarterService } from '../../../service/mohe-quarter.service';
import { TranslationService } from '../../../service/translation.service';
import { CodeListService } from '../../../service/code-list.service';
import { QuarterOption, toQuarterOptions, withFiscalYears } from '../accounting-shared';

/**
 * Payment batches (الدفعات): per quarter and attaché, the documents that pay
 * students.
 *
 *     OPEN --approve--> APPROVED --pay--> DISBURSED
 *      |
 *      +--cancel--> CANCELLED
 *
 * A batch is prepared from the quarter's finance rows: the screen offers the
 * rows, the officer ticks the students, and the server is sent exactly those
 * lines — a batch is a document someone signs, so what it holds must be what
 * they saw. Paying is the step that moves the attaché's advance, and the server
 * refuses a batch the attaché cannot cover.
 *
 * The preparer cannot approve; the server enforces it whatever the buttons show.
 */
@Component({
    selector: 'app-payment-batches',
    templateUrl: './payment-batches.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class PaymentBatchesComponent implements OnInit {

    loading = false;
    saving = false;

    quarters: QuarterOption[] = [];
    selectedQuarterKey: number | null = null;
    /** Null lists every attaché in scope. */
    selectedCountryId: number | null = null;

    countryOptions: LookupItem[] = [];
    currencyOptions: LookupItem[] = [];
    feeTypes: FeeType[] = [];

    rows: PaymentBatchSummary[] = [];

    detailVisible = false;
    detail: PaymentBatchDetail | null = null;

    createVisible = false;
    form: { studyCountryInId: number | null; feeTypeId: number | null; currencyId: number | null; notes: string } =
        { studyCountryInId: null, feeTypeId: null, currencyId: null, notes: '' };
    candidates: FinanceCandidate[] = [];
    selected: FinanceCandidate[] = [];
    loadingCandidates = false;

    /**
     * Set when Prepare is pressed, so the dialog can say what is missing.
     * Attache, currency and the selection were checked one after another and
     * reported by three toasts, none of which pointed at a field.
     */
    submitted = false;

    constructor(private api: AccountingApiService,
                private lookupService: LookupService,
                private quarterService: MoheQuarterService,
                private messages: MessageService,
                private confirm: ConfirmationService,
                private translate: TranslationService,
                private codeLists: CodeListService) {}

    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translate.getCurrentLanguage();
    }

    ngOnInit(): void {
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
        this.api.paymentBatches(this.selectedQuarterKey, this.selectedCountryId)
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
            feeType: row => this.feeTypeName(row.feeTypeId),
            currency: row => this.currencyName(row.currencyId),
            status: row => this.codeLists.label('payment-batch-status', row.status, this.currentLanguage)
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

    feeTypeName(id?: number | null): string {
        if (id == null) return this.translate.translate('ACCOUNTING.ALL_STREAMS');
        const f = this.feeTypes.find(t => t.id === id);
        if (!f) return String(id);
        return this.isArabic() ? f.nameAr : f.nameEn;
    }

    get entitledTotal(): number {
        return this.rows.reduce((s, r) => s + (Number(r.entitledTotal) || 0), 0);
    }

    get disbursedTotal(): number {
        return this.rows.reduce((s, r) => s + (Number(r.disbursedTotal) || 0), 0);
    }

    /** What the ticked students add up to, before the batch exists. */
    get selectedDisbursed(): number {
        return this.selected.reduce((s, c) => s + (Number(c.netAmount) || 0), 0);
    }

    private isArabic(): boolean {
        return (this.translate.getCurrentLanguage() || 'en').startsWith('ar');
    }

    // -------------------------------------------------------------- detail

    open(row: PaymentBatchSummary): void {
        this.detail = null;
        this.detailVisible = true;
        this.api.paymentBatch(row.id).subscribe({
            next: d => this.detail = d,
            error: () => { this.detailVisible = false; this.toastError('ACCOUNTING.LOAD_FAILED'); }
        });
    }

    // ------------------------------------------------------------- prepare

    openNew(): void {
        this.submitted = false;
        this.form = {
            studyCountryInId: this.selectedCountryId ?? (this.countryOptions.length === 1 ? this.countryOptions[0].id : null),
            feeTypeId: this.feeTypes.find(f => f.feeTypeCode === 'STIPEND')?.id ?? null,
            currencyId: null,
            notes: ''
        };
        this.candidates = [];
        this.selected = [];
        this.createVisible = true;
        if (this.form.studyCountryInId != null) { this.loadCandidates(); }
    }

    /**
     * The quarter's finance rows for the chosen attaché. Students already paid
     * in full this quarter start unticked; everyone else starts ticked, because
     * paying the quarter is the common case and holding someone is the exception.
     */
    loadCandidates(): void {
        if (this.selectedQuarterKey == null || this.form.studyCountryInId == null) {
            this.candidates = [];
            this.selected = [];
            return;
        }
        this.loadingCandidates = true;
        this.api.paymentBatchCandidates(this.selectedQuarterKey, this.form.studyCountryInId)
            .pipe(finalize(() => this.loadingCandidates = false))
            .subscribe({
                next: rows => {
                    this.candidates = rows;
                    this.selected = rows.filter(c => !c.alreadyPaid);
                    // The finance rows carry the currency; default to it so the batch
                    // and its lines agree.
                    if (this.form.currencyId == null) {
                        this.form.currencyId = rows.find(c => c.currencyId != null)?.currencyId
                            ?? (this.currencyOptions.length === 1 ? this.currencyOptions[0].id : null);
                    }
                },
                error: () => this.toastError('ACCOUNTING.LOAD_FAILED')
            });
    }

    /** Entitled is the finance row's amount; what goes out is its net after Wafar. */
    buildLines(): PaymentBatchLineRequest[] {
        const wafar = this.translate.translate('ACCOUNTING.HELD_WAFAR');
        return this.selected.map(c => ({
            studentId: c.studentId,
            entitledAmount: c.entitledAmount,
            disbursedAmount: c.netAmount,
            heldReason: c.netAmount < c.entitledAmount ? wafar : null
        }));
    }

    save(): void {
        this.submitted = true;
        if (this.selectedQuarterKey == null) { return; }
        if (this.form.studyCountryInId == null) { this.toastError('ACCOUNTING.ATTACHE_REQUIRED'); return; }
        if (this.selected.length === 0) { this.toastError('ACCOUNTING.SELECT_STUDENTS_REQUIRED'); return; }
        if (this.form.currencyId == null) { this.toastError('ACCOUNTING.CURRENCY_REQUIRED'); return; }

        const request: PaymentBatchCreateRequest = {
            moheQuarterKey: this.selectedQuarterKey,
            studyCountryInId: this.form.studyCountryInId,
            feeTypeId: this.form.feeTypeId,
            currencyId: this.form.currencyId,
            notes: this.form.notes || null,
            lines: this.buildLines()
        };
        this.saving = true;
        this.api.createPaymentBatch(request)
            .pipe(finalize(() => this.saving = false))
            .subscribe({
                next: () => {
                    this.createVisible = false;
                    this.toastSuccess('ACCOUNTING.BATCH_CREATED');
                    this.reload();
                },
                error: err => this.toastServerError(err)
            });
    }

    // ------------------------------------------------------------- actions

    approve(row: PaymentBatchSummary): void {
        this.ask('ACCOUNTING.CONFIRM_APPROVE_BATCH', () =>
            this.api.approvePaymentBatch(row.id).subscribe({
                next: () => { this.toastSuccess('ACCOUNTING.BATCH_APPROVED'); this.reload(); },
                error: err => this.toastServerError(err)
            }));
    }

    pay(row: PaymentBatchSummary): void {
        this.ask('ACCOUNTING.CONFIRM_PAY_BATCH', () =>
            this.api.payPaymentBatch(row.id).subscribe({
                next: () => { this.toastSuccess('ACCOUNTING.BATCH_PAID'); this.reload(); },
                error: err => this.toastServerError(err)
            }));
    }

    cancel(row: PaymentBatchSummary): void {
        this.ask('ACCOUNTING.CONFIRM_CANCEL_BATCH', () =>
            this.api.cancelPaymentBatch(row.id).subscribe({
                next: () => { this.toastSuccess('ACCOUNTING.BATCH_CANCELLED'); this.reload(); },
                error: err => this.toastServerError(err)
            }));
    }

    private ask(messageKey: string, accept: () => void): void {
        this.confirm.confirm({
            message: this.translate.translate(messageKey),
            header: this.translate.translate('ACCOUNTING.CONFIRM_TITLE'),
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: this.translate.translate('COMMON.YES'),
            rejectLabel: this.translate.translate('COMMON.NO'),
            accept
        });
    }

    // -------------------------------------------------------------- toasts

    private toastSuccess(key: string): void {
        this.messages.add({ severity: 'success', summary: this.translate.translate(key) });
    }

    private toastError(key: string): void {
        this.messages.add({ severity: 'error', summary: this.translate.translate(key) });
    }

    /** Verbatim: the server says why — preparer = approver, wrong status, advance overdrawn. */
    private toastServerError(err: { error?: { message?: string }; message?: string }): void {
        this.messages.add({
            severity: 'error',
            summary: this.translate.translate('ACCOUNTING.SAVE_FAILED'),
            detail: err?.error?.message || err?.message || '',
            life: 8000
        });
    }
}
