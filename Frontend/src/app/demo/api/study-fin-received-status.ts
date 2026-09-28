export class StudyFinReceivedStatus {
  id: number;
  studyFinReceivedStatusName: string;
  studyFinReceivedStatusMidate: string;
  /** Stable identifier — RECEIVED, REJECTED, ON_HOLD, … */
  code?: string | null;
  /** The severity the row is painted with, as STUDY_FIN_RECEIVED_STATUS states it. */
  uiSeverity?: string | null;

  constructor() {
    this.id = 0;
    this.studyFinReceivedStatusName = "";
    this.studyFinReceivedStatusMidate = "";
    this.code = null;
    this.uiSeverity = null;
  }
}
