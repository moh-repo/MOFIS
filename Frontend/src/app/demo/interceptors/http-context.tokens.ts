import { HttpContext, HttpContextToken } from '@angular/common/http';

/**
 * Marks a request whose failure is an expected outcome the caller handles itself, so the
 * global ErrorInterceptor stays quiet instead of showing a toast.
 *
 * Use this only where an error status is part of normal operation — for example probing
 * for an optional resource that may legitimately not exist. Authentication handling (401)
 * still runs regardless.
 */
export const SUPPRESS_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/** Convenience factory for the common `{ context: silentErrors() }` request option. */
export function silentErrors(): HttpContext {
  return new HttpContext().set(SUPPRESS_ERROR_TOAST, true);
}

/**
 * Marks a request that must not trigger the AuthInterceptor's 401 recovery — neither the
 * silent token refresh nor the sign-out-and-redirect that follows a failed one. The caller
 * takes responsibility for the 401 itself.
 *
 * Set on exactly one request today: the `/me` that `UmsAuthService.restoreSession` makes
 * from an `APP_INITIALIZER`. Recovery ends in `AuthService.logout()`, which navigates, and
 * the router has not bootstrapped yet at that point — the navigation is lost and the app
 * finishes starting on a shell it has already decided to abandon.
 */
export const SKIP_AUTH_RECOVERY = new HttpContextToken<boolean>(() => false);

/**
 * FE-01: requests whose 401 means "the password or code you typed is wrong", not "your
 * session is over" — sign-in and every second-factor step (`/ums/auth/mfa/verify`,
 * `enrol/*`, `disable`, `recovery-codes`; the backend's MfaService answers a refused
 * code with 401). The form that sent one shows its 401; neither interceptor refreshes
 * the token or signs the user out over it. A session that really has expired is
 * caught by the next ordinary request.
 */
export function isCredentialCheck(url: string): boolean {
  return url.includes('/ums/auth/login') || url.includes('/ums/auth/mfa/');
}
