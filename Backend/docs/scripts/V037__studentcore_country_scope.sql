/*
    V037 — country scope across every table in STUDENTCORE.

    Why
    ---
    STUDENTCORE is the student portal's own realm: 941 users, their sessions,
    their change requests, and an RBAC catalogue of roles, permissions and groups.
    None of it carried a country. V036 pinned one onto student_sessions to fix
    login; this finishes the schema.

    The gap it closes is on the ministry side rather than the portal side. A
    portal student only ever reads their own row, so the risk was never a student
    seeing another country's data. It was the staff screens: StudentUserAdmin,
    StudentGroupAdmin, StudentSessionAdmin and the rest list and edit portal
    accounts, and with no country on the tables a country officer could manage
    every country's students. Now the database refuses.

    Two kinds of table, treated differently
    ---------------------------------------
    Student-owned — the country is derived and maintained, exactly as V029/V032 do
    for MOHECORE:

        student_users            941   student_id
        student_user_groups      941   student_user_id -> student_users.student_id
        student_change_requests    8   student_id
        student_user_roles         0   student_user_id -> student_users.student_id
        student_sessions          11   column added in V036 (see below)

    RBAC catalogue — there is no student to derive from, and inventing one would
    be worse than leaving it open:

        student_permissions        6
        student_role_permissions  11
        student_roles              2
        student_groups             1
        student_group_roles        1

    These get a nullable column that stays NULL, meaning ministry-wide. That is
    not a placeholder: the V031 predicate already reads a NULL tenant column as
    "belongs to every tenant", the convention MOHE_QUARTER.COUNTRY_ID has used
    since V021. So a role called STUDENT is visible to every country, as it must
    be, while the column is there for the day someone needs a role that is not.

    student_sessions is deliberately not trigger-maintained
    ------------------------------------------------------
    Every other student-owned table here gets a drift trigger that re-derives the
    country from STUDENT_DATA, and a cascade so a student moving country takes
    their rows with them. A session does not. Its country is a snapshot of who the
    caller was when the token was issued (V036), and a student's country changing
    mid-session should end that session rather than silently move its scope. It is
    added to the policy but left out of both triggers.

    Application changes this depends on
    -----------------------------------
    Putting the auth tables behind a predicate re-creates the circular dependency
    V032 avoided by leaving StudentPortalSession outside the policy: reading a
    session is how the caller's scope is established, so that read cannot be
    filtered by the scope it produces. Three paths were elevated first, and this
    script is not safe to apply without them:

      TenantContextFilter                  grants SYSTEM to the portal auth paths
                                           so login can read STUDENT_DATA (V036)
      StudentPortalTokenInterceptor        elevates both token resolutions, or
                                           every logged-in student gets a 401
      StudentUserSeeder                    elevates its ApplicationRunner, which
                                           otherwise seeds nothing and says so

    Idempotent. Apply in SSMS after V036.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
-- Required before CREATE TRIGGER; SSMS defaults it ON, sqlcmd defaults it OFF.
SET QUOTED_IDENTIFIER ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * The student-owned tables, and how each reaches its student.
 *
 * student_hops = 1 when the table carries student_id itself, 2 when it has to go
 * through student_users. The two-hop tables are the join tables that attach a
 * portal user to a group or a role.
 * ──────────────────────────────────────────────────────────────────────────── */
IF OBJECT_ID('tempdb..#sc_owned') IS NOT NULL DROP TABLE #sc_owned;
CREATE TABLE #sc_owned (
    table_name   SYSNAME NOT NULL,
    student_hops TINYINT NOT NULL,
    needs_column BIT     NOT NULL,
    maintained   BIT     NOT NULL   -- 0 = no drift trigger (student_sessions)
);

INSERT INTO #sc_owned (table_name, student_hops, needs_column, maintained) VALUES
    ('student_users',           1, 1, 1),
    ('student_change_requests', 1, 1, 1),
    ('student_user_groups',     2, 1, 1),
    ('student_user_roles',      2, 1, 1),
    -- Column added by V036. Here only to join the policy; see the header on why
    -- a session's country is a snapshot and must not be re-derived.
    ('student_sessions',        1, 0, 0);

IF OBJECT_ID('tempdb..#sc_global') IS NOT NULL DROP TABLE #sc_global;
CREATE TABLE #sc_global (table_name SYSNAME NOT NULL);
INSERT INTO #sc_global (table_name) VALUES
    ('student_roles'), ('student_permissions'), ('student_role_permissions'),
    ('student_groups'), ('student_group_roles');
GO

