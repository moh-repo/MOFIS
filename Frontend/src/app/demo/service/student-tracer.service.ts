import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
    NetworkGraph,
    FilterOptions
} from '../components/student-tracer/student-tracer.models';
import { ReportCountryScopeService } from './report-country-scope.service';

@Injectable({
    providedIn: 'root'
})
export class StudentTracerService {
    private readonly baseUrl = `${environment.apiUrl}/mohe/api/tracer`;

    constructor(
        private readonly http: HttpClient,
        private readonly reportCountryScope: ReportCountryScopeService
    ) {}

    getNetworkGraph(filters?: any): Observable<NetworkGraph> {
        const body = { ...(filters || {}) };
        const countryId = this.reportCountryScope.countryIdParam();
        if (countryId != null) {
            body.countryId = countryId;
        }
        return this.http.post<NetworkGraph>(`${this.baseUrl}/network`, body);
    }

    getFilterOptions(): Observable<FilterOptions> {
        return this.http.get<FilterOptions>(
            this.reportCountryScope.appendCountryToUrl(`${this.baseUrl}/filter-options`)
        );
    }
}
