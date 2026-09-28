import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Table } from 'primeng/table';
import { MessageService, ConfirmationService } from 'primeng/api';
import { Subscription } from 'rxjs';
import { skip } from 'rxjs/operators';

import { StudentFinance } from '../../api/studentfinance';
import { StudentFinanceService, PagedResponse, PaginationRequest } from '../../service/studentfinance.service';
import { FinanceLookupsService, FinanceLookups, emptyFinanceLookups } from '../../service/finance-lookups.service';
import { TranslationService } from '../../service/translation.service';
import { extractApiErrorMessage } from '../../service/api-error.util';
import { CodeListService, PrimeSeverity } from '../../service/code-list.service';

@Component({
    templateUrl: './finance.component.html',
    styleUrls: ['./finance.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class FinanceComponent implements OnInit, OnDestroy {
  @ViewChild('dt') table!: Table;

  studentFinanceList: StudentFinance[] = [];
  totalElements = 0;
  loading = true;

  lookups: FinanceLookups = emptyFinanceLookups();

  /** Status filter options — STUDY_FIN_RECEIVED_STATUS plus an "all" entry. */
  statusOptions: Array<{ label: string; value: number | null }> = [];

  // Filters
  globalFilter = '';
  selectedStatus: number | null = null;
  dateRange: Date[] = [];

  // Paging / sorting
  rowsPerPageOptions = [5, 10, 25, 50];
  first = 0;
  rows = 10;
  currentPage = 0;
  totalPages = 0;
  sortField = 'id';
  sortOrder = 1;

  exporting = false;

  private languageSub?: Subscription;

  constructor(
    private studentFinanceService: StudentFinanceService,
    private financeLookups: FinanceLookupsService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private router: Router,
    public translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.loadLookups();
    this.loadStudentFinance();

    // Lookup labels are language-specific, so reload them on a language switch.
    // skip(1) avoids a duplicate load on init.
    this.languageSub = this.translationService.currentLanguage$
      .pipe(skip(1))
      .subscribe(() => this.loadLookups());
  }

  ngOnDestroy(): void {
    this.languageSub?.unsubscribe();
  }

  private loadLookups(): void {
    this.financeLookups.loadAll().subscribe(lookups => {
      this.lookups = lookups;
      this.statusOptions = [
        { label: this.translationService.translate('COMMON.ALL'), value: null },
        ...lookups.finReceivedStatuses.map(status => ({
          label: status.studyFinReceivedStatusName,
          value: status.id
        }))
      ];
    });
  }

  loadStudentFinance(): void {
    this.loading = true;
    const request: PaginationRequest & {
      searchTerm?: string;
      status?: number;
      startDate?: string;
      endDate?: string;
    } = {
      page: this.currentPage,
      size: this.rows,
      sortBy: this.sortField,
      sortDir: this.sortOrder === 1 ? 'asc' : 'desc',
      searchTerm: this.globalFilter,
      status: this.selectedStatus ?? undefined,
      startDate: this.dateRange?.[0] ? this.toIsoDate(this.dateRange[0]) : undefined,
      endDate: this.dateRange?.[1] ? this.toIsoDate(this.dateRange[1]) : undefined
    };

    const hasFilters = !!(this.globalFilter || this.selectedStatus !== null || this.dateRange?.length === 2);
    const serviceCall = hasFilters
      ? this.studentFinanceService.searchFinance(request)
      : this.studentFinanceService.getPagingAllAdvanced(request);

    serviceCall.subscribe({
      next: (data: PagedResponse) => {
        this.studentFinanceList = data.content || [];
        this.totalElements = data.totalElements;
        this.totalPages = data.totalPages;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading student finance data:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('FINANCE.MESSAGES.LOAD_ERROR')
        });
        this.loading = false;
      }
    });
  }

  // ── Display helpers (all values resolved from lookup tables) ──────────────

  getFinReceivedStatusName(statusId: number | null | undefined): string {
    return this.financeLookups.resolveName(
      this.lookups.finReceivedStatuses, statusId, 'studyFinReceivedStatusName'
    );
  }

  /**
   * UI_SEVERITY as STUDY_FIN_RECEIVED_STATUS states it; when the row has none,
   * the colour follows the code — money that moved is success, NOT_SPECIFIED is
   * neutral, the rest warn. This used to paint any known id green, which would
   * have turned the 976 "Not Specified" (99) rows green (live QA 23 Sep 2026, H2).
   */
  getFinReceivedStatusSeverity(statusId: number | null | undefined): PrimeSeverity {
    if (statusId === null || statusId === undefined) {
      return 'secondary';
    }
    const row = (this.lookups.finReceivedStatuses || []).find(s => Number(s.id) === Number(statusId));
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

  getCurrencyName(currencyId: number | null | undefined): string {
    return this.financeLookups.resolveCurrencyName(this.lookups.currencies, currencyId);
  }

  /**
   * What the student actually nets. NET_AMOUNT is a computed column on the server, but
   * it is only present once V015 has been applied, so fall back to the arithmetic rather
   * than render a blank column against an un-migrated database.
   */
  getNetAmount(row: StudentFinance): number {
    return row.netAmount ?? (row.studentFinRecievedAmount ?? 0) - (row.wafarAmount ?? 0);
  }

  // ── Filters / paging ──────────────────────────────────────────────────────

  // The table reference used to be passed in from the template and then
  // ignored. The filter bar now sits above <p-table>, so the argument
  // would have been a forward template reference for no purpose.
  onGlobalFilter(event: Event): void {
    this.globalFilter = (event.target as HTMLInputElement).value;
    this.resetToFirstPage();
    this.loadStudentFinance();
  }

  onStatusFilter(event: any): void {
    this.selectedStatus = event.value;
    this.resetToFirstPage();
    this.loadStudentFinance();
  }

  onDateFilter(event: any): void {
    if (event && event.length === 2) {
      this.dateRange = event;
      this.resetToFirstPage();
      this.loadStudentFinance();
    }
  }

  clearFilters(): void {
    this.globalFilter = '';
    this.selectedStatus = null;
    this.dateRange = [];
    this.resetToFirstPage();
    this.table?.clear();
    this.loadStudentFinance();
  }

  onPageChange(event: any): void {
    this.currentPage = Math.floor(event.first / event.rows);
    this.rows = event.rows;
    this.first = event.first;
    this.loadStudentFinance();
  }

  onSort(event: any): void {
    this.sortField = event.field;
    this.sortOrder = event.order;
    this.resetToFirstPage();
    this.loadStudentFinance();
  }

  private resetToFirstPage(): void {
    this.currentPage = 0;
    this.first = 0;
  }

  private toIsoDate(value: Date): string {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  goToAdd(): void {
    this.router.navigate(['/finance/add']);
  }

  goToEdit(studentFinance: StudentFinance): void {
    this.router.navigate(['/finance/edit', studentFinance.id]);
  }

  goToView(studentFinance: StudentFinance): void {
    this.router.navigate(['/finance/view', studentFinance.id]);
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  deleteStudentFinance(studentFinance: StudentFinance): void {
    this.confirmationService.confirm({
      message: this.translationService
        .translate('FINANCE.DELETE_CONFIRM_MESSAGE')
        .replace('{name}', studentFinance.studentFinName),
      header: this.translationService.translate('FINANCE.DELETE_CONFIRM_HEADER'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translationService.translate('FINANCE.DELETE_ACCEPT'),
      rejectLabel: this.translationService.translate('COMMON.CANCEL'),
      accept: () => {
        this.loading = true;
        this.studentFinanceService.Delete(studentFinance.id).subscribe({
          next: (response: any) => {
            this.loading = false;
            const detail = (response?.status === 'PENDING' && response?.message)
              ? response.message
              : this.translationService.translate('FINANCE.MESSAGES.DELETE_SUCCESS');
            this.messageService.add({
              severity: 'success',
              summary: this.translationService.translate('COMMON.SUCCESS'),
              detail
            });
            this.loadStudentFinance();
          },
          error: (error) => {
            this.loading = false;
            console.error('Error deleting student finance:', error);
            this.messageService.add({
              severity: 'error',
              summary: this.translationService.translate('COMMON.ERROR'),
              detail: extractApiErrorMessage(error)
                || this.translationService.translate('FINANCE.MESSAGES.DELETE_FAILED'),
              life: 10000
            });
          }
        });
      }
    });
  }

  // ── Excel export ──────────────────────────────────────────────────────────

  /** Exports every finance record with lookup ids resolved to their names. */
  exportToExcel(): void {
    this.exporting = true;
    this.studentFinanceService.getAll().subscribe({
      next: (data) => {
        const rows = data || [];
        if (!rows.length) {
          this.exporting = false;
          this.messageService.add({
            severity: 'warn',
            summary: this.translationService.translate('COMMON.WARNING'),
            detail: this.translationService.translate('FINANCE.MESSAGES.EXPORT_EMPTY')
          });
          return;
        }
        this.writeWorkbook(rows)
          .then(() => {
            this.exporting = false;
            this.messageService.add({
              severity: 'success',
              summary: this.translationService.translate('COMMON.SUCCESS'),
              detail: this.translationService.translate('FINANCE.MESSAGES.EXPORT_SUCCESS')
            });
          })
          .catch((error) => {
            this.exporting = false;
            console.error('Error writing finance export:', error);
            this.messageService.add({
              severity: 'error',
              summary: this.translationService.translate('COMMON.ERROR'),
              detail: this.translationService.translate('FINANCE.MESSAGES.EXPORT_FAILED')
            });
          });
      },
      error: () => {
        this.exporting = false;
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('FINANCE.MESSAGES.EXPORT_FAILED')
        });
      }
    });
  }

  private async writeWorkbook(rows: StudentFinance[]): Promise<void> {
    const t = (key: string) => this.translationService.translate(key);
    const isRtl = this.translationService.isRTL();

    // exceljs is ~1.4 MB, so it is pulled in on demand rather than being part
    // of the finance route's initial payload.
    const { Workbook } = await import('exceljs');
    const workbook = new Workbook();
    workbook.creator = 'MOHE System';
    workbook.created = new Date();

    const sheet = workbook.addWorksheet(t('FINANCE.TITLE'), { views: [{ rightToLeft: isRtl }] });

    sheet.columns = [
      { header: '#', key: 'idx', width: 6 },
      { header: t('FINANCE.STUDENT_ID'), key: 'studentId', width: 12 },
      { header: t('FINANCE.FINANCE_ID'), key: 'financeId', width: 16 },
      { header: t('FINANCE.STUDENT_NAME'), key: 'studentName', width: 38 },
      { header: t('FINANCE.AMOUNT_RECEIVED'), key: 'amount', width: 16, style: { numFmt: '#,##0.00' } },
      { header: t('FINANCE.CURRENCY'), key: 'currency', width: 14 },
      { header: t('FINANCE.CHECK_DATE'), key: 'checkDate', width: 16 },
      { header: t('FINANCE.FINANCE_RECEIVED_STATUS'), key: 'status', width: 20 },
      { header: t('FINANCE.STUDY_COUNTRY'), key: 'country', width: 20 },
      { header: t('FINANCE.STUDY_GOVERNMENT'), key: 'government', width: 20 },
      { header: t('FINANCE.FINANCE_BANK'), key: 'bank', width: 24 },
      { header: t('FINANCE.BANK_BRANCH'), key: 'branch', width: 24 },
      { header: t('FINANCE.FINANCE_DOF'), key: 'dof', width: 20 },
      { header: t('FINANCE.RECEIVED_WHOM'), key: 'receivedWhom', width: 24 },
      { header: t('FINANCE.WAFAR_AMOUNT'), key: 'wafarAmount', width: 16, style: { numFmt: '#,##0.00' } },
      { header: t('FINANCE.NET_AMOUNT'), key: 'netAmount', width: 20, style: { numFmt: '#,##0.00' } }
    ];

    const headerRow = sheet.getRow(1);
    headerRow.height = 28;
    headerRow.eachCell(cell => {
      cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF1B5E20' } };
      cell.font = { bold: true, color: { argb: 'FFFFFFFF' }, size: 11 };
      cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
      cell.border = { top: { style: 'thin' }, left: { style: 'thin' }, bottom: { style: 'thin' }, right: { style: 'thin' } };
    });

    const resolve = (list: any[], id: any, field: string) => this.financeLookups.resolveName(list, id, field);

    rows.forEach((row, i) => {
      const dataRow = sheet.addRow({
        idx: i + 1,
        studentId: row.id,
        financeId: row.studentFinId || '-',
        studentName: row.studentFinName || '-',
        amount: row.studentFinRecievedAmount ?? 0,
        currency: this.getCurrencyName(row.studyCurrencyInId) || '-',
        checkDate: row.studyCheckDate ? new Date(row.studyCheckDate) : '-',
        status: resolve(this.lookups.finReceivedStatuses, row.studyFinRecievedStatus, 'studyFinReceivedStatusName'),
        country: resolve(this.lookups.studyCountries, row.studyCountryInId, 'studyCountryInName'),
        government: resolve(this.lookups.studyGovernments, row.studyGovInId, 'studyGovInName'),
        bank: resolve(this.lookups.studyFinanceBanks, row.studyFinanceBankeId, 'studyFinanceBankeName'),
        branch: resolve(this.lookups.studyFinanceBankBranches, row.studyFinanceBankBranchId, 'studyFinanceBankBranchName'),
        dof: resolve(this.lookups.studyFinanceDofs, row.studyFinanceDofId, 'studyFinanceDofName'),
        receivedWhom: resolve(this.lookups.finReceivedWhoms, row.studyReceivedWhomId, 'finReceivedName'),
        wafarAmount: row.wafarAmount ?? 0,
        netAmount: this.getNetAmount(row)
      });
      dataRow.height = 20;
      dataRow.eachCell(cell => {
        cell.alignment = { vertical: 'middle' };
        cell.border = { top: { style: 'thin' }, left: { style: 'thin' }, bottom: { style: 'thin' }, right: { style: 'thin' } };
        if (i % 2 === 0) {
          cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF1F8E9' } };
        }
      });
    });

    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer as ArrayBuffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `Student_Finance_${new Date().toISOString().slice(0, 10)}.xlsx`;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    setTimeout(() => window.URL.revokeObjectURL(url), 200);
  }
}
