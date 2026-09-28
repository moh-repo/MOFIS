/*
    V038 — one finance row per student per quarter, enforced.

    Why
    ---
    STUDENT_FINANCE holds the current quarter; STUDENT_FINANCE_ARCH holds
    history. Every quarter the current table is snapshotted into the archive and
    the new quarter's adds, edits and deletes are applied to the live table. The
    rule that makes that work is that (student, year, quarter) never repeats.

    One table enforces it. The other does not.

        table                  primary key                unique(student,year,q)
        ---------------------  -------------------------  ----------------------
        STUDENT_FINANCE        STUDENT_ID  (alone)         implied, and stricter
        STUDENT_FINANCE_ARCH   FIN_ARCH_ENTRY_ID           NONE

    STUDENT_FINANCE cannot break the rule: its primary key is STUDENT_ID by
    itself, so the table physically holds at most one row per student. That is
    stricter than the rule and consistent with it being a current-quarter
    snapshot. The index added below is therefore documentation of intent rather
    than new protection — but it is protection the day anyone widens that primary
    key to let the table carry more than one quarter.

    STUDENT_FINANCE_ARCH is genuinely unprotected. It has a surrogate key and
    3,764 rows: 941 students x exactly 4 quarters of 2024. Nothing stops a second
    archive of the same quarter. If the rollover were run twice — a retry after a
    timeout, a re-run after a partial failure — every historical total would
    silently double, and the only symptom would be numbers that look plausible
    and are wrong. That is the defect this script closes.

    Both tables are clean right now (0 duplicate groups in each), so the indexes
    go on without repairing data first. The script re-checks anyway: it was
    written against one state of the database and must not assume it still holds.

    What this script deliberately does NOT do
    -----------------------------------------
    1. It does not add CHECK (quarter BETWEEN 1 AND 4), though the column is
       named ..._MONTH and will otherwise attract a month.

       StudentFinanceService.INSERT_FINANCE_FROM_STUDENT_SQL derives the quarter
       from STUDENT_DATA:

           year    = ISNULL(STUDENT_ADDED_QUARTER, 0) / 10
           quarter = ISNULL(STUDENT_ADDED_QUARTER, 0) % 10

       and 938 of 941 students have STUDENT_ADDED_QUARTER = 0. Only three carry a
       real value (20251 twice, 20241 once). So that insert writes year 0,
       quarter 0 for 99.7% of students, and a CHECK would break onboarding for
       almost everyone.

       Writing quarter 0 is itself a defect — a newly onboarded student's finance
       row claims quarter 0 of year 0 — but fixing it means changing what the
       insert does when STUDENT_ADDED_QUARTER is absent (fall back to the current
       open quarter, or refuse the insert), and that is a behaviour change to
       decide deliberately rather than smuggle into a constraint script. The
       verification block below reports the count so it stays visible.

    2. It does not make STUDENT_FIN_RECIEVED_YEAR / _MONTH NOT NULL on
       STUDENT_FINANCE, though both have zero NULLs today and the archive table
       already declares them NOT NULL. StudentFinanceController exposes
       POST /api/v1/student-finance/ which saves a client-supplied entity, and
       those two fields are optional on the way in. NOT NULL would turn a partial
       POST into a 500.

    3. It does not touch STUDY_UNIVERSITY_FEE / _ARCH. Those follow the archive
       pattern but are keyed by academic year and semester
       (STUDY_UNIVERSITY_FEE_YEAR, STUDY_UNIVERSITY_FEE_SEMESTER_NUMBER), not by
       quarter — a semester is not a quarter, and the rule stated for the finance
       tables does not automatically carry across. (student, year, semester) has
       0 duplicates there today; the equivalent constraint is a small follow-up
       once that rule is confirmed.

    Rollback
    --------
        DROP INDEX UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER ON MOHECORE.STUDENT_FINANCE_ARCH;
        DROP INDEX UX_STUDENT_FINANCE_STUDENT_QUARTER      ON MOHECORE.STUDENT_FINANCE;
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ------------------------------------------------------------------ *
 * 1. Refuse to run if either table already violates the rule.
 *    Creating the index would fail anyway; failing here says why, and
 *    says it before anything has been changed.
 * ------------------------------------------------------------------ */
DECLARE @finDups int, @archDups int;

SELECT @finDups = COUNT(*)
FROM (
    SELECT 1 AS x
    FROM MOHECORE.STUDENT_FINANCE
    GROUP BY STUDENT_ID, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH
    HAVING COUNT(*) > 1
) d;

SELECT @archDups = COUNT(*)
FROM (
    SELECT 1 AS x
    FROM MOHECORE.STUDENT_FINANCE_ARCH
    GROUP BY STUDENT_ID, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH
    HAVING COUNT(*) > 1
) d;

