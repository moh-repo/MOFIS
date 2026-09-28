/*
    V146 — the seeded Admin and Super Admin roles edit the budget appropriation.

    Round-5 review, decision D-3 (27 Sep 2026). BUDGET_EDIT used to accept
    ACCOUNTING.EDIT as well as BUDGET.EDIT. D-3 took that alternative away, because
    V094 gives ACCOUNTING.EDIT to COUNTRY_ADMIN and a country admin could revise the
    ministry's appropriation through the API. Admin and Super Admin reached the budget
    only through that same alternative: they hold ACCOUNTING.EDIT and COA.EDIT (V044)
    but were never given BUDGET.EDIT (V063 created it for no role; V069 gave it to
    Finance Manager). The owner's decision: they keep editing the budget. So they get
    the code itself, which also shows them the screen's buttons (they follow BUDGET.EDIT).

    On the development database (read 27 Sep 2026) BUDGET.EDIT is held by Finance
    Manager, MINISTRY_ADMIN and SUPER_ADMIN; Admin has 4 active holders and Super Admin 3.
    No separation-of-duties pair names BUDGET.EDIT, so the roles stay assignable.

    Nothing is done unless BUDGET.EDIT exists. A role that is missing or inactive is
    skipped. Strictly idempotent: a switched-off grant is switched back on, a missing one
    is inserted (V044's columns), and a second run changes nothing.

    Rollback
    --------
        UPDATE rp SET rp.IsActive = 0
          FROM UMSCORE.RolePermissions rp
          JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
          JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
         WHERE r.RoleName IN (N'Admin', N'Super Admin') AND p.PermissionCode = N'BUDGET.EDIT';
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @budgetEdit BIGINT = (SELECT TOP 1 Id FROM UMSCORE.Permissions WHERE PermissionCode = N'BUDGET.EDIT');

IF @budgetEdit IS NULL
BEGIN
    PRINT 'V146: *** BUDGET.EDIT absent — nothing changed';
    RETURN;
END

DECLARE @roles TABLE (RoleId BIGINT PRIMARY KEY, RoleName NVARCHAR(100) NOT NULL);
INSERT INTO @roles (RoleId, RoleName)
SELECT Id, RoleName FROM UMSCORE.Roles WHERE RoleName IN (N'Admin', N'Super Admin') AND IsActive = 1;

DECLARE @found NVARCHAR(MAX);
SELECT @found = COALESCE(@found + N', ', N'') + RoleName FROM @roles ORDER BY RoleName;
PRINT CONCAT('V146: roles to receive BUDGET.EDIT: ', ISNULL(@found, N'none (neither role exists or is active)'));

BEGIN TRANSACTION;

UPDATE rp SET rp.IsActive = 1
  FROM UMSCORE.RolePermissions rp
  JOIN @roles r ON r.RoleId = rp.RoleId
 WHERE rp.PermissionId = @budgetEdit AND rp.IsActive = 0;
PRINT CONCAT('V146: BUDGET.EDIT grants reactivated: ', @@ROWCOUNT);

INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT r.RoleId, @budgetEdit, SYSUTCDATETIME(), 1
  FROM @roles r
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp
                    WHERE rp.RoleId = r.RoleId AND rp.PermissionId = @budgetEdit);
PRINT CONCAT('V146: BUDGET.EDIT granted: ', @@ROWCOUNT);

COMMIT TRANSACTION;
