import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { silentErrors } from './http-context.tokens';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ErrorInterceptor } from './error.interceptor';
import { TranslationService } from '../service/translation.service';
import { StudentPortalApiService } from '../../student-portal/student-portal-api.service';

describe('ErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let messageService: jasmine.SpyObj<MessageService>;
  let router: jasmine.SpyObj<Router>;
  let portalApi: jasmine.SpyObj<StudentPortalApiService>;

  beforeEach(() => {
    const messageServiceSpy = jasmine.createSpyObj('MessageService', ['add']);
    const translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);
    translationServiceSpy.translate.and.callFake((key: string) => key);
    router = jasmine.createSpyObj('Router', ['navigate'], { url: '/student-portal/dashboard' });
    portalApi = jasmine.createSpyObj('StudentPortalApiService', ['clearToken']);

    TestBed.configureTestingModule({
    imports: [],
    providers: [
        { provide: MessageService, useValue: messageServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy },
        { provide: Router, useValue: router },
        { provide: StudentPortalApiService, useValue: portalApi },
        { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting()
    ]
});
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    messageService = TestBed.inject(MessageService) as jasmine.SpyObj<MessageService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should show error message on 5xx response', (done) => {
    http.get('/api/test').subscribe({
      error: () => {
        expect(messageService.add).toHaveBeenCalledWith(
          jasmine.objectContaining({
            severity: 'error',
            summary: jasmine.any(String),
            detail: jasmine.any(String)
          })
        );
        done();
      }
    });
    httpMock.expectOne('/api/test').flush('error', { status: 500, statusText: 'Server Error' });
  });

  it('should show connection error message on status 0', (done) => {
    http.get('/api/test').subscribe({
      error: () => {
        expect(messageService.add).toHaveBeenCalledWith(
          jasmine.objectContaining({
            severity: 'warn',
            summary: jasmine.any(String)
          })
        );
        done();
      }
    });
    httpMock.expectOne('/api/test').error(new ProgressEvent('error'), { status: 0 });
  });

  it('shows one "too many requests" toast for a burst of 429s, not the English body', () => {
    // A report page fires several calls at once; the rate limiter refuses each.
    for (let i = 0; i < 3; i++) {
      http.get(`/api/reports/meta${i}`).subscribe({ error: () => { } });
      httpMock.expectOne(`/api/reports/meta${i}`).flush(
        { error: 'Too many report requests. Please wait before retrying.' },
        { status: 429, statusText: 'Too Many Requests' });
    }
    expect(messageService.add).toHaveBeenCalledTimes(1);
    // The translate spy answers the key, so the interceptor falls back to its own text.
    expect(messageService.add).toHaveBeenCalledWith(jasmine.objectContaining({
      severity: 'warn',
      summary: 'Too many requests'
    }));
  });

  it('titles a pending-request refusal as such and shows the message from the server naming the request (W4-08)', (done) => {
    http.put('/api/loans/7', {}).subscribe({
      error: () => {
        expect(messageService.add).toHaveBeenCalledWith(jasmine.objectContaining({
          summary: 'Awaiting approval',
          detail: 'This record already has a request awaiting approval (request 20732).'
        }));
        done();
      }
    });
    httpMock.expectOne('/api/loans/7').flush(
      { code: 'PENDING_EXISTS', requestId: 20732, message: 'This record already has a request awaiting approval (request 20732).' },
      { status: 409, statusText: 'Conflict' });
  });

  it('should show not-found toast on 404', (done) => {
    http.get('/api/test').subscribe({
      error: () => {
        expect(messageService.add).toHaveBeenCalledWith(
          jasmine.objectContaining({
            severity: 'warn',
            summary: jasmine.any(String)
          })
        );
        done();
      }
    });
    httpMock.expectOne('/api/test').flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  // A 403 used to be swallowed: a route guarded by one code and an endpoint by
  // another left the user on a page that silently showed nothing.
  it('shows a "no permission" toast on 403 and still rejects the request', (done) => {
    http.get('/mohe/api/v1/charts/dashboard/overview').subscribe({
      error: (err) => {
        expect(err.status).toBe(403);
        expect(messageService.add).toHaveBeenCalledWith(
          jasmine.objectContaining({
            severity: 'warn',
            // The stub translator returns the key, so t() falls back to the English text.
            detail: 'You do not have permission for this action'
          })
        );
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/charts/dashboard/overview')
      .flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
  });

  it('stays quiet on a 403 when the caller opted out with SUPPRESS_ERROR_TOAST', (done) => {
    http.get('/mohe/api/v1/optional', { context: silentErrors() }).subscribe({
      error: (err) => {
        expect(err.status).toBe(403);
        expect(messageService.add).not.toHaveBeenCalled();
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/optional')
      .flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
  });

  it('tells the user the server is down on a gateway error, without echoing the proxy page', (done) => {
    http.get('/mohe/api/v1/approvals/count/pending').subscribe({
      error: () => {
        expect(messageService.add).toHaveBeenCalledWith(
          jasmine.objectContaining({
            severity: 'error',
            // The stub translator returns the key, so t() falls back to the English text.
            summary: 'Server Unavailable',
            detail: 'The application server is not running or is not responding. Please start it or contact the administrator, then try again.'
          })
        );
        const detail = messageService.add.calls.mostRecent().args[0].detail as string;
        expect(detail).not.toContain('proxy');
        expect(detail).not.toContain('localhost');
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/approvals/count/pending').flush(
      'Error occurred while trying to proxy: localhost:4200/mohe/api/v1/approvals/count/pending',
      { status: 504, statusText: 'Gateway Timeout' });
  });

  // A dead portal token used to be ignored: every 401 under /student-portal/ was
  // skipped, so the student stayed on a page whose every call failed.
  it('clears the portal token and goes to the portal login on a 401 from a session call', (done) => {
    http.get('/mohe/api/v1/student-portal/me/data').subscribe({
      error: () => {
        expect(portalApi.clearToken).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalledWith(['/student-portal/login']);
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/student-portal/me/data')
      .flush({ error: 'expired' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('treats a 401 on /change-requests as a dead portal session too', (done) => {
    http.post('/mohe/api/v1/student-portal/change-requests', {}).subscribe({
      error: () => {
        expect(portalApi.clearToken).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalledWith(['/student-portal/login']);
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/student-portal/change-requests')
      .flush({ error: 'expired' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('leaves a 401 from the portal login alone so the form can show the wrong-password error', (done) => {
    http.post('/mohe/api/v1/student-portal/auth/login', {}).subscribe({
      error: (err) => {
        expect(err.status).toBe(401);
        expect(portalApi.clearToken).not.toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalled();
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/student-portal/auth/login')
      .flush({ error: 'bad credentials' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('does not show a text/HTML 500 body as the message', (done) => {
    http.get('/api/test').subscribe({
      error: () => {
        const detail = messageService.add.calls.mostRecent().args[0].detail as string;
        expect(detail).toBe('Something went wrong on the server. Please try again later.');
        done();
      }
    });
    httpMock.expectOne('/api/test').flush('<html><body>Internal Server Error</body></html>',
      { status: 500, statusText: 'Server Error' });
  });

  it('still shows the backend\'s own message on a structured 500', (done) => {
    http.get('/api/test').subscribe({
      error: () => {
        const detail = messageService.add.calls.mostRecent().args[0].detail as string;
        expect(detail).toBe('Quarter 2024/2 is already closed');
        done();
      }
    });
    httpMock.expectOne('/api/test').flush({ message: 'Quarter 2024/2 is already closed' },
      { status: 500, statusText: 'Server Error' });
  });

  // Live QA M16: a mistyped authenticator code answered 401 and signed the student out.
  // The server now answers 400; a 400 on a session call must leave the session alone.
  it('keeps the portal session on a 400 from a session call (a wrong authenticator code)', (done) => {
    http.post('/mohe/api/v1/student-portal/me/security/totp/disable', {}).subscribe({
      error: (err) => {
        expect(err.status).toBe(400);
        expect(portalApi.clearToken).not.toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalledWith(['/student-portal/login']);
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/student-portal/me/security/totp/disable')
      .flush({ message: 'The code did not match' }, { status: 400, statusText: 'Bad Request' });
  });

  // SEC-001 (D9-a): a portal account that never chose its password is held to the change.
  it('sends a student held to the password change to the form, with no access-denied toast', (done) => {
    http.get('/mohe/api/v1/student-portal/me/data').subscribe({
      error: (err) => {
        expect(err.status).toBe(403);
        expect(router.navigate).toHaveBeenCalledWith(['/student-portal/security'], { queryParams: { changePassword: 'required' } });
        expect((portalApi as any).mustChangePassword).toBeTrue();
        expect(portalApi.clearToken).not.toHaveBeenCalled();
        expect(messageService.add).not.toHaveBeenCalled();
        done();
      }
    });
    httpMock.expectOne('/mohe/api/v1/student-portal/me/data')
      .flush({ code: 'PORTAL_PASSWORD_CHANGE_REQUIRED', message: 'Choose your own password before continuing.' },
        { status: 403, statusText: 'Forbidden' });
  });
});
