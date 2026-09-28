/* ============================================================================
   Ensure the Student loans screen is gated by an approval workflow
   ----------------------------------------------------------------------------
   Tables : ALERTCORE.ApprovalConfigs
            ALERTCORE.ApprovalLevels

   Why
     Every add / update / delete on /student-loan is submitted for approval
     (StudentLoanController -> ApprovalIntegrationService). The workflow needs an
     ApprovalConfig row whose EntityType = 'STUDENT_LOAN', or the request
     fails with "No approval config found for entity type: STUDENT_LOAN".

     Reads are unaffected; only the writes open a request.

   What this does
     If no STUDENT_LOAN config exists yet, it CLONES an existing, working
     config - preferring DEFAULT (two levels: Manager, then Director), then
     STUDENT_FINANCE, then STUDENT_DATA, then any active config - so the new
     config reuses the same levels and RequiredRoleId values that already work
     in your environment. If there is no config to clone, it creates a minimal
     single level ("Approval") with no required role.

   Safe to re-run: it only creates the config when it is missing, and never
   touches an existing STUDENT_LOAN config.

   Open in SSMS as UTF-8 so the Arabic text reads correctly, and make sure the
   query window is on the correct database.
   ============================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRAN;

IF NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalConfigs WHERE EntityType = 'STUDENT_LOAN')
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
            ('STUDENT_LOAN', N'قرض الطالب',
             'Approval workflow for student loans', N'مسار الموافقة على قروض الطلاب',
             1, 1, 1, SYSUTCDATETIME());

        SET @newId = SCOPE_IDENTITY();

        INSERT INTO ALERTCORE.ApprovalLevels
            (ConfigId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, CreatedAt)
        VALUES
            (@newId, 1, 'Approval', N'الموافقة', NULL, 0, 1, SYSUTCDATETIME());

        PRINT 'Created STUDENT_LOAN approval config with a default single level (no template found).';
    END
    ELSE
    BEGIN
        /* Clone the template config header... */
        INSERT INTO ALERTCORE.ApprovalConfigs
            (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt, CreatedBy)
        SELECT
            'STUDENT_LOAN', N'قرض الطالب',
            'Approval workflow for student loans', N'مسار الموافقة على قروض الطلاب',
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

        PRINT 'Created STUDENT_LOAN approval config by cloning config Id ' + CAST(@srcId AS NVARCHAR(20)) + '.';
    END
END
ELSE
BEGIN
    PRINT 'STUDENT_LOAN approval config already exists - no changes made.';
END

COMMIT;

/* ---------------------------------------------------------------------------
   Verify
   --------------------------------------------------------------------------- */
SELECT c.Id, c.EntityType, c.EntityTypeAr, c.TotalLevels, c.IsActive, c.RequireAllLevels
  FROM ALERTCORE.ApprovalConfigs c
 WHERE c.EntityType = 'STUDENT_LOAN';

SELECT l.Id, l.ConfigId, l.LevelOrder, l.LevelName, l.RequiredRoleId, l.CanSkip, l.IsActive
  FROM ALERTCORE.ApprovalLevels l
  JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
 WHERE c.EntityType = 'STUDENT_LOAN'
 ORDER BY l.LevelOrder;
