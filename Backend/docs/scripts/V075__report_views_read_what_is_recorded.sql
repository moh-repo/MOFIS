/* ============================================================================
   V075 — Eleven report views read what is recorded, and nothing else.

   Why
   ---
   V033 captured these views verbatim and V034 corrected five of them. The
   rest still asserted values nobody recorded: 0 AS scholarship_students,
   0 AS regular_amount_disbursed, N'active' AS program_status,
   NULL AS agreement_duration_years, CAST(0 AS DECIMAL) AS
   total_financial_commitment — and a graduated count written as
   STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج'), which matches nothing
   because this ministry records no graduation (report-data-audit.md). The
   owner's decision (30 Aug 2026): compute what a real column supports; a
   column with no source disappears from the view, the entity and the screen.

   What
   ----
   1. STUDENT_GENDER gains CODE (MALE, FEMALE), derived once from the English
      name, so no view tests a name with LIKE N'Male%'.
   2. CREATE OR ALTER of V_Annual_Report, V_Monthly_Report, V_Quarterly_Report,
      V_Program_Catalog, V_Student_Distribution_University,
      V_University_Agreement, V_University_Partnership,
      V_University_Performance, V_Academic_Performance,
      V_Student_List_Detailed and VW_STUDENT_ENROLLMENT_BY_COUNTRY:
        - status counts by STUDENT_STUDY_STATUS.CODE (ACTIVE, NEW, SUSPENDED);
        - new_enrollments = students whose status CODE is NEW, the convention
          CustomPeriodReportRepository already uses;
        - scholarship / regular students and amounts by
          STUDENT_STUDY_TYPE.IS_SCHOLARSHIP (V028), as V034's fiscal-year split;
        - bachelors / masters / phd by STUDENT_DEGREE.LEVEL_CODE (V028);
        - retention_rate and avg_study_duration as AcademicYearReportRepository
          computes them;
        - gender by STUDENT_GENDER.CODE.
      Removed, having no source: graduated_students, total_graduated,
      graduation_rate, is_graduated, GPA, program_status, program_start_date,
      last_enrollment_date, scholarship_slots, agreement_start_date,
      last_activity_date, agreement_duration_years, scholarship_allocations,
      total_financial_commitment, agreement_status, operational_status,
      partnership_start_date, partnership_duration_years, GraduatedStudents.

   Rollback
   --------
     Re-run the CREATE OR ALTER blocks of V033 (and V066 for
     V_Academic_Performance / V_Student_List_Detailed); the entities of the
     previous build map the old columns. ALTER TABLE MOHECORE.STUDENT_GENDER
     DROP COLUMN CODE.

   Idempotent. Run with sqlcmd -I -f 65001 -b after V074.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF COL_LENGTH('MOHECORE.STUDENT_STUDY_STATUS', 'CODE') IS NULL
    THROW 50075, 'V075: STUDENT_STUDY_STATUS.CODE is missing — run V028 first.', 1;
IF COL_LENGTH('MOHECORE.STUDENT_STUDY_TYPE', 'IS_SCHOLARSHIP') IS NULL
    THROW 50075, 'V075: STUDENT_STUDY_TYPE.IS_SCHOLARSHIP is missing — run V028 first.', 1;
IF COL_LENGTH('MOHECORE.STUDENT_DEGREE', 'LEVEL_CODE') IS NULL
    THROW 50075, 'V075: STUDENT_DEGREE.LEVEL_CODE is missing — run V028 first.', 1;
GO

/* ── 1. STUDENT_GENDER.CODE ──────────────────────────────────────────────── */
PRINT '=== V075 step 1: STUDENT_GENDER.CODE ===';
IF COL_LENGTH('MOHECORE.STUDENT_GENDER', 'CODE') IS NULL
    ALTER TABLE MOHECORE.STUDENT_GENDER ADD CODE NVARCHAR(50) NULL;
