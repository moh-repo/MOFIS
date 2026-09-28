import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';

export interface StudyAmountLookupItem {
  id?: number;
  studentDegreeId: number;
  studentAgencyTypeId: number;
  amount: number;
  currencyId?: number;
  /**
   * The attaché this amount applies to. Blank/null is the shared default. Only a
   * ministry user may create or edit a default.
   */
  studyCountryInId?: number | null;
  midate?: string;
}

@Injectable({ providedIn: 'root' })
export class StudyAmountLookupApiService {
  constructor(private http: HttpClient, private apiUrl: ApiUrlService) {}

  getAll(): Observable<StudyAmountLookupItem[]> {
    return this.http.get<StudyAmountLookupItem[]>(`${this.apiUrl.apiPath('/api/v1/lookup/study-amount/all')}`);
  }

  create(item: StudyAmountLookupItem): Observable<StudyAmountLookupItem> {
    return this.http.post<StudyAmountLookupItem>(`${this.apiUrl.apiPath('/api/v1/lookup/study-amount')}`, item);
  }

  update(id: number, item: StudyAmountLookupItem): Observable<StudyAmountLookupItem> {
    return this.http.put<StudyAmountLookupItem>(`${this.apiUrl.apiPath('/api/v1/lookup/study-amount')}/${id}`, item);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl.apiPath('/api/v1/lookup/study-amount')}/${id}`);
  }
}
