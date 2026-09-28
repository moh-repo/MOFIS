import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { LookupItem, LookupService } from './lookup.service';
import { TranslationService } from './translation.service';

/**
 * A lookup dropdown must offer the whole lookup.
 *
 * <p>`/api/lookup-tables/*` is paged and defaults to ten rows, so every list
 * fetched without a size arrived with ten of whatever it held. With 444 majors
 * in the database, the study-years screen resolved none of its rows to a name
 * and its add dialog offered ten of the majors a rule could be written for.
 * The cap on one page is 500 (`LookupTablePageUtil.MAX_PAGE_SIZE`), so a bigger
 * `size` is not an answer either — a table above the cap is read page by page.
 */
describe('LookupService paging', () => {

    let service: LookupService;
    let http: HttpTestingController;

    const base = '/api/lookup-tables/';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MessageService,
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        });
        service = TestBed.inject(LookupService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    /** The one request a lookup makes when everything fits on a page. */
    function expectPage(path: string, page: number) {
        return http.expectOne(r => r.url.includes(base + path) && r.url.includes(`page=${page}`) && r.url.includes('size=500'));
    }

    it('asks for a full page, not the default ten', () => {
        let majors: LookupItem[] = [];
        service.getStudentMajors().subscribe(m => majors = m);

        expectPage('student-major', 0).flush({
            content: [{ studentMajorId: 7, studentMajorName: 'هندسة', studentMajorNameEn: 'Engineering' }],
            totalElements: 1
        });

        expect(majors.map(m => m.value)).toEqual([7]);
        expect(majors.map(m => m.name)).toEqual(['Engineering']);
    });

    it('reads the pages that follow when the lookup is larger than one page', () => {
        let majors: LookupItem[] = [];
        service.getStudentMajors().subscribe(m => majors = m);

        const first = Array.from({ length: 500 }, (_, i) => ({ studentMajorId: i + 1, studentMajorName: 'م' + (i + 1) }));
        expectPage('student-major', 0).flush({ content: first, totalElements: 640 });
        // 640 rows is two pages, and the second is asked for only because the
        // first said so — nothing here guesses at a page count.
        expectPage('student-major', 1).flush({
            content: Array.from({ length: 140 }, (_, i) => ({ studentMajorId: 501 + i, studentMajorName: 'م' + (501 + i) })),
            totalElements: 640
        });

        expect(majors.length).toBe(640);
        expect(majors[639].value).toBe(640);
    });

    it('reads degrees the same way, and keeps caching them', () => {
        let degrees: LookupItem[] = [];
        service.getStudentDegrees().subscribe(d => degrees = d);
        expectPage('student-degree', 0).flush({
            content: [{ studentDegreeId: 3, studentDegreeName: 'ماجستير', studentDegreeNameEn: 'Master' }],
            totalElements: 1
        });
        expect(degrees.map(d => d.name)).toEqual(['Master']);

        // The second caller is served from the cache: no second request to verify.
        let again: LookupItem[] = [];
        service.getStudentDegrees().subscribe(d => again = d);
        expect(again.map(d => d.value)).toEqual([3]);
    });

    it('reads the whole currency list, which is what the study-amount rules are priced in', () => {
        let currencies: LookupItem[] = [];
        service.getStudyCurrencies().subscribe(c => currencies = c);

        // The seventeenth row is the one every amount rule uses; a default page
        // of ten never reached it and the column printed 1009.
        expectPage('study-currency-in', 0).flush({
            content: [
                { studyCurrencyInId: 1, studyCurrencyInName: 'الدولار', studyCurrencyInNameEn: 'US Dollar' },
                { studyCurrencyInId: 1009, studyCurrencyInName: 'ريال عماني', studyCurrencyInNameEn: 'Omani Rial (OMR)' }
            ],
            totalElements: 2
        });

        expect(currencies.map(c => c.value)).toEqual([1, 1009]);
        expect(currencies.find(c => c.value === 1009)?.name).toBe('Omani Rial (OMR)');
    });

    it('asks for every agency type, so the eleventh can be chosen', () => {
        let types: LookupItem[] = [];
        service.getAgencyTypes().subscribe(t => types = t);
        expectPage('student-agency-type', 0).flush({
            content: Array.from({ length: 11 }, (_, i) => ({
                studentAgencyTypeId: i + 1, studentAgencyTypeName: 'ج' + (i + 1)
            })),
            totalElements: 11
        });
        expect(types.length).toBe(11);
    });

    /*
     * Live QA N8 (22 Sep): the Add Student form offered 10 of 18 countries, 10 of 21
     * levels, 10 of 16 university types, 10 of 22 governorates, 10 of 173
     * municipalities and 10 of 16 study countries, and the portal printed raw ids
     * for anything past the tenth. Each of those getters now reads the whole table.
     */
    const fullPageGetters: Array<[string, (s: LookupService) => any]> = [
        ['country', s => s.getCountries()],
        ['student-current-level', s => s.getCurrentLevels()],
        ['study-status', s => s.getStudyStatuses()],
        ['student-university-type', s => s.getUniversityTypes()],
        ['governorate', s => s.getGovernments()],
        ['municipality', s => s.getMunicipalities()],
        ['study-major-in', s => s.getStudyMajorsIn()],
        ['study-country-in', s => s.getStudyCountriesIn()],
        ['student-ministry-type', s => s.getMinistryTypes()],
        ['student-study-type', s => s.getStudentStudyTypes()],
        ['student-gender', s => s.getStudentGenders()]
    ];
    for (const [path, call] of fullPageGetters) {
        it(`reads the whole ${path} list`, () => {
            call(service).subscribe();
            expectPage(path, 0).flush({ content: [], totalElements: 0 });
        });
    }

    it('reads every municipality of a governorate, not the first ten', () => {
        service.getMunicipalitiesByGovernment(4, 1).subscribe();
        http.expectOne(r => r.url.includes(base + 'municipality?govId=4&countryId=1')
            && r.url.includes('page=0') && r.url.includes('size=500')).flush([]);
    });

    it('survives a lookup that answers with a bare array and no page metadata', () => {
        let majors: LookupItem[] = [];
        service.getStudentMajors().subscribe(m => majors = m);
        expectPage('student-major', 0).flush([{ studentMajorId: 2, studentMajorName: 'طب' }]);
        expect(majors.map(m => m.value)).toEqual([2]);
    });
});
