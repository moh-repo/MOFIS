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
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzGridModule } from 'ng-zorro-antd/grid';

import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsField, UmsPage } from '../../../api/ums';
import { changesOnly } from '../../../service/reload-streams.util';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { silentErrors } from '../../../interceptors/http-context.tokens';

/** The columns this screen can be ordered by — the headers and the dropdown share them. */
export type FieldSortKey = 'fieldCode' | 'fieldName' | 'fieldNameAr' | 'pageName' | 'fieldType' | 'isSensitive';

@Component({
    selector: 'app-field-management',
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
    NzSwitchModule,
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
            <i class="pi pi-list text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.FIELDS.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.FIELDS.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'" (click)="showCreateModal()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.FIELDS.ADD_FIELD' | translate }}
            </button>
            <button nz-button (click)="loadFields()" [nzLoading]="loading">
              <span nz-icon nzType="reload"></span>
              {{ 'COMMON.REFRESH' | translate }}
            </button>
          </div>
        </div>
  
        <!-- Filters. Shared layout: assets/layout/styles/_filter-bar.scss.
             The list is held whole and narrowed here, as it always was; the two
             sort lists stay because the table's own headers do not sort it. -->
        <div class="filter-bar">
          <div class="filter-bar__grid filter-bar__grid--cols-6">
            <div class="filter-bar__field filter-bar__field--wide" [class.filter-bar__field--active]="fieldSearchKeyword">
              <label for="ufSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="ufSearch" type="text" nz-input [placeholder]="'UMS.FIELDS.SEARCH_FIELDS' | translate" [(ngModel)]="fieldSearchKeyword" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterFieldType">
              <label for="ufType">{{ 'UMS.FIELDS.FIELD_TYPE' | translate }}</label>
              <nz-select nzId="ufType" [(ngModel)]="filterFieldType">
                <nz-option [nzLabel]="'UMS.SHARED.ALL' | translate" nzValue=""></nz-option>
                @for (opt of fieldTypeFilterOptions; track opt.value) {
                  <nz-option [nzLabel]="opt.label" [nzValue]="opt.value"></nz-option>
                }
              </nz-select>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterSensitiveKind">
              <label for="ufSensitive">{{ 'UMS.FIELDS.SENSITIVE' | translate }}</label>
              <nz-select nzId="ufSensitive" [(ngModel)]="filterSensitiveKind">
                <nz-option [nzLabel]="'UMS.SHARED.ALL' | translate" nzValue=""></nz-option>
                <nz-option [nzLabel]="'UMS.FIELDS.SENSITIVE_ONLY' | translate" nzValue="yes"></nz-option>
                <nz-option [nzLabel]="'UMS.FIELDS.NON_SENSITIVE_ONLY' | translate" nzValue="no"></nz-option>
              </nz-select>
            </div>
            <div class="filter-bar__field">
              <label for="ufSortKey">{{ 'UMS.FIELDS.SORT_BY' | translate }}</label>
              <nz-select nzId="ufSortKey" [(ngModel)]="fieldSortKey">
                <nz-option nzValue="fieldCode" [nzLabel]="'UMS.FIELDS.FIELD_CODE' | translate"></nz-option>
                <nz-option nzValue="fieldName" [nzLabel]="'UMS.FIELDS.FIELD_NAME' | translate"></nz-option>
                <nz-option nzValue="fieldNameAr" [nzLabel]="'UMS.FIELDS.FIELD_NAME_AR' | translate"></nz-option>
                <nz-option nzValue="pageName" [nzLabel]="'UMS.FIELDS.PAGE' | translate"></nz-option>
                <nz-option nzValue="fieldType" [nzLabel]="'UMS.FIELDS.FIELD_TYPE' | translate"></nz-option>
                <nz-option nzValue="isSensitive" [nzLabel]="'UMS.FIELDS.SENSITIVE' | translate"></nz-option>
              </nz-select>
            </div>
            <div class="filter-bar__field">
              <label for="ufSortDir">{{ 'UMS.FIELDS.SORT_ASC' | translate }} / {{ 'UMS.FIELDS.SORT_DESC' | translate }}</label>
              <nz-select nzId="ufSortDir" [(ngModel)]="fieldSortDirection">
                <nz-option nzValue="asc" [nzLabel]="'UMS.FIELDS.SORT_ASC' | translate"></nz-option>
                <nz-option nzValue="desc" [nzLabel]="'UMS.FIELDS.SORT_DESC' | translate"></nz-option>
              </nz-select>
            </div>
          </div>
          <div class="filter-bar__actions">
            <button nz-button [disabled]="!hasSearchFilters" (click)="clearSearchFilters()">
              <span nz-icon nzType="clear"></span>
              {{ 'COMMON.CLEAR' | translate }}
            </button>
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ displayFields.length }}</span>
          </div>
        </div>
  
      <nz-table #fieldsTable [nzData]="displayFields" [nzLoading]="loading" nzShowPagination [nzPageSize]="20">
        <thead>
          <tr>
            <th nzColumnKey="fieldCode" [nzSortFn]="true" [nzSortOrder]="sortOrderOf('fieldCode')"
                (nzSortOrderChange)="applySort('fieldCode', $event)">{{ 'UMS.FIELDS.FIELD_CODE' | translate }}</th>
            <th nzColumnKey="fieldName" [nzSortFn]="true" [nzSortOrder]="sortOrderOf('fieldName')"
                (nzSortOrderChange)="applySort('fieldName', $event)">{{ 'UMS.FIELDS.FIELD_NAME' | translate }}</th>
            <th nzColumnKey="fieldNameAr" [nzSortFn]="true" [nzSortOrder]="sortOrderOf('fieldNameAr')"
                (nzSortOrderChange)="applySort('fieldNameAr', $event)">{{ 'UMS.FIELDS.FIELD_NAME_AR' | translate }}</th>
            <th nzColumnKey="pageName" [nzSortFn]="true" [nzSortOrder]="sortOrderOf('pageName')"
                (nzSortOrderChange)="applySort('pageName', $event)">{{ 'UMS.FIELDS.PAGE' | translate }}</th>
            <th nzColumnKey="fieldType" [nzSortFn]="true" [nzSortOrder]="sortOrderOf('fieldType')"
                (nzSortOrderChange)="applySort('fieldType', $event)">{{ 'UMS.FIELDS.FIELD_TYPE' | translate }}</th>
            <th nzColumnKey="isSensitive" [nzSortFn]="true" [nzSortOrder]="sortOrderOf('isSensitive')"
                (nzSortOrderChange)="applySort('isSensitive', $event)">{{ 'UMS.FIELDS.SENSITIVE' | translate }}</th>
            <th nzWidth="150px">{{ 'UMS.FIELDS.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (field of fieldsTable.data; track field) {
            <tr>
              <td><code>{{ field.fieldCode }}</code></td>
              <td>{{ field.fieldName }}</td>
              <td>{{ field.fieldNameAr }}</td>
              <td><nz-tag nzColor="blue">{{ getPageName(field.pageId) }}</nz-tag></td>
              <td>{{ field.fieldType }}</td>
              <td>
                <nz-tag [nzColor]="field.isSensitive ? 'red' : 'green'">
                  {{ field.isSensitive ? ('COMMON.YES' | translate) : ('COMMON.NO' | translate) }}
                </nz-tag>
              </td>
              <td>
                <button nz-button nzType="link" nzSize="small" (click)="showDetailModal(field)" [nz-tooltip]="'COMMON.VIEW' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'" (click)="showEditModal(field)" [nz-tooltip]="'COMMON.EDIT' | translate">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'" (click)="confirmDelete(field)" [nz-tooltip]="'COMMON.DELETE' | translate">
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
        [nzTitle]="selectedField ? ('UMS.FIELDS.EDIT_FIELD' | translate) : ('UMS.FIELDS.ADD_FIELD' | translate)"
        (nzOnCancel)="handleModalCancel()"
        (nzOnOk)="handleModalOk()"
        [nzWidth]="720"
        [nzOkLoading]="saving">
        <ng-container *nzModalContent>
          <div class="dv-view">
            <form nz-form [formGroup]="fieldForm" nzLayout="vertical">
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
                  <span class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</span>
                </div>
                <div>
                  <div nz-row [nzGutter]="16">
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.FIELDS.PAGE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'UMS.FIELDS.PAGE_REQUIRED' | translate">
                          <nz-select formControlName="pageId" nzShowSearch [nzPlaceHolder]="'UMS.FIELDS.SELECT_PAGE' | translate">
                            @for (page of pages; track page) {
                              <nz-option [nzValue]="page.id" [nzLabel]="page.pageName"></nz-option>
                            }
                          </nz-select>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label nzRequired>{{ 'UMS.FIELDS.FIELD_CODE' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'UMS.FIELDS.FIELD_CODE_REQUIRED' | translate">
                          <input nz-input formControlName="fieldCode" placeholder="e.g., STUDENT_NAME" />
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
                        <nz-form-label nzRequired>{{ 'UMS.FIELDS.FIELD_NAME' | translate }}</nz-form-label>
                        <nz-form-control [nzErrorTip]="'UMS.FIELDS.FIELD_NAME_REQUIRED' | translate">
                          <input nz-input formControlName="fieldName" />
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.FIELDS.FIELD_NAME_AR' | translate }}</nz-form-label>
                        <nz-form-control>
                          <input nz-input formControlName="fieldNameAr" dir="rtl" />
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
                        <nz-form-label>{{ 'UMS.FIELDS.FIELD_TYPE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-select formControlName="fieldType">
                            @for (opt of fieldTypeOptions; track opt) {
                              <nz-option [nzValue]="opt.value" [nzLabel]="opt.label"></nz-option>
                            }
                          </nz-select>
                        </nz-form-control>
                      </nz-form-item>
                    </div>
                    <div nz-col [nzSpan]="12">
                      <nz-form-item>
                        <nz-form-label>{{ 'UMS.FIELDS.SENSITIVE' | translate }}</nz-form-label>
                        <nz-form-control>
                          <nz-switch formControlName="isSensitive"></nz-switch>
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
        [nzTitle]="'UMS.FIELDS.TITLE' | translate"
        [nzWidth]="720"
        [nzFooter]="detailModalFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #detailModalFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
          @if (detailField) {
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'"
              (click)="isDetailModalVisible = false; showEditModal(detailField!)">
              <span nz-icon nzType="edit"></span> {{ 'COMMON.EDIT' | translate }}
            </button>
          }
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailField) {
            <div class="dv-view">
              <div class="dv-section">
                <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                <nz-descriptions-item [nzTitle]="'UMS.FIELDS.FIELD_CODE' | translate"><code>{{ detailField.fieldCode }}</code></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.FIELDS.FIELD_NAME' | translate">{{ detailField.fieldName }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.FIELDS.FIELD_NAME_AR' | translate">{{ detailField.fieldNameAr || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.FIELDS.FIELD_TYPE' | translate"><nz-tag nzColor="blue">{{ detailField.fieldType || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-tag></nz-descriptions-item>
                <nz-descriptions-item [nzTitle]="'UMS.FIELDS.SENSITIVE' | translate"><nz-tag [nzColor]="detailField.isSensitive ? 'red' : 'default'">{{ (detailField.isSensitive ? 'COMMON.YES' : 'COMMON.NO') | translate }}</nz-tag></nz-descriptions-item>
                </nz-descriptions>
              </div>
            </div>
          }
        </ng-container>
      </nz-modal>
    `,
    styles: []
})
export class FieldManagementComponent implements OnInit {
  fields: UmsField[] = [];
  pages: UmsPage[] = [];
  fieldTypeOptions: Array<{ label: string; value: string }> = [];
  fieldTypeFilterOptions: Array<{ label: string; value: string }> = [];
  /** The row the detail dialog is showing. */
  detailField: UmsField | null = null;
  isDetailModalVisible = false;

  loading = false;
  saving = false;
  fieldSearchKeyword = '';
  filterFieldType = '';
  filterSensitiveKind = '';
  fieldSortKey: FieldSortKey = 'fieldCode';
  fieldSortDirection: 'asc' | 'desc' = 'asc';

  isModalVisible = false;
  selectedField: UmsField | null = null;
  fieldForm!: FormGroup;

  private pagesApiUrl = `${environment.apiUrl}/mohe/api/v1/ums/pages`;

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
    this.fieldForm = this.fb.group({
      pageId: [null as number | null, Validators.required],
      fieldCode: ['', Validators.required],
      fieldName: ['', Validators.required],
      fieldNameAr: [''],
      fieldType: ['TEXT'],
      isSensitive: [false]
    });
  }

  ngOnInit(): void {
    this.loadFieldTypeOptions();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadFieldTypeOptions());
    this.loadPages();
    this.loadFields();
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  private loadFieldTypeOptions(): void {
    this.enumOptionsService.getDropdownOptions('ums-field-type').subscribe(opts => {
      this.fieldTypeOptions = opts;
      this.fieldTypeFilterOptions = opts;
    });
  }

  loadPages(): void {
    this.http.get<UmsPage[]>(`${this.pagesApiUrl}/all`).subscribe({
      next: (pages) => this.pages = pages
    });
  }

  /** Every filter back to its resting value, and the list from the first page. */
  clearSearchFilters(): void {
    this.fieldSearchKeyword = '';
    this.filterFieldType = '';
    this.filterSensitiveKind = '';
    this.fieldSortKey = 'fieldCode';
    this.fieldSortDirection = 'asc';
  }

  get hasSearchFilters(): boolean {
    return !!this.fieldSearchKeyword?.trim() || !!this.filterFieldType || !!this.filterSensitiveKind;
  }

  loadFields(): void {
    this.loading = true;
    this.http.get<UmsPage[]>(`${this.pagesApiUrl}/all`).subscribe({
      next: (pages) => {
        this.fields = [];
        let loaded = 0;
        if (pages.length === 0) {
          this.loading = false;
          return;
        }
        pages.forEach(page => {
          this.http.get<UmsField[]>(`${this.pagesApiUrl}/${page.id}/fields`).subscribe({
            next: (fields) => {
              this.fields = [...this.fields, ...fields.map(f => ({ ...f, pageId: page.id }))];
              loaded++;
              if (loaded === pages.length) this.loading = false;
            },
            error: () => {
              loaded++;
              if (loaded === pages.length) this.loading = false;
            }
          });
        });
      }
    });
  }

  get displayFields(): UmsField[] {
    let rows = [...this.fields];
    const kw = this.fieldSearchKeyword.trim().toLowerCase();
    if (kw) {
      rows = rows.filter(f => {
        const pageName = this.getPageName(f.pageId).toLowerCase();
        return (f.fieldCode ?? '').toLowerCase().includes(kw)
          || (f.fieldName ?? '').toLowerCase().includes(kw)
          || (f.fieldNameAr ?? '').toLowerCase().includes(kw)
          || pageName.includes(kw);
      });
    }
    if (this.filterFieldType) rows = rows.filter(f => f.fieldType === this.filterFieldType);
    if (this.filterSensitiveKind === 'yes') rows = rows.filter(f => f.isSensitive);
    if (this.filterSensitiveKind === 'no') rows = rows.filter(f => !f.isSensitive);
    const dir = this.fieldSortDirection === 'asc' ? 1 : -1;
    rows.sort((a, b) => this.compareFields(a, b, this.fieldSortKey, dir));
    return rows;
  }

  /**
   * The arrow a column header shows. The headers and the sort-by dropdowns are
   * two views of one state, so clicking a header moves the dropdown with it.
   */
  sortOrderOf(key: FieldSortKey): 'ascend' | 'descend' | null {
    if (this.fieldSortKey !== key) return null;
    return this.fieldSortDirection === 'asc' ? 'ascend' : 'descend';
  }

  /** Third click on a header (order null) returns to the default order, by code. */
  applySort(key: FieldSortKey, order: string | null): void {
    if (!order) {
      this.fieldSortKey = 'fieldCode';
      this.fieldSortDirection = 'asc';
      return;
    }
    this.fieldSortKey = key;
    this.fieldSortDirection = order === 'ascend' ? 'asc' : 'desc';
  }

  private compareFields(a: UmsField, b: UmsField, key: string, dir: number): number {
    const va = this.fieldSortValue(a, key);
    const vb = this.fieldSortValue(b, key);
    if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
    return String(va).localeCompare(String(vb)) * dir;
  }

  private fieldSortValue(f: UmsField, key: string): string | number {
    switch (key) {
      case 'pageName': return this.getPageName(f.pageId);
      case 'fieldCode': return f.fieldCode ?? '';
      case 'fieldName': return f.fieldName ?? '';
      case 'fieldNameAr': return f.fieldNameAr ?? '';
      case 'fieldType': return f.fieldType ?? '';
      case 'isSensitive': return f.isSensitive ? 1 : 0;
      default: return '';
    }
  }

  getPageName(pageId: number): string {
    return this.pages.find(p => p.id === pageId)?.pageName || this.t('COMMON.UNKNOWN');
  }

  private resetForm(field?: Partial<UmsField>): void {
    this.fieldForm.reset({
      pageId: field?.pageId ?? null,
      fieldCode: field?.fieldCode ?? '',
      fieldName: field?.fieldName ?? '',
      fieldNameAr: field?.fieldNameAr ?? '',
      fieldType: field?.fieldType ?? 'TEXT',
      isSensitive: field?.isSensitive ?? false
    });
    if (this.selectedField) {
      this.fieldForm.get('fieldCode')?.disable();
      this.fieldForm.get('pageId')?.disable();
    } else {
      this.fieldForm.get('fieldCode')?.enable();
      this.fieldForm.get('pageId')?.enable();
    }
  }

  showCreateModal(): void {
    this.selectedField = null;
    this.resetForm();
    this.isModalVisible = true;
  }

  showDetailModal(field: UmsField): void {
    this.detailField = field;
    this.isDetailModalVisible = true;
  }

  showEditModal(field: UmsField): void {
    this.selectedField = field;
    this.resetForm(field);
    this.isModalVisible = true;
  }

  handleModalCancel(): void { this.isModalVisible = false; }

  /** The message for the first required control that is empty, naming the field (live QA M4, 24 Sep 2026). */
  static readonly REQUIRED_MESSAGE: Record<string, string> = {
    pageId: 'UMS.FIELDS.PAGE_REQUIRED',
    fieldCode: 'UMS.FIELDS.FIELD_CODE_REQUIRED',
    fieldName: 'UMS.FIELDS.FIELD_NAME_REQUIRED'
  };

  firstMissingFieldMessage(): string {
    const missing = Object.keys(FieldManagementComponent.REQUIRED_MESSAGE)
      .find(name => this.fieldForm.get(name)?.invalid);
    return this.t(missing ? FieldManagementComponent.REQUIRED_MESSAGE[missing] : 'COMMON.REQUIRED_FIELD');
  }

  handleModalOk(): void {
    if (this.fieldForm.invalid) {
      Object.values(this.fieldForm.controls).forEach(c => { c.markAsDirty(); c.updateValueAndValidity(); });
      // The tips under the inputs name the field; so does this, for a dialog
      // whose page picker sits above the fold or was never touched.
      this.message.error(this.firstMissingFieldMessage());
      return;
    }
    this.saving = true;
    const payload = this.fieldForm.getRawValue();
    const req = this.selectedField
      ? this.http.put<UmsField>(`${this.pagesApiUrl}/fields/${this.selectedField.id}`, payload, { context: silentErrors() })
      : this.http.post<UmsField>(`${this.pagesApiUrl}/fields`, payload, { context: silentErrors() });

    req.subscribe({
      next: () => {
        this.message.success(this.t(this.selectedField ? 'UMS.FIELDS.FIELD_UPDATED' : 'UMS.FIELDS.FIELD_CREATED'));
        this.isModalVisible = false;
        this.loadFields();
        this.saving = false;
      },
      error: (err) => {
        this.message.error(extractApiErrorMessage(err) || this.t(this.selectedField ? 'UMS.FIELDS.FAILED_UPDATE' : 'UMS.FIELDS.FAILED_CREATE'));
        this.saving = false;
      }
    });
  }

  confirmDelete(field: UmsField): void {
    this.modal.confirm({
      nzTitle: this.t('UMS.FIELDS.DELETE_CONFIRM'),
      nzContent: `${this.t('UMS.FIELDS.FIELD_CODE')}: ${field.fieldCode}`,
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => this.http.delete(`${this.pagesApiUrl}/fields/${field.id}`, { context: silentErrors() }).subscribe({
        next: () => { this.message.success(this.t('UMS.FIELDS.FIELD_DELETED')); this.loadFields(); },
        error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.FIELDS.FAILED_DELETE'))
      })
    });
  }
}
