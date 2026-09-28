import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface QuarterlyReportData {
    reportYear: number;
    reportQuarter: number;
    reportPeriod: string;
    totalStudents: number;
    newEnrollments: number;
    activeStudents: number;
    totalFinancialTransactions: number;
    totalAmountDisbursed: number;
    scholarshipAmountDisbursed: number;
    avgAmountPerStudent: number;
    scholarshipStudents: number;
    regularStudents: number;
    totalUniversities: number;
    totalCountries: number;
    totalMajors: number;
}

@Component({
    selector: 'app-quarterly-report',
    templateUrl: './quarterly-report.component.html',
    styleUrls: ['./quarterly-report.component.scss'],
    standalone: false
})
export class QuarterlyReportComponent implements OnInit, OnDestroy {

    reportData: QuarterlyReportData[] = [];
    filteredData: QuarterlyReportData[] = [];
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
            ? this.apiUrl.apiPath(`/api/periodic-reports/quarterly/by-year/${this.selectedYear}`)
            : this.apiUrl.apiPath('/api/periodic-reports/quarterly');
        this.http.get<QuarterlyReportData[]>(base, { params: this.buildHttpParams() })
            .subscribe({
                next: (data) => {
                    this.reportData = data;
                    this.filteredData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading quarterly reports:', error);
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
        this.http.get<number[]>(this.apiUrl.apiPath('/api/periodic-reports/quarterly/years'), { params: this.buildHttpParams() })
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

    private quarterlyPdfQuery() {
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

    reportLoading = false;
    pdfApiPath = '/api/periodic-reports/quarterly/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.quarterlyPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.quarterlyPdfQuery(), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

