export class StudentDataArch {
    id: number;
    studentName: string;
    studentCountryId: number;
    studentGovId: number;
    studentMudId: number;
    studentDelegationDecisionYear: string;
    studentDegreeId: number;
    studentMajorId: number;
    studentMajorName: string;
    studentDelegateDecisionNo: number;
    studentStudyNoOfYears: number;
    studentCurrentLevelId: number;
    studentFirstReceiptOfDue: number;
    studentAmount: number;
    studentNote1: string;
    studentNote2: string;
    studentStudyStatusId: number;
    studentAgencyTypeId: number;
    studentMinistryTypeId: number;
    studentStudyTypeId: number;
    studentUniversityTypeId: number;
    studentAddedQuarter: number;
    studentLastQuarter: number;
    /** Current quarter as a YYYYQ code (year + quarter number), e.g. '20242'. */
    currentQuarter: string = '';
    studyUniversityIn: number;
    studyCollegeIn: number;
    studyCountryIn: number;
    syudyGovIn: number;
    studyEducationIn: number;
    studyMajorIn: number;
    studentBirthdate: Date | null;
    studentBirthdatePlace: string;
    studentGender: number;
    studentPassportNumber: string;
    studentPassportIssueDate: Date | null;
    studentPassportExpireDate: Date | null;
    studentPassportIssueCountryId: number;
    studyUniversityFeeId: number;
    studyUniversityFeeStatusId: number;
    studentMidate: Date;
    archiveDate: Date;
    insertedToArchDate: Date;
    querterIs: number;
    /** Real STUDENT_DATA.studentId this snapshot came from (repeatable across rows). */
    studentId: number;
    /*
     * Totals across this student's archived finance rows, sent by the server alongside
     * the snapshot. Not part of the positional constructor — they are never constructed
     * by hand, only mapped from the API response.
     *
     * Distinct from studentAmount above, which is the archived allowance rather than a
     * disbursement, and has no wafar to net against.
     */
    archivedAmountReceived = 0;
    archivedWafarAmount = 0;
    archivedNetAmount = 0;
    /** Full-profile JSON captured at archive time (may be absent on historical rows). */
    profileSnapshot?: string;
    // ── Added fields (gap-fill) ───────────────────────────────────────────────
    amountCurrency: number;
    studentAge: string;
    universityFeeStatusCurrency: number;
    picturePath: string;
    studentEmail: string;
    studentCellphone: string;
    studentHomeNumber: string;
    studentEmergencyContact: string;

