/*
 * Read-only probes for the report data audit (docs/report-data-audit.md).
 *
 * The static audit (scripts/audit_report_data.py) can prove that every template
 * field has a supplying bean property and that no template prints a bare foreign
 * key. What it cannot see is data-shaped emptiness: a column that exists but is
 * NULL for most rows, an English lookup name that was never filled in, or a view
 * whose INNER JOIN silently drops students.
 *
 * Nothing here writes. Run against the MOHE database and record the counts in
 * the audit document.
 */

SET NOCOUNT ON;

/* ─────────────────────────────────────────────────────────────────────────────
 * 1. English-name coverage per legacy lookup.
 *
 * Reports accept ?lang=en, but the legacy lookups carry NAME + NAME_EN and the
 * EN column is only partially populated. Every row counted here renders as
 * Arabic text inside an otherwise-English report.
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '=== English-name gaps per lookup (missing_en > 0 means EN reports fall back to Arabic) ===';

WITH LOOKUPS AS (
    SELECT 'STUDENT_COUNTRY'         AS TBL, 'STUDENT_COUNTRY_NAME'         AS AR, 'STUDENT_COUNTRY_NAME_EN'         AS EN
    UNION ALL SELECT 'STUDENT_GOV',            'STUDENT_GOV_NAME',            'STUDENT_GOV_NAME_EN'
    UNION ALL SELECT 'STUDENT_GOV_MUD',        'STUDENT_MUD_NAME',            'STUDENT_MUD_NAME_EN'
    UNION ALL SELECT 'STUDENT_DEGREE',         'STUDENT_DEGREE_NAME',         'STUDENT_DEGREE_NAME_EN'
    UNION ALL SELECT 'STUDENT_MAJOR',          'STUDENT_MAJOR_NAME',          'STUDENT_MAJOR_NAME_EN'
    UNION ALL SELECT 'STUDENT_CURRENT_LEVEL',  'STUDENT_CURRENT_LEVEL_NAME',  'STUDENT_CURRENT_LEVEL_NAME_EN'
    UNION ALL SELECT 'STUDENT_STUDY_TYPE',     'STUDENT_STUDY_TYPE_NAME',     'STUDENT_STUDY_TYPE_NAME_EN'
    UNION ALL SELECT 'STUDENT_STUDY_STATUS',   'STUDENT_STUDY_STATUS_NAME',   'STUDENT_STUDY_STATUS_NAME_EN'
    UNION ALL SELECT 'STUDENT_GENDER',         'STUDENT_GENDER_NAME',         'STUDENT_GENDER_NAME_EN'
    UNION ALL SELECT 'STUDENT_AGENCY_TYPE',    'STUDENT_AGENCY_TYPE_NAME',    'STUDENT_AGENCY_TYPE_NAME_EN'
    UNION ALL SELECT 'STUDENT_MINISTRY_TYPE',  'STUDENT_MINISTRY_TYPE_NAME',  'STUDENT_MINISTRY_TYPE_NAME_EN'
    UNION ALL SELECT 'STUDENT_UNIVERSITY_TYPE','STUDENT_UNIVERSITY_TYPE_NAME','STUDENT_UNIVERSITY_TYPE_NAME_EN'
    UNION ALL SELECT 'STUDY_COUNTRY_IN',       'STUDY_COUNTRY_IN_NAME',       'STUDY_COUNTRY_IN_NAME_EN'
    UNION ALL SELECT 'STUDY_GOV_IN',           'STUDY_GOV_IN_NAME',           'STUDY_GOV_IN_NAME_EN'
    UNION ALL SELECT 'STUDY_UNIVERSITY_IN',    'STUDY_UNIVERSITY_IN_NAME',    'STUDY_UNIVERSITY_IN_NAME_EN'
    UNION ALL SELECT 'STUDY_COLLEGE_IN',       'STUDY_COLLEGE_IN_NAME',       'STUDY_COLLEGE_IN_NAME_EN'
    UNION ALL SELECT 'STUDY_EDUCTION_IN',      'STUDY_EDUCTION_NAME',         'STUDY_EDUCTION_NAME_EN'
    UNION ALL SELECT 'STUDY_MAJOR_IN',         'STUDY_MAJOR_NAME',            'STUDY_MAJOR_NAME_EN'
    UNION ALL SELECT 'STUDY_CURRENCY_IN',      'STUDY_CURRENCY_IN_NAME',      'STUDY_CURRENCY_IN_NAME_EN'
    UNION ALL SELECT 'STUDY_FINANCE_BANK',     'STUDY_FINANCE_BANKE_NAME',    'STUDY_FINANCE_BANKE_NAME_EN'
    UNION ALL SELECT 'STUDY_WAFAR_TYPE',       'WAFAR_NAME',                  'WAFAR_NAME_EN'
)
SELECT TBL AS lookup_table, AR AS ar_column, EN AS en_column,
       'SELECT ''' + TBL + ''' AS lookup_table, COUNT(*) AS total, '
       + 'SUM(CASE WHEN NULLIF(LTRIM(RTRIM(' + EN + ')), '''') IS NULL THEN 1 ELSE 0 END) AS missing_en '
       + 'FROM MOHECORE.' + TBL AS run_this
FROM LOOKUPS
ORDER BY TBL;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 2. Report views: row count and NULL density.
 *
 * A view that returns far fewer rows than STUDENT_DATA has an INNER JOIN to a
 * lookup somewhere and is dropping students whose lookup value is unset — the
 * "students missing from the report" complaint.
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== Report view row counts vs. the live student count ===';

DECLARE @students INT =
    (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE IS_DELETED = 0 OR IS_DELETED IS NULL);
PRINT 'non-deleted STUDENT_DATA rows: ' + CAST(@students AS VARCHAR(20));

SELECT s.name + '.' + v.name AS view_name,
       'SELECT ''' + v.name + ''' AS view_name, COUNT(*) AS rows FROM '
       + s.name + '.' + v.name AS run_this
FROM sys.views v
JOIN sys.schemas s ON s.schema_id = v.schema_id
WHERE v.name LIKE 'V[_]%' OR v.name LIKE 'VW[_]%' OR v.name LIKE 'Q[_]%'
ORDER BY s.name, v.name;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 3. View definitions.
 *
 * The view bodies live only in the database (no DDL in source control), so this
 * is the starting point for adding the missing *_NAME_EN columns described in
 * docs/report-data-audit.md. Save the output before editing any view.
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== View definitions (save this output before altering any view) ===';

SELECT s.name AS schema_name, v.name AS view_name, m.definition
FROM sys.views v
JOIN sys.schemas s   ON s.schema_id = v.schema_id
JOIN sys.sql_modules m ON m.object_id = v.object_id
WHERE v.name LIKE 'V[_]%' OR v.name LIKE 'VW[_]%' OR v.name LIKE 'Q[_]%'
ORDER BY s.name, v.name;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 4. Views that reference a lookup table with an INNER (non-LEFT) join.
 *
 * Text search over the definitions, so treat it as a shortlist to read by hand
 * rather than a verdict.
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== Views whose definition contains a plain JOIN (candidate row-dropping) ===';

SELECT s.name AS schema_name, v.name AS view_name
FROM sys.views v
JOIN sys.schemas s     ON s.schema_id = v.schema_id
JOIN sys.sql_modules m ON m.object_id = v.object_id
WHERE (v.name LIKE 'V[_]%' OR v.name LIKE 'VW[_]%' OR v.name LIKE 'Q[_]%')
  AND m.definition LIKE '%JOIN%'
  AND m.definition NOT LIKE '%LEFT JOIN%'
  AND m.definition NOT LIKE '%LEFT OUTER JOIN%'
ORDER BY s.name, v.name;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 5. Spot-check the two views added by V016.
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== V016 views: presence and English-fallback behaviour ===';

IF OBJECT_ID('MOHECORE.V_STUDENT_PROFILE_DOC', 'V') IS NULL
    PRINT 'MISSING: MOHECORE.V_STUDENT_PROFILE_DOC (apply V016 first)';
ELSE
    SELECT TOP 20 STUDENT_ID, STUDENT_NAME,
                  DEGREE_NAME_AR, DEGREE_NAME_EN,
                  STUDY_COUNTRY_NAME_AR, STUDY_COUNTRY_NAME_EN,
                  STUDY_UNIVERSITY_NAME_AR, STUDY_UNIVERSITY_NAME_EN
    FROM MOHECORE.V_STUDENT_PROFILE_DOC
    ORDER BY STUDENT_ID;

IF OBJECT_ID('MOHECORE.V_STUDENT_FINANCIAL_STATEMENT', 'V') IS NULL
    PRINT 'MISSING: MOHECORE.V_STUDENT_FINANCIAL_STATEMENT (apply V016 first)';
ELSE
    SELECT TOP 20 ROW_KEY, SOURCE_TYPE, STUDENT_ID, MOHE_QUARTER_ID,
                  RECEIVED_AMOUNT, WAFAR_AMOUNT, NET_AMOUNT,
                  BANK_NAME_AR, BANK_NAME_EN
    FROM MOHECORE.V_STUDENT_FINANCIAL_STATEMENT
    ORDER BY STUDENT_ID, MOHE_QUARTER_ID;
GO

/* A student must never appear twice for one quarter (double-counted totals). */
PRINT '';
PRINT '=== V016 statement view: duplicate student+quarter rows (expect none) ===';
IF OBJECT_ID('MOHECORE.V_STUDENT_FINANCIAL_STATEMENT', 'V') IS NOT NULL
    SELECT STUDENT_ID, MOHE_QUARTER_ID, COUNT(*) AS row_count
    FROM MOHECORE.V_STUDENT_FINANCIAL_STATEMENT
    GROUP BY STUDENT_ID, MOHE_QUARTER_ID
    HAVING COUNT(*) > 1
    ORDER BY STUDENT_ID, MOHE_QUARTER_ID;
GO
