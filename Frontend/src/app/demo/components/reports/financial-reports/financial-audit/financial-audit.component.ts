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

interface FinancialAuditMetadataResponse {
  statuses: LookupOptionResponse[];
  categories: LookupOptionResponse[];
  banks: LookupOptionResponse[];
  auditors: LookupOptionResponse[];
  currencies: LookupOptionResponse[];
  years: LookupOptionResponse[];
  quarters: LookupOptionResponse[];
}

interface FinancialAuditMetadata {
  statuses: DropdownOption<string>[];
  categories: DropdownOption<string>[];
  banks: DropdownOption<string>[];
  auditors: DropdownOption<string>[];
  currencies: DropdownOption<string>[];
  years: DropdownOption<number>[];
  quarters: DropdownOption<number>[];
}

interface FinancialAuditResponse {
  content: FinancialAudit[];
  totalElements: number;
  number: number;
  size: number;
}

interface FinancialAudit {
  auditId: number | null;
  auditDate: string | null;
  auditCategory: string | null;
  auditAmount: number | string | null;
  auditCurrency: string | null;
  auditStatus: string | null;
  auditorName: string | null;
  /** English beside the Arabic (V124, W3-06); shown with `inLanguage`. */
  auditCategoryEn?: string | null;
  auditCurrencyEn?: string | null;
  auditStatusEn?: string | null;
  auditorNameEn?: string | null;
  auditNotes: string | null;
  studentName: string | null;
  studentPassport: string | null;
  bankName: string | null;
  fiscalYear: number | null;
  fiscalQuarter: number | null;
}

