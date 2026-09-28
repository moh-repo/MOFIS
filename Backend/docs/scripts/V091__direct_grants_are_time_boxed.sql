/*
    V091 — Direct permission grants are time-boxed exceptions.

    Security plan R-14 (6 Sep 2026). A permission granted to one account
    directly (UMSCORE.UserPermissions) bypasses the role model, and until now
    nothing recorded why or for how long. The table gains:

        Reason     NVARCHAR(500) NULL   why the exception was made
        ExpiresAt  DATETIME2     NULL   when it lapses

    The application (UmsUserService.grantPermissionToUser) requires both on
    every new grant, with ExpiresAt at most ums.direct-grant.max-days (90)
    ahead; every permission read ignores a lapsed row; the nightly review
    (DirectGrantReviewJob) deactivates lapsed rows and logs the rest.

    The one exception is configuration rather than privilege: the per-user
    dashboard sections of V071 (DASHBOARD_<SECTION>.VIEW), which every account
    starts with and administrators revoke individually. Those rows keep no
    expiry, here and in the application.

    Existing grants
    ---------------
    Every other active direct grant gets an expiry 90 days from now and a
    reason saying so, and is listed at the end: an administrator decides which
    to renew (through the permission screen, with a real reason) and which to
    let lapse. Nothing is revoked by this script.

    Rollback
    --------
        ALTER TABLE UMSCORE.UserPermissions DROP COLUMN Reason, ExpiresAt;
    (the application build that reads them must be rolled back first).

    Idempotent: columns are added only if absent; the expiry is set only where
    it is NULL, so a re-run changes nothing.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF COL_LENGTH('UMSCORE.UserPermissions', 'Reason') IS NULL
    ALTER TABLE UMSCORE.UserPermissions ADD Reason NVARCHAR(500) NULL;
IF COL_LENGTH('UMSCORE.UserPermissions', 'ExpiresAt') IS NULL
    ALTER TABLE UMSCORE.UserPermissions ADD ExpiresAt DATETIME2 NULL;
GO

UPDATE up
   SET up.ExpiresAt = DATEADD(DAY, 90, SYSUTCDATETIME()),
       up.Reason    = COALESCE(up.Reason, N'Pre-existing direct grant; 90-day expiry set by V091, renew if still needed')
  FROM UMSCORE.UserPermissions up
  JOIN UMSCORE.Permissions p ON p.Id = up.PermissionId
 WHERE up.IsActive = 1
   AND up.ExpiresAt IS NULL
   AND NOT (p.PermissionCode LIKE 'DASHBOARD[_]%.VIEW');
PRINT CONCAT('V091: existing exceptions given a 90-day expiry: ', @@ROWCOUNT);

PRINT 'V091: live exceptions (renew or let lapse):';
SELECT u.Username, p.PermissionCode, CONVERT(VARCHAR(10), up.ExpiresAt, 120) AS ExpiresOn, up.Reason
  FROM UMSCORE.UserPermissions up
  JOIN UMSCORE.Permissions p ON p.Id = up.PermissionId
  JOIN UMSCORE.Users u ON u.Id = up.UserId
 WHERE up.IsActive = 1 AND up.ExpiresAt IS NOT NULL AND u.IsDeleted = 0
 ORDER BY u.Username, p.PermissionCode;
GO
