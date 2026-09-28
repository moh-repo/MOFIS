/*
    V066 — schema hygiene: the objects that exist only by hand, the ones that
           are dead, and the constraints the entities already assume.

    Twelve steps, each guarded and each printing what it did. Definitions
    that are copied (ALERTEVENTCASES, the two rebuilt views) come from the
    live catalogue of 27 Aug 2026, not from memory.

      1.  ALERTEVENTCASES schema, ALERT_EVENT_SCENARIO and ALERT_EVENT_CASE
          (with PK, unique, check, FK, filtered index) and the three permissions
          ALERT_EVENTS.VIEW / ALERT_EVENTS.MANAGE / ALERT_SCENARIOS.MANAGE — all
          existed on the dev database in no script.
      2.  Drop MOHECORE.tr_TRG_AUDIT_MOHECORE_AUDIT_LOG_UPDATE_log: it calls
          [MOHE_LOG].[MOHELOGCORE].[SP_INSERT_AUDIT_LOG], a database and schema
          that do not exist. Then, if MOHECORE.AUDIT_LOG has 0 rows and no
          foreign key points at it, drop the table together with the two
          objects that select from it and nothing else (VW_AUDIT_LOG_SUMMARY,
          SP_GET_AUDIT_LOGS). The entity was deleted in the August revision
          (change-summary-2026-08-27, B8); QueryExecutionService lists both
          names only to deny them. The DENY V025 gave mohe_query_reader on the
          table goes with it.
      3.  Drop dbo.student_country_id_sequence / dbo.student_data_id_sequence.
          Nothing reads them; the ids they were for are IDENTITY.
      4.  Drop the redundant member of every pair of indexes with identical
          key columns, includes and filter on the same table. Computed from
          sys.indexes, not listed by hand. Kept, in order: a named unique
          constraint / PK, a system-named one, a named unique index, any
          unique index, the index named IX_<TABLE>_<KEY COLUMNS>, any index
          named IX_<TABLE>_..., else the older one. Two near-pairs the rule
          misses are handled explicitly and said so: UMSCORE.Users' Email
          (the named index is filtered on a NOT NULL column, so the filter is
          moot) and FINCORE.FX_RATE (IX_FX_RATE_LOOKUP differs from
          UX_FX_RATE_PAIR_DATE only by RATE_DATE DESC, which a backward scan
          of the unique key covers). Dropping an index on STUDENT_DATA changes
          no data; exactly one index on STUDENT_COUNTRY_ID is kept and that is
          asserted at the end of the step.
      5.  Foreign keys, trusted, each created only when every current value
          resolves (the orphan count is printed and the key skipped otherwise):
          ApprovalHistory.ActionBy, ApprovalRequests.RequestedBy,
          ApprovalSteps.ApprovedBy → UMSCORE.Users; ApprovalLevels.RequiredRoleId
          → UMSCORE.Roles; Permissions.ResourceId → UMSCORE.Resources.
      6.  A primary key on MOHECORE_ERR.error_log (one column, a heap): an
          IDENTITY column error_log_id is added and made the key.
      7.  Indexes on FINCORE.PAYMENT_BATCH_LINE and COA_ACCOUNT (STUDY_COUNTRY_IN_ID),
          the predicate column of the policy on both.
      8.  NOT NULL where the entity says nullable = false and the column is
          nullable, after checking for NULLs (skipped and printed if any):
          Users.PasswordHash; Permissions.PermissionCode / ResourceType /
          ActionType; STUDY_UNIVERSITY_FEE_ARCH.STUDENT_ID; STUDENT_NODE.STUDENT_ID;
          and the nine timestamp columns the UMS entities mark nullable = false
          (Users, Roles, Permissions, UserGroups, UserActions .CreatedAt;
          UserRoles, GroupRoles, UserGroupMemberships .AssignedAt;
          RolePermissions.GrantedAt). A timestamp with no default gets
          DEFAULT SYSUTCDATETIME(); an existing default is left alone. An
          index on the column is scripted from the catalogue, dropped for the
          ALTER and recreated (STUDENT_NODE.STUDENT_ID sits under two).
      9.  STUDY_STATUS.status_name to the database collation (it is the only
          column in the database on Arabic_CI_AS; every join to it needs a
          COLLATE); Users.FullName NVARCHAR(MAX) → NVARCHAR(200) after checking
          the longest value; UPDATED_AT DATETIME2 NULL on every FINCORE base
          table that lacks it.
      10. DROP the five dead views V_Academic_Year_Report, V_Fiscal_Year_Report,
          V_Custom_Period_Report, V_Custom_Filter_Report, V_Predictive_Analytics.
          Their repositories bypass them with native queries; the Java entities
          are being moved to @Subselect in parallel. ddl-auto is none, so the
          application starts either way; an entity still mapped to one of them
          fails only when queried. Its own step; see it before running.
      11. Rebuild V_Academic_Performance and V_Student_List_Detailed exactly as
          they are, except GPA is CAST(NULL AS DECIMAL(4,2)) instead of a bare
          NULL (which types as INT) and the three date columns are CAST AS DATE
          (the entity maps them as LocalDate).
      12. Drop UMSCORE.Users.LastLogin — populated on 0 rows; the entity maps
          LastLoginAt. Skipped and printed if any module still names it.

    Session context: the NULL and orphan counts read tables under the tenant
    policy, so TenantAll is set for this connection first.

    Rollback: the dropped views are in V030/V033 (CREATE OR ALTER, re-runnable);
    AUDIT_LOG, its view and procedure are in V033 / the baseline; everything
    else is a plain ALTER back. Idempotent: a second run prints "already" throughout.
    Run with sqlcmd -I -f 65001 -b.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';
PRINT '';
PRINT '=== V066: schema hygiene ===';
GO

/* ── 1. ALERTEVENTCASES ───────────────────────────────────────────────────── */
IF SCHEMA_ID('ALERTEVENTCASES') IS NULL
BEGIN
    EXEC (N'CREATE SCHEMA ALERTEVENTCASES AUTHORIZATION dbo;');
    PRINT 'V066: created schema ALERTEVENTCASES';
END
ELSE PRINT 'V066: schema ALERTEVENTCASES already present';
GO

IF OBJECT_ID('ALERTEVENTCASES.ALERT_EVENT_SCENARIO', 'U') IS NOT NULL
    PRINT 'V066: ALERT_EVENT_SCENARIO already present';
