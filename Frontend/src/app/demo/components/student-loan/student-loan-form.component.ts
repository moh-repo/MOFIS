import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, skip } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { StudentLoan } from '../../api/student-loan';
import { StudentLoanService } from '../../service/student-loan.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { MoheQuarterService, MoheQuarter } from '../../service/mohe-quarter.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { StudentPickerService, StudentOption } from '../../service/student-picker.service';
import { academicYearSpans } from '../../service/academic-year-spans';
import { localizeField } from '../../pipes/in-language.pipe';

@Component({
    selector: 'app-student-loan-form',
    templateUrl: './student-loan-form.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudentLoanFormComponent implements OnInit {

  form: FormGroup;
  isEditMode = false;
  loanId: number | null = null;
  loading = false;
  saving = false;

  currencies: LookupItem[] = [];
  quarters: MoheQuarter[] = [];
  students: StudentOption[] = [];
  filteredStudents: StudentOption[] = [];
  selectedStudent: StudentOption | null = null;

  loanStatuses: { id: number; name: string }[] = [];
  /** Academic years as the spans this column stores — see academic-year-spans.ts. */
  studyYearOptions: { label: string; value: string }[] = [];

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private service: StudentLoanService,
    private lookupService: LookupService,
    private quarterService: MoheQuarterService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService,
    private codeLists: CodeListService,
    private studentPicker: StudentPickerService
  ) {
    this.form = this.fb.group({
      loanId: [{ value: null, disabled: true }],
      studentId: [null, Validators.required],
      studyYear: [null],
      moheQuarterId: [null],
      // CK_STUDENT_LOAN_AMOUNT_POSITIVE refuses zero; this used to default to it.
      loanAmount: [null, [Validators.required, Validators.min(1)]],
      currencyId: [null, Validators.required],
      loanStatus: [0],
      repaymentNotes: [null]
    });
  }

  ngOnInit(): void {
    this.loadCodeLists();
    this.translationService.currentLanguage$.pipe(skip(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadCodeLists();
      this.loadLookups();
    });

    this.loadLookups();

    /*
     * Subscribed rather than read from the snapshot: the router reuses this
     * component when only :id changes, and the snapshot kept showing the first
     * record (live QA 23 Sep 2026, M5). Each id clears the form before loading.
     */
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const idParam = params.get('id');
      this.isEditMode = !!idParam;
      this.loanId = idParam ? +idParam : null;
      this.resetForm();
      if (this.isEditMode && this.loanId) {
        this.loadRecord(this.loanId);
      }
    });
  }

  /** Back to the blank form, as the constructor built it. */
  private resetForm(): void {
    this.selectedStudent = null;
    this.form.reset({
      loanId: null,
      studentId: null,
      studyYear: null,
      moheQuarterId: null,
      loanAmount: null,
      currencyId: null,
      loanStatus: 0,
      repaymentNotes: null
    });
  }

  /** Loan statuses and academic years, from the bulk code-list payload. */
  private loadCodeLists(): void {
    this.codeLists.whenLoaded().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const lang = this.translationService.getCurrentLanguage();
      this.loanStatuses = this.codeLists.active('loan-status')
        .map(row => ({ id: Number(row.value), name: this.codeLists.label('loan-status', row.value, lang) }))
        .filter(o => !isNaN(o.id));
      this.studyYearOptions = academicYearSpans(this.codeLists.active('academic-year'));
    });
  }

  private loadLookups(): void {
    forkJoin({
      currencies: this.lookupService.getStudyCurrencies().pipe(catchError(() => of([]))),
      quarters: this.quarterService.getAll().pipe(catchError(() => of([])))
    }).subscribe(res => {
      this.currencies = res.currencies ?? [];
      // The quarter's name in the reader's language: QUARTER_NAME is the Arabic one (QA B3).
      this.quarters = localizeField(res.quarters ?? [], 'quarterName', this.translationService.getCurrentLanguage());
    });

    this.studentPicker.search().subscribe(rows => {
      this.students = this.studentPicker.mergeAll(this.students, rows);
      this.resolveSelectedStudent();
    });
  }

  /**
   * The student the record names.
   *
   * <p>This used to be looked up in a list `loadLookups()` was still fetching,
   * so an edit page opened blank on a record that has a student. The picker
   * holds one page and this student is very often not on it, so they are asked
   * for by id.
   */
  private resolveSelectedStudent(): void {
    const studentId = this.form.get('studentId')?.value;
    if (!studentId) return;
    const held = this.students.find(s => s.studentId === studentId);
    if (held) {
      this.selectedStudent = held;
      return;
    }
    this.studentPicker.byIds([studentId]).subscribe(rows => {
      this.students = this.studentPicker.mergeAll(this.students, rows);
      this.selectedStudent = this.students.find(s => s.studentId === studentId) ?? null;
    });
  }

  private loadRecord(id: number): void {
    this.loading = true;
    this.service.getById(id).subscribe({
      next: (record) => {
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
        this.resolveSelectedStudent();
        this.loading = false;
      },
      error: (err: { status?: number }) => {
        // An unknown id in the address bar is the not-found page (live QA L1, 24 Sep 2026).
        if (err?.status === 404) {
          this.router.navigate(['/notfound']);
          return;
        }
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_LOAN.MESSAGES.LOAD_ERROR')
        });
        this.router.navigate(['/student-loan']);
      }
    });
  }

  /**
   * Searches on the server. Only one page of students is ever held, so filtering
   * in the browser could only ever have searched the page in hand.
   */
  onStudentSearch(event: { query?: string }): void {
    this.studentPicker.search(event?.query ?? '').subscribe(rows => {
      this.filteredStudents = rows;
      this.students = this.studentPicker.mergeAll(this.students, rows);
    });
  }

  /**
   * PrimeNG 21's autocomplete emits `{ originalEvent, value }` from `onSelect`,
   * not the row itself — so the old `student.studentId` read undefined and the
   * form never learned which student had been picked. Both shapes are accepted.
   */
  onStudentSelect(event: StudentOption | { value: StudentOption }): void {
    const student = (event as { value: StudentOption })?.value ?? (event as StudentOption);
    if (!student?.studentId) return;
    this.selectedStudent = student;
    this.form.patchValue({ studentId: student.studentId });
  }

  onStudentClear(): void {
    this.selectedStudent = null;
    this.form.patchValue({ studentId: null });
  }

  getStudentName(id: number | null | undefined): string {
    if (!id) return '';
    return this.students.find(s => s.studentId === id)?.studentName ?? id.toString();
  }

  /** True while the amount is present but not above zero — the database refuses it. */
  get amountIsNotPositive(): boolean {
    const control = this.form.get('loanAmount');
    return !!control && control.touched && control.value !== null && control.hasError('min');
  }

  saveRecord(): void {
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
      // Neither loanId nor midate is sent — see the list component for why.
    };

    this.saving = true;
    const request$ = this.isEditMode
      ? this.service.update(this.loanId!, { ...payload, loanId: this.loanId! })
      : this.service.create(payload);

    request$.subscribe({
      next: (response: any) => {
        const key = this.isEditMode ? 'STUDENT_LOAN.MESSAGES.UPDATE_SUCCESS' : 'STUDENT_LOAN.MESSAGES.CREATE_SUCCESS';
        // The write opens an approval request rather than saving, so the server's
        // own message is shown instead of "created successfully".
        const detail = (response?.status === 'PENDING' && response?.message)
          ? response.message
          : this.translationService.translate(key);
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail
        });
        this.saving = false;
        setTimeout(() => this.router.navigate(['/student-loan']), 800);
      },
      error: () => {
        this.saving = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_LOAN.MESSAGES.SAVE_ERROR')
        });
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/student-loan']);
  }
}
