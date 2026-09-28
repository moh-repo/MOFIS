import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';

import { PermissionManagementComponent } from './ums-admin/permission-management/permission-management.component';
import { PageManagementComponent } from './ums-admin/page-management/page-management.component';
import { ReportManagementComponent } from './ums-admin/report-management/report-management.component';
import { FieldManagementComponent } from './ums-admin/field-management/field-management.component';
import { ApiLinkManagementComponent } from './ums-admin/api-link-management/api-link-management.component';
import { SessionManagementComponent } from './ums-admin/session-management/session-management.component';

import { StudentAdminModule } from './student-admin/student-admin.module';
import { StudentUsersComponent } from './student-admin/student-users/student-users.component';
import { StudentPermissionsComponent } from './student-admin/student-permissions/student-permissions.component';
import { StudentRolesComponent } from './student-admin/student-roles/student-roles.component';

import { SettingsModule } from './settings.module';
import { StudyYearsLookupComponent } from './study-years-lookup/study-years-lookup.component';
import { StudyAmountLookupComponent } from './study-amount-lookup/study-amount-lookup.component';

import { UmsUserService } from '../service/ums-user.service';
import { UmsGroupService } from '../service/ums-group.service';
import { UmsRoleService } from '../service/ums-role.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { EnumOptionsService } from '../service/enum-options.service';
import { LookupService } from '../service/lookup.service';
import { ReportCatalogService } from '../service/report-catalog.service';
import { CodeListService } from '../service/code-list.service';
import { TranslationService } from '../service/translation.service';
import { StudyYearsLookupApiService } from '../service/study-years-lookup.service';
import { StudyAmountLookupApiService } from '../service/study-amount-lookup.service';
import { codeListStub } from '../../testing/code-list.stub';
import { provideUmsTestIcons } from '../../testing/nz-icons.stub';

/**
 * The row detail each of these screens was missing.
 *
 * <p>Ten Administration pages let a record be edited and deleted but never
 * simply read: the actions cell held a pencil and a bin and nothing else. On six
 * of them the server has offered a {@code GET /{id}} the whole time and nothing
 * had ever called it. Where a record has more to it than the columns — a
 * permission's description, a session's user agent — there was nowhere at all to
 * see it.
 *
 * <p>Each check opens the dialog through the component's own handler and looks
 * for the record in what is rendered. That is the part that matters: a dialog
 * that opens empty is no better than no dialog.
 */

const pageOrList = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));
const pageOrListProxy = () => new Proxy({} as any, { get: () => () => pageOrList() });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

interface Case {
    name: string;
    component: any;
    /** The module that declares it, for the pages that are not standalone. */
    module?: any;
    /** The row handed to the opener. */
    row: any;
    /** What opens the dialog. */
    open: string;
    /** A value from the row that the open dialog has to show. */
    expect: string;
    extraProviders?: any[];
}

const CASES: Case[] = [
    {
        name: 'staff permissions', component: PermissionManagementComponent, open: 'showDetailModal',
        row: { id: 1, permissionCode: 'ZZ.VIEW', permissionName: 'Zed', permissionNameAr: 'زد', resourceType: 'PAGE', actionType: 'VIEW', description: 'what it grants' },
        expect: 'what it grants'
    },
    {
        name: 'staff pages', component: PageManagementComponent, open: 'showDetailModal',
        row: { id: 1, pageCode: 'ZZ_PAGE', pageName: 'Zed Page', pageNameAr: 'صفحة', route: '/zed', displayOrder: 3, isVisible: true },
        expect: '/zed'
    },
    {
        name: 'staff reports', component: ReportManagementComponent, open: 'showDetailModal',
        row: { id: 1, reportCode: 'ZZ_REP', reportName: 'Zed Report', reportNameAr: 'تقرير', category: 'FINANCE', route: '/reports/zed', sortOrder: 2 },
        expect: '/reports/zed'
    },
    {
        name: 'staff fields', component: FieldManagementComponent, open: 'showDetailModal',
        row: { id: 1, fieldCode: 'ZZ_FIELD', fieldName: 'Zed Field', fieldNameAr: 'حقل', fieldType: 'TEXT', isSensitive: true },
        expect: 'Zed Field'
    },
    {
        name: 'staff api links', component: ApiLinkManagementComponent, open: 'showDetailModal',
        row: { id: 1, resourceCode: 'ZZ_API', resourceName: 'Zed Api', method: 'GET', urlPattern: '/api/zed/**', isActive: true },
        expect: '/api/zed/**'
    },
    {
        name: 'staff sessions', component: SessionManagementComponent, open: 'showDetailModal',
        row: {
            id: 1, user: { id: 2, userName: 'zed', email: 'zed@example.test' },
            ipAddress: '10.0.0.9', userAgent: 'Mozilla/5.0 (a very long agent string)',
            deviceInfo: 'Chrome', createdAt: '2026-08-01T09:00:00', expiresAt: '2026-09-01T09:00:00', isActive: true
        },
        // The user agent is the reason this dialog is worth having: too long for
        // the table, and it is what tells one session from another.
        expect: 'a very long agent string'
    },
    {
        name: 'portal users', component: StudentUsersComponent, module: StudentAdminModule, open: 'openView',
        row: { studentUserId: 5, username: 'portal.user', status: 'LOCKED', studentId: 771, failedLoginAttempts: 3 },
        expect: 'portal.user'
    },
    {
        name: 'portal permissions', component: StudentPermissionsComponent, module: StudentAdminModule, open: 'openView',
        row: { permissionId: 9, permissionCode: 'PORTAL.VIEW', permissionName: 'Portal view', permissionNameAr: 'عرض', resourceType: 'PAGE', actionType: 'VIEW', isActive: true, description: 'the description nobody could read' },
        expect: 'the description nobody could read'
    },
    {
        name: 'study years', component: StudyYearsLookupComponent, module: SettingsModule, open: 'viewItem',
        row: { id: 3, studentDegreeId: 1, studentMajorId: 2, studyYears: 7, studyCountryInId: null },
        expect: '7',
        extraProviders: [{ provide: StudyYearsLookupApiService, useValue: { getAll: () => of([]) } }]
    },
    {
        name: 'study amounts', component: StudyAmountLookupComponent, module: SettingsModule, open: 'viewItem',
        row: { id: 4, studentDegreeId: 1, studentAgencyTypeId: 2, amount: 1234, studyCountryInId: null },
        expect: '1234',
        extraProviders: [{ provide: StudyAmountLookupApiService, useValue: { getAll: () => of([]) } }]
    }
];

