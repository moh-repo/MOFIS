export class StudentFinanceWafar {
  id: number;
  studentFinId: string;
  studentFinName: string;
  studentFinRecievedYear: number;
  studentFinRecievedMonth: number;
  studentFinRecievedYearMonth: number;
  studentFinRecievedAmount: number;
  studyCountryInId: number;
  studyGovInId: number;
  studyFinanceBankeId: number;
  studyFinanceBankBranchId: number;
  studyFinanceDofId: number;
  studyFinRecievedStatus: number;
  wafarTypeId: number;
  studyNote1: string;
  studyFinanceMidate: string | null;

  constructor() {
    this.id = 0;
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
    this.studyFinRecievedStatus = 0;
    this.wafarTypeId = 0;
    this.studyNote1 = '';
    this.studyFinanceMidate = null;
  }
}
