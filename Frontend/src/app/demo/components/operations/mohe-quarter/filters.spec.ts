import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { MoheQuarterComponent } from './mohe-quarter.component';
import { OperationsModule } from '../operations.module';
import { MoheQuarterService } from '../../../service/mohe-quarter.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';

/**
 * The quarters page had one filter, a search box inside the table caption,
 * which read as part of the table rather than as a control over it. It now has
 * a filter bar with search, year and quarter — the two option lists were
 * already loaded for the dialog form, and the table filters client-side, so
 * neither addition costs a request.
 *
 * <p>Layout is covered centrally in src/app/filter-bar-shared.spec.ts. What is
 * pinned here is the wiring, which is new code: the bar renders before
 * &lt;p-table&gt;, so the table is reached through &#64;ViewChild rather than a
 * forward template reference.
 *
 * <p>fakeAsync because PrimeNG's Table.filter() stores the constraint and
 * defers the pass by filterDelay (300ms), rather than filtering when called.
 */
describe('MoheQuarter filters', () => {

    let fixture: ComponentFixture<MoheQuarterComponent>;
    let component: MoheQuarterComponent;

    const FILTER_DELAY = 350;

    const quarters = [
        { id: 1, quarterYear: 2025, quarterNumber: 1, quarterName: 'First 2025', notes: 'alpha' },
        { id: 2, quarterYear: 2025, quarterNumber: 2, quarterName: 'Second 2025', notes: 'beta' },
        { id: 3, quarterYear: 2026, quarterNumber: 1, quarterName: 'First 2026', notes: 'gamma' }
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OperationsModule],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: MoheQuarterService, useValue: { getAll: () => of(quarters), currentCreateJob: () => of(null) } },
                {
                    provide: LookupService,
                    useValue: {
                        getAcademicYears: () => of([
                            { label: '2025', value: '2025' },
                            { label: '2026', value: '2026' }
                        ]),
                        getQuarterNumbers: () => of([
                            { label: 'Q1', value: 1 },
                            { label: 'Q2', value: 2 }
                        ]),
                        getStudyCountriesIn: () => of([])
                    }
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(MoheQuarterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    const shown = () =>
        Array.from(fixture.nativeElement.querySelectorAll('tbody tr'))
            .map(row => (row as HTMLElement).textContent ?? '')
            .filter(text => text.trim().length > 0);

    const settle = () => {
        tick(FILTER_DELAY);
        fixture.detectChanges();
    };

    it('finds the table through ViewChild', () => {
        expect(component.table).toBeTruthy();
    });

    /* The Year filter offers the years that actually have a quarter, newest
       first — not the academic-years lookup, which lists every year the system
       knows about and so mostly filtered the table down to nothing. */
    it('offers only the years present in the loaded quarters, newest first', () => {
        expect(component.filterYearOptions.map(o => o.value)).toEqual([2026, 2025]);
        expect(component.filterYearOptions.map(o => o.label)).toEqual(['2026', '2025']);
    });

    it('keeps the dialog form on the academic-years lookup', () => {
        expect(component.quarterYearOptions.map(o => o.value)).toEqual([2025, 2026]);
    });

    it('drops a selected year that disappears on reload', fakeAsync(() => {
        component.filterYear = 2026;
        component.applyYearFilter();
        settle();
        expect(shown().length).toBe(1);

        const service = TestBed.inject(MoheQuarterService) as any;
        service.getAll = () => of(quarters.filter(q => q.quarterYear !== 2026));
        component.loadQuarters();
        settle();

        expect(component.filterYear).toBeNull();
        expect(component.filterYearOptions.map(o => o.value)).toEqual([2025]);
        expect(shown().length).toBe(2);
    }));

    it('starts with every quarter on screen', () => {
        expect(shown().length).withContext(`rows: ${JSON.stringify(shown())}`).toBe(3);
    });

    it('narrows the list by year', fakeAsync(() => {
        component.filterYear = 2026;
        component.applyYearFilter();
        settle();

        expect(shown().length).toBe(1);
        expect(shown()[0]).toContain('2026');
    }));

    it('narrows the list by quarter number', fakeAsync(() => {
        component.filterQuarterNumber = 1;
        component.applyQuarterFilter();
        settle();

        expect(shown().length).toBe(2);
    }));

    it('narrows the list by free text', fakeAsync(() => {
        component.applyGlobalFilter('beta');
        settle();

        expect(shown().length).toBe(1);
        expect(component.filterGlobal).toBe('beta');
    }));

    it('applies year and quarter together', fakeAsync(() => {
        component.filterYear = 2025;
        component.applyYearFilter();
        settle();
        component.filterQuarterNumber = 2;
        component.applyQuarterFilter();
        settle();

        expect(shown().length).toBe(1);
        expect(shown()[0]).toContain('Second');
    }));

    it('puts everything back when the filters are cleared', fakeAsync(() => {
        component.filterYear = 2026;
        component.applyYearFilter();
        settle();
        component.applyGlobalFilter('gamma');
        settle();
        expect(shown().length).toBeLessThan(3);

        component.clearFilters();
        settle();

        expect(shown().length).toBe(3);
        expect(component.filterGlobal).toBe('');
        expect(component.filterYear).toBeNull();
        expect(component.filterQuarterNumber).toBeNull();
    }));

    it('keeps the chosen sort when the filters are cleared', fakeAsync(() => {
        // Clearing resets each constraint individually rather than calling the
        // table's clear(), which also drops sortField and would silently
        // reorder the list under the user.
        component.table!.sortField = 'quarterYear';
        component.table!.sortOrder = -1;

        component.filterYear = 2026;
        component.applyYearFilter();
        settle();
        component.clearFilters();
        settle();

        expect(component.table!.sortField).toBe('quarterYear');
        expect(component.table!.sortOrder).toBe(-1);
    }));
});
