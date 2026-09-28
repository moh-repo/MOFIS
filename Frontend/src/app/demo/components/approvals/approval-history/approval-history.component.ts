import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import {
    ApprovalService,
    ApprovalRequest,
    ApprovalHistory,
} from '../../../service/approval.service';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { StudentTravelTicketService } from '../../../service/student-travel-ticket.service';
import { TravelFundingSource } from '../../../api/student-travel-ticket';
import { changesOnly } from '../../../service/reload-streams.util';
import { followPage } from '../../../service/table-paging.util';
import { CodeListService } from '../../../service/code-list.service';
import { entityName, entityTypeLabel } from '../approval-entity-label';

@Component({
    selector: 'app-approval-history',
    providers: [MessageService],
    template: `
        <p-toast></p-toast>
        <div class="card">
          <!--
            Header. The icon-and-title block takes the free space rather than the
            row carrying justify-content-between: styles.scss reverses that class
            again for RTL, which flipped the title back to the left in Arabic.

            The icon is the other half of it. Every other page in the application
            leads its heading with one; these four led with the words alone, so
            the Approvals menu was the odd one out at a glance.
          -->
          <div class="flex align-items-center mb-4 flex-wrap gap-3">
            <div class="flex align-items-center gap-3 flex-1">
              <i class="pi pi-history text-primary" style="font-size: 2.5rem"></i>
              <div>
                <h2 class="m-0 text-primary">{{ 'APPROVAL.historyTitle' | translate }}</h2>
                <p class="text-600 mt-1 mb-0">{{ 'APPROVAL.historySubtitle' | translate }}</p>
              </div>
            </div>
            <span class="text-500">{{ totalRecords }} {{ 'APPROVAL.items' | translate }}</span>
          </div>
        
          <!--
            Filters. Shared layout: assets/layout/styles/_filter-bar.scss.

            A wrapping flex row before, with each control carrying its own rem
            width and the two buttons as further items in the same row.
          -->
          <div class="filter-bar">
            <div class="filter-bar__grid filter-bar__grid--cols-3">
            <div class="filter-bar__field" [class.filter-bar__field--active]="filters.requestType">
              <label for="ahType">{{ 'APPROVAL.type' | translate }}</label>
              <p-select inputId="ahType" [options]="requestTypeOptions" [(ngModel)]="filters.requestType"
                [placeholder]="'APPROVAL.type' | translate"
                [showClear]="true" optionLabel="label" optionValue="value"
                (onChange)="applyFilters()"></p-select>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filters.entityType">
              <label for="ahEntity">{{ 'APPROVAL.entityType' | translate }}</label>
              <input id="ahEntity" pInputText [(ngModel)]="filters.entityType"
                [placeholder]="'APPROVAL.entityType' | translate"
                (keydown.enter)="applyFilters()">
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filters.status">
              <label for="ahStatus">{{ 'APPROVAL.statusLabel' | translate }}</label>
              <p-select inputId="ahStatus" [options]="statusOptions" [(ngModel)]="filters.status"
                [placeholder]="'APPROVAL.statusLabel' | translate"
                [showClear]="true" optionLabel="label" optionValue="value"
                (onChange)="applyFilters()"></p-select>
            </div>
          </div>

            <div class="filter-bar__actions">
              <button pButton icon="pi pi-search" [label]="'COMMON.SEARCH' | translate"
                class="p-button-sm" (click)="applyFilters()"></button>
              <button pButton icon="pi pi-times" [label]="'COMMON.RESET' | translate"
                class="p-button-sm p-button-action-outlined"
                [disabled]="!filters.requestType &amp;&amp; !filters.entityType &amp;&amp; !filters.status" (click)="resetFilters()"></button>
            </div>
          </div>
        
          <p-table
            [value]="requests"
            [lazy]="true"
            [paginator]="true"
            [rows]="rows"
            [(first)]="first"
            [totalRecords]="totalRecords"
            [loading]="loading"
            (onLazyLoad)="loadRequests($event)"
            [rowsPerPageOptions]="[10, 25, 50]"
            styleClass="p-datatable-sm p-datatable-striped"
            responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th pSortableColumn="id">{{ 'APPROVAL.id' | translate }} <p-sortIcon field="id"></p-sortIcon></th>
                <th pSortableColumn="requestType">{{ 'APPROVAL.type' | translate }} <p-sortIcon field="requestType"></p-sortIcon></th>
                <th pSortableColumn="entityType">{{ 'APPROVAL.entityType' | translate }} <p-sortIcon field="entityType"></p-sortIcon></th>
                <th>{{ 'APPROVAL.entityName' | translate }}</th>
                <th>{{ 'APPROVAL.requester' | translate }}</th>
                <th pSortableColumn="status">{{ 'APPROVAL.statusLabel' | translate }} <p-sortIcon field="status"></p-sortIcon></th>
                <th pSortableColumn="requestedAt">{{ 'APPROVAL.requestedAt' | translate }} <p-sortIcon field="requestedAt"></p-sortIcon></th>
                <th pSortableColumn="resolvedAt">{{ 'APPROVAL.resolvedAt' | translate }} <p-sortIcon field="resolvedAt"></p-sortIcon></th>
                <th>{{ 'APPROVAL.actions' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-request>
              <tr>
                <td>{{ request.id }}</td>
                <td>
                  <p-tag
                    [value]="getRequestTypeLabel(request.requestType)"
                  [severity]="request.requestType | codeSeverity:'request-type'"></p-tag>
                </td>
                <td>{{ getEntityTypeLabel(request) }}</td>
                <td>{{ getEntityName(request) }}</td>
                <td>
                  {{ request.requesterFullName || request.requester?.fullName || request.requester?.username || request.requestedBy }}
                </td>
                <td>
                  <p-tag
                    [value]="approvalService.getStatusLabel(request.status)"
                  [severity]="request.status | codeSeverity:'approval-status'"></p-tag>
                </td>
                <td>{{ request.requestedAt | date: 'short' }}</td>
                <td>{{ resolvedAt(request) | date: 'short' }}</td>
                <td>
                  <button
                    pButton
                    icon="pi pi-eye"
                    class="p-button-text p-button-sm"
                    pTooltip="{{ 'APPROVAL.viewDetails' | translate }}"
                  (click)="viewDetails(request)"></button>
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr>
                <td colspan="9" class="text-center p-4">
                  <i class="pi pi-history text-4xl text-300 mb-3"></i>
                  <p class="text-500">{{ 'APPROVAL.noHistoryRequests' | translate }}</p>
                </td>
              </tr>
            </ng-template>
          </p-table>
        </div>
        
        <!-- View Details Dialog -->
        <p-dialog [(visible)]="showDetailsDialog"
          [modal]="true"
          [style]="{width: '82vw', maxWidth: '1140px'}"
          [maximizable]="true"
          [draggable]="false"
          [resizable]="false"
          styleClass="approval-detail-dialog"
          (onHide)="onDetailsHide()">
        
          <ng-template pTemplate="header">
            @if (selectedRequest) {
              <div class="flex align-items-center gap-3 w-full">
                <div class="approval-icon-badge" [ngClass]="'badge-' + (selectedRequest.requestType | lowercase)">
                        <i class="pi" [ngClass]="{
                            'pi-plus-circle': selectedRequest.requestType === 'INSERT',
                            'pi-pencil':      selectedRequest.requestType === 'UPDATE',
                            'pi-trash':       selectedRequest.requestType === 'DELETE'
                        }"></i>
                </div>
                <div class="flex-1">
                  <div class="text-900 font-bold text-lg line-height-1">
                    {{ 'APPROVAL.requestDetails' | translate }} #{{ selectedRequest.id }}
                  </div>
                  <div class="flex align-items-center gap-2 mt-1">
                    <p-tag [value]="getRequestTypeLabel(selectedRequest.requestType)"
                    [severity]="selectedRequest.requestType | codeSeverity:'request-type'"></p-tag>
                    <p-tag [value]="approvalService.getStatusLabel(selectedRequest.status)"
                    [severity]="selectedRequest.status | codeSeverity:'approval-status'"></p-tag>
                    <span class="text-500 text-sm">{{ getEntityTypeLabel(selectedRequest) }}</span>
                  </div>
                </div>
                <div class="text-right text-500 text-sm mr-2">
                  <div><i class="pi pi-clock mr-1"></i>{{ selectedRequest.requestedAt | date:'medium' }}</div>
                </div>
              </div>
            }
          </ng-template>
        
          @if (selectedRequest) {
            <div class="approval-detail-body">
              <!-- ── Section 1: Approval Progress ───────────────────────────── -->
              <div class="approval-section approval-section--progress">
                <div class="approval-section__title">
                  <i class="pi pi-list-check text-primary"></i>
                  {{ 'APPROVAL.approvalProgress' | translate }}
                  <span class="ml-auto text-500 text-sm font-normal">
                    {{ 'APPROVAL.currentLevel' | translate }}:
                    <strong class="text-900">{{ selectedRequest.currentLevel }}</strong>
                    / {{ selectedRequest.config?.totalLevels ?? '?' }}
                  </span>
                </div>
                <div class="level-track">
                  @for (n of getLevelRange(selectedRequest.config?.totalLevels); track n; let last = $last) {
                    <div class="level-node"
                      [class.level-node--done]="isLevelDone(n)"
                      [class.level-node--active]="selectedRequest.currentLevel === n && !isFinallyResolved()"
                      [class.level-node--rejected]="isLevelRejected(n)"
                      [class.level-node--pending]="!isLevelDone(n) && !isLevelRejected(n) && selectedRequest.currentLevel !== n">
                      <div class="level-node__circle">
                        @if (isLevelDone(n)) {
                          <i class="pi pi-check"></i>
                        }
                        @if (isLevelRejected(n)) {
                          <i class="pi pi-times"></i>
                        }
                        @if (!isLevelDone(n) && !isLevelRejected(n)) {
                          <span>{{ n }}</span>
                        }
                      </div>
                      <div class="level-node__label">{{ getLevelName(n) }}</div>
                    </div>
                    @if (!last) {
                      <div class="level-connector"
                      [class.level-connector--done]="isLevelDone(n)"></div>
                    }
                  }
                </div>
                <div class="level-progress-bar">
                  <div class="level-progress-bar__fill"
                    [class.level-progress-bar__fill--success]="selectedRequest.status === 'EXECUTED' || selectedRequest.status === 'APPROVED'"
                    [class.level-progress-bar__fill--danger]="selectedRequest.status === 'REJECTED'"
                    [style.width.%]="getProgressPercent(selectedRequest)">
                  </div>
                </div>
              </div>
              <!-- ── Section 2: Info Cards ──────────────────────────────────── -->
              <div class="grid mt-3">
                <div class="col-12 md:col-6">
                  <div class="approval-info-card">
                    <div class="approval-info-card__title">
                      <i class="pi pi-info-circle text-blue-500"></i>
                      {{ 'APPROVAL.requestInfo' | translate }}
                    </div>
                    <div class="approval-kv-grid">
                      <span class="approval-kv-label">ID</span>
                      <span class="approval-kv-value font-mono">#{{ selectedRequest.id }}</span>
                      <span class="approval-kv-label">{{ 'APPROVAL.type' | translate }}</span>
                      <span class="approval-kv-value">
                        <p-tag [value]="getRequestTypeLabel(selectedRequest.requestType)"
                        [severity]="selectedRequest.requestType | codeSeverity:'request-type'"></p-tag>
                      </span>
                      <span class="approval-kv-label">{{ 'APPROVAL.entityType' | translate }}</span>
                      <span class="approval-kv-value">{{ getEntityTypeLabel(selectedRequest) }}</span>
                      @if (isStudentExtension(selectedRequest)) {
                        <span class="approval-kv-label">{{ 'APPROVAL.studentId' | translate }}</span>
                        <span class="approval-kv-value font-mono">{{ getStudentId(selectedRequest) }}</span>
                        <span class="approval-kv-label">{{ 'APPROVAL.studentName' | translate }}</span>
                        <span class="approval-kv-value font-semibold">{{ getStudentName(selectedRequest) }}</span>
                      }
                      <span class="approval-kv-label">{{ 'APPROVAL.entityName' | translate }}</span>
                      <span class="approval-kv-value">{{ getEntityName(selectedRequest) }}</span>
                      <span class="approval-kv-label">{{ 'APPROVAL.statusLabel' | translate }}</span>
                      <span class="approval-kv-value">
                        <p-tag [value]="approvalService.getStatusLabel(selectedRequest.status)"
                        [severity]="selectedRequest.status | codeSeverity:'approval-status'"></p-tag>
                      </span>
                      @if (selectedRequest.parentApprovalRequestId) {
                        <span class="approval-kv-label">{{ 'APPROVAL.parentRequestId' | translate }}</span>
                        <span class="approval-kv-value font-mono">#{{ selectedRequest.parentApprovalRequestId }}</span>
                      }
                    </div>
                  </div>
                </div>
                <div class="col-12 md:col-6">
                  <div class="approval-info-card">
                    <div class="approval-info-card__title">
                      <i class="pi pi-user text-purple-500"></i>
                      {{ 'APPROVAL.requester' | translate }}
                    </div>
                    <div class="approval-kv-grid">
                      <span class="approval-kv-label">{{ 'APPROVAL.requester' | translate }}</span>
                      <span class="approval-kv-value font-semibold">
                        {{ selectedRequest.requesterFullName || selectedRequest.requester?.fullName || selectedRequest.requester?.username || selectedRequest.requestedBy }}
                      </span>
                      <span class="approval-kv-label">{{ 'APPROVAL.requestedAt' | translate }}</span>
                      <span class="approval-kv-value">{{ selectedRequest.requestedAt | date:'medium' }}</span>
                      @if (selectedRequest.executedAt) {
                        <span class="approval-kv-label">{{ 'APPROVAL.executedAt' | translate }}</span>
                        <span class="approval-kv-value text-green-700">{{ selectedRequest.executedAt | date:'medium' }}</span>
                      }
                      @if (selectedRequest.rejectedAt) {
                        <span class="approval-kv-label">{{ 'APPROVAL.rejectedAt' | translate }}</span>
                        <span class="approval-kv-value text-red-600">{{ selectedRequest.rejectedAt | date:'medium' }}</span>
                      }
                      @if (selectedRequest.rejectionReason) {
                        <span class="approval-kv-label">{{ 'APPROVAL.rejectionReason' | translate }}</span>
                        <span class="approval-kv-value text-red-700 italic">{{ selectedRequest.rejectionReason }}</span>
                      }
                    </div>
                  </div>
                </div>
              </div>
              <!-- ── Section 3: Action History Timeline ─────────────────────── -->
              <div class="approval-section mt-3">
                <div class="approval-section__title">
                  <i class="pi pi-history text-orange-500"></i>
                  {{ 'APPROVAL.actionHistory' | translate }}
                </div>
                @if (historyLoading) {
                  <div class="flex justify-content-center p-4">
                    <p-progressSpinner styleClass="w-3rem h-3rem"></p-progressSpinner>
                  </div>
                }
                @if (!historyLoading && historyEntries.length === 0) {
                  <div class="text-500 text-sm p-2">
                    {{ 'APPROVAL.noActionHistory' | translate }}
                  </div>
                }
                @if (!historyLoading && historyEntries.length > 0) {
                  <div class="steps-timeline">
                    @for (h of historyEntries; track h) {
                      <div class="step-row"
                        [class.step-row--approved]="h.action === 'APPROVED' || h.action === 'EXECUTED'"
                        [class.step-row--rejected]="h.action === 'REJECTED'"
                        [class.step-row--pending]="h.action === 'CREATED' || h.action === 'SUBMITTED'">
                        <div class="step-row__icon">
                                <i class="pi" [ngClass]="{
                                    'pi-check':     h.action === 'APPROVED' || h.action === 'EXECUTED',
                                    'pi-times':     h.action === 'REJECTED',
                                    'pi-file-plus': h.action === 'CREATED' || h.action === 'SUBMITTED',
                                    'pi-sync':      h.action !== 'APPROVED' && h.action !== 'EXECUTED' && h.action !== 'REJECTED' && h.action !== 'CREATED' && h.action !== 'SUBMITTED'
                                }"></i>
                        </div>
                        <div class="step-row__body">
                          <div class="step-row__header">
                            <span class="font-semibold">{{ approvalService.getActionLabel(h.action) }}</span>
                            @if (h.levelOrder != null) {
                              <span class="text-500 text-sm">
                                — {{ 'APPROVAL.level' | translate }} {{ h.levelOrder }}
                              </span>
                            }
                            @if (h.oldStatus || h.newStatus) {
                              <span class="text-500 text-xs ml-auto">
                                {{ h.oldStatus ? approvalService.getStatusLabel(h.oldStatus) : '—' }}
                                → {{ h.newStatus ? approvalService.getStatusLabel(h.newStatus) : '—' }}
                              </span>
                            }
                          </div>
                          <div class="step-row__meta text-500 text-sm">
                            @if (h.actor) {
                              <span>{{ h.actor?.firstName }} {{ h.actor?.lastName }}</span>
                            }
                            @if (!h.actor && h.actionBy) {
                              <span> ID:{{ h.actionBy }}</span>
                            }
                            <span> &bull; {{ h.actionAt | date:'medium' }}</span>
                          </div>
                          @if (h.details) {
                            <div class="step-row__comment text-600 text-sm mt-1 italic">
                              "{{ approvalService.localizeHistoryDetails(h.details) }}"
                            </div>
                          }
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>
              <!--
              * Rendered by the shared component rather than a local diff of requestData:
              * a request carrying uploads stores an envelope ({studentData, …}), so the
              * old table reported every real field as emptied and the envelope keys as
              * added. This one uses a server-computed diff and also shows the photo and
              * documents involved.
              -->
              <!-- ── Travel Ticket cost summary ─────────────────────────────── -->
              @if (isTravelTicket(selectedRequest)) {
                <div class="approval-section mt-3">
                  <div class="approval-section__title">
                    <i class="pi pi-money-bill text-green-500"></i>
                    {{ 'APPROVAL.travelTicketCosts' | translate }}
                  </div>
                  <div class="approval-kv-grid ticket-meta-grid">
                    <span class="approval-kv-label">{{ 'STUDENT_TRAVEL_TICKETS.FORM.CURRENCY' | translate }}</span>
                    <span class="approval-kv-value font-semibold">{{ getTicketCurrencyName(selectedRequest) }}</span>
                    <span class="approval-kv-label">{{ 'STUDENT_TRAVEL_TICKETS.FORM.FUNDING_SOURCE' | translate }}</span>
                    <span class="approval-kv-value font-semibold">{{ getTicketFundingSourceName(selectedRequest) }}</span>
                  </div>
                  <table class="ticket-cost-table">
                    <thead>
                      <tr>
                        <th>{{ 'APPROVAL.beneficiary' | translate }}</th>
                        <th class="text-right">{{ 'STUDENT_TRAVEL_TICKETS.FORM.TICKET_COST' | translate }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td><i class="pi pi-user mr-2 text-primary"></i>{{ getStudentName(selectedRequest) }}</td>
                        <td class="text-right font-mono">{{ getTicketStudentCost(selectedRequest) | number:'1.2-2' }}</td>
                      </tr>
                      @for (m of getTicketFamilyMembers(selectedRequest); track m) {
                        <tr>
                          <td><i class="pi pi-users mr-2 text-500"></i>{{ m.memberName }}
                          @if (m.relationship) {
                            <span class="text-500 text-sm">({{ m.relationship }})</span>
                          }
                        </td>
                        <td class="text-right font-mono">{{ (m.ticketCost || 0) | number:'1.2-2' }}</td>
                      </tr>
                    }
                  </tbody>
                  <tfoot>
                    <tr>
                      <td class="font-bold">{{ 'STUDENT_TRAVEL_TICKETS.FORM.TOTAL_COST' | translate }}</td>
                      <td class="text-right font-bold ticket-total">
                        {{ getTicketTotal(selectedRequest) | number:'1.2-2' }}
                        <span class="text-500 text-sm ml-1">{{ getTicketCurrencyName(selectedRequest) }}</span>
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            }
            <div class="approval-section mt-3">
              <div class="approval-section__title">
                <i class="pi pi-sliders-h text-primary"></i>
                {{ 'APPROVAL.CHANGED_DATA' | translate }}
              </div>
              <app-approval-changes [requestId]="selectedRequest.id"></app-approval-changes>
            </div>
          </div>
        }
        
        <ng-template pTemplate="footer">
          <button pButton icon="pi pi-times" [label]="'COMMON.CLOSE' | translate"
            class="p-button-text p-button-secondary"
          (click)="showDetailsDialog = false"></button>
        </ng-template>
        </p-dialog>
        `,
    styles: [`
        :host ::ng-deep .p-datatable .p-datatable-tbody > tr > td { padding: 0.5rem 1rem; }

        .step-row--pending .step-row__icon { background: var(--blue-100); color: var(--blue-600); }

        .step-row__header { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    `],
    standalone: false
})
export class ApprovalHistoryComponent implements OnInit {
    requests: ApprovalRequest[] = [];
    loading = false;
    totalRecords = 0;
    rows = 10;
    page = 0;
    /** Bound to the table, so a filter that reloads page one moves the paginator there too (QA B8, N9). */
    first = 0;

