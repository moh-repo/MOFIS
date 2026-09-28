import {
    ChangeDetectorRef,
    Component,
    ElementRef,
    Input,
    OnChanges,
    OnDestroy,
    SimpleChanges,
    ViewChild
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ReportPdfQuery, ReportPdfService } from 'src/app/demo/service/report-pdf.service';
import { ReportPdfModalService } from 'src/app/demo/service/report-pdf-modal.service';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { Subscription } from 'rxjs';
import { destroyEmbedViewer, mountReportPdf, printMountedPdf } from '../embedpdf/embedpdf-viewer.util';

@Component({
    selector: 'app-report-viewer',
    templateUrl: './report-viewer.component.html',
    styleUrls: ['./report-viewer.component.scss'],
    standalone: false
})
export class ReportViewerComponent implements OnChanges, OnDestroy {

    /** Increment to trigger a fresh PDF load from pdfApiPath + pdfQuery. */
    @Input() loadTrigger = 0;
    @Input() pdfApiPath: string | null = null;
    @Input() pdfQuery: ReportPdfQuery | null = null;
    /** Present for a report the server renders from what the browser sends (the chart pages). */
    @Input() pdfBody: unknown = null;
    @Input() pdfBlob: Blob | null = null;
    /** @deprecated Prefer loadTrigger + pdfApiPath + pdfQuery. Still supported for existing report pages. */
    @Input() pdfUrl: string | null = null;
    @Input() loading = false;
    @Input() filename = 'report.pdf';
    /** When true (default), PDF opens in a PrimeNG dialog instead of inline. */
    @Input() useModal = true;
    /** Raise the print dialog once the document has mounted (used by the Print action). */
    @Input() printOnLoad = false;
    @Input() viewerHeight = '76vh';

    @ViewChild('pdfContainer') pdfContainer?: ElementRef<HTMLElement>;

    pdfSrc: Uint8Array | undefined;
    internalLoading = false;
    loadError = false;

    private fetchSub?: Subscription;
    private viewerMountGeneration = 0;

    constructor(
        private http: HttpClient,
        private reportPdfService: ReportPdfService,
        private reportPdfModalService: ReportPdfModalService,
        private messageService: MessageService,
        private translationService: TranslationService,
        private cdr: ChangeDetectorRef
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (this.useModal) {
            if (changes['loadTrigger'] && this.loadTrigger > 0 && this.pdfApiPath && this.pdfQuery) {
                this.reportPdfModalService.open({
                    pdfApiPath: this.pdfApiPath,
                    pdfQuery: this.pdfQuery,
                    pdfBody: this.pdfBody,
                    filename: this.filename
                });
            }
            if (changes['pdfBlob']?.currentValue instanceof Blob) {
                this.reportPdfModalService.open({
                    pdfBlob: changes['pdfBlob'].currentValue as Blob,
                    filename: this.filename
                });
            }
            return;
        }

        if (changes['pdfBlob']) {
            const blob = changes['pdfBlob'].currentValue as Blob | null;
            if (blob) {
                this.applyBlob(blob);
            } else if (!this.pdfApiPath) {
                this.clearPdf();
            }
        }
        if (changes['pdfUrl']) {
            const url = changes['pdfUrl'].currentValue as string | null;
            if (url) {
                this.fetchFromUrl(url);
            } else if (!this.pdfApiPath && !this.pdfBlob) {
                this.clearPdf();
            }
        }
        if (changes['loadTrigger'] && this.loadTrigger > 0 && this.pdfApiPath && this.pdfQuery) {
            this.fetchFromApi();
        }
        if (changes['loading'] && this.loading) {
            this.cancelPendingMount();
        }
    }

    ngOnDestroy(): void {
        this.fetchSub?.unsubscribe();
        this.cancelPendingMount();
        if (this.pdfContainer?.nativeElement) {
            destroyEmbedViewer(this.pdfContainer.nativeElement);
        }
    }

    get showLoadingOverlay(): boolean {
        return this.loading || this.internalLoading;
    }

    get showPlaceholder(): boolean {
        return !this.showLoadingOverlay && !this.pdfSrc && !this.loadError;
    }

    get showPdfContainer(): boolean {
        return !!this.pdfSrc && !this.showLoadingOverlay && !this.loadError;
    }

