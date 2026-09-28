import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { Confirmation, ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { BudgetAppropriationsComponent } from './budget-appropriations.component';
import { AccountingModule } from '../accounting.module';
import { AccountingApiService } from '../accounting-api.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';

describe('BudgetAppropriationsComponent', () => {
    let fixture: ComponentFixture<BudgetAppropriationsComponent>;
    let component: BudgetAppropriationsComponent;
    let approved: number[];
    let confirm: jasmine.Spy;

    const rows = [
        { id: 11, fiscalYearId: 1, feeTypeId: 1, currencyId: 1, amount: 250000, studyCountryInId: null,
          status: 'DRAFT', referenceNumber: 'MOF/2025/7', allocated: 0, transferred: 0, remaining: 250000 },
        { id: 12, fiscalYearId: 1, feeTypeId: 1, currencyId: 1, amount: 40000, studyCountryInId: 1,
          status: 'APPROVED', referenceNumber: null, allocated: 10000, transferred: 0, remaining: 30000 }
    ];

    beforeEach(async () => {
        approved = [];
        await TestBed.configureTestingModule({
            imports: [AccountingModule],
            providers: [
                {
                    provide: AccountingApiService,
                    useValue: {
                        fiscalYears: () => of([{ id: 1, yearNo: 2025, status: 'OPEN' }]),
                        feeTypes: () => of([{ id: 1, feeTypeCode: 'STIPEND', nameAr: 'المساعدة', nameEn: 'Stipend', active: true }]),
                        appropriations: () => of(rows),
                        approveAppropriation: (id: number) => { approved.push(id); return of({}); }
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
                { provide: HttpClient, useValue: { get: () => of({}) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, getCurrentLanguage: () => 'en',
                        // The confirm message carries placeholders; the stub returns them so the fill-in is checked.
                        translate: (k: string) => k === 'ACCOUNTING.CONFIRM_APPROVE_APPROPRIATION'
                            ? '{amount} {currency} {feeType} {attache} {year}' : k
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(BudgetAppropriationsComponent);
        component = fixture.componentInstance;
        confirm = spyOn(fixture.debugElement.injector.get(ConfirmationService), 'confirm');
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    it('offers the study countries as the attaché, not the home countries (live QA H6)', () => {
        expect(component.countryOptions.map(c => c.name)).toEqual(['Egypt']);
    });

    it('loads the open fiscal year and its appropriations', () => {
        expect(component.selectedFiscalYearId).toBe(1);
        expect(component.rows.length).toBe(2);
        expect(component.amountTotal).toBe(290000);
    });

    // The approved figure is what the whole fund chain draws down, so it asks first.
    it('approves once the confirmation is accepted, stating amount, stream, attaché and year', () => {
        confirm.and.callFake((c: Confirmation) => { c.accept!(); return {} as ConfirmationService; });

        component.confirmApprove(component.rows[0]);

        expect(approved).toEqual([11]);
        const message = confirm.calls.mostRecent().args[0].message as string;
        expect(message).toContain('250,000.00');
        expect(message).toContain('USD');
        expect(message).toContain('Stipend');
        expect(message).toContain('ACCOUNTING.MINISTRY_WIDE');
        expect(message).toContain('2025');
    });

    it('does nothing when the confirmation is rejected', () => {
        confirm.and.callFake((c: Confirmation) => { c.reject?.(); return {} as ConfirmationService; });

        component.confirmApprove(component.rows[0]);

        expect(approved).toEqual([]);
        expect(confirm).toHaveBeenCalled();
    });
});
