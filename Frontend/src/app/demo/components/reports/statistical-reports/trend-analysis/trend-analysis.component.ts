import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface TrendDataResponse {
    trendYear: number;
    trendMonth: number;
    trendPeriod: string;
    metricType: string;
    metricValue: number;
    previousValue: number;
    growthPercentage: number;
    trendDirection: string;
}

interface TrendData extends TrendDataResponse {}

interface LookupOptionResponse {
    id: number | null;
    name: string;
}

interface TrendAnalysisMetadataResponse {
    metricTypes: LookupOptionResponse[];
    directions: LookupOptionResponse[];
    years: number[];
}

interface DropdownOption<T = string | number | null> {
    label: string;
    value: T;
}

@Component({
    selector: 'app-trend-analysis',
    templateUrl: './trend-analysis.component.html',
    styleUrls: ['./trend-analysis.component.scss'],
    standalone: false
})
export class TrendAnalysisComponent implements OnInit, OnDestroy {

    trendData: TrendData[] = [];
    loading: boolean = false;
    metricTypeOptions: DropdownOption[] = [];
    directionOptions: DropdownOption[] = [];
    selectedMetricType: string | null = null;
    selectedDirection: string | null = null;

    selectedCountryIds: number[] = [];
    allowedCountries: { label: string; value: number }[] = [];
    private directionValues: string[] = [];
    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.refreshDirectionOptions();
        });
        if (this.hasMultiCountry) { this.loadCountries(); }
        this.loadMetadata();
        this.reportCountryScope.selectedCountryId$.pipe(takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            if (this.metricTypeOptions.length) {
                this.loadTrendData();
            }
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
        this.http.get<any[]>(`${environment.apiUrl}/mohe/api/lookup-tables/study-country-in`)
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

    onCountryChange(): void { this.loadMetadata(); }

    loadMetadata(): void {
        // The options are derived from the rows this scope can see, so the
        // country filter has to travel with the request.
        this.http
            .get<TrendAnalysisMetadataResponse>(
                `${environment.apiUrl}/mohe/api/reports/statistical/trend-analysis/metadata`,
                { params: this.buildHttpParams() })
            .subscribe({
                next: (metadata) => {
                    this.metricTypeOptions = this.toDropdownOptions(metadata.metricTypes);
                    this.directionValues = (metadata.directions ?? []).map((option) => option.name);
                    this.refreshDirectionOptions();
                    this.loadTrendData();
                },
                error: (error) => {
                    console.error('Error loading trend metadata:', error);
                }
            });
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.trendPdfQuery(), lang: this.selectedLang };

    }

    loadTrendData(): void {
        let params = this.buildHttpParams();
        if (this.selectedMetricType) {
            params = params.set('metricType', this.selectedMetricType);
        }
        if (this.selectedDirection) {
            params = params.set('direction', this.selectedDirection);
        }

        this.loading = true;
        this.http
            .get<TrendDataResponse[]>(`${environment.apiUrl}/mohe/api/reports/statistical/trend-analysis`, { params })
            .subscribe({
                next: (data) => {
                    this.trendData = (data ?? []).map((item) => this.normalizeTrendData(item));
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading trend data:', error);
                    this.loading = false;
                }
            });
    }

    onFilterChange(): void {
        this.loadTrendData();
    }

    clearFilters(): void {
        this.selectedMetricType = null;
        this.selectedDirection = null;
        this.selectedCountryIds = [];
        this.loadTrendData();
    }

    getTrendSeverity(direction: string): string {
        switch (this.directionKind(direction)) {
            case 'up':   return 'success';
            case 'down': return 'danger';
            default:     return 'info';
        }
    }

    private trendPdfQuery() {
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
            ['metricType', this.selectedMetricType],
            ['direction', this.selectedDirection],
            ['lang', this.selectedLang]
        ]);
    }

    reportLoading = false;
    pdfApiPath = '/api/reports/statistical/trend-analysis/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.trendPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    translateDirection(direction: string): string {
        switch (this.directionKind(direction)) {
            case 'up':   return this.translationService.translate('REPORTS.REPORT_SUBPAGES.INCREASING');
            case 'down': return this.translationService.translate('REPORTS.REPORT_SUBPAGES.DECREASING');
            case 'flat': return this.translationService.translate('REPORTS.REPORT_SUBPAGES.STABLE');
            // A value the mapping does not know: show what the database holds
            // rather than label it as something it may not be.
            default:     return direction ?? '';
        }
    }

    /**
     * The trend direction column holds 'up', 'down' or 'flat'.
     *
     * This used to test for 'increase' and 'decrease', which no stored value
     * contains, so every direction fell through to the last branch: all three
     * dropdown entries read "Stable", and a month that fell 99% was tagged
     * stable in neutral blue. The wordier spellings are kept so the mapping
     * still holds if the column is ever widened.
     */
    private directionKind(direction: string | null | undefined): 'up' | 'down' | 'flat' | null {
        const normalized = (direction ?? '').trim().toLowerCase();
        if (normalized === 'up' || normalized.includes('increase') || normalized.includes('rise')) {
            return 'up';
        }
        if (normalized === 'down' || normalized.includes('decrease') || normalized.includes('fall')) {
            return 'down';
        }
        if (normalized === 'flat' || normalized.includes('stable') || normalized.includes('steady')) {
            return 'flat';
        }
        return null;
    }

    private refreshDirectionOptions(): void {
        this.directionOptions = this.directionValues.map((value) => ({
            label: this.translateDirection(value),
            value
        }));
    }

    private toDropdownOptions(options: LookupOptionResponse[] | undefined): DropdownOption[] {
        return (options ?? [])
            .filter((option) => option && option.name)
            .map((option) => ({
                label: option.name,
                value: option.name
            }));
    }

    private normalizeTrendData(data: TrendDataResponse): TrendData {
        return {
            ...data,
            metricValue: this.toNumber(data.metricValue),
            previousValue: this.toNumber(data.previousValue),
            growthPercentage: this.toNumber(data.growthPercentage)
        };
    }

    private toNumber(value: number | string): number {
        if (typeof value === 'number') {
            return value;
        }
        const parsed = parseFloat(value);
        return Number.isNaN(parsed) ? 0 : parsed;
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

