export interface LookupTable {
    id: number;
    name: string;
    description?: string;
    isActive?: boolean;
    createdDate?: Date;
    modifiedDate?: Date;
}

export interface StudentGender extends LookupTable {
    studentGenderName: string;
    studentGenderMidate?: Date;
}

export interface StudyReceivedWhomStatus extends LookupTable {
    studyReceivedWhomStatusName: string;
}

export interface MoheMain extends LookupTable {
    moheMainName: string;
}

export interface StudentDegree extends LookupTable {
    studentDegreeName: string;
}

export interface StudentCurrentLevel extends LookupTable {
    studentCurrentLevelName: string;
}

export interface StudyStatus extends LookupTable {
    studyStatusName: string;
}

export interface StudyDegreeIn extends LookupTable {
    studyDegreeInName: string;
}

export interface StudentUniversityType extends LookupTable {
    studentUniversityTypeName: string;
}

export interface StudentAgencyType extends LookupTable {
    studentAgencyTypeName: string;
}

export interface MoheSubMain extends LookupTable {
    moheSubMainName: string;
    moheMainId: number;
}

export interface Country extends LookupTable {
    studentCountryId: number;
    studentCountryName: string;
    studentCountryOrderNo?: number;
    studentCountryStatus?: number;
    studentCountryInsertedDate?: Date;
}

export interface Governorate extends LookupTable {
    id: number;
    studentGovName: string;
    studentCountryId: number;
    studentMidate?: Date;
}

export interface Municipality extends LookupTable {
    id: number;
    studentMudName: string;
    studentCountryId: number;
    /**
     * The governorate this municipality belongs to. Text until V129, which made
     * it an int so it could carry a foreign key to STUDENT_GOV (plan W6-06).
     */
    studentGovId: number;
    studentMidate?: Date;
}

export interface LookupTableType {
    id: string;
    name: string;
    entityName: string;
    description: string;
}

/** Registry mirrored by settings/lookuptables/lookuptables.component.ts tab groups. */
export const LOOKUP_TABLE_TYPES: LookupTableType[] = [
    // Student
    { id: 'student-gender', name: 'Student Gender', entityName: 'StudentGender', description: 'Manage student gender options' },
    { id: 'student-degree', name: 'Student Degree', entityName: 'StudentDegree', description: 'Manage student degree types' },
    { id: 'student-current-level', name: 'Student Current Level', entityName: 'StudentCurrentLevel', description: 'Manage student current level options' },
    { id: 'student-agency-type', name: 'Student Agency Type', entityName: 'StudentAgencyType', description: 'Manage student agency types' },
    { id: 'student-ministry-type', name: 'Student Ministry Type', entityName: 'StudentMinistryType', description: 'Manage student ministry types' },
    { id: 'student-study-type', name: 'Student Study Type', entityName: 'StudentStudyType', description: 'Manage student study types' },
    { id: 'student-study-status', name: 'Student Study Status', entityName: 'StudentStudyStatus', description: 'Manage student study status' },
    { id: 'death-procedures', name: 'Death Procedure', entityName: 'LuDeathProcedure', description: 'Manage death case procedure steps' },
    // Study & University
    { id: 'study-status', name: 'Study Status', entityName: 'StudyStatus', description: 'Manage study status options' },
    { id: 'study-degree-in', name: 'Study Degree In', entityName: 'StudyDegreeIn', description: 'Manage study degree in options' },
    { id: 'student-university-type', name: 'Student University Type', entityName: 'StudentUniversityType', description: 'Manage student university types' },
    { id: 'student-major', name: 'Student Major', entityName: 'StudentMajor', description: 'Manage student majors' },
    { id: 'study-received-whom-status', name: 'Study Received Whom Status', entityName: 'StudyReceivedWhomStatus', description: 'Manage study received whom status options' },
    // Finance
    { id: 'wafar-spending-statuses', name: 'Wafar Spending Status', entityName: 'LuWafarSpendingStatus', description: 'Manage Wafar spending statuses' },
    { id: 'wafar-reconciliation-statuses', name: 'Wafar Reconciliation Status', entityName: 'LuWafarReconciliationStatus', description: 'Manage Wafar reconciliation statuses' },
    { id: 'payment-auth-statuses', name: 'Payment Auth Status', entityName: 'LuPaymentAuthStatus', description: 'Manage payment authorization statuses' },
    { id: 'payment-amount-sources', name: 'Payment Amount Source', entityName: 'LuPaymentAmountSource', description: 'Manage payment amount sources' },
    { id: 'books-allowance-pay-statuses', name: 'Books Allowance Pay Status', entityName: 'LuBooksAllowancePayStatus', description: 'Manage books allowance payment statuses' },
    { id: 'loan-statuses', name: 'Loan Status', entityName: 'LuLoanStatus', description: 'Manage student loan statuses' },
    // Operations
    { id: 'change-request-statuses', name: 'Change Request Status', entityName: 'LuChangeRequestStatus', description: 'Manage change request statuses' },
    { id: 'quarter-numbers', name: 'Quarter Number', entityName: 'LuQuarterNumber', description: 'Manage quarter number labels' },
    { id: 'mohe-main', name: 'MOHE Main', entityName: 'MoheMain', description: 'Manage MOHE main categories' },
    { id: 'mohe-sub-main', name: 'MOHE Sub Main', entityName: 'MoheSubMain', description: 'Manage MOHE sub main categories' },
    // System
    { id: 'notification-channels', name: 'Notification Channel', entityName: 'LuNotificationChannel', description: 'Manage notification channel types' },
    { id: 'notification-recipient-types', name: 'Notification Recipient Type', entityName: 'LuNotificationRecipientType', description: 'Manage notification recipient types' },
    { id: 'notification-scopes', name: 'Notification Scope', entityName: 'LuNotificationScope', description: 'Manage notification scope options' },
    { id: 'student-portal-account-statuses', name: 'Student Portal Account Status', entityName: 'LuStudentPortalAccountStatus', description: 'Manage student portal account statuses' },
    { id: 'report-dimensions', name: 'Report Dimension', entityName: 'LuReportDimension', description: 'Manage report group-by dimensions' },
    { id: 'trend-classes', name: 'Trend Class', entityName: 'LuTrendClass', description: 'Manage growth and confidence trend classes' },
    { id: 'report-labels', name: 'Report Label', entityName: 'LuReportLabel', description: 'Manage Jasper report header/footer labels' },
    // Location
    { id: 'country', name: 'Country', entityName: 'Country', description: 'Manage countries' },
    { id: 'governorate', name: 'Governorate', entityName: 'Governorate', description: 'Manage governorates' },
    { id: 'municipality', name: 'Municipality', entityName: 'Municipality', description: 'Manage municipalities' }
];
