import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { StudentInfoRequestsComponent } from './student-info-requests.component';
import { StudentInfoRequestsModule } from './student-info-requests.module';
import { ApproveAndApplyOutcome, StudentInfoRequestService } from '../../service/student-info-request.service';
import { StudentdataService } from '../../service/studentdata.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * H3-b, decision D4-a (25 Sep 2026): "Approve and apply" no longer writes the student's
 * data; its changes are sent to the approval chains. The message says so, and says when
 * the answer changed nothing.
 */
describe('StudentInfoRequests — approve and apply outcome', () => {

    const render = async (status: ApproveAndApplyOutcome['status']) => {
        const outcome = { request: {}, studentApprovalRequestId: 501, financeApprovalRequestId: null, status };
        await TestBed.configureTestingModule({
            imports: [StudentInfoRequestsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                providePrimeNG({ overlayAppendTo: 'body' }),
                { provide: StudentInfoRequestService, useValue: {
                    list: () => of({ content: [], totalElements: 0, totalPages: 0 }),
                    approveAndApply: () => of(outcome) } },
                { provide: StudentdataService, useValue: { getPagingAll: () => of({ content: [] }) } },
                { provide: LookupService, useValue: { getCountries: () => of([]), getStudentStudyStatuses: () => of([]),
                    getStudentInfoRequestKindsForUi: () => of([]) } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] } },
                { provide: HttpClient, useValue: { get: () => of({}) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();
        const fixture = TestBed.createComponent(StudentInfoRequestsComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        // The component provides its own MessageService.
        const messages = fixture.debugElement.injector.get(MessageService);
        const shown: string[] = [];
        spyOn(messages, 'add').and.callFake((m: { detail?: string }) => { shown.push(m.detail ?? ''); });
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const c = fixture.componentInstance as any;
        c.selectedRequest = { id: 955, studentId: 10117 };
        c.stagingData = { studentDataJson: '{"studentEmail":"new@example.org"}' };
        c.approveAndApply();
        return shown;
    };

    it('says the changes were sent for approval', async () => {
        expect(await render('SUBMITTED_FOR_APPROVAL')).toEqual(['STUDENT_INFO_REQUESTS.MESSAGES.APPROVED_SUBMITTED']);
    });

    it('says when the answer changed no data', async () => {
        expect(await render('NO_CHANGES')).toEqual(['STUDENT_INFO_REQUESTS.MESSAGES.APPROVED_NO_CHANGES']);
    });
});
