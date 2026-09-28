import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { Table } from 'primeng/table';
import { MessageService, ConfirmationService } from 'primeng/api';
import { LookupTableService } from '../../../service/lookuptable.service';
import { LookupTableType } from '../../../api/lookuptable';
import { TranslationService } from '../../../service/translation.service';
import {
    LookupDescriptor,
    LookupField,
    LookupOptionSource,
    LOOKUP_OPTION_LISTS,
    describeLookup
} from './lookup-descriptors';
import { changesOnly } from '../../../service/reload-streams.util';
import { extractApiErrorMessage } from '../../../service/api-error.util';

@Component({
    selector: 'app-lookuptables',
    templateUrl: './lookuptables.component.html',
    styleUrls: ['./lookuptables.component.css'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class LookupTablesComponent implements OnInit, OnDestroy {
  @ViewChild('dt') table!: Table;

  // Tab groups — 6 tabs (populated with translated values on init/language change)
  // Tab 0: Student
  studentLookups: LookupTableType[] = [];
  // Tab 1: Study & University
  studyUniversityLookups: LookupTableType[] = [];
  // Tab 2: Finance
  financeLookups: LookupTableType[] = [];
  // Tab 3: Operations
  operationsLookups: LookupTableType[] = [];
  // Tab 4: System
  systemLookups: LookupTableType[] = [];
  // Tab 5: Location
  locationLookups: LookupTableType[] = [];

  private languageSubscription?: any;

  // Data properties
  activeTabIndex: number = 0;
  selectedTableType: LookupTableType | null = null;
  currentData: any[] = [];
  totalRecords: number = 0;
  loading: boolean = false;

  // Pagination
  first: number = 0;
  rows: number = 10;
  rowsPerPageOptions = [5, 10, 25, 50];

  // Dialog states
  dataDialog: boolean = false;
  viewDialog: boolean = false;
  deleteDialog: boolean = false;
  deleteMultipleDialog: boolean = false;

  // Form data
  currentItem: any = {};
  selectedItems: any[] = [];
  submitted: boolean = false;
  isEditMode: boolean = false;

  /**
   * The option lists the foreign-key fields choose from, fetched once each and
   * kept. They used to be re-fetched on every table switch *and* on every
   * `openNew` and `editItem` — up to a thousand countries and a thousand
   * governorates a time, four round trips to open two dialogs, for eighteen and
   * twenty-two actual rows. An entry is dropped when the lookup it came from is
   * itself edited, which is the only way it can go stale.
   */
  private optionCache = new Map<LookupOptionSource, any[]>();
  private optionsLoading = new Set<LookupOptionSource>();

  // Search and filters
  globalFilter: string = '';
  searchTerm: string = '';
  sortField: string = '';
  sortOrder: number = 1;
  private searchSubject = new Subject<string>();
  private searchSubscription?: { unsubscribe: () => void };

  // Columns configuration
  columns: LookupField[] = [];

  // Flag to prevent double loading when tab changes
  private isManualTabChange: boolean = false;

  constructor(
    private lookupTableService: LookupTableService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService
  ) {}

  ngOnInit(): void {
    this.searchSubscription = this.searchSubject.pipe(debounceTime(300)).subscribe(term => {
      this.searchTerm = term;
      this.first = 0;
      this.loadData();
    });
    this.initializeLookupTables();

    // Subscribe to query parameters to handle tab navigation from menu
    this.route.queryParams.subscribe(params => {
      const tabIndex = params['tab'];
      const tableId = params['table'];

      // Skip if this is a manual tab change (to prevent double loading)
      if (this.isManualTabChange) {
        this.isManualTabChange = false;
        return;
      }

      if (tabIndex !== undefined && tabIndex !== null) {
        this.activeTabIndex = parseInt(tabIndex, 10);
      }

      if (tableId) {
        this.selectTableById(String(tableId));
      } else if (tabIndex !== undefined && tabIndex !== null) {
        this.loadDefaultTableForTab(this.activeTabIndex);
      } else {
        // Set default to first lookup in first tab if no tab parameter
        if (this.studentLookups.length > 0) {
          this.activeTabIndex = 0;
          this.selectTable(this.studentLookups[0]);
        }
      }
    });

    // Subscribe to language changes
    this.languageSubscription = this.translationService.currentLanguage$.pipe(changesOnly()).subscribe(() => {
      this.initializeLookupTables();
      if (this.selectedTableType) {
        // Refresh the selected table type with translated values
        this.refreshSelectedTableType();
      }
      this.setupColumns();
    });
  }

  ngOnDestroy(): void {
    this.loadSubscription?.unsubscribe();
    if (this.languageSubscription) {
      this.languageSubscription.unsubscribe();
    }
    this.searchSubscription?.unsubscribe();
  }

  initializeLookupTables(): void {
    const t = (key: string) => this.translationService.translate(key);

    // Tab 0 — Student: personal/profile lookups
    this.studentLookups = [
      { id: 'student-gender',       name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_GENDER'),       entityName: 'StudentGender',      description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_GENDER') },
      { id: 'student-degree',       name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_DEGREE'),       entityName: 'StudentDegree',      description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_DEGREE') },
      { id: 'student-current-level',name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_CURRENT_LEVEL'),entityName: 'StudentCurrentLevel',description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_CURRENT_LEVEL') },
      { id: 'student-agency-type',  name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_AGENCY_TYPE'),  entityName: 'StudentAgencyType',  description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_AGENCY_TYPE') },
      { id: 'student-ministry-type',name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_MINISTRY_TYPE'),entityName: 'StudentMinistryType',description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_MINISTRY_TYPE') },
      { id: 'student-study-type',   name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_STUDY_TYPE'),   entityName: 'StudentStudyType',   description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_STUDY_TYPE') },
      { id: 'student-study-status', name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_STUDY_STATUS'), entityName: 'StudentStudyStatus', description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_STUDY_STATUS') },
      { id: 'death-procedures',     name: t('LOOKUP_TABLES.TABLE_NAMES.DEATH_PROCEDURE'),      entityName: 'LuDeathProcedure',     description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.DEATH_PROCEDURE') }
    ];

    // Tab 1 — Study & University: academic study-abroad lookups
    this.studyUniversityLookups = [
      { id: 'study-status',              name: t('LOOKUP_TABLES.TABLE_NAMES.STUDY_STATUS'),              entityName: 'StudyStatus',            description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDY_STATUS') },
      { id: 'study-degree-in',           name: t('LOOKUP_TABLES.TABLE_NAMES.STUDY_DEGREE_IN'),           entityName: 'StudyDegreeIn',          description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDY_DEGREE_IN') },
      { id: 'student-university-type',   name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_UNIVERSITY_TYPE'),   entityName: 'StudentUniversityType',  description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_UNIVERSITY_TYPE') },
      { id: 'student-major',             name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_MAJOR'),             entityName: 'StudentMajor',           description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_MAJOR') },
      { id: 'study-received-whom-status',name: t('LOOKUP_TABLES.TABLE_NAMES.STUDY_RECEIVED_WHOM_STATUS'),entityName: 'StudyReceivedWhomStatus',description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDY_RECEIVED_WHOM_STATUS') }
    ];

    // Tab 2 — Finance: financial transaction & payment lookups
    this.financeLookups = [
      { id: 'wafar-spending-statuses',       name: t('LOOKUP_TABLES.TABLE_NAMES.WAFAR_SPENDING_STATUS'),       entityName: 'LuWafarSpendingStatus',      description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.WAFAR_SPENDING_STATUS') },
      { id: 'wafar-reconciliation-statuses', name: t('LOOKUP_TABLES.TABLE_NAMES.WAFAR_RECONCILIATION_STATUS'), entityName: 'LuWafarReconciliationStatus',description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.WAFAR_RECONCILIATION_STATUS') },
      { id: 'payment-auth-statuses',         name: t('LOOKUP_TABLES.TABLE_NAMES.PAYMENT_AUTH_STATUS'),         entityName: 'LuPaymentAuthStatus',        description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.PAYMENT_AUTH_STATUS') },
      { id: 'payment-amount-sources',        name: t('LOOKUP_TABLES.TABLE_NAMES.PAYMENT_AMOUNT_SOURCE'),       entityName: 'LuPaymentAmountSource',      description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.PAYMENT_AMOUNT_SOURCE') },
      { id: 'books-allowance-pay-statuses',  name: t('LOOKUP_TABLES.TABLE_NAMES.BOOKS_ALLOWANCE_PAY_STATUS'), entityName: 'LuBooksAllowancePayStatus',  description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.BOOKS_ALLOWANCE_PAY_STATUS') },
      { id: 'loan-statuses',                 name: t('LOOKUP_TABLES.TABLE_NAMES.LOAN_STATUS'),                 entityName: 'LuLoanStatus',               description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.LOAN_STATUS') }
    ];

    // Tab 3 — Operations: workflow, quarters, MOHE categories
    this.operationsLookups = [
      { id: 'change-request-statuses', name: t('LOOKUP_TABLES.TABLE_NAMES.CHANGE_REQUEST_STATUS'), entityName: 'LuChangeRequestStatus', description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.CHANGE_REQUEST_STATUS') },
      { id: 'quarter-numbers',         name: t('LOOKUP_TABLES.TABLE_NAMES.QUARTER_NUMBER'),         entityName: 'LuQuarterNumber',       description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.QUARTER_NUMBER') },
      { id: 'mohe-main',               name: t('LOOKUP_TABLES.TABLE_NAMES.MOHE_MAIN'),               entityName: 'MoheMain',              description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.MOHE_MAIN') },
      { id: 'mohe-sub-main',           name: t('LOOKUP_TABLES.TABLE_NAMES.MOHE_SUB_MAIN'),           entityName: 'MoheSubMain',           description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.MOHE_SUB_MAIN') }
    ];

    // Tab 4 — System: notification & portal configuration lookups
    this.systemLookups = [
      { id: 'notification-channels',        name: t('LOOKUP_TABLES.TABLE_NAMES.NOTIFICATION_CHANNEL'),        entityName: 'LuNotificationChannel',       description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.NOTIFICATION_CHANNEL') },
      { id: 'notification-recipient-types', name: t('LOOKUP_TABLES.TABLE_NAMES.NOTIFICATION_RECIPIENT_TYPE'), entityName: 'LuNotificationRecipientType', description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.NOTIFICATION_RECIPIENT_TYPE') },
      { id: 'notification-scopes',          name: t('LOOKUP_TABLES.TABLE_NAMES.NOTIFICATION_SCOPE'),          entityName: 'LuNotificationScope',         description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.NOTIFICATION_SCOPE') },
      { id: 'student-portal-account-statuses', name: t('LOOKUP_TABLES.TABLE_NAMES.STUDENT_PORTAL_ACCOUNT_STATUS'), entityName: 'LuStudentPortalAccountStatus', description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.STUDENT_PORTAL_ACCOUNT_STATUS') },
      { id: 'report-dimensions',             name: t('LOOKUP_TABLES.TABLE_NAMES.REPORT_DIMENSION'),              entityName: 'LuReportDimension',            description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.REPORT_DIMENSION') },
      { id: 'trend-classes',                   name: t('LOOKUP_TABLES.TABLE_NAMES.TREND_CLASS'),                   entityName: 'LuTrendClass',                 description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.TREND_CLASS') },
      { id: 'report-labels',                   name: t('LOOKUP_TABLES.TABLE_NAMES.REPORT_LABEL'),                  entityName: 'LuReportLabel',                description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.REPORT_LABEL') }
    ];

    // Tab 5 — Location: geographic lookups
    this.locationLookups = [
      { id: 'country',     name: t('LOOKUP_TABLES.TABLE_NAMES.COUNTRY'),     entityName: 'Country',     description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.COUNTRY') },
      { id: 'governorate', name: t('LOOKUP_TABLES.TABLE_NAMES.GOVERNORATE'), entityName: 'Governorate', description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.GOVERNORATE') },
      { id: 'municipality',name: t('LOOKUP_TABLES.TABLE_NAMES.MUNICIPALITY'),entityName: 'Municipality',description: t('LOOKUP_TABLES.TABLE_DESCRIPTIONS.MUNICIPALITY') }
    ];
  }

  private allLookups(): LookupTableType[] {
    return [
      ...this.studentLookups, ...this.studyUniversityLookups,
      ...this.financeLookups, ...this.operationsLookups,
      ...this.systemLookups, ...this.locationLookups
    ];
  }

  selectTableById(tableId: string): void {
    const match = this.allLookups().find(l => l.id === tableId);
    if (match) {
      this.selectTable(match);
    } else {
      this.loadDefaultTableForTab(this.activeTabIndex);
    }
  }

  refreshSelectedTableType(): void {
    if (!this.selectedTableType) return;

    const updated = this.allLookups().find(l => l.id === this.selectedTableType!.id);
    if (updated) {
      this.selectedTableType = updated;
    }
  }

  // ── The selected lookup, and what it is made of ───────────────────────────

  /** The fields of the selected lookup; empty until one is chosen. */
  get descriptor(): LookupDescriptor | undefined {
    return describeLookup(this.selectedTableType?.id);
  }

  isBilingualTable(id?: string): boolean {
    return !!describeLookup(id ?? this.selectedTableType?.id)?.bilingual;
  }

  /** Editable fields, in the order the dialog shows them. */
  get formFields(): LookupField[] {
    return (this.descriptor?.fields ?? []).filter(f => !f.readonly);
  }

  /** Everything the view dialog lists, key column and system flag included. */
  get viewFields(): LookupField[] {
    return this.descriptor?.fields ?? [];
  }

  loadDefaultTableForTab(tabIndex: number): void {
    const groups = [
      this.studentLookups,       // 0
      this.studyUniversityLookups, // 1
      this.financeLookups,       // 2
      this.operationsLookups,    // 3
      this.systemLookups,        // 4
      this.locationLookups       // 5
    ];
    const group = groups[tabIndex] ?? groups[0];
    if (group.length > 0) {
      this.selectTable(group[0]);
    }
  }

  // p-tabs emits the selected tab's value directly; the old p-tabView emitted an
  // event object and this read event.index off it.
  onTabChange(index: number): void {
    // Set flag to prevent double loading from queryParams subscription
    this.isManualTabChange = true;

    // Update URL query parameter when tab changes manually
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: index },
      queryParamsHandling: 'merge'
    });

    // Load the first table of the newly selected tab
    this.loadDefaultTableForTab(index);
  }

  onTableTypeChange(lookupType: LookupTableType): void {
    this.selectTable(lookupType);
  }

  /**
   * The one way into a lookup, from the menu, a tab or the chooser.
   *
   * <p>Changing tab used to set the table and load, and nothing else — so page 4
   * was still requested of a list with six rows, and the sort field of the lookup
   * just left ("studentGenderName") was sent to one that has no such column.
   */
  private selectTable(lookupType: LookupTableType): void {
    this.selectedTableType = lookupType;
    this.selectedItems = [];
    this.resetPagination();
    this.setupColumns();
    this.loadData();
    this.loadOptionLists();
  }

  setupColumns(): void {
    this.columns = (this.descriptor?.fields ?? []).filter(f => !f.formOnly);
  }

  getIdField(): string {
    return this.descriptor?.idField ?? 'id';
  }

  /** Arabic name field for the selected table (camelCase JSON property). */
  getNameFieldAr(): string {
    return this.descriptor?.bilingual ? 'nameAr' : (this.descriptor?.fields[1]?.key ?? 'name');
  }

  /** English name field for the selected table (camelCase JSON property). */
  getNameFieldEn(): string {
    return this.descriptor?.bilingual ? 'nameEn' : (this.descriptor?.fields[2]?.key ?? this.getNameFieldAr());
  }

  /** Name field for the current UI language (used for filtering / display fallbacks). */
  getNameField(): string {
    return this.translationService.getCurrentLanguage() === 'en'
      ? this.getNameFieldEn()
      : this.getNameFieldAr();
  }

  /**
   * The load in flight. A newer one cancels it: a late reply to an older search,
   * page or table used to land on top of the newer one's (QA B15; the
   * "intermittent" results in the 21 Sep verification).
   */
  private loadSubscription?: Subscription;

  loadData(): void {
    if (!this.selectedTableType) return;

    this.loading = true;
    const page = Math.floor(this.first / this.rows);

    this.loadSubscription?.unsubscribe();
    this.loadSubscription = this.lookupTableService.getAll(
      this.selectedTableType.id,
      page,
      this.rows,
      this.searchTerm,
      this.sortField || undefined,
      this.sortField ? (this.sortOrder === 1 ? 'asc' : 'desc') : undefined
    ).subscribe({
      next: (response) => {
        this.currentData = response.content || response;
        this.totalRecords = response.totalElements ?? response.length;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading data:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('LOOKUP_TABLES.MESSAGES.FAILED_TO_LOAD')
        });
        this.loading = false;
      }
    });
  }

  onLazyLoad(event: any): void {
    this.first = event.first ?? 0;
    this.rows = event.rows ?? this.rows;
    if (event.sortField) {
      this.sortField = event.sortField;
      this.sortOrder = event.sortOrder ?? 1;
    }
    this.loadData();
  }

  onGlobalFilter(event: any): void {
    this.globalFilter = event.target.value;
    this.searchSubject.next(this.globalFilter);
  }

  /** The search box back to empty, and the list with it. */
  clearGlobalFilter(): void {
    this.globalFilter = '';
    this.searchSubject.next('');
  }

  // ── Foreign-key option lists ──────────────────────────────────────────────

  /** The option lists the selected lookup's `ref` fields need, fetched once. */
  private loadOptionLists(): void {
    const sources = new Set<LookupOptionSource>(
      (this.descriptor?.fields ?? [])
        .filter(f => f.ref)
        .map(f => f.ref!.source)
    );
    sources.forEach(source => {
      if (this.optionCache.has(source) || this.optionsLoading.has(source)) {
        return;
      }
      this.optionsLoading.add(source);
      const list = LOOKUP_OPTION_LISTS[source];
      this.lookupTableService.getAll(list.table, 0, 1000, '').subscribe({
        next: response => {
          this.optionCache.set(source, response.content || response || []);
          this.optionsLoading.delete(source);
        },
        error: error => {
          console.error(`Error loading ${list.table} options:`, error);
          this.optionsLoading.delete(source);
        }
      });
    });
  }

  /** A cached option list is only stale if its own lookup has just been written to. */
  private invalidateOptionsForCurrentTable(): void {
    const id = this.selectedTableType?.id;
    (Object.keys(LOOKUP_OPTION_LISTS) as LookupOptionSource[])
      .filter(source => LOOKUP_OPTION_LISTS[source].table === id)
      .forEach(source => this.optionCache.delete(source));
  }

  /**
   * The options for one foreign-key field, narrowed by its parent when it has
   * one — a municipality's governorates are the ones in the country chosen above
   * them, not all of them with the control merely greyed out until a country is
   * picked.
   */
  optionsFor(field: LookupField): any[] {
    if (!field.ref) return [];
    const list = LOOKUP_OPTION_LISTS[field.ref.source];
    const cached = this.optionCache.get(field.ref.source) ?? [];
    const all = field.ref.asString ? this.withStringKeys(cached, list.value) : cached;
    if (!field.ref.parent || !list.parentKey) {
      return all;
    }
    const parentValue = this.currentItem?.[field.ref.parent];
    if (parentValue === null || parentValue === undefined || parentValue === '') {
      return [];
    }
    return all.filter(option => String(option[list.parentKey!]) === String(parentValue));
  }

  /**
   * A copy of the options with their key as a string. STUDENT_GOV_MUD.STUDENT_GOV_ID
   * is a varchar and STUDENT_GOV.ID an int, so the select compared "5" with 5,
   * matched nothing and opened every municipality's edit with the governorate
   * empty (live QA; `asString` was declared and never read). Kept per source list
   * so the select is not handed a new array on every check.
   */
  private readonly stringKeyed = new WeakMap<any[], any[]>();

  private withStringKeys(options: any[], key: string): any[] {
    let copy = this.stringKeyed.get(options);
    if (!copy) {
      copy = options.map(o => ({ ...o, [key]: o[key] == null ? o[key] : String(o[key]) }));
      this.stringKeyed.set(options, copy);
    }
    return copy;
  }

  optionLabelField(field: LookupField): string {
    const list = LOOKUP_OPTION_LISTS[field.ref!.source];
    return this.translationService.getCurrentLanguage() === 'en' ? list.labelEn : list.labelAr;
  }

  optionValueField(field: LookupField): string {
    return LOOKUP_OPTION_LISTS[field.ref!.source].value;
  }

  /** True while the field's parent has not been chosen yet. */
  awaitingParent(field: LookupField): boolean {
    const parent = field.ref?.parent;
    return !!parent && !this.currentItem?.[parent];
  }

  /**
   * A parent changed, so anything that hangs off it is no longer necessarily
   * valid. Left alone, choosing a different country kept the governorate that
   * belonged to the old one.
   */
  onRefChange(field: LookupField): void {
    (this.descriptor?.fields ?? [])
      .filter(f => f.ref?.parent === field.key)
      .forEach(child => { this.currentItem[child.key] = null; });
  }

  /**
   * The label of the row the field points at. Matched as strings on purpose:
   * `STUDENT_GOV_MUD.STUDENT_GOV_ID` is a varchar and `STUDENT_GOV.ID` an int, so
   * the `===` this replaces never matched and every municipality's governorate
   * read "N/A".
   */
  refLabel(row: any, field: LookupField): string {
    if (!field.ref) return '';
    const value = row?.[field.key];
    if (value === null || value === undefined || value === '') return '';
    const list = LOOKUP_OPTION_LISTS[field.ref.source];
    const option = (this.optionCache.get(field.ref.source) ?? [])
      .find(o => String(o[list.value]) === String(value));
    if (!option) return String(value);
    return option[this.optionLabelField(field)] || option[list.labelAr] || String(value);
  }

  // ── Rows the application depends on ───────────────────────────────────────

  /**
   * A row a Java constant names. `CodeListStartupGuard` will not let the
   * application start without it, so the page offers neither the delete button
   * nor the checkbox on it — the server refuses either way, and a button that
   * always fails is worse than no button.
   */
  isSystemRow(row: any): boolean {
    return !!this.descriptor?.bilingual && row?.isSystem === true;
  }

  canDelete(row: any): boolean {
    return !this.isSystemRow(row);
  }

  /** Select-all ticks the rows that can actually go, and no others. */
  onSelectionChange(selection: any[]): void {
    this.selectedItems = (selection ?? []).filter(row => this.canDelete(row));
  }

  // ── Dialogs ───────────────────────────────────────────────────────────────

  openNew(): void {
    this.currentItem = {};
    const descriptor = this.descriptor;
    if (descriptor) {
      // Text fields start empty rather than absent, so one left blank is sent as "".
      // Absent, it reached the server as null: a new report label with no English name
      // was refused "A required value is missing", its column being NOT NULL (live QA R5-24).
      for (const field of descriptor.fields) {
        if (field.kind === 'text') {
          this.currentItem[field.key] = '';
        }
      }
      if (descriptor.fields.some(f => f.key === 'isActive')) {
        this.currentItem.isActive = true;
      }
      if (descriptor.bilingual) {
        // The lists are read back in SORT_ORDER, and a new code used to arrive
        // with the column default of 0 — at the top, out of sequence.
        this.currentItem.sortOrder = this.totalRecords + 1;
      }
    }
    this.isEditMode = false;
    this.submitted = false;
    this.dataDialog = true;
  }

  editItem(item: any): void {
    this.currentItem = { ...item };
    this.isEditMode = true;
    this.submitted = false;
    this.dataDialog = true;
  }

  viewItem(item: any): void {
    this.currentItem = { ...item };
    this.viewDialog = true;
  }

  deleteItem(item: any): void {
    this.currentItem = { ...item };
    this.deleteDialog = true;
  }

  deleteSelectedItems(): void {
    this.deleteMultipleDialog = true;
  }

  /**
   * One toast, with the server's reason when it gave one. The writes are silent
   * now, so this is the only message: the server's sentence says which rule
   * refused it — still in use, a duplicate, a system code, a code too long for
   * its field (QA B15) — where the generic "failed to delete" said nothing.
   * A 5xx carries no reason worth reading, and keeps the page's own words.
   */
  private failed(messageKey: string, error: any): void {
    const fromServer = error?.status >= 400 && error?.status < 500 ? extractApiErrorMessage(error) : null;
    this.messageService.add({
      severity: 'error',
      summary: this.translationService.translate('COMMON.ERROR'),
      detail: fromServer || this.translationService.translate(messageKey)
    });
  }

  confirmDelete(): void {
    if (!this.selectedTableType) return;

    const itemId = this.currentItem[this.getIdField()];
    if (itemId === null || itemId === undefined) return;

    this.lookupTableService.delete(this.selectedTableType.id, itemId).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('LOOKUP_TABLES.MESSAGES.ITEM_DELETED_SUCCESS')
        });
        this.deleteDialog = false;
        this.invalidateOptionsForCurrentTable();
        this.loadData();
      },
      error: (error) => {
        console.error('Error deleting item:', error);
        // The answer is in: the dialog asked its question, so it closes either
        // way. Left open, it offered the same refused delete again (live QA #18).
        this.deleteDialog = false;
        this.failed('LOOKUP_TABLES.MESSAGES.FAILED_TO_DELETE', error);
      }
    });
  }

  confirmDeleteMultiple(): void {
    if (!this.selectedTableType || !this.selectedItems.length) return;

    const idField = this.getIdField();
    const ids = this.selectedItems.filter(item => this.canDelete(item)).map(item => item[idField]);
    if (!ids.length) return;

    this.lookupTableService.deleteMultiple(this.selectedTableType.id, ids).subscribe({
      next: () => {
        const successMessage = this.translationService.translate('LOOKUP_TABLES.MESSAGES.ITEMS_DELETED_SUCCESS').replace('{count}', ids.length.toString());
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: successMessage
        });
        this.deleteMultipleDialog = false;
        this.selectedItems = [];
        this.invalidateOptionsForCurrentTable();
        this.loadData();
      },
      error: (error) => {
        console.error('Error deleting items:', error);
        this.deleteMultipleDialog = false;
        this.failed('LOOKUP_TABLES.MESSAGES.FAILED_TO_DELETE_MULTIPLE', error);
      }
    });
  }

  /** A required field the user has not filled in, once Save has been pressed. */
  missing(field: LookupField): boolean {
    if (!field.required) return false;
    const value = this.currentItem?.[field.key];
    return this.submitted && (value === null || value === undefined || value === '');
  }

  saveItem(): void {
    this.submitted = true;

    if (!this.selectedTableType) return;
    if (this.formFields.some(field => this.missing(field))) return;

    const done = (messageKey: string) => {
      this.messageService.add({
        severity: 'success',
        summary: this.translationService.translate('COMMON.SUCCESS'),
        detail: this.translationService.translate(messageKey)
      });
      this.dataDialog = false;
      this.invalidateOptionsForCurrentTable();
      this.loadData();
    };

    if (this.isEditMode) {
      const itemId = this.currentItem[this.getIdField()];
      this.lookupTableService.update(this.selectedTableType.id, itemId, this.currentItem).subscribe({
        next: () => done('LOOKUP_TABLES.MESSAGES.ITEM_UPDATED_SUCCESS'),
        error: (error) => {
          console.error('Error updating item:', error);
          this.failed('LOOKUP_TABLES.MESSAGES.FAILED_TO_UPDATE', error);
        }
      });
    } else {
      this.lookupTableService.create(this.selectedTableType.id, this.currentItem).subscribe({
        next: () => {
          this.showNewestFirst();
          done('LOOKUP_TABLES.MESSAGES.ITEM_CREATED_SUCCESS');
        },
        error: (error) => {
          console.error('Error creating item:', error);
          this.failed('LOOKUP_TABLES.MESSAGES.FAILED_TO_CREATE', error);
        }
      });
    }
  }

  /**
   * The list sorted by id and reloaded its current page, so a new major landed on
   * page 45 of 45 and looked as if it had never been added (QA B15, CRUD [D]).
   * Newest first, from the top, with no search hiding it: the new row is the first.
   */
  private showNewestFirst(): void {
    this.sortField = this.getIdField();
    this.sortOrder = -1;
    this.first = 0;
    this.searchTerm = '';
    this.globalFilter = '';
  }

  hideDialog(): void {
    this.dataDialog = false;
    this.submitted = false;
    this.currentItem = {};
  }

  hideViewDialog(): void {
    this.viewDialog = false;
    this.currentItem = {};
  }

  /**
   * View → Edit. The record is copied before the view dialog closes: closing it
   * clears `currentItem`, so the editor used to open empty and Save sent
   * `PUT …/undefined`, answered "Parameter 'id' must be a valid Integer" (QA CRUD
   * cause [B], STD-003; live on all 14 tables).
   */
  editFromView(): void {
    const item = { ...this.currentItem };
    this.hideViewDialog();
    this.editItem(item);
  }

  hideDeleteDialog(): void {
    this.deleteDialog = false;
    this.currentItem = {};
  }

  hideDeleteMultipleDialog(): void {
    this.deleteMultipleDialog = false;
    this.selectedItems = [];
  }

  resetPagination(): void {
    this.first = 0;
    this.rows = 10;
    this.searchTerm = '';
    this.globalFilter = '';
    this.sortField = '';
    this.sortOrder = 1;
  }

  getFieldValue(item: any, field: string): any {
    const value = item?.[field];
    return value === null || value === undefined ? '' : value;
  }

  /** One row's value for one field, as the list and the view dialog show it. */
  displayValue(row: any, field: LookupField): string {
    switch (field.kind) {
      case 'ref':
        return this.refLabel(row, field) || this.translationService.translate('COMMON.NOT_AVAILABLE');
      case 'boolean':
      case 'system':
        return this.formatBoolean(row?.[field.key]);
      default: {
        const value = this.getFieldValue(row, field.key);
        return value === '' ? '' : String(value);
      }
    }
  }

  formatBoolean(value: any): string {
    return value ? this.translationService.translate('COMMON.YES') : this.translationService.translate('COMMON.NO');
  }
}
