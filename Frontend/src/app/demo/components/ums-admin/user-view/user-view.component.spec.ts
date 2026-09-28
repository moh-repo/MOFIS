import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { UserViewComponent } from './user-view.component';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsGroupService } from '../../../service/ums-group.service';
import { UmsRoleService } from '../../../service/ums-role.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { LookupService } from '../../../service/lookup.service';
import { CodeListService } from '../../../service/code-list.service';
import { TranslationService } from '../../../service/translation.service';
import { codeListStub, codeRow } from '../../../../testing/code-list.stub';
import { provideUmsTestIcons } from '../../../../testing/nz-icons.stub';

/**
 * The account page, /ums-admin/users/:id.
 *
 * <p>The users list shows two attachés and a count; this page is where the
 * whole scope is named, which is the check ums-admin-table-widths.spec.ts
 * used to make against the view dialog the page replaced. And every name the
 * page prints for a code comes from the database's code lists, not from a
 * translation of the code.
 */

const pageOrList = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));
const pageOrListProxy = () => new Proxy({} as any, { get: () => () => pageOrList() });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

/** Every country on the list: the scope one staff account actually has. */
const ALL_ATTACHES = [
    'مصر', 'غير محدد', 'استراليا', 'المانيا', 'الامارات العربية المتحدة', 'الصين',
    'المملكة الأردنية الهاشمية', 'المملكة العربية السعودية', 'المملكة المتحدة',
    'الولايات المتحدة الأمريكية', 'تركيا', 'جمهورية السودان', 'جمهورية الهند',
    'فرنسا', 'كندا', 'ماليزيا'
];

const account = (patch: Record<string, unknown> = {}) => ({
    id: 1, userName: 'sa_superadmin', email: 'sa_superadmin@mohe.test', firstName: 'Super', lastName: 'Administrator',
    status: 'ACTIVE', isAdmin: true, isSuperAdmin: false, failedLoginAttempts: 0, lastLoginAt: '2026-08-30T09:15:00Z',
    createdAt: '2026-01-01T00:00:00Z', updatedAt: null, countryIds: [], countryNames: ALL_ATTACHES, positionCode: 'DIRECTOR',
    ...patch
});

describe('the staff account page', () => {

    let fixture: any;

    const build = async (user: any) => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [UserViewComponent, RouterModule.forRoot([])],
            providers: [
                provideNoopAnimations(),
                provideUmsTestIcons(),
                {
                    provide: ActivatedRoute,
                    useValue: { paramMap: of(convertToParamMap({ id: '1' })), snapshot: { paramMap: convertToParamMap({ id: '1' }) } }
                },
                { provide: HttpClient, useValue: { get: () => of(['STUDENTS.VIEW', 'STUDENTS.EDIT']), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                {
                    provide: UmsUserService,
                    useValue: new Proxy({} as any, {
                        get: (_t, p) => p === 'getUserById' ? () => of(user)
                            : p === 'getMfaStatus' ? () => of({ enabled: true, required: false, enrolledAt: null, recoveryCodesLeft: 3, locked: false })
                            : p === 'getUserRoles' ? () => of([{ id: 2, roleName: 'FINANCE_OFFICER', roleNameAr: 'موظف مالي' }])
                            : () => pageOrList()
                    })
                },
                { provide: UmsGroupService, useValue: pageOrListProxy() },
                { provide: UmsRoleService, useValue: pageOrListProxy() },
                { provide: UmsPermissionService, useValue: pageOrListProxy() },
                { provide: EnumOptionsService, useValue: pageOrListProxy() },
                { provide: LookupService, useValue: pageOrListProxy() },
                {
                    // The names the page prints come from the tables, not from i18n keys of the codes.
                    provide: CodeListService,
                    useValue: codeListStub({
                        'ums-user-status': [codeRow('ACTIVE', { labelEn: 'Active (from the table)' }), codeRow('LOCKED', { labelEn: 'Locked (from the table)' })],
                        'position': [codeRow('DIRECTOR', { labelEn: 'Director (from the table)' })]
                    })
                },
                { provide: TranslationService, useValue: translation },
                {
                    provide: UmsAuthService,
                    useValue: new Proxy({ permissions$: of([]), currentUserValue: { id: 99 } } as any, {
                        get: (t, p) => p in t ? t[p] : () => true
                    })
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(UserViewComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        document.body.appendChild(fixture.nativeElement);
    };

    afterEach(() => {
        fixture?.nativeElement.remove();
        document.querySelectorAll('.cdk-overlay-container').forEach(o => o.remove());
    });

    const text = () => (fixture.nativeElement.textContent ?? '') as string;

    it('names every attaché the account covers, not two and a count', async () => {
        await build(account());

        ALL_ATTACHES.forEach(name => expect(text()).withContext(`${name} is not named on the page`).toContain(name));
        // The last of the set: the one the list is furthest from showing.
        expect(text()).toContain(ALL_ATTACHES[ALL_ATTACHES.length - 1]);
    });

    it('prints the status and the position by their names in the code lists', async () => {
        await build(account());

        expect(text()).toContain('Active (from the table)');
        expect(text()).toContain('Director (from the table)');
    });

    it('reads what the account holds: roles, permission codes and the second factor', async () => {
        await build(account());
        const component = fixture.componentInstance;

        expect(component.roles.map((r: any) => r.roleName)).toEqual(['FINANCE_OFFICER']);
        expect(component.permissionCodes).toEqual(['STUDENTS.VIEW', 'STUDENTS.EDIT']);
        expect(component.mfa?.enabled).toBeTrue();
        expect(component.failed).toEqual({ roles: false, groups: false, permissions: false, mfa: false });
    });

    it('offers to unlock a locked account and nothing else', async () => {
        await build(account({ status: 'LOCKED', failedLoginAttempts: 5, lockedUntil: null }));
        expect(text()).withContext('a locked account has no way out but this button').toContain('UMS.USERS.VIEW.UNLOCK');
        expect(text()).toContain('UMS.USERS.VIEW.LOCK_MANUAL');
        fixture.nativeElement.remove();

        await build(account({ status: 'ACTIVE' }));
        expect(text()).withContext('an open account has nothing to unlock').not.toContain('UMS.USERS.VIEW.UNLOCK');
    });

    it('leads to the edit page and back to the list by the account id', async () => {
        await build(account());
        const component = fixture.componentInstance;
        const routed: any[] = [];
        (component as any).router = { navigate: (...args: any[]) => { routed.push(args); return Promise.resolve(true); } };

        component.edit();
        component.goToList();

        expect(routed[0][0]).toEqual(['/ums-admin/users', 1, 'edit']);
        expect(routed[1][0]).toEqual(['/ums-admin/users']);
    });
});
