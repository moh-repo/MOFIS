import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { ReportCountryScopeService } from './report-country-scope.service';
import { silentErrors } from '../interceptors/http-context.tokens';
import { WafarBalanceSummaryDto } from './wafar.service';
import { AlertEventCaseSummaryDto } from './alert-events.service';
import { Alert } from '../api/alert';

/**
 * The ministry dashboard, one endpoint per section (`/api/v1/dashboard/*`).
 *
 * Country scope: these calls carry NO `countryId` query parameter, on purpose.
 * The topbar's selection reaches the server as the `X-Country-Id` header that
 * CountryScopeInterceptor adds to every GET under /mohe/api/ — and that same
 * interceptor SKIPS the header when a `countryId` param is present. Appending
 * the param here would drop the header, and the parts of the dashboard that
 * only read the header (Wafar, quarter, approvals, alert cases) would come
 * back ministry-wide while the student figures narrowed. One contract, the
 * header, keeps every section on the same scope.
 *
 * Section calls are marked SUPPRESS_ERROR_TOAST: `/sections` is the gate, so a
 * 403 on a section can only be a stale-permission race (a code revoked after
 * the page loaded). The component renders that section's empty state; a toast
 * would only shout about a panel the user is no longer meant to see.
 * `/sections` itself is not suppressed — failing to learn what to show is a
 * real error.
 */

export const DASHBOARD_SECTION_CODES = [
    'DASHBOARD_STUDENTS',
    'DASHBOARD_FINANCE',
    'DASHBOARD_APPROVALS',
    'DASHBOARD_ALERTS',
    'DASHBOARD_COUNTRIES',
    'DASHBOARD_USERS',
    'DASHBOARD_QUARTER',
    'DASHBOARD_NOTICES'
] as const;

export type DashboardSectionCode = typeof DASHBOARD_SECTION_CODES[number];

/** One labelled figure: a year, a country. A null label is "unspecified" (COMMON.UNSPECIFIED). */
export interface DashboardPoint {
    label: string | null;
    value: number;
}

/** One month of a series; the browser labels it in the reader's locale. */
export interface MonthPoint {
    year: number;
    /** 1–12. */
    month: number;
    count: number;
}

/**
 * A count per code, with the code resolved by the server: both names, the
 * tag severity, icon and colour of its row. `code` null = unspecified.
 */
export interface CodeCount {
    code: string | null;
    nameAr: string | null;
    nameEn: string | null;
    uiSeverity: string | null;
    icon: string | null;
    uiColor: string | null;
    sortOrder: number | null;
    count: number;
}

export interface DashboardSectionsDto {
    /** Section codes the caller's authorities open. */
    sections: string[];
    /** True when the caller holds DASHBOARD.VIEW or is an admin — every section. */
    all: boolean;
}

export interface DashboardStudentsDto {
    total: number;
    active: number;
    newStatus: number;
    suspended: number;
    notActive: number;
    newThisMonth: number;
    /** The dashboard's month window (SYS_PARAMETER DASHBOARD.MONTHS_WINDOW), oldest first. */
    monthly: MonthPoint[];
    byDelegationYear: DashboardPoint[];
    /** Students per study status, with the row's colour for the donut. */
    byStatus: CodeCount[];
}

export interface DashboardFinanceDto {
    totalDisbursed: number;
    averageDisbursement: number;
    byYear: DashboardPoint[];
    wafar: WafarBalanceSummaryDto;
}

export interface DashboardApprovalsDto {
    pendingTotal: number;
    /** Names from ApprovalConfigs; code null = a type with no configuration. */
    byEntityType: CodeCount[];
    /** Pending requests raised by the caller. */
    myPending: number;
}

export type DashboardAlertCasesDto = AlertEventCaseSummaryDto;

export interface DashboardUsersDto {
    total: number;
    newThisMonth: number;
    monthly: MonthPoint[];
    /** False = ministry-wide figure; true = accounts assigned to the caller's countries. */
    scoped: boolean;
}

export interface DashboardQuarterDto {
    /** False when there is no open quarter (200, not 204 — the body is always a DTO). */
    present: boolean;
    quarterYear: number | null;
    quarterNumber: number | null;
    quarterName: string | null;
    quarterNameEn: string | null;
    quarterClosed: boolean;
    /** Null = a global quarter shared by every country. */
    countryId: number | null;
    quarterAmount: number | null;
    budgetAmount: number | null;
    paidAmount: number | null;
    quarterNetAmount?: number | null;
    quarterStudentCount: number;
    quarterPayDate: string | null;
}

export interface DashboardNoticesDto {
    /** Codes arrive resolved (name, severity, icon); nothing is defaulted client-side. */
    today: Alert[];
    yesterday: Alert[];
    /** Always false: system notices carry no country column (owner decision D4). */
    countrySpecific: boolean;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {

    private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/dashboard`;

    /** The topbar country selection; emits its current value on subscribe. */
    readonly selectedCountryId$: Observable<number | null>;

    constructor(
        private http: HttpClient,
        reportCountryScope: ReportCountryScopeService
    ) {
        this.selectedCountryId$ = reportCountryScope.selectedCountryId$;
    }

    /** Which sections the caller may see. Not suppressed: this is the gate. */
    getSections(): Observable<DashboardSectionsDto> {
        return this.http.get<DashboardSectionsDto>(`${this.baseUrl}/sections`);
    }

    getStudents(): Observable<DashboardStudentsDto> {
        return this.section<DashboardStudentsDto>('students');
    }

    getFinance(): Observable<DashboardFinanceDto> {
        return this.section<DashboardFinanceDto>('finance');
    }

    getApprovals(): Observable<DashboardApprovalsDto> {
        return this.section<DashboardApprovalsDto>('approvals');
    }

    getAlertCases(): Observable<DashboardAlertCasesDto> {
        return this.section<DashboardAlertCasesDto>('alert-cases');
    }

    /** Top study countries; a null label is a student with no study country. */
    getCountries(): Observable<DashboardPoint[]> {
        return this.section<DashboardPoint[]>('countries');
    }

    getUsers(): Observable<DashboardUsersDto> {
        return this.section<DashboardUsersDto>('users');
    }

    getQuarter(): Observable<DashboardQuarterDto> {
        return this.section<DashboardQuarterDto>('quarter');
    }

    getNotices(): Observable<DashboardNoticesDto> {
        return this.section<DashboardNoticesDto>('notices');
    }

    /** A section GET: no params (see the header note above), toast suppressed. */
    private section<T>(path: string): Observable<T> {
        return this.http.get<T>(`${this.baseUrl}/${path}`, { context: silentErrors() });
    }
}
