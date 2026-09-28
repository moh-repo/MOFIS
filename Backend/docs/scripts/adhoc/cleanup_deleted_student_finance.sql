/*
    Finance rows and portal accounts left behind by deleted students  --  live QA H7 (25 Sep 2026).
    DBA script, not a migration. WRITTEN, NOT RUN (decision D19): review it, dry-run it, then decide.

    Why: a student delete is a soft delete (STUDENT_DATA.IS_DELETED = 1) and, until the H7 fix, touched
    nothing else. Student 10116 (deleted 24 Sep 2026) kept its STUDENT_FINANCE row: amount 0, received
    year 20251 (a YYYYQ quarter key stored as the year). The code now removes the finance row, its payment
    rows and the portal account's access when a student is deleted, and every hand-written report query
    excludes deleted students. Two views over STUDENT_FINANCE that aggregate without a student id
    (VW_FINANCIAL_DATA_BY_YEAR, VW_FINANCIAL_BY_QUARTER) cannot be filtered from Java; they drop the
    leftover rows only when this script has removed them.

    What it does, for every student with IS_DELETED = 1 (read-only counts on dev, 25 Sep 2026: students
    10116, 10117, 10118; 1 finance row (10116); 3 payment rows, one each; no active portal account):
      - deletes the student's STUDENT_FINANCE_PAYMENT rows and STUDENT_FINANCE row (the finance audit
        trigger keeps a copy of the deleted row);
      - sets the student's portal account INACTIVE and deletes its sessions.
    Before the finance row is deleted it is ARCHIVED (decision of 26 Sep 2026, FIX-PLAN P5-11: "they must be
    archived even if they are deleted"), the way the application now does it at delete time: the row is
    written into STUDENT_FINANCE_ARCH under its (student, year, quarter) — an existing archive row for that
    quarter is refreshed, a missing one inserted, the same upsert as the quarter close. A row the archive
    cannot key or hold (received year outside 1990-2100, no quarter, no name or year-month key) is
    reported and not archived; on dev that is 10116's only row (year 20251, amount 0), so on dev nothing
    is archived and the audit trail keeps its copy.
    Left alone on purpose: STUDENT_FINANCE_ARCH's existing rows (the record of what was paid), loans,
    allowances, extensions, received-by, fees, tickets and Wafar deposits (history, decision D5-a), and the
    student row itself.

    Guard: @ExpectedIds lists the students the dry run found. If the set differs when the script is run
    for real, it stops without changing anything, so a student deleted after the review is not swept up
    unseen. Idempotent: a second run finds nothing.

    Dry-run by default: set @Commit = 1 to keep the changes. Sets TenantAll itself.

        sqlcmd -S localhost,1433 -d MOHE -I -f 65001 -b -i cleanup_deleted_student_finance.sql
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Commit BIT = 0;                        /* 1 = keep the changes; 0 = print the counts, roll back */
DECLARE @ExpectedIds NVARCHAR(MAX) = N'10116,10117,10118';  /* comma-separated, ascending; the students the review found */

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @targets TABLE (STUDENT_ID INT PRIMARY KEY);
INSERT INTO @targets (STUDENT_ID)
SELECT sd.STUDENT_ID
FROM MOHECORE.STUDENT_DATA sd
WHERE sd.IS_DELETED = 1
  AND (   EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE f WHERE f.STUDENT_ID = sd.STUDENT_ID)
       OR EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE_PAYMENT p WHERE p.STUDENT_ID = sd.STUDENT_ID)
       OR EXISTS (SELECT 1 FROM STUDENTCORE.student_users u
                  WHERE u.student_id = sd.STUDENT_ID AND u.is_deleted = 0 AND u.status <> 'INACTIVE'));

DECLARE @found NVARCHAR(MAX) = (
    SELECT STUFF((SELECT ',' + CAST(STUDENT_ID AS NVARCHAR(10)) FROM @targets ORDER BY STUDENT_ID
                  FOR XML PATH('')), 1, 1, ''));
PRINT CONCAT('Deleted students with finance / portal leftovers: ', ISNULL(@found, '(none)'));

IF @found IS NULL
BEGIN
    PRINT 'Nothing to do.';
    RETURN;
END

IF ISNULL(@found, '') <> REPLACE(@ExpectedIds, ' ', '')
BEGIN
    PRINT CONCAT('STOPPED - the students found (', @found, ') are not the ones reviewed (', @ExpectedIds,
                 '). Review the new ones and update @ExpectedIds.');
    RETURN;
END

