export class StudentBooksAllowance {
  booksAllowId: number;
  studentId: number;
  studyYear: string | null;
  moheQuarterId: number | null;
  allowanceAmount: number;
  payStatus: number;
  receiptDate: string | null;
  notes: string | null;
  /**
   * When the row was last written. Read-only: `MIDATE` is NOT NULL and the
   * server stamps it. The dialogs used to send it as null, which is what made
   * every Add and Edit fail, so it is optional here and never put in a payload.
   */
  midate?: string | null;
  /** The country the row is filed under, derived from the student. Read-only. */
  studyCountryInId?: number | null;

  constructor() {
    this.booksAllowId = 0;
    this.studentId = 0;
    this.studyYear = null;
    this.moheQuarterId = null;
    this.allowanceAmount = 0;
    this.payStatus = 0;
    this.receiptDate = null;
    this.notes = null;
    this.midate = null;
  }
}
