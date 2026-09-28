import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentLoan } from '../../api/student-loan';
import { StudentLoanService } from '../../service/student-loan.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { MoheQuarterService, MoheQuarter } from '../../service/mohe-quarter.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService, PrimeSeverity } from '../../service/code-list.service';
import { StudentPickerService, StudentOption } from '../../service/student-picker.service';
import { academicYearSpans } from '../../service/academic-year-spans';
import { ApprovalService, ApprovalSummary } from '../../service/approval.service';
import { changesOnly } from '../../service/reload-streams.util';
import { resetPaging } from '../../service/table-paging.util';
import { localizeField } from '../../pipes/in-language.pipe';

@Component({
    selector: 'app-student-loan',
    templateUrl: './student-loan.component.html',
    styleUrls: ['./student-loan.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentLoanComponent implements OnInit {

  records: StudentLoan[] = [];
  filteredRecords: StudentLoan[] = [];
  loading = false;

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';
  saving = false;

  form: FormGroup;
  selectedRecord: StudentLoan | null = null;

  /** An undecided approval request for the record being edited; blocks a second one. */
  pendingApproval: ApprovalSummary | null = null;

  currencies: LookupItem[] = [];
  quarters: MoheQuarter[] = [];
  students: StudentOption[] = [];
  // Precomputed once — never recomputed inside getters to avoid change-detection loops
  studentsForDropdown: { id: number; label: string }[] = [];
  /** Typing in the picker's filter box; searched on the server, debounced. */
  private readonly studentSearch$ = new Subject<string>();

  loanStatuses: { id: number; name: string; nameEn?: string }[] = [];
  /** Academic years as the spans this column stores — see academic-year-spans.ts. */
  studyYearOptions: { label: string; value: string }[] = [];

  filters = {
    search: '',
    status: null as number | null
  };

  stats = {
    totalRecords: 0,
    totalAmount: 0,
    averageAmount: 0
  };

  tableRows = 10;
  rowsPerPageOptions = [10, 20, 50];
  /** Bound to the table, so a filter that shrinks the list starts it on page one (QA B8, N9). */
  first = 0;

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private service: StudentLoanService,
    private lookupService: LookupService,
    private quarterService: MoheQuarterService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public translationService: TranslationService,
    private codeLists: CodeListService,
    private studentPicker: StudentPickerService,
    private approvalService: ApprovalService
  ) {
    this.form = this.fb.group({
      loanId: [{ value: null, disabled: true }],
      studentId: [null, Validators.required],
      studyYear: [null],
      moheQuarterId: [null],
      // CK_STUDENT_LOAN_AMOUNT_POSITIVE refuses zero, and this used to default to
      // zero with min(0) — the dialog's own default was a value the database
      // would not take.
      loanAmount: [null, [Validators.required, Validators.min(1)]],
      currencyId: [null, Validators.required],
      loanStatus: [0],
      repaymentNotes: [null]
    });
  }

  ngOnInit(): void {
    this.loadCodeLists();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadCodeLists());
    this.loadLookupData();
    this.loadRecords();
    this.loadStudents();
    this.studentSearch$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(term => this.studentPicker.search(term)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(rows => this.setStudents(
      this.studentPicker.merge(this.students, rows, this.studentsInUse())
    ));
  }

  /**
   * The loan statuses and the academic years.
   *
   * <p>Both are in the bulk code-list payload the route guard already warms —
   * `loan-status` and `academic-year` — so neither costs a request, and the
   * status carries the colour the database gives it.
   */
  private loadCodeLists(): void {
    this.codeLists.whenLoaded().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const lang = this.translationService.getCurrentLanguage();
      this.loanStatuses = this.codeLists.active('loan-status')
        .map(row => ({
          id: Number(row.value),
          name: this.codeLists.label('loan-status', row.value, lang)
        }))
        .filter(o => !isNaN(o.id));
      this.studyYearOptions = academicYearSpans(this.codeLists.active('academic-year'));
    });
  }

  loadRecords(): void {
    this.loading = true;
    this.service.getAll().subscribe({
      next: (records) => {
        this.records = records ?? [];
        this.applyFilters();
        this.loading = false;
        // The table names a student per row, and the picker holds one page — so
        // the names on screen are fetched by id rather than hoped for.
        this.loadStudentsOfRecords();
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_LOAN.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  loadLookupData(): void {
    forkJoin({
      currencies: this.lookupService.getStudyCurrencies().pipe(catchError(() => of([]))),
      quarters: this.quarterService.getAll().pipe(catchError(() => of([])))
    }).subscribe({
      next: (res) => {
        this.currencies = res.currencies ?? [];
        // The quarter's name in the reader's language (QA B3).
        this.quarters = localizeField(res.quarters ?? [], 'quarterName', this.translationService.getCurrentLanguage());
      }
    });
  }

  /**
   * The student picker's first page.
   *
   * <p>It used to ask the student list, which is gated on `STUDENT_PII` — a
   * permission none of the finance roles that run this screen hold, so the
   * dropdown answered 403 and stayed empty and the table printed bare ids.
   * `/studentdata/options` answers with an id and a name and is open to finance.
   *
   * <p>Added to what is held rather than replacing it: the names already
   * resolved for the rows on screen must survive the first page arriving. Only a
   * search replaces the list, and even then it keeps the students still in use.
   */
  loadStudents(): void {
    this.studentPicker.search().subscribe(rows => this.setStudents(
      this.studentPicker.mergeAll(this.students, rows)
    ));
  }

  /** The students named by the open form, the open record, or a row on screen. */
  private studentsInUse(): number[] {
    return [
      this.form.get('studentId')?.value,
      this.selectedRecord?.studentId,
      ...this.records.map(r => r.studentId)
    ].filter((id): id is number => !!id && id > 0);
  }

  /** Searches the picker on the server; only one page is ever held locally. */
  onStudentFilter(event: { filter: string }): void {
    this.studentSearch$.next(event?.filter ?? '');
  }

  private setStudents(students: StudentOption[]): void {
    this.students = students;
    this.studentsForDropdown = this.studentPicker.toOptions(students);
  }

  /** Resolves the names the loaded rows refer to; the picker holds one page. */
  private loadStudentsOfRecords(): void {
    const missing = this.records
      .map(r => r.studentId)
      .filter(id => !!id && !this.students.some(s => s.studentId === id));
    if (missing.length === 0) return;
    this.studentPicker.byIds(missing).subscribe(rows =>
      this.setStudents(this.studentPicker.mergeAll(this.students, rows)));
  }

  /**
   * The workflow refuses a second request while one is undecided, so the form is
   * locked rather than letting the user fill it in and be rejected on save.
   *
   * <p>The endpoint sits behind the APPROVALS permission; a user who cannot read
   * it keeps the old behaviour and finds out at save time instead of being
   * locked out of their own screen.
   */
  private checkPendingApproval(recordId: number | null | undefined): void {
    this.pendingApproval = null;
    if (!recordId || recordId <= 0) return;
    this.approvalService.getPendingForEntity('STUDENT_LOAN', recordId)
      .pipe(catchError(() => of([] as ApprovalSummary[])))
      .subscribe(requests => { this.pendingApproval = requests?.length ? requests[0] : null; });
  }

  openCreateDialog(): void {
    this.dialogMode = 'create';
    this.selectedRecord = null;
    this.pendingApproval = null;
    this.form.reset({ loanId: null, studentId: null, studyYear: null, moheQuarterId: null, loanAmount: null, currencyId: null, loanStatus: 0, repaymentNotes: null });
    this.formDialogVisible = true;
  }

  openEditDialog(record: StudentLoan): void {
    this.dialogMode = 'edit';
    this.selectedRecord = record;
    this.checkPendingApproval(record.loanId);
    this.form.reset({
      loanId: record.loanId ?? null,
      studentId: record.studentId ?? null,
      studyYear: record.studyYear ?? null,
      moheQuarterId: record.moheQuarterId ?? null,
      loanAmount: record.loanAmount ?? null,
      currencyId: record.currencyId ?? null,
      loanStatus: record.loanStatus ?? 0,
      repaymentNotes: record.repaymentNotes ?? null
    });
    this.formDialogVisible = true;
  }

  openViewDialog(record: StudentLoan): void {
    this.selectedRecord = record;
    this.viewDialogVisible = true;
  }

  hideFormDialog(): void {
    this.formDialogVisible = false;
    this.form.reset();
  }

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

    const raw = this.form.getRawValue();
    const payload: StudentLoan = {
      studentId: raw.studentId ?? 0,
      studyYear: raw.studyYear ?? null,
      moheQuarterId: raw.moheQuarterId ?? null,
      loanAmount: raw.loanAmount ?? 0,
      currencyId: raw.currencyId ?? 0,
      loanStatus: raw.loanStatus ?? 0,
      repaymentNotes: raw.repaymentNotes ?? null
      // Neither loanId nor midate is sent. LOAN_ID is an identity column, and a
      // non-null id sent Spring Data down merge() — every Add answered 409.
      // MIDATE is NOT NULL and stamped by the server; sending null was the fault
      // waiting behind it.
    };

    this.saving = true;
    const request$ = this.dialogMode === 'create'
      ? this.service.create(payload)
      : this.service.update(raw.loanId!, { ...payload, loanId: raw.loanId });

    request$.subscribe({
      next: (response: any) => {
        const key = this.dialogMode === 'create' ? 'STUDENT_LOAN.MESSAGES.CREATE_SUCCESS' : 'STUDENT_LOAN.MESSAGES.UPDATE_SUCCESS';
        // Writes go through the approval workflow, so the backend answers with
        // the "submitted for approval" message rather than a saved record —
        // nothing has changed on the table yet.
        const detail = (response?.status === 'PENDING' && response?.message)
          ? response.message
          : this.translationService.translate(key);
        this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail });
        this.saving = false;
        this.hideFormDialog();
        this.loadRecords();
      },
      error: () => {
        this.saving = false;
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_LOAN.MESSAGES.SAVE_ERROR') });
      }
    });
  }

  confirmDelete(record: StudentLoan): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('STUDENT_LOAN.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('STUDENT_LOAN.DELETE_CONFIRM_MESSAGE').replace('{name}', this.getStudentName(record.studentId)),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteRecord(record)
    });
  }

  private deleteRecord(record: StudentLoan): void {
    if (!record.loanId) return;
    this.service.delete(record.loanId).subscribe({
      next: (response: any) => {
        // Deletion is queued for approval; the row stays until it is approved.
        const detail = (response?.status === 'PENDING' && response?.message)
          ? response.message
          : this.translationService.translate('STUDENT_LOAN.MESSAGES.DELETE_SUCCESS');
        this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail });
        this.loadRecords();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_LOAN.MESSAGES.DELETE_ERROR') });
      }
    });
  }

  onFiltersChanged(): void { resetPaging(this); this.applyFilters(); }

  clearFilters(): void {
    this.filters = { search: '', status: null };
    resetPaging(this);
    this.applyFilters();
  }

  private applyFilters(): void {
    const search = this.filters.search.trim().toLowerCase();
    const status = this.filters.status;

    this.filteredRecords = (this.records ?? []).filter(r => {
      const haystack = [
        r.loanId?.toString(),
        r.studentId?.toString(),
        this.getStudentName(r.studentId),
        r.studyYear,
        r.loanAmount?.toString(),
        this.getStatusName(r.loanStatus),
        this.getCurrencyName(r.currencyId)
      ].filter(Boolean).join(' ').toLowerCase();

      const matchSearch = !search || haystack.includes(search);
      const matchStatus = status === null || r.loanStatus === status;
      return matchSearch && matchStatus;
    });

    this.stats = {
      totalRecords: this.filteredRecords.length,
      totalAmount: this.filteredRecords.reduce((s, r) => s + (r.loanAmount ?? 0), 0),
      averageAmount: this.filteredRecords.length
        ? this.filteredRecords.reduce((s, r) => s + (r.loanAmount ?? 0), 0) / this.filteredRecords.length
        : 0
    };
  }

  get hasActiveFilters(): boolean {
    return !!(this.filters.search || this.filters.status !== null);
  }

  get tableReportTemplate(): string {
    return this.translationService.translate('STUDENT_LOAN.TABLE.REPORT_TEMPLATE');
  }

  get tableCaptionLabel(): string {
    return this.translationService.translate('STUDENT_LOAN.TABLE.CAPTION').replace('{count}', String(this.filteredRecords.length));
  }

  get dialogTitle(): string {
    return this.translationService.translate(
      this.dialogMode === 'create' ? 'STUDENT_LOAN.FORM.TITLE_CREATE' : 'STUDENT_LOAN.FORM.TITLE_EDIT'
    );
  }

  getStudentName(id: number | null | undefined): string {
    if (!id) return this.translationService.translate('COMMON.NOT_AVAILABLE');
    return this.students.find(s => s.studentId === id)?.studentName ?? id.toString();
  }

  /** True while the amount is present but not above zero — the database refuses it. */
  get amountIsNotPositive(): boolean {
    const control = this.form.get('loanAmount');
    return !!control && control.touched && control.value !== null && control.hasError('min');
  }

  getCurrencyName(id: number | null | undefined): string {
    if (!id) return this.translationService.translate('COMMON.NOT_AVAILABLE');
    const c = this.currencies.find(c => c.id === id);
    return c ? (c.name || c.label || id.toString()) : id.toString();
  }

  getQuarterName(id: number | null | undefined): string {
    if (!id) return this.translationService.translate('COMMON.NOT_AVAILABLE');
    const q = this.quarters.find(q => q.id === id || q.id1 === id);
    if (!q) return id.toString();
    return q.quarterName || (q.quarterYear ? `${q.quarterYear} Q${q.quarterNumber}` : id.toString());
  }

  getStatusName(status: number | null | undefined): string {
    const s = this.loanStatuses.find(x => x.id === status);
    return s ? s.name : (status?.toString() ?? this.translationService.translate('COMMON.NOT_AVAILABLE'));
  }

  /**
   * The colour the database gives the status.
   *
   * <p>This was a `switch` on the literals 0 to 3 while
   * `LU_LOAN_STATUS.UI_SEVERITY` held the answer — and two of its four cases
   * disagreed with the table: it painted Pending `'warning'`, which is not
   * PrimeNG 21's word for it and so rendered as nothing, and Approved `info`
   * where the table says `success`.
   */
  getStatusSeverity(status: number | null | undefined): PrimeSeverity {
    return this.codeLists.severity('loan-status', status);
  }
}
