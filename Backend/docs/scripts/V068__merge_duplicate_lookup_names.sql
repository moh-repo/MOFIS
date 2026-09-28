/*
    V068 — merge the lookup rows that share a name, then stop it recurring.

    !!  THIS SCRIPT ONLY REPORTS UNTIL YOU SET @Confirm = 1 BELOW.  !!

    What is duplicated (dev database, 27 Aug 2026)
    ----------------------------------------------
      MOHECORE.STUDENT_MAJOR      ذكاء صناعي        345, 348
                                  طب شرعي           363, 364
                                  نساء وولادة       357, 362
                                  هندسة ميكانيكية   350, 361
      MOHECORE.STUDY_COLLEGE_IN   كلية الطب         2, 1002

    The duplicates are found at run time by name, not listed by hand, so another
    environment with a different set gets its own report. The winner is the
    lowest id, as V048 kept Egypt's original id.

    One caution the report makes visible: STUDY_COLLEGE_IN carries the
    university the college belongs to, and the two كلية الطب rows sit under
    different universities (2 → جامعة عين شمس, 1002 → جامعة القاهرة). Two
    faculties of medicine at two universities are not the same college, so a
    college pair whose STUDY_UNIVERSITY_IN_ID differ is reported and NOT merged
    even with @Confirm = 1, and the college uniqueness is on
    (STUDY_UNIVERSITY_IN_ID, STUDY_COLLEGE_IN_NAME) rather than the name alone —
    the invariant a college lookup can actually keep. Majors have no such
    parent and are made unique by name.

    What the report counts
    ----------------------
    For every loser id, the rows in every base-table column that references
    the lookup by id — taken from the catalogue, so it covers the declared
    foreign keys (STUDENT_DATA, STUDENT_DATA_STAGING) and the undeclared
    references (STUDENT_DATA_ARCH, STUDY_YEARS_LOOKUP.STUDENT_MAJOR_ID,
    STUDY_EDUCTION_IN.STUDY_COLLEGE_IN_ID) alike — plus ETL_LOOKUP_ALIAS rows
    whose LOOKUP is 'MAJOR' and whose RESOLVED_ID is the loser (the ETL
    resolver's key for STUDENT_MAJOR; it has no college key).
    STUDENT_SCHOLARSHIP_DATA_IMPORT keeps the sheet's free text, never an id,
    so it needs nothing.

    With @Confirm = 1, in one transaction
    ------------------------------------
      1. every referencing column: loser → winner (STUDENT_DATA rows included —
         the only script allowed to update STUDENT_DATA's lookup values; its
         structure is untouched; the audit triggers record the change);
      2. ETL_LOOKUP_ALIAS.RESOLVED_ID: loser → winner;
      3. DELETE the losers;
      4. unique indexes, named after V007's UQ_<TABLE>_<COLUMN> convention,
         filtered on the name being NOT NULL because both name columns are
         nullable:
             UQ_STUDENT_MAJOR_NAME              (STUDENT_MAJOR_NAME)
             UQ_STUDY_COLLEGE_IN_UNIVERSITY_NAME (STUDY_UNIVERSITY_IN_ID, STUDY_COLLEGE_IN_NAME)
         Each only if no duplicate remains; otherwise it is reported and skipped.

    Session context: STUDENT_DATA and its staging/archive are under the tenant
    policy, so TenantAll is set first — with the policy ON and no context, the
    counts would be 0 and the UPDATEs would touch nothing while reporting success.

    Rollback: none for the merge (take a backup first); the indexes drop cleanly.
    Idempotent: a second run reports no duplicates and "already present" indexes.
    Needs QUOTED_IDENTIFIER ON for the filtered indexes (sqlcmd: -I).
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';
GO

/* ─────────────────────────────────────────────────────────────────────────────
 *  SET THIS TO 1 TO MERGE. Leave at 0 to report only.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @Confirm BIT = 1;   /* confirmed by the owner, 1 Sep 2026; snapshot taken first */

PRINT '';
PRINT '=== V068: duplicate lookup names ===';

/* ── The duplicate sets ───────────────────────────────────────────────────── */
IF OBJECT_ID('tempdb..#dups') IS NOT NULL DROP TABLE #dups;
CREATE TABLE #dups (
    lookup      SYSNAME       NOT NULL,   -- STUDENT_MAJOR / STUDY_COLLEGE_IN
    name_       NVARCHAR(255) NOT NULL,
    winner_id   INT           NOT NULL,
    loser_id    INT           NOT NULL,
    mergeable   BIT           NOT NULL,   -- 0: same name, different university
    note        NVARCHAR(200) NULL
);

