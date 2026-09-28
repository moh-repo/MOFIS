import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportPdfService, ReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { buildReportFilterQuery } from 'src/app/demo/service/report-query.util';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface BudgetMetadata {
  categories: string[];
  years: number[];
  quarters: number[];
}

@Component({
    selector: 'app-budget-analysis',
    templateUrl: './budget-analysis.component.html',
    styleUrls: ['./budget-analysis.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class BudgetAnalysisComponent implements OnInit, OnDestroy {

  loading = false;
  budgetCategories: any[] = [];
  categories: string[] = [];
  years: number[] = [];
  quarters: number[] = [];
  selectedCategory: string | null = null;
  selectedYear: number | null = null;
  selectedQuarter: number | null = null;

  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];
  private readonly destroy$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public translationService: TranslationService,
    private reportPdfService: ReportPdfService,
    private reportCountryScope: ReportCountryScopeService,
        private lookupService: LookupService) { }

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

  onCountryChange(): void { this.loadBudgetData(); }

  ngOnInit(): void {
    if (this.hasMultiCountry) { this.loadCountries(); }
    this.loadMetadata();
    this.loadBudgetData();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.loadMetadata();
      this.loadBudgetData();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private filterEntries(): [string, string | number | null][] {
    return [
      ['category', this.selectedCategory],
      ['year', this.selectedYear],
      ['quarter', this.selectedQuarter]
    ];
  }

  loadMetadata(): void {
    const params = this.buildHttpParams();
    this.http.get<BudgetMetadata>(
      `${environment.apiUrl}/mohe/api/reports/financial/budget-analysis/metadata`,
      { params }
    ).subscribe({
      next: (data) => {
        this.categories = data.categories || [];
        this.years = data.years || [];
        this.quarters = data.quarters || [];
      },
      error: () => { /* non-blocking */ }
    });
  }

  loadBudgetData(): void {
    this.loading = true;
    let params = this.buildHttpParams();
    this.filterEntries().forEach(([k, v]) => {
      if (v !== null && v !== undefined) params = params.set(k, String(v));
    });
    this.http.get<any[]>(`${environment.apiUrl}/mohe/api/reports/financial/budget-analysis/`, { params }).subscribe({
      next: (data) => {
        this.budgetCategories = (Array.isArray(data) ? data : ((data as any)?.content || [])).map(item => ({
          category: item.budgetCategory,
          allocated: item.allocatedAmount,
          spent: item.spentAmount,
          remaining: item.remainingAmount,
          percentage: item.utilizationPercentage
        }));
        this.loading = false;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('COMMON.LOAD_ERROR')
        });
        this.loading = false;
      }
    });
  }

  onFilterChange(): void {
    this.loadBudgetData();
  }

  clearFilters(): void {
    this.selectedCategory = null;
    this.selectedYear = null;
    this.selectedQuarter = null;
    this.selectedCountryIds = [];
    this.loadBudgetData();
  }

  reportLoading = false;
  pdfApiPath = '/api/reports/financial/budget-analysis/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  private buildBudgetPdfQuery() {
    const countryEntries: [string, any][] = [];
    if (this.selectedCountryIds.length === 1) {
      countryEntries.push(['countryId', this.selectedCountryIds[0]]);
    } else if (this.selectedCountryIds.length > 1) {
      countryEntries.push(['countryIds', this.selectedCountryIds]);
    } else {
      countryEntries.push(['countryId', this.reportCountryScope.countryIdParam()]);
    }
    return buildReportFilterQuery([
      ...countryEntries,
      ...this.filterEntries()
    ]);
  }

  generateReport(): void {
      this.pdfQuery = { ...this.buildBudgetPdfQuery(), lang: this.selectedLang };
      this.loadTrigger++;
  }

  /** Filters for this run of the report; shared by all four actions. */

  reportQuery(): ReportPdfQuery {

      return { ...this.buildBudgetPdfQuery(), lang: this.selectedLang };

  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount);
  }

  getSeverity(percentage: number): string {
    if (percentage < 50) return 'success';
    if (percentage < 75) return 'info';
    if (percentage < 90) return 'warning';
    return 'danger';
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
