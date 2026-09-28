/*
    V134 — the ACTIVE study status is "نشط", not "ثابت" (QA I18N-001, DSH-004).

    MOHECORE.STUDENT_STUDY_STATUS row 1 (CODE = ACTIVE, English "Active") was seeded
    with the Arabic "ثابت", which reads as "static" or "fixed" — so the dashboard's
    students-by-status donut, every status filter and every profile named the 981
    active students that way in Arabic. The testers asked for "نشط", which is what
    the sibling MOHECORE.STUDY_STATUS list already calls its own Active row and what
    the rest of the Arabic UI uses for "active". Decided 23 Sep 2026.

    Safe to rename: the import resolver (ScholarshipEtlService) matches Arabic text
    only for degree, major, agency type, ministry type and current level, never for
    the study status, so no sheet resolves through this word. Students point at the
    row by id and are untouched. The Arabic dashboard labels that were written to
    match the old word ("الطلاب الثابتون") change with it in the frontend.

    Idempotent: matched by CODE and only while the old word is still there.
    Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

UPDATE MOHECORE.STUDENT_STUDY_STATUS
   SET STUDENT_STUDY_STATUS_NAME = N'نشط'
 WHERE CODE = N'ACTIVE'
   AND LTRIM(RTRIM(STUDENT_STUDY_STATUS_NAME)) = N'ثابت';

PRINT 'V134: ACTIVE study status renamed: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_STUDY_STATUS WHERE CODE = N'ACTIVE' AND STUDENT_STUDY_STATUS_NAME = N'نشط')
    THROW 51340, 'V134: the ACTIVE study status is not named نشط', 1;
