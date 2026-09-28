import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentDataArch } from '../api/studentdataarch';
import { environment } from 'src/environments/environment';

const APIUrlStudentDataArch = `${environment.apiUrl}/mohe/api/v1/studentDataArch/`;

@Injectable({
  providedIn: 'root'
})
export class StudentDataArchService {

  constructor(private httpClient: HttpClient) { }

  getAll(): Observable<StudentDataArch[]> {
    return this.httpClient.get<StudentDataArch[]>(`${APIUrlStudentDataArch}`);
  }

  getById(id: number): Observable<StudentDataArch> {
    return this.httpClient.get<StudentDataArch>(`${APIUrlStudentDataArch}${id}`);
  }

  getPaginated(page: number = 0, size: number = 10): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.httpClient.get(`${APIUrlStudentDataArch}paginated`, { params });
  }

  searchByName(name: string): Observable<StudentDataArch[]> {
    const params = new HttpParams().set('name', name);
    return this.httpClient.get<StudentDataArch[]>(`${APIUrlStudentDataArch}search`, { params });
  }

  /**
   * Finance history: the server copies the finance columns only and keeps the rest.
   *
   * This is the only write the archive takes. A snapshot is written by quarter
   * create/close and is never created, replaced or deleted by hand: the endpoints
   * behind the old create/update/delete are gone (live QA 23 Sep 2026, H1).
   */
  updateFinance(id: number, studentDataArch: StudentDataArch): Observable<StudentDataArch> {
    return this.httpClient.put<StudentDataArch>(`${APIUrlStudentDataArch}${id}/finance`, studentDataArch);
  }


  // New methods for distinct students
  getDistinctStudentsPaginated(
    page: number = 0,
    size: number = 10,
    filterStudentId?: number | null,
    filterName?: string,
    filterPassport?: string,
    status?: number | null,
    startDate?: string,
    endDate?: string,
    sortField?: string | null,
    sortDir?: string | null
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (filterStudentId !== null && filterStudentId !== undefined) params = params.set('filterStudentId', filterStudentId.toString());
    if (filterName) params = params.set('filterName', filterName);
    if (filterPassport) params = params.set('filterPassport', filterPassport);
    if (status !== null && status !== undefined) params = params.set('status', status.toString());
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (sortField) params = params.set('sortField', sortField);
    if (sortDir) params = params.set('sortDir', sortDir);
    return this.httpClient.get(`${APIUrlStudentDataArch}distinct/paginated`, { params });
  }

  searchDistinctStudentsByName(name: string): Observable<StudentDataArch[]> {
    const params = new HttpParams().set('name', name);
    return this.httpClient.get<StudentDataArch[]>(`${APIUrlStudentDataArch}distinct/search`, { params });
  }
  
  getAllDistinctStudents(): Observable<StudentDataArch[]> {
    return this.httpClient.get<StudentDataArch[]>(`${APIUrlStudentDataArch}distinct/all`);
  }
  
  getFinancialRecordsByStudentName(studentName: string): Observable<StudentDataArch[]> {
    return this.httpClient.get<StudentDataArch[]>(`${APIUrlStudentDataArch}financial-records/${encodeURIComponent(studentName)}`);
  }

  /** Preferred: all snapshots for one student by the real studentId (avoids same-name mixing). */
  getFinancialRecordsByStudentId(studentId: number): Observable<StudentDataArch[]> {
    return this.httpClient.get<StudentDataArch[]>(`${APIUrlStudentDataArch}financial-records-by-id/${studentId}`);
  }
}
