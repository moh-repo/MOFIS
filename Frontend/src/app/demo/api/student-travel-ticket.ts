export interface StudentTravelTicketFamilyMember {
    familyMemberId?: number;
    travelTicketId?: number;
    memberName: string;
    relationship?: string;
    birthdate: string | Date;
    age?: number;
    ticketCost?: number;
    passportNumber?: string;
    passportExpiryDate?: string | Date;
    gender?: number;
    notes?: string;
    createdDate?: string | Date;
    createdBy?: string;
    modifiedDate?: string | Date;
    modifiedBy?: string;
}

export interface StudentTravelTicket {
    travelTicketId?: number;
    studentId: number;
    travelType: 'ALONE' | 'WITH_FAMILY';
    travelDate?: string | Date;
    departureCountry?: string;
    destinationCountry?: string;
    departureCity?: string;
    destinationCity?: string;
    airline?: string;
    flightNumber?: string;
    ticketNumber?: string;
    ticketCost?: number;
    totalTicketCost?: number;
    currencyId?: number;
    fundingSourceId?: number;
    bookingDate?: string | Date;
    travelPurpose?: string;
    notes?: string;
    createdDate?: string | Date;
    createdBy?: string;
    modifiedDate?: string | Date;
    modifiedBy?: string;
    /** The ticket PDF, a student document (V116, review T3). */
    documentId?: number | null;
    familyMembers?: StudentTravelTicketFamilyMember[];
}

export interface StudentTravelTicketFilters {
    studentId?: number | null;
    travelType?: string | null;
}

export interface TravelFundingSource {
    id: number;
    code: string;
    name: string;
    nameEn?: string;
    nameAr?: string;
}

export interface WafarBalanceSummary {
    totalDeposits: number;
    totalSpent: number;
    currentBalance: number;
    pendingAmount: number;
    transactionCount: number;
}