GO
UPDATE MOHECORE.STUDENT_GENDER SET CODE = N'MALE'   WHERE CODE IS NULL AND STUDENT_GENDER_NAME_EN LIKE N'Male%';
UPDATE MOHECORE.STUDENT_GENDER SET CODE = N'FEMALE' WHERE CODE IS NULL AND STUDENT_GENDER_NAME_EN LIKE N'Female%';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDENT_GENDER_CODE')
    CREATE UNIQUE INDEX UQ_STUDENT_GENDER_CODE ON MOHECORE.STUDENT_GENDER (CODE) WHERE CODE IS NOT NULL;
DECLARE @uncoded INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_GENDER WHERE CODE IS NULL);
PRINT 'V075: gender rows without a code = ' + CAST(@uncoded AS VARCHAR(10));
GO

/* ── 2. The views ───────────────────────────────────────────────────────── */
PRINT '=== V075 step 2: views ===';
GO

CREATE OR ALTER VIEW MOHECORE.V_Annual_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_id,
    YEAR(sf.STUDY_FINANCE_MIDATE) AS report_year,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_students,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'NEW'       THEN sd.STUDENT_ID END) AS new_enrollments,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE'    THEN sd.STUDENT_ID END) AS active_students,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'SUSPENDED' THEN sd.STUDENT_ID END) AS suspended_students,
    COUNT(*)  AS total_financial_transactions,
    CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS total_amount_disbursed,
    CAST(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sf.STUDENT_FIN_RECIEVED_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS scholarship_amount_disbursed,
    CAST(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sf.STUDENT_FIN_RECIEVED_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS regular_amount_disbursed,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN 0
         ELSE CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) * 1.0 / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2))
    END AS avg_amount_per_student,
    CAST(MAX(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS max_amount_disbursed,
    CAST(MIN(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS min_amount_disbursed,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_students,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sd.STUDENT_ID END) AS regular_students,
    COUNT(DISTINCT CASE WHEN sg.CODE = 'MALE'   THEN sd.STUDENT_ID END) AS male_students,
    COUNT(DISTINCT CASE WHEN sg.CODE = 'FEMALE' THEN sd.STUDENT_ID END) AS female_students,
    COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
    COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS total_majors,
    COUNT(DISTINCT sd.STUDENT_DEGREE_ID) AS total_degrees
FROM MOHECORE.STUDENT_FINANCE sf
LEFT JOIN MOHECORE.STUDENT_DATA         sd  ON sd.STUDENT_ID               = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sty ON sty.STUDENT_STUDY_TYPE_ID   = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_GENDER       sg  ON sg.STUDENT_GENDER_ID        = sd.STUDENT_GENDER
WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE);
GO

CREATE OR ALTER VIEW MOHECORE.V_Monthly_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_id,
    YEAR(sf.STUDY_FINANCE_MIDATE)  AS report_year,
    MONTH(sf.STUDY_FINANCE_MIDATE) AS report_month,
    CAST(YEAR(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(4)) + '-' +
        RIGHT('0' + CAST(MONTH(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(2)), 2) AS report_period,
    COUNT(DISTINCT sd.STUDENT_ID)   AS total_students,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'NEW'    THEN sd.STUDENT_ID END) AS new_enrollments,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS active_students,
    COUNT(*)                        AS total_financial_transactions,
    CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS total_amount_disbursed,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN 0
         ELSE CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) * 1.0 / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2))
    END AS avg_amount_per_student,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_students,
    COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
    COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries
FROM MOHECORE.STUDENT_FINANCE sf
LEFT JOIN MOHECORE.STUDENT_DATA         sd  ON sd.STUDENT_ID               = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sty ON sty.STUDENT_STUDY_TYPE_ID   = sd.STUDENT_STUDY_TYPE_ID
WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE);
GO

CREATE OR ALTER VIEW MOHECORE.V_Quarterly_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_id,
    mq.QUARTER_YEAR AS report_year,
    mq.QUARTER_NO   AS report_quarter,
    CAST(mq.QUARTER_YEAR AS NVARCHAR(4)) + '-Q' + CAST(mq.QUARTER_NO AS NVARCHAR(1)) AS report_period,
    COUNT(DISTINCT sd.STUDENT_ID)   AS total_students,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'NEW'    THEN sd.STUDENT_ID END) AS new_enrollments,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS active_students,
    COUNT(sf.STUDENT_ID)            AS total_financial_transactions,
    CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) AS DECIMAL(18,2)) AS total_amount_disbursed,
    CAST(ISNULL(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sf.STUDENT_FIN_RECIEVED_AMOUNT ELSE 0 END), 0) AS DECIMAL(18,2)) AS scholarship_amount_disbursed,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN 0
         ELSE CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) * 1.0 / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2))
    END AS avg_amount_per_student,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_students,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sd.STUDENT_ID END) AS regular_students,
    COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
    COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS total_majors
