import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

/** A segment of the account code: economic, administrative, funding source... */
export interface CoaSegment {
    id: number;
    segmentCode: string;
    nameAr: string;
    nameEn: string;
    ordinal: number;
    required: boolean;
    active: boolean;
}

/**
 * A level within a segment: باب، بند، نوع، مجموعة، حساب.
 *
 * `codeLength` is null until the widths are taken from the الدليل المحاسبي
 * الموحد, and null means "do not validate" — a guessed width would reject the
 * real manual on import.
 */
export interface CoaLevel {
    id: number;
    segmentId: number;
    levelNo: number;
    nameAr: string;
    nameEn: string;
    codeLength: number | null;
    active: boolean;
}

export interface CoaAccount {
    id: number;
    segmentId: number;
    levelNo: number;
    parentAccountId: number | null;
    code: string;
    fullCode: string;
    nameAr: string;
    nameEn: string | null;
    accountNature: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';
    normalBalance: 'D' | 'C';
    postable: boolean;
    budgetary: boolean;
    fiscalYearId: number;
    active: boolean;
    /**
     * The attaché this account belongs to. `null` means the unified chart
     * (الدليل المحاسبي الموحد), which every attaché books against — only the
     * ministry may create one of those.
     */
    studyCountryInId: number | null;
}

export interface FiscalYear {
    id: number;
    yearNo: number;
    nameAr: string;
    nameEn: string;
    startDate: string;
    endDate: string;
    status: 'OPEN' | 'CLOSED' | 'LOCKED';
}

export interface FiscalPeriod {
    id: number;
    fiscalYearId: number;
    periodNo: number;
    nameAr: string;
    nameEn: string;
    startDate: string;
    endDate: string;
    status: 'OPEN' | 'CLOSED';
    adjustment: boolean;
}

/**
 * A cultural attaché's advance (العهدة).
 *
 * `available` is un-earmarked cash and can never be negative. `wafarBalance` is
 * savings still held. Both are computed by the database.
 */
export interface AttacheAdvanceBalance {
    id: number;
    fiscalYearId: number;
    studyCountryInId: number;
    currencyId: number;
    openingBalance: number;
    transferred: number;
    disbursed: number;
    returned: number;
    wafarEarmarked: number;
    wafarSpent: number;
    wafarBalance: number;
    available: number;
}

/** A stored total that disagrees with the rows that produced it. All zeros is healthy. */
export interface AdvanceDiscrepancy {
    countryId: number;
    currencyId: number;
    transferredDelta: number;
    earmarkedDelta: number;
    spentDelta: number;
}

export interface ReconciliationResult {
    fiscalYearId: number;
    reconciled: boolean;
    discrepancies: AdvanceDiscrepancy[];
}

export interface ChartSummary {
    fiscalYearId: number;
    accountCount: number;
    postableCount: number;
    byLevel: Record<string, number>;
}

/** The streams the budget is broken into: stipend, books, travel, university fees, other. */
export interface FeeType {
    id: number;
    feeTypeCode: string;
    nameAr: string;
    nameEn: string;
    sourceTable: string | null;
    active: boolean;
}

export type AllocationStatus = 'DRAFT' | 'APPROVED' | 'TRANSFERRED' | 'CANCELLED';

/**
 * A quarterly transfer of budget to one attaché.
 *
 * `ADDITIONAL` allocations are rows in their own right, not adjustments: they
 * reach back over students added or corrected after the regular transfer went
 * out. Amounts are always positive — a downward correction becomes Wafar.
 */
export interface AttacheAllocation {
    id: number;
    fiscalYearId: number;
    /** The YYYYQ key of MOHE_QUARTER, e.g. 20253. */
    moheQuarterKey: number;
    studyCountryInId: number;
    allocationType: 'REGULAR' | 'ADDITIONAL';
    feeTypeId: number | null;
    currencyId: number;
    amount: number;
    status: AllocationStatus;
    transferDate: string | null;
    transferReference: string | null;
    notes: string | null;
    createdBy: number | null;
    createdAt: string | null;
    updatedBy: number | null;
    updatedAt: string | null;
}