/* ── 1. Add the column everywhere ─────────────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'
IF COL_LENGTH(''STUDENTCORE.' + table_name + N''', ''STUDY_COUNTRY_IN_ID'') IS NULL
    ALTER TABLE STUDENTCORE.' + QUOTENAME(table_name) + N' ADD STUDY_COUNTRY_IN_ID INT NULL;'
FROM (SELECT table_name FROM #sc_owned WHERE needs_column = 1
      UNION ALL SELECT table_name FROM #sc_global) t;
EXEC sp_executesql @sql;
GO

/* ── 2. Backfill the student-owned tables ─────────────────────────────────────
 * The catalogue tables are left NULL on purpose — that is their value, not a
 * missing one.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';

-- One hop: the table carries student_id.
SELECT @sql = @sql + N'
UPDATE t SET t.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
FROM STUDENTCORE.' + QUOTENAME(table_name) + N' t
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = t.student_id
WHERE ISNULL(t.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(sd.STUDY_COUNTRY_IN, -1);'
FROM #sc_owned WHERE student_hops = 1 AND needs_column = 1;

-- Two hops: through student_users.
SELECT @sql = @sql + N'
UPDATE t SET t.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
FROM STUDENTCORE.' + QUOTENAME(table_name) + N' t
JOIN STUDENTCORE.student_users su ON su.student_user_id = t.student_user_id
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
WHERE ISNULL(t.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(sd.STUDY_COUNTRY_IN, -1);'
FROM #sc_owned WHERE student_hops = 2;

EXEC sp_executesql @sql;
GO

/* ── 3. Index and foreign key ─────────────────────────────────────────────────
 * Index on the student-owned tables only: the predicate reads that column once
 * per row, and those are the tables with rows worth indexing. The catalogue
 * tables have single-digit row counts and an all-NULL column.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';

SELECT @sql = @sql + N'
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = ''IX_' + table_name + N'_STUDY_COUNTRY_IN_ID''
                 AND object_id = OBJECT_ID(''STUDENTCORE.' + table_name + N'''))
    CREATE INDEX ' + QUOTENAME(N'IX_' + table_name + N'_STUDY_COUNTRY_IN_ID')
    + N' ON STUDENTCORE.' + QUOTENAME(table_name) + N' (STUDY_COUNTRY_IN_ID);'
FROM #sc_owned WHERE needs_column = 1;

SELECT @sql = @sql + N'
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = ''FK_' + table_name + N'_STUDY_COUNTRY_IN'')
    ALTER TABLE STUDENTCORE.' + QUOTENAME(table_name) + N'
        ADD CONSTRAINT ' + QUOTENAME(N'FK_' + table_name + N'_STUDY_COUNTRY_IN')
    + N' FOREIGN KEY (STUDY_COUNTRY_IN_ID)
            REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID);'
FROM (SELECT table_name FROM #sc_owned WHERE needs_column = 1
      UNION ALL SELECT table_name FROM #sc_global) t;

EXEC sp_executesql @sql;
GO

/* ── 4. Drift triggers ────────────────────────────────────────────────────────
 * Same shape as V029 §4 and V032 §4, including the trailing WHERE that stops the
 * trigger firing on its own UPDATE. One EXEC each: CREATE OR ALTER TRIGGER must
 * be the first statement in its batch.
 *
 * student_sessions is excluded by maintained = 0.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @tbl SYSNAME, @hops TINYINT, @sql NVARCHAR(MAX);
DECLARE sc_cur CURSOR LOCAL FAST_FORWARD FOR
    SELECT table_name, student_hops FROM #sc_owned WHERE maintained = 1;
OPEN sc_cur;
FETCH NEXT FROM sc_cur INTO @tbl, @hops;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'
CREATE OR ALTER TRIGGER STUDENTCORE.' + QUOTENAME(N'TR_' + @tbl + N'_TENANT') + N'
ON STUDENTCORE.' + QUOTENAME(@tbl) + N' AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
    UPDATE t SET t.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
    FROM STUDENTCORE.' + QUOTENAME(@tbl) + N' t
    JOIN inserted i ON i.' + CASE WHEN @hops = 1 THEN N'student_id = t.student_id'
                                  ELSE N'student_user_id = t.student_user_id' END + N'
' + CASE WHEN @hops = 2
         THEN N'    JOIN STUDENTCORE.student_users su ON su.student_user_id = t.student_user_id
    JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id'
         ELSE N'    JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = t.student_id' END + N'
    WHERE ISNULL(t.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(sd.STUDY_COUNTRY_IN, -1);
END;';
    EXEC sp_executesql @sql;
    FETCH NEXT FROM sc_cur INTO @tbl, @hops;
END
CLOSE sc_cur;
DEALLOCATE sc_cur;
GO

/* ── 5. Extend the cascade to STUDENTCORE ─────────────────────────────────────
 * V032 rebuilt TR_STUDENT_DATA_TENANT_CASCADE from the catalogue, but scoped its
 * query to MOHECORE. A student moving country must take their portal rows with
 * them too, so the trigger is regenerated across both schemas.
 *
 * Driven by the same rule as V032: a table is cascaded if it carries
 * STUDY_COUNTRY_IN_ID and has a TR_<table>_TENANT drift trigger. That rule
 * excludes student_sessions automatically, since section 4 gave it no trigger.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @body NVARCHAR(MAX) = N'';

SELECT @body = @body + N'
    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' t
      JOIN inserted i ON i.STUDENT_ID = t.' + QUOTENAME(sc.name) + N'
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);
'
FROM sys.tables t
CROSS APPLY (
    SELECT TOP 1 c.name
    FROM sys.columns c
    WHERE c.object_id = t.object_id AND c.name IN ('STUDENT_ID', 'StudentId', 'student_id')
    ORDER BY c.name
) sc
WHERE SCHEMA_NAME(t.schema_id) IN ('MOHECORE', 'STUDENTCORE')
  AND EXISTS (SELECT 1 FROM sys.columns c2
              WHERE c2.object_id = t.object_id AND c2.name = 'STUDY_COUNTRY_IN_ID')
  AND EXISTS (SELECT 1 FROM sys.triggers tr
              WHERE tr.parent_id = t.object_id AND tr.name = N'TR_' + t.name + N'_TENANT')
ORDER BY SCHEMA_NAME(t.schema_id), t.name;

/* The two-hop join tables have no student_id, so the catalogue query above cannot
 * reach them and they need their own block.
 *
 * An earlier version of this script left them out, reasoning that the UPDATE on
 * student_users would fire their drift trigger. It does not: that trigger is
 * defined ON student_user_groups, so it fires for writes to that table, not to
 * its parent. Moving a student to another country was verified to move
 * student_users and leave student_user_groups behind — which under the policy
 * means the new country cannot see the student's group membership while the old
 * country still can. */
