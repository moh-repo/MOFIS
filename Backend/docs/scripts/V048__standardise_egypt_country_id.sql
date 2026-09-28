/*
================================================================================
V048 — Standardise Egypt on STUDY_COUNTRY_IN_ID = 1
================================================================================

Why
---
MOHECORE.STUDY_COUNTRY_IN holds two rows named Egypt:

    id    1  'مصر'                    (original seed, no created date)
    id 1011  'جمهورية مصر العربية'    (created 2026-05-27 with ids 1000-1014)

Every piece of data in the system uses id 1 — 11,305 audit rows, 5,051 year
results, 3,789 payment lines, 941 students, 941 finance rows, 941 portal
accounts. Id 1011 was referenced by exactly one row: `admin`'s entry in
UMSCORE.UserCountries.

This matters now rather than as tidiness. Country scope decides who may read
what and who may administer whom, and nine screens offer this list as a
dropdown. Anything saved against 1011 — a bank, a branch, a study-years
override — is invisible to every user scoped to Egypt, because they hold 1. It
fails silently and looks like missing data rather than like a wrong country.

V028__report_reference_data.sql already noticed the duplicate and worked around
it, seeding both rows with the same coordinates under a comment reading
"-- Egypt (duplicate entry)". That kept the map correct and left the trap.

A second, related defect: `admin` also held CountryId 1015, which exists in no
row of STUDY_COUNTRY_IN. Nothing rejected it because there is no foreign key on
UserCountries.CountryId. It granted nothing, quietly.

What this does
--------------
1. UMSCORE.UserCountries first and separately. UQ_UserCountries_User_Country
   makes a blind repoint fail — `admin` already holds 1, so (2,1011) -> (2,1)
   collides with a row that exists. Drop the colliding rows, repoint the rest.
2. Repoint 1011 -> 1 across every other country column, generated from the
   catalog rather than listed by hand so a column added later is still covered.
   A repoint rather than a guarded delete: another environment may have real
   data on 1011, and that data belongs in Egypt rather than blocking the
   migration or being dropped.
3. Remove UserCountries rows pointing at a country that does not exist (1015).
4. Delete STUDY_COUNTRY_IN id 1011, but only once nothing references it. If
   something still does, say what and skip — never delete data out from under a
   reference.
5. Add the two guards that stop this recurring, each only if the data supports
   it at that point:
      FK_UserCountries_StudyCountryIn  — stops the next 1015
      UX_StudyCountryIn_NameEn         — stops the next 1011

Id 1 keeps 'مصر'. Adopting 1011's formal name would relabel all 941 Egyptian
records and nine dropdowns, which is a separate decision from removing a
duplicate.

Re-runnable. A second run reports zero changes and skips both constraints as
already present.

Two SQL Server traps this script is written around
--------------------------------------------------
PRINT: every count is hoisted into a DECLAREd variable first. A subquery inside
PRINT is a *compile* error, and because it fails at compile time it takes the
entire batch with it — including the statements that were supposed to run. That
is how V046 silently did nothing on its first attempt.

@@ROWCOUNT: it is reset by the next statement, and `IF` counts as a statement.
`UPDATE ...; IF @@ROWCOUNT > 0 PRINT CAST(@@ROWCOUNT ...)` prints 1, always —
the IF has already overwritten it. Every rowcount below is captured with SET on
the line immediately after the statement it belongs to.

Precondition: id 1 must exist. Each destructive step re-checks it rather than
relying on an early RETURN, because RETURN ends only the current batch and every
GO-separated batch after it would still run.
================================================================================
*/

SET NOCOUNT ON;
GO

PRINT '';
PRINT '=== V048: standardising Egypt on id 1 ===';
GO

IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1)
    PRINT '*** Country 1 does not exist. Every step below is skipped - this script assumes 1 is the surviving Egypt.';
ELSE IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1011)
    PRINT 'Country 1011 is already gone; the repoint steps will report zero.';
ELSE
    PRINT 'Both Egypt rows present; proceeding.';
GO

/* ---------------------------------------------------------------------------
 * 1. UserCountries — collisions first, then repoint.
 * ------------------------------------------------------------------------ */
