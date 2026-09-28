import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { UmsPermissionGuard } from './ums-permission.guard';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { CodeListService } from '../service/code-list.service';
import { MenuVariantService } from '../../layout/menu/menu-variant.service';
import { of } from 'rxjs';

describe('UmsPermissionGuard', () => {
  let guard: UmsPermissionGuard;
  let authService: jasmine.SpyObj<AuthService>;
  let legacyUser: { id: number; userName: string; role: { roleName: string } };
  let umsAuthService: jasmine.SpyObj<UmsAuthService>;
  let permissionService: jasmine.SpyObj<UmsPermissionService>;
  let router: jasmine.SpyObj<Router>;
  let permissionsSubject: BehaviorSubject<string[]>;

  /** canActivate returns an Observable; unwrap it for the assertions below. */
  function activate(route: any, state: any): boolean {
    let result: boolean | undefined;
    guard.canActivate(route, state).subscribe(value => (result = value));
    return result as boolean;
  }

  beforeEach(() => {
    legacyUser = { id: 1, userName: 'user', role: { roleName: 'USER' } };
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['isLoggedIn']);
    authServiceSpy.isLoggedIn.and.returnValue(true);
    Object.defineProperty(authServiceSpy, 'currentUserValue', {
      get: () => legacyUser,
      configurable: true
    });

    permissionsSubject = new BehaviorSubject<string[]>(['STUDENTS.VIEW']);
    const umsAuthServiceSpy = jasmine.createSpyObj('UmsAuthService', ['isLoggedIn', 'isSuperAdmin', 'isAdmin', 'isMinistryAdminOrAbove']);
    umsAuthServiceSpy.isLoggedIn.and.returnValue(false);
    umsAuthServiceSpy.isSuperAdmin.and.returnValue(false);
    umsAuthServiceSpy.isAdmin.and.returnValue(false);
    umsAuthServiceSpy.isMinistryAdminOrAbove.and.returnValue(false);
    umsAuthServiceSpy.permissions$ = permissionsSubject.asObservable();
    Object.defineProperty(umsAuthServiceSpy, 'permissions', {
      get: () => permissionsSubject.value,
      configurable: true
    });

    const permissionServiceSpy = jasmine.createSpyObj('UmsPermissionService', ['hasPermission', 'canAccessPage', 'canView']);
    permissionServiceSpy.hasPermission.and.returnValue(true);
    permissionServiceSpy.canAccessPage.and.returnValue(true);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        UmsPermissionGuard,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: UmsAuthService, useValue: umsAuthServiceSpy },
        { provide: UmsPermissionService, useValue: permissionServiceSpy },
        { provide: Router, useValue: routerSpy },
        // Warm: the guard's code-list wait is covered by its own test below.
        { provide: CodeListService, useValue: { isLoaded: true, ensureLoaded: () => of(true) } },
        { provide: MenuVariantService, useValue: { isProposed: true } }
      ]
    });

    guard = TestBed.inject(UmsPermissionGuard);
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    umsAuthService = TestBed.inject(UmsAuthService) as jasmine.SpyObj<UmsAuthService>;
    permissionService = TestBed.inject(UmsPermissionService) as jasmine.SpyObj<UmsPermissionService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  it('keeps the admin tool pages from a country admin and opens them to the ministry admin (DEC-15)', () => {
    const route = { data: { pageCode: 'UMS_ADMIN', ministryAdminOnly: true } } as any;
    const state = { url: '/ums-admin/api-links' } as any;
    umsAuthService.isLoggedIn.and.returnValue(true);

    expect(activate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);

    umsAuthService.isMinistryAdminOrAbove.and.returnValue(true);
    permissionService.canAccessPage.and.returnValue(true);
    expect(activate(route, state)).toBe(true);
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should redirect to login when user is not logged in', () => {
    authService.isLoggedIn.and.returnValue(false);
    const route = { data: { pageCode: 'STUDENTS' } } as any;
    const state = { url: '/students' } as any;
    expect(activate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('should allow access when no pageCode or permission required', () => {
    const route = { data: {} } as any;
    const state = { url: '/some-page' } as any;
    expect(activate(route, state)).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should allow access when UMS not logged in but legacy user is ADMIN', () => {
    legacyUser.role = { roleName: 'ADMIN' };
    const route = { data: { pageCode: 'STUDENTS' } } as any;
    const state = { url: '/students' } as any;
    expect(activate(route, state)).toBe(true);
  });

  it('should deny access when UMS not logged in and legacy user is not admin', () => {
    const route = { data: { pageCode: 'STUDENTS' } } as any;
    const state = { url: '/students' } as any;
    expect(activate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);
  });

  it('should deny access when pageCode required and user cannot access', () => {
    umsAuthService.isLoggedIn.and.returnValue(true);
    permissionService.canAccessPage.and.returnValue(false);
    const route = { data: { pageCode: 'UMS_ADMIN' } } as any;
    const state = { url: '/ums-admin' } as any;
    expect(activate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);
  });

  it('should deny access when specific permission required and user does not have it', () => {
    umsAuthService.isLoggedIn.and.returnValue(true);
    permissionService.hasPermission.and.returnValue(false);
    const route = { data: { permission: 'STUDENTS.ADD' } } as any;
    const state = { url: '/student-add' } as any;
    expect(activate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);
  });

  it('should allow a super admin through without waiting for the permission list', () => {
    umsAuthService.isLoggedIn.and.returnValue(true);
    umsAuthService.isSuperAdmin.and.returnValue(true);
    permissionsSubject.next([]);
    const route = { data: { pageCode: 'FINANCE', permission: 'FINANCE.EDIT' } } as any;
    const state = { url: '/finance/add' } as any;
    expect(activate(route, state)).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  // R-12 (6 Sep 2026): an administrator is no longer "everything". The server
  // sends the codes of the tier bundle, and the guard judges those like anyone's.
  it('should judge an admin by the permission list, not by the flag', () => {
    umsAuthService.isLoggedIn.and.returnValue(true);
    umsAuthService.isAdmin.and.returnValue(true);
    permissionService.hasPermission.and.returnValue(false);
    permissionService.canAccessPage.and.returnValue(false);
    const route = { data: { pageCode: 'FINANCE', permission: 'FINANCE.EDIT' } } as any;
    const state = { url: '/finance/add' } as any;
    expect(activate(route, state)).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);
  });

  // Regression: permissions are cleared and re-fetched over HTTP after a
  // legacy-auth sync, so a direct navigation can reach the guard before the
  // list arrives. The guard must wait rather than deny on the empty snapshot.
  it('should wait for late-arriving permissions instead of denying', fakeAsync(() => {
    umsAuthService.isLoggedIn.and.returnValue(true);
    permissionsSubject.next([]);

    const route = { data: { pageCode: 'FINANCE', permission: 'FINANCE.EDIT' } } as any;
    const state = { url: '/finance/add' } as any;

    let result: boolean | undefined;
    guard.canActivate(route, state).subscribe(value => (result = value));

    // Nothing decided yet — the permission list is still empty.
    expect(result).toBeUndefined();
    expect(router.navigate).not.toHaveBeenCalled();

    permissionsSubject.next(['FINANCE.VIEW', 'FINANCE.EDIT']);
    tick();

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  }));

  it('should deny once the wait window expires and no permissions arrive', fakeAsync(() => {
    umsAuthService.isLoggedIn.and.returnValue(true);
    permissionService.hasPermission.and.returnValue(false);
    permissionsSubject.next([]);

    const route = { data: { permission: 'FINANCE.EDIT' } } as any;
    const state = { url: '/finance/add' } as any;

    let result: boolean | undefined;
    guard.canActivate(route, state).subscribe(value => (result = value));

    expect(result).toBeUndefined();

    tick(5000);

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);
  }));

  // The code lists (names, tag colours, icons of every code) load in the same
  // step as the permission map, so a feature route activates with them warm.
  it('should load the code lists before activating and not block on a slow load', fakeAsync(() => {
    const loaded = new Subject<boolean>();
    const codeLists = { isLoaded: false, ensureLoaded: jasmine.createSpy('ensureLoaded').and.returnValue(loaded.asObservable()) };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        UmsPermissionGuard,
        { provide: AuthService, useValue: authService },
        { provide: UmsAuthService, useValue: umsAuthService },
        { provide: UmsPermissionService, useValue: permissionService },
        { provide: Router, useValue: router },
        { provide: CodeListService, useValue: codeLists },
        { provide: MenuVariantService, useValue: { isProposed: true } }
      ]
    });
    guard = TestBed.inject(UmsPermissionGuard);
    umsAuthService.isLoggedIn.and.returnValue(true);

    const route = { data: { pageCode: 'FINANCE' } } as any;
    const state = { url: '/finance' } as any;

    let result: boolean | undefined;
    guard.canActivate(route, state).subscribe(value => (result = value));
    expect(codeLists.ensureLoaded).toHaveBeenCalled();
    expect(result).withContext('waits for the lists').toBeUndefined();

    loaded.next(true);
    tick();
    expect(result).toBe(true);

    // A load that never settles lets the route through after the wait window.
    result = undefined;
    guard.canActivate(route, state).subscribe(value => (result = value));
    tick(5000);
    expect(result).toBe(true);
  }));

  describe('an account holding a password an administrator set (DEC-04)', () => {
    beforeEach(() => {
      Object.defineProperty(umsAuthService, 'mustChangePassword', { get: () => true, configurable: true });
    });

    it('is sent to its profile from any other page', () => {
      const allowed = activate({ data: { pageCode: 'STUDENTS' } }, { url: '/students' });

      expect(allowed).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/profile'], { queryParams: { changePassword: 'required' } });
    });

    it('may open the profile, where the password is changed', () => {
      const allowed = activate({ data: {} }, { url: '/profile?changePassword=required' });

      expect(allowed).toBeTrue();
    });
  });

  /**
   * Live QA M5 (25 Sep 2026): a role with no dashboard section signed in, was sent to
   * the home page, and landed on Access Denied. A refused home page now goes to the
   * first row of the person's sidebar; any other refused page still goes to Access Denied.
   */
  describe('a refused home page (M5)', () => {
    const home = { data: { pageCodes: ['DASHBOARD', 'DASHBOARD_FINANCE'] } } as any;
    let held: string[];

    beforeEach(() => {
      umsAuthService.isLoggedIn.and.returnValue(true);
      held = ['REPORTS'];
      permissionService.canAccessPage.and.callFake((code: string) => held.includes(code));
      permissionService.canView.and.callFake((code: string) => held.includes(code));
      permissionService.hasPermission.and.callFake((code: string) => held.includes(code.replace(/\.VIEW$/, '')));
    });

    it('sends a reports-only role to the reports hub', () => {
      expect(activate(home, { url: '/' })).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/reports/list']);
      expect(router.navigate).not.toHaveBeenCalledWith(['/auth/access']);
    });

    it('sends a role whose sidebar is empty to its profile', () => {
      held = [];
      expect(activate(home, { url: '/' })).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/profile']);
    });

    it('still sends a refused feature page to Access Denied', () => {
      expect(activate({ data: { pageCode: 'FINANCE' } }, { url: '/finance' })).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/auth/access']);
    });

    it('lets a role with a dashboard section open the home page', () => {
      held = ['DASHBOARD_FINANCE'];
      expect(activate(home, { url: '/' })).toBeTrue();
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });
});
