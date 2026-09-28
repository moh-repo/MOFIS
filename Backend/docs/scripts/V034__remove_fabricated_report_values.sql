/*
    V034 — stop five report views asserting values nobody recorded.

    Why
    ---
    V033 captured these views verbatim, on purpose, so that this script could be
    the one that changes them. What it captured included figures the database
    never held:

      V_Scholarship_Tracking  scholarship_amount = STUDENT_STUDY_NO_OF_YEARS * 10000
                              scholarship_currency = 'USD'
                              payment_schedule = 'Quarterly'
      V_Financial_Audit       audit_currency = 'USD'
                              auditor_name = 'System Auditor'
                              audit_notes = a verdict derived from a status string
      V_Bank_Transaction      transaction_type = 'Debit' on every row
                              transaction_currency = 'USD'
                              reference_number = 'REF' + the surrogate id
      V_Payment_Summary       payment_currency = N'LYD'
                              payment_type, payment_method = N''
      V_Performance_Metrics   metric_unit = 'LYD' on the disbursement total
                              target_value = 70, and a status judged against it

    The scholarship figure is the one to look at twice. A student's award was
    reported as their year count times ten thousand, and amount_remaining then
    subtracted their real payments from it — so the row's arithmetic held
    together, and a reader had no way to see that the total it worked from was
    invented. 'System Auditor' is the same kind of error in words rather than
    numbers: it names an accountable person on a financial audit report where no
    person was recorded.

    Where the data turned out to exist
    ----------------------------------
    Most of these are replaced by real columns rather than by NULL:

      - STUDENT_DATA.STUDENT_AMOUNT holds the allowance, on 940 of 941 students,
        and amount_currency is a foreign key to STUDY_CURRENCY_IN on all 941.
        The 'USD' literal was in fact correct — that key resolves to US Dollar —
        but by luck. It asserted a currency instead of reading the one recorded,
        and would have gone on asserting it for the first student enrolled
        anywhere else.
      - STUDENT_FINANCE.STUDY_CURRENCY_IN_ID and STUDY_CHECK_ID give
        V_Financial_Audit and V_Bank_Transaction a real currency and a real
        reference number. STUDY_FINANCE_DOF is the finance office on the record,
        which is the nearest thing to an auditor that exists.
      - REPORT_METRIC_TARGET (V028) is where a performance target belongs. It is
        still empty, so target_value and the status judged from it are NULL —
        which is the honest reading of "no target has been set", and unlike the
        literal 70 it becomes correct on its own the day one is.

    Where it does not exist, the column is NULL
    -------------------------------------------
    payment_schedule, payment_type, payment_method, and V_Scholarship_Tracking's
    amount_remaining. That last one is a deliberate loss of a column that used to
    show a number: STUDENT_AMOUNT is a periodic allowance, not a total award, and
    a remaining balance needs a total. Subtracting cumulative payments from a
    periodic rate is meaningless, so the column reports nothing rather than that.

    Kept in step with the entities
    ------------------------------
    BankTransaction, FinancialAudit and ScholarshipTracking read their own
    @Subselect, not these views — the entities were corrected in Java, which left
    the views saying something different from the reports built on the same name.
    The bodies below are aligned with those entities so the Query Builder
    catalogue and the report screens agree.

    Idempotent. Apply in SSMS after V033.
*/

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ── 1. V_Scholarship_Tracking ─────────────────────────────────────────────────
 * Aligned with com.dilaw.mohe.model.ScholarshipTracking.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Scholarship_Tracking AS
