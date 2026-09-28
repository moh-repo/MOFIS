/*
    V032 — the tenant tables V029 missed.

    Why
    ---
    V029 gave ten tables a country column and V031 put a policy over fourteen.
    A survey of every table carrying a student reference found twelve more that
    hold tenant-owned data and are reachable through the API, with no country
    column and no policy — scoped by neither the Java layer nor the database:

        STUDENT_DATA_ARCH          1,882 rows   StudentDataArchController
        StudentInfoRequest           948        14 classes, incl. the portal
        WAFAR_SPENDING                17        WafarController
        STUDY_UNIVERSITY_FEE          15        StudyUniversityFeeController
        STUDENT_TRAVEL_TICKET         10        StudentTravelTicketController
        STUDY_UNIVERSITY_FEE_ARCH      4        StudyUniversityFeeArchController
        WAFAR_STUDENT_FEE_PAYMENT      3        WafarController
        STUDENT_PARENT                 0        StudentParentController
        STUDENT_RELATIVE               0        StudentRelativeController
        STUDENT_DEATH_CASE             0        death-case screens
        STUDENT_DOCUMENT_HISTORY       0        document version history
        StudentInquiry                 2        portal inquiry screens

    STUDENT_DATA_ARCH is the striking one: it is the archive of the root tenant
    table and holds twice as many rows as STUDENT_DATA itself. The empty ones
    matter as much in the long run — a table is easiest to tenant before it has
    data in it, and each of these has a controller waiting to fill it.

    Also closed here: STUDENT_FINANCE_PAYMENT and STUDENT_ACCOUNT already carry
    STUDY_COUNTRY_IN_ID (it predates V029) but were left out of the V031 policy.
    Both are empty today, so nothing leaked; both have controllers.

    STUDENT_DATA_ARCH is different from the rest
    --------------------------------------------
    It already has a STUDY_COUNTRY_IN column, and that column is not a tenant
    key this project added — it is the archived value, part of the record of what
    was true when the row was written. All 1,882 rows carry it and none is
    orphaned, so it works perfectly well as a predicate column.

    So it gets a policy predicate but no cascade trigger, and that is a decision
    rather than an omission. If a student later moves to another country, their
    archived rows stay with the country they were studying in at the time. That
    is what an archive is for; rewriting it to follow the student would destroy
    the fact it exists to preserve. The consequence is worth being explicit
    about: the old country keeps read access to that student's archived history.
    If that is not what you want, the fix is a separate tenant column alongside
    the archived one, not a trigger on this one.

    WAFAR_SPENDING is partly ministry-owned
    ---------------------------------------
    Only 6 of its 17 rows name a student; the other 11 are spending against a
    non-student beneficiary. Those keep a NULL tenant column, which the V031
    predicate already reads as "belongs to the ministry as a whole" — the same
    convention as MOHE_QUARTER.COUNTRY_ID (V021) and pre-V029 SAVED_QUERY rows.

    Idempotent: re-running changes nothing. Apply after V031, in SSMS.

    Operational note
    ----------------
    As with V029, the new triggers are created under QUOTED_IDENTIFIER ON, so
    that setting must be ON for any INSERT or UPDATE against these tables. The
    JDBC driver sets it ON; sqlcmd does not — pass -I for hand-written writes.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
-- Required before CREATE TRIGGER and before touching filtered indexes.
-- SSMS defaults this ON; sqlcmd defaults it OFF.
SET QUOTED_IDENTIFIER ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * The tables this script tenants, and how each reaches its student.
 *
 * Same shape as V029's #tenanted so the two scripts read alike. StudentInfoRequest
 * is the odd one out on column naming ("StudentId"), as STUDENT_YEAR_RESULT was
 * in V029.
 * ──────────────────────────────────────────────────────────────────────────── */
IF OBJECT_ID('tempdb..#tenanted32') IS NOT NULL DROP TABLE #tenanted32;
CREATE TABLE #tenanted32 (
    table_name   SYSNAME NOT NULL,
    student_col  SYSNAME NOT NULL,
    needs_column BIT     NOT NULL   -- 0 = column already exists; FK/index/trigger only
);

