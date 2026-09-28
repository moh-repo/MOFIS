import { Component, Input, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import {
    ApprovalChangeDetails, ApprovalPendingFile, ApprovalRemovedDocument, ApprovalService
} from '../../service/approval.service';
import { TranslationService } from '../../service/translation.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

/**
 * Renders what a pending request would change — field diff, replacement photo, and
 * attached documents — so it can be reviewed the same way from the approvals queue and
 * from the student's own profile.
 */
@Component({
    selector: 'app-approval-changes',
    imports: [
        CommonModule, TableModule, TagModule, ButtonModule,
        TooltipModule, ProgressSpinnerModule, TranslatePipe
    ],
    templateUrl: './approval-changes.component.html',
    styleUrls: ['./approval-changes.component.scss']
})
export class ApprovalChangesComponent implements OnDestroy {

    @Input() set requestId(id: number | null) {
        if (id && id !== this._requestId) {
            this._requestId = id;
            this.load(id);
        }
    }

    details: ApprovalChangeDetails | null = null;
    loading = false;
    failed = false;
    /** Staged replacement photo. */
    photoUrl: string | null = null;
    /** Photo on record, shown beside the replacement for comparison. */
    currentPhotoUrl: string | null = null;

    private _requestId: number | null = null;
    private objectUrls: string[] = [];

    constructor(
        private approvalService: ApprovalService,
        private studentDocumentService: StudentDocumentService,
        public translationService: TranslationService,
        private messageService: MessageService
    ) {}

    private load(id: number): void {
        this.loading = true;
        this.failed = false;
        this.releaseObjectUrls();
        this.photoUrl = null;
        this.currentPhotoUrl = null;

        this.approvalService.getRequestChanges(id).subscribe({
            next: (details) => {
                this.details = details;
                this.loading = false;
                if (details.photoChanged && details.photo) {
                    this.loadPhotoPreview(id, details.photo.fileName);
                    this.loadCurrentPhoto(details.entityId);
                }
            },
            error: () => {
                this.loading = false;
                this.failed = true;
            }
        });
    }

    private loadPhotoPreview(id: number, fileName: string): void {
        this.approvalService.getStagedFile(id, fileName).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                this.objectUrls.push(url);
                this.photoUrl = url;
            },
            error: () => { this.photoUrl = null; }
        });
    }

    /** Existing photo, for side-by-side comparison. Absent for a new student. */
    private loadCurrentPhoto(entityId: string): void {
        const studentId = Number(entityId);
        if (!Number.isFinite(studentId) || studentId <= 0) {
            return;
        }
        this.studentDocumentService.resolveStudentPhoto(studentId).subscribe({
            next: (blob) => {
                if (!blob) {
                    return;
                }
                const url = URL.createObjectURL(blob);
                this.objectUrls.push(url);
                this.currentPhotoUrl = url;
            },
            error: () => { this.currentPhotoUrl = null; }
        });
    }

    /** Opens a staged document in a new tab; fetched as a blob so the JWT is sent. */
    openDocument(file: ApprovalPendingFile): void {
        if (!this._requestId) {
            return;
        }
        this.approvalService.getStagedFile(this._requestId, file.fileName).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                this.objectUrls.push(url);
                window.open(url, '_blank');
            }
        });
    }

    /**
     * Opens a document that is still stored — a removal has not happened yet, so it is
     * fetched from the document endpoint rather than the staging area.
     */
    openStoredDocument(doc: ApprovalRemovedDocument): void {
        this.studentDocumentService.viewDocument(doc.documentId).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                this.objectUrls.push(url);
                window.open(url, '_blank');
            },
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate(
                        this.studentDocumentService.documentErrorKey(err, 'STUDENTS.MSG_DOCUMENT_VIEW_ERROR'))
                });
            }
        });
    }

    /**
     * Field names come back as entity property names. Translations are looked up under the
     * student form's keys first so approvers see the same wording as the edit screen, with
     * a de-camel-cased fallback for anything not covered.
     */
    fieldLabel(field: string): string {
        const humanized = field
            .replace(/([A-Z])/g, ' $1')
            .replace(/^./, c => c.toUpperCase())
            .trim();
        const key = `STUDENT_ADD.${this.toScreamingSnake(field)}`;
        const translated = this.translationService.translate(key);
        return translated && translated !== key ? translated : humanized;
    }

    private toScreamingSnake(field: string): string {
        return field.replace(/([A-Z])/g, '_$1').toUpperCase();
    }

    get hasAnyChange(): boolean {
        return !!this.details && (
            this.details.fieldChanges.length > 0
            || this.details.photoChanged
            || this.details.documents.length > 0
            || this.details.removedDocuments.length > 0
        );
    }

    private releaseObjectUrls(): void {
        this.objectUrls.forEach(url => URL.revokeObjectURL(url));
        this.objectUrls = [];
    }

    ngOnDestroy(): void {
        this.releaseObjectUrls();
    }
}
