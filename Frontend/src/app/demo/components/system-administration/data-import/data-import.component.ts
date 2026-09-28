import { Component, OnDestroy, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { NzTableModule, NzTableFilterList, NzTableQueryParams, NzTableSortOrder } from 'ng-zorro-antd/table';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { ToastService } from '../../../service/toast.service';
import { NzUploadModule, NzUploadFile } from 'ng-zorro-antd/upload';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzStepsModule } from 'ng-zorro-antd/steps';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzResultModule } from 'ng-zorro-antd/result';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { environment } from '../../../../../environments/environment';

import { TranslationService } from '../../../service/translation.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { LookupService } from '../../../service/lookup.service';
import { StudyCountryIn } from '../../../api/study-country-in';

/** The letterhead the importer reads off the rows above the grid. */
interface ScholarshipHeader {
    republic?: string;
    ministry?: string;
    department?: string;
    reportTitle?: string;
    entity?: string;
    quarter?: string;
    quarterNo?: number;
    year?: string;
    yearNo?: number;
    statementDate?: string;
    notes?: string[];
}

/** What the ETL says it did, or would do. */
interface EtlReport {
    rowsStaged?: number;
    rowsReplaced?: number;
    /** Rows a person had corrected, which the run left alone. */
    rowsPreserved?: number;
    rowsRejected?: number;
    quarter?: number;
    currentQuarter?: string;
    resolution?: { lookup: string; resolved: number; unresolved: number; notApplicable?: number }[];
    /** Spellings nobody has taught the resolver yet. Not errors. */
    unresolved?: { lookup: string; value: string; rows: number }[];
    rejected?: { sourceId: number; reason: string }[];
}

interface ScholarshipPreview {
    filename?: string;
    sheetName?: string;
    /** Where the parser found the grid captions, 1-based. Not row 1 on these forms. */
    headerRow?: number;
    header?: ScholarshipHeader;
    totalRows?: number;
    skippedRows?: number;
    warnings?: string[];
    previewRows?: Record<string, string | number | null>[];
}