IF EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1)
BEGIN
    DECLARE @dropped int, @moved int;

    SELECT @dropped = COUNT(*)
    FROM UMSCORE.UserCountries dup
    WHERE dup.CountryId = 1011
      AND EXISTS (SELECT 1 FROM UMSCORE.UserCountries keep
                  WHERE keep.UserId = dup.UserId AND keep.CountryId = 1);

    DELETE dup
    FROM UMSCORE.UserCountries dup
    WHERE dup.CountryId = 1011
      AND EXISTS (SELECT 1 FROM UMSCORE.UserCountries keep
                  WHERE keep.UserId = dup.UserId AND keep.CountryId = 1);

    UPDATE UMSCORE.UserCountries SET CountryId = 1 WHERE CountryId = 1011;
    SET @moved = @@ROWCOUNT;

    PRINT 'UserCountries: ' + CAST(@dropped AS varchar(10))
        + ' dropped as duplicates (owner already held 1), '
        + CAST(@moved AS varchar(10)) + ' repointed 1011 -> 1.';
END
GO

/* ---------------------------------------------------------------------------
 * 2. Every other country column, generated from the catalog.
 *
 * Same column set the investigation counted: int columns named
 * %STUDY_COUNTRY_IN%, COUNTRY_ID or CountryId, on base tables, excluding the
 * lookup's own primary key and UserCountries (handled above).
 * ------------------------------------------------------------------------ */
IF EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1)
BEGIN
    DECLARE @sql nvarchar(max) = N'DECLARE @r int;';

    SELECT @sql = @sql + N'
UPDATE [' + c.TABLE_SCHEMA + N'].[' + c.TABLE_NAME + N'] SET [' + c.COLUMN_NAME + N'] = 1 WHERE [' + c.COLUMN_NAME + N'] = 1011;
SET @r = @@ROWCOUNT;
IF @r > 0 PRINT ''  ' + c.TABLE_SCHEMA + '.' + c.TABLE_NAME + '.' + c.COLUMN_NAME + ': '' + CAST(@r AS varchar(10)) + '' row(s)'';'
    FROM INFORMATION_SCHEMA.COLUMNS c
    JOIN INFORMATION_SCHEMA.TABLES t
      ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.DATA_TYPE = 'int'
      AND (c.COLUMN_NAME LIKE '%STUDY_COUNTRY_IN%' OR c.COLUMN_NAME = 'COUNTRY_ID' OR c.COLUMN_NAME = 'CountryId')
      AND NOT (c.TABLE_NAME = 'STUDY_COUNTRY_IN' AND c.COLUMN_NAME = 'STUDY_COUNTRY_IN_ID')
      AND NOT (c.TABLE_SCHEMA = 'UMSCORE' AND c.TABLE_NAME = 'UserCountries');

    PRINT 'Repointing 1011 -> 1 across the remaining country columns:';
    EXEC sp_executesql @sql;
    PRINT '(no lines above means nothing else referenced 1011)';
END
GO

/* ---------------------------------------------------------------------------
 * 3. Assignments pointing at a country that does not exist.
 * ------------------------------------------------------------------------ */
DECLARE @orphans int;

SELECT @orphans = COUNT(*) FROM UMSCORE.UserCountries uc
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = uc.CountryId);

DELETE uc FROM UMSCORE.UserCountries uc
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = uc.CountryId);

PRINT 'UserCountries rows pointing at a nonexistent country (removed): ' + CAST(@orphans AS varchar(10));
GO

/* ---------------------------------------------------------------------------
 * 4. Delete the duplicate, only if nothing still points at it.
 * ------------------------------------------------------------------------ */
