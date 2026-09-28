/* ============================================================================
   V076 — The report labels Java asks for by name.

   Why
   ---
   LU_REPORT_LABEL (V004) held three rows. The code now reads every label it
   needs from that table (codes/ReportLabelCode, verified at startup): the
   "unspecified" word the charts and the enrollment report print, the ordinal
   quarter words the financial statement head writes ("الربع: الثالث"), and
   the enrollment-certificate sentence that StudentDocumentReportService used
   to assemble from Arabic literals. The templates' own 231 parameters follow
   in V077.

   What
   ----
   MERGE on CODE into MOHECORE.LU_REPORT_LABEL, IS_SYSTEM = 1. The certificate
   body is a template: {ministry}, {student}, {university}, {college},
   {major}, {degree}, {year} are replaced at render time.

   Rollback: DELETE FROM MOHECORE.LU_REPORT_LABEL WHERE CODE IN (the codes below).
   Idempotent. Run with sqlcmd -I -f 65001 -b after V074.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF COL_LENGTH('MOHECORE.LU_REPORT_LABEL', 'IS_SYSTEM') IS NULL
    THROW 50076, 'V076: LU_REPORT_LABEL lacks the V072 columns — run V072 first.', 1;
GO

MERGE MOHECORE.LU_REPORT_LABEL AS tgt
USING (VALUES
    (N'LBL_UNSPECIFIED',        N'غير محدد',  N'Unspecified', 10),
    (N'LBL_QUARTER_ORDINAL_1',  N'الأول',     N'First',       11),
    (N'LBL_QUARTER_ORDINAL_2',  N'الثاني',    N'Second',      12),
    (N'LBL_QUARTER_ORDINAL_3',  N'الثالث',    N'Third',       13),
    (N'LBL_QUARTER_ORDINAL_4',  N'الرابع',    N'Fourth',      14),
    (N'TPL_ENROLLMENT_CERT_BODY',
     N'تشهد {ministry} بأن الطالب/ـة {student} مسجل/ة للدراسة في {university} — {college} — تخصص {major} لنيل درجة {degree} للعام الدراسي {year}.',
     N'{ministry} certifies that the student {student} is enrolled at {university} — {college} — major {major} for the degree of {degree} in the academic year {year}.',
     15)
) AS src (CODE, NAME_AR, NAME_EN, SORT_ORDER)
   ON tgt.CODE = src.CODE
WHEN MATCHED THEN UPDATE SET IS_SYSTEM = 1
WHEN NOT MATCHED THEN
    INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED, IS_SYSTEM)
    VALUES (src.CODE, src.NAME_AR, src.NAME_EN, src.SORT_ORDER, 0, 1);
PRINT 'V076: rows merged = ' + CAST(@@ROWCOUNT AS VARCHAR(10));

SELECT CODE, NAME_EN, IS_SYSTEM FROM MOHECORE.LU_REPORT_LABEL ORDER BY SORT_ORDER, ID;
GO
