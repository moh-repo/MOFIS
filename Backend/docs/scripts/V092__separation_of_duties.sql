/*
    V092 — Separation of duties as data.

    Security plan R-15 (6 Sep 2026). UMSCORE.PermissionConflicts lists the
    pairs of permission codes one person must not hold together — the maker
    and checker halves of the flows that move money. The application
    (SeparationOfDutiesService) refuses, with 409, any assignment whose result
    would put both halves on one account: a role or group to a user, a direct
    grant, a permission to a role, a role to a group. The super admin is exempt
    (the audited exception, decisions D-12/D-13).

    Seeded pairs
    ------------
        ALLOCATION.CREATE     / ALLOCATION.APPROVE      active
        PAYMENT_BATCH.CREATE  / PAYMENT_BATCH.APPROVE   active
        WAFAR.ADD             / WAFAR.APPROVE           INACTIVE — while
            single-officer attachés exist (D-12) the per-transaction
            maker-checker in WafarSpendingService is the control; set
            IsActive = 1 when every attaché has two finance users.

    What this script does NOT do
    ----------------------------
    It removes nothing. The two reports at the end list the roles that carry
    both halves of an active pair (on the dev database: Super Admin, Admin and
    SUPER_ADMIN, which hold ALLOCATION.CREATE with ALLOCATION.APPROVE) and the
    accounts that hold both through any path. Until a role is split, assigning
    it is refused; the administrators resolve the existing ones. V094 seeds the
    new tier bundles without the maker halves, so the tier roles are clean.

    Rollback: DROP TABLE UMSCORE.PermissionConflicts (and the application build
    that reads it). Idempotent: the table and each pair are created only if
    absent; a pair an administrator has since switched off stays off.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF OBJECT_ID('UMSCORE.PermissionConflicts', 'U') IS NULL
BEGIN
    CREATE TABLE UMSCORE.PermissionConflicts (
        Id        BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PermissionConflicts PRIMARY KEY,
        CodeA     NVARCHAR(100) NOT NULL,
        CodeB     NVARCHAR(100) NOT NULL,
        Reason    NVARCHAR(500) NULL,
        IsActive  BIT NOT NULL CONSTRAINT DF_PermissionConflicts_IsActive DEFAULT 1,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_PermissionConflicts_CreatedAt DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_PermissionConflicts_Pair UNIQUE (CodeA, CodeB),
        CONSTRAINT CK_PermissionConflicts_Distinct CHECK (CodeA <> CodeB)
    );
    PRINT 'V092: UMSCORE.PermissionConflicts created';
END
GO

DECLARE @pairs TABLE (CodeA NVARCHAR(100), CodeB NVARCHAR(100), Reason NVARCHAR(500), IsActive BIT);
INSERT INTO @pairs VALUES
  (N'ALLOCATION.CREATE',    N'ALLOCATION.APPROVE',    N'The officer who prepares an allocation must not release it (V040 maker-checker).', 1),
  (N'PAYMENT_BATCH.CREATE', N'PAYMENT_BATCH.APPROVE', N'The officer who prepares a payment batch must not release the money (V063).', 1),
  (N'WAFAR.ADD',            N'WAFAR.APPROVE',         N'Recording and approving Wafar spending are two people; inactive until every attaché has two finance users (D-12).', 0);

INSERT INTO UMSCORE.PermissionConflicts (CodeA, CodeB, Reason, IsActive)
SELECT p.CodeA, p.CodeB, p.Reason, p.IsActive
  FROM @pairs p
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.PermissionConflicts c
                    WHERE (c.CodeA = p.CodeA AND c.CodeB = p.CodeB) OR (c.CodeA = p.CodeB AND c.CodeB = p.CodeA));
PRINT CONCAT('V092: pairs inserted: ', @@ROWCOUNT);

/* ---- Report 1: roles carrying both halves of an active pair -------------- */
PRINT 'V092: roles that carry both halves of a separated duty (split them; assigning them is refused until then):';
SELECT r.RoleName, c.CodeA, c.CodeB
  FROM UMSCORE.PermissionConflicts c
  JOIN UMSCORE.Roles r ON r.IsActive = 1
 WHERE c.IsActive = 1
   AND EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
                WHERE rp.RoleId = r.Id AND rp.IsActive = 1 AND p.PermissionCode = c.CodeA)
   AND EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
                WHERE rp.RoleId = r.Id AND rp.IsActive = 1 AND p.PermissionCode = c.CodeB)
 ORDER BY r.RoleName, c.CodeA;

/* ---- Report 2: accounts holding both halves through any path ------------- */
IF OBJECT_ID('tempdb..#eff') IS NOT NULL DROP TABLE #eff;
SELECT DISTINCT x.UserId, p.PermissionCode
  INTO #eff
  FROM (
        SELECT ur.UserId, rp.PermissionId
          FROM UMSCORE.UserRoles ur
          JOIN UMSCORE.Roles r ON r.Id = ur.RoleId AND r.IsActive = 1
          JOIN UMSCORE.RolePermissions rp ON rp.RoleId = ur.RoleId AND rp.IsActive = 1
         WHERE ur.IsActive = 1
        UNION ALL
        SELECT ug.UserId, rp.PermissionId
          FROM UMSCORE.UserGroupMemberships ug
          JOIN UMSCORE.GroupRoles gr ON gr.GroupId = ug.GroupId AND gr.IsActive = 1
          JOIN UMSCORE.Roles r ON r.Id = gr.RoleId AND r.IsActive = 1
          JOIN UMSCORE.RolePermissions rp ON rp.RoleId = gr.RoleId AND rp.IsActive = 1
         WHERE ug.IsActive = 1
        UNION ALL
        SELECT up.UserId, up.PermissionId
          FROM UMSCORE.UserPermissions up
         WHERE up.IsActive = 1 AND (up.ExpiresAt IS NULL OR up.ExpiresAt > SYSUTCDATETIME())
       ) x
  JOIN UMSCORE.Permissions p ON p.Id = x.PermissionId AND p.IsActive = 1;

PRINT 'V092: accounts that hold both halves (super admins excluded; resolve by role, nothing is changed here):';
SELECT u.Username, c.CodeA, c.CodeB
  FROM UMSCORE.PermissionConflicts c
  JOIN UMSCORE.Users u ON u.IsDeleted = 0 AND u.IsSuperAdmin = 0
 WHERE c.IsActive = 1
   AND EXISTS (SELECT 1 FROM #eff e WHERE e.UserId = u.Id AND e.PermissionCode = c.CodeA)
   AND EXISTS (SELECT 1 FROM #eff e WHERE e.UserId = u.Id AND e.PermissionCode = c.CodeB)
 ORDER BY u.Username, c.CodeA;
DROP TABLE #eff;
GO
