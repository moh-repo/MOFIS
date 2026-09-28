/*
 * Complete the REPORT_TYPE_SETTINGS row for the quarterly financial report.
 *
 * The row already carries TITLE_01..03, which is why the printed masthead is
 * correct, but FOOTER_TEXT was blank and SIGNATORY_COUNT was 0 — so the page
 * foot printed only the date and page number, and the signature block the
 * on-screen report shows (cultural advisor / financial advisor) never appeared
 * on the PDF.
 *
 * Everything below is editable afterwards from /settings/report-settings.
 *
 * Idempotent — only fills columns that are still NULL/blank, so a row an
 * administrator has already edited is never overwritten.
 */

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS WHERE REPORT_TYPE_CODE = 'QUARTERLY_FINANCIAL')
BEGIN
    INSERT INTO MOHECORE.REPORT_TYPE_SETTINGS
        (REPORT_TYPE_CODE, REPORT_TYPE_NAME_AR, REPORT_TYPE_NAME_EN, IS_ACTIVE,
         TITLE_01_AR, TITLE_01_EN, TITLE_02_AR, TITLE_02_EN, TITLE_03_AR, TITLE_03_EN,
         FOOTER_TEXT_AR, FOOTER_TEXT_EN, SIGNATORY_COUNT,
         SIGNATORY_NAME_01_AR, SIGNATORY_NAME_01_EN, SIGNATORY_POS_01_AR, SIGNATORY_POS_01_EN,
         SIGNATORY_NAME_02_AR, SIGNATORY_NAME_02_EN, SIGNATORY_POS_02_AR, SIGNATORY_POS_02_EN)
    VALUES
        ('QUARTERLY_FINANCIAL', N'التقرير المالي الربعي', 'Quarterly Financial Report', 1,
         N'وزارة المالية', 'Ministry of Finance',
         N'نظام إدارة البعثات الدراسية', 'Scholarship Management System',
         N'التقرير المالي الربعي للبعثات', 'Quarterly Financial Report for Scholarships',
         N'تقرير داخلي لأغراض المتابعة المالية، غير صالح للصرف ما لم يكن مختوماً ومعتمداً.',
         'Internal report for financial monitoring; not valid for disbursement unless stamped and approved.',
         2,
         N'', '', N'المستشار المالي', 'Financial Advisor',
         N'', '', N'المستشار الثقافي', 'Cultural Advisor');
END
ELSE
BEGIN
    UPDATE MOHECORE.REPORT_TYPE_SETTINGS
       SET FOOTER_TEXT_AR = COALESCE(NULLIF(LTRIM(RTRIM(FOOTER_TEXT_AR)), N''),
               N'تقرير داخلي لأغراض المتابعة المالية، غير صالح للصرف ما لم يكن مختوماً ومعتمداً.'),
           FOOTER_TEXT_EN = COALESCE(NULLIF(LTRIM(RTRIM(FOOTER_TEXT_EN)), ''),
               'Internal report for financial monitoring; not valid for disbursement unless stamped and approved.'),
           SIGNATORY_POS_01_AR = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_01_AR)), N''), N'المستشار المالي'),
           SIGNATORY_POS_01_EN = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_01_EN)), ''),  'Financial Advisor'),
           SIGNATORY_POS_02_AR = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_02_AR)), N''), N'المستشار الثقافي'),
           SIGNATORY_POS_02_EN = COALESCE(NULLIF(LTRIM(RTRIM(SIGNATORY_POS_02_EN)), ''),  'Cultural Advisor'),
           -- Only raise a count that was never configured; never lower an administrator's choice.
           SIGNATORY_COUNT = CASE WHEN COALESCE(SIGNATORY_COUNT, 0) = 0 THEN 2 ELSE SIGNATORY_COUNT END
     WHERE REPORT_TYPE_CODE = 'QUARTERLY_FINANCIAL';
END;

SELECT REPORT_TYPE_CODE, SIGNATORY_COUNT,
       CASE WHEN NULLIF(LTRIM(RTRIM(FOOTER_TEXT_AR)), N'') IS NULL THEN 'BLANK' ELSE 'set' END AS footer_ar,
       CASE WHEN NULLIF(LTRIM(RTRIM(FOOTER_TEXT_EN)), '')  IS NULL THEN 'BLANK' ELSE 'set' END AS footer_en
FROM MOHECORE.REPORT_TYPE_SETTINGS
WHERE REPORT_TYPE_CODE = 'QUARTERLY_FINANCIAL';
