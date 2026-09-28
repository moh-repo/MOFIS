import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { AuditLog, AuditLogService } from 'src/app/demo/api/auditlog';
import { TranslationService } from 'src/app/demo/service/translation.service';

interface Option<T> { label: string; value: T; }

/** One field of an audited row, as the detail dialog lists it (review M1). */
export interface AuditFieldRow {
    key: string;
    label: string;
    oldValue: string;
    newValue: string;
    changed: boolean;
    /** The server masked it (a password hash, a secret). */
    masked: boolean;
}

/** What the server puts in place of a credential; see AuditValueRedactor. */
const MASK = '***';

/**
 * The audit trail, one page at a time.
 *
 * <p>This page used to ask for the first thousand rows and page them in the
 * browser, and its two filters each replaced the list, so table and user could
 * not be combined and clearing an empty box quietly reloaded everything. The
 * endpoint had always paged and filtered on six parameters; the page now uses
 * them, and every filter goes into one request.
 */
@Component({
    selector: 'app-audit-log',
    templateUrl: './audit-log.component.html',
    styles: [`
        /* The values are JSON from the trigger; shown as written, wrapped so a
           long row never widens the dialog. */
        .al-diff td { vertical-align: top; word-break: break-word; }
        .al-diff .al-diff__old { color: var(--text-color-secondary); }
        .al-diff .al-diff__new { font-weight: 600; }
        .al-diff tr.al-diff--same td { color: var(--text-color-secondary); font-weight: 400; }
        .al-json {
            margin: 0;
            padding: .75rem .9rem;
            border-radius: 8px;
            background: var(--surface-100);
            color: var(--text-color);
            font-size: .8rem;
            line-height: 1.45;
            white-space: pre-wrap;
            word-break: break-word;
            max-height: 22rem;
            overflow: auto;
            /* Data, not prose: keys and values read left to right in both languages. */
            direction: ltr;
            text-align: start;
            unicode-bidi: isolate;
        }
    `],
    standalone: false
})
export class AuditLogComponent implements OnInit {

    logs: AuditLog[] = [];
    totalRecords = 0;
    loading = true;
    rows = 25;
    first = 0;
    /** The column the server orders by, and the direction; newest first by default. */
    sortField = 'changedAt';
    sortOrder = -1;

    // Filters — all six the endpoint accepts.
    filterTable: string | null = null;
    filterUser: string | null = null;
    filterOperation: string | null = null;
    filterRecordId = '';
    filterFrom: Date | null = null;
    filterTo: Date | null = null;

    tableOptions: Option<string>[] = [];
    userOptions: Option<string>[] = [];
    operationOptions: Option<string>[] = [];

    // Detail dialog
    showDetail = false;
    selected: AuditLog | null = null;
    /** Every field of the selected row; the table shows the changed ones unless showAllFields. */
    fieldRows: AuditFieldRow[] = [];
    showAllFields = false;
    /** The trigger recorded column names only (STUDENT_DATA does), so there are no values to compare. */
    valuesCaptured = true;

    private readonly destroyRef = inject(DestroyRef);

    constructor(
        private auditLogService: AuditLogService,
        private messageService: MessageService,
        private translationService: TranslationService
    ) { }

