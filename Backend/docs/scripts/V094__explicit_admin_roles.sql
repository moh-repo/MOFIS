/*
    V094 — Explicit administrator tiers as roles.

    Security plan R-12 (6 Sep 2026), the highest-impact item of the plan.

    Before
    ------
    Three flags on UMSCORE.Users (IsSuperAdmin, IsAdmin, IsMinistryUser) each
    turned into ROLE_ADMIN in the JWT filter, and every write expression
    accepted ROLE_ADMIN, so every administrator of every kind held every
    permission. Whether an IsAdmin account was "the ministry admin" or "a
    country admin" was inferred from how many attachés it happened to hold.

    After
    -----
    Four system roles carry the tiers (SUPER_ADMIN already existed):

        SUPER_ADMIN      everything; the audited break-glass account
        MINISTRY_ADMIN   every country; administers all staff below a super
                         admin; reads and approves Wafar, never records it
        MINISTRY_STAFF   reads every country; administers nobody but itself;
                         approves within its other roles; no Wafar writes
        COUNTRY_ADMIN    its own attachés' staff and data in full, Wafar included

    The application emits ROLE_<tier> from role membership, no expression
    accepts ROLE_ADMIN any more, and an administrator reaches an endpoint by
    holding the code — through the bundle seeded here plus whatever other
    roles they hold. The flags stay as columns; UmsUserService keeps them in
    step with the tier roles from now on, and this script sets them from the
    roles it assigns.

    Bundles (seeded from the live UMSCORE.Permissions, so a code added later
    is NOT in a bundle until granted — see PermissionCatalog):

        SUPER_ADMIN     every active code
        MINISTRY_ADMIN  every active code except
                          SYSTEM.RESTORE, SYS_BACKUP.RESTORE            (super admin only)
                          WAFAR.ADD/EDIT/DELETE/APPROVE, FIN_WAFAR.ADD/EDIT   (N-04)
                          ALLOCATION.CREATE, PAYMENT_BATCH.CREATE       (R-15: an admin is the checker, never the maker)
        COUNTRY_ADMIN   every active code except the above and the ministry-only
                        configuration: SYS_BACKUP.*, SYS_IMPORT.*, SYS_ADMIN.EDIT,
                        SETTINGS.EDIT, SYS_LOOKUPS.EDIT, ALERT_SCENARIOS.MANAGE,
                        NOTIFICATIONS.SETTINGS, BUDGET.EDIT, APP_CONFIG.EDIT,
                        COA.EDIT, COA.APPROVE, FISCAL_PERIOD.CLOSE,
                        UMS_ROLES.ADD/EDIT/DELETE, UMS_PERMISSIONS.EDIT, UMS_PAGES.*,
                        UMS_FIELDS.EDIT, UMS_API_LINKS.EDIT, UMS_REPORTS_ADMIN.ADD/EDIT
                        — but WITH the Wafar codes (a country owns its Wafar)
        MINISTRY_STAFF  every *.VIEW / *.SEARCH / *.EXPORT / *.PRINT code, plus
                        APP_PENDING.APPROVE, APP_PENDING.REJECT, APP_MY_REQUESTS.VIEW

    Assignment from the flags (only for accounts holding none of the three new
    roles; the super admin tier stays with the IsSuperAdmin flag, which is the
    break-glass column, and the SUPER_ADMIN role only carries the bundle):
        IsSuperAdmin = 1                                   -> SUPER_ADMIN role (bundle)
        IsAdmin = 1 and holds every active study country   -> MINISTRY_ADMIN (IsMinistryUser set to 1)
        IsAdmin = 1 otherwise                              -> COUNTRY_ADMIN
        IsMinistryUser = 1 without IsAdmin                 -> MINISTRY_STAFF
    Accounts that hold the SUPER_ADMIN role without the flag are listed: they
    hold every code but none of the audited bypasses; remove the role or set
    the flag, deliberately.

    Cutover check (R-13): the last report lists, per administrator, the codes
    they could reach through ROLE_ADMIN yesterday and cannot through their
    tier bundle and other roles today. Grant a role where one is needed. The
    effective-permission screen shows the same list per account.

    Rollback: deactivate the four UserRoles rows per account (or the three new
    roles); the flags are untouched by a rollback, and the previous application
    build reads only the flags. Re-running this script resets the three new
    bundles to the seed (an administrator's later edit to a tier bundle is
    replaced); it never removes a tier role from an account.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ---- 1. The roles ---------------------------------------------------------- */
