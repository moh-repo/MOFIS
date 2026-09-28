import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { PaymentBatchesComponent } from './payment-batches.component';
import { AccountingModule } from '../accounting.module';
import { AccountingApiService, PaymentBatchCreateRequest } from '../accounting-api.service';
import { LookupService } from '../../../service/lookup.service';
import { MoheQuarterService } from '../../../service/mohe-quarter.service';
import { TranslationService } from '../../../service/translation.service';
import { CodeListService } from '../../../service/code-list.service';
import { codeRow } from '../../../../testing/code-list.stub';

describe('PaymentBatchesComponent', () => {
    let fixture: ComponentFixture<PaymentBatchesComponent>;
    let component: PaymentBatchesComponent;
    let posted: PaymentBatchCreateRequest[];

    const batches = [
        { id: 31, moheQuarterKey: 20253, studyCountryInId: 1, batchNo: 1, feeTypeId: 1, currencyId: 1,
          status: 'DISBURSED', lineCount: 2, entitledTotal: 3360, disbursedTotal: 3000 },
        { id: 32, moheQuarterKey: 20253, studyCountryInId: 1, batchNo: 2, feeTypeId: 1, currencyId: 1,
          status: 'OPEN', lineCount: 1, entitledTotal: 500, disbursedTotal: 450 }
    ];

    const candidates = [
        { studentId: 10, studentName: 'A', entitledAmount: 300, wafarAmount: 0, netAmount: 300, currencyId: 1, alreadyPaid: true },
        { studentId: 11, studentName: 'B', entitledAmount: 200, wafarAmount: 50, netAmount: 150, currencyId: 1, alreadyPaid: false },
        { studentId: 12, studentName: 'C', entitledAmount: 100, wafarAmount: 0, netAmount: 100, currencyId: 1, alreadyPaid: false }
    ];

    beforeEach(async () => {
        posted = [];
        await TestBed.configureTestingModule({
            imports: [AccountingModule],
            providers: [
                {
                    provide: AccountingApiService,
                    useValue: {
                        feeTypes: () => of([{ id: 1, feeTypeCode: 'STIPEND', nameAr: 'المساعدة', nameEn: 'Stipend', active: true }]),
                        fiscalYears: () => of([{ id: 2, yearNo: 2025, status: 'OPEN' }]),
                        paymentBatches: () => of(batches),
                        paymentBatch: () => of({ batch: batches[1], lines: [] }),
                        paymentBatchCandidates: () => of(candidates),
                        createPaymentBatch: (r: PaymentBatchCreateRequest) => { posted.push(r); return of({ batch: {}, lines: [] }); },
                        approvePaymentBatch: () => of({}),
                        payPaymentBatch: () => of({}),
                        cancelPaymentBatch: () => of({})
                    }
                },
                {
                    provide: LookupService,
                    useValue: {
                        // Id 1 is Yemen in the home list and Egypt in the study list (live QA H6).
                        getCountries: () => of([{ id: 1, name: 'Yemen' }]),
                        getStudyCountriesIn: () => of([{ id: 1, name: 'Egypt' }]),
                        getStudyCurrencies: () => of([{ id: 1, name: 'USD' }])
                    }
                },
                {
                    provide: MoheQuarterService,
                    useValue: { getAll: () => of([{ quarterYear: 2025, quarterNumber: 3, quarterName: 'Q3 2025' }]) }
                },
                { provide: HttpClient, useValue: { get: () => of({}) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        // The status rows the tags read their colour from.
        TestBed.inject(CodeListService).store({ 'payment-batch-status': [codeRow('DISBURSED', { uiSeverity: 'success' })] });
        fixture = TestBed.createComponent(PaymentBatchesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    it('offers the study countries as the attaché, not the home countries (live QA H6)', () => {
        expect(component.countryOptions.map(c => c.name)).toEqual(['Egypt']);
    });

    it('creates and sums the quarter\'s batches', () => {
        expect(component).toBeTruthy();
        expect(component.selectedQuarterKey).toBe(20253);
        expect(component.rows.length).toBe(2);
        expect(component.entitledTotal).toBe(3860);
        expect(component.disbursedTotal).toBe(3450);
        // Tag colours are the payment-batch-status rows' own (codeSeverity pipe), not a switch here.
        const tags = Array.from(fixture.nativeElement.querySelectorAll('p-tag')) as HTMLElement[];
        const has = (sev: string) => tags.some(t => t.classList.contains(`p-tag-${sev}`) || !!t.querySelector(`.p-tag-${sev}`));
        expect(has('success')).withContext('DISBURSED → success from its row').toBeTrue();
        expect(has('secondary')).withContext('OPEN has no severity row → secondary').toBeTrue();
    });

    it('prepares a batch from the finance rows, leaving the already-paid unticked and holding the Wafar part', () => {
        component.openNew();

        expect(component.form.studyCountryInId).withContext('single attaché pre-filled').toBe(1);
        expect(component.form.feeTypeId).withContext('stipend is the default stream').toBe(1);
        expect(component.candidates.length).toBe(3);
        expect(component.selected.map(c => c.studentId))
            .withContext('a student already paid in full this quarter starts unticked').toEqual([11, 12]);
        expect(component.form.currencyId).withContext('taken from the finance rows').toBe(1);
        expect(component.selectedDisbursed).toBe(250);

        component.save();

        expect(posted.length).toBe(1);
        const req = posted[0];
        expect(req.moheQuarterKey).toBe(20253);
        expect(req.studyCountryInId).toBe(1);
        expect(req.lines).toEqual([
            { studentId: 11, entitledAmount: 200, disbursedAmount: 150, heldReason: 'ACCOUNTING.HELD_WAFAR' },
            { studentId: 12, entitledAmount: 100, disbursedAmount: 100, heldReason: null }
        ]);
        expect(component.createVisible).toBeFalse();
    });
});
