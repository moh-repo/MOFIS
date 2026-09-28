/*
    V050 — report type settings for the new reports
    ================================================

    ReportParamBuilder.build(reportCode, lang) resolves a report's masthead
    titles, footer text and signatory block from MOHECORE.REPORT_TYPE_SETTINGS,
    keyed by REPORT_TYPE_CODE. A template whose code has no row still renders —
    it just renders with an empty title block, which looks like a broken report
    rather than a missing configuration row.

    This adds the rows for the reports covering the modules that had none:
    Wafar, accounting, student entitlements, approvals, the audit trail, and the
    per-attaché summary.

    Re-runnable. Every insert is guarded by NOT EXISTS on the code, so a second
    run reports zero inserts and changes nothing — the same shape as V048/V049.
    Titles match the rows already present (وزارة المالية / نظام إدارة
    البعثات الدراسية), so the new reports carry the same masthead as the other
    forty-nine rather than a variant of it.

    REPORT_TYPE_ID is an identity column, so ids are not stated here.
*/

SET NOCOUNT ON;
GO

PRINT '=== V050: report type settings for new reports ===';
GO

DECLARE @inserted int = 0;
DECLARE @r int;

DECLARE @titleAr1 nvarchar(200) = N'وزارة المالية';
DECLARE @titleEn1 varchar(200)  = 'Ministry of Finance';
DECLARE @titleAr2 nvarchar(200) = N'نظام إدارة البعثات الدراسية';
DECLARE @titleEn2 varchar(200)  = 'Scholarship Management System';

-- One row per new report code. Kept as a table variable so the insert below is
-- written once rather than six times; adding a seventh report is one more row.
DECLARE @new TABLE (
    code    varchar(100),
    nameAr  nvarchar(200),
    nameEn  varchar(200)
);

INSERT INTO @new (code, nameAr, nameEn) VALUES
    ('WAFAR_STATEMENT',          N'كشف الوفر',                    'Wafar Statement'),
    ('ACCOUNTING_TRIAL_BALANCE', N'ميزان المراجعة والمخصصات',      'Trial Balance and Allocations'),
    ('STUDENT_ENTITLEMENTS',     N'مستحقات الطالب',                'Student Entitlements'),
    ('APPROVALS_REQUESTS',       N'الاعتمادات والطلبات',           'Approvals and Requests'),
    ('AUDIT_TRAIL',              N'سجل التدقيق',                   'Audit Trail'),
    ('ATTACHE_SUMMARY',          N'ملخص الملحقية',                 'Attaché Summary');

INSERT INTO MOHECORE.REPORT_TYPE_SETTINGS
    (REPORT_TYPE_CODE, REPORT_TYPE_NAME_AR, REPORT_TYPE_NAME_EN, IS_ACTIVE,
     TITLE_01_AR, TITLE_01_EN, TITLE_02_AR, TITLE_02_EN, SIGNATORY_COUNT)
SELECT n.code, n.nameAr, n.nameEn, 1,
       @titleAr1, @titleEn1, @titleAr2, @titleEn2, 0
FROM @new n
WHERE NOT EXISTS (
    SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS s
    WHERE s.REPORT_TYPE_CODE = n.code
);
SET @r = @@ROWCOUNT;   -- captured immediately: the next statement resets it
SET @inserted = @r;

PRINT '  report types inserted: ' + CAST(@inserted AS varchar(10));
IF @inserted = 0
    PRINT '  (already present — re-run is a no-op)';
GO

-- Verification, printed so a run is self-evidencing.
DECLARE @total int, @missing int;
SELECT @total = COUNT(*) FROM MOHECORE.REPORT_TYPE_SETTINGS;

SELECT @missing = COUNT(*)
FROM (VALUES
        ('WAFAR_STATEMENT'), ('ACCOUNTING_TRIAL_BALANCE'), ('STUDENT_ENTITLEMENTS'),
        ('APPROVALS_REQUESTS'), ('AUDIT_TRAIL'), ('ATTACHE_SUMMARY')
     ) AS expected(code)
WHERE NOT EXISTS (
    SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS s WHERE s.REPORT_TYPE_CODE = expected.code
);

PRINT '  report types total:    ' + CAST(@total AS varchar(10));
IF @missing > 0
    PRINT '  WARNING: ' + CAST(@missing AS varchar(10)) + ' expected code(s) still missing';
ELSE
    PRINT '  all six new codes present';
GO

PRINT '=== V050 complete ===';
GO
