/*
    V063 — UMS permissions for the round-2 accounting screens: payment
           batches and budget appropriations.

    Why
    ---
    The allocation screen reuses ALLOCATION.CREATE / ALLOCATION.APPROVE from
    V043, and the FX-rate screen is reference data of the same controlled kind
    as the chart, so it reuses COA.EDIT. Two things have no code of their own:

        PAYMENT_BATCH.CREATE   prepare a batch (الدفعة): pick the students and
                               the amounts
        PAYMENT_BATCH.APPROVE  approve it, mark it paid, or cancel it
        BUDGET.EDIT            enter or revise the annual appropriation from MOF

    PAYMENT_BATCH.CREATE and PAYMENT_BATCH.APPROVE are separate on purpose, as
    the allocation pair is: the officer who prepares the batch should not be
    the one who releases the money. PaymentBatchService.approve also refuses
    the preparer outright, so holding both is not enough on its own.

    BUDGET.EDIT is its own code rather than a reuse of COA.EDIT because the
    chart is structure and the appropriation is money — the figure the whole
    fund chain draws down. Whoever maintains account codes is not necessarily
    the person who may record what the ministry was granted this year.

    This script creates the permissions and, like V043, assigns them to NO
    role. Administrators reach the endpoints regardless through ROLE_ADMIN;
    the menu is already visible to them through ACCOUNTING.VIEW (V044). Who
    else may prepare or release a payment is an organisational decision.

    Idempotent: a code that already exists is left alone.

    Rollback
    --------
        DELETE FROM UMSCORE.RolePermissions
        WHERE PermissionId IN (SELECT Id FROM UMSCORE.Permissions
                               WHERE PermissionCode IN ('PAYMENT_BATCH.CREATE',
                                     'PAYMENT_BATCH.APPROVE','BUDGET.EDIT'));
        DELETE FROM UMSCORE.Permissions
        WHERE PermissionCode IN ('PAYMENT_BATCH.CREATE','PAYMENT_BATCH.APPROVE','BUDGET.EDIT');
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
    ('PAYMENT_BATCH.CREATE',  N'Prepare Payment Batch',      N'إعداد دفعة صرف',        'ADD'),
    ('PAYMENT_BATCH.APPROVE', N'Approve Payment Batch',      N'اعتماد دفعة صرف',       'APPROVE'),
    ('BUDGET.EDIT',           N'Edit Budget Appropriation',  N'تعديل اعتماد الموازنة', 'EDIT');

INSERT INTO UMSCORE.Permissions
    (PermissionCode, PermissionName, PermissionNameAr, Description,
     ResourceType, ActionType, PermissionLevel, IsActive, CreatedAt)
SELECT p.code, p.name_en, p.name_ar, p.name_en,
       'PAGE', p.action_type, 'READ', 1, SYSUTCDATETIME()
FROM @perms p
WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions e WHERE e.PermissionCode = p.code);

PRINT 'V063: created ' + CAST(@@ROWCOUNT AS varchar(10)) + ' accounting permissions (0 means already present).';
GO

/* ------------------------------------------------------------------ *
 * Verification: all three present, granted to nobody.
 * ------------------------------------------------------------------ */
SELECT p.PermissionCode, p.IsActive,
       (SELECT COUNT(*) FROM UMSCORE.RolePermissions rp WHERE rp.PermissionId = p.Id) AS roles_holding
FROM UMSCORE.Permissions p
WHERE p.PermissionCode IN ('PAYMENT_BATCH.CREATE', 'PAYMENT_BATCH.APPROVE', 'BUDGET.EDIT')
ORDER BY p.PermissionCode;
GO

/* To grant them, through UMS or by hand — NOT run here:
INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT r.Id, p.Id, SYSUTCDATETIME(), 1
FROM UMSCORE.Roles r CROSS JOIN UMSCORE.Permissions p
WHERE r.RoleName IN ('Super Admin', 'Admin')
  AND p.PermissionCode IN ('PAYMENT_BATCH.CREATE', 'PAYMENT_BATCH.APPROVE', 'BUDGET.EDIT')
  AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x WHERE x.RoleId = r.Id AND x.PermissionId = p.Id);
*/
