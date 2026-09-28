import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { TranslationService } from './translation.service';
import { CodeListService } from './code-list.service';
import { silentErrors } from '../interceptors/http-context.tokens';

/** N-03: a staff account named as approver of a level, optionally for one country. */
export interface ApprovalLevelApprover {
    userId: number;
    countryId?: number | null;
    userName?: string;
    fullName?: string;
}

export interface ApprovalLevel {
    id: number;
    levelOrder: number;
    levelName: string;
    levelNameAr?: string;
    requiredRoleId?: number;
    /** N-03: a MOHECORE.LU_POSITION code the approver must hold, resolved per country. */
    requiredPositionCode?: string | null;
    /** N-03: named approvers; empty means anyone meeting role and position. */
    namedApprovers?: ApprovalLevelApprover[];
    canSkip: boolean;
    isActive: boolean;
}

export interface ApprovalConfig {
    id: number;
    entityType: string;
    entityTypeAr?: string;
    description?: string;
    descriptionAr?: string;
    totalLevels: number;
    isActive: boolean;
    requireAllLevels: boolean;
    levels?: ApprovalLevel[];
}

export interface ApprovalStep {
    id: number;
    levelOrder: number;
    level: ApprovalLevel;
    status: string;
    approvedBy?: number;
    approvedAt?: string;
    comments?: string;
    approver?: any;
}

export interface ApprovalRequest {
    id: number;
    config: ApprovalConfig;
    requestType: 'INSERT' | 'UPDATE' | 'DELETE';
    entityType: string;
    entityId?: string;
    parentApprovalRequestId?: number;
    requestData: string;
    originalData?: string;
    currentLevel: number;
    status: 'PENDING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'EXECUTED';
    requestedBy: number;
    requester?: any;
    requesterFullName?: string;
    /** Set on the pending lists: what the server will let the caller do on the waiting level. */
    canApprove?: boolean;
    canReject?: boolean;
    requestedAt: string;
    rejectionReason?: string;
    rejectedBy?: number;
    rejectedAt?: string;
    executedAt?: string;
    executedBy?: number;
    steps?: ApprovalStep[];
}

export interface ApprovalHistory {
    id: number;
    action: string;
    actionBy: number;
    actor?: any;
    actionAt: string;
    levelOrder?: number;
    oldStatus?: string;
    newStatus?: string;
    details?: string;
}

export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

/** One field's before/after within a pending request. */
export interface ApprovalFieldChange {
    field: string;
    oldValue: string;
    newValue: string;
}

/** A file staged with a request, viewable before the decision is made. */
/**
 * What a write answers on a screen behind the approval workflow.
 *
 * Nothing was saved: a request was opened and an approver has to sign it off.
 * The screens show `message` in place of "created successfully".
 */
export interface ApprovalPending {
    message: string;
    status: string;
}

export interface ApprovalPendingFile {
    fileName: string;
    originalName: string;
    documentType?: string | null;
    image: boolean;
}

/** A stored document a request would remove. */
export interface ApprovalRemovedDocument {
    documentId: number;
    documentName?: string | null;
    documentType?: string | null;
    originalFilename?: string | null;
}

/** Flat request summary used by listing screens. */
export interface ApprovalSummary {
    id: number;
    entityType: string;
    entityId?: string;
    requestType: 'INSERT' | 'UPDATE' | 'DELETE';
    status: 'PENDING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'EXECUTED';
    currentLevel?: number;
    requestedAt: string;
    requestedBy?: number;
    requesterFullName?: string | null;
    rejectionReason?: string | null;
    rejectedBy?: number | null;
    rejectedAt?: string | null;
    executedAt?: string | null;
    executedBy?: number | null;
    /** What the server will let the caller do on the waiting level (open requests only). */
    canApprove?: boolean | null;
    canReject?: boolean | null;
}

/** A student's requests, grouped by whether a decision has been made. */
export interface StudentApprovalRequests {
    pending: ApprovalSummary[];
    completed: ApprovalSummary[];
}

/** What a request would change, for review before approving. */
export interface ApprovalChangeDetails {
    requestId: number;
    entityType: string;
    entityId: string;
    requestType: string;
    status: string;
    fieldChanges: ApprovalFieldChange[];
    photoChanged: boolean;
    photo?: ApprovalPendingFile | null;
    documents: ApprovalPendingFile[];
    removedDocuments: ApprovalRemovedDocument[];
    parseError?: string | null;
}

@Injectable({
    providedIn: 'root'
})
export class ApprovalService {
    private apiUrl = `${environment.apiUrl}/mohe/api/v1/approvals`;
    private configApiUrl = `${environment.apiUrl}/mohe/api/v1/approval-configs`;

