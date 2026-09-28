/*
    WD-15 — the entitlement amounts and study durations, derived from the
    records that already hold them.

    An adhoc script, not a migration: it reads student records, and records
    differ between environments. Run it on dev, read the counts, then run the
    same file on production — where it will find whatever real students are
    there, country by country.

    The problem it solves
    ---------------------
    `STUDY_AMOUNT_LOOKUP` and `STUDY_YEARS_LOOKUP` answer two questions on the
    Add Student screen: what is this student entitled to, and how long does the
    course run. Both are resolved by `StudyAmountLookupService.resolve` /
    `StudyYearsLookupService.resolve`, which are already country-aware — a row
    with a country wins, a row with none is the shared default.

    The mechanism was right and the data was not. **Not one of 981 students
    could resolve either figure**, because the 61 configuration rows are keyed
    to a catalogue no student uses:

        degree       the rows use 100-104; students hold 1-7
        agency type  the rows use 100-102 (Government Ministry / Government
                     Entity / Private Sector); students hold 1, 4, 5
                     (جامعة, وزارة, التعليم العالي)
        major        the rows use 1000-1023; students hold 49, 92, 184, 208 …
        currency     the rows use 1009, the Omani Rial; students use 1,
                     the US Dollar

    Four dimensions, four different vocabularies. Those rows are left exactly
    where they are — they match nothing, so they break nothing, and the
    catalogue may be someone's intended direction of travel (recorded on WD-15
    for the ministry).

    Where the figures come from
    ---------------------------
    Not from the ministry, and not invented here: from the students. 977 of 981
    carry a `STUDENT_AMOUNT`, and every one carries a `STUDENT_STUDY_NO_OF_YEARS`.
    Grouped by the keys the resolver uses, they agree with themselves almost
    everywhere — 10 of 13 amount groups and 154 of 161 duration groups hold a
    single value. What each student is actually paid *is* the rule, written
    down one student at a time.

    Where a group does not agree, the value the most students hold is written
    and **the split is printed**, so it can be read and checked rather than
    buried. On dev the two real disagreements are lopsided enough to settle
    themselves: عليا-ماجستير + التعليم العالي is 1860 × 31 against 1500 × 1, and
    عليا-دكتوراه + جامعة is 1920 × 5 against 2220 × 2. A genuine tie is never
    written — it is reported and skipped, because a coin toss is not a rule.

    Country scope
    -------------
    Rows are written **per country**, from each country's own students. On dev
    that is Egypt alone: the Jordan and Saudi students are the scope-test seed,
    copies of Egyptians carrying Egyptian figures, and they are excluded by
    their marker — otherwise a test fixture would become ministry
    configuration. Nothing is written as a shared default, so no attaché
    silently inherits another's stipend; each gets its own row the first time
    it has real students, or enters one through the admin screen, which already
    restricts a country officer to their own country.

    Identifying what this wrote
    ---------------------------
    Neither table has a note column, so the provenance is not stamped on the
    row. It does not need to be: every pre-existing row has a NULL country and
    every row written here has one. The Egypt-scoped rows are this script's.

    Idempotent: a key that already has a row is left alone, so a re-run adds
    only what is new and never overwrites a figure the ministry has corrected.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

-- STUDENT_DATA is under the tenant policy; without this the script sees no
-- rows and reports a cheerful zero.
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @SeedMarker NVARCHAR(50) = N'ZZ-SCOPE-SEED';

/* ---------------------------------------------------------------------------
   The students the figures are read from.
   --------------------------------------------------------------------------- */
IF OBJECT_ID('tempdb..#src') IS NOT NULL DROP TABLE #src;

SELECT d.STUDY_COUNTRY_IN            AS country_id,
       d.STUDENT_DEGREE_ID           AS degree_id,
       d.STUDENT_AGENCY_TYPE_ID      AS agency_id,
       d.STUDENT_MAJOR_ID            AS major_id,
       d.STUDENT_AMOUNT              AS amount,
       d.amount_currency             AS currency_id,
       d.STUDENT_STUDY_NO_OF_YEARS   AS years
  INTO #src
  FROM MOHECORE.STUDENT_DATA d
 WHERE ISNULL(d.IS_DELETED, 0) = 0
   AND ISNULL(d.STUDY_COUNTRY_IN, 0) <> 0
   AND ISNULL(d.STUDENT_DEGREE_ID, 0) <> 0
   AND ISNULL(CAST(d.STUDENT_NOTE_1 AS NVARCHAR(100)), N'') <> @SeedMarker;

