/*
    V029 — give every tenant-owned table its own country column.

    Why
    ---
    STUDY_COUNTRY_IN is this application's tenant key: UMSCORE.UserCountries holds
    STUDY_COUNTRY_IN ids, and CountryScopeService loads ministry scope from
    STUDY_COUNTRY_IN, so "which country's data is this" is answered by the country
    a student studies in.

    Seven tables that hold tenant-owned data had no country column at all. They
    were scoped only by joining back to STUDENT_DATA, which means:

      - every query has to remember to join, and a query that forgets returns
        every tenant's rows;
      - the Query Builder (QueryExecutionService) refuses to query them at all,
        because it only accepts tables carrying STUDY_COUNTRY_IN / _ID
        (QueryExecutionService.java:99) — so a whole set of tables is unusable
        there rather than usable-and-scoped;
      - Row-Level Security (V031) needs a column on the table itself to filter on;
        a policy cannot follow a join.

    What this script does
    ---------------------
    1. Adds STUDY_COUNTRY_IN_ID to the seven student-owned tables and backfills it
       from the owning student.
    2. Adds a foreign key and an index on every tenant column (the RLS predicate
       reads it once per row, so it must be indexed).
    3. Adds triggers that re-derive the column from STUDENT_DATA on insert and
       update, plus one trigger on STUDENT_DATA that cascades a change of a
       student's study country down to all child rows. This is what makes the
       denormalisation safe: it cannot drift, because nothing but these triggers
       decides its value.
    4. Adds a nullable STUDY_COUNTRY_IN_ID to the three owner-owned tables
       (SAVED_QUERY, SCHEDULED_REPORT, REPORT_ACCESS_LOG). These have no student
       to derive from, so the service layer stamps them from the acting user's
       scope; NULL keeps its existing meaning of "ministry-wide", the same
       convention MOHE_QUARTER.COUNTRY_ID already uses (V021).
    5. Closes an existing gap: STUDENT_FINANCE, _ARCH and _WAFAR already carried
       STUDY_COUNTRY_IN_ID, correctly populated (941/941, 3764/3764, 23/23) and in
       100% agreement with the student record — but with no FK and no trigger, so
       they were consistent by luck. They now get both.

    Deliberately NOT tenanted
    -------------------------
    Global reference data, where a country column would be wrong because the row
    belongs to every tenant: STUDENT_MAJOR, STUDENT_DEGREE, STUDENT_CURRENT_LEVEL,
    STUDY_CURRENCY_IN, all LU_* tables, REPORT_TYPE_SETTINGS. Also
    STUDENT_TOTAL_REPORT_ALL — despite the name it is a two-column label table
    (STUDENT_TOTAL_REPORT_NAME, STUDENT_TOTAL_NAME) with no student reference and
    no per-country meaning.

    Expected result
    ---------------
    Every backfilled column agrees with its student:

        SELECT COUNT(*) FROM MOHECORE.STUDENT_EXTENSION t
        JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = t.STUDENT_ID
        WHERE ISNULL(t.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(sd.STUDY_COUNTRY_IN, -1);
        -- 0, and the same for every table below

    Idempotent: re-running changes nothing.

    Operational note
    ----------------
    Because these tables now carry triggers created under QUOTED_IDENTIFIER ON,
    SQL Server requires that setting to be ON for any INSERT or UPDATE against
    them. The Microsoft JDBC driver sets it ON by default, so the application is
    unaffected. Ad-hoc sqlcmd sessions are not — sqlcmd defaults it OFF, so pass
    -I when writing to these tables by hand, or the write fails with
    "UPDATE failed because the following SET options have incorrect settings".

    Apply in SSMS.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
-- Required before touching tables that carry filtered indexes, and before
-- CREATE TRIGGER. SSMS defaults this ON; sqlcmd defaults it OFF.
SET QUOTED_IDENTIFIER ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * The tenanted tables and how each one reaches its student.
 *
 * Held in a temp table so the mechanical steps (add column, backfill, index,
 * foreign key, trigger) are applied identically to every table rather than
 * hand-repeated 10 times with the chance of one being subtly different.
 *
 * STUDENT_YEAR_RESULT is the odd one out: its key column is "StudentId", not
 * "STUDENT_ID" like every other table.
 * ──────────────────────────────────────────────────────────────────────────── */
IF OBJECT_ID('tempdb..#tenanted') IS NOT NULL DROP TABLE #tenanted;
CREATE TABLE #tenanted (
    table_name   SYSNAME NOT NULL,
    student_col  SYSNAME NOT NULL,
    needs_column BIT     NOT NULL   -- 0 = column already exists, FK/index/trigger only
);

