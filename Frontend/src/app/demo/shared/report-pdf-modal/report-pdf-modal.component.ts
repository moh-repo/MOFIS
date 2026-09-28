import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ReportPdfModalRequest, ReportPdfModalService } from 'src/app/demo/service/report-pdf-modal.service';
import { ReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { TranslationService } from 'src/app/demo/service/translation.service';

/**
 * The one viewer every report opens into.
 *
 * <p>The dialog is mounted once, in the application shell, and every Preview and
 * Print in the system routes a request to it. That makes it a single point of
 * failure, and it used to fail in the worst way available to a single point of
 * failure: silently, and permanently.
 *
 * <p>`openRequest` had three branches, and the middle one — already visible, not
 * yet shown — recorded the request and returned without rendering anything. The
 * only thing that could clear that state was `onShow`, and `onShow` is an
 * animation callback. Whenever it did not arrive (a route change while the
 * dialog was opening, a dismissed animation, an exception between `visible` and
 * the enter transition), `visible` stayed true and `dialogReady` stayed false —
 * so every later Preview and Print in the session took that branch, set a flag,
 * and returned. No viewer, no error, no toast. One report failed and then every
 * report after it did nothing at all, which is exactly what the ministry
 * reported in note G49.
 *
 * <p>Two rules now hold instead. A request is never swallowed: it always lands
 * on the fields and always asks for a fresh load. And readiness is never trusted
 * to arrive on its own — {@link READY_TIMEOUT_MS} after the dialog is asked to
 * open, the viewer mounts whether or not `onShow` ever fired. The viewer already
 * waits two frames and a beat for layout before mounting EmbedPDF, so the
 * fallback path renders correctly; it is only slower to settle than the
 * animation callback would have been.
 */
@Component({
    selector: 'app-report-pdf-modal',
    templateUrl: './report-pdf-modal.component.html',
    styleUrls: ['./report-pdf-modal.component.scss'],
    standalone: false
})
export class ReportPdfModalComponent implements OnInit, OnDestroy {

    /**
     * How long to wait for the dialog's own `onShow` before mounting anyway.
     *
     * <p>Comfortably longer than the PrimeNG enter transition (~150ms) so the
     * animation callback wins the race in every normal open, and short enough
     * that a reader who hits the abnormal path sees the report rather than
     * wondering whether the button worked.
     */
    private static readonly READY_TIMEOUT_MS = 1200;

    visible = false;
    title = '';
    filename = 'report.pdf';
    pdfApiPath: string | null = null;
    pdfQuery: ReportPdfQuery | null = null;
    pdfBody: unknown = null;
    pdfBlob: Blob | null = null;
    viewerKey = 0;
    dialogReady = false;
    printOnLoad = false;

    private openSub?: Subscription;
    private pendingRefresh = false;
    private readyTimer?: ReturnType<typeof setTimeout>;

    constructor(
        private reportPdfModalService: ReportPdfModalService,
        private zone: NgZone,
        public translationService: TranslationService
    ) {}

    ngOnInit(): void {
        this.openSub = this.reportPdfModalService.open$.subscribe((request) => this.openRequest(request));
    }

    ngOnDestroy(): void {
        this.openSub?.unsubscribe();
        this.clearReadyTimer();
    }

    onDialogShow(): void {
        this.clearReadyTimer();
        this.markReady();
    }

    onDialogHide(): void {
        this.clearReadyTimer();
        this.visible = false;
        this.dialogReady = false;
        this.viewerKey = 0;
        this.pdfApiPath = null;
        this.pdfQuery = null;
        this.pdfBody = null;
        this.pdfBlob = null;
        this.printOnLoad = false;
        this.pendingRefresh = false;
    }

    private openRequest(request: ReportPdfModalRequest): void {
        this.title = request.title
            || request.filename
            || this.translationService.translate('REPORTS.VIEWER.MODAL_TITLE')
            || 'Report Preview';
        this.filename = request.filename || 'report.pdf';
        this.pdfApiPath = request.pdfApiPath ?? null;
        this.pdfQuery = request.pdfQuery ?? null;
        this.pdfBody = request.pdfBody ?? null;
        this.pdfBlob = request.pdfBlob ?? null;
        this.printOnLoad = request.print === true;
        this.pendingRefresh = true;

        // Asking an already-open dialog to open again is a no-op for PrimeNG, so
        // this is safe to set unconditionally — and setting it unconditionally is
        // what stops a stale `visible` from locking the viewer shut.
        this.visible = true;

        if (this.dialogReady) {
            this.markReady();
            return;
        }
        this.startReadyTimer();
    }

    /** Hands the newest request to the viewer, at most once per request. */
    private markReady(): void {
        this.dialogReady = true;
        if (this.pendingRefresh || this.viewerKey === 0) {
            this.viewerKey++;
            this.pendingRefresh = false;
        }
    }

    /**
     * The watchdog. Outside Angular's zone so a pending timer cannot hold a test's
     * `whenStable` open, then back inside to touch the fields the template reads.
     */
    private startReadyTimer(): void {
        this.clearReadyTimer();
        this.zone.runOutsideAngular(() => {
            this.readyTimer = setTimeout(() => {
                this.zone.run(() => {
                    this.readyTimer = undefined;
                    if (this.visible && !this.dialogReady) {
                        this.markReady();
                    }
                });
            }, ReportPdfModalComponent.READY_TIMEOUT_MS);
        });
    }

    private clearReadyTimer(): void {
        if (this.readyTimer !== undefined) {
            clearTimeout(this.readyTimer);
            this.readyTimer = undefined;
        }
    }
}
