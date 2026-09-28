import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface DataVisualizationData {
    visualizationId: number;
    chartName: string;
    chartNameEn: string;
    chartType: string;
    category: string;
    dataValue: number;
    dataLabel: string;
    dataLabelEn: string;
    colorCode: string;
    sortOrder: number;
}

@Component({
    selector: 'app-data-visualization',
    templateUrl: './data-visualization.component.html',
    styleUrls: ['./data-visualization.component.scss'],
    standalone: false
})
export class DataVisualizationComponent implements OnInit, OnDestroy {

    reportData: DataVisualizationData[] = [];
    loading: boolean = false;
    chartData: any = {};
    chartOptions: any = {};
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

    ngOnInit(): void {
        this.loadReportData();
        this.initChartOptions();
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.loadReportData();
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadReportData(): void {
        this.loading = true;
        this.http.get<DataVisualizationData[]>(this.apiUrl.apiPath('/api/custom-reports/data-visualization'), { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (data) => {
                    this.reportData = data;
                    this.prepareChartData(data);
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading data visualization:', error);
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    prepareChartData(data: DataVisualizationData[]): void {
        // The view emits chartType 'pie'. This filtered on 'Pie Chart', which no
        // row has ever carried, so the chart was drawn from an empty array and
        // rendered nothing -- the same shape of defect as the geographic map
        // matching English country names against Arabic rows.
        const pieData = data.filter(d => d.chartType === 'pie');
        this.chartData = {
            labels: pieData.map(d => d.dataLabel),
            datasets: [{
                data: pieData.map(d => d.dataValue),
                backgroundColor: pieData.map(d => d.colorCode)
            }]
        };
    }

    initChartOptions(): void {
        this.chartOptions = {
            plugins: {
                legend: {
                    labels: {
                        usePointStyle: true
                    }
                }
            }
        };
    }

    reportLoading = false;
    pdfApiPath = '/api/custom-reports/data-visualization/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...buildReportPdfQuery([['countryId', this.reportCountryScope.countryIdParam()]]), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...buildReportPdfQuery([['countryId', this.reportCountryScope.countryIdParam()]]), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

