/*
    The finance approval chains, one position per level  --  FIX-PLAN D2-a with D25 (P5-12, 26 Sep 2026).
    DBA script, not a migration. WRITTEN, NOT RUN (decision D19): review it, dry-run it, then decide.

    Why: since M3 no one may approve two levels of the same request. STUDENT_FINANCE and
    STUDENT_FINANCE_WAFAR are seeded as HR Manager x 4, so every finance request would need four
    different HR Managers. D2-a sets them to L1 Accountant -> L2 Chief Accountant -> L3 HR Manager ->
    L4 Attaché. D25 (your answer of 26 Sep): no new roles — staff get their privileges from their
    roles when the account is created, and a level names a POSITION; whoever holds it, in the request's
    country, approves. So this script creates no role and grants nothing.

    What it does:
      1. Adds the positions the chain needs to MOHECORE.LU_POSITION (the list the Users screen and the
         Approval Rules screen offer) when their code is missing:
            ACCOUNTANT        محاسب                  Accountant
            CHIEF_ACCOUNTANT  رئيس المحاسبين         Chief accountant
            HR_MANAGER        مدير الموارد البشرية    HR manager
         The fourth level uses the existing ATTACHE (Cultural attaché). Dev has 8 positions, none of
         these three. SORT_ORDER places them between the deputy attaché (50) and the administrative
         officer (80); change it on the Code Lists screen if the rank should differ.
      2. For each of STUDENT_FINANCE and STUDENT_FINANCE_WAFAR (found by entity type, not by id): sets
         levels 1-4 to ACCOUNTANT, CHIEF_ACCOUNTANT, HR_MANAGER, ATTACHE, CLEARS their required role (a
         level that asks for a role and a position needs both) and renames them to match. Only a chain
         still in its seeded shape is changed: four active levels, each requiring the HR Manager role and
         no position. A chain already edited on the Approval Rules screen is left alone and reported.
      3. Before changing anything, copies the chain's levels as they are into
         ALERTCORE.ZZ_APPROVAL_LEVELS_BEFORE_D2 (created on the first run; a later run never overwrites
         it), which the rollback below restores from. ApprovalLevels has no audit trigger.

    Preview (read before setting @Commit = 1):
      - the chains before and after;
      - the requests in flight on them: a step reads its level's rule live, so a request waiting at
        level 1 then waits for an Accountant (dev: one STUDENT_FINANCE request, PENDING at level 1);
      - per attaché country, how many active accounts hold each of the four positions — the "can each
        office staff all four" check D2 asks for. DEV: NO ACCOUNT HOLDS ANY POSITION. Give the positions
        to people on the Users screen first; with nobody in a position, the finance chains wait.

    Idempotent: a second run finds the chains no longer in their seeded shape and changes nothing.
    Dry-run by default: set @Commit = 1 to keep the changes. Sets TenantAll itself.

        sqlcmd -S localhost,1433 -d MOHE -I -f 65001 -b -i reconfigure_finance_approval_chains.sql

    Rollback (after a committed run)
    --------
        UPDATE l SET l.RequiredRoleId = z.RequiredRoleId, l.RequiredPositionCode = z.RequiredPositionCode,
                     l.LevelName = z.LevelName, l.LevelNameAr = z.LevelNameAr
          FROM ALERTCORE.ApprovalLevels l JOIN ALERTCORE.ZZ_APPROVAL_LEVELS_BEFORE_D2 z ON z.Id = l.Id;
        -- the three positions may stay (unused) or be retired: UPDATE MOHECORE.LU_POSITION SET IS_DELETED = 1
        --   WHERE CODE IN (N'ACCOUNTANT', N'CHIEF_ACCOUNTANT', N'HR_MANAGER');
        DROP TABLE ALERTCORE.ZZ_APPROVAL_LEVELS_BEFORE_D2;
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Commit BIT = 0;   /* 1 = keep the changes; 0 = print, roll back */

-- Approval requests carry row-level security; the preview must see every country's.
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @hrManager BIGINT = (SELECT TOP 1 Id FROM UMSCORE.Roles WHERE RoleName = N'HR Manager');

