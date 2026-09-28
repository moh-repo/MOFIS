import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { WafarReconciliationComponent } from './wafar-reconciliation.component';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';

/**
 * The reconciliation summary.
 *
 * <p>It was three bare nz-cards on a col-4 grid, each holding an nz-statistic
 * and nothing else: no icon, no accent, and nothing but the value colour to
 * tell one from another. They now use the same summary band as the PrimeNG
 * pages, so the one ng-zorro screen in this set does not look like a different
 * application, and they sit in a section that starts collapsed.
 *
 * <p>Layout is covered centrally in src/app/summary-cards-shared.spec.ts. What
 * is pinned here belongs to this page: that the section starts closed, that
 * three cards get three declared columns, and that a discrepancy reads as a
 * warning while the other two do not.
 */
describe('WafarReconciliation summary', () => {

    let fixture: ComponentFixture<WafarReconciliationComponent>;
    let component: WafarReconciliationComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [WafarReconciliationComponent],
            providers: [
                {
                    provide: HttpClient,
                    useValue: {
                        get: () => of({ content: [], totalElements: 0 }),
                        post: () => of({})
                    }
                },
                {
                    provide: LookupService,
                    useValue: new Proxy({} as any, { get: () => () => of([]) })
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

        fixture = TestBed.createComponent(WafarReconciliationComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        component.summary = { MATCHED: 18, DISCREPANCY: 3, RESOLVED: 5 };
        fixture.detectChanges();

        // Attached to the document, or nothing has a computed layout at all.
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => fixture.nativeElement.remove());

    const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement;
    const all = (selector: string) =>
        Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];

    /**
     * Opens the summary section.
     *
     * p-panel is OnPush, so assigning `collapsed` imperatively never marks it
     * dirty. Clicking the toggler is both what a user does and what schedules
     * the check. The expand is animated, so the animations are finished before
     * anything is measured — collapsed content has no layout box at all.
     */
    function openSummary(): void {
        (fixture.nativeElement.querySelector('.p-panel-header button') as HTMLElement).click();
        fixture.detectChanges();

        (fixture.nativeElement as HTMLElement)
            .getAnimations({ subtree: true })
            .forEach(animation => animation.finish());
        fixture.detectChanges();
    }

    it('keeps the figures behind a collapsed section until asked', () => {
        const region = fixture.nativeElement.querySelector('[role="region"]') as HTMLElement;
        expect(region.getAttribute('aria-hidden'))
            .withContext('the section should start closed').toBe('true');

        const cards = all('.summary-card');
        expect(cards.length).toBe(3);
        expect(cards[0].getBoundingClientRect().height).toBe(0);

        openSummary();

        expect(region.getAttribute('aria-hidden')).toBe('false');
        expect(all('.summary-card')[0].getBoundingClientRect().height).toBeGreaterThan(0);
    });

    it('uses the shared summary band rather than bare nz-cards', () => {
        openSummary();

        const grid = el('.summary-cards');
        expect(grid).toBeTruthy();
        expect(grid.classList).toContain('summary-cards--cols-3');
        expect(grid.querySelector('nz-statistic'))
            .withContext('the nz-statistics should be gone').toBeNull();
    });

    it('puts the three cards on one row of three declared columns', () => {
        openSummary();

        const tracks = getComputedStyle(el('.summary-cards')).gridTemplateColumns.split(/\s+/);
        expect([1, 2, 3]).withContext(`tracks: ${tracks.join(' | ')}`).toContain(tracks.length);
    });

    it('gives every card an icon, which none of them had before', () => {
        openSummary();

        all('.summary-card').forEach((card, i) => {
            expect(card.querySelector('.summary-icon i'))
                .withContext(`card ${i} has no icon`).toBeTruthy();
        });
    });

    it('gives each figure its own accent', () => {
        openSummary();

        const accents = all('.summary-card').map(card =>
            getComputedStyle(card).getPropertyValue('--summary-accent').trim());

        expect(accents.every(a => a !== '')).withContext(`accents: ${accents}`).toBeTrue();
        expect(new Set(accents).size).withContext('each card should be distinguishable').toBe(3);
    });

    it('shows the discrepancy count in its warning colour', () => {
        openSummary();

        // A non-zero discrepancy count is the one number on this page that
        // means something needs doing; it should read as red before it is read
        // as a number. Compared against the edge bar rather than the custom
        // property, because the property returns the raw hex it was written as
        // while `color` resolves to rgb().
        const card = el('.summary-card.discrepancy');
        const value = card.querySelector('.summary-value') as HTMLElement;

        expect(getComputedStyle(value).color)
            .toBe(getComputedStyle(card, '::before').backgroundColor);
        expect(getComputedStyle(value).color)
            .not.toBe(getComputedStyle(el('.summary-card.matched'), '::before').backgroundColor);
    });

    it('renders the counts it was given', () => {
        openSummary();

        const valueOf = (cls: string) =>
            (el(`.summary-card.${cls} .summary-value`).textContent ?? '').trim();

        expect(valueOf('discrepancy')).toBe('3');
        expect(valueOf('matched')).toBe('18');
        expect(valueOf('resolved')).toBe('5');
    });
});
