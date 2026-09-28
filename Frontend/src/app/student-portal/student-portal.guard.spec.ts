import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';
import { StudentPortalGuard } from './student-portal.guard';
import { StudentPortalApiService } from './student-portal-api.service';
import { CodeListService } from '../demo/service/code-list.service';

describe('StudentPortalGuard', () => {
  let guard: StudentPortalGuard;
  let api: jasmine.SpyObj<StudentPortalApiService>;
  let router: jasmine.SpyObj<Router>;
  let codeLists: { isLoaded: boolean; ensureLoaded: jasmine.Spy };
  const loginTree = {} as UrlTree;

  beforeEach(() => {
    api = jasmine.createSpyObj('StudentPortalApiService', ['isLoggedIn', 'getToken', 'restoreSession']);
    api.getToken.and.returnValue('portal-token');
    api.restoreSession.and.returnValue(of(true));
    router = jasmine.createSpyObj('Router', ['createUrlTree']);
    router.createUrlTree.and.returnValue(loginTree);
    codeLists = { isLoaded: true, ensureLoaded: jasmine.createSpy('ensureLoaded').and.returnValue(of(true)) };

    TestBed.configureTestingModule({
      providers: [
        StudentPortalGuard,
        { provide: StudentPortalApiService, useValue: api },
        { provide: Router, useValue: router },
        { provide: CodeListService, useValue: codeLists }
      ]
    });
    guard = TestBed.inject(StudentPortalGuard);
  });

  /**
   * Activates the route and keeps whatever the guard answers, whenever it
   * answers: `value` is undefined while the guard is still deciding.
   */
  function activate(): { value: boolean | UrlTree | undefined } {
    const answered: { value: boolean | UrlTree | undefined } = { value: undefined };
    const result = guard.canActivate();
    if (result instanceof Observable) {
      result.subscribe(v => (answered.value = v));
    } else {
      answered.value = result;
    }
    return answered;
  }

  it('lets a student with a live session through', () => {
    api.isLoggedIn.and.returnValue(true);
    expect(activate().value).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('sends a student without a token to the portal login', () => {
    api.isLoggedIn.and.returnValue(false);
    expect(guard.canActivate()).toBe(loginTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/student-portal/login']);
    expect(api.restoreSession).withContext('nothing to check').not.toHaveBeenCalled();
  });

  /**
   * A token that expired while the browser was closed is still a string in
   * storage. It used to open the portal, and every request on the page then came
   * back 401 — a shell full of load failures instead of a sign-in prompt.
   */
  it('sends a student whose token the server has refused to the portal login', () => {
    api.isLoggedIn.and.returnValue(true);
    api.restoreSession.and.returnValue(of(false));

    expect(activate().value).toBe(loginTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/student-portal/login']);
  });

  it('loads the code lists with the portal token before the first page, bounded by the wait window', fakeAsync(() => {
    api.isLoggedIn.and.returnValue(true);
    codeLists.isLoaded = false;
    const loaded = new Subject<boolean>();
    codeLists.ensureLoaded.and.returnValue(loaded.asObservable());

    const first = activate();
    expect(codeLists.ensureLoaded).toHaveBeenCalledWith({ 'X-Student-Portal-Token': 'portal-token' });
    expect(first.value).toBeUndefined();

    loaded.next(true);
    tick();
    expect(first.value).toBe(true);

    codeLists.ensureLoaded.and.returnValue(new Subject<boolean>().asObservable());
    const second = activate();
    tick(5000);
    expect(second.value)
      .withContext('a load that never settles does not lock the student out').toBe(true);
  }));
});
