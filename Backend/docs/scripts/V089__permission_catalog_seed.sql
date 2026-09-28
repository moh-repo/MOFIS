/*
    V089 — Seed the permission codes the application enforces but the table lacked.

    Security plan R-11 (6 Sep 2026). config/PermissionCatalog.java lists every
    code a @PreAuthorize expression names; PermissionCatalogStartupGuard refuses
    to start outside the dev profile when one is missing here. On 6 Sep 2026 the
    comparison found six:

      MOHE_QUARTER.VIEW / ADD / EDIT / CLOSE / DELETE — enforced by the financial
          quarter endpoints since the quarter module was added; existed in no
          database, so only ROLE_ADMIN holders could ever use them.
      WAFAR.APPROVE — accepted by the WAFAR_APPROVE expression alongside
          WAFAR.EDIT; never seeded, so approval has always ridden on EDIT.

    Grants
    ------
    The codes are inserted; no role receives them here. Administrators pass by
    ROLE_ADMIN today and keep doing so until R-12 replaces the flags with
    roles, at which point the role bundles are seeded from the catalogue. A
    country finance role that should open and close quarters gets the codes
    through the permission screen, deliberately, by someone who knows which.

    Idempotent: each code is inserted only if absent; an inactive row is
    reactivated.
*/
SET NOCOUNT ON;

DECLARE @codes TABLE (
    Code NVARCHAR(100), Name NVARCHAR(200), NameAr NVARCHAR(200),
    ResourceType NVARCHAR(50), ActionType NVARCHAR(50), PermissionLevel NVARCHAR(20), Description NVARCHAR(500)
);
INSERT INTO @codes VALUES
  (N'MOHE_QUARTER.VIEW',   N'View Financial Quarters',  N'عرض الأرباع المالية',      N'PAGE', N'VIEW',   N'READ',  N'View financial quarters'),
  (N'MOHE_QUARTER.ADD',    N'Open Financial Quarter',   N'فتح ربع مالي',            N'PAGE', N'ADD',    N'WRITE', N'Open a financial quarter'),
  (N'MOHE_QUARTER.EDIT',   N'Edit Financial Quarter',   N'تعديل ربع مالي',          N'PAGE', N'EDIT',   N'WRITE', N'Edit a financial quarter'),
  (N'MOHE_QUARTER.CLOSE',  N'Close Financial Quarter',  N'إقفال ربع مالي',          N'PAGE', N'CLOSE',  N'WRITE', N'Close a financial quarter'),
  (N'MOHE_QUARTER.DELETE', N'Delete Financial Quarter', N'حذف ربع مالي',            N'PAGE', N'DELETE', N'WRITE', N'Delete a financial quarter'),
  (N'WAFAR.APPROVE',       N'Approve Wafar Spending',   N'اعتماد صرف الوفر',        N'PAGE', N'APPROVE',N'WRITE', N'Approve Wafar spending — attaché staff only; super admin use is audited');

-- Reactivate any that exist but are inactive.
UPDATE p SET p.IsActive = 1, p.UpdatedAt = SYSUTCDATETIME()
  FROM UMSCORE.Permissions p JOIN @codes c ON c.Code = p.PermissionCode
 WHERE p.IsActive = 0;
PRINT CONCAT('reactivated: ', @@ROWCOUNT);

-- Insert the missing ones. ResourceId follows the sibling code on the same page
-- when one exists (WAFAR.* share a page), otherwise NULL.
INSERT INTO UMSCORE.Permissions
       (PermissionCode, PermissionName, PermissionNameAr, ResourceType, ResourceId, ActionId,
        ActionType, PermissionLevel, Description, IsActive, CreatedAt)
SELECT c.Code, c.Name, c.NameAr, c.ResourceType,
       (SELECT TOP 1 s.ResourceId FROM UMSCORE.Permissions s
         WHERE LEFT(s.PermissionCode, CHARINDEX('.', s.PermissionCode)) = LEFT(c.Code, CHARINDEX('.', c.Code))
           AND s.ResourceId IS NOT NULL),
       NULL, c.ActionType, c.PermissionLevel, c.Description, 1, SYSUTCDATETIME()
  FROM @codes c
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions p WHERE p.PermissionCode = c.Code);
PRINT CONCAT('inserted: ', @@ROWCOUNT);

-- Verify: every catalogue code above is now active.
SELECT c.Code, CASE WHEN p.Id IS NULL THEN 'MISSING' WHEN p.IsActive = 0 THEN 'INACTIVE' ELSE 'ok' END AS state
  FROM @codes c LEFT JOIN UMSCORE.Permissions p ON p.PermissionCode = c.Code
 ORDER BY c.Code;
