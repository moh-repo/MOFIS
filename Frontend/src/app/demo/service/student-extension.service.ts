import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import {
    StudentExtensionFilters,
    StudentExtensionMetadataResponse,
    StudentExtensionPayload,
    StudentExtensionResponse
} from '../components/student-extension/student-extension.models';

@Injectable({
    providedIn: 'root'
})
export class StudentExtensionService {

    private readonly baseUrl = `${environment.apiUrl}/mohe/api/student-extension`;

    constructor(private http: HttpClient) { }

    getMetadata(): Observable<StudentExtensionMetadataResponse> {
        return this.http.get<StudentExtensionMetadataResponse>(`${this.baseUrl}/metadata`);
    }

    search(filters: StudentExtensionFilters): Observable<StudentExtensionResponse[]> {
        const params = this.buildParams(filters);
        return this.http.get<StudentExtensionResponse[]>(this.baseUrl, { params });
    }

    getById(extensionId: number): Observable<StudentExtensionResponse> {
        return this.http.get<StudentExtensionResponse>(`${this.baseUrl}/${extensionId}`);
    }

    /** Create; when workflow is enabled returns { message, status } instead of the entity. */
    create(payload: StudentExtensionPayload): Observable<StudentExtensionResponse | { message: string; status: string }> {
        return this.http.post<StudentExtensionResponse | { message: string; status: string }>(this.baseUrl, payload);
    }

    /** Update; when workflow is enabled returns { message, status } instead of the entity. */
    update(extensionId: number, payload: StudentExtensionPayload): Observable<StudentExtensionResponse | { message: string; status: string }> {
        return this.http.put<StudentExtensionResponse | { message: string; status: string }>(`${this.baseUrl}/${extensionId}`, payload);
    }

    /** Delete; when workflow is enabled returns { message, status } instead of void. */
    delete(extensionId: number): Observable<void | { message: string; status: string }> {
        return this.http.delete<void | { message: string; status: string }>(`${this.baseUrl}/${extensionId}`);
    }

    private buildParams(filters: StudentExtensionFilters): HttpParams {
        let params = new HttpParams();
        if (filters.studentId !== null && filters.studentId !== undefined) {
            params = params.set('studentId', filters.studentId);
        }
        if (filters.filterStudentId !== null && filters.filterStudentId !== undefined) {
            params = params.set('filterStudentId', filters.filterStudentId);
        }
        if (filters.extensionType) {
            params = params.set('extensionType', filters.extensionType);
        }
        if (filters.status) {
            params = params.set('status', filters.status);
        }
        if (filters.passport) {
            params = params.set('passport', filters.passport);
        }
        return params;
    }
}


