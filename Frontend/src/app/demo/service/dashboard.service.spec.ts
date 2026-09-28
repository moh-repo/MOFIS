import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { DASHBOARD_SECTION_CODES, DashboardService } from './dashboard.service';
import { ReportCountryScopeService } from './report-country-scope.service';
import { SUPPRESS_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { environment } from 'src/environments/environment';

describe('DashboardService', () => {
    let service: DashboardService;
    let httpMock: HttpTestingController;
    let country$: BehaviorSubject<number | null>;

    const base = `${environment.apiUrl}/mohe/api/v1/dashboard`;

    beforeEach(() => {
        country$ = new BehaviorSubject<number | null>(1004);
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                // The selected country is 1004 here on purpose: the old
                // StatisticsService would have turned that into ?countryId=1004.
                { provide: ReportCountryScopeService, useValue: { selectedCountryId$: country$.asObservable(), selectedCountryId: 1004 } }
            ]
        });
        service = TestBed.inject(DashboardService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    /** Every section getter, with the path it must hit. */
    const sections: [string, () => Observable<unknown>][] = [
        ['students', () => service.getStudents()],
        ['finance', () => service.getFinance()],
        ['approvals', () => service.getApprovals()],
        ['alert-cases', () => service.getAlertCases()],
        ['countries', () => service.getCountries()],
        ['users', () => service.getUsers()],
        ['quarter', () => service.getQuarter()],
        ['notices', () => service.getNotices()]
    ];

    it('lists eight section codes, one per getter', () => {
        expect(sections.length).toBe(DASHBOARD_SECTION_CODES.length);
    });

    sections.forEach(([path, call]) => {
        it(`GET /${path}: exact URL, no countryId param, error toast suppressed`, () => {
            call().subscribe();

            const req = httpMock.expectOne(`${base}/${path}`);
            expect(req.request.method).toBe('GET');
            // The X-Country-Id header is the country contract. A countryId param
            // would make CountryScopeInterceptor drop that header, and the
            // sections that only read the header would come back unscoped.
            expect(req.request.params.has('countryId')).withContext('no countryId param').toBeFalse();
            expect(req.request.params.has('countryIds')).toBeFalse();
            expect(req.request.urlWithParams).not.toContain('countryId');
            // /sections is the gate; a 403 here is a stale-permission race that
            // the component renders as the section's empty state.
            expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).toBeTrue();
            req.flush({});
        });
    });

    it('GET /sections: exact URL, no countryId param, toast NOT suppressed', () => {
        service.getSections().subscribe();

        const req = httpMock.expectOne(`${base}/sections`);
        expect(req.request.method).toBe('GET');
        expect(req.request.params.keys().length).toBe(0);
        expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).toBeFalse();
        req.flush({ sections: [], all: false });
    });

    it('re-exports the topbar country stream', () => {
        const seen: (number | null)[] = [];
        service.selectedCountryId$.subscribe(id => seen.push(id));
        country$.next(null);
        expect(seen).toEqual([1004, null]);
    });
});
