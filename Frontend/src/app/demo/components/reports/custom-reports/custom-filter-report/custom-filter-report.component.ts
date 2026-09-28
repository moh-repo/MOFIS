import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { LookupService } from 'src/app/demo/service/lookup.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportPdfService, ReportPdfQuery, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { changesOnly } from '../../../../service/reload-streams.util';
import { uiReportLang } from '../../../../service/report-lang.util';

interface CustomFilterReportData {
    studentId: number;
    studentName: string;
    nationalNumber: string;
    universityName: string;
    majorName: string;
    countryName: string;
    degreeLevelId: number;
    degreeLevelName: string;
    studyStatusId: number;
    studyStatusName: string;
    studyTypeId: number;
    studyTypeName: string;
    genderId: number;
    enrollmentDate: string;
    studyStartDate: string;
    studyEndDate: string;
    financialAmount: number;
    studyYears: number;
    enrollmentYear: number;
    statusCategory: string;
}

@Component({
    selector: 'app-custom-filter-report',
    templateUrl: './custom-filter-report.component.html',
    styleUrls: ['./custom-filter-report.component.scss'],
    standalone: false
})
export class CustomFilterReportComponent implements OnInit, OnDestroy {

    reportData: CustomFilterReportData[] = [];
    loading: boolean = false;
    selectedStatus: number | null = null;
    selectedDegreeLevel: number | null = null;
    statusOptions: { label: string; value: number }[] = [];
    degreeOptions: { label: string; value: number }[] = [];
    private readonly destroy$ = new Subject<void>();

    constructor(
        private http: HttpClient,
        private lookupService: LookupService,
        private apiUrl: ApiUrlService,
        private messageService: MessageService,
        public translationService: TranslationService,
        private reportCountryScope: ReportCountryScopeService,
        private reportPdfService: ReportPdfService
    ) {}

    ngOnInit(): void {
        this.loadLookupOptions();
        this.loadReportData();
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.loadReportData();
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadLookupOptions(): void {
        forkJoin({
            statuses: this.lookupService.getStudentStudyStatuses(),
            degrees: this.lookupService.getStudentDegrees()
        }).subscribe({
            next: (res) => {
                this.statusOptions = (res.statuses || []).map(s => ({ label: (s as any).name || (s as any).label || '', value: (s as any).id ?? (s as any).value }));
                this.degreeOptions = (res.degrees || []).map(d => ({ label: (d as any).name || (d as any).label || '', value: (d as any).id ?? (d as any).value }));
            },
            error: (err) => {
                console.error('Error loading lookup options for custom filter report', err);
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('COMMON.LOAD_ERROR')
                });
            }
        });
    }

    loadReportData(): void {
        this.loading = true;
        this.http.get<CustomFilterReportData[]>(this.apiUrl.apiPath('/api/custom-reports/custom-filter'), { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (data) => {
                    this.reportData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error loading custom filter report:', error);
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    applyFilters(): void {
        this.loading = true;
        let url = this.apiUrl.apiPath('/api/custom-reports/custom-filter');
        
        if (this.selectedStatus) {
            url = this.apiUrl.apiPath(`/api/custom-reports/custom-filter/by-status/${this.selectedStatus}`);
        } else if (this.selectedDegreeLevel) {
            url = this.apiUrl.apiPath(`/api/custom-reports/custom-filter/by-degree-level/${this.selectedDegreeLevel}`);
        }
        
        this.http.get<CustomFilterReportData[]>(url, { params: this.reportCountryScope.appendCountryParam() })
            .subscribe({
                next: (data) => {
                    this.reportData = data;
                    this.loading = false;
                },
                error: (error) => {
                    console.error('Error applying filters:', error);
                    this.loading = false;
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('COMMON.LOAD_ERROR')
                    });
                }
            });
    }

    clearFilters(): void {
        this.selectedStatus = null;
        this.selectedDegreeLevel = null;
        this.loadReportData();
    }

    private customFilterPdfQuery() {
        const countryId = this.reportCountryScope.countryIdParam();
        const entries: Array<readonly [string, number | null]> = [['countryId', countryId]];
        if (this.selectedStatus != null) {
            entries.push(['statusId', this.selectedStatus]);
        } else if (this.selectedDegreeLevel != null) {
            entries.push(['degreeLevelId', this.selectedDegreeLevel]);
        }
        return buildReportPdfQuery(entries);
    }

    reportLoading = false;
    pdfApiPath = '/api/custom-reports/custom-filter/export-report';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    generateReport(): void {
        this.pdfQuery = { ...this.customFilterPdfQuery(), lang: this.selectedLang };
        this.loadTrigger++;
    }

    /** Filters for this run of the report; shared by all four actions. */

    reportQuery(): ReportPdfQuery {

        return { ...this.customFilterPdfQuery(), lang: this.selectedLang };

    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}

