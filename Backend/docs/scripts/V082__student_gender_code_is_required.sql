/* ============================================================================
   V082 — A gender the reports cannot count is not a gender.

   Why
   ---
   V075 gave MOHECORE.STUDENT_GENDER a CODE and derived MALE / FEMALE from the
   English names, because eleven report views split the gender columns on it.
   It left the column nullable, and nothing above the database required one:
   the JPA entity StudentGender did not map CODE at all, so the Lookup Tables
   screen could neither show nor set it and every row added there was written
   with CODE NULL. One was — id 3, "test" / "test111" — and
   ReportViewsReadWhatIsRecordedTest.genderSplitIsBounded has failed on it
   since, asserting the invariant that nothing enforced.

   The entity now maps CODE as @NotBlank, and this script makes the database
   say the same thing, so a row without a code cannot arrive by any route:
   the screen, the API, a hand-written INSERT, or an import.

   What
   ----
   1. Derives a CODE for every row that has none: the English name upper-cased
      with separators turned to underscores and trimmed to 40, or GENDER where
      there is no name, and the row's id appended where that would collide with
      another row's code. Nothing is deleted: an uncoded row keeps its place and
      simply becomes countable — a code the views do not test for is counted in
      neither the male nor the female column, which is what a row with no code
      already did.
   2. Makes CODE NOT NULL. The unique index UQ_STUDENT_GENDER_CODE, which V075
      created, already forbids two rows sharing one.

   The derivation is a repair, not a naming convention: the two rows the
   reports actually read (MALE, FEMALE) were coded by V075 and are untouched
   here. A derived code such as TEST111 is a placeholder an administrator is
   expected to correct on the Lookup Tables screen, which can now edit it.

   Rollback
   --------
     ALTER TABLE MOHECORE.STUDENT_GENDER ALTER COLUMN CODE NVARCHAR(50) NULL;
   The derived codes are left in place; they are values, not structure, and
   removing them would restore the defect.

   Idempotent. On a database whose rows are all coded it derives nothing and
   reports the column already NOT NULL.
   ============================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF COL_LENGTH('MOHECORE.STUDENT_GENDER', 'CODE') IS NULL
BEGIN
    RAISERROR('STUDENT_GENDER.CODE is missing; apply V075 before this script.', 16, 1);
END
GO

/* ── 1. A code for every row that has none ───────────────────────────────── */
DECLARE @derived TABLE (id INT, name_en NVARCHAR(500), code NVARCHAR(50));

/*  Derived in one statement rather than "set it, then clean it up", so a row is
    visited once and what lands in the column is what the CASE decided.

    The suffix matters. Two rows named "Male" and "male ", or two both left
    blank, derive the same base — and UQ_STUDENT_GENDER_CODE would refuse the
    second with a unique-key violation that says nothing about why. Where a base
    is not unique among the rows being coded, or is already taken by a row that
    has a code, the row's id is appended instead. On this development database
    nothing collided; the suffix is for the next environment.                    */
WITH candidates AS (
    SELECT g.STUDENT_GENDER_ID                       AS id,
           g.STUDENT_GENDER_NAME_EN                  AS name_en,
           base = LEFT(
               CASE
                   WHEN NULLIF(LTRIM(RTRIM(g.STUDENT_GENDER_NAME_EN)), N'') IS NULL
                       THEN N'GENDER'
                   ELSE REPLACE(REPLACE(REPLACE(REPLACE(
                            UPPER(LTRIM(RTRIM(g.STUDENT_GENDER_NAME_EN))),
                            N' ', N'_'), N'-', N'_'), N'.', N'_'), N'/', N'_')
               END, 40)
      FROM MOHECORE.STUDENT_GENDER g
     WHERE g.CODE IS NULL
),
resolved AS (
    SELECT c.id, c.name_en,
           code = CASE
               WHEN COUNT(*) OVER (PARTITION BY c.base) > 1
                 OR EXISTS (SELECT 1 FROM MOHECORE.STUDENT_GENDER t
                             WHERE t.CODE = c.base AND t.STUDENT_GENDER_ID <> c.id)
                   THEN c.base + N'_' + CAST(c.id AS NVARCHAR(9))
               ELSE c.base
           END
      FROM candidates c
)
UPDATE g
SET CODE = r.code
OUTPUT inserted.STUDENT_GENDER_ID, inserted.STUDENT_GENDER_NAME_EN, inserted.CODE
    INTO @derived (id, name_en, code)
