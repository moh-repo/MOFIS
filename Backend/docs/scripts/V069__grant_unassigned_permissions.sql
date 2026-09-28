/*
    V069 — grant the permissions that no role holds.

    !!  THIS SCRIPT ONLY REPORTS UNTIL YOU SET @Confirm = 1 BELOW, AND     !!
    !!  UNTIL THE ROLE NAMES IN @map HAVE BEEN FILLED IN.                  !!

    Why
    ---
    V043, V063, V065 and V066 create permissions and, deliberately, assign them
    to no role: who may release a payment or restore the database is an
    organisational decision, not a migration's. Until somebody makes it the
    codes below are dead letters — administrators pass through ROLE_ADMIN
    regardless, and everyone else is refused:

        ALERT_EVENTS.VIEW        view student alert cases            (V066)
        ALERT_EVENTS.MANAGE      acknowledge / resolve / dismiss     (V066)
        ALERT_SCENARIOS.MANAGE   configure scenario thresholds       (V066)
        PAYMENT_BATCH.CREATE     prepare a payment batch             (V063)
        PAYMENT_BATCH.APPROVE    approve, pay or cancel it           (V063)
        BUDGET.EDIT              enter the annual appropriation      (V063)
        SYSTEM.RESTORE           restore the database from a backup  (V065)

    How to use it
    -------------
    Edit @map: one row per code, the RoleName exactly as in UMSCORE.Roles
    (15 roles on the dev database: Super Admin, Admin, HR Manager,
    Finance Manager, Student Officer, Report Viewer, Employee, SUPER_ADMIN,
    STAFF, REVIEWER, FINANCE_OFFICER, REPORTS_VIEWER, WAFAR_OFFICER,
    ARCHIVING_OFFICER, STUDENT_PORTAL_ADMIN). A code may appear on several
    rows. Rows still carrying the placeholder are skipped and said so, as are
    rows naming a role that does not exist or is inactive. Then set
    @Confirm = 1. The grants follow V044's INSERT (RoleId, PermissionId,
    GrantedAt, IsActive) and are never duplicated.

    Keep PAYMENT_BATCH.CREATE and PAYMENT_BATCH.APPROVE on different roles
    unless the role is an administrator (V063: the preparer must not release
    the money; PaymentBatchService.approve refuses the preparer anyway).

    Rollback
    --------
        DELETE rp FROM UMSCORE.RolePermissions rp
        JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
        WHERE p.PermissionCode IN (<the codes you granted>) AND rp.RoleId IN (<the roles>);

    Idempotent: existing grants are left alone; a second run grants 0 rows.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 *  1. SET THIS TO 1 TO GRANT. Leave at 0 to report only.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @Confirm BIT = 1;   /* filled in and confirmed by the owner, 1 Sep 2026 */

/* ─────────────────────────────────────────────────────────────────────────────
 *  2. WHO GETS WHAT. Replace every '<<ROLE NAME>>' with a RoleName from
 *     UMSCORE.Roles; add rows to give one code to several roles.
 *
 *     Example (not active — the owner decides):
 *         ('ALERT_EVENTS.VIEW',      'Student Officer'),
 *         ('ALERT_EVENTS.MANAGE',    'Student Officer'),
 *         ('ALERT_SCENARIOS.MANAGE', 'Admin'),
 *         ('PAYMENT_BATCH.CREATE',   'FINANCE_OFFICER'),
 *         ('PAYMENT_BATCH.APPROVE',  'Finance Manager'),
 *         ('BUDGET.EDIT',            'Finance Manager'),
 *         ('SYSTEM.RESTORE',         'Super Admin')
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @map TABLE (PermissionCode varchar(50) NOT NULL, RoleName nvarchar(100) NOT NULL);
/* The owner's decision, 1 Sep 2026.

   Alerts: the officer who works the cases reads them; changing what raises a
   case is configuration, so it sits with Admin. STAFF reads them too — the
   alert list is a working screen, not a report.

   Finance: PAYMENT_BATCH.CREATE and PAYMENT_BATCH.APPROVE go to different
   roles on purpose. V063 created them as a pair for exactly this reason, and a
   single role holding both would mean one person raising and approving the
   same payment. BUDGET.EDIT is the appropriation — money, not structure — so
   it goes with the approver.

   SYSTEM.RESTORE: named rather than implied. The two administrator roles
   already reach it through ROLE_ADMIN; granting it explicitly means the role
   screen shows who can restore the system instead of leaving it to be inferred. */
