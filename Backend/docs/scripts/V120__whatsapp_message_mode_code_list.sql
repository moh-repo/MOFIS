/*
    V120 — the WhatsApp message mode becomes a code list.

    V118 added MOHECORE.NOTIFICATION_SETTINGS.WHATSAPP_MESSAGE_MODE with a check
    constraint over TEXT / TEMPLATE / AUTO, and the settings screen offered those
    three as an array literal written in the component:

        this.whatsappModeOptions = [
            { label: this.t('...WHATSAPP_MODE_TEXT'),     value: 'TEXT' },
            ...

    which is the one thing the project does not do with a code list — the rule
    since V072 is that every list of business codes, and its names, colours and
    icons, comes from an LU_* table through CodeListService, so an administrator
    can rename or translate one without a deployment. audit_dropdown_sources.py
    is the gate, and this was one of the two bindings failing it.

    LU_WHATSAPP_MESSAGE_MODE therefore carries the same three codes, with the
    Arabic and English names the i18n bundle held, and IS_SYSTEM = 1 because Java
    depends on the codes: WhatsAppMessageMode and the V118 check constraint both
    name them, so the code-list API must refuse to rename, deactivate or delete a
    row here.

    The other failing binding, testChannelOptions, needs no table — the channels
    already exist as LU_NOTIFICATION_CHANNEL, registered as `notification-channel`.

    Rollback
    --------
        DROP TABLE MOHECORE.LU_WHATSAPP_MESSAGE_MODE;
    Roll the application back first: CodeListRegistry names the table at startup.

    Idempotent. THROWs if the three codes are not present afterwards.
    Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

/* ---- 1. The table, in the shape AbstractLuLookup maps --------------------------- */

IF OBJECT_ID(N'MOHECORE.LU_WHATSAPP_MESSAGE_MODE', N'U') IS NULL
BEGIN
    /*
       ID is IDENTITY because AbstractLuLookup maps it
       @GeneratedValue(strategy = IDENTITY), and the two have to agree: on a plain
       INT column Hibernate inserts without the id, asks the driver for the
       generated key, gets nothing and throws "null identifier" — so *add* would
       be broken on this list from the day it shipped. That is the same defect
       LookupWriteRoundTripTest was written for after it was found on 22 lookup
       entities, and IdentityMappingAgreementTest is the guard that catches it.
    */
    CREATE TABLE MOHECORE.LU_WHATSAPP_MESSAGE_MODE (
        ID          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_LU_WHATSAPP_MESSAGE_MODE PRIMARY KEY,
        CODE        NVARCHAR(50)   NOT NULL,
        NAME_AR     NVARCHAR(200)  NOT NULL,
        NAME_EN     NVARCHAR(200)  NOT NULL,
        SORT_ORDER  INT            NOT NULL CONSTRAINT DF_LU_WHATSAPP_MODE_SORT   DEFAULT (0),
        IS_DELETED  BIT            NOT NULL CONSTRAINT DF_LU_WHATSAPP_MODE_DEL    DEFAULT (0),
        UI_SEVERITY NVARCHAR(20)   NULL,
        ICON        NVARCHAR(50)   NULL,
        IS_SYSTEM   BIT            NOT NULL CONSTRAINT DF_LU_WHATSAPP_MODE_SYS    DEFAULT (0),
        CONSTRAINT UQ_LU_WHATSAPP_MESSAGE_MODE_CODE UNIQUE (CODE)
    );
    PRINT 'V120: LU_WHATSAPP_MESSAGE_MODE created';
END
ELSE
    PRINT 'V120: LU_WHATSAPP_MESSAGE_MODE already present';
GO

/* ---- 2. The three modes V118 allows --------------------------------------------- */

/*
   The names are the ones the i18n bundle carried, so the screen reads the same
   after the change as before it. Severities follow the meaning: TEXT is the
   default and unremarkable, TEMPLATE is the deliberate choice, AUTO is the one
   that can fall back at send time and is worth the eye.
*/
/* The id is assigned by IDENTITY; CODE is what the rows are matched on. */
MERGE MOHECORE.LU_WHATSAPP_MESSAGE_MODE AS target
USING (VALUES
        (N'TEXT',     N'نص حر',                        N'Free text',                1, N'info',    N'pi-comment'),
        (N'TEMPLATE', N'قالب معتمد',                    N'Approved template',        2, N'success', N'pi-file'),
        (N'AUTO',     N'نص حر مع التحول إلى القالب',     N'Free text, then template', 3, N'warn',    N'pi-sync')
      ) AS source (CODE, NAME_AR, NAME_EN, SORT_ORDER, UI_SEVERITY, ICON)
   ON target.CODE = source.CODE
 WHEN MATCHED THEN
      UPDATE SET NAME_AR     = COALESCE(NULLIF(target.NAME_AR, N''), source.NAME_AR),
                 NAME_EN     = COALESCE(NULLIF(target.NAME_EN, N''), source.NAME_EN),
                 UI_SEVERITY = COALESCE(target.UI_SEVERITY, source.UI_SEVERITY),
                 ICON        = COALESCE(target.ICON, source.ICON),
                 IS_SYSTEM   = 1
 WHEN NOT MATCHED THEN
      INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED, UI_SEVERITY, ICON, IS_SYSTEM)
      VALUES (source.CODE, source.NAME_AR, source.NAME_EN, source.SORT_ORDER, 0,
              source.UI_SEVERITY, source.ICON, 1);

PRINT CONCAT('V120: ', @@ROWCOUNT, ' WhatsApp message mode row(s) merged');
GO

/* ---- 3. Verify ------------------------------------------------------------------- */

IF (SELECT COUNT(*) FROM MOHECORE.LU_WHATSAPP_MESSAGE_MODE
     WHERE CODE IN (N'TEXT', N'TEMPLATE', N'AUTO') AND IS_DELETED = 0) <> 3
    THROW 50120, 'V120: the three WhatsApp message modes are not all present and active.', 1;

SELECT ID, CODE, NAME_AR, NAME_EN, SORT_ORDER, UI_SEVERITY, ICON, IS_SYSTEM
  FROM MOHECORE.LU_WHATSAPP_MESSAGE_MODE
 ORDER BY SORT_ORDER;