@Component({
    selector: 'app-data-import',
    imports: [
    FormsModule,
    NzTableModule,
    NzInputModule,
    NzTooltipModule,
    NzButtonModule,
    NzCardModule,
    NzUploadModule,
    NzIconModule,
    NzSpinModule,
    NzSelectModule,
    NzStepsModule,
    NzAlertModule,
    NzDividerModule,
    NzTagModule,
    NzResultModule,
    NzEmptyModule,
    NzCheckboxModule,
    NzModalModule,
    TranslatePipe
],
    template: `
        <div class="import-container">
          <!-- Scholarship data import: the quarterly financial-aid workbook -->
          <!-- The title is a template rather than a string so the card's
               heading can lead with an icon, as every other page does. -->
          <nz-card [nzTitle]="scholarshipTitleTemplate">
            <ng-template #scholarshipTitleTemplate>
              <span nz-icon nzType="import" class="mx-2"></span>{{ 'DATA_IMPORT.SCHOLARSHIP_TITLE' | translate }}
            </ng-template>
            <nz-steps [nzCurrent]="scholarshipStep" nzSize="small" style="margin-bottom: 24px;">
              <nz-step [nzTitle]="'DATA_IMPORT.STEP_UPLOAD' | translate"></nz-step>
              <nz-step [nzTitle]="'DATA_IMPORT.FILE_PREVIEW' | translate"></nz-step>
              <nz-step [nzTitle]="'DATA_IMPORT.STEP_IMPORT' | translate"></nz-step>
              <nz-step [nzTitle]="'DATA_IMPORT.VIEW_DATA' | translate"></nz-step>
            </nz-steps>

            <!-- Step 0: choose the country, then the workbook -->
            @if (scholarshipStep === 0) {
              <div>
                <nz-alert nzType="info" [nzMessage]="'DATA_IMPORT.SCHOLARSHIP_DESC' | translate" nzShowIcon style="margin-bottom: 16px;"></nz-alert>

                <label class="scholarship-label">{{ 'DATA_IMPORT.SCHOLARSHIP_COUNTRY' | translate }}</label>
                <nz-select style="width: 100%; margin-bottom: 16px;" [(ngModel)]="scholarshipCountryId"
                  [nzPlaceHolder]="'DATA_IMPORT.SCHOLARSHIP_COUNTRY_PLACEHOLDER' | translate" nzShowSearch nzAllowClear>
                  @for (country of countries; track country.id) {
                    <nz-option [nzValue]="country.id" [nzLabel]="countryLabel(country)"></nz-option>
                  }
                </nz-select>

                <nz-upload
                  nzType="drag"
                  [nzMultiple]="false"
                  [nzBeforeUpload]="beforeScholarshipUpload"
                  nzAccept=".xlsx,.xlsm,.xls">
                  <p class="ant-upload-drag-icon">
                    <span nz-icon nzType="file-excel"></span>
                  </p>
                  <p class="ant-upload-text">{{ 'DATA_IMPORT.SCHOLARSHIP_UPLOAD' | translate }}</p>
                  <p class="ant-upload-hint">{{ 'DATA_IMPORT.SCHOLARSHIP_HINT' | translate }}</p>
                </nz-upload>

                @if (scholarshipFile) {
                  <div style="margin-top: 16px;">
                    <nz-tag nzColor="blue">{{ scholarshipFile.name }}</nz-tag>
                    <button nz-button nzType="primary" (click)="scholarshipPreview()" [nzLoading]="isScholarshipPreviewLoading" style="margin-inline-start: 16px;">
                      {{ 'DATA_IMPORT.PREVIEW_BTN' | translate }}
                    </button>
                  </div>
                }

                <!-- Both default off, so the plain path appends. Ticking either
                     is a destructive choice and is confirmed with row counts
                     before the import runs. -->
                <div class="scholarship-delete-options">
                  <label nz-checkbox [(ngModel)]="scholarshipDeleteCountry" [nzDisabled]="scholarshipDeleteAll">
                    {{ 'DATA_IMPORT.SCHOLARSHIP_DELETE_COUNTRY' | translate }}
                  </label>
                  <span class="scholarship-delete-hint">{{ 'DATA_IMPORT.SCHOLARSHIP_DELETE_COUNTRY_HINT' | translate }}</span>

                  <label nz-checkbox [(ngModel)]="scholarshipDeleteAll" class="scholarship-delete-danger">
                    {{ 'DATA_IMPORT.SCHOLARSHIP_DELETE_ALL' | translate }}
                  </label>
                  <span class="scholarship-delete-hint">{{ 'DATA_IMPORT.SCHOLARSHIP_DELETE_ALL_HINT' | translate }}</span>
                </div>

                <nz-divider></nz-divider>
                <button nz-button nzType="default" (click)="goToViewScholarshipData()">
                  <span nz-icon nzType="table"></span> {{ 'DATA_IMPORT.SCHOLARSHIP_VIEW_EXISTING' | translate }}
                </button>
              </div>
            }

            <!-- Step 1: what was read, before anything is written -->
            @if (scholarshipStep === 1) {
              <div>
                <h4>{{ 'DATA_IMPORT.FILE_PREVIEW' | translate }}: {{ scholarshipPreviewData?.filename }}</h4>

                <nz-alert nzType="info" nzShowIcon style="margin-bottom: 16px;" [nzMessage]="scholarshipPreviewSummary()"></nz-alert>

                @if (scholarshipDeleteMode !== 'NONE') {
                  <nz-alert nzType="error" nzShowIcon style="margin-bottom: 16px;"
                    [nzMessage]="scholarshipDeleteWarning()"></nz-alert>
                }

                @for (warning of scholarshipPreviewData?.warnings || []; track warning) {
                  <nz-alert nzType="warning" [nzMessage]="warning" nzShowIcon style="margin-bottom: 8px;"></nz-alert>
                }

                <!-- The letterhead above the grid, which becomes a column on every row -->
                @if (scholarshipPreviewData?.header) {
                  <div class="scholarship-header-block">
                    @for (field of scholarshipHeaderFields(); track field.key) {
                      <div class="row-data-field">
                        <span class="row-data-label">{{ field.key | translate }}</span>
                        <span class="row-data-value">{{ field.value || '-' }}</span>
                      </div>
                    }
                  </div>
                }

                <nz-table #scholarshipPreviewTable [nzData]="scholarshipPreviewData?.previewRows || []"
                  nzSize="small" [nzScroll]="{ x: scholarshipGridWidth }">
                  <thead>
                    <tr>
                      @for (column of scholarshipGridColumns; track column) {
                        <th [nzWidth]="columnWidth(column)">{{ ('DATA_IMPORT.SCHOLARSHIP_COL_' + column) | translate }}</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of scholarshipPreviewTable.data; track row) {
                      <tr>
                        @for (column of scholarshipGridColumns; track column) {
                          <td>{{ row[column] }}</td>
                        }
                      </tr>
                    }
                  </tbody>
                </nz-table>

                <div style="margin-top: 16px;">
                  <button nz-button (click)="scholarshipStep = 0">{{ 'DATA_IMPORT.BACK' | translate }}</button>
                  <button nz-button nzType="primary" (click)="importScholarshipData()"
                    [nzLoading]="isScholarshipImporting" [disabled]="!scholarshipCountryId" style="margin-inline-start: 8px;">
                    <span nz-icon nzType="import"></span> {{ 'DATA_IMPORT.SCHOLARSHIP_IMPORT_BTN' | translate }}
                  </button>
                  @if (!scholarshipCountryId) {
                    <span class="scholarship-country-required">{{ 'DATA_IMPORT.SCHOLARSHIP_COUNTRY_REQUIRED' | translate }}</span>
                  }
                </div>
              </div>
            }

            <!-- Step 2: result -->
            @if (scholarshipStep === 2) {
              <div>
                @if (scholarshipResult?.success) {
                  <nz-result nzStatus="success" [nzTitle]="'DATA_IMPORT.SCHOLARSHIP_IMPORT_DONE' | translate">
                    <div nz-result-content>
                      <p><strong>{{ 'DATA_IMPORT.FILE' | translate }}:</strong> {{ scholarshipResult.filename }}</p>
                      <p><strong>{{ 'DATA_IMPORT.ROWS_IMPORTED' | translate }}:</strong> <nz-tag nzColor="green">{{ scholarshipResult.rowsImported }}</nz-tag></p>
                      <p><strong>{{ 'DATA_IMPORT.SCHOLARSHIP_SKIPPED' | translate }}:</strong> {{ scholarshipResult.skippedRows }}</p>
                      @if (scholarshipResult.rowsDeleted) {
                        <p><strong>{{ 'DATA_IMPORT.SCHOLARSHIP_ROWS_DELETED' | translate }}:</strong>
                          <nz-tag nzColor="red">{{ scholarshipResult.rowsDeleted }}</nz-tag></p>
                      }
                      <p><strong>{{ 'DATA_IMPORT.SCHOLARSHIP_STORED_FILE' | translate }}:</strong> <code>{{ scholarshipResult.storedFile }}</code></p>
                    </div>
                    <div nz-result-extra>
                      <button nz-button (click)="resetScholarshipImport()">{{ 'DATA_IMPORT.IMPORT_ANOTHER' | translate }}</button>
                      <button nz-button nzType="primary" (click)="goToViewScholarshipData()" style="margin-inline-start: 8px;">
                        <span nz-icon nzType="table"></span> {{ 'DATA_IMPORT.VIEW_DATA' | translate }}
                      </button>
                    </div>
                  </nz-result>
                }
                @if (scholarshipResult && !scholarshipResult.success) {
                  <nz-result nzStatus="error" [nzTitle]="'DATA_IMPORT.SCHOLARSHIP_IMPORT_FAILED' | translate">
                    <div nz-result-content>
                      <nz-alert nzType="error" [nzMessage]="scholarshipResult.message" nzShowIcon></nz-alert>
                    </div>
                    <div nz-result-extra>
                      <button nz-button (click)="scholarshipStep = 1">{{ 'DATA_IMPORT.BACK' | translate }}</button>
                      <button nz-button nzType="primary" (click)="resetScholarshipImport()" style="margin-inline-start: 8px;">{{ 'DATA_IMPORT.IMPORT_ANOTHER' | translate }}</button>
                    </div>
                  </nz-result>
                }
              </div>
            }

            <!-- Step 3: what is already imported -->
            @if (scholarshipStep === 3) {
              <div>
                <div class="scholarship-toolbar">
                  <div class="scholarship-toolbar-field">
                    <label class="scholarship-label">{{ 'DATA_IMPORT.SCHOLARSHIP_COUNTRY' | translate }}</label>
                    <nz-select style="width: 100%;" [(ngModel)]="scholarshipFilterCountryId"
                      [nzPlaceHolder]="'DATA_IMPORT.SCHOLARSHIP_ALL_COUNTRIES' | translate" nzShowSearch nzAllowClear
                      (ngModelChange)="onScholarshipCountryChange()">
                      @for (country of countries; track country.id) {
                        <nz-option [nzValue]="country.id" [nzLabel]="countryLabel(country)"></nz-option>
                      }
                    </nz-select>
                  </div>
                  <div class="scholarship-toolbar-field">
                    <label class="scholarship-label">{{ 'DATA_IMPORT.SCHOLARSHIP_SEARCH' | translate }}</label>
                    <nz-input-group [nzPrefix]="searchPrefix" [nzSuffix]="searchSuffix">
                      <input nz-input type="text" [(ngModel)]="scholarshipSearch"
                        [placeholder]="'DATA_IMPORT.SCHOLARSHIP_SEARCH_HINT' | translate"
                        (ngModelChange)="scholarshipSearch$.next($event)" />
                    </nz-input-group>
                    <ng-template #searchPrefix><span nz-icon nzType="search"></span></ng-template>
                    <ng-template #searchSuffix>
                      @if (scholarshipSearch) {
                        <span nz-icon nzType="close-circle" nzTheme="fill" class="scholarship-search-clear"
                          (click)="clearScholarshipSearch()"></span>
                      }
                    </ng-template>
                  </div>
                  <button nz-button (click)="resetScholarshipQuery()" [disabled]="!hasScholarshipQuery"
                    nz-tooltip [nzTooltipTitle]="'DATA_IMPORT.SCHOLARSHIP_CLEAR_QUERY' | translate">
                    <span nz-icon nzType="clear"></span>
                    {{ 'DATA_IMPORT.SCHOLARSHIP_CLEAR_QUERY' | translate }}
                  </button>
                </div>

                <!-- Stays mounted while loading. Wrapping it in @if (!loading)
                     tore the table out of the DOM on every page change and built
                     a fresh one from the response, which threw away its paging
                     state; nzLoading covers the same ground without that.

                     No nzScroll: these eight columns fit the page. The record has
                     thirty-odd fields and the table used to carry all of them,
                     which meant 2350px of horizontal scrolling to read one row.
                     The rest live in the record dialog. -->
                <nz-table
                  [nzData]="scholarshipTableData?.content || []" nzSize="small"
                  [nzLoading]="scholarshipTableLoading"
                  [nzPageIndex]="scholarshipPageIndex + 1" [nzPageSize]="scholarshipPageSize"
                  [nzTotal]="scholarshipTableData?.totalElements || 0" [nzFrontPagination]="false"
                  [nzNoResult]="scholarshipNoResult"
                  (nzQueryParams)="onScholarshipTableQuery($event)">
                  <thead>
                    <!-- nzSortFn and nzFilterFn are true, not functions: the sort
                         and the filter run in SQL. A function here would reorder
                         the ten rows on this page and call that a sort. -->
                    <tr>
                      <th nzWidth="70" nzColumnKey="ID" [nzSortFn]="true"
                        [nzSortOrder]="sortOrderFor('ID')">Id</th>
                      <th nzColumnKey="STUDENT_NAME" [nzSortFn]="true"
                        [nzSortOrder]="sortOrderFor('STUDENT_NAME')">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_STUDENT_NAME' | translate }}</th>
                      <th nzWidth="110" nzColumnKey="COUNTRY" [nzSortFn]="true"
                        [nzSortOrder]="sortOrderFor('COUNTRY')"
                        [nzFilters]="scholarshipFilterLists['COUNTRY']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_COUNTRY' | translate }}</th>
                      <th nzWidth="130" nzColumnKey="DEGREE" [nzSortFn]="true"
                        [nzSortOrder]="sortOrderFor('DEGREE')"
                        [nzFilters]="scholarshipFilterLists['DEGREE']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_DEGREE' | translate }}</th>
                      <th nzWidth="110" nzColumnKey="HDR_QUARTER" [nzSortFn]="true"
                        [nzSortOrder]="sortOrderFor('HDR_QUARTER')"
                        [nzFilters]="scholarshipFilterLists['HDR_QUARTER']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.SCHOLARSHIP_HDR_QUARTER' | translate }}</th>
                      <th nzWidth="90" nzColumnKey="HDR_YEAR" [nzSortFn]="true"
                        [nzSortOrder]="sortOrderFor('HDR_YEAR')"
                        [nzFilters]="scholarshipFilterLists['HDR_YEAR']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.SCHOLARSHIP_HDR_YEAR' | translate }}</th>
                      <th nzWidth="120" nzColumnKey="AMOUNT" [nzSortFn]="true"
                        [nzSortOrder]="sortOrderFor('AMOUNT')">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_AMOUNT' | translate }}</th>
                      <th nzWidth="110">{{ 'DATA_IMPORT.ACTIONS' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of scholarshipTableData?.content || []; track row.ID) {
                      <tr>
                        <td>{{ row.ID }}</td>
                        <td>{{ row.STUDENT_NAME }}</td>
                        <td>{{ row.COUNTRY }}</td>
                        <td>{{ row.DEGREE }}</td>
                        <td>{{ row.HDR_QUARTER }}</td>
                        <td>{{ row.HDR_YEAR }}</td>
                        <td>{{ row.AMOUNT }}</td>
                        <td>
                          <button nz-button nzType="link" nzSize="small" (click)="openScholarshipDetail(row.ID)">
                            <span nz-icon nzType="eye"></span> {{ 'DATA_IMPORT.SCHOLARSHIP_VIEW_RECORD' | translate }}
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </nz-table>
                <ng-template #scholarshipNoResult>
                  <!-- "Nothing imported yet" and "nothing matched your search" look
                       identical on screen and mean opposite things. -->
                  <nz-empty [nzNotFoundContent]="(hasScholarshipQuery
                    ? 'DATA_IMPORT.SCHOLARSHIP_NO_MATCHES'
                    : 'DATA_IMPORT.SCHOLARSHIP_NO_DATA') | translate"></nz-empty>
                </ng-template>

                <!-- The whole record, laid out as a form. This is where the
                     columns taken off the table above went. -->
                <nz-modal [(nzVisible)]="scholarshipDetailVisible" nzWidth="900px" [nzFooter]="null"
                  [nzTitle]="('DATA_IMPORT.SCHOLARSHIP_DETAIL_TITLE' | translate) + ' #' + (scholarshipDetail?.['ID'] ?? '')"
                  (nzOnCancel)="closeScholarshipDetail()">
                  <ng-container *nzModalContent>
                    @if (scholarshipDetailLoading) {
                      <div style="text-align: center; padding: 48px;">
                        <nz-spin nzSimple></nz-spin>
                      </div>
                    }
                    @if (!scholarshipDetailLoading && scholarshipDetail) {
                      <!-- dir is set here rather than inherited: ng-zorro renders
                           the dialog into an overlay on <body>, outside the
                           layout wrapper that carries the page direction. -->
                      <div class="record" [dir]="isRtl ? 'rtl' : 'ltr'">

                        <header class="record-hero">
                          <div class="record-hero-main">
                            <span class="record-eyebrow">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_STUDENT_NAME' | translate }}</span>
                            <h3 class="record-name">{{ scholarshipDetail['STUDENT_NAME'] || '-' }}</h3>
                            <div class="record-chips">
                              @for (chip of recordChips(); track chip.label) {
                                <span class="record-chip">{{ chip.value }}</span>
                              }
                            </div>
                          </div>
                          <div class="record-amount">
                            <span class="record-amount-label">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_AMOUNT' | translate }}</span>
                            <span class="record-amount-value">{{ recordAmount() }}</span>
                          </div>
                        </header>

                        <!-- The short facts are all numbers or codes; tiles read
                             far faster than the same values in a label column. -->
                        <div class="record-stats">
                          @for (stat of recordStats(); track stat.label) {
                            <div class="record-stat">
                              <span class="record-stat-label">{{ stat.label | translate }}</span>
                              <span class="record-stat-value">{{ stat.value || '-' }}</span>
                            </div>
                          }
                        </div>

                        @if (recordNotes().length) {
                          <section class="record-section">
                            <h4 class="record-section-title">{{ 'DATA_IMPORT.SCHOLARSHIP_SECTION_NOTES' | translate }}</h4>
                            @for (note of recordNotes(); track note.label) {
                              <div class="record-note">
                                <span class="record-note-label">{{ note.label | translate }}</span>
                                <p class="record-note-text">{{ note.value }}</p>
                              </div>
                            }
                          </section>
                        }

                        @for (section of scholarshipDetailSections(); track section.title) {
                          <section class="record-section">
                            <h4 class="record-section-title">{{ section.title | translate }}</h4>
                            <dl class="record-list">
                              @for (field of section.fields; track field.label) {
                                <div class="record-row" [class.record-row--wide]="field.wide">
                                  <dt>{{ field.label | translate }}</dt>
                                  <dd [class.record-mono]="field.mono">{{ field.value || '-' }}</dd>
                                </div>
                              }
                            </dl>
                          </section>
                        }
                      </div>
                    }
                  </ng-container>
                </nz-modal>
                <div style="margin-top: 16px;">
                  <button nz-button nzType="primary" (click)="resetScholarshipImport()">{{ 'DATA_IMPORT.IMPORT_ANOTHER' | translate }}</button>
                </div>
              </div>
            }
          </nz-card>

          <!--
            Quarterly base ETL. No upload: the workbook already arrived through
            the card above, and this moves what it landed into the staging
            table with the Arabic text resolved to lookup ids.
          -->
          <nz-card [nzTitle]="etlTitleTemplate" style="margin-top: 24px;">
            <ng-template #etlTitleTemplate>
              <span nz-icon nzType="database" class="mx-2"></span>{{ 'DATA_IMPORT.ETL_TITLE' | translate }}
            </ng-template>
            <nz-steps [nzCurrent]="etlStep" nzSize="small" style="margin-bottom: 24px;">
              <nz-step [nzTitle]="'DATA_IMPORT.ETL_STEP_SOURCE' | translate"></nz-step>
              <nz-step [nzTitle]="'DATA_IMPORT.ETL_STEP_MAPPING' | translate"></nz-step>
              <nz-step [nzTitle]="'DATA_IMPORT.ETL_STEP_RUN' | translate"></nz-step>
              <nz-step [nzTitle]="'DATA_IMPORT.VIEW_DATA' | translate"></nz-step>
            </nz-steps>

            <!-- Step 0: which country's landing rows to stage -->
            @if (etlStep === 0) {
              <div>
                <nz-alert nzType="info" [nzMessage]="'DATA_IMPORT.ETL_DESC' | translate" nzShowIcon
                  style="margin-bottom: 16px;"></nz-alert>

                <label class="scholarship-label">{{ 'DATA_IMPORT.ETL_COUNTRY' | translate }}</label>
                <nz-select style="width: 100%; margin-bottom: 16px;" [(ngModel)]="etlCountryId"
                  [nzPlaceHolder]="'DATA_IMPORT.SCHOLARSHIP_COUNTRY_PLACEHOLDER' | translate"
                  nzShowSearch (ngModelChange)="onEtlCountryChange()">
                  @for (country of countries; track country.id) {
                    <nz-option [nzValue]="country.id" [nzLabel]="countryLabel(country)"></nz-option>
                  }
                </nz-select>

                @if (etlCounts) {
                  <div class="etl-counts">
                    <div class="etl-count">
                      <span class="etl-count-label">{{ 'DATA_IMPORT.ETL_SOURCE_ROWS' | translate }}</span>
                      <span class="etl-count-value">{{ etlCounts.sourceRows }}</span>
                    </div>
                    <div class="etl-count">
                      <span class="etl-count-label">{{ 'DATA_IMPORT.ETL_STAGED_ROWS' | translate }}</span>
                      <span class="etl-count-value">{{ etlCounts.stagedRows }}</span>
                    </div>
                  </div>
                  @if (etlCounts.stagedRows > 0) {
                    <nz-alert nzType="warning" nzShowIcon style="margin-bottom: 16px;"
                      [nzMessage]="'DATA_IMPORT.ETL_WILL_REPLACE' | translate"></nz-alert>
                  }
                }

                <button nz-button nzType="primary" (click)="etlPreview()"
                  [nzLoading]="etlPreviewLoading" [disabled]="!etlCountryId || !etlCounts?.sourceRows">
                  <span nz-icon nzType="node-index"></span>
                  {{ 'DATA_IMPORT.ETL_PREVIEW_BTN' | translate }}
                </button>
                @if (etlCountryId && etlCounts && !etlCounts.sourceRows) {
                  <span class="scholarship-country-required">{{ 'DATA_IMPORT.ETL_NOTHING_TO_STAGE' | translate }}</span>
                }

                <nz-divider></nz-divider>
                <button nz-button nzType="default" (click)="goToViewStagedData()">
                  <span nz-icon nzType="table"></span> {{ 'DATA_IMPORT.ETL_VIEW_STAGED' | translate }}
                </button>
              </div>
            }

            <!-- Step 1: what the load would do -->
            @if (etlStep === 1 && etlReport) {
              <div>
                <div class="etl-counts">
                  <div class="etl-count">
                    <span class="etl-count-label">{{ 'DATA_IMPORT.ETL_WILL_STAGE' | translate }}</span>
                    <span class="etl-count-value">{{ etlReport.rowsStaged }}</span>
                  </div>
                  <div class="etl-count">
                    <span class="etl-count-label">{{ 'DATA_IMPORT.ETL_QUARTER' | translate }}</span>
                    <span class="etl-count-value">{{ etlReport.currentQuarter || '-' }}</span>
                  </div>
                  @if (etlReport.rowsPreserved) {
                    <div class="etl-count">
                      <span class="etl-count-label">{{ 'DATA_IMPORT.ETL_PRESERVED' | translate }}</span>
                      <span class="etl-count-value">{{ etlReport.rowsPreserved }}</span>
                    </div>
                  }
                  <div class="etl-count" [class.etl-count--bad]="etlReport.rowsRejected > 0">
                    <span class="etl-count-label">{{ 'DATA_IMPORT.ETL_REJECTED' | translate }}</span>
                    <span class="etl-count-value">{{ etlReport.rowsRejected }}</span>
                  </div>
                </div>

                <h4 class="etl-heading">{{ 'DATA_IMPORT.ETL_RESOLUTION' | translate }}</h4>
                <nz-table [nzData]="etlReport.resolution || []" nzSize="small" [nzShowPagination]="false">
                  <thead>
                    <tr>
                      <th>{{ 'DATA_IMPORT.ETL_LOOKUP' | translate }}</th>
                      <th nzWidth="120">{{ 'DATA_IMPORT.ETL_RESOLVED' | translate }}</th>
                      <th nzWidth="120">{{ 'DATA_IMPORT.ETL_UNRESOLVED' | translate }}</th>
                      <th nzWidth="140">{{ 'DATA_IMPORT.ETL_NOT_APPLICABLE' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (line of etlReport.resolution || []; track line.lookup) {
                      <tr>
                        <td>{{ ('DATA_IMPORT.ETL_LOOKUP_' + line.lookup) | translate }}</td>
                        <td>{{ line.resolved }}</td>
                        <td [class.etl-unresolved]="line.unresolved > 0">{{ line.unresolved }}</td>
                        <td class="etl-muted">{{ line.notApplicable || '-' }}</td>
                      </tr>
                    }
                  </tbody>
                </nz-table>

                @if (etlReport.unresolved?.length) {
                  <h4 class="etl-heading">{{ 'DATA_IMPORT.ETL_WORKLIST' | translate }}</h4>
                  <!-- Not an error: these rows still load, on غير مبين. This is
                       the list of spellings somebody has to rule on. -->
                  <nz-alert nzType="info" nzShowIcon style="margin-bottom: 12px;"
                    [nzMessage]="'DATA_IMPORT.ETL_WORKLIST_HINT' | translate"></nz-alert>
                  <nz-table [nzData]="etlReport.unresolved" nzSize="small" [nzPageSize]="10">
                    <thead>
                      <tr>
                        <th nzWidth="160">{{ 'DATA_IMPORT.ETL_LOOKUP' | translate }}</th>
                        <th>{{ 'DATA_IMPORT.ETL_VALUE' | translate }}</th>
                        <th nzWidth="100">{{ 'DATA_IMPORT.ETL_ROWS' | translate }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (line of etlReport.unresolved; track line.lookup + '|' + line.value) {
                        <tr>
                          <td>{{ ('DATA_IMPORT.ETL_LOOKUP_' + line.lookup) | translate }}</td>
                          <td>{{ line.value }}</td>
                          <td>{{ line.rows }}</td>
                        </tr>
                      }
                    </tbody>
                  </nz-table>
                }

                @if (etlReport.rejected?.length) {
                  <h4 class="etl-heading etl-heading--bad">{{ 'DATA_IMPORT.ETL_REJECTED_ROWS' | translate }}</h4>
                  <nz-table [nzData]="etlReport.rejected" nzSize="small" [nzPageSize]="10">
                    <thead>
                      <tr>
                        <th nzWidth="120">{{ 'DATA_IMPORT.ETL_SOURCE_ID' | translate }}</th>
                        <th>{{ 'DATA_IMPORT.ETL_REASON' | translate }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (line of etlReport.rejected; track line.sourceId) {
                        <tr>
                          <td>{{ line.sourceId }}</td>
                          <td>{{ line.reason }}</td>
                        </tr>
                      }
                    </tbody>
                  </nz-table>
                }

                <div style="margin-top: 16px;">
                  <button nz-button (click)="etlStep = 0">{{ 'DATA_IMPORT.BACK' | translate }}</button>
                  <button nz-button nzType="primary" (click)="confirmEtlRun()" [nzLoading]="etlRunLoading"
                    [disabled]="!etlReport.rowsStaged" style="margin-inline-start: 8px;">
                    <span nz-icon nzType="thunderbolt"></span>
                    {{ 'DATA_IMPORT.ETL_RUN_BTN' | translate }}
                  </button>
                </div>
              </div>
            }

            <!-- Step 2: what happened -->
            @if (etlStep === 2) {
              <div>
                @if (etlResult?.success) {
                  <nz-result nzStatus="success" [nzTitle]="'DATA_IMPORT.ETL_DONE' | translate"
                    [nzSubTitle]="etlSummary()">
                    <div nz-result-extra>
                      <button nz-button nzType="primary" (click)="goToViewStagedData()">
                        {{ 'DATA_IMPORT.ETL_VIEW_STAGED' | translate }}
                      </button>
                      <button nz-button (click)="resetEtl()" style="margin-inline-start: 8px;">
                        {{ 'DATA_IMPORT.ETL_RUN_AGAIN' | translate }}
                      </button>
                    </div>
                  </nz-result>
                }
                @if (etlResult && !etlResult.success) {
                  <nz-result nzStatus="error" [nzTitle]="'DATA_IMPORT.ETL_FAILED' | translate">
                    <div nz-result-content>
                      <nz-alert nzType="error" [nzMessage]="etlResult.message || ''" nzShowIcon></nz-alert>
                    </div>
                    <div nz-result-extra>
                      <button nz-button (click)="etlStep = 1">{{ 'DATA_IMPORT.BACK' | translate }}</button>
                    </div>
                  </nz-result>
                }
              </div>
            }

            <!-- Step 3: the staged rows, with the lookups shown as names -->
            @if (etlStep === 3) {
              <div>
                <div class="scholarship-toolbar">
                  <div class="scholarship-toolbar-field">
                    <label class="scholarship-label">{{ 'DATA_IMPORT.ETL_COUNTRY' | translate }}</label>
                    <nz-select style="width: 100%;" [(ngModel)]="etlViewCountryId"
                      [nzPlaceHolder]="'DATA_IMPORT.SCHOLARSHIP_ALL_COUNTRIES' | translate" nzShowSearch nzAllowClear
                      (ngModelChange)="onStagedCountryChange()">
                      @for (country of countries; track country.id) {
                        <nz-option [nzValue]="country.id" [nzLabel]="countryLabel(country)"></nz-option>
                      }
                    </nz-select>
                  </div>
                  <div class="scholarship-toolbar-field">
                    <label class="scholarship-label">{{ 'DATA_IMPORT.SCHOLARSHIP_SEARCH' | translate }}</label>
                    <nz-input-group [nzPrefix]="stagedSearchPrefix" [nzSuffix]="stagedSearchSuffix">
                      <input nz-input type="text" [(ngModel)]="stagedSearch"
                        [placeholder]="'DATA_IMPORT.ETL_SEARCH_HINT' | translate"
                        (ngModelChange)="stagedSearch$.next($event)" />
                    </nz-input-group>
                    <ng-template #stagedSearchPrefix><span nz-icon nzType="search"></span></ng-template>
                    <ng-template #stagedSearchSuffix>
                      @if (stagedSearch) {
                        <span nz-icon nzType="close-circle" nzTheme="fill" class="scholarship-search-clear"
                          (click)="clearStagedSearch()"></span>
                      }
                    </ng-template>
                  </div>
                  <div class="scholarship-toolbar-field" style="flex: 0 1 auto;">
                    <!-- The reason most people open this screen: the rows a
                         person still has to rule on. -->
                    <label nz-checkbox [(ngModel)]="stagedUnresolvedOnly"
                      (ngModelChange)="onStagedCriteriaChange()">
                      {{ 'DATA_IMPORT.ETL_UNRESOLVED_ONLY' | translate }}
                    </label>
                  </div>
                  <button nz-button (click)="resetStagedQuery()" [disabled]="!hasStagedQuery">
                    <span nz-icon nzType="clear"></span>
                    {{ 'DATA_IMPORT.SCHOLARSHIP_CLEAR_QUERY' | translate }}
                  </button>
                </div>

                <!-- The ids are shown beside the names on purpose: the point of
                     this screen is to check the resolution, and a name alone
                     does not say which lookup row it came from. -->
                <nz-table [nzData]="stagedData?.content || []" nzSize="small"
                  [nzLoading]="stagedLoading"
                  [nzPageIndex]="stagedPageIndex + 1" [nzPageSize]="stagedPageSize"
                  [nzTotal]="stagedData?.totalElements || 0" [nzFrontPagination]="false"
                  [nzNoResult]="stagedNoResult"
                  (nzQueryParams)="onStagedTableQuery($event)">
                  <thead>
                    <!-- nzSortFn and nzFilterFn are true, not functions: both run
                         in SQL. A function here would reorder the ten rows on
                         this page and call that a sort of eight hundred. -->
                    <tr>
                      <th nzWidth="110" nzColumnKey="STUDENT_ID" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('STUDENT_ID')"
                        [nzFilters]="stagedFilterLists['EDITED']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">Id</th>
                      <th nzColumnKey="STUDENT_NAME" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('STUDENT_NAME')">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_STUDENT_NAME' | translate }}</th>
                      <th nzWidth="150" nzColumnKey="DEGREE" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('DEGREE')"
                        [nzFilters]="stagedFilterLists['DEGREE']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_DEGREE' | translate }}</th>
                      <th nzWidth="180" nzColumnKey="MAJOR" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('MAJOR')">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_SPECIALIZATION' | translate }}</th>
                      <th nzWidth="130" nzColumnKey="LEVEL" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('LEVEL')"
                        [nzFilters]="stagedFilterLists['LEVEL']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_CURRENT_LEVEL' | translate }}</th>
                      <th nzWidth="140" nzColumnKey="AGENCY" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('AGENCY')"
                        [nzFilters]="stagedFilterLists['AGENCY']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.ETL_LOOKUP_AGENCY_TYPE' | translate }}</th>
                      <th nzWidth="110" nzColumnKey="AMOUNT" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('AMOUNT')">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_AMOUNT' | translate }}</th>
                      <th nzWidth="100" nzColumnKey="QUARTER" [nzSortFn]="true"
                        [nzSortOrder]="stagedSortOrderFor('QUARTER')"
                        [nzFilters]="stagedFilterLists['QUARTER']" [nzFilterFn]="true"
                        [nzFilterMultiple]="true">{{ 'DATA_IMPORT.SCHOLARSHIP_HDR_QUARTER' | translate }}</th>
                      <th nzWidth="180">{{ 'DATA_IMPORT.ACTIONS' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of stagedData?.content || []; track row.STUDENT_ID) {
                      <tr>
                        <td>
                          {{ row.STUDENT_ID }}
                          <!-- An edited row is outside the ETL's reach until the
                               flag is cleared, so it has to be visible here. -->
                          @if (row.IS_EDITED) {
                            <span class="record-flag">{{ 'DATA_IMPORT.ETL_EDITED' | translate }}</span>
                          }
                        </td>
                        <td>{{ row.STUDENT_NAME }}</td>
                        <td>{{ lookupCell(row.DEGREE_NAME, row.STUDENT_DEGREE_ID) }}</td>
                        <td>{{ lookupCell(row.MAJOR_NAME, row.STUDENT_MAJOR_ID) }}</td>
                        <td>{{ lookupCell(row.LEVEL_NAME, row.STUDENT_CURRENT_LEVEL_ID) }}</td>
                        <td>{{ lookupCell(row.AGENCY_NAME, row.STUDENT_AGENCY_TYPE_ID) }}</td>
                        <td>{{ row.STUDENT_AMOUNT }}</td>
                        <td>{{ row.CURRENT_QUARTER }}</td>
                        <td>
                          <button nz-button nzType="link" nzSize="small" (click)="openStagedDetail(row.STUDENT_ID)">
                            <span nz-icon nzType="eye"></span> {{ 'DATA_IMPORT.SCHOLARSHIP_VIEW_RECORD' | translate }}
                          </button>
                          <button nz-button nzType="link" nzSize="small" (click)="openStagedEdit(row.STUDENT_ID)">
                            <span nz-icon nzType="edit"></span> {{ 'DATA_IMPORT.ETL_EDIT' | translate }}
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </nz-table>
                <ng-template #stagedNoResult>
                  <nz-empty [nzNotFoundContent]="(hasStagedQuery
                    ? 'DATA_IMPORT.SCHOLARSHIP_NO_MATCHES'
                    : 'DATA_IMPORT.ETL_NO_STAGED') | translate"></nz-empty>
                </ng-template>

                <!-- The staged row in full. Every resolved lookup shows its name
                     and its id together: this dialog exists to check what the
                     ETL guessed, and neither half answers that on its own. -->
                <nz-modal [(nzVisible)]="stagedDetailVisible" nzWidth="900px" [nzFooter]="null"
                  [nzTitle]="('DATA_IMPORT.ETL_RECORD_TITLE' | translate) + ' #' + (stagedDetail?.['STUDENT_ID'] ?? '')"
                  (nzOnCancel)="closeStagedDetail()">
                  <ng-container *nzModalContent>
                    @if (stagedDetailLoading) {
                      <div style="text-align: center; padding: 32px;">
                        <nz-spin nzSimple></nz-spin>
                      </div>
                    }
                    @if (!stagedDetailLoading && stagedDetail) {
                      <div class="record" [dir]="isRtl ? 'rtl' : 'ltr'">

                        <header class="record-hero">
                          <div class="record-hero-main">
                            <span class="record-eyebrow">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_STUDENT_NAME' | translate }}</span>
                            <h3 class="record-name">
                              {{ stagedDetail['STUDENT_NAME'] || '-' }}
                              @if (stagedDetail['IS_EDITED']) {
                                <span class="record-flag">{{ 'DATA_IMPORT.ETL_EDITED' | translate }}</span>
                              }
                            </h3>
                            <div class="record-chips">
                              @for (chip of stagedChips(); track chip.label) {
                                <span class="record-chip">{{ chip.value }}</span>
                              }
                            </div>
                          </div>
                          <div class="record-amount">
                            <span class="record-amount-label">{{ 'DATA_IMPORT.SCHOLARSHIP_COL_AMOUNT' | translate }}</span>
                            <span class="record-amount-value">{{ stagedAmount() }}</span>
                            <button nz-button nzSize="small" style="margin-top: 8px;"
                              (click)="openStagedEdit(stagedDetail['STUDENT_ID'])">
                              <span nz-icon nzType="edit"></span> {{ 'DATA_IMPORT.ETL_EDIT' | translate }}
                            </button>
                          </div>
                        </header>

                        <div class="record-stats">
                          @for (stat of stagedStats(); track stat.label) {
                            <div class="record-stat">
                              <span class="record-stat-label">{{ stat.label | translate }}</span>
                              <span class="record-stat-value">{{ stat.value || '-' }}</span>
                            </div>
                          }
                        </div>

                        @if (stagedNotes().length) {
                          <section class="record-section">
                            <h4 class="record-section-title">{{ 'DATA_IMPORT.SCHOLARSHIP_SECTION_NOTES' | translate }}</h4>
                            @for (note of stagedNotes(); track note.label) {
                              <div class="record-note">
                                <span class="record-note-label">{{ note.label | translate }}</span>
                                <p class="record-note-text">{{ note.value }}</p>
                              </div>
                            }
                          </section>
                        }

                        @for (section of stagedSections(); track section.title) {
                          <section class="record-section">
                            <h4 class="record-section-title">{{ section.title | translate }}</h4>
                            <dl class="record-list">
                              @for (field of section.fields; track field.label) {
                                <div class="record-row" [class.record-row--wide]="field.wide">
                                  <dt>{{ field.label | translate }}</dt>
                                  <dd>
                                    {{ field.value || '-' }}
                                    <!-- Flagged rather than hidden: a row on غير مبين
                                         loaded fine but nobody has ruled on it yet. -->
                                    @if (field.unresolved) {
                                      <span class="record-flag">{{ 'DATA_IMPORT.ETL_UNRESOLVED' | translate }}</span>
                                    }
                                  </dd>
                                </div>
                              }
                            </dl>
                          </section>
                        }
                      </div>
                    }
                  </ng-container>
                </nz-modal>

                <!-- Correcting a staged row by hand. The lookups are dropdowns
                     because the whole point is to replace a resolver's guess
                     with a choice from the list it was guessing at. -->
                <nz-modal [(nzVisible)]="etlEditVisible" nzWidth="960px"
                  [nzTitle]="('DATA_IMPORT.ETL_EDIT_TITLE' | translate) + ' #' + (etlEditId ?? '')"
                  [nzOkText]="'DATA_IMPORT.ETL_SAVE' | translate"
                  [nzOkLoading]="etlEditSaving" [nzOkDisabled]="!etlEditDirty()"
                  (nzOnOk)="saveStagedEdit()" (nzOnCancel)="closeStagedEdit()">
                  <ng-container *nzModalContent>
                    @if (etlEditLoading) {
                      <div style="text-align: center; padding: 32px;">
                        <nz-spin nzSimple></nz-spin>
                      </div>
                    }
                    @if (!etlEditLoading && etlEditId) {
                      <div class="record" [dir]="isRtl ? 'rtl' : 'ltr'">
                        <nz-alert nzType="warning" nzShowIcon style="margin-bottom: 16px;"
                          [nzMessage]="'DATA_IMPORT.ETL_EDIT_WARNING' | translate"></nz-alert>

                        <!-- Without this an unreachable server looks exactly like
                             a lookup table with nothing in it, and the dropdowns
                             sit there empty with no reason given. -->
                        @if (etlLookupsFailed) {
                          <nz-alert nzType="error" nzShowIcon style="margin-bottom: 16px;"
                            [nzMessage]="'DATA_IMPORT.ETL_LOOKUPS_FAILED' | translate"
                            [nzDescription]="lookupsRetry">
                          </nz-alert>
                          <ng-template #lookupsRetry>
                            <p style="margin: 0 0 8px;">{{ 'DATA_IMPORT.ETL_LOOKUPS_FAILED_HINT' | translate }}</p>
                            <button nz-button nzSize="small" (click)="retryEtlLookups()"
                              [nzLoading]="etlLookupsLoading">
                              <span nz-icon nzType="reload"></span> {{ 'DATA_IMPORT.ETL_RETRY' | translate }}
                            </button>
                          </ng-template>
                        }

                        @for (section of etlEditSections; track section.title) {
                          <section class="record-section">
                            <h4 class="record-section-title">{{ section.title | translate }}</h4>
                            <div class="edit-grid">
                              @for (field of section.fields; track field.key) {
                                <div class="edit-field" [class.edit-field--wide]="field.wide">
                                  <label class="edit-label">
                                    {{ field.label | translate }}
                                    @if (isFieldChanged(field.key)) {
                                      <span class="edit-changed">{{ 'DATA_IMPORT.ETL_CHANGED' | translate }}</span>
                                    }
                                  </label>

                                  @if (field.type === 'lookup') {
                                    <nz-select style="width: 100%;" nzShowSearch nzAllowClear
                                      [(ngModel)]="etlEditForm[field.key]"
                                      [nzLoading]="etlLookupsLoading"
                                      [nzPlaceHolder]="(hasLookupOptions(field.key)
                                        ? 'DATA_IMPORT.ETL_NOT_SET'
                                        : 'DATA_IMPORT.ETL_NO_OPTIONS') | translate">
                                      @for (option of etlLookups[field.key] || []; track option.id) {
                                        <nz-option [nzValue]="option.id"
                                          [nzLabel]="option.name + ' (' + option.id + ')'"></nz-option>
                                      }
                                      <!-- The row already holds an id the list does
                                           not offer: keep it selectable rather than
                                           blanking the field the moment it opens. -->
                                      @if (etlEditForm[field.key] && !isKnownOption(field.key, etlEditForm[field.key])) {
                                        <nz-option [nzValue]="etlEditForm[field.key]"
                                          [nzLabel]="etlEditForm[field.key] + ''"></nz-option>
                                      }
                                    </nz-select>
                                  } @else if (field.type === 'textarea') {
                                    <textarea nz-input rows="2" [(ngModel)]="etlEditForm[field.key]"></textarea>
                                  } @else {
                                    <input nz-input [type]="field.type === 'number' ? 'number' : 'text'"
                                      [(ngModel)]="etlEditForm[field.key]" />
                                  }
                                </div>
                              }
                            </div>
                          </section>
                        }

                        @if (etlEditWasEdited) {
                          <section class="record-section">
                            <h4 class="record-section-title">{{ 'DATA_IMPORT.ETL_SECTION_ETL_CONTROL' | translate }}</h4>
                            <p class="edit-hint">{{ 'DATA_IMPORT.ETL_CLEAR_HINT' | translate }}</p>
                            <button nz-button nzDanger nzSize="small" (click)="clearStagedEdit()"
                              [nzLoading]="etlEditSaving">
                              <span nz-icon nzType="undo"></span> {{ 'DATA_IMPORT.ETL_CLEAR_EDIT' | translate }}
                            </button>
                          </section>
                        }
                      </div>
                    }
                  </ng-container>
                </nz-modal>

                <div style="margin-top: 16px;">
                  <button nz-button nzType="primary" (click)="resetEtl()">
                    {{ 'DATA_IMPORT.ETL_RUN_AGAIN' | translate }}
                  </button>
                </div>
              </div>
            }
          </nz-card>
        </div>
        `,
    styles: [`
        .import-container {
            padding: 24px;
        }
        :host ::ng-deep .ant-card-head {
            background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
            border-radius: 8px 8px 0 0;
        }
        :host ::ng-deep .ant-card-head-title {
            color: white;
            font-weight: 600;
        }
        :host ::ng-deep .ant-upload-drag {
            border: 2px dashed #d9d9d9;
            border-radius: 8px;
            background: #fafafa;
        }
        :host ::ng-deep .ant-upload-drag:hover {
            border-color: #11998e;
        }
        .row-data-form {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
            gap: 12px 24px;
        }
        .row-data-field {
            display: flex;
            flex-direction: column;
            gap: 2px;
        }
        .row-data-label {
            font-size: 11px;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .row-data-value {
            font-size: 13px;
            word-break: break-word;
        }
        .scholarship-label {
            display: block;
            margin-bottom: 4px;
            font-weight: 600;
        }
        /* The letterhead read off the sheet, shown as a block of facts so the
           uploader can confirm the quarter before committing 800+ rows. */
        .scholarship-header-block {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
            gap: 12px 24px;
            padding: 16px;
            margin-bottom: 16px;
            background: #fafafa;
            border-radius: 8px;
        }
        /* ── The imported-record dialog ────────────────────────────────────
           Laid out with logical properties throughout: this is the one place
           in the app that renders outside the layout wrapper, so it has to
           mirror on its own dir attribute rather than on a .rtl ancestor. */
        .record {
            --record-accent: #11998e;
            font-size: 13px;
        }
        .record-hero {
            display: flex;
            flex-wrap: wrap;
            gap: 16px;
            align-items: flex-start;
            justify-content: space-between;
            padding: 18px 20px;
            border-radius: 10px;
            background: linear-gradient(135deg, rgba(17, 153, 142, .08), rgba(56, 239, 125, .10));
            border: 1px solid rgba(17, 153, 142, .18);
        }
        .record-hero-main {
            min-width: 0;
        }
        .record-eyebrow {
            display: block;
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: .06em;
            color: #6b7280;
        }
        .record-name {
            margin: 2px 0 0;
            font-size: 19px;
            font-weight: 700;
            line-height: 1.35;
            word-break: break-word;
        }
        .record-chips {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            margin-top: 10px;
        }
        .record-chip {
            padding: 2px 10px;
            border-radius: 999px;
            background: #fff;
            border: 1px solid rgba(17, 153, 142, .28);
            color: var(--record-accent);
            font-size: 12px;
            white-space: nowrap;
        }
        .record-amount {
            display: flex;
            flex-direction: column;
            align-items: flex-end;
            text-align: end;
        }
        .record-amount-label {
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: .06em;
            color: #6b7280;
        }
        .record-amount-value {
            font-size: 24px;
            font-weight: 700;
            color: var(--record-accent);
            /* The figure stays left-to-right in Arabic; a currency amount is
               not mirrored text. */
            direction: ltr;
            unicode-bidi: isolate;
            font-variant-numeric: tabular-nums;
        }
        .record-stats {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
            gap: 10px;
            margin-top: 16px;
        }
        .record-stat {
            padding: 10px 12px;
            border: 1px solid #f0f0f0;
            border-radius: 8px;
            background: #fafafa;
        }
        .record-stat-label {
            display: block;
            font-size: 11px;
            color: #6b7280;
            margin-bottom: 2px;
        }
        .record-stat-value {
            font-size: 15px;
            font-weight: 600;
            word-break: break-word;
        }
        .record-section {
            margin-top: 22px;
        }
        .record-section-title {
            margin: 0 0 10px;
            padding-inline-start: 10px;
            border-inline-start: 3px solid var(--record-accent);
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: .05em;
            color: #374151;
        }
        .record-list {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
            gap: 0 24px;
            margin: 0;
        }
        .record-row {
            display: grid;
            grid-template-columns: minmax(0, 1fr);
            padding: 7px 0;
            border-bottom: 1px solid #f5f5f5;
        }
        .record-row--wide {
            grid-column: 1 / -1;
        }
        .record-row dt {
            font-size: 11px;
            color: #6b7280;
            margin-bottom: 1px;
        }
        .record-row dd {
            margin: 0;
            word-break: break-word;
        }
        .record-mono {
            font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
            font-size: 12px;
            direction: ltr;
            unicode-bidi: isolate;
        }
        .record-note {
            padding: 10px 12px;
            border-radius: 8px;
            background: #fafafa;
            border-inline-start: 3px solid #e5e7eb;
            margin-bottom: 8px;
        }
        .record-note-label {
            display: block;
            font-size: 11px;
            color: #6b7280;
            margin-bottom: 3px;
        }
        .record-note-text {
            margin: 0;
            line-height: 1.7;
            white-space: pre-line;
        }
        /* Country, search and reset on one line, stacking on a narrow screen. */
        .scholarship-toolbar {
            display: flex;
            flex-wrap: wrap;
            align-items: flex-end;
            gap: 12px;
            margin-bottom: 16px;
        }
        .scholarship-toolbar-field {
            flex: 1 1 240px;
            min-width: 200px;
        }
        .scholarship-search-clear {
            cursor: pointer;
            color: rgba(0, 0, 0, .25);
        }
        .scholarship-search-clear:hover {
            color: rgba(0, 0, 0, .45);
        }
        .scholarship-delete-options {
            display: grid;
            grid-template-columns: auto;
            gap: 4px;
            margin-top: 16px;
            padding: 12px 16px;
            border: 1px solid var(--surface-border, #f0f0f0);
            border-radius: 8px;
        }
        .scholarship-delete-hint {
            font-size: 12px;
            color: #666;
            margin: 0 0 8px 24px;
        }
        .scholarship-delete-danger {
            color: #cf1322;
            font-weight: 600;
        }
        /* Marks a field sitting on غير مبين. Not an error state — the row
           loaded — so it is a quiet note rather than a red one. */
        .record-flag {
            display: inline-block;
            margin-inline-start: 8px;
            padding: 1px 8px;
            border-radius: 10px;
            background: #fff7e6;
            color: #d46b08;
            font-size: 11px;
            font-weight: 600;
            white-space: nowrap;
        }
        .edit-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 12px 16px;
        }
        .edit-field--wide {
            grid-column: 1 / -1;
        }
        .edit-label {
            display: block;
            margin-bottom: 4px;
            font-size: 12px;
            color: rgba(0, 0, 0, .55);
        }
        .edit-changed {
            margin-inline-start: 6px;
            padding: 0 6px;
            border-radius: 8px;
            background: #e6f4ff;
            color: #0958d9;
            font-size: 10px;
            font-weight: 600;
        }
        .edit-hint {
            margin: 0 0 10px;
            color: rgba(0, 0, 0, .55);
            font-size: 13px;
            line-height: 1.6;
        }
        .etl-counts {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            margin-bottom: 16px;
        }
        .etl-count {
            flex: 1 1 140px;
            padding: 12px 14px;
            border: 1px solid #f0f0f0;
            border-radius: 8px;
            background: #fafafa;
        }
        .etl-count--bad {
            border-color: #ffccc7;
            background: #fff2f0;
        }
        .etl-count-label {
            display: block;
            font-size: 12px;
            color: rgba(0, 0, 0, .45);
            margin-bottom: 4px;
        }
        .etl-count-value {
            font-size: 20px;
            font-weight: 700;
            font-variant-numeric: tabular-nums;
        }
        .etl-heading {
            margin: 20px 0 8px;
            font-size: 13px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: .03em;
        }
        .etl-heading--bad {
            color: #cf1322;
        }
        .etl-unresolved {
            color: #d46b08;
            font-weight: 600;
        }
        .etl-muted {
            color: rgba(0, 0, 0, .45);
        }
        .scholarship-country-required {
            margin-inline-start: 12px;
            color: #cf1322;
            font-size: 12px;
        }
    `]
})
export class DataImportComponent implements OnInit, OnDestroy {
    /*
     * Quarterly base ETL: STUDENT_SCHOLARSHIP_DATA_IMPORT -> STUDENT_DATA_STAGING.
     *
     * There is no file here. The workbook was uploaded through the scholarship
     * card above and already sits in the landing table; this step resolves its
     * Arabic text to lookup ids and writes rows shaped like the student table.
     */
    etlStep = 0;
    /** Whose landing rows to stage. */
    etlCountryId: number | null = null;
    /** Which country's staged rows to look at, which is a separate question. */
    etlViewCountryId: number | null = null;
    etlCounts: { sourceRows: number; stagedRows: number; editedRows?: number; stagedTotal: number } | null = null;
    etlReport: EtlReport | null = null;
    etlResult: { success: boolean; message?: string } | null = null;
    etlPreviewLoading = false;
    etlRunLoading = false;

