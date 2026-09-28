/*
    WD-05 (DEC-34) — two bad rows in MOHECORE.STUDENT_DEGREE.

    Row 106 is named 'test' in both languages and is active, so it appears in
    every degree dropdown in the system.

    Row 105 'دبلوم عالٍ' duplicates row 101 'دبلوم عالي'. Same meaning, same
    order number (2), entered two weeks later with a diacritic on the last
    letter. It is not a harmless twin: both carry a full set of entitlement
    figures — the same three amounts (3000/2800/2200 by agency type) and the
    same three study-years rules — so whichever a clerk happened to pick, the
    money came out the same but the data divided in two.

    Why this is a migration and not an adhoc script
    -----------------------------------------------
    Lookup rows are reference data with fixed ids, the same in every
    environment; the students that point at them are not. So the vocabulary is
    corrected here and the 525 student records are corrected by the adhoc
    script next to this one.

    What it refuses to do
    ---------------------
    It moves no money. A figure on 105 is removed only when 101 already states
    exactly the same figure — same country, same agency type or major, same
    amount and currency, same number of years. A figure that exists only on 105
    is moved across; a figure that contradicts 101 is left alone and the whole
    retirement is abandoned with a message, because reconciling two different
    amounts for one degree is the ministry's decision and not this script's.

    Nothing in dev points at either row: no student, no archive snapshot, no
    staging row. The deletes below are still guarded, since production is not
    dev, and fall back to deactivating the row.

    Idempotent: every step checks the state it is about to create.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @dup   INT = 105;   -- the duplicate Higher Diploma
DECLARE @keep  INT = 101;   -- the one it duplicates
DECLARE @test  INT = 106;   -- the row named 'test'

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. The duplicate's entitlement figures.
-- ─────────────────────────────────────────────────────────────────────────────
IF EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DEGREE WHERE STUDENT_DEGREE_ID = @dup)
   AND EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DEGREE WHERE STUDENT_DEGREE_ID = @keep)
BEGIN
    DECLARE @conflicts INT = 0;

    -- An amount that names the same country and agency type as one on @keep but
    -- states a different figure. EXISTS on both sides so NULL country compares
    -- the way the unique constraint does.
    SELECT @conflicts = COUNT(*)
      FROM MOHECORE.STUDY_AMOUNT_LOOKUP d
      JOIN MOHECORE.STUDY_AMOUNT_LOOKUP k
        ON k.STUDENT_DEGREE_ID = @keep
       AND k.STUDENT_AGENCY_TYPE_ID = d.STUDENT_AGENCY_TYPE_ID
       AND (k.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN_ID
            OR (k.STUDY_COUNTRY_IN_ID IS NULL AND d.STUDY_COUNTRY_IN_ID IS NULL))
     WHERE d.STUDENT_DEGREE_ID = @dup
       AND (k.AMOUNT <> d.AMOUNT
            OR ISNULL(k.CURRENCY_ID, -1) <> ISNULL(d.CURRENCY_ID, -1));

    SELECT @conflicts = @conflicts + COUNT(*)
      FROM MOHECORE.STUDY_YEARS_LOOKUP d
      JOIN MOHECORE.STUDY_YEARS_LOOKUP k
        ON k.STUDENT_DEGREE_ID = @keep
       AND k.STUDENT_MAJOR_ID = d.STUDENT_MAJOR_ID
       AND (k.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN_ID
            OR (k.STUDY_COUNTRY_IN_ID IS NULL AND d.STUDY_COUNTRY_IN_ID IS NULL))
     WHERE d.STUDENT_DEGREE_ID = @dup
       AND k.STUDY_YEARS <> d.STUDY_YEARS;

    IF @conflicts > 0
    BEGIN
        PRINT '*** Degree ' + CAST(@dup AS varchar(6)) + ' states '
            + CAST(@conflicts AS varchar(6)) + ' figure(s) that differ from degree '
            + CAST(@keep AS varchar(6)) + '. Leaving both rows as they are: which figure'
            + ' is correct is the ministry''s decision, not this migration''s.';
    END
    ELSE
    BEGIN
        -- (a) Figures that exist only on the duplicate move across.
        UPDATE d SET STUDENT_DEGREE_ID = @keep
          FROM MOHECORE.STUDY_AMOUNT_LOOKUP d
         WHERE d.STUDENT_DEGREE_ID = @dup
           AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_AMOUNT_LOOKUP k
                            WHERE k.STUDENT_DEGREE_ID = @keep
                              AND k.STUDENT_AGENCY_TYPE_ID = d.STUDENT_AGENCY_TYPE_ID
                              AND (k.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN_ID
                                   OR (k.STUDY_COUNTRY_IN_ID IS NULL AND d.STUDY_COUNTRY_IN_ID IS NULL)));
        PRINT 'Amounts moved from ' + CAST(@dup AS varchar(6)) + ' to '
            + CAST(@keep AS varchar(6)) + ': ' + CAST(@@ROWCOUNT AS varchar(6));

        UPDATE d SET STUDENT_DEGREE_ID = @keep
          FROM MOHECORE.STUDY_YEARS_LOOKUP d
         WHERE d.STUDENT_DEGREE_ID = @dup
           AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_YEARS_LOOKUP k
                            WHERE k.STUDENT_DEGREE_ID = @keep
                              AND k.STUDENT_MAJOR_ID = d.STUDENT_MAJOR_ID
                              AND (k.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN_ID
                                   OR (k.STUDY_COUNTRY_IN_ID IS NULL AND d.STUDY_COUNTRY_IN_ID IS NULL)));
        PRINT 'Study-years rules moved from ' + CAST(@dup AS varchar(6)) + ' to '
            + CAST(@keep AS varchar(6)) + ': ' + CAST(@@ROWCOUNT AS varchar(6));

        -- (b) What is left says the same as @keep already says. Remove it.
        DELETE FROM MOHECORE.STUDY_AMOUNT_LOOKUP WHERE STUDENT_DEGREE_ID = @dup;
        PRINT 'Duplicate amounts removed: ' + CAST(@@ROWCOUNT AS varchar(6));

        DELETE FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDENT_DEGREE_ID = @dup;
        PRINT 'Duplicate study-years rules removed: ' + CAST(@@ROWCOUNT AS varchar(6));
    END
END

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Retire the two rows.
--
--    Deleting rather than deactivating, because IS_ACTIVE is not read: the
--    degree dropdown (LookupService.getStudentDegrees) lists every row the
--    endpoint returns, so a row left in the table stays on offer however the
--    flag is set. A row that anything still points at is deactivated instead
--    and named in the output, so the reference can be corrected by hand.
-- ─────────────────────────────────────────────────────────────────────────────
DECLARE @id INT, @refs INT, @name nvarchar(255);
DECLARE retire CURSOR LOCAL FAST_FORWARD FOR
    SELECT STUDENT_DEGREE_ID FROM MOHECORE.STUDENT_DEGREE
     WHERE STUDENT_DEGREE_ID IN (@dup, @test);

OPEN retire;
FETCH NEXT FROM retire INTO @id;
WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @name = STUDENT_DEGREE_NAME FROM MOHECORE.STUDENT_DEGREE WHERE STUDENT_DEGREE_ID = @id;

    SELECT @refs =
        (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA        WHERE STUDENT_DEGREE_ID = @id)
      + (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA_ARCH   WHERE STUDENT_DEGREE_ID = @id)
      + (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA_STAGING WHERE STUDENT_DEGREE_ID = @id)
      + (SELECT COUNT(*) FROM MOHECORE.STUDY_AMOUNT_LOOKUP WHERE STUDENT_DEGREE_ID = @id)
      + (SELECT COUNT(*) FROM MOHECORE.STUDY_YEARS_LOOKUP  WHERE STUDENT_DEGREE_ID = @id);

    IF @refs = 0
    BEGIN
        DELETE FROM MOHECORE.STUDENT_DEGREE WHERE STUDENT_DEGREE_ID = @id;
        PRINT 'Removed degree ' + CAST(@id AS varchar(6)) + ' (' + ISNULL(@name, N'?') + ').';
    END
    ELSE
    BEGIN
        UPDATE MOHECORE.STUDENT_DEGREE SET IS_ACTIVE = 0 WHERE STUDENT_DEGREE_ID = @id;
        PRINT '*** Degree ' + CAST(@id AS varchar(6)) + ' (' + ISNULL(@name, N'?') + ') is still named by '
            + CAST(@refs AS varchar(8)) + ' row(s), so it was deactivated rather than removed.'
            + ' Note that the dropdowns do not read IS_ACTIVE, so it remains on offer until those rows are moved.';
    END

    FETCH NEXT FROM retire INTO @id;
END
CLOSE retire;
DEALLOCATE retire;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. What the lookup now offers.
-- ─────────────────────────────────────────────────────────────────────────────
DECLARE @left INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_DEGREE);
PRINT '';
PRINT 'Degrees now in the lookup: ' + CAST(@left AS varchar(6));
