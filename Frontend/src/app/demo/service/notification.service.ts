import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { silentErrors } from '../interceptors/http-context.tokens';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';

export interface NotificationSettingsDto {
    countryId?: number | null;
    inheritedFromGlobal?: boolean;
    emailEnabled: boolean;
    smtpHost: string | null;
    smtpPort: number | null;
    smtpUsername: string | null;
    smtpPassword: string | null;
    smtpFromAddress: string | null;
    smtpFromName: string | null;
    smtpUseTls: boolean;
    smtpUseSsl: boolean;
    smsEnabled: boolean;
    smsWebhookUrl: string | null;
    smsWebhookMethod: string | null;
    smsWebhookHeaders: string | null;
    smsFromNumber: string | null;
    whatsappEnabled: boolean;
    whatsappWebhookUrl: string | null;
    /** Write-only like smsWebhookHeaders: null on read, blank on save keeps the stored value. */
    whatsappWebhookHeaders: string | null;
    whatsappFromNumber: string | null;
    /** Null = the Meta Cloud API text-message shape. */
    whatsappBodyTemplate: string | null;
    /** TEXT (free text), TEMPLATE (approved template) or AUTO (free text, then the template). */
    whatsappMessageMode: WhatsAppMessageMode;
    /** The approved-template message's JSON body; required for TEMPLATE and AUTO. */
    whatsappApprovedTemplate: string | null;

    // Read-only, filled on reads.
    countryName?: string | null;
    smtpPasswordSet?: boolean;
    smsWebhookHeadersSet?: boolean;
    whatsappWebhookHeadersSet?: boolean;
    /** Whether the signed-in user may change (and, for a country row, delete) this row. */
    canEdit?: boolean;
    updatedAt?: string | null;
}

/** How a WhatsApp message goes out: free text only works inside Meta's 24-hour window. */
export type WhatsAppMessageMode = 'TEXT' | 'TEMPLATE' | 'AUTO';

/** The channels a message can be delivered on (LU_NOTIFICATION_CHANNEL minus the legacy BOTH). */
export type DeliveryChannel = 'EMAIL' | 'SMS' | 'WHATSAPP';
export const DELIVERY_CHANNELS: DeliveryChannel[] = ['EMAIL', 'SMS', 'WHATSAPP'];

/** What a send to the chosen countries could go out on, and whose settings row carries it. */
export interface ChannelAvailability {
    /** null = the global (all countries) settings apply. */
    settingsCountryId: number | null;
    enabledChannels: DeliveryChannel[];
}

export interface StudentFilterDto {
    countryIds?: number[];
    majorIds?: number[];
    studyStatusIds?: number[];
    universityTypeIds?: number[];
    governorateIds?: number[];
}

export interface EmployeeFilterDto {
    groupIds?: number[];
    roleIds?: number[];
}

export interface SendMessageRequest {
    /** Each channel becomes its own history row under one batch id. */
    channels: DeliveryChannel[];
    recipientType: 'STUDENT' | 'EMPLOYEE';
    scope: 'ONE' | 'GROUP' | 'ALL';
    recipientId?: number;
    /** scope=ONE without an id: the email of a student or employee on file (review R4). */
    recipientEmail?: string;
    studentFilter?: StudentFilterDto;
    employeeFilter?: EmployeeFilterDto;
    subject?: string;
    body: string;
}

/** One match in the "send to one person" picker (review R4). */
export interface RecipientOption {
    id: number;
    name: string;
    email: string | null;
    hasEmail: boolean;
    hasPhone: boolean;
}

/** What a settings test send did (review R7). */
export interface NotificationTestResult {
    ok: boolean;
    /** The channel is switched on in these settings. */
    enabled: boolean;
    error: string | null;
}

export interface SendMessageResponse {
    batchId: string;
    status: string;
    message: string;
}

export interface NotificationLogDto {
    id: number;
    batchId: string;
    countryIds?: number[];
    countryNames?: string[];
    channel: string;
    recipientType: string;
    scope: string;
    subject: string | null;
    body: string;
    totalCount: number;
    sentCount: number;
    failedCount: number;
    status: string;
    errorSummary: string | null;
    createdBy: number | null;
    createdAt: string;
    sentAt: string | null;
}

/**
 * The history filter bar, as the server names its query parameters.
 *
 * <p>Codes rather than labels: the four dropdowns hold the codes of
 * LU_NOTIFICATION_CHANNEL, LU_NOTIFICATION_RECIPIENT_TYPE, LU_NOTIFICATION_SCOPE
 * and LU_NOTIFICATION_SEND_STATUS. The two dates are ISO instants, and `sentTo`
 * is the last instant of the chosen day rather than its midnight — a range
 * naming today has to include what was sent this afternoon.
 */
export interface NotificationHistoryFilters {
    channel?: string | null;
    recipientType?: string | null;
    scope?: string | null;
    status?: string | null;
    sentFrom?: string | null;
    sentTo?: string | null;
}

/**
 * One person a history row's message went to, on that row's channel (V140).
 * Batches sent before the recipient rows existed have none; the view says so.
 */
