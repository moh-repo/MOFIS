import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { MessageService } from 'primeng/api';
import { Router } from '@angular/router';
import { Subscription, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { uiReportLang } from '../../../service/report-lang.util';

interface EntityFinancialSummary {
    entityName: string;
    receivedAmount: number;
    receivedCount: number;
    disbursedAmount: number;
    disbursedCount: number;
    remainingAmount: number;
    remainingCount: number;
}

interface QuarterlyFinancialReport {
    reportDate: string;
    reportNumber: string;
    // The period the figures below actually come from. It differs from
    // requestedQuarter/requestedYear when the chosen quarter held no finance rows
    // and the backend fell back to the latest quarter that did.
    quarter: number;
    year: number;
    requestedQuarter: number;
    requestedYear: number;
    periodSubstituted: boolean;
    entities: EntityFinancialSummary[];
    entityCount: number;
    totalReceivedAmount: number;
    totalReceivedCount: number;
    totalDisbursedAmount: number;
    totalDisbursedCount: number;
    totalRemainingAmount: number;
    totalRemainingCount: number;
    disbursementRatio: number;
}

@Component({
    selector: 'app-quarterly-financial-report',
    templateUrl: './quarterly-financial-report.component.html',
    styleUrls: ['./quarterly-financial-report.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class QuarterlyFinancialReportComponent implements OnInit, OnDestroy {

    private apiUrl = `${environment.apiUrl}/mohe/api/v1/reports/quarterly-financial`;

    report: QuarterlyFinancialReport | null = null;
    loading = false;

    // Filter options
    selectedQuarter: number = 4;
    selectedYear: number = new Date().getFullYear();
    quarterOptions: { label: string; value: number }[] = [];
    yearOptions: { label: string; value: number }[] = [];
    years: number[] = [];

    private languageSubscription?: Subscription;
    private readonly destroy$ = new Subject<void>();

    // Jasper PDF viewer
    reportLoading = false;
    pdfApiPath = '/api/v1/reports/quarterly-financial/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private lookupService: LookupService,
        private messageService: MessageService,
        private router: Router,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ) {
        this.buildQuarterOptions();
    }

    ngOnInit(): void {
        this.languageSubscription = this.translationService.currentLanguage$
            .subscribe(() => {
                this.buildQuarterOptions();
                this.buildYearOptions();
            });
        this.loadYears();
        this.reportCountryScope.selectedCountryId$.pipe(takeUntil(this.destroy$)).subscribe(() => {
            this.loadReport();
        });
    }

    ngOnDestroy(): void {
        this.languageSubscription?.unsubscribe();
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadReport() {
        this.loading = true;
        let params = new HttpParams()
            .set('quarter', this.selectedQuarter.toString())
            .set('year', this.selectedYear.toString());
        const countryId = this.reportCountryScope.countryIdParam();
        if (countryId != null) {
            params = params.set('countryId', String(countryId));
        }

        this.http.get<QuarterlyFinancialReport>(this.apiUrl, { params }).subscribe({
            next: (data) => {
                this.report = data;
                this.loading = false;
                // Only the key figures above refresh with the filters. Rendering the
                // Jasper document is left to the Generate button, as on every other
                // report page.
            },
            error: (error) => {
                console.error('Error loading quarterly report:', error);
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('COMMON.LOAD_ERROR')
                });
                this.loading = false;
            }
        });
    }

    onFilterChange() {
        this.loadReport();
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.quarterlyPdfQuery(), lang: this.selectedLang };

    }

    private quarterlyPdfQuery(): ReportPdfQuery {
        const countryEntries: [string, any][] = [];
        const countryId = this.reportCountryScope.countryIdParam();
        if (countryId != null) {
            countryEntries.push(['countryId', countryId]);
        }
        return buildReportPdfQuery([
            ...countryEntries,
            ['quarter', this.selectedQuarter],
            ['year', this.selectedYear]
        ]);
    }

    generateReport(): void {
        this.pdfQuery = { ...this.quarterlyPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    goBack() {
        this.router.navigate(['/reports/financial']);
    }

    isRTL(): boolean {
        return this.translationService.isRTL();
    }

    private buildQuarterOptions(): void {
        this.lookupService.getQuarterNumbers().subscribe(opts => {
            this.quarterOptions = opts;
        });
    }

    private loadYears(): void {
        this.http.get<number[]>(`${this.apiUrl}/years`, { params: this.reportCountryScope.appendCountryParam() }).subscribe({
            next: (years) => {
                if (years && years.length > 0) {
                    this.years = years;
                } else {
                    this.loadYearsFromLookup();
                    return;
                }
                this.buildYearOptions();
                // Ensure selectedYear is in the list; default to most recent
                if (!this.years.includes(this.selectedYear)) {
                    this.selectedYear = this.years[0];
                }
                this.loadReport();
            },
            error: () => {
                this.loadYearsFromLookup();
            }
        });
    }

    private loadYearsFromLookup(): void {
        this.lookupService.getAcademicYears().subscribe({
            next: opts => {
                this.years = opts.map(o => parseInt(o.value, 10)).filter(n => !isNaN(n));
                if (this.years.length === 0) {
                    this.loadReport();
                    return;
                }
                this.buildYearOptions();
                if (!this.years.includes(this.selectedYear)) {
                    this.selectedYear = this.years[0];
                }
                this.loadReport();
            },
            error: () => {
                this.years = [];
                this.buildYearOptions();
                this.loadReport();
            }
        });
    }

    private buildYearOptions(): void {
        this.yearOptions = this.years.map(year => ({
            label: year.toString(),
            value: year
        }));
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

    private quarterLabel(quarter: number): string {
        const option = this.quarterOptions.find(o => o.value === quarter);
        return option ? option.label : String(quarter);
    }

    /** The period the figures on screen belong to, which is not always the one picked. */
    get shownPeriodLabel(): string {
        return this.report ? `${this.quarterLabel(this.report.quarter)} ${this.report.year}` : '';
    }

    get periodSubstitutedMessage(): string {
        return `${this.translationService.translate('REPORTS.QUARTERLY_FINANCIAL.PERIOD_SUBSTITUTED')} ${this.shownPeriodLabel}`;
    }

    /** Name the file after the period it contains, not the one requested. */
    get reportFilename(): string {
        return `${this.reportBaseFilename}.pdf`;
    }

    /** Same name without an extension, so each action can append its own. */
    get reportBaseFilename(): string {
        const quarter = this.report ? this.report.quarter : this.selectedQuarter;
        const year = this.report ? this.report.year : this.selectedYear;
        return `quarterly-financial-q${quarter}-${year}`;
    }

}