    /*
     * Editing a staged row.
     *
     * The form holds only the editable columns; the server holds the whitelist
     * of what those are, and a field it does not recognise is dropped there
     * rather than trusted here. Only what actually changed is sent.
     */
    etlEditVisible = false;
    etlEditLoading = false;
    etlEditSaving = false;
    etlEditId: number | null = null;
    etlEditWasEdited = false;
    etlEditForm: Record<string, any> = {};
    private etlEditOriginal: Record<string, any> = {};
    /**
     * Dropdown options by column, read from the lookup tables the foreign keys
     * point at — STUDENT_DEGREE, STUDENT_MAJOR and the rest. Fetched once per
     * session; about 1,400 options across seventeen tables.
     */
    etlLookups: Record<string, { id: number; name: string }[]> = {};
    etlLookupsLoading = false;
    /** Set when the fetch failed, so empty dropdowns can explain themselves. */
    etlLookupsFailed = false;

    /** The staged record dialog: one row, every column, fetched on demand. */
    stagedDetail: Record<string, any> | null = null;
    stagedDetailVisible = false;
    stagedDetailLoading = false;

    stagedData: { content: any[]; totalElements: number } | null = null;
    stagedLoading = false;
    stagedPageIndex = 0;
    stagedPageSize = 10;

    /*
     * Sorting, filtering and search on the staged table, all server-side for
     * the same reason the import table is: this page holds ten rows out of
     * hundreds, and sorting what is on screen is not sorting the data.
     */
    stagedSortBy: string | null = null;
    stagedSortDir: NzTableSortOrder = null;
    stagedSearch = '';
    stagedFilters: Record<string, string[]> = {};
    /** Rows where a lookup landed on غير مبين — the human worklist. */
    stagedUnresolvedOnly = false;

