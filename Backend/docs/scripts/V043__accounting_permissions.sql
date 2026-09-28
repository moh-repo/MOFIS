/*
    V043 — UMS permissions for the accounting module.

    Why
    ---
    Without these rows the feature is invisible. The navigation menu is
    fail-closed: AppMenuComponent.filterMenuItemByPermission hides any item whose
    page code the current user cannot view, and hides items with no mapping at
    all. So a chart-of-accounts screen with no ACCOUNTING permission behind it
    simply never appears, and the only symptom is a menu that looks unchanged.

    The API would still be reachable by an administrator, because every
    expression in MoheSecurityExpressions falls through to UMS_ADMIN.VIEW /
    ROLE_ADMIN. That is a poor way to ship a feature: it works for exactly the
    people who least need it to.

    What is granted, and what is not
    --------------------------------
    Codes follow the existing convention (PermissionCode 'X.VIEW', ResourceType
    'PAGE'), matching WAFAR.* which was the template.

        ACCOUNTING.VIEW      read the chart, the calendar, the balances
        ACCOUNTING.EDIT      general accounting maintenance
        COA.VIEW / COA.EDIT  the chart of accounts specifically
        COA.APPROVE          approve a change to the chart
        ALLOCATION.CREATE    propose a quarterly or additional allocation
        ALLOCATION.APPROVE   approve one, and mark it transferred
        FISCAL_PERIOD.CLOSE  close a period or a year -- one-way

    ALLOCATION.CREATE and ALLOCATION.APPROVE are separate on purpose: the officer
    who proposes a transfer should not be the one who releases it. Whether they
    are in fact held by different people is a matter of role assignment, but the
    system has to make the split expressible.

    This script creates the permissions. It does NOT assign them to any role --
    who may edit the chart of accounts or release a transfer is a decision about
    your organisation, not one a migration should make. Assign them through UMS,
    or with the commented statement at the end.

    Rollback
    --------
        DELETE FROM UMSCORE.RolePermissions
        WHERE PermissionId IN (SELECT Id FROM UMSCORE.Permissions
                               WHERE PermissionCode IN (...the codes below...));
        DELETE FROM UMSCORE.Permissions WHERE PermissionCode IN (...the codes below...);
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

DECLARE @perms TABLE (
    code        varchar(50)   NOT NULL,
    name_en     nvarchar(200) NOT NULL,
    name_ar     nvarchar(200) NOT NULL,
    action_type varchar(20)   NOT NULL
);

INSERT INTO @perms (code, name_en, name_ar, action_type) VALUES
    ('ACCOUNTING.VIEW',     N'View Accounting',              N'عرض المحاسبة',              'VIEW'),
    ('ACCOUNTING.EDIT',     N'Edit Accounting',              N'تعديل المحاسبة',            'EDIT'),
    ('COA.VIEW',            N'View Chart of Accounts',       N'عرض الدليل المحاسبي',       'VIEW'),
    ('COA.EDIT',            N'Edit Chart of Accounts',       N'تعديل الدليل المحاسبي',     'EDIT'),
    ('COA.APPROVE',         N'Approve Chart Changes',        N'اعتماد تعديلات الدليل',     'APPROVE'),
    ('ALLOCATION.CREATE',   N'Create Budget Allocation',     N'إنشاء تخصيص موازنة',        'ADD'),
    ('ALLOCATION.APPROVE',  N'Approve Budget Allocation',    N'اعتماد تخصيص موازنة',       'APPROVE'),
    ('FISCAL_PERIOD.CLOSE', N'Close Fiscal Period',          N'إقفال الفترة المالية',      'EDIT');

INSERT INTO UMSCORE.Permissions
    (PermissionCode, PermissionName, PermissionNameAr, Description,
     ResourceType, ActionType, PermissionLevel, IsActive, CreatedAt)
SELECT p.code, p.name_en, p.name_ar, p.name_en,
       'PAGE', p.action_type, 'READ', 1, SYSUTCDATETIME()
FROM @perms p
WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions e WHERE e.PermissionCode = p.code);

PRINT 'V043: created ' + CAST(@@ROWCOUNT AS varchar(10)) + ' accounting permissions.';
GO

/* ------------------------------------------------------------------ *
 * Verification
 * ------------------------------------------------------------------ */
PRINT '';
PRINT '================ V043 verification ================';

SELECT PermissionCode, PermissionName, ActionType, IsActive
FROM UMSCORE.Permissions
WHERE PermissionCode IN ('ACCOUNTING.VIEW','ACCOUNTING.EDIT','COA.VIEW','COA.EDIT',
                         'COA.APPROVE','ALLOCATION.CREATE','ALLOCATION.APPROVE','FISCAL_PERIOD.CLOSE')
ORDER BY PermissionCode;

PRINT '--- eight rows expected, all IsActive = 1 ---';
PRINT '';
PRINT '--- roles currently holding any of them (none, until you assign) ---';
SELECT r.RoleName, p.PermissionCode
FROM UMSCORE.RolePermissions rp
JOIN UMSCORE.Roles r       ON r.Id = rp.RoleId
JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
WHERE p.PermissionCode LIKE 'ACCOUNTING%' OR p.PermissionCode LIKE 'COA.%'
   OR p.PermissionCode LIKE 'ALLOCATION.%' OR p.PermissionCode = 'FISCAL_PERIOD.CLOSE'
ORDER BY r.RoleName, p.PermissionCode;

PRINT '';
PRINT '--- To grant the read-only pages to a role, edit and run:                    ---';
PRINT '---   INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive) ---';
PRINT '---   SELECT <RoleId>, Id, SYSUTCDATETIME(), 1 FROM UMSCORE.Permissions      ---';
PRINT '---   WHERE PermissionCode IN (''ACCOUNTING.VIEW'', ''COA.VIEW'');           ---';
PRINT '--- Grant COA.EDIT and the ALLOCATION.* pair deliberately and separately.    ---';
PRINT '=============== end V043 verification =============';
GO
