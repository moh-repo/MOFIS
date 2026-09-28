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
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzGridModule } from 'ng-zorro-antd/grid';

import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { buildUmsListParams } from '../../../service/ums-list-params';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { CodeListService } from '../../../service/code-list.service';
import { TranslationService } from '../../../service/translation.service';
import { changesOnly } from '../../../service/reload-streams.util';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { silentErrors } from '../../../interceptors/http-context.tokens';

interface ApiLink {
  id: number;
  resourceCode: string;
  resourceName: string;
  resourceNameAr: string;
  method: string;
  urlPattern: string;
  isActive: boolean;
}

@Component({
    selector: 'app-api-link-management',
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
    NzSelectModule,
    NzTooltipModule,
    NzSwitchModule,
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
            <i class="pi pi-link text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.API_LINKS.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.API_LINKS.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'" (click)="showCreateModal()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.API_LINKS.ADD_API_LINK' | translate }}
            </button>
            <button nz-button (click)="loadApiLinks()" [nzLoading]="loading">
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
              <label for="uaSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="uaSearch" type="text" nz-input [placeholder]="'COMMON.SEARCH' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterMethod">
              <label for="uaMethod">{{ 'UMS.SHARED.FILTER_METHOD' | translate }}</label>
              <nz-select nzId="uaMethod" [(ngModel)]="filterMethod" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_METHOD_ALL' | translate" nzValue=""></nz-option>
                @for (opt of methodFilterOptions; track opt.value) {
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
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalApiLinks }}</span>
          </div>
        </div>
  
      <nz-table
        #apiLinksTable
        [nzData]="apiLinks"
        [nzLoading]="loading"
        [nzTotal]="totalApiLinks"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="resourceCode" [nzSortOrder]="sortOrderFor('resourceCode')" (nzSortOrderChange)="onSort('resourceCode', $event)">{{ 'UMS.API_LINKS.API_CODE' | translate }}</th>
            <th nzShowSort nzColumnKey="resourceName" [nzSortOrder]="sortOrderFor('resourceName')" (nzSortOrderChange)="onSort('resourceName', $event)">{{ 'UMS.API_LINKS.API_NAME' | translate }}</th>
            <th nzShowSort nzColumnKey="method" [nzSortOrder]="sortOrderFor('method')" (nzSortOrderChange)="onSort('method', $event)">{{ 'UMS.API_LINKS.METHOD' | translate }}</th>
            <th nzShowSort nzColumnKey="urlPattern" [nzSortOrder]="sortOrderFor('urlPattern')" (nzSortOrderChange)="onSort('urlPattern', $event)">{{ 'UMS.API_LINKS.URL_PATTERN' | translate }}</th>
            <th nzShowSort nzColumnKey="isActive" [nzSortOrder]="sortOrderFor('isActive')" (nzSortOrderChange)="onSort('isActive', $event)">{{ 'UMS.API_LINKS.STATUS' | translate }}</th>
            <th nzWidth="150px">{{ 'UMS.API_LINKS.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (link of apiLinksTable.data; track link) {
            <tr>
              <td><code>{{ link.resourceCode }}</code></td>
              <td>{{ link.resourceName }}</td>
              <td><nz-tag [nzColor]="getMethodColor(link.method)">{{ link.method }}</nz-tag></td>
              <td><code>{{ link.urlPattern }}</code></td>
              <td>
                <nz-tag [nzColor]="link.isActive ? 'green' : 'red'">
                  {{ link.isActive ? ('UMS.STATUS.ACTIVE' | translate) : ('UMS.STATUS.INACTIVE' | translate) }}
                </nz-tag>
              </td>
              <td>
                <button nz-button nzType="link" nzSize="small" (click)="showDetailModal(link)" [nz-tooltip]="'COMMON.VIEW' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'" (click)="showEditModal(link)" [nz-tooltip]="'COMMON.EDIT' | translate">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'" (click)="confirmDelete(link)" [nz-tooltip]="'COMMON.DELETE' | translate">
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
        [nzTitle]="selectedLink ? ('UMS.API_LINKS.EDIT_API_LINK' | translate) : ('UMS.API_LINKS.ADD_API_LINK' | translate)"
        (nzOnCancel)="handleModalCancel()"
        (nzOnOk)="handleModalOk()"
        [nzWidth]="720"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            <form nz-form [formGroup]="linkForm" nzLayout="vertical">
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="api"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.API_LINKS.API_CODE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="resourceCode" placeholder="e.g., API_STUDENTS_GET" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.API_LINKS.METHOD' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <nz-select formControlName="method">
                            @for (opt of methodOptions; track opt) {
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
                        <nz-form-label nzRequired>{{ 'UMS.API_LINKS.API_NAME' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="resourceName" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.API_LINKS.API_NAME_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="resourceNameAr" dir="rtl" />
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
                        <nz-form-label nzRequired>{{ 'UMS.API_LINKS.URL_PATTERN' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'COMMON.REQUIRED_FIELD' | translate">
                          <input nz-input formControlName="urlPattern" placeholder="/api/v1/students/**" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="8">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.API_LINKS.IS_ACTIVE' | translate }}</nz-form-label>
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
        [nzTitle]="'UMS.API_LINKS.TITLE' | translate"
        [nzWidth]="720"
        [nzFooter]="detailModalFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #detailModalFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
          @if (detailLink) {
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'"
              (click)="isDetailModalVisible = false; showEditModal(detailLink!)">
              <span nz-icon nzType="edit"></span> {{ 'COMMON.EDIT' | translate }}
            </button>
          }
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailLink) {
            <div class="dv-view">
              <div class="dv-section">
                <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                <nz-descriptions-item [nzTitle]="'UMS.API_LINKS.API_CODE' | translate"><code>{{ detailLink.resourceCode }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.API_LINKS.API_NAME' | translate">{{ detailLink.resourceName }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.API_LINKS.METHOD' | translate"><nz-tag nzColor="blue">{{ detailLink.method }}</nz-tag></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.API_LINKS.URL_PATTERN' | translate"><code>{{ detailLink.urlPattern }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'COMMON.ACTIVE' | translate"><nz-tag [nzColor]="detailLink.isActive ? 'green' : 'default'">{{ (detailLink.isActive ? 'COMMON.YES' : 'COMMON.NO') | translate }}</nz-tag></nz-descriptions-item>
                </nz-descriptions>
              </div>
            </div>
          }
        </ng-container>
      </nz-modal>
    `,
    styles: []
})
export class ApiLinkManagementComponent implements OnInit {
  private readonly codeLists = inject(CodeListService);
  apiLinks: ApiLink[] = [];
  methodOptions: Array<{ label: string; value: string }> = [];
  methodFilterOptions: Array<{ label: string; value: string }> = [];
  /** The row the detail dialog is showing. */
  detailLink: ApiLink | null = null;
  isDetailModalVisible = false;

  loading = false;
  saving = false;
  searchKeyword = '';
  filterMethod = '';
  sortBy = 'resourceCode';
  sortDir: 'asc' | 'desc' = 'asc';
  totalApiLinks = 0;
  pageIndex = 1;
  pageSize = 10;

  isModalVisible = false;
  selectedLink: ApiLink | null = null;
  linkForm!: FormGroup;

  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/api-links`;

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
    this.linkForm = this.fb.group({
      resourceCode: ['', Validators.required],
      resourceName: ['', Validators.required],
      resourceNameAr: [''],
      method: ['GET', Validators.required],
      urlPattern: ['', Validators.required],
      isActive: [true]
    });
  }

  ngOnInit(): void {
    this.loadMethodOptions();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadMethodOptions());
    this.loadApiLinks();
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  private loadMethodOptions(): void {
    this.enumOptionsService.getDropdownOptions('http-method').subscribe(opts => {
      this.methodOptions = opts;
      this.methodFilterOptions = opts;
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
      this.sortBy = 'resourceCode';
      this.sortDir = 'asc';
    }
    this.pageIndex = 1;
    this.loadApiLinks();
  }

  /** Every filter back to its resting value, and the list from the first page. */
  clearSearchFilters(): void {
    this.searchKeyword = '';
    this.filterMethod = '';
    this.applySearchFilters();
  }

  get hasSearchFilters(): boolean {
    return !!this.searchKeyword?.trim() || !!this.filterMethod;
  }

  applySearchFilters(): void {
    this.pageIndex = 1;
    this.loadApiLinks();
  }

  loadApiLinks(): void {
    this.loading = true;
    const params = buildUmsListParams({
      page: this.pageIndex - 1,
      size: this.pageSize,
      sortBy: this.sortBy,
      sortDir: this.sortDir,
      keyword: this.searchKeyword?.trim(),
      method: this.filterMethod?.trim() || undefined
    });
    this.http.get<any>(`${this.apiUrl}/`, { params }).subscribe({
      next: (response) => {
        this.apiLinks = response.content || [];
        this.totalApiLinks = response.totalElements || 0;
        this.loading = false;
      },
      error: () => {
        this.message.error(this.t('UMS.API_LINKS.MESSAGES.LOAD_ERROR'));
        this.loading = false;
      }
    });
  }

  onPageChange(pageIndex: number): void { this.pageIndex = pageIndex; this.loadApiLinks(); }
  onPageSizeChange(ps: number): void { this.pageSize = ps; this.pageIndex = 1; this.loadApiLinks(); }

  /** ng-zorro preset for the method's http-method row (UI_SEVERITY). */
  getMethodColor(method: string): string {
    return this.codeLists.nzColor('http-method', method);
  }

  private resetForm(link?: Partial<ApiLink>): void {
    this.linkForm.reset({
      resourceCode: link?.resourceCode ?? '',
      resourceName: link?.resourceName ?? '',
      resourceNameAr: link?.resourceNameAr ?? '',
      method: link?.method ?? 'GET',
      urlPattern: link?.urlPattern ?? '',
      isActive: link?.isActive ?? true
    });
    if (this.selectedLink) {
      this.linkForm.get('resourceCode')?.disable();
    } else {
      this.linkForm.get('resourceCode')?.enable();
    }
  }

  showCreateModal(): void {
    this.selectedLink = null;
    this.resetForm();
    this.isModalVisible = true;
  }

  showDetailModal(link: ApiLink): void {
    this.detailLink = link;
    this.isDetailModalVisible = true;
  }

  showEditModal(link: ApiLink): void {
    this.selectedLink = link;
    this.resetForm(link);
    this.isModalVisible = true;
  }

  handleModalCancel(): void { this.isModalVisible = false; }

  handleModalOk(): void {
    if (this.linkForm.invalid) {
      Object.values(this.linkForm.controls).forEach(c => { c.markAsDirty(); c.updateValueAndValidity(); });
      return;
    }
    this.saving = true;
    const payload = this.linkForm.getRawValue();
    const req = this.selectedLink
      ? this.http.put<ApiLink>(`${this.apiUrl}/${this.selectedLink.id}`, payload, { context: silentErrors() })
      : this.http.post<ApiLink>(this.apiUrl, payload, { context: silentErrors() });

    req.subscribe({
      next: () => {
        this.message.success(this.t(this.selectedLink ? 'UMS.API_LINKS.MESSAGES.UPDATED' : 'UMS.API_LINKS.MESSAGES.CREATED'));
        this.isModalVisible = false;
        this.loadApiLinks();
        this.saving = false;
      },
      error: (err) => {
        this.message.error(extractApiErrorMessage(err) || this.t(this.selectedLink ? 'UMS.API_LINKS.MESSAGES.UPDATE_ERROR' : 'UMS.API_LINKS.MESSAGES.CREATE_ERROR'));
        this.saving = false;
      }
    });
  }

  confirmDelete(link: ApiLink): void {
    this.modal.confirm({
      nzTitle: this.t('UMS.API_LINKS.MESSAGES.DELETE_CONFIRM'),
      nzContent: `${this.t('UMS.API_LINKS.API_CODE')}: ${link.resourceCode}`,
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => this.http.delete(`${this.apiUrl}/${link.id}`, { context: silentErrors() }).subscribe({
        next: () => { this.message.success(this.t('UMS.API_LINKS.MESSAGES.DELETED')); this.loadApiLinks(); },
        error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.API_LINKS.MESSAGES.DELETE_ERROR'))
      })
    });
  }
}
