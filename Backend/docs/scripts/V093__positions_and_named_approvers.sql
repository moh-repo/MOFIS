/*
    V093 — Staff positions, and approval levels that name who approves.

    Security plan N-03 (6 Sep 2026): "approval configuration must take users
    and positions from the user table". Until now an approval level carried a
    required role and a free-text name that stood in for a position, and the
    staff row had only a free-text Position column nobody read.

    What
    ----
    1. MOHECORE.LU_POSITION — a code list in the V072 shape (CODE, NAME_AR,
       NAME_EN, SORT_ORDER = rank, IS_DELETED, UI_SEVERITY, ICON, IS_SYSTEM),
       registered as "position" in CodeListRegistry and maintained in Settings
       like every other list (decision D-17 a). Seeded with the positions the
       ministry uses; rename or add rows in Settings, not here.
    2. UMSCORE.Users.PositionCode — nullable, FK to LU_POSITION(CODE). The
       old free-text Position column is left in place; where its text equals a
       seeded name the code is filled in (N-03.g).
    3. ALERTCORE.ApprovalLevels.RequiredPositionCode — nullable FK. A level
       that requires a position is approved by the holder of that position in
       the request's country (ApprovalWorkflowService, N-03.c). Where a level's
       LevelName equals a seeded position name the code is pre-filled; the
       rest are listed for the administrator.
    4. ALERTCORE.ApprovalLevelApprovers (LevelId, UserId, CountryId NULL) —
       named approvers, optionally per country. When a level names anyone for
       the request's country (or for every country), only those may approve it.

    Also listed: levels whose RequiredRoleId matches no role (on the dev
    database two ids, 13 and 15, reference nothing).

    Rollback
    --------
        DROP TABLE ALERTCORE.ApprovalLevelApprovers;
        ALTER TABLE ALERTCORE.ApprovalLevels DROP CONSTRAINT FK_ApprovalLevels_Position; ALTER TABLE ALERTCORE.ApprovalLevels DROP COLUMN RequiredPositionCode;
        ALTER TABLE UMSCORE.Users DROP CONSTRAINT FK_Users_Position; ALTER TABLE UMSCORE.Users DROP COLUMN PositionCode;
        DROP TABLE MOHECORE.LU_POSITION;

    **The application build of the same date refuses to boot without the list**
    (CodeListStartupGuard). Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF OBJECT_ID('MOHECORE.LU_POSITION', 'U') IS NULL
BEGIN
    CREATE TABLE MOHECORE.LU_POSITION (
        ID          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_LU_POSITION PRIMARY KEY,
        CODE        NVARCHAR(50) NOT NULL,
        NAME_AR     NVARCHAR(200) NOT NULL,
        NAME_EN     NVARCHAR(200) NOT NULL,
        SORT_ORDER  INT NOT NULL CONSTRAINT DF_LU_POSITION_SORT DEFAULT 0,
        IS_DELETED  BIT NOT NULL CONSTRAINT DF_LU_POSITION_DEL DEFAULT 0,
        UI_SEVERITY NVARCHAR(20) NULL,
        ICON        NVARCHAR(50) NULL,
        IS_SYSTEM   BIT NOT NULL CONSTRAINT DF_LU_POSITION_IS_SYSTEM DEFAULT 0,
        CONSTRAINT UQ_LU_POSITION_CODE UNIQUE (CODE)
    );
    PRINT 'V093: MOHECORE.LU_POSITION created';
END
GO

DECLARE @seed TABLE (code NVARCHAR(50), name_ar NVARCHAR(200), name_en NVARCHAR(200), sort_order INT, icon NVARCHAR(50));
INSERT INTO @seed VALUES
  (N'UNDERSECRETARY',    N'وكيل الوزارة',            N'Undersecretary',            10, N'pi-star'),
  (N'MINISTRY_DIRECTOR', N'مدير إدارة بالوزارة',      N'Ministry director',         20, N'pi-briefcase'),
  (N'DEPARTMENT_HEAD',   N'رئيس قسم',                 N'Department head',           30, N'pi-sitemap'),
  (N'ATTACHE',           N'الملحق الثقافي',           N'Cultural attaché',          40, N'pi-globe'),
  (N'DEPUTY_ATTACHE',    N'نائب الملحق الثقافي',      N'Deputy cultural attaché',   50, N'pi-globe'),
  (N'FINANCE_OFFICER',   N'مسؤول مالي',               N'Finance officer',           60, N'pi-wallet'),
  (N'ACADEMIC_OFFICER',  N'مسؤول أكاديمي',            N'Academic officer',          70, N'pi-book'),
  (N'ADMIN_OFFICER',     N'مسؤول إداري',              N'Administrative officer',    80, N'pi-user');

INSERT INTO MOHECORE.LU_POSITION (CODE, NAME_AR, NAME_EN, SORT_ORDER, ICON, IS_SYSTEM)
SELECT s.code, s.name_ar, s.name_en, s.sort_order, s.icon, 0
  FROM @seed s
 WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.LU_POSITION p WHERE p.CODE = s.code);
PRINT CONCAT('V093: positions seeded: ', @@ROWCOUNT);
GO

IF COL_LENGTH('UMSCORE.Users', 'PositionCode') IS NULL
    ALTER TABLE UMSCORE.Users ADD PositionCode NVARCHAR(50) NULL;
IF COL_LENGTH('ALERTCORE.ApprovalLevels', 'RequiredPositionCode') IS NULL
    ALTER TABLE ALERTCORE.ApprovalLevels ADD RequiredPositionCode NVARCHAR(50) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Users_Position')
    ALTER TABLE UMSCORE.Users ADD CONSTRAINT FK_Users_Position
        FOREIGN KEY (PositionCode) REFERENCES MOHECORE.LU_POSITION (CODE);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ApprovalLevels_Position')
    ALTER TABLE ALERTCORE.ApprovalLevels ADD CONSTRAINT FK_ApprovalLevels_Position
        FOREIGN KEY (RequiredPositionCode) REFERENCES MOHECORE.LU_POSITION (CODE);
GO

IF OBJECT_ID('ALERTCORE.ApprovalLevelApprovers', 'U') IS NULL
BEGIN
    CREATE TABLE ALERTCORE.ApprovalLevelApprovers (
        Id        BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ApprovalLevelApprovers PRIMARY KEY,
        LevelId   BIGINT NOT NULL CONSTRAINT FK_ApprovalLevelApprovers_Level
                      REFERENCES ALERTCORE.ApprovalLevels (Id) ON DELETE CASCADE,
        UserId    BIGINT NOT NULL CONSTRAINT FK_ApprovalLevelApprovers_User
                      REFERENCES UMSCORE.Users (Id),
        CountryId INT NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_ApprovalLevelApprovers_CreatedAt DEFAULT SYSUTCDATETIME()
    );
    CREATE UNIQUE INDEX UQ_ApprovalLevelApprovers ON ALERTCORE.ApprovalLevelApprovers (LevelId, UserId, CountryId);
    PRINT 'V093: ALERTCORE.ApprovalLevelApprovers created';
END
GO

/* ---- N-03.g: pre-fill from the free text where it matches ---------------- */
UPDATE u SET u.PositionCode = p.CODE
  FROM UMSCORE.Users u
  JOIN MOHECORE.LU_POSITION p ON p.IS_DELETED = 0
   AND (LTRIM(RTRIM(u.Position)) = p.NAME_EN OR LTRIM(RTRIM(u.Position)) = p.NAME_AR OR UPPER(LTRIM(RTRIM(u.Position))) = p.CODE)
 WHERE u.PositionCode IS NULL AND u.Position IS NOT NULL;
