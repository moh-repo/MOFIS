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

interface MajorDistribution {
  studentMajorId: number;
  studentMajorName: string;
  studentDegreeId: number;
  studentDegreeName: string;
  studentCountryId: number;
  studentCountryName: string;
  studentStudyTypeId: number;
  studentStudyTypeName: string;
  studentCount: number;
  maleCount: number;
  femaleCount: number;
}

interface LookupOptionResponse {
  id: number;
  name: string;
}

interface MajorMetadataResponse {
  majors: LookupOptionResponse[];
  degrees: LookupOptionResponse[];
  countries: LookupOptionResponse[];
  studyTypes: LookupOptionResponse[];
}

interface DropdownOption {
  label: string;
  value: number;
}

interface MajorMetadata {
  majors: DropdownOption[];
  degrees: DropdownOption[];
  countries: DropdownOption[];
  studyTypes: DropdownOption[];
}

/** One card in the gender distribution block. */
interface GenderShare {
  label: string;
  value: number;
  color: string;
}

@Component({
    selector: 'app-major-distribution',
    templateUrl: './major-distribution.component.html',
    styleUrls: ['./major-distribution.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class MajorDistributionComponent implements OnInit, OnDestroy {

  distributionData: MajorDistribution[] = [];
  metadata: MajorMetadata = {
    majors: [],
    degrees: [],
    countries: [],
    studyTypes: []
  };

  // Filter options
  selectedMajor: number | null = null;
  selectedDegree: number | null = null;
  selectedStudyType: number | null = null;

  // Display options
  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];
  private readonly destroy$ = new Subject<void>();

  groupBy: string = 'major';
  showChart: boolean = false;
  groupByOptions: any[] = [];

  loading: boolean = false;
  totalStudents: number = 0;
  totalMale: number = 0;
  totalFemale: number = 0;

  /**
   * Both computed once per load (and the gender labels again on a language
   * switch), not by methods in the template. An `@for` over a method that builds
   * fresh objects on every call is tracked by identity, so on the dev-mode
   * verification pass every card was "new": the repeater rebuilt them after the
   * parent had been checked and threw NG0100 on every tick, from the first one,
   * since the two gender cards exist before any data (QA M3). The old top-majors
   * method also sorted `distributionData` — the array the table is bound to — in
   * place on every call; the ranking is now taken from a copy.
   */
  genderDistribution: GenderShare[] = [];
  topMajors: MajorDistribution[] = [];

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

  onCountryChange(): void {
    this.loadMetadata();
    this.loadDistributionData();
  }

  initializeGroupByOptions(): void {
    this.lookupService.getReportDimensions('STUDENT_REPORT_GROUPBY')
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: opts => { this.groupByOptions = opts; },
        error: () => { this.groupByOptions = []; }
      });
  }

  ngOnInit(): void {
    this.initializeGroupByOptions();
    // The two gender cards show before the data arrives, at zero.
    this.genderDistribution = this.buildGenderDistribution();
    if (this.hasMultiCountry) { this.loadCountries(); }
    this.loadMetadata();
    this.loadDistributionData();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
      this.loadDistributionData();
    });
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.initializeGroupByOptions();
      // The gender labels are translated captions, not row data.
      this.genderDistribution = this.buildGenderDistribution();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadMetadata(): void {
    this.http.get<MajorMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/major-distribution/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          majors: (data.majors || []).map(option => ({
            label: option.name,
            value: option.id
          })),
          degrees: (data.degrees || []).map(option => ({
            label: option.name,
            value: option.id
          })),
          countries: this.reportCountryScope.filterToAllowedStudyCountries(data.countries || []).map(option => ({
            label: option.name,
            value: option.id
          })),
          studyTypes: (data.studyTypes || []).map(option => ({
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

  loadDistributionData(): void {
    this.loading = true;

    let params = this.buildHttpParams();
    if (this.selectedMajor !== null) params = params.set('majorId', String(this.selectedMajor));
    if (this.selectedDegree !== null) params = params.set('degreeId', String(this.selectedDegree));
    if (this.selectedStudyType !== null) params = params.set('studyTypeId', String(this.selectedStudyType));

    this.http.get<MajorDistribution[]>(`${environment.apiUrl}/mohe/api/reports/major-distribution/search`, { params }).subscribe({
      next: (data) => {
        this.distributionData = data;
        this.calculateStatistics();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading distribution data:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
        this.loading = false;
      }
    });
  }

  calculateStatistics(): void {
    this.totalStudents = this.distributionData.reduce((sum, item) => sum + item.studentCount, 0);
    this.totalMale = this.distributionData.reduce((sum, item) => sum + item.maleCount, 0);
    this.totalFemale = this.distributionData.reduce((sum, item) => sum + item.femaleCount, 0);
    this.genderDistribution = this.buildGenderDistribution();
    // A copy: the table is bound to `distributionData` in the order the server sent it.
    this.topMajors = [...this.distributionData]
      .sort((a, b) => b.studentCount - a.studentCount)
      .slice(0, 5);
  }

  onFilterChange(): void {
    this.loadDistributionData();
  }

  clearFilters(): void {
    this.selectedCountryIds = [];
    this.selectedMajor = null;
    this.selectedDegree = null;
    this.selectedStudyType = null;
    this.loadMetadata();
    this.loadDistributionData();
  }

  private majorPdfQuery(): ReportPdfQuery {
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
      ['degreeId', this.selectedDegree],
      ['studyTypeId', this.selectedStudyType]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/major-distribution/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.majorPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.majorPdfQuery(), lang: this.selectedLang };

  }

  getGroupedData(): any[] {
    const grouped = this.distributionData.reduce((acc, item) => {
      let key = '';
      switch (this.groupBy) {
        case 'major':
          key = item.studentMajorName;
          break;
        case 'degree':
          key = item.studentDegreeName;
          break;
        case 'country':
          key = item.studentCountryName;
          break;
        case 'studyType':
          key = item.studentStudyTypeName;
          break;
      }
      
      if (!acc[key]) {
        acc[key] = { total: 0, male: 0, female: 0 };
      }
      acc[key].total += item.studentCount;
      acc[key].male += item.maleCount;
      acc[key].female += item.femaleCount;
      return acc;
    }, {} as any);

    return Object.entries(grouped).map(([key, value]) => ({
      label: key,
      total: (value as any).total,
      male: (value as any).male,
      female: (value as any).female
    }));
  }

  private buildGenderDistribution(): GenderShare[] {
    return [
      { label: this.translationService.translate('REPORTS.INDIVIDUAL_REPORTS.MAJOR_DISTRIBUTION.MALE'), value: this.totalMale, color: '#3b82f6' },
      { label: this.translationService.translate('REPORTS.INDIVIDUAL_REPORTS.MAJOR_DISTRIBUTION.FEMALE'), value: this.totalFemale, color: '#ec4899' }
    ];
  }

  /**
   * Track key for a top-majors card. A row is one major within one degree,
   * country and study type, so the major id alone can repeat in the top five.
   */
  topMajorKey(major: MajorDistribution): string {
    return `${major.studentMajorId}|${major.studentDegreeId}|${major.studentCountryId}|${major.studentStudyTypeId}`;
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
