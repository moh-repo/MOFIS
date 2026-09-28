import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';

import { RoleManagementComponent } from './ums-admin/role-management/role-management.component';
import { UserAccessDialogsComponent } from './ums-admin/user-access/user-access-dialogs.component';
import { UserViewComponent } from './ums-admin/user-view/user-view.component';
import { GroupManagementComponent } from './ums-admin/group-management/group-management.component';
import { StudentAdminModule } from './student-admin/student-admin.module';
import { StudentSessionsComponent } from './student-admin/student-sessions/student-sessions.component';

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
import { ToastService } from '../service/toast.service';
import { codeListStub } from '../../testing/code-list.stub';
import { provideUmsTestIcons } from '../../testing/nz-icons.stub';

/**
 * What the Administration screens do before they destroy something.
 *
 * <p>Three faults of the same shape, found in the 31 August audit:
 *
 * <ul>
 *   <li><b>A role could be stripped in silence.</b> The permissions dialog sends
 *   whatever is ticked to an endpoint that <em>replaces</em> the role's set, and
 *   its checkbox source was fetched with no error branch. A failed fetch left the
 *   tree empty, the dialog opened anyway, and pressing Save removed every
 *   permission the role had.</li>
 *   <li><b>Purge-expired swept every session with no prompt</b>, while the two
 *   narrower deletes on the same page both asked first.</li>
 *   <li><b>Assignment dialogs never reloaded.</b> They write as the transfer
 *   happens, so OK and Cancel both just closed, and the table went on showing
 *   what it had fetched before the change.</li>
 * </ul>
 *
 * <p>Each check drives the component rather than the markup, because what is
 * being pinned is the decision — ask first, refuse to open, re-read — and not
 * which button carries it.
 */

const pageOrList = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));
const pageOrListProxy = () => new Proxy({} as any, { get: () => () => pageOrList() });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

const umsProviders = (overrides: any[] = []) => [
    provideNoopAnimations(),
    provideUmsTestIcons(),
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
        useValue: new Proxy({ permissions$: of([]) } as any, {
            get: (t: any, p: string) => p in t ? t[p] : p === 'getCurrentUser' ? () => ({ id: 1, username: 'tester' }) : () => true
        })
    },
    ...overrides
];

describe('a role cannot be stripped of its permissions by accident', () => {

    /** The dialog's checkbox source; the fault turned on this failing. */
    const withPermissionSource = (source: any) => ({
        provide: HttpClient,
        useValue: {
            get: (url: string) => url.includes('/permissions/all') ? source : pageOrList(),
            post: () => of({}), put: () => of({}), delete: () => of({})
        }
    });

    const build = async (source: any) => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [RoleManagementComponent, RouterModule.forRoot([])],
            providers: umsProviders([withPermissionSource(source)])
        }).compileComponents();
        const fixture = TestBed.createComponent(RoleManagementComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        return fixture.componentInstance as any;
    };

    it('does not open the dialog when the permission list could not be loaded', async () => {
        const component = await build(throwError(() => new Error('down')));

        component.showPermissionsModal({ id: 7, roleName: 'Attaché' });

        expect(component.isPermissionsModalVisible)
            .withContext('the dialog opened with nothing to tick; saving it would clear the role')
            .toBeFalse();
    });

    it('opens normally when the permission list is there', async () => {
        // The positive control: the guard must not close the door on everyone.
        const component = await build(of([
            { id: 1, permissionCode: 'STUDENTS.VIEW', permissionName: 'View' }
        ]));
        component.roleService = { getRolePermissions: () => of([]) } as any;

        component.showPermissionsModal({ id: 7, roleName: 'Attaché' });

        expect(component.isPermissionsModalVisible).toBeTrue();
        expect(component.permissionsByPage.length).toBeGreaterThan(0);
    });

    it('asks before saving an empty set over a role', async () => {
        const component = await build(of([
            { id: 1, permissionCode: 'STUDENTS.VIEW', permissionName: 'View' }
        ]));
        let saved = false;
        component.selectedRole = { id: 7, roleName: 'Attaché' };
        component.permissionsByPage = [{ pageName: 'STUDENTS', permissions: [{ id: 1, checked: false }] }];
        component.roleService = { setRolePermissions: () => { saved = true; return of({}); } } as any;

        // The component's own NzModalService, not the root one: importing
        // NzModalModule gives the component its own instance.
        let confirmed = false;
        component.modal.confirm = (config: any) => { confirmed = true; return config; };

        component.saveRolePermissions();

        expect(confirmed)
            .withContext('clearing every permission a role has went through without a prompt')
            .toBeTrue();
        expect(saved)
            .withContext('it was saved before anyone confirmed')
            .toBeFalse();
    });

    it('saves a non-empty set without asking', async () => {
        const component = await build(of([
            { id: 1, permissionCode: 'STUDENTS.VIEW', permissionName: 'View' }
        ]));
        let savedWith: any = null;
        component.selectedRole = { id: 7, roleName: 'Attaché' };
        component.permissionsByPage = [{ pageName: 'STUDENTS', permissions: [{ id: 1, checked: true }] }];
        component.roleService = { setRolePermissions: (_id: number, ids: number[]) => { savedWith = ids; return of({}); } } as any;

        component.saveRolePermissions();

        expect(savedWith).withContext('an ordinary save must not need a prompt').toEqual([1]);
    });

    it('says so to the server once an empty set is confirmed', async () => {
        // The server now refuses an empty list without clearAll (QA B10), so the
        // confirmed clear has to carry it — and only the confirmed clear.
        const component = await build(of([
            { id: 1, permissionCode: 'STUDENTS.VIEW', permissionName: 'View' }
        ]));
        const calls: any[][] = [];
        component.selectedRole = { id: 7, roleName: 'Attaché' };
        component.permissionsByPage = [{ pageName: 'STUDENTS', permissions: [{ id: 1, checked: false }] }];
        component.roleService = { setRolePermissions: (...args: any[]) => { calls.push(args); return of({}); } } as any;
        component.modal.confirm = (config: any) => { config.nzOnOk(); return config; };

        component.saveRolePermissions();

        expect(calls).toEqual([[7, [], true]]);
    });
});

