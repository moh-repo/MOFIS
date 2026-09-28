/*
    V119 — the approval levels that nobody but a super admin could sign.

    Ten active rows in ALERTCORE.ApprovalLevels required RoleId 13 ("Manager
    Approval", level 1) or 15 ("Director Approval", level 2). Neither id exists in
    UMSCORE.Roles — the live ids are 1-10 and 10008-10015 — so
    ApprovalRequestPolicy resolved the role name to NULL and denied every
    non-super-admin:

        String roleName = roleRepository.findById(requiredRoleId)...orElse(null);
        if (roleName == null || !subject.hasRole(roleName)) {
            if (!superAdmin) return Decision.deny("User does not have the required role...");

    Those entity types write nothing until the chain completes, so create, update
    and delete on Student Loan, Books Allowance, Student Extension and
    Fin-Received-Whom could only be forced through by a super admin using the
    audited ROLE_BYPASS. DEFAULT is the fallback for any entity type without its
    own config, so anything new inherited the same dead end.

    This was already visible. V093 PRINTs the very same list — "active levels
    whose RequiredRoleId matches no active role (nobody but the super admin can
    approve them)" — and V066 had to skip FK_ApprovalLevels_RequiredRole because
    of the orphans it found. The detection existed; the fix did not, and the count
    grew from 4 rows to 10. Hence section 3: a print is not a check, so this
    script leaves a foreign key behind to make the state unrepresentable.

    What
    ----
      1. Repoint the orphans onto the tier roles V094 created:
             level 1 -> COUNTRY_ADMIN    (the attaché's own administrator)
             level 2 -> MINISTRY_ADMIN   (the ministry above them)
         This is the escalation the working configs already use (STUDENT_FINANCE,
         STUDENT_DATA), and it matches the intent of the old names: the attaché
         signs first, the ministry countersigns. Owner's decision, 20 Sep 2026.
         Roles are resolved by NAME, not by the literal ids, so an environment
         that seeded UMSCORE.Roles in a different order still lands correctly.
      2. Only rows whose RequiredRoleId matches no role at all are touched, so a
         level an administrator has since repointed by hand is left alone and a
         re-run changes nothing.
      3. Add FK_ApprovalLevels_RequiredRole, the constraint V066 listed and could
         not create.

    Levels affected on the development database (ids): 8, 9, 12, 13, 29, 30, 31,
    32, 33, 34 — two each for DEFAULT, STUDENT_BOOKS_ALLOWANCE, STUDENT_EXTENSION,
    STUDENT_LOAN and STUDY_FIN_RECEIVED_WHOM.

    Who can approve afterwards
    --------------------------
    A COUNTRY_ADMIN signs level 1 for their own attaché and a MINISTRY_ADMIN signs
    level 2. A super admin may still sign either, as before, and the bypass is
    still audited. Nobody loses a right they were exercising: before this script
    nobody but a super admin could sign these levels at all.

    Rollback
    --------
        ALTER TABLE ALERTCORE.ApprovalLevels DROP CONSTRAINT FK_ApprovalLevels_RequiredRole;
        -- and, if the old values are genuinely wanted back:
        UPDATE ALERTCORE.ApprovalLevels SET RequiredRoleId = 13 WHERE Id IN (8, 12, 29, 31, 33);
        UPDATE ALERTCORE.ApprovalLevels SET RequiredRoleId = 15 WHERE Id IN (9, 13, 30, 32, 34);
    That restores the defect; it is here only for completeness.

    Idempotent. THROWs if an active level still names a missing role afterwards.
    Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

/* ---- 1. The two tier roles this repoints onto ------------------------------------ */

DECLARE @countryAdmin BIGINT = (SELECT Id FROM UMSCORE.Roles WHERE RoleName = N'COUNTRY_ADMIN');
DECLARE @ministryAdmin BIGINT = (SELECT Id FROM UMSCORE.Roles WHERE RoleName = N'MINISTRY_ADMIN');

IF @countryAdmin IS NULL OR @ministryAdmin IS NULL
    THROW 50119, 'V119: COUNTRY_ADMIN / MINISTRY_ADMIN not found in UMSCORE.Roles. Apply V094 first.', 1;

/* ---- 2. Repoint only the levels whose role does not exist ------------------------ */

DECLARE @before INT = (
    SELECT COUNT(*) FROM ALERTCORE.ApprovalLevels l
     WHERE l.RequiredRoleId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM UMSCORE.Roles r WHERE r.Id = l.RequiredRoleId));

/*
   LevelOrder decides the target, not the old id: a config could have ordered its
   levels differently. Level 1 is the attaché, every level above it the ministry —
   the same shape STUDENT_FINANCE uses for its four levels.
*/
UPDATE l
   SET l.RequiredRoleId = CASE WHEN l.LevelOrder <= 1 THEN @countryAdmin ELSE @ministryAdmin END
  FROM ALERTCORE.ApprovalLevels l
 WHERE l.RequiredRoleId IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.Roles r WHERE r.Id = l.RequiredRoleId);

PRINT CONCAT('V119: repointed ', @@ROWCOUNT, ' approval level(s) of ', @before, ' orphaned');
GO

/* ---- 3. The foreign key V066 had to skip ----------------------------------------- */

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_ApprovalLevels_RequiredRole')
BEGIN
    IF EXISTS (SELECT 1 FROM ALERTCORE.ApprovalLevels l
                WHERE l.RequiredRoleId IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM UMSCORE.Roles r WHERE r.Id = l.RequiredRoleId))
        THROW 50119, 'V119: an orphan RequiredRoleId remains; the foreign key cannot be created.', 1;

    ALTER TABLE ALERTCORE.ApprovalLevels WITH CHECK
        ADD CONSTRAINT FK_ApprovalLevels_RequiredRole
        FOREIGN KEY (RequiredRoleId) REFERENCES UMSCORE.Roles (Id);
    PRINT 'V119: FK_ApprovalLevels_RequiredRole created';
END
ELSE
    PRINT 'V119: FK_ApprovalLevels_RequiredRole already present';
GO

/* ---- 4. Verify ------------------------------------------------------------------- */

IF EXISTS (SELECT 1 FROM ALERTCORE.ApprovalLevels l
            WHERE l.IsActive = 1 AND l.RequiredRoleId IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM UMSCORE.Roles r
                               WHERE r.Id = l.RequiredRoleId AND r.IsActive = 1))
    THROW 50119, 'V119: an active level still requires a missing or inactive role.', 1;

SELECT c.EntityType,
       l.LevelOrder,
       l.LevelName,
       l.RequiredRoleId,
       r.RoleName
  FROM ALERTCORE.ApprovalLevels l
  JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
  LEFT JOIN UMSCORE.Roles r ON r.Id = l.RequiredRoleId
 WHERE c.EntityType IN (N'DEFAULT', N'STUDENT_BOOKS_ALLOWANCE', N'STUDENT_EXTENSION',
                        N'STUDENT_LOAN', N'STUDY_FIN_RECEIVED_WHOM')
 ORDER BY c.EntityType, l.LevelOrder;
