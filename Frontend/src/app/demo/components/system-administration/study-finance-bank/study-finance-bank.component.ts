import { Table } from 'primeng/table';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MessageService, ConfirmationService } from 'primeng/api';
import { StudyFinanceBank } from '../../../api/study-finance-bank';
import { StudyFinanceBankService } from '../../../service/study-finance-bank.service';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { StudyCountryIn } from '../../../api/study-country-in';

@Component({
    selector: 'app-study-finance-bank',
    templateUrl: './study-finance-bank.component.html',
    styleUrls: ['./study-finance-bank.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudyFinanceBankComponent implements OnInit {

  banks: StudyFinanceBank[] = [];
  loading = false;

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';

  bankForm: FormGroup;
  selectedBank: StudyFinanceBank | null = null;

  studyCountries: StudyCountryIn[] = [];

  constructor(
    private bankService: StudyFinanceBankService,
    private lookupService: LookupService,
    private fb: FormBuilder,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public translationService: TranslationService
  ) {
    this.bankForm = this.fb.group({
      id: [{ value: null, disabled: true }],
      studyFinanceBankeName: ['', Validators.required],
      studyFinanceBankeNameEn: [''],
      studyCountryInId: [null, Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadStudyCountries();
    this.loadBanks();
  }

  loadStudyCountries(): void {
    this.lookupService.getAllStudyCountries().subscribe({
      next: (countries) => this.studyCountries = countries ?? [],
      error: () => {
        this.messageService.add({
          severity: 'warn',
          summary: this.translationService.translate('COMMON.WARNING'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK.MESSAGES.LOAD_COUNTRIES_ERROR')
        });
      }
    });
  }

  // Filters. The list is held whole, so the table narrows itself.
  filterSearch = '';
  filterCountry: number | null = null;

  get hasFilters(): boolean {
    return !!this.filterSearch || this.filterCountry !== null;
  }

  clearFilters(dt: Table): void {
    this.filterSearch = '';
    this.filterCountry = null;
    dt.filterGlobal('', 'contains');
    dt.filter(null, 'studyCountryInId', 'equals');
  }

  get dialogTitle(): string {
    const key = this.dialogMode === 'create'
      ? 'SYSTEM_ADMIN.STUDY_FINANCE_BANK.ADD'
      : 'SYSTEM_ADMIN.STUDY_FINANCE_BANK.EDIT';
    return this.translationService.translate(key);
  }

  loadBanks(): void {
    this.loading = true;
    this.bankService.getAll().subscribe({
      next: (banks) => {
        this.banks = banks ?? [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  openCreateDialog(): void {
    this.dialogMode = 'create';
    this.formDialogVisible = true;
    this.bankForm.reset({
      id: null,
      studyFinanceBankeName: '',
      studyFinanceBankeNameEn: '',
      studyCountryInId: null
    });
  }

  openEditDialog(bank: StudyFinanceBank): void {
    this.dialogMode = 'edit';
    this.selectedBank = bank;
    this.formDialogVisible = true;
    this.bankForm.reset({
      id: bank.id ?? null,
      studyFinanceBankeName: bank.studyFinanceBankeName ?? '',
      studyFinanceBankeNameEn: bank.studyFinanceBankeNameEn ?? '',
      studyCountryInId: bank.studyCountryInId ?? null
    });
  }

  openViewDialog(bank: StudyFinanceBank): void {
    this.selectedBank = bank;
    this.viewDialogVisible = true;
  }

  editFromView(): void {
    if (this.selectedBank) {
      this.viewDialogVisible = false;
      this.openEditDialog(this.selectedBank);
    }
  }

  hideFormDialog(): void {
    this.formDialogVisible = false;
    this.bankForm.reset();
  }

  saveBank(): void {
    if (this.bankForm.invalid) {
      this.bankForm.markAllAsTouched();
      return;
    }

    const payload = this.mapFormToPayload();
    const request$ = this.dialogMode === 'create'
      ? this.bankService.create(payload as StudyFinanceBank)
      : this.bankService.update(payload.id!, payload as StudyFinanceBank);

    request$.subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate(
            this.dialogMode === 'create'
              ? 'SYSTEM_ADMIN.STUDY_FINANCE_BANK.MESSAGES.CREATED'
              : 'SYSTEM_ADMIN.STUDY_FINANCE_BANK.MESSAGES.UPDATED'
          )
        });
        this.hideFormDialog();
        this.loadBanks();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK.MESSAGES.SAVE_ERROR')
        });
      }
    });
  }

  confirmDelete(bank: StudyFinanceBank): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK.DELETE_CONFIRM_MESSAGE')
        .replace('{name}', bank.studyFinanceBankeName ?? ''),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteBank(bank)
    });
  }

  private deleteBank(bank: StudyFinanceBank): void {
    if (!bank.id) {
      return;
    }

    this.bankService.delete(bank.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK.MESSAGES.DELETE_SUCCESS')
        });
        this.loadBanks();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK.MESSAGES.DELETE_ERROR')
        });
      }
    });
  }

  /**
   * The columns this dialog owns.
   *
   * <p>The English name is sent because the server copies what it is given onto
   * the row - leaving it out is how every save used to clear
   * STUDY_FINANCE_BANKE_NAME_EN. The MI date is not sent: the column is
   * insertable = false, updatable = false, so the picker that used to be here
   * could never have stored anything. The table still shows the date the server
   * sets.
   */
  private mapFormToPayload(): Partial<StudyFinanceBank> {
    const raw = this.bankForm.getRawValue();
    return {
      id: raw.id ?? undefined,
      studyFinanceBankeName: raw.studyFinanceBankeName ?? '',
      studyFinanceBankeNameEn: raw.studyFinanceBankeNameEn ?? '',
      studyCountryInId: raw.studyCountryInId ?? null
    };
  }

  getCountryName(countryId?: number | null): string | null {
    if (!countryId) {
      return null;
    }
    const country = this.studyCountries.find(c => c?.id === countryId);
    return country?.studyCountryInName ?? countryId.toString();
  }

  private toDate(value?: string | null): Date | null {
    if (!value) {
      return null;
    }
    const date = new Date(value);
    return isNaN(date.getTime()) ? null : date;
  }

  private toIsoString(value: Date | string | null): string | null {
    if (!value) {
      return null;
    }
    if (value instanceof Date) {
      return value.toISOString();
    }
    const date = new Date(value);
    return isNaN(date.getTime()) ? null : date.toISOString();
  }
}
