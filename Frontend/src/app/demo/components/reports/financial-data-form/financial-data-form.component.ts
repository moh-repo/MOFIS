import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
    CountryFilterOption,
    FinancialDataFormService,
    StudentFinancialDataForm,
    FinancialDataFormMetadata,
    relabelCountryOptions
} from '../../../service/financial-data-form.service';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from '../../../service/report-pdf.service';
import { SystemSettingsService } from '../../../service/system-settings.service';
import { ReportSettingsService, ReportTypeSettingsDto, getActiveTitles, getActiveSignatories } from '../../../service/report-settings.service';
import {
    AllCommunityModule,
    ColDef,
    GridApi,
    GridReadyEvent,
    ModuleRegistry,
    RowSelectionOptions,
    ValueFormatterParams
} from 'ag-grid-community';
import { ReportCountryScopeService } from '../../../service/report-country-scope.service';
import { ensureGlobalStylesheet } from '../../../service/lazy-style.util';
import { Subject, Subscription } from 'rxjs';
import { skip, takeUntil } from 'rxjs/operators';
import { changesOnly } from '../../../service/reload-streams.util';
import { uiReportLang } from '../../../service/report-lang.util';
import { agGridLocaleText } from '../../../shared/ag-grid-locale';

// AG Grid 33 stopped registering its features implicitly — a grid created without
// this throws at runtime. Registered here rather than at bootstrap so ag-grid stays
// inside this lazy-loaded reports chunk instead of the initial bundle. The grid's
// stylesheets are fetched the same way — on first use, not with the app shell.
ModuleRegistry.registerModules([AllCommunityModule]);
ensureGlobalStylesheet('ag-grid');
ensureGlobalStylesheet('ag-theme-alpine');

