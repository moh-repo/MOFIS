import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { PaymentAuth } from '../../../../api/payment-auth';
import { PaymentAuthService, PagedPaymentAuth } from '../../../../service/payment-auth.service';
import { TranslationService } from '../../../../service/translation.service';
import { LookupService } from '../../../../service/lookup.service';
import { ReportCountryScopeService } from '../../../../service/report-country-scope.service';
import { ReportPdfService, ReportPdfQuery } from '../../../../service/report-pdf.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { changesOnly } from '../../../../service/reload-streams.util';
import { pageOf } from '../../../../service/table-paging.util';
import { uiReportLang } from '../../../../service/report-lang.util';

@Component({
    selector: 'app-payment-auth-report',
    templateUrl: './payment-auth-report.component.html',
    styleUrls: ['./payment-auth-report.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class PaymentAuthReportComponent implements OnInit, OnDestroy {

  // List state
  records: PaymentAuth[] = [];
  totalElements = 0;
  loadingList = false;
  searchTerm = '';
  selectedStatusFilter: string | null = null;
  currentPage = 0;
  pageSize = 10;
  /** Bound to the table, so a search that reloads page one moves the paginator there too (QA B8, N9). */
  first = 0;

  // Selected record for report
  selectedRecord: PaymentAuth | null = null;
  loadingRecord = false;

  statusOptions: { label: string; value: string | null }[] = [];
  /** code → localized name, from the status lookup. */
  private statusLabels: Record<string, string> = {};
  selectedCountryIds: number[] = [];
  allowedCountries: { label: string; value: number }[] = [];
  private readonly destroy$ = new Subject<void>();

  // Jasper PDF viewer — the tabular list of authorizations
  reportLoading = false;
  pdfApiPath = '/api/v1/paymentAuth/export-report';
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;
  selectedLang = uiReportLang(this.translationService);

  // Jasper PDF viewer — the statutory voucher for the selected record
  formPdfApiPath: string | null = null;
  formPdfQuery: ReportPdfQuery | null = null;
  formLoadTrigger = 0;

  constructor(
    private http: HttpClient,
    private service: PaymentAuthService,
    private messageService: MessageService,
    public translationService: TranslationService,
    private lookupService: LookupService,
    private reportCountryScope: ReportCountryScopeService,
    private reportPdfService: ReportPdfService
  ) {}

  get hasMultiCountry(): boolean {
    return this.reportCountryScope.hasMultiCountryAccess();
  }

  loadCountries(): void {
    this.http.get<any[]>(`${environment.apiUrl}/mohe/api/lookup-tables/study-country-in`)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          const isAr = this.translationService.getCurrentLanguage() === 'ar';
          const all = (Array.isArray(data) ? data : ((data as any)?.content || [])).map(c => ({
            label: (isAr ? c.studyCountryInName : c.studyCountryInNameEn) || c.studyCountryInName || String(c.id),
            value: c.id as number
          }));
          if (!this.reportCountryScope.isMinistryUser && this.reportCountryScope.allowedCountryIds.length > 0) {
            const allowed = new Set(this.reportCountryScope.allowedCountryIds);
            this.allowedCountries = all.filter(c => allowed.has(c.value));
          } else {
            this.allowedCountries = all;
          }
        },
        error: () => {}
      });
  }

  private buildHttpParams(): HttpParams {
    if (this.selectedCountryIds.length > 0) {
      return this.reportCountryScope.appendMultiCountryParams(this.selectedCountryIds);
    }
    return this.reportCountryScope.appendCountryParam();
  }

  onCountryChange(): void {
    this.currentPage = 0;
    this.first = 0;
    this.loadList();
  }

  ngOnInit(): void {
    if (this.hasMultiCountry) { this.loadCountries(); }
    this.loadStatusOptions();
    this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => this.loadStatusOptions());
    this.loadList();
    this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
      this.selectedCountryIds = [];
      this.currentPage = 0;
      this.first = 0;
      this.loadList();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Statuses come from the LU_PAYMENT_AUTH_STATUS lookup, which carries the
   * bilingual names and the sort order. Codes present on records but missing
   * from the lookup would otherwise be unfilterable, so they are appended.
   */
  private loadStatusOptions(): void {
    this.lookupService.getPaymentAuthStatuses()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (statuses) => {
          this.statusLabels = {};
          for (const s of statuses) {
            if (s.value) { this.statusLabels[s.value] = s.label; }
          }
          this.statusOptions = [
            { label: this.translationService.translate('COMMON.ALL'), value: null },
            ...statuses
          ];
        },
        error: () => { this.statusOptions = [{ label: this.translationService.translate('COMMON.ALL'), value: null }]; }
      });
  }

  get isRTL(): boolean {
    return this.translationService.isRTL();
  }

  loadList(): void {
    this.loadingList = true;
    let params = this.buildHttpParams();
    params = params.set('page', String(this.currentPage))
      .set('size', String(this.pageSize))
      .set('sortBy', 'id')
      .set('sortDir', 'desc');
    if (this.searchTerm) params = params.set('searchTerm', this.searchTerm);
    if (this.selectedStatusFilter) params = params.set('status', this.selectedStatusFilter);
    this.http.get<PagedPaymentAuth>(`${environment.apiUrl}/mohe/api/v1/paymentAuth/`, { params })
      .subscribe({
        next: (res: PagedPaymentAuth) => {
          this.records = res.content || [];
          this.totalElements = res.totalElements;
          this.loadingList = false;
        },
        error: () => {
          this.loadingList = false;
          this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: this.translationService.translate('PAYMENT_AUTH.LOAD_ERROR')
          });
        }
      });
  }

  onSearch(): void {
    this.currentPage = 0;
    this.first = 0;
    this.loadList();
  }

  onPageChange(event: any): void {
    const at = pageOf(event, this.pageSize);
    this.currentPage = at.page;
    this.pageSize = at.rows;
    this.first = at.first;
    this.loadList();
  }

  selectRecord(record: PaymentAuth): void {
    this.loadingRecord = true;
    this.service.getById(record.id).subscribe({
      next: (r) => {
        this.selectedRecord = r;
        this.loadingRecord = false;
        this.showVoucher();
      },
      error: () => {
        this.loadingRecord = false;
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load record' });
      }
    });
  }

  clearSelection(): void {
    this.selectedRecord = null;
    this.formPdfApiPath = null;
    this.formPdfQuery = null;
  }

  /** Endpoint rendering the statutory voucher (نموذج رقم ١١ حسابات) for one record. */
  private voucherApiPath(id: number): string {
    return `/api/v1/paymentAuth/${id}/export-report`;
  }

  /** Loads the selected record's voucher into the embedded viewer. */
  showVoucher(): void {
    const id = this.selectedRecord?.id;
    if (!id) return;
    this.formPdfApiPath = this.voucherApiPath(id);
    this.formPdfQuery = { lang: this.selectedLang };
    this.formLoadTrigger++;
  }

  /** Re-render the open voucher when the report language changes. */
  onLangChange(): void {
    if (this.selectedRecord) {
      this.showVoucher();
    }
  }

  private paymentAuthPdfQuery() {
    const entries: [string, any][] = [];
    if (this.selectedCountryIds.length === 1) {
      entries.push(['countryId', this.selectedCountryIds[0]]);
    } else if (this.selectedCountryIds.length > 1) {
      entries.push(['countryIds', this.selectedCountryIds]);
    } else {
      const cid = this.reportCountryScope.countryIdParam();
      if (cid != null) entries.push(['countryId', cid]);
    }
    if (this.selectedStatusFilter) entries.push(['status', this.selectedStatusFilter]);
    return { lang: this.selectedLang, ...Object.fromEntries(entries) };
  }

  generateReport(): void {
      this.pdfQuery = this.paymentAuthPdfQuery();
      this.loadTrigger++;
  }

  /**
   * With a record selected these act on that record's voucher, which is what the
   * buttons sit next to; with no selection they act on the full list export.
   */
  /** Filters for this run of the report; shared by all four actions. */
  reportQuery(): ReportPdfQuery {
      return { ...this.paymentAuthPdfQuery(), lang: this.selectedLang };
  }

  exportCsv(): void {
    if (!this.selectedRecord) return;
    const r = this.selectedRecord;
    const isAr = this.isRTL;
    const rows = [
      [isAr ? 'الحقل' : 'Field', isAr ? 'القيمة' : 'Value'],
      [isAr ? 'رقم النموذج' : 'Form Number',       r.authFormNumber],
      [isAr ? 'التاريخ' : 'Date',                   r.authDate],
      [isAr ? 'رقم الطالب' : 'Student ID',          r.studentId],
      [isAr ? 'اسم الطالب' : 'Student Name',        r.studentName],
      [isAr ? 'الوزارة' : 'Ministry',               r.ministryName],
      [isAr ? 'الجهة' : 'Department',               r.deptName],
      [isAr ? 'المحافظة' : 'Province',              r.provinceName],
      [isAr ? 'البنك' : 'Bank',                     r.bankName],
      [isAr ? 'الفرع' : 'Branch',                   r.bankBranch],
      [isAr ? 'يرسل إلى' : 'Sent To',              r.sentTo],
      [isAr ? 'الغرض' : 'Purpose',                  r.paymentPurpose],
      [isAr ? 'أصل المبلغ' : 'Original Amount',     r.originalAmount],
      [isAr ? 'الاستقطاعات' : 'Deductions',         r.deductions],
      [isAr ? 'صافي المبلغ' : 'Net Amount',         r.netAmount],
      [isAr ? 'رقم الشيك' : 'Check Number',         r.checkNumber],
      [isAr ? 'تاريخ الشيك' : 'Check Date',         r.checkDate],
      [isAr ? 'الميزانية' : 'Budget Book',          r.budgetBookNumber],
      [isAr ? 'اسم المستلم' : 'Recipient Name',     r.recipientName],
      [isAr ? 'هوية المستلم' : 'Recipient ID',      r.recipientIdNumber],
      [isAr ? 'الحالة' : 'Status',                  r.authStatus],
      [isAr ? 'ملاحظات' : 'Notes',                  r.notes]
    ];
    const csv = rows.map(row =>
      row.map(c => `"${(c ?? '').toString().replace(/"/g, '""')}"`).join(',')
    ).join('\n');
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `payment-auth-${r.authFormNumber || r.id}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  formatAmount(v: any): string {
    if (v === null || v === undefined || v === '') return '—';
    return Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  getStatusSeverity(s: string): string {
    return s === 'APPROVED' ? 'success' : s === 'PAID' ? 'info' : 'warning';
  }

  /** Display name from the lookup; falls back to the raw code if it has no row. */
  getStatusLabel(s: string): string {
    return this.statusLabels[s] || s;
  }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

}
