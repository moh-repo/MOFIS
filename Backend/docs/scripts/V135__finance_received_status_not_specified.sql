/*
    V135 — the finance rows nobody has been paid for say so (live QA H2, 23 Sep 2026).

    976 of the 983 rows in MOHECORE.STUDENT_FINANCE carried STUDY_FIN_RECIEVED_STATUS
    0 or NULL: the column's default, which no row of STUDY_FIN_RECEIVED_STATUS
    describes. The form's own rule (review F3) refuses a status of 0, so every one
    of those rows answered 400 the moment it was saved back unchanged — the QA
    tester's "cannot edit". The lookup has had NOT_SPECIFIED since V072, seeded as
    id 99; this moves the rows onto it and makes it the column's default, so the
    quarter roll and a new student's first row land on a real status too.

    The cheque date is a separate matter: it is required from now on only for a
    status that means money moved (RECEIVED, DISBURSED, PARTIALLY_DISBURSED), in
    the code, not here. No date is invented for a row that never had a cheque.

    Side effects to expect on a populated database:
      - TR_STUDENT_FINANCE_Audit_Update writes one AUDITLOG.AuditTrail row per row
        updated (about 976 on dev), CHANGED_COLUMNS = 'STUDY_FIN_RECIEVED_STATUS'.
      - TR_STUDENT_FINANCE_TENANT re-syncs STUDY_COUNTRY_IN_ID from STUDENT_DATA on
        the rows it touches, where the two had drifted.
    STUDENT_FINANCE_ARCH is history and is left as archived.

    Idempotent: the UPDATE touches only rows still on 0/NULL; the default constraint
    is replaced only while it is not the one this script names. Resolves the id by
    CODE and stops if the lookup has no NOT_SPECIFIED row.
    Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @notSpecified INT = (
    SELECT TOP 1 STUDY_FIN_RECEIVED_STATUS_ID
      FROM MOHECORE.STUDY_FIN_RECEIVED_STATUS
     WHERE LTRIM(RTRIM(CODE)) = N'NOT_SPECIFIED');

IF @notSpecified IS NULL
    THROW 51350, 'V135: MOHECORE.STUDY_FIN_RECEIVED_STATUS has no NOT_SPECIFIED row (V072 seeds it) — nothing changed', 1;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. The rows: 0 / NULL → NOT_SPECIFIED.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE MOHECORE.STUDENT_FINANCE
   SET STUDY_FIN_RECIEVED_STATUS = @notSpecified
 WHERE STUDY_FIN_RECIEVED_STATUS IS NULL
    OR STUDY_FIN_RECIEVED_STATUS = 0;

PRINT 'V135: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' finance row(s) moved from 0/NULL to NOT_SPECIFIED (id '
    + CAST(@notSpecified AS NVARCHAR(10)) + ')';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. The column default: the system-named DF__STUDENT_F__STUDY__… ((0)) becomes a
--    named constraint on NOT_SPECIFIED, so a row inserted without a status is on
--    a real one.
-- ─────────────────────────────────────────────────────────────────────────────
DECLARE @oldDefault SYSNAME = (
    SELECT dc.name
      FROM sys.default_constraints dc
      JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
     WHERE dc.parent_object_id = OBJECT_ID(N'MOHECORE.STUDENT_FINANCE')
       AND c.name = N'STUDY_FIN_RECIEVED_STATUS');

DECLARE @ddl NVARCHAR(MAX);

IF @oldDefault IS NOT NULL AND @oldDefault <> N'DF_STUDENT_FINANCE_RECIEVED_STATUS'
BEGIN
    -- EXEC (...) takes no function calls inside its parentheses; the statement is
    -- built first (this line is what the first run of V135 tripped over).
    SET @ddl = N'ALTER TABLE MOHECORE.STUDENT_FINANCE DROP CONSTRAINT ' + QUOTENAME(@oldDefault);
    EXEC sp_executesql @ddl;
    PRINT 'V135: dropped default ' + @oldDefault;
END

IF NOT EXISTS (SELECT 1 FROM sys.default_constraints
                WHERE name = N'DF_STUDENT_FINANCE_RECIEVED_STATUS'
                  AND parent_object_id = OBJECT_ID(N'MOHECORE.STUDENT_FINANCE'))
BEGIN
    SET @ddl = N'ALTER TABLE MOHECORE.STUDENT_FINANCE ADD CONSTRAINT DF_STUDENT_FINANCE_RECIEVED_STATUS DEFAULT ('
             + CAST(@notSpecified AS NVARCHAR(10)) + N') FOR STUDY_FIN_RECIEVED_STATUS';
    EXEC sp_executesql @ddl;
    PRINT 'V135: STUDY_FIN_RECIEVED_STATUS now defaults to NOT_SPECIFIED';
END

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Check.
-- ─────────────────────────────────────────────────────────────────────────────
DECLARE @stillZero INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE
                           WHERE STUDY_FIN_RECIEVED_STATUS IS NULL OR STUDY_FIN_RECIEVED_STATUS = 0);
IF @stillZero > 0
    THROW 51351, 'V135: finance rows are still on status 0/NULL after the update', 1;
PRINT 'V135: no finance row is on status 0/NULL';