SELECT
    CAST(ROW_NUMBER() OVER (ORDER BY sd.STUDENT_ID) AS BIGINT)          AS scholarship_id,
    sd.STUDENT_ID                                                        AS student_id,
    sd.STUDENT_NAME                                                      AS student_name,
    sd.STUDENT_PASSPORT_NUMBER                                           AS student_passport_number,
    COALESCE(sst.STUDENT_STUDY_TYPE_NAME, 'Other')                       AS scholarship_type,
    COALESCE(sat.STUDENT_AGENCY_TYPE_NAME, 'Other')                      AS scholarship_provider,
    -- The recorded allowance, replacing STUDENT_STUDY_NO_OF_YEARS * 10000.
    CAST(sd.STUDENT_AMOUNT AS DECIMAL(18, 2))                            AS scholarship_amount,
    -- The currency on the student's own record, replacing the literal 'USD'.
    CAST(NULLIF(cur.STUDY_CURRENCY_IN_NAME, '') AS NVARCHAR(100))        AS scholarship_currency,
    sd.STUDENT_MIDATE                                                    AS allocated_date,
    sd.STUDENT_MIDATE                                                    AS start_date,
    DATEADD(YEAR, COALESCE(sd.STUDENT_STUDY_NO_OF_YEARS, 4), sd.STUDENT_MIDATE) AS end_date,
    -- Nothing in the schema records a payment schedule.
    CAST(NULL AS NVARCHAR(100))                                          AS payment_schedule,
    COALESCE(sf_totals.total_paid, 0)                                    AS amount_paid,
    -- See the header: an allowance is not a total, so there is no balance to
    -- report. Previously this subtracted real payments from an invented total.
    CAST(NULL AS DECIMAL(18, 2))                                         AS amount_remaining,
    sss.STUDENT_STUDY_STATUS_NAME                                        AS scholarship_status,
    sui.STUDY_UNIVERSITY_IN_NAME                                         AS university_name,
    smi.STUDY_MAJOR_NAME                                                 AS major_name
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE   sst ON sd.STUDENT_STUDY_TYPE_ID   = sst.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE  sat ON sd.STUDENT_AGENCY_TYPE_ID  = sat.STUDENT_AGENCY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sd.STUDENT_STUDY_STATUS_ID = sss.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN  sui ON sd.STUDY_UNIVERSITY_IN     = sui.STUDY_UNIVERSITY_IN_ID
LEFT JOIN MOHECORE.STUDY_MAJOR_IN       smi ON sd.STUDY_MAJOR_IN          = smi.STUDY_MAJOR_ID
LEFT JOIN MOHECORE.STUDY_CURRENCY_IN    cur ON sd.amount_currency         = cur.STUDY_CURRENCY_IN_ID
LEFT JOIN (
    SELECT sf.STUDENT_ID,
           SUM(CASE WHEN sf.STUDY_FIN_RECIEVED_STATUS = 1 THEN sf.STUDENT_FIN_RECIEVED_AMOUNT ELSE 0 END) AS total_paid
    FROM MOHECORE.STUDENT_FINANCE sf
    GROUP BY sf.STUDENT_ID
) sf_totals ON sf_totals.STUDENT_ID = sd.STUDENT_ID
WHERE sd.STUDENT_STUDY_TYPE_ID IN (1, 2);   -- scholarship students only, as before
GO

