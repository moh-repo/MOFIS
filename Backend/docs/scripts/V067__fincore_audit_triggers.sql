/*
    V067 — audit triggers on the FINCORE money tables.

    Why
    ---
    The fund chain (V042) moves money — appropriations, allocations, payment
    batches and their lines, the attaché advance balance, Wafar earmarks — and
    none of it was audited. Every other table whose rows matter this much
    (STUDENT_DATA, STUDENT_FINANCE, ApprovalRequests, Users) writes to
    AUDITLOG.AuditTrail on insert, update and delete.

    Which trigger shape, and why not sp_CreateAuditTrigger
    -----------------------------------------------------
    AUDITLOG.sp_CreateAuditTrigger exists but writes to AUDITLOG.AuditLogs — a
    different, older table with no country column and nothing reading it.
    AuditTrail is what MoheAuditLogService reads and what AuditTrailCountryTest
    guards, so the triggers here follow V047's stamping triggers instead: one
    row per affected record with OLD_VALUES / NEW_VALUES as JSON, the caller
    from AUDITLOG.usp_ParseAuditContext, and COUNTRY_ID stamped directly from
    the row (STUDY_COUNTRY_IN_ID). DELETE rows need that — by the time a
    resolver could look, the row is gone.

        FINCORE.TR_PAYMENT_BATCH_Audit           BATCH_ID
        FINCORE.TR_PAYMENT_BATCH_LINE_Audit      BATCH_LINE_ID   country falls back to the batch's
        FINCORE.TR_ATTACHE_ALLOCATION_Audit      ALLOCATION_ID
        FINCORE.TR_BUDGET_APPROPRIATION_Audit    APPROPRIATION_ID
        FINCORE.TR_ATTACHE_ADVANCE_BALANCE_Audit BALANCE_ID
        FINCORE.TR_WAFAR_EARMARK_Audit           EARMARK_ID

    The V047 resolver, AUDITLOG.TR_AuditTrail_ResolveCountry, is extended with
    the same six tables so anything written for them through
    AUDITLOG.usp_WriteAuditTrail (which does not carry a country) is resolved
    through STUDY_COUNTRY_IN_ID as well — PAYMENT_BATCH_LINE through its batch
    where its own column is NULL.

    What AuditTrailCountryTest.everyAuditedTableHasACountrySource needs
    -------------------------------------------------------------------
    No AuditTrail row with a NULL COUNTRY_ID except for TABLE_NAME 'Users'.
    Five of the six tables carry STUDY_COUNTRY_IN_ID NOT NULL, and a line's
    batch always has one. BUDGET_APPROPRIATION.STUDY_COUNTRY_IN_ID is nullable
    by design (V042: NULL = the ministry-wide appropriation), so an audit row
    for such an appropriation is ministry-only — exactly what a NULL means in
    that column — and the test will need 'BUDGET_APPROPRIATION' beside 'Users'
    in its exemption the first time a ministry-wide appropriation is written.
    Today the table is empty, so the test passes as it stands.

    Trigger bodies are created with sp_executesql because CREATE OR ALTER
    TRIGGER must be the first statement of its batch; each is idempotent.

    Rollback
    --------
        DROP TRIGGER FINCORE.TR_PAYMENT_BATCH_Audit, FINCORE.TR_PAYMENT_BATCH_LINE_Audit,
                     FINCORE.TR_ATTACHE_ALLOCATION_Audit, FINCORE.TR_BUDGET_APPROPRIATION_Audit,
                     FINCORE.TR_ATTACHE_ADVANCE_BALANCE_Audit, FINCORE.TR_WAFAR_EARMARK_Audit;
        re-run V047 section 2 for the previous resolver.

    Prerequisites: V042 (the tables), V047 (COUNTRY_ID, usp_ParseAuditContext).
    Run with sqlcmd -I -f 65001 -b.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

PRINT '';
PRINT '=== V067: FINCORE audit triggers ===';
GO

IF OBJECT_ID('AUDITLOG.AuditTrail', 'U') IS NULL OR COL_LENGTH('AUDITLOG.AuditTrail', 'COUNTRY_ID') IS NULL
   OR OBJECT_ID('AUDITLOG.usp_ParseAuditContext', 'P') IS NULL
    PRINT 'V067: *** AUDITLOG.AuditTrail.COUNTRY_ID or usp_ParseAuditContext absent (V047 not applied) — nothing to do';
ELSE
BEGIN
    DECLARE @t TABLE (table_name SYSNAME, pk_col SYSNAME, country_expr NVARCHAR(400), extra_join NVARCHAR(400));
    INSERT INTO @t VALUES
        ('PAYMENT_BATCH',           'BATCH_ID',         N'COALESCE(i.STUDY_COUNTRY_IN_ID, d.STUDY_COUNTRY_IN_ID)', N''),
        ('PAYMENT_BATCH_LINE',      'BATCH_LINE_ID',    N'COALESCE(i.STUDY_COUNTRY_IN_ID, d.STUDY_COUNTRY_IN_ID, pb.STUDY_COUNTRY_IN_ID)',
                                                        N'LEFT JOIN FINCORE.PAYMENT_BATCH pb ON pb.BATCH_ID = COALESCE(i.BATCH_ID, d.BATCH_ID)'),
        ('ATTACHE_ALLOCATION',      'ALLOCATION_ID',    N'COALESCE(i.STUDY_COUNTRY_IN_ID, d.STUDY_COUNTRY_IN_ID)', N''),
        ('BUDGET_APPROPRIATION',    'APPROPRIATION_ID', N'COALESCE(i.STUDY_COUNTRY_IN_ID, d.STUDY_COUNTRY_IN_ID)', N''),
        ('ATTACHE_ADVANCE_BALANCE', 'BALANCE_ID',       N'COALESCE(i.STUDY_COUNTRY_IN_ID, d.STUDY_COUNTRY_IN_ID)', N''),
        ('WAFAR_EARMARK',           'EARMARK_ID',       N'COALESCE(i.STUDY_COUNTRY_IN_ID, d.STUDY_COUNTRY_IN_ID)', N'');

    DECLARE @tbl SYSNAME, @pk SYSNAME, @cexpr NVARCHAR(400), @join NVARCHAR(400), @sql NVARCHAR(MAX), @existed BIT;
    DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT table_name, pk_col, country_expr, extra_join FROM @t;
    OPEN c; FETCH NEXT FROM c INTO @tbl, @pk, @cexpr, @join;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF OBJECT_ID('FINCORE.' + @tbl, 'U') IS NULL
            PRINT 'V067: FINCORE.' + @tbl + ' absent — skipped';
        ELSE IF COL_LENGTH('FINCORE.' + @tbl, 'STUDY_COUNTRY_IN_ID') IS NULL OR COL_LENGTH('FINCORE.' + @tbl, @pk) IS NULL
            PRINT 'V067: FINCORE.' + @tbl + ' lacks STUDY_COUNTRY_IN_ID or ' + @pk + ' — skipped';
        ELSE
        BEGIN
            SET @existed = CASE WHEN OBJECT_ID('FINCORE.TR_' + @tbl + '_Audit', 'TR') IS NULL THEN 0 ELSE 1 END;
            SET @sql = N'
CREATE OR ALTER TRIGGER FINCORE.' + QUOTENAME('TR_' + @tbl + '_Audit') + N'
ON FINCORE.' + QUOTENAME(@tbl) + N' AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted) RETURN;
    DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
    EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy = @cb OUTPUT, @IpAddress = @ip OUTPUT, @SessionId = @sid OUTPUT;
    DECLARE @op NVARCHAR(50) = CASE WHEN EXISTS (SELECT 1 FROM inserted) AND EXISTS (SELECT 1 FROM deleted) THEN N''UPDATE''
                                    WHEN EXISTS (SELECT 1 FROM inserted) THEN N''INSERT'' ELSE N''DELETE'' END;
    INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES,
                                     CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
    SELECT N''FINCORE'', N''' + @tbl + N''', CAST(COALESCE(i.' + QUOTENAME(@pk) + N', d.' + QUOTENAME(@pk) + N') AS NVARCHAR(255)), @op,
           (SELECT d2.* FROM deleted  d2 WHERE d2.' + QUOTENAME(@pk) + N' = d.' + QUOTENAME(@pk) + N' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           (SELECT i2.* FROM inserted i2 WHERE i2.' + QUOTENAME(@pk) + N' = i.' + QUOTENAME(@pk) + N' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           @cb, @ip, @sid, GETUTCDATE(), N''MOHE_SYSTEM'', ' + @cexpr + N'
      FROM inserted i
      FULL OUTER JOIN deleted d ON i.' + QUOTENAME(@pk) + N' = d.' + QUOTENAME(@pk) + N'
      ' + @join + N';
END;';
            EXEC sp_executesql @sql;
            PRINT 'V067: FINCORE.TR_' + @tbl + '_Audit ' + CASE WHEN @existed = 1 THEN 'refreshed (already present)' ELSE 'created' END;
        END
        FETCH NEXT FROM c INTO @tbl, @pk, @cexpr, @join;
    END
    CLOSE c; DEALLOCATE c;
END
GO

/*------------------------------------------------------------------------------
  The resolver (V047 §2), with the six FINCORE tables added. Body otherwise
  as V047 wrote it. Guarded so it is not replaced where V047 never ran.
------------------------------------------------------------------------------*/
IF OBJECT_ID('AUDITLOG.TR_AuditTrail_ResolveCountry', 'TR') IS NULL
    PRINT 'V067: *** TR_AuditTrail_ResolveCountry absent (V047 not applied) — resolver not extended';
