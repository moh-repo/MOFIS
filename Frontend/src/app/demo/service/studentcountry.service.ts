import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudentCountry } from '../api/student-country';

const APIUrlStudentCountry = `${environment.apiUrl}/mohe/api/v1/studentCountry/`;

@Injectable({
  providedIn: 'root'
})
export class studentCountryService extends DataService {
  constructor(http: HttpClient) {
    super(APIUrlStudentCountry, http);
  }

  override Create(data: StudentCountry): Observable<StudentCountry> {
    return this.httpClient.post<StudentCountry>(APIUrlStudentCountry, data);
  }

  override Update(data: StudentCountry): Observable<StudentCountry> {
    return this.httpClient.put<StudentCountry>(`${APIUrlStudentCountry}${data.id}`, data);
  }

  override Delete(id: number): Observable<void> {
    return this.httpClient.delete<void>(`${APIUrlStudentCountry}${id}`);
  }
}
