/*
    Stamp the Wafar spending rows that carry no attaché.  —  DBA script, not a migration.

    NOT APPLIED, BY DECISION (20 Sep 2026). Investigation found that all eleven
    unstamped rows on the development database have STUDENT_ID NULL and
    BENEFICIARY_TYPE 'OTHER': they are not a student's spend, so there is no
    attaché to recover, and the tenant predicate already reads a NULL country as
    "belongs to the ministry as a whole" — which is what these rows are. Stamping
    them would have widened who can see them rather than tightened it. The owner
    chose to leave them NULL. Kept because the same question will come back on an
    environment whose unstamped rows *do* name students, and there the derivation
    below is the right answer.

    Why this is not a numbered migration
    ------------------------------------
    The country to stamp is an answer only the owner of the data can give, and it
    differs per environment. A migration would apply the same literal everywhere,
    so on a database whose unstamped rows belong to three different attachés it
    would quietly assign all of them to one. Hence @CountryId below, and hence
    this folder — the same reasoning that keeps enable_staff_country_policy.sql
    out of the run list.

    What is being fixed
    -------------------
    MOHECORE.WAFAR_SPENDING.STUDY_COUNTRY_IN_ID is NULL on some rows. The tenant
    predicate reads a NULL country as "belongs to the ministry as a whole":

        OR @CountryId IS NULL     -- MOHECORE.fn_tenant_predicate, V031

    so at the database layer those rows are visible under *every* scope. The
    application layer is stricter — WafarSpendingRepository filters
    `studyCountryInId IN :countryIds`, and SQL NULL IN (...) is never true — so
    today a country officer does not see them and a ministry officer does. The
    write path can no longer create more: WafarSpendingService refuses a spend
    with no attaché ("Wafar is held per attaché and cannot be spent from no one").
    These are older rows from before that rule.

    ** Read this before running **
    ------------------------------
    This script CHANGES WHO CAN SEE THESE ROWS. They are currently ministry-only
    in the application. After stamping them with a country, that country's
    officers see them too — which is the point of the stamp, but it is a
    widening, not a tightening. Satisfy yourself that the country below is right.

    On the development database (20 Sep 2026) the 11 unstamped rows all have
    STUDENT_ID NULL and BENEFICIARY_TYPE 'OTHER', i.e. they are not a student's
    spend and no student's record can supply the country. Where a row *does* have
    a student, this script takes the country from that student rather than from
    @CountryId — deriving beats asserting.

    Usage
    -----
        sqlcmd -S localhost,1433 -d MOHE -I -f 65001 -b \
               -v CountryId=1 -i stamp_unstamped_wafar_spending_country.sql

    Dry run: leave @DryRun = 1 (the default) and nothing is written.

    Idempotent — a stamped row is never restamped.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* The attaché to assign where the row names no student. 1 = Egypt. */
DECLARE @CountryId INT = 1;

/* 1 = report only, 0 = write. Flip deliberately. */
DECLARE @DryRun BIT = 1;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = @CountryId)
    THROW 50001, 'That country id does not exist in MOHECORE.STUDY_COUNTRY_IN.', 1;

/* ---- What would change ------------------------------------------------------ */

SELECT ws.SPENDING_ID,
       ws.STUDENT_ID,
       ws.BENEFICIARY_TYPE,
       ws.AMOUNT,
       ws.SPENDING_DATE,
       sd.STUDY_COUNTRY_IN                              AS country_from_student,
       COALESCE(sd.STUDY_COUNTRY_IN, @CountryId)        AS country_to_be_stamped,
       CASE WHEN sd.STUDY_COUNTRY_IN IS NOT NULL
            THEN 'derived from the student'
            ELSE 'assigned from @CountryId' END         AS source
  FROM MOHECORE.WAFAR_SPENDING ws
  LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = ws.STUDENT_ID
 WHERE ws.STUDY_COUNTRY_IN_ID IS NULL
 ORDER BY ws.SPENDING_ID;

IF @DryRun = 1
BEGIN
    PRINT 'DRY RUN — nothing written. Set @DryRun = 0 to apply.';
    RETURN;
END

/* ---- Apply ------------------------------------------------------------------ */

BEGIN TRANSACTION;

UPDATE ws
   SET ws.STUDY_COUNTRY_IN_ID = COALESCE(sd.STUDY_COUNTRY_IN, @CountryId)
  FROM MOHECORE.WAFAR_SPENDING ws
  LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = ws.STUDENT_ID
 WHERE ws.STUDY_COUNTRY_IN_ID IS NULL;

PRINT CONCAT('Stamped ', @@ROWCOUNT, ' Wafar spending row(s).');

IF EXISTS (SELECT 1 FROM MOHECORE.WAFAR_SPENDING WHERE STUDY_COUNTRY_IN_ID IS NULL)
BEGIN
    ROLLBACK;
    THROW 50002, 'A row still has no country after the update; rolled back.', 1;
END

COMMIT;

/* ---- Verify ----------------------------------------------------------------- */

SELECT STUDY_COUNTRY_IN_ID AS country_id, COUNT(*) AS rows_
  FROM MOHECORE.WAFAR_SPENDING
 GROUP BY STUDY_COUNTRY_IN_ID
 ORDER BY rows_ DESC;

EXEC sp_set_session_context @key = N'TenantAll', @value = NULL;
