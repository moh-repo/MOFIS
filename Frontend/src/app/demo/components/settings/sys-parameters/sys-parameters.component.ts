import { Table } from 'primeng/table';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { MessageService, SortEvent } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { SysParameter, SysParameterService, SysParameterValueType } from '../../../service/sys-parameter.service';
import { sortRows } from '../../../service/table-sort.util';

/** A row of the table with its edit buffer. */
export interface SysParameterRow extends SysParameter {
    editing: boolean;
    /** The value as typed: a number for INT/DECIMAL, a boolean for BOOL, text otherwise. */
    draft: string | number | boolean | null;
    saving: boolean;
}

/**
 * System parameters (`SYS_PARAMETER`): the dashboard windows, the document
 * expiry ladder, the audit window, the matching floor. One table, the value
 * edited inline with the control its type calls for.
 */
@Component({
    selector: 'app-sys-parameters',
    templateUrl: './sys-parameters.component.html',
    standalone: false,
    providers: [MessageService]
})
export class SysParametersComponent implements OnInit, OnDestroy {

    rows: SysParameterRow[] = [];
    loading = false;
    currentLanguage = 'en';

    private readonly subscriptions = new Subscription();

    constructor(
        private service: SysParameterService,
        private messageService: MessageService,
        private permissions: UmsPermissionService,
        public translationService: TranslationService
    ) { }

    // Filters. The parameters are held whole, so the table narrows itself.
    filterSearch = '';
    filterType: string | null = null;
    private typeOptionsFor: { key: string; options: { label: string; value: string }[] } | null = null;

    /** One entry per value type the rows actually carry; rebuilt when the rows change. */
    get typeOptions(): { label: string; value: string }[] {
        const types = Array.from(new Set(this.rows.map(r => r.valueType).filter(Boolean))).sort();
        const key = types.join('|');
        if (this.typeOptionsFor?.key !== key) {
            this.typeOptionsFor = { key, options: types.map(t => ({ label: t, value: t })) };
        }
        return this.typeOptionsFor.options;
    }

    get hasFilters(): boolean {
        return !!this.filterSearch || this.filterType !== null;
    }

    clearFilters(dt: Table): void {
        this.filterSearch = '';
        this.filterType = null;
        dt.filterGlobal('', 'contains');
        dt.filter(null, 'valueType', 'equals');
    }

    ngOnInit(): void {
        this.currentLanguage = this.translationService.getCurrentLanguage();
        this.subscriptions.add(this.translationService.currentLanguage$.subscribe(lang => { this.currentLanguage = lang; }));
        this.load();
    }

    ngOnDestroy(): void {
        this.subscriptions.unsubscribe();
    }

    get canEdit(): boolean {
        return this.permissions.hasPermission('SETTINGS.EDIT');
    }

    load(): void {
        this.loading = true;
        this.service.getAll().subscribe({
            next: params => {
                this.rows = (params || []).map(p => ({ ...p, editing: false, draft: SysParametersComponent.parse(p), saving: false }));
                this.loading = false;
            },
            error: () => {
                this.rows = [];
                this.loading = false;
                this.toast('error', 'SETTINGS.SYS_PARAMETERS.LOAD_ERROR');
            }
        });
    }

    /**
     * Sorts by what the column prints: the name in the reader's language, the
     * value as it is displayed (a BOOL prints yes/no, not 1/0). The other three
     * columns are plain properties and need no accessor.
     */
    sortTable(event: SortEvent): void {
        sortRows(event, {
            name: (row: SysParameter) => this.name(row),
            value: (row: SysParameter) => this.display(row)
        }, this.currentLanguage);
    }

    name(row: SysParameter): string {
        const primary = this.currentLanguage === 'ar' ? row.nameAr : row.nameEn;
        const other = this.currentLanguage === 'ar' ? row.nameEn : row.nameAr;
        return primary || other || row.key;
    }

    /** What the table shows for a value: a localised yes/no for BOOL, the text otherwise. */
    display(row: SysParameter): string {
        if (row.valueType === 'BOOL') {
            return this.translationService.translate(SysParametersComponent.isTrue(row.value) ? 'COMMON.YES' : 'COMMON.NO');
        }
        return row.value ?? '';
    }

    edit(row: SysParameterRow): void {
        if (!this.canEdit) return;
        row.draft = SysParametersComponent.parse(row);
        row.editing = true;
    }

    cancel(row: SysParameterRow): void {
        row.editing = false;
        row.draft = SysParametersComponent.parse(row);
    }

    save(row: SysParameterRow): void {
        const value = SysParametersComponent.serialise(row.valueType, row.draft);
        if (value === null) {
            this.toast('warn', 'SETTINGS.SYS_PARAMETERS.INVALID_VALUE');
            return;
        }
        row.saving = true;
        this.service.update(row.key, value).subscribe({
            next: saved => {
                Object.assign(row, saved, { editing: false, saving: false });
                row.draft = SysParametersComponent.parse(row);
                this.toast('success', 'SETTINGS.SYS_PARAMETERS.SAVED');
            },
            error: err => {
                row.saving = false;
                this.toast('error', 'SETTINGS.SYS_PARAMETERS.SAVE_ERROR', err?.error?.error || err?.error?.message);
            }
        });
    }

    /** The stored text as the control's value. */
    static parse(p: SysParameter): string | number | boolean | null {
        if (SysParametersComponent.isNumeric(p.valueType)) {
            const n = Number(p.value);
            return p.value == null || p.value === '' || Number.isNaN(n) ? null : n;
        }
        if (p.valueType === 'BOOL') {
            return SysParametersComponent.isTrue(p.value);
        }
        return p.value ?? '';
    }

    /** The control's value as the text the server stores; null when it is not a valid value of the type. */
    static serialise(type: SysParameterValueType, draft: string | number | boolean | null): string | null {
        if (SysParametersComponent.isNumeric(type)) {
            const valid = typeof draft === 'number' && !Number.isNaN(draft) && (type === 'DECIMAL' || Number.isInteger(draft));
            return valid ? String(draft) : null;
        }
        if (type === 'BOOL') {
            return draft ? 'true' : 'false';
        }
        return draft == null ? '' : String(draft);
    }

    /** INT and DECIMAL are edited with a number control. */
    static isNumeric(type: SysParameterValueType): boolean {
        return type === 'INT' || type === 'DECIMAL';
    }

    static isTrue(value: string | null | undefined): boolean {
        return ['true', '1', 'yes', 'y'].includes(String(value ?? '').trim().toLowerCase());
    }

    private toast(severity: 'success' | 'warn' | 'error', key: string, detail?: string): void {
        this.messageService.add({
            severity,
            summary: this.translationService.translate(key),
            detail: detail || undefined,
            life: 4000
        });
    }
}
