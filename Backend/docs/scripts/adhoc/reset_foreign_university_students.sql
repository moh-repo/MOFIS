/*
    Students saved with another country's university  --  live QA M10 (FIX-PLAN P5-12, 26 Sep 2026).
    DBA script, not a migration. WRITTEN, NOT RUN (decision D19): review it, dry-run it, then decide.

    Why: until the M10 fix the Add Student wizard offered every university whatever the study country,
    because the university lookup fell back to all rows. Two students were saved that way (read-only on
    dev, 26 Sep 2026):
        10071  studies in Jordan (1004)       — Cairo University (1), its College of Engineering (1003),
                                                  governorate Cairo (1)
        10086  studies in Saudi Arabia (1005) — the same three
    The wizard now offers only the study country's universities; these two rows keep the Egyptian places.

    What it does, for every live student whose university belongs to another country than the student's
    study country (the "Not Specified" university, 999, belongs to none and is never matched):
      - university -> 999 "Not Specified";
      - college    -> 999 "Not Specified", when the college is one of the old university's;
      - governorate (SYUDY_GOV_IN) -> 999 "Not Specified", when it belongs to another country.
    A Cairo college under "Not Specified" would be a new mismatch, which is why the three move together.
    When the ministry sets up the right university, the student is corrected on the student form.
    The STUDENT_DATA audit trigger (TR_STUDENT_DATA_Audit_Update) records the old values. A pending
    approval request for the student (STUDENT_DATA_STAGING) is reported, not changed.

    Guard: @ExpectedIds lists the students the review found. If the set differs when the script is run for
    real, it stops without changing anything. The three "Not Specified" rows must exist. Idempotent: a
    second run finds nobody.

    Dry-run by default: set @Commit = 1 to keep the changes. Sets TenantAll itself.

        sqlcmd -S localhost,1433 -d MOHE -I -f 65001 -b -i reset_foreign_university_students.sql
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Commit BIT = 0;                              /* 1 = keep the changes; 0 = print, roll back */
DECLARE @ExpectedIds NVARCHAR(MAX) = N'10071,10086';  /* comma-separated, ascending; the students the review found */
DECLARE @NotSpecified INT = 999;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_UNIVERSITY_IN WHERE STUDY_UNIVERSITY_IN_ID = @NotSpecified)
   OR NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COLLEGE_IN WHERE STUDY_COLLEGE_IN_ID = @NotSpecified)
   OR NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_GOV_IN WHERE STUDY_GOV_IN_ID = @NotSpecified)
BEGIN
    PRINT 'STOPPED - a "Not Specified" row (999) is missing from STUDY_UNIVERSITY_IN, STUDY_COLLEGE_IN or STUDY_GOV_IN.';
    RETURN;
END

DECLARE @targets TABLE (STUDENT_ID INT PRIMARY KEY, OLD_UNIVERSITY INT);
INSERT INTO @targets (STUDENT_ID, OLD_UNIVERSITY)
SELECT sd.STUDENT_ID, sd.STUDY_UNIVERSITY_IN
FROM MOHECORE.STUDENT_DATA sd
JOIN MOHECORE.STUDY_UNIVERSITY_IN u ON u.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
WHERE ISNULL(sd.IS_DELETED, 0) = 0
  AND u.STUDY_UNIVERSITY_IN_ID <> @NotSpecified
  AND ISNULL(u.STUDY_COUNTRY_IN_ID, 0) NOT IN (0, @NotSpecified)
  AND ISNULL(sd.STUDY_COUNTRY_IN, -1) <> u.STUDY_COUNTRY_IN_ID;

DECLARE @found NVARCHAR(MAX) = (
    SELECT STUFF((SELECT ',' + CAST(STUDENT_ID AS NVARCHAR(10)) FROM @targets ORDER BY STUDENT_ID
                  FOR XML PATH('')), 1, 1, ''));
