import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudyFinanceBank } from '../api/study-finance-bank';

@Injectable({ providedIn: 'root' })
export class StudyFinanceBankService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/studyFinanceBank`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<StudyFinanceBank[]> {
    return this.http.get<StudyFinanceBank[]>(`${this.baseUrl}/`);
  }

  getById(id: number): Observable<StudyFinanceBank> {
    return this.http.get<StudyFinanceBank>(`${this.baseUrl}/${id}`);
  }

  create(payload: StudyFinanceBank): Observable<StudyFinanceBank> {
    return this.http.post<StudyFinanceBank>(`${this.baseUrl}/`, payload);
  }

  update(id: number, payload: StudyFinanceBank): Observable<StudyFinanceBank> {
    return this.http.put<StudyFinanceBank>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
