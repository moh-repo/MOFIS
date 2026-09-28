import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { AccountingModule } from './accounting.module';
import { ChartOfAccountsComponent } from './chart-of-accounts/chart-of-accounts.component';
import { BudgetAppropriationsComponent } from './budget-appropriations/budget-appropriations.component';
import { AllocationsComponent } from './allocations/allocations.component';
import { PaymentBatchesComponent } from './payment-batches/payment-batches.component';
import { FxRatesComponent } from './fx-rates/fx-rates.component';
import { AdvanceBalancesComponent } from './advance-balances/advance-balances.component';

import { AccountingApiService } from './accounting-api.service';
import { LookupService } from '../../service/lookup.service';
import { MoheQuarterService } from '../../service/mohe-quarter.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { codeListStub } from '../../../testing/code-list.stub';

/**
 * The Add, Edit and View dialogs on the six accounting screens.
 *
 * <p>Each of them was a bare `flex flex-column gap-3` of labels and inputs, and
 * none of them said anything about a field: a missing code, attaché, currency or
 * amount was reported by one toast that named none of them, so the only way to
 * find the empty field was to look. Four of the six had no View at all — an
 * appropriation's note and reference, an allocation's transfer advice and its
 * maker and checker, a rate's source, and an account's parent are on no row and
 * were on no screen either.
 *
 * <p>They use the shared vocabulary now — assets/layout/styles/_detail-dialog.scss,
 * the same one the MOHE quarter, lookup table and student-access dialogs use — so
 * a record looks the same whether it is being read or changed, and the same
 * across pages. What is asserted here is that vocabulary and the field-level
 * messages, not the wording.
 *
 * <p>Advance balances is the one screen with no dialog of either kind. It is a
 * reconciliation table: every figure it holds is already in the row, and there is
 * nothing to add or edit. It is in the last check so that giving it one brings it
 * under the rule rather than past it.
 */

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, getCurrentLanguage: () => 'en',
    translate: (key: string) => key.split('.').pop()!.replace(/_/g, ' ')
};

/** Every call answers with an empty list; the chart's summary is read as an object. */
const api = () => new Proxy({} as any, {
    get: (_t, prop) => {
        if (prop === 'chartSummary') { return () => of({ accountCount: 0, postableCount: 0 }); }
        if (prop === 'paymentBatch') {
            return () => of({
                batch: {
                    id: 1, batchNo: 'B-2025-3-001', studyCountryInId: 1, feeTypeId: 1, currencyId: 1,
                    lineCount: 1, entitledTotal: 1000, disbursedTotal: 900, status: 'OPEN',
                    preparedBy: 7, preparedAt: '2026-08-01T09:00:00Z', notes: 'first draft'
                },
                lines: [{ id: 1, studentId: 20211534, studentName: 'عبدالرحمن الشامي', entitledAmount: 1000, disbursedAmount: 900, heldReason: 'wafar' }]
            });
        }
        return () => of([]);
    }
});

interface Case {
    name: string;
    component: any;
    /** Opens the dialog that adds a record. */
    openAdd: (c: any) => void;
    /**
     * How many fields the empty Add form must mark. Measured, not guessed: it is
     * the number of required fields the dialog leaves blank when it opens.
     */
    minErrors: number;
    /**
     * How many of those are labelled fields carrying the required mark. The same
     * as `minErrors` unless something required is not a field: on payment batches
     * the third is the student selection, which is a table.
     */
    requiredMarks?: number;
    /** Opens the dialog that reads one back, on a sample row. */
    openView?: (c: any) => void;
}

const CASES: Case[] = [
    {
        name: 'chart of accounts', component: ChartOfAccountsComponent,
        openAdd: c => c.openNewRoot(),
        // Code and Arabic name.
        minErrors: 2,
        openView: c => c.openView({
            id: 4, code: '01', fullCode: '1.01', nameAr: 'الرواتب', nameEn: 'Salaries',
            accountNature: 'EXPENSE', normalBalance: 'DEBIT', postable: true, active: true,
            levelNo: 2, segmentId: 1, parentAccountId: null, studyCountryInId: null
        })
    },
    {
        name: 'budget appropriations', component: BudgetAppropriationsComponent,
        openAdd: c => c.openNew(),
        // Stream, currency and amount.
        minErrors: 3,
        openView: c => c.openView({
            id: 9, feeTypeId: 1, currencyId: 1, studyCountryInId: 1, amount: 500000,
            allocated: 120000, transferred: 100000, remaining: 380000, status: 'APPROVED',
            referenceNumber: 'MOF/2026/44', notes: 'first tranche', createdBy: 7,
            createdAt: '2026-08-01T09:00:00Z'
        })
    },
    {
        name: 'attache allocations', component: AllocationsComponent,
        openAdd: c => c.openNew(),
        // Attaché, currency and amount.
        minErrors: 3,
        openView: c => c.openView({
            id: 3, moheQuarterKey: 20253, studyCountryInId: 1, allocationType: 'REGULAR',
            feeTypeId: 1, currencyId: 1, amount: 25000, status: 'TRANSFERRED',
            transferDate: '2026-08-10', transferReference: 'SWIFT/9931', notes: 'quarterly',
            createdBy: 7, createdAt: '2026-08-01T09:00:00Z', updatedBy: 2, updatedAt: '2026-08-10T11:00:00Z'
        })
    },
    {
        name: 'payment batches', component: PaymentBatchesComponent,
        openAdd: c => c.openNew(),
        // Attaché, currency, and a selection with nobody in it — the last of
        // which is a table, not a field, so it carries a message but no mark.
        minErrors: 3, requiredMarks: 2,
        openView: c => c.open({ id: 1 })
    },
    {
        name: 'fx rates', component: FxRatesComponent,
        openAdd: c => c.openNew(),
        // From, to and the rate. The date opens filled in with today.
        minErrors: 3,
        openView: c => c.openView({
            id: 2, rateDate: '2026-08-30', fromCurrencyId: 1, toCurrencyId: 2, rate: 249.5,
            rateSource: 'Central Bank of Yemen', notes: 'daily fixing', createdBy: 7,
            createdAt: '2026-08-30T08:00:00Z'
        })
    }
];

