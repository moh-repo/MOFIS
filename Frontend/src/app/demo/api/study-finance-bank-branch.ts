export class StudyFinanceBankBranch {
  id: number;
  studyFinanceBankBranchName: string;
  /** STUDY_FINANCE_BANK_BRANCH_NAME_EN; see study-wafar-type. */
  studyFinanceBankBranchNameEn: string | null;
  studyFinanceBankeId: number;
  studyCountryInId: number;
  studyGovInId: number;
  studyFinanceBankBranchMidate: string;

  constructor() {
    this.id = 0;
    this.studyFinanceBankBranchName = "";
    this.studyFinanceBankBranchNameEn = null;
    this.studyFinanceBankeId = 0;
    this.studyCountryInId = 0;
    this.studyGovInId = 0;
    this.studyFinanceBankBranchMidate = "";
  }
}
