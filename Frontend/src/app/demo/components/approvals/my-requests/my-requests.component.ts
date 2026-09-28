import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { forkJoin } from 'rxjs';
import { ApprovalService, ApprovalRequest } from '../../../service/approval.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { changesOnly } from '../../../service/reload-streams.util';
import { followPage } from '../../../service/table-paging.util';
import { CodeListService } from '../../../service/code-list.service';
import { entityName, entityTypeLabel } from '../approval-entity-label';

@Component({
    selector: 'app-my-requests',
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
              <i class="pi pi-send text-primary" style="font-size: 2.5rem"></i>
              <div>
                <h2 class="m-0 text-primary">{{ 'APPROVAL.myRequests' | translate }}</h2>
                <p class="text-600 mt-1 mb-0">{{ 'APPROVAL.myRequestsSubtitle' | translate }}</p>
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
              <label for="mrType">{{ 'APPROVAL.type' | translate }}</label>
              <p-select inputId="mrType" [options]="requestTypeOptions" [(ngModel)]="filters.requestType"
                [placeholder]="'APPROVAL.type' | translate"
                [showClear]="true" optionLabel="label" optionValue="value"
                (onChange)="applyFilters()"></p-select>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filters.entityType">
              <label for="mrEntity">{{ 'APPROVAL.entityType' | translate }}</label>
              <input id="mrEntity" pInputText [(ngModel)]="filters.entityType"
                [placeholder]="'APPROVAL.entityType' | translate"
                (keydown.enter)="applyFilters()">
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filters.status">
              <label for="mrStatus">{{ 'APPROVAL.statusLabel' | translate }}</label>
              <p-select inputId="mrStatus" [options]="statusOptions" [(ngModel)]="filters.status"
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
                <th>{{ 'APPROVAL.progress' | translate }}</th>
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
                <td>{{ getEntityName(request) }}</td>
                <td>
                  <div class="flex align-items-center gap-2">
                    <div class="progress-bar" [style.width.px]="100">
                      <div class="progress-fill"
                        [style.width.%]="getProgressPercent(request)"
                        [class.success]="request.status === 'EXECUTED'"
                      [class.danger]="request.status === 'REJECTED'"></div>
                    </div>
                    <span class="text-sm">{{ request.currentLevel }}/{{ request.config?.totalLevels }}</span>
                  </div>
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
                    <!-- R5-16: the requester takes back a request still waiting on a level. -->
                    @if (canWithdraw(request)) {
                      <button pButton icon="pi pi-undo"
                        class="p-button-text p-button-sm p-button-warning withdraw-request"
                        pTooltip="{{ 'APPROVAL.withdraw' | translate }}"
                      (click)="openWithdraw(request)"></button>
                    }
                  </div>
                </td>
              </tr>
            </ng-template>
        
            <ng-template pTemplate="emptymessage">
              <tr>
                <td colspan="8" class="text-center p-4">
                  <i class="pi pi-inbox text-4xl text-300 mb-3"></i>
                  <p class="text-500">{{ 'APPROVAL.noRequests' | translate }}</p>
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
              <div class="approval-section">
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
                <div class="col-12 md:col-6">
                  <div class="approval-info-card">
                    <div class="approval-info-card__title">
                      <i class="pi pi-calendar text-purple-500"></i>
                      {{ 'APPROVAL.timeline' | translate }}
                    </div>
                    <div class="approval-kv-grid">
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
              <!-- ── Section 3: Approval Steps ─────────────────────────────── -->
              @if (selectedRequest.steps?.length) {
                <div class="approval-section mt-3">
                  <div class="approval-section__title">
                    <i class="pi pi-check-square text-indigo-500"></i>
                    {{ 'APPROVAL.approvalSteps' | translate }}
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
                            <span class="font-semibold">{{ 'APPROVAL.level' | translate }} {{ step.levelOrder }}</span>
                            @if (step.level?.levelName) {
                              <span class="text-600 text-sm ml-1">— {{ step.level?.levelName }}</span>
                            }
                            <p-tag class="ml-auto"
                              [value]="approvalService.getStatusLabel(step.status)"
                            [severity]="step.status | codeSeverity:'approval-status'"></p-tag>
                          </div>
                          @if (step.approver || step.approvedAt) {
                            <div class="step-row__meta text-500 text-sm">
                              @if (step.approver) {
                                <span>
                                  <i class="pi pi-user mr-1"></i>{{ step.approver?.fullName || step.approver?.username }}
                                </span>
                              }
                              @if (step.approvedAt) {
                                <span> &bull; {{ step.approvedAt | date:'medium' }}</span>
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
              * Rendered by the shared component rather than a local diff of requestData:
              * a request carrying uploads stores an envelope ({studentData, …}), so the
              * old table reported every real field as emptied and the envelope keys as
              * added. This one uses a server-computed diff and also shows the photo and
              * documents involved.
              -->
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

        <!-- Withdraw Dialog (R5-16) -->
        <p-dialog [(visible)]="showWithdrawDialog"
          [header]="'APPROVAL.withdrawTitle' | translate"
          [modal]="true"
          [style]="{width: '450px'}">
          <p class="mt-0 mb-3">{{ 'APPROVAL.withdrawConfirm' | translate }}</p>
          <div class="mb-3">
            <label for="withdrawReason" class="block mb-2">{{ 'APPROVAL.withdrawReason' | translate }}</label>
            <textarea pInputTextarea id="withdrawReason"
              [(ngModel)]="withdrawReason"
              rows="3"
            class="w-full"></textarea>
          </div>
          <ng-template pTemplate="footer">
            <button pButton label="{{ 'common.cancel' | translate }}"
              class="p-button-text"
            (click)="showWithdrawDialog = false"></button>
            <button pButton label="{{ 'APPROVAL.withdraw' | translate }}"
              class="p-button-warning"
              icon="pi pi-undo"
              [loading]="withdrawing"
            (click)="confirmWithdraw()"></button>
          </ng-template>
        </p-dialog>
        `,
    styles: [`
        .progress-bar { height: 8px; background: var(--surface-200); border-radius: 4px; overflow: hidden; }

        .progress-fill { height: 100%; background: var(--primary-color); transition: width 0.3s ease; }

        .progress-fill.success { background: var(--green-500); }

        .progress-fill.danger { background: var(--red-500); }

        :host ::ng-deep .p-datatable .p-datatable-tbody > tr > td { padding: 0.5rem 1rem; }

        .step-row--pending .step-row__icon { background: var(--blue-100);  color: var(--blue-600); }

        .step-row__header { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    `],
    standalone: false
})
export class MyRequestsComponent implements OnInit {
    requests: ApprovalRequest[] = [];
    loading = false;
    totalRecords = 0;
    rows = 10;
    page = 0;
    /** Bound to the table, so a filter that reloads page one moves the paginator there too (QA B8, N9). */
    first = 0;

    selectedRequest: ApprovalRequest | null = null;
    showDetailsDialog = false;

    /** R5-16: the request being withdrawn, and the optional reason. */
    withdrawTarget: ApprovalRequest | null = null;
    showWithdrawDialog = false;
    withdrawReason = '';
    withdrawing = false;

    private fieldLookup: Record<string, { id: any; name: string }[]> = {};
    private lookupsLoaded = false;

    filters = { requestType: '', entityType: '', status: '' };
    requestTypeOptions: Array<{ label: string; value: string }> = [];
    statusOptions: Array<{ label: string; value: string }> = [];

    get currentUserId(): number {
        return this.umsAuthService.currentUserValue?.id ?? 1;
    }

    // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        public approvalService: ApprovalService,
        private umsAuthService: UmsAuthService,
        private translationService: TranslationService,
        private lookupService: LookupService,
        private enumOptionsService: EnumOptionsService,
        private codeLists: CodeListService,
        private messageService: MessageService
    ) {}

    /** A request still waiting on a level can be taken back by the person who raised it (R5-16). */
    canWithdraw(request: ApprovalRequest): boolean {
        return request.status === 'PENDING' || request.status === 'IN_PROGRESS';
    }

    openWithdraw(request: ApprovalRequest): void {
        this.withdrawTarget = request;
        this.withdrawReason = '';
        this.showWithdrawDialog = true;
    }

    confirmWithdraw(): void {
        if (!this.withdrawTarget) return;
        this.withdrawing = true;
        this.approvalService.withdrawRequest(this.withdrawTarget.id, this.withdrawReason).subscribe({
            next: () => {
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('APPROVAL.requestWithdrawn')
                });
                this.showWithdrawDialog = false;
                this.withdrawing = false;
                this.loadRequests({ first: this.first, rows: this.rows });
            },
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: ApprovalService.errorMessage(err) || this.translationService.translate('APPROVAL.withdrawError')
                });
                this.withdrawing = false;
            }
        });
    }

    ngOnInit(): void {
        this.loadEnumOptions();
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadEnumOptions());
        this.loadRequests({ first: 0, rows: this.rows });
    }

    private loadEnumOptions(): void {
        this.enumOptionsService.getDropdownOptions('request-type').subscribe(opts => {
            this.requestTypeOptions = opts;
        });
        this.enumOptionsService.getDropdownOptions('approval-status').subscribe(opts => {
            this.statusOptions = opts;
        });
    }

    loadRequests(event: any): void {
        this.loading = true;
        followPage(this, event);

        const f = this.filters;
        const rt = f.requestType || undefined;
        const et = f.entityType?.trim() || undefined;
        const st = f.status || undefined;
        this.approvalService.getMyRequests(this.currentUserId, this.page, this.rows, { requestType: rt, entityType: et, status: st }).subscribe({
            next: (response) => {
                this.requests = response.content;
                this.totalRecords = response.totalElements;
                this.loading = false;
            },
            error: (err) => {
                console.error('Error loading requests:', err);
                this.loading = false;
            }
        });
    }

    viewDetails(request: ApprovalRequest): void {
        this.selectedRequest = request;
        this.showDetailsDialog = true;
        this.ensureLookupsLoaded();
    }

    onDetailsHide(): void {
        this.selectedRequest = null;
    }

    getProgressPercent(request: ApprovalRequest): number {
        if (request.status === 'EXECUTED' || request.status === 'APPROVED') return 100;
        if (request.status === 'REJECTED') return (request.currentLevel / (request.config?.totalLevels || 1)) * 100;
        return ((request.currentLevel - 1) / (request.config?.totalLevels || 1)) * 100;
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

    getLevelRange(total?: number): number[] {
        return Array.from({ length: total ?? 1 }, (_, i) => i + 1);
    }

    getLevelName(levelOrder: number): string {
        const step = this.selectedRequest?.steps?.find((s: any) => s.levelOrder === levelOrder);
        return step?.level?.levelName ?? `${this.translationService.translate('APPROVAL.level')} ${levelOrder}`;
    }

    getRequestTypeLabel(type: string): string {
        return this.approvalService.getRequestTypeLabel(type);
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
            genders:              this.lookupService.getStudentGenders(),
            countries:            this.lookupService.getCountries(),
            govs:                 this.lookupService.getGovernments(),
            munis:                this.lookupService.getMunicipalities(),
            degrees:              this.lookupService.getStudentDegrees(),
            majors:               this.lookupService.getStudentMajors(),
            levels:               this.lookupService.getCurrentLevels(),
            studyStatuses:        this.lookupService.getStudentStudyStatuses(),
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
            banks:                this.lookupService.getAllStudyFinanceBanks(),
            bankBranches:         this.lookupService.getAllStudyFinanceBankBranches(),
            dofs:                 this.lookupService.getAllStudyFinanceDofs(),
            finStatuses:          this.lookupService.getAllStudyFinReceivedStatuses(),
            receivedWhomStatuses: this.lookupService.getAllStudyReceivedWhomStatuses(),
            wafarTypes:           this.lookupService.getWafarTypes(),
        }).subscribe(d => {
            const e = (arr: any[]) => this.toEntries(arr);
            this.fieldLookup = {
                studentGender:                e(d.genders),
                studentCountryId:             e(d.countries),
                studentGovId:                 e(d.govs),
                studentMudId:                 e(d.munis),
                studentDegreeId:              e(d.degrees),
                studentMajorId:               e(d.majors),
                studentCurrentLevelId:        e(d.levels),
                studentStudyStatusId:         e(d.studyStatuses),
                studentAgencyTypeId:          e(d.agencyTypes),
                studentMinistryTypeId:        e(d.ministryTypes),
                studentStudyTypeId:           e(d.studyTypes),
                studentUniversityTypeId:      e(d.uniTypes),
                studentPassportIssueCountryId:e(d.countries),
                studyCountryIn:               e(d.studyCountries),
                syudyGovIn:                   e(d.studyGovs),
                studyUniversityIn:            e(d.studyUnis),
                studyCollegeIn:               e(d.studyColleges),
                studyEducationIn:             e(d.studyEdus),
                studyMajorIn:                 e(d.studyMajors),
                amountCurrency:               e(d.currencies),
                universityFeeStatusCurrency:  e(d.currencies),
                studyCountryInId:             e(d.studyCountries),
                studyGovInId:                 e(d.studyGovs),
                studyFinanceBankeId:          e(d.banks),
                studentBankId:               e(d.banks),
                studyFinanceBankBranchId:     e(d.bankBranches),
                studyFinanceDofId:            e(d.dofs),
                studyFinRecievedStatus:       e(d.finStatuses),
                studyReceivedWhomStatusId:    e(d.receivedWhomStatuses),
                wafarTypeId:                  e(d.wafarTypes),
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
}
