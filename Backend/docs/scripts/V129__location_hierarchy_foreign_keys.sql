/*
    V129 — The location lookups get the foreign keys they always implied
    (plan W6-06, QA C9 / Q-11c).

    Why
    ---
    Deleting a country or a governorate silently orphaned everything under it.
    The six delete endpoints call `repository.deleteById(id)` with no check for
    children, and the database had nothing to say about it: every parent pointer
    in the hierarchy — STUDENT_GOV.STUDENT_COUNTRY_ID, STUDENT_GOV_MUD's two,
    STUDY_GOV_IN.STUDY_COUNTRY_IN_ID — was an unconstrained INT. The orphans do
    not announce themselves; they surface later as a governorate list that is
    missing entries, or a municipality that belongs to nothing.

    Two things stood in the way, and both are handled here.

    1. `0` is this schema's "unset", and every one of these columns defaults to
       it. 0 is not a valid id in any parent table, so an FK would reject rows
       that are merely blank. They become NULL first, which is what "no parent"
       means to a foreign key. (V065 met the same trap on the finance documents
       and solved it the same way, with NULLIF(..., 0).)

    2. STUDENT_GOV_MUD.STUDENT_GOV_ID is `nvarchar(255)` while the key it points
       at, STUDENT_GOV.STUDENT_GOV_ID, is `int IDENTITY`. SQL Server will not
       build a foreign key between the two, so the column is converted. On the
       review database all 173 rows convert cleanly (0 non-numeric, 0 orphans),
       and the conversion is skipped rather than forced anywhere it would not.

    What
    ----
    1. Unset parents (0) become NULL on the four columns below.
    2. STUDENT_GOV_MUD.STUDENT_GOV_ID nvarchar -> int, keeping its index.
    3. Four foreign keys, each created only if its data supports it:
         STUDENT_GOV.STUDENT_COUNTRY_ID      -> STUDENT_COUNTRY
         STUDENT_GOV_MUD.STUDENT_COUNTRY_ID  -> STUDENT_COUNTRY
         STUDENT_GOV_MUD.STUDENT_GOV_ID      -> STUDENT_GOV
         STUDY_GOV_IN.STUDY_COUNTRY_IN_ID    -> STUDY_COUNTRY_IN

    Per decision Q-11c: orphan check first, and an environment whose data does
    not support a key gets a printed diagnosis rather than a failed migration.
    Re-runnable: every step tests for its own result first.

    What this does NOT do
    ---------------------
    It does not cascade. A delete that would orphan a child now fails at the
    database, which is the point; the services refuse it before it gets that far
    (see StudentGovService and friends, same wave), so the caller sees a 409
    naming what still depends on the row rather than a 500.
*/

/* ---------------------------------------------------------------------------
   1. "Unset" becomes "no parent".
   ------------------------------------------------------------------------ */

UPDATE MOHECORE.STUDENT_GOV      SET STUDENT_COUNTRY_ID   = NULL WHERE STUDENT_COUNTRY_ID = 0;
UPDATE MOHECORE.STUDENT_GOV_MUD  SET STUDENT_COUNTRY_ID   = NULL WHERE STUDENT_COUNTRY_ID = 0;
UPDATE MOHECORE.STUDY_GOV_IN     SET STUDY_COUNTRY_IN_ID  = NULL WHERE STUDY_COUNTRY_IN_ID = 0;
GO

-- The municipality's gov pointer is still text at this point, so '0' and '' both mean unset.
UPDATE MOHECORE.STUDENT_GOV_MUD
   SET STUDENT_GOV_ID = NULL
 WHERE STUDENT_GOV_ID IS NOT NULL
   AND (LTRIM(RTRIM(STUDENT_GOV_ID)) = '' OR TRY_CONVERT(int, STUDENT_GOV_ID) = 0);
GO

/* ---------------------------------------------------------------------------
   2. STUDENT_GOV_MUD.STUDENT_GOV_ID: nvarchar -> int.

   Skipped entirely if any value would not survive the trip. The index on the
   column has to go first and come back after: SQL Server will not alter a
   column an index is built on.
   ------------------------------------------------------------------------ */

IF EXISTS (SELECT 1 FROM sys.columns c
            JOIN sys.types t ON t.user_type_id = c.user_type_id
           WHERE c.object_id = OBJECT_ID('MOHECORE.STUDENT_GOV_MUD')
             AND c.name = 'STUDENT_GOV_ID'
             AND t.name IN ('nvarchar', 'varchar', 'nchar', 'char'))
BEGIN
    DECLARE @unconvertible int;
    SELECT @unconvertible = COUNT(*)
      FROM MOHECORE.STUDENT_GOV_MUD
     WHERE STUDENT_GOV_ID IS NOT NULL
       AND TRY_CONVERT(int, STUDENT_GOV_ID) IS NULL;

    IF @unconvertible > 0
        PRINT '*** Skipping STUDENT_GOV_ID conversion: ' + CAST(@unconvertible AS varchar(10))
              + ' value(s) are not numbers. The municipality -> governorate key cannot be added until they are fixed.';
    ELSE
    BEGIN
        IF EXISTS (SELECT 1 FROM sys.indexes
                    WHERE name = 'STUD_GOV_ID' AND object_id = OBJECT_ID('MOHECORE.STUDENT_GOV_MUD'))
            DROP INDEX STUD_GOV_ID ON MOHECORE.STUDENT_GOV_MUD;

        ALTER TABLE MOHECORE.STUDENT_GOV_MUD ALTER COLUMN STUDENT_GOV_ID int NULL;

        CREATE INDEX STUD_GOV_ID ON MOHECORE.STUDENT_GOV_MUD (STUDENT_GOV_ID);
        PRINT 'STUDENT_GOV_MUD.STUDENT_GOV_ID converted to int.';
    END
