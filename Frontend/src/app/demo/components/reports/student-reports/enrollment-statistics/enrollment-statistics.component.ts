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

interface EnrollmentStatistics {
  /**
   * Raw MOHE quarter code as stored in V_Enrollment_Statistics, e.g. 20251.
   * Never display this: it is a packed year+quarter key, not a quarter number.
   */
  quarterCode: number;
  /** Calendar year, resolved from MOHE_QUARTER. Null when the code is unknown. */
  enrollmentYear?: number | null;
  /** Quarter within the year, 1..4. Null when the code is unknown. */
  enrollmentQuarter?: number | null;
  /** Ready-to-display quarter name in the current language. */
  quarterLabel: string;
  studentCountryId: number;
  studentCountryName: string;
  studentMajorId: number;
  studentMajorName: string;
  studentDegreeId: number;
  studentDegreeName: string;
  studentStudyTypeId: number;
  studentStudyTypeName: string;
  studentCount: number;
}

interface LookupOptionResponse {
  id: number;
  name: string;
}

interface DropdownOption {
  label: string;
  value: number;
}

interface EnrollmentMetadata {
  quarters: number[];
  countries: DropdownOption[];
  majors: DropdownOption[];
  degrees: DropdownOption[];
  studyTypes: DropdownOption[];
}

interface EnrollmentMetadataResponse {
  quarters: number[];
  quarterOptions: LookupOptionResponse[];
  countries: LookupOptionResponse[];
  majors: LookupOptionResponse[];
  degrees: LookupOptionResponse[];
  studyTypes: LookupOptionResponse[];
}

