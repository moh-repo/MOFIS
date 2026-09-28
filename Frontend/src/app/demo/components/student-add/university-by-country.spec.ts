import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { LookupItem, LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { StudentAddComponent } from './student-add.component';

/**
 * Live QA M10 (25 Sep 2026): for a Jordan student the wizard offered Egypt's universities.
 * They loaded only after a governorate and, when nothing matched it, fell back to every
 * university. The wizard now asks for the chosen country's universities (the server
 * filters), narrows them by governorate, and an empty answer stays empty.
 */
describe('Add Student — universities of the chosen study country (M10)', () => {

    describe('the lookup', () => {
        let service: LookupService;
        let http: HttpTestingController;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [
                    provideHttpClient(), provideHttpClientTesting(), MessageService,
                    { provide: TranslationService, useValue: { currentLanguage$: of('en'), getCurrentLanguage: () => 'en', translate: (k: string) => k } }
                ]
            });
            service = TestBed.inject(LookupService);
            http = TestBed.inject(HttpTestingController);
        });

        afterEach(() => http.verify());

        it('asks the server for the country (and governorate) and keeps its answer', () => {
            let unis: LookupItem[] = [];
            service.getStudyUniversitiesOfCountry(1004, 7).subscribe(u => unis = u);
            const req = http.expectOne(r => r.url.includes('study-university-in?countryId=1004&govId=7'));
            req.flush({ content: [{ id: 999, studyUniversityInName: 'غير محدد', studyUniversityInNameEn: 'Not Specified' }], totalElements: 1 });
            expect(unis.map(u => u.value)).toEqual([999]);
        });

        it('shows no governorate of another country when none matches', () => {
            let govs: LookupItem[] = [];
            service.getStudyGovernoratesInByCountry(1009).subscribe(g => govs = g);
            http.expectOne(r => r.url.includes('study-gov-in')).flush({
                content: [{ id: 1, studyGovInName: 'القاهرة', studyCountryInId: 1 }], totalElements: 1
            });
            expect(govs).toEqual([]);
        });
    });

    describe('the wizard', () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const wizard = (asked: any[]): any => ({
            student: { studyCountryIn: 1004, syudyGovIn: 0 },
            lookupService: {
                getStudyGovernoratesInByCountry: () => of([]),
                getStudyUniversitiesOfCountry: (country: number, gov?: number | null) => { asked.push([country, gov ?? null]); return of([{ value: 999 }]); }
            },
            refreshAmountForCountry: () => undefined
        });

        it('loads the country\'s universities as soon as the country is chosen', () => {
            const asked: unknown[] = [];
            const self = wizard(asked);
            (StudentAddComponent.prototype as any).onStudyCountryInChange.call(self);
            expect(asked).toEqual([[1004, null]]);
            expect(self.studyUniversityInOptions).toEqual([{ value: 999 }]);
        });

        it('narrows them to a governorate, and back to the country when it is cleared', () => {
            const asked: unknown[] = [];
            const self = wizard(asked);
            self.student.syudyGovIn = 7;
            (StudentAddComponent.prototype as any).onStudyGovInChange.call(self);
            self.student.syudyGovIn = 0;
            (StudentAddComponent.prototype as any).onStudyGovInChange.call(self);
            expect(asked).toEqual([[1004, 7], [1004, null]]);
        });
    });
});
