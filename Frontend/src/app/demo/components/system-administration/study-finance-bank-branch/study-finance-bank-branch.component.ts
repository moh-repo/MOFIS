import { Table } from 'primeng/table';
import { Component, DestroyRef, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { skip } from 'rxjs';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MessageService, ConfirmationService } from 'primeng/api';
import { StudyFinanceBankBranch } from '../../../api/study-finance-bank-branch';
import { StudyFinanceBankBranchService } from '../../../service/study-finance-bank-branch.service';
import { StudyFinanceBankService } from '../../../service/study-finance-bank.service';
import { StudyFinanceBank } from '../../../api/study-finance-bank';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { StudyCountryIn } from '../../../api/study-country-in';
import { StudyGovIn } from '../../../api/study-gov-in';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { extractApiErrorMessage } from '../../../service/api-error.util';

/** A bank as the lists name it, with the country it belongs to so the dialog can narrow to one. */
interface BankOption { id: number; label: string; countryId: number | null; }

/** A branch as the table lists it: its name in the current language is what the Name column sorts on. */
type BranchRow = StudyFinanceBankBranch & { displayName: string };

@Component({
    selector: 'app-study-finance-bank-branch',
    templateUrl: './study-finance-bank-branch.component.html',
    styleUrls: ['./study-finance-bank-branch.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudyFinanceBankBranchComponent implements OnInit {

  branches: BranchRow[] = [];
  banks: StudyFinanceBank[] = [];
  /** The banks as the table and both bank lists name them: in the current language. */
  bankOptions: BankOption[] = [];
  /** The dialog's banks: those of the chosen country, or of every country this account may write in. */
  formBankOptions: BankOption[] = [];
  loading = false;

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';

  branchForm: FormGroup;
  selectedBranch: StudyFinanceBankBranch | null = null;

  studyCountries: StudyCountryIn[] = [];
  /** The dialog's countries: those of {@link studyCountries} this account may write a branch in. */
  formCountries: StudyCountryIn[] = [];
  studyGovernments: StudyGovIn[] = [];
  studyGovernmentsAll: StudyGovIn[] = [];

  constructor(
    private branchService: StudyFinanceBankBranchService,
    private bankService: StudyFinanceBankService,
    private lookupService: LookupService,
    private fb: FormBuilder,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    public translationService: TranslationService,
    private umsAuth: UmsAuthService,
    private permissionService: UmsPermissionService,
    private destroyRef: DestroyRef
  ) {
    this.branchForm = this.fb.group({
      id: [{ value: null, disabled: true }],
      studyFinanceBankBranchName: ['', Validators.required],
      studyFinanceBankBranchNameEn: [''],
      studyFinanceBankeId: [null, Validators.required],
      studyCountryInId: [null, Validators.required],
      studyGovInId: [null]
    });
  }

  ngOnInit(): void {
    this.loadBanks();
    this.loadAllStudyGovernments();
    this.loadStudyCountries();
    this.loadBranches();
    // The switch happens in place, without a reload. Banks and branches carry
    // both names, so they are only relabelled; LookupService names countries and
    // governments in the language current when they are fetched, so those are
    // fetched again. The first emission is the language the loads above used.
    this.translationService.currentLanguage$
      .pipe(skip(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.buildBankOptions();
        this.branches = this.branches.map(b => this.toRow(b));
        this.loadStudyCountries();
        this.loadAllStudyGovernments();
      });
  }

  /*
   * R5-07: who may add, change and delete a branch is the server's rule, not a
   * permission code, and the screen now draws only what that rule accepts; the
   * server refuses regardless. POST, PUT and DELETE on /studyFinanceBankBranch
   * pass two checks:
   *
   *  1. COUNTRY_CONFIG_WRITE: SETTINGS.EDIT, or the COUNTRY_ADMIN tier. Country
   *     admins hold no SETTINGS.EDIT, which is why they saw only View.
   *  2. CountryOwnedConfigPolicy (S-06): an attaché's branch is written by that
   *     attaché's own accounts; ministry-level accounts (super admin, ministry
   *     admin, ministry staff) only read it, whatever codes they hold. A branch
   *     with no country is the ministry's alone. It is asked about the stored
   *     country on edit and delete, and about the country the row is saved with
   *     on create and edit (its own, else its bank's), which is never empty.
   *
   * Writes carry no X-Country-Id, so "own countries" are the account's
   * assignments, not the topbar's selection.
   */

  /** Rule 1. A session from before R-12 has no tier; the flags stand in, as on the server. */
  private get passesWriteGate(): boolean {
    const tier = this.umsAuth.adminTier;
    const countryAdmin = tier ? tier === 'COUNTRY_ADMIN' : this.umsAuth.isAdmin() && !this.umsAuth.isMinistryLevel;
    return countryAdmin || this.permissionService.hasPermission('SETTINGS.EDIT');
  }

  /** The countries rule 2 lets this account write a branch in. */
  private get writableCountryIds(): number[] {
    if (this.umsAuth.isMinistryLevel) {
      return [];
    }
    const ids = this.umsAuth.countryIds;
    return Array.isArray(ids) ? ids : [];
  }

  /** A new branch always lands in a country, so the ministry's no-country case never applies. */
  get canAddBranch(): boolean {
    return this.passesWriteGate && this.writableCountryIds.length > 0;
  }

  /**
   * Only a branch in one of this account's countries. A branch with no country
   * passes the policy for the ministry on its stored country, but the save gives
   * it its bank's country, which the ministry may not write: nobody can finish it.
   */
  canEditBranch(branch: StudyFinanceBankBranch | null | undefined): boolean {
    return !!branch && this.passesWriteGate && this.writableCountryIds.includes(branch.studyCountryInId);
  }

  /** Delete asks about the stored country only, so a branch with no country is the ministry's to remove. */
  canDeleteBranch(branch: StudyFinanceBankBranch | null | undefined): boolean {
    if (!branch || !this.passesWriteGate) {
      return false;
    }
    return branch.studyCountryInId == null
      ? this.umsAuth.isMinistryLevel
      : this.writableCountryIds.includes(branch.studyCountryInId);
  }

  /** The form's country list: a branch can only be saved into a country this account may write. */
  private refreshFormCountries(): void {
    const writable = this.writableCountryIds;
    this.formCountries = this.studyCountries.filter(c => writable.includes(c.id));
  }

  // Filters. The list is held whole, so the table narrows itself.
  filterSearch = '';
  filterBank: number | null = null;
  filterCountry: number | null = null;

  get hasFilters(): boolean {
    return !!this.filterSearch || this.filterBank !== null || this.filterCountry !== null;
  }

  clearFilters(dt: Table): void {
    this.filterSearch = '';
    this.filterBank = null;
    this.filterCountry = null;
    dt.filterGlobal('', 'contains');
    dt.filter(null, 'studyFinanceBankeId', 'equals');
    dt.filter(null, 'studyCountryInId', 'equals');
  }

  get dialogTitle(): string {
    const key = this.dialogMode === 'create'
      ? 'SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.ADD'
      : 'SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.EDIT';
    return this.translationService.translate(key);
  }

  loadBanks(): void {
    this.bankService.getAll().subscribe({
      next: (banks) => {
        this.banks = banks ?? [];
        this.buildBankOptions();
      },
      error: () => {
        this.messageService.add({
          severity: 'warn',
          summary: this.translationService.translate('COMMON.WARNING'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.LOAD_BANKS_ERROR')
        });
      }
    });
  }

  loadBranches(): void {
    this.loading = true;
    this.branchService.getAll().subscribe({
      next: (branches) => {
        this.branches = (branches ?? []).map(b => this.toRow(b));
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  loadStudyCountries(): void {
    this.lookupService.getAllStudyCountries().subscribe({
      next: (countries) => {
        this.studyCountries = countries ?? [];
        this.refreshFormCountries();
      },
      error: () => {
        this.messageService.add({
          severity: 'warn',
          summary: this.translationService.translate('COMMON.WARNING'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.LOAD_COUNTRIES_ERROR')
        });
      }
    });
  }

  loadAllStudyGovernments(): void {
    this.lookupService.getAllStudyGovernments().subscribe({
      next: (governments) => {
        this.studyGovernmentsAll = governments ?? [];
        // Reloaded on a language switch: an open dialog's list follows.
        const countryId = this.branchForm.get('studyCountryInId')?.value;
        if (countryId) {
          this.studyGovernments = this.studyGovernmentsAll.filter(g => g.studyCountryInId === countryId);
        }
      },
      error: () => {
        this.messageService.add({
          severity: 'warn',
          summary: this.translationService.translate('COMMON.WARNING'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.LOAD_GOVERNMENTS_ERROR')
        });
      }
    });
  }

  loadStudyGovernments(countryId: number | null | undefined): void {
    if (!countryId) {
      this.studyGovernments = [];
      this.branchForm.get('studyGovInId')?.setValue(null);
      return;
    }

    if (this.studyGovernmentsAll.length) {
      this.studyGovernments = this.studyGovernmentsAll.filter(g => g.studyCountryInId === countryId);
      return;
    }

    this.lookupService.getStudyGovernmentsByCountry(countryId).subscribe({
      next: (governments) => this.studyGovernments = governments ?? [],
      error: () => {
        this.messageService.add({
          severity: 'warn',
          summary: this.translationService.translate('COMMON.WARNING'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.LOAD_GOVERNMENTS_ERROR')
        });
      }
    });
  }

  openCreateDialog(): void {
    this.dialogMode = 'create';
    this.refreshFormCountries();
    this.formDialogVisible = true;
    this.branchForm.reset({
      id: null,
      studyFinanceBankBranchName: '',
      studyFinanceBankBranchNameEn: '',
      studyFinanceBankeId: null,
      studyCountryInId: null,
      studyGovInId: null
    });
    this.refreshFormBanks();
    this.studyGovernments = [];
  }

  openEditDialog(branch: StudyFinanceBankBranch): void {
    this.dialogMode = 'edit';
    this.selectedBranch = branch;
    this.refreshFormCountries();
    this.formDialogVisible = true;
    this.branchForm.reset({
      id: branch.id ?? null,
      studyFinanceBankBranchName: branch.studyFinanceBankBranchName ?? '',
      studyFinanceBankBranchNameEn: branch.studyFinanceBankBranchNameEn ?? '',
      studyFinanceBankeId: branch.studyFinanceBankeId ?? null,
      studyCountryInId: branch.studyCountryInId ?? null,
      studyGovInId: branch.studyGovInId ?? null
    });
    this.refreshFormBanks();
    this.loadStudyGovernments(branch.studyCountryInId ?? null);
  }

  openViewDialog(branch: StudyFinanceBankBranch): void {
    this.selectedBranch = branch;
    this.viewDialogVisible = true;
  }

  editFromView(): void {
    if (this.selectedBranch) {
      this.viewDialogVisible = false;
      this.openEditDialog(this.selectedBranch);
    }
  }

  hideFormDialog(): void {
    this.formDialogVisible = false;
    this.branchForm.reset();
  }

  saveBranch(): void {
    if (this.branchForm.invalid) {
      this.branchForm.markAllAsTouched();
      return;
    }

    const payload = this.mapFormToPayload();
    const request$ = this.dialogMode === 'create'
      ? this.branchService.create(payload as StudyFinanceBankBranch)
      : this.branchService.update(payload.id!, payload as StudyFinanceBankBranch);

    request$.subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate(
            this.dialogMode === 'create'
              ? 'SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.CREATED'
              : 'SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.UPDATED'
          )
        });
        this.hideFormDialog();
        this.loadBranches();
      },
      error: err => this.showWriteError(err, 'SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.SAVE_ERROR')
    });
  }

  confirmDelete(branch: StudyFinanceBankBranch): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.DELETE_CONFIRM_MESSAGE')
        .replace('{name}', this.branchName(branch)),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteBranch(branch)
    });
  }

  private deleteBranch(branch: StudyFinanceBankBranch): void {
    if (!branch.id) {
      return;
    }

    this.branchService.delete(branch.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.DELETE_SUCCESS')
        });
        this.loadBranches();
      },
      error: err => this.showWriteError(err, 'SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.DELETE_ERROR')
    });
  }

  /**
   * One toast for a refused save or delete, saying why. The write calls are
   * silent, so the interceptor's generic toast no longer stacks on this one.
   *
   *  - 403: the server's reason is the access policy's, which it sends in English
   *    only, so the page gives it in the reader's language;
   *  - a refusal the server words for the reader (400, 404, 409, 422: a duplicate
   *    name, a branch still in use, a bank of another country): its sentence,
   *    already in the reader's language;
   *  - anything else (offline, a gateway page, a server fault): the page's own words.
   */
  private showWriteError(err: unknown, fallbackKey: string): void {
    const t = (key: string) => this.translationService.translate(key);
    const response = err instanceof HttpErrorResponse ? err : null;
    const status = response?.status ?? 0;
    let reason: string | null = null;
    if (status === 403) {
      reason = t('SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES.FORBIDDEN');
    } else if ([400, 404, 409, 422].includes(status) && response?.error && typeof response.error === 'object') {
      // Only a structured body: a text one is some proxy's page, not a sentence for the reader.
      reason = extractApiErrorMessage(response);
    }
    this.messageService.add({
      severity: 'error',
      summary: t(reason ? fallbackKey : 'COMMON.ERROR'),
      detail: reason ?? t(fallbackKey),
      life: 8000
    });
  }

  /** The columns this dialog owns; see study-finance-bank for why the MI date is not one. */
  private mapFormToPayload(): Partial<StudyFinanceBankBranch> {
    const raw = this.branchForm.getRawValue();
    return {
      id: raw.id ?? undefined,
      studyFinanceBankBranchName: raw.studyFinanceBankBranchName ?? '',
      studyFinanceBankBranchNameEn: raw.studyFinanceBankBranchNameEn ?? '',
      studyFinanceBankeId: raw.studyFinanceBankeId ?? null,
      studyCountryInId: raw.studyCountryInId ?? null,
      studyGovInId: raw.studyGovInId ?? null
    };
  }

  onCountryChange(_event?: unknown): void {
    const countryId = this.branchForm.get('studyCountryInId')?.value ?? null;
    this.loadStudyGovernments(countryId);
    this.refreshFormBanks();
    // A bank of the country just left would be refused (400): drop it.
    const bank = this.branchForm.get('studyFinanceBankeId');
    if (bank?.value != null && !this.formBankOptions.some(b => b.id === bank.value)) {
      bank.setValue(null);
    }
  }

  /** A bank chosen before the country fills the country in, as the server would take it from the bank. */
  onBankChange(_event?: unknown): void {
    const country = this.branchForm.get('studyCountryInId');
    const bankId = this.branchForm.get('studyFinanceBankeId')?.value;
    const bankCountry = this.bankOptions.find(b => b.id === bankId)?.countryId ?? null;
    if (country && country.value == null && bankCountry != null) {
      country.setValue(bankCountry);
      this.onCountryChange();
    }
  }

  /**
   * The dialog's bank list. A branch must be in its bank's country, so once a
   * country is chosen only its banks are offered; before that, the banks of every
   * country this account may write a branch in.
   */
  private refreshFormBanks(): void {
    const countryId = this.branchForm.get('studyCountryInId')?.value ?? null;
    const writable = this.writableCountryIds;
    this.formBankOptions = this.bankOptions.filter(b => countryId != null
      ? b.countryId === countryId
      : b.countryId != null && writable.includes(b.countryId));
  }

  getBankName(bankId?: number | null): string | null {
    if (!bankId) {
      return null;
    }
    return this.bankOptions.find(b => b.id === bankId)?.label ?? bankId.toString();
  }

  /**
   * R5-07: the banks come from the admin endpoint with both names, and the table
   * and both bank lists read the Arabic one in either language. Each language now
   * gets its own name, falling back to the other where it is empty.
   */
  private buildBankOptions(): void {
    this.bankOptions = this.banks.filter(b => !!b).map(bank => ({
      id: bank.id,
      label: this.localName(bank.studyFinanceBankeName, bank.studyFinanceBankeNameEn) || String(bank.id),
      countryId: bank.studyCountryInId ?? null
    }));
    this.refreshFormBanks();
  }

  /** A branch's name in the current language; the table, the view dialog and the delete prompt use it. */
  branchName(branch: StudyFinanceBankBranch): string {
    return this.localName(branch.studyFinanceBankBranchName, branch.studyFinanceBankBranchNameEn) ?? '';
  }

  private toRow(branch: StudyFinanceBankBranch): BranchRow {
    return { ...branch, displayName: this.branchName(branch) };
  }

  /** The name for the current language, else the other one: a row may carry only one. */
  private localName(ar?: string | null, en?: string | null): string | null {
    const a = ar?.trim();
    const e = en?.trim();
    return (this.translationService.getCurrentLanguage() === 'en' ? e || a : a || e) || null;
  }

  getCountryName(countryId?: number | null): string | null {
    if (!countryId) {
      return null;
    }
    const country = this.studyCountries.find(c => c?.id === countryId);
    return country?.studyCountryInName ?? countryId.toString();
  }

  getGovernmentName(governmentId?: number | null): string | null {
    if (!governmentId) {
      return null;
    }
    const gov = this.studyGovernmentsAll.find(g => g?.id === governmentId) || this.studyGovernments.find(g => g?.id === governmentId);
    return gov?.studyGovInName ?? governmentId.toString();
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