FROM MOHECORE.MOHE_QUARTER mq
LEFT JOIN MOHECORE.STUDENT_FINANCE      sf  ON sf.MOHE_QUARTER_ID = mq.QUARTER
LEFT JOIN MOHECORE.STUDENT_DATA         sd  ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sty ON sty.STUDENT_STUDY_TYPE_ID   = sd.STUDENT_STUDY_TYPE_ID
GROUP BY sd.STUDY_COUNTRY_IN, mq.QUARTER_YEAR, mq.QUARTER_NO;
GO

CREATE OR ALTER VIEW MOHECORE.V_Program_Catalog AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    CAST(sm.STUDENT_MAJOR_ID AS BIGINT) AS major_id,
    sdeg.STUDENT_DEGREE_ID AS degree_level_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS major_name,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS major_name_ar,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS degree_level_name,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
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
         sm.STUDENT_MAJOR_ID, sm.STUDENT_MAJOR_NAME, sdeg.STUDENT_DEGREE_ID, sdeg.STUDENT_DEGREE_NAME
HAVING COUNT(DISTINCT sd.STUDENT_ID) > 0;
GO

CREATE OR ALTER VIEW MOHECORE.V_Student_Distribution_University AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
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
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME;
GO

CREATE OR ALTER VIEW MOHECORE.V_University_Agreement AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
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
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME;
GO

CREATE OR ALTER VIEW MOHECORE.V_University_Partnership AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
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
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME;
GO

CREATE OR ALTER VIEW MOHECORE.V_University_Performance AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
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
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME;
GO

CREATE OR ALTER VIEW MOHECORE.V_Academic_Performance AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    sd.STUDENT_COUNTRY_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_MAJOR         sm   ON sm.STUDENT_MAJOR_ID          = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL scl  ON scl.STUDENT_CURRENT_LEVEL_ID = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS  sss  ON sss.STUDENT_STUDY_STATUS_ID  = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE        sdeg ON sdeg.STUDENT_DEGREE_ID       = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY       sc   ON sc.STUDENT_COUNTRY_ID        = sd.STUDENT_COUNTRY_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

CREATE OR ALTER VIEW MOHECORE.V_Student_List_Detailed AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDENT_PASSPORT_NUMBER,
    CAST(sd.STUDENT_BIRTHDATE AS DATE)   AS STUDENT_BIRTHDATE,
    TRY_CAST(sd.STUDENT_AGE AS INT)      AS student_age,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,
    sd.STUDENT_COUNTRY_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(sg.STUDENT_GENDER_NAME, N'')  AS STUDENT_GENDER,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    sd.STUDENT_STUDY_TYPE_ID,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    sd.STUDENT_UNIVERSITY_TYPE_ID,
    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'') AS STUDENT_UNIVERSITY_TYPE_NAME,
    sd.STUDENT_AGENCY_TYPE_ID,
    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'') AS STUDENT_AGENCY_TYPE_NAME,
    sd.STUDENT_MINISTRY_TYPE_ID,
    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'') AS STUDENT_MINISTRY_TYPE_NAME,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_DELEGATION_DECISION_YEAR,
    sd.STUDENT_ADDED_QUARTER,
    CAST(sd.STUDENT_PASSPORT_ISSUE_DATE AS DATE)  AS STUDENT_PASSPORT_ISSUE_DATE,
    CAST(sd.STUDENT_PASSPORT_EXPIRE_DATE AS DATE) AS STUDENT_PASSPORT_EXPIRE_DATE
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_COUNTRY          sc   ON sc.STUDENT_COUNTRY_ID          = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_GENDER           sg   ON sg.STUDENT_GENDER_ID           = sd.STUDENT_GENDER
LEFT JOIN MOHECORE.STUDENT_MAJOR            sm   ON sm.STUDENT_MAJOR_ID            = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE           sdeg ON sdeg.STUDENT_DEGREE_ID         = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL    scl  ON scl.STUDENT_CURRENT_LEVEL_ID   = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE       sst  ON sst.STUDENT_STUDY_TYPE_ID      = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS     sss  ON sss.STUDENT_STUDY_STATUS_ID    = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE  sut  ON sut.STUDENT_UNIVERSITY_TYPE_ID = sd.STUDENT_UNIVERSITY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE      sat  ON sat.STUDENT_AGENCY_TYPE_ID     = sd.STUDENT_AGENCY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE    smt  ON smt.STUDENT_MINISTRY_TYPE_ID   = sd.STUDENT_MINISTRY_TYPE_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

