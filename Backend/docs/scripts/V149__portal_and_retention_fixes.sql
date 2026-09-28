/*
    V149 — the student-status date retention measures from, and two portal
    account columns (system test PRT-01, PRT-04, PRT-07).

    1. MOHECORE.STUDENT_STUDY_STATUS_CHANGE (PRT-01)
    ------------------------------------------------
    DataRetentionJob anonymises a student "in a terminal status for N years", and
    measured that from STUDENT_DATA.STUDENT_MIDATE — the date the record was
    entered. A student who joined eight years ago and withdrew yesterday was
    anonymised, and their files deleted, on the next apply. Nothing in the schema
    recorded when a student's study status last changed, so this script starts
    recording it:

      - one row per student: the status last seen and when it was set;
      - kept by an AFTER INSERT, UPDATE trigger on STUDENT_DATA, so every write
        path (screens, approvals, the portal, ETL, hand-written SQL) is covered.
        The trigger writes only to this table, never back to STUDENT_DATA, so it
        does not re-fire the STUDENT_DATA audit and tenant triggers;
      - backfilled, for students it has no row for, from the quarterly archive
        (STUDENT_DATA_ARCH): the date of the FIRST snapshot showing the current
        status after the LAST snapshot showing any other status. The status was
        set at some point before that snapshot, so the date errs late — the
        retention clock starts later than it really did, never earlier;
      - a student with no archive evidence gets the date this script runs
        (SOURCE = 'MIGRATION'): the status is known to hold today and nothing
        says since when, so the clock starts now. Such a student is not a
        retention candidate until the full period has passed from today.

    The job reads a student only when this table's status equals the record's
    current status; a student without a row, or whose row disagrees, is never a
    candidate.

    2. STUDENTCORE.student_users.locked_until (PRT-04)
    ---------------------------------------------------
    A timed lock after repeated wrong portal passwords. failed_login_attempts
    already existed and was never incremented.

    3. STUDENTCORE.student_users.totp_last_step (PRT-07)
    -----------------------------------------------------
    The TOTP time step last accepted for the account, so a code cannot be used
    twice (the staff MFA's LastUsedStep).

    Idempotent. Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'MOHECORE.STUDENT_STUDY_STATUS_CHANGE', N'U') IS NULL
BEGIN
    CREATE TABLE MOHECORE.STUDENT_STUDY_STATUS_CHANGE (
        STUDENT_ID              int           NOT NULL CONSTRAINT PK_STUDENT_STUDY_STATUS_CHANGE PRIMARY KEY,
        STUDENT_STUDY_STATUS_ID int           NULL,
        CHANGED_AT              datetime2     NOT NULL,
        -- TRIGGER: seen changing; ARCHIVE: backfilled from STUDENT_DATA_ARCH; MIGRATION: no evidence, clock started by V149.
        SOURCE                  nvarchar(20)  NOT NULL
    );
    PRINT 'V149: MOHECORE.STUDENT_STUDY_STATUS_CHANGE created';
END
GO

-- The date is server-local (GETDATE), like STUDENT_MIDATE, because the job compares it
-- with LocalDateTime.now() on the application server.
CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_DATA_STATUS_CHANGE
ON MOHECORE.STUDENT_DATA AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(STUDENT_STUDY_STATUS_ID) RETURN;

    UPDATE c
       SET c.STUDENT_STUDY_STATUS_ID = i.STUDENT_STUDY_STATUS_ID,
           c.CHANGED_AT = GETDATE(),
           c.SOURCE = N'TRIGGER'
      FROM MOHECORE.STUDENT_STUDY_STATUS_CHANGE c
      JOIN inserted i ON i.STUDENT_ID = c.STUDENT_ID
     WHERE ISNULL(c.STUDENT_STUDY_STATUS_ID, -1) <> ISNULL(i.STUDENT_STUDY_STATUS_ID, -1);

    INSERT INTO MOHECORE.STUDENT_STUDY_STATUS_CHANGE (STUDENT_ID, STUDENT_STUDY_STATUS_ID, CHANGED_AT, SOURCE)
    SELECT i.STUDENT_ID, i.STUDENT_STUDY_STATUS_ID, GETDATE(), N'TRIGGER'
      FROM inserted i
     WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_STUDY_STATUS_CHANGE c WHERE c.STUDENT_ID = i.STUDENT_ID);
END
GO

-- Backfill every student the trigger has not seen yet. STUDENT_DATA and its archive are
-- under the V031 policy; the migration reads every country's rows.
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

;WITH cur AS (
    SELECT d.STUDENT_ID, d.STUDENT_STUDY_STATUS_ID
      FROM MOHECORE.STUDENT_DATA d
     WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_STUDY_STATUS_CHANGE c WHERE c.STUDENT_ID = d.STUDENT_ID)
), snap AS (
    SELECT a.STUDENT_ID, a.STUDENT_STUDY_STATUS_ID, COALESCE(a.ARCHIVE_DATE, a.INSERTED_TO_ARCH_DATE) AS SEEN_AT
      FROM MOHECORE.STUDENT_DATA_ARCH a
     WHERE COALESCE(a.ARCHIVE_DATE, a.INSERTED_TO_ARCH_DATE) IS NOT NULL
), last_other AS (
    SELECT c.STUDENT_ID, MAX(s.SEEN_AT) AS SEEN_AT
      FROM cur c
      JOIN snap s ON s.STUDENT_ID = c.STUDENT_ID
                 AND ISNULL(s.STUDENT_STUDY_STATUS_ID, -1) <> ISNULL(c.STUDENT_STUDY_STATUS_ID, -1)
     GROUP BY c.STUDENT_ID
), first_same AS (
    SELECT c.STUDENT_ID, MIN(s.SEEN_AT) AS SEEN_AT
      FROM cur c
      JOIN snap s ON s.STUDENT_ID = c.STUDENT_ID
                 AND ISNULL(s.STUDENT_STUDY_STATUS_ID, -1) = ISNULL(c.STUDENT_STUDY_STATUS_ID, -1)
      LEFT JOIN last_other o ON o.STUDENT_ID = c.STUDENT_ID
     WHERE o.SEEN_AT IS NULL OR s.SEEN_AT > o.SEEN_AT
     GROUP BY c.STUDENT_ID
)
INSERT INTO MOHECORE.STUDENT_STUDY_STATUS_CHANGE (STUDENT_ID, STUDENT_STUDY_STATUS_ID, CHANGED_AT, SOURCE)
SELECT c.STUDENT_ID,
       c.STUDENT_STUDY_STATUS_ID,
       CASE WHEN f.SEEN_AT IS NULL OR f.SEEN_AT > GETDATE() THEN GETDATE() ELSE f.SEEN_AT END,
       CASE WHEN f.SEEN_AT IS NULL THEN N'MIGRATION' ELSE N'ARCHIVE' END
  FROM cur c
  LEFT JOIN first_same f ON f.STUDENT_ID = c.STUDENT_ID;
PRINT CONCAT('V149: status dates backfilled: ', @@ROWCOUNT);

EXEC sp_set_session_context @key = N'TenantAll', @value = NULL;
GO

IF COL_LENGTH('STUDENTCORE.student_users', 'locked_until') IS NULL
BEGIN
    ALTER TABLE STUDENTCORE.student_users ADD locked_until datetime2 NULL;
    PRINT 'V149: student_users.locked_until added';
END
GO
IF COL_LENGTH('STUDENTCORE.student_users', 'totp_last_step') IS NULL
BEGIN
    ALTER TABLE STUDENTCORE.student_users ADD totp_last_step bigint NULL;
    PRINT 'V149: student_users.totp_last_step added';
END
GO
