/*
 * Seed REPORT_TYPE_SETTINGS for the chart/visualisation report ("تقرير الرسوم البيانية").
 *
 * ReportParamBuilder.build throws when a report code has no row here — there is no
 * Java default for a masthead — so the eight visualisation pages cannot produce a
 * PDF at all until this row exists. It backs chart-report.jrxml, which serves the
 * bar, pie, line and hierarchy chart pages, the geographic map, the comprehensive
 * dashboards, the student graph and the student tracer.
 *
 * REPORT_TYPE_NAME_* is deliberately generic: one template serves eight pages, and
 * each page sends its own heading, which ChartReportController puts over
 * REPORT_TITLE. The name here is what the settings screen lists the row under.
 *
 * SIGNATORY_COUNT is 0. A chart report is a working document — someone looking at
 * enrolment by country — not an instrument anybody countersigns, and a signature
 * block on it would invite it to be treated as one.
 *
 * Idempotent. The UPDATE branch fills only columns that are still NULL or blank, so
 * a row an administrator has already edited in /settings/report-settings is never
 * overwritten.
 */

IF NOT EXISTS (SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS WHERE REPORT_TYPE_CODE = 'CHART_REPORT')
BEGIN
    INSERT INTO MOHECORE.REPORT_TYPE_SETTINGS
        (REPORT_TYPE_CODE, REPORT_TYPE_NAME_AR, REPORT_TYPE_NAME_EN, IS_ACTIVE,
         TITLE_01_AR, TITLE_01_EN, TITLE_02_AR, TITLE_02_EN, TITLE_03_AR, TITLE_03_EN,
         FOOTER_TEXT_AR, FOOTER_TEXT_EN, SIGNATORY_COUNT)
    VALUES
        ('CHART_REPORT', N'تقرير الرسوم البيانية', 'Charts and Visualizations Report', 1,
         N'الجمهورية اليمنية', 'Republic of Yemen',
         N'وزارة المالية', 'Ministry of Finance',
         N'إدارة البعثات والعلاقات الثقافية', 'Department of Scholarships and Cultural Relations',
         N'الأرقام الواردة في هذا التقرير مستخرجة من النظام بتاريخ الطباعة الموضح أدناه.',
         'The figures in this report were extracted from the system on the print date shown below.',
         0);
END
ELSE
BEGIN
    UPDATE MOHECORE.REPORT_TYPE_SETTINGS
       SET REPORT_TYPE_NAME_AR = COALESCE(NULLIF(LTRIM(RTRIM(REPORT_TYPE_NAME_AR)), N''), N'تقرير الرسوم البيانية'),
           REPORT_TYPE_NAME_EN = COALESCE(NULLIF(LTRIM(RTRIM(REPORT_TYPE_NAME_EN)),  ''),  'Charts and Visualizations Report'),
           TITLE_01_AR    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_01_AR)),    N''), N'الجمهورية اليمنية'),
           TITLE_01_EN    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_01_EN)),     ''),  'Republic of Yemen'),
           TITLE_02_AR    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_02_AR)),    N''), N'وزارة المالية'),
           TITLE_02_EN    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_02_EN)),     ''),  'Ministry of Finance'),
           TITLE_03_AR    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_03_AR)),    N''), N'إدارة البعثات والعلاقات الثقافية'),
           TITLE_03_EN    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_03_EN)),     ''),  'Department of Scholarships and Cultural Relations'),
           FOOTER_TEXT_AR = COALESCE(NULLIF(LTRIM(RTRIM(FOOTER_TEXT_AR)), N''), N'الأرقام الواردة في هذا التقرير مستخرجة من النظام بتاريخ الطباعة الموضح أدناه.'),
           FOOTER_TEXT_EN = COALESCE(NULLIF(LTRIM(RTRIM(FOOTER_TEXT_EN)),  ''),  'The figures in this report were extracted from the system on the print date shown below.'),
           IS_ACTIVE      = COALESCE(IS_ACTIVE, 1)
     WHERE REPORT_TYPE_CODE = 'CHART_REPORT';
END;
