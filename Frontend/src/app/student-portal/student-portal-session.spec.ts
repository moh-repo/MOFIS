import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { StudentPortalApiService } from './student-portal-api.service';
import { UmsAuthService } from '../demo/service/ums-auth.service';

/**
 * The portal's half of the browser-storage pass, and the line between it and the
 * ministry session.
 *
 * <p>Two people sign in to this application: a student, with a portal token, and
 * ministry staff, with a UMS bearer JWT. They can share a browser — a student
 * using a terminal in an attaché office is the ordinary case — and neither side
 * may read, refresh, outlive or clear the other's session. These tests fail if a
 * future change makes the two sides share a key, a purge list, or a bootstrap.
 */
describe('student portal session', () => {

  const PORTAL_KEY = 'mohe.portal.token';
  const LEGACY_PORTAL_KEY = 'studentPortalToken';

  let api: StudentPortalApiService;
  let http: HttpTestingController;

  function build(): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule]
    });
    api = TestBed.inject(StudentPortalApiService);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    build();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  describe('what it keeps in browser storage', () => {

    it('keeps the token under its own namespaced key and nothing else', () => {
      api.setToken('portal-token');

      expect(localStorage.getItem(PORTAL_KEY)).toBe('portal-token');
      expect(localStorage.length).withContext('the token is the whole of it').toBe(1);
    });

    it('carries a token stored under the old key name across the deploy', () => {
      localStorage.setItem(LEGACY_PORTAL_KEY, 'signed-in-before-the-rename');

      build();

      expect(api.getToken()).toBe('signed-in-before-the-rename');
      expect(localStorage.getItem(LEGACY_PORTAL_KEY))
        .withContext('the old name must not keep a usable token').toBeNull();
    });

    it('signs out of every key name the token has had', () => {
      localStorage.setItem(PORTAL_KEY, 'a');
      localStorage.setItem(LEGACY_PORTAL_KEY, 'b');
      sessionStorage.setItem(PORTAL_KEY, 'c');

      api.clearToken();

      expect(localStorage.getItem(PORTAL_KEY)).toBeNull();
      expect(localStorage.getItem(LEGACY_PORTAL_KEY)).toBeNull();
      expect(sessionStorage.getItem(PORTAL_KEY)).toBeNull();
      expect(api.isLoggedIn()).toBeFalse();
    });

    /**
     * The guard calls `getToken` before any page opens. Storage that throws on
     * access there is a portal that will not open at all, rather than one that
     * asks for a sign-in — and a student on a locked-down phone browser is
     * exactly the case that has to degrade to the sign-in page.
     */
    it('reads as signed out when storage is unavailable rather than throwing', () => {
      const blocked = spyOn(localStorage, 'getItem').and.throwError('SecurityError');

      expect(() => api.getToken()).not.toThrow();
      expect(api.getToken()).toBeNull();
      expect(api.isLoggedIn()).toBeFalse();

      blocked.and.callThrough();
    });
  });

  describe('restoring a session on a page load', () => {

    /** A page load with a token already in storage, as a returning student produces. */
    function reloadWithToken(): void {
      localStorage.setItem(PORTAL_KEY, 'stored-token');
      build();
    }

    function expectProfileCall() {
      return http.expectOne(req => req.url.endsWith('/student-portal/me'));
    }

    it('asks the server whether the stored token is still a session', () => {
      reloadWithToken();
      let valid: boolean | undefined;

      api.restoreSession().subscribe(v => (valid = v));
      const call = expectProfileCall();
      expect(call.request.headers.get('X-Student-Portal-Token')).toBe('stored-token');
      call.flush({ studentId: 4321, studentName: 'Layla Al Mansouri' });

      expect(valid).toBeTrue();
      expect(api.currentStudent?.studentId)
        .withContext('the shell reads the student from memory instead of asking again').toBe(4321);
      http.verify();
    });

    it('drops a token the server refuses', () => {
      reloadWithToken();
      let valid: boolean | undefined;

      api.restoreSession().subscribe(v => (valid = v));
      expectProfileCall().flush({}, { status: 401, statusText: 'Unauthorized' });

      expect(valid).toBeFalse();
      expect(api.getToken()).toBeNull();
      expect(api.currentStudent).toBeNull();
    });

    // SEC-001: a session held to the password change is alive; only the change opens.
    it('keeps a session the server holds to the password change', () => {
      reloadWithToken();
      let valid: boolean | undefined;

      api.restoreSession().subscribe(v => (valid = v));
      expectProfileCall().flush({ code: 'PORTAL_PASSWORD_CHANGE_REQUIRED' }, { status: 403, statusText: 'Forbidden' });

      expect(valid).toBeTrue();
      expect(api.getToken()).toBe('stored-token');
    });

    it('keeps the session when the server could not answer at all', () => {
      reloadWithToken();
      let valid: boolean | undefined;

      api.restoreSession().subscribe(v => (valid = v));
      expectProfileCall().flush({}, { status: 500, statusText: 'Server Error' });

      expect(valid).withContext('an outage does not sign a student out').toBeTrue();
      expect(api.getToken()).toBe('stored-token');
    });

    it('checks once per page load, not once per navigation', () => {
      reloadWithToken();

      api.restoreSession().subscribe();
      expectProfileCall().flush({ studentId: 1, studentName: 'A' });

      let second: boolean | undefined;
      api.restoreSession().subscribe(v => (second = v));

      expect(second).toBeTrue();
      http.verify();
    });

    it('asks nothing when there is no token', () => {
      let valid: boolean | undefined;

      api.restoreSession().subscribe(v => (valid = v));

      expect(valid).toBeFalse();
      http.verify();
    });

    it('treats a fresh sign-in as already checked', () => {
      api.setToken('just-signed-in');

      api.restoreSession().subscribe();

      http.verify();
    });
  });

  /**
   * Neither sign-in may disturb the other. The two run different authentications
   * against different tables, and the only place they ever met — the interceptor
   * attaching the staff JWT to portal requests — is closed.
   */
  describe('separation from the ministry session', () => {

    let ums: UmsAuthService;

    beforeEach(() => {
      ums = TestBed.inject(UmsAuthService);
    });

    it('leaves the staff session alone when a student signs out', () => {
      localStorage.setItem('umsToken', 'staff-jwt');
      api.setToken('portal-token');

      api.clearToken();

      expect(localStorage.getItem('umsToken'))
        .withContext('a student signing out must not sign staff out').toBe('staff-jwt');
    });

    it('leaves the student session alone when staff sign out', () => {
      api.setToken('portal-token');
      localStorage.setItem('umsToken', 'staff-jwt');

      ums.clearAuthState();

      expect(api.getToken())
        .withContext('staff signing out must not sign a student out').toBe('portal-token');
      expect(localStorage.getItem('umsToken')).toBeNull();
    });

    it('does not restore a student session from a staff token, or the reverse', () => {
      localStorage.setItem('umsToken', 'staff-jwt');
      build();

      expect(api.isLoggedIn())
        .withContext('a staff token is not a portal session').toBeFalse();

      localStorage.clear();
      localStorage.setItem(PORTAL_KEY, 'portal-token');

      expect(TestBed.inject(UmsAuthService).getToken())
        .withContext('a portal token is not a staff session').toBeNull();
    });

    it('keeps the two tokens under names that cannot collide', () => {
      const umsKeys = ['umsToken', 'umsCurrentUser', 'umsPermissions', 'umsPermissionMap',
                       'umsCountryIds', 'umsIsMinistryUser', 'umsIsMinistryLevel', 'umsAdminTier',
                       'currentUser', 'umsUser'];

      for (const key of [PORTAL_KEY, LEGACY_PORTAL_KEY]) {
        expect(umsKeys).withContext(`${key} is also a ministry key`).not.toContain(key);
      }
    });
  });
});
