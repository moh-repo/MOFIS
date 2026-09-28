import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MessageService, ConfirmationService, SortEvent } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { CodeListService } from '../../../service/code-list.service';
import {
    AlertEventsService,
    AlertEventScenarioDto,
    AlertEventScenarioRequest
} from '../../../service/alert-events.service';
import { sortRows } from '../../../service/table-sort.util';

@Component({
    selector: 'app-alert-events-scenarios',
    templateUrl: './alert-events-scenarios.component.html',
    standalone: false
})
export class AlertEventsScenariosComponent implements OnInit, OnDestroy {

    private destroy$ = new Subject<void>();

    scenarios: AlertEventScenarioDto[] = [];
    loading = false;
    savingId: number | null = null;

    dialogVisible = false;
    dialogMode: 'add' | 'edit' = 'add';
    dialogSaving = false;
    submitted = false;
    editingId: number | null = null;

    form: AlertEventScenarioRequest = this.emptyForm();

    // View dialog
    viewVisible = false;
    viewing: AlertEventScenarioDto | null = null;

    severityOptions: any[] = [];
    /** Bound into the code-list pipes so a language switch re-labels the tags. */
    currentLanguage = 'en';

    constructor(
        private service: AlertEventsService,
        private messageService: MessageService,
        private confirmService: ConfirmationService,
        public translationService: TranslationService,
        private lookupService: LookupService,
        private permissionService: UmsPermissionService,
        /** Severity tones for the dialogs' hero and tiles. */
        private codeLists: CodeListService
    ) {}

    /**
     * Writes need ALERT_SCENARIOS.MANAGE on the server (AlertEventScenarioController).
     * The buttons are hidden with *hasPermission; the inline controls (threshold
     * box, active switch) cannot be wrapped, so they are disabled from here.
     */
    get canManage(): boolean {
        return this.permissionService.hasPermission('ALERT_SCENARIOS.MANAGE');
    }

    ngOnInit(): void {
        this.currentLanguage = this.translationService.getCurrentLanguage();
        this.buildSeverityOptions();
        this.translationService.currentLanguage$
            .pipe(takeUntil(this.destroy$))
            .subscribe(lang => { this.currentLanguage = lang; this.buildSeverityOptions(); });
        this.loadScenarios();
    }

    private buildSeverityOptions(): void {
        this.lookupService.getAlertSeverities().pipe(takeUntil(this.destroy$)).subscribe({
            next: opts => { this.severityOptions = opts; },
            error: () => { this.severityOptions = []; }
        });
    }

