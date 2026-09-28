/* ============================================================================
   V083 — A request nobody can see is not pending; it is lost.

   Why
   ---
   V046 filled the nine ApprovalRequests rows that had no country, and V065 put
   the table under tenant_country_policy. Since then a NULL CountryId is not
   "ministry-only" in the comfortable sense: the predicate lets no attaché see
   the row, including the officer who raised it, and only an account with
   TenantAll can. ApprovalCountryScopeTest.everyRequestHasACountry asserts that
   none exist.

   One does. Request 20155 (UPDATE on STUDENT_FINANCE, student 12, raised
   31 Aug 2026 by superadmin) came in after V046 with no country, and its three
   AUDITLOG.AuditTrail rows inherited the NULL from it — TR_AuditTrail_ResolveCountry
   reads the request's own CountryId, so a source with none resolves to none.
   Two more tests fail on those three rows.

   The cause is in the application and is fixed in the same change:
   ApprovalIntegrationService.createStudentFinanceRequest was the one creator
   that never resolved a country, passing null and leaving
   ApprovalWorkflowService to fall back on the requester's own attaché — which
   is nothing when the requester holds more than one country, as superadmin
   does. It now derives the country from the finance row, the row it replaces,
   or the student, the way every other creator in that class already did.

   What
   ----
   1. Re-runs V046's derivation for any request that still has no country: the
      record the request points at, an explicit country in the payload, then the
      student named in the payload. A country not in STUDY_COUNTRY_IN is never
      written — a stale id filters to nothing and would be worse than the NULL.
   2. Re-resolves AUDITLOG.AuditTrail.COUNTRY_ID for the ApprovalRequests rows
      whose source now has one. Nothing else is touched: UMSCORE.Users rows stay
      NULL by design, a user account having no country.
   3. Verifies, and throws rather than reports.

   Rollback
   --------
   None needed: it writes a derived country where there was none, and the value
   is what every reader would have computed anyway. To undo, set the two rows
   back to NULL by id.

   Idempotent. On a database with no uncountried request it derives nothing and
   says so.
   ============================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* This script reads and writes across every country, so it must see them. */
EXEC sp_set_session_context @key = N'TenantAll', @value = 1;
GO

/* ── 1. A country for every request that can have one ────────────────────── */
DECLARE @before INT = (SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE CountryId IS NULL);
PRINT N'ApprovalRequests with no country, before: ' + CAST(@before AS NVARCHAR(10));

UPDATE ar
   SET ar.CountryId = x.derived
  FROM ALERTCORE.ApprovalRequests ar
  JOIN (
        SELECT ar2.Id,
               COALESCE(
                   sd_entity.STUDY_COUNTRY_IN,
                   sf_entity.STUDY_COUNTRY_IN_ID,
                   uf_entity.STUDY_COUNTRY_IN_ID,
                   TRY_CAST(JSON_VALUE(ar2.RequestData, '$.studyCountryIn')   AS INT),
                   TRY_CAST(JSON_VALUE(ar2.RequestData, '$.studyCountryInId') AS INT),
                   TRY_CAST(JSON_VALUE(ar2.RequestData, '$.countryId')        AS INT),
                   sd_json.STUDY_COUNTRY_IN
               ) AS derived
          FROM ALERTCORE.ApprovalRequests ar2
          LEFT JOIN MOHECORE.STUDENT_DATA sd_entity
                 ON ar2.EntityType IN (N'STUDENT_DATA', N'STUDENT_EXTENSION',
                                       N'STUDENT_TRAVEL_TICKET', N'STUDENT_DOCUMENTS')
                AND sd_entity.STUDENT_ID = TRY_CAST(ar2.EntityId AS INT)
          LEFT JOIN MOHECORE.STUDENT_FINANCE sf_entity
                 ON ar2.EntityType IN (N'STUDENT_FINANCE', N'STUDENT_FINANCE_WAFAR')
                AND sf_entity.STUDENT_ID = TRY_CAST(ar2.EntityId AS INT)
          LEFT JOIN MOHECORE.STUDY_UNIVERSITY_FEE uf_entity
                 ON ar2.EntityType = N'STUDY_UNIVERSITY_FEE'
                AND uf_entity.STUDENT_ID = TRY_CAST(ar2.EntityId AS INT)
          LEFT JOIN MOHECORE.STUDENT_DATA sd_json
                 ON sd_json.STUDENT_ID = TRY_CAST(JSON_VALUE(ar2.RequestData, '$.studentId') AS INT)
         WHERE ar2.CountryId IS NULL
       ) x ON x.Id = ar.Id
 WHERE ar.CountryId IS NULL
   AND x.derived IS NOT NULL
   AND EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN c
                WHERE c.STUDY_COUNTRY_IN_ID = x.derived);

PRINT N'  rows filled: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

IF EXISTS (SELECT 1 FROM ALERTCORE.ApprovalRequests WHERE CountryId IS NULL)
BEGIN
    PRINT N'  still uncountried — nothing in these names a country, so they are reported, not guessed:';
    SELECT Id, RequestType, EntityType, EntityId, Status, RequestedBy, RequestedAt
      FROM ALERTCORE.ApprovalRequests WHERE CountryId IS NULL ORDER BY Id;
END
GO

/* ── 2. The audit rows those requests wrote ──────────────────────────────── */
UPDATE at
   SET at.COUNTRY_ID = ar.CountryId
  FROM AUDITLOG.AuditTrail at
  JOIN ALERTCORE.ApprovalRequests ar
    ON ar.Id = TRY_CAST(at.RECORD_ID AS BIGINT)
 WHERE at.[SCHEMA_NAME] = N'ALERTCORE'
   AND at.TABLE_NAME = N'ApprovalRequests'
   AND at.COUNTRY_ID IS NULL
   AND ar.CountryId IS NOT NULL;

PRINT N'AuditTrail rows re-resolved: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));
GO

/* ── 3. Verification — throws rather than reports ────────────────────────── */
DECLARE @requests INT = (SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE CountryId IS NULL);
DECLARE @audit INT = (
    SELECT COUNT(*) FROM AUDITLOG.AuditTrail
     WHERE COUNTRY_ID IS NULL AND TABLE_NAME <> N'Users');

IF @requests > 0
    RAISERROR('V083: %d approval request(s) still have no country. Read the list above: each names no student, no record and no country, so a value cannot be derived without inventing one. Decide each by hand.', 16, 1, @requests);
ELSE IF @audit > 0
    RAISERROR('V083: %d audit row(s) outside UMSCORE.Users still have no country. Their source table may be missing from TR_AuditTrail_ResolveCountry (V047, extended by V067).', 16, 1, @audit);
ELSE
    PRINT 'V083 OK: every approval request carries a country, and every audit row outside UMSCORE.Users resolves one.';
GO
