/* ============================================================================
   restore_sample_notifications.sql
   Undoes seed_sample_notifications.sql — DEV ONLY

   1. Puts back the notification settings exactly as they were before the first
      seed run — every row, secrets included — from
      MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED, then drops that copy.
      Any settings row added or changed while testing is discarded with the test
      rows; that is the point.
   2. Deletes the seeded history rows (BATCH_ID starting "seed-notify-").
      Messages actually sent while testing are real history rows and stay; delete
      them by hand if you want them gone.

   Safe to run twice: without the copy it restores nothing and says so.

   Run: sqlcmd -S localhost,1433 -d MOHE -U sa -I -f 65001 -b -i restore_sample_notifications.sql
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';
GO

BEGIN TRANSACTION;

IF OBJECT_ID(N'MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED', N'U') IS NULL
BEGIN
    PRINT 'No copy of the real settings found: the settings are left as they are';
END
ELSE
BEGIN
    DELETE FROM MOHECORE.NOTIFICATION_SETTINGS;

    -- Columns named, not *: the copy was taken after V118, and a column added
    -- since must not shift the others.
    INSERT INTO MOHECORE.NOTIFICATION_SETTINGS
        (ID, COUNTRY_ID, EMAIL_ENABLED, SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD,
         SMTP_FROM_ADDRESS, SMTP_FROM_NAME, SMTP_USE_TLS, SMTP_USE_SSL,
         SMS_ENABLED, SMS_WEBHOOK_URL, SMS_WEBHOOK_METHOD, SMS_WEBHOOK_HEADERS, SMS_FROM_NUMBER,
         WHATSAPP_ENABLED, WHATSAPP_WEBHOOK_URL, WHATSAPP_WEBHOOK_HEADERS, WHATSAPP_FROM_NUMBER,
         WHATSAPP_BODY_TEMPLATE, WHATSAPP_MESSAGE_MODE, WHATSAPP_APPROVED_TEMPLATE, UPDATED_BY, UPDATED_AT)
    SELECT ID, COUNTRY_ID, EMAIL_ENABLED, SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD,
           SMTP_FROM_ADDRESS, SMTP_FROM_NAME, SMTP_USE_TLS, SMTP_USE_SSL,
           SMS_ENABLED, SMS_WEBHOOK_URL, SMS_WEBHOOK_METHOD, SMS_WEBHOOK_HEADERS, SMS_FROM_NUMBER,
           WHATSAPP_ENABLED, WHATSAPP_WEBHOOK_URL, WHATSAPP_WEBHOOK_HEADERS, WHATSAPP_FROM_NUMBER,
           WHATSAPP_BODY_TEMPLATE, WHATSAPP_MESSAGE_MODE, WHATSAPP_APPROVED_TEMPLATE, UPDATED_BY, UPDATED_AT
    FROM MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED;

    PRINT 'Real settings restored (' + CAST(@@ROWCOUNT AS varchar(10)) + ' rows)';
    DROP TABLE MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED;
END

DELETE FROM MOHECORE.NOTIFICATION_LOG WHERE LEFT(BATCH_ID, 12) = 'seed-notify-';  -- countries cascade
PRINT 'Seeded history rows deleted: ' + CAST(@@ROWCOUNT AS varchar(10));

COMMIT;
GO

SELECT ID, COUNTRY_ID, EMAIL_ENABLED AS email, SMS_ENABLED AS sms, WHATSAPP_ENABLED AS whatsapp, SMTP_HOST
FROM MOHECORE.NOTIFICATION_SETTINGS ORDER BY COUNTRY_ID;