INSERT INTO @map (PermissionCode, RoleName) VALUES
    ('ALERT_EVENTS.VIEW',      N'Student Officer'),
    ('ALERT_EVENTS.VIEW',      N'STAFF'),
    ('ALERT_EVENTS.MANAGE',    N'Admin'),
    ('ALERT_SCENARIOS.MANAGE', N'Admin'),
    ('PAYMENT_BATCH.CREATE',   N'FINANCE_OFFICER'),
    ('PAYMENT_BATCH.APPROVE',  N'Finance Manager'),
    ('BUDGET.EDIT',            N'Finance Manager'),
    ('SYSTEM.RESTORE',         N'Super Admin'),
    ('SYSTEM.RESTORE',         N'SUPER_ADMIN');

DECLARE @codes TABLE (code varchar(50) PRIMARY KEY);
INSERT INTO @codes (code) VALUES
    ('ALERT_EVENTS.VIEW'), ('ALERT_EVENTS.MANAGE'), ('ALERT_SCENARIOS.MANAGE'),
    ('PAYMENT_BATCH.CREATE'), ('PAYMENT_BATCH.APPROVE'), ('BUDGET.EDIT'), ('SYSTEM.RESTORE');

PRINT '';
PRINT '=== V069: permissions granted to no role ===';

/* ── Report ───────────────────────────────────────────────────────────────── */
SELECT c.code AS PermissionCode,
       CASE WHEN p.Id IS NULL THEN 'MISSING (run V063/V065/V066)' ELSE 'present' END AS permission,
       ISNULL((SELECT COUNT(*) FROM UMSCORE.RolePermissions rp WHERE rp.PermissionId = p.Id AND rp.IsActive = 1), 0) AS roles_holding,
       STUFF((SELECT ', ' + r.RoleName FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
               WHERE rp.PermissionId = p.Id AND rp.IsActive = 1 ORDER BY r.RoleName FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, '') AS held_by
  FROM @codes c
  LEFT JOIN UMSCORE.Permissions p ON p.PermissionCode = c.code
 ORDER BY c.code;

DECLARE @ungranted INT = (SELECT COUNT(*) FROM @codes c LEFT JOIN UMSCORE.Permissions p ON p.PermissionCode = c.code
                           WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp WHERE rp.PermissionId = p.Id AND rp.IsActive = 1));
PRINT 'V069: codes held by no active role: ' + CAST(@ungranted AS VARCHAR(5)) + ' of 7';

/* What @map would do, row by row. */
SELECT m.PermissionCode, m.RoleName,
       CASE WHEN m.RoleName = N'<<ROLE NAME>>' THEN 'placeholder — edit @map'
            WHEN p.Id IS NULL THEN 'permission missing'
            WHEN r.Id IS NULL THEN 'no such role'
            WHEN r.IsActive = 0 THEN 'role inactive'
            WHEN EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp WHERE rp.RoleId = r.Id AND rp.PermissionId = p.Id) THEN 'already granted'
            ELSE 'will grant' END AS action_
  FROM @map m
  LEFT JOIN UMSCORE.Permissions p ON p.PermissionCode = m.PermissionCode
  LEFT JOIN UMSCORE.Roles r ON r.RoleName = m.RoleName
 ORDER BY m.PermissionCode, m.RoleName;

/* ── Grant ────────────────────────────────────────────────────────────────── */
IF @Confirm = 0
    PRINT 'V069: report only. Fill in @map and set @Confirm = 1 to grant.';
ELSE
BEGIN
    IF EXISTS (SELECT 1 FROM @map WHERE RoleName = N'<<ROLE NAME>>')
        PRINT 'V069: *** rows still carrying the placeholder role name are skipped';
    IF EXISTS (SELECT 1 FROM @map m WHERE m.RoleName <> N'<<ROLE NAME>>' AND NOT EXISTS (SELECT 1 FROM UMSCORE.Roles r WHERE r.RoleName = m.RoleName AND r.IsActive = 1))
        PRINT 'V069: *** rows naming a role that does not exist or is inactive are skipped (see the table above)';

    INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
    SELECT r.Id, p.Id, SYSUTCDATETIME(), 1
      FROM @map m
      JOIN UMSCORE.Permissions p ON p.PermissionCode = m.PermissionCode
      JOIN UMSCORE.Roles r ON r.RoleName = m.RoleName AND r.IsActive = 1
     WHERE m.RoleName <> N'<<ROLE NAME>>'
       AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp WHERE rp.RoleId = r.Id AND rp.PermissionId = p.Id);
    PRINT 'V069: granted ' + CAST(@@ROWCOUNT AS VARCHAR(10)) + ' role-permission row(s).';
END

/* ── Verification ─────────────────────────────────────────────────────────── */
PRINT '';
PRINT '--- who holds these codes now ---';
SELECT p.PermissionCode, r.RoleName, rp.GrantedAt
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
  JOIN @codes c ON c.code = p.PermissionCode
 WHERE rp.IsActive = 1
 ORDER BY p.PermissionCode, r.RoleName;
PRINT '(no rows above means nothing is granted yet — the menu stays invisible to non-administrators)';
GO
