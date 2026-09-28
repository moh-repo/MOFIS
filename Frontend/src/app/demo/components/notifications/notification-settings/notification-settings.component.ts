import { Component, OnInit, OnDestroy } from '@angular/core';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import {
    DeliveryChannel, NotificationService, NotificationSettingsDto, NotificationTestResult, WhatsAppMessageMode
} from '../../../service/notification.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

/** The Meta WhatsApp Cloud API text message — what the server sends when no template is saved. */
export const WHATSAPP_DEFAULT_TEMPLATE =
    '{"messaging_product":"whatsapp","recipient_type":"individual","to":"{{toDigits}}",'
    + '"type":"text","text":{"preview_url":false,"body":"{{body}}"}}';

/**
 * A Cloud API template message with one body parameter. The name and language
 * are whatever was approved in the WhatsApp Business account, so this is an
 * example to edit, inserted on request, never saved on its own.
 */
export const WHATSAPP_EXAMPLE_APPROVED_TEMPLATE =
    '{"messaging_product":"whatsapp","recipient_type":"individual","to":"{{toDigits}}",'
    + '"type":"template","template":{"name":"mohe_notification","language":{"code":"ar"},'
    + '"components":[{"type":"body","parameters":[{"type":"text","text":"{{bodyOneLine}}"}]}]}}';

function emptySettings(): NotificationSettingsDto {
    return {
        countryId: null,
        emailEnabled: false,
        smtpHost: null, smtpPort: 587,
        smtpUsername: null, smtpPassword: null,
        smtpFromAddress: null, smtpFromName: null,
        smtpUseTls: true, smtpUseSsl: false,
        smsEnabled: false,
        smsWebhookUrl: null, smsWebhookMethod: 'POST',
        smsWebhookHeaders: null, smsFromNumber: null,
        whatsappEnabled: false,
        whatsappWebhookUrl: null, whatsappWebhookHeaders: null,
        whatsappFromNumber: null, whatsappBodyTemplate: null,
        whatsappMessageMode: 'TEXT', whatsappApprovedTemplate: null
    };
}

/**
 * Notification settings as a list: the global row (all countries) and one row
 * per attaché that has its own. An attaché without a row sends through the
 * global one.
 *
 * <p>Who may change what is the server's call (CountryOwnedConfigPolicy): the
 * global row is the ministry's; an attaché's row is its own staff's, and the
 * ministry's too. Each row comes back with {@code canEdit}, so the page never
 * guesses.
 */
@Component({
    selector: 'app-notification-settings',
    templateUrl: './notification-settings.component.html',
    standalone: false
})
export class NotificationSettingsComponent implements OnInit, OnDestroy {

    private destroy$ = new Subject<void>();

    rows: NotificationSettingsDto[] = [];
    loading = false;
    /** Narrows the table to one attaché; null shows every row. */
    filterCountryId: number | null = null;

    /** The attachés the user may see, from the study-country list. */
    countryOptions: LookupItem[] = [];

    // ---- add / edit dialog ----------------------------------------------------------
    dialogVisible = false;
    editMode = false;
    readOnly = false;
    submitted = false;
    saving = false;
    current: NotificationSettingsDto = emptySettings();
    /** Active tab. p-tabs renders no panel until a value is bound, which left the tabs blank. */
    activeTab: string | number = 0;
    /** Webhook verbs, from the http-method list. */
    httpMethodOptions: Array<{ label: string; value: string }> = [];
    readonly whatsappDefaultTemplate = WHATSAPP_DEFAULT_TEMPLATE;
    readonly whatsappExampleApprovedTemplate = WHATSAPP_EXAMPLE_APPROVED_TEMPLATE;
    whatsappModeOptions: Array<{ label: string; value: WhatsAppMessageMode }> = [];

    // ---- view: one scope's settings, read-only ---------------------------------------
    viewVisible = false;
    viewing: NotificationSettingsDto | null = null;

    // ---- delete ---------------------------------------------------------------------
    deleteDialogVisible = false;
    rowToDelete: NotificationSettingsDto | null = null;
    deleting = false;

