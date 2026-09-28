import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, skip } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { StudentBooksAllowance } from '../../api/student-books-allowance';
import { StudentBooksAllowanceService } from '../../service/student-books-allowance.service';
import { MoheQuarterService, MoheQuarter } from '../../service/mohe-quarter.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { StudentPickerService, StudentOption } from '../../service/student-picker.service';
import { academicYearSpans } from '../../service/academic-year-spans';
import { localizeField } from '../../pipes/in-language.pipe';

@Component({
    selector: 'app-student-books-allowance-form',
    templateUrl: './student-books-allowance-form.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudentBooksAllowanceFormComponent implements OnInit {

  form: FormGroup;
  isEditMode = false;
  booksAllowId: number | null = null;
  loading = false;
  saving = false;

  quarters: MoheQuarter[] = [];
  students: StudentOption[] = [];
  filteredStudents: StudentOption[] = [];
  selectedStudent: StudentOption | null = null;

  payStatuses: { id: number; name: string }[] = [];
  /** Academic years as the spans this column stores - see academic-year-spans.ts. */
  studyYearOptions: { label: string; value: string }[] = [];

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private service: StudentBooksAllowanceService,
    private quarterService: MoheQuarterService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService,
    private codeLists: CodeListService,
    private studentPicker: StudentPickerService
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
      this.booksAllowId = idParam ? +idParam : null;
      this.resetForm();
      if (this.isEditMode && this.booksAllowId) {
        this.loadRecord(this.booksAllowId);
      }
    });
  }

  /** Back to the blank form, as the constructor built it. */
  private resetForm(): void {
    this.selectedStudent = null;
    this.form.reset({
      booksAllowId: null,
      studentId: null,
      studyYear: null,
      moheQuarterId: null,
      allowanceAmount: 0,
      payStatus: 0,
      receiptDate: null,
      notes: null
    });
  }

  /** Pay statuses and academic years, from the bulk code-list payload. */
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

  private loadLookups(): void {
    forkJoin({
      quarters: this.quarterService.getAll().pipe(catchError(() => of([])))
    }).subscribe(res => {
      // The quarter's name in the reader's language: QUARTER_NAME is the Arabic one (QA B3).
      this.quarters = localizeField(res.quarters ?? [], 'quarterName', this.translationService.getCurrentLanguage());
    });

    this.studentPicker.search().subscribe(rows => {
      this.students = this.studentPicker.mergeAll(this.students, rows);
      this.resolveSelectedStudent();
    });
  }

  /**
   * Puts the record's student in the autocomplete.
   *
   * <p>This used to look the id up in `this.students`, which the student fetch
   * was still filling, so opening a record for edit usually showed an empty
   * student box beside a form that had one. The picker is asked for that id
   * directly - it holds one page, and the record's student is rarely on it.
   */
  private resolveSelectedStudent(): void {
    const studentId = this.form.get('studentId')?.value;
    if (!studentId) {
      this.selectedStudent = null;
      return;
    }
    const known = this.students.find(s => s.studentId === studentId);
    if (known) {
      this.selectedStudent = known;
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
          booksAllowId: record.booksAllowId ?? null,
          studentId: record.studentId ?? null,
          studyYear: record.studyYear ?? null,
          moheQuarterId: record.moheQuarterId ?? null,
          allowanceAmount: record.allowanceAmount ?? 0,
          payStatus: record.payStatus ?? 0,
          receiptDate: this.toDate(record.receiptDate),
          notes: record.notes ?? null
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
          detail: this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.MESSAGES.LOAD_ERROR')
        });
        this.router.navigate(['/student-books-allowance']);
      }
    });
  }

  /**
   * Searches the picker on the server as the user types.
   *
   * <p>Only one page of students is held at a time, so filtering the held array
   * could only ever have searched that page.
   */
  onStudentSearch(event: any): void {
    this.studentPicker.search(event?.query || '').subscribe(rows => {
      this.students = this.studentPicker.mergeAll(this.students, rows);
      this.filteredStudents = rows;
    });
  }

  onStudentSelect(student: StudentOption): void {
    this.selectedStudent = student;
    this.form.patchValue({ studentId: student.studentId });
  }

  onStudentClear(): void {
    this.selectedStudent = null;
    this.form.patchValue({ studentId: null });
  }

  /** The academic years offered by the study-year select. */
  get studyYears(): { label: string; value: string }[] {
    return this.studyYearOptions;
  }

  getStudentName(id: number | null | undefined): string {
    if (!id) return '';
    return this.students.find(s => s.studentId === id)?.studentName ?? id.toString();
  }

  saveRecord(): void {
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
    const request$ = this.isEditMode
      ? this.service.update(this.booksAllowId!, payload)
      : this.service.create(payload);

    request$.subscribe({
      next: (response: any) => {
        const key = this.isEditMode ? 'STUDENT_BOOKS_ALLOWANCE.MESSAGES.UPDATE_SUCCESS' : 'STUDENT_BOOKS_ALLOWANCE.MESSAGES.CREATE_SUCCESS';
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
        setTimeout(() => this.router.navigate(['/student-books-allowance']), 800);
      },
      error: () => {
        this.saving = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_BOOKS_ALLOWANCE.MESSAGES.SAVE_ERROR')
        });
      }
    });
  }

  private toDate(value: string | null | undefined): Date | null {
    if (!value) return null;
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d;
  }

  goBack(): void {
    this.router.navigate(['/student-books-allowance']);
  }
}