ELSE
BEGIN
    CREATE TABLE ALERTEVENTCASES.ALERT_EVENT_SCENARIO (
        SCENARIO_ID      INT IDENTITY(1, 1) NOT NULL CONSTRAINT PK_ALERT_EVENT_SCENARIO PRIMARY KEY CLUSTERED,
        SCENARIO_CODE    NVARCHAR(50)  NOT NULL CONSTRAINT UQ_ALERT_EVENT_SCENARIO_CODE UNIQUE,
        SCENARIO_NAME_EN NVARCHAR(200) NOT NULL,
        SCENARIO_NAME_AR NVARCHAR(200) NOT NULL,
        DESCRIPTION_EN   NVARCHAR(MAX) NULL,
        DESCRIPTION_AR   NVARCHAR(MAX) NULL,
        SEVERITY         NVARCHAR(20)  NOT NULL CONSTRAINT DF_ALERT_EVENT_SCENARIO_SEVERITY DEFAULT (N'MEDIUM')
                                       CONSTRAINT CK_SCENARIO_SEVERITY CHECK (SEVERITY IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')),
        THRESHOLD_DAYS   INT           NULL,
        IS_ACTIVE        BIT           NOT NULL CONSTRAINT DF_ALERT_EVENT_SCENARIO_IS_ACTIVE DEFAULT (1),
        CREATED_AT       DATETIME2     NOT NULL CONSTRAINT DF_ALERT_EVENT_SCENARIO_CREATED_AT DEFAULT (GETDATE()),
        UPDATED_AT       DATETIME2     NULL
    );
    PRINT 'V066: created ALERTEVENTCASES.ALERT_EVENT_SCENARIO';
END
GO

IF OBJECT_ID('ALERTEVENTCASES.ALERT_EVENT_CASE', 'U') IS NOT NULL
    PRINT 'V066: ALERT_EVENT_CASE already present';
ELSE
BEGIN
    CREATE TABLE ALERTEVENTCASES.ALERT_EVENT_CASE (
        CASE_ID            INT IDENTITY(1, 1) NOT NULL CONSTRAINT PK_ALERT_EVENT_CASE PRIMARY KEY CLUSTERED,
        SCENARIO_ID        INT           NOT NULL CONSTRAINT FK_AEC_SCENARIO REFERENCES ALERTEVENTCASES.ALERT_EVENT_SCENARIO (SCENARIO_ID),
        STUDENT_ID         INT           NOT NULL CONSTRAINT FK_AEC_STUDENT_DATA REFERENCES MOHECORE.STUDENT_DATA (STUDENT_ID) ON DELETE CASCADE,
        STUDENT_COUNTRY_ID INT           NULL,
        REFERENCE_DATE     DATETIME2     NULL,
        STATUS             NVARCHAR(20)  NOT NULL CONSTRAINT DF_ALERT_EVENT_CASE_STATUS DEFAULT (N'OPEN')
                                         CONSTRAINT CK_AEC_STATUS CHECK (STATUS IN (N'OPEN', N'ACKNOWLEDGED', N'RESOLVED', N'DISMISSED')),
        NOTES              NVARCHAR(MAX) NULL,
        ACKNOWLEDGED_BY    NVARCHAR(150) NULL,
        ACKNOWLEDGED_AT    DATETIME2     NULL,
        RESOLVED_BY        NVARCHAR(150) NULL,
        RESOLVED_AT        DATETIME2     NULL,
        IS_DELETED         BIT           NOT NULL CONSTRAINT DF_ALERT_EVENT_CASE_IS_DELETED DEFAULT (0),
        CREATED_AT         DATETIME2     NOT NULL CONSTRAINT DF_ALERT_EVENT_CASE_CREATED_AT DEFAULT (GETDATE()),
        UPDATED_AT         DATETIME2     NULL
    );
    CREATE UNIQUE INDEX UX_AEC_ACTIVE ON ALERTEVENTCASES.ALERT_EVENT_CASE (SCENARIO_ID, STUDENT_ID)
        WHERE STATUS IN (N'OPEN', N'ACKNOWLEDGED') AND IS_DELETED = 0;
    CREATE INDEX IX_AEC_COUNTRY  ON ALERTEVENTCASES.ALERT_EVENT_CASE (STUDENT_COUNTRY_ID);
    CREATE INDEX IX_AEC_STATUS   ON ALERTEVENTCASES.ALERT_EVENT_CASE (STATUS);
    CREATE INDEX IX_AEC_STUDENT  ON ALERTEVENTCASES.ALERT_EVENT_CASE (STUDENT_ID);
    CREATE INDEX IX_AEC_SCENARIO ON ALERTEVENTCASES.ALERT_EVENT_CASE (SCENARIO_ID);
    PRINT 'V066: created ALERTEVENTCASES.ALERT_EVENT_CASE with its five indexes';
END
GO

DECLARE @perms TABLE (code varchar(50), name_en nvarchar(200), name_ar nvarchar(200), descr nvarchar(500), action_type varchar(20));
INSERT INTO @perms VALUES
    ('ALERT_EVENTS.VIEW',      N'View Alert Cases',       N'عرض حالات التنبيه',         N'View student alert event cases',                                            'VIEW'),
    ('ALERT_EVENTS.MANAGE',    N'Manage Alert Cases',     N'إدارة حالات التنبيه',       N'Acknowledge, resolve, and dismiss alert event cases',                       'EDIT'),
    ('ALERT_SCENARIOS.MANAGE', N'Manage Alert Scenarios', N'إدارة سيناريوهات التنبيه', N'Configure alert scenario thresholds and enable/disable scenarios',          'EDIT');
INSERT INTO UMSCORE.Permissions (PermissionCode, PermissionName, PermissionNameAr, Description, ResourceType, ActionType, PermissionLevel, IsActive, CreatedAt)
SELECT p.code, p.name_en, p.name_ar, p.descr, 'PAGE', p.action_type, 'READ', 1, SYSUTCDATETIME()
  FROM @perms p
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions e WHERE e.PermissionCode = p.code);
PRINT 'V066: created ' + CAST(@@ROWCOUNT AS VARCHAR(10)) + ' alert-event permission(s) (0 means already present)';
GO

/* ── 2. The trigger into a database that does not exist, and AUDIT_LOG ───── */
IF OBJECT_ID('MOHECORE.tr_TRG_AUDIT_MOHECORE_AUDIT_LOG_UPDATE_log', 'TR') IS NOT NULL
BEGIN
    DROP TRIGGER MOHECORE.tr_TRG_AUDIT_MOHECORE_AUDIT_LOG_UPDATE_log;
    PRINT 'V066: dropped trigger tr_TRG_AUDIT_MOHECORE_AUDIT_LOG_UPDATE_log (called [MOHE_LOG].[MOHELOGCORE].[SP_INSERT_AUDIT_LOG])';
END
ELSE PRINT 'V066: trigger tr_TRG_AUDIT_MOHECORE_AUDIT_LOG_UPDATE_log already gone';
GO

IF OBJECT_ID('MOHECORE.AUDIT_LOG', 'U') IS NULL
    PRINT 'V066: MOHECORE.AUDIT_LOG already gone';
