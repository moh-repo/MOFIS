import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

const BASE = `${environment.apiUrl}/mohe/api/v1/student-info-requests`;

export interface StudentInfoRequestDto {
  id: number;
  studentId: number;
  studentName?: string;
  requestType: string;
  title: string;
  description?: string;
  createdBy: string;
  createdAt: string;
  dueDate?: string;
  status: string;
  responseText?: string;
  responseAt?: string;
  responseDocumentId?: number;
  reviewStatus?: string;
  reviewedBy?: number;
  reviewedAt?: string;
  rejectionReason?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}

export interface ApproveAndApplyOutcome {
  request: StudentInfoRequestDto;
  studentApprovalRequestId: number | null;
  financeApprovalRequestId: number | null;
  status: 'SUBMITTED_FOR_APPROVAL' | 'NO_CHANGES';
}

@Injectable({ providedIn: 'root' })
export class StudentInfoRequestService {

  constructor(private http: HttpClient) {}

  create(body: {
    requestType: string;
    title: string;
    description?: string;
    dueDate?: string;
    target: { studentIds?: number[]; all?: boolean; countryId?: number; statusId?: number };
  }): Observable<{ count: number; message: string }> {
    return this.http.post<{ count: number; message: string }>(BASE, body);
  }

  list(params: {
    page?: number;
    size?: number;
    createdBy?: string;
    status?: string;
    requestType?: string;
    studentId?: number;
    dateFrom?: string;
    dateTo?: string;
  }): Observable<PageResponse<StudentInfoRequestDto>> {
    let httpParams = new HttpParams();
    if (params.page != null) httpParams = httpParams.set('page', String(params.page));
    if (params.size != null) httpParams = httpParams.set('size', String(params.size));
    if (params.createdBy != null) httpParams = httpParams.set('createdBy', params.createdBy);
    if (params.status != null) httpParams = httpParams.set('status', params.status);
    if (params.requestType != null) httpParams = httpParams.set('requestType', params.requestType);
    if (params.studentId != null) httpParams = httpParams.set('studentId', String(params.studentId));
    if (params.dateFrom != null) httpParams = httpParams.set('dateFrom', params.dateFrom);
    if (params.dateTo != null) httpParams = httpParams.set('dateTo', params.dateTo);
    return this.http.get<PageResponse<StudentInfoRequestDto>>(BASE, { params: httpParams });
  }

  getOne(id: number): Observable<StudentInfoRequestDto> {
    return this.http.get<StudentInfoRequestDto>(`${BASE}/${id}`);
  }

  /** Staging data submitted by student (for admin review before approve/reject). */
  getStaging(id: number): Observable<{ studentDataJson: string; studentFinanceJson: string; submittedAt: string }> {
    return this.http.get<{ studentDataJson: string; studentFinanceJson: string; submittedAt: string }>(`${BASE}/${id}/staging`);
  }

  byStudent(studentId: number, page = 0, size = 20): Observable<PageResponse<StudentInfoRequestDto>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<PageResponse<StudentInfoRequestDto>>(`${BASE}/by-student/${studentId}`, { params });
  }

  cancel(id: number): Observable<StudentInfoRequestDto> {
    return this.http.put<StudentInfoRequestDto>(`${BASE}/${id}/cancel`, {});
  }

  reject(id: number, rejectionReason?: string): Observable<StudentInfoRequestDto> {
    return this.http.put<StudentInfoRequestDto>(`${BASE}/${id}/reject`, { rejectionReason });
  }

  approve(id: number): Observable<StudentInfoRequestDto> {
    return this.http.put<StudentInfoRequestDto>(`${BASE}/${id}/approve`, {});
  }

  /**
   * Approves the answer; its data changes are raised as approval requests (H3-b, D4-a),
   * not written. `status` is SUBMITTED_FOR_APPROVAL or NO_CHANGES.
   */
  approveAndApply(id: number, body: { student?: any; finance?: any }): Observable<ApproveAndApplyOutcome> {
    return this.http.post<ApproveAndApplyOutcome>(`${BASE}/${id}/approve-and-apply`, body);
  }
}
