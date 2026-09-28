import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StudentQuickEditDto, PagedResponse, StudentQuickEditMetadata } from '../api/student-quick-edit';

@Injectable({
  providedIn: 'root'
})
export class StudentQuickEditService {
  private apiUrl = `${environment.apiUrl}/mohe/api/v1/studentQuickEdit`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<StudentQuickEditDto[]> {
    return this.http.get<StudentQuickEditDto[]>(this.apiUrl);
  }

  getAllPaged(page: number = 0, size: number = 20, sortBy: string = 'studentId', sortDir: string = 'asc'): Observable<PagedResponse<StudentQuickEditDto>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);

    return this.http.get<PagedResponse<StudentQuickEditDto>>(`${this.apiUrl}/paged`, { params });
  }

  getById(id: number): Observable<StudentQuickEditDto> {
    return this.http.get<StudentQuickEditDto>(`${this.apiUrl}/${id}`);
  }

  getMetadata(): Observable<StudentQuickEditMetadata> {
    return this.http.get<StudentQuickEditMetadata>(`${this.apiUrl}/metadata`);
  }

  /**
   * Quick edits go through the approval workflow, so the response is a status envelope
   * ({ status: 'PENDING' | 'NO_CHANGES', message, ... }) rather than the saved row.
   */
  update(id: number, data: StudentQuickEditDto): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}`, data);
  }
}

