import { Component, OnInit } from '@angular/core';
import { MessageService, SortEvent } from 'primeng/api';
import { finalize } from 'rxjs/operators';
import { AccountingApiService, AttacheAdvanceBalance, FiscalYear, ReconciliationResult } from '../accounting-api.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { sortRows } from '../../../service/table-sort.util';
import { TranslationService } from '../../../service/translation.service';

/**
 * Each cultural attaché's advance (العهدة), and whether it reconciles.
 *
 * The figure that matters is `available` — un-earmarked cash:
 *
 *     available = opening + transferred - disbursed - returned
 *                 - (wafarEarmarked - wafarSpent)
 *
 * At quarter close every unpaid entitlement has been earmarked to Wafar, so it
 * reaches zero. Mid-quarter it is positive.
 *
 * This screen replaces reading a single global Wafar figure. The pre-existing
 * summary summed every country and every currency into one number, which implied
 * Cairo's savings could pay Riyadh's students — they cannot, and each row here is
 * one attaché's own pool.
 *
 * The rows are country-scoped: an officer sees their own attachés, and only a
 * ministry user sees every one. So a short list is not necessarily a short list
 * of advances — it may be a short list of the ones you may read.
 */
@Component({
    selector: 'app-advance-balances',
    templateUrl: './advance-balances.component.html',
    // Own MessageService + <p-toast>, like the other accounting screens; without
    // them every toast here rendered twice through the root service.
    providers: [MessageService],
    standalone: false
})
export class AdvanceBalancesComponent implements OnInit {

    loading = false;
    fiscalYears: FiscalYear[] = [];
    selectedFiscalYearId: number | null = null;

    balances: AttacheAdvanceBalance[] = [];
    reconciliation: ReconciliationResult | null = null;
    countryOptions: LookupItem[] = [];

    constructor(private api: AccountingApiService,
                private lookupService: LookupService,
                private messages: MessageService,
                private translate: TranslationService) {}

    /** The attaché by name. The column showed a raw id, which named nothing. */
    /** The attaché column prints a country name, so that is what it sorts by. */
    sortTable(event: SortEvent): void {
        sortRows(event, { attache: row => this.countryName(row.studyCountryInId) },
            this.translate.getCurrentLanguage());
    }

    countryName(id?: number | null): string {
        if (id == null) return '—';
        return this.countryOptions.find(c => c.id === id)?.name ?? String(id);
    }

    ngOnInit(): void {
        // The attaché is a study country; the home-country list reuses its ids for other countries (live QA H6).
        this.lookupService.getStudyCountriesIn().subscribe({
            next: countries => this.countryOptions = countries,
            error: () => { this.countryOptions = []; }
        });
        this.loading = true;
        this.api.fiscalYears().subscribe({
            next: years => {
                this.fiscalYears = years;
                this.selectedFiscalYearId = (years.find(y => y.status === 'OPEN') ?? years[0])?.id ?? null;
                // reload() owns the spinner from here; with no fiscal year it returns at once.
                this.loading = false;
                this.reload();
            },
            error: () => { this.loading = false; this.toastError(); }
        });
    }

    reload(): void {
        if (this.selectedFiscalYearId == null) { return; }
        this.loading = true;
        this.api.advances(this.selectedFiscalYearId)
            .pipe(finalize(() => this.loading = false))
            .subscribe({
                next: rows => { this.balances = rows; },
                error: () => this.toastError()
            });
        this.api.reconciliation(this.selectedFiscalYearId).subscribe({
            next: result => this.reconciliation = result,
            error: () => this.toastError()
        });
    }

    private toastError(): void {
        this.messages.add({ severity: 'error', summary: this.translate.translate('ACCOUNTING.LOAD_FAILED') });
    }

    /**
     * True when a row's own numbers do not add up.
     *
     * The database refuses to store a negative `available`, so this should never
     * fire — which is exactly why it is worth showing if it ever does.
     */
    isBroken(row: AttacheAdvanceBalance): boolean {
        return row.available < 0 || row.wafarBalance < 0;
    }
}
