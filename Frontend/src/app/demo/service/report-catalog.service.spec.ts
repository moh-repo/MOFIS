import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { ReportCatalogService, parseQueryParams } from './report-catalog.service';
import { UmsPermissionService } from './ums-permission.service';
import { UmsReport } from '../api/ums';
import { environment } from 'src/environments/environment';

describe('ReportCatalogService', () => {
    let service: ReportCatalogService;
    let httpMock: HttpTestingController;
    let granted: Set<string>;

    const url = `${environment.apiUrl}/mohe/api/v1/ums/reports/catalog`;

    const row = (reportCode: string, patch: Partial<UmsReport> = {}): UmsReport => ({
        id: 0, reportCode, reportName: `${reportCode} en`, reportNameAr: `${reportCode} ar`,
        description: `${reportCode} desc en`, descriptionAr: `${reportCode} desc ar`, category: 'X', route: `/reports/${reportCode.toLowerCase()}`,
        isActive: true, parentCode: null, icon: 'pi-file', color: 'blue', queryParams: null, pageCode: null, permissionCodes: null,
        sortOrder: 0, createdAt: '', updatedAt: '', ...patch
    });

    const catalog: UmsReport[] = [
        row('HUB_STUDENT', { route: '/reports/student', pageCode: 'REPORT_ACADEMIC', sortOrder: 2 }),
        row('REP_FINANCIAL_DATA_FORM', { route: '/reports/financial-data-form', permissionCodes: 'REPORT_FINANCE,FINANCE', sortOrder: 1 }),
        row('HUB_FINANCIAL', { route: '/reports/financial', pageCode: 'REPORT_FINANCE', sortOrder: 3 }),
        row('REP_STUDENT_LIST', { parentCode: 'HUB_STUDENT', route: '/reports/student/list-detailed', sortOrder: 2 }),
        row('REP_DOC_ID_CARD', { parentCode: 'HUB_STUDENT', route: '/reports/student/individual-student', queryParams: 'doc=id-card', sortOrder: 3 }),
        row('REP_ENROLLMENT', { parentCode: 'HUB_STUDENT', route: '/reports/student/enrollment-statistics', sortOrder: 1, isActive: false }),
        row('REP_FIN_ONLY', { parentCode: 'HUB_STUDENT', route: '/reports/student/x', pageCode: 'REPORT_FINANCE', sortOrder: 4 })
    ];

    beforeEach(() => {
        granted = new Set(['REPORT_ACADEMIC']);
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: UmsPermissionService, useValue: { canAccessPage: (code: string) => granted.has(code) } }
            ]
        });
        service = TestBed.inject(ReportCatalogService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('fetches the catalogue once and shares it, sorted by sortOrder', () => {
        let a: UmsReport[] = [];
        let b: UmsReport[] = [];
        service.getCatalog().subscribe(r => (a = r));
        service.getCatalog().subscribe(r => (b = r));
        httpMock.expectOne(url).flush(catalog);

        expect(a.map(r => r.reportCode)).toEqual(['REP_FINANCIAL_DATA_FORM', 'REP_ENROLLMENT', 'HUB_STUDENT', 'REP_STUDENT_LIST', 'HUB_FINANCIAL', 'REP_DOC_ID_CARD', 'REP_FIN_ONLY']);
        expect(b).toBe(a);
        service.reload();
        service.getCatalog().subscribe();
        httpMock.expectOne(url).flush([]);
    });

    it('lists the landing cards: top-level rows the reader may open', () => {
        let cards: unknown[] = [];
        service.landingCards().subscribe(c => (cards = c));
        httpMock.expectOne(url).flush(catalog);

        expect(cards.map((c: any) => c.reportCode)).withContext('financial form (needs REPORT_FINANCE|FINANCE) and financial hub are hidden').toEqual(['HUB_STUDENT']);
        expect((cards[0] as any).nameAr).toBe('HUB_STUDENT ar');
        expect((cards[0] as any).pageCode).toBe('REPORT_ACADEMIC');

        granted.add('FINANCE');
        let again: unknown[] = [];
        service.landingCards().subscribe(c => (again = c));
        expect(again.map((c: any) => c.reportCode)).toEqual(['REP_FINANCIAL_DATA_FORM', 'HUB_STUDENT']);
        expect((again[0] as any).permissionCodes).toEqual(['REPORT_FINANCE', 'FINANCE']);
    });

    it('lists a hub\'s cards by its route, in the reader\'s language, filtered and with inactive rows as coming soon', () => {
        const lang$ = new BehaviorSubject<string>('en');
        let cards: any[] = [];
        service.hubCards('/reports/student?x=1', lang$).subscribe(c => (cards = c));
        httpMock.expectOne(url).flush(catalog);

        expect(cards.map(c => c.reportCode)).toEqual(['REP_ENROLLMENT', 'REP_STUDENT_LIST', 'REP_DOC_ID_CARD']);
        expect(cards[0].implemented).withContext('inactive → coming soon').toBeFalse();
        expect(cards[1].title).toBe('REP_STUDENT_LIST en');
        expect(cards[1].titleAr).toBe('REP_STUDENT_LIST ar');
        expect(cards[2].queryParams).toEqual({ doc: 'id-card' });

        lang$.next('ar');
        expect(cards[1].title).toBe('REP_STUDENT_LIST ar');
        expect(cards[1].description).toBe('REP_STUDENT_LIST desc ar');

        let none: any[] = [];
        service.hubCards('/reports/nowhere', lang$).subscribe(c => (none = c));
        expect(none).toEqual([]);
    });

    it('finds the official documents by their doc= parameter', () => {
        let docs: any[] = [];
        service.documentCards(new BehaviorSubject<string>('en')).subscribe(d => (docs = d));
        httpMock.expectOne(url).flush(catalog);
        expect(docs.map(d => d.kind)).toEqual(['id-card']);
        expect(docs[0].icon).toBe('pi-file');
    });

    it('is an empty catalogue when the request fails', () => {
        let cards: unknown[] | null = null;
        service.landingCards().subscribe(c => (cards = c));
        httpMock.expectOne(url).flush({}, { status: 500, statusText: 'Server Error' });
        expect(cards).toEqual([]);
    });

    it('parses query strings', () => {
        expect(parseQueryParams('doc=id-card')).toEqual({ doc: 'id-card' });
        expect(parseQueryParams('?a=1&b=x%20y')).toEqual({ a: '1', b: 'x y' });
        expect(parseQueryParams('')).toBeNull();
        expect(parseQueryParams(null)).toBeNull();
    });
});