SELECT @body = @body + N'
    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM STUDENTCORE.' + QUOTENAME(o.table_name) + N' t
      JOIN STUDENTCORE.student_users su ON su.student_user_id = t.student_user_id
      JOIN inserted i ON i.STUDENT_ID = su.student_id
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);
'
FROM #sc_owned o
WHERE o.student_hops = 2 AND o.maintained = 1
ORDER BY o.table_name;

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

/* ── 6. Put every STUDENTCORE table under the policy ──────────────────────────
 * A security policy cannot be extended in place and re-run, so it is dropped and
 * rebuilt from the catalogue — the same approach as V032 §6, widened to both
 * schemas. It keeps whatever state it had.
 *
 * BLOCK predicates go on the student-owned tables so a country officer cannot
 * create or move a portal account into another country. Not on the RBAC
 * catalogue: its rows are ministry-wide NULLs, and the predicate passes NULL for
 * everyone, so a block there would be theatre.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @wasEnabled BIT =
    (SELECT TOP 1 is_enabled FROM sys.security_policies WHERE name = 'tenant_country_policy');
IF @wasEnabled IS NULL SET @wasEnabled = 0;

IF EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy')
    DROP SECURITY POLICY MOHECORE.tenant_country_policy;

DECLARE @policy NVARCHAR(MAX) = N'
CREATE SECURITY POLICY MOHECORE.tenant_country_policy
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA AFTER INSERT,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA AFTER UPDATE,
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA_ARCH';

/* Every table in either schema carrying STUDY_COUNTRY_IN_ID. Driving this from
 * the catalogue is what stops a table gaining the column later and quietly
 * staying outside the policy. */
