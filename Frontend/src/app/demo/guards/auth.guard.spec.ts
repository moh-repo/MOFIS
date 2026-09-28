import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthGuard, jwtExpiry } from './auth.guard';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';

/** An unsigned JWT-shaped token whose payload carries the given claims. */
function fakeJwt(claims: object): string {
  const b64url = (s: string) => btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64url('{"alg":"none"}')}.${b64url(JSON.stringify(claims))}.sig`;
}

const NOW_SECONDS = Math.floor(Date.now() / 1000);
const USER = { id: 1, userName: 'test', lastName: 'User', role: { roleName: 'ADMIN' } };

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let authService: jasmine.SpyObj<AuthService>;
  let umsAuthService: jasmine.SpyObj<UmsAuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['clearSession'], {
      currentUserValue: null
    });
    const umsAuthServiceSpy = jasmine.createSpyObj('UmsAuthService', ['getToken', 'clearAuthState']);
    umsAuthServiceSpy.getToken.and.returnValue(fakeJwt({ exp: NOW_SECONDS + 3600 }));
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        AuthGuard,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: UmsAuthService, useValue: umsAuthServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    guard = TestBed.inject(AuthGuard);
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    umsAuthService = TestBed.inject(UmsAuthService) as jasmine.SpyObj<UmsAuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  function setUser(user: object | null): void {
    Object.defineProperty(authService, 'currentUserValue', { value: user, configurable: true });
  }

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should allow access when user is logged in with a live token', () => {
    setUser(USER);
    const route = { data: {} } as any;
    const state = { url: '/dashboard' } as any;
    expect(guard.canActivate(route, state)).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(authService.clearSession).not.toHaveBeenCalled();
  });

  it('should redirect to login when user is not logged in', () => {
    setUser(null);
    const route = { data: {} } as any;
    const state = { url: '/dashboard' } as any;
    expect(guard.canActivate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: { returnUrl: '/dashboard' } });
  });

  it('should redirect to access denied when user role is not allowed', () => {
    setUser({ ...USER, role: { roleName: 'USER' } });
    const route = { data: { roles: ['ADMIN'] } } as any;
    const state = { url: '/admin' } as any;
    expect(guard.canActivate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);
  });

  // A profile can outlive its token (cleared by the interceptor, or simply never
  // stored). The presence of currentUser alone used to be enough, and the shell
  // then loaded only for every call to 401.
  it('should clear the stale profile and redirect when there is no token', () => {
    setUser(USER);
    umsAuthService.getToken.and.returnValue(null);
    const route = { data: {} } as any;
    const state = { url: '/students' } as any;
    expect(guard.canActivate(route, state)).toBe(false);
    expect(authService.clearSession).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: { returnUrl: '/students' } });
  });

  it('should clear the session and redirect when the JWT has expired', () => {
    setUser(USER);
    umsAuthService.getToken.and.returnValue(fakeJwt({ exp: NOW_SECONDS - 60 }));
    const route = { data: {} } as any;
    const state = { url: '/students' } as any;
    expect(guard.canActivate(route, state)).toBe(false);
    expect(authService.clearSession).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: { returnUrl: '/students' } });
  });

  it('should accept an opaque (non-JWT) token on presence alone', () => {
    setUser(USER);
    umsAuthService.getToken.and.returnValue('opaque-session-token');
    const route = { data: {} } as any;
    const state = { url: '/dashboard' } as any;
    expect(guard.canActivate(route, state)).toBe(true);
  });

  describe('jwtExpiry', () => {
    it('reads exp from a JWT payload', () => {
      expect(jwtExpiry(fakeJwt({ exp: 1234567890 }))).toBe(1234567890);
    });

    it('returns null for a JWT without exp, a non-JWT, or a broken payload', () => {
      expect(jwtExpiry(fakeJwt({ sub: 'x' }))).toBeNull();
      expect(jwtExpiry('not-a-jwt')).toBeNull();
      expect(jwtExpiry('a.%%%.c')).toBeNull();
    });
  });
});
