import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService, TreeNode } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ChartDataService } from 'src/app/demo/service/chart-data.service';
import { ChartExportCard, ChartExportOptions, ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { formatNumber } from 'src/app/demo/shared/chart-export/chart-series.util';
import { drawHierarchyPicture, hierarchyTable } from 'src/app/demo/shared/chart-export/hierarchy-export.util';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { changesOnly } from '../../../service/reload-streams.util';

const PRINT_AREA_ID = 'hierarchical-charts-print-area';

type ExportAction = 'preview' | 'print' | 'pdf' | 'images';

/** Box colours in the exported pictures, after each card's tag on screen. */
const TONES = {
    organization: { fill: '#eff6ff', stroke: '#93c5fd' },
    location: { fill: '#ecfdf5', stroke: '#6ee7b7' },
    academic: { fill: '#fffbeb', stroke: '#fcd34d' },
};

interface HierarchyNodeResponse {
    label: string;
    value: number;
    children?: HierarchyNodeResponse[];
}

interface HierarchySummary {
    total: number;
    topLabel: string;
    topValue: number;
}

@Component({
    selector: 'app-hierarchical-charts',
    templateUrl: './hierarchical-charts.component.html',
    styleUrls: ['./hierarchical-charts.component.scss'],
    providers: [MessageService],
    standalone: false,
    // The academic chart alone is ~545 PrimeNG nodes, and checking them costs about a
    // second per application tick in a development build (a fraction of that in
    // production) — enough to keep the main thread busy whenever anything else on the
    // page runs, an export included (R5-11). OnPush checks this page only when its own
    // state changes; every async update below says so with markForCheck().
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class HierarchicalChartsComponent implements OnInit, OnDestroy {

    organizationData: TreeNode[] = [];
    locationData: TreeNode[] = [];
    academicData: TreeNode[] = [];

    organizationSummary: HierarchySummary = { total: 0, topLabel: '-', topValue: 0 };
    locationSummary: HierarchySummary = { total: 0, topLabel: '-', topValue: 0 };
    academicSummary: HierarchySummary = { total: 0, topLabel: '-', topValue: 0 };

    loading = false;
    /** The export under way, if any: its button spins and the others wait. */
    exportAction: ExportAction | null = null;

    private readonly destroy$ = new Subject<void>();
    private organizationRaw?: HierarchyNodeResponse;
    private locationRaw?: HierarchyNodeResponse;
    private academicRaw?: HierarchyNodeResponse;

    constructor(
        private chartDataService: ChartDataService,
        private chartPrintService: ChartPrintService,
        private router: Router,
        public translationService: TranslationService,
        private messageService: MessageService,
        private cdr: ChangeDetectorRef
    ) {}

    ngOnInit(): void {
        this.loadAllHierarchies();
        this.translationService.currentLanguage$.pipe(takeUntil(this.destroy$)).subscribe(() => {
            this.rebuildTrees();
        });
        this.chartDataService.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => this.loadAllHierarchies());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadAllHierarchies(): void {
        this.loading = true;
        this.cdr.markForCheck();
        let completed = 0;
        const totalRequests = 3;

        const done = () => {
            completed += 1;
            if (completed === totalRequests) {
                this.loading = false;
            }
            this.cdr.markForCheck();
        };

        this.chartDataService.getOrganizationHierarchy().subscribe({
            next: (data) => {
                this.organizationRaw = data;
                this.organizationData = this.buildTree(data);
                this.organizationSummary = this.computeSummary(data);
                done();
            },
            error: (error) => {
                console.error('Error loading organization hierarchy', error);
                this.showError(this.translationService.translate('HIERARCHY_CHARTS.ERRORS.ORGANIZATION'));
                done();
            }
        });

        this.chartDataService.getLocationHierarchy().subscribe({
            next: (data) => {
                this.locationRaw = data;
                this.locationData = this.buildTree(data);
                this.locationSummary = this.computeSummary(data);
                done();
            },
            error: (error) => {
                console.error('Error loading location hierarchy', error);
                this.showError(this.translationService.translate('HIERARCHY_CHARTS.ERRORS.LOCATION'));
                done();
            }
        });

        this.chartDataService.getAcademicHierarchy().subscribe({
            next: (data) => {
                this.academicRaw = data;
                this.academicData = this.buildTree(data);
                this.academicSummary = this.computeSummary(data);
                done();
            },
            error: (error) => {
                console.error('Error loading academic hierarchy', error);
                this.showError(this.translationService.translate('HIERARCHY_CHARTS.ERRORS.ACADEMIC'));
                done();
            }
        });
    }

    buildTree(root?: HierarchyNodeResponse): TreeNode[] {
        if (!root) {
            return [];
        }
        return [this.convertNode(root)];
    }

    convertNode(node: HierarchyNodeResponse): TreeNode {
        const formattedLabel = `${node.label} (${this.formatNumber(node.value)})`;
        return {
            label: formattedLabel,
            data: {
                label: node.label,
                value: node.value
            },
            children: (node.children || []).map(child => this.convertNode(child)),
            expanded: true
        };
    }

    computeSummary(node?: HierarchyNodeResponse): HierarchySummary {
        if (!node) {
            return { total: 0, topLabel: '-', topValue: 0 };
        }

        let topLabel = '-';
        let topValue = 0;
        (node.children || []).forEach(child => {
            if (child.value > topValue) {
                topValue = child.value;
                topLabel = child.label;
            }
        });

        return {
            total: node.value ?? 0,
            topLabel,
            topValue
        };
    }

    rebuildTrees(): void {
        if (this.organizationRaw) {
            this.organizationData = this.buildTree(this.organizationRaw);
        }
        if (this.locationRaw) {
            this.locationData = this.buildTree(this.locationRaw);
        }
        if (this.academicRaw) {
            this.academicData = this.buildTree(this.academicRaw);
        }
        this.cdr.markForCheck();
    }

    refresh(): void {
        this.loadAllHierarchies();
    }

    goBack(): void {
        this.router.navigate(['/reports/graphs']);
    }

    printCharts(): void {
        this.runExport('print', () =>
            this.chartPrintService.printChartsPdf(PRINT_AREA_ID, this.pageTitle(), this.exportOptions()));
    }

    previewChartsPdf(): void {
        this.runExport('preview', () =>
            this.chartPrintService.previewChartsPdf(PRINT_AREA_ID, this.pageTitle(), this.exportOptions('hierarchical-charts.pdf')));
    }

    downloadChartsPdf(): void {
        this.runExport('pdf', () =>
            this.chartPrintService.downloadChartsPdf(PRINT_AREA_ID, this.pageTitle(), this.exportOptions('hierarchical-charts.pdf')));
    }

    downloadChartImages(): void {
        this.runExport('images', () =>
            this.chartPrintService.downloadChartImages(PRINT_AREA_ID, this.pageTitle(), this.exportOptions('hierarchical-charts')));
    }

    private runExport(action: ExportAction, run: () => Promise<void>): void {
        if (this.exportAction) {
            return;
        }
        this.exportAction = action;
        run()
            .catch((error) => console.error('Hierarchy export failed', error))
            .finally(() => {
                this.exportAction = null;
                this.cdr.markForCheck();
            });
    }

    /**
     * The cards come from the hierarchies this page loaded, not from its DOM (R5-11).
     * The academic chart alone is ~8,700 elements and ~34,000 px wide; rasterising it
     * held the tab for minutes and yielded only the slice scrolled into view.
     */
    private exportOptions(filename?: string): ChartExportOptions {
        return { cards: () => this.exportCards(), filename };
    }

    /** One card per hierarchy the page shows, in page order. */
    private exportCards(): ChartExportCard[] {
        const t = (key: string) => this.translationService.translate(key);
        const sections = [
            {
                root: this.organizationRaw,
                shown: this.organizationData.length > 0,
                title: t('HIERARCHY_CHARTS.SECTIONS.ORGANIZATION_TITLE'),
                subtitle: t('HIERARCHY_CHARTS.SECTIONS.ORGANIZATION_DESCRIPTION'),
                tone: TONES.organization
            },
            {
                root: this.locationRaw,
                shown: this.locationData.length > 0,
                title: t('HIERARCHY_CHARTS.SECTIONS.LOCATION_TITLE'),
                subtitle: t('HIERARCHY_CHARTS.SECTIONS.LOCATION_DESCRIPTION'),
                tone: TONES.location
            },
            {
                root: this.academicRaw,
                shown: this.academicData.length > 0,
                title: t('HIERARCHY_CHARTS.SECTIONS.ACADEMIC_TITLE'),
                subtitle: t('HIERARCHY_CHARTS.SECTIONS.ACADEMIC_DESCRIPTION'),
                tone: TONES.academic
            }
        ];
        const more = t('HIERARCHY_CHARTS.EXPORT.MORE');

        return sections
            .filter((section) => section.root && section.shown)
            .map((section) => {
                const picture = drawHierarchyPicture(section.root!, {
                    rtl: this.isRTL(),
                    moreLabel: (count) => more.replace('{{count}}', formatNumber(count)),
                    ...section.tone
                });
                const table = hierarchyTable(section.root!,
                    t('HIERARCHY_CHARTS.EXPORT.SEGMENT'), t('HIERARCHY_CHARTS.EXPORT.STUDENTS'));
                return {
                    title: section.title,
                    subtitle: section.subtitle,
                    stats: [],
                    canvas: picture.canvas,
                    displayWidth: picture.width,
                    displayHeight: picture.height,
                    columns: table.columns,
                    rows: table.rows
                };
            });
    }

    private pageTitle(): string {
        return this.translationService.translate('GRAPHS.HIERARCHICAL_CHARTS');
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

    formatNumber(value: number): string {
        return (value ?? 0).toLocaleString();
    }

    trackByIndex(index: number): number {
        return index;
    }

}

