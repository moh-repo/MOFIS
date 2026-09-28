/*
    V096 — Approval configurations for privileged administration (four-eyes).

    Security plan S-03 (6 Sep 2026). Granting a privileged right (a tier role,
    any *.APPROVE code, SYSTEM.RESTORE, UMS_ADMIN.*, the administrator flag) and
    restoring a backup now go through the approval workflow unless the caller is
    the super admin (exempt, audited PRIVILEGE_CHANGE_DIRECT). The application
    raises requests of two new entity types; this script gives them a
    configuration so the workflow has a level to route them to.

    What
    ----
    1. ALERTCORE.ApprovalConfigs: USER_PRIVILEGE_CHANGE and SYSTEM_RESTORE, one
       level each, required role MINISTRY_ADMIN (created by V094). The
       requester cannot sign their own request (O-01), so a ministry admin's
       request needs another ministry admin or the super admin; a country
       admin's request needs a ministry admin. Change the level in the approval
       configuration screen like any other (position, named approvers, N-03).
    2. Labels in both languages (the code list "approval-entity-type" reads
       this table).

    Effect
    ------
    None until the build of 6 Sep 2026 is deployed. With that build and
    without this script, a privileged grant by a non-super-admin fails with
    "No approval config found for entity type: USER_PRIVILEGE_CHANGE"
    (the DEFAULT configuration is not used for these on purpose: its levels
    name roles that do not exist on the dev database).

    Rollback
    --------
        DELETE l FROM ALERTCORE.ApprovalLevels l JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
         WHERE c.EntityType IN ('USER_PRIVILEGE_CHANGE','SYSTEM_RESTORE');
        DELETE FROM ALERTCORE.ApprovalConfigs WHERE EntityType IN ('USER_PRIVILEGE_CHANGE','SYSTEM_RESTORE');
    (only if no request of these types exists).

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

DECLARE @ministryAdmin BIGINT = (SELECT TOP 1 Id FROM UMSCORE.Roles WHERE RoleName = 'MINISTRY_ADMIN' AND IsActive = 1);
IF @ministryAdmin IS NULL
BEGIN
    RAISERROR ('V096: the MINISTRY_ADMIN role does not exist; run V094 first.', 16, 1);
    RETURN;
END

DECLARE @types TABLE (EntityType NVARCHAR(100), EntityTypeAr NVARCHAR(200), Descr NVARCHAR(500), DescrAr NVARCHAR(500), LevelName NVARCHAR(200), LevelNameAr NVARCHAR(200));
INSERT INTO @types VALUES
    (N'USER_PRIVILEGE_CHANGE', N'تغيير صلاحيات مستخدم',
     N'A privileged grant (tier role, approval code, restore right, UMS administration, administrator flag) requested by one administrator and approved by a second.',
     N'منح صلاحية حساسة يطلبه مسؤول ويعتمده مسؤول ثانٍ.',
     N'Second administrator', N'مسؤول ثانٍ'),
    (N'SYSTEM_RESTORE', N'استعادة قاعدة البيانات',
     N'Restoring the database from a backup, requested by one administrator and approved by a second.',
     N'استعادة قاعدة البيانات من نسخة احتياطية يطلبها مسؤول ويعتمدها مسؤول ثانٍ.',
     N'Second administrator', N'مسؤول ثانٍ');

DECLARE @et NVARCHAR(100), @etAr NVARCHAR(200), @d NVARCHAR(500), @dAr NVARCHAR(500), @ln NVARCHAR(200), @lnAr NVARCHAR(200);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT EntityType, EntityTypeAr, Descr, DescrAr, LevelName, LevelNameAr FROM @types;
OPEN c;
FETCH NEXT FROM c INTO @et, @etAr, @d, @dAr, @ln, @lnAr;
WHILE @@FETCH_STATUS = 0
BEGIN
    DECLARE @configId BIGINT = (SELECT TOP 1 Id FROM ALERTCORE.ApprovalConfigs WHERE EntityType = @et);
    IF @configId IS NULL
    BEGIN
        INSERT INTO ALERTCORE.ApprovalConfigs (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt)
        VALUES (@et, @etAr, @d, @dAr, 1, 1, 1, SYSUTCDATETIME());
        SET @configId = SCOPE_IDENTITY();
        PRINT 'V096: approval configuration ' + @et + ' created (id ' + CAST(@configId AS NVARCHAR(20)) + ').';
    END
    ELSE
    BEGIN
        UPDATE ALERTCORE.ApprovalConfigs SET IsActive = 1, EntityTypeAr = COALESCE(EntityTypeAr, @etAr) WHERE Id = @configId;
        PRINT 'V096: approval configuration ' + @et + ' already exists (id ' + CAST(@configId AS NVARCHAR(20)) + ').';
    END
    IF NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalLevels WHERE ConfigId = @configId AND LevelOrder = 1)
    BEGIN
        INSERT INTO ALERTCORE.ApprovalLevels (ConfigId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, CreatedAt)
        VALUES (@configId, 1, @ln, @lnAr, @ministryAdmin, 0, 1, SYSUTCDATETIME());
        PRINT 'V096: level 1 of ' + @et + ' requires MINISTRY_ADMIN.';
    END
    FETCH NEXT FROM c INTO @et, @etAr, @d, @dAr, @ln, @lnAr;
END
CLOSE c;
DEALLOCATE c;
GO

PRINT 'V096: the four-eyes configurations:';
SELECT c.EntityType, c.TotalLevels, l.LevelOrder, r.RoleName AS RequiredRole, l.RequiredPositionCode
  FROM ALERTCORE.ApprovalConfigs c
  JOIN ALERTCORE.ApprovalLevels l ON l.ConfigId = c.Id AND l.IsActive = 1
  LEFT JOIN UMSCORE.Roles r ON r.Id = l.RequiredRoleId
 WHERE c.EntityType IN ('USER_PRIVILEGE_CHANGE', 'SYSTEM_RESTORE')
 ORDER BY c.EntityType, l.LevelOrder;
GO