    constructor(
        private http: HttpClient,
        private translationService: TranslationService,
        private codeLists: CodeListService
    ) { }

    // ========== Approval Requests ==========

    getAllRequests(page: number = 0, size: number = 10, sortField: string = 'requestedAt', sortOrder: string = 'desc'): Observable<Page<ApprovalRequest>> {
        const params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString())
            .set('sortField', sortField)
            .set('sortOrder', sortOrder);
        return this.http.get<Page<ApprovalRequest>>(this.apiUrl, { params });
    }

    /**
     * Pending requests. `scope` is `mine` (the default since N-03.d, 6 Sep 2026:
     * only the requests the caller is eligible to sign at their current level)
     * or `all` (every open request in the caller's country scope, for oversight).
     */
    getPendingRequests(
        page: number = 0, size: number = 10,
        filters: { requestType?: string; entityType?: string } = {},
        scope: 'mine' | 'all' = 'mine'
    ): Observable<Page<ApprovalRequest>> {
        let params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString())
            .set('scope', scope);
        if (filters.requestType) params = params.set('requestType', filters.requestType);
        if (filters.entityType)  params = params.set('entityType',  filters.entityType);
        return this.http.get<Page<ApprovalRequest>>(`${this.apiUrl}/pending`, { params });
    }

    /** Rejected, executed, or fully approved (awaiting execute) — country-scoped like list APIs. */
    getCompletedHistory(
        page: number = 0, size: number = 10,
        sortField: string = 'requestedAt', sortOrder: string = 'desc',
        filters: { requestType?: string; entityType?: string; status?: string } = {}
    ): Observable<Page<ApprovalRequest>> {
        let params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString())
            .set('sortField', sortField)
            .set('sortOrder', sortOrder);
        if (filters.requestType) params = params.set('requestType', filters.requestType);
        if (filters.entityType)  params = params.set('entityType',  filters.entityType);
        if (filters.status)      params = params.set('status',      filters.status);
        return this.http.get<Page<ApprovalRequest>>(`${this.apiUrl}/completed`, { params });
    }

    getPendingForRoles(roleIds: number[]): Observable<ApprovalRequest[]> {
        const params = new HttpParams().set('roleIds', roleIds.join(','));
        return this.http.get<ApprovalRequest[]>(`${this.apiUrl}/pending/roles`, { params });
    }

    /**
     * The signed-in user's own requests.
     *
     * The server takes the user from the token and refuses a `userId` that
     * disagrees with it, so none is sent. It used to be, and the server used to
     * trust it — which meant anyone could read anyone else's requests, and a
     * request carries the full proposed state of a student record.
     *
     * `userId` is still accepted as a parameter so existing callers compile, and
     * deliberately ignored.
     */
    getMyRequests(
        userId?: number, page: number = 0, size: number = 10,
        filters: { requestType?: string; entityType?: string; status?: string } = {}
    ): Observable<Page<ApprovalRequest>> {
        let params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString());
        if (filters.requestType) params = params.set('requestType', filters.requestType);
        if (filters.entityType)  params = params.set('entityType',  filters.entityType);
        if (filters.status)      params = params.set('status',      filters.status);
        return this.http.get<Page<ApprovalRequest>>(`${this.apiUrl}/my-requests`, { params });
    }

    getRequestById(id: number): Observable<ApprovalRequest> {
        return this.http.get<ApprovalRequest>(`${this.apiUrl}/${id}`);
    }

    getRequestHistory(id: number): Observable<ApprovalHistory[]> {
        return this.http.get<ApprovalHistory[]>(`${this.apiUrl}/${id}/history`);
    }

    createRequest(request: {
        entityType: string;
        requestType: string;
        entityId?: string;
        requestData: string;
        originalData?: string;
        requestedBy: number;
    }): Observable<ApprovalRequest> {
        return this.http.post<ApprovalRequest>(this.apiUrl, request);
    }

    /*
     * Silent to the global interceptor: both callers show their own message, and the
     * interceptor's toast on top made every refusal appear twice (live QA #18).
     */
    approveRequest(id: number, approverId: number, comments?: string): Observable<ApprovalRequest> {
        return this.http.post<ApprovalRequest>(`${this.apiUrl}/${id}/approve`, { approverId, comments },
            { context: silentErrors() });
    }

    rejectRequest(id: number, rejectedBy: number, reason: string): Observable<ApprovalRequest> {
        return this.http.post<ApprovalRequest>(`${this.apiUrl}/${id}/reject`, { rejectedBy, reason },
            { context: silentErrors() });
    }

    /** The requester takes back their own request (R5-16); needs no approvals permission. */
    withdrawRequest(id: number, reason?: string): Observable<ApprovalRequest> {
        return this.http.post<ApprovalRequest>(`${this.apiUrl}/${id}/withdraw`, { reason: reason?.trim() || null },
            { context: silentErrors() });
    }

    /** The server's own sentence for a failed decision, or null to use the page's fallback. */
    static errorMessage(err: any): string | null {
        const body = err?.error;
        if (typeof body?.message === 'string' && body.message.trim()) return body.message;
        if (typeof body?.error === 'string' && body.error.trim()) return body.error;
        return null;
    }

    /**
     * Polled by the topbar every minute. A failure used to turn into `{ count: 0 }`
     * here, which reads as "nothing pending"; it now reaches the caller, which shows
     * no badge, and stays silent so an outage is not a toast a minute (QA B9, N7).
     */
    countPending(): Observable<{ count: number }> {
        return this.http.get<{ count: number }>(`${this.apiUrl}/count/pending`, { context: silentErrors() }).pipe(
            map((res: any) => ({ count: typeof res?.count === 'number' ? res.count : parseInt(String(res?.count || 0), 10) || 0 }))
        );
    }

    /** Field diff plus staged files for one request. */
    getRequestChanges(id: number): Observable<ApprovalChangeDetails> {
        return this.http.get<ApprovalChangeDetails>(`${this.apiUrl}/${id}/changes`);
    }

    /**
     * Staged file bytes. Fetched through HttpClient rather than linked directly so the
     * JWT header is attached; callers turn the blob into an object URL.
     */
    getStagedFile(id: number, fileName: string): Observable<Blob> {
        return this.http.get(`${this.apiUrl}/${id}/staged/${encodeURIComponent(fileName)}`, {
            responseType: 'blob'
        });
    }

    /**
     * Every request for one student, split into awaiting-decision and decided. Document
     * removals are keyed on the document, so a student-id query alone would miss them.
     */
    getStudentRequests(studentId: number): Observable<StudentApprovalRequests> {
        return this.http.get<StudentApprovalRequests>(`${this.apiUrl}/student/${studentId}/requests`);
    }

    /** Undecided requests for one record, for the "pending approval" notice. */
    getPendingForEntity(entityType: string, entityId: string | number): Observable<ApprovalSummary[]> {
        return this.http.get<ApprovalSummary[]>(
            `${this.apiUrl}/entity/${encodeURIComponent(entityType)}/${encodeURIComponent(String(entityId))}/pending`);
    }

    getPendingEntityIds(entityType: string): Observable<string[]> {
        return this.http.get<string[]>(`${this.apiUrl}/pending-entities`, {
            params: new HttpParams().set('entityType', entityType)
        });
    }

    // ========== Approval Configs (Admin) ==========

    getAllConfigs(): Observable<ApprovalConfig[]> {
        return this.http.get<ApprovalConfig[]>(this.configApiUrl);
    }

    getActiveConfigs(): Observable<ApprovalConfig[]> {
        return this.http.get<ApprovalConfig[]>(`${this.configApiUrl}/active`);
    }

    getConfigById(id: number): Observable<ApprovalConfig> {
        return this.http.get<ApprovalConfig>(`${this.configApiUrl}/${id}`);
    }

    getConfigByEntityType(entityType: string): Observable<ApprovalConfig> {
        return this.http.get<ApprovalConfig>(`${this.configApiUrl}/entity/${entityType}`);
    }

    createConfig(config: Partial<ApprovalConfig>): Observable<ApprovalConfig> {
        return this.http.post<ApprovalConfig>(this.configApiUrl, config);
    }

    updateConfig(id: number, config: Partial<ApprovalConfig>): Observable<ApprovalConfig> {
        return this.http.put<ApprovalConfig>(`${this.configApiUrl}/${id}`, config);
    }

    deactivateConfig(id: number): Observable<any> {
        return this.http.delete(`${this.configApiUrl}/${id}`);
    }

    // ========== Approval Levels ==========

    getLevels(configId: number): Observable<ApprovalLevel[]> {
        return this.http.get<ApprovalLevel[]>(`${this.configApiUrl}/${configId}/levels`);
    }

    addLevel(configId: number, level: Partial<ApprovalLevel>): Observable<ApprovalLevel> {
        return this.http.post<ApprovalLevel>(`${this.configApiUrl}/${configId}/levels`, level);
    }

    updateLevel(levelId: number, level: Partial<ApprovalLevel>): Observable<ApprovalLevel> {
        return this.http.put<ApprovalLevel>(`${this.configApiUrl}/levels/${levelId}`, level);
    }

    reorderLevels(configId: number, levelIds: number[]): Observable<any> {
        return this.http.post(`${this.configApiUrl}/${configId}/levels/reorder`, levelIds);
    }

    deleteLevel(levelId: number): Observable<any> {
        return this.http.delete(`${this.configApiUrl}/levels/${levelId}`);
    }

    // ========== Helper Methods ==========
    //
    // Names come from the code lists (approval-status, request-type,
    // approval-action, approval-entity-type); tag colours are the codeSeverity
    // pipe over the same lists. No label or colour is spelled out here.

    getStatusLabel(status: string, isArabic?: boolean): string {
        return this.codeLists.label('approval-status', status, this.lang(isArabic));
    }

    getRequestTypeLabel(type: string, isArabic?: boolean): string {
        return this.codeLists.label('request-type', type, this.lang(isArabic));
    }

    /** Label for a history action. */
    getActionLabel(action: string): string {
        if (!action) return '';
        return this.codeLists.label('approval-action', action);
    }

    /** Human name for an entity type (ApprovalConfigs, served as the approval-entity-type list). */
    getEntityTypeLabel(entityType: string): string {
        if (!entityType) return '';
        return this.codeLists.label('approval-entity-type', entityType);
    }

    private lang(isArabic?: boolean): string {
        if (isArabic === undefined) {
            return this.translationService.getCurrentLanguage();
        }
        return isArabic ? 'ar' : 'en';
    }

    /**
     * Localizes a history entry's detail text.
     *
     * The stored text is machine-generated English built from a fixed set of templates,
     * often with a trailing free-text comment the actor typed. The machine portion is
     * rebuilt from translation keys so it follows the UI language, and any user comment is
     * appended verbatim — a comment typed in Arabic stays Arabic, one typed in English
     * stays English. Anything that matches no template is returned unchanged.
     */
    localizeHistoryDetails(details?: string | null): string {
        const text = (details ?? '').trim();
        if (!text) return '';

        const ar = this.translationService.isRTL();
        let m: RegExpMatchArray | null;

        // "Request created for {TYPE} on {ENTITY}"  (optionally " (chained)")
        m = text.match(/^Request created for (\w+) on ([\w]+)(\s*\(chained\))?$/i);
        if (m) {
            const type = this.getRequestTypeLabel(m[1].toUpperCase());
            const entity = this.getEntityTypeLabel(m[2].toUpperCase());
            const base = ar
                ? `تم إنشاء طلب ${type} على ${entity}`
                : `Request created for ${type} on ${entity}`;
            return m[3] ? `${base} ${ar ? '(مرتبط)' : '(chained)'}` : base;
        }

        // "Level {N} approved. Moving to level {M}"
        m = text.match(/^Level (\d+) approved\. Moving to level (\d+)$/i);
        if (m) {
            return ar
                ? `تمت الموافقة على المستوى ${m[1]}. الانتقال إلى المستوى ${m[2]}.`
                : `Level ${m[1]} approved. Moving to level ${m[2]}.`;
        }

        // "Final approval by level {N}. {comment}"
        m = text.match(/^Final approval by level (\d+)\.\s*(.*)$/i);
        if (m) {
            const base = ar
                ? `الموافقة النهائية من المستوى ${m[1]}.`
                : `Final approval by level ${m[1]}.`;
            return m[2].trim() ? `${base} ${m[2].trim()}` : base;
        }

        // "Request executed. Changes applied to {ENTITY}"
        m = text.match(/^Request executed\. Changes applied to ([\w]+)$/i);
        if (m) {
            const entity = this.getEntityTypeLabel(m[1].toUpperCase());
            return ar
                ? `تم تنفيذ الطلب. طُبّقت التغييرات على ${entity}.`
                : `Request executed. Changes applied to ${entity}.`;
        }

        // "Rejected at level {N}. Reason: {reason}"  — reason is user text, kept verbatim.
        m = text.match(/^Rejected at level (\d+)\. Reason:\s*(.*)$/i);
        if (m) {
            const base = ar ? `تم الرفض في المستوى ${m[1]}.` : `Rejected at level ${m[1]}.`;
            const reasonLabel = ar ? 'السبب:' : 'Reason:';
            return m[2].trim() ? `${base} ${reasonLabel} ${m[2].trim()}` : base;
        }

        // No known template — likely a pure user comment; leave it as typed.
        return text;
    }
}
