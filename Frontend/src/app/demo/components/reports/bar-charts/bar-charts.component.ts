import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { MessageService } from 'primeng/api';
import { Router } from '@angular/router';
import { ChartDataService } from 'src/app/demo/service/chart-data.service';
import { ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { UIChart } from 'primeng/chart';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { changesOnly } from '../../../service/reload-streams.util';
import { localizeChartSeries } from '../chart-series-labels';

const PRINT_AREA_ID = 'bar-charts-print-area';

interface ChartData {
    labels: string[];
    datasets: Dataset[];
}

interface Dataset {
    label: string;
    data: number[];
    backgroundColor: string;
    borderColor: string;
}

@Component({
    selector: 'app-bar-charts',
    templateUrl: './bar-charts.component.html',
    styleUrls: ['./bar-charts.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class BarChartsComponent implements OnInit, OnDestroy {

    @ViewChild('enrollmentChart') enrollmentChart?: UIChart;
    @ViewChild('financialYearChart') financialYearChart?: UIChart;
    @ViewChild('degreesChart') degreesChart?: UIChart;
    @ViewChild('quarterChart') quarterChart?: UIChart;
    @ViewChild('statusChart') statusChart?: UIChart;

    enrollmentByCountryData: any;
    financialByYearData: any;
    studentsByDegreeData: any;
    financialByQuarterData: any;
    statusDistributionData: any;

    chartOptions: any;
    loading = false;

    private readonly destroy$ = new Subject<void>();

    /** The replies as the server sent them, kept to re-label the series on a language switch. */
    private enrollmentRawData?: ChartData;
    private financialYearRawData?: ChartData;
    private degreeRawData?: ChartData;
    private quarterRawData?: ChartData;
    private statusRawData?: ChartData;

    constructor(
        private chartDataService: ChartDataService,
        private chartPrintService: ChartPrintService,
        public translationService: TranslationService,
        private messageService: MessageService,
        private router: Router
    ) { }

    ngOnInit(): void {
        this.initChartOptions();
        this.loadAllCharts();
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.initChartOptions();
            this.relabelCharts();
        });
        this.chartDataService.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => this.loadAllCharts());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    initChartOptions() {
        const documentStyle = getComputedStyle(document.documentElement);
        const textColor = documentStyle.getPropertyValue('--text-color');
        const textColorSecondary = documentStyle.getPropertyValue('--text-color-secondary');
        const surfaceBorder = documentStyle.getPropertyValue('--surface-border');
        const isRtl = this.translationService.isRTL();

        this.chartOptions = {
            maintainAspectRatio: false,
            aspectRatio: 0.8,
            locale: this.translationService.getCurrentLanguage(),
            plugins: {
                legend: {
                    rtl: isRtl,
                    textDirection: isRtl ? 'rtl' : 'ltr',
                    labels: {
                        color: textColor,
                        textAlign: isRtl ? 'right' : 'left',
                        usePointStyle: true,
                        font: { weight: 500 }
                    }
                },
                tooltip: { mode: 'index', intersect: false, rtl: isRtl }
            },
            scales: {
                x: {
                    reverse: isRtl,
                    ticks: { color: textColorSecondary, font: { weight: 500 } },
                    grid: { display: false, drawBorder: false }
                },
                y: {
                    ticks: { color: textColorSecondary },
                    grid: { color: surfaceBorder, drawBorder: false }
                }
            }
        };
    }

    loadAllCharts() {
        this.loading = true;
        let completedRequests = 0;
        const totalRequests = 5;
        const checkComplete = () => {
            completedRequests++;
            if (completedRequests === totalRequests) {
                this.loading = false;
            }
        };

        this.loadEnrollmentByCountry(checkComplete);
        this.loadFinancialByYear(checkComplete);
        this.loadStudentsByDegree(checkComplete);
        this.loadFinancialByQuarter(checkComplete);
        this.loadStatusDistribution(checkComplete);
    }

    loadEnrollmentByCountry(callback?: () => void) {
        this.chartDataService.getEnrollmentByCountry().subscribe({
            next: (data: ChartData) => {
                this.enrollmentRawData = data;
                this.enrollmentByCountryData = this.localized(data);
                if (callback) callback();
            },
            error: () => { this.showError(this.translationService.translate('COMMON.LOAD_ERROR')); if (callback) callback(); }
        });
    }

    loadFinancialByYear(callback?: () => void) {
        this.chartDataService.getFinancialByYear().subscribe({
            next: (data: ChartData) => {
                this.financialYearRawData = data;
                this.financialByYearData = this.localized(data);
                if (callback) callback();
            },
            error: () => { this.showError(this.translationService.translate('COMMON.LOAD_ERROR')); if (callback) callback(); }
        });
    }

    loadStudentsByDegree(callback?: () => void) {
        this.chartDataService.getStudentsByDegree().subscribe({
            next: (data: ChartData) => {
                this.degreeRawData = data;
                this.studentsByDegreeData = this.localized(data);
                if (callback) callback();
            },
            error: () => { this.showError(this.translationService.translate('COMMON.LOAD_ERROR')); if (callback) callback(); }
        });
    }

    loadFinancialByQuarter(callback?: () => void) {
        this.chartDataService.getFinancialByQuarter().subscribe({
            next: (data: ChartData) => {
                this.quarterRawData = data;
                this.financialByQuarterData = this.localized(data);
                if (callback) callback();
            },
            error: () => { this.showError(this.translationService.translate('COMMON.LOAD_ERROR')); if (callback) callback(); }
        });
    }

    loadStatusDistribution(callback?: () => void) {
        this.chartDataService.getStatusDistribution().subscribe({
            next: (data: ChartData) => {
                this.statusRawData = data;
                this.statusDistributionData = this.localized(data);
                if (callback) callback();
            },
            error: () => { this.showError(this.translationService.translate('COMMON.LOAD_ERROR')); if (callback) callback(); }
        });
    }

    /**
     * The series names in the page's language (live QA R5-12): the legend is what the
     * chart PDF prints as its column headers, so English names here made the Arabic
     * PDF's headers English.
     */
    private localized(data: ChartData): ChartData {
        return localizeChartSeries(data, key => this.translationService.translate(key));
    }

    private relabelCharts(): void {
        if (this.enrollmentRawData) this.enrollmentByCountryData = this.localized(this.enrollmentRawData);
        if (this.financialYearRawData) this.financialByYearData = this.localized(this.financialYearRawData);
        if (this.degreeRawData) this.studentsByDegreeData = this.localized(this.degreeRawData);
        if (this.quarterRawData) this.financialByQuarterData = this.localized(this.quarterRawData);
        if (this.statusRawData) this.statusDistributionData = this.localized(this.statusRawData);
    }

    showError(message: string) {
        this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: message
        });
    }

    downloadChart(chartRef: UIChart | undefined, filename: string): void {
        this.chartPrintService.downloadChartAsPng(chartRef, filename);
    }

    refreshCharts() { this.loadAllCharts(); }
    goBack() { this.router.navigate(['/reports/graphs']); }

    printCharts(): void {
        void this.chartPrintService.printChartsPdf(PRINT_AREA_ID, this.pageTitle());
    }

    previewChartsPdf(): void {
        void this.chartPrintService.previewChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'bar-charts.pdf' });
    }

    downloadChartsPdf(): void {
        void this.chartPrintService.downloadChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'bar-charts.pdf' });
    }

    downloadChartImages(): void {
        void this.chartPrintService.downloadChartImages(PRINT_AREA_ID, this.pageTitle(), { filename: 'bar-charts' });
    }

    private pageTitle(): string {
        return this.translationService.translate('GRAPHS.BAR_CHARTS');
    }

    sumArray(arr: number[] | undefined): number {
        if (!arr || !Array.isArray(arr)) return 0;
        return arr.reduce((a, b) => a + b, 0);
    }

    getDatasetData(chartData: any, datasetIndex: number): number[] {
        return chartData?.datasets?.[datasetIndex]?.data || [];
    }

    calculateAverage(arr: number[] | undefined): number {
        if (!arr || !Array.isArray(arr) || arr.length === 0) return 0;
        return arr.reduce((a, b) => a + b, 0) / arr.length;
    }
}