ELSE
BEGIN
    DECLARE @rows BIGINT = (SELECT SUM(p.rows) FROM sys.partitions p WHERE p.object_id = OBJECT_ID('MOHECORE.AUDIT_LOG') AND p.index_id IN (0, 1));
    DECLARE @fks INT = (SELECT COUNT(*) FROM sys.foreign_keys WHERE referenced_object_id = OBJECT_ID('MOHECORE.AUDIT_LOG'));
    IF @rows > 0 OR @fks > 0
        PRINT 'V066: *** MOHECORE.AUDIT_LOG kept — rows: ' + CAST(@rows AS VARCHAR(20)) + ', foreign keys pointing at it: ' + CAST(@fks AS VARCHAR(10));
    ELSE
    BEGIN
        IF OBJECT_ID('MOHECORE.VW_AUDIT_LOG_SUMMARY', 'V') IS NOT NULL
           AND OBJECT_DEFINITION(OBJECT_ID('MOHECORE.VW_AUDIT_LOG_SUMMARY')) LIKE '%AUDIT_LOG%'
        BEGIN
            DROP VIEW MOHECORE.VW_AUDIT_LOG_SUMMARY;
            PRINT 'V066: dropped MOHECORE.VW_AUDIT_LOG_SUMMARY (selected from AUDIT_LOG; denied to the query builder, read by nothing)';
        END
        IF OBJECT_ID('MOHECORE.SP_GET_AUDIT_LOGS', 'P') IS NOT NULL
           AND OBJECT_DEFINITION(OBJECT_ID('MOHECORE.SP_GET_AUDIT_LOGS')) LIKE '%AUDIT_LOG%'
        BEGIN
            DROP PROCEDURE MOHECORE.SP_GET_AUDIT_LOGS;
            PRINT 'V066: dropped MOHECORE.SP_GET_AUDIT_LOGS (read AUDIT_LOG; called by nothing)';
        END
        DROP TABLE MOHECORE.AUDIT_LOG;
        PRINT 'V066: dropped MOHECORE.AUDIT_LOG (0 rows, no foreign key, no entity)';
    END
END
GO

/* ── 3. Orphan sequences ──────────────────────────────────────────────────── */
IF OBJECT_ID('dbo.student_country_id_sequence', 'SO') IS NOT NULL
BEGIN DROP SEQUENCE dbo.student_country_id_sequence; PRINT 'V066: dropped dbo.student_country_id_sequence'; END
ELSE PRINT 'V066: dbo.student_country_id_sequence already gone';
IF OBJECT_ID('dbo.student_data_id_sequence', 'SO') IS NOT NULL
BEGIN DROP SEQUENCE dbo.student_data_id_sequence; PRINT 'V066: dropped dbo.student_data_id_sequence'; END
ELSE PRINT 'V066: dbo.student_data_id_sequence already gone';
GO

/* ── 4. Duplicate indexes ─────────────────────────────────────────────────── */
PRINT '';
PRINT '--- 4. duplicate indexes (computed from sys.indexes) ---';
IF OBJECT_ID('tempdb..#ix') IS NOT NULL DROP TABLE #ix;
SELECT i.object_id, i.index_id, i.name, i.is_unique, i.is_primary_key, i.is_unique_constraint, i.type,
       OBJECT_SCHEMA_NAME(i.object_id) AS schema_name, OBJECT_NAME(i.object_id) AS table_name,
       ISNULL(i.filter_definition, '') AS flt,
       ISNULL((SELECT ',' + CAST(ic.column_id AS VARCHAR(10)) + CASE WHEN ic.is_descending_key = 1 THEN 'D' ELSE '' END
                 FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
                ORDER BY ic.key_ordinal FOR XML PATH('')), '') AS keys_,
       ISNULL((SELECT ',' + CAST(ic.column_id AS VARCHAR(10))
                 FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1
                ORDER BY ic.column_id FOR XML PATH('')), '') AS incl,
       ISNULL((SELECT '_' + c.name
                 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
                ORDER BY ic.key_ordinal FOR XML PATH('')), '') AS key_names
  INTO #ix
  FROM sys.indexes i JOIN sys.tables t ON t.object_id = i.object_id
 WHERE i.type IN (1, 2) AND i.is_hypothetical = 0 AND t.is_ms_shipped = 0;

/* rank 1 = keep */
IF OBJECT_ID('tempdb..#ranked') IS NOT NULL DROP TABLE #ranked;
SELECT x.*,
       ROW_NUMBER() OVER (PARTITION BY x.object_id, x.keys_, x.incl, x.flt
                          ORDER BY CASE WHEN (x.is_primary_key = 1 OR x.is_unique_constraint = 1) AND x.name NOT LIKE '%[_][_]%' THEN 1
                                        WHEN  x.is_primary_key = 1 OR x.is_unique_constraint = 1 THEN 2
                                        WHEN  x.is_unique = 1 AND x.name NOT LIKE '%[_][_]%' THEN 3
                                        WHEN  x.is_unique = 1 THEN 4
                                        WHEN  x.name = 'IX_' + x.table_name + x.key_names THEN 5
                                        WHEN  x.name LIKE 'IX[_]' + x.table_name + '[_]%' THEN 6
                                        ELSE 7 END, x.index_id) AS rk,
       COUNT(*) OVER (PARTITION BY x.object_id, x.keys_, x.incl, x.flt) AS members
  INTO #ranked FROM #ix x;

