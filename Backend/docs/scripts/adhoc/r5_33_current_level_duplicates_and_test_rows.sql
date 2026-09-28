/*
    Duplicate current levels and two "test" lookup rows  --  live QA R5-33 (27 Sep 2026).
    DBA script, not a migration. APPLIED TO DEV 28 Sep 2026. Elsewhere (decision D19): review it, dry-run it, then decide.

    What QA found
    -------------
    The current-level list holds seven names twice, and the current-level and agency-type lists each offer
    an active row named "test". Read-only on dev, 27 Sep 2026:

        STUDENT_CURRENT_LEVEL   1..7   السنة الأولى .. السابع          First Year .. Seventh Year    (in use)
                                100..106 السنة الأولى .. السنة السابعة  First Year .. Seventh Year    (nothing points at them)
                                109    test / test, active                                           (nothing points at it)
        STUDENT_AGENCY_TYPE     104    test / test, active                                           (nothing points at it)

    "Nothing points at them" covers every column that holds these ids: STUDENT_DATA, STUDENT_DATA_ARCH,
    STUDENT_DATA_STAGING, STUDENT_DEATH_CASE.LEVEL_AT_DEATH_ID and ETL_LOOKUP_ALIAS for the levels; the
    same three student tables, STUDENT_MINISTRY_TYPE, STUDENT_MINISTRY_SUB_TYPE, STUDENT_UNIVERSITY_TYPE,
    STUDY_AMOUNT_LOOKUP and ETL_LOOKUP_ALIAS for the agency type. Ids 1..7 carry 15 students and 842
    staging rows. Production may differ, which is why the script moves references rather than assuming
    there are none.

    Not touched, on purpose:
      - 107 "Graduated" and 108 "Suspended" share the 100-series but are not duplicates;
      - agency types 100..103 are different names (and carry 15 amount rows);
      - level 7's Arabic is "السابع" where its twin 106 says "السنة السابعة". Both are "Seventh Year" in
        English, so they are the same level; 7 is kept (it is the one in use) with its wording as it is. The
        ministry can reword it on System Coding.

    What it does, in one transaction
    --------------------------------
      1. Duplicate levels: every level whose English name a level with a lower id already has (the lower id
         wins, as in V068 and V131). Each duplicate's references move to its twin, then the duplicate is
         deleted. A duplicate whose order number differs from its twin's is not the same level: the script
         stops and changes nothing.
      2. "test" rows (both names "test"): switched off (IS_ACTIVE = 0). Every dropdown drops inactive rows
         (LookupService.mapToLookupItems on the screens, LocalizedLookupOptions on the server), so they leave
         every list; they stay visible, marked inactive, on System Coding, where they can be deleted.

    Guards: the ids found must equal @ExpectedDuplicates, @ExpectedTestLevels and @ExpectedTestAgencyTypes,
    or it stops without changing anything. It also stops if an open approval request (PENDING, IN_PROGRESS)
    names a duplicate level in its RequestData: approving it after the delete would fail on the foreign
    key, so decide that request first. Idempotent: a second run finds nothing to do.

    Rollback: every moved reference and every switched-off flag is recorded in MOHECORE.ZZ_R533_CHANGES,
    and every deleted level is copied whole into MOHECORE.ZZ_R533_CURRENT_LEVEL_BEFORE, before the change.
    To undo a committed run:

        EXEC sp_set_session_context N'TenantAll', N'1';
        BEGIN TRAN;
        INSERT INTO MOHECORE.STUDENT_CURRENT_LEVEL (STUDENT_CURRENT_LEVEL_ID, STUDENT_CURRENT_LEVEL_NAME,
               STUDENT_CURRENT_LEVEL_NAME_EN, STUDENT_CURRENT_LEVEL_ORDER_NO, STUDENT_CURRENT_LEVEL_STATUS,
               STUDENT_CURRENT_LEVEL_INSERTED_DATE, IS_ACTIVE)
        SELECT b.STUDENT_CURRENT_LEVEL_ID, b.STUDENT_CURRENT_LEVEL_NAME, b.STUDENT_CURRENT_LEVEL_NAME_EN,
               b.STUDENT_CURRENT_LEVEL_ORDER_NO, b.STUDENT_CURRENT_LEVEL_STATUS,
               b.STUDENT_CURRENT_LEVEL_INSERTED_DATE, b.IS_ACTIVE
        FROM MOHECORE.ZZ_R533_CURRENT_LEVEL_BEFORE b
        WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_CURRENT_LEVEL l
                          WHERE l.STUDENT_CURRENT_LEVEL_ID = b.STUDENT_CURRENT_LEVEL_ID);
        UPDATE x SET STUDENT_CURRENT_LEVEL_ID = c.OLD_VALUE FROM MOHECORE.STUDENT_DATA x
          JOIN MOHECORE.ZZ_R533_CHANGES c ON c.TBL = 'STUDENT_DATA' AND c.KEY_VALUE = x.STUDENT_ID;
        UPDATE x SET STUDENT_CURRENT_LEVEL_ID = c.OLD_VALUE FROM MOHECORE.STUDENT_DATA_ARCH x
          JOIN MOHECORE.ZZ_R533_CHANGES c ON c.TBL = 'STUDENT_DATA_ARCH' AND c.KEY_VALUE = x.ID;
        UPDATE x SET STUDENT_CURRENT_LEVEL_ID = c.OLD_VALUE FROM MOHECORE.STUDENT_DATA_STAGING x
          JOIN MOHECORE.ZZ_R533_CHANGES c ON c.TBL = 'STUDENT_DATA_STAGING' AND c.KEY_VALUE = x.STUDENT_ID;
        UPDATE x SET LEVEL_AT_DEATH_ID = c.OLD_VALUE FROM MOHECORE.STUDENT_DEATH_CASE x
          JOIN MOHECORE.ZZ_R533_CHANGES c ON c.TBL = 'STUDENT_DEATH_CASE' AND c.KEY_VALUE = x.DEATH_CASE_ID;
        UPDATE x SET RESOLVED_ID = c.OLD_VALUE FROM MOHECORE.ETL_LOOKUP_ALIAS x
          JOIN MOHECORE.ZZ_R533_CHANGES c ON c.TBL = 'ETL_LOOKUP_ALIAS' AND c.KEY_VALUE = x.ID;
        UPDATE x SET IS_ACTIVE = c.OLD_VALUE FROM MOHECORE.STUDENT_CURRENT_LEVEL x
          JOIN MOHECORE.ZZ_R533_CHANGES c ON c.TBL = 'STUDENT_CURRENT_LEVEL' AND c.KEY_VALUE = x.STUDENT_CURRENT_LEVEL_ID;
        UPDATE x SET IS_ACTIVE = c.OLD_VALUE FROM MOHECORE.STUDENT_AGENCY_TYPE x
          JOIN MOHECORE.ZZ_R533_CHANGES c ON c.TBL = 'STUDENT_AGENCY_TYPE' AND c.KEY_VALUE = x.STUDENT_AGENCY_TYPE_ID;
        COMMIT;
        -- then, once satisfied: DROP TABLE MOHECORE.ZZ_R533_CHANGES; DROP TABLE MOHECORE.ZZ_R533_CURRENT_LEVEL_BEFORE;

    The STUDENT_DATA and STUDENT_DATA_ARCH audit triggers record any student row this changes, both ways.

    Dry-run by default: set @Commit = 1 to keep the changes. Sets TenantAll itself (the student tables are
    under the tenant policy; without it the counts read 0 and the updates touch nothing).

        sqlcmd -S localhost,1433 -d MOHE -I -f 65001 -b -i r5_33_current_level_duplicates_and_test_rows.sql
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Commit BIT = 0;                                               /* 1 = keep the changes; 0 = print, roll back */
DECLARE @ExpectedDuplicates      NVARCHAR(MAX) = N'100,101,102,103,104,105,106';  /* comma-separated, ascending */
DECLARE @ExpectedTestLevels      NVARCHAR(MAX) = N'109';
DECLARE @ExpectedTestAgencyTypes NVARCHAR(MAX) = N'104';

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

