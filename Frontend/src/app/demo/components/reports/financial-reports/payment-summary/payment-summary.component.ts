import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { environment } from 'src/environments/environment';
import { Subject, Subscription } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { CodeListService, PrimeSeverity } from 'src/app/demo/service/code-list.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface PaymentSummary {
  paymentId: number;
  studentId: number;
  studentName: string;
  studentPassportNumber: string;
  paymentDate: string;
  paymentAmount: number;
  paymentCurrency: string;
  paymentStatus: string | null;
  paymentStatusEn?: string | null;
  /** STUDY_FIN_RECEIVED_STATUS.CODE - the key the tag is coloured and the totals are grouped by. */
  paymentStatusCode: string | null;
  paymentType: string | null;
  paymentTypeEn?: string | null;
  /** LU_PAYMENT_METHOD code (V078). */
  paymentMethod: string;
  quarterName: string | null;
  quarterNameEn?: string | null;
  yearName: number | string | null;
  universityName: string;
  majorName: string;
  paymentYear?: number;
  paymentMonth?: number;
}

interface LookupOptionResponse {
  id: number | null;
  name: string;
}

interface PaymentMetadataResponse {
  statuses: LookupOptionResponse[];
  types: LookupOptionResponse[];
  quarters: LookupOptionResponse[];
  years: LookupOptionResponse[];
}

interface DropdownOption<T> {
  label: string;
  value: T;
}

interface PaymentMetadata {
  statuses: DropdownOption<string>[];
  types: DropdownOption<string>[];
  quarters: DropdownOption<string>[];
  years: DropdownOption<number>[];
}

interface PaymentSummaryPage {
  content: PaymentSummary[];
  totalElements: number;
  number: number;
  size: number;
}

/** The money cards over every row the filters select (R6-05). */
interface PaymentSummaryTotals {
  totalAmount: number | string | null;
  byStatus: Array<{ code: string | null; amount: number | string | null }> | null;
}

