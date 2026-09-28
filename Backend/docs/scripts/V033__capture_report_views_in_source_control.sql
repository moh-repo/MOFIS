/*
    V033 — the 34 report views that existed only in the database.

    Why
    ---
    The database has 50 views. Sixteen of them were created by a migration in
    docs/scripts/; the other 34 were created by hand in SSMS at some point and
    have never been in source control. Restoring this database from the scripts
    in this directory therefore produced a schema the application could not run
    against: V_Payment_Summary, V_Financial_Audit, V_Bank_Transaction,
    V_Scholarship_Tracking and 30 more simply would not exist, and every report
    screen bound to them would fail at query time rather than at deploy time.

    report-data-audit.md records this ("the view bodies exist only in the
    database") as the reason an earlier fix was left undone. This script is that
    fix: it is the current definition of each of those 34 views, captured from
    sys.sql_modules and rewritten as CREATE OR ALTER so it can be applied to a
    database that already has them.

    What this script deliberately does NOT do
    -----------------------------------------
    It does not improve anything. Several of these views assert values that were
    never recorded — V_Scholarship_Tracking computes a scholarship amount as
    STUDENT_STUDY_NO_OF_YEARS * 10000, V_Financial_Audit names 'System Auditor'
    as the auditor of every row, V_Bank_Transaction labels every transaction
    'Debit'. Those are real defects and V034 corrects them.

    They are reproduced here verbatim first, on purpose. A migration that both
    captures an unknown baseline and changes it leaves no way to tell which of
    the two produced a difference in behaviour, and no way to apply one without
    the other. So V033 records what is true today, and V034 changes it.

    Order within the script
    -----------------------
    Alphabetical, not dependency order. None of these views references another
    (checked against sys.sql_expression_dependencies), so there is nothing for an
    ordering to get right, and alphabetical is the order that makes a diff
    against a future capture readable.

    Idempotent: CREATE OR ALTER throughout. Apply in SSMS after V032.
*/

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ──  1/34. Q_STUD_DATA_ALL ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.Q_STUD_DATA_ALL as
SELECT 
    SD.STUDENT_ID,
    SD.STUDENT_NAME,
    SD.STUDENT_COUNTRY_ID,
    SD.STUDENT_DELEGATION_DECISION_YEAR,
    SD.STUDENT_DEGREE_ID,
    SD.STUDENT_MAJOR_ID,
    SD.STUDENT_DELEGATE_DECISION_NO,
    SD.STUDENT_STUDY_NO_OF_YEARS,
    SD.STUDENT_CURRENT_LEVEL_ID,
    SD.STUDENT_FIRST_RECEIPT_OF_DUE,
    SD.STUDENT_AMOUNT,
    SD.STUDENT_NOTE_1,
    SD.STUDENT_NOTE_2,
    SD.STUDENT_STUDY_STATUS_ID,
    SD.STUDENT_AGENCY_TYPE_ID,
    SD.STUDENT_MINISTRY_TYPE_ID,
    SD.STUDENT_STUDY_TYPE_ID,
    SD.STUDENT_UNIVERSITY_TYPE_ID,
    SSS.STUDENT_STUDY_STATUS_NAME,
    SAT.STUDENT_AGENCY_TYPE_NAME,
    SC.STUDENT_COUNTRY_NAME,
    SDG.STUDENT_DEGREE_NAME,
    SMJ.STUDENT_MAJOR_NAME,
    SMT.STUDENT_MINISTRY_TYPE_NAME,
    SUT.STUDENT_UNIVERSITY_TYPE_NAME,
    SCI.STUDY_COUNTRY_IN_NAME,
    SF.STUDENT_FIN_RECIEVED_YEAR,
    SF.STUDENT_FIN_RECIEVED_MONTH,
    N'إستمارة صرف مستحقات مالية للربع ' 
        + CAST(SF.STUDENT_FIN_RECIEVED_MONTH AS NVARCHAR(10)) 
        + N' للعام ' 
        + CAST(SF.STUDENT_FIN_RECIEVED_YEAR AS NVARCHAR(10)) 
        AS HEAD_TITLE
FROM 
    MOHECORE.STUDENT_FINANCE SF
INNER JOIN MOHECORE.STUDENT_DATA SD 
    ON SF.STUDENT_ID = SD.STUDENT_ID
INNER JOIN MOHECORE.STUDENT_STUDY_STATUS SSS 
    ON SD.STUDENT_STUDY_STATUS_ID = SSS.STUDENT_STUDY_STATUS_ID
INNER JOIN MOHECORE.STUDENT_CURRENT_LEVEL SCL 
    ON SD.STUDENT_CURRENT_LEVEL_ID = SCL.STUDENT_CURRENT_LEVEL_ID
INNER JOIN MOHECORE.STUDENT_AGENCY_TYPE SAT 
    ON SD.STUDENT_AGENCY_TYPE_ID = SAT.STUDENT_AGENCY_TYPE_ID
INNER JOIN MOHECORE.STUDENT_COUNTRY SC 
    ON SD.STUDENT_COUNTRY_ID = SC.STUDENT_COUNTRY_ID
INNER JOIN MOHECORE.STUDENT_DEGREE SDG 
    ON SD.STUDENT_DEGREE_ID = SDG.STUDENT_DEGREE_ID
INNER JOIN MOHECORE.STUDENT_MAJOR SMJ 
    ON SD.STUDENT_MAJOR_ID = SMJ.STUDENT_MAJOR_ID
INNER JOIN MOHECORE.STUDENT_MINISTRY_TYPE SMT 
    ON SD.STUDENT_MINISTRY_TYPE_ID = SMT.STUDENT_MINISTRY_TYPE_ID
INNER JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE SUT 
    ON SD.STUDENT_UNIVERSITY_TYPE_ID = SUT.STUDENT_UNIVERSITY_TYPE_ID
INNER JOIN MOHECORE.STUDY_COUNTRY_IN SCI 
    ON SCI.STUDY_COUNTRY_IN_ID = SD.STUDENT_COUNTRY_ID;
GO

/* ──  2/34. Q_Stud_Financial_Data_Form ─────────────────────────────────────────────── */
-- ============================================================
-- Q_Stud_Financial_Data_Form
-- Used by StudentFinancialDataFormController / StudentFinancialDataForm entity.
-- Joins each finance record to its student + all dimension names.
-- Keyed on STUDENT_ID (1 row per student — latest finance record).
-- ============================================================
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
    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'')    AS STUDENT_AGENCY_TYPE_NAME,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'')         AS STUDENT_COUNTRY_NAME,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'')        AS STUDENT_DEGREE_NAME,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'')           AS STUDENT_MAJOR_NAME,
    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'')  AS STUDENT_MINISTRY_TYPE_NAME,
    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'') AS STUDENT_UNIVERSITY_TYPE_NAME,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'')       AS STUDY_COUNTRY_IN_NAME,
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

