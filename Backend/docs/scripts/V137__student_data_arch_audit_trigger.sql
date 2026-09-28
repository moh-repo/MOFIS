/*
    V137 — an audit trail on the student archive (live QA H1, 23 Sep 2026).

    MOHECORE.STUDENT_DATA_ARCH is the quarterly snapshot of every student: the
    system's history. The live test renamed and then deleted an archived row
    through the API, and AUDITLOG.AuditTrail recorded neither — the table had no
    audit trigger, unlike STUDENT_DATA, the UMS tables and the six FINCORE tables
    (V067). The generic update and delete endpoints are gone with the same change;
    the one write left is the finance-column correction from the finance history
    page (review F5), and this trigger records it — and any delete, whoever runs
    it — the way V067 records FINCORE.

    AFTER UPDATE, DELETE only. Inserts are the quarter close, ~941 rows per period
    written by the system in one transaction; auditing those would double the
    archive's size for no question anyone asks. The row itself is the record of
    the insert.

    Same shape as the V067 template: AUDITLOG.usp_ParseAuditContext (V047) turns
    the connection's CONTEXT_INFO into who, from where and which session; the old
    and new rows go in as JSON; COUNTRY_ID is the snapshot's STUDY_COUNTRY_IN, so
    the audit list's country scope applies to these rows as to any other.

    One difference from V067, which is why the trigger is built as dynamic SQL:
    the archive still carries columns of the legacy text/ntext types (the two
    notes), and SQL Server refuses those inside a trigger's inserted/deleted
    tables. The column list is therefore read from sys.columns at migration time:
    every other column is taken from inserted/deleted; a legacy LOB column is
    left out of OLD_VALUES and read for NEW_VALUES from the table itself (an
    AFTER trigger sees the new row there), cast to nvarchar(max). If the notes
    are ever converted to nvarchar(max) the trigger picks them up in full on the
    next run of this script by hand (CREATE OR ALTER).

    Idempotent (CREATE OR ALTER). Stops, with a message, if V047 is not in place.
    Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'MOHECORE.STUDENT_DATA_ARCH', N'U') IS NULL
BEGIN
    PRINT 'V137: MOHECORE.STUDENT_DATA_ARCH absent — nothing to do';
    RETURN;
END
IF OBJECT_ID(N'AUDITLOG.AuditTrail', N'U') IS NULL
   OR COL_LENGTH(N'AUDITLOG.AuditTrail', N'COUNTRY_ID') IS NULL
   OR OBJECT_ID(N'AUDITLOG.usp_ParseAuditContext', N'P') IS NULL
BEGIN
    PRINT 'V137: *** AUDITLOG.AuditTrail.COUNTRY_ID or usp_ParseAuditContext absent (V047 not applied) — trigger not created';
    RETURN;
END

-- The columns, in table order. Legacy LOB types cannot be read from inserted/deleted.
DECLARE @oldCols NVARCHAR(MAX) = STUFF((
    SELECT N', d2.' + QUOTENAME(c.name)
      FROM sys.columns c
      JOIN sys.types t ON t.user_type_id = c.user_type_id
     WHERE c.object_id = OBJECT_ID(N'MOHECORE.STUDENT_DATA_ARCH')
       AND t.name NOT IN (N'text', N'ntext', N'image')
     ORDER BY c.column_id
       FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 2, N'');

DECLARE @newCols NVARCHAR(MAX) = STUFF((
    SELECT N', ' + CASE WHEN t.name IN (N'text', N'ntext', N'image')
                        THEN N'CAST(b.' + QUOTENAME(c.name) + N' AS NVARCHAR(MAX)) AS ' + QUOTENAME(c.name)
                        ELSE N'i2.' + QUOTENAME(c.name) END
      FROM sys.columns c
      JOIN sys.types t ON t.user_type_id = c.user_type_id
     WHERE c.object_id = OBJECT_ID(N'MOHECORE.STUDENT_DATA_ARCH')
     ORDER BY c.column_id
       FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 2, N'');

DECLARE @lobCount INT = (
    SELECT COUNT(*) FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
     WHERE c.object_id = OBJECT_ID(N'MOHECORE.STUDENT_DATA_ARCH') AND t.name IN (N'text', N'ntext', N'image'));

DECLARE @sql NVARCHAR(MAX) = N'
CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_DATA_ARCH_Audit
ON MOHECORE.STUDENT_DATA_ARCH AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted) RETURN;
    DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
    EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy = @cb OUTPUT, @IpAddress = @ip OUTPUT, @SessionId = @sid OUTPUT;
    DECLARE @op NVARCHAR(50) = CASE WHEN EXISTS (SELECT 1 FROM inserted) THEN N''UPDATE'' ELSE N''DELETE'' END;
    INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES,
                                     CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
    SELECT N''MOHECORE'', N''STUDENT_DATA_ARCH'', CAST(COALESCE(i.ID, d.ID) AS NVARCHAR(255)), @op,
           (SELECT ' + @oldCols + N'
              FROM deleted d2 WHERE d2.ID = d.ID FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           (SELECT ' + @newCols + N'
              FROM inserted i2
              JOIN MOHECORE.STUDENT_DATA_ARCH b ON b.ID = i2.ID
             WHERE i2.ID = i.ID FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           @cb, @ip, @sid, GETUTCDATE(), N''MOHE_SYSTEM'', COALESCE(i.STUDY_COUNTRY_IN, d.STUDY_COUNTRY_IN)
      FROM inserted i
      FULL OUTER JOIN deleted d ON i.ID = d.ID;
END;';

EXEC sp_executesql @sql;

PRINT 'V137: MOHECORE.TR_STUDENT_DATA_ARCH_Audit in place (UPDATE, DELETE); legacy LOB columns read from the table for NEW_VALUES: '
    + CAST(@lobCount AS NVARCHAR(10));
