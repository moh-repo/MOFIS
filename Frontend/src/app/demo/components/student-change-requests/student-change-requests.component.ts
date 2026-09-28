import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient, HttpParams } from '@angular/common/http';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, finalize, timeout } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../service/translation.service';
import { LookupService } from '../../service/lookup.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { CodeListService } from '../../service/code-list.service';
import { environment } from '../../../../environments/environment';
import { silentErrors } from '../../interceptors/http-context.tokens';
import { changeRequestDateText, fromDateText, toDateText } from '../../service/change-request-value.util';
import { changesOnly } from '../../service/reload-streams.util';
import { followPage, resetPaging } from '../../service/table-paging.util';
import { resolveChangeRequestFieldLabel } from '../../../student-portal/my-requests/change-request-field-label';

/** The contact numbers, under their current codes and the older ones stored requests still carry. */
const PHONE_FIELDS = new Set(['studentCellphone', 'studentHomeNumber', 'studentEmergencyContact',
  'cellphone', 'homeNumber', 'emergencyContact']);

const BASE = `${environment.apiUrl}/mohe/api/v1/studentdata/change-requests`;
const LOOKUP = `${environment.apiUrl}/mohe/api/lookup-tables`;

interface ChangeRequest {
  requestId: number;
  studentId: number;
  studentUserId?: number;
  submissionId: string;
  fieldName: string;
  fieldLabel?: string;
  oldValue?: string;
  newValue: string;
  status: string;
  rejectionReason?: string;
  employeeComment?: string;
  appliedValue?: string;
  /** CHANGE_REQUEST_FIELD.DATA_TYPE: TEXT, DATE, LOOKUP, EMAIL or PHONE. */
  fieldType?: string;
  /** The server withheld the values: the reviewer may not see this field on the student record (QA N3). */
  valuesMasked?: boolean;
  reviewedBy?: number;
  reviewedByName?: string;
  reviewedAt?: string;
  appliedAt?: string;
  createdAt: string;
  // Enriched by backend DTO
  studentName?: string;
  studentDegreeId?: number;
  studentCurrentLevelId?: number;
  studentStudyStatusId?: number;
  studyCountryIn?: number;
  syudyGovIn?: number;
  studyUniversityIn?: number;
  studyCollegeIn?: number;
  studyEducationIn?: number;
  studyMajorIn?: number;
}

