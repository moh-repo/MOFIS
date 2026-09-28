import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MenuModule } from 'primeng/menu';
import { RippleModule } from 'primeng/ripple';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { StudentDocument, StudentDocumentService } from '../../service/student-document.service';
import { DocumentTypeService } from '../../service/document-type.service';
import { TranslationService } from '../../service/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

/**
 * A student's documents, shared by the students view dialog (read-only) and the
 * quick-edit dialog (editable), so both present the same cards and the same actions.
 *
 * Every action here reaches its own endpoint immediately — nothing is buffered for a
 * host form's save button.
 */
@Component({
    selector: 'app-student-documents',
    imports: [
        CommonModule, FormsModule, ButtonModule, ConfirmDialogModule, DatePickerModule,
        DialogModule, InputTextModule, MenuModule, RippleModule, SelectModule, TagModule,
        TooltipModule, TranslatePipe, HasPermissionDirective
    ],
    templateUrl: './student-documents.component.html',
    styleUrls: ['./student-documents.component.scss'],
    // Its own instance, so the confirm prompt is answered by this component's dialog
    // rather than whichever keyless dialog the host page happens to render.
    providers: [ConfirmationService]
})
export class StudentDocumentsComponent {

    @Input() set studentId(id: number | null | undefined) {
        if (id !== this._studentId) {
            this._studentId = id ?? null;
            if (id) {
                this.loadStudentDocuments(id);
            } else {
                this.studentDocuments = [];
            }
        }
    }
    get studentId(): number | null {
        return this._studentId;
    }

    /** When set, only View and Download are offered — no add, edit, replace or delete. */
    @Input() readonly = false;

    studentDocuments: StudentDocument[] = [];
    loadingDocuments = false;
    documentTypeOptions: { label: string; value: string }[] = [];

    // Document management
    selectedDocument: StudentDocument | null = null;
    editDocumentDialog = false;
    newDocumentName = '';
    newDocumentType = '';
    newDocumentExpiry: Date | null = null;

    // Add / replace document
    addDocumentDialog = false;
    pendingDocumentFile: File | null = null;
    uploadingDocument = false;

    private _studentId: number | null = null;
    /** Set while a file picked through the hidden input should replace this document. */
    private replaceTargetDocument: StudentDocument | null = null;

    constructor(
        private studentDocumentService: StudentDocumentService,
        private documentTypeService: DocumentTypeService,
        private messageService: MessageService,
        private confirmationService: ConfirmationService,
        public translationService: TranslationService
    ) {
        this.loadDocumentTypes();
    }

    loadDocumentTypes() {
        this.documentTypeService.getAllActiveDocumentTypes().subscribe({
            next: (types) => {
                this.documentTypeOptions = (types || []).map(t => ({
                    label: t.documentTypeName,
                    value: t.documentTypeName
                }));
            },
            error: () => {
                this.documentTypeOptions = [];
            }
        });
    }

    /**
     * Business classification (passport, certificate, …). Rows created before types were
     * carried through hold a file extension, which describes the format rather than the
     * type — those read as "unspecified" so the two concepts stay distinct.
     */
    getDocumentTypeLabel(documentType: string | null | undefined): string {
        if (this.studentDocumentService.isFormatLikeType(documentType)) {
            return this.translationService.translate('STUDENTS.UNKNOWN_TYPE');
        }
        const raw = (documentType || '').trim();
        const match = this.documentTypeOptions.find(t => t.value.trim().toLowerCase() === raw.toLowerCase());
        return match ? match.label : raw;
    }

    /** True when the row carries a real business type (not blank, not an extension). */
    hasDocumentType(documentType: string | null | undefined): boolean {
        return !this.studentDocumentService.isFormatLikeType(documentType);
    }

    /** File format (PDF, image, …), derived from the stored filename. */
    getDocumentFormatLabel(studentDocument: StudentDocument): string {
        return this.translationService.translate(
            this.studentDocumentService.getDocumentFormatLabelKey(studentDocument));
    }

    /** Icon reflects the file format — the type is shown as a separate tag. */
    getDocumentTypeIcon(studentDocument: StudentDocument): string {
        return this.studentDocumentService.getDocumentFormatIcon(studentDocument);
    }