DECLARE @plan TABLE (LevelOrder INT PRIMARY KEY, PositionCode NVARCHAR(50), LevelName NVARCHAR(200), LevelNameAr NVARCHAR(200));
INSERT INTO @plan VALUES
    (1, N'ACCOUNTANT',       N'Accountant review',         N'مراجعة المحاسب'),
    (2, N'CHIEF_ACCOUNTANT', N'Chief accountant review',   N'مراجعة رئيس المحاسبين'),
    (3, N'HR_MANAGER',       N'HR manager approval',       N'موافقة مدير الموارد البشرية'),
    (4, N'ATTACHE',          N'Cultural attaché approval', N'اعتماد الملحق الثقافي');

DECLARE @newPositions TABLE (Code NVARCHAR(50) PRIMARY KEY, NameAr NVARCHAR(200), NameEn NVARCHAR(200), SortOrder INT);
INSERT INTO @newPositions VALUES
    (N'HR_MANAGER',       N'مدير الموارد البشرية', N'HR manager',       55),
    (N'CHIEF_ACCOUNTANT', N'رئيس المحاسبين',       N'Chief accountant', 57),
    (N'ACCOUNTANT',       N'محاسب',                N'Accountant',       65);

/* Which chains are still in their seeded shape */
DECLARE @chains TABLE (ConfigId BIGINT PRIMARY KEY, EntityType NVARCHAR(100));
INSERT INTO @chains (ConfigId, EntityType)
SELECT c.Id, c.EntityType
FROM ALERTCORE.ApprovalConfigs c
WHERE c.EntityType IN (N'STUDENT_FINANCE', N'STUDENT_FINANCE_WAFAR')
  AND (SELECT COUNT(*) FROM ALERTCORE.ApprovalLevels l WHERE l.ConfigId = c.Id AND l.IsActive = 1) = 4
  AND NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalLevels l
                   WHERE l.ConfigId = c.Id AND l.IsActive = 1
                     AND (l.LevelOrder NOT BETWEEN 1 AND 4
                          OR ISNULL(l.RequiredRoleId, -1) <> ISNULL(@hrManager, -2)
                          OR l.RequiredPositionCode IS NOT NULL));

SELECT c.EntityType,
       CASE WHEN ch.ConfigId IS NULL THEN 'NOT in its seeded shape - left alone' ELSE 'will be reconfigured' END AS verdict
FROM ALERTCORE.ApprovalConfigs c
LEFT JOIN @chains ch ON ch.ConfigId = c.Id
WHERE c.EntityType IN (N'STUDENT_FINANCE', N'STUDENT_FINANCE_WAFAR');

IF NOT EXISTS (SELECT 1 FROM @chains)
BEGIN
    PRINT 'Nothing to do: neither finance chain is in its seeded shape (HR Manager x 4, no position).';
    RETURN;
END

/* Preview 1: the chains before and after */
SELECT ch.EntityType, l.LevelOrder, l.LevelName AS name_now, r.RoleName AS role_now, l.RequiredPositionCode AS position_now,
       p.LevelName AS name_after, CAST(NULL AS NVARCHAR(50)) AS role_after, p.PositionCode AS position_after
FROM @chains ch
JOIN ALERTCORE.ApprovalLevels l ON l.ConfigId = ch.ConfigId AND l.IsActive = 1
JOIN @plan p ON p.LevelOrder = l.LevelOrder
LEFT JOIN UMSCORE.Roles r ON r.Id = l.RequiredRoleId
ORDER BY ch.EntityType, l.LevelOrder;

/* Preview 2: requests in flight on those chains */
SELECT r.EntityType, r.Status, r.CurrentLevel, COUNT(*) AS requests
FROM ALERTCORE.ApprovalRequests r
JOIN @chains ch ON ch.EntityType = r.EntityType
WHERE r.Status = N'PENDING'
GROUP BY r.EntityType, r.Status, r.CurrentLevel;

