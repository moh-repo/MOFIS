/*
================================================================================
V054 — STUDENT_DATA_STAGING: create the table the scholarship ETL loads into
================================================================================

Why
---
ScholarshipEtlService (V051–V053) reads the landing table
STUDENT_SCHOLARSHIP_DATA_IMPORT and writes resolved rows into
MOHECORE.STUDENT_DATA_STAGING. V051 creates the landing table and V053 alters
the staging table, but until this script nothing in source control *created*
it — it existed only on the machine where the feature was built. A fresh
environment could run every numbered script and still have no staging table,
and the ETL would fail on its first INSERT.

What it is
----------
The staging table has the same columns as STUDENT_DATA: the ETL inserts 36 of
them by name and the edit screen updates a further four (NATIONAL_ID,
STUDENT_PASSPORT_NUMBER, STUDENT_EMAIL, STUDENT_CELLPHONE). The definition is
therefore read from STUDENT_DATA's own catalogue entry rather than transcribed
here, where it would drift the first time STUDENT_DATA gained a column.

This script reads STUDENT_DATA and changes nothing in it.

Two deliberate differences from STUDENT_DATA:

  1. STUDENT_ID is NOT an IDENTITY column here. In staging it is only the
     landing row's id (STUDENT_SCHOLARSHIP_DATA_IMPORT.ID) so a re-run can
     find and replace the row; the ETL supplies the value. It is not a real
     student id — those are created by STUDENT_DATA's own IDENTITY, and a
     staged row's id is to be ignored for that purpose. `SELECT … INTO`
     copies the IDENTITY property from a plain column reference and drops it
     when the query contains a join, which is why the CROSS JOIN is there.
  2. It carries V053's edit-tracking columns (IS_EDITED, EDITED_AT,
     EDITED_BY) and the index the run filters on. V053 is guarded, so running
     it before or after this script is harmless either way.

Constraints and triggers are deliberately NOT copied: staging is derived data
with no audit trail, no foreign keys (unresolved lookups are the whole point
of a staging row) and no country drift trigger.

Moving a staged row into STUDENT_DATA is outside this script and outside the
current stage of the work; nothing here prepares for it.

Idempotent: every statement checks before it acts.
================================================================================
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF OBJECT_ID('MOHECORE.STUDENT_DATA', 'U') IS NULL
BEGIN
    RAISERROR('MOHECORE.STUDENT_DATA does not exist; STUDENT_DATA_STAGING is derived from it.', 16, 1);
    RETURN;
END
GO

IF OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING', 'U') IS NULL
BEGIN
    /* The join strips IDENTITY from STUDENT_ID; TOP 0 copies no rows. */
    SELECT TOP 0 s.*
      INTO MOHECORE.STUDENT_DATA_STAGING
      FROM MOHECORE.STUDENT_DATA s
     CROSS JOIN (SELECT 1 AS one) AS strip_identity;

    ALTER TABLE MOHECORE.STUDENT_DATA_STAGING
        ADD CONSTRAINT PK_STUDENT_DATA_STAGING PRIMARY KEY CLUSTERED (STUDENT_ID);

    PRINT 'Created MOHECORE.STUDENT_DATA_STAGING from STUDENT_DATA.';
END
ELSE
    PRINT 'MOHECORE.STUDENT_DATA_STAGING already exists; left as is.';
GO

/* ── V053 columns, so a fresh database ends up identical whichever order the
      two scripts ran in ─────────────────────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING') AND name = 'IS_EDITED')
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_STAGING
        ADD IS_EDITED BIT NOT NULL
            CONSTRAINT DF_STUDENT_DATA_STAGING_IS_EDITED DEFAULT (0);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING') AND name = 'EDITED_AT')
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_STAGING ADD EDITED_AT DATETIME2(3) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING') AND name = 'EDITED_BY')
BEGIN
    ALTER TABLE MOHECORE.STUDENT_DATA_STAGING ADD EDITED_BY BIGINT NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_STUDENT_DATA_STAGING_COUNTRY_EDITED'
               AND object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING'))
BEGIN
    CREATE INDEX IX_STUDENT_DATA_STAGING_COUNTRY_EDITED
        ON MOHECORE.STUDENT_DATA_STAGING (STUDY_COUNTRY_IN, IS_EDITED);
END
GO

/* ── Verification ──────────────────────────────────────────────────────────────
   The ETL's INSERT names every column it supplies and relies on no default.
   Anything NOT NULL without a default that it does *not* supply would make
   the first run fail, so say so here rather than there. */
;WITH supplied AS (
    SELECT name FROM (VALUES
        ('STUDENT_ID'), ('STUDENT_NAME'), ('STUDENT_COUNTRY_ID'), ('STUDENT_GOV_ID'), ('STUDENT_MUD_ID'),
        ('STUDENT_DELEGATION_DECISION_YEAR'), ('STUDENT_DEGREE_ID'), ('STUDENT_MAJOR_ID'), ('STUDENT_MAJOR_NAME'),
        ('STUDENT_DELEGATE_DECISION_NO'), ('STUDENT_STUDY_NO_OF_YEARS'), ('STUDENT_CURRENT_LEVEL_ID'),
        ('STUDENT_FIRST_RECEIPT_OF_DUE'), ('STUDENT_AMOUNT'), ('STUDENT_NOTE_1'), ('STUDENT_NOTE_2'),
        ('STUDENT_STUDY_STATUS_ID'), ('STUDENT_AGENCY_TYPE_ID'), ('STUDENT_MINISTRY_TYPE_ID'),
        ('STUDENT_STUDY_TYPE_ID'), ('STUDENT_UNIVERSITY_TYPE_ID'), ('STUDENT_ADDED_QUARTER'), ('STUDENT_LAST_QUARTER'),
        ('STUDY_UNIVERSITY_IN'), ('STUDY_COLLEGE_IN'), ('STUDY_COUNTRY_IN'), ('SYUDY_GOV_IN'), ('STUDY_EDUCATION_IN'),
        ('STUDY_MAJOR_IN'), ('STUDENT_GENDER'), ('STUDENT_PASSPORT_ISSUE_COUNTRY_ID'),
        ('STUDY_UNIVERSITY_FEE_ID'), ('STUDY_UNIVERSITY_FEE_STATUS_ID'), ('STUDENT_MIDATE'),
        ('IS_DELETED'), ('RECORD_VISIBILITY'), ('CURRENT_QUARTER'), ('IS_EDITED')
    ) AS v(name)
)
SELECT c.name AS column_not_supplied_by_etl_and_not_null_without_default
  FROM sys.columns c
  LEFT JOIN supplied s ON s.name = c.name
 WHERE c.object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING')
   AND c.is_nullable = 0
   AND c.default_object_id = 0
   AND s.name IS NULL;
GO

SELECT COUNT(*) AS staging_columns
  FROM sys.columns
 WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA_STAGING');
GO
