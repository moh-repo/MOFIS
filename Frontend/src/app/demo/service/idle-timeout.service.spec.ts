import { TestBed, fakeAsync, tick, discardPeriodicTasks, flushMicrotasks } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { IdleTimeoutService } from './idle-timeout.service';
import { UmsAuthService } from './ums-auth.service';
import { AuthService } from './auth.service';
import { TranslationService } from './translation.service';

const WARN_AFTER_MS = 25 * 60 * 1000;
const LOGOUT_AFTER_MS = 30 * 60 * 1000;

describe('IdleTimeoutService', () => {
  let service: IdleTimeoutService;
  let messageService: jasmine.SpyObj<MessageService>;
  let router: jasmine.SpyObj<Router>;
  let umsAuthService: jasmine.SpyObj<UmsAuthService>;
  let authService: jasmine.SpyObj<AuthService>;
  let translationService: jasmine.SpyObj<TranslationService>;
  let fetchSpy: jasmine.Spy;
  /** What the token store holds; clearing the session empties it, as the real one does. */
  let storedToken: string | null;

  beforeEach(() => {
    messageService = jasmine.createSpyObj('MessageService', ['add', 'clear']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    umsAuthService = jasmine.createSpyObj('UmsAuthService', ['getToken']);
    authService = jasmine.createSpyObj('AuthService', ['logout', 'clearSession']);
    translationService = jasmine.createSpyObj('TranslationService', ['translate']);
    // Untranslated by default, so the English fallbacks are what get asserted.
    translationService.translate.and.callFake((key: string) => key);
    storedToken = null;
    umsAuthService.getToken.and.callFake(() => storedToken);
    authService.clearSession.and.callFake(() => { storedToken = null; });
    fetchSpy = spyOn(window, 'fetch').and.resolveTo(new Response(null, { status: 200 }));

    TestBed.configureTestingModule({
      providers: [
        IdleTimeoutService,
        { provide: MessageService, useValue: messageService },
        { provide: Router, useValue: router },
        { provide: UmsAuthService, useValue: umsAuthService },
        { provide: AuthService, useValue: authService },
        { provide: TranslationService, useValue: translationService }
      ]
    });
    service = TestBed.inject(IdleTimeoutService);
  });

  afterEach(() => {
    service.stop();
  });

  // The warning is keyed so that only the dedicated <p-toast key="idle-warn"> in
  // app.component.html renders it; without that key on the message (and that
  // outlet in the template) the user gets no notice before the 30-minute logout.
  it('publishes the 25-minute warning on the idle-warn toast key', fakeAsync(() => {
    service.start();
    tick(WARN_AFTER_MS);

    expect(messageService.add).toHaveBeenCalledWith(jasmine.objectContaining({
      key: 'idle-warn',
      severity: 'warn',
      summary: 'Session expiring'
    }));
    expect(IdleTimeoutService.WARN_TOAST_KEY).toBe('idle-warn');
    discardPeriodicTasks();
  }));

  // Live QA R5-10: the warning went with `life: 0`, and PrimeNG closes any toast that
  // is not sticky after `life || 3000` ms, so it was gone within seconds.
  it('keeps the warning on screen: sticky, with no life that would close it', fakeAsync(() => {
    service.start();
    tick(WARN_AFTER_MS);

    const warning = messageService.add.calls.mostRecent().args[0];
    expect(warning.sticky).toBeTrue();
    expect(warning.life).toBeUndefined();
    expect(messageService.clear).not.toHaveBeenCalled();
    discardPeriodicTasks();
  }));

  it('clears the warning when the user is active again, and warns again after a new idle spell', fakeAsync(() => {
    const zone = TestBed.inject(NgZone);
    service.start();
    tick(WARN_AFTER_MS);
    expect(messageService.add).toHaveBeenCalledTimes(1);

    zone.run(() => document.dispatchEvent(new Event('mousemove')));
    tick(500); // activity debounce

    expect(messageService.clear).toHaveBeenCalledOnceWith('idle-warn');
    expect(router.navigate).not.toHaveBeenCalled();

    tick(WARN_AFTER_MS);
    expect(messageService.add).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  it('leaves the toasts alone on activity while no warning is showing', fakeAsync(() => {
    const zone = TestBed.inject(NgZone);
    service.start();
    tick(60_000);
    zone.run(() => document.dispatchEvent(new Event('keydown')));
    tick(500);

    expect(messageService.clear).not.toHaveBeenCalled();
    discardPeriodicTasks();
  }));

  it('clears a warning still on screen when stopped (manual sign-out)', fakeAsync(() => {
    service.start();
    tick(WARN_AFTER_MS);
    service.stop();

    expect(messageService.clear).toHaveBeenCalledOnceWith('idle-warn');
    discardPeriodicTasks();
  }));

  it('uses the translated strings when the dictionary has them', fakeAsync(() => {
    translationService.translate.and.callFake((key: string) =>
      key === 'COMMON.IDLE.WARN_TITLE' ? 'الجلسة على وشك الانتهاء' : key);
    service.start();
    tick(WARN_AFTER_MS);

    expect(messageService.add).toHaveBeenCalledWith(jasmine.objectContaining({
      key: 'idle-warn',
      summary: 'الجلسة على وشك الانتهاء'
    }));
    discardPeriodicTasks();
  }));

  it('clears the warning, signs out and goes to login at 30 minutes', fakeAsync(() => {
    service.start();
    tick(LOGOUT_AFTER_MS);

    expect(messageService.clear).toHaveBeenCalledWith('idle-warn');
    expect(authService.clearSession).toHaveBeenCalled();
    // logout() navigates to the login page itself, without the reason.
    expect(authService.logout).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledOnceWith(['/auth/login'], { queryParams: { reason: 'idle_timeout' } });
    discardPeriodicTasks();
  }));

  // Live QA R5-03: the idle sign-out cleared the stored user before calling
  // AuthService.logout(), which then had no user id and sent no logout, so the
  // old token still answered 200 on the server.
  it('sends POST /ums/auth/logout with the stored token before clearing it', fakeAsync(() => {
    storedToken = 'idle-token';
    let tokenAtFetch: string | null = 'unset';
    fetchSpy.and.callFake(() => {
      tokenAtFetch = storedToken;
      return Promise.resolve(new Response(null, { status: 200 }));
    });

    service.start();
    tick(LOGOUT_AFTER_MS);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.calls.mostRecent().args as [string, RequestInit];
    expect(url).toMatch(/\/mohe\/api\/v1\/ums\/auth\/logout$/);
    expect(init.method).toBe('POST');
    expect(init.keepalive).toBeTrue();
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer idle-token');
    expect(tokenAtFetch).toBe('idle-token');
    expect(storedToken).toBeNull();
    expect(router.navigate).toHaveBeenCalledOnceWith(['/auth/login'], { queryParams: { reason: 'idle_timeout' } });
    discardPeriodicTasks();
  }));

  const failures: Array<[string, () => Promise<Response>]> = [
    ['a network error', () => Promise.reject(new TypeError('Failed to fetch'))],
    ['a 401', () => Promise.resolve(new Response(null, { status: 401 }))],
    ['a fetch that throws', () => { throw new TypeError('fetch is not available'); }]
  ];
  for (const [label, outcome] of failures) {
    it(`still clears the session and goes to login with the idle reason after ${label}`, fakeAsync(() => {
      storedToken = 'idle-token';
      fetchSpy.and.callFake(outcome);

      service.start();
      tick(LOGOUT_AFTER_MS);
      flushMicrotasks();

      expect(fetchSpy).toHaveBeenCalledTimes(1);
      expect(storedToken).toBeNull();
      expect(router.navigate).toHaveBeenCalledOnceWith(['/auth/login'], { queryParams: { reason: 'idle_timeout' } });
      discardPeriodicTasks();
    }));
  }

  it('sends no logout when the token is already gone', fakeAsync(() => {
    service.start();
    tick(LOGOUT_AFTER_MS);

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledOnceWith(['/auth/login'], { queryParams: { reason: 'idle_timeout' } });
    discardPeriodicTasks();
  }));

  it('restarts the timers on activity', fakeAsync(() => {
    const zone = TestBed.inject(NgZone);
    service.start();
    tick(WARN_AFTER_MS - 60_000);
    zone.run(() => document.dispatchEvent(new Event('keydown')));
    tick(500); // activity debounce
    tick(60_000);

    expect(messageService.add).not.toHaveBeenCalled();
    discardPeriodicTasks();
  }));
});
