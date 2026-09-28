-- Deduplicate audit triggers on MOHECORE.STUDENT_DATA.
-- Keep the granular TR_STUDENT_DATA_Audit_Insert/Update/Delete trio; drop the legacy combined trigger.
IF EXISTS (SELECT 1 FROM sys.triggers WHERE name = 'tr_STUDENT_DATA_Audit' AND parent_id = OBJECT_ID('MOHECORE.STUDENT_DATA'))
BEGIN
    DROP TRIGGER MOHECORE.tr_STUDENT_DATA_Audit;
END
GO
