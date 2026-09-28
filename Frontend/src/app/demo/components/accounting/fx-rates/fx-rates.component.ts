import { Component, OnInit } from '@angular/core';
import { ConfirmationService, MessageService, SortEvent } from 'primeng/api';
import { finalize } from 'rxjs/operators';
import { AccountingApiService, FxRate } from '../accounting-api.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { toIsoDate } from '../accounting-shared';
import { sortRows } from '../../../service/table-sort.util';

/**
 * Exchange rates: how many units of one currency another buys on a given day.
 *
 * Dated, because a transaction may only be translated at the rate of the day it
 * happened; one row per pair per day. No rate is ever assumed — the table ships
 * empty and stays empty until someone enters a rate from a source they can name.
 *
 * Reference data, not tenant data: the same rate serves every attaché, so there
 * is no country filter here. A wrong rate is deleted rather than retired.
 */
@Component({
    selector: 'app-fx-rates',
    templateUrl: './fx-rates.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class FxRatesComponent implements OnInit {

    loading = false;
    saving = false;

    currencyOptions: LookupItem[] = [];
    filterFrom: number | null = null;
    filterTo: number | null = null;

    rows: FxRate[] = [];

    dialogVisible = false;
    draft: { fromCurrencyId: number | null; toCurrencyId: number | null; rateDate: Date | null;
             rate: number | null; rateSource: string; notes: string } = this.emptyDraft();

    /**
     * Set when Save is pressed, so the dialog can say which field is missing.
     * `draftValid` covered all four at once and reported them with a single
     * toast that named none of them.
     */
    submitted = false;

    viewVisible = false;
    viewing: FxRate | null = null;

    constructor(private api: AccountingApiService,
                private lookupService: LookupService,
                private messages: MessageService,
                private confirm: ConfirmationService,
                private translate: TranslationService) {}

    ngOnInit(): void {
        this.lookupService.getStudyCurrencies().subscribe({
            next: c => this.currencyOptions = c,
            error: () => { this.currencyOptions = []; }
        });
        this.reload();
    }

    reload(): void {
        this.loading = true;
        this.api.fxRates(this.filterFrom, this.filterTo)
            .pipe(finalize(() => this.loading = false))
            .subscribe({
                next: rows => this.rows = rows,
                error: () => this.toastError('ACCOUNTING.LOAD_FAILED')
            });
    }

    /** Both currency columns print a name, so both sort by the name. */
    sortTable(event: SortEvent): void {
        sortRows(event, {
            fromCurrency: row => this.currencyName(row.fromCurrencyId),
            toCurrency: row => this.currencyName(row.toCurrencyId)
        }, this.translate.getCurrentLanguage());
    }

    currencyName(id?: number | null): string {
        if (id == null) return '—';
        return this.currencyOptions.find(c => c.id === id)?.name ?? String(id);
    }

    // ------------------------------------------------------------- create

    openNew(): void {
        this.submitted = false;
        this.draft = this.emptyDraft();
        // The functional currency (YER) is the usual target; pre-fill it when known.
        const yer = this.currencyOptions.find(c => /YER|Yemen|يمني/i.test(c.name));
        if (yer) { this.draft.toCurrencyId = yer.id; }
        this.dialogVisible = true;
    }

    /** True when the draft can be sent: two different currencies, a day, a positive rate. */
    get draftValid(): boolean {
        const d = this.draft;
        return d.fromCurrencyId != null && d.toCurrencyId != null
            && d.fromCurrencyId !== d.toCurrencyId
            && d.rateDate != null
            && d.rate != null && d.rate > 0;
    }

    /** The rate as it was filed: the source it came from and the note against it. */
    openView(rate: FxRate): void {
        this.viewing = rate;
        this.viewVisible = true;
    }

    save(): void {
        this.submitted = true;
        if (!this.draftValid) {
            this.toastError('ACCOUNTING.RATE_REQUIRED');
            return;
        }
        this.saving = true;
        this.api.createFxRate({
            fromCurrencyId: this.draft.fromCurrencyId!,
            toCurrencyId: this.draft.toCurrencyId!,
            rateDate: toIsoDate(this.draft.rateDate)!,
            rate: this.draft.rate!,
            rateSource: this.draft.rateSource || null,
            notes: this.draft.notes || null
        }).pipe(finalize(() => this.saving = false))
            .subscribe({
                next: () => {
                    this.dialogVisible = false;
                    this.toastSuccess('ACCOUNTING.SAVED');
                    this.reload();
                },
                error: err => this.toastServerError(err)
            });
    }

    remove(rate: FxRate): void {
        this.confirm.confirm({
            message: this.translate.translate('ACCOUNTING.CONFIRM_DELETE_RATE'),
            header: this.translate.translate('ACCOUNTING.CONFIRM_TITLE'),
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: this.translate.translate('COMMON.YES'),
            rejectLabel: this.translate.translate('COMMON.NO'),
            accept: () => this.api.deleteFxRate(rate.id).subscribe({
                next: () => { this.toastSuccess('ACCOUNTING.RATE_DELETED'); this.reload(); },
                error: err => this.toastServerError(err)
            })
        });
    }

    private emptyDraft() {
        return { fromCurrencyId: null, toCurrencyId: null, rateDate: new Date(), rate: null, rateSource: '', notes: '' };
    }

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
