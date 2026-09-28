/*
    V065 — close the remaining tenant-policy gaps, and three small columns the
           services now write.

    What
    ----
    1. tenant_country_policy gains the tables the August 2026 review found
       outside it although they carry a country:
           AUDITLOG.AuditTrail                     (COUNTRY_ID, V047)        filter + block after insert
           ALERTCORE.ApprovalRequests              (CountryId, V058/V046)    filter + block after insert
           MOHECORE.STUDY_FIN_RECEIVED_WHOM        (FINANCE_COUNTRY_ID)      filter + block after insert
           ALERTEVENTCASES.ALERT_EVENT_CASE        (STUDENT_COUNTRY_ID)      filter + block after insert
           MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS    new STUDY_COUNTRY_IN_ID, backfilled from the parent
                                                   STUDY_FIN_RECEIVED_WHOM.FINANCE_COUNTRY_ID through
                                                   WHOM_ID (0 = "not set" in that legacy column, so it
                                                   becomes NULL = ministry-wide, never a country 0)
       and the three archives, which V032/V037 gave a FILTER only, get the
       block predicates a write would need:
           MOHECORE.STUDENT_DATA_ARCH              every predicate STUDENT_DATA has (read at run time from
                                                   sys.security_predicates and mirrored on STUDY_COUNTRY_IN)
           MOHECORE.STUDENT_FINANCE_ARCH           block after insert
           MOHECORE.STUDY_UNIVERSITY_FEE_ARCH      block after insert
       The policy is extended with ALTER SECURITY POLICY ... ADD (V060's way);
       its STATE is not touched. Same function, MOHECORE.fn_tenant_predicate.
    2. MOHECORE.REPORT_ACCESS_LOG.STUDY_COUNTRY_IN_ID (present, all NULL) is
       backfilled from the writer's country — USERNAME joined to UMSCORE.Users,
       and only where the user holds exactly one active UserCountries row.
       Anyone with several countries, or an unmatched name ('system'), stays
       NULL = ministry-wide. Counts are printed.
    3. MOHECORE.PAYMENT_AUTH.AUTHORIZED_BY, FK to UMSCORE.Users. The Users key
       is BIGINT, so the column is BIGINT (an INT column cannot carry a foreign
       key to a BIGINT key).
    4. Permission SYSTEM.RESTORE, granted to nobody (V063's way). Its sibling
       SYS_BACKUP.RESTORE uses ActionType 'RESTORE' / PermissionLevel 'WRITE',
       and that is followed here.
    5. V032's "student rows outside the policy" guard, with the exemption list
       brought up to date, plus a second guard for tables that carry a country
       column but are not under the policy. Both run at the end and PRINT
       anything unexpected; the queries are repeated in the trailing comment.

    Deliberate exemptions (NULL = global, scoped in Java where it matters)
    ----------------------------------------------------------------------
       MOHE_QUARTER.COUNTRY_ID               V021 reads NULL as ministry-wide; quarters are shared
       NOTIFICATION_SETTINGS.COUNTRY_ID      one row per country plus the NULL default; NotificationService scopes
       NOTIFICATION_LOG_COUNTRY.COUNTRY_ID   fan-out table keyed by LOG_ID; the log itself is ministry-level
       REPORT_METRIC_TARGET.COUNTRY_ID       V028 targets, NULL = the ministry-wide target
       STUDY_YEARS_LOOKUP / STUDY_AMOUNT_LOOKUP  V049 overrides, NULL = shared default (recorded by V060)
       STUDY_*_IN lookups, STUDY_FINANCE_BANK(_BRANCH)  shared reference data (V032)
       STUDENT_COUNTRY / STUDENT_GOV / STUDENT_GOV_MUD  nationality lookups — STUDENT_COUNTRY_ID is not the tenant key
       StudentPortalSession / StudentPortalAccount     authentication (V032)

    Prerequisites: V031 (function + policy), V047, V058. Where V031 is absent the
    policy steps print and skip; everything else still runs.

    Session context: several steps read or update tables that are under the
    policy (REPORT_ACCESS_LOG, the guards' row counts). With the policy ON a
    session without context sees nothing, so TenantAll is set for this
    connection first — the same key the application sets for ministry work.

    Rollback
    --------
        ALTER SECURITY POLICY MOHECORE.tenant_country_policy
            DROP FILTER PREDICATE ON AUDITLOG.AuditTrail, DROP BLOCK PREDICATE ON AUDITLOG.AuditTrail AFTER INSERT, ...;
        ALTER TABLE MOHECORE.PAYMENT_AUTH DROP CONSTRAINT FK_PAYMENT_AUTH_AUTHORIZED_BY;
        ALTER TABLE MOHECORE.PAYMENT_AUTH DROP COLUMN AUTHORIZED_BY;
        ALTER TABLE MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS DROP CONSTRAINT FK_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN;
        DROP INDEX IX_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN_ID ON MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS;
        ALTER TABLE MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS DROP COLUMN STUDY_COUNTRY_IN_ID;
        DELETE FROM UMSCORE.Permissions WHERE PermissionCode = 'SYSTEM.RESTORE';
        UPDATE MOHECORE.REPORT_ACCESS_LOG SET STUDY_COUNTRY_IN_ID = NULL;   -- the backfill only ever filled NULLs

    Idempotent: every step checks before it acts and prints "already present".
    Run with sqlcmd -I -f 65001 -b (see the runbook).
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';
PRINT '';
PRINT '=== V065: tenant policy coverage and audit columns ===';
GO

/* ── 1a. MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS.STUDY_COUNTRY_IN_ID ─────────── */
IF OBJECT_ID('MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS', 'U') IS NULL
    PRINT 'V065: FIN_RECEIVED_WHOM_DOCUMENTS absent (V058 not applied) — skipped';
