/*
    V021 — make V_Budget_Analysis return rows.

    Problem
    -------
    The view joined STUDENT_FINANCE to MOHE_QUARTER on

        INNER JOIN MOHECORE.MOHE_QUARTER mq ON sf.MOHE_QUARTER_ID = mq.QUARTER

    but STUDENT_FINANCE.MOHE_QUARTER_ID is NULL on every one of the 941 finance
    rows in this database, so the inner join eliminated everything and the view
    returned zero rows. The budget analysis page has therefore always shown an
    empty table — not because the page is wrong, but because its source view
    could never produce a row.

    Fix
    ---
    Derive the period the same way the quarterly financial report does: from
    STUDY_CHECK_DATE when present, otherwise from STUDENT_FIN_RECIEVED_YEAR /
    _MONTH, which are the populated columns. MOHE_QUARTER is then joined only to
    pick up BUDGET_AMOUNT, and as a LEFT JOIN so a period missing from the
    quarter table still reports its spending.

    This changes no data. STUDENT_FINANCE.MOHE_QUARTER_ID is left alone: back-
    filling it would be a data migration, and the derivation above is what the
    rest of the reporting layer already relies on.

    Still outstanding after this script
    -----------------------------------
    MOHE_QUARTER.BUDGET_AMOUNT is NULL on all 12 quarter rows, so allocated_amount
    is 0, remaining_amount is the negative of spending, and utilization_percentage
    is 0. The report will show what was spent but cannot show it against a budget
    until BUDGET_AMOUNT is entered. Expected output immediately after applying:

        year quarter country  allocated  spent        utilization
        2024 1       1        0          1,569,720    0
        2025 1       1        0          3,360        0
        2026 2       1        0          1,500        0

    Apply in SSMS.
*/

CREATE OR ALTER VIEW MOHECORE.V_Budget_Analysis AS
WITH finance AS (
    SELECT
        sd.STUDY_COUNTRY_IN AS study_country_in_id,
        COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS amount,
        COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) AS budget_year,
        COALESCE(
            DATEPART(QUARTER, sf.STUDY_CHECK_DATE),
            CASE
                WHEN sf.STUDENT_FIN_RECIEVED_MONTH IS NOT NULL AND sf.STUDENT_FIN_RECIEVED_MONTH > 0
                    THEN ((sf.STUDENT_FIN_RECIEVED_MONTH - 1) / 3) + 1
            END
        ) AS budget_quarter
    FROM MOHECORE.STUDENT_FINANCE sf
    INNER JOIN MOHECORE.STUDENT_DATA sd
        ON sd.STUDENT_ID = sf.STUDENT_ID
       AND (sd.IS_DELETED = 0 OR sd.IS_DELETED IS NULL)
    WHERE sd.STUDY_COUNTRY_IN IS NOT NULL
      -- A zero amount is not a disbursement, so it must not create a period row.
      AND COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) <> 0
)
SELECT
    -- One row per (country, year, quarter), which is what makes this unique.
    CAST(f.study_country_in_id * 1000000
         + f.budget_year * 10
         + f.budget_quarter AS BIGINT) AS budget_id,
    N'quarterly' AS budget_category,
    CAST(COALESCE(MAX(mq.BUDGET_AMOUNT), 0) AS DECIMAL(18,2)) AS allocated_amount,
    CAST(SUM(f.amount) AS DECIMAL(18,2)) AS spent_amount,
    CAST(COALESCE(MAX(mq.BUDGET_AMOUNT), 0) - SUM(f.amount) AS DECIMAL(18,2)) AS remaining_amount,
    CASE
        WHEN COALESCE(MAX(mq.BUDGET_AMOUNT), 0) = 0 THEN 0
        ELSE CAST(SUM(f.amount) * 100.0 / MAX(mq.BUDGET_AMOUNT) AS INT)
    END AS utilization_percentage,
    f.budget_year,
    f.budget_quarter,
    f.study_country_in_id
FROM finance f
-- Budget only. MOHE_QUARTER.COUNTRY_ID is NULL on every current row, meaning a
-- ministry-wide figure; the predicate keeps a per-country budget working if one
-- is ever entered, without fanning the row out.
LEFT JOIN MOHECORE.MOHE_QUARTER mq
       ON mq.QUARTER_YEAR = f.budget_year
      AND mq.QUARTER_NO   = f.budget_quarter
      AND (mq.COUNTRY_ID IS NULL OR mq.COUNTRY_ID = f.study_country_in_id)
WHERE f.budget_year IS NOT NULL
  AND f.budget_quarter IS NOT NULL
GROUP BY f.study_country_in_id, f.budget_year, f.budget_quarter;
