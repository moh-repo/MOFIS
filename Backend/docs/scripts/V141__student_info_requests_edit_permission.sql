/*
    V141 — STUDENT_INFO_REQUESTS.EDIT: the permission behind raising, cancelling
           and deciding information requests to students.

    Live QA H3 (25 Sep 2026). Every information-request endpoint was gated by the
    read rule (STUDENT_INFO_REQUESTS.VIEW or STUDENTS.VIEW or UMS_ADMIN.VIEW), so an
    account that could only view students raised requests. The writes now need
    STUDENT_INFO_REQUESTS.EDIT, and the code joins PermissionCatalog, whose startup
    guard refuses to start outside the dev profile while the code is missing.

    The code exists on the development database (id 10232, from an early hand seed
    that V122 renamed), held by STAFF, COUNTRY_ADMIN, MINISTRY_ADMIN, Admin and
    SUPER_ADMIN. No migration ever inserted it, so another database may lack it.
    This script only fills that gap (FIX-PLAN decision D18-c):

    1. The code is inserted when absent, or reactivated when inactive.
    2. Only when step 1 had to INSERT it, it is granted to every active role that
       holds both STUDENT_INFO_REQUESTS.VIEW and STUDENTS.EDIT — the rule that
       reproduces the development grants exactly. A database that already has the
       code keeps its own grants untouched.

    On the development database this changes nothing (the code exists and is
    active). Strictly idempotent: a second run finds nothing to do.

    Rollback (only on a database where step 1 inserted the code)
    --------
        DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
         WHERE p.PermissionCode = N'STUDENT_INFO_REQUESTS.EDIT';
        DELETE FROM UMSCORE.Permissions WHERE PermissionCode = N'STUDENT_INFO_REQUESTS.EDIT';
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @inserted INT = 0;

UPDATE UMSCORE.Permissions SET IsActive = 1, UpdatedAt = SYSUTCDATETIME()
 WHERE PermissionCode = N'STUDENT_INFO_REQUESTS.EDIT' AND IsActive = 0;
PRINT CONCAT('V141: reactivated: ', @@ROWCOUNT);

INSERT INTO UMSCORE.Permissions
       (PermissionCode, PermissionName, PermissionNameAr, ResourceType, ResourceId, ActionId,
        ActionType, PermissionLevel, Description, IsActive, CreatedAt)
SELECT N'STUDENT_INFO_REQUESTS.EDIT', N'Manage information requests to students',
       N'إدارة طلبات المعلومات من الطلاب', N'PAGE',
       (SELECT TOP 1 s.ResourceId FROM UMSCORE.Permissions s
         WHERE s.PermissionCode = N'STUDENT_INFO_REQUESTS.VIEW' AND s.ResourceId IS NOT NULL),
       NULL, N'EDIT', N'WRITE', N'Raise, cancel and decide information requests to students', 1, SYSUTCDATETIME()
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions p WHERE p.PermissionCode = N'STUDENT_INFO_REQUESTS.EDIT');
SET @inserted = @@ROWCOUNT;
PRINT CONCAT('V141: inserted: ', @inserted);

IF @inserted = 1
BEGIN
    INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
    SELECT DISTINCT r.Id, np.Id, SYSUTCDATETIME(), 1
      FROM UMSCORE.Roles r
     CROSS JOIN UMSCORE.Permissions np
     WHERE np.PermissionCode = N'STUDENT_INFO_REQUESTS.EDIT'
       AND r.IsActive = 1
       AND EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
                    WHERE rp.RoleId = r.Id AND rp.IsActive = 1 AND p.PermissionCode = N'STUDENT_INFO_REQUESTS.VIEW')
       AND EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
                    WHERE rp.RoleId = r.Id AND rp.IsActive = 1 AND p.PermissionCode = N'STUDENTS.EDIT')
       AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x WHERE x.RoleId = r.Id AND x.PermissionId = np.Id);
    PRINT CONCAT('V141: role grants inserted: ', @@ROWCOUNT);
END
GO
