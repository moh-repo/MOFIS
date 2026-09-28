import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { StudentQuickEditDto } from '../../api/student-quick-edit';
import { StudentProgressDto } from '../../api/student-progress';
import { StudentYearResultDto, YearOutcome, yearOutcome, YEAR_OUTCOME_KEY, YEAR_OUTCOME_SEVERITY } from '../../api/student-year-result';
import { StudentQuickEditService } from '../../service/student-quick-edit.service';
import { StudentdataService } from '../../service/studentdata.service';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { LookupService } from '../../service/lookup.service';
import { isValidPhone, normalizePhone } from '../../service/phone-number.util';
import { changesOnly } from '../../service/reload-streams.util';

interface SummaryChip {
  icon: string;
  label: string;
  value: string;
  severity: 'primary' | 'info' | 'success' | 'warning' | 'danger' | 'neutral';
}

interface DetailItem {
  label: string;
  value: string;
  emphasize?: 'positive' | 'muted';
}

interface DetailSection {
  icon: string;
  title: string;
  items: DetailItem[];
}

type QuickEditFieldKey = keyof StudentQuickEditDto;

interface EditField {
  key: QuickEditFieldKey;
  label: string;
  type: 'text' | 'number' | 'textarea' | 'dropdown';
  gridClass?: string;
  min?: number;
  max?: number;
  step?: number;
  rows?: number;
  disabled?: boolean;
  useGrouping?: boolean;
  numberMode?: 'decimal' | 'currency';
  options?: { label: string; value: any }[];
  placeholder?: string;
}

interface EditSection {
  icon: string;
  title: string;
  description?: string;
  fields: EditField[];
}

