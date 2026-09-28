import { ChangeDetectorRef, Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { UnsavedInputService } from '../../demo/service/unsaved-input.service';
import { NavigationEnd, Router } from '@angular/router';
import { Subject, filter, startWith, takeUntil } from 'rxjs';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService, Language } from '../../demo/service/translation.service';
import { NzI18nService } from 'ng-zorro-antd/i18n';

const MOBILE_BREAKPOINT = 992;
const COLLAPSED_KEY = 'studentPortalSiderCollapsed';

/** One destination in the side menu. */
export interface PortalNavItem {
  route: string;
  icon: string;
  labelKey: string;
}

/** A titled run of destinations. The titles are what make eleven links readable. */
export interface PortalNavGroup {
  labelKey: string;
  items: PortalNavItem[];
}

@Component({
    selector: 'app-portal-layout',
    templateUrl: './portal-layout.component.html',
    styleUrls: ['./portal-layout.component.scss'],
    standalone: false
})
export class PortalLayoutComponent implements OnInit, OnDestroy {

  /**
   * The navigation, written once.
   *
   * <p>It used to be written three times — the desktop bar, the mobile drawer and
   * the dashboard's card grid — and the copies had already drifted: Documents was
   * a `file` icon in one and `file-text` in another, and the drawer was missing
   * Security. One array feeds all of them, so they cannot disagree again.
   *
   * <p>Every icon here is registered in `AppModule`'s `NZ_ICONS` list. An
   * unregistered one is fetched from the Ant CDN at runtime, which fails on the
   * ministry network — so a new destination needs an icon already on that list.
   */
  readonly navGroups: PortalNavGroup[] = [
    {
      labelKey: 'STUDENT_PORTAL.NAV_GROUP.OVERVIEW',
      items: [
        { route: '/student-portal/dashboard', icon: 'dashboard', labelKey: 'STUDENT_PORTAL.DASHBOARD' }
      ]
    },
    {
      labelKey: 'STUDENT_PORTAL.NAV_GROUP.MY_FILE',
      items: [
        { route: '/student-portal/my-data', icon: 'idcard', labelKey: 'STUDENT_PORTAL.MY_DATA' },
        { route: '/student-portal/documents', icon: 'file-text', labelKey: 'STUDENT_PORTAL.DOCUMENTS' },
        { route: '/student-portal/year-results', icon: 'bar-chart', labelKey: 'STUDENT_PORTAL.YEAR_RESULTS' },
        { route: '/student-portal/finance-status', icon: 'dollar', labelKey: 'STUDENT_PORTAL.FINANCE_STATUS' }
      ]
    },
    {
      labelKey: 'STUDENT_PORTAL.NAV_GROUP.REQUESTS',
      items: [
        { route: '/student-portal/info-requests', icon: 'form', labelKey: 'STUDENT_PORTAL.INFO_REQUESTS' },
        { route: '/student-portal/inquiries', icon: 'message', labelKey: 'STUDENT_PORTAL.INQUIRIES' },
        { route: '/student-portal/my-requests', icon: 'edit', labelKey: 'STUDENT_PORTAL.MY_REQUESTS' },
        { route: '/student-portal/travel-tickets', icon: 'car', labelKey: 'STUDENT_PORTAL.TRAVEL_TICKETS' },
        { route: '/student-portal/extensions', icon: 'calendar', labelKey: 'STUDENT_PORTAL.EXTENSIONS' }
      ]
    },
    {
      labelKey: 'STUDENT_PORTAL.NAV_GROUP.ACCOUNT',
      items: [
        { route: '/student-portal/security', icon: 'safety', labelKey: 'STUDENT_PORTAL.SECURITY.MENU' }
      ]
    }
  ];

  languages: Language[] = [];
  currentLang = 'en';
  mobileMenuVisible = false;
  isMobile = false;
  isRTL = false;
  isCollapsed = false;

  /** Who is signed in, for the identity block. Absent is a normal state here. */
  studentName = '';
  studentId: number | string | null = null;

  /** The current page, for the header title and the trail above it. */
  pageTitleKey = 'STUDENT_PORTAL.DASHBOARD';
  sectionTitleKey = '';

  private destroy$ = new Subject<void>();

  /**
   * False for one turn after a language switch, which takes the routed page out
   * of the DOM and builds it again. See {@link reinitialiseRoute}.
   */
  routeAlive = true;

  constructor(
    public api: StudentPortalApiService,
    private router: Router,
    private translationService: TranslationService,
    private nzI18n: NzI18nService,
    private cd: ChangeDetectorRef,
    private modal: NzModalService,
    private unsavedInput: UnsavedInputService
  ) {
    this.languages = this.translationService.getAvailableLanguages();
    this.currentLang = this.translationService.getCurrentLanguage() || 'en';
    this.checkMobile();
    this.isCollapsed = this.readCollapsed();
  }

  @HostListener('window:resize')
  onResize(): void {
    this.checkMobile();
    if (!this.isMobile && this.mobileMenuVisible) {
      this.mobileMenuVisible = false;
    }
  }

  private checkMobile(): void {
    this.isMobile = typeof window !== 'undefined' && window.innerWidth < MOBILE_BREAKPOINT;
  }