DECLARE @sch SYSNAME, @tbl SYSNAME, @ix SYSNAME, @isc BIT, @typ TINYINT, @oid INT, @iid INT, @keep SYSNAME, @sql NVARCHAR(MAX), @dropped INT = 0;
DECLARE dup CURSOR LOCAL FAST_FORWARD FOR
    SELECT r.schema_name, r.table_name, r.name, r.is_unique_constraint | r.is_primary_key, r.type, r.object_id, r.index_id,
           (SELECT k.name FROM #ranked k WHERE k.object_id = r.object_id AND k.keys_ = r.keys_ AND k.incl = r.incl AND k.flt = r.flt AND k.rk = 1)
      FROM #ranked r WHERE r.members > 1 AND r.rk > 1
     ORDER BY r.schema_name, r.table_name, r.name;
OPEN dup; FETCH NEXT FROM dup INTO @sch, @tbl, @ix, @isc, @typ, @oid, @iid, @keep;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF @typ = 1
        PRINT 'V066: kept ' + @sch + '.' + @tbl + '.' + @ix + ' — clustered; drop by hand if it is really redundant with ' + @keep;
    ELSE IF EXISTS (SELECT 1 FROM sys.foreign_keys fk WHERE fk.referenced_object_id = @oid AND fk.key_index_id = @iid)
        PRINT 'V066: kept ' + @sch + '.' + @tbl + '.' + @ix + ' — a foreign key references it (repoint the key to ' + @keep + ' first)';
    ELSE
    BEGIN
        SET @sql = CASE WHEN @isc = 1
                        THEN N'ALTER TABLE ' + QUOTENAME(@sch) + N'.' + QUOTENAME(@tbl) + N' DROP CONSTRAINT ' + QUOTENAME(@ix) + N';'
                        ELSE N'DROP INDEX ' + QUOTENAME(@ix) + N' ON ' + QUOTENAME(@sch) + N'.' + QUOTENAME(@tbl) + N';' END;
        EXEC sp_executesql @sql;
        SET @dropped += 1;
        PRINT 'V066: dropped ' + @sch + '.' + @tbl + '.' + @ix + ' (duplicate of ' + @keep + ')';
    END
    FETCH NEXT FROM dup INTO @sch, @tbl, @ix, @isc, @typ, @oid, @iid, @keep;
END
CLOSE dup; DEALLOCATE dup;
IF @dropped = 0 PRINT 'V066: no exact duplicate index pairs (already clean)';

/* Near-pairs the exact rule does not see. */
IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('UMSCORE.Users') AND name = 'UQ_UMSCORE_USERS_EMAIL' AND is_unique = 1)
BEGIN
    SELECT @ix = kc.name FROM sys.key_constraints kc
      JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
     WHERE kc.parent_object_id = OBJECT_ID('UMSCORE.Users') AND kc.type = 'UQ' AND kc.name LIKE 'UQ[_][_]Users[_][_]%'
       AND COL_NAME(ic.object_id, ic.column_id) = 'Email'
       AND (SELECT COUNT(*) FROM sys.index_columns ic2 WHERE ic2.object_id = kc.parent_object_id AND ic2.index_id = kc.unique_index_id) = 1;
    IF @ix IS NULL
        PRINT 'V066: UMSCORE.Users — no system-named unique constraint on Email left (already clean)';
    ELSE IF EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.key_constraints kc ON kc.name = @ix AND kc.parent_object_id = OBJECT_ID('UMSCORE.Users')
                     WHERE fk.referenced_object_id = OBJECT_ID('UMSCORE.Users') AND fk.key_index_id = kc.unique_index_id)
        PRINT 'V066: kept UMSCORE.Users.' + @ix + ' — a foreign key references it';
    ELSE
    BEGIN
        SET @sql = N'ALTER TABLE UMSCORE.Users DROP CONSTRAINT ' + QUOTENAME(@ix) + N';';
        EXEC sp_executesql @sql;
        PRINT 'V066: dropped UMSCORE.Users.' + @ix + ' (system-named; UQ_UMSCORE_USERS_EMAIL covers Email, its IS NOT NULL filter is moot on a NOT NULL column)';
    END
END
SET @ix = NULL;
IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('FINCORE.FX_RATE') AND name = 'IX_FX_RATE_LOOKUP')
   AND EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('FINCORE.FX_RATE') AND name = 'UX_FX_RATE_PAIR_DATE' AND is_unique = 1)
   AND (SELECT ISNULL((SELECT ',' + CAST(ic.column_id AS VARCHAR(10)) FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                       WHERE i.object_id = OBJECT_ID('FINCORE.FX_RATE') AND i.name = 'IX_FX_RATE_LOOKUP' ORDER BY ic.key_ordinal FOR XML PATH('')), ''))
     = (SELECT ISNULL((SELECT ',' + CAST(ic.column_id AS VARCHAR(10)) FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                       WHERE i.object_id = OBJECT_ID('FINCORE.FX_RATE') AND i.name = 'UX_FX_RATE_PAIR_DATE' ORDER BY ic.key_ordinal FOR XML PATH('')), ''))
BEGIN
    DROP INDEX IX_FX_RATE_LOOKUP ON FINCORE.FX_RATE;
    PRINT 'V066: dropped FINCORE.FX_RATE.IX_FX_RATE_LOOKUP (same key columns as UX_FX_RATE_PAIR_DATE; RATE_DATE DESC is served by a backward scan)';
END
ELSE PRINT 'V066: FINCORE.FX_RATE.IX_FX_RATE_LOOKUP already gone or not redundant';

/* Sanity: exactly one index on STUDENT_DATA (STUDENT_COUNTRY_ID). */
DECLARE @n INT = (SELECT COUNT(*) FROM sys.indexes i
                   WHERE i.object_id = OBJECT_ID('MOHECORE.STUDENT_DATA') AND i.type = 2
                     AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id) = 1
                     AND EXISTS (SELECT 1 FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                                    AND COL_NAME(ic.object_id, ic.column_id) = 'STUDENT_COUNTRY_ID' AND ic.key_ordinal = 1));
PRINT 'V066: single-column indexes on STUDENT_DATA.STUDENT_COUNTRY_ID now: ' + CAST(@n AS VARCHAR(5)) + CASE WHEN @n = 1 THEN ' (expected)' ELSE ' *** expected 1' END;
DROP TABLE #ranked; DROP TABLE #ix;
GO

/* ── 5. Foreign keys, trusted, only when the data allows ──────────────────── */
PRINT '';
PRINT '--- 5. foreign keys ---';
DECLARE @fk TABLE (fk_name SYSNAME, sch SYSNAME, tbl SYSNAME, col SYSNAME, rsch SYSNAME, rtbl SYSNAME, rcol SYSNAME);
INSERT INTO @fk VALUES
    ('FK_ApprovalHistory_ActionBy',      'ALERTCORE', 'ApprovalHistory',  'ActionBy',       'UMSCORE', 'Users',     'Id'),
    ('FK_ApprovalLevels_RequiredRole',   'ALERTCORE', 'ApprovalLevels',   'RequiredRoleId', 'UMSCORE', 'Roles',     'Id'),
    ('FK_ApprovalRequests_RequestedBy',  'ALERTCORE', 'ApprovalRequests', 'RequestedBy',    'UMSCORE', 'Users',     'Id'),
    ('FK_ApprovalSteps_ApprovedBy',      'ALERTCORE', 'ApprovalSteps',    'ApprovedBy',     'UMSCORE', 'Users',     'Id'),
    ('FK_Permissions_Resource',          'UMSCORE',   'Permissions',      'ResourceId',     'UMSCORE', 'Resources', 'Id');
