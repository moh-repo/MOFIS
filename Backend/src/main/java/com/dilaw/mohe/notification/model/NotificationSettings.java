package com.dilaw.mohe.notification.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "NOTIFICATION_SETTINGS", schema = "MOHECORE")
public class NotificationSettings implements Persistable<Integer> {

    @Id
    @Column(name = "ID", nullable = false)
    private Integer id = 1;

    /** NULL = global default; otherwise per-country SMTP/SMS config for STUDY_COUNTRY_IN. */
    @Column(name = "COUNTRY_ID")
    private Integer countryId;

    @Column(name = "EMAIL_ENABLED", nullable = false)
    private Boolean emailEnabled = false;

    @Column(name = "SMTP_HOST", length = 255)
    private String smtpHost;

    @Column(name = "SMTP_PORT", nullable = false)
    private Integer smtpPort = 587;

    @Column(name = "SMTP_USERNAME", length = 255)
    private String smtpUsername;

    @Column(name = "SMTP_PASSWORD", length = 500)
    private String smtpPassword;

    @Column(name = "SMTP_FROM_ADDRESS", length = 255)
    private String smtpFromAddress;

    @Column(name = "SMTP_FROM_NAME", length = 255)
    private String smtpFromName;

    @Column(name = "SMTP_USE_TLS", nullable = false)
    private Boolean smtpUseTls = true;

    @Column(name = "SMTP_USE_SSL", nullable = false)
    private Boolean smtpUseSsl = false;

    @Column(name = "SMS_ENABLED", nullable = false)
    private Boolean smsEnabled = false;

    @Column(name = "SMS_WEBHOOK_URL", length = 500)
    private String smsWebhookUrl;

    @Column(name = "SMS_WEBHOOK_METHOD", length = 10)
    private String smsWebhookMethod = "POST";

    @Column(name = "SMS_WEBHOOK_HEADERS", columnDefinition = "NVARCHAR(MAX)")
    private String smsWebhookHeaders;

    @Column(name = "SMS_FROM_NUMBER", length = 50)
    private String smsFromNumber;

    @Column(name = "WHATSAPP_ENABLED", nullable = false)
    private Boolean whatsappEnabled = false;

    @Column(name = "WHATSAPP_WEBHOOK_URL", length = 500)
    private String whatsappWebhookUrl;

    /** Extra headers as JSON [{"key","value"}]; carries the gateway credential, write-only. */
    @Column(name = "WHATSAPP_WEBHOOK_HEADERS", columnDefinition = "NVARCHAR(MAX)")
    private String whatsappWebhookHeaders;

    @Column(name = "WHATSAPP_FROM_NUMBER", length = 50)
    private String whatsappFromNumber;

    /** The JSON the gateway expects, with {{to}} {{toDigits}} {{from}} {{body}}; null = Meta Cloud API text message. */
    @Column(name = "WHATSAPP_BODY_TEMPLATE", columnDefinition = "NVARCHAR(MAX)")
    private String whatsappBodyTemplate;

    /** TEXT, TEMPLATE or AUTO (free text, falling back to the approved template); see V118. */
    @Column(name = "WHATSAPP_MESSAGE_MODE", length = 10, nullable = false)
    private String whatsappMessageMode = "TEXT";

    /** The approved-template message's JSON request body; required for TEMPLATE and AUTO. */
    @Column(name = "WHATSAPP_APPROVED_TEMPLATE", columnDefinition = "NVARCHAR(MAX)")
    private String whatsappApprovedTemplate;

    @Column(name = "UPDATED_BY")
    private Integer updatedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    /**
     * The id is assigned by the application (max + 1), so Spring Data would
     * take every row for an existing one and merge it. A row built for a first
     * save is marked new instead, so it is inserted and a clashing id fails on
     * the key rather than overwriting another attaché's row.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean fresh;

    public void markNew() {
        fresh = true;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        fresh = false;
    }

    @PreUpdate
    @PrePersist
    public void touch() {
        updatedAt = Instant.now();
    }
}
