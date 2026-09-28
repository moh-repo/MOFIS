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
