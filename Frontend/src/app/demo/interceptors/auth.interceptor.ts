import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject, of } from 'rxjs';
import { catchError, filter, finalize, switchMap, take } from 'rxjs/operators';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { SKIP_AUTH_RECOVERY, isCredentialCheck, silentErrors } from './http-context.tokens';

/**
 * Interceptor that:
 * 1. Attaches the UMS Bearer JWT to every request except the student portal's.
 * 2. Refreshes the token before it expires — at {@link REFRESH_AT} of its life
 *    (DEC-17, plan W5-02). The token lasts an hour; waiting for the 401 meant
 *    every session ended at the server's expiry, since a refresh of an expired
 *    token is refused.
 * 3. On 401: attempts a silent token refresh once.
 *    - If refresh succeeds: retries the original request with the new token.
 *    - If refresh fails: clears auth state and rethrows the 401, which ErrorInterceptor
 *      turns into the sign-in redirect. That only works because ErrorInterceptor is
 *      registered *before* this one in app.module.ts (outermost, so it sees an error
 *      only after this interceptor has tried to recover from it) — FE-02.
 *
 * The portal exclusion is not cosmetic. It used to attach the staff JWT to
 * /student-portal/ requests as well, and the backend's portal endpoints called
 * ministry services that authorise against the UMS SecurityContext — so a
 * student's access to their own record was decided by whichever staff account
 * happened to be logged in to the same browser. The same student with the same
 * portal token saw 403 with nobody logged in, 200 behind a ministry account, and
 * 403 again behind an account scoped to another country.
 *
 * The backend no longer consults UMS on those paths, so this alone is not what
 * fixes it — but a staff credential has no business travelling on a request that
 * cannot use it, and leaving it attached keeps the coupling one refactor away
 * from returning.
 */
/** How much of a token's life may pass before it is refreshed (DEC-17). */
export const REFRESH_AT = 0.75;

/** The refresh endpoint, relative to `/mohe/api/v1`. */
export const REFRESH_PATH = '/ums/auth/refresh';

