/*
    V061 — drop the three hand-made backup tables of 24 August 2026.

    !!  THIS SCRIPT DOES NOTHING UNTIL YOU SET @Confirm = 1 BELOW.  !!

    Same shape as V035, for the same reason: these tables hold copies of
    student rows, and dropping them is a decision the operator makes after
    reading the report, not something a migration does on its own.

    What they are
    -------------
      MOHECORE.BK_STUDENT_DOCUMENTS_20260824   copy of STUDENT_DOCUMENTS
      MOHECORE.BK_STUDENT_DOC_HIST_20260824    copy of STUDENT_DOCUMENT_HISTORY
      MOHECORE.BK_STUDENT_PICPATH_20260824     STUDENT_ID + picture path (941 rows)

    They were taken by hand as a safety net while the plaintext student
    documents were re-encrypted (system-review addendum of 22 August 2026:
    "half the student documents were in plaintext"). No script created them;
    nothing reads them; they are heaps with no primary key, and they hold file
    paths and document metadata for every country with no tenant column — the
    same class of leftover V035 dealt with.

    Drop them once the re-encryption has been confirmed good on this
    environment (every STUDENT_DOCUMENTS row IS_ENCRYPTED = 1 and every file
    opens). Until then they are the only way back.

    Idempotent either way. Reports first; drops only on @Confirm = 1.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 *  SET THIS TO 1 TO ACTUALLY DROP. Leave at 0 to report only.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @Confirm BIT = 0;

DECLARE @backups TABLE (table_name SYSNAME);
INSERT INTO @backups VALUES
    ('BK_STUDENT_DOCUMENTS_20260824'),
    ('BK_STUDENT_DOC_HIST_20260824'),
    ('BK_STUDENT_PICPATH_20260824');

/* Report */
SELECT b.table_name,
       CASE WHEN OBJECT_ID('MOHECORE.' + b.table_name, 'U') IS NULL THEN 'absent' ELSE 'present' END AS state,
       (SELECT SUM(p.rows) FROM sys.partitions p
         WHERE p.object_id = OBJECT_ID('MOHECORE.' + b.table_name) AND p.index_id IN (0, 1)) AS rows_
  FROM @backups b;

SELECT 'STUDENT_DOCUMENTS not yet encrypted' AS check_, COUNT(*) AS rows_
  FROM MOHECORE.STUDENT_DOCUMENTS WHERE IS_ENCRYPTED = 0 OR IS_ENCRYPTED IS NULL;

IF @Confirm = 0
BEGIN
    PRINT 'V061: report only. Set @Confirm = 1 to drop the three backup tables.';
END
ELSE
BEGIN
    DECLARE @name SYSNAME, @sql NVARCHAR(MAX);
    DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT table_name FROM @backups;
    OPEN c;
    FETCH NEXT FROM c INTO @name;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF OBJECT_ID('MOHECORE.' + @name, 'U') IS NOT NULL
        BEGIN
            SET @sql = N'DROP TABLE MOHECORE.' + QUOTENAME(@name) + N';';
            EXEC sp_executesql @sql;
            PRINT 'V061: dropped MOHECORE.' + @name;
        END
        ELSE PRINT 'V061: MOHECORE.' + @name + ' already gone';
        FETCH NEXT FROM c INTO @name;
    END
    CLOSE c; DEALLOCATE c;
END
GO
