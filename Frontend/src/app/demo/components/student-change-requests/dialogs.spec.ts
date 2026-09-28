import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { providePrimeNG } from 'primeng/config';
import { StudentChangeRequestsComponent } from './student-change-requests.component';
import { StudentChangeRequestsModule } from './student-change-requests.module';
import { LookupService } from '../../service/lookup.service';
import { CodeListService } from '../../service/code-list.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * The two dialogs of the change requests page (25 Sep 2026): the review
 * (approve / reject / modify) rebuilt in the .dv-* vocabulary every other
 * record's Edit dialog uses, and a new read-only view of one request that can
 * hand straight over to a decision.
 *
 * <p>Dialogs land on <body> (providePrimeNG's overlayAppendTo, as in
 * app.module.ts), so they are read from the document, not the fixture.
 */
describe('StudentChangeRequests — review and view dialogs', () => {

    let fixture: ComponentFixture<StudentChangeRequestsComponent>;
    let component: StudentChangeRequestsComponent;

    const row = (value: string, label: string, uiSeverity: string | null, extra: string | null = null) =>
        ({ value, label, labelAr: label, labelEn: label, sortOrder: 1, uiSeverity, icon: null, isSystem: true, active: true, extra });

    const CODE_LISTS = {
        'change-request-status': [
            row('PENDING', 'Pending', 'warn'), row('APPROVED', 'Approved', 'success'),
            row('REJECTED', 'Rejected', 'danger'), row('MODIFIED', 'Modified', 'info')
        ],
        'change-request-field': [row('studentEmail', 'E-mail', null)]
    };

    const pending: any = {
        requestId: 12, studentId: 1003, studentName: 'QA Student', submissionId: 'SUB-9',
        fieldName: 'studentEmail', fieldType: 'TEXT', oldValue: 'old@example.org', newValue: 'new@example.org',
        status: 'PENDING', createdAt: '2026-09-20T09:00:00Z'
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentChangeRequestsModule, RouterModule.forRoot([])],
            providers: [
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: HttpClient,
                    useValue: {
                        get: (url: string) => url.includes('/code-lists') ? of(CODE_LISTS)
                            : url.includes('/change-requests') ? of({ content: [], totalElements: 0 })
                            : of([]),
                        post: () => of({})
                    }
                },
                { provide: LookupService, useValue: { getChangeRequestStatuses: () => of([]) } },
                { provide: UmsPermissionService, useValue: { getAllowedCountryIds: () => [] } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        TestBed.inject(CodeListService).reload().subscribe();

        fixture = TestBed.createComponent(StudentChangeRequestsComponent);
        component = fixture.componentInstance;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        component.showViewDialog = false;
        component.showReviewDialog = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const settle = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    /** The .p-dialog that holds the given element. */
    const dialogWith = (selector: string) =>
        document.querySelector(selector)?.closest('.p-dialog.dv-dialog') as HTMLElement;

    const footerButtons = (dialog: HTMLElement) =>
        Array.from(dialog.querySelectorAll('.dv-footer__actions button')).map(b => b.textContent?.trim() ?? '');

    it('the row offers a View button for every request', () => {
        component.requests = [pending, { ...pending, requestId: 13, status: 'APPROVED' }];
        fixture.detectChanges();
        const eyes = fixture.nativeElement.querySelectorAll('td .pi-eye');
        expect(eyes.length).toBe(2);
    });

    it('view: a pending request in the shared vocabulary, with the three decisions in the footer', async () => {
        component.openView(pending);
        await settle();

        const dialog = dialogWith('.dv-kpis');
        expect(dialog).withContext('view dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog)).toBeFalse();
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--warn');
        expect(dialog.querySelector('.dv-hero__badge')?.textContent).toContain('12');
        expect(dialog.querySelector('.dv-hero__eyebrow')?.textContent).toContain('E-mail');
        expect(dialog.querySelector('.dv-hero__title')?.textContent).toContain('QA Student');
        expect(dialog.querySelectorAll('.dv-kpi').length).toBe(3);
        expect(dialog.querySelector('.dv-dl__blue')?.textContent).toContain('new@example.org');
        expect(dialog.querySelector('.dv-notice--warn')).withContext('awaiting review').toBeTruthy();
        expect(footerButtons(dialog)).toEqual([
            'COMMON.CLOSE',
            'STUDENT_CHANGE_REQUESTS.APPROVE',
            'STUDENT_CHANGE_REQUESTS.REJECT',
            'STUDENT_CHANGE_REQUESTS.MODIFY_APPROVE'
        ]);
    });

    it('view: a modified request shows the decision and only Close', async () => {
        component.openView({
            ...pending, requestId: 14, status: 'MODIFIED', appliedValue: 'fixed@example.org',
            reviewedByName: 'Officer', reviewedAt: '2026-09-21T10:00:00Z', employeeComment: 'Typo corrected.'
        });
        await settle();

        const dialog = dialogWith('.dv-kpis');
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--info');
        expect(dialog.querySelector('.dv-dl__green')?.textContent).toContain('fixed@example.org');
        expect(dialog.textContent).toContain('Officer');
        expect(dialog.querySelector('.dv-notes')?.textContent).toContain('Typo corrected.');
        expect(footerButtons(dialog)).toEqual(['COMMON.CLOSE']);
    });

    it('review: reject asks for the reason and confirms in red', async () => {
        component.openReview(pending, 'reject');
        await settle();

        const dialog = dialogWith('#scrReason');
        expect(dialog).withContext('review dialog').toBeTruthy();
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--danger');
        expect(dialog.querySelector('.dv-notice--danger')).toBeTruthy();
        expect(dialog.querySelector('#scrApply')).toBeNull();
        expect(dialog.querySelector('#scrComment')).toBeNull();
        const confirm = dialog.querySelectorAll('.dv-footer__actions button')[1] as HTMLElement;
        expect(confirm.className).toContain('p-button-danger');

        component.submitReview();
        await settle();
        expect(dialog.querySelector('.dv-error')).withContext('an empty reason is marked').toBeTruthy();
    });

    it('review: modify asks for the value to apply and an optional comment', async () => {
        component.openReview(pending, 'modify');
        await settle();

        const dialog = dialogWith('#scrApply');
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--warn');
        expect(dialog.querySelector('#scrComment')).toBeTruthy();
        expect((dialog.querySelector('#scrApply') as HTMLInputElement).value).toBe('new@example.org');
    });

    it('the view hands over to a decision on the same request', async () => {
        component.openView(pending);
        await settle();
        component.reviewFromView('approve');
        await settle();

        expect(component.showViewDialog).toBeFalse();
        expect(component.showReviewDialog).toBeTrue();
        expect(component.reviewMode).toBe('approve');
        expect(component.selectedRequest).toBe(pending);
        expect(dialogWith('#scrComment')?.querySelector('.dv-notice--success')).toBeTruthy();
    });
});
