import { NgZone } from '@angular/core';
import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';

import { ReportPdfModalComponent } from './report-pdf-modal.component';
import { ReportPdfModalService } from 'src/app/demo/service/report-pdf-modal.service';
import { TranslationService } from 'src/app/demo/service/translation.service';

/**
 * The report viewer's open path.
 *
 * <p>These assertions exist because of note G49: "if one report fails, every
 * report after it fails to generate, preview or download". The report engine was
 * blamed, but the engine was only ever half of it — the other half was this
 * component, where a request that arrived while the dialog was open-but-not-yet-
 * shown was recorded in a flag and dropped. Nothing cleared that state except the
 * dialog's own `onShow` animation callback, so once it was missed the viewer was
 * shut for the rest of the session and every later Preview and Print did nothing
 * at all — no viewer, no error, no toast.
 *
 * <p>What is pinned here is the pair of rules that replaced it: a request always
 * reaches the viewer, and readiness never depends on a callback arriving. The
 * tests are written against the component directly rather than a rendered
 * PrimeNG dialog on purpose — the bug was in this state machine, and a test that
 * drove the real dialog would only prove that the real dialog fires `onShow`,
 * which is the one thing that cannot be relied upon.
 */
describe('ReportPdfModalComponent — a request is never swallowed', () => {

    let service: ReportPdfModalService;
    let component: ReportPdfModalComponent;

    /** Runs callbacks straight through, so `tick()` still drives the watchdog. */
    const zone = {
        run: <T>(fn: () => T) => fn(),
        runOutsideAngular: <T>(fn: () => T) => fn()
    } as NgZone;

    const translation = {
        currentLanguage$: of('en'),
        isRTL: () => false,
        translate: (k: string) => k,
        getCurrentLanguage: () => 'en'
    } as unknown as TranslationService;

    beforeEach(() => {
        service = new ReportPdfModalService();
        component = new ReportPdfModalComponent(service, zone, translation);
        component.ngOnInit();
    });

    afterEach(() => component.ngOnDestroy());

    const request = (filename: string) => ({
        pdfApiPath: `/api/reports/${filename}/doc`,
        pdfQuery: { lang: 'ar' },
        filename: `${filename}.pdf`
    });

    it('mounts the viewer once the dialog reports it has shown', () => {
        service.open(request('monthly'));
        expect(component.visible).withContext('dialog asked to open').toBeTrue();
        expect(component.viewerKey).withContext('nothing mounted before onShow').toBe(0);

        component.onDialogShow();

        expect(component.dialogReady).toBeTrue();
        expect(component.viewerKey).withContext('viewer mounted').toBe(1);
        expect(component.pdfApiPath).toBe('/api/reports/monthly/doc');
    });

    it('reloads the viewer for a second report while the dialog stays open', () => {
        service.open(request('monthly'));
        component.onDialogShow();
        const first = component.viewerKey;

        service.open(request('quarterly'));

        expect(component.viewerKey).withContext('a new load, not the old document').toBe(first + 1);
        expect(component.pdfApiPath).toBe('/api/reports/quarterly/doc');
        expect(component.filename).toBe('quarterly.pdf');
    });

    // The regression itself. Before the fix this left viewerKey at 0 forever.
    it('mounts anyway when onShow never arrives', fakeAsync(() => {
        service.open(request('monthly'));
        expect(component.viewerKey).toBe(0);

        tick(1200);

        expect(component.dialogReady).withContext('watchdog forced readiness').toBeTrue();
        expect(component.viewerKey).withContext('viewer mounted without onShow').toBe(1);
    }));

    it('serves a later report after an open that never showed', fakeAsync(() => {
        // The exact shape of G49: one report opens and never renders, and then
        // every report the user asks for afterwards is expected to still work.
        service.open(request('broken'));
        tick(1200);

        service.open(request('student-list'));

        expect(component.pdfApiPath).withContext('newest request won').toBe('/api/reports/student-list/doc');
        expect(component.viewerKey).withContext('and it was handed to the viewer').toBe(2);
        component.onDialogShow();
        expect(component.viewerKey).withContext('onShow arriving late mounts nothing extra').toBe(2);
    }));

    it('keeps serving reports after the dialog is closed and reopened', fakeAsync(() => {
        service.open(request('monthly'));
        component.onDialogShow();
        component.onDialogHide();

        expect(component.viewerKey).withContext('state cleared on close').toBe(0);
        expect(component.pdfApiPath).toBeNull();

        service.open(request('annual'));
        component.onDialogShow();

        expect(component.visible).toBeTrue();
        expect(component.viewerKey).toBe(1);
        expect(component.pdfApiPath).toBe('/api/reports/annual/doc');
        tick(1200);
    }));

    it('carries the print flag through to the viewer', () => {
        service.open({ ...request('monthly'), print: true });
        component.onDialogShow();

        expect(component.printOnLoad).toBeTrue();

        service.open(request('monthly'));
        expect(component.printOnLoad).withContext('a plain preview does not inherit it').toBeFalse();
    });

    it('drops the watchdog when the dialog closes', fakeAsync(() => {
        service.open(request('monthly'));
        component.onDialogHide();

        tick(1200);

        expect(component.viewerKey).withContext('no ghost mount after close').toBe(0);
        expect(component.visible).toBeFalse();
    }));
});
