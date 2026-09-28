/*
    WD-05 (DEC-32) — STUDENT_DEGREE_ID is 0 on 937 of 941 students.

    An adhoc script, not a migration: it corrects records, and records differ
    between environments. Run it on dev, read the counts, then run the same
    file on production. V131 corrects the lookup this script writes into, so
    run that first.

    A correction to the plan's premise
    ----------------------------------
    The plan says the back-fill comes from MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT,
    and an earlier reading of mine called that impossible. It is not. I had
    joined the two tables on their ids — staging runs 1685..2526, students run
    1..1003, so nothing met and the conclusion was that nothing could. The
    staging table's join key is the student's **name**; it has no student id at
    all. On the name, 525 of 941 students match, each to exactly one degree
    text, with no student matching two different degrees.

    The names are compared after normalising what Arabic keyboards vary on:
    the tatweel, أ إ آ against ا, ة against ه, ى against ي, and doubled
    spaces. Exact matching alone found 454; the normalisation is worth 71
    students and invents nothing, since it only makes two spellings of the same
    name meet.

    Which vocabulary (DEC-32)
    -------------------------
    The lookup holds two rival families: the legacy 1–7 from 2023, and 100–104
    added on 2026-05-27. The owner chose the legacy family, and it is the one
    the source file speaks: جامعية, زمالة and بورد exist only there. The
    others — بكالوريوس, ماجستير, دكتوراه — exist in both.

    **The consequence, recorded rather than worked around:** the entitlement
    figures in STUDY_AMOUNT_LOOKUP and STUDY_YEARS_LOOKUP are keyed on 100–104
    only. A student carrying a legacy degree id therefore resolves no expected
    amount and no study duration, on this script's rows as on the four that
    already carried one. Correcting that means re-entering the ministry's
    figures against the legacy ids, or merging the two families — a decision
    about money, left to the ministry (tracked as an owner item on WD-05).

    The 416 students with no row in the source file (DEC-33)
    --------------------------------------------------------
    They are left at 0. The owner chose to keep the difference visible between
    "the ministry recorded no degree" and "no source row exists for this
    student". 0 is not an id in STUDENT_DEGREE, so those students stay outside
    every degree-filtered report until a source arrives — which is the honest
    state, and it is why no foreign key is added on the column yet.

    Idempotent: only students still at 0 are touched, and only where the source
    is unambiguous.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

-- STUDENT_DATA is under the tenant policy; without this the script sees no
-- rows and reports a cheerful zero.
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

-- ─────────────────────────────────────────────────────────────────────────────
-- The vocabulary, written down rather than guessed at.
--
-- Every text in the source file, with the count seen in dev. The three
-- misspellings of دكتوراه are the file's own; they are mapped, not corrected,
-- because the file is the record of what the ministry sent.
-- ─────────────────────────────────────────────────────────────────────────────
DECLARE @map TABLE (degree_text nvarchar(150) NOT NULL PRIMARY KEY, degree_id INT NOT NULL);
INSERT INTO @map (degree_text, degree_id) VALUES
    (N'جامعية',         1),   -- 344 students
    (N'د.جامعية',       1),   --   0 in dev; 2 source rows, both طب بشري over 7 years
    (N'ماجستير',        2),   --  54 students
    (N'دكتوراة',        3),   --  92 students
    (N'دكتوراه',        3),   --   4 students
    (N'الدكتوراه',      3),   --   0 in dev
    (N'دتوراة',         3),   --   1 student  (the file's typo)
    (N'زمالة',          4),   --  28 students
    (N'بورد',           6),   --   1 student
    (N'دبلوم+ماجستير',  7);   --   1 student

-- Every mapped id must exist, or the script would point students at nothing —
-- the fault V129 and V130 were written to end.
IF EXISTS (SELECT 1 FROM @map m
            WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DEGREE d
                               WHERE d.STUDENT_DEGREE_ID = m.degree_id))
BEGIN
    PRINT '*** Refusing to run: the mapping names a degree id that is not in MOHECORE.STUDENT_DEGREE.';
    SELECT N'missing id: ' + CAST(m.degree_id AS varchar(6)) + N' (for ' + m.degree_text + N')' AS problem
      FROM @map m
     WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DEGREE d WHERE d.STUDENT_DEGREE_ID = m.degree_id);
    RETURN;
END

-- ─────────────────────────────────────────────────────────────────────────────
-- Both sides, normalised the same way.
-- ─────────────────────────────────────────────────────────────────────────────
IF OBJECT_ID('tempdb..#student') IS NOT NULL DROP TABLE #student;
IF OBJECT_ID('tempdb..#source')  IS NOT NULL DROP TABLE #source;

SELECT d.STUDENT_ID,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
           LTRIM(RTRIM(d.STUDENT_NAME)),
           NCHAR(1600), N''),          -- tatweel ـ
           NCHAR(1571), NCHAR(1575)),  -- أ → ا
           NCHAR(1573), NCHAR(1575)),  -- إ → ا
           NCHAR(1570), NCHAR(1575)),  -- آ → ا
           NCHAR(1577), NCHAR(1607)),  -- ة → ه
           NCHAR(1609), NCHAR(1610)),  -- ى → ي
           N'  ', N' ') AS nm
  INTO #student
  FROM MOHECORE.STUDENT_DATA d
 WHERE ISNULL(d.STUDENT_DEGREE_ID, 0) = 0
   AND NULLIF(LTRIM(RTRIM(d.STUDENT_NAME)), N'') IS NOT NULL;

SELECT LTRIM(RTRIM(i.DEGREE)) AS degree_text,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
           LTRIM(RTRIM(i.STUDENT_NAME)),
           NCHAR(1600), N''),
           NCHAR(1571), NCHAR(1575)),
           NCHAR(1573), NCHAR(1575)),
           NCHAR(1570), NCHAR(1575)),
           NCHAR(1577), NCHAR(1607)),
           NCHAR(1609), NCHAR(1610)),
           N'  ', N' ') AS nm
  INTO #source
  FROM MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT i
 WHERE NULLIF(LTRIM(RTRIM(i.DEGREE)), N'') IS NOT NULL
   AND NULLIF(LTRIM(RTRIM(i.STUDENT_NAME)), N'') IS NOT NULL;

-- No index on nm: an Arabic name is nvarchar(255), which is past the 1700-byte
-- limit for an index key, and both tables are under a thousand rows.

-- ─────────────────────────────────────────────────────────────────────────────
-- What the source says, before anything is written.
-- ─────────────────────────────────────────────────────────────────────────────
DECLARE @before INT = (SELECT COUNT(*) FROM #student);
PRINT 'Students with no degree, before: ' + CAST(@before AS varchar(8));

PRINT '';
PRINT 'Source texts that this script does not recognise (these students stay at 0):';
SELECT s.degree_text + N'  ->  ' + CAST(COUNT(DISTINCT st.STUDENT_ID) AS varchar(8)) + N' student(s)' AS unmapped
  FROM #source s
  JOIN #student st ON st.nm = s.nm
 WHERE NOT EXISTS (SELECT 1 FROM @map m WHERE m.degree_text = s.degree_text)
 GROUP BY s.degree_text
 ORDER BY COUNT(DISTINCT st.STUDENT_ID) DESC;

PRINT '';
PRINT 'Students the source contradicts itself about (left at 0):';
SELECT N'student ' + CAST(x.STUDENT_ID AS varchar(8)) + N' has '
     + CAST(x.kinds AS varchar(4)) + N' different degrees in the source' AS conflict
  FROM (SELECT st.STUDENT_ID, COUNT(DISTINCT m.degree_id) AS kinds
          FROM #student st
          JOIN #source s ON s.nm = st.nm
          JOIN @map m ON m.degree_text = s.degree_text
         GROUP BY st.STUDENT_ID) x
 WHERE x.kinds > 1;

-- ─────────────────────────────────────────────────────────────────────────────
-- The back-fill. One degree, unambiguously, or nothing.
-- ─────────────────────────────────────────────────────────────────────────────
;WITH resolved AS (
    SELECT st.STUDENT_ID, MIN(m.degree_id) AS degree_id
      FROM #student st
      JOIN #source s ON s.nm = st.nm
      JOIN @map m ON m.degree_text = s.degree_text
     GROUP BY st.STUDENT_ID
    HAVING COUNT(DISTINCT m.degree_id) = 1
)
UPDATE d
   SET STUDENT_DEGREE_ID = r.degree_id
  FROM MOHECORE.STUDENT_DATA d
  JOIN resolved r ON r.STUDENT_ID = d.STUDENT_ID
 WHERE ISNULL(d.STUDENT_DEGREE_ID, 0) = 0;

-- Captured on the next statement, not later: PRINT resets @@ROWCOUNT, so a
-- count read after one reports a confident zero.
DECLARE @filled INT = @@ROWCOUNT;

PRINT '';
PRINT 'Students given a degree: ' + CAST(@filled AS varchar(8));

-- ─────────────────────────────────────────────────────────────────────────────
-- What the table holds now.
-- ─────────────────────────────────────────────────────────────────────────────
PRINT '';
PRINT 'Degrees held, after:';
SELECT ISNULL(l.STUDENT_DEGREE_NAME, N'(none — no source row)') + N'  ->  '
     + CAST(COUNT(*) AS varchar(8)) + N' student(s)' AS held
  FROM MOHECORE.STUDENT_DATA d
  LEFT JOIN MOHECORE.STUDENT_DEGREE l ON l.STUDENT_DEGREE_ID = NULLIF(d.STUDENT_DEGREE_ID, 0)
 GROUP BY l.STUDENT_DEGREE_NAME
 ORDER BY COUNT(*) DESC;

-- Nothing may point at a degree that is not there.
IF EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA d
            WHERE ISNULL(d.STUDENT_DEGREE_ID, 0) <> 0
              AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DEGREE l
                               WHERE l.STUDENT_DEGREE_ID = d.STUDENT_DEGREE_ID))
    PRINT '*** Warning: some students name a degree that does not exist.';
ELSE
    PRINT 'Every degree held names a degree that exists.';

DROP TABLE #student;
DROP TABLE #source;
