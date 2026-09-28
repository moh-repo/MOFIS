import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { environment } from 'src/environments/environment';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { firstOf, followPage, resetPaging } from '../../../../service/table-paging.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface LookupOptionResponse {
  id: number | null;
  name: string | null;
}

interface DropdownOption<T> {
  label: string;
  value: T;
}

interface BankTransactionMetadataResponse {
  transactionTypes: LookupOptionResponse[];
  banks: LookupOptionResponse[];
  bankBranches: LookupOptionResponse[];
  currencies: LookupOptionResponse[];
}

interface BankTransactionMetadata {
  types: DropdownOption<string>[];
  banks: DropdownOption<string>[];
  branches: DropdownOption<string>[];
  currencies: DropdownOption<string>[];
}

interface BankTransactionResponse {
  content: BankTransaction[];
  totalElements: number;
  number: number;
  size: number;
}

interface BankTransaction {
  transactionId: number | null;
  transactionDate: string | null;
  transactionType: string | null;
  /** English beside the Arabic (V124, W3-06); shown with `inLanguage`. */
  transactionTypeEn?: string | null;
  transactionDirection: string | null;
  transactionDescription: string | null;
  transactionAmount: number | string | null;
  transactionCurrency: string | null;
  referenceNumber: string | null;
  bankName: string | null;
  bankBranch: string | null;
  beneficiaryName: string | null;
  beneficiaryAccount: string | null;
}

