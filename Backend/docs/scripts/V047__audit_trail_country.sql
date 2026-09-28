/*
================================================================================
V047 — A country on the audit trail
================================================================================

Why
---
AUDITLOG.AuditTrail holds 11,544 rows, of which **11,076 are individual
STUDENT_DATA changes**. Anyone holding the AUDIT permission could read every
student's change history for every country, because the table has no country
column and so nothing could filter it. This is the largest of the ten scope gaps
and the only one needing a schema change.

How the country gets there
--------------------------
Two mechanisms, because the triggers that feed this table are not uniform.

1. **The short triggers stamp it directly.** The INSERT and DELETE triggers on
   STUDENT_DATA, STUDENT_FINANCE and ApprovalRequests are one-line inserts, so
   they are rewritten here to carry the country from `inserted` / `deleted`.
   DELETE is the case that *must* work this way: by the time anything else could
   look, the record is gone. There are no DELETE rows in the table today, but the
   triggers exist and the deletion of a student is exactly the event a country
   officer should be able to see.

2. **A resolver trigger on AuditTrail fills the rest.** The three UPDATE triggers
   are long generated bodies that enumerate every column of the audited table;
   rewriting them here would mean transcribing ~24 column comparisons by hand and
   would drift the moment a column is added. Instead `TR_AuditTrail_ResolveCountry`
   fills COUNTRY_ID from the source record after the row lands. For an UPDATE the
   record still exists, so this is exact.

   It also catches anything written through AUDITLOG.usp_WriteAuditTrail, so that
   procedure needs no change.

Note the resolver knows three tables by name. UMSCORE.Users is deliberately absent
— a user account is a ministry-level record with no country, and its audit rows
stay NULL. If another table is put under audit, add it to the resolver:
`AuditTrailCountryTest.everyAuditedTableHasACountrySource` fails until you do.

What a NULL country means
-------------------------
Ministry-only. MoheAuditLogService shows a NULL row to a ministry user and hides
it from a country user. Fail-closed: not seeing the ministry's own administrative
history is a smaller harm than one attaché reading another's students.

Re-runnable
-----------
The column add is guarded, the triggers are CREATE OR ALTER, and the backfill only
ever fills a NULL.

    sqlcmd -S <server> -U sa -P <pw> -d MOHE -I -f 65001 -i V047__audit_trail_country.sql

Run V046 first: it fills ApprovalRequests.CountryId, which this script reads.
================================================================================
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

PRINT N'';
PRINT N'=== V047: audit trail country ===';
GO

/*------------------------------------------------------------------------------
  1. The column
------------------------------------------------------------------------------*/
IF COL_LENGTH('AUDITLOG.AuditTrail', 'COUNTRY_ID') IS NULL
BEGIN
    ALTER TABLE AUDITLOG.AuditTrail ADD COUNTRY_ID int NULL;
    PRINT N'AuditTrail.COUNTRY_ID added.';
END
ELSE
    PRINT N'AuditTrail.COUNTRY_ID already present.';
GO

/*  No FK to STUDY_COUNTRY_IN. An audit row is a historical statement and must
    survive the country lookup being reorganised — the duplicate-country cleanup
    V039 reported is still outstanding. A dangling id here is better than an audit
    row that cannot be written.                                                  */
IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('AUDITLOG.AuditTrail')
                  AND name = 'IX_AuditTrail_Country_ChangedAt')
BEGIN
    CREATE INDEX IX_AuditTrail_Country_ChangedAt
        ON AUDITLOG.AuditTrail (COUNTRY_ID, CHANGED_AT DESC)
        INCLUDE (TABLE_NAME, OPERATION);
    PRINT N'IX_AuditTrail_Country_ChangedAt created.';
END
GO

/*------------------------------------------------------------------------------
  2. The resolver

  Fires after rows land in AuditTrail and fills COUNTRY_ID where the trigger that
  wrote the row did not. DELETE rows are skipped on purpose: the record they
  describe no longer exists, so a lookup would find nothing and the stamping
  triggers in section 3 are what covers that case.

  No recursion risk — this trigger updates the table it is on, and an UPDATE does
  not fire an AFTER INSERT trigger.
------------------------------------------------------------------------------*/
CREATE OR ALTER TRIGGER AUDITLOG.TR_AuditTrail_ResolveCountry
ON AUDITLOG.AuditTrail
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM inserted WHERE COUNTRY_ID IS NULL AND OPERATION <> N'DELETE')
        RETURN;

    UPDATE at
       SET at.COUNTRY_ID = COALESCE(sd.STUDY_COUNTRY_IN, sf.STUDY_COUNTRY_IN_ID, ar.CountryId)
      FROM AUDITLOG.AuditTrail at
      JOIN inserted i ON i.LOG_ID = at.LOG_ID
      LEFT JOIN MOHECORE.STUDENT_DATA sd
             ON at.[SCHEMA_NAME] = N'MOHECORE' AND at.TABLE_NAME = N'STUDENT_DATA'
            AND sd.STUDENT_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN MOHECORE.STUDENT_FINANCE sf
             ON at.[SCHEMA_NAME] = N'MOHECORE' AND at.TABLE_NAME = N'STUDENT_FINANCE'
            AND sf.STUDENT_ID = TRY_CAST(at.RECORD_ID AS int)
      LEFT JOIN ALERTCORE.ApprovalRequests ar
             ON at.[SCHEMA_NAME] = N'ALERTCORE' AND at.TABLE_NAME = N'ApprovalRequests'
            AND ar.Id = TRY_CAST(at.RECORD_ID AS bigint)
     WHERE at.COUNTRY_ID IS NULL
       AND at.OPERATION <> N'DELETE';
