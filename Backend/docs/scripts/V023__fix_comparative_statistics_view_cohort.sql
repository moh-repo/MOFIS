/*
    V023 — compare cohorts in V_Comparative_Statistics, not years of birth.

    Problem
    -------
    Both periods were derived from the clock and the counts were taken from the
    students' date of birth:

        SUM(CASE WHEN YEAR(sd.STUDENT_BIRTHDATE) = YEAR(GETDATE())     THEN 1 ELSE 0 END) AS current_period_value,
        SUM(CASE WHEN YEAR(sd.STUDENT_BIRTHDATE) = YEAR(GETDATE()) - 1 THEN 1 ELSE 0 END) AS previous_period_value
        ...
        CAST(YEAR(GETDATE()) AS NVARCHAR(10)) AS current_period

    So "2026" meant students born in 2026 and "2025" students born in 2025. No
    scholarship student is either, and every row the view produced was zero
    against zero with a null change. The period columns could not be influenced
    by anything in the database, which is also why the page's two period
    dropdowns — fed from this view — offered a single clock-derived value each.

    Fix
    ---
    Count by STUDENT_DELEGATION_DECISION_YEAR, the year the student's delegation
    decision was issued. It is populated on 846 of the 941 students and spans
    2014-2042. It is nvarchar, so it is parsed with TRY_CAST and unparseable
    values are dropped rather than counted as year zero.

    The alternatives are not usable: STUDENT_ADDED_QUARTER is set on 3 students,
    and CURRENT_QUARTER holds '20242' for every row.

    GETDATE() survives in one place only, and not as a source of data: it picks
    the default current period as the most recent cohort year that is not in the
    future. This database records decision years out to 2042, one student in each
    of 2027-2042, which look like data entry errors; without that guard the view
    would report one student against one. Those years remain selectable on the
    page, which resolves its own periods and does not read this view.

    Also changed: the country dimension now groups by STUDY_COUNTRY_IN, the study
    destination, where it previously used STUDENT_COUNTRY_ID, the nationality.
    The destination is what report country scope filters on, so the two now agree.

    Dimensions that are zero in both years are dropped — a comparison row of two
    zeros says nothing, and it used to be every row.

    Expected output after applying (2026 against 2025):

        type     dimension          current  previous  change
        country  <destination>      1        3         -66.67
        degree   -                  1        1           0.00
        degree   <degree name>      0        1        -100.00
        degree   <degree name>      0        1        -100.00

    Scope
    -----
    The comparative statistics page no longer reads this view: its rows and its
    filter options are both built in ComparativeStatisticsRepository, which
    applies the same derivation together with the country filter the view cannot
    express. This script brings the view into line for any other consumer, and so
    the two definitions do not drift further apart.

    Apply in SSMS.
*/

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
        SUM(CASE WHEN ch.decision_year = k.current_year  THEN 1 ELSE 0 END) AS current_period_value,
        SUM(CASE WHEN ch.decision_year = k.previous_year THEN 1 ELSE 0 END) AS previous_period_value,
        k.current_year,
        k.previous_year
    FROM cohort ch
    CROSS JOIN chosen k
    LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = ch.STUDY_COUNTRY_IN
    GROUP BY sci.STUDY_COUNTRY_IN_NAME, k.current_year, k.previous_year

    UNION ALL

    SELECT
        'degree',
        ISNULL(sdeg.STUDENT_DEGREE_NAME, N'-'),
        SUM(CASE WHEN ch.decision_year = k.current_year  THEN 1 ELSE 0 END),
        SUM(CASE WHEN ch.decision_year = k.previous_year THEN 1 ELSE 0 END),
        k.current_year,
        k.previous_year
    FROM cohort ch
    CROSS JOIN chosen k
    LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = ch.STUDENT_DEGREE_ID
    GROUP BY sdeg.STUDENT_DEGREE_NAME, k.current_year, k.previous_year
)
SELECT
    comparison_type,
    dimension_name,
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
