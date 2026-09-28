import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfQuery, ReportPdfService, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface MonthlyReportData {
    reportYear: number;
    reportMonth: number;
    reportPeriod: string;
    totalStudents: number;
    newEnrollments: number;
    activeStudents: number;
    totalFinancialTransactions: number;
    totalAmountDisbursed: number;
    avgAmountPerStudent: number;
    scholarshipStudents: number;
    totalUniversities: number;
    totalCountries: number;
}

@Component({
    selector: 'app-monthly-report',
    templateUrl: './monthly-report.component.html',
    styleUrls: ['./monthly-report.component.scss'],
    standalone: false
})
export class MonthlyReportComponent implements OnInit, OnDestroy {

    reportData: MonthlyReportData[] = [];
    filteredData: MonthlyReportData[] = [];
    loading: boolean = false;
    years: number[] = [];
    selectedYear: number | null = null;
    selectedCountryIds: number[] = [];
    allowedCountries: { label: string; value: number }[] = [];
    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        private messageService: MessageService,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ,
        private lookupService: LookupService) {}

    get hasMultiCountry(): boolean {
        return this.reportCountryScope.hasMultiCountryAccess();
    }

    ngOnInit(): void {
        if (this.hasMultiCountry) {
            this.loadCountries();
        }
        this.loadReportData();
        this.loadYears();
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            this.loadReportData();
            this.loadYears();
        });
        this.translationService.currentLanguage$.pipe(takeUntil(this.destroy$)).subscribe(() => {
            // Rebuild years options with new language if needed (years are numbers, no rebuild needed)
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadCountries(): void {
        this.http.get<any[]>(this.apiUrl.apiPath('/api/lookup-tables/study-country-in'))
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (data) => {
                    const isAr = this.translationService.getCurrentLanguage() === 'ar';
                    const all = (Array.isArray(data) ? data : ((data as any)?.content || [])).map(c => ({
                        label: (isAr ? c.studyCountryInName : c.studyCountryInNameEn) || c.studyCountryInName || String(c.id),
                        value: c.id as number
                    }));
                    if (!this.reportCountryScope.isMinistryUser && this.reportCountryScope.allowedCountryIds.length > 0) {
                        const allowed = new Set(this.reportCountryScope.allowedCountryIds);
                        this.allowedCountries = all.filter(c => allowed.has(c.value));
                    } else {
                        this.allowedCountries = all;
                    }
                },
                error: () => {}
            });
    }

    private buildHttpParams(): HttpParams {
        if (this.selectedCountryIds.length > 0) {
            return this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds);
        }
        return this.reportCountryScope.appendCountryParam();
    }

    loadReportData(): void {
        this.loading = true;
        const base = this.selectedYear
            ? this.apiUrl.apiPath(`/api/periodic-reports/monthly/by-year/${this.selectedYear}`)
            : this.apiUrl.apiPath('/api/periodic-reports/monthly');
        this.http.get<MonthlyReportData[]>(base, { params: this.buildHttpParams() })
            .subscribe({
                next: (data) => {
                    this.reportData = data;
                    this.filteredData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading monthly reports:', error);
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    loadYears(): void {
        this.http.get<number[]>(this.apiUrl.apiPath('/api/periodic-reports/monthly/years'), { params: this.buildHttpParams() })
            .subscribe({
                next: (years) => {
                    this.years = years;
                },
                error: (error) => {
                    console.error('Error loading years:', error);
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    filterData(): void {
        this.loadReportData();
    }

    onCountryChange(): void {
        this.loadReportData();
        this.loadYears();
    }

    private monthlyPdfQuery() {
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
            ['year', this.selectedYear]
        ]);
    }

    selectedLang = uiReportLang(this.translationService);
    pdfApiPath = '/api/periodic-reports/monthly/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    reportLoading = false;

    generateReport(): void {
        this.pdfQuery = { ...this.monthlyPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.monthlyPdfQuery(), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