DECLARE @roles TABLE (RoleName NVARCHAR(100), RoleNameAr NVARCHAR(100), Description NVARCHAR(500), Priority INT);
INSERT INTO @roles VALUES
  (N'SUPER_ADMIN',    N'مدير النظام الأعلى',  N'Tier role (R-12): everything, audited break-glass account.', 100),
  (N'MINISTRY_ADMIN', N'مدير الوزارة',        N'Tier role (R-12): every country; administers all staff below a super admin; reads and approves Wafar, never records it.', 95),
  (N'MINISTRY_STAFF', N'موظف الوزارة',        N'Tier role (R-12): reads every country; administers no staff but itself; no Wafar writes.', 60),
  (N'COUNTRY_ADMIN',  N'مدير الملحقية',       N'Tier role (R-12): full control of its own attachés, staff and Wafar included.', 90);

INSERT INTO UMSCORE.Roles (RoleName, RoleNameAr, Description, DescriptionAr, IsSystemRole, IsSystem, IsActive, Priority, CreatedAt)
SELECT r.RoleName, r.RoleNameAr, r.Description, r.Description, 1, 1, 1, r.Priority, SYSUTCDATETIME()
  FROM @roles r
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Roles x WHERE x.RoleName = r.RoleName);
PRINT CONCAT('V094: tier roles created: ', @@ROWCOUNT);

UPDATE x SET x.IsSystem = 1, x.IsSystemRole = 1, x.IsActive = 1
  FROM UMSCORE.Roles x JOIN @roles r ON r.RoleName = x.RoleName
 WHERE x.IsSystem = 0 OR x.IsSystemRole = 0 OR x.IsActive = 0;
GO

/* ---- 2. The bundles -------------------------------------------------------- */
IF OBJECT_ID('tempdb..#bundle') IS NOT NULL DROP TABLE #bundle;
CREATE TABLE #bundle (RoleName NVARCHAR(100), PermissionId BIGINT, PermissionCode NVARCHAR(100));

DECLARE @ministryOnly TABLE (Code NVARCHAR(100));
INSERT INTO @ministryOnly
SELECT PermissionCode FROM UMSCORE.Permissions
 WHERE PermissionCode LIKE 'SYS[_]BACKUP.%' OR PermissionCode LIKE 'SYS[_]IMPORT.%'
    OR PermissionCode IN ('SYS_ADMIN.EDIT','SETTINGS.EDIT','SYS_LOOKUPS.EDIT','ALERT_SCENARIOS.MANAGE',
                          'NOTIFICATIONS.SETTINGS','BUDGET.EDIT','APP_CONFIG.EDIT','COA.EDIT','COA.APPROVE',
                          'FISCAL_PERIOD.CLOSE','UMS_ROLES.ADD','UMS_ROLES.EDIT','UMS_ROLES.DELETE',
                          'UMS_PERMISSIONS.EDIT','UMS_FIELDS.EDIT','UMS_API_LINKS.EDIT',
                          'UMS_REPORTS_ADMIN.ADD','UMS_REPORTS_ADMIN.EDIT')
    OR PermissionCode LIKE 'UMS[_]PAGES.%';

DECLARE @superOnly TABLE (Code NVARCHAR(100));
INSERT INTO @superOnly VALUES ('SYSTEM.RESTORE'), ('SYS_BACKUP.RESTORE');

DECLARE @wafarWrites TABLE (Code NVARCHAR(100));
INSERT INTO @wafarWrites VALUES ('WAFAR.ADD'), ('WAFAR.EDIT'), ('WAFAR.DELETE'), ('WAFAR.APPROVE'), ('FIN_WAFAR.ADD'), ('FIN_WAFAR.EDIT');

DECLARE @makers TABLE (Code NVARCHAR(100));
INSERT INTO @makers VALUES ('ALLOCATION.CREATE'), ('PAYMENT_BATCH.CREATE');

