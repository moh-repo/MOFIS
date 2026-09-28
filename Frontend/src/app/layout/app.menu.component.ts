import { OnInit, OnDestroy } from '@angular/core';
import { Component } from '@angular/core';
import { LayoutService } from './service/app.layout.service';
import { MenuService } from './app.menu.service';
import { TranslationService } from '../demo/service/translation.service';
import { UmsPermissionService } from '../demo/service/ums-permission.service';
import { UmsAuthService } from '../demo/service/ums-auth.service';
import { MenuVariantService } from './menu/menu-variant.service';
import { AppMenuItem } from './menu/menu-model';
import { buildClassicMenu } from './menu/menu-classic';
import { buildProposedMenu } from './menu/menu-proposed';
import { visibleMenu } from './menu/menu-access';
import { Subscription, merge } from 'rxjs';

@Component({
    selector: 'app-menu',
    templateUrl: './app.menu.component.html',
    standalone: false
})
export class AppMenuComponent implements OnInit, OnDestroy {

    /** What the sidebar renders — the built tree, less anything the search has filtered out. */
    model: AppMenuItem[] = [];

    /** Typed into the search field. Proposed layout only; classic never renders the field. */
    searchTerm = '';

    /** The permission-filtered tree before search narrows it. */
    private fullModel: AppMenuItem[] = [];

    private subscription?: Subscription;

    constructor(
        public layoutService: LayoutService,
        private menuService: MenuService,
        public translationService: TranslationService,
        private permissionService: UmsPermissionService,
        private authService: UmsAuthService,
        public variantService: MenuVariantService
    ) { }

    ngOnInit() {
        // Language, permissions, the signed-in user and the chosen layout all
        // change what the tree should be. One subscription rebuilds on any of
        // them; each source emits its current value on subscribe, so there is no
        // separate priming call.
        this.subscription = merge(
            this.translationService.currentLanguage$,
            this.authService.permissions$,
            this.authService.currentUser$,
            this.variantService.variant$
        ).subscribe(() => this.initializeMenu());
    }

    ngOnDestroy() {
        this.subscription?.unsubscribe();

        // The flag is only meaningful while something is computing it. Left at
        // false after a sign-out it would still be false when the next sign-in
        // builds a fresh layout, and that layout would start with its sidebar
        // and hamburger hidden on the strength of the previous user's answer.
        this.menuService.setHasVisibleItems(true);
    }

    /**
     * Root sections open and close in the proposed layout only. Classic renders
     * them as static headers exactly as it always has, and while a search is
     * running everything is expanded so results are visible without hunting.
     */
    get collapsibleRoots(): boolean {
        return this.variantService.isProposed && !this.searchTerm.trim();
    }

    /** The search field belongs to the proposed layout; classic is left untouched. */
    get showSearch(): boolean {
        return this.variantService.isProposed;
    }

    onSearch(term: string) {
        this.searchTerm = term;
        this.applySearch();
    }

    clearSearch() {
        this.searchTerm = '';
        this.applySearch();
    }

    initializeMenu() {
        const t = (key: string) => this.translationService.translate(key);
        const build = this.variantService.isProposed ? buildProposedMenu : buildClassicMenu;

        // The same filter the route guard uses to pick a landing page (live QA M5).
        this.fullModel = visibleMenu(build(t), {
            canView: code => this.permissionService.canView(code),
            hasPermission: code => this.permissionService.hasPermission(code),
            isSuperAdmin: () => this.authService.isSuperAdmin(),
            isMinistryAdminOrAbove: () => this.authService.isMinistryAdminOrAbove()
        });

        // Measured on the full tree, not the searched one: an empty result set
        // should not take the topbar's menu button away with it.
        const hasVisibleItems = this.fullModel.some(item => !item.separator && (
            (item.items && item.items.length > 0) || item.routerLink
        ));
        this.menuService.setHasVisibleItems(hasVisibleItems);

        this.applySearch();
    }

    private applySearch() {
        const term = this.searchTerm.trim().toLocaleLowerCase();
        if (!term) {
            this.model = this.fullModel;
            return;
        }
        this.model = this.fullModel
            .map(item => this.filterMenuItemBySearch(item, term))
            .filter(item => item !== null) as AppMenuItem[];
    }

    /**
     * A row survives if its own label matches, or if anything beneath it does.
     * A matching parent keeps its whole subtree — once you have found "Wafar",
     * hiding the three rows inside it would be the wrong kind of helpful.
     */
    private filterMenuItemBySearch(item: AppMenuItem, term: string): AppMenuItem | null {
        if (item.separator) {
            return null;
        }

        const selfMatches = (item.label || '').toLocaleLowerCase().includes(term);
        if (selfMatches) {
            return item;
        }

        if (item.items?.length) {
            const matchedChildren = item.items
                .map(child => this.filterMenuItemBySearch(child, term))
                .filter(child => child !== null) as AppMenuItem[];

            if (matchedChildren.length) {
                return { ...item, items: matchedChildren };
            }
        }

        return null;
    }
}
