import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ReplaySubject, Subject, Subscription, forkJoin, of } from 'rxjs';
import { catchError, skip, take, takeUntil } from 'rxjs/operators';

import { StudentFinance } from '../../../api/studentfinance';
import { StudyFinReceivedWhom } from '../../../api/study-fin-received-whom';
import { StudyGovIn } from '../../../api/study-gov-in';
import { StudyFinanceBank } from '../../../api/study-finance-bank';
import { StudyFinanceBankBranch } from '../../../api/study-finance-bank-branch';

import { StudentFinanceService, FinanceNewRecordInfo } from '../../../service/studentfinance.service';
import { FinanceReceivedWhomService } from '../../../service/finance-received-whom.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { FinanceLookupsService, FinanceLookups, emptyFinanceLookups } from '../../../service/finance-lookups.service';
import { StudentPickerService, StudentOption } from '../../../service/student-picker.service';
import { TranslationService } from '../../../service/translation.service';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { ApprovalService, ApprovalSummary } from '../../../service/approval.service';

/** Cascading lists for the recipient dialog's own bank block. */
interface RecipientCascades {
  governments: StudyGovIn[];
  banks: StudyFinanceBank[];
  branches: StudyFinanceBankBranch[];
}

@Component({
    templateUrl: './finance-form.component.html',
    styleUrls: ['./finance-form.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class FinanceFormComponent implements OnInit, OnDestroy {

  creatingMode = true;
  loading = false;
  saving = false;
  submitted = false;
  loadFailed = false;

  /**
   * An undecided approval request already exists for this record. The backend
   * rejects a second one, so the form is put in read-only mode rather than
   * letting the user fill it in and hit a guaranteed rejection on save.
   */
  pendingApproval: ApprovalSummary | null = null;

  model: StudentFinance = new StudentFinance();
  lookups: FinanceLookups = emptyFinanceLookups();

  /** Cascaded subsets for the main form; the full lists live on `lookups`. */
  studyGovernments: StudyGovIn[] = [];
  studyFinanceBanks: StudyFinanceBank[] = [];
  studyFinanceBankBranches: StudyFinanceBankBranch[] = [];

  // Student picker: /studentdata/options, the list the finance roles may read.
  filteredStudents: StudentOption[] = [];
  selectedStudent: StudentOption | null = null;

  /**
   * Create mode: the picked student already has a finance record. There is one per
   * student, so the form offers that record instead of raising a second (F4).
   */
  existingRecordId: number | null = null;
  /** The pending request's changes, opened from the banner. */
  pendingDialogVisible = false;

  // Dates that the API stores as strings but the form edits as Date
  accountEndDate: Date | null = null;

  // Wafar
  hasWafar = false;
  wafarAmount: number | null = null;
  wafarTypeId: number | null = null;
  wafarNotes = '';
  /**
   * The wafar as the record had it, so a save that leaves it alone sends it back
   * as it was. It used to send 0 for an empty wafar, and the approver was shown
   * "wafar: (empty) → 0" on a change nobody made (QA B12, N10). Null on a new record.
   */
  private loadedWafar: { amount: number | null; typeId: number | null } | null = null;

  // Recipient dialog
  recipientDialogVisible = false;
  recipientDialogMode: 'create' | 'edit' = 'create';
  recipient: StudyFinReceivedWhom = new StudyFinReceivedWhom();
  recipientSubmitted = false;
  savingRecipient = false;
  recipientPassportIssueDate: Date | null = null;
  recipientPassportExpireDate: Date | null = null;
  recipientCheckDate: Date | null = null;
  recipientManualFormDate: Date | null = null;
  recipientCascades: RecipientCascades = { governments: [], banks: [], branches: [] };

  // Recipient-only lookups (LU_FAMILY_RELATIONSHIP, FIN_RECEIVED_REASON, STUDENT_COUNTRY_ALL)
  relationshipOptions: Array<{ label: string; value: string }> = [];
  reasonOptions: Array<{ label: string; value: string }> = [];
  allCountryOptions: LookupItem[] = [];

  private languageSub?: Subscription;
  private destroy$ = new Subject<void>();
  /** Fires once the first lookup load has landed; a record load waits on it. */
  private readonly lookupsReady$ = new ReplaySubject<void>(1);
  /** The id the form is showing or fetching, so a slow answer for an earlier id is dropped. */
  private requestedId: number | null = null;

  /** Received-status codes under which money moved, so the check that moved it must be dated. */
  private static readonly DATED_STATUS_CODES = ['RECEIVED', 'DISBURSED', 'PARTIALLY_DISBURSED'];

  constructor(
    private studentFinanceService: StudentFinanceService,
    private recipientService: FinanceReceivedWhomService,
    private lookupService: LookupService,
    private financeLookups: FinanceLookupsService,
    private studentPicker: StudentPickerService,
    private approvalService: ApprovalService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService
  ) { }

  ngOnInit(): void {
    // Lookups load once; the record loads on every id the route hands over.
    this.loadLookups(() => this.lookupsReady$.next());

    /*
     * Subscribed rather than read from the snapshot: the router reuses this
     * component when only :id changes (/finance/edit/1003 → /finance/edit/1),
     * and the snapshot kept showing the first record (live QA 23 Sep 2026, M5).
     * Each id clears what the previous one filled in before its record loads.
     */
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const idParam = params.get('id');
      this.creatingMode = !idParam;
      this.resetRecordState();
      if (idParam) {
        const id = Number(idParam);
        this.lookupsReady$
          .pipe(take(1), takeUntil(this.destroy$))
          .subscribe(() => this.loadRecord(id));
      }
    });

    this.languageSub = this.translationService.currentLanguage$
      .pipe(skip(1))
      .subscribe(() => this.loadLookups());
  }

  ngOnDestroy(): void {
    this.languageSub?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Everything a loaded record (or a picked student) puts on the form. */
  private resetRecordState(): void {
    this.requestedId = null;
    this.model = new StudentFinance();
    this.accountEndDate = null;
    this.selectedStudent = null;
    this.existingRecordId = null;
    this.pendingApproval = null;
    this.loadedWafar = null;
    this.hasWafar = false;
    this.wafarAmount = null;
    this.wafarTypeId = null;
    this.wafarNotes = '';
    this.submitted = false;
    this.loadFailed = false;
    this.loading = false;
    this.applyCascadesFor(null, null);
  }

  // ── Loading ───────────────────────────────────────────────────────────────

  private loadLookups(done?: () => void): void {
    forkJoin({
      finance: this.financeLookups.loadAll(),
      relationships: this.lookupService.getLuFamilyRelationshipsForUi().pipe(catchError(() => of([]))),
      reasons: this.lookupService.getFinReceivedReasonsForUi().pipe(catchError(() => of([]))),
      countriesAll: this.lookupService.getCountriesAll().pipe(catchError(() => of([])))
    }).subscribe(result => {
      this.lookups = result.finance;
      this.relationshipOptions = result.relationships;
      this.reasonOptions = result.reasons;
      this.allCountryOptions = result.countriesAll;

      // Re-apply the cascades so the current selections stay resolvable after a
      // language reload.
      this.applyCascadesFor(this.model.studyCountryInId, this.model.studyFinanceBankeId);
      done?.();
    });
  }

  private loadRecord(id: number): void {
    this.loading = true;
    this.requestedId = id;
    this.studentFinanceService.getById(id).subscribe({
      next: (record) => {
        // A later id wins over a slow answer for an earlier one.
        if (this.requestedId !== id) {
          return;
        }
        this.model = { ...new StudentFinance(), ...record };
        this.normalizeDatesFromApi(this.model);
        this.accountEndDate = this.parseDate(record.studentAccountEndDate);

        this.selectedStudent = { studentId: this.model.id, studentName: this.model.studentFinName };

        // Wafar state lives partly on the finance row and partly on its own table.
        this.loadedWafar = { amount: record.wafarAmount ?? null, typeId: record.wafarTypeId ?? null };
        const finWafarAmount = this.model.wafarAmount ?? 0;
        this.hasWafar = finWafarAmount > 0;
        this.wafarAmount = finWafarAmount > 0 ? finWafarAmount : null;
        this.wafarTypeId = this.model.wafarTypeId ?? null;
        this.wafarNotes = '';

        this.applyCascadesFor(this.model.studyCountryInId, this.model.studyFinanceBankeId);
        this.loadWafarNotes(id);
        this.checkPendingApproval(id);
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => {
        if (this.requestedId !== id) {
          return;
        }
        // A record that does not exist is the app's not-found page, not an
        // empty edit form under an error toast (live QA 23 Sep 2026, L6).
        if (error?.status === 404) {
          this.router.navigate(['/notfound']);
          return;
        }
        console.error('Error loading finance record:', error);
        this.loading = false;
        this.loadFailed = true;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('FINANCE.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  /**
   * ApprovalWorkflowService refuses a second request while one is undecided, so
   * without this the user fills in the whole form only to be rejected on save.
   *
   * The endpoint sits behind the APPROVALS permission. A user who cannot read it
   * simply keeps the old behaviour — the form stays editable and the save-time
   * error explains the rejection — rather than being locked out of editing.
   */
  private checkPendingApproval(studentId: number): void {
    this.approvalService.getPendingForEntity('STUDENT_FINANCE', studentId)
      .pipe(catchError(() => of([] as ApprovalSummary[])))
      .subscribe(requests => {
        if (this.requestedId !== studentId) {
          return;
        }
        this.pendingApproval = requests?.length ? requests[0] : null;
      });
  }

  /** STUDENT_FINANCE_WAFAR holds the notes; STUDENT_FINANCE holds amount + type. */
  private loadWafarNotes(studentId: number): void {
    this.studentFinanceService.getWafar(studentId).subscribe({
      next: (wafar) => {
        if (!wafar || this.requestedId !== studentId) {
          return;
        }
        if (!this.hasWafar) {
          this.hasWafar = true;
          this.wafarAmount = wafar.studentFinRecievedAmount ?? null;
          this.wafarTypeId = wafar.wafarTypeId ?? null;
        }
        this.wafarNotes = wafar.studyNote1 ?? '';
      },
      error: () => { /* 404 simply means the student has no wafar record */ }
    });
  }

  /**
   * Narrows the country/bank-dependent lists to the current selections without
   * clearing them — used on load, unlike the change handlers below.
   */
  private applyCascadesFor(countryId: number | null | undefined, bankId: number | null | undefined): void {
    this.studyGovernments = this.filterByCountry(this.lookups.studyGovernments, countryId);
    this.studyFinanceBanks = this.filterByCountry(this.lookups.studyFinanceBanks, countryId);
    this.studyFinanceBankBranches = this.filterByBank(this.lookups.studyFinanceBankBranches, bankId);
  }

  private filterByCountry<T extends { studyCountryInId?: number }>(list: T[], countryId: number | null | undefined): T[] {
    if (!countryId || countryId <= 0) {
      return [];
    }
    return (list || []).filter(item => Number(item.studyCountryInId) === Number(countryId));
  }

  private filterByBank<T extends { studyFinanceBankeId?: number }>(list: T[], bankId: number | null | undefined): T[] {
    if (!bankId || bankId <= 0) {
      return [];
    }
    return (list || []).filter(item => Number(item.studyFinanceBankeId) === Number(bankId));
  }

  // ── Cascading dropdowns (main form) ───────────────────────────────────────

  onStudyCountryChange(): void {
    const countryId = this.model.studyCountryInId;
    this.model.studyGovInId = 0;
    this.model.studyFinanceBankeId = 0;
    this.model.studyFinanceBankBranchId = 0;
    this.studyGovernments = this.filterByCountry(this.lookups.studyGovernments, countryId);
    this.studyFinanceBanks = this.filterByCountry(this.lookups.studyFinanceBanks, countryId);
    this.studyFinanceBankBranches = [];

    // Fall back to a targeted request when the full list carries no country FK.
    if (countryId && countryId > 0 && !this.studyGovernments.length) {
      this.lookupService.getStudyGovernmentsByCountry(countryId)
        .subscribe({ next: (data) => (this.studyGovernments = data), error: () => { } });
    }
    if (countryId && countryId > 0 && !this.studyFinanceBanks.length) {
      this.lookupService.getStudyFinanceBanksByCountry(countryId)
        .subscribe({ next: (data) => (this.studyFinanceBanks = data), error: () => { } });
    }
  }

  onStudyFinanceBankChange(): void {
    const bankId = this.model.studyFinanceBankeId;
    this.model.studyFinanceBankBranchId = 0;
    this.studyFinanceBankBranches = this.filterByBank(this.lookups.studyFinanceBankBranches, bankId);

    if (bankId && bankId > 0 && !this.studyFinanceBankBranches.length) {
      this.lookupService.getStudyFinanceBankBranchesByBank(bankId)
        .subscribe({ next: (data) => (this.studyFinanceBankBranches = data), error: () => { } });
    }
  }

  // ── Student picker ────────────────────────────────────────────────────────

  /** Searched on the server: only one page of students is ever held. */
  onStudentSearch(event: { query?: string }): void {
    this.studentPicker.search(event?.query ?? '').subscribe(rows => (this.filteredStudents = rows));
  }

  /**
   * STUDENT_FIN_RECIEVED_YEAR_MONTH is the YYYYQ period key — V038 checks it
   * equals YEAR*10 + MONTH and the allocations join on it — so it is derived
   * from the two fields beside it rather than typed. Zero while either is empty.
   */
  static yearMonthOf(year: number | null | undefined, month: number | null | undefined): number {
    const y = Number(year) || 0;
    const m = Number(month) || 0;
    return y > 0 && m > 0 ? y * 10 + m : 0;
  }

  syncYearMonth(): void {
    this.model.studentFinRecievedYearMonth =
      FinanceFormComponent.yearMonthOf(this.model.studentFinRecievedYear, this.model.studentFinRecievedMonth);
  }

  syncRecipientYearMonth(): void {
    this.recipient.finRecievedYearMonth =
      FinanceFormComponent.yearMonthOf(this.recipient.finRecievedYear, this.recipient.finRecievedMonth);
  }

  /**
   * The picker is the only way to set the student: رقم الطالب is read-only and
   * follows it (F3). The picked student's defaults and any reason a new record
   * would be refused come from one finance-side read (F4).
   */
  onStudentSelect(student: StudentOption | null): void {
    if (!student?.studentId) {
      return;
    }
    this.selectedStudent = student;
    this.model.id = student.studentId;
    this.model.studentFinName = student.studentName;
    this.existingRecordId = null;
    if (this.creatingMode) {
      this.pendingApproval = null;
      this.loadNewRecordInfo(student.studentId);
    }

    this.messageService.add({
      severity: 'success',
      summary: this.translationService.translate('FINANCE.MESSAGES.STUDENT_SELECTED'),
      detail: this.translationService
        .translate('FINANCE.MESSAGES.STUDENT_SELECTED_DETAIL')
        .replace('{name}', student.studentName ?? '')
    });
  }

  onStudentClear(): void {
    this.selectedStudent = null;
    this.model.id = 0;
    this.model.studentFinName = '';
    if (this.creatingMode) {
      this.existingRecordId = null;
      this.pendingApproval = null;
    }
  }

  private loadNewRecordInfo(studentId: number): void {
    this.studentFinanceService.newRecordFor(studentId)
      .pipe(catchError(() => of(null)))
      .subscribe(info => {
        // A later pick wins over a slow answer for an earlier one.
        if (!info || Number(this.model.id) !== studentId) {
          return;
        }
        this.applyNewRecordInfo(info);
      });
  }

  /** Fills only what is still empty, so a value the user already typed is kept. */
  applyNewRecordInfo(info: FinanceNewRecordInfo): void {
    this.existingRecordId = info.hasFinanceRecord ? info.studentId : null;
    this.pendingApproval = info.pendingRequestId
      ? {
          id: info.pendingRequestId,
          entityType: 'STUDENT_FINANCE',
          entityId: String(info.studentId),
          requestType: 'INSERT',
          status: 'PENDING',
          requestedAt: info.pendingRequestedAt ?? ''
        }
      : null;

    if (!this.model.studentFinRecievedAmount && (info.studentAmount ?? 0) > 0) {
      this.model.studentFinRecievedAmount = info.studentAmount!;
    }
    // addedQuarter is the YYYYQ key: year and quarter are the two fields beside it.
    const quarter = Number(info.addedQuarter) || 0;
    if (!this.model.studentFinRecievedYear && quarter > 10000) {
      this.model.studentFinRecievedYear = Math.floor(quarter / 10);
      this.model.studentFinRecievedMonth = quarter % 10;
      this.syncYearMonth();
    }
    if (this.isCountryInvalid() && info.studyCountryInId) {
      this.model.studyCountryInId = info.studyCountryInId;
      this.onStudyCountryChange();
    }
  }

  openExistingRecord(): void {
    if (this.existingRecordId) {
      this.router.navigate(['/finance/edit', this.existingRecordId]);
    }
  }

  // ── Validation ────────────────────────────────────────────────────────────

  isStudentSelectionInvalid(): boolean {
    return this.creatingMode
      ? !this.selectedStudent
      : !(this.model.studentFinName || '').trim();
  }

  isFinanceIdMissingForSubmit(): boolean {
    return this.creatingMode && !String(this.model.studentFinId ?? '').trim();
  }

  /** A payment of nothing is not a payment: the amount must be above 0 (F3). */
  isAmountInvalid(): boolean {
    const amount = Number(this.model.studentFinRecievedAmount);
    return this.model.studentFinRecievedAmount == null || Number.isNaN(amount) || amount <= 0;
  }

  /** The wafar is taken out of the amount, so it cannot be negative or exceed it. */
  isWafarAmountInvalid(): boolean {
    if (!this.hasWafar) {
      return false;
    }
    // Ticked with no amount was saved as a wafar of 0 — a silent "none".
    if (this.wafarAmount == null) {
      return true;
    }
    const wafar = Number(this.wafarAmount);
    return wafar < 0 || wafar > Number(this.model.studentFinRecievedAmount ?? 0);
  }

  /**
   * The check date is required only when money moved: the selected received
   * status resolves, in STUDY_FIN_RECEIVED_STATUS, to RECEIVED, DISBURSED or
   * PARTIALLY_DISBURSED. NOT_SPECIFIED (99, which the legacy 0 rows are
   * back-filled to) is valid without a date, and so is any status the lookup
   * does not know — the backend applies the same rule (live QA 23 Sep 2026, H2).
   */
  checkDateRequired(): boolean {
    const status = this.model.studyFinRecievedStatus;
    if (status === null || status === undefined) {
      return false;
    }
    const row = (this.lookups.finReceivedStatuses || []).find(s => Number(s.id) === Number(status));
    const code = (row?.code ?? '').toUpperCase();
    return FinanceFormComponent.DATED_STATUS_CODES.includes(code);
  }

  isCheckDateInvalid(): boolean {
    if (!this.checkDateRequired()) {
      return false;
    }
    const date = this.model.studyCheckDate;
    return !date || Number.isNaN(new Date(date as Date).getTime());
  }

  /** Option id 0 means unset; the backend expects a real status. */
  isFinReceivedStatusInvalid(): boolean {
    const status = this.model.studyFinRecievedStatus;
    return status === null || status === undefined || Number(status) <= 0;
  }

  isCountryInvalid(): boolean {
    return !this.model.studyCountryInId || Number(this.model.studyCountryInId) <= 0;
  }

  /**
   * Gated on hasWafar because unticking the checkbox does not clear wafarAmount — the
   * field is just hidden, and save() zeroes it only on submit. This field is now visible
   * with the checkbox off, so without the guard it would keep subtracting a wafar the
   * record is no longer going to have. Mirrors what save() writes.
   */
  get netAmount(): number {
    const wafar = this.hasWafar ? (this.wafarAmount ?? 0) : 0;
    return (this.model.studentFinRecievedAmount ?? 0) - wafar;
  }

  get currencyName(): string {
    return this.financeLookups.resolveCurrencyName(this.lookups.currencies, this.model.studyCurrencyInId);
  }

  // ── Save ──────────────────────────────────────────────────────────────────

  save(): void {
    if (this.creatingMode && this.existingRecordId) {
      this.messageService.add({
        severity: 'warn',
        summary: this.translationService.translate('FINANCE.RECORD_EXISTS_TITLE'),
        detail: this.translationService.translate('FINANCE.RECORD_EXISTS_MESSAGE'),
        life: 8000
      });
      return;
    }
    if (this.pendingApproval) {
      this.messageService.add({
        severity: 'warn',
        summary: this.translationService.translate('FINANCE.PENDING_APPROVAL_TITLE'),
        detail: this.translationService.translate('FINANCE.PENDING_APPROVAL_MESSAGE'),
        life: 8000
      });
      return;
    }

    this.submitted = true;

    const idOk = this.model.id != null && Number(this.model.id) > 0;
    const invalid = !idOk
      || this.isStudentSelectionInvalid()
      || this.isAmountInvalid()
      || this.isWafarAmountInvalid()
      || this.isCheckDateInvalid()
      || this.isFinReceivedStatusInvalid()
      || this.isCountryInvalid()
      || (this.creatingMode && this.isFinanceIdMissingForSubmit());

    if (invalid) {
      this.messageService.add({
        severity: 'error',
        summary: this.translationService.translate('COMMON.VALIDATION_ERROR'),
        detail: this.translationService.translate(
          this.creatingMode ? 'FINANCE.VALIDATION_REQUIRED_FIELDS' : 'FINANCE.VALIDATION_REQUIRED_FIELDS_EDIT'
        )
      });
      return;
    }

    // Finance id defaults to the student id when left blank on edit.
    if (!String(this.model.studentFinId ?? '').trim()) {
      this.model.studentFinId = String(this.model.id);
    }
    this.model.studentAccountEndDate = this.formatDate(this.accountEndDate);

    /*
     * Wafar travels inside the approval payload and is written to
     * STUDENT_FINANCE_WAFAR by the executor once the request is approved. Nothing
     * here writes the wafar row directly — a rejected request must leave no wafar
     * behind, and on a new record there is no finance row to attach one to yet.
     */
    const wafar = this.wafarForSave();
    this.model.wafarAmount = wafar.amount as number;
    this.model.wafarTypeId = wafar.typeId as number;
    this.model.wafarNote = wafar.note;

    this.saving = true;
    const request$ = this.creatingMode
      ? this.studentFinanceService.Create(this.model)
      : this.studentFinanceService.Update(this.model);

    request$.subscribe({
      next: (response: any) => {
        this.saving = false;

        const successKey = this.creatingMode
          ? 'FINANCE.MESSAGES.CREATE_SUCCESS'
          : 'FINANCE.MESSAGES.UPDATE_SUCCESS';
        const detail = (response?.status === 'PENDING' && response?.message)
          ? response.message
          : this.translationService.translate(successKey);

        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail
        });
        // Let the toast register before leaving the page.
        setTimeout(() => this.goBack(), 900);
      },
      error: (error) => {
        this.saving = false;
        console.error('Error saving student finance:', error);
        const failKey = this.creatingMode
          ? 'FINANCE.MESSAGES.CREATE_FAILED'
          : 'FINANCE.MESSAGES.UPDATE_FAILED';
        const body = error?.error ?? {};
        // The two refusals a user can act on are said in their language (F4).
        const known = body.code === 'PENDING_EXISTS' ? 'FINANCE.PENDING_EXISTS_FOR_STUDENT'
          : body.code === 'FINANCE_EXISTS' ? 'FINANCE.RECORD_EXISTS_MESSAGE'
          : null;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: known
            ? this.translationService.translate(known)
            : extractApiErrorMessage(error) || this.translationService.translate(failKey),
          life: 10000
        });
        // A request may have been raised elsewhere since this form loaded;
        // show the banner instead of inviting another attempt.
        const studentId = Number(this.model.id);
        if (body.code === 'PENDING_EXISTS' && body.requestId) {
          this.pendingApproval = {
            id: body.requestId, entityType: 'STUDENT_FINANCE', entityId: String(studentId),
            requestType: this.creatingMode ? 'INSERT' : 'UPDATE', status: 'PENDING', requestedAt: ''
          };
        } else if (body.code === 'FINANCE_EXISTS') {
          this.existingRecordId = studentId;
        } else if (!this.creatingMode && studentId > 0) {
          this.checkPendingApproval(studentId);
        }
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/finance']);
  }

  // ── Recipient dialog ──────────────────────────────────────────────────────

  /**
   * Recipient status id 1 means the student collects in person, so no delegate
   * record is needed. Any other status opens the recipient form.
   */
  onReceivedWhomStatusChange(statusId: number): void {
    if (statusId && Number(statusId) !== 1) {
      this.openRecipientDialog('create');
    }
  }

  openNewRecipient(): void {
    this.openRecipientDialog('create');
  }

  openSelectedRecipient(): void {
    const whomId = this.model.studyReceivedWhomId;
    if (!whomId) {
      return;
    }
    this.recipientService.getById(whomId).subscribe({
      next: (record) => this.openRecipientDialog('edit', record),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.translationService.translate('COMMON.ERROR'),
        detail: this.translationService.translate('FINANCE.MESSAGES.LOAD_RECIPIENT_ERROR')
      })
    });
  }

  private openRecipientDialog(mode: 'create' | 'edit', record?: StudyFinReceivedWhom): void {
    this.recipientDialogMode = mode;
    this.recipientSubmitted = false;
    this.savingRecipient = false;
    this.recipient = record
      ? { ...new StudyFinReceivedWhom(), ...record }
      : new StudyFinReceivedWhom();

    this.recipientPassportIssueDate = this.parseDate(this.recipient.finReceivedPassportIssueDate);
    this.recipientPassportExpireDate = this.parseDate(this.recipient.finReceivedPassportExpireDate);
    this.recipientCheckDate = this.parseDate(this.recipient.checkDate);
    this.recipientManualFormDate = this.parseDate(this.recipient.finManualFormDate);

    this.applyRecipientCascades();
    this.recipientDialogVisible = true;
  }

  private applyRecipientCascades(): void {
    this.recipientCascades = {
      governments: this.filterByCountry(this.lookups.studyGovernments, this.recipient.financeCountryId),
      banks: this.filterByCountry(this.lookups.studyFinanceBanks, this.recipient.financeCountryId),
      branches: this.filterByBank(this.lookups.studyFinanceBankBranches, this.recipient.financeBankeId)
    };
  }

  onRecipientCountryChange(): void {
    this.recipient.financeGovId = 0;
    this.recipient.financeBankeId = 0;
    this.recipient.financeBankBranchId = 0;
    this.applyRecipientCascades();

    const countryId = this.recipient.financeCountryId;
    if (countryId && countryId > 0 && !this.recipientCascades.governments.length) {
      this.lookupService.getStudyGovernmentsByCountry(countryId)
        .subscribe({ next: (data) => (this.recipientCascades.governments = data), error: () => { } });
    }
    if (countryId && countryId > 0 && !this.recipientCascades.banks.length) {
      this.lookupService.getStudyFinanceBanksByCountry(countryId)
        .subscribe({ next: (data) => (this.recipientCascades.banks = data), error: () => { } });
    }
  }

  onRecipientBankChange(): void {
    this.recipient.financeBankBranchId = 0;
    this.recipientCascades.branches = this.filterByBank(
      this.lookups.studyFinanceBankBranches, this.recipient.financeBankeId
    );

    const bankId = this.recipient.financeBankeId;
    if (bankId && bankId > 0 && !this.recipientCascades.branches.length) {
      this.lookupService.getStudyFinanceBankBranchesByBank(bankId)
        .subscribe({ next: (data) => (this.recipientCascades.branches = data), error: () => { } });
    }
  }

  isRecipientNameInvalid(): boolean {
    return !this.recipient.finReceivedName?.trim();
  }

  saveRecipient(): void {
    this.recipientSubmitted = true;
    if (this.isRecipientNameInvalid()) {
      this.messageService.add({
        severity: 'error',
        summary: this.translationService.translate('COMMON.ERROR'),
        detail: this.translationService.translate('FINANCE.MESSAGES.NAME_REQUIRED')
      });
      return;
    }

    this.recipient.finReceivedPassportIssueDate = this.formatDate(this.recipientPassportIssueDate);
    this.recipient.finReceivedPassportExpireDate = this.formatDate(this.recipientPassportExpireDate);
    this.recipient.checkDate = this.formatDate(this.recipientCheckDate);
    this.recipient.finManualFormDate = this.formatDate(this.recipientManualFormDate);

    this.savingRecipient = true;
    const isEdit = this.recipientDialogMode === 'edit' && this.recipient.id > 0;
    const request$ = isEdit
      ? this.recipientService.update(this.recipient.id, this.recipient)
      : this.recipientService.create(this.recipient);

    request$.subscribe({
      next: (saved: any) => {
        this.savingRecipient = false;
        this.recipientDialogVisible = false;
        /*
         * The recipients screen went behind the approval workflow, so a new
         * recipient has no id until an approver signs it off — there is nothing
         * to select here yet. Said plainly rather than selecting `undefined`,
         * which would have silently cleared the field the dialog was opened to
         * fill. An edit keeps its id and needs no re-selection either way.
         */
        const pending = saved?.status === 'PENDING';
        if (!isEdit && !pending && saved?.id) {
          this.model.studyReceivedWhomId = saved.id;
        }
        this.financeLookups.reloadReceivedWhoms()
          .subscribe(list => (this.lookups.finReceivedWhoms = list));
        this.messageService.add({
          severity: pending ? 'info' : 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: pending
            ? this.translationService.translate('FINANCE.MESSAGES.RECIPIENT_PENDING')
            : this.translationService.translate('FINANCE.MESSAGES.RECIPIENT_SAVED'),
          life: pending ? 8000 : undefined
        });
      },
      error: () => {
        this.savingRecipient = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('FINANCE.MESSAGES.SAVE_RECIPIENT_ERROR')
        });
      }
    });
  }

  /**
   * What save() writes for the wafar. Ticked: what the form shows. Unticked on a
   * record that had a wafar: 0, which the approval executor reads as "remove it".
   * Unticked on a record that had none: what it had, so nothing reads as changed.
   */
  private wafarForSave(): { amount: number | null; typeId: number | null; note: string } {
    if (this.hasWafar) {
      return { amount: this.wafarAmount, typeId: this.wafarTypeId, note: this.wafarNotes ?? '' };
    }
    const loaded = this.loadedWafar;
    if (!loaded || (loaded.amount ?? 0) > 0) {
      return { amount: 0, typeId: 0, note: '' };
    }
    return { amount: loaded.amount, typeId: loaded.typeId, note: '' };
  }

  // ── Date helpers ──────────────────────────────────────────────────────────

  /** The API returns ISO strings; p-calendar needs a Date. */
  private normalizeDatesFromApi(row: StudentFinance): void {
    // An empty check date stays empty, for the user to fill in: filling it with
    // today on load changed a field nobody touched (QA B12, N10).
    row.studyCheckDate = this.parseDate(row.studyCheckDate) as Date;
    const midate = this.parseDate(row.studyFinanceMidate);
    if (midate) {
      row.studyFinanceMidate = midate;
    }
    const finId = row.studentFinId;
    if (finId !== null && finId !== undefined && String(finId).trim()) {
      row.studentFinId = String(finId).trim();
    }
  }

  private parseDate(value: unknown): Date | null {
    if (value instanceof Date) {
      return Number.isNaN(value.getTime()) ? null : value;
    }
    if (typeof value === 'string' && value.trim()) {
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    return null;
  }

  /** These columns are NVARCHAR in the schema, so dates go back as YYYY-MM-DD. */
  private formatDate(value: Date | null): string {
    if (!value || Number.isNaN(value.getTime())) {
      return '';
    }
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