export interface NotificationRecipientDto {
    id: number;
    recipientType: string;
    recipientId: number | null;
    name: string | null;
    /** The e-mail or phone the channel used; null when the person had none on file. */
    address: string | null;
    /** SENT or FAILED (codes of LU_NOTIFICATION_SEND_STATUS). */
    status: string;
    error: string | null;
    sentAt: string;
}

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {

    private readonly settingsBase = '/api/v1/notification-settings';
    private readonly notifBase = '/api/v1/notifications';

    constructor(private http: HttpClient, private apiUrl: ApiUrlService) {}

    getSettings(countryId?: number | null): Observable<NotificationSettingsDto> {
        let params = new HttpParams();
        if (countryId != null) {
            params = params.set('countryId', countryId);
        }
        return this.http.get<NotificationSettingsDto>(this.apiUrl.apiPath(this.settingsBase), { params });
    }

    /** The global row first, then every attaché row the caller may see. */
    listSettings(): Observable<NotificationSettingsDto[]> {
        return this.http.get<NotificationSettingsDto[]>(this.apiUrl.apiPath(`${this.settingsBase}/list`));
    }

    /** Adds an attaché's own settings row (409 when it already has one). */
    createSettings(dto: NotificationSettingsDto): Observable<NotificationSettingsDto> {
        return this.http.post<NotificationSettingsDto>(this.apiUrl.apiPath(this.settingsBase), dto);
    }

    updateSettings(dto: NotificationSettingsDto): Observable<NotificationSettingsDto> {
        return this.http.put<NotificationSettingsDto>(this.apiUrl.apiPath(this.settingsBase), dto);
    }

    /** Removes an attaché's own row; its sends fall back to the global settings. */
    deleteSettings(countryId: number): Observable<void> {
        return this.http.delete<void>(this.apiUrl.apiPath(`${this.settingsBase}/${countryId}`));
    }

    /** The channels switched on in the settings a send to these countries would use. */
    channelAvailability(countryIds?: number[] | null): Observable<ChannelAvailability> {
        let params = new HttpParams();
        for (const id of countryIds ?? []) {
            params = params.append('countryIds', id);
        }
        return this.http.get<ChannelAvailability>(this.apiUrl.apiPath(`${this.notifBase}/channels`), { params });
    }

    /** Sends one test message through the saved settings of countryId (null = ministry-wide). */
    testSettings(countryId: number | null, channel: DeliveryChannel, to: string): Observable<NotificationTestResult> {
        return this.http.post<NotificationTestResult>(this.apiUrl.apiPath(`${this.settingsBase}/test`),
            { countryId, channel, to });
    }

    /** Staff groups a message may be addressed to: ids and names (live QA M6). */
    getRecipientGroups(): Observable<{ id: number; name: string; nameAr?: string | null }[]> {
        return this.http.get<{ id: number; name: string; nameAr?: string | null }[]>(
            this.apiUrl.apiPath(`${this.notifBase}/recipient-groups`));
    }

    /** Students or employees the caller may message, by id, name or email. */
    searchRecipients(type: 'STUDENT' | 'EMPLOYEE', q: string): Observable<RecipientOption[]> {
        const params = new HttpParams().set('type', type).set('q', q);
        return this.http.get<RecipientOption[]>(this.apiUrl.apiPath(`${this.notifBase}/recipients`), { params });
    }

    send(req: SendMessageRequest): Observable<SendMessageResponse> {
        return this.http.post<SendMessageResponse>(this.apiUrl.apiPath(`${this.notifBase}/send`), req);
    }

    previewCount(req: SendMessageRequest): Observable<{ count: number }> {
        return this.http.post<{ count: number }>(this.apiUrl.apiPath(`${this.notifBase}/preview-count`), req);
    }

    getHistory(page: number = 0, size: number = 20, countryId?: number | null,
               filters: NotificationHistoryFilters = {}): Observable<PageResponse<NotificationLogDto>> {
        let params = new HttpParams().set('page', page).set('size', size);
        if (countryId != null) {
            params = params.set('countryId', countryId);
        }
        // Only what is actually set. An empty parameter is not the same as an
        // absent one on the server side: it would be compared to a code and
        // match nothing, emptying the page instead of widening it.
        for (const [key, value] of Object.entries(filters)) {
            if (value !== null && value !== undefined && value !== '') {
                params = params.set(key, value);
            }
        }
        return this.http.get<PageResponse<NotificationLogDto>>(this.apiUrl.apiPath(`${this.notifBase}/history`), { params });
    }

    /**
     * Who one history row's message went to, in dispatch order (V140).
     *
     * <p>Silent: the view dialog reports a failure in its own recipients section,
     * so the global "not found" toast would only say the same thing twice.
     */
    getRecipients(logId: number): Observable<NotificationRecipientDto[]> {
        return this.http.get<NotificationRecipientDto[]>(
            this.apiUrl.apiPath(`${this.notifBase}/history/${logId}/recipients`), { context: silentErrors() });
    }
}