/* ── What is there ─────────────────────────────────────────────────────────────────────────────── */

/* Each duplicate level and the lowest-id level with the same English name. */
DECLARE @pairs TABLE (DUP INT PRIMARY KEY, KEEP INT NOT NULL, SAME_ORDER BIT NOT NULL);
INSERT INTO @pairs (DUP, KEEP, SAME_ORDER)
SELECT d.STUDENT_CURRENT_LEVEL_ID, k.STUDENT_CURRENT_LEVEL_ID,
       CASE WHEN ISNULL(d.STUDENT_CURRENT_LEVEL_ORDER_NO, -1) = ISNULL(k.STUDENT_CURRENT_LEVEL_ORDER_NO, -1) THEN 1 ELSE 0 END
FROM MOHECORE.STUDENT_CURRENT_LEVEL d
CROSS APPLY (SELECT TOP 1 w.STUDENT_CURRENT_LEVEL_ID, w.STUDENT_CURRENT_LEVEL_ORDER_NO
             FROM MOHECORE.STUDENT_CURRENT_LEVEL w
             WHERE w.STUDENT_CURRENT_LEVEL_ID < d.STUDENT_CURRENT_LEVEL_ID
               AND LOWER(LTRIM(RTRIM(w.STUDENT_CURRENT_LEVEL_NAME_EN))) = LOWER(LTRIM(RTRIM(d.STUDENT_CURRENT_LEVEL_NAME_EN)))
             ORDER BY w.STUDENT_CURRENT_LEVEL_ID) k
