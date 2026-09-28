/* ============================================================================
   V070 — Dashboard permissions: the whole page, or one section at a time.

   Why
   ---
   Until now the landing page was opened by DASHBOARD.VIEW — a permission that
   existed on the dev database only because someone inserted it by hand (no
   script ever created it) — and, as a side effect of the security expression,
   by REPORT_OTHER.VIEW, REPORTS.VIEW and UMS_ADMIN.VIEW. The owner asked (29 Aug
   2026) for the dashboard to be granted as the whole page, one section, several
   sections or all, to roles or to individual users.

   What
   ----
   1. Creates DASHBOARD.VIEW if absent (a no-op where it was hand-seeded) and one
      page code per section:
         DASHBOARD_STUDENTS.VIEW   DASHBOARD_FINANCE.VIEW   DASHBOARD_APPROVALS.VIEW
         DASHBOARD_ALERTS.VIEW     DASHBOARD_COUNTRIES.VIEW DASHBOARD_USERS.VIEW
         DASHBOARD_QUARTER.VIEW    DASHBOARD_NOTICES.VIEW
      The application's MoheSecurityExpressions gates each /api/v1/dashboard
      section handler with "DASHBOARD.VIEW or DASHBOARD_<SECTION>.VIEW or
      administrator". (No "slash-star" in this header: T-SQL nests comments.)
   2. Grants DASHBOARD.VIEW to every active role, and every user with a direct
      grant, that holds REPORT_OTHER.VIEW, REPORTS.VIEW or UMS_ADMIN.VIEW — the
      three codes that used to open the page as a side effect and no longer do.
      Nobody loses the landing page on upgrade. This grant is deliberate and
      runs unconditionally (the owner's decision), unlike V069's @Confirm gate.
   3. The eight section codes are granted to nobody (V043 rule): which role sees
      which part of the dashboard is the ministry's call, made in Role
      Management or per user in User Management.

   UMSCORE.Permissions is UNIQUE on PermissionName as well as on the code; the
   names below are chosen not to collide with the hand-seeded "View Dashboard".

   Rollback
   --------
     DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
       WHERE p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW';
     DELETE up FROM UMSCORE.UserPermissions up JOIN UMSCORE.Permissions p ON p.Id = up.PermissionId
       WHERE p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW';
     DELETE FROM UMSCORE.Permissions WHERE PermissionCode LIKE 'DASHBOARD[_]%.VIEW';
   (DASHBOARD.VIEW itself and its step-2 grants are left in place: removing
   them would take the page away from roles that had it before this script.)

   Idempotent: every INSERT is guarded by NOT EXISTS; a second run creates and
   grants nothing. Run with sqlcmd -I -f 65001 -b (see the runbook).
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ── 1. The nine permission codes ─────────────────────────────────────────── */
DECLARE @perms TABLE (
    code        varchar(50)   NOT NULL,
    name_en     nvarchar(200) NOT NULL,
    name_ar     nvarchar(200) NOT NULL,
    descr       nvarchar(500) NOT NULL
);
INSERT INTO @perms (code, name_en, name_ar, descr) VALUES
    ('DASHBOARD.VIEW',           N'View Dashboard',                  N'عرض لوحة المعلومات',
        N'The landing page, every section'),
    ('DASHBOARD_STUDENTS.VIEW',  N'Dashboard - Students section',    N'لوحة المعلومات - قسم الطلاب',
        N'Student counts, monthly intake, students by delegation year and by status'),
    ('DASHBOARD_FINANCE.VIEW',   N'Dashboard - Finance section',     N'لوحة المعلومات - القسم المالي',
        N'Disbursed totals, disbursement by year and the Wafar balance'),
    ('DASHBOARD_APPROVALS.VIEW', N'Dashboard - Approvals section',   N'لوحة المعلومات - قسم الاعتمادات',
        N'Pending approvals, by entity type, and the caller''s own requests'),
    ('DASHBOARD_ALERTS.VIEW',    N'Dashboard - Student alert cases', N'لوحة المعلومات - حالات تنبيه الطلاب',
        N'Open student alert cases by status and severity'),
    ('DASHBOARD_COUNTRIES.VIEW', N'Dashboard - Students by country', N'لوحة المعلومات - الطلاب حسب الدولة',
        N'Students by study country'),
    ('DASHBOARD_USERS.VIEW',     N'Dashboard - Users section',       N'لوحة المعلومات - قسم المستخدمين',
        N'Staff accounts of the caller''s countries and their monthly registrations'),
    ('DASHBOARD_QUARTER.VIEW',   N'Dashboard - Current quarter',     N'لوحة المعلومات - الربع الحالي',
        N'The open MOHE quarter visible to the caller'),
    ('DASHBOARD_NOTICES.VIEW',   N'Dashboard - System notices',      N'لوحة المعلومات - إشعارات النظام',
        N'System notices for today and yesterday (not country-specific)');

