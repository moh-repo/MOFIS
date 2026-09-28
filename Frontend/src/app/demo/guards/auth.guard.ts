import { Injectable } from '@angular/core';
import { Router, CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';

/**
 * The `exp` claim (seconds since the epoch) of a JWT, or null when the token is
 * not a JWT or carries no `exp`. This only decodes the payload — verifying the
 * signature is the server's job; the guard just wants to know whether a token
 * is already dead before loading the shell on it.
 */
export function jwtExpiry(token: string): number | null {
    const parts = token.split('.');
    if (parts.length !== 3) {
        return null;
    }
    try {
        const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
        const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
        const exp = JSON.parse(atob(padded))?.exp;
        return typeof exp === 'number' ? exp : null;
    } catch {
        return null;
    }
}

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
    constructor(
        private router: Router,
        private authService: AuthService,
        private umsAuthService: UmsAuthService
    ) { }

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const currentUser = this.authService.currentUserValue;

        if (currentUser && this.hasUsableToken()) {
            if (route.data['roles'] && !route.data['roles'].includes(currentUser.role.roleName)) {
                this.router.navigate(['/auth/access']);
                return false;
            }
            return true;
        }

        if (currentUser) {
            // A stored profile with no token, or an expired one, would load the shell
            // only for every request to come back 401. Drop it so the login page
            // starts clean and the user is not shown a half-working screen.
            this.authService.clearSession();
        }

        this.router.navigate(['/auth/login'], { queryParams: { returnUrl: state.url } });
        return false;
    }

    /** A token is present and, when it is a JWT with `exp`, has not expired. */
    private hasUsableToken(): boolean {
        const token = this.umsAuthService.getToken();
        if (!token) {
            return false;
        }
        const exp = jwtExpiry(token);
        return exp === null || exp * 1000 > Date.now();
    }
}
