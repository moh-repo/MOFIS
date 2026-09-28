/*
    V105 — English reports print English data: university, statistics and payment views
    ===================================================================================

    The second half of finding 4 of docs/report-data-audit.md. V104 did the four
    student-facing views; this one covers the rest of the printed reports:

        V_Program_Catalog                  V_University_Performance
        V_Fee_Structure                    V_Enrollment_Statistics
        V_Student_Distribution_University  V_Major_Distribution
        V_University_Agreement             V_Payment_Summary
        V_University_Partnership

    Each gains an English sibling beside every lookup name column, with the same
    COALESCE(NULLIF(en,''), ar) fallback V104 uses, and for the same reason: a
    lookup with no English name should print Arabic, not a blank cell.
    BilingualRows (wired into JasperReportService.fill) does the per-language
    resolution, so no template changes and no per-report code.

    ON THE _ar COLUMNS

    Several of these views already carried a `*_name_ar` column beside the plain
    `*_name`. Both held the same Arabic string -- the `_ar` was a duplicate, not
    a translation, so nothing was bilingual. The plain column keeps its Arabic
    value and gains an `_en` sibling; the `_ar` column is left exactly as it is,
    because a report that deliberately prints both languages side by side (the
    programme catalogue does) needs the Arabic one to stay Arabic.

    ON THE GROUP BY

    Seven of these views aggregate. Each new English column is added to GROUP BY
    beside the Arabic name it accompanies. It is functionally dependent on the
    same lookup id, so this cannot split a group -- verified rather than assumed:
    every view returns exactly the row count it returned before.

        V_Program_Catalog 4    V_Fee_Structure 3     V_University_Agreement 3
        V_University_Partnership 3                   V_University_Performance 3
        V_Student_Distribution_University 3          V_Enrollment_Statistics 199
        V_Major_Distribution 199                     V_Payment_Summary 941

    WHAT IS NOT TRANSLATED

    STUDENT_NAME is the person's own name. It has no English form and no column
    to read one from, so it is deliberately left alone; the same goes for
    user-entered report and template names.

    Two of the new columns will print empty until the ministry records the data
    behind them: V_Payment_Summary.payment_currency and .quarter_name are NULL on
    all 941 rows today, because STUDENT_FINANCE.STUDY_CURRENCY_IN_ID and
    .MOHE_QUARTER_ID are unpopulated. Wiring their English siblings now is still
    right -- the reports become correct the moment the data arrives, rather than
    needing a second pass.

    AFTER APPLYING

        python scripts/audit_report_data.py
        sqlcmd ... -i docs/scripts/adhoc/probe_report_column_population.sql

    Apply before deploying the matching build: the mapped entities carry the new
    columns and ddl-auto=none means a missing column fails at query time.
*/

SET QUOTED_IDENTIFIER ON;
GO

