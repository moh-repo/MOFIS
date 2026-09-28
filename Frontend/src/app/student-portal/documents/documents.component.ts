import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { StudentPortalApiService, StudentDocument, PortalLookupItem } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';
import { filterRows } from '../portal-list.helper';
import { pickLanguage } from '../../demo/pipes/in-language.pipe';
import { extractApiErrorMessage } from '../../demo/service/api-error.util';

@Component({
    selector: 'app-student-portal-documents',
    templateUrl: './documents.component.html',
    styleUrls: ['./documents.component.scss'],
    standalone: false
})
export class DocumentsComponent implements OnInit {

  documents: StudentDocument[] = [];
  loading = true;
  uploadFile: File | null = null;
  uploadName = '';
  /** A DOCUMENT_TYPES name, or none (the server then files it as GENERAL). */
  uploadType: string | null = null;
  documentTypes: PortalLookupItem[] = [];
  uploading = false;
  /** Translation key for fixed messages (SELECT_FILE, UPLOAD_FAILED) */
  uploadErrorKey = '';
  /** Raw message from API when present */
  uploadError = '';

  /**
   * The real file input, so it can be cleared after a successful upload.
   *
   * <p>This used to be `document.getElementById('fileInput')`, and the element
   * has a template reference (#fileInput) rather than an id — so the lookup
   * found nothing and the input kept its last selection. Choosing the same file
   * again fired no change event, and the second upload silently did nothing.
   */
  @ViewChild('fileInput') private fileInputRef?: ElementRef<HTMLInputElement>;

  /** Client-side search: the endpoint returns every document, so this is honest. */
  search = '';

  get filteredDocuments(): StudentDocument[] {
    return filterRows(this.documents, this.search, [
      doc => doc.documentName,
      doc => this.typeLabel(doc.documentType),
      doc => doc.originalFilename
    ]);
  }

  get filteredEverythingOut(): boolean {
    return this.documents.length > 0 && this.filteredDocuments.length === 0;
  }

  clearSearch(): void {
    this.search = '';
  }

  constructor(private api: StudentPortalApiService,
              private translationService: TranslationService,
              private toast: ToastService) {}

  ngOnInit(): void {
    this.load();
    this.api.getDocumentTypes().subscribe({
      next: types => { this.documentTypes = types ?? []; },
      error: () => { this.documentTypes = []; }
    });
  }

  /**
   * The stored type in the reader's language. Rows hold a DOCUMENT_TYPES name
   * (either language), an upper-case code the application writes (PASSPORT,
   * PROFILE_PICTURE…), or — before types were carried through — a file extension,
   * which says nothing about what the document is (live QA #15).
   */
  typeLabel(stored: string | null | undefined): string {
    const raw = (stored ?? '').trim();
    if (!raw || /^(pdf|png|jpe?g|gif|bmp|webp|image|docx?|xlsx?)$/i.test(raw)) {
      return this.translationService.translate('STUDENT_PORTAL.DOC_TYPE_UNSPECIFIED');
    }
    const key = raw.toLowerCase();
    const match = this.documentTypes.find(t =>
      (t.name ?? '').trim().toLowerCase() === key || (t.nameEn ?? '').trim().toLowerCase() === key);
    if (match) {
      return pickLanguage(match, 'name', this.translationService.getCurrentLanguage());
    }
    const code = `STUDENT_PORTAL.DOC_TYPE_${raw.toUpperCase()}`;
    const named = this.translationService.translate(code);
    return named !== code ? named : raw;
  }

  /**
   * The stored name, without the English prefix the server writes on a reply
   * to an information request ("Info request response: <title>"). The type line
   * above it already says what the document is, in the reader's language; the
   * prefix only repeated that in English on the Arabic page (live QA L11,
   * 24 Sep 2026). Any other document shows its name as stored.
   */
  displayName(doc: StudentDocument): string {
    const name = (doc.documentName ?? '').trim();
    if ((doc.documentType ?? '').trim().toUpperCase() === 'INFO_REQUEST_RESPONSE') {
      return name.replace(/^Info request response:\s*/i, '').trim() || name;
    }
    return name;
  }

  load(): void {
    this.loading = true;
    // A failed load used to look like "no documents".
    this.api.getDocuments()
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: (list) => { this.documents = list || []; },
        error: () => this.toast.error(this.translationService.translate('STUDENT_PORTAL.LOAD_DOCUMENTS_ERROR'))
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length) {
      this.uploadFile = input.files[0];
      if (!this.uploadName) this.uploadName = this.uploadFile.name;
      this.uploadErrorKey = '';
      this.uploadError = '';
    }
  }

  clearFile(event: Event): void {
    event.stopPropagation();
    this.uploadFile = null;
    this.uploadName = '';
    this.uploadErrorKey = '';
    this.uploadError = '';
  }

  upload(): void {
    if (!this.uploadFile) {
      this.uploadErrorKey = 'STUDENT_PORTAL.SELECT_FILE';
      this.uploadError = '';
      return;
    }
    this.uploadErrorKey = '';
    this.uploadError = '';
    this.uploading = true;
    this.api.uploadDocument(this.uploadFile, this.uploadName || undefined, this.uploadType || undefined).subscribe({
      next: () => {
        this.uploading = false;
        this.uploadFile = null;
        this.uploadName = '';
        if (this.fileInputRef) {
          this.fileInputRef.nativeElement.value = '';
        }
        this.load();
      },
      error: (err) => {
        this.uploading = false;
        // The refusal's text is in `message` (ErrorDetails); reading only `error` left the
        // card saying "Upload failed." at the 20-document limit (live QA R5-32), and for a
        // file over the size limit (R5-17's message).
        const apiMsg = extractApiErrorMessage(err);
        if (apiMsg) {
          this.uploadError = apiMsg;
          this.uploadErrorKey = '';
        } else {
          this.uploadError = '';
          this.uploadErrorKey = 'STUDENT_PORTAL.UPLOAD_FAILED';
        }
      }
    });
  }

  download(doc: StudentDocument): void {
    this.api.downloadDocument(doc.documentId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.originalFilename || doc.documentName || 'document';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        // A download failure belongs where the download was asked for. This used
        // to set uploadErrorKey, which printed the message inside the upload
        // card at the top of the page — nowhere near the row that failed, and
        // reading as though the upload had gone wrong.
        this.toast.error(this.translationService.translate('STUDENT_PORTAL.DOWNLOAD_FAILED'));
      }
    });
  }
}
