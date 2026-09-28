import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

export interface WafarSpendingCategoryDto {
    id: number;
    categoryCode: string;
    categoryNameAr: string;
    categoryNameEn: string;
    isActive: boolean;
}

export interface WafarSpendingDto {
    id: number;
    category: WafarSpendingCategoryDto;
    amount: number;
    spendingDate: string;
    referenceNumber: string;
    descriptionAr: string;
    descriptionEn: string;
    payeeNameAr: string;
    payeeNameEn: string;
    studentId: number;
    studentName: string;
    /** The attaché that holds this Wafar. Null on rows written before it was recorded. */
    studyCountryInId?: number | null;
    beneficiaryType: string;
    status: string;
    notes: string;
    createdBy: number;
    createdAt: string;
    approvedBy: number;
    approvedAt: string;
    rejectionReason: string;
}

/** N-04.e: only the fields given are changed; `cancel` cancels a still-pending row. */
export interface WafarCorrectionRequest {
    amount?: number | null;
    spendingDate?: string | null;
    descriptionAr?: string | null;
    descriptionEn?: string | null;
    notes?: string | null;
    cancel?: boolean | null;
    reason: string;
}

export interface WafarSpendingCreateDto {
    categoryId: number;
    amount: number;
    spendingDate: string;
    referenceNumber?: string;
    descriptionAr?: string;
    descriptionEn?: string;
    payeeNameAr?: string;
    payeeNameEn?: string;
    studentId?: number;
    /**
     * The attaché paying. Optional when a student is named — the server takes the
     * student's own country — and when the signed-in user has exactly one attaché.
     * Required otherwise; the server refuses rather than storing a transaction no
     * attaché owns.
     */
    studyCountryInId?: number | null;
    beneficiaryType?: string;
    academicYear?: string;
    notes?: string;
}

export interface WafarBalanceSummaryDto {
    totalDeposits: number;
    totalSpent: number;
    currentBalance: number;
    pendingAmount: number;
    transactionCount: number;
}

export interface WafarStudentFeePaymentDto {
    id: number;
    spendingId: number;
    studentId: number;
    academicYear: string;
    notes: string;
    createdAt: string;
    amount: number;
    spendingDate: string;
    referenceNumber: string;
    status: string;
}

@Injectable({ providedIn: 'root' })
export class WafarService {
    private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/wafar`;

    constructor(private http: HttpClient) {}

    getBalance(): Observable<WafarBalanceSummaryDto> {
        return this.http.get<WafarBalanceSummaryDto>(`${this.baseUrl}/balance`);
    }

    getCategories(): Observable<WafarSpendingCategoryDto[]> {
        return this.http.get<WafarSpendingCategoryDto[]>(`${this.baseUrl}/categories`);
    }

    getTransactions(filters: {
        categoryId?: number;
        status?: string;
        studentId?: number;
        dateFrom?: string;
        dateTo?: string;
    } = {}): Observable<WafarSpendingDto[]> {
        let params = new HttpParams();
        if (filters.categoryId != null) params = params.set('categoryId', filters.categoryId);
        if (filters.status) params = params.set('status', filters.status);
        if (filters.studentId != null) params = params.set('studentId', filters.studentId);
        if (filters.dateFrom) params = params.set('dateFrom', filters.dateFrom);
        if (filters.dateTo) params = params.set('dateTo', filters.dateTo);
        return this.http.get<WafarSpendingDto[]>(`${this.baseUrl}/transactions`, { params });
    }

    getTransaction(id: number): Observable<WafarSpendingDto> {
        return this.http.get<WafarSpendingDto>(`${this.baseUrl}/transactions/${id}`);
    }

    createTransaction(dto: WafarSpendingCreateDto): Observable<WafarSpendingDto> {
        return this.http.post<WafarSpendingDto>(`${this.baseUrl}/transactions`, dto);
    }

    updateTransaction(id: number, dto: WafarSpendingCreateDto): Observable<WafarSpendingDto> {
        return this.http.put<WafarSpendingDto>(`${this.baseUrl}/transactions/${id}`, dto);
    }

    approveTransaction(id: number): Observable<WafarSpendingDto> {
        return this.http.put<WafarSpendingDto>(`${this.baseUrl}/transactions/${id}/approve`, {});
    }

    rejectTransaction(id: number, reason: string): Observable<WafarSpendingDto> {
        return this.http.put<WafarSpendingDto>(`${this.baseUrl}/transactions/${id}/reject`, { reason });
    }

    cancelTransaction(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/transactions/${id}`);
    }

    /** N-04.e: a ministry account proposes a correction; the attaché's administrator approves it. */
    requestCorrection(id: number, correction: WafarCorrectionRequest): Observable<{ status: string; requestId: number; message: string }> {
        return this.http.post<{ status: string; requestId: number; message: string }>(
            `${this.baseUrl}/transactions/${id}/correction-requests`, correction);
    }

    getStudentFeePayments(studentId: number): Observable<WafarStudentFeePaymentDto[]> {
        return this.http.get<WafarStudentFeePaymentDto[]>(`${this.baseUrl}/student-fee-payments/${studentId}`);
    }
}