ELSE IF COL_LENGTH('MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS', 'STUDY_COUNTRY_IN_ID') IS NOT NULL
    PRINT 'V065: FIN_RECEIVED_WHOM_DOCUMENTS.STUDY_COUNTRY_IN_ID already present';
ELSE
BEGIN
    ALTER TABLE MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS ADD STUDY_COUNTRY_IN_ID INT NULL;
    PRINT 'V065: added FIN_RECEIVED_WHOM_DOCUMENTS.STUDY_COUNTRY_IN_ID';
END
GO

/* Backfill from the parent row. Only NULLs are filled; a 0 in the legacy
   FINANCE_COUNTRY_ID means "not set" and becomes NULL, not country 0. */
IF COL_LENGTH('MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS', 'STUDY_COUNTRY_IN_ID') IS NOT NULL
BEGIN
    DECLARE @filled INT;
    UPDATE d SET d.STUDY_COUNTRY_IN_ID = NULLIF(w.FINANCE_COUNTRY_ID, 0)
      FROM MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS d
      JOIN MOHECORE.STUDY_FIN_RECEIVED_WHOM w ON w.STUDY_RECEIVED_WHOM_ID = d.WHOM_ID
     WHERE d.STUDY_COUNTRY_IN_ID IS NULL
       AND NULLIF(w.FINANCE_COUNTRY_ID, 0) IS NOT NULL
       AND EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = w.FINANCE_COUNTRY_ID);
    SET @filled = @@ROWCOUNT;
    PRINT 'V065: FIN_RECEIVED_WHOM_DOCUMENTS rows given a country from their parent: ' + CAST(@filled AS VARCHAR(10));

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS')
                     AND name = 'IX_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN_ID')
    BEGIN
        CREATE INDEX IX_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN_ID
            ON MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS (STUDY_COUNTRY_IN_ID);
        PRINT 'V065: created IX_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN_ID';
    END

    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN')
        PRINT 'V065: FK_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN already present';
    ELSE IF EXISTS (SELECT 1 FROM MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS d
                     WHERE d.STUDY_COUNTRY_IN_ID IS NOT NULL
                       AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = d.STUDY_COUNTRY_IN_ID))
        PRINT 'V065: *** FK_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN skipped — orphan country ids exist';
    ELSE
    BEGIN
        ALTER TABLE MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS WITH CHECK
            ADD CONSTRAINT FK_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN
            FOREIGN KEY (STUDY_COUNTRY_IN_ID) REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID);
        PRINT 'V065: created FK_FIN_RECEIVED_WHOM_DOCUMENTS_STUDY_COUNTRY_IN';
    END