@Component({
    selector: 'app-enrollment-statistics',
    templateUrl: './enrollment-statistics.component.html',
    styleUrls: ['./enrollment-statistics.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class EnrollmentStatisticsComponent implements OnInit, OnDestroy {

  private readonly destroy$ = new Subject<void>();

  quarterOptions: DropdownOption[] = [];
  enrollmentData: EnrollmentStatistics[] = [];
  metadata: EnrollmentMetadata = {
    quarters: [],
    countries: [],
    majors: [],
    degrees: [],
    studyTypes: []
  };

  // Filter options
  selectedQuarter: number | null = null;
  selectedMajor: number | null = null;
  selectedDegree: number | null = null;
  selectedStudyType: number | null = null;
  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];

  // Display options
  groupBy: string = 'quarter';
  showChart: boolean = false;
  groupByOptions: any[] = [];

  loading: boolean = false;
  totalStudents: number = 0;

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

  initializeGroupByOptions(): void {
    this.lookupService.getReportDimensions('ENROLLMENT_STATISTICS_GROUPBY')
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: opts => { this.groupByOptions = opts; },
        error: () => { this.groupByOptions = []; }
      });
  }

  ngOnInit(): void {
    this.initializeGroupByOptions();
    this.loadMetadata();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
    });
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.initializeGroupByOptions();
      this.loadMetadata();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildHttpParams(): HttpParams {
    const base = this.selectedCountryIds.length > 0
      ? this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds)
      : this.reportCountryScope.appendCountryParam();
    // Quarter names are resolved server-side from MOHE_QUARTER, so the request
    // has to say which language to label them in.
    return base.set('lang', this.translationService.getCurrentLanguage() === 'en' ? 'en' : 'ar');
  }

  loadMetadata(): void {
    this.http.get<EnrollmentMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/enrollment-statistics/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          quarters: data.quarters ?? [],
          // No filterToAllowedStudyCountries here: these ids are nationalities
          // (STUDENT_COUNTRY), not study destinations, and the server has
          // already scoped them.
          countries: (data.countries ?? []).map((option) => this.toDropdownOption(option)),
          majors: (data.majors ?? []).map((option) => this.toDropdownOption(option)),
          degrees: (data.degrees ?? []).map((option) => this.toDropdownOption(option)),
          studyTypes: (data.studyTypes ?? []).map((option) => this.toDropdownOption(option))
        };
        // Labels come from MOHE_QUARTER. Building them locally produced
        // "Quarter 20251", because the stored value is a packed quarter code.
        this.quarterOptions = (data.quarterOptions ?? []).map((option) => this.toDropdownOption(option));
        this.allowedCountries = this.metadata.countries;
        this.dropStaleSelections();
        this.loadEnrollmentData();
      },
      error: (error) => {
        console.error('Error loading metadata:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
        // Still show whatever the current filters return.
        this.loadEnrollmentData();
      }
    });
  }

  /**
   * Clears any selection that the refreshed option lists no longer offer.
   *
   * Without this, narrowing the country would leave a major selected that is
   * not present in the new scope, and the page would sit on an empty result
   * with a filter the dropdown can no longer even display.
   */
  private dropStaleSelections(): void {
    const has = (opts: DropdownOption[], v: number | null) =>
      v === null || opts.some((o) => o.value === v);

    if (!has(this.quarterOptions, this.selectedQuarter)) this.selectedQuarter = null;
    if (!has(this.metadata.majors, this.selectedMajor)) this.selectedMajor = null;
    if (!has(this.metadata.degrees, this.selectedDegree)) this.selectedDegree = null;
    if (!has(this.metadata.studyTypes, this.selectedStudyType)) this.selectedStudyType = null;

    const allowed = new Set(this.metadata.countries.map((o) => o.value));
    const keptCountries = this.selectedCountryIds.filter((id) => allowed.has(id));
    if (keptCountries.length !== this.selectedCountryIds.length) {
      this.selectedCountryIds = keptCountries;
    }
  }

  loadEnrollmentData(): void {
    this.loading = true;
    let params = this.buildHttpParams();
    if (this.selectedQuarter !== null) params = params.set('quarter', String(this.selectedQuarter));
    if (this.selectedMajor !== null) params = params.set('majorId', String(this.selectedMajor));
    if (this.selectedDegree !== null) params = params.set('degreeId', String(this.selectedDegree));
    if (this.selectedStudyType !== null) params = params.set('studyTypeId', String(this.selectedStudyType));

    this.http.get<EnrollmentStatistics[]>(
      `${environment.apiUrl}/mohe/api/reports/enrollment-statistics/search`,
      { params }
    ).subscribe({
      next: (data) => {
        this.enrollmentData = data;
        this.calculateTotalStudents();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading enrollment data:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
        this.loading = false;
      }
    });
  }

  calculateTotalStudents(): void {
    this.totalStudents = this.enrollmentData.reduce((sum, item) => sum + item.studentCount, 0);
    this.rebuildChart();
  }

  /**
   * Distinct countries in the rows on screen.
   *
   * The summary cards used to show metadata.countries.length, which is the size
   * of the country *filter list* — every country in the lookup, unchanged by any
   * filter — so the figure never described the data being displayed.
   */
  get distinctCountries(): number {
    return new Set(this.enrollmentData.map((r) => r.studentCountryId)).size;
  }

  get distinctMajors(): number {
    return new Set(this.enrollmentData.map((r) => r.studentMajorId)).size;
  }

  // ── Grouped breakdown ────────────────────────────────────────────────────
  // "Group by" and "Show chart" were bound to the component but nothing ever
  // rendered them: getGroupedData() had no caller and no chart existed.

  chartData: any = null;
  chartOptions: any = null;

  onGroupByChange(): void {
    this.rebuildChart();
  }

  /** Grouped rows, largest first, for the table beside the chart. */
  groupedRows(): { label: string; value: number; share: number }[] {
    const rows = this.getGroupedData()
      .map((r) => ({ label: r.label || '—', value: Number(r.value) || 0 }))
      .sort((a, b) => b.value - a.value);
    const total = rows.reduce((s, r) => s + r.value, 0);
    return rows.map((r) => ({ ...r, share: total > 0 ? (r.value * 100) / total : 0 }));
  }

  private rebuildChart(): void {
    const rows = this.groupedRows();
    if (rows.length === 0) {
      this.chartData = null;
      return;
    }
    const style = getComputedStyle(document.documentElement);
    const text = style.getPropertyValue('--text-color') || '#495057';
    const grid = style.getPropertyValue('--surface-border') || '#dee2e6';

    this.chartData = {
      labels: rows.map((r) => r.label),
      datasets: [
        {
          label: this.translationService.translate('REPORTS.REPORT_SUBPAGES.TOTAL_STUDENTS'),
          data: rows.map((r) => r.value),
          backgroundColor: '#722030',
          borderColor: '#722030',
          borderRadius: 4
        }
      ]
    };
    this.chartOptions = {
      indexAxis: 'y',
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { beginAtZero: true, ticks: { color: text, precision: 0 }, grid: { color: grid } },
        y: { ticks: { color: text }, grid: { display: false } }
      }
    };
  }

  onFilterChange(): void {
    this.loadEnrollmentData();
  }

  onCountryChange(): void {
    // Narrowing the country narrows every other option list too.
    this.loadMetadata();
  }

  clearFilters(): void {
    this.selectedQuarter = null;
    this.selectedMajor = null;
    this.selectedDegree = null;
    this.selectedStudyType = null;
    this.selectedCountryIds = [];
    this.loadMetadata();
  }

  private enrollmentPdfQuery(): ReportPdfQuery {
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
      ['quarter', this.selectedQuarter],
      ['majorId', this.selectedMajor],
      ['degreeId', this.selectedDegree],
      ['studyTypeId', this.selectedStudyType]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/enrollment-statistics/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.enrollmentPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.enrollmentPdfQuery(), lang: this.selectedLang };

  }

  getGroupedData(): any[] {
    const grouped = this.enrollmentData.reduce((acc, item) => {
      let key = '';
      switch (this.groupBy) {
        case 'quarter':
          key = item.quarterLabel;
          break;
        case 'country':
          key = item.studentCountryName;
          break;
        case 'major':
          key = item.studentMajorName;
          break;
        case 'degree':
          key = item.studentDegreeName;
          break;
        case 'studyType':
          key = item.studentStudyTypeName;
          break;
      }
      if (!acc[key]) acc[key] = 0;
      acc[key] += item.studentCount;
      return acc;
    }, {} as any);

    return Object.entries(grouped).map(([key, value]) => ({
      label: key,
      value: value
    }));
  }

  private toDropdownOption(option: LookupOptionResponse): DropdownOption {
    return { label: option.name, value: option.id };
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

