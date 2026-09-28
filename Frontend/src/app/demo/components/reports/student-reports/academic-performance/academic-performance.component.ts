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

interface AcademicPerformance {
  studentId: number;
  studentName: string;
  studentMajorId: number;
  studentMajorName: string;
  studentCurrentLevelId: number;
  studentCurrentLevelName: string;
  studentStudyNoOfYears: number;
  studentStudyStatusId: number;
  studentStudyStatusName: string;
  studentDegreeId: number;
  studentDegreeName: string;
  studentCountryId: number;
  studentCountryName: string;
  yearsEnrolled: number;
}

interface LookupOptionResponse {
  id: number;
  name: string;
}

interface PerformanceMetadataResponse {
  majors: LookupOptionResponse[];
  levels: LookupOptionResponse[];
  statuses: LookupOptionResponse[];
  degrees: LookupOptionResponse[];
  countries: LookupOptionResponse[];
}

interface DropdownOption {
  label: string;
  value: number;
}

interface PerformanceMetadata {
  majors: DropdownOption[];
  levels: DropdownOption[];
  statuses: DropdownOption[];
  degrees: DropdownOption[];
  countries: DropdownOption[];
}

@Component({
    selector: 'app-academic-performance',
    templateUrl: './academic-performance.component.html',
    styleUrls: ['./academic-performance.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class AcademicPerformanceComponent implements OnInit, OnDestroy {

  performanceData: AcademicPerformance[] = [];
  metadata: PerformanceMetadata = {
    majors: [],
    levels: [],
    statuses: [],
    degrees: [],
    countries: []
  };

  // Filter options
  selectedMajor: number | null = null;
  selectedLevel: number | null = null;
  selectedStatus: number | null = null;
  selectedDegree: number | null = null;

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];

  // Display options
  groupBy: string = 'major';
  showChart: boolean = false;
  groupByOptions: any[] = [];
  private readonly destroy$ = new Subject<void>();

  loading: boolean = false;
  totalStudents: number = 0;
  averageYearsEnrolled: number = 0;

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
    this.lookupService.getReportDimensions('ACADEMIC_PERFORMANCE_GROUPBY')
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
    this.loadPerformanceData();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
      this.loadPerformanceData();
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
    this.http.get<PerformanceMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/academic-performance/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          majors: (data.majors || []).map(option => this.toDropdownOption(option)),
          levels: (data.levels || []).map(option => this.toDropdownOption(option)),
          statuses: (data.statuses || []).map(option => this.toDropdownOption(option)),
          degrees: (data.degrees || []).map(option => this.toDropdownOption(option)),
          countries: this.reportCountryScope.filterToAllowedStudyCountries(data.countries || []).map(option => this.toDropdownOption(option))
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
    this.loadPerformanceData();
  }

  loadPerformanceData(): void {
    this.loading = true;
    let params = this.buildHttpParams();
    if (this.selectedMajor !== null) params = params.set('majorId', String(this.selectedMajor));
    if (this.selectedLevel !== null) params = params.set('levelId', String(this.selectedLevel));
    if (this.selectedStatus !== null) params = params.set('studyStatusId', String(this.selectedStatus));
    if (this.selectedDegree !== null) params = params.set('degreeId', String(this.selectedDegree));

    this.http.get<AcademicPerformance[]>(`${environment.apiUrl}/mohe/api/reports/academic-performance/search`, { params }).subscribe({
      next: (data) => {
        this.performanceData = data;
        this.calculateStatistics();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading performance data:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
        this.loading = false;
      }
    });
  }

  calculateStatistics(): void {
    this.totalStudents = this.performanceData.length;

    const totalYears = this.performanceData.reduce((sum, s) => sum + (s.yearsEnrolled ?? 0), 0);
    this.averageYearsEnrolled = this.totalStudents > 0 ? totalYears / this.totalStudents : 0;
  }

  onFilterChange(): void {
    this.loadPerformanceData();
  }

  clearFilters(): void {
    this.selectedMajor = null;
    this.selectedLevel = null;
    this.selectedStatus = null;
    this.selectedDegree = null;
    this.selectedCountryIds = [];
    this.loadMetadata();
    this.loadPerformanceData();
  }

  private academicPdfQuery(): ReportPdfQuery {
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
      ['majorId', this.selectedMajor],
      ['studyStatusId', this.selectedStatus],
      ['degreeId', this.selectedDegree],
      ['levelId', this.selectedLevel]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/academic-performance/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.academicPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.academicPdfQuery(), lang: this.selectedLang };

  }

  getGroupedData(): any[] {
    const grouped = this.performanceData.reduce((acc, item) => {
      let key = '';
      switch (this.groupBy) {
        case 'major':
          key = item.studentMajorName;
          break;
        case 'level':
          key = item.studentCurrentLevelName;
          break;
        case 'status':
          key = item.studentStudyStatusName;
          break;
        case 'degree':
          key = item.studentDegreeName;
          break;
        case 'country':
          key = item.studentCountryName;
          break;
      }
      
      if (!acc[key]) {
        acc[key] = { total: 0 };
      }
      acc[key].total += 1;
      return acc;
    }, {} as any);

    return Object.entries(grouped).map(([key, value]) => ({
      label: key,
      total: (value as any).total
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

  getPerformanceLevel(yearsEnrolled?: number): string {
    const years = yearsEnrolled ?? 0;
    if (years <= 2) return 'Early';
    if (years <= 4) return 'Normal';
    if (years <= 6) return 'Extended';
    return 'Long-term';
  }

  getPerformanceSeverity(yearsEnrolled?: number): string {
    const years = yearsEnrolled ?? 0;
    if (years <= 2) return 'success';
    if (years <= 4) return 'info';
    if (years <= 6) return 'warning';
    return 'danger';
  }

  private toDropdownOption(option: LookupOptionResponse): DropdownOption {
    return {
      label: option.name,
      value: option.id
    };
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
