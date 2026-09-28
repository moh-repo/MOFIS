import {
    ChangeDetectorRef,
    Component,
    ElementRef,
    OnDestroy,
    OnInit,
    ViewChild
} from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { ReportPdfQuery, ReportPdfService } from 'src/app/demo/service/report-pdf.service';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { Subscription } from 'rxjs';
import { destroyEmbedViewer, mountReportPdf } from 'src/app/demo/shared/embedpdf/embedpdf-viewer.util';

@Component({
    selector: 'app-report-pdf-preview',
    imports: [ProgressSpinnerModule],
    templateUrl: './report-pdf-preview.component.html',
    styleUrls: ['./report-pdf-preview.component.scss']
})
export class ReportPdfPreviewComponent implements OnInit, OnDestroy {

    @ViewChild('pdfContainer') pdfContainer?: ElementRef<HTMLElement>;

    pdfSrc: Uint8Array | undefined;
    loading = true;
    error = false;
    filename = 'report.pdf';
    private sub?: Subscription;
    private fetchSub?: Subscription;
    private viewerMountGeneration = 0;

    constructor(
        private route: ActivatedRoute,
        private reportPdfService: ReportPdfService,
        public translationService: TranslationService,
        private cdr: ChangeDetectorRef
    ) {}

    ngOnInit(): void {
        this.sub = this.route.queryParamMap.subscribe(params => {
            const apiPath = params.get('apiPath');
            if (!apiPath) {
                this.error = true;
                this.loading = false;
                return;
            }
            this.filename = params.get('filename') || 'report.pdf';
            const skipKeys = new Set(['apiPath', 'filename']);
            const query: ReportPdfQuery = {};
            for (const key of params.keys) {
                if (skipKeys.has(key)) {
                    continue;
                }
                const vals = params.getAll(key);
                if (vals.length > 1) {
                    query[key] = vals;
                } else if (vals.length === 1) {
                    query[key] = vals[0];
                }
            }
            if (!query['disposition']) {
                query['disposition'] = 'inline';
            }
            this.loadPdf(decodeURIComponent(apiPath), query);
        });
    }

    ngOnDestroy(): void {
        this.sub?.unsubscribe();
        this.fetchSub?.unsubscribe();
        this.cancelPendingMount();
    }

    get showPdfContainer(): boolean {
        return !!this.pdfSrc && !this.loading && !this.error;
    }

    private loadPdf(apiPath: string, query: ReportPdfQuery): void {
        this.loading = true;
        this.error = false;
        this.clearPdf();
        this.fetchSub?.unsubscribe();
        this.fetchSub = this.reportPdfService.fetchPdfBlob(apiPath, query).subscribe({
            next: (blob) => {
                if (!blob?.size || blob.type.includes('json')) {
                    this.loading = false;
                    this.error = true;
                    this.cdr.detectChanges();
                    return;
                }
                blob.arrayBuffer().then(buf => {
                    this.pdfSrc = new Uint8Array(buf);
                    this.loading = false;
                    this.cdr.detectChanges();
                    this.scheduleViewerMount();
                }).catch(() => {
                    this.loading = false;
                    this.error = true;
                    this.cdr.detectChanges();
                });
            },
            error: () => {
                this.loading = false;
                this.error = true;
                this.cdr.detectChanges();
            }
        });
    }

    private clearPdf(): void {
        this.pdfSrc = undefined;
        this.cancelPendingMount();
    }

    private cancelPendingMount(): void {
        this.viewerMountGeneration++;
        if (this.pdfContainer?.nativeElement) {
            destroyEmbedViewer(this.pdfContainer.nativeElement);
        }
    }

    private scheduleViewerMount(): void {
        const generation = ++this.viewerMountGeneration;
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                void this.mountViewer(generation);
            });
        });
    }

    private async mountViewer(generation: number): Promise<void> {
        if (generation !== this.viewerMountGeneration) {
            return;
        }

        const container = this.pdfContainer?.nativeElement;
        const pdfData = this.pdfSrc;
        if (!container || !pdfData || this.loading || this.error) {
            return;
        }

        try {
            await mountReportPdf(container, pdfData, this.filename, '100vh', generation);
            if (generation !== this.viewerMountGeneration) {
                destroyEmbedViewer(container);
            }
        } catch {
            if (generation === this.viewerMountGeneration) {
                this.error = true;
                this.cdr.detectChanges();
            }
        }
    }
}
