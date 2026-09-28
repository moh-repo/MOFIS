import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { ApprovalService, ApprovalRequest } from '../../../service/approval.service';
import { MessageService } from 'primeng/api';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { AuthService } from '../../../service/auth.service';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { StudentTravelTicketService } from '../../../service/student-travel-ticket.service';
import { TravelFundingSource } from '../../../api/student-travel-ticket';
import { CodeListService } from '../../../service/code-list.service';
import { entityName, entityTypeLabel } from '../approval-entity-label';

@Component({
    selector: 'app-pending-approvals',
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
              <i class="pi pi-inbox text-primary" style="font-size: 2.5rem"></i>
              <div>
                <h2 class="m-0 text-primary">{{ 'APPROVAL.pending' | translate }}</h2>
                <p class="text-600 mt-1 mb-0">{{ 'APPROVAL.pendingSubtitle' | translate }}</p>
              </div>
            </div>
            <p-selectButton [options]="scopeOptions" [(ngModel)]="scope" optionLabel="label" optionValue="value"
              [allowEmpty]="false" (onChange)="applyFilters()" styleClass="p-button-sm"></p-selectButton>
            <span class="text-500">{{ totalRecords }} {{ 'APPROVAL.items' | translate }}</span>
          </div>
        
          <!--
            Filters. Shared layout: assets/layout/styles/_filter-bar.scss.

            A wrapping flex row before, with each control carrying its own rem
            width and the two buttons as further items in the same row.
          -->
          <div class="filter-bar">
            <div class="filter-bar__grid filter-bar__grid--cols-2">
            <div class="filter-bar__field" [class.filter-bar__field--active]="filters.requestType">
              <label for="paType">{{ 'APPROVAL.type' | translate }}</label>
              <p-select inputId="paType" [options]="requestTypeOptions" [(ngModel)]="filters.requestType"
                [placeholder]="'APPROVAL.type' | translate"
                [showClear]="true" optionLabel="label" optionValue="value"
                (onChange)="applyFilters()"></p-select>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filters.entityType">
              <label for="paEntity">{{ 'APPROVAL.entityType' | translate }}</label>
              <input id="paEntity" pInputText [(ngModel)]="filters.entityType"
                [placeholder]="'APPROVAL.entityType' | translate"
                (keydown.enter)="applyFilters()">
            </div>
          </div>

            <div class="filter-bar__actions">
              <button pButton icon="pi pi-search" [label]="'COMMON.SEARCH' | translate"
                class="p-button-sm" (click)="applyFilters()"></button>
              <button pButton icon="pi pi-times" [label]="'COMMON.RESET' | translate"
                class="p-button-sm p-button-action-outlined"
                [disabled]="!filters.requestType &amp;&amp; !filters.entityType" (click)="resetFilters()"></button>
            </div>
          </div>
        
          <p-table
            [value]="requests"
            [lazy]="true"
            [paginator]="true"
            [rows]="rows"
            [first]="first"
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
                <th>{{ 'APPROVAL.requester' | translate }}</th>
                <th pSortableColumn="currentLevel">{{ 'APPROVAL.currentLevel' | translate }} <p-sortIcon field="currentLevel"></p-sortIcon></th>
                <th pSortableColumn="status">{{ 'APPROVAL.statusLabel' | translate }} <p-sortIcon field="status"></p-sortIcon></th>
                <th pSortableColumn="requestedAt">{{ 'APPROVAL.requestedAt' | translate }} <p-sortIcon field="requestedAt"></p-sortIcon></th>
                <th>{{ 'APPROVAL.actions' | translate }}</th>
              </tr>
            </ng-template>
        
            <ng-template pTemplate="body" let-request>
              <tr>
                <td>{{ request.id }}</td>
                <td>
                  <p-tag [value]="getRequestTypeLabel(request.requestType)"
                  [severity]="request.requestType | codeSeverity:'request-type'"></p-tag>
                </td>
                <td>{{ getEntityTypeLabel(request) }}</td>
                <td>{{ request.requesterFullName || request.requester?.fullName || request.requester?.username || request.requestedBy }}</td>
                <td>
                  <span class="font-bold">{{ request.currentLevel }}</span> / {{ request.config?.totalLevels }}
                </td>
                <td>
                  <p-tag [value]="approvalService.getStatusLabel(request.status)"
                  [severity]="request.status | codeSeverity:'approval-status'"></p-tag>
                </td>
                <td>{{ request.requestedAt | date:'short' }}</td>
                <td>
                  <div class="flex gap-2">
                    <button pButton icon="pi pi-eye"
                      class="p-button-text p-button-sm"
                      pTooltip="{{ 'APPROVAL.viewDetails' | translate }}"
                    (click)="viewDetails(request)"></button>
                    <!-- The server's decision per request (FIX-PLAN D3-a): the chain decides who signs a
                         level, so a Student Officer or an Employee approves without APP_PENDING.APPROVE
                         (QA #61), and a view-only account cannot approve a level that names nobody (H5). -->
                    @if (request.canApprove) {
                      <button pButton icon="pi pi-check"
                        class="p-button-success p-button-text p-button-sm"
                        pTooltip="{{ 'APPROVAL.approve' | translate }}"
                      (click)="openApproveDialog(request)"></button>
                    }
                    @if (request.canReject) {
                      <button pButton icon="pi pi-times"
                        class="p-button-danger p-button-text p-button-sm"
                        pTooltip="{{ 'APPROVAL.reject' | translate }}"
                      (click)="openRejectDialog(request)"></button>
                    }
                  </div>
                </td>
              </tr>
            </ng-template>
        
            <ng-template pTemplate="emptymessage">
              <tr>
                <td colspan="8" class="text-center p-4">
                  @if (loadFailed) {
                    <i class="pi pi-exclamation-triangle text-4xl text-300 mb-3"></i>
                    <p class="text-500 load-failed">{{ 'APPROVAL.loadFailed' | translate }}</p>
                    <button pButton icon="pi pi-refresh" class="p-button-outlined p-button-sm retry-load"
                      [label]="'COMMON.RETRY' | translate"
                    (click)="loadRequests({ first: first, rows: rows })"></button>
                  } @else {
                    <i class="pi pi-inbox text-4xl text-300 mb-3"></i>
                    <p class="text-500">{{ 'APPROVAL.noPendingRequests' | translate }}</p>
                  }
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
          styleClass="approval-detail-dialog">
        
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
                <!-- Visual step track -->
                <div class="level-track">
                  @for (n of getLevelRange(selectedRequest.config?.totalLevels); track n; let last = $last) {
                    <!-- Step circle -->
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
                    <!-- Connector line -->
                    @if (!last) {
                      <div class="level-connector"
                      [class.level-connector--done]="isLevelDone(n)"></div>
                    }
                  }
                </div>
                <!-- Progress bar -->
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
                <!-- Request Info card -->
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
                    </div>
                  </div>
                </div>
                <!-- Requester Info card -->
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
                      @if (selectedRequest.rejectedBy) {
                        <span class="approval-kv-label">{{ 'APPROVAL.rejectedBy' | translate }}</span>
                        <span class="approval-kv-value text-red-600">{{ selectedRequest.rejectedBy }}</span>
                        <span class="approval-kv-label">{{ 'APPROVAL.rejectedAt' | translate }}</span>
                        <span class="approval-kv-value">{{ selectedRequest.rejectedAt | date:'medium' }}</span>
                      }
                      @if (selectedRequest.rejectionReason) {
                        <span class="approval-kv-label">{{ 'APPROVAL.rejectionReason' | translate }}</span>
                        <span class="approval-kv-value text-red-700 italic">{{ selectedRequest.rejectionReason }}</span>
                      }
                    </div>
                  </div>
                </div>
              </div>
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
            <!-- ── Section 3: What this request changes ───────────────────── -->
            <div class="approval-section mt-3">
              <div class="approval-section__title">
                <i class="pi pi-sliders-h text-primary"></i>
                {{ 'APPROVAL.CHANGED_DATA' | translate }}
              </div>
              <app-approval-changes [requestId]="selectedRequest.id"></app-approval-changes>
            </div>
            <!-- ── Section 4: Approval Steps History ─────────────────────── -->
            @if (selectedRequest.steps && selectedRequest.steps.length > 0) {
              <div class="approval-section mt-3">
                <div class="approval-section__title">
                  <i class="pi pi-history text-orange-500"></i>
                  {{ 'APPROVAL.stepsHistory' | translate }}
                </div>
                <div class="steps-timeline">
                  @for (step of selectedRequest.steps; track step) {
                    <div class="step-row"
                      [class.step-row--approved]="step.status === 'APPROVED'"
                      [class.step-row--rejected]="step.status === 'REJECTED'"
                      [class.step-row--pending]="step.status === 'PENDING'">
                      <div class="step-row__icon">
                                <i class="pi" [ngClass]="{
                                    'pi-check': step.status === 'APPROVED',
                                    'pi-times': step.status === 'REJECTED',
                                    'pi-clock': step.status === 'PENDING'
                                }"></i>
                      </div>
                      <div class="step-row__body">
                        <div class="step-row__header">
                          <span class="font-semibold">{{ step.level?.levelName || ('APPROVAL.level' | translate) + ' ' + step.levelOrder }}</span>
                          <p-tag [value]="approvalService.getStatusLabel(step.status)"
                            [severity]="step.status | codeSeverity:'approval-status'"
                          styleClass="text-xs"></p-tag>
                        </div>
                        @if (step.approvedAt || step.approver) {
                          <div class="step-row__meta text-500 text-sm">
                            @if (step.approver) {
                              <span>{{ step.approver?.fullName || step.approver?.username }}</span>
                            }
                            @if (step.approvedAt) {
                              <span> &bull; {{ step.approvedAt | date:'mediumDate' }}</span>
                            }
                          </div>
                        }
                        @if (step.comments) {
                          <div class="step-row__comment text-600 text-sm mt-1 italic">
                            "{{ step.comments }}"
                          </div>
                        }
                      </div>
                    </div>
                  }
                </div>
              </div>
            }
            <!--
            * The former "proposed changes" table was removed: it diffed the raw
            * requestData, which for a request carrying uploads is an envelope
            * ({studentData, pendingUploadSessionId}). Every real field therefore
            * looked emptied and the envelope keys looked added. Section 3 above
            * renders the same information from a server-computed diff that
            * understands the envelope.
            -->
          </div>
        }
        
        <ng-template pTemplate="footer">
          <div class="flex justify-content-between align-items-center w-full">
            <button pButton icon="pi pi-times" [label]="'COMMON.CLOSE' | translate"
              class="p-button-text p-button-secondary"
            (click)="showDetailsDialog = false"></button>
            <div class="flex gap-2">
              @if (selectedRequest?.canApprove && selectedRequest?.status !== 'APPROVED' && selectedRequest?.status !== 'REJECTED') {
                <button
                  pButton icon="pi pi-check" [label]="'APPROVAL.approve' | translate"
                  class="p-button-success"
                (click)="showDetailsDialog = false; openApproveDialog(selectedRequest!)"></button>
              }
              @if (selectedRequest?.canReject && selectedRequest?.status !== 'APPROVED' && selectedRequest?.status !== 'REJECTED') {
                <button
                  pButton icon="pi pi-times" [label]="'APPROVAL.reject' | translate"
                  class="p-button-danger"
                (click)="showDetailsDialog = false; openRejectDialog(selectedRequest!)"></button>
              }
            </div>
          </div>
        </ng-template>
        </p-dialog>
        
        <!-- Approve Dialog -->
        <p-dialog [(visible)]="showApproveDialog"
          [header]="'APPROVAL.confirmApproval' | translate"
          [modal]="true"
          [style]="{width: '450px'}">
          <div class="mb-3">
            <label for="approveComments" class="block mb-2">{{ 'APPROVAL.comments' | translate }}</label>
            <textarea pInputTextarea id="approveComments"
              [(ngModel)]="approveComments"
              rows="3"
              class="w-full"
            [placeholder]="'APPROVAL.commentsPlaceholder' | translate"></textarea>
          </div>
          <ng-template pTemplate="footer">
            <button pButton label="{{ 'common.cancel' | translate }}"
              class="p-button-text"
            (click)="showApproveDialog = false"></button>
            <button pButton label="{{ 'APPROVAL.approve' | translate }}"
              class="p-button-success"
              icon="pi pi-check"
              [loading]="approving"
            (click)="confirmApprove()"></button>
          </ng-template>
        </p-dialog>
        
        <!-- Reject Dialog -->
        <p-dialog [(visible)]="showRejectDialog"
          [header]="'APPROVAL.confirmRejection' | translate"
          [modal]="true"
          [style]="{width: '450px'}">
          <div class="mb-3">
            <label for="rejectReason" class="block mb-2">{{ 'APPROVAL.rejectionReason' | translate }} *</label>
            <textarea pInputTextarea id="rejectReason"
              [(ngModel)]="rejectReason"
              rows="3"
              class="w-full"
            [placeholder]="'APPROVAL.rejectionReasonPlaceholder' | translate"></textarea>
          </div>
          <ng-template pTemplate="footer">
            <button pButton label="{{ 'common.cancel' | translate }}"
              class="p-button-text"
            (click)="showRejectDialog = false"></button>
            <button pButton label="{{ 'APPROVAL.reject' | translate }}"
              class="p-button-danger"
              icon="pi pi-times"
              [loading]="rejecting"
              [disabled]="!rejectReason?.trim()"
            (click)="confirmReject()"></button>
          </ng-template>
        </p-dialog>
        `,
    styles: [`
        :host ::ng-deep .p-datatable .p-datatable-tbody > tr > td { padding: 0.5rem 1rem; }

        /* The connector itself is in _approval-detail.scss with the rest of the
           track; this page is the only one that animates it. */
        .level-connector { transition: background 0.2s; }

        .step-row--pending .step-row__icon { background: var(--yellow-100); color: var(--yellow-700); }

        .step-row__header { display: flex; align-items: center; gap: 0.5rem; }
    `],
    standalone: false
})
export class PendingApprovalsComponent implements OnInit {
    requests: ApprovalRequest[] = [];
    loading = false;
    /** The last load failed: the table says so instead of "no pending requests" (R5-01). */
    loadFailed = false;
    totalRecords = 0;
    rows = 10;
    /** Bound to the table so a reload the component starts (after a decision) moves the paginator too. */
    first = 0;
    page = 0;

    selectedRequest: ApprovalRequest | null = null;
    showDetailsDialog = false;
    showApproveDialog = false;
    showRejectDialog = false;

    approveComments = '';
    rejectReason = '';
    approving = false;
    rejecting = false;


    private fieldLookup: Record<string, { id: any; name: string }[]> = {};
    private lookupsLoaded = false;
    private travelFundingSources: TravelFundingSource[] = [];

    filters = { requestType: '', entityType: '' };
    requestTypeOptions: Array<{ label: string; value: string }> = [];

    /**
     * N-03.d: "mine" lists only the requests the caller may sign now (role,
     * position, named approvers, Wafar ownership, country); "all" is the old
     * list, every open request in scope, for oversight.
     */
    scope: 'mine' | 'all' = 'mine';
    scopeOptions: Array<{ label: string; value: 'mine' | 'all' }> = [];

    // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        public approvalService: ApprovalService,
        private messageService: MessageService,
        private umsAuthService: UmsAuthService,
        private authService: AuthService,
        private translationService: TranslationService,
        private lookupService: LookupService,
        private enumOptionsService: EnumOptionsService,
        private travelTicketService: StudentTravelTicketService,
        private codeLists: CodeListService
    ) {}

    /** Resolved from UMS or legacy session — never fabricated; approvals use JWT on the server. */
    get currentUserId(): number | null {
        const umsUser = this.umsAuthService.currentUserValue;
        const legacyUser = this.authService.currentUserValue;
        const raw = umsUser?.id ?? legacyUser?.id;
        if (raw == null) {
            return null;
        }
        const n = Number(raw);
        return Number.isFinite(n) && n > 0 ? n : null;
    }

    private buildScopeOptions(): void {
        this.scopeOptions = [
            { label: this.translationService.translate('APPROVAL.scopeMine'), value: 'mine' },
            { label: this.translationService.translate('APPROVAL.scopeAll'), value: 'all' }
        ];
    }

    ngOnInit(): void {
        this.buildScopeOptions();
        this.translationService.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.buildScopeOptions());
        this.enumOptionsService.getDropdownOptions('request-type').subscribe(opts => {
            this.requestTypeOptions = opts;
        });
        this.translationService.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.enumOptionsService.getDropdownOptions('request-type').subscribe(opts => {
                this.requestTypeOptions = opts;
            });
        });
        this.travelTicketService.getFundingSources().subscribe({
            next: (sources) => { this.travelFundingSources = sources || []; },
            error: () => { /* raw id remains as fallback */ }
        });
        this.loadRequests({ first: 0, rows: this.rows });
    }

    loadRequests(event: any): void {
        this.loading = true;
        this.page = event.first / event.rows;
        this.rows = event.rows;
        this.first = event.first;

        const f = this.filters;
        const rt = f.requestType || undefined;
        const et = f.entityType?.trim() || undefined;
        this.approvalService.getPendingRequests(this.page, this.rows, { requestType: rt, entityType: et }, this.scope).subscribe({
            next: (response) => {
                // Deciding the last request on the last page leaves that page empty;
                // step back to the last page that still has rows (review A1).
                const lastPage = Math.max(0, Math.ceil(response.totalElements / this.rows) - 1);
                if (!response.content?.length && this.page > lastPage) {
                    this.loadRequests({ first: lastPage * this.rows, rows: this.rows });
                    return;
                }
                this.requests = response.content;
                this.totalRecords = response.totalElements;
                this.loadFailed = false;
                this.loading = false;
            },
            error: (err) => {
                console.error('Error loading requests:', err);
                this.requests = [];
                this.totalRecords = 0;
                this.loadFailed = true;
                this.loading = false;
            }
        });
    }

    viewDetails(request: ApprovalRequest): void {
        this.selectedRequest = request;
        this.showDetailsDialog = true;
        this.ensureLookupsLoaded();
    }

    openApproveDialog(request: ApprovalRequest): void {
        this.selectedRequest = request;
        this.approveComments = '';
        this.showApproveDialog = true;
    }

    openRejectDialog(request: ApprovalRequest): void {
        this.selectedRequest = request;
        this.rejectReason = '';
        this.showRejectDialog = true;
    }

    confirmApprove(): void {
        if (!this.selectedRequest) return;

        const approverHint = this.currentUserId ?? 0;
        if (!this.currentUserId) {
            console.error('Approve: missing session user id (UMS/localStorage). JWT will still authorize on server.');
        }

        this.approving = true;
        this.approvalService.approveRequest(this.selectedRequest.id, approverHint, this.approveComments).subscribe({
            next: () => {
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('APPROVAL.requestApproved')
                });
                this.showApproveDialog = false;
                this.loadRequests({ first: this.page * this.rows, rows: this.rows });
                this.approving = false;
            },
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: ApprovalService.errorMessage(err) || this.translationService.translate('APPROVAL.approveError')
                });
                this.approving = false;
            }
        });
    }

    confirmReject(): void {
        if (!this.selectedRequest || !this.rejectReason?.trim()) return;

        this.rejecting = true;
        this.approvalService.rejectRequest(this.selectedRequest.id, this.currentUserId ?? 0, this.rejectReason).subscribe({
            next: () => {
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('APPROVAL.requestRejected')
                });
                this.showRejectDialog = false;
                this.loadRequests({ first: this.page * this.rows, rows: this.rows });
                this.rejecting = false;
            },
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: ApprovalService.errorMessage(err) || this.translationService.translate('APPROVAL.rejectError')
                });
                this.rejecting = false;
            }
        });
    }

    getRequestTypeLabel(type: string): string {
        return this.approvalService.getRequestTypeLabel(type);
    }


    // ── Details dialog helpers ────────────────────────────────────────────────

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

    getLevelRange(total?: number): number[] {
        if (!total || total < 1) return [];
        return Array.from({ length: total }, (_, i) => i + 1);
    }

    getLevelName(levelOrder: number): string {
        const levels = this.selectedRequest?.config?.levels;
        if (!levels) return `Level ${levelOrder}`;
        const lvl = levels.find((l: any) => l.levelOrder === levelOrder);
        return lvl?.levelName || `Level ${levelOrder}`;
    }

    getProgressPercent(request?: any): number {
        request = request ?? this.selectedRequest;
        if (!request) return 0;
        const total = request.config?.totalLevels || 1;
        // Fully resolved fills the bar; a rejection fills up to and including the level it stopped at.
        if (request.status === 'EXECUTED' || request.status === 'APPROVED') return 100;
        if (request.status === 'REJECTED') return Math.min(100, Math.round((request.currentLevel / total) * 100));
        return Math.min(100, Math.round(((request.currentLevel - 1) / total) * 100));
    }




    // ── Lookup resolution ─────────────────────────────────────────────────────

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
        this.lookupsLoaded = true;

        forkJoin({
            genders:              this.lookupService.getStudentGenders(),
            countries:            this.lookupService.getCountries(),
            govs:                 this.lookupService.getGovernments(),
            munis:                this.lookupService.getMunicipalities(),
            degrees:              this.lookupService.getStudentDegrees(),
            majors:               this.lookupService.getStudentMajors(),
            levels:               this.lookupService.getCurrentLevels(),
            studyStatuses:        this.lookupService.getStudyStatuses(),
            agencyTypes:          this.lookupService.getAgencyTypes(),
            ministryTypes:        this.lookupService.getMinistryTypes(),
            studyTypes:           this.lookupService.getStudentStudyTypes(),
            uniTypes:             this.lookupService.getUniversityTypes(),
            studyCountries:       this.lookupService.getStudyCountriesIn(),
            studyGovs:            this.lookupService.getStudyGovsIn(),
            studyUnis:            this.lookupService.getStudyUniversitiesIn(),
            studyColleges:        this.lookupService.getStudyCollegesIn(),
            studyEdus:            this.lookupService.getStudyEducationsIn(),
            studyMajors:          this.lookupService.getStudyMajorsIn(),
            currencies:           this.lookupService.getStudyCurrencies(),
            wafarTypes:           this.lookupService.getWafarTypes(),
            banks:                this.lookupService.getAllStudyFinanceBanks(),
            bankBranches:         this.lookupService.getAllStudyFinanceBankBranches(),
            dofs:                 this.lookupService.getAllStudyFinanceDofs(),
            finStatuses:          this.lookupService.getAllStudyFinReceivedStatuses(),
            receivedWhomStatuses: this.lookupService.getAllStudyReceivedWhomStatuses(),
        }).subscribe({
            next: (d) => {
                const e = (arr: any[]) => this.toEntries(arr);
                this.fieldLookup = {
                    // Student personal fields
                    studentGender:                 e(d.genders),
                    studentCountryId:              e(d.countries),
                    studentGovId:                  e(d.govs),
                    studentMudId:                  e(d.munis),
                    studentDegreeId:               e(d.degrees),
                    studentMajorId:                e(d.majors),
                    studentCurrentLevelId:         e(d.levels),
                    studentStudyStatusId:          e(d.studyStatuses),
                    studentAgencyTypeId:           e(d.agencyTypes),
                    studentMinistryTypeId:         e(d.ministryTypes),
                    studentStudyTypeId:            e(d.studyTypes),
                    studentUniversityTypeId:       e(d.uniTypes),
                    studentPassportIssueCountryId: e(d.countries),
                    // Study location abroad
                    studyCountryIn:                e(d.studyCountries),
                    syudyGovIn:                    e(d.studyGovs),
                    studyUniversityIn:             e(d.studyUnis),
                    studyCollegeIn:                e(d.studyColleges),
                    studyEducationIn:              e(d.studyEdus),
                    studyMajorIn:                  e(d.studyMajors),
                    // Currency
                    amountCurrency:                e(d.currencies),
                    universityFeeStatusCurrency:   e(d.currencies),
                    // Finance
                    studyCountryInId:              e(d.studyCountries),
                    studyGovInId:                  e(d.studyGovs),
                    studyFinanceBankeId:           e(d.banks),
                    studentBankId:                 e(d.banks),
                    studyFinanceBankBranchId:      e(d.bankBranches),
                    studyFinanceDofId:             e(d.dofs),
                    studyFinRecievedStatus:        e(d.finStatuses),
                    studyReceivedWhomStatusId:     e(d.receivedWhomStatuses),
                    wafarTypeId:                   e(d.wafarTypes),
                };
            },
            error: () => { /* silent — raw IDs remain as fallback */ }
        });
    }

    resolveLookup(key: string, val: any): string | null {
        if (val == null) return null;
        const entries = this.fieldLookup[key];
        if (!entries?.length) return null;
        // eslint-disable-next-line eqeqeq
        const found = entries.find(e => e.id == val);
        return found?.name ?? null;
    }


    private extractObjectName(obj: any): string {
        if (!obj || typeof obj !== 'object') return String(obj ?? '?');
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        const keys = Object.keys(obj);
        // Find *NameEn and *Name (AR) fields following the system-wide convention
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
        this.filters = { requestType: '', entityType: '' };
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
