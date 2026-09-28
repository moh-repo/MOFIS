import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { UserFormComponent } from './user-form.component';
import { PRIVILEGE_ADMIN, PRIVILEGE_REGULAR } from './user-form.model';
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
 * The add and edit pages of a staff account: /ums-admin/users/new and
 * /ums-admin/users/:id/edit.
 *
 * <p>What is pinned: every dropdown is fed from the database's code lists
 * (position, status, account type) and lookups (attachés); the password rule
 * is the server's; the attachés go with the create request and the account
 * type becomes the admin flag; a country administrator cannot leave a new
 * account unassigned; and editing neither touches the username nor asks for
 * a password.
 */

const pageOrList = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));
const pageOrListProxy = () => new Proxy({} as any, { get: () => () => pageOrList() });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

/** The signed-in account: the super admin unless a test says otherwise. */
const superAdminCaller = () => ({
    permissions$: of([]), currentUserValue: { id: 99 },
    isSuperAdmin: () => true, isMinistryUser: true, isAdmin: () => true, adminTier: 'SUPER_ADMIN', countryIds: [],
    mayGrantTier: () => true
});

const countryAdminCaller = () => ({
    permissions$: of([]), currentUserValue: { id: 99 },
    isSuperAdmin: () => false, isMinistryUser: false, isAdmin: () => true, adminTier: 'COUNTRY_ADMIN', countryIds: [1004],
    mayGrantTier: (tier: string) => tier === 'COUNTRY_ADMIN' || tier === 'COUNTRY_STAFF'
});

