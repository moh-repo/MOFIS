import { Component, OnInit, OnDestroy, ViewChild, ChangeDetectorRef } from '@angular/core';
import { Table } from 'primeng/table';
import { MessageService } from 'primeng/api';
import { skip } from 'rxjs/operators';
import { StudentDataArchService } from '../../service/studentdataarch.service';
import { StudentFinanceArchService } from '../../service/studentfinancearch.service';
import { StudentDataArch } from '../../api/studentdataarch';
import { StudentFinanceArch } from '../../api/studentfinancearch';
import { StudyFinReceivedStatus } from '../../api/study-fin-received-status';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService, PrimeSeverity } from '../../service/code-list.service';

@Component({
    selector: 'app-studentfinancearch',
    templateUrl: './studentfinancearch.component.html',
    styleUrls: ['./studentfinancearch.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentFinanceArchComponent implements OnInit, OnDestroy {
    @ViewChild('dt') dt: Table | undefined;

    studentFinanceArchList: StudentDataArch[] = [];
    /** Finance history records for the selected student (loaded from StudentFinanceArch). */
    financeArchDetails: StudentFinanceArch[] = [];
    newStudentFinanceArch: StudentDataArch = new StudentDataArch();
    selectedStudentFinanceArch: StudentDataArch | null = null;
    submitted = false;
    loading = true;
    isLoadingData = false;
    loadingFinancialRecords = false;
    totalElements = 0;
    rows = 10;
    rowsPerPageOptions: number[] = [5, 10, 20];
    currentPage = 0;
    first = 0;
    globalFilter = '';
    feeDialog = false;
    viewDialog = false;

    // ── Lookup dropdown options ───────────────────────────────────────────────
    genderOptions: LookupItem[] = [];
    countryOptions: LookupItem[] = [];
    governmentOptions: LookupItem[] = [];
    municipalityOptions: LookupItem[] = [];
    degreeOptions: LookupItem[] = [];
    majorOptions: LookupItem[] = [];
    currentLevelOptions: LookupItem[] = [];
    studyStatusOptions: LookupItem[] = [];
    agencyTypeOptions: LookupItem[] = [];
    ministryTypeOptions: LookupItem[] = [];
    studyTypeOptions: LookupItem[] = [];
    universityTypeOptions: LookupItem[] = [];
    studyCountryInOptions: LookupItem[] = [];
    studyGovInOptions: LookupItem[] = [];
    studyUniversityInOptions: LookupItem[] = [];
    studyCollegeInOptions: LookupItem[] = [];
    studyMajorInOptions: LookupItem[] = [];
    studyFinanceBankOptions: LookupItem[] = [];
    studyFinanceBankBranchOptions: LookupItem[] = [];
    studyFinanceDofOptions: LookupItem[] = [];
    /**
     * STUDY_FIN_RECEIVED_STATUS rows as served, not flattened to id/name: the
     * history table paints each row by the status's own CODE and UI_SEVERITY.
     */
    finReceivedStatuses: StudyFinReceivedStatus[] = [];
    studyReceivedWhomStatusOptions: LookupItem[] = [];
    wafarTypeOptions: LookupItem[] = [];
    /** STUDY_CURRENCY_IN — AMOUNT_CURRENCY and UNIVERSITY_FEE_STATUS_CURRENCY are ids into it. */
    currencyOptions: LookupItem[] = [];
    studyEducationInOptions: LookupItem[] = [];

    private languageSubscription?: any;

    constructor(
        private studentDataArchService: StudentDataArchService,
        private studentFinanceArchService: StudentFinanceArchService,
        private lookupService: LookupService,
        private messageService: MessageService,
        public translationService: TranslationService,
        private cdr: ChangeDetectorRef
    ) { }

    ngOnInit() {
        this.loadStudentFinanceArch();
        this.loadLookupData();
        // Lookup names are language-specific, so reload them when the language changes.
        // skip(1) avoids a duplicate load on init.
        this.languageSubscription = this.translationService.currentLanguage$
            .pipe(skip(1))
            .subscribe(() => this.loadLookupData());
    }

    ngOnDestroy() {
        if (this.languageSubscription) {
            this.languageSubscription.unsubscribe();
        }
    }

    // ── Lookup helpers ────────────────────────────────────────────────────────

    /**
     * Resolves a lookup id to its name from the database list.
     *
     * Never falls through to String(id): a raw foreign key on screen reads as data to a
     * user who cannot tell it apart from a real value. An id with no matching row means the
     * lookup failed to load or the row was removed, and "not specified" says so honestly.
     */
    getLookupName(id: number | null | undefined, options: LookupItem[]): string {
        if (!id || Number(id) <= 0) {
            return this.translationService.translate('COMMON.NOT_SPECIFIED');
        }
        return options.find(o => Number(o.id) === Number(id))?.name
            ?? this.translationService.translate('COMMON.NOT_SPECIFIED');
    }

    /** Currency name for AMOUNT_CURRENCY / UNIVERSITY_FEE_STATUS_CURRENCY (STUDY_CURRENCY_IN ids). */
    getCurrencyName(id: number | null | undefined): string {
        return this.getLookupName(id, this.currencyOptions);
    }

    /**
     * The received status of an archived finance row, in the reader's language.
     *
     * Resolved through STUDY_FIN_RECEIVED_STATUS rather than a 0/1/2 switch: the
     * legacy 0 is being back-filled to 99 (NOT_SPECIFIED), which the switch rendered
     * as nothing at all (live QA 23 Sep 2026, H2). Id 99 is a real row and shows its
     * own name; an id the lookup does not know falls back to "not specified".
     */
    getFinReceivedStatusName(id: number | null | undefined): string {
        const name = this.findFinReceivedStatus(id)?.studyFinReceivedStatusName ?? '';
        return name.trim() ? name : this.translationService.translate('COMMON.NOT_SPECIFIED');
    }

    /**
     * UI_SEVERITY as the row states it; when the row has none, the colour follows
     * the code — money that moved is success, "not specified" is neutral, the rest
     * warn. An unknown id is neutral, never green.
     */
    getFinReceivedStatusSeverity(id: number | null | undefined): PrimeSeverity {
        const row = this.findFinReceivedStatus(id);
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

    private findFinReceivedStatus(id: number | null | undefined): StudyFinReceivedStatus | undefined {
        if (id === null || id === undefined) {
            return undefined;
        }
        return this.finReceivedStatuses.find(s => Number(s.id) === Number(id));
    }

    /**
     * Net of wafar for one archived finance row. NET_AMOUNT is a computed column on
     * STUDENT_FINANCE_ARCH, but it only exists once V015 has been applied, so fall back
     * to the arithmetic rather than render a blank column against an un-migrated database.
     */
    getRecordNetAmount(record: StudentFinanceArch): number {
        return record.netAmount ?? (record.studentFinRecievedAmount ?? 0) - (record.wafarAmount ?? 0);
    }

    getQuarterLabel(yyyyQ?: number): string {
        if (!yyyyQ) return '—';
        const year = Math.floor(yyyyQ / 10);
        const q    = yyyyQ % 10;
        return `${year} Q${q}`;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    loadStudentFinanceArch() {
        if (this.isLoadingData) return;
        this.isLoadingData = true;
        this.loading = true;
        this.cdr.detectChanges();
        /*
         * Name is the only filter this endpoint supports: StudentDataArchController's
         * /distinct/paginated takes filterStudentId, filterName, filterPassport and the sort
         * pair, and nothing else. The status and date-range arguments that used to be passed
         * here were serialized onto the query string and silently dropped by Spring, and the
         * controls that fed them were never rendered.
         */
        this.studentDataArchService.getDistinctStudentsPaginated(
            this.currentPage,
            this.rows,
            null,
            this.globalFilter.trim() || undefined
        ).subscribe({
            next: (response: any) => {
                this.studentFinanceArchList = this.mapToStudentDataArch(response.content || []);
                this.totalElements = response.totalElements || 0;
                this.loading = false;
                this.isLoadingData = false;
                this.cdr.detectChanges();
            },
            error: () => {
                this.studentFinanceArchList = [];
                this.totalElements = 0;
                this.loading = false;
                this.isLoadingData = false;
                this.cdr.detectChanges();
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_FINANCE_ARCH.MESSAGES.LOAD_HISTORY_ERROR'), life: 3000 });
            }
        });
    }

    private mapToStudentDataArch(data: any[]): StudentDataArch[] {
        return data.map(item => {
            const s = new StudentDataArch();
            s.id = item.id;
            s.studentName = item.studentName;
            s.studentCountryId = item.studentCountryId;
            s.studentGovId = item.studentGovId;
            s.studentMudId = item.studentMudId;
            s.studentDelegationDecisionYear = item.studentDelegationDecisionYear;
            s.studentDegreeId = item.studentDegreeId;
            s.studentMajorId = item.studentMajorId;
            s.studentMajorName = item.studentMajorName;
            s.studentDelegateDecisionNo = item.studentDelegateDecisionNo;
            s.studentStudyNoOfYears = item.studentStudyNoOfYears;
            s.studentCurrentLevelId = item.studentCurrentLevelId;
            s.studentFirstReceiptOfDue = item.studentFirstReceiptOfDue;
            s.studentAmount = item.studentAmount;
            // Totals over STUDENT_FINANCE_ARCH, filled in per page by the server.
            s.archivedAmountReceived = item.archivedAmountReceived ?? 0;
            s.archivedWafarAmount = item.archivedWafarAmount ?? 0;
            s.archivedNetAmount = item.archivedNetAmount ?? 0;
            s.studentNote1 = item.studentNote1;
            s.studentNote2 = item.studentNote2;
            s.studentStudyStatusId = item.studentStudyStatusId;
            s.studentAgencyTypeId = item.studentAgencyTypeId;
            s.studentMinistryTypeId = item.studentMinistryTypeId;
            s.studentStudyTypeId = item.studentStudyTypeId;
            s.studentUniversityTypeId = item.studentUniversityTypeId;
            s.studentAddedQuarter = item.studentAddedQuarter;
            s.studentLastQuarter = item.studentLastQuarter;
            s.studyUniversityIn = item.studyUniversityIn;
            s.studyCollegeIn = item.studyCollegeIn;
            s.studyCountryIn = item.studyCountryIn;
            s.syudyGovIn = item.syudyGovIn;
            s.studyEducationIn = item.studyEducationIn;
            s.studyMajorIn = item.studyMajorIn;
            s.studentBirthdate = item.studentBirthdate ? new Date(item.studentBirthdate) : null;
            s.studentBirthdatePlace = item.studentBirthdatePlace;
            s.studentGender = item.studentGender;
            s.studentPassportNumber = item.studentPassportNumber;
            s.studentPassportIssueDate = item.studentPassportIssueDate ? new Date(item.studentPassportIssueDate) : null;
            s.studentPassportExpireDate = item.studentPassportExpireDate ? new Date(item.studentPassportExpireDate) : null;
            s.studentPassportIssueCountryId = item.studentPassportIssueCountryId;
            s.studyUniversityFeeId = item.studyUniversityFeeId;
            s.studyUniversityFeeStatusId = item.studyUniversityFeeStatusId;
            s.studentMidate = item.studentMidate ? new Date(item.studentMidate) : new Date();
            s.archiveDate = item.archiveDate ? new Date(item.archiveDate) : new Date();
            s.querterIs = item.querterIs;
            // ── New fields ────────────────────────────────────────────────────
            s.studentId = item.studentId;
            s.amountCurrency = item.amountCurrency;
            s.studentAge = item.studentAge;
            s.universityFeeStatusCurrency = item.universityFeeStatusCurrency;
            s.picturePath = item.picturePath;
            s.studentEmail = item.studentEmail;
            s.studentCellphone = item.studentCellphone;
            s.studentHomeNumber = item.studentHomeNumber;
            s.studentEmergencyContact = item.studentEmergencyContact;
            return s;
        });
    }

    /** Load real StudentFinanceArch records for the selected student. */
    private loadFinanceArchDetails(sourceStudentId: number) {
        if (!sourceStudentId) return;
        this.loadingFinancialRecords = true;
        this.studentFinanceArchService.getByStudentId(sourceStudentId).subscribe({
            next: (records) => {
                this.financeArchDetails = records ?? [];
                this.loadingFinancialRecords = false;
                this.cdr.detectChanges();
            },
            error: () => {
                this.financeArchDetails = [];
                this.loadingFinancialRecords = false;
                this.cdr.detectChanges();
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_FINANCE_ARCH.MESSAGES.LOAD_FINANCE_ERROR'), life: 3000 });
            }
        });
    }

    loadLookupData() {
        this.lookupService.getStudentGenders().subscribe({ next: d => this.genderOptions = d, error: () => {} });
        this.lookupService.getCountries().subscribe({ next: d => this.countryOptions = d, error: () => {} });
        this.lookupService.getGovernments().subscribe({ next: d => this.governmentOptions = d, error: () => {} });
        this.lookupService.getStudentDegrees().subscribe({ next: d => this.degreeOptions = d, error: () => {} });
        this.lookupService.getStudentMajors().subscribe({ next: d => this.majorOptions = d, error: () => {} });
        this.lookupService.getCurrentLevels().subscribe({ next: d => this.currentLevelOptions = d, error: () => {} });
        this.lookupService.getStudyStatuses().subscribe({ next: d => this.studyStatusOptions = d, error: () => {} });
        this.lookupService.getAgencyTypes().subscribe({ next: d => this.agencyTypeOptions = d, error: () => {} });
        this.lookupService.getMinistryTypes().subscribe({ next: d => this.ministryTypeOptions = d, error: () => {} });
        this.lookupService.getStudentStudyTypes().subscribe({ next: d => this.studyTypeOptions = d, error: () => {} });
        this.lookupService.getUniversityTypes().subscribe({ next: d => this.universityTypeOptions = d, error: () => {} });
        this.lookupService.getStudyCountriesIn().subscribe({ next: d => this.studyCountryInOptions = d, error: () => {} });
        this.lookupService.getStudyGovsIn().subscribe({ next: d => this.studyGovInOptions = d, error: () => {} });
        this.lookupService.getStudyUniversitiesIn().subscribe({ next: d => this.studyUniversityInOptions = d, error: () => {} });
        this.lookupService.getStudyCollegesIn().subscribe({ next: d => this.studyCollegeInOptions = d, error: () => {} });
        this.lookupService.getStudyMajorsIn().subscribe({ next: d => this.studyMajorInOptions = d, error: () => {} });
        this.lookupService.getStudyEducationsIn().subscribe({ next: d => this.studyEducationInOptions = d, error: () => {} });
        /*
         * AMOUNT_CURRENCY and UNIVERSITY_FEE_STATUS_CURRENCY are ids into STUDY_CURRENCY_IN,
         * not amounts — the same lookup the student-add screen binds them to. They were
         * rendered as free number inputs here, so the dialog asked for a raw id and the view
         * printed one back.
         */
        this.lookupService.getStudyCurrencies().subscribe({ next: d => this.currencyOptions = d, error: () => {} });
        this.lookupService.getAllStudyReceivedWhomStatuses().subscribe({
            next: d => this.studyReceivedWhomStatusOptions = d.map((item: any) => ({
                id: item.id || item.studyReceivedWhomStatusId,
                name: item.studyReceivedWhomStatusName || item.name
            } as LookupItem))
        });
        /*
         * No recipient list (live QA M6 / R5-08): /lookup/study-fin-received-whoms is
         * recipient records behind FINANCE, the Archiving Officer opens this page with
         * FINANCE_ARCH alone and got "Access denied", and nothing here showed the list.
         */
        this.lookupService.getAllStudyFinanceBanks().subscribe({
            next: d => this.studyFinanceBankOptions = d.map((item: any) => ({
                id: item.id || item.studyFinanceBankId,
                name: item.studyFinanceBankeName || item.name
            } as LookupItem))
        });
        this.lookupService.getAllStudyFinanceBankBranches().subscribe({
            next: d => this.studyFinanceBankBranchOptions = d.map((item: any) => ({
                id: item.id || item.studyFinanceBankBranchId,
                name: item.studyFinanceBankBranchName || item.name
            } as LookupItem))
        });
        this.lookupService.getAllStudyFinanceDofs().subscribe({
            next: d => this.studyFinanceDofOptions = d.map((item: any) => ({
                id: item.id || item.studyFinanceDofId,
                name: item.studyFinanceDofName || item.name
            } as LookupItem))
        });
        this.lookupService.getAllStudyFinReceivedStatuses().subscribe({
            next: d => this.finReceivedStatuses = d ?? [],
            error: () => {}
        });
        this.lookupService.getWafarTypes().subscribe({
            next: d => this.wafarTypeOptions = d.map((item: any) => ({
                id: item.id || item.wafarTypeId,
                name: item.wafarTypeName || item.name
            } as LookupItem)),
            error: () => {}
        });
    }

    // ── CRUD operations ───────────────────────────────────────────────────────

    onPageChange(event: any) {
        if (this.currentPage !== event.page || this.rows !== event.rows) {
            this.currentPage = event.page;
            this.rows = event.rows;
            this.first = event.first;
            this.loadStudentFinanceArch();
        }
    }

    refreshData() {
        this.loadStudentFinanceArch();
    }

    searchStudents() {
        if (this.isLoadingData) return;
        this.currentPage = 0;
        this.first = 0;
        this.loadStudentFinanceArch();
    }

    clearFilters() {
        this.globalFilter = '';
        this.currentPage = 0;
        this.first = 0;
        this.loadStudentFinanceArch();
    }

    editStudentFinanceArch(student: StudentDataArch) {
        this.newStudentFinanceArch = Object.assign(new StudentDataArch(), student);
        this.selectedStudentFinanceArch = student;
        this.submitted = false;
        if (student.studentId) {
            this.loadFinanceArchDetails(student.studentId);
        }
        this.feeDialog = true;
    }

    viewStudentFinanceArch(student: StudentDataArch) {
        this.newStudentFinanceArch = Object.assign(new StudentDataArch(), student);
        this.selectedStudentFinanceArch = student;
        if (student.studentId) {
            this.loadFinanceArchDetails(student.studentId);
        }
        this.viewDialog = true;
    }

    /*
     * There is no delete. An archived quarter is the record of what was on file
     * when the quarter closed; the snapshot endpoints that removed one are gone
     * (live QA 23 Sep 2026, H1), and the finance columns are the only thing this
     * screen corrects — see saveStudentFinanceArch().
     */

    hideDialog() {
        this.feeDialog = false;
        this.viewDialog = false;
        this.submitted = false;
        this.financeArchDetails = [];
    }

    /**
     * Saves the finance columns only (review F5): the server copies amount, currencies,
     * first receipt and notes onto the archived row and keeps the rest as archived.
     */
    saveStudentFinanceArch() {
        this.submitted = true;
        const row = this.newStudentFinanceArch;
        if (!row?.id) {
            return;
        }
        if (row.studentAmount != null && row.studentAmount < 0) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_FINANCE_ARCH.MESSAGES.AMOUNT_NEGATIVE'), life: 3000 });
            return;
        }
        this.studentDataArchService.updateFinance(row.id, row).subscribe({
            next: () => {
                this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('STUDENT_FINANCE_ARCH.MESSAGES.UPDATE_SUCCESS'), life: 3000 });
                this.feeDialog = false;
                this.financeArchDetails = [];
                this.newStudentFinanceArch = new StudentDataArch();
                this.loadStudentFinanceArch();
            },
            error: () => {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_FINANCE_ARCH.MESSAGES.UPDATE_ERROR'), life: 3000 });
            }
        });
    }

}
