export interface StudentYearResultDto {
  id?: number;
  studentId?: number;
  yearNumber: number;
  academicYear?: string;
  passed: boolean;
  grade?: string;
  notes?: string;
  studyYearStatusId?: number;
  moheQuarterId?: number;
  updatedAt?: string;
  /** Someone recorded this year's result; false for a seeded placeholder (FIX-PLAN P5-8). */
  recorded?: boolean;
}

/**
 * How a year reads (FIX-PLAN P5-8, D24-a): a seeded placeholder is "not recorded", not
 * "passed". The server decides what is recorded (`StudentYearResult.isRecorded`); a row
 * without the flag (an older answer) reads as before.
 */
export type YearOutcome = 'passed' | 'failed' | 'not-recorded';

export function yearOutcome(r: { passed: boolean; recorded?: boolean }): YearOutcome {
  if (r.recorded === false) return 'not-recorded';
  return r.passed ? 'passed' : 'failed';
}

/** The staff screens' tag text for each outcome. */
export const YEAR_OUTCOME_KEY: Record<YearOutcome, string> = {
  passed: 'STUDENT_QUICK_EDIT.YEAR_RESULTS.PASSED',
  failed: 'STUDENT_QUICK_EDIT.YEAR_RESULTS.FAILED',
  'not-recorded': 'STUDENT_QUICK_EDIT.YEAR_RESULTS.NOT_RECORDED'
};

/** The staff screens' tag colour for each outcome (PrimeNG severities). */
export const YEAR_OUTCOME_SEVERITY: Record<YearOutcome, 'success' | 'danger' | 'secondary'> = {
  passed: 'success',
  failed: 'danger',
  'not-recorded': 'secondary'
};
