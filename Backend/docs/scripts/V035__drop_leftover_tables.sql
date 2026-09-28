/*
    V035 — drop the five leftover tables.

    !!  THIS SCRIPT DOES NOTHING UNTIL YOU SET @Confirm = 1 BELOW.  !!

    As shipped it only reports. That is deliberate: two of these tables hold real
    student rows, and dropping 1,938 rows of them is not a decision a migration
    should make on your behalf the first time it is run. Read the report, satisfy
    yourself, then set the flag and run it again.

    What it drops, and why each is leftover
    ---------------------------------------
      MOHECORE.Q2_2024_EGY          939 rows  one-off Q2 2024 staging table. Not
                                              mapped by any entity, referenced by
                                              no view, and holding a frozen copy
                                              of student data that nothing reads.
      MOHECORE.STUDENT_DATA_BKUP    999 rows  a manual backup copy of STUDENT_DATA
                                              taken by hand at some point. The
                                              live table has 941 rows; this one is
                                              a snapshot of a different day.
      MOHECORE.temp_MajorNameMap     67 rows  import helper from a major-name
                                              migration that has long since run.
      MOHECORE.Users___old            5 rows  legacy users table. Authentication
                                              lives in UMSCORE.Users.
      MOHECORE.ROLES                  0 rows  empty duplicate of UMSCORE.Roles.

    On MOHECORE.ROLES in particular
    -------------------------------
    There are two tables called Roles in this database and only one of them is
    real:

        UMSCORE.Roles     15 rows, four foreign keys pointing at it from
                          UserRoles, GroupRoles, RolePermissions and itself
        MOHECORE.ROLES     0 rows, nothing references it

    SQL Server compares these names case-insensitively, so `DROP TABLE ROLES`
    run in the wrong default schema takes authorisation down for the whole
    ministry. Every statement below is schema-qualified and guarded by an
    object-id check for exactly that reason.

    Why these were never dropped before
    -----------------------------------
    PROPOSED_cleanup_leftover_tables.sql has listed them since the first system
    review, with its DROP statements commented out and a note to run them
    manually. Nobody did. Leaving them costs more than disk: Q2_2024_EGY carries
    student rows with no tenant column and was, until this review, invisible to
    the tenancy work — it shows up in the V032 verification query as a table
    holding student data outside the policy, and has to be excluded there by name.

    Before you set the flag
    -----------------------
    BACK UP THE DATABASE. This is not reversible from within the script.
    A table dropped here is recoverable only from that backup.

    Idempotent either way: dropping a table that is already gone is a no-op.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 *  SET THIS TO 1 TO ACTUALLY DROP. Leave at 0 to report only.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @Confirm BIT = 0;

DECLARE @leftovers TABLE (schema_name SYSNAME, table_name SYSNAME, note NVARCHAR(200));
INSERT INTO @leftovers VALUES
    ('MOHECORE', 'Q2_2024_EGY',       N'one-off Q2 2024 staging copy of student data'),
    ('MOHECORE', 'STUDENT_DATA_BKUP', N'manual backup copy of STUDENT_DATA'),
    ('MOHECORE', 'temp_MajorNameMap', N'import helper from the major-name migration'),
    ('MOHECORE', 'Users___old',       N'legacy users table; UMS lives in UMSCORE.Users'),
    ('MOHECORE', 'ROLES',             N'empty duplicate of UMSCORE.Roles — see header');

/* ── 1. What is there, and is anything using it? ──────────────────────────── */
PRINT '=== leftover tables, and what still depends on them ===';

SELECT l.schema_name + '.' + l.table_name AS table_name,
       CASE WHEN OBJECT_ID(l.schema_name + '.' + l.table_name, 'U') IS NULL
            THEN NULL ELSE
            (SELECT SUM(p.rows) FROM sys.partitions p
             WHERE p.object_id = OBJECT_ID(l.schema_name + '.' + l.table_name, 'U')
               AND p.index_id IN (0, 1)) END AS row_count,
       CASE WHEN OBJECT_ID(l.schema_name + '.' + l.table_name, 'U') IS NULL
            THEN 'already gone' ELSE 'present' END AS state,
       -- A view or procedure that reads it. Dropping a table something selects
       -- from turns a working report into a runtime error, so this must be 0.
       (SELECT COUNT(DISTINCT d.referencing_id) FROM sys.sql_expression_dependencies d
        WHERE d.referenced_entity_name = l.table_name
          AND d.referenced_schema_name = l.schema_name) AS referencing_modules,
       -- A foreign key from another table into this one.
       (SELECT COUNT(*) FROM sys.foreign_keys fk
        WHERE fk.referenced_object_id = OBJECT_ID(l.schema_name + '.' + l.table_name, 'U')) AS inbound_foreign_keys,
       l.note