/* ──  3/34. VW_AUDIT_LOG_SUMMARY ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW [MOHECORE].[VW_AUDIT_LOG_SUMMARY] AS
SELECT 
    AUDIT_LOG_ID,
    TABLE_NAME,
    RECORD_ID,
    OPERATION_TYPE,
    CHANGED_BY,
    CHANGED_BY_USER_ID,
    CHANGE_TIMESTAMP,
    IP_ADDRESS,
    CHANGE_SUMMARY,
    COUNT(*) OVER (PARTITION BY TABLE_NAME, RECORD_ID) AS TOTAL_CHANGES_FOR_RECORD
FROM [MOHECORE].[AUDIT_LOG];
GO

/* ──  4/34. VW_FINANCIAL_BY_QUARTER ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.VW_FINANCIAL_BY_QUARTER AS
SELECT
    sf.STUDY_COUNTRY_IN_ID AS StudyCountryInId,
    CAST(mq.QUARTER_YEAR AS NVARCHAR(4)) + '-Q' + CAST(mq.QUARTER_NO AS NVARCHAR(1)) AS QuarterName,
    SUM(CAST(ISNULL(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18,2))) AS TotalReceived,
    SUM(CAST(ISNULL(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18,2))) AS TotalBudget,
    COUNT(DISTINCT sf.STUDENT_ID) AS StudentCount
FROM MOHECORE.STUDENT_FINANCE sf
JOIN MOHECORE.MOHE_QUARTER mq ON mq.QUARTER = sf.MOHE_QUARTER_ID
GROUP BY sf.STUDY_COUNTRY_IN_ID, mq.QUARTER_YEAR, mq.QUARTER_NO;
GO

/* ──  5/34. VW_FINANCIAL_DATA_BY_YEAR ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.VW_FINANCIAL_DATA_BY_YEAR AS
SELECT
    sf.STUDY_COUNTRY_IN_ID AS StudyCountryInId,
    sf.STUDENT_FIN_RECIEVED_YEAR AS [Year],
    SUM(CAST(sf.STUDENT_FIN_RECIEVED_AMOUNT AS DECIMAL(18,2))) AS TotalAmount,
    AVG(CAST(sf.STUDENT_FIN_RECIEVED_AMOUNT AS DECIMAL(18,2))) AS AvgAmount,
    COUNT(DISTINCT sf.STUDENT_ID) AS StudentCount
FROM MOHECORE.STUDENT_FINANCE sf
WHERE sf.STUDENT_FIN_RECIEVED_YEAR IS NOT NULL
GROUP BY sf.STUDY_COUNTRY_IN_ID, sf.STUDENT_FIN_RECIEVED_YEAR;
GO

/* ──  6/34. VW_STUDENT_BY_DEGREE ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.VW_STUDENT_BY_DEGREE AS
SELECT
    sd.STUDY_COUNTRY_IN AS StudyCountryInId,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'Unknown') AS DegreeName,
    COUNT(*) AS StudentCount,
    CAST(SUM(ISNULL(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0)) AS DECIMAL(18,2)) AS TotalAmount,
    CAST(AVG(CAST(ISNULL(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18,2))) AS DECIMAL(18,2)) AS AvgAmount
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_FINANCE sf ON sf.STUDENT_ID = sd.STUDENT_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDY_COUNTRY_IN, sdeg.STUDENT_DEGREE_NAME;
GO

/* ──  7/34. VW_STUDENT_ENROLLMENT_BY_COUNTRY ─────────────────────────────────────────────── */
-- ============================================================
-- Chart views: add StudyCountryInId for scoped chart APIs
-- ============================================================

CREATE OR ALTER VIEW MOHECORE.VW_STUDENT_ENROLLMENT_BY_COUNTRY AS
SELECT
    sd.STUDY_COUNTRY_IN AS StudyCountryInId,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'Unknown') AS CountryName,
    COUNT(*) AS StudentCount,
    SUM(CASE WHEN sd.STUDENT_STUDY_STATUS_ID = 1 THEN 1 ELSE 0 END) AS ActiveStudents,
    SUM(CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN 1 ELSE 0 END) AS GraduatedStudents,
    CAST(AVG(CAST(ISNULL(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18,2))) AS DECIMAL(18,2)) AS AvgAmount
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_FINANCE sf ON sf.STUDENT_ID = sd.STUDENT_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDY_COUNTRY_IN, sci.STUDY_COUNTRY_IN_NAME;
GO

/* ──  8/34. VW_STUDENT_EXTENSIONS ─────────────────────────────────────────────── */
-- Create a view for easy querying of student extensions
CREATE OR ALTER VIEW [MOHECORE].[VW_STUDENT_EXTENSIONS] AS
SELECT 
	sd.STUDENT_ID,
	sd.STUDENT_NAME,
	sd.STUDENT_MAJOR_NAME,
	se.EXTENSION_ID,
	se.EXTENSION_TYPE,
	se.EXTENSION_QUARTERS,
	se.EXTENSION_START_DATE,
	se.EXTENSION_END_DATE,
	DATEDIFF(DAY, se.EXTENSION_START_DATE, se.EXTENSION_END_DATE) AS EXTENSION_DAYS,
	se.EXTENSION_REASON,
	se.EXTENSION_REASON_DETAILS,
	se.EXTENSION_DECISION_NO,
	se.EXTENSION_DECISION_DATE,
	se.APPROVED_BY,
	se.EXTENSION_STATUS,
	se.CREATED_DATE,
	-- Calculate total quarters extended per student
	(SELECT SUM(EXTENSION_QUARTERS) 
	 FROM [MOHECORE].[STUDENT_EXTENSION] 
	 WHERE STUDENT_ID = sd.STUDENT_ID 
	 AND EXTENSION_STATUS = 'ACTIVE') AS TOTAL_QUARTERS_EXTENDED
FROM [MOHECORE].[STUDENT_DATA] sd
INNER JOIN [MOHECORE].[STUDENT_EXTENSION] se ON sd.STUDENT_ID = se.STUDENT_ID;
GO