    /** Held as a field, not built by a method: see scholarshipFilterLists. */
    stagedFilterLists: Record<string, NzTableFilterList> = {
        DEGREE: [], LEVEL: [], AGENCY: [], QUARTER: [], EDITED: []
    };
    private stagedFilterOptions: Record<string, { id: any; name: string }[]> = {};

    readonly stagedSearch$ = new Subject<string>();
    /** The last staged query actually sent, so echoes cost nothing. */
    private stagedQuerySent = '';

    /*
     * Scholarship import. The grid columns are listed here rather than read off
     * the file because this importer targets one fixed table: the server returns
     * rows keyed by database column, and these are the keys, in the order the
     * form lays them out.
     */
    readonly scholarshipGridColumns = [
        'STUDENT_NAME', 'ENTITY', 'COUNTRY', 'SCHOLARSHIP_YEAR', 'DEGREE', 'SPECIALIZATION',
        'DECISION_NO', 'STUDY_DURATION', 'CURRENT_LEVEL', 'FIRST_DISBURSEMENT', 'AMOUNT',
        'STUDENT_SIGNATURE', 'NOTES', 'FINANCIAL_NOTES'
    ];

    /**
     * A width for every grid column.
     *
     * <p>Not decoration: nzScroll.x makes ng-zorro render the header and the body
     * as two separate tables with `table-layout: fixed`. A column with no width
     * is sized independently in each of them, so the headers drift out of line
     * with the cells underneath. Every column needs one, and nzScroll.x needs to
     * be their sum or the last column is clipped.
     */
    private static readonly COLUMN_WIDTHS: Record<string, number> = {
        STUDENT_NAME: 220, ENTITY: 120, COUNTRY: 90, SCHOLARSHIP_YEAR: 110,
        DEGREE: 120, SPECIALIZATION: 160, DECISION_NO: 110, STUDY_DURATION: 110,
        CURRENT_LEVEL: 110, FIRST_DISBURSEMENT: 130, AMOUNT: 110,
        STUDENT_SIGNATURE: 140, NOTES: 240, FINANCIAL_NOTES: 160
    };

