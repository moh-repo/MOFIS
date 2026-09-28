import { Component, OnInit, ViewChild } from '@angular/core';
import { Table } from 'primeng/table';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of, Observable, Subject } from 'rxjs';
import { catchError, switchMap, map, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MessageService, ConfirmationService } from 'primeng/api';
import { StudyFinReceivedWhom } from '../../api/study-fin-received-whom';
import { Studentdata } from '../../api/studentdata';
import { FinanceReceivedWhomService, FinRecipientDocument } from '../../service/finance-received-whom.service';
import { DocumentTypeService } from '../../service/document-type.service';
import { DocumentType } from '../../api/document-type';
import { LookupItem, LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { StudyCountryIn } from '../../api/study-country-in';
import { StudyGovIn } from '../../api/study-gov-in';
import { StudyFinanceBank } from '../../api/study-finance-bank';
import { StudyFinanceBankBranch } from '../../api/study-finance-bank-branch';
import { StudyFinanceDof } from '../../api/study-finance-dof';
import { StudyFinReceivedStatus } from '../../api/study-fin-received-status';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { StudentPickerService } from '../../service/student-picker.service';
import { ApprovalService, ApprovalSummary } from '../../service/approval.service';
import { environment } from '../../../../environments/environment';

@Component({
    templateUrl: './finance-received-whom.component.html',
    styleUrls: ['./finance-received-whom.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class FinanceReceivedWhomComponent implements OnInit {

    records: StudyFinReceivedWhom[] = [];
    loading = false;
    saving = false;

    /* Filters. The whole list is already in memory — loadAll() returns an array
       and the table paginates it client-side — so these narrow what is on
       screen without another round trip. Held here rather than read back off
       the table so Clear knows whether there is anything to clear. */
    @ViewChild('dt') table?: Table;
    filterText = '';
    filterStatus: number | null = null;

    formVisible = false;
    viewVisible = false;
    formMode: 'create' | 'edit' = 'create';
    form: StudyFinReceivedWhom = new StudyFinReceivedWhom();
    viewRecord: StudyFinReceivedWhom | null = null;

    /** An undecided approval request for the record being edited; blocks a second one. */
    pendingApproval: ApprovalSummary | null = null;
    viewStudentData: Studentdata | null = null;
    loadingViewStudent = false;

    // Documents (per recipient, shown in the view and edit dialogs)
    documents: FinRecipientDocument[] = [];
    loadingDocuments = false;
    uploadingDocument = false;
    /** Owner id of the currently displayed document list (view record or edit form). */
    private docsOwnerId: number | null = null;

    // Document types (from the DB lookup) + files staged in the add/edit dialog
    documentTypes: DocumentType[] = [];
    pendingUploads: { file: File; documentType: string }[] = [];

    /** Optional: when set, after saving this recipient the StudentFinance record with this ID will be linked. */
    linkedStudentFinanceId: number | null = null;

    // Student lookup — precomputed once, never recomputed in a getter
    students: Studentdata[] = [];
    studentsForDropdown: { id: number; label: string }[] = [];
    /** Typing in the picker's filter box; searched on the server, debounced. */
    private readonly studentSearch$ = new Subject<string>();

    studyCountries: StudyCountryIn[] = [];        // finance-scope filtered (finance country dropdowns)
    allStudyCountries: StudyCountryIn[] = [];     // full list (passport issue country + name resolution)
    studyGovernments: StudyGovIn[] = [];   // filtered by selected country in form
    studyFinanceBanks: StudyFinanceBank[] = [];      // filtered by selected country in form
    studyFinanceBankBranches: StudyFinanceBankBranch[] = []; // filtered by selected bank in form

    studyFinanceDofs: StudyFinanceDof[] = [];
    studyFinReceivedStatuses: StudyFinReceivedStatus[] = [];

    /* Reference lists that used to be typed by hand into a text box.
       Both are DB-backed (LU_FAMILY_RELATIONSHIP, FIN_RECEIVED_REASON) and both
       carry a stable code as their value, which is what gets stored. */
    relationshipOptions: { label: string; value: string }[] = [];
    reasonOptions: { label: string; value: string }[] = [];
    /** STUDY_WAFAR_TYPE — used to name the wafar a student's money is held against. */
    wafarTypes: LookupItem[] = [];

    /* ── The selected student's own finance record ────────────────────────────
       STUDENT_FINANCE is keyed by STUDENT_ID: one row per student, holding what
       they are entitled to, what is held back as wafar, and the net that may
       actually be handed over. Loaded when a student is picked so the dialog
       stops being a blank amount box. */
    studentFinance: any | null = null;
    studentWafar: any | null = null;
    loadingStudentFinance = false;
    /** True once a lookup came back with no finance row for the chosen student. */
    noFinanceRecord = false;

    // Full lists for view-dialog name resolution
    private allStudyGovernments: StudyGovIn[] = [];
    private allStudyFinanceBanks: StudyFinanceBank[] = [];
    private allStudyFinanceBankBranches: StudyFinanceBankBranch[] = [];

    constructor(
        private service: FinanceReceivedWhomService,
        private documentTypeService: DocumentTypeService,
        private lookupService: LookupService,
        private messageService: MessageService,
        private confirmationService: ConfirmationService,
        public translationService: TranslationService,
        private httpClient: HttpClient,
        private permissionService: UmsPermissionService,
        private studentPicker: StudentPickerService,
        private approvalService: ApprovalService
    ) {}

    ngOnInit(): void {
        this.loadLookups();
        this.loadAll();
        this.loadStudents();
        this.loadDocumentTypes();
        this.loadReferenceLists();
        this.studentSearch$.pipe(
            debounceTime(300),
            distinctUntilChanged(),
            switchMap(term => this.studentPicker.search(term))
        ).subscribe(rows => this.mergeStudents(rows, true));
    }

    /**
     * Relationship, reason and wafar type — three lists the database already
     * holds and the form used to make the user type or the code to guess.
     */
    private loadReferenceLists(): void {
        this.lookupService.getLuFamilyRelationshipsForUi()
            .subscribe({ next: d => this.relationshipOptions = d || [], error: () => {} });
        this.lookupService.getFinReceivedReasonsForUi()
            .subscribe({ next: d => this.reasonOptions = d || [], error: () => {} });
        this.lookupService.getWafarTypes()
            .subscribe({ next: d => this.wafarTypes = d || [], error: () => {} });
    }

    private loadDocumentTypes(): void {
        this.documentTypeService.getAllActiveDocumentTypes().subscribe({
            next: (data) => { this.documentTypes = data || []; },
            error: () => { this.documentTypes = []; }
        });
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * The student picker's first page.
     *
     * <p>This used to ask `/api/v1/studentdata/?page=0&size=5000` and read
     * `res.content`. That endpoint returns a bare array and ignores paging
     * entirely, so `res.content` was always undefined and the dropdown was empty
     * for everybody — and even with the right URL, the student list is gated on
     * `STUDENT_PII`, which none of the three finance roles hold. The picker now
     * reads `/studentdata/options`, which answers with an id and a name and
     * nothing else and is open to finance.
     */
    private loadStudents(): void {
        this.studentPicker.search().subscribe(rows => this.mergeStudents(rows, true));
    }

    /**
     * Folds a page of options into the picker.
     *
     * <p>`replace` swaps the visible list — a new search — while the students
     * already referenced by the form or the table are kept regardless, because a
     * `p-select` renders the label of its selected value only if that value is
     * still among its options.
     */
    private mergeStudents(rows: any[], replace = false): void {
        this.students = replace
            ? this.studentPicker.merge(this.students as any, rows, this.studentsInUse()) as any
            : this.studentPicker.mergeAll(this.students as any, rows) as any;
        this.studentsForDropdown = this.studentPicker.toOptions(this.students as any);
    }

    /** The students named by the open form, the open view, or a row on screen. */
    private studentsInUse(): number[] {
        return [
            this.form.studentForDelegationId,
            this.viewRecord?.studentForDelegationId ?? 0,
            ...this.records.map(r => r.studentForDelegationId)
        ].filter(id => !!id && id > 0);
    }

    /** Resolves the names for the students the loaded records refer to. */
    private loadStudentsOfRecords(): void {
        const ids = Array.from(new Set(
            this.records.map(r => r.studentForDelegationId).filter(id => !!id && id > 0)
        )).filter(id => !this.students.some(s => s.studentId === id));
        if (ids.length === 0) return;
        this.studentPicker.byIds(ids).subscribe(rows => this.mergeStudents(rows));
    }

    /**
     * Searches the picker on the server as the user types.
     *
     * <p>Only one page of students is held at a time, so filtering in the browser
     * could only ever search that page. The term goes to the same endpoint, and
     * the request is debounced and switched so a slow earlier answer cannot
     * overwrite a later one.
     */
    onStudentFilter(event: { filter: string }): void {
        this.studentSearch$.next(event?.filter ?? '');
    }

    private loadLookups(): void {
        forkJoin({
            countries:   this.lookupService.getAllStudyCountries().pipe(catchError(() => of([]))),
            dofs:        this.lookupService.getAllStudyFinanceDofs().pipe(catchError(() => of([]))),
            statuses:    this.lookupService.getAllStudyFinReceivedStatuses().pipe(catchError(() => of([]))),
            governments: this.lookupService.getAllStudyGovernments().pipe(catchError(() => of([]))),
            banks:       this.lookupService.getAllStudyFinanceBanks().pipe(catchError(() => of([]))),
            branches:    this.lookupService.getAllStudyFinanceBankBranches().pipe(catchError(() => of([])))
        }).subscribe(res => {
            this.allStudyCountries = res.countries || [];
            const allowedIds = this.permissionService.getAllowedCountryIds();
            this.studyCountries = allowedIds.length > 0
              ? this.allStudyCountries.filter((c: StudyCountryIn) => allowedIds.includes(c.id))
              : this.allStudyCountries;
            this.studyFinanceDofs          = res.dofs;
            this.studyFinReceivedStatuses  = res.statuses;
            this.allStudyGovernments       = res.governments;
            this.allStudyFinanceBanks      = res.banks;
            this.allStudyFinanceBankBranches = res.branches;
        });
    }

    loadAll(): void {
        this.loading = true;
        this.service.getAll().subscribe({
            next: data => {
                this.records = data;
                this.loading = false;
                // The list shows a student's name per row, and the picker holds one
                // page — so the names on screen are fetched by id, not hoped for.
                this.loadStudentsOfRecords();
            },
            error: () => {
                this.loading = false;
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.LOAD_ERROR') });
            }
        });
    }

    // ── Dialog open/close ─────────────────────────────────────────────────────

    /**
     * FIN_RECIEVED_YEAR_MONTH is YYYYMM — `YEAR * 100 + MONTH`, which is what
     * every row in the table holds (202401, 202403, 202312). It was computed as
     * `YEAR * 10 + MONTH` here, so January 2026 was written as 20261 and no
     * record saved through this screen could be read back by that key. The YYYYQ
     * rule the old comment cited belongs to STUDENT_FIN_RECIEVED_YEAR_MONTH on
     * the finance tables, which is a quarter and a different column.
     */
    syncYearMonth(): void {
        const y = Number(this.form.finRecievedYear) || 0;
        const m = Number(this.form.finRecievedMonth) || 0;
        this.form.finRecievedYearMonth = y > 0 && m > 0 ? y * 100 + m : 0;
    }

    resetForm(): void {
        this.form = new StudyFinReceivedWhom();
        this.studyGovernments = [];
        this.studyFinanceBanks = [];
        this.studyFinanceBankBranches = [];
        this.pendingUploads = [];
        this.documents = [];
        this.clearStudentFinance();
    }

    openNew(): void {
        this.form = new StudyFinReceivedWhom();
        this.studyGovernments = [];
        this.studyFinanceBanks = [];
        this.studyFinanceBankBranches = [];
        this.formMode = 'create';
        this.pendingApproval = null;
        this.linkedStudentFinanceId = null;
        this.pendingUploads = [];
        this.documents = [];
        this.clearStudentFinance();
        this.formVisible = true;
    }

    /**
     * The workflow refuses a second request while one is undecided, so the form
     * is locked rather than letting the user fill it in and be rejected on save.
     *
     * The endpoint sits behind the APPROVALS permission; a user who cannot read
     * it keeps the old behaviour and finds out at save time instead of being
     * locked out of their own screen.
     */
    private checkPendingApproval(recordId: number | null | undefined): void {
        this.pendingApproval = null;
        if (!recordId || recordId <= 0) return;
        this.approvalService.getPendingForEntity('STUDY_FIN_RECEIVED_WHOM', recordId)
            .pipe(catchError(() => of([] as ApprovalSummary[])))
            .subscribe(requests => { this.pendingApproval = requests?.length ? requests[0] : null; });
    }

    openEdit(record: StudyFinReceivedWhom): void {
        this.form = { ...record };
        this.formMode = 'edit';
        this.checkPendingApproval(record.id);
        this.linkedStudentFinanceId = null;
        this.studyGovernments = [];
        this.studyFinanceBanks = [];
        this.studyFinanceBankBranches = [];
        this.pendingUploads = [];
        this.formVisible = true;
        // Show the recipient's existing documents so they can be managed while editing.
        this.loadDocuments(record.id);
        // Read the student's finance record so the wafar split is on screen and the
        // save guard has something to check — but prefill nothing: this row's stored
        // amount and bank are the record, and reopening it must not rewrite them.
        this.clearStudentFinance();
        this.loadStudentFinance(record.studentForDelegationId, false);
        // Pre-filter cascading dropdowns from the preloaded full lists
        if (record.financeCountryId) {
            if (this.allStudyGovernments.length) {
                this.studyGovernments = this.allStudyGovernments.filter(g => g.studyCountryInId === record.financeCountryId);
            } else {
                this.lookupService.getStudyGovernmentsByCountry(record.financeCountryId)
                    .subscribe({ next: d => this.studyGovernments = d, error: () => {} });
            }
            if (this.allStudyFinanceBanks.length) {
                this.studyFinanceBanks = this.allStudyFinanceBanks.filter(b => b.studyCountryInId === record.financeCountryId);
            } else {
                this.lookupService.getStudyFinanceBanksByCountry(record.financeCountryId)
                    .subscribe({ next: d => this.studyFinanceBanks = d, error: () => {} });
            }
        }
        if (record.financeBankeId) {
            if (this.allStudyFinanceBankBranches.length) {
                this.studyFinanceBankBranches = this.allStudyFinanceBankBranches.filter(b => b.studyFinanceBankeId === record.financeBankeId);
            } else {
                this.lookupService.getStudyFinanceBankBranchesByBank(record.financeBankeId)
                    .subscribe({ next: d => this.studyFinanceBankBranches = d, error: () => {} });
            }
        }
    }

    openView(record: StudyFinReceivedWhom): void {
        this.viewRecord = record;
        this.viewStudentData = null;
        this.viewVisible = true;
        this.loadDocuments(record.id);
        if (record.studentForDelegationId && record.studentForDelegationId > 0) {
            this.loadingViewStudent = true;
            this.httpClient.get<Studentdata>(`${environment.apiUrl}/mohe/api/v1/studentdata/${record.studentForDelegationId}`).subscribe({
                next: (s) => { this.viewStudentData = s; this.loadingViewStudent = false; },
                error: () => { this.loadingViewStudent = false; }
            });
        }
    }

    // ── The student's finance record ──────────────────────────────────────────

    private clearStudentFinance(): void {
        this.studentFinance = null;
        this.studentWafar = null;
        this.noFinanceRecord = false;
        this.loadingStudentFinance = false;
    }

    /** Fired by the student dropdown: pick a student, get that student's money. */
    onStudentChange(): void {
        this.clearStudentFinance();
        this.loadStudentFinance(this.form.studentForDelegationId, true);
    }

    /**
     * Reads STUDENT_FINANCE and STUDENT_FINANCE_WAFAR for one student.
     *
     * <p>Both are keyed by STUDENT_ID and both 404 when the student has no such
     * row. That is an ordinary answer, not a failure: a student with no finance
     * record simply leaves the form as the user found it, and the dialog says so
     * rather than showing an error the user cannot act on.
     *
     * <p>With {@code prefill}, the recipient's amount and banking details are
     * filled from that record. Year and month are deliberately left alone: the
     * two tables count time differently — STUDENT_FINANCE holds year and
     * *quarter* (2024, 2 → 20242, on 939 of its 941 rows) while this table holds
     * year and *month* (2024, 3 → 202403) — so copying one into the other would
     * file a March payment as the second quarter.
     */
    private loadStudentFinance(studentId: number, prefill: boolean): void {
        if (!studentId || studentId <= 0) return;
        const base = `${environment.apiUrl}/mohe/api/v1/studentFinance/${studentId}`;
        this.loadingStudentFinance = true;
        forkJoin({
            finance: this.httpClient.get<any>(base).pipe(catchError(() => of(null))),
            wafar: this.httpClient.get<any>(`${base}/wafar`).pipe(catchError(() => of(null)))
        }).subscribe(res => {
            this.loadingStudentFinance = false;
            this.studentFinance = res.finance;
            this.studentWafar = res.wafar;
            this.noFinanceRecord = !res.finance;
            if (res.finance && prefill) {
                this.applyFinancePrefill(res.finance);
            }
        });
    }

    /**
     * Copies the student's finance record into the recipient form.
     *
     * <p>The amount is the *net* — what is left after wafar — because that is the
     * only figure this screen is entitled to hand over. The country cascade is
     * driven through the existing onCountryChange/onBankChange so the dependent
     * dropdowns hold the right options, then the stored ids are restored on top;
     * those handlers clear their children by design.
     */
    private applyFinancePrefill(finance: any): void {
        this.form.finRecievedAmount = this.payableAmount;
        const countryId = Number(finance.studyCountryInId) || 0;
        if (countryId) {
            this.form.financeCountryId = countryId;
            this.onCountryChange();
            this.form.financeGovId = Number(finance.studyGovInId) || 0;
            const bankId = Number(finance.studyFinanceBankeId) || 0;
            if (bankId) {
                this.form.financeBankeId = bankId;
                this.onBankChange();
                this.form.financeBankBranchId = Number(finance.studyFinanceBankBranchId) || 0;
            }
        }
        this.form.financeDofId = Number(finance.studyFinanceDofId) || this.form.financeDofId;
    }

    /** What the student is entitled to for the quarter, before wafar. */
    get entitlementAmount(): number {
        return Number(this.studentFinance?.studentFinRecievedAmount) || 0;
    }

    /** The part of it held back as wafar. */
    get wafarAmount(): number {
        return Number(this.studentFinance?.wafarAmount) || 0;
    }

    /**
     * What may actually be handed to the recipient.
     *
     * <p>NET_AMOUNT is a computed column in the database (amount − wafar,
     * PERSISTED), so it is read and never sent. The subtraction here is only a
     * fallback for a row read before that column existed.
     */
    get payableAmount(): number {
        const net = this.studentFinance?.netAmount;
        if (net !== null && net !== undefined) return Number(net) || 0;
        return Math.max(0, this.entitlementAmount - this.wafarAmount);
    }

    get hasWafar(): boolean {
        return !!this.studentFinance && this.wafarAmount > 0;
    }

    /** Which wafar the money is held against — STUDY_WAFAR_TYPE, not a guess. */
    get wafarTypeName(): string {
        const id = Number(this.studentFinance?.wafarTypeId ?? this.studentWafar?.wafarTypeId) || 0;
        if (!id) return '';
        return this.wafarTypes.find(t => t.id === id)?.name ?? String(id);
    }

    /** The quarter the finance record belongs to, shown as context, never copied. */
    get financeQuarterLabel(): string {
        const f = this.studentFinance;
        if (!f?.studentFinRecievedYear) return '';
        return `${f.studentFinRecievedYear} / Q${f.studentFinRecievedMonth ?? '—'}`;
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    loadDocuments(whomId: number): void {
        this.documents = [];
        this.docsOwnerId = whomId || null;
        if (!whomId) return;
        this.loadingDocuments = true;
        this.service.getDocuments(whomId).subscribe({
            next: docs => { this.documents = docs; this.loadingDocuments = false; },
            error: () => { this.loadingDocuments = false; }
        });
    }

    /** Uploads a single file straight away — used by the view dialog (record already exists). */
    onDocumentSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file || !this.viewRecord) return;
        this.uploadingDocument = true;
        this.service.uploadDocument(this.viewRecord.id, file).subscribe({
            next: () => {
                this.uploadingDocument = false;
                this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('FIN_RECEIVED_WHOM.DOC_UPLOAD_SUCCESS') });
                this.loadDocuments(this.viewRecord!.id);
            },
            error: () => {
                this.uploadingDocument = false;
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('FIN_RECEIVED_WHOM.DOC_UPLOAD_ERROR') });
            }
        });
        input.value = ''; // allow re-selecting the same file
    }

    // ── Staged uploads for the add/edit dialog ────────────────────────────────

    /** Stages picked files (with an empty type) to be uploaded once the record is saved. */
    onFormDocumentsSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        const files = input.files;
        if (files) {
            for (let i = 0; i < files.length; i++) {
                this.pendingUploads.push({ file: files[i], documentType: '' });
            }
        }
        input.value = ''; // allow re-selecting the same file
    }

    removePendingUpload(index: number): void {
        this.pendingUploads.splice(index, 1);
    }

    /** Uploads every staged file for the saved recipient; per-file errors are tolerated. */
    private uploadPendingDocuments(whomId: number | null): Observable<any[]> {
        if (!whomId || this.pendingUploads.length === 0) {
            return of([]);
        }
        const calls = this.pendingUploads.map(p =>
            this.service.uploadDocument(whomId, p.file, undefined, p.documentType || undefined)
                .pipe(catchError(() => of(null)))
        );
        return forkJoin(calls);
    }

    viewDocument(doc: FinRecipientDocument): void {
        this.service.viewDocument(doc.documentId).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                window.open(url, '_blank');
                setTimeout(() => URL.revokeObjectURL(url), 60000);
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.LOAD_ERROR') })
        });
    }

    downloadDocument(doc: FinRecipientDocument): void {
        this.service.downloadDocument(doc.documentId).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = doc.originalFilename || doc.documentName || 'document';
                a.click();
                URL.revokeObjectURL(url);
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.LOAD_ERROR') })
        });
    }

    confirmDeleteDocument(doc: FinRecipientDocument): void {
        this.confirmationService.confirm({
            message: this.t('FIN_RECEIVED_WHOM.DOC_DELETE_CONFIRM_MSG'),
            header: this.t('FIN_RECEIVED_WHOM.DOC_DELETE_CONFIRM'),
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
                this.service.deleteDocument(doc.documentId).subscribe({
                    next: () => {
                        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('COMMON.DELETE') });
                        if (this.docsOwnerId) this.loadDocuments(this.docsOwnerId);
                    },
                    error: () => this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.LOAD_ERROR') })
                });
            }
        });
    }

    getDocumentIcon(doc: FinRecipientDocument): string {
        const name = (doc.originalFilename || doc.filePath || '').toLowerCase();
        const ext = name.substring(name.lastIndexOf('.') + 1);
        if (ext === 'pdf') return 'pi pi-file-pdf text-red-500';
        if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) return 'pi pi-image text-blue-500';
        if (['doc', 'docx'].includes(ext)) return 'pi pi-file-word text-blue-700';
        return 'pi pi-file text-500';
    }

    formatFileSize(bytes: number): string {
        if (!bytes || bytes <= 0) return '—';
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    // ── Cascading country/bank dropdowns ──────────────────────────────────────

    onCountryChange(): void {
        const id = this.form.financeCountryId;
        if (id) {
            this.studyGovernments = this.allStudyGovernments.filter(g => g.studyCountryInId === id);
            this.studyFinanceBanks = this.allStudyFinanceBanks.filter(b => b.studyCountryInId === id);
            this.form.financeGovId = 0;
            this.form.financeBankeId = 0;
            this.form.financeBankBranchId = 0;
            this.studyFinanceBankBranches = [];
        } else {
            this.studyGovernments = [];
            this.studyFinanceBanks = [];
            this.studyFinanceBankBranches = [];
        }
    }

    onBankChange(): void {
        const id = this.form.financeBankeId;
        if (id) {
            this.studyFinanceBankBranches = this.allStudyFinanceBankBranches.filter(b => b.studyFinanceBankeId === id);
            this.form.financeBankBranchId = 0;
        } else {
            this.studyFinanceBankBranches = [];
        }
    }

    // ── Save / Delete ─────────────────────────────────────────────────────────

    save(): void {
        if (this.pendingApproval) {
            this.messageService.add({
                severity: 'warn',
                summary: this.t('FINANCE.PENDING_APPROVAL_TITLE'),
                detail: this.t('FINANCE.PENDING_APPROVAL_MESSAGE'),
                life: 8000
            });
            return;
        }
        if (!this.form.finReceivedName?.trim()) {
            this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.REQUIRED_FIELD') });
            return;
        }
        // The attaché is what scopes the record. Saved without one it is stored
        // under country 0, which no attaché's staff can ever see — the server
        // refuses it, and this says so in the dialog rather than as a failed save.
        if (!this.form.financeCountryId) {
            this.messageService.add({
                severity: 'error', summary: this.t('COMMON.ERROR'),
                detail: this.t('FIN_RECEIVED_WHOM.COUNTRY_REQUIRED')
            });
            return;
        }
        // Wafar goes to wafar. When the student's finance record holds an amount
        // back, only the net is payable to whoever collects it, and the screen
        // that records the handover is the one that has to say no — the wafar
        // portion is already booked in STUDENT_FINANCE_WAFAR. Recipient 1 was
        // handed the 500 that belonged to wafar; recipient 4 collected 900
        // against a net of zero. Neither could be saved through this now.
        if (this.studentFinance && Number(this.form.finRecievedAmount || 0) > this.payableAmount) {
            this.messageService.add({
                severity: 'error', summary: this.t('COMMON.ERROR'),
                detail: this.tp('FIN_RECEIVED_WHOM.ABOVE_NET', {
                    payable: this.payableAmount.toLocaleString(),
                    wafar: this.wafarAmount.toLocaleString(),
                    type: this.wafarTypeName || this.t('FIN_RECEIVED_WHOM.WAFAR_HELD')
                })
            });
            return;
        }
        this.saving = true;
        const pendingCount = this.pendingUploads.length;
        const wasCreate = this.formMode === 'create';
        const op = wasCreate
            ? this.service.create(this.form)
            : this.service.update(this.form.id, this.form);

        /*
         * A new recipient has no id until an approver signs the request off, so
         * there is nothing for staged files to belong to yet. An edit is a record
         * that already exists, and its documents are outside the workflow — they
         * still upload immediately.
         */
        op.pipe(
            switchMap((saved: any) => {
                const recordId = wasCreate ? null : this.form.id;
                return this.uploadPendingDocuments(recordId).pipe(map(() => ({ saved, recordId })));
            })
        ).subscribe({
            next: ({ saved, recordId }) => {
                this.saving = false;
                this.formVisible = false;
                // The write opens an approval request rather than saving, so the
                // server's own message is shown instead of "saved".
                const detail = (saved?.status === 'PENDING' && saved?.message)
                    ? saved.message
                    : this.t('COMMON.SAVE');
                this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail });
                if (pendingCount > 0 && recordId) {
                    this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('FIN_RECEIVED_WHOM.DOC_UPLOAD_SUCCESS') });
                } else if (pendingCount > 0) {
                    // Said plainly rather than reporting an upload that did not happen.
                    this.messageService.add({ severity: 'info', summary: this.t('COMMON.SUCCESS'), detail: this.t('FIN_RECEIVED_WHOM.DOCS_AFTER_APPROVAL'), life: 8000 });
                }
                this.pendingUploads = [];
                this.loadAll();
                // Link the student's finance record back to this recipient.
                // STUDENT_FINANCE is keyed by STUDENT_ID, so the student picked in
                // the dialog *is* the finance record's id; linkedStudentFinanceId
                // remains as an explicit override. Until now nothing ever set it,
                // which is why STUDY_RECEIVED_WHOM_ID is 0 on all 941 rows and no
                // finance record can tell that its money was collected.
                const finId = this.linkedStudentFinanceId
                    ?? (this.studentFinance ? this.form.studentForDelegationId : null);
                // A new recipient has no id to link to yet; the link is made when
                // the record itself is approved and the row exists.
                if (finId && !recordId) {
                    this.messageService.add({ severity: 'info', summary: this.t('COMMON.SUCCESS'), detail: this.t('FIN_RECEIVED_WHOM.LINK_AFTER_APPROVAL'), life: 8000 });
                }
                if (finId && recordId) {
                    this.linkedStudentFinanceId = null;
                    const url = `${environment.apiUrl}/mohe/api/v1/studentFinance/${finId}/receivedWhom?whomId=${recordId}`;
                    this.httpClient.patch(url, {}).subscribe({
                        next: () => {
                            // That endpoint routes through the approval workflow, so
                            // the link is a pending request, not a written column.
                            // Say that rather than claiming it is linked.
                            this.messageService.add({ severity: 'info', summary: this.t('COMMON.SUCCESS'), detail: this.t('FIN_RECEIVED_WHOM.LINK_PENDING') });
                        },
                        error: () => {
                            this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('FIN_RECEIVED_WHOM.LINK_FINANCE_ERROR') });
                        }
                    });
                }
            },
            error: () => {
                this.saving = false;
                this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.LOAD_ERROR') });
            }
        });
    }

    confirmDelete(record: StudyFinReceivedWhom): void {
        this.confirmationService.confirm({
            message: this.t('FIN_RECEIVED_WHOM.DELETE_CONFIRM_MSG'),
            header: this.t('FIN_RECEIVED_WHOM.DELETE_CONFIRM'),
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
                this.service.delete(record.id).subscribe({
                    next: (response: any) => {
                        // Deletion is queued for approval; the row stays until it is approved.
                        const detail = (response?.status === 'PENDING' && response?.message)
                            ? response.message
                            : this.t('COMMON.DELETE');
                        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail });
                        this.loadAll();
                    },
                    error: () => this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t('COMMON.LOAD_ERROR') })
                });
            }
        });
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    getStudentName(id: number): string {
        if (!id || id === 0) return this.t('COMMON.NOT_AVAILABLE');
        return this.students.find(s => s.studentId === id)?.studentName ?? String(id);
    }

    getCountryName(id: number): string {
        if (!id || id === 0) return this.t('COMMON.NOT_AVAILABLE');
        // Resolve against the full (unfiltered) list so out-of-scope countries — e.g. a
        // passport issue country outside the finance user's scope — still show a name.
        const list = this.allStudyCountries.length ? this.allStudyCountries : this.studyCountries;
        return list.find(c => c.id === id)?.studyCountryInName ?? String(id);
    }

    getGovernmentName(id: number): string {
        if (!id || id === 0) return this.t('COMMON.NOT_AVAILABLE');
        return this.allStudyGovernments.find(g => g.id === id)?.studyGovInName ?? String(id);
    }

    getBankName(id: number): string {
        if (!id || id === 0) return this.t('COMMON.NOT_AVAILABLE');
        return this.allStudyFinanceBanks.find(b => b.id === id)?.studyFinanceBankeName ?? String(id);
    }

    getBranchName(id: number): string {
        if (!id || id === 0) return this.t('COMMON.NOT_AVAILABLE');
        return this.allStudyFinanceBankBranches.find(b => b.id === id)?.studyFinanceBankBranchName ?? String(id);
    }

    getDofName(id: number): string {
        if (!id || id === 0) return this.t('COMMON.NOT_AVAILABLE');
        return this.studyFinanceDofs.find(d => d.id === id)?.studyFinanceDofName ?? String(id);
    }

    applyTextFilter(value: string): void {
        this.filterText = value;
        this.table?.filterGlobal(value, 'contains');
    }

    applyStatusFilter(): void {
        this.table?.filter(this.filterStatus, 'finRecievedStatus', 'equals');
    }

    clearFilters(): void {
        this.filterText = '';
        this.filterStatus = null;
        this.table?.filter(null, 'finRecievedStatus', 'equals');
        this.table?.filterGlobal('', 'contains');
    }

    get statusFilterOptions(): { label: string; value: number }[] {
        return this.studyFinReceivedStatuses.map(s => ({
            label: s.studyFinReceivedStatusName,
            value: s.id
        }));
    }

    getStatusLabel(statusId: number): string {
        const s = this.studyFinReceivedStatuses.find(x => x.id === statusId);
        return s ? s.studyFinReceivedStatusName : (statusId ? String(statusId) : '—');
    }

    /**
     * The colour STUDY_FIN_RECEIVED_STATUS.UI_SEVERITY gives the status.
     *
     * <p>It used to be `statusId === 1 ? 'success' : 'secondary'`, which painted
     * one of the nine statuses green and the other eight the same grey —
     * *Rejected* looked exactly like *Not Received*. The column has held a
     * severity for all nine since May 2026; the entity simply never mapped it.
     */
    getStatusSeverity(statusId: number): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
        const declared = this.studyFinReceivedStatuses.find(s => s.id === statusId)?.uiSeverity;
        switch (declared) {
            case 'success': return 'success';
            case 'info': return 'info';
            case 'warn':
            case 'warning': return 'warn';
            case 'danger': return 'danger';
            case 'secondary': return 'secondary';
            default: return 'secondary';
        }
    }

    /**
     * Shows a relationship the way it is stored.
     *
     * <p>New rows store the LU_FAMILY_RELATIONSHIP code, which resolves to a
     * localized name. The rows written before the lookup existed hold free text
     * (`ولي أمر`, `صديق مخوّل`) that matches no code, and those are shown as
     * they were typed rather than blanked.
     */
    getRelationshipLabel(value: string): string {
        if (!value) return '—';
        return this.relationshipOptions.find(o => o.value === value)?.label ?? value;
    }

    /** Same rule as the relationship, for FIN_RECEIVED_REASON. */
    getReasonLabel(value: string): string {
        if (!value) return '';
        return this.reasonOptions.find(o => o.value === value)?.label ?? value;
    }

    formatDate(v: any): string {
        if (!v) return '—';
        const d = new Date(v);
        return isNaN(d.getTime()) ? String(v) : d.toLocaleDateString();
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    /** Translate, then fill `{name}` placeholders — the service takes no params. */
    tp(key: string, params: Record<string, string | number>): string {
        return Object.entries(params).reduce(
            (text, [name, value]) => text.split(`{${name}}`).join(String(value)),
            this.t(key));
    }
}
