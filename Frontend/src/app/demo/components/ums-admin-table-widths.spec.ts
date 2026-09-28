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
 * How wide the nine Staff-access tables are, in the width the shell leaves them.
 *
 * <p>These pages list codes, routes and URL patterns — single tokens with no
 * space to break at, up to 49 characters of them. A table cell that cannot break
 * its content cannot be narrower than it, whatever the column asks for, so one
 * such column takes whatever it needs and the rest of the table is squeezed into
 * what is left. The same thing was measured and fixed on the Lookup Tables screen
 * on 30 August; these nine are built the same way.
 *
 * <p>Every one of them also wrapped its card in `.grid > .col-12`. PrimeFlex
 * gives `.grid` a margin of -0.5rem and hands the matching padding to the column,
 * so a grid holding a single full-width column is 1rem — 13px at this
 * application's 12.6px root — wider than the box it sits in.
 *
 * <p>The rows below carry the longest values these tables actually hold, read off
 * the database. Sample rows of the `CODE_A` sort would make every one of these
 * pages look as though it fitted, which is the whole thing being measured.
 */

/** An empty list that is also an empty page; see page-headings.spec.ts. */
const pageOrList = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));
const pageOrListProxy = () => new Proxy({} as any, { get: () => () => pageOrList() });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, getCurrentLanguage: () => 'en',
    // The last segment, underscores opened out. Returning the whole key would
    // give every heading an unbreakable 30-character string and make the
    // measurement about the stub rather than the page.
    translate: (key: string) => key.split('.').pop()!.replace(/_/g, ' ')
};

/**
 * The width the shell leaves a page on a 1366px window: 1366 less the 270px
 * sidebar and the main container's own 3.5rem + 1.5rem of padding at this
 * application's 12.6px root size.
 */
const MAIN_CONTAINER_CONTENT = 1033;

interface Case {
    name: string;
    component: any;
    /** The component field the table reads its rows from. */
    rowsField: string;
    /** Rows carrying the longest values the table actually holds. */
    rows: any[];
    /**
     * The headings that say who or what the row is about. Between them they must
     * be given more room than the buttons at the end of it. A guard rather than a
     * fix: on users it held by 253px to 242px before any of this, which is its own
     * comment on how the table was proportioned.
     */
    identity?: string[];
    /** Headings that must no longer be columns, having moved into another cell. */
    foldedAway?: string[];
    /** How many controls a row may put in its last cell. */
    maxRowControls?: number;
    /**
     * A cell that lists a set rather than holding a value. `heading` names the
     * column, `show` is how many of the set the cell may lay out before it
     * counts the rest, and `of` is the size of the set in the first sample row.
     * The whole set is named in the dialog `openFullSet` opens.
     */
    setCell?: { heading: string; show: number; of: number; openFullSet?: string };
    /** Providers this page's own checks need on top of the shared ones. */
    providers?: any[];
}

/**
 * Every country on the list, which is the scope one staff account actually has.
 * Read off MOHECORE.STUDY_COUNTRY_IN, which is what UmsUserService names a
 * user's attachés from.
 */
const ALL_ATTACHES = [
    'مصر', 'غير محدد', 'استراليا', 'المانيا', 'الامارات العربية المتحدة', 'الصين',
    'المملكة الأردنية الهاشمية', 'المملكة العربية السعودية', 'المملكة المتحدة',
    'الولايات المتحدة الأمريكية', 'تركيا', 'جمهورية السودان', 'جمهورية الهند',
    'فرنسا', 'كندا', 'ماليزيا'
];

const USER_ROWS = [
    { id: 1, userName: 'sa_superadmin', email: 'sa_superadmin@mohe.test', firstName: 'Super', lastName: 'Administrator', status: 'ACTIVE', isActive: true, isSuperAdmin: true, lastLoginAt: '2026-08-30T09:15:00Z', countryNames: ALL_ATTACHES },
    { id: 2, userName: 'finance_officer', email: 'finance.officer@mohe.edu.ye', firstName: 'Finance', lastName: 'Officer', status: 'ACTIVE', isActive: true, isSuperAdmin: false, lastLoginAt: null, countryNames: ['المملكة العربية السعودية', 'الولايات المتحدة الأمريكية'] }
];

