import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { StudentDataArch } from '../../api/studentdataarch';
import { StudentProgressDto } from '../../api/student-progress';
import { StudentYearResultDto, YearOutcome, yearOutcome, YEAR_OUTCOME_KEY, YEAR_OUTCOME_SEVERITY } from '../../api/student-year-result';
import { StudentDataArchService } from '../../service/studentdataarch.service';
import { StudentdataService } from '../../service/studentdata.service';
import { StudentDocument, StudentDocumentService } from '../../service/student-document.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { MessageService } from 'primeng/api';
import { forkJoin, Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { formatQuarterCode } from '../../service/quarter-code.util';

@Component({
    selector: 'app-student-history',
    templateUrl: './student-history.component.html',
    styleUrls: ['./student-history.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentHistoryComponent implements OnInit, OnDestroy {

  private languageSubscription?: Subscription;

  // Master data
  students: StudentDataArch[] = [];
  selectedStudent: StudentDataArch | null = null;
  selectedStudentFinancialRecords: StudentDataArch[] = [];
  selectedDetailedRecord: StudentDataArch | null = null; // For nested master-details
  newStudent: StudentDataArch = new StudentDataArch();
  loading: boolean = false;
  isLoadingData: boolean = false; // Guard to prevent infinite loops
  loadingFinancialRecords: boolean = false;
  studentProgress: StudentProgressDto | null = null;
  progressLoading: boolean = false;
  yearResults: StudentYearResultDto[] = [];

  /** A seeded placeholder year reads "not recorded" (FIX-PLAN P5-8). */
  outcome(r: StudentYearResultDto): YearOutcome {
    return yearOutcome(r);
  }

  outcomeKey(r: StudentYearResultDto): string {
    return YEAR_OUTCOME_KEY[yearOutcome(r)];
  }

  outcomeSeverity(r: StudentYearResultDto): 'success' | 'danger' | 'secondary' {
    return YEAR_OUTCOME_SEVERITY[yearOutcome(r)];
  }
  yearResultsLoading: boolean = false;

  // Photo + documents for the selected/viewed archived student (fetched live by studentId).
  viewPhotoUrl: string | null = null;
  private _viewPhotoBlobUrl: string | null = null;
  historyDocuments: StudentDocument[] = [];
  loadingHistoryDocs: boolean = false;
  private _docBlobUrls: string[] = [];

  /** Field-level differences of the selected student between consecutive quarter snapshots. */
  quarterChanges: { fromLabel: string; toLabel: string; changes: { label: string; from: string; to: string }[] }[] = [];

  // Dialog flags. View only: the archive is read-only (DEC-08 a).
  viewDialog: boolean = false;

  // Pagination
  totalRecords: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  first: number = 0;

  // Server-side sort (default: newest archive id first)
  sortField: string = 'id';
  sortDir: 'asc' | 'desc' = 'desc';

  // Filters
  filterStudentId: string = '';
  filterStudentName: string = '';
  filterPassport: string = '';

  // Lookup Maps
  countryMap: Map<number, string> = new Map();
  govMap: Map<number, string> = new Map();
  mudMap: Map<number, string> = new Map();
  degreeMap: Map<number, string> = new Map();
  majorMap: Map<number, string> = new Map();
  studyStatusMap: Map<number, string> = new Map();
  agencyTypeMap: Map<number, string> = new Map();
  ministryTypeMap: Map<number, string> = new Map();
  universityTypeMap: Map<number, string> = new Map();
  studyTypeMap: Map<number, string> = new Map();
  levelMap: Map<number, string> = new Map();
  studyCountryInMap: Map<number, string> = new Map();
  studyGovInMap: Map<number, string> = new Map();
  studyUniversityInMap: Map<number, string> = new Map();
  studyCollegeInMap: Map<number, string> = new Map();
  studyEducationInMap: Map<number, string> = new Map();
  studyMajorInMap: Map<number, string> = new Map();
  genderMap: Map<number, string> = new Map();
  uniFeeStatusMap: Map<number, string> = new Map();
  genderOptions: { label: string; value: number }[] = [];

  // Table columns (headers set from translation in ngOnInit)
  cols: any[] = [
    { field: 'studentId', header: '', width: '80px' },
    { field: 'studentName', header: '', width: '200px' },
    { field: 'studentMajorName', header: '', width: '150px' },
    { field: 'currentQuarter', header: '', width: '100px' },
    { field: 'studentAmount', header: '', width: '120px' },
    { field: 'archiveDate', header: '', width: '120px' },
    { field: 'studentGender', header: '', width: '100px' }
  ];

  constructor(
    private studentDataArchService: StudentDataArchService,
    private studentdataService: StudentdataService,
    public studentDocumentService: StudentDocumentService,
    private lookupService: LookupService,
    public translationService: TranslationService,
    private cdr: ChangeDetectorRef,
    private messageService: MessageService
  ) { }

  private updateColumnHeaders(): void {
    const t = (key: string) => this.translationService.translate(key);
    this.cols[0].header = t('STUDENT_HISTORY.STUDENT_ID');
    this.cols[1].header = t('STUDENTS.NAME');
    this.cols[2].header = t('STUDENTS.MAJOR');
    this.cols[3].header = t('STUDENT_HISTORY.SNAPSHOT_QUARTER');
    this.cols[4].header = t('STUDENT_HISTORY.AMOUNT');
    this.cols[5].header = t('STUDENT_HISTORY.ARCHIVE_DATE_LABEL');
    this.cols[6].header = t('STUDENT_HISTORY.GENDER');
    this.cdr.markForCheck();
  }

  /** Keystrokes in the filter boxes, debounced in `ngOnInit`. */
  private readonly filterInput$ = new Subject<void>();
  private filterSubscription?: Subscription;
  /** The filters the list was last loaded with; a pause that changed nothing sends nothing. */
  private lastFilterKey = '||';
  /** A filter change arrived while a page was loading; it runs when that load ends. */
  private filterReloadPending = false;

  ngOnInit() {
    // One request once the user stops typing, not one per keystroke (review S8).
    this.filterSubscription = this.filterInput$.pipe(debounceTime(400)).subscribe(() => this.applyFilters());
    this.updateColumnHeaders();
    this.languageSubscription = this.translationService.currentLanguage$.subscribe(() => this.updateColumnHeaders());
    this.loadLookupMaps();
    setTimeout(() => {
      this.loadStudents();
    }, 0);
  }

  ngOnDestroy(): void {
    this.languageSubscription?.unsubscribe();
    this.filterSubscription?.unsubscribe();
    this.revokeViewPhoto();
  }

  loadLookupMaps() {
    forkJoin({
      countries: this.lookupService.getCountries(),
      govs: this.lookupService.getGovernments(),
      muds: this.lookupService.getMunicipalities(),
      degrees: this.lookupService.getStudentDegrees(),
      majors: this.lookupService.getStudentMajors(),
      studyStatuses: this.lookupService.getStudentStudyStatuses(),
      agencyTypes: this.lookupService.getAgencyTypes(),
      ministryTypes: this.lookupService.getMinistryTypes(),
      universityTypes: this.lookupService.getUniversityTypes(),
      studyTypes: this.lookupService.getStudentStudyTypes(),
      levels: this.lookupService.getCurrentLevels(),
      studyCountriesIn: this.lookupService.getStudyCountriesIn(),
      studyGovsIn: this.lookupService.getStudyGovsIn(),
      studyUniversitiesIn: this.lookupService.getStudyUniversitiesIn(),
      studyCollegesIn: this.lookupService.getStudyCollegesIn(),
      studyEducationsIn: this.lookupService.getStudyEducationsIn(),
      studyMajorsIn: this.lookupService.getStudyMajorsIn(),
      genders: this.lookupService.getStudentGenders(),
      uniFeeStatuses: this.lookupService.getUniversityFeeStatuses()
    }).subscribe({
      next: (results) => {
        this.genderOptions = (results.genders || []).map(g => ({ label: g.name || g.label || '', value: g.id ?? g.value }));
        (results.genders || []).forEach(g => {
          const id = Number(g.id ?? g.value);
          if (Number.isFinite(id)) this.genderMap.set(id, g.name || g.label || '');
        });
        (results.uniFeeStatuses || []).forEach(s => {
          const id = Number(s.value);
          if (Number.isFinite(id)) this.uniFeeStatusMap.set(id, s.label);
        });
        this.populateMap(this.countryMap, results.countries);
        this.populateMap(this.govMap, results.govs);
        this.populateMap(this.mudMap, results.muds);
        this.populateMap(this.degreeMap, results.degrees);
        this.populateMap(this.majorMap, results.majors);
        this.populateMap(this.studyStatusMap, results.studyStatuses);
        this.populateMap(this.agencyTypeMap, results.agencyTypes);
        this.populateMap(this.ministryTypeMap, results.ministryTypes);
        this.populateMap(this.universityTypeMap, results.universityTypes);
        this.populateMap(this.studyTypeMap, results.studyTypes);
        this.populateMap(this.levelMap, results.levels);
        this.populateMap(this.studyCountryInMap, results.studyCountriesIn);
        this.populateMap(this.studyGovInMap, results.studyGovsIn);
        this.populateMap(this.studyUniversityInMap, results.studyUniversitiesIn);
        this.populateMap(this.studyCollegeInMap, results.studyCollegesIn);
        this.populateMap(this.studyEducationInMap, results.studyEducationsIn);
        this.populateMap(this.studyMajorInMap, results.studyMajorsIn);
      },
      error: (err) => console.error('Error loading lookups', err)
    });
  }

  private populateMap(map: Map<number, string>, data: any[]) {
    data.forEach(item => {
      if (item.id !== undefined && item.name) {
        map.set(item.id, item.name);
      }
    });
  }

  /**
   * Gender label from the DB lookup. 0 is how the archive stores "not recorded" —
   * 1874 of 1882 rows on dev — and it showed as a bare "0" (live QA STD-004).
   */
  genderLabel(value: any): string {
    const n = Number(value);
    if (value == null || value === '' || !Number.isFinite(n) || n <= 0) {
      return this.translationService.translate('COMMON.UNSPECIFIED');
    }
    return this.genderMap.get(n) || String(value);
  }

  /**
   * "20242" → "2024 Q2": the YYYYQ code of the quarter the snapshot was taken in.
   * Formatted here rather than looked up: the quarter list sits behind the quarter
   * and finance permissions, which the history roles need not hold.
   */
  quarterLabel(code: unknown): string {
    return formatQuarterCode(code, this.translationService.translate('COMMON.QUARTER_SHORT'));
  }

  /** University-fee-status label from the DB lookup. */
  uniFeeStatusLabel(value: any): string {
    if (value === null || value === undefined || value === '') return 'N/A';
    const n = Number(value);
    return (Number.isFinite(n) && this.uniFeeStatusMap.get(n)) || String(value);
  }

  /** Up to two initials for the detail-dialog hero badge; empty when there is no name. */
  studentInitials(name: string | null | undefined): string {
    const parts = (name || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '';
    return parts.slice(0, 2).map(p => p.charAt(0)).join('').toUpperCase();
  }

  /** Detail-dialog display value: blank or the legacy 'N/A' renders as an em dash. */
  orDash(value: unknown): string {
    if (value === null || value === undefined || value === '' || value === 'N/A') return '—';
    return String(value);
  }

  /** A quarter snapshot's display label: its CURRENT_QUARTER, else archive date, else #id. */
  private quarterDisplay(r: StudentDataArch): string {
    if (r?.currentQuarter) return r.currentQuarter;
    if (r?.archiveDate) return new Date(r.archiveDate).toLocaleDateString();
    return '#' + r?.id;
  }

  /** Fields tracked when diffing quarters, each resolved to a DB-backed label. */
  private quarterFieldDefs(): { key: keyof StudentDataArch; label: string; resolve: (v: any) => string }[] {
    const t = (k: string) => this.translationService.translate(k);
    const fromMap = (m: Map<number, string>) => (v: any) =>
      (v === null || v === undefined || v === '') ? '—' : (m.get(Number(v)) || String(v));
    const amount = (v: any) => (v === null || v === undefined || v === '') ? '—' : Number(v).toLocaleString();
    const raw = (v: any) => (v === null || v === undefined || v === '') ? '—' : String(v);
    return [
      { key: 'studentName', label: t('STUDENTS.NAME'), resolve: raw },
      { key: 'currentQuarter', label: t('STUDENT_ADD.CURRENT_QUARTER'), resolve: raw },
      { key: 'studentMajorName', label: t('STUDENTS.MAJOR'), resolve: raw },
      { key: 'studentDegreeId', label: t('STUDENT_ADD.STUDENT_DEGREE'), resolve: fromMap(this.degreeMap) },
      { key: 'studentMajorId', label: t('STUDENT_ADD.STUDENT_MAJOR'), resolve: fromMap(this.majorMap) },
      { key: 'studentCurrentLevelId', label: t('STUDENTS.STUDENT_FORM.CURRENT_LEVEL'), resolve: fromMap(this.levelMap) },
      { key: 'studentStudyStatusId', label: t('STUDENTS.STATUS'), resolve: fromMap(this.studyStatusMap) },
      { key: 'studentStudyTypeId', label: t('STUDENT_ADD.STUDY_TYPE'), resolve: fromMap(this.studyTypeMap) },
      { key: 'studentAgencyTypeId', label: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_AGENCY_TYPE'), resolve: fromMap(this.agencyTypeMap) },
      { key: 'studentMinistryTypeId', label: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_MINISTRY_TYPE'), resolve: fromMap(this.ministryTypeMap) },
      { key: 'studentUniversityTypeId', label: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_UNIVERSITY_TYPE'), resolve: fromMap(this.universityTypeMap) },
      { key: 'studentGender', label: t('STUDENT_HISTORY.GENDER'), resolve: (v) => this.genderLabel(v) },
      { key: 'studentAmount', label: t('STUDENT_HISTORY.AMOUNT'), resolve: amount },
      { key: 'studyUniversityFeeStatusId', label: t('STUDENT_ADD.UNIVERSITY_FEE_STATUS'), resolve: (v) => this.uniFeeStatusLabel(v) },
      { key: 'studentCountryId', label: t('STUDENT_ADD.STUDENT_COUNTRY'), resolve: fromMap(this.countryMap) },
      { key: 'studentGovId', label: t('STUDENT_ADD.GOVERNMENT'), resolve: fromMap(this.govMap) },
      { key: 'studentMudId', label: t('STUDENT_ADD.MUNICIPALITY'), resolve: fromMap(this.mudMap) },
      { key: 'studyCountryIn', label: t('STUDENT_ADD.STUDY_COUNTRY_IN'), resolve: fromMap(this.studyCountryInMap) },
      { key: 'syudyGovIn', label: t('STUDENT_ADD.STUDY_GOV_IN'), resolve: fromMap(this.studyGovInMap) },
      { key: 'studyUniversityIn', label: t('STUDENT_ADD.STUDY_UNIVERSITY_IN'), resolve: fromMap(this.studyUniversityInMap) },
      { key: 'studyCollegeIn', label: t('STUDENT_ADD.STUDY_COLLEGE_IN'), resolve: fromMap(this.studyCollegeInMap) },
      { key: 'studyEducationIn', label: t('STUDENT_ADD.STUDY_EDUCATION_IN'), resolve: fromMap(this.studyEducationInMap) },
      { key: 'studyMajorIn', label: t('STUDENT_ADD.STUDY_MAJOR_IN'), resolve: fromMap(this.studyMajorInMap) },
      { key: 'studentAddedQuarter', label: t('STUDENT_ADD.ADDED_QUARTER'), resolve: raw },
      { key: 'studentLastQuarter', label: t('STUDENT_ADD.LAST_QUARTER'), resolve: raw },
      { key: 'studentDelegationDecisionYear', label: t('STUDENT_HISTORY.DECISION_YEAR'), resolve: raw },
      { key: 'studentDelegateDecisionNo', label: t('STUDENT_ADD.DELEGATE_DECISION_NO'), resolve: raw }
    ];
  }

  /** Builds the quarter-over-quarter change log for the currently selected student. */
  private computeQuarterChanges(): void {
    const recs = [...(this.selectedStudentFinancialRecords || [])];
    recs.sort((a, b) => {
      const qa = Number(a.currentQuarter) || 0;
      const qb = Number(b.currentQuarter) || 0;
      if (qa !== qb) return qa - qb;
      const ta = a.archiveDate ? new Date(a.archiveDate).getTime() : 0;
      const tb = b.archiveDate ? new Date(b.archiveDate).getTime() : 0;
      return ta - tb;
    });

    const defs = this.quarterFieldDefs();
    const out: { fromLabel: string; toLabel: string; changes: { label: string; from: string; to: string }[] }[] = [];
    for (let i = 1; i < recs.length; i++) {
      const prev = recs[i - 1];
      const cur = recs[i];
      const changes: { label: string; from: string; to: string }[] = [];
      for (const d of defs) {
        const from = d.resolve((prev as any)[d.key]);
        const to = d.resolve((cur as any)[d.key]);
        if (from !== to) changes.push({ label: d.label, from, to });
      }
      if (changes.length) {
        out.push({ fromLabel: this.quarterDisplay(prev), toLabel: this.quarterDisplay(cur), changes });
      }
    }
    this.quarterChanges = out;
  }

  /**
   * Loads the archived student's photo and documents live, keyed by the real studentId.
   * Archived/deleted students may have neither — the panels degrade to a placeholder / empty.
   */
  private loadPhotoAndDocuments(studentId: number | null | undefined): void {
    this.revokeViewPhoto();
    this.viewPhotoUrl = null;
    this.historyDocuments = [];
    const sid = Number(studentId);
    if (!Number.isFinite(sid) || sid <= 0) return;

    this.studentDocumentService.resolveStudentPhoto(sid).subscribe({
      next: (blob) => {
        if (blob) {
          this._viewPhotoBlobUrl = URL.createObjectURL(blob);
          this.viewPhotoUrl = this._viewPhotoBlobUrl;
        } else {
          this.viewPhotoUrl = null;
        }
        this.cdr.detectChanges();
      },
      error: () => { this.viewPhotoUrl = null; this.cdr.detectChanges(); }
    });

    this.loadingHistoryDocs = true;
    this.studentDocumentService.getStudentDocuments(sid).subscribe({
      next: (docs) => { this.historyDocuments = docs || []; this.loadingHistoryDocs = false; this.cdr.detectChanges(); },
      error: () => { this.historyDocuments = []; this.loadingHistoryDocs = false; this.cdr.detectChanges(); }
    });
  }

  /** Opens an archived student's document in a new tab (fetched as a blob so the JWT is sent). */
  viewHistoryDocument(doc: StudentDocument): void {
    this.studentDocumentService.viewDocument(doc.documentId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        this._docBlobUrls.push(url);
        window.open(url, '_blank');
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate(
            this.studentDocumentService.documentErrorKey(err, 'STUDENTS.MSG_DOCUMENT_VIEW_ERROR'))
        });
      }
    });
  }

  /** Business classification for a document, or a dash when the type is just a file extension. */
  documentTypeLabel(doc: StudentDocument): string {
    return this.studentDocumentService.isFormatLikeType(doc?.documentType) ? '—' : doc.documentType;
  }

  /** Translated format label (PDF / Image / Word / Other). */
  documentFormatLabel(doc: StudentDocument): string {
    return this.translationService.translate(this.studentDocumentService.getDocumentFormatLabelKey(doc));
  }

  /** Human-readable file size. */
  formatFileSize(bytes: number | null | undefined): string {
    if (bytes === null || bytes === undefined || !Number.isFinite(Number(bytes))) return '—';
    const b = Number(bytes);
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`;
    return `${(b / (1024 * 1024)).toFixed(1)} MB`;
  }

  private revokeViewPhoto(): void {
    if (this._viewPhotoBlobUrl) { URL.revokeObjectURL(this._viewPhotoBlobUrl); this._viewPhotoBlobUrl = null; }
    this._docBlobUrls.forEach(u => URL.revokeObjectURL(u));
    this._docBlobUrls = [];
  }

  loadStudents() {
    // Prevent infinite loops
    if (this.isLoadingData) {
      return;
    }

    this.isLoadingData = true;
    this.loading = true;
    this.cdr.detectChanges();
    const parsedId = parseInt(this.filterStudentId, 10);
    const studentId = this.filterStudentId && !isNaN(parsedId) ? parsedId : null;
    this.studentDataArchService.getDistinctStudentsPaginated(
      this.currentPage, this.pageSize, studentId, this.filterStudentName || undefined, this.filterPassport || undefined,
      undefined, undefined, undefined, this.sortField, this.sortDir)
      .subscribe({
        next: (response: any) => {
          this.students = this.mapToStudentDataArch(response.content || []);
          this.totalRecords = response.totalElements || 0;
          this.loading = false;
          this.isLoadingData = false;
          this.cdr.detectChanges(); // Trigger change detection
          this.runPendingFilterReload();
        },
        error: (error) => {
          console.error('Error loading distinct students:', error);
          this.students = [];
          this.totalRecords = 0;
          this.loading = false;
          this.isLoadingData = false;
          this.cdr.detectChanges(); // Trigger change detection
          this.runPendingFilterReload();
        }
      });
  }

  private mapToStudentDataArch(data: any[]): StudentDataArch[] {
    return (data || []).map(item => {
      // Copy every field straight through so nothing the API returns is silently dropped
      // (email, phone, age, currency, picturePath, studentId, insertedToArchDate, …),
      // then normalise the date fields to Date objects for the template's date pipes.
      const student = Object.assign(new StudentDataArch(), item) as StudentDataArch;
      student.studentBirthdate = item.studentBirthdate ? new Date(item.studentBirthdate) : null;
      student.studentPassportIssueDate = item.studentPassportIssueDate ? new Date(item.studentPassportIssueDate) : null;
      student.studentPassportExpireDate = item.studentPassportExpireDate ? new Date(item.studentPassportExpireDate) : null;
      student.studentMidate = item.studentMidate ? new Date(item.studentMidate) : new Date();
      student.archiveDate = item.archiveDate ? new Date(item.archiveDate) : new Date();
      student.insertedToArchDate = item.insertedToArchDate ? new Date(item.insertedToArchDate) : student.archiveDate;
      return student;
    });
  }

  /**
   * The archived full-profile snapshot, pretty-printed for display. Returns null when the
   * row carries no snapshot (historical rows predate the feature); falls back to the raw
   * string if it is not valid JSON.
   */
  get archivedProfilePretty(): string | null {
    const raw = this.selectedStudent?.profileSnapshot;
    if (!raw) return null;
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }

  onPageChange(event: any) {
    const newPage = event.first != null && event.rows ? Math.floor(event.first / event.rows) : (event.page ?? this.currentPage);
    const newSize = event.rows ?? this.pageSize;
    // p-table emits the clicked column in sortField/sortOrder (1 asc, -1 desc).
    const newSortField = event.sortField || 'id';
    const newSortDir: 'asc' | 'desc' = event.sortOrder === 1 ? 'asc' : 'desc';

    const changed = newPage !== this.currentPage
      || newSize !== this.pageSize
      || newSortField !== this.sortField
      || newSortDir !== this.sortDir;

    if (!changed) return;

    // A new sort resets to the first page so the user sees the top of the ordering.
    if (newSortField !== this.sortField || newSortDir !== this.sortDir) {
      this.currentPage = 0;
      this.first = 0;
    } else {
      this.currentPage = newPage;
      this.first = event.first ?? this.first;
    }
    this.pageSize = newSize;
    this.sortField = newSortField;
    this.sortDir = newSortDir;
    this.loadStudents();
  }

  onRowSelect(event: any) {
    this.selectedStudent = event.data;
    this.selectedDetailedRecord = null; // Reset detailed record when selecting a new student
    this.studentProgress = null;
    this.yearResults = [];
    // Progress and year results belong to the live student, keyed by the real
    // studentId — not the archive row's surrogate id.
    const realStudentId = this.selectedStudent?.studentId ?? this.selectedStudent?.id;
    if (this.selectedStudent && realStudentId != null) {
      this.loadProgress(realStudentId);
      this.loadYearResults(realStudentId);
      this.loadPhotoAndDocuments(realStudentId);
    }
    // Load all archived snapshots for the selected student (by studentId when available)
    if (this.selectedStudent && (this.selectedStudent.studentId || this.selectedStudent.studentName)) {
      this.loadFinancialRecordsForSelectedStudent();
    }
  }

  loadProgress(studentId: number): void {
    this.progressLoading = true;
    // Archived students may no longer exist live, so a 404 here is expected — probe silently.
    this.studentdataService.getProgress(studentId, true).subscribe({
      next: (p) => {
        this.studentProgress = p;
        this.progressLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.studentProgress = null;
        this.progressLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  loadYearResults(studentId: number): void {
    this.yearResultsLoading = true;
    // As with progress: a missing year-results record for an archived student is not an error.
    this.studentdataService.getYearResults(studentId, true).subscribe({
      next: (list) => {
        this.yearResults = list || [];
        this.yearResultsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.yearResults = [];
        this.yearResultsLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  onRowUnselect(_event: any) {
    this.selectedStudent = null;
    this.selectedDetailedRecord = null;
    this.selectedStudentFinancialRecords = [];
    this.studentProgress = null;
    this.yearResults = [];
    this.quarterChanges = [];
  }

  onFilterChange() {
    this.filterInput$.next();
  }

  clearFilters() {
    this.filterStudentId = '';
    this.filterStudentName = '';
    this.filterPassport = '';
    this.applyFilters();
  }

  /**
   * Reloads from the first page for the current filters. `loadStudents` refuses to start
   * while another load runs, which used to drop whatever was typed during a load; such a
   * change now waits and runs when that load ends.
   */
  private applyFilters(): void {
    const key = [this.filterStudentId, this.filterStudentName, this.filterPassport]
      .map(v => (v ?? '').trim()).join('|');
    if (key === this.lastFilterKey) return;
    if (this.isLoadingData) {
      this.filterReloadPending = true;
      return;
    }
    this.lastFilterKey = key;
    this.currentPage = 0;
    this.first = 0;
    this.loadStudents();
  }

  private runPendingFilterReload(): void {
    if (this.filterReloadPending) {
      this.filterReloadPending = false;
      this.applyFilters();
    }
  }

  refreshData() {
    this.loadStudents();
  }

  /**
   * Fetches all archived snapshots for a student, keyed by the real studentId when present
   * (the correct grouping) and only falling back to name for legacy rows that lack one.
   */
  private archRecordsFor(student: StudentDataArch | null) {
    const sid = Number(student?.studentId);
    if (Number.isFinite(sid) && sid > 0) {
      return this.studentDataArchService.getFinancialRecordsByStudentId(sid);
    }
    return this.studentDataArchService.getFinancialRecordsByStudentName(student?.studentName || '');
  }

  loadFinancialRecordsForSelectedStudent() {
    const sid = Number(this.selectedStudent?.studentId);
    const hasKey = (Number.isFinite(sid) && sid > 0) || !!this.selectedStudent?.studentName;
    if (!this.selectedStudent || !hasKey) {
      this.selectedStudentFinancialRecords = [];
      this.selectedDetailedRecord = null;
      return;
    }

    this.loadingFinancialRecords = true;

    this.archRecordsFor(this.selectedStudent)
      .subscribe({
        next: (records: any[]) => {
          this.selectedStudentFinancialRecords = this.mapToStudentDataArch(records);
          // Auto-select the first record if available
          if (this.selectedStudentFinancialRecords.length > 0 && !this.selectedDetailedRecord) {
            this.selectedDetailedRecord = this.selectedStudentFinancialRecords[0];
          }
          this.computeQuarterChanges();
          this.loadingFinancialRecords = false;
          this.cdr.detectChanges();
        },
        error: (error) => {
          console.error('Error loading duplicated records:', error);
          this.selectedStudentFinancialRecords = [];
          this.selectedDetailedRecord = null;
          this.loadingFinancialRecords = false;
          this.cdr.detectChanges();
        }
      });
  }

  onDetailedRecordSelect(record: StudentDataArch) {
    this.selectedDetailedRecord = record;
  }

  viewStudent(student: StudentDataArch) {
    this.newStudent = Object.assign(new StudentDataArch(), student);
    // Don't change selectedStudent when viewing a detailed record to maintain master-details state
    if (student.studentName && student.studentName !== this.selectedStudent?.studentName) {
      this.selectedStudent = student;
      this.loadFinancialRecordsFor(student);
    }
    this.loadPhotoAndDocuments(student.studentId ?? student.id);
    this.viewDialog = true;
  }

  hideDialog() {
    this.viewDialog = false;
    this.selectedStudentFinancialRecords = [];
    this.selectedDetailedRecord = null;
  }

  private loadFinancialRecordsFor(student: StudentDataArch) {
    this.loadingFinancialRecords = true;

    this.archRecordsFor(student)
      .subscribe({
        next: (records: any[]) => {
          this.selectedStudentFinancialRecords = this.mapToStudentDataArch(records);
          this.computeQuarterChanges();
          this.loadingFinancialRecords = false;
          this.cdr.detectChanges();
        },
        error: (error) => {
          console.error('Error loading financial records:', error);
          this.selectedStudentFinancialRecords = [];
          this.loadingFinancialRecords = false;
          this.cdr.detectChanges();
        }
      });
  }
}
