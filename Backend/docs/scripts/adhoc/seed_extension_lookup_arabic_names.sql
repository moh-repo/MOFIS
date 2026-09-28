/* ============================================================================
   Populate Arabic names for the Student Extension lookup tables
   ----------------------------------------------------------------------------
   Tables : MOHECORE.EXTENSION_TYPE  (EXTENSION_TYPE_NAME_AR)
            MOHECORE.EXTENSION_STATUS(EXTENSION_STATUS_NAME_AR)

   How it works
     * Each row below maps a KEY (an English name OR a code, case/space
       insensitive) to its Arabic name.
     * The UPDATE matches a lookup row when the KEY equals either its CODE or
       its English name, so it works no matter which convention your data uses.
     * Only rows whose Arabic name is currently NULL/blank are touched, so the
       script is safe to re-run and will not overwrite Arabic names you edited.

   Before running
     * Open this file in SSMS as UTF-8 (it is saved UTF-8 with BOM) so the
       Arabic literals are read correctly.
     * Make sure the query window is connected to the correct database, or add a
       USE [YourDatabaseName]; line at the top.
     * The *_NAME_AR columns must be NVARCHAR (they hold Unicode). The N'...'
       prefixes below keep the literals Unicode.

   The final SELECT lists any rows still missing an Arabic name so you can add
   them to the mapping and re-run.
   ============================================================================ */

SET NOCOUNT ON;

/* ---------------------------------------------------------------------------
   1) EXTENSION TYPES
   --------------------------------------------------------------------------- */
DECLARE @TypeMap TABLE (MatchKey NVARCHAR(200) NOT NULL, NameAr NVARCHAR(200) NOT NULL);

INSERT INTO @TypeMap (MatchKey, NameAr) VALUES
    (N'ACADEMIC',                 N'تمديد أكاديمي'),
    (N'ACADEMIC EXTENSION',       N'تمديد أكاديمي'),
    (N'ACADEMIC_EXTENSION',       N'تمديد أكاديمي'),
    (N'FINANCIAL',                N'تمديد مالي'),
    (N'FINANCIAL EXTENSION',      N'تمديد مالي'),
    (N'FINANCIAL_EXTENSION',      N'تمديد مالي'),
    (N'ADMINISTRATIVE',           N'تمديد إداري'),
    (N'ADMINISTRATIVE EXTENSION', N'تمديد إداري'),
    (N'ADMINISTRATIVE_EXTENSION', N'تمديد إداري'),
    (N'SPECIAL',                  N'ظرف خاص'),
    (N'SPECIAL CIRCUMSTANCE',     N'ظرف خاص'),
    (N'SPECIAL_CIRCUMSTANCE',     N'ظرف خاص'),
    (N'MEDICAL',                  N'تمديد طبي'),
    (N'MEDICAL EXTENSION',        N'تمديد طبي'),
    (N'MATERNITY',                N'تمديد أمومة'),
    (N'MATERNITY EXTENSION',      N'تمديد أمومة'),
    (N'RESEARCH',                 N'تمديد بحثي'),
    (N'RESEARCH EXTENSION',       N'تمديد بحثي'),
    (N'PERSONAL',                 N'ظرف شخصي'),
    (N'EMERGENCY',                N'تمديد طارئ');

UPDATE t
   SET t.EXTENSION_TYPE_NAME_AR = m.NameAr
  FROM MOHECORE.EXTENSION_TYPE AS t
  JOIN @TypeMap AS m
    ON UPPER(LTRIM(RTRIM(t.EXTENSION_TYPE_CODE)))    = m.MatchKey
    OR UPPER(LTRIM(RTRIM(t.EXTENSION_TYPE_NAME_EN))) = m.MatchKey
 WHERE t.EXTENSION_TYPE_NAME_AR IS NULL
    OR LTRIM(RTRIM(t.EXTENSION_TYPE_NAME_AR)) = N'';

PRINT 'EXTENSION_TYPE rows updated: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

/* ---------------------------------------------------------------------------
   2) EXTENSION STATUSES
   --------------------------------------------------------------------------- */
DECLARE @StatusMap TABLE (MatchKey NVARCHAR(200) NOT NULL, NameAr NVARCHAR(200) NOT NULL);

INSERT INTO @StatusMap (MatchKey, NameAr) VALUES
    (N'PENDING',       N'قيد الانتظار'),
    (N'APPROVED',      N'مُعتمد'),
    (N'REJECTED',      N'مرفوض'),
    (N'IN PROGRESS',   N'قيد المعالجة'),
    (N'IN_PROGRESS',   N'قيد المعالجة'),
    (N'INPROGRESS',    N'قيد المعالجة'),
    (N'CANCELLED',     N'ملغى'),
    (N'CANCELED',      N'ملغى'),
    (N'SUBMITTED',     N'مُقدَّم'),
    (N'COMPLETED',     N'مكتمل'),
    (N'UNDER REVIEW',  N'قيد المراجعة'),
    (N'UNDER_REVIEW',  N'قيد المراجعة'),
    (N'ACTIVE',        N'نشط'),
    (N'EXPIRED',       N'منتهٍ'),
    (N'DRAFT',         N'مسودة'),
    (N'CLOSED',        N'مغلق'),
    (N'ON HOLD',       N'معلّق'),
    (N'ON_HOLD',       N'معلّق');

UPDATE s
   SET s.EXTENSION_STATUS_NAME_AR = m.NameAr
  FROM MOHECORE.EXTENSION_STATUS AS s
  JOIN @StatusMap AS m
    ON UPPER(LTRIM(RTRIM(s.EXTENSION_STATUS_CODE)))    = m.MatchKey
    OR UPPER(LTRIM(RTRIM(s.EXTENSION_STATUS_NAME_EN))) = m.MatchKey
 WHERE s.EXTENSION_STATUS_NAME_AR IS NULL
    OR LTRIM(RTRIM(s.EXTENSION_STATUS_NAME_AR)) = N'';

PRINT 'EXTENSION_STATUS rows updated: ' + CAST(@@ROWCOUNT AS NVARCHAR(10));

/* ---------------------------------------------------------------------------
   3) VERIFY — review results and spot anything still missing an Arabic name
   --------------------------------------------------------------------------- */
SELECT EXTENSION_TYPE_CODE, EXTENSION_TYPE_NAME_EN, EXTENSION_TYPE_NAME_AR
  FROM MOHECORE.EXTENSION_TYPE
 ORDER BY DISPLAY_ORDER, EXTENSION_TYPE_NAME_EN;

SELECT EXTENSION_STATUS_CODE, EXTENSION_STATUS_NAME_EN, EXTENSION_STATUS_NAME_AR
  FROM MOHECORE.EXTENSION_STATUS
 ORDER BY DISPLAY_ORDER, EXTENSION_STATUS_NAME_EN;

-- Rows still without an Arabic name (add them to the maps above and re-run):
SELECT 'EXTENSION_TYPE' AS TableName, EXTENSION_TYPE_CODE AS Code, EXTENSION_TYPE_NAME_EN AS NameEn
  FROM MOHECORE.EXTENSION_TYPE
 WHERE EXTENSION_TYPE_NAME_AR IS NULL OR LTRIM(RTRIM(EXTENSION_TYPE_NAME_AR)) = N''
UNION ALL
SELECT 'EXTENSION_STATUS', EXTENSION_STATUS_CODE, EXTENSION_STATUS_NAME_EN
  FROM MOHECORE.EXTENSION_STATUS
 WHERE EXTENSION_STATUS_NAME_AR IS NULL OR LTRIM(RTRIM(EXTENSION_STATUS_NAME_AR)) = N'';