DECLARE @fkn SYSNAME, @s SYSNAME, @t SYSNAME, @c SYSNAME, @rs SYSNAME, @rt SYSNAME, @rc SYSNAME, @orphans INT, @sql NVARCHAR(MAX);
DECLARE fkc CURSOR LOCAL FAST_FORWARD FOR SELECT fk_name, sch, tbl, col, rsch, rtbl, rcol FROM @fk;
OPEN fkc; FETCH NEXT FROM fkc INTO @fkn, @s, @t, @c, @rs, @rt, @rc;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = @fkn)
        PRINT 'V066: ' + @fkn + ' already present';
    ELSE IF COL_LENGTH(@s + '.' + @t, @c) IS NULL OR COL_LENGTH(@rs + '.' + @rt, @rc) IS NULL
        PRINT 'V066: ' + @fkn + ' skipped — column absent';
    ELSE
    BEGIN
        SET @sql = N'SELECT @o = COUNT(*) FROM ' + QUOTENAME(@s) + N'.' + QUOTENAME(@t) + N' x WHERE x.' + QUOTENAME(@c) + N' IS NOT NULL AND NOT EXISTS (SELECT 1 FROM '
                 + QUOTENAME(@rs) + N'.' + QUOTENAME(@rt) + N' r WHERE r.' + QUOTENAME(@rc) + N' = x.' + QUOTENAME(@c) + N')';
        EXEC sp_executesql @sql, N'@o INT OUTPUT', @o = @orphans OUTPUT;
        IF @orphans > 0
            PRINT 'V066: *** ' + @fkn + ' skipped — ' + CAST(@orphans AS VARCHAR(10)) + ' row(s) of ' + @s + '.' + @t + '.' + @c + ' point at no ' + @rt + ' row';
        ELSE
        BEGIN
            SET @sql = N'ALTER TABLE ' + QUOTENAME(@s) + N'.' + QUOTENAME(@t) + N' WITH CHECK ADD CONSTRAINT ' + QUOTENAME(@fkn)
                     + N' FOREIGN KEY (' + QUOTENAME(@c) + N') REFERENCES ' + QUOTENAME(@rs) + N'.' + QUOTENAME(@rt) + N' (' + QUOTENAME(@rc) + N');';
            EXEC sp_executesql @sql;
            PRINT 'V066: created ' + @fkn;
        END
    END
    FETCH NEXT FROM fkc INTO @fkn, @s, @t, @c, @rs, @rt, @rc;
END
CLOSE fkc; DEALLOCATE fkc;
GO

/* ── 6. A primary key on MOHECORE_ERR.error_log ───────────────────────────── */
IF OBJECT_ID('MOHECORE_ERR.error_log', 'U') IS NULL
    PRINT 'V066: MOHECORE_ERR.error_log absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID('MOHECORE_ERR.error_log') AND type = 'PK')
    PRINT 'V066: MOHECORE_ERR.error_log already has a primary key';
ELSE
BEGIN
    IF COL_LENGTH('MOHECORE_ERR.error_log', 'error_log_id') IS NULL
        ALTER TABLE MOHECORE_ERR.error_log ADD error_log_id INT IDENTITY(1, 1) NOT NULL;
    ALTER TABLE MOHECORE_ERR.error_log ADD CONSTRAINT PK_error_log PRIMARY KEY CLUSTERED (error_log_id);
    PRINT 'V066: MOHECORE_ERR.error_log — added error_log_id INT IDENTITY and PK_error_log';
END
GO

/* ── 7. Indexes on the FINCORE predicate columns ──────────────────────────── */
IF COL_LENGTH('FINCORE.PAYMENT_BATCH_LINE', 'STUDY_COUNTRY_IN_ID') IS NULL
    PRINT 'V066: PAYMENT_BATCH_LINE.STUDY_COUNTRY_IN_ID absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('FINCORE.PAYMENT_BATCH_LINE') AND name = 'IX_PAYMENT_BATCH_LINE_STUDY_COUNTRY_IN_ID')
    PRINT 'V066: IX_PAYMENT_BATCH_LINE_STUDY_COUNTRY_IN_ID already exists';
ELSE
BEGIN
    CREATE INDEX IX_PAYMENT_BATCH_LINE_STUDY_COUNTRY_IN_ID ON FINCORE.PAYMENT_BATCH_LINE (STUDY_COUNTRY_IN_ID) INCLUDE (BATCH_ID, STUDENT_ID);
    PRINT 'V066: created IX_PAYMENT_BATCH_LINE_STUDY_COUNTRY_IN_ID';
END
IF COL_LENGTH('FINCORE.COA_ACCOUNT', 'STUDY_COUNTRY_IN_ID') IS NULL
    PRINT 'V066: COA_ACCOUNT.STUDY_COUNTRY_IN_ID absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('FINCORE.COA_ACCOUNT') AND name = 'IX_COA_ACCOUNT_STUDY_COUNTRY_IN_ID')
    PRINT 'V066: IX_COA_ACCOUNT_STUDY_COUNTRY_IN_ID already exists';
ELSE
BEGIN
    CREATE INDEX IX_COA_ACCOUNT_STUDY_COUNTRY_IN_ID ON FINCORE.COA_ACCOUNT (STUDY_COUNTRY_IN_ID);
    PRINT 'V066: created IX_COA_ACCOUNT_STUDY_COUNTRY_IN_ID';
END
GO

