/*
    V103 — Quarterly Report: derive the period from the payment date
    ================================================================

    THE DEFECT

    MOHECORE.V_Quarterly_Report returned seven rows in which every measure was
    zero and study_country_id was NULL. Not an empty report -- a populated one
    whose every number was 0.00. On dev, 2025-Q4 alone holds 939 payments
    totalling 1,571,220.

    It was driven from MOHE_QUARTER and joined the payments on

        LEFT JOIN MOHECORE.STUDENT_FINANCE sf ON sf.MOHE_QUARTER_ID = mq.QUARTER

    which cannot match, for two independent reasons:

      1. STUDENT_FINANCE.MOHE_QUARTER_ID is NULL on all 941 rows. Nothing writes
         it. This is the gap already recorded in docs/report-data-audit.md.

      2. Even fully populated it would not match. MOHE_QUARTER.QUARTER holds a
         YYYYQ composite (20241, 20242, ...) while MOHE_QUARTER.ID holds the
         surrogate key (10, 3, 11, ...). A column named *_ID was compared against
         the composite. So the join was wrong in shape as well as empty in fact.

    Because the join is a LEFT JOIN from MOHE_QUARTER, the failure was silent:
    one row per defined quarter, every aggregate folded to zero, no error.

    THE FIX

    Derive the quarter from the payment date, exactly as this view's siblings
    already do. V_Monthly_Report and V_Annual_Report are driven from
    STUDENT_FINANCE and take their period from STUDY_FINANCE_MIDATE; both report
    correct figures today. This makes the quarterly report consistent with them.

    MOHE_QUARTER is not used at all, deliberately. It carries no date bounds --
    QUARTER_PAY_DATE and QUARTER_RECIVED_DATE are NULL on every row -- so there
    is nothing to map a payment date onto. Until a quarter records its span, the
    calendar quarter of the payment is the only quarter the data supports.

    A consequence worth stating plainly: the reported quarters are the ones the
    payments fall in, not the ones MOHE_QUARTER defines. Dev payments land in
    2025-Q4 and 2026-Q2, neither of which exists in MOHE_QUARTER (which covers
    2024-Q1..2025-Q3). Quarters with no payments no longer appear at all, where
    before they appeared as zero rows. That is the honest shape: a quarter the
    ministry has defined but not yet disbursed against has nothing to report.

    The column list is unchanged, so QuarterlyReport (the @Immutable entity) and
    quarterly-report.jrxml bind exactly as before.

    AFTER APPLYING

        python scripts/audit_report_data.py
        sqlcmd ... -i docs/scripts/adhoc/probe_report_column_population.sql

    study_country_id should no longer appear in probe section 2, and section 1
    should show V_Quarterly_Report returning one row per country-quarter that
    has payments rather than seven zero rows.
*/

SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID('MOHECORE.V_Quarterly_Report', 'V') IS NULL
    THROW 50103, 'MOHECORE.V_Quarterly_Report does not exist; V103 expects it.', 1;
GO

CREATE OR ALTER VIEW MOHECORE.V_Quarterly_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_id,
    YEAR(sf.STUDY_FINANCE_MIDATE)              AS report_year,
    DATEPART(QUARTER, sf.STUDY_FINANCE_MIDATE) AS report_quarter,
    CAST(YEAR(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(4)) + '-Q' +
        CAST(DATEPART(QUARTER, sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(1)) AS report_period,
    COUNT(DISTINCT sd.STUDENT_ID) AS total_students,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'NEW'    THEN sd.STUDENT_ID END) AS new_enrollments,
    COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS active_students,
    COUNT(*) AS total_financial_transactions,
    CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) AS DECIMAL(18,2)) AS total_amount_disbursed,
    CAST(ISNULL(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 1
                         THEN sf.STUDENT_FIN_RECIEVED_AMOUNT ELSE 0 END), 0)
         AS DECIMAL(18,2)) AS scholarship_amount_disbursed,
    CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN 0
         ELSE CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) * 1.0
                   / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2))
    END AS avg_amount_per_student,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_students,
    COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sd.STUDENT_ID END) AS regular_students,
    COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
    COUNT(DISTINCT sd.STUDY_COUNTRY_IN)    AS total_countries,
    COUNT(DISTINCT sd.STUDENT_MAJOR_ID)    AS total_majors
FROM MOHECORE.STUDENT_FINANCE sf
LEFT JOIN MOHECORE.STUDENT_DATA         sd  ON sd.STUDENT_ID               = sf.STUDENT_ID
                                           AND ISNULL(sd.IS_DELETED, 0)    = 0
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sty ON sty.STUDENT_STUDY_TYPE_ID   = sd.STUDENT_STUDY_TYPE_ID
WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
GROUP BY sd.STUDY_COUNTRY_IN,
         YEAR(sf.STUDY_FINANCE_MIDATE),
         DATEPART(QUARTER, sf.STUDY_FINANCE_MIDATE);
GO

/*  Verification — expect one row per country-quarter that has payments, with
    non-zero measures, and no NULL study_country_id.  */
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1', @read_only = 0;
SELECT study_country_id, report_period, total_students,
       total_financial_transactions, total_amount_disbursed
FROM MOHECORE.V_Quarterly_Report
ORDER BY report_year, report_quarter;
GO
