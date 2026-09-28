import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { environment } from '../../../../../environments/environment';
import { changesOnly } from '../../../service/reload-streams.util';
import { followPage, resetPaging } from '../../../service/table-paging.util';

const BASE = `${environment.apiUrl}/mohe/api/v1/student-admin/users`;
const GROUPS_BASE = `${environment.apiUrl}/mohe/api/v1/student-admin/groups`;

@Component({
    selector: 'app-student-users',
    templateUrl: './student-users.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudentUsersComponent implements OnInit {

  users: any[] = [];

  /** The account the detail dialog is showing. */
  selectedUser: any = null;
  showViewDialog = false;
  totalRecords = 0;
  loading = false;
  page = 0;
  rows = 20;
  /** Bound to the table, so a search that reloads page one moves the paginator there too (QA B8, N9). */
  first = 0;
  searchTerm = '';
  selectedStatus: string | null = null;

  statusOptions: { label: string; value: string | null }[] = [];

  // Reset password dialog
  showResetDialog = false;
  resetUserId: number | null = null;
  resetUserName = '';
  newPassword = '';
  resetLoading = false;
  /** The rule is shown under the field once Confirm has been pressed, not before. */
  resetSubmitted = false;

  // Groups dialog
  showGroupsDialog = false;
  selectedUserGroups: any[] = [];
  allGroups: any[] = [];
  /** Narrows the groups list in the groups dialog (review M11). */
  groupFilter = '';

  get filteredGroups(): any[] {
    const q = this.groupFilter.trim().toLowerCase();
    if (!q) return this.allGroups;
    return this.allGroups.filter(g =>
      [g.groupName, g.groupNameAr, g.groupCode].some(v => String(v ?? '').toLowerCase().includes(q)));
  }
  groupsUserId: number | null = null;
  groupsUserName = '';
  groupsLoading = false;

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public t: TranslationService,
    private lookupService: LookupService
  ) {}

  ngOnInit(): void {
    this.loadStatusOptions();
    this.load();
    this.loadAllGroups();
    this.t.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadStatusOptions());
  }

  private loadStatusOptions(): void {
    this.lookupService.getStudentPortalAccountStatuses().subscribe(statuses => {
      this.statusOptions = [
        { label: this.t.translate('COMMON.ALL'), value: null },
        ...statuses
      ];
    });
  }

  load(): void {
    this.loading = true;
    let params = new HttpParams().set('page', this.page).set('size', this.rows).set('search', this.searchTerm);
    if (this.selectedStatus) params = params.set('status', this.selectedStatus);
    this.http.get<any>(BASE, { params }).subscribe({
      next: d => { this.users = d.content; this.totalRecords = d.totalElements; this.loading = false; },
      error: () => { this.loading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.USERS.MESSAGES.LOAD_ERROR') }); }
    });
  }

  loadAllGroups(): void {
    this.http.get<any[]>(GROUPS_BASE).subscribe({
      next: d => this.allGroups = d, error: () => {}
    });
  }

  onPage(e: any): void { followPage(this, e); this.load(); }
  onSearch(): void { resetPaging(this); this.load(); }
  onFilter(): void { resetPaging(this); this.load(); }

  get hasFilters(): boolean {
    return !!this.searchTerm || this.selectedStatus != null;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedStatus = null;
    resetPaging(this);
    this.load();
  }

  /** The status code's label from the lookup the filter offers, so the cell and the filter agree. */
  statusLabel(status: string): string {
    return this.statusOptions.find(o => o.value === status)?.label ?? status;
  }

  get passwordValid(): boolean {
    return !!this.newPassword && this.newPassword.length >= 8;
  }

  updateStatus(user: any, status: string): void {
    this.http.put(`${BASE}/${user.studentUserId}/status`, { status }).subscribe({
      next: () => { user.status = status; if (status === 'ACTIVE') user.failedLoginAttempts = 0; this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.UPDATED'), detail: this.t.translate('STUDENT_ADMIN.USERS.MESSAGES.STATUS_UPDATED').replace('{status}', status) }); },
      error: err => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: err.error?.error || this.t.translate('STUDENT_ADMIN.USERS.MESSAGES.FAILED') })
    });
  }

  openReset(user: any): void {
    this.resetUserId = user.studentUserId;
    this.resetUserName = user.username;
    this.newPassword = '';
    this.resetSubmitted = false;
    this.showResetDialog = true;
  }

  submitReset(): void {
    this.resetSubmitted = true;
    if (!this.passwordValid) {
      return;
    }
    this.resetLoading = true;
    this.http.post(`${BASE}/${this.resetUserId}/reset-password`, { newPassword: this.newPassword }).subscribe({
      // Re-read: a reset also unlocks a LOCKED account server-side, so without
      // this the row went on showing LOCKED after a reset that had worked.
      next: () => { this.resetLoading = false; this.showResetDialog = false; this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.SUCCESS'), detail: this.t.translate('STUDENT_ADMIN.USERS.MESSAGES.PASSWORD_RESET_SUCCESS') }); },
      error: err => { this.resetLoading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: err.error?.error || this.t.translate('STUDENT_ADMIN.USERS.MESSAGES.FAILED') }); }
    });
  }

  openGroups(user: any): void {
    this.groupsUserId = user.studentUserId;
    this.groupsUserName = user.username;
    this.groupsLoading = true;
    this.groupFilter = '';
    this.showGroupsDialog = true;
    this.http.get<any[]>(`${BASE}/${user.studentUserId}/groups`).subscribe({
      next: d => { this.selectedUserGroups = d; this.groupsLoading = false; },
      error: () => this.groupsLoading = false
    });
  }

  hasGroup(groupId: number): boolean {
    return this.selectedUserGroups.some(g => g.groupId === groupId);
  }

  toggleGroup(groupId: number): void {
    if (this.hasGroup(groupId)) {
      this.http.delete(`${BASE}/${this.groupsUserId}/groups/${groupId}`).subscribe({
        next: () => this.selectedUserGroups = this.selectedUserGroups.filter(g => g.groupId !== groupId),
        error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.USERS.MESSAGES.REMOVE_GROUP_ERROR') })
      });
    } else {
      this.http.post(`${BASE}/${this.groupsUserId}/groups/${groupId}`, {}).subscribe({
        next: () => this.selectedUserGroups.push({ groupId }),
        error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.USERS.MESSAGES.ASSIGN_GROUP_ERROR') })
      });
    }
  }

  statusSeverity(s: string): string {
    return s === 'ACTIVE' ? 'success' : s === 'LOCKED' ? 'danger' : 'warning';
  }

  openView(user: any): void {
    this.selectedUser = user;
    this.showViewDialog = true;
  }
}
