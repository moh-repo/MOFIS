import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { PaymentAuthorizationModule } from './payment-authorization.module';
import { PaymentAuthorizationComponent } from './payment-authorization.component';
import { PaymentAuthReportComponent } from './payment-auth-report/payment-auth-report.component';

import { PaymentAuthService } from '../../service/payment-auth.service';
import { ReportPdfService } from '../../service/report-pdf.service';
import { LookupService } from '../../service/lookup.service';
import { StudentPickerService } from '../../service/student-picker.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * Payment authorisations, printed from Jasper instead of from the browser.
 *
 * <p>The page used to render an HTML replica of نموذج رقم ١١ حسابات and call
 * `window.print()` — the row's Print button did it behind a 400 ms timeout, and the
 * routed report page's View, Print and Download PDF buttons were all the same call.
 * A replica cannot show the masthead, the ministry logo or the footer sentence,
 * because those live in `REPORT_TYPE_SETTINGS` and only the server's rendering reads
 * them; so what a user printed was never quite the statutory form.
 *
 * <p>What is pinned here is that the page now asks the server for its documents: the
 * voucher endpoint for one record, the list endpoint for the table, both through the
 * shared viewer that 47 other report pages use.
 */
describe('Payment authorisation — Jasper documents', () => {

    const RECORD = {
        id: 7, authFormNumber: 'PA-2024-007', authStatus: 'APPROVED',
        studentId: 1, studentName: 'Student One'
    };

    let reportPdf: jasmine.SpyObj<ReportPdfService>;

    const translation = {
        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
    };

    const lookup = {
        getPaymentAmountSources: () => of([]),
        getPaymentAuthStatuses: () => of([
            { label: 'Approved', value: 'APPROVED' },
            { label: 'Paid', value: 'PAID' }
        ]),
        getReportLangOptions: () => [
            { label: 'AR', value: 'ar' },
            { label: 'EN', value: 'en' }
        ]
    };

    const providers = (routeId: number | null) => [
        MessageService,
        ConfirmationService,
        { provide: ReportPdfService, useValue: reportPdf },
        {
            provide: PaymentAuthService,
            useValue: {
                getAll: () => of({ content: [RECORD], totalElements: 1 }),
                getById: () => of(RECORD)
            }
        },
        { provide: HttpClient, useValue: { get: () => of([]) } },
        { provide: LookupService, useValue: lookup },
        { provide: StudentPickerService, useValue: { search: () => of([]), merge: () => [], toOptions: () => [] } },
        { provide: TranslationService, useValue: translation },
        {
            provide: UmsPermissionService,
            useValue: new Proxy({} as any, {
                get: (_t, prop) => prop === 'getAllowedCountryIds' ? () => [] : () => true
            })
        },
        {
            provide: ActivatedRoute,
            useValue: { params: of(routeId === null ? {} : { id: String(routeId) }) }
        }
    ];

    beforeEach(() => {
        reportPdf = jasmine.createSpyObj<ReportPdfService>('ReportPdfService',
            ['previewPdf', 'downloadPdf', 'printPdf', 'exportExcel', 'toExcelApiPath', 'fetchPdfBlob',
             'toastPdfError']);
        reportPdf.fetchPdfBlob.and.returnValue(of(new Blob()));
    });

    // ── The list page ────────────────────────────────────────────────────────

    describe('the list', () => {

        let fixture: ComponentFixture<PaymentAuthorizationComponent>;
        let component: PaymentAuthorizationComponent;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [PaymentAuthorizationModule, RouterModule.forRoot([])],
                providers: providers(null)
            }).compileComponents();

            fixture = TestBed.createComponent(PaymentAuthorizationComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('prints a row through the server, not through the browser', () => {
            const printSpy = spyOn(window, 'print');

            component.openPrint(RECORD as any);

            expect(reportPdf.printPdf).toHaveBeenCalled();
            expect(printSpy).withContext('window.print is no longer how this page prints')
                .not.toHaveBeenCalled();
        });

        it('asks for that record’s own voucher', () => {
            component.openPrint(RECORD as any);

            const [path, query, filename] = reportPdf.printPdf.calls.mostRecent().args;
            expect(path).toBe('/api/v1/paymentAuth/7/export-report');
            // The stubbed UI language is English, and the document follows it.
            expect(query).toEqual({ lang: 'en' });
            expect(filename).toBe('payment-auth-PA-2024-007.pdf');
        });

        it('renders in the language being read, without anyone choosing', () => {
            // The masthead and the footer sentence are stored in both languages; an
            // English reader should not have to switch to get an English document.
            expect(component.reportLang).toBe('en');
        });

        it('closes the view dialog before printing, rather than printing behind it', () => {
            component.viewVisible = true;

            component.openPrint(RECORD as any);

            expect(component.viewVisible).toBeFalse();
        });

        it('offers the list report the page could not reach before', () => {
            expect(component.listReportApiPath).toBe('/api/v1/paymentAuth/export-report');
            expect(fixture.nativeElement.querySelector('app-report-actions'))
                .withContext('no report action bar on the list page').toBeTruthy();
        });

        it('sends the status filter on screen to the list report', () => {
            component.selectedStatusFilter = 'APPROVED';

            expect(component.listReportQuery()).toEqual({ lang: 'en', status: 'APPROVED' });
        });

        it('sends no status at all when the filter is cleared', () => {
            // "All statuses" is the absence of a filter, not an empty one the server
            // would have to interpret.
            component.selectedStatusFilter = null;

            expect(component.listReportQuery()).toEqual({ lang: 'en' });
        });

        it('renders the document in the language chosen for it', () => {
            component.reportLang = 'ar';

            expect(component.listReportQuery()).toEqual({ lang: 'ar' });
        });

        it('has no HTML replica of the form left anywhere on the page', () => {
            expect(fixture.nativeElement.querySelector('app-payment-auth-voucher')).toBeNull();
        });
    });

    // ── The routed report page ───────────────────────────────────────────────

    describe('one record', () => {

        let fixture: ComponentFixture<PaymentAuthReportComponent>;
        let component: PaymentAuthReportComponent;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [PaymentAuthorizationModule, RouterModule.forRoot([])],
                providers: providers(7)
            }).compileComponents();

            fixture = TestBed.createComponent(PaymentAuthReportComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        });

        it('shows the server’s document, not a replica of it', () => {
            expect(fixture.nativeElement.querySelector('app-report-viewer')).toBeTruthy();
            expect(fixture.nativeElement.querySelector('app-payment-auth-voucher')).toBeNull();
        });

        it('points the viewer at this record’s voucher', () => {
            expect(component.pdfApiPath).toBe('/api/v1/paymentAuth/7/export-report');
            expect(component.loadTrigger).toBeGreaterThan(0);
        });

        it('gives the action bar the same endpoint the viewer is showing', () => {
            // Otherwise Download and Print would hand back a different document from
            // the one on screen.
            expect(component.voucherApiPath).toBe(component.pdfApiPath);
            expect(fixture.nativeElement.querySelector('app-report-actions')).toBeTruthy();
        });

        it('re-renders when the document language changes', () => {
            const before = component.loadTrigger;
            component.selectedLang = 'en';

            component.onLangChange();

            expect(component.loadTrigger).toBeGreaterThan(before);
            expect(component.pdfQuery).toEqual({ lang: 'en' });
        });

        it('names the download after the form number', () => {
            expect(component.filename).toBe('payment-auth-PA-2024-007.pdf');
        });

        it('asks for the document before the record has loaded', () => {
            // The server reads the record again when it renders, so the viewer does
            // not wait on the row this page only needs for its heading.
            expect(component.pdfApiPath).toBeTruthy();
        });
    });
});