CASES.forEach(({ name, component, module, row, open, expect: expected, extraProviders }) => {

    describe(`the ${name} detail view`, () => {

        let fixture: any;

        beforeEach(async () => {
            TestBed.resetTestingModule();
            await TestBed.configureTestingModule({
                imports: [module ?? component, RouterModule.forRoot([])],
                providers: [
                    provideNoopAnimations(),
                    provideUmsTestIcons(),
                    MessageService,
                    ConfirmationService,
                    { provide: HttpClient, useValue: { get: () => pageOrList(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                    { provide: UmsUserService, useValue: pageOrListProxy() },
                    { provide: UmsGroupService, useValue: pageOrListProxy() },
                    { provide: UmsRoleService, useValue: pageOrListProxy() },
                    { provide: UmsPermissionService, useValue: pageOrListProxy() },
                    { provide: EnumOptionsService, useValue: pageOrListProxy() },
                    { provide: LookupService, useValue: pageOrListProxy() },
                    { provide: ReportCatalogService, useValue: { getCatalog: () => of([]), reload: () => undefined } },
                    { provide: CodeListService, useValue: codeListStub() },
                    { provide: TranslationService, useValue: translation },
                    {
                        provide: UmsAuthService,
                        useValue: new Proxy({ permissions$: of([]), profile$: of() } as any, {
                            get: (t: any, p: string) => p in t ? t[p] : p === 'getCurrentUser' ? () => ({ id: 1, username: 'tester' }) : () => true
                        })
                    },
                    ...(extraProviders ?? [])
                ]
            }).compileComponents();

            fixture = TestBed.createComponent(component);
            fixture.detectChanges();
            await fixture.whenStable();
            document.body.appendChild(fixture.nativeElement);
        });

        afterEach(() => fixture.nativeElement.remove());

        it('has a way to open one row on its own', () => {
            expect(typeof fixture.componentInstance[open])
                .withContext(`${name} has no ${open}; the row was only readable across a table`)
                .toBe('function');
        });

        it('shows the record once it is open', async () => {
            fixture.componentInstance[open](row);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            // The dialog renders outside the component's own element, so the
            // whole document is the place to look.
            expect(document.body.textContent)
                .withContext(`${name}: the dialog opened without "${expected}" in it`)
                .toContain(expected);
        });
    });
});

/**
 * Granting and revoking a portal role's permissions.
 *
 * <p>`POST` and `DELETE /roles/{id}/permissions/{permissionId}` have existed
 * since these screens were written and nothing in the frontend called either, so
 * a role's permissions could be read and never changed. One at a time, like the
 * group's roles next door — not a replace, so nothing can be cleared by accident.
 */
describe('portal role permissions', () => {

    let component: any;
    let calls: string[];

    beforeEach(async () => {
        calls = [];
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [StudentAdminModule, RouterModule.forRoot([])],
            providers: [
                provideNoopAnimations(),
                MessageService,
                ConfirmationService,
                {
                    provide: HttpClient,
                    useValue: {
                        get: () => of([]),
                        post: (url: string) => { calls.push('POST ' + url); return of({}); },
                        delete: (url: string) => { calls.push('DELETE ' + url); return of({}); },
                        put: () => of({})
                    }
                },
                { provide: LookupService, useValue: pageOrListProxy() },
                { provide: TranslationService, useValue: translation }
            ]
        }).compileComponents();
        const fixture = TestBed.createComponent(StudentRolesComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        component = fixture.componentInstance;
        component.selectedRole = { roleId: 3, roleName: 'Reviewer' };
    });

    it('grants a permission the role does not have', () => {
        component.rolePermissions = [];

        component.togglePermission({ permissionId: 11, permissionCode: 'P.VIEW' });

        expect(calls.length).toBe(1);
        expect(calls[0]).toContain('POST');
        expect(calls[0]).toContain('/roles/3/permissions/11');
        expect(component.hasPermission(11)).toBeTrue();
    });

    it('revokes one it has', () => {
        component.rolePermissions = [{ permissionId: 11, permissionCode: 'P.VIEW' }];

        component.togglePermission({ permissionId: 11, permissionCode: 'P.VIEW' });

        expect(calls.length).toBe(1);
        expect(calls[0]).toContain('DELETE');
        expect(calls[0]).toContain('/roles/3/permissions/11');
        expect(component.hasPermission(11)).toBeFalse();
    });
});
