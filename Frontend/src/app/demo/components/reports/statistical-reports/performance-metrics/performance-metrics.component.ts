import { Component, OnInit, OnDestroy } from '@angular/core';
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

interface MetricData {
    metricCategory: string;
    metricName: string;
    metricValue: number;
    metricUnit: string;
    targetValue: number;
    achievementPercentage: number;
    performanceStatus: string;
    measurementPeriod: string;
}

@Component({
    selector: 'app-performance-metrics',
    templateUrl: './performance-metrics.component.html',
    styleUrls: ['./performance-metrics.component.scss'],
    standalone: false
})
export class PerformanceMetricsComponent implements OnInit, OnDestroy {

    metricsData: MetricData[] = [];
    filteredData: MetricData[] = [];
    loading = false;
    categories: string[] = [];
    selectedCategory = '';
    selectedStatus = '';
    statusOptions: string[] = [];
    selectedCountryIds: number[] = [];
    allowedCountries: { label: string; value: number }[] = [];
    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private messageService: MessageService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        if (this.hasMultiCountry) { this.loadCountries(); }
        this.loadPerformanceStatuses();
        this.loadMetricsData();
        this.loadCategories();
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            this.loadPerformanceStatuses();
            this.loadMetricsData();
            this.loadCategories();
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    get hasMultiCountry(): boolean {
        return this.reportCountryScope.hasMultiCountryAccess();
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
        let params = new HttpParams();
        if (this.selectedCountryIds.length > 0) {
            params = this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds, params);
        } else {
            params = this.reportCountryScope.appendCountryParam(params);
        }
        if (this.selectedCategory) {
            params = params.set('category', this.selectedCategory);
        }
        if (this.selectedStatus) {
            params = params.set('status', this.selectedStatus);
        }
        return params;
    }

    onCountryChange(): void {
        this.loadPerformanceStatuses();
        this.loadMetricsData();
    }

    loadPerformanceStatuses(): void {
        this.http.get<string[]>(
            this.apiUrl.apiPath('/api/statistical-reports/performance-metrics/performance-statuses'),
            { params: this.reportCountryScope.appendCountryParam() }
        ).subscribe({
            next: (statuses) => { this.statusOptions = statuses || []; },
            error: () => { this.statusOptions = []; }
        });
    }

    loadMetricsData(): void {
        this.loading = true;
        this.http.get<MetricData[]>(
            this.apiUrl.apiPath('/api/statistical-reports/performance-metrics'),
            { params: this.buildHttpParams() }
        ).subscribe({
            next: (data) => {
                this.metricsData = data;
                this.filteredData = data;
                this.loading = false;
            },
            error: () => {
                this.loading = false;
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('COMMON.LOAD_ERROR')
                });
            }
        });
    }

    loadCategories(): void {
        this.http.get<string[]>(
            this.apiUrl.apiPath('/api/statistical-reports/performance-metrics/metric-categories'),
            { params: this.reportCountryScope.appendCountryParam() }
        ).subscribe({
            next: (categories) => { this.categories = categories; },
            error: () => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('COMMON.LOAD_ERROR')
                });
            }
        });
    }

    filterData(): void {
        this.loadMetricsData();
    }

    getStatusSeverity(status: string): string {
        const s = (status || '').toLowerCase();
        if (s.includes('exceed') || s.includes('on_target') || s.includes('on track')) return 'success';
        if (s.includes('informational') || s.includes('on track')) return 'info';
        if (s.includes('below')) return 'warning';
        if (s.includes('critical')) return 'danger';
        return 'info';
    }

    statusLabel(status: string): string {
        return status || '';
    }

    private performancePdfQuery() {
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
            ['category', this.selectedCategory || undefined],
            ['status', this.selectedStatus || undefined],
            ['lang', this.selectedLang]
        ]);
    }

    reportLoading = false;
    pdfApiPath = '/api/statistical-reports/performance-metrics/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.performancePdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.performancePdfQuery(), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
