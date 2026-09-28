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
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzGridModule } from 'ng-zorro-antd/grid';

import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { buildUmsListParams } from '../../../service/ums-list-params';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { TranslationService } from '../../../service/translation.service';
import { UmsPage } from '../../../api/ums';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { silentErrors } from '../../../interceptors/http-context.tokens';

@Component({
    selector: 'app-page-management',
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
    NzSwitchModule,
    NzInputNumberModule,
    NzTooltipModule,
    NzSelectModule,
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
            <i class="pi pi-file text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.PAGES.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.PAGES.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'" (click)="showCreateModal()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.PAGES.ADD_PAGE' | translate }}
            </button>
            <button nz-button (click)="loadPages()" [nzLoading]="loading">
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
              <label for="ugpSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="ugpSearch" type="text" nz-input [placeholder]="'COMMON.SEARCH' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterVisible">
              <label for="ugpVisible">{{ 'UMS.SHARED.FILTER_VISIBLE' | translate }}</label>
              <nz-select nzId="ugpVisible" [(ngModel)]="filterVisible" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.VISIBLE_ALL' | translate" nzValue=""></nz-option>
                <nz-option [nzLabel]="'UMS.SHARED.VISIBLE_YES' | translate" nzValue="yes"></nz-option>
                <nz-option [nzLabel]="'UMS.SHARED.VISIBLE_NO' | translate" nzValue="no"></nz-option>
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
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalPages }}</span>
          </div>
        </div>
  
      <nz-table
        #pagesTable
        [nzData]="pages"
        [nzLoading]="loading"
        [nzTotal]="totalPages"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="resourceCode" [nzSortOrder]="sortOrderFor('resourceCode')" (nzSortOrderChange)="onSort('resourceCode', $event)">{{ 'UMS.PAGES.PAGE_CODE' | translate }}</th>
            <th nzShowSort nzColumnKey="resourceName" [nzSortOrder]="sortOrderFor('resourceName')" (nzSortOrderChange)="onSort('resourceName', $event)">{{ 'UMS.PAGES.PAGE_NAME' | translate }}</th>
            <th nzShowSort nzColumnKey="resourceNameAr" [nzSortOrder]="sortOrderFor('resourceNameAr')" (nzSortOrderChange)="onSort('resourceNameAr', $event)">{{ 'UMS.PAGES.PAGE_NAME_AR' | translate }}</th>
            <th nzShowSort nzColumnKey="route" [nzSortOrder]="sortOrderFor('route')" (nzSortOrderChange)="onSort('route', $event)">{{ 'UMS.PAGES.ROUTE' | translate }}</th>
            <th nzShowSort nzColumnKey="displayOrder" [nzSortOrder]="sortOrderFor('displayOrder')" (nzSortOrderChange)="onSort('displayOrder', $event)">{{ 'UMS.PAGES.DISPLAY_ORDER' | translate }}</th>
            <th nzShowSort nzColumnKey="isVisible" [nzSortOrder]="sortOrderFor('isVisible')" (nzSortOrderChange)="onSort('isVisible', $event)">{{ 'UMS.PAGES.VISIBLE' | translate }}</th>
            <th nzWidth="150px">{{ 'UMS.PAGES.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (page of pagesTable.data; track page) {
            <tr>
              <td><code>{{ page.pageCode }}</code></td>
              <td>{{ page.pageName }}</td>
              <td>{{ page.pageNameAr }}</td>
              <td>{{ page.route }}</td>
              <td>{{ page.displayOrder }}</td>
              <td><nz-tag [nzColor]="page.isVisible ? 'green' : 'red'">{{ page.isVisible ? ('COMMON.YES' | translate) : ('COMMON.NO' | translate) }}</nz-tag></td>
              <td>
                <button nz-button nzType="link" nzSize="small" (click)="showDetailModal(page)" [nz-tooltip]="'COMMON.VIEW' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'" (click)="showEditModal(page)" [nz-tooltip]="'COMMON.EDIT' | translate">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'" (click)="confirmDelete(page)" [nz-tooltip]="'COMMON.DELETE' | translate">
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
        [nzTitle]="selectedPage ? ('UMS.PAGES.EDIT_PAGE' | translate) : ('UMS.PAGES.ADD_PAGE' | translate)"
        (nzOnCancel)="handleModalCancel()"
        (nzOnOk)="handleModalOk()"
        [nzWidth]="720"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            <form nz-form [formGroup]="pageForm" nzLayout="vertical">
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.PAGES.PAGE_CODE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="pageCode" placeholder="e.g., STUDENTS" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.PAGES.PARENT_PAGE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-select formControlName="parentPageId" nzAllowClear [nzPlaceHolder]="'UMS.FORM.PARENT_PAGE_NONE' | translate" nzShowSearch>
                            @for (p of parentPageOptions; track p) {
                              <nz-option [nzValue]="p.id" [nzLabel]="p.pageName"></nz-option>
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
                        <nz-form-label nzRequired>{{ 'UMS.PAGES.PAGE_NAME' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="pageName" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.PAGES.PAGE_NAME_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="pageNameAr" dir="rtl" />
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
                    <nz-form-label>{{ 'UMS.PAGES.DESCRIPTION' | translate }}</nz-form-label>
                    <nz-form-control>
                      <textarea nz-input formControlName="description" rows="2"></textarea>
                    </nz-form-control>
                  </nz-form-item>
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
                        <nz-form-label>{{ 'UMS.PAGES.ROUTE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="route" placeholder="/students" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.PAGES.ICON' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="icon" placeholder="pi pi-users" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.PAGES.DISPLAY_ORDER' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-input-number formControlName="displayOrder" [nzMin]="0" style="width: 100%;"></nz-input-number>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.PAGES.VISIBLE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-switch formControlName="isVisible"></nz-switch>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.PAGES.IS_ACTIVE' | translate }}</nz-form-label>
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
      <!-- The detail view. The GET /{{id}} endpoint behind this page existed
           all along and nothing called it; the row was only ever readable in the
           table. Same shape as the one on Staff users. -->
      <nz-modal
        [(nzVisible)]="isDetailModalVisible"
        [nzTitle]="'UMS.PAGES.TITLE' | translate"
        [nzWidth]="720"
        [nzFooter]="detailModalFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #detailModalFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
          @if (detailPage) {
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'"
              (click)="isDetailModalVisible = false; showEditModal(detailPage!)">
              <span nz-icon nzType="edit"></span> {{ 'COMMON.EDIT' | translate }}
            </button>
          }
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailPage) {
            <div class="dv-view">
              <div class="dv-section">
                <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                <nz-descriptions-item [nzTitle]="'UMS.PAGES.PAGE_CODE' | translate"><code>{{ detailPage.pageCode }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PAGES.PAGE_NAME' | translate">{{ detailPage.pageName }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PAGES.PAGE_NAME_AR' | translate">{{ detailPage.pageNameAr || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PAGES.ROUTE' | translate"><code>{{ detailPage.route || ('COMMON.NOT_AVAILABLE' | translate) }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PAGES.DISPLAY_ORDER' | translate">{{ detailPage.displayOrder }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.PAGES.VISIBLE' | translate"><nz-tag [nzColor]="detailPage.isVisible ? 'green' : 'default'">{{ (detailPage.isVisible ? 'COMMON.YES' : 'COMMON.NO') | translate }}</nz-tag></nz-descriptions-item>
                </nz-descriptions>
              </div>
            </div>
          }
        </ng-container>
      </nz-modal>
    `,
    styles: []
})
export class PageManagementComponent implements OnInit {
  pages: UmsPage[] = [];
  parentPageOptions: UmsPage[] = [];
  /** The row the detail dialog is showing. */
  detailPage: UmsPage | null = null;
  isDetailModalVisible = false;

  loading = false;
  saving = false;
  searchKeyword = '';
  filterVisible = '';
  sortBy = 'displayOrder';
  sortDir: 'asc' | 'desc' = 'asc';
  totalPages = 0;
  pageIndex = 1;
  pageSize = 10;

  isModalVisible = false;
  selectedPage: UmsPage | null = null;
  pageForm!: FormGroup;

  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/pages`;

  constructor(
    private http: HttpClient,
    private modal: NzModalService,
    private message: ToastService,
    private fb: FormBuilder,
    public translationService: TranslationService
  ) {
    this.pageForm = this.fb.group({
      pageCode: ['', Validators.required],
      pageName: ['', Validators.required],
      pageNameAr: [''],
      description: [''],
      route: [''],
      icon: [''],
      parentPageId: [null as number | null],
      displayOrder: [0],
      isVisible: [true],
      isActive: [true]
    });
  }

  ngOnInit(): void {
    this.loadPages();
    this.loadParentPageOptions();
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  loadParentPageOptions(): void {
    this.http.get<UmsPage[]>(`${this.apiUrl}/all`).subscribe({
      next: (pages) => this.parentPageOptions = pages
    });
  }

  resolveVisible(): boolean | undefined {
    if (this.filterVisible === 'yes') return true;
    if (this.filterVisible === 'no') return false;
    return undefined;
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
      this.sortBy = 'displayOrder';
      this.sortDir = 'asc';
    }
    this.pageIndex = 1;
    this.loadPages();
  }

  /** Every filter back to its resting value, and the list from the first page. */
  clearSearchFilters(): void {
    this.searchKeyword = '';
    this.filterVisible = '';
    this.applySearchFilters();
  }

  get hasSearchFilters(): boolean {
    return !!this.searchKeyword?.trim() || !!this.filterVisible;
  }

  applySearchFilters(): void {
    this.pageIndex = 1;
    this.loadPages();
  }

  loadPages(): void {
    this.loading = true;
    const params = buildUmsListParams({
      page: this.pageIndex - 1,
      size: this.pageSize,
      sortBy: this.sortBy,
      sortDir: this.sortDir,
      keyword: this.searchKeyword?.trim(),
      visible: this.resolveVisible()
    });
    this.http.get<any>(`${this.apiUrl}/`, { params }).subscribe({
      next: (response) => {
        this.pages = response.content;
        this.totalPages = response.totalElements;
        this.loading = false;
      },
      error: () => {
        this.message.error(this.t('UMS.PAGES.MESSAGES.LOAD_ERROR'));
        this.loading = false;
      }
    });
  }

  onPageChange(pageIndex: number): void {
    this.pageIndex = pageIndex;
    this.loadPages();
  }

  onPageSizeChange(ps: number): void {
    this.pageSize = ps;
    this.pageIndex = 1;
    this.loadPages();
  }

  private resetForm(page?: Partial<UmsPage>): void {
    this.pageForm.reset({
      pageCode: page?.pageCode ?? '',
      pageName: page?.pageName ?? '',
      pageNameAr: page?.pageNameAr ?? '',
      description: page?.description ?? '',
      route: page?.route ?? '',
      icon: page?.icon ?? '',
      parentPageId: page?.parentPageId ?? null,
      displayOrder: page?.displayOrder ?? 0,
      isVisible: page?.isVisible ?? true,
      isActive: page?.isActive ?? true
    });
    if (this.selectedPage) {
      this.pageForm.get('pageCode')?.disable();
    } else {
      this.pageForm.get('pageCode')?.enable();
    }
    this.parentPageOptions = this.parentPageOptions.filter(p => p.id !== this.selectedPage?.id);
  }

  showCreateModal(): void {
    this.selectedPage = null;
    this.loadParentPageOptions();
    this.resetForm();
    this.isModalVisible = true;
  }

  showDetailModal(page: UmsPage): void {
    this.detailPage = page;
    this.isDetailModalVisible = true;
  }

  showEditModal(page: UmsPage): void {
    this.selectedPage = page;
    this.loadParentPageOptions();
    this.resetForm(page);
    this.isModalVisible = true;
  }

  handleModalCancel(): void {
    this.isModalVisible = false;
  }

  private validateForm(): boolean {
    if (this.pageForm.valid) {
      return true;
    }
    Object.values(this.pageForm.controls).forEach(c => {
      c.markAsDirty();
      c.updateValueAndValidity();
    });
    return false;
  }

  handleModalOk(): void {
    if (!this.validateForm()) {
      return;
    }
    this.saving = true;
    const payload = this.pageForm.getRawValue();

    if (this.selectedPage) {
      this.http.put<UmsPage>(`${this.apiUrl}/${this.selectedPage.id}`, payload, { context: silentErrors() }).subscribe({
        next: () => {
          this.message.success(this.t('UMS.PAGES.MESSAGES.UPDATED'));
          this.isModalVisible = false;
          this.loadPages();
          this.saving = false;
        },
        error: (err) => {
          this.message.error(extractApiErrorMessage(err) || this.t('UMS.PAGES.MESSAGES.UPDATE_ERROR'));
          this.saving = false;
        }
      });
    } else {
      this.http.post<UmsPage>(this.apiUrl, payload, { context: silentErrors() }).subscribe({
        next: () => {
          this.message.success(this.t('UMS.PAGES.MESSAGES.CREATED'));
          this.isModalVisible = false;
          this.loadPages();
          this.loadParentPageOptions();
          this.saving = false;
        },
        error: (err) => {
          this.message.error(extractApiErrorMessage(err) || this.t('UMS.PAGES.MESSAGES.CREATE_ERROR'));
          this.saving = false;
        }
      });
    }
  }

  confirmDelete(page: UmsPage): void {
    this.modal.confirm({
      nzTitle: this.t('UMS.PAGES.MESSAGES.DELETE_CONFIRM'),
      nzContent: `${this.t('UMS.PAGES.PAGE_CODE')}: ${page.pageCode}`,
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => {
        this.http.delete(`${this.apiUrl}/${page.id}`, { context: silentErrors() }).subscribe({
          next: () => {
            this.message.success(this.t('UMS.PAGES.MESSAGES.DELETED'));
            this.loadPages();
            this.loadParentPageOptions();
          },
          error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.PAGES.MESSAGES.DELETE_ERROR'))
        });
      }
    });
  }
}