const CASES: Case[] = [
    {
        name: 'staff users', component: UserManagementComponent, rowsField: 'users',
        identity: ['USERNAME', 'NAME', 'EMAIL'], maxRowControls: 4,
        setCell: { heading: 'ATTACHES', show: 2, of: ALL_ATTACHES.length },
        rows: USER_ROWS,
        // The view dialog reads the account back from the server rather than
        // taking the row, so the check that the whole set is named there needs a
        // getUserById that answers with the row it was given.
        providers: [{
            provide: UmsUserService,
            useValue: new Proxy({} as any, {
                get: (_t, p) => p === 'getUserById'
                    ? (id: number) => of(USER_ROWS.find(r => r.id === id))
                    : () => pageOrList()
            })
        }]
    },
    {
        name: 'staff groups', component: GroupManagementComponent, rowsField: 'groups',
        rows: [
            { id: 1, groupCode: 'ADMINISTRATORS', groupName: 'Administrators', groupNameAr: 'المسؤولون', description: 'System administrators group', isActive: true, isSystem: true },
            { id: 2, groupCode: 'STUDENT_AFFAIRS', groupName: 'Student Affairs', groupNameAr: 'شؤون الطلاب', description: 'Student affairs staff group', isActive: true, isSystem: false }
        ]
    },
    {
        name: 'staff roles', component: RoleManagementComponent, rowsField: 'roles',
        rows: [
            { id: 1, roleCode: 'FINANCE_OFFICER', roleName: 'FINANCE_OFFICER', roleNameAr: 'موظف مالي', priority: 10, isActive: true, isSystem: true, description: 'Full access to all financial modules including wafar, payment auth, and archiving' },
            { id: 2, roleCode: 'WAFAR_OFFICER', roleName: 'WAFAR_OFFICER', roleNameAr: 'موظف وفار', priority: 20, isActive: true, isSystem: false, description: 'Full access to Wafar financial savings records with read-only finance view' }
        ]
    },
    {
        name: 'staff permissions', component: PermissionManagementComponent, rowsField: 'permissions',
        rows: [
            { id: 1, permissionCode: 'STUDENT_INFO_REQUESTS.VIEW', permissionName: 'View Info Requests', permissionNameAr: 'عرض طلبات المعلومات', resourceType: 'PAGE', actionType: 'VIEW', isActive: true },
            { id: 2, permissionCode: 'STUDENT_OPERATIONS.SEARCH', permissionName: 'Student Operations - Search', permissionNameAr: 'عمليات الطلاب - بحث', resourceType: 'PAGE', actionType: 'SEARCH', isActive: true }
        ]
    },
    {
        name: 'staff pages', component: PageManagementComponent, rowsField: 'pages',
        rows: [
            { id: 1, pageCode: 'SYS_BACKUP', pageName: 'Backup and restore', pageNameAr: 'النسخ الاحتياطي', route: '/system-admin/backup-restore', displayOrder: 1, isActive: true, isVisible: true },
            { id: 2, pageCode: 'ADD_STUDENT_EMERGENCY_CONTACT', pageName: 'Document types', pageNameAr: 'أنواع المستندات', route: '/system-admin/document-types', displayOrder: 2, isActive: true, isVisible: true }
        ]
    },
    {
        name: 'staff reports', component: ReportManagementComponent, rowsField: 'reports',
        identity: ['REPORT NAME', 'REPORT NAME AR'],
        foldedAway: ['PARENT CODE', 'ICON', 'PAGE CODE'],
        rows: [
            { id: 1, reportCode: 'REP_STATISTICAL_STUDENT_EXTENSION', reportName: 'Student extension statistics', reportNameAr: 'إحصائيات تمديد الطلاب', category: 'STATISTICAL', route: '/reports/statistical/student-extension-statistics', parentCode: 'REP_STATISTICAL', permissionCodes: 'REPORT_FINANCE,FINANCE', icon: 'pi-chart-bar', sortOrder: 1, isActive: true, queryParams: null },
            { id: 2, reportCode: 'REP_STATISTICAL_DISTRIBUTION', reportName: 'Distribution statistics', reportNameAr: 'إحصائيات التوزيع', category: 'STATISTICAL', route: '/reports/statistical/distribution-statistics', parentCode: 'REP_STATISTICAL', permissionCodes: 'REPORT_FINANCE,FINANCE', icon: 'pi-chart-pie', sortOrder: 2, isActive: true, queryParams: null }
        ]
    },
    {
        name: 'staff sessions', component: SessionManagementComponent, rowsField: 'sessions',
        rows: [
            { id: 1, user: { userName: 'sa_superadmin' }, deviceInfo: 'Desktop - Chrome (Windows)', ipAddress: '0:0:0:0:0:0:0:1', isActive: true, createdAt: '2026-08-30T09:15:00Z', expiresAt: '2026-08-30T17:15:00Z' },
            { id: 2, user: { userName: 'finance_officer' }, deviceInfo: 'Desktop - Firefox (Windows)', ipAddress: '192.168.10.24', isActive: false, createdAt: '2026-08-29T08:00:00Z', expiresAt: '2026-08-29T16:00:00Z' }
        ]
    },
    {
        name: 'staff fields', component: FieldManagementComponent, rowsField: 'fields',
        rows: [
            { id: 1, fieldCode: 'ADD_STUDENT_EMERGENCY_CONTACT', fieldName: 'Emergency contact', fieldNameAr: 'جهة اتصال الطوارئ', fieldType: 'TEXT', isSensitive: true, isActive: true, pageId: 1 },
            { id: 2, fieldCode: 'UMSSESSIONS_LAST_ACTIVITY', fieldName: 'Last activity', fieldNameAr: 'آخر نشاط', fieldType: 'DATE', isSensitive: false, isActive: true, pageId: 1 }
        ]
    },
    {
        name: 'staff api links', component: ApiLinkManagementComponent, rowsField: 'apiLinks',
        rows: [
            { id: 1, resourceCode: 'API_REPORTS_GENERATE', resourceName: 'Generate report', method: 'POST', urlPattern: '/mohe/api/v1/reports/generate/**', isActive: true },
            { id: 2, resourceCode: 'API_STUDENTS_SEARCH', resourceName: 'Search students', method: 'GET', urlPattern: '/mohe/api/v1/students/search/**', isActive: true }
        ]
    }
];

