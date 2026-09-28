import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportPdfService, ReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { buildReportFilterQuery } from 'src/app/demo/service/report-query.util';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface StudentListDetailed {
  studentId: number;
  studentName: string;
  studentPassportNumber: string;
  studentBirthdate: string;
  studentAge: number;
  studentCountryId: number;
  studentCountryName: string;
  studentGender: number;
  studentGenderName: string;
  studentMajorId: number;
  studentMajorName: string;
  studentDegreeId: number;
  studentDegreeName: string;
  studentCurrentLevelId: number;
  studentCurrentLevelName: string;
  studentStudyTypeId: number;
  studentStudyTypeName: string;
  studentStudyStatusId: number;
  studentStudyStatusName: string;
  studentUniversityTypeId: number;
  studentUniversityTypeName: string;
  studentAgencyTypeId: number;
  studentAgencyTypeName: string;
  studentMinistryTypeId: number;
  studentMinistryTypeName: string;
  studentStudyNoOfYears: number;
  studentDelegationDecisionYear: string;
  studentCreatedDate: string;
}

interface LookupOptionResponse {
  id: number;
  name: string;
}

interface StudentListMetadataResponse {
  countries: LookupOptionResponse[];
  genders: LookupOptionResponse[];
  majors: LookupOptionResponse[];
  degrees: LookupOptionResponse[];
  levels: LookupOptionResponse[];
  studyTypes: LookupOptionResponse[];
  statuses: LookupOptionResponse[];
  universityTypes: LookupOptionResponse[];
  agencyTypes: LookupOptionResponse[];
  ministryTypes: LookupOptionResponse[];
}

interface DropdownOption {
  label: string;
  value: number | string;
}

interface StudentListMetadata {
  countries: DropdownOption[];
  genders: DropdownOption[];
  majors: DropdownOption[];
  degrees: DropdownOption[];
  levels: DropdownOption[];
  studyTypes: DropdownOption[];
  statuses: DropdownOption[];
  universityTypes: DropdownOption[];
  agencyTypes: DropdownOption[];
  ministryTypes: DropdownOption[];
}