WHERE NULLIF(LTRIM(RTRIM(d.STUDENT_CURRENT_LEVEL_NAME_EN)), N'') IS NOT NULL
  AND LOWER(LTRIM(RTRIM(d.STUDENT_CURRENT_LEVEL_NAME_EN))) <> N'test';

DECLARE @testLevels TABLE (ID INT PRIMARY KEY);
INSERT INTO @testLevels (ID)
SELECT STUDENT_CURRENT_LEVEL_ID FROM MOHECORE.STUDENT_CURRENT_LEVEL
WHERE IS_ACTIVE = 1
  AND LOWER(LTRIM(RTRIM(ISNULL(STUDENT_CURRENT_LEVEL_NAME, N'')))) = N'test'
  AND LOWER(LTRIM(RTRIM(ISNULL(STUDENT_CURRENT_LEVEL_NAME_EN, N'')))) = N'test';

DECLARE @testAgencyTypes TABLE (ID INT PRIMARY KEY);
INSERT INTO @testAgencyTypes (ID)
SELECT STUDENT_AGENCY_TYPE_ID FROM MOHECORE.STUDENT_AGENCY_TYPE
WHERE IS_ACTIVE = 1
  AND LOWER(LTRIM(RTRIM(ISNULL(STUDENT_AGENCY_TYPE_NAME, N'')))) = N'test'
  AND LOWER(LTRIM(RTRIM(ISNULL(STUDENT_AGENCY_TYPE_NAME_EN, N'')))) = N'test';

DECLARE @foundDup NVARCHAR(MAX) = (SELECT STUFF((SELECT ',' + CAST(DUP AS NVARCHAR(10)) FROM @pairs ORDER BY DUP FOR XML PATH('')), 1, 1, ''));
DECLARE @foundTestLevels NVARCHAR(MAX) = (SELECT STUFF((SELECT ',' + CAST(ID AS NVARCHAR(10)) FROM @testLevels ORDER BY ID FOR XML PATH('')), 1, 1, ''));
DECLARE @foundTestAgency NVARCHAR(MAX) = (SELECT STUFF((SELECT ',' + CAST(ID AS NVARCHAR(10)) FROM @testAgencyTypes ORDER BY ID FOR XML PATH('')), 1, 1, ''));

PRINT CONCAT('Duplicate current levels:      ', ISNULL(@foundDup, '(none)'));
PRINT CONCAT('Active "test" current levels:  ', ISNULL(@foundTestLevels, '(none)'));
PRINT CONCAT('Active "test" agency types:    ', ISNULL(@foundTestAgency, '(none)'));

IF @foundDup IS NULL AND @foundTestLevels IS NULL AND @foundTestAgency IS NULL
BEGIN
    PRINT 'Nothing to do.';
    RETURN;
END

/* ── Guards (nothing has changed yet) ──────────────────────────────────────────────────────────── */

