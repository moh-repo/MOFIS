import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { MessageService } from 'primeng/api';
import { ChartDataService } from 'src/app/demo/service/chart-data.service';
import { ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { UIChart } from 'primeng/chart';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { changesOnly } from '../../../service/reload-streams.util';
import { localizeSeriesLabel } from '../chart-series-labels';

const PRINT_AREA_ID = 'line-charts-print-area';

interface DatasetResponse {
    label: string;
    data: Array<number | string>;
    backgroundColor?: string;
    borderColor?: string;
}

interface ChartDataResponse {
    labels: string[];
    datasets: DatasetResponse[];
}

@Component({
    selector: 'app-line-charts',
    templateUrl: './line-charts.component.html',
    styleUrls: ['./line-charts.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class LineChartsComponent implements OnInit, OnDestroy {

    @ViewChild('enrollmentChart') enrollmentChart?: UIChart;
    @ViewChild('paymentChart') paymentChart?: UIChart;
    @ViewChild('academicChart') academicChart?: UIChart;
    @ViewChild('quarterlyChart') quarterlyChart?: UIChart;
    @ViewChild('retentionChart') retentionChart?: UIChart;

    loading = false;
    lineChartOptions: any;

    enrollmentTrendData: any;
    paymentTrendData: any;
    academicProgressData: any;
    quarterlyDisbursementData: any;
    retentionTrendData: any;

    enrollmentSummary = {
        totalStudents: 0,
        growthRate: 0,
        yearSpan: '-'
    };

    paymentSummary = {
        totalDisbursed: 0,
        averageDisbursement: 0,
        paymentCount: 0
    };

    academicSummary = {
        averageLevel: 0,
        activeStudents: 0,
        newStudents: 0
    };

    quarterlySummary = {
        totalDisbursed: 0,
        averageDisbursement: 0,
        paymentCount: 0
    };

    retentionSummary = {
        activeStudents: 0,
        suspendedStudents: 0,
        withdrawnStudents: 0
    };

    private readonly destroy$ = new Subject<void>();

    private enrollmentRawData?: ChartDataResponse;
    private paymentRawData?: ChartDataResponse;
    private academicRawData?: ChartDataResponse;
    private quarterlyRawData?: ChartDataResponse;
    private retentionRawData?: ChartDataResponse;

    constructor(
        private chartDataService: ChartDataService,
        private chartPrintService: ChartPrintService,
        private router: Router,
        public translationService: TranslationService,
        private messageService: MessageService
    ) {}

    ngOnInit(): void {
        this.initChartOptions();
        this.loadAllCharts();
        this.translationService.currentLanguage$.pipe(takeUntil(this.destroy$)).subscribe(() => {
            this.rebuildLocalizedCharts();
        });
        this.chartDataService.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => this.loadAllCharts());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    initChartOptions(): void {
        const documentStyle = getComputedStyle(document.documentElement);
        const textColor = documentStyle.getPropertyValue('--text-color');
        const textColorSecondary = documentStyle.getPropertyValue('--text-color-secondary');
        const surfaceBorder = documentStyle.getPropertyValue('--surface-border');
        const isRtl = this.translationService.isRTL();

        this.lineChartOptions = {
            maintainAspectRatio: false,
            aspectRatio: 0.8,
            locale: this.translationService.getCurrentLanguage(),
            plugins: {
                legend: {
                    position: 'top',
                    rtl: isRtl,
                    textDirection: isRtl ? 'rtl' : 'ltr',
                    labels: {
                        color: textColor,
                        textAlign: isRtl ? 'right' : 'left',
                        usePointStyle: true,
                        font: {
                            weight: 500
                        }
                    }
                },
                tooltip: {
                    mode: 'index',
                    intersect: false,
                    rtl: isRtl
                }
            },
            interaction: {
                mode: 'index',
                intersect: false
            },
            scales: {
                x: {
                    reverse: isRtl,
                    ticks: {
                        color: textColorSecondary
                    },
                    grid: {
                        display: false,
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

    loadAllCharts(): void {
        this.loading = true;
        let completedRequests = 0;
        const totalRequests = 5;

        const checkComplete = () => {
            completedRequests += 1;
            if (completedRequests === totalRequests) {
                this.loading = false;
            }
        };

        this.loadEnrollmentTrend(checkComplete);
        this.loadPaymentPatterns(checkComplete);
        this.loadAcademicProgress(checkComplete);
        this.loadQuarterlyDisbursement(checkComplete);
        this.loadRetentionTrend(checkComplete);
    }

    loadEnrollmentTrend(callback?: () => void): void {
        this.chartDataService.getEnrollmentGrowthTrend().subscribe({
            next: (data) => {
                this.enrollmentRawData = data;
                this.enrollmentTrendData = this.buildLineChart(data);
                this.updateEnrollmentSummary(data);
                if (callback) {
                    callback();
                }
            },
            error: (error) => {
                console.error('Error loading enrollment growth data', error);
                this.showError(this.translationService.translate('LINE_CHARTS.ERRORS.ENROLLMENT'));
                if (callback) {
                    callback();
                }
            }
        });
    }

    loadPaymentPatterns(callback?: () => void): void {
        this.chartDataService.getPaymentPatternsTrend().subscribe({
            next: (data) => {
                this.paymentRawData = data;
                this.paymentTrendData = this.buildLineChart(data);
                this.updatePaymentSummary(data);
                if (callback) {
                    callback();
                }
            },
            error: (error) => {
                console.error('Error loading payment pattern data', error);
                this.showError(this.translationService.translate('LINE_CHARTS.ERRORS.PAYMENT'));
                if (callback) {
                    callback();
                }
            }
        });
    }

    loadAcademicProgress(callback?: () => void): void {
        this.chartDataService.getAcademicProgressTrend().subscribe({
            next: (data) => {
                this.academicRawData = data;
                this.academicProgressData = this.buildLineChart(data);
                this.updateAcademicSummary(data);
                if (callback) {
                    callback();
                }
            },
            error: (error) => {
                console.error('Error loading academic progress data', error);
                this.showError(this.translationService.translate('LINE_CHARTS.ERRORS.ACADEMIC'));
                if (callback) {
                    callback();
                }
            }
        });
    }

    loadQuarterlyDisbursement(callback?: () => void): void {
        this.chartDataService.getQuarterlyDisbursementTrend().subscribe({
            next: (data) => {
                this.quarterlyRawData = data;
                this.quarterlyDisbursementData = this.buildLineChart(data);
                this.updateQuarterlySummary(data);
                if (callback) {
                    callback();
                }
            },
            error: (error) => {
                console.error('Error loading quarterly disbursement data', error);
                this.showError(this.translationService.translate('LINE_CHARTS.ERRORS.QUARTERLY'));
                if (callback) {
                    callback();
                }
            }
        });
    }

    loadRetentionTrend(callback?: () => void): void {
        this.chartDataService.getStudentRetentionTrend().subscribe({
            next: (data) => {
                this.retentionRawData = data;
                this.retentionTrendData = this.buildLineChart(data);
                this.updateRetentionSummary(data);
                if (callback) {
                    callback();
                }
            },
            error: (error) => {
                console.error('Error loading student retention data', error);
                this.showError(this.translationService.translate('LINE_CHARTS.ERRORS.RETENTION'));
                if (callback) {
                    callback();
                }
            }
        });
    }

    buildLineChart(response: ChartDataResponse): any {
        const translate = (key: string) => this.translationService.translate(key);
        const datasets = response.datasets.map(dataset => {
            return {
                // Keyed on the series' own name, not its position (chart-series-labels.ts).
                label: localizeSeriesLabel(dataset.label, translate),
                data: (dataset.data || []).map(value => Number(value)),
                fill: false,
                borderColor: dataset.borderColor || 'rgba(99, 102, 241, 1)',
                backgroundColor: dataset.borderColor || 'rgba(99, 102, 241, 1)',
                tension: 0.35,
                pointRadius: 4,
                pointHoverRadius: 6,
                borderWidth: 2
            };
        });

        return {
            labels: response.labels,
            datasets
        };
    }

    updateEnrollmentSummary(response: ChartDataResponse): void {
        const studentDataset = response.datasets[0]?.data?.map(value => Number(value)) ?? [];
        const totalStudents = studentDataset.reduce((sum, value) => sum + value, 0);
        const firstValue = studentDataset[0] ?? 0;
        const lastValue = studentDataset[studentDataset.length - 1] ?? 0;
        const growthRate = studentDataset.length > 1 && firstValue !== 0
            ? ((lastValue - firstValue) / firstValue) * 100
            : 0;

        const firstYear = response.labels[0] ?? '-';
        const lastYear = response.labels[response.labels.length - 1] ?? '-';
        const yearSpan = response.labels.length > 1 ? `${firstYear} – ${lastYear}` : firstYear;

        this.enrollmentSummary = {
            totalStudents,
            growthRate,
            yearSpan
        };
    }

    updatePaymentSummary(response: ChartDataResponse): void {
        const totalDataset = response.datasets[0]?.data?.map(value => Number(value)) ?? [];
        const averageDataset = response.datasets[1]?.data?.map(value => Number(value)) ?? [];
        const countDataset = response.datasets[2]?.data?.map(value => Number(value)) ?? [];

        const totalDisbursed = totalDataset.reduce((sum, value) => sum + value, 0);
        const averageDisbursement = averageDataset.length
            ? averageDataset.reduce((sum, value) => sum + value, 0) / averageDataset.length
            : 0;
        const paymentCount = countDataset.reduce((sum, value) => sum + value, 0);

        this.paymentSummary = {
            totalDisbursed,
            averageDisbursement,
            paymentCount
        };
    }

    updateAcademicSummary(response: ChartDataResponse): void {
        const levelDataset = response.datasets[0]?.data?.map(value => Number(value)) ?? [];
        const activeDataset = response.datasets[1]?.data?.map(value => Number(value)) ?? [];
        const newDataset = response.datasets[2]?.data?.map(value => Number(value)) ?? [];

        const averageLevel = levelDataset.length
            ? levelDataset.reduce((sum, value) => sum + value, 0) / levelDataset.length
            : 0;
        const activeStudents = activeDataset.reduce((sum, value) => sum + value, 0);
        const newStudents = newDataset.reduce((sum, value) => sum + value, 0);

        this.academicSummary = {
            averageLevel,
            activeStudents,
            newStudents
        };
    }

    updateQuarterlySummary(response: ChartDataResponse): void {
        const totalDataset = response.datasets[0]?.data?.map(value => Number(value)) ?? [];
        const averageDataset = response.datasets[1]?.data?.map(value => Number(value)) ?? [];
        const countDataset = response.datasets[2]?.data?.map(value => Number(value)) ?? [];

        const totalDisbursed = totalDataset.reduce((sum, value) => sum + value, 0);
        const averageDisbursement = averageDataset.length
            ? averageDataset.reduce((sum, value) => sum + value, 0) / averageDataset.length
            : 0;
        const paymentCount = countDataset.reduce((sum, value) => sum + value, 0);

        this.quarterlySummary = {
            totalDisbursed,
            averageDisbursement,
            paymentCount
        };
    }

    updateRetentionSummary(response: ChartDataResponse): void {
        const activeDataset = response.datasets[0]?.data?.map(value => Number(value)) ?? [];
        const suspendedDataset = response.datasets[1]?.data?.map(value => Number(value)) ?? [];
        const withdrawnDataset = response.datasets[2]?.data?.map(value => Number(value)) ?? [];

        const activeStudents = activeDataset.reduce((sum, value) => sum + value, 0);
        const suspendedStudents = suspendedDataset.reduce((sum, value) => sum + value, 0);
        const withdrawnStudents = withdrawnDataset.reduce((sum, value) => sum + value, 0);

        this.retentionSummary = {
            activeStudents,
            suspendedStudents,
            withdrawnStudents
        };
    }

    private rebuildLocalizedCharts(): void {
        this.initChartOptions();
        if (this.enrollmentRawData) {
            this.enrollmentTrendData = this.buildLineChart(this.enrollmentRawData);
        }
        if (this.paymentRawData) {
            this.paymentTrendData = this.buildLineChart(this.paymentRawData);
        }
        if (this.academicRawData) {
            this.academicProgressData = this.buildLineChart(this.academicRawData);
        }
        if (this.quarterlyRawData) {
            this.quarterlyDisbursementData = this.buildLineChart(this.quarterlyRawData);
        }
        if (this.retentionRawData) {
            this.retentionTrendData = this.buildLineChart(this.retentionRawData);
        }
    }

    downloadChart(chartRef: UIChart | undefined, filename: string): void {
        this.chartPrintService.downloadChartAsPng(chartRef, filename);
    }

    refreshCharts(): void {
        this.loadAllCharts();
    }

    goBack(): void {
        this.router.navigate(['/reports/graphs']);
    }

    printCharts(): void {
        void this.chartPrintService.printChartsPdf(PRINT_AREA_ID, this.pageTitle());
    }

    previewChartsPdf(): void {
        void this.chartPrintService.previewChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'line-charts.pdf' });
    }

    downloadChartsPdf(): void {
        void this.chartPrintService.downloadChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'line-charts.pdf' });
    }

    downloadChartImages(): void {
        void this.chartPrintService.downloadChartImages(PRINT_AREA_ID, this.pageTitle(), { filename: 'line-charts' });
    }

    private pageTitle(): string {
        return this.translationService.translate('GRAPHS.LINE_CHARTS');
    }

    showError(message: string): void {
        this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: message
        });
    }

    isRTL(): boolean {
        return this.translationService.isRTL();
    }
}