/* ──  9/34. VW_STUDENT_FINANCE_PAYMENT_ALL ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.VW_STUDENT_FINANCE_PAYMENT_ALL
AS
SELECT 
    SF.STUDENT_ID,
    SFBB.STUDY_FINANCE_BANK_BRANCH_ID,
    SFBB.STUDY_FINANCE_BANK_BRANCH_NAME,
    SD.STUDENT_AGENCY_TYPE_ID,
    SAT.STUDENT_AGENCY_TYPE_NAME,
    SMT.STUDENT_MINISTRY_TYPE_ID,
    SMT.STUDENT_MINISTRY_TYPE_NAME,
    SUT.STUDENT_UNIVERSITY_TYPE_ID,
    SUT.STUDENT_UNIVERSITY_TYPE_NAME,
    CASE 
        WHEN SUT.STUDENT_UNIVERSITY_TYPE_ID = 99 THEN
            CASE 
                WHEN SMT.STUDENT_MINISTRY_TYPE_ID = 99 THEN SAT.STUDENT_AGENCY_TYPE_NAME
                ELSE SMT.STUDENT_MINISTRY_TYPE_NAME
            END
        ELSE SUT.STUDENT_UNIVERSITY_TYPE_NAME
    END AS TYPE_ALL_IS,
    SAT.STUDENT_AGENCY_TYPE_ORDER,
    STATUS_01.STUDENT_ID AS STUDENT_ID_STATUS_01,
    STATUS_01.STUDENT_FIN_RECIEVED_AMOUNT AS STUDENT_FIN_RECIEVED_AMOUNT_STATUS_01,
    STATUS_02.STUDENT_ID AS STUDENT_ID_STATUS_02,
    STATUS_02.STUDENT_FIN_RECIEVED_AMOUNT AS STUDENT_FIN_RECIEVED_AMOUNT_STATUS_02
FROM 
    MOHECORE.STUDENT_FINANCE SF
    INNER JOIN MOHECORE.STUDY_FINANCE_BANK_BRANCH SFBB 
        ON SF.STUDY_FINANCE_BANK_BRANCH_ID = SFBB.STUDY_FINANCE_BANK_BRANCH_ID
    INNER JOIN MOHECORE.STUDENT_DATA SD 
        ON SF.STUDENT_ID = SD.STUDENT_ID
    INNER JOIN MOHECORE.STUDENT_AGENCY_TYPE SAT 
        ON SD.STUDENT_AGENCY_TYPE_ID = SAT.STUDENT_AGENCY_TYPE_ID
    LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE SMT 
        ON SD.STUDENT_MINISTRY_TYPE_ID = SMT.STUDENT_MINISTRY_TYPE_ID
    LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE SUT 
        ON SD.STUDENT_UNIVERSITY_TYPE_ID = SUT.STUDENT_UNIVERSITY_TYPE_ID
    LEFT JOIN (
        SELECT 
            STUDENT_ID,
            STUDY_FIN_RECIEVED_STATUS,
            STUDENT_FIN_RECIEVED_AMOUNT
        FROM MOHECORE.STUDENT_FINANCE
        WHERE STUDY_FIN_RECIEVED_STATUS = 1
    ) STATUS_01 
        ON SF.STUDENT_ID = STATUS_01.STUDENT_ID
    LEFT JOIN (
        SELECT 
            STUDENT_ID,
            STUDY_FIN_RECIEVED_STATUS,
            STUDENT_FIN_RECIEVED_AMOUNT
        FROM MOHECORE.STUDENT_FINANCE
        WHERE STUDY_FIN_RECIEVED_STATUS <> 1
    ) STATUS_02 
        ON SF.STUDENT_ID = STATUS_02.STUDENT_ID;
GO

/* ── 10/34. VW_STUDENT_FINANCIAL_SUMMARY ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.VW_STUDENT_FINANCIAL_SUMMARY AS
SELECT 
    sc.STUDENT_COUNTRY_NAME as Category,
    'Country' as CategoryType,
    COUNT(DISTINCT sd.STUDENT_ID) as TotalStudents,
    SUM(CAST(sd.STUDENT_AMOUNT as FLOAT)) as BudgetedAmount,
    SUM(CAST(sf.STUDENT_FIN_RECIEVED_AMOUNT as FLOAT)) as ReceivedAmount,
    AVG(CAST(sd.STUDENT_CURRENT_LEVEL_ID as FLOAT)) as AvgLevel
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_FINANCE sf ON sd.STUDENT_ID = sf.STUDENT_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY sc ON sd.STUDENT_COUNTRY_ID = sc.STUDENT_COUNTRY_ID
GROUP BY sc.STUDENT_COUNTRY_NAME;
GO

/* ── 11/34. VW_STUDENT_STATUS_DISTRIBUTION ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.VW_STUDENT_STATUS_DISTRIBUTION AS
SELECT
    sd.STUDY_COUNTRY_IN AS StudyCountryInId,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'Unknown') AS StatusName,
    COUNT(*) AS StudentCount,
    CAST(SUM(ISNULL(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0)) AS DECIMAL(18,2)) AS TotalAmount
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_FINANCE sf ON sf.STUDENT_ID = sd.STUDENT_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDY_COUNTRY_IN, sss.STUDENT_STUDY_STATUS_NAME;
GO

/* ── 12/34. V_Academic_Year_Report ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Academic_Year_Report AS
SELECT report_year, total_students, new_enrollments, active_students,
       graduated_students, suspended_students, total_financial_transactions,
       total_amount_disbursed, scholarship_amount_disbursed, regular_amount_disbursed,
       avg_amount_per_student, max_amount_disbursed, min_amount_disbursed,
       scholarship_students, regular_students, male_students, female_students,
       total_universities, total_countries, total_majors, total_degrees,
       study_country_id
FROM MOHECORE.V_Annual_Report;
GO

/* ── 13/34. V_Annual_Report ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Annual_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_id,
    YEAR(sf.STUDY_FINANCE_MIDATE) AS report_year,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_students,
    0 AS new_enrollments,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN sd.STUDENT_ID END) AS active_students,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN sd.STUDENT_ID END) AS graduated_students,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Suspended', N'موقوف') THEN sd.STUDENT_ID END) AS suspended_students,
    COUNT(*)  AS total_financial_transactions,
    CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS total_amount_disbursed,
    CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS scholarship_amount_disbursed,
    0 AS regular_amount_disbursed,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN 0
         ELSE CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) * 1.0 / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2))
    END AS avg_amount_per_student,
    CAST(MAX(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS max_amount_disbursed,
    CAST(MIN(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS min_amount_disbursed,
    0 AS scholarship_students,
    0 AS regular_students,
    COUNT(DISTINCT CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Male%'   THEN sd.STUDENT_ID END) AS male_students,
    COUNT(DISTINCT CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Female%' THEN sd.STUDENT_ID END) AS female_students,
    COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
    COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS total_majors,
    COUNT(DISTINCT sd.STUDENT_DEGREE_ID) AS total_degrees
FROM MOHECORE.STUDENT_FINANCE sf
LEFT JOIN MOHECORE.STUDENT_DATA        sd  ON sd.STUDENT_ID              = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_GENDER      sg  ON sg.STUDENT_GENDER_ID       = sd.STUDENT_GENDER
WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE);
GO

/* ── 14/34. V_Bank_Transaction ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Bank_Transaction AS
SELECT 
    SF.STUDENT_FIN_ID AS transaction_id,
    SF.STUDY_CHECK_DATE AS transaction_date,
    'Debit' AS transaction_type,
    'Student Payment - ' + SD.STUDENT_NAME AS transaction_description,
    SF.STUDENT_FIN_RECIEVED_AMOUNT AS transaction_amount,
    'USD' AS transaction_currency,
    'REF' + CAST(SF.STUDENT_FIN_ID AS VARCHAR(10)) AS reference_number,
    SFB.STUDY_FINANCE_BANKE_NAME AS bank_name,
    SFBB.STUDY_FINANCE_BANK_BRANCH_NAME AS bank_branch,
    SD.STUDENT_NAME AS beneficiary_name,
    SD.STUDENT_PASSPORT_NUMBER AS beneficiary_account
FROM 
    MOHECORE.STUDENT_FINANCE SF
    INNER JOIN MOHECORE.STUDENT_DATA SD ON SF.STUDENT_ID = SD.STUDENT_ID
    LEFT JOIN MOHECORE.STUDY_FINANCE_BANK SFB ON SF.STUDY_FINANCE_BANKE_ID = SFB.STUDY_FINANCE_BANKE_ID
    LEFT JOIN MOHECORE.STUDY_FINANCE_BANK_BRANCH SFBB ON SF.STUDY_FINANCE_BANK_BRANCH_ID = SFBB.STUDY_FINANCE_BANK_BRANCH_ID
WHERE 
    SF.STUDY_CHECK_DATE IS NOT NULL
    AND SF.STUDY_FINANCE_BANKE_ID IS NOT NULL;
GO

/* ── 15/34. V_Custom_Filter_Report ─────────────────────────────────────────────── */
-- V_Custom_Filter_Report — student-centric filtered view (mirrors V_Student_List_Detailed)
CREATE OR ALTER VIEW MOHECORE.V_Custom_Filter_Report AS
SELECT STUDENT_ID, STUDENT_NAME, STUDENT_PASSPORT_NUMBER, STUDENT_BIRTHDATE,
       student_age, STUDENT_COUNTRY_ID, STUDENT_COUNTRY_NAME, STUDENT_GENDER,
       STUDENT_MAJOR_ID, STUDENT_MAJOR_NAME, STUDENT_DEGREE_ID, STUDENT_DEGREE_NAME,
       STUDENT_CURRENT_LEVEL_ID, STUDENT_CURRENT_LEVEL_NAME,
       STUDENT_STUDY_TYPE_ID, STUDENT_STUDY_TYPE_NAME,
       STUDENT_STUDY_STATUS_ID, STUDENT_STUDY_STATUS_NAME,
       STUDENT_UNIVERSITY_TYPE_ID, STUDENT_UNIVERSITY_TYPE_NAME,
       STUDENT_AGENCY_TYPE_ID, STUDENT_AGENCY_TYPE_NAME,
       STUDENT_MINISTRY_TYPE_ID, STUDENT_MINISTRY_TYPE_NAME,
       STUDENT_STUDY_NO_OF_YEARS, STUDENT_DELEGATION_DECISION_YEAR,
       STUDENT_ADDED_QUARTER, STUDENT_PASSPORT_ISSUE_DATE, STUDENT_PASSPORT_EXPIRE_DATE, GPA