IF (@foundDup IS NOT NULL AND @foundDup <> REPLACE(@ExpectedDuplicates, ' ', ''))
   OR (@foundTestLevels IS NOT NULL AND @foundTestLevels <> REPLACE(@ExpectedTestLevels, ' ', ''))
   OR (@foundTestAgency IS NOT NULL AND @foundTestAgency <> REPLACE(@ExpectedTestAgencyTypes, ' ', ''))
BEGIN
    PRINT 'STOPPED - the rows found are not the ones reviewed (@ExpectedDuplicates, @ExpectedTestLevels,'
        + ' @ExpectedTestAgencyTypes). Review the new ones and update the expected ids.';
    RETURN;
END

IF EXISTS (SELECT 1 FROM @pairs WHERE SAME_ORDER = 0)
BEGIN
    SELECT p.DUP, p.KEEP, d.STUDENT_CURRENT_LEVEL_ORDER_NO AS dup_order, k.STUDENT_CURRENT_LEVEL_ORDER_NO AS keep_order
    FROM @pairs p
    JOIN MOHECORE.STUDENT_CURRENT_LEVEL d ON d.STUDENT_CURRENT_LEVEL_ID = p.DUP
    JOIN MOHECORE.STUDENT_CURRENT_LEVEL k ON k.STUDENT_CURRENT_LEVEL_ID = p.KEEP
    WHERE p.SAME_ORDER = 0;
    PRINT 'STOPPED - a level shares an English name with another but not its order number (listed above):'
        + ' they are not the same level. Which one is right is the ministry''s decision.';
    RETURN;
END

DECLARE @openRequests TABLE (Id BIGINT, DUP INT);
INSERT INTO @openRequests (Id, DUP)
SELECT r.Id, p.DUP
FROM ALERTCORE.ApprovalRequests r
JOIN @pairs p ON r.RequestData LIKE CONCAT('%"studentCurrentLevelId":', p.DUP, ',%')
              OR r.RequestData LIKE CONCAT('%"studentCurrentLevelId":', p.DUP, '}%')
              OR r.RequestData LIKE CONCAT('%"levelAtDeathId":', p.DUP, ',%')
              OR r.RequestData LIKE CONCAT('%"levelAtDeathId":', p.DUP, '}%')
WHERE r.Status IN ('PENDING', 'IN_PROGRESS');
IF EXISTS (SELECT 1 FROM @openRequests)
BEGIN
    SELECT Id AS open_request, DUP AS names_duplicate_level FROM @openRequests ORDER BY Id;
    PRINT 'STOPPED - open approval requests (listed above) name a duplicate level. Approve or reject them first.';
    RETURN;
END

/* ── Preview ───────────────────────────────────────────────────────────────────────────────────── */

SELECT p.DUP, d.STUDENT_CURRENT_LEVEL_NAME AS dup_name_ar, p.KEEP, k.STUDENT_CURRENT_LEVEL_NAME AS keep_name_ar,
       k.STUDENT_CURRENT_LEVEL_NAME_EN AS name_en, k.STUDENT_CURRENT_LEVEL_ORDER_NO AS order_no,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA x WHERE x.STUDENT_CURRENT_LEVEL_ID = p.DUP)         AS student_data,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA_ARCH x WHERE x.STUDENT_CURRENT_LEVEL_ID = p.DUP)    AS student_data_arch,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA_STAGING x WHERE x.STUDENT_CURRENT_LEVEL_ID = p.DUP) AS staging,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_DEATH_CASE x WHERE x.LEVEL_AT_DEATH_ID = p.DUP)          AS death_cases,
       (SELECT COUNT(*) FROM MOHECORE.ETL_LOOKUP_ALIAS x WHERE x.LOOKUP = 'CURRENT_LEVEL' AND x.RESOLVED_ID = p.DUP) AS etl_aliases
FROM @pairs p
JOIN MOHECORE.STUDENT_CURRENT_LEVEL d ON d.STUDENT_CURRENT_LEVEL_ID = p.DUP
JOIN MOHECORE.STUDENT_CURRENT_LEVEL k ON k.STUDENT_CURRENT_LEVEL_ID = p.KEEP
ORDER BY p.DUP;

