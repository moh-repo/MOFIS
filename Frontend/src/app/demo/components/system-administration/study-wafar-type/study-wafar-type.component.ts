import { Table } from 'primeng/table';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MessageService, ConfirmationService } from 'primeng/api';
import { StudyWafarType } from '../../../api/study-wafar-type';
import { StudyWafarTypeService } from '../../../service/study-wafar-type.service';
import { TranslationService } from '../../../service/translation.service';

@Component({
    selector: 'app-study-wafar-type',
    templateUrl: './study-wafar-type.component.html',
    styleUrls: ['./study-wafar-type.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudyWafarTypeComponent implements OnInit {

  wafarTypes: StudyWafarType[] = [];
  loading = false;

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';

  wafarTypeForm: FormGroup;
  selectedWafarType: StudyWafarType | null = null;

  constructor(
    private service: StudyWafarTypeService,
    private fb: FormBuilder,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public translationService: TranslationService
  ) {
    this.wafarTypeForm = this.fb.group({
      id: [{ value: null, disabled: true }],
      wafarName: ['', Validators.required],
      wafarNameEn: [''],
      wafarNote1: ['']
    });
  }

  ngOnInit(): void {
    this.loadWafarTypes();
  }

  // Filters. The list is held whole, so the table narrows itself.
  filterSearch = '';

  get hasFilters(): boolean {
    return !!this.filterSearch;
  }

  clearFilters(dt: Table): void {
    this.filterSearch = '';
    dt.filterGlobal('', 'contains');
  }

  get dialogTitle(): string {
    const key = this.dialogMode === 'create'
      ? 'SYSTEM_ADMIN.STUDY_WAFAR_TYPE.ADD'
      : 'SYSTEM_ADMIN.STUDY_WAFAR_TYPE.EDIT';
    return this.translationService.translate(key);
  }

  loadWafarTypes(): void {
    this.loading = true;
    this.service.getAll().subscribe({
      next: (types) => {
        this.wafarTypes = types ?? [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_WAFAR_TYPE.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  openCreateDialog(): void {
    this.dialogMode = 'create';
    this.formDialogVisible = true;
    this.wafarTypeForm.reset({
      id: null,
      wafarName: '',
      wafarNameEn: '',
      wafarNote1: ''
    });
  }

  openEditDialog(type: StudyWafarType): void {
    this.dialogMode = 'edit';
    this.selectedWafarType = type;
    this.formDialogVisible = true;
    this.wafarTypeForm.reset({
      id: type.id ?? null,
      wafarName: type.wafarName ?? '',
      wafarNameEn: type.wafarNameEn ?? '',
      wafarNote1: type.wafarNote1 ?? ''
    });
  }

  openViewDialog(type: StudyWafarType): void {
    this.selectedWafarType = type;
    this.viewDialogVisible = true;
  }

  editFromView(): void {
    if (this.selectedWafarType) {
      this.viewDialogVisible = false;
      this.openEditDialog(this.selectedWafarType);
    }
  }

  hideFormDialog(): void {
    this.formDialogVisible = false;
    this.wafarTypeForm.reset();
  }

  saveWafarType(): void {
    if (this.wafarTypeForm.invalid) {
      this.wafarTypeForm.markAllAsTouched();
      return;
    }

    const payload = this.mapFormToPayload();
    // The id is not in the payload (the request DTO has no id), so an edit took
    // payload.id and went to PUT /undefined. It is the row the dialog opened.
    const editId = this.selectedWafarType?.id;
    if (this.dialogMode === 'edit' && editId == null) {
      return;
    }
    const request$ = this.dialogMode === 'create'
      ? this.service.create(payload as StudyWafarType)
      : this.service.update(editId!, payload as StudyWafarType);

    request$.subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate(
            this.dialogMode === 'create'
              ? 'SYSTEM_ADMIN.STUDY_WAFAR_TYPE.MESSAGES.CREATED'
              : 'SYSTEM_ADMIN.STUDY_WAFAR_TYPE.MESSAGES.UPDATED'
          )
        });
        this.hideFormDialog();
        this.loadWafarTypes();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_WAFAR_TYPE.MESSAGES.SAVE_ERROR')
        });
      }
    });
  }

  confirmDelete(type: StudyWafarType): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('SYSTEM_ADMIN.STUDY_WAFAR_TYPE.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('SYSTEM_ADMIN.STUDY_WAFAR_TYPE.DELETE_CONFIRM_MESSAGE')
        .replace('{name}', type.wafarName ?? ''),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteWafarType(type)
    });
  }

  private deleteWafarType(type: StudyWafarType): void {
    if (!type.id) {
      return;
    }

    this.service.delete(type.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_WAFAR_TYPE.MESSAGES.DELETE_SUCCESS')
        });
        this.loadWafarTypes();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_WAFAR_TYPE.MESSAGES.DELETE_ERROR')
        });
      }
    });
  }

  /**
   * What StudyWafarTypeRequest accepts, and nothing else.
   *
   * <p>It used to send `id` and `swafarMidate`, neither of which is on that DTO,
   * so both were discarded in silence - the MI-date picker on the dialog could
   * never have stored anything. The English name was never sent at all, and the
   * server applied it unconditionally, so every save cleared WAFAR_NAME_EN.
   */
  private mapFormToPayload(): Partial<StudyWafarType> {
    const raw = this.wafarTypeForm.getRawValue();
    return {
      wafarName: raw.wafarName ?? '',
      wafarNameEn: raw.wafarNameEn ?? '',
      wafarNote1: raw.wafarNote1 ?? ''
    };
  }
}
