import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // /ums/users/{id} sits under UMS_VIEW, so a user without the UMS admin page
  // got a 403 on their own profile. /me is open to any authenticated user.
  it('reads the profile from /ums/users/me and maps the UMS user to the shell User', (done) => {
    service.getUserProfile().subscribe(user => {
      expect(user.id).toBe(7);
      expect(user.userName).toBe('w.ali');
      expect(user.lastName).toBe('Ali');
      expect(user.role.roleName).toBe('ADMIN');
      done();
    });

    const req = httpMock.expectOne(r => r.url.endsWith('/ums/users/me'));
    expect(req.request.method).toBe('GET');
    req.flush({ id: 7, userName: 'w.ali', lastName: 'Ali', isAdmin: true, isSuperAdmin: false });
  });

  // The profile page needs the whole record — email, phone, status and the
  // three dates — and used to be handed the shell's four-field view, which is
  // why it filled those rows with today's date instead.
  it('hands the whole UMS record to callers that ask for the account', (done) => {
    service.getMyAccount().subscribe(account => {
      expect(account.email).toBe('w.ali@mohe.gov.ye');
      expect(account.status).toBe('ACTIVE');
      expect(account.lastLoginAt).toBe('2026-08-28T07:15:00Z');
      expect(account.passwordChangedAt).toBe('2026-06-01T09:00:00Z');
      done();
    });

    const req = httpMock.expectOne(r => r.url.endsWith('/ums/users/me'));
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 7, userName: 'w.ali', firstName: 'Wael', lastName: 'Ali',
      email: 'w.ali@mohe.gov.ye', phone: '+967 1 234567', status: 'ACTIVE',
      isAdmin: true, isSuperAdmin: false,
      lastLoginAt: '2026-08-28T07:15:00Z', passwordChangedAt: '2026-06-01T09:00:00Z',
      createdAt: '2025-03-04T10:00:00Z'
    });
  });

  // updateUserProfile is gone with the profile page's edit mode: an account's
  // login name and the name on it are the administrator's to set (owner's
  // decision, 29 August 2026), and the endpoint never had a branch for userName
  // in any case.
});
