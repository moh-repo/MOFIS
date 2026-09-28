import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subscription, debounceTime } from 'rxjs';
import { LayoutService } from 'src/app/layout/service/app.layout.service';
import { StudentdataService } from '../../service/studentdata.service';
import { Studentdata } from '../../api/studentdata';
import { Alert, CodeRef, codeRefName, timeAgo } from '../../api/alert';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { LookupService } from '../../service/lookup.service';
import { CodeListService, PrimeSeverity } from '../../service/code-list.service';
import {
    CodeCount,
    DASHBOARD_SECTION_CODES,
    DashboardAlertCasesDto,
    DashboardApprovalsDto,
    DashboardFinanceDto,
    DashboardNoticesDto,
    DashboardPoint,
    DashboardQuarterDto,
    DashboardSectionCode,
    DashboardService,
    DashboardStudentsDto,
    DashboardUsersDto,
    MonthPoint
} from '../../service/dashboard.service';

/** One section's fetch: spinner while loading, empty state on error or no body, content otherwise. */
export interface SectionState<T> {
    loading: boolean;
    error: boolean;
    /** The error was a 403: the section is not the reader's to see, rather than broken. */
    forbidden?: boolean;
    data: T | null;
}

const idle = <T>(): SectionState<T> => ({ loading: false, error: false, data: null });

/**
 * The ministry landing page.
 *
 * What renders is decided twice: `*hasPermission` (the client's own permission
 * list) and `/dashboard/sections` (what the server's authorities open). A
 * section shows only when both agree. Every visible section re-fetches on a
 * country change — the old page skipped alerts and recent students there, so
 * a Jordan-only view still carried ministry-wide notices and rows.
 *
 * Every name, colour and legend row on the page comes with the data: the
 * server resolves each code against its code-list row (both names, severity,
 * icon, colour), month points arrive as (year, month) and are labelled here in
 * the reader's locale, and nothing missing is defaulted — a null renders `—`.
 *
 * Language and theme changes rebuild the chart configs from the last payloads;
 * they never re-fetch.
 */
@Component({
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss'],
    standalone: false
})
export class DashboardComponent implements OnInit, OnDestroy {

    /** The one list of section codes; the guard list in app-routing is asserted equal in the spec. */
    readonly sectionCodes = DASHBOARD_SECTION_CODES;

    /** Section codes `/sections` listed for this caller. */
    visible = new Set<DashboardSectionCode>();
    /** True when the caller holds DASHBOARD.VIEW or is an admin. */
    all = false;
    sectionsLoading = true;
    sectionsError = false;

    currentLanguage = 'en';

    students: SectionState<DashboardStudentsDto> = idle();
    finance: SectionState<DashboardFinanceDto> = idle();
    approvals: SectionState<DashboardApprovalsDto> = idle();
    alertCases: SectionState<DashboardAlertCasesDto> = idle();
    countries: SectionState<DashboardPoint[]> = idle();
    users: SectionState<DashboardUsersDto> = idle();
    quarter: SectionState<DashboardQuarterDto> = idle();
    notices: SectionState<DashboardNoticesDto> = idle();

    todayAlerts: Alert[] = [];
    yesterdayAlerts: Alert[] = [];

    recentStudents: Studentdata[] = [];
    recentStudentsLoading = false;

    /**
     * Major names by id (QA C5 / DSH-002). The recent-students rows carry both
     * STUDENT_MAJOR_NAME, a free-text column, and the lookup id; the add-student
     * wizard fills only the id, so every student added through it showed "—".
     * The students list and its view dialog resolve it this way already.
     */
    majorMap = new Map<number, string>();

    // Chart configs, rebuilt from the section payloads on language/theme change.
    monthlyStudentsChartData: any;
    monthlyStudentsChartOptions: any;
    delegationYearChartData: any;
    delegationYearChartOptions: any;
    statusChartData: any;
    statusChartOptions: any;
    disbursementChartData: any;
    disbursementChartOptions: any;
    topCountriesChartData: any;
    topCountriesChartOptions: any;
    monthlyUsersChartData: any;
    monthlyUsersChartOptions: any;

    private readonly subscriptions = new Subscription();
    private countrySubscription?: Subscription;