END
GO

/* ── 1b. Extend the policy ────────────────────────────────────────────────────
 * ops: 1 = FILTER, 2 = BLOCK AFTER INSERT, 4 = BLOCK AFTER UPDATE (bit mask).
 * Each missing predicate becomes one ADD clause of a single ALTER SECURITY
 * POLICY per table, exactly as V060 does. STATE is left alone.
 * ──────────────────────────────────────────────────────────────────────────── */
IF OBJECT_ID('MOHECORE.fn_tenant_predicate', 'IF') IS NULL
   OR NOT EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy')
    PRINT 'V065: V031 has not been applied here (no fn_tenant_predicate / tenant_country_policy) — policy steps skipped';
ELSE
BEGIN
    DECLARE @targets TABLE (schema_name SYSNAME, table_name SYSNAME, country_col SYSNAME, ops TINYINT);
    INSERT INTO @targets VALUES
        ('AUDITLOG',        'AuditTrail',                  'COUNTRY_ID',          3),
        ('ALERTCORE',       'ApprovalRequests',            'CountryId',           3),
        ('MOHECORE',        'STUDY_FIN_RECEIVED_WHOM',     'FINANCE_COUNTRY_ID',  3),
        ('ALERTEVENTCASES', 'ALERT_EVENT_CASE',            'STUDENT_COUNTRY_ID',  3),
        ('MOHECORE',        'FIN_RECEIVED_WHOM_DOCUMENTS', 'STUDY_COUNTRY_IN_ID', 3),
        ('MOHECORE',        'STUDENT_FINANCE_ARCH',        'STUDY_COUNTRY_IN_ID', 2),
        ('MOHECORE',        'STUDY_UNIVERSITY_FEE_ARCH',   'STUDY_COUNTRY_IN_ID', 2);

    /* STUDENT_DATA_ARCH mirrors whatever STUDENT_DATA has, read from the catalogue. */
    DECLARE @sdOps TINYINT = (
        SELECT ISNULL(SUM(DISTINCT CASE WHEN sp.predicate_type = 0 THEN 1
                                        WHEN sp.predicate_type = 1 AND sp.operation = 1 THEN 2
                                        WHEN sp.predicate_type = 1 AND sp.operation = 2 THEN 4 ELSE 0 END), 0)
          FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
         WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = OBJECT_ID('MOHECORE.STUDENT_DATA'));
    INSERT INTO @targets VALUES ('MOHECORE', 'STUDENT_DATA_ARCH', 'STUDY_COUNTRY_IN', @sdOps);
    PRINT 'V065: STUDENT_DATA carries predicate mask ' + CAST(@sdOps AS VARCHAR(3)) + ' (1 filter, 2 block insert, 4 block update); mirrored on STUDENT_DATA_ARCH';

    DECLARE @sch SYSNAME, @tbl SYSNAME, @col SYSNAME, @ops TINYINT, @sql NVARCHAR(MAX), @oid INT, @fq NVARCHAR(300);
    DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT schema_name, table_name, country_col, ops FROM @targets;
    OPEN c;
    FETCH NEXT FROM c INTO @sch, @tbl, @col, @ops;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @fq  = QUOTENAME(@sch) + N'.' + QUOTENAME(@tbl);
        SET @oid = OBJECT_ID(@sch + '.' + @tbl, 'U');
        IF @oid IS NULL
            PRINT 'V065: ' + @sch + '.' + @tbl + ' absent — skipped';
        ELSE IF COL_LENGTH(@sch + '.' + @tbl, @col) IS NULL
            PRINT 'V065: ' + @sch + '.' + @tbl + '.' + @col + ' absent — skipped';
        ELSE
        BEGIN
            SET @sql = N'';
            IF (@ops & 1) = 1 AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                            WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = @oid AND sp.predicate_type = 0)
                SET @sql += N' ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(' + QUOTENAME(@col) + N') ON ' + @fq + N',';
            IF (@ops & 2) = 2 AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                            WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = @oid AND sp.predicate_type = 1 AND sp.operation = 1)
                SET @sql += N' ADD BLOCK PREDICATE MOHECORE.fn_tenant_predicate(' + QUOTENAME(@col) + N') ON ' + @fq + N' AFTER INSERT,';
            IF (@ops & 4) = 4 AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                            WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = @oid AND sp.predicate_type = 1 AND sp.operation = 2)
                SET @sql += N' ADD BLOCK PREDICATE MOHECORE.fn_tenant_predicate(' + QUOTENAME(@col) + N') ON ' + @fq + N' AFTER UPDATE,';

            IF @sql = N''
                PRINT 'V065: ' + @sch + '.' + @tbl + ' already under the policy with the requested predicates';
            ELSE
            BEGIN
                SET @sql = N'ALTER SECURITY POLICY MOHECORE.tenant_country_policy' + LEFT(@sql, LEN(@sql) - 1) + N';';
                EXEC sp_executesql @sql;
                PRINT 'V065: ' + @sch + '.' + @tbl + ' — predicates added on ' + @col;
            END
        END
        FETCH NEXT FROM c INTO @sch, @tbl, @col, @ops;
    END
    CLOSE c; DEALLOCATE c;

    DECLARE @state BIT = (SELECT is_enabled FROM sys.security_policies WHERE name = 'tenant_country_policy');
    PRINT 'V065: tenant_country_policy STATE unchanged: ' + CASE WHEN @state = 1 THEN 'ON' ELSE 'OFF' END;
