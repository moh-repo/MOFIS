import { Component, OnInit, OnDestroy, ViewChild, ElementRef, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { UnsavedInputService } from '../demo/service/unsaved-input.service';
import { LayoutService } from "./service/app.layout.service";
import { MenuService } from './app.menu.service';
import { Router } from '@angular/router';
import { TranslationService } from '../demo/service/translation.service';
import { AuthService } from '../demo/service/auth.service';
import { ApprovalService } from '../demo/service/approval.service';
import { AlertService } from '../demo/service/alert.service';
import { UmsPermissionService } from '../demo/service/ums-permission.service';
import { UmsAuthService } from '../demo/service/ums-auth.service';
import { CountrySelectionService } from '../demo/service/country-selection.service';
import { LookupService } from '../demo/service/lookup.service';
import { SystemSettingsService, SysSettingsDto } from '../demo/service/system-settings.service';
import { SYSTEM_LOGO_FALLBACK } from '../demo/service/system-logo';
import { Subscription, interval, fromEvent, merge, filter, skip } from 'rxjs';
import { StudyCountryIn } from '../demo/api/study-country-in';
import { NzI18nService } from 'ng-zorro-antd/i18n';
import { Popover } from 'primeng/popover';
import { AGENDA_FALLBACK_ICON, AGENDA_ICONS, CalendarAgendaService, CalendarDay, CalendarEntry, extraByKind } from '../demo/service/calendar-agenda.service';

/**
 * Where each kind of agenda entry leads, and with what.
 *
 * <p>A map rather than a switch because it is a table: one row per kind, read
 * once. The two screens that take a date read it from the query string — see
 * `applyRouteFilters` on the notification history and the alert-events cases —
 * so following a row lands on the same day the agenda was describing rather
 * than on an unfiltered list the reader then has to narrow by hand.
 */
const AGENDA_DESTINATIONS: Readonly<Record<string,
    (entry: CalendarEntry, date: string) => { commands: any[]; queryParams?: Record<string, string> }>> = {
    DOCUMENT_EXPIRY: entry => ({
        commands: entry.referenceId != null ? ['/students/profile', entry.referenceId] : ['/students']
    }),
    ALERT_CASE: (_entry, date) => ({
        commands: ['/alert-events/cases'], queryParams: { dateFrom: date, dateTo: date }
    }),
    NOTIFICATION: (_entry, date) => ({
        commands: ['/notifications/history'], queryParams: { sentFrom: date, sentTo: date }
    }),
    QUARTER_PAY: () => ({ commands: ['/operations/mohe-quarter'] }),
    QUARTER_RECEIVED: () => ({ commands: ['/operations/mohe-quarter'] }),
    REPORT_RUN: () => ({ commands: ['/reports/custom/scheduled-reports'] }),
    APPROVAL_PENDING: () => ({ commands: ['/approvals/pending'] })
};

@Component({
    selector: 'app-topbar',
    templateUrl: './app.topbar.component.html',
    standalone: false,
    // Its own instance, answered only by the topbar's keyed dialog: pages host their
    // own <p-confirmdialog> and must not pick up this question.
    providers: [ConfirmationService]
})
export class AppTopBarComponent implements OnInit, OnDestroy {

    items!: MenuItem[];
    pendingApprovalCount: number = 0;
    unreadAlertCount: number = 0;
    canViewPendingApprovals: boolean = false;
    private alertRefreshSubscription?: Subscription;
    hasVisibleMenuItems = true;
    private refreshSubscription?: Subscription;
    private permissionsSubscription?: Subscription;
    private menuVisibleSubscription?: Subscription;

    @ViewChild('menubutton') menuButton!: any;

    @ViewChild('topbarmenubutton') topbarMenuButton!: any;

    @ViewChild('topbarmenu') menu!: any;

    @ViewChild('calendarOverlay') calendarOverlay!: Popover;

    @ViewChild('calendarButton') calendarButton!: ElementRef<HTMLElement>;

    private calendarPanelResize?: ResizeObserver;

    /* ── the day agenda ──────────────────────────────────────────────────────
       The calendar was a date picker bound to a field nothing read. It now
       answers the question the picture of a calendar makes a user ask: what is
       happening on this day? The server decides which kinds of entry the caller
       may see, so nothing here filters by permission a second time. */
    agendaEntries: CalendarEntry[] = [];
    agendaMore: { kind: string; extra: number }[] = [];
    agendaLoading = false;
    agendaFailed = false;

    /**
     * Kinds the day loaded without (QA C10 / N14). The agenda is shown, with a
     * line naming what is missing and a way to ask again — an incomplete day
     * that says nothing is indistinguishable from a quiet one.
     */
    agendaPartial: string[] = [];

    /** True when the month's marks could not be read; some days may be unmarked. */
    markedFailed = false;
    /** Days of the shown month that have at least one entry, as yyyy-MM-dd. */
    private markedDays = new Set<string>();
    private agendaRequest?: Subscription;
    private markedRequest?: Subscription;

    profileItems: MenuItem[] = [];
    calendarDate: Date = new Date();

    countryOptions: StudyCountryIn[] = [];
    selectedCountryId: number | null = null;

    sysSettings: SysSettingsDto | null = null;
    /**
     * One emblem for both colour schemes. The two file names this replaced were
     * byte-identical copies of an auto-trace that began with an opaque black
     * rectangle over the whole canvas — the black box behind the emblem in the
     * August review's dashboard screenshot. See {@link SYSTEM_LOGO_FALLBACK}.
     */
    readonly fallbackLogo = SYSTEM_LOGO_FALLBACK;

    // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
    private readonly destroyRef = inject(DestroyRef);

    private readonly confirmation = inject(ConfirmationService);
    private readonly unsavedInput = inject(UnsavedInputService);

    constructor(
        public layoutService: LayoutService,
        private menuService: MenuService,
        private router: Router,
        public translationService: TranslationService,
        private authService: AuthService,
        private approvalService: ApprovalService,
        private permissionService: UmsPermissionService,
        private umsAuthService: UmsAuthService,
        private countrySelectionService: CountrySelectionService,
        private lookupService: LookupService,
        private systemSettingsService: SystemSettingsService,
        private alertService: AlertService,
        private calendarAgenda: CalendarAgendaService,
        private nzI18n: NzI18nService
    ) { }

    get systemLogoUrl(): string {
        if (this.sysSettings?.sysLogoPath) {
            return this.systemSettingsService.getLogoUrl();
        }
        return this.fallbackLogo;
    }

    get systemDisplayName(): string | null {
        if (!this.sysSettings) return null;
        return this.translationService.isRTL()
            ? (this.sysSettings.sysNameAr || this.sysSettings.sysNameEn)
            : (this.sysSettings.sysNameEn || this.sysSettings.sysNameAr);
    }

    onLogoError(event: Event): void {
        const img = event.target as HTMLImageElement;
        img.src = this.fallbackLogo;
    }

    ngOnInit() {
        this.updateProfileItems();
        this.checkPermissions();
        this.loadCountryOptions();
        this.systemSettingsService.getSettings().subscribe({
            next: dto => { this.sysSettings = dto; },
            error: () => { /* silently ignore — fallback logo/name shown */ }
        });
        this.selectedCountryId = this.countrySelectionService.selectedCountryId;
        this.countrySelectionService.selectedCountryId$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(id => {
            this.selectedCountryId = id;
        });

        // Subscribe to permission changes
        this.permissionsSubscription = this.umsAuthService.permissions$.subscribe(() => {
            this.checkPermissions();
        });
        this.umsAuthService.countryIds$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadCountryOptions());

        // Load unread alert count and refresh every 60 seconds
        this.loadUnreadAlertCount();
        // Poll only while the tab is visible; refresh immediately on return to it.
        this.alertRefreshSubscription = merge(interval(60000), fromEvent(document, 'visibilitychange'))
            .pipe(filter(() => document.visibilityState === 'visible'))
            .subscribe(() => this.loadUnreadAlertCount());

        // Subscribe to language changes to update menu items
        this.translationService.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.updateProfileItems();
        });
        // Country names are localized when fetched, so a language switch has to fetch them
        // again or the selector keeps the old language (review Q5). skip(1): the stream
        // replays the current language, and the list was loaded just above.
        this.translationService.currentLanguage$.pipe(skip(1), takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.loadCountryOptions());

        // Hide menu toggle when sidebar has no items
        this.menuVisibleSubscription = this.menuService.hasVisibleItems$.subscribe(hasItems => {
            this.hasVisibleMenuItems = hasItems;
        });
    }

    loadUnreadAlertCount(): void {
        this.alertService.getUnreadCount().subscribe({
            next: (res) => { this.unreadAlertCount = res?.count ?? 0; },
            error: () => { this.unreadAlertCount = 0; }
        });
    }

    ngOnDestroy() {
        if (this.refreshSubscription) {
            this.refreshSubscription.unsubscribe();
        }
        if (this.alertRefreshSubscription) {
            this.alertRefreshSubscription.unsubscribe();
        }
        if (this.permissionsSubscription) {
            this.permissionsSubscription.unsubscribe();
        }
        if (this.menuVisibleSubscription) {
            this.menuVisibleSubscription.unsubscribe();
        }
        this.stopWatchingCalendarPanel();
        this.agendaRequest?.unsubscribe();
        this.markedRequest?.unsubscribe();
    }

    checkPermissions() {
        this.canViewPendingApprovals = this.permissionService.canView('APPROVALS');
        
        if (this.canViewPendingApprovals) {
            this.loadPendingApprovalCount();
            
            // Refresh pending count every 60 seconds
            if (this.refreshSubscription) {
                this.refreshSubscription.unsubscribe();
            }
            // Poll only while the tab is visible; refresh immediately on return to it.
            this.refreshSubscription = merge(interval(60000), fromEvent(document, 'visibilitychange'))
                .pipe(filter(() => document.visibilityState === 'visible'))
                .subscribe(() => {
                    this.loadPendingApprovalCount();
                });
        } else {
            this.pendingApprovalCount = 0;
            if (this.refreshSubscription) {
                this.refreshSubscription.unsubscribe();
            }
        }
    }

    loadPendingApprovalCount() {
        if (!this.canViewPendingApprovals) {
            return;
        }
        this.approvalService.countPending().subscribe({
            next: (result) => {
                const count = result?.count;
                this.pendingApprovalCount = typeof count === 'number' ? count : (typeof count === 'string' ? parseInt(count, 10) : 0) || 0;
            },
            error: () => {
                this.pendingApprovalCount = 0;
            }
        });
    }

    goToPendingApprovals() {
        this.router.navigate(['/approvals/pending']);
    }

    /**
     * The account menu: who you are, and the way out.
     *
     * <p>Settings used to sit between the two. It went to a bare `/settings`,
     * which redirects to Lookup Tables — one of the seven settings screens, and
     * not an obvious destination for a menu item called Settings. All seven are
     * their own entries under Administration → Settings in the sidebar, which is
     * where they are reached from.
     */
    updateProfileItems() {
        this.profileItems = [
            {
                label: this.translationService.translate('LAYOUT.PROFILE'),
                icon: 'pi pi-user',
                command: () => this.router.navigate(['/profile'])
            },
            { separator: true },
            {
                label: this.translationService.translate('LAYOUT.LOGOUT'),
                icon: 'pi pi-sign-out',
                command: () => this.logout()
            }
        ];
    }

    loadCountryOptions(): void {
        const allowedIds = this.umsAuthService.countryIds || [];
        const isMinistry = this.umsAuthService.isMinistryUser;

        if (!isMinistry && allowedIds.length === 0) {
            this.countryOptions = [];
            return;
        }

        this.lookupService.getAllStudyCountries().subscribe({
            next: (list) => {
                const allCountries = list || [];
                if (isMinistry) {
                    this.countryOptions = allCountries;
                } else {
                    this.countryOptions = allCountries.filter(c => allowedIds.includes(c.id));
                }
                const current = this.countrySelectionService.selectedCountryId;
                const permittedIds = isMinistry
                    ? allCountries.map(c => c.id)
                    : allowedIds;
                if (current != null && !permittedIds.includes(current)) {
                    this.countrySelectionService.setSelectedCountryId(null);
                }
            },
            error: () => { this.countryOptions = []; }
        });
    }

    get showCountrySelector(): boolean {
        return this.umsAuthService.isMinistryUser || this.countryOptions.length > 1;
    }

    onCountryChange(): void {
        // The select has already taken the new value; a "stay" puts the old one back.
        const previous = this.countrySelectionService.selectedCountryId;
        this.whenInputMayBeLost(() => {
            this.countrySelectionService.setSelectedCountryId(this.selectedCountryId);
            const url = this.router.url;
            this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
                this.router.navigateByUrl(url);
            });
        }, () => { this.selectedCountryId = previous; });
    }

    /**
     * Runs `proceed` straight away, or — when the page holds typed input a switch
     * would wipe — after the user confirms (live QA #4 / N15, decision DEC-07).
     *
     * <p>"Switch and clear" clears the input by design (QA note 15, decision D15-a,
     * 26 Sep 2026): the page is rebuilt in the other language or scope, and the
     * message says so before the user chooses. "Stay" keeps it.
     */
    private whenInputMayBeLost(proceed: () => void, stay?: () => void): void {
        if (!this.unsavedInput.hasUnsavedInput()) {
            proceed();
            return;
        }
        const t = (k: string) => this.translationService.translate(k);
        this.confirmation.confirm({
            key: 'unsaved-input',
            header: t('LAYOUT.UNSAVED_INPUT_TITLE'),
            message: t('LAYOUT.UNSAVED_INPUT_MESSAGE'),
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: t('LAYOUT.UNSAVED_INPUT_CONTINUE'),
            rejectLabel: t('LAYOUT.UNSAVED_INPUT_STAY'),
            acceptButtonStyleClass: 'p-button-warning',
            rejectButtonStyleClass: 'p-button-text',
            accept: proceed,
            reject: () => stay?.()
        });
    }

    /**
     * Places the calendar panel under its button, and keeps it there.
     *
     * <p>PrimeNG positions a popover in `onAnimationStart`, which runs before the
     * `<ng-template pTemplate="content">` inside it has laid out, so
     * `absolutePosition` measures a container 19px wide — padding and the arrow.
     * Its overflow guard (`target.left + width > viewport.width`) therefore finds
     * nothing to correct; the datepicker then renders, the popover grows to about
     * 262px, and nothing moves it again. In English the topbar actions sit at the
     * right end of the bar, so 126px of the calendar hung off the edge of the
     * window.
     *
     * <p>Re-running PrimeNG's own `align()` would not fix it — one frame later
     * the panel is still 19px — and would not fix Arabic either: `align()` writes
     * the trigger's viewport x into `inset-inline-end`, and the block the panel is
     * positioned from does not start at the window's left edge when the scrollbar
     * sits on the left, which put the panel 15px off its button.
     *
     * <p>So rather than guess a frame count, the panel's size is followed: it is
     * placed whenever it changes, which covers the first render and the wider
     * month and year views the header opens. The rule is the one a dropdown
     * follows — line its leading edge up with the button's leading edge, fall back
     * to the trailing pair when that would run off the screen, clamp only if
     * neither fits — written as a nudge from where the panel actually is rather
     * than as an absolute coordinate, so it does not depend on where that
     * containing block begins. Measured both ways in
     * topbar-calendar-overlay.spec.ts.
     */
    onCalendarShow(): void {
        const panel = this.calendarOverlay?.container;
        if (!panel) {
            return;
        }
        this.stopWatchingCalendarPanel();
        if (typeof ResizeObserver !== 'undefined') {
            this.calendarPanelResize = new ResizeObserver(() => this.placeCalendarPanel());
            this.calendarPanelResize.observe(panel);
        }
        this.placeCalendarPanel();
        this.loadAgenda();
        this.loadMonthMarks(this.calendarDate.getFullYear(), this.calendarDate.getMonth() + 1);
    }

    onCalendarHide(): void {
        this.stopWatchingCalendarPanel();
    }

    /** The chosen day, written the way the current language writes dates. */
    get agendaDateLabel(): string {
        const locale = this.translationService.isRTL() ? 'ar-EG' : 'en-GB';
        return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'long', year: 'numeric' })
            .format(this.calendarDate);
    }

    /** True for a day of the shown month that has at least one entry on it. */
    isMarked(day: { day: number; month: number; year: number; otherMonth?: boolean }): boolean {
        if (day.otherMonth) {
            return false;
        }
        return this.markedDays.has(CalendarAgendaService.isoDate(new Date(day.year, day.month, day.day)));
    }

    /**
     * The month's marks, when the header moves to another month or year.
     *
     * <p>PrimeNG numbers this event's month from 1 and the date cells' month from
     * 0; the request wants the 1-based one, so it is passed through untouched.
     */
    onCalendarMonthChange(event: { month?: number; year?: number }): void {
        if (event.month == null || event.year == null) {
            return;
        }
        this.loadMonthMarks(event.year, event.month);
    }

    /** What falls on the chosen day. */
    loadAgenda(): void {
        this.agendaRequest?.unsubscribe();
        this.agendaLoading = true;
        this.agendaFailed = false;
        this.agendaRequest = this.calendarAgenda.day(this.calendarDate).subscribe({
            next: day => {
                this.agendaLoading = false;
                this.applyAgenda(day);
            },
            error: () => {
                this.agendaLoading = false;
                this.agendaFailed = true;
                this.agendaEntries = [];
                this.agendaMore = [];
                this.agendaPartial = [];
            }
        });
    }

    private applyAgenda(day: CalendarDay): void {
        this.agendaEntries = day.entries ?? [];
        this.agendaMore = extraByKind(day);
        this.agendaPartial = day.failed ?? [];
    }

    /** The names of the kinds that failed, in the reader's language. */
    get agendaPartialLabel(): string {
        const separator = this.translationService.isRTL() ? '، ' : ', ';
        return this.agendaPartial
            .map(kind => this.translationService.translate(`CALENDAR.KIND.${kind}`))
            .join(separator);
    }

    /** Re-reads both the day and the month's marks. */
    retryAgenda(): void {
        this.loadAgenda();
        this.loadMonthMarks(this.calendarDate.getFullYear(), this.calendarDate.getMonth() + 1);
    }

    private loadMonthMarks(year: number, month: number): void {
        this.markedRequest?.unsubscribe();
        this.markedRequest = this.calendarAgenda.month(year, month).subscribe({
            next: result => {
                this.markedDays = new Set((result.days ?? []).map(d => d.date));
                // Marks drawn from an incomplete count would quietly under-report
                // which days are worth opening, so the panel says so (QA C10).
                this.markedFailed = (result.failed ?? []).length > 0;
            },
            // A month without its marks is a plain calendar, which is what it was
            // before; the agenda below it still says what is on the chosen day.
            error: () => {
                this.markedDays = new Set<string>();
                this.markedFailed = true;
            }
        });
    }

    /**
     * The first line of an entry. The server names what it sends in the reader's
     * language — the kind of record of a pending approval, a quarter, a scenario
     * (W3-07) — so only an entry without a title falls back to its kind's name.
     */
    agendaTitle(entry: CalendarEntry): string {
        return entry.title || this.translationService.translate(`CALENDAR.KIND.${entry.kind}`);
    }

    /** The icon for a kind of entry — see AGENDA_ICONS. */
    agendaIcon(kind: string): string {
        return AGENDA_ICONS[kind] ?? AGENDA_FALLBACK_ICON;
    }

    /**
     * Opens the screen an entry belongs to, on the day it belongs to.
     *
     * <p>The routes live here rather than on the server: the API answers what
     * happened, and which URL shows it is the browser's business. Where a screen
     * reads a date from its query string the day is passed on, so the list that
     * opens is the list the agenda was describing — see AGENDA_DESTINATIONS.
     */
    openAgendaEntry(entry: CalendarEntry): void {
        const destination = AGENDA_DESTINATIONS[entry.kind];
        if (!destination) {
            return;
        }
        this.calendarOverlay?.hide();
        const { commands, queryParams } = destination(entry, CalendarAgendaService.isoDate(this.calendarDate));
        this.router.navigate(commands, queryParams ? { queryParams } : {});
    }

    private stopWatchingCalendarPanel(): void {
        this.calendarPanelResize?.disconnect();
        this.calendarPanelResize = undefined;
    }

    private placeCalendarPanel(): void {
        const panel = this.calendarOverlay?.container;
        const button = this.calendarButton?.nativeElement;
        if (!panel || !button) {
            return;
        }

        const trigger = button.getBoundingClientRect();
        // The entrance animation scales the panel, so its bounding box is not its
        // layout box — measuring through it left the panel 9px off the button once
        // the scale finished. An inline `!important` outranks an animation in the
        // cascade, so the transform can be suspended for the measurement and put
        // back before anything is painted.
        const transform = panel.style.getPropertyValue('transform');
        const priority = panel.style.getPropertyPriority('transform');
        panel.style.setProperty('transform', 'none', 'important');
        const box = panel.getBoundingClientRect();
        const current = parseFloat(getComputedStyle(panel).left) || 0;
        panel.style.setProperty('transform', transform, priority);
        // The root element's box, not innerWidth: it excludes the scrollbar and,
        // in Arabic, starts at the scrollbar's width rather than at zero.
        const view = document.documentElement.getBoundingClientRect();
        const rtl = this.translationService.isRTL();

        const leading = rtl ? trigger.right - box.width : trigger.left;
        const trailing = rtl ? trigger.left : trigger.right - box.width;
        const fits = (left: number) => left >= view.left && left + box.width <= view.right;

        let wanted = leading;
        if (!fits(wanted)) {
            wanted = fits(trailing)
                ? trailing
                : Math.max(view.left, Math.min(leading, view.right - box.width));
        }

        panel.style.insetInlineStart = '';
        panel.style.insetInlineEnd = '';
        panel.style.left = `${current + (wanted - box.left)}px`;
    }

    switchLanguage(language: string): void {
        if (language === this.translationService.getCurrentLanguage()) {
            return;
        }
        // Switches in place — see TranslationService.switchLanguage. The page is
        // rebuilt, so typed input is lost: ask first when there is any.
        this.whenInputMayBeLost(() => this.translationService.switchLanguage(language));
    }

    logout() {
        // Use the AuthService logout method which properly handles authentication state
        this.authService.logout();
    }

}
