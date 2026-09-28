import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { silentErrors } from '../interceptors/http-context.tokens';
import { StudentProgressDto } from '../api/student-progress';
import { StudentYearResultDto } from '../api/student-year-result';
import {
  StudentDeathCaseDto,
  StudentDeathCaseRequest,
  StudentDeathProcedureStepDto,
  StudentDeathProcedureStepUpdateRequest,
  StudentParentDto,
  StudentRelativeDto,
  StudentRelativeSearchDto
} from '../api/student-family';

const APIUrlStudentData = `${environment.apiUrl}/mohe/api/v1/studentdata/`;

@Injectable({
  providedIn: 'root'
})
export class StudentdataService extends DataService {

  // constructor() { }

  constructor(protected override httpClient: HttpClient) {
    super(APIUrlStudentData, httpClient);
  }

  /**
   * @param silent set when the caller treats a missing record as an ordinary state (e.g. the
   *               archive/history screen probing progress for a student that may not exist live),
   *               so a 404 is not reported to the user by the global error interceptor.
   */
  /**
   * One student's record, with the global error toast suppressed: for callers that
   * load it as part of a set and say once, in their own words, that the student
   * could not be loaded. `DataService.get` raises the generic toast as well, so the
   * individual-student report showed two for a single failure (review item P2).
   */
  getSilent(studentId: number | string): Observable<unknown> {
    return this.httpClient.get<unknown>(`${APIUrlStudentData}${studentId}`, { context: silentErrors() });
  }

  getProgress(studentId: number, silent = false): Observable<StudentProgressDto> {
    return this.httpClient.get<StudentProgressDto>(`${APIUrlStudentData}${studentId}/progress`,
      silent ? { context: silentErrors() } : {});
  }

  getYearResults(studentId: number, silent = false): Observable<StudentYearResultDto[]> {
    return this.httpClient.get<StudentYearResultDto[]>(`${APIUrlStudentData}${studentId}/year-results`,
      silent ? { context: silentErrors() } : {});
  }

  putYearResults(studentId: number, body: StudentYearResultDto[]): Observable<StudentYearResultDto[]> {
    return this.httpClient.put<StudentYearResultDto[]>(`${APIUrlStudentData}${studentId}/year-results`, body);
  }

  /**
   * Aggregated student 360 snapshot (student, finance, payments, delegation, loans, …).
   *
   * @param silent as above — the individual-student report asks for this alongside three
   *               other calls and reports a partial load once, itself, instead of letting
   *               each failure raise its own toast (review item P2).
   */
  getProfile(studentId: number, silent = false): Observable<Record<string, unknown>> {
    return this.httpClient.get<Record<string, unknown>>(`${APIUrlStudentData}${studentId}/profile`,
      silent ? { context: silentErrors() } : {});
  }

  /**
   * Ids of students with a change awaiting a decision, in the caller's scope. Behind the
   * student read permission rather than the approvals one (live QA M6).
   */
  getPendingStudentIds(): Observable<string[]> {
    return this.httpClient.get<string[]>(`${APIUrlStudentData}pending-ids`);
  }

  getFinancePaymentsByStudent(studentId: number): Observable<any[]> {
    return this.httpClient.get<any[]>(`${environment.apiUrl}/mohe/api/v1/studentFinance/payment/byStudent/${studentId}`);
  }

  getFinanceArchByStudent(studentId: number): Observable<any[]> {
    return this.httpClient.get<any[]>(`${environment.apiUrl}/mohe/api/v1/studentFinanceArch/byStudent/${studentId}`);
  }

  override getPagingAll(request: {
    page: string; size: string;
    search?: string;
    sortField?: string; sortOrder?: string;
    statusId?: number | null;
    filterStudentId?: number | null;
    filterStudentName?: string;
    filterPassport?: string;
  }): Observable<any> {
    let url = `${APIUrlStudentData}studentall?page=${request.page}&size=${request.size}`;

    if (request.search) url += `&search=${encodeURIComponent(request.search)}`;
    if (request.sortField) url += `&sortField=${request.sortField}`;
    if (request.sortOrder) url += `&sortOrder=${request.sortOrder}`;
    if (request.statusId !== null && request.statusId !== undefined) url += `&statusId=${request.statusId}`;
    if (request.filterStudentId !== null && request.filterStudentId !== undefined) url += `&filterStudentId=${request.filterStudentId}`;
    if (request.filterStudentName) url += `&filterName=${encodeURIComponent(request.filterStudentName)}`;
    if (request.filterPassport) url += `&filterPassport=${encodeURIComponent(request.filterPassport)}`;

    return this.httpClient.get(url);
  }

  // Get recent students (top 5 by creation date)
  getRecentStudents(): Observable<any> {
    return this.httpClient.get(`${APIUrlStudentData}recent`);
  }

  override Create(data: unknown): Observable<unknown> {
    return this.httpClient.post<unknown>(`${APIUrlStudentData}new`, data);
  }

  getParents(studentId: number): Observable<StudentParentDto[]> {
    return this.httpClient.get<StudentParentDto[]>(`${APIUrlStudentData}${studentId}/parents`);
  }

  replaceParents(studentId: number, body: StudentParentDto[]): Observable<StudentParentDto[]> {
    return this.httpClient.put<StudentParentDto[]>(`${APIUrlStudentData}${studentId}/parents`, body);
  }

  getRelatives(studentId: number): Observable<StudentRelativeDto[]> {
    return this.httpClient.get<StudentRelativeDto[]>(`${APIUrlStudentData}${studentId}/relatives`);
  }

  replaceRelatives(studentId: number, body: StudentRelativeDto[]): Observable<StudentRelativeDto[]> {
    return this.httpClient.put<StudentRelativeDto[]>(`${APIUrlStudentData}${studentId}/relatives`, body);
  }

  searchRelativeStudents(q: string, excludeStudentId?: number): Observable<StudentRelativeSearchDto[]> {
    let url = `${APIUrlStudentData}relatives/search?q=${encodeURIComponent(q)}`;
    if (excludeStudentId != null) {
      url += `&excludeStudentId=${excludeStudentId}`;
    }
    return this.httpClient.get<StudentRelativeSearchDto[]>(url);
  }

  getDeathCase(studentId: number): Observable<StudentDeathCaseDto | null> {
    return this.httpClient.get<StudentDeathCaseDto>(`${APIUrlStudentData}${studentId}/death-case`, {
      observe: 'response'
    }).pipe(
      map(res => res.status === 204 ? null : (res.body as StudentDeathCaseDto))
    );
  }

  createDeathCase(studentId: number, body: StudentDeathCaseRequest): Observable<StudentDeathCaseDto> {
    return this.httpClient.post<StudentDeathCaseDto>(`${APIUrlStudentData}${studentId}/death-case`, body);
  }

  closeDeathCase(studentId: number): Observable<StudentDeathCaseDto> {
    return this.httpClient.put<StudentDeathCaseDto>(`${APIUrlStudentData}${studentId}/death-case/close`, {});
  }

  updateDeathProcedureStep(
    studentId: number,
    stepId: number,
    body: StudentDeathProcedureStepUpdateRequest
  ): Observable<StudentDeathProcedureStepDto> {
    return this.httpClient.put<StudentDeathProcedureStepDto>(
      `${APIUrlStudentData}${studentId}/death-case/steps/${stepId}`,
      body
    );
  }
}
