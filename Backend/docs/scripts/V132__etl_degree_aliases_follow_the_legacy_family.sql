/*
    WD-05 (DEC-35) — the import resolver is pointed at the same degree family
    the records now use.

    The problem
    -----------
    MOHECORE.STUDENT_DEGREE holds two families that mean the same things: the
    legacy 1–7 from 2023, and 100–104 added on 27 May 2026. The ETL resolves a
    sheet's Arabic text by matching the lookup's own names (V052), so it lands
    in whichever family happens to spell the word that way:

        جامعية        → 1    only the legacy family has it
        زمالة         → 4    only the legacy family has it
        بورد          → 6    only the legacy family has it
        دبلوم+ماجستير → 7    only the legacy family has it
        ماجستير       → 103  the legacy row is named 'عليا - ماجستير'
        دكتوراه       → 104  the legacy row is named 'عليا - دكتوراه'

    So one import wrote students into both families at once, and the two
    curated aliases V052 seeded pointed at 104 as well. The owner has chosen
    the legacy family for the student records (DEC-32), and `adhoc/backfill_student_degree.sql`
    has written 523 students accordingly. Left alone, the next import of the
    same sheet would move about 151 of them back — the split this is meant to
    end.

    What this does
    --------------
    States the five spellings that resolve into the wrong family, so the
    resolver's curated answer wins over the accidental name match. The alias
    mechanism is V052's own and exists for exactly this: "each of those has
    exactly one right answer, and only a person knows what it is".

    It does not touch 100–104 themselves. They are still what
    STUDY_AMOUNT_LOOKUP and STUDY_YEARS_LOOKUP are configured against, which is
    the open question recorded as WD-15 — a decision about money, for the
    ministry. This migration only stops new imports from making the split
    worse.

    Idempotent: MERGE on the unique key (LOOKUP, NORMALISED_VALUE), which
    updates the two rows V052 seeded and inserts the three that are new.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

/*  NORMALISED_VALUE must be written the way the ETL normalises, or the row
    never matches: tatweel and harakat removed, أ إ آ → ا, ة → ه, ى → ي.
    These are written out rather than computed, exactly as V052 wrote its
    three, so what the resolver will compare against is visible on the page. */
MERGE MOHECORE.ETL_LOOKUP_ALIAS AS target
USING (VALUES
    (N'DEGREE', N'ماجستير',   N'ماجستير',   2,
        N'DEC-35: the legacy family names this row عليا - ماجستير, so the bare word matched 103 instead'),
    /*  One row, not two: the sheets write both دكتوراه and دكتوراة and the ة → ه
        folding puts them on the same normalised key, which is the unique one. */
    (N'DEGREE', N'دكتوراه',   N'دكتوراه',   3,
        N'DEC-35: the legacy family names this row عليا - دكتوراه, so the bare word matched 104 instead. Covers the sheets'' دكتوراة, which folds onto the same key'),
    (N'DEGREE', N'الدكتوراه', N'الدكتوراه', 3,
        N'DEC-35: definite article only. Was 104 (V052), repointed to the legacy family'),
    (N'DEGREE', N'دتوراة',    N'دتوراه',    3,
        N'DEC-35: typo for دكتوراة, one row in the 2025-Q3 Egypt batch. Was 104 (V052), repointed')
) AS source (LOOKUP, RAW_VALUE, NORMALISED_VALUE, RESOLVED_ID, NOTE)
ON target.LOOKUP = source.LOOKUP AND target.NORMALISED_VALUE = source.NORMALISED_VALUE
WHEN MATCHED AND (target.RESOLVED_ID <> source.RESOLVED_ID) THEN
    UPDATE SET RESOLVED_ID = source.RESOLVED_ID,
               RAW_VALUE   = source.RAW_VALUE,
               NOTE        = source.NOTE
WHEN NOT MATCHED BY TARGET THEN
    INSERT (LOOKUP, RAW_VALUE, NORMALISED_VALUE, RESOLVED_ID, NOTE)
    VALUES (source.LOOKUP, source.RAW_VALUE, source.NORMALISED_VALUE, source.RESOLVED_ID, source.NOTE);
GO

/*  An alias that names a degree which is not there would send the resolver to
    its "غير مبين" fallback without saying so. */
IF EXISTS (SELECT 1 FROM MOHECORE.ETL_LOOKUP_ALIAS a
            WHERE a.LOOKUP = N'DEGREE'
              AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DEGREE d
                               WHERE d.STUDENT_DEGREE_ID = a.RESOLVED_ID))
BEGIN
    THROW 51000, 'A DEGREE alias names a degree id that is not in MOHECORE.STUDENT_DEGREE.', 1;
END
GO

DECLARE @aliases INT = (SELECT COUNT(*) FROM MOHECORE.ETL_LOOKUP_ALIAS WHERE LOOKUP = N'DEGREE');
PRINT 'Degree aliases now curated: ' + CAST(@aliases AS varchar(6));
