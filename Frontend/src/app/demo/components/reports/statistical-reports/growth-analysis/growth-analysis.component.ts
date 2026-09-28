import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface GrowthData {
    growthCategory: string;
    analysisYear: number;
    currentValue: number;
    /** Null on the first year: there is no year before it to compare with. */
    previousValue: number | null;
    growthRate: number | null;
    absoluteGrowth: number | null;
    growthStatus: string;
}

/**
 * The rows say `growing` / `declining` / `stable`; the status filter offers the
 * GROWTH_STATUS trend classes, whose English names are Growth / Decline / Stable. Both
 * are read through this key, so the filter matches the rows and each row is named.
 */
const GROWTH_KEYS: Record<string, string> = {
    growing: 'GROWTH', growth: 'GROWTH', declining: 'DECLINE', decline: 'DECLINE', stable: 'STABLE',
    // FE-12: the first year of a series has nothing to compare with; the server calls it
    // `baseline` ("Base year" / "سنة الأساس"). It is not a trend class, so it has no filter
    // option and is named from the translation files.
    baseline: 'BASELINE'
};
/** The vocabulary of the rows, which the export filters on. */
const ROW_STATUS: Record<string, string> = { GROWTH: 'growing', DECLINE: 'declining', STABLE: 'stable', BASELINE: 'baseline' };

@Component({
    selector: 'app-growth-analysis',
    templateUrl: './growth-analysis.component.html',
    styleUrls: ['./growth-analysis.component.scss'],
    standalone: false
})
export class GrowthAnalysisComponent implements OnInit, OnDestroy {

    growthData: GrowthData[] = [];
    filteredData: GrowthData[] = [];
    loading: boolean = false;
    categories: string[] = [];
    categoryOptions: { label: string; value: string }[] = [];
    selectedCategory: string = '';
    selectedStatus: string = '';
    statusOptions: { label: string; value: string }[] = [];
    selectedCountryIds: number[] = [];
    allowedCountries: { label: string; value: number }[] = [];
    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        public translationService: TranslationService,
        private apiUrl: ApiUrlService,
        private messageService: MessageService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        this.initializeStatusOptions();
        if (this.hasMultiCountry) { this.loadCountries(); }
        this.loadGrowthData();
        this.loadCategories();

        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.initializeStatusOptions();
            this.buildCategoryOptions();
        });
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.selectedCountryIds = [];
            this.loadGrowthData();
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    get hasMultiCountry(): boolean {
        return this.reportCountryScope.hasMultiCountryAccess();
    }

    loadCountries(): void {
        this.http.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/study-country-in')}`)
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

    onCountryChange(): void { this.loadGrowthData(); }

    initializeStatusOptions(): void {
        this.lookupService.getTrendClassFilterOptions('GROWTH_STATUS')
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: opts => { this.statusOptions = opts; },
                error: () => { this.statusOptions = []; }
            });
    }

    loadGrowthData(): void {
        this.loading = true;
        this.http.get<GrowthData[]>(this.apiUrl.apiPath('/api/statistical-reports/growth-analysis'), { params: this.buildHttpParams() })
            .subscribe({
                next: (data) => {
                    this.growthData = data;
                    this.filteredData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading growth data:', error);
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    loadCategories(): void {
        this.http.get<string[]>(this.apiUrl.apiPath('/api/statistical-reports/growth-analysis/growth-categories'), { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (categories) => {
                    this.categories = categories ?? [];
                    this.buildCategoryOptions();
                },
                error: (error) => {
                    console.error('Error loading categories:', error);
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    filterData(): void {
        this.filteredData = this.growthData.filter(growth => {
            const matchesCategory = !this.selectedCategory || growth.growthCategory === this.selectedCategory;
            const matchesStatus = !this.selectedStatus
                || this.growthStatusKey(growth.growthStatus) === this.growthStatusKey(this.selectedStatus);
            return matchesCategory && matchesStatus;
        });
    }

    getGrowthSeverity(status: string): string {
        switch (this.growthStatusKey(status)) {
            case 'GROWTH': return 'success';
            case 'DECLINE': return 'danger';
            case 'BASELINE': return 'secondary';
            default: return 'info';
        }
    }

    /** GROWTH / DECLINE / STABLE for a row's status or a filter choice; anything else upper-cased. */
    growthStatusKey(status: string | null | undefined): string {
        const value = (status ?? '').trim();
        return GROWTH_KEYS[value.toLowerCase()] ?? value.toUpperCase();
    }

    /**
     * A row's status in the reader's language (live QA #64, L1: the page showed
     * `growing`): the trend class's own name, else the translation, else the value.
     */
    growthStatusLabel(status: string | null | undefined): string {
        if (!status) return '—';
        const key = this.growthStatusKey(status);
        const option = this.statusOptions.find(o => this.growthStatusKey(o.value) === key);
        if (option?.label) return option.label;
        const i18n = `REPORTS.INDIVIDUAL_REPORTS.GROWTH_STATUS_OPTIONS.${key}`;
        const text = this.translationService.translate(i18n);
        return text && text !== i18n ? text : status;
    }

    /** A growth category (`total_students`) named from the translation files. */
    growthCategoryLabel(category: string | null | undefined): string {
        if (!category) return '—';
        const key = `REPORTS.INDIVIDUAL_REPORTS.GROWTH_ANALYSIS.CATEGORIES.${category.toUpperCase()}`;
        const text = this.translationService.translate(key);
        return text && text !== key ? text : category;
    }

    /** "—" for the first year, which has no rate; the page printed "null%" (#64). */
    rateText(rate: number | null | undefined): string {
        return rate == null ? '—' : `${rate}%`;
    }

    private buildCategoryOptions(): void {
        this.categoryOptions = this.categories.map(c => ({ label: this.growthCategoryLabel(c), value: c }));
    }

    private growthPdfQuery() {
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
            ['growthCategory', this.selectedCategory || undefined],
            // The export filters on the rows' own vocabulary (growing / declining / stable).
            ['growthStatus', this.selectedStatus
                ? (ROW_STATUS[this.growthStatusKey(this.selectedStatus)] ?? this.selectedStatus)
                : undefined],
            ['lang', this.selectedLang]
        ]);
    }

    reportLoading = false;
    pdfApiPath = '/api/statistical-reports/growth-analysis/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.growthPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.growthPdfQuery(), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

