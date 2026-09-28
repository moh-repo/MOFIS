import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudyFinanceBankBranch } from '../api/study-finance-bank-branch';
import { silentErrors } from '../interceptors/http-context.tokens';

@Injectable({ providedIn: 'root' })
export class StudyFinanceBankBranchService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/studyFinanceBankBranch`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<StudyFinanceBankBranch[]> {
    return this.http.get<StudyFinanceBankBranch[]>(`${this.baseUrl}/`);
  }

  getById(id: number): Observable<StudyFinanceBankBranch> {
    return this.http.get<StudyFinanceBankBranch>(`${this.baseUrl}/${id}`);
  }

  // The writes are silent: the page's own toast says why one failed, and the
  // interceptor's generic one used to stack on top of it.

  create(payload: StudyFinanceBankBranch): Observable<StudyFinanceBankBranch> {
    return this.http.post<StudyFinanceBankBranch>(`${this.baseUrl}/`, payload, { context: silentErrors() });
  }

  update(id: number, payload: StudyFinanceBankBranch): Observable<StudyFinanceBankBranch> {
    return this.http.put<StudyFinanceBankBranch>(`${this.baseUrl}/${id}`, payload, { context: silentErrors() });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`, { context: silentErrors() });
  }
}
