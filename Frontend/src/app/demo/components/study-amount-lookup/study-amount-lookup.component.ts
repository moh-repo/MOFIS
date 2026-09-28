import { Table } from 'primeng/table';
import { Component, OnInit } from '@angular/core';
import { MessageService } from 'primeng/api';
import { StudyAmountLookupApiService, StudyAmountLookupItem } from '../../service/study-amount-lookup.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';

@Component({
    selector: 'app-study-amount-lookup',
    templateUrl: './study-amount-lookup.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudyAmountLookupComponent implements OnInit {
    items: StudyAmountLookupItem[] = [];
    degreeOptions: LookupItem[] = [];
    agencyTypeOptions: LookupItem[] = [];
    currencyOptions: LookupItem[] = [];
    /** The attaches this user may write an amount for. */
    countryOptions: LookupItem[] = [];
    /** Only a ministry user may set the shared default; see the years lookup. */
    canSetSharedDefault = false;
    loading = false;
    dialogVisible = false;

    /** The row the detail dialog is showing. */
    viewing: any = null;
    viewVisible = false;
    deleteDialogVisible = false;
    editMode = false;
    submitted = false;
    current: StudyAmountLookupItem = { studentDegreeId: 0, studentAgencyTypeId: 0, amount: 0 };
    itemToDelete: StudyAmountLookupItem | null = null;

    constructor(
        private service: StudyAmountLookupApiService,
        private lookupService: LookupService,
        private messageService: MessageService,
        private authService: UmsAuthService,
        private translationService: TranslationService
    ) {}

    // Filters. The list is held whole, so the table narrows itself.
    filterDegree: number | null = null;
    filterAgency: number | null = null;

    get hasFilters(): boolean {
        return this.filterDegree !== null || this.filterAgency !== null;
    }

    clearFilters(dt: Table): void {
        this.filterDegree = null;
        this.filterAgency = null;
        dt.filter(null, 'studentDegreeId', 'equals');
        dt.filter(null, 'studentAgencyTypeId', 'equals');
    }

    ngOnInit() {
        this.load();
        this.lookupService.getStudentDegrees().subscribe(d => this.degreeOptions = d);
        this.lookupService.getAgencyTypes().subscribe(a => this.agencyTypeOptions = a);
        this.lookupService.getStudyCurrencies().subscribe(c => this.currencyOptions = c);
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
        this.current = { studentDegreeId: 0, studentAgencyTypeId: 0, amount: 0 };
        this.editMode = false;
        this.submitted = false;
        this.dialogVisible = true;
    }

    viewItem(item: any) {
        this.viewing = item;
        this.viewVisible = true;
    }

    editItem(item: StudyAmountLookupItem) {
        this.current = { ...item };
        this.editMode = true;
        this.submitted = false;
        this.dialogVisible = true;
    }

    save() {
        this.submitted = true;
        if (!this.current.studentDegreeId || !this.current.studentAgencyTypeId || !this.current.amount) return;
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
                this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('STUDY_AMOUNT_LOOKUP.MESSAGES.SAVED') });
                this.dialogVisible = false;
                this.load();
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDY_AMOUNT_LOOKUP.MESSAGES.SAVE_ERROR') })
        });
    }

    confirmDelete(item: StudyAmountLookupItem) {
        this.itemToDelete = item;
        this.deleteDialogVisible = true;
    }

    deleteItem() {
        if (!this.itemToDelete?.id) return;
        this.service.delete(this.itemToDelete.id).subscribe({
            next: () => {
                this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('STUDY_AMOUNT_LOOKUP.MESSAGES.DELETED') });
                this.deleteDialogVisible = false;
                this.itemToDelete = null;
                this.load();
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDY_AMOUNT_LOOKUP.MESSAGES.DELETE_ERROR') })
        });
    }

    /** Blank means the shared default, not missing data — see the years lookup. */
    countryLabel(id: number | null | undefined): string {
        if (!id) return this.translationService.translate('STUDY_LOOKUP.ALL_ATTACHES');
        return this.countryOptions.find(c => c.value === id)?.name || String(id);
    }

    degreeLabel(id: number): string {
        return this.degreeOptions.find(d => d.value === id)?.name || String(id);
    }

    agencyLabel(id: number): string {
        return this.agencyTypeOptions.find(a => a.value === id)?.name || String(id);
    }

    currencyLabel(id: number | undefined): string {
        if (!id) return '';
        return this.currencyOptions.find(c => c.value === id)?.name || String(id);
    }
}
