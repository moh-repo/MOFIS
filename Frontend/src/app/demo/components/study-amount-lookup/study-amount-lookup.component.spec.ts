import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { StudyAmountLookupComponent } from './study-amount-lookup.component';
import { SettingsModule } from '../settings.module';
import { StudyAmountLookupApiService } from '../../service/study-amount-lookup.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';

/**
 * The amount rules are ids — degree, agency type, currency — and the page reads
 * them as words.
 *
 * <p>Every one of the 18 rules on the dev database is in currency 1009, the
 * seventeenth row of STUDY_CURRENCY_IN. The lookup arrived ten rows long, so the
 * amount column read "150 1009" and the dialog could not offer that currency at
 * all. The fetch is fixed in LookupService (lookup-paging.spec.ts); this pins
 * what the screen shows.
 */
describe('StudyAmountLookupComponent', () => {

    let fixture: ComponentFixture<StudyAmountLookupComponent>;
    let component: StudyAmountLookupComponent;

    const rules = [
        { id: 1, studentDegreeId: 100, studentAgencyTypeId: 102, amount: 150, currencyId: 1009, studyCountryInId: 1 },
        { id: 2, studentDegreeId: 101, studentAgencyTypeId: 100, amount: 200, currencyId: 1011, studyCountryInId: null }
    ];

    const currencies = [
        { id: 1, value: 1, name: 'US Dollar', label: 'US Dollar' },
        { id: 1009, value: 1009, name: 'Omani Rial (OMR)', label: 'Omani Rial (OMR)' },
        { id: 1011, value: 1011, name: 'Euro', label: 'Euro' }
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SettingsModule, RouterModule.forRoot([])],
            providers: [
                { provide: StudyAmountLookupApiService, useValue: { getAll: () => of(rules) } },
                {
                    provide: LookupService,
                    useValue: {
                        getStudentDegrees: () => of([
                            { id: 100, value: 100, name: 'Bachelor' }, { id: 101, value: 101, name: 'Master' }
                        ]),
                        getAgencyTypes: () => of([
                            { id: 100, value: 100, name: 'Government ministry' },
                            { id: 102, value: 102, name: 'Private sector' }
                        ]),
                        getStudyCurrencies: () => of(currencies),
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

        fixture = TestBed.createComponent(StudyAmountLookupComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    it('names the currency of every rule instead of printing its id', () => {
        const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
        expect(text).toContain('Omani Rial (OMR)');
        expect(text).toContain('Euro');

        const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody tr td'))
            .map((td: any) => (td.textContent ?? '').trim());
        expect(cells).withContext('a bare currency id is the bug this test exists for').not.toContain('1009');
    });

    it('names the degree and the agency type as well', () => {
        const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
        expect(text).toContain('Private sector');
        expect(text).toContain('Bachelor');
    });

    it('offers every currency and agency type the lookups hold', () => {
        expect(component.currencyOptions.map(c => c.value)).toEqual([1, 1009, 1011]);
        expect(component.agencyTypeOptions.map(a => a.value)).toEqual([100, 102]);
    });

    it('keeps the id only when the lookup really has no such row', () => {
        expect(component.currencyLabel(4242)).toBe('4242');
        expect(component.currencyLabel(undefined)).toBe('');
    });
});
