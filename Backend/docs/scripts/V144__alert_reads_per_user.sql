/*
    V144 — an alert is read per user, not for everybody (live QA N10, FIX-PLAN P5-3).

    MOHE_ALERTS.alerts carries one is_read / read_date / read_by for all accounts.
    PUT /api/v1/alerts/{id}/read?readBy= and PUT /mark-all-read?readBy= took the
    reader's name from the request (default "system") and marked the alert read for
    every account, and the unread count read that one flag: any signed-in account
    could clear everybody's alerts, under any name.

    This table holds who has read what. The application takes the reader from the
    session and counts an account's unread alerts against its own rows. The three old
    columns are left in place and no longer written or read (dropping them is a later
    clean-up).

    1. MOHE_ALERTS.alert_reads (alert_id, user_id) is created when absent; a deleted
       alert takes its read rows with it (ON DELETE CASCADE).
    2. Every alert already marked read is copied for the account its read_by names,
       only where the name is an existing account and the row is not there yet.
       On the development database (read 26 Sep 2026): 512 alerts, 5 marked read, all
       by "admin", so 5 rows are copied.

    Strictly idempotent: a second run creates nothing and copies nothing.

    Rollback
    --------
        DROP TABLE MOHE_ALERTS.alert_reads;
    (the old columns were never changed, so nothing else is needed)
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'MOHE_ALERTS.alerts', N'U') IS NULL
BEGIN
    PRINT 'V144: *** MOHE_ALERTS.alerts absent — alert_reads not created';
    RETURN;
END

IF OBJECT_ID(N'MOHE_ALERTS.alert_reads', N'U') IS NULL
BEGIN
    CREATE TABLE MOHE_ALERTS.alert_reads (
        alert_id INT       NOT NULL,
        user_id  BIGINT    NOT NULL,
        read_at  DATETIME2 NOT NULL CONSTRAINT DF_alert_reads_read_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_alert_reads PRIMARY KEY (alert_id, user_id),
        CONSTRAINT FK_alert_reads_alert FOREIGN KEY (alert_id)
            REFERENCES MOHE_ALERTS.alerts (alert_id) ON DELETE CASCADE
    );
    CREATE INDEX IX_alert_reads_user ON MOHE_ALERTS.alert_reads (user_id);
    PRINT 'V144: MOHE_ALERTS.alert_reads created';
END

INSERT INTO MOHE_ALERTS.alert_reads (alert_id, user_id, read_at)
SELECT a.alert_id, u.Id, ISNULL(CAST(a.read_date AS DATETIME2), SYSUTCDATETIME())
  FROM MOHE_ALERTS.alerts a
  JOIN UMSCORE.Users u ON u.UserName = a.read_by
 WHERE a.is_read = 1
   AND NOT EXISTS (SELECT 1 FROM MOHE_ALERTS.alert_reads r WHERE r.alert_id = a.alert_id AND r.user_id = u.Id);
PRINT CONCAT('V144: existing reads copied: ', @@ROWCOUNT);