/* Preview: each finance row, and whether it can be archived */
SELECT f.STUDENT_ID, f.STUDENT_FIN_RECIEVED_YEAR AS received_year, f.STUDENT_FIN_RECIEVED_MONTH AS quarter,
       f.STUDENT_FIN_RECIEVED_AMOUNT AS amount,
       CASE WHEN f.STUDENT_FIN_RECIEVED_YEAR BETWEEN 1990 AND 2100   AND f.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4   AND f.STUDENT_FIN_RECIEVED_YEAR_MONTH IS NOT NULL   AND f.STUDENT_FIN_NAME IS NOT NULL THEN 'archive' ELSE 'cannot key the archive - not archived' END AS action,
       CASE WHEN EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE_ARCH a
                          WHERE a.STUDENT_ID = f.STUDENT_ID AND a.STUDENT_FIN_RECIEVED_YEAR = f.STUDENT_FIN_RECIEVED_YEAR
                            AND a.STUDENT_FIN_RECIEVED_MONTH = f.STUDENT_FIN_RECIEVED_MONTH)
            THEN 'refresh' ELSE 'insert' END AS archive_row
FROM MOHECORE.STUDENT_FINANCE f JOIN @targets t ON t.STUDENT_ID = f.STUDENT_ID;

DECLARE @n INT;
BEGIN TRAN;

/* 1. Archive the finance rows that can be archived: refresh the quarter's archive row, or insert it */
UPDATE a SET
       a.STUDENT_FIN_ID = f.STUDENT_FIN_ID,
       a.STUDENT_FIN_NAME = f.STUDENT_FIN_NAME,
       a.STUDENT_FIN_RECIEVED_YEAR_MONTH = f.STUDENT_FIN_RECIEVED_YEAR_MONTH,
       a.STUDENT_FIN_RECIEVED_AMOUNT = f.STUDENT_FIN_RECIEVED_AMOUNT,
       a.STUDY_COUNTRY_IN_ID = f.STUDY_COUNTRY_IN_ID,
       a.STUDY_GOV_IN_ID = f.STUDY_GOV_IN_ID,
       a.STUDY_FINANCE_BANKE_ID = f.STUDY_FINANCE_BANKE_ID,
       a.STUDY_FINANCE_BANK_BRANCH_ID = f.STUDY_FINANCE_BANK_BRANCH_ID,
       a.STUDY_FINANCE_DOF_ID = f.STUDY_FINANCE_DOF_ID,
       a.STUDY_CHECK_ID = f.STUDY_CHECK_ID,
       a.STUDY_CHECK_DATE = f.STUDY_CHECK_DATE,
       a.STUDY_CHECK_DETAILS = f.STUDY_CHECK_DETAILS,
       a.STUDY_CHECK_FORM_NUMBER = f.STUDY_CHECK_FORM_NUMBER,
       a.STUDY_FIN_RECIEVED_STATUS = f.STUDY_FIN_RECIEVED_STATUS,
       a.STUDY_NOTE_1 = f.STUDY_NOTE_1,
       a.STUDY_RECEIVED_WHOM_STATUS_ID = f.STUDY_RECEIVED_WHOM_STATUS_ID,
       a.STUDY_RECEIVED_WHOM_ID = f.STUDY_RECEIVED_WHOM_ID,
       a.STUDY_FINANCE_MIDATE = f.STUDY_FINANCE_MIDATE,
       a.WAFAR_AMOUNT = f.WAFAR_AMOUNT,
       a.WAFAR_TYPE_ID = f.WAFAR_TYPE_ID,
       a.STUDENT_BANK_ID = f.STUDENT_BANK_ID,
       a.STUDENT_ACCOUNT_NUMBER = f.STUDENT_ACCOUNT_NUMBER,
       a.STUDENT_ACCOUNT_ENGLISH_NAME = f.STUDENT_ACCOUNT_ENGLISH_NAME,
       a.STUDENT_ACCOUNT_END_DATE = f.STUDENT_ACCOUNT_END_DATE,
       a.STUDENT_BANK_NOTES = f.STUDENT_BANK_NOTES,
       a.ARCHIEVED_DATE = SYSUTCDATETIME()
FROM MOHECORE.STUDENT_FINANCE_ARCH a
JOIN MOHECORE.STUDENT_FINANCE f
  ON f.STUDENT_ID = a.STUDENT_ID AND f.STUDENT_FIN_RECIEVED_YEAR = a.STUDENT_FIN_RECIEVED_YEAR
 AND f.STUDENT_FIN_RECIEVED_MONTH = a.STUDENT_FIN_RECIEVED_MONTH
JOIN @targets t ON t.STUDENT_ID = f.STUDENT_ID
WHERE f.STUDENT_FIN_RECIEVED_YEAR BETWEEN 1990 AND 2100
  AND f.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4
  AND f.STUDENT_FIN_RECIEVED_YEAR_MONTH IS NOT NULL
  AND f.STUDENT_FIN_NAME IS NOT NULL;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  finance archive rows refreshed');

