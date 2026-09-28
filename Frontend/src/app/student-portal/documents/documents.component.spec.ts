import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { DocumentsComponent } from './documents.component';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';

/**
 * Live QA R5-32: at the 20-document limit the reason showed in the top toast, but the
 * upload card itself said only "Upload failed.". The card read `error` from the reply,
 * and the server's refusal (ErrorDetails) carries its text in `message`.
 */
describe('Portal documents — the upload card shows why an upload was refused (R5-32)', () => {

    const LIMIT = 'You already have 20 documents on file. Delete one before uploading another.';

    function build(uploadError: unknown): DocumentsComponent {
        const api = {
            getDocuments: () => of([]),
            getDocumentTypes: () => of([]),
            uploadDocument: () => throwError(() => uploadError)
        } as unknown as StudentPortalApiService;
        const translation = { translate: (k: string) => k, getCurrentLanguage: () => 'en' } as unknown as TranslationService;
        const toast = { error: () => undefined } as unknown as ToastService;

        const component = new DocumentsComponent(api, translation, toast);
        component.uploadFile = new File(['x'], 'passport.pdf', { type: 'application/pdf' });
        return component;
    }

    it('shows the server\'s reason at the document limit', () => {
        const component = build(new HttpErrorResponse({
            status: 400,
            error: { timestamp: '2026-09-27T10:00:00Z', message: LIMIT, details: 'uri=/mohe/api/v1/portal/me/documents' }
        }));

        component.upload();

        expect(component.uploadError).toBe(LIMIT);
        expect(component.uploadErrorKey).toBe('');
        expect(component.uploading).toBeFalse();
    });

    it('shows the size refusal the same way (R5-17\'s translated message)', () => {
        const component = build(new HttpErrorResponse({
            status: 413,
            error: { message: 'الملف كبير جدًا. الحد الأقصى 10 ميغابايت.', details: 'uri=/mohe/api/v1/portal/me/documents' }
        }));

        component.upload();

        expect(component.uploadError).toBe('الملف كبير جدًا. الحد الأقصى 10 ميغابايت.');
    });

    it('falls back to "Upload failed" when the reply carries no text', () => {
        const component = build(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));

        component.upload();

        expect(component.uploadError).toBe('');
        expect(component.uploadErrorKey).toBe('STUDENT_PORTAL.UPLOAD_FAILED');
    });
});