/* ── 2. V_Financial_Audit ──────────────────────────────────────────────────────
 * Aligned with com.dilaw.mohe.model.FinancialAudit.
 *
 * auditor_name now reads STUDY_FINANCE_DOF — the finance office recorded against
 * the transaction. It is NULL where none was recorded, which is the correct
 * answer and the one 'System Auditor' was covering up.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Financial_Audit AS
SELECT
    'AUDIT' + CAST(sf.STUDENT_FIN_ID AS VARCHAR(10))                     AS audit_id,
    sf.STUDY_CHECK_DATE                                                  AS audit_date,
    CAST(NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME, '') AS NVARCHAR(255)) AS audit_category,
    sf.STUDENT_FIN_RECIEVED_AMOUNT                                       AS audit_amount,
    CAST(NULLIF(cur.STUDY_CURRENCY_IN_NAME, '') AS NVARCHAR(100))        AS audit_currency,
    CAST(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') AS NVARCHAR(255)) AS audit_status,
    CAST(NULLIF(dof.STUDY_FINANCE_DOF_NAME, '') AS NVARCHAR(255))        AS auditor_name,
    -- Was a verdict ('All clear' / 'Requires review' / 'Discrepancy found')
    -- computed from the status text. Now the note actually written on the record.
    CAST(COALESCE(
            NULLIF(CAST(sf.STUDY_CHECK_DETAILS AS NVARCHAR(MAX)), ''),
            NULLIF(CAST(sf.STUDY_NOTE_1 AS NVARCHAR(MAX)), '')
        ) AS NVARCHAR(1000))                                             AS audit_notes,
    sd.STUDENT_NAME                                                      AS student_name,
    sd.STUDENT_PASSPORT_NUMBER                                           AS student_passport,
    bank.STUDY_FINANCE_BANKE_NAME                                        AS bank_name,
    sf.STUDENT_FIN_RECIEVED_YEAR                                         AS fiscal_year,
    CASE
        WHEN sf.STUDENT_FIN_RECIEVED_MONTH IS NULL OR sf.STUDENT_FIN_RECIEVED_MONTH <= 0 THEN NULL
        ELSE ((sf.STUDENT_FIN_RECIEVED_MONTH - 1) / 3) + 1
    END                                                                  AS fiscal_quarter
FROM MOHECORE.STUDENT_FINANCE sf
INNER JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS  sfrs ON sf.STUDY_FIN_RECIEVED_STATUS    = sfrs.STUDY_FIN_RECEIVED_STATUS_ID
LEFT JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS srws ON sf.STUDY_RECEIVED_WHOM_STATUS_ID = srws.STUDY_RECEIVED_WHOM_STATUS_ID
LEFT JOIN MOHECORE.STUDY_FINANCE_BANK         bank ON sf.STUDY_FINANCE_BANKE_ID        = bank.STUDY_FINANCE_BANKE_ID
LEFT JOIN MOHECORE.STUDY_FINANCE_DOF          dof  ON sf.STUDY_FINANCE_DOF_ID          = dof.STUDY_FINANCE_DOF_ID
LEFT JOIN MOHECORE.STUDY_CURRENCY_IN          cur  ON sf.STUDY_CURRENCY_IN_ID          = cur.STUDY_CURRENCY_IN_ID
WHERE sf.STUDY_CHECK_DATE IS NOT NULL;
GO

/* ── 3. V_Bank_Transaction ─────────────────────────────────────────────────────
 * Aligned with com.dilaw.mohe.model.BankTransaction.
 *
 * transaction_type was 'Debit' on every row and reference_number was 'REF' glued
 * to the surrogate id — a reference number that referred to nothing outside this
 * view. Both now come from the record: the received-status, and STUDY_CHECK_ID.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Bank_Transaction AS
SELECT
    sf.STUDENT_FIN_ID                                                    AS transaction_id,
    sf.STUDY_CHECK_DATE                                                  AS transaction_date,
    CAST(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') AS NVARCHAR(255)) AS transaction_type,
    CAST(COALESCE(
            NULLIF(CAST(sf.STUDY_CHECK_DETAILS AS NVARCHAR(MAX)), ''),
            NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME, ''),
            NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '')
        ) AS NVARCHAR(1000))                                             AS transaction_description,
    sf.STUDENT_FIN_RECIEVED_AMOUNT                                       AS transaction_amount,
    CAST(NULLIF(cur.STUDY_CURRENCY_IN_NAME, '') AS NVARCHAR(100))        AS transaction_currency,
    CAST(NULLIF(CAST(sf.STUDY_CHECK_ID AS NVARCHAR(255)), '') AS NVARCHAR(255)) AS reference_number,
    bank.STUDY_FINANCE_BANKE_NAME                                        AS bank_name,
    branch.STUDY_FINANCE_BANK_BRANCH_NAME                                AS bank_branch,
    sd.STUDENT_NAME                                                      AS beneficiary_name,
    sd.STUDENT_PASSPORT_NUMBER                                           AS beneficiary_account
FROM MOHECORE.STUDENT_FINANCE sf
INNER JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
LEFT JOIN MOHECORE.STUDY_FINANCE_BANK         bank   ON sf.STUDY_FINANCE_BANKE_ID        = bank.STUDY_FINANCE_BANKE_ID
LEFT JOIN MOHECORE.STUDY_FINANCE_BANK_BRANCH  branch ON sf.STUDY_FINANCE_BANK_BRANCH_ID  = branch.STUDY_FINANCE_BANK_BRANCH_ID
LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS  sfrs   ON sf.STUDY_FIN_RECIEVED_STATUS     = sfrs.STUDY_FIN_RECEIVED_STATUS_ID
LEFT JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS srws   ON sf.STUDY_RECEIVED_WHOM_STATUS_ID = srws.STUDY_RECEIVED_WHOM_STATUS_ID
LEFT JOIN MOHECORE.STUDY_CURRENCY_IN          cur    ON sf.STUDY_CURRENCY_IN_ID          = cur.STUDY_CURRENCY_IN_ID
WHERE sf.STUDY_CHECK_DATE IS NOT NULL
  AND sf.STUDY_FINANCE_BANKE_ID IS NOT NULL;
GO

/* ── 4. V_Payment_Summary ──────────────────────────────────────────────────────
 * STUDENT_FINANCE_PAYMENT has no currency, type or method column — the header
 * comment on the original view says as much, and then supplied N'LYD' and two
 * empty strings anyway. An empty string is not the same as "not recorded": it
 * renders as a blank cell that looks like a value, and it sorts and groups as
 * one. These are NULL now.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Payment_Summary AS
SELECT
    fp.PAYMENT_ID                                                        AS payment_id,
    fp.STUDENT_ID                                                        AS student_id,
    ISNULL(sd.STUDENT_NAME, N'')                                         AS student_name,
    ISNULL(sd.STUDENT_PASSPORT_NUMBER, N'')                              AS student_passport_number,
    CAST(fp.INSERTED_AT AS DATE)                                         AS payment_date,
    CAST(ISNULL(fp.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18,2))     AS payment_amount,
    CAST(NULL AS NVARCHAR(100))                                          AS payment_currency,
    CAST(ISNULL(fp.STUDY_FIN_RECIEVED_STATUS, 0) AS NVARCHAR(10))        AS payment_status,
    CAST(NULL AS NVARCHAR(100))                                          AS payment_type,
    CAST(NULL AS NVARCHAR(100))                                          AS payment_method,
    ISNULL(CAST(mq.QUARTER_NO AS NVARCHAR(10)), N'')                     AS quarter_name,
    mq.QUARTER_YEAR                                                      AS year_name,
    ISNULL(sui.STUDY_UNIVERSITY_IN_NAME, N'')                            AS university_name,
    ISNULL(sma.STUDENT_MAJOR_NAME, N'')                                  AS major_name,
    fp.STUDENT_FIN_RECIEVED_YEAR                                         AS payment_year,
    fp.STUDENT_FIN_RECIEVED_MONTH                                        AS payment_month
FROM MOHECORE.STUDENT_FINANCE_PAYMENT fp
LEFT JOIN MOHECORE.STUDENT_DATA        sd  ON sd.STUDENT_ID             = fp.STUDENT_ID
LEFT JOIN MOHECORE.MOHE_QUARTER        mq  ON mq.QUARTER                = fp.MOHE_QUARTER_ID
LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
LEFT JOIN MOHECORE.STUDENT_MAJOR       sma ON sma.STUDENT_MAJOR_ID       = sd.STUDENT_MAJOR_ID;
GO

/* ── 5. V_Performance_Metrics ──────────────────────────────────────────────────
 * Two changes.
 *
 * The disbursement total carried metric_unit 'LYD'. STUDENT_FINANCE records its
 * currency in STUDY_CURRENCY_IN_ID and that column is NULL on all 941 rows, so
 * the unit is not known — and summing amounts whose currencies are unrecorded is
 * only sound while they are all the same. The unit is NULL and the caveat is
 * here rather than implied by a label.
 *
 * The graduation metric measured itself against a literal 70 and then reported
 * 'on_target' or 'below_target' from it. REPORT_METRIC_TARGET (V028) is where a
 * target belongs; it is empty, so both columns are NULL and the status says
 * 'no_target' rather than passing judgement. Entering one row makes this correct
 * with no further change here.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Performance_Metrics AS
SELECT metric_category, metric_name, metric_value, metric_unit,
       target_value, achievement_percentage, performance_status, measurement_period
FROM (
    SELECT
        'enrollment'                                    AS metric_category,
        'total_students'                                AS metric_name,
        CAST(COUNT(*) AS DECIMAL(18,2))                 AS metric_value,
        'count'                                         AS metric_unit,
        CAST(NULL AS DECIMAL(18,2))                     AS target_value,
        CAST(NULL AS DECIMAL(18,2))                     AS achievement_percentage,
        'informational'                                 AS performance_status,
        CAST(YEAR(GETDATE()) AS NVARCHAR(10))           AS measurement_period
    FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED, 0) = 0

    UNION ALL

    SELECT
        'finance', 'total_disbursed',
        CAST(ISNULL(SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT), 0) AS DECIMAL(18,2)),
        -- Was 'LYD'. The currency is recorded per row and is not set on any of them.
        CAST(NULL AS NVARCHAR(10)),
        CAST(NULL AS DECIMAL(18,2)), CAST(NULL AS DECIMAL(18,2)), 'informational',
        CAST(YEAR(GETDATE()) AS NVARCHAR(10))
    FROM MOHECORE.STUDENT_FINANCE sf

    UNION ALL

    SELECT
        'graduation', 'graduation_rate',
        CAST(COUNT(CASE WHEN sss.CODE = 'GRADUATED' THEN 1 END) * 100.0
             / NULLIF(COUNT(*), 0) AS DECIMAL(18,2)),
        '%',
        CAST(tgt.TARGET_VALUE AS DECIMAL(18,2)),
        CASE WHEN tgt.TARGET_VALUE IS NULL OR tgt.TARGET_VALUE = 0 THEN NULL
             ELSE CAST(COUNT(CASE WHEN sss.CODE = 'GRADUATED' THEN 1 END) * 100.0
                       / NULLIF(COUNT(*), 0) / tgt.TARGET_VALUE * 100 AS DECIMAL(18,2))
        END,
        CASE
            WHEN tgt.TARGET_VALUE IS NULL THEN 'no_target'
            WHEN COUNT(CASE WHEN sss.CODE = 'GRADUATED' THEN 1 END) * 100.0
                 / NULLIF(COUNT(*), 0) >= tgt.TARGET_VALUE THEN 'on_target'
            ELSE 'below_target'
        END,
        CAST(YEAR(GETDATE()) AS NVARCHAR(10))
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    -- Matched on category and name; no country filter, because this view reports
    -- a ministry-wide figure and a per-country target would not apply to it.
    LEFT JOIN MOHECORE.REPORT_METRIC_TARGET tgt
           ON tgt.METRIC_CATEGORY = 'graduation'
          AND tgt.METRIC_NAME     = 'graduation_rate'
          AND tgt.COUNTRY_ID IS NULL
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY tgt.TARGET_VALUE
) m;
GO

/* ── Verify ───────────────────────────────────────────────────────────────────
 * Each view must still be queryable, and the columns changed above must no
 * longer be carrying a literal.
 * ──────────────────────────────────────────────────────────────────────────── */
