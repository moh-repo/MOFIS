import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

export interface StudentFinancialDataForm {
    studentId: number;
    studentName: string;
    studentCountryId: number;
    studentDelegationDecisionYear: string;
    studentDegreeId: number;
    studentMajorId: number;
    studentDelegateDecisionNo: number;
    studentStudyNoOfYears: number;
    studentCurrentLevelId: number;
    studentFirstReceiptOfDue: number;
    studentAmount: number;
    studentNote1: string;
    studentNote2: string;
    studentStudyStatusId: number;
    studentAgencyTypeId: number;
    studentMinistryTypeId: number;
    studentStudyTypeId: number;
    studentUniversityTypeId: number;
    studentStudyStatusName: string;
    studentAgencyTypeName: string;
    studentCountryName: string;
    studentDegreeName: string;
    studentMajorName: string;
    studentMinistryTypeName: string;
    studentUniversityTypeName: string;
    studyCountryInName: string;
    studentFinReceivedYear: number;
    studentFinReceivedMonth: number;
    headTitle: string;
    quarter: number;
}

/**
 * A Country filter option (v1 and v2). The value is the Arabic name the rows and the
 * PDF are filtered by; the label is the name in the reader's language (live QA R5-29).
 */
export interface CountryFilterOption {
    value: string;
    label: string;
    labelEn: string;
    labelAr: string;
}

/** The options re-labelled after a language switch, without asking the server again. */
export function relabelCountryOptions(options: CountryFilterOption[], arabic: boolean): CountryFilterOption[] {
    return options.map(o => ({ ...o, label: (arabic ? o.labelAr : o.labelEn) || o.value }));
}

export interface FinancialDataFormMetadata {
    years: number[];
    countries: CountryFilterOption[];
    quarters: number[];
}

@Injectable({
    providedIn: 'root'
})
export class FinancialDataFormService {
    private apiUrl = `${environment.apiUrl}/mohe/api/reports/financial-data-form`;

    constructor(private http: HttpClient) { }

    private withCountry(params: HttpParams, countryId?: number | null): HttpParams {
        if (countryId != null) {
            return params.set('countryId', String(countryId));
        }
        return params;
    }

    getAll(countryId?: number | null): Observable<StudentFinancialDataForm[]> {
        return this.http.get<StudentFinancialDataForm[]>(`${this.apiUrl}/`, {
            params: this.withCountry(new HttpParams(), countryId)
        });
    }

    getByQuarterAndYear(quarter: number, year: number, countryId?: number | null): Observable<StudentFinancialDataForm[]> {
        let params = new HttpParams()
            .set('quarter', quarter.toString())
            .set('year', year.toString());
        params = this.withCountry(params, countryId);
        return this.http.get<StudentFinancialDataForm[]>(`${this.apiUrl}/by-quarter-year`, { params });
    }

    getByCountryQuarterAndYear(country: string, quarter: number, year: number, countryId?: number | null): Observable<StudentFinancialDataForm[]> {
        let params = new HttpParams()
            .set('country', country)
            .set('quarter', quarter.toString())
            .set('year', year.toString());
        params = this.withCountry(params, countryId);
        return this.http.get<StudentFinancialDataForm[]>(`${this.apiUrl}/by-country-quarter-year`, { params });
    }

    getMetadata(countryId?: number | null): Observable<FinancialDataFormMetadata> {
        return this.http.get<FinancialDataFormMetadata>(`${this.apiUrl}/metadata`, {
            params: this.withCountry(new HttpParams(), countryId)
        });
    }

    getById(id: number): Observable<StudentFinancialDataForm> {
        return this.http.get<StudentFinancialDataForm>(`${this.apiUrl}/${id}`);
    }
}
