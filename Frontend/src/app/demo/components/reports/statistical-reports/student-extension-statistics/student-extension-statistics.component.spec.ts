import { TestBed } from '@angular/core/testing';
import { HttpParams, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';
import { BehaviorSubject, of } from 'rxjs';
import { StudentExtensionStatisticsComponent } from './student-extension-statistics.component';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ApiUrlService } from 'src/app/demo/service/api-url.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';

/**
 * FE-13: the export applied the chosen year and the statistics call did not, so the
 * table on screen and the PDF of the same filters counted different extensions. The
 * statistics endpoint now takes `year` as well.
 */
describe('StudentExtensionStatisticsComponent year filter', () => {
    let httpMock: HttpTestingController;
    const STATS = '/api/reports/statistical/student-extension/statistics';
    const isStats = (r: { url: string }) => r.url === STATS;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [StudentExtensionStatisticsComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslationService, useValue: {
                    currentLanguage$: new BehaviorSubject('en'), getCurrentLanguage: () => 'en', translate: (k: string) => k } },
                { provide: ApiUrlService, useValue: { apiPath: (p: string) => p } },
                { provide: ReportCountryScopeService, useValue: {
                    selectedCountryId$: new BehaviorSubject(null),
                    hasMultiCountryAccess: () => false,
                    countryIdParam: () => null,
                    appendCountryParam: () => new HttpParams(),
                    appendMultiCountryParams: () => new HttpParams() } },
                { provide: ReportPdfService, useValue: {} },
                { provide: MessageService, useValue: { add: () => undefined } },
                { provide: LookupService, useValue: { getAcademicYears: () => of([{ value: '2025' }]), getReportLangOptions: () => [] } }
            ]
        });
        TestBed.overrideTemplate(StudentExtensionStatisticsComponent, '');
        httpMock = TestBed.inject(HttpTestingController);
    });

    function open() {
        const fixture = TestBed.createComponent(StudentExtensionStatisticsComponent);
        fixture.detectChanges();
        const first = httpMock.expectOne(isStats);
        expect(first.request.params.has('year')).withContext('no year chosen yet').toBeFalse();
        first.flush([{ extensionType: 'A', approvedCount: 1 }, { extensionType: 'B', approvedCount: 2 }]);
        httpMock.match(r => r.url.endsWith('/trends')).forEach(r => r.flush([]));
        httpMock.match(r => r.url.endsWith('/metadata')).forEach(r => r.flush({}));
        return fixture.componentInstance;
    }

    it('sends the chosen year to /statistics, as the export does', () => {
        const page = open();
        page.selectedYear = 2025;
        page.filterData();

        const reload = httpMock.expectOne(isStats);
        expect(reload.request.params.get('year')).toBe('2025');
        reload.flush([{ extensionType: 'A', approvedCount: 1 }]);
        expect(page.filteredStatistics.length).toBe(1);

        // The type alone is narrowed on screen, without another request.
        page.selectedType = 'B';
        page.filterData();
        httpMock.expectNone(isStats);
        expect(page.filteredStatistics).toEqual([]);

        // Clearing the year reloads the whole set.
        page.clearFilters();
        const cleared = httpMock.expectOne(isStats);
        expect(cleared.request.params.has('year')).toBeFalse();
        cleared.flush([]);
        httpMock.verify();
    });
});