    // ---- test send (review R7): one message through a row's saved settings ------------
    showTestDialog = false;
    testRow: NotificationSettingsDto | null = null;
    testChannel: DeliveryChannel = 'EMAIL';
    testTo = '';
    testSending = false;
    testResult: NotificationTestResult | null = null;
    testChannelOptions: Array<{ label: string; value: DeliveryChannel }> = [];

    /** Every delivery channel the code list knows, narrowed per row in openTestDialog(). */
    private channelOptions: Array<{ label: string; value: string }> = [];

    constructor(
        private service: NotificationService,
        private lookupService: LookupService,
        private umsAuthService: UmsAuthService,
        private messageService: MessageService,
        public translationService: TranslationService,
        private enumOptions: EnumOptionsService
    ) {}

    ngOnInit(): void {
        // The three modes are a code list (V120), not a literal: an administrator
        // renames or translates them in Settings rather than through a deployment.
        this.enumOptions.getDropdownOptions('whatsapp-message-mode')
            .pipe(takeUntil(this.destroy$))
            .subscribe(opts => {
                this.whatsappModeOptions = opts as Array<{ label: string; value: WhatsAppMessageMode }>;
            });
        // Which channels a test message can go out on. LU_NOTIFICATION_CHANNEL
        // also carries BOTH, which is a setting for real sends and not somewhere
        // a test can be addressed, so the list is narrowed to the channels this
        // row actually has switched on — which is what the default below already
        // assumed.
        this.enumOptions.getDropdownOptions('notification-channel')
            .pipe(takeUntil(this.destroy$))
            .subscribe(opts => { this.channelOptions = opts; });
        this.enumOptions.getDropdownOptions('http-method')
            .pipe(takeUntil(this.destroy$))
            .subscribe(opts => { this.httpMethodOptions = opts; });
        this.lookupService.getAllStudyCountries()
            .pipe(takeUntil(this.destroy$))
            .subscribe(list => {
                this.countryOptions = this.umsAuthService.filterToAllowedStudyCountries(
                    (list as any[]).map(c => ({ id: c.studyCountryInId ?? c.id, name: c.studyCountryInName ?? c.name }))
                );
            });
        this.loadRows();
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    loadRows(): void {
        this.loading = true;
        this.service.listSettings().subscribe({
            next: rows => {
                this.rows = rows;
                this.loading = false;
            },
            error: () => {
                this.loading = false;
                this.showError('COMMON.LOAD_ERROR');
            }
        });
    }

    get visibleRows(): NotificationSettingsDto[] {
        if (this.filterCountryId == null) return this.rows;
        return this.rows.filter(r => r.countryId === this.filterCountryId);
    }

    /** Attachés in scope that have no row of their own yet — the ones "Add" can offer. */
    get addableCountries(): LookupItem[] {
        const taken = new Set(this.rows.map(r => r.countryId).filter(id => id != null));
        return this.countryOptions.filter(c => !taken.has(c.id));
    }

    /** The ministry may add any attaché's row; an attaché its own (the server decides either way). */
    get canAdd(): boolean {
        return this.addableCountries.length > 0;
    }

    scopeLabel(row: NotificationSettingsDto): string {
        if (row.countryId == null) return this.t('NOTIFICATIONS.SETTINGS.GLOBAL_DEFAULT');
        return row.countryName
            || this.countryOptions.find(c => c.id === row.countryId)?.name
            || String(row.countryId);
    }

    // ---- add / edit -----------------------------------------------------------------

    /** A new attaché row starts from the global row's non-secret values. */
    openNew(): void {
        const global = this.rows.find(r => r.countryId == null);
        this.current = {
            ...emptySettings(),
            ...(global ? this.withoutReadOnly(global) : {}),
            countryId: this.addableCountries.length === 1 ? this.addableCountries[0].id : null
        };
        this.clearSecrets(this.current);
        this.editMode = false;
        this.readOnly = false;
        this.openDialog();
    }

    editRow(row: NotificationSettingsDto): void {
        this.current = { ...emptySettings(), ...this.withoutReadOnly(row) };
        this.current.smtpPasswordSet = row.smtpPasswordSet;
        this.current.smsWebhookHeadersSet = row.smsWebhookHeadersSet;
        this.current.whatsappWebhookHeadersSet = row.whatsappWebhookHeadersSet;
        this.current.countryName = row.countryName;
        this.clearSecrets(this.current);
        this.editMode = true;
        this.readOnly = !row.canEdit;
        this.openDialog();
    }

    private openDialog(): void {
        this.submitted = false;
        this.activeTab = 0;
        this.dialogVisible = true;
    }

    /** The server's read-only fields are not sent back. */
    private withoutReadOnly(row: NotificationSettingsDto): NotificationSettingsDto {
        const { countryName, smtpPasswordSet, smsWebhookHeadersSet, whatsappWebhookHeadersSet,
                canEdit, updatedAt, inheritedFromGlobal, ...rest } = row;
        return rest as NotificationSettingsDto;
    }

    /** The secrets are write-only: blank on save keeps what is stored. */
    private clearSecrets(dto: NotificationSettingsDto): void {
        dto.smtpPassword = null;
        dto.smsWebhookHeaders = null;
        dto.whatsappWebhookHeaders = null;
    }

    /** The first problem with the form, as a translation key; null when it may be saved. */
    get formProblem(): string | null {
        const c = this.current;
        if (!this.editMode && c.countryId == null) return 'NOTIFICATIONS.SETTINGS.COUNTRY_REQUIRED';
        if (c.emailEnabled && !c.smtpHost?.trim()) return 'NOTIFICATIONS.SETTINGS.SMTP_HOST_REQUIRED';
        if (c.smsEnabled && !c.smsWebhookUrl?.trim()) return 'NOTIFICATIONS.SETTINGS.SMS_URL_REQUIRED';
        if (c.whatsappEnabled && !c.whatsappWebhookUrl?.trim()) return 'NOTIFICATIONS.SETTINGS.WHATSAPP_URL_REQUIRED';
        if (c.whatsappEnabled && this.usesApprovedTemplate && !c.whatsappApprovedTemplate?.trim()) {
            return 'NOTIFICATIONS.SETTINGS.WHATSAPP_APPROVED_REQUIRED';
        }
        return null;
    }

    /** TEMPLATE and AUTO send the approved template, so they need one. */
    get usesApprovedTemplate(): boolean {
        return this.current.whatsappMessageMode === 'TEMPLATE' || this.current.whatsappMessageMode === 'AUTO';
    }

    insertExampleApprovedTemplate(): void {
        this.current.whatsappApprovedTemplate = WHATSAPP_EXAMPLE_APPROVED_TEMPLATE;
    }

    save(): void {
        this.submitted = true;
        if (this.readOnly) return;
        const problem = this.formProblem;
        if (problem) {
            this.showError(problem);
            return;
        }
        this.saving = true;
        const payload = this.withoutReadOnly(this.current);
        const request = this.editMode ? this.service.updateSettings(payload) : this.service.createSettings(payload);
        request.subscribe({
            next: () => {
                this.saving = false;
                this.dialogVisible = false;
                this.showSuccess('COMMON.SAVED_SUCCESSFULLY');
                this.loadRows();
            },
            error: err => {
                this.saving = false;
                this.showServerError(err, 'COMMON.SAVE_ERROR');
            }
        });
    }

    // ---- view -----------------------------------------------------------------------

    openView(row: NotificationSettingsDto): void {
        this.viewing = row;
        this.viewVisible = true;
    }

    /** From the view straight into editing the same row; the server said whether that is allowed. */
    editFromView(): void {
        const row = this.viewing;
        if (!row?.canEdit) return;
        this.viewVisible = false;
        this.editRow(row);
    }

    testFromView(): void {
        const row = this.viewing;
        if (!row) return;
        this.viewVisible = false;
        this.openTestDialog(row);
    }

    channelsOn(row: NotificationSettingsDto): number {
        return [row.emailEnabled, row.smsEnabled, row.whatsappEnabled].filter(Boolean).length;
    }

    channelsOnText(row: NotificationSettingsDto): string {
        return this.t('NOTIFICATIONS.SETTINGS.CHANNELS_ON').replace('{count}', String(this.channelsOn(row)));
    }

    /** Grey with nothing switched on; otherwise blue for the global row, green for a country's. */
    viewHeroClass(row: NotificationSettingsDto): string {
        if (this.channelsOn(row) === 0) return 'dv-hero--muted';
        return row.countryId == null ? 'dv-hero--info' : 'dv-hero--success';
    }

    /** Secrets never come back; only whether one is stored. */
    secretText(stored?: boolean): string {
        return this.t(stored ? 'NOTIFICATIONS.SETTINGS.SECRET_SET' : 'NOTIFICATIONS.SETTINGS.SECRET_NOT_SET');
    }

    securityText(row: NotificationSettingsDto): string {
        if (row.smtpUseSsl) return this.t('NOTIFICATIONS.SETTINGS.SECURITY_SSL');
        if (row.smtpUseTls) return this.t('NOTIFICATIONS.SETTINGS.SECURITY_TLS');
        return this.t('NOTIFICATIONS.SETTINGS.SECURITY_NONE');
    }

    whatsappModeLabel(mode: WhatsAppMessageMode | null | undefined): string {
        return this.whatsappModeOptions.find(o => o.value === mode)?.label || mode || '—';
    }

    /** Just the host of a webhook URL, for the tile; the full URL is in its section. */
    hostOf(url: string | null | undefined): string {
        if (!url) return '—';
        try { return new URL(url).host || url; } catch { return url; }
    }

    // ---- delete ---------------------------------------------------------------------

    confirmDelete(row: NotificationSettingsDto): void {
        if (row.countryId == null || !row.canEdit) return;
        this.rowToDelete = row;
        this.deleteDialogVisible = true;
    }

    deleteRow(): void {
        const row = this.rowToDelete;
        if (!row || row.countryId == null) return;
        this.deleting = true;
        this.service.deleteSettings(row.countryId).subscribe({
            next: () => {
                this.deleting = false;
                this.deleteDialogVisible = false;
                this.rowToDelete = null;
                this.showSuccess('NOTIFICATIONS.SETTINGS.DELETED');
                this.loadRows();
            },
            error: err => {
                this.deleting = false;
                this.showServerError(err, 'COMMON.DELETE_ERROR');
            }
        });
    }

    // ---- test send ------------------------------------------------------------------

    openTestDialog(row: NotificationSettingsDto): void {
        this.testRow = row;
        const enabled: Record<string, boolean> = {
            EMAIL: !!row.emailEnabled, SMS: !!row.smsEnabled, WHATSAPP: !!row.whatsappEnabled
        };
        this.testChannelOptions = this.channelOptions
            .filter(o => enabled[o.value])
            .map(o => ({ label: o.label, value: o.value as DeliveryChannel }));
        this.testChannel = row.emailEnabled ? 'EMAIL' : row.smsEnabled ? 'SMS' : row.whatsappEnabled ? 'WHATSAPP' : 'EMAIL';
        this.testResult = null;
        this.showTestDialog = true;
    }

    sendTest(): void {
        const to = this.testTo.trim();
        if (!to) {
            this.showError('NOTIFICATIONS.SETTINGS.TEST_TO_REQUIRED');
            return;
        }
        this.testSending = true;
        this.testResult = null;
        this.service.testSettings(this.testRow?.countryId ?? null, this.testChannel, to).subscribe({
            next: result => {
                this.testSending = false;
                this.testResult = result;
            },
            error: err => {
                this.testSending = false;
                // A 400 is the server refusing the address or number, not the relay failing.
                this.testResult = { ok: false, enabled: true, error: this.serverMessage(err) || this.t('COMMON.ERROR') };
            }
        });
    }

    // ---- helpers --------------------------------------------------------------------

    private serverMessage(err: any): string | null {
        return err?.error?.message || err?.error?.error || null;
    }

    private showServerError(err: any, fallbackKey: string): void {
        this.messageService.add({
            severity: 'error', summary: this.t('COMMON.ERROR'),
            detail: this.serverMessage(err) || this.t(fallbackKey)
        });
    }

    private showSuccess(key: string): void {
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t(key) });
    }

    private showError(key: string): void {
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t(key) });
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }
}
