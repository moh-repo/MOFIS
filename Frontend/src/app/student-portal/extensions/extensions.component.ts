import { Component, OnInit, inject } from '@angular/core';
import { StudentPortalApiService, PortalExtension } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { portalEnumLabel } from '../student-portal-i18n.helper';
import { distinctValues, filterRows } from '../portal-list.helper';
import { CodeListService } from '../../demo/service/code-list.service';

@Component({
    selector: 'app-portal-extensions',
    templateUrl: './extensions.component.html',
    styleUrls: ['./extensions.component.scss'],
    standalone: false
})
export class ExtensionsComponent implements OnInit {
  private readonly codeLists = inject(CodeListService);

  list: PortalExtension[] = [];
  loading = true;
  errorKey = '';
  selectedExtension: PortalExtension | null = null;
  showDetailModal = false;

  constructor(
    private api: StudentPortalApiService,
    private translationService: TranslationService
  ) {}

  ngOnInit(): void {
    this.api.getExtensions().subscribe({
      next: (data) => {
        this.list = Array.isArray(data) ? data : [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        if (err?.status === 404 || err?.status === 501) {
          this.list = [];
          this.errorKey = '';
        } else {
          this.errorKey = 'STUDENT_PORTAL.LOAD_EXTENSIONS_ERROR';
        }
      }
    });
  }

  /**
   * Search and status filter, applied here rather than at the server.
   *
   * <p>Honest because the endpoint returns the whole list — there is no paging
   * to search past. The paged lists deliberately do not get a box like this;
   * see portal-list.helper.
   */
  search = '';
  statusFilter: string | null = null;

  get statusOptions(): string[] {
    // From the rows the student actually has, not from the enum: offering
    // "Rejected" to someone with no rejected extension is a dead end.
    return distinctValues(this.list, row => this.statusKey(row));
  }

  get filteredList(): PortalExtension[] {
    const byStatus = this.statusFilter
      ? this.list.filter(row => this.statusKey(row) === this.statusFilter)
      : this.list;
    return filterRows(byStatus, this.search, [
      row => this.extensionTypeLabel(row),
      row => this.extensionStatusLabel(row),
      row => row.extensionReason,
      row => row.extensionDecisionNo
    ]);
  }

  /** True when the list is empty only because of the filters. */
  get filteredEverythingOut(): boolean {
    return this.list.length > 0 && this.filteredList.length === 0;
  }

  clearFilters(): void {
    this.search = '';
    this.statusFilter = null;
  }

  // ── Names ───────────────────────────────────────────────────
  //
  // STUDENT_EXTENSION stores the staff form's option value — the English name,
  // "In Progress" — and the page looked that up as if it were a code, so the
  // status stayed English in Arabic (QA SP-009). The server now resolves each
  // stored value to its lookup code; the code list names it in the reader's
  // language and follows the in-place language switch. A value no lookup row
  // matches has no code and is shown as the server labelled it, else as stored.

  /** The status as a code-list key: the resolved code, else the stored value itself. */
  statusKey(row: PortalExtension): string {
    return row.extensionStatusCode || row.extensionStatus || '';
  }

  extensionStatusLabel(row: PortalExtension): string {
    return portalEnumLabel(this.codeLists, 'extension-status', this.statusKey(row), '-', row.extensionStatusLabel);
  }

  /** The filter's options are keys alone, with no row behind them. */
  statusOptionLabel(key: string): string {
    return portalEnumLabel(this.codeLists, 'extension-status', key);
  }

  extensionTypeLabel(row: PortalExtension): string {
    return portalEnumLabel(this.codeLists, 'extension-type',
      row.extensionTypeCode || row.extensionType, '-', row.extensionTypeLabel);
  }

  /** Green for an approved extension, red for a refused one, by code — "Approved" as stored used to read as neither. */
  statusColor(row: PortalExtension): string {
    switch (this.statusKey(row)) {
      case 'APPROVED': return 'green';
      case 'REJECTED': return 'red';
      default: return 'default';
    }
  }

  openDetail(row: PortalExtension): void {
    this.selectedExtension = row;
    this.showDetailModal = true;
  }

  closeDetail(): void {
    this.showDetailModal = false;
    this.selectedExtension = null;
  }
}
