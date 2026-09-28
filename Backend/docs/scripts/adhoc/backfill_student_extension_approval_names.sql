/* ============================================================================
   Backfill the student name into existing Student Extension approval requests
   ----------------------------------------------------------------------------
   Why
     The pending-approval view shows "who" a request is for by reading a
     studentName from the request payload (RequestData JSON). STUDENT_EXTENSION
     requests created before the studentName fix only stored studentId, so the
     name is blank for those already-pending requests.

     New requests carry the name automatically (server stamps it on creation).
     This script fills it in for requests that already exist so you don't have to
     recreate them.

   What it does
     For every PENDING / IN_PROGRESS STUDENT_EXTENSION request whose payload has a
     studentId but no studentName, it looks up the name in MOHECORE.STUDENT_DATA
     and writes it into the JSON via JSON_MODIFY.

   Safe to re-run: only touches rows whose studentName is currently missing/blank.
   Requires SQL Server 2016+ (JSON_VALUE / JSON_MODIFY).
   ============================================================================ */

SET NOCOUNT ON;

UPDATE r
   SET r.RequestData = JSON_MODIFY(r.RequestData, '$.studentName', sd.STUDENT_NAME)
  FROM ALERTCORE.ApprovalRequests AS r
 CROSS APPLY (SELECT TRY_CONVERT(INT, JSON_VALUE(r.RequestData, '$.studentId')) AS sid) AS j
  JOIN MOHECORE.STUDENT_DATA AS sd ON sd.STUDENT_ID = j.sid
 WHERE r.EntityType = 'STUDENT_EXTENSION'
   AND r.Status IN ('PENDING', 'IN_PROGRESS')
   AND ISJSON(r.RequestData) = 1
   AND NULLIF(LTRIM(RTRIM(JSON_VALUE(r.RequestData, '$.studentName'))), '') IS NULL
   AND sd.STUDENT_NAME IS NOT NULL;

PRINT 'STUDENT_EXTENSION requests backfilled with studentName: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

/* Verify */
SELECT
    r.Id,
    r.RequestType,
    r.Status,
    JSON_VALUE(r.RequestData, '$.studentId')   AS StudentId,
    JSON_VALUE(r.RequestData, '$.studentName') AS StudentName
  FROM ALERTCORE.ApprovalRequests AS r
 WHERE r.EntityType = 'STUDENT_EXTENSION'
   AND r.Status IN ('PENDING', 'IN_PROGRESS')
 ORDER BY r.RequestedAt DESC;
