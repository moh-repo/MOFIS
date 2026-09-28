import { TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { BehaviorSubject, of } from 'rxjs';
import { ChartOfAccountsComponent } from './chart-of-accounts.component';
import { AccountingApiService } from '../accounting-api.service';
import { LookupService } from '../../../service/lookup.service';
import { CodeListService } from '../../../service/code-list.service';
import { TranslationService } from '../../../service/translation.service';

/**
 * FE-04: the component subscribed to currentLanguage$ (which never completes) without
 * tearing it down, so every visit left a subscriber behind that went on rebuilding the
 * account-nature options of a destroyed component on each language switch.
 */
describe('ChartOfAccountsComponent language subscription', () => {
    let lang$: BehaviorSubject<string>;
    let options: jasmine.Spy;

    beforeEach(() => {
        lang$ = new BehaviorSubject('en');
        options = jasmine.createSpy('options').and.returnValue([]);
        TestBed.configureTestingModule({
            declarations: [ChartOfAccountsComponent],
            providers: [
                { provide: AccountingApiService, useValue: { fiscalYears: () => of([]) } },
                { provide: LookupService, useValue: { getStudyCountriesIn: () => of([]) } },
                { provide: CodeListService, useValue: { options } },
                { provide: TranslationService, useValue: { currentLanguage$: lang$, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: MessageService, useValue: { add: () => undefined } },
                { provide: ConfirmationService, useValue: { confirm: () => undefined } }
            ]
        });
        TestBed.overrideTemplate(ChartOfAccountsComponent, '');
    });

    it('rebuilds the natures on a language change while alive, and stops once destroyed', () => {
        const fixture = TestBed.createComponent(ChartOfAccountsComponent);
        fixture.detectChanges();
        expect(lang$.observed).toBeTrue();

        options.calls.reset();
        lang$.next('ar');
        expect(options).toHaveBeenCalledWith('account-nature');

        fixture.destroy();
        options.calls.reset();
        expect(lang$.observed).withContext('nothing left subscribed').toBeFalse();
        lang$.next('en');
        expect(options).not.toHaveBeenCalled();
    });
});
