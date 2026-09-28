import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { UmsAuthService } from './ums-auth.service';
import { LoginResponse, UmsUser } from '../api/ums';

describe('UmsAuthService', () => {
  let service: UmsAuthService;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule]
    });
    service = TestBed.inject(UmsAuthService);
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should store and retrieve token from umsToken key', () => {
    service.setTokenOnly('test-jwt-token');
    expect(service.getToken()).toBe('test-jwt-token');
    expect(localStorage.getItem('umsToken')).toBe('test-jwt-token');
  });

  it('clearAuthState removes umsToken and umsCurrentUser', () => {
    localStorage.setItem('umsToken', 'abc');
    localStorage.setItem('umsCurrentUser', '{}');
    service.clearAuthState();
    expect(localStorage.getItem('umsToken')).toBeNull();
    expect(localStorage.getItem('umsCurrentUser')).toBeNull();
  });

  /**
   * The login reply carries the whole account record, and none of it is kept:
   * browser storage holds the token and nothing else. These tests exist so that
   * a future convenience cache cannot quietly put the account — or its permission
   * map — back on disk.
   */
  describe('what a sign-in leaves in browser storage', () => {
    /** Everything the server sends about the account, including what must not be kept. */
    const fullUser: UmsUser = {
      id: 1,
      userName: 'superadmin',
      email: 'superadmin@mohe.edu.ye',
      phone: '+967000000',
      firstName: 'Super',
      lastName: 'Admin',
      status: 'ACTIVE',
      isAdmin: true,
      isSuperAdmin: true,
      isMinistryUser: true,
      failedLoginAttempts: 3,
      lastLoginAt: '2026-09-07T10:00:00Z',
      passwordChangedAt: '2026-01-01T00:00:00Z',
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2026-09-07T10:00:00Z',
      countryNames: ['Egypt'],
      positionCode: 'DIRECTOR'
    };

    const loginResponse: LoginResponse = {
      token: 'a.b.c',
      user: fullUser,
      permissions: ['REP_STATISTICAL.VIEW'],
      permissionMap: { AUDIT: ['VIEW'] },
      countryIds: [1, 999]
    } as LoginResponse;

    function signIn(): void {
      const http = TestBed.inject(HttpTestingController);
      service.login('superadmin', 'pw', 'cap-id', '1234').subscribe();
      http.expectOne(req => req.url.endsWith('/ums/auth/login')).flush(loginResponse);
      http.verify();
    }

    /** Every key and value in both stores, so a check cannot miss a new key name. */
    function storageDump(): string {
      const entries: string[] = [];
      for (const store of [localStorage, sessionStorage]) {
        for (let i = 0; i < store.length; i++) {
          const key = store.key(i) as string;
          entries.push(`${key}=${store.getItem(key)}`);
        }
      }
      return entries.join('\n');
    }

    it('keeps the token and nothing else', () => {
      signIn();
      expect(localStorage.getItem('umsToken')).toBe('a.b.c');
      for (const key of ['umsCurrentUser', 'umsPermissions', 'umsPermissionMap',
                         'umsCountryIds', 'umsIsMinistryUser', 'umsIsMinistryLevel',
                         'umsAdminTier', 'currentUser']) {
        expect(localStorage.getItem(key)).withContext(`${key} must not be written`).toBeNull();
      }
    });

    it('does not leave the account or its permissions anywhere in storage', () => {
      signIn();
      const dump = storageDump();
      for (const secret of ['superadmin@mohe.edu.ye', '+967000000', 'REP_STATISTICAL',
                            'AUDIT', 'DIRECTOR', 'Admin']) {
        expect(dump).withContext(`${secret} must not reach browser storage`).not.toContain(secret);
      }
    });

    it('still exposes the identity the application needs', () => {
      signIn();
      expect(service.currentUserValue?.id).toBe(1);
      expect(service.isSuperAdmin()).toBeTrue();
      expect(service.isMinistryUser).toBeTrue();
      expect(service.permissions).toEqual(['REP_STATISTICAL.VIEW']);
      expect(service.countryIds).toEqual([1, 999]);
    });

    /** A fresh instance is what a page reload produces. */
    function reload(): UmsAuthService {
      return new UmsAuthService(
        TestBed.inject(HttpClient),
        { navigate: () => {} } as never,
        { setSelectedCountryId: () => {} } as never
      );
    }

    it('starts a reload knowing nothing but the token', () => {
      signIn();
      const reloaded = reload();
      expect(reloaded.getToken()).toBe('a.b.c');
      expect(reloaded.currentUserValue).toBeNull();
      expect(reloaded.permissions).toEqual([]);
    });

    it('restores the same identity from /me', async () => {
      signIn();
      const http = TestBed.inject(HttpTestingController);
      const reloaded = reload();

      const restored = reloaded.restoreSession();
      http.expectOne(req => req.url.endsWith('/ums/auth/me')).flush(loginResponse);
      await restored;

      expect(reloaded.currentUserValue?.id).toBe(1);
      expect(reloaded.currentUserValue?.isSuperAdmin).toBeTrue();
      expect(reloaded.permissions).toEqual(['REP_STATISTICAL.VIEW']);
      expect(reloaded.countryIds).toEqual([1, 999]);
      http.verify();
    });

    it('makes no request when there is no token to restore', async () => {
      const http = TestBed.inject(HttpTestingController);
      await reload().restoreSession();
      http.verify();
    });

    it('drops a token the server has rejected', async () => {
      signIn();
      const http = TestBed.inject(HttpTestingController);
      const reloaded = reload();

      const restored = reloaded.restoreSession();
      http.expectOne(req => req.url.endsWith('/ums/auth/me'))
        .flush({}, { status: 401, statusText: 'Unauthorized' });
      await restored;

      expect(reloaded.getToken()).withContext('an expired token is not worth keeping').toBeNull();
      expect(reloaded.currentUserValue).toBeNull();
    });

    it('keeps the token when the server could not answer', async () => {
      signIn();
      const http = TestBed.inject(HttpTestingController);
      const reloaded = reload();

      const restored = reloaded.restoreSession();
      http.expectOne(req => req.url.endsWith('/ums/auth/me'))
        .flush({}, { status: 500, statusText: 'Server Error' });
      await restored;

      expect(reloaded.getToken())
        .withContext('an outage must not sign everybody out')
        .toBe('a.b.c');
      expect(reloaded.currentUserValue).toBeNull();
    });
  });

  describe('signing out', () => {
    const AUTH_KEYS = ['umsToken', 'umsCurrentUser', 'umsPermissions', 'umsPermissionMap',
                       'umsCountryIds', 'umsIsMinistryUser', 'umsIsMinistryLevel', 'umsAdminTier'];

    it('clears every auth key from both stores', () => {
      for (const key of AUTH_KEYS) {
        localStorage.setItem(key, 'x');
        sessionStorage.setItem(key, 'x');
      }

      service.clearAuthState();

      for (const key of AUTH_KEYS) {
        expect(localStorage.getItem(key)).withContext(`localStorage.${key}`).toBeNull();
        expect(sessionStorage.getItem(key)).withContext(`sessionStorage.${key}`).toBeNull();
      }
    });

    it('leaves nothing behind in memory either', () => {
      service.clearAuthState();
      expect(service.currentUserValue).toBeNull();
      expect(service.permissions).toEqual([]);
      expect(service.countryIds).toEqual([]);
      expect(service.isMinistryUser).toBeFalse();
      expect(service.adminTier).toBeNull();
      expect(service.isLoggedIn()).toBeFalse();
    });
  });

  /**
   * `TOKEN_IN_SESSION_STORAGE` decides which store holds the token. Whichever way
   * it is set, a token left in the other store by a previous build is moved rather
   * than stranded — a stranded one is a working credential in the store the
   * configuration just decided should not hold one.
   */
  it('migrates a token left in the store the current build does not use', () => {
    sessionStorage.setItem('umsToken', 'from-a-previous-build');

    const migrated = new UmsAuthService(
      TestBed.inject(HttpTestingController) as never,
      { navigate: () => {} } as never,
      { setSelectedCountryId: () => {} } as never
    );

    expect(migrated.getToken()).toBe('from-a-previous-build');
    expect(sessionStorage.getItem('umsToken'))
      .withContext('the abandoned store must not keep a usable token')
      .toBeNull();
  });

  /**
   * A browser that signed in under an older build is carrying that build's copy
   * of the account and its permission map. Loading this one has to clear them —
   * waiting for the next sign-out would leave them in place for the life of the
   * session, which is exactly the window that matters.
   */
  it('purges what an older build persisted, without disturbing the token', () => {
    const stale = {
      umsCurrentUser: '{"id":1,"email":"superadmin@mohe.edu.ye"}',
      umsPermissions: '["REP_STATISTICAL.VIEW"]',
      umsPermissionMap: '{"AUDIT":["VIEW"]}',
      umsCountryIds: '[1,999]',
      umsIsMinistryUser: 'true',
      umsIsMinistryLevel: 'true',
      umsAdminTier: 'SUPER_ADMIN',
      currentUser: '{"id":1,"userName":"superadmin"}',
      umsUser: '{"id":1}'
    };
    for (const [key, value] of Object.entries(stale)) {
      localStorage.setItem(key, value);
      sessionStorage.setItem(key, value);
    }
    localStorage.setItem('umsToken', 'still-valid');

    const booted = new UmsAuthService(
      TestBed.inject(HttpClient),
      { navigate: () => {} } as never,
      { setSelectedCountryId: () => {} } as never
    );

    for (const key of Object.keys(stale)) {
      expect(localStorage.getItem(key)).withContext(`localStorage.${key}`).toBeNull();
      expect(sessionStorage.getItem(key)).withContext(`sessionStorage.${key}`).toBeNull();
    }
    expect(booted.getToken())
      .withContext('the session itself is still good; only the cache is discarded')
      .toBe('still-valid');
    expect(booted.currentUserValue).toBeNull();
    expect(booted.permissions).toEqual([]);
  });
});
