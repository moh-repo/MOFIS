import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface DistributionData {
    distributionType: string;
    categoryName: string;
    studentCount: number;
    percentageOfTotal: number;
    rankPosition: number;
    categorySignificance: string;
}

@Component({
    selector: 'app-distribution-statistics',
    templateUrl: './distribution-statistics.component.html',
    styleUrls: ['./distribution-statistics.component.scss'],
    standalone: false
})
export class DistributionStatisticsComponent implements OnInit, OnDestroy {

    distributionData: DistributionData[] = [];
    filteredData: DistributionData[] = [];
    loading: boolean = false;
    distributionTypes: string[] = [];
    selectedDistributionType: string = '';
    selectedCountryIds: number[] = [];
    allowedCountries: { label: string; value: number }[] = [];
    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        private apiUrl: ApiUrlService,
        private messageService: MessageService,
        public translationService: TranslationService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        if (this.hasMultiCountry) { this.loadCountries(); }
        this.loadDistributionData();
        this.loadDistributionTypes();
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            this.loadDistributionData();
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

    onCountryChange(): void { this.loadDistributionData(); }

    loadDistributionData(): void {
        this.loading = true;
        this.http.get<DistributionData[]>(this.apiUrl.apiPath('/api/statistical-reports/distribution-statistics'), { params: this.buildHttpParams() })
            .subscribe({
                next: (data) => {
                    this.distributionData = data;
                    this.filteredData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading distribution data:', error);
                    this.loading = false;
                }
            });
    }

    loadDistributionTypes(): void {
        this.http.get<string[]>(this.apiUrl.apiPath('/api/statistical-reports/distribution-statistics/distribution-types'), { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (types) => {
                    this.distributionTypes = types;
                },
                error: (error) => {
                    console.error('Error loading distribution types:', error);
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    filterData(): void {
        this.filteredData = this.distributionData.filter(dist => 
            !this.selectedDistributionType || dist.distributionType === this.selectedDistributionType
        );
    }

    getSignificanceSeverity(significance: string): string {
        switch (significance) {
            case 'Top 3': return 'success';
            case 'Major Category': return 'info';
            case 'Minor Category': return 'warning';
            default: return 'secondary';
        }
    }

    private distributionPdfQuery() {
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
            ['distributionType', this.selectedDistributionType || undefined],
            ['lang', this.selectedLang]
        ]);
    }

    reportLoading = false;
    pdfApiPath = '/api/statistical-reports/distribution-statistics/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.distributionPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.distributionPdfQuery(), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