@Component({
    selector: 'app-bank-transaction',
    templateUrl: './bank-transaction.component.html',
    styleUrls: ['./bank-transaction.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class BankTransactionComponent implements OnInit, OnDestroy {

  transactions: BankTransaction[] = [];
  loading: boolean = false;
  totalDebit: number = 0;
  totalCredit: number = 0;
  totalRecords: number = 0;
  rows: number = 20;
  page: number = 0;
  /** Bound to the table, so a filter that reloads page one moves the paginator there too (QA B8, N9). */
  first: number = 0;

  metadata: BankTransactionMetadata = {
    types: [],
    banks: [],
    branches: [],
    currencies: []
  };

  selectedType: string | null = null;
  selectedBank: string | null = null;
  selectedBranch: string | null = null;
  selectedCurrency: string | null = null;
  dateRange: Date[] | null = null;
  searchTerm: string = '';
  displayCurrency: string | null = null;

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];

  private readonly sortBy: string = 'transactionDate';
  private readonly sortDirection: 'ASC' | 'DESC' = 'DESC';
  private readonly destroy$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public translationService: TranslationService,
    private reportCountryScope: ReportCountryScopeService,
    private reportPdfService: ReportPdfService,
        private lookupService: LookupService) { }

  get hasMultiCountry(): boolean {
    return this.reportCountryScope.hasMultiCountryAccess();
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

  private buildHttpParams(): HttpParams {
    if (this.selectedCountryIds.length > 0) {
      return this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds);
    }
    return this.reportCountryScope.appendCountryParam();
  }

  onCountryChange(): void {
    resetPaging(this);
    this.loadMetadata();
    this.loadTransactionData();
  }

  ngOnInit(): void {
    if (this.hasMultiCountry) { this.loadCountries(); }
    this.loadMetadata();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      resetPaging(this);
      this.loadMetadata();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadMetadata(): void {
    this.http.get<BankTransactionMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/financial/bank-transaction/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          types: this.toDropdownOptions(data.transactionTypes),
          banks: this.toDropdownOptions(data.banks),
          branches: this.toDropdownOptions(data.bankBranches),
          currencies: this.toDropdownOptions(data.currencies)
        };
        this.loadTransactionData();
      },
      error: (error) => {
        console.error('Error loading bank transaction metadata:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.REPORT_SUBPAGES.METADATA_LOAD_FAILED')
        });
        this.loadTransactionData();
      }
    });
  }

  loadTransactionData(): void {
    this.loading = true;

    let params = this.buildHttpParams();
    if (this.selectedType) params = params.set('type', this.selectedType);
    if (this.selectedBank) params = params.set('bankName', this.selectedBank);
    if (this.selectedBranch) params = params.set('branchName', this.selectedBranch);
    if (this.selectedCurrency) params = params.set('currency', this.selectedCurrency);
    if (this.dateRange?.[0]) params = params.set('startDate', this.toIsoDate(this.dateRange[0]));
    if (this.dateRange?.[1]) params = params.set('endDate', this.toIsoDate(this.dateRange[1]));
    if (this.searchTerm?.trim()) params = params.set('search', this.searchTerm.trim());
    params = params.set('page', this.page.toString());
    params = params.set('size', this.rows.toString());
    params = params.set('sortBy', this.sortBy);
    params = params.set('sortDir', this.sortDirection);

    const url = `${environment.apiUrl}/mohe/api/reports/financial/bank-transaction/`;

    this.http.get<BankTransactionResponse>(url, { params }).subscribe({
      next: (response) => {
        this.transactions = (response.content ?? []).map((item) => this.normalizeTransaction(item));
        this.totalRecords = response.totalElements ?? this.transactions.length;
        this.page = response.number ?? 0;
        this.rows = response.size ?? this.rows;
        this.first = firstOf(this.page, this.rows);
        this.calculateTotals();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading bank transactions:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.REPORT_SUBPAGES.NO_DATA_FOUND')
        });
        this.transactions = [];
        this.totalRecords = 0;
        this.totalDebit = 0;
        this.totalCredit = 0;
        this.displayCurrency = null;
        this.loading = false;
      }
    });
  }

  private normalizeTransaction(item: BankTransaction): BankTransaction {
    return {
      transactionId: item.transactionId,
      transactionDate: item.transactionDate,
      transactionType: item.transactionType,
      transactionTypeEn: item.transactionTypeEn,
      transactionDirection: item.transactionDirection,
      transactionDescription: item.transactionDescription,
      transactionAmount: this.toNumber(item.transactionAmount),
      transactionCurrency: item.transactionCurrency,
      referenceNumber: item.referenceNumber,
      bankName: item.bankName,
      bankBranch: item.bankBranch,
      beneficiaryName: item.beneficiaryName,
      beneficiaryAccount: item.beneficiaryAccount
    };
  }

  private calculateTotals(): void {
    this.totalDebit = this.transactions
      .filter((t) => (t.transactionDirection ?? '').toLowerCase() === 'debit')
      .reduce((sum, t) => sum + this.toNumber(t.transactionAmount), 0);

    this.totalCredit = this.transactions
      .filter((t) => (t.transactionDirection ?? '').toLowerCase() === 'credit')
      .reduce((sum, t) => sum + this.toNumber(t.transactionAmount), 0);

    const currency = this.transactions.find((t) => t.transactionCurrency)?.transactionCurrency ?? null;
    this.displayCurrency = currency;
  }

  onPageChange(event: any): void {
    followPage(this, event);
    this.loadTransactionData();
  }

  onFiltersChanged(): void {
    resetPaging(this);
    this.loadTransactionData();
  }

  onDateRangeSelected(): void {
    if (this.dateRange && this.dateRange[0] && this.dateRange[1]) {
      this.onFiltersChanged();
    }
  }

  onSearch(): void {
    this.onFiltersChanged();
  }

  clearFilters(): void {
    this.selectedType = null;
    this.selectedBank = null;
    this.selectedBranch = null;
    this.selectedCurrency = null;
    this.dateRange = null;
    this.searchTerm = '';
    this.selectedCountryIds = [];
    this.onFiltersChanged();
  }

  private bankPdfQuery(): ReportPdfQuery {
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
      ['sortBy', this.sortBy],
      ['sortDir', this.sortDirection],
      ['type', this.selectedType ?? undefined],
      ['bankName', this.selectedBank ?? undefined],
      ['branchName', this.selectedBranch ?? undefined],
      ['currency', this.selectedCurrency ?? undefined],
      ['startDate', this.dateRange?.[0] ? this.toIsoDate(this.dateRange[0]) : undefined],
      ['endDate', this.dateRange?.[1] ? this.toIsoDate(this.dateRange[1]) : undefined],
      ['search', this.searchTerm?.trim() || undefined]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/financial/bank-transaction/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.bankPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.bankPdfQuery(), lang: this.selectedLang };

  }

  formatCurrency(amount: number | null | undefined, currency?: string | null): string {
    const value = this.toNumber(amount);
    // The currency now comes from STUDY_CURRENCY_IN, which stores names such as
    // "US Dollar" or "الدولار الأمريكي" rather than ISO codes. Intl throws a
    // RangeError on anything that is not a three-letter code, so only a real one
    // is passed through and the symbol falls back otherwise.
    const currencyCode = this.isoCurrencyCode(currency)
      ?? this.isoCurrencyCode(this.displayCurrency)
      ?? 'USD';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: currencyCode }).format(value);
  }

  private isoCurrencyCode(value: string | null | undefined): string | null {
    const trimmed = (value ?? '').trim();
    return /^[A-Za-z]{3}$/.test(trimmed) ? trimmed.toUpperCase() : null;
  }

  formatDate(dateString: string | null | undefined): string {
    if (!dateString) {
      return this.translationService.translate('REPORTS.REPORT_SUBPAGES.UNKNOWN');
    }
    return new Date(dateString).toLocaleDateString();
  }

  getTypeSeverity(transaction: BankTransaction): string {
    const direction = transaction?.transactionDirection;
    if (!direction) {
      return 'info';
    }
    const lower = direction.toLowerCase();
    if (lower === 'credit') {
      return 'success';
    }
    if (lower === 'debit') {
      return 'danger';
    }
    return 'info';
  }

  private toDropdownOptions(items: LookupOptionResponse[] | null | undefined): DropdownOption<string>[] {
    if (!items) {
      return [];
    }

    return items
      .filter((item) => item && item.name && item.name.trim().length > 0)
      .map((item) => ({
        label: item.name as string,
        value: item.name as string
      }))
      .sort((a, b) => a.label.localeCompare(b.label, undefined, { sensitivity: 'base' }));
  }

  private toIsoDate(date: Date): string {
    const tzOffset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - tzOffset).toISOString().split('T')[0];
  }

  private toNumber(value: number | string | null | undefined): number {
    if (value === null || value === undefined) {
      return 0;
    }
    if (typeof value === 'number') {
      return value;
    }
    const parsed = Number(value);
    return isNaN(parsed) ? 0 : parsed;
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