INSERT INTO #tenanted32 (table_name, student_col, needs_column) VALUES
    ('StudentInfoRequest',        'StudentId',  1),
    ('STUDENT_TRAVEL_TICKET',     'STUDENT_ID', 1),
    ('STUDY_UNIVERSITY_FEE',      'STUDENT_ID', 1),
    ('STUDY_UNIVERSITY_FEE_ARCH', 'STUDENT_ID', 1),
    ('WAFAR_SPENDING',            'STUDENT_ID', 1),
    ('WAFAR_STUDENT_FEE_PAYMENT', 'STUDENT_ID', 1),
    ('STUDENT_PARENT',            'STUDENT_ID', 1),
    ('STUDENT_RELATIVE',          'STUDENT_ID', 1),
    ('STUDENT_DEATH_CASE',        'STUDENT_ID', 1),
    ('STUDENT_DOCUMENT_HISTORY',  'STUDENT_ID', 1),
    ('StudentInquiry',            'StudentId',  1),
    -- Column predates V029; here for the index, FK, trigger and policy.
    ('STUDENT_FINANCE_PAYMENT',   'STUDENT_ID', 0),
    ('STUDENT_ACCOUNT',           'STUDENT_ID', 0);
GO

/* ── 1. Add the column ─────────────────────────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'
IF COL_LENGTH(''MOHECORE.' + table_name + N''', ''STUDY_COUNTRY_IN_ID'') IS NULL
    ALTER TABLE MOHECORE.' + QUOTENAME(table_name) + N' ADD STUDY_COUNTRY_IN_ID INT NULL;'
FROM #tenanted32 WHERE needs_column = 1;
EXEC sp_executesql @sql;
GO

/* ── 2. Backfill from the owning student ──────────────────────────────────────
 * An inner join, so a row with no student (WAFAR_SPENDING's non-student
 * beneficiaries) keeps NULL rather than being given a country it has no claim to.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'
UPDATE t SET t.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
FROM MOHECORE.' + QUOTENAME(table_name) + N' t
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = t.' + QUOTENAME(student_col) + N'
WHERE ISNULL(t.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(sd.STUDY_COUNTRY_IN, -1);'
FROM #tenanted32;
EXEC sp_executesql @sql;
GO

/* ── 3. Index, then foreign key ───────────────────────────────────────────────
 * The index first: the V031 predicate reads this column once per row evaluated.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = ''IX_' + table_name + N'_STUDY_COUNTRY_IN_ID''
                 AND object_id = OBJECT_ID(''MOHECORE.' + table_name + N'''))
    CREATE INDEX ' + QUOTENAME(N'IX_' + table_name + N'_STUDY_COUNTRY_IN_ID')
    + N' ON MOHECORE.' + QUOTENAME(table_name) + N' (STUDY_COUNTRY_IN_ID);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name = ''FK_' + table_name + N'_STUDY_COUNTRY_IN'')
    ALTER TABLE MOHECORE.' + QUOTENAME(table_name) + N'
        ADD CONSTRAINT ' + QUOTENAME(N'FK_' + table_name + N'_STUDY_COUNTRY_IN')
    + N' FOREIGN KEY (STUDY_COUNTRY_IN_ID)
            REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID);'
FROM #tenanted32;
EXEC sp_executesql @sql;
GO

/* ── 4. One drift trigger per table ───────────────────────────────────────────
 * Identical in shape to V029 §4, including the trailing WHERE that stops the
 * trigger re-firing on its own UPDATE. One EXEC each: CREATE OR ALTER TRIGGER
 * must be the first statement in its batch.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @tbl SYSNAME, @col SYSNAME, @sql NVARCHAR(MAX);
DECLARE tenant32_cur CURSOR LOCAL FAST_FORWARD FOR SELECT table_name, student_col FROM #tenanted32;
OPEN tenant32_cur;
FETCH NEXT FROM tenant32_cur INTO @tbl, @col;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'
CREATE OR ALTER TRIGGER MOHECORE.' + QUOTENAME(N'TR_' + @tbl + N'_TENANT') + N'
ON MOHECORE.' + QUOTENAME(@tbl) + N' AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    UPDATE t SET t.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
    FROM MOHECORE.' + QUOTENAME(@tbl) + N' t
    JOIN inserted i ON i.' + QUOTENAME(@col) + N' = t.' + QUOTENAME(@col) + N'
    JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = t.' + QUOTENAME(@col) + N'
    WHERE ISNULL(t.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(sd.STUDY_COUNTRY_IN, -1);
END;';
    EXEC sp_executesql @sql;
    FETCH NEXT FROM tenant32_cur INTO @tbl, @col;
END
CLOSE tenant32_cur;
DEALLOCATE tenant32_cur;
GO

/* ── 5. Extend the cascade ────────────────────────────────────────────────────
 * V029 created TR_STUDENT_DATA_TENANT_CASCADE listing its ten tables by hand.
 * Rather than add twelve more UPDATE blocks to it — and leave two hand-kept
 * lists to drift apart — the trigger body is now generated from the set of
 * tables that actually carry the column and a drift trigger. Adding a table in a
 * future migration and re-running this section is enough; nothing has to
 * remember to edit a list.
 *
 * STUDENT_DATA_ARCH is excluded by construction: it has no STUDY_COUNTRY_IN_ID
 * (its column is STUDY_COUNTRY_IN, the archived value), which is the distinction
 * argued for in the header.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @body NVARCHAR(MAX) = N'';

SELECT @body = @body + N'
    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.' + QUOTENAME(t.name) + N' t
      JOIN inserted i ON i.STUDENT_ID = t.' + QUOTENAME(sc.name) + N'
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);
'
FROM sys.tables t
CROSS APPLY (
    -- The student column, whichever of the two spellings this table uses.
    SELECT TOP 1 c.name
    FROM sys.columns c
    WHERE c.object_id = t.object_id AND c.name IN ('STUDENT_ID', 'StudentId')
    ORDER BY c.name
) sc
WHERE SCHEMA_NAME(t.schema_id) = 'MOHECORE'
  AND EXISTS (SELECT 1 FROM sys.columns c2
              WHERE c2.object_id = t.object_id AND c2.name = 'STUDY_COUNTRY_IN_ID')
  AND EXISTS (SELECT 1 FROM sys.triggers tr
              WHERE tr.parent_id = t.object_id AND tr.name = N'TR_' + t.name + N'_TENANT')
ORDER BY t.name;

DECLARE @cascade NVARCHAR(MAX) = N'
CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_DATA_TENANT_CASCADE
ON MOHECORE.STUDENT_DATA AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(STUDY_COUNTRY_IN) RETURN;
' + @body + N'
END;';

EXEC sp_executesql @cascade;
GO

/* ── 6. Put the new tables under the policy ───────────────────────────────────
 * A security policy cannot be added to piecemeal in a way that survives
 * re-running, so it is dropped and recreated with the full set. The predicate
 * function itself is untouched — V031 still owns it.
 *
 * The policy keeps whatever state it had. Recreating an enabled policy as
 * disabled would silently remove the protection from the fourteen tables that
 * already had it, which is the opposite of what this script is for.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @wasEnabled BIT =
    (SELECT TOP 1 is_enabled FROM sys.security_policies WHERE name = 'tenant_country_policy');
IF @wasEnabled IS NULL SET @wasEnabled = 0;

IF EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy')
    DROP SECURITY POLICY MOHECORE.tenant_country_policy;

DECLARE @policy NVARCHAR(MAX) = N'
CREATE SECURITY POLICY MOHECORE.tenant_country_policy
    -- The root table, on its own column name.
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA AFTER INSERT,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA AFTER UPDATE,
    -- The archive, on the archived value. Read-only by nature: filter, no block.
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA_ARCH';

/* Every table carrying STUDY_COUNTRY_IN_ID gets a filter predicate, and a block
 * predicate too where the application writes to it. Driving this from the
 * catalogue rather than a hand-kept list is what stops a table gaining the
 * column in a later migration and quietly staying outside the policy — the exact
 * failure this script exists to correct. */
