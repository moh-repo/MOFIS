import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Table } from 'primeng/table';
import { TranslationService } from '../../../service/translation.service';
import { environment } from '../../../../../environments/environment';

const BASE = `${environment.apiUrl}/mohe/api/v1/student-admin/roles`;

@Component({
    selector: 'app-student-roles',
    templateUrl: './student-roles.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentRolesComponent implements OnInit {

  roles: any[] = [];
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
  rolePermissions: any[] = [];
  /** Every permission there is, for the grant/revoke list in the view dialog. */
  allPermissions: any[] = [];
  /** Narrows the grant/revoke list in the view dialog (review M11). */
  permissionFilter = '';

  get filteredPermissions(): any[] {
    const q = this.permissionFilter.trim().toLowerCase();
    if (!q) return this.allPermissions;
    // The Arabic name too (W4-09, review M11): an Arabic reader types the name they see.
    return this.allPermissions.filter(p =>
      [p.permissionName, p.permissionNameAr, p.permissionCode, p.actionType]
        .some(v => String(v ?? '').toLowerCase().includes(q)));
  }

  /** A permission's name in the reader's language; the other one when it has none. */
  permissionLabel(p: { permissionName?: string | null; permissionNameAr?: string | null }): string {
    const ar = this.t.getCurrentLanguage() === 'ar';
    return (ar ? p.permissionNameAr || p.permissionName : p.permissionName || p.permissionNameAr) ?? '';
  }
  permissionsLoading = false;
  selectedRole: any = null;

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
      next: d => { this.roles = d; this.loading = false; },
      error: () => { this.loading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.LOAD_ERROR') }); }
    });
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

  openEdit(role: any): void {
    if (role.isSystem) { this.messageService.add({ severity: 'warn', summary: this.t.translate('COMMON.WARNING'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.SYSTEM_CANNOT_EDIT') }); return; }
    this.form = { ...role }; this.editMode = true; this.submitted = false; this.showDialog = true;
  }

  save(): void {
    this.submitted = true;
    if (!this.form.roleName?.trim()) {
      return;
    }
    this.saving = true;
    const req = this.editMode
      ? this.http.put(`${BASE}/${this.form.roleId}`, this.form)
      : this.http.post(BASE, this.form);
    req.subscribe({
      next: () => { this.saving = false; this.showDialog = false; this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.SUCCESS'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.SAVED') }); },
      error: err => { this.saving = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: err.error?.error || this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.FAILED') }); }
    });
  }

  /** Deleting used to be one click; a role carries permissions, so it asks first. */
  deleteRole(role: any): void {
    if (role.isSystem) { this.messageService.add({ severity: 'warn', summary: this.t.translate('COMMON.WARNING'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.SYSTEM_CANNOT_DELETE') }); return; }
    this.confirmation.confirm({
      header: this.t.translate('COMMON.DELETE'),
      message: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.CONFIRM_DELETE').replace('{name}', role.roleName ?? ''),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      acceptLabel: this.t.translate('COMMON.YES'),
      rejectLabel: this.t.translate('COMMON.NO'),
      accept: () => this.performDelete(role)
    });
  }

  private performDelete(role: any): void {
    this.http.delete(`${BASE}/${role.roleId}`).subscribe({
      next: () => { this.load(); this.messageService.add({ severity: 'success', summary: this.t.translate('COMMON.DELETED'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.DELETED') }); },
      error: err => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: err.error?.error || this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.FAILED') })
    });
  }

  /** The view opens at once and the permissions arrive into it; a slow list should not hold the dialog back. */
  openView(role: any): void {
    this.selectedRole = role;
    this.rolePermissions = [];
    this.permissionsLoading = true;
    this.permissionFilter = '';
    this.showViewDialog = true;
    this.loadAllPermissions();
    this.http.get<any[]>(`${BASE}/${role.roleId}/permissions`).subscribe({
      next: d => { this.rolePermissions = d; this.permissionsLoading = false; },
      error: () => { this.permissionsLoading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.LOAD_PERMISSIONS_ERROR') }); }
    });
  }

  editFromView(): void {
    const role = this.selectedRole;
    this.showViewDialog = false;
    if (role) { this.openEdit(role); }
  }

  /** The catalogue, loaded once and kept. */
  private loadAllPermissions(): void {
    if (this.allPermissions.length) { return; }
    this.http.get<any[]>(`${environment.apiUrl}/mohe/api/v1/student-admin/permissions`).subscribe({
      next: d => this.allPermissions = d ?? [],
      error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.LOAD_PERMISSIONS_ERROR') })
    });
  }

  hasPermission(permissionId: number): boolean {
    return this.rolePermissions.some(p => p.permissionId === permissionId);
  }

  /**
   * Grants or revokes one permission.
   *
   * <p>Both endpoints have existed since the portal admin screens were written
   * and nothing called either of them, so a role's permissions could be read
   * here and changed nowhere. One at a time, like the group's roles next door -
   * not a replace, so nothing can be cleared by accident.
   */
  togglePermission(permission: any): void {
    const id = permission.permissionId;
    if (this.hasPermission(id)) {
      this.http.delete(`${BASE}/${this.selectedRole.roleId}/permissions/${id}`).subscribe({
        next: () => this.rolePermissions = this.rolePermissions.filter(p => p.permissionId !== id),
        error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.REVOKE_PERMISSION_ERROR') })
      });
    } else {
      this.http.post(`${BASE}/${this.selectedRole.roleId}/permissions/${id}`, {}).subscribe({
        next: () => this.rolePermissions = [...this.rolePermissions, permission],
        error: () => this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.ROLES.MESSAGES.GRANT_PERMISSION_ERROR') })
      });
    }
  }
}
