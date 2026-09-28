export class StudyFinanceBank {
  id: number;
  studyFinanceBankeName: string;
  /** STUDY_FINANCE_BANKE_NAME_EN; see study-wafar-type. */
  studyFinanceBankeNameEn: string | null;
  studyCountryInId: number;
  studyFinanceBankMidate: string;

  constructor() {
    this.id = 0;
    this.studyFinanceBankeName = "";
    this.studyFinanceBankeNameEn = null;
    this.studyCountryInId = 0;
    this.studyFinanceBankMidate = "";
  }
}
