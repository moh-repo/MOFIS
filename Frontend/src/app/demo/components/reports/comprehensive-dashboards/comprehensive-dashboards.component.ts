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

const PRINT_AREA_ID = 'comprehensive-dashboards-print-area';

interface SimpleDataPoint {
    label: string;
    value: number;
}

interface DashboardOverview {
    totalStudents: number;
    activeStudents: number;
    newStudents: number;
    suspendedStudents: number;
    totalDisbursedAmount: number;
    averageDisbursement: number;
    enrollmentTrend: SimpleDataPoint[];
    financialTrend: SimpleDataPoint[];
    topCountries: SimpleDataPoint[];
}

@Component({
    selector: 'app-comprehensive-dashboards',
    templateUrl: './comprehensive-dashboards.component.html',
    styleUrls: ['./comprehensive-dashboards.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class ComprehensiveDashboardsComponent implements OnInit, OnDestroy {

    @ViewChild('enrollmentDashChart') enrollmentDashChart?: UIChart;
    @ViewChild('financialDashChart') financialDashChart?: UIChart;
    @ViewChild('topCountriesChart') topCountriesChart?: UIChart;

    overview?: DashboardOverview;

    enrollmentChartData: any;
    enrollmentChartOptions: any;

    financialChartData: any;
    financialChartOptions: any;

    topCountriesChartData: any;
    topCountriesChartOptions: any;

    loading = false;

    private readonly destroy$ = new Subject<void>();

    constructor(
        private chartDataService: ChartDataService,
        private chartPrintService: ChartPrintService,
        private router: Router,
        private messageService: MessageService,
        public translationService: TranslationService
    ) {}

    ngOnInit(): void {
        this.loadOverview();
        this.translationService.currentLanguage$.pipe(takeUntil(this.destroy$)).subscribe(() => {
            this.buildCharts();
        });
        this.chartDataService.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => this.loadOverview());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadOverview(): void {
        this.loading = true;
        this.chartDataService.getDashboardOverview().subscribe({
            next: (data) => {
                this.overview = data;
                this.buildCharts();
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading dashboard overview', error);
                this.showError(this.translationService.translate('COMPREHENSIVE_DASHBOARDS.ERRORS.LOAD'));
                this.loading = false;
            }
        });
    }

    buildCharts(): void {
        if (!this.overview) {
            return;
        }
        const documentStyle = getComputedStyle(document.documentElement);
        const textColor = documentStyle.getPropertyValue('--text-color');
        const textColorSecondary = documentStyle.getPropertyValue('--text-color-secondary');
        const surfaceBorder = documentStyle.getPropertyValue('--surface-border');

        this.enrollmentChartData = {
            labels: this.overview.enrollmentTrend.map(point => point.label),
            datasets: [
                {
                    label: this.translationService.translate('COMPREHENSIVE_DASHBOARDS.CHARTS.ENROLLMENT_LABEL'),
                    data: this.overview.enrollmentTrend.map(point => point.value),
                    fill: false,
                    borderColor: '#6366f1',
                    tension: 0.35,
                    borderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 6
                }
            ]
        };

        this.enrollmentChartOptions = {
            maintainAspectRatio: false,
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
                        color: surfaceBorder
                    }
                },
                y: {
                    ticks: {
                        color: textColorSecondary
                    },
                    grid: {
                        color: surfaceBorder
                    }
                }
            }
        };

        this.financialChartData = {
            labels: this.overview.financialTrend.map(point => point.label),
            datasets: [
                {
                    type: 'bar',
                    label: this.translationService.translate('COMPREHENSIVE_DASHBOARDS.CHARTS.FINANCIAL_LABEL'),
                    data: this.overview.financialTrend.map(point => point.value),
                    backgroundColor: '#34d399',
                    borderColor: '#059669',
                    borderWidth: 1.5,
                    maxBarThickness: 48
                }
            ]
        };

        this.financialChartOptions = {
            maintainAspectRatio: false,
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
                        display: false
                    }
                },
                y: {
                    ticks: {
                        color: textColorSecondary
                    },
                    grid: {
                        color: surfaceBorder
                    }
                }
            }
        };

        this.topCountriesChartData = {
            labels: this.overview.topCountries.map(point => point.label),
            datasets: [
                {
                    label: this.translationService.translate('COMPREHENSIVE_DASHBOARDS.CHARTS.TOP_COUNTRIES_LABEL'),
                    data: this.overview.topCountries.map(point => point.value),
                    backgroundColor: this.generatePalette(this.overview.topCountries.length),
                    borderColor: '#ffffff',
                    borderWidth: 1
                }
            ]
        };

        this.topCountriesChartOptions = {
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    labels: {
                        color: textColor
                    }
                }
            }
        };
    }

    generatePalette(count: number): string[] {
        const baseColors = ['#6366f1', '#14b8a6', '#f97316', '#3b82f6', '#a855f7', '#ec4899', '#22c55e', '#facc15'];
        const palette: string[] = [];
        for (let i = 0; i < count; i++) {
            palette.push(baseColors[i % baseColors.length]);
        }
        return palette;
    }

    downloadChart(chartRef: UIChart | undefined, filename: string): void {
        this.chartPrintService.downloadChartAsPng(chartRef, filename);
    }

    refresh(): void {
        this.loadOverview();
    }

    goBack(): void {
        this.router.navigate(['/reports/graphs']);
    }

    printCharts(): void {
        void this.chartPrintService.printChartsPdf(PRINT_AREA_ID, this.pageTitle());
    }

    previewChartsPdf(): void {
        void this.chartPrintService.previewChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'dashboards.pdf' });
    }

    downloadChartsPdf(): void {
        void this.chartPrintService.downloadChartsPdf(PRINT_AREA_ID, this.pageTitle(), { filename: 'dashboards.pdf' });
    }

    downloadChartImages(): void {
        void this.chartPrintService.downloadChartImages(PRINT_AREA_ID, this.pageTitle(), { filename: 'dashboards' });
    }

    private pageTitle(): string {
        return this.translationService.translate('GRAPHS.DASHBOARDS');
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

    formatNumber(value?: number): string {
        return (value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 0 });
    }

    formatCurrency(value?: number): string {
        return (value ?? 0).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 });
    }
}

