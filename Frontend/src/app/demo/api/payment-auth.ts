export class PaymentAuth {
  id: number;
  authFormNumber: string;
  authDate: string;
  studentId: number;
  studentName: string;
  ministryName: string;
  deptName: string;
  provinceName: string;
  bankName: string;
  bankBranch: string;
  sentTo: string;
  /** FINANCE | WAFAR | UNIVERSITY_FEE | COMBINATION | ALL */
  amountSource: string;
  refFinanceId: number;
  refWafarId: number;
  refUniversityFeeId: number;
  originalAmount: number;
  deductions: number;
  netAmount: number;
  paymentPurpose: string;
  checkNumber: string;
  checkDate: string;
  budgetBookNumber: string;
  recipientName: string;
  recipientIdNumber: string;
  /** DRAFT | APPROVED | PAID */
  authStatus: string;
  notes: string;
  createdBy: number;
  authMidate: string;

  constructor() {
    this.id = 0;
    this.authFormNumber = '';
    this.authDate = '';
    this.studentId = 0;
    this.studentName = '';
    this.ministryName = '';
    this.deptName = '';
    this.provinceName = '';
    this.bankName = '';
    this.bankBranch = '';
    this.sentTo = '';
    this.amountSource = 'ALL';
    this.refFinanceId = 0;
    this.refWafarId = 0;
    this.refUniversityFeeId = 0;
    this.originalAmount = 0;
    this.deductions = 0;
    this.netAmount = 0;
    this.paymentPurpose = '';
    this.checkNumber = '';
    this.checkDate = '';
    this.budgetBookNumber = '';
    this.recipientName = '';
    this.recipientIdNumber = '';
    this.authStatus = 'DRAFT';
    this.notes = '';
    this.createdBy = 0;
    this.authMidate = '';
  }
}
