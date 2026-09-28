/*
 * Restructure STUDENT_DATA_ARCH so it mirrors STUDENT_DATA and behaves as a proper
 * archive: one live student may own many archive rows.
 *
 * Before: STUDENT_ID was the table's own IDENTITY primary key and the real student was
 * kept in SOURCE_STUDENT_ID. That made STUDENT_ID mean something different from the live
 * STUDENT_DATA.STUDENT_ID and left no surrogate key of its own.
 *
 * After:
 *   - ID                    surrogate IDENTITY primary key (renamed from the old STUDENT_ID,
 *                           so its existing values — and any references to them — are kept).
 *   - STUDENT_ID            the REAL student id (repeatable), copied from SOURCE_STUDENT_ID.
 *   - PROFILE_SNAPSHOT      full-profile JSON captured at archive time (NULL for historical rows).
 *   - INSERTED_TO_ARCH_DATE when the row was written to the archive (seeded from ARCHIVE_DATE).
 *   - IS_DELETED / DELETED_DATE / RECORD_VISIBILITY / CREATED_ROLE_LEVEL  mirror STUDENT_DATA.
 *   - SOURCE_STUDENT_ID     dropped (its value now lives in STUDENT_ID).
 *
 * The IDENTITY column is renamed in place (sp_rename keeps the IDENTITY property), so no
 * table rebuild and no IDENTITY_INSERT are needed. Idempotent — safe to re-run.
 */

SET NOCOUNT ON;
GO

/* 1. Rename the old IDENTITY PK column STUDENT_ID -> ID (keeps values + IDENTITY property). */
IF  COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'ID') IS NULL
AND COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'STUDENT_ID') IS NOT NULL
BEGIN
    EXEC sp_rename 'MOHECORE.STUDENT_DATA_ARCH.STUDENT_ID', 'ID', 'COLUMN';
END
GO

/* 2. Add the new STUDENT_ID column (real student id, repeatable, non-identity). */
IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'STUDENT_ID') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_ARCH ADD STUDENT_ID INT NULL;
END
GO

/* 3. Backfill STUDENT_ID from SOURCE_STUDENT_ID for existing rows (only if that column exists).
 *    Dynamic SQL: SQL Server binds column names at compile time, so a direct reference to
 *    SOURCE_STUDENT_ID would fail the whole batch when the column is absent, even under the
 *    IF guard. EXEC defers name resolution to run time, so the guard actually protects it. */
IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'SOURCE_STUDENT_ID') IS NOT NULL
   AND COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'STUDENT_ID') IS NOT NULL
BEGIN
    EXEC('UPDATE MOHECORE.STUDENT_DATA_ARCH SET STUDENT_ID = SOURCE_STUDENT_ID WHERE STUDENT_ID IS NULL');
END
GO

/* 4. Full-profile JSON snapshot column. */
IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'PROFILE_SNAPSHOT') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_ARCH ADD PROFILE_SNAPSHOT NVARCHAR(MAX) NULL;
END
GO

/* 5. Archive-insert timestamp (default now); seed existing rows from ARCHIVE_DATE. */
IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'INSERTED_TO_ARCH_DATE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_ARCH
        ADD INSERTED_TO_ARCH_DATE DATETIME2 NOT NULL
        CONSTRAINT DF_STUDENT_DATA_ARCH_INSERTED_TO_ARCH_DATE DEFAULT (SYSDATETIME());
END
GO

IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'ARCHIVE_DATE') IS NOT NULL
BEGIN
    UPDATE MOHECORE.STUDENT_DATA_ARCH
       SET INSERTED_TO_ARCH_DATE = ARCHIVE_DATE
     WHERE ARCHIVE_DATE IS NOT NULL;
END
GO

/* 6. Columns mirrored from STUDENT_DATA. */
IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'IS_DELETED') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_ARCH
        ADD IS_DELETED BIT NOT NULL
        CONSTRAINT DF_STUDENT_DATA_ARCH_IS_DELETED DEFAULT (0);
END
GO

IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'DELETED_DATE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_ARCH ADD DELETED_DATE DATETIME2 NULL;
END
GO

IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'RECORD_VISIBILITY') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_ARCH
        ADD RECORD_VISIBILITY NVARCHAR(50) NOT NULL
        CONSTRAINT DF_STUDENT_DATA_ARCH_RECORD_VISIBILITY DEFAULT ('ALL');
END
GO

IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'CREATED_ROLE_LEVEL') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_ARCH ADD CREATED_ROLE_LEVEL NVARCHAR(255) NULL;
END
GO

/* 7. Drop the now-redundant SOURCE_STUDENT_ID (value moved into STUDENT_ID). */
IF COL_LENGTH('MOHECORE.STUDENT_DATA_ARCH', 'SOURCE_STUDENT_ID') IS NOT NULL
BEGIN
    DECLARE @df SYSNAME;
    SELECT @df = dc.name
      FROM sys.default_constraints dc
      JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
     WHERE dc.parent_object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_ARCH')
       AND c.name = 'SOURCE_STUDENT_ID';
    IF @df IS NOT NULL
        EXEC('ALTER TABLE MOHECORE.STUDENT_DATA_ARCH DROP CONSTRAINT ' + @df);

    EXEC('ALTER TABLE MOHECORE.STUDENT_DATA_ARCH DROP COLUMN SOURCE_STUDENT_ID');
END
GO

/* 8. Index to speed up per-student history lookups on the new STUDENT_ID column. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_STUDENT_DATA_ARCH_STUDENT_ID'
              AND object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_ARCH'))
BEGIN
    CREATE INDEX IX_STUDENT_DATA_ARCH_STUDENT_ID
        ON MOHECORE.STUDENT_DATA_ARCH (STUDENT_ID);
END
GO
