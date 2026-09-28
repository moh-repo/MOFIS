import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { Observable, forkJoin, of, race, timer } from 'rxjs';
import { filter, map, take } from 'rxjs/operators';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { CodeListService } from '../service/code-list.service';
import { MenuVariantService } from '../../layout/menu/menu-variant.service';
import { buildClassicMenu } from '../../layout/menu/menu-classic';
import { buildProposedMenu } from '../../layout/menu/menu-proposed';
import { firstPermittedLink, passesGate, visibleMenu } from '../../layout/menu/menu-access';

/**
 * How long to wait for the UMS permission list to arrive before deciding.
 * The list comes over HTTP (sign-in, or /ums/auth/me), so a direct navigation
 * can reach the guard while it is still empty.
 */
const PERMISSIONS_WAIT_MS = 5000;

@Injectable({
    providedIn: 'root'
})
export class UmsPermissionGuard implements CanActivate {

    constructor(
        private authService: AuthService,
        private umsAuthService: UmsAuthService,
        private permissionService: UmsPermissionService,
        private router: Router,
        private codeLists: CodeListService,
        private menuVariant: MenuVariantService
    ) { }

    canActivate(route: ActivatedRouteSnapshot, _state: RouterStateSnapshot): Observable<boolean> {
        // First check the existing auth system (primary)
        if (!this.authService.isLoggedIn()) {
            this.router.navigate(['/auth/login']);
            return of(false);
        }

        // DEC-04 (QA SEC-001): a password an administrator set is temporary. Until it is
        // changed, the profile — where it is changed — is the only page that opens. The
        // server refuses the rest with PASSWORD_CHANGE_REQUIRED either way.
        if (this.umsAuthService.mustChangePassword && !_state.url.startsWith('/profile')) {
            this.router.navigate(['/profile'], { queryParams: { changePassword: 'required' } });
            return of(false);
        }

        // Get required permission from route data
        const requiredPermission = route.data['permission'] as string;
        const requiredPageCode = route.data['pageCode'] as string;
        // pageCodes: string[] — user needs canAccessPage() for AT LEAST ONE code (used for
        // routes that have a specific code but also accept a legacy fallback code)
        const requiredPageCodes = route.data['pageCodes'] as string[] | undefined;

        // A page only the super admin may open, whatever else the user holds.
        if (route.data['superAdminOnly']) {
            if (this.umsAuthService.isLoggedIn() && this.umsAuthService.isSuperAdmin()) {
                return this.whenCodeListsSettled().pipe(map(() => true));
            }
            this.router.navigate(['/auth/access']);
            return of(false);
        }

        // The admin tool pages (DEC-15): the super admin and the ministry admin, and then
        // only with the page's own permission, checked below like any other.
        if (route.data['ministryAdminOnly']
            && !(this.umsAuthService.isLoggedIn() && this.umsAuthService.isMinistryAdminOrAbove())) {
            this.router.navigate(['/auth/access']);
            return of(false);
        }

        // If no permission required, allow access
        if (!requiredPermission && !requiredPageCode && !requiredPageCodes?.length) {
            return this.whenCodeListsSettled().pipe(map(() => true));
        }

        // The super admin is allowed regardless of the permission list, so there
        // is nothing to wait for on the permission side. R-12: an administrator
        // of any other tier is judged by the codes of its bundle, like everyone.
        if (this.umsAuthService.isLoggedIn() && this.umsAuthService.isSuperAdmin()) {
            return this.whenCodeListsSettled().pipe(map(() => true));
        }

        return forkJoin([this.whenPermissionsSettled(), this.whenCodeListsSettled()]).pipe(
            map(() => this.evaluate(requiredPermission, requiredPageCode, requiredPageCodes, _state.url))
        );
    }

    /**
     * Loads the code lists (names, tag colours, icons of every code) in the
     * same step that waits for the permission map, so a feature route never
     * renders raw codes. Bounded by the same wait window: a slow or failed
     * load lets the route through and the pipes fall back to the codes.
     */
    private whenCodeListsSettled(): Observable<unknown> {
        if (this.codeLists.isLoaded) {
            return of(true);
        }
        return race(
            this.codeLists.ensureLoaded().pipe(take(1)),
            timer(PERMISSIONS_WAIT_MS)
        ).pipe(take(1));
    }

    /**
     * Emits once the permission list is populated, or once the wait window
     * expires — whichever comes first. A user who genuinely has no permissions
     * is still denied, just after the timeout rather than instantly.
     */
    private whenPermissionsSettled(): Observable<unknown> {
        if (this.umsAuthService.permissions.length > 0) {
            return of(true);
        }
        return race(
            this.umsAuthService.permissions$.pipe(filter(list => list.length > 0), take(1)),
            timer(PERMISSIONS_WAIT_MS)
        ).pipe(take(1));
    }

    /** The permission decision itself, evaluated against the settled list. */
    private evaluate(
        requiredPermission: string,
        requiredPageCode: string,
        requiredPageCodes: string[] | undefined,
        url: string
    ): boolean {
        // No UMS session: no access. FE-08: a legacy user record in storage whose
        // role.roleName said ADMIN or SUPER_ADMIN used to pass here — anyone could write
        // that to storage and open every guarded page (the backend still refused the
        // data). Rights come only from the UMS session the server issued.
        if (!this.umsAuthService.isLoggedIn()) {
            return this.deny(url);
        }

        // Super admin has all access
        if (this.umsAuthService.isSuperAdmin()) {
            return true;
        }

        // The permission, the page's VIEW right, and any one of the pages: the same check the
        // sidebar makes of every route on a row's link (R5-09).
        const gate = { permission: requiredPermission, pageCode: requiredPageCode, pageCodes: requiredPageCodes };
        if (!passesGate(gate, {
            canView: code => this.permissionService.canAccessPage(code),
            hasPermission: code => this.permissionService.hasPermission(code)
        })) {
            return this.deny(url);
        }

        return true;
    }

    /**
     * Where a refused navigation goes. A refused home page (live QA M5: a role with no
     * dashboard section, which is where sign-in lands) goes to the first row of the
     * person's own sidebar, or to their profile, which needs no permission, when the
     * sidebar is empty; anything else goes to Access Denied, as before. The target is
     * never the home page itself, so this cannot loop.
     */
    private deny(url: string): false {
        const path = (url || '/').split(/[?#]/)[0];
        if (path !== '/' && path !== '') {
            this.router.navigate(['/auth/access']);
            return false;
        }
        const build = this.menuVariant.isProposed ? buildProposedMenu : buildClassicMenu;
        const first = firstPermittedLink(visibleMenu(build(key => key), {
            canView: code => this.permissionService.canView(code),
            hasPermission: code => this.permissionService.hasPermission(code),
            isSuperAdmin: () => this.umsAuthService.isSuperAdmin(),
            isMinistryAdminOrAbove: () => this.umsAuthService.isMinistryAdminOrAbove()
        }));
        if (!first?.routerLink) {
            this.router.navigate(['/profile']);
        } else if (first.queryParams) {
            this.router.navigate(first.routerLink, { queryParams: first.queryParams });
        } else {
            this.router.navigate(first.routerLink);
        }
        return false;
    }
}