@Component({
    selector: 'app-student-quick-edit',
    templateUrl: './student-quick-edit.component.html',
    styleUrls: ['./student-quick-edit.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentQuickEditComponent implements OnInit, OnDestroy {
  students: StudentQuickEditDto[] = [];
  filteredStudents: StudentQuickEditDto[] = [];
  selectedStudent: StudentQuickEditDto | null = null;
  /** The row as it was when the edit dialog opened; see `validatePhoneFields`. */
  private editOriginal: StudentQuickEditDto | null = null;
  displayEditDialog = false;
  displayViewDialog = false;
  editActiveTabIndex = 0;
  loading = false;
  studentProgress: StudentProgressDto | null = null;
  progressLoading = false;
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
  yearResultsLoading = false;

  // Pagination
  totalRecords = 0;
  rows = 20;
  first = 0;

  // Filters
  searchTerm = '';
  selectedYear: string | null = null;
  selectedGender: number | null = null;
  selectedStatus: number | null = null;

  yearOptions: { label: string; value: string | null }[] = [];
  genderFilterOptions: { label: string; value: number | null }[] = [];
  statusOptions: { label: string; value: number | null }[] = [];

  // Lookup data
  genderOptions: { label: string; value: number }[] = [];
  degreeOptions: { label: string; value: number }[] = [];
  majorOptions: { label: string; value: number }[] = [];
  levelOptions: { label: string; value: number }[] = [];
  countryOptions: { label: string; value: number }[] = [];
  governmentOptions: { label: string; value: number }[] = [];
  bankOptions: { label: string; value: number }[] = [];
  bankBranchOptions: { label: string; value: number }[] = [];
  dofOptions: { label: string; value: number }[] = [];
  universityFeeStatusOptions: { label: string; value: number }[] = [];
  finReceivedStatusOptions: { label: string; value: number }[] = [];
  receivedWhomStatusOptions: { label: string; value: number }[] = [];
  agencyTypeOptions: { label: string; value: number }[] = [];
  ministryTypeOptions: { label: string; value: number }[] = [];
  studyTypeOptions: { label: string; value: number }[] = [];
  universityTypeOptions: { label: string; value: number }[] = [];
  homeCountryOptions: { label: string; value: number }[] = [];
  finReceivedWhomOptions: { label: string; value: number }[] = [];

  lastLoadedAt: Date | null = null;

  // Cached computed values to prevent getter recalculation
  cachedViewSummaryChips: SummaryChip[] = [];
  cachedViewFinanceChips: SummaryChip[] = [];
  cachedViewDetailSections: DetailSection[] = [];
  cachedEditSummaryChips: SummaryChip[] = [];
  cachedEditFinanceChips: SummaryChip[] = [];
  cachedEditSections: EditSection[] = [];
  cachedFilteredStatusOptions: { label: string; value: number }[] = [];
  cachedStudentGenderLabel: string = '';
  cachedStudentStatusLabel: string = '';
  cachedStudentStatusSeverity: 'success' | 'info' | 'warning' | 'danger' | 'secondary' = 'secondary';

  private languageSubscription?: Subscription;

  constructor(
    private studentQuickEditService: StudentQuickEditService,
    private studentdataService: StudentdataService,
    private messageService: MessageService,
    public translationService: TranslationService,
    private permissionService: UmsPermissionService,
    private lookupService: LookupService
  ) {}

  ngOnInit(): void {
    this.languageSubscription = this.translationService.currentLanguage$.pipe(changesOnly()).subscribe(() => {
      this.prepareFilterOptions();
      this.applyFilters();
      // Quarter labels are language-aware, so re-fetch them on a language switch.
      this.loadQuarterNumbers();
    });
    this.loadMetadata();
    this.loadQuarterNumbers();
    this.loadData();
  }

  /** Quarter-number options (1–4) loaded from the database lookup table, not hardcoded. */
  private loadQuarterNumbers(): void {
    this.lookupService.getQuarterNumbers().subscribe({
      next: data => { this.quarterNumberOptions = data ?? []; },
      error: () => { this.quarterNumberOptions = []; }
    });
  }

  ngOnDestroy(): void {
    this.languageSubscription?.unsubscribe();
  }

  loadMetadata(): void {
    this.studentQuickEditService.getMetadata().subscribe({
      next: (metadata) => {
        // Map to dropdown format: { label: string, value: number }
        this.genderOptions = metadata.genders.map(g => ({ label: g.name, value: g.id }));
        this.statusOptions = [
          { label: this.translationService.translate('STUDENT_QUICK_EDIT.FILTERS.ALL_STATUSES'), value: null },
          ...metadata.statuses.map(s => ({ label: s.name, value: s.id }))
        ];
        this.degreeOptions = metadata.degrees.map(d => ({ label: d.name, value: d.id }));
        this.majorOptions = metadata.majors.map(m => ({ label: m.name, value: m.id }));
        this.levelOptions = metadata.levels.map(l => ({ label: l.name, value: l.id }));
        const allCountries = metadata.countries.map(c => ({ label: c.name, value: c.id }));
        const allowedIds = this.permissionService.getAllowedCountryIds();
        this.countryOptions = allowedIds.length > 0
          ? allCountries.filter(c => allowedIds.includes(c.value))
          : allCountries;
        this.governmentOptions = metadata.governments.map(g => ({ label: g.name, value: g.id }));
        this.bankOptions = metadata.banks.map(b => ({ label: b.name, value: b.id }));
        this.bankBranchOptions = metadata.bankBranches.map(b => ({ label: b.name, value: b.id }));
        this.dofOptions = metadata.dofs.map(d => ({ label: d.name, value: d.id }));
        this.universityFeeStatusOptions = metadata.universityFeeStatuses.map(s => ({ label: s.name, value: s.id }));
        this.finReceivedStatusOptions = metadata.finReceivedStatuses.map(s => ({ label: s.name, value: s.id }));
        this.receivedWhomStatusOptions = metadata.receivedWhomStatuses.map(s => ({ label: s.name, value: s.id }));
        this.agencyTypeOptions = (metadata.agencyTypes || []).map(t => ({ label: t.name, value: t.id }));
        this.ministryTypeOptions = (metadata.ministryTypes || []).map(t => ({ label: t.name, value: t.id }));
        this.studyTypeOptions = (metadata.studyTypes || []).map(t => ({ label: t.name, value: t.id }));
        this.universityTypeOptions = (metadata.universityTypes || []).map(t => ({ label: t.name, value: t.id }));
        this.homeCountryOptions = (metadata.homeCountries || []).map(c => ({ label: c.name, value: c.id }));
        this.finReceivedWhomOptions = (metadata.finReceivedWhoms || []).map(w => ({ label: w.name, value: w.id }));

        this.prepareFilterOptions();
      },
      error: (error) => {
        console.error('Error loading metadata:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_QUICK_EDIT.MESSAGES.LOAD_METADATA_FAILED')
        });
      }
    });
  }

  loadData(): void {
    this.loading = true;

    this.studentQuickEditService.getAll().subscribe({
      next: (data: StudentQuickEditDto[]) => {
        this.students = data || [];
        this.prepareFilterOptions();
        this.applyFilters();
        this.lastLoadedAt = new Date();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading students:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_QUICK_EDIT.MESSAGES.LOAD_ERROR')
        });
        this.loading = false;
      }
    });
  }

  viewStudent(student: StudentQuickEditDto): void {
    this.selectedStudent = { ...student };
    this.computeViewData();
    this.studentProgress = null;
    this.yearResults = [];
    this.loadProgress(student.studentId);
    this.loadYearResults(student.studentId);
    this.displayViewDialog = true;
  }

  loadYearResults(studentId: number): void {
    this.yearResultsLoading = true;
    this.studentdataService.getYearResults(studentId).subscribe({
      next: (list) => {
        this.yearResults = list || [];
        this.yearResultsLoading = false;
      },
      error: () => {
        this.yearResults = [];
        this.yearResultsLoading = false;
      }
    });
  }

  loadProgress(studentId: number): void {
    this.progressLoading = true;
    this.studentdataService.getProgress(studentId).subscribe({
      next: (p) => {
        this.studentProgress = p;
        this.progressLoading = false;
      },
      error: () => {
        this.studentProgress = null;
        this.progressLoading = false;
      }
    });
  }

  editStudent(student: StudentQuickEditDto): void {
    this.editOriginal = { ...student };
    this.selectedStudent = { ...student };
    this.editActiveTabIndex = 0;
    this.cacheStudentLabels();
    this.computeEditData();
    this.displayEditDialog = true;
  }

  editFromView(): void {
    this.displayViewDialog = false;
    this.editActiveTabIndex = 0;
    this.cacheStudentLabels();
    this.computeEditData();
    this.displayEditDialog = true;
  }

  private cacheStudentLabels(): void {
    if (!this.selectedStudent) return;
    this.cachedStudentGenderLabel = this.getGenderLabel(this.selectedStudent.studentGender);
    this.cachedStudentStatusLabel = this.getStatusLabel(this.selectedStudent.studentStudyStatusId);
    this.cachedStudentStatusSeverity = this.getStatusSeverity(this.selectedStudent.studentStudyStatusId);
  }

  closeViewDialog(): void {
    this.displayViewDialog = false;
    this.selectedStudent = null;
  }

  applyFilters(): void {
    const term = this.searchTerm.trim().toLowerCase();

    this.filteredStudents = this.students.filter(student => {
      const matchesSearch = term
        ? [
          student.studentId,
          student.studentName,
          student.studentNote1,
          student.studentNote2,
          student.studentFinName
        ]
          .filter(Boolean)
          .some(field =>
            field!.toString().toLowerCase().includes(term)
          )
        : true;

      const matchesYear = this.selectedYear !== null
        ? (student.studentDelegationDecisionYear ?? '') === this.selectedYear
        : true;

      const matchesGender = this.selectedGender !== null
        ? student.studentGender === this.selectedGender
        : true;

      const matchesStatus = this.selectedStatus !== null
        ? student.studentStudyStatusId === this.selectedStatus
        : true;

      return matchesSearch && matchesYear && matchesGender && matchesStatus;
    });

    this.totalRecords = this.filteredStudents.length;
    this.first = 0;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedYear = null;
    this.selectedGender = null;
    this.selectedStatus = null;
    this.applyFilters();
  }

  saveStudent(): void {
    if (!this.selectedStudent || !this.selectedStudent.studentId) {
      return;
    }
    if (!this.validatePhoneFields(this.selectedStudent)) {
      return;
    }

    this.loading = true;
    this.studentQuickEditService.update(this.selectedStudent.studentId, this.selectedStudent).subscribe({
      next: (res: any) => {
        this.loading = false;
        this.displayEditDialog = false;

        // Quick edits are queued for approval; the live row is not changed here — it updates
        // only once the request is approved. So the table is intentionally left as-is.
        if (res?.status === 'NO_CHANGES') {
          this.messageService.add({
            severity: 'info',
            summary: this.translationService.translate('COMMON.INFO'),
            detail: this.translationService.translate('STUDENT_QUICK_EDIT.MESSAGES.NO_CHANGES')
          });
          return;
        }

        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENT_QUICK_EDIT.MESSAGES.SUBMITTED_FOR_APPROVAL')
        });
      },
      error: (error) => {
        console.error('Error updating student:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENT_QUICK_EDIT.MESSAGES.SAVE_ERROR')
        });
        this.loading = false;
      }
    });
  }

  /**
   * Same rule as the add wizard (`phone-number.util`), which the server applies too; a
   * failure here names the field instead of coming back as a generic save error. Like the
   * server, only numbers changed in this edit are checked, so an old number nobody touched
   * does not block an unrelated change.
   */
  private validatePhoneFields(student: StudentQuickEditDto): boolean {
    const t = this.translationService;
    const fields: { key: 'studentCellphone' | 'studentHomeNumber' | 'studentEmergencyContact'; label: string; mobile: boolean }[] = [
      { key: 'studentCellphone', label: 'STUDENT_QUICK_EDIT.FORM.CELLPHONE', mobile: true },
      { key: 'studentHomeNumber', label: 'STUDENT_QUICK_EDIT.FORM.HOME_NUMBER', mobile: false },
      { key: 'studentEmergencyContact', label: 'STUDENT_QUICK_EDIT.FORM.EMERGENCY_CONTACT', mobile: false }
    ];
    const failures: string[] = [];
    for (const f of fields) {
      if (normalizePhone(student[f.key]) === normalizePhone(this.editOriginal?.[f.key])) {
        continue;
      }
      student[f.key] = normalizePhone(student[f.key]);
      if (!isValidPhone(student[f.key], f.mobile)) {
        failures.push(`${t.translate(f.label)}: ${t.translate(f.mobile ? 'STUDENT_ADD.MOBILE_INVALID' : 'STUDENT_ADD.PHONE_INVALID')}`);
      }
    }
    if (failures.length) {
      this.messageService.add({
        severity: 'error',
        summary: t.translate('STUDENT_ADD.CONTACT_VALIDATION_ERROR'),
        detail: failures.join('\n'),
        life: 10000
      });
    }
    return failures.length === 0;
  }

  cancelEdit(): void {
    this.displayEditDialog = false;
    this.editActiveTabIndex = 0;
    this.selectedStudent = null;
  }

  /**
   * 0 is how the data stores "not recorded" — most of the dev students — and it
   * read "Unknown" beside an otherwise complete record (QA B5, UI-001). Unknown is
   * kept for a real id the list does not hold.
   */
  getGenderLabel(gender: number | undefined): string {
    if (gender === null || gender === undefined || Number(gender) <= 0) {
      return this.translationService.translate('COMMON.UNSPECIFIED');
    }
    const genderOption = this.genderOptions.find(g => g.value === gender);
    return genderOption ? genderOption.label : this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.UNKNOWN');
  }


  getQuarterLabel(quarter: number | undefined): string {
    if (!quarter) {
      return this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.NO_QUARTER');
    }
    return `${this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.QUARTER')} ${quarter}`;
  }

  /** Display label for a CURRENT_QUARTER code (YYYYQ), e.g. '20242' → "2024 · Q2". */
  getCurrentQuarterLabel(code: string | undefined): string {
    const val = Number(code);
    if (!val || val <= 0) return this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.NO_QUARTER');
    const q = val % 10;
    const year = Math.floor(val / 10);
    if (q < 1 || q > 4 || year < 1990 || year > 2099) return String(code);
    return `${year} · ${this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.QUARTER')} ${q}`;
  }

  // ── Current-quarter composite (year + quarter number ⇄ 'YYYYQ' string) ──────
  currentQuarterYear: number | null = null;
  currentQuarterQ: number | null = null;
  /** Populated from the DB quarter-number lookup (see loadQuarterNumbers). */
  quarterNumberOptions: { label: string; value: number }[] = [];

  /** Composes the year + quarter inputs back into the model's YYYYQ string. */
  onCurrentQuarterChange(): void {
    if (!this.selectedStudent) return;
    const y = this.currentQuarterYear;
    const q = this.currentQuarterQ;
    this.selectedStudent.currentQuarter =
      (y && q && y >= 1990 && y <= 2099 && q >= 1 && q <= 4) ? String(y * 10 + q) : '';
  }

  /** Splits the model's YYYYQ string into the year + quarter inputs for editing. */
  private syncCurrentQuarterSplit(): void {
    const val = Number(this.selectedStudent?.currentQuarter);
    if (val && val > 0) {
      const q = val % 10;
      const year = Math.floor(val / 10);
      if (q >= 1 && q <= 4 && year >= 1990 && year <= 2099) {
        this.currentQuarterYear = year;
        this.currentQuarterQ = q;
        return;
      }
    }
    this.currentQuarterYear = null;
    this.currentQuarterQ = null;
  }

  getStatusLabel(status: number | undefined): string {
    if (status === null || status === undefined) {
      return this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.STATUS_UNKNOWN');
    }
    const statusOption = this.statusOptions.find(s => s.value === status);
    return statusOption ? statusOption.label : `${this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.STATUS')} ${status}`;
  }

  getDegreeLabel(degreeId: number | null | undefined): string {
    if (degreeId === null || degreeId === undefined) return '---';
    const degree = this.degreeOptions.find(d => d.value === degreeId);
    return degree ? degree.label : `ID: ${degreeId}`;
  }

  getMajorLabel(majorId: number | null | undefined): string {
    if (majorId === null || majorId === undefined) return '---';
    const major = this.majorOptions.find(m => m.value === majorId);
    return major ? major.label : `ID: ${majorId}`;
  }

  getLevelLabel(levelId: number | null | undefined): string {
    if (levelId === null || levelId === undefined) return '---';
    const level = this.levelOptions.find(l => l.value === levelId);
    return level ? level.label : `ID: ${levelId}`;
  }

  getCountryLabel(countryId: number | null | undefined): string {
    if (countryId === null || countryId === undefined) return '---';
    const country = this.countryOptions.find(c => c.value === countryId);
    return country ? country.label : `ID: ${countryId}`;
  }

  getGovernmentLabel(govId: number | null | undefined): string {
    if (govId === null || govId === undefined) return '---';
    const gov = this.governmentOptions.find(g => g.value === govId);
    return gov ? gov.label : `ID: ${govId}`;
  }

  getBankLabel(bankId: number | null | undefined): string {
    if (bankId === null || bankId === undefined) return '---';
    const bank = this.bankOptions.find(b => b.value === bankId);
    return bank ? bank.label : `ID: ${bankId}`;
  }

  getBankBranchLabel(branchId: number | null | undefined): string {
    if (branchId === null || branchId === undefined) return '---';
    const branch = this.bankBranchOptions.find(b => b.value === branchId);
    return branch ? branch.label : `ID: ${branchId}`;
  }

  getDofLabel(dofId: number | null | undefined): string {
    if (dofId === null || dofId === undefined) return '---';
    const dof = this.dofOptions.find(d => d.value === dofId);
    return dof ? dof.label : `ID: ${dofId}`;
  }

  getUniversityFeeStatusLabel(statusId: number | null | undefined): string {
    if (statusId === null || statusId === undefined) return '---';
    const status = this.universityFeeStatusOptions.find(s => s.value === statusId);
    return status ? status.label : `ID: ${statusId}`;
  }

  getFinReceivedStatusLabel(statusId: number | null | undefined): string {
    if (statusId === null || statusId === undefined) return '---';
    const status = this.finReceivedStatusOptions.find(s => s.value === statusId);
    return status ? status.label : `ID: ${statusId}`;
  }

  getReceivedWhomStatusLabel(statusId: number | null | undefined): string {
    if (statusId === null || statusId === undefined) return '---';
    const status = this.receivedWhomStatusOptions.find(s => s.value === statusId);
    return status ? status.label : `ID: ${statusId}`;
  }

  getAgencyTypeLabel(id: number | null | undefined): string {
    if (id === null || id === undefined) return '---';
    const t = this.agencyTypeOptions.find(o => o.value === id);
    return t ? t.label : `ID: ${id}`;
  }

  getMinistryTypeLabel(id: number | null | undefined): string {
    if (id === null || id === undefined) return '---';
    const t = this.ministryTypeOptions.find(o => o.value === id);
    return t ? t.label : `ID: ${id}`;
  }

  getStudyTypeLabel(id: number | null | undefined): string {
    if (id === null || id === undefined) return '---';
    const t = this.studyTypeOptions.find(o => o.value === id);
    return t ? t.label : `ID: ${id}`;
  }

  getUniversityTypeLabel(id: number | null | undefined): string {
    if (id === null || id === undefined) return '---';
    const t = this.universityTypeOptions.find(o => o.value === id);
    return t ? t.label : `ID: ${id}`;
  }

  getHomeCountryLabel(id: number | string | null | undefined): string {
    if (id === null || id === undefined || id === '') return '---';
    const numId = typeof id === 'string' ? parseInt(id, 10) : id;
    const c = this.homeCountryOptions.find(o => o.value === numId);
    return c ? c.label : `ID: ${id}`;
  }

  getFinReceivedWhomLabel(id: number | null | undefined): string {
    if (id === null || id === undefined) return '---';
    const w = this.finReceivedWhomOptions.find(o => o.value === id);
    return w ? w.label : `ID: ${id}`;
  }

  getStatusSeverity(status: number | undefined): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (status) {
      case 1:
        return 'info';
      case 2:
        return 'success';
      case 3:
        return 'warning';
      case 4:
        return 'info';
      case 6:
      case 7:
        return 'danger';
      default:
        return 'secondary';
    }
  }

  get activeFiltersCount(): number {
    let count = 0;
    if (this.searchTerm) count++;
    if (this.selectedYear !== null) count++;
    if (this.selectedGender !== null) count++;
    if (this.selectedStatus !== null) count++;
    return count;
  }

  get filterResultsLabel(): string {
    return this.formatMessage('STUDENT_QUICK_EDIT.FILTERS.RESULTS', {
      count: this.formatNumber(this.filteredStudents.length),
      total: this.formatNumber(this.students.length)
    });
  }

  get tableCaptionLabel(): string {
    return this.formatMessage('STUDENT_QUICK_EDIT.TABLE.CAPTION', {
      count: this.formatNumber(this.filteredStudents.length)
    });
  }

  get activeFiltersLabel(): string | null {
    if (this.activeFiltersCount === 0) {
      return null;
    }
    return this.formatMessage('STUDENT_QUICK_EDIT.TABLE.ACTIVE_FILTERS', {
      count: this.formatNumber(this.activeFiltersCount)
    });
  }

  get lastUpdatedLabel(): string {
    if (!this.lastLoadedAt) {
      return this.translationService.translate('STUDENT_QUICK_EDIT.SUMMARY.NO_REFRESH');
    }
    return this.formatDateTime(this.lastLoadedAt);
  }

  get viewSummaryChips(): SummaryChip[] {
    return this.cachedViewSummaryChips;
  }

  get viewFinanceChips(): SummaryChip[] {
    return this.cachedViewFinanceChips;
  }

  get editSummaryChips(): SummaryChip[] {
    return this.cachedEditSummaryChips;
  }

  get editFinanceChips(): SummaryChip[] {
    return this.cachedEditFinanceChips;
  }

  private computeViewData(): void {
    if (!this.selectedStudent) {
      this.cachedViewSummaryChips = [];
      this.cachedViewFinanceChips = [];
      this.cachedViewDetailSections = [];
      return;
    }

    const statusSeverity = this.mapStatusSeverity(this.getStatusSeverity(this.selectedStudent.studentStudyStatusId));

    this.cachedViewSummaryChips = [
      {
        icon: 'pi pi-id-card',
        label: this.translationService.translate('STUDENT_QUICK_EDIT.FORM.STUDENT_ID'),
        value: `#${this.selectedStudent.studentId ?? '---'}`,
        severity: 'info'
      },
      {
        icon: 'pi pi-user',
        label: this.translationService.translate('STUDENT_QUICK_EDIT.FORM.STUDENT_NAME'),
        value: this.formatNullable(this.selectedStudent.studentName),
        severity: 'primary'
      },
      {
        icon: 'pi pi-check-circle',
        label: this.translationService.translate('STUDENT_QUICK_EDIT.TABLE.STATUS'),
        value: this.getStatusLabel(this.selectedStudent.studentStudyStatusId),
        severity: statusSeverity
      },
      {
        icon: 'pi pi-calendar',
        label: this.translationService.translate('STUDENT_QUICK_EDIT.FORM.DELEGATION_YEAR'),
        value: this.formatNullable(this.selectedStudent.studentDelegationDecisionYear),
        severity: 'neutral'
      }
    ];

    this.cachedViewFinanceChips = [
      {
        icon: 'pi pi-chart-line',
        label: this.translationService.translate('STUDENT_QUICK_EDIT.TABLE.AWARD'),
        value: this.formatAmount(this.selectedStudent.studentAmount),
        severity: 'success'
      },
      {
        icon: 'pi pi-briefcase',
        label: this.translationService.translate('STUDENT_QUICK_EDIT.TABLE.FINANCE'),
        value: this.formatAmount(this.selectedStudent.studentFinRecievedAmount),
        severity: 'info'
      },
      {
        icon: 'pi pi-clock',
        label: this.translationService.translate('STUDENT_QUICK_EDIT.TABLE.FIN_YEAR'),
        value: this.formatNullable(this.selectedStudent.studentFinRecievedYear),
        severity: 'neutral'
      }
    ];

    this.computeViewDetailSections();
  }

  private computeEditData(): void {
    this.computeViewData();
    this.syncCurrentQuarterSplit();
    this.cachedEditSummaryChips = [...this.cachedViewSummaryChips];
    this.cachedEditFinanceChips = [...this.cachedViewFinanceChips];
    this.computeEditSections();
  }

  get viewDetailSections(): DetailSection[] {
    return this.cachedViewDetailSections;
  }

  get editSections(): EditSection[] {
    return this.cachedEditSections;
  }

  get filteredStatusOptions() {
    return this.cachedFilteredStatusOptions;
  }

  private computeViewDetailSections(): void {
    if (!this.selectedStudent) {
      this.cachedViewDetailSections = [];
      return;
    }

    const student = this.selectedStudent;
    const t = this.translationService;

    const studentSection: DetailSection = {
      icon: 'pi pi-user',
      title: t.translate('STUDENT_QUICK_EDIT.VIEW.STUDENT_SECTION'),
      items: [
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDENT_NAME'), value: this.formatNullable(student.studentName) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDENT_ID'), value: this.wrapIdentifier(student.studentId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.GENDER'), value: this.getGenderLabel(student.studentGender) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.PASSPORT'), value: this.formatNullable(student.studentPassportNumber) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.PASSPORT_ISSUE_COUNTRY'), value: this.getHomeCountryLabel(student.studentPassportIssueCountryId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.BIRTHDATE_PLACE'), value: this.getHomeCountryLabel(student.studentBirthdatePlace) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.NOTE1'), value: this.formatNullable(student.studentNote1), emphasize: 'muted' },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.NOTE2'), value: this.formatNullable(student.studentNote2), emphasize: 'muted' }
      ]
    };

    const contactSection: DetailSection = {
      icon: 'pi pi-phone',
      title: t.translate('STUDENT_QUICK_EDIT.VIEW.CONTACT_SECTION'),
      items: [
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.EMAIL'), value: this.formatNullable(student.studentEmail) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.CELLPHONE'), value: this.formatNullable(student.studentCellphone) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.HOME_NUMBER'), value: this.formatNullable(student.studentHomeNumber) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.EMERGENCY_CONTACT'), value: this.formatNullable(student.studentEmergencyContact) }
      ]
    };

    const academicSection: DetailSection = {
      icon: 'pi pi-graduation-cap',
      title: t.translate('STUDENT_QUICK_EDIT.VIEW.ACADEMIC_SECTION'),
      items: [
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.DELEGATION_YEAR'), value: this.formatNullable(student.studentDelegationDecisionYear) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.DEGREE'), value: this.getDegreeLabel(student.studentDegreeId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.MAJOR'), value: this.getMajorLabel(student.studentMajorId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.CURRENT_LEVEL'), value: this.getLevelLabel(student.studentCurrentLevelId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDY_YEARS'), value: this.formatNullable(student.studentStudyNoOfYears) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.QUARTER'), value: this.getQuarterLabel(student.studentAddedQuarter) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.QUARTER_LAST'), value: this.getQuarterLabel(student.studentLastQuarter) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.CURRENT_QUARTER'), value: this.getCurrentQuarterLabel(student.currentQuarter) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.AGENCY_TYPE'), value: this.getAgencyTypeLabel(student.studentAgencyTypeId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.MINISTRY_TYPE'), value: this.getMinistryTypeLabel(student.studentMinistryTypeId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDY_TYPE'), value: this.getStudyTypeLabel(student.studentStudyTypeId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.UNIVERSITY_TYPE'), value: this.getUniversityTypeLabel(student.studentUniversityTypeId) }
      ]
    };

    const financeSection: DetailSection = {
      icon: 'pi pi-briefcase',
      title: t.translate('STUDENT_QUICK_EDIT.VIEW.FINANCE_SECTION'),
      items: [
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.AMOUNT'), value: this.formatAmount(student.studentAmount), emphasize: 'positive' },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.UNIVERSITY_FEE_STATUS'), value: this.getUniversityFeeStatusLabel(student.studyUniversityFeeStatusId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.FIN_ID'), value: this.formatNullable(student.studentFinId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.FIN_NAME'), value: this.formatNullable(student.studentFinName) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_YEAR'), value: this.formatNullable(student.studentFinRecievedYear) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_MONTH'), value: this.formatNullable(student.studentFinRecievedMonth) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_AMOUNT'), value: this.formatAmount(student.studentFinRecievedAmount) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.FINANCE_RECEIVED_STATUS'), value: this.getFinReceivedStatusLabel(student.studyFinRecievedStatus) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_WHOM_STATUS'), value: this.getReceivedWhomStatusLabel(student.studyReceivedWhomStatusId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_WHOM'), value: this.getFinReceivedWhomLabel(student.studyReceivedWhomId) }
      ]
    };

    const locationSection: DetailSection = {
      icon: 'pi pi-compass',
      title: t.translate('STUDENT_QUICK_EDIT.VIEW.LOCATION_SECTION'),
      items: [
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.COUNTRY'), value: this.getCountryLabel(student.studyCountryInId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.GOVERNMENT'), value: this.getGovernmentLabel(student.studyGovInId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.BANK'), value: this.getBankLabel(student.studyFinanceBankeId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.BRANCH'), value: this.getBankBranchLabel(student.studyFinanceBankBranchId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.DOF'), value: this.getDofLabel(student.studyFinanceDofId) },
        { label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDY_NOTE'), value: this.formatNullable(student.studyNote1), emphasize: 'muted' }
      ]
    };

    this.cachedViewDetailSections = [studentSection, contactSection, academicSection, financeSection, locationSection];
  }

  private computeEditSections(): void {
    if (!this.selectedStudent) {
      this.cachedEditSections = [];
      return;
    }

    const t = this.translationService;
    const statusOptions = this.statusOptions
      .filter(option => option.value !== null) as { label: string; value: number }[];
    const genderDropdownOptions = [
      { label: t.translate('STUDENT_QUICK_EDIT.LABELS.UNKNOWN'), value: null },
      ...this.genderOptions
    ];

    this.cachedEditSections = [
      {
        icon: 'pi pi-user-edit',
        title: t.translate('STUDENT_QUICK_EDIT.EDIT.PRIMARY_SECTION'),
        description: t.translate('STUDENT_QUICK_EDIT.EDIT.PRIMARY_HINT'),
        fields: [
          { key: 'studentName', label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDENT_NAME'), type: 'text' },
          { key: 'studentId', label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDENT_ID'), type: 'text', disabled: true, gridClass: 'col-12 md:col-6 lg:col-4' },
          {
            key: 'studentGender',
            label: t.translate('STUDENT_QUICK_EDIT.FORM.GENDER'),
            type: 'dropdown',
            options: genderDropdownOptions,
            placeholder: t.translate('STUDENT_QUICK_EDIT.FILTERS.GENDER_PLACEHOLDER'),
            gridClass: 'col-12 md:col-6 lg:col-4'
          },
          { key: 'studentDelegationDecisionYear', label: t.translate('STUDENT_QUICK_EDIT.FORM.DELEGATION_YEAR'), type: 'text', gridClass: 'col-12 md:col-6 lg:col-4' },
          {
            key: 'studentStudyStatusId',
            label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDY_STATUS'),
            type: 'dropdown',
            options: statusOptions,
            placeholder: t.translate('STUDENT_QUICK_EDIT.FILTERS.STATUS_PLACEHOLDER'),
            gridClass: 'col-12 md:col-6 lg:col-4'
          },
          { key: 'studentAddedQuarter', label: t.translate('STUDENT_QUICK_EDIT.FORM.QUARTER_ADDED'), type: 'number', min: 19901, max: 20994, step: 1, gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentLastQuarter', label: t.translate('STUDENT_QUICK_EDIT.FORM.QUARTER_LAST'), type: 'number', min: 19901, max: 20994, step: 1, gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentPassportIssueCountryId', label: t.translate('STUDENT_QUICK_EDIT.FORM.PASSPORT_ISSUE_COUNTRY'), type: 'dropdown', options: this.homeCountryOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_HOME_COUNTRY'), gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentBirthdatePlace', label: t.translate('STUDENT_QUICK_EDIT.FORM.BIRTHDATE_PLACE'), type: 'text', gridClass: 'col-12 md:col-6 lg:col-4' }
        ]
      },
      {
        icon: 'pi pi-graduation-cap',
        title: t.translate('STUDENT_QUICK_EDIT.EDIT.ACADEMIC_SECTION'),
        description: t.translate('STUDENT_QUICK_EDIT.EDIT.ACADEMIC_HINT'),
        fields: [
          { key: 'studentDegreeId', label: t.translate('STUDENT_QUICK_EDIT.FORM.DEGREE'), type: 'dropdown', options: this.degreeOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_DEGREE'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentMajorId', label: t.translate('STUDENT_QUICK_EDIT.FORM.MAJOR'), type: 'dropdown', options: this.majorOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_MAJOR'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentCurrentLevelId', label: t.translate('STUDENT_QUICK_EDIT.FORM.CURRENT_LEVEL'), type: 'dropdown', options: this.levelOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_LEVEL'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentStudyNoOfYears', label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDY_YEARS'), type: 'number', min: 0, step: 1, gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentAgencyTypeId', label: t.translate('STUDENT_QUICK_EDIT.FORM.AGENCY_TYPE'), type: 'dropdown', options: this.agencyTypeOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_AGENCY_TYPE'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentMinistryTypeId', label: t.translate('STUDENT_QUICK_EDIT.FORM.MINISTRY_TYPE'), type: 'dropdown', options: this.ministryTypeOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_MINISTRY_TYPE'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentStudyTypeId', label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDY_TYPE'), type: 'dropdown', options: this.studyTypeOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_STUDY_TYPE'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentUniversityTypeId', label: t.translate('STUDENT_QUICK_EDIT.FORM.UNIVERSITY_TYPE'), type: 'dropdown', options: this.universityTypeOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_UNIVERSITY_TYPE'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studentNote1', label: t.translate('STUDENT_QUICK_EDIT.FORM.NOTE1'), type: 'textarea', rows: 3 },
          { key: 'studentNote2', label: t.translate('STUDENT_QUICK_EDIT.FORM.NOTE2'), type: 'textarea', rows: 3 }
        ]
      },
      {
        icon: 'pi pi-briefcase',
        title: t.translate('STUDENT_QUICK_EDIT.EDIT.FINANCE_SECTION'),
        description: t.translate('STUDENT_QUICK_EDIT.EDIT.FINANCE_HINT'),
        fields: [
          { key: 'studentAmount', label: t.translate('STUDENT_QUICK_EDIT.FORM.AMOUNT'), type: 'number', numberMode: 'decimal', gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentFinId', label: t.translate('STUDENT_QUICK_EDIT.FORM.FIN_ID'), type: 'text', gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentFinName', label: t.translate('STUDENT_QUICK_EDIT.FORM.FIN_NAME'), type: 'text', gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentFinRecievedYear', label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_YEAR'), type: 'number', min: 1900, max: 2100, step: 1, gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentFinRecievedMonth', label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_MONTH'), type: 'number', min: 1, max: 12, step: 1, gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studentFinRecievedAmount', label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_AMOUNT'), type: 'number', numberMode: 'decimal', gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studyFinRecievedStatus', label: t.translate('STUDENT_QUICK_EDIT.FORM.FINANCE_RECEIVED_STATUS'), type: 'dropdown', options: this.finReceivedStatusOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_FIN_STATUS'), gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studyReceivedWhomStatusId', label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_WHOM_STATUS'), type: 'dropdown', options: this.receivedWhomStatusOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_WHOM_STATUS'), gridClass: 'col-12 md:col-6 lg:col-4' },
          { key: 'studyReceivedWhomId', label: t.translate('STUDENT_QUICK_EDIT.FORM.RECEIVED_WHOM'), type: 'dropdown', options: this.finReceivedWhomOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_RECEIVED_WHOM'), gridClass: 'col-12 md:col-6 lg:col-4' }
        ]
      },
      {
        icon: 'pi pi-compass',
        title: t.translate('STUDENT_QUICK_EDIT.EDIT.LOCATION_SECTION'),
        description: t.translate('STUDENT_QUICK_EDIT.EDIT.LOCATION_HINT'),
        fields: [
          { key: 'studyCountryInId', label: t.translate('STUDENT_QUICK_EDIT.FORM.COUNTRY'), type: 'dropdown', options: this.countryOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_COUNTRY'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studyGovInId', label: t.translate('STUDENT_QUICK_EDIT.FORM.GOVERNMENT'), type: 'dropdown', options: this.governmentOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_GOVERNMENT'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studyFinanceBankeId', label: t.translate('STUDENT_QUICK_EDIT.FORM.BANK'), type: 'dropdown', options: this.bankOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_BANK'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studyFinanceBankBranchId', label: t.translate('STUDENT_QUICK_EDIT.FORM.BRANCH'), type: 'dropdown', options: this.bankBranchOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_BRANCH'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studyFinanceDofId', label: t.translate('STUDENT_QUICK_EDIT.FORM.DOF'), type: 'dropdown', options: this.dofOptions, placeholder: t.translate('STUDENT_QUICK_EDIT.FORM.SELECT_DOF'), gridClass: 'col-12 md:col-6 lg:col-3' },
          { key: 'studyNote1', label: t.translate('STUDENT_QUICK_EDIT.FORM.STUDY_NOTE'), type: 'textarea', rows: 3 }
        ]
      },
      {
        icon: 'pi pi-phone',
        title: t.translate('STUDENT_QUICK_EDIT.EDIT.CONTACT_SECTION'),
        description: t.translate('STUDENT_QUICK_EDIT.EDIT.CONTACT_HINT'),
        fields: [
          { key: 'studentEmail', label: t.translate('STUDENT_QUICK_EDIT.FORM.EMAIL'), type: 'text', gridClass: 'col-12 md:col-6' },
          { key: 'studentCellphone', label: t.translate('STUDENT_QUICK_EDIT.FORM.CELLPHONE'), type: 'text', gridClass: 'col-12 md:col-6' },
          { key: 'studentHomeNumber', label: t.translate('STUDENT_QUICK_EDIT.FORM.HOME_NUMBER'), type: 'text', gridClass: 'col-12 md:col-6' },
          { key: 'studentEmergencyContact', label: t.translate('STUDENT_QUICK_EDIT.FORM.EMERGENCY_CONTACT'), type: 'text', gridClass: 'col-12 md:col-6' }
        ]
      }
    ];
  }


  get latestFinanceYear(): string {
    const years = this.filteredStudents
      .map(student => student.studentFinRecievedYear)
      .filter((year): year is number => year !== undefined && year !== null);
    if (!years.length) {
      return this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.NOT_AVAILABLE');
    }
    return Math.max(...years).toString();
  }

  private prepareFilterOptions(): void {
    const yearSet = new Set<string>();

    this.students.forEach(student => {
      if (student.studentDelegationDecisionYear && student.studentDelegationDecisionYear.trim().length > 0) {
        yearSet.add(student.studentDelegationDecisionYear);
      }
    });

    const sortedYears = Array.from(yearSet).sort((a, b) => b.localeCompare(a));

    this.yearOptions = [
      { label: this.translationService.translate('STUDENT_QUICK_EDIT.FILTERS.ALL_YEARS'), value: null },
      ...sortedYears.map(year => ({ label: year, value: year }))
    ];

    this.genderFilterOptions = [
      { label: this.translationService.translate('STUDENT_QUICK_EDIT.FILTERS.ALL_GENDERS'), value: null },
      ...this.genderOptions
    ];

    // Cache filtered status options for edit dialog (only non-null values)
    this.cachedFilteredStatusOptions = this.statusOptions.filter(option => option.value !== null) as { label: string; value: number }[];
  }

  private formatNumber(value: number, fractionDigits = 0): string {
    const formatter = new Intl.NumberFormat(this.locale, {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits
    });
    return formatter.format(value);
  }

  private formatAmount(value: number | null | undefined): string {
    const amount = value ?? 0;
    return this.formatNumber(amount);
  }

  private formatMessage(key: string, replacements: Record<string, string | number>): string {
    let message = this.translationService.translate(key);
    Object.entries(replacements).forEach(([placeholder, value]) => {
      message = message.replace(`{${placeholder}}`, String(value));
    });
    return message;
  }

  private formatDateTime(date: Date): string {
    return new Intl.DateTimeFormat(this.locale, {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(date);
  }

  private formatNullable(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.NOT_AVAILABLE');
    }
    return value.toString();
  }

  private wrapIdentifier(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return this.translationService.translate('STUDENT_QUICK_EDIT.LABELS.NOT_AVAILABLE');
    }
    return `#${value}`;
  }

  private mapStatusSeverity(severity: 'success' | 'info' | 'warning' | 'danger' | 'secondary'): SummaryChip['severity'] {
    return severity === 'secondary' ? 'neutral' : severity;
  }

  private get locale(): string {
    const lang = this.translationService.getCurrentLanguage();
    return lang === 'ar' ? 'ar-LY' : 'en-US';
  }
}

