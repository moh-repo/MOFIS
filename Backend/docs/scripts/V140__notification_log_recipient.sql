/*
    V140 — who a notification actually went to (25 Sep 2026).

    MOHECORE.NOTIFICATION_LOG holds one row per batch and channel with counts
    only — how many were sent, how many failed — and the failures' addresses in
    ERROR_SUMMARY. Nothing recorded who the message reached, so the history page
    could not say to whom a message was sent. This table records one row per
    recipient and channel as the dispatcher works through a batch: who (type,
    id, name), the address that channel used, and the outcome.

    No country column: a row belongs to its log, which is ministry-level and
    scoped in Java (NotificationService.getHistory / getRecipients), exactly as
    NOTIFICATION_LOG itself is. No STUDENT_ID column either, on purpose: the
    recipient may be an employee, and the guards of V032/V065 read a STUDENT_ID
    column as a student row that ought to be under the tenant policy.

    Batches sent before this script have no rows here; the page says so.
    Idempotent. Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'MOHECORE.NOTIFICATION_LOG', N'U') IS NULL
BEGIN
    PRINT 'V140: *** MOHECORE.NOTIFICATION_LOG absent — nothing to attach the recipients to';
    RETURN;
END

IF OBJECT_ID(N'MOHECORE.NOTIFICATION_LOG_RECIPIENT', N'U') IS NULL
BEGIN
    CREATE TABLE MOHECORE.NOTIFICATION_LOG_RECIPIENT (
        ID              BIGINT IDENTITY(1,1) NOT NULL
                            CONSTRAINT PK_NOTIFICATION_LOG_RECIPIENT PRIMARY KEY CLUSTERED,
        LOG_ID          BIGINT         NOT NULL,
        RECIPIENT_TYPE  NVARCHAR(20)   NOT NULL,   -- LU_NOTIFICATION_RECIPIENT_TYPE: STUDENT | EMPLOYEE
        RECIPIENT_ID    BIGINT         NULL,       -- STUDENT_DATA.STUDENT_ID or UMSCORE.Users.Id, by type
        RECIPIENT_NAME  NVARCHAR(300)  NULL,
        ADDRESS         NVARCHAR(320)  NULL,       -- the e-mail or phone this channel used; NULL when the person had none
        STATUS          NVARCHAR(20)   NOT NULL,   -- LU_NOTIFICATION_SEND_STATUS: SENT | FAILED
        ERROR           NVARCHAR(1000) NULL,
        SENT_AT         DATETIME2(3)   NOT NULL
                            CONSTRAINT DF_NOTIFICATION_LOG_RECIPIENT_SENT_AT DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_NOTIFICATION_LOG_RECIPIENT_LOG FOREIGN KEY (LOG_ID)
            REFERENCES MOHECORE.NOTIFICATION_LOG (ID) ON DELETE CASCADE
    );
    CREATE INDEX IX_NOTIFICATION_LOG_RECIPIENT_LOG_ID ON MOHECORE.NOTIFICATION_LOG_RECIPIENT (LOG_ID);
    PRINT 'V140: MOHECORE.NOTIFICATION_LOG_RECIPIENT created';
END
ELSE
    PRINT 'V140: MOHECORE.NOTIFICATION_LOG_RECIPIENT already present';