IF EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1011)
BEGIN
    DECLARE @refs int = 0, @deleted int;
    DECLARE @checkSql nvarchar(max) = N'SELECT @out = 0';

    SELECT @checkSql = @checkSql + N' + (SELECT COUNT(*) FROM [' + c.TABLE_SCHEMA + N'].[' + c.TABLE_NAME
                     + N'] WHERE [' + c.COLUMN_NAME + N'] = 1011)'
    FROM INFORMATION_SCHEMA.COLUMNS c
    JOIN INFORMATION_SCHEMA.TABLES t
      ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.DATA_TYPE = 'int'
      AND (c.COLUMN_NAME LIKE '%STUDY_COUNTRY_IN%' OR c.COLUMN_NAME = 'COUNTRY_ID' OR c.COLUMN_NAME = 'CountryId')
      AND NOT (c.TABLE_NAME = 'STUDY_COUNTRY_IN' AND c.COLUMN_NAME = 'STUDY_COUNTRY_IN_ID');

    EXEC sp_executesql @checkSql, N'@out int OUTPUT', @out = @refs OUTPUT;
    PRINT 'Remaining references to 1011 after repointing: ' + CAST(@refs AS varchar(10));

    IF @refs > 0
    BEGIN
        PRINT '*** Skipping the delete: something still references 1011.';
        PRINT '*** The repoint above should have cleared it; investigate before re-running.';
    END
    ELSE
    BEGIN
        DELETE FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1011;
        SET @deleted = @@ROWCOUNT;
        PRINT 'Duplicate Egypt row deleted: ' + CAST(@deleted AS varchar(10));
    END
END
ELSE
    PRINT 'Duplicate Egypt row already absent (0 deleted).';
GO

/* ---------------------------------------------------------------------------
 * 5. The guards.
 *
 * Each is created only if the data supports it. On an environment whose data is
 * dirtier than this one, that yields a diagnosis rather than a failed migration
 * — and the rest of the script has already done its useful work by then.
 * ------------------------------------------------------------------------ */

-- 5a. Foreign key: stops the next dangling assignment.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_UserCountries_StudyCountryIn')
    PRINT 'FK_UserCountries_StudyCountryIn already present.';
ELSE
BEGIN
    DECLARE @badFk int;
    SELECT @badFk = COUNT(*) FROM UMSCORE.UserCountries uc
    WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = uc.CountryId);

    IF @badFk > 0
        PRINT '*** Skipping FK_UserCountries_StudyCountryIn: ' + CAST(@badFk AS varchar(10)) + ' orphan row(s) remain.';
    ELSE
    BEGIN
        ALTER TABLE UMSCORE.UserCountries
            ADD CONSTRAINT FK_UserCountries_StudyCountryIn
            FOREIGN KEY (CountryId) REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID);
        PRINT 'FK_UserCountries_StudyCountryIn created.';
    END
END
GO

-- 5b. Unique name: stops the next duplicate country.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_StudyCountryIn_NameEn'
           AND object_id = OBJECT_ID('MOHECORE.STUDY_COUNTRY_IN'))
    PRINT 'UX_StudyCountryIn_NameEn already present.';
ELSE
BEGIN
    DECLARE @dupNames int;
    SELECT @dupNames = COUNT(*) FROM (
        SELECT STUDY_COUNTRY_IN_NAME_EN FROM MOHECORE.STUDY_COUNTRY_IN
        WHERE STUDY_COUNTRY_IN_NAME_EN IS NOT NULL
        GROUP BY STUDY_COUNTRY_IN_NAME_EN HAVING COUNT(*) > 1) x;

    IF @dupNames > 0
        PRINT '*** Skipping UX_StudyCountryIn_NameEn: ' + CAST(@dupNames AS varchar(10)) + ' duplicate name(s) remain.';
    ELSE
    BEGIN
        CREATE UNIQUE INDEX UX_StudyCountryIn_NameEn
            ON MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_NAME_EN)
            WHERE STUDY_COUNTRY_IN_NAME_EN IS NOT NULL;
        PRINT 'UX_StudyCountryIn_NameEn created.';
    END
END
GO

/* ---------------------------------------------------------------------------
 * Result
 * ------------------------------------------------------------------------ */
DECLARE @countries int, @egypts int, @dangling int;

SELECT @countries = COUNT(*) FROM MOHECORE.STUDY_COUNTRY_IN;
SELECT @egypts = COUNT(*) FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_NAME_EN = 'Egypt';
SELECT @dangling = COUNT(*) FROM UMSCORE.UserCountries uc
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = uc.CountryId);

PRINT '';
PRINT '--- V048 result ---';
PRINT 'Countries in the lookup:        ' + CAST(@countries AS varchar(10));
PRINT 'Rows named Egypt (expected 1):  ' + CAST(@egypts AS varchar(10));
PRINT 'Dangling country assignments:   ' + CAST(@dangling AS varchar(10));
PRINT '';
GO
