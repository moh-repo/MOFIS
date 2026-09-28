import { Table } from 'primeng/table';
import { Component, OnInit } from '@angular/core';
import { MessageService, ConfirmationService } from 'primeng/api';
import { StudyYearsLookupApiService, StudyYearsLookupItem } from '../../service/study-years-lookup.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';

@Component({
    selector: 'app-study-years-lookup',
    templateUrl: './study-years-lookup.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudyYearsLookupComponent implements OnInit {
    items: StudyYearsLookupItem[] = [];
    degreeOptions: LookupItem[] = [];
    majorOptions: LookupItem[] = [];
    /** The attachés this user may write a rule for. */
    countryOptions: LookupItem[] = [];
    /**
     * Whether this user may set the shared default (the row with no attaché).
     * Only a ministry user may: an unqualified rule applies to every attaché.
     * Inferred from the countries they hold — an officer with one is a country
     * officer, and the backend refuses the write regardless of what the UI allows.
     */
    canSetSharedDefault = false;
    loading = false;
    dialogVisible = false;

    /** The row the detail dialog is showing. */
    viewing: any = null;
    viewVisible = false;
    deleteDialogVisible = false;
    editMode = false;
    submitted = false;
    current: StudyYearsLookupItem = { studentDegreeId: 0, studentMajorId: 0, studyYears: 0 };
    itemToDelete: StudyYearsLookupItem | null = null;

    constructor(
        private service: StudyYearsLookupApiService,
        private lookupService: LookupService,
        private messageService: MessageService,
        private authService: UmsAuthService,
        private translationService: TranslationService
    ) {}

    // Filters. The list is held whole, so the table narrows itself.
    filterDegree: number | null = null;
    filterMajor: number | null = null;

    get hasFilters(): boolean {
        return this.filterDegree !== null || this.filterMajor !== null;
    }

    clearFilters(dt: Table): void {
        this.filterDegree = null;
        this.filterMajor = null;
        dt.filter(null, 'studentDegreeId', 'equals');
        dt.filter(null, 'studentMajorId', 'equals');
    }

    ngOnInit() {
        this.load();
        this.lookupService.getStudentDegrees().subscribe(d => this.degreeOptions = d);
        this.lookupService.getStudentMajors().subscribe(m => this.majorOptions = m);
        this.lookupService.getAllStudyCountries().subscribe(list => {
            this.countryOptions = (list || []).map((c: any) => ({
                id: c.studyCountryInId ?? c.id,
                value: c.studyCountryInId ?? c.id,
                name: c.studyCountryInName ?? c.studyCountryInNameEn ?? ''
            }));
            this.canSetSharedDefault = this.authService.isSuperAdmin() || this.authService.countryIds.length !== 1;
        });
    }

    load() {
        this.loading = true;
        this.service.getAll().subscribe({
            next: data => { this.items = data; this.loading = false; },
            error: () => {
                this.loading = false;
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('COMMON.LOAD_ERROR')
                });
            }
        });
    }

    openNew() {
        this.current = { studentDegreeId: 0, studentMajorId: 0, studyYears: 0 };
        this.editMode = false;
        this.submitted = false;
        this.dialogVisible = true;
    }

    viewItem(item: any) {
        this.viewing = item;
        this.viewVisible = true;
    }

    editItem(item: StudyYearsLookupItem) {
        this.current = { ...item };
        this.editMode = true;
        this.submitted = false;
        this.dialogVisible = true;
    }

    save() {
        this.submitted = true;
        if (!this.current.studentDegreeId || !this.current.studentMajorId || !this.current.studyYears) return;
        // An edit with no id would fall through to create and duplicate the
        // row instead of changing it. That is a broken state, not a new record.
        if (this.editMode && !this.current.id) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('COMMON.SAVE_ERROR') });
            return;
        }
        const op$ = this.editMode
            ? this.service.update(this.current.id!, this.current)
            : this.service.create(this.current);
        op$.subscribe({
            next: () => {
                this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('STUDY_YEARS_LOOKUP.MESSAGES.SAVED') });
                this.dialogVisible = false;
                this.load();
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDY_YEARS_LOOKUP.MESSAGES.SAVE_ERROR') })
        });
    }

    confirmDelete(item: StudyYearsLookupItem) {
        this.itemToDelete = item;
        this.deleteDialogVisible = true;
    }

    deleteItem() {
        if (!this.itemToDelete?.id) return;
        this.service.delete(this.itemToDelete.id).subscribe({
            next: () => {
                this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('STUDY_YEARS_LOOKUP.MESSAGES.DELETED') });
                this.deleteDialogVisible = false;
                this.itemToDelete = null;
                this.load();
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDY_YEARS_LOOKUP.MESSAGES.DELETE_ERROR') })
        });
    }

    /**
     * A blank attaché is the shared default, not missing data — it is the rule
     * that applies wherever an attaché has not set its own. Saying so on the row
     * matters: otherwise a default and an override look identical in the list.
     */
    countryLabel(id: number | null | undefined): string {
        if (!id) return this.translationService.translate('STUDY_LOOKUP.ALL_ATTACHES');
        return this.countryOptions.find(c => c.value === id)?.name || String(id);
    }

    degreeLabel(id: number): string {
        return this.degreeOptions.find(d => d.value === id)?.name || String(id);
    }

    majorLabel(id: number): string {
        return this.majorOptions.find(m => m.value === id)?.name || String(id);
    }
}
