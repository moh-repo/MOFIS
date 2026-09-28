/*
================================================================================
V053 — STUDENT_DATA_STAGING: mark the rows a person has corrected by hand, so
        the next ETL run does not throw the correction away
================================================================================

Why
---
Staging is derived data. The ETL run replaces a country's rows wholesale —
DELETE by STUDY_COUNTRY_IN, then reload from the landing table — because the
staged row is keyed on the landing row's id and a second run would otherwise
collide on the primary key rather than refresh anything.

That is the right behaviour for a machine-generated row and exactly the wrong
behaviour for one somebody has fixed by hand. The resolver gets about nine
specializations in ten and no gender, birthdate or university at all, because
the sheet does not carry them; the gap is meant to be closed by a person. If
the next quarterly run silently deleted their work, the edit screen would be a
lie.

So a corrected row says so, and the run leaves it alone:

    DELETE FROM STUDENT_DATA_STAGING
     WHERE STUDY_COUNTRY_IN = @country
       AND (IS_EDITED = 0 OR IS_EDITED IS NULL)

and the reload skips any landing row whose id survived that delete. The run
reports how many it preserved, so "842 staged" and "700 staged, 142 preserved"
are visibly different outcomes rather than the same number twice.

The trade
---------
A preserved row stops tracking the landing table. If the attaché sends a
corrected sheet for a student somebody has already edited here, the new figures
do not reach the staged row — the human edit wins until a human says otherwise.
That is the safer of the two failure modes: losing an edit is silent, whereas a
stale row is visible on the screen next to its landing row. Clearing IS_EDITED
puts the row back under the ETL's control on the next run.

IS_EDITED is NOT NULL DEFAULT 0 so every row the ETL inserts is unambiguously
machine-generated without the insert having to say so.

Ordering
--------
On a database where STUDENT_DATA_STAGING does not exist yet (a fresh
environment, before V054 has run) every statement here is a no-op: V054
creates the table with these columns already in place. On a database where
the table exists, this script adds them. Either order ends in the same state.
================================================================================
*/

IF OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING', 'U') IS NULL
BEGIN
    PRINT 'MOHECORE.STUDENT_DATA_STAGING does not exist yet; V054 creates it with these columns. Nothing to do.';
END
GO

IF OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING', 'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING') AND name = 'IS_EDITED')
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_STAGING
        ADD IS_EDITED BIT NOT NULL
            CONSTRAINT DF_STUDENT_DATA_STAGING_IS_EDITED DEFAULT (0);
END
GO

IF OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING', 'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING') AND name = 'EDITED_AT')
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_STAGING ADD EDITED_AT DATETIME2(3) NULL;
END
GO

IF OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING', 'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING') AND name = 'EDITED_BY')
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_STAGING ADD EDITED_BY BIGINT NULL;
END
GO

/* The run filters on exactly this pair, once per country, over the whole
   table. */
IF OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING', 'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_STUDENT_DATA_STAGING_COUNTRY_EDITED'
               AND object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING'))
BEGIN
    CREATE INDEX IX_STUDENT_DATA_STAGING_COUNTRY_EDITED
        ON MOHECORE.STUDENT_DATA_STAGING (STUDY_COUNTRY_IN, IS_EDITED);
END
GO