  ngOnInit(): void {
    this.isRTL = this.translationService.isRTL();
    let firstEmission = true;
    this.translationService.currentLanguage$.pipe(takeUntil(this.destroy$)).subscribe(lang => {
      this.currentLang = lang || 'en';
      this.isRTL = this.translationService.isRTL();
      // currentLanguage$ is a BehaviorSubject: the first emission is the
      // language the page loaded in, not a switch.
      if (firstEmission) {
        firstEmission = false;
        return;
      }
      this.reinitialiseRoute();
    });

    // The header names the page, so it has to follow the router rather than be
    // set by each page for itself.
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      startWith(null),
      takeUntil(this.destroy$)
    ).subscribe(() => this.syncTitleToRoute());

    this.loadIdentity();
  }

  /**
   * The signed-in student, for the identity block at the foot of the menu.
   *
   * <p>Silent on failure on purpose: the shell is not the place to report a
   * profile that would not load. Every page inside it fetches what it needs and
   * says so itself, and a missing name here costs a set of initials, not a page.
   */
  private loadIdentity(): void {
    // The guard has just read the student back from the server to check the
    // token; using what it kept in memory saves asking again one navigation later.
    const known = this.api.currentStudent;
    if (known) {
      this.studentName = known.studentName || '';
      this.studentId = known.studentId ?? null;
      return;
    }
    this.api.getProfile().pipe(takeUntil(this.destroy$)).subscribe({
      next: profile => {
        this.studentName = profile?.studentName || '';
        this.studentId = profile?.studentId ?? null;
      },
      error: () => { /* see the note above */ }
    });
  }

  private syncTitleToRoute(): void {
    const url = this.router.url.split('?')[0];
    for (const group of this.navGroups) {
      // startsWith, not equals: a page with an id in the path is still that page.
      const item = group.items.find(i => url === i.route || url.startsWith(i.route + '/'));
      if (item) {
        this.pageTitleKey = item.labelKey;
        this.sectionTitleKey = group.labelKey;
        return;
      }
    }
    this.pageTitleKey = 'STUDENT_PORTAL.PORTAL_TITLE';
    this.sectionTitleKey = '';
  }

  /** Two words' worth of initials, the same rule the dashboard uses. */
  get studentInitials(): string {
    const name = (this.studentName || '').trim();
    if (!name) {
      return '—';
    }
    return name.split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase();
  }

  toggleCollapsed(): void {
    this.isCollapsed = !this.isCollapsed;
    this.writeCollapsed(this.isCollapsed);
  }

  /**
   * Storage is a convenience here and never a requirement: a private window or a
   * browser set to block site data throws on read, and the menu simply opens
   * expanded.
   */
  private readCollapsed(): boolean {
    try {
      return localStorage.getItem(COLLAPSED_KEY) === '1';
    } catch {
      return false;
    }
  }

  private writeCollapsed(collapsed: boolean): void {
    try {
      localStorage.setItem(COLLAPSED_KEY, collapsed ? '1' : '0');
    } catch {
      // Not worth telling anyone about; the menu still works this session.
    }
  }

  /**
   * Builds the routed page again, the way the staff shell does — see
   * AppLayoutComponent.reinitialiseRoute for why a language switch cannot be
   * answered by change detection alone. The portal needs it for the same
   * reasons and one of its own: it is ng-zorro throughout, and an ng-zorro
   * widget reads its direction when it is created.
   */
  private reinitialiseRoute(): void {
    this.routeAlive = false;
    this.cd.detectChanges();
    // A macrotask, not a microtask: the outlet has to be off the DOM for a turn
    // or Angular reuses the component it never destroyed.
    setTimeout(() => {
      this.routeAlive = true;
      this.cd.detectChanges();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleMobileMenu(): void {
    this.mobileMenuVisible = !this.mobileMenuVisible;
  }

  closeMobileMenu(): void {
    this.mobileMenuVisible = false;
  }

  navigateAndClose(route: string): void {
    this.router.navigate([route]);
    this.closeMobileMenu();
  }

  switchLanguage(code: string): void {
    if (code === this.translationService.getCurrentLanguage()) {
      return;
    }
    // The page is rebuilt on a switch, so typed input is lost: ask first when there
    // is any (live QA #4 / N15, decision DEC-07). Clearing on "switch" is by design
    // (QA note 15, D15-a); so is the switch being out of reach while a dialog is open,
    // since a modal dialog holds the page until it is closed.
    if (this.unsavedInput.hasUnsavedInput(UnsavedInputService.PORTAL_PAGE)) {
      const t = (k: string) => this.translationService.translate(k);
      this.modal.confirm({
        nzTitle: t('LAYOUT.UNSAVED_INPUT_TITLE'),
        nzContent: t('LAYOUT.UNSAVED_INPUT_MESSAGE'),
        nzOkText: t('LAYOUT.UNSAVED_INPUT_CONTINUE'),
        nzCancelText: t('LAYOUT.UNSAVED_INPUT_STAY'),
        nzOkDanger: true,
        nzOnOk: () => this.translationService.switchLanguage(code)
      });
      return;
    }
    // Switches in place — see TranslationService.switchLanguage.
    this.translationService.switchLanguage(code);
  }

  goToNotifications(): void {
    this.router.navigate(['/student-portal/info-requests']);
    this.closeMobileMenu();
  }

  logout(): void {
    this.api.logout().subscribe({
      next: () => {
        this.api.clearToken();
        this.router.navigate(['/student-portal/login']);
      },
      error: () => {
        this.api.clearToken();
        this.router.navigate(['/student-portal/login']);
      }
    });
  }
}
