/* ============================================================================
   V079 — The inline bilingual labels the templates still carried.

   Why
   ---
   V077 moved every defaultValueExpression label of the 52 Jasper templates
   into MOHECORE.LU_REPORT_LABEL, and ReportParamBuilder fills every COL_* /
   LBL_* parameter from that table. It missed the labels written inline as a
   ternary in the text-field expression itself —
   $P{IS_RTL} ? "الجهة" : "Provider" — 59 of them across 21 templates
   (column heads of the periodic, statistical and university reports, the
   male/female heads and summary of the major distribution, the six heads of
   the extension statistics). Owner rule (30 Aug 2026): no display text in
   code or templates; every label is a row of this table. After this script
   the templates read those 59 heads through parameters, and the parameters
   resolve here.

   What
   ----
   1. Preconditions: the V077 rows this script reuses exist, and the five
      report codes the override rows name have their REPORT_TYPE_SETTINGS row.
   2. MERGE on CODE, IS_SYSTEM = 1, SORT_ORDER continuing after V077 (268):
      - 32 base rows, one per distinct (Arabic, English) pair not already in
        the table. The wording is byte-for-byte what the ternary printed on
        28 Aug 2026 — a rendered-text diff of every template in both
        languages, before and after the template edit, is identical.
      - 7 per-report override rows named CODE@REPORT_TYPE_CODE, where the
        template's wording shares one language with an existing base row
        but not the other (V077's rule): the budget analysis reads
        COL_ALLOCATED as "المخصص" and COL_DISBURSED as "Spent", the custom
        period report says "تاريخ البداية" for COL_START_DATE, the financial
        audit prints COL_BANK as "البنك / Bank", the scholarship tracking
        heads its entity column "Provider", and the extension statistics
        read LBL_TOTAL as "الإجمالي" and LBL_PENDING as "معلق".
      - Six ternaries matched an existing row in both languages and reuse it
        with no new row: COL_YEAR (7 templates), LBL_QUARTER (3), COL_DATE (2),
        COL_CURRENCY (2), LBL_TOTAL_AMOUNT (2), COL_REMAINING_GROUP (1).
   3. Verification: every code this script seeds is present and active;
      THROW otherwise. The six reused rows are listed for the operator.

   Rollback: DELETE FROM MOHECORE.LU_REPORT_LABEL WHERE SORT_ORDER BETWEEN 269 AND 307;
   Idempotent (a re-run merges 0 rows and does not overwrite an admin's later
   edit of a name). Run with sqlcmd -I -f 65001 -b after V077.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- 1. Preconditions ------------------------------------------------------------
IF COL_LENGTH('MOHECORE.LU_REPORT_LABEL', 'IS_SYSTEM') IS NULL
    THROW 50079, 'V079: LU_REPORT_LABEL lacks the V072 columns — run V072, V076 and V077 first.', 1;
GO

-- Variable concatenation rather than STRING_AGG: the dev server predates SQL Server 2017.
DECLARE @missingLabel NVARCHAR(MAX);
SELECT @missingLabel = ISNULL(@missingLabel + N', ', N'') + c.CODE
FROM (VALUES
    (N'COL_YEAR'), (N'LBL_QUARTER'), (N'COL_DATE'), (N'COL_CURRENCY'),
    (N'LBL_TOTAL_AMOUNT'), (N'COL_REMAINING_GROUP'),
    (N'COL_ALLOCATED'), (N'COL_DISBURSED'), (N'COL_START_DATE'), (N'COL_BANK'),
    (N'COL_ENTITY'), (N'LBL_TOTAL'), (N'LBL_PENDING')
) AS c (CODE)
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.LU_REPORT_LABEL l WHERE l.CODE = c.CODE AND l.IS_DELETED = 0)
ORDER BY c.CODE;
IF @missingLabel IS NOT NULL
BEGIN
    DECLARE @msg1 NVARCHAR(MAX) = N'V079: the V077 rows this script builds on are missing: ' + @missingLabel
        + N'. Run V077 first.';
    THROW 50079, @msg1, 1;
END

DECLARE @missingReport NVARCHAR(MAX);
SELECT @missingReport = ISNULL(@missingReport + N', ', N'') + c.CODE
FROM (VALUES
    (N'BUDGET_ANALYSIS'), (N'CUSTOM_PERIOD_REPORT'), (N'FINANCIAL_AUDIT'),
    (N'SCHOLARSHIP_TRACKING'), (N'STUDENT_EXTENSION')
) AS c (CODE)
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS s WHERE s.REPORT_TYPE_CODE = c.CODE)
ORDER BY c.CODE;
IF @missingReport IS NOT NULL
BEGIN
    DECLARE @msg2 NVARCHAR(MAX) = N'V079: REPORT_TYPE_SETTINGS has no row for: ' + @missingReport
        + N'. The override rows name these reports; seed the settings row first.';
    THROW 50079, @msg2, 1;
END
GO

-- 2. The labels --------------------------------------------------------------
DECLARE @done TABLE (act NVARCHAR(10));
MERGE MOHECORE.LU_REPORT_LABEL AS tgt
USING (VALUES
    (N'COL_ACADEMIC_YEAR', N'العام الدراسي', N'Academic Year', 269),
    (N'COL_ACHIEVEMENT_RATE', N'نسبة الإنجاز', N'Achievement Rate', 270),
    (N'COL_ANNUAL_FEE', N'الرسوم السنوية', N'Annual Fee', 271),
    (N'COL_APPROVAL_RATE', N'نسبة القبول', N'Approval Rate', 272),
    (N'COL_APPROVED', N'مقبول', N'Approved', 273),
    (N'COL_AUDITOR', N'المدقق', N'Auditor', 274),
    (N'COL_BUDGET', N'الميزانية', N'Budget', 275),
    (N'COL_DIMENSION', N'البعد', N'Dimension', 276),
    (N'COL_END_DATE', N'تاريخ النهاية', N'End Date', 277),
    (N'COL_EXPENSES', N'المصروفات', N'Expenses', 278),
    (N'COL_EXTENSION_TYPE', N'نوع التمديد', N'Extension Type', 279),
    (N'COL_FEMALE', N'إناث', N'Female', 280),
    (N'COL_FISCAL_YEAR', N'السنة المالية', N'Fiscal Year', 281),
    (N'COL_GROWTH_RATE', N'معدل النمو', N'Growth Rate', 282),
    (N'COL_MALE', N'ذكور', N'Male', 283),
    (N'COL_METRIC', N'المؤشر', N'Metric', 284),
    (N'COL_METRIC_TYPE', N'نوع المؤشر', N'Metric Type', 285),
    (N'COL_METRIC_VALUE', N'قيمة المؤشر', N'Metric Value', 286),
    (N'COL_MONTH', N'الشهر', N'Month', 287),
    (N'COL_NEW_ENROLLMENTS', N'قيود جديدة', N'New Enrollments', 288),
    (N'COL_PAID', N'المدفوع', N'Paid', 289),
    (N'COL_PERCENTAGE', N'النسبة', N'Percentage', 290),
    (N'COL_PERIOD', N'الفترة', N'Period', 291),
    (N'COL_PREDICTED_VALUE', N'القيمة المتوقعة', N'Predicted Value', 292),
    (N'COL_REJECTED', N'مرفوض', N'Rejected', 293),
    (N'COL_RETENTION_RATE', N'نسبة الاستبقاء', N'Retention Rate', 294),
    (N'COL_SIGNIFICANCE', N'الأهمية', N'Significance', 295),
    (N'COL_TOTAL_PAYMENTS', N'إجمالي المدفوعات', N'Total Payments', 296),
    (N'COL_TREND', N'الاتجاه', N'Trend', 297),
    (N'COL_UTILIZATION_RATE', N'نسبة الاستخدام', N'Utilization Rate', 298),
    (N'COL_VALUE', N'القيمة', N'Value', 299),
    (N'COL_YEARS', N'السنوات', N'Years', 300),
    (N'COL_ALLOCATED@BUDGET_ANALYSIS', N'المخصص', N'Allocated', 301),
    (N'COL_DISBURSED@BUDGET_ANALYSIS', N'المصروف', N'Spent', 302),
    (N'COL_START_DATE@CUSTOM_PERIOD_REPORT', N'تاريخ البداية', N'Start Date', 303),
    (N'COL_BANK@FINANCIAL_AUDIT', N'البنك', N'Bank', 304),
    (N'COL_ENTITY@SCHOLARSHIP_TRACKING', N'الجهة', N'Provider', 305),
    (N'LBL_TOTAL@STUDENT_EXTENSION', N'الإجمالي', N'Total', 306),
    (N'LBL_PENDING@STUDENT_EXTENSION', N'معلق', N'Pending', 307)
) AS src (CODE, NAME_AR, NAME_EN, SORT_ORDER)
   ON tgt.CODE = src.CODE
WHEN MATCHED AND tgt.IS_SYSTEM = 0 THEN UPDATE SET IS_SYSTEM = 1
WHEN NOT MATCHED THEN
    INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED, IS_SYSTEM)
    VALUES (src.CODE, src.NAME_AR, src.NAME_EN, src.SORT_ORDER, 0, 1)
OUTPUT $action INTO @done;
DECLARE @inserted INT, @updated INT;
SELECT @inserted = COUNT(*) FROM @done WHERE act = N'INSERT';
SELECT @updated = COUNT(*) FROM @done WHERE act = N'UPDATE';
PRINT 'V079: rows inserted = ' + CAST(@inserted AS VARCHAR(10)) + ', flagged IS_SYSTEM = ' + CAST(@updated AS VARCHAR(10))
    + ' (first run 39 / 0, re-run 0 / 0)';
GO

-- 3. Verification --------------------------------------------------------------
DECLARE @missing NVARCHAR(MAX);
SELECT @missing = ISNULL(@missing + N', ', N'') + c.CODE
FROM (VALUES
    (N'COL_ACADEMIC_YEAR'), (N'COL_ACHIEVEMENT_RATE'), (N'COL_ANNUAL_FEE'), (N'COL_APPROVAL_RATE'),
    (N'COL_APPROVED'), (N'COL_AUDITOR'), (N'COL_BUDGET'), (N'COL_DIMENSION'), (N'COL_END_DATE'),
    (N'COL_EXPENSES'), (N'COL_EXTENSION_TYPE'), (N'COL_FEMALE'), (N'COL_FISCAL_YEAR'),
    (N'COL_GROWTH_RATE'), (N'COL_MALE'), (N'COL_METRIC'), (N'COL_METRIC_TYPE'), (N'COL_METRIC_VALUE'),
    (N'COL_MONTH'), (N'COL_NEW_ENROLLMENTS'), (N'COL_PAID'), (N'COL_PERCENTAGE'), (N'COL_PERIOD'),
    (N'COL_PREDICTED_VALUE'), (N'COL_REJECTED'), (N'COL_RETENTION_RATE'), (N'COL_SIGNIFICANCE'),
    (N'COL_TOTAL_PAYMENTS'), (N'COL_TREND'), (N'COL_UTILIZATION_RATE'), (N'COL_VALUE'), (N'COL_YEARS'),
    (N'COL_ALLOCATED@BUDGET_ANALYSIS'), (N'COL_DISBURSED@BUDGET_ANALYSIS'),
    (N'COL_START_DATE@CUSTOM_PERIOD_REPORT'), (N'COL_BANK@FINANCIAL_AUDIT'),
    (N'COL_ENTITY@SCHOLARSHIP_TRACKING'), (N'LBL_TOTAL@STUDENT_EXTENSION'), (N'LBL_PENDING@STUDENT_EXTENSION')
) AS c (CODE)
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.LU_REPORT_LABEL l WHERE l.CODE = c.CODE AND l.IS_DELETED = 0 AND l.IS_SYSTEM = 1)
ORDER BY c.CODE;
IF @missing IS NOT NULL
BEGIN
    DECLARE @msg3 NVARCHAR(MAX) = N'V079: seeded labels missing or inactive after the merge: ' + @missing;
    THROW 50079, @msg3, 1;
END

DECLARE @labels INT, @overrides INT, @v079 INT;
SELECT @labels = COUNT(*) FROM MOHECORE.LU_REPORT_LABEL
 WHERE (CODE LIKE 'COL[_]%' OR CODE LIKE 'LBL[_]%') AND CODE NOT LIKE '%@%' AND IS_DELETED = 0;
SELECT @overrides = COUNT(*) FROM MOHECORE.LU_REPORT_LABEL WHERE CODE LIKE '%@%' AND IS_DELETED = 0;
SELECT @v079 = COUNT(*) FROM MOHECORE.LU_REPORT_LABEL WHERE SORT_ORDER BETWEEN 269 AND 307 AND IS_DELETED = 0;
PRINT 'V079: active COL_/LBL_ base labels = ' + CAST(@labels AS VARCHAR(10)) + ' (expected 271 = 239 after V077 + 32)';
PRINT 'V079: per-report override rows = ' + CAST(@overrides AS VARCHAR(10)) + ' (expected 29 = 22 after V077 + 7)';
PRINT 'V079: rows in this script''s SORT_ORDER range = ' + CAST(@v079 AS VARCHAR(10)) + ' (expected 39)';
SELECT CODE, NAME_AR, NAME_EN AS REUSED_BY_V079_TEMPLATES
  FROM MOHECORE.LU_REPORT_LABEL
 WHERE CODE IN (N'COL_YEAR', N'LBL_QUARTER', N'COL_DATE', N'COL_CURRENCY', N'LBL_TOTAL_AMOUNT', N'COL_REMAINING_GROUP')
 ORDER BY CODE;
GO
