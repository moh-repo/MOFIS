import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { silentErrors } from '../interceptors/http-context.tokens';
import { StudentTravelTicket, StudentTravelTicketFamilyMember, StudentTravelTicketFilters, TravelFundingSource, WafarBalanceSummary } from '../api/student-travel-ticket';

@Injectable({
    providedIn: 'root'
})
export class StudentTravelTicketService {

    private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/student-travel-tickets`;
    private readonly wafarUrl = `${environment.apiUrl}/mohe/api/v1/wafar`;

    constructor(private http: HttpClient) { }

    getFundingSources(): Observable<TravelFundingSource[]> {
        return this.http.get<TravelFundingSource[]>(`${this.baseUrl}/funding-sources`);
    }

    getWafarBalance(): Observable<WafarBalanceSummary> {
        return this.http.get<WafarBalanceSummary>(`${this.wafarUrl}/balance`);
    }

    getAll(): Observable<StudentTravelTicket[]> {
        return this.http.get<StudentTravelTicket[]>(this.baseUrl);
    }

    getById(travelTicketId: number): Observable<StudentTravelTicket> {
        return this.http.get<StudentTravelTicket>(`${this.baseUrl}/${travelTicketId}`);
    }

    getByStudentId(studentId: number): Observable<StudentTravelTicket[]> {
        return this.http.get<StudentTravelTicket[]>(`${this.baseUrl}/student/${studentId}`);
    }

    getByTravelType(travelType: string): Observable<StudentTravelTicket[]> {
        return this.http.get<StudentTravelTicket[]>(`${this.baseUrl}/type/${travelType}`);
    }

    search(filters: StudentTravelTicketFilters): Observable<StudentTravelTicket[]> {
        let params = new HttpParams();
        if (filters.studentId !== null && filters.studentId !== undefined) {
            params = params.set('studentId', filters.studentId.toString());
        }
        if (filters.travelType !== null && filters.travelType !== undefined) {
            params = params.set('travelType', filters.travelType);
        }
        return this.http.get<StudentTravelTicket[]>(this.baseUrl, { params });
    }

    create(travelTicket: StudentTravelTicket): Observable<StudentTravelTicket> {
        return this.http.post<StudentTravelTicket>(this.baseUrl, travelTicket);
    }

    update(travelTicketId: number, travelTicket: StudentTravelTicket): Observable<StudentTravelTicket> {
        return this.http.put<StudentTravelTicket>(`${this.baseUrl}/${travelTicketId}`, travelTicket);
    }

    /**
     * Stores the ticket PDF for a student and answers with its document id, which the
     * ticket then carries through approval (review T3). Hidden until the ticket is approved.
     */
    uploadTicketDocument(studentId: number, file: File): Observable<{ documentId: number; originalFilename: string }> {
        const form = new FormData();
        form.append('studentId', String(studentId));
        form.append('file', file);
        return this.http.post<{ documentId: number; originalFilename: string }>(`${this.baseUrl}/document`, form,
            { context: silentErrors() });
    }

    /** A ticket PDF as a blob, for opening in a new tab. */
    viewTicketDocument(documentId: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/document/${documentId}`, { responseType: 'blob', context: silentErrors() });
    }

    delete(travelTicketId: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${travelTicketId}`);
    }

    getFamilyMembers(travelTicketId: number): Observable<StudentTravelTicketFamilyMember[]> {
        return this.http.get<StudentTravelTicketFamilyMember[]>(`${this.baseUrl}/${travelTicketId}/family-members`);
    }
}

