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
 * The filter bar above the archived-student list.
 *
 * <p>Its three inputs were wrapped in `p-input-icon-left` / `p-input-icon-right`,
 * chosen at runtime from `isRTL()`. PrimeNG 21 removed both classes and ships
 * nothing in their place, so the icons rendered as loose glyphs beside the
 * boxes rather than inside them, and no compiler could see it. The geometry
 * assertions here are what catch that: they measure where each icon actually
 * lands relative to its own input.
 *
 * <p>The widths matter too. This panel is full width until a student is
 * selected and half width afterwards, so the fields share the row instead of
 * carrying the fixed 120px/160px they used to.
 */
describe('StudentHistory filter bar', () => {

    let fixture: ComponentFixture<StudentHistoryComponent>;

    /** Every service method returns an empty stream unless named below. */
    const emptyService = (overrides: Record<string, unknown> = {}) =>
        new Proxy(overrides, {
            get: (target, prop: string) =>
                prop in target ? (target as any)[prop] : () => of([])
        });

    beforeEach(async () => {
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
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentHistoryComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    const el = <T extends HTMLElement>(selector: string) =>
        fixture.nativeElement.querySelector(selector) as T;

    const fields = () =>
        Array.from(fixture.nativeElement.querySelectorAll('.filter-bar__field')) as HTMLElement[];

    it('positions the icons through the restored compatibility rules', () => {
        // p-input-icon-left is a dead class in PrimeNG 21. It works here only
        // because styles.scss restores the v17 rules for it, and Karma loads
        // that file — so this page shares one icon implementation with the
        // rest of the app instead of carrying its own.
        expect(fields()[0].querySelector('.p-input-icon-left')).toBeTruthy();
        expect(el('.p-input-icon-right')).toBeNull();
    });

    it('renders the three filters', () => {
        expect(fields().length).toBe(3);
    });

    it('puts every icon inside its own input', () => {
        fields().forEach((field, i) => {
            const icon = (field.querySelector('i') as HTMLElement).getBoundingClientRect();
            const input = (field.querySelector('input') as HTMLElement).getBoundingClientRect();

            const x = icon.left + icon.width / 2;
            const y = icon.top + icon.height / 2;

            expect(x).withContext(`field ${i}: icon x ${x} vs ${input.left}-${input.right}`)
                .toBeGreaterThanOrEqual(input.left);
            expect(x).toBeLessThanOrEqual(input.right);
            expect(y).withContext(`field ${i}: icon y ${y} vs ${input.top}-${input.bottom}`)
                .toBeGreaterThanOrEqual(input.top);
            expect(y).toBeLessThanOrEqual(input.bottom);
        });
    });

    it('reserves room so typed text cannot run under the icon', () => {
        fields().forEach((field, i) => {
            const input = field.querySelector('input') as HTMLInputElement;
            const padding = parseFloat(getComputedStyle(input).paddingLeft);
            const iconRight = (field.querySelector('i') as HTMLElement).getBoundingClientRect().right;

            expect(padding)
                .withContext(`field ${i} padding ${padding}`)
                .toBeGreaterThan(iconRight - input.getBoundingClientRect().left);
        });
    });

    it('lets the fields share the row instead of pinning them to fixed widths', () => {
        fields().forEach((field, i) => {
            const input = field.querySelector('input') as HTMLInputElement;

            // The old markup set style="width: 120px" / "160px" inline, which
            // cannot adapt when the panel halves.
            expect(input.style.width).withContext(`field ${i} has an inline width`).toBe('');
        });
    });

    it('keeps the clear button in place instead of adding it as you type', () => {
        const clear = () => el<HTMLButtonElement>('.filter-bar__actions button');

        // It used to be inside an @if, so the row jumped the moment the first
        // character was entered.
        expect(clear()).toBeTruthy();
        expect(clear().disabled).toBeTrue();

        fixture.componentInstance.filterStudentName = 'ahmed';
        fixture.detectChanges();

        expect(clear()).toBeTruthy();
        expect(clear().disabled).toBeFalse();
    });

    it('marks only the filters that are actually set', () => {
        const active = () => fixture.nativeElement
            .querySelectorAll('.filter-bar__field--active').length;

        expect(active()).toBe(0);

        fixture.componentInstance.filterStudentId = '42';
        fixture.componentInstance.filterPassport = 'A123';
        fixture.detectChanges();

        expect(active()).toBe(2);
    });
});
