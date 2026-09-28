export interface StudentProgressDto {
  studentId: number;
  currentYearLabel: string;
  /** The same year in English; pick with the `inLanguage` pipe. */
  currentYearLabelEn?: string;
  currentYearOrder: number;
  totalYears: number;
  progressPercent: number;
}
