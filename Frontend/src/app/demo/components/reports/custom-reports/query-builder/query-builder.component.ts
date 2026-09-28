import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface QueryBuilderData {
    tableName: string;
    columnName: string;
    dataType: string;
    columnDescription: string;
    isFilterable: number;
    isSortable: number;
}

interface QueryFilter {
    column: string | null;
    operator: string;
    value: string;
    value2: string;
}

/** One table joined to the query, and the two columns it is matched on. */
interface QueryJoin {
    table: string | null;
    type: 'INNER' | 'LEFT';
    leftTable: string | null;
    leftColumn: string | null;
    column: string | null;
    /** Held here so the template binds a stable array, never a fresh one. */
    leftColumnOptions: Array<{ label: string; value: string }>;
    columnOptions: Array<{ label: string; value: string }>;
    suggestion: string | null;
}

/**
 * A way two objects can be joined, offered by the server.
 *
 * <p>`kind` is FK when the database declares the relationship and NAME when it
 * is inferred from a shared column name — the reporting views carry no foreign
 * keys, so the inferred ones are what make them joinable at all.
 */
interface JoinSuggestion {
    fromTable: string;
    fromColumn: string;
    toTable: string;
    toColumn: string;
    kind: 'FK' | 'NAME';
}

interface SavedQuery {
    savedQueryId: number;
    queryName: string;
    description: string | null;
    sourceName: string;
    ownerUsername: string;
    shared: boolean;
}

interface QueryResult {
    columns: Array<{ name: string; dataType: string }>;
    rows: Array<Record<string, any>>;
    rowCount: number;
    truncated: boolean;
    sql: string;
    parameters: any[];
    scopeNote: string | null;
}

@Component({
    selector: 'app-query-builder',
    templateUrl: './query-builder.component.html',
    styleUrls: ['./query-builder.component.scss'],
    providers: [ConfirmationService],
    standalone: false
})
export class QueryBuilderComponent implements OnInit, OnDestroy {

    reportData: QueryBuilderData[] = [];
    /** What the table shows: the catalogue narrowed by the controls above it. */
    filteredData: QueryBuilderData[] = [];
    loading: boolean = false;