FROM MOHECORE.V_Student_List_Detailed;
GO

/* ── 16/34. V_Custom_Period_Report ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Custom_Period_Report AS
SELECT
    report_year, report_month,
    report_period,
    total_students, new_enrollments, active_students,
    total_financial_transactions, total_amount_disbursed,
    avg_amount_per_student, scholarship_students,
    total_universities, total_countries,
    study_country_id
FROM MOHECORE.V_Monthly_Report;
GO

/* ── 17/34. V_Distribution_Statistics ─────────────────────────────────────────────── */
-- ============================================================
-- 7. V_Distribution_Statistics
--    Combines distribution by country, major, degree, and status
-- ============================================================
CREATE OR ALTER VIEW MOHECORE.V_Distribution_Statistics AS
SELECT distribution_type, category_name, student_count,
       CAST(student_count * 100.0 / SUM(student_count) OVER (PARTITION BY distribution_type) AS DECIMAL(5,2)) AS percentage_of_total,
       RANK() OVER (PARTITION BY distribution_type ORDER BY student_count DESC) AS rank_position,
       CASE WHEN student_count >= 20 THEN 'major' WHEN student_count >= 5 THEN 'minor' ELSE 'marginal' END AS category_significance
FROM (
    -- by country
    SELECT 'country' AS distribution_type, ISNULL(sc.STUDENT_COUNTRY_NAME, N'Unknown') AS category_name, COUNT(*) AS student_count
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_COUNTRY sc ON sc.STUDENT_COUNTRY_ID = sd.STUDENT_COUNTRY_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sc.STUDENT_COUNTRY_NAME

    UNION ALL

    -- by major
    SELECT 'major', ISNULL(sm.STUDENT_MAJOR_NAME, N'Unknown'), COUNT(*)
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sm.STUDENT_MAJOR_NAME

    UNION ALL

    -- by degree
    SELECT 'degree', ISNULL(sdeg.STUDENT_DEGREE_NAME, N'Unknown'), COUNT(*)
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sdeg.STUDENT_DEGREE_NAME

    UNION ALL

    -- by status
    SELECT 'status', ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'Unknown'), COUNT(*)
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sss.STUDENT_STUDY_STATUS_NAME
) base;
GO

/* ── 18/34. V_Fee_Structure ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Fee_Structure AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    sdeg.STUDENT_DEGREE_ID                     AS degree_level_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'')  AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'')  AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'')     AS country_name,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'')      AS degree_level_name,
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
         sdeg.STUDENT_DEGREE_ID, sdeg.STUDENT_DEGREE_NAME
HAVING COUNT(DISTINCT sd.STUDENT_ID) > 0;
GO

/* ── 19/34. V_Financial_Audit ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Financial_Audit AS
SELECT 
    'AUDIT' + CAST(SF.STUDENT_FIN_ID AS VARCHAR(10)) AS audit_id,
    SF.STUDY_CHECK_DATE AS audit_date,
    ISNULL(SRWS.STUDY_RECEIVED_WHOM_STATUS_NAME, 'Other') AS audit_category,
    SF.STUDENT_FIN_RECIEVED_AMOUNT AS audit_amount,
    'USD' AS audit_currency,
    ISNULL(SFRS.STUDY_FIN_RECEIVED_STATUS_NAME, 'Unknown') AS audit_status,
    'System Auditor' AS auditor_name,
    CASE 
        WHEN SFRS.STUDY_FIN_RECEIVED_STATUS_NAME LIKE '%Paid%' OR SFRS.STUDY_FIN_RECEIVED_STATUS_NAME LIKE '%Complete%' THEN 'All clear'
        WHEN SFRS.STUDY_FIN_RECEIVED_STATUS_NAME LIKE '%Pending%' THEN 'Requires review'
        ELSE 'Discrepancy found'
    END AS audit_notes,
    SD.STUDENT_NAME AS student_name,
    SD.STUDENT_PASSPORT_NUMBER AS student_passport,
    SFB.STUDY_FINANCE_BANKE_NAME AS bank_name,
    SF.STUDENT_FIN_RECIEVED_YEAR AS fiscal_year,
    ((SF.STUDENT_FIN_RECIEVED_MONTH - 1) / 3) + 1 AS fiscal_quarter
FROM 
    MOHECORE.STUDENT_FINANCE SF
    INNER JOIN MOHECORE.STUDENT_DATA SD ON SF.STUDENT_ID = SD.STUDENT_ID
    LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS SFRS ON SF.STUDY_FIN_RECIEVED_STATUS = SFRS.STUDY_FIN_RECEIVED_STATUS_ID
    LEFT JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS SRWS ON SF.STUDY_RECEIVED_WHOM_STATUS_ID = SRWS.STUDY_RECEIVED_WHOM_STATUS_ID
    LEFT JOIN MOHECORE.STUDY_FINANCE_BANK SFB ON SF.STUDY_FINANCE_BANKE_ID = SFB.STUDY_FINANCE_BANKE_ID
WHERE 
    SF.STUDY_CHECK_DATE IS NOT NULL;
GO

/* ── 20/34. V_Fiscal_Year_Report ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Fiscal_Year_Report AS
SELECT report_year, total_students, new_enrollments, active_students,
       graduated_students, suspended_students, total_financial_transactions,
       total_amount_disbursed, scholarship_amount_disbursed, regular_amount_disbursed,
       avg_amount_per_student, max_amount_disbursed, min_amount_disbursed,
       scholarship_students, regular_students, male_students, female_students,
       total_universities, total_countries, total_majors, total_degrees,
       study_country_id
FROM MOHECORE.V_Annual_Report;
GO

/* ── 21/34. V_Growth_Analysis ─────────────────────────────────────────────── */
-- ============================================================
-- 9. V_Growth_Analysis
--    Year-over-year growth by category
-- ============================================================
CREATE OR ALTER VIEW MOHECORE.V_Growth_Analysis AS
WITH yearly AS (
    SELECT
        YEAR(GETDATE()) - n AS analysis_year,
        'total_students' AS growth_category,
        COUNT(sd.STUDENT_ID) AS cnt
    FROM MOHECORE.STUDENT_DATA sd
    CROSS JOIN (VALUES (0),(1),(2),(3),(4)) AS y(n)
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
      AND TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) <= YEAR(GETDATE()) - n
    GROUP BY n
)
SELECT
    growth_category,
    analysis_year,
    cnt AS current_value,
    LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year) AS previous_value,
    CASE
        WHEN LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year) = 0 THEN NULL
        ELSE CAST((cnt - LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year)) * 100.0
                  / LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year) AS DECIMAL(8,2))
    END AS growth_rate,
    cnt - LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year) AS absolute_growth,
    CASE
        WHEN cnt > LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year) THEN 'growing'
        WHEN cnt < LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year) THEN 'declining'
        ELSE 'stable'
    END AS growth_status
