import { Component, OnDestroy, OnInit } from '@angular/core';
import { MessageService } from 'primeng/api';
import {
    AllCommunityModule,
    ColDef,
    GridApi,
    GridReadyEvent,
    ModuleRegistry,
    RowSelectionOptions,
    ValueFormatterParams
} from 'ag-grid-community';
import { Subject } from 'rxjs';
import { skip, takeUntil } from 'rxjs/operators';
import {
    FinancialDataFormV2Metadata,
    FinancialDataFormV2Service,
    StudentFinancialDataFormV2
} from '../../../service/financial-data-form-v2.service';
import { CountryFilterOption, relabelCountryOptions } from '../../../service/financial-data-form.service';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { ReportPdfQuery, buildReportPdfQuery } from '../../../service/report-pdf.service';
import { ReportSettingsService, ReportTypeSettingsDto, getActiveSignatories, getActiveTitles } from '../../../service/report-settings.service';
import { ReportCountryScopeService } from '../../../service/report-country-scope.service';
import { ensureGlobalStylesheet } from '../../../service/lazy-style.util';
import { changesOnly } from '../../../service/reload-streams.util';
import { uiReportLang } from '../../../service/report-lang.util';
import { agGridLocaleText } from '../../../shared/ag-grid-locale';

// AG Grid 33 stopped registering its features implicitly — a grid created without
// this throws at runtime. Registered here so ag-grid stays inside the lazy-loaded
// reports chunk; its stylesheets are fetched on first use the same way.
ModuleRegistry.registerModules([AllCommunityModule]);
ensureGlobalStylesheet('ag-grid');
ensureGlobalStylesheet('ag-theme-alpine');

/**
 * Financial Data Form V2 — the quarterly financial assistance statement
 * ("كشوفات المساعدة المالية الربعية"), beside the first form.
 *
 * The screen is a filter bar (year / quarter / country), a summary strip, the
 * rows as an interactive grid in the statement's column order, and the Jasper
 * viewer for the printed statement itself.
 */
