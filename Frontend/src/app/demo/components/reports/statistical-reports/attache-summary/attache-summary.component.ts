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

interface AttacheSummaryRow {
    studyCountryInId: number | null;
    studyCountryName: string | null;
    studentCount: number;
    activeStudentCount: number;
    financeRecordCount: number;
    financeTotal: number | null;
    bankCount: number;
    bankBranchCount: number;
    studyYearsOverrides: number;
    studyAmountOverrides: number;
    staffCount: number;
}

/**
 * One line per attaché: students, money, banks, and the rules it has set itself.
 *
 * <p>Read this knowing that every student is currently in Egypt, so one row
 * carries the figures and the rest read zero. The zeros are the useful part —
 * they are how you see which attachés have banks but no branches, or no rules of
 * their own — but this is not yet a comparison.
 */
@Component({
    selector: 'app-attache-summary',
    templateUrl: './attache-summary.component.html',
    styleUrls: ['./attache-summary.component.scss'],
    standalone: false
})
export class AttacheSummaryComponent implements OnInit, OnDestroy {

    rows: AttacheSummaryRow[] = [];
    loading = false;
    selectedLang = uiReportLang(this.translationService);

    readonly pdfApiPath = '/api/reports/statistical/attache-summary/export-report';
    readonly excelApiPath = '/api/reports/statistical/attache-summary/export-excel';

    private readonly destroy$ = new Subject<void>();
    private readonly base = '/api/reports/statistical/attache-summary';

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private messageService: MessageService,
        private lookupService: LookupService
    ) {}

    ngOnInit(): void {
        this.load();

        this.reportCountryScope.selectedCountryId$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => this.load());

        this.translationService.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => this.load());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    private get lang(): string {
        return this.translationService.getCurrentLanguage() === 'en' ? 'en' : 'ar';
    }

    load(): void {
        this.loading = true;
        const params: HttpParams = this.reportCountryScope.appendCountryParam().set('lang', this.lang);

        this.http.get<AttacheSummaryRow[]>(this.apiUrl.apiPath(`${this.base}/rows`), { params })
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

    reportQuery(): ReportPdfQuery {
        return buildReportPdfQuery([
            ['countryId', this.reportCountryScope.countryIdParam()],
            ['lang', this.selectedLang]
        ]);
    }

    /** How many attachés actually hold students — the caveat this report needs. */
    get attachesWithStudents(): number {
        return this.rows.filter(r => r.studentCount > 0).length;
    }

    /** Banks but no branches: the gap the country-scope round surfaced. */
    hasBanksWithoutBranches(row: AttacheSummaryRow): boolean {
        return row.bankCount > 0 && row.bankBranchCount === 0;
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }
}
