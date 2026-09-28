/*
 * Add STUDY_CURRENCY_IN_ID to STUDENT_FINANCE.
 *
 * The finance screen previously rendered every amount as USD, hardcoded in the
 * Angular templates, even though the row carried no currency at all. This column
 * holds the currency as an FK-by-convention into MOHECORE.STUDY_CURRENCY_IN
 * (nullable, no DB-level constraint — matches every other finance lookup column
 * in this schema).
 *
 * Idempotent — safe to re-run.
 */

IF COL_LENGTH('MOHECORE.STUDENT_FINANCE', 'STUDY_CURRENCY_IN_ID') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_FINANCE ADD STUDY_CURRENCY_IN_ID INT NULL;
END
GO