/**
 * The assignment dialogs are built from the list of everything that can be
 * assigned. When that list failed to load they opened anyway and showed every
 * group or role as unassigned, and the View dialog said "none" for a part it had
 * simply not been able to read (QA B10, N12). Roles already refused to open.
 */
describe('the assignment dialogs when a list could not be loaded', () => {

    const down = () => throwError(() => new Error('down'));
    /** A service stub whose named methods are overridden and the rest answer an empty page. */
    const serviceWith = (methods: Record<string, () => any>) => new Proxy(methods as any, {
        get: (t: any, p: string) => p in t ? t[p] : () => pageOrList()
    });

    const build = async (component: any, overrides: any[]) => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [component, RouterModule.forRoot([])],
            providers: umsProviders(overrides)
        }).compileComponents();
        const fixture = TestBed.createComponent(component);
        fixture.detectChanges();
        await fixture.whenStable();
        return fixture.componentInstance as any;
    };

    // The staff-user dialogs live in one component hosted by the users list and
    // by the account page (ums-admin/user-access), so they are driven directly.
    it('users: the groups dialog does not open without the group list, and retries', async () => {
        let loads = 0;
        let asked = false;
        const component = await build(UserAccessDialogsComponent, [
            { provide: UmsGroupService, useValue: serviceWith({ getAllActiveGroups: () => { loads++; return down(); } }) },
            { provide: UmsUserService, useValue: serviceWith({ getUserGroups: () => { asked = true; return of([]); } }) }
        ]);
        const before = loads;

        component.openGroups({ id: 5 });

        expect(component.isGroupsModalVisible)
            .withContext('opened with every group shown as unassigned')
            .toBeFalse();
        expect(asked).withContext('went on to read the groups of the user anyway').toBeFalse();
        expect(loads).withContext('did not retry the list').toBe(before + 1);
    });

    it('users: the roles dialog does not open without the role list', async () => {
        const component = await build(UserAccessDialogsComponent, [
            { provide: UmsRoleService, useValue: serviceWith({ getAllActiveRoles: down }) }
        ]);

        component.openRoles({ id: 5 });

        expect(component.isRolesModalVisible).toBeFalse();
    });

    it('users: the dialogs still open once the lists are there', async () => {
        const component = await build(UserAccessDialogsComponent, [
            { provide: UmsGroupService, useValue: serviceWith({ getAllActiveGroups: () => of([{ id: 1, groupName: 'G' }]) }) },
            { provide: UmsRoleService, useValue: serviceWith({ getAllActiveRoles: () => of([{ id: 2, roleName: 'R' }]) }) },
            { provide: UmsUserService, useValue: serviceWith({ getUserGroups: () => of([]), getUserRoles: () => of([]) }) }
        ]);

        component.openGroups({ id: 5 });
        component.openRoles({ id: 5 });

        expect(component.isGroupsModalVisible).toBeTrue();
        expect(component.isRolesModalVisible).toBeTrue();
    });

    it('users: the account page marks the part it could not read instead of saying "none"', async () => {
        const component = await build(UserViewComponent, [
            {
                provide: ActivatedRoute,
                useValue: { paramMap: of(convertToParamMap({ id: '5' })), snapshot: { paramMap: convertToParamMap({ id: '5' }) } }
            },
            { provide: UmsUserService, useValue: serviceWith({
                getUserById: () => of({ id: 5, userName: 'u', status: 'ACTIVE' }),
                getUserRoles: down,
                getUserGroups: () => of([])
            }) }
        ]);

        expect(component.user).withContext('the page did not open on a part failing').toBeTruthy();
        expect(component.failed).toEqual({ roles: true, groups: false, permissions: false, mfa: false });
    });

    it('groups: the roles dialog does not open without the role list', async () => {
        let read = false;
        const component = await build(GroupManagementComponent, [
            { provide: UmsRoleService, useValue: serviceWith({ getAllActiveRoles: down }) },
            { provide: UmsGroupService, useValue: serviceWith({ getGroupRoles: () => { read = true; return of([]); } }) }
        ]);

        component.showRolesModal({ id: 3 });

        expect(component.isRolesModalVisible).toBeFalse();
        expect(read).toBeFalse();
    });

    it('groups: View says the roles could not be read', async () => {
        const component = await build(GroupManagementComponent, [
            { provide: UmsGroupService, useValue: serviceWith({ getGroupRoles: down }) }
        ]);

        component.showDetailModal({ id: 3, groupName: 'G' });

        expect(component.isDetailModalVisible).toBeTrue();
        expect(component.detailRolesFailed).toBeTrue();
    });
});