INSERT INTO MOHECORE.STUDENT_FINANCE_ARCH (STUDENT_ID, STUDENT_FIN_ID, STUDENT_FIN_NAME, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH, STUDENT_FIN_RECIEVED_YEAR_MONTH, STUDENT_FIN_RECIEVED_AMOUNT, STUDY_COUNTRY_IN_ID, STUDY_GOV_IN_ID, STUDY_FINANCE_BANKE_ID, STUDY_FINANCE_BANK_BRANCH_ID, STUDY_FINANCE_DOF_ID, STUDY_CHECK_ID, STUDY_CHECK_DATE, STUDY_CHECK_DETAILS, STUDY_CHECK_FORM_NUMBER, STUDY_FIN_RECIEVED_STATUS, STUDY_NOTE_1, STUDY_RECEIVED_WHOM_STATUS_ID, STUDY_RECEIVED_WHOM_ID, STUDY_FINANCE_MIDATE, WAFAR_AMOUNT, WAFAR_TYPE_ID, STUDENT_BANK_ID, STUDENT_ACCOUNT_NUMBER, STUDENT_ACCOUNT_ENGLISH_NAME, STUDENT_ACCOUNT_END_DATE, STUDENT_BANK_NOTES, MOHE_QUARTER_ID)
SELECT f.STUDENT_ID, f.STUDENT_FIN_ID, f.STUDENT_FIN_NAME, f.STUDENT_FIN_RECIEVED_YEAR, f.STUDENT_FIN_RECIEVED_MONTH, f.STUDENT_FIN_RECIEVED_YEAR_MONTH, f.STUDENT_FIN_RECIEVED_AMOUNT, f.STUDY_COUNTRY_IN_ID, f.STUDY_GOV_IN_ID, f.STUDY_FINANCE_BANKE_ID, f.STUDY_FINANCE_BANK_BRANCH_ID, f.STUDY_FINANCE_DOF_ID, f.STUDY_CHECK_ID, f.STUDY_CHECK_DATE, f.STUDY_CHECK_DETAILS, f.STUDY_CHECK_FORM_NUMBER, f.STUDY_FIN_RECIEVED_STATUS, f.STUDY_NOTE_1, f.STUDY_RECEIVED_WHOM_STATUS_ID, f.STUDY_RECEIVED_WHOM_ID, f.STUDY_FINANCE_MIDATE, f.WAFAR_AMOUNT, f.WAFAR_TYPE_ID, f.STUDENT_BANK_ID, f.STUDENT_ACCOUNT_NUMBER, f.STUDENT_ACCOUNT_ENGLISH_NAME, f.STUDENT_ACCOUNT_END_DATE, f.STUDENT_BANK_NOTES, f.MOHE_QUARTER_ID
FROM MOHECORE.STUDENT_FINANCE f
JOIN @targets t ON t.STUDENT_ID = f.STUDENT_ID
WHERE f.STUDENT_FIN_RECIEVED_YEAR BETWEEN 1990 AND 2100
  AND f.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4
  AND f.STUDENT_FIN_RECIEVED_YEAR_MONTH IS NOT NULL
  AND f.STUDENT_FIN_NAME IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE_ARCH a
                   WHERE a.STUDENT_ID = f.STUDENT_ID AND a.STUDENT_FIN_RECIEVED_YEAR = f.STUDENT_FIN_RECIEVED_YEAR
                     AND a.STUDENT_FIN_RECIEVED_MONTH = f.STUDENT_FIN_RECIEVED_MONTH);
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  finance archive rows inserted');

/* 2. Then remove the live rows */

DELETE p FROM MOHECORE.STUDENT_FINANCE_PAYMENT p JOIN @targets t ON t.STUDENT_ID = p.STUDENT_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  STUDENT_FINANCE_PAYMENT rows deleted');

DELETE f FROM MOHECORE.STUDENT_FINANCE f JOIN @targets t ON t.STUDENT_ID = f.STUDENT_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  STUDENT_FINANCE rows deleted');

DELETE s FROM STUDENTCORE.student_sessions s JOIN @targets t ON t.STUDENT_ID = s.student_id;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  portal sessions deleted');

UPDATE u SET u.status = 'INACTIVE', u.updated_at = SYSUTCDATETIME()
FROM STUDENTCORE.student_users u JOIN @targets t ON t.STUDENT_ID = u.student_id
WHERE u.is_deleted = 0 AND u.status <> 'INACTIVE';
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  portal accounts set INACTIVE');

/* What stays, as history */
SELECT t.STUDENT_ID,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE_ARCH a WHERE a.STUDENT_ID = t.STUDENT_ID) AS finance_archive_rows_kept
FROM @targets t;

IF @Commit = 1
BEGIN
    COMMIT;
    PRINT 'COMMITTED';
END
ELSE
BEGIN
    ROLLBACK;
    PRINT 'ROLLED BACK (dry run) - set @Commit = 1 to apply';
END