END
GO

/* ── 2. REPORT_ACCESS_LOG.STUDY_COUNTRY_IN_ID backfill ────────────────────── */
IF COL_LENGTH('MOHECORE.REPORT_ACCESS_LOG', 'STUDY_COUNTRY_IN_ID') IS NULL
    PRINT 'V065: REPORT_ACCESS_LOG.STUDY_COUNTRY_IN_ID absent — backfill skipped';
ELSE
BEGIN
    DECLARE @before INT, @updated INT, @after INT, @multi INT, @unmatched INT;
    SELECT @before = COUNT(*) FROM MOHECORE.REPORT_ACCESS_LOG WHERE STUDY_COUNTRY_IN_ID IS NULL;

    /* Users holding exactly one active country. */
    UPDATE l SET l.STUDY_COUNTRY_IN_ID = one.CountryId
      FROM MOHECORE.REPORT_ACCESS_LOG l
      JOIN UMSCORE.Users u ON u.Username = l.USERNAME
      JOIN (SELECT uc.UserId, MIN(uc.CountryId) AS CountryId
              FROM UMSCORE.UserCountries uc WHERE uc.IsActive = 1
             GROUP BY uc.UserId HAVING COUNT(DISTINCT uc.CountryId) = 1) one ON one.UserId = u.Id
     WHERE l.STUDY_COUNTRY_IN_ID IS NULL;
    SET @updated = @@ROWCOUNT;

    SELECT @after = COUNT(*) FROM MOHECORE.REPORT_ACCESS_LOG WHERE STUDY_COUNTRY_IN_ID IS NULL;
    SELECT @multi = COUNT(*) FROM MOHECORE.REPORT_ACCESS_LOG l
     WHERE l.STUDY_COUNTRY_IN_ID IS NULL
       AND EXISTS (SELECT 1 FROM UMSCORE.Users u WHERE u.Username = l.USERNAME);
    SET @unmatched = @after - @multi;

    PRINT 'V065: REPORT_ACCESS_LOG — NULL country before: ' + CAST(@before AS VARCHAR(10))
        + ', filled from a single-country user: ' + CAST(@updated AS VARCHAR(10))
        + ', left NULL because the user holds several countries: ' + CAST(@multi AS VARCHAR(10))
        + ', left NULL because USERNAME matches no user: ' + CAST(@unmatched AS VARCHAR(10));
