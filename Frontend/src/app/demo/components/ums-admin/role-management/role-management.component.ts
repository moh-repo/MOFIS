import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';

// NG-ZORRO Imports
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { ToastService } from '../../../service/toast.service';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';

import { UmsRoleService } from '../../../service/ums-role.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { UmsRole, UmsPermission } from '../../../api/ums';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { TranslationService } from '../../../service/translation.service';
import { extractApiErrorMessage } from '../../../service/api-error.util';

@Component({
    selector: 'app-role-management',
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        TranslatePipe,
        NzTableModule,
        NzButtonModule,
        NzInputModule,
        NzIconModule,
        NzModalModule,
        NzFormModule,
        NzTagModule,
        NzInputNumberModule,
        NzCardModule,
        NzBadgeModule,
        NzCheckboxModule,
        NzDividerModule,
        NzCollapseModule,
        NzTooltipModule,
        NzSelectModule,
        NzGridModule,
        NzSwitchModule,
        NzDescriptionsModule,
        HasPermissionDirective
    ],
    template: `
    <!-- .ums-admin-page rather than .grid > .col-12. PrimeFlex gives .grid a
         margin of -0.5rem and hands the matching padding to the column, so a
         grid holding one full-width column is 1rem — 13px at this application's
         12.6px root — wider than the box it sits in, and every one of these nine
         pages overflowed the main container by exactly that. There is one column
         here; the card is it. The class is the user-creation wizard's, now
         defined in assets/layout/styles/_ums-admin.scss. (No backticks here:
         this is an inline template.) -->
    <div class="ums-admin-page">
      <div class="card">
        <!-- Header Section. flex-1 on the icon-and-title block, not
             justify-content-between: under direction: rtl a flex row already
             runs right to left, and the global rule in styles.scss reverses every
             .flex.justify-content-between again, which put the heading back on
             the left and the buttons on the right in Arabic. See
             src/app/demo/components/page-headings.spec.ts. (No backticks here:
             this is an inline template.) -->
        <div class="flex align-items-center mb-4 flex-wrap gap-3">
          <div class="flex align-items-center gap-3 flex-1">
            <i class="pi pi-key text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.ROLES.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.ROLES.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'" (click)="showCreateModal()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.ROLES.ADD_ROLE' | translate }}
            </button>
            <button nz-button (click)="loadRolesList()" [nzLoading]="loading">
              <span nz-icon nzType="reload"></span>
              {{ 'COMMON.REFRESH' | translate }}
            </button>
          </div>
        </div>
  
        <!-- The permission catalogue failed to load: said on the page, not only in a
             toast, because the permissions button then opens nothing (live QA M3). -->
        @if (permissionsLoadFailed) {
          <div class="flex align-items-center gap-3 flex-wrap p-3 mb-3 border-round border-1 border-red-200 bg-red-50 text-red-700"
               role="alert" data-testid="permissions-load-failed">
            <span nz-icon nzType="warning" nzTheme="outline"></span>
            <span class="flex-1">{{ 'UMS.ROLES.PERMISSIONS_LOAD_FAILED' | translate }}</span>
            <button nz-button nzSize="small" (click)="loadAllPermissions()">
              <span nz-icon nzType="reload"></span>
              {{ 'UMS.ROLES.PERMISSIONS_RETRY' | translate }}
            </button>
          </div>
        }

        <!-- Filters. Shared layout: assets/layout/styles/_filter-bar.scss.
             Narrowing is the server's job: the table holds one page at a time. -->
        <div class="filter-bar">
          <div class="filter-bar__grid filter-bar__grid--cols-3">
            <div class="filter-bar__field filter-bar__field--wide" [class.filter-bar__field--active]="searchKeyword">
              <label for="urSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="urSearch" type="text" nz-input [placeholder]="'UMS.ROLES.SEARCH_PLACEHOLDER' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterKind">
              <label for="urKind">{{ 'UMS.SHARED.FILTER_TYPE' | translate }}</label>
              <nz-select nzId="urKind" [(ngModel)]="filterKind" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_TYPE_ALL' | translate" nzValue=""></nz-option>
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_TYPE_SYSTEM' | translate" nzValue="SYSTEM"></nz-option>
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_TYPE_CUSTOM' | translate" nzValue="CUSTOM"></nz-option>
              </nz-select>
            </div>
          </div>
          <div class="filter-bar__actions">
            <button nz-button nzType="primary" (click)="applySearchFilters()">
              <span nz-icon nzType="search"></span>
              {{ 'COMMON.SEARCH' | translate }}
            </button>
            <button nz-button [disabled]="!hasSearchFilters" (click)="clearSearchFilters()">
              <span nz-icon nzType="clear"></span>
              {{ 'COMMON.CLEAR' | translate }}
            </button>
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalRoles }}</span>
          </div>
        </div>
  
      <!-- Roles Table -->
      <nz-table
        #rolesTable
        [nzData]="roles"
        [nzLoading]="loading"
        [nzTotal]="totalRoles"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="roleName" [nzSortOrder]="sortOrderFor('roleName')" (nzSortOrderChange)="onSort('roleName', $event)">{{ 'UMS.ROLES.ROLE_NAME' | translate }}</th>
            <th nzShowSort nzColumnKey="roleNameAr" [nzSortOrder]="sortOrderFor('roleNameAr')" (nzSortOrderChange)="onSort('roleNameAr', $event)">{{ 'UMS.ROLES.ROLE_NAME_AR' | translate }}</th>
            <th nzShowSort nzColumnKey="description" [nzSortOrder]="sortOrderFor('description')" (nzSortOrderChange)="onSort('description', $event)">{{ 'UMS.ROLES.DESCRIPTION' | translate }}</th>
            <th nzShowSort nzColumnKey="priority" [nzSortOrder]="sortOrderFor('priority')" (nzSortOrderChange)="onSort('priority', $event)">{{ 'UMS.ROLES.PRIORITY' | translate }}</th>
            <th nzShowSort nzColumnKey="isSystem" [nzSortOrder]="sortOrderFor('isSystem')" (nzSortOrderChange)="onSort('isSystem', $event)">{{ 'UMS.ROLES.TYPE' | translate }}</th>
            <th nzWidth="200px">{{ 'UMS.ROLES.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (role of rolesTable.data; track role) {
            <tr>
              <td>{{ role.roleName }}</td>
              <td>{{ role.roleNameAr }}</td>
              <td>{{ role.description }}</td>
              <td>
                <nz-tag nzColor="blue">{{ role.priority }}</nz-tag>
              </td>
              <td>
                <nz-tag [nzColor]="role.isSystem ? 'gold' : 'green'">{{ role.isSystem ? ('UMS.ROLES.SYSTEM' | translate) : ('UMS.ROLES.CUSTOM' | translate) }}</nz-tag>
              </td>
              <td>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.VIEW'" (click)="showDetailModal(role)" [nz-tooltip]="'UMS.USERS.VIEW_DETAILS' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'" (click)="showEditModal(role)"
                  [disabled]="role.isSystem"
                  [nz-tooltip]="(role.isSystem ? 'UMS.SHARED.CANNOT_EDIT_SYSTEM_ROLE' : 'COMMON.EDIT') | translate">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'" (click)="showPermissionsModal(role)" [nz-tooltip]="'UMS.ROLES.VIEW_PERMISSIONS' | translate">
                  <span nz-icon nzType="key"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'"
                  [disabled]="role.isSystem" (click)="confirmDelete(role)" [nz-tooltip]="'COMMON.DELETE' | translate">
                  <span nz-icon nzType="delete"></span>
                </button>
              </td>
            </tr>
          }
        </tbody>
      </nz-table>
      </div>
    </div>
    
      <!-- Create/Edit Modal -->
      <nz-modal
        [(nzVisible)]="isRoleModalVisible"
        [nzTitle]="selectedRole ? ('UMS.ROLES.EDIT_ROLE' | translate) : ('UMS.ROLES.CREATE_ROLE' | translate)"
        (nzOnCancel)="handleRoleModalCancel()"
        (nzOnOk)="handleRoleModalOk()"
        [nzWidth]="720"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            <form nz-form [formGroup]="roleForm" nzLayout="vertical">
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="24">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.ROLES.ROLE_NAME' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="roleName" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                  </div>
                </div>
              </div>
    
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="translation"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_NAMES' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.ROLES.ROLE_NAME_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="roleNameAr" dir="rtl" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.ROLES.PRIORITY' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-input-number formControlName="priority" [nzMin]="0" [nzMax]="1000" style="width: 100%;"></nz-input-number>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                  </div>
                </div>
              </div>
    
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="file-text"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_DESCRIPTION' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.ROLES.DESCRIPTION' | translate }}</nz-form-label>
                        <nz-form-control>
                          <textarea nz-input formControlName="description" rows="2"></textarea>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.ROLES.DESCRIPTION_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <textarea nz-input formControlName="descriptionAr" rows="2" dir="rtl"></textarea>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                  </div>
                </div>
              </div>
    
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="setting"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_SETTINGS' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.ROLES.IS_ACTIVE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-switch formControlName="isActive"></nz-switch>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                  </div>
                </div>
              </div>
            </form>
          </div>
        </ng-container>
      </nz-modal>
    
      <!-- Role Detail View Modal -->
      <nz-modal
        [(nzVisible)]="isDetailModalVisible"
        [nzTitle]="'UMS.USERS.VIEW_DETAILS' | translate"
        [nzWidth]="720"
        [nzFooter]="roleDetailFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #roleDetailFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
          @if (detailRole) {
            <button nz-button nzType="primary" [disabled]="detailRole?.isSystem" (click)="isDetailModalVisible = false; showEditModal(detailRole!)">
              <span nz-icon nzType="edit"></span> {{ 'COMMON.EDIT' | translate }}
            </button>
          }
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailRole) {
            <div class="dv-view">
              <div class="dv-section">
              <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                <nz-descriptions-item [nzTitle]="'UMS.ROLES.ROLE_NAME' | translate">{{ detailRole.roleName }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.ROLES.ROLE_NAME_AR' | translate">{{ detailRole.roleNameAr || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.ROLES.DESCRIPTION' | translate" [nzSpan]="2">{{ detailRole.description || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.ROLES.PRIORITY' | translate"><nz-tag nzColor="blue">{{ detailRole.priority }}</nz-tag></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.ROLES.IS_ACTIVE' | translate">
                  <nz-badge [nzStatus]="detailRole.isActive ? 'success' : 'default'" [nzText]="detailRole.isActive ? ('UMS.STATUS.ACTIVE' | translate) : ('UMS.STATUS.INACTIVE' | translate)"></nz-badge>
                </nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.ROLES.TYPE' | translate">
                  <nz-tag [nzColor]="detailRole.isSystem ? 'gold' : 'green'">{{ detailRole.isSystem ? ('UMS.ROLES.SYSTEM' | translate) : ('UMS.ROLES.CUSTOM' | translate) }}</nz-tag>
                </nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.USERS.CREATED' | translate">{{ detailRole.createdAt ? (detailRole.createdAt | date:'medium') : ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
              </nz-descriptions>
              </div>
              @if (detailPermissionCount > 0) {
                <div class="dv-section">
                  <div class="dv-section__head"><span class="dv-section__icon"><span nz-icon nzType="key"></span></span><div class="dv-section__title">{{ 'UMS.ROLES.VIEW_PERMISSIONS' | translate }} ({{ detailPermissionCount }})</div></div>
                  <div>
                    <div class="flex flex-wrap gap-1">
                      @for (code of detailPermissionCodes.slice(0, 12); track code) {
                        <nz-tag>{{ code }}</nz-tag>
                      }
                      @if (detailPermissionCount > 12) {
                        <nz-tag nzColor="default">+{{ detailPermissionCount - 12 }}</nz-tag>
                      }
                    </div>
                  </div>
                </div>
              }
            </div>
          }
        </ng-container>
      </nz-modal>
    
      <!-- Permissions Modal -->
      <nz-modal
        [(nzVisible)]="isPermissionsModalVisible"
        [nzTitle]="'UMS.ROLES.ASSIGN_PERMISSIONS' | translate"
        (nzOnCancel)="handlePermissionsModalCancel()"
        (nzOnOk)="saveRolePermissions()"
        [nzWidth]="900"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            @if (selectedRole) {
              <div class="dv-hero">
                <div class="dv-hero__badge"><span nz-icon nzType="safety"></span></div>
                <div class="dv-hero__body">
                  <div class="dv-hero__eyebrow">{{ 'UMS.ROLES.ROLE_NAME' | translate }}</div>
                  <div class="dv-hero__title">{{ selectedRole.roleName }}</div>
                </div>
              </div>
            }
            <div class="dv-section">
              <nz-collapse>
                @for (pageGroup of permissionsByPage; track pageGroup) {
                  <nz-collapse-panel [nzHeader]="pageGroup.pageName" [nzActive]="true">
                    @for (perm of pageGroup.permissions; track perm) {
                      <div class="py-1">
                        <label nz-checkbox [(ngModel)]="perm.checked" [ngModelOptions]="{standalone: true}">
                          {{ perm.actionType }} - {{ perm.permissionName }}
                        </label>
                      </div>
                    }
                  </nz-collapse-panel>
                }
              </nz-collapse>
            </div>
          </div>
        </ng-container>
      </nz-modal>
    `,
    styles: [`
    :host ::ng-deep .ant-collapse-content-box {
      padding: 8px 16px;
    }
  `]
})
export class RoleManagementComponent implements OnInit {
  roles: UmsRole[] = [];
  allPermissions: UmsPermission[] = [];
  /** The permission catalogue could not be fetched; the page says so until a reload succeeds (live QA M3). */
  permissionsLoadFailed = false;

