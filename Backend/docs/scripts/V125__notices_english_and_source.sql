/*
    V125 — System notices in both languages, and tied to what raised them
    (plan W3-08; review round 2 H2).

    MOHE_ALERTS.alerts held one title and one message. The approval notices were
    written in Arabic only, so an English dashboard read Arabic, and nothing tied a
    notice to its request: a decided request left its "awaiting approval" notice
    ACTIVE forever and the panel filled up (123 notices, 3 requests still open, on
    dev on 22 Sep).

    1. alert_title_en / alert_message_en: the English text, where a notice has one.
       The screens pick by language and fall back to the Arabic.
    2. source_ref: what raised the notice, e.g. 'APPROVAL:20732', so it can be
       resolved when that is decided.
    3. The existing approval notices get their source_ref (read from the request
       number in their text), an English text built from their request, and are
       RESOLVED where the request is no longer pending or no longer exists.

    Idempotent. THROWs if a column is missing afterwards.
    Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF COL_LENGTH(N'MOHE_ALERTS.alerts', N'alert_title_en') IS NULL
    ALTER TABLE MOHE_ALERTS.alerts ADD alert_title_en NVARCHAR(255) NULL;
IF COL_LENGTH(N'MOHE_ALERTS.alerts', N'alert_message_en') IS NULL
    ALTER TABLE MOHE_ALERTS.alerts ADD alert_message_en NVARCHAR(MAX) NULL;
IF COL_LENGTH(N'MOHE_ALERTS.alerts', N'source_ref') IS NULL
    ALTER TABLE MOHE_ALERTS.alerts ADD source_ref NVARCHAR(100) NULL;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE name = N'IX_alerts_source_ref' AND object_id = OBJECT_ID(N'MOHE_ALERTS.alerts'))
    CREATE INDEX IX_alerts_source_ref ON MOHE_ALERTS.alerts (source_ref) WHERE source_ref IS NOT NULL;

/* ---- the approval notices already written ------------------------------------------ */

;WITH parsed AS (
    SELECT a.alert_id,
           TRY_CAST(SUBSTRING(a.alert_message,
                              CHARINDEX(N'طلب رقم ', a.alert_message) + LEN(N'طلب رقم '),
                              CHARINDEX(N':', a.alert_message) - CHARINDEX(N'طلب رقم ', a.alert_message) - LEN(N'طلب رقم '))
                    AS BIGINT) AS request_id
      FROM MOHE_ALERTS.alerts a
     WHERE a.alert_title = N'طلب موافقة جديد'
       AND a.source_ref IS NULL
       AND CHARINDEX(N'طلب رقم ', a.alert_message) = 1
       AND CHARINDEX(N':', a.alert_message) > LEN(N'طلب رقم ')
)
UPDATE a
   SET a.source_ref = N'APPROVAL:' + CAST(p.request_id AS NVARCHAR(20))
  FROM MOHE_ALERTS.alerts a
  JOIN parsed p ON p.alert_id = a.alert_id
 WHERE p.request_id IS NOT NULL;

UPDATE a
   SET a.alert_title_en = N'New approval request',
       a.alert_message_en = N'Request ' + CAST(r.Id AS NVARCHAR(20)) + N': '
            + CASE r.RequestType WHEN 'INSERT' THEN N'add' WHEN 'UPDATE' THEN N'update'
                                 WHEN 'DELETE' THEN N'delete' ELSE CAST(r.RequestType AS NVARCHAR(20)) END
            + N' — ' + ISNULL(NULLIF(c.EntityTypeEn, N''), r.EntityType)
            + N'. Awaiting approval on the pending approvals page.'
  FROM MOHE_ALERTS.alerts a
  JOIN ALERTCORE.ApprovalRequests r ON a.source_ref = N'APPROVAL:' + CAST(r.Id AS NVARCHAR(20))
  LEFT JOIN ALERTCORE.ApprovalConfigs c ON c.EntityType = r.EntityType
 WHERE a.alert_title_en IS NULL;

UPDATE a
   SET a.alert_status = N'RESOLVED'
  FROM MOHE_ALERTS.alerts a
 WHERE a.source_ref LIKE N'APPROVAL:%'
   AND a.alert_status = N'ACTIVE'
   AND NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalRequests r
                    WHERE a.source_ref = N'APPROVAL:' + CAST(r.Id AS NVARCHAR(20))
                      AND r.Status IN ('PENDING', 'IN_PROGRESS'));

/* ---- checks ------------------------------------------------------------------------ */

IF COL_LENGTH(N'MOHE_ALERTS.alerts', N'alert_title_en') IS NULL
   OR COL_LENGTH(N'MOHE_ALERTS.alerts', N'alert_message_en') IS NULL
   OR COL_LENGTH(N'MOHE_ALERTS.alerts', N'source_ref') IS NULL
    THROW 51250, 'V125: a notice column is missing', 1;
