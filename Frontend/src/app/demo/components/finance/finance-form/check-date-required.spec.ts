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
import { StudyFinReceivedStatus } from '../../../api/study-fin-received-status';

/**
 * The check date is required only when money moved (live QA 23 Sep 2026, H2).
 *
 * <p>STUDY_FIN_RECEIVED_STATUS 99 (NOT_SPECIFIED) is what the legacy 0 rows are
 * back-filled to, and 976 of them have no check date; the form used to refuse
 * every one of them. The backend now requires the date only for RECEIVED,
 * DISBURSED and PARTIALLY_DISBURSED, and the form applies the same rule.
 */
describe('FinanceFormComponent requires the check date only when money moved', () => {

    let component: FinanceFormComponent;
    let sent: any[];

    /** STUDY_FIN_RECEIVED_STATUS as V072 codes it. */
    const status = (id: number, code: string): StudyFinReceivedStatus =>
        ({ id, code, uiSeverity: null, studyFinReceivedStatusName: code, studyFinReceivedStatusMidate: '' });
    const statuses = [
        status(1, 'RECEIVED'), status(2, 'NOT_RECEIVED'), status(99, 'NOT_SPECIFIED'),
        status(100, 'DISBURSED'), status(101, 'PARTIALLY_DISBURSED'), status(103, 'REJECTED')
    ];

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
        lookups.loadAll.and.returnValue(of({ ...emptyFinanceLookups(), finReceivedStatuses: statuses }));

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

    /** A record the form accepts as it stands, apart from the status and date under test. */
    const valid = (overrides: any = {}) => ({
        id: 1, studentFinId: '1', studentFinName: 'X', studentFinRecievedAmount: 5000,
        studyCheckDate: null, studyFinRecievedStatus: 1, studyCountryInId: 1,
        wafarAmount: null, wafarTypeId: null, ...overrides
    });

    it('accepts NOT_SPECIFIED (99) without a check date', () => {
        build(valid({ studyFinRecievedStatus: 99 }));

        expect(component.checkDateRequired()).toBeFalse();
        expect(component.isCheckDateInvalid()).toBeFalse();
        expect(component.isFinReceivedStatusInvalid()).withContext('99 is a real status').toBeFalse();

        component.save();

        expect(sent.length).withContext('saved without a check date').toBe(1);
        expect(sent[0].studyFinRecievedStatus).toBe(99);
        expect(sent[0].studyCheckDate).toBeNull();
    });

    it('refuses RECEIVED (1) without a check date', () => {
        build(valid({ studyFinRecievedStatus: 1 }));

        expect(component.checkDateRequired()).toBeTrue();
        expect(component.isCheckDateInvalid()).toBeTrue();

        component.save();

        expect(sent).withContext('a received payment must be dated').toEqual([]);
    });

    it('accepts RECEIVED once the check is dated', () => {
        build(valid({ studyFinRecievedStatus: 1, studyCheckDate: '2026-01-10T12:00:00Z' }));

        expect(component.isCheckDateInvalid()).toBeFalse();
        component.save();
        expect(sent.length).toBe(1);
    });

    it('dates the statuses under which money moved, and no other', () => {
        build(valid());
        const required: Array<[number, boolean]> = [
            [1, true], [100, true], [101, true],
            [2, false], [99, false], [103, false]
        ];

        for (const [id, expected] of required) {
            component.model.studyFinRecievedStatus = id;
            expect(component.checkDateRequired()).withContext(`status ${id}`).toBe(expected);
        }
    });

    /*
     * The lookup is the only thing that knows a code. An id it does not hold
     * cannot be shown to need a date, and the backend applies its own rule.
     */
    it('does not require a date for a status the lookup does not know', () => {
        build(valid({ studyFinRecievedStatus: 12345 }));

        expect(component.checkDateRequired()).toBeFalse();
    });

    it('still requires a real status: 0 is unset', () => {
        build(valid({ studyFinRecievedStatus: 0 }));

        expect(component.isFinReceivedStatusInvalid()).toBeTrue();
        component.save();
        expect(sent).toEqual([]);
    });
});
