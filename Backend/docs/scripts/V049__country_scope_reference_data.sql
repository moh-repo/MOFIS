/*
================================================================================
V049 — A country dimension for the study-years and study-amount lookups
================================================================================

Runs after V048. V048 standardised Egypt on id 1; this script assigns rows to
Egypt, and "Egypt" has to mean one id by then.

Why
---
Each attaché may set its own number of study years for a degree and major, and
its own finance amount for a degree and agency type. Today both lookups hold one
global row per key:

    STUDY_YEARS_LOOKUP    49 rows, unique on (degree, major)
    STUDY_AMOUNT_LOOKUP   18 rows, unique on (degree, agency type)

The model is a **shared default with a per-country override**. The existing rows
keep a NULL country and go on applying everywhere; an attaché adds a row only
where its rule differs, and that row wins for that country. Nothing changes on
day one and no data is migrated.

The mechanism, and why the unique constraint is the whole design
----------------------------------------------------------------
SQL Server treats NULLs as *equal* to one another inside a UNIQUE constraint.
So UNIQUE (STUDY_COUNTRY_IN_ID, STUDENT_DEGREE_ID, STUDENT_MAJOR_ID) permits:

    (NULL, 1, 5)   <- the default for degree 1 / major 5   ... exactly one
    (1004, 1, 5)   <- Jordan's override                    ... at most one
    (1005, 1, 5)   <- Saudi's override                     ... at most one

and rejects a second row with a NULL country for the same key. The "there is
exactly one default" half of the model is therefore enforced by the database
rather than by convention in a service. This is the one place where SQL Server's
unusual NULL handling in unique constraints is an asset rather than a trap.

Resolution lives in StudyYearsLookupService / StudyAmountLookupService: the
country row is preferred and the NULL row is the fallback, in one query ordered
so the override sorts first.

Also here
---------
The two "Not Specified" sentinel rows on the bank lists disagreed with each other
and with everything else — bank 999 carried country 0, branch 999 carried 999.
Neither is a real attaché and nothing in the database referenced either, so both
become Egypt's and then follow the ordinary country rule with no special case in
the scope specification. Consequence, stated plainly: attachés other than Egypt
no longer see a "Not Specified" bank or branch.

Re-runnable. A second run reports zero changes.

The PRINT and @@ROWCOUNT traps V048 documents apply here too: counts are hoisted
into variables, and rowcounts are captured with SET immediately after the
statement they belong to.
================================================================================
*/

SET NOCOUNT ON;
GO

PRINT '';
PRINT '=== V049: country dimension for the study lookups ===';
GO

/* ---------------------------------------------------------------------------
 * 1. The columns.
 * ------------------------------------------------------------------------ */
IF COL_LENGTH('MOHECORE.STUDY_YEARS_LOOKUP', 'STUDY_COUNTRY_IN_ID') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDY_YEARS_LOOKUP ADD STUDY_COUNTRY_IN_ID int NULL;
    PRINT 'STUDY_YEARS_LOOKUP.STUDY_COUNTRY_IN_ID added.';
END
ELSE
    PRINT 'STUDY_YEARS_LOOKUP.STUDY_COUNTRY_IN_ID already present.';
GO

IF COL_LENGTH('MOHECORE.STUDY_AMOUNT_LOOKUP', 'STUDY_COUNTRY_IN_ID') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDY_AMOUNT_LOOKUP ADD STUDY_COUNTRY_IN_ID int NULL;
    PRINT 'STUDY_AMOUNT_LOOKUP.STUDY_COUNTRY_IN_ID added.';
END
ELSE
    PRINT 'STUDY_AMOUNT_LOOKUP.STUDY_COUNTRY_IN_ID already present.';
GO

/* ---------------------------------------------------------------------------
 * 2. Widen the uniqueness to include the country.
 *
 * The existing constraints are what make the override impossible: with
 * UNIQUE (degree, major) there can only ever be one row per key, so Jordan
 * could not add its own.
 * ------------------------------------------------------------------------ */
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDY_YEARS_DEGREE_MAJOR'
           AND object_id = OBJECT_ID('MOHECORE.STUDY_YEARS_LOOKUP'))