@Component({
    selector: 'app-payment-summary',
    templateUrl: './payment-summary.component.html',
    styleUrls: ['./payment-summary.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class PaymentSummaryComponent implements OnInit, OnDestroy {

  paymentData: PaymentSummary[] = [];
  metadata: PaymentMetadata = {
    statuses: [],
    types: [],
    quarters: [],
    years: []
  };

  // Filter options
  selectedStatus: string | null = null;
  selectedType: string | null = null;
  selectedMethod: string | null = null;
  selectedQuarter: string | null = null;
  selectedYear: number | null = null;
  searchText: string = '';
  startDate: Date | null = null;
  endDate: Date | null = null;
  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];

  // Pagination
  currentPage: number = 0;
  pageSize: number = 20;
  totalRecords: number = 0;

  // Summary statistics
  totalAmount: number | null = 0;
  totalPayments: number = 0;
  /** Every selected row's amount per status code, in the code list's order; a null code is "unspecified". */
  amountByStatus: Array<{ code: string | null; amount: number }> = [];

  loading: boolean = false;
  private readonly destroy$ = new Subject<void>();
  /** The totals call in flight; a newer filter cancels it, so an older answer cannot land last. */
  private totalsSub?: Subscription;

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public translationService: TranslationService,
    private reportCountryScope: ReportCountryScopeService,
    private reportPdfService: ReportPdfService,
        private lookupService: LookupService,
        public codeLists: CodeListService) { }

  get hasMultiCountry(): boolean {
    return this.reportCountryScope.hasMultiCountryAccess();
  }

  get currentLanguage(): string {
    return this.translationService.getCurrentLanguage();
  }

  /** Payment-method filter options from LU_PAYMENT_METHOD (V078). */
  get methodOptions(): Array<{ label: string; value: string }> {
    return this.codeLists.options('payment-method', this.currentLanguage);
  }

  methodLabel(code: string | null | undefined): string {
    return this.codeLists.label('payment-method', code, this.currentLanguage);
  }

  statusLabel(code: string | null | undefined): string {
    return this.codeLists.label('fin-received-status', code, this.currentLanguage);
  }

  /**
   * A row's lookup name in the screen's language. The row carries the Arabic name and
   * its English sibling, and the English screen showed the Arabic one for the type,
   * the status and the quarter (round-6 QA R6-06). A blank name falls back to the other.
   */
  localized(ar: string | null | undefined, en: string | null | undefined): string | null {
    const arabic = this.currentLanguage === 'ar';
    return (arabic ? ar : en) || (arabic ? en : ar) || null;
  }

  /** The row field a name column sorts by: the one it shows. */
  nameField(field: 'paymentType' | 'paymentStatus'): string {
    return this.currentLanguage === 'ar' ? field : `${field}En`;
  }

  ngOnInit(): void {
    if (this.hasMultiCountry) {
      this.loadCountries();
    }
    this.loadMetadata();
    this.loadPaymentData();
    this.loadTotals();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
      this.loadPaymentData();
      this.loadTotals();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadCountries(): void {
    this.http.get<any[]>(`${environment.apiUrl}/mohe/api/lookup-tables/study-country-in`)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          const isAr = this.translationService.getCurrentLanguage() === 'ar';
          const all = (Array.isArray(data) ? data : ((data as any)?.content || [])).map(c => ({
            label: (isAr ? c.studyCountryInName : c.studyCountryInNameEn) || c.studyCountryInName || String(c.id),
            value: c.id as number
          }));
          if (!this.reportCountryScope.isMinistryUser && this.reportCountryScope.allowedCountryIds.length > 0) {
            const allowed = new Set(this.reportCountryScope.allowedCountryIds);
            this.allowedCountries = all.filter(c => allowed.has(c.value));
          } else {
            this.allowedCountries = all;
          }
        },
        error: () => {}
      });
  }

  private buildBaseParams(): HttpParams {
    if (this.selectedCountryIds.length > 0) {
      return this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds);
    }
    return this.reportCountryScope.appendCountryParam();
  }

  loadMetadata(): void {
    this.http.get<PaymentMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/financial/payment-summary/metadata`,
      { params: this.buildBaseParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          statuses: this.toStringOptions(data.statuses),
          types: this.toStringOptions(data.types),
          quarters: this.toStringOptions(data.quarters),
          years: this.toYearOptions(data.years)
        };
      },
      error: (error) => {
        console.error('Error loading payment metadata:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.FINANCIAL_DATA_FORM.METADATA_ERROR')
        });
      }
    });
  }

  /** The filters as query parameters; the page and its totals are asked for the same rows. */
  private filterParams(): HttpParams {
    let params = this.buildBaseParams();
    if (this.selectedStatus) params = params.set('status', this.selectedStatus);
    if (this.selectedType) params = params.set('type', this.selectedType);
    if (this.selectedMethod) params = params.set('method', this.selectedMethod);
    if (this.selectedQuarter) params = params.set('quarter', this.selectedQuarter);
    if (this.selectedYear !== null) params = params.set('year', String(this.selectedYear));
    if (this.searchText) params = params.set('search', this.searchText.trim());
    if (this.startDate) params = params.set('startDate', this.toDateParam(this.startDate));
    if (this.endDate) params = params.set('endDate', this.toDateParam(this.endDate));
    return params;
  }

  /** The rows and their count; the page and the filters both change them. */
  loadPaymentData(): void {
    this.loading = true;
    const params = this.filterParams()
      .set('page', String(this.currentPage))
      .set('size', String(this.pageSize));

    this.http.get<PaymentSummaryPage>(
      `${environment.apiUrl}/mohe/api/reports/financial/payment-summary/`,
      { params }
    ).subscribe({
      next: (response) => {
        // One bad row must not take the whole page down with it (R5-05).
        this.paymentData = (response?.content ?? []).filter((row): row is PaymentSummary => row != null);
        this.totalRecords = response?.totalElements ?? 0;
        this.totalPayments = this.totalRecords;
        this.pageSize = response?.size ?? this.pageSize;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading payment data:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.INDIVIDUAL_REPORTS.PAYMENT_SUMMARY.NO_PAYMENT_RECORDS')
        });
        this.paymentData = [];
        this.totalRecords = 0;
        this.totalPayments = 0;
        this.loading = false;
      }
    });
  }

  /**
   * Total Amount and Amount by status over every row the filters select. They were
   * added up from the page on screen while Total Payments counted every row, so the
   * card read 24,300 of 1,596,620 (round-6 QA R6-05). Only the filters change them;
   * turning the page does not.
   */
  loadTotals(): void {
    this.totalsSub?.unsubscribe();
    this.totalsSub = this.http.get<PaymentSummaryTotals>(
      `${environment.apiUrl}/mohe/api/reports/financial/payment-summary/totals`,
      { params: this.filterParams() }
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: (totals) => this.applyTotals(totals),
      error: (error) => {
        console.error('Error loading payment totals:', error);
        this.applyTotals(null);
      }
    });
  }

  /** No totals (a failed call) shows the cards empty rather than a sum of 0. */
  private applyTotals(totals: PaymentSummaryTotals | null): void {
    this.totalAmount = totals ? this.finite(totals.totalAmount) : null;
    const order = this.codeLists.active('fin-received-status').map((row) => String(row.value));
    const rank = (code: string | null): number => (code === null ? order.length : order.indexOf(code));
    this.amountByStatus = (totals?.byStatus ?? [])
      .filter((row) => row != null)
      .map((row) => ({ code: row.code ?? null, amount: this.finite(row.amount) }))
      .sort((a, b) => rank(a.code) - rank(b.code));
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadPaymentData();
    this.loadTotals();
  }

  onCountryChange(): void {
    this.currentPage = 0;
    this.loadMetadata();
    this.loadPaymentData();
    this.loadTotals();
  }

  clearFilters(): void {
    this.selectedStatus = null;
    this.selectedType = null;
    this.selectedMethod = null;
    this.selectedQuarter = null;
    this.selectedYear = null;
    this.searchText = '';
    this.startDate = null;
    this.endDate = null;
    this.selectedCountryIds = [];
    this.currentPage = 0;
    this.loadPaymentData();
    this.loadTotals();
  }

  onPageChange(event: any): void {
    this.currentPage = event?.page ?? 0;
    this.pageSize = event?.rows ?? this.pageSize;
    this.loadPaymentData();
  }

  private paymentPdfQuery(): ReportPdfQuery {
    const countryEntries: [string, any][] = [];
    if (this.selectedCountryIds.length === 1) {
      countryEntries.push(['countryId', this.selectedCountryIds[0]]);
    } else if (this.selectedCountryIds.length > 1) {
      countryEntries.push(['countryIds', this.selectedCountryIds]);
    } else {
      countryEntries.push(['countryId', this.reportCountryScope.countryIdParam()]);
    }
    return buildReportPdfQuery([
      ...countryEntries,
      ['sortBy', 'paymentDate'],
      ['sortDir', 'DESC'],
      ['status', this.selectedStatus ?? undefined],
      ['type', this.selectedType ?? undefined],
      ['method', this.selectedMethod ?? undefined],
      ['quarter', this.selectedQuarter ?? undefined],
      ['year', this.selectedYear ?? undefined],
      ['search', this.searchText?.trim() || undefined],
      ['startDate', this.startDate ? this.toDateParam(this.startDate) : undefined],
      ['endDate', this.endDate ? this.toDateParam(this.endDate) : undefined]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/financial/payment-summary/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.paymentPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.paymentPdfQuery(), lang: this.selectedLang };

  }

  /** Tag colour from STUDY_FIN_RECEIVED_STATUS.UI_SEVERITY (V078), by the row's status code. */
  getStatusSeverity(row: PaymentSummary): PrimeSeverity {
    return this.codeLists.severity('fin-received-status', row.paymentStatusCode);
  }

  /**
   * The amount in the currency recorded on the row; a plain number when none is
   * recorded (no currency is assumed), and "amount name" when the recorded
   * currency is a name Intl does not know.
   */
  formatCurrency(amount: number | string | null | undefined, currency?: string | null): string {
    const value = this.toNumber(amount);
    if (!currency) {
      return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value);
    }
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);
    } catch {
      return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)} ${currency}`;
    }
  }

  formatDate(dateString: string): string {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString();
  }

  private toDateParam(date: Date): string {
    return date.toISOString().split('T')[0];
  }

  private toNumber(value: number | string | null | undefined): number {
    if (value === null || value === undefined) return 0;
    return typeof value === 'number' ? value : Number(value);
  }

  /** An amount for the cards; a missing or unreadable one counts as 0. */
  private finite(value: number | string | null | undefined): number {
    const amount = this.toNumber(value);
    return Number.isFinite(amount) ? amount : 0;
  }

  private toStringOptions(options: LookupOptionResponse[] | undefined): DropdownOption<string>[] {
    return (options ?? [])
      .filter((option) => !!option?.name)
      .map((option) => ({ label: option.name, value: option.name }))
      .sort((a, b) => a.label.localeCompare(b.label));
  }

  private toYearOptions(options: LookupOptionResponse[] | undefined): DropdownOption<number>[] {
    return (options ?? [])
      .map((option) => this.toYearOption(option))
      .filter((option): option is DropdownOption<number> => option !== null)
      .sort((a, b) => b.value - a.value);
  }

  private toYearOption(option: LookupOptionResponse): DropdownOption<number> | null {
    if (!option || !option.name) return null;
    const parsed = this.toNumber(option.name);
    if (isNaN(parsed)) return null;
    return { label: option.name, value: parsed };
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

