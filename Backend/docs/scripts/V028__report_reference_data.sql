/*
    V028 — give the report layer real reference data to key on.

    Problem
    -------
    Several reports encode business meaning as literals in Java or in SQL because
    there was no column to read it from. Four separate cases, one root cause:

    1. Study status is addressed two incompatible ways and both are wrong.

       ChartDataService and StatisticsService assume the IDs mean
       1=active, 2=graduated, 3=suspended, 4=withdrawn. The table actually says:

            0  Deceased          4  Suspended
            1  Active            5  Transferred
            2  New               6  Under Processing
            3  Withdrawn         7  Struggling
                                 99 Not Specified

       So "= 2" counts New students as graduates and 3/4 are swapped. Elsewhere
       (PerformanceMetricsRepository, AcademicYearReportRepository) the same
       concept is addressed by display name, IN (N'Graduated', N'متخرج') — which
       matches nothing, because there is no Graduated row at all.

       Neither an ID literal nor a display name is a safe key: the IDs are wrong
       today and the names are user-editable and language-specific. The LU_*
       tables added by V003/V004 all solved this with a stable CODE column; the
       legacy lookups never got one.

    2. Scholarship-vs-regular splits are reported as literal zeros because
       nothing marks which STUDENT_STUDY_TYPE rows are funded. The table also
       mixes two unrelated concepts — funding source (Government Funded,
       Self-Funded) and study modality (Full-time, Distance) — so "not
       self-funded" is not a usable test either.

    3. Map coordinates live in an 18-entry HashMap in ChartDataService keyed on
       English country names, matched against the Arabic name column, so every
       country falls through to (0,0) and the geographic map pins the entire
       ministry at Null Island. The map is also missing United Arab Emirates and
       Sudan, which this database has.

    4. LU_TREND_CLASS and LU_REPORT_DIMENSION exist precisely to hold the
       classifications the native queries emit as literals, but only two of the
       needed groups were ever seeded.

    Fix
    ---
    Add the missing reference columns and seed them. Every value below is
    reference data — a fact about the domain, not a measurement — which is why it
    belongs in a lookup table rather than in a query.

    All statements are idempotent: re-running the script changes nothing.
    Coordinates are seeded by ID, not by name, so a renamed country keeps its pin.

    Nothing here fabricates report figures. It gives the queries in V029 (Java
    side) something real to join to so they can stop inventing them.

    Apply in SSMS.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
-- Must be ON before anything touches STUDENT_STUDY_STATUS: section 1 creates a
-- filtered index on it, and once that exists SQL Server refuses any DML on the
-- table under QUOTED_IDENTIFIER OFF. SSMS defaults it ON, sqlcmd defaults it
-- OFF, so set it here to make re-runs behave the same under both.
SET QUOTED_IDENTIFIER ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 1. STUDENT_STUDY_STATUS.CODE — the stable key for study status.
 *
 * Seeded from the current IDs, which are the ministry's own and are not going to
 * change. From here on, report SQL joins on CODE and never on an ID literal or a
 * display name.
 *
 * Note there is deliberately no GRADUATED code: this ministry does not record
 * graduation as a study status. Reports that need a graduation figure must read
 * V_Academic_Performance.isGraduated instead, and the ones that cannot are now
 * explicit about it rather than silently reporting 0%.
 * ──────────────────────────────────────────────────────────────────────────── */
IF COL_LENGTH('MOHECORE.STUDENT_STUDY_STATUS', 'CODE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_STUDY_STATUS ADD CODE VARCHAR(40) NULL;
END
GO

UPDATE s SET s.CODE = v.CODE
FROM MOHECORE.STUDENT_STUDY_STATUS s
JOIN (VALUES
    ( 0, 'DECEASED'),
    ( 1, 'ACTIVE'),
    ( 2, 'NEW'),
    ( 3, 'WITHDRAWN'),
    ( 4, 'SUSPENDED'),
    ( 5, 'TRANSFERRED'),
    ( 6, 'UNDER_PROCESSING'),
    ( 7, 'STRUGGLING'),
    (99, 'NOT_SPECIFIED')
) AS v(ID, CODE) ON v.ID = s.STUDENT_STUDY_STATUS_ID
WHERE s.CODE IS NULL OR s.CODE <> v.CODE;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'UQ_STUDENT_STUDY_STATUS_CODE'
                 AND object_id = OBJECT_ID('MOHECORE.STUDENT_STUDY_STATUS'))