INSERT INTO #bundle SELECT 'SUPER_ADMIN', p.Id, p.PermissionCode FROM UMSCORE.Permissions p WHERE p.IsActive = 1;

INSERT INTO #bundle
SELECT 'MINISTRY_ADMIN', p.Id, p.PermissionCode FROM UMSCORE.Permissions p
 WHERE p.IsActive = 1
   AND p.PermissionCode NOT IN (SELECT Code FROM @superOnly)
   AND p.PermissionCode NOT IN (SELECT Code FROM @wafarWrites)
   AND p.PermissionCode NOT IN (SELECT Code FROM @makers);

INSERT INTO #bundle
SELECT 'COUNTRY_ADMIN', p.Id, p.PermissionCode FROM UMSCORE.Permissions p
 WHERE p.IsActive = 1
   AND p.PermissionCode NOT IN (SELECT Code FROM @superOnly)
   AND p.PermissionCode NOT IN (SELECT Code FROM @ministryOnly)
   AND p.PermissionCode NOT IN (SELECT Code FROM @makers);

INSERT INTO #bundle
SELECT 'MINISTRY_STAFF', p.Id, p.PermissionCode FROM UMSCORE.Permissions p
 WHERE p.IsActive = 1
   AND (p.PermissionCode LIKE '%.VIEW' OR p.PermissionCode LIKE '%.SEARCH'
        OR p.PermissionCode LIKE '%.EXPORT' OR p.PermissionCode LIKE '%.PRINT'
        OR p.PermissionCode IN ('APP_PENDING.APPROVE','APP_PENDING.REJECT','APP_MY_REQUESTS.VIEW'));

-- Install: add the missing rows, deactivate rows outside the bundle (converges on re-run).
INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT r.Id, b.PermissionId, SYSUTCDATETIME(), 1
  FROM #bundle b JOIN UMSCORE.Roles r ON r.RoleName = b.RoleName
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp WHERE rp.RoleId = r.Id AND rp.PermissionId = b.PermissionId);
PRINT CONCAT('V094: bundle rows inserted: ', @@ROWCOUNT);

UPDATE rp SET rp.IsActive = 1
  FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
  JOIN #bundle b ON b.RoleName = r.RoleName AND b.PermissionId = rp.PermissionId
 WHERE rp.IsActive = 0;
PRINT CONCAT('V094: bundle rows reactivated: ', @@ROWCOUNT);

UPDATE rp SET rp.IsActive = 0
  FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
 WHERE r.RoleName IN ('MINISTRY_ADMIN','COUNTRY_ADMIN','MINISTRY_STAFF') AND rp.IsActive = 1
   AND NOT EXISTS (SELECT 1 FROM #bundle b WHERE b.RoleName = r.RoleName AND b.PermissionId = rp.PermissionId);
PRINT CONCAT('V094: rows outside the bundles deactivated: ', @@ROWCOUNT);

SELECT r.RoleName, COUNT(*) AS Codes
  FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Roles r ON r.Id = rp.RoleId
 WHERE rp.IsActive = 1 AND r.RoleName IN ('SUPER_ADMIN','MINISTRY_ADMIN','COUNTRY_ADMIN','MINISTRY_STAFF')
 GROUP BY r.RoleName ORDER BY 2 DESC;
GO

/* ---- 3. Assignment from the flags ----------------------------------------- */
IF OBJECT_ID('tempdb..#tier') IS NOT NULL DROP TABLE #tier;
SELECT u.Id AS UserId, u.Username,
       CASE WHEN u.IsSuperAdmin = 1 THEN 'SUPER_ADMIN'
            WHEN u.IsAdmin = 1 AND NOT EXISTS (
                     SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN c
                      WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.UserCountries uc
                                         WHERE uc.UserId = u.Id AND uc.IsActive = 1 AND uc.CountryId = c.STUDY_COUNTRY_IN_ID))
                 THEN 'MINISTRY_ADMIN'
            WHEN u.IsAdmin = 1 THEN 'COUNTRY_ADMIN'
            WHEN u.IsMinistryUser = 1 THEN 'MINISTRY_STAFF'
            ELSE NULL END AS Tier
  INTO #tier
  FROM UMSCORE.Users u
 WHERE u.IsDeleted = 0
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.UserRoles ur JOIN UMSCORE.Roles r ON r.Id = ur.RoleId
                    WHERE ur.UserId = u.Id AND ur.IsActive = 1
                      AND r.RoleName IN ('MINISTRY_ADMIN','COUNTRY_ADMIN','MINISTRY_STAFF'))
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.UserGroupMemberships ug JOIN UMSCORE.GroupRoles gr ON gr.GroupId = ug.GroupId AND gr.IsActive = 1
                    JOIN UMSCORE.Roles r ON r.Id = gr.RoleId
                    WHERE ug.UserId = u.Id AND ug.IsActive = 1
                      AND r.RoleName IN ('MINISTRY_ADMIN','COUNTRY_ADMIN','MINISTRY_STAFF'));

