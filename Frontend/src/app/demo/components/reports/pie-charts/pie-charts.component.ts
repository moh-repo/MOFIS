import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ChartDataService } from 'src/app/demo/service/chart-data.service';
import { ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { UIChart } from 'primeng/chart';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { changesOnly } from '../../../service/reload-streams.util';

const PRINT_AREA_ID = 'pie-charts-print-area';

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

interface PieSummary {
    total: number;
    topLabel: string;
    topValue: number;
}

@Component({
    selector: 'app-pie-charts',
    templateUrl: './pie-charts.component.html',
    styleUrls: ['./pie-charts.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class PieChartsComponent implements OnInit, OnDestroy {

    @ViewChild('majorChart') majorChart?: UIChart;
    @ViewChild('genderChart') genderChart?: UIChart;
    @ViewChild('universityChart') universityChart?: UIChart;
    @ViewChild('studyStatusChart') studyStatusChart?: UIChart;

    loading = false;
    pieChartOptions: any;

    majorChartData: any;
    genderChartData: any;
    universityTypeChartData: any;
    studyStatusChartData: any;

    majorSummary: PieSummary = { total: 0, topLabel: '-', topValue: 0 };
    genderSummary: PieSummary = { total: 0, topLabel: '-', topValue: 0 };
    universitySummary: PieSummary = { total: 0, topLabel: '-', topValue: 0 };
    studyStatusSummary: PieSummary = { total: 0, topLabel: '-', topValue: 0 };

    private readonly destroy$ = new Subject<void>();
    private majorRawData?: ChartDataResponse;
    private genderRawData?: ChartDataResponse;
    private universityRawData?: ChartDataResponse;
    private studyStatusRawData?: ChartDataResponse;

    constructor(
        private chartDataService: ChartDataService,
        private chartPrintService: ChartPrintService,
        private router: Router,
        public translationService: TranslationService,
        private messageService: MessageService
    ) {}

    ngOnInit(): void {
        this.initPieOptions();
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

    initPieOptions(): void {
        const documentStyle = getComputedStyle(document.documentElement);
        const textColor = documentStyle.getPropertyValue('--text-color');
        const isRtl = this.translationService.isRTL();

        this.pieChartOptions = {
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'right',
                    rtl: isRtl,
                    labels: {
                        color: textColor,
                        usePointStyle: true,
                        boxWidth: 12,
                        textAlign: isRtl ? 'right' : 'left'
                    }
                },
                tooltip: {
                    rtl: isRtl,
                    callbacks: {
                        label: (context: any) => {
                            const dataset = context.dataset;
                            const total = (dataset.data as number[]).reduce((sum: number, value: number) => sum + value, 0);
                            const value = context.raw as number;
                            const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : '0.0';
                            return ` ${context.label}: ${value.toLocaleString()} (${percentage}%)`;
                        }
                    }
                }
            }
        };
    }

    loadAllCharts(): void {
        this.loading = true;
        let completedRequests = 0;
        const totalRequests = 4;

        const checkComplete = () => {
            completedRequests += 1;
            if (completedRequests === totalRequests) {
                this.loading = false;
            }
        };

        this.loadMajorDistribution(checkComplete);
        this.loadGenderDistribution(checkComplete);
        this.loadUniversityDistribution(checkComplete);
        this.loadStudyStatusDistribution(checkComplete);
    }

    loadMajorDistribution(callback?: () => void): void {
        this.chartDataService.getMajorDistribution().subscribe({
            next: (data) => {
                this.majorRawData = data;
                this.majorChartData = this.buildPieChart(data, 'PIE_CHART_PAGE.DATASETS.MAJOR');
                this.majorSummary = this.computeSummary(data);
                callback?.();
            },
            error: (error) => {
                console.error('Error loading major distribution data', error);
                this.showError(this.translationService.translate('PIE_CHART_PAGE.ERRORS.MAJOR'));
                callback?.();
            }
        });
    }

    loadGenderDistribution(callback?: () => void): void {
        this.chartDataService.getGenderDistribution().subscribe({
            next: (data) => {
                this.genderRawData = data;
                this.genderChartData = this.buildPieChart(data, 'PIE_CHART_PAGE.DATASETS.GENDER');
                this.genderSummary = this.computeSummary(data);
                callback?.();
            },
            error: (error) => {
                console.error('Error loading gender distribution data', error);
                this.showError(this.translationService.translate('PIE_CHART_PAGE.ERRORS.GENDER'));
                callback?.();
            }
        });
    }

    loadUniversityDistribution(callback?: () => void): void {
        this.chartDataService.getUniversityTypeDistribution().subscribe({
            next: (data) => {
                this.universityRawData = data;
                this.universityTypeChartData = this.buildPieChart(data, 'PIE_CHART_PAGE.DATASETS.UNIVERSITY');
                this.universitySummary = this.computeSummary(data);
                callback?.();
            },
            error: (error) => {
                console.error('Error loading university type distribution data', error);
                this.showError(this.translationService.translate('PIE_CHART_PAGE.ERRORS.UNIVERSITY'));
                callback?.();
            }
        });
    }

    loadStudyStatusDistribution(callback?: () => void): void {
        this.chartDataService.getStudyStatusDistribution().subscribe({
            next: (data) => {
                this.studyStatusRawData = data;
                this.studyStatusChartData = this.buildPieChart(data, 'PIE_CHART_PAGE.DATASETS.STATUS');
                this.studyStatusSummary = this.computeSummary(data);
                callback?.();
            },
            error: (error) => {
                console.error('Error loading study status distribution data', error);
                this.showError(this.translationService.translate('PIE_CHART_PAGE.ERRORS.STATUS'));
                callback?.();
            }
        });
    }

    buildPieChart(response: ChartDataResponse, datasetKey: string): any {
        const dataset = response.datasets[0];
        const data = dataset?.data?.map(value => Number(value)) ?? [];
        const palette = this.generatePalette(data.length);
        const label = this.translationService.translate(datasetKey);

        return {
            labels: response.labels,
            datasets: [
                {
                    label,
                    data,
                    backgroundColor: palette,
                    borderColor: palette.map(() => '#ffffff'),
                    borderWidth: 1,
                    hoverOffset: 8
                }
            ]
        };
    }

    computeSummary(response: ChartDataResponse): PieSummary {
        const dataset = response.datasets[0];
        const data = dataset?.data?.map(value => Number(value)) ?? [];
        const total = data.reduce((sum, value) => sum + value, 0);

        let topLabel = '-';
        let topValue = 0;
        data.forEach((value, index) => {
            if (value > topValue) {
                topValue = value;
                topLabel = response.labels[index] ?? '-';
            }
        });

        return { total, topLabel, topValue };
    }

    rebuildLocalizedCharts(): void {
        this.initPieOptions();
        if (this.majorRawData) {
            this.majorChartData = this.buildPieChart(this.majorRawData, 'PIE_CHART_PAGE.DATASETS.MAJOR');
        }
        if (this.genderRawData) {
            this.genderChartData = this.buildPieChart(this.genderRawData, 'PIE_CHART_PAGE.DATASETS.GENDER');
        }
        if (this.universityRawData) {
            this.universityTypeChartData = this.buildPieChart(this.universityRawData, 'PIE_CHART_PAGE.DATASETS.UNIVERSITY');
        }
        if (this.studyStatusRawData) {
            this.studyStatusChartData = this.buildPieChart(this.studyStatusRawData, 'PIE_CHART_PAGE.DATASETS.STATUS');
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
        void this.chartPrintService.previewChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'pie-charts.pdf' });
    }

    downloadChartsPdf(): void {
        void this.chartPrintService.downloadChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'pie-charts.pdf' });
    }

    downloadChartImages(): void {
        void this.chartPrintService.downloadChartImages(PRINT_AREA_ID, this.pageTitle(), { filename: 'pie-charts' });
    }

    private pageTitle(): string {
        return this.translationService.translate('GRAPHS.PIE_CHARTS');
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

    private generatePalette(size: number): string[] {
        const baseColors = [
            '#6366F1', '#22D3EE', '#F97316', '#34D399', '#EF4444',
            '#FBBF24', '#A855F7', '#EC4899', '#3B82F6', '#2DD4BF',
            '#EAB308', '#F59E0B', '#C084FC', '#F472B6', '#93C5FD'
        ];

        const palette: string[] = [];
        for (let i = 0; i < size; i++) {
            palette.push(baseColors[i % baseColors.length]);
        }
        return palette;
    }
}

