import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Table } from 'primeng/table';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable, Subscription } from 'rxjs';
import { MessageService, ConfirmationService } from 'primeng/api';
import { MoheQuarter, MoheQuarterCreateJob, MoheQuarterCreateResult, MoheQuarterService } from '../../../service/mohe-quarter.service';
import { TranslationService } from '../../../service/translation.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { changesOnly } from '../../../service/reload-streams.util';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { UmsAuthService } from '../../../service/ums-auth.service';

@Component({
    selector: 'app-mohe-quarter',
    templateUrl: './mohe-quarter.component.html',
    styleUrls: ['./mohe-quarter.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class MoheQuarterComponent implements OnInit, OnDestroy {

  quarters: MoheQuarter[] = [];
  loading = false;
  saving = false;

  /** The quarter creation running on the server, shown as a progress bar (review Q4). */
  createJob: MoheQuarterCreateJob | null = null;
  private createJobSub?: Subscription;

  /* Filter state. Held here rather than read off the table so the Clear button
     can tell whether there is anything to clear, and so the labels can mark
     which filters are actually narrowing the list. */
  @ViewChild('dt') table?: Table;
  filterGlobal = '';
  filterYear: number | null = null;
  filterQuarterNumber: number | null = null;

  /**
   * Years offered by the Year filter. Built from the quarters actually on the
   * list (distinct QUARTER_YEAR values, newest first) rather than from the
   * academic-years lookup: the lookup lists every year the system knows about,
   * most of which have no quarter and so filtered the table down to nothing.
   * The dialog form keeps the lookup — a new quarter may well be for a year
   * that has no row yet.
   */
  filterYearOptions: { label: string; value: number }[] = [];

  formDialogVisible = false;
  viewDialogVisible = false;
  dialogMode: 'create' | 'edit' = 'create';

  quarterForm: FormGroup;
  selectedQuarter: MoheQuarter | null = null;

  quarterYearOptions:   { label: string; value: number }[] = [];
  quarterNumberOptions: { label: string; value: number }[] = [];
  countryOptions: LookupItem[] = [];
  private langSub?: any;

  constructor(
      private moheQuarterService: MoheQuarterService,
      private fb: FormBuilder,
      private messageService: MessageService,
      private confirmationService: ConfirmationService,
      public translationService: TranslationService,
      private lookupService: LookupService,
      private permissions: UmsPermissionService,
      private umsAuth: UmsAuthService
  ) {
    // quarterAmount and insertedDate are deliberately not form controls.
    // The amount is summed server-side from the finance rows on every read and
    // ignored on save; INSERTED_DATE is a database default the server never
    // accepts from the client. Both are shown read-only in the dialogs instead
    // of as inputs that silently do nothing.
    this.quarterForm = this.fb.group({
      id: [{ value: null, disabled: true }],
      id1: [null],
      // Year and number are the quarter's period: QUARTER, QUARTER_YEAR and QUARTER_NO
      // are all NOT NULL, and the server derives the YYYYQ key from these two. They
      // were optional here, so a save with either blank reached the database as a NULL.
      quarterYear: [null, Validators.required],
      quarterNumber: [null, [Validators.required, Validators.min(1), Validators.max(4)]],
      quarterName: ['', Validators.required],
      quarterPayDate: [null],
      quarterRecivedDate: [null],
      notes: [''],
      countryId: [null]
    });
  }

  ngOnInit(): void {
    this.initializeDropdownOptions();
    this.loadQuarters();
    // A creation started earlier (by this user or another) is still shown — to
    // those who may create one; the endpoint refuses everyone else (W4-02).
    if (this.canManageQuarters && this.permissions.hasPermission('MOHE_QUARTER.ADD')) {
      this.moheQuarterService.currentCreateJob().subscribe({
        next: job => { if (job) this.followCreateJob(job); },
        error: () => { /* no job to show */ }
      });
    }
    this.langSub = this.translationService.currentLanguage$.pipe(changesOnly()).subscribe(() => {
      this.initializeDropdownOptions();
    });
  }

  /**
   * FE-14: a MOHE quarter is the ministry's payment period for every attaché, so the
   * server creates and closes one only for the ministry tier (super admin, ministry
   * admin, ministry staff) and refuses a country user with 403. The Add and Close
   * actions are not offered to anyone else. The server decides; this only hides what
   * would be refused.
   */
  get canManageQuarters(): boolean {
    return this.umsAuth.isSuperAdmin() || this.umsAuth.isMinistryLevel;
  }

  ngOnDestroy(): void {
    this.langSub?.unsubscribe();
    this.createJobSub?.unsubscribe();
  }

  /** Percent done, for the progress bar; 0 until the server has counted the students. */
  get createJobPercent(): number {
    const job = this.createJob;
    if (!job?.total) return 0;
    return Math.min(100, Math.round((job.done / job.total) * 100));
  }

  /**
   * Shows a creation's progress until it ends, then reports the outcome. The job runs
   * on the server either way; leaving the page only stops the polling.
   */
  private followCreateJob(job: MoheQuarterCreateJob): void {
    this.createJob = job;
    this.createJobSub?.unsubscribe();
    this.createJobSub = this.moheQuarterService.watchCreateJob(job.jobId).subscribe({
      next: latest => {
        this.createJob = latest;
        if (latest.state === 'DONE') {
          this.createJob = null;
          this.messageService.add({
            severity: 'success',
            summary: this.translationService.translate('COMMON.SUCCESS'),
            detail: this.createSuccessDetail(latest.summary ?? {}),
            life: 10000
          });
          this.loadQuarters();
        } else if (latest.state === 'FAILED') {
          this.createJob = null;
          this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: latest.error || this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.SAVE_ERROR'),
            life: 15000
          });
        }
      },
      error: () => {
        // Polling failed (network, restart); the job itself may still be running.
        this.createJob = null;
        this.messageService.add({
          severity: 'warn',
          summary: this.translationService.translate('COMMON.WARNING'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.CREATE_PROGRESS_LOST'),
          life: 10000
        });
      }
    });
  }

  private createSuccessDetail(r: MoheQuarterCreateResult): string {
    let detail = this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.CREATE_SUCCESS');
    if (r.studentSnapshotsArchived != null) {
      detail += ` ${r.studentSnapshotsArchived} ${this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.SNAPSHOTS_STUDENTS')}, `
              + `${r.financeSnapshotsArchived} ${this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.SNAPSHOTS_FINANCE')}.`;
    }
    return detail;
  }

  initializeDropdownOptions(): void {
    this.lookupService.getAcademicYears().subscribe({
      next: opts => {
        this.quarterYearOptions = opts.map(o => ({
          label: o.label,
          value: parseInt(o.value, 10)
        })).filter(o => !isNaN(o.value));
      },
      error: () => { this.quarterYearOptions = []; }
    });
    this.lookupService.getQuarterNumbers().subscribe(opts => {
      this.quarterNumberOptions = opts;
    });
    // The attaché is a study country; the home-country list reuses its ids for other countries (live QA H6).
    this.lookupService.getStudyCountriesIn().subscribe({
      next: d => this.countryOptions = d,
      error: () => {}
    });
  }

  /**
   * Where a quarter's amount was summed from. Worth surfacing: the live table and
   * the archive do not always agree, and knowing which one a figure came from is
   * the difference between a number and an unexplained number.
   */
  amountSourceLabel(source?: 'LIVE' | 'ARCHIVE' | null): string {
    if (!source) return '';
    return this.translationService.translate(
      source === 'LIVE'
        ? 'OPERATIONS.MOHE_QUARTER.AMOUNT_SOURCE.LIVE'
        : 'OPERATIONS.MOHE_QUARTER.AMOUNT_SOURCE.ARCHIVE');
  }

  quarterNumberLabel(n?: number | null): string {
    if (n == null) return '—';
    const hit = this.quarterNumberOptions.find(o => o.value === n);
    return hit ? hit.label : String(n);
  }

  /** "2026 · Q1", or whichever half is known. Used by the dialog headers. */
  periodLabel(q?: MoheQuarter | null): string {
    if (!q) return '';
    const parts: string[] = [];
    if (q.quarterYear != null) parts.push(String(q.quarterYear));
    if (q.quarterNumber != null) parts.push(`Q${q.quarterNumber}`);
    return parts.join(' · ');
  }

  /** True once the server has found finance rows to sum for this quarter. */
  hasFinance(q?: MoheQuarter | null): boolean {
    return !!q?.quarterAmountSource;
  }

  get isEditMode(): boolean {
    return this.dialogMode === 'edit';
  }

  get dialogTitle(): string {
    const key = this.dialogMode === 'create'
      ? 'OPERATIONS.MOHE_QUARTER.ADD'
      : 'OPERATIONS.MOHE_QUARTER.EDIT';
    return this.translationService.translate(key);
  }

  /** Live preview for the form dialog's header while the user types. */
  get formPreviewName(): string {
    const name = (this.quarterForm.get('quarterName')?.value ?? '').toString().trim();
    return name || this.translationService.translate('OPERATIONS.MOHE_QUARTER.FORM.NAME');
  }

  get formPreviewPeriod(): string {
    return this.periodLabel({
      quarterYear: this.quarterForm.get('quarterYear')?.value ?? null,
      quarterNumber: this.quarterForm.get('quarterNumber')?.value ?? null
    });
  }

  applyGlobalFilter(value: string): void {
    this.filterGlobal = value;
    this.table?.filterGlobal(value, 'contains');
  }

  applyYearFilter(): void {
    this.table?.filter(this.filterYear, 'quarterYear', 'equals');
  }

  applyQuarterFilter(): void {
    this.table?.filter(this.filterQuarterNumber, 'quarterNumber', 'equals');
  }

  clearFilters(): void {
    this.filterGlobal = '';
    this.filterYear = null;
    this.filterQuarterNumber = null;
    // Each constraint is reset individually rather than through the table's
    // clear(), which also drops the sort order the user chose. A null value is
    // treated as blank and ignored when the table filters.
    this.table?.filter(null, 'quarterYear', 'equals');
    this.table?.filter(null, 'quarterNumber', 'equals');
    this.table?.filterGlobal('', 'contains');
  }

  /**
   * Distinct years present in the loaded quarters, newest first. Re-run after
   * every load: creating or deleting a quarter can add or remove a year, and a
   * year that has just vanished must not stay selected as a filter that now
   * hides everything.
   */
  private rebuildFilterYearOptions(): void {
    const years = Array.from(new Set(
      this.quarters
        .map(q => q.quarterYear)
        .filter((y): y is number => y != null && !isNaN(Number(y)))
        .map(y => Number(y))
    )).sort((a, b) => b - a);

    this.filterYearOptions = years.map(y => ({ label: String(y), value: y }));

    if (this.filterYear !== null && !years.includes(this.filterYear)) {
      this.filterYear = null;
      this.applyYearFilter();
    }
  }

  loadQuarters(): void {
    this.loading = true;
    this.moheQuarterService.getAll().subscribe({
      next: (quarters) => {
        this.quarters = quarters ?? [];
        this.rebuildFilterYearOptions();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  openCreateDialog(): void {
    this.dialogMode = 'create';
    this.selectedQuarter = null;
    this.formDialogVisible = true;
    this.quarterForm.reset({
      id: null,
      id1: null,
      quarterYear: null,
      quarterNumber: null,
      quarterName: '',
      quarterPayDate: null,
      quarterRecivedDate: null,
      notes: '',
      countryId: null
    });
    // Country is chosen at creation only; the server refuses to move a quarter
    // between countries afterwards, so the control is locked in edit mode.
    this.quarterForm.get('countryId')?.enable({ emitEvent: false });
    this.applyNextQuarterDefaults();
  }

  /**
   * Opens the dialog on the quarter that follows the latest one on record — year,
   * number and name — instead of on three empty boxes. Adding a quarter is a
   * once-a-period act that all but always moves to the next one, and typing the year
   * and picking the number by hand each time is three chances to file the roll under
   * the wrong period.
   *
   * <p>Defaults, not a lock: every field stays editable, and the server still accepts
   * a later quarter, so a skipped period can be recorded. If the call fails the dialog
   * simply opens empty, as it did before — a default is not worth an error toast.
   */
  private applyNextQuarterDefaults(): void {
    this.moheQuarterService.next().subscribe({
      next: defaults => {
        // The answer may arrive after the dialog was closed again, or after an Edit
        // was opened from behind it; either way it is no longer the right form to fill.
        if (!defaults || !this.formDialogVisible || this.dialogMode !== 'create') {
          return;
        }
        // The Arabic name whatever the reader's language: the single Name box on this
        // form is bound to QUARTER_NAME, the nationalized column, and all seven rows
        // in it are Arabic. Defaulting to the English name would leave one row reading
        // differently from every other for no reason the data can explain.
        this.quarterForm.patchValue({
          quarterYear: defaults.quarterYear ?? null,
          quarterNumber: defaults.quarterNumber ?? null,
          quarterName: defaults.quarterName ?? ''
        });
      },
      error: () => {}
    });
  }

  openEditDialog(quarter: MoheQuarter): void {
    this.dialogMode = 'edit';
    this.selectedQuarter = quarter;
    this.formDialogVisible = true;
    this.quarterForm.reset({
      id: quarter.id ?? null,
      id1: quarter.id1 ?? null,
      quarterYear: quarter.quarterYear ?? null,
      quarterNumber: quarter.quarterNumber ?? null,
      quarterName: quarter.quarterName ?? '',
      quarterPayDate: this.toDate(quarter.quarterPayDate),
      quarterRecivedDate: this.toDate(quarter.quarterRecivedDate),
      notes: quarter.notes ?? '',
      countryId: quarter.countryId ?? null
    });
    this.quarterForm.get('countryId')?.disable({ emitEvent: false });
  }

  openViewDialog(quarter: MoheQuarter): void {
    this.selectedQuarter = quarter;
    this.viewDialogVisible = true;
  }

  editFromView(): void {
    if (this.selectedQuarter) {
      this.viewDialogVisible = false;
      this.openEditDialog(this.selectedQuarter);
    }
  }

  closeFromView(): void {
    if (this.selectedQuarter && !this.selectedQuarter.quarterClosed) {
      this.viewDialogVisible = false;
      this.confirmCloseQuarter(this.selectedQuarter);
    }
  }

  hideFormDialog(): void {
    this.formDialogVisible = false;
    this.quarterForm.reset();
  }

  saveQuarter(): void {
    if (this.quarterForm.invalid) {
      this.quarterForm.markAllAsTouched();
      return;
    }

    const payload = this.mapFormToPayload();
    const request$: Observable<any> = this.dialogMode === 'create'
      ? this.moheQuarterService.create(payload)
      : this.moheQuarterService.update(payload.id!, payload);

    this.saving = true;
    request$.subscribe({
      next: (res) => {
        this.saving = false;
        if (this.dialogMode === 'create') {
          // Accepted, not finished: the progress bar takes it from here.
          this.hideFormDialog();
          this.followCreateJob(res as MoheQuarterCreateJob);
          return;
        }
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.UPDATE_SUCCESS')
        });
        this.hideFormDialog();
        this.loadQuarters();
      },
      error: () => {
        this.saving = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.SAVE_ERROR')
        });
      }
    });
  }

  confirmDelete(quarter: MoheQuarter): void {
    this.confirmationService.confirm({
      header: this.translationService.translate('OPERATIONS.MOHE_QUARTER.DELETE_CONFIRM_TITLE'),
      message: this.translationService.translate('OPERATIONS.MOHE_QUARTER.DELETE_CONFIRM_MESSAGE')
        .replace('{quarter}', `${quarter.quarterName ?? quarter.quarter ?? quarter.id}`),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.deleteQuarter(quarter)
    });
  }

  confirmCloseQuarter(quarter: MoheQuarter): void {
    if (!quarter.id || quarter.quarterClosed || !this.canManageQuarters) {
      return;
    }

    // Falls back to the period, not the row id: "close Q15" named nothing a person
    // could recognise, and the confirm dialog is the last chance to notice the wrong
    // quarter is about to be closed.
    const label = quarter.quarterName ?? String(quarter.quarter ?? quarter.id);
    this.confirmationService.confirm({
      header: this.translationService.translate('OPERATIONS.MOHE_QUARTER.CLOSE_CONFIRM_TITLE'),
      message: this.translationService.translate('OPERATIONS.MOHE_QUARTER.CLOSE_CONFIRM_MESSAGE')
        .replace('{quarter}', label),
      icon: 'pi pi-lock',
      acceptLabel: this.translationService.translate('COMMON.YES'),
      rejectLabel: this.translationService.translate('COMMON.NO'),
      acceptButtonStyleClass: 'p-button-warning',
      rejectButtonStyleClass: 'p-button-text',
      accept: () => this.runCloseQuarter(quarter)
    });
  }

  private runCloseQuarter(quarter: MoheQuarter): void {
    if (!quarter.id) {
      return;
    }

    this.moheQuarterService.close(quarter.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.CLOSE_SUCCESS')
        });
        this.loadQuarters();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.CLOSE_ERROR')
        });
      }
    });
  }

  private deleteQuarter(quarter: MoheQuarter): void {
    if (!quarter.id) {
      return;
    }

    this.moheQuarterService.delete(quarter.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.DELETE_SUCCESS')
        });
        this.loadQuarters();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('OPERATIONS.MOHE_QUARTER.MESSAGES.DELETE_ERROR')
        });
      }
    });
  }

  private mapFormToPayload(): MoheQuarter {
    const raw = this.quarterForm.getRawValue();
    return {
      id: raw.id ?? undefined,
      // quarter is not sent: the server derives the YYYYQ key from the year and the
      // number, and ignores anything a body carries. id1 is not sent either — nothing
      // on the server has ever read it.
      quarterYear: raw.quarterYear ?? undefined,
      quarterNumber: raw.quarterNumber ?? undefined,
      quarterName: raw.quarterName ?? undefined,
      // quarterAmount and insertedDate are not sent: the amount is derived from
      // the finance tables and the server ignores it on update; the inserted
      // date is a database default the server never accepts from the client.
      quarterPayDate: this.toIsoString(raw.quarterPayDate),
      quarterRecivedDate: this.toIsoString(raw.quarterRecivedDate),
      notes: raw.notes ?? undefined,
      countryId: raw.countryId ?? null
    };
  }

  countryName(id?: number | null): string {
    if (id == null) return this.translationService.translate('OPERATIONS.MOHE_QUARTER.GLOBAL');
    return this.countryOptions.find(c => c.id === id)?.name ?? String(id);
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
