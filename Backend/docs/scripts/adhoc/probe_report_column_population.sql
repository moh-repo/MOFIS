/*
    Report column population probe
    ==============================

    The static audits (scripts/audit_report_data.py, scripts/audit_dropdown_sources.py)
    prove that every report field is supplied by a bean and every dropdown is
    filled from the database. Neither can see data-shaped emptiness: a column
    that exists, is mapped, is printed -- and is NULL on every row.

    That is the defect class the earlier provenance pass kept finding, so this
    probe asks the database directly, for every report view:

      * how many rows it returns for a ministry-scoped session
      * which of its columns are NULL (or blank) on every one of those rows

    A column reported here prints as an empty cell in the PDF and as an empty
    dropdown on screen. That is either a ministry data-entry gap -- which must
    be reported as empty, never back-filled with a plausible value -- or a view
    selecting something nothing records. Section 4 of report-data-audit.md lists
    the gaps already known and accepted.

    Run:
        sqlcmd -S <server> -d MOHE -U <user> -P <pw> -C -I -i docs/scripts/adhoc/probe_report_column_population.sql

    -I matters: the tenanted tables carry triggers created under
    QUOTED_IDENTIFIER ON, and sqlcmd does not set it by default.
*/

SET NOCOUNT ON;

/*  Ministry-wide scope. tenant_country_policy filters every read against
    SESSION_CONTEXT, so without this the probe would report every view empty and
    call it a finding. This mirrors what TenantContextDataSource sets for a
    ministry user; it is read-only reporting, and the context dies with the
    session.  */
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1', @read_only = 0;

IF OBJECT_ID('tempdb..#col_stats') IS NOT NULL DROP TABLE #col_stats;
CREATE TABLE #col_stats (
    view_name    sysname,
    column_name  sysname,
    row_count    bigint,
    non_null     bigint
);

DECLARE @schema sysname, @view sysname, @sql nvarchar(max);

DECLARE view_cur CURSOR LOCAL FAST_FORWARD FOR
    SELECT s.name, v.name
    FROM sys.views v
    JOIN sys.schemas s ON s.schema_id = v.schema_id
    ORDER BY s.name, v.name;

OPEN view_cur;
FETCH NEXT FROM view_cur INTO @schema, @view;

WHILE @@FETCH_STATUS = 0
BEGIN
    /*  One pass per view: a single scan produces the row count and a non-null
        tally for every column. Text-ish columns count '' as unpopulated too --
        a blank name is as empty on the page as a NULL one.  */
    SELECT @sql = N'
        INSERT INTO #col_stats (view_name, column_name, row_count, non_null)
        SELECT ' + QUOTENAME(@schema + '.' + @view, '''') + N', v.column_name, x.n, v.cnt
        FROM (SELECT COUNT_BIG(*) AS n' +
        -- STRING_AGG needs SQL Server 2017; this server is 2016, so the column
        -- lists are stitched with FOR XML PATH.
        (
            SELECT CAST((
                SELECT N', SUM(CASE WHEN ' + QUOTENAME(c.name) +
                       CASE WHEN t.name IN ('char','nchar','varchar','nvarchar')
                            THEN N' IS NULL OR LTRIM(RTRIM(' + QUOTENAME(c.name) + N')) = '''''
                            ELSE N' IS NULL'
                       END +
                       N' THEN 0 ELSE 1 END) AS ' + QUOTENAME('c_' + CAST(c.column_id AS varchar(10)))
                FROM sys.columns c
                JOIN sys.types t ON t.user_type_id = c.user_type_id
                WHERE c.object_id = OBJECT_ID(QUOTENAME(@schema) + '.' + QUOTENAME(@view))
                  -- these cannot be aggregated and are never report or dropdown values
                  AND t.name NOT IN ('xml','geography','geometry','hierarchyid','image')
                ORDER BY c.column_id
                FOR XML PATH(''), TYPE
            ).value('.', 'nvarchar(max)') AS nvarchar(max))
        ) + N'
              FROM ' + QUOTENAME(@schema) + N'.' + QUOTENAME(@view) + N') x
        CROSS APPLY (VALUES ' +
        STUFF((
            SELECT CAST((
                SELECT N',(' + QUOTENAME(c.name, '''') + N', x.' +
                       QUOTENAME('c_' + CAST(c.column_id AS varchar(10))) + N')'
                FROM sys.columns c
                JOIN sys.types t ON t.user_type_id = c.user_type_id
                WHERE c.object_id = OBJECT_ID(QUOTENAME(@schema) + '.' + QUOTENAME(@view))
                  AND t.name NOT IN ('xml','geography','geometry','hierarchyid','image')
                ORDER BY c.column_id
                FOR XML PATH(''), TYPE
            ).value('.', 'nvarchar(max)') AS nvarchar(max))
        ), 1, 1, N'') + N') v(column_name, cnt);';

    BEGIN TRY
        EXEC sp_executesql @sql;
    END TRY
    BEGIN CATCH
        /*  A view that cannot be read at all is itself the finding -- record it
            rather than aborting the sweep.  */
        INSERT INTO #col_stats (view_name, column_name, row_count, non_null)
        VALUES (@schema + '.' + @view, '(view failed: ' + LEFT(ERROR_MESSAGE(), 120) + ')', -1, -1);
    END CATCH;

    FETCH NEXT FROM view_cur INTO @schema, @view;
END

CLOSE view_cur;
DEALLOCATE view_cur;

PRINT '=== 1. Rows per view ============================================';
SELECT view_name, MAX(row_count) AS rows_visible
FROM #col_stats
GROUP BY view_name
ORDER BY rows_visible, view_name;

PRINT '';
PRINT '=== 2. Columns empty on every row (non-empty views only) ========';
SELECT view_name, column_name, row_count
FROM #col_stats
WHERE row_count > 0 AND non_null = 0
ORDER BY view_name, column_name;

PRINT '';
PRINT '=== 3. Views that could not be read =============================';
SELECT view_name, column_name AS problem
FROM #col_stats
WHERE row_count = -1
ORDER BY view_name;