/* The checks below read data, not view text.
 *
 * A first draft grepped sys.sql_modules for 'USD' and friends and reported four
 * views still guilty — of which every one was a false positive: three were the
 * explanatory comments written above, and the fourth was V_Budget_Analysis's
 * N'quarterly' category label and its surrogate key's * 1000000, which contains
 * "* 10000". Searching source text for the shape of a mistake finds comments
 * about the mistake just as readily as the mistake. Asking the data what it
 * returns does not have that problem.
 *
 * com.dilaw.mohe.report.ReportsDoNotInventValuesTest guards the same properties
 * on every build. */
PRINT '';
PRINT '=== a currency column must not be constant while its source column is empty ===';
PRINT '(expected: 0 distinct currencies, because STUDY_CURRENCY_IN_ID is unset on all 941 finance rows)';
SELECT (SELECT COUNT(DISTINCT audit_currency)       FROM MOHECORE.V_Financial_Audit)  AS audit_currencies,
       (SELECT COUNT(DISTINCT transaction_currency) FROM MOHECORE.V_Bank_Transaction) AS bank_currencies,
       (SELECT COUNT(DISTINCT payment_currency)     FROM MOHECORE.V_Payment_Summary)  AS payment_currencies;
GO

PRINT '';
PRINT '=== a scholarship amount must match the student record it belongs to ===';
PRINT '(expected: 0 mismatched — the amount is read, not computed)';
SELECT COUNT(*) AS rows_not_matching_student_record
FROM MOHECORE.V_Scholarship_Tracking st
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = st.student_id
WHERE ISNULL(st.scholarship_amount, -1) <> ISNULL(CAST(sd.STUDENT_AMOUNT AS DECIMAL(18,2)), -1);
GO

PRINT '';
PRINT '=== scholarship amounts now come from STUDENT_AMOUNT ===';
SELECT COUNT(*)                        AS scholarship_rows,
       COUNT(scholarship_amount)       AS with_amount,
       COUNT(scholarship_currency)     AS with_currency,
       COUNT(amount_remaining)         AS with_remaining,   -- expected 0
       MIN(scholarship_amount)         AS min_amount,
       MAX(scholarship_amount)         AS max_amount
FROM MOHECORE.V_Scholarship_Tracking;
GO

PRINT '';
PRINT '=== financial audit no longer names an auditor that was not recorded ===';
SELECT COUNT(*) AS audit_rows, COUNT(auditor_name) AS with_auditor, COUNT(audit_currency) AS with_currency
FROM MOHECORE.V_Financial_Audit;
GO

PRINT '';
PRINT '=== performance metrics ===';
SELECT metric_category, metric_name, metric_value, metric_unit, target_value, performance_status
FROM MOHECORE.V_Performance_Metrics
ORDER BY metric_category;
GO