    countries: StudyCountryIn[] = [];
    scholarshipStep = 0;
    scholarshipFile: File | null = null;
    /** Which country folder the workbook is filed under. Required before importing. */
    scholarshipCountryId: number | null = null;
    scholarshipFilterCountryId: number | null = null;
    scholarshipPreviewData: ScholarshipPreview | null = null;
    scholarshipResult: {
        success: boolean; filename?: string; rowsImported?: number; skippedRows?: number;
        storedFile?: string; rowsDeleted?: number; message?: string;
    } | null = null;
    isScholarshipPreviewLoading = false;
    isScholarshipImporting = false;
    scholarshipTableData: { content: any[]; totalElements: number } | null = null;
    scholarshipTableLoading = false;
    scholarshipPageIndex = 0;
    scholarshipPageSize = 10;

    /*
     * Sorting, filtering and search all run on the server. They have to: the
     * table holds one page of ten rows out of several thousand, so anything
     * done here would sort and filter the ten rows on screen and quietly
     * ignore the rest.
     */
    scholarshipSortBy: string | null = null;
    scholarshipSortDir: NzTableSortOrder = null;
    scholarshipSearch = '';
    /** Column key to the values ticked in its dropdown. */
    scholarshipFilters: Record<string, string[]> = {};

    /**
     * The dropdown contents, held as fields rather than built by a method.
     *
     * <p>ng-zorro rebuilds a column filter whenever the array it was given is a
     * different object, and re-emits the query. A method called from the
     * template returns a new array on every change-detection pass, which would
     * make that loop forever.
     */
    scholarshipFilterLists: Record<string, NzTableFilterList> = {
        COUNTRY: [], DEGREE: [], HDR_QUARTER: [], HDR_YEAR: []
    };

    /** What came back from /scholarship-data/filters, before it is made into lists. */
    private scholarshipFilterOptions: Record<string, string[]> = {};

    /** Keystrokes, before the debounce turns them into one request. */
    readonly scholarshipSearch$ = new Subject<string>();
    private readonly destroy$ = new Subject<void>();

    /**
     * The last query actually sent.
     *
     * <p>The table re-emits nzQueryParams for changes we made ourselves — the
     * page reset that follows a filter, the filter state restored when the
     * dropdown contents reload. Those are echoes, not new questions, and
     * without this each one would cost a round trip.
     */
    private scholarshipQuerySent = '';

    /** Clear this country's rows before writing the new batch. */
    scholarshipDeleteCountry = false;
    /** Clear the whole table, every country, before writing the new batch. */
    scholarshipDeleteAll = false;
    /** Row counts behind the confirmation, refreshed when the preview is taken. */
    scholarshipCounts: { countryRows: number; totalRows: number } | null = null;

    /** The record dialog: one row, every field, fetched on demand. */
    scholarshipDetail: Record<string, any> | null = null;
    scholarshipDetailVisible = false;
    scholarshipDetailLoading = false;

    private apiUrl = `${environment.apiUrl}/mohe/api/v1/import`;

    constructor(
        private http: HttpClient,
        private message: ToastService,
        private translationService: TranslationService,
        private lookupService: LookupService,
        private modal: NzModalService
    ) { }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    ngOnInit(): void {
        // 350ms: long enough that typing a name is one request rather than
        // fifteen, short enough that it still feels like it is keeping up.
        this.scholarshipSearch$
            .pipe(debounceTime(350), distinctUntilChanged(), takeUntil(this.destroy$))
            .subscribe(() => {
                this.scholarshipPageIndex = 0;
                this.loadScholarshipData();
            });

        this.stagedSearch$
            .pipe(debounceTime(350), distinctUntilChanged(), takeUntil(this.destroy$))
            .subscribe(() => {
                this.stagedPageIndex = 0;
                this.loadStagedData();
            });

        this.loadCountries();
    }

    // ── Quarterly base ETL ───────────────────────────────────────────────────

    onEtlCountryChange(): void {
        this.etlReport = null;
        this.etlCounts = null;
        if (!this.etlCountryId) {
            return;
        }
        this.http.get<any>(`${this.apiUrl}/etl/staging/counts`, {
            params: { countryId: String(this.etlCountryId) }
        }).subscribe({
            next: (data) => { this.etlCounts = data; },
            error: () => { this.etlCounts = null; }
        });
    }

    /** Asks the server what the load would do. Writes nothing. */
    etlPreview(): void {
        if (!this.etlCountryId) {
            return;
        }
        this.etlPreviewLoading = true;
        this.http.post<EtlReport>(`${this.apiUrl}/etl/staging/preview`, null, {
            params: { countryId: String(this.etlCountryId) }
        }).subscribe({
            next: (report) => {
                this.etlReport = report;
                this.etlStep = 1;
                this.etlPreviewLoading = false;
            },
            error: (err) => {
                this.etlPreviewLoading = false;
                this.message.error(err.error?.message
                    || this.translationService.translate('DATA_IMPORT.ETL_FAILED'));
            }
        });
    }

    /**
     * Confirms before running, because a run replaces rather than appends.
     *
     * <p>Staging is derived data and the landing table it comes from is
     * untouched, so nothing here is unrecoverable — but somebody looking at
     * the old staged rows should be told they are about to be replaced.
     */
    confirmEtlRun(): void {
        const staged = this.etlCounts?.stagedRows ?? 0;
        if (staged === 0) {
            this.runEtl();
            return;
        }
        this.modal.confirm({
            nzTitle: this.translationService.translate('DATA_IMPORT.ETL_RUN_CONFIRM_TITLE'),
            nzContent: this.translationService.translate('DATA_IMPORT.ETL_RUN_CONFIRM_BODY')
                .replace('{staged}', String(staged))
                .replace('{incoming}', String(this.etlReport?.rowsStaged ?? 0)),
            nzOkText: this.translationService.translate('DATA_IMPORT.ETL_RUN_BTN'),
            nzOkDanger: true,
            nzOnOk: () => this.runEtl()
        });
    }

    private runEtl(): void {
        if (!this.etlCountryId) {
            return;
        }
        this.etlRunLoading = true;
        this.http.post<EtlReport>(`${this.apiUrl}/etl/staging/run`, null, {
            params: { countryId: String(this.etlCountryId) }
        }).subscribe({
            next: (report) => {
                this.etlReport = report;
                this.etlResult = { success: true };
                this.etlStep = 2;
                this.etlRunLoading = false;
                this.message.success(this.translationService.translate('DATA_IMPORT.ETL_DONE'));
            },
            error: (err) => {
                this.etlResult = {
                    success: false,
                    message: err.error?.message
                        || this.translationService.translate('DATA_IMPORT.ETL_FAILED')
                };
                this.etlStep = 2;
                this.etlRunLoading = false;
            }
        });
    }

    /** The one-line summary under the success result. */
    etlSummary(): string {
        const t = (key: string) => this.translationService.translate(key);
        const report = this.etlReport;
        let summary = `${t('DATA_IMPORT.ETL_WILL_STAGE')}: ${report?.rowsStaged ?? 0}`
            + ` \u00b7 ${t('DATA_IMPORT.ETL_REPLACED')}: ${report?.rowsReplaced ?? 0}`;
        // Only when it happened: "preserved: 0" on every run is noise, but a run
        // that skipped rows must not read the same as one that staged them.
        if (report?.rowsPreserved) {
            summary += ` \u00b7 ${t('DATA_IMPORT.ETL_PRESERVED')}: ${report.rowsPreserved}`;
        }
        return summary + ` \u00b7 ${t('DATA_IMPORT.ETL_QUARTER')}: ${report?.currentQuarter ?? '-'}`;
    }

    goToViewStagedData(): void {
        this.etlStep = 3;
        this.stagedPageIndex = 0;
        this.etlViewCountryId = this.etlViewCountryId ?? this.etlCountryId;
        this.loadStagedFilterOptions();
        // Forced: a run just changed the rows, so the same question has a
        // different answer.
        this.loadStagedData(true);
    }

    /** A different country is a different set of rows, so its filters go too. */
    onStagedCountryChange(): void {
        this.stagedFilters = {};
        this.stagedPageIndex = 0;
        this.loadStagedFilterOptions();
        this.loadStagedData();
    }

    /** Something outside the table narrowed the list. */
    onStagedCriteriaChange(): void {
        this.stagedPageIndex = 0;
        this.loadStagedData();
    }

    stagedSortOrderFor(column: string): NzTableSortOrder {
        return this.stagedSortBy === column ? this.stagedSortDir : null;
    }

    get hasStagedQuery(): boolean {
        return !!this.stagedSearch.trim()
            || !!this.stagedSortBy
            || this.stagedUnresolvedOnly
            || Object.values(this.stagedFilters).some(values => values.length > 0);
    }

    clearStagedSearch(): void {
        this.stagedSearch = '';
        this.stagedSearch$.next('');
    }

    /** Back to the plain list. The country is the scope, so it is left alone. */
    resetStagedQuery(): void {
        this.stagedSearch = '';
        this.stagedSearch$.next('');
        this.stagedSortBy = null;
        this.stagedSortDir = null;
        this.stagedFilters = {};
        this.stagedUnresolvedOnly = false;
        this.stagedPageIndex = 0;
        this.rebuildStagedFilterLists();
        this.loadStagedData();
    }

    /** Page, size, every sort and every filter, in one event. */
    onStagedTableQuery(params: NzTableQueryParams): void {
        const before = this.stagedCriteria();

        this.stagedPageIndex = Math.max(0, (params.pageIndex || 1) - 1);
        this.stagedPageSize = params.pageSize || this.stagedPageSize;

        const sorted = (params.sort || []).find(item => !!item.value);
        this.stagedSortBy = sorted ? sorted.key : null;
        this.stagedSortDir = sorted ? sorted.value : null;

        const filters: Record<string, string[]> = {};
        for (const item of params.filter || []) {
            const values = (Array.isArray(item.value) ? item.value : [item.value])
                .filter(value => value !== null && value !== undefined && value !== '')
                .map(value => String(value));
            if (values.length) {
                filters[item.key] = values;
            }
        }
        this.stagedFilters = filters;

        // Narrowing the list sends you back to the start of it: page 7 of the
        // old list is usually not a page of the new one at all.
        if (this.stagedCriteria() !== before) {
            this.stagedPageIndex = 0;
        }

        this.loadStagedData();
    }

    private stagedCriteria(): string {
        return JSON.stringify([
            this.stagedSortBy,
            this.stagedSortDir,
            this.stagedSearch.trim(),
            this.stagedUnresolvedOnly,
            Object.keys(this.stagedFilters).sort()
                .map(key => [key, [...this.stagedFilters[key]].sort()])
        ]);
    }

    private stagedQueryParams(): HttpParams {
        let params = new HttpParams()
            .set('page', String(this.stagedPageIndex))
            .set('size', String(this.stagedPageSize));
        if (this.etlViewCountryId) {
            params = params.set('countryId', String(this.etlViewCountryId));
        }
        if (this.stagedSortBy && this.stagedSortDir) {
            params = params.set('sortBy', this.stagedSortBy).set('sortDir', this.stagedSortDir);
        }
        const search = this.stagedSearch.trim();
        if (search) {
            params = params.set('search', search);
        }
        if (this.stagedUnresolvedOnly) {
            params = params.set('unresolvedOnly', 'true');
        }
        for (const key of Object.keys(this.stagedFilters).sort()) {
            for (const value of this.stagedFilters[key]) {
                params = params.append('filter.' + key, value);
            }
        }
        return params;
    }

