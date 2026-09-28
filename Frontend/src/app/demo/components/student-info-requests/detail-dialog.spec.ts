import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { StudentInfoRequestsComponent } from './student-info-requests.component';
import { StudentInfoRequestsModule } from './student-info-requests.module';
import { StudentInfoRequestDto, StudentInfoRequestService } from '../../service/student-info-request.service';
import { StudentdataService } from '../../service/studentdata.service';
import { LookupService } from '../../service/lookup.service';
import { CodeListService } from '../../service/code-list.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * The request detail dialog (25 Sep 2026): laid out with the .dv-* vocabulary
 * every other record's View dialog uses, with the actions in the footer and
 * only the ones the request's state allows.
 *
 * <p>The dialog lands on <body> (providePrimeNG's overlayAppendTo, as in
 * app.module.ts), so it is read from the document, not the fixture.
 */
describe('StudentInfoRequests — the request detail dialog', () => {

    let fixture: ComponentFixture<StudentInfoRequestsComponent>;
    let component: StudentInfoRequestsComponent;
    let current: StudentInfoRequestDto;

    const STATUS_LIST = [
        { value: 'PENDING', label: 'Pending', labelAr: 'قيد الاستجابة', labelEn: 'Pending', sortOrder: 1, uiSeverity: 'warn', icon: 'pi-clock', isSystem: true, active: true, extra: null },
        { value: 'OVERDUE', label: 'Overdue', labelAr: 'متأخر', labelEn: 'Overdue', sortOrder: 2, uiSeverity: 'danger', icon: 'pi-exclamation-triangle', isSystem: true, active: true, extra: null },
        { value: 'RESPONDED', label: 'Responded', labelAr: 'تم الرد', labelEn: 'Responded', sortOrder: 3, uiSeverity: 'success', icon: 'pi-check', isSystem: true, active: true, extra: null },
        { value: 'CANCELLED', label: 'Cancelled', labelAr: 'ملغى', labelEn: 'Cancelled', sortOrder: 4, uiSeverity: 'secondary', icon: 'pi-ban', isSystem: true, active: true, extra: null }
    ];

    const base: StudentInfoRequestDto = {
        id: 77, studentId: 1003, studentName: 'QA Student', requestType: 'STATUS_CERT',
        title: 'Renew the status certificate', description: 'Please upload the new certificate.',
        createdBy: 'officer', createdAt: '2026-09-01T09:00:00Z', dueDate: '2026-09-20T00:00:00Z',
        status: 'PENDING'
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentInfoRequestsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: StudentInfoRequestService,
                    useValue: {
                        list: () => of({ content: [], totalElements: 0, totalPages: 0 }),
                        getOne: () => of(current),
                        getStaging: () => of({ studentDataJson: '', studentFinanceJson: '', submittedAt: '' })
                    }
                },
                { provide: StudentdataService, useValue: { getPagingAll: () => of({ content: [] }) } },
                { provide: LookupService, useValue: { getCountries: () => of([{ id: 1, name: 'Yemen' }, { id: 2, name: 'Egypt' }]),
                    getStudentStudyStatuses: () => of([{ id: 5, name: 'Enrolled' }]), getStudentInfoRequestKindsForUi: () => of([{ label: 'Status certificate', value: 'STATUS_CERT' }]) } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] } },
                {
                    provide: HttpClient,
                    useValue: { get: (url: string) => url.includes('/code-lists') ? of({ 'info-request-status': STATUS_LIST }) : of([]) }
                },
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

        fixture = TestBed.createComponent(StudentInfoRequestsComponent);
        component = fixture.componentInstance;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        component.showDetailDialog = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const open = async (overrides: Partial<StudentInfoRequestDto>) => {
        current = { ...base, ...overrides };
        component.openDetail(current);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        return document.querySelector('.p-dialog.dv-dialog') as HTMLElement;
    };

    const footerButtons = (dialog: HTMLElement) =>
        Array.from(dialog.querySelectorAll('.dv-footer__actions button')).map(b => b.textContent?.trim() ?? '');

    it('is laid out in the shared vocabulary, outside the component host', async () => {
        const dialog = await open({});
        expect(dialog).withContext('dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog)).toBeFalse();
        expect(dialog.querySelector('.dv-hero')).withContext('hero').toBeTruthy();
        expect(dialog.querySelectorAll('.dv-kpi').length).withContext('kpis').toBe(3);
        expect(dialog.querySelectorAll('.dv-columns > .dv-section').length).withContext('two columns').toBe(2);
        expect(dialog.querySelector('.review-detail')).withContext('old layout gone').toBeNull();
    });

    it('the hero names the request, its kind and its student', async () => {
        const dialog = await open({});
        expect(dialog.querySelector('.dv-hero__badge')?.textContent).toContain('77');
        expect(dialog.querySelector('.dv-hero__eyebrow')?.textContent).toContain('Status certificate');
        expect(dialog.querySelector('.dv-hero__title')?.textContent).toContain('Renew the status certificate');
        expect(dialog.querySelector('.dv-hero__subtitle')?.textContent).toContain('1003');
        expect(dialog.querySelector('.dv-hero__subtitle')?.textContent).toContain('QA Student');
        expect(dialog.querySelector('.dv-hero')?.className).withContext('pending reads as a warning').toContain('dv-hero--warn');
    });

    it('a pending request can be cancelled and nothing else', async () => {
        const dialog = await open({ status: 'PENDING' });
        expect(footerButtons(dialog)).toEqual([
            'STUDENT_INFO_REQUESTS.CLOSE',
            'STUDENT_INFO_REQUESTS.CANCEL_REQUEST'
        ]);
        expect(dialog.querySelector('#rejectReason')).withContext('no decision yet').toBeNull();
        expect(dialog.querySelector('.dv-notice--muted')).withContext('no response yet').toBeTruthy();
    });

    it('an answered request awaiting review offers the decision and its three buttons', async () => {
        const dialog = await open({
            status: 'RESPONDED', reviewStatus: 'PENDING_REVIEW',
            responseText: 'Uploaded.', responseAt: '2026-09-10T10:00:00Z', responseDocumentId: 5
        });
        expect(footerButtons(dialog)).toEqual([
            'STUDENT_INFO_REQUESTS.CLOSE',
            'STUDENT_INFO_REQUESTS.REJECT',
            'STUDENT_INFO_REQUESTS.APPROVE_INFO_ONLY',
            'STUDENT_INFO_REQUESTS.APPROVE_AND_APPLY'
        ]);
        expect(dialog.querySelector('#rejectReason')).withContext('rejection comment field').toBeTruthy();
        expect(dialog.querySelector('.dv-notes')?.textContent).toContain('Please upload the new certificate.');
        expect(dialog.textContent).toContain('#5');
    });

    it('a decided request shows the decision and no further actions', async () => {
        const dialog = await open({
            status: 'RESPONDED', reviewStatus: 'REJECTED', responseAt: '2026-09-10T10:00:00Z',
            reviewedAt: '2026-09-11T08:00:00Z', reviewedBy: 4, rejectionReason: 'Wrong year on the certificate.'
        });
        expect(footerButtons(dialog)).toEqual(['STUDENT_INFO_REQUESTS.CLOSE']);
        expect(dialog.querySelector('.dv-notice--danger')?.textContent).toContain('Wrong year on the certificate.');
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--danger');
        expect(dialog.querySelector('.dv-footer__hint')?.textContent).toContain('STUDENT_INFO_REQUESTS.REVIEWED_AT');
    });
});
