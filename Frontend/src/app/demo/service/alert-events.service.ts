import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

export interface AlertEventCaseDto {
    caseId: number;
    scenarioId: number;
    scenarioCode: string;
    scenarioNameEn: string;
    scenarioNameAr: string;
    severity: string;
    studentId: number;
    studentName: string;
    studentCountryId: number;
    referenceDate: string;
    status: string;
    notes: string;
    acknowledgedBy: string;
    acknowledgedAt: string;
    resolvedBy: string;
    resolvedAt: string;
    createdAt: string;
    updatedAt: string;
}

/** One legend row: a code-list row (every active one, zero counts included) with its open-case count. */
export interface AlertEventCodeCount {
    code: string;
    nameAr: string | null;
    nameEn: string | null;
    uiSeverity: string | null;
    icon: string | null;
    sortOrder: number | null;
    count: number;
}

export interface AlertEventCaseSummaryDto {
    totalOpen: number;
    totalAcknowledged: number;
    totalResolved: number;
    totalDismissed: number;
    totalAll: number;
    openCritical: number;
    openHigh: number;
    openMedium: number;
    openLow: number;
    /** Open cases per severity, in LU_ALERT_SEVERITY order — the legend is the table. */
    bySeverity: AlertEventCodeCount[];
    /** Cases per status, in LU_ALERT_STATUS order. */
    byStatus: AlertEventCodeCount[];
}

export interface AlertEventScenarioDto {
    scenarioId: number;
    scenarioCode: string;
    scenarioNameEn: string;
    scenarioNameAr: string;
    descriptionEn: string;
    descriptionAr: string;
    severity: string;
    thresholdDays: number;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface AlertEventScenarioRequest {
    scenarioCode: string;
    scenarioNameEn: string;
    scenarioNameAr: string;
    descriptionEn?: string;
    descriptionAr?: string;
    severity: string;
    thresholdDays?: number | null;
    isActive: boolean;
}

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface AlertCaseFilters {
    status?: string;
    scenarioCode?: string;
    severity?: string;
    studentId?: number;
    studentName?: string;
    countryId?: number;
    dateFrom?: string;
    dateTo?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
}

@Injectable({ providedIn: 'root' })
export class AlertEventsService {

    private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/alert-events`;
    private readonly scenariosUrl = `${environment.apiUrl}/mohe/api/v1/alert-scenarios`;

    constructor(private http: HttpClient) {}

    getCases(filters: AlertCaseFilters): Observable<PageResponse<AlertEventCaseDto>> {
        let params = new HttpParams();
        if (filters.status) params = params.set('status', filters.status);
        if (filters.scenarioCode) params = params.set('scenarioCode', filters.scenarioCode);
        if (filters.severity) params = params.set('severity', filters.severity);
        if (filters.studentId != null) params = params.set('studentId', filters.studentId.toString());
        if (filters.studentName) params = params.set('studentName', filters.studentName);
        if (filters.countryId != null) params = params.set('countryId', filters.countryId.toString());
        if (filters.dateFrom) params = params.set('dateFrom', filters.dateFrom);
        if (filters.dateTo) params = params.set('dateTo', filters.dateTo);
        params = params.set('page', (filters.page ?? 0).toString());
        params = params.set('size', (filters.size ?? 20).toString());
        params = params.set('sortBy', filters.sortBy ?? 'createdAt');
        params = params.set('sortDir', filters.sortDir ?? 'desc');
        return this.http.get<PageResponse<AlertEventCaseDto>>(this.baseUrl, { params });
    }

    getSummary(): Observable<AlertEventCaseSummaryDto> {
        return this.http.get<AlertEventCaseSummaryDto>(`${this.baseUrl}/summary`);
    }

    acknowledge(caseId: number, notes?: string): Observable<AlertEventCaseDto> {
        return this.http.patch<AlertEventCaseDto>(`${this.baseUrl}/${caseId}/acknowledge`, { notes });
    }

    resolve(caseId: number, notes?: string): Observable<AlertEventCaseDto> {
        return this.http.patch<AlertEventCaseDto>(`${this.baseUrl}/${caseId}/resolve`, { notes });
    }

    dismiss(caseId: number, notes?: string): Observable<AlertEventCaseDto> {
        return this.http.patch<AlertEventCaseDto>(`${this.baseUrl}/${caseId}/dismiss`, { notes });
    }

    runDetection(): Observable<{ newCasesCreated: number }> {
        return this.http.post<{ newCasesCreated: number }>(`${this.baseUrl}/run-detection`, {});
    }

    getScenarios(): Observable<AlertEventScenarioDto[]> {
        return this.http.get<AlertEventScenarioDto[]>(this.scenariosUrl);
    }

    updateScenario(scenarioId: number, isActive?: boolean, thresholdDays?: number): Observable<AlertEventScenarioDto> {
        const body: any = {};
        if (isActive !== undefined) body.isActive = isActive;
        if (thresholdDays !== undefined) body.thresholdDays = thresholdDays;
        return this.http.patch<AlertEventScenarioDto>(`${this.scenariosUrl}/${scenarioId}`, body);
    }

    createScenario(req: AlertEventScenarioRequest): Observable<AlertEventScenarioDto> {
        return this.http.post<AlertEventScenarioDto>(this.scenariosUrl, req);
    }

    saveScenario(scenarioId: number, req: AlertEventScenarioRequest): Observable<AlertEventScenarioDto> {
        return this.http.put<AlertEventScenarioDto>(`${this.scenariosUrl}/${scenarioId}`, req);
    }

    deleteScenario(scenarioId: number): Observable<void> {
        return this.http.delete<void>(`${this.scenariosUrl}/${scenarioId}`);
    }
}
