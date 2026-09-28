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
import { CodeListService, PrimeSeverity } from 'src/app/demo/service/code-list.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface EntitlementRow {
    kind: string | null;
    studentId: number | null;
    studentName: string | null;
    studyCountryInId: number | null;
    studyCountryName: string | null;
    studyYear: string | null;
    entitlementDate: string | null;
    amount: number | null;
    currencyName: string | null;
    status: string | null;
    /** The code behind `status` (loan-status, books-allowance-pay-status or travel-type). */
    statusCode: string | null;
    detail: string | null;
}

/**
 * Loans, books allowance and travel tickets in one statement.
 *
 * <p>Three tables with three small screens; the question people actually ask is
 * what a student — or an attaché — has been given, so the three are shown as one
 * list with a kind column.
 */
@Component({
    selector: 'app-student-entitlements',
    templateUrl: './student-entitlements.component.html',
    styleUrls: ['./student-entitlements.component.scss'],
    standalone: false
})
export class StudentEntitlementsComponent implements OnInit, OnDestroy {

    rows: EntitlementRow[] = [];
    kinds: Array<{ code: string; name: string }> = [];
    allowedCountries: { label: string; value: number }[] = [];

    selectedKind: string | null = null;
    selectedCountryIds: number[] = [];
    studentId: number | null = null;

    loading = false;
    selectedLang = uiReportLang(this.translationService);

    readonly pdfApiPath = '/api/reports/financial/student-entitlements/export-report';
    readonly excelApiPath = '/api/reports/financial/student-entitlements/export-excel';

    private readonly destroy$ = new Subject<void>();
    private readonly base = '/api/reports/financial/student-entitlements';

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
                    this.kinds = meta?.kinds ?? [];
                    this.allowedCountries = (meta?.countries ?? [])
                        .map((c: any) => ({ label: c.name, value: c.id }));
                },
                error: () => { this.kinds = []; this.allowedCountries = []; }
            });
    }

    private buildParams(): HttpParams {
        let params = this.selectedCountryIds.length > 0
            ? this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds)
            : this.reportCountryScope.appendCountryParam();
        params = params.set('lang', this.lang);
        if (this.selectedKind) { params = params.set('kind', this.selectedKind); }
        if (this.studentId != null) { params = params.set('studentId', String(this.studentId)); }
        return params;
    }

    load(): void {
        this.loading = true;
        this.http.get<EntitlementRow[]>(this.apiUrl.apiPath(`${this.base}/rows`), { params: this.buildParams() })
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
        this.selectedKind = null;
        this.selectedCountryIds = [];
        this.studentId = null;
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
            ['kind', this.selectedKind || undefined],
            ['studentId', this.studentId ?? undefined],
            ['lang', this.selectedLang]
        ]);
    }

    /** Totals per kind. A loan and a ticket are different obligations; never summed together. */
    get totalsByKind(): Array<{ kind: string; total: number; count: number }> {
        const map = new Map<string, { total: number; count: number }>();
        for (const r of this.rows) {
            const key = r.kind || '';
            const cur = map.get(key) ?? { total: 0, count: 0 };
            cur.total += r.amount ?? 0;
            cur.count += 1;
            map.set(key, cur);
        }
        return Array.from(map.entries()).map(([kind, v]) => ({ kind, ...v }));
    }

    /**
     * Tag colour from the status's own code list — a loan status, a books
     * allowance pay status or a travel type. The report serves the resolved
     * name in `status` and the code in `statusCode`
     * (StudentEntitlementsReportService); the colour is looked up by the code.
     */
    statusSeverity(row: EntitlementRow): PrimeSeverity {
        return this.codeLists.severityIn(
            ['loan-status', 'books-allowance-pay-status', 'travel-type'],
            row.statusCode ?? row.status);
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }
}
