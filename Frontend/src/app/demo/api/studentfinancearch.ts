export class StudentFinanceArch {
    financeArchEntryId: number;
    studentId: number;
    studentFinId: string;
    studentFinName: string;
    studentFinRecievedYear: number;
    studentFinRecievedMonth: number;     // quarter number 1-4
    studentFinRecievedYearMonth: number; // YYYYQ format e.g. 20241
    studentFinRecievedAmount: number;
    studyCountryInId: number;
    studyGovInId: number;
    studyFinanceBankeId: number;
    studyFinanceBankBranchId: number;
    studyFinanceDofId: number;
    studyCheckId: string;
    studyCheckDate: Date | null;
    studyCheckDetails: string;
    studyCheckFormNumber: string;
    studyFinRecievedStatus: number;
    studyNote1: string;
    studyReceivedWhomStatusId: number;
    studyReceivedWhomId: number;
    studyFinanceMidate: Date | null;
    moheQuarterId: number;
    archievedDate: Date | null;
    // ── Added fields (gap-fill) ───────────────────────────────────────────────
    wafarAmount: number;
    wafarTypeId: number;
    /**
     * Amount received minus wafar — a PERSISTED computed column, read-only.
     *
     * Null rather than 0 when unknown: callers fall back to `amount - wafar` with `??`, and
     * a 0 default would satisfy that operator and render a hard zero against a database
     * where V015 has not been applied yet.
     */
    netAmount: number | null;
    studentBankId: number;
    studentAccountNumber: string;
    studentAccountEnglishName: string;
    studentAccountEndDate: string;
    studentBankNotes: string;

    constructor() {
        this.financeArchEntryId = 0;
        this.studentId = 0;
        this.studentFinId = '';
        this.studentFinName = '';
        this.studentFinRecievedYear = 0;
        this.studentFinRecievedMonth = 0;
        this.studentFinRecievedYearMonth = 0;
        this.studentFinRecievedAmount = 0;
        this.studyCountryInId = 0;
        this.studyGovInId = 0;
        this.studyFinanceBankeId = 0;
        this.studyFinanceBankBranchId = 0;
        this.studyFinanceDofId = 0;
        this.studyCheckId = '';
        this.studyCheckDate = null;
        this.studyCheckDetails = '';
        this.studyCheckFormNumber = '';
        this.studyFinRecievedStatus = 0;
        this.studyNote1 = '';
        this.studyReceivedWhomStatusId = 0;
        this.studyReceivedWhomId = 0;
        this.studyFinanceMidate = null;
        this.moheQuarterId = 0;
        this.archievedDate = null;
        this.wafarAmount = 0;
        this.wafarTypeId = 0;
        this.netAmount = null;
        this.studentBankId = 0;
        this.studentAccountNumber = '';
        this.studentAccountEnglishName = '';
        this.studentAccountEndDate = '';
        this.studentBankNotes = '';
    }

    /** Returns e.g. "2024 Q1" from yearMonth=20241 */
    getQuarterLabel(): string {
        if (!this.studentFinRecievedYearMonth) return '—';
        const year = Math.floor(this.studentFinRecievedYearMonth / 10);
        const q    = this.studentFinRecievedYearMonth % 10;
        return `${year} Q${q}`;
    }
}
