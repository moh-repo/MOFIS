import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Table } from 'primeng/table';
import { TranslationService } from '../../../service/translation.service';
import { environment } from '../../../../../environments/environment';

const BASE       = `${environment.apiUrl}/mohe/api/v1/student-admin/groups`;
const ROLES_BASE = `${environment.apiUrl}/mohe/api/v1/student-admin/roles`;

@Component({
    selector: 'app-student-groups',
    templateUrl: './student-groups.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentGroupsComponent implements OnInit {

  groups: any[] = [];
  loading = false;

  // Filters. The list is held whole, so the table narrows itself.
  filterSearch = '';
  filterActive: boolean | null = null;
  activeOptions: { label: string; value: boolean | null }[] = [];

  showDialog = false;
  editMode = false;
  saving = false;
  form: any = {};
  /** The required-field message is shown once Save has been pressed, not before. */
  submitted = false;

  showViewDialog = false;
  showRolesDialog = false;
  groupRoles: any[] = [];
  rolesLoading = false;
  allRoles: any[] = [];
  /** Narrows the roles list in the manage dialog (review M11). */
  roleFilter = '';

  get filteredRoles(): any[] {
    const q = this.roleFilter.trim().toLowerCase();
    if (!q) return this.allRoles;
    return this.allRoles.filter(r =>
      [r.roleName, r.roleNameAr, r.roleCode].some(v => String(v ?? '').toLowerCase().includes(q)));
  }
  selectedGroup: any = null;

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    private confirmation: ConfirmationService,
    public t: TranslationService
  ) {}

  ngOnInit(): void {
    this.buildOptions();
    this.load();
    this.loadAllRoles();
    this.t.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.buildOptions());
  }

  private buildOptions(): void {
    this.activeOptions = [
      { label: this.t.translate('COMMON.ALL'), value: null },
      { label: this.t.translate('COMMON.ACTIVE'), value: true },
      { label: this.t.translate('COMMON.INACTIVE'), value: false }
    ];
  }

  load(): void {
    this.loading = true;
    this.http.get<any[]>(BASE).subscribe({
      next: d => { this.groups = d; this.loading = false; },
      error: () => { this.loading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.LOAD_ERROR') }); }
    });
  }

  loadAllRoles(): void {
    this.http.get<any[]>(ROLES_BASE).subscribe({ next: d => this.allRoles = d, error: () => {} });
  }

  get hasFilters(): boolean {
    return !!this.filterSearch || this.filterActive !== null;
  }

  clearFilters(dt: Table): void {
    this.filterSearch = '';
    this.filterActive = null;
    dt.filterGlobal('', 'contains');
    dt.filter(null, 'isActive', 'equals');
  }

  openNew(): void { this.form = { isActive: true }; this.editMode = false; this.submitted = false; this.showDialog = true; }

  openEdit(group: any): void {
    if (group.isSystem) { this.messageService.add({ severity: 'warn', summary: this.t.translate('COMMON.WARNING'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.SYSTEM_CANNOT_EDIT') }); return; }
    this.form = { ...group }; this.editMode = true; this.submitted = false; this.showDialog = true;
  }

  save(): void {
    this.submitted = true;
    if (!this.form.groupName?.trim()) {
      return;
    }
    this.saving = true;
    const req = this.editMode
      ? this.http.put(`${BASE}/${this.form.groupId}`, this.form)
      : this.http.post(BASE, this.form);
    req.subscribe({
      next: () => { this.saving = false; this.showDialog = false; this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.SUCCESS'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.SAVED') }); },
      error: err => { this.saving = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: err.error?.error || this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.FAILED') }); }
    });
  }

  /** Deleting used to be one click; a group carries role assignments, so it asks first. */
  deleteGroup(group: any): void {
    if (group.isSystem) { this.messageService.add({ severity: 'warn', summary: this.t.translate('COMMON.WARNING'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.SYSTEM_CANNOT_DELETE') }); return; }
    this.confirmation.confirm({
      header: this.t.translate('COMMON.DELETE'),
      message: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.CONFIRM_DELETE').replace('{name}', group.groupName ?? ''),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      acceptLabel: this.t.translate('COMMON.YES'),
      rejectLabel: this.t.translate('COMMON.NO'),
      accept: () => this.performDelete(group)
    });
  }

  private performDelete(group: any): void {
    this.http.delete(`${BASE}/${group.groupId}`).subscribe({
      next: () => { this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.DELETED'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.DELETED') }); },
      error: err => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: err.error?.error || this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.FAILED') })
    });
  }

  /** The dialog opens at once and the roles arrive into it. */
  private loadGroupRoles(group: any): void {
    this.selectedGroup = group;
    this.groupRoles = [];
    this.rolesLoading = true;
    this.http.get<any[]>(`${BASE}/${group.groupId}/roles`).subscribe({
      next: d => { this.groupRoles = d; this.rolesLoading = false; },
      error: () => { this.rolesLoading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.LOAD_ROLES_ERROR') }); }
    });
  }

  openView(group: any): void {
    this.loadGroupRoles(group);
    this.showViewDialog = true;
  }

  openManageRoles(group: any): void {
    this.loadGroupRoles(group);
    this.roleFilter = '';
    this.showRolesDialog = true;
  }

  editFromView(): void {
    const group = this.selectedGroup;
    this.showViewDialog = false;
    if (group) { this.openEdit(group); }
  }

  manageFromView(): void {
    this.showViewDialog = false;
    this.roleFilter = '';
    this.showRolesDialog = true;
  }

  /** The assignment rows carry only the id; the name comes from the full list. */
  roleName(assigned: any): string {
    const role = this.allRoles.find(r => r.roleId === assigned.roleId);
    return role?.roleName ?? assigned.roleName ?? String(assigned.roleId);
  }

  hasRole(roleId: number): boolean { return this.groupRoles.some(r => r.roleId === roleId); }

  toggleRole(roleId: number): void {
    if (this.hasRole(roleId)) {
      this.http.delete(`${BASE}/${this.selectedGroup.groupId}/roles/${roleId}`).subscribe({
        next: () => this.groupRoles = this.groupRoles.filter(r => r.roleId !== roleId),
        error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.REMOVE_ROLE_ERROR') })
      });
    } else {
      this.http.post(`${BASE}/${this.selectedGroup.groupId}/roles/${roleId}`, {}).subscribe({
        next: () => this.groupRoles.push({ roleId }),
        error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.GROUPS.MESSAGES.ASSIGN_ROLE_ERROR') })
      });
    }
  }
}