    constructor(
        id: number = 0,
        studentName: string = '',
        studentCountryId: number = 0,
        studentGovId: number = 0,
        studentMudId: number = 0,
        studentDelegationDecisionYear: string = '',
        studentDegreeId: number = 0,
        studentMajorId: number = 0,
        studentMajorName: string = '',
        studentDelegateDecisionNo: number = 0,
        studentStudyNoOfYears: number = 0,
        studentCurrentLevelId: number = 0,
        studentFirstReceiptOfDue: number = 0,
        studentAmount: number = 0,
        studentNote1: string = '',
        studentNote2: string = '',
        studentStudyStatusId: number = 0,
        studentAgencyTypeId: number = 0,
        studentMinistryTypeId: number = 0,
        studentStudyTypeId: number = 0,
        studentUniversityTypeId: number = 0,
        studentAddedQuarter: number = 0,
        studentLastQuarter: number = 0,
        studyUniversityIn: number = 0,
        studyCollegeIn: number = 999,
        studyCountryIn: number = 0,
        syudyGovIn: number = 0,
        studyEducationIn: number = 0,
        studyMajorIn: number = 0,
        studentBirthdate: Date | null = null,
        studentBirthdatePlace: string = '',
        studentGender: number = 0,
        studentPassportNumber: string = '',
        studentPassportIssueDate: Date | null = null,
        studentPassportExpireDate: Date | null = null,
        studentPassportIssueCountryId: number = 0,
        studyUniversityFeeId: number = 0,
        studyUniversityFeeStatusId: number = 0,
        studentMidate: Date = new Date(),
        archiveDate: Date = new Date(),
        insertedToArchDate: Date = new Date(),
        querterIs: number = 0,
        studentId: number = 0,
        profileSnapshot: string = '',
        amountCurrency: number = 0,
        studentAge: string = '',
        universityFeeStatusCurrency: number = 0,
        picturePath: string = '',
        studentEmail: string = '',
        studentCellphone: string = '',
        studentHomeNumber: string = '',
        studentEmergencyContact: string = ''
    ) {
        this.id = id;
        this.studentName = studentName;
        this.studentCountryId = studentCountryId;
        this.studentGovId = studentGovId;
        this.studentMudId = studentMudId;
        this.studentDelegationDecisionYear = studentDelegationDecisionYear;
        this.studentDegreeId = studentDegreeId;
        this.studentMajorId = studentMajorId;
        this.studentMajorName = studentMajorName;
        this.studentDelegateDecisionNo = studentDelegateDecisionNo;
        this.studentStudyNoOfYears = studentStudyNoOfYears;
        this.studentCurrentLevelId = studentCurrentLevelId;
        this.studentFirstReceiptOfDue = studentFirstReceiptOfDue;
        this.studentAmount = studentAmount;
        this.studentNote1 = studentNote1;
        this.studentNote2 = studentNote2;
        this.studentStudyStatusId = studentStudyStatusId;
        this.studentAgencyTypeId = studentAgencyTypeId;
        this.studentMinistryTypeId = studentMinistryTypeId;
        this.studentStudyTypeId = studentStudyTypeId;
        this.studentUniversityTypeId = studentUniversityTypeId;
        this.studentAddedQuarter = studentAddedQuarter;
        this.studentLastQuarter = studentLastQuarter;
        this.studyUniversityIn = studyUniversityIn;
        this.studyCollegeIn = studyCollegeIn;
        this.studyCountryIn = studyCountryIn;
        this.syudyGovIn = syudyGovIn;
        this.studyEducationIn = studyEducationIn;
        this.studyMajorIn = studyMajorIn;
        this.studentBirthdate = studentBirthdate;
        this.studentBirthdatePlace = studentBirthdatePlace;
        this.studentGender = studentGender;
        this.studentPassportNumber = studentPassportNumber;
        this.studentPassportIssueDate = studentPassportIssueDate;
        this.studentPassportExpireDate = studentPassportExpireDate;
        this.studentPassportIssueCountryId = studentPassportIssueCountryId;
        this.studyUniversityFeeId = studyUniversityFeeId;
        this.studyUniversityFeeStatusId = studyUniversityFeeStatusId;
        this.studentMidate = studentMidate;
        this.archiveDate = archiveDate;
        this.insertedToArchDate = insertedToArchDate;
        this.querterIs = querterIs;
        this.studentId = studentId;
        this.profileSnapshot = profileSnapshot;
        this.amountCurrency = amountCurrency;
        this.studentAge = studentAge;
        this.universityFeeStatusCurrency = universityFeeStatusCurrency;
        this.picturePath = picturePath;
        this.studentEmail = studentEmail;
        this.studentCellphone = studentCellphone;
        this.studentHomeNumber = studentHomeNumber;
        this.studentEmergencyContact = studentEmergencyContact;
    }

    getFormattedAmount(): string {
        return this.studentAmount ? this.studentAmount.toLocaleString() : '0';
    }

    getArchiveDateFormatted(): string {
        return this.archiveDate ? new Date(this.archiveDate).toLocaleDateString() : 'N/A';
    }

    getStudentMidateFormatted(): string {
        return this.studentMidate ? new Date(this.studentMidate).toLocaleDateString() : 'N/A';
    }
}