    tableOptions: Array<{ label: string; value: string }> = [];
    selectedTable: string | null = null;
    searchTerm = '';
    /** Show only the columns a query may filter or sort on. */
    filterableOnly = false;

    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        private messageService: MessageService,
        private confirmation: ConfirmationService,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        this.loadReportData();
        this.loadSavedQueries();
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.loadReportData();
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadReportData(): void {
        this.loading = true;
        this.http.get<QueryBuilderData[]>(this.apiUrl.apiPath('/api/custom-reports/query-builder'), { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (data) => {
                    this.reportData = data ?? [];
                    this.tableOptions = Array.from(new Set(this.reportData.map(r => r.tableName)))
                        .sort((a, b) => a.localeCompare(b))
                        .map(name => ({ label: name, value: name }));
                    this.applyFilters();
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading query builder:', error);
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    /**
     * The catalogue is 2,055 columns across 180 objects, so it is only navigable
     * with a filter. Applied here rather than server-side: the whole catalogue is
     * already loaded, and narrowing it is instant.
     */
    applyFilters(): void {
        const term = this.searchTerm.trim().toLowerCase();
        this.filteredData = this.reportData.filter(row => {
            if (this.selectedTable && row.tableName !== this.selectedTable) {
                return false;
            }
            if (this.filterableOnly && row.isFilterable !== 1) {
                return false;
            }
            if (!term) {
                return true;
            }
            return `${row.tableName} ${row.columnName} ${row.dataType} ${row.columnDescription ?? ''}`
                .toLowerCase()
                .includes(term);
        });
        this.describedCount = this.filteredData
            .filter(r => (r.columnDescription ?? '').trim().length > 0).length;
    }

    clearFilters(): void {
        this.selectedTable = null;
        this.searchTerm = '';
        this.filterableOnly = false;
        this.applyFilters();
    }

    get objectCount(): number {
        return this.tableOptions.length;
    }

    /**
     * Set when the filters run, not computed in a getter.
     *
     * <p>A getter bound in the template is re-evaluated on every change detection
     * cycle; scanning 2,055 rows each time is waste, and — where the value is an
     * array — a new identity each cycle is worse than waste. See
     * {@link recomputeQueryOptions}.
     */
    describedCount = 0;

    // ── Query builder ────────────────────────────────────────────────────────
    // The spec is structural: a source, columns, filters and a sort. No SQL is
    // composed here — the server does that from the catalogue, so the page
    // cannot ask for anything the catalogue does not list.

    querySource: string | null = null;
    queryColumns: string[] = [];
    queryFilters: QueryFilter[] = [];
    querySortColumn: string | null = null;
    querySortDirection: 'ASC' | 'DESC' = 'ASC';
    queryLimit = 500;

    running = false;
    queryResult: QueryResult | null = null;
    queryError: string | null = null;
    showSql = false;

    readonly operatorOptions = [
        { label: '=', value: 'EQ' },
        { label: '≠', value: 'NE' },
        { label: '>', value: 'GT' },
        { label: '≥', value: 'GE' },
        { label: '<', value: 'LT' },
        { label: '≤', value: 'LE' },
        { label: 'contains', value: 'LIKE' },
        { label: 'in', value: 'IN' },
        { label: 'between', value: 'BETWEEN' },
        { label: 'is empty', value: 'IS_NULL' },
        { label: 'is not empty', value: 'IS_NOT_NULL' }
    ];

    /**
     * Option lists for the pickers, and the sort directions.
     *
     * <p>These are plain fields, rebuilt only when the source changes. They were
     * getters, and a getter that returns a new array is re-evaluated on every
     * change detection cycle — so every cycle handed PrimeNG a new array
     * identity, which re-rendered the control, which scheduled another cycle.
     * That is a loop with no exit: it locked the browser on this page and the
     * page could not be navigated away from.
     *
     * <p>The sort directions were worse still, written as an array literal in the
     * template. That is a fresh array on every cycle whatever the state, so the
     * page froze on load, before anything was selected.
     */
    columnOptions: Array<{ label: string; value: string }> = [];
    filterableColumnOptions: Array<{ label: string; value: string }> = [];
    readonly sortDirectionOptions = [
        { label: 'ASC', value: 'ASC' },
        { label: 'DESC', value: 'DESC' }
    ];

    /**
     * Rebuilds the pickers over every table in the query.
     *
     * <p>Once a query has joins, a bare column name may be in more than one of
     * them and the server refuses it as ambiguous. So a column is offered
     * qualified whenever the query has joins, which is also what the result
     * names it — the picker, the statement and the result column agree.
     */
    private recomputeQueryOptions(): void {
        const tables = this.queryTables();
        const qualify = tables.length > 1;
        const columns = this.reportData.filter(r => tables.includes(r.tableName));

        this.columnOptions = columns.map(c => {
            const value = qualify ? `${c.tableName}.${c.columnName}` : c.columnName;
            const name = qualify ? `${c.tableName}.${c.columnName}` : c.columnName;
            return { label: c.columnDescription ? `${name} — ${c.columnDescription}` : name, value };
        });

        // Only columns the catalogue says can take a WHERE or ORDER BY.
        this.filterableColumnOptions = columns
            .filter(c => c.isFilterable === 1)
            .map(c => ({
                label: `${qualify ? c.tableName + '.' : ''}${c.columnName} (${c.dataType})`,
                value: qualify ? `${c.tableName}.${c.columnName}` : c.columnName
            }));
    }

    /** Every table in the query: the source, plus each join that names one. */
    queryTables(): string[] {
        const tables = this.querySource ? [this.querySource] : [];
        for (const join of this.queryJoins) {
            if (join.table) {
                tables.push(join.table);
            }
        }
        return tables;
    }

    onQuerySourceChange(): void {
        this.queryColumns = [];
        this.queryFilters = [];
        this.queryJoins = [];
        this.joinSuggestions = [];
        this.querySortColumn = null;
        this.queryResult = null;
        this.queryError = null;
        this.reportTicket = null;
        this.recomputeQueryOptions();
        this.loadJoinSuggestions();
    }

    // ── Joins ────────────────────────────────────────────────────────────────

    queryJoins: QueryJoin[] = [];
    joinSuggestions: JoinSuggestion[] = [];
    /** Suggestions shaped for the picker, rebuilt when the source changes. */
    joinSuggestionOptions: Array<{ label: string; value: string }> = [];
    readonly joinTypeOptions = [
        { label: 'LEFT', value: 'LEFT' },
        { label: 'INNER', value: 'INNER' }
    ];
    readonly maxJoins = 3;

    /**
     * Asks the server how this source relates to the rest of the schema.
     *
     * <p>Only for the root table: suggestions off a joined table would need the
     * user to have picked it first, and one hop is enough to build the joins
     * this page allows.
     */
    private loadJoinSuggestions(): void {
        this.joinSuggestions = [];
        this.joinSuggestionOptions = [];
        if (!this.querySource) {
            return;
        }
        const source = this.querySource;
        this.http.get<JoinSuggestion[]>(
            this.apiUrl.apiPath(`/api/custom-reports/query-builder/joins/${encodeURIComponent(source)}`))
            .subscribe({
                next: (suggestions) => {
                    // The server returns the relationship from either side; state
                    // it consistently as "this source's column -> that table's".
                    const seen = new Set<string>();
                    const normalised: JoinSuggestion[] = [];
                    for (const s of suggestions ?? []) {
                        const mine = s.fromTable === source ? s : {
                            ...s,
                            fromTable: s.toTable, fromColumn: s.toColumn,
                            toTable: s.fromTable, toColumn: s.fromColumn
                        };
                        const key = `${mine.fromColumn}|${mine.toTable}|${mine.toColumn}`;
                        if (mine.toTable !== source && !seen.has(key)) {
                            seen.add(key);
                            normalised.push(mine);
                        }
                    }
                    this.joinSuggestions = normalised;
                    this.joinSuggestionOptions = normalised.map((s, i) => ({
                        label: `${s.toTable} · ${s.fromColumn} = ${s.toColumn}`
                            + (s.kind === 'FK' ? ' ★' : ''),
                        value: String(i)
                    }));
                },
                // Suggestions are a convenience; a join can still be built by hand.
                error: () => { this.joinSuggestions = []; this.joinSuggestionOptions = []; }
            });
    }

    addJoin(): void {
        if (this.queryJoins.length >= this.maxJoins) {
            return;
        }
        this.queryJoins = [...this.queryJoins, {
            table: null, type: 'LEFT', leftTable: this.querySource,
            leftColumn: null, column: null, suggestion: null,
            leftColumnOptions: this.columnsOf(this.querySource), columnOptions: []
        }];
    }

    removeJoin(index: number): void {
        this.queryJoins = this.queryJoins.filter((_, i) => i !== index);
        this.onJoinChange();
    }

    /** Fills a join from one of the server's suggestions. */
    applyJoinSuggestion(index: number, suggestionIndex: string | null): void {
        const suggestion = suggestionIndex === null ? null : this.joinSuggestions[Number(suggestionIndex)];
        if (!suggestion) {
            return;
        }
        const join = this.queryJoins[index];
        join.table = suggestion.toTable;
        join.leftTable = suggestion.fromTable;
        join.leftColumn = suggestion.fromColumn;
        join.column = suggestion.toColumn;
        this.onJoinChange();
    }

    /**
     * Adding or removing a table changes which columns exist and how they must
     * be named, so the selections that referred to the old shape are dropped
     * rather than left to fail on the server.
     */
    onJoinChange(): void {
        this.queryColumns = [];
        this.queryFilters = [];
        this.querySortColumn = null;
        this.queryResult = null;
        this.reportTicket = null;
        this.recomputeJoinOptions();
        this.recomputeQueryOptions();
    }

    /**
     * The ON-clause pickers for each join, held on the join itself.
     *
     * <p>Not a method called from the template: a method returning a new array
     * is re-evaluated on every change detection cycle, and handing PrimeNG a new
     * array identity each cycle is what froze this page once already.
     */
    private recomputeJoinOptions(): void {
        for (const join of this.queryJoins) {
            join.leftColumnOptions = this.columnsOf(join.leftTable);
            join.columnOptions = this.columnsOf(join.table);
        }
    }

    private columnsOf(table: string | null): Array<{ label: string; value: string }> {
        if (!table) {
            return [];
        }
        return this.reportData
            .filter(r => r.tableName === table)
            .map(c => ({ label: `${c.columnName} (${c.dataType})`, value: c.columnName }));
    }

    addFilter(): void {
        this.queryFilters = [...this.queryFilters, { column: null, operator: 'EQ', value: '', value2: '' }];
    }

    removeFilter(index: number): void {
        this.queryFilters = this.queryFilters.filter((_, i) => i !== index);
    }

    needsValue(operator: string): boolean {
        return operator !== 'IS_NULL' && operator !== 'IS_NOT_NULL';
    }

    needsSecondValue(operator: string): boolean {
        return operator === 'BETWEEN';
    }

    /** The spec both Run and Save send. Structural throughout — never SQL. */
    private buildSpec() {
        return {
            source: this.querySource,
            // Only joins that name a table and both sides of the match; a
            // half-filled row is one the user has not finished, not an error.
            joins: this.queryJoins
                .filter(j => j.table && j.leftColumn && j.column)
                .map(j => ({
                    table: j.table,
                    type: j.type,
                    leftTable: j.leftTable,
                    leftColumn: j.leftColumn,
                    column: j.column
                })),
            columns: this.queryColumns,
            filters: this.queryFilters
                .filter(f => f.column)
                .map(f => ({
                    column: f.column,
                    operator: f.operator,
                    value: f.value,
                    value2: f.value2,
                    // 'in' takes a comma-separated list, split here so the server
                    // receives discrete values to bind rather than one string.
                    values: f.operator === 'IN'
                        ? f.value.split(',').map(v => v.trim()).filter(v => v.length > 0)
                        : []
                })),
            sorts: this.querySortColumn
                ? [{ column: this.querySortColumn, direction: this.querySortDirection }]
                : [],
            limit: this.queryLimit,
            countryId: this.reportCountryScope.countryIdParam()
        };
    }

    runQuery(): void {
        if (!this.querySource) {
            return;
        }
        this.running = true;
        this.queryError = null;

        const spec = this.buildSpec();

        this.http.post<QueryResult>(this.apiUrl.apiPath('/api/custom-reports/query-builder/execute'), spec)
            .subscribe({
                next: (result) => {
                    this.queryResult = result;
                    this.running = false;
                },
                error: (error) => {
                    // The server explains why a spec was refused; showing that is
                    // more use than a generic failure message.
                    this.queryError = error?.error?.message ?? error?.message
                        ?? this.translationService.translate('COMMON.LOAD_ERROR');
                    this.queryResult = null;
                    this.running = false;
                }
            });
    }

    // ── Saved queries ────────────────────────────────────────────────────────

    savedQueries: SavedQuery[] = [];
    saveName = '';
    saveDescription = '';
    saveShared = false;
    showSaveDialog = false;

    loadSavedQueries(): void {
        this.http.get<SavedQuery[]>(this.apiUrl.apiPath('/api/custom-reports/query-builder/saved'))
            .subscribe({
                next: (queries) => this.savedQueries = queries ?? [],
                // The saved-query table is created by a script that may not have
                // been applied; the builder itself works without it, so a failure
                // here must not take the page down with it.
                error: () => this.savedQueries = []
            });
    }

    openSaveDialog(): void {
        this.saveName = '';
        this.saveDescription = '';
        this.saveShared = false;
        this.showSaveDialog = true;
    }

    saveQuery(): void {
        if (!this.querySource || !this.saveName.trim()) {
            return;
        }
        const body = {
            name: this.saveName.trim(),
            description: this.saveDescription,
            shared: this.saveShared,
            spec: this.buildSpec()
        };
        this.http.post<SavedQuery>(this.apiUrl.apiPath('/api/custom-reports/query-builder/saved'), body)
            .subscribe({
                next: () => {
                    this.showSaveDialog = false;
                    this.loadSavedQueries();
                    this.messageService.add({
                        severity: 'success',
                        summary: this.translationService.translate('COMMON.SUCCESS'),
                        detail: this.translationService.translate('REPORTS.CUSTOM_REPORTS.QB_SAVED')
                    });
                },
                error: (error) => {
                    this.queryError = error?.error?.message ?? error?.message;
                    this.showSaveDialog = false;
                }
            });
    }

    /** Loads a saved query into the builder so it can be seen before it is run. */
    loadSavedQuery(saved: SavedQuery): void {
        this.http.get<any>(this.apiUrl.apiPath(`/api/custom-reports/query-builder/saved/${saved.savedQueryId}/spec`))
            .subscribe({
                next: (spec) => {
                    this.querySource = spec.source;
                    this.queryJoins = (spec.joins ?? []).map((j: any) => ({
                        table: j.table,
                        type: j.type === 'INNER' ? 'INNER' : 'LEFT',
                        leftTable: j.leftTable ?? spec.source,
                        leftColumn: j.leftColumn,
                        column: j.column,
                        suggestion: null,
                        leftColumnOptions: this.columnsOf(j.leftTable ?? spec.source),
                        columnOptions: this.columnsOf(j.table)
                    }));
                    this.loadJoinSuggestions();
                    this.recomputeQueryOptions();
                    this.queryColumns = spec.columns ?? [];
                    this.queryFilters = (spec.filters ?? []).map((f: any) => ({
                        column: f.column,
                        operator: f.operator,
                        value: f.operator === 'IN' ? (f.values ?? []).join(', ') : (f.value ?? ''),
                        value2: f.value2 ?? ''
                    }));
                    this.querySortColumn = spec.sorts?.[0]?.column ?? null;
                    this.querySortDirection = spec.sorts?.[0]?.direction ?? 'ASC';
                    this.queryLimit = spec.limit ?? 500;
                    this.queryResult = null;
                    this.queryError = null;
                },
                error: (error) => this.queryError = error?.error?.message ?? error?.message
            });
    }

    /** A saved query is someone's work; it asks before it goes. */
    deleteSavedQuery(saved: SavedQuery): void {
        this.confirmation.confirm({
            header: this.translationService.translate('COMMON.DELETE'),
            message: this.translationService.translate('REPORTS.CUSTOM_REPORTS.QB_CONFIRM_DELETE_SAVED')
                .replace('{name}', saved.queryName ?? ''),
            icon: 'pi pi-exclamation-triangle',
            acceptButtonStyleClass: 'p-button-danger',
            acceptLabel: this.translationService.translate('COMMON.YES'),
            rejectLabel: this.translationService.translate('COMMON.NO'),
            accept: () => this.http.delete(this.apiUrl.apiPath(`/api/custom-reports/query-builder/saved/${saved.savedQueryId}`))
                .subscribe({
                    next: () => this.loadSavedQueries(),
                    error: (error) => this.queryError = error?.error?.message ?? error?.message
                })
        });
    }

    resetQuery(): void {
        this.querySource = null;
        this.onQuerySourceChange();
        this.queryLimit = 500;
    }

    // ── Reporting on the result ──────────────────────────────────────────────
    // The PDF endpoints are reached by iframe and direct navigation, so they
    // must be GETs — and a spec with joins and filters will not fit in a query
    // string. The spec is posted once for a ticket, and the ticket goes in the
    // PDF URL. The server re-runs the query when rendering, under this user's
    // own scope, so the document can never show more than the result did.

    reportTicket: string | null = null;
    reportPreparing = false;

    /** Column count the server can lay out on one landscape page. */
    readonly maxPrintColumns = 15;

    get tooManyColumnsToPrint(): boolean {
        return this.queryResult !== null && this.queryResult.columns.length > this.maxPrintColumns;
    }

    private withTicket(then: (ticket: string) => void): void {
        if (!this.querySource) {
            return;
        }
        this.reportPreparing = true;
        this.http.post<{ ticket: string }>(
            this.apiUrl.apiPath('/api/custom-reports/query-builder/result-report/ticket'),
            this.buildSpec())
            .subscribe({
                next: (response) => {
                    this.reportPreparing = false;
                    this.reportTicket = response.ticket;
                    then(response.ticket);
                },
                error: (error) => {
                    this.reportPreparing = false;
                    this.queryError = error?.error?.message ?? error?.message
                        ?? this.translationService.translate('COMMON.LOAD_ERROR');
                }
            });
    }

    previewResultReport(): void {
        this.withTicket(ticket => this.reportPdfService.previewPdf(
            '/api/custom-reports/query-builder/result-report/export-report',
            buildReportPdfQuery([['ticket', ticket], ['lang', this.selectedLang]]),
            'query-result.pdf'));
    }

    downloadResultReport(): void {
        this.withTicket(ticket => this.reportPdfService.downloadPdf(
            '/api/custom-reports/query-builder/result-report/export-report',
            buildReportPdfQuery([['ticket', ticket], ['lang', this.selectedLang]]),
            'query-result.pdf'));
    }

    exportQueryResult(): void {
        if (!this.queryResult) {
            return;
        }
        const blob = new Blob([JSON.stringify(this.queryResult.rows, null, 2)], { type: 'application/json' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `query-${this.querySource}-${new Date().getTime()}.json`;
        link.click();
    }

    reportLoading = false;
    pdfApiPath = '/api/custom-reports/query-builder/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...buildReportPdfQuery([['countryId', this.reportCountryScope.countryIdParam()]]), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */
    reportQuery(): ReportPdfQuery {
        return { ...buildReportPdfQuery([['countryId', this.reportCountryScope.countryIdParam()]]), lang: this.selectedLang };
    }

    /**
     * A field, not a getter. The service builds a new array on each call, and a
     * new array identity every change detection cycle is what locked this page
     * up before — the option lists here are all resolved once for that reason.
     */
    readonly reportLangOptions: Array<{ label: string; value: string }> =
        this.lookupService.getReportLangOptions();

}

