/*
    V117 — WhatsApp as a notification channel.

    Review request (19 Sep 2026): notifications go out by e-mail and SMS only; the
    ministry and the attachés want WhatsApp as well, configured per attaché like
    the other two, with the ministry-wide row as the fallback.

    What
    ----
    1. MOHECORE.NOTIFICATION_SETTINGS gains the WhatsApp gateway, shaped like the
       SMS webhook that sits beside it:
         WHATSAPP_ENABLED          bit, default 0
         WHATSAPP_WEBHOOK_URL      the gateway endpoint (e.g. the Meta Cloud API
                                   https://graph.facebook.com/v21.0/<phone-number-id>/messages)
         WHATSAPP_WEBHOOK_HEADERS  extra headers as JSON [{"key","value"}] — carries
                                   the gateway credential, write-only through the API
         WHATSAPP_FROM_NUMBER      the sender number or id, offered to the template
         WHATSAPP_BODY_TEMPLATE    the JSON the gateway expects, with {{to}},
                                   {{toDigits}}, {{from}} and {{body}} placeholders;
                                   NULL means the Meta Cloud API text-message shape
                                   (WhatsAppService.DEFAULT_BODY_TEMPLATE)
    2. LU_NOTIFICATION_CHANNEL gains WHATSAPP. The four channel codes become
       IS_SYSTEM = 1: the dispatcher now branches on them
       (com.dilaw.mohe.codes.NotificationChannel, checked by CodeListStartupGuard),
       so the lookup screen must not delete or rename them.

    Rollback
    --------
        ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS DROP CONSTRAINT DF_NOTIFICATION_SETTINGS_WHATSAPP_ENABLED;
        ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS DROP COLUMN WHATSAPP_ENABLED, WHATSAPP_WEBHOOK_URL,
            WHATSAPP_WEBHOOK_HEADERS, WHATSAPP_FROM_NUMBER, WHATSAPP_BODY_TEMPLATE;
        DELETE FROM MOHECORE.LU_NOTIFICATION_CHANNEL WHERE CODE = N'WHATSAPP';
        UPDATE MOHECORE.LU_NOTIFICATION_CHANNEL SET IS_SYSTEM = 0 WHERE CODE IN (N'EMAIL', N'SMS', N'BOTH');
    Roll the application back first: it refuses to boot without the WHATSAPP code.

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_ENABLED') IS NULL
BEGIN
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS
        ADD WHATSAPP_ENABLED BIT NOT NULL
            CONSTRAINT DF_NOTIFICATION_SETTINGS_WHATSAPP_ENABLED DEFAULT 0;
    PRINT 'V117: WHATSAPP_ENABLED added';
END
GO

IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_WEBHOOK_URL') IS NULL
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS ADD WHATSAPP_WEBHOOK_URL NVARCHAR(500) NULL;
IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_WEBHOOK_HEADERS') IS NULL
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS ADD WHATSAPP_WEBHOOK_HEADERS NVARCHAR(MAX) NULL;
IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_FROM_NUMBER') IS NULL
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS ADD WHATSAPP_FROM_NUMBER NVARCHAR(50) NULL;
IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_BODY_TEMPLATE') IS NULL
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS ADD WHATSAPP_BODY_TEMPLATE NVARCHAR(MAX) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM MOHECORE.LU_NOTIFICATION_CHANNEL WHERE CODE = N'WHATSAPP')
BEGIN
    INSERT INTO MOHECORE.LU_NOTIFICATION_CHANNEL (CODE, NAME_AR, NAME_EN, SORT_ORDER, UI_SEVERITY, ICON, IS_SYSTEM)
    VALUES (N'WHATSAPP', N'واتساب', N'WhatsApp',
            (SELECT ISNULL(MAX(SORT_ORDER), 0) + 1 FROM MOHECORE.LU_NOTIFICATION_CHANNEL),
            N'success', N'pi-whatsapp', 1);
    PRINT 'V117: WHATSAPP channel added';
END
GO

UPDATE MOHECORE.LU_NOTIFICATION_CHANNEL
   SET IS_SYSTEM = 1, IS_DELETED = 0
 WHERE CODE IN (N'EMAIL', N'SMS', N'BOTH', N'WHATSAPP')
   AND (IS_SYSTEM = 0 OR IS_DELETED = 1);
GO

/* ---- Verify --------------------------------------------------------------------- */
SELECT COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_BODY_TEMPLATE') AS whatsapp_template_column,
       (SELECT COUNT(*) FROM MOHECORE.LU_NOTIFICATION_CHANNEL WHERE IS_SYSTEM = 1) AS system_channels;