/* ── 8. NOT NULL where the entities assume it ─────────────────────────────────
 * The column's type is rebuilt from sys.columns so the ALTER restates exactly
 * what is there. Nonclustered indexes on the column are scripted, dropped and
 * recreated around the ALTER — SQL Server refuses to change the nullability of
 * an indexed int column otherwise (Msg 5074).
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '--- 8. NOT NULL ---';
DECLARE @cols TABLE (sch SYSNAME, tbl SYSNAME, col SYSNAME, is_ts BIT);
INSERT INTO @cols VALUES
    ('UMSCORE',    'Users',                    'PasswordHash',   0),
    ('UMSCORE',    'Permissions',              'PermissionCode', 0),
    ('UMSCORE',    'Permissions',              'ResourceType',   0),
    ('UMSCORE',    'Permissions',              'ActionType',     0),
    ('MOHECORE',   'STUDY_UNIVERSITY_FEE_ARCH','STUDENT_ID',     0),
    ('MOHENTracer','STUDENT_NODE',             'STUDENT_ID',     0),
    -- entity nullable = false, column nullable (compared 27 Aug 2026)
    ('UMSCORE',    'Users',                    'CreatedAt',      1),
    ('UMSCORE',    'Roles',                    'CreatedAt',      1),
    ('UMSCORE',    'Permissions',              'CreatedAt',      1),
    ('UMSCORE',    'UserGroups',               'CreatedAt',      1),
    ('UMSCORE',    'UserActions',              'CreatedAt',      1),
    ('UMSCORE',    'UserRoles',                'AssignedAt',     1),
    ('UMSCORE',    'GroupRoles',               'AssignedAt',     1),
    ('UMSCORE',    'UserGroupMemberships',     'AssignedAt',     1),
    ('UMSCORE',    'RolePermissions',          'GrantedAt',      1);

DECLARE @s SYSNAME, @t SYSNAME, @c SYSNAME, @ts BIT, @nulls INT, @sql NVARCHAR(MAX), @typ NVARCHAR(200), @oid INT, @colid INT, @recreate NVARCHAR(MAX), @drops NVARCHAR(MAX);
DECLARE nn CURSOR LOCAL FAST_FORWARD FOR SELECT sch, tbl, col, is_ts FROM @cols;
OPEN nn; FETCH NEXT FROM nn INTO @s, @t, @c, @ts;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @oid = OBJECT_ID(@s + '.' + @t, 'U');
    IF @oid IS NULL OR COL_LENGTH(@s + '.' + @t, @c) IS NULL
        PRINT 'V066: ' + @s + '.' + @t + '.' + @c + ' absent — skipped';
    ELSE IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = @oid AND name = @c AND is_nullable = 0)
        PRINT 'V066: ' + @s + '.' + @t + '.' + @c + ' already NOT NULL';
    ELSE
    BEGIN
        SET @sql = N'SELECT @o = COUNT(*) FROM ' + QUOTENAME(@s) + N'.' + QUOTENAME(@t) + N' WHERE ' + QUOTENAME(@c) + N' IS NULL';
        EXEC sp_executesql @sql, N'@o INT OUTPUT', @o = @nulls OUTPUT;
        IF @nulls > 0
            PRINT 'V066: *** ' + @s + '.' + @t + '.' + @c + ' left nullable — ' + CAST(@nulls AS VARCHAR(10)) + ' NULL row(s)';
        ELSE
        BEGIN
            SELECT @colid = c.column_id,
                   @typ = CASE
                            WHEN ty.name IN ('nvarchar', 'nchar') THEN ty.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length / 2 AS VARCHAR(10)) END + ')'
                            WHEN ty.name IN ('varchar', 'char', 'varbinary', 'binary') THEN ty.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length AS VARCHAR(10)) END + ')'
                            WHEN ty.name IN ('decimal', 'numeric') THEN ty.name + '(' + CAST(c.precision AS VARCHAR(5)) + ',' + CAST(c.scale AS VARCHAR(5)) + ')'
                            WHEN ty.name IN ('datetime2', 'time', 'datetimeoffset') THEN ty.name + '(' + CAST(c.scale AS VARCHAR(5)) + ')'
                            ELSE ty.name END
                        + CASE WHEN c.collation_name IS NOT NULL THEN ' COLLATE ' + c.collation_name ELSE '' END
              FROM sys.columns c JOIN sys.types ty ON ty.user_type_id = c.user_type_id
             WHERE c.object_id = @oid AND c.name = @c;

            /* Script the nonclustered indexes that touch the column. */
            SET @drops = N''; SET @recreate = N'';
            SELECT @drops = @drops + N'DROP INDEX ' + QUOTENAME(i.name) + N' ON ' + QUOTENAME(@s) + N'.' + QUOTENAME(@t) + N';' + NCHAR(10),
                   @recreate = @recreate + N'CREATE ' + CASE WHEN i.is_unique = 1 THEN N'UNIQUE ' ELSE N'' END + N'NONCLUSTERED INDEX ' + QUOTENAME(i.name)
                       + N' ON ' + QUOTENAME(@s) + N'.' + QUOTENAME(@t) + N' ('
                       + STUFF((SELECT N', ' + QUOTENAME(COL_NAME(ic.object_id, ic.column_id)) + CASE WHEN ic.is_descending_key = 1 THEN N' DESC' ELSE N'' END
                                  FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
                                 ORDER BY ic.key_ordinal FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'') + N')'
                       + ISNULL(N' INCLUDE (' + STUFF((SELECT N', ' + QUOTENAME(COL_NAME(ic.object_id, ic.column_id))
                                  FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1
                                 ORDER BY ic.column_id FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'') + N')', N'')
                       + ISNULL(N' WHERE ' + i.filter_definition, N'') + N';' + NCHAR(10)
              FROM sys.indexes i
             WHERE i.object_id = @oid AND i.type = 2 AND i.is_primary_key = 0 AND i.is_unique_constraint = 0
               AND EXISTS (SELECT 1 FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.column_id = @colid);

            IF EXISTS (SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                        WHERE i.object_id = @oid AND ic.column_id = @colid AND (i.is_primary_key = 1 OR i.is_unique_constraint = 1 OR i.type = 1))
                PRINT 'V066: *** ' + @s + '.' + @t + '.' + @c + ' left nullable — it is part of a key constraint or the clustered index; change it by hand';
            ELSE
            BEGIN
                SET @sql = @drops
                         + N'ALTER TABLE ' + QUOTENAME(@s) + N'.' + QUOTENAME(@t) + N' ALTER COLUMN ' + QUOTENAME(@c) + N' ' + @typ + N' NOT NULL;' + NCHAR(10)
                         + @recreate;
                EXEC sp_executesql @sql;
                PRINT 'V066: ' + @s + '.' + @t + '.' + @c + ' is now ' + @typ + ' NOT NULL' + CASE WHEN @drops <> N'' THEN ' (indexes recreated)' ELSE '' END;
            END
        END
        IF @ts = 1 AND NOT EXISTS (SELECT 1 FROM sys.default_constraints d WHERE d.parent_object_id = @oid AND d.parent_column_id = COLUMNPROPERTY(@oid, @c, 'ColumnId'))
        BEGIN
            SET @sql = N'ALTER TABLE ' + QUOTENAME(@s) + N'.' + QUOTENAME(@t) + N' ADD CONSTRAINT ' + QUOTENAME('DF_' + @t + '_' + @c) + N' DEFAULT (SYSUTCDATETIME()) FOR ' + QUOTENAME(@c) + N';';
            EXEC sp_executesql @sql;
            PRINT 'V066: added DF_' + @t + '_' + @c + ' DEFAULT SYSUTCDATETIME()';
        END
    END
    FETCH NEXT FROM nn INTO @s, @t, @c, @ts;
END
CLOSE nn; DEALLOCATE nn;
GO

/* ── 9. Collation, FullName width, FINCORE.UPDATED_AT ─────────────────────── */
PRINT '';
PRINT '--- 9. collation / widths / UPDATED_AT ---';
DECLARE @dbcoll NVARCHAR(128) = CAST(DATABASEPROPERTYEX(DB_NAME(), 'Collation') AS NVARCHAR(128)), @sql NVARCHAR(MAX);
IF COL_LENGTH('MOHECORE.STUDY_STATUS', 'status_name') IS NULL
    PRINT 'V066: STUDY_STATUS.status_name absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('MOHECORE.STUDY_STATUS') AND name = 'status_name' AND collation_name = @dbcoll)
    PRINT 'V066: STUDY_STATUS.status_name already on ' + @dbcoll;
ELSE IF EXISTS (SELECT 1 FROM sys.index_columns ic WHERE ic.object_id = OBJECT_ID('MOHECORE.STUDY_STATUS') AND COL_NAME(ic.object_id, ic.column_id) = 'status_name')
    PRINT 'V066: *** STUDY_STATUS.status_name left as is — it is indexed; drop the index first';
ELSE
BEGIN
    SET @sql = N'ALTER TABLE MOHECORE.STUDY_STATUS ALTER COLUMN status_name NVARCHAR(200) COLLATE ' + @dbcoll + N' NULL;';
    EXEC sp_executesql @sql;
    PRINT 'V066: STUDY_STATUS.status_name now collates ' + @dbcoll + ' (was Arabic_CI_AS)';
END

IF COL_LENGTH('UMSCORE.Users', 'FullName') IS NULL
    PRINT 'V066: Users.FullName absent — skipped';
ELSE IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('UMSCORE.Users') AND name = 'FullName' AND max_length = 400)
    PRINT 'V066: Users.FullName already NVARCHAR(200)';
ELSE
BEGIN
    DECLARE @maxlen INT = (SELECT ISNULL(MAX(LEN(FullName)), 0) FROM UMSCORE.Users);
    IF @maxlen > 200
        PRINT 'V066: *** Users.FullName left NVARCHAR(MAX) — longest value is ' + CAST(@maxlen AS VARCHAR(10)) + ' characters';
    ELSE
    BEGIN
        ALTER TABLE UMSCORE.Users ALTER COLUMN FullName NVARCHAR(200) NULL;
        PRINT 'V066: Users.FullName is now NVARCHAR(200) (longest value: ' + CAST(@maxlen AS VARCHAR(10)) + ')';
    END
END

DECLARE @t SYSNAME;
DECLARE ua CURSOR LOCAL FAST_FORWARD FOR
    SELECT t.name FROM sys.tables t
     WHERE SCHEMA_NAME(t.schema_id) = 'FINCORE'
       AND NOT EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'UPDATED_AT')
     ORDER BY t.name;
