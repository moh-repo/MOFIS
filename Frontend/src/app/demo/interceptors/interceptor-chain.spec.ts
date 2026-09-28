import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { APP_HTTP_INTERCEPTORS } from './http-interceptors';
import { AuthService } from '../service/auth.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { TranslationService } from '../service/translation.service';
import { CountrySelectionService } from '../service/country-selection.service';
import { StudentPortalApiService } from '../../student-portal/student-portal-api.service';

/**
 * The whole interceptor chain, in the order app.module.ts registers it.
 *
 * FE-02: ErrorInterceptor used to sit innermost, saw every 401 first and signed the
 * user out, so AuthInterceptor's refresh found no token and the retry never ran.
 * FE-01: a wrong MFA code (401 from /ums/auth/mfa/*) signed the user out.
 * FE-03: a proactive refresh that failed on a network error or 5xx signed the user out
 * although the token was still valid.
 */
describe('HTTP interceptor chain (app order)', () => {
    let http: HttpClient;
    let httpMock: HttpTestingController;
    let token: string | null;
    let ums: { getToken: () => string | null; setTokenOnly: (t: string) => void;
               clearAuthState: jasmine.Spy; refreshProfile: jasmine.Spy };
    let authService: jasmine.SpyObj<AuthService>;
    let router: jasmine.SpyObj<Router>;
    let messages: jasmine.SpyObj<MessageService>;

    const API = '/mohe/api/v1/students';
    const isRefresh = (r: { url: string }) => r.url.endsWith('/ums/auth/refresh');

    function jwt(issuedSecondsAgo: number, lifeSeconds = 3600): string {
        const iat = Math.floor(Date.now() / 1000) - issuedSecondsAgo;
        const payload = btoa(JSON.stringify({ sub: 'u', iat, exp: iat + lifeSeconds }));
        return `header.${payload}.signature`;
    }

    beforeEach(() => {
        token = null;
        ums = {
            getToken: () => token,
            setTokenOnly: (t: string) => { token = t; },
            clearAuthState: jasmine.createSpy('clearAuthState').and.callFake(() => { token = null; }),
            refreshProfile: jasmine.createSpy('refreshProfile').and.returnValue(of(null))
        };
        authService = jasmine.createSpyObj('AuthService', ['logout']);
        router = jasmine.createSpyObj('Router', ['navigate'], { url: '/students' });
        messages = jasmine.createSpyObj('MessageService', ['add']);
        const translation = jasmine.createSpyObj('TranslationService', ['translate', 'getCurrentLanguage']);
        translation.translate.and.callFake((k: string) => k);
        translation.getCurrentLanguage.and.returnValue('en');

        TestBed.configureTestingModule({
            providers: [
                { provide: UmsAuthService, useValue: ums },
                { provide: AuthService, useValue: authService },
                { provide: Router, useValue: router },
                { provide: MessageService, useValue: messages },
                { provide: TranslationService, useValue: translation },
                { provide: CountrySelectionService, useValue: { selectedCountryId: null } },
                { provide: StudentPortalApiService, useValue: jasmine.createSpyObj('StudentPortalApiService', ['clearToken']) },
                ...APP_HTTP_INTERCEPTORS,
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting()
            ]
        });
        http = TestBed.inject(HttpClient);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    function expectSignedIn(): void {
        expect(ums.clearAuthState).not.toHaveBeenCalled();
        expect(authService.logout).not.toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalledWith(['/auth/login'], jasmine.anything());
    }

    function expectSignedOut(): void {
        expect(ums.clearAuthState).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalledWith(['/auth/login'],
            jasmine.objectContaining({ queryParams: jasmine.objectContaining({ reason: 'session_expired' }) }));
    }

    // ── FE-02 ────────────────────────────────────────────────────────────────

    it('FE-02: 401 → refresh 200 → retry succeeds, and the user stays signed in', () => {
        token = jwt(60);
        const oldToken = token;
        const newToken = jwt(0, 7200);
        let body: unknown;
        let failed = false;
        http.get(API).subscribe({ next: b => body = b, error: () => failed = true });

        httpMock.expectOne(API).flush({ error: 'expired' }, { status: 401, statusText: 'Unauthorized' });

        const refresh = httpMock.expectOne(isRefresh);
        expect(refresh.request.headers.get('Authorization')).toBe(`Bearer ${oldToken}`);
        refresh.flush({ token: newToken });

        const retry = httpMock.expectOne(API);
        expect(retry.request.headers.get('Authorization')).toBe(`Bearer ${newToken}`);
        expect(retry.request.headers.get('Accept-Language')).toBe('en');
        retry.flush([{ id: 1 }]);

        expect(body).toEqual([{ id: 1 }]);
        expect(failed).toBeFalse();
        expect(token).toBe(newToken);
        expectSignedIn();
        expect(messages.add).not.toHaveBeenCalled();
    });

    it('FE-02: a refresh that is itself refused (401) signs out once, with no second refresh', () => {
        token = jwt(60);
        let error: { status?: number } | undefined;
        http.get(API).subscribe({ error: e => error = e });

        httpMock.expectOne(API).flush({}, { status: 401, statusText: 'Unauthorized' });
        httpMock.expectOne(isRefresh).flush({}, { status: 401, statusText: 'Unauthorized' });
        // No retry, no further refresh: verify() in afterEach fails on any extra request.
        httpMock.expectNone(isRefresh);
        httpMock.expectNone(API);

        expect(error?.status).toBe(401);
        expectSignedOut();
        expect(router.navigate).toHaveBeenCalledTimes(1);
    });

    it('FE-02: a retry that is refused again signs out without another refresh', () => {
        token = jwt(60);
        let error: { status?: number } | undefined;
        http.get(API).subscribe({ error: e => error = e });

        httpMock.expectOne(API).flush({}, { status: 401, statusText: 'Unauthorized' });
        httpMock.expectOne(isRefresh).flush({ token: jwt(0) });
        httpMock.expectOne(API).flush({}, { status: 401, statusText: 'Unauthorized' });
        httpMock.expectNone(isRefresh);

        expect(error?.status).toBe(401);
        expectSignedOut();
    });

    it('FE-02: a 500 on the retry is shown as a server error, not a sign-out', () => {
        token = jwt(60);
        http.get(API).subscribe({ error: () => undefined });

        httpMock.expectOne(API).flush({}, { status: 401, statusText: 'Unauthorized' });
        httpMock.expectOne(isRefresh).flush({ token: jwt(0) });
        httpMock.expectOne(API).flush({}, { status: 500, statusText: 'Server Error' });

        expectSignedIn();
        expect(messages.add).toHaveBeenCalledWith(jasmine.objectContaining({ severity: 'error' }));
    });

    it('FE-02: concurrent 401s share one refresh', () => {
        token = jwt(60);
        const newToken = jwt(0, 7200);
        const results: unknown[] = [];
        http.get(API).subscribe(b => results.push(b));
        http.get('/mohe/api/v1/countries').subscribe(b => results.push(b));

        httpMock.expectOne(API).flush({}, { status: 401, statusText: 'Unauthorized' });
        httpMock.expectOne('/mohe/api/v1/countries').flush({}, { status: 401, statusText: 'Unauthorized' });
        httpMock.expectOne(isRefresh).flush({ token: newToken });

        httpMock.expectOne(API).flush('a');
        httpMock.expectOne('/mohe/api/v1/countries').flush('b');
        expect(results.sort()).toEqual(['a', 'b']);
        expectSignedIn();
    });

    // ── FE-01 ────────────────────────────────────────────────────────────────

    for (const path of ['mfa/disable', 'mfa/recovery-codes', 'mfa/enrol/confirm', 'mfa/verify']) {
        it(`FE-01: a wrong code on /ums/auth/${path} reaches the caller and does not sign out`, () => {
            token = jwt(60);
            const url = `/mohe/api/v1/ums/auth/${path}`;
            let error: { status?: number } | undefined;
            http.post(url, { code: '000000' }).subscribe({ error: e => error = e });

            httpMock.expectOne(url).flush({ message: 'The code did not match.' },
                { status: 401, statusText: 'Unauthorized' });
            httpMock.expectNone(isRefresh);

            expect(error?.status).toBe(401);
            expectSignedIn();
            expect(token).not.toBeNull();
        });
    }

    it('FE-01: a 401 from an ordinary /ums/auth/ call (e.g. /me) still signs out', () => {
        token = jwt(60);
        http.get('/mohe/api/v1/ums/auth/me').subscribe({ error: () => undefined });
        httpMock.expectOne('/mohe/api/v1/ums/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
        expectSignedOut();
    });

    // ── FE-03 ────────────────────────────────────────────────────────────────

    for (const status of [0, 500, 503]) {
        it(`FE-03: a proactive refresh failing with ${status} sends the request with the old token`, () => {
            const stale = jwt(3300);
            token = stale;
            let body: unknown;
            http.get(API).subscribe(b => body = b);

            const refresh = httpMock.expectOne(isRefresh);
            if (status === 0) {
                refresh.error(new ProgressEvent('error'));
            } else {
                refresh.flush({}, { status, statusText: 'Unavailable' });
            }

            const sent = httpMock.expectOne(API);
            expect(sent.request.headers.get('Authorization')).toBe(`Bearer ${stale}`);
            sent.flush({ ok: true });

            expect(body).toEqual({ ok: true });
            expect(token).toBe(stale);
            expectSignedIn();
            // A background renewal the user never asked for is not toasted.
            expect(messages.add).not.toHaveBeenCalled();
        });
    }

    it('FE-03: after a failed proactive refresh the next request tries again', () => {
        token = jwt(3300);
        http.get(API).subscribe();
        httpMock.expectOne(isRefresh).flush({}, { status: 503, statusText: 'Unavailable' });
        httpMock.expectOne(API).flush({});

        http.get(API).subscribe();
        httpMock.expectOne(isRefresh).flush({ token: jwt(0) });
        httpMock.expectOne(API).flush({});
        expectSignedIn();
    });

    for (const status of [401, 403]) {
        it(`FE-03: a proactive refresh refused with ${status} signs out`, () => {
            token = jwt(3300);
            let failed = false;
            http.get(API).subscribe({ error: () => failed = true });
            httpMock.expectOne(isRefresh).flush({}, { status, statusText: 'Refused' });
            httpMock.expectNone(API);

            expect(failed).toBeTrue();
            expect(ums.clearAuthState).toHaveBeenCalled();
            expect(authService.logout).toHaveBeenCalled();
        });
    }

    it('FE-03: after a 401, a refresh that fails on the network still signs out (the token is already refused)', () => {
        token = jwt(60);
        let error: { status?: number } | undefined;
        http.get(API).subscribe({ error: e => error = e });
        httpMock.expectOne(API).flush({}, { status: 401, statusText: 'Unauthorized' });
        httpMock.expectOne(isRefresh).error(new ProgressEvent('error'));

        expect(error?.status).toBe(401);
        expectSignedOut();
    });
});
