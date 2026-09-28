import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { uiReportLang } from '../../../../service/report-lang.util';

interface AuditTrailRow {
    logId: number;
    changedAt: string | null;
    schemaName: string | null;
    tableName: string | null;
    recordId: string | null;
    operation: string | null;
    changedBy: string | null;
    changedColumns: string | null;
    ipAddress: string | null;
    countryId: number | null;
    countryName: string | null;
}

interface AuditTrailMetadata {
    tableNames?: string[];
    changedByOptions?: string[];
    operations?: string[];
    defaultDateFrom?: string;
    defaultDateTo?: string;
    maxRows?: number;
    countries?: Array<{ id: number; name: string }>;
}

/**
 * The audit trail report.
 *
 * <p>Filter-first by design. The trail holds tens of thousands of rows and the
 * last thirty days alone are over nine thousand, so the server refuses a range
 * that would exceed its cap rather than truncating it — an audit document that
 * silently omits records is worse than no document. This screen therefore counts
 * before it exports, and says plainly when the range is too wide.
 */
@Component({
    selector: 'app-audit-trail-report',
    templateUrl: './audit-trail.component.html',
    styleUrls: ['./audit-trail.component.scss'],
    standalone: false
})
export class AuditTrailReportComponent implements OnInit, OnDestroy {
    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translationService.getCurrentLanguage();
    }


    rows: AuditTrailRow[] = [];
    tableNames: string[] = [];
    changedByOptions: string[] = [];
    operations: string[] = [];

    dateFrom: Date | null = null;
    dateTo: Date | null = null;
    selectedTable: string | null = null;
    selectedOperation: string | null = null;
    selectedChangedBy: string | null = null;
    recordId = '';

    /** How many records the current filters cover; drives the warning below. */
    matchCount = 0;
    maxRows = 0;
    loading = false;
    selectedLang = uiReportLang(this.translationService);

    readonly pdfApiPath = '/api/reports/operations/audit-trail/export-report';
    readonly excelApiPath = '/api/reports/operations/audit-trail/export-excel';

    private readonly destroy$ = new Subject<void>();
    private readonly base = '/api/reports/operations/audit-trail';

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private messageService: MessageService,
        private lookupService: LookupService
    ) {}

    ngOnInit(): void {
        this.loadMetadata();

        this.translationService.currentLanguage$
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => this.load());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    private get lang(): string {
        return this.translationService.getCurrentLanguage() === 'en' ? 'en' : 'ar';
    }

    /** True while the range would produce more than the server will render. */
    get overCap(): boolean {
        return this.maxRows > 0 && this.matchCount > this.maxRows;
    }

    get rangeMissing(): boolean {
        return !this.dateFrom || !this.dateTo;
    }

    get canExport(): boolean {
        return !this.rangeMissing && !this.overCap && this.matchCount > 0;
    }

    loadMetadata(): void {
        this.http.get<AuditTrailMetadata>(this.apiUrl.apiPath(`${this.base}/metadata`), {
            params: new HttpParams().set('lang', this.lang)
        })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: meta => {
                    this.tableNames = meta.tableNames ?? [];
                    this.changedByOptions = meta.changedByOptions ?? [];
                    this.operations = meta.operations ?? [];
                    this.maxRows = meta.maxRows ?? 0;

                    // The server supplies the window so the screen cannot drift
                    // from the default the report itself would apply.
                    if (!this.dateFrom && meta.defaultDateFrom) {
                        this.dateFrom = new Date(meta.defaultDateFrom);
                    }
                    if (!this.dateTo && meta.defaultDateTo) {
                        this.dateTo = new Date(meta.defaultDateTo);
                    }
                    this.load();
                },
                error: () => {
                    this.tableNames = [];
                    this.changedByOptions = [];
                    this.operations = [];
                }
            });
    }

    private toIsoDate(d: Date): string {
        const month = `${d.getMonth() + 1}`.padStart(2, '0');
        const day = `${d.getDate()}`.padStart(2, '0');
        return `${d.getFullYear()}-${month}-${day}`;
    }

    private buildParams(): HttpParams {
        let params = new HttpParams().set('lang', this.lang);
        if (this.dateFrom) { params = params.set('dateFrom', this.toIsoDate(this.dateFrom)); }
        if (this.dateTo) { params = params.set('dateTo', this.toIsoDate(this.dateTo)); }
        if (this.selectedTable) { params = params.set('tableName', this.selectedTable); }
        if (this.selectedOperation) { params = params.set('operation', this.selectedOperation); }
        if (this.selectedChangedBy) { params = params.set('changedBy', this.selectedChangedBy); }
        if (this.recordId.trim()) { params = params.set('recordId', this.recordId.trim()); }
        return params;
    }

    load(): void {
        if (this.rangeMissing) {
            this.rows = [];
            this.matchCount = 0;
            return;
        }

        this.loading = true;
        const params = this.buildParams();

        // Count first: it is cheap, and it decides whether asking for the rows
        // would be refused.
        this.http.get<{ count: number }>(this.apiUrl.apiPath(`${this.base}/count`), { params })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: res => {
                    this.matchCount = res?.count ?? 0;
                    if (this.overCap) {
                        this.rows = [];
                        this.loading = false;
                        return;
                    }
                    this.loadRows(params);
                },
                error: () => {
                    this.matchCount = 0;
                    this.loading = false;
                }
            });
    }

    private loadRows(params: HttpParams): void {
        this.http.get<AuditTrailRow[]>(this.apiUrl.apiPath(`${this.base}/rows`), { params })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: data => {
                    this.rows = data ?? [];
                    this.loading = false;
                },
                error: err => {
                    this.rows = [];
                    this.loading = false;
                    this.messageService.add({
                        severity: 'warn',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: err?.error?.message
                            || this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    onFilterChange(): void {
        this.load();
    }

    clearFilters(): void {
        this.selectedTable = null;
        this.selectedOperation = null;
        this.selectedChangedBy = null;
        this.recordId = '';
        this.load();
    }

    reportQuery(): ReportPdfQuery {
        return buildReportPdfQuery([
            ['dateFrom', this.dateFrom ? this.toIsoDate(this.dateFrom) : undefined],
            ['dateTo', this.dateTo ? this.toIsoDate(this.dateTo) : undefined],
            ['tableName', this.selectedTable || undefined],
            ['operation', this.selectedOperation || undefined],
            ['changedBy', this.selectedChangedBy || undefined],
            ['recordId', this.recordId.trim() || undefined],
            ['lang', this.selectedLang]
        ]);
    }

    /**
     * A change with no attaché is a ministry-level one — a user account, a role.
     * Naming it beats an empty cell that reads as missing data.
     */
    attacheLabel(row: AuditTrailRow): string {
        return row.countryName
            || this.translationService.translate('REPORTS.AUDIT_TRAIL.MINISTRY_LEVEL');
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }
}