OPEN ua; FETCH NEXT FROM ua INTO @t;
IF @@FETCH_STATUS <> 0 PRINT 'V066: every FINCORE table already has UPDATED_AT';
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'ALTER TABLE FINCORE.' + QUOTENAME(@t) + N' ADD UPDATED_AT DATETIME2 NULL;';
    EXEC sp_executesql @sql;
    PRINT 'V066: added FINCORE.' + @t + '.UPDATED_AT';
    FETCH NEXT FROM ua INTO @t;
END
CLOSE ua; DEALLOCATE ua;
GO

/* ═════════════════════════════════════════════════════════════════════════════
 * 10. DROP THE FIVE DEAD REPORT VIEWS
 *
 *   V_Academic_Year_Report, V_Fiscal_Year_Report, V_Custom_Period_Report,
 *   V_Custom_Filter_Report, V_Predictive_Analytics
 *
 * AcademicYearReportRepository / FiscalYearReportRepository query with native
 * SQL and PredictiveAnalyticsService no longer reads V_Predictive_Analytics;
 * the entities that still carry @Table on these names are being converted to
 * @Subselect in parallel. V_Custom_Filter_Report is dropped first because it
 * selects from V_Student_List_Detailed, which step 11 rebuilds.
 * ═══════════════════════════════════════════════════════════════════════════ */
PRINT '';
PRINT '--- 10. dead views ---';
DECLARE @v SYSNAME, @sql NVARCHAR(MAX);
DECLARE dv CURSOR LOCAL FAST_FORWARD FOR
    SELECT v FROM (VALUES ('V_Custom_Filter_Report'), ('V_Academic_Year_Report'), ('V_Fiscal_Year_Report'),
                          ('V_Custom_Period_Report'), ('V_Predictive_Analytics')) x(v);
OPEN dv; FETCH NEXT FROM dv INTO @v;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID('MOHECORE.' + @v, 'V') IS NULL
        PRINT 'V066: MOHECORE.' + @v + ' already gone';
    ELSE
    BEGIN
        SET @sql = N'DROP VIEW MOHECORE.' + QUOTENAME(@v) + N';';
        EXEC sp_executesql @sql;
        PRINT 'V066: dropped MOHECORE.' + @v;
    END
    FETCH NEXT FROM dv INTO @v;
END
CLOSE dv; DEALLOCATE dv;
GO

