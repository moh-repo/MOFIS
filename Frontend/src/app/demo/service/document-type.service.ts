import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentType } from '../api/document-type';
import { environment } from 'src/environments/environment';

const API_URL = `${environment.apiUrl}/mohe/api/v1/document-types/`;

@Injectable({
  providedIn: 'root'
})
export class DocumentTypeService {

  constructor(private httpClient: HttpClient) { }

  getAllDocumentTypes(): Observable<DocumentType[]> {
    return this.httpClient.get<DocumentType[]>(`${API_URL}`);
  }

  getAllActiveDocumentTypes(): Observable<DocumentType[]> {
    return this.httpClient.get<DocumentType[]>(`${API_URL}active`);
  }

  getDocumentTypeById(id: number): Observable<DocumentType> {
    return this.httpClient.get<DocumentType>(`${API_URL}${id}`);
  }

  getDocumentTypeByName(name: string): Observable<DocumentType> {
    return this.httpClient.get<DocumentType>(`${API_URL}name/${encodeURIComponent(name)}`);
  }

  createDocumentType(documentType: DocumentType): Observable<DocumentType> {
    return this.httpClient.post<DocumentType>(`${API_URL}`, documentType);
  }

  updateDocumentType(id: number, documentType: DocumentType): Observable<DocumentType> {
    return this.httpClient.put<DocumentType>(`${API_URL}${id}`, documentType);
  }

  deleteDocumentType(id: number): Observable<void> {
    return this.httpClient.delete<void>(`${API_URL}${id}`);
  }

  // initializeDefaultDocumentTypes() was removed 30 Aug 2026: it posted to
  // /document-types/initialize, an endpoint DocumentTypeController never had,
  // and nothing called it.
}
