import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { ApproveAndApplyOutcome, StudentInfoRequestService, StudentInfoRequestDto } from '../../service/student-info-request.service';
import { MessageService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { StudentdataService } from '../../service/studentdata.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { changesOnly } from '../../service/reload-streams.util';

export interface StudentOption {
  studentId: number;
  studentName: string;
  label?: string;
}

@Component({
    selector: 'app-student-info-requests',
    templateUrl: './student-info-requests.component.html',
    styleUrls: ['./student-info-requests.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentInfoRequestsComponent implements OnInit, OnDestroy {

  private destroy$ = new Subject<void>();

  requests: StudentInfoRequestDto[] = [];
  totalElements = 0;
  totalPages = 0;
  page = 0;
  size = 20;
  loading = false;
  filterStatus = '';
  filterRequestType = '';
  filterStudentId: number | null = null;

  showCreateDialog = false;
  createStep = 0;
  /** A kind from the list (R5-26: the server refuses any other); CUSTOM never was one. */
  createType = 'OTHER';
  createTitle = '';
  createDescription = '';
  createDueDate: Date | null = null;
  targetMode: 'one' | 'selected' | 'all' = 'one';
  targetStudentId: number | null = null;
  targetStudentIds: number[] = [];
  targetCountryId: number | null = null;
  targetStatusId: number | null = null;
  creating = false;

  /** For "one student": autocomplete suggestions and selected option */
  studentSuggestions: StudentOption[] = [];
  selectedStudentOption: StudentOption | null = null;
  /** For "selected" students: options loaded by search */
  studentOptionsForMulti: StudentOption[] = [];
  studentSearchTerm = '';
  loadingStudents = false;

  /**
   * The "all students" filters. The server matches the study country (STUDY_COUNTRY_IN)
   * and STUDENT_STUDY_STATUS_ID (findStudentIdsByOptionalFilters), so these are the study
   * countries the user may reach and the student-study-status lookup, offered by name.
   * The country was the nationality until FIX-PLAN P5-4 (26 Sep 2026).
   */
  countryOptions: LookupItem[] = [];
  studyStatusOptions: LookupItem[] = [];
  private targetLookupsLoaded = false;

  selectedRequest: StudentInfoRequestDto | null = null;
  showDetailDialog = false;
  stagingData: { studentDataJson: string; studentFinanceJson: string; submittedAt: string } | null = null;
  rejectReason = '';
  rejecting = false;
  approving = false;
  applyApplying = false;

  requestTypes: { label: string; value: string }[] = [];
  /** LU_INFO_REQUEST_STATUS, for the filter and for naming the column. */
  statusOptions: { label: string; value: string }[] = [];
  createSteps: { label: string }[] = [
    { label: '' },
    { label: '' }
  ];

  constructor(
    private service: StudentInfoRequestService,
    private http: HttpClient,
    private messageService: MessageService,
    private studentDataService: StudentdataService,
    private lookupService: LookupService,
    public translationService: TranslationService,
    private codeLists: CodeListService,
    private permissions: UmsPermissionService
  ) {}

  /**
   * Raising, cancelling and deciding requests needs STUDENT_INFO_REQUESTS.EDIT; the
   * server refuses them without it (live QA H3), so the buttons are not offered.
   */
  get canWrite(): boolean {
    return this.permissions.hasPermission('STUDENT_INFO_REQUESTS.EDIT');
  }

  ngOnInit(): void {
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.refreshCreateStepsTranslations();
      this.loadStatusOptions();
      this.targetLookupsLoaded = false;
      if (this.showCreateDialog) this.loadTargetLookups();
    });
    this.refreshCreateStepsTranslations();
    this.loadStatusOptions();
    this.loadKindOptions();
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private refreshCreateStepsTranslations(): void {
    const t = (key: string) => this.translationService.translate(key);
    this.createSteps = [
      { label: t('STUDENT_INFO_REQUESTS.STEP_REQUEST_DETAILS') },
      { label: t('STUDENT_INFO_REQUESTS.STEP_CHOOSE_STUDENTS') }
    ];
  }

  /**
   * The statuses the filter offers, from the bulk code-list payload the route
   * guard already warms - so it costs no request, and the names follow the UI
   * language.
   */
  private loadStatusOptions(): void {
    this.codeLists.whenLoaded().pipe(takeUntil(this.destroy$)).subscribe(() => {
      const lang = this.translationService.getCurrentLanguage();
      this.statusOptions = this.codeLists.options('info-request-status', lang);
    });
  }

  private loadKindOptions(): void {
    this.lookupService.getStudentInfoRequestKindsForUi().pipe(takeUntil(this.destroy$)).subscribe({
      next: kinds => { this.requestTypes = kinds ?? []; },
      error: () => { this.requestTypes = []; }
    });
  }

  /**
   * Runs the filters from the first page.
   *
   * <p>`load()` alone kept whatever page the user was on. There are 948 requests
   * at 20 a page, so filtering from page 3 asked the server for page 3 of a
   * one-page result and came back empty - which reads as "nothing matched"
   * rather than "you are past the end of it".
   */
  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filterStatus = '';
    this.filterRequestType = '';
    this.filterStudentId = null;
    this.applyFilters();
  }

  get hasFilters(): boolean {
    return !!(this.filterStatus?.trim() || this.filterRequestType?.trim() || this.filterStudentId != null);
  }

  load(): void {
    this.loading = true;
    const params: any = { page: this.page, size: this.size };
    // The backend compares with cb.equal, so a stray space matches nothing at
    // all; an all-spaces box is not a filter the user asked for either.
    const status = this.filterStatus?.trim();
    const requestType = this.filterRequestType?.trim();
    if (status) params.status = status;
    if (requestType) params.requestType = requestType;
    if (this.filterStudentId != null) params.studentId = this.filterStudentId;
    this.service.list(params).subscribe({
      next: (res) => {
        this.requests = res.content || [];
        this.totalElements = res.totalElements ?? 0;
        this.totalPages = res.totalPages ?? 0;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  openCreate(): void {
    this.createStep = 0;
    this.createType = 'OTHER';
    this.createTitle = '';
    this.createDescription = '';
    this.createDueDate = null;
    this.targetMode = 'one';
    this.targetStudentId = null;
    this.targetStudentIds = [];
    this.selectedStudentOption = null;
    this.studentOptionsForMulti = [];
    this.studentSearchTerm = '';
    this.targetCountryId = null;
    this.targetStatusId = null;
    this.loadTargetLookups();
    this.showCreateDialog = true;
  }

  /** Fetched on first use; the service caches the rows. */
  private loadTargetLookups(): void {
    if (this.targetLookupsLoaded) return;
    this.targetLookupsLoaded = true;
    // Study countries, cut to the user's own (the server refuses another with 403).
    this.lookupService.getStudyCountriesIn().pipe(takeUntil(this.destroy$)).subscribe({
      next: rows => { this.countryOptions = (rows ?? []).filter(r => this.permissions.hasCountryAccess(Number(r.id))); },
      error: () => { this.countryOptions = []; }
    });
    this.lookupService.getStudentStudyStatuses().pipe(takeUntil(this.destroy$)).subscribe({
      next: rows => { this.studyStatusOptions = rows ?? []; },
      error: () => { this.studyStatusOptions = []; }
    });
  }

  // ── Create dialog: what the hero and footer show ──────────────────────────

  /** Who the request will go to, as the hero's recipients chip. */
  get targetSummary(): string {
    switch (this.targetMode) {
      case 'one':
        return this.selectedStudentOption?.studentName || this.t('STUDENT_INFO_REQUESTS.TARGET_ONE');
      case 'selected':
        return this.targetStudentIds.length ? this.selectedCountText : this.t('STUDENT_INFO_REQUESTS.TARGET_SELECTED');
      default: {
        const parts = [this.t('STUDENT_INFO_REQUESTS.TARGET_ALL')];
        const country = this.countryOptions.find(c => c.id === this.targetCountryId)?.name;
        const status = this.studyStatusOptions.find(s => s.id === this.targetStatusId)?.name;
        if (country) parts.push(country);
        if (status) parts.push(status);
        return parts.join(' · ');
      }
    }
  }

  get selectedCountText(): string {
    return this.t('STUDENT_INFO_REQUESTS.SELECTED_COUNT').replace('{count}', String(this.targetStudentIds.length));
  }

  get createStepText(): string {
    return this.t('STUDENT_INFO_REQUESTS.STEP_OF')
      .replace('{current}', String(this.createStep + 1))
      .replace('{total}', String(this.createSteps.length));
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  nextCreateStep(): void {
    if (this.createStep === 0 && !this.createTitle.trim()) {
      this.messageService.add({ severity: 'warn', summary: this.t('COMMON.VALIDATION_ERROR'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.TITLE_REQUIRED') });
      return;
    }
    this.createStep++;
  }

  prevCreateStep(): void {
    this.createStep--;
  }

  /** Load student suggestions for autocomplete (one student) */
  searchStudentsOne(event: { query: string }): void {
    const q = (event.query || '').trim();
    if (!q || q.length < 2) {
      this.studentSuggestions = [];
      return;
    }
    this.loadingStudents = true;
    this.studentDataService.getPagingAll({
      page: '0',
      size: '30',
      search: q
    }).subscribe({
      next: (res: any) => {
        const content = res?.content || [];
        this.studentSuggestions = content.map((s: any) => ({
          studentId: s.studentId ?? s.STUDENT_ID,
          studentName: s.studentName ?? s.studentName ?? String(s.studentId ?? s.STUDENT_ID)
        }));
        this.loadingStudents = false;
      },
      error: () => this.loadingStudents = false
    });
  }

  onStudentOneSelect(event: StudentOption | { value?: StudentOption } | null): void {
    const opt = event && typeof event === 'object' && 'studentId' in event ? event as StudentOption : (event as { value?: StudentOption })?.value ?? null;
    this.selectedStudentOption = opt;
    this.targetStudentId = opt ? opt.studentId : null;
  }

  /** Load students for multi-select (selected students) */
  loadStudentsForMulti(): void {
    const q = this.studentSearchTerm.trim() || '';
    this.loadingStudents = true;
    this.studentDataService.getPagingAll({
      page: '0',
      size: '100',
      search: q || undefined
    }).subscribe({
      next: (res: any) => {
        const content = res?.content || [];
        this.studentOptionsForMulti = content.map((s: any) => ({
          studentId: s.studentId ?? s.STUDENT_ID,
          studentName: s.studentName ?? s.studentName ?? String(s.studentId ?? s.STUDENT_ID)
        }));
        this.loadingStudents = false;
      },
      error: () => this.loadingStudents = false
    });
  }


  create(): void {
    if (!this.createTitle.trim()) {
      this.messageService.add({ severity: 'warn', summary: this.t('COMMON.VALIDATION_ERROR'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.TITLE_REQUIRED') });
      return;
    }
    let studentIds: number[] | undefined;
    let all = false;
    if (this.targetMode === 'one') {
      const id = this.targetStudentId ?? this.selectedStudentOption?.studentId;
      if (id != null) studentIds = [id];
    }
    if (this.targetMode === 'selected' && this.targetStudentIds.length) {
      studentIds = this.targetStudentIds;
    }
    if (this.targetMode === 'all') {
      all = true;
    }
    if ((studentIds == null || studentIds.length === 0) && !all) {
      this.messageService.add({ severity: 'warn', summary: this.t('COMMON.VALIDATION_ERROR'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.SELECT_STUDENTS') });
      return;
    }
    const target: any = all
      ? { all: true, countryId: this.targetCountryId ?? undefined, statusId: this.targetStatusId ?? undefined }
      : { studentIds };
    const body: any = {
      requestType: this.createType,
      title: this.createTitle.trim(),
      description: this.createDescription.trim() || undefined,
      dueDate: this.createDueDate ? this.createDueDate.toISOString() : undefined,
      target
    };
    this.creating = true;
    this.service.create(body).subscribe({
      next: (res) => {
        this.creating = false;
        this.showCreateDialog = false;
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: res.message || this.t('STUDENT_INFO_REQUESTS.MESSAGES.CREATED') });
        this.load();
      },
      error: (err) => {
        this.creating = false;
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: err.error?.error || this.t('STUDENT_INFO_REQUESTS.MESSAGES.CREATE_FAILED') });
      }
    });
  }

  openDetail(r: StudentInfoRequestDto): void {
    this.stagingData = null;
    this.service.getOne(r.id).subscribe({
      next: (req) => {
        this.selectedRequest = req;
        this.rejectReason = '';
        this.showDetailDialog = true;
        if (req.status === 'RESPONDED') {
          this.service.getStaging(req.id).subscribe({
            next: (staging) => { this.stagingData = staging; },
            error: () => {}
          });
        }
      }
    });
  }

  cancelRequest(): void {
    if (!this.selectedRequest) return;
    this.service.cancel(this.selectedRequest.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.CANCELLED') });
        this.showDetailDialog = false;
        this.load();
      },
      error: () => this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.CANCEL_FAILED') })
    });
  }

  reject(): void {
    if (!this.selectedRequest) return;
    this.rejecting = true;
    this.service.reject(this.selectedRequest.id, this.rejectReason).subscribe({
      next: () => {
        this.rejecting = false;
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.REJECTED') });
        this.showDetailDialog = false;
        this.load();
      },
      error: () => { this.rejecting = false; this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.REJECT_FAILED') }); }
    });
  }

  approve(): void {
    if (!this.selectedRequest) return;
    this.approving = true;
    this.service.approve(this.selectedRequest.id).subscribe({
      next: () => {
        this.approving = false;
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.APPROVED') });
        this.showDetailDialog = false;
        this.load();
      },
      error: () => { this.approving = false; this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('STUDENT_INFO_REQUESTS.MESSAGES.APPROVE_FAILED') }); }
    });
  }

  approveAndApply(): void {
    if (!this.selectedRequest) return;
    this.applyApplying = true;
    // The data is not written here any more: its changes go to the approval chains (H3-b).
    const onSuccess = (outcome?: ApproveAndApplyOutcome) => {
      this.applyApplying = false;
      const key = outcome?.status === 'NO_CHANGES'
        ? 'STUDENT_INFO_REQUESTS.MESSAGES.APPROVED_NO_CHANGES'
        : 'STUDENT_INFO_REQUESTS.MESSAGES.APPROVED_SUBMITTED';
      this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t(key) });
      this.showDetailDialog = false;
      this.load();
    };
    const onError = (err?: any) => {
      this.applyApplying = false;
      this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: err?.error?.error || this.t('STUDENT_INFO_REQUESTS.MESSAGES.APPLY_FAILED') });
    };
    if (this.stagingData && (this.stagingData.studentDataJson || this.stagingData.studentFinanceJson)) {
      this.service.approveAndApply(this.selectedRequest.id, {}).subscribe({ next: onSuccess, error: onError });
      return;
    }
    const sid = this.selectedRequest.studentId;
    const base = `${environment.apiUrl}/mohe/api/v1`;
    this.http.get<any>(`${base}/studentdata/${sid}`).subscribe({
      next: (student) => {
        this.http.get<any>(`${base}/studentFinance/${sid}`).subscribe({
          next: (finance) => {
            this.service.approveAndApply(this.selectedRequest!.id, { student, finance }).subscribe({ next: onSuccess, error: onError });
          },
          error: () => {
            this.service.approveAndApply(this.selectedRequest!.id, { student }).subscribe({ next: onSuccess, error: onError });
          }
        });
      },
      error: () => onError()
    });
  }

  /**
   * The status as the database names it.
   *
   * <p>The column printed the raw code, so the value a user had to type into the
   * filter was never on screen anywhere. A code the catalogue does not hold is
   * shown as it is rather than hidden - three of the stored request types are in
   * no catalogue at all.
   */
  /** LU_INFO_REVIEW_STATUS by name; the column and the dialog printed the code (QA B11). */
  reviewLabel(code: string | null | undefined): string {
    if (!code) return '—';
    return this.codeLists.label('info-review-status', code, this.translationService.getCurrentLanguage());
  }

  statusLabel(code: string | null | undefined): string {
    if (!code) return '—';
    return this.codeLists.label('info-request-status', code, this.translationService.getCurrentLanguage());
  }

  /** The request kind as the catalogue names it, or the raw code when it has none. */
  requestTypeLabel(code: string | null | undefined): string {
    if (!code) return '—';
    return this.requestTypes.find(t => t.value === code)?.label ?? code;
  }

  // ── Request detail: tones and states for the shared .dv-* vocabulary ──────

  /** The code list's severity as the palette name the .dv-* modifiers use. */
  private tone(list: string, code: string | null | undefined): 'green' | 'orange' | 'red' | 'blue' | 'gray' {
    const severity: string = code ? this.codeLists.severity(list, code) : '';
    switch (severity) {
      case 'success': return 'green';
      case 'warn':
      case 'warning': return 'orange';
      case 'danger': return 'red';
      case 'info': return 'blue';
      default: return 'gray';
    }
  }

  private chip(tone: string): string {
    return tone === 'gray' ? 'dv-chip' : `dv-chip dv-chip--${tone}`;
  }

  /** The hero's colour: the decision once there is one, otherwise the status. */
  heroClass(r: StudentInfoRequestDto): string {
    if (r.status === 'CANCELLED') return 'dv-hero--muted';
    if (this.isDecided(r)) return r.reviewStatus === 'APPROVED' ? 'dv-hero--success' : 'dv-hero--danger';
    switch (this.tone('info-request-status', r.status)) {
      case 'green': return 'dv-hero--success';
      case 'orange': return 'dv-hero--warn';
      case 'red': return 'dv-hero--danger';
      case 'blue': return 'dv-hero--info';
      default: return '';
    }
  }

  statusChipClass(r: StudentInfoRequestDto): string {
    return this.chip(this.tone('info-request-status', r.status));
  }

  reviewChipClass(r: StudentInfoRequestDto): string {
    return this.chip(this.tone('info-review-status', r.reviewStatus));
  }

  dueChipClass(r: StudentInfoRequestDto): string {
    return this.isOverdue(r) ? 'dv-chip dv-chip--red' : 'dv-chip dv-chip--orange';
  }

  statusKpiClass(r: StudentInfoRequestDto): string {
    return `dv-kpi dv-kpi--${this.tone('info-request-status', r.status)}`;
  }

  reviewKpiClass(r: StudentInfoRequestDto): string {
    return `dv-kpi dv-kpi--${this.tone('info-review-status', r.reviewStatus)}`;
  }

  reviewValueClass(r: StudentInfoRequestDto): string {
    const tone = this.tone('info-review-status', r.reviewStatus);
    return tone === 'gray' ? 'dv-dl__muted' : `dv-dl__${tone}`;
  }

  /** Marked overdue, or still pending past its due date. */
  isOverdue(r: StudentInfoRequestDto): boolean {
    if (r.status === 'OVERDUE') return true;
    return r.status === 'PENDING' && !!r.dueDate && new Date(r.dueDate).getTime() < Date.now();
  }

  /** A request can be withdrawn until the student answers it. */
  canCancel(r: StudentInfoRequestDto): boolean {
    return r.status === 'PENDING' || r.status === 'OVERDUE';
  }

  isDecided(r: StudentInfoRequestDto): boolean {
    return r.reviewStatus === 'APPROVED' || r.reviewStatus === 'REJECTED';
  }

  /** An answer is waiting for a decision. */
  canDecide(r: StudentInfoRequestDto): boolean {
    return r.status === 'RESPONDED' && !this.isDecided(r);
  }

  prevPage(): void { if (this.page > 0) { this.page--; this.load(); } }
  nextPage(): void { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }
}
