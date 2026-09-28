import { Table } from 'primeng/table';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { CodeListName, CodeListService, CodeRowInput, EnumOption, PRIMENG_SEVERITIES } from '../../../service/code-list.service';
import { CodeListGroup, CODE_LIST_GROUPS, codeListKey, describeCodeList, OwningScreen } from './code-list-catalog';

/**
 * A list as the picker shows it: the API's entry, plus what the catalogue says
 * it is — the human title, the one-line purpose, its group, and the screen that
 * owns it when this one only reads it.
 */
export interface CodeListOption extends CodeListName {
    title: string;
    description: string;
    group: CodeListGroup;
    screen?: OwningScreen;
    /** Title, slug and table in one string: what the picker's filter matches. */
    search: string;
}

/** One group of the picker. */
export interface CodeListGroupOption {
    label: string;
    items: CodeListOption[];
}

/** A code-list row with its database id, as the admin endpoint serves it. */
export interface CodeListRow extends EnumOption {
    id?: number;
}

/** The add/edit dialog's fields. */
export interface CodeRowDraft {
    id: number | null;
    code: string;
    nameAr: string;
    nameEn: string;
    sortOrder: number | null;
    uiSeverity: string | null;
    icon: string;
    extra: string;
    active: boolean;
    isSystem: boolean;
}

/**
 * Code lists: every LU table of the system on one page. Pick a list, see its
 * rows (code, both names, order, tag colour, icon, the list's extra column
 * when it has one, active), add or edit a row. Rows the code depends on
 * (`IS_SYSTEM`) are shown locked: their code cannot change and they cannot be
 * deactivated or deleted — the server refuses with 409 and its message is
 * what the user sees.
 *
 * The API names a list with a slug and nothing more, which told a reader of
 * this screen nothing about what `boolean-filter` or `ums-role-kind` is for.
 * `code-list-catalog.ts` supplies the rest: the picker groups the lists by the
 * part of the system that uses them, and the panel under it names the selected
 * list, says what it drives and — for a list this screen only reads — links to the
 * screen that does own it. Rows are switched off, never deleted: a delete set the
 * same flag, so the "deleted" code came back as an inactive one (QA B14, M4).
 */
@Component({
    selector: 'app-code-lists',
    templateUrl: './code-lists.component.html',
    styleUrls: ['./code-lists.component.scss'],
    standalone: false,
    providers: [MessageService]
})
export class CodeListsComponent implements OnInit, OnDestroy {

    lists: CodeListOption[] = [];
    /** The same lists, in the picker's groups; a group with no list is left out. */
    groupedLists: CodeListGroupOption[] = [];
    selectedList: string | null = null;
    rows: CodeListRow[] = [];
    helpVisible = false;

    // Filters. The selected list is held whole, so the table narrows itself.
    filterSearch = '';
    filterActive: boolean | null = null;
    private activeOptionsFor: { lang: string; options: { label: string; value: boolean | null }[] } | null = null;

    /** Rebuilt when the language changes, and only then, so the select keeps one array between changes. */
    get activeOptions(): { label: string; value: boolean | null }[] {
        const lang = this.translationService.getCurrentLanguage();
        if (this.activeOptionsFor?.lang !== lang) {
            this.activeOptionsFor = {
                lang,
                options: [
                    { label: this.translationService.translate('COMMON.ALL'), value: null },
                    { label: this.translationService.translate('COMMON.ACTIVE'), value: true },
                    { label: this.translationService.translate('COMMON.INACTIVE'), value: false }
                ]
            };
        }
        return this.activeOptionsFor.options;
    }

    get hasFilters(): boolean {
        return !!this.filterSearch || this.filterActive !== null;
    }

    clearFilters(dt: Table): void {
        this.filterSearch = '';
        this.filterActive = null;
        dt.filterGlobal('', 'contains');
        dt.filter(null, 'active', 'equals');
    }
    listsLoading = false;
    rowsLoading = false;
    currentLanguage = 'en';

    dialogVisible = false;
    saving = false;
    draft: CodeRowDraft = CodeListsComponent.emptyDraft();