@Component({
    selector: 'app-financial-data-form-v2',
    templateUrl: './financial-data-form-v2.component.html',
    styleUrls: ['./financial-data-form-v2.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class FinancialDataFormV2Component implements OnInit, OnDestroy {

    static readonly REPORT_CODE = 'FINANCIAL_DATA_FORM_V2';
    static readonly PDF_API_PATH = '/api/reports/financial-data-form-v2/export-report';

    rows: StudentFinancialDataFormV2[] = [];
    loading = false;

    selectedYear: number | null = null;
    selectedQuarter: number | null = null;
    selectedCountry: string | null = null;

    years: number[] = [];
    quarters: number[] = [];
    quarterOptions: Array<{ label: string; value: number }> = [];
    countries: CountryFilterOption[] = [];

    totalAmount = 0;
    entityCount = 0;

    // AG Grid
    rowSelection: RowSelectionOptions = { mode: 'multiRow' };
    columnDefs: ColDef[] = [];
    defaultColDef: ColDef = {
        resizable: true,
        sortable: true,
        filter: true,
        minWidth: 70,
        wrapHeaderText: true,
        autoHeaderHeight: true,
    };
    gridQuickFilter = '';
    /** AG Grid's own words (pagers, filter menu) in the page's language (R5-14). */
    gridLocaleText: Record<string, string> = {};
    /** The language the grid was created in; the template re-creates the grid when it changes. */
    gridLanguage: string[] = [];
    private gridApi: GridApi | null = null;

    // Report settings (masthead / signatories shown in the viewer card)
    reportTypeSettings: ReportTypeSettingsDto | null = null;

    // Jasper PDF viewer
    reportLoading = false;
    pdfApiPath = FinancialDataFormV2Component.PDF_API_PATH;
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    private readonly destroy$ = new Subject<void>();

    constructor(
        private service: FinancialDataFormV2Service,
        private messageService: MessageService,
        public translationService: TranslationService,
        private lookupService: LookupService,
        private reportSettingsService: ReportSettingsService,
        private reportCountryScope: ReportCountryScopeService,
    ) { }

    ngOnInit(): void {
        this.buildColumnDefs();
        this.applyGridLanguage();
        this.translationService.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => {
                this.buildColumnDefs();
                this.applyGridLanguage();
                this.countries = relabelCountryOptions(this.countries, this.isRtl);
            });
        this.loadMetadata();
        this.loadReportSettings();
        // skip(1): BehaviorSubject emits on subscription; loadMetadata() already ran.
        this.reportCountryScope.selectedCountryId$
            .pipe(skip(1), takeUntil(this.destroy$))
            .subscribe(() => {
                this.loadMetadata();
                if (this.selectedYear && this.selectedQuarter) {
                    this.loadRows();
                }
            });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    private loadReportSettings(): void {
        this.reportSettingsService.getByCode(FinancialDataFormV2Component.REPORT_CODE).subscribe({
            next: dto => { this.reportTypeSettings = dto; },
            error: () => { /* no dynamic masthead preview; the PDF still carries the seeded head */ }
        });
    }

    get activeTitles(): string[] {
        return this.reportTypeSettings ? getActiveTitles(this.reportTypeSettings, this.isRtl) : [];
    }

    get activeSignatories(): { name: string; position: string }[] {
        return this.reportTypeSettings ? getActiveSignatories(this.reportTypeSettings, this.isRtl) : [];
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }

    private t(key: string): string {
        return this.translationService.translate(key);
    }

    /** The statement's column order: م، الاسم، الجهة، الدولة، عام الإيفاد، الدرجة، التخصص، القرار، المدة، المستوى، أول مصرف، المبلغ، التوقيع، الملاحظات. */
    buildColumnDefs(): void {
        const number = (p: ValueFormatterParams) => p.value != null ? Number(p.value).toLocaleString() : '';
        this.columnDefs = [
            {
                headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.ROW_NO'),
                valueGetter: p => (p.node?.rowIndex ?? 0) + 1,
                width: 70, sortable: false, filter: false, pinned: this.isRtl ? 'right' : 'left'
            },
            { field: 'studentName', headerName: this.t('REPORTS.COMMON_FIELDS.STUDENT_NAME'), flex: 2, minWidth: 200 },
            { field: 'entityName', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.ENTITY'), flex: 1, minWidth: 120 },
            { field: 'studyCountryInName', headerName: this.t('REPORTS.COMMON_FIELDS.COUNTRY'), width: 100 },
            { field: 'studentDelegationDecisionYear', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.DECISION_YEAR'), width: 100 },
            { field: 'studentDegreeName', headerName: this.t('REPORTS.COMMON_FIELDS.DEGREE'), width: 100 },
            { field: 'studentMajorName', headerName: this.t('REPORTS.COMMON_FIELDS.MAJOR'), flex: 1, minWidth: 120 },
            { field: 'studentDelegateDecisionNo', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.DECISION_NO'), width: 110, filter: 'agNumberColumnFilter' },
            { field: 'studentStudyNoOfYears', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.STUDY_YEARS'), width: 90, filter: 'agNumberColumnFilter' },
            { field: 'currentLevelLabel', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.CURRENT_LEVEL'), width: 90 },
            { field: 'studentFirstReceiptOfDue', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.FIRST_RECEIPT'), width: 100, filter: 'agNumberColumnFilter' },
            {
                field: 'studentAmount',
                headerName: this.t('REPORTS.COMMON_FIELDS.AMOUNT'),
                width: 110,
                filter: 'agNumberColumnFilter',
                type: 'numericColumn',
                valueFormatter: number,
            },
            { field: 'studentNote2', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.SIGNATURE_NOTE'), flex: 1, minWidth: 120 },
            { field: 'studentNote1', headerName: this.t('REPORTS.FINANCIAL_DATA_FORM_V2.NOTES'), flex: 2, minWidth: 180 },
        ];
    }

    private applyGridLanguage(): void {
        this.gridLocaleText = agGridLocaleText(key => this.t(key));
        this.gridLanguage = [this.translationService.getCurrentLanguage()];
    }

    onGridReady(params: GridReadyEvent): void {
        this.gridApi = params.api;
        // A grid re-created on a language switch keeps the search the user typed.
        if (this.gridQuickFilter) {
            params.api.setGridOption('quickFilterText', this.gridQuickFilter);
        }
    }

    onGridQuickFilter(): void {
        this.gridApi?.setGridOption('quickFilterText', this.gridQuickFilter);
    }

    loadMetadata(): void {
        this.loading = true;
        const countryId = this.reportCountryScope.countryIdParam();
        this.service.getMetadata(countryId).subscribe({
            next: (metadata: FinancialDataFormV2Metadata) => {
                this.years = metadata.years || [];
                this.countries = metadata.countries || [];
                this.quarters = metadata.quarters || [];
                this.lookupService.getQuarterNumbers().subscribe(opts => { this.quarterOptions = opts; });

                if (this.years.length > 0) this.selectedYear = this.years[0];
                if (this.quarters.length > 0) this.selectedQuarter = this.quarters[this.quarters.length - 1];
                if (this.countries.length === 1) this.selectedCountry = this.countries[0].value;

                this.loading = false;
                if (this.selectedYear && this.selectedQuarter) this.loadRows();
            },
            error: () => {
                this.loading = false;
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('REPORTS.FINANCIAL_DATA_FORM.METADATA_ERROR') });
            }
        });
    }

    loadRows(): void {
        if (!this.selectedYear || !this.selectedQuarter) {
            this.messageService.add({ severity: 'warn', summary: this.t('COMMON.WARNING'), detail: this.t('REPORTS.FINANCIAL_DATA_FORM.SELECT_YEAR_QUARTER') });
            return;
        }
        this.loading = true;
        const countryId = this.reportCountryScope.countryIdParam();
        const call$ = this.selectedCountry
            ? this.service.getByCountryQuarterAndYear(this.selectedCountry, this.selectedQuarter, this.selectedYear, countryId)
            : this.service.getByQuarterAndYear(this.selectedQuarter, this.selectedYear, countryId);

        call$.subscribe({
            next: (data: StudentFinancialDataFormV2[]) => {
                this.rows = data;
                this.summarise();
                this.loading = false;
                if (!data.length) {
                    this.messageService.add({ severity: 'info', summary: this.t('COMMON.INFO'), detail: this.t('REPORTS.NO_DATA') });
                }
            },
            error: () => {
                this.loading = false;
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('REPORTS.FINANCIAL_DATA_FORM.LOAD_ERROR') });
            }
        });
    }

    private summarise(): void {
        this.totalAmount = this.rows.reduce((s, r) => s + (r.studentAmount || 0), 0);
        this.entityCount = new Set(this.rows.map(r => r.entityName || '')).size;
    }

    onFilterChange(): void { this.loadRows(); }

    clearFilters(): void {
        this.selectedYear = this.years.length > 0 ? this.years[0] : null;
        this.selectedQuarter = this.quarters.length > 0 ? this.quarters[this.quarters.length - 1] : null;
        this.selectedCountry = null;
        this.rows = [];
        this.totalAmount = 0;
        this.entityCount = 0;
    }

    /** Filters for this run of the statement; shared by the actions bar and the viewer. */
    reportQuery(): ReportPdfQuery {
        return { ...this.buildPdfQuery(), lang: this.selectedLang };
    }

    generateReport(): void {
        if (!this.selectedYear || !this.selectedQuarter) {
            this.messageService.add({ severity: 'warn', summary: this.t('COMMON.WARNING'), detail: this.t('REPORTS.FINANCIAL_DATA_FORM.SELECT_YEAR_QUARTER') });
            return;
        }
        this.pdfQuery = this.reportQuery();
        this.loadTrigger++;
    }

    get reportFilename(): string {
        return `financial-data-form-v2-q${this.selectedQuarter}-${this.selectedYear}.pdf`;
    }

    get quarterLabel(): string {
        return this.selectedQuarter ? this.translationService.translate(`REPORTS.QUARTERS.Q${this.selectedQuarter}`) : '';
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

    private buildPdfQuery(): ReportPdfQuery {
        return buildReportPdfQuery([
            ['countryId', this.reportCountryScope.countryIdParam()],
            ['quarter', this.selectedQuarter ?? undefined],
            ['year', this.selectedYear ?? undefined],
            ['country', this.selectedCountry ?? undefined]
        ]);
    }
}
