/*
    Students outside Egypt, so country scope can be tested.  —  DBA script, not a migration.

    Why this exists
    ---------------
    Every one of the 941 students on the development database has
    STUDY_COUNTRY_IN = 1 (Egypt). Two things follow, and both cost time:

      * the country-scoped accounts — jo_admin / jo_staff / jo_reviewer (1004)
        and sa_admin / sa_staff / sa_reviewer (1005) — see empty screens
        everywhere. That is correct scope behaviour and it looks exactly like a
        bug during manual QA;

      * cross-country isolation can only be proved in the negative. "Jordan sees
        nothing" is easy when Jordan owns nothing; "Jordan sees its own and not
        Saudi's" needs both to own something.

    This seeds a small set under Jordan and Saudi Arabia so the second question
    can be asked. Development data only — never run it anywhere real.

    ** Applied 20 Sep 2026, rolled back, and adopted on 23 Sep 2026 (DEC-23). **
    ---------------------------------------------------------------------------
    It works — with 20 students each, a 1004 scope saw exactly its own 20, a 1005
    scope its own 20, and Egypt still 941, which is the positive proof the
    Egypt-only dataset cannot give. But the backend suite then failed three tests,
    because **the 941-Egypt dataset is part of the suite's contract**:

      * TenantKeyIsStudyCountryTest.scope_filtersOnStudyCountry — "nobody studies
        in 1004, so a scope for it must see nothing": expected 0, got 20;
      * StudentDetailScopeTest.listsAreUnchanged — "every student is Egyptian, so
        Jordan sees none": expected 0, got 20;
      * QuarterRolloverIntegrationTest.theWholeMinistryRollsOver — asserts one
        snapshot per student and compares against the *total* student count.
        Seeded students have no STUDENT_FINANCE row so they are not snapshotted;
        the real invariant in that assertion (after == before, nobody snapshotted
        twice) held.

    And those three are only what breaks first. A dozen more tests reason about
    "941", "938 of 941", "939 of 941" in assertions and comments. Using this seed
    for real therefore means a deliberate round of work: give the seeded students
    the related rows a real student has (STUDENT_FINANCE at least), and rewrite
    the dataset-coupled assertions to test the behaviour — "a Jordan scope sees
    only Jordan's students" rather than "Jordan sees none". That is worth doing;
    it is not something to switch on quietly, which is why this was rolled back.

    That round of work is what DEC-23 authorised, and it is done: the seed now
    writes a STUDENT_FINANCE row for every student it creates (below), and the
    dataset-coupled tests assert behaviour rather than the number 941. The seed
    is part of the development dataset from here on; the tests are written so
    that running it, or not running it, leaves them green either way.

    How the rows are built
    ----------------------
    By copying existing students rather than inventing values: every lookup id on
    STUDENT_DATA is a foreign key, and a hand-written row gets one of them wrong.
    The copy overrides four things — the study country, the two columns under a
    unique index (NATIONAL_ID, STUDENT_PASSPORT_NUMBER), and the name, which gets
    a suffix so a human can see at a glance that the row is seeded.

    Every seeded row carries STUDENT_NOTE_1 = 'ZZ-SCOPE-SEED', which is what the
    cleanup at the bottom keys on. Nothing that existed before is touched.

    Usage
    -----
        -- look first (default)
        sqlcmd -S localhost,1433 -d MOHE -I -f 65001 -b -i seed_scope_test_students.sql

        -- then set @DryRun = 0 to write, or @Cleanup = 1 to remove what it wrote

    Idempotent: a second run with @DryRun = 0 adds nothing, because it stops when
    the marker is already present.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @PerCountry INT = 20;              /* owner's decision, 20 Sep 2026 */
DECLARE @DryRun     BIT = 1;               /* 1 = report only */
DECLARE @Cleanup    BIT = 0;               /* 1 = delete the seeded rows and stop */

DECLARE @Marker NVARCHAR(50) = N'ZZ-SCOPE-SEED';

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';   /* the BLOCK predicate needs this */

/* ---- Cleanup ------------------------------------------------------------------- */

