export class StudentLoan {
  /**
   * Set by the database. `LOAN_ID` is an identity column, so a create must not
   * carry one — a non-null id sends Spring Data down `merge()` and every Add
   * answers 409.
   */
  loanId?: number;
  studentId: number;
  studyYear: string | null;
  moheQuarterId: number | null;
  loanAmount: number;
  currencyId: number;
  loanStatus: number;
  repaymentNotes: string | null;
  /** Stamped by the server on every write; read-only here. */
  midate?: string | null;
  /** Derived from the student by the server; read-only here. */
  studyCountryInId?: number | null;

  constructor() {
    this.studentId = 0;
    this.studyYear = null;
    this.moheQuarterId = null;
    this.loanAmount = 0;
    this.currencyId = 0;
    this.loanStatus = 0;
    this.repaymentNotes = null;
  }
}
