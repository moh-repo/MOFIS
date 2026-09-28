import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudentFinanceArch } from '../api/studentfinancearch';

const APIUrlStudentFinanceArch = `${environment.apiUrl}/mohe/api/v1/studentFinanceArch/`;

@Injectable({
  providedIn: 'root'
})
export class StudentFinanceArchService extends DataService {

  constructor(http: HttpClient) {
    super(APIUrlStudentFinanceArch, http);
  }

  override getPagingAll(request: { page: string; size: string }): Observable<any> {
    return this.httpClient.get(`${APIUrlStudentFinanceArch}?page=${request.page}&size=${request.size}`).pipe(
      catchError(error => throwError(() => error))
    );
  }

  override getAll(): Observable<StudentFinanceArch[]> {
    return this.httpClient.get<StudentFinanceArch[]>(`${APIUrlStudentFinanceArch}all`).pipe(
      catchError(error => throwError(() => error))
    );
  }

  /** Returns all finance archive records for a specific student. */
  getByStudentId(studentId: number): Observable<StudentFinanceArch[]> {
    return this.httpClient.get<StudentFinanceArch[]>(`${APIUrlStudentFinanceArch}byStudent/${studentId}`).pipe(
      catchError(error => throwError(() => error))
    );
  }

  // No Create, Update or Delete: the finance archive is read-only since live QA
  // H2 (24 Sep 2026) — the server answers 405 to a write, and no screen offers one.
}