PRINT CONCAT('V093: users whose free-text position matched a code: ', @@ROWCOUNT);

UPDATE l SET l.RequiredPositionCode = p.CODE
  FROM ALERTCORE.ApprovalLevels l
  JOIN MOHECORE.LU_POSITION p ON p.IS_DELETED = 0
   AND (LTRIM(RTRIM(l.LevelName)) = p.NAME_EN OR LTRIM(RTRIM(l.LevelNameAr)) = p.NAME_AR)
 WHERE l.RequiredPositionCode IS NULL;
PRINT CONCAT('V093: levels whose name matched a position: ', @@ROWCOUNT);

PRINT 'V093: active levels with no position yet (set one in Approval Configuration if the level is a position):';
SELECT c.EntityType, l.LevelOrder, l.LevelName, l.RequiredRoleId
  FROM ALERTCORE.ApprovalLevels l JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
 WHERE l.IsActive = 1 AND l.RequiredPositionCode IS NULL
 ORDER BY c.EntityType, l.LevelOrder;

PRINT 'V093: active levels whose RequiredRoleId matches no active role (nobody but the super admin can approve them):';
SELECT c.EntityType, l.LevelOrder, l.LevelName, l.RequiredRoleId
  FROM ALERTCORE.ApprovalLevels l JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
 WHERE l.IsActive = 1 AND l.RequiredRoleId IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.Roles r WHERE r.Id = l.RequiredRoleId AND r.IsActive = 1)
 ORDER BY c.EntityType, l.LevelOrder;

PRINT 'V093: staff with no position (approval levels that require one cannot route to them):';
SELECT u.Username FROM UMSCORE.Users u WHERE u.IsDeleted = 0 AND u.PositionCode IS NULL ORDER BY u.Username;
GO
