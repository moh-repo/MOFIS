/*
    V139 — an audit trail on the finance and fee archives (live QA H2, 24 Sep 2026).

    V137 gave MOHECORE.STUDENT_DATA_ARCH the audit trigger the live test of 23 Sep
    found missing. The next day's round did to the finance archive what the first
    had done to the student archive: DELETE /api/v1/studentFinanceArch/1 answered
    200, FIN_ARCH_ENTRY_ID 1 (student 1, snapshot of 4 June 2026) was gone, and
    AUDITLOG.AuditTrail recorded nothing, because the trigger covered one archive
    table of three. The write endpoints of both archives are removed with the
    same change; this script records any change that still reaches the rows —
    a correction by hand, a script — for MOHECORE.STUDENT_FINANCE_ARCH and
    MOHECORE.STUDY_UNIVERSITY_FEE_ARCH.

    Same shape as V137, table by table: AFTER UPDATE, DELETE only (inserts are the
    quarter close, ~1,000 rows per period, and the row itself is the record of the
    insert); AUDITLOG.usp_ParseAuditContext (V047) turns the connection's
    CONTEXT_INFO into who, from where and which session; old and new rows go in
    as JSON; COUNTRY_ID is the row's STUDY_COUNTRY_IN_ID so the audit list's
    country scope applies. Legacy text/ntext columns cannot be read from
    inserted/deleted inside a trigger, so the column list is read from
    sys.columns at migration time: a LOB column is left out of OLD_VALUES and
    read for NEW_VALUES from the table itself, cast to nvarchar(max).

    Each table is skipped, with a message, if it or its key/country column is
    absent. Idempotent (CREATE OR ALTER). Stops if V047 is not in place.
    Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'AUDITLOG.AuditTrail', N'U') IS NULL
   OR COL_LENGTH(N'AUDITLOG.AuditTrail', N'COUNTRY_ID') IS NULL
   OR OBJECT_ID(N'AUDITLOG.usp_ParseAuditContext', N'P') IS NULL
BEGIN
    PRINT 'V139: *** AUDITLOG.AuditTrail.COUNTRY_ID or usp_ParseAuditContext absent (V047 not applied) — triggers not created';
    RETURN;
END

DECLARE @targets TABLE (seq INT IDENTITY(1,1), tbl SYSNAME, keyCol SYSNAME, countryCol SYSNAME, trg SYSNAME);
INSERT INTO @targets (tbl, keyCol, countryCol, trg) VALUES
    (N'STUDENT_FINANCE_ARCH',     N'FIN_ARCH_ENTRY_ID',       N'STUDY_COUNTRY_IN_ID', N'TR_STUDENT_FINANCE_ARCH_Audit'),
    (N'STUDY_UNIVERSITY_FEE_ARCH', N'STUDY_UNIVERSITY_FEE_ID', N'STUDY_COUNTRY_IN_ID', N'TR_STUDY_UNIVERSITY_FEE_ARCH_Audit');

DECLARE @i INT = 1, @n INT = (SELECT COUNT(*) FROM @targets);
DECLARE @tbl SYSNAME, @keyCol SYSNAME, @countryCol SYSNAME, @trg SYSNAME, @full NVARCHAR(300);
DECLARE @oldCols NVARCHAR(MAX), @newCols NVARCHAR(MAX), @lobCount INT, @sql NVARCHAR(MAX);

WHILE @i <= @n
BEGIN
    SELECT @tbl = tbl, @keyCol = keyCol, @countryCol = countryCol, @trg = trg FROM @targets WHERE seq = @i;
    SET @full = N'MOHECORE.' + @tbl;

    IF OBJECT_ID(@full, N'U') IS NULL
        PRINT 'V139: ' + @full + ' absent — skipped';
    ELSE IF COL_LENGTH(@full, @keyCol) IS NULL OR COL_LENGTH(@full, @countryCol) IS NULL
        PRINT 'V139: *** ' + @full + ' lacks ' + @keyCol + ' or ' + @countryCol + ' — trigger not created';
    ELSE
    BEGIN
        -- The columns, in table order. Legacy LOB types cannot be read from inserted/deleted.
        SET @oldCols = STUFF((
            SELECT N', d2.' + QUOTENAME(c.name)
              FROM sys.columns c
              JOIN sys.types t ON t.user_type_id = c.user_type_id
             WHERE c.object_id = OBJECT_ID(@full)
               AND t.name NOT IN (N'text', N'ntext', N'image')
             ORDER BY c.column_id
               FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 2, N'');

        SET @newCols = STUFF((
            SELECT N', ' + CASE WHEN t.name IN (N'text', N'ntext', N'image')
                                THEN N'CAST(b.' + QUOTENAME(c.name) + N' AS NVARCHAR(MAX)) AS ' + QUOTENAME(c.name)
                                ELSE N'i2.' + QUOTENAME(c.name) END
              FROM sys.columns c
              JOIN sys.types t ON t.user_type_id = c.user_type_id
             WHERE c.object_id = OBJECT_ID(@full)
             ORDER BY c.column_id
               FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 2, N'');

        SET @lobCount = (
            SELECT COUNT(*) FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
             WHERE c.object_id = OBJECT_ID(@full) AND t.name IN (N'text', N'ntext', N'image'));

        SET @sql = N'
CREATE OR ALTER TRIGGER MOHECORE.' + QUOTENAME(@trg) + N'
ON ' + @full + N' AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted) RETURN;
    DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
    EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy = @cb OUTPUT, @IpAddress = @ip OUTPUT, @SessionId = @sid OUTPUT;
    DECLARE @op NVARCHAR(50) = CASE WHEN EXISTS (SELECT 1 FROM inserted) THEN N''UPDATE'' ELSE N''DELETE'' END;
    INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES,
                                     CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
    SELECT N''MOHECORE'', N''' + @tbl + N''', CAST(COALESCE(i.' + QUOTENAME(@keyCol) + N', d.' + QUOTENAME(@keyCol) + N') AS NVARCHAR(255)), @op,
           (SELECT ' + @oldCols + N'
              FROM deleted d2 WHERE d2.' + QUOTENAME(@keyCol) + N' = d.' + QUOTENAME(@keyCol) + N' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           (SELECT ' + @newCols + N'
              FROM inserted i2
              JOIN ' + @full + N' b ON b.' + QUOTENAME(@keyCol) + N' = i2.' + QUOTENAME(@keyCol) + N'
             WHERE i2.' + QUOTENAME(@keyCol) + N' = i.' + QUOTENAME(@keyCol) + N' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           @cb, @ip, @sid, GETUTCDATE(), N''MOHE_SYSTEM'', COALESCE(i.' + QUOTENAME(@countryCol) + N', d.' + QUOTENAME(@countryCol) + N')
      FROM inserted i
      FULL OUTER JOIN deleted d ON i.' + QUOTENAME(@keyCol) + N' = d.' + QUOTENAME(@keyCol) + N';
END;';

        EXEC sp_executesql @sql;

        PRINT 'V139: MOHECORE.' + @trg + ' in place on ' + @full + ' (UPDATE, DELETE); legacy LOB columns read from the table for NEW_VALUES: '
            + CAST(@lobCount AS NVARCHAR(10));
    END

    SET @i = @i + 1;
END
