import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { StudentBooksAllowance } from '../../api/student-books-allowance';
import { StudentBooksAllowanceService } from '../../service/student-books-allowance.service';
import { MoheQuarterService, MoheQuarter } from '../../service/mohe-quarter.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { StudentPickerService, StudentOption } from '../../service/student-picker.service';
import { changesOnly } from '../../service/reload-streams.util';
import { localizeField } from '../../pipes/in-language.pipe';

@Component({
    selector: 'app-student-books-allowance-view',
    templateUrl: './student-books-allowance-view.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudentBooksAllowanceViewComponent implements OnInit {

  record: StudentBooksAllowance | null = null;
  loading = false;
  booksAllowId: number | null = null;

  quarters: MoheQuarter[] = [];
  students: StudentOption[] = [];

  payStatuses: { id: number; name: string }[] = [];

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private service: StudentBooksAllowanceService,
    private quarterService: MoheQuarterService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService,
    private codeLists: CodeListService,
    private studentPicker: StudentPickerService
  ) {}

  ngOnInit(): void {
    this.loadPayStatuses();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadPayStatuses());

    this.loadLookups();

    /*
     * Subscribed rather than read from the snapshot: the router reuses this
     * component when only :id changes, and the snapshot kept showing the first
     * record (live QA 23 Sep 2026, M5). Each id clears the last one before loading.
     */
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const idParam = params.get('id');
      this.booksAllowId = idParam ? +idParam : null;
      this.record = null;
      if (this.booksAllowId) {
        this.loadRecord(this.booksAllowId);
      }
    });
  }

  /** Pay statuses from the bulk code-list payload, with the colour they carry. */
  private loadPayStatuses(): void {
    this.codeLists.whenLoaded().subscribe(() => {
      const lang = this.translationService.getCurrentLanguage();
      this.payStatuses = this.codeLists.active('books-allowance-pay-status')
        .map(row => ({
          id: Number(row.value),
          name: this.codeLists.label('books-allowance-pay-status', row.value, lang)
        }))
        .filter(o => !isNaN(o.id));
    });
  }

  private loadLookups(): void {
    forkJoin({
      quarters: this.quarterService.getAll().pipe(catchError(() => of([])))
    }).subscribe(res => {
      // The quarter's name in the reader's language: QUARTER_NAME is the Arabic one (QA B3).
      this.quarters = localizeField(res.quarters ?? [], 'quarterName', this.translationService.getCurrentLanguage());
    });

    // The student list is gated on STUDENT_PII, which the finance roles that
    // run this screen do not hold; the picker answers with an id and a name.
    this.studentPicker.search().subscribe(rows => {
      this.students = this.studentPicker.mergeAll(this.students, rows);
    });
  }

  private loadRecord(id: number): void {
    this.loading = true;
    this.service.getById(id).subscribe({
      next: (record) => {
        this.record = record;
        this.loading = false;
        // The picker holds one page, and this record's student is rarely on it.
        if (record?.studentId) {
          this.studentPicker.byIds([record.studentId]).subscribe(rows => {
            this.students = this.studentPicker.mergeAll(this.students, rows);
          });
        }
      },
      error: (err: { status?: number }) => {
        // An allowance that does not exist is the not-found page (live QA L1, 24 Sep 2026).
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

  /** The colour LU_BOOKS_ALLOWANCE_PAY_STATUS.UI_SEVERITY gives the status. */
  getStatusSeverity(status: number | null | undefined) {
    return this.codeLists.severity('books-allowance-pay-status', status);
  }

  navigateToEdit(): void {
    this.router.navigate(['/student-books-allowance/edit', this.booksAllowId]);
  }

  goBack(): void {
    this.router.navigate(['/student-books-allowance']);
  }
}