    /** What each section code loads; derived from DASHBOARD_SECTION_CODES, never listed again. */
    private readonly loaders: Record<DashboardSectionCode, () => void> = {
        DASHBOARD_STUDENTS: () => {
            this.loadStudents();
            if (this.canViewStudents()) {
                this.loadRecentStudents();
            }
        },
        DASHBOARD_FINANCE: () => this.loadFinance(),
        DASHBOARD_APPROVALS: () => this.loadApprovals(),
        DASHBOARD_ALERTS: () => this.loadAlertCases(),
        DASHBOARD_COUNTRIES: () => this.loadCountries(),
        DASHBOARD_USERS: () => this.loadUsers(),
        DASHBOARD_QUARTER: () => this.loadQuarter(),
        DASHBOARD_NOTICES: () => this.loadNotices()
    };

    constructor(
        public layoutService: LayoutService,
        private dashboardService: DashboardService,
        private studentDataService: StudentdataService,
        private translationService: TranslationService,
        private umsAuthService: UmsAuthService,
        private lookupService: LookupService
    ) { }

    ngOnInit() {
        this.currentLanguage = this.translationService.getCurrentLanguage();
        this.subscriptions.add(
            this.layoutService.configUpdate$.pipe(debounceTime(25)).subscribe(() => this.rebuildCharts())
        );
        this.subscriptions.add(
            this.translationService.currentLanguage$.subscribe(lang => {
                this.currentLanguage = lang;
                this.rebuildCharts();
            })
        );
        this.loadSections();
    }

    ngOnDestroy() {
        this.subscriptions.unsubscribe();
        this.countrySubscription?.unsubscribe();
    }

    isVisible(code: DashboardSectionCode): boolean {
        return this.visible.has(code);
    }

    /** Any-of codes for `*hasPermission`: the whole dashboard, or this one section. */
    permissionsFor(code: DashboardSectionCode): string[] {
        return ['DASHBOARD.VIEW', `${code}.VIEW`];
    }

    /** Recent-students rows come from /studentdata/recent, which is gated by STUDENTS.* on the server. */
    canViewStudents(): boolean {
        const u = this.umsAuthService.currentUserValue;
        if (u?.isAdmin || u?.isSuperAdmin) return true;
        return (this.umsAuthService.permissions || []).some(p =>
            p.startsWith('STUDENTS.') || p.startsWith('STUDENTS_LIST.') || p.startsWith('STUDENTS_ADD.'));
    }

    // ── sections ──────────────────────────────────────────────────────────────

    loadSections() {
        this.sectionsLoading = true;
        this.sectionsError = false;
        this.dashboardService.getSections().subscribe({
            next: dto => {
                this.visible = new Set((dto?.sections || []) as DashboardSectionCode[]);
                this.all = !!dto?.all;
                this.sectionsLoading = false;
                // The country stream emits its current value on subscribe, so this
                // is also the first load. Each later emission reloads every visible
                // section under the new X-Country-Id.
                this.countrySubscription?.unsubscribe();
                this.countrySubscription = this.dashboardService.selectedCountryId$
                    .subscribe(() => this.loadVisibleSections());
            },
            error: () => {
                this.visible = new Set();
                this.all = false;
                this.sectionsLoading = false;
                this.sectionsError = true;
            }
        });
    }

    /** The "could not be loaded" state's retry. */
    retrySection(code: DashboardSectionCode) {
        this.loaders[code]();
    }

    loadVisibleSections() {
        for (const code of DASHBOARD_SECTION_CODES) {
            if (this.isVisible(code)) {
                this.loaders[code]();
            }
        }
    }

    loadStudents() {
        this.load(this.students, this.dashboardService.getStudents(), () => this.buildStudentCharts());
    }

    loadFinance() {
        this.load(this.finance, this.dashboardService.getFinance(), () => this.buildFinanceCharts());
    }

    loadApprovals() {
        this.load(this.approvals, this.dashboardService.getApprovals());
    }

    loadAlertCases() {
        this.load(this.alertCases, this.dashboardService.getAlertCases());
    }

    loadCountries() {
        this.load(this.countries, this.dashboardService.getCountries(), () => this.buildCountriesChart());
    }

    loadUsers() {
        this.load(this.users, this.dashboardService.getUsers(), () => this.buildUsersChart());
    }

    loadQuarter() {
        this.load(this.quarter, this.dashboardService.getQuarter());
    }