    loadScenarios(): void {
        this.loading = true;
        this.service.getScenarios()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: s => { this.scenarios = s; this.loading = false; },
                error: () => { this.loading = false; }
            });
    }

    openAdd(): void {
        this.dialogMode = 'add';
        this.editingId = null;
        this.submitted = false;
        this.form = this.emptyForm();
        this.dialogVisible = true;
    }

    openEdit(s: AlertEventScenarioDto): void {
        this.dialogMode = 'edit';
        this.editingId = s.scenarioId;
        this.submitted = false;
        this.form = {
            scenarioCode:  s.scenarioCode,
            scenarioNameEn: s.scenarioNameEn,
            scenarioNameAr: s.scenarioNameAr,
            descriptionEn:  s.descriptionEn ?? '',
            descriptionAr:  s.descriptionAr ?? '',
            severity:       s.severity,
            thresholdDays:  s.thresholdDays ?? null,
            isActive:       s.isActive
        };
        this.dialogVisible = true;
    }

    saveDialog(): void {
        this.submitted = true;
        if (!this.form.scenarioCode?.trim() || !this.form.scenarioNameEn?.trim() || !this.form.scenarioNameAr?.trim()) {
            this.messageService.add({ severity: 'warn', summary: this.t('COMMON.VALIDATION_ERROR'), detail: this.t('ALERT_EVENTS.SCENARIO_REQUIRED_FIELDS') });
            return;
        }
        this.dialogSaving = true;
        const call = this.dialogMode === 'add'
            ? this.service.createScenario(this.form)
            : this.service.saveScenario(this.editingId!, this.form);

        call.pipe(takeUntil(this.destroy$)).subscribe({
            next: result => {
                if (this.dialogMode === 'add') {
                    this.scenarios = [...this.scenarios, result];
                } else {
                    const idx = this.scenarios.findIndex(s => s.scenarioId === result.scenarioId);
                    if (idx >= 0) this.scenarios[idx] = result;
                    this.scenarios = [...this.scenarios];
                }
                this.dialogVisible = false;
                this.dialogSaving = false;
                this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('ALERT_EVENTS.SCENARIO_SAVED') });
            },
            error: (err) => {
                this.dialogSaving = false;
                const detail = err?.error?.message || this.t('COMMON.OPERATION_FAILED');
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail });
            }
        });
    }

    confirmDelete(s: AlertEventScenarioDto): void {
        this.confirmService.confirm({
            message: this.t('ALERT_EVENTS.CONFIRM_DELETE_SCENARIO').replace('{name}', this.scenarioName(s)),
            header: this.t('COMMON.CONFIRM_DELETE'),
            icon: 'pi pi-exclamation-triangle',
            acceptButtonStyleClass: 'p-button-danger',
            accept: () => this.doDelete(s)
        });
    }

    private doDelete(s: AlertEventScenarioDto): void {
        this.savingId = s.scenarioId;
        this.service.deleteScenario(s.scenarioId)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: () => {
                    this.scenarios = this.scenarios.filter(x => x.scenarioId !== s.scenarioId);
                    this.savingId = null;
                    this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('ALERT_EVENTS.SCENARIO_DELETED') });
                },
                error: (err) => {
                    this.savingId = null;
                    const detail = err?.error?.message || this.t('COMMON.OPERATION_FAILED');
                    this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail });
                }
            });
    }

    toggleActive(s: AlertEventScenarioDto): void {
        this.savingId = s.scenarioId;
        this.service.updateScenario(s.scenarioId, s.isActive)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: updated => {
                    const idx = this.scenarios.findIndex(x => x.scenarioId === updated.scenarioId);
                    if (idx >= 0) this.scenarios[idx] = updated;
                    this.scenarios = [...this.scenarios];
                    this.savingId = null;
                    this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('ALERT_EVENTS.SCENARIO_UPDATED') });
                },
                error: () => {
                    s.isActive = !s.isActive;
                    this.savingId = null;
                    this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.OPERATION_FAILED') });
                }
            });
    }

    saveThreshold(s: AlertEventScenarioDto): void {
        this.savingId = s.scenarioId;
        this.service.updateScenario(s.scenarioId, undefined, s.thresholdDays ?? undefined)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: updated => {
                    const idx = this.scenarios.findIndex(x => x.scenarioId === updated.scenarioId);
                    if (idx >= 0) this.scenarios[idx] = updated;
                    this.scenarios = [...this.scenarios];
                    this.savingId = null;
                    this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('ALERT_EVENTS.SCENARIO_UPDATED') });
                },
                error: () => {
                    this.savingId = null;
                    this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.OPERATION_FAILED') });
                }
            });
    }

    /**
     * Sorts by what the column prints. Severity sorts by its position in the
     * severity list — critical, high, medium, low — and not by its code, which
     * would put CRITICAL between neither of its neighbours in any useful order.
     */
    sortTable(event: SortEvent): void {
        sortRows(event, {
            scenario: (s: AlertEventScenarioDto) => this.scenarioName(s),
            severity: (s: AlertEventScenarioDto) => this.severityRank(s.severity)
        }, this.currentLanguage);
    }

    /** Where a severity sits in the list the filter offers; unknown codes go last. */
    private severityRank(severity: string | null | undefined): number {
        const index = this.severityOptions.findIndex(o => o?.value === severity);
        return index < 0 ? this.severityOptions.length : index;
    }

    scenarioName(s: AlertEventScenarioDto): string {
        return this.isRtl ? s.scenarioNameAr : s.scenarioNameEn;
    }

    // ── View dialog ────────────────────────────────────────────────────────

    openView(s: AlertEventScenarioDto): void {
        this.viewing = s;
        this.viewVisible = true;
    }

    /** From the view straight into editing the same scenario. */
    editFromView(): void {
        const s = this.viewing;
        if (!s) return;
        this.viewVisible = false;
        this.openEdit(s);
    }

    /** The name in the other language, under the title. */
    otherName(s: AlertEventScenarioDto): string {
        return this.isRtl ? s.scenarioNameEn : s.scenarioNameAr;
    }

    thresholdText(days: number | null | undefined): string {
        return days == null ? this.t('ALERT_EVENTS.NO_THRESHOLD') : `${days} ${this.t('ALERT_EVENTS.DAYS')}`;
    }

    // ── Tones for the shared .dv-* vocabulary ─────────────────────────────

    /** The severity code list's severity as the palette name the .dv-* modifiers use. */
    private tone(severity: string | null | undefined): 'green' | 'orange' | 'red' | 'blue' | 'gray' {
        const ui: string = severity ? this.codeLists.severity('alert-severity', severity) : '';
        switch (ui) {
            case 'success': return 'green';
            case 'warn':
            case 'warning': return 'orange';
            case 'danger': return 'red';
            case 'info': return 'blue';
            default: return 'gray';
        }
    }

    /** Coloured by severity; an inactive scenario reads grey whatever its severity. */
    private heroClassOf(severity: string | null | undefined, active: boolean): string {
        if (!active) return 'dv-hero--muted';
        switch (this.tone(severity)) {
            case 'green': return 'dv-hero--success';
            case 'orange': return 'dv-hero--warn';
            case 'red': return 'dv-hero--danger';
            case 'blue': return 'dv-hero--info';
            default: return '';
        }
    }

    heroClassFor(s: AlertEventScenarioDto): string {
        return this.heroClassOf(s.severity, s.isActive);
    }

    severityKpiClass(s: AlertEventScenarioDto): string {
        return `dv-kpi dv-kpi--${this.tone(s.severity)}`;
    }

    /** The add/edit hero: green for a new record, otherwise as the record would look. */
    get formHeroClass(): string {
        return this.dialogMode === 'add' ? 'dv-hero--new' : this.heroClassOf(this.form.severity, this.form.isActive);
    }

    /** The name in the current language as typed, falling back to the other one. */
    get formPreviewName(): string {
        const en = this.form.scenarioNameEn?.trim() ?? '';
        const ar = this.form.scenarioNameAr?.trim() ?? '';
        return (this.isRtl ? ar || en : en || ar);
    }

    /** The other language's name, once both are typed. */
    get formPreviewOtherName(): string {
        const en = this.form.scenarioNameEn?.trim() ?? '';
        const ar = this.form.scenarioNameAr?.trim() ?? '';
        return en && ar ? (this.isRtl ? en : ar) : '';
    }

    get dialogHeader(): string {
        return this.dialogMode === 'add'
            ? this.t('ALERT_EVENTS.ADD_SCENARIO')
            : this.t('ALERT_EVENTS.EDIT_SCENARIO');
    }

    private emptyForm(): AlertEventScenarioRequest {
        return { scenarioCode: '', scenarioNameEn: '', scenarioNameAr: '', descriptionEn: '', descriptionAr: '', severity: 'MEDIUM', thresholdDays: null, isActive: true };
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
