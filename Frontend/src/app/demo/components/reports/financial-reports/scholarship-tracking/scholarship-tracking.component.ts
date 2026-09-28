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
import { CodeListService, PrimeSeverity } from 'src/app/demo/service/code-list.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface ScholarshipTracking {
  scholarshipId: number;
  studentId: number;
  studentName: string;
  studentPassportNumber: string;
  scholarshipType: string;
  scholarshipProvider: string;
  scholarshipAmount: number;
  scholarshipCurrency: string;
  allocatedDate: string;
  startDate: string;
  endDate: string;
  amountPaid: number;
  scholarshipStatus: string | null;
  /** STUDENT_STUDY_STATUS.CODE - the key the tag is coloured by. */
  scholarshipStatusCode: string | null;
  universityName: string;
  majorName: string;
}

interface LookupOptionResponse {
  id: number | null;
  name: string;
}

interface DropdownOption<T = string> {
  label: string;
  value: T;
}

interface ScholarshipMetadataResponse {
  types: LookupOptionResponse[];
  providers: LookupOptionResponse[];
  statuses: LookupOptionResponse[];
}

interface ScholarshipMetadata {
  types: DropdownOption<string>[];
  providers: DropdownOption<string>[];
  statuses: DropdownOption<string>[];
}

interface ScholarshipTrackingPage {
  content: ScholarshipTracking[];
  totalElements: number;
  size: number;
}

@Component({
    selector: 'app-scholarship-tracking',
    templateUrl: './scholarship-tracking.component.html',
    styleUrls: ['./scholarship-tracking.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class ScholarshipTrackingComponent implements OnInit, OnDestroy {

  scholarshipData: ScholarshipTracking[] = [];
  metadata: ScholarshipMetadata = {
    types: [],
    providers: [],
    statuses: []
  };

  // Filter options
  selectedType: string | null = null;
  selectedProvider: string | null = null;
  selectedStatus: string | null = null;
  searchText: string = '';

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];

  // Pagination
  currentPage: number = 0;
  pageSize: number = 20;
  totalRecords: number = 0;

  // Summary statistics
  totalScholarships: number = 0;
  totalAllocated: number = 0;
  totalPaid: number = 0;

  loading: boolean = false;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public translationService: TranslationService,
    private reportCountryScope: ReportCountryScopeService,
    private reportPdfService: ReportPdfService,
        private lookupService: LookupService,
        private codeLists: CodeListService) { }

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
    this.loadMetadata();
    this.loadScholarshipData();
  }

  ngOnInit(): void {
    if (this.hasMultiCountry) { this.loadCountries(); }
    this.loadMetadata();
    this.loadScholarshipData();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
      this.loadScholarshipData();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadMetadata(): void {
    this.http.get<ScholarshipMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/financial/scholarship-tracking/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          types: this.toDropdownOptions(data.types),
          providers: this.toDropdownOptions(data.providers),
          statuses: this.toDropdownOptions(data.statuses)
        };
      },
      error: (error) => {
        console.error('Error loading scholarship metadata:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.REPORT_SUBPAGES.METADATA_LOAD_FAILED')
        });
      }
    });
  }

  loadScholarshipData(): void {
    this.loading = true;

    let params = this.buildHttpParams();
    params = params.set('page', this.currentPage.toString());
    params = params.set('size', this.pageSize.toString());
    if (this.selectedType) params = params.set('type', this.selectedType);
    if (this.selectedProvider) params = params.set('provider', this.selectedProvider);
    if (this.selectedStatus) params = params.set('status', this.selectedStatus);
    if (this.searchText) params = params.set('search', this.searchText.trim());

    const url = `${environment.apiUrl}/mohe/api/reports/financial/scholarship-tracking/`;

    this.http.get<ScholarshipTrackingPage>(url, { params }).subscribe({
      next: (response) => {
        this.scholarshipData = response?.content ?? [];
        this.totalRecords = response?.totalElements ?? 0;
        this.pageSize = response?.size ?? this.pageSize;
        this.calculateSummary();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading scholarship data:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('REPORTS.REPORT_SUBPAGES.NO_SCHOLARSHIP_RECORDS')
        });
        this.scholarshipData = [];
        this.totalRecords = 0;
        this.calculateSummary();
        this.loading = false;
      }
    });
  }

  calculateSummary(): void {
    const data = this.scholarshipData ?? [];
    this.totalScholarships = this.totalRecords;
    this.totalAllocated = data.reduce((sum, s) => sum + this.toNumber(s.scholarshipAmount), 0);
    this.totalPaid = data.reduce((sum, s) => sum + this.toNumber(s.amountPaid), 0);
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadScholarshipData();
  }

  clearFilters(): void {
    this.selectedType = null;
    this.selectedProvider = null;
    this.selectedStatus = null;
    this.searchText = '';
    this.currentPage = 0;
    this.selectedCountryIds = [];
    this.loadScholarshipData();
  }

  onPageChange(event: any): void {
    this.currentPage = event.page;
    this.pageSize = event.rows;
    this.loadScholarshipData();
  }

  private scholarshipPdfQuery(): ReportPdfQuery {
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
      ['sortBy', 'startDate'],
      ['sortDir', 'DESC'],
      ['type', this.selectedType ?? undefined],
      ['provider', this.selectedProvider ?? undefined],
      ['status', this.selectedStatus ?? undefined],
      ['search', this.searchText?.trim() || undefined]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/financial/scholarship-tracking/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.scholarshipPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.scholarshipPdfQuery(), lang: this.selectedLang };

  }

  /** Tag colour from STUDENT_STUDY_STATUS.UI_SEVERITY (V078), by the row's status code. */
  getStatusSeverity(row: ScholarshipTracking): PrimeSeverity {
    return this.codeLists.severity('study-status', row.scholarshipStatusCode);
  }

  getProgressPercentage(paid: number, total: number): number {
    const paidAmount = this.toNumber(paid);
    const totalAmount = this.toNumber(total);
    return totalAmount > 0 ? Math.round((paidAmount / totalAmount) * 100) : 0;
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
    const date = new Date(dateString);
    return date.toLocaleDateString();
  }

  private toDropdownOptions(options: LookupOptionResponse[] | undefined): DropdownOption<string>[] {
    return (options ?? [])
      .filter((option) => !!option?.name)
      .map((option) => ({
        label: option.name,
        value: option.name
      }))
      .sort((a, b) => a.label.localeCompare(b.label));
  }

  private toNumber(value: number | string | null | undefined): number {
    if (value === null || value === undefined) {
      return 0;
    }
    return typeof value === 'number' ? value : Number(value);
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