    loadNotices() {
        this.load(this.notices, this.dashboardService.getNotices(), () => {
            // As served: a missing priority stays null and renders as '—'.
            this.todayAlerts = this.notices.data?.today || [];
            this.yesterdayAlerts = this.notices.data?.yesterday || [];
        });
    }

    /**
     * One fetch into one state record. A failure (a stale-permission 403, or
     * anything else) is the section's empty state — that is the intended
     * rendering, so it is recorded as `error` rather than swallowed.
     */
    private load<T>(state: SectionState<T>, source: Observable<T>, then?: () => void) {
        state.loading = true;
        state.error = false;
        state.forbidden = false;
        source.subscribe({
            next: data => {
                state.data = data ?? null;
                state.loading = false;
                then?.();
            },
            error: (err) => {
                state.data = null;
                state.error = true;
                state.forbidden = err?.status === 403;
                state.loading = false;
                then?.();
            }
        });
    }

    /** The major of a row: the lookup first, then the free-text column. */
    majorOf(student: Studentdata): string {
        return this.majorMap.get(student.studentMajorId) || student.studentMajorName || '—';
    }

    loadRecentStudents() {
        this.recentStudentsLoading = true;
        if (this.majorMap.size === 0) {
            // Names only; a failure leaves the free-text column to answer, which
            // is what the table did before.
            this.lookupService.getStudentMajors().subscribe({
                next: items => items.forEach(i => this.majorMap.set(i.id, i.name)),
                error: () => undefined
            });
        }
        this.studentDataService.getRecentStudents().subscribe({
            next: (data: Studentdata[] | { content?: Studentdata[] }) => {
                this.recentStudents = Array.isArray(data) ? data : (data?.content || []);
                this.recentStudentsLoading = false;
            },
            error: () => {
                // The table's own empty message is the rendering for this.
                this.recentStudents = [];
                this.recentStudentsLoading = false;
            }
        });
    }

    // ── derived display ───────────────────────────────────────────────────────

    /** Quarter name in the reader's language; the English name falls back to the Arabic one. */
    get quarterDisplayName(): string {
        const q = this.quarter.data;
        if (!q) return '';
        const arabic = this.currentLanguage === 'ar';
        return (arabic ? q.quarterName : (q.quarterNameEn || q.quarterName)) || '';
    }

    /** The name of a resolved code row in the reader's language; a null code is "unspecified". */
    codeName(row: { code: string | null; nameAr: string | null; nameEn: string | null } | null | undefined): string {
        if (!row || row.code == null) {
            return this.translationService.translate('COMMON.UNSPECIFIED');
        }
        return codeRefName({ code: row.code, nameAr: row.nameAr, nameEn: row.nameEn, uiSeverity: null, icon: null }, this.currentLanguage);
    }

    /** A resolved code on a notice; '—' when the notice has none. */
    refName(ref: CodeRef | null | undefined): string {
        return ref ? (codeRefName(ref, this.currentLanguage) || '—') : '—';
    }

    /** The row's severity, held to PrimeNG's vocabulary. */
    tagSeverity(value: string | null | undefined): PrimeSeverity {
        return CodeListService.toSeverity(value);
    }

    /** "3 hours ago" in the reader's language; '' when the notice has no date. */
    timeAgo(date: string | null | undefined): string {
        return timeAgo(date, this.currentLanguage);
    }

    /** Short month name in the reader's locale, e.g. "Mar" / "مارس". */
    monthLabel(p: MonthPoint): string {
        return new Intl.DateTimeFormat(this.currentLanguage, { month: 'short' }).format(new Date(p.year, p.month - 1, 1));
    }

    /** A point's label; a null label is "unspecified". */
    pointLabel(p: DashboardPoint): string {
        return p.label ?? this.translationService.translate('COMMON.UNSPECIFIED');
    }

    // ── charts ────────────────────────────────────────────────────────────────

    private rebuildCharts() {
        this.buildStudentCharts();
        this.buildFinanceCharts();
        this.buildCountriesChart();
        this.buildUsersChart();
    }

