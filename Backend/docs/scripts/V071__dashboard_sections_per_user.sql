/* ============================================================================
   V071 — Dashboard sections granted to every user, to be revoked per user.

   Why
   ---
   V070 created the section codes and left them ungranted, with DASHBOARD.VIEW
   (the whole page) on the roles. The owner decided otherwise the same day
   (29 Aug 2026): every user starts with every section, and sections are taken
   away per user in User Management. A per-user revoke can only have an effect
   if the user does not also hold "the whole page" through a role, so this
   script moves the dashboard off the roles and onto the users.

   What
   ----
   1. Deactivates (IsActive = 0, not deleted) every role grant of the nine
      DASHBOARD codes. Role Management shows them unchecked; ticking one again
      restores it. Administrators (IsAdmin, IsSuperAdmin, IsMinistryUser) see
      the whole page regardless, through ROLE_ADMIN — a revoke does nothing
      for them.
   2. Grants the eight DASHBOARD_<SECTION>.VIEW codes directly to every
      non-deleted user (UMSCORE.UserPermissions), guarded by NOT EXISTS on
      the (user, permission) pair whatever its IsActive. The UI revokes by
      setting IsActive = 0, so a later re-run of this script does not undo a
      revoke. DASHBOARD.VIEW itself is NOT granted per user: it would make the
      section revokes meaningless.
   3. Accounts created after this script hold no dashboard section until one
      is granted (User Management → Permissions) or a role is given a code.

   Rollback
   --------
     UPDATE rp SET IsActive = 1 FROM UMSCORE.RolePermissions rp
       JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
      WHERE p.PermissionCode = 'DASHBOARD.VIEW' AND rp.IsActive = 0;      -- the V070 state
     DELETE up FROM UMSCORE.UserPermissions up
       JOIN UMSCORE.Permissions p ON p.Id = up.PermissionId
      WHERE p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW' AND up.GrantedBy IS NULL;

   Idempotent: a second run deactivates and grants nothing. Run with
   sqlcmd -I -f 65001 -b (see the runbook).
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions WHERE PermissionCode = 'DASHBOARD_STUDENTS.VIEW')
    THROW 50071, 'V071: the dashboard section codes do not exist — run V070 first.', 1;

/* ── 1. The dashboard leaves the roles ────────────────────────────────────── */
PRINT '=== V071: role grants of dashboard codes being deactivated ===';
SELECT r.RoleName, p.PermissionCode
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
 WHERE rp.IsActive = 1
   AND (p.PermissionCode = 'DASHBOARD.VIEW' OR p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW')
 ORDER BY r.RoleName, p.PermissionCode;

UPDATE rp SET IsActive = 0
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
 WHERE rp.IsActive = 1
   AND (p.PermissionCode = 'DASHBOARD.VIEW' OR p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW');
PRINT 'V071: deactivated ' + CAST(@@ROWCOUNT AS varchar(10)) + ' role grant(s) of dashboard codes (0 means already done).';

/* ── 2. Every section to every user ───────────────────────────────────────── */
INSERT INTO UMSCORE.UserPermissions (UserId, PermissionId, GrantedAt, IsActive)
SELECT u.Id, p.Id, SYSUTCDATETIME(), 1
  FROM UMSCORE.Users u
 CROSS JOIN UMSCORE.Permissions p
 WHERE u.IsDeleted = 0
   AND p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW'
   AND p.IsActive = 1
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.UserPermissions x
                    WHERE x.UserId = u.Id AND x.PermissionId = p.Id);
PRINT 'V071: granted ' + CAST(@@ROWCOUNT AS varchar(10)) + ' user-section pair(s) (0 means already present or revoked).';

/* ── 3. Verification ──────────────────────────────────────────────────────── */
PRINT '=== V071: sections per user (active direct grants; administrators see everything regardless) ===';
SELECT u.Id, u.Username,
       is_admin = CASE WHEN u.IsAdmin = 1 OR u.IsSuperAdmin = 1 OR u.IsMinistryUser = 1 THEN 1 ELSE 0 END,
       sections = (SELECT COUNT(*) FROM UMSCORE.UserPermissions up
                    JOIN UMSCORE.Permissions p ON p.Id = up.PermissionId
                   WHERE up.UserId = u.Id AND up.IsActive = 1 AND p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW')
  FROM UMSCORE.Users u
 WHERE u.IsDeleted = 0
 ORDER BY u.Id;

PRINT '=== V071: active role grants of dashboard codes (expected: none) ===';
SELECT r.RoleName, p.PermissionCode
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
 WHERE rp.IsActive = 1
   AND (p.PermissionCode = 'DASHBOARD.VIEW' OR p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW');
GO
