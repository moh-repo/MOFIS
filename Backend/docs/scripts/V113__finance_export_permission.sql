/*
    V113 — FINANCE.EXPORT: the permission behind the finance Excel export button.

    Review round 2, item F1 (19 Sep 2026). The export button on the finance page
    ("السجلات المالية") is shown only to holders of FINANCE.EXPORT. The code was
    never in a migration or in PermissionCatalog; some databases have it from an
    early hand seed (the development one since Jan 2026, granted to seven roles
    but not to FINANCE_OFFICER, WAFAR_OFFICER or STAFF), others do not.
    Administrators used to pass every permission check; since 6 Sep they hold
    real permissions only, so wherever the code or the grant was missing the
    button disappeared.

    What
    ----
    1. The code is inserted (or reactivated), on the same page as FINANCE.VIEW.
    2. Every active role holding FINANCE.VIEW receives it. Those roles can already
       see every row the export writes, so this changes who sees the button, not
       what data they can read. Take it off a role on the permission screen to
       stop that role exporting.

    Direct user grants of FINANCE.VIEW are not copied: since V091 a direct grant
    carries a reason and an expiry, which this script cannot invent.

    PermissionCatalogStartupGuard refuses to start outside the dev profile until
    this code exists, so apply this script before deploying the build of
    19 Sep 2026 or later.

    Rollback
    --------
        DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
         WHERE p.PermissionCode = N'FINANCE.EXPORT';
        DELETE FROM UMSCORE.Permissions WHERE PermissionCode = N'FINANCE.EXPORT';

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- 1. The code ----------------------------------------------------------- */
UPDATE UMSCORE.Permissions SET IsActive = 1, UpdatedAt = SYSUTCDATETIME()
 WHERE PermissionCode = N'FINANCE.EXPORT' AND IsActive = 0;
PRINT CONCAT('V113: reactivated: ', @@ROWCOUNT);

INSERT INTO UMSCORE.Permissions
       (PermissionCode, PermissionName, PermissionNameAr, ResourceType, ResourceId, ActionId,
        ActionType, PermissionLevel, Description, IsActive, CreatedAt)
SELECT N'FINANCE.EXPORT', N'Export Student Finance', N'تصدير السجلات المالية', N'PAGE',
       (SELECT TOP 1 s.ResourceId FROM UMSCORE.Permissions s
         WHERE s.PermissionCode LIKE N'FINANCE.%' AND s.ResourceId IS NOT NULL),
       NULL, N'EXPORT', N'READ', N'Export the student finance records to Excel', 1, SYSUTCDATETIME()
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions p WHERE p.PermissionCode = N'FINANCE.EXPORT');
PRINT CONCAT('V113: inserted: ', @@ROWCOUNT);
GO

/* ---- 2. Roles that can view finance can export it ---------------------------- */
INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT DISTINCT rp.RoleId, np.Id, SYSUTCDATETIME(), 1
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions op ON op.Id = rp.PermissionId AND op.PermissionCode = N'FINANCE.VIEW'
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId AND r.IsActive = 1
 CROSS JOIN UMSCORE.Permissions np
 WHERE np.PermissionCode = N'FINANCE.EXPORT'
   AND rp.IsActive = 1
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x WHERE x.RoleId = rp.RoleId AND x.PermissionId = np.Id);
PRINT CONCAT('V113: role grants inserted: ', @@ROWCOUNT);
GO

/* ---- Verify ------------------------------------------------------------------ */
SELECT r.RoleName, CASE WHEN ex.Id IS NULL THEN 'MISSING' ELSE 'ok' END AS finance_export
  FROM UMSCORE.Roles r
  JOIN UMSCORE.RolePermissions rp ON rp.RoleId = r.Id AND rp.IsActive = 1
  JOIN UMSCORE.Permissions v ON v.Id = rp.PermissionId AND v.PermissionCode = N'FINANCE.VIEW'
  LEFT JOIN UMSCORE.RolePermissions ex ON ex.RoleId = r.Id AND ex.IsActive = 1
       AND ex.PermissionId = (SELECT Id FROM UMSCORE.Permissions WHERE PermissionCode = N'FINANCE.EXPORT')
 WHERE r.IsActive = 1
 ORDER BY r.RoleName;
