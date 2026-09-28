import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { FxRatesComponent } from './fx-rates.component';
import { AccountingModule } from '../accounting.module';
import { AccountingApiService, FxRate } from '../accounting-api.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';

describe('FxRatesComponent', () => {
    let fixture: ComponentFixture<FxRatesComponent>;
    let component: FxRatesComponent;
    let created: Partial<FxRate>[];

    beforeEach(async () => {
        created = [];
        await TestBed.configureTestingModule({
            imports: [AccountingModule],
            providers: [
                {
                    provide: AccountingApiService,
                    useValue: {
                        fxRates: () => of([
                            { id: 2, fromCurrencyId: 1, toCurrencyId: 1012, rateDate: '2026-02-01', rate: 525.12345678, rateSource: 'CBY' },
                            { id: 3, fromCurrencyId: 5, toCurrencyId: 1012, rateDate: '2026-01-15', rate: 613.40, rateSource: 'ECB' }
                        ]),
                        createFxRate: (r: Partial<FxRate>) => { created.push(r); return of({}); },
                        deleteFxRate: () => of(void 0)
                    }
                },
                {
                    provide: LookupService,
                    useValue: {
                        getStudyCurrencies: () => of([
                            { id: 1, name: 'US Dollar' }, { id: 5, name: 'Euro' }, { id: 1012, name: 'Yemeni Rial (YER)' }
                        ])
                    }
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

        fixture = TestBed.createComponent(FxRatesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    it('sorts a currency column by the name it prints, not by the id behind it', () => {
        const header = Array.from(fixture.nativeElement.querySelectorAll('th'))
            .find((th: any) => (th.textContent ?? '').includes('ACCOUNTING.FROM_CURRENCY')) as HTMLElement;
        expect(header).withContext('the from-currency column carries a sort header').toBeTruthy();

        header.click();
        fixture.detectChanges();

        expect(component.rows.map(r => component.currencyName(r.fromCurrencyId))).toEqual(['Euro', 'US Dollar']);
        // The ids run the other way: 1 is the dollar and 5 the euro, so a sort by
        // the raw field would have left the rows exactly as they were.
        expect(component.rows.map(r => r.fromCurrencyId)).toEqual([5, 1]);
    });

    it('creates and lists the rates by currency name', () => {
        expect(component).toBeTruthy();
        expect(component.rows.length).toBe(2);
        const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
        expect(text).toContain('US Dollar');
        expect(text).toContain('Yemeni Rial (YER)');
    });

    it('refuses a rate of a currency against itself before any request, and sends a valid one as an ISO day', () => {
        component.openNew();
        expect(component.draft.toCurrencyId).withContext('the functional currency is the usual target').toBe(1012);

        component.draft.fromCurrencyId = 1012;
        component.draft.rate = 1;
        component.draft.rateDate = new Date(2026, 1, 15);
        expect(component.draftValid).toBeFalse();
        component.save();
        expect(created.length).toBe(0);

        component.draft.fromCurrencyId = 1;
        component.draft.rate = 530.5;
        component.draft.rateSource = 'CBY';
        expect(component.draftValid).toBeTrue();
        component.save();

        expect(created.length).toBe(1);
        expect(created[0]).toEqual(jasmine.objectContaining({
            fromCurrencyId: 1, toCurrencyId: 1012, rate: 530.5, rateDate: '2026-02-15', rateSource: 'CBY'
        }));
        expect(component.dialogVisible).toBeFalse();
    });
});