DECLARE @srcCount INT = (SELECT COUNT(*) FROM #src);
PRINT 'Students read: ' + CAST(@srcCount AS varchar(8));

/* ===========================================================================
   1. The entitlement amount, by country, degree and agency type.
   =========================================================================== */
PRINT '';
PRINT '--- Amounts -----------------------------------------------------------';

IF OBJECT_ID('tempdb..#amt') IS NOT NULL DROP TABLE #amt;

-- Every value each group holds, and how many students hold it.
SELECT country_id, degree_id, agency_id,
       amount,
       MIN(currency_id) AS currency_id,
       COUNT(*)         AS held_by,
       ROW_NUMBER() OVER (PARTITION BY country_id, degree_id, agency_id
                          ORDER BY COUNT(*) DESC, amount ASC) AS rank_,
       COUNT(*) OVER (PARTITION BY country_id, degree_id, agency_id) AS dummy
  INTO #amt
  FROM #src
 WHERE ISNULL(agency_id, 0) <> 0
   AND ISNULL(amount, 0) > 0
 GROUP BY country_id, degree_id, agency_id, amount;

PRINT '';
PRINT 'Groups whose students do not all hold the same amount:';
SELECT N'  country ' + CAST(a.country_id AS varchar(6))
     + N', degree ' + CAST(a.degree_id AS varchar(6))
     + N', agency ' + CAST(a.agency_id AS varchar(6))
     + N'  ->  ' + CAST(a.amount AS varchar(20)) + N' x ' + CAST(a.held_by AS varchar(6))
     -- The label has to agree with what the INSERT below actually does. A
     -- top value that ties with the second is not a majority and is skipped,
     -- so saying "written" against it would be a report contradicting its own
     -- script.
     + CASE WHEN a.rank_ <> 1 THEN N''
            WHEN EXISTS (SELECT 1 FROM #amt t
                          WHERE t.country_id = a.country_id AND t.degree_id = a.degree_id
                            AND t.agency_id = a.agency_id AND t.rank_ = 2
                            AND t.held_by = a.held_by)
                 THEN N'   <= tied, nothing written'
            ELSE N'   <= written' END AS split
  FROM #amt a
 WHERE EXISTS (SELECT 1 FROM #amt b
                WHERE b.country_id = a.country_id AND b.degree_id = a.degree_id
                  AND b.agency_id = a.agency_id AND b.rank_ = 2)
 ORDER BY a.country_id, a.degree_id, a.agency_id, a.rank_;

INSERT INTO MOHECORE.STUDY_AMOUNT_LOOKUP
       (STUDENT_DEGREE_ID, STUDENT_AGENCY_TYPE_ID, AMOUNT, CURRENCY_ID, STUDY_COUNTRY_IN_ID)
SELECT a.degree_id, a.agency_id, a.amount, NULLIF(a.currency_id, 0), a.country_id
  FROM #amt a
 WHERE a.rank_ = 1
   -- a tie is not a majority
   AND NOT EXISTS (SELECT 1 FROM #amt b
                    WHERE b.country_id = a.country_id AND b.degree_id = a.degree_id
                      AND b.agency_id = a.agency_id AND b.rank_ = 2
                      AND b.held_by = a.held_by)
   -- never overwrite a figure already configured for this key
   AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_AMOUNT_LOOKUP e
                    WHERE e.STUDENT_DEGREE_ID = a.degree_id
                      AND e.STUDENT_AGENCY_TYPE_ID = a.agency_id
                      AND (e.STUDY_COUNTRY_IN_ID = a.country_id
                           OR (e.STUDY_COUNTRY_IN_ID IS NULL AND a.country_id IS NULL)));

DECLARE @amtWritten INT = @@ROWCOUNT;
PRINT '';
PRINT 'Amount rows written: ' + CAST(@amtWritten AS varchar(8));

/* ===========================================================================
   2. The study duration, by country, degree and major.
   =========================================================================== */
PRINT '';
PRINT '--- Study durations ---------------------------------------------------';

IF OBJECT_ID('tempdb..#yrs') IS NOT NULL DROP TABLE #yrs;

SELECT country_id, degree_id, major_id,
       years,
       COUNT(*) AS held_by,
       ROW_NUMBER() OVER (PARTITION BY country_id, degree_id, major_id
                          ORDER BY COUNT(*) DESC, years ASC) AS rank_
  INTO #yrs
  FROM #src
 WHERE ISNULL(major_id, 0) <> 0
   AND ISNULL(years, 0) > 0
 GROUP BY country_id, degree_id, major_id, years;

PRINT '';
PRINT 'Groups whose students do not all study for the same number of years:';
SELECT N'  country ' + CAST(a.country_id AS varchar(6))
     + N', degree ' + CAST(a.degree_id AS varchar(6))
     + N', major ' + CAST(a.major_id AS varchar(8))
     + N'  ->  ' + CAST(a.years AS varchar(6)) + N' years x ' + CAST(a.held_by AS varchar(6))
     + CASE WHEN a.rank_ <> 1 THEN N''
            WHEN EXISTS (SELECT 1 FROM #yrs t
                          WHERE t.country_id = a.country_id AND t.degree_id = a.degree_id
                            AND t.major_id = a.major_id AND t.rank_ = 2
                            AND t.held_by = a.held_by)
                 THEN N'   <= tied, nothing written'
            ELSE N'   <= written' END AS split
  FROM #yrs a
 WHERE EXISTS (SELECT 1 FROM #yrs b
                WHERE b.country_id = a.country_id AND b.degree_id = a.degree_id
                  AND b.major_id = a.major_id AND b.rank_ = 2)
 ORDER BY a.country_id, a.degree_id, a.major_id, a.rank_;

