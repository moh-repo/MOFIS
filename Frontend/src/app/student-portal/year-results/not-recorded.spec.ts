import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { YearResultsComponent } from './year-results.component';
import { YEAR_OUTCOME_KEY, yearOutcome } from '../../demo/api/student-year-result';
import { AppModule } from '../../app.module';
import { StudentPortalModule } from '../student-portal.module';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { MyDataComponent } from '../my-data/my-data.component';

/**
 * FIX-PLAN P5-8, decision D24-a (26 Sep 2026): a seeded placeholder year (no academic
 * year, no grade, marked passed) reads "Not recorded", not "Passed", and the portal's
 * passed / failed filter leaves it out of both.
 */
describe('Year results — a placeholder year is not recorded', () => {

    const placeholder = { yearNumber: 3, passed: true, recorded: false };
    const passed = { yearNumber: 1, passed: true, recorded: true, academicYear: '2023/2024' };
    const failed = { yearNumber: 2, passed: false, recorded: true };

    it('reads not recorded, passed or failed; a row without the flag reads as before', () => {
        expect(yearOutcome(placeholder)).toBe('not-recorded');
        expect(yearOutcome(passed)).toBe('passed');
        expect(yearOutcome(failed)).toBe('failed');
        expect(yearOutcome({ passed: true })).toBe('passed');
        expect(YEAR_OUTCOME_KEY['not-recorded']).toBe('STUDENT_QUICK_EDIT.YEAR_RESULTS.NOT_RECORDED');
    });

    it('the portal page names it and leaves it out of the passed and failed filters', () => {
        const page = Object.create(YearResultsComponent.prototype) as YearResultsComponent;
        page.results = [passed, failed, placeholder] as any;
        page.search = '';

        expect(page.outcomeKey(placeholder as any)).toBe('STUDENT_PORTAL.NOT_RECORDED');
        expect(page.outcomeColor(placeholder as any)).toBe('default');

        page.outcomeFilter = 'passed';
        expect(page.filteredResults.map(r => r.yearNumber)).toEqual([1]);
        page.outcomeFilter = 'failed';
        expect(page.filteredResults.map(r => r.yearNumber)).toEqual([2]);
        page.outcomeFilter = null;
        expect(page.filteredResults.length).toBe(3);
    });
});

/**
 * Live QA R5-06 (round 5): every Year results load asked for
 * `assets/fill/minus-circle.svg`, got a 404 and logged "icon not registered" once
 * per placeholder card. The page picks `minus-circle` for a placeholder year, and
 * AppModule's icon list did not carry it. The placeholder was not grey either, as
 * D24-a asks: its tag was drawn in ink on both pages, and My data marked the row
 * green, like a passed year.
 *
 * <p>Built on AppModule, so the icons are the ones the application registers,
 * not a list written for the test.
 */
describe('A placeholder year on the portal pages', () => {

    const placeholder = { yearNumber: 3, passed: true, recorded: false };
    const passed = { yearNumber: 1, passed: true, recorded: true, academicYear: '2023/2024' };
    const failed = { yearNumber: 2, passed: false, recorded: true };

    /** `--p-muted`'s fallback; the pages are rendered here without the shell. */
    const GREY = 'rgb(100, 116, 139)';

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AppModule, StudentPortalModule],
            providers: [
                provideHttpClientTesting(),
                {
                    provide: StudentPortalApiService,
                    useValue: {
                        getYearResults: () => of([passed, failed, placeholder]),
                        getMyData: () => of({ student: null, finance: null }),
                        getLookups: () => of({}),
                        getProgress: () => of(null)
                    }
                },
                {
                    provide: TranslationService,
                    useValue: {
                        initialize: () => Promise.resolve(true), currentLanguage$: of('en'), initialized$: of(true),
                        isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();
    });

    /**
     * Renders a page and lets the icons draw, which takes a promise. Not
     * `whenStable`: AppModule's own timers keep the zone busy for good.
     */
    async function render<T>(page: Type<T>): Promise<ComponentFixture<T>> {
        const fixture = TestBed.createComponent(page);
        fixture.detectChanges();
        await new Promise(resolve => setTimeout(resolve));
        fixture.detectChanges();
        return fixture;
    }

    it('Year results draws every outcome\'s icon from the registered set, and fetches none', async () => {
        const fixture = await render(YearResultsComponent);

        const fetched = TestBed.inject(HttpTestingController).match(req => req.url.endsWith('.svg'));
        expect(fetched.map(req => req.request.url)).toEqual([]);

        const icons = Array.from(fixture.nativeElement.querySelectorAll('.yr-card-icon')) as HTMLElement[];
        expect(icons.length).toBe(3);
        icons.forEach((icon, i) => expect(icon.querySelector('svg')).withContext(`card ${i + 1}`).not.toBeNull());
    });

    it('Year results draws the placeholder year grey: its icon and its tag', async () => {
        const fixture = await render(YearResultsComponent);

        const card = fixture.nativeElement.querySelectorAll('.yr-card')[2] as HTMLElement;
        expect(getComputedStyle(card.querySelector('.yr-card-icon')!).color).toBe(GREY);
        expect(getComputedStyle(card.querySelector('nz-tag')!).color).toBe(GREY);
    });

    it('My data marks the placeholder year grey, not green like a passed one', async () => {
        const fixture = await render(MyDataComponent);

        const rows = Array.from(fixture.nativeElement.querySelectorAll('.md-years__row')) as HTMLElement[];
        expect(rows.length).toBe(3);
        expect(getComputedStyle(rows[0]).borderInlineStartColor).withContext('passed').toBe('rgb(31, 138, 83)');
        expect(getComputedStyle(rows[2]).borderInlineStartColor).withContext('placeholder').toBe(GREY);
        expect(getComputedStyle(rows[2].querySelector('nz-tag')!).color).toBe(GREY);
    });
});
