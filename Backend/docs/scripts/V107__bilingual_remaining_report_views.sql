/*
    V107 — English reports print English data: the last of the printed views
    ========================================================================

    The third and final instalment of finding 4 of docs/report-data-audit.md,
    after V104 (student views) and V105 (university, statistics, payment).

        Q_Stud_Financial_Data_Form      V_Scholarship_Tracking
        Q_Stud_Financial_Data_Form_V2   V_Comparative_Statistics

    Same shape as before: a paired English column beside each Arabic lookup name,
    COALESCE(NULLIF(en,''), ar) so an untranslated lookup prints Arabic rather
    than a blank, and BilingualRows (JasperReportService.fill) resolving per
    language so no template changes.

    WHAT IS DELIBERATELY LEFT ARABIC-ONLY

    Six name columns remain untranslated across the printed views, each because
    it does not name a lookup:

      V_Query_Builder.table_name, .column_name   database identifiers. Translating
                                                 a table name would break the
                                                 query the user then builds.
      V_Scheduled_Report.schedule_name           typed by the person who made the
                                                 schedule.
      V_Performance_Metrics.metric_name          a stable key ('total_students'),
                                                 not a label -- the same reason
                                                 V028 keys status on CODE. The
                                                 screen maps it through i18n.
      V_Data_Visualization.chart_name            a hardcoded N'Student Distribution'
                                                 in the view. It is chrome that
                                                 belongs in i18n, and it is the
                                                 mirror of finding 4: an *Arabic*
                                                 report prints this in English.
                                                 Recorded, not fixed here -- it
                                                 needs the label moved out of SQL.
      V_Distribution_Statistics.category_name    a UNION whose branches draw from
                                                 four different lookups. Doable,
                                                 but every branch needs its own
                                                 join, so it is its own change.

    ENTITY_NAME on the V2 form is a COALESCE across three lookups -- university
    type, then ministry type, then agency type. Its English sibling repeats that
    order over the _EN columns, so the two agree on which lookup answered.

    V_Comparative_Statistics is written out by hand rather than generated: its
    dimension_name comes from a UNION inside a CTE, so the English column has to
    be added to both branches, to both GROUP BYs, and to the outer select.

    AFTER APPLYING

        python scripts/audit_report_data.py
        mvn -o test -Dtest=BilingualRowsTest,BilingualViewMappingTest

    Apply before deploying the matching build: the entities map the new columns
    and ddl-auto=none means a missing column fails at query time. View-only, so
    it applies as mohe_app -- ignore the misleading Msg 3701 and check the result.
*/

SET QUOTED_IDENTIFIER ON;
GO

