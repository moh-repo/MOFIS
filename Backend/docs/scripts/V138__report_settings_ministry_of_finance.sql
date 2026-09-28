/*
    V138 — the ministry named on every printed report (live QA M1, 24 Sep 2026).

    The masthead, footer and signatory lines of every PDF come from
    MOHECORE.REPORT_TYPE_SETTINGS. The seed scripts that fill that table (V017,
    V050, V057, V111) were reworded to "Ministry of Finance" by the rename of
    24 Sep 2026, but they sit below the Flyway baseline (96) and never run
    again, so an existing database still prints the earlier name: 48 report
    types carry "Ministry of Higher Education" / "وزارة التعليم العالي" in
    TITLE_01, the quarterly financial form carries the long form
    "Ministry of Higher Education and Scientific Research" in TITLE_02 and
    "Minister of Higher Education and Scientific Research" as its fifth
    signatory — under a top bar and footer that already read
    "Ministry of Finance".

    MOHECORE.SYS_SETTINGS is brought to the same name. V136 (23 Sep) set it to
    "Ministry of Higher Education" and stays as it ran; a database that ran
    V136 and was not corrected on the screen since is corrected here, and one
    already reading "Ministry of Finance" is left alone.

    Every text column of REPORT_TYPE_SETTINGS is rewritten with REPLACE,
    longest phrase first, so a title an administrator has extended keeps its
    extension, and a row without the old name is not touched (UPDATED_AT is
    left as it is: this script is the record of the change). Rows that still
    contain the phrase afterwards are counted and printed, never failed on.

    Idempotent. Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF OBJECT_ID(N'MOHECORE.SYS_SETTINGS', N'U') IS NOT NULL
BEGIN
    UPDATE MOHECORE.SYS_SETTINGS
       SET SYS_NAME_EN = 'Ministry of Finance'
     WHERE LTRIM(RTRIM(SYS_NAME_EN)) = 'Ministry of Higher Education';
    PRINT 'V138: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' English system name(s) corrected';

    UPDATE MOHECORE.SYS_SETTINGS
       SET SYS_NAME_AR = N'وزارة المالية'
     WHERE LTRIM(RTRIM(SYS_NAME_AR)) = N'وزارة التعليم العالي';
    PRINT 'V138: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' Arabic system name(s) corrected';
END

IF OBJECT_ID(N'MOHECORE.REPORT_TYPE_SETTINGS', N'U') IS NULL
BEGIN
    PRINT 'V138: MOHECORE.REPORT_TYPE_SETTINGS absent — nothing to do';
    RETURN;
END

/* One UPDATE per text column, built from the catalogue so a column added later
   (TITLE_06, a sixth signatory) is covered by a re-run without an edit here. */
DECLARE @sql NVARCHAR(MAX) = N'';

SELECT @sql = @sql + N'
UPDATE MOHECORE.REPORT_TYPE_SETTINGS
   SET ' + QUOTENAME(c.COLUMN_NAME) + N' =
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(' + QUOTENAME(c.COLUMN_NAME) + N',
           N''Ministry of Higher Education and Scientific Research'', N''Ministry of Finance''),
           N''Minister of Higher Education and Scientific Research'', N''Minister of Finance''),
           N''Ministry of Higher Education'', N''Ministry of Finance''),
           N''Minister of Higher Education'', N''Minister of Finance''),
           N''وزارة التعليم العالي والبحث العلمي'', N''وزارة المالية''),
           N''وزير التعليم العالي والبحث العلمي'', N''وزير المالية''),
           N''وزارة التعليم العالي'', N''وزارة المالية''),
           N''وزير التعليم العالي'', N''وزير المالية'')
 WHERE ' + QUOTENAME(c.COLUMN_NAME) + N' LIKE N''%Higher Education%''
    OR ' + QUOTENAME(c.COLUMN_NAME) + N' LIKE N''%التعليم العالي%'';
PRINT ''V138: '' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + '' value(s) corrected in ' + c.COLUMN_NAME + N''';'
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE c.TABLE_SCHEMA = N'MOHECORE'
   AND c.TABLE_NAME   = N'REPORT_TYPE_SETTINGS'
   AND c.DATA_TYPE IN (N'varchar', N'nvarchar')
   AND c.COLUMN_NAME <> N'REPORT_TYPE_CODE'
 ORDER BY c.ORDINAL_POSITION;

EXEC sp_executesql @sql;

/* What is left: a phrase this script does not know (printed, not failed on). */
DECLARE @left NVARCHAR(MAX) = N'';
SELECT @left = @left + N' OR ' + QUOTENAME(c.COLUMN_NAME) + N' LIKE N''%Higher Education%'''
                     + N' OR ' + QUOTENAME(c.COLUMN_NAME) + N' LIKE N''%التعليم العالي%'''
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE c.TABLE_SCHEMA = N'MOHECORE'
   AND c.TABLE_NAME   = N'REPORT_TYPE_SETTINGS'
   AND c.DATA_TYPE IN (N'varchar', N'nvarchar')
   AND c.COLUMN_NAME <> N'REPORT_TYPE_CODE';

SET @left = N'DECLARE @n INT;
SELECT @n = COUNT(*) FROM MOHECORE.REPORT_TYPE_SETTINGS WHERE 1 = 0' + @left + N';
PRINT ''V138: '' + CAST(@n AS NVARCHAR(10)) + '' report type(s) still name Higher Education'';';

EXEC sp_executesql @left;
