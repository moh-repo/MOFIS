/*
    V088 — Drop the unused UMSCORE.Users.PasswordSalt column.

    Security plan O-10 (6 Sep 2026). BCrypt carries its own salt inside the
    hash; the column has been written as NULL by every code path since the
    original salted scheme was retired, and the entity no longer maps it. A
    column that exists but means nothing is one a future reader will try to
    use.

    Deploy the application build that drops the mapping first (it does not
    select the column, so either order works), then run this once.
*/
IF COL_LENGTH('UMSCORE.Users', 'PasswordSalt') IS NOT NULL
BEGIN
    DECLARE @df sysname;
    SELECT @df = dc.name
      FROM sys.default_constraints dc
      JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
     WHERE dc.parent_object_id = OBJECT_ID('UMSCORE.Users') AND c.name = 'PasswordSalt';
    IF @df IS NOT NULL
        EXEC('ALTER TABLE UMSCORE.Users DROP CONSTRAINT ' + @df);
    ALTER TABLE UMSCORE.Users DROP COLUMN PasswordSalt;
    PRINT 'UMSCORE.Users.PasswordSalt dropped';
END
ELSE
    PRINT 'UMSCORE.Users.PasswordSalt already absent';

/*
    The audit trigger on UMSCORE.Users (V0xx) named the column in its
    changed-columns list, so after the drop every UPDATE on Users failed with
    "Invalid column name 'PasswordSalt'" (found on the first dev run under the
    full suite, 6 Sep 2026). Re-created without that line, only when the
    trigger still mentions the column, so the script stays idempotent.
*/
IF EXISTS (SELECT 1 FROM sys.sql_modules WHERE object_id = OBJECT_ID('UMSCORE.TR_Users_Audit_Update')
             AND definition LIKE '%d.PasswordSalt%')
BEGIN
    EXEC(N'ALTER TRIGGER UMSCORE.TR_Users_Audit_Update ON UMSCORE.Users AFTER UPDATE AS
SET NOCOUNT ON;
DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy=@cb OUTPUT, @IpAddress=@ip OUTPUT, @SessionId=@sid OUTPUT;
INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES, CHANGED_COLUMNS, CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME)
SELECT N''UMSCORE'', N''Users'', CAST(d.Id AS NVARCHAR(255)), N''UPDATE'',
  (SELECT d2.* FROM deleted d2 WHERE d2.Id = d.Id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
  (SELECT i2.* FROM inserted i2 WHERE i2.Id = d.Id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
  NULLIF(STUFF(
    (CASE WHEN ISNULL(d.UserName, N'''') <> ISNULL(i.UserName, N'''') THEN N'', UserName'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.Email, N'''') <> ISNULL(i.Email, N'''') THEN N'', Email'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.PasswordHash, N'''') <> ISNULL(i.PasswordHash, N'''') THEN N'', PasswordHash'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.FirstName, N'''') <> ISNULL(i.FirstName, N'''') THEN N'', FirstName'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.LastName, N'''') <> ISNULL(i.LastName, N'''') THEN N'', LastName'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.Status, N'''') <> ISNULL(i.Status, N'''') THEN N'', Status'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.IsAdmin AS NVARCHAR(5)), N'''') <> ISNULL(CAST(i.IsAdmin AS NVARCHAR(5)), N'''') THEN N'', IsAdmin'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.IsSuperAdmin AS NVARCHAR(5)), N'''') <> ISNULL(CAST(i.IsSuperAdmin AS NVARCHAR(5)), N'''') THEN N'', IsSuperAdmin'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.IsMinistryUser AS NVARCHAR(5)), N'''') <> ISNULL(CAST(i.IsMinistryUser AS NVARCHAR(5)), N'''') THEN N'', IsMinistryUser'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.FailedLoginAttempts, -1) <> ISNULL(i.FailedLoginAttempts, -1) THEN N'', FailedLoginAttempts'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.LastLoginAt AS NVARCHAR(50)), N'''') <> ISNULL(CAST(i.LastLoginAt AS NVARCHAR(50)), N'''') THEN N'', LastLoginAt'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.PasswordChangedAt AS NVARCHAR(50)), N'''') <> ISNULL(CAST(i.PasswordChangedAt AS NVARCHAR(50)), N'''') THEN N'', PasswordChangedAt'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.CreatedAt AS NVARCHAR(50)), N'''') <> ISNULL(CAST(i.CreatedAt AS NVARCHAR(50)), N'''') THEN N'', CreatedAt'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.UpdatedAt AS NVARCHAR(50)), N'''') <> ISNULL(CAST(i.UpdatedAt AS NVARCHAR(50)), N'''') THEN N'', UpdatedAt'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.CreatedBy, -1) <> ISNULL(i.CreatedBy, -1) THEN N'', CreatedBy'' ELSE N'''' END) +
    (CASE WHEN ISNULL(d.UpdatedBy, -1) <> ISNULL(i.UpdatedBy, -1) THEN N'', UpdatedBy'' ELSE N'''' END) +
    (CASE WHEN ISNULL(CAST(d.IsDeleted AS NVARCHAR(5)), N'''') <> ISNULL(CAST(i.IsDeleted AS NVARCHAR(5)), N'''') THEN N'', IsDeleted'' ELSE N'''' END),
  1, 2, N''''), N''''),
  @cb, @ip, @sid, GETUTCDATE(), N''MOHE_SYSTEM''
FROM deleted d INNER JOIN inserted i ON i.Id = d.Id;');
    PRINT 'UMSCORE.TR_Users_Audit_Update re-created without PasswordSalt';
END
ELSE
    PRINT 'UMSCORE.TR_Users_Audit_Update already free of PasswordSalt';
