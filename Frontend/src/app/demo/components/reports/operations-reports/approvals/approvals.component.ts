import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface ApprovalRow {
    id: number;
    requestType: string | null;
    entityType: string | null;
    entityId: string | null;
    status: string | null;
    currentLevel: number | null;
    requestedBy: string | null;
    requestedAt: string | null;
    ageDays: number;
    countryId: number | null;
    countryName: string | null;
    note: string | null;
}

/**
 * The approvals queue.
 *
 * <p>Sorted oldest first, because the question a queue report answers is what
 * has been waiting longest — not what was raised most recently.
 */
@Component({
    selector: 'app-approvals-report',
    templateUrl: './approvals.component.html',
    styleUrls: ['./approvals.component.scss'],
    standalone: false
})
export class ApprovalsReportComponent implements OnInit, OnDestroy {
    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translationService.getCurrentLanguage();
    }


    rows: ApprovalRow[] = [];
    states: Array<{ code: string; name: string }> = [];
    allowedCountries: { label: string; value: number }[] = [];

    selectedState: string | null = null;
    selectedCountryIds: number[] = [];

    loading = false;
    selectedLang = uiReportLang(this.translationService);

    readonly pdfApiPath = '/api/reports/operations/approvals/export-report';
    readonly excelApiPath = '/api/reports/operations/approvals/export-excel';

    private readonly destroy$ = new Subject<void>();
    private readonly base = '/api/reports/operations/approvals';

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private messageService: MessageService,
        private lookupService: LookupService
    ) {}

    ngOnInit(): void {
        this.loadMetadata();
        this.load();

        this.reportCountryScope.selectedCountryId$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => { this.selectedCountryIds = []; this.load(); });

        this.translationService.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => { this.loadMetadata(); this.load(); });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    get hasMultiCountry(): boolean {
        return this.reportCountryScope.hasMultiCountryAccess();
    }

    private get lang(): string {
        return this.translationService.getCurrentLanguage() === 'en' ? 'en' : 'ar';
    }

    loadMetadata(): void {
        this.http.get<any>(this.apiUrl.apiPath(`${this.base}/metadata`), {
            params: new HttpParams().set('lang', this.lang)
        })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: meta => {
                    this.states = meta?.states ?? [];
                    this.allowedCountries = (meta?.countries ?? [])
                        .map((c: any) => ({ label: c.name, value: c.id }));
                },
                error: () => { this.states = []; this.allowedCountries = []; }
            });
    }

    private buildParams(): HttpParams {
        let params = this.selectedCountryIds.length > 0
            ? this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds)
            : this.reportCountryScope.appendCountryParam();
        params = params.set('lang', this.lang);
        if (this.selectedState) { params = params.set('state', this.selectedState); }
        return params;
    }

    load(): void {
        this.loading = true;
        this.http.get<ApprovalRow[]>(this.apiUrl.apiPath(`${this.base}/rows`), { params: this.buildParams() })
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: data => { this.rows = data ?? []; this.loading = false; },
                error: err => {
                    this.rows = [];
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
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
        this.selectedState = null;
        this.selectedCountryIds = [];
        this.load();
    }

    reportQuery(): ReportPdfQuery {
        const countryEntries: [string, any][] = [];
        if (this.selectedCountryIds.length === 1) {
            countryEntries.push(['countryId', this.selectedCountryIds[0]]);
        } else if (this.selectedCountryIds.length > 1) {
            countryEntries.push(['countryIds', this.selectedCountryIds]);
        } else {
            countryEntries.push(['countryId', this.reportCountryScope.countryIdParam()]);
        }
        return buildReportPdfQuery([
            ...countryEntries,
            ['state', this.selectedState || undefined],
            ['lang', this.selectedLang]
        ]);
    }

    get oldestAgeDays(): number {
        return this.rows.length ? this.rows[0].ageDays : 0;
    }

    get pendingCount(): number {
        return this.rows.filter(r => (r.status || '').toUpperCase() === 'PENDING').length;
    }

    /** Anything waiting more than a fortnight is worth the reader's eye. */
    ageSeverity(days: number): string {
        if (days >= 30) { return 'danger'; }
        if (days >= 14) { return 'warning'; }
        return 'info';
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }
}
