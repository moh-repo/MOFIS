package com.dilaw.mohe.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class NotificationSettingsDto {

    /** Target country for per-country settings; null = global default. */
    private Integer countryId;
    /** True when no country-specific row exists and values come from global default. */
    private Boolean inheritedFromGlobal;

    private Boolean emailEnabled;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    /** Null when reading — only populated when the client explicitly updates the password. */
    private String smtpPassword;
    private String smtpFromAddress;
    private String smtpFromName;
    private Boolean smtpUseTls;
    private Boolean smtpUseSsl;

    private Boolean smsEnabled;
    private String smsWebhookUrl;
    private String smsWebhookMethod;
    /** JSON string of extra headers: [{"key":"Authorization","value":"Bearer token"}] */
    private String smsWebhookHeaders;
    private String smsFromNumber;

    private Boolean whatsappEnabled;
    private String whatsappWebhookUrl;
    /** Write-only like {@link #smsWebhookHeaders}: null on read, blank on save keeps the stored value. */
    private String whatsappWebhookHeaders;
    private String whatsappFromNumber;
    /** Null or blank = the Meta Cloud API text-message shape. */
    private String whatsappBodyTemplate;
    /** TEXT | TEMPLATE | AUTO (free text, then the approved template when the 24-hour window is closed). */
    private String whatsappMessageMode;
    /** The approved-template message's JSON request body; required for TEMPLATE and AUTO. */
    private String whatsappApprovedTemplate;

    // ---- read-only, filled on reads -------------------------------------------------

    /** The country's display name; null on the global row. */
    private String countryName;
    /** Whether a secret is stored, since the secrets themselves never come back. */
    private Boolean smtpPasswordSet;
    private Boolean smsWebhookHeadersSet;
    private Boolean whatsappWebhookHeadersSet;
    /** Whether the caller may change (and, for a country row, delete) this row. */
    private Boolean canEdit;
    private Instant updatedAt;
}
