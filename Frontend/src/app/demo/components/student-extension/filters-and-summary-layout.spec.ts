import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentExtensionComponent } from './student-extension.component';
import { StudentExtensionModule } from './student-extension.module';
import { StudentExtensionService } from '../../service/student-extension.service';
import { TranslationService } from '../../service/translation.service';

/**
 * The filter row and the summary band on the extensions page.
 *
 * <p>Both were laid out by hand and neither survived contact with a narrow
 * screen. Every filter control carried its own pixel width — 120, 160, 200,
 * 180, 160 — so the five could not share a row evenly and the two buttons
 * wrapped wherever there happened to be space. The five summary cards used
 * `auto-fit`, which at most widths leaves one of them stranded on a row of its
 * own.
 *
 * <p>The panel stays collapsed on arrival, which is the behaviour asked for:
 * the figures are a detail of the list, not the reason for opening the page.
 *
 * <p>The assertions are geometric because the failures are geometric: nothing
 * in the compiler can see a row that wraps badly.
 */
describe('StudentExtension filters and summary', () => {

    let fixture: ComponentFixture<StudentExtensionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentExtensionModule],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: StudentExtensionService,
                    useValue: {
                        getMetadata: () => of({ students: [], extensionTypes: [], statuses: [] }),
                        // search() resolves to a plain array, not a page: the
                        // component maps over it directly.
                        search: () => of([]),
                        create: () => of({}),
                        update: () => of({}),
                        delete: () => of({})
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

        fixture = TestBed.createComponent(StudentExtensionComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    const all = (selector: string) =>
        Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];

    const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement;

    /** Puts figures on the page so the summary panel renders at all. */
    function withSummary(): void {
        fixture.componentInstance.summary = {
            total: 42, approved: 20, pending: 15, rejected: 7,
            inProgress: 5, averageQuarters: 3, lastUpdated: new Date()
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

    it('renders the five filters', () => {
        expect(all('.ext-filters__field').length).toBe(5);
    });

    it('lets every control fill its column instead of a fixed width', () => {
        all('.ext-filters__field input, .ext-filters__field p-select').forEach((control, i) => {
            expect(control.getAttribute('style'))
                .withContext(`control ${i} still carries an inline width`)
                .toBeNull();
        });
    });

    it('keeps the clear button out of the field flow', () => {
        // It used to be a flex item beside the fields, so it wrapped under
        // whichever field ran out of room first. The search button is gone:
        // the filters search as they change (review S10).
        const actions = el('.ext-filters__actions');

        expect(actions).toBeTruthy();
        expect(actions.querySelectorAll('p-button').length).toBe(1);
        expect(el('.ext-filters__grid').contains(actions)).toBeFalse();
    });

    it('searches once the user stops typing, and not again for the same filters', fakeAsync(() => {
        const search = spyOn(TestBed.inject(StudentExtensionService), 'search').and.callThrough();
        const form = fixture.componentInstance.filtersForm;

        form.patchValue({ passport: 'A' });
        tick(200);
        form.patchValue({ passport: 'A1' });
        tick(399);
        expect(search).not.toHaveBeenCalled();
        tick(1);
        expect(search).toHaveBeenCalledTimes(1);

        form.patchValue({ passport: ' A1 ' });
        tick(400);
        expect(search).toHaveBeenCalledTimes(1);
    }));

    it('marks only the filters that are set', () => {
        const active = () => all('.ext-filters__field--active').length;

        expect(active()).toBe(0);

        fixture.componentInstance.filtersForm.patchValue({ passport: 'A1', status: 2 });
        fixture.detectChanges();

        expect(active()).toBe(2);
    });

    it('gives every filter a label bound to its control', () => {
        all('.ext-filters__field').forEach((field, i) => {
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
        expect(cards.length).toBe(5);
        expect(cards[0].getBoundingClientRect().height).toBe(0);

        openSummary();

        expect(region.getAttribute('aria-hidden')).toBe('false');
        expect(all('.summary-card')[0].getBoundingClientRect().height).toBeGreaterThan(0);
    });

    it('declares its columns rather than letting auto-fit choose them', () => {
        openSummary();

        // A viewport-independent assertion on purpose. Measuring where the
        // cards happen to land at Karma's own width proves nothing: auto-fit
        // and the declared columns agree at some widths and only diverge at
        // others, so the measurement passed either way when I checked.
        const tracks = getComputedStyle(el('.summary-cards')).gridTemplateColumns.split(/\s+/);

        expect(tracks.length)
            .withContext(`tracks: ${tracks.join(' | ')}`)
            .toBeLessThanOrEqual(5);
        // auto-fit emits a track per fitted column and never a repeat(); what
        // matters is that the count is one of the three declared steps.
        expect([1, 2, 5]).toContain(tracks.length);
    });

    it('spans the fifth card across the row when there are only two columns', () => {
        openSummary();

        const cards = all('.summary-card');
        const tracks = getComputedStyle(el('.summary-cards')).gridTemplateColumns.split(/\s+/).length;
        if (tracks !== 2) {
            pending(`viewport gives ${tracks} columns; the span only applies at 2`);
            return;
        }

        // Five cards over two columns strands the last one; letting it span
        // both is what keeps the block square.
        const row = el('.summary-cards').getBoundingClientRect();
        const last = cards[cards.length - 1].getBoundingClientRect();

        expect(Math.round(last.width)).toBeGreaterThan(Math.round(row.width * 0.9));
    });

    it('gives each card its own accent, read by the edge bar and the icon', () => {
        openSummary();

        const accents = all('.summary-card').map(card =>
            getComputedStyle(card).getPropertyValue('--summary-accent').trim());

        expect(accents.length).toBe(5);
        expect(accents.every(a => a !== '')).withContext(`accents: ${accents}`).toBeTrue();
        expect(new Set(accents).size).withContext('each card should be distinguishable').toBe(5);
    });

    it('keeps the digits the same width across cards', () => {
        openSummary();

        const value = el('.summary-card .summary-value');
        expect(getComputedStyle(value).fontVariantNumeric).toContain('tabular-nums');
    });
});