IF @Cleanup = 1
BEGIN
    DELETE f
      FROM MOHECORE.STUDENT_FINANCE f
      JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = f.STUDENT_ID
     WHERE CAST(s.STUDENT_NOTE_1 AS NVARCHAR(100)) = @Marker;
    PRINT CONCAT('Removed ', @@ROWCOUNT, ' seeded finance row(s).');

    DELETE FROM MOHECORE.STUDENT_DATA WHERE CAST(STUDENT_NOTE_1 AS NVARCHAR(100)) = @Marker;
    PRINT CONCAT('Removed ', @@ROWCOUNT, ' seeded student(s).');
    EXEC sp_set_session_context @key = N'TenantAll', @value = NULL;
    RETURN;
END

/* ---- Guard ---------------------------------------------------------------------- */

IF EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA WHERE CAST(STUDENT_NOTE_1 AS NVARCHAR(100)) = @Marker)
BEGIN
    PRINT 'Seed is already present. Re-run with @Cleanup = 1 to remove it first.';
    SELECT STUDY_COUNTRY_IN AS country_id, COUNT(*) AS seeded
      FROM MOHECORE.STUDENT_DATA WHERE CAST(STUDENT_NOTE_1 AS NVARCHAR(100)) = @Marker
     GROUP BY STUDY_COUNTRY_IN;
    EXEC sp_set_session_context @key = N'TenantAll', @value = NULL;
    RETURN;
END

/* The countries to seed, and the donor rows each copy is based on. */
IF OBJECT_ID('tempdb..#targets') IS NOT NULL DROP TABLE #targets;
CREATE TABLE #targets (CountryId INT PRIMARY KEY, CountryName NVARCHAR(200));
INSERT INTO #targets (CountryId, CountryName)
SELECT STUDY_COUNTRY_IN_ID, STUDY_COUNTRY_IN_NAME_EN
  FROM MOHECORE.STUDY_COUNTRY_IN
 WHERE STUDY_COUNTRY_IN_ID IN (1004, 1005);       /* Jordan, Saudi Arabia */

