import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudentBooksAllowance } from '../api/student-books-allowance';
import { ApprovalPending } from './approval.service';

@Injectable({ providedIn: 'root' })
export class StudentBooksAllowanceService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/studentBooksAllowance`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<StudentBooksAllowance[]> {
    return this.http.get<StudentBooksAllowance[]>(`${this.baseUrl}/`);
  }

  getById(id: number): Observable<StudentBooksAllowance> {
    return this.http.get<StudentBooksAllowance>(`${this.baseUrl}/${id}`);
  }

  getByStudentId(studentId: number): Observable<StudentBooksAllowance[]> {
    return this.http.get<StudentBooksAllowance[]>(`${this.baseUrl}/student/${studentId}`);
  }

  /** Opens an approval request; nothing is written until it is signed off. */
  create(payload: StudentBooksAllowance): Observable<ApprovalPending> {
    return this.http.post<ApprovalPending>(`${this.baseUrl}/`, payload);
  }

  /** Opens an approval request; the row is unchanged until it is signed off. */
  update(id: number, payload: StudentBooksAllowance): Observable<ApprovalPending> {
    return this.http.put<ApprovalPending>(`${this.baseUrl}/${id}`, payload);
  }

  /** Opens a removal request; the row stays until it is signed off. */
  delete(id: number): Observable<ApprovalPending> {
    return this.http.delete<ApprovalPending>(`${this.baseUrl}/${id}`);
  }
}