END
ELSE
    PRINT 'STUDENT_GOV_MUD.STUDENT_GOV_ID is already numeric.';
GO

/* ---------------------------------------------------------------------------
   3. The four keys. Each one: already there? data clean? then create.
   ------------------------------------------------------------------------ */

-- 3a. Governorate -> country.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_STUDENT_GOV_COUNTRY')
    PRINT 'FK_STUDENT_GOV_COUNTRY already present.';
ELSE
BEGIN
    DECLARE @badGov int;
    SELECT @badGov = COUNT(*)
      FROM MOHECORE.STUDENT_GOV g
     WHERE g.STUDENT_COUNTRY_ID IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_COUNTRY c
                        WHERE c.STUDENT_COUNTRY_ID = g.STUDENT_COUNTRY_ID);

    IF @badGov > 0
        PRINT '*** Skipping FK_STUDENT_GOV_COUNTRY: ' + CAST(@badGov AS varchar(10)) + ' orphan governorate(s).';
    ELSE
    BEGIN
        ALTER TABLE MOHECORE.STUDENT_GOV WITH CHECK
            ADD CONSTRAINT FK_STUDENT_GOV_COUNTRY
            FOREIGN KEY (STUDENT_COUNTRY_ID) REFERENCES MOHECORE.STUDENT_COUNTRY (STUDENT_COUNTRY_ID);
        PRINT 'FK_STUDENT_GOV_COUNTRY created.';
    END
END
GO

-- 3b. Municipality -> country.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_STUDENT_GOV_MUD_COUNTRY')
    PRINT 'FK_STUDENT_GOV_MUD_COUNTRY already present.';
ELSE
BEGIN
    DECLARE @badMudCountry int;
    SELECT @badMudCountry = COUNT(*)
      FROM MOHECORE.STUDENT_GOV_MUD m
     WHERE m.STUDENT_COUNTRY_ID IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_COUNTRY c
                        WHERE c.STUDENT_COUNTRY_ID = m.STUDENT_COUNTRY_ID);

    IF @badMudCountry > 0
        PRINT '*** Skipping FK_STUDENT_GOV_MUD_COUNTRY: ' + CAST(@badMudCountry AS varchar(10)) + ' orphan municipalit(ies).';
    ELSE
    BEGIN
        ALTER TABLE MOHECORE.STUDENT_GOV_MUD WITH CHECK
            ADD CONSTRAINT FK_STUDENT_GOV_MUD_COUNTRY
            FOREIGN KEY (STUDENT_COUNTRY_ID) REFERENCES MOHECORE.STUDENT_COUNTRY (STUDENT_COUNTRY_ID);
        PRINT 'FK_STUDENT_GOV_MUD_COUNTRY created.';
    END
END
GO

-- 3c. Municipality -> governorate. Only possible once the column is an int.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_STUDENT_GOV_MUD_GOV')
    PRINT 'FK_STUDENT_GOV_MUD_GOV already present.';
ELSE IF NOT EXISTS (SELECT 1 FROM sys.columns c
                     JOIN sys.types t ON t.user_type_id = c.user_type_id
                    WHERE c.object_id = OBJECT_ID('MOHECORE.STUDENT_GOV_MUD')
                      AND c.name = 'STUDENT_GOV_ID' AND t.name = 'int')
    PRINT '*** Skipping FK_STUDENT_GOV_MUD_GOV: STUDENT_GOV_ID is not an int (see step 2).';
ELSE
BEGIN
    DECLARE @badMudGov int;
    SELECT @badMudGov = COUNT(*)
      FROM MOHECORE.STUDENT_GOV_MUD m
     WHERE m.STUDENT_GOV_ID IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_GOV g
                        WHERE g.STUDENT_GOV_ID = m.STUDENT_GOV_ID);

    IF @badMudGov > 0
        PRINT '*** Skipping FK_STUDENT_GOV_MUD_GOV: ' + CAST(@badMudGov AS varchar(10)) + ' municipalit(ies) name a governorate that is gone.';
    ELSE
    BEGIN
        ALTER TABLE MOHECORE.STUDENT_GOV_MUD WITH CHECK
            ADD CONSTRAINT FK_STUDENT_GOV_MUD_GOV
            FOREIGN KEY (STUDENT_GOV_ID) REFERENCES MOHECORE.STUDENT_GOV (STUDENT_GOV_ID);
        PRINT 'FK_STUDENT_GOV_MUD_GOV created.';
    END
END
GO

-- 3d. Study governorate -> study country. This is the tenant hierarchy: the
--     country here is the key RLS filters every scoped table by.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_STUDY_GOV_IN_COUNTRY')
    PRINT 'FK_STUDY_GOV_IN_COUNTRY already present.';
ELSE
BEGIN
    DECLARE @badStudyGov int;
    SELECT @badStudyGov = COUNT(*)
      FROM MOHECORE.STUDY_GOV_IN g
     WHERE g.STUDY_COUNTRY_IN_ID IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN c
                        WHERE c.STUDY_COUNTRY_IN_ID = g.STUDY_COUNTRY_IN_ID);

    IF @badStudyGov > 0
        PRINT '*** Skipping FK_STUDY_GOV_IN_COUNTRY: ' + CAST(@badStudyGov AS varchar(10)) + ' orphan study governorate(s).';
    ELSE
    BEGIN
        ALTER TABLE MOHECORE.STUDY_GOV_IN WITH CHECK
            ADD CONSTRAINT FK_STUDY_GOV_IN_COUNTRY
            FOREIGN KEY (STUDY_COUNTRY_IN_ID) REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID);
        PRINT 'FK_STUDY_GOV_IN_COUNTRY created.';
    END
END
GO
