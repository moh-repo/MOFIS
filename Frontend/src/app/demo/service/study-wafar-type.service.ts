import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudyWafarType } from '../api/study-wafar-type';

@Injectable({ providedIn: 'root' })
export class StudyWafarTypeService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/studyWafarType`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<StudyWafarType[]> {
    return this.http.get<StudyWafarType[]>(`${this.baseUrl}/`);
  }

  getById(id: number): Observable<StudyWafarType> {
    return this.http.get<StudyWafarType>(`${this.baseUrl}/${id}`);
  }

  create(payload: StudyWafarType): Observable<StudyWafarType> {
    return this.http.post<StudyWafarType>(`${this.baseUrl}/`, payload);
  }

  update(id: number, payload: StudyWafarType): Observable<StudyWafarType> {
    return this.http.put<StudyWafarType>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