FROM @leftovers l
ORDER BY l.table_name;

/* ── 2. Refuse to drop anything still referenced ──────────────────────────────
 * The checks above are reported for a human to read; this one stops the script.
 * A dependency appearing later — someone builds a view over Q2_2024_EGY — must
 * not be silently dropped through.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @blocked NVARCHAR(MAX) = NULL;

SELECT @blocked = COALESCE(@blocked + N', ', N'') + l.schema_name + N'.' + l.table_name
FROM @leftovers l
WHERE OBJECT_ID(l.schema_name + '.' + l.table_name, 'U') IS NOT NULL
  AND (EXISTS (SELECT 1 FROM sys.sql_expression_dependencies d
               WHERE d.referenced_entity_name = l.table_name
                 AND d.referenced_schema_name = l.schema_name)
    OR EXISTS (SELECT 1 FROM sys.foreign_keys fk
               WHERE fk.referenced_object_id = OBJECT_ID(l.schema_name + '.' + l.table_name, 'U')));

IF @blocked IS NOT NULL
BEGIN
    PRINT '';
    RAISERROR(N'Refusing to drop: something still references %s. Investigate before re-running.',
              16, 1, @blocked);
    RETURN;
END

/* ── 3. Drop, if confirmed ────────────────────────────────────────────────── */
IF @Confirm = 0
BEGIN
    PRINT '';
    PRINT '@Confirm is 0 — reported only, nothing dropped.';
    PRINT 'Back up the database, then set @Confirm = 1 at the top of this script and re-run.';
    RETURN;
END

PRINT '';
PRINT '=== dropping ===';

DECLARE @schema SYSNAME, @table SYSNAME, @sql NVARCHAR(MAX);
DECLARE leftover_cur CURSOR LOCAL FAST_FORWARD FOR
    SELECT schema_name, table_name FROM @leftovers;
OPEN leftover_cur;
FETCH NEXT FROM leftover_cur INTO @schema, @table;
WHILE @@FETCH_STATUS = 0
BEGIN
    -- Schema-qualified and object-id guarded. See the header on MOHECORE.ROLES:
    -- an unqualified drop of that name would hit UMSCORE.Roles.
    IF OBJECT_ID(@schema + '.' + @table, 'U') IS NOT NULL
    BEGIN
        SET @sql = N'DROP TABLE ' + QUOTENAME(@schema) + N'.' + QUOTENAME(@table) + N';';
        EXEC sp_executesql @sql;
        PRINT '  dropped ' + @schema + '.' + @table;
    END
    ELSE
        PRINT '  skipped ' + @schema + '.' + @table + ' (already gone)';

    FETCH NEXT FROM leftover_cur INTO @schema, @table;
END
CLOSE leftover_cur;
DEALLOCATE leftover_cur;
GO

/* ── 4. Confirm the live tables are untouched ─────────────────────────────────
 * Named explicitly because the whole risk of this script is dropping one of
 * these by mistake.
 * ──────────────────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== the real tables, which must all still be here ===';
SELECT 'UMSCORE.Roles'        AS table_name, COUNT(*) AS row_count FROM UMSCORE.Roles
UNION ALL SELECT 'UMSCORE.Users',            COUNT(*) FROM UMSCORE.Users
UNION ALL SELECT 'MOHECORE.STUDENT_DATA',    COUNT(*) FROM MOHECORE.STUDENT_DATA
UNION ALL SELECT 'MOHECORE.STUDENT_DATA_ARCH', COUNT(*) FROM MOHECORE.STUDENT_DATA_ARCH;
GO
