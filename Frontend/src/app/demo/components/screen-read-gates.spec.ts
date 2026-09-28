import { of, throwError } from 'rxjs';
import { StudentTravelTicketsComponent } from './student-travel-tickets/student-travel-tickets.component';
import { StudentProfileComponent } from './students/student-profile.component';
import { StudentsComponent } from './students/students.component';
import { SendNotificationComponent } from './notifications/send-notification/send-notification.component';
import { ApprovalConfigsComponent } from './approvals/approval-configs/approval-configs.component';
import { StudentFinanceWafarComponent } from './student-finance-wafar/student-finance-wafar.component';
import { StudentFinanceArchComponent } from './studentfinancearch/studentfinancearch.component';
import { AppLayoutComponent } from '../../layout/app.layout.component';
import { UmsAuthService } from '../service/ums-auth.service';

/**
 * Live QA M6 (25 Sep 2026): a role allowed on a screen got an "Access denied" toast
 * because the screen also called an endpoint behind another module's permission. Each
 * screen now skips that call for a role without the permission, or calls an endpoint of
 * its own. The methods run against a stand-in `this` holding one role's codes.
 */
describe('screens make only the calls the role may make (M6)', () => {

    /** A permission service answering for the codes given. */
    const holding = (...codes: string[]) => ({
        hasAnyPermission: (wanted: string[]) => wanted.some(c => codes.includes(c)),
        hasPermission: (c: string) => codes.includes(c)
    });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const call = (proto: any, method: string, self: any) => proto[method].call(self);

    it('Travel Tickets: no Wafar balance call without a Wafar code; one with it', () => {
        const getWafarBalance = jasmine.createSpy('getWafarBalance').and.returnValue(of({ currentBalance: 5 }));
        const self: any = { travelTicketService: { getWafarBalance }, permissionService: holding('STUDENT_TRAVEL.VIEW'), wafarBalance: {} };

        call(StudentTravelTicketsComponent.prototype, 'loadWafarBalance', self);
        expect(getWafarBalance).not.toHaveBeenCalled();
        expect(self.wafarBalance).toBeNull();

        self.permissionService = holding('STUDENT_TRAVEL.VIEW', 'WAFAR.VIEW');
        call(StudentTravelTicketsComponent.prototype, 'loadWafarBalance', self);
        expect(getWafarBalance).toHaveBeenCalledTimes(1);
    });

    it('Student profile: a student viewer makes no approvals, finance or Wafar call', () => {
        const spy = () => jasmine.createSpy().and.returnValue(of([]));
        const self: any = {
            studentId: 7,
            permissionService: holding('STUDENTS.VIEW'),
            approvalService: { getStudentRequests: spy() },
            studentdataService: { getFinancePaymentsByStudent: spy(), getFinanceArchByStudent: spy() },
            wafarService: { getStudentFeePayments: spy() }
        };
        Object.defineProperty(self, 'canSeeApprovals', {
            get: () => Object.getOwnPropertyDescriptor(StudentProfileComponent.prototype, 'canSeeApprovals')!.get!.call(self)
        });

        call(StudentProfileComponent.prototype, 'loadPendingApprovals', self);
        call(StudentProfileComponent.prototype, 'loadFinanceData', self);

        expect(self.approvalService.getStudentRequests).not.toHaveBeenCalled();
        expect(self.studentdataService.getFinancePaymentsByStudent).not.toHaveBeenCalled();
        expect(self.studentdataService.getFinanceArchByStudent).not.toHaveBeenCalled();
        expect(self.wafarService.getStudentFeePayments).not.toHaveBeenCalled();
        expect(self.canSeeApprovals).toBeFalse();
    });

    it('Student profile: the Archiving Officer still reads the finance history', () => {
        const self: any = {
            studentId: 7,
            permissionService: holding('FINANCE_ARCH.VIEW'),
            studentdataService: { getFinancePaymentsByStudent: jasmine.createSpy(), getFinanceArchByStudent: jasmine.createSpy().and.returnValue(of([{ id: 1 }])) },
            wafarService: { getStudentFeePayments: jasmine.createSpy() }
        };
        call(StudentProfileComponent.prototype, 'loadFinanceData', self);
        expect(self.studentdataService.getFinanceArchByStudent).toHaveBeenCalledWith(7);
        expect(self.financeHistory).toEqual([{ id: 1 }]);
        expect(self.studentdataService.getFinancePaymentsByStudent).not.toHaveBeenCalled();
    });

    it('All Students: the pending marker comes from the student endpoint, not the approvals one', () => {
        const self: any = { studentDataService: { getPendingStudentIds: () => of(['12', '15']) } };
        call(StudentsComponent.prototype, 'loadPendingStatus', self);
        expect([...self.pendingStudentIds]).toEqual(['12', '15']);
    });

    it('Approval Rules: role names come from the rules endpoint, in the reader\'s language', () => {
        let url = '';
        const self: any = {
            httpClient: { get: (u: string) => { url = u; return of([{ id: 3, roleName: 'HR Manager', roleNameAr: 'مدير الموارد' }]); } },
            translationService: { getCurrentLanguage: () => 'ar', translate: (k: string) => k },
            messageService: { add: () => undefined }
        };
        call(ApprovalConfigsComponent.prototype, 'loadRoles', self);
        expect(url).toContain('/api/v1/approval-configs/role-options');
        expect(url).not.toContain('/ums/roles');
        expect(self.roleOptions).toEqual([{ label: 'مدير الموارد', value: 3 }]);
    });

    it('Send Notification: a refused group list leaves the countries, majors and statuses loaded', () => {
        const self: any = {
            loadTypeOptions: () => undefined,
            applyDefaultCountries: () => undefined,
            refreshAvailability: () => undefined,
            lookupService: {
                getAllStudyCountries: () => of([{ id: 1, name: 'Egypt' }]),
                getStudentMajors: () => of([{ id: 2 }]),
                getStudyStatuses: () => of([{ id: 3 }])
            },
            notificationService: { getRecipientGroups: () => throwError(() => ({ status: 403 })) },
            umsAuthService: { filterToAllowedStudyCountries: (c: unknown[]) => c },
            translationService: { getCurrentLanguage: () => 'en' }
        };
        call(SendNotificationComponent.prototype, 'loadLookups', self);
        expect(self.countries).toEqual([{ id: 1, name: 'Egypt' }]);
        expect(self.majors).toEqual([{ id: 2 }]);
        expect(self.umsGroups).toEqual([]);
        expect(self.loadingLookups).toBeFalse();
    });

    it('Student Wafar: the finance and fee lists are not read when the page opens', () => {
        const self: any = {
            loadLookupData: jasmine.createSpy(),
            loadStudentData: jasmine.createSpy(),
            loadRecords: jasmine.createSpy(),
            keepYearMonthInStep: jasmine.createSpy()
        };
        call(StudentFinanceWafarComponent.prototype, 'ngOnInit', self);
        expect(self.loadRecords).toHaveBeenCalled();
        expect(self.loadStudentData).not.toHaveBeenCalled();
    });

    it('Student Wafar: an edit opened before the lists arrived picks its record when they do', () => {
        let picked: unknown = null;
        const self: any = {
            dialogMode: 'edit',
            selectedRecord: { id: 44, wafarTypeId: 1 },
            isUniversityFeeType: false,
            studentFinances: [{ id: 44, studentFinId: 'S44', studentFinName: 'Name' }],
            universityFees: [],
            form: { get: () => ({ value: picked, setValue: (v: unknown) => { picked = v; } }) },
            checkIsUniversityFeeType: () => false
        };
        self.findFinanceLookupId = (StudentFinanceWafarComponent.prototype as any).findFinanceLookupId.bind(self);
        call(StudentFinanceWafarComponent.prototype, 'rebuildDropdownOptions', self);
        expect(picked).toBe(44);
    });

    it('Finance History: no recipient list, which the Archiving Officer may not read (R5-08)', () => {
        const called: string[] = [];
        const lookupService = new Proxy({}, {
            get: (_target, name) => () => { called.push(String(name)); return of([]); }
        });
        call(StudentFinanceArchComponent.prototype, 'loadLookupData', { lookupService });
        expect(called).toContain('getAllStudyFinReceivedStatuses');
        expect(called).not.toContain('getAllStudyFinReceivedWhoms');
    });

    it('Any page, an account holding no code: the shell does not re-read its permissions by user id (R5-08)', () => {
        const self: any = {
            idleTimeoutService: { start: jasmine.createSpy() },
            alertStreamService: { connect: jasmine.createSpy() },
            menuService: { hasVisibleItems$: of(false) }
        };
        call(AppLayoutComponent.prototype, 'ngOnInit', self);
        expect(self.hasVisibleMenuItems).toBeFalse();
        expect((UmsAuthService.prototype as any).loadPermissionsForUser).toBeUndefined();
    });
});
