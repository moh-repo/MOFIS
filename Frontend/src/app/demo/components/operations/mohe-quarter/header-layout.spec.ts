import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { MoheQuarterComponent } from './mohe-quarter.component';
import { OperationsModule } from '../operations.module';
import { MoheQuarterService } from '../../../service/mohe-quarter.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';

/**
 * The page header: title and subtitle on the leading edge, refresh and add on
 * the trailing edge — left/right in English, right/left in Arabic.
 *
 * <p>`justify-content: space-between` does that by itself; what defeated it was
 * the app-wide rule in styles.scss that row-reverses every
 * `.flex.justify-content-between` under `dir="rtl"`. In an RTL document
 * `space-between` has already mirrored the row, so the extra reversal flipped it
 * back and the Arabic page carried the English arrangement.
 *
 * <p>This page reads its direction from an ancestor rather than a `dir` on its
 * own root, which is why the cancelling rule is written with `:host-context`
 * and why these tests wrap the page in a directional element. They are
 * geometry assertions on purpose: the defect was two stylesheets disagreeing,
 * which reading the markup would never show. Karma loads styles.scss, so the
 * global rule is in force here exactly as it is in the browser.
 */
describe('MoheQuarter page header', () => {

    let fixture: ComponentFixture<MoheQuarterComponent>;

    const quarters = [
        { id: 1, quarterYear: 2025, quarterNumber: 1, quarterName: 'First 2025', notes: 'alpha' }
    ];

    /**
     * The page under an ancestor that carries the direction — a wrapper here, the
     * document element in the browser. It has to be an ancestor of our own rather
     * than `document.documentElement`: several specs in this suite drive the real
     * TranslationService, which writes `dir` on the document element when its
     * language changes, and one of those writes landing mid-spec made this file
     * fail intermittently in the full run.
     */
    let host: HTMLElement;

    const build = async (rtl: boolean) => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [OperationsModule],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: MoheQuarterService, useValue: { getAll: () => of(quarters), currentCreateJob: () => of(null) } },
                {
                    provide: LookupService,
                    useValue: {
                        getAcademicYears: () => of([]),
                        getQuarterNumbers: () => of([]),
                        getStudyCountriesIn: () => of([])
                    }
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of(rtl ? 'ar' : 'en'),
                        initialized$: of(true),
                        isReady: () => true,
                        isRTL: () => rtl,
                        translate: (k: string) => k,
                        getCurrentLanguage: () => (rtl ? 'ar' : 'en')
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(MoheQuarterComponent);
        fixture.nativeElement.style.width = '1100px';
        host?.remove();
        host = document.createElement('div');
        host.setAttribute('dir', rtl ? 'rtl' : 'ltr');
        host.appendChild(fixture.nativeElement);
        document.body.appendChild(host);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => {
        fixture?.destroy();
        host?.remove();
    });

    const header = () => fixture.nativeElement.querySelector('.mq-page-header') as HTMLElement;
    const titleBlock = () => header().firstElementChild as HTMLElement;
    const actions = () => header().lastElementChild as HTMLElement;

    it('puts the title on the left and the actions on the right in English', async () => {
        await build(false);

        const box = header().getBoundingClientRect();
        const title = titleBlock().getBoundingClientRect();
        const buttons = actions().getBoundingClientRect();

        expect(actions().querySelectorAll('button').length)
            .withContext('refresh and add').toBe(2);
        expect(title.left)
            .withContext(`title ${title.left} should start before the buttons ${buttons.left}`)
            .toBeLessThan(buttons.left);
        expect(title.left - box.left).withContext('title on the left edge').toBeLessThan(2);
        expect(box.right - buttons.right).withContext('buttons on the right edge').toBeLessThan(2);
    });

    it('mirrors both in Arabic — title on the right, actions on the left', async () => {
        await build(true);

        const box = header().getBoundingClientRect();
        const title = titleBlock().getBoundingClientRect();
        const buttons = actions().getBoundingClientRect();

        expect(buttons.left)
            .withContext(`buttons ${buttons.left} should start before the title ${title.left}`)
            .toBeLessThan(title.left);
        expect(buttons.left - box.left).withContext('buttons on the left edge').toBeLessThan(2);
        expect(box.right - title.right).withContext('title on the right edge').toBeLessThan(2);
    });

    it('keeps the icon and the words together at the leading edge of the header', async () => {
        // The icon badge and the two lines of text are one block; only the block
        // as a whole changes sides. gap-3 spaces them, so neither direction needs
        // a margin override.
        for (const rtl of [false, true]) {
            await build(rtl);

            const badge = (titleBlock().querySelector('span') as HTMLElement).getBoundingClientRect();
            const heading = (titleBlock().querySelector('h2') as HTMLElement).getBoundingClientRect();

            if (rtl) {
                expect(badge.left).withContext('Arabic: badge after the words').toBeGreaterThanOrEqual(heading.right);
            } else {
                expect(badge.right).withContext('English: badge before the words').toBeLessThanOrEqual(heading.left);
            }
        }
    });
});