  loading = false;
  saving = false;
  searchKeyword = '';
  filterKind = '';
  sortBy = 'roleName';
  sortDir: 'asc' | 'desc' = 'asc';
  totalRoles = 0;
  pageIndex = 1;
  pageSize = 10;

  isRoleModalVisible = false;
  isPermissionsModalVisible = false;
  isDetailModalVisible = false;
  detailRole: UmsRole | null = null;
  detailPermissionCodes: string[] = [];
  detailPermissionCount = 0;
  selectedRole: UmsRole | null = null;
  roleForm!: FormGroup;

  permissionsByPage: { pageName: string; permissions: (UmsPermission & { checked: boolean })[] }[] = [];

  constructor(
    private roleService: UmsRoleService,
    public permissionService: UmsPermissionService,
    private http: HttpClient,
    private modal: NzModalService,
    private message: ToastService,
    private fb: FormBuilder,
    public translationService: TranslationService
  ) {
    this.roleForm = this.fb.group({
      roleName: ['', Validators.required],
      roleNameAr: [''],
      description: [''],
      descriptionAr: [''],
      priority: [0],
      isActive: [true]
    });
  }

  ngOnInit(): void {
    this.loadRolesList();
    this.loadAllPermissions();
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  sortOrderFor(column: string): 'ascend' | 'descend' | null {
    return this.sortBy === column ? (this.sortDir === 'desc' ? 'descend' : 'ascend') : null;
  }

  onSort(column: string, order: string | null): void {
    this.sortBy = column;
    if (order === 'descend') {
      this.sortDir = 'desc';
    } else if (order === 'ascend') {
      this.sortDir = 'asc';
    } else {
      this.sortBy = 'roleName';
      this.sortDir = 'asc';
    }
    this.pageIndex = 1;
    this.loadRolesList();
  }

  resolveSystemFlag(): boolean | undefined {
    if (this.filterKind === 'SYSTEM') return true;
    if (this.filterKind === 'CUSTOM') return false;
    return undefined;
  }

  /** Every filter back to its resting value, and the list from the first page. */
  clearSearchFilters(): void {
    this.searchKeyword = '';
    this.filterKind = '';
    this.applySearchFilters();
  }

  get hasSearchFilters(): boolean {
    return !!this.searchKeyword?.trim() || !!this.filterKind;
  }

  applySearchFilters(): void {
    this.pageIndex = 1;
    this.loadRolesList();
  }

  loadRolesList(): void {
    this.loading = true;
    this.roleService.getAllRoles(this.pageIndex - 1, this.pageSize, {
      keyword: this.searchKeyword?.trim(),
      system: this.resolveSystemFlag(),
      sortBy: this.sortBy,
      sortDir: this.sortDir
    }).subscribe({
      next: (response) => {
        this.roles = response.content;
        this.totalRoles = response.totalElements;
        this.loading = false;
      },
      error: () => {
        this.message.error(this.t('UMS.ROLES.FAILED_LOAD'));
        this.loading = false;
      }
    });
  }

  loadAllPermissions(): void {
    // The checkbox tree is built from this list, and saving replaces a role's
    // permissions with whatever is ticked. If this fetch fails silently the tree
    // is empty, and pressing Save then strips the role of everything it had — so
    // the failure has to be visible, and openable-ness is guarded on it below.
    //
    // Visible twice: the toast, which is gone in eight seconds, and a notice
    // that stays on the page until a reload succeeds. Two live tests (23 and
    // 24 Sep 2026, M3) clicked the permissions button after the load had failed
    // and saw nothing that explained why the dialog did not open.
    this.http.get<UmsPermission[]>(`${environment.apiUrl}/mohe/api/v1/ums/permissions/all`).subscribe({
      next: (permissions) => {
        this.allPermissions = permissions;
        this.permissionsLoadFailed = false;
      },
      error: () => {
        this.allPermissions = [];
        this.permissionsLoadFailed = true;
        this.message.error(this.t('UMS.ROLES.PERMISSIONS_LOAD_FAILED'));
      }
    });
  }

  onPageChange(pageIndex: number): void {
    this.pageIndex = pageIndex;
    this.loadRolesList();
  }

  onPageSizeChange(ps: number): void {
    this.pageSize = ps;
    this.pageIndex = 1;
    this.loadRolesList();
  }

  showCreateModal(): void {
    this.selectedRole = null;
    this.roleForm.reset({ roleName: '', roleNameAr: '', description: '', descriptionAr: '', priority: 0, isActive: true });
    this.isRoleModalVisible = true;
  }

  /**
   * Review M9: a system role is refused with 409, which used to surface as the
   * generic failure. The buttons are disabled for those rows; this covers a
   * stale list or a second tab.
   */
  private refusal(err: { status?: number; error?: { error?: string; message?: string } }, fallbackKey: string): string {
    if (err?.status === 409) {
      return this.t('UMS.SHARED.CANNOT_EDIT_SYSTEM_ROLE');
    }
    return extractApiErrorMessage(err) || this.t(fallbackKey);
  }

  showEditModal(role: UmsRole): void {
    this.selectedRole = role;
    this.roleForm.reset({
      roleName: role.roleName,
      roleNameAr: role.roleNameAr ?? '',
      description: role.description ?? '',
      descriptionAr: role.descriptionAr ?? '',
      priority: role.priority ?? 0,
      isActive: role.isActive ?? true
    });
    this.isRoleModalVisible = true;
  }

  showDetailModal(role: UmsRole): void {
    this.detailRole = role;
    this.detailPermissionCodes = [];
    this.detailPermissionCount = 0;
    this.roleService.getRolePermissions(role.id).subscribe({
      next: (perms) => {
        this.detailPermissionCodes = perms.map(p => p.permissionCode);
        this.detailPermissionCount = perms.length;
        this.isDetailModalVisible = true;
      },
      error: () => {
        this.isDetailModalVisible = true;
      }
    });
  }

  showPermissionsModal(role: UmsRole): void {
    // Nothing to choose from means nothing can be ticked, and saving an empty
    // tree would clear the role. Refuse to open rather than offer a dialog whose
    // only possible outcome is destructive; retry the load so a transient
    // failure recovers on the next click.
    if (!this.allPermissions.length) {
      this.permissionsLoadFailed = true;
      this.message.error(this.t('UMS.ROLES.PERMISSIONS_LOAD_FAILED'));
      this.loadAllPermissions();
      return;
    }

    this.selectedRole = role;

    this.roleService.getRolePermissions(role.id).subscribe({
      next: (rolePermissions) => {
        const rolePermissionIds = rolePermissions.map(p => p.id);

        // Group permissions by page
        const groupedMap = new Map<string, (UmsPermission & { checked: boolean })[]>();

        this.allPermissions.forEach(perm => {
          const parts = perm.permissionCode.split('.');
          const pageName = parts[0] || 'Other';

          if (!groupedMap.has(pageName)) {
            groupedMap.set(pageName, []);
          }

          groupedMap.get(pageName)!.push({
            ...perm,
            checked: rolePermissionIds.includes(perm.id)
          });
        });

        this.permissionsByPage = Array.from(groupedMap.entries()).map(([pageName, permissions]) => ({
          pageName,
          permissions
        }));

        this.isPermissionsModalVisible = true;
      },
      // A failed fetch must not open the dialog: the tree would show nothing
      // ticked, and saving that would strip the role of everything it has.
      error: () => {
        this.permissionsLoadFailed = true;
        this.message.error(this.t('UMS.ROLES.PERMISSIONS_LOAD_FAILED'));
      }
    });
  }

  handleRoleModalCancel(): void {
    this.isRoleModalVisible = false;
  }

  handleRoleModalOk(): void {
    if (this.roleForm.invalid) {
      Object.values(this.roleForm.controls).forEach(c => { c.markAsDirty(); c.updateValueAndValidity(); });
      return;
    }
    this.saving = true;
    const payload = this.roleForm.getRawValue();

    if (this.selectedRole) {
      this.roleService.updateRole(this.selectedRole.id, payload).subscribe({
        next: () => {
          this.message.success(this.t('UMS.ROLES.ROLE_UPDATED'));
          this.isRoleModalVisible = false;
          this.loadRolesList();
          this.saving = false;
        },
        error: (err) => {
          this.message.error(this.refusal(err, 'UMS.ROLES.FAILED_UPDATE'));
          this.saving = false;
        }
      });
    } else {
      this.roleService.createRole(payload).subscribe({
        next: () => {
          this.message.success(this.t('UMS.ROLES.ROLE_CREATED'));
          this.isRoleModalVisible = false;
          this.loadRolesList();
          this.saving = false;
        },
        error: (err) => {
          this.message.error(extractApiErrorMessage(err) || this.t('UMS.ROLES.FAILED_CREATE'));
          this.saving = false;
        }
      });
    }
  }

  handlePermissionsModalCancel(): void {
    this.isPermissionsModalVisible = false;
  }

  saveRolePermissions(): void {
    if (!this.selectedRole) return;

    const selectedPermissionIds = this.permissionsByPage
      .flatMap(pg => pg.permissions)
      .filter(p => p.checked)
      .map(p => p.id);

    // The endpoint replaces the role's set, so an empty tree is a request to
    // remove every permission the role has. That is a legitimate thing to want
    // and a terrible thing to do by accident — it is what an unnoticed failure
    // of loadAllPermissions used to cause — so it is confirmed rather than
    // refused.
    if (!selectedPermissionIds.length) {
      this.modal.confirm({
        nzTitle: this.t('UMS.ROLES.CLEAR_PERMISSIONS_TITLE'),
        nzContent: this.t('UMS.ROLES.CLEAR_PERMISSIONS_MSG'),
        nzOkDanger: true,
        nzOkText: this.t('COMMON.CONFIRM'),
        nzCancelText: this.t('COMMON.CANCEL'),
        nzOnOk: () => this.commitRolePermissions(selectedPermissionIds)
      });
      return;
    }

    this.commitRolePermissions(selectedPermissionIds);
  }

  private commitRolePermissions(selectedPermissionIds: number[]): void {
    if (!this.selectedRole) return;

    this.saving = true;

    // An empty set only gets here through the confirm above.
    this.roleService.setRolePermissions(this.selectedRole.id, selectedPermissionIds,
      selectedPermissionIds.length === 0).subscribe({
      next: () => {
        this.message.success(this.t('UMS.ROLES.PERMISSIONS_SAVED'));
        this.isPermissionsModalVisible = false;
        this.saving = false;
      },
      error: () => {
        this.message.error(this.t('UMS.ROLES.PERMISSIONS_SAVE_FAILED'));
        this.saving = false;
      }
    });
  }

  confirmDelete(role: UmsRole): void {
    if (role.isSystem) {
      this.message.warning(this.t('UMS.SHARED.CANNOT_DELETE_SYSTEM_ROLE'));
      return;
    }

    this.modal.confirm({
      nzTitle: this.t('UMS.ROLES.DELETE_CONFIRM'),
      nzContent: `${this.t('UMS.ROLES.ROLE_NAME')}: ${role.roleName}`,
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => {
        this.roleService.deleteRole(role.id).subscribe({
          next: () => {
            this.message.success(this.t('UMS.ROLES.ROLE_DELETED'));
            this.loadRolesList();
          },
          error: (err) => this.message.error(this.refusal(err, 'UMS.ROLES.FAILED_DELETE'))
        });
      }
    });
  }
}
