/* ============================================================================
   V078__scholarship_and_payment_reports_read_what_is_recorded.sql
   30 August 2026 — every figure and every list from the database, last two views

   Why
   ----
   V034 removed the invented values from V_Scholarship_Tracking and
   V_Payment_Summary but left what it could not source as NULL placeholder
   columns (payment_schedule, amount_remaining, payment_type, payment_method),
   a made-up four-year study length in end_date, the label literals 'Other' and
   'Unknown', a received-status compared by id (= 1), and — in the Java copy the
   report actually ran — the payment method as three English words. Owner
   decision O1 of 30 August: compute what a real column supports and remove the
   rest, end to end; O3: every remaining code list gets a table.

   What
   ----
   1. MOHECORE.LU_PAYMENT_METHOD — BANK_TRANSFER, CHECK, CASH (IS_SYSTEM = 1:
      PaymentSummaryRepository derives them from the row's bank / cheque
      references and binds them from codes/PaymentMethodCode).
   2. UI_SEVERITY and ICON on STUDY_FIN_RECEIVED_STATUS and STUDENT_STUDY_STATUS
      (the two adapter lists whose tags the report screens colour), seeded by
      CODE only where NULL.
   3. V_Scholarship_Tracking rewritten: payment_schedule and amount_remaining
      gone; type / provider / status print the recorded name or NULL; end_date
      is NULL when the study length is not recorded; amount_paid sums the rows
      whose status CODE is RECEIVED; scholarship_status_code added.
      Every student, as the report has always listed (the Java subselect had
      no study-type filter; this view's old IN (1, 2) was never what ran).
   4. V_Payment_Summary rewritten to the SELECT the report runs
      (STUDENT_FINANCE, one row per finance record): payment_status_code added,
      payment_method carries the LU_PAYMENT_METHOD code, no placeholder columns.

   Rollback
   --------
   DROP TABLE MOHECORE.LU_PAYMENT_METHOD;
   ALTER TABLE MOHECORE.STUDY_FIN_RECEIVED_STATUS DROP COLUMN UI_SEVERITY, ICON;
   ALTER TABLE MOHECORE.STUDENT_STUDY_STATUS DROP COLUMN UI_SEVERITY, ICON;
   re-run sections 1 and 4 of V034 for the two views.

   Run: sqlcmd -S localhost,1433 -d MOHE -U sa -I -f 65001 -b -i V078__scholarship_and_payment_reports_read_what_is_recorded.sql
   Idempotent: every step checks before it changes.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

PRINT '=== V078 step 1: LU_PAYMENT_METHOD ===';
IF OBJECT_ID('MOHECORE.LU_PAYMENT_METHOD', 'U') IS NULL
BEGIN
    CREATE TABLE MOHECORE.LU_PAYMENT_METHOD (
        ID          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_LU_PAYMENT_METHOD PRIMARY KEY,
        CODE        NVARCHAR(50)  NOT NULL,
        NAME_AR     NVARCHAR(200) NOT NULL,
        NAME_EN     NVARCHAR(200) NOT NULL,
        SORT_ORDER  INT NOT NULL CONSTRAINT DF_LU_PAYMENT_METHOD_SORT DEFAULT 0,
        IS_DELETED  BIT NOT NULL CONSTRAINT DF_LU_PAYMENT_METHOD_DEL DEFAULT 0,
        UI_SEVERITY NVARCHAR(20) NULL,
        ICON        NVARCHAR(50) NULL,
        IS_SYSTEM   BIT NOT NULL CONSTRAINT DF_LU_PAYMENT_METHOD_IS_SYSTEM DEFAULT 0,
        CONSTRAINT UQ_LU_PAYMENT_METHOD_CODE UNIQUE (CODE)
    );
    PRINT 'V078: LU_PAYMENT_METHOD created';
END

MERGE MOHECORE.LU_PAYMENT_METHOD AS t
USING (VALUES
    ('BANK_TRANSFER', N'تحويل مصرفي', N'Bank transfer', 1, N'pi-building'),
    ('CHECK',         N'صك',          N'Cheque',        2, N'pi-file'),
    ('CASH',          N'نقدًا',        N'Cash',          3, N'pi-money-bill')
) AS s (CODE, NAME_AR, NAME_EN, SORT_ORDER, ICON)
ON t.CODE = s.CODE
WHEN NOT MATCHED THEN
    INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, ICON, IS_SYSTEM)
    VALUES (s.CODE, s.NAME_AR, s.NAME_EN, s.SORT_ORDER, s.ICON, 1)
WHEN MATCHED AND t.IS_SYSTEM = 0 THEN
    UPDATE SET IS_SYSTEM = 1;
DECLARE @pm INT = @@ROWCOUNT;
PRINT 'V078: payment-method rows inserted or flagged = ' + CAST(@pm AS VARCHAR(10)) + ' (first run 3, re-run 0)';

PRINT '=== V078 step 2: UI_SEVERITY / ICON on the two status tables ===';
IF COL_LENGTH('MOHECORE.STUDY_FIN_RECEIVED_STATUS', 'UI_SEVERITY') IS NULL
    ALTER TABLE MOHECORE.STUDY_FIN_RECEIVED_STATUS ADD UI_SEVERITY NVARCHAR(20) NULL;
IF COL_LENGTH('MOHECORE.STUDY_FIN_RECEIVED_STATUS', 'ICON') IS NULL
    ALTER TABLE MOHECORE.STUDY_FIN_RECEIVED_STATUS ADD ICON NVARCHAR(50) NULL;
IF COL_LENGTH('MOHECORE.STUDENT_STUDY_STATUS', 'UI_SEVERITY') IS NULL
    ALTER TABLE MOHECORE.STUDENT_STUDY_STATUS ADD UI_SEVERITY NVARCHAR(20) NULL;
IF COL_LENGTH('MOHECORE.STUDENT_STUDY_STATUS', 'ICON') IS NULL
    ALTER TABLE MOHECORE.STUDENT_STUDY_STATUS ADD ICON NVARCHAR(50) NULL;
GO

-- Seeds fill NULLs only, so a colour chosen on the Code lists screen survives a re-run.
UPDATE t SET UI_SEVERITY = v.sev
FROM MOHECORE.STUDY_FIN_RECEIVED_STATUS t
JOIN (VALUES
    ('RECEIVED',            'success'),
    ('DISBURSED',           'success'),
    ('PARTIALLY_DISBURSED', 'warn'),
    ('UNDER_SETTLEMENT',    'warn'),
    ('ON_HOLD',             'warn'),
    ('NOT_RECEIVED',        'secondary'),
    ('REJECTED',            'danger'),
    ('RETURNED',            'info'),
    ('NOT_SPECIFIED',       'secondary')
) AS v (code, sev) ON v.code = t.CODE
WHERE t.UI_SEVERITY IS NULL;
DECLARE @fin INT = @@ROWCOUNT;

UPDATE t SET UI_SEVERITY = v.sev
FROM MOHECORE.STUDENT_STUDY_STATUS t
JOIN (VALUES
    ('ACTIVE',           'success'),
    ('NEW',              'info'),
    ('WITHDRAWN',        'danger'),
    ('SUSPENDED',        'warn'),
    ('TRANSFERRED',      'secondary'),
    ('UNDER_PROCESSING', 'info'),
    ('STRUGGLING',       'warn'),
    ('DECEASED',         'contrast'),
    ('NOT_SPECIFIED',    'secondary')
) AS v (code, sev) ON v.code = t.CODE
WHERE t.UI_SEVERITY IS NULL;
DECLARE @study INT = @@ROWCOUNT;
PRINT 'V078: severities filled: fin-received-status ' + CAST(@fin AS VARCHAR(10))
    + ', study-status ' + CAST(@study AS VARCHAR(10)) + ' (first run 9 / 9, re-run 0 / 0)';
GO

PRINT '=== V078 step 3: V_Scholarship_Tracking ===';
GO
CREATE OR ALTER VIEW MOHECORE.V_Scholarship_Tracking AS
SELECT
    CAST(ROW_NUMBER() OVER (ORDER BY sd.STUDENT_ID) AS BIGINT)          AS scholarship_id,
    sd.STUDENT_ID                                                        AS student_id,
    sd.STUDENT_NAME                                                      AS student_name,
    sd.STUDENT_PASSPORT_NUMBER                                           AS student_passport_number,
    sst.STUDENT_STUDY_TYPE_NAME                                          AS scholarship_type,
    sat.STUDENT_AGENCY_TYPE_NAME                                         AS scholarship_provider,
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
    sss.CODE                                                             AS scholarship_status_code,
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
    SELECT sf.STUDENT_ID, SUM(sf.STUDENT_FIN_RECIEVED_AMOUNT) AS total_paid
    FROM MOHECORE.STUDENT_FINANCE sf
    JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS s ON s.STUDY_FIN_RECEIVED_STATUS_ID = sf.STUDY_FIN_RECIEVED_STATUS
    WHERE s.CODE = 'RECEIVED'
    GROUP BY sf.STUDENT_ID
) sf_totals ON sf_totals.STUDENT_ID = sd.STUDENT_ID
WHERE sd.STUDENT_ID IS NOT NULL;
GO

PRINT '=== V078 step 4: V_Payment_Summary ===';
GO
CREATE OR ALTER VIEW MOHECORE.V_Payment_Summary AS
SELECT
    sf.STUDENT_FIN_ID                                                    AS payment_id,
    sd.STUDENT_ID                                                        AS student_id,
    sd.STUDENT_NAME                                                      AS student_name,
    sd.STUDENT_PASSPORT_NUMBER                                           AS student_passport_number,
    CAST(sf.STUDY_CHECK_DATE AS DATE)                                    AS payment_date,
    sf.NET_AMOUNT                                                        AS payment_amount,
    cur.STUDY_CURRENCY_IN_NAME                                           AS payment_currency,
    sfrs.STUDY_FIN_RECEIVED_STATUS_NAME                                  AS payment_status,
    sfrs.CODE                                                            AS payment_status_code,
    srws.STUDY_RECEIVED_WHOM_STATUS_NAME                                 AS payment_type,
    -- How the money went out, from what the row references (LU_PAYMENT_METHOD codes).
    CASE WHEN sf.STUDY_FINANCE_BANKE_ID IS NOT NULL THEN 'BANK_TRANSFER'
         WHEN sf.STUDY_CHECK_ID IS NOT NULL AND LTRIM(RTRIM(sf.STUDY_CHECK_ID)) <> '' THEN 'CHECK'
         ELSE 'CASH' END                                                 AS payment_method,
    mq.QUARTER_NAME                                                      AS quarter_name,
    sf.STUDENT_FIN_RECIEVED_YEAR                                         AS year_name,
    sui.STUDY_UNIVERSITY_IN_NAME                                         AS university_name,
    smi.STUDY_MAJOR_NAME                                                 AS major_name,
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

PRINT '=== V078 verification ===';
DECLARE @bad INT = 0, @methods INT, @finNull INT, @studyNull INT;
IF COL_LENGTH('MOHECORE.V_Scholarship_Tracking', 'amount_remaining') IS NOT NULL SET @bad += 1;
IF COL_LENGTH('MOHECORE.V_Scholarship_Tracking', 'payment_schedule') IS NOT NULL SET @bad += 1;
IF COL_LENGTH('MOHECORE.V_Scholarship_Tracking', 'scholarship_status_code') IS NULL SET @bad += 1;
IF COL_LENGTH('MOHECORE.V_Payment_Summary', 'payment_status_code') IS NULL SET @bad += 1;
SELECT @methods = COUNT(*) FROM MOHECORE.LU_PAYMENT_METHOD WHERE IS_SYSTEM = 1 AND IS_DELETED = 0;
IF @methods <> 3 SET @bad += 1;
SELECT @finNull = COUNT(*) FROM MOHECORE.STUDY_FIN_RECEIVED_STATUS WHERE CODE IS NOT NULL AND UI_SEVERITY IS NULL;
SELECT @studyNull = COUNT(*) FROM MOHECORE.STUDENT_STUDY_STATUS WHERE CODE IS NOT NULL AND UI_SEVERITY IS NULL;
IF @finNull + @studyNull > 0 SET @bad += 1;
IF EXISTS (SELECT 1 FROM MOHECORE.V_Scholarship_Tracking WHERE scholarship_type = 'Other' OR scholarship_status = 'Unknown') SET @bad += 1;
IF @bad > 0
BEGIN
    DECLARE @msg NVARCHAR(200) = N'V078 verification failed: ' + CAST(@bad AS NVARCHAR(10)) + N' check(s)';
    THROW 50078, @msg, 1;
END
PRINT 'V078: verified — payment methods = 3, placeholder columns gone, status codes on both views, no status without a severity';
GO