/* Preview 3: who would approve, per attaché country (active, not deleted accounts holding the position) */
SELECT ISNULL(sci.STUDY_COUNTRY_IN_NAME_EN, N'(no country: ministry-wide)') AS attache,
       SUM(CASE WHEN u.PositionCode = N'ACCOUNTANT'       THEN 1 ELSE 0 END) AS accountants,
       SUM(CASE WHEN u.PositionCode = N'CHIEF_ACCOUNTANT' THEN 1 ELSE 0 END) AS chief_accountants,
       SUM(CASE WHEN u.PositionCode = N'HR_MANAGER'       THEN 1 ELSE 0 END) AS hr_managers,
       SUM(CASE WHEN u.PositionCode = N'ATTACHE'          THEN 1 ELSE 0 END) AS attaches
FROM UMSCORE.Users u
LEFT JOIN UMSCORE.UserCountries uc ON uc.UserId = u.Id AND uc.IsActive = 1
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = uc.CountryId
WHERE ISNULL(u.IsDeleted, 0) = 0 AND u.Status = N'ACTIVE'
  AND u.PositionCode IN (N'ACCOUNTANT', N'CHIEF_ACCOUNTANT', N'HR_MANAGER', N'ATTACHE')
GROUP BY sci.STUDY_COUNTRY_IN_NAME_EN
ORDER BY attache;
DECLARE @holders INT = (SELECT COUNT(*) FROM UMSCORE.Users u WHERE ISNULL(u.IsDeleted, 0) = 0 AND u.Status = N'ACTIVE'
                          AND u.PositionCode IN (N'ACCOUNTANT', N'CHIEF_ACCOUNTANT', N'HR_MANAGER', N'ATTACHE'));
PRINT CONCAT('Active accounts holding any of the four positions: ', @holders,
             ' (0 means nobody could approve the finance chains after this script)');

DECLARE @n INT;
BEGIN TRAN;

/* 0. The copy the rollback restores from (first run only) */
IF OBJECT_ID(N'ALERTCORE.ZZ_APPROVAL_LEVELS_BEFORE_D2', N'U') IS NULL
BEGIN
    SELECT l.Id, l.ConfigId, l.LevelOrder, l.LevelName, l.LevelNameAr, l.RequiredRoleId, l.RequiredPositionCode,
           SYSUTCDATETIME() AS CopiedAt
      INTO ALERTCORE.ZZ_APPROVAL_LEVELS_BEFORE_D2
      FROM ALERTCORE.ApprovalLevels l
      JOIN @chains ch ON ch.ConfigId = l.ConfigId;
    SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  levels copied to ALERTCORE.ZZ_APPROVAL_LEVELS_BEFORE_D2');
END

/* 1. The positions the chain needs */
INSERT INTO MOHECORE.LU_POSITION (CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED, IS_SYSTEM)
SELECT np.Code, np.NameAr, np.NameEn, np.SortOrder, 0, 0
FROM @newPositions np
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.LU_POSITION p WHERE p.CODE = np.Code);
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  positions added');

UPDATE MOHECORE.LU_POSITION SET IS_DELETED = 0
WHERE CODE IN (N'ACCOUNTANT', N'CHIEF_ACCOUNTANT', N'HR_MANAGER', N'ATTACHE') AND IS_DELETED = 1;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  retired positions restored');

/* 2. The levels: one position each, no role, named to match */
UPDATE l SET l.RequiredPositionCode = p.PositionCode,
             l.RequiredRoleId = NULL,
             l.LevelName = p.LevelName,
             l.LevelNameAr = p.LevelNameAr
FROM ALERTCORE.ApprovalLevels l
JOIN @chains ch ON ch.ConfigId = l.ConfigId
JOIN @plan p ON p.LevelOrder = l.LevelOrder
WHERE l.IsActive = 1 AND l.RequiredPositionCode IS NULL AND l.RequiredRoleId = @hrManager;
SET @n = @@ROWCOUNT; PRINT CONCAT(RIGHT('     ' + CAST(@n AS NVARCHAR(10)), 5), '  levels reconfigured');

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