SELECT @policy = @policy + N',
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.' + QUOTENAME(t.name)
    + CASE WHEN t.name NOT LIKE '%[_]ARCH' THEN N',
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.' + QUOTENAME(t.name)
                        + N' AFTER INSERT' ELSE N'' END
FROM sys.tables t
WHERE SCHEMA_NAME(t.schema_id) = 'MOHECORE'
  AND EXISTS (SELECT 1 FROM sys.columns c
              WHERE c.object_id = t.object_id AND c.name = 'STUDY_COUNTRY_IN_ID')
  -- STUDY_COUNTRY_IN itself is the lookup of countries, not tenant-owned data,
  -- and the *_IN lookups below are shared reference data that every tenant reads.
  AND t.name NOT IN ('STUDY_COUNTRY_IN', 'STUDY_GOV_IN', 'STUDY_COLLEGE_IN',
                     'STUDY_UNIVERSITY_IN', 'STUDY_FINANCE_BANK', 'STUDY_FINANCE_BANK_BRANCH')
ORDER BY t.name;

SET @policy = @policy + N'
WITH (STATE = ' + CASE WHEN @wasEnabled = 1 THEN N'ON' ELSE N'OFF' END + N');';

EXEC sp_executesql @policy;
PRINT 'tenant_country_policy recreated with STATE = '
      + CASE WHEN @wasEnabled = 1 THEN 'ON (as it was)' ELSE 'OFF (as it was)' END;