BEGIN
    ALTER TABLE MOHECORE.STUDY_YEARS_LOOKUP DROP CONSTRAINT UQ_STUDY_YEARS_DEGREE_MAJOR;
    PRINT 'Dropped UQ_STUDY_YEARS_DEGREE_MAJOR (degree, major).';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDY_YEARS_COUNTRY_DEGREE_MAJOR'
               AND object_id = OBJECT_ID('MOHECORE.STUDY_YEARS_LOOKUP'))
BEGIN
    DECLARE @dupYears int;
    SELECT @dupYears = COUNT(*) FROM (
        SELECT 1 AS x FROM MOHECORE.STUDY_YEARS_LOOKUP
        GROUP BY STUDY_COUNTRY_IN_ID, STUDENT_DEGREE_ID, STUDENT_MAJOR_ID HAVING COUNT(*) > 1) d;

    IF @dupYears > 0
        PRINT '*** Skipping UQ_STUDY_YEARS_COUNTRY_DEGREE_MAJOR: ' + CAST(@dupYears AS varchar(10)) + ' duplicate key(s).';
    ELSE
    BEGIN
        ALTER TABLE MOHECORE.STUDY_YEARS_LOOKUP
            ADD CONSTRAINT UQ_STUDY_YEARS_COUNTRY_DEGREE_MAJOR
            UNIQUE (STUDY_COUNTRY_IN_ID, STUDENT_DEGREE_ID, STUDENT_MAJOR_ID);
        PRINT 'Created UQ_STUDY_YEARS_COUNTRY_DEGREE_MAJOR (country, degree, major).';
    END
END
ELSE
    PRINT 'UQ_STUDY_YEARS_COUNTRY_DEGREE_MAJOR already present.';
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDY_AMOUNT_DEGREE_AGENCY'
           AND object_id = OBJECT_ID('MOHECORE.STUDY_AMOUNT_LOOKUP'))
BEGIN
    ALTER TABLE MOHECORE.STUDY_AMOUNT_LOOKUP DROP CONSTRAINT UQ_STUDY_AMOUNT_DEGREE_AGENCY;
    PRINT 'Dropped UQ_STUDY_AMOUNT_DEGREE_AGENCY (degree, agency).';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDY_AMOUNT_COUNTRY_DEGREE_AGENCY'
               AND object_id = OBJECT_ID('MOHECORE.STUDY_AMOUNT_LOOKUP'))
BEGIN
    DECLARE @dupAmt int;
    SELECT @dupAmt = COUNT(*) FROM (
        SELECT 1 AS x FROM MOHECORE.STUDY_AMOUNT_LOOKUP
        GROUP BY STUDY_COUNTRY_IN_ID, STUDENT_DEGREE_ID, STUDENT_AGENCY_TYPE_ID HAVING COUNT(*) > 1) d;

    IF @dupAmt > 0
        PRINT '*** Skipping UQ_STUDY_AMOUNT_COUNTRY_DEGREE_AGENCY: ' + CAST(@dupAmt AS varchar(10)) + ' duplicate key(s).';
    ELSE
    BEGIN
        ALTER TABLE MOHECORE.STUDY_AMOUNT_LOOKUP
            ADD CONSTRAINT UQ_STUDY_AMOUNT_COUNTRY_DEGREE_AGENCY
            UNIQUE (STUDY_COUNTRY_IN_ID, STUDENT_DEGREE_ID, STUDENT_AGENCY_TYPE_ID);
        PRINT 'Created UQ_STUDY_AMOUNT_COUNTRY_DEGREE_AGENCY (country, degree, agency).';
    END
END
ELSE
    PRINT 'UQ_STUDY_AMOUNT_COUNTRY_DEGREE_AGENCY already present.';
GO

/* ---------------------------------------------------------------------------
 * 3. Supporting indexes for the resolution query.
 * ------------------------------------------------------------------------ */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_STUDY_YEARS_LOOKUP_COUNTRY'
               AND object_id = OBJECT_ID('MOHECORE.STUDY_YEARS_LOOKUP'))
BEGIN
    CREATE INDEX IX_STUDY_YEARS_LOOKUP_COUNTRY
        ON MOHECORE.STUDY_YEARS_LOOKUP (STUDY_COUNTRY_IN_ID);
    PRINT 'IX_STUDY_YEARS_LOOKUP_COUNTRY created.';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_STUDY_AMOUNT_LOOKUP_COUNTRY'
               AND object_id = OBJECT_ID('MOHECORE.STUDY_AMOUNT_LOOKUP'))