INSERT INTO #dups (lookup, name_, winner_id, loser_id, mergeable, note)
SELECT 'STUDENT_MAJOR', m.STUDENT_MAJOR_NAME, w.winner_id, m.STUDENT_MAJOR_ID, 1, NULL
  FROM MOHECORE.STUDENT_MAJOR m
  JOIN (SELECT STUDENT_MAJOR_NAME, MIN(STUDENT_MAJOR_ID) AS winner_id
          FROM MOHECORE.STUDENT_MAJOR WHERE STUDENT_MAJOR_NAME IS NOT NULL
         GROUP BY STUDENT_MAJOR_NAME HAVING COUNT(*) > 1) w ON w.STUDENT_MAJOR_NAME = m.STUDENT_MAJOR_NAME
 WHERE m.STUDENT_MAJOR_ID <> w.winner_id;

INSERT INTO #dups (lookup, name_, winner_id, loser_id, mergeable, note)
SELECT 'STUDY_COLLEGE_IN', c.STUDY_COLLEGE_IN_NAME, w.winner_id, c.STUDY_COLLEGE_IN_ID,
       CASE WHEN ISNULL(c.STUDY_UNIVERSITY_IN_ID, 0) = ISNULL(wc.STUDY_UNIVERSITY_IN_ID, 0) THEN 1 ELSE 0 END,
       CASE WHEN ISNULL(c.STUDY_UNIVERSITY_IN_ID, 0) = ISNULL(wc.STUDY_UNIVERSITY_IN_ID, 0) THEN NULL
            ELSE N'different universities: winner ' + CAST(wc.STUDY_UNIVERSITY_IN_ID AS NVARCHAR(10)) + N' (' + ISNULL(wu.STUDY_UNIVERSITY_IN_NAME, N'?')
               + N'), loser ' + CAST(c.STUDY_UNIVERSITY_IN_ID AS NVARCHAR(10)) + N' (' + ISNULL(lu.STUDY_UNIVERSITY_IN_NAME, N'?') + N') — NOT merged' END
  FROM MOHECORE.STUDY_COLLEGE_IN c
  JOIN (SELECT STUDY_COLLEGE_IN_NAME, MIN(STUDY_COLLEGE_IN_ID) AS winner_id
          FROM MOHECORE.STUDY_COLLEGE_IN WHERE STUDY_COLLEGE_IN_NAME IS NOT NULL
         GROUP BY STUDY_COLLEGE_IN_NAME HAVING COUNT(*) > 1) w ON w.STUDY_COLLEGE_IN_NAME = c.STUDY_COLLEGE_IN_NAME
  JOIN MOHECORE.STUDY_COLLEGE_IN wc ON wc.STUDY_COLLEGE_IN_ID = w.winner_id
  LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN wu ON wu.STUDY_UNIVERSITY_IN_ID = wc.STUDY_UNIVERSITY_IN_ID
  LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN lu ON lu.STUDY_UNIVERSITY_IN_ID = c.STUDY_UNIVERSITY_IN_ID
 WHERE c.STUDY_COLLEGE_IN_ID <> w.winner_id;

/* ── The columns that reference each lookup by id, from the catalogue ─────── */
IF OBJECT_ID('tempdb..#refs') IS NOT NULL DROP TABLE #refs;
CREATE TABLE #refs (lookup SYSNAME NOT NULL, sch SYSNAME NOT NULL, tbl SYSNAME NOT NULL, col SYSNAME NOT NULL);
INSERT INTO #refs (lookup, sch, tbl, col)
SELECT CASE WHEN c.COLUMN_NAME = 'STUDENT_MAJOR_ID' THEN 'STUDENT_MAJOR' ELSE 'STUDY_COLLEGE_IN' END, c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME
  FROM INFORMATION_SCHEMA.COLUMNS c
  JOIN INFORMATION_SCHEMA.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
 WHERE c.DATA_TYPE IN ('int', 'bigint')
   AND c.COLUMN_NAME IN ('STUDENT_MAJOR_ID', 'STUDY_COLLEGE_IN', 'STUDY_COLLEGE_IN_ID')
   AND NOT (c.TABLE_NAME = 'STUDENT_MAJOR'    AND c.COLUMN_NAME = 'STUDENT_MAJOR_ID')
   AND NOT (c.TABLE_NAME = 'STUDY_COLLEGE_IN' AND c.COLUMN_NAME = 'STUDY_COLLEGE_IN_ID');

