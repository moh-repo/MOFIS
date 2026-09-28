import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LookupTableType, LOOKUP_TABLE_TYPES } from '../api/lookuptable';
import { environment } from 'src/environments/environment';
import { silentErrors } from '../interceptors/http-context.tokens';

/**
 * System Coding reports a failed write itself, with the server's reason; the
 * interceptor's toast was a second one for the same refusal (QA B15, live QA #18).
 */
const handled = () => ({ context: silentErrors() });

@Injectable({
  providedIn: 'root'
})
export class LookupTableService {
  private baseUrl = `${environment.apiUrl}/mohe/api/lookup-tables`;

  constructor(private http: HttpClient) { }

  // Get all lookup table types
  getLookupTableTypes(): LookupTableType[] {
    return LOOKUP_TABLE_TYPES;
  }

  // Get lookup table type by ID
  getLookupTableType(id: string): LookupTableType | undefined {
    return LOOKUP_TABLE_TYPES.find(type => type.id === id);
  }

  // Generic CRUD operations for any lookup table
  getAll(
    tableType: string,
    page: number = 0,
    size: number = 10,
    search?: string,
    sortBy?: string,
    sortDir?: string
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (search) {
      params = params.set('search', search);
    }
    if (sortBy) {
      params = params.set('sortBy', sortBy);
    }
    if (sortDir) {
      params = params.set('sortDir', sortDir);
    }

    return this.http.get(`${this.baseUrl}/${tableType}`, { params });
  }

  getById(tableType: string, id: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/${tableType}/${id}`);
  }

  create(tableType: string, data: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/${tableType}`, data, handled());
  }

  update(tableType: string, id: number, data: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/${tableType}/${id}`, data, handled());
  }

  delete(tableType: string, id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/${tableType}/${id}`, handled());
  }

  deleteMultiple(tableType: string, ids: number[]): Observable<any> {
    return this.http.delete(`${this.baseUrl}/${tableType}/batch`, {
      body: { ids },
      ...handled()
    });
  }

  // Specific methods for each lookup table type
  // Student Gender
  getAllStudentGenders(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('student-gender', page, size, search);
  }

  getStudentGenderById(id: number): Observable<any> {
    return this.getById('student-gender', id);
  }

  createStudentGender(data: any): Observable<any> {
    return this.create('student-gender', data);
  }

  updateStudentGender(id: number, data: any): Observable<any> {
    return this.update('student-gender', id, data);
  }

  deleteStudentGender(id: number): Observable<any> {
    return this.delete('student-gender', id);
  }

  // Study Received Whom Status
  getAllStudyReceivedWhomStatuses(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('study-received-whom-status', page, size, search);
  }

  getStudyReceivedWhomStatusById(id: number): Observable<any> {
    return this.getById('study-received-whom-status', id);
  }

  createStudyReceivedWhomStatus(data: any): Observable<any> {
    return this.create('study-received-whom-status', data);
  }

  updateStudyReceivedWhomStatus(id: number, data: any): Observable<any> {
    return this.update('study-received-whom-status', id, data);
  }

  deleteStudyReceivedWhomStatus(id: number): Observable<any> {
    return this.delete('study-received-whom-status', id);
  }

  // MOHE Main
  getAllMoheMains(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('mohe-main', page, size, search);
  }

  getMoheMainById(id: number): Observable<any> {
    return this.getById('mohe-main', id);
  }

  createMoheMain(data: any): Observable<any> {
    return this.create('mohe-main', data);
  }

  updateMoheMain(id: number, data: any): Observable<any> {
    return this.update('mohe-main', id, data);
  }

  deleteMoheMain(id: number): Observable<any> {
    return this.delete('mohe-main', id);
  }

  // Student Degree
  getAllStudentDegrees(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('student-degree', page, size, search);
  }

  getStudentDegreeById(id: number): Observable<any> {
    return this.getById('student-degree', id);
  }

  createStudentDegree(data: any): Observable<any> {
    return this.create('student-degree', data);
  }

  updateStudentDegree(id: number, data: any): Observable<any> {
    return this.update('student-degree', id, data);
  }

  deleteStudentDegree(id: number): Observable<any> {
    return this.delete('student-degree', id);
  }

  // Student Current Level
  getAllStudentCurrentLevels(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('student-current-level', page, size, search);
  }

  getStudentCurrentLevelById(id: number): Observable<any> {
    return this.getById('student-current-level', id);
  }

  createStudentCurrentLevel(data: any): Observable<any> {
    return this.create('student-current-level', data);
  }

  updateStudentCurrentLevel(id: number, data: any): Observable<any> {
    return this.update('student-current-level', id, data);
  }

  deleteStudentCurrentLevel(id: number): Observable<any> {
    return this.delete('student-current-level', id);
  }

  // Study Status
  getAllStudyStatuses(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('study-status', page, size, search);
  }

  getStudyStatusById(id: number): Observable<any> {
    return this.getById('study-status', id);
  }

  createStudyStatus(data: any): Observable<any> {
    return this.create('study-status', data);
  }

  updateStudyStatus(id: number, data: any): Observable<any> {
    return this.update('study-status', id, data);
  }

  deleteStudyStatus(id: number): Observable<any> {
    return this.delete('study-status', id);
  }

  // Study Degree In
  getAllStudyDegreeIns(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('study-degree-in', page, size, search);
  }

  getStudyDegreeInById(id: number): Observable<any> {
    return this.getById('study-degree-in', id);
  }

  createStudyDegreeIn(data: any): Observable<any> {
    return this.create('study-degree-in', data);
  }

  updateStudyDegreeIn(id: number, data: any): Observable<any> {
    return this.update('study-degree-in', id, data);
  }

  deleteStudyDegreeIn(id: number): Observable<any> {
    return this.delete('study-degree-in', id);
  }

  // Student University Type
  getAllStudentUniversityTypes(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('student-university-type', page, size, search);
  }

  getStudentUniversityTypeById(id: number): Observable<any> {
    return this.getById('student-university-type', id);
  }

  createStudentUniversityType(data: any): Observable<any> {
    return this.create('student-university-type', data);
  }

  updateStudentUniversityType(id: number, data: any): Observable<any> {
    return this.update('student-university-type', id, data);
  }

  deleteStudentUniversityType(id: number): Observable<any> {
    return this.delete('student-university-type', id);
  }

  // Student Agency Type
  getAllStudentAgencyTypes(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('student-agency-type', page, size, search);
  }

  getStudentAgencyTypeById(id: number): Observable<any> {
    return this.getById('student-agency-type', id);
  }

  createStudentAgencyType(data: any): Observable<any> {
    return this.create('student-agency-type', data);
  }

  updateStudentAgencyType(id: number, data: any): Observable<any> {
    return this.update('student-agency-type', id, data);
  }

  deleteStudentAgencyType(id: number): Observable<any> {
    return this.delete('student-agency-type', id);
  }

  // MOHE Sub Main
  getAllMoheSubMains(page: number = 0, size: number = 10, search?: string): Observable<any> {
    return this.getAll('mohe-sub-main', page, size, search);
  }

  getMoheSubMainById(id: number): Observable<any> {
    return this.getById('mohe-sub-main', id);
  }

  createMoheSubMain(data: any): Observable<any> {
    return this.create('mohe-sub-main', data);
  }

  updateMoheSubMain(id: number, data: any): Observable<any> {
    return this.update('mohe-sub-main', id, data);
  }

  deleteMoheSubMain(id: number): Observable<any> {
    return this.delete('mohe-sub-main', id);
  }
}
