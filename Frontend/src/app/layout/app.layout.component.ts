import { ChangeDetectorRef, Component, OnDestroy, OnInit, Renderer2, ViewChild } from '@angular/core';
import { Event, NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { LayoutService } from "./service/app.layout.service";
import { MenuService } from './app.menu.service';
import { AppSidebarComponent } from "./app.sidebar.component";
import { AppTopBarComponent } from './app.topbar.component';
import { TranslationService } from '../demo/service/translation.service';
import { IdleTimeoutService } from '../demo/service/idle-timeout.service';
import { AlertStreamService } from '../demo/service/alert-stream.service';

@Component({
    selector: 'app-layout',
    templateUrl: './app.layout.component.html',
    standalone: false
})
export class AppLayoutComponent implements OnInit, OnDestroy {

    overlayMenuOpenSubscription: Subscription;
    private languageSubscription?: Subscription;
    private menuVisibleSubscription?: Subscription;
    hasVisibleMenuItems = true;

    /**
     * False for one turn after a language switch, which takes the routed page
     * out of the DOM and builds it again. See {@link reinitialiseRoute}.
     */
    routeAlive = true;

    /**
     * True while a navigation is resolving (chunk download, guards), shown as a
     * thin bar under the topbar (review round 2, H3). Raised only after
     * {@link NAVIGATION_BAR_DELAY_MS} so a quick navigation does not flicker.
     */
    navigating = false;
    private navigationTimer?: ReturnType<typeof setTimeout>;
    private navigationSubscription?: Subscription;
    private static readonly NAVIGATION_BAR_DELAY_MS = 150;

    menuOutsideClickListener: any;

    profileMenuOutsideClickListener: any;

    @ViewChild(AppSidebarComponent) appSidebar!: AppSidebarComponent;

    @ViewChild(AppTopBarComponent) appTopbar!: AppTopBarComponent;

    constructor(
        public layoutService: LayoutService,
        private menuService: MenuService,
        public renderer: Renderer2,
        public router: Router,
        public translationService: TranslationService,
        private idleTimeoutService: IdleTimeoutService,
        private alertStreamService: AlertStreamService,
        private cd: ChangeDetectorRef
    ) {
        this.overlayMenuOpenSubscription = this.layoutService.overlayOpen$.subscribe(() => {
            if (!this.menuOutsideClickListener) {
                this.menuOutsideClickListener = this.renderer.listen('document', 'click', event => {
                    const sidebarEl = this.appSidebar?.el?.nativeElement;
                    const menuBtnEl = this.appTopbar?.menuButton?.nativeElement;
                    const isOutsideClicked = !(
                        (sidebarEl && (sidebarEl.isSameNode(event.target) || sidebarEl.contains(event.target)))
                        || (menuBtnEl && (menuBtnEl.isSameNode(event.target) || menuBtnEl.contains(event.target)))
                    );
                    
                    if (isOutsideClicked) {
                        this.hideMenu();
                    }
                });
            }

            if (!this.profileMenuOutsideClickListener) {
                this.profileMenuOutsideClickListener = this.renderer.listen('document', 'click', event => {
                    const isOutsideClicked = !(this.appTopbar.menu.nativeElement.isSameNode(event.target) || this.appTopbar.menu.nativeElement.contains(event.target)
                        || this.appTopbar.topbarMenuButton.nativeElement.isSameNode(event.target) || this.appTopbar.topbarMenuButton.nativeElement.contains(event.target));

                    if (isOutsideClicked) {
                        this.hideProfileMenu();
                    }
                });
            }

            if (this.layoutService.state.staticMenuMobileActive) {
                this.blockBodyScroll();
            }
        });

        this.router.events.pipe(filter(event => event instanceof NavigationEnd))
            .subscribe(() => {
                this.hideMenu();
                this.hideProfileMenu();
                // Live test #28: the settings drawer stayed open across a
                // navigation, so it hung over whatever page was arrived at.
                // Closed here rather than in hideMenu(), which also runs on an
                // outside click of the menu — a different gesture.
                this.layoutService.state.configSidebarVisible = false;
            });

        this.navigationSubscription = this.router.events.subscribe(event => this.trackNavigation(event));

        // Subscribe to language changes for RTL support
        let firstEmission = true;
        this.languageSubscription = this.translationService.currentLanguage$.subscribe(() => {
            this.updateLayoutDirection();
            // currentLanguage$ is a BehaviorSubject: the first emission is the
            // language the page loaded in, not a switch.
            if (firstEmission) {
                firstEmission = false;
                return;
            }
            this.reinitialiseRoute();
        });
    }

    ngOnInit(): void {
        this.idleTimeoutService.start();
        this.alertStreamService.connect();

        /*
         * No permission reload here (live QA R5-08). Sign-in and /ums/auth/me already
         * set the codes, the map and the countries; an empty list means the account holds
         * no code, and re-reading it through the staff-administration endpoints answered
         * such an account (p_none) with three "Access denied" toasts on its profile.
         */

        // Hide sidebar when user has no menu items (no permissions for any page)
        this.menuVisibleSubscription = this.menuService.hasVisibleItems$.subscribe(hasItems => {
            this.hasVisibleMenuItems = hasItems;
        });
    }

    /**
     * Builds the routed page again, so a language switch shows in it.
     *
     * <p>Switching the language used to reload the whole application, and that
     * is why every screen looked right afterwards. The reload went in the third
     * pass, on the grounds that no consumer needed it; these are the consumers
     * that did. Text in templates does follow the switch on its own — the
     * `translate` pipe is impure and drops its cache — but three things cannot:
     *
     * <ul>
     *   <li>text the server chose. Every request carries `Accept-Language` and
     *       the backend picks the name to send (`ChartDataService` reads
     *       `LocaleContextHolder` to choose between `nameEn` and `nameAr`), so
     *       a chart's category labels are whatever language they were fetched
     *       in. Of 173 components, 122 never re-fetch on a switch.</li>
     *   <li>strings a component built once into a field in `ngOnInit` — the
     *       labels of a dropdown, the headers of a table.</li>
     *   <li>third-party widgets that read the direction when they are created:
     *       ag-grid on the financial data forms is the one left.</li>
     * </ul>
     *
     * <p>Making all 122 reactive by hand would be a large change that no new
     * page would stay inside. Taking the outlet out of the DOM for one turn
     * runs every one of them through `ngOnInit` again, which re-fetches and
     * rebuilds, and it costs a page rather than an application: the shell, the
     * session, the permissions, the menu and the country selection all stay up,
     * and nothing is downloaded again. What it does cost is anything unsaved on
     * the page — which a manual refresh, the thing this replaces, also cost.
     */
    private reinitialiseRoute(): void {
        this.routeAlive = false;
        this.cd.detectChanges();
        // A macrotask, not a microtask: the outlet has to be off the DOM for a
        // turn or Angular reuses the component it never destroyed.
        setTimeout(() => {
            this.routeAlive = true;
            this.cd.detectChanges();
        });
    }

    private trackNavigation(event: Event): void {
        if (event instanceof NavigationStart) {
            clearTimeout(this.navigationTimer);
            this.navigationTimer = setTimeout(() => {
                this.navigating = true;
                this.cd.markForCheck();
            }, AppLayoutComponent.NAVIGATION_BAR_DELAY_MS);
        } else if (event instanceof NavigationEnd || event instanceof NavigationCancel
            || event instanceof NavigationError) {
            clearTimeout(this.navigationTimer);
            this.navigating = false;
            this.cd.markForCheck();
        }
    }

    hideMenu() {
        this.layoutService.state.overlayMenuActive = false;
        this.layoutService.state.staticMenuMobileActive = false;
        this.layoutService.state.menuHoverActive = false;
        if (this.menuOutsideClickListener) {
            this.menuOutsideClickListener();
            this.menuOutsideClickListener = null;
        }
        this.unblockBodyScroll();
    }

    hideProfileMenu() {
        this.layoutService.state.profileSidebarVisible = false;
        if (this.profileMenuOutsideClickListener) {
            this.profileMenuOutsideClickListener();
            this.profileMenuOutsideClickListener = null;
        }
    }

    blockBodyScroll(): void {
        if (document.body.classList) {
            document.body.classList.add('blocked-scroll');
        }
        else {
            document.body.className += ' blocked-scroll';
        }
    }

    unblockBodyScroll(): void {
        if (document.body.classList) {
            document.body.classList.remove('blocked-scroll');
        }
        else {
            document.body.className = document.body.className.replace(new RegExp('(^|\\b)' +
                'blocked-scroll'.split(' ').join('|') + '(\\b|$)', 'gi'), ' ');
        }
    }

    get containerClass() {
        return {
            /*
             * Owned here rather than by a separate [class.rtl] binding: that binding and
             * this ngClass object both write the element's class list, and ngClass — which
             * re-diffs every cycle because this getter returns a fresh object — could drop
             * `rtl` without [class.rtl] re-adding it, since its own bound value had not
             * changed. That left the direction half-applied until a reload.
             */
            'rtl': this.translationService.isRTL(),
            'layout-theme-light': this.layoutService.config().colorScheme === 'light',
            'layout-theme-dark': this.layoutService.config().colorScheme === 'dark',
            'layout-overlay': this.layoutService.config().menuMode === 'overlay',
            'layout-static': this.layoutService.config().menuMode === 'static',
            // A hidden sidebar leaves a gutter the main content should reclaim,
            // whether it is hidden because the user collapsed it or because there
            // is nothing in it to show.
            'layout-static-inactive': (this.layoutService.state.staticMenuDesktopInactive || !this.hasVisibleMenuItems) && this.layoutService.config().menuMode === 'static',
            'layout-overlay-active': this.layoutService.state.overlayMenuActive,
            'layout-mobile-active': this.layoutService.state.staticMenuMobileActive,
            'p-input-filled': this.layoutService.config().inputStyle === 'filled',
            'p-ripple-disabled': !this.layoutService.config().ripple
        }
    }

    ngOnDestroy() {
        this.idleTimeoutService.stop();
        this.alertStreamService.disconnect();
        clearTimeout(this.navigationTimer);
        this.navigationSubscription?.unsubscribe();

        if (this.overlayMenuOpenSubscription) {
            this.overlayMenuOpenSubscription.unsubscribe();
        }

        if (this.languageSubscription) {
            this.languageSubscription.unsubscribe();
        }

        if (this.menuVisibleSubscription) {
            this.menuVisibleSubscription.unsubscribe();
        }

        if (this.menuOutsideClickListener) {
            this.menuOutsideClickListener();
        }
    }

    /**
     * The wrapper's direction classes are rendered from {@link containerClass}; this only
     * makes sure a language change that originates outside a click (restored preference,
     * another component) still reaches change detection. Writing the class here directly
     * would race the template binding and leave the layout in a half-flipped state.
     */
    updateLayoutDirection() {
        this.cd.markForCheck();
    }
}
