import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Subject, Subscription, forkJoin } from 'rxjs';
import { skip, takeUntil } from 'rxjs/operators';

import { StudentFinance } from '../../../api/studentfinance';
import { StudentFinanceService } from '../../../service/studentfinance.service';
import { FinanceLookupsService, FinanceLookups, emptyFinanceLookups } from '../../../service/finance-lookups.service';
import { TranslationService } from '../../../service/translation.service';
import { CodeListService, PrimeSeverity } from '../../../service/code-list.service';

/** One label/value pair in a detail card. */
interface DetailRow {
  label: string;
  value: string | number | null;
  /** Renders as a date rather than plain text. */
  date?: boolean;
  /** Long-form text gets its own full-width block. */
  block?: boolean;
}

@Component({
    templateUrl: './finance-view.component.html',
    styleUrls: ['./finance-view.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class FinanceViewComponent implements OnInit, OnDestroy {

  loading = true;
  loadFailed = false;

  model: StudentFinance = new StudentFinance();
  lookups: FinanceLookups = emptyFinanceLookups();
  wafar: any = null;

  private languageSub?: Subscription;
  private destroy$ = new Subject<void>();
  /** The id on screen or in flight, so a slow answer for an earlier id is dropped. */
  private requestedId: number | null = null;

  constructor(
    private studentFinanceService: StudentFinanceService,
    private financeLookups: FinanceLookupsService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService
  ) { }

  ngOnInit(): void {
    /*
     * Subscribed rather than read from the snapshot: the router reuses this
     * component when only :id changes, and the snapshot kept showing the first
     * record (live QA 23 Sep 2026, M5). Each id clears the last one before loading.
     */
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const id = Number(params.get('id'));
      this.model = new StudentFinance();
      this.wafar = null;
      this.loadFailed = false;
      this.load(id);
    });

    this.languageSub = this.translationService.currentLanguage$
      .pipe(skip(1))
      .subscribe(() => {
        this.financeLookups.loadAll().subscribe(lookups => (this.lookups = lookups));
      });
  }

  ngOnDestroy(): void {
    this.languageSub?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Lookups and the record load together so that every id on the page has a
   * name to resolve to by first paint — this view is deep-linkable, so nothing
   * can be assumed to be already cached from the list.
   */
  private load(id: number): void {
    this.loading = true;
    this.requestedId = id;
    forkJoin({
      record: this.studentFinanceService.getById(id),
      lookups: this.financeLookups.loadAll()
    }).subscribe({
      next: ({ record, lookups }) => {
        if (this.requestedId !== id) {
          return;
        }
        this.model = { ...new StudentFinance(), ...record };
        this.lookups = lookups;
        this.loading = false;
        this.loadWafar(id);
      },
      error: (error: HttpErrorResponse) => {
        if (this.requestedId !== id) {
          return;
        }
        // A record that does not exist is the app's not-found page, not a
        // blank view under an error toast (live QA 23 Sep 2026, L6).
        if (error?.status === 404) {
          this.router.navigate(['/notfound']);
          return;
        }
        console.error('Error loading finance record:', error);
        this.loading = false;
        this.loadFailed = true;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('FINANCE.MESSAGES.LOAD_ERROR')
        });
      }
    });
  }

  private loadWafar(id: number): void {
    this.studentFinanceService.getWafar(id).subscribe({
      next: (wafar) => {
        if (this.requestedId === id) {
          this.wafar = wafar || null;
        }
      },
      error: () => {
        if (this.requestedId === id) {
          this.wafar = null;
        }
      }
    });
  }

  // ── Resolved values ───────────────────────────────────────────────────────

  private t(key: string): string {
    return this.translationService.translate(key);
  }

  private name(list: any[], id: number | null | undefined, field: string): string {
    return this.financeLookups.resolveName(list, id, field);
  }

  get statusName(): string {
    return this.name(this.lookups.finReceivedStatuses, this.model.studyFinRecievedStatus, 'studyFinReceivedStatusName');
  }

  /**
   * UI_SEVERITY as STUDY_FIN_RECEIVED_STATUS states it; when the row has none,
   * the colour follows the code — money that moved is success, NOT_SPECIFIED is
   * neutral, the rest warn. This used to paint any known id green, which would
   * have turned every "Not Specified" (99) row green (live QA 23 Sep 2026, H2).
   */
  get statusSeverity(): PrimeSeverity {
    const id = this.model.studyFinRecievedStatus;
    if (id === null || id === undefined) {
      return 'secondary';
    }
    const row = (this.lookups.finReceivedStatuses || []).find(s => Number(s.id) === Number(id));
    if (!row) {
      return 'secondary';
    }
    if (row.uiSeverity) {
      return CodeListService.toSeverity(row.uiSeverity);
    }
    switch ((row.code ?? '').toUpperCase()) {
      case 'RECEIVED':
      case 'DISBURSED':
        return 'success';
      case 'NOT_SPECIFIED':
        return 'secondary';
      default:
        return 'warn';
    }
  }

  get currencyName(): string {
    return this.financeLookups.resolveCurrencyName(this.lookups.currencies, this.model.studyCurrencyInId);
  }

  get hasWafar(): boolean {
    return (this.model.wafarAmount ?? 0) > 0 || !!this.wafar;
  }

  get wafarAmount(): number {
    return this.model.wafarAmount ?? this.wafar?.studentFinRecievedAmount ?? 0;
  }

  /**
   * Deliberately computed from the wafar this page is showing rather than read from the
   * server's NET_AMOUNT column: the wafarAmount getter above falls back to the separate
   * wafar record, which NET_AMOUNT cannot see. On a legacy row whose wafar was written
   * only to STUDENT_FINANCE_WAFAR, the column would report the full amount and the three
   * tiles would visibly fail to add up.
   */
  get netAmount(): number {
    return (this.model.studentFinRecievedAmount ?? 0) - this.wafarAmount;
  }

  /** First letter of the student's name, for the hero avatar. */
  get avatarInitial(): string {
    const name = (this.model.studentFinName || '').trim();
    return name ? name.charAt(0).toUpperCase() : '#';
  }

  // ── Detail cards ──────────────────────────────────────────────────────────

  get financialRows(): DetailRow[] {
    return [
      { label: this.t('FINANCE.CURRENCY'), value: this.currencyName || null },
      { label: this.t('FINANCE.CHECK_DATE'), value: this.model.studyCheckDate as any, date: true },
      { label: this.t('FINANCE.RECEIVED_YEAR'), value: this.model.studentFinRecievedYear || null },
      { label: this.t('FINANCE.RECEIVED_MONTH'), value: this.model.studentFinRecievedMonth || null },
      { label: this.t('FINANCE.YEAR_MONTH'), value: this.model.studentFinRecievedYearMonth || null }
    ];
  }

  get checkRows(): DetailRow[] {
    return [
      { label: this.t('FINANCE.CHECK_NUMBER'), value: this.model.studyCheckId },
      { label: this.t('FINANCE.CHECK_FORM_NUMBER'), value: this.model.studyCheckFormNumber },
      { label: this.t('FINANCE.CHECK_DETAILS'), value: this.model.studyCheckDetails, block: true }
    ];
  }

  get locationRows(): DetailRow[] {
    return [
      {
        label: this.t('FINANCE.STUDY_COUNTRY'),
        value: this.name(this.lookups.studyCountries, this.model.studyCountryInId, 'studyCountryInName')
      },
      {
        label: this.t('FINANCE.STUDY_GOVERNMENT'),
        value: this.name(this.lookups.studyGovernments, this.model.studyGovInId, 'studyGovInName')
      }
    ];
  }

  get bankRows(): DetailRow[] {
    return [
      {
        label: this.t('FINANCE.FINANCE_BANK'),
        value: this.name(this.lookups.studyFinanceBanks, this.model.studyFinanceBankeId, 'studyFinanceBankeName')
      },
      {
        label: this.t('FINANCE.BANK_BRANCH'),
        value: this.name(this.lookups.studyFinanceBankBranches, this.model.studyFinanceBankBranchId, 'studyFinanceBankBranchName')
      },
      {
        label: this.t('FINANCE.FINANCE_DOF'),
        value: this.name(this.lookups.studyFinanceDofs, this.model.studyFinanceDofId, 'studyFinanceDofName')
      },
      { label: this.t('FINANCE.FINANCE_MI_DATE'), value: this.model.studyFinanceMidate as any, date: true }
    ];
  }

  get recipientRows(): DetailRow[] {
    return [
      {
        label: this.t('FINANCE.RECEIVED_WHOM_STATUS'),
        value: this.name(this.lookups.receivedWhomStatuses, this.model.studyReceivedWhomStatusId, 'studyReceivedWhomStatusName')
      },
      {
        label: this.t('FINANCE.RECEIVED_WHOM'),
        value: this.name(this.lookups.finReceivedWhoms, this.model.studyReceivedWhomId, 'finReceivedName')
      }
    ];
  }

  get accountRows(): DetailRow[] {
    return [
      {
        label: this.t('FINANCE.STUDENT_BANK'),
        value: this.name(this.lookups.studyFinanceBanks, this.model.studentBankId, 'studyFinanceBankeName')
      },
      { label: this.t('FINANCE.ACCOUNT_NUMBER'), value: this.model.studentAccountNumber },
      { label: this.t('FINANCE.ACCOUNT_ENGLISH_NAME'), value: this.model.studentAccountEnglishName },
      { label: this.t('FINANCE.ACCOUNT_END_DATE'), value: this.model.studentAccountEndDate }
    ];
  }

  get wafarRows(): DetailRow[] {
    return [
      {
        label: this.t('FINANCE.WAFAR_TYPE'),
        value: this.name(this.lookups.wafarTypes, this.model.wafarTypeId, 'wafarName')
      },
      { label: this.t('FINANCE.NET_AMOUNT'), value: this.netAmount },
      { label: this.t('FINANCE.WAFAR_NOTES'), value: this.wafar?.studyNote1 ?? null, block: true }
    ];
  }

  /** Placeholder for any row the record leaves empty. */
  display(value: string | number | null | undefined): string {
    if (value === null || value === undefined || String(value).trim() === '') {
      return this.t('COMMON.NOT_SPECIFIED');
    }
    return String(value);
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  goBack(): void {
    this.router.navigate(['/finance']);
  }

  goToEdit(): void {
    this.router.navigate(['/finance/edit', this.model.id]);
  }
}