/* ── Q_Stud_Financial_Data_Form ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.Q_Stud_Financial_Data_Form AS


SELECT

    sd.STUDENT_ID,

    sd.STUDENT_NAME,

    sd.STUDENT_COUNTRY_ID,

    sd.STUDENT_DELEGATION_DECISION_YEAR,

    sd.STUDENT_DEGREE_ID,

    sd.STUDENT_MAJOR_ID,

    sd.STUDENT_DELEGATE_DECISION_NO,

    sd.STUDENT_STUDY_NO_OF_YEARS,

    sd.STUDENT_CURRENT_LEVEL_ID,

    sd.STUDENT_FIRST_RECEIPT_OF_DUE,

    sd.STUDENT_AMOUNT,

    sd.STUDENT_NOTE_1,

    sd.STUDENT_NOTE_2,

    sd.STUDENT_STUDY_STATUS_ID,

    sd.STUDENT_AGENCY_TYPE_ID,

    sd.STUDENT_MINISTRY_TYPE_ID,

    sd.STUDENT_STUDY_TYPE_ID,

    sd.STUDENT_UNIVERSITY_TYPE_ID,

    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'')   AS STUDENT_STUDY_STATUS_NAME,
    ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'') AS STUDENT_STUDY_STATUS_NAME_EN,

    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'')    AS STUDENT_AGENCY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sat.STUDENT_AGENCY_TYPE_NAME_EN, N''), sat.STUDENT_AGENCY_TYPE_NAME), N'') AS STUDENT_AGENCY_TYPE_NAME_EN,

    ISNULL(sc.STUDENT_COUNTRY_NAME, N'')         AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN,

    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'')        AS STUDENT_DEGREE_NAME,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS STUDENT_DEGREE_NAME_EN,

    ISNULL(sm.STUDENT_MAJOR_NAME, N'')           AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,

    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'')  AS STUDENT_MINISTRY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(smt.STUDENT_MINISTRY_TYPE_NAME_EN, N''), smt.STUDENT_MINISTRY_TYPE_NAME), N'') AS STUDENT_MINISTRY_TYPE_NAME_EN,

    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'') AS STUDENT_UNIVERSITY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sut.STUDENT_UNIVERSITY_TYPE_NAME_EN, N''), sut.STUDENT_UNIVERSITY_TYPE_NAME), N'') AS STUDENT_UNIVERSITY_TYPE_NAME_EN,

    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'')       AS STUDY_COUNTRY_IN_NAME,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS STUDY_COUNTRY_IN_NAME_EN,

    sf.STUDENT_FIN_RECIEVED_YEAR,

    sf.STUDENT_FIN_RECIEVED_MONTH,

    ISNULL(sd.STUDENT_NAME, N'') + N' - ' +

        ISNULL(CAST(sf.STUDENT_FIN_RECIEVED_YEAR AS NVARCHAR(4)), N'') AS HEAD_TITLE

FROM MOHECORE.STUDENT_DATA sd

LEFT JOIN MOHECORE.STUDENT_FINANCE           sf  ON sf.STUDENT_ID             = sd.STUDENT_ID

LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS      sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID

LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE       sat ON sat.STUDENT_AGENCY_TYPE_ID  = sd.STUDENT_AGENCY_TYPE_ID

LEFT JOIN MOHECORE.STUDENT_COUNTRY           sc  ON sc.STUDENT_COUNTRY_ID       = sd.STUDENT_COUNTRY_ID

LEFT JOIN MOHECORE.STUDENT_DEGREE            sdeg ON sdeg.STUDENT_DEGREE_ID     = sd.STUDENT_DEGREE_ID

LEFT JOIN MOHECORE.STUDENT_MAJOR             sm  ON sm.STUDENT_MAJOR_ID         = sd.STUDENT_MAJOR_ID

LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE     smt ON smt.STUDENT_MINISTRY_TYPE_ID = sd.STUDENT_MINISTRY_TYPE_ID

LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE   sut ON sut.STUDENT_UNIVERSITY_TYPE_ID = sd.STUDENT_UNIVERSITY_TYPE_ID

LEFT JOIN MOHECORE.STUDY_COUNTRY_IN          sci ON sci.STUDY_COUNTRY_IN_ID     = sf.STUDY_COUNTRY_IN_ID

WHERE ISNULL(sd.IS_DELETED, 0) = 0;

GO

/* ── Q_Stud_Financial_Data_Form_V2 ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.Q_Stud_Financial_Data_Form_V2 AS

SELECT
    sd.STUDENT_ID,
    ISNULL(sd.STUDENT_NAME, N'')                                   AS STUDENT_NAME,
    -- الجهة: university -> ministry -> agency, never NULL
    COALESCE(NULLIF(LTRIM(RTRIM(sut.STUDENT_UNIVERSITY_TYPE_NAME)), N''),
             NULLIF(LTRIM(RTRIM(smt.STUDENT_MINISTRY_TYPE_NAME)),   N''),
             NULLIF(LTRIM(RTRIM(sat.STUDENT_AGENCY_TYPE_NAME)),     N''),
             N'')                                                  AS ENTITY_NAME,
    -- The English form answers with the same lookup the Arabic one did: the
    -- COALESCE order is repeated over the _EN columns, each falling back to its
    -- own Arabic name, so the two columns can never describe different bodies.
    COALESCE(NULLIF(LTRIM(RTRIM(COALESCE(NULLIF(sut.STUDENT_UNIVERSITY_TYPE_NAME_EN, N''), sut.STUDENT_UNIVERSITY_TYPE_NAME))), N''),
             NULLIF(LTRIM(RTRIM(COALESCE(NULLIF(smt.STUDENT_MINISTRY_TYPE_NAME_EN,   N''), smt.STUDENT_MINISTRY_TYPE_NAME))),   N''),
             NULLIF(LTRIM(RTRIM(COALESCE(NULLIF(sat.STUDENT_AGENCY_TYPE_NAME_EN,     N''), sat.STUDENT_AGENCY_TYPE_NAME))),     N''),
             N'')                                                  AS ENTITY_NAME_EN,
    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'')                      AS STUDENT_AGENCY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sat.STUDENT_AGENCY_TYPE_NAME_EN, N''), sat.STUDENT_AGENCY_TYPE_NAME), N'') AS STUDENT_AGENCY_TYPE_NAME_EN,
    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'')                    AS STUDENT_MINISTRY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(smt.STUDENT_MINISTRY_TYPE_NAME_EN, N''), smt.STUDENT_MINISTRY_TYPE_NAME), N'') AS STUDENT_MINISTRY_TYPE_NAME_EN,
    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'')                  AS STUDENT_UNIVERSITY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sut.STUDENT_UNIVERSITY_TYPE_NAME_EN, N''), sut.STUDENT_UNIVERSITY_TYPE_NAME), N'') AS STUDENT_UNIVERSITY_TYPE_NAME_EN,
    sf.STUDY_COUNTRY_IN_ID,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'')                         AS STUDY_COUNTRY_IN_NAME,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS STUDY_COUNTRY_IN_NAME_EN,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'')                           AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN,
    sd.STUDENT_DELEGATION_DECISION_YEAR,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'')                          AS STUDENT_DEGREE_NAME,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS STUDENT_DEGREE_NAME_EN,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'')                             AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,
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
    ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'') AS STUDENT_STUDY_STATUS_NAME_EN,
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

/* ── V_Scholarship_Tracking ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Scholarship_Tracking AS

SELECT
    CAST(ROW_NUMBER() OVER (ORDER BY sd.STUDENT_ID) AS BIGINT)          AS scholarship_id,
    sd.STUDENT_ID                                                        AS student_id,
    sd.STUDENT_NAME                                                      AS student_name,
    sd.STUDENT_PASSPORT_NUMBER                                           AS student_passport_number,
    sst.STUDENT_STUDY_TYPE_NAME                                          AS scholarship_type,
    COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS scholarship_type_en,
    sat.STUDENT_AGENCY_TYPE_NAME                                         AS scholarship_provider,
    COALESCE(NULLIF(sat.STUDENT_AGENCY_TYPE_NAME_EN, N''), sat.STUDENT_AGENCY_TYPE_NAME) AS scholarship_provider_en,
    -- The recorded allowance (STUDENT_AMOUNT) in the currency on the record.
    CAST(sd.STUDENT_AMOUNT AS DECIMAL(18, 2))                            AS scholarship_amount,
    CAST(NULLIF(cur.STUDY_CURRENCY_IN_NAME, '') AS NVARCHAR(100))        AS scholarship_currency,
    CAST(sd.STUDENT_MIDATE AS DATE)                                      AS allocated_date,
    CAST(sd.STUDENT_MIDATE AS DATE)                                      AS start_date,
    -- Start plus the recorded study length; nothing is assumed when it is not recorded.
    CAST(CASE WHEN sd.STUDENT_STUDY_NO_OF_YEARS IS NULL THEN NULL
              ELSE DATEADD(YEAR, sd.STUDENT_STUDY_NO_OF_YEARS, sd.STUDENT_MIDATE) END AS DATE) AS end_date,
    -- Sum of the finance rows whose status is RECEIVED (an empty set sums to 0).
    COALESCE(sf_totals.total_paid, 0)                                    AS amount_paid,
    sss.STUDENT_STUDY_STATUS_NAME                                        AS scholarship_status,
    COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS scholarship_status_en,
    sss.CODE                                                             AS scholarship_status_code,
    sui.STUDY_UNIVERSITY_IN_NAME                                         AS university_name,
    COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
    smi.STUDY_MAJOR_NAME                                                 AS major_name,
    COALESCE(NULLIF(smi.STUDY_MAJOR_NAME_EN, N''), smi.STUDY_MAJOR_NAME) AS major_name_en
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sst ON sd.STUDENT_STUDY_TYPE_ID   = sst.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE  sat ON sd.STUDENT_AGENCY_TYPE_ID  = sat.STUDENT_AGENCY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sd.STUDENT_STUDY_STATUS_ID = sss.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN  sui ON sd.STUDY_UNIVERSITY_IN     = sui.STUDY_UNIVERSITY_IN_ID
LEFT JOIN MOHECORE.STUDY_MAJOR_IN       smi ON sd.STUDY_MAJOR_IN          = smi.STUDY_MAJOR_ID
LEFT JOIN MOHECORE.STUDY_CURRENCY_IN    cur ON sd.amount_currency         = cur.STUDY_CURRENCY_IN_ID
LEFT JOIN (
    SELECT sf.STUDENT_ID, SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS total_paid
    FROM MOHECORE.STUDENT_FINANCE sf
    JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS s ON s.STUDY_FIN_RECEIVED_STATUS_ID = sf.STUDY_FIN_RECIEVED_STATUS
    WHERE s.CODE = 'RECEIVED'
    GROUP BY sf.STUDENT_ID
) sf_totals ON sf_totals.STUDENT_ID = sd.STUDENT_ID
WHERE sd.STUDENT_ID IS NOT NULL;

GO

/* ── V_Comparative_Statistics ────────────────────────────────────────────
   dimension_name is a country name in one branch of the UNION and a degree name
   in the other, so the English column is added to both branches with its own
   source, to both GROUP BYs, and to the outer select that reads the CTE.       */
