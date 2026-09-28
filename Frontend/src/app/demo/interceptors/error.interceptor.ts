import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { UmsAuthService } from '../service/ums-auth.service';
import { AuthService } from '../service/auth.service';
import { TranslationService } from '../service/translation.service';
import { SUPPRESS_ERROR_TOAST } from './http-context.tokens';
import { extractApiErrorMessage } from '../service/api-error.util';
import { StudentPortalApiService } from '../../student-portal/student-portal-api.service';

/** Backend ErrorDetails shape (aligned with GlobalExceptionHandler). */
interface ErrorDetailsBody {
  timestamp?: string;
  code?: string;
  /** The request a PENDING_EXISTS refusal names (W4-08). */
  requestId?: number;
  message?: string;
  details?: string;
  fieldErrors?: Array<{ field: string; message: string }>;
}

/**
 * Global HTTP error interceptor.
 *
 * 401 → clear auth state + redirect to /auth/login (staff), or clear the portal token
 *        + redirect to /student-portal/login (portal session calls)
 * 403 → "no permission" toast (honours SUPPRESS_ERROR_TOAST); the component's own error path still runs
 * 400/422 → show field-level validation errors if present, else generic message
 * 404 → show not-found toast
 * 409 → show conflict message
 * 429 → "too many requests, wait a minute" toast, at most one per 15 s
 * 502/503/504 → "application server is not running" toast (gateway body never shown)
 * other 5xx → generic server error toast
 * status=0 → network offline toast
 */
@Injectable()
export class ErrorInterceptor implements HttpInterceptor {

  /** One 429 toast per burst — see the 429 branch. */
  private static readonly THROTTLE_TOAST_GAP_MS = 15000;
  private lastThrottleToastAt = 0;

