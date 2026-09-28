/*
    V143 — three report cards are shown to the people who may read their data.

    FIX-PLAN M6, decision D18-a (live QA, 25 Sep 2026). A report card on the reports
    hub is shown by its category code (REPORT_ACADEMIC, REPORT_OTHER), but three of
    the reports read data behind another permission, so a Reports Viewer was offered
    the card and then refused every row:

        REP_STUDENT_LIST_BY_CRITERIA  /reports/student/list-detailed   data: STUDENTS (student records)
        REP_OPERATIONS_AUDIT_TRAIL    /reports/operations/audit-trail  data: AUDIT
        REP_OPERATIONS_APPROVALS      /reports/operations/approvals    data: APPROVALS

    The card's PermissionCodes (any one held shows it) now name the codes the data
    endpoint accepts. The hub the card sits in still needs its category code, and the
    routes check the same codes (reports-routing.module.ts), so the card, the page and
    the data agree. The catalogue is only read by the screen; the server's own gates
    are unchanged.

    Only a row that still carries the seeded value (PermissionCodes NULL) is changed:
    a code an administrator set on the catalogue screen is kept. Strictly idempotent:
    a second run changes nothing.

    On the development database (read 26 Sep 2026) the three rows are active and have
    PermissionCodes NULL, so each is updated once.

    Rollback
    --------
        UPDATE UMSCORE.UMS_Reports SET PermissionCodes = NULL, UpdatedAt = SYSUTCDATETIME()
         WHERE ReportCode IN (N'REP_STUDENT_LIST_BY_CRITERIA', N'REP_OPERATIONS_AUDIT_TRAIL', N'REP_OPERATIONS_APPROVALS');
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

UPDATE r
   SET r.PermissionCodes = v.Codes,
       r.UpdatedAt = SYSUTCDATETIME()
  FROM UMSCORE.UMS_Reports r
  JOIN (VALUES
        (N'REP_STUDENT_LIST_BY_CRITERIA', N'STUDENTS,STUDENTS_LIST,UMS_ADMIN'),
        (N'REP_OPERATIONS_AUDIT_TRAIL',   N'AUDIT,UMS_ADMIN'),
        (N'REP_OPERATIONS_APPROVALS',     N'APPROVALS,APP_PENDING,UMS_ADMIN')
       ) AS v (ReportCode, Codes) ON v.ReportCode = r.ReportCode
 WHERE r.PermissionCodes IS NULL;

PRINT CONCAT('V143: report cards updated: ', @@ROWCOUNT);
