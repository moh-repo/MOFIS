/*
    V095 — Row-level backstop for rule N-04: only a country connection writes Wafar.

    Security plan N-04.d (6 Sep 2026). The application refuses every Wafar write
    from a ministry-level account (WafarPolicy, since the N-04.a pass), but a
    refusal in Java protects only the paths that remember to ask. This script
    makes the database refuse as well.

    What
    ----
    1. A second SESSION_CONTEXT key, TenantWafarWrite. TenantContextDataSource
       sets it on every connection it hands out: 1 for a country-scoped account
       and for system work (startup, migrations, scheduled jobs), 0 for a
       ministry-level account (super admin, ministry admin, ministry staff).
    2. MOHECORE.fn_wafar_write_predicate(@CountryId): the tenant predicate of
       V031 AND TenantWafarWrite = 1.
    3. On the Wafar tables the INSERT block predicate from V031/V032 is replaced
       by the new one, and UPDATE and DELETE are blocked too (BEFORE UPDATE,
       BEFORE DELETE), which the plain policy never did: a row a ministry
       connection could see, it could also change.

    Tables: WAFAR_SPENDING, WAFAR_STUDENT_FEE_PAYMENT, STUDENT_FINANCE_WAFAR,
    WAFAR_RECONCILIATION. WAFAR_EARMARK and WAFAR_USE stay under the plain
    tenant predicate: they are the ledger's earmarks of Wafar against
    allocations and payment batches, written by ministry accounting flows the
    ministry legitimately runs.

    Two ministry-side writes remain and both raise the key for one transaction
    on the connection they already hold (WafarWriteKey.elevateCurrentTransaction),
    each audited: the super admin approving a Wafar spend under decision D-13(b)
    (WAFAR_SUPERADMIN_APPROVE), and the approval workflow executing a request the
    attaché raised and the chain signed (WAFAR_WRITE_ELEVATED).

    Effect
    ------
    A build older than this date does not set the key, so on that build every
    Wafar write fails with "The attempted operation failed because the target
    object ... has a block predicate that conflicts with this operation".
    Deploy the build of 6 Sep 2026 or later with this script.

    V032 recreates the whole policy from the catalogue when re-run. Do not re-run
    V032 after this script; if you must, run this one again afterwards.

    Rollback
    --------
        ALTER SECURITY POLICY MOHECORE.tenant_country_policy
            DROP BLOCK PREDICATE ON MOHECORE.WAFAR_SPENDING AFTER INSERT, ... (each predicate below),
            ADD BLOCK PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.WAFAR_SPENDING AFTER INSERT, ...;
        DROP FUNCTION MOHECORE.fn_wafar_write_predicate;

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ---- 1. The predicate ------------------------------------------------------ */
CREATE OR ALTER FUNCTION MOHECORE.fn_wafar_write_predicate(@CountryId INT)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN
    SELECT 1 AS is_writable
    WHERE
        -- The connection may write Wafar at all (country account or system work).
        TRY_CAST(CAST(SESSION_CONTEXT(N'TenantWafarWrite') AS NVARCHAR(4)) AS BIT) = 1
        AND (
            -- ...and the row is inside its scope, as for every other tenanted table.
            TRY_CAST(CAST(SESSION_CONTEXT(N'TenantAll') AS NVARCHAR(4)) AS BIT) = 1
            OR @CountryId IS NULL
            OR @CountryId IN (
                SELECT TRY_CAST(value AS INT)
                FROM STRING_SPLIT(CAST(SESSION_CONTEXT(N'TenantCountryIds') AS NVARCHAR(4000)), ',')
                WHERE TRY_CAST(value AS INT) IS NOT NULL
            )
        );
GO

/* ---- 2. Swap the predicates on the Wafar tables --------------------------- */
DECLARE @tables TABLE (name SYSNAME);
INSERT INTO @tables VALUES ('WAFAR_SPENDING'), ('WAFAR_STUDENT_FEE_PAYMENT'), ('STUDENT_FINANCE_WAFAR'), ('WAFAR_RECONCILIATION');

DECLARE @t SYSNAME, @sql NVARCHAR(MAX);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM @tables;
OPEN c;
FETCH NEXT FROM c INTO @t;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID('MOHECORE.' + @t, 'U') IS NULL
    BEGIN
        PRINT 'V095: MOHECORE.' + @t + ' does not exist here; skipped.';
    END
    ELSE
    BEGIN
        DECLARE @obj INT = OBJECT_ID('MOHECORE.' + @t);
        DECLARE @already BIT = CASE WHEN EXISTS (
            SELECT 1 FROM sys.security_predicates sp
             WHERE sp.target_object_id = @obj AND sp.predicate_type_desc = 'BLOCK'
               AND sp.predicate_definition LIKE '%fn_wafar_write_predicate%') THEN 1 ELSE 0 END;
        IF @already = 1
        BEGIN
            PRINT 'V095: MOHECORE.' + @t + ' already carries the Wafar write predicate.';
        END
        ELSE
        BEGIN
            -- The V031/V032 INSERT block, if present, is dropped in its own statement:
            -- a DROP and an ADD for the same operation cannot share one ALTER.
            IF EXISTS (SELECT 1 FROM sys.security_predicates sp
                        WHERE sp.target_object_id = @obj AND sp.predicate_type_desc = 'BLOCK'
                          AND sp.operation_desc = 'AFTER INSERT')
            BEGIN
                SET @sql = N'ALTER SECURITY POLICY MOHECORE.tenant_country_policy DROP BLOCK PREDICATE ON MOHECORE.'
                    + QUOTENAME(@t) + N' AFTER INSERT;';
                EXEC sp_executesql @sql;
            END
            SET @sql = N'ALTER SECURITY POLICY MOHECORE.tenant_country_policy '
                + N'ADD BLOCK PREDICATE MOHECORE.fn_wafar_write_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.' + QUOTENAME(@t) + N' AFTER INSERT, '
                + N'ADD BLOCK PREDICATE MOHECORE.fn_wafar_write_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.' + QUOTENAME(@t) + N' AFTER UPDATE, '
                + N'ADD BLOCK PREDICATE MOHECORE.fn_wafar_write_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.' + QUOTENAME(@t) + N' BEFORE UPDATE, '
                + N'ADD BLOCK PREDICATE MOHECORE.fn_wafar_write_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.' + QUOTENAME(@t) + N' BEFORE DELETE;';
            EXEC sp_executesql @sql;
            PRINT 'V095: MOHECORE.' + @t + ' now blocks INSERT, UPDATE and DELETE without the Wafar write key.';
        END
    END
    FETCH NEXT FROM c INTO @t;
END
CLOSE c;
DEALLOCATE c;
GO

/* ---- 3. Report ------------------------------------------------------------- */
PRINT 'V095: block predicates on the Wafar tables:';
SELECT OBJECT_NAME(sp.target_object_id) AS TableName, sp.operation_desc AS Operation,
       CASE WHEN sp.predicate_definition LIKE '%fn_wafar_write_predicate%' THEN 'wafar write key' ELSE 'tenant only' END AS Predicate
  FROM sys.security_predicates sp
 WHERE sp.predicate_type_desc = 'BLOCK'
   AND OBJECT_NAME(sp.target_object_id) IN ('WAFAR_SPENDING', 'WAFAR_STUDENT_FEE_PAYMENT', 'STUDENT_FINANCE_WAFAR', 'WAFAR_RECONCILIATION')
 ORDER BY 1, 2;
GO