    private fetchFromUrl(url: string): void {
        this.fetchSub?.unsubscribe();
        this.loadError = false;
        this.clearPdf();
        this.internalLoading = true;
        this.fetchSub = this.http.get(url, { responseType: 'blob' }).subscribe({
            next: (blob) => this.handleFetchedBlob(blob),
            error: (error) => this.handleFetchError(error)
        });
    }

    private fetchFromApi(): void {
        this.fetchSub?.unsubscribe();
        this.loadError = false;
        this.clearPdf();
        this.internalLoading = true;
        this.fetchSub = this.reportPdfService.fetchPdfBlob(this.pdfApiPath!, this.pdfQuery!, this.pdfBody).subscribe({
            next: (blob) => this.handleFetchedBlob(blob),
            error: (error) => this.handleFetchError(error)
        });
    }

    private handleFetchedBlob(blob: Blob): void {
        if (!blob?.size || blob.type.includes('json')) {
            this.internalLoading = false;
            this.loadError = true;
            // A JSON body where a PDF was expected is the server explaining itself
            // (a row cap, a busy renderer). Hand it over to be read rather than
            // reporting the generic "could not load" over the top of it (P7).
            this.reportPdfService.toastPdfError(blob?.size ? { status: 200, error: blob } : undefined);
            this.cdr.detectChanges();
            return;
        }
        this.applyBlob(blob);
    }

    private handleFetchError(error?: unknown): void {
        this.internalLoading = false;
        this.loadError = true;
        this.reportPdfService.toastPdfError(error);
        this.cdr.detectChanges();
    }

    private applyBlob(blob: Blob): void {
        blob.arrayBuffer().then(buf => {
            this.pdfSrc = new Uint8Array(buf);
            this.internalLoading = false;
            this.loadError = false;
            this.cdr.detectChanges();
            this.scheduleViewerMount();
        }).catch(() => {
            this.internalLoading = false;
            this.loadError = true;
            this.toastLoadError();
            this.cdr.detectChanges();
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
        // Wait for Angular to render the container and for dialog layout to settle.
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                window.setTimeout(() => {
                    void this.mountViewer(generation);
                }, 50);
            });
        });
    }

    private async mountViewer(generation: number): Promise<void> {
        if (generation !== this.viewerMountGeneration) {
            return;
        }

        const container = this.pdfContainer?.nativeElement;
        const pdfData = this.pdfSrc;
        if (!container || !pdfData || this.showLoadingOverlay || this.loadError) {
            return;
        }

        try {
            await mountReportPdf(container, pdfData, this.filename, this.viewerHeight, generation);
            if (generation !== this.viewerMountGeneration) {
                destroyEmbedViewer(container);
                return;
            }
            if (this.printOnLoad) {
                // The Print button opens this viewer purely to print from it, so the
                // print dialog is raised as soon as the document is actually mounted —
                // asking earlier prints an empty viewer.
                //
                // Its own try/catch: the document is on screen by now, so a failure
                // here is the printer step, not the report. Reporting it as "could
                // not load the PDF" (review item P5) sent people to re-run a report
                // that had in fact rendered, and hid the viewer they could have
                // printed from by hand.
                try {
                    await printMountedPdf(container);
                } catch (printErr) {
                    console.error('EmbedPDF print failed', printErr);
                    this.messageService.add({
                        severity: 'warn',
                        summary: this.translationService.translate('COMMON.WARNING'),
                        detail: this.translationService.translate('REPORTS.VIEWER.PRINT_FAILED')
                            || 'Could not open the print dialog. Use the print button in the viewer toolbar.'
                    });
                }
            }
        } catch (err) {
            console.error('EmbedPDF mount failed', err);
            if (generation === this.viewerMountGeneration) {
                this.loadError = true;
                // The report itself arrived; this is the engine failing to display it,
                // and a timeout (P2) is worth saying plainly — "please try again" is
                // the only useful advice either way, but not the same sentence.
                const timedOut = err instanceof Error && err.message.includes('timed out');
                this.toastLoadError(timedOut ? 'REPORTS.VIEWER.OPEN_TIMEOUT' : 'REPORTS.VIEWER.LOAD_FAILED');
                this.cdr.detectChanges();
            }
        }
    }

    private toastLoadError(key = 'REPORTS.VIEWER.LOAD_FAILED'): void {
        const detail = this.translationService.translate(key);
        this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: detail && detail !== key ? detail : 'Could not load the PDF report. Please try again.'
        });
    }
}