END
GO

/* ── 3. PAYMENT_AUTH.AUTHORIZED_BY → UMSCORE.Users ─────────────────────────── */
IF COL_LENGTH('MOHECORE.PAYMENT_AUTH', 'AUTHORIZED_BY') IS NOT NULL
    PRINT 'V065: PAYMENT_AUTH.AUTHORIZED_BY already present';
ELSE
BEGIN
    ALTER TABLE MOHECORE.PAYMENT_AUTH ADD AUTHORIZED_BY BIGINT NULL;
    PRINT 'V065: added PAYMENT_AUTH.AUTHORIZED_BY (BIGINT — the type of UMSCORE.Users.Id)';
END
GO
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_PAYMENT_AUTH_AUTHORIZED_BY')
    PRINT 'V065: FK_PAYMENT_AUTH_AUTHORIZED_BY already present';
ELSE IF COL_LENGTH('MOHECORE.PAYMENT_AUTH', 'AUTHORIZED_BY') IS NULL
    PRINT 'V065: FK_PAYMENT_AUTH_AUTHORIZED_BY skipped — column absent';
ELSE
BEGIN
    ALTER TABLE MOHECORE.PAYMENT_AUTH WITH CHECK
        ADD CONSTRAINT FK_PAYMENT_AUTH_AUTHORIZED_BY FOREIGN KEY (AUTHORIZED_BY) REFERENCES UMSCORE.Users (Id);
    PRINT 'V065: created FK_PAYMENT_AUTH_AUTHORIZED_BY';
END
GO

/* ── 4. Permission SYSTEM.RESTORE ─────────────────────────────────────────── */
INSERT INTO UMSCORE.Permissions
    (PermissionCode, PermissionName, PermissionNameAr, Description,
     ResourceType, ActionType, PermissionLevel, IsActive, CreatedAt)
SELECT 'SYSTEM.RESTORE', N'Restore Database from Backup', N'استعادة قاعدة البيانات من نسخة احتياطية',
       N'Restore the database from an uploaded backup — replaces every row in the system',
       'PAGE', 'RESTORE', 'WRITE', 1, SYSUTCDATETIME()
WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions e WHERE e.PermissionCode = 'SYSTEM.RESTORE');
PRINT 'V065: created ' + CAST(@@ROWCOUNT AS VARCHAR(10)) + ' permission(s) for SYSTEM.RESTORE (0 means already present); granted to nobody';
GO

