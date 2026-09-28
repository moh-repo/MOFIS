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

interface ForecastData {
    forecastCategory: string;
    forecastYear: number;
    historicalAverage: number;
    growthRate: number;
    predictedValue: number;
    confidenceLevel: string;
    forecastTrend: string;
}

@Component({
    selector: 'app-predictive-analytics',
    templateUrl: './predictive-analytics.component.html',
    styleUrls: ['./predictive-analytics.component.scss'],
    standalone: false
})
export class PredictiveAnalyticsComponent implements OnInit, OnDestroy {

    forecastData: ForecastData[] = [];
    filteredData: ForecastData[] = [];
    loading: boolean = false;
    categories: string[] = [];
    selectedCategory: string = '';
    selectedConfidence: string = '';
    confidenceOptions: { label: string; value: string }[] = [];
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
        this.initializeConfidenceOptions();
        if (this.hasMultiCountry) { this.loadCountries(); }
        this.loadForecastData();
        this.loadCategories();
        
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.initializeConfidenceOptions();
        });
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            this.loadForecastData();
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

    onCountryChange(): void { this.loadForecastData(); }

    initializeConfidenceOptions(): void {
        this.lookupService.getTrendClassFilterOptions('CONFIDENCE')
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: opts => { this.confidenceOptions = opts; },
                error: () => { this.confidenceOptions = []; }
            });
    }

    loadForecastData(): void {
        this.loading = true;
        this.http.get<ForecastData[]>(this.apiUrl.apiPath('/api/statistical-reports/predictive-analytics'), { params: this.buildHttpParams() })
            .subscribe({
                next: (data) => {
                    this.forecastData = data;
                    this.filteredData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading forecast data:', error);
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
        this.http.get<string[]>(this.apiUrl.apiPath('/api/statistical-reports/predictive-analytics/forecast-categories'), { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (categories) => {
                    this.categories = categories;
                },
                error: (error) => {
                    console.error('Error loading categories:', error);
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    filterData(): void {
        this.filteredData = this.forecastData.filter(forecast => {
            const matchesCategory = !this.selectedCategory || forecast.forecastCategory === this.selectedCategory;
            const matchesConfidence = !this.selectedConfidence || forecast.confidenceLevel === this.selectedConfidence;
            return matchesCategory && matchesConfidence;
        });
    }

    getConfidenceSeverity(level: string): string {
        switch (level) {
            case 'High': return 'success';
            case 'Medium': return 'info';
            case 'Low': return 'warning';
            default: return 'secondary';
        }
    }

    getTrendSeverity(trend: string): string {
        if (trend.includes('High Growth') || trend.includes('Moderate Growth')) return 'success';
        if (trend.includes('Slow Growth')) return 'info';
        if (trend.includes('Slight Decline')) return 'warning';
        return 'danger';
    }

    private predictivePdfQuery() {
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
            ['forecastCategory', this.selectedCategory || undefined],
            ['confidenceLevel', this.selectedConfidence || undefined],
            ['lang', this.selectedLang]
        ]);
    }

    reportLoading = false;
    pdfApiPath = '/api/statistical-reports/predictive-analytics/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.predictivePdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.predictivePdfQuery(), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