const build = async (component: any): Promise<any> => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [AccountingModule],
        providers: [
            { provide: AccountingApiService, useValue: api() },
            { provide: LookupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
            { provide: MoheQuarterService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
            { provide: CodeListService, useValue: codeListStub() },
            { provide: TranslationService, useValue: translation },
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } }
        ]
    }).compileComponents();

    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
};

CASES.forEach(({ name, component, openAdd, minErrors, requiredMarks, openView }) => {

    describe(`${name} dialogs`, () => {

        it('adds through the shared dialog vocabulary', async () => {
            const fixture = await build(component);
            openAdd(fixture.componentInstance);
            fixture.detectChanges();

            const dialog = fixture.nativeElement.querySelector('.dv-dialog') as HTMLElement;
            expect(dialog).withContext('the Add dialog is not built from the shared vocabulary').toBeTruthy();
            // A hero says which record is being changed, a section groups the
            // fields, and a footer carries the actions. A bare column of inputs
            // has none of the three.
            ['.dv-hero', '.dv-section', '.dv-footer'].forEach(part =>
                expect(dialog.querySelector(part))
                    .withContext(`the Add dialog has no ${part}`).not.toBeNull());
        });

        it('says which fields are missing rather than only that something is', async () => {
            const fixture = await build(component);
            openAdd(fixture.componentInstance);
            fixture.detectChanges();

            const errorsBefore = fixture.nativeElement.querySelectorAll('.dv-dialog .dv-error').length;
            expect(errorsBefore)
                .withContext('the dialog opens already complaining')
                .toBe(0);

            fixture.componentInstance.save();
            fixture.detectChanges();

            const errors = fixture.nativeElement.querySelectorAll('.dv-dialog .dv-error');
            expect(errors.length)
                .withContext(`Save on an empty form marked ${errors.length} fields; `
                    + 'the toast that replaced this named none of them')
                .toBeGreaterThanOrEqual(minErrors);
        });

        it('marks its required fields in the form as well as in the footer', async () => {
            const fixture = await build(component);
            openAdd(fixture.componentInstance);
            fixture.detectChanges();

            const marks = fixture.nativeElement.querySelectorAll('.dv-dialog .dv-label .dv-required');
            expect(marks.length)
                .withContext(`${marks.length} fields carry the required mark; `
                    + 'without it the footer note explains a mark that is nowhere')
                .toBeGreaterThanOrEqual(requiredMarks ?? minErrors);
        });

        if (openView) {
            it('reads one back in a view dialog', async () => {
                const fixture = await build(component);
                openView(fixture.componentInstance);
                fixture.detectChanges();
                await fixture.whenStable();
                fixture.detectChanges();

                const dialogs = Array.from(
                    fixture.nativeElement.querySelectorAll('.dv-dialog')) as HTMLElement[];
                // The view is the one with no Save: a record being read has a
                // label/value list or a table, not a form.
                const view = dialogs.find(d => d.querySelector('.dv-dl, .p-datatable'));
                expect(view).withContext(`${name} has no view dialog`).toBeTruthy();
                expect(view!.querySelector('.dv-hero__title'))
                    .withContext('the view dialog does not name the record it is showing').not.toBeNull();
                expect(view!.querySelector('input, textarea'))
                    .withContext('the view dialog is an edit form; a record being read is not typed into')
                    .toBeNull();
            });
        }
    });
});

describe('advance balances', () => {
    it('has no dialog to hold to the vocabulary', async () => {
        const fixture = await build(AdvanceBalancesComponent);
        // Stated rather than left out: it is a reconciliation table and every
        // figure it holds is already in the row. Giving it a dialog brings it
        // under the rule above rather than past it.
        expect(fixture.nativeElement.querySelector('p-dialog'))
            .withContext('advance balances grew a dialog; it now belongs in CASES')
            .toBeNull();
    });
});