INSERT INTO #tenanted (table_name, student_col, needs_column) VALUES
    ('STUDENT_EXTENSION',       'STUDENT_ID', 1),
    ('STUDENT_LOAN',            'STUDENT_ID', 1),
    ('STUDENT_BOOKS_ALLOWANCE', 'STUDENT_ID', 1),
    ('STUDENT_YEAR_RESULT',     'StudentId',  1),
    ('STUDENT_DOCUMENTS',       'STUDENT_ID', 1),
    ('PAYMENT_AUTH',            'STUDENT_ID', 1),
    ('WAFAR_RECONCILIATION',    'STUDENT_ID', 1),
    -- Already carry the column; here for the FK, index and drift trigger.
    ('STUDENT_FINANCE',         'STUDENT_ID', 0),
    ('STUDENT_FINANCE_ARCH',    'STUDENT_ID', 0),
    ('STUDENT_FINANCE_WAFAR',   'STUDENT_ID', 0);
GO

/* ── 1. Add the column ─────────────────────────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'
IF COL_LENGTH(''MOHECORE.' + table_name + N''', ''STUDY_COUNTRY_IN_ID'') IS NULL
    ALTER TABLE MOHECORE.' + QUOTENAME(table_name) + N' ADD STUDY_COUNTRY_IN_ID INT NULL;'
FROM #tenanted WHERE needs_column = 1;
EXEC sp_executesql @sql;
GO

/* ── 2. Backfill from the owning student ───────────────────────────────────── */
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'
UPDATE t SET t.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
FROM MOHECORE.' + QUOTENAME(table_name) + N' t
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = t.' + QUOTENAME(student_col) + N'
WHERE ISNULL(t.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(sd.STUDY_COUNTRY_IN, -1);'
FROM #tenanted;
EXEC sp_executesql @sql;
GO

/* ── 3. Index, then foreign key ────────────────────────────────────────────────
 * The index comes first because the RLS predicate in V031 reads this column for
 * every row it evaluates.
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
FROM #tenanted
-- An index named IX_STUDENT_FINANCE_STUDY_COUNTRY_IN_ID already exists; the
-- guard above skips it.
;
EXEC sp_executesql @sql;
GO

/* ── 4. Keep it in step: one trigger per child table ──────────────────────────
 * Each generated trigger is this, with the table and student column substituted:
 *
 *     CREATE OR ALTER TRIGGER MOHECORE.TR_<table>_TENANT
 *     ON MOHECORE.<table> AFTER INSERT, UPDATE AS
 *     BEGIN
 *         SET NOCOUNT ON;
 *         IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;
 *         UPDATE t SET t.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
 *         FROM MOHECORE.<table> t
 *         JOIN inserted i ON i.<key> = t.<key>
 *         JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = t.<student_col>
 *         WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(sd.STUDY_COUNTRY_IN,-1);
 *     END
 *
 * The final WHERE matters: without it the UPDATE inside the trigger would fire
 * the trigger again on every write. With it, the second pass finds nothing to
 * change and recursion stops.
 *
 * Joining "inserted" on the student column rather than the primary key keeps this
 * generator independent of each table's key shape — several of these tables have
 * composite or non-obvious keys, and the student column is what we need anyway.
 * ──────────────────────────────────────────────────────────────────────────── */
-- One EXEC per trigger: CREATE OR ALTER TRIGGER has to be the first statement in
-- its batch, so these cannot be concatenated into a single sp_executesql call.
DECLARE @tbl SYSNAME, @col SYSNAME, @sql NVARCHAR(MAX);
DECLARE tenant_cur CURSOR LOCAL FAST_FORWARD FOR SELECT table_name, student_col FROM #tenanted;
OPEN tenant_cur;
FETCH NEXT FROM tenant_cur INTO @tbl, @col;
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
    FETCH NEXT FROM tenant_cur INTO @tbl, @col;
END
CLOSE tenant_cur;
DEALLOCATE tenant_cur;
GO

/* ── 5. Cascade a student's move to another country ────────────────────────────
 * The child triggers above fire on writes to the child. This one covers the other
 * direction: a student's STUDY_COUNTRY_IN changing, which must move all of their
 * rows with them or the child copies go stale.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_DATA_TENANT_CASCADE
ON MOHECORE.STUDENT_DATA AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(STUDY_COUNTRY_IN) RETURN;

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_EXTENSION t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_LOAN t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_BOOKS_ALLOWANCE t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_YEAR_RESULT t JOIN inserted i ON i.STUDENT_ID = t.StudentId
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_DOCUMENTS t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.PAYMENT_AUTH t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.WAFAR_RECONCILIATION t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_FINANCE t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_FINANCE_ARCH t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);

    UPDATE t SET t.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN
      FROM MOHECORE.STUDENT_FINANCE_WAFAR t JOIN inserted i ON i.STUDENT_ID = t.STUDENT_ID
     WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1) <> ISNULL(i.STUDY_COUNTRY_IN,-1);
END;
GO

/* ── 6. Owner-owned tables ────────────────────────────────────────────────────
 * SAVED_QUERY, SCHEDULED_REPORT and REPORT_ACCESS_LOG belong to a user, not to a
 * student, so there is nothing to derive the country from in SQL. The column is
 * added here and the service layer stamps it from the acting user's scope on
 * insert. Existing rows stay NULL, which keeps its established meaning of
 * "ministry-wide" (the same convention MOHE_QUARTER.COUNTRY_ID uses) rather than
 * guessing a country for data that predates the column.
 * ──────────────────────────────────────────────────────────────────────────── */
IF COL_LENGTH('MOHECORE.SAVED_QUERY', 'STUDY_COUNTRY_IN_ID') IS NULL
    ALTER TABLE MOHECORE.SAVED_QUERY ADD STUDY_COUNTRY_IN_ID INT NULL;
GO
IF COL_LENGTH('MOHECORE.SCHEDULED_REPORT', 'STUDY_COUNTRY_IN_ID') IS NULL
    ALTER TABLE MOHECORE.SCHEDULED_REPORT ADD STUDY_COUNTRY_IN_ID INT NULL;
GO
IF COL_LENGTH('MOHECORE.REPORT_ACCESS_LOG', 'STUDY_COUNTRY_IN_ID') IS NULL
    ALTER TABLE MOHECORE.REPORT_ACCESS_LOG ADD STUDY_COUNTRY_IN_ID INT NULL;
GO

DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = ''IX_' + v.t + N'_STUDY_COUNTRY_IN_ID''
                 AND object_id = OBJECT_ID(''MOHECORE.' + v.t + N'''))
    CREATE INDEX ' + QUOTENAME(N'IX_' + v.t + N'_STUDY_COUNTRY_IN_ID')
    + N' ON MOHECORE.' + QUOTENAME(v.t) + N' (STUDY_COUNTRY_IN_ID);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = ''FK_' + v.t + N'_STUDY_COUNTRY_IN'')
    ALTER TABLE MOHECORE.' + QUOTENAME(v.t) + N'
        ADD CONSTRAINT ' + QUOTENAME(N'FK_' + v.t + N'_STUDY_COUNTRY_IN')
    + N' FOREIGN KEY (STUDY_COUNTRY_IN_ID)
            REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID);'
