import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { MessageService } from 'primeng/api';
import { BehaviorSubject, NEVER, of, throwError } from 'rxjs';

import { FinanceFormComponent } from './finance-form.component';
import { StudentFinanceService } from '../../../service/studentfinance.service';
import { ApprovalService } from '../../../service/approval.service';
import { FinanceLookupsService, emptyFinanceLookups } from '../../../service/finance-lookups.service';
import { LookupService } from '../../../service/lookup.service';
import { StudentdataService } from '../../../service/studentdata.service';
import { FinanceReceivedWhomService } from '../../../service/finance-received-whom.service';
import { TranslationService } from '../../../service/translation.service';

/**
 * The router reuses a routed component when only a parameter changes, so a
 * navigation from /finance/edit/1003 to /finance/edit/1 does not run ngOnInit
 * again. The form used to read the id once from the snapshot and kept showing
 * record 1003 under the URL for record 1 (live QA 23 Sep 2026, M5).
 */
describe('FinanceFormComponent follows the :id in the URL', () => {

    let component: FinanceFormComponent;
    let financeService: jasmine.SpyObj<StudentFinanceService>;
    let params$: BehaviorSubject<ParamMap>;

    const record = (id: number, overrides: any = {}) => ({
        id, studentFinId: String(id), studentFinName: `Student ${id}`, studentFinRecievedAmount: 5000,
        studyCheckDate: '2026-01-10T12:00:00Z', studyFinRecievedStatus: 1, studyCountryInId: 1,
        wafarAmount: null, wafarTypeId: null, ...overrides
    });

    const build = (getById: (id: number) => any, firstId = '1003') => {
        params$ = new BehaviorSubject<ParamMap>(convertToParamMap({ id: firstId }));

        financeService = jasmine.createSpyObj<StudentFinanceService>(
            'StudentFinanceService', ['getById', 'getWafar', 'Update', 'Create']
        );
        financeService.getById.and.callFake((id: number) => getById(id));
        financeService.getWafar.and.returnValue(throwError(() => ({ status: 404 })));

        const approvalService = jasmine.createSpyObj<ApprovalService>('ApprovalService', ['getPendingForEntity']);
        approvalService.getPendingForEntity.and.returnValue(of([]) as any);

        const lookups = jasmine.createSpyObj<FinanceLookupsService>(
            'FinanceLookupsService', ['loadAll', 'reloadReceivedWhoms', 'resolveName', 'resolveCurrencyName']
        );
        lookups.loadAll.and.returnValue(of(emptyFinanceLookups()));

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
                // A live paramMap, as the router hands a reused component.
                { provide: ActivatedRoute, useValue: { paramMap: params$.asObservable(), snapshot: { paramMap: params$.value } } },
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting()
            ]
        });
        component = TestBed.inject(FinanceFormComponent);
        component.ngOnInit();
    };

    it('loads the record for each id the route emits, in turn', () => {
        build(id => of(record(id)));

        expect(financeService.getById.calls.allArgs()).toEqual([[1003]]);
        expect(component.model.id).toBe(1003);
        expect(component.creatingMode).toBeFalse();

        params$.next(convertToParamMap({ id: '1' }));

        expect(financeService.getById.calls.allArgs()).withContext('one load per id').toEqual([[1003], [1]]);
        expect(component.model.id).toBe(1);
        expect(component.model.studentFinName).toBe('Student 1');
        expect(component.selectedStudent?.studentId).toBe(1);
    });

    /*
     * The next record may be slow to arrive. Until it does, nothing of the
     * previous one may stay on the form — a wafar in particular, which save()
     * would otherwise write against the new id.
     */
    it('clears what the previous record filled in before the next one arrives', () => {
        build(id => (id === 1 ? NEVER : of(record(id, { wafarAmount: 800, wafarTypeId: 2 }))));
        expect(component.hasWafar).toBeTrue();
        expect(component.model.id).toBe(1003);

        params$.next(convertToParamMap({ id: '1' }));

        expect(component.model.id).toBe(0);
        expect(component.hasWafar).toBeFalse();
        expect(component.wafarAmount).toBeNull();
        expect(component.selectedStudent).toBeNull();
        expect(component.loading).withContext('record 1 is still loading').toBeTrue();
    });

    it('switches to create mode when the id leaves the URL', () => {
        build(id => of(record(id)));

        params$.next(convertToParamMap({}));

        expect(component.creatingMode).toBeTrue();
        expect(component.model.id).toBe(0);
        expect(financeService.getById).toHaveBeenCalledTimes(1);
    });

    it('stops following the route once destroyed', () => {
        build(id => of(record(id)));
        component.ngOnDestroy();

        params$.next(convertToParamMap({ id: '1' }));

        expect(financeService.getById).toHaveBeenCalledTimes(1);
    });
});
