import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { ReportCountryScopeService } from './report-country-scope.service';

const APIUrlCharts = `${environment.apiUrl}/mohe/api/v1/charts`;

@Injectable({ providedIn: 'root' })
export class ChartDataService {

    constructor(
        private http: HttpClient,
        private reportCountryScope: ReportCountryScopeService
    ) {}

    readonly selectedCountryId$ = this.reportCountryScope.selectedCountryId$;

    private scopedGet<T>(path: string): Observable<T> {
        return this.http.get<T>(this.reportCountryScope.appendCountryToUrl(`${APIUrlCharts}${path}`));
    }

    getEnrollmentByCountry(): Observable<any> {
        return this.scopedGet('/enrollment-by-country');
    }

    getFinancialByYear(): Observable<any> {
        return this.scopedGet('/financial-by-year');
    }

    getStudentsByDegree(): Observable<any> {
        return this.scopedGet('/students-by-degree');
    }

    getFinancialByQuarter(): Observable<any> {
        return this.scopedGet('/financial-by-quarter');
    }

    getStatusDistribution(): Observable<any> {
        return this.scopedGet('/status-distribution');
    }

    getMajorDistribution(): Observable<any> {
        return this.scopedGet('/pie/major-distribution');
    }

    getGenderDistribution(): Observable<any> {
        return this.scopedGet('/pie/gender-distribution');
    }

    getUniversityTypeDistribution(): Observable<any> {
        return this.scopedGet('/pie/university-type-distribution');
    }

    getStudyStatusDistribution(): Observable<any> {
        return this.scopedGet('/pie/study-status-distribution');
    }

    getLocationHierarchy(): Observable<any> {
        return this.scopedGet('/hierarchy/location');
    }

    getOrganizationHierarchy(): Observable<any> {
        return this.scopedGet('/hierarchy/organization');
    }

    getAcademicHierarchy(): Observable<any> {
        return this.scopedGet('/hierarchy/academic');
    }

    getGeographicDistribution(): Observable<any> {
        return this.scopedGet('/geo/distribution');
    }

    getDashboardOverview(): Observable<any> {
        return this.scopedGet('/dashboard/overview');
    }

    getEnrollmentGrowthTrend(): Observable<any> {
        return this.scopedGet('/line/enrollment-growth');
    }

    getPaymentPatternsTrend(): Observable<any> {
        return this.scopedGet('/line/payment-patterns');
    }

    getAcademicProgressTrend(): Observable<any> {
        return this.scopedGet('/line/academic-progress');
    }

    getQuarterlyDisbursementTrend(): Observable<any> {
        return this.scopedGet('/line/quarterly-disbursement');
    }

    getStudentRetentionTrend(): Observable<any> {
        return this.scopedGet('/line/student-retention');
    }
}