describe('the staff user assignment dialogs', () => {

    let component: any;

    beforeEach(async () => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [UserAccessDialogsComponent, RouterModule.forRoot([])],
            providers: umsProviders()
        }).compileComponents();
        const fixture = TestBed.createComponent(UserAccessDialogsComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        component = fixture.componentInstance;
    });

    ['Groups', 'Roles', 'Countries'].forEach(kind => {
        it(`tells its host to re-read when the ${kind.toLowerCase()} dialog closes`, () => {
            // The writes have already happened by then, so closing is the only
            // moment left at which the host can catch up: the list's ATTACHES
            // column and the account page's sections are rendered from their
            // last read, and both re-read on `changed`.
            let reloads = 0;
            component.changed.subscribe(() => { reloads++; });

            component[`handle${kind}ModalOk`]();
            expect(reloads).withContext(`OK on the ${kind} dialog left the host stale`).toBe(1);

            component[`handle${kind}ModalCancel`]();
            expect(reloads).withContext(`Cancel on the ${kind} dialog left the host stale`).toBe(2);
        });
    });
});

describe('purging expired portal sessions', () => {

    let component: any;
    let deleted: string[];

    beforeEach(async () => {
        deleted = [];
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [StudentAdminModule, RouterModule.forRoot([])],
            providers: [
                provideNoopAnimations(),
                MessageService,
                ConfirmationService,
                { provide: ToastService, useValue: new Proxy({} as any, { get: () => () => undefined }) },
                {
                    provide: HttpClient,
                    useValue: {
                        get: () => pageOrList(),
                        post: () => of({}),
                        put: () => of({}),
                        delete: (url: string) => { deleted.push(url); return of({}); }
                    }
                },
                { provide: TranslationService, useValue: translation }
            ]
        }).compileComponents();
        const fixture = TestBed.createComponent(StudentSessionsComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        component = fixture.componentInstance;
    });

    it('asks before sweeping every expired session', () => {
        let asked = false;
        component.confirmationService.confirm = (config: any) => { asked = true; return config; };

        component.purgeExpired();

        expect(asked)
            .withContext('the widest delete on the page fired straight off the button')
            .toBeTrue();
        expect(deleted)
            .withContext('and it had already reached the server')
            .toEqual([]);
    });

    it('purges once the prompt is accepted', () => {
        // So the guard is a prompt, not a wall.
        component.confirmationService.confirm = (config: any) => { config.accept(); return config; };

        component.purgeExpired();

        expect(deleted.length).toBe(1);
        expect(deleted[0]).toContain('/expired');
    });
});