CREATE OR ALTER VIEW MOHECORE.V_Comparative_Statistics AS
WITH cohort AS (
    SELECT
        sd.STUDY_COUNTRY_IN,
        sd.STUDENT_DEGREE_ID,
        TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) AS decision_year
    FROM MOHECORE.STUDENT_DATA sd
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
      AND TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) IS NOT NULL
),
chosen AS (
    -- Most recent cohort year that is not in the future, against the next year
    -- down that also has students.
    SELECT
        c.current_year,
        (SELECT MAX(decision_year) FROM cohort WHERE decision_year < c.current_year) AS previous_year
    FROM (
        SELECT MAX(decision_year) AS current_year
        FROM cohort
        WHERE decision_year <= YEAR(GETDATE())
    ) c
),
base AS (
    SELECT
        'country' AS comparison_type,
        ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'-') AS dimension_name,
        ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'-') AS dimension_name_en,
        SUM(CASE WHEN ch.decision_year = k.current_year  THEN 1 ELSE 0 END) AS current_period_value,
        SUM(CASE WHEN ch.decision_year = k.previous_year THEN 1 ELSE 0 END) AS previous_period_value,
        k.current_year,
        k.previous_year
    FROM cohort ch
    CROSS JOIN chosen k
    LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = ch.STUDY_COUNTRY_IN
    GROUP BY sci.STUDY_COUNTRY_IN_NAME, sci.STUDY_COUNTRY_IN_NAME_EN, k.current_year, k.previous_year

    UNION ALL

    SELECT
        'degree',
        ISNULL(sdeg.STUDENT_DEGREE_NAME, N'-'),
        ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'-'),
        SUM(CASE WHEN ch.decision_year = k.current_year  THEN 1 ELSE 0 END),
        SUM(CASE WHEN ch.decision_year = k.previous_year THEN 1 ELSE 0 END),
        k.current_year,
        k.previous_year
    FROM cohort ch
    CROSS JOIN chosen k
    LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = ch.STUDENT_DEGREE_ID
    GROUP BY sdeg.STUDENT_DEGREE_NAME, sdeg.STUDENT_DEGREE_NAME_EN, k.current_year, k.previous_year
)
SELECT
    comparison_type,
    dimension_name,
    dimension_name_en,
    current_period_value,
    previous_period_value,
    CASE WHEN ISNULL(previous_period_value, 0) = 0 THEN NULL
         ELSE CAST((current_period_value - previous_period_value) * 100.0
                   / previous_period_value AS DECIMAL(8,2))
    END AS change_percentage,
    CAST(current_year AS NVARCHAR(10))  AS current_period,
    CAST(previous_year AS NVARCHAR(10)) AS previous_period
