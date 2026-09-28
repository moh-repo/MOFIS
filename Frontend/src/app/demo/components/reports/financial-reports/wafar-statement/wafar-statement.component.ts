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
import { CodeListService } from 'src/app/demo/service/code-list.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface WafarStatementRow {
    id: number;
    spendingDate: string | null;
    categoryId: number | null;
    categoryName: string | null;
    referenceNumber: string | null;
    description: string | null;
    payeeName: string | null;
    studentId: number | null;
    studentName: string | null;
    beneficiaryType: string | null;
    studyCountryInId: number | null;
    studyCountryName: string | null;
    amount: number | null;
    status: string | null;
    /** The status in the requested language (M14); older servers send only the code. */
    statusLabel?: string | null;
}

interface CategoryTotal {
    categoryId: number | null;
    categoryName: string | null;
    transactionCount: number;
    approvedAmount: number | null;
    pendingAmount: number | null;
    totalAmount: number | null;
}

interface WafarStatementSummary {
    totalDeposits: number | null;
    totalSpent: number | null;
    currentBalance: number | null;
    pendingAmount: number | null;
    transactionCount: number;
    categoryTotals: CategoryTotal[];
}

interface WafarCategory {
    id: number;
    categoryCode: string;
    categoryNameAr: string;
    categoryNameEn: string;
    isActive: boolean;
}

interface WafarReportMetadata {
    categories?: WafarCategory[];
    statuses?: string[];
    countries?: Array<{ id: number; name: string }>;
}

/**
 * The Wafar statement.
 *
 * <p>Wafar is budget already transferred to an attaché and never disbursed, so it
 * stays there — one attaché's savings cannot pay another's students. The screen
 * therefore never offers a country the caller does not hold; the backend refuses
 * one anyway, but a filter that can only produce a 403 is a bad filter.
 */
@Component({
    selector: 'app-wafar-statement',
    templateUrl: './wafar-statement.component.html',
    styleUrls: ['./wafar-statement.component.scss'],
    standalone: false
})
export class WafarStatementComponent implements OnInit, OnDestroy {

    rows: WafarStatementRow[] = [];
    summary: WafarStatementSummary | null = null;
    categories: WafarCategory[] = [];
    statuses: string[] = [];
    allowedCountries: { label: string; value: number }[] = [];

    selectedCountryIds: number[] = [];
    selectedCategoryId: number | null = null;
    selectedStatus: string | null = null;
    dateFrom: Date | null = null;
    dateTo: Date | null = null;

    loading = false;
    selectedLang = uiReportLang(this.translationService);

    readonly pdfApiPath = '/api/reports/financial/wafar/export-report';
    readonly excelApiPath = '/api/reports/financial/wafar/export-excel';

    private readonly destroy$ = new Subject<void>();
    private readonly base = '/api/reports/financial/wafar';

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private messageService: MessageService,
        private lookupService: LookupService,
        private codeLists: CodeListService
    ) {}

    ngOnInit(): void {
        this.loadMetadata();
        this.load();

        this.reportCountryScope.selectedCountryId$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => {
                this.selectedCountryIds = [];
                this.load();
            });

        // The report resolves category and attaché names server-side for the
        // requested language, so a language switch has to re-fetch rather than
        // re-render: the names live in the payload, not in a translation file.
        this.translationService.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => {
                this.loadMetadata();
                this.load();
            });
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
        this.http.get<WafarReportMetadata>(this.apiUrl.apiPath(`${this.base}/metadata`), {
            params: new HttpParams().set('lang', this.lang)
        })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: meta => {
                    this.categories = meta.categories ?? [];
                    this.statuses = meta.statuses ?? [];
                    this.allowedCountries = (meta.countries ?? [])
                        .map(c => ({ label: c.name, value: c.id }));
                },
                error: () => {
                    this.categories = [];
                    this.statuses = [];
                    this.allowedCountries = [];
                }
            });
    }

    private buildParams(): HttpParams {
        let params = this.selectedCountryIds.length > 0
            ? this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds)
            : this.reportCountryScope.appendCountryParam();

        params = params.set('lang', this.lang);
        if (this.selectedCategoryId != null) {
            params = params.set('categoryId', String(this.selectedCategoryId));
        }
        if (this.selectedStatus) {
            params = params.set('status', this.selectedStatus);
        }
        if (this.dateFrom) {
            params = params.set('dateFrom', this.toIsoDate(this.dateFrom));
        }
        if (this.dateTo) {
            params = params.set('dateTo', this.toIsoDate(this.dateTo));
        }
        return params;
    }

    /** Local calendar date, not toISOString(): that shifts to UTC and can lose a day. */
    private toIsoDate(d: Date): string {
        const month = `${d.getMonth() + 1}`.padStart(2, '0');
        const day = `${d.getDate()}`.padStart(2, '0');
        return `${d.getFullYear()}-${month}-${day}`;
    }

    load(): void {
        this.loading = true;
        const params = this.buildParams();

        this.http.get<WafarStatementRow[]>(this.apiUrl.apiPath(`${this.base}/rows`), { params })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: data => {
                    this.rows = data ?? [];
                    this.loading = false;
                },
                error: () => {
                    this.rows = [];
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });

        this.http.get<WafarStatementSummary>(this.apiUrl.apiPath(`${this.base}/summary`), { params })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: data => this.summary = data,
                error: () => this.summary = null
            });
    }

    onFilterChange(): void {
        this.load();
    }

    clearFilters(): void {
        this.selectedCountryIds = [];
        this.selectedCategoryId = null;
        this.selectedStatus = null;
        this.dateFrom = null;
        this.dateTo = null;
        this.load();
    }

    /** The filters for this run, shared by the PDF and Excel actions. */
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
            ['categoryId', this.selectedCategoryId ?? undefined],
            ['status', this.selectedStatus || undefined],
            ['dateFrom', this.dateFrom ? this.toIsoDate(this.dateFrom) : undefined],
            ['dateTo', this.dateTo ? this.toIsoDate(this.dateTo) : undefined],
            ['lang', this.selectedLang]
        ]);
    }

    categoryLabel(c: WafarCategory): string {
        const ar = this.translationService.getCurrentLanguage() !== 'en';
        return (ar ? c.categoryNameAr : c.categoryNameEn) || c.categoryNameAr || c.categoryNameEn || String(c.id);
    }

    /** A status code named in the reader's language (live QA M14: the codes were shown as stored). */
    statusLabel(status: string | null): string {
        return this.codeLists.label('wafar-spending-status', status);
    }

    /** The status filter's choices: the codes the rows hold, named. */
    get statusOptions(): { label: string; value: string }[] {
        return this.statuses.map(code => ({ label: this.statusLabel(code), value: code }));
    }

    statusSeverity(status: string | null): string {
        switch ((status || '').toLowerCase()) {
            case 'approved': return 'success';
            case 'pending': return 'warning';
            case 'rejected': return 'danger';
            case 'cancelled': return 'secondary';
            default: return 'info';
        }
    }

    /**
     * A transaction with no attaché is not an error to hide — 11 of them predate
     * the country column. Labelling it plainly is better than an empty cell that
     * reads as a rendering fault.
     */
    attacheLabel(row: WafarStatementRow): string {
        return row.studyCountryName
            || this.translationService.translate('REPORTS.WAFAR_STATEMENT.NO_ATTACHE');
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }
}
