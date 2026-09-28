import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { StudentLoan } from '../../api/student-loan';
import { StudentLoanService } from '../../service/student-loan.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { MoheQuarterService, MoheQuarter } from '../../service/mohe-quarter.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService, PrimeSeverity } from '../../service/code-list.service';
import { StudentPickerService, StudentOption } from '../../service/student-picker.service';
import { changesOnly } from '../../service/reload-streams.util';
import { localizeField } from '../../pipes/in-language.pipe';

@Component({
    selector: 'app-student-loan-view',
    templateUrl: './student-loan-view.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudentLoanViewComponent implements OnInit {

  record: StudentLoan | null = null;
  loading = false;
  loanId: number | null = null;

  currencies: LookupItem[] = [];
  quarters: MoheQuarter[] = [];
  students: StudentOption[] = [];

  loanStatuses: { id: number; name: string }[] = [];

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private service: StudentLoanService,
    private lookupService: LookupService,
    private quarterService: MoheQuarterService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService,
    private codeLists: CodeListService,
    private studentPicker: StudentPickerService
  ) {}

  ngOnInit(): void {
    this.loadLoanStatuses();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadLoanStatuses());

    this.loadLookups();

    /*
     * Subscribed rather than read from the snapshot: the router reuses this
     * component when only :id changes, and the snapshot kept showing the first
     * record (live QA 23 Sep 2026, M5). Each id clears the last one before loading.
     */
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const idParam = params.get('id');
      this.loanId = idParam ? +idParam : null;
      this.record = null;
      this.students = [];
      if (this.loanId) {
        this.loadRecord(this.loanId);
      }
    });
  }

  /** Loan statuses from the bulk code-list payload the route guard already warms. */
  private loadLoanStatuses(): void {
    this.codeLists.whenLoaded().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const lang = this.translationService.getCurrentLanguage();
      this.loanStatuses = this.codeLists.active('loan-status')
        .map(row => ({ id: Number(row.value), name: this.codeLists.label('loan-status', row.value, lang) }))
        .filter(o => !isNaN(o.id));
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

  }

  private loadRecord(id: number): void {
    this.loading = true;
    this.service.getById(id).subscribe({
      next: (record) => {
        this.record = record;
        this.loading = false;
        // Only this one student is named, and the picker's first page almost
        // never holds them — so they are asked for by id.
        this.studentPicker.byIds([record.studentId]).subscribe(rows => { this.students = rows; });
      },
      error: (err: { status?: number }) => {
        // A loan that does not exist is the app's not-found page, as for a
        // student profile — not a silent return to the list (live QA L1, 24 Sep 2026).
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

  getStudentName(id: number | null | undefined): string {
    if (!id) return this.translationService.translate('COMMON.NOT_AVAILABLE');
    return this.students.find(s => s.studentId === id)?.studentName ?? id.toString();
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

  /** The colour the database gives the status — see the list component. */
  getStatusSeverity(status: number | null | undefined): PrimeSeverity {
    return this.codeLists.severity('loan-status', status);
  }

  navigateToEdit(): void {
    this.router.navigate(['/student-loan/edit', this.loanId]);
  }

  goBack(): void {
    this.router.navigate(['/student-loan']);
  }
}
