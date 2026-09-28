export class StudyFinReceivedWhom {
  id: number;
  finReceivedName: string;
  finReceivedPassportNumber: string;
  finReceivedPassportIssueDate: string;
  finReceivedPassportExpireDate: string;
  finReceivedIssueCountry: number;
  finRecievedYear: number;
  finRecievedMonth: number;
  finRecievedYearMonth: number;
  finRecievedAmount: number;
  financeCountryId: number;
  financeGovId: number;
  financeBankeId: number;
  financeBankBranchId: number;
  financeDofId: number;
  checkId: string;
  checkDate: string;
  checkDetails: string;
  checkFormNumber: string;
  finRecievedStatus: number;
  note1: string;
  finReceivedReason: string;
  finReceivedRelationship: string;
  finManualFormNumber: string;
  finManualFormDate: string;
  studentForDelegationId: number;
  studyReceivedWhomMidate: string;

  constructor() {
    this.id = 0;
    this.finReceivedName = "";
    this.finReceivedPassportNumber = "";
    this.finReceivedPassportIssueDate = "";
    this.finReceivedPassportExpireDate = "";
    this.finReceivedIssueCountry = 0;
    this.finRecievedYear = 0;
    this.finRecievedMonth = 0;
    this.finRecievedYearMonth = 0;
    this.finRecievedAmount = 0;
    this.financeCountryId = 0;
    this.financeGovId = 0;
    this.financeBankeId = 0;
    this.financeBankBranchId = 0;
    this.financeDofId = 0;
    this.checkId = "";
    this.checkDate = "";
    this.checkDetails = "";
    this.checkFormNumber = "";
    this.finRecievedStatus = 0;
    this.note1 = "";
    this.finReceivedReason = "";
    this.finReceivedRelationship = "";
    this.finManualFormNumber = "";
    this.finManualFormDate = "";
    this.studentForDelegationId = 0;
    this.studyReceivedWhomMidate = "";
  }
}
