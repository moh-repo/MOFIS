import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, ReplaySubject, map, tap, catchError, of, firstValueFrom } from 'rxjs';
import { environment } from 'src/environments/environment';
import { LoginResponse, PermissionMap, AdminTier, MfaStatus, MfaEnrolment, SessionUser, toSessionUser } from '../api/ums';
import { HttpContext } from '@angular/common/http';
import { SKIP_AUTH_RECOVERY, SUPPRESS_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { Router } from '@angular/router';
import { CountrySelectionService } from './country-selection.service';

/**
 * Every browser-storage key an auth session may leave behind; the list
 * `clearAuthState` walks.
 *
 * <p>Only `umsToken` is still written (see {@link UmsAuthService.tokenStore}).
 * The rest are keys older builds wrote and this one purges — see
 * {@link PROFILE_KEYS}. Signing out keeps clearing all of them: a browser that
 * has not loaded the new build since its last sign-in still holds the old values.
 */
const AUTH_KEYS = [
    'umsToken', 'umsCurrentUser', 'umsPermissions', 'umsPermissionMap',
    'umsCountryIds', 'umsIsMinistryUser', 'umsIsMinistryLevel', 'umsAdminTier'
] as const;

/**
 * What the signed-in account is and may do: the account record, its permission
 * codes and map, its attachés, and the ministry/tier flags.
 *
 * <p>These used to be written to `localStorage` at every sign-in and `/me`, and
 * they sat there until logout — the whole permission map of a super admin, in
 * plain text, in a tab any browser extension or injected script can read. They
 * are not a security boundary (the server re-reads the real tier and codes from
 * the database on every request — `JwtAuthFilter` → `UserAuthorityCache`), so
 * nothing was ever *granted* by them; they were simply a cache so a page refresh
 * did not have to wait for `/me` before the router could decide a route.
 *
 * <p>That cache now lives in memory only. The wait it saved is paid once at
 * bootstrap instead, by {@link UmsAuthService.restoreSession}, which the
 * `APP_INITIALIZER` in `app.module.ts` runs before the first navigation. The
 * only auth value left in browser storage is the token itself.
 *
 * <p>The list is purged on construction so an existing browser is cleaned by
 * loading the new build, without needing the user to sign out first.
 */
const PROFILE_KEYS = [
    'umsCurrentUser', 'umsPermissions', 'umsPermissionMap', 'umsCountryIds',
    'umsIsMinistryUser', 'umsIsMinistryLevel', 'umsAdminTier',
    // Written by older builds: the legacy shell user (now an in-memory
    // projection in AuthService) and a UMS user record that predates it.
    'currentUser', 'umsUser'
] as const;

/**
 * A removal that cannot throw.
 *
 * <p>Storage is not always there: a private window, a browser configured to block
 * site data, or an embedded webview all make `localStorage` throw on access rather
 * than return null. That used to happen inside the constructor of a
 * `providedIn: 'root'` service, which means the application does not boot at all —
 * a blank page, for a stored value the app can perfectly well do without.
 */
function removeKey(store: Storage, key: string): void {
    try {
        store.removeItem(key);
    } catch { /* nothing to do; the value is already unreachable */ }
}

@Injectable({
    providedIn: 'root'
})
export class UmsAuthService {
    private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/auth`;

    /**
     * Where the session token lives: `sessionStorage` (per tab, gone when the tab
     * closes) when true, `localStorage` (shared by every tab, survives a browser
     * restart) when false.
     *
     * <p>**This is a deployment policy decision, not a bug to fix, and it is off
     * because turning it on has a cost that is not obvious.** `sessionStorage` is
     * per-tab, so each new tab has no token and must sign in again — and the server
     * allows `jwt.max-concurrent-sessions` (3) live sessions per account and *ends
     * the oldest* beyond that (`UmsUserSessionService.evictBeyondLimit`). A user
     * working across four tabs would silently lose the first one.
     *
     * <p>What it buys is narrow: it closes the case where someone closes the tab
     * without signing out and leaves the machine unlocked, so the next person to
     * open the application is already signed in as them. It does **not** defend
     * against injected script — a same-origin script reads `sessionStorage` exactly
     * as easily as `localStorage`; the CSP in `index.html` is what addresses that.
     *
     * <p>Turn it on for shared or kiosk workstations, and raise
     * `JWT_MAX_CONCURRENT_SESSIONS` at the same time. Leave it off where staff have
     * their own machines. Existing sessions are migrated either way — see
     * {@link migrateTokenStore}.
     */
    private static readonly TOKEN_IN_SESSION_STORAGE = false;

    private static get tokenStore(): Storage {
        return UmsAuthService.TOKEN_IN_SESSION_STORAGE ? sessionStorage : localStorage;
    }

    private currentUserSubject: BehaviorSubject<SessionUser | null>;
    private permissionsSubject: BehaviorSubject<string[]>;
    private permissionMapSubject: BehaviorSubject<PermissionMap>;
    private countryIdsSubject: BehaviorSubject<number[]>;
    private isMinistryUserSubject: BehaviorSubject<boolean>;
    /** N-01/N-04: super admin, ministry user, or admin holding every country — read-only on Wafar and attaché staff. */
    private isMinistryLevelSubject: BehaviorSubject<boolean>;
    /** R-12: the tier the server resolved from the roles; null before a login that carries it. */
    private adminTierSubject: BehaviorSubject<AdminTier | null>;
    /** R-20: when the profile was last re-read, so a focus every few seconds does not become a request. */
    private lastProfileRefresh = 0;
    private static readonly PROFILE_REFRESH_MIN_MS = 30_000;

    /**
     * The whole account record as the server last sent it, for the one consumer
     * that needs more than {@link SessionUser}: `AuthService`, which projects the
     * shell's `User` (it shows a surname in the topbar) and used to read it from
     * `localStorage.currentUser`.
     *
     * <p>A `ReplaySubject` and not a plain one: `AuthService` is constructed
     * lazily by whichever guard or component asks for it first, which is after
     * the `APP_INITIALIZER` has already restored the session. A late subscriber
     * has to get the profile it missed, or the shell shows nobody signed in.
     */
    private profileSubject = new ReplaySubject<LoginResponse>(1);
    public profile$ = this.profileSubject.asObservable();

    public currentUser$: Observable<SessionUser | null>;
    public permissions$: Observable<string[]>;
    public permissionMap$: Observable<PermissionMap>;
    public countryIds$: Observable<number[]>;
    public isMinistryUser$: Observable<boolean>;

    constructor(
        private http: HttpClient,
        private router: Router,
        private countrySelectionService: CountrySelectionService
    ) {
        UmsAuthService.migrateTokenStore();
        UmsAuthService.purgeProfileKeys();

        // The signed-out state, always. What the account is and may do is not read
        // back from storage any more (see PROFILE_KEYS); `restoreSession` fills these
        // from `/me` before the first navigation, and a login fills them from its reply.
        this.currentUserSubject = new BehaviorSubject<SessionUser | null>(null);
        this.permissionsSubject = new BehaviorSubject<string[]>([]);
        this.permissionMapSubject = new BehaviorSubject<PermissionMap>({});
        this.countryIdsSubject = new BehaviorSubject<number[]>([]);
        this.isMinistryUserSubject = new BehaviorSubject<boolean>(false);
        this.isMinistryLevelSubject = new BehaviorSubject<boolean>(false);
        this.adminTierSubject = new BehaviorSubject<AdminTier | null>(null);

        this.currentUser$ = this.currentUserSubject.asObservable();
        // R-20: a role or country change made by an administrator reaches this
        // browser when the window comes back into view, not at the next login.
        if (typeof window !== 'undefined' && typeof document !== 'undefined') {
            window.addEventListener('focus', () => this.refreshProfileOnFocus());
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState === 'visible') { this.refreshProfileOnFocus(); }
            });
        }
        this.permissions$ = this.permissionsSubject.asObservable();
        this.permissionMap$ = this.permissionMapSubject.asObservable();
        this.countryIds$ = this.countryIdsSubject.asObservable();
        this.isMinistryUser$ = this.isMinistryUserSubject.asObservable();
    }

    /**
     * Moves an existing token to whichever store {@link TOKEN_IN_SESSION_STORAGE}
     * now names, so flipping that constant does not sign everybody out on the
     * deploy that carries it — and, more importantly, does not leave a working
     * token behind in the store we just decided should not hold one.
     */
    private static migrateTokenStore(): void {
        try {
            const abandoned = UmsAuthService.TOKEN_IN_SESSION_STORAGE ? localStorage : sessionStorage;
            const stale = abandoned.getItem('umsToken');
            if (stale === null) {
                return;
            }
            if (UmsAuthService.tokenStore.getItem('umsToken') === null) {
                UmsAuthService.tokenStore.setItem('umsToken', stale);
            }
            abandoned.removeItem('umsToken');
        } catch { /* storage unavailable: nothing stored, nothing to move */ }
    }

    /**
     * Removes what older builds persisted about the account, from both stores.
     *
     * <p>Runs on construction rather than only on sign-out, so a browser that is
     * already carrying a super admin's permission map is cleaned by loading the
     * new build — the values are dead weight the moment this service stops
     * reading them, and leaving them to expire at the next sign-out would keep
     * them in place for as long as the session lasts.
     */
    private static purgeProfileKeys(): void {
        for (const key of PROFILE_KEYS) {
            removeKey(localStorage, key);
            removeKey(sessionStorage, key);
        }
    }

    /**
     * Fills the session from the server before the router decides anything.
     *
     * <p>Called by the `APP_INITIALIZER` in `app.module.ts`. Nothing about the
     * account survives a page load any more, so a refresh arrives with a token
     * and no idea who it belongs to; this is the one request that turns the
     * former into the latter. Without it `AuthGuard` would see a signed-out app
     * on every F5 and bounce a perfectly valid session to the login page.
     *
     * <p>The failure split is deliberate:
     * <ul>
     *   <li>401/403 — the token is expired, revoked, or the account is gone.
     *       Clear it and let the guard redirect; keeping it would only produce a
     *       shell where every request comes back 401.
     *   <li>anything else (the server is down, the network dropped, a 500) — keep
     *       the token. The app still starts signed out and the guard still
     *       redirects, but a reload once the server is back restores the session
     *       instead of demanding credentials over a transient outage.
     * </ul>
     *
     * <p>`SKIP_AUTH_RECOVERY` keeps the interceptor out of it: its 401 handling
     * ends in `AuthService.logout()`, which navigates — and navigating from
     * inside an initializer, before the router has bootstrapped, is how you get
     * a blank page instead of a login form.
     */
    restoreSession(): Promise<void> {
        if (!this.getToken()) {
            return Promise.resolve();
        }
        const context = new HttpContext()
            .set(SUPPRESS_ERROR_TOAST, true)
            .set(SKIP_AUTH_RECOVERY, true);
        this.lastProfileRefresh = Date.now();
        return firstValueFrom(
            this.http.get<LoginResponse>(`${this.apiUrl}/me`, { context }).pipe(
                tap(response => this.applyProfile(response)),
                catchError((error: { status?: number }) => {
                    if (error?.status === 401 || error?.status === 403) {
                        this.clearAuthState();
                    }
                    return of(null);
                })
            )
        ).then(() => void 0);
    }

    public get currentUserValue(): SessionUser | null {
        return this.currentUserSubject.value;
    }

    /** DEC-04: the password was set by an administrator; nothing else opens until it is changed. */
    public get mustChangePassword(): boolean {
        return this.currentUserSubject.value?.mustChangePassword === true;
    }

    public get permissions(): string[] {
        return this.permissionsSubject.value;
    }

    public get permissionMap(): PermissionMap {
        return this.permissionMapSubject.value;
    }

    public get countryIds(): number[] {
        return this.countryIdsSubject.value;
    }

    public get isMinistryUser(): boolean {
        return this.isMinistryUserSubject.value;
    }

    /**
     * N-01/N-04: the account acts for the ministry (super admin, ministry user, or an
     * admin holding every attaché). Such accounts read Wafar but never write it, and
     * ministry staff (the flag without IsAdmin) administer no attaché staff. The
     * server decides; this only hides what would be refused.
     */
    public get isMinistryLevel(): boolean {
        return this.isMinistryLevelSubject.value;
    }

    /** R-12: the tier the server resolved; null when the session predates it (the flags stand in). */
    public get adminTier(): AdminTier | null {
        return this.adminTierSubject.value;
    }

    /**
     * The super admin or the ministry admin: who keeps the admin tool pages (DEC-15,
     * review M15). The server decides; this only hides what would be refused.
     */
    public isMinistryAdminOrAbove(): boolean {
        if (this.isSuperAdmin()) { return true; }
        const tier = this.adminTier;
        if (tier) { return tier === 'SUPER_ADMIN' || tier === 'MINISTRY_ADMIN'; }
        return this.isMinistryUser && this.isAdmin();
    }

    /** Ministry staff (the MINISTRY_STAFF tier): sees every staff account, administers none. */
    public isMinistryStaff(): boolean {
        const tier = this.adminTier;
        if (tier) { return tier === 'MINISTRY_STAFF'; }
        return this.isMinistryUser && !this.isAdmin() && !this.isSuperAdmin();
    }

    /** Whether the signed-in account may hand out a role that confers `tier` (N-02.b, mirrored from the server). */
    public mayGrantTier(tier: AdminTier): boolean {
        const mine = this.adminTier ?? (this.isSuperAdmin() ? 'SUPER_ADMIN' : this.isAdmin() ? 'COUNTRY_ADMIN' : 'COUNTRY_STAFF');
        switch (mine) {
            case 'SUPER_ADMIN': return true;
            case 'MINISTRY_ADMIN': return tier !== 'SUPER_ADMIN';
            case 'COUNTRY_ADMIN': return tier === 'COUNTRY_ADMIN' || tier === 'COUNTRY_STAFF';
            default: return false;
        }
    }

    /**
     * R-20: re-reads the signed-in profile from the server and replaces the
     * stored permission map, so menus and buttons follow an administrator's
     * change within seconds. Silent: a failure leaves the current state alone.
     */
    refreshProfile(): Observable<LoginResponse | null> {
        if (!this.getToken() || !this.isLoggedIn()) { return of(null); }
        this.lastProfileRefresh = Date.now();
        return this.http.get<LoginResponse>(`${this.apiUrl}/me`, { context: new HttpContext().set(SUPPRESS_ERROR_TOAST, true) })
            .pipe(
                tap(response => this.applyProfile(response)),
                catchError(() => of(null))
            );
    }

    private refreshProfileOnFocus(): void {
        if (Date.now() - this.lastProfileRefresh < UmsAuthService.PROFILE_REFRESH_MIN_MS) { return; }
        this.refreshProfile().subscribe();
    }

    /**
     * Stores what the server says about the account (login and /me alike).
     *
     * <p>The account itself is narrowed to {@link SessionUser} first: the reply
     * carries the full record, and only these fields are ever read. The whole
     * reply still goes to {@link profile$} for `AuthService`, in memory.
     *
     * <p>Nothing here is persisted — see {@link PROFILE_KEYS}. This state lives
     * for as long as the page does, and {@link restoreSession} rebuilds it after
     * a reload.
     */
    private applyProfile(response: LoginResponse): void {
        const isMinistry = response.isMinistryUser ?? response.user?.isMinistryUser ?? false;
        const tier = response.adminTier ?? null;
        const isMinistryLevel = response.isMinistryLevel === true
            || tier === 'SUPER_ADMIN' || tier === 'MINISTRY_ADMIN' || tier === 'MINISTRY_STAFF';
        const sessionUser = toSessionUser(response.user);

        this.currentUserSubject.next(sessionUser);
        this.permissionsSubject.next(response.permissions);
        this.permissionMapSubject.next(response.permissionMap);
        this.countryIdsSubject.next(response.countryIds);
        this.isMinistryLevelSubject.next(isMinistryLevel);
        this.isMinistryUserSubject.next(isMinistry);
        this.adminTierSubject.next(tier);
        this.profileSubject.next(response);
    }

    /** Limit study-country dropdowns to the user's UMS-assigned countries. */
    filterToAllowedStudyCountries<T extends { id: number }>(items: T[]): T[] {
        if (this.isMinistryUser) {
            return items;
        }
        const allowed = new Set(this.countryIds || []);
        return items.filter(item => allowed.has(item.id));
    }

    /**
     * A new security-code challenge: the image to show, and the id to send back
     * with the credentials. The answer stays on the server — the old challenge
     * was drawn and marked in the browser, so anything posting straight to
     * `/login` never met it.
     */
    captcha(): Observable<{ id: string; imageUrl: string }> {
        return this.http.get(`${this.apiUrl}/captcha`, { observe: 'response', responseType: 'blob' })
            .pipe(map(response => ({
                id: response.headers.get('X-Captcha-Id') || '',
                imageUrl: URL.createObjectURL(response.body as Blob)
            })));
    }

    login(username: string, password: string, captchaId: string, captchaAnswer: string): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(`${this.apiUrl}/login`, { username, password, captchaId, captchaAnswer })
            .pipe(tap(response => this.storeSignIn(response)));
    }

    /**
     * Keeps a finished sign-in. A reply without a token is an O-16 challenge
     * (`mfaRequired` / `mfaEnrolmentRequired` with a pending token) and stores
     * nothing: the account is not signed in until the second factor is done.
     */
    private storeSignIn(response: LoginResponse): void {
        if (!response.token) { return; }
        this.setTokenOnly(response.token);
        this.lastProfileRefresh = Date.now();
        this.applyProfile(response);
    }

    // ── O-16: the second factor ─────────────────────────────────────────────

    /** Finishes a sign-in whose password was accepted; the reply is the full sign-in. */
    mfaVerify(mfaToken: string, code: string): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(`${this.apiUrl}/mfa/verify`, { mfaToken, code })
            .pipe(tap(response => this.storeSignIn(response)));
    }

    /** A new secret and QR code: mid sign-in with the pending token, or from the profile with the session. */
    mfaEnrolStart(mfaToken?: string): Observable<MfaEnrolment> {
        return this.http.post<MfaEnrolment>(`${this.apiUrl}/mfa/enrol/start`, mfaToken ? { mfaToken } : {});
    }

    /** Turns the factor on; mid sign-in the reply is also the finished sign-in. */
    mfaEnrolConfirm(code: string, mfaToken?: string): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(`${this.apiUrl}/mfa/enrol/confirm`, mfaToken ? { mfaToken, code } : { code })
            .pipe(tap(response => this.storeSignIn(response)));
    }

    mfaStatus(): Observable<MfaStatus> {
        return this.http.get<MfaStatus>(`${this.apiUrl}/mfa/status`);
    }

    mfaDisable(code: string): Observable<unknown> {
        return this.http.post(`${this.apiUrl}/mfa/disable`, { code });
    }

    mfaRecoveryCodes(code: string): Observable<{ recoveryCodes: string[] }> {
        return this.http.post<{ recoveryCodes: string[] }>(`${this.apiUrl}/mfa/recovery-codes`, { code });
    }

    logout(): void {
        const token = this.getToken();
        const headers: { [key: string]: string } = {};
        if (token) headers['Authorization'] = `Bearer ${token}`;
        if (Object.keys(headers).length > 0) {
            this.http.post(`${this.apiUrl}/logout`, {}, { headers }).subscribe();
        }
        this.clearAuthState();
        this.router.navigate(['/auth/login']);
    }

    /**
     * Notify backend of logout (terminate session). Call before clearAuthState when
     * AuthService handles the logout flow.
     */
    notifyLogout(_userId: number): void {
        const token = this.getToken();
        const headers: { [key: string]: string } = {};
        if (token) headers['Authorization'] = `Bearer ${token}`;
        this.http.post(`${this.apiUrl}/logout`, {}, { headers }).subscribe({
            next: () => {},
            error: () => {}
        });
    }

    /**
     * The session's bearer token. The only reader outside this service is
     * `AuthInterceptor`, which puts it in an `Authorization` header — never in a
     * URL, where it would reach the access log and the browser history.
     */
    getToken(): string | null {
        try {
            return UmsAuthService.tokenStore.getItem('umsToken');
        } catch {
            return null;
        }
    }

    /** Update the stored JWT only — used by the auth interceptor after a silent refresh. */
    setTokenOnly(token: string): void {
        try {
            UmsAuthService.tokenStore.setItem('umsToken', token);
        } catch { /* storage unavailable: the interceptor still holds it for this request */ }
    }

    /**
     * Clear auth state without navigating. Used when AuthService handles logout.
     *
     * <p>Clears each key from **both** stores rather than from the one
     * {@link tokenStore} currently names: a build that flipped
     * {@link TOKEN_IN_SESSION_STORAGE} must not leave the old store's copy behind,
     * and signing out is the one operation that has to be thorough even when the
     * configuration has moved underneath it.
     */
    clearAuthState(): void {
        for (const key of [...AUTH_KEYS, ...PROFILE_KEYS]) {
            removeKey(localStorage, key);
            removeKey(sessionStorage, key);
        }

        this.currentUserSubject.next(null);
        this.permissionsSubject.next([]);
        this.permissionMapSubject.next({});
        this.countryIdsSubject.next([]);
        this.isMinistryUserSubject.next(false);
        this.isMinistryLevelSubject.next(false);
        this.adminTierSubject.next(null);
        this.countrySelectionService.setSelectedCountryId(null);
    }

    changePassword(currentPassword: string, newPassword: string): Observable<any> {
        return this.http.post(`${this.apiUrl}/change-password`, { currentPassword, newPassword });
    }

    isLoggedIn(): boolean {
        return !!this.currentUserValue;
    }

    isAdmin(): boolean {
        return this.currentUserValue?.isAdmin || false;
    }

    isSuperAdmin(): boolean {
        return this.currentUserValue?.isSuperAdmin || false;
    }

    /**
     * Set user from legacy auth response when UMS login fails but old auth succeeds.
     * Clears any stale permissions and ensures privilege checks use the correct user.
     */
    setUserFromLegacyAuth(legacyUser: { id: number; userName?: string; isAdmin?: boolean; isSuperAdmin?: boolean }): void {
        const sessionUser: SessionUser = {
            id: legacyUser.id,
            userName: legacyUser.userName ?? '',
            isAdmin: legacyUser.isAdmin ?? false,
            isSuperAdmin: legacyUser.isSuperAdmin ?? false,
            isMinistryUser: false
        };
        this.currentUserSubject.next(sessionUser);
        this.permissionsSubject.next([]);
        this.permissionMapSubject.next({});
        this.countryIdsSubject.next([]);
        this.isMinistryUserSubject.next(false);
        this.isMinistryLevelSubject.next(false);
        this.adminTierSubject.next(null);
    }

    /*
     * There is no reload of the permissions by user id (live QA R5-08). It read the
     * staff-administration endpoints (/ums/permissions/user/{id}/codes and /map,
     * /ums/users/{id}/countries), which an account without that page is refused; the
     * session's own copy comes from sign-in and /me (applyProfile).
     */
}