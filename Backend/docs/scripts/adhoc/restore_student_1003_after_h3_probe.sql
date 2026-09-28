/*
    restore_student_1003_after_h3_probe.sql — puts back the nine columns the H3
    probe of the 23 Sep 2026 live test cleared on test student 1003.

    The probe edited only the e-mail through /student-add/edit/1003 and approved
    the request. The wizard had never loaded these nine columns, sent them empty,
    and the approval wrote them: AMOUNT_CURRENCY 1→0, the four type ids 1/1/2/2→NULL,
    STUDENT_BIRTHDATE_PLACE '1'→NULL, both passport dates→NULL, the passport
    country 1→NULL. The values below are the ones approval request 21671's change
    list recorded as "from" (report §6). The wizard itself is fixed (it now sends
    only what changed); this is the data.

    Guarded: writes only while every one of the nine columns still holds the
    cleared value, so a row somebody has since edited is left alone. Sets TenantAll
    itself (row-level security would otherwise hide the row). Development data;
    never meant for production, where no such probe ran.

    Run by hand: sqlcmd -I -f 65001 -b -i restore_student_1003_after_h3_probe.sql
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = 1003)
BEGIN
    PRINT 'restore_student_1003: no row with STUDENT_ID 1003 — nothing to do';
    RETURN;
END

IF NOT EXISTS (
    SELECT 1
      FROM MOHECORE.STUDENT_DATA
     WHERE STUDENT_ID = 1003
       AND ISNULL(AMOUNT_CURRENCY, 0) = 0
       AND ISNULL(STUDENT_AGENCY_TYPE_ID, 0) = 0
       AND ISNULL(STUDENT_MINISTRY_TYPE_ID, 0) = 0
       AND ISNULL(STUDENT_STUDY_TYPE_ID, 0) = 0
       AND ISNULL(STUDENT_UNIVERSITY_TYPE_ID, 0) = 0
       AND (STUDENT_BIRTHDATE_PLACE IS NULL OR LTRIM(RTRIM(STUDENT_BIRTHDATE_PLACE)) = N'')
       AND STUDENT_PASSPORT_ISSUE_DATE IS NULL
       AND STUDENT_PASSPORT_EXPIRE_DATE IS NULL
       AND ISNULL(STUDENT_PASSPORT_ISSUE_COUNTRY_ID, 0) = 0)
BEGIN
    PRINT 'restore_student_1003: the nine columns no longer all hold the cleared values — left alone';
    RETURN;
END

UPDATE MOHECORE.STUDENT_DATA
   SET AMOUNT_CURRENCY = 1,
       STUDENT_AGENCY_TYPE_ID = 1,
       STUDENT_MINISTRY_TYPE_ID = 1,
       STUDENT_STUDY_TYPE_ID = 2,
       STUDENT_UNIVERSITY_TYPE_ID = 2,
       STUDENT_BIRTHDATE_PLACE = N'1',
       STUDENT_PASSPORT_ISSUE_DATE = '2026-05-26 22:19:49.557',
       STUDENT_PASSPORT_EXPIRE_DATE = '2026-05-30',
       STUDENT_PASSPORT_ISSUE_COUNTRY_ID = 1
 WHERE STUDENT_ID = 1003;

PRINT 'restore_student_1003: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' row restored (nine columns)';
