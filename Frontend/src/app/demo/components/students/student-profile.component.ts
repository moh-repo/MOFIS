import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Observable, Subject, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, takeUntil } from 'rxjs/operators';
import { StudentdataService } from '../../service/studentdata.service';
import { TranslationService } from '../../service/translation.service';
import { StudentYearResultDto } from '../../api/student-year-result';
import {
  StudentDeathCaseDto,
  StudentDeathCaseRequest,
  StudentParentDto,
  StudentRelativeDto,
  StudentRelativeSearchDto
} from '../../api/student-family';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { StudentDocument, StudentDocumentService } from '../../service/student-document.service';
import { WafarService, WafarStudentFeePaymentDto } from '../../service/wafar.service';
import { StudentAddComponent } from '../student-add/student-add.component';
import { ApprovalService, ApprovalSummary } from '../../service/approval.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { APPROVALS_READ, FINANCE_ARCH_READ, FINANCE_READ, WAFAR_READ } from '../../service/read-gates';
import { formatQuarterCode } from '../../service/quarter-code.util';

interface RelativeRowEdit extends StudentRelativeDto {
  selectedStudent?: StudentRelativeSearchDto | null;
}

/** Index of the last <p-tabpanel> in the template — the Approvals tab. */
const APPROVALS_TAB_INDEX = 11;

