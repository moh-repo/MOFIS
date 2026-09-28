import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import type { Network } from 'vis-network/standalone';
import { NetworkGraph, Node, TracerFilter, FilterOptions, NetworkStats } from './student-tracer.models';
import { TranslationService } from '../../service/translation.service';
import { StudentTracerService } from '../../service/student-tracer.service';
import { ReportCountryScopeService } from '../../service/report-country-scope.service';
import { ChartPrintService } from '../../service/chart-print.service';
import { changesOnly } from '../../service/reload-streams.util';

@Component({
    selector: 'app-student-tracer',
    templateUrl: './student-tracer.component.html',
    styleUrls: ['./student-tracer.component.scss'],
    providers: [MessageService],
    standalone: false,
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class StudentTracerComponent implements OnInit, OnDestroy {
    @ViewChild('networkContainer', { static: false }) networkContainer!: ElementRef;

    filterForm: FormGroup;
    filterOptions: FilterOptions = {
        universities: [],
        majors: [],
        countries: [],
        employmentStatuses: [],
        degreeLevels: [],
        connectionTypes: []
    };

    /**
     * Above this many students the force simulation is switched off and the graph is
     * drawn once, from the stabilised layout. Physics on a graph this size pins the
     * browser to 100% of a core for as long as the page is open, for a picture that
     * stopped being readable long before (review item P6). The nodes can still be
     * dragged; they simply no longer push each other about.
     */
    static readonly PHYSICS_NODE_LIMIT = 300;

    /** The id the print/export service captures the network panel from. */
    readonly printAreaId = 'student-tracer-print-area';

    networkGraph: NetworkGraph | null = null;
    network: Network | null = null;
    /** vis-network is loaded on demand so it lands in its own lazy chunk. */
    private visModule?: Promise<typeof import('vis-network/standalone')>;
    selectedNode: Node | null = null;
    sidebarVisible = false;
    loading = false;

    private readonly layoutOptionConfigs = [
        { labelKey: 'STUDENT_TRACER.LAYOUT_OPTIONS.FORCE_DIRECTED', value: 'forceAtlas2Based' },
        { labelKey: 'STUDENT_TRACER.LAYOUT_OPTIONS.HIERARCHICAL', value: 'hierarchical' },
        { labelKey: 'STUDENT_TRACER.LAYOUT_OPTIONS.RANDOM', value: 'random' }
    ];
    layoutOptions: { label: string; value: string }[] = [];
    selectedLayout = 'forceAtlas2Based';

    private readonly destroy$ = new Subject<void>();

    constructor(
        private readonly fb: FormBuilder,
        private readonly tracerService: StudentTracerService,
        private readonly messageService: MessageService,
        private readonly translation: TranslationService,
        private readonly reportCountryScope: ReportCountryScopeService,
        private readonly chartPrint: ChartPrintService,
        private readonly cdr: ChangeDetectorRef
    ) {
        this.filterForm = this.fb.group({
            university: [null],
            major: [null],
            country: [null],
            employmentStatus: [null],
            degreeLevel: [null],
            connectionType: [null],
            minConnectionStrength: [null]
        });
    }

    ngOnInit(): void {
        this.updateLayoutOptions();
        this.translation.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => this.updateLayoutOptions());

        this.loadFilterOptions();
        this.loadNetworkData();

        this.reportCountryScope.selectedCountryId$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => {
                this.loadFilterOptions();
                this.loadNetworkData(this.filterForm.value);
            });
    }

    ngOnDestroy(): void {
        if (this.network) {
            this.network.destroy();
        }
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadFilterOptions(): void {
        this.tracerService.getFilterOptions()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (options) => {
                    this.filterOptions = options;
                    this.cdr.markForCheck();
                },
                error: (err) => {
                    console.error('Failed to load filter options:', err);
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('STUDENT_TRACER.MESSAGES.LOAD_FILTERS_ERROR')
                    });
                }
            });
    }

    loadNetworkData(filter?: TracerFilter): void {
        this.loading = true;
        this.cdr.markForCheck();
        this.tracerService.getNetworkGraph(filter)
            .pipe(
                takeUntil(this.destroy$),
                finalize(() => {
                    this.loading = false;
                    this.cdr.markForCheck();
                })
            )
            .subscribe({
                next: (data) => {
                    this.networkGraph = data;
                    this.cdr.markForCheck();
                    void this.initializeNetwork();
                    this.messageService.add({
                        severity: 'success',
                        summary: this.translation.translate('COMMON.SUCCESS'),
                        detail: this.formatMessage('STUDENT_TRACER.MESSAGES.LOAD_SUCCESS', {
                            students: data.nodes.length,
                            connections: data.edges.length
                        })
                    });
                },
                error: (err) => {
                    console.error('Failed to load network data:', err);
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('STUDENT_TRACER.MESSAGES.LOAD_ERROR')
                    });
                }
            });
    }

    applyFilters(): void {
        const filter: TracerFilter = this.filterForm.value;
        this.loadNetworkData(filter);
    }

    clearFilters(): void {
        this.filterForm.reset();
        this.loadNetworkData();
    }

    changeLayout(): void {
        if (this.network) {
            const layout = this.selectedLayout === 'hierarchical' 
                ? { hierarchical: { direction: 'UD', sortMethod: 'directed' } }
                : { randomSeed: this.selectedLayout === 'random' ? Math.random() : undefined };
            
            this.network.setOptions({ layout });
        }
    }

    private loadVis() {
        return this.visModule ??= import('vis-network/standalone');
    }

    private async initializeNetwork(): Promise<void> {
        if (!this.networkGraph || !this.networkContainer) {
            return;
        }

        const { Network, DataSet } = await this.loadVis();
        // A newer search may have replaced the graph while the module was loading.
        if (!this.networkGraph || !this.networkContainer) {
            return;
        }

        const container = this.networkContainer.nativeElement;
        
        // Clean up edges: remove invalid properties that vis-network doesn't accept
        const cleanedEdges = this.networkGraph.edges.map(edge => {
            const cleaned: any = {
                id: edge.id,
                from: edge.from,
                to: edge.to
            };
            
            // Only add optional properties if they're valid
            if (edge.label) cleaned.label = edge.label;
            if (edge.title) cleaned.title = edge.title;
            if (edge.width) cleaned.width = edge.width;
            if (edge.color) cleaned.color = edge.color;
            
            // Ignore dashes property from backend as it's a string
            // vis-network will use default smooth curves
            
            return cleaned;
        });
        
        const nodes = new DataSet(this.networkGraph.nodes);
        const edges = new DataSet(cleanedEdges);

        const data = { nodes, edges };
        const options = {
            nodes: {
                shape: 'dot',
                size: 25,
                font: {
                    size: 14,
                    color: '#343434'
                },
                borderWidth: 2,
                borderWidthSelected: 4
            },
            edges: {
                width: 2,
                color: { inherit: 'both' },
                smooth: {
                    enabled: true,
                    type: 'continuous',
                    roundness: 0.5
                }
            },
            physics: {
                // Off for a large graph; see PHYSICS_NODE_LIMIT.
                enabled: this.physicsEnabled,
                forceAtlas2Based: {
                    gravitationalConstant: -26,
                    centralGravity: 0.005,
                    springLength: 230,
                    springConstant: 0.18
                },
                maxVelocity: 146,
                solver: 'forceAtlas2Based',
                timestep: 0.35,
                stabilization: { iterations: 150 }
            },
            interaction: {
                tooltipDelay: 200,
                hover: true,
                navigationButtons: true,
                keyboard: true
            },
            layout: {
                improvedLayout: true
            }
        };

        // Destroy existing network if any
        if (this.network) {
            this.network.destroy();
        }

        this.network = new Network(container, data, options);

        // Event listeners
        this.network.on('click', (params) => {
            if (params.nodes.length > 0) {
                const nodeId = params.nodes[0];
                this.onNodeClick(nodeId);
                
                // Focus/maximize on the clicked node with smooth animation
                this.network?.focus(nodeId, {
                    scale: 2.0,
                    offset: { x: 0, y: 0 },
                    animation: { 
                        duration: 800, 
                        easingFunction: 'easeInOutQuad' 
                    }
                });
            } else {
                // If clicked on empty space, fit all nodes
                this.network?.fit({
                    animation: { 
                        duration: 500, 
                        easingFunction: 'easeInOutQuad' 
                    }
                });
            }
        });

        this.network.on('doubleClick', (params) => {
            if (params.nodes.length > 0) {
                // Double-click: zoom in even more
                this.network?.focus(params.nodes[0], {
                    scale: 3.0,
                    animation: { duration: 1000, easingFunction: 'easeInOutQuad' }
                });
            }
        });
    }

    private onNodeClick(nodeId: number): void {
        const node = this.networkGraph?.nodes.find(n => n.id === nodeId);
        if (node) {
            this.selectedNode = node;
            this.sidebarVisible = true;
            this.cdr.markForCheck();
        }
    }

    closeSidebar(): void {
        this.sidebarVisible = false;
        this.selectedNode = null;
    }

    fitNetwork(): void {
        if (this.network) {
            this.network.fit({ animation: { duration: 1000, easingFunction: 'easeInOutQuad' } });
        }
    }

    resetZoom(): void {
        if (this.network) {
            this.network.moveTo({
                scale: 1,
                animation: { duration: 500, easingFunction: 'easeInOutQuad' }
            });
        }
    }

    /** True while the graph is small enough to keep the force simulation running. */
    get physicsEnabled(): boolean {
        const count = this.networkGraph?.nodes.length ?? 0;
        return count > 0 && count <= StudentTracerComponent.PHYSICS_NODE_LIMIT;
    }

    /** Set when the server capped the graph, so the page can say what is missing. */
    get truncatedNotice(): string | null {
        const graph = this.networkGraph;
        if (!graph?.truncated) {
            return null;
        }
        return this.formatMessage('STUDENT_TRACER.MESSAGES.TRUNCATED', {
            shown: graph.nodes.length,
            total: graph.matchedNodeCount ?? graph.nodes.length
        });
    }

    /**
     * Prints the network through the server's chart report, the way the student
     * graph and every chart page do.
     *
     * The tracer was the last page printing itself with a print stylesheet (review
     * item P6): the browser printed the page's DOM, which meant the canvas came out
     * blank or clipped and the masthead was whatever the screen layout happened to
     * be. Here the panel is rasterised and sent to the same Jasper chart report,
     * so the printed page matches every other report.
     */
    printNetwork(): void {
        if (!this.networkGraph?.nodes.length) {
            return;
        }
        void this.chartPrint.printChartsPdf(
            this.printAreaId,
            this.translation.translate('STUDENT_TRACER.TITLE'),
            { domCardSelector: '.network-container' }
        );
    }

    exportImage(): void {
        if (this.network && this.networkContainer) {
            const canvas = this.networkContainer.nativeElement.getElementsByTagName('canvas')[0];
            if (canvas) {
                const link = document.createElement('a');
                link.download = 'student-tracer-network.png';
                link.href = canvas.toDataURL();
                link.click();
            }
        }
    }

    get stats(): NetworkStats | null {
        return this.networkGraph?.stats || null;
    }

    getActiveFiltersCount(): string {
        const filters = this.filterForm.value;
        let count = 0;
        
        if (filters.university) count++;
        if (filters.major) count++;
        if (filters.country) count++;
        if (filters.employmentStatus) count++;
        if (filters.degreeLevel) count++;
        if (filters.connectionType) count++;
        if (filters.minConnectionStrength) count++;
        
        return count > 0 ? count.toString() : '';
    }

    private updateLayoutOptions(): void {
        this.layoutOptions = this.layoutOptionConfigs.map(option => ({
            value: option.value,
            label: this.translation.translate(option.labelKey)
        }));
        this.cdr.markForCheck();
    }

    private formatMessage(key: string, replacements: Record<string, string | number> = {}): string {
        let message = this.translation.translate(key);
        Object.entries(replacements).forEach(([placeholder, value]) => {
            message = message.replace(`{${placeholder}}`, String(value));
        });
        return message;
    }
}