/* ── Report ───────────────────────────────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM #dups)
    PRINT 'V068: no duplicate names in STUDENT_MAJOR or STUDY_COLLEGE_IN (already clean)';
ELSE
BEGIN
    PRINT '--- duplicate names (winner = lowest id) ---';
    SELECT lookup, name_, winner_id, loser_id, mergeable, note FROM #dups ORDER BY lookup, name_, loser_id;

    IF OBJECT_ID('tempdb..#counts') IS NOT NULL DROP TABLE #counts;
    CREATE TABLE #counts (lookup SYSNAME, name_ NVARCHAR(255), loser_id INT, referencing NVARCHAR(400), rows_ INT);

    DECLARE @sql NVARCHAR(MAX) = N'';
    SELECT @sql = @sql + N'
INSERT INTO #counts SELECT ''' + d.lookup + N''', N''' + REPLACE(d.name_, N'''', N'''''') + N''', ' + CAST(d.loser_id AS NVARCHAR(10))
        + N', ''' + r.sch + N'.' + r.tbl + N'.' + r.col + N''', COUNT(*) FROM ' + QUOTENAME(r.sch) + N'.' + QUOTENAME(r.tbl)
        + N' WHERE ' + QUOTENAME(r.col) + N' = ' + CAST(d.loser_id AS NVARCHAR(10)) + N';'
      FROM #dups d JOIN #refs r ON r.lookup = d.lookup;
    IF OBJECT_ID('MOHECORE.ETL_LOOKUP_ALIAS', 'U') IS NOT NULL
        SELECT @sql = @sql + N'
INSERT INTO #counts SELECT ''STUDENT_MAJOR'', N''' + REPLACE(d.name_, N'''', N'''''') + N''', ' + CAST(d.loser_id AS NVARCHAR(10))
            + N', ''MOHECORE.ETL_LOOKUP_ALIAS (LOOKUP = MAJOR)'', COUNT(*) FROM MOHECORE.ETL_LOOKUP_ALIAS WHERE LOOKUP = N''MAJOR'' AND RESOLVED_ID = '
            + CAST(d.loser_id AS NVARCHAR(10)) + N';'
          FROM #dups d WHERE d.lookup = 'STUDENT_MAJOR';
    EXEC sp_executesql @sql;

    PRINT '--- rows referencing each loser, by table.column ---';
    SELECT lookup, name_, loser_id, referencing, rows_ FROM #counts ORDER BY lookup, name_, loser_id, referencing;
    SELECT lookup, name_, loser_id, SUM(rows_) AS total_referencing_rows FROM #counts GROUP BY lookup, name_, loser_id ORDER BY 1, 2, 3;
END

/* ── Merge ────────────────────────────────────────────────────────────────── */
IF @Confirm = 0
    PRINT 'V068: report only. Set @Confirm = 1 to repoint the references, delete the losers and add the unique indexes.';
