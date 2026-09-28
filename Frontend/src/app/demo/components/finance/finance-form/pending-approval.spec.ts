import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Observable, of, throwError } from 'rxjs';

import { FinanceFormComponent } from './finance-form.component';
import { StudentFinanceService } from '../../../service/studentfinance.service';
import { ApprovalService, ApprovalSummary } from '../../../service/approval.service';
import { FinanceLookupsService, emptyFinanceLookups } from '../../../service/finance-lookups.service';
import { LookupService } from '../../../service/lookup.service';
import { StudentdataService } from '../../../service/studentdata.service';
import { FinanceReceivedWhomService } from '../../../service/finance-received-whom.service';
import { TranslationService } from '../../../service/translation.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

/**
 * ApprovalWorkflowService refuses a second request while one is undecided, so
 * StudentFinanceController answers 400 "There is already a pending approval
 * request for this entity". The form checks for that up front and locks itself,
 * instead of letting the user fill everything in and be rejected on save.
 */
describe('FinanceFormComponent pending-approval guard', () => {
  let component: FinanceFormComponent;
  let approvalService: jasmine.SpyObj<ApprovalService>;
  let financeService: jasmine.SpyObj<StudentFinanceService>;

  const pending: ApprovalSummary = {
    id: 7,
    entityType: 'STUDENT_FINANCE',
    entityId: '1',
    requestType: 'UPDATE',
    status: 'PENDING',
    requestedAt: '2026-07-28T10:00:00Z',
    requesterFullName: 'Test User'
  };

  function setup(pendingResult: Observable<ApprovalSummary[]>) {
    approvalService = jasmine.createSpyObj<ApprovalService>('ApprovalService', ['getPendingForEntity']);
    approvalService.getPendingForEntity.and.returnValue(pendingResult as any);

    financeService = jasmine.createSpyObj<StudentFinanceService>(
      'StudentFinanceService', ['getById', 'getWafar', 'Update', 'Create']
    );
    financeService.getById.and.returnValue(of({ id: 1, studentFinName: 'X' } as any));
    financeService.getWafar.and.returnValue(throwError(() => ({ status: 404 })));

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
        {
            provide: TranslationService,
            useValue: {
                currentLanguage$: of('en'),
                translate: (k: string) => k,
                getCurrentLanguage: () => 'en',
                isRTL: () => false
            }
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
  }

  it('locks the form when a request is already awaiting a decision', () => {
    setup(of([pending]));

    expect(approvalService.getPendingForEntity).toHaveBeenCalledWith('STUDENT_FINANCE', 1);
    expect(component.pendingApproval).toEqual(pending);

    component.save();
    expect(financeService.Update).not.toHaveBeenCalled();
  });

  it('allows saving when nothing is pending', () => {
    setup(of([]));

    expect(component.pendingApproval).toBeNull();
  });

  /*
   * The endpoint sits behind the APPROVALS permission. A finance user who cannot
   * read it must keep editing, not be locked out of their own screen.
   */
  it('leaves the form editable when the pending check is not permitted', () => {
    setup(throwError(() => ({ status: 403 })));

    expect(component.pendingApproval).toBeNull();
  });
});
