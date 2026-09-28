import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../service/translation.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { CodeListService } from '../../service/code-list.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import {
    WafarService,
    WafarBalanceSummaryDto,
    WafarSpendingDto,
    WafarSpendingCategoryDto,
    WafarSpendingCreateDto
} from '../../service/wafar.service';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { changesOnly } from '../../service/reload-streams.util';
import { resetPaging } from '../../service/table-paging.util';

@Component({
    selector: 'app-wafar',
    templateUrl: './wafar.component.html',
    styleUrls: ['./wafar.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class WafarComponent implements OnInit {

    loading = false;
    saving = false;

    balance: WafarBalanceSummaryDto = {
        totalDeposits: 0, totalSpent: 0, currentBalance: 0, pendingAmount: 0, transactionCount: 0
    };

    transactions: WafarSpendingDto[] = [];
    categories: WafarSpendingCategoryDto[] = [];
    /** Bound to the table, so a filter that shrinks the list starts it on page one (QA B8, N9). */
    first = 0;

    // Filters
    filterCategoryId: number | null = null;
    filterStatus: string | null = null;
    filterDateFrom: Date | null = null;
    filterDateTo: Date | null = null;

    statusOptions: { label: string; value: string }[] = [];
    categoryOptions: { label: string; value: number | null }[] = [];

    // Create/Edit dialog
    formVisible = false;
    formMode: 'create' | 'edit' = 'create';
    editingId: number | null = null;
    form: WafarSpendingCreateDto = this.emptyForm();

    selectedCategoryCode = '';
    formDate: Date | null = null;

    // Beneficiary
    beneficiaryTypeOptions: { label: string; value: string }[] = [];

    /**
     * Attachés the signed-in user may spend from. Wafar stays at the attaché that
     * holds it, so a transaction has to name one — 11 of the 17 rows already in the
     * table do not, and nobody can now tell whose money they were.
     */
    countryOptions: LookupItem[] = [];

    // Student search
    studentSuggestions: any[] = [];
    selectedStudent: any = null;

    // View dialog
    viewVisible = false;
    viewingTx: WafarSpendingDto | null = null;

    // Approve dialog
    approveVisible = false;
    approvingTx: WafarSpendingDto | null = null;

    // Reject dialog
    rejectVisible = false;
    rejectingTx: WafarSpendingDto | null = null;
    rejectReason = '';

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }

    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translationService.getCurrentLanguage();
    }

    // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        private wafarService: WafarService,
        private messageService: MessageService,
        public translationService: TranslationService,
        private lookupService: LookupService,
        private http: HttpClient,
        private codeLists: CodeListService,
        private umsAuth: UmsAuthService
    ) {}

    // ── N-04.e: the ministry's correction request ───────────────────────────

    /** Ministry-level accounts cannot edit an attaché's Wafar; they ask instead. */
    get isMinistryLevel(): boolean {
        return this.umsAuth.isMinistryLevel;
    }

    correctionVisible = false;
    correctionTx: WafarSpendingDto | null = null;
    correctionAmount: number | null = null;
    correctionDate: Date | null = null;
    correctionDescriptionAr = '';
    correctionDescriptionEn = '';
    correctionNotes = '';
    correctionCancel = false;
    correctionReason = '';
    submittingCorrection = false;

    openCorrection(tx: WafarSpendingDto): void {
        this.correctionTx = tx;
        this.correctionAmount = null;
        this.correctionDate = null;
        this.correctionDescriptionAr = '';
        this.correctionDescriptionEn = '';
        this.correctionNotes = '';
        this.correctionCancel = false;
        this.correctionReason = '';
        this.correctionVisible = true;
    }

    get correctionChangesSomething(): boolean {
        return this.correctionAmount != null || !!this.correctionDate || !!this.correctionDescriptionAr
            || !!this.correctionDescriptionEn || !!this.correctionNotes || this.correctionCancel;
    }

    submitCorrection(): void {
        if (!this.correctionTx || !this.correctionReason.trim() || !this.correctionChangesSomething) { return; }
        this.submittingCorrection = true;
        const d = this.correctionDate;
        const spendingDate = d ? `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` : null;
        this.wafarService.requestCorrection(this.correctionTx.id, {
            amount: this.correctionAmount,
            spendingDate,
            descriptionAr: this.correctionDescriptionAr || null,
            descriptionEn: this.correctionDescriptionEn || null,
            notes: this.correctionNotes || null,
            cancel: this.correctionCancel || null,
            reason: this.correctionReason.trim()
        }).subscribe({
            next: () => {
                this.submittingCorrection = false;
                this.correctionVisible = false;
                this.messageService.add({ severity: 'info', summary: this.translationService.translate('WAFAR.CORRECTION.TITLE'),
                    detail: this.translationService.translate('WAFAR.CORRECTION.SENT') });
            },
            error: err => {
                this.submittingCorrection = false;
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('WAFAR.CORRECTION.TITLE'),
                    detail: err?.error?.message || err?.error?.error || this.translationService.translate('WAFAR.CORRECTION.FAILED') });
            }
        });
    }

    ngOnInit(): void {
        this.loadStatusOptions();
        this.buildBeneficiaryTypeOptions();
        this.loadCountryOptions();
        this.loadCategories();
        this.loadAll();

        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.loadStatusOptions();
            this.buildCategoryOptions();
            this.buildBeneficiaryTypeOptions();
        });
    }

    private loadCountryOptions(): void {
        // The attaché is a study country; the home-country list reuses its ids for other countries (live QA H6).
        this.lookupService.getStudyCountriesIn().subscribe({
            next: countries => this.countryOptions = countries,
            error: () => { this.countryOptions = []; }
        });
    }

    /**
     * Whether the attaché has to be picked by hand. When a student is named the
     * server takes that student's own country, and when the user has exactly one
     * attaché it uses that — so the field only appears when neither applies.
     */
    get needsAttacheChoice(): boolean {
        return this.form.beneficiaryType !== 'STUDENT' && this.countryOptions.length > 1;
    }

    countryName(id?: number | null): string {
        if (id == null) return '—';
        return this.countryOptions.find(c => c.id === id)?.name ?? String(id);
    }

    private buildBeneficiaryTypeOptions(): void {
        this.beneficiaryTypeOptions = this.codeLists.options('wafar-beneficiary-type');
    }

    private loadStatusOptions(): void {
        this.lookupService.getWafarSpendingStatuses().subscribe(statuses => {
            this.statusOptions = [
                { label: this.t('WAFAR.STATUS.ALL'), value: '' },
                ...statuses
            ];
        });
    }

    private buildCategoryOptions(): void {
        this.categoryOptions = [
            { label: this.t('WAFAR.CATEGORIES.ALL'), value: null },
            ...this.categories.map(c => ({
                label: this.isRtl ? c.categoryNameAr : c.categoryNameEn,
                value: c.id
            }))
        ];
    }

    loadCategories(): void {
        this.wafarService.getCategories().subscribe({
            next: cats => {
                this.categories = cats;
                this.buildCategoryOptions();
            },
            error: () => this.showError('WAFAR.LOAD_ERROR')
        });
    }

    loadAll(): void {
        this.loading = true;
        this.wafarService.getBalance().subscribe({
            next: b => this.balance = b,
            error: () => {}
        });
        this.loadTransactions();
    }

    /** A filter change: back to page one, unlike the reload after a save, which keeps its page. */
    applyFilters(): void {
        resetPaging(this);
        this.loadTransactions();
    }

    private loadTransactions(): void {
        this.loading = true;
        const filters: any = {};
        if (this.filterCategoryId) filters.categoryId = this.filterCategoryId;
        if (this.filterStatus) filters.status = this.filterStatus;
        if (this.filterDateFrom) filters.dateFrom = this.toIsoDate(this.filterDateFrom);
        if (this.filterDateTo) filters.dateTo = this.toIsoDate(this.filterDateTo);

        this.wafarService.getTransactions(filters).subscribe({
            next: txs => { this.transactions = txs; this.loading = false; },
            error: () => { this.showError('WAFAR.LOAD_ERROR'); this.loading = false; }
        });
    }

    clearFilters(): void {
        this.filterCategoryId = null;
        this.filterStatus = null;
        this.filterDateFrom = null;
        this.filterDateTo = null;
        this.applyFilters();
    }

    // ── Student autocomplete ──────────────────────────────────────────────────

    searchStudents(event: any): void {
        const query = event.query || '';
        if (query.length < 2) { this.studentSuggestions = []; return; }
        this.http.get<any>(`${environment.apiUrl}/mohe/api/v1/studentdata/studentall?page=0&size=20&search=${encodeURIComponent(query)}`)
            .subscribe({
                next: (res: any) => {
                    const items = res?.content ?? res ?? [];
                    this.studentSuggestions = items.map((s: any) => ({
                        id: s.studentId,
                        name: s.studentName,
                        label: `${s.studentId} — ${s.studentName}`
                    }));
                },
                error: () => { this.studentSuggestions = []; }
            });
    }

    onStudentSelect(student: { id: number } | null): void {
        if (student?.id) {
            this.form.studentId = student.id;
        }
    }

    // ── Form dialog ───────────────────────────────────────────────────────────

    openNew(): void {
        this.formMode = 'create';
        this.editingId = null;
        this.form = this.emptyForm();
        this.formDate = null;
        this.selectedCategoryCode = '';
        this.selectedStudent = null;
        this.formVisible = true;
    }

    // ── Beneficiary ───────────────────────────────────────────────────────────

    onBeneficiaryTypeChange(type: string): void {
        // Clear fields that don't belong to the chosen beneficiary type.
        if (type !== 'STUDENT') {
            this.form.studentId = undefined;
            this.selectedStudent = null;
            this.form.academicYear = '';
        }
        if (type !== 'OTHER') {
            this.form.payeeNameAr = '';
            this.form.payeeNameEn = '';
        }
        // A student carries their own attaché, so a hand-picked one would only
        // contradict it.
        if (type === 'STUDENT') {
            this.form.studyCountryInId = undefined;
        }
    }

    /**
     * Who received the money: the student when the row names one, else the
     * payee, else the beneficiary type's own name (a ministry, cultural affairs).
     */
    getBeneficiaryLabel(tx: WafarSpendingDto): string {
        if (tx.studentId != null) {
            return tx.studentName || String(tx.studentId);
        }
        const payee = (this.isRtl ? tx.payeeNameAr : tx.payeeNameEn) || tx.payeeNameEn || tx.payeeNameAr;
        if (payee) {
            return payee;
        }
        return this.codeLists.label('wafar-beneficiary-type', tx.beneficiaryType);
    }

    openEdit(tx: WafarSpendingDto): void {
        this.formMode = 'edit';
        this.editingId = tx.id;
        this.form = {
            categoryId: tx.category?.id,
            amount: tx.amount,
            spendingDate: tx.spendingDate,
            referenceNumber: tx.referenceNumber,
            descriptionAr: tx.descriptionAr,
            descriptionEn: tx.descriptionEn,
            payeeNameAr: tx.payeeNameAr,
            payeeNameEn: tx.payeeNameEn,
            studentId: tx.studentId,
            beneficiaryType: tx.beneficiaryType || (tx.studentId ? 'STUDENT' : 'OTHER'),
            notes: tx.notes
        };
        this.formDate = tx.spendingDate ? new Date(tx.spendingDate) : null;
        this.selectedCategoryCode = tx.category?.categoryCode ?? '';
        if (tx.studentId && tx.studentName) {
            this.selectedStudent = { id: tx.studentId, name: tx.studentName, label: `${tx.studentId} — ${tx.studentName}` };
        } else {
            this.selectedStudent = null;
        }
        this.formVisible = true;
    }

    // ── View dialog ───────────────────────────────────────────────────────────

    openView(tx: WafarSpendingDto): void {
        this.viewingTx = tx;
        this.viewVisible = true;
    }

    onCategoryChange(categoryId: number): void {
        const cat = this.categories.find(c => c.id === categoryId);
        this.selectedCategoryCode = cat?.categoryCode ?? '';
        // A university-fee spending is always paid on behalf of a student, so lock the
        // beneficiary type to STUDENT (the approval step posts it to the student's fee record).
        if (this.selectedCategoryCode === 'UNIVERSITY_FEE') {
            this.form.beneficiaryType = 'STUDENT';
            this.onBeneficiaryTypeChange('STUDENT');
        }
    }

    get isUniversityFee(): boolean {
        return this.selectedCategoryCode === 'UNIVERSITY_FEE';
    }

    saveForm(): void {
        if (!this.form.categoryId || !this.form.amount || !this.formDate) {
            this.showError('COMMON.REQUIRED_FIELDS');
            return;
        }
        if (this.form.beneficiaryType === 'STUDENT' && !this.form.studentId) {
            this.showError('WAFAR.STUDENT_REQUIRED');
            return;
        }
        if (this.needsAttacheChoice && !this.form.studyCountryInId) {
            // Caught here as well as on the server so the user gets a field-level
            // message rather than a 400 from a save they thought had worked.
            this.showError('WAFAR.ATTACHE_REQUIRED');
            return;
        }
        this.form.spendingDate = this.toIsoDate(this.formDate!);
        this.saving = true;
        const op = this.formMode === 'create'
            ? this.wafarService.createTransaction(this.form)
            : this.wafarService.updateTransaction(this.editingId!, this.form);

        op.subscribe({
            next: () => {
                this.showSuccess(this.formMode === 'create' ? 'WAFAR.SAVE_SUCCESS' : 'WAFAR.UPDATE_SUCCESS');
                this.formVisible = false;
                this.saving = false;
                this.loadAll();
            },
            error: () => { this.showError('WAFAR.SAVE_ERROR'); this.saving = false; }
        });
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    openApprove(tx: WafarSpendingDto): void {
        this.approvingTx = tx;
        this.approveVisible = true;
    }

    confirmApprove(): void {
        if (!this.approvingTx) return;
        this.wafarService.approveTransaction(this.approvingTx.id).subscribe({
            next: () => {
                this.showSuccess('WAFAR.APPROVE_SUCCESS');
                this.approveVisible = false;
                this.loadAll();
            },
            error: () => this.showError('WAFAR.SAVE_ERROR')
        });
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    openReject(tx: WafarSpendingDto): void {
        this.rejectingTx = tx;
        this.rejectReason = '';
        this.rejectVisible = true;
    }

    confirmReject(): void {
        if (!this.rejectingTx) return;
        this.wafarService.rejectTransaction(this.rejectingTx.id, this.rejectReason).subscribe({
            next: () => {
                this.showSuccess('WAFAR.REJECT_SUCCESS');
                this.rejectVisible = false;
                this.loadAll();
            },
            error: () => this.showError('WAFAR.SAVE_ERROR')
        });
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    cancelTransaction(tx: WafarSpendingDto): void {
        this.wafarService.cancelTransaction(tx.id).subscribe({
            next: () => {
                this.showSuccess('WAFAR.CANCEL_SUCCESS');
                this.loadAll();
            },
            error: () => this.showError('WAFAR.SAVE_ERROR')
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    getCategoryLabel(cat: WafarSpendingCategoryDto): string {
        if (!cat) return '';
        return this.isRtl ? cat.categoryNameAr : cat.categoryNameEn;
    }

    private emptyForm(): WafarSpendingCreateDto {
        return {
            categoryId: undefined as any,
            amount: undefined as any,
            spendingDate: '',
            referenceNumber: '',
            descriptionAr: '',
            descriptionEn: '',
            payeeNameAr: '',
            payeeNameEn: '',
            studentId: undefined,
            studyCountryInId: undefined,
            beneficiaryType: 'STUDENT',
            academicYear: '',
            notes: ''
        };
    }

    private toIsoDate(d: Date): string {
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    }

    private showSuccess(key: string): void {
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS') || 'Success', detail: this.t(key) });
    }

    private showError(key: string): void {
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR') || 'Error', detail: this.t(key) });
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }
}
