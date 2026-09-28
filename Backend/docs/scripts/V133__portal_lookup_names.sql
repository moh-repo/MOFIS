/*
    V133 — the names the student portal reads, in both languages, and one
    misspelling (QA SP-004, SP-006; live retest M7, M10).

    1. MOHECORE.STUDENT_AGENCY_TYPE
       Four of the six legacy rows have no English name, and row 5
       'التعليم العالي' is the one 762 of the 984 students point at — so My Data
       in English printed Arabic under "Agency type". The rows are matched by
       their Arabic name, as V112 matched study countries, and a row somebody
       has already named in English is left alone.

    2. MOHECORE.STUDY_UNIVERSITY_IN
       Row 999 reads 'عير مبين', a slip of the pen for 'غير مبين' ("not
       specified"). It is the placeholder every student without a university
       is on, so the slip was on My Data in Arabic. The retest guide of 23 Sep
       said it had been corrected; that was by hand, on one database. A
       migration reaches every environment.

    Idempotent: both statements touch only rows still in the state described.
    Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. English names for the agency types that have none.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE a
   SET a.STUDENT_AGENCY_TYPE_NAME_EN = v.NameEn
  FROM MOHECORE.STUDENT_AGENCY_TYPE a
  JOIN (VALUES
        (N'جامعة',          N'University'),
        (N'جامعية',         N'University-level'),
        (N'دراسات عليا',    N'Postgraduate studies'),
        (N'وزارة',          N'Ministry'),
        (N'التعليم العالي', N'Higher Education'),
        (N'غير مبين',       N'Not Specified')
       ) v (NameAr, NameEn) ON LTRIM(RTRIM(a.STUDENT_AGENCY_TYPE_NAME)) = v.NameAr
 WHERE a.STUDENT_AGENCY_TYPE_NAME_EN IS NULL
    OR LTRIM(RTRIM(a.STUDENT_AGENCY_TYPE_NAME_EN)) = N''
    OR a.STUDENT_AGENCY_TYPE_NAME_EN = a.STUDENT_AGENCY_TYPE_NAME;

PRINT 'V133: agency types named in English: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. The misspelt placeholder university.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE MOHECORE.STUDY_UNIVERSITY_IN
   SET STUDY_UNIVERSITY_IN_NAME = N'غير مبين'
 WHERE LTRIM(RTRIM(STUDY_UNIVERSITY_IN_NAME)) = N'عير مبين';

PRINT 'V133: universities respelt: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

-- What is still without an English name, for the lookup screen to complete.
DECLARE @left INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_AGENCY_TYPE
                      WHERE ISNULL(IS_ACTIVE, 1) = 1
                        AND (STUDENT_AGENCY_TYPE_NAME_EN IS NULL OR LTRIM(RTRIM(STUDENT_AGENCY_TYPE_NAME_EN)) = N''));
PRINT 'V133: active agency types still without an English name: ' + CAST(@left AS NVARCHAR(10));
