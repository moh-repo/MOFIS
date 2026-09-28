import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MessageService, ConfirmationService } from 'primeng/api';
import { of, throwError } from 'rxjs';

import { FinanceComponent } from './finance.component';
import { FinanceFormComponent } from './finance-form/finance-form.component';
import { StudentFinance } from '../../api/studentfinance';
import { StudentFinanceService } from '../../service/studentfinance.service';
import { ApprovalService } from '../../service/approval.service';
import { FinanceLookupsService, emptyFinanceLookups } from '../../service/finance-lookups.service';
import { LookupService } from '../../service/lookup.service';
import { StudentdataService } from '../../service/studentdata.service';
import { FinanceReceivedWhomService } from '../../service/finance-received-whom.service';
import { TranslationService } from '../../service/translation.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

const translationStub = {
  currentLanguage$: of('en'),
  translate: (k: string) => k,
  getCurrentLanguage: () => 'en',
  isRTL: () => false
};

/**
 * Net Amount is what the student actually receives, so the list has to show it for
 * every row, not just rows the server has already computed a NET_AMOUNT for.
 */
describe('FinanceComponent net amount column', () => {
  let component: FinanceComponent;

  beforeEach(() => {
    const financeService = jasmine.createSpyObj<StudentFinanceService>(
      'StudentFinanceService', ['getPagingAllAdvanced', 'searchFinance', 'getAll', 'Delete']
    );
    financeService.getPagingAllAdvanced.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 0 } as any)
    );

    const lookups = jasmine.createSpyObj<FinanceLookupsService>(
      'FinanceLookupsService', ['loadAll', 'resolveName', 'resolveCurrencyName']
    );
    lookups.loadAll.and.returnValue(of(emptyFinanceLookups()));

    TestBed.configureTestingModule({
    imports: [],
    providers: [
        FinanceComponent,
        MessageService,
        ConfirmationService,
        { provide: StudentFinanceService, useValue: financeService },
        { provide: FinanceLookupsService, useValue: lookups },
        { provide: TranslationService, useValue: translationStub },
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting()
    ]
});

    component = TestBed.inject(FinanceComponent);
  });

  it('uses the server NET_AMOUNT column when it is present', () => {
    const row = { studentFinRecievedAmount: 5000, wafarAmount: 1200, netAmount: 3800 } as StudentFinance;

    expect(component.getNetAmount(row)).toBe(3800);
  });

  /*
   * NET_AMOUNT only exists once V015 has been applied. Against an un-migrated database
   * the server sends null, and a blank Net column on every row would look like the
   * feature simply does not work.
   */
  it('falls back to amount minus wafar when the column is absent', () => {
    const row = { studentFinRecievedAmount: 5000, wafarAmount: 1200 } as StudentFinance;

    expect(component.getNetAmount(row)).toBe(3800);
  });

  it('equals the amount received when there is no wafar', () => {
    const row = { studentFinRecievedAmount: 5000, wafarAmount: 0 } as StudentFinance;

    expect(component.getNetAmount(row)).toBe(5000);
  });
});

/**
 * The form's Net Amount field sits outside the "Has wafar" block, so it is on screen
 * with the checkbox unticked. Unticking does not clear wafarAmount — the field is only
 * hidden, and save() zeroes it on submit — so the getter has to gate on hasWafar or it
 * keeps subtracting a wafar the record is no longer going to have.
 */
describe('FinanceFormComponent net amount', () => {
  let component: FinanceFormComponent;

  beforeEach(() => {
    const financeService = jasmine.createSpyObj<StudentFinanceService>(
      'StudentFinanceService', ['getById', 'getWafar', 'Update', 'Create']
    );
    financeService.getById.and.returnValue(of({ id: 1, studentFinName: 'X' } as any));
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

    TestBed.configureTestingModule({
    imports: [],
    providers: [
        FinanceFormComponent,
        MessageService,
        { provide: StudentFinanceService, useValue: financeService },
        { provide: ApprovalService, useValue: approvalService },
        { provide: FinanceLookupsService, useValue: lookups },
        { provide: LookupService, useValue: lookupService },
        { provide: StudentdataService, useValue: studentdata },
        { provide: FinanceReceivedWhomService, useValue: {} },
        { provide: TranslationService, useValue: translationStub },
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
  });

  it('subtracts the wafar while "Has wafar" is ticked', () => {
    component.model.studentFinRecievedAmount = 5000;
    component.hasWafar = true;
    component.wafarAmount = 1200;

    expect(component.netAmount).toBe(3800);
  });

  it('ignores a leftover wafar amount once "Has wafar" is unticked', () => {
    component.model.studentFinRecievedAmount = 5000;
    component.hasWafar = true;
    component.wafarAmount = 1200;

    component.hasWafar = false;

    expect(component.netAmount).toBe(5000);
  });
});
