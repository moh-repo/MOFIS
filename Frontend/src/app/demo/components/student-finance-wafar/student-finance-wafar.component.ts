import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentFinanceWafar } from '../../api/student-finance-wafar';
import { StudentFinanceWafarService } from '../../service/student-finance-wafar.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { StudyCountryIn } from '../../api/study-country-in';
import { StudyGovIn } from '../../api/study-gov-in';
import { StudyFinanceBank } from '../../api/study-finance-bank';
import { StudyFinanceBankBranch } from '../../api/study-finance-bank-branch';
import { StudyFinanceDof } from '../../api/study-finance-dof';
import { StudyFinReceivedStatus } from '../../api/study-fin-received-status';
import { StudyWafarType } from '../../api/study-wafar-type';
import { TranslationService } from '../../service/translation.service';
import { StudyWafarTypeService } from '../../service/study-wafar-type.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { StudentFinance } from '../../api/studentfinance';
import { StudentFinanceService } from '../../service/studentfinance.service';
import { StudyUniversityFeeService } from '../../service/studyuniversityfee.service';
import { extractApiErrorMessage } from '../../service/api-error.util';
import { resetPaging } from '../../service/table-paging.util';
import { ApprovalService, ApprovalSummary } from '../../service/approval.service';
import { localizeField } from '../../pipes/in-language.pipe';