CASES.forEach(({ name, component, rowsField, rows, identity, foldedAway, maxRowControls, setCell, providers }) => {

    describe(`${name}, in the width the shell leaves it`, () => {

        let fixture: any;
        let frame: HTMLElement;

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
                    { provide: ReportCatalogService, useValue: { getCatalog: () => of([]), reload: () => undefined } },
                    { provide: CodeListService, useValue: codeListStub() },
                    { provide: TranslationService, useValue: translation },
                    {
                        provide: UmsAuthService,
                        useValue: new Proxy({ permissions$: of([]), profile$: of() } as any, {
                            get: (t, p) => p in t ? t[p] : p === 'getCurrentUser' ? () => ({ id: 1, username: 'tester' }) : () => true
                        })
                    },
                    ...(providers ?? [])
                ]
            }).compileComponents();

            fixture = TestBed.createComponent(component);
            fixture.detectChanges();
            await fixture.whenStable();

            // The rows go in after the page has loaded its (empty) list, so what
            // is measured is this page rendering real content.
            fixture.componentInstance[rowsField] = rows;

            frame = document.createElement('div');
            frame.style.width = `${MAIN_CONTAINER_CONTENT}px`;
            document.body.appendChild(frame);
            frame.appendChild(fixture.nativeElement);
            fixture.detectChanges();
        });

        afterEach(() => {
            frame.remove();
            // A dropdown or a modal renders into the CDK overlay container on the
            // body, which outlives the fixture; left there it would be found by
            // the next test.
            document.querySelectorAll('.cdk-overlay-container').forEach(o => o.remove());
        });

        const table = () => fixture.nativeElement.querySelector('nz-table table') as HTMLElement;

        const headers = () =>
            Array.from(fixture.nativeElement.querySelectorAll('nz-table table thead th')) as HTMLElement[];

        /** Each column's share of the table, as a percentage, with its heading. */
        const shares = () => {
            const total = table().getBoundingClientRect().width;
            return headers().map(th => ({
                label: (th.textContent ?? '').trim().slice(0, 24) || '(unlabelled)',
                width: Math.round(th.getBoundingClientRect().width),
                share: Math.round((th.getBoundingClientRect().width / total) * 100)
            }));
        };

        it('renders its rows', () => {
            expect(fixture.nativeElement.querySelectorAll('nz-table table tbody tr').length)
                .withContext('the sample rows did not reach the table, so nothing below measures anything')
                .toBeGreaterThan(0);
        });

        it('is no wider than the container the shell gives it', () => {
            // The frame, not the page's own first child: `.grid` carries negative
            // margins, so its own clientWidth already includes the overflow and
            // measuring it against itself can never fail.
            expect(frame.scrollWidth)
                .withContext(`the page needs ${frame.scrollWidth}px inside ${frame.clientWidth}px, `
                    + `so the whole window scrolls — ${shares().map(s => `${s.label} ${s.width}px`).join(', ')}`)
                .toBeLessThanOrEqual(frame.clientWidth);
        });

        /**
         * The squeeze the overflow used to be paid for with. A column of 50px
         * holds about six characters; below that a heading is unreadable and a
         * value is a column of single letters.
         */
        it('leaves every column a readable width', () => {
            const thin = shares().filter(c => c.width < 50);
            expect(thin.map(c => `${c.label} ${c.width}px`))
                .withContext(`— ${shares().map(c => `${c.label} ${c.share}%`).join(', ')}`)
                .toEqual([]);
        });

        if (identity) {
            /**
             * Users gave seven buttons 242px — a quarter of the table, and more
             * than any column of data: username 75px, name 82px, email 96px.
             * Reports spread eleven columns over the same space and left Report
             * Name 81px and Report Name (Arabic) 76px.
             */
            it('gives the row more room to say what it is than to act on it', () => {
                const columns = shares();
                const named = identity.map(label => {
                    const found = columns.find(c => c.label === label);
                    expect(found).withContext(`no "${label}" column; the headings changed`).toBeTruthy();
                    return found!;
                });
                const actions = columns[columns.length - 1];
                const total = named.reduce((sum, c) => sum + c.width, 0);
                expect(total)
                    .withContext(`${named.map(c => `${c.label} ${c.width}px`).join(' + ')} = ${total}px `
                        + `against ${actions.width}px of buttons`)
                    .toBeGreaterThan(actions.width);
            });
        }

        if (foldedAway) {
            /**
             * Eleven columns left Report Name 81px and Report Name (Arabic) 76px.
             * The three that were never sortable moved to the cells they qualify —
             * the icon in front of the name it belongs to, the parent code under
             * the category that groups the report, the permission or page code
             * under the route it opens.
             */
            it('folds its qualifying columns into the cells they belong to', () => {
                const labels = shares().map(c => c.label);
                expect(labels.filter(l => foldedAway.includes(l)))
                    .withContext(`still columns of their own — ${labels.join(', ')}`)
                    .toEqual([]);
            });

            it('still shows what those columns said', () => {
                const body = fixture.nativeElement.querySelector('nz-table table tbody') as HTMLElement;
                // The parent code of the first sample row; folded, not dropped.
                expect(body.textContent)
                    .withContext('the parent code went with its column')
                    .toContain('REP_STATISTICAL');
                expect(body.querySelector('.cell-note'))
                    .withContext('nothing renders as a second line on any cell').not.toBeNull();
            });
        }

        if (maxRowControls) {
            /**
             * Seven buttons took 242px of a 981px table — a quarter of it, and more
             * than any column of data. What is done to a user stays in the row;
             * what is granted to one moved behind a menu.
             */
            it('does not line up more controls than the row has room for', () => {
                const cell = fixture.nativeElement
                    .querySelector('nz-table table tbody tr td:last-child') as HTMLElement;
                const controls = cell.querySelectorAll('button');
                expect(controls.length)
                    .withContext(`${controls.length} controls in one row's last cell`)
                    .toBeLessThanOrEqual(maxRowControls);
            });

            it('keeps the actions it moved reachable', async () => {
                // An ng-zorro dropdown renders into the CDK overlay container on
                // the body, not into the row, so the menu has to be opened to be
                // seen — which is also the only thing worth asserting about it.
                const trigger = Array.from(
                    fixture.nativeElement.querySelectorAll('nz-table table tbody tr td:last-child button')
                ).pop() as HTMLElement;
                trigger.click();
                fixture.detectChanges();
                await fixture.whenStable();
                fixture.detectChanges();

                const overlay = document.querySelector('.cdk-overlay-container') as HTMLElement;
                expect(overlay).withContext('the moved actions have no menu to live in').toBeTruthy();
                ['MANAGE GROUPS', 'MANAGE ROLES', 'MANAGE PERMISSIONS', 'MANAGE COUNTRIES']
                    .forEach(action => expect(overlay.textContent)
                        .withContext(`${action} was dropped rather than moved`)
                        .toContain(action));
            });
        }

        if (setCell) {
            /**
             * One account covers every country on the list, and a cell is an
             * inline run: sixteen tags laid end to end made the column 324px of a
             * 981px table — a third of it, against 70px of username and 84px of
             * email — and stood the row 148px tall.
             */
            it('does not lay a whole set out across the row', () => {
                const columns = shares();
                const index = columns.findIndex(c => c.label === setCell.heading);
                expect(index).withContext(`no "${setCell.heading}" column`).toBeGreaterThanOrEqual(0);

                const cell = fixture.nativeElement
                    .querySelectorAll('nz-table table tbody tr')[0]
                    .querySelectorAll('td')[index] as HTMLElement;
                const laidOut = cell.querySelectorAll('.ant-tag').length;
                expect(laidOut)
                    .withContext(`${laidOut} of ${setCell.of} laid out in one cell, `
                        + `${columns[index].width}px wide and ${Math.round(cell.getBoundingClientRect().height)}px tall`)
                    .toBeLessThanOrEqual(setCell.show + 1);
            });

            it('says how many of the set it did not lay out', () => {
                const index = shares().findIndex(c => c.label === setCell.heading);
                const cell = fixture.nativeElement
                    .querySelectorAll('nz-table table tbody tr')[0]
                    .querySelectorAll('td')[index] as HTMLElement;
                expect((cell.textContent ?? '').trim())
                    .withContext('the rest of the set went unmentioned rather than counted')
                    .toContain(`+${setCell.of - setCell.show}`);
            });

            // The staff users page no longer has a view dialog: the whole set is
            // named on the account page, /ums-admin/users/:id, and pinned in
            // ums-admin/user-view/user-view.component.spec.ts.
            (setCell.openFullSet ? it : xit)('names the whole set in the view dialog', async () => {
                fixture.componentInstance[setCell.openFullSet!](rows[0]);
                fixture.detectChanges();
                await fixture.whenStable();
                fixture.detectChanges();

                const overlay = document.querySelector('.cdk-overlay-container') as HTMLElement;
                expect(overlay).withContext('the view dialog did not open').toBeTruthy();
                // The last of the set: the one the list is furthest from showing.
                expect(overlay.textContent)
                    .withContext('the dialog names no more of the set than the row does')
                    .toContain((rows[0] as any).countryNames.at(-1));
            });
        }

        it('leaves the row buttons their line', () => {
            const cell = fixture.nativeElement
                .querySelector('nz-table table tbody tr td:last-child') as HTMLElement;
            expect(cell).withContext('no actions cell').toBeTruthy();
            expect(cell.scrollWidth)
                .withContext(`the actions column has ${cell.clientWidth}px for buttons that `
                    + `need ${cell.scrollWidth}px`)
                .toBeLessThanOrEqual(cell.clientWidth);
        });

    });
});
