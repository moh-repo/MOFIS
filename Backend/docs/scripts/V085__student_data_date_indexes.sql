/*
    V085 — indexes on the STUDENT_DATA date columns the code filters on.

    Found in the September 2026 performance pass, same method as V059: reading
    the repositories against the scripts.

    1. STUDENT_PASSPORT_EXPIRE_DATE
       StudentDataRepository.findWithPassportExpiringBetween and
       findWithPassportExpiredBefore drive two nightly alert-event scenarios
       (AlertEventDetectionService, 01:00 cron). Both filter on this column;
       with no index each run scans STUDENT_DATA in full.

    2. STUDENT_MIDATE
       StudentDataRepository.findStudentCountsByMonthSinceScoped and
       countByStudentMidateAfter feed the dashboard's registration-trend
       widgets, per request. Same situation: range filters, no index.

    V059 indexed the finance tables' MOHE_QUARTER_ID and the archive's
    STUDY_COUNTRY_IN; STUDENT_DATA's own date columns were not looked at then.

    Idempotent. Needs QUOTED_IDENTIFIER ON (sqlcmd: -I).
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ── 1. STUDENT_DATA.STUDENT_PASSPORT_EXPIRE_DATE ─────────────────────────── */
IF COL_LENGTH('MOHECORE.STUDENT_DATA', 'STUDENT_PASSPORT_EXPIRE_DATE') IS NULL
    PRINT 'V085: STUDENT_DATA.STUDENT_PASSPORT_EXPIRE_DATE absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA')
                  AND name = 'IX_STUDENT_DATA_PASSPORT_EXPIRE')
    PRINT 'V085: IX_STUDENT_DATA_PASSPORT_EXPIRE already exists';
ELSE
BEGIN
    CREATE NONCLUSTERED INDEX IX_STUDENT_DATA_PASSPORT_EXPIRE
        ON MOHECORE.STUDENT_DATA (STUDENT_PASSPORT_EXPIRE_DATE)
        INCLUDE (STUDY_COUNTRY_IN);
    PRINT 'V085: created IX_STUDENT_DATA_PASSPORT_EXPIRE';
END
GO

/* ── 2. STUDENT_DATA.STUDENT_MIDATE ───────────────────────────────────────── */
IF COL_LENGTH('MOHECORE.STUDENT_DATA', 'STUDENT_MIDATE') IS NULL
    PRINT 'V085: STUDENT_DATA.STUDENT_MIDATE absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA')
                  AND name = 'IX_STUDENT_DATA_MIDATE')
    PRINT 'V085: IX_STUDENT_DATA_MIDATE already exists';
ELSE
BEGIN
    CREATE NONCLUSTERED INDEX IX_STUDENT_DATA_MIDATE
        ON MOHECORE.STUDENT_DATA (STUDENT_MIDATE)
        INCLUDE (STUDY_COUNTRY_IN);
    PRINT 'V085: created IX_STUDENT_DATA_MIDATE';
END
GO