/** What a refresh ended with: the token to use, or the error that ended the session. */
interface RefreshOutcome {
  token: string | null;
  error?: unknown;
}

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  private refreshing = false;
  /** The latest refresh's outcome; null while one is in flight. Requests that arrive meanwhile wait on it. */
  private refreshOutcome$ = new BehaviorSubject<RefreshOutcome | null>(null);

  constructor(
    private authService: AuthService,
    private umsAuthService: UmsAuthService,
    private http: HttpClient
  ) {}

  /** The student portal authenticates with X-Student-Portal-Token, never the UMS JWT. */
  private static isStudentPortal(url: string): boolean {
    return url.includes('/student-portal/');
  }

  /**
   * True once REFRESH_AT of the token's life has passed. A token that cannot be
   * read is left alone: the 401 path still covers it.
   */
  static isStale(token: string | null, now = Date.now()): boolean {
    if (!token) { return false; }
    try {
      const [, payload] = token.split('.');
      const claims = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { iat?: number; exp?: number };
      if (!claims.iat || !claims.exp || claims.exp <= claims.iat) { return false; }
      const life = (claims.exp - claims.iat) * 1000;
      return now >= claims.iat * 1000 + life * REFRESH_AT;
    } catch {
      return false;
    }
  }

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.umsAuthService.getToken();
    const req = token && !AuthInterceptor.isStudentPortal(request.url)
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

    // Renew it before it expires, on the back of a request that is going out anyway
    // (W5-02). Not for the portal, and not for the auth endpoints themselves — the
    // refresh call is one of them.
    if (token && !AuthInterceptor.isStudentPortal(req.url) && !req.url.includes('/ums/auth/')
        && !this.refreshing && AuthInterceptor.isStale(token)) {
      return this.handleRefresh(req, next, null);
    }

    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status !== 401) return throwError(() => error);
        // The caller handles this 401 itself; refreshing or signing out here would
        // fight it. See SKIP_AUTH_RECOVERY.
        if (req.context.get(SKIP_AUTH_RECOVERY)) return throwError(() => error);
        // FE-01: a refused password or MFA code is the form's answer, not the end of
        // the session. The component shows it; nothing here refreshes or signs out.
        if (isCredentialCheck(req.url)) return throwError(() => error);
        // S-09: the server ended the session because the token turned up on another
        // device. Remember why, so the sign-in page can say so instead of just appearing.
        const ended = error.headers?.get('X-Session-Ended');
        if (ended) {
          try { sessionStorage.setItem('mohe.sessionEnded', ended); } catch { /* storage blocked: the page just reloads */ }
          this.clearAuth();
          return throwError(() => error);
        }
        // Student portal uses its own token — never touch UMS auth state for these.
        if (AuthInterceptor.isStudentPortal(req.url)) {
          return throwError(() => error);
        }
        // The refresh call's own failure is decided by handleRefresh, which made it.
        if (req.url.includes(REFRESH_PATH)) {
          return throwError(() => error);
        }
        // Skip refresh for UMS auth endpoints to avoid loops.
        if (req.url.includes('/ums/auth/')) {
          this.clearAuth();
          return throwError(() => error);
        }
        return this.handleRefresh(req, next, error);
      })
    );
  }

  /**
   * Renews the token, then sends `req` with the new one.
   *
   * `refused` is the 401 that prompted the refresh, or null when the refresh is the
   * proactive one (W5-02) and the current token is still good. How a failed refresh
   * ends depends on which (FE-03):
   * - after a 401 the token is already refused, so any failure signs out and the
   *   original 401 is rethrown for ErrorInterceptor to redirect;
   * - proactively, only a 401/403 from the refresh means the session is over. A
   *   network error or a 5xx says nothing about the token, which is still within its
   *   life: the request goes out with it, and a later request tries the renewal again.
   *
   * Only the refresh call's failure is handled here. The retried request's own error
   * goes to the caller untouched — a 500 on the retry is not a reason to sign out, and
   * a second 401 reaches ErrorInterceptor, which signs out. There is no second
   * refresh, so there is no loop.
   */
  private handleRefresh(req: HttpRequest<unknown>, next: HttpHandler,
                        refused: HttpErrorResponse | null): Observable<HttpEvent<unknown>> {
    if (this.refreshing) {
      // If there is no UMS token there is nothing to refresh and no outcome will ever be
      // published on refreshOutcome$, so waiting on it would hang. Fail fast instead.
      if (!this.umsAuthService.getToken()) {
        return throwError(() => refused ?? new Error('No UMS token to refresh'));
      }
      return this.refreshOutcome$.pipe(
        filter((o): o is RefreshOutcome => o !== null),
        take(1),
        switchMap(o => o.token
          ? next.handle(this.addToken(req, o.token))
          // The refresh ended the session: a request that was already refused shares the sign-out.
          : throwError(() => refused ?? o.error))
      );
    }

    const oldToken = this.umsAuthService.getToken();
    if (!oldToken) {
      this.clearAuth();
      return throwError(() => refused ?? new Error('No token to refresh'));
    }

    this.refreshing = true;
    this.refreshOutcome$.next(null);

    return this.http.post<{ token: string }>(
      `${environment.apiUrl}/mohe/api/v1${REFRESH_PATH}`,
      {},
      // The outcome is decided below; ErrorInterceptor must not toast a 503 from a
      // background renewal the user never asked for.
      { headers: { Authorization: `Bearer ${oldToken}` }, context: silentErrors() }
    ).pipe(
      // The request that started the refresh was cancelled before an answer came:
      // release the lock, and let anything waiting go on with the old token.
      finalize(() => {
        if (this.refreshing) {
          this.refreshing = false;
          this.refreshOutcome$.next({ token: oldToken });
        }
      }),
      catchError((err: unknown) => {
        this.refreshing = false;
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        if (refused !== null || status === 401 || status === 403) {
          this.refreshOutcome$.next({ token: null, error: err });
          this.clearAuth();
          return throwError(() => refused ?? err);
        }
        // Proactive renewal with the server unreachable or failing: carry on with the old token.
        this.refreshOutcome$.next({ token: oldToken });
        return of(null);
      }),
      switchMap(res => {
        if (res === null) {
          return next.handle(req);
        }
        this.refreshing = false;
        this.umsAuthService.setTokenOnly(res.token);
        this.refreshOutcome$.next({ token: res.token });
        return next.handle(this.addToken(req, res.token));
      })
    );
  }

  private addToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
    return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  private clearAuth(): void {
    this.umsAuthService.clearAuthState();
    this.authService.logout();
  }
}
