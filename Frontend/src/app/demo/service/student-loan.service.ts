import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudentLoan } from '../api/student-loan';
import { ApprovalPending } from './approval.service';

@Injectable({ providedIn: 'root' })
export class StudentLoanService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/studentLoan`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<StudentLoan[]> {
    return this.http.get<StudentLoan[]>(`${this.baseUrl}/`);
  }

  getById(id: number): Observable<StudentLoan> {
    return this.http.get<StudentLoan>(`${this.baseUrl}/${id}`);
  }

  getByStudentId(studentId: number): Observable<StudentLoan[]> {
    return this.http.get<StudentLoan[]>(`${this.baseUrl}/student/${studentId}`);
  }

  /** Opens an approval request; nothing is written until it is signed off. */
  create(payload: StudentLoan): Observable<ApprovalPending> {
    return this.http.post<ApprovalPending>(`${this.baseUrl}/`, payload);
  }

  /** Opens an approval request; the row is unchanged until it is signed off. */
  update(id: number, payload: StudentLoan): Observable<ApprovalPending> {
    return this.http.put<ApprovalPending>(`${this.baseUrl}/${id}`, payload);
  }

  /** Opens a removal request; the row stays until it is signed off. */
  delete(id: number): Observable<ApprovalPending> {
    return this.http.delete<ApprovalPending>(`${this.baseUrl}/${id}`);
  }
}