PRINT CONCAT('Students whose university is in another country: ', ISNULL(@found, '(none)'));

IF @found IS NULL
BEGIN
    PRINT 'Nothing to do.';
    RETURN;
END

IF @found <> REPLACE(@ExpectedIds, ' ', '')
BEGIN
    PRINT CONCAT('STOPPED - the students found (', @found, ') are not the ones reviewed (', @ExpectedIds,
                 '). Review the new ones and update @ExpectedIds.');
    RETURN;
END

/* Preview: the study places as they are, and what each becomes */
SELECT sd.STUDENT_ID,
       sci.STUDY_COUNTRY_IN_NAME_EN                                   AS study_country,
       sd.STUDY_UNIVERSITY_IN                                         AS university_now,
       @NotSpecified                                                  AS university_after,
       sd.STUDY_COLLEGE_IN                                            AS college_now,
       CASE WHEN c.STUDY_UNIVERSITY_IN_ID = t.OLD_UNIVERSITY THEN @NotSpecified ELSE sd.STUDY_COLLEGE_IN END AS college_after,
       sd.SYUDY_GOV_IN                                                AS governorate_now,
       CASE WHEN g.STUDY_COUNTRY_IN_ID IS NOT NULL AND g.STUDY_COUNTRY_IN_ID <> sd.STUDY_COUNTRY_IN
            THEN @NotSpecified ELSE sd.SYUDY_GOV_IN END               AS governorate_after,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA_STAGING st WHERE st.STUDENT_ID = sd.STUDENT_ID) AS pending_staging_rows
FROM MOHECORE.STUDENT_DATA sd
JOIN @targets t ON t.STUDENT_ID = sd.STUDENT_ID
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
LEFT JOIN MOHECORE.STUDY_COLLEGE_IN c ON c.STUDY_COLLEGE_IN_ID = sd.STUDY_COLLEGE_IN
LEFT JOIN MOHECORE.STUDY_GOV_IN g ON g.STUDY_GOV_IN_ID = sd.SYUDY_GOV_IN;

DECLARE @n INT;
BEGIN TRAN;

/* 1. The college, while it is still one of the old university's */
UPDATE sd SET sd.STUDY_COLLEGE_IN = @NotSpecified
FROM MOHECORE.STUDENT_DATA sd
JOIN @targets t ON t.STUDENT_ID = sd.STUDENT_ID
JOIN MOHECORE.STUDY_COLLEGE_IN c ON c.STUDY_COLLEGE_IN_ID = sd.STUDY_COLLEGE_IN
WHERE c.STUDY_UNIVERSITY_IN_ID = t.OLD_UNIVERSITY;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  colleges set to Not Specified');

/* 2. The governorate, when it belongs to another country */
UPDATE sd SET sd.SYUDY_GOV_IN = @NotSpecified
FROM MOHECORE.STUDENT_DATA sd
JOIN @targets t ON t.STUDENT_ID = sd.STUDENT_ID
JOIN MOHECORE.STUDY_GOV_IN g ON g.STUDY_GOV_IN_ID = sd.SYUDY_GOV_IN
WHERE g.STUDY_COUNTRY_IN_ID IS NOT NULL AND g.STUDY_COUNTRY_IN_ID <> sd.STUDY_COUNTRY_IN;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  governorates set to Not Specified');

/* 3. The university */
UPDATE sd SET sd.STUDY_UNIVERSITY_IN = @NotSpecified
FROM MOHECORE.STUDENT_DATA sd
JOIN @targets t ON t.STUDENT_ID = sd.STUDENT_ID
WHERE sd.STUDY_UNIVERSITY_IN = t.OLD_UNIVERSITY;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  universities set to Not Specified');

IF @Commit = 1
BEGIN
    COMMIT;
    PRINT 'COMMITTED';
END
ELSE
BEGIN
    ROLLBACK;
    PRINT 'ROLLED BACK (dry run) - set @Commit = 1 to apply';
END
