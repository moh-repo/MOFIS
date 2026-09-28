import { Component, OnInit } from '@angular/core';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { ReportSettingsService, ReportTypeSettingsDto } from '../../../service/report-settings.service';

@Component({
    selector: 'app-report-settings',
    templateUrl: './report-settings.component.html',
    styleUrls: ['./report-settings.component.scss'],
    standalone: false,
    // Own MessageService so the local <p-toast> renders each message once instead of
    // alongside the root toast.
    providers: [MessageService, ConfirmationService]
})
export class ReportSettingsComponent implements OnInit {

    reportTypes: ReportTypeSettingsDto[] = [];
    selectedType: ReportTypeSettingsDto | null = null;

    loading = false;
    saving = false;

    showAddDialog = false;
    newTypeCode = '';
    newTypeNameAr = '';
    newTypeNameEn = '';

    signatoryCountOptions = [0, 1, 2, 3, 4, 5];

    titleSlots = [
        { arField: 'title01Ar' as const, enField: 'title01En' as const, label: 1 },
        { arField: 'title02Ar' as const, enField: 'title02En' as const, label: 2 },
        { arField: 'title03Ar' as const, enField: 'title03En' as const, label: 3 },
        { arField: 'title04Ar' as const, enField: 'title04En' as const, label: 4 },
        { arField: 'title05Ar' as const, enField: 'title05En' as const, label: 5 },
    ];

    signatorySlots = [
        { nameArField: 'signatoryName01Ar' as const, nameEnField: 'signatoryName01En' as const, posArField: 'signatoryPos01Ar' as const, posEnField: 'signatoryPos01En' as const, label: 1 },
        { nameArField: 'signatoryName02Ar' as const, nameEnField: 'signatoryName02En' as const, posArField: 'signatoryPos02Ar' as const, posEnField: 'signatoryPos02En' as const, label: 2 },
        { nameArField: 'signatoryName03Ar' as const, nameEnField: 'signatoryName03En' as const, posArField: 'signatoryPos03Ar' as const, posEnField: 'signatoryPos03En' as const, label: 3 },
        { nameArField: 'signatoryName04Ar' as const, nameEnField: 'signatoryName04En' as const, posArField: 'signatoryPos04Ar' as const, posEnField: 'signatoryPos04En' as const, label: 4 },
        { nameArField: 'signatoryName05Ar' as const, nameEnField: 'signatoryName05En' as const, posArField: 'signatoryPos05Ar' as const, posEnField: 'signatoryPos05En' as const, label: 5 },
    ];

    constructor(
        private service: ReportSettingsService,
        private messageService: MessageService,
        private confirmationService: ConfirmationService,
        public translationService: TranslationService
    ) {}

    ngOnInit(): void {
        this.loadTypes();
    }

    loadTypes(): void {
        this.loading = true;
        this.service.getAll().subscribe({
            next: types => {
                this.reportTypes = types;
                this.loading = false;
                if (this.selectedType) {
                    const updated = types.find(t => t.id === this.selectedType!.id);
                    this.selectedType = updated || null;
                }
            },
            error: () => {
                this.loading = false;
                this.showError('COMMON.LOAD_ERROR');
            }
        });
    }

    selectType(type: ReportTypeSettingsDto): void {
        this.selectedType = { ...type };
    }

    save(): void {
        if (!this.selectedType?.id) return;
        this.saving = true;
        this.service.update(this.selectedType.id, this.selectedType).subscribe({
            next: (dto) => {
                const idx = this.reportTypes.findIndex(t => t.id === dto.id);
                if (idx >= 0) this.reportTypes[idx] = dto;
                this.selectedType = { ...dto };
                this.saving = false;
                this.showSuccess('COMMON.SAVED_SUCCESSFULLY');
            },
            error: () => {
                this.saving = false;
                this.showError('COMMON.SAVE_ERROR');
            }
        });
    }

    /** The report codes still without settings; "Add type" picks one (DEC-22, review M3). */
    catalogueCodes: string[] = [];
    catalogueLoading = false;

    openAddDialog(): void {
        this.newTypeCode = '';
        this.newTypeNameAr = '';
        this.newTypeNameEn = '';
        this.catalogueCodes = [];
        this.catalogueLoading = true;
        this.showAddDialog = true;
        this.service.catalogue().subscribe({
            next: codes => { this.catalogueCodes = codes; this.catalogueLoading = false; },
            error: () => { this.catalogueLoading = false; }
        });
    }

    createType(): void {
        if (!this.newTypeCode) return;
        const dto: ReportTypeSettingsDto = {
            reportTypeCode: this.newTypeCode,
            reportTypeNameAr: this.newTypeNameAr,
            reportTypeNameEn: this.newTypeNameEn,
            signatoryCount: 0
        };
        this.service.create(dto).subscribe({
            next: (created) => {
                this.reportTypes = [...this.reportTypes, created];
                this.showAddDialog = false;
                this.showSuccess('COMMON.CREATED_SUCCESSFULLY');
            },
            error: (err) => {
                const msg = err?.error?.message || this.t('COMMON.SAVE_ERROR');
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: msg });
            }
        });
    }

    deleteType(type: ReportTypeSettingsDto): void {
        this.confirmationService.confirm({
            message: this.t('COMMON.CONFIRM_DELETE'),
            accept: () => {
                this.service.delete(type.id!).subscribe({
                    next: () => {
                        this.reportTypes = this.reportTypes.filter(t => t.id !== type.id);
                        if (this.selectedType?.id === type.id) this.selectedType = null;
                        this.showSuccess('COMMON.DELETED_SUCCESSFULLY');
                    },
                    error: () => this.showError('COMMON.DELETE_ERROR')
                });
            }
        });
    }

    private showSuccess(key: string): void {
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t(key) });
    }

    private showError(key: string): void {
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t(key) });
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }
}