/* ── 5. Guards ────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== guard A: tables holding student rows outside the policy (expected: none) ===';
DECLARE @name SYSNAME, @rows BIGINT, @found INT = 0;
DECLARE g CURSOR LOCAL FAST_FORWARD FOR
    SELECT t.name, SUM(pt.rows)
      FROM sys.tables t
      JOIN sys.partitions pt ON pt.object_id = t.object_id AND pt.index_id IN (0, 1)
     WHERE SCHEMA_NAME(t.schema_id) = 'MOHECORE'
       AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name IN ('STUDENT_ID', 'StudentId'))
       AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp
                       JOIN sys.security_policies p ON p.object_id = sp.object_id
                       WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = t.object_id)
       AND t.name NOT IN (
           -- leftovers handled by V035 / V061, not tenanted
           'Q2_2024_EGY', 'STUDENT_DATA_BKUP',
           'BK_STUDENT_DOCUMENTS_20260824', 'BK_STUDENT_DOC_HIST_20260824', 'BK_STUDENT_PICPATH_20260824',
           -- authentication tables: reading them is how the scope is determined (V032)
           'StudentPortalSession', 'StudentPortalAccount')
     GROUP BY t.name ORDER BY t.name;
OPEN g; FETCH NEXT FROM g INTO @name, @rows;
WHILE @@FETCH_STATUS = 0
BEGIN
    PRINT '*** UNEXPECTED: MOHECORE.' + @name + ' (' + CAST(@rows AS VARCHAR(20)) + ' rows) holds student rows and is not under the policy';
    SET @found += 1;
    FETCH NEXT FROM g INTO @name, @rows;
END
CLOSE g; DEALLOCATE g;
IF @found = 0 PRINT 'guard A: clean';

PRINT '';
PRINT '=== guard B: tables carrying a country column outside the policy, beyond the deliberate exemptions (expected: none) ===';
DECLARE @sch2 SYSNAME, @col2 SYSNAME;
SET @found = 0;
DECLARE g2 CURSOR LOCAL FAST_FORWARD FOR
    SELECT SCHEMA_NAME(t.schema_id), t.name, c.name
      FROM sys.tables t JOIN sys.columns c ON c.object_id = t.object_id
     WHERE SCHEMA_NAME(t.schema_id) IN ('MOHECORE', 'STUDENTCORE', 'FINCORE', 'ALERTCORE', 'ALERTEVENTCASES', 'AUDITLOG')
       AND c.name IN ('STUDY_COUNTRY_IN_ID', 'STUDY_COUNTRY_IN', 'COUNTRY_ID', 'CountryId', 'STUDENT_COUNTRY_ID', 'FINANCE_COUNTRY_ID')
       AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp
                       JOIN sys.security_policies p ON p.object_id = sp.object_id
                       WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = t.object_id)
       AND t.name NOT IN (
           -- shared reference data every tenant reads (V032): the country is what the row describes
           'STUDY_COUNTRY_IN', 'STUDY_GOV_IN', 'STUDY_COLLEGE_IN', 'STUDY_UNIVERSITY_IN', 'STUDY_FINANCE_BANK', 'STUDY_FINANCE_BANK_BRANCH',
           -- nationality lookups: STUDENT_COUNTRY_ID is the student's nationality, never the tenant key
           'STUDENT_COUNTRY', 'STUDENT_GOV', 'STUDENT_GOV_MUD',
           -- V049 per-attaché overrides; NULL = the shared default (recorded by V060)
           'STUDY_YEARS_LOOKUP', 'STUDY_AMOUNT_LOOKUP',
           -- NULL = global, scoped by the Java services where it matters (this script's header)
           'MOHE_QUARTER', 'NOTIFICATION_SETTINGS', 'NOTIFICATION_LOG_COUNTRY', 'REPORT_METRIC_TARGET',
           -- authentication tables (V032)
           'StudentPortalSession', 'StudentPortalAccount')
     ORDER BY 1, 2;
OPEN g2; FETCH NEXT FROM g2 INTO @sch2, @name, @col2;
WHILE @@FETCH_STATUS = 0
BEGIN
    PRINT '*** UNEXPECTED: ' + @sch2 + '.' + @name + '.' + @col2 + ' — country column outside the policy and not on the exemption list';
    SET @found += 1;
    FETCH NEXT FROM g2 INTO @sch2, @name, @col2;
END
CLOSE g2; DEALLOCATE g2;
IF @found = 0 PRINT 'guard B: clean';
GO

/* ── Verification ─────────────────────────────────────────────────────────── */
SELECT OBJECT_SCHEMA_NAME(sp.target_object_id) + '.' + OBJECT_NAME(sp.target_object_id) AS table_name,
       sp.predicate_type_desc, sp.operation_desc, p.is_enabled
  FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
 WHERE p.name = 'tenant_country_policy'
   AND OBJECT_NAME(sp.target_object_id) IN ('AuditTrail', 'ApprovalRequests', 'STUDY_FIN_RECEIVED_WHOM', 'ALERT_EVENT_CASE',
                                            'FIN_RECEIVED_WHOM_DOCUMENTS', 'STUDENT_DATA', 'STUDENT_DATA_ARCH',
                                            'STUDENT_FINANCE_ARCH', 'STUDY_UNIVERSITY_FEE_ARCH')
 ORDER BY 1, 2, 3;