ELSE
BEGIN
    EXEC sp_executesql N'
CREATE OR ALTER TRIGGER AUDITLOG.TR_AuditTrail_ResolveCountry
ON AUDITLOG.AuditTrail
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM inserted WHERE COUNTRY_ID IS NULL AND OPERATION <> N''DELETE'')
        RETURN;

    UPDATE at
       SET at.COUNTRY_ID = COALESCE(sd.STUDY_COUNTRY_IN, sf.STUDY_COUNTRY_IN_ID, ar.CountryId,
                                    pb.STUDY_COUNTRY_IN_ID, pbl.STUDY_COUNTRY_IN_ID, pblb.STUDY_COUNTRY_IN_ID,
                                    aa.STUDY_COUNTRY_IN_ID, ba.STUDY_COUNTRY_IN_ID, ab.STUDY_COUNTRY_IN_ID, we.STUDY_COUNTRY_IN_ID)
      FROM AUDITLOG.AuditTrail at
      JOIN inserted i ON i.LOG_ID = at.LOG_ID
      LEFT JOIN MOHECORE.STUDENT_DATA sd
             ON at.[SCHEMA_NAME] = N''MOHECORE'' AND at.TABLE_NAME = N''STUDENT_DATA''
            AND sd.STUDENT_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN MOHECORE.STUDENT_FINANCE sf
             ON at.[SCHEMA_NAME] = N''MOHECORE'' AND at.TABLE_NAME = N''STUDENT_FINANCE''
            AND sf.STUDENT_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN ALERTCORE.ApprovalRequests ar
             ON at.[SCHEMA_NAME] = N''ALERTCORE'' AND at.TABLE_NAME = N''ApprovalRequests''
            AND ar.Id = TRY_CAST(at.RECORD_ID AS bigint)
      -- V067: the FINCORE money tables, all on STUDY_COUNTRY_IN_ID
      LEFT JOIN FINCORE.PAYMENT_BATCH pb
             ON at.[SCHEMA_NAME] = N''FINCORE'' AND at.TABLE_NAME = N''PAYMENT_BATCH''
            AND pb.BATCH_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN FINCORE.PAYMENT_BATCH_LINE pbl
             ON at.[SCHEMA_NAME] = N''FINCORE'' AND at.TABLE_NAME = N''PAYMENT_BATCH_LINE''
            AND pbl.BATCH_LINE_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN FINCORE.PAYMENT_BATCH pblb ON pblb.BATCH_ID = pbl.BATCH_ID
      LEFT JOIN FINCORE.ATTACHE_ALLOCATION aa
             ON at.[SCHEMA_NAME] = N''FINCORE'' AND at.TABLE_NAME = N''ATTACHE_ALLOCATION''
            AND aa.ALLOCATION_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN FINCORE.BUDGET_APPROPRIATION ba
             ON at.[SCHEMA_NAME] = N''FINCORE'' AND at.TABLE_NAME = N''BUDGET_APPROPRIATION''
            AND ba.APPROPRIATION_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN FINCORE.ATTACHE_ADVANCE_BALANCE ab
             ON at.[SCHEMA_NAME] = N''FINCORE'' AND at.TABLE_NAME = N''ATTACHE_ADVANCE_BALANCE''
            AND ab.BALANCE_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN FINCORE.WAFAR_EARMARK we
             ON at.[SCHEMA_NAME] = N''FINCORE'' AND at.TABLE_NAME = N''WAFAR_EARMARK''
            AND we.EARMARK_ID = TRY_CAST(at.RECORD_ID AS int)
     WHERE at.COUNTRY_ID IS NULL
       AND at.OPERATION <> N''DELETE'';
END';
    PRINT 'V067: TR_AuditTrail_ResolveCountry now resolves the six FINCORE tables through STUDY_COUNTRY_IN_ID';
END
GO

/*------------------------------------------------------------------------------
  Verification
------------------------------------------------------------------------------*/
PRINT '';
PRINT '=== V067 result ===';
SELECT OBJECT_NAME(tr.parent_id) AS table_name, tr.name AS trigger_name, tr.is_disabled
  FROM sys.triggers tr
 WHERE OBJECT_SCHEMA_NAME(tr.parent_id) = 'FINCORE' AND tr.name LIKE 'TR[_]%[_]Audit'
 ORDER BY 1;

SELECT TABLE_NAME, COUNT(*) AS rows_, SUM(CASE WHEN COUNTRY_ID IS NULL THEN 1 ELSE 0 END) AS no_country
  FROM AUDITLOG.AuditTrail
 WHERE [SCHEMA_NAME] = N'FINCORE'
 GROUP BY TABLE_NAME ORDER BY 1;
PRINT 'Expected: six triggers, none disabled; no FINCORE audit row without a country';
PRINT '(a NULL on BUDGET_APPROPRIATION is a ministry-wide appropriation — see the header).';
PRINT '=== V067 done ===';
GO