    private theme() {
        const style = getComputedStyle(document.documentElement);
        return {
            textColor: style.getPropertyValue('--text-color'),
            textColorSecondary: style.getPropertyValue('--text-color-secondary'),
            surfaceBorder: style.getPropertyValue('--surface-border'),
            primary: style.getPropertyValue('--primary-color') || '#3B82F6',
            bluegray: style.getPropertyValue('--bluegray-500') || '#64748B',
            bluegrayDark: style.getPropertyValue('--bluegray-700') || '#475569',
            green: style.getPropertyValue('--green-600') || '#16A34A',
            /** For a status row that carries no colour of its own (the unspecified slice). */
            neutral: style.getPropertyValue('--surface-400') || '#94A3B8'
        };
    }

    /**
     * The options every chart with axes on this page shares.
     *
     * <p>Four things here answer the August review's note on the "quick report"
     * charts, which asked for the charting library to be changed. The library was
     * not the problem; these settings were.
     *
     * <p><b>It fills the card.</b> Chart.js keeps a 2:1 ratio unless told not to,
     * so a chart in a 260 px box was letterboxed inside it. The donut set this and
     * the axis charts did not, which is why only the donut looked right.
     *
     * <p><b>It counts in whole people.</b> With no `precision` a maximum of one
     * produces an axis reading 0, 0.4, 0.8 — which is what the reviewer
     * photographed, and what any small cohort would produce again.
     *
     * <p><b>No legend.</b> Every chart here draws one series, so the legend chip
     * only repeated the card's own title. The dataset label stays: it is what the
     * tooltip says.
     *
     * <p><b>It reads the way the page reads.</b> Chart.js does not follow the
     * document's direction on its own, so an Arabic page had a year axis running
     * left to right and tooltips aligned the wrong way.
     *
     * @param extra merged last, so a chart can override any of it
     */
    private axesOptions(extra: Record<string, unknown> = {}) {
        const t = this.theme();
        const rtl = this.translationService.isRTL();
        return {
            maintainAspectRatio: false,
            // The end labels get room of their own (QA C4 / DSH-005). Chart.js
            // centres the first and last tick on the axis end, so half of that
            // label sits outside the canvas and is clipped -- "مارس" was drawn
            // as "ارس". It shows in Arabic first only because `reverse` puts a
            // different month at the edge, so the padding is not conditional.
            layout: { padding: { left: 12, right: 12 } },
            plugins: {
                legend: { display: false, rtl, labels: { color: t.textColor } },
                tooltip: { rtl, textDirection: rtl ? 'rtl' : 'ltr' }
            },
            scales: {
                x: {
                    reverse: rtl,
                    ticks: { color: t.textColorSecondary },
                    grid: { color: t.surfaceBorder, drawBorder: false }
                },
                y: {
                    beginAtZero: true,
                    ticks: { color: t.textColorSecondary, precision: 0 },
                    grid: { color: t.surfaceBorder, drawBorder: false }
                }
            },
            ...extra
        };
    }

    private buildStudentCharts() {
        const s = this.students.data;
        if (!s) {
            this.monthlyStudentsChartData = null;
            this.delegationYearChartData = null;
            this.statusChartData = null;
            return;
        }
        const t = this.theme();

        const monthly = s.monthly || [];
        this.monthlyStudentsChartData = monthly.length ? {
            labels: monthly.map(p => this.monthLabel(p)),
            datasets: [{
                label: this.translationService.translate('DASHBOARD.CHART_NEW_STUDENTS'),
                data: monthly.map(p => p.count),
                fill: false,
                backgroundColor: t.bluegrayDark,
                borderColor: t.bluegrayDark,
                tension: .4
            }]
        } : null;
        this.monthlyStudentsChartOptions = this.axesOptions();

        const years = s.byDelegationYear || [];
        this.delegationYearChartData = years.length ? {
            labels: years.map(p => this.pointLabel(p)),
            datasets: [{
                label: this.translationService.translate('DASHBOARD.ENROLLMENT_BY_YEAR_DATASET'),
                data: years.map(p => p.value),
                backgroundColor: t.bluegray,
                borderRadius: 4
            }]
        } : null;
        this.delegationYearChartOptions = this.axesOptions();

        this.statusChartData = this.buildStatusDonut(s.byStatus || []);
        // The donut keeps its legend — it has several slices and the names are the
        // only thing identifying them — but the legend and tooltip follow the
        // page's direction like the axis charts now do.
        const rtl = this.translationService.isRTL();
        this.statusChartOptions = {
            plugins: {
                legend: { position: 'bottom', rtl, labels: { color: t.textColor, usePointStyle: true } },
                tooltip: { rtl, textDirection: rtl ? 'rtl' : 'ltr' }
            },
            maintainAspectRatio: false
        };
    }

