import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MessageService, ConfirmationService } from 'primeng/api';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, finalize, takeUntil } from 'rxjs/operators';
import {
    LocalizedOptionResponse,
    LookupOptionResponse,
    StudentExtensionPayload,
    StudentExtensionResponse
} from './student-extension.models';
import { StudentExtensionService } from '../../service/student-extension.service';
import { TranslationService } from '../../service/translation.service';
import { resetPaging } from '../../service/table-paging.util';

type StatusSeverity = 'success' | 'info' | 'warning' | 'danger' | undefined;

interface StudentExtensionViewModel extends Omit<StudentExtensionResponse,
    'extensionStartDate' | 'extensionEndDate' | 'extensionDecisionDate' | 'createdDate' | 'modifiedDate'> {
    extensionStartDate?: Date | null;
    extensionEndDate?: Date | null;
    extensionDecisionDate?: Date | null;
    createdDate?: Date | null;
    modifiedDate?: Date | null;
}

@Component({
    selector: 'app-student-extension',
    templateUrl: './student-extension.component.html',
    styleUrls: ['./student-extension.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentExtensionComponent implements OnInit, OnDestroy {

    filtersForm: FormGroup;
    extensionForm: FormGroup;

    studentOptions: LookupOptionResponse[] = [];
    extensionTypeOptions: LocalizedOptionResponse[] = [];
    statusOptions: LocalizedOptionResponse[] = [];

    extensions: StudentExtensionViewModel[] = [];
    /** Bound to the table, so a search that shrinks the list starts it on page one (QA B8, N9). */
    first = 0;

    summary = {
        total: 0,
        approved: 0,
        pending: 0,
        rejected: 0,
        inProgress: 0,
        averageQuarters: 0,
        lastUpdated: null as Date | null
    };

    timelineItems: Array<{ label: string; value: string; icon: string; color: string }> = [];

    loading = false;
    metadataLoading = false;
    modalVisible = false;
    modalSubmitting = false;
    drawerVisible = false;
    formMode: 'create' | 'edit' = 'create';
    selectedExtensionId?: number;
    viewingExtension?: StudentExtensionViewModel;
    deletingIds = new Set<number>();

    private readonly destroy$ = new Subject<void>();
    /** The list request in flight; a newer search cancels it so its answer cannot land last. */
    private loadSubscription?: Subscription;
    /** The filters the list was last searched with. */
    private lastFilterKey = '';

    constructor(
        private readonly fb: FormBuilder,
        private readonly extensionService: StudentExtensionService,
        private readonly messageService: MessageService,
        private readonly confirmationService: ConfirmationService,
        public readonly translation: TranslationService
    ) {
        this.filtersForm = this.fb.group({
            filterStudentId: [null],
            passport: [null],
            studentId: [null],
            extensionType: [null],
            status: [null]
        });

        this.extensionForm = this.fb.group({
            studentId: [null, Validators.required],
            extensionType: [null, Validators.required],
            extensionStatus: [null, Validators.required],
            extensionQuarters: [null, [Validators.min(0)]],
            extensionStartDate: [null],
            extensionEndDate: [null],
            extensionDecisionNo: [null],
            extensionDecisionDate: [null],
            approvedBy: [null],
            extensionReason: [null],
            extensionReasonDetails: [null],
            notes: [null]
        });
    }

    /**
     * Filters search as they change, like the other student lists (review item S10): one
     * request once the user pauses, and none when the pause left the filters as they were.
     */
    ngOnInit(): void {
        this.lastFilterKey = this.filterKey();
        this.filtersForm.valueChanges
            .pipe(debounceTime(400), takeUntil(this.destroy$))
            .subscribe(() => this.applyFilters());
        this.loadMetadata();
    }

    /** Searches when the filters differ from the last search; `force` searches regardless. */
    applyFilters(force = false): void {
        const key = this.filterKey();
        if (!force && key === this.lastFilterKey) return;
        this.lastFilterKey = key;
        resetPaging(this);
        this.loadExtensions();
    }

    /** The filters as the server sees them: blank text is no filter. */
    private filterKey(): string {
        const value = this.filtersForm.getRawValue() as Record<string, unknown>;
        return JSON.stringify(Object.keys(value).sort().map(k => {
            const v = value[k];
            return typeof v === 'string' ? (v.trim() || null) : (v ?? null);
        }));
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadMetadata(): void {
        this.metadataLoading = true;
        this.extensionService.getMetadata()
            .pipe(
                takeUntil(this.destroy$),
                finalize(() => this.metadataLoading = false)
            )
            .subscribe({
                next: metadata => {
                    this.studentOptions = this.filterStudentOptions(metadata?.students);
                    this.extensionTypeOptions = metadata?.extensionTypes ?? [];
                    this.statusOptions = metadata?.statuses ?? [];
                    this.loadExtensions();
                },
                        error: () => {
                            this.messageService.add({
                                severity: 'error',
                                summary: this.translation.translate('COMMON.ERROR'),
                                detail: this.translation.translate('STUDENT_EXTENSION.MESSAGES.METADATA_FAILED')
                            });
                            this.studentOptions = [];
                            this.extensionTypeOptions = [];
                            this.statusOptions = [];
                            this.loadExtensions();
                        }
            });
    }

    loadExtensions(): void {
        const filters = this.filtersForm.value ?? {};
        const parsedId = parseInt(filters.filterStudentId, 10);
        this.loadSubscription?.unsubscribe();
        this.loading = true;
        this.loadSubscription = this.extensionService.search({
            studentId: filters.studentId ?? null,
            filterStudentId: filters.filterStudentId && !isNaN(parsedId) ? parsedId : null,
            extensionType: filters.extensionType ?? null,
            status: filters.status ?? null,
            passport: filters.passport || null
        }).pipe(
            takeUntil(this.destroy$),
            finalize(() => this.loading = false)
        ).subscribe({
            next: data => {
                this.extensions = (data ?? []).map(record => this.mapToViewModel(record));
                this.updateSummary();
            },
                error: () => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('STUDENT_EXTENSION.MESSAGES.LOAD_FAILED')
                    });
                    this.extensions = [];
                    this.updateSummary();
                }
        });
    }

    clearFilters(): void {
        this.filtersForm.reset({
            filterStudentId: null,
            passport: null,
            studentId: null,
            extensionType: null,
            status: null
        });
        this.applyFilters();
    }

    openCreate(): void {
        this.formMode = 'create';
        this.selectedExtensionId = undefined;
        this.extensionForm.reset({
            studentId: null,
            extensionType: null,
            extensionStatus: null,
            extensionQuarters: null,
            extensionStartDate: null,
            extensionEndDate: null,
            extensionDecisionNo: null,
            extensionDecisionDate: null,
            approvedBy: null,
            extensionReason: null,
            extensionReasonDetails: null,
            notes: null
        });
        this.modalVisible = true;
    }

    openEdit(extension: StudentExtensionViewModel): void {
        this.formMode = 'edit';
        this.selectedExtensionId = extension.extensionId;
        this.extensionForm.reset({
            studentId: extension.studentId ?? null,
            extensionType: extension.extensionType ?? null,
            extensionStatus: extension.extensionStatus ?? null,
            extensionQuarters: extension.extensionQuarters ?? null,
            extensionStartDate: extension.extensionStartDate ?? null,
            extensionEndDate: extension.extensionEndDate ?? null,
            extensionDecisionNo: extension.extensionDecisionNo ?? null,
            extensionDecisionDate: extension.extensionDecisionDate ?? null,
            approvedBy: extension.approvedBy ?? null,
            extensionReason: extension.extensionReason ?? null,
            extensionReasonDetails: extension.extensionReasonDetails ?? null,
            notes: extension.notes ?? null
        });
        this.modalVisible = true;
    }

    closeModal(): void {
        this.modalVisible = false;
        this.extensionForm.markAsPristine();
    }

    submitForm(): void {
        if (this.extensionForm.invalid) {
            this.extensionForm.markAllAsTouched();
            return;
        }

        const payload = this.buildPayload();
        this.modalSubmitting = true;
        const request$ = this.formMode === 'create'
            ? this.extensionService.create(payload)
            : this.extensionService.update(this.selectedExtensionId!, payload);

        request$.pipe(
            takeUntil(this.destroy$),
            finalize(() => this.modalSubmitting = false)
        ).subscribe({
                next: (response: any) => {
                    const detail = (response?.status === 'PENDING' && response?.message)
                        ? response.message
                        : this.translation.translate(this.formMode === 'create'
                            ? 'STUDENT_EXTENSION.MESSAGES.CREATED'
                            : 'STUDENT_EXTENSION.MESSAGES.UPDATED');
                    this.messageService.add({
                        severity: 'success',
                        summary: this.translation.translate('COMMON.SUCCESS'),
                        detail
                    });
                    this.modalVisible = false;
                    this.loadExtensions();
                },
                error: () => {
                    const messageKey = this.formMode === 'create'
                        ? 'STUDENT_EXTENSION.MESSAGES.CREATE_FAILED'
                        : 'STUDENT_EXTENSION.MESSAGES.UPDATE_FAILED';
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate(messageKey)
                    });
                }
        });
    }

    openView(extension: StudentExtensionViewModel): void {
        this.viewingExtension = extension;
        this.prepareTimeline(extension);
        this.drawerVisible = true;
    }

    closeDrawer(): void {
        this.drawerVisible = false;
        this.viewingExtension = undefined;
    }

    editFromView(): void {
        const ext = this.viewingExtension;
        this.closeDrawer();
        if (ext) this.openEdit(ext);
    }

    deleteExtension(extension: StudentExtensionViewModel): void {
        if (!extension?.extensionId) {
            return;
        }
        
        this.confirmationService.confirm({
            message: this.translation.translate('STUDENT_EXTENSION.CONFIRM.DELETE_DESCRIPTION'),
            header: this.translation.translate('COMMON.CONFIRM'),
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
                const targetId = extension.extensionId;
                this.deletingIds.add(targetId);
                this.extensionService.delete(targetId)
                    .pipe(
                        takeUntil(this.destroy$),
                        finalize(() => this.deletingIds.delete(targetId))
                    )
                    .subscribe({
                        next: (response: any) => {
                            const detail = (response?.status === 'PENDING' && response?.message)
                                ? response.message
                                : this.translation.translate('STUDENT_EXTENSION.MESSAGES.DELETED');
                            this.messageService.add({
                                severity: 'success',
                                summary: this.translation.translate('COMMON.SUCCESS'),
                                detail
                            });
                            this.loadExtensions();
                        },
                        error: () => {
                            this.messageService.add({
                                severity: 'error',
                                summary: this.translation.translate('COMMON.ERROR'),
                                detail: this.translation.translate('STUDENT_EXTENSION.MESSAGES.DELETE_FAILED')
                            });
                        }
                    });
            }
        });
    }

    trackByExtensionId(_: number, item: StudentExtensionViewModel): number {
        return item.extensionId;
    }

    /** Localized display value for an extension type, falling back to the stored value. */
    typeDisplay(extension: StudentExtensionViewModel): string {
        return extension.extensionTypeLabel
            || extension.extensionType
            || this.translation.translate('COMMON.NOT_AVAILABLE');
    }

    /** Localized display value for an extension status, falling back to the stored value. */
    statusDisplay(extension: StudentExtensionViewModel): string {
        return extension.extensionStatusLabel
            || extension.extensionStatus
            || this.translation.translate('COMMON.NOT_AVAILABLE');
    }

    getStatusSeverity(status?: string | null): StatusSeverity {
        const normalized = (status ?? '').toLowerCase();
        if (!normalized) {
            return undefined;
        }
        if (normalized.includes('approve') || normalized.includes('active')) {
            return 'success';
        }
        if (normalized.includes('pending') || normalized.includes('progress')) {
            return 'warning';
        }
        if (normalized.includes('reject') || normalized.includes('cancel')) {
            return 'danger';
        }
        return 'info';
    }

    /** Hero accent for the view dialog, from the same status buckets as the tag severity. */
    statusHeroClass(status?: string | null): string {
        switch (this.getStatusSeverity(status)) {
            case 'success': return 'dv-hero--success';
            case 'warning': return 'dv-hero--warn';
            case 'danger':  return 'dv-hero--danger';
            case 'info':    return 'dv-hero--info';
            default:        return '';
        }
    }

    /** PrimeIcons class for a timeline marker; the item icons use a few names PrimeIcons lacks. */
    timelineMarkerClass(icon: string): string {
        const aliases: Record<string, string> = { 'solution': 'pi-file', 'clock-circle': 'pi-clock' };
        return `pi ${aliases[icon] ?? 'pi-' + icon}`;
    }

    get modalTitle(): string {
        return this.translation.translate(
            this.formMode === 'create'
                ? 'STUDENT_EXTENSION.FORM.TITLE_ADD'
                : 'STUDENT_EXTENSION.FORM.TITLE_EDIT'
        );
    }

    filterOption = (input: string, option: any): boolean => {
        const label = option.nzLabel?.toLowerCase() || '';
        const searchText = input.toLowerCase();
        return label.indexOf(searchText) >= 0;
    }

    private updateSummary(): void {
        const normalized = this.extensions ?? [];
        const statusMatches = (value: string | undefined | null, keywords: string[]): boolean => {
            if (!value) {
                return false;
            }
            const lower = value.toLowerCase();
            return keywords.some(keyword => lower.includes(keyword));
        };

        const approved = normalized.filter(item => statusMatches(item.extensionStatus, ['approve', 'active', 'completed'])).length;
        const pending = normalized.filter(item => statusMatches(item.extensionStatus, ['pending', 'await', 'submitted'])).length;
        const rejected = normalized.filter(item => statusMatches(item.extensionStatus, ['reject', 'denied', 'cancel'])).length;
        const inProgress = normalized.filter(item => statusMatches(item.extensionStatus, ['progress', 'processing', 'review'])).length;

        const quarters = normalized
            .map(item => item.extensionQuarters ?? 0)
            .filter(value => typeof value === 'number' && !Number.isNaN(value)) as number[];

        const averageQuarters = quarters.length
            ? quarters.reduce((sum, value) => sum + value, 0) / quarters.length
            : 0;

        const mostRecent = normalized
            .map(item => item.modifiedDate || item.createdDate)
            .filter((date): date is Date => !!date)
            .sort((a, b) => b.getTime() - a.getTime())[0] ?? null;

        this.summary = {
            total: normalized.length,
            approved,
            pending,
            rejected,
            inProgress,
            averageQuarters: Number(averageQuarters.toFixed(1)),
            lastUpdated: mostRecent
        };
    }

    private prepareTimeline(extension: StudentExtensionViewModel | undefined): void {
        if (!extension) {
            this.timelineItems = [];
            return;
        }

        const items: Array<{ label: string; value: string; icon: string; color: string }> = [];

        if (extension.extensionStartDate) {
            items.push({
                label: this.translation.translate('STUDENT_EXTENSION.TIMELINE.START_DATE'),
                value: extension.extensionStartDate.toLocaleDateString(),
                icon: 'calendar',
                color: 'blue'
            });
        }

        if (extension.extensionDecisionDate || extension.extensionDecisionNo) {
            const decisionParts: string[] = [];
            if (extension.extensionDecisionNo) {
                decisionParts.push(`${this.translation.translate('STUDENT_EXTENSION.FORM.DECISION_NO')}: ${extension.extensionDecisionNo}`);
            }
            if (extension.extensionDecisionDate) {
                decisionParts.push(`${this.translation.translate('STUDENT_EXTENSION.FORM.DECISION_DATE')}: ${extension.extensionDecisionDate.toLocaleDateString()}`);
            }
            if (extension.approvedBy) {
                decisionParts.push(`${this.translation.translate('STUDENT_EXTENSION.FORM.APPROVED_BY')}: ${extension.approvedBy}`);
            }
            items.push({
                label: this.translation.translate('STUDENT_EXTENSION.TIMELINE.DECISION'),
                value: decisionParts.join(' | '),
                icon: 'solution',
                color: 'green'
            });
        }

        if (extension.extensionEndDate) {
            items.push({
                label: this.translation.translate('STUDENT_EXTENSION.TIMELINE.END_DATE'),
                value: extension.extensionEndDate.toLocaleDateString(),
                icon: 'flag',
                color: 'red'
            });
        }

        if (extension.createdDate) {
            const createdBy = extension.createdBy ? ` (${extension.createdBy})` : '';
            items.push({
                label: this.translation.translate('STUDENT_EXTENSION.TIMELINE.CREATED'),
                value: `${extension.createdDate.toLocaleString()}${createdBy}`,
                icon: 'clock-circle',
                color: 'purple'
            });
        }

        if (extension.modifiedDate) {
            const modifiedBy = extension.modifiedBy ? ` (${extension.modifiedBy})` : '';
            items.push({
                label: this.translation.translate('STUDENT_EXTENSION.TIMELINE.UPDATED'),
                value: `${extension.modifiedDate.toLocaleString()}${modifiedBy}`,
                icon: 'sync',
                color: 'orange'
            });
        }

        this.timelineItems = items.length ? items : [{
            label: this.translation.translate('STUDENT_EXTENSION.TIMELINE.NO_EVENTS'),
            value: this.translation.translate('COMMON.NOT_AVAILABLE'),
            icon: 'info-circle',
            color: 'gray'
        }];
    }

    private buildPayload(): StudentExtensionPayload {
        const value = this.extensionForm.value;
        return {
            studentId: value.studentId,
            extensionType: value.extensionType?.trim(),
            extensionStatus: value.extensionStatus?.trim(),
            extensionQuarters: value.extensionQuarters !== null && value.extensionQuarters !== undefined
                ? Number(value.extensionQuarters)
                : null,
            extensionStartDate: this.formatDate(value.extensionStartDate),
            extensionEndDate: this.formatDate(value.extensionEndDate),
            extensionDecisionNo: value.extensionDecisionNo?.trim() || null,
            extensionDecisionDate: this.formatDate(value.extensionDecisionDate),
            approvedBy: value.approvedBy?.trim() || null,
            extensionReason: value.extensionReason?.trim() || null,
            extensionReasonDetails: value.extensionReasonDetails?.trim() || null,
            notes: value.notes?.trim() || null
        };
    }

    private mapToViewModel(response: StudentExtensionResponse): StudentExtensionViewModel {
        return {
            ...response,
            extensionStartDate: this.toDate(response.extensionStartDate),
            extensionEndDate: this.toDate(response.extensionEndDate),
            extensionDecisionDate: this.toDate(response.extensionDecisionDate),
            createdDate: this.toDate(response.createdDate),
            modifiedDate: this.toDate(response.modifiedDate)
        };
    }

    private filterStudentOptions(options?: LookupOptionResponse[]): LookupOptionResponse[] {
        return (options ?? []).filter(
            (option): option is LookupOptionResponse => !!option && option.id !== null
        );
    }

    private toDate(value?: string | null): Date | null {
        if (!value) {
            return null;
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    private formatDate(value?: Date | null): string | null {
        if (!value) {
            return null;
        }
        const year = value.getFullYear();
        const month = `${value.getMonth() + 1}`.padStart(2, '0');
        const day = `${value.getDate()}`.padStart(2, '0');
        return `${year}-${month}-${day}`;
    }
}
