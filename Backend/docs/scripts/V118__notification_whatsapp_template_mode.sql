/*
    V118 — WhatsApp: free text, an approved template, or free text falling back
    to the template.

    Owner's decision (19 Sep 2026), after V117 added the WhatsApp gateway: "apply
    both". Meta delivers a free-form text only inside the 24-hour window that
    opens when the recipient last wrote to the business number; outside it the
    Cloud API refuses the message with error 131047 and wants a pre-approved
    template message instead. A notification from the ministry is usually outside
    that window, so a free-text-only channel would fail for most students.

    What
    ----
    MOHECORE.NOTIFICATION_SETTINGS gains:
      WHATSAPP_MESSAGE_MODE       TEXT     — free text only (V117's behaviour, the default)
                                  TEMPLATE — always the approved template
                                  AUTO     — free text first; when the gateway answers that
                                             the 24-hour window is closed (131047), the same
                                             message again as the approved template
      WHATSAPP_APPROVED_TEMPLATE  the JSON request body of the approved-template message,
                                  with the same placeholders as WHATSAPP_BODY_TEMPLATE plus
                                  {{bodyOneLine}} (Meta refuses new lines inside a template
                                  parameter). Required for TEMPLATE and AUTO.

    V117 was not edited: it had already been applied, and Flyway validates checksums.

    Rollback
    --------
        ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS DROP CONSTRAINT CK_NOTIFICATION_SETTINGS_WHATSAPP_MODE;
        ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS DROP CONSTRAINT DF_NOTIFICATION_SETTINGS_WHATSAPP_MODE;
        ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS DROP COLUMN WHATSAPP_MESSAGE_MODE, WHATSAPP_APPROVED_TEMPLATE;
    Roll the application back first.

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_MESSAGE_MODE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS
        ADD WHATSAPP_MESSAGE_MODE NVARCHAR(10) NOT NULL
            CONSTRAINT DF_NOTIFICATION_SETTINGS_WHATSAPP_MODE DEFAULT N'TEXT';
    PRINT 'V118: WHATSAPP_MESSAGE_MODE added';
END
GO

IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_APPROVED_TEMPLATE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS ADD WHATSAPP_APPROVED_TEMPLATE NVARCHAR(MAX) NULL;
    PRINT 'V118: WHATSAPP_APPROVED_TEMPLATE added';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_NOTIFICATION_SETTINGS_WHATSAPP_MODE')
BEGIN
    ALTER TABLE MOHECORE.NOTIFICATION_SETTINGS
        ADD CONSTRAINT CK_NOTIFICATION_SETTINGS_WHATSAPP_MODE
        CHECK (WHATSAPP_MESSAGE_MODE IN (N'TEXT', N'TEMPLATE', N'AUTO'));
    PRINT 'V118: mode check added';
END
GO

/* ---- Verify --------------------------------------------------------------------- */
SELECT COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_MESSAGE_MODE') AS mode_column,
       COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_APPROVED_TEMPLATE') AS approved_template_column;
