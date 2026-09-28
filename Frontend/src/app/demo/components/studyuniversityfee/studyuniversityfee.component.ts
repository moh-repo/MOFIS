import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { StudyUniversityFee } from '../../api/studyuniversityfee';
import { StudyUniversityFeeService } from '../../service/studyuniversityfee.service';
import { LookupService } from '../../service/lookup.service';
import { StudentPickerService, StudentOption } from '../../service/student-picker.service';
import { MessageService, ConfirmationService } from 'primeng/api';
import { Table, TableLazyLoadEvent } from 'primeng/table';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { changesOnly } from '../../service/reload-streams.util';

@Component({
    templateUrl: './studyuniversityfee.component.html',
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudyUniversityFeeComponent implements OnInit, OnDestroy {
    @ViewChild('dt') table!: Table;

    fees: StudyUniversityFee[] = [];
    totalElements: number = 0;
    newFee: StudyUniversityFee = new StudyUniversityFee();
    creatingMode: boolean = true;
    loading: boolean = true;
    feeDialog: boolean = false;
    viewDialog: boolean = false;
    rowsPerPageOptions = [5, 10, 25, 50];
    first: number = 0;
    rows: number = 5;
    submitted: boolean = false;
    
    // Add filter properties
    globalFilter: string = '';
    statuses: any[] = [];
    selectedStatus: number | null = null;
    dateRange: Date[] = [];
    /** Server-side order of the list; the table is lazy, so sorting is the server's. */
    sortBy = 'id';
    sortDir: 'asc' | 'desc' = 'desc';
    /** Typing in the search box: one search after a pause, not one per key. */
    private readonly search$ = new Subject<void>();
    private searchSub?: Subscription;
    private languageSubscription?: any;
    currencies: any[] = [];
    feeSources: { label: string; value: number }[] = [];
    semesterScopeOptions: { label: string; value: number }[] = [];
    yearScopeOptions: { label: string; value: number }[] = [];
    /** 1 = student's name on check, 2 = university (direct payment) */
    checkPayorOptions: { label: string; value: number }[] = [];
    academicYearOptions: { label: string; value: string }[] = [];
    firstYearOptions: { label: string; value: string }[] = [];
    /** The academic years as pairs, before a row's own stored value is added. */
    private academicYearPairs: { label: string; value: string }[] = [];

    /**
     * The academic-years lookup holds single years ("2023"); fee rows store the
     * academic year as a pair ("2023/2024"). Offered as single years, no option
     * matched a stored value, so opening a row for edit showed the field empty and
     * saving it failed as "required" (live QA #9). The first-year field stores a
     * single year and keeps the lookup as it is.
     */
    static academicYearPairsFrom(years: { label: string; value: string }[]): { label: string; value: string }[] {
        return years
            .filter(y => /^\d{4}$/.test(y.value))
            .map(y => {
                const pair = `${y.value}/${Number(y.value) + 1}`;
                return { label: pair, value: pair };
            });
    }

    /** Keeps a stored value the list does not offer (older rows hold "2021" or a range), so edit never blanks it. */
    static withStoredValue(options: { label: string; value: string }[], stored: unknown): { label: string; value: string }[] {
        const value = stored == null ? '' : String(stored).trim();
        if (!value || options.some(o => o.value === value)) {
            return options;
        }
        return [{ label: value, value }, ...options];
    }
    semesterNumberOptions: { label: string; value: number }[] = [];

    // Student picker: /studentdata/options, the list the finance roles may read.
    filteredStudents: StudentOption[] = [];
    selectedStudent: StudentOption | null = null;

    constructor(
        private feeService: StudyUniversityFeeService,
        private lookupService: LookupService,
        private studentPicker: StudentPickerService,
        private messageService: MessageService,
        private confirmationService: ConfirmationService,
        public translationService: TranslationService,
        private codeLists: CodeListService
    ) { }

    ngOnInit() {
        this.loadLookupOptions();
        this.loadCurrencies();
        // The lazy table asks for the first page itself (onLazyLoad).
        this.searchSub = this.search$.pipe(debounceTime(400)).subscribe(() => {
            this.first = 0;
            this.loadFees();
        });

        this.languageSubscription = this.translationService.currentLanguage$.pipe(changesOnly()).subscribe(() => {
            this.loadLookupOptions();
            this.loadCurrencies();
        });
    }

    private loadLookupOptions(): void {
        this.lookupService.getUniversityFeeStatuses().subscribe({
            next: data => { this.statuses = data; },
            error: () => { this.statuses = []; }
        });
        this.lookupService.getFeeSources().subscribe({
            next: data => { this.feeSources = data; },
            error: () => { this.feeSources = []; }
        });
        this.lookupService.getFeeCheckPayors().subscribe({
            next: data => { this.checkPayorOptions = data; },
            error: () => { this.checkPayorOptions = []; }
        });
        this.lookupService.getFeeScopes('SEMESTER').subscribe({
            next: data => { this.semesterScopeOptions = data; },
            error: () => { this.semesterScopeOptions = []; }
        });
        this.lookupService.getFeeScopes('YEAR').subscribe({
            next: data => { this.yearScopeOptions = data; },
            error: () => { this.yearScopeOptions = []; }
        });
        this.lookupService.getAcademicYears().subscribe({
            next: data => {
                this.academicYearPairs = StudyUniversityFeeComponent.academicYearPairsFrom(data);
                this.academicYearOptions = StudyUniversityFeeComponent.withStoredValue(
                    this.academicYearPairs, this.newFee?.studyUniversityFeeYear);
                this.firstYearOptions = data;
            },
            error: () => {
                this.academicYearPairs = [];
                this.academicYearOptions = [];
                this.firstYearOptions = [];
            }
        });
        this.lookupService.getSemesterNumbers().subscribe({
            next: data => { this.semesterNumberOptions = data; },
            error: () => { this.semesterNumberOptions = []; }
        });
    }

    /**
     * Searched on the server. This read /studentdata/studentall (5000 rows), which is
     * behind the student permissions the finance roles do not hold, so their picker
     * was empty.
     */
    onStudentSearch(event: { query?: string }) {
        this.studentPicker.search(event?.query ?? '').subscribe(rows => (this.filteredStudents = rows));
    }

    onStudentSelect(student: StudentOption | null) {
        if (!student || student.studentId == null) {
            return;
        }
        this.selectedStudent = student;
        this.newFee.studentId = Number(student.studentId);
        this.newFee.studyUniversityFeeStudentName = (student.studentName || '').trim();
    }

    onStudentClear() {
        this.selectedStudent = null;
        this.newFee.studentId = undefined;
        this.newFee.studyUniversityFeeStudentName = '';
    }

    private syncSelectedStudentFromFee(fee: StudyUniversityFee) {
        const sid =
            fee.studentId != null && fee.studentId !== undefined
                ? Number(fee.studentId)
                : 0;
        this.selectedStudent =
            sid > 0 && (fee.studyUniversityFeeStudentName || '').trim()
                ? { studentId: sid, studentName: (fee.studyUniversityFeeStudentName || '').trim() }
                : null;
    }

    isStudentSelectionInvalid(): boolean {
        const idOk =
            this.newFee.studentId != null &&
            Number(this.newFee.studentId) > 0;
        const nameOk = !!(this.newFee.studyUniversityFeeStudentName || '').trim();
        return !idOk || !nameOk;
    }

    ngOnDestroy() {
        if (this.languageSubscription) {
            this.languageSubscription.unsubscribe();
        }
        this.searchSub?.unsubscribe();
    }

    /**
     * The net fee is not typed: it is the amount less the discount, which is what the
     * server stores whatever the form sends (normalizeFeeAmounts). An editable field
     * suggested otherwise (review F7).
     */
    get netFeeAmount(): number {
        const gross = Number(this.newFee?.studyUniversityFeeAmount) || 0;
        const discount = Number(this.newFee?.studyUniversityFeeDiscount) || 0;
        return Math.max(0, gross - discount);
    }

    isDiscountInvalid(): boolean {
        const discount = Number(this.newFee?.studyUniversityFeeDiscount) || 0;
        return discount < 0 || discount > (Number(this.newFee?.studyUniversityFeeAmount) || 0);
    }

    currencyLabel(code?: number | string | null): string {
        if (code == null || code === '' || code === 0) return '';
        const hit = this.currencies.find(c => String(c.code) === String(code));
        return hit ? hit.name : String(code);
    }

    feeSourceLabel(sourceId?: number): string {
        if (!sourceId || sourceId < 1) {
            return this.translationService.translate('STUDY_UNIVERSITY_FEE.SOURCE_NOT_SET');
        }
        const hit = this.feeSources.find((x) => x.value === sourceId);
        return hit ? hit.label : String(sourceId);
    }

    isFeeSourceInvalid(): boolean {
        const v = this.newFee.studyUniversityFeeSourceId;
        return v == null || v === undefined || Number(v) <= 0;
    }

    checkPayorLabel(payorId?: number): string {
        if (!payorId || payorId < 1 || payorId > 2) {
            return this.translationService.translate('STUDY_UNIVERSITY_FEE.CHECK_PAYOR_NOT_SET');
        }
        const hit = this.checkPayorOptions.find((x) => x.value === payorId);
        return hit ? hit.label : String(payorId);
    }

    isCheckPayorInvalid(): boolean {
        const v = Number(this.newFee.studyUniversityCheckPayorTypeId);
        return !Number.isFinite(v) || v < 1 || v > 2;
    }

    semesterScopeLabel(scopeId?: number): string {
        if (!scopeId || scopeId < 1) {
            return this.translationService.translate('STUDY_UNIVERSITY_FEE.SCOPE_NOT_SET');
        }
        const hit = this.semesterScopeOptions.find((x) => x.value === scopeId);
        return hit ? hit.label : String(scopeId);
    }

    yearScopeLabel(scopeId?: number): string {
        if (!scopeId || scopeId < 1) {
            return this.translationService.translate('STUDY_UNIVERSITY_FEE.SCOPE_NOT_SET');
        }
        const hit = this.yearScopeOptions.find((x) => x.value === scopeId);
        return hit ? hit.label : String(scopeId);
    }

    isSemesterScopeInvalid(): boolean {
        const v = this.newFee.studyUniversityFeeSemesterScopeId;
        return v == null || v === undefined || Number(v) <= 0;
    }

    isYearScopeInvalid(): boolean {
        const v = this.newFee.studyUniversityFeeYearScopeId;
        return v == null || v === undefined || Number(v) <= 0;
    }

    isAcademicYearInvalid(): boolean {
        const y = this.newFee.studyUniversityFeeYear;
        if (y === null || y === undefined || y === '') {
            return true;
        }
        return String(y).trim().length === 0;
    }

    isSemesterNumberInvalid(): boolean {
        const sn = this.newFee.studyUniversityFeeSemesterNumber;
        return sn == null || sn === undefined || Number(sn) < 1;
    }

    isPayStatusInvalid(): boolean {
        const v = this.newFee.studyUniversityFeePayStatus;
        return v === null || v === undefined || Number.isNaN(Number(v));
    }

    /** The semester's name from the lookup; 0 is "not by semester" and shows as a dash. */
    semesterLabel(value?: number | string | null): string {
        const n = Number(value);
        if (!Number.isFinite(n) || n <= 0) {
            return '—';
        }
        return this.semesterNumberOptions.find(o => o.value === n)?.label ?? String(n);
    }

    payStatusLabel(code?: number | string | null): string {
        const n =
            typeof code === 'number' && !Number.isNaN(code)
                ? code
                : this.coercePayStatus(code);
        const hit = this.statuses.find((s) => s.value === n);
        return hit ? hit.label : String(code ?? '');
    }

    loadCurrencies() {
        this.lookupService.getStudyCurrencies().subscribe({
            next: (data) => {
                this.currencies = (data || []).map(item => ({
                    name: item.name || item.label || '',
                    code: item.id ?? item.value
                }));
            },
            error: () => {
                // No invented list: the picker stays empty and the load error is the message.
                this.currencies = [];
            }
        });
    }

    loadFees() {
        this.loading = true;
        this.feeService.getPagingAll({
            page: (this.first / this.rows).toString(),
            size: this.rows.toString(),
            searchTerm: this.globalFilter,
            status: this.selectedStatus,
            startDate: this.dateRange?.[0] ? this.toIsoDate(this.dateRange[0]) : undefined,
            endDate: this.dateRange?.[1] ? this.toIsoDate(this.dateRange[1]) : undefined,
            sortBy: this.sortBy,
            sortDir: this.sortDir
        }).subscribe({
            next: (data) => {
                this.fees = Array.isArray(data) ? data : data['content'] || [];
                this.totalElements = Array.isArray(data) ? data.length : data['totalElements'] || 0;
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading fees:', error);
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDY_UNIVERSITY_FEE.FAILED_TO_LOAD_FEES')
                });
                this.loading = false;
            }
        });
    }

    onGlobalFilter(table: Table, event: Event) {
        this.globalFilter = (event.target as HTMLInputElement).value;
        this.search$.next();
    }

    onStatusFilter(event: any) {
        this.selectedStatus = event.value;
        this.first = 0;
        this.loadFees();
    }

    /**
     * A range picker selects one date per click and emits that date, not the range,
     * so this reads the bound model: search once both ends are chosen.
     */
    onDateFilter(_event?: unknown) {
        if (this.dateRange?.[0] && this.dateRange?.[1]) {
            this.first = 0;
            this.loadFees();
        }
    }

    clearFilters() {
        this.globalFilter = '';
        this.selectedStatus = null;
        this.dateRange = [];
        this.first = 0;
        if (this.table) {
            this.table.clear();
        }
        this.loadFees();
    }

    private toIsoDate(value: Date): string {
        const year = value.getFullYear();
        const month = String(value.getMonth() + 1).padStart(2, '0');
        const day = String(value.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    /** Page and sort changes from the lazy table, including its first page. */
    onLazyLoad(event: TableLazyLoadEvent) {
        this.first = event.first ?? 0;
        this.rows = event.rows ?? this.rows;
        const field = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
        this.sortBy = field || 'id';
        this.sortDir = field ? (event.sortOrder === -1 ? 'desc' : 'asc') : 'desc';
        this.loadFees();
    }

    openNew() {
        this.newFee = new StudyUniversityFee();
        this.newFee.studyUniversityFeePayStatus = 0;
        this.newFee.studyUniversityCheckPayorTypeId = 1;
        this.academicYearOptions = this.academicYearPairs;
        this.selectedStudent = null;
        this.creatingMode = true;
        this.submitted = false;
        this.feeDialog = true;
    }

    editFee(fee: StudyUniversityFee) {
        this.newFee = { ...fee };
        const payor = Number(this.newFee.studyUniversityCheckPayorTypeId);
        if (!Number.isFinite(payor) || payor < 1 || payor > 2) {
            this.newFee.studyUniversityCheckPayorTypeId = 1;
        }
        this.academicYearOptions = StudyUniversityFeeComponent.withStoredValue(
            this.academicYearPairs, this.newFee.studyUniversityFeeYear);
        this.syncSelectedStudentFromFee(this.newFee);
        this.creatingMode = false;
        this.feeDialog = true;
    }

    viewFee(fee: StudyUniversityFee) {
        this.newFee = { ...fee };
        const payor = Number(this.newFee.studyUniversityCheckPayorTypeId);
        if (!Number.isFinite(payor) || payor < 1 || payor > 2) {
            this.newFee.studyUniversityCheckPayorTypeId = 1;
        }
        this.viewDialog = true;
    }

    hideDialog() {
        this.feeDialog = false;
        this.viewDialog = false;
        this.submitted = false;
        this.selectedStudent = null;
    }

    private resolveBackendErrorMessage(error: unknown): string {
        const payload = (error as { error?: Record<string, unknown> | string | null })?.error;
        if (payload == null) {
            return '';
        }
        if (typeof payload === 'string') {
            return payload;
        }
        const msg = payload['message'];
        if (typeof msg === 'string') {
            return msg;
        }
        const nested = payload['error'];
        if (typeof nested === 'string') {
            return nested;
        }
        return '';
    }

    /** Ensure lookup scope IDs and ids are serialized as numbers for the API JSON body. */
    private buildFeePayload(): StudyUniversityFee {
        const p = { ...this.newFee };
        const n = (v: unknown): number =>
            v === null || v === undefined || v === ''
                ? 0
                : Number(v);
        p.studentId =
            this.newFee.studentId != null ? Number(this.newFee.studentId) : undefined;
        p.studyUniversityFeeYear =
            this.newFee.studyUniversityFeeYear != null
                ? String(this.newFee.studyUniversityFeeYear).trim()
                : '';
        p.studyUniversityFeeSourceId = n(this.newFee.studyUniversityFeeSourceId);
        p.studyUniversityFeeSemesterScopeId = n(
            this.newFee.studyUniversityFeeSemesterScopeId
        );
        p.studyUniversityFeeYearScopeId = n(this.newFee.studyUniversityFeeYearScopeId);
        p.studyUniversityFeeSemesterNumber = n(this.newFee.studyUniversityFeeSemesterNumber);
        p.studyUniversityFeePayStatus = this.coercePayStatus(this.newFee.studyUniversityFeePayStatus);
        p.studyUniversityRealFeeAmount = this.netFeeAmount;
        p.studyUniversityCheckPayorTypeId = n(this.newFee.studyUniversityCheckPayorTypeId);
        if (this.creatingMode) {
            delete (p as { id?: number }).id;
        }
        return p;
    }

    /**
     * A pay status as a number. Legacy rows carried the status name instead
     * of its code; those are matched against the university-fee-status list
     * (by code or by either name) rather than against a map kept here.
     */
    private coercePayStatus(v: unknown): number {
        if (typeof v === 'number' && !Number.isNaN(v)) {
            return v;
        }
        const key = String(v ?? '').trim().toUpperCase();
        if (key === '') {
            return 0;
        }
        const row = this.codeLists.rows('university-fee-status').find(r =>
            String(r.value).toUpperCase() === key
            || (r.labelEn || '').toUpperCase() === key
            || (r.labelAr || '').toUpperCase() === key);
        const n = row ? Number(row.value) : Number(key);
        return Number.isNaN(n) ? 0 : n;
    }

    saveFee() {
        this.submitted = true;

        if (this.isStudentSelectionInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate(
                    'STUDY_UNIVERSITY_FEE.STUDENT_SELECTION_REQUIRED'
                )
            });
            return;
        }

        if (this.isFeeSourceInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate('STUDY_UNIVERSITY_FEE.SOURCE_REQUIRED')
            });
            return;
        }

        if (this.isSemesterScopeInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate(
                    'STUDY_UNIVERSITY_FEE.SEMESTER_SCOPE_REQUIRED'
                )
            });
            return;
        }

        if (this.isYearScopeInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate(
                    'STUDY_UNIVERSITY_FEE.YEAR_SCOPE_REQUIRED'
                )
            });
            return;
        }

        if (this.isAcademicYearInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate(
                    'STUDY_UNIVERSITY_FEE.ACADEMIC_YEAR_REQUIRED'
                )
            });
            return;
        }

        if (this.isSemesterNumberInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate(
                    'STUDY_UNIVERSITY_FEE.SEMESTER_NUMBER_REQUIRED'
                )
            });
            return;
        }

        if (this.isCheckPayorInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate(
                    'STUDY_UNIVERSITY_FEE.CHECK_PAYOR_REQUIRED'
                )
            });
            return;
        }

        if (!(Number(this.newFee.studyUniversityFeeAmount) > 0) || this.isDiscountInvalid()) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('COMMON.ERROR'),
                detail: this.translationService.translate(
                    this.isDiscountInvalid() ? 'STUDY_UNIVERSITY_FEE.DISCOUNT_INVALID' : 'STUDY_UNIVERSITY_FEE.AMOUNT_REQUIRED'
                )
            });
            return;
        }

        if (this.creatingMode) {
            this.createFee();
        } else {
            this.modifyFee();
        }
    }

    createFee() {
        this.feeService.Create(this.buildFeePayload()).subscribe({
            next: (response: any) => {
                const detail = (response?.status === 'PENDING' && response?.message)
                    ? response.message
                    : this.translationService.translate('STUDY_UNIVERSITY_FEE.FEE_CREATED_SUCCESS');
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail
                });
                this.feeDialog = false;
                this.selectedStudent = null;
                this.loadFees();
            },
            error: (error) => {
                const msg =
                    this.resolveBackendErrorMessage(error) ||
                    this.translationService.translate('STUDY_UNIVERSITY_FEE.FAILED_TO_CREATE_FEE');
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: msg
                });
            }
        });
    }

    modifyFee() {
        this.feeService.Update(this.buildFeePayload()).subscribe({
            next: (response: any) => {
                const detail = (response?.status === 'PENDING' && response?.message)
                    ? response.message
                    : this.translationService.translate('STUDY_UNIVERSITY_FEE.FEE_UPDATED_SUCCESS');
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail
                });
                this.feeDialog = false;
                this.selectedStudent = null;
                this.loadFees();
            },
            error: (error) => {
                const msg =
                    this.resolveBackendErrorMessage(error) ||
                    this.translationService.translate('STUDY_UNIVERSITY_FEE.FAILED_TO_UPDATE_FEE');
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: msg
                });
            }
        });
    }

    deleteFee(fee: StudyUniversityFee) {
        this.confirmationService.confirm({
            message: this.translationService.translate('STUDY_UNIVERSITY_FEE.CONFIRM_DELETE_FEE'),
            header: this.translationService.translate('COMMON.CONFIRM'),
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
                this.feeService.Delete(fee.id!).subscribe({
                    next: (response: any) => {
                        const detail = (response?.status === 'PENDING' && response?.message)
                            ? response.message
                            : this.translationService.translate('STUDY_UNIVERSITY_FEE.FEE_DELETED');
                        this.messageService.add({ 
                            severity: 'success', 
                            summary: this.translationService.translate('COMMON.SUCCESS'), 
                            detail, 
                            life: 3000 
                        });
                        this.loadFees();
                    },
                    error: (error) => {
                        console.error('Error deleting fee:', error);
                        this.messageService.add({ 
                            severity: 'error', 
                            summary: this.translationService.translate('COMMON.ERROR'), 
                            detail: this.translationService.translate('STUDY_UNIVERSITY_FEE.ERROR_DELETING_FEE') 
                        });
                    }
                });
            }
        });
    }

    /** The status row's own tag colour (university-fee-status.UI_SEVERITY). */
    getSeverity(status: number | string | null | undefined) {
        const n = typeof status === 'number' ? status : this.coercePayStatus(status);
        return this.codeLists.severity('university-fee-status', n);
    }
} 