export interface LookupOptionResponse {
    id: number | null;
    name: string;
}

/** A selectable option whose stored `value` is language-neutral and `label` is localized. */
export interface LocalizedOptionResponse {
    value: string;
    label: string;
    labelEn?: string;
    labelAr?: string;
}

export interface StudentExtensionMetadataResponse {
    students: LookupOptionResponse[];
    extensionTypes: LocalizedOptionResponse[];
    statuses: LocalizedOptionResponse[];
}

export interface StudentExtensionResponse {
    extensionId: number;
    studentId: number;
    studentName?: string;
    extensionType?: string;
    /** Localized display label for `extensionType` in the active language. */
    extensionTypeLabel?: string;
    extensionQuarters?: number | null;
    extensionStartDate?: string | null;
    extensionEndDate?: string | null;
    extensionReason?: string | null;
    extensionReasonDetails?: string | null;
    extensionDecisionNo?: string | null;
    extensionDecisionDate?: string | null;
    approvedBy?: string | null;
    extensionStatus?: string | null;
    /** Localized display label for `extensionStatus` in the active language. */
    extensionStatusLabel?: string | null;
    createdDate?: string | null;
    createdBy?: string | null;
    modifiedDate?: string | null;
    modifiedBy?: string | null;
    notes?: string | null;
}

export interface StudentExtensionFilters {
    studentId?: number | null;
    filterStudentId?: number | null;
    extensionType?: string | null;
    status?: string | null;
    passport?: string | null;
}

export interface StudentExtensionPayload {
    studentId: number;
    extensionType: string;
    extensionStatus: string;
    extensionQuarters?: number | null;
    extensionStartDate?: string | null;
    extensionEndDate?: string | null;
    extensionReason?: string | null;
    extensionReasonDetails?: string | null;
    extensionDecisionNo?: string | null;
    extensionDecisionDate?: string | null;
    approvedBy?: string | null;
    notes?: string | null;
    createdBy?: string | null;
    modifiedBy?: string | null;
}


