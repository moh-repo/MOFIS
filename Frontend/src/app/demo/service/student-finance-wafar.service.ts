import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { ApprovalPending } from './approval.service';
import { StudentFinanceWafar } from '../api/student-finance-wafar';
import { silentErrors } from '../interceptors/http-context.tokens';

/** What an approval-gated write returns instead of the saved record. */
export { ApprovalPending } from './approval.service';

@Injectable({ providedIn: 'root' })
export class StudentFinanceWafarService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/studentFinanceWafar`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<StudentFinanceWafar[]> {
    return this.http.get<StudentFinanceWafar[]>(`${this.baseUrl}/`);
  }

  /**
   * Student ids with a change awaiting a decision: wafar requests, and finance
   * requests (the finance form carries the wafar). Silent: the badge is extra.
   */
  pending(): Observable<{ wafar: string[]; finance: string[] }> {
    return this.http.get<{ wafar: string[]; finance: string[] }>(`${this.baseUrl}/pending`, {
      context: silentErrors()
    });
  }

  getById(id: number): Observable<StudentFinanceWafar> {
    return this.http.get<StudentFinanceWafar>(`${this.baseUrl}/${id}`);
  }

  /*
   * Writes are submitted to the approval workflow, so these return the
   * { message, status: "PENDING" } envelope rather than the saved record. The
   * row only reaches STUDENT_FINANCE_WAFAR once the request is approved.
   *
   * Errors are silenced for the interceptor because the caller renders the
   * backend's own message (it arrives as { error: "..." }).
   */

  create(payload: StudentFinanceWafar): Observable<ApprovalPending> {
    return this.http.post<ApprovalPending>(`${this.baseUrl}/`, payload, { context: silentErrors() });
  }

  update(id: number, payload: StudentFinanceWafar): Observable<ApprovalPending> {
    return this.http.put<ApprovalPending>(`${this.baseUrl}/${id}`, payload, { context: silentErrors() });
  }

  delete(id: number): Observable<ApprovalPending> {
    return this.http.delete<ApprovalPending>(`${this.baseUrl}/${id}`, { context: silentErrors() });
  }
}