FROM MOHECORE.STUDENT_GENDER g
JOIN resolved r ON r.id = g.STUDENT_GENDER_ID
WHERE g.CODE IS NULL;

IF EXISTS (SELECT 1 FROM @derived)
BEGIN
    PRINT 'Derived a CODE for these rows (correct them on the Lookup Tables screen if the derivation is not the name you want):';
    SELECT id AS STUDENT_GENDER_ID, name_en AS NAME_EN, code AS DERIVED_CODE FROM @derived;
END
ELSE
    PRINT 'Every STUDENT_GENDER row already carries a CODE; nothing derived.';
GO

/* ── 2. The column stops allowing NULL ───────────────────────────────────── */
IF EXISTS (SELECT 1 FROM MOHECORE.STUDENT_GENDER WHERE CODE IS NULL)
BEGIN
    RAISERROR('STUDENT_GENDER still has rows with no CODE; step 1 did not fill them.', 16, 1);
END
ELSE IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_GENDER') AND name = 'CODE' AND is_nullable = 1)
BEGIN
    /* UQ_STUDENT_GENDER_CODE is built on this column, so ALTER COLUMN is refused
       while it stands (Msg 5074). It goes and comes back — and comes back without
       V075's WHERE CODE IS NOT NULL filter, which existed only to let the uncoded
       rows through and means nothing once none can exist. */
    IF EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_GENDER') AND name = 'UQ_STUDENT_GENDER_CODE')
        DROP INDEX UQ_STUDENT_GENDER_CODE ON MOHECORE.STUDENT_GENDER;

    ALTER TABLE MOHECORE.STUDENT_GENDER ALTER COLUMN CODE NVARCHAR(50) NOT NULL;

    CREATE UNIQUE INDEX UQ_STUDENT_GENDER_CODE ON MOHECORE.STUDENT_GENDER (CODE);
    PRINT 'STUDENT_GENDER.CODE is now NOT NULL, and UQ_STUDENT_GENDER_CODE is unfiltered.';
END
ELSE
BEGIN
    /* Already NOT NULL. The index may still be the filtered one from V075 if an
       earlier run of this script stopped between the two statements. */
    IF EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_GENDER')
                  AND name = 'UQ_STUDENT_GENDER_CODE' AND has_filter = 1)
    BEGIN
        DROP INDEX UQ_STUDENT_GENDER_CODE ON MOHECORE.STUDENT_GENDER;
        CREATE UNIQUE INDEX UQ_STUDENT_GENDER_CODE ON MOHECORE.STUDENT_GENDER (CODE);
        PRINT 'STUDENT_GENDER.CODE was already NOT NULL; the redundant index filter has been dropped.';
    END
    ELSE IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_GENDER') AND name = 'UQ_STUDENT_GENDER_CODE')
    BEGIN
        CREATE UNIQUE INDEX UQ_STUDENT_GENDER_CODE ON MOHECORE.STUDENT_GENDER (CODE);
        PRINT 'STUDENT_GENDER.CODE is already NOT NULL; UQ_STUDENT_GENDER_CODE created.';
    END
    ELSE
        PRINT 'STUDENT_GENDER.CODE is already NOT NULL and uniquely indexed.';
END
GO

/* ── 3. Verification — throws rather than reports ────────────────────────── */
DECLARE @nulls INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_GENDER WHERE CODE IS NULL);
DECLARE @nullable INT = (
    SELECT COUNT(*) FROM sys.columns
    WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_GENDER') AND name = 'CODE' AND is_nullable = 1);
DECLARE @unique INT = (
    SELECT COUNT(*) FROM sys.indexes
    WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_GENDER') AND name = 'UQ_STUDENT_GENDER_CODE' AND is_unique = 1);
DECLARE @male INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_GENDER WHERE CODE = N'MALE');
DECLARE @female INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_GENDER WHERE CODE = N'FEMALE');

IF @nulls > 0 OR @nullable > 0
    RAISERROR('V082 failed: STUDENT_GENDER.CODE is still nullable or still holds NULLs.', 16, 1);
ELSE IF @unique = 0
    RAISERROR('V082 failed: UQ_STUDENT_GENDER_CODE is missing; apply V075.', 16, 1);
ELSE IF @male = 0 OR @female = 0
    RAISERROR('V082 failed: the MALE / FEMALE rows the report views split on are not both present.', 16, 1);
ELSE
    PRINT 'V082 OK: every STUDENT_GENDER row carries a unique CODE, the column is NOT NULL, and MALE and FEMALE are both present.';
GO
