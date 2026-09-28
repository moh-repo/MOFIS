/*
 * Repair orphaned lookup ids on MOHECORE.STUDENT_DATA.
 *
 * Symptom
 * -------
 *   Editing any single field on most students failed with
 *     "The UPDATE statement conflicted with the FOREIGN KEY constraint
 *      FK_STUDENT_DATA_GOV ... column 'STUDENT_GOV_ID'"
 *   even when the edit never touched that column.
 *
 * Cause
 * -----
 *   Nine foreign keys on STUDENT_DATA are marked untrusted (is_not_trusted = 1),
 *   i.e. they were created or re-enabled WITH NOCHECK over rows that already
 *   violated them. The offending rows use sentinel ids for "not specified" —
 *   0 (gender, degree, level, passport country, study major, ministry type,
 *   university type), 99 (governorate), 999 (district) — and no lookup table
 *   has a row with those ids. SQL Server tolerates such rows at rest but
 *   re-validates every foreign key an UPDATE touches, so any write to the row
 *   failed. Roughly 940 of 941 students were affected.
 *
 * Fix
 * ---
 *   These columns are all nullable, and NULL is what "not specified" should
 *   have been. Each orphaned value is set to NULL — matched by absence of a
 *   parent row rather than by listing sentinel numbers, so genuinely stray
 *   values (two STUDENT_MUD_ID rows pointing at deleted districts) are cleaned
 *   up too and no valid id is ever touched. The constraints are then
 *   re-validated so they become trusted and this cannot silently recur.
 *
 *   The application side is fixed separately: StudentDatum is now
 *   @DynamicUpdate, so an edit writes only the columns it changed.
 *
 * Idempotent — re-running finds nothing left to null.
 * Read-only preview: set @WhatIf = 1 to report without changing anything.
 */

/* STUDENT_DATA carries index/view definitions that require these to be ON for
   any UPDATE. SSMS sets them by default; sqlcmd does not, so state them here
   and the script behaves identically however it is run. */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

DECLARE @WhatIf BIT = 0;   -- set to 1 to preview only

DECLARE @fk SYSNAME, @childCol SYSNAME, @parentSchema SYSNAME, @parentTable SYSNAME, @parentCol SYSNAME;
DECLARE @sql NVARCHAR(MAX), @affected INT, @total INT = 0;

DECLARE fk_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT fk.name, pc.name, SCHEMA_NAME(rt.schema_id), rt.name, rc.name
    FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
    JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id     AND pc.column_id = fkc.parent_column_id
    JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
    JOIN sys.tables  rt ON rt.object_id = fk.referenced_object_id
    WHERE fk.parent_object_id = OBJECT_ID('MOHECORE.STUDENT_DATA')
      AND pc.is_nullable = 1                       -- only columns that may hold NULL
      AND fk.parent_object_id = fkc.parent_object_id;

OPEN fk_cursor;
FETCH NEXT FROM fk_cursor INTO @fk, @childCol, @parentSchema, @parentTable, @parentCol;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'UPDATE c SET c.' + QUOTENAME(@childCol) + N' = NULL
                 FROM MOHECORE.STUDENT_DATA c
                 WHERE c.' + QUOTENAME(@childCol) + N' IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM ' + QUOTENAME(@parentSchema) + N'.' + QUOTENAME(@parentTable)
                 + N' r WHERE r.' + QUOTENAME(@parentCol) + N' = c.' + QUOTENAME(@childCol) + N');';

    IF @WhatIf = 1
    BEGIN
        SET @sql = N'SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA c
                     WHERE c.' + QUOTENAME(@childCol) + N' IS NOT NULL
                       AND NOT EXISTS (SELECT 1 FROM ' + QUOTENAME(@parentSchema) + N'.' + QUOTENAME(@parentTable)
                     + N' r WHERE r.' + QUOTENAME(@parentCol) + N' = c.' + QUOTENAME(@childCol) + N');';
        PRINT 'WOULD CLEAR ' + @fk + ' -> ' + @childCol;
        EXEC sp_executesql @sql;
    END
    ELSE
    BEGIN
        EXEC sp_executesql @sql;
        SET @affected = @@ROWCOUNT;
        SET @total = @total + @affected;
        IF @affected > 0
            PRINT 'cleared ' + CAST(@affected AS NVARCHAR(10)) + ' orphaned value(s): ' + @fk + ' -> ' + @childCol;
    END

    FETCH NEXT FROM fk_cursor INTO @fk, @childCol, @parentSchema, @parentTable, @parentCol;
END

CLOSE fk_cursor;
DEALLOCATE fk_cursor;

IF @WhatIf = 0
BEGIN
    PRINT '';
    PRINT 'total values cleared: ' + CAST(@total AS NVARCHAR(10));

    /* Re-validate so the constraints become trusted. This fails loudly if any
       violating row remains, which is the desired outcome: it means a column
       that is NOT NULL still holds an orphan and needs a decision. */
    DECLARE @name SYSNAME;
    DECLARE check_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT name FROM sys.foreign_keys
        WHERE parent_object_id = OBJECT_ID('MOHECORE.STUDENT_DATA') AND is_not_trusted = 1;
    OPEN check_cursor;
    FETCH NEXT FROM check_cursor INTO @name;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @sql = N'ALTER TABLE MOHECORE.STUDENT_DATA WITH CHECK CHECK CONSTRAINT ' + QUOTENAME(@name) + N';';
        EXEC sp_executesql @sql;
        PRINT 'revalidated ' + @name;
        FETCH NEXT FROM check_cursor INTO @name;
    END
    CLOSE check_cursor;
    DEALLOCATE check_cursor;
END

/* Anything still listed here needs manual attention. */
PRINT '';
PRINT 'still untrusted after this script:';
SELECT name AS untrusted_constraint
FROM sys.foreign_keys
WHERE parent_object_id = OBJECT_ID('MOHECORE.STUDENT_DATA') AND is_not_trusted = 1;