INSERT INTO MOHECORE.STUDY_YEARS_LOOKUP
       (STUDENT_DEGREE_ID, STUDENT_MAJOR_ID, STUDY_YEARS, STUDY_COUNTRY_IN_ID)
SELECT a.degree_id, a.major_id, a.years, a.country_id
  FROM #yrs a
 WHERE a.rank_ = 1
   AND NOT EXISTS (SELECT 1 FROM #yrs b
                    WHERE b.country_id = a.country_id AND b.degree_id = a.degree_id
                      AND b.major_id = a.major_id AND b.rank_ = 2
                      AND b.held_by = a.held_by)
   AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_YEARS_LOOKUP e
                    WHERE e.STUDENT_DEGREE_ID = a.degree_id
                      AND e.STUDENT_MAJOR_ID = a.major_id
                      AND (e.STUDY_COUNTRY_IN_ID = a.country_id
                           OR (e.STUDY_COUNTRY_IN_ID IS NULL AND a.country_id IS NULL)));

DECLARE @yrsWritten INT = @@ROWCOUNT;
PRINT '';
PRINT 'Study-years rows written: ' + CAST(@yrsWritten AS varchar(8));

/* ===========================================================================
   3. What the configuration reaches now.
   =========================================================================== */
DECLARE @canAmount INT = (
    SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA d
     WHERE ISNULL(d.IS_DELETED,0) = 0
       AND EXISTS (SELECT 1 FROM MOHECORE.STUDY_AMOUNT_LOOKUP a
                    WHERE a.STUDENT_DEGREE_ID = d.STUDENT_DEGREE_ID
                      AND a.STUDENT_AGENCY_TYPE_ID = d.STUDENT_AGENCY_TYPE_ID
                      AND (a.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN
                           OR a.STUDY_COUNTRY_IN_ID IS NULL)));

DECLARE @canYears INT = (
    SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA d
     WHERE ISNULL(d.IS_DELETED,0) = 0
       AND EXISTS (SELECT 1 FROM MOHECORE.STUDY_YEARS_LOOKUP y
                    WHERE y.STUDENT_DEGREE_ID = d.STUDENT_DEGREE_ID
                      AND y.STUDENT_MAJOR_ID = d.STUDENT_MAJOR_ID
                      AND (y.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN
                           OR y.STUDY_COUNTRY_IN_ID IS NULL)));

DECLARE @live INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED,0) = 0);

PRINT '';
PRINT '--- After -------------------------------------------------------------';
PRINT 'Students who can now resolve an amount:         '
    + CAST(@canAmount AS varchar(8)) + ' of ' + CAST(@live AS varchar(8));
PRINT 'Students who can now resolve a study duration:  '
    + CAST(@canYears AS varchar(8)) + ' of ' + CAST(@live AS varchar(8));

PRINT '';
PRINT 'Students who still cannot resolve an amount, and why:';
SELECT N'  degree ' + CAST(ISNULL(d.STUDENT_DEGREE_ID,0) AS varchar(6))
     + N' (' + ISNULL(dl.STUDENT_DEGREE_NAME, N'unset') + N')'
     + N', agency ' + CAST(ISNULL(d.STUDENT_AGENCY_TYPE_ID,0) AS varchar(6))
     + N', country ' + CAST(ISNULL(d.STUDY_COUNTRY_IN,0) AS varchar(6))
     + N'  ->  ' + CAST(COUNT(*) AS varchar(6)) + N' student(s)' AS still_missing
  FROM MOHECORE.STUDENT_DATA d
  LEFT JOIN MOHECORE.STUDENT_DEGREE dl ON dl.STUDENT_DEGREE_ID = d.STUDENT_DEGREE_ID
 WHERE ISNULL(d.IS_DELETED,0) = 0
   AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_AMOUNT_LOOKUP a
                    WHERE a.STUDENT_DEGREE_ID = d.STUDENT_DEGREE_ID
                      AND a.STUDENT_AGENCY_TYPE_ID = d.STUDENT_AGENCY_TYPE_ID
                      AND (a.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN
                           OR a.STUDY_COUNTRY_IN_ID IS NULL))
 GROUP BY d.STUDENT_DEGREE_ID, dl.STUDENT_DEGREE_NAME, d.STUDENT_AGENCY_TYPE_ID, d.STUDY_COUNTRY_IN
 ORDER BY COUNT(*) DESC;

DROP TABLE #src;
DROP TABLE #amt;
DROP TABLE #yrs;
