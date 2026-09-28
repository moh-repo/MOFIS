import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, filter, map, shareReplay, take, tap } from 'rxjs/operators';
import { ApiUrlService } from './api-url.service';
import { TranslationService } from './translation.service';

/** PrimeNG's tag/toast vocabulary. It is PrimeNG's API, not business data. */
export const PRIMENG_SEVERITIES = ['success', 'info', 'warn', 'danger', 'secondary', 'contrast'] as const;
export type PrimeSeverity = typeof PRIMENG_SEVERITIES[number];

/** One row of a code list as `GET /api/v1/code-lists` returns it. */
export interface EnumOption {
    /** Server-default name (Arabic); pick labelAr/labelEn client-side by language. */
    label: string;
    value: string;
    labelAr: string;
    labelEn: string;
    sortOrder: number;
    uiSeverity: PrimeSeverity | null;
    /** PrimeIcons name, e.g. `pi-check`. */
    icon: string | null;
    isSystem: boolean;
    active: boolean;
    /** The list's extra column (a hex colour, a lookup key, a normal balance…), when it has one. */
    extra: string | null;
}

/** One entry of `GET /api/v1/code-lists/names`. */
export interface CodeListName {
    name: string;
    editable: boolean;
    /** Name of the list's extra column, or '' when it has none. */
    extra: string;
    /** Schema-qualified table the list is served from, e.g. `MOHECORE.LU_ALERT_SEVERITY`. */
    table?: string;
}

/** Body of POST/PUT on `/api/v1/code-lists/{list}`. */
export interface CodeRowInput {
    code?: string;
    nameAr?: string;
    nameEn?: string;
    sortOrder?: number;
    uiSeverity?: string | null;
    icon?: string | null;
    active?: boolean;
    extra?: string | null;
}

/**
 * Every code list of the system, loaded once per session.
 *
 * The bulk payload carries both names, the tag colour, the icon and the sort
 * order for every row of every list — inactive rows included, so a historical
 * value still resolves to its name. The map is language-independent: a
 * language switch only changes which of the two names is read, so nothing is
 * cleared or re-fetched on it.
 *
 * Loading is triggered by the route guards (see UmsPermissionGuard and
 * StudentPortalGuard) in the same step that waits for the permission map, so a
 * feature route activates with the map warm and the `codeLabel` /
 * `codeSeverity` / `codeIcon` pipes can be pure, synchronous lookups.
 */
@Injectable({ providedIn: 'root' })
export class CodeListService {

    private lists = new Map<string, Map<string, EnumOption>>();
    private readonly loadedSubject = new BehaviorSubject<boolean>(false);
    private inflight: Observable<boolean> | null = null;

    /** True once the bulk payload has been stored (or a load has failed and given up). */
    readonly loaded$ = this.loadedSubject.asObservable();

    constructor(
        private http: HttpClient,
        private apiUrl: ApiUrlService,
        private translationService: TranslationService
    ) { }

    get isLoaded(): boolean {
        return this.loadedSubject.value;
    }

    /**
     * Loads the bulk payload if it has not been loaded yet. Concurrent callers
     * share one request. `headers` lets the student portal send its own token.
     */
    ensureLoaded(headers?: Record<string, string>): Observable<boolean> {
        if (this.isLoaded) {
            return of(true);
        }
        return this.reload(headers);
    }

    /** Fetches the bulk payload again (after an admin edit, or on first use). */
    reload(headers?: Record<string, string>): Observable<boolean> {
        if (this.inflight) {
            return this.inflight;
        }
        this.inflight = this.http
            .get<Record<string, EnumOption[]>>(this.apiUrl.apiPath('/api/v1/code-lists'), { headers: headers ?? {} })
            .pipe(
                map(payload => this.store(payload)),
                catchError(() => of(false)),
                // Settled either way: a failed load must not block route activation
                // forever, the pipes simply fall back to the raw codes.
                tap(() => { this.inflight = null; this.loadedSubject.next(true); }),
                shareReplay(1)
            );
        return this.inflight;
    }

    /** Emits once the map is warm; never errors. */
    whenLoaded(): Observable<boolean> {
        return this.loaded$.pipe(filter(Boolean), take(1));
    }

    /** Replaces the stored map with a payload (also used by the tests). */
    store(payload: Record<string, EnumOption[]> | null | undefined): boolean {
        const next = new Map<string, Map<string, EnumOption>>();
        for (const [list, rows] of Object.entries(payload ?? {})) {
            const byCode = new Map<string, EnumOption>();
            for (const row of Array.isArray(rows) ? rows : []) {
                if (row && row.value != null) {
                    byCode.set(this.key(row.value), row);
                }
            }
            next.set(list, byCode);
        }
        this.lists = next;
        return next.size > 0;
    }

    /** Clears the map (a fresh login reloads it). */
    clear(): void {
        this.lists = new Map();
        this.loadedSubject.next(false);
    }

    /** Names of the lists the payload carried. */
    listNames(): string[] {
        return Array.from(this.lists.keys()).sort();
    }

    has(list: string): boolean {
        return this.lists.has(list);
    }

