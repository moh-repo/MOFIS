/*
    V022 — period V_Trend_Analysis by the month the money belongs to.

    Problem
    -------
    The view grouped by STUDY_FINANCE_MIDATE, the row's last-modified timestamp:

        WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
        GROUP BY YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE)

    939 of the 941 finance rows were written in one go, so the view returned two
    points — 2025-11 and 2026-05 — and reported a 99.79% collapse between them.
    Neither month contains a scholarship payment. The money sits in 2024-02.

    Fix
    ---
    Derive the period from STUDY_CHECK_DATE where present and from
    STUDENT_FIN_RECIEVED_YEAR / _MONTH otherwise, which are the populated
    columns, and sum NET_AMOUNT so the figures agree with the quarterly financial
    report rather than the gross amount before wafar.

    Expected output after applying:

        period    metric          value        previous     growth   direction
        2024-02   finance_amount  1,533,060                           flat
        2025-01   finance_amount  3,360        1,533,060    -99.78    down
        2026-05   finance_amount  1,000        3,360        -70.24    down

    Scope
    -----
    The trend analysis page no longer reads this view: its rows and its filter
    options are both built in TrendAnalysisRepository, which already applies the
    same derivation together with the country filter the view cannot express.
    This script brings the view into line for any other consumer, and so the two
    definitions do not drift further apart.

    Note that base.total_records is computed and never selected, so the view can
    only ever offer the single metric 'finance_amount'. A second metric would be
    a change in what the page shows, so it is left alone here.

    Apply in SSMS.
*/

CREATE OR ALTER VIEW MOHECORE.V_Trend_Analysis AS
WITH base AS (
    SELECT
        COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR)   AS yr,
        COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH) AS mo,
        SUM(CAST(COALESCE(sf.NET_AMOUNT, 0) AS BIGINT)) AS total_amount,
        COUNT(*) AS total_records
    FROM MOHECORE.STUDENT_FINANCE sf
    WHERE COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) IS NOT NULL
      AND COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH) BETWEEN 1 AND 12
    GROUP BY COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR),
             COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH)
)
SELECT
    CAST(yr AS NVARCHAR(4)) + '-' + RIGHT('0' + CAST(mo AS NVARCHAR(2)), 2) AS trend_period,
    'finance_amount' AS metric_type,
    yr AS trend_year,
    mo AS trend_month,
    CAST(total_amount AS DECIMAL(18,2)) AS metric_value,
    LAG(CAST(total_amount AS DECIMAL(18,2))) OVER (ORDER BY yr, mo) AS previous_value,
    CASE
        WHEN LAG(total_amount) OVER (ORDER BY yr, mo) = 0 THEN NULL
        ELSE CAST((total_amount - LAG(total_amount) OVER (ORDER BY yr, mo)) * 100.0
                  / LAG(total_amount) OVER (ORDER BY yr, mo) AS DECIMAL(8,2))
    END AS growth_percentage,
    CASE
        WHEN total_amount > LAG(total_amount) OVER (ORDER BY yr, mo) THEN 'up'
        WHEN total_amount < LAG(total_amount) OVER (ORDER BY yr, mo) THEN 'down'
        ELSE 'flat'
    END AS trend_direction
FROM base;
