import { environment } from '../../../environments/environment';
import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, fromEvent, merge, Subscription, timer } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { UmsAuthService } from './ums-auth.service';
import { AuthService } from './auth.service';
import { TranslationService } from './translation.service';

// O-27 (security plan): the durations come from the build environment so an
// operator can lengthen them for the ministry's long-running report screens
// without touching this file. Defaults: warn at 25 minutes, sign out at 30.
const WARN_AFTER_MINUTES = (environment as { idleWarnMinutes?: number }).idleWarnMinutes ?? 25;
const LOGOUT_AFTER_MINUTES = (environment as { idleLogoutMinutes?: number }).idleLogoutMinutes ?? 30;
const WARN_AFTER_MS  = WARN_AFTER_MINUTES * 60 * 1000;
const LOGOUT_AFTER_MS = LOGOUT_AFTER_MINUTES * 60 * 1000;
const ACTIVITY_EVENTS = ['mousemove', 'keydown', 'mousedown', 'touchstart', 'scroll', 'click'];

/**
 * Watches for user inactivity.
 * After 25 min of no activity → warning toast.
 * After 30 min → automatic logout with redirect to login.
 *
 * Call start() on login, stop() on logout.
 */
@Injectable({ providedIn: 'root' })
export class IdleTimeoutService implements OnDestroy {

  /** Toast outlet key; matched by the keyed <p-toast> in app.component.html. */
  static readonly WARN_TOAST_KEY = 'idle-warn';

  private destroy$ = new Subject<void>();
  private warnTimer?: Subscription;
  private logoutTimer?: Subscription;
  private activitySub?: Subscription;
  private running = false;
  private warningShown = false;

  constructor(
    private zone: NgZone,
    private router: Router,
    private messageService: MessageService,
    private umsAuthService: UmsAuthService,
    private authService: AuthService,
    private translationService: TranslationService
  ) {}

  start(): void {
    if (this.running) return;
    this.running = true;
    this.scheduleTimers();

    // Run activity detection outside Angular zone to avoid triggering CD on every mouse move
    this.zone.runOutsideAngular(() => {
      const activity$ = merge(...ACTIVITY_EVENTS.map(e => fromEvent(document, e)));
      this.activitySub = activity$.pipe(
        debounceTime(500),
        takeUntil(this.destroy$)
      ).subscribe(() => {
        this.zone.run(() => this.resetTimers());
      });
    });
  }

  stop(): void {
    this.running = false;
    this.cancelTimers();
    this.clearWarning();
    this.activitySub?.unsubscribe();
  }

  private resetTimers(): void {
    this.cancelTimers();
    this.clearWarning();
    this.scheduleTimers();
  }

  private scheduleTimers(): void {
    this.warnTimer = timer(WARN_AFTER_MS).subscribe(() => this.showWarning());
    this.logoutTimer = timer(LOGOUT_AFTER_MS).subscribe(() => this.forceLogout());
  }

  private cancelTimers(): void {
    this.warnTimer?.unsubscribe();
    this.logoutTimer?.unsubscribe();
  }

  /**
   * Rendered by the keyed <p-toast key="idle-warn"> in app.component.html — a keyed
   * message is dropped by every unkeyed toast, so that outlet must exist.
   *
   * `sticky`, not `life: 0` (live QA R5-10): PrimeNG closes a toast after
   * `life || 3000` ms unless it is sticky, so a 0 closed the warning within seconds,
   * before a user away from the screen could see it. It stays until activity
   * (resetTimers) or the sign-out (stop) clears it.
   */
  private showWarning(): void {
    this.messageService.add({
      severity: 'warn',
      summary: this.t('COMMON.IDLE.WARN_TITLE', 'Session expiring'),
      detail: this.t('COMMON.IDLE.WARN_DETAIL', 'You will be logged out in {minutes} minutes due to inactivity.')
        .replace('{minutes}', String(Math.max(1, LOGOUT_AFTER_MINUTES - WARN_AFTER_MINUTES))),
      sticky: true,
      key: IdleTimeoutService.WARN_TOAST_KEY
    });
    this.warningShown = true;
  }

  /** Only when the warning is on screen, so ordinary activity does not touch the toasts. */
  private clearWarning(): void {
    if (!this.warningShown) return;
    this.warningShown = false;
    this.messageService.clear(IdleTimeoutService.WARN_TOAST_KEY);
  }

  /**
   * Ends the server session first, while the token is still stored (live QA R5-03).
   * This used to clear the UMS state and then call `AuthService.logout()`, which by
   * then had no user id and never sent the logout, so the old token kept answering
   * 200. `clearSession()` rather than `logout()`: `logout()` also navigates to the
   * login page, without the reason the navigation below carries.
   */
  private forceLogout(): void {
    this.stop(); // also clears the warning
    this.endServerSession();
    this.authService.clearSession();
    this.messageService.add({
      severity: 'info',
      summary: this.t('COMMON.IDLE.EXPIRED_TITLE', 'Session expired'),
      detail: this.t('COMMON.IDLE.EXPIRED_DETAIL', 'You have been logged out due to inactivity.'),
      life: 5000
    });
    this.router.navigate(['/auth/login'], { queryParams: { reason: 'idle_timeout' } });
  }

  /**
   * The same POST /ums/auth/logout the profile menu's Logout sends, with the stored
   * token, and sent the way the Access Denied page sends it since M4: a plain `fetch`
   * with `keepalive`, outside the HTTP interceptors. Through HttpClient, a 401 (a token
   * that expired during the idle half-hour) would reach their sign-out handling, which
   * navigates to the login page again without `reason=idle_timeout`. A failure is
   * ignored: the browser side is cleared either way.
   */
  private endServerSession(): void {
    const token = this.umsAuthService.getToken();
    if (!token) return;
    try {
      fetch(`${environment.apiUrl}/mohe/api/v1/ums/auth/logout`, {
        method: 'POST',
        keepalive: true,
        headers: { Authorization: `Bearer ${token}` }
      }).catch(() => undefined);
    } catch { /* no fetch: the browser state is still cleared */ }
  }

  /** Falls back to English when the dictionaries have not loaded (same as ErrorInterceptor). */
  private t(key: string, fallback: string): string {
    const translated = this.translationService.translate(key);
    return translated && translated !== key ? translated : fallback;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.stop();
  }
}