    /** PrimeNG's own severity vocabulary — the picker's choices are PrimeNG's API, not business data. */
    readonly severityOptions = PRIMENG_SEVERITIES.map(s => ({ label: s, value: s }));

    private readonly subscriptions = new Subscription();

    constructor(
        private codeLists: CodeListService,
        private messageService: MessageService,
        private permissions: UmsPermissionService,
        public translationService: TranslationService
    ) { }

    ngOnInit(): void {
        this.currentLanguage = this.translationService.getCurrentLanguage();
        this.subscriptions.add(this.translationService.currentLanguage$.subscribe(lang => {
            this.currentLanguage = lang;
            // Title and purpose are translations; a language switch re-reads them.
            this.lists = this.lists.map(l => this.describe(l));
            this.buildGroups();
        }));
        this.loadLists();
    }

    ngOnDestroy(): void {
        this.subscriptions.unsubscribe();
    }

    get canEdit(): boolean {
        return this.permissions.hasPermission('SETTINGS.EDIT');
    }

    /** The selected list: what it is, is it editable, what is its extra column called. */
    get selected(): CodeListOption | undefined {
        return this.lists.find(l => l.name === this.selectedList);
    }

    get extraColumn(): string {
        return this.selected?.extra || '';
    }

    /** The extra column under a readable name; the raw column name when it has none yet. */
    get extraColumnLabel(): string {
        const column = this.extraColumn;
        return column ? this.t('SETTINGS.CODE_LISTS.EXTRA.' + column, column) : '';
    }

    /** What the extra column holds, for the dialog; empty when nothing is written for it. */
    get extraColumnHint(): string {
        const column = this.extraColumn;
        return column ? this.t('SETTINGS.CODE_LISTS.EXTRA_HINTS.' + column, '') : '';
    }

    get editable(): boolean {
        return this.canEdit && !!this.selected?.editable;
    }

    /** How many rows are locked, and how many are switched off. */
    get systemCount(): number {
        return this.rows.filter(r => r.isSystem).length;
    }

    get inactiveCount(): number {
        return this.rows.filter(r => r.active === false).length;
    }

    /** A translation, or `fallback` when the key has no entry (translate() echoes the key). */
    t(key: string, fallback: string): string {
        const value = this.translationService.translate(key);
        return value === key ? fallback : value;
    }

    /** The catalogue's view of a list: title, purpose, group, owning screen. */
    private describe(list: CodeListName): CodeListOption {
        const entry = describeCodeList(list.name);
        const key = codeListKey(list.name);
        const title = this.t('SETTINGS.CODE_LISTS.LIST_NAMES.' + key, list.name);
        return {
            ...list,
            title,
            description: this.t('SETTINGS.CODE_LISTS.LIST_DESCRIPTIONS.' + key, ''),
            group: entry.group,
            screen: entry.screen,
            search: title + ' ' + list.name + ' ' + (list.table || '')
        };
    }

    /** The picker's groups, in the catalogue's order, each sorted by title. */
    private buildGroups(): void {
        this.groupedLists = CODE_LIST_GROUPS
            .map(group => ({
                label: this.t('SETTINGS.CODE_LISTS.GROUPS.' + group, group),
                items: this.lists.filter(l => l.group === group).sort((a, b) => a.title.localeCompare(b.title))
            }))
            .filter(g => g.items.length > 0);
    }

    loadLists(): void {
        this.listsLoading = true;
        this.codeLists.getNames().subscribe({
            next: names => {
                this.lists = (names || []).map(n => this.describe(n)).sort((a, b) => a.name.localeCompare(b.name));
                this.buildGroups();
                this.listsLoading = false;
                if (!this.selectedList && this.lists.length) {
                    this.selectList(this.lists[0].name);
                }
            },
            error: () => {
                this.lists = [];
                this.groupedLists = [];
                this.listsLoading = false;
                this.toast('error', 'SETTINGS.CODE_LISTS.LOAD_ERROR');
            }
        });
    }