END
GO

PRINT N'TR_AuditTrail_ResolveCountry installed.';
GO

/*------------------------------------------------------------------------------
  3. The stamping triggers

  Rewritten verbatim from their current definitions with COUNTRY_ID added. The
  Users triggers are left alone: a user account has no country.
------------------------------------------------------------------------------*/
CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_DATA_Audit_Insert
ON MOHECORE.STUDENT_DATA AFTER INSERT AS
SET NOCOUNT ON;
DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy=@cb OUTPUT, @IpAddress=@ip OUTPUT, @SessionId=@sid OUTPUT;
INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES, CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
SELECT N'MOHECORE', N'STUDENT_DATA', CAST(i.STUDENT_ID AS NVARCHAR(255)), N'INSERT', NULL, NULL, @cb, @ip, @sid, GETUTCDATE(), N'MOHE_SYSTEM', i.STUDY_COUNTRY_IN FROM inserted i;
GO

CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_DATA_Audit_Delete
ON MOHECORE.STUDENT_DATA AFTER DELETE AS
SET NOCOUNT ON;
DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy=@cb OUTPUT, @IpAddress=@ip OUTPUT, @SessionId=@sid OUTPUT;
INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES, CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
SELECT N'MOHECORE', N'STUDENT_DATA', CAST(d.STUDENT_ID AS NVARCHAR(255)), N'DELETE', NULL, NULL, @cb, @ip, @sid, GETUTCDATE(), N'MOHE_SYSTEM', d.STUDY_COUNTRY_IN FROM deleted d;
GO

CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_FINANCE_Audit_Insert
ON MOHECORE.STUDENT_FINANCE AFTER INSERT AS
SET NOCOUNT ON;
DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy=@cb OUTPUT, @IpAddress=@ip OUTPUT, @SessionId=@sid OUTPUT;
INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES, CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
SELECT N'MOHECORE', N'STUDENT_FINANCE', CAST(i.STUDENT_ID AS NVARCHAR(255)), N'INSERT', NULL, NULL, @cb, @ip, @sid, GETUTCDATE(), N'MOHE_SYSTEM', i.STUDY_COUNTRY_IN_ID FROM inserted i;
GO

CREATE OR ALTER TRIGGER MOHECORE.TR_STUDENT_FINANCE_Audit_Delete
ON MOHECORE.STUDENT_FINANCE AFTER DELETE AS
SET NOCOUNT ON;
DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy=@cb OUTPUT, @IpAddress=@ip OUTPUT, @SessionId=@sid OUTPUT;
INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES, CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
SELECT N'MOHECORE', N'STUDENT_FINANCE', CAST(d.STUDENT_ID AS NVARCHAR(255)), N'DELETE', NULL, NULL, @cb, @ip, @sid, GETUTCDATE(), N'MOHE_SYSTEM', d.STUDY_COUNTRY_IN_ID FROM deleted d;
GO

