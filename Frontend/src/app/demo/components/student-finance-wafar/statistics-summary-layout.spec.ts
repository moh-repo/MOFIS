import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentFinanceWafarComponent } from './student-finance-wafar.component';
import { StudentFinanceWafarModule } from './student-finance-wafar.module';
import { StudentFinanceWafarService } from '../../service/student-finance-wafar.service';
import { StudentFinanceService } from '../../service/studentfinance.service';
import { StudyWafarTypeService } from '../../service/study-wafar-type.service';
import { StudyUniversityFeeService } from '../../service/studyuniversityfee.service';
import { ApprovalService } from '../../service/approval.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * The statistics summary on the finance/wafar page.
 *
 * <p>It was the third hand-built copy of the same band of figures. The four
 * cards were laid out on primeflex columns (col-12 / md:col-6 / xl:col-3), and
 * the accent colour was set on the icon alone, so nothing else on a card told
 * the four apart. The panel used to start collapsed behind a toggle; review
 * F10 took the toggle out, so the figures are always shown.
 *
 * <p>Layout now comes from the shared band and is tested centrally in
 * src/app/summary-cards-shared.spec.ts. What is pinned here is what belongs to
 * this page — that it uses that band, declares four columns for its four
 * figures, and gives each an accent of its own — plus the direction bug the
 * migration removed, which is the assertion most worth keeping.
 */
describe('StudentFinanceWafar statistics summary', () => {

    let fixture: ComponentFixture<StudentFinanceWafarComponent>;

    /** Any method returns an empty observable. */
    const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentFinanceWafarModule],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: StudentFinanceWafarService, useValue: quiet() },
                { provide: StudentFinanceService, useValue: quiet() },
                { provide: StudyWafarTypeService, useValue: quiet() },
                { provide: StudyUniversityFeeService, useValue: quiet() },
                { provide: ApprovalService, useValue: quiet() },
                { provide: LookupService, useValue: quiet() },
                {
                    provide: UmsPermissionService,
                    useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] }
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

        fixture = TestBed.createComponent(StudentFinanceWafarComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.componentInstance.stats = {
            totalRecords: 1204, totalAmount: 98765, averageAmount: 82,
            uniqueCountries: 7, lastUpdated: new Date()
        };
        fixture.detectChanges();

        // Attached to the document, or nothing has a computed layout at all.
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.nativeElement.remove();
        document.body.classList.remove('rtl');
    });

    const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement;
    const all = (selector: string) =>
        Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];

    /**
     * Settles the statistics panel before anything is measured. It is always
     * open now (review F10); any entry animation is finished so the cards have
     * their final size.
     */
    function openSummary(): void {
        (fixture.nativeElement as HTMLElement)
            .getAnimations({ subtree: true })
            .forEach(animation => animation.finish());
        fixture.detectChanges();
    }

    it('uses the shared summary band', () => {
        openSummary();

        expect(el('.summary-cards')).toBeTruthy();
        expect(all('.summary-card').length).toBe(4);
    });

    it('drops the primeflex column wrappers', () => {
        openSummary();

        // Each card used to be wrapped in col-12 md:col-6 xl:col-3, which is a
        // second, competing statement of the column count.
        expect(el('.summary-cards')?.querySelector('[class*="col-"]')).toBeNull();
    });

    it('declares four columns for its four figures', () => {
        openSummary();

        const grid = el('.summary-cards');
        const tracks = getComputedStyle(grid).gridTemplateColumns.split(/\s+/);

        expect(grid.classList).toContain('summary-cards--cols-4');
        expect([1, 2, 4]).withContext(`tracks: ${tracks.join(' | ')}`).toContain(tracks.length);
    });

    it('shows the statistics open, with no collapse button', () => {
        // Review F10: the toggle hid the figures and was taken out.
        expect(fixture.nativeElement.querySelector('.p-panel-header button'))
            .withContext('no toggler in the header').toBeNull();

        openSummary();

        const cards = all('.summary-card');
        expect(cards.length).toBe(4);
        expect(cards[0].getBoundingClientRect().height).toBeGreaterThan(0);
    });

    it('gives each card its own accent', () => {
        openSummary();

        const accents = all('.summary-card').map(card =>
            getComputedStyle(card).getPropertyValue('--summary-accent').trim());

        expect(accents.every(a => a !== '')).withContext(`accents: ${accents}`).toBeTrue();
        expect(new Set(accents).size).withContext('each card should be distinguishable').toBe(4);
    });

    it('keeps the digits the same width across cards', () => {
        openSummary();

        expect(getComputedStyle(el('.summary-value')).fontVariantNumeric)
            .toContain('tabular-nums');
    });

    it('puts the icon on the leading edge in both directions', () => {
        // The bug this replaced: an :host-context(.rtl) rule set
        // flex-direction: row-reverse on these cards. A plain `row` already
        // lays out right-to-left under dir="rtl", so reversing it again put the
        // icon back on the left — the opposite of what the override intended.
        openSummary();

        const iconSide = () => {
            const card = el('.summary-card').getBoundingClientRect();
            const icon = el('.summary-icon').getBoundingClientRect();
            return icon.left - card.left < card.right - icon.right ? 'left' : 'right';
        };

        expect(iconSide()).withContext('left to right, the icon leads on the left').toBe('left');

        // Both switches are thrown, because the page has two: `dir` is what
        // the browser mirrors from, and the `.rtl` class on an ancestor is what
        // this component's :host-context rules key off. Setting only `dir`
        // would leave a reintroduced :host-context(.rtl) flip undetected.
        document.body.classList.add('rtl');
        el('.student-finance-wafar-page').setAttribute('dir', 'rtl');
        fixture.detectChanges();

        expect(iconSide()).withContext('right to left, the icon leads on the right').toBe('right');
    });
});
