import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PaymentAuth } from '../../api/payment-auth';
import { Studentdata } from '../../api/studentdata';
import { PaymentAuthService, AmountCalculation } from '../../service/payment-auth.service';
import { TranslationService } from '../../service/translation.service';
import { LookupService } from '../../service/lookup.service';
import { StudentPickerService } from '../../service/student-picker.service';
import { ReportPdfQuery, ReportPdfService, buildReportPdfQuery } from '../../service/report-pdf.service';
import { extractApiErrorMessage } from '../../service/api-error.util';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { changesOnly } from '../../service/reload-streams.util';
import { pageOf } from '../../service/table-paging.util';

@Component({
    selector: 'app-payment-authorization',
    templateUrl: './payment-authorization.component.html',
    styleUrls: ['./payment-authorization.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class PaymentAuthorizationComponent implements OnInit {
  /** For the code-list pipes: the language the names are read in. */
  get currentLanguage(): string {
    return this.translationService.getCurrentLanguage();
  }


  records: PaymentAuth[] = [];
  totalElements = 0;
  loading = false;
  saving = false;

  currentPage = 0;
  pageSize = 10;
  /** Bound to the table, so a search that reloads page one moves the paginator there too (QA B8, N9). */
  first = 0;
  sortBy = 'id';
  sortDir = 'desc';
  searchTerm = '';
  selectedStatusFilter: string | null = null;

  formVisible = false;
  viewVisible = false;
  formMode: 'create' | 'edit' = 'create';
  /** Set by the first save attempt, so required-field errors show only after it. */
  submitted = false;

  /** Only a draft is edited or deleted; after release the record is the voucher (F6). */
  static readonly DRAFT = 'DRAFT';

  /**
   * The language the Jasper documents are rendered in. Both the voucher's masthead
   * and the list report's are stored in Arabic and English on the settings row, so
   * this picks which one the server prints; it follows the UI language by default.
   */
  reportLang = 'ar';
  form: PaymentAuth = new PaymentAuth();
  viewRecord: PaymentAuth | null = null;

  // Student dropdown — precomputed once to avoid change-detection loops
  students: Studentdata[] = [];
  studentsForDropdown: { id: number; label: string }[] = [];
  /** Typing in the picker's filter box; searched on the server, debounced. */
  private readonly studentSearch$ = new Subject<string>();

  // Options rebuilt on language change
  amountSources: { label: string; value: string }[] = [];
  selectedSources: string[] = ['FINANCE'];
  calculatingAmount = false;
  amountBreakdown: AmountCalculation | null = null;

  statusOptions: { label: string; value: string | null }[] = [];
  statusFormOptions: { label: string; value: string }[] = [];

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private service: PaymentAuthService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public translationService: TranslationService,
    private lookupService: LookupService,
    private httpClient: HttpClient,
    private router: Router,
    private studentPicker: StudentPickerService,
    private reportPdfService: ReportPdfService
  ) {}

  ngOnInit(): void {
    this.reportLang = this.translationService.getCurrentLanguage() === 'en' ? 'en' : 'ar';
    this.loadOptionLists();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadOptionLists());
    this.loadAll();
    this.loadStudents();
    this.studentSearch$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(term => this.loadStudents(term));
  }

  // ── DB-driven option lists ────────────────────────────────────────────────

  private loadOptionLists(): void {
    this.lookupService.getPaymentAmountSources().subscribe(sources => {
      this.amountSources = sources;
    });
    this.lookupService.getPaymentAuthStatuses().subscribe(statuses => {
      this.statusOptions = [
        { label: this.t('COMMON.ALL'), value: null },
        ...statuses
      ];
      this.statusFormOptions = statuses.filter(s => s.value !== null) as { label: string; value: string }[];
    });
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadAll(): void {
    this.loading = true;
    this.service.getAll(this.currentPage, this.pageSize, this.sortBy, this.sortDir,
        this.searchTerm || undefined, this.selectedStatusFilter || undefined)
      .subscribe({
        next: (res) => {
          this.records = res.content || [];
          this.totalElements = res.totalElements;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('PAYMENT_AUTH.LOAD_ERROR') });
        }
      });
  }

  /**
   * The student picker's first page, then a server-side search as the user types.
   *
   * <p>This asked `/api/v1/studentdata/?page=0&size=5000` and read `res.content`.
   * That endpoint returns a bare array and ignores paging, so `content` was
   * always undefined and the dropdown was empty for everyone — and the student
   * list is gated on `STUDENT_PII`, which the finance roles do not hold, so even
   * the right URL would have answered 403. `/studentdata/options` returns an id
   * and a name and is open to finance. Same fix as the finance recipients screen.
   */
  loadStudents(search?: string): void {
    this.studentPicker.search(search).subscribe(rows => {
      // The student the form already names is kept whatever the search says: a
      // p-select shows the label of its value only while that value is one of
      // its options.
      this.students = this.studentPicker.merge(
        this.students as any, rows, [this.form?.studentId]) as any;
      this.studentsForDropdown = this.studentPicker.toOptions(this.students as any);
    });
  }

  /** Searches the picker on the server; only one page is ever held locally. */
  onStudentFilter(event: { filter: string }): void {
    this.studentSearch$.next(event?.filter ?? '');
  }

  // ── Amount calculation ────────────────────────────────────────────────────

  calculateAmount(): void {
    if (!this.form.studentId || this.selectedSources.length === 0) {
      this.messageService.add({ severity: 'warn', summary: this.t('COMMON.WARNING'), detail: this.t('PAYMENT_AUTH.SOURCE_REQUIRED') });
      return;
    }
    this.calculatingAmount = true;
    this.service.calculateAmount(this.form.studentId, this.selectedSources).subscribe({
      next: (res) => {
        this.amountBreakdown = res;
        this.form.originalAmount = Number(res.totalAmount) || 0;
        this.form.amountSource = this.selectedSources.length === 3 ? 'ALL'
          : this.selectedSources.length === 1 ? this.selectedSources[0] : 'COMBINATION';
        this.onDeductionChange();
        this.calculatingAmount = false;
      },
      error: () => {
        this.calculatingAmount = false;
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('PAYMENT_AUTH.CALCULATE_ERROR') });
      }
    });
  }

  onDeductionChange(): void {
    const orig = this.form.originalAmount || 0;
    const ded = this.form.deductions || 0;
    this.form.netAmount = Math.max(0, orig - ded);
  }

  // ── Dialog open/close ─────────────────────────────────────────────────────

  openNew(): void {
    this.form = new PaymentAuth();
    this.form.authDate = new Date().toISOString().split('T')[0];
    this.form.authStatus = PaymentAuthorizationComponent.DRAFT;
    this.selectedSources = ['FINANCE'];
    this.amountBreakdown = null;
    this.submitted = false;
    this.formMode = 'create';
    this.formVisible = true;
  }

  isDraft(record: PaymentAuth | null | undefined): boolean {
    return (record?.authStatus || '').toUpperCase() === PaymentAuthorizationComponent.DRAFT;
  }

  isApproved(record: PaymentAuth | null | undefined): boolean {
    return (record?.authStatus || '').toUpperCase() === 'APPROVED';
  }

  openEdit(record: PaymentAuth): void {
    // Released or paid: shown, not edited (the server refuses the save anyway).
    if (!this.isDraft(record)) {
      this.openView(record);
      return;
    }
    this.submitted = false;
    this.form = { ...record };
    this.formMode = 'edit';
    this.selectedSources = record.amountSource === 'ALL'
      ? ['FINANCE', 'WAFAR', 'UNIVERSITY_FEE']
      : record.amountSource === 'COMBINATION'
        ? ['FINANCE', 'WAFAR', 'UNIVERSITY_FEE']
        : [record.amountSource || 'FINANCE'];
    this.amountBreakdown = null;
    this.formVisible = true;
  }

  openView(record: PaymentAuth): void {
    this.viewRecord = record;
    this.viewVisible = true;
  }

  /**
   * Prints the statutory voucher — نموذج رقم ١١ حسابات — for one record.
   *
   * <p>This used to hide the whole application behind a CSS class, render an HTML
   * replica of the form, and call {@code window.print()} 400 ms later in the hope
   * that the browser had laid it out by then. It now prints the server's own PDF
   * through the shared viewer, which raises the print dialog when the document has
   * actually mounted rather than after a guessed delay — and which carries the
   * masthead, the ministry logo and the footer sentence, none of which the replica
   * could show because they live in REPORT_TYPE_SETTINGS.
   */
  openPrint(record: PaymentAuth): void {
    this.viewVisible = false;
    this.reportPdfService.printPdf(
      this.voucherApiPath(record.id),
      { lang: this.reportLang },
      this.voucherFilename(record));
  }

  openReport(record: PaymentAuth): void {
    this.router.navigate(['/payment-authorization/report', record.id]);
  }

  // ── The Jasper documents ──────────────────────────────────────────────────

  /** The voucher endpoint for one record. */
  private voucherApiPath(id: number): string {
    return `/api/v1/paymentAuth/${id}/export-report`;
  }

  private voucherFilename(record: PaymentAuth): string {
    return `payment-auth-${record.authFormNumber || record.id}.pdf`;
  }

  /** The tabular report over the whole list. */
  readonly listReportApiPath = '/api/v1/paymentAuth/export-report';

  /**
   * The filters on screen, passed to the report rather than applied afterwards:
   * what is printed is what the page is showing.
   */
  listReportQuery(): ReportPdfQuery {
    // The builder drops empty entries, so "all statuses" sends no status at all
    // rather than an empty one the server would have to interpret.
    return buildReportPdfQuery([
      ['lang', this.reportLang],
      ['status', this.selectedStatusFilter]
    ]);
  }

  get reportLangOptions(): Array<{ label: string; value: string }> {
    return this.lookupService.getReportLangOptions();
  }

  resetForm(): void {
    this.form = new PaymentAuth();
    this.selectedSources = ['FINANCE'];
    this.amountBreakdown = null;
  }

  // ── Save / Delete ─────────────────────────────────────────────────────────

  // ── Validation (F6) ───────────────────────────────────────────────────────

  isStudentInvalid(): boolean {
    return !this.form.studentId || this.form.studentId <= 0;
  }

  isDateInvalid(): boolean {
    return !this.form.authDate;
  }

  isOriginalAmountInvalid(): boolean {
    return !(Number(this.form.originalAmount) > 0);
  }

  isDeductionsInvalid(): boolean {
    const ded = Number(this.form.deductions || 0);
    return ded < 0 || ded > Number(this.form.originalAmount || 0);
  }

  save(): void {
    this.submitted = true;
    if (this.isStudentInvalid() || this.isDateInvalid() || this.isOriginalAmountInvalid() || this.isDeductionsInvalid()) {
      this.messageService.add({
        severity: 'error', summary: this.t('COMMON.VALIDATION_ERROR'),
        detail: this.t(this.isStudentInvalid() ? 'PAYMENT_AUTH.STUDENT_REQUIRED' : 'PAYMENT_AUTH.REQUIRED_FIELDS')
      });
      return;
    }
    this.saving = true;
    const op$ = this.formMode === 'create'
      ? this.service.create(this.form)
      : this.service.update(this.form.id, this.form);
    op$.subscribe({
      next: () => {
        this.saving = false;
        this.formVisible = false;
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('PAYMENT_AUTH.SAVE_SUCCESS') });
        this.loadAll();
      },
      error: (err) => {
        this.saving = false;
        // 409: released since the form opened; 400: a rule the server names.
        const detail = err?.status === 409 ? this.t('PAYMENT_AUTH.ONLY_DRAFT_EDITABLE')
          : extractApiErrorMessage(err) || this.t('PAYMENT_AUTH.SAVE_ERROR');
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail });
        if (err?.status === 409) {
          this.formVisible = false;
          this.loadAll();
        }
      }
    });
  }

  /** DRAFT to APPROVED, through the release endpoint (PAYMENT_AUTH.APPROVE). */
  confirmAuthorize(record: PaymentAuth): void {
    this.confirmationService.confirm({
      message: this.t('PAYMENT_AUTH.AUTHORIZE_CONFIRM_MSG').replace('{num}', record.authFormNumber || String(record.id)),
      header: this.t('PAYMENT_AUTH.AUTHORIZE'),
      icon: 'pi pi-check-circle',
      acceptLabel: this.t('COMMON.YES'),
      rejectLabel: this.t('COMMON.NO'),
      rejectButtonStyleClass: 'p-button-text',
      accept: () => {
        this.service.authorize(record.id).subscribe({
          next: (res) => {
            const failed = res?.failed?.[0];
            this.messageService.add(failed
              ? { severity: 'warn', summary: this.t('COMMON.WARNING'), detail: failed.reason }
              : { severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('PAYMENT_AUTH.AUTHORIZE_SUCCESS') });
            this.viewVisible = false;
            this.loadAll();
          },
          error: () => this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('PAYMENT_AUTH.AUTHORIZE_ERROR') })
        });
      }
    });
  }

  /** APPROVED to PAID. */
  confirmMarkPaid(record: PaymentAuth): void {
    this.confirmationService.confirm({
      message: this.t('PAYMENT_AUTH.MARK_PAID_CONFIRM_MSG').replace('{num}', record.authFormNumber || String(record.id)),
      header: this.t('PAYMENT_AUTH.MARK_PAID'),
      icon: 'pi pi-wallet',
      acceptLabel: this.t('COMMON.YES'),
      rejectLabel: this.t('COMMON.NO'),
      rejectButtonStyleClass: 'p-button-text',
      accept: () => {
        this.service.markPaid(record.id).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('PAYMENT_AUTH.MARK_PAID_SUCCESS') });
            this.viewVisible = false;
            this.loadAll();
          },
          error: (err) => this.messageService.add({
            severity: 'error', summary: this.t('COMMON.ERROR'),
            detail: extractApiErrorMessage(err) || this.t('PAYMENT_AUTH.MARK_PAID_ERROR')
          })
        });
      }
    });
  }

  confirmDelete(record: PaymentAuth): void {
    this.confirmationService.confirm({
      message: this.t('PAYMENT_AUTH.DELETE_CONFIRM_MSG').replace('{num}', record.authFormNumber || String(record.id)),
      header: this.t('PAYMENT_AUTH.DELETE_CONFIRM_TITLE'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.t('COMMON.YES'),
      rejectLabel: this.t('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => {
        this.service.delete(record.id).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('PAYMENT_AUTH.DELETE_SUCCESS') });
            this.loadAll();
          },
          error: () => this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('PAYMENT_AUTH.DELETE_ERROR') })
        });
      }
    });
  }

  // ── Pagination / Search ───────────────────────────────────────────────────

  onPageChange(event: any): void {
    const at = pageOf(event, this.pageSize);
    this.currentPage = at.page;
    this.pageSize = at.rows;
    this.first = at.first;
    this.loadAll();
  }

  onSearch(): void {
    this.currentPage = 0;
    this.first = 0;
    this.loadAll();
  }

  // ── Display helpers ───────────────────────────────────────────────────────

  getStudentName(id: number): string {
    if (!id) return this.t('COMMON.NOT_AVAILABLE');
    const s = this.students.find(x => x.studentId === id);
    return s ? (s.studentName || String(id)) : String(id);
  }

  formatAmount(v: number): string {
    if (!v && v !== 0) return '—';
    return Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  t(key: string): string {
    return this.translationService.translate(key) || key;
  }
}
