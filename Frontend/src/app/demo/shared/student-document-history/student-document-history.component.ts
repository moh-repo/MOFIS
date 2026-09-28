import { Component, Input, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import {
    StudentDocumentHistory, StudentDocumentService
} from '../../service/student-document.service';
import { TranslationService } from '../../service/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

/**
 * Read-only list of a student's archived document versions — files that were replaced and
 * documents that were removed, including superseded profile photos. Shared by the students
 * view dialog and the profile page so both present history identically.
 */
@Component({
    selector: 'app-student-document-history',
    imports: [CommonModule, ButtonModule, TagModule, TooltipModule, TranslatePipe],
    templateUrl: './student-document-history.component.html',
    styleUrls: ['./student-document-history.component.scss']
})
export class StudentDocumentHistoryComponent implements OnDestroy {

    @Input() set studentId(id: number | null | undefined) {
        if (id && id !== this._studentId) {
            this._studentId = id;
            this.load(id);
        }
    }

    history: StudentDocumentHistory[] = [];
    loading = false;
    /** Thumbnail object URLs for image entries, keyed by history id. */
    thumbnails: { [historyId: number]: string } = {};

    private _studentId: number | null = null;
    private objectUrls: string[] = [];

    constructor(
        private documentService: StudentDocumentService,
        public translationService: TranslationService
    ) {}

    private load(studentId: number): void {
        this.loading = true;
        this.releaseObjectUrls();
        this.thumbnails = {};

        this.documentService.getDocumentHistory(studentId).subscribe({
            next: (items) => {
                this.history = items ?? [];
                this.loading = false;
                this.history.filter(h => this.isImage(h)).forEach(h => this.loadThumbnail(h));
            },
            error: () => {
                this.history = [];
                this.loading = false;
            }
        });
    }

    private loadThumbnail(item: StudentDocumentHistory): void {
        this.documentService.viewHistoryDocument(item.historyId).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                this.objectUrls.push(url);
                this.thumbnails[item.historyId] = url;
            },
            error: () => { /* leave the file icon */ }
        });
    }

    /** Opens an archived version in a new tab; fetched as a blob so the JWT is sent. */
    view(item: StudentDocumentHistory): void {
        this.documentService.viewHistoryDocument(item.historyId).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                this.objectUrls.push(url);
                window.open(url, '_blank');
            }
        });
    }

    isImage(item: StudentDocumentHistory): boolean {
        return this.documentService.getDocumentFormat(item) === 'image';
    }

    isPhoto(item: StudentDocumentHistory): boolean {
        const type = (item.documentType || '').toLowerCase();
        const name = `${item.originalFilename || ''} ${item.documentName || ''}`.toLowerCase();
        return type.includes('profile') || type.includes('photo') || type === 'picture'
            || name.includes('profile') || name.includes('photo');
    }

    actionSeverity(action: string): string {
        return action === 'DELETED' ? 'danger' : 'warning';
    }

    actionLabelKey(action: string): string {
        return action === 'DELETED' ? 'STUDENTS.HISTORY_REMOVED' : 'STUDENTS.HISTORY_REPLACED';
    }

    formatFileSize(bytes: number): string {
        if (!bytes) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
    }

    private releaseObjectUrls(): void {
        this.objectUrls.forEach(url => URL.revokeObjectURL(url));
        this.objectUrls = [];
    }

    ngOnDestroy(): void {
        this.releaseObjectUrls();
    }
}