    /** Every row of a list, active and inactive, in sort order. */
    rows(list: string): EnumOption[] {
        const byCode = this.lists.get(list);
        if (!byCode) {
            return [];
        }
        return Array.from(byCode.values()).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
    }

    /** Active rows of a list, in sort order. */
    active(list: string): EnumOption[] {
        return this.rows(list).filter(r => r.active !== false);
    }

    /** Case-insensitive lookup of one row. */
    find(list: string, code: string | number | null | undefined): EnumOption | undefined {
        if (code == null || code === '') {
            return undefined;
        }
        return this.lists.get(list)?.get(this.key(code));
    }

    /**
     * Name of a code in the given (or current) language. Falls back to the
     * other language, then to the code itself — so a code the table does not
     * know is still shown rather than hidden.
     */
    label(list: string, code: string | number | null | undefined, lang?: string): string {
        if (code == null || code === '') {
            return '';
        }
        const row = this.find(list, code);
        if (!row) {
            return String(code);
        }
        const arabic = (lang ?? this.translationService.getCurrentLanguage()) !== 'en';
        const primary = arabic ? row.labelAr : row.labelEn;
        const fallback = arabic ? row.labelEn : row.labelAr;
        return primary || fallback || row.label || String(code);
    }

    /** Tag severity for a code, validated against PrimeNG's vocabulary; `secondary` otherwise. */
    severity(list: string, code: string | number | null | undefined): PrimeSeverity {
        return CodeListService.toSeverity(this.find(list, code)?.uiSeverity);
    }

    /**
     * Severity of a code that may belong to any of several lists (a mixed
     * statement such as loans + books allowances): the first list holding the
     * code decides; `secondary` when none does.
     */
    severityIn(lists: string[], code: string | number | null | undefined): PrimeSeverity {
        for (const list of lists) {
            const row = this.find(list, code);
            if (row) {
                return CodeListService.toSeverity(row.uiSeverity);
            }
        }
        return 'secondary';
    }

    /** PrimeIcons class name (`pi-…`) for a code, or null when the row has none. */
    icon(list: string, code: string | number | null | undefined): string | null {
        return this.find(list, code)?.icon || null;
    }

    /** The list's extra column for a code (a colour for graph-category / study-status). */
    color(list: string, code: string | number | null | undefined): string | null {
        return this.find(list, code)?.extra || null;
    }

    /** The extra column, whatever it holds (lookup key, normal balance…). */
    extra(list: string, code: string | number | null | undefined): string | null {
        return this.find(list, code)?.extra || null;
    }

    /** Active rows as `{label, value}` in the current (or given) language, for dropdowns. */
    options(list: string, lang?: string): Array<{ label: string; value: string }> {
        return this.active(list).map(row => ({ label: this.label(list, row.value, lang), value: row.value }));
    }

    /**
     * ng-zorro's `nzColor` preset for a code. PrimeNG's severity is what the
     * table stores; ng-zorro screens translate it to their own five presets.
     */
    nzColor(list: string, code: string | number | null | undefined): string {
        return CodeListService.toNzColor(this.find(list, code)?.uiSeverity);
    }

    /** `success|info|warn|danger|secondary|contrast`, else `secondary`. */
    static toSeverity(value: string | null | undefined): PrimeSeverity {
        const v = (value ?? '').toLowerCase();
        return (PRIMENG_SEVERITIES as readonly string[]).includes(v) ? (v as PrimeSeverity) : 'secondary';
    }

    /** PrimeNG severity → ng-zorro tag preset. */
    static toNzColor(value: string | null | undefined): string {
        switch (CodeListService.toSeverity(value)) {
            case 'success': return 'success';
            case 'info': return 'processing';
            case 'warn': return 'warning';
            case 'danger': return 'error';
            default: return 'default';
        }
    }

    /** PrimeNG severity → MessageService (toast) severity. */
    static toToastSeverity(value: string | null | undefined): 'success' | 'info' | 'warn' | 'error' | 'secondary' | 'contrast' {
        const s = CodeListService.toSeverity(value);
        return s === 'danger' ? 'error' : s;
    }

    // ── admin API (settings/code-lists) ──────────────────────────────────────

    getNames(): Observable<CodeListName[]> {
        return this.http.get<CodeListName[]>(this.apiUrl.apiPath('/api/v1/code-lists/names'));
    }

    getRows(list: string): Observable<EnumOption[]> {
        return this.http.get<EnumOption[]>(this.apiUrl.apiPath(`/api/v1/code-lists/${encodeURIComponent(list)}`));
    }

    createRow(list: string, body: CodeRowInput): Observable<EnumOption> {
        return this.http.post<EnumOption>(this.apiUrl.apiPath(`/api/v1/code-lists/${encodeURIComponent(list)}`), body);
    }

    updateRow(list: string, id: number, body: CodeRowInput): Observable<EnumOption> {
        return this.http.put<EnumOption>(this.apiUrl.apiPath(`/api/v1/code-lists/${encodeURIComponent(list)}/${id}`), body);
    }

    private key(code: string | number): string {
        return String(code).trim().toUpperCase();
    }
}