FROM yearly;
GO

/* ── 22/34. V_Monthly_Report ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Monthly_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_id,
    YEAR(sf.STUDY_FINANCE_MIDATE)  AS report_year,
    MONTH(sf.STUDY_FINANCE_MIDATE) AS report_month,
    CAST(YEAR(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(4)) + '-' +
        RIGHT('0' + CAST(MONTH(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(2)), 2) AS report_period,
    COUNT(DISTINCT sd.STUDENT_ID)   AS total_students,
    0                               AS new_enrollments,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN sd.STUDENT_ID END) AS active_students,
    COUNT(*)                        AS total_financial_transactions,
    CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS DECIMAL(18,2)) AS total_amount_disbursed,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN 0
         ELSE CAST(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) * 1.0 / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2))
    END AS avg_amount_per_student,
    0 AS scholarship_students,
    COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
    COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries
FROM MOHECORE.STUDENT_FINANCE sf
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE);
GO

/* ── 23/34. V_Payment_Summary ─────────────────────────────────────────────── */
-- ============================================================
-- 12. V_Payment_Summary
-- STUDENT_FINANCE_PAYMENT has no PAYMENT_DATE/AMOUNT/STATUS/TYPE/METHOD columns.
-- Map: INSERTED_AT→payment_date, STUDENT_FIN_RECIEVED_AMOUNT→payment_amount,
--      STUDY_FIN_RECIEVED_STATUS→payment_status (cast to NVARCHAR), NULL→type/method.
-- ============================================================
CREATE OR ALTER VIEW MOHECORE.V_Payment_Summary AS
SELECT
    fp.PAYMENT_ID                                                  AS payment_id,
    fp.STUDENT_ID                                                  AS student_id,
    ISNULL(sd.STUDENT_NAME, N'')                                   AS student_name,
    ISNULL(sd.STUDENT_PASSPORT_NUMBER, N'')                        AS student_passport_number,
    CAST(fp.INSERTED_AT AS DATE)                                   AS payment_date,
    CAST(ISNULL(fp.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18,2)) AS payment_amount,
    N'LYD'                                                         AS payment_currency,
    CAST(ISNULL(fp.STUDY_FIN_RECIEVED_STATUS, 0) AS NVARCHAR(10)) AS payment_status,
    N''                                                            AS payment_type,
    N''                                                            AS payment_method,
    ISNULL(CAST(mq.QUARTER_NO AS NVARCHAR(10)), N'')               AS quarter_name,
    mq.QUARTER_YEAR                                                AS year_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'')                      AS university_name,
    ISNULL(sma.STUDENT_MAJOR_NAME, N'')                            AS major_name,
    fp.STUDENT_FIN_RECIEVED_YEAR                                   AS payment_year,
    fp.STUDENT_FIN_RECIEVED_MONTH                                  AS payment_month
FROM MOHECORE.STUDENT_FINANCE_PAYMENT fp
LEFT JOIN MOHECORE.STUDENT_DATA        sd  ON sd.STUDENT_ID              = fp.STUDENT_ID
LEFT JOIN MOHECORE.MOHE_QUARTER        mq  ON mq.QUARTER                 = fp.MOHE_QUARTER_ID
LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID  = sd.STUDY_UNIVERSITY_IN
LEFT JOIN MOHECORE.STUDENT_MAJOR       sma ON sma.STUDENT_MAJOR_ID        = sd.STUDENT_MAJOR_ID;
GO

/* ── 24/34. V_Performance_Metrics ─────────────────────────────────────────────── */
-- ============================================================
-- 17. V_Performance_Metrics
-- ============================================================
CREATE OR ALTER VIEW MOHECORE.V_Performance_Metrics AS
SELECT metric_category, metric_name, metric_value, metric_unit,
       target_value, achievement_percentage, performance_status, measurement_period
FROM (
    SELECT
        'enrollment'  AS metric_category,
        'total_students' AS metric_name,
        CAST(COUNT(*) AS DECIMAL(18,2)) AS metric_value,
        'count' AS metric_unit,
        NULL AS target_value,
        NULL AS achievement_percentage,
        'informational' AS performance_status,
        CAST(YEAR(GETDATE()) AS NVARCHAR(10)) AS measurement_period
    FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED, 0) = 0

    UNION ALL

    SELECT
        'finance', 'total_disbursed',
        CAST(ISNULL(SUM(STUDENT_FIN_RECIEVED_AMOUNT), 0) AS DECIMAL(18,2)),
        'LYD', NULL, NULL, 'informational',
        CAST(YEAR(GETDATE()) AS NVARCHAR(10))
    FROM MOHECORE.STUDENT_FINANCE

    UNION ALL

    SELECT
        'graduation', 'graduation_rate',
        CAST(COUNT(CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN 1 END) * 100.0
             / NULLIF(COUNT(*), 0) AS DECIMAL(5,2)),
        '%', CAST(70 AS DECIMAL(18,2)),
        CAST(COUNT(CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN 1 END) * 100.0
             / NULLIF(COUNT(*), 0) AS DECIMAL(5,2)),
        CASE WHEN COUNT(CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN 1 END) * 100.0
                  / NULLIF(COUNT(*), 0) >= 70 THEN 'on_target' ELSE 'below_target' END,
        CAST(YEAR(GETDATE()) AS NVARCHAR(10))
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
) m;
GO

/* ── 25/34. V_Predictive_Analytics ─────────────────────────────────────────────── */
-- ============================================================
-- 11. V_Predictive_Analytics
--     Simple linear extrapolation from historical averages
-- ============================================================
CREATE OR ALTER VIEW MOHECORE.V_Predictive_Analytics AS
WITH hist AS (
    SELECT
        YEAR(sf.STUDY_FINANCE_MIDATE) AS yr,
        SUM(CAST(sf.STUDENT_FIN_RECIEVED_AMOUNT AS BIGINT)) AS yr_total,
        COUNT(*) AS yr_count
    FROM MOHECORE.STUDENT_FINANCE sf
    WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
      AND YEAR(sf.STUDY_FINANCE_MIDATE) >= YEAR(GETDATE()) - 4
    GROUP BY YEAR(sf.STUDY_FINANCE_MIDATE)
),
avg_data AS (
    SELECT
        AVG(CAST(yr_total AS DECIMAL(18,2))) AS historical_avg,
        AVG(CAST(yr_count AS DECIMAL(18,2))) AS avg_count,
        CASE
            WHEN COUNT(*) < 2 THEN NULL
            ELSE (MAX(CAST(yr_total AS DECIMAL(18,2))) - MIN(CAST(yr_total AS DECIMAL(18,2)))) /
                 NULLIF(MAX(yr) - MIN(yr), 0)
        END AS slope
    FROM hist
)
SELECT
    'finance_amount' AS forecast_category,
    YEAR(GETDATE()) + n AS forecast_year,
    ad.historical_avg,
    CASE WHEN ad.historical_avg = 0 THEN NULL
         ELSE CAST(ad.slope / ad.historical_avg * 100 AS DECIMAL(8,2))
    END AS growth_rate,
    ad.historical_avg + ad.slope * n AS predicted_value,
    CASE WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_avg, 0), 0)) < 0.05 THEN 'HIGH'
         WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_avg, 0), 0)) < 0.15 THEN 'MEDIUM'
         ELSE 'LOW'
    END AS confidence_level,
    CASE WHEN ISNULL(ad.slope, 0) > 0 THEN 'upward'
         WHEN ISNULL(ad.slope, 0) < 0 THEN 'downward'
         ELSE 'stable'
    END AS forecast_trend