@Component({
    selector: 'app-student-list-detailed',
    templateUrl: './student-list-detailed.component.html',
    styleUrls: ['./student-list-detailed.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentListDetailedComponent implements OnInit, OnDestroy {

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];
  private readonly destroy$ = new Subject<void>();

  studentData: StudentListDetailed[] = [];
  metadata: StudentListMetadata = {
    countries: [],
    genders: [],
    majors: [],
    degrees: [],
    levels: [],
    studyTypes: [],
    statuses: [],
    universityTypes: [],
    agencyTypes: [],
    ministryTypes: []
  };

  // Filter options
  selectedGender: number | null = null;
  selectedMajor: number | null = null;
  selectedDegree: number | null = null;
  selectedLevel: number | null = null;
  selectedStudyType: number | null = null;
  selectedStatus: number | null = null;
  selectedUniversityType: number | null = null;
  selectedAgencyType: number | null = null;
  selectedMinistryType: number | null = null;

  // Pagination
  currentPage: number = 0;
  pageSize: number = 20;
  totalRecords: number = 0;

  // Display options
  showAdvancedFilters: boolean = false;

  loading: boolean = false;

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

  ngOnInit(): void {
    if (this.hasMultiCountry) { this.loadCountries(); }
    this.loadMetadata();
    this.loadStudentData();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.currentPage = 0;
      this.loadMetadata();
      this.loadStudentData();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadMetadata(): void {
    this.http.get<StudentListMetadataResponse>(
      `${environment.apiUrl}/mohe/api/reports/student-list/metadata`,
      { params: this.buildHttpParams() }
    ).subscribe({
      next: (data) => {
        this.metadata = {
          countries: this.reportCountryScope.filterToAllowedStudyCountries(data.countries || []).map(option => this.toDropdownOption(option)),
          genders: (data.genders || []).map(option => this.toDropdownOption(option)),
          majors: (data.majors || []).map(option => this.toDropdownOption(option)),
          degrees: (data.degrees || []).map(option => this.toDropdownOption(option)),
          levels: (data.levels || []).map(option => this.toDropdownOption(option)),
          studyTypes: (data.studyTypes || []).map(option => this.toDropdownOption(option)),
          statuses: (data.statuses || []).map(option => this.toDropdownOption(option)),
          universityTypes: (data.universityTypes || []).map(option => this.toDropdownOption(option)),
          agencyTypes: (data.agencyTypes || []).map(option => this.toDropdownOption(option)),
          ministryTypes: (data.ministryTypes || []).map(option => this.toDropdownOption(option))
        };
      },
      error: (error) => {
        console.error('Error loading metadata:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
      }
    });
  }

  onCountryChange(): void {
    this.currentPage = 0;
    this.loadMetadata();
    this.loadStudentData();
  }

  loadStudentData(): void {
    this.loading = true;
    let params = this.buildHttpParams()
      .set('page', String(this.currentPage))
      .set('size', String(this.pageSize));
    if (this.selectedGender !== null) params = params.set('genderId', String(this.selectedGender));
    if (this.selectedMajor !== null) params = params.set('majorId', String(this.selectedMajor));
    if (this.selectedDegree !== null) params = params.set('degreeId', String(this.selectedDegree));
    if (this.selectedLevel !== null) params = params.set('levelId', String(this.selectedLevel));
    if (this.selectedStudyType !== null) params = params.set('studyTypeId', String(this.selectedStudyType));
    if (this.selectedStatus !== null) params = params.set('studyStatusId', String(this.selectedStatus));
    if (this.selectedUniversityType !== null) params = params.set('universityTypeId', String(this.selectedUniversityType));
    if (this.selectedAgencyType !== null) params = params.set('agencyTypeId', String(this.selectedAgencyType));
    if (this.selectedMinistryType !== null) params = params.set('ministryTypeId', String(this.selectedMinistryType));

    this.http.get<any>(`${environment.apiUrl}/mohe/api/reports/student-list/search`, { params }).subscribe({
      next: (response) => {
        this.studentData = response.content || response;
        this.totalRecords = response.totalElements || response.length;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading student data:', error);
        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.LOAD_ERROR') });
        this.loading = false;
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0; // Reset to first page when filters change
    this.loadStudentData();
  }

  clearFilters(): void {
    this.selectedGender = null;
    this.selectedMajor = null;
    this.selectedDegree = null;
    this.selectedLevel = null;
    this.selectedStudyType = null;
    this.selectedStatus = null;
    this.selectedUniversityType = null;
    this.selectedAgencyType = null;
    this.selectedMinistryType = null;
    this.selectedCountryIds = [];
    this.currentPage = 0;
    this.loadMetadata();
    this.loadStudentData();
  }

  private toDropdownOption(option: LookupOptionResponse, valueOverride?: number | string): DropdownOption {
    return {
      label: option.name,
      value: valueOverride !== undefined ? valueOverride : option.id
    };
  }

  onPageChange(event: any): void {
    this.currentPage = event.page;
    this.pageSize = event.rows;
    this.loadStudentData();
  }

  private studentListPdfQuery() {
    const countryEntries: [string, any][] = [];
    if (this.selectedCountryIds.length === 1) {
      countryEntries.push(['countryId', this.selectedCountryIds[0]]);
    } else if (this.selectedCountryIds.length > 1) {
      countryEntries.push(['countryIds', this.selectedCountryIds]);
    } else {
      countryEntries.push(['countryId', this.reportCountryScope.countryIdParam()]);
    }
    return buildReportFilterQuery([
      ...countryEntries,
      ['genderId', this.selectedGender],
      ['majorId', this.selectedMajor],
      ['degreeId', this.selectedDegree],
      ['levelId', this.selectedLevel],
      ['studyTypeId', this.selectedStudyType],
      ['studyStatusId', this.selectedStatus],
      ['universityTypeId', this.selectedUniversityType],
      ['agencyTypeId', this.selectedAgencyType],
      ['ministryTypeId', this.selectedMinistryType]
    ]);
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/student-list/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  generateReport(): void {
      this.pdfQuery = { ...this.studentListPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.studentListPdfQuery(), lang: this.selectedLang };

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

  getGenderSeverity(gender: string): string {
    if (gender?.toLowerCase().includes('male') || gender?.toLowerCase().includes('ذكر')) {
      return 'info';
    } else if (gender?.toLowerCase().includes('female') || gender?.toLowerCase().includes('أنثى')) {
      return 'success';
    }
    return 'secondary';
  }

  formatDate(dateString: string): string {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString();
  }

  getAgeRange(age: number): string {
    if (age < 20) return 'Under 20';
    if (age < 25) return '20-24';
    if (age < 30) return '25-29';
    if (age < 35) return '30-34';
    if (age < 40) return '35-39';
    return '40+';
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
