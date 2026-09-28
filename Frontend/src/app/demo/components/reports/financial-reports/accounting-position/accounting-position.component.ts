import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface PositionRow {
    studyCountryInId: number | null;
    studyCountryName: string | null;
    currencyId: number | null;
    currencyName: string | null;
    openingBalance: number | null;
    transferred: number | null;
    disbursed: number | null;
    returned: number | null;
    wafarBalance: number | null;
    available: number | null;
    allocationCount: number;
    allocatedAmount: number | null;
}

interface FiscalYearOption { id: number; name: string; status: string; }

/**
 * The accounting position, by attaché and currency.
 *
 * <p>A position is always as at a fiscal year, so the year is required rather
 * than optional: without one the report would be adding unrelated years
 * together. The newest year is selected on load so the screen opens with
 * something to show.
 */
@Component({
    selector: 'app-accounting-position',
    templateUrl: './accounting-position.component.html',
    styleUrls: ['./accounting-position.component.scss'],
    standalone: false
})
export class AccountingPositionComponent implements OnInit, OnDestroy {

    rows: PositionRow[] = [];
    fiscalYears: FiscalYearOption[] = [];
    allowedCountries: { label: string; value: number }[] = [];

    selectedFiscalYearId: number | null = null;
    selectedCountryIds: number[] = [];

    loading = false;
    selectedLang = uiReportLang(this.translationService);

    readonly pdfApiPath = '/api/reports/financial/accounting-position/export-report';
    readonly excelApiPath = '/api/reports/financial/accounting-position/export-excel';

    private readonly destroy$ = new Subject<void>();
    private readonly base = '/api/reports/financial/accounting-position';

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private messageService: MessageService,
        private lookupService: LookupService
    ) {}

    ngOnInit(): void {
        this.loadMetadata();

        this.reportCountryScope.selectedCountryId$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => { this.selectedCountryIds = []; this.load(); });

        this.translationService.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => { this.loadMetadata(); this.load(); });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    get hasMultiCountry(): boolean {
        return this.reportCountryScope.hasMultiCountryAccess();
    }

    private get lang(): string {
        return this.translationService.getCurrentLanguage() === 'en' ? 'en' : 'ar';
    }

    loadMetadata(): void {
        this.http.get<any>(this.apiUrl.apiPath(`${this.base}/metadata`), {
            params: new HttpParams().set('lang', this.lang)
        })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: meta => {
                    this.fiscalYears = meta?.fiscalYears ?? [];
                    this.allowedCountries = (meta?.countries ?? [])
                        .map((c: any) => ({ label: c.name, value: c.id }));

                    // Newest year by default: the screen should open on something.
                    if (this.selectedFiscalYearId == null && this.fiscalYears.length) {
                        this.selectedFiscalYearId = this.fiscalYears[this.fiscalYears.length - 1].id;
                    }
                    this.load();
                },
                error: () => {
                    this.fiscalYears = [];
                    this.allowedCountries = [];
                }
            });
    }

    private buildParams(): HttpParams {
        let params = this.selectedCountryIds.length > 0
            ? this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds)
            : this.reportCountryScope.appendCountryParam();
        params = params.set('lang', this.lang);
        if (this.selectedFiscalYearId != null) {
            params = params.set('fiscalYearId', String(this.selectedFiscalYearId));
        }
        return params;
    }

    load(): void {
        if (this.selectedFiscalYearId == null) {
            this.rows = [];
            return;
        }
        this.loading = true;
        this.http.get<PositionRow[]>(this.apiUrl.apiPath(`${this.base}/rows`), { params: this.buildParams() })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: data => { this.rows = data ?? []; this.loading = false; },
                error: err => {
                    this.rows = [];
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: err?.error?.message
                            || this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    onFilterChange(): void {
        this.load();
    }

    clearFilters(): void {
        this.selectedCountryIds = [];
        this.load();
    }

    reportQuery(): ReportPdfQuery {
        const countryEntries: [string, any][] = [];
        if (this.selectedCountryIds.length === 1) {
            countryEntries.push(['countryId', this.selectedCountryIds[0]]);
        } else if (this.selectedCountryIds.length > 1) {
            countryEntries.push(['countryIds', this.selectedCountryIds]);
        } else {
            countryEntries.push(['countryId', this.reportCountryScope.countryIdParam()]);
        }
        return buildReportPdfQuery([
            ...countryEntries,
            ['fiscalYearId', this.selectedFiscalYearId ?? undefined],
            ['lang', this.selectedLang]
        ]);
    }

    /** Available per currency; never one total across currencies. */
    get totalsByCurrency(): Array<{ currency: string; total: number }> {
        const map = new Map<string, number>();
        for (const r of this.rows) {
            const key = r.currencyName || '';
            map.set(key, (map.get(key) ?? 0) + (r.available ?? 0));
        }
        return Array.from(map.entries()).map(([currency, total]) => ({ currency, total }));
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }
}
