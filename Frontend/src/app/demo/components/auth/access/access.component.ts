import { Component } from '@angular/core';
import { SYSTEM_LOGO_FALLBACK } from '../../../service/system-logo';
import { environment } from 'src/environments/environment';

@Component({
    selector: 'app-access',
    templateUrl: './access.component.html',
    standalone: false
})
export class AccessComponent {

    /** The system's own emblem, not the template's (QA C2 / UI-003). */
    readonly logoUrl = SYSTEM_LOGO_FALLBACK;

    /**
     * Hard logout: clears ALL auth state from browser storage then forces a
     * full-page navigation to the login route.  Using window.location.href
     * instead of Angular Router guarantees we escape any guard loops.
     *
     * <p>Both stores, and deliberately more keys than `UmsAuthService` owns: this
     * is the escape hatch a user reaches when the app will not let them out
     * normally, so it clears the legacy keys (`umsUser`, `currentUser`) and the
     * `umsIsMinistryLevel`/`umsAdminTier` pair the original list predated. It
     * cannot call `UmsAuthService.clearAuthState()` — the point of this page is to
     * work when the injector state is what is broken.
     */
    logout(): void {
        this.endServerSession();
        const authKeys = [
            'umsToken', 'umsCurrentUser', 'umsPermissions',
            'umsPermissionMap', 'umsCountryIds', 'umsIsMinistryUser',
            'umsIsMinistryLevel', 'umsAdminTier',
            'umsUser', 'currentUser', 'loginAttempts'
        ];
        for (const key of authKeys) {
            try { localStorage.removeItem(key); } catch { /* storage blocked: nothing is stored either */ }
            try { sessionStorage.removeItem(key); } catch { /* as above */ }
        }
        this.go('/#/auth/login');
    }

    /**
     * Ends the session on the server before the browser forgets it (live QA M4): the
     * page used to clear storage only, so the token stayed valid. The same call the
     * profile menu's Logout makes, sent with a plain `fetch` rather than HttpClient
     * for the reason above; `keepalive` lets it finish while the page navigates away.
     */
    private endServerSession(): void {
        let token: string | null = null;
        for (const store of [() => localStorage, () => sessionStorage]) {
            try { token = token ?? store().getItem('umsToken'); } catch { /* storage blocked */ }
        }
        if (!token) return;
        try {
            fetch(`${environment.apiUrl}/mohe/api/v1/ums/auth/logout`, {
                method: 'POST',
                keepalive: true,
                headers: { Authorization: `Bearer ${token}` }
            }).catch(() => undefined);
        } catch { /* no fetch: the browser state is still cleared */ }
    }

    /**
     * The home page. Someone who may not open the dashboard is taken on from there to
     * the first page of their sidebar by the route guard (live QA M5); it used to bring
     * them straight back here.
     */
    goToDashboard(): void {
        this.go('/');
    }

    /** A full-page navigation, which escapes any guard loop. Its own method so a spec can stub it. */
    protected go(url: string): void {
        window.location.href = url;
    }
}
