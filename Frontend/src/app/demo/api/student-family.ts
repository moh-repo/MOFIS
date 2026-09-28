export interface StudentParentDto {
  parentId?: number;
  studentId?: number;
  relationshipId: number;
  relationshipName?: string;
  parentName: string;
  nationalId?: string;
  occupation?: string;
  phone?: string;
  address?: string;
  isAlive?: boolean;
  notes?: string;
}

export interface StudentRelativeDto {
  relativeId?: number;
  studentId?: number;
  relatedStudentId: number;
  relatedStudentName?: string;
  relatedStudentStatusId?: number;
  relatedStudentStatusName?: string;
  relatedStudentCurrentLevelId?: number;
  relatedStudentCurrentLevelName?: string;
  relationshipId: number;
  relationshipName?: string;
  notes?: string;
}

export interface StudentRelativeSearchDto {
  studentId: number;
  studentName: string;
  passportNumber?: string;
  studyStatusId?: number;
  studyStatusName?: string;
  currentLevelId?: number;
  currentLevelName?: string;
}

export interface StudentDeathProcedureStepDto {
  stepId?: number;
  deathCaseId?: number;
  procedureId?: number;
  procedureCode?: string;
  procedureNameAr?: string;
  procedureNameEn?: string;
  sortOrder?: number;
  isCompleted?: boolean;
  completedDate?: string;
  completedBy?: string;
  notes?: string;
}

export interface StudentDeathCaseDto {
  deathCaseId?: number;
  studentId?: number;
  dateOfDeath: string;
  levelAtDeathId?: number;
  levelAtDeathName?: string;
  studyYearAtDeath?: number;
  placeOfDeath?: string;
  cause?: string;
  reportedDate?: string;
  reportedBy?: string;
  caseStatus?: string;
  notes?: string;
  steps?: StudentDeathProcedureStepDto[];
}

export interface StudentDeathCaseRequest {
  dateOfDeath: string;
  levelAtDeathId?: number;
  studyYearAtDeath?: number;
  placeOfDeath?: string;
  cause?: string;
  notes?: string;
}

export interface StudentDeathProcedureStepUpdateRequest {
  isCompleted: boolean;
  notes?: string;
}
