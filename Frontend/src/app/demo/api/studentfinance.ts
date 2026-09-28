export class StudentFinance {
  id: number;
  studentFinId: string;
  studentFinName: string;
  studentFinRecievedYear: number;
  studentFinRecievedMonth: number;
  studentFinRecievedYearMonth: number;
  studentFinRecievedAmount: number;
  studyCurrencyInId: number | null;
  studyCountryInId: number;
  studyGovInId: number;
  studyFinanceBankeId: number;
  studyFinanceBankBranchId: number;
  studyFinanceDofId: number;
  studyCheckId: string;
  studyCheckDate: Date;
  studyCheckDetails: string;
  studyCheckFormNumber: string;
  studyFinRecievedStatus: number;
  studyNote1: string;
  studyReceivedWhomStatusId: number;
  studyReceivedWhomId: number;
  wafarAmount: number;
  wafarTypeId: number;
  /**
   * Wafar note. Not a STUDENT_FINANCE column — it belongs to the wafar record,
   * which is written only when the finance request is approved, so the note
   * rides along in the approval payload.
   */
  wafarNote: string;
  /**
   * Amount received minus wafar. A PERSISTED computed column on STUDENT_FINANCE, so it
   * is read-only: the server ignores it on the way in and recomputes it on every write.
   *
   * Null rather than 0 when unknown: callers fall back to `amount - wafar` with `??`, and
   * a 0 default would satisfy that operator and render a hard zero against a database
   * where V015 has not been applied yet.
   */
  netAmount: number | null;
  studyFinanceMidate: Date;
  studentBankId: number;
  studentAccountNumber: string;
  studentAccountEnglishName: string;
  studentAccountEndDate: string;
  studentBankNotes: string;

  constructor() {
      this.id = 0;
      this.studentFinId = "";
      this.studentFinName = "";
      this.studentFinRecievedYear = 0;
      this.studentFinRecievedMonth = 0;
      this.studentFinRecievedYearMonth = 0;
      this.studentFinRecievedAmount = 0;
      this.studyCurrencyInId = null;
      this.studyCountryInId = 0;
      this.studyGovInId = 0;
      this.studyFinanceBankeId = 0;
      this.studyFinanceBankBranchId = 0;
      this.studyFinanceDofId = 0;
      this.studyCheckId = "";
      this.studyCheckDate = new Date();
      this.studyCheckDetails = "";
      this.studyCheckFormNumber = "";
      this.studyFinRecievedStatus = 0;
      this.studyNote1 = "";
      this.studyReceivedWhomStatusId = 0;
      this.studyReceivedWhomId = 0;
      this.wafarAmount = 0;
      this.wafarTypeId = 0;
      this.wafarNote = "";
      this.netAmount = null;
      this.studyFinanceMidate = new Date();
      this.studentBankId = 0;
      this.studentAccountNumber = "";
      this.studentAccountEnglishName = "";
      this.studentAccountEndDate = "";
      this.studentBankNotes = "";
  }
}