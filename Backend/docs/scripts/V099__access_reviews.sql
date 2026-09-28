/*
    V099 - Quarterly access review (security plan S-04).

    What
    ----
    1. UMSCORE.AccessReviews: one review per period and attaché (CountryId NULL
       is the ministry's own staff), with a due date and a status.
    2. UMSCORE.AccessReviewItems: one row per account in the review, with a
       snapshot of what the account held when the review opened (roles,
       countries, position, last sign-in, direct grants) and the attestation
       (KEEP / CHANGE / REMOVE, by whom, when, note).
    3. Permission codes ACCESS_REVIEW.VIEW / ACCESS_REVIEW.EDIT, granted to
       every role that already holds UMS_ADMIN.EDIT: whoever administers staff
       also attests them. The country admin attests their own attaché's list,
       the ministry admin the ministry's (AccessReviewService).

    Effect
    ------
    None until the build of 6 Sep 2026 is deployed. The first reviews open on
    the next quarter start (AccessReviewJob) or when a ministry admin opens
    them from the screen.

    Rollback
    --------
    DROP TABLE UMSCORE.AccessReviewItems; DROP TABLE UMSCORE.AccessReviews;
    DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId WHERE p.PermissionCode LIKE 'ACCESS_REVIEW.%';
    DELETE FROM UMSCORE.Permissions WHERE PermissionCode LIKE 'ACCESS_REVIEW.%';
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'UMSCORE.AccessReviews', N'U') IS NULL
BEGIN
    CREATE TABLE UMSCORE.AccessReviews (
        Id        BIGINT IDENTITY(1,1) NOT NULL,
        Period    NVARCHAR(20)  NOT NULL,
        CountryId INT           NULL,
        Status    NVARCHAR(20)  NOT NULL CONSTRAINT DF_AccessReviews_Status DEFAULT (N'OPEN'),
        OpenedAt  DATETIME2     NOT NULL CONSTRAINT DF_AccessReviews_OpenedAt DEFAULT (SYSDATETIME()),
        OpenedBy  BIGINT        NULL,
        DueAt     DATETIME2     NOT NULL,
        ClosedAt  DATETIME2     NULL,
        ClosedBy  BIGINT        NULL,
        CONSTRAINT PK_AccessReviews PRIMARY KEY (Id),
        CONSTRAINT UQ_AccessReviews_Period_Country UNIQUE (Period, CountryId)
    );
    PRINT N'V099: UMSCORE.AccessReviews created.';
END
ELSE
    PRINT N'V099: UMSCORE.AccessReviews already exists.';

IF OBJECT_ID(N'UMSCORE.AccessReviewItems', N'U') IS NULL
BEGIN
    CREATE TABLE UMSCORE.AccessReviewItems (
        Id              BIGINT IDENTITY(1,1) NOT NULL,
        ReviewId        BIGINT        NOT NULL,
        UserId          BIGINT        NOT NULL,
        Snapshot        NVARCHAR(MAX) NOT NULL,
        Decision        NVARCHAR(10)  NULL,
        DecidedBy       BIGINT        NULL,
        DecidedAt       DATETIME2     NULL,
        Note            NVARCHAR(500) NULL,
        OverdueFlaggedAt DATETIME2    NULL,
        CONSTRAINT PK_AccessReviewItems PRIMARY KEY (Id),
        CONSTRAINT FK_AccessReviewItems_Review FOREIGN KEY (ReviewId) REFERENCES UMSCORE.AccessReviews (Id) ON DELETE CASCADE,
        CONSTRAINT FK_AccessReviewItems_User FOREIGN KEY (UserId) REFERENCES UMSCORE.Users (Id),
        CONSTRAINT UQ_AccessReviewItems UNIQUE (ReviewId, UserId)
    );
    PRINT N'V099: UMSCORE.AccessReviewItems created.';
END
ELSE
    PRINT N'V099: UMSCORE.AccessReviewItems already exists.';
GO

DECLARE @codes TABLE (Code NVARCHAR(100), Name NVARCHAR(200), NameAr NVARCHAR(200),
                      ResourceType NVARCHAR(50), ActionType NVARCHAR(50), PermissionLevel NVARCHAR(20), Description NVARCHAR(500));
INSERT INTO @codes VALUES
  (N'ACCESS_REVIEW.VIEW', N'View Access Reviews',   N'عرض مراجعات الصلاحيات',  N'PAGE', N'VIEW', N'READ',  N'See the quarterly access reviews of the attaches the caller administers'),
  (N'ACCESS_REVIEW.EDIT', N'Attest Access Reviews', N'اعتماد مراجعات الصلاحيات', N'PAGE', N'EDIT', N'WRITE', N'Attest each account of a review: keep, change or remove');
UPDATE p SET p.IsActive = 1, p.UpdatedAt = SYSUTCDATETIME()
  FROM UMSCORE.Permissions p JOIN @codes c ON c.Code = p.PermissionCode
 WHERE p.IsActive = 0;
INSERT INTO UMSCORE.Permissions
       (PermissionCode, PermissionName, PermissionNameAr, ResourceType, ResourceId, ActionId,
        ActionType, PermissionLevel, Description, IsActive, CreatedAt)
SELECT c.Code, c.Name, c.NameAr, c.ResourceType,
       (SELECT TOP 1 s.ResourceId FROM UMSCORE.Permissions s WHERE s.PermissionCode LIKE 'UMS_ADMIN.%' AND s.ResourceId IS NOT NULL),
       NULL, c.ActionType, c.PermissionLevel, c.Description, 1, SYSUTCDATETIME()
  FROM @codes c
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions p WHERE p.PermissionCode = c.Code);
PRINT CONCAT('V099: permission codes inserted: ', @@ROWCOUNT);

-- Whoever administers staff also attests them.
INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT DISTINCT rp.RoleId, np.Id, SYSUTCDATETIME(), 1
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions op ON op.Id = rp.PermissionId AND op.PermissionCode = N'UMS_ADMIN.EDIT'
  CROSS JOIN UMSCORE.Permissions np
 WHERE np.PermissionCode IN (N'ACCESS_REVIEW.VIEW', N'ACCESS_REVIEW.EDIT')
   AND rp.IsActive = 1
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x WHERE x.RoleId = rp.RoleId AND x.PermissionId = np.Id);
PRINT CONCAT('V099: role grants inserted: ', @@ROWCOUNT);
GO