CREATE OR ALTER VIEW MOHECORE.VW_STUDENT_ENROLLMENT_BY_COUNTRY AS
SELECT
    sd.STUDY_COUNTRY_IN AS StudyCountryInId,
    sci.STUDY_COUNTRY_IN_NAME AS CountryName,
    COUNT(*) AS StudentCount,
    SUM(CASE WHEN sss.CODE = 'ACTIVE' THEN 1 ELSE 0 END) AS ActiveStudents,
    CAST(AVG(CAST(ISNULL(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18,2))) AS DECIMAL(18,2)) AS AvgAmount
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN     sci ON sci.STUDY_COUNTRY_IN_ID     = sd.STUDY_COUNTRY_IN
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_FINANCE      sf  ON sf.STUDENT_ID = sd.STUDENT_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDY_COUNTRY_IN, sci.STUDY_COUNTRY_IN_NAME;
GO

/* ── 3. Verification ─────────────────────────────────────────────────────── */
PRINT '=== V075 verification ===';
SELECT v.name AS view_name, c.name AS column_still_present
  FROM sys.views v
  JOIN sys.columns c ON c.object_id = v.object_id
 WHERE v.schema_id = SCHEMA_ID('MOHECORE')
   AND v.name IN ('V_Annual_Report', 'V_Monthly_Report', 'V_Quarterly_Report', 'V_Program_Catalog',
                  'V_Student_Distribution_University', 'V_University_Agreement', 'V_University_Partnership',
                  'V_University_Performance', 'V_Academic_Performance', 'V_Student_List_Detailed',
                  'VW_STUDENT_ENROLLMENT_BY_COUNTRY')
   AND c.name IN ('graduated_students', 'total_graduated', 'graduation_rate', 'is_graduated', 'GPA',
                  'program_status', 'program_start_date', 'last_enrollment_date', 'scholarship_slots',
                  'agreement_start_date', 'last_activity_date', 'agreement_duration_years',
                  'scholarship_allocations', 'total_financial_commitment', 'agreement_status',
                  'operational_status', 'partnership_start_date', 'partnership_duration_years',
                  'GraduatedStudents');

SELECT 'annual' AS v, COUNT(*) AS rows_, SUM(CASE WHEN scholarship_students + regular_students > total_students THEN 1 ELSE 0 END) AS over_count
  FROM MOHECORE.V_Annual_Report
UNION ALL SELECT 'quarterly', COUNT(*), SUM(CASE WHEN scholarship_students + regular_students > total_students THEN 1 ELSE 0 END)
  FROM MOHECORE.V_Quarterly_Report
UNION ALL SELECT 'distribution', COUNT(*), SUM(CASE WHEN scholarship_students + regular_students > total_students
                                                    OR bachelors_students + masters_students + phd_students > total_students THEN 1 ELSE 0 END)
  FROM MOHECORE.V_Student_Distribution_University
UNION ALL SELECT 'partnership', COUNT(*), SUM(CASE WHEN scholarship_students + regular_students > total_students THEN 1 ELSE 0 END)
  FROM MOHECORE.V_University_Partnership
UNION ALL SELECT 'performance', COUNT(*), SUM(CASE WHEN retention_rate > 100 THEN 1 ELSE 0 END)
  FROM MOHECORE.V_University_Performance;
GO