GO

/* ── 7. Verify ─────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== tenant column fill, by table ===';
DECLARE @check NVARCHAR(MAX) = N'';
SELECT @check = @check + N'
SELECT ''' + t.name + N''' AS table_name,
       COUNT(*) AS rows_total,
       COUNT(STUDY_COUNTRY_IN_ID) AS with_tenant,
       SUM(CASE WHEN ' + QUOTENAME(sc.name) + N' IS NULL THEN 1 ELSE 0 END) AS no_student
FROM MOHECORE.' + QUOTENAME(t.name) + N' UNION ALL'
FROM sys.tables t
CROSS APPLY (SELECT TOP 1 c.name FROM sys.columns c
             WHERE c.object_id = t.object_id AND c.name IN ('STUDENT_ID','StudentId')
             ORDER BY c.name) sc
WHERE SCHEMA_NAME(t.schema_id) = 'MOHECORE'
  AND EXISTS (SELECT 1 FROM sys.columns c2
              WHERE c2.object_id = t.object_id AND c2.name = 'STUDY_COUNTRY_IN_ID')
ORDER BY t.name;

-- Trim the trailing UNION ALL.
SET @check = LEFT(@check, LEN(@check) - LEN(N' UNION ALL')) + N' ORDER BY table_name;';
EXEC sp_executesql @check;
GO

PRINT '';
PRINT '=== tables under the tenant policy (a row per predicate) ===';
SELECT OBJECT_NAME(sp.target_object_id) AS table_name,
       sp.predicate_type_desc,
       p.is_enabled AS policy_enabled
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id = sp.object_id
WHERE p.name = 'tenant_country_policy'
ORDER BY table_name, sp.predicate_type_desc;
GO

PRINT '';
PRINT '=== anything still holding student data outside the policy ===';
PRINT '(expected: empty. A row here is a table this migration should have covered.)';
SELECT t.name AS table_name, SUM(pt.rows) AS row_count
FROM sys.tables t
JOIN sys.partitions pt ON pt.object_id = t.object_id AND pt.index_id IN (0, 1)
WHERE SCHEMA_NAME(t.schema_id) = 'MOHECORE'
  AND EXISTS (SELECT 1 FROM sys.columns c
              WHERE c.object_id = t.object_id AND c.name IN ('STUDENT_ID', 'StudentId'))
  AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp
                  JOIN sys.security_policies p ON p.object_id = sp.object_id
                  WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = t.object_id)
  -- Known and deliberate: one-off staging and manual-backup copies, dropped by
  -- PROPOSED_cleanup_leftover_tables.sql rather than tenanted here.
  AND t.name NOT IN ('Q2_2024_EGY', 'STUDENT_DATA_BKUP',
  -- Authentication tables, deliberately outside the policy. A portal session is
  -- read in order to work out who is asking, which is what determines the scope
  -- in the first place; filtering that read by the scope it produces is circular,
  -- and would make login impossible rather than secure. They are protected by
  -- holding no student data beyond an id, and by the token being the secret.
  -- Same reasoning as UMSCORE.Users, which TenantContextFilter reads as system.
                     'StudentPortalSession', 'StudentPortalAccount')
GROUP BY t.name
ORDER BY row_count DESC;
GO