CREATE OR ALTER TRIGGER ALERTCORE.TR_ApprovalRequests_Audit_Insert
ON ALERTCORE.ApprovalRequests AFTER INSERT AS
SET NOCOUNT ON;
DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy=@cb OUTPUT, @IpAddress=@ip OUTPUT, @SessionId=@sid OUTPUT;
INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES, CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
SELECT N'ALERTCORE', N'ApprovalRequests', CAST(i.Id AS NVARCHAR(255)), N'INSERT', NULL,
       (SELECT i2.* FROM inserted i2 WHERE i2.Id = i.Id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
       @cb, @ip, @sid, GETUTCDATE(), N'MOHE_SYSTEM', i.CountryId FROM inserted i;
GO

CREATE OR ALTER TRIGGER ALERTCORE.TR_ApprovalRequests_Audit_Delete
ON ALERTCORE.ApprovalRequests AFTER DELETE AS
SET NOCOUNT ON;
DECLARE @cb NVARCHAR(100), @ip NVARCHAR(50), @sid NVARCHAR(255);
EXEC AUDITLOG.usp_ParseAuditContext @ChangedBy=@cb OUTPUT, @IpAddress=@ip OUTPUT, @SessionId=@sid OUTPUT;
INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES, CHANGED_BY, IP_ADDRESS, SESSION_ID, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
SELECT N'ALERTCORE', N'ApprovalRequests', CAST(d.Id AS NVARCHAR(255)), N'DELETE',
       (SELECT d2.* FROM deleted d2 WHERE d2.Id = d.Id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
       NULL, @cb, @ip, @sid, GETUTCDATE(), N'MOHE_SYSTEM', d.CountryId FROM deleted d;
GO

PRINT N'Six stamping triggers rewritten (Users left alone — no country).';
GO

/*------------------------------------------------------------------------------
  4. Backfill the 11,544 existing rows
------------------------------------------------------------------------------*/
DECLARE @before int = (SELECT COUNT(*) FROM AUDITLOG.AuditTrail WHERE COUNTRY_ID IS NULL);
PRINT N'';
PRINT N'Rows with no country, before: ' + CAST(@before AS nvarchar(10));

UPDATE at
   SET at.COUNTRY_ID = sd.STUDY_COUNTRY_IN
  FROM AUDITLOG.AuditTrail at
  JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = TRY_CAST(at.RECORD_ID AS int)
 WHERE at.COUNTRY_ID IS NULL
   AND at.[SCHEMA_NAME] = N'MOHECORE' AND at.TABLE_NAME = N'STUDENT_DATA'
   AND sd.STUDY_COUNTRY_IN IS NOT NULL;
DECLARE @fromStudents int = @@ROWCOUNT;
PRINT N'  from STUDENT_DATA: ' + CAST(@fromStudents AS nvarchar(10));

UPDATE at
   SET at.COUNTRY_ID = sf.STUDY_COUNTRY_IN_ID
  FROM AUDITLOG.AuditTrail at
  JOIN MOHECORE.STUDENT_FINANCE sf ON sf.STUDENT_ID = TRY_CAST(at.RECORD_ID AS int)
 WHERE at.COUNTRY_ID IS NULL
   AND at.[SCHEMA_NAME] = N'MOHECORE' AND at.TABLE_NAME = N'STUDENT_FINANCE'
   AND sf.STUDY_COUNTRY_IN_ID IS NOT NULL;
DECLARE @fromFinance int = @@ROWCOUNT;
PRINT N'  from STUDENT_FINANCE: ' + CAST(@fromFinance AS nvarchar(10));

UPDATE at
   SET at.COUNTRY_ID = ar.CountryId
  FROM AUDITLOG.AuditTrail at
  JOIN ALERTCORE.ApprovalRequests ar ON ar.Id = TRY_CAST(at.RECORD_ID AS bigint)
 WHERE at.COUNTRY_ID IS NULL
   AND at.[SCHEMA_NAME] = N'ALERTCORE' AND at.TABLE_NAME = N'ApprovalRequests'
   AND ar.CountryId IS NOT NULL;
DECLARE @fromApprovals int = @@ROWCOUNT;
PRINT N'  from ApprovalRequests: ' + CAST(@fromApprovals AS nvarchar(10));

/*  A student whose finance row moved country would leave STUDENT_FINANCE audit
    rows resolvable only through STUDENT_DATA. Second pass, lower confidence,
    so it runs last and only on what is still NULL.                              */
UPDATE at
   SET at.COUNTRY_ID = sd.STUDY_COUNTRY_IN
  FROM AUDITLOG.AuditTrail at
  JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = TRY_CAST(at.RECORD_ID AS int)
 WHERE at.COUNTRY_ID IS NULL
   AND at.[SCHEMA_NAME] = N'MOHECORE' AND at.TABLE_NAME = N'STUDENT_FINANCE'
   AND sd.STUDY_COUNTRY_IN IS NOT NULL;
DECLARE @finViaStudent int = @@ROWCOUNT;
PRINT N'  finance rows resolved via the student instead: ' + CAST(@finViaStudent AS nvarchar(10));
GO

/*------------------------------------------------------------------------------
  Verification
------------------------------------------------------------------------------*/
PRINT N'';
PRINT N'=== V047 result: what remains without a country ===';

SELECT [SCHEMA_NAME] AS sch, TABLE_NAME, OPERATION,
       COUNT(*) AS rows_,
       SUM(CASE WHEN COUNTRY_ID IS NULL THEN 1 ELSE 0 END) AS no_country
  FROM AUDITLOG.AuditTrail
 GROUP BY [SCHEMA_NAME], TABLE_NAME, OPERATION
 ORDER BY 1, 2, 3;
GO

PRINT N'Expected: UMSCORE.Users fully NULL (ministry-level, correct); everything';
PRINT N'else resolved. A leftover elsewhere means the audited record was deleted';
PRINT N'after the audit row was written — those stay ministry-only.';
GO
