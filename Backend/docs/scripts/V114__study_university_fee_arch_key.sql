/*
    V114 — The fee log (سجل الرسوم) gets a key the application can generate.

    Review round 2, item F8 (19 Sep 2026). STUDY_UNIVERSITY_FEE_ARCH's primary key
    is STUDY_UNIVERSITY_FEE_ID, a plain INT with DEFAULT 0. The entity mapped
    STUDENT_ID as its @Id instead, so an edit or delete addressed "the row of
    student N" — wrong or non-unique once a student has two rows — and a new row
    was inserted with key 0, which the second insert collides with. Nothing but
    that screen writes the table, so nothing ever supplied a real key.

    What
    ----
    1. MOHECORE.SEQ_STUDY_UNIVERSITY_FEE_ARCH, starting after the highest key in
       the table (read with TenantAll so every attaché's rows count).
    2. The column's DEFAULT 0 becomes NEXT VALUE FOR that sequence, so an insert
       that names no key (a script, a report tool) gets one too.

    The application (build of 19 Sep 2026 or later) maps STUDY_UNIVERSITY_FEE_ID
    as the @Id and draws it from the sequence. Deploy the two together: an older
    build keeps inserting key 0 explicitly, which the default does not prevent.

    mohe_app reaches the sequence through its schema-level UPDATE grant (V090).

    Rollback
    --------
        ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE_ARCH DROP CONSTRAINT DF_STUDY_UNIVERSITY_FEE_ARCH_ID;
        ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE_ARCH ADD DEFAULT (0) FOR STUDY_UNIVERSITY_FEE_ID;
        DROP SEQUENCE MOHECORE.SEQ_STUDY_UNIVERSITY_FEE_ARCH;

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

/* ---- 1. The sequence ---------------------------------------------------------- */
IF OBJECT_ID(N'MOHECORE.SEQ_STUDY_UNIVERSITY_FEE_ARCH', N'SO') IS NULL
BEGIN
    DECLARE @start INT = (SELECT ISNULL(MAX(STUDY_UNIVERSITY_FEE_ID), 0) + 1 FROM MOHECORE.STUDY_UNIVERSITY_FEE_ARCH);
    DECLARE @sql NVARCHAR(400) = N'CREATE SEQUENCE MOHECORE.SEQ_STUDY_UNIVERSITY_FEE_ARCH AS INT START WITH '
        + CAST(@start AS NVARCHAR(12)) + N' INCREMENT BY 1 NO CYCLE;';
    EXEC (@sql);
    PRINT CONCAT('V114: sequence created, starting at ', @start);
END
ELSE
    PRINT 'V114: sequence already exists';
GO

/* ---- 2. The column default ------------------------------------------------------- */
DECLARE @old SYSNAME = (
    SELECT dc.name
      FROM sys.default_constraints dc
      JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
     WHERE dc.parent_object_id = OBJECT_ID(N'MOHECORE.STUDY_UNIVERSITY_FEE_ARCH')
       AND c.name = N'STUDY_UNIVERSITY_FEE_ID');

IF @old IS NOT NULL AND @old <> N'DF_STUDY_UNIVERSITY_FEE_ARCH_ID'
BEGIN
    DECLARE @drop NVARCHAR(400) = N'ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE_ARCH DROP CONSTRAINT ' + QUOTENAME(@old) + N';';
    EXEC (@drop);
    PRINT CONCAT('V114: dropped default ', @old);
END

IF NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'DF_STUDY_UNIVERSITY_FEE_ARCH_ID'
                AND parent_object_id = OBJECT_ID(N'MOHECORE.STUDY_UNIVERSITY_FEE_ARCH'))
BEGIN
    ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE_ARCH
        ADD CONSTRAINT DF_STUDY_UNIVERSITY_FEE_ARCH_ID
        DEFAULT (NEXT VALUE FOR MOHECORE.SEQ_STUDY_UNIVERSITY_FEE_ARCH) FOR STUDY_UNIVERSITY_FEE_ID;
    PRINT 'V114: default is now the sequence';
END
GO

/* ---- Verify --------------------------------------------------------------------- */
SELECT s.name AS sequence_name, s.current_value, dc.definition AS column_default
  FROM sys.sequences s
 CROSS JOIN sys.default_constraints dc
 WHERE s.object_id = OBJECT_ID(N'MOHECORE.SEQ_STUDY_UNIVERSITY_FEE_ARCH')
   AND dc.name = N'DF_STUDY_UNIVERSITY_FEE_ARCH_ID';