    ngOnInit(): void {
        this.buildOperationOptions();
        this.loadMetadata();
        this.translationService.currentLanguage$
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.buildOperationOptions());
        // The first page comes from the table's own lazy-load event.
    }

    private buildOperationOptions(): void {
        this.operationOptions = ['INSERT', 'UPDATE', 'DELETE']
            .map(op => ({ label: this.operationLabel(op), value: op }));
    }

    /** The table and user lists come from what the trail actually holds, so a filter cannot name something no row carries. */
    private loadMetadata(): void {
        this.auditLogService.getDistinctTableNames().subscribe({
            next: names => this.tableOptions = (names ?? []).map(n => ({ label: n, value: n })),
            error: () => this.tableOptions = []
        });
        this.auditLogService.getDistinctUsers().subscribe({
            next: users => this.userOptions = (users ?? []).map(u => ({ label: u, value: u })),
            error: () => this.userOptions = []
        });
    }

    onLazyLoad(event: { first?: number | null; rows?: number | null; sortField?: string | string[] | null; sortOrder?: number | null }): void {
        this.first = event.first ?? 0;
        this.rows = event.rows ?? this.rows;
        // A header click arrives here, not in the browser: the table holds one
        // page of a table with tens of thousands of rows.
        const field = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
        if (field) {
            this.sortField = field;
            this.sortOrder = event.sortOrder ?? -1;
        }
        this.loadLogs();
    }

    loadLogs(): void {
        this.loading = true;
        this.auditLogService.getAuditLogs({
            page: Math.floor(this.first / this.rows),
            size: this.rows,
            tableName: this.filterTable || undefined,
            changedBy: this.filterUser || undefined,
            operation: this.filterOperation || undefined,
            recordId: this.filterRecordId.trim() || undefined,
            sortBy: this.sortField,
            sortDir: this.sortOrder === 1 ? 'asc' : 'desc',
            startDate: this.filterFrom ? startOfDay(this.filterFrom) : undefined,
            // The closing date is sent as the last instant of its day, or a
            // range ending today would exclude everything changed today.
            endDate: this.filterTo ? endOfDay(this.filterTo) : undefined
        }).subscribe({
            next: data => {
                this.logs = data?.content ?? [];
                this.totalRecords = data?.totalElements ?? 0;
                this.loading = false;
            },
            error: () => {
                this.logs = [];
                this.totalRecords = 0;
                this.loading = false;
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('COMMON.LOAD_ERROR')
                });
            }
        });
    }

    /** A changed filter starts again from the first page. */
    applyFilters(): void {
        this.first = 0;
        this.loadLogs();
    }

    get hasFilters(): boolean {
        return !!(this.filterTable || this.filterUser || this.filterOperation
            || this.filterRecordId.trim() || this.filterFrom || this.filterTo);
    }

    clearFilters(): void {
        this.filterTable = null;
        this.filterUser = null;
        this.filterOperation = null;
        this.filterRecordId = '';
        this.filterFrom = null;
        this.filterTo = null;
        this.applyFilters();
    }

    openDetail(log: AuditLog): void {
        this.selected = log;
        this.showAllFields = false;
        this.valuesCaptured = !!(log.oldValues || log.newValues);
        this.fieldRows = this.buildFieldRows(log);
        this.showDetail = true;
    }

    /** The rows the table shows: what changed, or everything when asked. */
    get visibleFieldRows(): AuditFieldRow[] {
        return this.showAllFields ? this.fieldRows : this.fieldRows.filter(r => r.changed);
    }

    get unchangedCount(): number {
        return this.fieldRows.filter(r => !r.changed).length;
    }

    /**
     * Old and new values side by side, one row per column (review M1). The
     * dialog used to print the two JSON blobs whole, so finding what changed
     * meant comparing them by eye.
     */
    private buildFieldRows(log: AuditLog): AuditFieldRow[] {
        const before = parseObject(log.oldValues);
        const after = parseObject(log.newValues);
        const listed = new Set(this.columnsOf(log).map(c => c.toUpperCase()));
        const keys = [...new Set([...Object.keys(before), ...Object.keys(after), ...this.columnsOf(log)])];
        return keys.map(key => {
            const o = before[key];
            const n = after[key];
            const oldValue = this.formatValue(o);
            const newValue = this.formatValue(n);
            let changed: boolean;
            if (log.operation === 'INSERT') changed = n !== null && n !== undefined && n !== '';
            else if (log.operation === 'DELETE') changed = o !== null && o !== undefined && o !== '';
            else changed = listed.has(key.toUpperCase()) || oldValue !== newValue;
            return {
                key,
                label: this.fieldLabel(key),
                oldValue,
                newValue,
                changed,
                masked: o === MASK || n === MASK
            };
        }).sort((a, b) => Number(b.changed) - Number(a.changed));
    }

    /** A column's name in the reader's language, or the column name made readable. */
    fieldLabel(key: string): string {
        const i18nKey = `AUDIT_LOGS.FIELDS.${toUpperSnake(key)}`;
        const label = this.translationService.translate(i18nKey);
        return label && label !== i18nKey ? label : humanize(key);
    }

    /** A table's name in the reader's language, or as stored. */
    tableLabel(name: string | null | undefined): string {
        if (!name) { return ''; }
        const i18nKey = `AUDIT_LOGS.TABLES.${toUpperSnake(name)}`;
        const label = this.translationService.translate(i18nKey);
        return label && label !== i18nKey ? label : name;
    }

    private formatValue(v: unknown): string {
        if (v === null || v === undefined || v === '') { return ''; }
        if (v === MASK) { return this.translationService.translate('AUDIT_LOGS.MASKED'); }
        if (typeof v === 'boolean') { return this.translationService.translate(v ? 'COMMON.YES' : 'COMMON.NO'); }
        if (typeof v === 'object') { return JSON.stringify(v); }
        const text = String(v);
        // ISO timestamps from the trigger, shown as a date and time.
        if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)) {
            return text.substring(0, 16).replace('T', ' ');
        }
        return text;
    }

    operationLabel(op: string | null | undefined): string {
        if (!op) { return ''; }
        const key = `AUDIT_LOGS.OPERATION.${op}`;
        const label = this.translationService.translate(key);
        return label === key ? op : label;
    }

    operationSeverity(op: string | null | undefined): 'success' | 'danger' | 'warn' | 'secondary' {
        return op === 'INSERT' ? 'success' : op === 'DELETE' ? 'danger' : op === 'UPDATE' ? 'warn' : 'secondary';
    }

    /** The trigger writes JSON; shown indented when it parses, as written when it does not. */
    pretty(value: string | null | undefined): string {
        if (!value) { return ''; }
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch {
            return value;
        }
    }

    /** The changed-columns list: a comma list, or a JSON array in the trigger's newer rows. */
    columnsOf(log: AuditLog | null): string[] {
        return (log?.changedColumns ?? '')
            .replace(/^\s*\[|\]\s*$/g, '')
            .split(',')
            .map(c => c.trim().replace(/^"|"$/g, ''))
            .filter(Boolean);
    }
}

function parseObject(value: string | null | undefined): Record<string, unknown> {
    if (!value) { return {}; }
    try {
        const parsed = JSON.parse(value);
        return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
    } catch {
        return {};
    }
}

/** PasswordChangedAt and STUDENT_NAME both become one key form: PASSWORD_CHANGED_AT, STUDENT_NAME. */
function toUpperSnake(key: string): string {
    return key.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/[^A-Za-z0-9]+/g, '_').toUpperCase();
}

/** STUDENT_MAJOR_ID -> Student major id; LastLoginAt -> Last login at. */
function humanize(key: string): string {
    const words = toUpperSnake(key).toLowerCase().split('_').filter(Boolean);
    const text = words.join(' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function startOfDay(d: Date): Date {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0);
}

function endOfDay(d: Date): Date {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59);
}
