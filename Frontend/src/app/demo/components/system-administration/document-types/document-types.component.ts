import { Table } from 'primeng/table';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { DocumentTypeService } from '../../../service/document-type.service';
import { DocumentType } from '../../../api/document-type';
import { TranslationService } from '../../../service/translation.service';

@Component({
    selector: 'app-document-types',
    templateUrl: './document-types.component.html',
    styleUrls: ['./document-types.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class DocumentTypesComponent implements OnInit {

  documentTypes: DocumentType[] = [];
  loading = false;

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';

  documentTypeForm: FormGroup;
  selectedDocumentType: DocumentType | null = null;

  constructor(
    private documentTypeService: DocumentTypeService,
    private fb: FormBuilder,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public translationService: TranslationService
  ) {
    this.documentTypeForm = this.fb.group({
      documentTypeId: [{ value: null, disabled: true }],
      documentTypeName: ['', Validators.required],
      documentTypeDescription: [''],
      sortOrder: [0],
      isActive: [true]
    });
  }

  ngOnInit(): void {
    this.loadDocumentTypes();
  }

  // Filters. The list is held whole, so the table narrows itself.
  filterSearch = '';
  filterActive: boolean | null = null;
  private activeOptionsFor: { lang: string; options: { label: string; value: boolean | null }[] } | null = null;

  /** Rebuilt when the language changes, and only then, so the select keeps one array between changes. */
  get activeOptions(): { label: string; value: boolean | null }[] {
    const lang = this.translationService.getCurrentLanguage();
    if (this.activeOptionsFor?.lang !== lang) {
      this.activeOptionsFor = {
        lang,
        options: [
          { label: this.translationService.translate('COMMON.ALL'), value: null },
          { label: this.translationService.translate('COMMON.ACTIVE'), value: true },
          { label: this.translationService.translate('COMMON.INACTIVE'), value: false }
        ]
      };
    }
    return this.activeOptionsFor.options;
  }

  get hasFilters(): boolean {
    return !!this.filterSearch || this.filterActive !== null;
  }

  clearFilters(dt: Table): void {
    this.filterSearch = '';
    this.filterActive = null;
    dt.filterGlobal('', 'contains');
    dt.filter(null, 'isActive', 'equals');
  }

  get dialogTitle(): string {
    const key = this.dialogMode === 'create'
      ? 'SYSTEM_ADMIN.DOCUMENT_TYPES.ADD'
      : 'SYSTEM_ADMIN.DOCUMENT_TYPES.EDIT';
    return this.translationService.translate(key);
  }

  loadDocumentTypes(): void {
    this.loading = true;
    this.documentTypeService.getAllDocumentTypes().subscribe({
      next: (types) => {
        this.documentTypes = types ?? [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.DOCUMENT_TYPES.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  openCreateDialog(): void {
    this.dialogMode = 'create';
    this.formDialogVisible = true;
    this.documentTypeForm.reset({
      documentTypeId: null,
      documentTypeName: '',
      documentTypeDescription: '',
      sortOrder: 0,
      isActive: true
    });
  }

  openEditDialog(documentType: DocumentType): void {
    this.dialogMode = 'edit';
    this.selectedDocumentType = documentType;
    this.formDialogVisible = true;
    this.documentTypeForm.reset({
      documentTypeId: documentType.documentTypeId ?? null,
      documentTypeName: documentType.documentTypeName ?? '',
      documentTypeDescription: documentType.documentTypeDescription ?? '',
      sortOrder: documentType.sortOrder ?? 0,
      isActive: documentType.isActive ?? true
    });
  }

  openViewDialog(documentType: DocumentType): void {
    this.selectedDocumentType = documentType;
    this.viewDialogVisible = true;
  }

  editFromView(): void {
    if (this.selectedDocumentType) {
      this.viewDialogVisible = false;
      this.openEditDialog(this.selectedDocumentType);
    }
  }

  hideFormDialog(): void {
    this.formDialogVisible = false;
    this.documentTypeForm.reset();
  }

  saveDocumentType(): void {
    if (this.documentTypeForm.invalid) {
      this.documentTypeForm.markAllAsTouched();
      return;
    }

    const payload = this.mapFormToPayload();
    const request$ = this.dialogMode === 'create'
      ? this.documentTypeService.createDocumentType(payload as DocumentType)
      : this.documentTypeService.updateDocumentType(payload.documentTypeId!, payload as DocumentType);

    request$.subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate(
            this.dialogMode === 'create'
              ? 'SYSTEM_ADMIN.DOCUMENT_TYPES.MESSAGES.CREATED'
              : 'SYSTEM_ADMIN.DOCUMENT_TYPES.MESSAGES.UPDATED'
          )
        });
        this.hideFormDialog();
        this.loadDocumentTypes();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.DOCUMENT_TYPES.MESSAGES.SAVE_ERROR')
        });
      }
    });
  }

  confirmDelete(documentType: DocumentType): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('SYSTEM_ADMIN.DOCUMENT_TYPES.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('SYSTEM_ADMIN.DOCUMENT_TYPES.DELETE_CONFIRM_MESSAGE')
        .replace('{name}', documentType.documentTypeName ?? ''),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteDocumentType(documentType)
    });
  }

  private deleteDocumentType(documentType: DocumentType): void {
    const id = documentType.documentTypeId;
    if (!id) {
      return;
    }
    this.documentTypeService.deleteDocumentType(id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('SYSTEM_ADMIN.DOCUMENT_TYPES.MESSAGES.DELETE_SUCCESS')
        });
        this.loadDocumentTypes();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.DOCUMENT_TYPES.MESSAGES.DELETE_ERROR')
        });
      }
    });
  }

  private mapFormToPayload(): Partial<DocumentType> {
    const raw = this.documentTypeForm.getRawValue();
    return {
      documentTypeId: raw.documentTypeId ?? undefined,
      documentTypeName: raw.documentTypeName ?? '',
      documentTypeDescription: raw.documentTypeDescription ?? '',
      sortOrder: raw.sortOrder ?? 0,
      isActive: raw.isActive ?? true
    };
  }
}
