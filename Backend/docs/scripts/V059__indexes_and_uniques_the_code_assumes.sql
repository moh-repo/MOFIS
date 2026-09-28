/*
    V059 — the indexes and unique constraints the application already relies on.

    Two kinds of gap, both found by reading the repositories against the scripts
    in the August 2026 revision.

    1. Join and filter columns with no index
       MOHE_QUARTER_ID is the join key of four native queries in
       FiscalYearReportRepository and the quarter filter on every finance page,
       and is indexed on STUDENT_FINANCE_PAYMENT only (IX_SFP_QUARTER). Added on
       STUDENT_FINANCE, STUDENT_FINANCE_ARCH, STUDENT_LOAN and
       STUDENT_BOOKS_ALLOWANCE.
       STUDENT_DATA_ARCH.STUDY_COUNTRY_IN is the row-level-security predicate
       column of the archive (V031/V032/V037) and the country filter of the
       student-history page; V029 indexed every STUDY_COUNTRY_IN_ID column and
       said so, but the archive's column is spelt without the _ID and was
       missed. STUDENT_DATA's own is indexed already.
       NOTIFICATION_LOG_COUNTRY (V046) is joined on LOG_ID from the notification
       log; its primary key leads with LOG_ID, so nothing to add there — noted
       so nobody looks again.

    2. Optional-returning finders on columns with no unique constraint
       ReportTypeSettingsRepository.findByReportTypeCode and
       LuReportLabelRepository.findByCode return Optional; a second row with the
       same code makes them throw NonUniqueResultException on every report.
       V017/V018/V020/V050/V057 all key their "insert if absent" on
       REPORT_TYPE_CODE, so the constraint only states what the scripts assume.
       (NOTIFICATION_SETTINGS.COUNTRY_ID already has UX_NOTIFICATION_SETTINGS_COUNTRY.)

    A duplicate that already exists is *reported*, not resolved: the script
    prints the offending codes and skips that constraint, because choosing which
    of two settings rows to keep is a person's call.

    Idempotent. Needs QUOTED_IDENTIFIER ON (sqlcmd: -I).
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ── 1. MOHE_QUARTER_ID on the four finance tables ────────────────────────── */
DECLARE @t TABLE (tbl SYSNAME, idx SYSNAME, lead_col SYSNAME, include_cols NVARCHAR(200));
INSERT INTO @t VALUES
    ('STUDENT_FINANCE',        'IX_STUDENT_FINANCE_QUARTER',        'MOHE_QUARTER_ID', 'STUDENT_ID'),
    ('STUDENT_FINANCE_ARCH',   'IX_STUDENT_FINANCE_ARCH_QUARTER',   'MOHE_QUARTER_ID', 'STUDENT_ID'),
    ('STUDENT_LOAN',           'IX_STUDENT_LOAN_QUARTER',           'MOHE_QUARTER_ID', 'STUDENT_ID'),
    ('STUDENT_BOOKS_ALLOWANCE','IX_STUDENT_BOOKS_ALLOWANCE_QUARTER','MOHE_QUARTER_ID', 'STUDENT_ID');

DECLARE @tbl SYSNAME, @idx SYSNAME, @col SYSNAME, @inc NVARCHAR(200), @sql NVARCHAR(MAX);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT tbl, idx, lead_col, include_cols FROM @t;
OPEN c;
FETCH NEXT FROM c INTO @tbl, @idx, @col, @inc;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID('MOHECORE.' + @tbl, 'U') IS NULL
        PRINT 'V059: MOHECORE.' + @tbl + ' absent — skipped';
    ELSE IF COL_LENGTH('MOHECORE.' + @tbl, @col) IS NULL
        PRINT 'V059: MOHECORE.' + @tbl + '.' + @col + ' absent — skipped';
    ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('MOHECORE.' + @tbl) AND name = @idx)
        PRINT 'V059: ' + @idx + ' already exists';
    ELSE
    BEGIN
        SET @sql = N'CREATE NONCLUSTERED INDEX ' + QUOTENAME(@idx) + N' ON MOHECORE.' + QUOTENAME(@tbl)
                 + N' (' + QUOTENAME(@col) + N')'
                 + CASE WHEN COL_LENGTH('MOHECORE.' + @tbl, @inc) IS NULL THEN N'' ELSE N' INCLUDE (' + QUOTENAME(@inc) + N')' END + N';';
        EXEC sp_executesql @sql;
        PRINT 'V059: created ' + @idx;
    END
    FETCH NEXT FROM c INTO @tbl, @idx, @col, @inc;
