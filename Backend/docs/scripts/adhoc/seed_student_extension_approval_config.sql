/* ============================================================================
   Ensure the Student Extension feature is gated by an approval workflow
   ----------------------------------------------------------------------------
   Tables : ALERTCORE.ApprovalConfigs
            ALERTCORE.ApprovalLevels

   Why
     Every add / update / delete on a student extension is submitted for approval
     (StudentExtensionController -> ApprovalIntegrationService). The workflow needs
     an ApprovalConfig row whose EntityType = 'STUDENT_EXTENSION' (it otherwise
     falls back to a 'DEFAULT' config, and if neither exists the request fails with
     "No approval config found for entity type: STUDENT_EXTENSION").

   What this does
     If no STUDENT_EXTENSION config exists yet, it CLONES an existing, working
     config — preferring STUDENT_DATA, then DEFAULT, then any active config — so the
     new config reuses the same levels and RequiredRoleId values that already work in
     your environment. If there is no config to clone, it creates a minimal single
     level ("Approval") with no required role.

   Safe to re-run: it only creates the config when it is missing, and never touches
   an existing STUDENT_EXTENSION config.

   Open in SSMS as UTF-8 (saved with BOM) so the Arabic text reads correctly, and
   make sure the query window is on the correct database.
   ============================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRAN;

IF NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalConfigs WHERE EntityType = 'STUDENT_EXTENSION')
BEGIN
    /* Pick a template config to clone: STUDENT_DATA first, then DEFAULT, then any active one. */
    DECLARE @srcId BIGINT =
    (
        SELECT TOP (1) Id
          FROM ALERTCORE.ApprovalConfigs
         WHERE IsActive = 1
           AND EntityType IN ('STUDENT_DATA', 'DEFAULT')
         ORDER BY CASE EntityType WHEN 'STUDENT_DATA' THEN 0 WHEN 'DEFAULT' THEN 1 ELSE 2 END
    );

    IF @srcId IS NULL
        SET @srcId = (SELECT TOP (1) Id FROM ALERTCORE.ApprovalConfigs WHERE IsActive = 1 ORDER BY Id);

    DECLARE @newId BIGINT;

    IF @srcId IS NULL
    BEGIN
        /* No template exists — create a standalone single-level approval config. */
        INSERT INTO ALERTCORE.ApprovalConfigs
            (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt)
        VALUES
            ('STUDENT_EXTENSION', N'تمديد الطالب',
             'Approval workflow for student study extensions', N'مسار الموافقة على تمديد دراسة الطالب',
             1, 1, 1, SYSUTCDATETIME());

        SET @newId = SCOPE_IDENTITY();

        INSERT INTO ALERTCORE.ApprovalLevels
            (ConfigId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, CreatedAt)
        VALUES
            (@newId, 1, 'Approval', N'الموافقة', NULL, 0, 1, SYSUTCDATETIME());

        PRINT 'Created STUDENT_EXTENSION approval config with a default single level (no template found).';
    END
    ELSE
    BEGIN
        /* Clone the template config header... */
        INSERT INTO ALERTCORE.ApprovalConfigs
            (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt, CreatedBy)
        SELECT
            'STUDENT_EXTENSION', N'تمديد الطالب',
            'Approval workflow for student study extensions', N'مسار الموافقة على تمديد دراسة الطالب',
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

        PRINT 'Created STUDENT_EXTENSION approval config by cloning config Id ' + CAST(@srcId AS NVARCHAR(20)) + '.';
    END
END
ELSE
BEGIN
    PRINT 'STUDENT_EXTENSION approval config already exists — no changes made.';
END

COMMIT;

/* ---------------------------------------------------------------------------
   Verify
   --------------------------------------------------------------------------- */
SELECT c.Id, c.EntityType, c.TotalLevels, c.IsActive, c.RequireAllLevels
  FROM ALERTCORE.ApprovalConfigs c
 WHERE c.EntityType = 'STUDENT_EXTENSION';

SELECT l.Id, l.ConfigId, l.LevelOrder, l.LevelName, l.RequiredRoleId, l.CanSkip, l.IsActive
  FROM ALERTCORE.ApprovalLevels l
  JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
 WHERE c.EntityType = 'STUDENT_EXTENSION'
 ORDER BY l.LevelOrder;
