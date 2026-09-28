/*
    V142 — APP_CONFIG.EDIT: the permission behind changing approval rules.

    FIX-PLAN ESC (25 Sep 2026). Approval configurations and their levels — which
    carry the role each approval step needs — were written under the approvals
    read rule, so an account that could only view approvals could rewrite who
    approves what. The writes now need APP_CONFIG.EDIT, which joins
    PermissionCatalog; its startup guard refuses to start outside the dev profile
    while the code is missing.

    The code exists on the development database (id 55, "Configurations - Edit"),
    held by Admin, Finance Manager, HR Manager, MINISTRY_ADMIN, Super Admin and
    SUPER_ADMIN, and V094 already treats it as ministry-only (it is withheld from
    COUNTRY_ADMIN). No migration inserted it, so another database may lack it.
    This script only fills that gap (FIX-PLAN decision D18-c):

    1. The code is inserted when absent, or reactivated when inactive.
    2. Only when step 1 had to INSERT it, it is granted to the two ministry tier
       roles, SUPER_ADMIN and MINISTRY_ADMIN. A database that already has the code
       keeps its own grants untouched.

    On the development database this changes nothing. Strictly idempotent.

    Rollback (only on a database where step 1 inserted the code)
    --------
        DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
         WHERE p.PermissionCode = N'APP_CONFIG.EDIT';
        DELETE FROM UMSCORE.Permissions WHERE PermissionCode = N'APP_CONFIG.EDIT';
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @inserted INT = 0;

UPDATE UMSCORE.Permissions SET IsActive = 1, UpdatedAt = SYSUTCDATETIME()
 WHERE PermissionCode = N'APP_CONFIG.EDIT' AND IsActive = 0;
PRINT CONCAT('V142: reactivated: ', @@ROWCOUNT);

INSERT INTO UMSCORE.Permissions
       (PermissionCode, PermissionName, PermissionNameAr, ResourceType, ResourceId, ActionId,
        ActionType, PermissionLevel, Description, IsActive, CreatedAt)
SELECT N'APP_CONFIG.EDIT', N'Configurations - Edit', N'إعدادات الموافقات - تعديل', N'PAGE',
       (SELECT TOP 1 s.ResourceId FROM UMSCORE.Permissions s
         WHERE s.PermissionCode = N'APP_CONFIG.VIEW' AND s.ResourceId IS NOT NULL),
       NULL, N'EDIT', N'WRITE', N'Create, change and remove approval rules', 1, SYSUTCDATETIME()
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions p WHERE p.PermissionCode = N'APP_CONFIG.EDIT');
SET @inserted = @@ROWCOUNT;
PRINT CONCAT('V142: inserted: ', @inserted);

IF @inserted = 1
BEGIN
    INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
    SELECT r.Id, np.Id, SYSUTCDATETIME(), 1
      FROM UMSCORE.Roles r
     CROSS JOIN UMSCORE.Permissions np
     WHERE np.PermissionCode = N'APP_CONFIG.EDIT'
       AND r.IsActive = 1
       AND r.RoleName IN (N'SUPER_ADMIN', N'MINISTRY_ADMIN')
       AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x WHERE x.RoleId = r.Id AND x.PermissionId = np.Id);
    PRINT CONCAT('V142: role grants inserted: ', @@ROWCOUNT);
END
GO