export type PaymentBatchStatus = 'OPEN' | 'APPROVED' | 'DISBURSED' | 'CLOSED' | 'CANCELLED';

/** A batch (الدفعة) with its totals summed from the lines. */
export interface PaymentBatchSummary {
    id: number;
    fiscalYearId: number;
    moheQuarterKey: number;
    studyCountryInId: number;
    batchNo: number;
    feeTypeId: number | null;
    currencyId: number;
    status: PaymentBatchStatus;
    preparedBy: number | null;
    preparedAt: string | null;
    approvedBy: number | null;
    approvedAt: string | null;
    disbursedAt: string | null;
    notes: string | null;
    createdAt: string | null;
    lineCount: number;
    entitledTotal: number;
    disbursedTotal: number;
}

export interface PaymentBatchLine {
    id: number;
    studentId: number;
    studentName: string | null;
    entitledAmount: number;
    disbursedAmount: number;
    heldReason: string | null;
}

export interface PaymentBatchDetail {
    batch: PaymentBatchSummary;
    lines: PaymentBatchLine[];
}

export interface PaymentBatchLineRequest {
    studentId: number;
    entitledAmount: number;
    /** Omit to pay in full. */
    disbursedAmount?: number | null;
    heldReason?: string | null;
}

export interface PaymentBatchCreateRequest {
    moheQuarterKey: number;
    /** May be omitted by an officer holding a single attaché. */
    studyCountryInId?: number | null;
    feeTypeId?: number | null;
    currencyId: number;
    notes?: string | null;
    lines: PaymentBatchLineRequest[];
}

/** A finance row of the quarter offered as a batch line. */
export interface FinanceCandidate {
    studentId: number;
    studentName: string | null;
    entitledAmount: number;
    wafarAmount: number;
    netAmount: number;
    currencyId: number | null;
    /** Already carried in full by a batch of this quarter and attaché. */
    alreadyPaid: boolean;
}

/** How many `toCurrencyId` units one `fromCurrencyId` unit buys on `rateDate`. */
export interface FxRate {
    id: number;
    fromCurrencyId: number;
    toCurrencyId: number;
    rateDate: string;
    rate: number;
    rateSource: string | null;
    notes: string | null;
    createdBy: number | null;
    createdAt: string | null;
}

export type AppropriationStatus = 'DRAFT' | 'APPROVED' | 'REVISED';

/**
 * The annual appropriation from MOF, next to what has been drawn against it.
 * `studyCountryInId` null is the ministry-wide figure, shared with every reader.
 */
export interface BudgetAppropriation {
    id: number;
    fiscalYearId: number;
    feeTypeId: number;
    currencyId: number;
    amount: number;
    studyCountryInId: number | null;
    status: AppropriationStatus;
    referenceNumber: string | null;
    notes: string | null;
    createdBy: number | null;
    createdAt: string | null;
    updatedBy: number | null;
    updatedAt: string | null;
    allocated: number;
    transferred: number;
    remaining: number;
}

@Injectable({ providedIn: 'root' })
export class AccountingApiService {

