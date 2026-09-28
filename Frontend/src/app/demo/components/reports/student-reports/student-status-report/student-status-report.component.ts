import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface StudentStatusReport {
  studentStudyStatusId: number;
  studentStudyStatusName: string;
  studentCountryId: number;
  studentCountryName: string;
  studentMajorId: number;
  studentMajorName: string;
  studentCount: number;
  statusCategory: string;
}

interface LookupOptionResponse {
  id: number;
  name: string;
}

interface StatusMetadataResponse {
  statuses: LookupOptionResponse[];
  countries: LookupOptionResponse[];
  majors: LookupOptionResponse[];
}

interface DropdownOption {
  label: string;
  value: number;
}

interface StatusMetadata {
  statuses: DropdownOption[];
  countries: DropdownOption[];
  majors: DropdownOption[];
}

/** One card in the status distribution block, per status category. */
interface StatusDistributionEntry {
  label: string;
  value: number;
  severity: string;
}

@Component({
    selector: 'app-student-status-report',
    templateUrl: './student-status-report.component.html',
    styleUrls: ['./student-status-report.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentStatusReportComponent implements OnInit, OnDestroy {

  statusData: StudentStatusReport[] = [];
  metadata: StatusMetadata = {
    statuses: [],
    countries: [],
    majors: []
  };

  // Filter options
  selectedStatus: number | null = null;
  selectedMajor: number | null = null;

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];

  // Display options
  groupBy: string = 'status';
  showChart: boolean = false;
  groupByOptions: any[] = [];
  private readonly destroy$ = new Subject<void>();

  loading: boolean = false;
  totalStudents: number = 0;

  /**
   * Computed once per load, not by a method in the template. An `@for` over a
   * method that builds fresh objects on every call is tracked by identity, so on
   * the dev-mode verification pass every card was "new": the repeater rebuilt
   * them after the parent had been checked and threw NG0100 on every tick (QA M3).
   */
  statusDistribution: StatusDistributionEntry[] = [];

  // Jasper PDF viewer
  reportLoading = false;
  pdfApiPath = '/api/reports/student-status/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public translationService: TranslationService,
    private reportPdfService: ReportPdfService,
    private reportCountryScope: ReportCountryScopeService
  ,
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

  initializeGroupByOptions(): void {
    this.lookupService.getReportDimensions('STUDENT_STATUS_GROUPBY')
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: opts => { this.groupByOptions = opts; },
        error: () => { this.groupByOptions = []; }
      });
  }

  ngOnInit(): void {
    this.initializeGroupByOptions();
    if (this.hasMultiCountry) { this.loadCountries(); }
    this.loadMetadata();
    this.loadStatusData();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
      this.loadStatusData();
    });
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.initializeGroupByOptions();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadMetadata(): void {
    this.http.get<StatusMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/student-status/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          statuses: (data.statuses || []).map(option => ({
            label: option.name,
            value: option.id
          })),
          countries: this.reportCountryScope.filterToAllowedStudyCountries(data.countries || []).map(option => ({
            label: option.name,
            value: option.id
          })),
          majors: (data.majors || []).map(option => ({
            label: option.name,
            value: option.id
          }))
        };
      },
      error: (error) => {
        console.error('Error loading metadata:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
      }
    });
  }

  onCountryChange(): void {
    this.loadMetadata();
    this.loadStatusData();
  }

  loadStatusData(): void {
    this.loading = true;
    let params = this.buildHttpParams();
    if (this.selectedStatus !== null) params = params.set('studyStatusId', String(this.selectedStatus));
    if (this.selectedMajor !== null) params = params.set('majorId', String(this.selectedMajor));

    this.http.get<StudentStatusReport[]>(`${environment.apiUrl}/mohe/api/reports/student-status/search`, { params }).subscribe({
      next: (data) => {
        this.statusData = data;
        this.calculateTotalStudents();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading status data:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
        this.loading = false;
      }
    });
  }

  calculateTotalStudents(): void {
    this.totalStudents = this.statusData.reduce((sum, item) => sum + item.studentCount, 0);
    this.statusDistribution = this.buildStatusDistribution();
  }

  onFilterChange(): void {
    this.loadStatusData();
  }

  clearFilters(): void {
    this.selectedStatus = null;
    this.selectedMajor = null;
    this.selectedCountryIds = [];
    this.loadMetadata();
    this.loadStatusData();
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.statusPdfQuery(), lang: this.selectedLang };

  }

  generateReport(): void {
      this.pdfQuery = { ...this.statusPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  private statusPdfQuery(): ReportPdfQuery {
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
      ['studyStatusId', this.selectedStatus],
      ['majorId', this.selectedMajor]
    ]);
  }

  getGroupedData(): any[] {
    const grouped = this.statusData.reduce((acc, item) => {
      let key = '';
      switch (this.groupBy) {
        case 'status':
          key = item.studentStudyStatusName;
          break;
        case 'country':
          key = item.studentCountryName;
          break;
        case 'major':
          key = item.studentMajorName;
          break;
        case 'category':
          key = item.statusCategory;
          break;
      }
      
      if (!acc[key]) {
        acc[key] = 0;
      }
      acc[key] += item.studentCount;
      return acc;
    }, {} as any);

    return Object.entries(grouped).map(([key, value]) => ({
      label: key,
      value: value
    }));
  }

  getStatusSeverity(status: string): string {
    if (status?.toLowerCase().includes('graduated') || status?.toLowerCase().includes('متخرج')) {
      return 'success';
    } else if (status?.toLowerCase().includes('active') || status?.toLowerCase().includes('نشط')) {
      return 'info';
    } else if (status?.toLowerCase().includes('suspended') || status?.toLowerCase().includes('موقوف')) {
      return 'warning';
    } else if (status?.toLowerCase().includes('withdrawn') || status?.toLowerCase().includes('منسحب')) {
      return 'danger';
    }
    return 'secondary';
  }

  getCategorySeverity(category: string): string {
    switch (category?.toLowerCase()) {
      case 'active':
        return 'info';
      case 'graduated':
        return 'success';
      case 'suspended':
        return 'warning';
      case 'withdrawn':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  private buildStatusDistribution(): StatusDistributionEntry[] {
    const distribution = this.statusData.reduce((acc, item) => {
      acc[item.statusCategory] = (acc[item.statusCategory] ?? 0) + item.studentCount;
      return acc;
    }, {} as Record<string, number>);

    return Object.entries(distribution).map(([category, count]) => ({
      label: category,
      value: count,
      severity: this.getCategorySeverity(category)
    }));
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
