import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import {
    AlertEventsService,
    AlertEventCaseDto,
    AlertEventCaseSummaryDto,
    AlertEventCodeCount,
    AlertEventScenarioDto
} from '../../../service/alert-events.service';
import { CodeListService, PrimeSeverity } from '../../../service/code-list.service';
import { pageOf, resetPaging } from '../../../service/table-paging.util';

@Component({
    selector: 'app-alert-events-cases',
    templateUrl: './alert-events-cases.component.html',
    styleUrls: ['./alert-events-cases.component.scss'],
    standalone: false
})
export class AlertEventsCasesComponent implements OnInit, OnDestroy {

    private destroy$ = new Subject<void>();

    cases: AlertEventCaseDto[] = [];
    totalRecords = 0;
    loading = false;
    summaryLoading = false;
    /** The figures could not be read: the band says so instead of showing zeros (QA B9). */
    summaryFailed = false;

    summary: AlertEventCaseSummaryDto = {
        totalOpen: 0, totalAcknowledged: 0, totalResolved: 0, totalDismissed: 0, totalAll: 0,
        openCritical: 0, openHigh: 0, openMedium: 0, openLow: 0,
        bySeverity: [], byStatus: []
    };

    scenarios: AlertEventScenarioDto[] = [];
    scenarioOptions: any[] = [];

    // Filters
    filterStatus: string | null = null;
    filterScenarioCode: string | null = null;
    filterSeverity: string | null = null;
    filterStudentName: string | null = null;
    filterDateFrom: Date | null = null;
    filterDateTo: Date | null = null;

    page = 0;
    pageSize = 20;
    /** The table's own position, bound both ways so a filter's reset reaches it (QA B8, N9). */
    first = 0;
    sortBy = 'createdAt';
    sortDir = 'desc';

    statusOptions: any[] = [];
    severityOptions: any[] = [];
    /** Bound into the code-list pipes so a language switch re-labels the tags. */
    currentLanguage = 'en';

    // Action dialog
    actionDialogVisible = false;
    actionDialogMode: 'acknowledge' | 'resolve' | 'dismiss' = 'acknowledge';
    actionDialogCase: AlertEventCaseDto | null = null;
    actionNotes = '';
    actionLoading = false;

    constructor(
        private route: ActivatedRoute,
        private service: AlertEventsService,
        private messageService: MessageService,
        public translationService: TranslationService,
        public permissionService: UmsPermissionService,
        private lookupService: LookupService
    ) {}

    ngOnInit(): void {
        this.applyRouteFilters();
        this.currentLanguage = this.translationService.getCurrentLanguage();
        this.buildOptions();
        this.translationService.currentLanguage$
            .pipe(takeUntil(this.destroy$))
            .subscribe(lang => { this.currentLanguage = lang; this.buildOptions(); });
        this.loadScenarios();
        this.loadSummary();
        this.loadCases();
    }

    /**
     * A date range handed over in the query string, so a link can open this page
     * on the day it was talking about — the topbar calendar's agenda does.
     *
     * <p>Read once rather than subscribed: the range is an opening position, not
     * a binding. Once the page is open the filter bar owns it.
     */
    private applyRouteFilters(): void {
        const params = this.route.snapshot.queryParamMap;
        const from = AlertEventsCasesComponent.parseDate(params.get('dateFrom'));
        const to = AlertEventsCasesComponent.parseDate(params.get('dateTo'));
        if (from) { this.filterDateFrom = from; }
        if (to) { this.filterDateTo = to; }
    }

    /** `yyyy-MM-dd` as a local date; anything else is ignored rather than guessed at. */
    private static parseDate(value: string | null): Date | null {
        if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
            return null;
        }
        const [year, month, day] = value.split('-').map(Number);
        const date = new Date(year, month - 1, day);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    /** The last instant of a day, for an inclusive "to". */
    private static endOfDay(date: Date): string {
        const end = new Date(date);
        end.setHours(23, 59, 59, 999);
        return end.toISOString();
    }

    private buildOptions(): void {
        this.lookupService.getAlertStatuses().subscribe({
            next: rows => {
                this.statusOptions = [
                    { label: this.t('COMMON.ALL'), value: null },
                    ...rows
                ];
            },
            error: () => { this.statusOptions = [{ label: this.t('COMMON.ALL'), value: null }]; }
        });
        this.lookupService.getAlertSeverities().subscribe({
            next: rows => {
                this.severityOptions = [
                    { label: this.t('COMMON.ALL'), value: null },
                    ...rows
                ];
            },
            error: () => { this.severityOptions = [{ label: this.t('COMMON.ALL'), value: null }]; }
        });
        this.rebuildScenarioOptions();
    }

    private rebuildScenarioOptions(): void {
        if (this.scenarios.length > 0) {
            this.scenarioOptions = [
                { label: this.t('COMMON.ALL'), value: null },
                ...this.scenarios.map(s => ({
                    label: this.isRtl ? s.scenarioNameAr : s.scenarioNameEn,
                    value: s.scenarioCode
                }))
            ];
        }
    }

