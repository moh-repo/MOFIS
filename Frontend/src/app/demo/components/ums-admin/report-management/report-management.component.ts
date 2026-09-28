import { Component, OnInit } from '@angular/core';

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
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';

import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { buildUmsListParams } from '../../../service/ums-list-params';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { TranslationService } from '../../../service/translation.service';
import { UmsReport } from '../../../api/ums';
import { ReportCatalogService } from '../../../service/report-catalog.service';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { silentErrors } from '../../../interceptors/http-context.tokens';

@Component({
    selector: 'app-report-management',
    imports: [
    FormsModule,
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
    NzTooltipModule,
    NzSwitchModule,
    NzGridModule,
    NzSelectModule,
    NzInputNumberModule,
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
            <i class="pi pi-chart-bar text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.REPORTS.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.REPORTS.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'" (click)="showCreateModal()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.REPORTS.ADD_REPORT' | translate }}
            </button>
            <button nz-button (click)="loadReports()" [nzLoading]="loading">
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
              <label for="urpSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="urpSearch" type="text" nz-input [placeholder]="'COMMON.SEARCH' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterCategory">
              <label for="urpCategory">{{ 'UMS.SHARED.FILTER_CATEGORY' | translate }}</label>
              <nz-input-group [nzPrefix]="prefixCategory">
                <input id="urpCategory" type="text" nz-input [(ngModel)]="filterCategory" [placeholder]="'UMS.SHARED.FILTER_CATEGORY' | translate" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #prefixCategory>
                <span nz-icon nzType="filter"></span>
              </ng-template>
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
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalReports }}</span>
          </div>
        </div>
  
      <nz-table
        #reportsTable
        [nzData]="reports"
        [nzLoading]="loading"
        [nzTotal]="totalReports"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="reportCode" [nzSortOrder]="sortOrderFor('reportCode')" (nzSortOrderChange)="onSort('reportCode', $event)">{{ 'UMS.REPORTS.REPORT_CODE' | translate }}</th>
            <th nzShowSort nzColumnKey="reportName" [nzSortOrder]="sortOrderFor('reportName')" (nzSortOrderChange)="onSort('reportName', $event)">{{ 'UMS.REPORTS.REPORT_NAME' | translate }}</th>
            <th nzShowSort nzColumnKey="reportNameAr" [nzSortOrder]="sortOrderFor('reportNameAr')" (nzSortOrderChange)="onSort('reportNameAr', $event)">{{ 'UMS.REPORTS.REPORT_NAME_AR' | translate }}</th>
            <th nzShowSort nzColumnKey="category" [nzSortOrder]="sortOrderFor('category')" (nzSortOrderChange)="onSort('category', $event)">{{ 'UMS.REPORTS.CATEGORY' | translate }}</th>
            <th nzShowSort nzColumnKey="route" [nzSortOrder]="sortOrderFor('route')" (nzSortOrderChange)="onSort('route', $event)">{{ 'UMS.REPORTS.ROUTE' | translate }}</th>
            <th nzShowSort nzColumnKey="sortOrder" [nzSortOrder]="sortOrderFor('sortOrder')" (nzSortOrderChange)="onSort('sortOrder', $event)">{{ 'UMS.REPORTS.SORT_ORDER' | translate }}</th>
            <th>{{ 'UMS.REPORTS.IS_ACTIVE' | translate }}</th>
            <th nzWidth="150px">{{ 'UMS.REPORTS.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (report of reportsTable.data; track report) {
            <tr>
              <td><code>{{ report.reportCode }}</code></td>
              <!-- The report's own icon in front of its name, where it belongs;
                   it had a column of its own that printed the icon and then its
                   class name beside it. -->
              <td>
                @if (report.icon) { <i class="pi {{ report.icon }} mx-1 text-500"></i> }{{ report.reportName }}
              </td>
              <td>{{ report.reportNameAr }}</td>
              <!-- A report's parent is what groups it, so it reads under the
                   category rather than three columns away. -->
              <td>
                <nz-tag nzColor="blue">{{ report.category }}</nz-tag>
                @if (report.parentCode) {
                  <div class="cell-note"><code>{{ report.parentCode }}</code></div>
                }
              </td>
              <!-- Where the report lives, and who may open it. The old Page Code
                   column showed permissionCodes when it had them and the page code
                   otherwise, under one heading that named only the second. -->
              <td>
                {{ report.route }}@if (report.queryParams) {<span class="text-500">?{{ report.queryParams }}</span>}
                @if (report.permissionCodes) {
                  <div class="cell-note">
                    <span nz-icon nzType="key" class="mx-1"></span><code>{{ report.permissionCodes }}</code>
                  </div>
                } @else if (report.pageCode) {
                  <div class="cell-note">
                    <span nz-icon nzType="file" class="mx-1"></span><code>{{ report.pageCode }}</code>
                  </div>
                }
              </td>
              <td>{{ report.sortOrder ?? '—' }}</td>
              <td><nz-tag [nzColor]="report.isActive ? 'success' : 'default'">{{ (report.isActive ? 'COMMON.ACTIVE' : 'COMMON.INACTIVE') | translate }}</nz-tag></td>
              <td>
                <button nz-button nzType="link" nzSize="small" (click)="showDetailModal(report)" [nz-tooltip]="'COMMON.VIEW' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'" (click)="showEditModal(report)" [nz-tooltip]="'COMMON.EDIT' | translate">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'" (click)="confirmDelete(report)" [nz-tooltip]="'COMMON.DELETE' | translate">
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
        [nzTitle]="selectedReport ? ('UMS.REPORTS.EDIT_REPORT' | translate) : ('UMS.REPORTS.ADD_REPORT' | translate)"
        (nzOnCancel)="handleModalCancel()"
        (nzOnOk)="handleModalOk()"
        [nzWidth]="720"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            <form nz-form [formGroup]="reportForm" nzLayout="vertical">
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.REPORTS.REPORT_CODE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="reportCode" placeholder="e.g., STUDENT_STATS" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.CATEGORY' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="category" placeholder="e.g., Statistics" />
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
                        <nz-form-label nzRequired>{{ 'UMS.REPORTS.REPORT_NAME' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="reportName" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.REPORT_NAME_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="reportNameAr" dir="rtl" />
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
                        <nz-form-label>{{ 'UMS.REPORTS.DESCRIPTION' | translate }}</nz-form-label>
                        <nz-form-control>
                          <textarea nz-input formControlName="description" rows="2"></textarea>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.DESCRIPTION_AR' | translate }}</nz-form-label>
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
                    <div nz-col [nzSpan]="16">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.ROUTE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="route" placeholder="/reports/student-stats" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.IS_ACTIVE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-switch formControlName="isActive"></nz-switch>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                  </div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.PARENT_CODE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-select formControlName="parentCode" nzAllowClear nzShowSearch [nzPlaceHolder]="'UMS.REPORTS.PARENT_NONE' | translate">
                            @for (hub of hubOptions; track hub.reportCode) {
                              <nz-option [nzValue]="hub.reportCode" [nzLabel]="hub.reportCode + ' — ' + (translationService.isRTL() ? hub.reportNameAr : hub.reportName)"></nz-option>
                            }
                          </nz-select>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="6">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.SORT_ORDER' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-input-number formControlName="sortOrder" [nzStep]="1" style="width: 100%"></nz-input-number>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="6">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.COLOR' | translate }}</nz-form-label>
                        <nz-form-control [nzExtra]="'UMS.REPORTS.COLOR_HINT' | translate">
                          <input nz-input formControlName="color" placeholder="green-600" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                  </div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.ICON' | translate }}</nz-form-label>
                        <nz-form-control [nzExtra]="'UMS.REPORTS.ICON_HINT' | translate">
                          <nz-input-group [nzPrefix]="iconPreview">
                            <input nz-input formControlName="icon" placeholder="pi-users" />
                          </nz-input-group>
                          <ng-template #iconPreview><i class="pi {{ reportForm.value.icon }}"></i></ng-template>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.QUERY_PARAMS' | translate }}</nz-form-label>
                        <nz-form-control [nzExtra]="'UMS.REPORTS.QUERY_PARAMS_HINT' | translate">
                          <input nz-input formControlName="queryParams" placeholder="doc=id-card" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.PAGE_CODE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="pageCode" placeholder="REPORT_FINANCE" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                  </div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="24">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.REPORTS.PERMISSION_CODES' | translate }}</nz-form-label>
                        <nz-form-control [nzExtra]="'UMS.REPORTS.PERMISSION_CODES_HINT' | translate">
                          <input nz-input formControlName="permissionCodes" placeholder="REPORT_FINANCE, FINANCE" />
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
      <!-- The detail view. The GET /{{id}} endpoint behind this page existed
           all along and nothing called it; the row was only ever readable in the
           table. Same shape as the one on Staff users. -->
      <nz-modal
        [(nzVisible)]="isDetailModalVisible"
        [nzTitle]="'UMS.REPORTS.TITLE' | translate"
        [nzWidth]="720"
        [nzFooter]="detailModalFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #detailModalFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
          @if (detailReport) {
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'"
              (click)="isDetailModalVisible = false; showEditModal(detailReport!)">
              <span nz-icon nzType="edit"></span> {{ 'COMMON.EDIT' | translate }}
            </button>
          }
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailReport) {
            <div class="dv-view">
              <div class="dv-section">
                <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                <nz-descriptions-item [nzTitle]="'UMS.REPORTS.REPORT_CODE' | translate"><code>{{ detailReport.reportCode }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.REPORTS.REPORT_NAME' | translate">{{ detailReport.reportName }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.REPORTS.REPORT_NAME_AR' | translate">{{ detailReport.reportNameAr || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.REPORTS.CATEGORY' | translate">{{ detailReport.category || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.REPORTS.ROUTE' | translate"><code>{{ detailReport.route || ('COMMON.NOT_AVAILABLE' | translate) }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.REPORTS.SORT_ORDER' | translate">{{ detailReport.sortOrder }}</nz-descriptions-item>
                </nz-descriptions>
              </div>
            </div>
          }
        </ng-container>
      </nz-modal>
    `,
    styles: []
})
export class ReportManagementComponent implements OnInit {
  reports: UmsReport[] = [];
  /** The row the detail dialog is showing. */
  detailReport: UmsReport | null = null;
  isDetailModalVisible = false;

  loading = false;
  saving = false;
  searchKeyword = '';
  filterCategory = '';
  sortBy = 'reportCode';
  sortDir: 'asc' | 'desc' = 'asc';
  totalReports = 0;
  pageIndex = 1;
  pageSize = 10;

  isModalVisible = false;
  selectedReport: UmsReport | null = null;
  reportForm!: FormGroup;
  /** Top-level rows (hubs): what a card may be filed under. */
  hubOptions: UmsReport[] = [];

  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/reports`;

  constructor(
    private http: HttpClient,
    private modal: NzModalService,
    private message: ToastService,
    private fb: FormBuilder,
    public translationService: TranslationService,
    private catalog: ReportCatalogService
  ) {
    this.reportForm = this.fb.group({
      reportCode: ['', Validators.required],
      reportName: ['', Validators.required],
      reportNameAr: [''],
      description: [''],
      descriptionAr: [''],
      category: [''],
      route: [''],
      isActive: [true],
      parentCode: [null],
      icon: [''],
      color: [''],
      queryParams: [''],
      pageCode: [''],
      permissionCodes: [''],
      sortOrder: [null]
    });
  }

  ngOnInit(): void {
    this.loadReports();
    this.loadHubOptions();
  }

  private loadHubOptions(): void {
    this.catalog.getCatalog().subscribe(rows => {
      this.hubOptions = rows.filter(r => r.parentCode == null);
    });
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
      this.sortBy = 'reportCode';
      this.sortDir = 'asc';
    }
    this.pageIndex = 1;
    this.loadReports();
  }

  /** Every filter back to its resting value, and the list from the first page. */
  clearSearchFilters(): void {
    this.searchKeyword = '';
    this.filterCategory = '';
    this.applySearchFilters();
  }

  get hasSearchFilters(): boolean {
    return !!this.searchKeyword?.trim() || !!this.filterCategory;
  }

  applySearchFilters(): void {
    this.pageIndex = 1;
    this.loadReports();
  }

  loadReports(): void {
    this.loading = true;
    const params = buildUmsListParams({
      page: this.pageIndex - 1,
      size: this.pageSize,
      sortBy: this.sortBy,
      sortDir: this.sortDir,
      keyword: this.searchKeyword?.trim(),
      category: this.filterCategory?.trim()
    });
    this.http.get<any>(`${this.apiUrl}/`, { params }).subscribe({
      next: (response) => {
        this.reports = response.content;
        this.totalReports = response.totalElements;
        this.loading = false;
      },
      error: () => {
        this.message.error(this.t('UMS.REPORTS.MESSAGES.LOAD_ERROR'));
        this.loading = false;
      }
    });
  }

  onPageChange(pageIndex: number): void {
    this.pageIndex = pageIndex;
    this.loadReports();
  }

  onPageSizeChange(ps: number): void {
    this.pageSize = ps;
    this.pageIndex = 1;
    this.loadReports();
  }

  private resetForm(report?: Partial<UmsReport>): void {
    this.reportForm.reset({
      reportCode: report?.reportCode ?? '',
      reportName: report?.reportName ?? '',
      reportNameAr: report?.reportNameAr ?? '',
      description: report?.description ?? '',
      descriptionAr: report?.descriptionAr ?? '',
      category: report?.category ?? '',
      route: report?.route ?? '',
      isActive: report?.isActive ?? true,
      parentCode: report?.parentCode ?? null,
      icon: report?.icon ?? '',
      color: report?.color ?? '',
      queryParams: report?.queryParams ?? '',
      pageCode: report?.pageCode ?? '',
      permissionCodes: report?.permissionCodes ?? '',
      sortOrder: report?.sortOrder ?? null
    });
    if (this.selectedReport) {
      this.reportForm.get('reportCode')?.disable();
    } else {
      this.reportForm.get('reportCode')?.enable();
    }
  }

  showCreateModal(): void {
    this.selectedReport = null;
    this.resetForm();
    this.isModalVisible = true;
  }

  showDetailModal(report: UmsReport): void {
    this.detailReport = report;
    this.isDetailModalVisible = true;
  }

  showEditModal(report: UmsReport): void {
    this.selectedReport = report;
    this.resetForm(report);
    this.isModalVisible = true;
  }

  handleModalCancel(): void {
    this.isModalVisible = false;
  }

  handleModalOk(): void {
    if (this.reportForm.invalid) {
      Object.values(this.reportForm.controls).forEach(c => {
        c.markAsDirty();
        c.updateValueAndValidity();
      });
      return;
    }
    this.saving = true;
    const raw = this.reportForm.getRawValue();
    // Empty text is "no value" on the nullable catalogue columns.
    const blankToNull = (v: unknown) => (typeof v === 'string' && v.trim() === '' ? null : v);
    const payload = {
      ...raw,
      parentCode: blankToNull(raw.parentCode),
      icon: blankToNull(raw.icon),
      color: blankToNull(raw.color),
      queryParams: blankToNull(raw.queryParams),
      pageCode: blankToNull(raw.pageCode),
      permissionCodes: blankToNull(raw.permissionCodes)
    };
    const req = this.selectedReport
      ? this.http.put<UmsReport>(`${this.apiUrl}/${this.selectedReport.id}`, payload, { context: silentErrors() })
      : this.http.post<UmsReport>(this.apiUrl, payload, { context: silentErrors() });

    req.subscribe({
      next: () => {
        this.message.success(this.t(this.selectedReport ? 'UMS.REPORTS.MESSAGES.UPDATED' : 'UMS.REPORTS.MESSAGES.CREATED'));
        this.isModalVisible = false;
        this.loadReports();
        // The hubs read the catalogue from a session cache; a saved row must show on the next visit.
        this.catalog.reload();
        this.loadHubOptions();
        this.saving = false;
      },
      error: (err) => {
        this.message.error(extractApiErrorMessage(err) || this.t(this.selectedReport ? 'UMS.REPORTS.MESSAGES.UPDATE_ERROR' : 'UMS.REPORTS.MESSAGES.CREATE_ERROR'));
        this.saving = false;
      }
    });
  }

  confirmDelete(report: UmsReport): void {
    this.modal.confirm({
      nzTitle: this.t('UMS.REPORTS.MESSAGES.DELETE_CONFIRM'),
      nzContent: `${this.t('UMS.REPORTS.REPORT_CODE')}: ${report.reportCode}`,
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => {
        this.http.delete(`${this.apiUrl}/${report.id}`, { context: silentErrors() }).subscribe({
          next: () => {
            this.message.success(this.t('UMS.REPORTS.MESSAGES.DELETED'));
            this.loadReports();
            this.catalog.reload();
            this.loadHubOptions();
          },
          error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.REPORTS.MESSAGES.DELETE_ERROR'))
        });
      }
    });
  }
}
