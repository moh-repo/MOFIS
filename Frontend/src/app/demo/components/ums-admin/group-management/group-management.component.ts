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
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzTransferModule, TransferItem } from 'ng-zorro-antd/transfer';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';

import { UmsGroupService } from '../../../service/ums-group.service';
import { UmsRoleService } from '../../../service/ums-role.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { UmsGroup, UmsRole } from '../../../api/ums';
import { TranslationService } from '../../../service/translation.service';
import { extractApiErrorMessage } from '../../../service/api-error.util';

@Component({
    selector: 'app-group-management',
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
        NzSwitchModule,
        NzCardModule,
        NzBadgeModule,
        NzTransferModule,
        NzTooltipModule,
        NzSelectModule,
        NzGridModule,
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
            <i class="pi pi-sitemap text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.GROUPS.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.GROUPS.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'; adminTools: true" (click)="showCreateModal()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.GROUPS.ADD_GROUP' | translate }}
            </button>
            <button nz-button (click)="loadGroups()" [nzLoading]="loading">
              <span nz-icon nzType="reload"></span>
              {{ 'COMMON.REFRESH' | translate }}
            </button>
          </div>
        </div>
  
        <!-- Filters. Shared layout: assets/layout/styles/_filter-bar.scss.
             Narrowing is the server's job: the table holds one page at a time. -->
        <div class="filter-bar">
          <div class="filter-bar__grid filter-bar__grid--cols-3">
            <div class="filter-bar__field filter-bar__field--wide" [class.filter-bar__field--active]="searchKeyword">
              <label for="ugSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="ugSearch" type="text" nz-input [placeholder]="'UMS.GROUPS.SEARCH_PLACEHOLDER' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterKind">
              <label for="ugKind">{{ 'UMS.SHARED.FILTER_TYPE' | translate }}</label>
              <nz-select nzId="ugKind" [(ngModel)]="filterKind" (ngModelChange)="applySearchFilters()">
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
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalGroups }}</span>
          </div>
        </div>
  
      <!-- Groups Table -->
      <nz-table
        #groupsTable
        [nzData]="groups"
        [nzLoading]="loading"
        [nzTotal]="totalGroups"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="groupName" [nzSortOrder]="sortOrderFor('groupName')" (nzSortOrderChange)="onSort('groupName', $event)">{{ 'UMS.GROUPS.GROUP_NAME' | translate }}</th>
            <th nzShowSort nzColumnKey="groupNameAr" [nzSortOrder]="sortOrderFor('groupNameAr')" (nzSortOrderChange)="onSort('groupNameAr', $event)">{{ 'UMS.GROUPS.GROUP_NAME_AR' | translate }}</th>
            <th nzShowSort nzColumnKey="description" [nzSortOrder]="sortOrderFor('description')" (nzSortOrderChange)="onSort('description', $event)">{{ 'UMS.GROUPS.DESCRIPTION' | translate }}</th>
            <th nzShowSort nzColumnKey="isActive" [nzSortOrder]="sortOrderFor('isActive')" (nzSortOrderChange)="onSort('isActive', $event)">{{ 'UMS.GROUPS.STATUS' | translate }}</th>
            <th nzShowSort nzColumnKey="isSystem" [nzSortOrder]="sortOrderFor('isSystem')" (nzSortOrderChange)="onSort('isSystem', $event)">{{ 'UMS.GROUPS.TYPE' | translate }}</th>
            <th nzWidth="200px">{{ 'UMS.GROUPS.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (group of groupsTable.data; track group) {
            <tr>
              <td>{{ group.groupName }}</td>
              <td>{{ group.groupNameAr }}</td>
              <td>{{ group.description }}</td>
              <td>
                <nz-badge [nzStatus]="group.isActive ? 'success' : 'default'" [nzText]="group.isActive ? ('UMS.GROUPS.ACTIVE' | translate) : ('UMS.GROUPS.INACTIVE' | translate)"></nz-badge>
              </td>
              <td>
                <nz-tag [nzColor]="group.isSystem ? 'gold' : 'blue'">{{ group.isSystem ? ('UMS.GROUPS.SYSTEM' | translate) : ('UMS.GROUPS.CUSTOM' | translate) }}</nz-tag>
              </td>
              <td>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.VIEW'" (click)="showDetailModal(group)" [nz-tooltip]="'UMS.USERS.VIEW_DETAILS' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true" (click)="showEditModal(group)"
                  [disabled]="group.isSystem"
                  [nz-tooltip]="(group.isSystem ? 'UMS.SHARED.CANNOT_EDIT_SYSTEM_GROUP' : 'COMMON.EDIT') | translate">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true" (click)="showRolesModal(group)" [nz-tooltip]="'UMS.GROUPS.VIEW_ROLES' | translate">
                  <span nz-icon nzType="safety"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'; adminTools: true"
                  [disabled]="group.isSystem" (click)="confirmDelete(group)" [nz-tooltip]="'COMMON.DELETE' | translate">
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
        [(nzVisible)]="isGroupModalVisible"
        [nzTitle]="selectedGroup ? ('UMS.GROUPS.EDIT_GROUP' | translate) : ('UMS.GROUPS.CREATE_GROUP' | translate)"
        (nzOnCancel)="handleGroupModalCancel()"
        (nzOnOk)="handleGroupModalOk()"
        [nzWidth]="720"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            <form nz-form [formGroup]="groupForm" nzLayout="vertical">
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.GROUPS.GROUP_NAME' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="groupName" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.GROUPS.GROUP_NAME_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="groupNameAr" dir="rtl" />
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
                        <nz-form-label>{{ 'UMS.GROUPS.DESCRIPTION' | translate }}</nz-form-label>
                        <nz-form-control>
                          <textarea nz-input formControlName="description" rows="3"></textarea>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.GROUPS.DESCRIPTION_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <textarea nz-input formControlName="descriptionAr" rows="3" dir="rtl"></textarea>
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
                        <nz-form-label>{{ 'UMS.GROUPS.ACTIVE' | translate }}</nz-form-label>
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
    
      <!-- Group Detail View Modal -->
      <nz-modal
        [(nzVisible)]="isDetailModalVisible"
        [nzTitle]="'UMS.USERS.VIEW_DETAILS' | translate"
        [nzWidth]="720"
        [nzFooter]="groupDetailFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #groupDetailFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
          @if (detailGroup) {
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true" [disabled]="detailGroup?.isSystem" (click)="isDetailModalVisible = false; showEditModal(detailGroup!)">
              <span nz-icon nzType="edit"></span> {{ 'COMMON.EDIT' | translate }}
            </button>
          }
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailGroup) {
            <div class="dv-view">
              <div class="dv-section">
              <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                <nz-descriptions-item [nzTitle]="'UMS.GROUPS.GROUP_NAME' | translate">{{ detailGroup.groupName }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.GROUPS.GROUP_NAME_AR' | translate">{{ detailGroup.groupNameAr || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.GROUPS.DESCRIPTION' | translate" [nzSpan]="2">{{ detailGroup.description || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.GROUPS.STATUS' | translate">
                  <nz-badge [nzStatus]="detailGroup.isActive ? 'success' : 'default'" [nzText]="detailGroup.isActive ? ('UMS.GROUPS.ACTIVE' | translate) : ('UMS.GROUPS.INACTIVE' | translate)"></nz-badge>
                </nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.GROUPS.TYPE' | translate">
                  <nz-tag [nzColor]="detailGroup.isSystem ? 'gold' : 'blue'">{{ detailGroup.isSystem ? ('UMS.GROUPS.SYSTEM' | translate) : ('UMS.GROUPS.CUSTOM' | translate) }}</nz-tag>
                </nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.USERS.CREATED' | translate" [nzSpan]="2">{{ detailGroup.createdAt ? (detailGroup.createdAt | date:'medium') : ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
              </nz-descriptions>
              </div>
              @if (detailRoles.length) {
                <div class="dv-section">
                  <div class="dv-section__head"><span class="dv-section__icon"><span nz-icon nzType="safety"></span></span><div class="dv-section__title">{{ 'UMS.GROUPS.ASSIGNED_ROLES' | translate }}</div></div>
                  <div>
                    <div class="flex flex-wrap gap-1">
                      @for (r of detailRoles; track r) {
                        <nz-tag nzColor="blue">{{ r.roleName }}</nz-tag>
                      }
                    </div>
                  </div>
                </div>
              } @else if (detailRolesFailed) {
                <div class="dv-section">
                  <div class="dv-section__head"><span class="dv-section__icon"><span nz-icon nzType="safety"></span></span><div class="dv-section__title">{{ 'UMS.GROUPS.ASSIGNED_ROLES' | translate }}</div></div>
                  <div class="dv-notice dv-notice--warn roles-load-failed"><i class="pi pi-exclamation-triangle"></i><span>{{ 'UMS.GROUPS.ROLES_LOAD_FAILED' | translate }}</span></div>
                </div>
              }
            </div>
          }
        </ng-container>
      </nz-modal>
    
      <!-- Roles Assignment Modal -->
      <nz-modal
        [(nzVisible)]="isRolesModalVisible"
        [nzTitle]="'UMS.GROUPS.ASSIGN_ROLES' | translate"
        (nzOnCancel)="handleRolesModalCancel()"
        (nzOnOk)="handleRolesModalOk()"
        [nzWidth]="720">
        <ng-container *nzModalContent>
          <div class="dv-view">
            @if (selectedGroup) {
              <div class="dv-hero">
                <div class="dv-hero__badge"><span nz-icon nzType="team"></span></div>
                <div class="dv-hero__body">
                  <div class="dv-hero__eyebrow">{{ 'UMS.GROUPS.GROUP_NAME' | translate }}</div>
                  <div class="dv-hero__title">{{ selectedGroup.groupName }}</div>
                </div>
              </div>
            }
            <div class="dv-section">
              <nz-transfer
                [nzDataSource]="roleTransferItems"
                [nzTitles]="[('UMS.GROUPS.AVAILABLE_ROLES' | translate), ('UMS.GROUPS.ASSIGNED_ROLES' | translate)]"
                nzShowSearch
                (nzChange)="transferRoles($event)">
              </nz-transfer>
            </div>
          </div>
        </ng-container>
      </nz-modal>
    `,
    styles: []
})
export class GroupManagementComponent implements OnInit {
  groups: UmsGroup[] = [];
  roles: UmsRole[] = [];

  loading = false;
  saving = false;
  searchKeyword = '';
  /** '' | SYSTEM | CUSTOM */
  filterKind = '';
  sortBy = 'groupName';
  sortDir: 'asc' | 'desc' = 'asc';
  totalGroups = 0;
  pageIndex = 1;
  pageSize = 10;

  isGroupModalVisible = false;
  isRolesModalVisible = false;
  isDetailModalVisible = false;
  detailGroup: UmsGroup | null = null;
  detailRoles: UmsRole[] = [];
  /** The group's roles could not be read, so the View dialog must not say it has none. */
  detailRolesFailed = false;
  /**
   * The list of roles to choose from could not be read. The assignment dialog is
   * built from it, so opening it would show every role as unassigned (QA B10, N12).
   */
  rolesLoadFailed = false;
  selectedGroup: UmsGroup | null = null;
  groupForm!: FormGroup;

  roleTransferItems: TransferItem[] = [];

  constructor(
    private groupService: UmsGroupService,
    private roleService: UmsRoleService,
    public permissionService: UmsPermissionService,
    private modal: NzModalService,
    private message: ToastService,
    private fb: FormBuilder,
    public translationService: TranslationService
  ) {
    this.groupForm = this.fb.group({
      groupName: ['', Validators.required],
      groupNameAr: [''],
      description: [''],
      descriptionAr: [''],
      isActive: [true]
    });
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  ngOnInit(): void {
    this.loadGroups();
    this.loadRoles();
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
      this.sortBy = 'groupName';
      this.sortDir = 'asc';
    }
    this.pageIndex = 1;
    this.loadGroups();
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
    this.loadGroups();
  }

  loadGroups(): void {
    this.loading = true;
    this.groupService.getAllGroups(this.pageIndex - 1, this.pageSize, {
      keyword: this.searchKeyword?.trim(),
      system: this.resolveSystemFlag(),
      sortBy: this.sortBy,
      sortDir: this.sortDir
    }).subscribe({
      next: (response) => {
        this.groups = response.content;
        this.totalGroups = response.totalElements;
        this.loading = false;
      },
      error: () => {
        this.message.error(this.t('UMS.GROUPS.FAILED_LOAD'));
        this.loading = false;
      }
    });
  }

  loadRoles(): void {
    this.roleService.getAllActiveRoles().subscribe({
      next: (roles) => { this.roles = roles; this.rolesLoadFailed = false; },
      error: () => { this.roles = []; this.rolesLoadFailed = true; }
    });
  }

  searchGroups(): void {
    this.applySearchFilters();
  }

  onPageChange(pageIndex: number): void {
    this.pageIndex = pageIndex;
    this.loadGroups();
  }

  onPageSizeChange(ps: number): void {
    this.pageSize = ps;
    this.pageIndex = 1;
    this.loadGroups();
  }

  showCreateModal(): void {
    this.selectedGroup = null;
    this.groupForm.reset({ groupName: '', groupNameAr: '', description: '', descriptionAr: '', isActive: true });
    this.isGroupModalVisible = true;
  }

  /**
   * Review M9: a system group is refused with 409, which used to surface as the
   * generic failure. The buttons are disabled for those rows; this covers a
   * stale list or a second tab.
   */
  private refusal(err: { status?: number; error?: { error?: string; message?: string } }, fallbackKey: string): string {
    if (err?.status === 409) {
      return this.t('UMS.SHARED.CANNOT_EDIT_SYSTEM_GROUP');
    }
    return extractApiErrorMessage(err) || this.t(fallbackKey);
  }

  showEditModal(group: UmsGroup): void {
    this.selectedGroup = group;
    this.groupForm.reset({
      groupName: group.groupName,
      groupNameAr: group.groupNameAr ?? '',
      description: group.description ?? '',
      descriptionAr: group.descriptionAr ?? '',
      isActive: group.isActive ?? true
    });
    this.isGroupModalVisible = true;
  }

  showDetailModal(group: UmsGroup): void {
    this.detailGroup = group;
    this.detailRoles = [];
    this.detailRolesFailed = false;
    this.groupService.getGroupRoles(group.id).subscribe({
      next: (roles) => {
        this.detailRoles = roles;
        this.isDetailModalVisible = true;
      },
      error: () => {
        this.detailRolesFailed = true;
        this.isDetailModalVisible = true;
      }
    });
  }

  showRolesModal(group: UmsGroup): void {
    // Retry, so a passing failure recovers on the next click.
    if (this.rolesLoadFailed) {
      this.message.error(this.t('UMS.GROUPS.ROLES_LOAD_FAILED'));
      this.loadRoles();
      return;
    }
    this.selectedGroup = group;
    this.groupService.getGroupRoles(group.id).subscribe({
      next: (groupRoles) => {
        const groupRoleIds = groupRoles.map(r => r.id);
        this.roleTransferItems = this.roles.map(r => ({
          key: r.id.toString(),
          title: r.roleName,
          description: r.description || '',
          direction: groupRoleIds.includes(r.id) ? 'right' : 'left'
        } as TransferItem));
        this.isRolesModalVisible = true;
      },
      // Without this the button did nothing at all on a failed fetch, with no
      // sign of why.
      error: () => this.message.error(this.t('UMS.GROUPS.ROLES_LOAD_FAILED'))
    });
  }

  handleGroupModalCancel(): void {
    this.isGroupModalVisible = false;
  }

  handleGroupModalOk(): void {
    if (this.groupForm.invalid) {
      Object.values(this.groupForm.controls).forEach(c => { c.markAsDirty(); c.updateValueAndValidity(); });
      return;
    }
    this.saving = true;
    const payload = this.groupForm.getRawValue();

    if (this.selectedGroup) {
      this.groupService.updateGroup(this.selectedGroup.id, payload).subscribe({
        next: () => {
          this.message.success(this.t('UMS.GROUPS.GROUP_UPDATED'));
          this.isGroupModalVisible = false;
          this.loadGroups();
          this.saving = false;
        },
        error: (err) => {
          this.message.error(this.refusal(err, 'UMS.GROUPS.FAILED_UPDATE'));
          this.saving = false;
        }
      });
    } else {
      this.groupService.createGroup(payload).subscribe({
        next: () => {
          this.message.success(this.t('UMS.GROUPS.GROUP_CREATED'));
          this.isGroupModalVisible = false;
          this.loadGroups();
          this.saving = false;
        },
        error: (err) => {
          this.message.error(extractApiErrorMessage(err) || this.t('UMS.GROUPS.FAILED_CREATE'));
          this.saving = false;
        }
      });
    }
  }

  // The transfer writes as it happens, so both buttons mean "close" - Cancel
  // cannot undo what has already been saved. The reload is what keeps a later
  // View from showing the roles this group had before the change.
  private closeRolesModal(): void {
    this.isRolesModalVisible = false;
    this.loadGroups();
  }

  handleRolesModalCancel(): void {
    this.closeRolesModal();
  }

  handleRolesModalOk(): void {
    this.closeRolesModal();
  }

  transferRoles(event: any): void {
    if (!this.selectedGroup) return;

    const { to, list } = event;

    list.forEach((item: TransferItem) => {
      const roleId = parseInt(item['key']);
      if (to === 'right') {
        this.groupService.assignRoleToGroup(this.selectedGroup!.id, roleId).subscribe({
          next: () => this.message.success(this.t('UMS.GROUPS.ROLE_ASSIGNED')),
          error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.GROUPS.ROLE_ASSIGN_FAILED'))
        });
      } else {
        this.groupService.removeRoleFromGroup(this.selectedGroup!.id, roleId).subscribe({
          next: () => this.message.success(this.t('UMS.GROUPS.ROLE_REMOVED')),
          error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.GROUPS.ROLE_REMOVE_FAILED'))
        });
      }
    });
  }

  confirmDelete(group: UmsGroup): void {
    if (group.isSystem) {
      this.message.warning(this.t('UMS.SHARED.CANNOT_DELETE_SYSTEM_GROUP'));
      return;
    }

    this.modal.confirm({
      nzTitle: this.t('UMS.GROUPS.DELETE_CONFIRM'),
      nzContent: `${this.t('UMS.GROUPS.GROUP_NAME')}: ${group.groupName}`,
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => {
        this.groupService.deleteGroup(group.id).subscribe({
          next: () => {
            this.message.success(this.t('UMS.GROUPS.GROUP_DELETED'));
            this.loadGroups();
          },
          error: (err) => this.message.error(this.refusal(err, 'UMS.GROUPS.FAILED_DELETE'))
        });
      }
    });
  }
}
