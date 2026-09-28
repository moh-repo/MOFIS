import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { environment } from '../../../../../environments/environment';
import { followPage, resetPaging } from '../../../service/table-paging.util';

const BASE = `${environment.apiUrl}/mohe/api/v1/student-admin/sessions`;

@Component({
    selector: 'app-student-sessions',
    templateUrl: './student-sessions.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentSessionsComponent implements OnInit {

  sessions: any[] = [];
  totalRecords = 0;
  loading = false;
  page = 0;
  rows = 20;
  /** Bound to the table, so a filter that reloads page one moves the paginator there too (QA B8, N9). */
  first = 0;

  filterUserId: number | null = null;
  activeOnly = false;
  statusOptions: { label: string; value: boolean }[] = [];
  purging = false;

  now = new Date();

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public t: TranslationService
  ) {}

  ngOnInit(): void {
    this.buildOptions();
    this.load();
    this.t.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.buildOptions());
  }

  private buildOptions(): void {
    this.statusOptions = [
      { label: this.t.translate('COMMON.ALL'), value: false },
      { label: this.t.translate('STUDENT_ADMIN.SESSIONS.ACTIVE_ONLY'), value: true }
    ];
  }

  load(): void {
    this.loading = true;
    this.now = new Date();
    let params = new HttpParams().set('page', this.page).set('size', this.rows);
    if (this.filterUserId) params = params.set('studentUserId', this.filterUserId);
    if (this.activeOnly)   params = params.set('activeOnly', 'true');

    this.http.get<any>(BASE, { params }).subscribe({
      next: d => { this.sessions = d.content; this.totalRecords = d.totalElements; this.loading = false; },
      error: () => { this.loading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.SESSIONS.MESSAGES.LOAD_ERROR') }); }
    });
  }

  onPage(e: any): void { followPage(this, e); this.load(); }

  onFilter(): void { resetPaging(this); this.load(); }

  get hasFilters(): boolean {
    return !!this.filterUserId || this.activeOnly;
  }

  clearFilters(): void {
    this.filterUserId = null;
    this.activeOnly = false;
    resetPaging(this);
    this.load();
  }

  sessionStatus(s: any): 'active' | 'logged-out' | 'expired' {
    if (s.loggedOutAt) return 'logged-out';
    if (new Date(s.expiresAt) <= this.now) return 'expired';
    return 'active';
  }

  statusSeverity(s: any): string {
    const st = this.sessionStatus(s);
    return st === 'active' ? 'success' : st === 'logged-out' ? 'warning' : 'danger';
  }

  statusLabel(s: any): string {
    const st = this.sessionStatus(s);
    if (st === 'active')     return this.t.translate('STUDENT_ADMIN.SESSIONS.ACTIVE');
    if (st === 'logged-out') return this.t.translate('STUDENT_ADMIN.SESSIONS.LOGGED_OUT');
    return this.t.translate('STUDENT_ADMIN.SESSIONS.EXPIRED');
  }

  isExpired(expiresAt: string): boolean {
    return new Date(expiresAt) <= this.now;
  }

  revoke(session: any): void {
    this.confirmationService.confirm({
      message: this.t.translate('STUDENT_ADMIN.SESSIONS.CONFIRM_REVOKE'),
      header: this.t.translate('STUDENT_ADMIN.SESSIONS.REVOKE'),
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.http.delete(`${BASE}/${session.sessionId}`).subscribe({
          next: () => { this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.SUCCESS'), detail: this.t.translate('STUDENT_ADMIN.SESSIONS.REVOKED') }); },
          error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.SESSIONS.MESSAGES.REVOKE_ERROR') })
        });
      }
    });
  }

  revokeAllForUser(userId: number): void {
    this.confirmationService.confirm({
      message: this.t.translate('STUDENT_ADMIN.SESSIONS.CONFIRM_REVOKE_ALL_USER'),
      header: this.t.translate('STUDENT_ADMIN.SESSIONS.REVOKE_ALL_USER'),
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.http.delete(`${BASE}/user/${userId}`).subscribe({
          next: () => { this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.SUCCESS'), detail: this.t.translate('STUDENT_ADMIN.SESSIONS.ALL_REVOKED') }); },
          error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.SESSIONS.MESSAGES.FAILED') })
        });
      }
    });
  }

  purgeExpired(): void {
    // The widest delete on the page - it sweeps every expired session for every
    // user - and the only one that used to fire straight off the button while
    // both narrower ones asked first.
    this.confirmationService.confirm({
      message: this.t.translate('STUDENT_ADMIN.SESSIONS.CONFIRM_PURGE_EXPIRED'),
      header: this.t.translate('STUDENT_ADMIN.SESSIONS.PURGE_EXPIRED'),
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.performPurgeExpired()
    });
  }

  private performPurgeExpired(): void {
    this.purging = true;
    this.http.delete(`${BASE}/expired`).subscribe({
      next: () => { this.purging = false; this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.SUCCESS'), detail: this.t.translate('STUDENT_ADMIN.SESSIONS.PURGED') }); },
      error: () => { this.purging = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.SESSIONS.MESSAGES.PURGE_ERROR') }); }
    });
  }
}
