import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

const APIUrlStudyUniversityFee = `${environment.apiUrl}/mohe/api/v1/studyUniversityFee/`;

@Injectable({
    providedIn: 'root'
})
export class StudyUniversityFeeService extends DataService{

    constructor(http: HttpClient) {
        super(APIUrlStudyUniversityFee, http);
    }

    getAllFees(): Observable<any[]> {
        return this.httpClient.get<any[]>(APIUrlStudyUniversityFee);
    }

    /** Backend is @PostMapping("/") — not .../new */
    override Create(data: unknown): Observable<unknown> {
        return this.httpClient.post<unknown>(`${APIUrlStudyUniversityFee}`, data);
    }

    /** Backend is @PutMapping("/{id}") */
    override Update(data: unknown): Observable<unknown> {
        const id = (data as { id?: number }).id;
        return this.httpClient.put<unknown>(`${APIUrlStudyUniversityFee}${id}`, data);
    }

  /**
   * One page of the fee list, searched and filtered on the server (review F7). This
   * used to call the collection endpoint, which ignored every parameter and returned
   * all rows, so the search box and the filters did nothing.
   */
  override getPagingAll(request: {
    page: string;
    size: string;
    searchTerm?: string;
    status?: number | null;
    startDate?: string;
    endDate?: string;
    sortBy?: string;
    sortDir?: 'asc' | 'desc';
  }): Observable<any> {
    let params = new HttpParams()
      .set('page', request.page)
      .set('size', request.size);
    if (request.sortBy) {
      params = params.set('sortBy', request.sortBy).set('sortDir', request.sortDir || 'asc');
    }
    if (request.searchTerm?.trim()) {
      params = params.set('searchTerm', request.searchTerm.trim());
    }
    if (request.status !== null && request.status !== undefined) {
      params = params.set('status', request.status.toString());
    }
    if (request.startDate) {
      params = params.set('startDate', request.startDate);
    }
    if (request.endDate) {
      params = params.set('endDate', request.endDate);
    }
    return this.httpClient.get(`${APIUrlStudyUniversityFee}search`, { params });
  }
    
}