IF (SELECT COUNT(*) FROM #targets) <> 2
    THROW 50003, 'Jordan (1004) and Saudi Arabia (1005) are not both in STUDY_COUNTRY_IN.', 1;

IF OBJECT_ID('tempdb..#plan') IS NOT NULL DROP TABLE #plan;
SELECT t.CountryId,
       t.CountryName,
       d.STUDENT_ID                                           AS donor_id,
       ROW_NUMBER() OVER (PARTITION BY t.CountryId ORDER BY d.STUDENT_ID) AS n
  INTO #plan
  FROM #targets t
  CROSS APPLY (
        SELECT TOP (@PerCountry) s.STUDENT_ID
          FROM MOHECORE.STUDENT_DATA s
         WHERE s.IS_DELETED = 0 AND s.STUDY_COUNTRY_IN = 1
         ORDER BY s.STUDENT_ID
  ) d;

SELECT CountryId, CountryName, COUNT(*) AS students_to_create FROM #plan GROUP BY CountryId, CountryName;

IF @DryRun = 1
BEGIN
    PRINT 'DRY RUN - nothing written. Set @DryRun = 0 to apply.';
    EXEC sp_set_session_context @key = N'TenantAll', @value = NULL;
    RETURN;
END

/* ---- Apply ---------------------------------------------------------------------- */

BEGIN TRANSACTION;

INSERT INTO MOHECORE.STUDENT_DATA (
        STUDENT_NAME, STUDENT_COUNTRY_ID, STUDENT_GOV_ID, STUDENT_MUD_ID,
        STUDENT_DELEGATION_DECISION_YEAR, STUDENT_DEGREE_ID, STUDENT_MAJOR_ID, STUDENT_MAJOR_NAME,
        STUDENT_DELEGATE_DECISION_NO, STUDENT_STUDY_NO_OF_YEARS, STUDENT_CURRENT_LEVEL_ID,
        STUDENT_AMOUNT, STUDENT_NOTE_1, STUDENT_STUDY_STATUS_ID, STUDENT_AGENCY_TYPE_ID,
        STUDENT_MINISTRY_TYPE_ID, STUDENT_STUDY_TYPE_ID, STUDENT_UNIVERSITY_TYPE_ID,
        STUDY_UNIVERSITY_IN, STUDY_COLLEGE_IN, STUDY_COUNTRY_IN, SYUDY_GOV_IN,
        STUDY_EDUCATION_IN, STUDY_MAJOR_IN, STUDENT_BIRTHDATE, STUDENT_GENDER,
        /* Every FK column on this table defaults to 0, and 0 is not a valid id in
           any of the parent tables — so a column left out of this list is a
           foreign key violation, not a NULL. Passport issue country is the one
           that is easy to forget because nothing else reads it. */
        STUDENT_PASSPORT_ISSUE_COUNTRY_ID,
        /* The quarter columns. Left out of the first version of this script, and
           the omission was not silent for long: the quarterly rollover files each
           snapshot under the student's own CURRENT_QUARTER, so forty students with
           NULL there produced forty snapshots under a null period and
           QuarterRolloverIntegrationTest counted 941 where it expected 981. A
           seeded student is a copy of a real one; it has to be a copy in the
           columns nothing displays too. */
        CURRENT_QUARTER, STUDENT_ADDED_QUARTER, STUDENT_LAST_QUARTER, STUDENT_MIDATE,
        STUDENT_PASSPORT_NUMBER, NATIONAL_ID, RECORD_VISIBILITY, IS_DELETED)
SELECT  s.STUDENT_NAME + N' [' + p.CountryName + N']',
        s.STUDENT_COUNTRY_ID, s.STUDENT_GOV_ID, s.STUDENT_MUD_ID,
        s.STUDENT_DELEGATION_DECISION_YEAR, s.STUDENT_DEGREE_ID, s.STUDENT_MAJOR_ID, s.STUDENT_MAJOR_NAME,
        s.STUDENT_DELEGATE_DECISION_NO, s.STUDENT_STUDY_NO_OF_YEARS, s.STUDENT_CURRENT_LEVEL_ID,
        s.STUDENT_AMOUNT, @Marker, s.STUDENT_STUDY_STATUS_ID, s.STUDENT_AGENCY_TYPE_ID,
        s.STUDENT_MINISTRY_TYPE_ID, s.STUDENT_STUDY_TYPE_ID, s.STUDENT_UNIVERSITY_TYPE_ID,
        s.STUDY_UNIVERSITY_IN, s.STUDY_COLLEGE_IN,
        p.CountryId,                                   /* the point of the exercise */
        s.SYUDY_GOV_IN, s.STUDY_EDUCATION_IN, s.STUDY_MAJOR_IN,
        s.STUDENT_BIRTHDATE, s.STUDENT_GENDER,
        s.STUDENT_PASSPORT_ISSUE_COUNTRY_ID,
        s.CURRENT_QUARTER, s.STUDENT_ADDED_QUARTER, s.STUDENT_LAST_QUARTER, s.STUDENT_MIDATE,
        /* both columns carry a unique index, so they cannot be copied */
        CONCAT(N'ZZ', p.CountryId, N'-', FORMAT(p.n, '000')),
        CONCAT(N'ZZ', p.CountryId, FORMAT(p.n, '00000000')),
        COALESCE(s.RECORD_VISIBILITY, N'ALL'), 0
  FROM #plan p
  JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = p.donor_id;

PRINT CONCAT('Seeded ', @@ROWCOUNT, ' student(s).');

/*
    A student without a STUDENT_FINANCE row is not a whole student here: the
    quarter rollover snapshots from the finance table, the scope tests read
    through it, and its own STUDY_COUNTRY_IN_ID is a second tenant key that has
    to agree with the student's. The row is copied from the same donor, keyed by
    the new student (STUDENT_ID is this table's primary key, one row per
    student), with the two country columns moved to the seeded country.
*/
INSERT INTO MOHECORE.STUDENT_FINANCE (
        STUDENT_ID, STUDENT_FIN_ID, STUDENT_FIN_NAME,
        STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH, STUDENT_FIN_RECIEVED_YEAR_MONTH,
        STUDENT_FIN_RECIEVED_AMOUNT, STUDY_COUNTRY_IN_ID, STUDY_GOV_IN_ID,
        STUDY_FINANCE_BANKE_ID, STUDY_FINANCE_BANK_BRANCH_ID, STUDY_FINANCE_DOF_ID,
        STUDY_CHECK_ID, STUDY_CHECK_DATE, STUDY_CHECK_DETAILS, STUDY_CHECK_FORM_NUMBER,
        STUDY_FIN_RECIEVED_STATUS, STUDY_NOTE_1,
        STUDY_RECEIVED_WHOM_STATUS_ID, STUDY_RECEIVED_WHOM_ID, STUDY_FINANCE_MIDATE,
        STUDENT_BANK_ID, STUDENT_ACCOUNT_NUMBER, STUDENT_ACCOUNT_ENGLISH_NAME,
        STUDENT_ACCOUNT_END_DATE, STUDENT_BANK_NOTES,
        /* NET_AMOUNT is computed; naming it in an insert is an error, not a no-op. */
        WAFAR_AMOUNT, WAFAR_TYPE_ID, MOHE_QUARTER_ID, STUDY_CURRENCY_IN_ID)
SELECT  seeded.STUDENT_ID, f.STUDENT_FIN_ID, seeded.STUDENT_NAME,
        f.STUDENT_FIN_RECIEVED_YEAR, f.STUDENT_FIN_RECIEVED_MONTH, f.STUDENT_FIN_RECIEVED_YEAR_MONTH,
        f.STUDENT_FIN_RECIEVED_AMOUNT,
        seeded.STUDY_COUNTRY_IN,                      /* the tenant key, not the donor's */
        f.STUDY_GOV_IN_ID,
        f.STUDY_FINANCE_BANKE_ID, f.STUDY_FINANCE_BANK_BRANCH_ID, f.STUDY_FINANCE_DOF_ID,
        f.STUDY_CHECK_ID, f.STUDY_CHECK_DATE, f.STUDY_CHECK_DETAILS, f.STUDY_CHECK_FORM_NUMBER,
        f.STUDY_FIN_RECIEVED_STATUS, @Marker,
        f.STUDY_RECEIVED_WHOM_STATUS_ID, f.STUDY_RECEIVED_WHOM_ID, f.STUDY_FINANCE_MIDATE,
        f.STUDENT_BANK_ID,
        /* the account number carries a mask and a rule of its own; seeded rows
           get a visibly fake one rather than a copy of a real person's */
        CONCAT(N'ZZ', seeded.STUDY_COUNTRY_IN, N'-ACC-', seeded.STUDENT_ID),
        f.STUDENT_ACCOUNT_ENGLISH_NAME, f.STUDENT_ACCOUNT_END_DATE, f.STUDENT_BANK_NOTES,
        f.WAFAR_AMOUNT, f.WAFAR_TYPE_ID, f.MOHE_QUARTER_ID, f.STUDY_CURRENCY_IN_ID
  FROM MOHECORE.STUDENT_DATA seeded
  /* Joined on the passport number, which the insert above built as
     ZZ<country>-<nnn> from the same #plan row — an exact key. Matching on the
     name would not do: the suffix is bracketed, and '[' opens a character class
     in a LIKE pattern, so '%[Jordan]' matches any one of J,o,r,d,a,n. */
  JOIN #plan p       ON seeded.STUDENT_PASSPORT_NUMBER
                        = CONCAT(N'ZZ', p.CountryId, N'-', FORMAT(p.n, '000'))
  JOIN MOHECORE.STUDENT_FINANCE f ON f.STUDENT_ID = p.donor_id
 WHERE CAST(seeded.STUDENT_NOTE_1 AS NVARCHAR(100)) = @Marker
   AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE ex WHERE ex.STUDENT_ID = seeded.STUDENT_ID);

PRINT CONCAT('Seeded ', @@ROWCOUNT, ' finance row(s).');
COMMIT;

/* ---- Verify: each attaché sees its own and nobody else's ------------------------ */

SELECT STUDY_COUNTRY_IN AS country_id, COUNT(*) AS seeded
  FROM MOHECORE.STUDENT_DATA WHERE CAST(STUDENT_NOTE_1 AS NVARCHAR(100)) = @Marker
 GROUP BY STUDY_COUNTRY_IN;

EXEC sp_set_session_context @key = N'TenantAll',        @value = N'0';
EXEC sp_set_session_context @key = N'TenantCountryIds', @value = N'1004';
SELECT 'scope 1004 (Jordan)' AS ctx, COUNT(*) AS visible_students FROM MOHECORE.STUDENT_DATA;

EXEC sp_set_session_context @key = N'TenantCountryIds', @value = N'1005';
SELECT 'scope 1005 (Saudi)' AS ctx, COUNT(*) AS visible_students FROM MOHECORE.STUDENT_DATA;

EXEC sp_set_session_context @key = N'TenantAll',        @value = NULL;
EXEC sp_set_session_context @key = N'TenantCountryIds', @value = NULL;