FROM (VALUES ('SAVED_QUERY'), ('SCHEDULED_REPORT'), ('REPORT_ACCESS_LOG')) AS v(t);
EXEC sp_executesql @sql;
GO

DROP TABLE #tenanted;
GO

/* ── 7. Verify ─────────────────────────────────────────────────────────────── */
PRINT '=== tenant column agreement with STUDENT_DATA (every row must be 0) ===';
SELECT 'STUDENT_EXTENSION' tbl, COUNT(*) mismatched FROM MOHECORE.STUDENT_EXTENSION t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'STUDENT_LOAN', COUNT(*) FROM MOHECORE.STUDENT_LOAN t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'STUDENT_BOOKS_ALLOWANCE', COUNT(*) FROM MOHECORE.STUDENT_BOOKS_ALLOWANCE t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'STUDENT_YEAR_RESULT', COUNT(*) FROM MOHECORE.STUDENT_YEAR_RESULT t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.StudentId WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'STUDENT_DOCUMENTS', COUNT(*) FROM MOHECORE.STUDENT_DOCUMENTS t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'PAYMENT_AUTH', COUNT(*) FROM MOHECORE.PAYMENT_AUTH t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'WAFAR_RECONCILIATION', COUNT(*) FROM MOHECORE.WAFAR_RECONCILIATION t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'STUDENT_FINANCE', COUNT(*) FROM MOHECORE.STUDENT_FINANCE t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'STUDENT_FINANCE_ARCH', COUNT(*) FROM MOHECORE.STUDENT_FINANCE_ARCH t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1)
UNION ALL SELECT 'STUDENT_FINANCE_WAFAR', COUNT(*) FROM MOHECORE.STUDENT_FINANCE_WAFAR t JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID=t.STUDENT_ID WHERE ISNULL(t.STUDY_COUNTRY_IN_ID,-1)<>ISNULL(sd.STUDY_COUNTRY_IN,-1);
GO
