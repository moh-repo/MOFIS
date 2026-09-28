/* ============================================================================
   Ensure the Finance recipients screen is gated by an approval workflow
   ----------------------------------------------------------------------------
   Tables : ALERTCORE.ApprovalConfigs
            ALERTCORE.ApprovalLevels

   Why
     Every add / update / delete on /finance-received-whom is submitted for approval
     (StudyFinReceivedWhomController -> ApprovalIntegrationService). The workflow needs an
     ApprovalConfig row whose EntityType = 'STUDY_FIN_RECEIVED_WHOM', or the request
     fails with "No approval config found for entity type: STUDY_FIN_RECEIVED_WHOM".

     This config governs the recipient record only. Documents attached to a
     recipient are uploaded and removed immediately, as before.

   What this does
     If no STUDY_FIN_RECEIVED_WHOM config exists yet, it CLONES an existing, working
     config - preferring DEFAULT (two levels: Manager, then Director), then
     STUDENT_FINANCE, then STUDENT_DATA, then any active config - so the new
     config reuses the same levels and RequiredRoleId values that already work
     in your environment. If there is no config to clone, it creates a minimal
     single level ("Approval") with no required role.

   Safe to re-run: it only creates the config when it is missing, and never
   touches an existing STUDY_FIN_RECEIVED_WHOM config.

   Open in SSMS as UTF-8 so the Arabic text reads correctly, and make sure the
   query window is on the correct database.
   ============================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRAN;

IF NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalConfigs WHERE EntityType = 'STUDY_FIN_RECEIVED_WHOM')
BEGIN
    /* Pick a template config to clone, closest match first. Two levels was the
       depth chosen for these finance screens, which is what DEFAULT carries. */
    DECLARE @srcId BIGINT =
    (
        SELECT TOP (1) Id
          FROM ALERTCORE.ApprovalConfigs
         WHERE IsActive = 1
           AND EntityType IN ('DEFAULT', 'STUDENT_FINANCE', 'STUDENT_DATA')
         ORDER BY CASE EntityType
                      WHEN 'DEFAULT'         THEN 0
                      WHEN 'STUDENT_FINANCE' THEN 1
                      WHEN 'STUDENT_DATA'    THEN 2
                      ELSE 3
                  END
    );

    IF @srcId IS NULL
        SET @srcId = (SELECT TOP (1) Id FROM ALERTCORE.ApprovalConfigs WHERE IsActive = 1 ORDER BY Id);

    DECLARE @newId BIGINT;

    IF @srcId IS NULL
    BEGIN
        /* No template exists - create a standalone single-level approval config. */
        INSERT INTO ALERTCORE.ApprovalConfigs
            (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt)
        VALUES
            ('STUDY_FIN_RECEIVED_WHOM', N'مستلم المبلغ',
             'Approval workflow for finance payment recipients', N'مسار الموافقة على مستلمي المبالغ',
             1, 1, 1, SYSUTCDATETIME());

        SET @newId = SCOPE_IDENTITY();

        INSERT INTO ALERTCORE.ApprovalLevels
            (ConfigId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, CreatedAt)
        VALUES
            (@newId, 1, 'Approval', N'الموافقة', NULL, 0, 1, SYSUTCDATETIME());

        PRINT 'Created STUDY_FIN_RECEIVED_WHOM approval config with a default single level (no template found).';
    END
    ELSE
    BEGIN
        /* Clone the template config header... */
        INSERT INTO ALERTCORE.ApprovalConfigs
            (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt, CreatedBy)
        SELECT
            'STUDY_FIN_RECEIVED_WHOM', N'مستلم المبلغ',
            'Approval workflow for finance payment recipients', N'مسار الموافقة على مستلمي المبالغ',
            TotalLevels, 1, RequireAllLevels, SYSUTCDATETIME(), CreatedBy
        FROM ALERTCORE.ApprovalConfigs
        WHERE Id = @srcId;

        SET @newId = SCOPE_IDENTITY();

        /* ...and its active levels (reusing the same RequiredRoleId values that already work). */
        INSERT INTO ALERTCORE.ApprovalLevels
            (ConfigId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, CreatedAt)
        SELECT
            @newId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, SYSUTCDATETIME()
        FROM ALERTCORE.ApprovalLevels
        WHERE ConfigId = @srcId
          AND IsActive = 1
        ORDER BY LevelOrder;

        PRINT 'Created STUDY_FIN_RECEIVED_WHOM approval config by cloning config Id ' + CAST(@srcId AS NVARCHAR(20)) + '.';
    END
END
ELSE
BEGIN
    PRINT 'STUDY_FIN_RECEIVED_WHOM approval config already exists - no changes made.';
END

COMMIT;

/* ---------------------------------------------------------------------------
   Verify
   --------------------------------------------------------------------------- */
SELECT c.Id, c.EntityType, c.EntityTypeAr, c.TotalLevels, c.IsActive, c.RequireAllLevels
  FROM ALERTCORE.ApprovalConfigs c
 WHERE c.EntityType = 'STUDY_FIN_RECEIVED_WHOM';

SELECT l.Id, l.ConfigId, l.LevelOrder, l.LevelName, l.RequiredRoleId, l.CanSkip, l.IsActive
  FROM ALERTCORE.ApprovalLevels l
  JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
 WHERE c.EntityType = 'STUDY_FIN_RECEIVED_WHOM'
 ORDER BY l.LevelOrder;