@Component({
    selector: 'app-student-finance-wafar',
    templateUrl: './student-finance-wafar.component.html',
    styleUrls: ['./student-finance-wafar.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentFinanceWafarComponent implements OnInit {

  records: StudentFinanceWafar[] = [];
  filteredRecords: StudentFinanceWafar[] = [];
  loading = false;

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';

  form: FormGroup;
  selectedRecord: StudentFinanceWafar | null = null;

  /** An undecided approval request for the record being edited; blocks a second one. */
  pendingApproval: ApprovalSummary | null = null;

  /**
   * Students whose wafar has a change awaiting a decision (review F9). A row here
   * changes only once the request is approved, so without a mark the list looked
   * as if a save had done nothing.
   */
  pendingIds = new Set<number>();
  /** Student ids of pending wafar requests, to count the adds that have no row yet. */
  private pendingWafarIds: number[] = [];

  studyCountries: StudyCountryIn[] = [];
  studyGovernments: StudyGovIn[] = [];
  studyFinanceBanks: StudyFinanceBank[] = [];
  studyFinanceBankBranches: StudyFinanceBankBranch[] = [];
  studyFinanceDofs: StudyFinanceDof[] = [];
  studyFinReceivedStatuses: StudyFinReceivedStatus[] = [];
  studyWafarTypes: StudyWafarType[] = [];
  studyCurrencies: LookupItem[] = [];
  currencyLabel = 'USD';
  currencyCode = 'USD';

  // Student finance / university fee lookup data
  studentFinances: StudentFinance[] = [];
  universityFees: any[] = [];
  isUniversityFeeType = false;

  // Precomputed dropdown option lists — updated only when source data changes
  studentFinancesForDropdown: { id: number; label: string }[] = [];
  universityFeesForDropdown: { id: number; label: string }[] = [];
  activeStudentDropdownOptions: { id: number; label: string }[] = [];

  filters = {
    search: '',
    status: null as number | null,
    country: null as number | null,
    wafarType: null as number | null
  };

  stats = {
    totalRecords: 0,
    totalAmount: 0,
    averageAmount: 0,
    uniqueCountries: 0,
    lastUpdated: null as Date | null
  };

  tableRows = 10;
  rowsPerPageOptions = [10, 20, 50];
  /** Bound to the table, so a filter that shrinks the list starts it on page one (QA B8, N9). */
  first = 0;

  private allStudyGovernments: StudyGovIn[] = [];
  private allStudyFinanceBanks: StudyFinanceBank[] = [];
  private allStudyFinanceBankBranches: StudyFinanceBankBranch[] = [];

  constructor(
    private fb: FormBuilder,
    private service: StudentFinanceWafarService,
    private lookupService: LookupService,
    private studyWafarTypeService: StudyWafarTypeService,
    private studentFinanceService: StudentFinanceService,
    private universityFeeService: StudyUniversityFeeService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private approvalService: ApprovalService,
    public translationService: TranslationService,
    private permissionService: UmsPermissionService
  ) {
    this.form = this.fb.group({
      id: [{ value: null, disabled: true }],
      /*
       * STUDENT_FINANCE_WAFAR.STUDENT_ID is the primary key and is assigned by hand, so a
       * record cannot be saved without one. It comes from whichever record is picked below,
       * which is why that dropdown is required.
       */
      studentId: [{ value: null, disabled: true }],
      financeLookupId: [null, Validators.required],
      studentFinId: ['', Validators.required],
      studentFinName: ['', Validators.required],
      studentFinRecievedYear: [null, Validators.required],
      studentFinRecievedMonth: [null, Validators.required],
      studentFinRecievedYearMonth: [null],
      studentFinRecievedAmount: [null, [Validators.required, Validators.min(0)]],
      studyCountryInId: [null, Validators.required],
      studyGovInId: [null],
      studyFinanceBankeId: [null],
      studyFinanceBankBranchId: [null],
      studyFinanceDofId: [null],
      studyFinRecievedStatus: [null],
      wafarTypeId: [null, Validators.required],
      studyFinanceMidate: [null],
      studyNote1: ['']
    });
  }

  ngOnInit(): void {
    this.loadLookupData();
    // The finance and fee lists feed the form's record picker only, and are finance
    // reads: they load when the form opens (live QA M6), not for every Wafar viewer.
    this.loadRecords();
    this.keepYearMonthInStep();
  }

  /**
   * STUDENT_FIN_RECIEVED_YEAR_MONTH is the YYYYQ period key — V038 checks it
   * equals YEAR*10 + MONTH — so it follows the two fields beside it instead of
   * being typed. The control stays in the form so it is still posted.
   */
  private keepYearMonthInStep(): void {
    const sync = () => {
      const y = Number(this.form.get('studentFinRecievedYear')?.value) || 0;
      const m = Number(this.form.get('studentFinRecievedMonth')?.value) || 0;
      this.form.get('studentFinRecievedYearMonth')?.setValue(y > 0 && m > 0 ? y * 10 + m : null, { emitEvent: false });
    };
    this.form.get('studentFinRecievedYear')?.valueChanges.subscribe(sync);
    this.form.get('studentFinRecievedMonth')?.valueChanges.subscribe(sync);
  }

  get dialogTitle(): string {
    const key = this.dialogMode === 'create'
      ? 'STUDENT_FINANCE_WAFAR.FORM.TITLE_CREATE'
      : 'STUDENT_FINANCE_WAFAR.FORM.TITLE_EDIT';
    return this.translationService.translate(key);
  }

  get dialogFormStyleClass(): string {
    return 'finance-form-dialog' + (this.translationService.isRTL() ? ' rtl' : '');
  }

  get dialogViewStyleClass(): string {
    return 'finance-view-dialog' + (this.translationService.isRTL() ? ' rtl' : '');
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadRecords(): void {
    this.loading = true;
    this.loadPending();
    this.service.getAll().subscribe({
      next: (records) => {
        this.records = records ?? [];
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  private loadPending(): void {
    this.service.pending().pipe(catchError(() => of({ wafar: [], finance: [] }))).subscribe(res => {
      const toIds = (ids: string[] | undefined) => (ids ?? []).map(Number).filter(n => n > 0);
      this.pendingWafarIds = toIds(res?.wafar);
      this.pendingIds = new Set([...this.pendingWafarIds, ...toIds(res?.finance)]);
    });
  }

  isPending(record: StudentFinanceWafar): boolean {
    return record?.id != null && this.pendingIds.has(Number(record.id));
  }

  /** Wafar adds awaiting approval: they have no row in the list until approved. */
  get pendingNewCount(): number {
    const rowIds = new Set((this.records ?? []).map(r => Number(r.id)));
    return new Set(this.pendingWafarIds.filter(id => !rowIds.has(id))).size;
  }

  loadLookupData(): void {
    forkJoin({
      countries: this.lookupService.getAllStudyCountries().pipe(catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_COUNTRIES_ERROR'); return of([]); })),
      governments: this.lookupService.getAllStudyGovernments().pipe(catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_GOVS_ERROR'); return of([]); })),
      banks: this.lookupService.getAllStudyFinanceBanks().pipe(catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_BANKS_ERROR'); return of([]); })),
      branches: this.lookupService.getAllStudyFinanceBankBranches().pipe(catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_BRANCHES_ERROR'); return of([]); })),
      dofs: this.lookupService.getAllStudyFinanceDofs().pipe(catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_DOF_ERROR'); return of([]); })),
      statuses: this.lookupService.getAllStudyFinReceivedStatuses().pipe(catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_STATUSES_ERROR'); return of([]); })),
      wafarTypes: this.studyWafarTypeService.getAll().pipe(catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_WAFAR_TYPES_ERROR'); return of([]); }))
    }).subscribe({
      next: (res) => {
        const allowedIds = this.permissionService.getAllowedCountryIds();
        const allCountries: StudyCountryIn[] = res.countries ?? [];
        this.studyCountries = allowedIds.length > 0
          ? allCountries.filter(c => allowedIds.includes(c.id))
          : allCountries;
        this.allStudyGovernments = res.governments ?? [];
        this.allStudyFinanceBanks = res.banks ?? [];
        this.allStudyFinanceBankBranches = res.branches ?? [];
        this.studyFinanceDofs = res.dofs ?? [];
        this.studyFinReceivedStatuses = res.statuses ?? [];
        // Shown in the reader's language: options, table tags and the chips (QA B2).
        this.studyWafarTypes = localizeField(res.wafarTypes ?? [], 'wafarName', this.translationService.getCurrentLanguage());
        this.applyFilters();
      },
      error: () => this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_ERROR')
    });

    this.lookupService.getStudyCurrencies().subscribe({
      next: (currencies) => { this.studyCurrencies = currencies ?? []; },
      error: () => this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_CURRENCY_ERROR')
    });
    this.lookupService.getDefaultCurrency().subscribe({
      next: (currency) => this.updateCurrencyLabel(currency),
      error: () => this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_CURRENCY_ERROR')
    });
  }

  loadStudentData(): void {
    this.studentFinanceService.getAll().pipe(
      catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_STUDENTS_ERROR'); return of([]); })
    ).subscribe(data => {
      this.studentFinances = data ?? [];
      this.rebuildDropdownOptions();
    });

    this.universityFeeService.getAllFees().pipe(
      catchError(() => { this.showWarning('STUDENT_FINANCE_WAFAR.MESSAGES.LOAD_UNIVERSITY_FEES_ERROR'); return of([]); })
    ).subscribe(data => {
      this.universityFees = data ?? [];
      this.rebuildDropdownOptions();
    });
  }

  private rebuildDropdownOptions(): void {
    this.studentFinancesForDropdown = this.studentFinances.map(sf => ({
      id: sf.id,
      label: [sf.studentFinId, sf.studentFinName].filter(Boolean).join(' — ')
    }));
    this.universityFeesForDropdown = this.universityFees.map((f: any) => ({
      id: f.id,
      label: [f.studyUniversityFeeStudentName, f.studyUniversityFeeYear].filter(Boolean).join(' | ')
    }));
    this.activeStudentDropdownOptions = this.isUniversityFeeType
      ? this.universityFeesForDropdown
      : this.studentFinancesForDropdown;
    // An edit opened before the lists arrived picks its record once they are here.
    const picker = this.form.get('financeLookupId');
    if (this.dialogMode === 'edit' && this.selectedRecord && picker?.value == null) {
      picker?.setValue(this.findFinanceLookupId(this.selectedRecord), { emitEvent: false });
    }
  }

  // ── Wafar type + student lookup logic ────────────────────────────────────

  onWafarTypeChange(typeId: number | null): void {
    const wasUniversity = this.isUniversityFeeType;
    this.isUniversityFeeType = this.checkIsUniversityFeeType(typeId);
    // Swap the active source list when category changes
    if (wasUniversity !== this.isUniversityFeeType) {
      this.activeStudentDropdownOptions = this.isUniversityFeeType
        ? this.universityFeesForDropdown
        : this.studentFinancesForDropdown;
      this.form.patchValue({ financeLookupId: null, studentFinId: '', studentFinName: '' });
    }
  }

  /**
   * Wafar type ids that draw on a university fee rather than a stipend record.
   *
   * MOHECORE.STUDY_WAFAR_TYPE has no code column, so the id is the only stable
   * key: 8 = خصم رسوم دراسية (Tuition Fee Deduction). The name test it replaces
   * matched on 'رسوم' / 'university', which is display text the lookup editor
   * can change at any time.
   */
  private static readonly UNIVERSITY_FEE_WAFAR_TYPE_IDS = new Set<number>([8]);

  private checkIsUniversityFeeType(typeId: number | null): boolean {
    return typeId != null && StudentFinanceWafarComponent.UNIVERSITY_FEE_WAFAR_TYPE_IDS.has(Number(typeId));
  }

  /**
   * The two dropdowns are keyed differently, which is why the student id is resolved here
   * rather than taken straight from the selected value: for a finance record the option id
   * IS the student id (StudentFinance's PK is STUDENT_ID), but for a university fee it is
   * the fee row's own id and the student is a separate field.
   */
  onStudentRecordSelected(recordId: number | null): void {
    if (!recordId) {
      this.form.patchValue({ studentId: null, studentFinId: '', studentFinName: '' });
      return;
    }

    if (this.isUniversityFeeType) {
      const fee = this.universityFees.find((f: any) => f.id === recordId);
      if (fee) {
        this.form.patchValue({
          studentId: fee.studentId ?? null,
          studentFinId: String(fee.studentId ?? ''),
          studentFinName: fee.studyUniversityFeeStudentName ?? '',
          studentFinRecievedAmount: fee.studyUniversityFeeAmount ?? null,
          studentFinRecievedYear: fee.studyUniversityFeeYear ? parseInt(fee.studyUniversityFeeYear, 10) : null,
        });
      }
    } else {
      const sf = this.studentFinances.find(f => f.id === recordId);
      if (sf) {
        this.form.patchValue({
          studentId: sf.id ?? null,
          studentFinId: sf.studentFinId ?? '',
          studentFinName: sf.studentFinName ?? '',
          studentFinRecievedYear: sf.studentFinRecievedYear ?? null,
          studentFinRecievedMonth: sf.studentFinRecievedMonth ?? null,
          studentFinRecievedYearMonth: sf.studentFinRecievedYearMonth ?? null,
          // The field is مبلغ الوفر. It was filled with the stipend (the finance
          // record's received amount); the wafar on that record is its WAFAR_AMOUNT
          // (review F9). Left empty when the record has none.
          studentFinRecievedAmount: (sf.wafarAmount ?? 0) > 0 ? sf.wafarAmount : null,
          studyCountryInId: sf.studyCountryInId ?? null,
          studyGovInId: sf.studyGovInId ?? null,
          studyFinanceBankeId: sf.studyFinanceBankeId ?? null,
          studyFinanceBankBranchId: sf.studyFinanceBankBranchId ?? null,
          studyFinanceDofId: sf.studyFinanceDofId ?? null,
          studyFinRecievedStatus: sf.studyFinRecievedStatus ?? null,
        });
        this.onCountryChange(sf.studyCountryInId ?? null);
      }
    }
  }

  /**
   * Preselects the record this wafar belongs to when the edit dialog opens.
   *
   * Matched on the student id rather than the studentFinId text: a wafar row's own id IS
   * the student id, so it is exact, whereas studentFinId is a business code that is not
   * guaranteed unique — a near miss there would silently point the dialog at a different
   * student, and reselecting would then move the wafar onto them.
   */
  private findFinanceLookupId(record: StudentFinanceWafar): number | null {
    if (!record.id) return null;
    if (this.checkIsUniversityFeeType(record.wafarTypeId)) {
      const fee = this.universityFees.find((f: any) => Number(f.studentId) === Number(record.id));
      return fee ? fee.id : null;
    }
    const sf = this.studentFinances.find(f => Number(f.id) === Number(record.id));
    return sf ? sf.id : null;
  }

  // ── Currency helpers ──────────────────────────────────────────────────────

  /** The statement's currency is STUDY_CURRENCY_IN's default row (IS_DEFAULT), not a name match. */
  private updateCurrencyLabel(preferred: LookupItem | null | undefined): void {
    if (!preferred) {
      return;
    }
    const displayName = preferred.name || preferred.label || this.currencyLabel;
    const candidateSources = [preferred.value, preferred.name, preferred.label];
    const resolvedCode = candidateSources
      .map(source => (typeof source === 'string' ? this.extractCurrencyCode(source) : null))
      .find(code => !!code) || this.currencyCode;

    this.currencyLabel = displayName;
    this.currencyCode = resolvedCode;
  }

  private extractCurrencyCode(source: string | null | undefined): string {
    if (!source) {
      return this.currencyCode;
    }

    const trimmed = source.trim();
    const isoMatch = trimmed.match(/([A-Z]{3})/);
    if (isoMatch) {
      return isoMatch[1];
    }

    if (trimmed.length <= 4) {
      return trimmed.toUpperCase();
    }

    return this.currencyCode;
  }

  private showWarning(messageKey: string): void {
    this.messageService.add({
      severity: 'warn',
      summary: this.translationService.translate('COMMON.WARNING'),
      detail: this.translationService.translate(messageKey)
    });
  }

  // ── Cascading country/bank dropdowns ─────────────────────────────────────

  onCountryChange(countryId: number | null): void {
    if (!countryId) {
      this.studyGovernments = [];
      this.studyFinanceBanks = [];
      this.studyFinanceBankBranches = [];
      this.form.patchValue({
        studyGovInId: null,
        studyFinanceBankeId: null,
        studyFinanceBankBranchId: null
      });
      return;
    }

    this.studyGovernments = this.allStudyGovernments.filter(g => g.studyCountryInId === countryId);
    this.studyFinanceBanks = this.allStudyFinanceBanks.filter(b => b.studyCountryInId === countryId);

    this.studyFinanceBankBranches = [];
    this.form.patchValue({
      studyGovInId: null,
      studyFinanceBankeId: null,
      studyFinanceBankBranchId: null
    });
  }

  onBankChange(bankId: number | null): void {
    if (!bankId) {
      this.studyFinanceBankBranches = [];
      this.form.patchValue({ studyFinanceBankBranchId: null });
      return;
    }

    this.studyFinanceBankBranches = this.allStudyFinanceBankBranches.filter(branch => branch.studyFinanceBankeId === bankId);
  }

  // ── Dialog open/close ─────────────────────────────────────────────────────

  /**
   * The workflow refuses a second request while one is undecided, so the form is
   * locked rather than letting the user fill it in and be rejected on save.
   *
   * The endpoint sits behind the APPROVALS permission; a user who cannot read it
   * keeps the old behaviour and finds out at save time instead of being locked
   * out of their own screen.
   */
  private checkPendingApproval(recordId: number | null | undefined): void {
    this.pendingApproval = null;
    if (!recordId || recordId <= 0) {
      return;
    }
    this.approvalService.getPendingForEntity('STUDENT_FINANCE_WAFAR', recordId)
      .pipe(catchError(() => of([] as ApprovalSummary[])))
      .subscribe(requests => {
        this.pendingApproval = requests?.length ? requests[0] : null;
      });
  }

  openCreateDialog(): void {
    // Finance records and fees change on their own screens; pick from what is there now (F9).
    this.loadStudentData();
    this.dialogMode = 'create';
    this.pendingApproval = null;
    this.selectedRecord = null;
    this.isUniversityFeeType = false;
    this.activeStudentDropdownOptions = this.studentFinancesForDropdown;
    this.formDialogVisible = true;
    this.studyGovernments = [];
    this.studyFinanceBanks = [];
    this.studyFinanceBankBranches = [];
    this.form.reset({
      id: null,
      studentId: null,
      financeLookupId: null,
      studentFinId: '',
      studentFinName: '',
      studentFinRecievedYear: null,
      studentFinRecievedMonth: null,
      studentFinRecievedYearMonth: null,
      studentFinRecievedAmount: null,
      studyCountryInId: null,
      studyGovInId: null,
      studyFinanceBankeId: null,
      studyFinanceBankBranchId: null,
      studyFinanceDofId: null,
      studyFinRecievedStatus: null,
      wafarTypeId: null,
      studyFinanceMidate: null,
      studyNote1: ''
    });
  }

  openEditDialog(record: StudentFinanceWafar): void {
    this.loadStudentData();
    this.dialogMode = 'edit';
    this.selectedRecord = record;
    this.checkPendingApproval(record.id);
    this.isUniversityFeeType = this.checkIsUniversityFeeType(record.wafarTypeId);
    this.activeStudentDropdownOptions = this.isUniversityFeeType
      ? this.universityFeesForDropdown
      : this.studentFinancesForDropdown;
    this.formDialogVisible = true;

    if (record.studyCountryInId) {
      this.studyGovernments = this.allStudyGovernments.filter(g => g.studyCountryInId === record.studyCountryInId);
      this.studyFinanceBanks = this.allStudyFinanceBanks.filter(b => b.studyCountryInId === record.studyCountryInId);
      if (record.studyFinanceBankeId) {
        this.studyFinanceBankBranches = this.allStudyFinanceBankBranches.filter(branch => branch.studyFinanceBankeId === record.studyFinanceBankeId);
      } else {
        this.studyFinanceBankBranches = [];
      }
    } else {
      this.studyGovernments = [];
      this.studyFinanceBanks = [];
      this.studyFinanceBankBranches = [];
    }

    this.form.reset({
      id: record.id ?? null,
      studentId: record.id ?? null,
      financeLookupId: this.findFinanceLookupId(record),
      studentFinId: record.studentFinId ?? '',
      studentFinName: record.studentFinName ?? '',
      studentFinRecievedYear: record.studentFinRecievedYear ?? null,
      studentFinRecievedMonth: record.studentFinRecievedMonth ?? null,
      studentFinRecievedYearMonth: record.studentFinRecievedYearMonth ?? null,
      studentFinRecievedAmount: record.studentFinRecievedAmount ?? null,
      studyCountryInId: record.studyCountryInId ?? null,
      studyGovInId: record.studyGovInId ?? null,
      studyFinanceBankeId: record.studyFinanceBankeId ?? null,
      studyFinanceBankBranchId: record.studyFinanceBankBranchId ?? null,
      studyFinanceDofId: record.studyFinanceDofId ?? null,
      studyFinRecievedStatus: record.studyFinRecievedStatus ?? null,
      wafarTypeId: record.wafarTypeId ?? null,
      studyFinanceMidate: this.toDate(record.studyFinanceMidate),
      studyNote1: record.studyNote1 ?? ''
    });
  }

  openViewDialog(record: StudentFinanceWafar): void {
    this.selectedRecord = record;
    this.viewDialogVisible = true;
  }

  hideFormDialog(): void {
    this.formDialogVisible = false;
    this.isUniversityFeeType = false;
    this.form.reset();
  }

  // ── Save / Delete ─────────────────────────────────────────────────────────

  saveRecord(): void {
    if (this.pendingApproval) {
      this.messageService.add({
        severity: 'warn',
        summary: this.translationService.translate('FINANCE.PENDING_APPROVAL_TITLE'),
        detail: this.translationService.translate('FINANCE.PENDING_APPROVAL_MESSAGE'),
        life: 8000
      });
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.mapFormToPayload();
    const request$ = this.dialogMode === 'create'
      ? this.service.create(payload)
      : this.service.update(payload.id!, payload);

    request$.subscribe({
      next: (response: any) => {
        const messageKey = this.dialogMode === 'create'
          ? 'STUDENT_FINANCE_WAFAR.MESSAGES.CREATE_SUCCESS'
          : 'STUDENT_FINANCE_WAFAR.MESSAGES.UPDATE_SUCCESS';
        // Writes go through the approval workflow, so the backend answers with the
        // "submitted for approval" message rather than a saved record.
        const detail = (response?.status === 'PENDING' && response?.message)
          ? response.message
          : this.translationService.translate(messageKey);
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail
        });
        this.hideFormDialog();
        this.loadRecords();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: extractApiErrorMessage(error)
            || this.translationService.translate('STUDENT_FINANCE_WAFAR.MESSAGES.SAVE_ERROR'),
          life: 10000
        });
      }
    });
  }

  confirmDelete(record: StudentFinanceWafar): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('STUDENT_FINANCE_WAFAR.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('STUDENT_FINANCE_WAFAR.DELETE_CONFIRM_MESSAGE')
        .replace('{name}', record.studentFinName ?? ''),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteRecord(record)
    });
  }

  private deleteRecord(record: StudentFinanceWafar): void {
    if (!record.id) {
      return;
    }
    this.service.delete(record.id).subscribe({
      next: (response: any) => {
        // Deletion is queued for approval; the row stays until it is approved.
        const detail = (response?.status === 'PENDING' && response?.message)
          ? response.message
          : this.translationService.translate('STUDENT_FINANCE_WAFAR.MESSAGES.DELETE_SUCCESS');
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail
        });
        this.loadRecords();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: extractApiErrorMessage(error)
            || this.translationService.translate('STUDENT_FINANCE_WAFAR.MESSAGES.DELETE_ERROR'),
          life: 10000
        });
      }
    });
  }

  // ── Filters ───────────────────────────────────────────────────────────────

  onFiltersChanged(): void {
    resetPaging(this);
    this.applyFilters();
  }

  clearFilters(): void {
    this.filters = {
      search: '',
      status: null,
      country: null,
      wafarType: null
    };
    resetPaging(this);
    this.applyFilters();
  }

  get tableReportTemplate(): string {
    return this.translationService.translate('STUDENT_FINANCE_WAFAR.TABLE.REPORT_TEMPLATE');
  }

  get filterResultsLabel(): string {
    return this.formatMessage('STUDENT_FINANCE_WAFAR.FILTERS.RESULTS', {
      count: this.filteredRecords.length,
      total: this.records.length
    });
  }

  get tableCaptionLabel(): string {
    return this.formatMessage('STUDENT_FINANCE_WAFAR.TABLE.CAPTION', {
      count: this.filteredRecords.length
    });
  }

  get hasActiveFilters(): boolean {
    return !!(
      this.filters.search ||
      this.filters.status ||
      this.filters.country ||
      this.filters.wafarType
    );
  }

  /**
   * Tag colour by STUDY_FIN_RECEIVED_STATUS id (no code column exists):
   * 1 Received, 100 Disbursed, 101 Partially Disbursed → success;
   * 102 Under Settlement, 105 On Hold → warning; 103 Rejected, 104 Returned → danger.
   * The name test it replaces looked for 'مستلم', which the Arabic seed
   * ("تم الاستلام") never contained.
   */
  getStatusSeverity(id: number | null | undefined): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (Number(id)) {
      case 1: case 100: case 101: return 'success';
      case 102: case 105: return 'warning';
      case 103: case 104: return 'danger';
      case 2: return 'info';
      default: return 'secondary';
    }
  }

  private applyFilters(): void {
    const search = this.filters.search.trim().toLowerCase();
    const status = this.filters.status;
    const country = this.filters.country;
    const wafarType = this.filters.wafarType;

    const workingRecords = this.records ?? [];

    this.filteredRecords = workingRecords.filter((record) => {
      const matchesSearch = !search || this.buildSearchHaystack(record).includes(search);
      const matchesStatus = !status || record.studyFinRecievedStatus === status;
      const matchesCountry = !country || record.studyCountryInId === country;
      const matchesWafarType = !wafarType || record.wafarTypeId === wafarType;
      return matchesSearch && matchesStatus && matchesCountry && matchesWafarType;
    });

    this.calculateStats();
  }

  private calculateStats(): void {
    const totalRecords = this.filteredRecords.length;
    const totalAmount = this.filteredRecords
      .map(record => record.studentFinRecievedAmount ?? 0)
      .reduce((sum, value) => sum + (value || 0), 0);

    const uniqueCountries = new Set(
      this.filteredRecords
        .map(record => record.studyCountryInId)
        .filter((id): id is number => !!id)
    ).size;

    const lastUpdated = this.filteredRecords.reduce<Date | null>((latest, record) => {
      const date = this.toDate(record.studyFinanceMidate);
      if (!date) {
        return latest;
      }
      if (!latest || date > latest) {
        return date;
      }
      return latest;
    }, null);

    this.stats = {
      totalRecords,
      totalAmount,
      averageAmount: totalRecords ? totalAmount / totalRecords : 0,
      uniqueCountries,
      lastUpdated
    };
  }

  private buildSearchHaystack(record: StudentFinanceWafar): string {
    const tokens = [
      record.studentFinId,
      record.studentFinName,
      this.getCountryName(record.studyCountryInId),
      this.getStatusName(record.studyFinRecievedStatus),
      this.getWafarTypeName(record.wafarTypeId),
      record.studentFinRecievedAmount?.toString(),
      record.studyFinanceDofId?.toString()
    ];

    return tokens
      .filter((token): token is string => !!token)
      .map(token => token.toLowerCase())
      .join(' ');
  }

  private formatMessage(key: string, replacements: Record<string, string | number> = {}): string {
    let message = this.translationService.translate(key);
    Object.entries(replacements).forEach(([placeholder, value]) => {
      message = message.replace(`{${placeholder}}`, String(value));
    });
    return message;
  }

  private mapFormToPayload(): StudentFinanceWafar {
    const raw = this.form.getRawValue();
    // financeLookupId is a UI-only helper and must not be sent to the backend
    return {
      /*
       * The wafar row's key is the student id. On edit it is already on the record; on
       * create it comes from the record the user picked. Sending it as undefined is what
       * made the approval executor fail with "ids for this class must be manually assigned".
       */
      id: raw.id ?? raw.studentId ?? undefined,
      studentFinId: raw.studentFinId ?? '',
      studentFinName: raw.studentFinName ?? '',
      studentFinRecievedYear: raw.studentFinRecievedYear ?? 0,
      studentFinRecievedMonth: raw.studentFinRecievedMonth ?? 0,
      studentFinRecievedYearMonth: raw.studentFinRecievedYearMonth ?? 0,
      studentFinRecievedAmount: raw.studentFinRecievedAmount ?? 0,
      studyCountryInId: raw.studyCountryInId ?? 0,
      studyGovInId: raw.studyGovInId ?? 0,
      studyFinanceBankeId: raw.studyFinanceBankeId ?? 0,
      studyFinanceBankBranchId: raw.studyFinanceBankBranchId ?? 0,
      studyFinanceDofId: raw.studyFinanceDofId ?? 0,
      studyFinRecievedStatus: raw.studyFinRecievedStatus ?? 0,
      wafarTypeId: raw.wafarTypeId ?? 0,
      studyFinanceMidate: this.toIsoString(raw.studyFinanceMidate),
      studyNote1: raw.studyNote1 ?? ''
    } as StudentFinanceWafar;
  }

  // ── Display helpers ───────────────────────────────────────────────────────

  getCountryName(id: number | null | undefined): string {
    if (!id) {
      return this.translationService.translate('COMMON.NOT_AVAILABLE');
    }
    return this.studyCountries.find(c => c.id === id)?.studyCountryInName
      ?? id.toString();
  }

  getGovernmentName(id: number | null | undefined): string {
    if (!id) {
      return this.translationService.translate('COMMON.NOT_AVAILABLE');
    }
    return this.allStudyGovernments.find(g => g.id === id)?.studyGovInName
      ?? id.toString();
  }

  getBankName(id: number | null | undefined): string {
    if (!id) {
      return this.translationService.translate('COMMON.NOT_AVAILABLE');
    }
    return this.allStudyFinanceBanks.find(b => b.id === id)?.studyFinanceBankeName
      ?? id.toString();
  }

  getBranchName(id: number | null | undefined): string {
    if (!id) {
      return this.translationService.translate('COMMON.NOT_AVAILABLE');
    }
    return this.allStudyFinanceBankBranches.find(b => b.id === id)?.studyFinanceBankBranchName
      ?? id.toString();
  }

  getDofName(id: number | null | undefined): string {
    if (!id) {
      return this.translationService.translate('COMMON.NOT_AVAILABLE');
    }
    return this.studyFinanceDofs.find(d => d.id === id)?.studyFinanceDofName
      ?? id.toString();
  }

  getStatusName(id: number | null | undefined): string {
    if (!id) {
      return this.translationService.translate('COMMON.NOT_AVAILABLE');
    }
    return this.studyFinReceivedStatuses.find(s => s.id === id)?.studyFinReceivedStatusName
      ?? id.toString();
  }

  getWafarTypeName(id: number | null | undefined): string {
    if (!id) {
      return this.translationService.translate('COMMON.NOT_AVAILABLE');
    }
    return this.studyWafarTypes.find(t => t.id === id)?.wafarName
      ?? id.toString();
  }

  private toDate(value: Date | string | null | undefined): Date | null {
    if (!value) {
      return null;
    }
    const date = value instanceof Date ? value : new Date(value);
    return isNaN(date.getTime()) ? null : date;
  }

  private toIsoString(value: Date | string | null | undefined): string | null {
    if (!value) {
      return null;
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    const date = new Date(value);
    return isNaN(date.getTime()) ? null : date.toISOString();
  }
}
