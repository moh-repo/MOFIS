export interface StudentQuickEditDto {
  // From STUDENT_DATA
  studentId?: number;
  studentName?: string;
  studentDelegationDecisionYear?: string;
  studentDegreeId?: number;
  studentMajorId?: number;
  studentDelegateDecisionNo?: number;
  studentStudyNoOfYears?: number;
  studentCurrentLevelId?: number;
  studentFirstReceiptOfDue?: number;
  studentAmount?: number;
  studentNote1?: string;
  studentNote2?: string;
  studentStudyStatusId?: number;
  studentAddedQuarter?: number;
  studentLastQuarter?: number;
  currentQuarter?: string; // YYYYQ code (year + quarter number), e.g. '20242'
  studentAge?: string;
  studentGender?: number;
  studentPassportNumber?: string;
  studyUniversityFeeId?: number;
  studyUniversityFeeStatusId?: number;
  studentAgencyTypeId?: number;
  studentMinistryTypeId?: number;
  studentStudyTypeId?: number;
  studentUniversityTypeId?: number;
  studentBirthdatePlace?: string;
  studentPassportIssueCountryId?: number;
  studentEmail?: string;
  studentCellphone?: string;
  studentHomeNumber?: string;
  studentEmergencyContact?: string;

  // From STUDENT_FINANCE
  studentFinId?: string;
  studentFinName?: string;
  studentFinRecievedYear?: number;
  studentFinRecievedMonth?: number;
  studentFinRecievedYearMonth?: number;
  studentFinRecievedAmount?: number;
  studyCountryInId?: number;
  studyGovInId?: number;
  studyFinanceBankeId?: number;
  studyFinanceBankBranchId?: number;
  studyFinanceDofId?: number;
  studyFinRecievedStatus?: number;
  studyNote1?: string;
  studyReceivedWhomStatusId?: number;
  studyReceivedWhomId?: number;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface LookupOption {
  id: number;
  name: string;
}

export interface StudentQuickEditMetadata {
  genders: LookupOption[];
  statuses: LookupOption[];
  degrees: LookupOption[];
  majors: LookupOption[];
  levels: LookupOption[];
  countries: LookupOption[];
  governments: LookupOption[];
  banks: LookupOption[];
  bankBranches: LookupOption[];
  dofs: LookupOption[];
  universityFeeStatuses: LookupOption[];
  finReceivedStatuses: LookupOption[];
  receivedWhomStatuses: LookupOption[];
  agencyTypes: LookupOption[];
  ministryTypes: LookupOption[];
  studyTypes: LookupOption[];
  universityTypes: LookupOption[];
  homeCountries: LookupOption[];
  finReceivedWhoms: LookupOption[];
}