SELECT @policy = @policy + N',
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON '
    + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name)
    + CASE WHEN t.name NOT LIKE '%[_]ARCH'
                AND NOT EXISTS (SELECT 1 FROM #sc_global g WHERE g.table_name = t.name)
           THEN N',
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON '
                + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' AFTER INSERT'
           ELSE N'' END
FROM sys.tables t
WHERE SCHEMA_NAME(t.schema_id) IN ('MOHECORE', 'STUDENTCORE')
  AND EXISTS (SELECT 1 FROM sys.columns c
              WHERE c.object_id = t.object_id AND c.name = 'STUDY_COUNTRY_IN_ID')
  -- Shared reference data every tenant reads; a country column on these is the
  -- country the row describes, not the tenant that owns it.
  AND t.name NOT IN ('STUDY_COUNTRY_IN', 'STUDY_GOV_IN', 'STUDY_COLLEGE_IN',
                     'STUDY_UNIVERSITY_IN', 'STUDY_FINANCE_BANK', 'STUDY_FINANCE_BANK_BRANCH')
ORDER BY SCHEMA_NAME(t.schema_id), t.name;

SET @policy = @policy + N'
WITH (STATE = ' + CASE WHEN @wasEnabled = 1 THEN N'ON' ELSE N'OFF' END + N');';

EXEC sp_executesql @policy;
PRINT 'tenant_country_policy rebuilt across MOHECORE and STUDENTCORE, STATE = '
      + CASE WHEN @wasEnabled = 1 THEN 'ON (as it was)' ELSE 'OFF (as it was)' END;
GO

/* ── 7. Verify ────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== every STUDENTCORE table now has a country column ===';
SELECT t.name AS table_name,
       (SELECT SUM(p.rows) FROM sys.partitions p
        WHERE p.object_id = t.object_id AND p.index_id IN (0,1)) AS row_count,
       CASE WHEN COL_LENGTH('STUDENTCORE.' + t.name, 'STUDY_COUNTRY_IN_ID') IS NULL
            THEN 'MISSING' ELSE 'yes' END AS has_column,
       CASE WHEN EXISTS (SELECT 1 FROM #sc_global g WHERE g.table_name = t.name)
            THEN 'ministry-wide (NULL by design)' ELSE 'student-owned' END AS kind,
       CASE WHEN EXISTS (SELECT 1 FROM sys.triggers tr
                         WHERE tr.parent_id = t.object_id AND tr.name = N'TR_' + t.name + N'_TENANT')
            THEN 'yes' ELSE 'no' END AS drift_trigger
FROM sys.tables t
WHERE SCHEMA_NAME(t.schema_id) = 'STUDENTCORE'
ORDER BY t.name;
GO

PRINT '';
PRINT '=== student-owned rows agree with the student record ===';
PRINT '(expected: 0 disagreeing on each)';
SELECT 'student_users' AS table_name, COUNT(*) AS disagreeing
FROM STUDENTCORE.student_users su
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
WHERE ISNULL(su.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL
SELECT 'student_user_groups', COUNT(*)
FROM STUDENTCORE.student_user_groups g
JOIN STUDENTCORE.student_users su ON su.student_user_id = g.student_user_id
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
WHERE ISNULL(g.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL
SELECT 'student_change_requests', COUNT(*)
FROM STUDENTCORE.student_change_requests cr
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = cr.student_id
WHERE ISNULL(cr.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(sd.STUDY_COUNTRY_IN,-1);
GO

PRINT '';
PRINT '=== the RBAC catalogue stays ministry-wide ===';
PRINT '(expected: 0 with a country on each — NULL is the intended value)';
SELECT 'student_roles' AS table_name, COUNT(STUDY_COUNTRY_IN_ID) AS with_country FROM STUDENTCORE.student_roles
UNION ALL SELECT 'student_permissions',      COUNT(STUDY_COUNTRY_IN_ID) FROM STUDENTCORE.student_permissions
UNION ALL SELECT 'student_role_permissions', COUNT(STUDY_COUNTRY_IN_ID) FROM STUDENTCORE.student_role_permissions
UNION ALL SELECT 'student_groups',           COUNT(STUDY_COUNTRY_IN_ID) FROM STUDENTCORE.student_groups
UNION ALL SELECT 'student_group_roles',      COUNT(STUDY_COUNTRY_IN_ID) FROM STUDENTCORE.student_group_roles;
GO

PRINT '';
PRINT '=== policy coverage, by schema ===';
SELECT OBJECT_SCHEMA_NAME(sp.target_object_id) AS schema_name,
       COUNT(DISTINCT sp.target_object_id) AS tables_covered,
       COUNT(*) AS predicates
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id = sp.object_id
WHERE p.name = 'tenant_country_policy'
GROUP BY OBJECT_SCHEMA_NAME(sp.target_object_id)
ORDER BY schema_name;
GO

PRINT '';
PRINT '=== anything left in STUDENTCORE outside the policy ===';
PRINT '(expected: empty)';
SELECT t.name AS table_name
FROM sys.tables t
WHERE SCHEMA_NAME(t.schema_id) = 'STUDENTCORE'
  AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp
                  JOIN sys.security_policies p ON p.object_id = sp.object_id
                  WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = t.object_id)
ORDER BY t.name;
GO