@Component({
    selector: 'app-student-profile',
    templateUrl: './student-profile.component.html',
    styleUrls: ['./student-profile.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentProfileComponent implements OnInit, OnDestroy {
  /** For the code-list pipes: the language the names are read in. */
  get currentLanguage(): string {
    return this.translationService.getCurrentLanguage();
  }


  @ViewChild('studentFormEmbed') studentFormEmbed?: StudentAddComponent;

  private destroy$ = new Subject<void>();

  studentId = 0;
  loading = false;

  /** Undecided requests for this student; drives the notice and the approvals tab. */
  pendingApprovals: ApprovalSummary[] = [];
  /** Decisions already taken — approved, rejected or executed. */
  completedApprovals: ApprovalSummary[] = [];
  loadingApprovals = false;
  decisionInFlight = false;
  rejectDialogVisible = false;
  rejectReason = '';
  private rejectTarget: ApprovalSummary | null = null;
  /** Two-way bound to <p-tabs [(value)]>. */
  activeTabIndex = 0;
  savingYears = false;

  studentFormModalVisible = false;

  profile: Record<string, any> | null = null;
  yearEdit: StudentYearResultDto[] = [];

  genderOptions: LookupItem[] = [];
  degreeOptions: LookupItem[] = [];
  majorOptions: LookupItem[] = [];
  agencyTypeOptions: LookupItem[] = [];
  studyStatusOptions: LookupItem[] = [];
  countryOptions: LookupItem[] = [];
  ministryTypeOptions: LookupItem[] = [];
  universityTypeOptions: LookupItem[] = [];
  currentLevelOptions: LookupItem[] = [];
  familyRelationshipOptions: LookupItem[] = [];

  /** Coded columns shown in the ledger/loan/book/travel tables, resolved to DB labels. */
  loanStatusOptions: LookupItem[] = [];
  bookPayStatusOptions: LookupItem[] = [];
  uniFeeStatusOptions: LookupItem[] = [];
  studyYearStatusOptions: LookupItem[] = [];
  travelTypeOptions: { label: string; value: string }[] = [];

  parentEdit: StudentParentDto[] = [];
  relativeEdit: RelativeRowEdit[] = [];
  relativeSearchResults: StudentRelativeSearchDto[] = [];
  savingFamily = false;

  deathCase: StudentDeathCaseDto | null = null;
  deathCaseForm: {
    dateOfDeath: Date | null;
    levelAtDeathId?: number;
    studyYearAtDeath?: number;
    placeOfDeath?: string;
    cause?: string;
    notes?: string;
  } = { dateOfDeath: null };
  savingDeathCase = false;
  closingDeathCase = false;

  photoUrl: string | null = null;

  /** Current documents, shown in the Documents tab. */
  studentDocuments: StudentDocument[] = [];
  loadingDocuments = false;

  financePayments: any[] = [];
  financeHistory: any[] = [];
  wafarFeePayments: WafarStudentFeePaymentDto[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private studentdataService: StudentdataService,
    private messageService: MessageService,
    public translationService: TranslationService,
    private lookupService: LookupService,
    public studentDocumentService: StudentDocumentService,
    private wafarService: WafarService,
    private approvalService: ApprovalService,
    private umsAuthService: UmsAuthService,
    private permissionService: UmsPermissionService
  ) {}

  /**
   * The Approvals tab and its requests are an approvals read (live QA M6): a student
   * viewer without it was shown "Access denied" on every profile.
   */
  get canSeeApprovals(): boolean {
    return this.permissionService.hasAnyPermission(APPROVALS_READ);
  }

  ngOnInit(): void {
    this.loadLookups();
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const id = +params['studentId'];
      if (!Number.isFinite(id) || id <= 0) {
        this.router.navigate(['/students']);
        return;
      }
      this.studentId = id;
      this.loadProfile();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this._revokeBlobUrl();
  }

  private loadLookups(): void {
    this.lookupService.getStudentGenders().subscribe(d => this.genderOptions = d);
    this.lookupService.getStudentDegrees().subscribe(d => this.degreeOptions = d);
    this.lookupService.getStudentMajors().subscribe(d => this.majorOptions = d);
    this.lookupService.getAgencyTypes().subscribe(d => this.agencyTypeOptions = d);
    this.lookupService.getStudentStudyStatuses().subscribe(d => this.studyStatusOptions = d);
    this.lookupService.getCountries().subscribe(d => this.countryOptions = d);
    this.lookupService.getMinistryTypes().subscribe(d => this.ministryTypeOptions = d);
    this.lookupService.getUniversityTypes().subscribe(d => this.universityTypeOptions = d);
    this.lookupService.getCurrentLevels().subscribe(d => this.currentLevelOptions = d);
    this.lookupService.getFamilyRelationshipLookupItems().subscribe(d => this.familyRelationshipOptions = d);

    // Coded status/type columns → DB-backed labels (normalised to LookupItem for label()).
    const toItems = (d: Array<{ label: string; value: any }>): LookupItem[] =>
      (d ?? []).map(o => ({ id: Number(o.value), name: o.label, label: o.label, value: o.value }));
    this.lookupService.getLoanStatuses().subscribe(d => this.loanStatusOptions = toItems(d));
    this.lookupService.getBooksAllowancePayStatuses().subscribe(d => this.bookPayStatusOptions = toItems(d));
    this.lookupService.getUniversityFeeStatuses().subscribe(d => this.uniFeeStatusOptions = toItems(d));
    this.lookupService.getStudyStatuses().subscribe(d => this.studyYearStatusOptions = d);
    this.lookupService.getLuTravelTypesForUi().subscribe(d => this.travelTypeOptions = d ?? []);
  }

  /**
   * Resolve a numeric status/type code to its DB label. Unlike label(), a code of 0 is
   * a valid status here, so it is matched rather than treated as "none".
   */
  statusLabel(arr: LookupItem[], code: any): string {
    if (code === null || code === undefined || code === '') return '—';
    const n = Number(code);
    if (!Number.isFinite(n)) return String(code);
    const found = arr.find(x => Number(x.id) === n || Number(x.value) === n);
    return found ? (found.name || found.label || String(code)) : String(code);
  }

  /** Resolve a travel-type code (string) to its DB label; the value survives as-is when unmatched. */
  travelTypeLabel(code: any): string {
    if (code === null || code === undefined || code === '') return '—';
    const s = String(code);
    return this.travelTypeOptions.find(o => o.value === s)?.label ?? s;
  }

  loadProfile(): void {
    this.loading = true;
    this.profile = null;
    this.financePayments = [];
    this.financeHistory = [];
    this.wafarFeePayments = [];
    this._revokeBlobUrl();
    this.studentdataService.getProfile(this.studentId).subscribe({
      next: (data) => {
        this.profile = data ?? {};
        const yrs = Array.isArray(this.profile['yearResults']) ? this.profile['yearResults'] : [];
        this.yearEdit = JSON.parse(JSON.stringify(yrs)) as StudentYearResultDto[];
        // Seed from profile data immediately so tables are never empty on load
        this.financePayments = Array.isArray(this.profile['financePayments'])
          ? this.profile['financePayments'] as any[] : [];
        this.financeHistory = Array.isArray(this.profile['financeHistory'])
          ? this.profile['financeHistory'] as any[] : [];
        this.parentEdit = JSON.parse(JSON.stringify(this.profile['parents'] ?? []));
        this.relativeEdit = (this.profile['relatives'] ?? []).map((r: StudentRelativeDto) => ({
          ...r,
          selectedStudent: r.relatedStudentId ? {
            studentId: r.relatedStudentId,
            studentName: r.relatedStudentName ?? '',
            studyStatusName: r.relatedStudentStatusName,
            currentLevelName: r.relatedStudentCurrentLevelName
          } : null
        }));
        this.deathCase = (this.profile['deathCase'] as StudentDeathCaseDto) ?? null;
        this.resetDeathCaseForm();
        this.loading = false;
        this.loadPhoto();
        this.loadDocuments();
        this.loadFinanceData();
        this.loadPendingApprovals();
      },
      error: (err: HttpErrorResponse) => {
        // A student that does not exist is the app's not-found page, not an
        // empty profile under an error toast (live QA 23 Sep 2026, L6).
        if (err?.status === 404) {
          this.router.navigate(['/notfound']);
          return;
        }
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENTS.PROFILE_PAGE.LOAD_ERROR')
        });
      }
    });
  }

  /** Requests still awaiting a decision, driving both the notice and the approvals tab. */
  private loadPendingApprovals(): void {
    if (!this.canSeeApprovals) {
      this.pendingApprovals = [];
      this.completedApprovals = [];
      return;
    }
    this.loadingApprovals = true;
    this.approvalService.getStudentRequests(this.studentId).subscribe({
      next: (result) => {
        this.pendingApprovals = result?.pending ?? [];
        this.completedApprovals = result?.completed ?? [];
        this.loadingApprovals = false;
      },
      error: () => {
        this.pendingApprovals = [];
        this.completedApprovals = [];
        this.loadingApprovals = false;
      }
    });
  }

  /** Approvals is the last tab; jumping there beats asking the user to hunt for it. */
  goToApprovalsTab(): void {
    this.activeTabIndex = APPROVALS_TAB_INDEX;
  }

  approve(request: ApprovalSummary): void {
    const approverId = this.umsAuthService.currentUserValue?.id;
    if (!approverId) {
      return;
    }
    this.decisionInFlight = true;
    this.approvalService.approveRequest(request.id, approverId).subscribe({
      next: () => {
        this.decisionInFlight = false;
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('APPROVAL.APPROVED_SUCCESS')
        });
        // The decision may have executed the change, so re-read the record as well.
        this.loadProfile();
      },
      error: (err) => {
        this.decisionInFlight = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: ApprovalService.errorMessage(err) || this.translationService.translate('APPROVAL.APPROVE_ERROR')
        });
      }
    });
  }

  openReject(request: ApprovalSummary): void {
    this.rejectTarget = request;
    this.rejectReason = '';
    this.rejectDialogVisible = true;
  }

  confirmReject(): void {
    const rejectedBy = this.umsAuthService.currentUserValue?.id;
    if (!this.rejectTarget || !rejectedBy || !this.rejectReason.trim()) {
      return;
    }
    this.decisionInFlight = true;
    this.approvalService.rejectRequest(this.rejectTarget.id, rejectedBy, this.rejectReason.trim()).subscribe({
      next: () => {
        this.decisionInFlight = false;
        this.rejectDialogVisible = false;
        this.rejectTarget = null;
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('APPROVAL.REJECTED_SUCCESS')
        });
        this.loadPendingApprovals();
      },
      error: (err) => {
        this.decisionInFlight = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: ApprovalService.errorMessage(err) || this.translationService.translate('APPROVAL.REJECT_ERROR')
        });
      }
    });
  }

  /** Each list only for a reader of its module (live QA M6); the profile seeds what it may show. */
  private loadFinanceData(): void {
    const may = (codes: string[]) => this.permissionService.hasAnyPermission(codes);
    if (may(FINANCE_READ)) {
      this.studentdataService.getFinancePaymentsByStudent(this.studentId).subscribe({
        next: (data) => { this.financePayments = data ?? []; },
        error: () => { /* keep whatever was seeded from the profile */ }
      });
    }
    if (may(FINANCE_ARCH_READ)) {
      this.studentdataService.getFinanceArchByStudent(this.studentId).subscribe({
        next: (data) => { this.financeHistory = data ?? []; },
        error: () => { /* keep whatever was seeded from the profile */ }
      });
    }
    if (may(WAFAR_READ)) {
      this.wafarService.getStudentFeePayments(this.studentId).subscribe({
        next: (data) => { this.wafarFeePayments = data ?? []; },
        error: () => { this.wafarFeePayments = []; }
      });
    } else {
      this.wafarFeePayments = [];
    }
  }

  /**
   * Uses the shared resolver so this page shows the same portrait as the students list:
   * querying only the photo endpoint left the avatar blank for every student whose
   * picture lives as a document rather than on the student row.
   */
  private loadPhoto(): void {
    this.studentDocumentService.resolveStudentPhoto(this.studentId).subscribe({
      next: (blob) => { this.photoUrl = blob ? URL.createObjectURL(blob) : null; },
      error: () => { this.photoUrl = null; }
    });
  }

  private loadDocuments(): void {
    this.loadingDocuments = true;
    this.studentDocumentService.getStudentDocuments(this.studentId).subscribe({
      next: (docs) => { this.studentDocuments = docs ?? []; this.loadingDocuments = false; },
      error: () => { this.studentDocuments = []; this.loadingDocuments = false; }
    });
  }

  /** Opens a document in a new tab; fetched as a blob so the JWT header is sent. */
  viewDocument(doc: StudentDocument): void {
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

  /** Translated format label (PDF / Image / Word / Other) for the documents list. */
  documentFormatLabel(doc: StudentDocument): string {
    return this.translationService.translate(this.studentDocumentService.getDocumentFormatLabelKey(doc));
  }

  /** DOCUMENT_TYPE only when it is a real classification, not an extension standing in for one. */
  documentTypeLabel(doc: StudentDocument): string {
    return this.studentDocumentService.isFormatLikeType(doc?.documentType) ? '—' : doc.documentType;
  }

  /** Human-readable file size (B / KB / MB) for the documents list. */
  formatFileSize(bytes: number | null | undefined): string {
    if (bytes === null || bytes === undefined || !Number.isFinite(Number(bytes))) return '—';
    const b = Number(bytes);
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`;
    return `${(b / (1024 * 1024)).toFixed(1)} MB`;
  }

  private _docBlobUrls: string[] = [];

  private _revokeBlobUrl(): void {
    if (this.photoUrl) {
      URL.revokeObjectURL(this.photoUrl);
      this.photoUrl = null;
    }
    this._docBlobUrls.forEach(url => URL.revokeObjectURL(url));
    this._docBlobUrls = [];
  }

  backToList(): void {
    this.router.navigate(['/students']);
  }

  editStudent(): void {
    this.studentFormModalVisible = true;
  }

  openStudentFormModalShown(): void {
    this.studentFormEmbed?.prepareDialog(this.studentId);
  }

  onStudentFormSaved(_studentId: number): void {
    this.studentFormModalVisible = false;
    this.loadProfile();
  }

  saveYears(): void {
    this.savingYears = true;
    this.studentdataService.putYearResults(this.studentId, this.yearEdit).subscribe({
      next: (saved) => {
        this.yearEdit = JSON.parse(JSON.stringify(saved ?? []));
        if (this.profile) this.profile['yearResults'] = saved;
        this.savingYears = false;
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENTS.PROFILE_PAGE.YEARS_SAVED')
        });
      },
      error: () => {
        this.savingYears = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENTS.PROFILE_PAGE.YEARS_SAVE_ERROR')
        });
      }
    });
  }

  addYearRow(): void {
    const lastNum = this.yearEdit.length > 0
      ? Math.max(...this.yearEdit.map(r => r.yearNumber || 0))
      : 0;
    this.yearEdit.push({ yearNumber: lastNum + 1, passed: false });
  }

  removeYearRow(i: number): void {
    this.yearEdit.splice(i, 1);
  }

  hintRemaining(): number {
    const v = this.profile?.['remainingBalanceHint'];
    return typeof v === 'number' ? v : 0;
  }

  get studentSnapshot(): Record<string, any> | null {
    const s = this.profile?.['student'];
    return s !== null && s !== undefined && typeof s === 'object' ? (s as Record<string, any>) : null;
  }

  /** "20242" → "2024 Q2" (QA B3). */
  quarterLabel(code: unknown): string {
    return formatQuarterCode(code, this.translationService.translate('COMMON.QUARTER_SHORT'));
  }

  label(arr: LookupItem[], id: any): string {
    if (!id && id !== 0) return '—';
    const n = Number(id);
    if (!n) return '—';
    const item = arr.find(x => Number(x.id) === n || Number(x.value) === n);
    return item ? (item.name || item.label || String(id)) : String(id);
  }

  avatarInitials(): string {
    const name = (this.studentSnapshot?.['studentName'] as string) || '';
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) {
      const w = parts[0];
      return w.length <= 2 ? w.toUpperCase() : (w[0] + w[w.length - 1]).toUpperCase();
    }
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  }

  progressNumeric(): number {
    const p = this.profile?.['progress'] as Record<string, any> | undefined;
    const v = p?.['progressPercent'];
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isFinite(n)) return 0;
    return Math.min(100, Math.max(0, n));
  }

  formatDate(v: any): string {
    if (!v) return '—';
    const d = new Date(v);
    if (isNaN(d.getTime())) return String(v);
    return d.toLocaleDateString();
  }

  formatAmount(v: any): string {
    if (v === null || v === undefined || v === '') return '—';
    const n = Number(v);
    if (!Number.isFinite(n)) return String(v);
    return n.toLocaleString();
  }

  addParentRow(): void {
    this.parentEdit.push({ relationshipId: 0, parentName: '', isAlive: true });
  }

  removeParentRow(i: number): void {
    this.parentEdit.splice(i, 1);
  }

  addRelativeRow(): void {
    this.relativeEdit.push({ relationshipId: 0, relatedStudentId: 0, selectedStudent: null });
  }

  removeRelativeRow(i: number): void {
    this.relativeEdit.splice(i, 1);
  }

  searchRelativeStudents(event: { query: string }): void {
    const q = (event?.query ?? '').trim();
    if (q.length < 2) {
      this.relativeSearchResults = [];
      return;
    }
    this.studentdataService.searchRelativeStudents(q, this.studentId).subscribe({
      next: results => { this.relativeSearchResults = results ?? []; },
      error: () => { this.relativeSearchResults = []; }
    });
  }

  onRelativeStudentSelected(row: RelativeRowEdit, selected: StudentRelativeSearchDto | null): void {
    row.selectedStudent = selected;
    row.relatedStudentId = selected?.studentId ?? 0;
  }

  relativeStudentLabel(item: StudentRelativeSearchDto): string {
    if (!item) return '';
    return [`#${item.studentId}`, item.studentName, item.passportNumber].filter(Boolean).join(' — ');
  }

  saveFamily(): void {
    this.savingFamily = true;
    const parents = this.parentEdit.filter(p => p.parentName?.trim() && p.relationshipId > 0);
    const relatives = this.relativeEdit
      .filter(r => r.relationshipId > 0 && r.relatedStudentId > 0)
      .map(r => ({
        relativeId: r.relativeId,
        relationshipId: r.relationshipId,
        relatedStudentId: r.relatedStudentId,
        notes: r.notes
      } as StudentRelativeDto));

    /*
     * Parents and relatives are two endpoints and two transactions; there is no
     * single "replace family" call on the server yet. Until there is, both are
     * sent together and each outcome is reported on its own: the old nesting
     * (relatives only after parents succeeded) left the family half-saved with
     * one generic error, and the user could not tell which half had gone in.
     */
    const outcome = <T>(call: Observable<T>) => call.pipe(
      map(value => ({ ok: true as const, value })),
      catchError(() => of({ ok: false as const, value: undefined as T | undefined }))
    );
    forkJoin({
      parents: outcome(this.studentdataService.replaceParents(this.studentId, parents)),
      relatives: outcome(this.studentdataService.replaceRelatives(this.studentId, relatives))
    }).pipe(finalize(() => this.savingFamily = false)).subscribe(({ parents: p, relatives: r }) => {
      if (p.ok) {
        this.parentEdit = p.value ?? [];
        if (this.profile) this.profile['parents'] = p.value;
      }
      if (r.ok) {
        this.relativeEdit = (r.value ?? []).map(rel => ({
          ...rel,
          selectedStudent: rel.relatedStudentId ? {
            studentId: rel.relatedStudentId,
            studentName: rel.relatedStudentName ?? ''
          } : null
        }));
        if (this.profile) this.profile['relatives'] = r.value;
      }

      if (p.ok && r.ok) {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENTS.PROFILE_PAGE.FAMILY_SAVED')
        });
        return;
      }
      const failedKey = !p.ok && !r.ok
        ? 'STUDENTS.PROFILE_PAGE.FAMILY_SAVE_ERROR'
        : (!p.ok ? 'STUDENTS.PROFILE_PAGE.FAMILY_PARENTS_FAILED' : 'STUDENTS.PROFILE_PAGE.FAMILY_RELATIVES_FAILED');
      this.messageService.add({
        severity: 'error',
        summary: this.translationService.translate('COMMON.ERROR'),
        detail: this.translationService.translate(failedKey),
        life: 8000
      });
    });
  }

  resetDeathCaseForm(): void {
    const levelId = Number(this.studentSnapshot?.['studentCurrentLevelId'] ?? 0) || undefined;
    this.deathCaseForm = {
      dateOfDeath: null,
      levelAtDeathId: levelId,
      studyYearAtDeath: (this.profile?.['progress'] as any)?.currentYearOrder,
      placeOfDeath: '',
      cause: '',
      notes: ''
    };
  }

  private formatDateForApi(value: Date | string | null | undefined): string {
    if (!value) return '';
    const d = value instanceof Date ? value : new Date(value);
    if (isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  createDeathCase(): void {
    if (!this.deathCaseForm.dateOfDeath) {
      this.messageService.add({
        severity: 'error',
        summary: this.translationService.translate('COMMON.ERROR'),
        detail: this.translationService.translate('STUDENTS.PROFILE_PAGE.DEATH_DATE_REQUIRED')
      });
      return;
    }
    this.savingDeathCase = true;
    const payload: StudentDeathCaseRequest = {
      dateOfDeath: this.formatDateForApi(this.deathCaseForm.dateOfDeath),
      levelAtDeathId: this.deathCaseForm.levelAtDeathId,
      studyYearAtDeath: this.deathCaseForm.studyYearAtDeath,
      placeOfDeath: this.deathCaseForm.placeOfDeath,
      cause: this.deathCaseForm.cause,
      notes: this.deathCaseForm.notes
    };
    this.studentdataService.createDeathCase(this.studentId, payload).subscribe({
      next: created => {
        this.deathCase = created;
        if (this.profile) {
          this.profile['deathCase'] = created;
          if (this.studentSnapshot) {
            this.studentSnapshot['studentStudyStatusId'] = created ? undefined : this.studentSnapshot['studentStudyStatusId'];
          }
        }
        this.savingDeathCase = false;
        this.loadProfile();
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENTS.PROFILE_PAGE.DEATH_CASE_CREATED')
        });
      },
      error: err => {
        this.savingDeathCase = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: err?.error?.message || this.translationService.translate('STUDENTS.PROFILE_PAGE.DEATH_CASE_CREATE_ERROR')
        });
      }
    });
  }

  toggleDeathStep(step: { stepId?: number; isCompleted?: boolean }, completed: boolean): void {
    if (!step?.stepId) return;
    this.studentdataService.updateDeathProcedureStep(this.studentId, step.stepId, { isCompleted: completed }).subscribe({
      next: updated => {
        if (this.deathCase?.steps) {
          const idx = this.deathCase.steps.findIndex(s => s.stepId === updated.stepId);
          if (idx >= 0) {
            this.deathCase.steps[idx] = updated;
          }
        }
      },
      error: err => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: err?.error?.message || this.translationService.translate('STUDENTS.PROFILE_PAGE.DEATH_STEP_UPDATE_ERROR')
        });
      }
    });
  }

  closeDeathCase(): void {
    this.closingDeathCase = true;
    this.studentdataService.closeDeathCase(this.studentId).subscribe({
      next: closed => {
        this.deathCase = closed;
        if (this.profile) this.profile['deathCase'] = closed;
        this.closingDeathCase = false;
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENTS.PROFILE_PAGE.DEATH_CASE_CLOSED')
        });
      },
      error: err => {
        this.closingDeathCase = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: err?.error?.message || this.translationService.translate('STUDENTS.PROFILE_PAGE.DEATH_CASE_CLOSE_ERROR')
        });
      }
    });
  }

  deathProcedureLabel(step: { procedureNameAr?: string; procedureNameEn?: string }): string {
    return this.translationService.getCurrentLanguage() === 'en'
      ? (step.procedureNameEn || step.procedureNameAr || '—')
      : (step.procedureNameAr || step.procedureNameEn || '—');
  }

  studyStatusSeverity(statusId: any): string {
    const id = Number(statusId);
    if (id === 2) return 'success';
    if (id === 4) return 'info';
    if (id === 3) return 'warning';
    if (id === 6 || id === 7) return 'danger';
    return 'secondary';
  }
}
