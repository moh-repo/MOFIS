import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentHistoryComponent } from './student-history.component';
import { StudentHistoryModule } from './student-history.module';
import { StudentDataArchService } from '../../service/studentdataarch.service';
import { StudentdataService } from '../../service/studentdata.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';

/**
 * The page header: title and subtitle on the leading edge, refresh and add on
 * the trailing edge — left/right in English, right/left in Arabic.
 *
 * <p>`justify-content: space-between` does that by itself; what defeated it was
 * the app-wide rule in styles.scss that row-reverses every
 * `.flex.justify-content-between` under `dir="rtl"`. Inside an RTL container
 * `space-between` has already mirrored the row, so the extra reversal flipped it
 * back and the Arabic page carried the English arrangement. The component
 * stylesheet cancels that rule for this header.
 *
 * <p>These are geometry assertions rather than class assertions on purpose: the
 * defect was two stylesheets disagreeing, which no amount of reading the markup
 * would have shown. Karma loads styles.scss, so the global rule is in force here
 * exactly as it is in the browser.
 */
describe('StudentHistory page header', () => {

    let fixture: ComponentFixture<StudentHistoryComponent>;

    /** Every service method returns an empty stream unless named below. */
    const emptyService = (overrides: Record<string, unknown> = {}) =>
        new Proxy(overrides, {
            get: (target, prop: string) =>
                prop in target ? (target as any)[prop] : () => of([])
        });

    const build = async (rtl: boolean) => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [StudentHistoryModule],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: StudentDataArchService,
                    useValue: emptyService({
                        getDistinctStudentsPaginated: () => of({ content: [], totalElements: 0 }),
                        getAllDistinctStudents: () => of([])
                    })
                },
                {
                    provide: StudentdataService,
                    useValue: emptyService({ getProgress: () => of(null), getYearResults: () => of([]) })
                },
                {
                    provide: StudentDocumentService,
                    useValue: emptyService({
                        isFormatLikeType: () => true,
                        getDocumentFormatLabelKey: () => 'STUDENTS.FORMAT_OTHER',
                        getDocumentFormatIcon: () => 'pi pi-file'
                    })
                },
                { provide: LookupService, useValue: emptyService() },
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

        fixture = TestBed.createComponent(StudentHistoryComponent);
        fixture.nativeElement.style.width = '900px';
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => fixture?.nativeElement.remove());

    const header = () => fixture.nativeElement.querySelector('.sh-page-header') as HTMLElement;
    const titleBlock = () => header().firstElementChild as HTMLElement;
    const actions = () => header().lastElementChild as HTMLElement;

    it('puts the title on the left and the actions on the right in English', async () => {
        await build(false);

        const box = header().getBoundingClientRect();
        const title = titleBlock().getBoundingClientRect();
        const buttons = actions().getBoundingClientRect();

        // Refresh only: the archive is read-only, so there is no Add (DEC-08 a).
        expect(actions().querySelectorAll('button').length)
            .withContext('refresh').toBe(1);
        expect(title.left)
            .withContext(`title ${title.left} should start before the buttons ${buttons.left}`)
            .toBeLessThan(buttons.left);
        expect(title.left - box.left).withContext('title on the left edge').toBeLessThan(2);
        expect(box.right - buttons.right).withContext('buttons on the right edge').toBeLessThan(2);
    });

    it('mirrors both in Arabic — title on the right, actions on the left', async () => {
        await build(true);

        expect(header().closest('[dir]')?.getAttribute('dir')).toBe('rtl');

        const box = header().getBoundingClientRect();
        const title = titleBlock().getBoundingClientRect();
        const buttons = actions().getBoundingClientRect();

        expect(buttons.left)
            .withContext(`buttons ${buttons.left} should start before the title ${title.left}`)
            .toBeLessThan(title.left);
        expect(buttons.left - box.left).withContext('buttons on the left edge').toBeLessThan(2);
        expect(box.right - title.right).withContext('title on the right edge').toBeLessThan(2);
    });

    it('keeps the icon beside the title on the leading side, in both directions', async () => {
        await build(false);
        let icon = (titleBlock().querySelector('i') as HTMLElement).getBoundingClientRect();
        let text = (titleBlock().querySelector('h5') as HTMLElement).getBoundingClientRect();
        expect(icon.right).withContext('English: icon before the words').toBeLessThanOrEqual(text.left);
        const ltrGap = text.left - icon.right;

        await build(true);
        icon = (titleBlock().querySelector('i') as HTMLElement).getBoundingClientRect();
        text = (titleBlock().querySelector('h5') as HTMLElement).getBoundingClientRect();
        expect(icon.left).withContext('Arabic: icon after the words').toBeGreaterThanOrEqual(text.right);

        // gap-3 rather than the physical mr-3 the header used to carry: the space
        // is the same on the leading side in both directions.
        expect(icon.left - text.right).toBeCloseTo(ltrGap, 0);
    });
});