  constructor(
    private messageService: MessageService,
    private router: Router,
    private umsAuthService: UmsAuthService,
    private authService: AuthService,
    private translationService: TranslationService,
    private portalApi: StudentPortalApiService
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        const status = error.status;
        const body: ErrorDetailsBody | null =
          error.error && typeof error.error === 'object' ? error.error as ErrorDetailsBody : null;

        /*
         * Callers that treat a failure as a normal outcome opt out of the toast. Session
         * handling below still runs: an expired token must sign the user out no matter
         * which request happened to surface it.
         */
        const silent = request.context.get(SUPPRESS_ERROR_TOAST);

        switch (true) {
          case status === 0:
            if (!silent && !request.url.includes('/export-report') && !request.url.includes('/report-pdf')) {
              this.messageService.add({
                severity: 'warn',
                summary: this.t('COMMON.HTTP.CONNECTION_ERROR_TITLE', 'Connection Error'),
                detail: this.t('COMMON.HTTP.CONNECTION_ERROR',
                  'Unable to reach the server. Please check your network and try again.'),
                life: 8000
              });
            }
            break;

          case status === 401:
            if (this.isPortalSessionCall(request.url)) {
              this.portalApi.clearToken();
              this.router.navigate(['/student-portal/login']);
            } else if (!request.url.includes('/student-portal/') && !request.url.includes('/ums/auth/login')) {
              this.umsAuthService.clearAuthState();
              this.authService.logout();
              this.router.navigate(['/auth/login'], {
                queryParams: { returnUrl: this.router.url, reason: 'session_expired' }
              });
            }
            break;

          /*
           * A 403 used to be swallowed here on the theory that UmsPermissionGuard
           * had already decided the page. It had not always decided the same way
           * the server did: a route guarded by one code and an endpoint by another
           * left the user on a page that silently showed nothing. The toast names
           * the fact; the component's own error path still runs.
           */
          case status === 403 && body?.code === 'PASSWORD_CHANGE_REQUIRED':
            // DEC-04: the account holds a password an administrator set. Every call the
            // shell makes is refused until it is changed, so this is a redirect, not a
            // stream of "access denied" toasts.
            if (!this.router.url.startsWith('/profile')) {
              this.router.navigate(['/profile'], { queryParams: { changePassword: 'required' } });
            }
            break;

          case status === 403 && body?.code === 'PORTAL_PASSWORD_CHANGE_REQUIRED':
            // SEC-001 (D9-a): the student has never chosen a password; the portal is held
            // to the change. A redirect to the form, not an "access denied" toast.
            this.portalApi.mustChangePassword = true;
            if (!this.router.url.startsWith('/student-portal/security')) {
              this.router.navigate(['/student-portal/security'], { queryParams: { changePassword: 'required' } });
            }
            break;

          case status === 403:
            // R-20: a refusal may mean the account's rights changed since the
            // menus were drawn; re-read the profile so they catch up.
            if (!request.url.includes('/student-portal/') && !request.url.includes('/ums/auth/me')) {
              this.umsAuthService.refreshProfile().subscribe();
            }
            if (silent) {
              break;
            }
            this.messageService.add({
              severity: 'warn',
              summary: this.t('COMMON.HTTP.FORBIDDEN_TITLE', 'Access denied'),
              detail: this.t('COMMON.FORBIDDEN', 'You do not have permission for this action'),
              life: 8000
            });
            break;

          case status === 400 || status === 422: {
            if (silent) {
              break;
            }
            if (body?.fieldErrors?.length) {
              const fieldMessages = body.fieldErrors
                .map(fe => `${fe.field}: ${fe.message}`)
                .join('\n');
              this.messageService.add({
                severity: 'warn',
                summary: this.t('COMMON.HTTP.VALIDATION_ERROR', 'Validation Error'),
                detail: fieldMessages,
                life: 10000
              });
            } else {
              const detail = extractApiErrorMessage(error)
                || this.t('COMMON.HTTP.INVALID_REQUEST_DETAIL', 'Please check your input and try again.');
              this.messageService.add({
                severity: 'warn',
                summary: this.t('COMMON.HTTP.INVALID_REQUEST', 'Invalid Request'),
                detail,
                life: 8000
              });
            }
            break;
          }

          case status === 404: {
            if (silent) {
              break;
            }
            const detail = extractApiErrorMessage(error)
              || this.t('COMMON.HTTP.NOT_FOUND', 'The requested resource was not found.');
            this.messageService.add({
              severity: 'warn',
              summary: this.t('COMMON.HTTP.NOT_FOUND_TITLE', 'Not Found'),
              detail,
              life: 8000
            });
            break;
          }

          case status === 409: {
            if (silent) {
              break;
            }
            const detail = extractApiErrorMessage(error)
              || this.t('COMMON.HTTP.CONFLICT_DETAIL', 'A conflict occurred. Please refresh and try again.');
            // The record already has a request awaiting approval (W4-08): the server's
            // message names the request; the title says what kind of conflict it is.
            const pending = body?.code === 'PENDING_EXISTS';
            this.messageService.add({
              severity: 'warn',
              summary: pending
                ? this.t('COMMON.HTTP.PENDING_EXISTS', 'Awaiting approval')
                : this.t('COMMON.HTTP.CONFLICT', 'Conflict'),
              detail,
              life: 10000
            });
            break;
          }

          /*
           * 429 is ApiRateLimitFilter (reports, charts, PDF, import). Its body is
           * an English sentence, so the message is our own. A report page fires
           * several requests at once and each is refused, so one toast stands
           * for the burst rather than one per call.
           *
           * Since September 2026 the filter also sends Retry-After (whole seconds
           * until the window closes), so the toast can say how long rather than
           * "wait a minute" — which was wrong by up to a minute either way.
           */
          case status === 429: {
            if (silent) {
              break;
            }
            const now = Date.now();
            if (now - this.lastThrottleToastAt < ErrorInterceptor.THROTTLE_TOAST_GAP_MS) {
              break;
            }
            this.lastThrottleToastAt = now;
            const seconds = this.retryAfterSeconds(error);
            const detail = seconds
              ? this.t('COMMON.HTTP.TOO_MANY_REQUESTS_RETRY',
                  'Too many requests were sent in a short time. Please try again in {{seconds}} seconds.')
                  .replace('{{seconds}}', String(seconds))
              : this.t('COMMON.HTTP.TOO_MANY_REQUESTS',
                  'Too many requests were sent in a short time. Please wait a minute and try again.');
            this.messageService.add({
              severity: 'warn',
              summary: this.t('COMMON.HTTP.TOO_MANY_REQUESTS_TITLE', 'Too many requests'),
              detail,
              life: 10000
            });
            break;
          }

          /*
           * 502/503/504 come from whatever sits in front of the backend — the
           * ng-serve proxy in development, nginx in production — when the
           * backend itself is down. Their bodies are that gateway's own page
           * ("Error occurred while trying to proxy: localhost:4200/mohe/api/…"),
           * which names hosts and URLs and tells the user nothing they can act
           * on, so it is never echoed: the message is the plain fact that the
           * application server is not running.
           */
          case status === 502 || status === 503 || status === 504: {
            if (silent) {
              break;
            }
            this.messageService.add({
              severity: 'error',
              summary: this.t('COMMON.HTTP.BACKEND_UNAVAILABLE_TITLE', 'Server Unavailable'),
              detail: this.t('COMMON.HTTP.BACKEND_UNAVAILABLE',
                'The application server is not running or is not responding. Please start it or contact the administrator, then try again.'),
              life: 10000
            });
            break;
          }

          case status >= 500: {
            if (silent) {
              break;
            }
            // Only a structured backend body (ErrorDetails / {error}) carries a
            // message meant for the user; an HTML or text body is a server page.
            const detail = (body ? extractApiErrorMessage(error) : null)
              || this.t('COMMON.HTTP.SERVER_ERROR',
                'Something went wrong on the server. Please try again later.');
            this.messageService.add({
              severity: 'error',
              summary: this.t('COMMON.HTTP.SERVER_ERROR_TITLE', 'Server Error'),
              detail,
              life: 8000
            });
            break;
          }
        }

        return throwError(() => error);
      })
    );
  }

  /**
   * How long the server says to wait, from the `Retry-After` header or the
   * `retryAfterSeconds` field of the body. Returns 0 when neither is usable (an
   * HTTP-date `Retry-After`, a proxy's own 429, a blob body), and the toast then
   * falls back to the vague wording.
   */
  private retryAfterSeconds(error: HttpErrorResponse): number {
    const header = Number(error.headers?.get('Retry-After'));
    if (Number.isFinite(header) && header > 0) {
      return Math.ceil(header);
    }
    const fromBody = Number((error.error as { retryAfterSeconds?: unknown })?.retryAfterSeconds);
    return Number.isFinite(fromBody) && fromBody > 0 ? Math.ceil(fromBody) : 0;
  }

  /**
   * Portal calls that ride on the session token. A 401 from one of these means the
   * token is dead (expired, revoked, or cleared server-side). The portal auth
   * endpoints are deliberately not included: a 401 from login or the reset form is
   * a wrong password, which the form itself shows.
   */
  private isPortalSessionCall(url: string): boolean {
    return url.includes('/student-portal/me') || url.includes('/student-portal/change-requests');
  }

  private t(key: string, fallback: string): string {
    const translated = this.translationService.translate(key);
    return translated && translated !== key ? translated : fallback;
  }
}
