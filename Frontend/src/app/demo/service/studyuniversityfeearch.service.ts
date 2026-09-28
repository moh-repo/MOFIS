import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError } from 'rxjs';
import { environment } from 'src/environments/environment';

const APIUrlStudyUniversityFeeArch = `${environment.apiUrl}/mohe/api/v1/studyUniversityFeeArch/`;

@Injectable({
  providedIn: 'root'
})
export class StudyUniversityFeeArchService extends DataService {

  constructor(http: HttpClient) {
    super(APIUrlStudyUniversityFeeArch, http);
  }

  override getPagingAll(request: { page: string; size: string }): Observable<any> {
    return this.httpClient.get(`${APIUrlStudyUniversityFeeArch}?page=${request.page}&size=${request.size}`).pipe(
      catchError(error => {
        throw error;
      })
    );
  }

  override getAll(): Observable<any> {
    return this.httpClient.get(`${APIUrlStudyUniversityFeeArch}all`).pipe(
      catchError(error => {
        throw error;
      })
    );
  }

  // No create, update or delete: the fee archive is read-only since live QA H2
  // (24 Sep 2026); the server answers 405 to a write. The inherited Create /
  // Update / Delete of DataService are not overridden and must not be called.

  getByStudentId(studentId: number): Observable<any> {
    return this.httpClient.get(`${APIUrlStudyUniversityFeeArch}student/${studentId}`).pipe(
      catchError(error => {
        throw error;
      })
    );
  }

  searchByStudentName(studentName: string): Observable<any> {
    return this.httpClient.get(`${APIUrlStudyUniversityFeeArch}search?studentName=${studentName}`).pipe(
      catchError(error => {
        throw error;
      })
    );
  }

  getByPayStatus(payStatus: number): Observable<any> {
    return this.httpClient.get(`${APIUrlStudyUniversityFeeArch}status/${payStatus}`).pipe(
      catchError(error => {
        throw error;
      })
    );
  }

  getByYear(year: string): Observable<any> {
    return this.httpClient.get(`${APIUrlStudyUniversityFeeArch}year/${year}`).pipe(
      catchError(error => {
        throw error;
      })
    );
  }

  getDistinctStudents(): Observable<any> {
    return this.httpClient.get(`${APIUrlStudyUniversityFeeArch}distinct-students`).pipe(
      catchError(error => {
        throw error;
      })
    );
  }
}
