import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { uiReportLang } from '../../../../service/report-lang.util';
import { changesOnly } from '../../../../service/reload-streams.util';

interface ComparisonData {
    comparisonType: string;
    dimensionName: string;
    dimensionNameEn?: string | null;
    currentPeriodValue: number;
    previousPeriodValue: number;
    /** Null when the previous period had no students: there is no rate to show. */
    changePercentage: number | null;
    currentPeriod: string;
    previousPeriod: string;
}

interface LookupOptionResponse {
    id: number | null;
    name: string;
}

interface ComparativeStatisticsMetadataResponse {
    comparisonTypes: LookupOptionResponse[];
    currentPeriods: string[];
    previousPeriods: string[];
}

interface DropdownOption<T = string | number | null> {
    label: string;
    value: T;
}

@Component({
    selector: 'app-comparative-statistics',
    templateUrl: './comparative-statistics.component.html',
    styleUrls: ['./comparative-statistics.component.scss'],
    standalone: false
})
export class ComparativeStatisticsComponent implements OnInit, OnDestroy {

    comparisonData: ComparisonData[] = [];
    loading = false;
    private readonly destroy$ = new Subject<void>();

    comparisonTypeOptions: DropdownOption<string>[] = [];
    private comparisonTypes: string[] = [];
    currentPeriodOptions: DropdownOption<string>[] = [];
    previousPeriodOptions: DropdownOption<string>[] = [];

    selectedComparisonType: string | null = null;
    selectedCurrentPeriod: string | null = null;
    selectedPreviousPeriod: string | null = null;
    selectedCountryIds: number[] = [];
    allowedCountries: { label: string; value: number }[] = [];

    constructor(
        private http: HttpClient,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService,
        public translationService: TranslationService
    ,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        if (this.hasMultiCountry) { this.loadCountries(); }
        this.loadMetadata();
        this.reportCountryScope.selectedCountryId$.pipe(takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            this.loadComparisonData();
        });
        // The type names are built from the translation files, so they follow a switch.
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$))
            .subscribe(() => this.buildComparisonTypeOptions());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    get hasMultiCountry(): boolean {
        return this.reportCountryScope.hasMultiCountryAccess();
    }

    loadCountries(): void {
        this.http.get<any[]>(`${environment.apiUrl}/mohe/api/lookup-tables/study-country-in`)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (data) => {
                    const isAr = this.translationService.getCurrentLanguage() === 'ar';
                    const all = (Array.isArray(data) ? data : ((data as any)?.content || [])).map(c => ({
                        label: (isAr ? c.studyCountryInName : c.studyCountryInNameEn) || c.studyCountryInName || String(c.id),
                        value: c.id as number
                    }));
                    if (!this.reportCountryScope.isMinistryUser && this.reportCountryScope.allowedCountryIds.length > 0) {
                        const allowed = new Set(this.reportCountryScope.allowedCountryIds);
                        this.allowedCountries = all.filter(c => allowed.has(c.value));
                    } else {
                        this.allowedCountries = all;
                    }
                },
                error: () => {}
            });
    }

    private buildHttpParams(): HttpParams {
        if (this.selectedCountryIds.length > 0) {
            return this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds);
        }
        return this.reportCountryScope.appendCountryParam();
    }

    onCountryChange(): void { this.loadMetadata(); }

    loadMetadata(): void {
        // The cohort years on offer depend on the students in scope, so the
        // country filter has to travel with the request.
        this.http.get<ComparativeStatisticsMetadataResponse>(
            `${environment.apiUrl}/mohe/api/reports/statistical/comparative-statistics/metadata`,
            { params: this.buildHttpParams() }
        ).subscribe({
            next: (metadata) => {
                this.comparisonTypes = (metadata?.comparisonTypes ?? [])
                    .map(option => option?.name)
                    .filter((name): name is string => !!name);
                this.buildComparisonTypeOptions();
                this.currentPeriodOptions = this.toStringOptions(metadata?.currentPeriods);
                this.previousPeriodOptions = this.toStringOptions(metadata?.previousPeriods);
                this.loadComparisonData();
            },
            error: (error) => {
                console.error('Error loading comparative statistics metadata:', error);
                this.loadComparisonData();
            }
        });
    }

    loadComparisonData(): void {
        let params = this.buildHttpParams();
        if (this.selectedComparisonType) {
            params = params.set('comparisonType', this.selectedComparisonType);
        }
        if (this.selectedCurrentPeriod) {
            params = params.set('currentPeriod', this.selectedCurrentPeriod);
        }
        if (this.selectedPreviousPeriod) {
            params = params.set('previousPeriod', this.selectedPreviousPeriod);
        }

        this.loading = true;
        this.http.get<ComparisonData[]>(
            `${environment.apiUrl}/mohe/api/reports/statistical/comparative-statistics/search`, { params }
        ).subscribe({
            next: (data) => {
                this.comparisonData = data ?? [];
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading comparative statistics data:', error);
                this.loading = false;
            }
        });
    }

    onFilterChange(): void {
        this.loadComparisonData();
    }

    clearFilters(): void {
        this.selectedComparisonType = null;
        this.selectedCurrentPeriod = null;
        this.selectedPreviousPeriod = null;
        this.selectedCountryIds = [];
        this.loadComparisonData();
    }

    /**
     * The comparison type named in the reader's language (live QA #63, L1: the page
     * showed the codes `country`, `degree`); an unknown type is shown as itself.
     */
    comparisonTypeLabel(code: string | null | undefined): string {
        if (!code) return '—';
        const key = `REPORTS.INDIVIDUAL_REPORTS.COMPARATIVE_STATISTICS.TYPES.${code.toUpperCase()}`;
        const text = this.translationService.translate(key);
        return text && text !== key ? text : code;
    }

    /** The dimension (a country, a degree) in the reader's language, the other as fallback (#63). */
    dimensionLabel(row: ComparisonData): string {
        const english = this.translationService.getCurrentLanguage() === 'en';
        const first = english ? row.dimensionNameEn : row.dimensionName;
        const second = english ? row.dimensionName : row.dimensionNameEn;
        return first || second || '—';
    }

    /** "—" when the previous period had no students; the page printed "null%" (#63). */
    changeText(change: number | null | undefined): string {
        return change == null ? '—' : `${change}%`;
    }

    private buildComparisonTypeOptions(): void {
        this.comparisonTypeOptions = this.comparisonTypes.map(code => ({ label: this.comparisonTypeLabel(code), value: code }));
    }

    getChangeSeverity(change: number | null): string {
        if (change == null) return 'secondary';
        if (change > 10) return 'success';
        if (change > 0) return 'info';
        if (change > -10) return 'warning';
        return 'danger';
    }

    private comparativePdfQuery() {
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
            ['comparisonType', this.selectedComparisonType],
            ['currentPeriod', this.selectedCurrentPeriod],
            ['previousPeriod', this.selectedPreviousPeriod],
            ['lang', this.selectedLang]
        ]);
    }

    reportLoading = false;
    pdfApiPath = '/api/reports/statistical/comparative-statistics/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.comparativePdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.comparativePdfQuery(), lang: this.selectedLang };

    }

    private toStringOptions(values: string[] | undefined): DropdownOption<string>[] {
        return (values ?? [])
            .filter((value) => !!value)
            .map((value) => ({
                label: value,
                value
            }));
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
