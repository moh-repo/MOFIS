/*
    V123 — English names for the information-request kinds (plan W3-04, QA SP-007, D3).

    MOHECORE.STUDENT_INFO_REQUEST_KIND.DISPLAY_NAME is the English column, and it held
    the Arabic name on every row, so the staff filter and the portal (which now names
    the kind through the info-request-kind code list) showed Arabic in English. Rows
    whose English name is empty or repeats the Arabic one get the name below; a kind
    somebody named in English is left alone.

    Idempotent. THROWs if one of the five kinds still has no English name.
    Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

UPDATE k
   SET k.DISPLAY_NAME = v.NameEn
  FROM MOHECORE.STUDENT_INFO_REQUEST_KIND k
  JOIN (VALUES
        (N'STATUS_CERT',       N'Study status certificate'),
        (N'BANK_COVER_LETTER', N'Bank introduction letter'),
        (N'FIN_SUMMARY',       N'Student financial summary'),
        (N'TRANSCRIPT',        N'Official transcript'),
        (N'OTHER',             N'Information request — other')
       ) v (KindCode, NameEn) ON v.KindCode = k.KIND_CODE
 WHERE k.DISPLAY_NAME IS NULL
    OR LTRIM(RTRIM(k.DISPLAY_NAME)) = N''
    OR k.DISPLAY_NAME = k.DISPLAY_NAME_AR;

IF EXISTS (SELECT 1 FROM MOHECORE.STUDENT_INFO_REQUEST_KIND
            WHERE KIND_CODE IN (N'STATUS_CERT', N'BANK_COVER_LETTER', N'FIN_SUMMARY', N'TRANSCRIPT', N'OTHER')
              AND (DISPLAY_NAME IS NULL OR DISPLAY_NAME = DISPLAY_NAME_AR))
    THROW 51230, 'V123: an information-request kind still has no English name', 1;
