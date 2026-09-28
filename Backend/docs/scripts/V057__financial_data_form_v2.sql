/*
    V057 — Financial Data Form V2 ("كشوفات المساعدة المالية الربعية")
    ====================================================================

    A second financial data form, beside the existing one, laid out like the
    ministry's own quarterly statement: one row per student with the sponsoring
    entity, delegation year, decision number, study duration, current level,
    first payment quarter, amount, a signature cell and the notes.

    Two things, both idempotent:

    1. MOHECORE.Q_Stud_Financial_Data_Form_V2 — the read view behind the
       StudentFinancialDataFormV2 entity. It differs from
       Q_Stud_Financial_Data_Form in what the printed form needs:
         - ENTITY_NAME resolves university type -> ministry type -> agency
           type, the same fallback the quarterly financial report uses
           (TYPE_ALL_IS), so "الجهة" reads "جامعة عدن" / "وزارة الدفاع" /
           "بعثة" rather than the mostly-blank ministry type on its own.
         - CURRENT_LEVEL_LABEL prints the level as the number the form shows
           ("7") when it is a study year, and the level name otherwise
           ("تمديد نظامي"). The level name in both languages is kept too.
         - STUDY_COUNTRY_IN_ID is exposed, so country scoping is by id rather
           than by resolving names.
         - The NTEXT notes are cast to NVARCHAR(MAX) so they compare and
           sort without the NTEXT restrictions.
         - STUDENT_FINANCE is an inner join: the form is per quarter and a
           student without a finance row has no quarter to appear under.
       Reads STUDENT_DATA and STUDENT_FINANCE; changes nothing in either.

    2. A MOHECORE.REPORT_TYPE_SETTINGS row, FINANCIAL_DATA_FORM_V2, carrying
       the statutory masthead (الجمهورية اليمنية / الوزارة / الإدارة العامة
       للبعثات - إدارة مالية البعثات), the report name and the five signatory
       POSITIONS the form prints. Names are deliberately left blank — they are
       the current office holders and are entered from /settings/report-settings,
       as V020 does for the quarterly report. Only blank columns are filled on
       re-run, so an administrator's edits are never overwritten.

    Verification prints are self-evidencing: the view's row count for the
    populated quarter and the settings row's state.
*/

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
GO

PRINT '=== V057: financial data form v2 ===';
GO

