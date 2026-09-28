import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';

export interface StudyYearsLookupItem {
  id?: number;
  studentDegreeId: number;
  studentMajorId: number;
  studyYears: number;
  /**
   * The attaché this rule applies to. Blank/null is the shared default — the
   * number of years used wherever an attaché has not set its own. Only a
   * ministry user may create or edit a default.
   */
  studyCountryInId?: number | null;
  midate?: string;
}

@Injectable({ providedIn: 'root' })
export class StudyYearsLookupApiService {
  constructor(private http: HttpClient, private apiUrl: ApiUrlService) {}

  getAll(): Observable<StudyYearsLookupItem[]> {
    return this.http.get<StudyYearsLookupItem[]>(`${this.apiUrl.apiPath('/api/v1/lookup/study-years/all')}`);
  }

  create(item: StudyYearsLookupItem): Observable<StudyYearsLookupItem> {
    return this.http.post<StudyYearsLookupItem>(`${this.apiUrl.apiPath('/api/v1/lookup/study-years')}`, item);
  }

  update(id: number, item: StudyYearsLookupItem): Observable<StudyYearsLookupItem> {
    return this.http.put<StudyYearsLookupItem>(`${this.apiUrl.apiPath('/api/v1/lookup/study-years')}/${id}`, item);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl.apiPath('/api/v1/lookup/study-years')}/${id}`);
  }
}
