import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface StudentDemographics {
  studentId: number;
  studentName: string;
  studentCountryId: number;
  studentCountryName: string;
  studentGenderName: string;
  studentAge: number | null;
  studentCurrentLevelId: number;
  studentCurrentLevelName: string;
  studentStudyTypeId: number;
  studentStudyTypeName: string;
  studentDegreeId: number;
  studentDegreeName: string;
  studentMajorId: number;
  studentMajorName: string;
  studentStudyStatusId: number;
  studentStudyStatusName: string;
}

interface LookupOptionResponse {
  id: number;
  name: string;
}

interface DropdownOption {
  label: string;
  value: number;
}

interface AgeRangeOption {
  label: string;
  value: string;
}

interface DemographicsMetadataResponse {
  countries: LookupOptionResponse[];
  genders: LookupOptionResponse[];
  ageRanges: string[];
  levels: LookupOptionResponse[];
  studyTypes: LookupOptionResponse[];
  degrees: LookupOptionResponse[];
  majors: LookupOptionResponse[];
  statuses: LookupOptionResponse[];
}

interface DemographicsMetadata {
  countries: DropdownOption[];
  genders: DropdownOption[];
  ageRanges: AgeRangeOption[];
  levels: DropdownOption[];
  studyTypes: DropdownOption[];
  degrees: DropdownOption[];
  majors: DropdownOption[];
  statuses: DropdownOption[];
}

/** One card in the gender distribution block. */
interface GenderDistributionEntry {
  label: string;
  value: number;
  severity: string;
}