    private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/accounting`;

    constructor(private http: HttpClient) {}

    // ------------------------------------------------------------ chart

    segments(): Observable<CoaSegment[]> {
        return this.http.get<CoaSegment[]>(`${this.baseUrl}/chart/segments`);
    }

    /** Fetched rather than hard-coded: the Regulation defines these and may change them. */
    levels(): Observable<CoaLevel[]> {
        return this.http.get<CoaLevel[]>(`${this.baseUrl}/chart/levels`);
    }

    accounts(fiscalYearId: number, segmentId?: number): Observable<CoaAccount[]> {
        let params = new HttpParams().set('fiscalYearId', fiscalYearId);
        if (segmentId != null) {
            params = params.set('segmentId', segmentId);
        }
        return this.http.get<CoaAccount[]>(`${this.baseUrl}/chart/accounts`, { params });
    }

    chartSummary(fiscalYearId: number): Observable<ChartSummary> {
        return this.http.get<ChartSummary>(`${this.baseUrl}/chart/summary`,
            { params: new HttpParams().set('fiscalYearId', fiscalYearId) });
    }

    createAccount(account: Partial<CoaAccount>): Observable<CoaAccount> {
        return this.http.post<CoaAccount>(`${this.baseUrl}/chart/accounts`, account);
    }

    updateAccount(id: number, changes: Partial<CoaAccount>): Observable<CoaAccount> {
        return this.http.put<CoaAccount>(`${this.baseUrl}/chart/accounts/${id}`, changes);
    }

    /** There is no delete: an account that has been posted to must survive for audit. */
    deactivateAccount(id: number): Observable<CoaAccount> {
        return this.http.post<CoaAccount>(`${this.baseUrl}/chart/accounts/${id}/deactivate`, {});
    }

    // --------------------------------------------------------- calendar

    fiscalYears(): Observable<FiscalYear[]> {
        return this.http.get<FiscalYear[]>(`${this.baseUrl}/fiscal-years`);
    }

    /** Opens a fiscal year with its 13 periods (FISCAL_YEAR.ADD; W4-01). */
    openFiscalYear(yearNo: number): Observable<FiscalYear> {
        return this.http.post<FiscalYear>(`${this.baseUrl}/fiscal-years`, { yearNo });
    }

    periods(fiscalYearId: number): Observable<FiscalPeriod[]> {
        return this.http.get<FiscalPeriod[]>(`${this.baseUrl}/fiscal-years/${fiscalYearId}/periods`);
    }

    feeTypes(): Observable<FeeType[]> {
        return this.http.get<FeeType[]>(`${this.baseUrl}/fee-types`);
    }

    // --------------------------------------------------------- advances

    advances(fiscalYearId: number): Observable<AttacheAdvanceBalance[]> {
        return this.http.get<AttacheAdvanceBalance[]>(`${this.baseUrl}/advances`,
            { params: new HttpParams().set('fiscalYearId', fiscalYearId) });
    }

    reconciliation(fiscalYearId: number): Observable<ReconciliationResult> {
        return this.http.get<ReconciliationResult>(`${this.baseUrl}/advances/reconciliation`,
            { params: new HttpParams().set('fiscalYearId', fiscalYearId) });
    }

    // ------------------------------------------------------ allocations

    /** The quarter's allocations the caller may see — an attaché reads only its own. */
    allocations(quarterKey: number): Observable<AttacheAllocation[]> {
        return this.http.get<AttacheAllocation[]>(`${this.baseUrl}/allocations`,
            { params: new HttpParams().set('quarterKey', quarterKey) });
    }

    /** A draft. The maker is the signed-in user; the server derives the fiscal year from the quarter. */
    createAllocation(draft: Partial<AttacheAllocation>): Observable<AttacheAllocation> {
        return this.http.post<AttacheAllocation>(`${this.baseUrl}/allocations`, draft);
    }

    /** Refused for the user who drafted it: maker and checker are two people. */
    approveAllocation(id: number): Observable<AttacheAllocation> {
        return this.http.post<AttacheAllocation>(`${this.baseUrl}/allocations/${id}/approve`, {});
    }

    /** The point the advance grows. `transferDate` is an ISO date (yyyy-MM-dd). */
    transferAllocation(id: number, transferDate?: string | null, reference?: string | null): Observable<AttacheAllocation> {
        let params = new HttpParams();
        if (transferDate) { params = params.set('transferDate', transferDate); }
        if (reference) { params = params.set('reference', reference); }
        return this.http.post<AttacheAllocation>(`${this.baseUrl}/allocations/${id}/transfer`, {}, { params });
    }

    // --------------------------------------------------- payment batches

    paymentBatches(quarterKey: number, countryId?: number | null): Observable<PaymentBatchSummary[]> {
        let params = new HttpParams().set('quarterKey', quarterKey);
        if (countryId != null) { params = params.set('countryId', countryId); }
        return this.http.get<PaymentBatchSummary[]>(`${this.baseUrl}/payment-batches`, { params });
    }

    paymentBatch(id: number): Observable<PaymentBatchDetail> {
        return this.http.get<PaymentBatchDetail>(`${this.baseUrl}/payment-batches/${id}`);
    }

    /** The quarter's finance rows for one attaché, to pick lines from. */
    paymentBatchCandidates(quarterKey: number, countryId: number): Observable<FinanceCandidate[]> {
        return this.http.get<FinanceCandidate[]>(`${this.baseUrl}/payment-batches/candidates`,
            { params: new HttpParams().set('quarterKey', quarterKey).set('countryId', countryId) });
    }

    createPaymentBatch(request: PaymentBatchCreateRequest): Observable<PaymentBatchDetail> {
        return this.http.post<PaymentBatchDetail>(`${this.baseUrl}/payment-batches`, request);
    }

    approvePaymentBatch(id: number): Observable<PaymentBatchSummary> {
        return this.http.post<PaymentBatchSummary>(`${this.baseUrl}/payment-batches/${id}/approve`, {});
    }

    /** Books the disbursed total against the attaché's advance. */
    payPaymentBatch(id: number): Observable<PaymentBatchSummary> {
        return this.http.post<PaymentBatchSummary>(`${this.baseUrl}/payment-batches/${id}/pay`, {});
    }

    cancelPaymentBatch(id: number): Observable<PaymentBatchSummary> {
        return this.http.post<PaymentBatchSummary>(`${this.baseUrl}/payment-batches/${id}/cancel`, {});
    }

    // ---------------------------------------------------------- FX rates

    fxRates(fromCurrencyId?: number | null, toCurrencyId?: number | null, on?: string | null): Observable<FxRate[]> {
        let params = new HttpParams();
        if (fromCurrencyId != null) { params = params.set('fromCurrencyId', fromCurrencyId); }
        if (toCurrencyId != null) { params = params.set('toCurrencyId', toCurrencyId); }
        if (on) { params = params.set('on', on); }
        return this.http.get<FxRate[]>(`${this.baseUrl}/fx-rates`, { params });
    }

    /** The rate in force on a day. 404 when none — never a guessed 1.0. */
    effectiveFxRate(fromCurrencyId: number, toCurrencyId: number, on?: string | null): Observable<FxRate> {
        let params = new HttpParams().set('fromCurrencyId', fromCurrencyId).set('toCurrencyId', toCurrencyId);
        if (on) { params = params.set('on', on); }
        return this.http.get<FxRate>(`${this.baseUrl}/fx-rates/effective`, { params });
    }

    createFxRate(rate: Partial<FxRate>): Observable<FxRate> {
        return this.http.post<FxRate>(`${this.baseUrl}/fx-rates`, rate);
    }

    /** A wrong rate is deleted, not retired: nothing references it and the table has no active flag. */
    deleteFxRate(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/fx-rates/${id}`);
    }

    // ---------------------------------------------------- appropriations

    appropriations(fiscalYearId: number): Observable<BudgetAppropriation[]> {
        return this.http.get<BudgetAppropriation[]>(`${this.baseUrl}/appropriations`,
            { params: new HttpParams().set('fiscalYearId', fiscalYearId) });
    }

    createAppropriation(draft: Partial<BudgetAppropriation>): Observable<BudgetAppropriation> {
        return this.http.post<BudgetAppropriation>(`${this.baseUrl}/appropriations`, draft);
    }

    /** Edits a draft in place; an approved figure is revised and a new draft opened. */
    updateAppropriation(id: number, changes: Partial<BudgetAppropriation>): Observable<BudgetAppropriation> {
        return this.http.put<BudgetAppropriation>(`${this.baseUrl}/appropriations/${id}`, changes);
    }

    approveAppropriation(id: number): Observable<BudgetAppropriation> {
        return this.http.post<BudgetAppropriation>(`${this.baseUrl}/appropriations/${id}/approve`, {});
    }
}