    selectList(name: string | null): void {
        this.selectedList = name;
        this.rows = [];
        if (!name) return;
        this.rowsLoading = true;
        this.codeLists.getRows(name).subscribe({
            next: rows => {
                // `active` is absent on a row that was never switched off; the active filter compares it, so it is spelled out.
                this.rows = (rows || []).map(r => ({ ...r, active: r.active !== false })).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
                this.rowsLoading = false;
            },
            error: () => {
                this.rows = [];
                this.rowsLoading = false;
                this.toast('error', 'SETTINGS.CODE_LISTS.LOAD_ERROR');
            }
        });
    }

    label(row: EnumOption): string {
        const primary = this.currentLanguage === 'ar' ? row.labelAr : row.labelEn;
        const other = this.currentLanguage === 'ar' ? row.labelEn : row.labelAr;
        return primary || other || row.value;
    }

    openAdd(): void {
        this.draft = CodeListsComponent.emptyDraft();
        this.draft.sortOrder = (this.rows.reduce((max, r) => Math.max(max, r.sortOrder ?? 0), 0) || 0) + 1;
        this.dialogVisible = true;
    }

    openEdit(row: CodeListRow): void {
        this.draft = {
            id: row.id ?? null,
            code: row.value,
            nameAr: row.labelAr ?? '',
            nameEn: row.labelEn ?? '',
            sortOrder: row.sortOrder ?? null,
            uiSeverity: row.uiSeverity ?? null,
            icon: row.icon ?? '',
            extra: row.extra ?? '',
            active: row.active !== false,
            isSystem: !!row.isSystem
        };
        this.dialogVisible = true;
    }

    get draftValid(): boolean {
        return !!this.draft.code.trim() && (!!this.draft.nameAr.trim() || !!this.draft.nameEn.trim());
    }

    save(): void {
        if (!this.selectedList || !this.draftValid) return;
        const body: CodeRowInput = {
            code: this.draft.code.trim(),
            nameAr: this.draft.nameAr.trim(),
            nameEn: this.draft.nameEn.trim(),
            sortOrder: this.draft.sortOrder ?? undefined,
            uiSeverity: this.draft.uiSeverity || null,
            icon: this.draft.icon.trim() || null,
            active: this.draft.active,
            extra: this.extraColumn ? (this.draft.extra.trim() || null) : undefined
        };
        this.saving = true;
        const request = this.draft.id == null
            ? this.codeLists.createRow(this.selectedList, body)
            : this.codeLists.updateRow(this.selectedList, this.draft.id, body);
        request.subscribe({
            next: () => {
                this.saving = false;
                this.dialogVisible = false;
                this.toast('success', 'SETTINGS.CODE_LISTS.SAVED');
                this.afterWrite();
            },
            error: err => {
                this.saving = false;
                this.toast('error', 'SETTINGS.CODE_LISTS.SAVE_ERROR', this.serverMessage(err));
            }
        });
    }

    /** Active on/off straight from the grid; a system row cannot be deactivated (409). */
    toggleActive(row: CodeListRow): void {
        if (!this.selectedList || row.id == null) return;
        const active = row.active === false;
        this.codeLists.updateRow(this.selectedList, row.id, { active }).subscribe({
            next: saved => {
                Object.assign(row, saved);
                this.toast('success', 'SETTINGS.CODE_LISTS.SAVED');
                this.codeLists.reload().subscribe();
            },
            error: err => this.toast('error', 'SETTINGS.CODE_LISTS.SAVE_ERROR', this.serverMessage(err))
        });
    }

    /** Re-reads the grid and the bulk map every screen renders from. */
    private afterWrite(): void {
        this.selectList(this.selectedList);
        this.codeLists.reload().subscribe();
    }

    /** The server's own message (a 409 says which rule refused the write). */
    serverMessage(err: any): string | undefined {
        return err?.error?.message || err?.error?.error || (typeof err?.error === 'string' ? err.error : undefined);
    }

    static emptyDraft(): CodeRowDraft {
        return { id: null, code: '', nameAr: '', nameEn: '', sortOrder: null, uiSeverity: null, icon: '', extra: '', active: true, isSystem: false };
    }

    private toast(severity: 'success' | 'warn' | 'error', key: string, detail?: string): void {
        this.messageService.add({
            severity,
            summary: this.translationService.translate(key),
            detail: detail || undefined,
            life: 5000
        });
    }
}
