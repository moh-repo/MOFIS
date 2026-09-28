import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { DashboardModule } from './dashboard.module';
import { DASHBOARD_SECTION_CODES } from '../../service/dashboard.service';
import { ReportCountryScopeService } from '../../service/report-country-scope.service';
import { StudentdataService } from '../../service/studentdata.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { LayoutService } from 'src/app/layout/service/app.layout.service';

/**
 * The dashboard by section.
 *
 * <p>What shows is decided by `/dashboard/sections` (the server's authorities)
 * and `*hasPermission` (the client's list); the permission side is stubbed
 * permissive here, so these tests are about the server side and the fetch
 * states. The fixed rule the old page broke — every visible section re-fetches
 * on a country change — is pinned by one test; the rest pin that every name,
 * colour and legend row comes with the data and nothing is defaulted.
 */
describe('DashboardComponent', () => {
    let fixture: ComponentFixture<DashboardComponent>;
    let component: DashboardComponent;
    let httpMock: HttpTestingController;
    let country$: BehaviorSubject<number | null>;
    let language$: BehaviorSubject<string>;

    const ALL = [...DASHBOARD_SECTION_CODES];

    const severity = (code: string, count: number, uiSeverity: string, sortOrder: number) =>
        ({ code, nameAr: `${code}-ar`, nameEn: `${code}-en`, uiSeverity, icon: null, sortOrder, count });

    /** Section code → endpoint path and a benign body. */
    const endpoints: Record<string, { path: string; body: unknown }> = {
        DASHBOARD_STUDENTS: { path: 'students', body: {
            total: 120, active: 100, newStatus: 5, suspended: 3, notActive: 20, newThisMonth: 7,
            monthly: [{ year: 2026, month: 3, count: 2 }, { year: 2026, month: 4, count: 5 }],
            byDelegationYear: [{ label: '2025', value: 40 }],
            byStatus: [
                { code: 'ACTIVE', nameAr: 'نشط', nameEn: 'Active', uiSeverity: 'success', icon: null, uiColor: '#16A34A', sortOrder: 1, count: 100 },
                { code: null, nameAr: null, nameEn: null, uiSeverity: null, icon: null, uiColor: null, sortOrder: null, count: 20 }
            ]
        } },
        DASHBOARD_FINANCE: { path: 'finance', body: { totalDisbursed: 5000, averageDisbursement: 250, byYear: [], wafar: { totalDeposits: 10, totalSpent: 4, currentBalance: 6, pendingAmount: 1, transactionCount: 3 } } },
        DASHBOARD_APPROVALS: { path: 'approvals', body: { pendingTotal: 4, myPending: 1, byEntityType: [
            { code: 'STUDENT_FINANCE', nameAr: 'مالية الطالب', nameEn: 'Student finance', uiSeverity: null, icon: null, uiColor: null, sortOrder: 1, count: 3 },
            { code: null, nameAr: null, nameEn: null, uiSeverity: null, icon: null, uiColor: null, sortOrder: null, count: 1 }
        ] } },
        DASHBOARD_ALERTS: { path: 'alert-cases', body: {
            totalOpen: 2, totalAcknowledged: 1, totalResolved: 0, totalDismissed: 0, totalAll: 3,
            openCritical: 1, openHigh: 1, openMedium: 0, openLow: 0,
            bySeverity: [severity('CRITICAL', 1, 'danger', 1), severity('HIGH', 1, 'warn', 2), severity('MEDIUM', 0, 'info', 3), severity('LOW', 0, 'success', 4), severity('TRIVIAL', 0, 'contrast', 5)],
            byStatus: []
        } },
        DASHBOARD_COUNTRIES: { path: 'countries', body: [{ label: 'Jordan', value: 30 }, { label: null, value: 2 }] },
        DASHBOARD_USERS: { path: 'users', body: { total: 40, newThisMonth: 2, monthly: [{ year: 2025, month: 12, count: 1 }], scoped: true } },
        DASHBOARD_QUARTER: { path: 'quarter', body: { present: true, quarterYear: 2026, quarterNumber: 3, quarterName: 'الربع الثالث', quarterNameEn: 'Q3', quarterClosed: false, countryId: null, quarterAmount: null, budgetAmount: null, paidAmount: 10, quarterStudentCount: 9, quarterPayDate: null } },
        DASHBOARD_NOTICES: { path: 'notices', body: { countrySpecific: false, yesterday: [], today: [
            {
                alertId: 1, alertTitle: 'Passport expiring', alertMessage: 'Two weeks left', createdBy: 'system', isRead: false,
                createdDate: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(), expiresDate: null,
                type: { code: 'WARNING', nameAr: 'تحذير', nameEn: 'Warning', uiSeverity: 'warn', icon: 'pi-exclamation-triangle' },
                priority: { code: 'HIGH', nameAr: 'عالية', nameEn: 'High', uiSeverity: 'warn', icon: null },
                status: null
            },
            { alertId: 2, alertTitle: 'Bare notice', alertMessage: null, createdBy: null, isRead: false, createdDate: null, expiresDate: null, type: null, priority: null, status: null },
            {
                alertId: 3, alertTitle: 'طلب موافقة جديد', alertMessage: 'طلب رقم 42: تحديث — بيانات الطالب.',
                alertTitleEn: 'New approval request', alertMessageEn: 'Request 42: Update — Student data.',
                createdBy: 'system', isRead: false, createdDate: null, expiresDate: null, type: null, priority: null,
                status: { code: 'RESOLVED', nameAr: 'تمت المعالجة', nameEn: 'Resolved', uiSeverity: 'success', icon: null }
            }
        ] } }
    };

    const sectionEl = (code: string): HTMLElement | null =>
        fixture.nativeElement.querySelector(`[data-section="${code}"]`);

    /** True when a p-tag carries the severity class, on its host or on its inner element. */
    const hasSeverity = (tag: Element | null, severity: string): boolean =>
        !!tag && (tag.classList.contains(`p-tag-${severity}`) || !!tag.querySelector(`.p-tag-${severity}`));

    /** Answers /sections, which starts the per-section loads. */
    function answerSections(codes: string[]) {
        httpMock.expectOne(r => r.url.endsWith('/dashboard/sections'))
            .flush({ sections: codes, all: codes.length === ALL.length });
        fixture.detectChanges();
    }

    /** Answers every outstanding section request; `fail` lists the codes to refuse with a 403. */
    function answerSectionsData(codes: string[], fail: string[] = []) {
        for (const code of codes) {
            const { path, body } = endpoints[code];
            const req = httpMock.expectOne(r => r.url.endsWith(`/dashboard/${path}`));
            if (fail.includes(code)) {
                req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
            } else {
                req.flush(body);
            }
        }
        fixture.detectChanges();
    }

    beforeEach(async () => {
        country$ = new BehaviorSubject<number | null>(null);
        language$ = new BehaviorSubject<string>('en');

        await TestBed.configureTestingModule({
            imports: [DashboardModule],
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ReportCountryScopeService, useValue: { selectedCountryId$: country$.asObservable(), selectedCountryId: null } },
                { provide: StudentdataService, useValue: { getRecentStudents: () => of([]) } },
                // QA C5 / DSH-002: the recent-students table resolves the major by id.
                { provide: LookupService, useValue: { getStudentMajors: () => of([{ id: 1, name: 'Physics' }]) } },
                { provide: LayoutService, useValue: { configUpdate$: new Subject<void>() } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: language$.asObservable(), initialized$: of(true), isReady: () => true,
                        isRTL: () => language$.value === 'ar', translate: (k: string) => k, getCurrentLanguage: () => language$.value
                    }
                },
                // Admin: *hasPermission passes everywhere, so the server's list alone decides.
                {
                    provide: UmsAuthService,
                    useValue: {
                        currentUserValue: { id: 1, isAdmin: true, isSuperAdmin: true },
                        permissions: [], permissions$: of([]),
                        isAdmin: () => true, isSuperAdmin: () => true, isLoggedIn: () => true
                    }
                },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, hasAnyPermission: () => true } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(DashboardComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
    });

    afterEach(() => {
        // p-dialog appends to document.body, outside the fixture: destroying the
        // component is not enough, and a leftover would be visible to the next
        // spec's document queries.
        fixture.destroy();
        document.querySelectorAll('.dashboard-chart-dialog, .p-dialog-mask').forEach(el => el.remove());
    });

    afterEach(() => httpMock.verify());

    it('renders only the sections /sections lists, and fetches only those', () => {
        answerSections(['DASHBOARD_STUDENTS', 'DASHBOARD_USERS']);

        // Nothing else was asked for.
        httpMock.expectNone(r => r.url.endsWith('/dashboard/finance'));
        httpMock.expectNone(r => r.url.endsWith('/dashboard/notices'));
        answerSectionsData(['DASHBOARD_STUDENTS', 'DASHBOARD_USERS']);

        expect(component.all).toBeFalse();
        expect(sectionEl('DASHBOARD_STUDENTS')).withContext('listed section renders').not.toBeNull();
        expect(sectionEl('DASHBOARD_USERS')).not.toBeNull();
        for (const code of ALL.filter(c => c !== 'DASHBOARD_STUDENTS' && c !== 'DASHBOARD_USERS')) {
            expect(sectionEl(code)).withContext(`${code} is not listed`).toBeNull();
        }
        expect(sectionEl('DASHBOARD_STUDENTS')!.textContent).toContain('120');
    });

    it('shows one section\'s empty state on a 403 and leaves the others intact', () => {
        answerSections(ALL);
        answerSectionsData(ALL, ['DASHBOARD_FINANCE']);

        const finance = sectionEl('DASHBOARD_FINANCE')!;
        expect(finance.querySelector('.dashboard-unavailable')).withContext('finance shows SECTION_UNAVAILABLE').not.toBeNull();
        expect(finance.textContent).toContain('DASHBOARD.SECTION_UNAVAILABLE');
        expect(component.finance.error).toBeTrue();
        expect(component.finance.data).toBeNull();

        for (const code of ALL.filter(c => c !== 'DASHBOARD_FINANCE')) {
            const el = sectionEl(code)!;
            expect(el).withContext(`${code} still renders`).not.toBeNull();
            expect(el.querySelector('.dashboard-unavailable')).withContext(`${code} is not in its empty state`).toBeNull();
        }
        expect(sectionEl('DASHBOARD_QUARTER')!.textContent).toContain('Q3');
        expect(sectionEl('DASHBOARD_QUARTER')!.textContent).toContain('DASHBOARD.QUARTER_GLOBAL');
    });

    /**
     * A 500 is not a refusal: the reader may see the section, it just did not
     * arrive. It used to read "not available to you" all the same (QA B9, N7).
     */
    it('says a section could not be loaded, not that it is unavailable, and retries it', () => {
        answerSections(['DASHBOARD_FINANCE']);
        httpMock.expectOne(r => r.url.endsWith('/dashboard/finance'))
            .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        const finance = sectionEl('DASHBOARD_FINANCE')!;
        expect(finance.querySelector('.dashboard-unavailable')).withContext('a failure read as a refusal').toBeNull();
        expect(finance.querySelector('.dashboard-failed')).not.toBeNull();
        expect(finance.textContent).toContain('DASHBOARD.SECTION_LOAD_FAILED');

        (finance.querySelector('.dashboard-failed button') as HTMLButtonElement).click();
        httpMock.expectOne(r => r.url.endsWith('/dashboard/finance')).flush(endpoints['DASHBOARD_FINANCE'].body);
        fixture.detectChanges();
        expect(sectionEl('DASHBOARD_FINANCE')!.querySelector('.dashboard-failed')).withContext('retry did not recover').toBeNull();
    });

    it('re-fetches every visible section when the country changes', () => {
        answerSections(ALL);
        answerSectionsData(ALL);

        country$.next(1004);

        const again = httpMock.match(r => r.url.includes('/dashboard/'));
        const paths = again.map(r => r.request.url.slice(r.request.url.lastIndexOf('/') + 1)).sort();
        expect(paths).toEqual(ALL.map(c => endpoints[c].path).sort());
        // No countryId param: the header carries the country.
        for (const r of again) {
            expect(r.request.params.has('countryId')).toBeFalse();
            r.flush(endpoints[ALL.find(c => r.request.url.endsWith(endpoints[c].path))!].body);
        }
        fixture.detectChanges();
        expect(component.students.data?.total).toBe(120);
    });

    it('captions the users figure as ministry-wide when scoped is false', () => {
        answerSections(['DASHBOARD_USERS']);
        httpMock.expectOne(r => r.url.endsWith('/dashboard/users'))
            .flush({ total: 40, newThisMonth: 2, monthly: [], scoped: false });
        fixture.detectChanges();

        const caption = sectionEl('DASHBOARD_USERS')!.querySelector('.dashboard-users-scope')!;
        expect(caption.textContent).toContain('DASHBOARD.USERS_SCOPE_ALL');
        expect(caption.textContent).not.toContain('DASHBOARD.USERS_SCOPE_COUNTRY');
    });

    it('captions the users figure as country-scoped when scoped is true', () => {
        answerSections(['DASHBOARD_USERS']);
        answerSectionsData(['DASHBOARD_USERS']);

        const caption = sectionEl('DASHBOARD_USERS')!.querySelector('.dashboard-users-scope')!;
        expect(caption.textContent).toContain('DASHBOARD.USERS_SCOPE_COUNTRY');
    });

    it('labels the notices as not country-specific', () => {
        answerSections(['DASHBOARD_NOTICES']);
        httpMock.expectOne(r => r.url.endsWith('/dashboard/notices')).flush({ today: [], yesterday: [], countrySpecific: false });
        fixture.detectChanges();

        const notices = sectionEl('DASHBOARD_NOTICES')!;
        expect(notices.querySelector('.dashboard-notices-scope')!.textContent).toContain('DASHBOARD.NOTICES_NOT_COUNTRY_SPECIFIC');
        expect(notices.textContent).toContain('DASHBOARD.NO_ALERTS_TODAY');
    });

    it('shows the no-open-quarter state when present is false', () => {
        answerSections(['DASHBOARD_QUARTER']);
        httpMock.expectOne(r => r.url.endsWith('/dashboard/quarter'))
            .flush({ present: false, quarterYear: null, quarterNumber: null, quarterName: null, quarterNameEn: null, quarterClosed: false, countryId: null, quarterAmount: null, budgetAmount: null, paidAmount: null, quarterStudentCount: 0, quarterPayDate: null });
        fixture.detectChanges();

        const quarter = sectionEl('DASHBOARD_QUARTER')!;
        expect(quarter.textContent).toContain('DASHBOARD.NO_OPEN_QUARTER');
        expect(quarter.querySelector('.dashboard-unavailable')).withContext('an absent quarter is not an error').toBeNull();
    });

    // ── everything from the data ──────────────────────────────────────────────

    it('draws the severity legend from the rows the server sends, in their order, zero counts included', () => {
        answerSections(['DASHBOARD_ALERTS']);
        answerSectionsData(['DASHBOARD_ALERTS']);

        const tags = Array.from(sectionEl('DASHBOARD_ALERTS')!.querySelectorAll('.dashboard-severity-legend p-tag')) as HTMLElement[];
        expect(tags.length).withContext('five rows, not a fixed four').toBe(5);
        expect(tags.map(t => t.textContent!.trim())).toEqual(['CRITICAL-en', 'HIGH-en', 'MEDIUM-en', 'LOW-en', 'TRIVIAL-en']);
        expect(hasSeverity(tags[0], 'danger')).withContext('severity is the row\'s own').toBeTrue();
        expect(hasSeverity(tags[4], 'contrast')).toBeTrue();
        expect(sectionEl('DASHBOARD_ALERTS')!.textContent).not.toContain('ALERT_EVENTS.SEVERITY.');
    });

    it('re-labels the legend when the language changes, without a request', () => {
        answerSections(['DASHBOARD_ALERTS']);
        answerSectionsData(['DASHBOARD_ALERTS']);

        language$.next('ar');
        fixture.detectChanges();

        httpMock.expectNone(r => r.url.endsWith('/dashboard/alert-cases'));
        const first = sectionEl('DASHBOARD_ALERTS')!.querySelector('.dashboard-severity-legend p-tag')!;
        expect(first.textContent!.trim()).toBe('CRITICAL-ar');
    });

    it('labels months in the reader\'s locale from (year, month) points', () => {
        answerSections(['DASHBOARD_STUDENTS']);
        answerSectionsData(['DASHBOARD_STUDENTS']);

        expect(component.monthlyStudentsChartData.labels).toEqual(['Mar', 'Apr']);
        expect(component.monthlyStudentsChartData.datasets[0].data).toEqual([2, 5]);

        language$.next('ar');
        fixture.detectChanges();
        const arabic = component.monthlyStudentsChartData.labels as string[];
        expect(arabic[0]).toBe(new Intl.DateTimeFormat('ar', { month: 'short' }).format(new Date(2026, 2, 1)));
        expect(arabic[0]).not.toBe('Mar');
    });

    it('colours the status donut with each row\'s own colour and names a null code "unspecified"', () => {
        answerSections(['DASHBOARD_STUDENTS']);
        answerSectionsData(['DASHBOARD_STUDENTS']);

        const donut = component.statusChartData;
        expect(donut.labels).toEqual(['Active', 'COMMON.UNSPECIFIED']);
        expect(donut.datasets[0].backgroundColor[0]).toBe('#16A34A');
        expect(donut.datasets[0].data).toEqual([100, 20]);
    });

    it('names approval types from the rows and shows a null type as unspecified', () => {
        answerSections(['DASHBOARD_APPROVALS']);
        answerSectionsData(['DASHBOARD_APPROVALS']);

        const items = Array.from(sectionEl('DASHBOARD_APPROVALS')!.querySelectorAll('.dashboard-kv li'))
            .map(li => Array.from(li.querySelectorAll('span')).map(s => s.textContent!.trim()));
        expect(items).toEqual([['Student finance', '3'], ['COMMON.UNSPECIFIED', '1']]);
    });

    it('labels a country with no name as unspecified', () => {
        answerSections(['DASHBOARD_COUNTRIES']);
        answerSectionsData(['DASHBOARD_COUNTRIES']);

        expect(component.topCountriesChartData.labels).toEqual(['Jordan', 'COMMON.UNSPECIFIED']);
    });

    it('renders notices as served — resolved names, relative time, and "—" for what is missing, never a default', () => {
        answerSections(['DASHBOARD_NOTICES']);
        answerSectionsData(['DASHBOARD_NOTICES']);

        const rows = Array.from(sectionEl('DASHBOARD_NOTICES')!.querySelectorAll('.dashboard-notice')) as HTMLElement[];
        expect(rows.length).toBe(3);

        const full = rows[0];
        const priority = (row: HTMLElement) => row.querySelector('p-tag.dashboard-notice-priority, .dashboard-notice-priority') as HTMLElement;
        const type = (row: HTMLElement) => row.querySelector('p-tag.dashboard-notice-type, .dashboard-notice-type') as HTMLElement;
        expect(priority(full).textContent).toContain('High');
        expect(hasSeverity(priority(full), 'warn')).toBeTrue();
        expect(type(full).textContent).toContain('Warning');
        expect(type(full).querySelector('.pi-exclamation-triangle')).not.toBeNull();
        expect(full.textContent).toContain(new Intl.RelativeTimeFormat('en', { numeric: 'auto' }).format(-3, 'hour'));

        const bare = rows[1];
        expect(priority(bare).textContent!.trim()).toBe('—');
        expect(hasSeverity(priority(bare), 'secondary')).withContext('no severity → neutral, not LOW/success').toBeTrue();
        expect(bare.textContent).not.toContain('LOW');
        expect(bare.textContent).not.toContain('INFO');
        expect(component.todayAlerts[1].createdDate).withContext('a missing date stays missing').toBeNull();
        expect(bare.querySelector('.dashboard-notice-status')).withContext('no status, no tag').toBeNull();
    });

    it('shows a notice in the reader\'s language with its status, so a decided request no longer reads as waiting (QA H2)', () => {
        answerSections(['DASHBOARD_NOTICES']);
        answerSectionsData(['DASHBOARD_NOTICES']);

        const rows = Array.from(sectionEl('DASHBOARD_NOTICES')!.querySelectorAll('.dashboard-notice')) as HTMLElement[];
        const approval = rows[2];
        expect(approval.textContent).toContain('New approval request');
        expect(approval.textContent).toContain('Request 42: Update — Student data.');
        expect(approval.textContent).not.toContain('طلب موافقة جديد');
        const status = approval.querySelector('p-tag.dashboard-notice-status, .dashboard-notice-status') as HTMLElement;
        expect(status.textContent).toContain('Resolved');
        expect(hasSeverity(status, 'success')).toBeTrue();
        expect(rows[0].textContent).withContext('a notice with no English text keeps its own').toContain('Passport expiring');
    });

    it('shows "—" for a quarter figure the server did not send', () => {
        answerSections(['DASHBOARD_QUARTER']);
        answerSectionsData(['DASHBOARD_QUARTER']);

        const values = Array.from(sectionEl('DASHBOARD_QUARTER')!.querySelectorAll('.summary-value')).map(v => v.textContent!.trim());
        expect(values[0]).withContext('budget null').toBe('—');
        expect(values[1]).withContext('paid 10').toBe('10');
    });

    it('derives its permission arrays from the section code', () => {
        answerSections([]);
        expect(component.permissionsFor('DASHBOARD_FINANCE')).toEqual(['DASHBOARD.VIEW', 'DASHBOARD_FINANCE.VIEW']);
        expect(component.sectionCodes).toBe(DASHBOARD_SECTION_CODES);
    });

    it('opens a chart at full size when it is clicked, showing the data the card holds', () => {
        answerSections(['DASHBOARD_STUDENTS']);
        answerSectionsData(['DASHBOARD_STUDENTS']);

        // Every chart on the page is reachable: it is a real button, so the
        // keyboard gets there too, and it carries an accessible name.
        const buttons = Array.from(
            sectionEl('DASHBOARD_STUDENTS')!.querySelectorAll('button.dashboard-chart-open')
        ) as HTMLButtonElement[];
        expect(buttons.length).withContext('registrations, delegation year, status').toBe(3);
        buttons.forEach(b => expect(b.getAttribute('aria-label')).toBe('DASHBOARD.EXPAND_CHART'));

        expect(document.querySelector('.dashboard-chart-dialog canvas'))
            .withContext('nothing enlarged until asked').toBeNull();

        buttons[1].click();
        fixture.detectChanges();

        expect(component.expandedChart!.titleKey).toBe('DASHBOARD.ENROLLMENT_BY_YEAR');
        expect(component.expandedChart!.type).toBe('bar');
        // The same objects as the card, so the two views cannot disagree.
        expect(component.expandedChart!.data).toBe(component.delegationYearChartData);
        expect(component.expandedChart!.options).toBe(component.delegationYearChartOptions);

        const dialog = document.querySelector('.dashboard-chart-dialog');
        expect(dialog).withContext('the enlarged chart is on screen').not.toBeNull();
        expect(dialog!.querySelector('canvas')).not.toBeNull();
        expect(dialog!.textContent).toContain('DASHBOARD.ENROLLMENT_BY_YEAR');
    });

    it('closes the enlarged chart through the dialog close paths', () => {
        answerSections(['DASHBOARD_STUDENTS']);
        answerSectionsData(['DASHBOARD_STUDENTS']);

        (sectionEl('DASHBOARD_STUDENTS')!.querySelector('button.dashboard-chart-open') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.expandedChart).not.toBeNull();

        // p-dialog emits visibleChange when the X, Escape or the mask closes it.
        component.onExpandedVisibleChange(false);
        fixture.detectChanges();
        expect(component.expandedChart).toBeNull();

        // The chart is inside an @if, so it is destroyed at once. PrimeNG keeps
        // the dialog element itself mounted and hides it when its leave
        // animation ends, which is why the assertion is about the content: it
        // needs no waiting and so cannot make this spec depend on timing.
        expect(document.querySelector('.dashboard-chart-dialog canvas')).toBeNull();
    });

    it('has nothing to open when a section reports no data', () => {
        answerSections(['DASHBOARD_COUNTRIES']);
        httpMock.expectOne(r => r.url.endsWith('/dashboard/countries')).flush([]);
        fixture.detectChanges();

        expect(sectionEl('DASHBOARD_COUNTRIES')!.querySelector('button.dashboard-chart-open')).toBeNull();
        component.expandChart('DASHBOARD.SECTIONS.COUNTRIES', 'bar', null, {});
        expect(component.expandedChart).withContext('no chart, nothing to enlarge').toBeNull();
    });

    /*
     * The chart options, which the August review reported as "change the
     * Chart.js library". The library was not the problem — these settings were,
     * and the screenshot shows all of them at once: a y-axis reading 0, 0.4, 0.8
     * for a count of people, charts letterboxed inside the boxes the template
     * sizes, a legend chip repeating the card's own title, and an Arabic page
     * whose years ran left to right.
     */
    describe('chart options', () => {

        const optionsOf = (chart: 'delegationYear' | 'monthlyStudents' | 'topCountries') =>
            (component as any)[`${chart}ChartOptions`];

        it('counts in whole students, starting at zero', () => {
            answerSections(['DASHBOARD_STUDENTS']);
            answerSectionsData(['DASHBOARD_STUDENTS']);

            const y = optionsOf('delegationYear').scales.y;
            expect(y.ticks.precision).withContext('no fifths of a person on the axis').toBe(0);
            expect(y.beginAtZero).toBeTrue();
        });

        it('fills the box the card gives it', () => {
            answerSections(['DASHBOARD_STUDENTS']);
            answerSectionsData(['DASHBOARD_STUDENTS']);

            // Chart.js keeps a 2:1 ratio unless told otherwise, so the template's
            // height was only ever an upper bound. The donut set this and the
            // axis charts did not, which is why only the donut looked right.
            expect(optionsOf('delegationYear').maintainAspectRatio).toBeFalse();
            expect(optionsOf('monthlyStudents').maintainAspectRatio).toBeFalse();
        });

        it('draws no legend for a chart with one series', () => {
            answerSections(['DASHBOARD_STUDENTS']);
            answerSectionsData(['DASHBOARD_STUDENTS']);

            expect(optionsOf('delegationYear').plugins.legend.display).toBeFalse();
            // The dataset label stays — it is what the tooltip says.
            expect(component.delegationYearChartData.datasets[0].label).toBeTruthy();
        });

        it('reads right-to-left in Arabic, and left-to-right in English', () => {
            answerSections(['DASHBOARD_STUDENTS']);
            answerSectionsData(['DASHBOARD_STUDENTS']);
            expect(optionsOf('delegationYear').scales.x.reverse)
                .withContext('English').toBeFalse();

            language$.next('ar');
            fixture.detectChanges();

            const rtl = optionsOf('delegationYear');
            expect(rtl.scales.x.reverse).withContext('Arabic years run right to left').toBeTrue();
            expect(rtl.plugins.tooltip.rtl).toBeTrue();
            expect(rtl.plugins.tooltip.textDirection).toBe('rtl');
        });

        it('applies the same corrections to the horizontal bars, the other way round', () => {
            // The countries chart puts categories on y and counts on x, so its
            // value axis is the one that must count in whole people.
            answerSections(['DASHBOARD_COUNTRIES']);
            answerSectionsData(['DASHBOARD_COUNTRIES']);

            const options = optionsOf('topCountries');
            expect(options.indexAxis).toBe('y');
            expect(options.maintainAspectRatio).toBeFalse();
            expect(options.scales.x.ticks.precision).toBe(0);
            expect(options.scales.x.beginAtZero).toBeTrue();
        });
    });

    describe('the major column (QA C5 / DSH-002)', () => {

        // majorOf is pure, but the component is live: answer the sections call
        // so the afterEach verify has nothing outstanding.
        beforeEach(() => answerSections([]));

        // A student added through the wizard has the lookup id and no free-text
        // name; the table showed "—" for every one of them.
        it('names the major from the id when the free-text column is empty', () => {
            component.majorMap.set(1, 'Physics');
            expect(component.majorOf({ studentMajorId: 1, studentMajorName: null } as any)).toBe('Physics');
        });

        it('keeps the free-text name when the id resolves to nothing', () => {
            expect(component.majorOf({ studentMajorId: 99, studentMajorName: 'Law' } as any)).toBe('Law');
        });

        it('shows a dash when it has neither', () => {
            expect(component.majorOf({ studentMajorId: 0, studentMajorName: null } as any)).toBe('—');
        });
    });
});