BEGIN
    CREATE UNIQUE INDEX UQ_STUDENT_STUDY_STATUS_CODE
        ON MOHECORE.STUDENT_STUDY_STATUS (CODE)
        WHERE CODE IS NOT NULL;
END
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 2. STUDENT_STUDY_TYPE.IS_SCHOLARSHIP — funding source, where one applies.
 *
 * 1 = ministry/externally funded, 0 = self-funded, NULL = not a funding
 * classification at all (the modality rows) or not recorded.
 *
 * NULL is load-bearing: a report must be able to say "of 941 students, 3 are
 * funded, 0 are self-funded and 938 are unclassified" rather than silently
 * folding the unknowns into "regular".
 * ──────────────────────────────────────────────────────────────────────────── */
IF COL_LENGTH('MOHECORE.STUDENT_STUDY_TYPE', 'IS_SCHOLARSHIP') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_STUDY_TYPE ADD IS_SCHOLARSHIP BIT NULL;
END
GO

UPDATE t SET t.IS_SCHOLARSHIP = v.IS_SCHOLARSHIP
FROM MOHECORE.STUDENT_STUDY_TYPE t
JOIN (VALUES
    (   1, CAST(1 AS BIT)),   -- Government Funded
    (   2, CAST(1 AS BIT)),   -- Cultural Exchange
    (   3, CAST(0 AS BIT)),   -- Self-Funded
    (   4, CAST(1 AS BIT))    -- Assistance - Tuition Fees
) AS v(ID, IS_SCHOLARSHIP) ON v.ID = t.STUDENT_STUDY_TYPE_ID
WHERE t.IS_SCHOLARSHIP IS NULL OR t.IS_SCHOLARSHIP <> v.IS_SCHOLARSHIP;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 3. STUDY_COUNTRY_IN.LATITUDE / LONGITUDE — replaces the hardcoded Java map.
 *
 * Seeded by ID so the pin survives a rename or a translation. Countries with no
 * meaningful location (Not Specified) are left NULL, and the geographic endpoint
 * now omits them rather than drawing them at (0,0) in the Gulf of Guinea.
 *
 * Egypt used to appear twice here, as ids 1 and 1011, and this block seeded both
 * so the map pinned correctly either way. V048 removed that duplicate and
 * standardised Egypt on id 1, so the 1011 line is gone. The UPDATE joins on id,
 * so it stays re-runnable against a database at either version.
 * ──────────────────────────────────────────────────────────────────────────── */