FROM base
WHERE current_period_value <> 0 OR previous_period_value <> 0;

GO

/*  Verification — expect zero rows from the first query (every lookup name has
    an English sibling, apart from the six recorded in the header), and the row
    counts unchanged: 941, 941, 941, 4.  */
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1', @read_only = 0;

SELECT v.name AS vw, c.name AS still_arabic_only
FROM sys.views v
JOIN sys.columns c ON c.object_id = v.object_id
WHERE v.name IN ('Q_Stud_Financial_Data_Form', 'Q_Stud_Financial_Data_Form_V2',
                 'V_Scholarship_Tracking', 'V_Comparative_Statistics')
  AND c.name LIKE '%NAME%' AND c.name NOT LIKE '%NAME[_]EN'
  AND c.name NOT IN ('STUDENT_NAME', 'student_name')
  AND NOT EXISTS (SELECT 1 FROM sys.columns c2
                  WHERE c2.object_id = v.object_id AND c2.name = c.name + '_EN')
ORDER BY v.name, c.name;

SELECT 'Q_Stud_Financial_Data_Form' AS vw, COUNT(*) AS rows_ FROM MOHECORE.Q_Stud_Financial_Data_Form
UNION ALL SELECT 'Q_Stud_Financial_Data_Form_V2', COUNT(*) FROM MOHECORE.Q_Stud_Financial_Data_Form_V2
UNION ALL SELECT 'V_Scholarship_Tracking', COUNT(*) FROM MOHECORE.V_Scholarship_Tracking
UNION ALL SELECT 'V_Comparative_Statistics', COUNT(*) FROM MOHECORE.V_Comparative_Statistics;
GO
