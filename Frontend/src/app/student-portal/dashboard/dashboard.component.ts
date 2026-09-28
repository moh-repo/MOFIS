import { Component, OnInit } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import {
  StudentPortalApiService,
  Profile,
  MyData,
  StudentProgressDto
} from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';

@Component({
    selector: 'app-student-portal-dashboard',
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss'],
    standalone: false
})
export class StudentPortalDashboardComponent implements OnInit {

  profile: Profile | null = null;
  myData: MyData | null = null;
  progress: StudentProgressDto | null = null;
  documentCount = 0;
  /** Information requests waiting for the student's answer: PENDING and OVERDUE. */
  pendingRequestsCount = 0;
  /** The student's own change requests still under review. */
  pendingChangesCount = 0;
  /** Inquiries the ministry has answered. */
  answeredInquiriesCount = 0;
  loading = true;

  /**
   * Review T1: four things to act on, each with its count, instead of a grid
   * repeating the nine destinations the side menu already lists. The rest of
   * the portal is one click away in that menu.
   */
  get actionCards(): Array<{ route: string; icon: string; titleKey: string; hintKey: string; count: number; attention: boolean; failed: boolean }> {
    const card = (route: string, icon: string, titleKey: string, hintKey: string, source: string, count: number, attention: boolean) => {
      const failed = this.failedCounts.has(source);
      return { route, icon, titleKey, hintKey, count, attention: attention && !failed, failed };
    };
    return [
      card('/student-portal/info-requests', 'form',      'INFO_REQUESTS', 'ACTION_INFO_REQUESTS_HINT', 'infoRequests',
        this.pendingRequestsCount,   this.pendingRequestsCount > 0),
      card('/student-portal/my-requests',   'edit',      'MY_REQUESTS',   'ACTION_MY_REQUESTS_HINT',   'changes',
        this.pendingChangesCount,    false),
      card('/student-portal/inquiries',     'message',   'INQUIRIES',     'ACTION_INQUIRIES_HINT',     'inquiries',
        this.answeredInquiriesCount, this.answeredInquiriesCount > 0),
      card('/student-portal/documents',     'file-text', 'DOCUMENTS',     'ACTION_DOCUMENTS_HINT',     'documents',
        this.documentCount,          false)
    ];
  }

  /**
   * The sources whose count could not be read. Their card shows a dash, not the
   * 0 the fallback left, which read as "nothing to do" (QA B9, N7).
   */
  failedCounts = new Set<string>();

  get countsFailed(): boolean {
    return this.failedCounts.size > 0;
  }

  constructor(private api: StudentPortalApiService,
              private translationService: TranslationService,
              private toast: ToastService) {}

  ngOnInit(): void {
    this.load();
  }

  /** Also the retry under the cards. */
  load(): void {
    this.loading = true;
    const failedCounts = new Set<string>();
    /*
     * Each card degrades on its own (a null profile still lets the documents count
     * show), but a failure is still said once: before this, every catchError
     * swallowed silently and a dead backend looked like a student with nothing.
     */
    let failed = false;
    const quiet = <T>(fallback: T, source?: string) => catchError(() => {
      failed = true;
      if (source) failedCounts.add(source);
      return of(fallback);
    });

    forkJoin({
      profile:      this.api.getProfile().pipe(quiet(null)),
      myData:       this.api.getMyData().pipe(quiet(null)),
      progress:     this.api.getProgress().pipe(quiet(null)),
      documents:    this.api.getDocuments().pipe(quiet([], 'documents')),
      infoRequests: this.api.getInfoRequests(0, 1, 'PENDING').pipe(quiet({ content: [], totalElements: 0 }, 'infoRequests')),
      overdue:      this.api.getInfoRequests(0, 1, 'OVERDUE').pipe(quiet({ content: [], totalElements: 0 }, 'infoRequests')),
      changes:      this.api.getMyChangeRequests().pipe(quiet([], 'changes')),
      // One student's own inquiries; the first hundred is all of them in practice.
      inquiries:    this.api.getInquiries(0, 100).pipe(quiet({ content: [], totalElements: 0, totalPages: 0 }, 'inquiries'))
    }).pipe(finalize(() => this.loading = false)).subscribe({
      next: r => {
        this.profile  = r.profile  as Profile | null;
        this.myData   = r.myData   as MyData  | null;
        this.progress = r.progress as StudentProgressDto | null;
        this.documentCount        = (r.documents as any[]).length;
        this.pendingRequestsCount = ((r.infoRequests as any).totalElements ?? 0) + ((r.overdue as any).totalElements ?? 0);
        this.pendingChangesCount = (r.changes as any[]).filter(c => c.status === 'PENDING').length;
        this.answeredInquiriesCount = ((r.inquiries as any).content ?? []).filter((i: any) => i.status === 'RESPONDED').length;
        this.failedCounts = failedCounts;
        if (failed) {
          this.toast.warning(this.translationService.translate('STUDENT_PORTAL.LOAD_DASHBOARD_PARTIAL'));
        }
      },
      error: () => this.toast.error(this.translationService.translate('STUDENT_PORTAL.LOAD_MY_DATA_ERROR'))
    });
  }

  get studentInitials(): string {
    const name = this.profile?.studentName || '';
    return name.trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase() || '?';
  }

  get passportExpiryDays(): number | null {
    const exp = this.myData?.student?.studentPassportExpireDate as string | undefined;
    if (!exp) return null;
    return Math.ceil((new Date(exp).getTime() - Date.now()) / 86_400_000);
  }

  get passportAlertType(): 'error' | 'warning' | null {
    const d = this.passportExpiryDays;
    if (d === null) return null;
    if (d <= 0)  return 'error';
    if (d <= 90) return 'warning';
    return null;
  }

  get progressPercent(): number {
    return Math.round(this.progress?.progressPercent ?? 0);
  }
}