/* ── 11. V_Academic_Performance / V_Student_List_Detailed, typed ──────────────
 * Bodies as stored on 27 Aug 2026 (V030), with only GPA and the three dates
 * changed. is_graduated is still the V030 expression — see V030's note.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Academic_Performance AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,   -- tenant key
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    sd.STUDENT_COUNTRY_ID,                                          -- nationality
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN 1 ELSE 0 END AS is_graduated,
    CAST(NULL AS DECIMAL(4, 2)) AS GPA
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_MAJOR         sm   ON sm.STUDENT_MAJOR_ID          = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL scl  ON scl.STUDENT_CURRENT_LEVEL_ID = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS  sss  ON sss.STUDENT_STUDY_STATUS_ID  = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE        sdeg ON sdeg.STUDENT_DEGREE_ID       = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY       sc   ON sc.STUDENT_COUNTRY_ID        = sd.STUDENT_COUNTRY_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO
PRINT 'V066: V_Academic_Performance rebuilt (GPA DECIMAL(4,2))';
GO

CREATE OR ALTER VIEW MOHECORE.V_Student_List_Detailed AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDENT_PASSPORT_NUMBER,
    CAST(sd.STUDENT_BIRTHDATE AS DATE)   AS STUDENT_BIRTHDATE,
    TRY_CAST(sd.STUDENT_AGE AS INT)      AS student_age,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,   -- tenant key
    sd.STUDENT_COUNTRY_ID,                                          -- nationality
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(sg.STUDENT_GENDER_NAME, N'')  AS STUDENT_GENDER,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    sd.STUDENT_STUDY_TYPE_ID,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    sd.STUDENT_UNIVERSITY_TYPE_ID,
    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'') AS STUDENT_UNIVERSITY_TYPE_NAME,
    sd.STUDENT_AGENCY_TYPE_ID,
    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'') AS STUDENT_AGENCY_TYPE_NAME,
    sd.STUDENT_MINISTRY_TYPE_ID,
    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'') AS STUDENT_MINISTRY_TYPE_NAME,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_DELEGATION_DECISION_YEAR,
    sd.STUDENT_ADDED_QUARTER,
    CAST(sd.STUDENT_PASSPORT_ISSUE_DATE AS DATE)  AS STUDENT_PASSPORT_ISSUE_DATE,
    CAST(sd.STUDENT_PASSPORT_EXPIRE_DATE AS DATE) AS STUDENT_PASSPORT_EXPIRE_DATE,
    CAST(NULL AS DECIMAL(4, 2)) AS GPA
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_COUNTRY          sc   ON sc.STUDENT_COUNTRY_ID          = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_GENDER           sg   ON sg.STUDENT_GENDER_ID           = sd.STUDENT_GENDER
LEFT JOIN MOHECORE.STUDENT_MAJOR            sm   ON sm.STUDENT_MAJOR_ID            = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE           sdeg ON sdeg.STUDENT_DEGREE_ID         = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL    scl  ON scl.STUDENT_CURRENT_LEVEL_ID   = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE       sst  ON sst.STUDENT_STUDY_TYPE_ID      = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS     sss  ON sss.STUDENT_STUDY_STATUS_ID    = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE  sut  ON sut.STUDENT_UNIVERSITY_TYPE_ID = sd.STUDENT_UNIVERSITY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE      sat  ON sat.STUDENT_AGENCY_TYPE_ID     = sd.STUDENT_AGENCY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE    smt  ON smt.STUDENT_MINISTRY_TYPE_ID   = sd.STUDENT_MINISTRY_TYPE_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO
PRINT 'V066: V_Student_List_Detailed rebuilt (GPA DECIMAL(4,2); birth date and passport dates as DATE)';
GO

/* ── 12. UMSCORE.Users.LastLogin ──────────────────────────────────────────────
 * The populated-row count is hoisted into a variable first: a subquery inside
 * PRINT is a compile error that takes the whole batch with it (V048's header).
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @populated INT = 0, @sqlLL NVARCHAR(200);
IF COL_LENGTH('UMSCORE.Users', 'LastLogin') IS NOT NULL
BEGIN
    SET @sqlLL = N'SELECT @o = COUNT(*) FROM UMSCORE.Users WHERE LastLogin IS NOT NULL';
    EXEC sp_executesql @sqlLL, N'@o INT OUTPUT', @o = @populated OUTPUT;
END
IF COL_LENGTH('UMSCORE.Users', 'LastLogin') IS NULL
    PRINT 'V066: Users.LastLogin already gone';
ELSE IF EXISTS (SELECT 1 FROM sys.sql_modules m WHERE m.definition LIKE '%[^A-Za-z0-9_]LastLogin[^A-Za-z0-9_]%')
    PRINT 'V066: *** Users.LastLogin kept — a module still names it (check sys.sql_modules for [^A-Za-z0-9_]LastLogin[^A-Za-z0-9_])';
ELSE IF @populated > 0
    PRINT 'V066: *** Users.LastLogin kept — it holds values on ' + CAST(@populated AS VARCHAR(10)) + ' row(s)';
ELSE
BEGIN
    DECLARE @df SYSNAME = (SELECT d.name FROM sys.default_constraints d
                            WHERE d.parent_object_id = OBJECT_ID('UMSCORE.Users') AND d.parent_column_id = COLUMNPROPERTY(OBJECT_ID('UMSCORE.Users'), 'LastLogin', 'ColumnId'));
    IF @df IS NOT NULL EXEC (N'ALTER TABLE UMSCORE.Users DROP CONSTRAINT ' + @df + N';');
    ALTER TABLE UMSCORE.Users DROP COLUMN LastLogin;
    PRINT 'V066: dropped Users.LastLogin (the entity maps LastLoginAt)';
END
GO

/* ── Summary ──────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== V066 summary ===';
SELECT 'ALERTEVENTCASES.ALERT_EVENT_CASE' AS object_, CASE WHEN OBJECT_ID('ALERTEVENTCASES.ALERT_EVENT_CASE', 'U') IS NULL THEN 'MISSING' ELSE 'ok' END AS state
UNION ALL SELECT 'MOHECORE.AUDIT_LOG (expected gone)',   CASE WHEN OBJECT_ID('MOHECORE.AUDIT_LOG', 'U') IS NULL THEN 'gone' ELSE 'present' END
UNION ALL SELECT 'dbo.student_data_id_sequence (expected gone)', CASE WHEN OBJECT_ID('dbo.student_data_id_sequence', 'SO') IS NULL THEN 'gone' ELSE 'present' END
UNION ALL SELECT 'FK_ApprovalHistory_ActionBy',           CASE WHEN EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ApprovalHistory_ActionBy') THEN 'ok' ELSE 'MISSING' END
UNION ALL SELECT 'FK_ApprovalLevels_RequiredRole',        CASE WHEN EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ApprovalLevels_RequiredRole') THEN 'ok' ELSE 'skipped/missing' END
UNION ALL SELECT 'FK_Permissions_Resource',               CASE WHEN EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Permissions_Resource') THEN 'ok' ELSE 'MISSING' END
UNION ALL SELECT 'PK_error_log',                          CASE WHEN EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'PK_error_log') THEN 'ok' ELSE 'MISSING' END
UNION ALL SELECT 'IX_PAYMENT_BATCH_LINE_STUDY_COUNTRY_IN_ID', CASE WHEN EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_PAYMENT_BATCH_LINE_STUDY_COUNTRY_IN_ID') THEN 'ok' ELSE 'MISSING' END
UNION ALL SELECT 'Users.PasswordHash NOT NULL',           CASE WHEN EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('UMSCORE.Users') AND name = 'PasswordHash' AND is_nullable = 0) THEN 'ok' ELSE 'nullable' END
UNION ALL SELECT 'STUDENT_NODE.STUDENT_ID NOT NULL',      CASE WHEN EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('MOHENTracer.STUDENT_NODE') AND name = 'STUDENT_ID' AND is_nullable = 0) THEN 'ok' ELSE 'nullable' END
UNION ALL SELECT 'STUDY_STATUS.status_name collation',    ISNULL((SELECT collation_name FROM sys.columns WHERE object_id = OBJECT_ID('MOHECORE.STUDY_STATUS') AND name = 'status_name'), 'n/a')
UNION ALL SELECT 'FINCORE.PAYMENT_BATCH.UPDATED_AT',      CASE WHEN COL_LENGTH('FINCORE.PAYMENT_BATCH', 'UPDATED_AT') IS NULL THEN 'MISSING' ELSE 'ok' END
UNION ALL SELECT 'V_Predictive_Analytics (expected gone)', CASE WHEN OBJECT_ID('MOHECORE.V_Predictive_Analytics', 'V') IS NULL THEN 'gone' ELSE 'present' END
UNION ALL SELECT 'V_Student_List_Detailed.GPA type',      ISNULL((SELECT TYPE_NAME(c.system_type_id) FROM sys.columns c WHERE c.object_id = OBJECT_ID('MOHECORE.V_Student_List_Detailed') AND c.name = 'GPA'), 'n/a')
UNION ALL SELECT 'Users.LastLogin (expected gone)',       CASE WHEN COL_LENGTH('UMSCORE.Users', 'LastLogin') IS NULL THEN 'gone' ELSE 'present' END;
PRINT '=== V066 done ===';
GO
