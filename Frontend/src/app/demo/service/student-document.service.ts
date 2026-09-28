import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { silentErrors } from '../interceptors/http-context.tokens';

const API_URL = `${environment.apiUrl}/mohe/api/v1/studentdata/`;

export interface StudentDocument {
  documentId: number;
  studentId: number;
  documentName: string;
  documentType: string;
  filePath: string;
  originalFilename: string;
  fileSize: number;
  uploadDate: string;
  isActive: boolean;
  expiryDate?: string | null;
  versionNo?: number;
}

/** File format of the stored bytes — derived from the filename, never from DOCUMENT_TYPE. */
export type DocumentFormat = 'pdf' | 'image' | 'word' | 'other';

/**
 * Extensions that appear as DOCUMENT_TYPE values on rows created before the business
 * type was carried through the upload. Such a value describes the format, not the type,
 * so it is treated as "no type set" for display purposes.
 */
const FORMAT_LIKE_TYPES = new Set([
  'pdf', 'jpg', 'jpeg', 'png', 'gif', 'doc', 'docx', 'image', 'document', 'other', 'file'
]);

const FORMAT_BY_EXTENSION: Record<string, DocumentFormat> = {
  pdf: 'pdf',
  jpg: 'image', jpeg: 'image', png: 'image', gif: 'image', bmp: 'image', webp: 'image',
  doc: 'word', docx: 'word'
};

/** An archived document version — a replaced file or a removed document. */
export interface StudentDocumentHistory {
  historyId: number;
  documentId: number;
  studentId: number;
  documentName: string;
  documentType: string;
  filePath: string;
  originalFilename: string;
  fileSize: number;
  versionNo: number;
  /** REPLACED or DELETED. */
  action: string;
  changedBy?: number;
  changedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class StudentDocumentService {

  constructor(private httpClient: HttpClient) { }

  /** Archived versions for a student (replaced files and removed documents), newest first. */
  getDocumentHistory(studentId: number): Observable<StudentDocumentHistory[]> {
    return this.httpClient.get<StudentDocumentHistory[]>(`${API_URL}${studentId}/documents/history`);
  }

  /** Fetches an archived version's bytes through HttpClient so the JWT header is sent. */
  viewHistoryDocument(historyId: number): Observable<Blob> {
    return this.httpClient.get(`${API_URL}document/history/${historyId}/view`, { responseType: 'blob' });
  }

  /** Lowercase extension of the stored file, without the dot. */
  getFileExtension(doc: Pick<StudentDocument, 'originalFilename' | 'filePath'> | null | undefined): string {
    const name = doc?.originalFilename || doc?.filePath || '';
    const idx = name.lastIndexOf('.');
    return idx >= 0 ? name.substring(idx + 1).toLowerCase() : '';
  }

  getDocumentFormat(doc: Pick<StudentDocument, 'originalFilename' | 'filePath'> | null | undefined): DocumentFormat {
    return FORMAT_BY_EXTENSION[this.getFileExtension(doc)] || 'other';
  }

  /** Translation key for the format's display label. */
  getDocumentFormatLabelKey(doc: Pick<StudentDocument, 'originalFilename' | 'filePath'> | null | undefined): string {
    return `STUDENTS.FORMAT_${this.getDocumentFormat(doc).toUpperCase()}`;
  }

  getDocumentFormatIcon(doc: Pick<StudentDocument, 'originalFilename' | 'filePath'> | null | undefined): string {
    switch (this.getDocumentFormat(doc)) {
      case 'pdf': return 'pi pi-file-pdf';
      case 'image': return 'pi pi-image';
      case 'word': return 'pi pi-file-word';
      default: return 'pi pi-file';
    }
  }

  /** True when DOCUMENT_TYPE holds a format/extension rather than a real classification. */
  isFormatLikeType(documentType: string | null | undefined): boolean {
    const raw = (documentType || '').trim().toLowerCase();
    return raw.length === 0 || FORMAT_LIKE_TYPES.has(raw);
  }

  getStudentDocuments(studentId: number): Observable<StudentDocument[]> {
    return this.httpClient.get<StudentDocument[]>(`${API_URL}${studentId}/documents`);
  }

  getDocumentsByStudentId(studentId: number): Observable<StudentDocument[]> {
    return this.getStudentDocuments(studentId);
  }

  deleteDocument(documentId: number): Observable<any> {
    return this.httpClient.delete(`${API_URL}document/${documentId}`);
  }

  /**
   * View and download are silent: every caller reports a failure itself, with the message
   * from `documentErrorKey`. With the interceptor also reporting, one click on "عرض" gave
   * two toasts, and the interceptor's could only say "Not Found" (review item S7).
   */
  downloadDocument(documentId: number): Observable<Blob> {
    return this.httpClient.get(`${API_URL}document/download/${documentId}`, {
      responseType: 'blob',
      context: silentErrors()
    });
  }

  /** See `downloadDocument`. */
  viewDocument(documentId: number): Observable<Blob> {
    return this.httpClient.get(`${API_URL}document/view/${documentId}`, {
      responseType: 'blob',
      context: silentErrors()
    });
  }

  /**
   * The message key for a failed view or download. The server answers 404 when the
   * document row exists but its file is not on disk (never uploaded to this server, or
   * left behind by the country-folder migration); `fallbackKey` covers everything else.
   */
  documentErrorKey(error: unknown, fallbackKey: string): string {
    const status = error instanceof HttpErrorResponse ? error.status : (error as { status?: number })?.status;
    if (status === 404) return 'STUDENTS.MSG_DOCUMENT_FILE_MISSING';
    if (status === 403) return 'COMMON.FORBIDDEN';
    if (status === 0) return 'COMMON.HTTP.CONNECTION_ERROR';
    return fallbackKey;
  }

  /** Adds a new document to an existing student. */
  uploadDocument(studentId: number, file: File, documentName?: string,
                 documentType?: string, expiryDate?: string): Observable<StudentDocument> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (documentName) formData.append('documentName', documentName);
    if (documentType) formData.append('documentType', documentType);
    if (expiryDate) formData.append('expiryDate', expiryDate);
    return this.httpClient.post<StudentDocument>(`${API_URL}${studentId}/documents`, formData);
  }

