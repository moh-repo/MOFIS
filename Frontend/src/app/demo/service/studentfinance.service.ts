import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentFinance } from '../api/studentfinance';
import { silentErrors } from '../interceptors/http-context.tokens';
import { environment } from 'src/environments/environment';

const APIUrlStudentFinance = `${environment.apiUrl}/mohe/api/v1/studentFinance/`;

export interface PagedResponse {
  content: StudentFinance[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

/** What the new-record form learns once a student is picked (review F3/F4). */
export interface FinanceNewRecordInfo {
  studentId: number;
  studentName: string;
  studentAmount: number | null;
  studyCountryInId: number | null;
  /** YYYYQ the student was added in, or 0. */
  addedQuarter: number | null;
  /** One finance record per student: this one is edited, not added again. */
  hasFinanceRecord: boolean;
  pendingRequestId: number | null;
  pendingRequestedAt: string | null;
}

export interface PaginationRequest {
  page: number;
  size: number;
  sortBy?: string;
  sortDir?: string;
}

@Injectable({
  providedIn: 'root'
})
export class StudentFinanceService extends DataService {

  constructor(http: HttpClient) {
    super(APIUrlStudentFinance, http);
  }

  // Get paginated data with sorting
  override getPagingAll(request: { page: string; size: string }): Observable<any> {
    const params = new HttpParams()
      .set('page', request.page)
      .set('size', request.size);

    return this.httpClient.get<any>(`${APIUrlStudentFinance}`, { params });
  }

  // Get paginated data with advanced options
  getPagingAllAdvanced(request: PaginationRequest): Observable<PagedResponse> {
    let params = new HttpParams()
      .set('page', request.page.toString())
      .set('size', request.size.toString());
    
    if (request.sortBy) {
      params = params.set('sortBy', request.sortBy);
    }
    
    if (request.sortDir) {
      params = params.set('sortDir', request.sortDir);
    }

    return this.httpClient.get<PagedResponse>(`${APIUrlStudentFinance}`, { params });
  }

  // Get all data without pagination
  override getAll(): Observable<StudentFinance[]> {
    return this.httpClient.get<StudentFinance[]>(`${APIUrlStudentFinance}all`);
  }

  // Get by ID
  getById(id: number): Observable<StudentFinance> {
    return this.httpClient.get<StudentFinance>(`${APIUrlStudentFinance}${id}`);
  }

  /*
   * StudentFinanceController wraps these in a broad try/catch and returns
   * 400 { error: "..." } for business-rule rejections — most commonly
   * "There is already a pending approval request for this entity". The callers
   * surface that message themselves via extractApiErrorMessage, so the generic
   * interceptor toast is suppressed to avoid showing it twice.
   */

  // Create new record (returns approval response when workflow is enabled)
  override Create(studentFinance: StudentFinance): Observable<StudentFinance | { message: string; status: string }> {
    return this.httpClient.post<StudentFinance | { message: string; status: string }>(
      `${APIUrlStudentFinance}`, studentFinance, { context: silentErrors() });
  }

  // Update existing record (returns approval response when workflow is enabled)
  override Update(studentFinance: StudentFinance): Observable<StudentFinance | { message: string; status: string }> {
    return this.httpClient.put<StudentFinance | { message: string; status: string }>(
      `${APIUrlStudentFinance}${studentFinance.id}`, studentFinance, { context: silentErrors() });
  }

  // Delete record (returns approval response when workflow is enabled)
  override Delete(id: number): Observable<void | { message: string; status: string }> {
    return this.httpClient.delete<void | { message: string; status: string }>(
      `${APIUrlStudentFinance}${id}`, { context: silentErrors() });
  }

  // Search with filters (distinct signature from DataService.search(keyword: string))
  searchFinance(request: PaginationRequest & {
    searchTerm?: string;
    status?: number;
    startDate?: string;
    endDate?: string;
  }): Observable<PagedResponse> {
    let params = new HttpParams()
      .set('page', request.page.toString())
      .set('size', request.size.toString());
    
    if (request.sortBy) {
      params = params.set('sortBy', request.sortBy);
    }
    
    if (request.sortDir) {
      params = params.set('sortDir', request.sortDir);
    }

    if (request.searchTerm && request.searchTerm.trim()) {
      params = params.set('searchTerm', request.searchTerm.trim());
    }

    if (request.status !== null && request.status !== undefined) {
      params = params.set('status', request.status.toString());
    }

    if (request.startDate) {
      params = params.set('startDate', request.startDate);
    }

    if (request.endDate) {
      params = params.set('endDate', request.endDate);
    }

    return this.httpClient.get<PagedResponse>(`${APIUrlStudentFinance}search`, { params });
  }

  // ── Wafar side-record (STUDENT_FINANCE_WAFAR) ───────────────────────────
  // Read-only from here. The wafar row is written by the approval executor once
  // a finance request is approved, so there is no client-side write path.

  /**
   * Wafar row for a finance record. Most students have none, so a 404 is a
   * normal outcome rather than an error — silenced so the global interceptor
   * does not show a "Not Found" toast on every finance record without wafar.
   *
   * The probe cannot be skipped based on STUDENT_FINANCE.WAFAR_AMOUNT: legacy
   * rows can have a STUDENT_FINANCE_WAFAR record while that column is still 0.
   */
  /** Defaults and refusal reasons for a new record for this student; the form handles failure. */
  newRecordFor(studentId: number): Observable<FinanceNewRecordInfo> {
    return this.httpClient.get<FinanceNewRecordInfo>(`${APIUrlStudentFinance}student/${studentId}/new-record`, {
      context: silentErrors()
    });
  }

  getWafar(studentId: number): Observable<any> {
    return this.httpClient.get<any>(`${APIUrlStudentFinance}${studentId}/wafar`, {
      context: silentErrors()
    });
  }
}