    private loadStagedData(force = false): void {
        const params = this.stagedQueryParams();
        const asked = params.toString();
        if (!force && asked === this.stagedQuerySent) {
            return;
        }
        this.stagedQuerySent = asked;

        this.stagedLoading = true;
        this.http.get<any>(`${this.apiUrl}/etl/staging/data`, { params }).subscribe({
            next: (data) => {
                this.stagedData = data;
                this.stagedLoading = false;
            },
            error: () => {
                this.stagedData = { content: [], totalElements: 0 };
                this.stagedLoading = false;
                // A failed request is not an answer; let the next identical
                // query try again.
                this.stagedQuerySent = '';
            }
        });
    }

    /** The values each dropdown offers, read off the staged rows themselves. */
    private loadStagedFilterOptions(): void {
        const params = this.etlViewCountryId
            ? new HttpParams().set('countryId', String(this.etlViewCountryId))
            : new HttpParams();
        this.http.get<Record<string, { id: any; name: string }[]>>(
            `${this.apiUrl}/etl/staging/filters`, { params }).subscribe({
            next: (options) => {
                this.stagedFilterOptions = options || {};
                this.rebuildStagedFilterLists();
            },
            error: () => {
                this.stagedFilterOptions = {};
                this.rebuildStagedFilterLists();
            }
        });
    }

    private rebuildStagedFilterLists(): void {
        const lists: Record<string, NzTableFilterList> = {};
        for (const key of Object.keys(this.stagedFilterLists)) {
            const chosen = this.stagedFilters[key] || [];
            if (key === 'EDITED') {
                // Not a lookup: two fixed states, and the label is what makes
                // the distinction legible rather than "1" and "0".
                lists[key] = [
                    { text: this.translationService.translate('DATA_IMPORT.ETL_EDITED_YES'), value: '1',
                      byDefault: chosen.includes('1') },
                    { text: this.translationService.translate('DATA_IMPORT.ETL_EDITED_NO'), value: '0',
                      byDefault: chosen.includes('0') }
                ];
                continue;
            }
            lists[key] = (this.stagedFilterOptions[key] || []).map(option => ({
                text: option.name ? `${option.name} (${option.id})` : String(option.id),
                value: String(option.id),
                byDefault: chosen.includes(String(option.id))
            }));
        }
        this.stagedFilterLists = lists;
    }

    /**
     * A resolved lookup, shown as "name (id)".
     *
     * <p>The id is there because this screen exists to check the resolution.
     * Two lookup rows can carry the same name — السنة الأولى is both 1 and 100
     * — and the name on its own cannot tell you which one the load picked.
     */
    lookupCell(name: string | null | undefined, id: number | null | undefined): string {
        if (id === null || id === undefined) {
            return '-';
        }
        return name ? `${name} (${id})` : String(id);
    }

    openStagedDetail(id: number): void {
        this.stagedDetailVisible = true;
        this.stagedDetailLoading = true;
        this.stagedDetail = null;
        this.http.get<Record<string, any>>(`${this.apiUrl}/etl/staging/${id}`).subscribe({
            next: (row) => {
                this.stagedDetail = row;
                this.stagedDetailLoading = false;
            },
            error: (err) => {
                this.stagedDetailLoading = false;
                this.stagedDetailVisible = false;
                this.message.error(err.error?.message
                    || this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_DETAIL_FAILED'));
            }
        });
    }

    closeStagedDetail(): void {
        this.stagedDetailVisible = false;
        this.stagedDetail = null;
    }

    private stagedValue(key: string): string {
        const value = this.stagedDetail?.[key];
        return value === null || value === undefined ? '' : String(value);
    }

    /**
     * A resolved lookup as the dialog shows it: "name (id)".
     *
     * <p>Both halves, for the reason {@link lookupCell} gives — except here the
     * caller also learns whether the id is the lookup's غير مبين row, so the
     * field can be flagged as still needing a human decision.
     */
    private stagedLookup(label: string, idKey: string, nameKey: string, wide = false):
        { label: string; value: string; wide: boolean; unresolved: boolean } {
        const id = this.stagedDetail?.[idKey];
        const name = this.stagedValue(nameKey);
        const missing = id === null || id === undefined;
        return {
            label,
            value: missing ? '' : (name ? `${name} (${id})` : String(id)),
            wide,
            // 99 and 999 are both "غير مبين", in different lookups.
            unresolved: !missing && (Number(id) === 99 || Number(id) === 999)
        };
    }

    /** What identifies the staged student at a glance. */
    stagedChips(): { label: string; value: string }[] {
        return [
            { label: 'DATA_IMPORT.ETL_LOOKUP_DEGREE', value: this.stagedValue('DEGREE_NAME') },
            { label: 'DATA_IMPORT.ETL_LOOKUP_MAJOR', value: this.stagedValue('MAJOR_NAME') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_COUNTRY', value: this.stagedValue('COUNTRY_NAME') }
        ].filter(chip => chip.value !== '');
    }

    /** Grouped thousands — STUDENT_AMOUNT is an INT, so no decimals to show. */
    stagedAmount(): string {
        const raw = this.stagedDetail?.['STUDENT_AMOUNT'];
        if (raw === null || raw === undefined || raw === '') {
            return '-';
        }
        const value = Number(raw);
        return isNaN(value) ? String(raw) : value.toLocaleString('en-US');
    }

    stagedStats(): { label: string; value: string }[] {
        return [
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_SCHOLARSHIP_YEAR', value: this.stagedValue('STUDENT_DELEGATION_DECISION_YEAR') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_STUDY_DURATION', value: this.stagedValue('STUDENT_STUDY_NO_OF_YEARS') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_DECISION_NO', value: this.stagedValue('STUDENT_DELEGATE_DECISION_NO') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_FIRST_DISBURSEMENT', value: this.stagedValue('STUDENT_FIRST_RECEIPT_OF_DUE') },
            { label: 'DATA_IMPORT.ETL_QUARTER', value: this.stagedValue('CURRENT_QUARTER') }
        ];
    }

    /** The two note columns the ETL carried over, when either has anything. */
    stagedNotes(): { label: string; value: string }[] {
        return [
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_NOTES', value: this.stagedValue('STUDENT_NOTE_1') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_FINANCIAL_NOTES', value: this.stagedValue('STUDENT_NOTE_2') }
        ].filter(note => note.value !== '');
    }

    /**
     * The rest of the record, grouped by what the fields are for.
     *
     * <p>"Resolved" first, because those are the ones the ETL decided and the
     * ones worth checking. "Placeholders" last, because the sheet said nothing
     * about them and a reader should be able to tell that apart from a value
     * that was chosen.
     */
    stagedSections(): {
        title: string;
        fields: { label: string; value: string; wide?: boolean; unresolved?: boolean }[];
    }[] {
        if (!this.stagedDetail) return [];

        const plain = (label: string, key: string, wide = false) =>
            ({ label, value: this.stagedValue(key), wide });

        return [
            {
                title: 'DATA_IMPORT.ETL_SECTION_RESOLVED',
                fields: [
                    this.stagedLookup('DATA_IMPORT.ETL_LOOKUP_DEGREE', 'STUDENT_DEGREE_ID', 'DEGREE_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_LOOKUP_MAJOR', 'STUDENT_MAJOR_ID', 'MAJOR_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_LOOKUP_CURRENT_LEVEL', 'STUDENT_CURRENT_LEVEL_ID', 'LEVEL_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_LOOKUP_AGENCY_TYPE', 'STUDENT_AGENCY_TYPE_ID', 'AGENCY_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_LOOKUP_MINISTRY_TYPE', 'STUDENT_MINISTRY_TYPE_ID', 'MINISTRY_NAME'),
                    // The text the sheet actually carried, kept whether or not
                    // the specialization above resolved to anything.
                    plain('DATA_IMPORT.ETL_MAJOR_AS_WRITTEN', 'STUDENT_MAJOR_NAME', true)
                ]
            },
            {
                title: 'DATA_IMPORT.ETL_SECTION_STATUS',
                fields: [
                    this.stagedLookup('DATA_IMPORT.ETL_STUDY_STATUS', 'STUDENT_STUDY_STATUS_ID', 'STATUS_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_NATIONALITY', 'STUDENT_COUNTRY_ID', 'NATIONALITY_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_STUDY_COUNTRY', 'STUDY_COUNTRY_IN', 'COUNTRY_NAME'),
                    plain('DATA_IMPORT.ETL_ADDED_QUARTER', 'STUDENT_ADDED_QUARTER'),
                    plain('DATA_IMPORT.ETL_LAST_QUARTER', 'STUDENT_LAST_QUARTER'),
                    plain('DATA_IMPORT.ETL_VISIBILITY', 'RECORD_VISIBILITY'),
                    plain('DATA_IMPORT.ETL_STAGED_AT', 'STUDENT_MIDATE')
                ]
            },
            {
                title: 'DATA_IMPORT.ETL_SECTION_PLACEHOLDERS',
                fields: [
                    this.stagedLookup('DATA_IMPORT.ETL_UNIVERSITY', 'STUDY_UNIVERSITY_IN', 'UNIVERSITY_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_COLLEGE', 'STUDY_COLLEGE_IN', 'COLLEGE_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_STUDY_GOV', 'SYUDY_GOV_IN', 'STUDY_GOV_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_EDUCATION', 'STUDY_EDUCATION_IN', 'EDUCATION_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_STUDY_MAJOR_IN', 'STUDY_MAJOR_IN', 'STUDY_MAJOR_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_STUDY_TYPE', 'STUDENT_STUDY_TYPE_ID', 'STUDY_TYPE_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_UNIVERSITY_TYPE', 'STUDENT_UNIVERSITY_TYPE_ID', 'UNIVERSITY_TYPE_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_HOME_GOV', 'STUDENT_GOV_ID', 'HOME_GOV_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_HOME_DISTRICT', 'STUDENT_MUD_ID', 'HOME_DISTRICT_NAME'),
                    this.stagedLookup('DATA_IMPORT.ETL_GENDER', 'STUDENT_GENDER', 'GENDER_NAME')
                ]
            }
        ];
    }

    /**
     * The edit form, grouped the way the record dialog is.
     *
     * <p>A field is a dropdown exactly when the column is a foreign key: the
     * point of this screen is to replace the resolver's guess with a choice
     * from the list it was guessing at, and a free-text box there would only
     * let somebody invent an id that does not exist.
     */
    readonly etlEditSections: {
        title: string;
        fields: { key: string; label: string; type: 'text' | 'number' | 'lookup' | 'textarea'; wide?: boolean }[];
    }[] = [
        {
            title: 'DATA_IMPORT.ETL_SECTION_RESOLVED',
            fields: [
                { key: 'STUDENT_NAME', label: 'DATA_IMPORT.SCHOLARSHIP_COL_STUDENT_NAME', type: 'text', wide: true },
                { key: 'STUDENT_DEGREE_ID', label: 'DATA_IMPORT.ETL_LOOKUP_DEGREE', type: 'lookup' },
                { key: 'STUDENT_MAJOR_ID', label: 'DATA_IMPORT.ETL_LOOKUP_MAJOR', type: 'lookup' },
                { key: 'STUDENT_MAJOR_NAME', label: 'DATA_IMPORT.ETL_MAJOR_AS_WRITTEN', type: 'text', wide: true },
                { key: 'STUDENT_CURRENT_LEVEL_ID', label: 'DATA_IMPORT.ETL_LOOKUP_CURRENT_LEVEL', type: 'lookup' },
                { key: 'STUDENT_AGENCY_TYPE_ID', label: 'DATA_IMPORT.ETL_LOOKUP_AGENCY_TYPE', type: 'lookup' },
                { key: 'STUDENT_MINISTRY_TYPE_ID', label: 'DATA_IMPORT.ETL_LOOKUP_MINISTRY_TYPE', type: 'lookup' },
                { key: 'STUDENT_STUDY_STATUS_ID', label: 'DATA_IMPORT.ETL_STUDY_STATUS', type: 'lookup' }
            ]
        },
        {
            title: 'DATA_IMPORT.ETL_SECTION_SCHOLARSHIP',
            fields: [
                { key: 'STUDENT_DELEGATION_DECISION_YEAR', label: 'DATA_IMPORT.SCHOLARSHIP_COL_SCHOLARSHIP_YEAR', type: 'text' },
                { key: 'STUDENT_DELEGATE_DECISION_NO', label: 'DATA_IMPORT.SCHOLARSHIP_COL_DECISION_NO', type: 'number' },
                { key: 'STUDENT_STUDY_NO_OF_YEARS', label: 'DATA_IMPORT.SCHOLARSHIP_COL_STUDY_DURATION', type: 'number' },
                { key: 'STUDENT_FIRST_RECEIPT_OF_DUE', label: 'DATA_IMPORT.SCHOLARSHIP_COL_FIRST_DISBURSEMENT', type: 'number' },
                { key: 'STUDENT_AMOUNT', label: 'DATA_IMPORT.SCHOLARSHIP_COL_AMOUNT', type: 'number' },
                { key: 'STUDENT_ADDED_QUARTER', label: 'DATA_IMPORT.ETL_ADDED_QUARTER', type: 'number' },
                { key: 'STUDENT_LAST_QUARTER', label: 'DATA_IMPORT.ETL_LAST_QUARTER', type: 'number' },
                { key: 'CURRENT_QUARTER', label: 'DATA_IMPORT.ETL_QUARTER', type: 'text' }
            ]
        },
        {
            title: 'DATA_IMPORT.ETL_SECTION_STUDENT',
            fields: [
                { key: 'STUDENT_COUNTRY_ID', label: 'DATA_IMPORT.ETL_NATIONALITY', type: 'lookup' },
                { key: 'STUDENT_GENDER', label: 'DATA_IMPORT.ETL_GENDER', type: 'lookup' },
                { key: 'STUDENT_GOV_ID', label: 'DATA_IMPORT.ETL_HOME_GOV', type: 'lookup' },
                { key: 'STUDENT_MUD_ID', label: 'DATA_IMPORT.ETL_HOME_DISTRICT', type: 'lookup' },
                { key: 'NATIONAL_ID', label: 'DATA_IMPORT.ETL_NATIONAL_ID', type: 'text' },
                { key: 'STUDENT_PASSPORT_NUMBER', label: 'DATA_IMPORT.ETL_PASSPORT', type: 'text' },
                { key: 'STUDENT_EMAIL', label: 'DATA_IMPORT.ETL_EMAIL', type: 'text' },
                { key: 'STUDENT_CELLPHONE', label: 'DATA_IMPORT.ETL_CELLPHONE', type: 'text' }
            ]
        },
        {
            title: 'DATA_IMPORT.ETL_SECTION_PLACE',
            fields: [
                { key: 'STUDY_UNIVERSITY_IN', label: 'DATA_IMPORT.ETL_UNIVERSITY', type: 'lookup' },
                { key: 'STUDY_COLLEGE_IN', label: 'DATA_IMPORT.ETL_COLLEGE', type: 'lookup' },
                { key: 'SYUDY_GOV_IN', label: 'DATA_IMPORT.ETL_STUDY_GOV', type: 'lookup' },
                { key: 'STUDY_EDUCATION_IN', label: 'DATA_IMPORT.ETL_EDUCATION', type: 'lookup' },
                { key: 'STUDY_MAJOR_IN', label: 'DATA_IMPORT.ETL_STUDY_MAJOR_IN', type: 'lookup' },
                { key: 'STUDENT_STUDY_TYPE_ID', label: 'DATA_IMPORT.ETL_STUDY_TYPE', type: 'lookup' },
                { key: 'STUDENT_UNIVERSITY_TYPE_ID', label: 'DATA_IMPORT.ETL_UNIVERSITY_TYPE', type: 'lookup' }
            ]
        },
        {
            title: 'DATA_IMPORT.SCHOLARSHIP_SECTION_NOTES',
            fields: [
                { key: 'STUDENT_NOTE_1', label: 'DATA_IMPORT.SCHOLARSHIP_COL_NOTES', type: 'textarea', wide: true },
                { key: 'STUDENT_NOTE_2', label: 'DATA_IMPORT.SCHOLARSHIP_COL_FINANCIAL_NOTES', type: 'textarea', wide: true }
            ]
        }
    ];