  updateDocument(documentId: number, documentName: string,
                 documentType?: string, expiryDate?: string | null): Observable<StudentDocument> {
    const body: any = { documentName };
    if (documentType !== undefined) body.documentType = documentType ?? '';
    if (expiryDate !== undefined) body.expiryDate = expiryDate ?? '';
    return this.httpClient.put<StudentDocument>(`${API_URL}document/${documentId}`, body);
  }

  replaceDocument(documentId: number, file: File): Observable<StudentDocument> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.httpClient.post<StudentDocument>(`${API_URL}document/${documentId}/replace`, formData);
  }

  getDocumentViewUrl(documentId: number): string {
    return `${API_URL}document/view/${documentId}`;
  }

  getDocumentDownloadUrl(documentId: number): string {
    return `${API_URL}document/download/${documentId}`;
  }

  /**
   * Fetches the student profile photo through HttpClient so the JWT Authorization
   * header is included. Returns a Blob; caller creates an object URL from it.
   *
   * The endpoint answers 404 whenever a student has no picture, which is an ordinary
   * state rather than a failure — errors are silenced so callers can fall back to a
   * placeholder without the global interceptor reporting "Not Found" to the user.
   */
  getStudentPhotoBlob(studentId: number): Observable<Blob> {
    return this.httpClient.get(`${API_URL}${studentId}/photo`, {
      responseType: 'blob',
      context: silentErrors()
    });
  }

  /**
   * Picks the document most likely to be a portrait of the student.
   *
   * Prefers an explicit type or name hint, then falls back to any image file.
   */
  findLikelyPictureDocument(docs: StudentDocument[]): StudentDocument | undefined {
    const lowered = (docs || []).map(d => ({
      doc: d,
      type: (d.documentType || '').toLowerCase(),
      names: `${d.originalFilename || ''} ${d.documentName || ''}`.toLowerCase()
    }));
    const byHint = lowered.find(({ type, names }) =>
      type.includes('photo') || type.includes('profile') || type === 'picture'
      || type === 'image' || names.includes('profile') || names.includes('photo'));
    if (byHint) {
      return byHint.doc;
    }
    return lowered.find(({ doc }) => this.getDocumentFormat(doc) === 'image')?.doc;
  }

  /**
   * Resolves a student's portrait, emitting null when none can be found.
   *
   * The dedicated photo endpoint only answers when the student row carries a picture
   * path, which is often not the case: a picture uploaded through the add/edit wizard is
   * also stored as a document, and the update endpoint never writes the picture path. So
   * a missing photo falls back to a picture-like document before giving up.
   */
  resolveStudentPhoto(studentId: number): Observable<Blob | null> {
    return this.getStudentPhotoBlob(studentId).pipe(
      // 204 arrives as an empty blob: the student simply has no photo on record.
      switchMap(blob => (blob && blob.size > 0)
        ? of(blob)
        : this.photoFromDocuments(studentId)),
      catchError(() => this.photoFromDocuments(studentId))
    );
  }

  /** Falls back to a picture-like document when no photo is on the student row. */
  private photoFromDocuments(studentId: number): Observable<Blob | null> {
    return this.getStudentDocuments(studentId).pipe(
      catchError(() => of([] as StudentDocument[])),
      switchMap(docs => {
        const picture = this.findLikelyPictureDocument(docs);
        return picture
          ? this.viewDocument(picture.documentId).pipe(catchError(() => of(null)))
          : of(null);
      })
    );
  }
}
