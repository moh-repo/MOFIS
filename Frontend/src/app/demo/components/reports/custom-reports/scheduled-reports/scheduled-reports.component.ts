import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface ScheduledReportData {
    scheduleId: number;
    scheduleName: string;
    scheduleDescription: string;
    frequency: string;
    nextRunDate: string;
    lastRunDate: string;
    scheduleStatus: string;
    recordsInLastRun: number;
    recipientEmail: string;
    isEnabled: number;
}

@Component({
    selector: 'app-scheduled-reports',
    templateUrl: './scheduled-reports.component.html',
    styleUrls: ['./scheduled-reports.component.scss'],
    standalone: false
})
export class ScheduledReportsComponent implements OnInit, OnDestroy {

    reportData: ScheduledReportData[] = [];
    loading: boolean = false;

    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translationService.getCurrentLanguage();
    }

    /** DAILY, WEEKLY… by name (W3-07); a frequency without one is shown as it is. */
    frequencyName(code: string | null | undefined): string {
        if (!code) {
            return '—';
        }
        const key = `REPORTS.CUSTOM_REPORTS.FREQUENCY.${code.toUpperCase()}`;
        const named = this.translationService.translate(key);
        return named && named !== key ? named : code;
    }
    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        private messageService: MessageService,
        public translationService: TranslationService,
        private apiUrlService: ApiUrlService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ,
        private lookupService: LookupService) {}

    ngOnInit(): void {
        this.loadReportData();
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
        this.http.get<ScheduledReportData[]>(this.apiUrlService.apiPath('/api/custom-reports/scheduled-reports'), { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (data) => {
                    this.reportData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading scheduled reports:', error);
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    reportLoading = false;
    pdfApiPath = '/api/custom-reports/scheduled-reports/export-report';
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

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

