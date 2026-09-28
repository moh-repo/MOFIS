import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { CodeListService, EnumOption } from './code-list.service';
import { TranslationService } from './translation.service';
import { environment } from 'src/environments/environment';

/**
 * The bulk code-list map: one request after login, both names kept so a
 * language switch reads the other name without a reload, severities checked
 * against PrimeNG's vocabulary, and an unknown code shown as itself.
 */
describe('CodeListService', () => {
    let service: CodeListService;
    let httpMock: HttpTestingController;
    let language$: BehaviorSubject<string>;

    const url = `${environment.apiUrl}/mohe/api/v1/code-lists`;

    const row = (value: string, patch: Partial<EnumOption> = {}): EnumOption => ({
        label: `${value}-ar`, value, labelAr: `${value}-ar`, labelEn: `${value}-en`,
        sortOrder: 0, uiSeverity: null, icon: null, isSystem: false, active: true, extra: null, ...patch
    });

    const payload = {
        'alert-severity': [
            row('HIGH', { sortOrder: 2, uiSeverity: 'warn', icon: 'pi-exclamation-triangle' }),
            row('CRITICAL', { sortOrder: 1, uiSeverity: 'danger', icon: 'pi-exclamation-circle' }),
            row('LEGACY', { sortOrder: 9, active: false })
        ],
        'graph-category': [row('cat_finance', { extra: '#4CAF50', uiSeverity: 'not-a-severity' as never })],
        'half-named': [row('ONLY_AR', { labelEn: '' })]
    };

    beforeEach(() => {
        language$ = new BehaviorSubject<string>('en');
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslationService,
                    useValue: { currentLanguage$: language$.asObservable(), getCurrentLanguage: () => language$.value }
                }
            ]
        });
        service = TestBed.inject(CodeListService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    function load(body: unknown = payload) {
        let result: boolean | undefined;
        service.ensureLoaded().subscribe(ok => (result = ok));
        httpMock.expectOne(url).flush(body);
        return result;
    }

    it('loads the bulk endpoint once and shares it between callers', () => {
        expect(service.isLoaded).toBeFalse();
        service.ensureLoaded().subscribe();
        service.ensureLoaded().subscribe();
        httpMock.expectOne(url).flush(payload);

        expect(service.isLoaded).toBeTrue();
        service.ensureLoaded().subscribe();
        httpMock.expectNone(url);
        expect(service.listNames()).toEqual(['alert-severity', 'graph-category', 'half-named']);
    });

    it('keeps inactive rows for historical values but leaves them out of the options', () => {
        load();
        expect(service.rows('alert-severity').map(r => r.value)).toEqual(['CRITICAL', 'HIGH', 'LEGACY']);
        expect(service.active('alert-severity').map(r => r.value)).toEqual(['CRITICAL', 'HIGH']);
        expect(service.options('alert-severity')).toEqual([
            { label: 'CRITICAL-en', value: 'CRITICAL' },
            { label: 'HIGH-en', value: 'HIGH' }
        ]);
        expect(service.label('alert-severity', 'LEGACY')).toBe('LEGACY-en');
    });

    it('labels by the current language, then the other language, then the code', () => {
        load();
        expect(service.label('alert-severity', 'HIGH')).toBe('HIGH-en');
        language$.next('ar');
        expect(service.label('alert-severity', 'HIGH')).toBe('HIGH-ar');
        expect(service.label('alert-severity', 'high', 'en')).withContext('case-insensitive').toBe('HIGH-en');
        expect(service.label('half-named', 'ONLY_AR', 'en')).withContext('other language').toBe('ONLY_AR-ar');
        expect(service.label('alert-severity', 'UNKNOWN')).withContext('unknown code').toBe('UNKNOWN');
        expect(service.label('no-such-list', 'X')).toBe('X');
        expect(service.label('alert-severity', null)).toBe('');
    });

    it('does not clear the map on a language change', () => {
        load();
        language$.next('ar');
        language$.next('en');
        httpMock.expectNone(url);
        expect(service.has('alert-severity')).toBeTrue();
    });

    it('validates severities against the PrimeNG vocabulary', () => {
        load();
        expect(service.severity('alert-severity', 'CRITICAL')).toBe('danger');
        expect(service.severity('alert-severity', 'HIGH')).toBe('warn');
        expect(service.severity('alert-severity', 'LEGACY')).withContext('null severity').toBe('secondary');
        expect(service.severity('graph-category', 'cat_finance')).withContext('not in the vocabulary').toBe('secondary');
        expect(service.severity('alert-severity', 'NOPE')).toBe('secondary');
        expect(CodeListService.toSeverity('warning')).withContext('PrimeNG 21 has no `warning`').toBe('secondary');
        expect(CodeListService.toNzColor('danger')).toBe('error');
        expect(CodeListService.toToastSeverity('danger')).toBe('error');
        expect(CodeListService.toToastSeverity('warn')).toBe('warn');
    });

    it('serves icons and the extra column', () => {
        load();
        expect(service.icon('alert-severity', 'critical')).toBe('pi-exclamation-circle');
        expect(service.icon('alert-severity', 'LEGACY')).toBeNull();
        expect(service.color('graph-category', 'cat_finance')).toBe('#4CAF50');
        expect(service.color('graph-category', 'cat_none')).toBeNull();
    });

    it('settles as loaded on a failed request so routes are not blocked', () => {
        let result: boolean | undefined;
        service.ensureLoaded().subscribe(ok => (result = ok));
        httpMock.expectOne(url).flush({ error: 'boom' }, { status: 500, statusText: 'Server Error' });

        expect(result).toBeFalse();
        expect(service.isLoaded).toBeTrue();
        expect(service.label('alert-severity', 'HIGH')).toBe('HIGH');
    });

    it('sends the caller\'s headers (the portal token) and reloads on demand', () => {
        service.ensureLoaded({ 'X-Student-Portal-Token': 'abc' }).subscribe();
        const req = httpMock.expectOne(url);
        expect(req.request.headers.get('X-Student-Portal-Token')).toBe('abc');
        req.flush(payload);

        service.reload().subscribe();
        httpMock.expectOne(url).flush({ 'alert-severity': [row('ONLY')] });
        expect(service.rows('alert-severity').map(r => r.value)).toEqual(['ONLY']);
    });

    it('talks to the admin endpoints with the list name in the path', () => {
        service.getNames().subscribe();
        httpMock.expectOne(`${url}/names`).flush([]);
        service.getRows('alert-severity').subscribe();
        httpMock.expectOne(`${url}/alert-severity`).flush([]);
        service.createRow('alert-severity', { code: 'X' }).subscribe();
        expect(httpMock.expectOne(r => r.method === 'POST' && r.url === `${url}/alert-severity`).request.body).toEqual({ code: 'X' });
        service.updateRow('alert-severity', 7, { nameEn: 'y' }).subscribe();
        httpMock.expectOne(r => r.method === 'PUT' && r.url === `${url}/alert-severity/7`).flush({});
        // No delete: it set the same flag as switching a row off (QA B14, M4).
        expect((service as any).deleteRow).toBeUndefined();
    });
});