/* ── The change ────────────────────────────────────────────────────────────────────────────────── */

DECLARE @n INT;
BEGIN TRAN;

IF OBJECT_ID('MOHECORE.ZZ_R533_CHANGES') IS NULL
    CREATE TABLE MOHECORE.ZZ_R533_CHANGES (
        ID        INT IDENTITY(1,1) PRIMARY KEY,
        TBL       SYSNAME   NOT NULL,   /* the table changed                        */
        KEY_VALUE BIGINT    NOT NULL,   /* its key: STUDENT_ID, ID, DEATH_CASE_ID … */
        COL       SYSNAME   NOT NULL,   /* the column changed                       */
        OLD_VALUE INT       NULL,
        NEW_VALUE INT       NULL,
        CHANGED_AT DATETIME2 NOT NULL DEFAULT SYSDATETIME());

IF OBJECT_ID('MOHECORE.ZZ_R533_CURRENT_LEVEL_BEFORE') IS NULL
    SELECT TOP 0 *, CAST(NULL AS DATETIME2) AS COPIED_AT
    INTO MOHECORE.ZZ_R533_CURRENT_LEVEL_BEFORE
    FROM MOHECORE.STUDENT_CURRENT_LEVEL;

/* 1a. References to a duplicate move to its twin (recorded first). */
INSERT INTO MOHECORE.ZZ_R533_CHANGES (TBL, KEY_VALUE, COL, OLD_VALUE, NEW_VALUE)
SELECT 'STUDENT_DATA', x.STUDENT_ID, 'STUDENT_CURRENT_LEVEL_ID', p.DUP, p.KEEP
FROM MOHECORE.STUDENT_DATA x JOIN @pairs p ON p.DUP = x.STUDENT_CURRENT_LEVEL_ID;
UPDATE x SET x.STUDENT_CURRENT_LEVEL_ID = p.KEEP
FROM MOHECORE.STUDENT_DATA x JOIN @pairs p ON p.DUP = x.STUDENT_CURRENT_LEVEL_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  STUDENT_DATA rows moved to the kept level');

INSERT INTO MOHECORE.ZZ_R533_CHANGES (TBL, KEY_VALUE, COL, OLD_VALUE, NEW_VALUE)
SELECT 'STUDENT_DATA_ARCH', x.ID, 'STUDENT_CURRENT_LEVEL_ID', p.DUP, p.KEEP
FROM MOHECORE.STUDENT_DATA_ARCH x JOIN @pairs p ON p.DUP = x.STUDENT_CURRENT_LEVEL_ID;
UPDATE x SET x.STUDENT_CURRENT_LEVEL_ID = p.KEEP
FROM MOHECORE.STUDENT_DATA_ARCH x JOIN @pairs p ON p.DUP = x.STUDENT_CURRENT_LEVEL_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  STUDENT_DATA_ARCH rows moved');

INSERT INTO MOHECORE.ZZ_R533_CHANGES (TBL, KEY_VALUE, COL, OLD_VALUE, NEW_VALUE)
SELECT 'STUDENT_DATA_STAGING', x.STUDENT_ID, 'STUDENT_CURRENT_LEVEL_ID', p.DUP, p.KEEP
FROM MOHECORE.STUDENT_DATA_STAGING x JOIN @pairs p ON p.DUP = x.STUDENT_CURRENT_LEVEL_ID;
UPDATE x SET x.STUDENT_CURRENT_LEVEL_ID = p.KEEP
FROM MOHECORE.STUDENT_DATA_STAGING x JOIN @pairs p ON p.DUP = x.STUDENT_CURRENT_LEVEL_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  STUDENT_DATA_STAGING rows moved');

INSERT INTO MOHECORE.ZZ_R533_CHANGES (TBL, KEY_VALUE, COL, OLD_VALUE, NEW_VALUE)
SELECT 'STUDENT_DEATH_CASE', x.DEATH_CASE_ID, 'LEVEL_AT_DEATH_ID', p.DUP, p.KEEP
FROM MOHECORE.STUDENT_DEATH_CASE x JOIN @pairs p ON p.DUP = x.LEVEL_AT_DEATH_ID;
UPDATE x SET x.LEVEL_AT_DEATH_ID = p.KEEP
FROM MOHECORE.STUDENT_DEATH_CASE x JOIN @pairs p ON p.DUP = x.LEVEL_AT_DEATH_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  STUDENT_DEATH_CASE rows moved');

