/*
    V122 — English names the screens printed in Arabic, or as raw codes
    (plan Wave 3: W3-03, W3-07 part, W3-09; QA B3, B11, G7).

    1. MOHECORE.MOHE_QUARTER.QUARTER_NAME_EN held a copy of the Arabic name on
       every row, so an English screen read "الربع الثاني 2024". It is rebuilt from
       LU_QUARTER_NUMBER.NAME_EN and the year — "Quarter 2 2024", the same shape
       MoheQuarterCreateService gives a new quarter — wherever it is empty or is
       the Arabic name again. A name somebody wrote in English is left alone.

    2. UMSCORE.Permissions: the R1a rename (19 Sep) renamed the feature on every
       screen but not the two permission names, which live in the database, so the
       roles and permissions screens still said "طلبات المعلومات" (G7).

    3. ALERTCORE.ApprovalConfigs had no English name: the approval-entity-type code
       list served EntityType itself as the English label, so English screens
       showed STUDENT_FINANCE_WAFAR (B11). EntityTypeEn is added and filled; the
       adapter in CodeListRegistry reads it, falling back to the code.

    Idempotent. THROWs if a row is still wrong afterwards.
    Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

/* ---- 1. Quarter names in English ------------------------------------------------ */

UPDATE q
   SET q.QUARTER_NAME_EN = n.NAME_EN + N' ' + CAST(q.QUARTER_YEAR AS NVARCHAR(4))
  FROM MOHECORE.MOHE_QUARTER q
  JOIN MOHECORE.LU_QUARTER_NUMBER n ON n.CODE = CAST(q.QUARTER_NO AS NVARCHAR(10))
 WHERE q.QUARTER_YEAR IS NOT NULL
   AND n.NAME_EN IS NOT NULL AND LTRIM(RTRIM(n.NAME_EN)) <> N''
   AND (q.QUARTER_NAME_EN IS NULL
        OR LTRIM(RTRIM(q.QUARTER_NAME_EN)) = N''
        OR q.QUARTER_NAME_EN = q.QUARTER_NAME);

/* ---- 2. The renamed information-request permissions ---------------------------- */

UPDATE UMSCORE.Permissions
   SET PermissionNameAr = N'عرض طلبات استيفاء بيانات من الطلاب',
       PermissionName   = N'View information requests to students'
 WHERE PermissionCode = N'STUDENT_INFO_REQUESTS.VIEW';

UPDATE UMSCORE.Permissions
   SET PermissionNameAr = N'إدارة طلبات استيفاء بيانات من الطلاب',
       PermissionName   = N'Manage information requests to students'
 WHERE PermissionCode = N'STUDENT_INFO_REQUESTS.EDIT';

/* ---- 3. Approval entity types in English ---------------------------------------- */

IF COL_LENGTH(N'ALERTCORE.ApprovalConfigs', N'EntityTypeEn') IS NULL
    ALTER TABLE ALERTCORE.ApprovalConfigs ADD EntityTypeEn NVARCHAR(100) NULL;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

UPDATE c
   SET c.EntityTypeEn = v.NameEn
  FROM ALERTCORE.ApprovalConfigs c
  JOIN (VALUES
        (N'STUDENT_DATA',            N'Student data'),
        (N'STUDENT_FINANCE',         N'Student finance'),
        (N'STUDENT_EXTENSION',       N'Study extension'),
        (N'LOOKUP_TABLE',            N'System coding'),
        (N'DEFAULT',                 N'Default'),
        (N'STUDY_UNIVERSITY_FEE',    N'University fee'),
        (N'STUDENT_DOCUMENT',        N'Student documents'),
        (N'STUDENT_TRAVEL_TICKET',   N'Travel tickets'),
        (N'STUDENT_FINANCE_WAFAR',   N'Student wafar'),
        (N'STUDENT_LOAN',            N'Student loan'),
        (N'STUDENT_BOOKS_ALLOWANCE', N'Books allowance'),
        (N'STUDY_FIN_RECEIVED_WHOM', N'Payment recipient'),
        (N'USER_PRIVILEGE_CHANGE',   N'User privilege change'),
        (N'SYSTEM_RESTORE',          N'Database restore'),
        (N'WAFAR_CORRECTION',        N'Wafar correction')
       ) v (EntityType, NameEn) ON v.EntityType = c.EntityType
 WHERE c.EntityTypeEn IS NULL OR LTRIM(RTRIM(c.EntityTypeEn)) = N'';

/* ---- checks ---------------------------------------------------------------------- */

IF EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER
            WHERE QUARTER_YEAR IS NOT NULL AND QUARTER_NO BETWEEN 1 AND 4
              AND (QUARTER_NAME_EN IS NULL OR QUARTER_NAME_EN = QUARTER_NAME))
    THROW 51220, 'V122: a quarter still has no English name', 1;

IF EXISTS (SELECT 1 FROM UMSCORE.Permissions
            WHERE PermissionCode IN (N'STUDENT_INFO_REQUESTS.VIEW', N'STUDENT_INFO_REQUESTS.EDIT')
              AND PermissionNameAr NOT LIKE N'%استيفاء%')
    THROW 51221, 'V122: an information-request permission keeps its old Arabic name', 1;

IF EXISTS (SELECT 1 FROM ALERTCORE.ApprovalConfigs
            WHERE EntityType IN (N'STUDENT_DATA', N'STUDENT_FINANCE', N'STUDENT_FINANCE_WAFAR')
              AND (EntityTypeEn IS NULL OR EntityTypeEn = EntityType))
    THROW 51222, 'V122: an approval entity type has no English name', 1;
