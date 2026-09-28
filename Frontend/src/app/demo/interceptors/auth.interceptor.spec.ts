import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { AuthInterceptor, REFRESH_AT } from './auth.interceptor';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';

/**
 * DEC-17, plan W5-02: the token lasts an hour and the browser renews it at
 * REFRESH_AT of its life. Waiting for the 401 ended every session at the
 * server's expiry, because a refresh of an expired token is refused.
 */
describe('AuthInterceptor proactive refresh', () => {
    let http: HttpClient;
    let httpMock: HttpTestingController;
    let token: string | null;

    /** A JWT whose signature nobody checks here: only iat and exp are read. */
    function jwt(issuedSecondsAgo: number, lifeSeconds = 3600): string {
        const iat = Math.floor(Date.now() / 1000) - issuedSecondsAgo;
        const payload = btoa(JSON.stringify({ sub: 'u', iat, exp: iat + lifeSeconds }));
        return `header.${payload}.signature`;
    }

    beforeEach(() => {
        token = null;
        const ums = {
            getToken: () => token,
            setTokenOnly: (t: string) => { token = t; },
            clearAuthState: () => { token = null; }
        };
        TestBed.configureTestingModule({
            providers: [
                { provide: UmsAuthService, useValue: ums },
                { provide: AuthService, useValue: { logout: () => undefined } },
                { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting()
            ]
        });
        http = TestBed.inject(HttpClient);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('reads how much of a token life has passed', () => {
        expect(AuthInterceptor.isStale(jwt(10))).withContext('fresh').toBeFalse();
        expect(AuthInterceptor.isStale(jwt(3600 * REFRESH_AT - 60))).withContext('just before').toBeFalse();
        expect(AuthInterceptor.isStale(jwt(3600 * REFRESH_AT + 60))).withContext('just after').toBeTrue();
        expect(AuthInterceptor.isStale(null)).toBeFalse();
        expect(AuthInterceptor.isStale('not.a.jwt')).withContext('unreadable: left to the 401 path').toBeFalse();
    });

    it('renews a stale token first and sends the request with the new one', () => {
        const stale = jwt(3300);
        token = stale;
        http.get('/mohe/api/v1/students').subscribe();

        const refresh = httpMock.expectOne(r => r.url.endsWith('/ums/auth/refresh'));
        expect(refresh.request.headers.get('Authorization')).toBe(`Bearer ${stale}`);
        refresh.flush({ token: jwt(0) });

        const sent = httpMock.expectOne('/mohe/api/v1/students');
        expect(sent.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
        sent.flush({});
    });

    it('leaves a fresh token alone', () => {
        token = jwt(60);
        http.get('/mohe/api/v1/students').subscribe();
        httpMock.expectOne('/mohe/api/v1/students').flush({});
    });

    it('does not renew on the way to the auth endpoints or the portal', () => {
        token = jwt(3500);
        http.post('/mohe/api/v1/ums/auth/logout', {}).subscribe();
        httpMock.expectOne('/mohe/api/v1/ums/auth/logout').flush({});

        http.get('/mohe/api/v1/student-portal/me').subscribe();
        const portal = httpMock.expectOne('/mohe/api/v1/student-portal/me');
        expect(portal.request.headers.has('Authorization')).withContext('the portal has its own token').toBeFalse();
        portal.flush({});
    });
});