interface PagedResponse {
  content: ChangeRequest[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** A lookup row: both names (M8) and whether it may still be chosen (retired rows only name old values). */
interface LookupOption { id: number; name: string; nameEn: string; active: boolean; }

@Component({
    selector: 'app-student-change-requests',
    templateUrl: './student-change-requests.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudentChangeRequestsComponent implements OnInit {

  requests: ChangeRequest[] = [];
  totalRecords = 0;
  loading = false;
  page = 0;
  rows = 20;
  /** Bound to the table, so a search that reloads page one moves the paginator there too (QA B8, N9). */
  first = 0;

  // Search
  searchStudentId = '';
  searchStudentName = '';

  // Sort (PrimeNG: sortOrder 1 = asc, -1 = desc)
  sortField = 'createdAt';
  sortOrder = -1;

  selectedStatus: string | null = null;
  statusOptions: { label: string; value: string | null }[] = [];

  // Review dialog
  selectedRequest: ChangeRequest | null = null;
  reviewMode: 'approve' | 'reject' | 'modify' | null = null;
  showReviewDialog = false;
  employeeComment = '';
  rejectionReason = '';
  modifiedValue = '';
  /** The value to apply when the field is a date (review R2b). */
  modifiedDate: Date | null = null;
  reviewLoading = false;
  /** Set once Confirm is pressed, so an empty required box is marked only then. */
  reviewSubmitted = false;
  // Pre-computed once per openReview() call — avoids returning a new array reference
  // on every change detection cycle (which would cause p-dropdown to re-render in a loop).
  dialogFieldIsLookup = false;
  dialogFieldIsDate = false;
  dialogFieldOptions: Array<{ label: string; value: string }> = [];
  /** Student name + study context rows shown at the top of the review dialog. */
  dialogStudentInfo: Array<{ label: string; value: string }> = [];

  // View dialog
  showViewDialog = false;
  viewedRequest: ChangeRequest | null = null;
  /** Student name + study context rows shown in the view dialog. */
  viewStudentInfo: Array<{ label: string; value: string }> = [];

  // Lookup data
  private lookups: Record<string, LookupOption[]> = {};

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);
  /** Field names, labels and lookup keys come from the change-request-field list. */
  private readonly codeLists = inject(CodeListService);
  currentLanguage = 'en';

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public translationService: TranslationService,
    private lookupService: LookupService,
    private permissionService: UmsPermissionService
  ) {}

  ngOnInit(): void {
    this.loadStatusOptions();
    this.currentLanguage = this.translationService.getCurrentLanguage();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(lang => {
      this.currentLanguage = lang;
      this.loadStatusOptions();
    });
    this.loadRequests();
    this.loadLookups();
  }

  private loadStatusOptions(): void {
    this.lookupService.getChangeRequestStatuses().subscribe(statuses => {
      this.statusOptions = [
        { label: this.translationService.translate('COMMON.ALL'), value: null },
        ...statuses
      ];
    });
  }

  statusLabel(status: string): string {
    return this.codeLists.label('change-request-status', status, this.currentLanguage);
  }

  /** The field's name: the server's label when it sends one, else the change-request-field row. */
  /**
   * The field named in the reader's language (live QA M14). The stored label is the
   * submitter's ("Cell Phone" from an English portal), so it only helps find the field.
   */
  fieldLabel(req: ChangeRequest): string {
    return resolveChangeRequestFieldLabel(this.codeLists, req.fieldName, req.fieldLabel);
  }

  /** The lookup list a field's values come from (CHANGE_REQUEST_FIELD.LOOKUP_LIST), if any. */
  private lookupKeyFor(fieldName?: string): string | null {
    return fieldName ? this.codeLists.extra('change-request-field', fieldName) : null;
  }

  private loadLookups(): void {
    // Each lookup only names ids; a missing one leaves the raw id showing, so its
    // failure is not worth a toast (silentErrors on every call below).
    const safe = (obs: Observable<any>) => obs.pipe(timeout(8000), catchError(() => of([])));
    // The server now sends the whole table when no page is asked for (live QA M7).
    const ext = (res: any, idCands: string[], nameCands: string[], nameEnKey?: string): LookupOption[] => {
      const arr: any[] = res?.content ?? (Array.isArray(res) ? res : []);
      if (!arr.length) return [];
      const s = arr[0];
      const idKey = idCands.find(k => k in s) ?? idCands[idCands.length - 1];
      const nameKey = nameCands.find(k => k in s) ?? nameCands[nameCands.length - 1];
      return arr
        .map(x => ({
          id: Number(x[idKey]),
          name: String(x[nameKey] ?? ''),
          nameEn: nameEnKey ? String(x[nameEnKey] ?? '') : '',
          active: x.isActive !== false && x.isActive !== 0
        }))
        .filter(x => !isNaN(x.id));
    };

    forkJoin({
      genders:          safe(this.http.get(`${LOOKUP}/student-gender`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['id'], ['studentGenderName', 'name'], 'studentGenderNameEn'))),
      studentCountries: safe(this.http.get(`${LOOKUP}/country`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['studentCountryId', 'id'], ['studentCountryName', 'name'], 'studentCountryNameEn'))),
      degrees:          safe(this.http.get(`${LOOKUP}/student-degree`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['id'], ['studentDegreeName', 'name'], 'studentDegreeNameEn'))),
      currentLevels:    safe(this.http.get(`${LOOKUP}/student-current-level`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['id'], ['studentCurrentLevelName', 'name'], 'studentCurrentLevelNameEn'))),
      studyStatuses:    safe(this.http.get(`${LOOKUP}/student-study-status`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['id'], ['studentStudyStatusName', 'name'], 'studentStudyStatusNameEn'))),
      studyCountries:   safe(this.http.get(`${LOOKUP}/study-country-in`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['STUDY_COUNTRY_IN_ID', 'id'], ['STUDY_COUNTRY_IN_NAME', 'studyCountryInName', 'name'], 'studyCountryInNameEn'))),
      studyGovernments: safe(this.http.get(`${LOOKUP}/study-gov-in`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['STUDY_GOVERNORATE_IN_ID', 'STUDY_GOV_IN_ID', 'id'], ['STUDY_GOVERNORATE_IN_NAME', 'studyGovInName', 'name'], 'studyGovInNameEn'))),
      studyUniversities: safe(this.http.get(`${LOOKUP}/study-university-in`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['STUDY_UNIVERSITY_IN_ID', 'id'], ['STUDY_UNIVERSITY_IN_NAME', 'studyUniversityInName', 'name'], 'studyUniversityInNameEn'))),
      studyColleges:    safe(this.http.get(`${LOOKUP}/study-college-in`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['STUDY_COLLEGE_IN_ID', 'id'], ['STUDY_COLLEGE_IN_NAME', 'studyCollegeInName', 'name'], 'studyCollegeInNameEn'))),
      studyEducations:  safe(this.http.get(`${LOOKUP}/study-education-in`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['STUDY_EDUCATION_IN_ID', 'id'], ['STUDY_EDUCATION_IN_NAME', 'studyEductionName', 'name'], 'studyEductionNameEn'))),
      studyMajors:      safe(this.http.get(`${LOOKUP}/study-major-in`, { context: silentErrors() }))
                          .pipe(map((r: any) => ext(r, ['STUDY_MAJOR_IN_ID', 'id'], ['STUDY_MAJOR_IN_NAME', 'studyMajorName', 'name'], 'studyMajorNameEn'))),
    }).subscribe(result => {
      this.lookups = result as Record<string, LookupOption[]>;
      const allowedIds = this.permissionService.getAllowedCountryIds();
      if (allowedIds.length > 0) {
        this.lookups['studyCountries'] = (this.lookups['studyCountries'] || [])
          .filter(c => allowedIds.includes(c.id));
      }
    });
  }

  /** A value as the row shows it: "Hidden" when the server withheld it (QA N3, DEC-05). */
  /**
   * A phone number is written left to right in both languages (live QA L11: in Arabic
   * the "+" of "+962 …" was drawn at the wrong end). Other values keep the page's
   * direction; each value is isolated with `<bdi>` in the template.
   */
  valueDir(req: ChangeRequest): 'ltr' | null {
    return PHONE_FIELDS.has(req?.fieldName ?? '') ? 'ltr' : null;
  }

  shownValue(req: ChangeRequest, rawValue?: string | null): string {
    return req.valuesMasked
      ? this.translationService.translate('STUDENT_CHANGE_REQUESTS.VALUE_HIDDEN')
      : this.resolveValue(req.fieldName, rawValue);
  }

  /** Resolve a raw ID value to its display name for lookup-type fields. */
  resolveValue(fieldName: string, rawValue?: string | null): string {
    if (!rawValue) return '';
    const key = this.lookupKeyFor(fieldName);
    if (!key || !this.lookups[key]?.length) return changeRequestDateText(rawValue);
    const found = this.lookups[key].find(o => o.id === Number(rawValue));
    return found ? this.optionName(found) : rawValue;
  }

  /** The row's name in the reader's language, the other one when it has none (live QA M8). */
  private optionName(o: LookupOption): string {
    const english = this.currentLanguage === 'en';
    return (english ? o.nameEn || o.name : o.name || o.nameEn) || String(o.id);
  }

  /** Returns dropdown options (label/value pairs) for a lookup field; empty array for text fields. */
  getLookupDropdownOptions(fieldName?: string): Array<{ label: string; value: string }> {
    if (!fieldName) return [];
    const key = this.lookupKeyFor(fieldName);
    if (!key || !this.lookups[key]?.length) return [];
    // A retired row still names an old value above, but is not offered (the server refuses it, M18).
    return this.lookups[key].filter(o => o.active).map(o => ({ label: this.optionName(o), value: String(o.id) }));
  }

  isLookupField(fieldName?: string): boolean {
    return this.getLookupDropdownOptions(fieldName).length > 0;
  }

  /** Resolve a study-context ID (from the student's current data) to its display name. */
  private resolveStudyField(lookupKey: string, id?: number | null): string {
    if (id == null) return '—';
    const opts = this.lookups[lookupKey];
    if (!opts?.length) return String(id);
    const found = opts.find(o => o.id === id);
    return found ? this.optionName(found) : String(id);
  }

  private buildStudentInfoRows(req: ChangeRequest): Array<{ label: string; value: string }> {
    const t = (key: string) => this.translationService.translate(key);
    const rows: Array<{ label: string; value: string }> = [];

    rows.push({ label: t('STUDENTS.STUDENT_ID'),   value: String(req.studentId) });
    if (req.studentName)
      rows.push({ label: t('STUDENT_PORTAL.NAME'), value: req.studentName });

    const studyCountry = this.resolveStudyField('studyCountries', req.studyCountryIn);
    if (studyCountry !== '—')
      rows.push({ label: t('STUDENT_PORTAL.STUDY_COUNTRY_IN'),   value: studyCountry });

    const studyGov = this.resolveStudyField('studyGovernments', req.syudyGovIn);
    if (studyGov !== '—')
      rows.push({ label: t('STUDENT_PORTAL.STUDY_GOV_IN'),       value: studyGov });

    const university = this.resolveStudyField('studyUniversities', req.studyUniversityIn);
    if (university !== '—')
      rows.push({ label: t('STUDENT_PORTAL.STUDY_UNIVERSITY_IN'), value: university });

    const college = this.resolveStudyField('studyColleges', req.studyCollegeIn);
    if (college !== '—')
      rows.push({ label: t('STUDENT_PORTAL.STUDY_COLLEGE_IN'),   value: college });

    const education = this.resolveStudyField('studyEducations', req.studyEducationIn);
    if (education !== '—')
      rows.push({ label: t('STUDENT_PORTAL.STUDY_EDUCATION_IN'), value: education });

    const major = this.resolveStudyField('studyMajors', req.studyMajorIn);
    if (major !== '—')
      rows.push({ label: t('STUDENT_PORTAL.STUDY_MAJOR_IN'),     value: major });

    const degree = this.resolveStudyField('degrees', req.studentDegreeId);
    if (degree !== '—')
      rows.push({ label: t('STUDENT_PORTAL.DEGREE'),             value: degree });

    const level = this.resolveStudyField('currentLevels', req.studentCurrentLevelId);
    if (level !== '—')
      rows.push({ label: t('STUDENT_PORTAL.CURRENT_LEVEL'),      value: level });

    return rows;
  }

  loadRequests(): void {
    this.loading = true;
    let params = new HttpParams()
      .set('page', String(this.page))
      .set('size', String(this.rows))
      .set('sortField', this.sortField)
      .set('sortDir', this.sortOrder === 1 ? 'asc' : 'desc');
    if (this.selectedStatus)                           params = params.set('status', this.selectedStatus);
    if (this.searchStudentId.trim())                   params = params.set('studentId', this.searchStudentId.trim());
    if (this.searchStudentName.trim())                 params = params.set('studentName', this.searchStudentName.trim());

    // Silent (review T6): this component reports a failed load itself, and the
    // interceptor's toast on top of it showed the same failure twice.
    this.http.get<PagedResponse>(BASE, { params, context: silentErrors() })
      .pipe(finalize(() => { this.loading = false; }))
      .subscribe({
        next: (data) => {
          this.requests = data.content;
          this.totalRecords = data.totalElements;
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: this.translationService.translate('STUDENT_CHANGE_REQUESTS.LOAD_FAILED')
          });
        }
      });
  }

  onLazyLoad(event: any): void {
    followPage(this, event);
    if (event.sortField) {
      this.sortField = event.sortField;
      this.sortOrder = event.sortOrder ?? -1;
    }
    this.loadRequests();
  }

  onSearch(): void {
    resetPaging(this);
    this.loadRequests();
  }

  get hasFilters(): boolean {
    return !!(this.selectedStatus || this.searchStudentId.trim() || this.searchStudentName.trim());
  }

  resetFilters(): void {
    this.selectedStatus = null;
    this.searchStudentId = '';
    this.searchStudentName = '';
    this.onSearch();
  }

  openReview(req: ChangeRequest, mode: 'approve' | 'reject' | 'modify'): void {
    this.selectedRequest = req;
    this.reviewMode = mode;
    this.employeeComment = '';
    this.rejectionReason = '';
    this.modifiedValue = req.newValue;
    this.reviewSubmitted = false;
    this.dialogFieldIsDate = req.fieldType === 'DATE';
    this.modifiedDate = this.dialogFieldIsDate ? fromDateText(req.newValue) : null;
    this.dialogFieldIsLookup = this.isLookupField(req.fieldName);
    this.dialogFieldOptions = this.getLookupDropdownOptions(req.fieldName);
    this.dialogStudentInfo = this.buildStudentInfoRows(req);
    this.showReviewDialog = true;
  }

  /** Reject needs a reason (review R2c); modify needs the value to apply. */
  get reviewInvalid(): boolean {
    if (this.reviewMode === 'reject') return !this.rejectionReason.trim();
    if (this.reviewMode === 'modify') {
      return this.dialogFieldIsDate ? !this.modifiedDate : !String(this.modifiedValue ?? '').trim();
    }
    return false;
  }

  // ── View dialog ────────────────────────────────────────────────────────────

  openView(req: ChangeRequest): void {
    this.viewedRequest = req;
    this.viewStudentInfo = this.buildStudentInfoRows(req);
    this.showViewDialog = true;
  }

  /** From the view straight into a decision on the same request. */
  reviewFromView(mode: 'approve' | 'reject' | 'modify'): void {
    const req = this.viewedRequest;
    if (!req) return;
    this.showViewDialog = false;
    this.openReview(req, mode);
  }

  // ── Tones for the shared .dv-* vocabulary ─────────────────────────────────

  /** The status code list's severity as the palette name the .dv-* modifiers use. */
  private tone(status: string | null | undefined): 'green' | 'orange' | 'red' | 'blue' | 'gray' {
    const severity: string = status ? this.codeLists.severity('change-request-status', status) : '';
    switch (severity) {
      case 'success': return 'green';
      case 'warn':
      case 'warning': return 'orange';
      case 'danger': return 'red';
      case 'info': return 'blue';
      default: return 'gray';
    }
  }

  heroClassFor(status: string | null | undefined): string {
    switch (this.tone(status)) {
      case 'green': return 'dv-hero--success';
      case 'orange': return 'dv-hero--warn';
      case 'red': return 'dv-hero--danger';
      case 'blue': return 'dv-hero--info';
      default: return '';
    }
  }

  statusKpiClass(status: string | null | undefined): string {
    return `dv-kpi dv-kpi--${this.tone(status)}`;
  }

  /** The review dialog is coloured for the decision being made, not the request's status. */
  get reviewTitleKey(): string {
    switch (this.reviewMode) {
      case 'approve': return 'STUDENT_CHANGE_REQUESTS.APPROVE';
      case 'reject': return 'STUDENT_CHANGE_REQUESTS.REJECT';
      default: return 'STUDENT_CHANGE_REQUESTS.MODIFY_APPROVE';
    }
  }

  get reviewHintKey(): string {
    switch (this.reviewMode) {
      case 'approve': return 'STUDENT_CHANGE_REQUESTS.REVIEW_HINT_APPROVE';
      case 'reject': return 'STUDENT_CHANGE_REQUESTS.REVIEW_HINT_REJECT';
      default: return 'STUDENT_CHANGE_REQUESTS.REVIEW_HINT_MODIFY';
    }
  }

  get reviewHeroClass(): string {
    switch (this.reviewMode) {
      case 'approve': return 'dv-hero--success';
      case 'reject': return 'dv-hero--danger';
      default: return 'dv-hero--warn';
    }
  }

  get reviewNoticeClass(): string {
    switch (this.reviewMode) {
      case 'approve': return 'dv-notice--success';
      case 'reject': return 'dv-notice--danger';
      default: return 'dv-notice--warn';
    }
  }

  get reviewSectionIconClass(): string {
    switch (this.reviewMode) {
      case 'approve': return 'dv-section__icon--green';
      case 'reject': return 'dv-section__icon--red';
      default: return 'dv-section__icon--orange';
    }
  }

  get reviewNoticeIcon(): string {
    switch (this.reviewMode) {
      case 'approve': return 'pi-check-circle';
      case 'reject': return 'pi-times-circle';
      default: return 'pi-pencil';
    }
  }

  submitReview(): void {
    if (!this.selectedRequest || !this.reviewMode) return;
    this.reviewSubmitted = true;
    if (this.reviewInvalid) return;
    if (this.reviewMode === 'modify' && this.dialogFieldIsDate && this.modifiedDate) {
      this.modifiedValue = toDateText(this.modifiedDate);
    }
    this.reviewLoading = true;

    let url: string;
    let body: any;

    switch (this.reviewMode) {
      case 'approve':
        url = `${BASE}/${this.selectedRequest.requestId}/approve`;
        body = { employeeComment: this.employeeComment };
        break;
      case 'reject':
        url = `${BASE}/${this.selectedRequest.requestId}/reject`;
        body = { rejectionReason: this.rejectionReason.trim() };
        break;
      case 'modify':
        url = `${BASE}/${this.selectedRequest.requestId}/modify-approve`;
        body = { appliedValue: this.modifiedValue, employeeComment: this.employeeComment };
        break;
    }

    // Silent for the same reason: the error branch below says why it failed.
    this.http.post(url, body, { context: silentErrors() }).subscribe({
      next: () => {
        this.reviewLoading = false;
        this.showReviewDialog = false;
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENT_CHANGE_REQUESTS.REVIEW_SAVED')
        });
        this.loadRequests();
      },
      error: (err) => {
        this.reviewLoading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: err.error?.error || this.translationService.translate('STUDENT_CHANGE_REQUESTS.REVIEW_FAILED')
        });
      }
    });
  }

}