    loadScenarios(): void {
        this.service.getScenarios()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: scenarios => {
                    this.scenarios = scenarios;
                    this.rebuildScenarioOptions();
                },
                error: () => {}
            });
    }

    loadSummary(): void {
        this.summaryLoading = true;
        this.service.getSummary()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: s => { this.summary = s; this.summaryLoading = false; this.summaryFailed = false; },
                error: () => { this.summaryLoading = false; this.summaryFailed = true; }
            });
    }

    loadCases(): void {
        this.loading = true;
        this.service.getCases({
            status: this.filterStatus ?? undefined,
            scenarioCode: this.filterScenarioCode ?? undefined,
            severity: this.filterSeverity ?? undefined,
            studentName: this.filterStudentName ?? undefined,
            dateFrom: this.filterDateFrom?.toISOString(),
            // The picker hands back midnight, and the server compares "to"
            // inclusively against CREATED_AT — so a range ending on the chosen day
            // used to exclude every case opened during it. Pick today and today and
            // the list came back empty.
            dateTo: this.filterDateTo ? AlertEventsCasesComponent.endOfDay(this.filterDateTo) : undefined,
            page: this.page,
            size: this.pageSize,
            sortBy: this.sortBy,
            sortDir: this.sortDir
        }).pipe(takeUntil(this.destroy$))
          .subscribe({
              next: resp => {
                  this.cases = resp.content;
                  this.totalRecords = resp.totalElements;
                  this.loading = false;
              },
              error: () => { this.loading = false; }
          });
    }

    onPage(event: any): void {
        const at = pageOf(event, this.pageSize);
        this.first = at.first;
        this.page = at.page;
        this.pageSize = at.rows;
        this.loadCases();
    }

    onSort(event: any): void {
        this.sortBy = event.field;
        this.sortDir = event.order === 1 ? 'asc' : 'desc';
        // The table goes back to its first page on a sort; so does the request,
        // or the server sent page 3 of the new order under a paginator on page 1.
        resetPaging(this);
        this.loadCases();
    }

    applyFilters(): void {
        resetPaging(this);
        this.loadSummary();
        this.loadCases();
    }

    resetFilters(): void {
        this.filterStatus = null;
        this.filterScenarioCode = null;
        this.filterSeverity = null;
        this.filterStudentName = null;
        this.filterDateFrom = null;
        this.filterDateTo = null;
        this.applyFilters();
    }

    openAction(caseItem: AlertEventCaseDto, mode: 'acknowledge' | 'resolve' | 'dismiss'): void {
        this.actionDialogCase = caseItem;
        this.actionDialogMode = mode;
        this.actionNotes = '';
        this.actionDialogVisible = true;
    }

    confirmAction(): void {
        if (!this.actionDialogCase) return;
        this.actionLoading = true;
        const id = this.actionDialogCase.caseId;
        const notes = this.actionNotes || undefined;

        const call = this.actionDialogMode === 'acknowledge'
            ? this.service.acknowledge(id, notes)
            : this.actionDialogMode === 'resolve'
            ? this.service.resolve(id, notes)
            : this.service.dismiss(id, notes);

        call.pipe(takeUntil(this.destroy$)).subscribe({
            next: updated => {
                const idx = this.cases.findIndex(c => c.caseId === updated.caseId);
                if (idx >= 0) this.cases[idx] = updated;
                this.cases = [...this.cases];
                this.actionDialogVisible = false;
                this.actionLoading = false;
                this.loadSummary();
                this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('ALERT_EVENTS.ACTION_SUCCESS') });
            },
            error: () => {
                this.actionLoading = false;
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.OPERATION_FAILED') });
            }
        });
    }

    runDetection(): void {
        this.service.runDetection()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: result => {
                    this.messageService.add({
                        severity: 'info',
                        summary: this.t('ALERT_EVENTS.DETECTION_DONE'),
                        detail: `${result.newCasesCreated} ${this.t('ALERT_EVENTS.NEW_CASES_CREATED')}`
                    });
                    this.loadSummary();
                    this.loadCases();
                },
                error: () => {
                    this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.OPERATION_FAILED') });
                }
            });
    }

    /** A legend row's name in the reader's language. */
    severityName(row: AlertEventCodeCount): string {
        const primary = this.isRtl ? row.nameAr : row.nameEn;
        const other = this.isRtl ? row.nameEn : row.nameAr;
        return primary || other || row.code;
    }

    /** The row's severity, held to PrimeNG's vocabulary. */
    tagSeverity(value: string | null | undefined): PrimeSeverity {
        return CodeListService.toSeverity(value);
    }

    scenarioLabel(c: AlertEventCaseDto): string {
        return this.isRtl ? c.scenarioNameAr : c.scenarioNameEn;
    }

    get actionDialogHeader(): string {
        switch (this.actionDialogMode) {
            case 'acknowledge': return this.t('ALERT_EVENTS.ACKNOWLEDGE');
            case 'resolve':     return this.t('ALERT_EVENTS.RESOLVE');
            case 'dismiss':     return this.t('ALERT_EVENTS.DISMISS');
        }
    }

    canManage(): boolean {
        return this.permissionService.hasPermission('ALERT_EVENTS.MANAGE');
    }

    canRunDetection(): boolean {
        return this.permissionService.hasPermission('ALERT_SCENARIOS.MANAGE');
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }
}