IF COL_LENGTH('MOHECORE.STUDY_COUNTRY_IN', 'LATITUDE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDY_COUNTRY_IN ADD LATITUDE DECIMAL(9,6) NULL;
END
GO

IF COL_LENGTH('MOHECORE.STUDY_COUNTRY_IN', 'LONGITUDE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDY_COUNTRY_IN ADD LONGITUDE DECIMAL(9,6) NULL;
END
GO

UPDATE c SET c.LATITUDE = v.LAT, c.LONGITUDE = v.LON
FROM MOHECORE.STUDY_COUNTRY_IN c
JOIN (VALUES
    (   1,  26.820600,  30.802500),   -- Egypt
    (1000, -25.274400, 133.775100),   -- Australia
    (1001,  51.165700,  10.451500),   -- Germany
    (1002,  23.424100,  53.847800),   -- United Arab Emirates
    (1003,  35.861700, 104.195400),   -- China
    (1004,  31.240000,  36.510000),   -- Jordan
    (1005,  23.885900,  45.079200),   -- Saudi Arabia
    (1006,  55.378100,  -3.436000),   -- United Kingdom
    (1007,  37.090200, -95.712900),   -- United States of America
    (1008,  38.963700,  35.243300),   -- Turkey
    (1009,  12.862800,  30.217600),   -- Sudan
    (1010,  20.593700,  78.962900),   -- India
    (1012,  46.227600,   2.213700),   -- France
    (1013,  56.130400, -106.346800),  -- Canada
    (1014,   4.210500, 101.975800)    -- Malaysia
) AS v(ID, LAT, LON) ON v.ID = c.STUDY_COUNTRY_IN_ID
WHERE c.LATITUDE IS NULL OR c.LONGITUDE IS NULL;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 4. LU_TREND_CLASS — the remaining classification groups.
 *
 * GROWTH_STATUS and CONFIDENCE were seeded by V004. These two are the ones the
 * native queries still emit as string literals.
 * ──────────────────────────────────────────────────────────────────────────── */
-- THRESHOLD_MIN carries the lower bound for classifications that are decided by
-- a number. DistributionStatisticsRepository had these as literals inside the
-- SQL (>= 20 'major', >= 5 'minor'), so the cut-off between a "major" and a
-- "minor" concentration of students was a magic number in a query string.
-- NULL for classifications that are not threshold-based (CONFIDENCE, TREND_
-- DIRECTION, GROWTH_STATUS all derive from their own arithmetic).
IF COL_LENGTH('MOHECORE.LU_TREND_CLASS', 'THRESHOLD_MIN') IS NULL
BEGIN
    ALTER TABLE MOHECORE.LU_TREND_CLASS ADD THRESHOLD_MIN DECIMAL(18,2) NULL;
END
GO

MERGE MOHECORE.LU_TREND_CLASS AS tgt
USING (VALUES
    ('DISTRIBUTION_SIGNIFICANCE', 'MAJOR',    N'رئيسي',  'Major',    1, CAST(20 AS DECIMAL(18,2))),
    ('DISTRIBUTION_SIGNIFICANCE', 'MINOR',    N'ثانوي',  'Minor',    2, CAST( 5 AS DECIMAL(18,2))),
    ('DISTRIBUTION_SIGNIFICANCE', 'MARGINAL', N'هامشي',  'Marginal', 3, CAST( 0 AS DECIMAL(18,2))),
    ('TREND_DIRECTION',           'UP',       N'صاعد',   'Up',       1, NULL),
    ('TREND_DIRECTION',           'DOWN',     N'هابط',   'Down',     2, NULL),
    ('TREND_DIRECTION',           'FLAT',     N'مستقر',  'Flat',     3, NULL)
) AS src(CATEGORY, CODE, NAME_AR, NAME_EN, SORT_ORDER, THRESHOLD_MIN)
    ON tgt.CATEGORY = src.CATEGORY AND tgt.CODE = src.CODE
WHEN NOT MATCHED BY TARGET THEN
    INSERT (CATEGORY, CODE, NAME_AR, NAME_EN, SORT_ORDER, THRESHOLD_MIN, IS_DELETED)
    VALUES (src.CATEGORY, src.CODE, src.NAME_AR, src.NAME_EN, src.SORT_ORDER, src.THRESHOLD_MIN, 0)
WHEN MATCHED AND ISNULL(tgt.THRESHOLD_MIN, -1) <> ISNULL(src.THRESHOLD_MIN, -1) THEN
    UPDATE SET THRESHOLD_MIN = src.THRESHOLD_MIN;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 5. LU_REPORT_DIMENSION — the age bands the demographics metadata service
 * currently hardcodes as a Java List.of(...).
 *
 * The boundaries are the ministry's reporting bands; keeping them here means a
 * change is a data edit, not a redeploy.
 * ──────────────────────────────────────────────────────────────────────────── */
/* ─────────────────────────────────────────────────────────────────────────────
 * 6. STUDENT_DEGREE.LEVEL_CODE — collapses 14 degree rows onto the four levels
 * the periodic reports actually break down by.
 *
 * The list has accumulated duplicates and compound entries over the years
 * (both 1 and 102 are "Bachelor's Degree"; 5 is "Master's and Doctorate";
 * 7 is "Diploma + Master's"), so no name match or ID range works. Compound
 * degrees are classified by their terminal award, which is what "degrees
 * awarded" counts.
 * ──────────────────────────────────────────────────────────────────────────── */
IF COL_LENGTH('MOHECORE.STUDENT_DEGREE', 'LEVEL_CODE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DEGREE ADD LEVEL_CODE VARCHAR(20) NULL;
END
GO

UPDATE d SET d.LEVEL_CODE = v.LEVEL_CODE
FROM MOHECORE.STUDENT_DEGREE d
JOIN (VALUES
    (  1, 'BACHELOR'),   -- Bachelor's Degree
    (  2, 'MASTER'),     -- Postgraduate - Master's Degree
    (  3, 'PHD'),        -- Postgraduate - Doctorate (PhD)
    (  4, 'OTHER'),      -- Fellowship
    (  5, 'PHD'),        -- Postgraduate - Master's and Doctorate (terminal award)
    (  6, 'OTHER'),      -- Board Certification
    (  7, 'MASTER'),     -- Diploma + Master's Degree (terminal award)
    (100, 'DIPLOMA'),    -- Diploma
    (101, 'DIPLOMA'),    -- Higher Diploma
    (102, 'BACHELOR'),   -- Bachelor's Degree (duplicate of 1)
    (103, 'MASTER'),     -- Master's Degree
    (104, 'PHD'),        -- Doctorate (PhD)
    (105, 'DIPLOMA')     -- Higher Diploma (duplicate of 101)
    -- 99 "Not Specified" is deliberately left NULL.
) AS v(ID, LEVEL_CODE) ON v.ID = d.STUDENT_DEGREE_ID
WHERE d.LEVEL_CODE IS NULL OR d.LEVEL_CODE <> v.LEVEL_CODE;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 7. REPORT_METRIC_TARGET — where KPI targets live.
 *
 * PerformanceMetricsRepository asserted a target of 70% for the graduation
 * metric, as the literal CAST(70 AS DECIMAL(18,2)), and derived on_target /
 * below_target from the same 70. Nothing in the database backed that number and
 * nobody at the ministry set it.
 *
 * This table is created EMPTY and stays empty. That is the point: with no row,
 * target_value and achievement_percentage report NULL and every metric reads
 * "informational", which is the truth — no target has been agreed. When the
 * ministry sets one, inserting a row here makes the metric evaluate against it
 * with no code change and no redeploy.
 * ──────────────────────────────────────────────────────────────────────────── */
IF OBJECT_ID('MOHECORE.REPORT_METRIC_TARGET', 'U') IS NULL
BEGIN
    CREATE TABLE MOHECORE.REPORT_METRIC_TARGET (
        METRIC_TARGET_ID  INT IDENTITY(1,1) NOT NULL,
        METRIC_CATEGORY   VARCHAR(60)    NOT NULL,
        METRIC_NAME       VARCHAR(100)   NOT NULL,
        TARGET_VALUE      DECIMAL(18,2)  NOT NULL,
        EFFECTIVE_YEAR    INT            NULL,   -- NULL = applies to every year
        COUNTRY_ID        INT            NULL,   -- NULL = ministry-wide
        NOTES             NVARCHAR(1000) NULL,
        CONSTRAINT PK_REPORT_METRIC_TARGET PRIMARY KEY (METRIC_TARGET_ID),
        CONSTRAINT UQ_REPORT_METRIC_TARGET
            UNIQUE (METRIC_CATEGORY, METRIC_NAME, EFFECTIVE_YEAR, COUNTRY_ID)
    );
END
GO

MERGE MOHECORE.LU_REPORT_DIMENSION AS tgt
USING (VALUES
    ('AGE_RANGE', 'UNDER_20', N'أقل من 20', 'Under 20', 1),
    ('AGE_RANGE', '20_24',    N'20 - 24',   '20-24',    2),
    ('AGE_RANGE', '25_29',    N'25 - 29',   '25-29',    3),
    ('AGE_RANGE', '30_34',    N'30 - 34',   '30-34',    4),
    ('AGE_RANGE', '35_39',    N'35 - 39',   '35-39',    5),
    ('AGE_RANGE', '40_PLUS',  N'40 فأكثر',  '40+',      6)
) AS src(GROUP_CODE, CODE, NAME_AR, NAME_EN, SORT_ORDER)
    ON tgt.GROUP_CODE = src.GROUP_CODE AND tgt.CODE = src.CODE
WHEN NOT MATCHED BY TARGET THEN
    INSERT (GROUP_CODE, CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED)
    VALUES (src.GROUP_CODE, src.CODE, src.NAME_AR, src.NAME_EN, src.SORT_ORDER, 0);
GO
