import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { StudyYearsLookupComponent } from './study-years-lookup.component';
import { SettingsModule } from '../settings.module';
import { StudyYearsLookupApiService } from '../../service/study-years-lookup.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';

/**
 * The rules on this page are three ids — degree, major, attaché — and the page
 * exists to read them as words.
 *
 * <p>It resolved the names against lists it fetched from the paged lookup
 * endpoints without a size, so it held ten majors out of 444, and every rule
 * fell through to `String(id)`: 49 rows printing a major id and 42 a degree id
 * on the dev database. The fetch is fixed in LookupService (see
 * lookup-paging.spec.ts); this file pins what the screen must show.
 */
describe('StudyYearsLookupComponent', () => {

    let fixture: ComponentFixture<StudyYearsLookupComponent>;
    let component: StudyYearsLookupComponent;

    // A rule whose major is far down the lookup — the case that printed an id.
    const rules = [
        { id: 1, studentDegreeId: 3, studentMajorId: 412, studyYears: 5, studyCountryInId: 1 },
        { id: 2, studentDegreeId: 3, studentMajorId: 7, studyYears: 4, studyCountryInId: null }
    ];

    const degrees = [
        { id: 3, value: 3, name: 'Master', label: 'Master' },
        { id: 4, value: 4, name: 'PhD', label: 'PhD' }
    ];
    const majors = [
        { id: 7, value: 7, name: 'Engineering', label: 'Engineering' },
        { id: 412, value: 412, name: 'Veterinary Medicine', label: 'Veterinary Medicine' }
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SettingsModule, RouterModule.forRoot([])],
            providers: [
                { provide: StudyYearsLookupApiService, useValue: { getAll: () => of(rules) } },
                {
                    provide: LookupService,
                    useValue: {
                        getStudentDegrees: () => of(degrees),
                        getStudentMajors: () => of(majors),
                        getAllStudyCountries: () => of([{ studyCountryInId: 1, studyCountryInName: 'Egypt' }])
                    }
                },
                { provide: HttpClient, useValue: { get: () => of([]) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                },
                {
                    provide: UmsAuthService,
                    useValue: { permissions$: of([]), isSuperAdmin: () => true, countryIds: [], isLoggedIn: () => true }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudyYearsLookupComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    it('prints the degree and major names, not the ids stored on the rule', () => {
        const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
        expect(text).toContain('Veterinary Medicine');
        expect(text).toContain('Engineering');
        expect(text).toContain('Master');

        const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody tr td'))
            .map((td: any) => (td.textContent ?? '').trim());
        expect(cells).withContext('a bare major id is the bug this test exists for').not.toContain('412');
        expect(cells).not.toContain('7');
    });

    it('offers every major the lookup holds, not the first page of it', () => {
        expect(component.majorOptions.map(m => m.value)).toEqual([7, 412]);
        expect(component.degreeOptions.map(d => d.value)).toEqual([3, 4]);
    });

    it('says a rule with no attaché is the shared default rather than showing a blank', () => {
        expect(component.countryLabel(null)).toBe('STUDY_LOOKUP.ALL_ATTACHES');
        expect(component.countryLabel(1)).toBe('Egypt');
    });

    it('falls back to the id only when the lookup really has no such row', () => {
        expect(component.majorLabel(999)).toBe('999');
        expect(component.degreeLabel(412)).toBe('412');
    });
});