    loadStudentDocuments(studentId: number) {
        this.loadingDocuments = true;
        this.studentDocuments = [];

        this.studentDocumentService.getStudentDocuments(studentId).subscribe(
            (documents) => {
                this.studentDocuments = documents;
                this.loadingDocuments = false;
            },
            () => {
                this.studentDocuments = [];
                this.loadingDocuments = false;
                this.messageService.add({
                    severity: 'warn',
                    summary: this.translationService.translate('COMMON.WARNING'),
                    detail: this.translationService.translate('STUDENTS.MSG_DOCUMENTS_LOAD_WARNING')
                });
            }
        );
    }

    /** Fetched through HttpClient so the JWT Authorization header is sent — the raw
     *  endpoint URL cannot be opened directly (the API rejects unauthenticated requests). */
    viewDocument(studentDocument: StudentDocument) {
        this.studentDocumentService.viewDocument(studentDocument.documentId).subscribe({
            next: (blob) => {
                const url = window.URL.createObjectURL(blob);
                const opened = window.open(url, '_blank');
                if (!opened) {
                    const link = document.createElement('a');
                    link.href = url;
                    link.target = '_blank';
                    link.click();
                }
                setTimeout(() => window.URL.revokeObjectURL(url), 60000);
            },
            error: (error) => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate(
                        this.studentDocumentService.documentErrorKey(error, 'STUDENTS.MSG_DOCUMENT_VIEW_ERROR'))
                });
            }
        });
    }

    downloadDocument(studentDocument: StudentDocument) {
        this.studentDocumentService.downloadDocument(studentDocument.documentId).subscribe(
            (blob) => {
                const url = window.URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = studentDocument.originalFilename;
                link.click();
                window.URL.revokeObjectURL(url);
            },
            (error) => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate(
                        this.studentDocumentService.documentErrorKey(error, 'STUDENT_ADD.MSG_DOCUMENT_DOWNLOAD_ERROR'))
                });
            }
        );
    }

    // ── Add document ──────────────────────────────────────────────────────────

    openAddDocumentDialog() {
        this.pendingDocumentFile = null;
        this.newDocumentName = '';
        this.newDocumentType = '';
        this.newDocumentExpiry = null;
        this.addDocumentDialog = true;
    }

    closeAddDocumentDialog() {
        this.addDocumentDialog = false;
        this.pendingDocumentFile = null;
        this.newDocumentName = '';
        this.newDocumentType = '';
        this.newDocumentExpiry = null;
    }

    onAddDocumentFileSelected(event: Event) {
        const input = event.target as HTMLInputElement;
        const file = input.files && input.files.length ? input.files[0] : null;
        this.pendingDocumentFile = file;
        if (file && !this.newDocumentName.trim()) {
            this.newDocumentName = file.name;
        }
        input.value = '';
    }

    uploadDocument() {
        if (!this.pendingDocumentFile || !this._studentId) {
            return;
        }
        this.uploadingDocument = true;
        const studentId = this._studentId;
        this.studentDocumentService.uploadDocument(
            studentId,
            this.pendingDocumentFile,
            this.newDocumentName.trim() || this.pendingDocumentFile.name,
            this.newDocumentType || undefined,
            this.toIsoDate(this.newDocumentExpiry)
        ).subscribe({
            next: () => {
                this.uploadingDocument = false;
                this.closeAddDocumentDialog();
                this.loadStudentDocuments(studentId);
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('STUDENTS.MSG_DOCUMENT_UPLOADED')
                });
            },
            error: (error) => {
                this.uploadingDocument = false;
                console.error('Error uploading document:', error);
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDENTS.MSG_DOCUMENT_UPLOAD_ERROR')
                });
            }
        });
    }

    // ── Replace document file ─────────────────────────────────────────────────

    /** Arms the hidden file input; the actual upload happens in onReplaceFileSelected(). */
    startReplaceDocument(studentDocument: StudentDocument, fileInput: HTMLInputElement) {
        this.replaceTargetDocument = studentDocument;
        fileInput.value = '';
        fileInput.click();
    }

    onReplaceFileSelected(event: Event) {
        const input = event.target as HTMLInputElement;
        const file = input.files && input.files.length ? input.files[0] : null;
        const target = this.replaceTargetDocument;
        input.value = '';
        this.replaceTargetDocument = null;
        if (!file || !target) {
            return;
        }

        this.uploadingDocument = true;
        this.studentDocumentService.replaceDocument(target.documentId, file).subscribe({
            next: () => {
                this.uploadingDocument = false;
                if (this._studentId) {
                    this.loadStudentDocuments(this._studentId);
                }
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('STUDENTS.MSG_DOCUMENT_REPLACED')
                });
            },
            error: (error) => {
                this.uploadingDocument = false;
                console.error('Error replacing document:', error);
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDENTS.MSG_DOCUMENT_REPLACE_ERROR')
                });
            }
        });
    }

    private toIsoDate(value: Date | null): string | undefined {
        if (!value) return undefined;
        const month = `${value.getMonth() + 1}`.padStart(2, '0');
        const day = `${value.getDate()}`.padStart(2, '0');
        return `${value.getFullYear()}-${month}-${day}`;
    }

    // ── Edit document metadata ────────────────────────────────────────────────

    editDocument(studentDocument: StudentDocument) {
        this.selectedDocument = studentDocument;
        this.newDocumentName = studentDocument.documentName;
        this.newDocumentType = studentDocument.documentType || '';
        this.newDocumentExpiry = studentDocument.expiryDate ? new Date(studentDocument.expiryDate) : null;
        this.editDocumentDialog = true;
    }

    saveDocumentName() {
        if (this.selectedDocument && this.newDocumentName.trim()) {
            this.studentDocumentService.updateDocument(
                this.selectedDocument.documentId,
                this.newDocumentName.trim(),
                this.newDocumentType,
                this.toIsoDate(this.newDocumentExpiry) ?? null
            ).subscribe(
                (updatedDocument) => {
                    // Update the document in the list
                    const index = this.studentDocuments.findIndex(doc => doc.documentId === updatedDocument.documentId);
                    if (index !== -1) {
                        this.studentDocuments[index] = updatedDocument;
                    }

                    this.messageService.add({
                        severity: 'success',
                        summary: this.translationService.translate('COMMON.SUCCESS'),
                        detail: this.translationService.translate('STUDENTS.MSG_DOCUMENT_NAME_UPDATED')
                    });

                    this.closeEditDocumentDialog();
                },
                (error) => {
                    console.error('Error updating document:', error);
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('STUDENTS.MSG_DOCUMENT_NAME_UPDATE_ERROR')
                    });
                }
            );
        }
    }

    closeEditDocumentDialog() {
        this.editDocumentDialog = false;
        this.selectedDocument = null;
        this.newDocumentName = '';
        this.newDocumentType = '';
        this.newDocumentExpiry = null;
    }

    deleteDocument(studentDocument: StudentDocument) {
        this.confirmationService.confirm({
            message: this.translationService.translate('STUDENTS.CONFIRM_DELETE_DOCUMENT_MSG').replace('{name}', studentDocument.documentName),
            header: this.translationService.translate('STUDENTS.CONFIRM_DELETE_DOCUMENT'),
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
                this.studentDocumentService.deleteDocument(studentDocument.documentId).subscribe(
                    () => {
                        /*
                         * The document is not gone yet — removal happens when the request is approved,
                         * so it stays in the list rather than disappearing on an unapproved action.
                         */
                        this.messageService.add({
                            severity: 'info',
                            summary: this.translationService.translate('COMMON.INFO'),
                            detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_DELETE_PENDING'),
                            life: 7000
                        });
                    },
                    (error) => {
                        console.error('Error deleting document:', error);
                        this.messageService.add({
                            severity: 'error',
                            summary: this.translationService.translate('COMMON.ERROR'),
                            detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_DELETE_ERROR')
                        });
                    }
                );
            }
        });
    }

    formatFileSize(bytes: number): string {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    getDocumentMenuItems(studentDocument: StudentDocument, replaceInput?: HTMLInputElement): any[] {
        return [
            {
                label: this.translationService.translate('STUDENTS.EDIT_DOCUMENT_NAME'),
                icon: 'pi pi-pencil',
                command: () => this.editDocument(studentDocument)
            },
            {
                label: this.translationService.translate('STUDENTS.REPLACE_DOCUMENT_FILE'),
                icon: 'pi pi-refresh',
                disabled: !replaceInput,
                command: () => {
                    if (replaceInput) {
                        this.startReplaceDocument(studentDocument, replaceInput);
                    }
                }
            },
            {
                label: this.translationService.translate('COMMON.DELETE'),
                icon: 'pi pi-trash',
                command: () => this.deleteDocument(studentDocument)
            }
        ];
    }
}
