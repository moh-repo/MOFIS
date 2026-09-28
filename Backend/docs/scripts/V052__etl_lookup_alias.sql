/*
================================================================================
V052 — ETL_LOOKUP_ALIAS: the spellings a human has decided the resolver should
        accept when moving STUDENT_SCHOLARSHIP_DATA_IMPORT into
        STUDENT_DATA_STAGING
================================================================================

Why
---
The imported sheets carry Arabic free text where the operational tables carry
lookup ids. Most of it resolves on its own: the ETL normalises tatweel,
harakat, أ/إ/آ → ا, ة → ه and ى → ي, then matches the result against the
lookup's own name, and on the first real batch that alone placed 838 of 842
degrees and 769 of 842 specializations.

What is left over is not something code should guess at. It is a mix of typos
('دتوراة' for 'دكتوراة'), abbreviations ('د.جامعية' for 'درجة جامعية') and
specializations the ministry writes differently from how STUDENT_MAJOR spells
them. Each of those has exactly one right answer, and only a person knows what
it is. This table is where that person's decision is recorded so the ETL makes
it again on every subsequent run.

It is deliberately data and not code. Adding a spelling is an INSERT, not a
release.

How the resolver uses it
------------------------
For each raw value the ETL tries, in order:

  1. this table, matched on LOOKUP + the normalised RAW_VALUE
  2. the lookup table's own name, normalised the same way
  3. the lookup's "غير مبين" row

Because step 1 comes first, an alias also acts as an override: if the ministry
starts using a name that the lookup happens to spell the same way but means
something else by, the alias wins.

RESOLVED_ID is intentionally NOT a foreign key. There are five different
lookup tables behind LOOKUP and no single table to point at; the ETL validates
the id against the right lookup as it loads, and reports an alias that points
at a row which no longer exists rather than failing the whole batch.

The seeded rows
---------------
Only the three degree spellings that were verified against the first real
batch, each one a variant of a name the lookup already carries:

  د.جامعية   -> 1   جامعية      ('د.' is درجة, degree — the same abbreviation
                                  the ENTITY column uses in 'د. جامعية')
  الدكتوراه  -> 104 دكتوراه     (the definite article, nothing else)
  دتوراة     -> 104 دكتوراه     (a typo, one row in the batch)

The 73 unresolved specializations and 'كليات المجتمع' as an agency type are
deliberately NOT seeded. Nobody has decided what they should be yet, and a
guess recorded here would be indistinguishable from a decision. They show up
on the ETL preview screen as a worklist until someone rules on them.
================================================================================
*/

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE object_id = OBJECT_ID('MOHECORE.ETL_LOOKUP_ALIAS'))
BEGIN
    CREATE TABLE MOHECORE.ETL_LOOKUP_ALIAS (
        ID              INT IDENTITY(1,1)  NOT NULL,

        /* Which lookup this spelling belongs to: DEGREE, MAJOR, AGENCY_TYPE,
           MINISTRY_TYPE or CURRENT_LEVEL. Kept as text rather than a lookup of
           its own — there are five of them and they change when the ETL
           changes, not when the data does. */
        LOOKUP          NVARCHAR(40)       NOT NULL,

        /* The spelling as it appears in the sheet, kept exactly as written so
           it can be read back and recognised. Matching happens on the
           normalised form, which is what NORMALISED_VALUE holds. */
        RAW_VALUE       NVARCHAR(255)      NOT NULL,

        /* RAW_VALUE after the ETL's Arabic normalisation. Stored rather than
           computed so the uniqueness below actually bites: without it, three
           rows spelling the same word with different hamzas would all be
           allowed to point at different ids. */
        NORMALISED_VALUE NVARCHAR(255)     NOT NULL,

        RESOLVED_ID     INT                NOT NULL,

        /* Why this decision was made. Free text, for the next person. */
        NOTE            NVARCHAR(500)      NULL,

        CREATED_AT      DATETIME2(3)       NOT NULL CONSTRAINT DF_ETL_LOOKUP_ALIAS_CREATED DEFAULT SYSUTCDATETIME(),
        CREATED_BY      BIGINT             NULL,

        CONSTRAINT PK_ETL_LOOKUP_ALIAS PRIMARY KEY (ID),

        /* One answer per spelling per lookup. Two rows saying 'دتوراة' is
           sometimes 104 and sometimes 3 would make the load non-deterministic. */
        CONSTRAINT UQ_ETL_LOOKUP_ALIAS UNIQUE (LOOKUP, NORMALISED_VALUE)
    );
END
GO

/* The resolver reads every alias for one lookup at the start of a run. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_ETL_LOOKUP_ALIAS_LOOKUP'
               AND object_id = OBJECT_ID('MOHECORE.ETL_LOOKUP_ALIAS'))
BEGIN
    CREATE INDEX IX_ETL_LOOKUP_ALIAS_LOOKUP
        ON MOHECORE.ETL_LOOKUP_ALIAS (LOOKUP) INCLUDE (NORMALISED_VALUE, RESOLVED_ID);
END
GO

/* ---------------------------------------------------------------------------
   Seed: the three verified degree spellings.

   Guarded by NOT EXISTS so re-running this script is harmless, and matched on
   the normalised value because that is what the unique constraint is on.
   --------------------------------------------------------------------------- */
MERGE MOHECORE.ETL_LOOKUP_ALIAS AS target
USING (VALUES
    (N'DEGREE', N'د.جامعية',  N'د.جامعيه',  1,   N'د. = درجة; same abbreviation the ENTITY column uses'),
    (N'DEGREE', N'الدكتوراه', N'الدكتوراه', 104, N'definite article only'),
    (N'DEGREE', N'دتوراة',    N'دتوراه',    104, N'typo for دكتوراة, one row in the 2025-Q3 Egypt batch')
) AS source (LOOKUP, RAW_VALUE, NORMALISED_VALUE, RESOLVED_ID, NOTE)
ON target.LOOKUP = source.LOOKUP AND target.NORMALISED_VALUE = source.NORMALISED_VALUE
WHEN NOT MATCHED BY TARGET THEN
    INSERT (LOOKUP, RAW_VALUE, NORMALISED_VALUE, RESOLVED_ID, NOTE)
    VALUES (source.LOOKUP, source.RAW_VALUE, source.NORMALISED_VALUE, source.RESOLVED_ID, source.NOTE);
GO