IF @finDups > 0 OR @archDups > 0
BEGIN
    PRINT '';
    PRINT '*** V038 ABORTED — duplicate (student, year, quarter) rows exist. ***';
    PRINT '    STUDENT_FINANCE duplicate groups      : ' + CAST(@finDups  AS varchar(20));
    PRINT '    STUDENT_FINANCE_ARCH duplicate groups : ' + CAST(@archDups AS varchar(20));
    PRINT '';
    PRINT '    List them with:';
    PRINT '      SELECT STUDENT_ID, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH, COUNT(*)';
    PRINT '      FROM MOHECORE.STUDENT_FINANCE_ARCH';
    PRINT '      GROUP BY STUDENT_ID, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH';
    PRINT '      HAVING COUNT(*) > 1;';
    PRINT '';
    PRINT '    Decide which row is authoritative before re-running. Do not';
    PRINT '    delete blindly: a duplicated archive quarter may be a doubled';
    PRINT '    rollover, in which case the totals for that quarter are wrong';
    PRINT '    and the surviving row still needs checking against the source.';
    THROW 50038, 'V038: duplicate (student, year, quarter) rows must be resolved first.', 1;
END

PRINT 'V038 pre-check: no duplicate (student, year, quarter) rows in either table.';
GO

/* ------------------------------------------------------------------ *
 * 2. STUDENT_FINANCE_ARCH — the one that is actually unprotected.
 * ------------------------------------------------------------------ */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_FINANCE_ARCH')
      AND name = 'UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER'
)
BEGIN
    CREATE UNIQUE INDEX UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER
        ON MOHECORE.STUDENT_FINANCE_ARCH
           (STUDENT_ID, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH);
    PRINT 'V038: created UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER.';
END
ELSE
    PRINT 'V038: UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER already present.';
GO

/* ------------------------------------------------------------------ *
 * 3. STUDENT_FINANCE — states the rule the primary key currently
 *    implies, so widening that key later cannot silently drop it.
 * ------------------------------------------------------------------ */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_FINANCE')
      AND name = 'UX_STUDENT_FINANCE_STUDENT_QUARTER'
)
BEGIN
    CREATE UNIQUE INDEX UX_STUDENT_FINANCE_STUDENT_QUARTER
        ON MOHECORE.STUDENT_FINANCE
           (STUDENT_ID, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH);
    PRINT 'V038: created UX_STUDENT_FINANCE_STUDENT_QUARTER.';
END
ELSE
    PRINT 'V038: UX_STUDENT_FINANCE_STUDENT_QUARTER already present.';
GO

/* ------------------------------------------------------------------ *
 * 4. Verification — proves the indexes exist and are unique, and
 *    surfaces the two data problems this script deliberately leaves
 *    alone rather than letting them stay invisible.
 * ------------------------------------------------------------------ */
PRINT '';
PRINT '================ V038 verification ================';

SELECT
    OBJECT_SCHEMA_NAME(i.object_id) + '.' + OBJECT_NAME(i.object_id) AS [table],
    i.name                                                          AS [index],
    i.is_unique                                                     AS [is_unique]
FROM sys.indexes i
WHERE i.name IN ('UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER',
                 'UX_STUDENT_FINANCE_STUDENT_QUARTER');

PRINT '--- both rows above must show is_unique = 1 ---';
PRINT '';

/* Quarter values outside 1-4, which no constraint currently forbids. */
SELECT 'STUDENT_FINANCE'      AS [table],
       SUM(CASE WHEN STUDENT_FIN_RECIEVED_MONTH NOT BETWEEN 1 AND 4 THEN 1 ELSE 0 END) AS quarter_out_of_range,
       COUNT(*) AS total_rows
FROM MOHECORE.STUDENT_FINANCE
UNION ALL
SELECT 'STUDENT_FINANCE_ARCH',
       SUM(CASE WHEN STUDENT_FIN_RECIEVED_MONTH NOT BETWEEN 1 AND 4 THEN 1 ELSE 0 END),
       COUNT(*)
FROM MOHECORE.STUDENT_FINANCE_ARCH;

PRINT '--- both are 0 today. See note 1 in the header: no CHECK is added,';
PRINT '    because the onboarding INSERT would write quarter 0 for the 938';
PRINT '    students whose STUDENT_ADDED_QUARTER is 0. ---';
PRINT '';

/* How many students would onboard into quarter 0 right now. */
SELECT COUNT(*) AS students_that_would_onboard_to_quarter_zero
FROM MOHECORE.STUDENT_DATA
WHERE ISNULL(IS_DELETED, 0) = 0
  AND ISNULL(STUDENT_ADDED_QUARTER, 0) % 10 NOT BETWEEN 1 AND 4;

/* YEAR_MONTH should always be year*10 + quarter; drift means the three
   columns disagree about which quarter the row belongs to. */
SELECT 'STUDENT_FINANCE' AS [table],
       SUM(CASE WHEN STUDENT_FIN_RECIEVED_YEAR_MONTH
                     <> STUDENT_FIN_RECIEVED_YEAR * 10 + STUDENT_FIN_RECIEVED_MONTH
                THEN 1 ELSE 0 END) AS year_month_inconsistent
FROM MOHECORE.STUDENT_FINANCE
UNION ALL
SELECT 'STUDENT_FINANCE_ARCH',
       SUM(CASE WHEN STUDENT_FIN_RECIEVED_YEAR_MONTH
                     <> STUDENT_FIN_RECIEVED_YEAR * 10 + STUDENT_FIN_RECIEVED_MONTH
                THEN 1 ELSE 0 END)
FROM MOHECORE.STUDENT_FINANCE_ARCH;

PRINT '--- year_month_inconsistent must be 0 in both ---';
PRINT '=============== end V038 verification =============';
GO