END
CLOSE c; DEALLOCATE c;
GO

/* ── 1b. STUDENT_DATA_ARCH.STUDY_COUNTRY_IN ───────────────────────────────── */
IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'STUDY_COUNTRY_IN') IS NULL
    PRINT 'V059: STUDENT_DATA_ARCH.STUDY_COUNTRY_IN absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_ARCH') AND name = 'IX_STUDENT_DATA_ARCH_STUDY_COUNTRY_IN')
    PRINT 'V059: IX_STUDENT_DATA_ARCH_STUDY_COUNTRY_IN already exists';
ELSE
BEGIN
    CREATE NONCLUSTERED INDEX IX_STUDENT_DATA_ARCH_STUDY_COUNTRY_IN
        ON MOHECORE.STUDENT_DATA_ARCH (STUDY_COUNTRY_IN) INCLUDE (STUDENT_ID);
    PRINT 'V059: created IX_STUDENT_DATA_ARCH_STUDY_COUNTRY_IN';
END
GO

/* ── 2. Unique codes ──────────────────────────────────────────────────────── */
/* REPORT_TYPE_SETTINGS.REPORT_TYPE_CODE */
IF OBJECT_ID('MOHECORE.REPORT_TYPE_SETTINGS', 'U') IS NULL
    PRINT 'V059: REPORT_TYPE_SETTINGS absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('MOHECORE.REPORT_TYPE_SETTINGS') AND name = 'UX_REPORT_TYPE_SETTINGS_CODE')
    PRINT 'V059: UX_REPORT_TYPE_SETTINGS_CODE already exists';
ELSE IF EXISTS (SELECT REPORT_TYPE_CODE FROM MOHECORE.REPORT_TYPE_SETTINGS GROUP BY REPORT_TYPE_CODE HAVING COUNT(*) > 1)
BEGIN
    PRINT 'V059: REPORT_TYPE_SETTINGS has duplicate REPORT_TYPE_CODE values — resolve these by hand, then re-run:';
    SELECT REPORT_TYPE_CODE, COUNT(*) AS rows_ FROM MOHECORE.REPORT_TYPE_SETTINGS GROUP BY REPORT_TYPE_CODE HAVING COUNT(*) > 1;
END
ELSE
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_REPORT_TYPE_SETTINGS_CODE ON MOHECORE.REPORT_TYPE_SETTINGS (REPORT_TYPE_CODE);
    PRINT 'V059: created UX_REPORT_TYPE_SETTINGS_CODE';
END
GO

/* LU_REPORT_LABEL.CODE — the one V003/V004 lookup that V007 did not make unique */
IF OBJECT_ID('MOHECORE.LU_REPORT_LABEL', 'U') IS NULL
    PRINT 'V059: LU_REPORT_LABEL absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('MOHECORE.LU_REPORT_LABEL') AND name = 'UQ_LU_REPORT_LABEL_CODE')
    PRINT 'V059: UQ_LU_REPORT_LABEL_CODE already exists';
ELSE IF EXISTS (SELECT CODE FROM MOHECORE.LU_REPORT_LABEL GROUP BY CODE HAVING COUNT(*) > 1)
BEGIN
    PRINT 'V059: LU_REPORT_LABEL has duplicate CODE values — resolve these by hand, then re-run:';
    SELECT CODE, COUNT(*) AS rows_ FROM MOHECORE.LU_REPORT_LABEL GROUP BY CODE HAVING COUNT(*) > 1;
END
ELSE
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UQ_LU_REPORT_LABEL_CODE ON MOHECORE.LU_REPORT_LABEL (CODE);
    PRINT 'V059: created UQ_LU_REPORT_LABEL_CODE';
END
GO

/* ── Verification ─────────────────────────────────────────────────────────── */
SELECT name AS index_name, OBJECT_NAME(object_id) AS table_name, is_unique
  FROM sys.indexes
 WHERE name IN ('IX_STUDENT_FINANCE_QUARTER', 'IX_STUDENT_FINANCE_ARCH_QUARTER', 'IX_STUDENT_LOAN_QUARTER',
                'IX_STUDENT_BOOKS_ALLOWANCE_QUARTER', 'IX_STUDENT_DATA_ARCH_STUDY_COUNTRY_IN',
                'UX_REPORT_TYPE_SETTINGS_CODE', 'UQ_LU_REPORT_LABEL_CODE')
 ORDER BY table_name, name;
GO