ELSE
BEGIN
    BEGIN TRANSACTION;

    DECLARE @lookup SYSNAME, @winner INT, @loser INT, @name NVARCHAR(255), @sch SYSNAME, @tbl SYSNAME, @col SYSNAME, @n INT, @stmt NVARCHAR(MAX);
    DECLARE m CURSOR LOCAL FAST_FORWARD FOR SELECT lookup, name_, winner_id, loser_id FROM #dups WHERE mergeable = 1 ORDER BY lookup, name_, loser_id;
    OPEN m; FETCH NEXT FROM m INTO @lookup, @name, @winner, @loser;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        DECLARE r CURSOR LOCAL FAST_FORWARD FOR SELECT sch, tbl, col FROM #refs WHERE lookup = @lookup;
        OPEN r; FETCH NEXT FROM r INTO @sch, @tbl, @col;
        WHILE @@FETCH_STATUS = 0
        BEGIN
            SET @stmt = N'UPDATE ' + QUOTENAME(@sch) + N'.' + QUOTENAME(@tbl) + N' SET ' + QUOTENAME(@col) + N' = @w WHERE ' + QUOTENAME(@col) + N' = @l;';
            EXEC sp_executesql @stmt, N'@w INT, @l INT', @w = @winner, @l = @loser;
            SET @n = @@ROWCOUNT;
            IF @n > 0 PRINT 'V068: ' + @sch + '.' + @tbl + '.' + @col + ': ' + CAST(@n AS VARCHAR(10)) + ' row(s) ' + CAST(@loser AS VARCHAR(10)) + ' -> ' + CAST(@winner AS VARCHAR(10));
            FETCH NEXT FROM r INTO @sch, @tbl, @col;
        END
        CLOSE r; DEALLOCATE r;

        IF @lookup = 'STUDENT_MAJOR' AND OBJECT_ID('MOHECORE.ETL_LOOKUP_ALIAS', 'U') IS NOT NULL
        BEGIN
            UPDATE MOHECORE.ETL_LOOKUP_ALIAS SET RESOLVED_ID = @winner WHERE LOOKUP = N'MAJOR' AND RESOLVED_ID = @loser;
            SET @n = @@ROWCOUNT;
            IF @n > 0 PRINT 'V068: ETL_LOOKUP_ALIAS (MAJOR): ' + CAST(@n AS VARCHAR(10)) + ' alias row(s) ' + CAST(@loser AS VARCHAR(10)) + ' -> ' + CAST(@winner AS VARCHAR(10));
        END

        IF @lookup = 'STUDENT_MAJOR'
            DELETE FROM MOHECORE.STUDENT_MAJOR WHERE STUDENT_MAJOR_ID = @loser;
        ELSE
            DELETE FROM MOHECORE.STUDY_COLLEGE_IN WHERE STUDY_COLLEGE_IN_ID = @loser;
        PRINT 'V068: ' + @lookup + ' ' + CAST(@loser AS VARCHAR(10)) + ' (' + @name + ') deleted; ' + CAST(@winner AS VARCHAR(10)) + ' kept';

        FETCH NEXT FROM m INTO @lookup, @name, @winner, @loser;
    END
    CLOSE m; DEALLOCATE m;

    IF EXISTS (SELECT 1 FROM #dups WHERE mergeable = 0)
    BEGIN
        PRINT 'V068: *** left alone (same name under different universities — decide by hand):';
        SELECT lookup, name_, winner_id, loser_id, note FROM #dups WHERE mergeable = 0;
    END

    /* Unique indexes, only when the data supports them. */
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_MAJOR') AND name = 'UQ_STUDENT_MAJOR_NAME')
        PRINT 'V068: UQ_STUDENT_MAJOR_NAME already present';
    ELSE IF EXISTS (SELECT STUDENT_MAJOR_NAME FROM MOHECORE.STUDENT_MAJOR WHERE STUDENT_MAJOR_NAME IS NOT NULL GROUP BY STUDENT_MAJOR_NAME HAVING COUNT(*) > 1)
        PRINT 'V068: *** UQ_STUDENT_MAJOR_NAME skipped — duplicate major names remain';
    ELSE
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX UQ_STUDENT_MAJOR_NAME ON MOHECORE.STUDENT_MAJOR (STUDENT_MAJOR_NAME) WHERE STUDENT_MAJOR_NAME IS NOT NULL;
        PRINT 'V068: created UQ_STUDENT_MAJOR_NAME';
    END

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('MOHECORE.STUDY_COLLEGE_IN') AND name = 'UQ_STUDY_COLLEGE_IN_UNIVERSITY_NAME')
        PRINT 'V068: UQ_STUDY_COLLEGE_IN_UNIVERSITY_NAME already present';
    ELSE IF EXISTS (SELECT STUDY_UNIVERSITY_IN_ID, STUDY_COLLEGE_IN_NAME FROM MOHECORE.STUDY_COLLEGE_IN WHERE STUDY_COLLEGE_IN_NAME IS NOT NULL
                     GROUP BY STUDY_UNIVERSITY_IN_ID, STUDY_COLLEGE_IN_NAME HAVING COUNT(*) > 1)
        PRINT 'V068: *** UQ_STUDY_COLLEGE_IN_UNIVERSITY_NAME skipped — duplicate college names remain within one university';
    ELSE
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX UQ_STUDY_COLLEGE_IN_UNIVERSITY_NAME ON MOHECORE.STUDY_COLLEGE_IN (STUDY_UNIVERSITY_IN_ID, STUDY_COLLEGE_IN_NAME) WHERE STUDY_COLLEGE_IN_NAME IS NOT NULL;
        PRINT 'V068: created UQ_STUDY_COLLEGE_IN_UNIVERSITY_NAME';
    END

    COMMIT TRANSACTION;
    PRINT 'V068: committed';
END

/* ── Result ───────────────────────────────────────────────────────────────── */
DECLARE @majorDups INT = (SELECT COUNT(*) FROM (SELECT STUDENT_MAJOR_NAME FROM MOHECORE.STUDENT_MAJOR WHERE STUDENT_MAJOR_NAME IS NOT NULL GROUP BY STUDENT_MAJOR_NAME HAVING COUNT(*) > 1) x);
DECLARE @collegeDups INT = (SELECT COUNT(*) FROM (SELECT STUDY_COLLEGE_IN_NAME FROM MOHECORE.STUDY_COLLEGE_IN WHERE STUDY_COLLEGE_IN_NAME IS NOT NULL GROUP BY STUDY_COLLEGE_IN_NAME HAVING COUNT(*) > 1) x);
PRINT '';
PRINT '--- V068 result ---';
PRINT 'Major names still duplicated:   ' + CAST(@majorDups AS VARCHAR(10));
PRINT 'College names still duplicated: ' + CAST(@collegeDups AS VARCHAR(10)) + ' (a same-name pair under two universities counts here and is legitimate)';
DROP TABLE #refs; DROP TABLE #dups;
IF OBJECT_ID('tempdb..#counts') IS NOT NULL DROP TABLE #counts;
GO
