import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { ToastService } from '../../../service/toast.service';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzGridModule } from 'ng-zorro-antd/grid';

import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { buildUmsListParams } from '../../../service/ums-list-params';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermission } from '../../../api/ums';
import { changesOnly } from '../../../service/reload-streams.util';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { silentErrors } from '../../../interceptors/http-context.tokens';
import { CodeLabelPipe } from '../../../pipes/code-label.pipe';

@Component({
    selector: 'app-permission-management',
    imports: [
    FormsModule,
    // The action type by name, not VIEW / EDIT (QA B11).
    CodeLabelPipe,
    ReactiveFormsModule,
    TranslatePipe,
    NzTableModule,
    NzButtonModule,
    NzInputModule,
    NzIconModule,
    NzModalModule,
    NzDescriptionsModule,
    NzFormModule,
    NzTagModule,
    NzCardModule,
    NzSelectModule,
    NzTooltipModule,
    NzGridModule,
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
            <i class="pi pi-lock text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.PERMISSIONS.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.PERMISSIONS.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'; adminTools: true" (click)="showCreateModal()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.PERMISSIONS.ADD_PERMISSION' | translate }}
            </button>
            <button nz-button (click)="loadPermissions()" [nzLoading]="loading">
              <span nz-icon nzType="reload"></span>
              {{ 'COMMON.REFRESH' | translate }}
            </button>
          </div>
        </div>
  
        <!-- Filters. Shared layout: assets/layout/styles/_filter-bar.scss.
             Narrowing is the server's job: the table holds one page at a time. -->
        <div class="filter-bar">
          <div class="filter-bar__grid filter-bar__grid--cols-4">
            <div class="filter-bar__field filter-bar__field--wide" [class.filter-bar__field--active]="searchKeyword">
              <label for="upSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="upSearch" type="text" nz-input [placeholder]="'COMMON.SEARCH' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterResourceType">
              <label for="upResource">{{ 'UMS.SHARED.FILTER_RESOURCE' | translate }}</label>
              <nz-select nzId="upResource" [(ngModel)]="filterResourceType" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_RESOURCE_ALL' | translate" nzValue=""></nz-option>
                @for (opt of resourceTypeFilterOptions; track opt.value) {
                  <nz-option [nzLabel]="opt.label" [nzValue]="opt.value"></nz-option>
                }
              </nz-select>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterActionType">
              <label for="upAction">{{ 'UMS.SHARED.FILTER_ACTION' | translate }}</label>
              <nz-select nzId="upAction" [(ngModel)]="filterActionType" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_ACTION_ALL' | translate" nzValue=""></nz-option>
                @for (opt of actionTypeFilterOptions; track opt.value) {
                  <nz-option [nzLabel]="opt.label" [nzValue]="opt.value"></nz-option>
                }
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
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalPermissions }}</span>
          </div>
        </div>
  
      <nz-table
        #permissionsTable
        [nzData]="permissions"
        [nzLoading]="loading"
        [nzTotal]="totalPermissions"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="permissionCode" [nzSortOrder]="sortOrderFor('permissionCode')" (nzSortOrderChange)="onSort('permissionCode', $event)">{{ 'UMS.PERMISSIONS.PERMISSION_CODE' | translate }}</th>
            <th nzShowSort nzColumnKey="permissionName" [nzSortOrder]="sortOrderFor('permissionName')" (nzSortOrderChange)="onSort('permissionName', $event)">{{ 'UMS.PERMISSIONS.PERMISSION_NAME' | translate }}</th>
            <th nzShowSort nzColumnKey="permissionNameAr" [nzSortOrder]="sortOrderFor('permissionNameAr')" (nzSortOrderChange)="onSort('permissionNameAr', $event)">{{ 'UMS.PERMISSIONS.PERMISSION_NAME_AR' | translate }}</th>
            <th nzShowSort nzColumnKey="resourceType" [nzSortOrder]="sortOrderFor('resourceType')" (nzSortOrderChange)="onSort('resourceType', $event)">{{ 'UMS.PERMISSIONS.RESOURCE_TYPE' | translate }}</th>
            <th nzShowSort nzColumnKey="actionType" [nzSortOrder]="sortOrderFor('actionType')" (nzSortOrderChange)="onSort('actionType', $event)">{{ 'UMS.PERMISSIONS.ACTION_TYPE' | translate }}</th>
            <th nzWidth="150px">{{ 'UMS.PERMISSIONS.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (perm of permissionsTable.data; track perm) {
            <tr>
              <td><code>{{ perm.permissionCode }}</code></td>
              <td>{{ perm.permissionName }}</td>
              <td>{{ perm.permissionNameAr }}</td>
              <td><nz-tag nzColor="blue">{{ perm.resourceType }}</nz-tag></td>
              <td><nz-tag nzColor="green">{{ perm.actionType | codeLabel:'permission-action-type':translationService.getCurrentLanguage() }}</nz-tag></td>
              <td>
                <button nz-button nzType="link" nzSize="small" (click)="showDetailModal(perm)" [nz-tooltip]="'COMMON.VIEW' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true" (click)="showEditModal(perm)" [nz-tooltip]="'COMMON.EDIT' | translate">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'; adminTools: true" (click)="confirmDelete(perm)" [nz-tooltip]="'COMMON.DELETE' | translate">
                  <span nz-icon nzType="delete"></span>
                </button>
              </td>
            </tr>
          }
        </tbody>
      </nz-table>
      </div>
    </div>
    
      <nz-modal
        [(nzVisible)]="isModalVisible"
        [nzTitle]="selectedPermission ? ('UMS.PERMISSIONS.EDIT_PERMISSION' | translate) : ('UMS.PERMISSIONS.ADD_PERMISSION' | translate)"
        (nzOnCancel)="handleModalCancel()"
        (nzOnOk)="handleModalOk()"
        [nzWidth]="720"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            <form nz-form [formGroup]="permissionForm" nzLayout="vertical">
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.PERMISSIONS.PERMISSION_CODE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="permissionCode" placeholder="e.g., STUDENTS.VIEW" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="6">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.PERMISSIONS.RESOURCE_TYPE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <nz-select formControlName="resourceType">
                            @for (opt of resourceTypeOptions; track opt) {
                              <nz-option [nzValue]="opt.value" [nzLabel]="opt.label"></nz-option>
                            }
                          </nz-select>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="6">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.PERMISSIONS.ACTION_TYPE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <nz-select formControlName="actionType">
                            @for (opt of actionTypeOptions; track opt) {
                              <nz-option [nzValue]="opt.value" [nzLabel]="opt.label"></nz-option>
                            }
                          </nz-select>
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
                        <nz-form-label nzRequired>{{ 'UMS.PERMISSIONS.PERMISSION_NAME' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="permissionName" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.PERMISSIONS.PERMISSION_NAME_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="permissionNameAr" dir="rtl" />
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
                  <nz-form-item>
                    <nz-form-label>{{ 'UMS.PERMISSIONS.DESCRIPTION' | translate }}</nz-form-label>
                    <nz-form-control>
                      <textarea nz-input formControlName="description" rows="2"></textarea>
                    </nz-form-control>
                  </nz-form-item>
                </div>
              </div>
            </form>
          </div>
        </ng-container>
      </nz-modal>
      <!-- The detail view. The GET /{{id}} endpoint behind this page existed
           all along and nothing called it; the row was only ever readable in the
           table. Same shape as the one on Staff users. -->
      <nz-modal
        [(nzVisible)]="isDetailModalVisible"
        [nzTitle]="'UMS.PERMISSIONS.TITLE' | translate"
        [nzWidth]="720"
        [nzFooter]="detailModalFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #detailModalFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
          @if (detailPermission) {
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true"
              (click)="isDetailModalVisible = false; showEditModal(detailPermission!)">
              <span nz-icon nzType="edit"></span> {{ 'COMMON.EDIT' | translate }}
            </button>
          }
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailPermission) {
            <div class="dv-view">
              <div class="dv-section">
                <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                <nz-descriptions-item [nzTitle]="'UMS.PERMISSIONS.PERMISSION_CODE' | translate"><code>{{ detailPermission.permissionCode }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PERMISSIONS.PERMISSION_NAME' | translate">{{ detailPermission.permissionName }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PERMISSIONS.PERMISSION_NAME_AR' | translate">{{ detailPermission.permissionNameAr || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PERMISSIONS.RESOURCE_TYPE' | translate"><nz-tag nzColor="blue">{{ detailPermission.resourceType }}</nz-tag></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PERMISSIONS.ACTION_TYPE' | translate"><nz-tag nzColor="green">{{ detailPermission.actionType | codeLabel:'permission-action-type':translationService.getCurrentLanguage() }}</nz-tag></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PERMISSIONS.DESCRIPTION' | translate">{{ detailPermission.description || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                </nz-descriptions>
              </div>
            </div>
          }
        </ng-container>
      </nz-modal>
    `,
    styles: []
})
export class PermissionManagementComponent implements OnInit {
  permissions: UmsPermission[] = [];
  resourceTypeOptions: Array<{ label: string; value: string }> = [];
  actionTypeOptions: Array<{ label: string; value: string }> = [];
  resourceTypeFilterOptions: Array<{ label: string; value: string }> = [];
  actionTypeFilterOptions: Array<{ label: string; value: string }> = [];

  /** The row the detail dialog is showing. */
  detailPermission: UmsPermission | null = null;
  isDetailModalVisible = false;

  loading = false;
  saving = false;
  searchKeyword = '';
  filterResourceType = '';
  filterActionType = '';
  sortBy = 'permissionCode';
  sortDir: 'asc' | 'desc' = 'asc';
  totalPermissions = 0;
  pageIndex = 1;
  pageSize = 10;

  isModalVisible = false;
  selectedPermission: UmsPermission | null = null;
  permissionForm!: FormGroup;

  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/permissions`;

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private http: HttpClient,
    private modal: NzModalService,
    private message: ToastService,
    private fb: FormBuilder,
    private enumOptionsService: EnumOptionsService,
    public translationService: TranslationService
  ) {
    this.permissionForm = this.fb.group({
      permissionCode: ['', Validators.required],
      permissionName: ['', Validators.required],
      permissionNameAr: [''],
      resourceType: ['PAGE', Validators.required],
      actionType: ['VIEW', Validators.required],
      description: ['']
    });
  }

  ngOnInit(): void {
    this.loadEnumOptions();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadEnumOptions());
    this.loadPermissions();
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  private loadEnumOptions(): void {
    this.enumOptionsService.getDropdownOptions('permission-resource-type').subscribe(opts => {
      this.resourceTypeOptions = opts;
      this.resourceTypeFilterOptions = opts;
    });
    this.enumOptionsService.getDropdownOptions('permission-action-type').subscribe(opts => {
      this.actionTypeOptions = opts;
      this.actionTypeFilterOptions = opts;
    });
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
      this.sortBy = 'permissionCode';
      this.sortDir = 'asc';
    }
    this.pageIndex = 1;
    this.loadPermissions();
  }

  /** Every filter back to its resting value, and the list from the first page. */
  clearSearchFilters(): void {
    this.searchKeyword = '';
    this.filterResourceType = '';
    this.filterActionType = '';
    this.applySearchFilters();
  }

  get hasSearchFilters(): boolean {
    return !!this.searchKeyword?.trim() || !!this.filterResourceType || !!this.filterActionType;
  }

  applySearchFilters(): void {
    this.pageIndex = 1;
    this.loadPermissions();
  }

  loadPermissions(): void {
    this.loading = true;
    const params = buildUmsListParams({
      page: this.pageIndex - 1,
      size: this.pageSize,
      sortBy: this.sortBy,
      sortDir: this.sortDir,
      keyword: this.searchKeyword?.trim(),
      resourceType: this.filterResourceType?.trim() || undefined,
      actionType: this.filterActionType?.trim() || undefined
    });
    this.http.get<any>(`${this.apiUrl}/`, { params }).subscribe({
      next: (response) => {
        this.permissions = response.content;
        this.totalPermissions = response.totalElements;
        this.loading = false;
      },
      error: () => {
        this.message.error(this.t('UMS.PERMISSIONS.MESSAGES.LOAD_ERROR'));
        this.loading = false;
      }
    });
  }

  onPageChange(pageIndex: number): void { this.pageIndex = pageIndex; this.loadPermissions(); }
  onPageSizeChange(ps: number): void { this.pageSize = ps; this.pageIndex = 1; this.loadPermissions(); }

  private resetForm(perm?: Partial<UmsPermission>): void {
    this.permissionForm.reset({
      permissionCode: perm?.permissionCode ?? '',
      permissionName: perm?.permissionName ?? '',
      permissionNameAr: perm?.permissionNameAr ?? '',
      resourceType: perm?.resourceType ?? 'PAGE',
      actionType: perm?.actionType ?? 'VIEW',
      description: perm?.description ?? ''
    });
    if (this.selectedPermission) {
      this.permissionForm.get('permissionCode')?.disable();
    } else {
      this.permissionForm.get('permissionCode')?.enable();
    }
  }

  showCreateModal(): void {
    this.selectedPermission = null;
    this.resetForm();
    this.isModalVisible = true;
  }

  showDetailModal(perm: UmsPermission): void {
    this.detailPermission = perm;
    this.isDetailModalVisible = true;
  }

  showEditModal(perm: UmsPermission): void {
    this.selectedPermission = perm;
    this.resetForm(perm);
    this.isModalVisible = true;
  }

  handleModalCancel(): void { this.isModalVisible = false; }

  handleModalOk(): void {
    if (this.permissionForm.invalid) {
      Object.values(this.permissionForm.controls).forEach(c => { c.markAsDirty(); c.updateValueAndValidity(); });
      return;
    }
    this.saving = true;
    const payload = this.permissionForm.getRawValue();
    const req = this.selectedPermission
      ? this.http.put<UmsPermission>(`${this.apiUrl}/${this.selectedPermission.id}`, payload, { context: silentErrors() })
      : this.http.post<UmsPermission>(this.apiUrl, payload, { context: silentErrors() });

    req.subscribe({
      next: () => {
        this.message.success(this.t(this.selectedPermission ? 'UMS.PERMISSIONS.MESSAGES.UPDATED' : 'UMS.PERMISSIONS.MESSAGES.CREATED'));
        this.isModalVisible = false;
        this.loadPermissions();
        this.saving = false;
      },
      error: (err) => {
        this.message.error(extractApiErrorMessage(err) || this.t(this.selectedPermission ? 'UMS.PERMISSIONS.MESSAGES.UPDATE_ERROR' : 'UMS.PERMISSIONS.MESSAGES.CREATE_ERROR'));
        this.saving = false;
      }
    });
  }

  confirmDelete(perm: UmsPermission): void {
    this.modal.confirm({
      nzTitle: this.t('UMS.PERMISSIONS.MESSAGES.DELETE_CONFIRM'),
      nzContent: `${this.t('UMS.PERMISSIONS.PERMISSION_CODE')}: ${perm.permissionCode}`,
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => this.http.delete(`${this.apiUrl}/${perm.id}`, { context: silentErrors() }).subscribe({
        next: () => { this.message.success(this.t('UMS.PERMISSIONS.MESSAGES.DELETED')); this.loadPermissions(); },
        error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.PERMISSIONS.MESSAGES.DELETE_ERROR'))
      })
    });
  }
}
