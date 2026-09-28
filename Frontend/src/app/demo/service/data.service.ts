import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DataService {

   constructor(
     @Inject(String) protected apiUrl: string,
     protected httpClient: HttpClient
   ) { }

  getAll(): Observable<unknown> {
    return this.httpClient.get<unknown>(this.apiUrl);
  }

   getPagingAll(request: { page: string; size: string }): Observable<unknown> {
    const params = new HttpParams()
        .set('page', request.page)
        .set('size', request.size);
    return this.httpClient.get<unknown>(this.apiUrl, { params });
}

  get(id: string | number): Observable<unknown> {
    return this.httpClient.get<unknown>(`${this.apiUrl}${id}`);
  }

  Update(data: unknown): Observable<unknown> {
    return this.httpClient.put<unknown>(`${this.apiUrl}`, data);
  }

  Create(data: unknown): Observable<unknown> {
    return this.httpClient.post<unknown>(this.apiUrl, data);
  }

  Delete(id: string | number): Observable<unknown> {
    return this.httpClient.delete<unknown>(`${this.apiUrl}${id}`);
  }

  search(keyword: string): Observable<unknown> {
    return this.httpClient.get<unknown>(`${this.apiUrl}search?keyword=${encodeURIComponent(keyword)}`);
  }

}