/* ── 1. Read view ────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.Q_Stud_Financial_Data_Form_V2 AS
SELECT
    sd.STUDENT_ID,
    ISNULL(sd.STUDENT_NAME, N'')                                   AS STUDENT_NAME,
    -- الجهة: university -> ministry -> agency, never NULL
    COALESCE(NULLIF(LTRIM(RTRIM(sut.STUDENT_UNIVERSITY_TYPE_NAME)), N''),
             NULLIF(LTRIM(RTRIM(smt.STUDENT_MINISTRY_TYPE_NAME)),   N''),
             NULLIF(LTRIM(RTRIM(sat.STUDENT_AGENCY_TYPE_NAME)),     N''),
             N'')                                                  AS ENTITY_NAME,
    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'')                      AS STUDENT_AGENCY_TYPE_NAME,
    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'')                    AS STUDENT_MINISTRY_TYPE_NAME,
    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'')                  AS STUDENT_UNIVERSITY_TYPE_NAME,
    sf.STUDY_COUNTRY_IN_ID,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'')                         AS STUDY_COUNTRY_IN_NAME,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'')                           AS STUDENT_COUNTRY_NAME,
    sd.STUDENT_DELEGATION_DECISION_YEAR,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'')                          AS STUDENT_DEGREE_NAME,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'')                             AS STUDENT_MAJOR_NAME,
    sd.STUDENT_DELEGATE_DECISION_NO,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'')                    AS CURRENT_LEVEL_NAME,
    ISNULL(NULLIF(scl.STUDENT_CURRENT_LEVEL_NAME_EN, ''),
           scl.STUDENT_CURRENT_LEVEL_NAME)                         AS CURRENT_LEVEL_NAME_EN,
    scl.STUDENT_CURRENT_LEVEL_ORDER_NO                             AS CURRENT_LEVEL_ORDER_NO,
    -- "المستوى الحالي" as the form prints it: the year number, else the level name
    CASE WHEN scl.STUDENT_CURRENT_LEVEL_ORDER_NO IS NOT NULL
          AND (scl.STUDENT_CURRENT_LEVEL_NAME_EN LIKE '%Year%'
               OR scl.STUDENT_CURRENT_LEVEL_NAME LIKE N'السنة%')
         THEN CAST(scl.STUDENT_CURRENT_LEVEL_ORDER_NO AS NVARCHAR(10))
         ELSE ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') END      AS CURRENT_LEVEL_LABEL,
    sd.STUDENT_FIRST_RECEIPT_OF_DUE,
    sd.STUDENT_AMOUNT,
    sf.STUDENT_FIN_RECIEVED_AMOUNT,
    CAST(sd.STUDENT_NOTE_1 AS NVARCHAR(MAX))                       AS STUDENT_NOTE_1,
    CAST(sd.STUDENT_NOTE_2 AS NVARCHAR(MAX))                       AS STUDENT_NOTE_2,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'')                     AS STUDENT_STUDY_STATUS_NAME,
    sf.STUDENT_FIN_RECIEVED_YEAR,
    sf.STUDENT_FIN_RECIEVED_MONTH,
    sf.MOHE_QUARTER_ID
FROM MOHECORE.STUDENT_DATA sd
JOIN      MOHECORE.STUDENT_FINANCE          sf   ON sf.STUDENT_ID                  = sd.STUDENT_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS     sss  ON sss.STUDENT_STUDY_STATUS_ID    = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE      sat  ON sat.STUDENT_AGENCY_TYPE_ID     = sd.STUDENT_AGENCY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE    smt  ON smt.STUDENT_MINISTRY_TYPE_ID   = sd.STUDENT_MINISTRY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE  sut  ON sut.STUDENT_UNIVERSITY_TYPE_ID = sd.STUDENT_UNIVERSITY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY          sc   ON sc.STUDENT_COUNTRY_ID          = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE           sdeg ON sdeg.STUDENT_DEGREE_ID         = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_MAJOR            sm   ON sm.STUDENT_MAJOR_ID            = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL    scl  ON scl.STUDENT_CURRENT_LEVEL_ID   = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN         sci  ON sci.STUDY_COUNTRY_IN_ID        = sf.STUDY_COUNTRY_IN_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

PRINT '  view MOHECORE.Q_Stud_Financial_Data_Form_V2 created or altered';
GO

/* ── 2. Report type settings row ─────────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS WHERE REPORT_TYPE_CODE = 'FINANCIAL_DATA_FORM_V2')
BEGIN
    INSERT INTO MOHECORE.REPORT_TYPE_SETTINGS
        (REPORT_TYPE_CODE, REPORT_TYPE_NAME_AR, REPORT_TYPE_NAME_EN, IS_ACTIVE,
         TITLE_01_AR, TITLE_01_EN, TITLE_02_AR, TITLE_02_EN, TITLE_03_AR, TITLE_03_EN,
         FOOTER_TEXT_AR, FOOTER_TEXT_EN, SIGNATORY_COUNT,
         SIGNATORY_NAME_01_AR, SIGNATORY_NAME_01_EN, SIGNATORY_POS_01_AR, SIGNATORY_POS_01_EN,
         SIGNATORY_NAME_02_AR, SIGNATORY_NAME_02_EN, SIGNATORY_POS_02_AR, SIGNATORY_POS_02_EN,
         SIGNATORY_NAME_03_AR, SIGNATORY_NAME_03_EN, SIGNATORY_POS_03_AR, SIGNATORY_POS_03_EN,
         SIGNATORY_NAME_04_AR, SIGNATORY_NAME_04_EN, SIGNATORY_POS_04_AR, SIGNATORY_POS_04_EN,
         SIGNATORY_NAME_05_AR, SIGNATORY_NAME_05_EN, SIGNATORY_POS_05_AR, SIGNATORY_POS_05_EN)
    VALUES
        ('FINANCIAL_DATA_FORM_V2',
         N'كشوفات المساعدة المالية الربعية', 'Quarterly Financial Assistance Statements', 1,
         N'الجمهورية اليمنية',                               'Republic of Yemen',
         N'وزارة المالية',               'Ministry of Finance',
         N'الإدارة العامة للبعثات - إدارة مالية البعثات',     'General Department of Scholarships - Scholarship Finance Department',
         N'', '', 5,
         N'', '', N'مدير مالية البعثات',                     'Director of Scholarship Finance',
         N'', '', N'مدير عام البعثات والمنح الدراسية',        'Director General of Scholarships and Grants',
         N'', '', N'مدير عام الشؤون المالية',                 'Director General of Financial Affairs',
         N'', '', N'وكيل الوزارة لقطاع البعثات',              'Deputy Minister for the Scholarships Sector',
         N'', '', N'وزير التعليم العالي والبحث العلمي',       'Minister of Higher Education and Scientific Research');
    PRINT '  REPORT_TYPE_SETTINGS: FINANCIAL_DATA_FORM_V2 inserted';
END
ELSE
BEGIN
    UPDATE MOHECORE.REPORT_TYPE_SETTINGS
       SET TITLE_01_AR = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_01_AR)), N''), N'الجمهورية اليمنية'),
           TITLE_01_EN = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_01_EN)), ''),  'Republic of Yemen'),
           TITLE_02_AR = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_02_AR)), N''), N'وزارة المالية'),
           TITLE_02_EN = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_02_EN)), ''),  'Ministry of Finance'),
           TITLE_03_AR = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_03_AR)), N''), N'الإدارة العامة للبعثات - إدارة مالية البعثات'),
           TITLE_03_EN = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_03_EN)), ''),  'General Department of Scholarships - Scholarship Finance Department'),
           SIGNATORY_POS_01_AR = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_01_AR)), N''), N'مدير مالية البعثات'),
           SIGNATORY_POS_01_EN = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_01_EN)), ''),  'Director of Scholarship Finance'),
           SIGNATORY_POS_02_AR = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_02_AR)), N''), N'مدير عام البعثات والمنح الدراسية'),
           SIGNATORY_POS_02_EN = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_02_EN)), ''),  'Director General of Scholarships and Grants'),
           SIGNATORY_POS_03_AR = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_03_AR)), N''), N'مدير عام الشؤون المالية'),
           SIGNATORY_POS_03_EN = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_03_EN)), ''),  'Director General of Financial Affairs'),
           SIGNATORY_POS_04_AR = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_04_AR)), N''), N'وكيل الوزارة لقطاع البعثات'),
           SIGNATORY_POS_04_EN = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_04_EN)), ''),  'Deputy Minister for the Scholarships Sector'),
           SIGNATORY_POS_05_AR = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_05_AR)), N''), N'وزير التعليم العالي والبحث العلمي'),
           SIGNATORY_POS_05_EN = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_05_EN)), ''),  'Minister of Higher Education and Scientific Research'),
           -- Only raise a count that was never configured; never lower an administrator's choice.
           SIGNATORY_COUNT = CASE WHEN COALESCE(SIGNATORY_COUNT, 0) = 0 THEN 5 ELSE SIGNATORY_COUNT END
     WHERE REPORT_TYPE_CODE = 'FINANCIAL_DATA_FORM_V2';
    PRINT '  REPORT_TYPE_SETTINGS: FINANCIAL_DATA_FORM_V2 already present — blanks filled, edits kept';
END
GO

/* ── 3. Verification ─────────────────────────────────────────────────────── */
DECLARE @rows int, @blankEntity int;
SELECT @rows = COUNT(*), @blankEntity = SUM(CASE WHEN ENTITY_NAME = N'' THEN 1 ELSE 0 END)
FROM MOHECORE.Q_Stud_Financial_Data_Form_V2;
PRINT '  view rows (students with a finance row): ' + CAST(@rows AS varchar(10))
    + ', with no resolvable entity: ' + CAST(ISNULL(@blankEntity, 0) AS varchar(10));

SELECT STUDENT_FIN_RECIEVED_YEAR AS [year], STUDENT_FIN_RECIEVED_MONTH AS [quarter], COUNT(*) AS students
FROM MOHECORE.Q_Stud_Financial_Data_Form_V2
GROUP BY STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH
ORDER BY 1 DESC, 2 DESC;

SELECT REPORT_TYPE_CODE, SIGNATORY_COUNT,
       CASE WHEN NULLIF(LTRIM(RTRIM(TITLE_03_AR)), N'') IS NULL THEN 'BLANK' ELSE 'set' END AS title_03_ar,
       CASE WHEN NULLIF(LTRIM(RTRIM(SIGNATORY_POS_05_AR)), N'') IS NULL THEN 'BLANK' ELSE 'set' END AS pos_05_ar
FROM MOHECORE.REPORT_TYPE_SETTINGS
WHERE REPORT_TYPE_CODE = 'FINANCIAL_DATA_FORM_V2';
GO

PRINT '=== V057 complete ===';
GO