INSERT INTO MOHECORE.ZZ_R533_CHANGES (TBL, KEY_VALUE, COL, OLD_VALUE, NEW_VALUE)
SELECT 'ETL_LOOKUP_ALIAS', x.ID, 'RESOLVED_ID', p.DUP, p.KEEP
FROM MOHECORE.ETL_LOOKUP_ALIAS x JOIN @pairs p ON p.DUP = x.RESOLVED_ID
WHERE x.LOOKUP = 'CURRENT_LEVEL';
UPDATE x SET x.RESOLVED_ID = p.KEEP
FROM MOHECORE.ETL_LOOKUP_ALIAS x JOIN @pairs p ON p.DUP = x.RESOLVED_ID
WHERE x.LOOKUP = 'CURRENT_LEVEL';
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  ETL_LOOKUP_ALIAS rows moved');

/* 1b. The duplicates themselves, copied whole, then deleted. */
INSERT INTO MOHECORE.ZZ_R533_CURRENT_LEVEL_BEFORE
SELECT l.*, SYSDATETIME()
FROM MOHECORE.STUDENT_CURRENT_LEVEL l JOIN @pairs p ON p.DUP = l.STUDENT_CURRENT_LEVEL_ID;
DELETE l FROM MOHECORE.STUDENT_CURRENT_LEVEL l JOIN @pairs p ON p.DUP = l.STUDENT_CURRENT_LEVEL_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  duplicate current levels deleted');

/* 2. The "test" rows, switched off. */
INSERT INTO MOHECORE.ZZ_R533_CHANGES (TBL, KEY_VALUE, COL, OLD_VALUE, NEW_VALUE)
SELECT 'STUDENT_CURRENT_LEVEL', t.ID, 'IS_ACTIVE', 1, 0 FROM @testLevels t;
UPDATE l SET l.IS_ACTIVE = 0
FROM MOHECORE.STUDENT_CURRENT_LEVEL l JOIN @testLevels t ON t.ID = l.STUDENT_CURRENT_LEVEL_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  "test" current levels switched off');

INSERT INTO MOHECORE.ZZ_R533_CHANGES (TBL, KEY_VALUE, COL, OLD_VALUE, NEW_VALUE)
SELECT 'STUDENT_AGENCY_TYPE', t.ID, 'IS_ACTIVE', 1, 0 FROM @testAgencyTypes t;
UPDATE a SET a.IS_ACTIVE = 0
FROM MOHECORE.STUDENT_AGENCY_TYPE a JOIN @testAgencyTypes t ON t.ID = a.STUDENT_AGENCY_TYPE_ID;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  "test" agency types switched off');

/* What the two lists hold now (inside the transaction, so a dry run shows the result it would keep). */
SELECT STUDENT_CURRENT_LEVEL_ID AS id, STUDENT_CURRENT_LEVEL_NAME AS name_ar, STUDENT_CURRENT_LEVEL_NAME_EN AS name_en,
       STUDENT_CURRENT_LEVEL_ORDER_NO AS order_no, IS_ACTIVE AS active
FROM MOHECORE.STUDENT_CURRENT_LEVEL ORDER BY STUDENT_CURRENT_LEVEL_ID;
SELECT STUDENT_AGENCY_TYPE_ID AS id, STUDENT_AGENCY_TYPE_NAME AS name_ar, STUDENT_AGENCY_TYPE_NAME_EN AS name_en, IS_ACTIVE AS active
FROM MOHECORE.STUDENT_AGENCY_TYPE ORDER BY STUDENT_AGENCY_TYPE_ID;

IF @Commit = 1
BEGIN
    COMMIT;
    PRINT 'COMMITTED - the rollback is in the header; MOHECORE.ZZ_R533_* hold what it needs.';
END
ELSE
BEGIN
    ROLLBACK;
    PRINT 'ROLLED BACK (dry run) - set @Commit = 1 to apply';
END
