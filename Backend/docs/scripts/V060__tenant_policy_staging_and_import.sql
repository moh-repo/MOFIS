/*
    V060 — bring the two ETL landing tables under the tenant policy.

    V032 and V037 rebuild tenant_country_policy from the catalogue: every
    MOHECORE / STUDENTCORE table with a column literally named
    STUDY_COUNTRY_IN_ID, plus STUDENT_DATA and STUDENT_DATA_ARCH by name (their
    column is STUDY_COUNTRY_IN). Two tables created since fall outside both rules:

      MOHECORE.STUDENT_DATA_STAGING            (V054)  full student rows for every
                                                        country; column STUDY_COUNTRY_IN,
                                                        inherited from STUDENT_DATA
      MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT (V051)  the raw attaché sheets, one
                                                        row per student; column COUNTRY_ID

    The service layer scopes both today (DataImportGuardTest, the ETL guards),
    so this is defence in depth — the same predicate, the same function, the same
    three operations STUDENT_DATA carries: a FILTER on read and BLOCK AFTER
    INSERT / AFTER UPDATE so a row cannot be written into a country the session
    cannot see. The policy's STATE is left exactly as it was (it ships OFF; see
    the runbook for enabling it).

    Also recorded here, not changed: V049 added STUDY_COUNTRY_IN_ID to
    STUDY_YEARS_LOOKUP and STUDY_AMOUNT_LOOKUP *after* the last catalogue rebuild,
    so they are outside the policy today and would be pulled in by a V032/V037
    re-run. Under the V049 convention a NULL country is the shared ministry
    default and fn_tenant_predicate passes NULL rows through, so either state is
    correct; V032's verification query below now lists them as deliberate.

    Idempotent: each predicate is added only if absent. Needs the policy and the
    predicate function from V031.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID('MOHECORE.fn_tenant_predicate', 'IF') IS NULL
   OR NOT EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy')
BEGIN
    PRINT 'V060: V031 has not been applied here (no fn_tenant_predicate / tenant_country_policy) — nothing to do.';
    RETURN;
END
GO

DECLARE @targets TABLE (table_name SYSNAME, country_col SYSNAME);
INSERT INTO @targets VALUES
    ('STUDENT_DATA_STAGING',            'STUDY_COUNTRY_IN'),
    ('STUDENT_SCHOLARSHIP_DATA_IMPORT', 'COUNTRY_ID');

DECLARE @tbl SYSNAME, @col SYSNAME, @sql NVARCHAR(MAX), @oid INT;
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT table_name, country_col FROM @targets;
OPEN c;
FETCH NEXT FROM c INTO @tbl, @col;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @oid = OBJECT_ID('MOHECORE.' + @tbl, 'U');
    IF @oid IS NULL
        PRINT 'V060: MOHECORE.' + @tbl + ' absent — skipped';
    ELSE IF COL_LENGTH('MOHECORE.' + @tbl, @col) IS NULL
        PRINT 'V060: MOHECORE.' + @tbl + '.' + @col + ' absent — skipped';
    ELSE
    BEGIN
        SET @sql = N'';
        IF NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                        WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = @oid AND sp.predicate_type = 0)
            SET @sql += N' ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(' + QUOTENAME(@col) + N') ON MOHECORE.' + QUOTENAME(@tbl) + N',';
        IF NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                        WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = @oid AND sp.predicate_type = 1 AND sp.operation = 1)
            SET @sql += N' ADD BLOCK PREDICATE MOHECORE.fn_tenant_predicate(' + QUOTENAME(@col) + N') ON MOHECORE.' + QUOTENAME(@tbl) + N' AFTER INSERT,';
        IF NOT EXISTS (SELECT 1 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
                        WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = @oid AND sp.predicate_type = 1 AND sp.operation = 2)
            SET @sql += N' ADD BLOCK PREDICATE MOHECORE.fn_tenant_predicate(' + QUOTENAME(@col) + N') ON MOHECORE.' + QUOTENAME(@tbl) + N' AFTER UPDATE,';

        IF @sql = N''
            PRINT 'V060: MOHECORE.' + @tbl + ' already under the policy';
        ELSE
        BEGIN
            SET @sql = N'ALTER SECURITY POLICY MOHECORE.tenant_country_policy' + LEFT(@sql, LEN(@sql) - 1) + N';';
            EXEC sp_executesql @sql;
            PRINT 'V060: MOHECORE.' + @tbl + ' added to tenant_country_policy on ' + @col;
        END
    END
    FETCH NEXT FROM c INTO @tbl, @col;
END
CLOSE c; DEALLOCATE c;
GO

/* ── Verification: V032's guard, with the exemption list brought up to date ── */
PRINT '';
PRINT '=== tables holding student rows outside the policy (expected: empty) ===';
SELECT t.name AS table_name, SUM(pt.rows) AS row_count
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
GROUP BY t.name
ORDER BY row_count DESC;

SELECT OBJECT_NAME(sp.target_object_id) AS table_name, sp.predicate_type_desc, sp.operation_desc, p.is_enabled
  FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id = sp.object_id
 WHERE p.name = 'tenant_country_policy'
   AND OBJECT_NAME(sp.target_object_id) IN ('STUDENT_DATA_STAGING', 'STUDENT_SCHOLARSHIP_DATA_IMPORT')
 ORDER BY 1, 2, 3;
GO