    selectedRequest: ApprovalRequest | null = null;
    showDetailsDialog = false;
    historyEntries: ApprovalHistory[] = [];
    historyLoading = false;

    private fieldLookup: Record<string, { id: any; name: string }[]> = {};
    private lookupsLoaded = false;
    private travelFundingSources: TravelFundingSource[] = [];

    filters = { requestType: '', entityType: '', status: '' };
    requestTypeOptions: Array<{ label: string; value: string }> = [];
    statusOptions: Array<{ label: string; value: string }> = [];

    // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        public approvalService: ApprovalService,
        private messageService: MessageService,
        private translationService: TranslationService,
        private lookupService: LookupService,
        private enumOptionsService: EnumOptionsService,
        private travelTicketService: StudentTravelTicketService,
        private codeLists: CodeListService
    ) {}

    ngOnInit(): void {
        this.loadEnumOptions();
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadEnumOptions());
        this.travelTicketService.getFundingSources().subscribe({
            next: (sources) => { this.travelFundingSources = sources || []; },
            error: () => { /* raw id remains as fallback */ }
        });
        this.loadRequests({ first: 0, rows: this.rows });
    }

    private loadEnumOptions(): void {
        this.enumOptionsService.getDropdownOptions('request-type').subscribe(opts => {
            this.requestTypeOptions = opts;
        });
        this.enumOptionsService.getDropdownOptions('approval-status').subscribe(opts => {
            this.statusOptions = opts.filter(o =>
                ['APPROVED', 'EXECUTED', 'REJECTED'].includes(o.value)
            );
        });
    }

    loadRequests(event: { first: number; rows: number }): void {
        this.loading = true;
        followPage(this, event);

        const f = this.filters;
        const rt = f.requestType || undefined;
        const et = f.entityType?.trim() || undefined;
        const st = f.status || undefined;
        this.approvalService.getCompletedHistory(this.page, this.rows, 'requestedAt', 'desc', { requestType: rt, entityType: et, status: st }).subscribe({
            next: (response) => {
                this.requests = response.content;
                this.totalRecords = response.totalElements;
                this.loading = false;
            },
            error: (err) => {
                console.error('Error loading approval history:', err);
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: err?.error?.message || err?.message || this.translationService.translate('APPROVAL.loadError'),
                });
                this.loading = false;
            },
        });
    }

    resolvedAt(request: ApprovalRequest): string | null {
        if (request.status === 'REJECTED' && request.rejectedAt) {
            return request.rejectedAt;
        }
        if (request.executedAt) {
            return request.executedAt;
        }
        if (request.status === 'APPROVED' && request.requestedAt) {
            return request.requestedAt;
        }
        return null;
    }

    viewDetails(request: ApprovalRequest): void {
        this.selectedRequest = request;
        this.showDetailsDialog = true;
        this.historyEntries = [];
        this.historyLoading = true;
        this.ensureLookupsLoaded();
        this.approvalService.getRequestHistory(request.id).subscribe({
            next: (rows) => {
                this.historyEntries = [...rows].sort(
                    (a, b) => new Date(b.actionAt).getTime() - new Date(a.actionAt).getTime()
                );
                this.historyLoading = false;
            },
            error: () => {
                this.historyLoading = false;
                this.messageService.add({
                    severity: 'warn',
                    summary: this.translationService.translate('APPROVAL.actionHistory'),
                    detail: this.translationService.translate('APPROVAL.failedLoadHistory'),
                });
            },
        });
    }

    onDetailsHide(): void {
        this.selectedRequest = null;
        this.historyEntries = [];
    }

    prettyJson(raw: string | undefined): string {
        if (raw == null || raw === '') {
            return '';
        }
        try {
            return JSON.stringify(JSON.parse(raw), null, 2);
        } catch {
            return raw;
        }
    }

    getRequestTypeLabel(type: string): string {
        return this.approvalService.getRequestTypeLabel(type);
    }


    getLevelRange(total?: number): number[] {
        const n = total ?? 1;
        return Array.from({ length: n }, (_, i) => i + 1);
    }

    getLevelName(levelOrder: number): string {
        const step = this.selectedRequest?.steps?.find((s: any) => s.levelOrder === levelOrder);
        return step?.level?.levelName ?? `${this.translationService.translate('APPROVAL.level')} ${levelOrder}`;
    }

    getProgressPercent(request?: any): number {
        request = request ?? this.selectedRequest;
        if (!request) return 0;
        const total = request.config?.totalLevels || 1;
        // Fully resolved fills the bar; a rejection fills up to and including the level it stopped at.
        if (request.status === 'EXECUTED' || request.status === 'APPROVED') return 100;
        if (request.status === 'REJECTED') return Math.round((request.currentLevel / total) * 100);
        return Math.round(((request.currentLevel - 1) / total) * 100);
    }

    isLevelDone(n: number): boolean {
        const req = this.selectedRequest;
        if (!req) return false;
        if (req.status === 'EXECUTED' || req.status === 'APPROVED') return true;
        const step = req.steps?.find((st: any) => st.levelOrder === n);
        if (step?.status === 'APPROVED') return true;
        // Per-step status is not always populated; any level before the current one was cleared.
        return n < (req.currentLevel ?? 1);
    }

    isLevelRejected(n: number): boolean {
        const req = this.selectedRequest;
        if (!req) return false;
        const step = req.steps?.find((st: any) => st.levelOrder === n);
        if (step?.status === 'REJECTED') return true;
        // Reliable fallback: a rejected request stopped at its current level.
        return req.status === 'REJECTED' && n === (req.currentLevel ?? 1);
    }

    isFinallyResolved(): boolean {
        const s = this.selectedRequest?.status;
        return s === 'EXECUTED' || s === 'APPROVED' || s === 'REJECTED';
    }




    private toEntries(arr: any[]): { id: any; name: string }[] {
        return (arr || []).map(item => ({
            id: item.id,
            name: item.name
                ?? item.studyFinanceBankeName
                ?? item.studyFinanceBankBranchName
                ?? item.studyFinanceDofName
                ?? item.studyFinReceivedStatusName
                ?? item.studyReceivedWhomStatusName
                ?? String(item.id ?? '?')
        }));
    }

    private ensureLookupsLoaded(): void {
        if (this.lookupsLoaded) return;
        forkJoin({
            genders:             this.lookupService.getStudentGenders(),
            countries:           this.lookupService.getCountries(),
            govs:                this.lookupService.getGovernments(),
            munis:               this.lookupService.getMunicipalities(),
            degrees:             this.lookupService.getStudentDegrees(),
            majors:              this.lookupService.getStudentMajors(),
            levels:              this.lookupService.getCurrentLevels(),
            studyStatuses:       this.lookupService.getStudentStudyStatuses(),
            agencyTypes:         this.lookupService.getAgencyTypes(),
            ministryTypes:       this.lookupService.getMinistryTypes(),
            studyTypes:          this.lookupService.getStudentStudyTypes(),
            uniTypes:            this.lookupService.getUniversityTypes(),
            studyCountries:      this.lookupService.getStudyCountriesIn(),
            studyGovs:           this.lookupService.getStudyGovsIn(),
            studyUnis:           this.lookupService.getStudyUniversitiesIn(),
            studyColleges:       this.lookupService.getStudyCollegesIn(),
            studyEdus:           this.lookupService.getStudyEducationsIn(),
            studyMajors:         this.lookupService.getStudyMajorsIn(),
            currencies:          this.lookupService.getStudyCurrencies(),
            banks:               this.lookupService.getAllStudyFinanceBanks(),
            bankBranches:        this.lookupService.getAllStudyFinanceBankBranches(),
            dofs:                this.lookupService.getAllStudyFinanceDofs(),
            finStatuses:         this.lookupService.getAllStudyFinReceivedStatuses(),
            receivedWhomStatuses:this.lookupService.getAllStudyReceivedWhomStatuses(),
            wafarTypes:          this.lookupService.getWafarTypes(),
        }).subscribe(d => {
            const e = (arr: any[]) => this.toEntries(arr);
            this.fieldLookup = {
                studentGender:               e(d.genders),
                studentCountryId:            e(d.countries),
                studentGovId:                e(d.govs),
                studentMudId:                e(d.munis),
                studentDegreeId:             e(d.degrees),
                studentMajorId:              e(d.majors),
                studentCurrentLevelId:       e(d.levels),
                studentStudyStatusId:        e(d.studyStatuses),
                studentAgencyTypeId:         e(d.agencyTypes),
                studentMinistryTypeId:       e(d.ministryTypes),
                studentStudyTypeId:          e(d.studyTypes),
                studentUniversityTypeId:     e(d.uniTypes),
                studentPassportIssueCountryId: e(d.countries),
                studyCountryIn:              e(d.studyCountries),
                syudyGovIn:                  e(d.studyGovs),
                studyUniversityIn:           e(d.studyUnis),
                studyCollegeIn:              e(d.studyColleges),
                studyEducationIn:            e(d.studyEdus),
                studyMajorIn:                e(d.studyMajors),
                amountCurrency:              e(d.currencies),
                universityFeeStatusCurrency: e(d.currencies),
                studyCountryInId:            e(d.studyCountries),
                studyGovInId:                e(d.studyGovs),
                studyFinanceBankeId:         e(d.banks),
                studentBankId:               e(d.banks),
                studyFinanceBankBranchId:    e(d.bankBranches),
                studyFinanceDofId:           e(d.dofs),
                studyFinRecievedStatus:      e(d.finStatuses),
                studyReceivedWhomStatusId:   e(d.receivedWhomStatuses),
                wafarTypeId:                 e(d.wafarTypes),
            };
            this.lookupsLoaded = true;
        });
    }

    resolveLookup(key: string, val: any): string | null {
        const list = this.fieldLookup[key];
        if (!list || val == null) return null;
        // eslint-disable-next-line eqeqeq
        const found = list.find(item => item.id == val);
        return found ? found.name : null;
    }


    private extractObjectName(obj: any): string {
        if (!obj || typeof obj !== 'object') return String(obj ?? '?');
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        const keys = Object.keys(obj);
        const enKey = keys.find(k => /NameEn$/i.test(k));
        const arKey = keys.find(k => /Name$/i.test(k) && !/NameEn$/i.test(k));
        const pick = (primary?: string, fallback?: string) =>
            (primary ? obj[primary] : null) ?? (fallback ? obj[fallback] : null) ?? obj['name'] ?? String(obj['id'] ?? '?');
        return isEn ? pick(enKey, arKey) : pick(arKey, enKey);
    }

    applyFilters(): void {
        this.loadRequests({ first: 0, rows: this.rows });
    }

    resetFilters(): void {
        this.filters = { requestType: '', entityType: '', status: '' };
        this.applyFilters();
    }

    /** Shared with the other approval screens: see approval-entity-label.ts. */
    getEntityTypeLabel(request: ApprovalRequest): string {
        return entityTypeLabel(this.codeLists, request, this.translationService.getCurrentLanguage() !== 'en');
    }

    getEntityName(request: ApprovalRequest): string {
        return entityName(request);
    }

    /** Entity types whose payload carries a studentId/studentName worth showing. */
    isStudentExtension(request: ApprovalRequest): boolean {
        const t = request?.entityType;
        return t === 'STUDENT_EXTENSION' || t === 'STUDENT_TRAVEL_TICKET';
    }

    /**
     * Student the extension request is for. Prefers the name stamped into the payload; for
     * older requests that only stored studentId it falls back to "#<id>" so the row is never
     * blank.
     */
    /** Unwrap the request payload (onboarding wraps the record under studentData). */
    private parseStudentPayload(request: ApprovalRequest): any {
        const parse = (raw?: string | null): any => {
            if (!raw) return null;
            try {
                let d: any = JSON.parse(raw);
                if (d && d.studentData) d = d.studentData;
                return d;
            } catch {
                return null;
            }
        };
        return parse(request?.requestData) ?? parse(request?.originalData);
    }

    /** Student ID the extension request is for. */
    getStudentId(request: ApprovalRequest): string {
        const sid = this.parseStudentPayload(request)?.studentId;
        return sid != null ? String(sid) : (this.translationService.translate('COMMON.NOT_AVAILABLE') || '-');
    }

    /**
     * Student name the extension request is for. Uses the name stamped into the payload; older
     * requests that only stored studentId show N/A until backfilled.
     */
    getStudentName(request: ApprovalRequest): string {
        const name = this.parseStudentPayload(request)?.studentName;
        return name || (this.translationService.translate('COMMON.NOT_AVAILABLE') || '-');
    }

    // ── Travel ticket cost helpers ────────────────────────────────────────────

    isTravelTicket(request: ApprovalRequest): boolean {
        return request?.entityType === 'STUDENT_TRAVEL_TICKET';
    }

    getTicketStudentCost(request: ApprovalRequest): number {
        return Number(this.parseStudentPayload(request)?.ticketCost) || 0;
    }

    getTicketFamilyMembers(request: ApprovalRequest): any[] {
        const members = this.parseStudentPayload(request)?.familyMembers;
        return Array.isArray(members) ? members : [];
    }

    /** Prefer the stored total; fall back to student cost + Σ member costs for older payloads. */
    getTicketTotal(request: ApprovalRequest): number {
        const ticket = this.parseStudentPayload(request);
        if (ticket?.totalTicketCost != null) return Number(ticket.totalTicketCost) || 0;
        const members = this.getTicketFamilyMembers(request)
            .reduce((sum, m) => sum + (Number(m.ticketCost) || 0), 0);
        return (Number(ticket?.ticketCost) || 0) + members;
    }

    getTicketCurrencyName(request: ApprovalRequest): string {
        const id = this.parseStudentPayload(request)?.currencyId;
        if (id == null) return '-';
        return this.resolveLookup('amountCurrency', id) ?? String(id);
    }

    getTicketFundingSourceName(request: ApprovalRequest): string {
        const id = this.parseStudentPayload(request)?.fundingSourceId;
        if (id == null) return '-';
        const fs = this.travelFundingSources.find(f => f.id === id);
        if (!fs) return String(id);
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        return (isEn ? (fs.nameEn || fs.name) : (fs.nameAr || fs.name)) || fs.name || String(id);
    }
}