describe('the staff account form page', () => {

    let fixture: any;
    let component: any;
    let created: any[];
    let updated: any[];
    let routed: any[];

    const build = async (opts: { id?: string; user?: any; auth?: any } = {}) => {
        created = [];
        updated = [];
        routed = [];
        // The three code lists every dropdown of the form is fed from.
        const codeLists = codeListStub({
            'position': [codeRow('DIRECTOR', { labelEn: 'Director' }), codeRow('ATTACHE', { labelEn: 'Cultural attaché' })],
            'ums-user-status': [codeRow('ACTIVE'), codeRow('INACTIVE'), codeRow('LOCKED')],
            'ums-user-privilege': [codeRow('ADMIN'), codeRow('REGULAR')]
        });
        const params = convertToParamMap(opts.id ? { id: opts.id } : {});

        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [UserFormComponent, RouterModule.forRoot([])],
            providers: [
                provideNoopAnimations(),
                provideUmsTestIcons(),
                { provide: ActivatedRoute, useValue: { paramMap: of(params), snapshot: { paramMap: params } } },
                { provide: HttpClient, useValue: { get: () => pageOrList(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                {
                    provide: UmsUserService,
                    useValue: new Proxy({} as any, {
                        get: (_t, p) => p === 'getUserById' ? () => of(opts.user)
                            : p === 'createUser' ? (payload: any) => { created.push(payload); return of({ id: 7, ...payload }); }
                            : p === 'updateUser' ? (id: number, payload: any) => { updated.push([id, payload]); return of({ id, ...payload }); }
                            : () => of({})
                    })
                },
                { provide: UmsGroupService, useValue: { getAllActiveGroups: () => of([{ id: 1, groupName: 'Finance', groupNameAr: 'المالية' }]) } },
                { provide: UmsRoleService, useValue: { getAllActiveRoles: () => of([{ id: 2, roleName: 'FINANCE_OFFICER', roleNameAr: 'موظف مالي' }, { id: 3, roleName: 'SUPER_ADMIN', roleNameAr: '' }]) } },
                { provide: UmsPermissionService, useValue: pageOrListProxy() },
                { provide: EnumOptionsService, useValue: { getDropdownOptions: (name: string) => of(codeLists.options(name, 'en')) } },
                { provide: LookupService, useValue: { getAllStudyCountries: () => of([{ id: 1004, studyCountryInName: 'Jordan' }, { id: 1007, studyCountryInName: 'United States' }]) } },
                { provide: CodeListService, useValue: codeLists },
                { provide: TranslationService, useValue: translation },
                { provide: UmsAuthService, useValue: opts.auth ?? superAdminCaller() }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(UserFormComponent);
        component = fixture.componentInstance;
        component.router = { navigate: (...args: any[]) => { routed.push(args); return Promise.resolve(true); } };
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        document.body.appendChild(fixture.nativeElement);
    };

    afterEach(() => {
        fixture?.nativeElement.remove();
        document.querySelectorAll('.cdk-overlay-container').forEach(o => o.remove());
    });

    const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement | null;

    const fillValid = () => {
        component.form.patchValue({
            userName: 'new_officer', firstName: 'New', lastName: 'Officer', email: 'new.officer@mohe.gov.ye',
            phone: '+967 771 234 567', positionCode: 'DIRECTOR', privilege: PRIVILEGE_ADMIN, status: 'ACTIVE',
            countryIds: [1004], password: 'Abcdefghijk1', confirmPassword: 'Abcdefghijk1'
        });
    };

    it('feeds the position, status and account-type lists from the code lists', async () => {
        await build();

        expect(component.positionOptions.map((o: any) => o.value)).toEqual(['DIRECTOR', 'ATTACHE']);
        expect(component.positionOptions.map((o: any) => o.label)).toEqual(['Director', 'Cultural attaché']);
        expect(component.statusOptions.map((o: any) => o.value)).toEqual(['ACTIVE', 'INACTIVE', 'LOCKED']);
        expect(component.privilegeOptions.map((o: any) => o.value)).toEqual(['ADMIN', 'REGULAR']);
        expect(component.countryOptions.map((o: any) => o.id)).toEqual([1004, 1007]);

        ['suPosition', 'suPrivilege', 'suStatus', 'suCountries'].forEach(id =>
            expect(el(`#${id}`)).withContext(`no control ${id} on the page`).toBeTruthy());
    });

    it('asks for a password that meets the server policy, confirmed', async () => {
        await build();
        const password = component.form.get('password');
        const confirm = component.form.get('confirmPassword');

        password.setValue('short1');
        expect(password.valid).withContext('eleven characters are not twelve').toBeFalse();
        password.setValue('123456789012');
        expect(password.valid).withContext('digits alone are not a letter and a digit').toBeFalse();
        password.setValue('Abcdefghijk1');
        expect(password.valid).toBeTrue();

        confirm.setValue('Abcdefghijk2');
        expect(confirm.errors).toEqual({ mismatch: true });
        confirm.setValue('Abcdefghijk1');
        expect(confirm.valid).toBeTrue();
    });

    it('creates the account with its attachés, the type as the admin flag, and opens its page', async () => {
        await build();
        fillValid();
        component.form.patchValue({ groupIds: [1], roleIds: [2] });

        component.save();
        await fixture.whenStable();

        expect(created.length).toBe(1);
        expect(created[0].countryIds).toEqual([1004]);
        expect(created[0].isAdmin).toBeTrue();
        expect(created[0].password).toBe('Abcdefghijk1');
        expect(created[0].passwordHash).toBeUndefined();
        expect(created[0].positionCode).toBe('DIRECTOR');
        expect(routed[0][0]).toEqual(['/ums-admin/users', 7]);
    });

    it('does not offer a country administrator the roles it may not grant', async () => {
        await build({ auth: countryAdminCaller() });
        expect(component.roles.map((r: any) => r.roleName)).toEqual(['FINANCE_OFFICER']);
    });

    it('refuses to let a country administrator leave a new account unassigned', async () => {
        await build({ auth: countryAdminCaller() });
        fillValid();
        component.form.patchValue({ countryIds: [] });

        component.save();
        expect(created.length).withContext('an unassigned account left the country admin\'s scope').toBe(0);
        expect(component.form.get('countryIds').errors).toEqual({ required: true });

        component.form.patchValue({ countryIds: [1004] });
        component.save();
        expect(created.length).toBe(1);
    });

    describe('editing', () => {
        const record = (patch: Record<string, unknown> = {}) => ({
            id: 7, userName: 'finance_officer', firstName: 'Finance', lastName: 'Officer', email: 'finance.officer@mohe.edu.ye',
            phone: null, status: 'ACTIVE', isAdmin: false, isSuperAdmin: false, positionCode: 'ATTACHE', countryIds: [1004],
            createdAt: '2026-01-01T00:00:00Z', lastLoginAt: null, ...patch
        });

        it('locks the username, asks for no password, and shows the record\'s own values', async () => {
            await build({ id: '7', user: record() });

            expect(component.mode).toBe('edit');
            expect(component.form.get('userName').disabled).toBeTrue();
            expect(component.form.get('userName').value).toBe('finance_officer');
            expect(component.form.get('password')).withContext('a password is set at creation only').toBeNull();
            expect(el('#suPassword')).toBeNull();
            expect(component.form.get('privilege').value).toBe(PRIVILEGE_REGULAR);
            expect(component.form.get('positionCode').value).toBe('ATTACHE');
            expect(el('#suCountries')).withContext('attachés are managed from the account page, not here').toBeNull();
        });

        it('saves the type as the admin flag and returns to the account page', async () => {
            await build({ id: '7', user: record() });
            component.form.patchValue({ privilege: PRIVILEGE_ADMIN, phone: '' });

            component.save();
            await fixture.whenStable();

            expect(updated.length).toBe(1);
            expect(updated[0][0]).toBe(7);
            expect(updated[0][1].isAdmin).toBeTrue();
            expect(updated[0][1].userName).withContext('the username is not the client\'s to send').toBeUndefined();
            expect(updated[0][1].password).toBeUndefined();
            expect(routed[0][0]).toEqual(['/ums-admin/users', 7]);
        });

        it('shows a super administrator\'s type rather than offering it', async () => {
            await build({ id: '7', user: record({ isSuperAdmin: true, isAdmin: true }) });
            expect(component.form.get('privilege').disabled).toBeTrue();
        });

        it('disables Save on a super administrator\'s account for anyone who is not one', async () => {
            await build({ id: '7', user: record({ isSuperAdmin: true, isAdmin: true }), auth: countryAdminCaller() });
            expect(component.protected).toBeTrue();

            component.form.patchValue({ firstName: 'X' });
            component.save();
            expect(updated.length).toBe(0);
        });
    });
});