BEGIN
    CREATE INDEX IX_STUDY_AMOUNT_LOOKUP_COUNTRY
        ON MOHECORE.STUDY_AMOUNT_LOOKUP (STUDY_COUNTRY_IN_ID);
    PRINT 'IX_STUDY_AMOUNT_LOOKUP_COUNTRY created.';
END
GO

/* ---------------------------------------------------------------------------
 * 4. The bank / branch sentinels become Egypt's.
 * ------------------------------------------------------------------------ */
DECLARE @bank int, @branch int;

/* The sentinels by id, and anything whose country is missing or points at no
 * real row. Bank 999 carried country 0 (not a country at all); branch 999
 * carried 999, which *is* a valid country — "Not Specified" — so a catch-all
 * for invalid values alone would have left the branch behind while moving its
 * bank, and a branch has to agree with the bank it belongs to. Hence both the
 * explicit ids and the invalid-value sweep. */
/* The trailing "and not already Egypt" is what makes a re-run report zero.
 * Without it the sentinel clause matches its own result every time, and a
 * migration that claims to have done work on every run is one you can no longer
 * re-run as a check. */
UPDATE MOHECORE.STUDY_FINANCE_BANK SET STUDY_COUNTRY_IN_ID = 1
WHERE (STUDY_FINANCE_BANKE_ID = 999
       OR STUDY_COUNTRY_IN_ID IS NULL
       OR STUDY_COUNTRY_IN_ID NOT IN (SELECT STUDY_COUNTRY_IN_ID FROM MOHECORE.STUDY_COUNTRY_IN))
  AND (STUDY_COUNTRY_IN_ID IS NULL OR STUDY_COUNTRY_IN_ID <> 1);
SET @bank = @@ROWCOUNT;

UPDATE MOHECORE.STUDY_FINANCE_BANK_BRANCH SET STUDY_COUNTRY_IN_ID = 1
WHERE (STUDY_FINANCE_BANK_BRANCH_ID = 999
       OR STUDY_COUNTRY_IN_ID IS NULL
       OR STUDY_COUNTRY_IN_ID NOT IN (SELECT STUDY_COUNTRY_IN_ID FROM MOHECORE.STUDY_COUNTRY_IN))
  AND (STUDY_COUNTRY_IN_ID IS NULL OR STUDY_COUNTRY_IN_ID <> 1);
SET @branch = @@ROWCOUNT;

PRINT 'Banks assigned to Egypt (sentinel or no valid country):    ' + CAST(@bank AS varchar(10));
PRINT 'Branches assigned to Egypt (sentinel or no valid country): ' + CAST(@branch AS varchar(10));
GO

/* ---------------------------------------------------------------------------
 * Result
 * ------------------------------------------------------------------------ */
DECLARE @years int, @yearsDefault int, @amt int, @amtDefault int, @badBank int, @badBranch int;

SELECT @years = COUNT(*), @yearsDefault = SUM(CASE WHEN STUDY_COUNTRY_IN_ID IS NULL THEN 1 ELSE 0 END)
FROM MOHECORE.STUDY_YEARS_LOOKUP;
SELECT @amt = COUNT(*), @amtDefault = SUM(CASE WHEN STUDY_COUNTRY_IN_ID IS NULL THEN 1 ELSE 0 END)
FROM MOHECORE.STUDY_AMOUNT_LOOKUP;
SELECT @badBank = COUNT(*) FROM MOHECORE.STUDY_FINANCE_BANK b
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = b.STUDY_COUNTRY_IN_ID);
SELECT @badBranch = COUNT(*) FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH b
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = b.STUDY_COUNTRY_IN_ID);

PRINT '';
PRINT '--- V049 result ---';
PRINT 'Study years rows:      ' + CAST(@years AS varchar(10)) + ' (' + CAST(@yearsDefault AS varchar(10)) + ' shared defaults)';
PRINT 'Study amount rows:     ' + CAST(@amt AS varchar(10)) + ' (' + CAST(@amtDefault AS varchar(10)) + ' shared defaults)';
PRINT 'Banks with no country:    ' + CAST(@badBank AS varchar(10));
PRINT 'Branches with no country: ' + CAST(@badBranch AS varchar(10));
PRINT '';
GO