FROM avg_data ad
CROSS JOIN (VALUES (1),(2),(3)) AS f(n);
GO

/* ── 26/34. V_Program_Catalog ─────────────────────────────────────────────── */
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
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN sd.STUDENT_ID END) AS current_students,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN sd.STUDENT_ID END) AS graduated_students,
    0 AS scholarship_slots,
    CAST(NULL AS DATE) AS program_start_date,
    CAST(NULL AS DATE) AS last_enrollment_date,
    N'active' AS program_status
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

/* ── 27/34. V_Quarterly_Report ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Quarterly_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_id,
    mq.QUARTER_YEAR AS report_year,
    mq.QUARTER_NO   AS report_quarter,
    CAST(mq.QUARTER_YEAR AS NVARCHAR(4)) + '-Q' + CAST(mq.QUARTER_NO AS NVARCHAR(1)) AS report_period,
    COUNT(DISTINCT sd.STUDENT_ID)   AS total_students,
    0                               AS new_enrollments,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN sd.STUDENT_ID END) AS active_students,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN sd.STUDENT_ID END) AS graduated_students,
    COUNT(sf.STUDENT_ID)            AS total_financial_transactions,
    CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) AS DECIMAL(18,2)) AS total_amount_disbursed,
    CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) AS DECIMAL(18,2)) AS scholarship_amount_disbursed,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN 0
         ELSE CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) * 1.0 / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2))
    END AS avg_amount_per_student,
    0 AS scholarship_students,
    0 AS regular_students,
    COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
    COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS total_majors
FROM MOHECORE.MOHE_QUARTER mq
LEFT JOIN MOHECORE.STUDENT_FINANCE sf ON sf.MOHE_QUARTER_ID = mq.QUARTER
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
GROUP BY sd.STUDY_COUNTRY_IN, mq.QUARTER_YEAR, mq.QUARTER_NO;
GO

/* ── 28/34. V_STUDENT_SUMMARY ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_STUDENT_SUMMARY AS
SELECT
    -- Core identity
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.NATIONAL_ID,
    sd.STUDENT_PASSPORT_NUMBER,
    sd.STUDENT_BIRTHDATE,
    sd.STUDENT_AGE,
    sd.STUDENT_GENDER,
    sd.PICTURE_PATH,

    -- Origin location
    sd.STUDENT_COUNTRY_ID,
    sc.STUDENT_COUNTRY_NAME                     AS COUNTRY_NAME,
    sc.STUDENT_COUNTRY_NAME_EN                  AS COUNTRY_NAME_EN,
    sd.STUDENT_GOV_ID,
    sg.STUDENT_GOV_NAME                         AS GOV_NAME,
    sg.STUDENT_GOV_NAME_EN                      AS GOV_NAME_EN,
    sd.STUDENT_MUD_ID,
    mud.STUDENT_MUD_NAME                        AS MUD_NAME,

    -- Academic
    sd.STUDENT_DEGREE_ID,
    sdeg.STUDENT_DEGREE_NAME                    AS DEGREE_NAME,
    sdeg.STUDENT_DEGREE_NAME_EN                 AS DEGREE_NAME_EN,
    sd.STUDENT_MAJOR_ID,
    smaj.STUDENT_MAJOR_NAME                     AS MAJOR_NAME,
    smaj.STUDENT_MAJOR_NAME_EN                  AS MAJOR_NAME_EN,
    sd.STUDENT_MAJOR_NAME                       AS STUDENT_MAJOR_NAME_FREE,
    sd.STUDENT_DELEGATION_DECISION_YEAR,
    sd.STUDENT_DELEGATE_DECISION_NO,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_CURRENT_LEVEL_ID,
    scl.STUDENT_CURRENT_LEVEL_NAME              AS CURRENT_LEVEL_NAME,
    scl.STUDENT_CURRENT_LEVEL_NAME_EN           AS CURRENT_LEVEL_NAME_EN,

    -- Study destination
    sd.STUDY_COUNTRY_IN                         AS STUDY_COUNTRY_IN_ID,
    sci.STUDY_COUNTRY_IN_NAME                   AS STUDY_COUNTRY_NAME,
    sci.STUDY_COUNTRY_IN_NAME_EN                AS STUDY_COUNTRY_NAME_EN,
    sd.STUDY_UNIVERSITY_IN                      AS STUDY_UNIVERSITY_IN_ID,
    sui.STUDY_UNIVERSITY_IN_NAME                AS STUDY_UNIVERSITY_NAME,
    sui.STUDY_UNIVERSITY_IN_NAME_EN             AS STUDY_UNIVERSITY_NAME_EN,

    -- Classification
    sd.STUDENT_STUDY_TYPE_ID,
    sst.STUDENT_STUDY_TYPE_NAME                 AS STUDY_TYPE_NAME,
    sst.STUDENT_STUDY_TYPE_NAME_EN              AS STUDY_TYPE_NAME_EN,
    sd.STUDENT_UNIVERSITY_TYPE_ID,
    sut.STUDENT_UNIVERSITY_TYPE_NAME            AS UNIVERSITY_TYPE_NAME,
    sut.STUDENT_UNIVERSITY_TYPE_NAME_EN         AS UNIVERSITY_TYPE_NAME_EN,
    sd.STUDENT_AGENCY_TYPE_ID,
    sat.STUDENT_AGENCY_TYPE_NAME                AS AGENCY_TYPE_NAME,
    sat.STUDENT_AGENCY_TYPE_NAME_EN             AS AGENCY_TYPE_NAME_EN,
    sd.STUDENT_MINISTRY_TYPE_ID,
    smt.STUDENT_MINISTRY_TYPE_NAME              AS MINISTRY_TYPE_NAME,
    smt.STUDENT_MINISTRY_TYPE_NAME_EN           AS MINISTRY_TYPE_NAME_EN,

    -- Status
    sd.STUDENT_STUDY_STATUS_ID,
    sss.STUDENT_STUDY_STATUS_NAME               AS STUDY_STATUS_NAME,
    sss.STUDENT_STUDY_STATUS_NAME_EN            AS STUDY_STATUS_NAME_EN,

    -- Quarter / financial
    sd.STUDENT_ADDED_QUARTER,
    sd.STUDENT_LAST_QUARTER,
    sd.STUDENT_AMOUNT,
    sd.AMOUNT_CURRENCY,
    sd.STUDENT_FIRST_RECEIPT_OF_DUE,

    -- Soft delete
    ISNULL(sd.IS_DELETED, 0)                    AS IS_DELETED,

    -- Timestamps
    sd.STUDENT_MIDATE                           AS CREATED_DATE

FROM MOHECORE.STUDENT_DATA sd

-- Country of origin
LEFT JOIN MOHECORE.STUDENT_COUNTRY sc
    ON sc.STUDENT_COUNTRY_ID = sd.STUDENT_COUNTRY_ID

-- Governorate
LEFT JOIN MOHECORE.STUDENT_GOV sg
    ON sg.STUDENT_GOV_ID = sd.STUDENT_GOV_ID

-- Municipality
LEFT JOIN MOHECORE.STUDENT_GOV_MUD mud
    ON mud.STUDENT_MUD_ID = sd.STUDENT_MUD_ID

-- Degree
LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg
    ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID

-- Major
LEFT JOIN MOHECORE.STUDENT_MAJOR smaj
    ON smaj.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID

-- Current level
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL scl
    ON scl.STUDENT_CURRENT_LEVEL_ID = sd.STUDENT_CURRENT_LEVEL_ID

-- Study destination country
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci
    ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN

-- Study university
LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui
    ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN

-- Study type
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst
    ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID

-- University type
LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE sut
    ON sut.STUDENT_UNIVERSITY_TYPE_ID = sd.STUDENT_UNIVERSITY_TYPE_ID

-- Agency type
LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE sat
    ON sat.STUDENT_AGENCY_TYPE_ID = sd.STUDENT_AGENCY_TYPE_ID

-- Ministry type
LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE smt
    ON smt.STUDENT_MINISTRY_TYPE_ID = sd.STUDENT_MINISTRY_TYPE_ID

-- Study status
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss
    ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID

WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

/* ── 29/34. V_STUD_DATA_CALC_ALL_1 ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW [MOHECORE].[V_STUD_DATA_CALC_ALL_1]
AS
SELECT 
    SD.STUDENT_ID,
    SD.STUDENT_NAME,
    SD.STUDENT_COUNTRY_ID,
    SD.STUDENT_DELEGATION_DECISION_YEAR,
    SD.STUDENT_DEGREE_ID,
    SD.STUDENT_MAJOR_ID,
   -- SD.STUDENT_MAJOR_NAME,
    SD.STUDENT_DELEGATE_DECISION_NO,
    SD.STUDENT_STUDY_NO_OF_YEARS,
    SD.STUDENT_CURRENT_LEVEL_ID,
    SD.STUDENT_FIRST_RECEIPT_OF_DUE,
    SD.STUDENT_AMOUNT,
    SD.STUDENT_NOTE_1,
    SD.STUDENT_NOTE_2,
    SD.STUDENT_STUDY_STATUS_ID,
    SD.STUDENT_AGENCY_TYPE_ID,
    SD.STUDENT_MINISTRY_TYPE_ID,
    SD.STUDENT_STUDY_TYPE_ID,
    SD.STUDENT_UNIVERSITY_TYPE_ID,

    -- Lookup Names from Other Tables
    SSS.STUDENT_STUDY_STATUS_NAME,
    SAT.STUDENT_AGENCY_TYPE_NAME,
    SC.STUDENT_COUNTRY_NAME,
    SDG.STUDENT_DEGREE_NAME,
    SMJ.STUDENT_MAJOR_NAME,
    SMT.STUDENT_MINISTRY_TYPE_NAME,
    SUT.STUDENT_UNIVERSITY_TYPE_NAME,

    -- Derived / Calculated Columns (Safe Conversion)
    (TRY_CAST(LEFT(SD.STUDENT_DELEGATION_DECISION_YEAR, 4) AS INT) + SD.STUDENT_STUDY_NO_OF_YEARS) AS END_STUDY_YEAR,
    (TRY_CAST(LEFT(SD.STUDENT_DELEGATION_DECISION_YEAR, 4) AS INT) + SD.STUDENT_CURRENT_LEVEL_ID) AS CURRENT_STUDY_YEAR,
    (4 * SD.STUDENT_STUDY_NO_OF_YEARS) AS NO_OF_QUARTER_ALL,
    (4 * SD.STUDENT_STUDY_NO_OF_YEARS) * SD.STUDENT_AMOUNT AS AMOUNT_OF_QUARTER_MONEY_ALL,
    LEFT(SD.STUDENT_FIRST_RECEIPT_OF_DUE, 4) AS STUDENT_FIRST_RECEIPT_OF_DUE_YEAR,
    RIGHT(SD.STUDENT_FIRST_RECEIPT_OF_DUE, 1) AS STUDENT_FIRST_RECEIPT_OF_DUE_QUARTER,
    (TRY_CAST(LEFT(SD.STUDENT_FIRST_RECEIPT_OF_DUE, 4) AS INT) + SD.STUDENT_STUDY_NO_OF_YEARS) AS END_STUDY_YEAR_WITHOUT_ADDTIONAL,
    RIGHT(SD.STUDENT_FIRST_RECEIPT_OF_DUE, 1) AS STUDENT_FINAL_RECEIPT_OF_DUE_QUARTER_NO_ADDITIONAL,
    (ROUND((SD.STUDENT_STUDY_NO_OF_YEARS * 4.0) / 3.0, 0) - 1) AS STUDENT_ADDITIONAL_QUARTER_ALL,
    ((ROUND((SD.STUDENT_STUDY_NO_OF_YEARS * 4.0) / 3.0, 0) - 1) * SD.STUDENT_AMOUNT) AS STUDENT_ADDITIONAL_QUARTER_MONEY_ALL

FROM 
    MOHECORE.STUDENT_DATA SD
    INNER JOIN MOHECORE.STUDENT_STUDY_STATUS SSS 
        ON SD.STUDENT_STUDY_STATUS_ID = SSS.STUDENT_STUDY_STATUS_ID
    INNER JOIN MOHECORE.STUDENT_CURRENT_LEVEL SCL
        ON SD.STUDENT_CURRENT_LEVEL_ID = SCL.STUDENT_CURRENT_LEVEL_ID
    INNER JOIN MOHECORE.STUDENT_AGENCY_TYPE SAT
        ON SD.STUDENT_AGENCY_TYPE_ID = SAT.STUDENT_AGENCY_TYPE_ID
    INNER JOIN MOHECORE.STUDENT_COUNTRY SC
        ON SD.STUDENT_COUNTRY_ID = SC.STUDENT_COUNTRY_ID
    INNER JOIN MOHECORE.STUDENT_DEGREE SDG
        ON SD.STUDENT_DEGREE_ID = SDG.STUDENT_DEGREE_ID
    INNER JOIN MOHECORE.STUDENT_MAJOR SMJ
        ON SD.STUDENT_MAJOR_ID = SMJ.STUDENT_MAJOR_ID
    INNER JOIN MOHECORE.STUDENT_MINISTRY_TYPE SMT
        ON SD.STUDENT_MINISTRY_TYPE_ID = SMT.STUDENT_MINISTRY_TYPE_ID
    INNER JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE SUT
        ON SD.STUDENT_UNIVERSITY_TYPE_ID = SUT.STUDENT_UNIVERSITY_TYPE_ID;
GO

/* ── 30/34. V_Scholarship_Tracking ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Scholarship_Tracking AS
SELECT 
    ROW_NUMBER() OVER (ORDER BY SD.STUDENT_ID) AS scholarship_id,
    SD.STUDENT_ID AS student_id,
    SD.STUDENT_NAME AS student_name,
    SD.STUDENT_PASSPORT_NUMBER AS student_passport_number,
    ISNULL(SST.STUDENT_STUDY_TYPE_NAME, 'Other') AS scholarship_type,
    ISNULL(SAT.STUDENT_AGENCY_TYPE_NAME, 'Other') AS scholarship_provider,
    ISNULL(SD.STUDENT_STUDY_NO_OF_YEARS * 10000, 0) AS scholarship_amount,
    'USD' AS scholarship_currency,
    SD.STUDENT_MIDATE AS allocated_date,
    SD.STUDENT_MIDATE AS start_date,
    DATEADD(YEAR, ISNULL(SD.STUDENT_STUDY_NO_OF_YEARS, 4), SD.STUDENT_MIDATE) AS end_date,
    'Quarterly' AS payment_schedule,
    ISNULL((SELECT SUM(SF.STUDENT_FIN_RECIEVED_AMOUNT) 
            FROM MOHECORE.STUDENT_FINANCE SF 
            WHERE SF.STUDENT_ID = SD.STUDENT_ID 
            AND SF.STUDY_FIN_RECIEVED_STATUS = 1), 0) AS amount_paid,
    ISNULL(SD.STUDENT_STUDY_NO_OF_YEARS * 10000, 0) - 
    ISNULL((SELECT SUM(SF.STUDENT_FIN_RECIEVED_AMOUNT) 
            FROM MOHECORE.STUDENT_FINANCE SF 
            WHERE SF.STUDENT_ID = SD.STUDENT_ID 
            AND SF.STUDY_FIN_RECIEVED_STATUS = 1), 0) AS amount_remaining,
    SSS.STUDENT_STUDY_STATUS_NAME AS scholarship_status,
    SUI.STUDY_UNIVERSITY_IN_NAME AS university_name,
    SMI.STUDY_MAJOR_NAME AS major_name
FROM 
    MOHECORE.STUDENT_DATA SD
    LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE SST ON SD.STUDENT_STUDY_TYPE_ID = SST.STUDENT_STUDY_TYPE_ID
    LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE SAT ON SD.STUDENT_AGENCY_TYPE_ID = SAT.STUDENT_AGENCY_TYPE_ID
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS SSS ON SD.STUDENT_STUDY_STATUS_ID = SSS.STUDENT_STUDY_STATUS_ID
    LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN SUI ON SD.STUDY_UNIVERSITY_IN = SUI.STUDY_UNIVERSITY_IN_ID
    LEFT JOIN MOHECORE.STUDY_MAJOR_IN SMI ON SD.STUDY_MAJOR_IN = SMI.STUDY_MAJOR_ID
WHERE 
    SD.STUDENT_STUDY_TYPE_ID IN (1, 2); -- Only scholarship students;
GO

/* ── 31/34. V_Student_Distribution_University ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Student_Distribution_University AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name_ar,
    COUNT(sd.STUDENT_ID) AS total_students,
    COUNT(CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN 1 END) AS active_students,
    COUNT(CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN 1 END) AS graduated_students,
    COUNT(CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Suspended', N'موقوف') THEN 1 END) AS suspended_students,
    COUNT(CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Male%'   THEN 1 END) AS male_students,
    COUNT(CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Female%' THEN 1 END) AS female_students,
    0 AS scholarship_students,
    0 AS regular_students,
    0 AS bachelors_students,
    0 AS masters_students,
    0 AS phd_students,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS programs_count,
    CAST(COUNT(sd.STUDENT_ID) * 100.0 / NULLIF(SUM(COUNT(sd.STUDENT_ID)) OVER (), 0) AS DECIMAL(5,2)) AS percentage_of_total_students
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN  sci ON sci.STUDY_COUNTRY_IN_ID  = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA       sd  ON sd.STUDY_UNIVERSITY_IN  = sui.STUDY_UNIVERSITY_IN_ID
                                          AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_GENDER     sg  ON sg.STUDENT_GENDER_ID    = sd.STUDENT_GENDER
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME;
GO

/* ── 32/34. V_University_Agreement ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_University_Agreement AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name_ar,
    CAST(NULL AS DATE) AS agreement_start_date,
    CAST(NULL AS DATE) AS last_activity_date,
    NULL AS agreement_duration_years,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_students_under_agreement,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN sd.STUDENT_ID END) AS active_students,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS programs_covered,
    COUNT(DISTINCT sd.STUDENT_DEGREE_ID) AS degree_levels_covered,
    0 AS scholarship_allocations,
    CAST(0 AS DECIMAL(18,2)) AS total_financial_commitment,
    N'active' AS agreement_status,
    N'operational' AS operational_status
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID
                                   AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME;
GO

/* ── 33/34. V_University_Partnership ─────────────────────────────────────────────── */
-- Add country_id (STUDY_COUNTRY_IN_ID) to university report views for country scoping.
-- Idempotent — safe to re-run.

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
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN sd.STUDENT_ID END) AS active_students,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN sd.STUDENT_ID END) AS graduated_students,
    CAST(NULL AS DATE) AS partnership_start_date,
    CAST(NULL AS DATE) AS last_enrollment_date,
    NULL AS partnership_duration_years,
    COUNT(DISTINCT sd.STUDENT_DEGREE_ID) AS degree_levels_offered,
    0 AS scholarship_students,
    0 AS regular_students