    /** Slice colour = the status row's own colour (STUDENT_STUDY_STATUS.UI_COLOR); no index palette. */
    private buildStatusDonut(rows: CodeCount[]): any {
        if (!rows.length) return null;
        const neutral = this.theme().neutral;
        const colours = rows.map(r => r.uiColor || neutral);
        return {
            labels: rows.map(r => this.codeName(r)),
            datasets: [{
                label: this.translationService.translate('DASHBOARD.STUDENTS_BY_STATUS'),
                data: rows.map(r => Number(r.count)),
                backgroundColor: colours,
                borderColor: colours.map(() => '#ffffff'),
                borderWidth: 1,
                hoverOffset: 8
            }]
        };
    }

    private buildFinanceCharts() {
        const f = this.finance.data;
        const years = f?.byYear || [];
        if (!years.length) {
            this.disbursementChartData = null;
            return;
        }
        const t = this.theme();
        this.disbursementChartData = {
            labels: years.map(p => this.pointLabel(p)),
            datasets: [{
                label: this.translationService.translate('DASHBOARD.DISBURSEMENT_BY_YEAR'),
                data: years.map(p => p.value),
                backgroundColor: t.green,
                borderRadius: 4
            }]
        };
        this.disbursementChartOptions = this.axesOptions();
    }

    private buildCountriesChart() {
        const countries = this.countries.data || [];
        if (!countries.length) {
            this.topCountriesChartData = null;
            return;
        }
        const t = this.theme();
        this.topCountriesChartData = {
            labels: countries.map(p => this.pointLabel(p)),
            datasets: [{
                label: this.translationService.translate('DASHBOARD.TOP_COUNTRIES_DATASET'),
                data: countries.map(p => p.value),
                backgroundColor: t.primary
            }]
        };
        // Horizontal bars, so the axes swap roles: the countries are the category
        // axis and the counts are the value axis. Same four corrections as
        // axesOptions(), applied the other way round.
        const rtl = this.translationService.isRTL();
        this.topCountriesChartOptions = {
            indexAxis: 'y',
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: { enabled: true, rtl, textDirection: rtl ? 'rtl' : 'ltr' }
            },
            scales: {
                x: {
                    beginAtZero: true,
                    reverse: rtl,
                    ticks: { color: t.textColorSecondary, precision: 0 },
                    grid: { color: t.surfaceBorder, drawBorder: false }
                },
                y: {
                    position: rtl ? 'right' : 'left',
                    ticks: { color: t.textColorSecondary },
                    grid: { display: false }
                }
            }
        };
    }

    private buildUsersChart() {
        const monthly = this.users.data?.monthly || [];
        if (!monthly.length) {
            this.monthlyUsersChartData = null;
            return;
        }
        const t = this.theme();
        this.monthlyUsersChartData = {
            labels: monthly.map(p => this.monthLabel(p)),
            datasets: [{
                label: this.translationService.translate('DASHBOARD.CHART_NEW_USERS'),
                data: monthly.map(p => p.count),
                fill: false,
                backgroundColor: t.green,
                borderColor: t.green,
                tension: .4
            }]
        };
        this.monthlyUsersChartOptions = this.axesOptions();
    }

    // ── Expanded chart ──────────────────────────────────────────────────────

    /**
     * The chart the reader has opened, or null. The dashboard draws its charts
     * in cards a few hundred pixels wide, where a dense axis (twelve months, ten
     * countries) is unreadable; clicking one opens the same data at full size.
     *
     * <p>Data and options are the ones the card already holds — the same objects,
     * not a copy — so the enlarged chart cannot drift from the small one. Only
     * the height differs, and that comes from the dialog's own stylesheet.
     */
    expandedChart: { titleKey: string; type: string; data: any; options: any } | null = null;

    expandChart(titleKey: string, type: string, data: any, options: any) {
        if (!data) {
            return;
        }
        this.expandedChart = { titleKey, type, data, options };
    }

    /** p-dialog's own close paths (the X, Escape, the mask) route through here. */
    onExpandedVisibleChange(visible: boolean) {
        if (!visible) {
            this.expandedChart = null;
        }
    }
}
