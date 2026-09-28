import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';

import { FinanceFormComponent } from './finance-form.component';
import { StudentFinanceService } from '../../../service/studentfinance.service';
import { ApprovalService } from '../../../service/approval.service';
import { FinanceLookupsService, emptyFinanceLookups } from '../../../service/finance-lookups.service';
import { LookupService } from '../../../service/lookup.service';
import { StudentdataService } from '../../../service/studentdata.service';
import { FinanceReceivedWhomService } from '../../../service/finance-received-whom.service';
import { TranslationService } from '../../../service/translation.service';

/**
 * Editing a finance record changed fields nobody touched (QA B12 = F3, N10).
 *
 * <p>An empty check date was filled with today as the record loaded, and an empty
 * wafar was saved as 0 — so the approver was shown changes the user never made,
 * and an approved edit wrote them.
 */
describe('FinanceFormComponent leaves untouched fields as they were', () => {

    let component: FinanceFormComponent;
    let sent: any[];

    const build = (record: any) => {
        sent = [];
        const financeService = jasmine.createSpyObj<StudentFinanceService>(
            'StudentFinanceService', ['getById', 'getWafar', 'Update', 'Create']
        );
        financeService.getById.and.returnValue(of(record));
        financeService.getWafar.and.returnValue(throwError(() => ({ status: 404 })));
        financeService.Update.and.callFake((m: any) => { sent.push({ ...m }); return of({}) as any; });

        const approvalService = jasmine.createSpyObj<ApprovalService>('ApprovalService', ['getPendingForEntity']);
        approvalService.getPendingForEntity.and.returnValue(of([]) as any);

        const lookups = jasmine.createSpyObj<FinanceLookupsService>(
            'FinanceLookupsService', ['loadAll', 'reloadReceivedWhoms', 'resolveName', 'resolveCurrencyName']
        );
        // Status 1 has to resolve to RECEIVED: the check date is only required
        // once the status says money moved (H2), and these records carry status 1.
        lookups.loadAll.and.returnValue(of({
            ...emptyFinanceLookups(),
            finReceivedStatuses: [{
                id: 1, code: 'RECEIVED', uiSeverity: 'success',
                studyFinReceivedStatusName: 'Received', studyFinReceivedStatusMidate: ''
            }]
        }));

        const lookupService = jasmine.createSpyObj<LookupService>(
            'LookupService', ['getLuFamilyRelationshipsForUi', 'getFinReceivedReasonsForUi', 'getCountriesAll',
                'getStudyGovernmentsByCountry', 'getStudyFinanceBanksByCountry', 'getStudyFinanceBankBranchesByBank']
        );
        lookupService.getLuFamilyRelationshipsForUi.and.returnValue(of([]));
        lookupService.getFinReceivedReasonsForUi.and.returnValue(of([]));
        lookupService.getCountriesAll.and.returnValue(of([]));

        const studentdata = jasmine.createSpyObj<StudentdataService>('StudentdataService', ['getPagingAll']);
        studentdata.getPagingAll.and.returnValue(of({ content: [] } as any));

        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                FinanceFormComponent,
                MessageService,
                { provide: StudentFinanceService, useValue: financeService },
                { provide: ApprovalService, useValue: approvalService },
                { provide: FinanceLookupsService, useValue: lookups },
                { provide: LookupService, useValue: lookupService },
                { provide: StudentdataService, useValue: studentdata },
                { provide: FinanceReceivedWhomService, useValue: {} },
                {
                    provide: TranslationService,
                    useValue: { currentLanguage$: of('en'), translate: (k: string) => k, getCurrentLanguage: () => 'en', isRTL: () => false }
                },
                // The form follows paramMap, not the snapshot (M5).
                {
                    provide: ActivatedRoute,
                    useValue: { paramMap: of(convertToParamMap({ id: '1' })), snapshot: { paramMap: convertToParamMap({ id: '1' }) } }
                },
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting()
            ]
        });
        component = TestBed.inject(FinanceFormComponent);
        component.ngOnInit();
    };

    /** A record the form accepts as it stands. */
    const valid = (overrides: any = {}) => ({
        id: 1, studentFinId: '1', studentFinName: 'X', studentFinRecievedAmount: 5000,
        studyCheckDate: '2026-01-10T12:00:00Z', studyFinRecievedStatus: 1, studyCountryInId: 1,
        wafarAmount: null, wafarTypeId: null, ...overrides
    });

    it('does not fill an empty check date with today', () => {
        build(valid({ studyCheckDate: null }));

        expect(component.model.studyCheckDate).toBeNull();
        component.save();
        expect(sent).withContext('saved with a check date nobody entered').toEqual([]);
    });

    it('sends back an empty wafar as it was, not as 0', () => {
        build(valid());

        component.save();

        expect(sent.length).toBe(1);
        expect(sent[0].wafarAmount).toBeNull();
        expect(sent[0].wafarTypeId).toBeNull();
    });

    it('still removes a wafar the user unticked', () => {
        build(valid({ wafarAmount: 800, wafarTypeId: 2 }));
        expect(component.hasWafar).toBeTrue();

        component.hasWafar = false;
        component.save();

        expect(sent[0].wafarAmount).withContext('0 is what the executor reads as "remove"').toBe(0);
        expect(sent[0].wafarTypeId).toBe(0);
    });

    it('refuses "Has wafar" ticked with no amount, rather than saving 0', () => {
        build(valid());

        component.hasWafar = true;
        component.wafarAmount = null;
        component.save();

        expect(component.isWafarAmountInvalid()).toBeTrue();
        expect(sent).toEqual([]);
    });
});