INSERT INTO UMSCORE.Permissions
    (PermissionCode, PermissionName, PermissionNameAr, Description,
     ResourceType, ActionType, PermissionLevel, IsActive, CreatedAt)
SELECT p.code, p.name_en, p.name_ar, p.descr,
       'PAGE', 'VIEW', 'READ', 1, SYSUTCDATETIME()
  FROM @perms p
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions e WHERE e.PermissionCode = p.code);
PRINT 'V070: created ' + CAST(@@ROWCOUNT AS varchar(10)) + ' dashboard permission(s) (0 means already present).';

/* ── 2. Nobody loses the landing page ─────────────────────────────────────── */
DECLARE @dash bigint = (SELECT Id FROM UMSCORE.Permissions WHERE PermissionCode = 'DASHBOARD.VIEW');
IF @dash IS NULL
    THROW 50070, 'V070: DASHBOARD.VIEW was not created and does not exist. Check UMSCORE.Permissions before re-running.', 1;

INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT DISTINCT rp.RoleId, @dash, SYSUTCDATETIME(), 1
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId AND r.IsActive = 1
 WHERE rp.IsActive = 1
   AND p.PermissionCode IN ('REPORT_OTHER.VIEW', 'REPORTS.VIEW', 'UMS_ADMIN.VIEW')
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x
                    WHERE x.RoleId = rp.RoleId AND x.PermissionId = @dash);
PRINT 'V070: granted DASHBOARD.VIEW to ' + CAST(@@ROWCOUNT AS varchar(10)) + ' role(s) that held a report or UMS code.';

INSERT INTO UMSCORE.UserPermissions (UserId, PermissionId, GrantedAt, IsActive)
SELECT DISTINCT up.UserId, @dash, SYSUTCDATETIME(), 1
  FROM UMSCORE.UserPermissions up
  JOIN UMSCORE.Permissions p ON p.Id = up.PermissionId
 WHERE up.IsActive = 1
   AND p.PermissionCode IN ('REPORT_OTHER.VIEW', 'REPORTS.VIEW', 'UMS_ADMIN.VIEW')
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.UserPermissions x
                    WHERE x.UserId = up.UserId AND x.PermissionId = @dash);
PRINT 'V070: granted DASHBOARD.VIEW directly to ' + CAST(@@ROWCOUNT AS varchar(10)) + ' user(s) that held a report or UMS code.';

/* ── 3. Verification: the nine codes and who holds each ───────────────────── */
PRINT '=== V070: dashboard permissions and the roles holding them ===';
SELECT p.PermissionCode,
       p.PermissionName,
       roles_holding = ISNULL(STUFF((SELECT ', ' + r.RoleName
                                       FROM UMSCORE.RolePermissions rp
                                       JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
                                      WHERE rp.PermissionId = p.Id AND rp.IsActive = 1
                                      ORDER BY r.RoleName
                                        FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, ''), '(nobody)'),
       users_direct = (SELECT COUNT(*) FROM UMSCORE.UserPermissions up
                        WHERE up.PermissionId = p.Id AND up.IsActive = 1)
  FROM UMSCORE.Permissions p
 WHERE p.PermissionCode = 'DASHBOARD.VIEW' OR p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW'
 ORDER BY CASE WHEN p.PermissionCode = 'DASHBOARD.VIEW' THEN 0 ELSE 1 END, p.PermissionCode;

PRINT '=== V070: roles that hold a report or UMS code but still not DASHBOARD.VIEW (expected: none) ===';
SELECT DISTINCT r.RoleName
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId AND r.IsActive = 1
 WHERE rp.IsActive = 1
   AND p.PermissionCode IN ('REPORT_OTHER.VIEW', 'REPORTS.VIEW', 'UMS_ADMIN.VIEW')
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x JOIN UMSCORE.Permissions d ON d.Id = x.PermissionId
                    WHERE x.RoleId = rp.RoleId AND x.IsActive = 1 AND d.PermissionCode = 'DASHBOARD.VIEW');
GO