@Component({
    selector: 'app-financial-audit',
    templateUrl: './financial-audit.component.html',
    styleUrls: ['./financial-audit.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class FinancialAuditComponent implements OnInit, OnDestroy {

  auditRecords: FinancialAudit[] = [];
  loading: boolean = false;
  auditSummary = {
    totalRecords: 0,
    verified: 0,
    pending: 0,
    discrepancies: 0
  };

  metadata: FinancialAuditMetadata = {
    statuses: [],
    categories: [],
    banks: [],
    auditors: [],
    currencies: [],
    years: [],
    quarters: []
  };

  selectedStatus: string | null = null;
  selectedCategory: string | null = null;
  selectedBank: string | null = null;
  selectedAuditor: string | null = null;
  selectedCurrency: string | null = null;
  selectedYear: number | null = null;
  selectedQuarter: number | null = null;
  dateRange: Date[] | null = null;
  searchTerm: string = '';

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];

  totalRecords: number = 0;
  rows: number = 20;
  page: number = 0;
  /** Bound to the table, so a filter that reloads page one moves the paginator there too (QA B8, N9). */
  first: number = 0;

  private readonly sortBy = 'auditDate';
  private readonly sortDir: 'ASC' | 'DESC' = 'DESC';
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
    this.loadAuditData();
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
    this.http.get<FinancialAuditMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/financial/financial-audit/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          statuses: this.toDropdownOptions(data.statuses),
          categories: this.toDropdownOptions(data.categories),
          banks: this.toDropdownOptions(data.banks),
          auditors: this.toDropdownOptions(data.auditors),
          currencies: this.toDropdownOptions(data.currencies),
          years: this.toDropdownNumberOptions(data.years),
          quarters: this.toDropdownNumberOptions(data.quarters)
        };
        this.loadAuditData();
      },
      error: (error) => {
        console.error('Error loading financial audit metadata:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.REPORT_SUBPAGES.METADATA_LOAD_FAILED')
        });
        this.loadAuditData();
      }
    });
  }

  loadAuditData(): void {
    this.loading = true;

    let params = this.buildHttpParams();
    if (this.selectedStatus) params = params.set('status', this.selectedStatus);
    if (this.selectedCategory) params = params.set('category', this.selectedCategory);
    if (this.selectedYear !== null) params = params.set('year', this.selectedYear.toString());
    if (this.selectedQuarter !== null) params = params.set('quarter', this.selectedQuarter.toString());
    if (this.selectedBank) params = params.set('bank', this.selectedBank);
    if (this.selectedAuditor) params = params.set('auditor', this.selectedAuditor);
    if (this.selectedCurrency) params = params.set('currency', this.selectedCurrency);
    if (this.dateRange?.[0]) params = params.set('startDate', this.toIsoDate(this.dateRange[0]));
    if (this.dateRange?.[1]) params = params.set('endDate', this.toIsoDate(this.dateRange[1]));
    if (this.searchTerm?.trim()) params = params.set('search', this.searchTerm.trim());
    params = params.set('page', this.page.toString());
    params = params.set('size', this.rows.toString());
    params = params.set('sortBy', this.sortBy);
    params = params.set('sortDir', this.sortDir);

    const url = `${environment.apiUrl}/mohe/api/reports/financial/financial-audit/`;

    this.http.get<FinancialAuditResponse>(url, { params }).subscribe({
      next: (response) => {
        this.auditRecords = (response.content ?? []).map((item) => this.normalizeAuditRecord(item));
        this.totalRecords = response.totalElements ?? this.auditRecords.length;
        this.page = response.number ?? 0;
        this.rows = response.size ?? this.rows;
        this.first = firstOf(this.page, this.rows);
        this.calculateSummary();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading financial audit data:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.REPORT_SUBPAGES.NO_DATA_FOUND')
        });
        this.auditRecords = [];
        this.totalRecords = 0;
        this.auditSummary = { totalRecords: 0, verified: 0, pending: 0, discrepancies: 0 };
        this.loading = false;
      }
    });
  }

  calculateSummary(): void {
    this.auditSummary.totalRecords = this.auditRecords.length;

    const counts = { verified: 0, pending: 0, discrepancies: 0 };

    this.auditRecords.forEach(record => {
      // The English wording is what the words below match (W3-06).
      const status = (record.auditStatusEn || record.auditStatus || '').toLowerCase();
      if (['verified', 'approved', 'completed', 'paid', 'received'].some(s => status.includes(s))) {
        counts.verified += 1;
      } else if (['pending', 'process', 'await', 'in progress'].some(s => status.includes(s))) {
        counts.pending += 1;
      } else if (['discrepancy', 'issue', 'error', 'reject', 'fail'].some(s => status.includes(s))) {
        counts.discrepancies += 1;
      } else {
        counts.pending += 1;
      }
    });

    this.auditSummary.verified = counts.verified;
    this.auditSummary.pending = counts.pending;
    this.auditSummary.discrepancies = counts.discrepancies;
  }

  onPageChange(event: any): void {
    followPage(this, event);
    this.loadAuditData();
  }

  onFiltersChanged(): void {
    resetPaging(this);
    this.loadAuditData();
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
    this.selectedStatus = null;
    this.selectedCategory = null;
    this.selectedBank = null;
    this.selectedAuditor = null;
    this.selectedCurrency = null;
    this.selectedYear = null;
    this.selectedQuarter = null;
    this.dateRange = null;
    this.searchTerm = '';
    this.selectedCountryIds = [];
    this.onFiltersChanged();
  }

  private auditPdfQuery(): ReportPdfQuery {
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
      ['sortDir', this.sortDir],
      ['status', this.selectedStatus ?? undefined],
      ['category', this.selectedCategory ?? undefined],
      ['year', this.selectedYear ?? undefined],
      ['quarter', this.selectedQuarter ?? undefined],
      ['bank', this.selectedBank ?? undefined],
      ['auditor', this.selectedAuditor ?? undefined],
      ['currency', this.selectedCurrency ?? undefined],
      ['startDate', this.dateRange?.[0] ? this.toIsoDate(this.dateRange[0]) : undefined],
      ['endDate', this.dateRange?.[1] ? this.toIsoDate(this.dateRange[1]) : undefined],
      ['search', this.searchTerm?.trim() || undefined]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/financial/financial-audit/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.auditPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.auditPdfQuery(), lang: this.selectedLang };

  }

  getStatusSeverity(status: string): string {
    const value = (status ?? '').toLowerCase();
    if (['verified', 'approved', 'completed', 'paid', 'received'].some(s => value.includes(s))) {
      return 'success';
    }
    if (['pending', 'process', 'await', 'progress'].some(s => value.includes(s))) {
      return 'warning';
    }
    if (['discrepancy', 'issue', 'error', 'reject', 'fail'].some(s => value.includes(s))) {
      return 'danger';
    }
    return 'info';
  }

  formatCurrency(amount: number | string | null | undefined, currency?: string | null): string {
    const value = this.toNumber(amount);
    // The currency now comes from STUDY_CURRENCY_IN, which stores names such as
    // "US Dollar" rather than ISO codes, and Intl throws a RangeError on
    // anything that is not a three-letter code.
    const currencyCode = this.isoCurrencyCode(currency)
      ?? this.isoCurrencyCode(this.selectedCurrency)
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

  private normalizeAuditRecord(item: FinancialAudit): FinancialAudit {
    return {
      auditId: item.auditId,
      auditDate: item.auditDate,
      auditCategory: item.auditCategory,
      auditAmount: this.toNumber(item.auditAmount),
      // Left as the record has it: substituting 'USD' here would put back the
      // assumption that was just removed from the query.
      auditCurrency: item.auditCurrency,
      auditStatus: item.auditStatus,
      auditorName: item.auditorName,
      auditCategoryEn: item.auditCategoryEn,
      auditCurrencyEn: item.auditCurrencyEn,
      auditStatusEn: item.auditStatusEn,
      auditorNameEn: item.auditorNameEn,
      auditNotes: item.auditNotes,
      studentName: item.studentName,
      studentPassport: item.studentPassport,
      bankName: item.bankName,
      fiscalYear: item.fiscalYear,
      fiscalQuarter: item.fiscalQuarter
    };
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

  private toDropdownNumberOptions(items: LookupOptionResponse[] | null | undefined): DropdownOption<number>[] {
    if (!items) {
      return [];
    }
    return items
      .filter((item) => item && item.name && item.name.trim().length > 0)
      .map((item) => ({
        label: item.name as string,
        value: item.id as number
      }))
      .sort((a, b) => (a.value ?? 0) - (b.value ?? 0));
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

