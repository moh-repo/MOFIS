import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, filter, switchMap, take } from 'rxjs/operators';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { SKIP_AUTH_RECOVERY } from './http-context.tokens';

/**
 * Interceptor that:
 * 1. Attaches the UMS Bearer JWT to every request except the student portal's.
 * 2. Refreshes the token before it expires — at {@link REFRESH_AT} of its life
 *    (DEC-17, plan W5-02). The token lasts an hour; waiting for the 401 meant
 *    every session ended at the server's expiry, since a refresh of an expired
 *    token is refused.
 * 3. On 401: attempts a silent token refresh once.
 *    - If refresh succeeds: retries the original request with the new token.
 *    - If refresh fails: clears auth state (user sees login page via ErrorInterceptor).
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

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  private refreshing = false;
  private refreshToken$ = new BehaviorSubject<string | null>(null);

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
      return this.handleRefresh(req, next);
    }

    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status !== 401) return throwError(() => error);
        // The caller handles this 401 itself; refreshing or signing out here would
        // fight it. See SKIP_AUTH_RECOVERY.
        if (req.context.get(SKIP_AUTH_RECOVERY)) return throwError(() => error);
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
        // Skip refresh for UMS auth endpoints to avoid loops.
        if (req.url.includes('/ums/auth/')) {
          this.clearAuth();
          return throwError(() => error);
        }
        return this.handleRefresh(req, next);
      })
    );
  }

  private handleRefresh(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (this.refreshing) {
      // If there is no UMS token there is nothing to refresh and no success value will
      // ever be emitted on refreshToken$, so the downstream filter(t => t !== null) would
      // hang indefinitely. Fail fast instead.
      if (!this.umsAuthService.getToken()) {
        return throwError(() => new Error('No UMS token to refresh'));
      }
      return this.refreshToken$.pipe(
        filter(t => t !== null),
        take(1),
        switchMap(newToken => next.handle(this.addToken(req, newToken!)))
      );
    }

    this.refreshing = true;
    this.refreshToken$.next(null);

    const oldToken = this.umsAuthService.getToken();
    if (!oldToken) {
      this.refreshing = false;
      this.clearAuth();
      return throwError(() => new Error('No token to refresh'));
    }

    return this.http.post<{ token: string }>(
      `${environment.apiUrl}/mohe/api/v1/ums/auth/refresh`,
      {},
      { headers: { Authorization: `Bearer ${oldToken}` } }
    ).pipe(
      switchMap(res => {
        this.refreshing = false;
        this.umsAuthService.setTokenOnly(res.token);
        this.refreshToken$.next(res.token);
        return next.handle(this.addToken(req, res.token));
      }),
      catchError(err => {
        this.refreshing = false;
        this.clearAuth();
        return throwError(() => err);
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
