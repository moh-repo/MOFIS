/*
    V098 - Time-bound roles, delegation, and the ministry's Wafar correction path.

    Security plan R-21 (decision D-11) and N-04.e (decision D-16 (a)), 6 Sep 2026.

    What
    ----
    1. UMSCORE.UserRoles gains ValidFrom / ValidTo (the window in which the row
       counts), DelegatedFrom (the account whose roles were copied, for a
       delegation) and Reason. IsActive stays the flag every permission read
       uses: a row is switched on when its window opens and off when it closes
       by the nightly RoleWindowJob, so no query changes.
    2. ALERTCORE.ApprovalConfigs: WAFAR_CORRECTION, one level, required role
       COUNTRY_ADMIN (V094). A ministry account that finds a wrong Wafar row
       raises a correction request; the attache's administrator approves it and
       the change is applied as them, so the row is still written only by the
       country that owns it (N-04).

    Effect
    ------
    None until the build of 6 Sep 2026 is deployed.

    Rollback
    --------
    ALTER TABLE UMSCORE.UserRoles DROP COLUMN ValidFrom, ValidTo, DelegatedFrom, Reason;
    DELETE l FROM ALERTCORE.ApprovalLevels l JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId WHERE c.EntityType = 'WAFAR_CORRECTION';
    DELETE FROM ALERTCORE.ApprovalConfigs WHERE EntityType = 'WAFAR_CORRECTION';
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF COL_LENGTH('UMSCORE.UserRoles', 'ValidFrom') IS NULL
BEGIN
    ALTER TABLE UMSCORE.UserRoles ADD
        ValidFrom     DATETIME2      NULL,
        ValidTo       DATETIME2      NULL,
        DelegatedFrom BIGINT         NULL,
        Reason        NVARCHAR(500)  NULL;
    PRINT N'V098: UMSCORE.UserRoles window columns added.';
END
ELSE
    PRINT N'V098: UMSCORE.UserRoles window columns already exist.';
GO

DECLARE @countryAdmin BIGINT = (SELECT TOP 1 Id FROM UMSCORE.Roles WHERE RoleName = 'COUNTRY_ADMIN' AND IsActive = 1);
IF @countryAdmin IS NULL
BEGIN
    RAISERROR ('V098: the COUNTRY_ADMIN role does not exist; run V094 first.', 16, 1);
    RETURN;
END
DECLARE @configId BIGINT = (SELECT TOP 1 Id FROM ALERTCORE.ApprovalConfigs WHERE EntityType = N'WAFAR_CORRECTION');
IF @configId IS NULL
BEGIN
    INSERT INTO ALERTCORE.ApprovalConfigs (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt)
    VALUES (N'WAFAR_CORRECTION', N'تصحيح وفر',
            N'A correction to a Wafar spend proposed by the ministry and approved by the attache that holds the money (N-04.e).',
            N'تصحيح لصرف من الوفر يقترحه ديوان الوزارة ويعتمده الملحق الذي يملك المال.',
            1, 1, 1, SYSUTCDATETIME());
    SET @configId = SCOPE_IDENTITY();
    PRINT 'V098: approval configuration WAFAR_CORRECTION created (id ' + CAST(@configId AS NVARCHAR(20)) + ').';
END
ELSE
BEGIN
    UPDATE ALERTCORE.ApprovalConfigs SET IsActive = 1 WHERE Id = @configId;
    PRINT 'V098: approval configuration WAFAR_CORRECTION already exists (id ' + CAST(@configId AS NVARCHAR(20)) + ').';
END
IF NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalLevels WHERE ConfigId = @configId AND LevelOrder = 1)
BEGIN
    INSERT INTO ALERTCORE.ApprovalLevels (ConfigId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, CreatedAt)
    VALUES (@configId, 1, N'Attache administrator', N'مسؤول الملحقية', @countryAdmin, 0, 1, SYSUTCDATETIME());
    PRINT 'V098: level 1 of WAFAR_CORRECTION requires COUNTRY_ADMIN.';
END
GO
