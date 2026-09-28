import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface ExtensionStatistics {
    extensionType: string;
    totalExtensions: number;
    approvedCount: number;
    pendingCount: number;
    rejectedCount: number;
    averageQuarters: number;
    approvalRate: number;
}

interface ExtensionTrend {
    year: number;
    quarter: string;
    extensionCount: number;
    approvedCount: number;
    trend: string;
}

interface ExtensionMetadataResponse {
    extensionTypes?: string[];
}

@Component({
    selector: 'app-student-extension-statistics',
    templateUrl: './student-extension-statistics.component.html',
    styleUrls: ['./student-extension-statistics.component.scss'],
    standalone: false
})
export class StudentExtensionStatisticsComponent implements OnInit, OnDestroy {

    statisticsData: ExtensionStatistics[] = [];
    trendData: ExtensionTrend[] = [];
    filteredStatistics: ExtensionStatistics[] = [];
    filteredTrends: ExtensionTrend[] = [];
    loading: boolean = false;
    extensionTypes: string[] = [];
    selectedType: string = '';
    selectedYear: number | null = null;
    years: number[] = [];
    selectedCountryIds: number[] = [];
    allowedCountries: { label: string; value: number }[] = [];
    private readonly destroy$ = new Subject<void>();

    chartData: unknown;
    chartOptions: unknown;

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService,
        private messageService: MessageService
    ,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        if (this.hasMultiCountry) { this.loadCountries(); }
        this.loadStatistics();
        this.loadTrends();
        this.loadExtensionTypes();
        this.loadAvailableYears();
        this.initializeChart();

        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.initializeChart();
        });
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            this.loadStatistics();
            this.loadTrends();
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
        if (this.selectedCountryIds.length > 0) {
            return this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds);
        }
        return this.reportCountryScope.appendCountryParam();
    }

    onCountryChange(): void { this.loadStatistics(); this.loadTrends(); }

    loadStatistics(): void {
        this.loading = true;
        this.http.get<ExtensionStatistics[]>(this.apiUrl.apiPath('/api/reports/statistical/student-extension/statistics'), { params: this.buildHttpParams() })
            .subscribe({
                next: (data) => {
                    this.statisticsData = data ?? [];
                    this.filteredStatistics = this.statisticsData;
                    this.updateChart();
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading extension statistics:', error);
                    this.loading = false;
                    this.statisticsData = [];
                    this.filteredStatistics = [];
                    this.updateChart();
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    loadTrends(): void {
        this.http.get<ExtensionTrend[]>(this.apiUrl.apiPath('/api/reports/statistical/student-extension/trends'), { params: this.buildHttpParams() })
            .subscribe({
                next: (data) => {
                    this.trendData = data ?? [];
                    this.filteredTrends = this.trendData;
                },
                error: (error) => {
                    console.error('Error loading extension trends:', error);
                    this.trendData = [];
                    this.filteredTrends = [];
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    loadExtensionTypes(): void {
        this.http.get<ExtensionMetadataResponse>(this.apiUrl.apiPath('/api/reports/statistical/student-extension/metadata'))
            .subscribe({
                next: (metadata) => {
                    this.extensionTypes = metadata.extensionTypes ?? [];
                },
                error: (error) => {
                    console.error('Error loading extension types:', error);
                    this.extensionTypes = [];
                }
            });
    }

    loadAvailableYears(): void {
        this.lookupService.getAcademicYears()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: opts => {
                    this.years = opts.map(o => parseInt(o.value, 10)).filter(n => !isNaN(n));
                },
                error: () => { this.years = []; }
            });
    }

    filterData(): void {
        this.filteredStatistics = this.statisticsData.filter(stat => {
            return !this.selectedType || stat.extensionType === this.selectedType;
        });

        this.filteredTrends = this.trendData.filter(trend => {
            return !this.selectedYear || trend.year === this.selectedYear;
        });

        this.updateChart();
    }

    clearFilters(): void {
        this.selectedType = '';
        this.selectedYear = null;
        this.selectedCountryIds = [];
        this.filteredStatistics = this.statisticsData;
        this.filteredTrends = this.trendData;
        this.updateChart();
    }

    private extensionReportPdfQuery() {
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
            ['extensionType', this.selectedType || undefined],
            ['year', this.selectedYear ?? undefined],
            ['lang', this.selectedLang]
        ]);
    }

    reportLoading = false;
    pdfApiPath = '/api/reports/statistical/student-extension/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.extensionReportPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.extensionReportPdfQuery(), lang: this.selectedLang };

    }

    getStatusSeverity(status: string): string {
        switch (status.toLowerCase()) {
            case 'approved': return 'success';
            case 'pending': return 'warning';
            case 'rejected': return 'danger';
            default: return 'info';
        }
    }

    getTrendSeverity(trend: string): string {
        if (trend.includes('Increasing')) return 'success';
        if (trend.includes('Stable')) return 'info';
        if (trend.includes('Decreasing')) return 'warning';
        return 'secondary';
    }

    initializeChart(): void {
        const documentStyle = getComputedStyle(document.documentElement);
        const textColor = documentStyle.getPropertyValue('--text-color');
        const textColorSecondary = documentStyle.getPropertyValue('--text-color-secondary');
        const surfaceBorder = documentStyle.getPropertyValue('--surface-border');

        this.chartOptions = {
            maintainAspectRatio: false,
            aspectRatio: 0.8,
            plugins: {
                legend: {
                    labels: {
                        color: textColor
                    }
                }
            },
            scales: {
                x: {
                    ticks: {
                        color: textColorSecondary
                    },
                    grid: {
                        color: surfaceBorder,
                        drawBorder: false
                    }
                },
                y: {
                    ticks: {
                        color: textColorSecondary
                    },
                    grid: {
                        color: surfaceBorder,
                        drawBorder: false
                    }
                }
            }
        };
    }

    updateChart(): void {
        const documentStyle = getComputedStyle(document.documentElement);

        this.chartData = {
            labels: this.filteredStatistics.map(s => s.extensionType),
            datasets: [
                {
                    label: this.translationService.translate('REPORTS.INDIVIDUAL_REPORTS.STUDENT_EXTENSION_STATS.APPROVED'),
                    backgroundColor: documentStyle.getPropertyValue('--green-500'),
                    data: this.filteredStatistics.map(s => s.approvedCount)
                },
                {
                    label: this.translationService.translate('REPORTS.INDIVIDUAL_REPORTS.STUDENT_EXTENSION_STATS.PENDING'),
                    backgroundColor: documentStyle.getPropertyValue('--yellow-500'),
                    data: this.filteredStatistics.map(s => s.pendingCount)
                },
                {
                    label: this.translationService.translate('REPORTS.INDIVIDUAL_REPORTS.STUDENT_EXTENSION_STATS.REJECTED'),
                    backgroundColor: documentStyle.getPropertyValue('--red-500'),
                    data: this.filteredStatistics.map(s => s.rejectedCount)
                }
            ]
        };
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
