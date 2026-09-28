import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentDataAdd } from '../api/student-data-add';
import { environment } from 'src/environments/environment';

const APIUrlStudentData = `${environment.apiUrl}/mohe/api/v1/studentdata/`;

@Injectable({
  providedIn: 'root'
})
export class StudentdataAddService extends DataService {
  constructor(protected override httpClient: HttpClient) {
    super(APIUrlStudentData, httpClient);
  }

  // Override get method to fetch full student data
  override get(id: string | number): Observable<StudentDataAdd> {
    return this.httpClient.get<StudentDataAdd>(`${APIUrlStudentData}${id}`);
  }

  // Override Update method to match base class signature
  override Update(data: any): Observable<any> {
    const id = data.get('studentId') || data.studentId;
    return this.httpClient.put(`${APIUrlStudentData}${id}`, data);
  }

  // Add a separate method for updating with FormData
  updateWithFormData(id: number, formData: FormData): Observable<any> {
    return this.httpClient.put(`${APIUrlStudentData}${id}`, formData);
  }

  // Override Create method to handle FormData
  override Create(formData: FormData): Observable<any> {
    return this.httpClient.post(`${APIUrlStudentData}new`, formData);
  }

  // Add method to delete document
  deleteDocument(docId: number): Observable<any> {
    return this.httpClient.delete(`${APIUrlStudentData}document/${docId}`);
  }

  // Add method to download document
  downloadDocument(docId: number): Observable<Blob> {
    return this.httpClient.get(`${APIUrlStudentData}document/download/${docId}`, 
      { responseType: 'blob' }
    );
  }
}