@Component({
    selector: 'app-student-demographics',
    templateUrl: './student-demographics.component.html',
    styleUrls: ['./student-demographics.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentDemographicsComponent implements OnInit, OnDestroy {

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];
  private readonly destroy$ = new Subject<void>();

  demographicsData: StudentDemographics[] = [];
  metadata: DemographicsMetadata = {
    countries: [],
    genders: [],
    ageRanges: [],
    levels: [],
    studyTypes: [],
    degrees: [],
    majors: [],
    statuses: []
  };

  // Filter options
  selectedGender: number | null = null;
  selectedAgeRange: string | null = null;
  selectedLevel: number | null = null;
  selectedStudyType: number | null = null;
  selectedDegree: number | null = null;
  selectedMajor: number | null = null;
  selectedStatus: number | null = null;

  // Display options
  groupBy: string = 'country';
  showChart: boolean = false;
  groupByOptions: any[] = [];

  loading: boolean = false;
  totalStudents: number = 0;

  /**
   * Computed once per load, not by a method in the template. An `@for` over a
   * method that builds fresh objects on every call is tracked by identity, so on
   * the dev-mode verification pass every card was "new": the repeater rebuilt
   * them after the parent had been checked and threw NG0100 on every tick (QA M3).
   */
  genderDistribution: GenderDistributionEntry[] = [];

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
    this.loadMetadata();
    this.loadDemographicsData();
  }

  initializeGroupByOptions(): void {
    this.lookupService.getReportDimensions('STUDENT_DEMOGRAPHICS_GROUPBY')
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
    this.loadDemographicsData();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
      this.loadDemographicsData();
    });
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.initializeGroupByOptions();
      this.metadata.ageRanges = (this.metadata.ageRanges || []).map(option => ({
        value: option.value,
        label: this.getAgeRangeLabel(option.value)
      }));
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadMetadata(): void {
    this.http.get<DemographicsMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/student-demographics/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          countries: this.reportCountryScope.filterToAllowedStudyCountries(data.countries ?? [])
              .map(option => this.toDropdownOption(option)),
          genders: (data.genders ?? []).map(option => this.toDropdownOption(option)),
          ageRanges: (data.ageRanges ?? []).map(value => this.toAgeRangeOption(value)),
          levels: (data.levels ?? []).map(option => this.toDropdownOption(option)),
          studyTypes: (data.studyTypes ?? []).map(option => this.toDropdownOption(option)),
          degrees: (data.degrees ?? []).map(option => this.toDropdownOption(option)),
          majors: (data.majors ?? []).map(option => this.toDropdownOption(option)),
          statuses: (data.statuses ?? []).map(option => this.toDropdownOption(option))
        };
      },
      error: (error) => {
        console.error('Error loading metadata:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
      }
    });
  }

  loadDemographicsData(): void {
    this.loading = true;

    let params = this.buildHttpParams();
    if (this.selectedGender !== null) params = params.set('genderId', String(this.selectedGender));
    if (this.selectedAgeRange) params = params.set('ageRange', this.selectedAgeRange);
    if (this.selectedLevel !== null) params = params.set('levelId', String(this.selectedLevel));
    if (this.selectedStudyType !== null) params = params.set('studyTypeId', String(this.selectedStudyType));
    if (this.selectedDegree !== null) params = params.set('degreeId', String(this.selectedDegree));
    if (this.selectedMajor !== null) params = params.set('majorId', String(this.selectedMajor));
    if (this.selectedStatus !== null) params = params.set('studyStatusId', String(this.selectedStatus));

    this.http.get<StudentDemographics[]>(`${environment.apiUrl}/mohe/api/reports/student-demographics/search`, { params }).subscribe({
      next: (data) => {
        this.demographicsData = data;
        this.calculateTotalStudents();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading demographics data:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
        this.loading = false;
      }
    });
  }

  calculateTotalStudents(): void {
    this.totalStudents = this.demographicsData.length;
    this.genderDistribution = this.buildGenderDistribution();
  }

  onFilterChange(): void {
    this.loadDemographicsData();
  }

  clearFilters(): void {
    this.selectedCountryIds = [];
    this.selectedGender = null;
    this.selectedAgeRange = null;
    this.selectedLevel = null;
    this.selectedStudyType = null;
    this.selectedDegree = null;
    this.selectedMajor = null;
    this.selectedStatus = null;
    this.loadMetadata();
    this.loadDemographicsData();
  }

  private demographicsPdfQuery(): ReportPdfQuery {
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
      ['genderId', this.selectedGender],
      ['ageRange', this.selectedAgeRange ?? undefined],
      ['levelId', this.selectedLevel],
      ['studyTypeId', this.selectedStudyType],
      ['degreeId', this.selectedDegree],
      ['majorId', this.selectedMajor],
      ['studyStatusId', this.selectedStatus]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/student-demographics/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.demographicsPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.demographicsPdfQuery(), lang: this.selectedLang };

  }

  getGroupedData(): any[] {
    const grouped = this.demographicsData.reduce((acc, item) => {
      let key = '';
      switch (this.groupBy) {
        case 'country':
          key = item.studentCountryName;
          break;
        case 'gender':
          key = item.studentGenderName;
          break;
        case 'age':
          key = this.getAgeRangeLabel(this.getAgeRangeCode(item.studentAge));
          break;
        case 'level':
          key = item.studentCurrentLevelName;
          break;
        case 'studyType':
          key = item.studentStudyTypeName;
          break;
        case 'degree':
          key = item.studentDegreeName;
          break;
        case 'major':
          key = item.studentMajorName;
          break;
        case 'status':
          key = item.studentStudyStatusName;
          break;
      }
      
      if (!acc[key]) {
        acc[key] = 0;
      }
      acc[key] += 1;
      return acc;
    }, {} as any);

    return Object.entries(grouped).map(([key, value]) => ({
      label: key,
      value: value
    }));
  }

  getAgeRangeCode(age: number | null): string | null {
    if (age === null || age === undefined) {
      return null;
    }
    if (age < 20) return 'UNDER_20';
    if (age < 25) return '20_24';
    if (age < 30) return '25_29';
    if (age < 35) return '30_34';
    if (age < 40) return '35_39';
    return '40_PLUS';
  }

  /**
   * Tag colour by the row's status id, not by its display name.
   *
   * The names are whatever the lookup holds in the current language — and the
   * Arabic seed says ثابت / منزل / موقف, not the نشط / منسحب / موقوف the old
   * substring test looked for, so every Arabic row came out grey. The ids are
   * MOHECORE.STUDENT_STUDY_STATUS (CODE column, V028): 1 ACTIVE, 2 NEW,
   * 3 WITHDRAWN, 4 SUSPENDED, 5 TRANSFERRED, 7 STRUGGLING, 0 DECEASED.
   * There is no GRADUATED row in that table; nothing here can be "success".
   */
  getStatusSeverity(statusId: number | null | undefined): string {
    switch (Number(statusId)) {
      case 1: case 2: return 'info';      // ACTIVE, NEW
      case 4: case 7: return 'warning';   // SUSPENDED, STRUGGLING
      case 3: case 0: return 'danger';    // WITHDRAWN, DECEASED
      default: return 'secondary';
    }
  }

  private buildGenderDistribution(): GenderDistributionEntry[] {
    const genderCounts = this.demographicsData.reduce((acc, item) => {
      const gender = item.studentGenderName || 'Unknown';
      acc[gender] = (acc[gender] ?? 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    return Object.entries(genderCounts).map(([label, value]) => ({
      label,
      value,
      severity: this.getGenderSeverity(label)
    }));
  }

  getGenderSeverity(label: string): string {
    if (this.isMale(label)) {
      return 'info';
    }
    if (this.isFemale(label)) {
      return 'success';
    }
    return 'secondary';
  }

  isMale(label: string): boolean {
    const lower = (label || '').toLowerCase();
    return lower.includes('male') || lower.includes('ذكر');
  }

  isFemale(label: string): boolean {
    const lower = (label || '').toLowerCase();
    return lower.includes('female') || lower.includes('أنثى');
  }

  private toDropdownOption(option: LookupOptionResponse): DropdownOption {
    return {
      label: option.name,
      value: option.id
    };
  }

  private toAgeRangeOption(value: string): AgeRangeOption {
    return {
      value,
      label: this.getAgeRangeLabel(value)
    };
  }

  private getAgeRangeLabel(code: string | null): string {
    if (!code) {
      return this.translationService.translate('REPORTS.REPORT_SUBPAGES.UNKNOWN');
    }
    // Age buckets are computed by the report (arithmetic on birth dates, not a
    // table); their captions live under AGE_RANGE_<bucket>. A bucket the
    // dictionary does not know is shown as itself.
    const key = `REPORTS.INDIVIDUAL_REPORTS.STUDENT_DEMOGRAPHICS.AGE_RANGE_${code}`;
    const label = this.translationService.translate(key);
    return label !== key ? label : code;
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
