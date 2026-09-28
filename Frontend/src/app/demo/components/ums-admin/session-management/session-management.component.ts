import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { ToastService } from '../../../service/toast.service';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';

import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { buildUmsListParams } from '../../../service/ums-list-params';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { TranslationService } from '../../../service/translation.service';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { silentErrors } from '../../../interceptors/http-context.tokens';

interface UserSession {
  id: number;
  user: { id: number; userName: string; email: string };
  ipAddress: string;
  userAgent: string;
  deviceInfo: string;
  createdAt: string;
  expiresAt: string;
  isActive: boolean;
}

@Component({
    selector: 'app-session-management',
    imports: [
        CommonModule,
        FormsModule,
        TranslatePipe,
        NzTableModule,
        NzButtonModule,
        NzIconModule,
        NzModalModule,
        NzDescriptionsModule,
        NzTagModule,
        NzCardModule,
        NzTooltipModule,
        NzInputModule,
        NzSelectModule,
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
            <i class="pi pi-clock text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.SESSIONS.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.SESSIONS.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button (click)="loadSessions()" [nzLoading]="loading">
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
              <label for="usSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="usSearch" type="text" nz-input [placeholder]="'COMMON.SEARCH' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterActive">
              <label for="usStatus">{{ 'UMS.SHARED.FILTER_SESSION_STATUS' | translate }}</label>
              <nz-select nzId="usStatus" [(ngModel)]="filterActive" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_SESSION_ANY' | translate" nzValue=""></nz-option>
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_SESSION_ACTIVE' | translate" nzValue="true"></nz-option>
                <nz-option [nzLabel]="'UMS.SHARED.INACTIVE_ONLY' | translate" nzValue="false"></nz-option>
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
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalSessions }}</span>
          </div>
        </div>
  
      <nz-table
        #sessionsTable
        [nzData]="sessions"
        [nzLoading]="loading"
        [nzTotal]="totalSessions"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="user.userName" [nzSortOrder]="sortOrderFor('user.userName')" (nzSortOrderChange)="onSort('user.userName', $event)">{{ 'UMS.SESSIONS.USER' | translate }}</th>
            <th nzShowSort nzColumnKey="ipAddress" [nzSortOrder]="sortOrderFor('ipAddress')" (nzSortOrderChange)="onSort('ipAddress', $event)">{{ 'UMS.SESSIONS.IP_ADDRESS' | translate }}</th>
            <th>{{ 'UMS.SESSIONS.USER_AGENT' | translate }}</th>
            <th nzShowSort nzColumnKey="createdAt" [nzSortOrder]="sortOrderFor('createdAt')" (nzSortOrderChange)="onSort('createdAt', $event)">{{ 'UMS.SESSIONS.STARTED_AT' | translate }}</th>
            <th nzShowSort nzColumnKey="expiresAt" [nzSortOrder]="sortOrderFor('expiresAt')" (nzSortOrderChange)="onSort('expiresAt', $event)">{{ 'UMS.SESSIONS.EXPIRES_AT' | translate }}</th>
            <th nzShowSort nzColumnKey="isActive" [nzSortOrder]="sortOrderFor('isActive')" (nzSortOrderChange)="onSort('isActive', $event)">{{ 'UMS.SESSIONS.STATUS' | translate }}</th>
            <th nzWidth="100px">{{ 'UMS.SESSIONS.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (session of sessionsTable.data; track session) {
            <tr>
              <td>{{ session.user?.userName || ('COMMON.UNKNOWN' | translate) }}</td>
              <td><code>{{ session.ipAddress || ('COMMON.NOT_AVAILABLE' | translate) }}</code></td>
              <td>{{ session.deviceInfo || (session.userAgent ? (session.userAgent.length > 50 ? session.userAgent.substring(0, 50) + '...' : session.userAgent) : ('COMMON.NOT_AVAILABLE' | translate)) }}</td>
              <td>{{ session.createdAt | date:'short' }}</td>
              <td>{{ session.expiresAt | date:'short' }}</td>
              <td>
                <nz-tag [nzColor]="session.isActive ? 'green' : 'red'">
                  {{ session.isActive ? ('UMS.STATUS.ACTIVE' | translate) : ('UMS.STATUS.INACTIVE' | translate) }}
                </nz-tag>
              </td>
              <td>
                <button nz-button nzType="link" nzSize="small" (click)="showDetailModal(session)" [nz-tooltip]="'COMMON.VIEW' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger
                  *hasPermission="'UMS_ADMIN.DELETE'"
                  [disabled]="!session.isActive"
                  (click)="confirmTerminate(session)"
                  [nz-tooltip]="'UMS.SESSIONS.TERMINATE' | translate">
                  <span nz-icon nzType="stop"></span>
                </button>
              </td>
            </tr>
          }
        </tbody>
      </nz-table>
      </div>
    </div>
      <!-- The detail view. The user agent is the reason it is worth having: it
           is far too long for the table and it is what tells one session from
           another. -->
      <nz-modal
        [(nzVisible)]="isDetailModalVisible"
        [nzTitle]="'UMS.SESSIONS.TITLE' | translate"
        [nzWidth]="720"
        [nzFooter]="detailModalFooter"
        (nzOnCancel)="isDetailModalVisible = false">
        <ng-template #detailModalFooter>
          <button nz-button (click)="isDetailModalVisible = false">{{ 'COMMON.CLOSE' | translate }}</button>
        </ng-template>
        <ng-container *nzModalContent>
          @if (detailSession) {
            <div class="dv-view">
              <div class="dv-section">
                <nz-descriptions nzBordered [nzColumn]="2" nzSize="small">
                  <nz-descriptions-item [nzTitle]="'UMS.SESSIONS.USER' | translate">{{ detailSession.user?.userName || NOT_AVAILABLE }}</nz-descriptions-item>
                  <nz-descriptions-item [nzTitle]="'UMS.USERS.EMAIL' | translate">{{ detailSession.user?.email || NOT_AVAILABLE }}</nz-descriptions-item>
                  <nz-descriptions-item [nzTitle]="'UMS.SESSIONS.IP_ADDRESS' | translate">{{ detailSession.ipAddress || NOT_AVAILABLE }}</nz-descriptions-item>
                  <nz-descriptions-item [nzTitle]="'UMS.SESSIONS.LAST_ACTIVITY' | translate">{{ detailSession.deviceInfo || NOT_AVAILABLE }}</nz-descriptions-item>
                  <nz-descriptions-item [nzTitle]="'UMS.SESSIONS.STARTED_AT' | translate">{{ detailSession.createdAt | date:'medium' }}</nz-descriptions-item>
                  <nz-descriptions-item [nzTitle]="'UMS.SESSIONS.EXPIRES_AT' | translate">{{ detailSession.expiresAt | date:'medium' }}</nz-descriptions-item>
                  <nz-descriptions-item [nzTitle]="'UMS.SESSIONS.STATUS' | translate">
                    <nz-tag [nzColor]="detailSession.isActive ? 'green' : 'red'">
                      {{ (detailSession.isActive ? 'UMS.STATUS.ACTIVE' : 'UMS.STATUS.INACTIVE') | translate }}
                    </nz-tag>
                  </nz-descriptions-item>
                  <nz-descriptions-item [nzTitle]="'UMS.SESSIONS.USER_AGENT' | translate" [nzSpan]="2">
                    <code style="word-break: break-all">{{ detailSession.userAgent || NOT_AVAILABLE }}</code>
                  </nz-descriptions-item>
                </nz-descriptions>
              </div>
            </div>
          }
        </ng-container>
      </nz-modal>
    `,
    styles: []
})
export class SessionManagementComponent implements OnInit {
  sessions: UserSession[] = [];

  /** The session the detail dialog is showing. */
  detailSession: UserSession | null = null;
  isDetailModalVisible = false;
  readonly NOT_AVAILABLE = '-';
  loading = false;
  totalSessions = 0;
  pageIndex = 1;
  pageSize = 10;
  searchKeyword = '';
  /** '' | 'true' | 'false' as string from nz-option */
  filterActive = '';
  sortBy = 'createdAt';
  sortDir: 'asc' | 'desc' = 'desc';

  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/sessions`;

  constructor(
    private http: HttpClient,
    private modal: NzModalService,
    private message: ToastService,
    public translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.loadSessions();
  }

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  resolveActiveFilter(): boolean | undefined {
    const v = this.filterActive as unknown;
    if (v === true || v === 'true') {
      return true;
    }
    if (v === false || v === 'false') {
      return false;
    }
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
      this.sortBy = 'createdAt';
      this.sortDir = 'desc';
    }
    this.pageIndex = 1;
    this.loadSessions();
  }

  /** Every filter back to its resting value, and the list from the first page. */
  clearSearchFilters(): void {
    this.searchKeyword = '';
    this.filterActive = '';
    this.applySearchFilters();
  }

  get hasSearchFilters(): boolean {
    return !!this.searchKeyword?.trim() || !!this.filterActive;
  }

  applySearchFilters(): void {
    this.pageIndex = 1;
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading = true;
    const params = buildUmsListParams({
      page: this.pageIndex - 1,
      size: this.pageSize,
      sortBy: this.sortBy,
      sortDir: this.sortDir,
      keyword: this.searchKeyword?.trim(),
      active: this.resolveActiveFilter()
    });
    this.http.get<any>(`${this.apiUrl}/`, { params }).subscribe({
      next: (response) => {
        this.sessions = response.content;
        this.totalSessions = response.totalElements;
        this.loading = false;
      },
      error: () => {
        this.message.error(this.t('UMS.SESSIONS.FAILED_LOAD'));
        this.loading = false;
      }
    });
  }

  onPageChange(pageIndex: number): void {
    this.pageIndex = pageIndex;
    this.loadSessions();
  }

  onPageSizeChange(ps: number): void {
    this.pageSize = ps;
    this.pageIndex = 1;
    this.loadSessions();
  }

  showDetailModal(session: UserSession): void {
    this.detailSession = session;
    this.isDetailModalVisible = true;
  }

  confirmTerminate(session: UserSession): void {
    this.modal.confirm({
      nzTitle: this.t('UMS.SESSIONS.TERMINATE_CONFIRM_SHORT'),
      nzContent: this.t('UMS.SESSIONS.TERMINATE_LABEL')
        .replace('{user}', session.user?.userName || this.t('COMMON.UNKNOWN'))
        .replace('{ip}', session.ipAddress || this.t('COMMON.NOT_AVAILABLE')),
      nzOkText: this.t('COMMON.YES'),
      nzCancelText: this.t('COMMON.NO'),
      nzOkDanger: true,
      nzOnOk: () => {
        this.http.delete(`${this.apiUrl}/${session.id}`, { context: silentErrors() }).subscribe({
          next: () => {
            this.message.success(this.t('UMS.SESSIONS.SESSION_TERMINATED'));
            this.loadSessions();
          },
          error: (err) => this.message.error(extractApiErrorMessage(err) || this.t('UMS.SESSIONS.TERMINATE_FAILED'))
        });
      }
    });
  }
}
