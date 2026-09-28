import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { StudyFinReceivedWhom } from '../api/study-fin-received-whom';
import { ApprovalPending } from './approval.service';

export interface FinRecipientDocument {
    documentId: number;
    whomId: number;
    documentName: string;
    documentType: string | null;
    filePath: string;
    originalFilename: string;
    fileSize: number;
    uploadDate: string;
    isActive: boolean;
}

@Injectable({ providedIn: 'root' })
export class FinanceReceivedWhomService {
    private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/studyFinReceivedWhom`;

    constructor(private http: HttpClient) {}

    getAll(): Observable<StudyFinReceivedWhom[]> {
        return this.http.get<StudyFinReceivedWhom[]>(`${this.baseUrl}/`);
    }

    getById(id: number): Observable<StudyFinReceivedWhom> {
        return this.http.get<StudyFinReceivedWhom>(`${this.baseUrl}/${id}`);
    }

    /** Opens an approval request; nothing is written until it is signed off. */
    create(payload: StudyFinReceivedWhom): Observable<ApprovalPending> {
        return this.http.post<ApprovalPending>(`${this.baseUrl}/`, payload);
    }

    /** Opens an approval request; the row is unchanged until it is signed off. */
    update(id: number, payload: StudyFinReceivedWhom): Observable<ApprovalPending> {
        return this.http.put<ApprovalPending>(`${this.baseUrl}/${id}`, payload);
    }

    /** Opens a removal request; the row and its documents stay until it is signed off. */
    delete(id: number): Observable<ApprovalPending> {
        return this.http.delete<ApprovalPending>(`${this.baseUrl}/${id}`);
    }

    // ── Documents ──────────────────────────────────────────────────────────────

    getDocuments(whomId: number): Observable<FinRecipientDocument[]> {
        return this.http.get<FinRecipientDocument[]>(`${this.baseUrl}/${whomId}/documents`);
    }

    uploadDocument(whomId: number, file: File, documentName?: string,
                   documentType?: string): Observable<FinRecipientDocument> {
        const formData = new FormData();
        formData.append('file', file, file.name);
        if (documentName) formData.append('documentName', documentName);
        if (documentType) formData.append('documentType', documentType);
        return this.http.post<FinRecipientDocument>(`${this.baseUrl}/${whomId}/documents`, formData);
    }

    viewDocument(docId: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/documents/${docId}/view`, { responseType: 'blob' });
    }

    downloadDocument(docId: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/documents/${docId}/download`, { responseType: 'blob' });
    }

    deleteDocument(docId: number): Observable<any> {
        return this.http.delete(`${this.baseUrl}/documents/${docId}`);
    }
}