-- A super admin by flag needs no tier row beyond the bundle role.
UPDATE #tier SET Tier = 'SUPER_ADMIN' WHERE Tier = 'SUPER_ADMIN';

DELETE FROM #tier WHERE Tier IS NULL;

INSERT INTO UMSCORE.UserRoles (UserId, RoleId, AssignedAt, IsActive)
SELECT t.UserId, r.Id, SYSUTCDATETIME(), 1
  FROM #tier t JOIN UMSCORE.Roles r ON r.RoleName = t.Tier
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.UserRoles ur WHERE ur.UserId = t.UserId AND ur.RoleId = r.Id);
PRINT CONCAT('V094: tier roles assigned: ', @@ROWCOUNT);

UPDATE ur SET ur.IsActive = 1
  FROM UMSCORE.UserRoles ur JOIN UMSCORE.Roles r ON r.Id = ur.RoleId JOIN #tier t ON t.UserId = ur.UserId AND t.Tier = r.RoleName
 WHERE ur.IsActive = 0;

-- The flags follow the roles: a ministry admin is ministry-wide.
UPDATE u SET u.IsMinistryUser = 1 FROM UMSCORE.Users u JOIN #tier t ON t.UserId = u.Id WHERE t.Tier = 'MINISTRY_ADMIN' AND u.IsMinistryUser = 0;
PRINT CONCAT('V094: IsMinistryUser set on ministry admins: ', @@ROWCOUNT);

PRINT 'V094: tier per account:';
SELECT t.Username, t.Tier FROM #tier t ORDER BY t.Tier, t.Username;

PRINT 'V094: accounts holding the SUPER_ADMIN role without the IsSuperAdmin flag (every code, no bypasses; remove the role or set the flag):';
SELECT u.Username
  FROM UMSCORE.Users u
 WHERE u.IsDeleted = 0 AND u.IsSuperAdmin = 0
   AND EXISTS (SELECT 1 FROM UMSCORE.UserRoles ur JOIN UMSCORE.Roles r ON r.Id = ur.RoleId
                WHERE ur.UserId = u.Id AND ur.IsActive = 1 AND r.RoleName = 'SUPER_ADMIN')
 ORDER BY u.Username;
GO

/* ---- 4. Cutover report (R-13): what ROLE_ADMIN gave that the tier does not -- */
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
         WHERE up.IsActive = 1 AND (COL_LENGTH('UMSCORE.UserPermissions','ExpiresAt') IS NULL OR up.ExpiresAt IS NULL OR up.ExpiresAt > SYSUTCDATETIME())
       ) x
  JOIN UMSCORE.Permissions p ON p.Id = x.PermissionId AND p.IsActive = 1;

PRINT 'V094: administrators (not super admins) and the codes ROLE_ADMIN used to give them that their roles do not:';
SELECT u.Username, p.PermissionCode
  FROM UMSCORE.Users u
  CROSS JOIN UMSCORE.Permissions p
 WHERE u.IsDeleted = 0 AND u.IsSuperAdmin = 0 AND (u.IsAdmin = 1 OR u.IsMinistryUser = 1)
   AND p.IsActive = 1
   AND NOT EXISTS (SELECT 1 FROM #eff e WHERE e.UserId = u.Id AND e.PermissionCode = p.PermissionCode)
 ORDER BY u.Username, p.PermissionCode;
DROP TABLE #eff;
DROP TABLE #tier;
GO
