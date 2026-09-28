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
 * Which side of each row on the Wafar page its first item lands on (round-5 Phase 7).
 *
 * <p>Under `direction: rtl` a flex row already runs right to left, so its first child
 * sits at the right edge without help. Nine rules then said `flex-direction:
 * row-reverse` on top: the filters title and its end, the caption's active chip, the
 * amount chip, and in the two dialogs the summary chips, the section headers, the
 * view header's badge and subtitle, and the view chips. In Arabic every icon sat on
 * the left of its text and the Clear button came before the count. The chips' label
 * column also said `align-items: flex-end`, which under RTL is the left edge.
 *
 * <p>Measured in both directions, against the neighbour and the chip rather than a
 * number, as in hero-direction.spec.ts and portal-direction.spec.ts.
 */
describe('StudentFinanceWafar rows in both reading directions', () => {

    let fixture: ComponentFixture<StudentFinanceWafarComponent>;
    let frame: HTMLElement;

    const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

    const RECORD = {
        id: 1, studentFinId: 9, studentFinName: 'زيد علي', wafarTypeId: 1, studentFinRecievedAmount: 1500,
        studyCountryInId: 1004, studyFinRecievedStatus: 1
    };

    const build = async (dir: 'ltr' | 'rtl') => {
        const lang = dir === 'rtl' ? 'ar' : 'en';
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [StudentFinanceWafarModule],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: StudentFinanceWafarService,
                    useValue: new Proxy({ getAll: () => of([RECORD]), pending: () => of({ wafar: [], finance: [] }) } as any, {
                        get: (target, prop) => prop in target ? target[prop] : () => of([])
                    })
                },
                { provide: StudentFinanceService, useValue: quiet() },
                { provide: StudyWafarTypeService, useValue: quiet() },
                { provide: StudyUniversityFeeService, useValue: quiet() },
                { provide: ApprovalService, useValue: quiet() },
                { provide: LookupService, useValue: quiet() },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of(lang), initialized$: of(true), isReady: () => true,
                        isRTL: () => dir === 'rtl', translate: (k: string) => k.split('.').pop()!, getCurrentLanguage: () => lang
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentFinanceWafarComponent);

        // What the layout puts around a routed page: the direction, and in Arabic the
        // class this component's :host-context(.rtl) rules key on.
        frame = document.createElement('div');
        frame.setAttribute('dir', dir);
        frame.style.width = '1200px';
        if (dir === 'rtl') { frame.className = 'rtl'; }
        document.body.appendChild(frame);
        frame.appendChild(fixture.nativeElement);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => {
        // A dialog may have moved itself to <body>; closed here so none outlives its case.
        if (fixture) {
            fixture.componentInstance.formDialogVisible = false;
            fixture.componentInstance.viewDialogVisible = false;
            fixture.detectChanges();
        }
        frame?.remove();
    });

    /** Page parts are in the fixture; dialog parts may be moved to <body>, so the whole document is searched. */
    const el = (selector: string): HTMLElement => {
        const found = document.querySelector(selector) as HTMLElement;
        expect(found).withContext(`nothing matches ${selector}`).toBeTruthy();
        return found;
    };

    /** `first` sits on the reading-start side of `second`: the right in Arabic, the left in English. */
    const expectReadFirst = (first: string, second: string, dir: 'ltr' | 'rtl') => {
        const a = el(first).getBoundingClientRect();
        const b = el(second).getBoundingClientRect();
        if (dir === 'ltr') {
            expect(a.right).withContext(`${first} ends at ${Math.round(a.right)}, ${second} starts at ${Math.round(b.left)}`)
                .toBeLessThanOrEqual(b.left + 1);
        } else {
            expect(a.left).withContext(`${first} starts at ${Math.round(a.left)}, ${second} ends at ${Math.round(b.right)}`)
                .toBeGreaterThanOrEqual(b.right - 1);
        }
    };

    /** The row's icon is in the half of the row the page is read from (for rows whose text is a bare text node). */
    const expectIconLeads = (row: string, dir: 'ltr' | 'rtl') => {
        const box = el(row).getBoundingClientRect();
        const icon = el(`${row} > i`).getBoundingClientRect();
        const iconMiddle = (icon.left + icon.right) / 2, rowMiddle = (box.left + box.right) / 2;
        expect(dir === 'rtl' ? iconMiddle > rowMiddle : iconMiddle < rowMiddle)
            .withContext(`${row}: the icon's middle is at ${Math.round(iconMiddle)}, the row's at ${Math.round(rowMiddle)}`)
            .toBeTrue();
    };

    /** A chip's label starts at the chip column's reading-start edge. */
    const expectLabelAtStart = (chip: string, dir: 'ltr' | 'rtl') => {
        const column = el(`${chip} .chip-content`).getBoundingClientRect();
        const range = document.createRange();
        range.selectNodeContents(el(`${chip} .chip-label`));
        const text = range.getBoundingClientRect();
        const fromStart = dir === 'rtl' ? column.right - text.right : text.left - column.left;
        expect(Math.abs(fromStart)).withContext(`${chip}: the label starts ${Math.round(fromStart)}px from its column's start`)
            .toBeLessThanOrEqual(1);
    };

    /** The row is mirrored by `direction` alone, never reversed on top of it. */
    const expectNotReversed = (...selectors: string[]) => {
        for (const selector of selectors) {
            expect(getComputedStyle(el(selector)).flexDirection)
                .withContext(`${selector} is reversed a second time on top of direction: rtl`)
                .not.toBe('row-reverse');
        }
    };

    /** `selector` sits in the half of `container` the row ends in: the left in Arabic, the right in English. */
    const expectAtReadingEnd = (selector: string, container: string, dir: 'ltr' | 'rtl') => {
        const box = el(container).getBoundingClientRect();
        const item = el(selector).getBoundingClientRect();
        const toEnd = dir === 'rtl' ? item.left - box.left : box.right - item.right;
        const toStart = dir === 'rtl' ? box.right - item.right : item.left - box.left;
        expect(toEnd).withContext(`${selector} is ${Math.round(toEnd)}px from the row's end, ${Math.round(toStart)}px from its start`)
            .toBeLessThan(toStart);
    };

    (['ltr', 'rtl'] as const).forEach(dir => {

        describe(dir, () => {

            // Round-6 QA R6-09: the two rows Phase 7 left, which the global
            // `.flex.justify-content-between/-end` rule also reversed.
            it('page: the caption\'s count leads its chips, which sit at the row\'s end', async () => {
                await build(dir);
                fixture.componentInstance.filters.wafarType = RECORD.wafarTypeId as any;
                fixture.componentInstance.onFiltersChanged();
                fixture.detectChanges();

                expectReadFirst('.table-caption > span:first-child', '.table-caption-active', dir);
                expectAtReadingEnd('.table-caption-active', '.table-caption', dir);
                if (dir === 'rtl') { expectNotReversed('.table-caption'); }
            });

            it('form dialog: Cancel is read before Save, and both sit at the footer\'s end', async () => {
                await build(dir);
                fixture.componentInstance.openCreateDialog();
                fixture.detectChanges();
                await fixture.whenStable();

                expectReadFirst('.p-dialog-footer button:first-child', '.p-dialog-footer button:last-child', dir);
                expectAtReadingEnd('.p-dialog-footer button:first-child', '.p-dialog-footer', dir);
                if (dir === 'rtl') { expectNotReversed('.form-footer'); }
            });

            it('page: the filters icon leads its title, the count leads Clear, the amount leads its currency', async () => {
                await build(dir);

                expectReadFirst('.filters-title > i', '.filters-title > span', dir);
                expectReadFirst('.filters-header-end .filters-meta', '.filters-header-end button', dir);
                expectReadFirst('.amount-chip > span', '.amount-chip > small', dir);
                if (dir === 'rtl') { expectNotReversed('.filters-title', '.filters-header-end', '.amount-chip'); }
            });

            it('page: the caption\'s active-filters chip leads with its icon', async () => {
                await build(dir);
                fixture.componentInstance.filters.wafarType = RECORD.wafarTypeId as any;
                fixture.componentInstance.onFiltersChanged();
                fixture.detectChanges();

                expectIconLeads('.table-caption-active', dir);
                if (dir === 'rtl') { expectNotReversed('.table-caption-active'); }
            });

            it('form dialog: each summary chip leads with its icon and its label starts at the chip\'s start', async () => {
                await build(dir);
                fixture.componentInstance.openCreateDialog();
                fixture.detectChanges();
                await fixture.whenStable();

                const chip = '.finance-dialog__summary-strip .summary-chip';
                expectReadFirst(`${chip} > i`, `${chip} .chip-content`, dir);
                expectLabelAtStart(chip, dir);
                expectReadFirst('.finance-dialog__form .section-card__header > i', '.finance-dialog__form .section-card__header > span', dir);
                if (dir === 'rtl') { expectNotReversed(chip, '.finance-dialog__form .section-card__header'); }
            });

            it('view dialog: the badge, the subtitle, the chips and the section headers lead with their icons', async () => {
                await build(dir);
                fixture.componentInstance.openViewDialog(fixture.componentInstance.records[0]);
                fixture.detectChanges();
                await fixture.whenStable();

                expectReadFirst('.header-card__badge > i', '.header-card__badge > span', dir);
                expectIconLeads('.header-card__subtitle', dir);
                const chip = '.finance-view__chips-strip .view-chip';
                expectReadFirst(`${chip} > i`, `${chip} .chip-content`, dir);
                expectLabelAtStart(chip, dir);
                expectReadFirst('.finance-view .section-card__header > i', '.finance-view .section-card__header > span', dir);
                if (dir === 'rtl') {
                    expectNotReversed('.header-card__badge', '.header-card__subtitle', chip, '.finance-view .section-card__header');
                }
            });
        });
    });
});
