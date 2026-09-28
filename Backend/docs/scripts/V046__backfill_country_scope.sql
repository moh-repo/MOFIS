/*
================================================================================
V046 — Backfill the country on rows that lost it
================================================================================

Why
---
Ten screens were returning data unfiltered by country. Enforcing the filter is a
code change (see the services), but enforcement alone would hide rows that are
legitimately somebody's — a country was simply never written to them. Filtering
first and backfilling later would look like data loss.

So this runs first, and it only writes a country it can *derive*. It invents
nothing. Where a country cannot be established the row keeps its NULL and becomes
ministry-only, and the script says how many of those there are rather than
quietly leaving them.

What it covers
--------------
1. ALERTCORE.ApprovalRequests — 9 of 44 rows have CountryId NULL. All nine are
   derivable, by three routes tried in order of authority:
      a. EntityType + EntityId against the source table. The request points at a
         real record, so that record's country is the answer.
      b. An explicit country in the RequestData JSON ($.studyCountryIn or
         $.studyCountryInId). Used for INSERT requests, where EntityId is NULL
         because the entity does not exist yet.
      c. $.studentId in the RequestData, resolved through STUDENT_DATA.
   (a) beats (b) beats (c): a stored record outranks a payload, and an explicit
   country outranks one inferred from a student.

2. MOHECORE.WAFAR_SPENDING — 11 of 17 rows have STUDY_COUNTRY_IN_ID NULL.
   **None of them are derivable and this script does not touch them.** All eleven
   are BENEFICIARY_TYPE = 'OTHER' attaché running costs — rent, salaries,
   transport — with no STUDENT_ID and no TRAVEL_TICKET_ID. Nothing in the row
   says which attaché spent the money. They are reported and left alone;
   WafarSpendingService stamps the country on new ones so this stops growing.

3. MOHECORE.NOTIFICATION_LOG — deliberately not backfilled. NOTIFICATION_LOG_COUNTRY
   is empty against 8 logs. Three of those were sent with SCOPE = 'ALL' and went
   to everyone; the notification history query treats such a log as global rather
   than being given fabricated country rows here. The GROUP and ONE logs cannot be
   resolved at all and stay ministry-only.

Re-runnable
-----------
Only ever fills a NULL, so a second run is a no-op. No schema changes.

    sqlcmd -S <server> -U sa -P <pw> -d MOHE -I -f 65001 -i V046__backfill_country_scope.sql
================================================================================
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

PRINT N'';
PRINT N'=== V046: country backfill ===';
GO

/*------------------------------------------------------------------------------
  1. ApprovalRequests
------------------------------------------------------------------------------*/
DECLARE @approvalsBefore int =
        (SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE CountryId IS NULL);

PRINT N'ApprovalRequests with no country, before: ' + CAST(@approvalsBefore AS nvarchar(10));

/*  Resolve all three routes in one pass, then take the first that produced a
    value. Done as a single UPDATE ... FROM so a row is visited once, and so the
    precedence is visible in one COALESCE rather than spread over three
    statements that would each have to re-check what the previous one did.       */
UPDATE ar
   SET ar.CountryId = x.derived
  FROM ALERTCORE.ApprovalRequests ar
  JOIN (
        SELECT ar2.Id,
               COALESCE(
                   -- (a) the record the request points at
                   sd_entity.STUDY_COUNTRY_IN,
                   sf_entity.STUDY_COUNTRY_IN_ID,
                   uf_entity.STUDY_COUNTRY_IN_ID,
                   -- (b) an explicit country in the payload
                   TRY_CAST(JSON_VALUE(ar2.RequestData, '$.studyCountryIn')   AS int),
                   TRY_CAST(JSON_VALUE(ar2.RequestData, '$.studyCountryInId') AS int),
                   TRY_CAST(JSON_VALUE(ar2.RequestData, '$.countryId')        AS int),
                   -- (c) the student named in the payload
                   sd_json.STUDY_COUNTRY_IN
               ) AS derived
          FROM ALERTCORE.ApprovalRequests ar2
          LEFT JOIN MOHECORE.STUDENT_DATA sd_entity
                 ON ar2.EntityType IN (N'STUDENT_DATA', N'STUDENT_EXTENSION',
                                       N'STUDENT_TRAVEL_TICKET', N'STUDENT_DOCUMENTS')
                AND sd_entity.STUDENT_ID = TRY_CAST(ar2.EntityId AS int)
          LEFT JOIN MOHECORE.STUDENT_FINANCE sf_entity
                 ON ar2.EntityType IN (N'STUDENT_FINANCE', N'STUDENT_FINANCE_WAFAR')
                AND sf_entity.STUDENT_ID = TRY_CAST(ar2.EntityId AS int)
          LEFT JOIN MOHECORE.STUDY_UNIVERSITY_FEE uf_entity
                 ON ar2.EntityType = N'STUDY_UNIVERSITY_FEE'
                AND uf_entity.STUDENT_ID = TRY_CAST(ar2.EntityId AS int)
          LEFT JOIN MOHECORE.STUDENT_DATA sd_json
                 ON sd_json.STUDENT_ID = TRY_CAST(JSON_VALUE(ar2.RequestData, '$.studentId') AS int)
         WHERE ar2.CountryId IS NULL
       ) x ON x.Id = ar.Id
 WHERE ar.CountryId IS NULL
   AND x.derived IS NOT NULL
   -- Never write a country that is not in the lookup: a stale id filters to
   -- nothing and would be worse than the NULL it replaced.
   AND EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN c
                WHERE c.STUDY_COUNTRY_IN_ID = x.derived);

