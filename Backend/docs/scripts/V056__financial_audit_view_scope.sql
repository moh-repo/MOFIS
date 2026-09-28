/*
================================================================================
V056 — V_Financial_Audit: filter on the amount, as the entity does
================================================================================

Why
---
V034 rebuilt V_Financial_Audit with WHERE sf.STUDY_CHECK_DATE IS NOT NULL, which
matches 1 row on this database. The JPA entity behind the Financial Audit report
and the Query Builder (com.dilaw.mohe.model.FinancialAudit) filters on
STUDENT_FIN_RECIEVED_AMOUNT IS NOT NULL, which matches 941. The two disagreed
about what a "financial audit row" is, and the rolling review recorded that as
open because changing it changes what the Query Builder returns.

This script settles it on the entity's side: a finance row is auditable when an
amount was received, whether or not a cheque date was recorded. A cheque date
is a property of one payment method, not of the transaction.

Why a new script and not an edit to V034
-----------------------------------------
V034 has been applied. Editing an applied script changes nothing on any
database that already ran it and leaves the repository disagreeing with every
such database. A forward script is the only way the change reaches them.

The view body is otherwise V034's, unchanged.
================================================================================
*/

SET NOCOUNT ON;
GO

CREATE OR ALTER VIEW MOHECORE.V_Financial_Audit AS
SELECT
    'AUDIT' + CAST(sf.STUDENT_FIN_ID AS VARCHAR(10))                     AS audit_id,
    sf.STUDY_CHECK_DATE                                                  AS audit_date,
    CAST(NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME, '') AS NVARCHAR(255)) AS audit_category,
    sf.STUDENT_FIN_RECIEVED_AMOUNT                                       AS audit_amount,
    CAST(NULLIF(cur.STUDY_CURRENCY_IN_NAME, '') AS NVARCHAR(100))        AS audit_currency,
    CAST(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') AS NVARCHAR(255)) AS audit_status,
    CAST(NULLIF(dof.STUDY_FINANCE_DOF_NAME, '') AS NVARCHAR(255))        AS auditor_name,
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
WHERE sf.STUDENT_FIN_RECIEVED_AMOUNT IS NOT NULL;
GO

/* ── Verification: the view and the entity's own predicate now agree ──────── */
SELECT (SELECT COUNT(*) FROM MOHECORE.V_Financial_Audit)                                      AS view_rows,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE WHERE STUDENT_FIN_RECIEVED_AMOUNT IS NOT NULL) AS entity_rows;
GO