SELECT 'PAYMENT_AUTH.AUTHORIZED_BY' AS object_, CASE WHEN COL_LENGTH('MOHECORE.PAYMENT_AUTH', 'AUTHORIZED_BY') IS NULL THEN 'MISSING' ELSE 'ok' END AS state
UNION ALL SELECT 'FK_PAYMENT_AUTH_AUTHORIZED_BY', CASE WHEN EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_PAYMENT_AUTH_AUTHORIZED_BY') THEN 'ok' ELSE 'MISSING' END
UNION ALL SELECT 'FIN_RECEIVED_WHOM_DOCUMENTS.STUDY_COUNTRY_IN_ID', CASE WHEN COL_LENGTH('MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS', 'STUDY_COUNTRY_IN_ID') IS NULL THEN 'MISSING' ELSE 'ok' END
UNION ALL SELECT 'SYSTEM.RESTORE', CASE WHEN EXISTS (SELECT 1 FROM UMSCORE.Permissions WHERE PermissionCode = 'SYSTEM.RESTORE') THEN 'ok' ELSE 'MISSING' END;

PRINT '';
PRINT '=== V065 done ===';
GO

/* ─────────────────────────────────────────────────────────────────────────────
   The guard queries, for running by hand (guard B is new in V065; guard A is
   V032 §7 / V060 with the exemption list of 27 Aug 2026):

   -- A. student rows outside the policy
   SELECT t.name, SUM(pt.rows) AS row_count
     FROM sys.tables t JOIN sys.partitions pt ON pt.object_id = t.object_id AND pt.index_id IN (0, 1)
    WHERE SCHEMA_NAME(t.schema_id) = 'MOHECORE'
      AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name IN ('STUDENT_ID', 'StudentId'))
      AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                      WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = t.object_id)
      AND t.name NOT IN ('Q2_2024_EGY', 'STUDENT_DATA_BKUP',
                         'BK_STUDENT_DOCUMENTS_20260824', 'BK_STUDENT_DOC_HIST_20260824', 'BK_STUDENT_PICPATH_20260824',
                         'StudentPortalSession', 'StudentPortalAccount')
    GROUP BY t.name ORDER BY row_count DESC;

   -- B. country columns outside the policy
   SELECT SCHEMA_NAME(t.schema_id) AS schema_name, t.name, c.name AS country_col
     FROM sys.tables t JOIN sys.columns c ON c.object_id = t.object_id
    WHERE SCHEMA_NAME(t.schema_id) IN ('MOHECORE', 'STUDENTCORE', 'FINCORE', 'ALERTCORE', 'ALERTEVENTCASES', 'AUDITLOG')
      AND c.name IN ('STUDY_COUNTRY_IN_ID', 'STUDY_COUNTRY_IN', 'COUNTRY_ID', 'CountryId', 'STUDENT_COUNTRY_ID', 'FINANCE_COUNTRY_ID')
      AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                      WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = t.object_id)
      AND t.name NOT IN ('STUDY_COUNTRY_IN', 'STUDY_GOV_IN', 'STUDY_COLLEGE_IN', 'STUDY_UNIVERSITY_IN', 'STUDY_FINANCE_BANK', 'STUDY_FINANCE_BANK_BRANCH',
                         'STUDENT_COUNTRY', 'STUDENT_GOV', 'STUDENT_GOV_MUD',
                         'STUDY_YEARS_LOOKUP', 'STUDY_AMOUNT_LOOKUP',
                         'MOHE_QUARTER', 'NOTIFICATION_SETTINGS', 'NOTIFICATION_LOG_COUNTRY', 'REPORT_METRIC_TARGET',
                         'StudentPortalSession', 'StudentPortalAccount')
    ORDER BY 1, 2;
   ──────────────────────────────────────────────────────────────────────────── */