    /** Every column the form can write, flattened. */
    private editableKeys(): string[] {
        return this.etlEditSections.flatMap(section => section.fields.map(field => field.key));
    }

    openStagedEdit(id: number): void {
        this.etlEditVisible = true;
        this.etlEditLoading = true;
        this.etlEditId = null;
        this.etlEditForm = {};
        this.etlEditOriginal = {};

        this.loadEtlLookups();
        this.http.get<Record<string, any>>(`${this.apiUrl}/etl/staging/${id}`).subscribe({
            next: (row) => {
                this.etlEditId = row['STUDENT_ID'];
                this.etlEditWasEdited = !!row['IS_EDITED'];
                for (const key of this.editableKeys()) {
                    // null and '' are the same thing to the form; the server
                    // turns an empty box back into NULL on the way in.
                    this.etlEditForm[key] = row[key] === null || row[key] === undefined ? null : row[key];
                }
                this.etlEditOriginal = { ...this.etlEditForm };
                this.etlEditLoading = false;
            },
            error: (err) => {
                this.etlEditLoading = false;
                this.etlEditVisible = false;
                this.message.error(err.error?.message
                    || this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_DETAIL_FAILED'));
            }
        });
    }

    /** Fetched once and kept: the options do not change while a dialog is open. */
    private loadEtlLookups(): void {
        if (this.hasAnyLookups() || this.etlLookupsLoading) {
            return;
        }
        this.etlLookupsLoading = true;
        this.etlLookupsFailed = false;
        this.http.get<Record<string, { id: number; name: string }[]>>(
            `${this.apiUrl}/etl/staging/lookups`).subscribe({
            next: (lookups) => {
                this.etlLookups = lookups || {};
                this.etlLookupsLoading = false;
                // An empty response is a failure too. The lookup tables behind
                // these are never empty, so nothing came back that can be used.
                this.etlLookupsFailed = !this.hasAnyLookups();
            },
            error: () => {
                this.etlLookupsLoading = false;
                this.etlLookupsFailed = true;
                this.etlLookups = {};
            }
        });
    }

    retryEtlLookups(): void {
        this.etlLookups = {};
        this.etlLookupsFailed = false;
        this.loadEtlLookups();
    }

    private hasAnyLookups(): boolean {
        return Object.values(this.etlLookups).some(options => options?.length > 0);
    }

    /** Whether this one column got any options back. */
    hasLookupOptions(key: string): boolean {
        return (this.etlLookups[key]?.length ?? 0) > 0;
    }

    /**
     * Whether the value the row already holds is in the offered list.
     *
     * <p>It usually is. It is not when the lists failed to load, or when a
     * lookup row was deleted after this row was staged — and in both cases the
     * id must stay in the select, or opening the form would silently blank a
     * field nobody touched.
     */
    isKnownOption(key: string, value: any): boolean {
        return (this.etlLookups[key] || []).some(option => option.id === value);
    }

    /** True when this one field differs from what was loaded. */
    isFieldChanged(key: string): boolean {
        return this.normalisedEdit(this.etlEditForm[key]) !== this.normalisedEdit(this.etlEditOriginal[key]);
    }

    etlEditDirty(): boolean {
        return this.editableKeys().some(key => this.isFieldChanged(key));
    }

    /**
     * Compares the way the server will store it.
     *
     * <p>An untouched number box hands back the string "1500" where the record
     * carried the number 1500, and an emptied text box hands back "" where it
     * carried null. Neither is a change, and without this both would be sent
     * on every save and every field would look edited.
     */
    private normalisedEdit(value: any): string {
        return value === null || value === undefined ? '' : String(value).trim();
    }

    saveStagedEdit(): void {
        if (!this.etlEditId || !this.etlEditDirty()) {
            return;
        }
        const changes: Record<string, any> = {};
        for (const key of this.editableKeys()) {
            if (this.isFieldChanged(key)) {
                changes[key] = this.etlEditForm[key];
            }
        }

        this.etlEditSaving = true;
        this.http.put<Record<string, any>>(
            `${this.apiUrl}/etl/staging/${this.etlEditId}`, changes).subscribe({
            next: (row) => {
                this.etlEditSaving = false;
                this.etlEditVisible = false;
                this.message.success(this.translationService.translate('DATA_IMPORT.ETL_SAVED'));
                // The dialog behind this one, if it is open on the same row.
                if (this.stagedDetailVisible && this.stagedDetail?.['STUDENT_ID'] === row?.['STUDENT_ID']) {
                    this.stagedDetail = row;
                }
                this.loadStagedData(true);
            },
            error: (err) => {
                this.etlEditSaving = false;
                this.message.error(err.error?.message
                    || this.translationService.translate('DATA_IMPORT.ETL_SAVE_FAILED'));
            }
        });
    }

    /**
     * Hands the row back to the ETL, so the next run replaces it again.
     *
     * <p>Confirmed: this throws away a correction somebody typed by hand, and it
     * used to go on a single click from a danger button.
     */
    clearStagedEdit(): void {
        if (!this.etlEditId) {
            return;
        }
        this.modal.confirm({
            nzTitle: this.translationService.translate('DATA_IMPORT.ETL_CLEAR_CONFIRM_TITLE'),
            nzContent: this.translationService.translate('DATA_IMPORT.ETL_CLEAR_CONFIRM_MSG'),
            nzOkDanger: true,
            nzOkText: this.translationService.translate('COMMON.CONFIRM'),
            nzCancelText: this.translationService.translate('COMMON.CANCEL'),
            nzOnOk: () => this.performClearStagedEdit()
        });
    }

    private performClearStagedEdit(): void {
        this.etlEditSaving = true;
        this.http.delete(`${this.apiUrl}/etl/staging/${this.etlEditId}/edit`).subscribe({
            next: () => {
                this.etlEditSaving = false;
                this.etlEditVisible = false;
                this.etlEditWasEdited = false;
                this.message.success(this.translationService.translate('DATA_IMPORT.ETL_CLEARED'));
                this.loadStagedData(true);
            },
            error: (err) => {
                this.etlEditSaving = false;
                this.message.error(err.error?.message
                    || this.translationService.translate('DATA_IMPORT.ETL_SAVE_FAILED'));
            }
        });
    }

    closeStagedEdit(): void {
        this.etlEditVisible = false;
        this.etlEditId = null;
        this.etlEditForm = {};
        this.etlEditOriginal = {};
    }

    resetEtl(): void {
        this.etlStep = 0;
        this.etlReport = null;
        this.etlResult = null;
        this.stagedData = null;
        this.stagedPageIndex = 0;
        this.closeStagedDetail();
        this.closeStagedEdit();
        // The country survives: it is almost always the one being worked on.
        this.onEtlCountryChange();
    }

    // ── Scholarship data import ──────────────────────────────────────────────

    private loadCountries(): void {
        this.lookupService.getAllStudyCountries().subscribe({
            next: (list) => { this.countries = list || []; },
            error: () => { this.countries = []; }
        });
    }

    /**
     * getAllStudyCountries() has already put the name for the active language
     * into studyCountryInName, so there is no English field left to fall back to.
     */
    columnWidth(column: string): string {
        return `${DataImportComponent.COLUMN_WIDTHS[column] ?? 120}px`;
    }

    /** Total width of the grid columns alone — the preview table shows only these. */
    get scholarshipGridWidth(): string {
        return `${this.scholarshipGridColumns.reduce(
            (sum, c) => sum + (DataImportComponent.COLUMN_WIDTHS[c] ?? 120), 0)}px`;
    }

    countryLabel(country: StudyCountryIn): string {
        return country.studyCountryInName || String(country.id);
    }

    beforeScholarshipUpload = (file: NzUploadFile): boolean => {
        this.scholarshipFile = file as any;
        this.scholarshipResult = null;
        this.scholarshipPreviewData = null;
        // false: hand the file to us rather than letting nz-upload post it.
        return false;
    };

    scholarshipPreview(): void {
        if (!this.scholarshipFile) return;
        this.isScholarshipPreviewLoading = true;
        const formData = new FormData();
        formData.append('file', this.scholarshipFile);
        this.http.post<ScholarshipPreview>(`${this.apiUrl}/scholarship-data/preview`, formData).subscribe({
            next: (data) => {
                this.scholarshipPreviewData = data;
                this.loadScholarshipCounts();
                this.scholarshipStep = 1;
                this.isScholarshipPreviewLoading = false;
            },
            error: (err) => {
                this.message.error(err.error?.message || this.translationService.translate('DATA_IMPORT.PARSE_FAILED'));
                this.isScholarshipPreviewLoading = false;
            }
        });
    }

    /** Row count, which row the captions were found on, and how many rows were passed over. */
    scholarshipPreviewSummary(): string {
        const t = (key: string) => this.translationService.translate(key);
        const preview = this.scholarshipPreviewData;
        return `${t('DATA_IMPORT.TOTAL_ROWS')}: ${preview?.totalRows ?? 0}`
            + ` \u00b7 ${t('DATA_IMPORT.SCHOLARSHIP_HEADER_ROW')}: ${preview?.headerRow ?? '-'}`
            + ` \u00b7 ${t('DATA_IMPORT.SCHOLARSHIP_SKIPPED')}: ${preview?.skippedRows ?? 0}`;
    }

    /** The letterhead, as label/value pairs for the preview block. */
    scholarshipHeaderFields(): { key: string; value: string }[] {
        const header = this.scholarshipPreviewData?.header;
        if (!header) return [];
        return [
            { key: 'DATA_IMPORT.SCHOLARSHIP_HDR_REPORT_TITLE', value: header.reportTitle || '' },
            { key: 'DATA_IMPORT.SCHOLARSHIP_HDR_QUARTER', value: header.quarter || '' },
            { key: 'DATA_IMPORT.SCHOLARSHIP_HDR_YEAR', value: header.year || '' },
            { key: 'DATA_IMPORT.SCHOLARSHIP_HDR_ENTITY', value: header.entity || '' },
            { key: 'DATA_IMPORT.SCHOLARSHIP_HDR_DEPARTMENT', value: header.department || '' },
            { key: 'DATA_IMPORT.SCHOLARSHIP_HDR_STATEMENT_DATE', value: header.statementDate || '' }
        ];
    }

    /** ALL contains COUNTRY, so both ticked is one wipe. Mirrors DeleteMode.of(). */
    get scholarshipDeleteMode(): 'NONE' | 'COUNTRY' | 'ALL' {
        if (this.scholarshipDeleteAll) return 'ALL';
        return this.scholarshipDeleteCountry ? 'COUNTRY' : 'NONE';
    }

    /** What is about to be destroyed, with the number of rows it comes to. */
    scholarshipDeleteWarning(): string {
        const t = (key: string) => this.translationService.translate(key);
        if (this.scholarshipDeleteMode === 'ALL') {
            return t('DATA_IMPORT.SCHOLARSHIP_CONFIRM_ALL')
                .replace('{rows}', String(this.scholarshipCounts?.totalRows ?? 0));
        }
        if (this.scholarshipDeleteMode === 'COUNTRY') {
            return t('DATA_IMPORT.SCHOLARSHIP_CONFIRM_COUNTRY')
                .replace('{rows}', String(this.scholarshipCounts?.countryRows ?? 0))
                .replace('{country}', this.selectedCountryName());
        }
        return '';
    }

    private selectedCountryName(): string {
        const match = this.countries.find(c => c.id === this.scholarshipCountryId);
        return match ? this.countryLabel(match) : String(this.scholarshipCountryId ?? '');
    }

    /** How many rows a delete would take, so the confirmation can say so. */
    private loadScholarshipCounts(): void {
        const params: Record<string, string> = {};
        if (this.scholarshipCountryId) {
            params['countryId'] = String(this.scholarshipCountryId);
        }
        this.http.get<any>(`${this.apiUrl}/scholarship-data/counts`, { params }).subscribe({
            next: (data) => { this.scholarshipCounts = data; },
            error: () => { this.scholarshipCounts = null; }
        });
    }

    importScholarshipData(): void {
        if (!this.scholarshipFile) return;
        if (!this.scholarshipCountryId) {
            // The country decides which folder the workbook is filed under, so
            // there is nothing sensible to do without it.
            this.message.error(this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_COUNTRY_REQUIRED'));
            return;
        }
        // Deleting is never one click away from the button that also imports.
        if (this.scholarshipDeleteMode !== 'NONE') {
            this.modal.confirm({
                nzTitle: this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_CONFIRM_TITLE'),
                nzContent: this.scholarshipDeleteWarning(),
                nzOkDanger: true,
                nzOkText: this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_CONFIRM_OK'),
                nzCancelText: this.translationService.translate('COMMON.CANCEL'),
                nzOnOk: () => this.runScholarshipImport()
            });
            return;
        }
        this.runScholarshipImport();
    }

    private runScholarshipImport(): void {
        this.isScholarshipImporting = true;
        this.scholarshipResult = null;
        const formData = new FormData();
        formData.append('file', this.scholarshipFile);
        formData.append('countryId', String(this.scholarshipCountryId));
        formData.append('deleteCountryData', String(this.scholarshipDeleteMode === 'COUNTRY'));
        formData.append('deleteAllData', String(this.scholarshipDeleteMode === 'ALL'));
        this.http.post<any>(`${this.apiUrl}/scholarship-data`, formData).subscribe({
            next: (data) => {
                this.scholarshipResult = {
                    success: true,
                    filename: data.filename,
                    rowsImported: data.rowsImported,
                    skippedRows: data.skippedRows,
                    storedFile: data.storedFile,
                    rowsDeleted: data.rowsDeleted
                };
                this.scholarshipStep = 2;
                this.isScholarshipImporting = false;
                this.message.success(this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_IMPORT_DONE'));
            },
            error: (err) => {
                const message = err.error?.message
                    || this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_IMPORT_FAILED');
                this.scholarshipResult = { success: false, message };
                this.scholarshipStep = 2;
                this.isScholarshipImporting = false;
                this.message.error(message);
            }
        });
    }

    goToViewScholarshipData(): void {
        this.scholarshipStep = 3;
        this.scholarshipPageIndex = 0;
        // Default the filter to whatever was just imported, so the rows the user
        // is looking for are the ones on screen.
        this.scholarshipFilterCountryId = this.scholarshipFilterCountryId ?? this.scholarshipCountryId;
        this.loadScholarshipFilterOptions();
        // Forced: an import just changed the rows, so the answer to the same
        // question is not the same answer.
        this.loadScholarshipData(true);
    }

    /**
     * A different country is a different set of rows, so the column filters
     * chosen for the last one are thrown away rather than carried over: the
     * degrees on offer in Egypt are not the degrees on offer in Malaysia, and
     * keeping a stale tick would show an empty table with no visible reason.
     */
    onScholarshipCountryChange(): void {
        this.scholarshipFilters = {};
        this.scholarshipPageIndex = 0;
        this.loadScholarshipFilterOptions();
        this.loadScholarshipData();
    }

    /** Which arrow, if any, this column's header should be showing. */
    sortOrderFor(column: string): NzTableSortOrder {
        return this.scholarshipSortBy === column ? this.scholarshipSortDir : null;
    }

    /** True when anything is narrowing the list — drives the reset button. */
    get hasScholarshipQuery(): boolean {
        return !!this.scholarshipSearch.trim()
            || !!this.scholarshipSortBy
            || Object.values(this.scholarshipFilters).some(values => values.length > 0);
    }

    clearScholarshipSearch(): void {
        this.scholarshipSearch = '';
        this.scholarshipSearch$.next('');
    }

    /**
     * Back to the plain newest-first list.
     *
     * <p>The country is left alone. It is the scope the user chose, not a
     * filter they are trying to escape, and resetting it would swap the rows
     * under them for somebody else's.
     */
    resetScholarshipQuery(): void {
        this.scholarshipSearch = '';
        this.scholarshipSearch$.next('');
        this.scholarshipSortBy = null;
        this.scholarshipSortDir = null;
        this.scholarshipFilters = {};
        this.scholarshipPageIndex = 0;
        // New array identities, so the ticks actually clear on screen: ng-zorro
        // reads the checked state off `byDefault` when the list object changes.
        this.rebuildScholarshipFilterLists();
        this.loadScholarshipData();
    }

    /**
     * Everything the table can ask for, in one event.
     *
     * <p>ng-zorro emits the whole query — page, size, every column's sort and
     * every column's filter — on any change to any of them, so this reads the
     * lot rather than trying to work out which part moved.
     */
    onScholarshipTableQuery(params: NzTableQueryParams): void {
        const before = this.scholarshipCriteria();

        this.scholarshipPageIndex = Math.max(0, (params.pageIndex || 1) - 1);
        this.scholarshipPageSize = params.pageSize || this.scholarshipPageSize;

        // One column at a time. The header bindings clear the others, and the
        // server orders by a single column, so a second one would be ignored.
        const sorted = (params.sort || []).find(item => !!item.value);
        this.scholarshipSortBy = sorted ? sorted.key : null;
        this.scholarshipSortDir = sorted ? sorted.value : null;

        const filters: Record<string, string[]> = {};
        for (const item of params.filter || []) {
            const values = (Array.isArray(item.value) ? item.value : [item.value])
                .filter(value => value !== null && value !== undefined && value !== '')
                .map(value => String(value));
            if (values.length) {
                filters[item.key] = values;
            }
        }
        this.scholarshipFilters = filters;

        // Page 7 of the old list is not page 7 of the new one, and is often not
        // a page at all. Narrowing the list sends you back to the start of it.
        if (this.scholarshipCriteria() !== before) {
            this.scholarshipPageIndex = 0;
        }

        this.loadScholarshipData();
    }

    /** Sort, filters and search — everything except which page of them. */
    private scholarshipCriteria(): string {
        return JSON.stringify([
            this.scholarshipSortBy,
            this.scholarshipSortDir,
            this.scholarshipSearch.trim(),
            Object.keys(this.scholarshipFilters).sort()
                .map(key => [key, [...this.scholarshipFilters[key]].sort()])
        ]);
    }

    /**
     * The request, as query parameters.
     *
     * <p>Column filters go out as repeated `filter.COLUMN=value` pairs. The
     * column names are the server's to accept or ignore — it holds the list of
     * what may be sorted and filtered, because these names end up in SQL.
     */
    private scholarshipQueryParams(): HttpParams {
        let params = new HttpParams()
            .set('page', String(this.scholarshipPageIndex))
            .set('size', String(this.scholarshipPageSize));
        if (this.scholarshipFilterCountryId) {
            params = params.set('countryId', String(this.scholarshipFilterCountryId));
        }
        if (this.scholarshipSortBy && this.scholarshipSortDir) {
            params = params.set('sortBy', this.scholarshipSortBy)
                .set('sortDir', this.scholarshipSortDir);
        }
        const search = this.scholarshipSearch.trim();
        if (search) {
            params = params.set('search', search);
        }
        for (const key of Object.keys(this.scholarshipFilters).sort()) {
            for (const value of this.scholarshipFilters[key]) {
                params = params.append('filter.' + key, value);
            }
        }
        return params;
    }

    private loadScholarshipData(force = false): void {
        const params = this.scholarshipQueryParams();
        const asked = params.toString();
        if (!force && asked === this.scholarshipQuerySent) {
            return;
        }
        this.scholarshipQuerySent = asked;

        this.scholarshipTableLoading = true;
        this.http.get<any>(`${this.apiUrl}/scholarship-data`, { params }).subscribe({
            next: (data) => {
                this.scholarshipTableData = data;
                this.scholarshipTableLoading = false;
            },
            error: () => {
                this.scholarshipTableData = { content: [], totalElements: 0 };
                this.scholarshipTableLoading = false;
                // A failed request is not an answer, so do not let it stand as
                // one: the next identical query must be allowed to try again.
                this.scholarshipQuerySent = '';
            }
        });
    }

    /** The values each dropdown offers, read off the rows that are really there. */
    private loadScholarshipFilterOptions(): void {
        const params = this.scholarshipFilterCountryId
            ? new HttpParams().set('countryId', String(this.scholarshipFilterCountryId))
            : new HttpParams();
        this.http.get<Record<string, string[]>>(`${this.apiUrl}/scholarship-data/filters`, { params })
            .subscribe({
                next: (options) => {
                    this.scholarshipFilterOptions = options || {};
                    this.rebuildScholarshipFilterLists();
                },
                error: () => {
                    // No options is a dropdown with nothing in it, which is
                    // honest. The table itself is unaffected.
                    this.scholarshipFilterOptions = {};
                    this.rebuildScholarshipFilterLists();
                }
            });
    }

    private rebuildScholarshipFilterLists(): void {
        const lists: Record<string, NzTableFilterList> = {};
        for (const key of Object.keys(this.scholarshipFilterLists)) {
            const chosen = this.scholarshipFilters[key] || [];
            lists[key] = (this.scholarshipFilterOptions[key] || []).map(value => ({
                text: value,
                value,
                byDefault: chosen.includes(value)
            }));
        }
        this.scholarshipFilterLists = lists;
    }

    openScholarshipDetail(id: number): void {
        this.scholarshipDetailVisible = true;
        this.scholarshipDetailLoading = true;
        this.scholarshipDetail = null;
        this.http.get<Record<string, any>>(`${this.apiUrl}/scholarship-data/${id}`).subscribe({
            next: (row) => {
                this.scholarshipDetail = row;
                this.scholarshipDetailLoading = false;
            },
            error: (err) => {
                this.scholarshipDetailLoading = false;
                this.scholarshipDetailVisible = false;
                this.message.error(err.error?.message
                    || this.translationService.translate('DATA_IMPORT.SCHOLARSHIP_DETAIL_FAILED'));
            }
        });
    }

    closeScholarshipDetail(): void {
        this.scholarshipDetailVisible = false;
        this.scholarshipDetail = null;
    }

    /** The dialog renders on <body>, so it cannot inherit the page direction. */
    get isRtl(): boolean {
        return this.translationService.isRTL();
    }

    private detailValue(key: string): string {
        const value = this.scholarshipDetail?.[key];
        return value === null || value === undefined ? '' : String(value);
    }

    /** Country, degree and specialization — what identifies the student at a glance. */
    recordChips(): { label: string; value: string }[] {
        return [
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_COUNTRY', value: this.detailValue('COUNTRY') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_DEGREE', value: this.detailValue('DEGREE') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_SPECIALIZATION', value: this.detailValue('SPECIALIZATION') }
        ].filter(chip => chip.value !== '');
    }

    /** Grouped thousands and two decimals — it is a sum of money, not a count. */
    recordAmount(): string {
        const raw = this.scholarshipDetail?.['AMOUNT'];
        if (raw === null || raw === undefined || raw === '') {
            return '-';
        }
        const value = Number(raw);
        return isNaN(value)
            ? String(raw)
            : value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    /** The short facts, as tiles. */
    recordStats(): { label: string; value: string }[] {
        return [
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_SCHOLARSHIP_YEAR', value: this.detailValue('SCHOLARSHIP_YEAR') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_STUDY_DURATION', value: this.detailValue('STUDY_DURATION') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_CURRENT_LEVEL', value: this.detailValue('CURRENT_LEVEL') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_FIRST_DISBURSEMENT', value: this.detailValue('FIRST_DISBURSEMENT') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_DECISION_NO', value: this.detailValue('DECISION_NO') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_ENTITY', value: this.detailValue('ENTITY') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_STUDENT_SIGNATURE', value: this.detailValue('STUDENT_SIGNATURE') }
        ];
    }

    /** The two free-text columns, when the sheet had anything in them. */
    recordNotes(): { label: string; value: string }[] {
        return [
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_NOTES', value: this.detailValue('NOTES') },
            { label: 'DATA_IMPORT.SCHOLARSHIP_COL_FINANCIAL_NOTES', value: this.detailValue('FINANCIAL_NOTES') }
        ].filter(note => note.value !== '');
    }

    /**
     * What is left once the summary, the tiles and the notes have taken their
     * share: the letterhead the line arrived under, and where it came from.
     */
    scholarshipDetailSections(): {
        title: string;
        fields: { label: string; value: string; wide?: boolean; mono?: boolean }[];
    }[] {
        if (!this.scholarshipDetail) return [];

        const field = (label: string, key: string, opts: { wide?: boolean; mono?: boolean } = {}) =>
            ({ label, value: this.detailValue(key), ...opts });

        const notes = [1, 2, 3, 4]
            .filter(i => this.detailValue(`HDR_NOTE_${i}`) !== '')
            .map(i => ({
                label: 'DATA_IMPORT.SCHOLARSHIP_HDR_NOTE_' + i,
                value: this.detailValue(`HDR_NOTE_${i}`),
                wide: true
            }));

        return [
            {
                title: 'DATA_IMPORT.SCHOLARSHIP_SECTION_STATEMENT',
                fields: [
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_REPORT_TITLE', 'HDR_REPORT_TITLE'),
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_QUARTER', 'HDR_QUARTER'),
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_YEAR', 'HDR_YEAR'),
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_STATEMENT_DATE', 'HDR_STATEMENT_DATE'),
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_ENTITY', 'HDR_ENTITY'),
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_REPUBLIC', 'HDR_REPUBLIC'),
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_MINISTRY', 'HDR_MINISTRY', { wide: true }),
                    field('DATA_IMPORT.SCHOLARSHIP_HDR_DEPARTMENT', 'HDR_DEPARTMENT', { wide: true })
                ].concat(notes)
            },
            {
                title: 'DATA_IMPORT.SCHOLARSHIP_SECTION_SOURCE',
                fields: [
                    field('DATA_IMPORT.SCHOLARSHIP_COL_SOURCE_FILE', 'SOURCE_FILE', { wide: true, mono: true }),
                    field('DATA_IMPORT.SCHOLARSHIP_COL_SOURCE_SHEET', 'SOURCE_SHEET'),
                    field('DATA_IMPORT.SCHOLARSHIP_COL_SOURCE_ROW', 'SOURCE_ROW'),
                    field('DATA_IMPORT.IMPORTED_AT', 'IMPORTED_AT'),
                    field('DATA_IMPORT.SCHOLARSHIP_COL_IMPORTED_BY', 'IMPORTED_BY')
                ]
            }
        ];
    }

    resetScholarshipImport(): void {
        this.scholarshipStep = 0;
        this.scholarshipFile = null;
        this.scholarshipPreviewData = null;
        this.scholarshipResult = null;
        this.scholarshipCounts = null;
        this.closeScholarshipDetail();
        // These do NOT survive. The country is kept because attachés are usually
        // uploaded one after another for the same country — but a "delete
        // everything" left ticked for the next upload would be a disaster.
        this.scholarshipDeleteCountry = false;
        this.scholarshipDeleteAll = false;
    }
}