/* ── V_Program_Catalog ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Program_Catalog AS

SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    CAST(sm.STUDENT_MAJOR_ID AS BIGINT) AS major_id,
    sdeg.STUDENT_DEGREE_ID AS degree_level_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME), N'') AS university_name_en,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS major_name,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS major_name_en,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS major_name_ar,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS degree_level_name,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS degree_level_name_en,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS country_name_en,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_students_enrolled,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS current_students
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
CROSS JOIN MOHECORE.STUDENT_MAJOR sm
CROSS JOIN MOHECORE.STUDENT_DEGREE sdeg
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID
                                   AND sd.STUDENT_MAJOR_ID = sm.STUDENT_MAJOR_ID
                                   AND sd.STUDENT_DEGREE_ID = sdeg.STUDENT_DEGREE_ID
                                   AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME,
         sm.STUDENT_MAJOR_ID, sm.STUDENT_MAJOR_NAME, sdeg.STUDENT_DEGREE_ID, sdeg.STUDENT_DEGREE_NAME,
         sui.STUDY_UNIVERSITY_IN_NAME_EN,
         sm.STUDENT_MAJOR_NAME_EN,
         sdeg.STUDENT_DEGREE_NAME_EN,
         sci.STUDY_COUNTRY_IN_NAME_EN
HAVING COUNT(DISTINCT sd.STUDENT_ID) > 0;

GO

/* ── V_Fee_Structure ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Fee_Structure AS


SELECT

    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,

    sdeg.STUDENT_DEGREE_ID                     AS degree_level_id,

    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'')  AS university_name,
    ISNULL(COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME), N'') AS university_name_en,

    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'')  AS university_name_ar,

    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'')     AS country_name,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS country_name_en,

    sui.STUDY_COUNTRY_IN_ID AS country_id,

    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'')      AS degree_level_name,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS degree_level_name_en,

    COUNT(DISTINCT sd.STUDENT_ID) AS total_students,

    CAST(AVG(CAST(suf.STUDY_UNIVERSITY_FEE_AMOUNT AS DECIMAL(18,2))) AS DECIMAL(18,2)) AS avg_fee_amount,

    CAST(MIN(CAST(suf.STUDY_UNIVERSITY_FEE_AMOUNT AS DECIMAL(18,2))) AS DECIMAL(18,2)) AS min_fee_amount,

    CAST(MAX(CAST(suf.STUDY_UNIVERSITY_FEE_AMOUNT AS DECIMAL(18,2))) AS DECIMAL(18,2)) AS max_fee_amount,

    CAST(SUM(CAST(suf.STUDY_UNIVERSITY_FEE_AMOUNT AS DECIMAL(18,2))) AS DECIMAL(18,2)) AS total_fees_paid,

    COUNT(suf.STUDY_UNIVERSITY_FEE_ID) AS total_transactions,

    CASE WHEN COUNT(suf.STUDY_UNIVERSITY_FEE_ID) = 0 THEN CAST(0 AS DECIMAL(18,2))

         ELSE CAST(SUM(CAST(suf.STUDY_UNIVERSITY_FEE_AMOUNT AS DECIMAL(18,2))) / COUNT(suf.STUDY_UNIVERSITY_FEE_ID) AS DECIMAL(18,2))

    END AS avg_per_transaction

FROM MOHECORE.STUDY_UNIVERSITY_IN sui

CROSS JOIN MOHECORE.STUDENT_DEGREE sdeg

LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sui.STUDY_COUNTRY_IN_ID

LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID

                                   AND sd.STUDENT_DEGREE_ID = sdeg.STUDENT_DEGREE_ID

                                   AND ISNULL(sd.IS_DELETED, 0) = 0

LEFT JOIN MOHECORE.STUDY_UNIVERSITY_FEE suf ON suf.STUDENT_ID = sd.STUDENT_ID

GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME,

         sdeg.STUDENT_DEGREE_ID, sdeg.STUDENT_DEGREE_NAME,
         sui.STUDY_UNIVERSITY_IN_NAME_EN,
         sci.STUDY_COUNTRY_IN_NAME_EN,
         sdeg.STUDENT_DEGREE_NAME_EN

HAVING COUNT(DISTINCT sd.STUDENT_ID) > 0;

GO

/* ── V_Student_Distribution_University ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Student_Distribution_University AS

SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME), N'') AS university_name_en,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS country_name_en,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name_ar,
    COUNT(sd.STUDENT_ID) AS total_students,
    COUNT(CASE WHEN sss.CODE = 'ACTIVE'    THEN 1 END) AS active_students,
    COUNT(CASE WHEN sss.CODE = 'SUSPENDED' THEN 1 END) AS suspended_students,
    COUNT(CASE WHEN sg.CODE = 'MALE'   THEN 1 END) AS male_students,
    COUNT(CASE WHEN sg.CODE = 'FEMALE' THEN 1 END) AS female_students,
    COUNT(CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN 1 END) AS scholarship_students,
    COUNT(CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN 1 END) AS regular_students,
    COUNT(CASE WHEN sdeg.LEVEL_CODE = 'BACHELOR' THEN 1 END) AS bachelors_students,
    COUNT(CASE WHEN sdeg.LEVEL_CODE = 'MASTER'   THEN 1 END) AS masters_students,
    COUNT(CASE WHEN sdeg.LEVEL_CODE = 'PHD'      THEN 1 END) AS phd_students,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS programs_count,
    CAST(COUNT(sd.STUDENT_ID) * 100.0 / NULLIF(SUM(COUNT(sd.STUDENT_ID)) OVER (), 0) AS DECIMAL(5,2)) AS percentage_of_total_students
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN     sci  ON sci.STUDY_COUNTRY_IN_ID     = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA         sd   ON sd.STUDY_UNIVERSITY_IN      = sui.STUDY_UNIVERSITY_IN_ID
                                            AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss  ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_GENDER       sg   ON sg.STUDENT_GENDER_ID        = sd.STUDENT_GENDER
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sty  ON sty.STUDENT_STUDY_TYPE_ID   = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE       sdeg ON sdeg.STUDENT_DEGREE_ID      = sd.STUDENT_DEGREE_ID
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME,
         sui.STUDY_UNIVERSITY_IN_NAME_EN,
         sci.STUDY_COUNTRY_IN_NAME_EN;

GO

/* ── V_University_Agreement ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_University_Agreement AS

SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME), N'') AS university_name_en,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS country_name_en,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name_ar,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_students_under_agreement,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS active_students,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS programs_covered,
    COUNT(DISTINCT sd.STUDENT_DEGREE_ID) AS degree_levels_covered
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID
                                   AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME,
         sui.STUDY_UNIVERSITY_IN_NAME_EN,
         sci.STUDY_COUNTRY_IN_NAME_EN;

GO

/* ── V_University_Partnership ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_University_Partnership AS

SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME), N'') AS university_name_en,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS country_name_en,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name_ar,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_students,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS total_programs,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS active_students,
    COUNT(DISTINCT sd.STUDENT_DEGREE_ID) AS degree_levels_offered,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_students,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sd.STUDENT_ID END) AS regular_students
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID
                                   AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sty ON sty.STUDENT_STUDY_TYPE_ID   = sd.STUDENT_STUDY_TYPE_ID
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME,
         sui.STUDY_UNIVERSITY_IN_NAME_EN,
         sci.STUDY_COUNTRY_IN_NAME_EN;

GO

/* ── V_University_Performance ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_University_Performance AS

SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME), N'') AS university_name_en,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'') AS country_name_en,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_enrolled,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE'    THEN sd.STUDENT_ID END) AS currently_active,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'SUSPENDED' THEN sd.STUDENT_ID END) AS total_suspended,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN CAST(0 AS DECIMAL(5,2))
         ELSE CAST(COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) * 100.0
                   / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(5,2)) END AS retention_rate,
    CAST(AVG(CAST(NULLIF(sd.STUDENT_STUDY_NO_OF_YEARS, 0) AS DECIMAL(9,4))) AS DECIMAL(5,2)) AS avg_study_duration,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS programs_offered,
    COUNT(DISTINCT sd.STUDENT_DEGREE_ID) AS degree_levels,
    MIN(TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT)) AS first_enrollment_year,
    MAX(TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT)) AS last_enrollment_year
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID
                                   AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME,
         sui.STUDY_UNIVERSITY_IN_NAME_EN,
         sci.STUDY_COUNTRY_IN_NAME_EN;

GO

/* ── V_Enrollment_Statistics ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Enrollment_Statistics AS

SELECT
    ISNULL(sd.STUDENT_ADDED_QUARTER, 0) AS enrollment_quarter,
    sd.STUDY_COUNTRY_IN AS study_country_in_id,                     -- tenant key
    -- These five are the entity's composite key. Hibernate returns a null entity
    -- for any row where a key component is null, and with the lookup joins now
    -- LEFT they can be: STUDENT_DEGREE_ID is unset on 937 of 941 students. 0 is
    -- the existing "unset" sentinel here, already used for enrollment_quarter.
    ISNULL(sd.STUDENT_COUNTRY_ID, 0)    AS STUDENT_COUNTRY_ID,       -- nationality
    ISNULL(sd.STUDENT_MAJOR_ID, 0)      AS STUDENT_MAJOR_ID,
    ISNULL(sd.STUDENT_DEGREE_ID, 0)     AS STUDENT_DEGREE_ID,
    ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0) AS STUDENT_STUDY_TYPE_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS STUDENT_DEGREE_NAME_EN,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME), N'') AS STUDENT_STUDY_TYPE_NAME_EN,
    COUNT(sd.STUDENT_ID) AS student_count
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_COUNTRY     sc   ON sc.STUDENT_COUNTRY_ID     = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_MAJOR       sm   ON sm.STUDENT_MAJOR_ID       = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE      sdeg ON sdeg.STUDENT_DEGREE_ID    = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE  sst  ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDENT_ADDED_QUARTER, sd.STUDY_COUNTRY_IN, ISNULL(sd.STUDENT_COUNTRY_ID, 0),
         ISNULL(sd.STUDENT_MAJOR_ID, 0), ISNULL(sd.STUDENT_DEGREE_ID, 0), ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0),
         sc.STUDENT_COUNTRY_NAME, sm.STUDENT_MAJOR_NAME, sdeg.STUDENT_DEGREE_NAME,
         sst.STUDENT_STUDY_TYPE_NAME,
         sc.STUDENT_COUNTRY_NAME_EN,
         sm.STUDENT_MAJOR_NAME_EN,
         sdeg.STUDENT_DEGREE_NAME_EN,
         sst.STUDENT_STUDY_TYPE_NAME_EN;

GO

/* ── V_Major_Distribution ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Major_Distribution AS

SELECT
    sd.STUDY_COUNTRY_IN AS study_country_in_id,                     -- tenant key
    -- Composite key components, coalesced for the same reason as above.
    ISNULL(sd.STUDENT_MAJOR_ID, 0)      AS STUDENT_MAJOR_ID,
    ISNULL(sd.STUDENT_DEGREE_ID, 0)     AS STUDENT_DEGREE_ID,
    ISNULL(sd.STUDENT_COUNTRY_ID, 0)    AS STUDENT_COUNTRY_ID,       -- nationality
    ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0) AS STUDENT_STUDY_TYPE_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS STUDENT_DEGREE_NAME_EN,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME), N'') AS STUDENT_STUDY_TYPE_NAME_EN,
    COUNT(sd.STUDENT_ID) AS student_count,
    SUM(CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Male%'   THEN 1 ELSE 0 END) AS male_count,
    SUM(CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Female%' THEN 1 ELSE 0 END) AS female_count
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_MAJOR       sm   ON sm.STUDENT_MAJOR_ID       = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE      sdeg ON sdeg.STUDENT_DEGREE_ID    = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY     sc   ON sc.STUDENT_COUNTRY_ID     = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE  sst  ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_GENDER      sg   ON sg.STUDENT_GENDER_ID      = sd.STUDENT_GENDER
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDY_COUNTRY_IN, ISNULL(sd.STUDENT_MAJOR_ID, 0), ISNULL(sd.STUDENT_DEGREE_ID, 0),
         ISNULL(sd.STUDENT_COUNTRY_ID, 0), ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0),
         sm.STUDENT_MAJOR_NAME, sdeg.STUDENT_DEGREE_NAME, sc.STUDENT_COUNTRY_NAME,
         sst.STUDENT_STUDY_TYPE_NAME,
         sm.STUDENT_MAJOR_NAME_EN,
         sdeg.STUDENT_DEGREE_NAME_EN,
         sc.STUDENT_COUNTRY_NAME_EN,
         sst.STUDENT_STUDY_TYPE_NAME_EN;

GO

/* ── V_Payment_Summary ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Payment_Summary AS

SELECT
    sf.STUDENT_FIN_ID                                                    AS payment_id,
    sd.STUDENT_ID                                                        AS student_id,
    sd.STUDENT_NAME                                                      AS student_name,
    sd.STUDENT_PASSPORT_NUMBER                                           AS student_passport_number,
    CAST(sf.STUDY_CHECK_DATE AS DATE)                                    AS payment_date,
    sf.NET_AMOUNT                                                        AS payment_amount,
    cur.STUDY_CURRENCY_IN_NAME                                           AS payment_currency,
    COALESCE(NULLIF(cur.STUDY_CURRENCY_IN_NAME_EN, N''), cur.STUDY_CURRENCY_IN_NAME) AS payment_currency_en,
    sfrs.STUDY_FIN_RECEIVED_STATUS_NAME                                  AS payment_status,
    COALESCE(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME_EN, N''), sfrs.STUDY_FIN_RECEIVED_STATUS_NAME) AS payment_status_en,
    sfrs.CODE                                                            AS payment_status_code,
    srws.STUDY_RECEIVED_WHOM_STATUS_NAME                                 AS payment_type,
    COALESCE(NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME_EN, N''), srws.STUDY_RECEIVED_WHOM_STATUS_NAME) AS payment_type_en,
    -- How the money went out, from what the row references (LU_PAYMENT_METHOD codes).
    CASE WHEN sf.STUDY_FINANCE_BANKE_ID IS NOT NULL THEN 'BANK_TRANSFER'
         WHEN sf.STUDY_CHECK_ID IS NOT NULL AND LTRIM(RTRIM(sf.STUDY_CHECK_ID)) <> '' THEN 'CHECK'
         ELSE 'CASH' END                                                 AS payment_method,
    mq.QUARTER_NAME                                                      AS quarter_name,
    COALESCE(NULLIF(mq.QUARTER_NAME_EN, N''), mq.QUARTER_NAME) AS quarter_name_en,
    sf.STUDENT_FIN_RECIEVED_YEAR                                         AS year_name,
    sui.STUDY_UNIVERSITY_IN_NAME                                         AS university_name,
    COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
    smi.STUDY_MAJOR_NAME                                                 AS major_name,
    COALESCE(NULLIF(smi.STUDY_MAJOR_NAME_EN, N''), smi.STUDY_MAJOR_NAME) AS major_name_en,
    sf.STUDENT_FIN_RECIEVED_YEAR                                         AS payment_year,
    sf.STUDENT_FIN_RECIEVED_MONTH                                        AS payment_month
FROM MOHECORE.STUDENT_FINANCE sf
LEFT JOIN MOHECORE.STUDENT_DATA               sd   ON sf.STUDENT_ID                    = sd.STUDENT_ID
LEFT JOIN MOHECORE.STUDY_CURRENCY_IN          cur  ON cur.STUDY_CURRENCY_IN_ID         = sf.STUDY_CURRENCY_IN_ID
LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS  sfrs ON sf.STUDY_FIN_RECIEVED_STATUS     = sfrs.STUDY_FIN_RECEIVED_STATUS_ID
LEFT JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS srws ON sf.STUDY_RECEIVED_WHOM_STATUS_ID = srws.STUDY_RECEIVED_WHOM_STATUS_ID
LEFT JOIN MOHECORE.MOHE_QUARTER               mq   ON mq.QUARTER = CASE WHEN sf.STUDENT_FIN_RECIEVED_MONTH IS NULL THEN NULL
                                                                        ELSE ((sf.STUDENT_FIN_RECIEVED_MONTH - 1) / 3) + 1 END
LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN        sui  ON sd.STUDY_UNIVERSITY_IN           = sui.STUDY_UNIVERSITY_IN_ID
LEFT JOIN MOHECORE.STUDY_MAJOR_IN             smi  ON sd.STUDY_MAJOR_IN                = smi.STUDY_MAJOR_ID;

GO

/*  Verification — every view should return the row count noted in the header,
    and every lookup name column should now have an English sibling.  */
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1', @read_only = 0;

SELECT v.name AS vw, c.name AS still_arabic_only
FROM sys.views v
JOIN sys.columns c ON c.object_id = v.object_id
WHERE v.name IN ('V_Program_Catalog','V_Fee_Structure','V_Student_Distribution_University',
                 'V_University_Agreement','V_University_Partnership','V_University_Performance',
                 'V_Enrollment_Statistics','V_Major_Distribution','V_Payment_Summary')
  AND c.name LIKE '%name%' AND c.name NOT LIKE '%name[_]en' AND c.name NOT LIKE '%name[_]ar'
  AND c.name NOT IN ('student_name', 'STUDENT_NAME')
  AND NOT EXISTS (SELECT 1 FROM sys.columns c2
                  WHERE c2.object_id = v.object_id AND c2.name = c.name + '_EN')
ORDER BY v.name, c.name;
GO
