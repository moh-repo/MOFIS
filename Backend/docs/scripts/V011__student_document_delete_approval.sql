/*
 * Approval workflow for student document deletion.
 *
 * Deleting a document used to take effect immediately, so it never appeared in the
 * approvals queue and reviewers had no way to see or stop it. The delete endpoint now
 * raises a STUDENT_DOCUMENT request and the removal happens only once that is approved,
 * which needs a config row for the new entity type.
 *
 * The workflow falls back to the DEFAULT config when a type has none, so this script is
 * additive: without it deletions still queue, they just inherit the default levels.
 *
 * Idempotent — safe to re-run.
 */

IF NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalConfigs WHERE EntityType = 'STUDENT_DOCUMENT')
BEGIN
    INSERT INTO ALERTCORE.ApprovalConfigs
        (EntityType, EntityTypeAr, Description, DescriptionAr,
         TotalLevels, IsActive, RequireAllLevels, CreatedAt)
    VALUES
        ('STUDENT_DOCUMENT',
         N'مستندات الطالب',
         'Approval required before a student document is removed',
         N'يتطلب اعتماداً قبل حذف مستند الطالب',
         1, 1, 1, SYSUTCDATETIME());
END
GO

/* Single review level, mirroring the shape used by the other student workflows. */
IF NOT EXISTS (
    SELECT 1
    FROM ALERTCORE.ApprovalLevels L
    INNER JOIN ALERTCORE.ApprovalConfigs C ON C.Id = L.ConfigId
    WHERE C.EntityType = 'STUDENT_DOCUMENT' AND L.LevelOrder = 1)
BEGIN
    INSERT INTO ALERTCORE.ApprovalLevels
        (ConfigId, LevelOrder, LevelName, LevelNameAr, CanSkip, IsActive, CreatedAt)
    SELECT C.Id, 1, 'Review', N'مراجعة', 0, 1, SYSUTCDATETIME()
    FROM ALERTCORE.ApprovalConfigs C
    WHERE C.EntityType = 'STUDENT_DOCUMENT';
END
GO
