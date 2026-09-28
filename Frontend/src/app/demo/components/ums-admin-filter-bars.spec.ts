import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { UserManagementComponent } from './ums-admin/user-management/user-management.component';
import { GroupManagementComponent } from './ums-admin/group-management/group-management.component';
import { RoleManagementComponent } from './ums-admin/role-management/role-management.component';
import { PermissionManagementComponent } from './ums-admin/permission-management/permission-management.component';
import { PageManagementComponent } from './ums-admin/page-management/page-management.component';
import { ReportManagementComponent } from './ums-admin/report-management/report-management.component';
import { SessionManagementComponent } from './ums-admin/session-management/session-management.component';
import { FieldManagementComponent } from './ums-admin/field-management/field-management.component';
import { ApiLinkManagementComponent } from './ums-admin/api-link-management/api-link-management.component';

import { UmsUserService } from '../service/ums-user.service';
import { UmsGroupService } from '../service/ums-group.service';
import { UmsRoleService } from '../service/ums-role.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { TranslationService } from '../service/translation.service';
import { EnumOptionsService } from '../service/enum-options.service';
import { LookupService } from '../service/lookup.service';
import { ReportCatalogService } from '../service/report-catalog.service';
import { CodeListService } from '../service/code-list.service';
import { codeListStub } from '../../testing/code-list.stub';
import { provideUmsTestIcons } from '../../testing/nz-icons.stub';

/**
 * The filter rows across the Staff-access menu.
 *
 * <p>All nine pages had one, built the same way: a `.search-toolbar` div that
 * no stylesheet in the repository defines, holding a search box at
 * `min-width: 260px` and one to four selects at their own pixel widths, none
 * of them labelled. The div being unstyled, the controls simply ran on in a
 * line and wrapped wherever they ran out of room.
 *
 * <p>The layout itself is covered once, centrally, in
 * src/app/filter-bar-shared.spec.ts. What this file pins is that each page
 * actually uses it, with ng-zorro controls inside it: every control labelled
 * and bound, no inline widths, the buttons out of the field flow, and the
 * header clear of filters.
 */

/** An empty list that is also an empty page; see page-headings.spec.ts. */
const pageOrList = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));
const pageOrListProxy = () => new Proxy({} as any, { get: () => () => pageOrList() });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

interface Case {
    name: string;
    component: any;
    /** How many filter controls the page offers. */
    fields: number;
    /** How many buttons sit in the action row. */
    actions: number;
}

const CASES: Case[] = [
    { name: 'staff users', component: UserManagementComponent, fields: 3, actions: 2 },
    { name: 'staff groups', component: GroupManagementComponent, fields: 2, actions: 2 },
    { name: 'staff roles', component: RoleManagementComponent, fields: 2, actions: 2 },
    { name: 'staff permissions', component: PermissionManagementComponent, fields: 3, actions: 2 },
    { name: 'staff pages', component: PageManagementComponent, fields: 2, actions: 2 },
    { name: 'staff reports', component: ReportManagementComponent, fields: 2, actions: 2 },
    { name: 'staff sessions', component: SessionManagementComponent, fields: 2, actions: 2 },
    // Fields narrows the list it holds as it is typed, so there is nothing to
    // press Search for; the two sort lists are fields too.
    { name: 'staff fields', component: FieldManagementComponent, fields: 5, actions: 1 },
    { name: 'staff api links', component: ApiLinkManagementComponent, fields: 2, actions: 2 }
];

CASES.forEach(({ name, component, fields, actions }) => {

    describe(`filter bar on ${name}`, () => {

        let fixture: any;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [component, RouterModule.forRoot([])],
                providers: [
                    provideNoopAnimations(),
                    provideUmsTestIcons(),
                    { provide: HttpClient, useValue: { get: () => pageOrList(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                    { provide: UmsUserService, useValue: pageOrListProxy() },
                    { provide: UmsGroupService, useValue: pageOrListProxy() },
                    { provide: UmsRoleService, useValue: pageOrListProxy() },
                    { provide: UmsPermissionService, useValue: pageOrListProxy() },
                    { provide: EnumOptionsService, useValue: pageOrListProxy() },
                    { provide: LookupService, useValue: pageOrListProxy() },
                    { provide: ReportCatalogService, useValue: { getCatalog: () => of([]), reload: () => {} } },
                    { provide: CodeListService, useValue: codeListStub() },
                    { provide: TranslationService, useValue: translation },
                    {
                        provide: UmsAuthService,
                        useValue: new Proxy({ permissions$: of([]) } as any, {
                            get: (t, p) => p in t ? t[p] : p === 'getCurrentUser' ? () => ({ id: 1, username: 'tester' }) : () => true
                        })
                    }
                ]
            }).compileComponents();

            fixture = TestBed.createComponent(component);
            fixture.detectChanges();
            await fixture.whenStable();

            document.body.appendChild(fixture.nativeElement);
            fixture.detectChanges();
        });

        afterEach(() => fixture.nativeElement.remove());

        const el = (selector: string) =>
            fixture.nativeElement.querySelector(selector) as HTMLElement;
        const all = (selector: string) =>
            Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];

        it('uses the shared filter bar', () => {
            expect(el('.filter-bar')).withContext('no shared filter bar on the page').toBeTruthy();
            expect(el('.search-toolbar')).withContext('the unstyled toolbar is still there').toBeNull();
            expect(all('.filter-bar__field').length).toBe(fields);
        });

        it('declares a column count that its fields divide into', () => {
            const grid = el('.filter-bar__grid');
            const wide = all('.filter-bar__field--wide').length;
            const declared = Array.from(grid.classList)
                .map(c => /^filter-bar__grid--cols-(\d+)$/.exec(c))
                .find(Boolean);

            expect(declared).withContext(`no cols-N on: ${grid.className}`).toBeTruthy();
            expect(Number(declared![1])).toBe(fields + wide);
        });

        it('labels every control, bound to it', () => {
            all('.filter-bar__field').forEach((field, i) => {
                const label = field.querySelector('label') as HTMLLabelElement;
                expect(label).withContext(`field ${i} has no label`).toBeTruthy();
                expect(label.htmlFor).withContext(`field ${i} label is not bound`).not.toBe('');
                expect(field.querySelector(`#${CSS.escape(label.htmlFor)}`))
                    .withContext(`field ${i} label points at nothing in its own field`)
                    .toBeTruthy();
            });
        });

        it('keeps the buttons out of the field flow', () => {
            const bar = el('.filter-bar__actions');

            expect(bar).withContext('no action row').toBeTruthy();
            expect(bar.querySelectorAll('button').length).toBe(actions);
            expect(el('.filter-bar__grid').contains(bar))
                .withContext('the buttons are back inside the field grid').toBeFalse();
        });

        it('lets every control fill its column instead of a fixed width', () => {
            all('.filter-bar__field input, .filter-bar__field nz-select, .filter-bar__field nz-input-group').forEach((control, i) => {
                const inline = control.getAttribute('style') ?? '';
                expect(/width/.test(inline))
                    .withContext(`control ${i} still carries an inline width: ${inline}`)
                    .toBeFalse();
            });
        });

        it('keeps the page header clear of filters', () => {
            const header = el('.card > .flex');
            expect(header).withContext('no header row in the card').toBeTruthy();
            expect(header.querySelector('.filter-bar, input, nz-select')).toBeNull();
        });
    });
});