DECLARE @approvalsFilled int = @@ROWCOUNT;
DECLARE @approvalsAfter  int =
        (SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE CountryId IS NULL);

PRINT N'  rows filled: ' + CAST(@approvalsFilled AS nvarchar(10));
PRINT N'  still NULL (ministry-only from here): ' + CAST(@approvalsAfter AS nvarchar(10));
GO

/*------------------------------------------------------------------------------
  2. WAFAR_SPENDING — report only

  Two derivations are attempted and both are expected to find nothing on the
  current data. They are here because the script must not assume the state it was
  written against: if a student-linked row ever loses its country, this fills it.
------------------------------------------------------------------------------*/
DECLARE @wafarBefore int =
        (SELECT COUNT(*) FROM MOHECORE.WAFAR_SPENDING WHERE STUDY_COUNTRY_IN_ID IS NULL);

PRINT N'';
PRINT N'WAFAR_SPENDING with no country, before: ' + CAST(@wafarBefore AS nvarchar(10));

UPDATE ws
   SET ws.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
  FROM MOHECORE.WAFAR_SPENDING ws
  JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = ws.STUDENT_ID
 WHERE ws.STUDY_COUNTRY_IN_ID IS NULL
   AND sd.STUDY_COUNTRY_IN IS NOT NULL;

DECLARE @fromStudent int = @@ROWCOUNT;
PRINT N'  filled from the student: ' + CAST(@fromStudent AS nvarchar(10));

UPDATE ws
   SET ws.STUDY_COUNTRY_IN_ID = tt.STUDY_COUNTRY_IN_ID
  FROM MOHECORE.WAFAR_SPENDING ws
  JOIN MOHECORE.STUDENT_TRAVEL_TICKET tt ON tt.TRAVEL_TICKET_ID = ws.TRAVEL_TICKET_ID
 WHERE ws.STUDY_COUNTRY_IN_ID IS NULL
   AND tt.STUDY_COUNTRY_IN_ID IS NOT NULL;

DECLARE @fromTicket int = @@ROWCOUNT;
PRINT N'  filled from the travel ticket: ' + CAST(@fromTicket AS nvarchar(10));
GO

PRINT N'';
PRINT N'  Rows left without a country. These are attaché running costs with no';
PRINT N'  student and no ticket, so nothing in them names an attaché. They are';
PRINT N'  ministry-only until someone assigns them:';

SELECT SPENDING_ID, BENEFICIARY_TYPE, AMOUNT, SPENDING_DATE, STATUS,
       LEFT(ISNULL(DESCRIPTION_AR, DESCRIPTION_EN), 60) AS DESCRIPTION
  FROM MOHECORE.WAFAR_SPENDING
 WHERE STUDY_COUNTRY_IN_ID IS NULL
 ORDER BY SPENDING_ID;
GO

/*------------------------------------------------------------------------------
  3. Notification logs — report only, see the header
------------------------------------------------------------------------------*/
PRINT N'';
PRINT N'Notification logs, by whether their country can be known:';

SELECT CASE WHEN nl.SCOPE = N'ALL' THEN N'ALL — global, visible to everyone'
            ELSE N'GROUP/ONE — unresolvable, ministry-only' END AS disposition,
       COUNT(*) AS logs
  FROM MOHECORE.NOTIFICATION_LOG nl
 WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.NOTIFICATION_LOG_COUNTRY c WHERE c.LOG_ID = nl.ID)
 GROUP BY CASE WHEN nl.SCOPE = N'ALL' THEN N'ALL — global, visible to everyone'
               ELSE N'GROUP/ONE — unresolvable, ministry-only' END;
GO

/*------------------------------------------------------------------------------
  Verification
------------------------------------------------------------------------------*/
PRINT N'';
PRINT N'=== V046 result ===';

SELECT N'ApprovalRequests' AS [table],
       COUNT(*) AS rows_total,
       SUM(CASE WHEN CountryId IS NULL THEN 1 ELSE 0 END) AS no_country
  FROM ALERTCORE.ApprovalRequests
UNION ALL
SELECT N'WAFAR_SPENDING', COUNT(*),
       SUM(CASE WHEN STUDY_COUNTRY_IN_ID IS NULL THEN 1 ELSE 0 END)
  FROM MOHECORE.WAFAR_SPENDING
UNION ALL
SELECT N'STUDY_UNIVERSITY_FEE_ARCH', COUNT(*),
       SUM(CASE WHEN STUDY_COUNTRY_IN_ID IS NULL THEN 1 ELSE 0 END)
  FROM MOHECORE.STUDY_UNIVERSITY_FEE_ARCH
UNION ALL
SELECT N'ALERT_EVENT_CASE', COUNT(*),
       SUM(CASE WHEN STUDENT_COUNTRY_ID IS NULL THEN 1 ELSE 0 END)
  FROM ALERTEVENTCASES.ALERT_EVENT_CASE;
GO

PRINT N'Expected after a first run: ApprovalRequests 0 without a country,';
PRINT N'WAFAR_SPENDING 11 (the attaché running costs), the other two already 0.';
GO
