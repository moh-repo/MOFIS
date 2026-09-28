import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { CountryFilterOption } from './financial-data-form.service';

/**
 * One row of the quarterly financial assistance statement
 * ("كشوفات المساعدة المالية الربعية") — Financial Data Form V2.
 * Mirrors the backend StudentFinancialDataFormV2 entity (view
 * MOHECORE.Q_Stud_Financial_Data_Form_V2, V057).
 */
export interface StudentFinancialDataFormV2 {
    studentId: number;
    studentName: string;
    /** الجهة — university type, else ministry type, else agency type. */
    entityName: string;
    studentAgencyTypeName: string;
    studentMinistryTypeName: string;
    studentUniversityTypeName: string;
    studyCountryInId: number | null;
    studyCountryInName: string;
    studentCountryName: string;
    studentDelegationDecisionYear: string | null;
    studentDegreeName: string;
    studentMajorName: string;
    studentDelegateDecisionNo: number | null;
    studentStudyNoOfYears: number | null;
    studentCurrentLevelId: number | null;
    currentLevelName: string;
    currentLevelNameEn: string;
    currentLevelOrderNo: number | null;
    /** "7" for a study year, else the level name. */
    currentLevelLabel: string;
    studentFirstReceiptOfDue: number | null;
    studentAmount: number | null;
    studentFinReceivedAmount: number | null;
    studentNote1: string | null;
    studentNote2: string | null;
    studentStudyStatusName: string;
    studentFinReceivedYear: number | null;
    /** The quarter number (1–4). */
    studentFinReceivedMonth: number | null;
    moheQuarterId: number | null;
}

export interface FinancialDataFormV2Metadata {
    years: number[];
    countries: CountryFilterOption[];
    quarters: number[];
}

@Injectable({
    providedIn: 'root'
})
export class FinancialDataFormV2Service {
    private apiUrl = `${environment.apiUrl}/mohe/api/reports/financial-data-form-v2`;

    constructor(private http: HttpClient) { }

    private withCountry(params: HttpParams, countryId?: number | null): HttpParams {
        if (countryId != null) {
            return params.set('countryId', String(countryId));
        }
        return params;
    }

    getByQuarterAndYear(quarter: number, year: number, countryId?: number | null): Observable<StudentFinancialDataFormV2[]> {
        let params = new HttpParams()
            .set('quarter', quarter.toString())
            .set('year', year.toString());
        params = this.withCountry(params, countryId);
        return this.http.get<StudentFinancialDataFormV2[]>(`${this.apiUrl}/by-quarter-year`, { params });
    }

    getByCountryQuarterAndYear(country: string, quarter: number, year: number, countryId?: number | null): Observable<StudentFinancialDataFormV2[]> {
        let params = new HttpParams()
            .set('country', country)
            .set('quarter', quarter.toString())
            .set('year', year.toString());
        params = this.withCountry(params, countryId);
        return this.http.get<StudentFinancialDataFormV2[]>(`${this.apiUrl}/by-country-quarter-year`, { params });
    }

    getMetadata(countryId?: number | null): Observable<FinancialDataFormV2Metadata> {
        return this.http.get<FinancialDataFormV2Metadata>(`${this.apiUrl}/metadata`, {
            params: this.withCountry(new HttpParams(), countryId)
        });
    }
}
