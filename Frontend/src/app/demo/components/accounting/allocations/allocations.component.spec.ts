import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { Confirmation, ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { AllocationsComponent } from './allocations.component';
import { AccountingModule } from '../accounting.module';
import { AccountingApiService } from '../accounting-api.service';
import { LookupService } from '../../../service/lookup.service';
import { MoheQuarterService } from '../../../service/mohe-quarter.service';
import { TranslationService } from '../../../service/translation.service';

describe('AllocationsComponent', () => {
    let fixture: ComponentFixture<AllocationsComponent>;
    let component: AllocationsComponent;
    let created: unknown[];
    let approved: number[];

    const allocations = [
        { id: 1, moheQuarterKey: 20253, studyCountryInId: 1, allocationType: 'REGULAR', feeTypeId: null,
          currencyId: 1, amount: 1000, status: 'DRAFT', createdBy: 13 },
        { id: 2, moheQuarterKey: 20253, studyCountryInId: 1, allocationType: 'ADDITIONAL', feeTypeId: 1,
          currencyId: 1, amount: 250.5, status: 'TRANSFERRED', createdBy: 13, updatedBy: 2 }
    ];

    beforeEach(async () => {
        created = [];
        approved = [];
        await TestBed.configureTestingModule({
            imports: [AccountingModule],
            providers: [
                {
                    provide: AccountingApiService,
                    useValue: {
                        feeTypes: () => of([{ id: 1, nameAr: 'المساعدة', nameEn: 'Stipend', active: true }]),
                        fiscalYears: () => of([{ id: 2, yearNo: 2025, status: 'OPEN' }]),
                        allocations: (key: number) => of(key === 20253 ? allocations : []),
                        createAllocation: (draft: unknown) => { created.push(draft); return of({}); },
                        approveAllocation: (id: number) => { approved.push(id); return of({}); },
                        transferAllocation: () => of({})
                    }
                },
                {
                    provide: LookupService,
                    useValue: {
                        // Id 1 is Yemen in the home list and Egypt in the study list (live QA H6).
                        getCountries: () => of([{ id: 1, name: 'Yemen' }]),
                        getStudyCountriesIn: () => of([{ id: 1, name: 'Egypt' }]),
                        getStudyCurrencies: () => of([{ id: 1, name: 'USD' }, { id: 1012, name: 'YER' }])
                    }
                },
                {
                    provide: MoheQuarterService,
                    useValue: {
                        getAll: () => of([
                            { quarterYear: 2025, quarterNumber: 2, quarterName: 'Q2 2025' },
                            { quarterYear: 2025, quarterNumber: 3, quarterName: 'Q3 2025' },
                            // A country-specific duplicate of Q3 folds into the same key.
                            { quarterYear: 2025, quarterNumber: 3, quarterName: 'Q3 2025 (JO)', countryId: 1004 },
                            { quarterYear: null, quarterNumber: null, quarterName: 'unkeyed' }
                        ])
                    }
                },
                { provide: HttpClient, useValue: { get: () => of({}) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, getCurrentLanguage: () => 'en',
                        // The confirm message carries placeholders; the stub returns them so the fill-in is checked.
                        translate: (k: string) => k === 'ACCOUNTING.CONFIRM_APPROVE_ALLOCATION'
                            ? '{amount} {currency} {attache} {quarter}' : k
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(AllocationsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    it('creates, picks the newest quarter and loads its allocations', () => {
        expect(component).toBeTruthy();
        expect(component.quarters.map(q => q.key)).toEqual([20253, 20252]);
        expect(component.selectedQuarterKey).toBe(20253);
        expect(component.rows.length).toBe(2);
        expect(component.total).toBeCloseTo(1250.5, 2);

        const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody td')) as HTMLElement[];
        expect(cells.some(c => c.textContent?.includes('Egypt'))).withContext('attaché shown by name').toBeTrue();
    });

    it('sends a draft for the selected quarter with the single attaché pre-filled', () => {
        component.openNew();
        expect(component.draft.studyCountryInId).withContext('one attaché in scope needs no choice').toBe(1);
        expect(component.draft.allocationType).toBe('REGULAR');

        // A draft without an amount is refused locally, before any request.
        component.save();
        expect(created.length).toBe(0);

        component.draft.amount = 5000;
        component.draft.currencyId = 1;
        component.save();

        expect(created.length).toBe(1);
        expect(created[0]).toEqual(jasmine.objectContaining({
            moheQuarterKey: 20253, studyCountryInId: 1, currencyId: 1, amount: 5000, allocationType: 'REGULAR'
        }));
        expect(component.dialogVisible).toBeFalse();
    });

    // Approval releases a transfer, so it asks — naming the amount, attaché and quarter — first.
    describe('approve', () => {
        let confirm: jasmine.Spy;

        beforeEach(() => {
            // Component-scoped provider: read it from the component's injector, not TestBed's.
            confirm = spyOn(fixture.debugElement.injector.get(ConfirmationService), 'confirm');
        });

        it('approves once the confirmation is accepted, stating what is approved', () => {
            confirm.and.callFake((c: Confirmation) => { c.accept!(); return {} as ConfirmationService; });

            component.confirmApprove(component.rows[0]);

            expect(approved).toEqual([1]);
            const message = confirm.calls.mostRecent().args[0].message as string;
            expect(message).toContain('1,000.00');
            expect(message).toContain('Egypt');
            expect(message).toContain('Q3 2025');
        });

        it('does nothing when the confirmation is rejected', () => {
            confirm.and.callFake((c: Confirmation) => { c.reject?.(); return {} as ConfirmationService; });

            component.confirmApprove(component.rows[0]);

            expect(approved).toEqual([]);
            expect(confirm).toHaveBeenCalled();
        });
    });
});
