/*
    V128 — University fees (رسوم الجامعة) get a key the application can generate
    (plan W4-10: found while adding the fee-search tests).

    STUDY_UNIVERSITY_FEE's primary key STUDY_UNIVERSITY_FEE_ID is a plain INT with
    DEFAULT 0, but the entity mapped it as an IDENTITY column. Hibernate inserted
    without a key and asked the database for the generated one, so every new fee
    failed with "null identifier" — including an approved "add fee" request, whose
    execution calls the same save. IdentityMappingAgreementTest had it on its list
    of known mismatches. The fee log (STUDY_UNIVERSITY_FEE_ARCH) had the same
    defect and was fixed the same way by V114.

    What
    ----
    1. MOHECORE.SEQ_STUDY_UNIVERSITY_FEE, starting after the highest key in the
       table (read with TenantAll so every attaché's rows count).
    2. The column's DEFAULT 0 becomes NEXT VALUE FOR that sequence, so an insert
       that names no key (a script, a report tool) gets one too.

    The application (build of 22 Sep 2026 or later) draws the key from the
    sequence. mohe_app reaches the sequence through its schema-level UPDATE grant
    (V090).

    Rollback
    --------
        ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE DROP CONSTRAINT DF_STUDY_UNIVERSITY_FEE_ID;
        ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE ADD DEFAULT (0) FOR STUDY_UNIVERSITY_FEE_ID;
        DROP SEQUENCE MOHECORE.SEQ_STUDY_UNIVERSITY_FEE;

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

/* ---- 1. The sequence ---------------------------------------------------------- */
IF OBJECT_ID(N'MOHECORE.SEQ_STUDY_UNIVERSITY_FEE', N'SO') IS NULL
BEGIN
    DECLARE @start INT = (SELECT ISNULL(MAX(STUDY_UNIVERSITY_FEE_ID), 0) + 1 FROM MOHECORE.STUDY_UNIVERSITY_FEE);
    DECLARE @sql NVARCHAR(400) = N'CREATE SEQUENCE MOHECORE.SEQ_STUDY_UNIVERSITY_FEE AS INT START WITH '
        + CAST(@start AS NVARCHAR(12)) + N' INCREMENT BY 1 NO CYCLE;';
    EXEC (@sql);
    PRINT CONCAT('V128: sequence created, starting at ', @start);
END
ELSE
    PRINT 'V128: sequence already exists';
GO

/* ---- 2. The column default ------------------------------------------------------- */
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @old SYSNAME = (
    SELECT dc.name
      FROM sys.default_constraints dc
      JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
     WHERE dc.parent_object_id = OBJECT_ID(N'MOHECORE.STUDY_UNIVERSITY_FEE')
       AND c.name = N'STUDY_UNIVERSITY_FEE_ID');
IF @old IS NOT NULL AND @old <> N'DF_STUDY_UNIVERSITY_FEE_ID'
BEGIN
    DECLARE @drop NVARCHAR(400) = N'ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE DROP CONSTRAINT ' + QUOTENAME(@old) + N';';
    EXEC (@drop);
    PRINT CONCAT('V128: dropped default ', @old);
END

IF NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'DF_STUDY_UNIVERSITY_FEE_ID'
                AND parent_object_id = OBJECT_ID(N'MOHECORE.STUDY_UNIVERSITY_FEE'))
BEGIN
    ALTER TABLE MOHECORE.STUDY_UNIVERSITY_FEE
        ADD CONSTRAINT DF_STUDY_UNIVERSITY_FEE_ID
        DEFAULT (NEXT VALUE FOR MOHECORE.SEQ_STUDY_UNIVERSITY_FEE) FOR STUDY_UNIVERSITY_FEE_ID;
    PRINT 'V128: default is now the sequence';
END
GO

/* ---- Checks ------------------------------------------------------------------- */
IF OBJECT_ID(N'MOHECORE.SEQ_STUDY_UNIVERSITY_FEE', N'SO') IS NULL
    THROW 51280, 'V128: the sequence is missing', 1;
