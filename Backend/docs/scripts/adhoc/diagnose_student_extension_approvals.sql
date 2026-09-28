/* ============================================================================
   Diagnose Student Extension approval requests
   ----------------------------------------------------------------------------
   Run this to understand why an extension add/update seems to "not work" or why a
   pending request is missing from the Pending Approvals list.

   It answers three questions:
     1) Do STUDENT_EXTENSION approval requests exist, and in what status?
     2) Is any pending request hidden from the list because its ConfigId is NULL or
        points to a config row that no longer exists? (the backend list query used to
        INNER JOIN the config and silently drop such rows — now fixed to LEFT JOIN)
     3) Which country is each pending request scoped to? A country-scoped approver
        only sees requests whose CountryId is NULL or within their allowed countries.
   ============================================================================ */

SET NOCOUNT ON;

/* 1) All STUDENT_EXTENSION requests by status */
SELECT r.Status, COUNT(*) AS Cnt
  FROM ALERTCORE.ApprovalRequests r
 WHERE r.EntityType = 'STUDENT_EXTENSION'
 GROUP BY r.Status
 ORDER BY r.Status;

/* 2) Pending/in-progress requests, with config + country, flagging hidden ones */
SELECT
    r.Id,
    r.RequestType,
    r.EntityId,
    r.Status,
    r.CurrentLevel,
    r.CountryId,
    r.ConfigId,
    c.EntityType         AS ConfigEntityType,
    c.IsActive           AS ConfigActive,
    CASE
        WHEN r.ConfigId IS NULL THEN 'HIDDEN: ConfigId is NULL'
        WHEN c.Id IS NULL       THEN 'HIDDEN: ConfigId points to a missing config row'
        ELSE 'visible'
    END                  AS ListVisibility,
    r.RequestedBy,
    r.RequestedAt
  FROM ALERTCORE.ApprovalRequests r
  LEFT JOIN ALERTCORE.ApprovalConfigs c ON c.Id = r.ConfigId
 WHERE r.EntityType = 'STUDENT_EXTENSION'
   AND r.Status IN ('PENDING', 'IN_PROGRESS')
 ORDER BY r.RequestedAt DESC;

/* 3) Do the pending requests have steps? A request with zero steps cannot be approved. */
SELECT
    r.Id                 AS RequestId,
    r.Status,
    COUNT(s.Id)          AS StepCount
  FROM ALERTCORE.ApprovalRequests r
  LEFT JOIN ALERTCORE.ApprovalSteps s ON s.RequestId = r.Id
 WHERE r.EntityType = 'STUDENT_EXTENSION'
   AND r.Status IN ('PENDING', 'IN_PROGRESS')
 GROUP BY r.Id, r.Status
 ORDER BY r.Id DESC;

/* Reference: the STUDENT_EXTENSION approval config and its levels */
SELECT c.Id, c.EntityType, c.TotalLevels, c.IsActive
  FROM ALERTCORE.ApprovalConfigs c
 WHERE c.EntityType IN ('STUDENT_EXTENSION', 'DEFAULT');

SELECT l.ConfigId, l.LevelOrder, l.LevelName, l.RequiredRoleId, l.IsActive
  FROM ALERTCORE.ApprovalLevels l
  JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
 WHERE c.EntityType IN ('STUDENT_EXTENSION', 'DEFAULT')
 ORDER BY l.ConfigId, l.LevelOrder;
