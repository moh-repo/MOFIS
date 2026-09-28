import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentDataAdd } from '../api/student-data-add';
import { environment } from 'src/environments/environment';

const APIUrl = `${environment.apiUrl}/mohe/api/v1/studentdata/`;

@Injectable({
    providedIn: 'root'
})
export class StudentNameValidationService {
    
    constructor(private httpClient: HttpClient) { }

    /**
     * Check if a student name already exists in the database
     * @param name The student name to check
     * @returns Observable of array of matching student records
     */
    checkDuplicateName(name: string): Observable<StudentDataAdd[]> {
        // Use HttpParams which automatically handles URL encoding for special characters
        const params = new HttpParams().set('name', name.trim());
        return this.httpClient.get<StudentDataAdd[]>(`${APIUrl}check-duplicate-name`, { params });
    }
}
