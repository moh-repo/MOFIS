import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { WafarComponent } from './wafar.component';
import { WafarModule } from './wafar.module';
import { WafarService } from '../../service/wafar.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';

/**
 * The balance summary on the wafar page.
 *
 * <p>It was a fourth hand-built copy of the same band of figures: four cards
 * on primeflex columns (col-12 / md:col-3), sitting loose above the filters
 * with no way to put them away. They then moved into a collapsible section that
 * started closed; review F10 took the collapse button out again, so the section
 * is always open.
 *
 * <p>Layout is covered centrally in src/app/summary-cards-shared.spec.ts. What
 * is pinned here belongs to this page: that the section is always open, that
 * it declares four columns for its four figures, and that an overdrawn balance
 * turns red — which is the only one of the four that carries a warning.
 */
describe('Wafar balance summary', () => {

    let fixture: ComponentFixture<WafarComponent>;
    let component: WafarComponent;

    const balance = {
        totalDeposits: 37160, totalSpent: 32400,
        currentBalance: 4760, pendingAmount: 11568
    };

    /** Any method returns an empty observable. */
    const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [WafarModule],
            providers: [
                MessageService,
                {
                    provide: WafarService,
                    useValue: new Proxy({} as any, {
                        get: (_t, prop) => prop === 'getBalanceSummary'
                            ? () => of(balance)
                            : () => of([])
                    })
                },
                { provide: LookupService, useValue: quiet() },
                { provide: HttpClient, useValue: { get: () => of({ content: [] }) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(WafarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        component.balance = { ...balance } as any;
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
     * Settles the summary before anything is measured. The section is always open
     * now (review F10); any entry animation is finished so the cards have their
     * final size.
     */
    function openSummary(): void {
        (fixture.nativeElement as HTMLElement)
            .getAnimations({ subtree: true })
            .forEach(animation => animation.finish());
        fixture.detectChanges();
    }

    it('shows the balance open, with no collapse button', () => {
        // Review F10: the toggle hid the figures and was taken out.
        expect(fixture.nativeElement.querySelector('.p-panel-header button'))
            .withContext('no toggler in the header').toBeNull();

        openSummary();

        const cards = all('.summary-card');
        expect(cards.length).toBe(4);
        expect(cards[0].getBoundingClientRect().height).toBeGreaterThan(0);
    });

    it('uses the shared summary band rather than primeflex columns', () => {
        openSummary();

        const grid = el('.summary-cards');
        expect(grid).toBeTruthy();
        expect(grid.classList).toContain('summary-cards--cols-4');
        expect(grid.querySelector('[class*="col-"]'))
            .withContext('the col-12/md:col-3 wrappers should be gone').toBeNull();
    });

    it('declares four columns for its four figures', () => {
        openSummary();

        const tracks = getComputedStyle(el('.summary-cards')).gridTemplateColumns.split(/\s+/);
        expect([1, 2, 4]).withContext(`tracks: ${tracks.join(' | ')}`).toContain(tracks.length);
    });

    it('gives each figure its own accent', () => {
        openSummary();

        const accents = all('.summary-card').map(card =>
            getComputedStyle(card).getPropertyValue('--summary-accent').trim());

        expect(accents.every(a => a !== '')).withContext(`accents: ${accents}`).toBeTrue();
        expect(new Set(accents).size).withContext('each card should be distinguishable').toBe(4);
    });

    it('turns the balance red when it is overdrawn', () => {
        openSummary();

        const card = () => el('.summary-card.balance');
        const accent = () => getComputedStyle(card()).getPropertyValue('--summary-accent').trim();
        const spent = getComputedStyle(el('.summary-card.spent'))
            .getPropertyValue('--summary-accent').trim();

        const healthy = accent();
        expect(card().classList).not.toContain('negative');
        expect(healthy).withContext('a positive balance is not a warning').not.toBe(spent);

        component.balance = { ...balance, currentBalance: -250 } as any;
        fixture.detectChanges();

        // An overdrawn balance is the one figure here that means something is
        // wrong, so it takes the same red as spending.
        expect(card().classList).toContain('negative');
        expect(accent()).toBe(spent);
    });

    it('keeps the value in the accent colour', () => {
        openSummary();

        // Unlike the other summary bands: green deposits against red spending
        // is the comparison these cards exist to make.
        //
        // Compared against the edge bar rather than the --summary-accent
        // custom property: the property comes back as the raw '#4CAF50' it was
        // written as, while `color` resolves to 'rgb(76, 175, 80)'. The bar is
        // painted from the same accent and resolves the same way.
        const card = el('.summary-card.deposits');
        const value = card.querySelector('.summary-value') as HTMLElement;

        expect(getComputedStyle(value).color)
            .toBe(getComputedStyle(card, '::before').backgroundColor);
    });
});
