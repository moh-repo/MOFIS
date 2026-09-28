/*
    V145 — the maker codes leave the seeded admin roles; FINANCE_OFFICER makes allocations.

    Live QA L10, decision D23-a (26 Sep 2026). The seeded roles Admin, Super Admin and
    SUPER_ADMIN hold both halves of a separated duty — ALLOCATION.CREATE with
    ALLOCATION.APPROVE (V044), and for SUPER_ADMIN also PAYMENT_BATCH.CREATE with
    PAYMENT_BATCH.APPROVE (its V094 bundle is every code) — so
    SeparationOfDutiesService refuses to give them to anyone who is not a super admin.
    Removing the maker halves alone (D10-a) would have left no role able to create an
    allocation: on the development database only those three roles hold
    ALLOCATION.CREATE. So:

    1. ALLOCATION.CREATE is granted to FINANCE_OFFICER, the role that already makes
       payment batches (PAYMENT_BATCH.CREATE) and holds neither approve code.
    2. ALLOCATION.CREATE and PAYMENT_BATCH.CREATE are switched off (IsActive = 0, as
       V094 does) on the roles named Admin, Super Admin and SUPER_ADMIN. They keep the
       approve codes.

    Effect: the three roles can be assigned again; the super admins can approve
    allocations and payment batches but no longer create them; allocations are created
    by FINANCE_OFFICER holders, so give that role to the people who propose them.

    Preview (PRINT): the grants about to change, and every active account that would
    hold both halves of a pair afterwards (through a role, a group or a direct grant) —
    printed as a warning, not a stop, because a failed migration stops the backend.
    On the development database (read 26 Sep 2026) that list is empty: FINANCE_OFFICER's
    one holder has no other role.

    Nothing is done unless FINANCE_OFFICER and ALLOCATION.CREATE both exist, so the
    admin roles never lose the maker code with no one to take it over.
    Strictly idempotent: every statement is guarded by the state it produces; a second
    run changes nothing. V094 is a versioned migration and does not run again; a future
    migration that grants "every code" to SUPER_ADMIN would bring the maker codes back.

    Rollback
    --------
        UPDATE rp SET rp.IsActive = 1
          FROM UMSCORE.RolePermissions rp
          JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
          JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
         WHERE r.RoleName IN (N'Admin', N'Super Admin', N'SUPER_ADMIN')
           AND p.PermissionCode IN (N'ALLOCATION.CREATE', N'PAYMENT_BATCH.CREATE');
        UPDATE rp SET rp.IsActive = 0
          FROM UMSCORE.RolePermissions rp
          JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
          JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
         WHERE r.RoleName = N'FINANCE_OFFICER' AND p.PermissionCode = N'ALLOCATION.CREATE';
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @financeOfficer BIGINT = (SELECT TOP 1 Id FROM UMSCORE.Roles WHERE RoleName = N'FINANCE_OFFICER');
DECLARE @allocationCreate BIGINT = (SELECT TOP 1 Id FROM UMSCORE.Permissions WHERE PermissionCode = N'ALLOCATION.CREATE');

IF @financeOfficer IS NULL OR @allocationCreate IS NULL
BEGIN
    PRINT 'V145: *** FINANCE_OFFICER or ALLOCATION.CREATE absent — nothing changed; the admin roles keep their maker codes';
    RETURN;
END

-- ── Preview 1: the admin roles' maker grants that will be switched off ─────────
DECLARE @stripping NVARCHAR(MAX);
SELECT @stripping = COALESCE(@stripping + N', ', N'') + r.RoleName + N' / ' + p.PermissionCode
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
 WHERE rp.IsActive = 1
   AND r.RoleName IN (N'Admin', N'Super Admin', N'SUPER_ADMIN')
   AND p.PermissionCode IN (N'ALLOCATION.CREATE', N'PAYMENT_BATCH.CREATE');
PRINT CONCAT('V145: maker grants to switch off: ', ISNULL(@stripping, N'none'));

-- ── Preview 2: accounts that would hold both halves of a pair afterwards ──────
-- What each active account holds after this script: its roles (direct or through a
-- group), less the switched-off admin maker grants, plus FINANCE_OFFICER's new code,
-- plus direct grants.
DECLARE @conflicts NVARCHAR(MAX);
WITH roles_of AS (
    SELECT ur.UserId, ur.RoleId FROM UMSCORE.UserRoles ur WHERE ur.IsActive = 1
    UNION
    SELECT m.UserId, gr.RoleId
      FROM UMSCORE.UserGroupMemberships m
      JOIN UMSCORE.GroupRoles gr ON gr.GroupId = m.GroupId AND gr.IsActive = 1
     WHERE m.IsActive = 1
), codes_after AS (
    SELECT ro.UserId, p.PermissionCode
      FROM roles_of ro
      JOIN UMSCORE.Roles r ON r.Id = ro.RoleId AND r.IsActive = 1
      JOIN UMSCORE.RolePermissions rp ON rp.RoleId = r.Id AND rp.IsActive = 1
      JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
     WHERE NOT (r.RoleName IN (N'Admin', N'Super Admin', N'SUPER_ADMIN')
                AND p.PermissionCode IN (N'ALLOCATION.CREATE', N'PAYMENT_BATCH.CREATE'))
    UNION
    SELECT ro.UserId, N'ALLOCATION.CREATE' FROM roles_of ro WHERE ro.RoleId = @financeOfficer
    UNION
    SELECT up.UserId, p.PermissionCode
      FROM UMSCORE.UserPermissions up
      JOIN UMSCORE.Permissions p ON p.Id = up.PermissionId
     WHERE up.IsActive = 1
)
SELECT @conflicts = COALESCE(@conflicts + N', ', N'') + u.UserName + N' (' + pc.CodeA + N' + ' + pc.CodeB + N')'
  FROM UMSCORE.PermissionConflicts pc
  JOIN codes_after a ON a.PermissionCode = pc.CodeA
  JOIN codes_after b ON b.PermissionCode = pc.CodeB AND b.UserId = a.UserId
  JOIN UMSCORE.Users u ON u.Id = a.UserId AND ISNULL(u.IsDeleted, 0) = 0
 WHERE pc.IsActive = 1
   AND pc.CodeA IN (N'ALLOCATION.CREATE', N'PAYMENT_BATCH.CREATE');
PRINT CONCAT('V145: accounts holding both halves of a pair afterwards (warning only): ', ISNULL(@conflicts, N'none'));

BEGIN TRANSACTION;

-- ── 1. FINANCE_OFFICER makes allocations ───────────────────────────────────────
UPDATE UMSCORE.RolePermissions SET IsActive = 1
 WHERE RoleId = @financeOfficer AND PermissionId = @allocationCreate AND IsActive = 0;
PRINT CONCAT('V145: FINANCE_OFFICER / ALLOCATION.CREATE reactivated: ', @@ROWCOUNT);

INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT @financeOfficer, @allocationCreate, SYSUTCDATETIME(), 1
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions
                    WHERE RoleId = @financeOfficer AND PermissionId = @allocationCreate);
PRINT CONCAT('V145: FINANCE_OFFICER / ALLOCATION.CREATE granted: ', @@ROWCOUNT);

-- ── 2. the seeded admin roles lose the maker halves ───────────────────────────
UPDATE rp SET rp.IsActive = 0
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
  JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
 WHERE rp.IsActive = 1
   AND r.RoleName IN (N'Admin', N'Super Admin', N'SUPER_ADMIN')
   AND p.PermissionCode IN (N'ALLOCATION.CREATE', N'PAYMENT_BATCH.CREATE');
PRINT CONCAT('V145: admin maker grants switched off: ', @@ROWCOUNT);

COMMIT TRANSACTION;