@Component({
    selector: 'app-financial-data-form',
    templateUrl: './financial-data-form.component.html',
    styleUrls: ['./financial-data-form.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class FinancialDataFormComponent implements OnInit, OnDestroy {

    financialData: StudentFinancialDataForm[] = [];
    loading = false;

    selectedYear: number | null = null;
    selectedQuarter: number | null = null;
    selectedCountry: string | null = null;

    years: number[] = [];
    quarters: number[] = [];
    quarterOptions: any[] = [];
    countries: CountryFilterOption[] = [];

    reportDate = '';
    reportNumber = '';
    totalAmount = 0;

    // AG Grid
    /** AG Grid 32.2 replaced the 'multiple' string with this object form. */
    rowSelection: RowSelectionOptions = { mode: 'multiRow' };
    columnDefs: ColDef[] = [];
    defaultColDef: ColDef = {
        resizable: true,
        sortable: true,
        filter: true,
        minWidth: 80,
        wrapHeaderText: true,
        autoHeaderHeight: true,
    };
    gridQuickFilter = '';
    /** AG Grid's own words (pagers, filter menu) in the page's language (R5-14). */
    gridLocaleText: Record<string, string> = {};
    /** The language the grid was created in; the template re-creates the grid when it changes. */
    gridLanguage: string[] = [];
    private gridApi: GridApi | null = null;

    // ECharts
    showCharts = false;
    barChartOptions: any = {};
    pieChartOptions: any = {};

    // Report settings
    reportTypeSettings: ReportTypeSettingsDto | null = null;

    // Jasper PDF viewer
    reportLoading = false;
    pdfApiPath = '/api/reports/financial-data-form/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    private readonly destroy$ = new Subject<void>();
    private countrySub?: Subscription;

    constructor(
        private http: HttpClient,
        private financialDataFormService: FinancialDataFormService,
        private messageService: MessageService,
        public translationService: TranslationService,
        private lookupService: LookupService,
        private reportPdfService: ReportPdfService,
        public systemSettingsService: SystemSettingsService,
        private reportSettingsService: ReportSettingsService,
        private reportCountryScope: ReportCountryScopeService,
    ) {
        const today = new Date();
        this.reportDate = this.formatDate(today);
        this.reportNumber = `1-${today.getFullYear()}${String(today.getMonth() + 1).padStart(2, '0')}${String(today.getDate()).padStart(2, '0')}`;
    }

    ngOnInit(): void {
        this.buildColumnDefs();
        this.applyGridLanguage();
        this.translationService.currentLanguage$
            .pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => {
                this.buildColumnDefs();
                this.applyGridLanguage();
                this.countries = relabelCountryOptions(this.countries, this.translationService.isRTL());
            });
        this.loadMetadata();
        this.loadReportSettings();
        // skip(1): BehaviorSubject emits on subscription; we already call loadMetadata() above.
        this.countrySub = this.reportCountryScope.selectedCountryId$
            .pipe(skip(1), takeUntil(this.destroy$))
            .subscribe(() => {
                this.loadMetadata();
                if (this.selectedYear && this.selectedQuarter) {
                    this.loadFinancialData();
                }
            });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
        this.countrySub?.unsubscribe();
    }

    private loadReportSettings(): void {
        this.reportSettingsService.getByCode('FINANCIAL_DATA_FORM').subscribe({
            next: dto => { this.reportTypeSettings = dto; },
            error: () => { /* fallback: no dynamic header/footer */ }
        });
    }

    get activeTitles(): string[] {
        if (!this.reportTypeSettings) return [];
        return getActiveTitles(this.reportTypeSettings, this.isRtl);
    }

    get activeSignatories(): { name: string; position: string }[] {
        if (!this.reportTypeSettings) return [];
        return getActiveSignatories(this.reportTypeSettings, this.isRtl);
    }

    get systemLogoUrl(): string {
        return this.systemSettingsService.getLogoUrl();
    }

    onLogoError(event: Event): void {
        const img = event.target as HTMLImageElement;
        img.src = 'assets/MOHE_Logo.png';
    }

    private t(key: string): string {
        return this.translationService.translate(key);
    }

    /** The chart's locale: Arabic figures follow the page, not the browser default. */
    private get chartLocale(): string {
        return this.translationService.getCurrentLanguage() === 'ar' ? 'ar' : 'en';
    }

    /**
     * An amount in the language on screen, with no currency beside it.
     *
     * The tooltip used to print "USD" after every figure (review item P4). Nothing
     * in this report says which currency a row is in — the column is a plain amount
     * — so naming one was a guess, and a wrong one wherever the ministry pays in
     * anything else.
     */
    private formatAmount(value: number | null | undefined): string {
        return Number(value ?? 0).toLocaleString(this.chartLocale);
    }

    /** "12K" / "١٢ ألف": axis labels short enough to fit, in the reader's numerals. */
    private formatCompact(value: number): string {
        try {
            return new Intl.NumberFormat(this.chartLocale, { notation: 'compact' }).format(value);
        } catch {
            return value >= 1000 ? `${value / 1000}k` : String(value);
        }
    }

    buildColumnDefs(): void {
        this.columnDefs = [
            { field: 'studentId', headerName: this.t('REPORTS.INDIVIDUAL_REPORTS.STUDENT_DEMOGRAPHICS.STUDENT_ID'), width: 90, filter: 'agNumberColumnFilter' },
            { field: 'studentName', headerName: this.t('REPORTS.INDIVIDUAL_REPORTS.STUDENT_DEMOGRAPHICS.NAME'), flex: 2, minWidth: 180 },
            { field: 'studentMajorName', headerName: this.t('REPORTS.COMMON_FIELDS.MAJOR'), flex: 1, minWidth: 120 },
            { field: 'studentDegreeName', headerName: this.t('REPORTS.COMMON_FIELDS.DEGREE'), flex: 1, minWidth: 100 },
            { field: 'studentCountryName', headerName: this.t('REPORTS.COMMON_FIELDS.COUNTRY'), flex: 1, minWidth: 100 },
            { field: 'studentStudyStatusName', headerName: this.t('REPORTS.COMMON_FIELDS.STATUS'), flex: 1, minWidth: 110 },
            {
                field: 'studentAmount',
                headerName: this.t('REPORTS.COMMON_FIELDS.AMOUNT'),
                width: 130,
                filter: 'agNumberColumnFilter',
                type: 'numericColumn',
                valueFormatter: (p: ValueFormatterParams) => p.value != null ? Number(p.value).toLocaleString() : '0',
            },
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
        setTimeout(() => params.api.sizeColumnsToFit(), 0);
    }

    onFirstDataRendered(): void {
        this.gridApi?.sizeColumnsToFit();
    }

    onGridQuickFilter(): void {
        this.gridApi?.setGridOption('quickFilterText', this.gridQuickFilter);
    }

    get isRtl(): boolean {
        return document.documentElement.dir === 'rtl' || document.body.dir === 'rtl';
    }

    loadMetadata(): void {
        this.loading = true;
        const countryId = this.reportCountryScope.countryIdParam();
        this.financialDataFormService.getMetadata(countryId).subscribe({
            next: (metadata: FinancialDataFormMetadata) => {
                this.years = metadata.years || [];
                this.countries = metadata.countries || [];
                this.quarters = metadata.quarters || [];
                this.lookupService.getQuarterNumbers().subscribe(opts => { this.quarterOptions = opts; });

                if (this.years.length > 0) this.selectedYear = this.years[0];
                if (this.quarters.length > 0) this.selectedQuarter = this.quarters[this.quarters.length - 1];
                if (this.countries.length === 1) this.selectedCountry = this.countries[0].value;

                this.loading = false;
                if (this.selectedYear && this.selectedQuarter) this.loadFinancialData();
            },
            error: () => {
                this.loading = false;
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('REPORTS.FINANCIAL_DATA_FORM.METADATA_ERROR') });
            }
        });
    }

    loadFinancialData(): void {
        if (!this.selectedYear || !this.selectedQuarter) {
            this.messageService.add({ severity: 'warn', summary: this.t('COMMON.WARNING'), detail: this.t('REPORTS.FINANCIAL_DATA_FORM.SELECT_YEAR_QUARTER') });
            return;
        }
        this.loading = true;
        const countryId = this.reportCountryScope.countryIdParam();
        const call$ = this.selectedCountry
            ? this.financialDataFormService.getByCountryQuarterAndYear(this.selectedCountry, this.selectedQuarter, this.selectedYear, countryId)
            : this.financialDataFormService.getByQuarterAndYear(this.selectedQuarter, this.selectedYear, countryId);

        call$.subscribe({
            next: (data: StudentFinancialDataForm[]) => {
                this.financialData = data;
                this.calculateTotalAmount();
                this.buildChartOptions();
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

    calculateTotalAmount(): void {
        this.totalAmount = this.financialData.reduce((s, r) => s + (r.studentAmount || 0), 0);
    }

    onFilterChange(): void { this.loadFinancialData(); }

    clearFilters(): void {
        this.selectedYear = this.years.length > 0 ? this.years[0] : null;
        this.selectedQuarter = this.quarters.length > 0 ? this.quarters[this.quarters.length - 1] : null;
        this.selectedCountry = null;
        this.financialData = [];
        this.totalAmount = 0;
    }

    toggleCharts(): void {
        this.showCharts = !this.showCharts;
    }

    // ─── ECharts ────────────────────────────────────────────────────────────────

    buildChartOptions(): void {
        this.buildBarChart();
        this.buildPieChart();
    }

    private buildBarChart(): void {
        const byMajor = new Map<string, number>();
        this.financialData.forEach(d => {
            const key = d.studentMajorName || this.t('COMMON.NOT_AVAILABLE');
            byMajor.set(key, (byMajor.get(key) || 0) + (d.studentAmount || 0));
        });
        const majors = Array.from(byMajor.keys());
        const amounts = Array.from(byMajor.values());

        this.barChartOptions = {
            // Amounts are formatted for the language on screen, and the currency
            // comes from the report rather than the literal "USD" that used to be
            // printed beside every figure (review item P4).
            tooltip: {
                trigger: 'axis',
                formatter: (p: any[]) => `${p[0].name}<br/><b>${this.formatAmount(p[0].value)}</b>`
            },
            grid: { left: '3%', right: '4%', bottom: '20%', containLabel: true },
            xAxis: { type: 'category', data: majors, axisLabel: { rotate: 35, fontSize: 10, overflow: 'truncate', width: 100 } },
            yAxis: { type: 'value', axisLabel: { formatter: (v: number) => this.formatCompact(v) } },
            series: [{
                name: this.t('REPORTS.COMMON_FIELDS.AMOUNT'),
                type: 'bar',
                data: amounts,
                itemStyle: { color: '#4CAF50', borderRadius: [3, 3, 0, 0] },
                emphasis: { itemStyle: { color: '#2e7d32' } },
                label: { show: true, position: 'top', formatter: (p: any) => p.value?.toLocaleString(), fontSize: 9 }
            }]
        };
    }

    private buildPieChart(): void {
        const byCountry = new Map<string, number>();
        this.financialData.forEach(d => {
            const key = d.studentCountryName || d.studyCountryInName || this.t('COMMON.NOT_AVAILABLE');
            byCountry.set(key, (byCountry.get(key) || 0) + 1);
        });

        const isRtl = this.isRtl;
        this.pieChartOptions = {
            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
            legend: {
                orient: 'vertical',
                ...(isRtl ? { right: 10 } : { left: 10 }),
                top: 'center',
                textStyle: { fontSize: 10 }
            },
            series: [{
                name: this.t('REPORTS.COMMON_FIELDS.COUNTRY'),
                type: 'pie',
                radius: ['40%', '68%'],
                center: [isRtl ? '40%' : '60%', '50%'],
                data: Array.from(byCountry.entries()).map(([name, value]) => ({ name, value })),
                emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.4)' } },
                label: { formatter: '{b}: {d}%', fontSize: 10 }
            }]
        };
    }

    // ─── ExcelJS Export ─────────────────────────────────────────────────────────

    // ─── PDF Export (print-window — full Arabic support via browser fonts) ────────

    /** Opens the formatted report in a styled print window; user can Save as PDF from print dialog. */
    /** Filters for this run of the report; shared by all four actions. */
    reportQuery(): ReportPdfQuery {
        return { ...this.buildPdfQuery(), lang: this.selectedLang };
    }

    // ─── Jasper PDF Viewer ───────────────────────────────────────────────────────

    generateReport(): void {
        if (!this.selectedYear || !this.selectedQuarter) {
            this.messageService.add({ severity: 'warn', summary: this.t('COMMON.WARNING'), detail: this.t('REPORTS.FINANCIAL_DATA_FORM.SELECT_YEAR_QUARTER') });
            return;
        }
        this.pdfQuery = { ...this.buildPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    // ─── Server-side PDF ────────────────────────────────────────────────────────

    private buildPdfQuery(): ReportPdfQuery {
        return buildReportPdfQuery([
            ['countryId', this.reportCountryScope.countryIdParam()],
            ['quarter', this.selectedQuarter ?? undefined],
            ['year', this.selectedYear ?? undefined],
            ['country', this.selectedCountry ?? undefined]
        ]);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    getStudentThreeNames(student: StudentFinancialDataForm): string {
        return (student.studentName || '').split(' ').slice(0, 3).join(' ');
    }

    getStudentFourNames(student: StudentFinancialDataForm): string {
        return (student.studentName || '').split(' ').slice(0, 4).join(' ');
    }

    getStudentFiveNames(student: StudentFinancialDataForm): string {
        return student.studentName || '';
    }

    getQuarterLabel(quarter: number): string {
        return this.translationService.translate(`REPORTS.QUARTERS.Q${quarter}`);
    }

    private formatDate(date: Date): string {
        const d = date.getDate().toString().padStart(2, '0');
        const m = (date.getMonth() + 1).toString().padStart(2, '0');
        return `${d}-${m}-${date.getFullYear()}`;
    }

    private escapeHtml(s: string): string {
        return String(s ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    private formatNumber(n: number | null | undefined): string {
        return (n ?? 0).toLocaleString();
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
