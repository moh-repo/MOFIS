import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentTravelTicketsComponent } from './student-travel-tickets.component';
import { StudentTravelTicketsModule } from './student-travel-tickets.module';
import { StudentTravelTicketService } from '../../service/student-travel-ticket.service';
import { StudentPickerService } from '../../service/student-picker.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { codeListStub } from '../../../testing/code-list.stub';

/**
 * The filter row and the summary band on the travel tickets page.
 *
 * <p>Both were laid out by hand and neither survived a narrow screen. The two
 * filter controls carried their own pixel widths — 200px and 180px — in a
 * wrapping flex row with the buttons as a third item, so the buttons landed
 * wherever there happened to be space. The four summary cards used `auto-fit`,
 * which strands whatever does not divide into the column count it picks.
 *
 * <p>The panel stays collapsed on arrival, which is the behaviour asked for:
 * the figures are a detail of the list, not the reason for opening the page.
 *
 * <p>The layout itself is covered once, centrally, in
 * src/app/filter-bar-shared.spec.ts and src/app/summary-cards-shared.spec.ts.
 * What is pinned here is what belongs to this page: that it uses those blocks
 * at all, declares the right number of columns for the number of cards it has,
 * and gives each card an accent of its own.
 */
describe('StudentTravelTickets filters and summary', () => {

    let fixture: ComponentFixture<StudentTravelTicketsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentTravelTicketsModule],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: StudentTravelTicketService,
                    useValue: {
                        // search() resolves to a plain array, not a page: the
                        // component maps over it directly.
                        search: () => of([]),
                        getById: () => of({}),
                        create: () => of({}),
                        update: () => of({}),
                        delete: () => of({}),
                        getFundingSources: () => of([]),
                        getWafarBalance: () => of({ balance: 0 })
                    }
                },
                {
                    provide: StudentPickerService,
                    useValue: { search: () => of([]), byIds: () => of([]), merge: () => [], mergeAll: () => [], toOptions: () => [] }
                },
                {
                    provide: LookupService,
                    useValue: new Proxy({} as any, { get: () => () => of([]) })
                },
                { provide: CodeListService, useValue: codeListStub() },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentTravelTicketsComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement;
    const all = (selector: string) =>
        Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];

    /** Puts figures on the page so the summary panel renders at all. */
    function withSummary(): void {
        // One card per travel type the table holds, between the total and the family members.
        fixture.componentInstance.summary = {
            total: 24,
            byType: [{ code: 'ALONE', label: 'Alone', count: 15 }, { code: 'WITH_FAMILY', label: 'With family', count: 9 }],
            totalFamilyMembers: 21, lastUpdated: new Date()
        };
        fixture.detectChanges();
    }

    /**
     * Opens the statistics panel and puts figures behind it.
     *
     * The panel is collapsed on arrival, and collapsed content has no layout
     * box at all — grid tracks come back as the unresolved `repeat(4, ...)`
     * rather than pixel widths, and every getBoundingClientRect is zero. So
     * anything measuring the cards has to open it first.
     */
    function openSummary(): void {
        withSummary();

        // p-panel is OnPush, so assigning `collapsed` imperatively never marks
        // it dirty and the content stays shut. Clicking the toggler is both
        // what a user does and what schedules the check.
        (fixture.nativeElement.querySelector('.p-panel-header button') as HTMLElement).click();
        fixture.detectChanges();

        // The expand is animated, so the content is still mid-transition when
        // detectChanges returns; finishing the animations settles it at its
        // final size before anything is measured.
        (fixture.nativeElement as HTMLElement)
            .getAnimations({ subtree: true })
            .forEach(animation => animation.finish());
        fixture.detectChanges();
    }

    // ───────────────────────────────────────────────────────────── filters ──

    it('uses the shared filter bar rather than a hand-rolled row', () => {
        expect(el('.filter-bar')).toBeTruthy();
        expect(all('.filter-bar__field').length).toBe(2);
    });

    it('lets every control fill its column instead of a fixed width', () => {
        all('.filter-bar__field p-select').forEach((control, i) => {
            // These carried [style]="{'width':'200px'}" and '180px'.
            expect(control.getAttribute('style'))
                .withContext(`control ${i} still carries an inline width`)
                .toBeNull();
        });
    });

    it('gives the student picker the wider column', () => {
        // It holds a name; the travel type holds a word.
        const wide = all('.filter-bar__field--wide');

        expect(wide.length).toBe(1);
        expect(wide[0].querySelector('label')?.getAttribute('for')).toBe('studentFilter');
    });

    it('keeps the clear button out of the field flow', () => {
        // No search button: the filters search as they change (review S10).
        const actions = el('.filter-bar__actions');

        expect(actions).toBeTruthy();
        expect(actions.querySelectorAll('p-button').length).toBe(1);
        expect(el('.filter-bar__grid').contains(actions)).toBeFalse();
    });

    it('searches after a filter changes, and clearing searches at once', fakeAsync(() => {
        const search = spyOn(TestBed.inject(StudentTravelTicketService), 'search').and.callThrough();
        const component = fixture.componentInstance;

        component.filtersForm.patchValue({ travelType: 'ALONE' });
        tick(399);
        expect(search).not.toHaveBeenCalled();
        tick(1);
        expect(search).toHaveBeenCalledTimes(1);

        component.clearFilters();
        expect(search).toHaveBeenCalledTimes(2);
        tick(400);
        expect(search).toHaveBeenCalledTimes(2);
    }));

    it('marks only the filters that are set', () => {
        const active = () => all('.filter-bar__field--active').length;

        expect(active()).toBe(0);

        fixture.componentInstance.filtersForm.patchValue({ travelType: 'ALONE' });
        fixture.detectChanges();

        expect(active()).toBe(1);
    });

    it('gives every filter a label bound to its control', () => {
        all('.filter-bar__field').forEach((field, i) => {
            const label = field.querySelector('label') as HTMLLabelElement;
            expect(label).withContext(`field ${i} has no label`).toBeTruthy();
            expect(label.htmlFor).withContext(`field ${i} label is not bound`).not.toBe('');
        });
    });

    // ───────────────────────────────────────────────────────────── summary ──

    it('keeps the statistics behind a collapsed panel until asked', () => {
        withSummary();

        // The panel's content region carries its own hidden state. Read that
        // rather than the toggler's aria-expanded, which PrimeNG puts on the
        // <p-button> host element and not on the <button> inside it.
        const region = fixture.nativeElement.querySelector('[role="region"]') as HTMLElement;
        expect(region.getAttribute('aria-hidden'))
            .withContext('the panel should start closed').toBe('true');

        // The cards are in the DOM either way — PrimeNG hides the content
        // rather than removing it — so the collapse shows up as the absence of
        // a layout box, not the absence of the elements.
        const cards = all('.summary-card');
        expect(cards.length).toBe(4);
        expect(cards[0].getBoundingClientRect().height).toBe(0);

        openSummary();

        expect(region.getAttribute('aria-hidden')).toBe('false');
        expect(all('.summary-card')[0].getBoundingClientRect().height).toBeGreaterThan(0);
    });

    it('declares four columns for its four cards', () => {
        openSummary();

        // Viewport-independent: measuring where the cards land at Karma's own
        // width proves nothing, because auto-fit and a declared count agree at
        // some widths and only diverge at others.
        const grid = el('.summary-cards');
        const tracks = getComputedStyle(grid).gridTemplateColumns.split(/\s+/);

        expect(grid.classList).toContain('summary-cards--cols-4');
        expect([1, 2, 4]).withContext(`tracks: ${tracks.join(' | ')}`).toContain(tracks.length);
    });

    it('gives each card its own accent', () => {
        openSummary();

        const accents = all('.summary-card').map(card =>
            getComputedStyle(card).getPropertyValue('--summary-accent').trim());

        expect(accents.length).toBe(4);
        expect(accents.every(a => a !== '')).withContext(`accents: ${accents}`).toBeTrue();
        expect(new Set(accents).size).withContext('each card should be distinguishable').toBe(4);
    });

    it('keeps the digits the same width across cards', () => {
        openSummary();

        const value = el('.summary-card .summary-value');
        expect(getComputedStyle(value).fontVariantNumeric).toContain('tabular-nums');
    });
});
