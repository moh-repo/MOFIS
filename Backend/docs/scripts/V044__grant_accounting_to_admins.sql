/*
    V044 — grant the accounting permissions to the administrator roles.

    Why
    ---
    V043 created the eight accounting permissions and assigned them to nobody,
    on the grounds that who may edit the chart of accounts or release a transfer
    is an organisational decision. That was the right default, and it left the
    "Accounting System" menu invisible to everyone: AppMenuComponent is
    fail-closed, so a page whose code the user cannot view simply never appears.

    This grants them to the administrator roles, as requested.

    Who gets what
    -------------
    Both administrator roles get all eight. That is a deliberate choice for
    administrators and NOT a template for anyone else:

      * FINANCE_OFFICER, WAFAR_OFFICER and the rest are untouched here. A finance
        officer who needs to read the chart should get ACCOUNTING.VIEW and
        COA.VIEW, and nothing more, unless someone decides otherwise.

      * ALLOCATION.CREATE and ALLOCATION.APPROVE are separate permissions so that
        the officer who proposes a transfer need not be the one who releases it.
        Granting both to an administrator collapses that separation for
        administrators only. If segregation of duties matters for your audit,
        the pair should be split across two operational roles and the
        administrator roles left as the exception.

    Which roles count as administrator
    ----------------------------------
    UMSCORE.Roles holds two: 'Super Admin' (id 1) and 'Admin' (id 2), plus a
    legacy 'SUPER_ADMIN' (id 8). All three are matched by name so the script does
    not depend on ids that differ between deployments.

    The user accounts behind them -- superadmin (IsSuperAdmin=1), admin,
    sa_superadmin, sa_admin, jo_admin -- already receive ROLE_ADMIN from
    JwtAuthFilter because of their IsAdmin/IsSuperAdmin flags, so the API was
    always reachable for them. What they lacked was the page permission the menu
    checks, which is what this fixes.

    Rollback
    --------
        DELETE rp FROM UMSCORE.RolePermissions rp
        JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
        WHERE p.PermissionCode IN ('ACCOUNTING.VIEW','ACCOUNTING.EDIT','COA.VIEW',
              'COA.EDIT','COA.APPROVE','ALLOCATION.CREATE','ALLOCATION.APPROVE',
              'FISCAL_PERIOD.CLOSE');
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

DECLARE @codes TABLE (code varchar(50) PRIMARY KEY);
INSERT INTO @codes (code) VALUES
    ('ACCOUNTING.VIEW'), ('ACCOUNTING.EDIT'),
    ('COA.VIEW'), ('COA.EDIT'), ('COA.APPROVE'),
    ('ALLOCATION.CREATE'), ('ALLOCATION.APPROVE'),
    ('FISCAL_PERIOD.CLOSE');

DECLARE @adminRoles TABLE (RoleId int PRIMARY KEY);
INSERT INTO @adminRoles (RoleId)
SELECT r.Id FROM UMSCORE.Roles r
WHERE r.IsActive = 1
  AND r.RoleName IN ('Super Admin', 'Admin', 'SUPER_ADMIN');

IF NOT EXISTS (SELECT 1 FROM @adminRoles)
    THROW 50044, 'V044: no active administrator role found. Check UMSCORE.Roles before re-running.', 1;

INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT ar.RoleId, p.Id, SYSUTCDATETIME(), 1
FROM @adminRoles ar
CROSS JOIN UMSCORE.Permissions p
JOIN @codes c ON c.code = p.PermissionCode
WHERE NOT EXISTS (
    SELECT 1 FROM UMSCORE.RolePermissions rp
    WHERE rp.RoleId = ar.RoleId AND rp.PermissionId = p.Id);

PRINT 'V044: granted ' + CAST(@@ROWCOUNT AS varchar(10)) + ' role-permission rows.';
GO

/* ------------------------------------------------------------------ *
 * Verification
 * ------------------------------------------------------------------ */
PRINT '';
PRINT '================ V044 verification ================';

PRINT '--- each administrator role must hold all 8 ---';
SELECT r.RoleName, COUNT(*) AS accounting_permissions
FROM UMSCORE.RolePermissions rp
JOIN UMSCORE.Roles r       ON r.Id = rp.RoleId
JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
WHERE p.PermissionCode IN ('ACCOUNTING.VIEW','ACCOUNTING.EDIT','COA.VIEW','COA.EDIT',
                           'COA.APPROVE','ALLOCATION.CREATE','ALLOCATION.APPROVE','FISCAL_PERIOD.CLOSE')
GROUP BY r.RoleName
ORDER BY r.RoleName;

PRINT '';
PRINT '--- the admin users these roles reach ---';
SELECT u.Username, u.IsAdmin, u.IsSuperAdmin, r.RoleName
FROM UMSCORE.Users u
JOIN UMSCORE.UserRoles ur ON ur.UserId = u.Id
JOIN UMSCORE.Roles r      ON r.Id = ur.RoleId
WHERE r.RoleName IN ('Super Admin', 'Admin', 'SUPER_ADMIN')
ORDER BY u.Username;

PRINT '';
PRINT '--- no NON-administrator role should have picked these up ---';
SELECT r.RoleName, COUNT(*) AS granted
FROM UMSCORE.RolePermissions rp
JOIN UMSCORE.Roles r       ON r.Id = rp.RoleId
JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
WHERE p.PermissionCode LIKE 'ACCOUNTING.%' OR p.PermissionCode LIKE 'COA.%'
   OR p.PermissionCode LIKE 'ALLOCATION.%' OR p.PermissionCode = 'FISCAL_PERIOD.CLOSE'
GROUP BY r.RoleName
HAVING r.RoleName NOT IN ('Super Admin', 'Admin', 'SUPER_ADMIN');
PRINT '--- (no rows above is the expected result) ---';
PRINT '=============== end V044 verification =============';
GO
