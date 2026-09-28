import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentBooksAllowance } from '../../api/student-books-allowance';
import { StudentBooksAllowanceService } from '../../service/student-books-allowance.service';
import { MoheQuarterService, MoheQuarter } from '../../service/mohe-quarter.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { StudentPickerService, StudentOption } from '../../service/student-picker.service';
import { academicYearSpans } from '../../service/academic-year-spans';
import { ApprovalService, ApprovalSummary } from '../../service/approval.service';
import { changesOnly } from '../../service/reload-streams.util';
import { resetPaging } from '../../service/table-paging.util';
import { localizeField } from '../../pipes/in-language.pipe';

@Component({
    selector: 'app-student-books-allowance',
    templateUrl: './student-books-allowance.component.html',
    styleUrls: ['./student-books-allowance.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentBooksAllowanceComponent implements OnInit {

  records: StudentBooksAllowance[] = [];
  filteredRecords: StudentBooksAllowance[] = [];
  loading = false;

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';
  saving = false;

  form: FormGroup;
  selectedRecord: StudentBooksAllowance | null = null;

  /** An undecided approval request for the record being edited; blocks a second one. */
  pendingApproval: ApprovalSummary | null = null;

  quarters: MoheQuarter[] = [];
  students: StudentOption[] = [];
  studentsForDropdown: { id: number; label: string }[] = [];
  /** Typing in the picker's filter box; searched on the server, debounced. */
  private readonly studentSearch$ = new Subject<string>();

  payStatuses: { id: number; name: string }[] = [];
  /** Academic years as the spans this column stores - see academic-year-spans.ts. */
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
    private service: StudentBooksAllowanceService,
    private quarterService: MoheQuarterService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public translationService: TranslationService,
    private codeLists: CodeListService,
    private studentPicker: StudentPickerService,
    private approvalService: ApprovalService
  ) {
    this.form = this.fb.group({
      booksAllowId: [{ value: null, disabled: true }],
      studentId: [null, Validators.required],
      studyYear: [null],
      moheQuarterId: [null],
      allowanceAmount: [0, [Validators.required, Validators.min(0)]],
      payStatus: [0],
      receiptDate: [null],
      notes: [null]
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
   * The pay statuses and the academic years.
   *
   * <p>Both are in the bulk code-list payload the route guard already warms -
   * `books-allowance-pay-status` and `academic-year` - so neither costs a
   * request, and the status carries the colour the database gives it.
   */
  private loadCodeLists(): void {
    this.codeLists.whenLoaded().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const lang = this.translationService.getCurrentLanguage();
      this.payStatuses = this.codeLists.active('books-allowance-pay-status')
        .map(row => ({
          id: Number(row.value),
          name: this.codeLists.label('books-allowance-pay-status', row.value, lang)
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
        // The table names a student per row, and the picker holds one page - so
        // the names on screen are fetched by id rather than hoped for.
        this.loadStudentsOfRecords();
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  loadLookupData(): void {
    this.quarterService.getAll().pipe(catchError(() => of([]))).subscribe({
      // The quarter's name in the reader's language (QA B3).
      next: (quarters) => { this.quarters = localizeField(quarters ?? [], 'quarterName', this.translationService.getCurrentLanguage()); }
    });
  }

  /**
   * The student picker's first page.
   *
   * <p>It used to ask the student list, which is gated on `STUDENT_PII` - a
   * permission none of the three finance roles that run this screen hold, so the
   * dropdown answered 403 and stayed empty. `/studentdata/options` answers with
   * an id and a name and is open to finance.
   */
  loadStudents(): void {
    // Added to what is held rather than replacing it: the names already resolved
    // for the rows on screen must survive the first page arriving. Only a search
    // replaces the list, and even then it keeps the students still in use.
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
    this.approvalService.getPendingForEntity('STUDENT_BOOKS_ALLOWANCE', recordId)
      .pipe(catchError(() => of([] as ApprovalSummary[])))
      .subscribe(requests => { this.pendingApproval = requests?.length ? requests[0] : null; });
  }

  openCreateDialog(): void {
    this.dialogMode = 'create';
    this.selectedRecord = null;
    this.pendingApproval = null;
    this.form.reset({ booksAllowId: null, studentId: null, studyYear: null, moheQuarterId: null, allowanceAmount: 0, payStatus: 0, receiptDate: null, notes: null });
    this.formDialogVisible = true;
  }

  openEditDialog(record: StudentBooksAllowance): void {
    this.dialogMode = 'edit';
    this.selectedRecord = record;
    this.checkPendingApproval(record.booksAllowId);
    this.form.reset({
      booksAllowId: record.booksAllowId ?? null,
      studentId: record.studentId ?? null,
      studyYear: record.studyYear ?? null,
      moheQuarterId: record.moheQuarterId ?? null,
      allowanceAmount: record.allowanceAmount ?? 0,
      payStatus: record.payStatus ?? 0,
      receiptDate: this.toDate(record.receiptDate),
      notes: record.notes ?? null
    });
    this.formDialogVisible = true;
  }

  openViewDialog(record: StudentBooksAllowance): void {
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
    const payload: StudentBooksAllowance = {
      booksAllowId: raw.booksAllowId ?? 0,
      studentId: raw.studentId ?? 0,
      studyYear: raw.studyYear ?? null,
      moheQuarterId: raw.moheQuarterId ?? null,
      allowanceAmount: raw.allowanceAmount ?? 0,
      payStatus: raw.payStatus ?? 0,
      receiptDate: raw.receiptDate ? new Date(raw.receiptDate).toISOString() : null,
      notes: raw.notes ?? null
      // midate is not sent. It is NOT NULL in the database and stamped by the
      // server; sending null was what made every save fail.
    };

    this.saving = true;
    const request$ = this.dialogMode === 'create'
      ? this.service.create(payload)
      : this.service.update(payload.booksAllowId!, payload);

    request$.subscribe({
      next: (response: any) => {
        const key = this.dialogMode === 'create' ? 'STUDENT_BOOKS_ALLOWANCE.MESSAGES.CREATE_SUCCESS' : 'STUDENT_BOOKS_ALLOWANCE.MESSAGES.UPDATE_SUCCESS';
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
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.MESSAGES.SAVE_ERROR') });
      }
    });
  }

  confirmDelete(record: StudentBooksAllowance): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.DELETE_CONFIRM_MESSAGE').replace('{name}', this.getStudentName(record.studentId)),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteRecord(record)
    });
  }

  private deleteRecord(record: StudentBooksAllowance): void {
    if (!record.booksAllowId) return;
    this.service.delete(record.booksAllowId).subscribe({
      next: (response: any) => {
        // Deletion is queued for approval; the row stays until it is approved.
        const detail = (response?.status === 'PENDING' && response?.message)
          ? response.message
          : this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.MESSAGES.DELETE_SUCCESS');
        this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail });
        this.loadRecords();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.MESSAGES.DELETE_ERROR') });
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
        r.booksAllowId?.toString(),
        r.studentId?.toString(),
        this.getStudentName(r.studentId),
        r.studyYear,
        r.allowanceAmount?.toString(),
        this.getStatusName(r.payStatus)
      ].filter(Boolean).join(' ').toLowerCase();

      const matchSearch = !search || haystack.includes(search);
      const matchStatus = status === null || r.payStatus === status;
      return matchSearch && matchStatus;
    });

    this.stats = {
      totalRecords: this.filteredRecords.length,
      totalAmount: this.filteredRecords.reduce((s, r) => s + (r.allowanceAmount ?? 0), 0),
      averageAmount: this.filteredRecords.length
        ? this.filteredRecords.reduce((s, r) => s + (r.allowanceAmount ?? 0), 0) / this.filteredRecords.length
        : 0
    };
  }

  get hasActiveFilters(): boolean {
    return !!(this.filters.search || this.filters.status !== null);
  }

  get tableReportTemplate(): string {
    return this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.TABLE.REPORT_TEMPLATE');
  }

  get tableCaptionLabel(): string {
    return this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.TABLE.CAPTION').replace('{count}', String(this.filteredRecords.length));
  }

  get dialogTitle(): string {
    return this.translationService.translate(
      this.dialogMode === 'create' ? 'STUDENT_BOOKS_ALLOWANCE.FORM.TITLE_CREATE' : 'STUDENT_BOOKS_ALLOWANCE.FORM.TITLE_EDIT'
    );
  }

  getStudentName(id: number | null | undefined): string {
    if (!id) return this.translationService.translate('COMMON.NOT_AVAILABLE');
    return this.students.find(s => s.studentId === id)?.studentName ?? id.toString();
  }

  getQuarterName(id: number | null | undefined): string {
    if (!id) return this.translationService.translate('COMMON.NOT_AVAILABLE');
    const q = this.quarters.find(q => q.id === id || q.id1 === id);
    if (!q) return id.toString();
    return q.quarterName || (q.quarterYear ? `${q.quarterYear} Q${q.quarterNumber}` : id.toString());
  }

  getStatusName(status: number | null | undefined): string {
    if (status === null || status === undefined) return this.translationService.translate('COMMON.NOT_AVAILABLE');
    return this.codeLists.label('books-allowance-pay-status', status, this.translationService.getCurrentLanguage())
      || String(status);
  }

  /**
   * The colour LU_BOOKS_ALLOWANCE_PAY_STATUS.UI_SEVERITY gives the status.
   *
   * <p>It used to be a `switch` on the literals 0, 1 and 2 returning `'warning'`,
   * which is not PrimeNG 21's word for it. The column has held `warn`, `success`
   * and `danger` all along.
   */
  getStatusSeverity(status: number | null | undefined) {
    return this.codeLists.severity('books-allowance-pay-status', status);
  }

  private toDate(value: string | null | undefined): Date | null {
    if (!value) return null;
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d;
  }
}