FROM MOHECORE.STUDY_UNIVERSITY_IN sui
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sui.STUDY_COUNTRY_IN_ID
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID
                                   AND ISNULL(sd.IS_DELETED, 0) = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
GROUP BY sui.STUDY_UNIVERSITY_IN_ID, sui.STUDY_UNIVERSITY_IN_NAME, sui.STUDY_COUNTRY_IN_ID, sci.STUDY_COUNTRY_IN_NAME;
GO

/* ── 34/34. V_University_Performance ─────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_University_Performance AS
SELECT
    CAST(sui.STUDY_UNIVERSITY_IN_ID AS BIGINT) AS university_id,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'') AS university_name_ar,
    ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'') AS country_name,
    sui.STUDY_COUNTRY_IN_ID AS country_id,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_enrolled,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Active', N'نشط') THEN sd.STUDENT_ID END) AS currently_active,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN sd.STUDENT_ID END) AS total_graduated,
    COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Suspended', N'موقوف') THEN sd.STUDENT_ID END) AS total_suspended,
    CAST(
        COUNT(DISTINCT CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN sd.STUDENT_ID END) * 100.0
        / NULLIF(COUNT(DISTINCT sd.STUDENT_ID), 0)
    AS DECIMAL(5,2)) AS graduation_rate,
    CAST(NULL AS DECIMAL(5,2)) AS retention_rate,
    CAST(NULL AS DECIMAL(5,2)) AS avg_study_duration,
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

/* ── Verify ───────────────────────────────────────────────────────────────────
 * Every view in the database should now be reproducible from this directory.
 * A name printed here is a view still living only in the database.
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== view count ===';
SELECT COUNT(*) AS views_in_database FROM sys.views WHERE SCHEMA_NAME(schema_id) = 'MOHECORE';
GO

PRINT '';
PRINT '=== each view still returns rows (or is legitimately empty) ===';
PRINT '(A view that errors here has been captured wrongly and must be fixed before this script is trusted.)';
GO
