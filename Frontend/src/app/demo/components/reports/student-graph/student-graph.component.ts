import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { Subject } from 'rxjs';
import { takeUntil, finalize } from 'rxjs/operators';
import type { Network, DataSet } from 'vis-network/standalone';
import { environment } from 'src/environments/environment';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { CodeListService } from 'src/app/demo/service/code-list.service';
import { ChartExportOptions, ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { changesOnly } from '../../../service/reload-streams.util';

const PRINT_AREA_ID = 'student-graph-print-area';

/** vis-network draws to a canvas, but the legend sits beside it — export the whole panel. */
const EXPORT_OPTIONS: ChartExportOptions = { domCardSelector: '.sg-network-wrapper' };

interface DropdownOption {
    label: string;
    value: number;
}

/**
 * A node of the tree returned by /api/reports/student-graph. Labels and values
 * arrive already resolved to the requested language, and depth is open-ended:
 * anything with children can be expanded further.
 */
interface GraphNode {
    id: string;
    label: string;
    value?: string | null;
    category?: string | null;
    children: GraphNode[];
}

/** Theme fallback for a node whose category has no colour in LU_GRAPH_CATEGORY (the root, for one). */
const FALLBACK_COLOR = '#607D8B';

@Component({
    selector: 'app-student-graph',
    templateUrl: './student-graph.component.html',
    styleUrls: ['./student-graph.component.scss'],
    providers: [MessageService],
    standalone: false,
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class StudentGraphComponent implements OnInit, OnDestroy {
    @ViewChild('graphContainer', { static: false }) graphContainer!: ElementRef;

    countryOptions: DropdownOption[] = [];
    studentOptions: DropdownOption[] = [];

    selectedCountryId: number | null = null;
    selectedStudentId: number | null = null;

    loadingStudents = false;
    loadingGraph = false;
    exporting = false;

    /** vis-network is loaded on demand so it lands in its own lazy chunk. */
    private visModule?: Promise<typeof import('vis-network/standalone')>;

    private network: Network | null = null;
    private nodes: DataSet<any> | null = null;
    private edges: DataSet<any> | null = null;
    private expandedNodes = new Set<string>();

    /** The tree as returned by the server, indexed by node id for expansion. */
    private graphRoot: GraphNode | null = null;
    private nodeIndex = new Map<string, GraphNode>();

    private readonly destroy$ = new Subject<void>();

    constructor(
        private readonly http: HttpClient,
        private readonly messageService: MessageService,
        private readonly translation: TranslationService,
        private readonly lookupService: LookupService,
        private readonly chartPrintService: ChartPrintService,
        private readonly codeLists: CodeListService,
        private readonly cdr: ChangeDetectorRef
    ) {}

    ngOnInit(): void {
        this.loadCountries();
        this.translation.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => {
                // LookupService clears its cache on language change, so reload
                this.loadCountries();
                // Node labels are resolved server-side, so the tree must be
                // refetched rather than relabelled in place.
                if (this.graphRoot) this.drawGraph();
            });
    }

    ngOnDestroy(): void {
        if (this.network) this.network.destroy();
        this.destroy$.next();
        this.destroy$.complete();
    }

    private loadCountries(): void {
        // LookupService.getStudyCountriesIn() handles field-name mapping (studyCountryInName /
        // studyCountryInNameEn) and returns LookupItem[] with a pre-localised `name` field.
        this.lookupService.getStudyCountriesIn()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (items) => {
                    this.countryOptions = items.map(c => ({
                        label: c.name || c.label || String(c.id),
                        value: c.id
                    }));
                    this.cdr.markForCheck();
                },
                error: () => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    onCountryChange(): void {
        this.selectedStudentId = null;
        this.studentOptions = [];
        this.resetGraph();
        if (!this.selectedCountryId) return;

        this.loadingStudents = true;
        const params = new HttpParams()
            .set('countryId', String(this.selectedCountryId))
            .set('size', '500');

        this.http.get<any>(`${environment.apiUrl}/mohe/api/reports/student-list/search`, { params })
            .pipe(takeUntil(this.destroy$), finalize(() => {
                this.loadingStudents = false;
                this.cdr.markForCheck();
            }))
            .subscribe({
                next: (page) => {
                    const students: any[] = page?.content ?? [];
                    this.studentOptions = students.map(s => ({
                        label: s.studentName,
                        value: s.studentId
                    }));
                    this.cdr.markForCheck();
                },
                error: () => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('STUDENT_GRAPH.LOAD_STUDENTS_FAILED')
                    });
                }
            });
    }

    onStudentChange(): void {
        this.resetGraph();
    }

    drawGraph(): void {
        const studentId = this.selectedStudentId;
        if (!studentId) return;
        this.loadingGraph = true;
        this.resetGraph();
        this.cdr.markForCheck();

        const params = new HttpParams().set('lang', this.translation.getCurrentLanguage() === 'en' ? 'en' : 'ar');

        this.http.get<GraphNode>(`${environment.apiUrl}/mohe/api/reports/student-graph/${studentId}`, { params })
            .pipe(takeUntil(this.destroy$), finalize(() => {
                this.loadingGraph = false;
                this.cdr.markForCheck();
            }))
            .subscribe({
                next: (root) => {
                    this.graphRoot = root;
                    this.indexTree(root);
                    setTimeout(() => void this.initNetwork(), 50);
                },
                error: () => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    private indexTree(node: GraphNode): void {
        this.nodeIndex.set(node.id, node);
        (node.children ?? []).forEach(child => this.indexTree(child));
    }

    private loadVis() {
        return this.visModule ??= import('vis-network/standalone');
    }

    private async initNetwork(): Promise<void> {
        if (!this.graphContainer || !this.graphRoot) return;

        const { Network, DataSet } = await this.loadVis();
        // A new search may have reset the graph while the module was loading.
        if (!this.graphContainer || !this.graphRoot) return;

        this.nodes = new DataSet<any>([this.toVisNode(this.graphRoot, true)]);
        this.edges = new DataSet<any>([]);
        this.expandedNodes.clear();

        if (this.network) {
            this.network.destroy();
            this.network = null;
        }

        this.network = new Network(
            this.graphContainer.nativeElement,
            { nodes: this.nodes, edges: this.edges },
            this.networkOptions()
        );

        this.network.on('click', (params) => {
            if (params.nodes.length > 0) {
                this.onNodeClick(String(params.nodes[0]));
            }
        });

        this.network.once('stabilizationIterationsDone', () => {
            this.network?.fit({ animation: { duration: 500, easingFunction: 'easeInOutQuad' } });
        });

        this.cdr.markForCheck();
    }

    /** Any node with children toggles, at any depth. */
    private onNodeClick(nodeId: string): void {
        const node = this.nodeIndex.get(nodeId);
        if (!node || !(node.children?.length)) return;

        if (this.expandedNodes.has(nodeId)) {
            this.collapse(node);
        } else {
            this.expand(node);
        }
    }

    private expand(node: GraphNode): void {
        const color = this.colorFor(node.category);
        node.children.forEach(child => {
            this.nodes!.add(this.toVisNode(child, false));
            this.edges!.add({
                id: `edge_${node.id}_${child.id}`,
                from: node.id,
                to: child.id,
                color: { color, opacity: child.children?.length ? 0.8 : 0.6 },
                width: child.children?.length ? 2 : 1,
                dashes: !(child.children?.length),
                smooth: { enabled: true, type: 'dynamic' }
            });
        });
        this.expandedNodes.add(node.id);
    }

    /** Removes the node's whole subtree, so collapsing a branch leaves nothing orphaned. */
    private collapse(node: GraphNode): void {
        node.children.forEach(child => {
            if (this.expandedNodes.has(child.id)) {
                this.collapse(child);
            }
            this.edges!.remove(`edge_${node.id}_${child.id}`);
            this.nodes!.remove(child.id);
        });
        this.expandedNodes.delete(node.id);
    }

    private toVisNode(node: GraphNode, isRoot: boolean): any {
        const color = this.colorFor(node.category);
        const label = node.value ? `${node.label}\n${node.value}` : node.label;

        if (isRoot) {
            return {
                id: node.id,
                label: node.label,
                shape: 'star',
                size: 35,
                color: {
                    background: color,
                    border: this.darkenColor(color),
                    highlight: { background: color, border: this.darkenColor(color) }
                },
                font: { color: '#ffffff', size: 14, bold: true },
                title: this.translation.translate('STUDENT_GRAPH.LEGEND_STUDENT'),
                nodeType: 'student'
            };
        }

        // A node that groups others stays a filled ellipse; a value is a pale box.
        if (node.children?.length) {
            return {
                id: node.id,
                label,
                shape: 'ellipse',
                size: 22,
                color: {
                    background: color,
                    border: this.darkenColor(color),
                    highlight: { background: color, border: this.darkenColor(color) }
                },
                font: { color: '#ffffff', size: 12 },
                title: node.label,
                nodeType: 'category'
            };
        }

        return {
            id: node.id,
            label,
            shape: 'box',
            size: 12,
            color: {
                background: '#ECEFF1',
                border: this.darkenColor(color),
                highlight: { background: '#CFD8DC', border: this.darkenColor(color) }
            },
            font: { color: '#37474F', size: 11 },
            nodeType: 'data'
        };
    }

    /** The category's colour (LU_GRAPH_CATEGORY.UI_COLOR), served in the graph-category list. */
    private colorFor(category: string | null | undefined): string {
        return this.codeLists.color('graph-category', category) || FALLBACK_COLOR;
    }

    private darkenColor(hex: string): string {
        const n = parseInt(hex.replace('#', ''), 16);
        const r = Math.max(0, (n >> 16) - 50);
        const g = Math.max(0, ((n >> 8) & 0xff) - 50);
        const b = Math.max(0, (n & 0xff) - 50);
        return '#' + ((r << 16) | (g << 8) | b).toString(16).padStart(6, '0');
    }

    private resetGraph(): void {
        if (this.network) {
            this.network.destroy();
            this.network = null;
        }
        this.nodes = null;
        this.edges = null;
        this.expandedNodes.clear();
        this.graphRoot = null;
        this.nodeIndex.clear();
    }

    private networkOptions(): any {
        return {
            nodes: {
                borderWidth: 2,
                borderWidthSelected: 4,
                shadow: { enabled: true, size: 6, x: 2, y: 2, color: 'rgba(0,0,0,0.15)' }
            },
            edges: {
                smooth: { enabled: true, type: 'dynamic' },
                selectionWidth: 3
            },
            physics: {
                forceAtlas2Based: {
                    gravitationalConstant: -60,
                    centralGravity: 0.01,
                    springLength: 160,
                    springConstant: 0.08
                },
                maxVelocity: 100,
                solver: 'forceAtlas2Based',
                stabilization: { iterations: 250, updateInterval: 25, fit: true }
            },
            interaction: {
                hover: true,
                tooltipDelay: 200,
                navigationButtons: true,
                keyboard: false,
                zoomView: true,
                dragView: true
            },
            layout: { improvedLayout: true }
        };
    }

    get canDraw(): boolean {
        return !!this.selectedStudentId && !this.loadingGraph;
    }

    get graphVisible(): boolean {
        return !!this.network;
    }

    get legendItems() {
        return [
            { labelKey: 'STUDENT_GRAPH.LEGEND_STUDENT',  color: '#2196F3', shape: 'star' },
            { labelKey: 'STUDENT_GRAPH.LEGEND_CATEGORY', color: '#9C27B0', shape: 'ellipse' },
            { labelKey: 'STUDENT_GRAPH.LEGEND_DATA',     color: '#607D8B', shape: 'box' },
        ];
    }

    isRTL(): boolean {
        return this.translation.isRTL();
    }

    printGraph(): void {
        void this.chartPrintService.printChartsPdf(PRINT_AREA_ID, this.graphTitle(), { ...EXPORT_OPTIONS });
    }

    previewGraphPdf(): void {
        void this.runExport(() => this.chartPrintService.previewChartsPdf(PRINT_AREA_ID, this.graphTitle(), {
            ...EXPORT_OPTIONS,
            filename: `${this.exportBaseName()}.pdf`
        }));
    }

    downloadGraphPdf(): void {
        void this.runExport(() => this.chartPrintService.downloadChartsPdf(PRINT_AREA_ID, this.graphTitle(), {
            ...EXPORT_OPTIONS,
            filename: `${this.exportBaseName()}.pdf`
        }));
    }

    downloadGraphImage(): void {
        void this.runExport(() => this.chartPrintService.downloadChartImages(PRINT_AREA_ID, this.graphTitle(), {
            ...EXPORT_OPTIONS,
            filename: this.exportBaseName()
        }));
    }

    /** Rasterising the network takes a moment — keep the buttons disabled until it finishes. */
    private async runExport(action: () => Promise<void>): Promise<void> {
        this.exporting = true;
        try {
            await action();
        } catch {
            this.messageService.add({
                severity: 'error',
                summary: this.translation.translate('COMMON.ERROR'),
                detail: this.translation.translate('REPORTS.VIEWER.LOAD_FAILED')
            });
        } finally {
            this.exporting = false;
            this.cdr.markForCheck();
        }
    }

    private graphTitle(): string {
        const studentName = this.studentName();
        const title = this.translation.translate('STUDENT_GRAPH.TITLE');
        return studentName ? `${title} — ${studentName}` : title;
    }

    private exportBaseName(): string {
        return this.selectedStudentId ? `student-graph-${this.selectedStudentId}` : 'student-graph';
    }

    private studentName(): string {
        return this.graphRoot?.label ?? '';
    }
}
