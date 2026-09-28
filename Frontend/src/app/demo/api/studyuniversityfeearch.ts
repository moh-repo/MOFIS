export class StudyUniversityFeeArch {
  studentId: number;
  studyUniversityFeeId: number;
  studyUniversityFeeStudentName: string;
  studyUniversityFeeFirstYear: string;
  studyUniversityFeeAmount: number;
  studyUniversityFeeYear: string;
  studyUniversityFeeDiscount: number;
  studyUniversityRealFeeAmount: number;
  studyUniversityFeeCurrency: number;
  studyUniversityFeePayDate: Date;
  studyUniversityFeePayStatus: number;
  studyUniversityCheckNo: string;
  studyUniversityCheckDate: Date;
  studyUniversityCheckNotes: string;
  studyUniversityCheckFormNumber: string;
  studyUniversityFeeMidate: Date;
  studyUniversityFeeArchDate: Date;

  constructor() {
    this.studentId = 0;
    this.studyUniversityFeeId = 0;
    this.studyUniversityFeeStudentName = "";
    this.studyUniversityFeeFirstYear = "";
    this.studyUniversityFeeAmount = 0;
    this.studyUniversityFeeYear = "";
    this.studyUniversityFeeDiscount = 0;
    this.studyUniversityRealFeeAmount = 0;
    this.studyUniversityFeeCurrency = 0;
    this.studyUniversityFeePayDate = new Date();
    this.studyUniversityFeePayStatus = 0;
    this.studyUniversityCheckNo = "";
    this.studyUniversityCheckDate = new Date();
    this.studyUniversityCheckNotes = "";
    this.studyUniversityCheckFormNumber = "";
    this.studyUniversityFeeMidate = new Date();
    this.studyUniversityFeeArchDate = new Date();
  }
}
