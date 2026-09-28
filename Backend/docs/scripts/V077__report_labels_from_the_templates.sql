/* ============================================================================
   V077 — The report labels the templates declare.

   Why
   ---
   The 52 Jasper templates under Backend/src/main/resources/reports declare
   231 distinct COL_* / LBL_* parameters (column heads, box captions, the
   statutory notes of the quarterly financial assistance statement, the
   wording of the payment authorisation voucher). Until now their text lived
   in three places: ~300 lines of bilingual maps in ReportParamBuilder, a
   report.col.* copy in messages*.properties that no code read, and the
   defaultValueExpression of every template. The owner's decision (30 Aug
   2026): the database owns the catalogue. After this script the builder
   fills every template parameter from LU_REPORT_LABEL in one cached read,
   the Java maps and the bundle keys are deleted, and the template defaults
   are blanked.

   What
   ----
   1. NAME_AR / NAME_EN widened to NVARCHAR(1000). The four statutory notes
      are paragraphs (the longest is 291 characters); nothing else changes
      on the table.
   2. A precondition: every report code ReportParamBuilder is called with
      must have a MOHECORE.REPORT_TYPE_SETTINGS row, because the builder no
      longer carries Java defaults for a masthead or a title — a missing row
      is an error at render time, and this script names it before it is one.
   3. MERGE on CODE, IS_SYSTEM = 1, SORT_ORDER continuing after V076:
      - 228 base rows, one per template parameter not already in the
        table (LBL_GENERATED, LBL_PAGE and LBL_OF are V004 rows and keep
        their names). The wording is byte-for-byte what the builder's maps
        rendered on 28 Aug 2026; where a parameter had no Java text the
        template's own default was taken (COL_REPORT_NAME took the
        EXPORT_MANAGER translation over scheduled-reports' English default);
        an empty English text (LBL_YEAR_SUFFIX) stays empty on purpose.
      - 22 per-report override rows named CODE@REPORT_TYPE_CODE. One
        parameter name means different things on different forms
        (COL_STATUS is the study status on a student list and the approval
        status on the approvals queue; the statutory forms shorten "الدرجة
        العلمية" to "الدرجة"). The builder puts the base row first and then,
        for the report it is building, every row whose suffix is that
        report code — so each form prints exactly what it printed before.
        Delete an override row and the base wording applies.
      - LBL_APPROVALS_STATE_PENDING / _COMPLETED (the two buckets of the
        approvals report) and LBL_KIND_LOAN (the entitlements report names
        books and travel by FINCORE.FEE_TYPE; loans have no fee type).
   4. Verification: the count of template labels, and any row whose Arabic
      and English are both empty.

   Rollback: DELETE FROM MOHECORE.LU_REPORT_LABEL WHERE SORT_ORDER BETWEEN 16 AND 268;
   the widened columns can stay. Idempotent (a re-run merges 0 rows and
   does not overwrite an admin's later edit of a name). Run with
   sqlcmd -I -f 65001 -b after V076.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF COL_LENGTH('MOHECORE.LU_REPORT_LABEL', 'IS_SYSTEM') IS NULL
    THROW 50077, 'V077: LU_REPORT_LABEL lacks the V072 columns — run V072 (and V076) first.', 1;
IF NOT EXISTS (SELECT 1 FROM MOHECORE.LU_REPORT_LABEL WHERE CODE = N'TPL_ENROLLMENT_CERT_BODY')
    THROW 50077, 'V077: the V076 rows are missing — run V076 first.', 1;
GO

-- 1. Paragraph-length labels ------------------------------------------------
IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'MOHECORE' AND TABLE_NAME = 'LU_REPORT_LABEL' AND COLUMN_NAME = 'NAME_AR') < 1000
    ALTER TABLE MOHECORE.LU_REPORT_LABEL ALTER COLUMN NAME_AR NVARCHAR(1000) NOT NULL;
IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'MOHECORE' AND TABLE_NAME = 'LU_REPORT_LABEL' AND COLUMN_NAME = 'NAME_EN') < 1000
    ALTER TABLE MOHECORE.LU_REPORT_LABEL ALTER COLUMN NAME_EN NVARCHAR(1000) NOT NULL;
GO

-- 2. Every report the builder renders has its settings row -------------------
-- Variable concatenation rather than STRING_AGG: the dev server predates SQL Server 2017.
DECLARE @missing NVARCHAR(MAX);
SELECT @missing = ISNULL(@missing + N', ', N'') + c.CODE
FROM (VALUES
    (N'ACADEMIC_PERFORMANCE'),
    (N'ACADEMIC_YEAR_REPORT'),
    (N'ACCOUNTING_TRIAL_BALANCE'),
    (N'ANNUAL_REPORT'),
    (N'APPROVALS_REQUESTS'),
    (N'ATTACHE_SUMMARY'),
    (N'AUDIT_TRAIL'),
    (N'BANK_TRANSACTION'),
    (N'BUDGET_ANALYSIS'),
    (N'COMPARATIVE_STATISTICS'),
    (N'CUSTOM_FILTER_REPORT'),
    (N'CUSTOM_PERIOD_REPORT'),
    (N'DATA_VISUALIZATION'),
    (N'DISTRIBUTION_STATISTICS'),
    (N'ENROLLMENT_CERTIFICATE'),
    (N'ENROLLMENT_STATISTICS'),
    (N'EXPORT_MANAGER'),
    (N'FEE_STRUCTURE'),
    (N'FINANCIAL_AUDIT'),
    (N'FINANCIAL_DATA_FORM'),
    (N'FINANCIAL_DATA_FORM_V2'),
    (N'FISCAL_YEAR_REPORT'),
    (N'GROWTH_ANALYSIS'),
    (N'INDIVIDUAL_STUDENT'),
    (N'MAJOR_DISTRIBUTION'),
    (N'MONTHLY_REPORT'),
    (N'PAYMENT_AUTH'),
    (N'PAYMENT_AUTH_FORM'),
    (N'PAYMENT_SUMMARY'),
    (N'PERFORMANCE_METRICS'),
    (N'PREDICTIVE_ANALYTICS'),
    (N'PROGRAM_CATALOG'),
    (N'QUARTERLY_FINANCIAL'),
    (N'QUARTERLY_REPORT'),
    (N'QUERY_BUILDER'),
    (N'REPORT_TEMPLATES'),
    (N'SCHEDULED_REPORTS'),
    (N'SCHOLARSHIP_TRACKING'),
    (N'STUDENT_DEMOGRAPHICS'),
    (N'STUDENT_DIST_UNIVERSITY'),
    (N'STUDENT_ENTITLEMENTS'),
    (N'STUDENT_EXTENSION'),
    (N'STUDENT_FINANCIAL_STATEMENT'),
    (N'STUDENT_ID_CARD'),
    (N'STUDENT_LIST'),
    (N'STUDENT_REGISTRATION_FORM'),
    (N'STUDENT_STATUS'),
    (N'TREND_ANALYSIS'),
    (N'UNIVERSITY_AGREEMENTS'),
    (N'UNIVERSITY_PARTNERSHIPS'),
    (N'UNIVERSITY_PERFORMANCE'),
    (N'WAFAR_STATEMENT')
) AS c (CODE)
WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS s WHERE s.REPORT_TYPE_CODE = c.CODE)
ORDER BY c.CODE;
IF @missing IS NOT NULL
BEGIN
    DECLARE @msg NVARCHAR(MAX) = N'V077: REPORT_TYPE_SETTINGS has no row for: ' + @missing
        + N'. ReportParamBuilder has no Java default for a masthead any more; seed the row first.';
    THROW 50077, @msg, 1;
END
GO

-- 3. The labels --------------------------------------------------------------
DECLARE @done TABLE (act NVARCHAR(10));
MERGE MOHECORE.LU_REPORT_LABEL AS tgt
USING (VALUES
    (N'COL_ACTIVE', N'النشطون', N'Active', 16),
    (N'COL_AGE', N'العمر', N'Age', 17),
    (N'COL_ALLOCATED', N'إجمالي المخصصات', N'Allocated', 18),
    (N'COL_ALLOC_COUNT', N'عدد المخصصات', N'Allocations', 19),
    (N'COL_AMOUNT', N'المبلغ', N'Amount', 20),
    (N'COL_AMOUNT_RULES', N'قواعد المبالغ', N'Amount rules', 21),
    (N'COL_ARABIC_NAME', N'الاسم بالعربية', N'Arabic Name', 22),
    (N'COL_ATTACHE', N'الملحقية', N'Attaché', 23),
    (N'COL_AVAILABLE', N'المتاح', N'Available', 24),
    (N'COL_BANK', N'البنك / الفرع', N'Bank / Branch', 25),
    (N'COL_BANKS', N'البنوك', N'Banks', 26),
    (N'COL_BENEFICIARY', N'المستفيد', N'Beneficiary', 27),
    (N'COL_BIRTH_DATE', N'تاريخ الميلاد', N'Birth Date', 28),
    (N'COL_BRANCH', N'الفرع', N'Branch', 29),
    (N'COL_BRANCHES', N'الفروع', N'Branches', 30),
    (N'COL_CATEGORY', N'التصنيف', N'Category', 31),
    (N'COL_CHANGED_BY', N'بواسطة', N'Changed by', 32),
    (N'COL_CHART_NAME', N'اسم الرسم البياني', N'Chart Name', 33),
    (N'COL_CHART_TYPE', N'النوع', N'Type', 34),
    (N'COL_CHECK_NO', N'رقم الشيك', N'Check No.', 35),
    (N'COL_COLUMN', N'العمود', N'Column', 36),
    (N'COL_COLUMNS', N'الحقول المعدّلة', N'Columns changed', 37),
    (N'COL_COUNT', N'العدد', N'Count', 38),
    (N'COL_COUNTRY', N'البلد', N'Country', 39),
    (N'COL_CURRENCY', N'العملة', N'Currency', 40),
    (N'COL_DATE', N'التاريخ', N'Date', 41),
    (N'COL_DECISION_NO', N'رقم قرار الإيفاد', N'Decision No.', 42),
    (N'COL_DECISION_YEAR', N'عام الإيفاد', N'Delegation Year', 43),
    (N'COL_DEGREE', N'الدرجة العلمية', N'Degree', 44),
    (N'COL_DEGREE_LEVEL', N'مستوى الدرجة', N'Degree Level', 45),
    (N'COL_DETAIL', N'التفاصيل', N'Detail', 46),
    (N'COL_DISBURSED', N'المصروف', N'Disbursed', 47),
    (N'COL_DISBURSED_AMOUNT', N'المبلغ المصروف', N'Disbursed Amount', 48),
    (N'COL_DISBURSED_COUNT', N'عدد المصروفين', N'Disbursed #', 49),
    (N'COL_DISBURSED_GROUP', N'المنصرف', N'Disbursed', 50),
    (N'COL_DISTRIBUTION_PCT', N'نسبة التوزيع', N'Distribution %', 51),
    (N'COL_ENTITY', N'الجهة', N'Entity', 52),
    (N'COL_ENTITY_ID', N'رقم السجل', N'Record', 53),
    (N'COL_ENTITY_TYPE', N'الجهة', N'Entity', 54),
    (N'COL_FIN_COUNT', N'سجلات مالية', N'Finance rows', 55),
    (N'COL_FIN_TOTAL', N'إجمالي المستلم', N'Finance total', 56),
    (N'COL_FIRST_RECEIPT', N'أول مصرف', N'First Payment', 57),
    (N'COL_FORMAT', N'التنسيق', N'Format', 58),
    (N'COL_FORM_NO', N'رقم الاستمارة', N'Form No.', 59),
    (N'COL_FREQUENCY', N'التكرار', N'Frequency', 60),
    (N'COL_GENDER', N'الجنس', N'Gender', 61),
    (N'COL_IP', N'عنوان IP', N'IP address', 62),
    (N'COL_KIND', N'نوع المستحق', N'Kind', 63),
    (N'COL_LAST_EXPORT', N'آخر تصدير', N'Last Export', 64),
    (N'COL_LEVEL', N'المستوى', N'Level', 65),
    (N'COL_MAJOR', N'التخصص', N'Major', 66),
    (N'COL_NET_AMOUNT', N'المبلغ الصافي', N'Net Amount', 67),
    (N'COL_NEXT_RUN', N'التشغيل القادم', N'Next Run', 68),
    (N'COL_NOTES', N'ملاحظات', N'Notes', 69),
    (N'COL_OPENING', N'الرصيد الافتتاحي', N'Opening', 70),
    (N'COL_OPERATION', N'العملية', N'Operation', 71),
    (N'COL_PASSPORT', N'رقم جواز السفر', N'Passport', 72),
    (N'COL_PAYEE', N'المستفيد', N'Payee', 73),
    (N'COL_PROGRAM', N'البرنامج', N'Program', 74),
    (N'COL_QUARTER', N'الفصل', N'Quarter', 75),
    (N'COL_RECEIVED_AMOUNT', N'المبلغ المستلم', N'Received Amount', 76),
    (N'COL_RECEIVED_COUNT', N'عدد المستلمين', N'Received #', 77),
    (N'COL_RECEIVED_GROUP', N'الوارد', N'Received', 78),
    (N'COL_RECORD', N'السجل', N'Record', 79),
    (N'COL_REFERENCE', N'المرجع', N'Reference', 80),
    (N'COL_REMAINING_AMOUNT', N'المبلغ المتبقي', N'Remaining Amount', 81),
    (N'COL_REMAINING_COUNT', N'عدد المتبقي', N'Remaining #', 82),
    (N'COL_REMAINING_GROUP', N'المتبقي', N'Remaining', 83),
    (N'COL_REPORT_NAME', N'اسم التقرير', N'Report Name', 84),
    (N'COL_REPORT_TYPE', N'نوع التقرير', N'Report Type', 85),
    (N'COL_REQUESTED_AT', N'تاريخ الطلب', N'Requested at', 86),
    (N'COL_REQUESTED_BY', N'مقدّم الطلب', N'Requested by', 87),
    (N'COL_REQ_TYPE', N'نوع الطلب', N'Request type', 88),
    (N'COL_RETURNED', N'المرتجع', N'Returned', 89),
    (N'COL_ROW_NO', N'م', N'#', 90),
    (N'COL_STAFF', N'الموظفون', N'Staff', 91),
    (N'COL_START_DATE', N'تاريخ البدء', N'Start Date', 92),
    (N'COL_STATUS', N'الحالة الدراسية', N'Status', 93),
    (N'COL_STUDENTS', N'الطلاب', N'Students', 94),
    (N'COL_STUDENT_ID', N'رقم الطالب', N'Student ID', 95),
    (N'COL_STUDENT_NAME', N'اسم الطالب', N'Student Name', 96),
    (N'COL_STUDENT_SIGNATURE', N'توقيع الطالب', N'Student Signature', 97),
    (N'COL_STUDY_TYPE', N'نوع الدراسة', N'Study Type', 98),
    (N'COL_STUDY_YEAR', N'السنة الدراسية', N'Study year', 99),
    (N'COL_STUDY_YEARS', N'مدة الدراسة', N'Study Duration', 100),
    (N'COL_TABLE', N'الجدول', N'Table', 101),
    (N'COL_TEMPLATE_NAME', N'اسم القالب', N'Template Name', 102),
    (N'COL_TIMESTAMP', N'التاريخ والوقت', N'When', 103),
    (N'COL_TRANSFERRED', N'المحوّل', N'Transferred', 104),
    (N'COL_TX_TYPE', N'النوع', N'Type', 105),
    (N'COL_UNIVERSITY', N'الجامعة', N'University', 106),
    (N'COL_WAFAR', N'الوفر', N'Wafar', 107),
    (N'COL_WAFAR_BALANCE', N'رصيد الوفر', N'Wafar balance', 108),
    (N'COL_WAFAR_TYPE', N'نوع الوفر', N'Wafar Type', 109),
    (N'COL_YEAR', N'السنة', N'Year', 110),
    (N'COL_YEAR_MONTH', N'السنة / الشهر', N'Year / Month', 111),
    (N'COL_YEAR_RULES', N'قواعد السنوات', N'Year rules', 112),
    (N'LBL_ADDED_QUARTER', N'فصل الإضافة', N'Added Quarter', 113),
    (N'LBL_AGE', N'العمر', N'Age', 114),
    (N'LBL_AGENCY_TYPE', N'نوع الجهة', N'Agency Type', 115),
    (N'LBL_AMOUNT', N'المخصص المالي', N'Allocated Amount', 116),
    (N'LBL_AMOUNT_CUR', N'المبلغ', N'Amount', 117),
    (N'LBL_B1', N'١- قيد في سجل استمارة اعتماد الصرف برقم ............ تاريخ ............', N'1- Recorded in the payment authorization register under No. ............ dated ............', 118),
    (N'LBL_B2', N'٢- إقرار بأن ربط البند المُعتمد يسمح بالصرف لدى', N'2- Confirmation that the approved item allows disbursement through', 119),
    (N'LBL_B3', N'٣- الإقرار بأن مبلغ أضيف بحسابات.', N'3- Confirmation that the amount has been posted to the accounts.', 120),
    (N'LBL_BALANCE', N'الرصيد الحالي', N'Current balance', 121),
    (N'LBL_BIRTH_DATE', N'تاريخ الميلاد', N'Birth Date', 122),
    (N'LBL_BIRTH_PLACE', N'مكان الميلاد', N'Birth Place', 123),
    (N'LBL_C1', N'يُتحمد مطبق', N'Certified applicable for', 124),
    (N'LBL_C1_TAIL', N'بتاريخ ....................', N'dated ....................', 125),
    (N'LBL_CELLPHONE', N'رقم الجوال', N'Mobile No.', 126),
    (N'LBL_CHECK_BRANCH', N'فرع', N', branch', 127),
    (N'LBL_CHECK_VIA', N'يصحب شيك عن', N'Accompanied by a cheque on', 128),
    (N'LBL_COLLEGE', N'الكلية', N'College', 129),
    (N'LBL_COUNTRIES', N'عدد الدول', N'Countries', 130),
    (N'LBL_COUNTRY', N'الدولة', N'Country', 131),
    (N'LBL_CURRENCY', N'العملة', N'Currency', 132),
    (N'LBL_CURRENT_QUARTER', N'الفصل الحالي', N'Current Quarter', 133),
    (N'LBL_D1', N'١- رقم الشيك / دفتر الميزانية العام:', N'1- Cheque No. / General budget book:', 134),
    (N'LBL_D2', N'٢- توقيعات موظفي المبنى الجاري ..............................', N'2- Signatures of the current office staff ..............................', 135),
    (N'LBL_D3', N'٣- رقم الشيك / القرار أصلح مبلغ أضيف بحسابات:', N'3- Cheque No. / decision for the amount posted to the accounts:', 136),
    (N'LBL_D4', N'٤- توقيع مستلم الشيك: اسم المستلم', N'4- Cheque recipient signature: recipient name', 137),
    (N'LBL_D4_ID', N'رقم الهوية', N'ID number', 138),
    (N'LBL_DATE', N'تاريخ', N'Date', 139),
    (N'LBL_DECISION_NO', N'رقم قرار الإيفاد', N'Delegation Decision No.', 140),
    (N'LBL_DECISION_YEAR', N'سنة القرار', N'Decision Year', 141),
    (N'LBL_DEDUCTIONS', N'التخفيضات', N'Deductions', 142),
    (N'LBL_DEDUCT_NOTE', N'سيمله الاستقطاعات', N'Deductions applied', 143),
    (N'LBL_DEFAULT_BANK', N'البنك المركزي', N'the Central Bank', 144),
    (N'LBL_DEGREE', N'الدرجة العلمية', N'Degree', 145),
    (N'LBL_DEPT', N'الإدارة:', N'Department:', 146),
    (N'LBL_DISBURSED_AMOUNT_SUM', N'المبلغ المنصرف بالدولار', N'Amount Disbursed (USD)', 147),
    (N'LBL_DISBURSED_COUNT_SUM', N'العدد المنصرف', N'Disbursed Count', 148),
    (N'LBL_DISBURSEMENT_RATIO', N'نسبة الصرف', N'Disbursement Ratio', 149),
    (N'LBL_DOC_TITLE', N'شهادة قيد', N'Enrollment Certificate', 150),
    (N'LBL_EDUCATION', N'القسم', N'Department', 151),
    (N'LBL_EMAIL', N'البريد الإلكتروني', N'Email', 152),
    (N'LBL_EMERGENCY', N'جهة اتصال للطوارئ', N'Emergency Contact', 153),
    (N'LBL_ENTITY', N'الجهة', N'Entity', 154),
    (N'LBL_EXPENSE_HEAD', N'باب وتفصيل والبند والنوع المصروف عليه', N'Chapter, detail, item and type of expenditure', 155),
    (N'LBL_FORM_SERIAL', N'[  نموذج رقم ١١ حسابات  ]', N'[  Form No. 11 - Accounts  ]', 156),
    (N'LBL_GENDER', N'الجنس', N'Gender', 157),
    (N'LBL_GRAND_TOTAL', N'الإجمالي', N'Grand Total', 158),
    (N'LBL_HOME_COUNTRY', N'الدولة', N'Country', 159),
    (N'LBL_HOME_GOV', N'المحافظة', N'Governorate', 160),
    (N'LBL_HOME_MUD', N'المديرية', N'District', 161),
    (N'LBL_HOME_NUMBER', N'هاتف المنزل', N'Home Phone', 162),
    (N'LBL_LAST_QUARTER', N'آخر فصل', N'Last Quarter', 163),
    (N'LBL_LEVEL', N'المستوى الحالي', N'Current Level', 164),
    (N'LBL_MAJOR', N'التخصص', N'Major', 165),
    (N'LBL_MAJORS', N'عدد التخصصات', N'Majors', 166),
    (N'LBL_MINISTRY_FLD', N'وزارة / جواز مركزي / محاسبات:', N'Ministry / Central Authority / Accounts:', 167),
    (N'LBL_MINISTRY_TYPE', N'الوزارة / الجهة', N'Ministry / Entity', 168),
    (N'LBL_NAME', N'الاسم', N'Name', 169),
    (N'LBL_NET_DUE', N'صافي المبلغ المستحق صرفه وأقدره:', N'Net amount payable:', 170),
    (N'LBL_NOTES_TITLE', N'ملاحظات هامة:', N'Important notes:', 171),
    (N'LBL_NOTE_1', N'1. لا يتم الصرف للطلاب الموقوفة مستحقاتهم إلا بموجب إطلاق من الوزارة، وعلى السفارة أو الملحقية تحرير كشف جماعي بالبيانات المالية والأكاديمية لهؤلاء الطلاب وإرساله إلى الوزارة خلال أسبوع من تاريخ وصول الكشوفات.', N'1. No payment is made to students whose entitlements are suspended except upon release by the Ministry; the embassy or attaché office must prepare a consolidated statement of these students'' financial and academic data and send it to the Ministry within one week of receiving the statements.', 172),
    (N'LBL_NOTE_2', N'2. الطلاب المحدد لهم الربع الحالي كآخر مصرف؛ يعتبر هذا إنذاراً نهائياً، يتم إبلاغهم بذلك - ولا يتم الصرف إلا لمن يستحق.', N'2. For students whose current quarter is marked as the last payment, this is a final warning; they are to be notified, and payment is made only to those entitled.', 173),
    (N'LBL_NOTE_3', N'3. يتم إيقاف الصرف للطلاب المتعثرين والمنقطعين والخريجين والذين لم يتم توقيف مستحقاتهم من قبل الوزارة، وموافاة الوزارة بكشف جماعي بهؤلاء الطلاب.', N'3. Payment is stopped for students who have failed, dropped out or graduated and whose entitlements the Ministry has not yet suspended, and the Ministry is to be provided with a consolidated statement of these students.', 174),
    (N'LBL_NOTE_4', N'4. على مسئولية السفارة أو الملحقية صحة وقانونية الطلب والصرف لأي طالب لا يستحق ولم يتم الإبلاغ به.', N'4. The embassy or attaché office is responsible for the validity and legality of the request and payment for any student who is not entitled and was not reported.', 175),
    (N'LBL_NO_DATA', N'لا توجد بيانات', N'No data available', 176),
    (N'LBL_NUMBER', N'رقم', N'No.', 177),
    (N'LBL_ORIGINAL', N'أصل المبلغ', N'Original amount', 178),
    (N'LBL_PASSPORT_COUNTRY', N'بلد الإصدار', N'Issuing Country', 179),
    (N'LBL_PASSPORT_EXPIRE', N'تاريخ الانتهاء', N'Expiry Date', 180),
    (N'LBL_PASSPORT_ISSUE', N'تاريخ الإصدار', N'Issue Date', 181),
    (N'LBL_PASSPORT_NO', N'رقم الجواز', N'Passport No.', 182),
    (N'LBL_PENDING', N'قيد الاعتماد', N'Pending', 183),
    (N'LBL_PROVINCE', N'محافظة:', N'Province:', 184),
    (N'LBL_QUARTER', N'الربع', N'Quarter', 185),
    (N'LBL_QUARTER_SUMMARY', N'خلاصة', N'Summary of', 186),
    (N'LBL_QUARTER_TOTAL', N'إجمالي الفصل', N'Quarter Total', 187),
    (N'LBL_RECEIVED_AMOUNT_SUM', N'المبلغ الوارد بالدولار', N'Amount Received (USD)', 188),
    (N'LBL_RECEIVED_COUNT_SUM', N'العدد الوارد', N'Received Count', 189),
    (N'LBL_REF_NO', N'الرقم المرجعي', N'Reference No.', 190),
    (N'LBL_REMAINING_AMOUNT_SUM', N'المبلغ المتبقي بالدولار', N'Amount Remaining (USD)', 191),
    (N'LBL_REMAINING_COUNT_SUM', N'العدد المتبقي', N'Remaining Count', 192),
    (N'LBL_SEC_A', N'هذا الجزء خاص بالحسابات  (أ)', N'For Accounts use only  (A)', 193),
    (N'LBL_SEC_ACADEMIC', N'البيانات الأكاديمية', N'Academic Information', 194),
    (N'LBL_SEC_B', N'(ب)', N'(B)', 195),
    (N'LBL_SEC_C', N'(ج)', N'(C)', 196),
    (N'LBL_SEC_CONTACTS', N'بيانات الاتصال', N'Contact Information', 197),
    (N'LBL_SEC_D', N'(د)', N'(D)', 198),
    (N'LBL_SEC_DELEGATION', N'قرار الإيفاد', N'Delegation Decision', 199),
    (N'LBL_SEC_DESTINATION', N'جهة الدراسة', N'Study Destination', 200),
    (N'LBL_SEC_FINANCE', N'ملخص مالي', N'Finance Summary', 201),
    (N'LBL_SEC_PASSPORT', N'بيانات جواز السفر', N'Passport Information', 202),
    (N'LBL_SEC_PERSONAL', N'البيانات الشخصية', N'Personal Information', 203),
    (N'LBL_SENT_TO', N'ويرسل إلى:', N'Sent to:', 204),
    (N'LBL_SEQ', N'ف', N'#', 205),
    (N'LBL_SIGNATORY', N'المفوض بالتوقيع', N'Authorized Signatory', 206),
    (N'LBL_SIG_ACCMGR', N'توقيع مدير / رئيس الحسابات .....................................................', N'Signature of the Accounts Manager / Head .....................................................', 207),
    (N'LBL_SIG_ACCT', N'توقيع الموظف المختص بالحسابات .....................................................', N'Signature of the Accounts Officer .....................................................', 208),
    (N'LBL_SIG_DG', N'توقيع المدير العام', N'Signature of the Director General', 209),
    (N'LBL_SIG_HEAD', N'توقيع رئيس الجهة', N'Signature of the Head of Entity', 210),
    (N'LBL_SIG_OFFICER', N'توقيع الموظف المختص', N'Signature of the Responsible Officer', 211),
    (N'LBL_STUDENTS', N'عدد الطلاب', N'Students', 212),
    (N'LBL_STUDENT_ID', N'رقم الطالب', N'Student ID', 213),
    (N'LBL_STUDENT_NAME', N'اسم الطالب', N'Student Name', 214),
    (N'LBL_STUDY_COUNTRY', N'بلد الدراسة', N'Country of Study', 215),
    (N'LBL_STUDY_GOV', N'مدينة / إقليم الدراسة', N'Study City / Region', 216),
    (N'LBL_STUDY_MAJOR_IN', N'التخصص الدقيق', N'Specialization', 217),
    (N'LBL_STUDY_STATUS', N'الحالة الدراسية', N'Study Status', 218),
    (N'LBL_STUDY_TYPE', N'نوع الدراسة', N'Study Type', 219),
    (N'LBL_STUDY_YEARS', N'عدد سنوات الدراسة', N'Years of Study', 220),
    (N'LBL_SUB_1', N'بنص', N'Text', 221),
    (N'LBL_SUB_2', N'حصل', N'Recd.', 222),
    (N'LBL_SUB_3', N'بند', N'Item', 223),
    (N'LBL_SUB_4', N'باب', N'Chapter', 224),
    (N'LBL_SUMMARY', N'ملخص البيانات', N'Summary', 225),
    (N'LBL_TOTAL', N'الجملة', N'Total', 226),
    (N'LBL_TOTAL_AMOUNT', N'إجمالي المبلغ', N'Total Amount', 227),
    (N'LBL_TOTAL_BOOKS', N'إجمالي بدل الكتب', N'Total Books Allowance', 228),
    (N'LBL_TOTAL_DEPOSITS', N'إجمالي الإيداعات', N'Total deposits', 229),
    (N'LBL_TOTAL_FEES', N'إجمالي الرسوم الجامعية', N'Total University Fees', 230),
    (N'LBL_TOTAL_LOANS', N'إجمالي القروض', N'Total Loans', 231),
    (N'LBL_TOTAL_NET', N'إجمالي الصافي', N'Total Net', 232),
    (N'LBL_TOTAL_RECEIVED', N'إجمالي المستلم', N'Total Received', 233),
    (N'LBL_TOTAL_RECORDS', N'إجمالي السجلات', N'Total Records', 234),
    (N'LBL_TOTAL_SPENT', N'إجمالي المصروف', N'Total spent', 235),
    (N'LBL_TOTAL_STUDENTS', N'إجمالي الطلاب', N'Total Students', 236),
    (N'LBL_TOTAL_WAFAR', N'إجمالي الوفر', N'Total Wafar', 237),
    (N'LBL_TO_WHOM', N'إلى من يهمه الأمر', N'To Whom It May Concern', 238),
    (N'LBL_UNIVERSITY', N'الجامعة', N'University', 239),
    (N'LBL_UNIVERSITY_TYPE', N'نوع الجامعة', N'University Type', 240),
    (N'LBL_VALID_THROUGH', N'سارية حتى', N'Valid Through', 241),
    (N'LBL_YEAR', N'العام', N'Year', 242),
    (N'LBL_YEAR_SUFFIX', N'م', N'', 243),
    (N'COL_AGE@APPROVALS_REQUESTS', N'المدة (يوم)', N'Age (days)', 244),
    (N'COL_STATUS@APPROVALS_REQUESTS', N'الحالة', N'Status', 245),
    (N'COL_BANK@BANK_TRANSACTION', N'البنك', N'Bank', 246),
    (N'COL_CATEGORY@DATA_VISUALIZATION', N'الفئة', N'Category', 247),
    (N'COL_COUNTRY@FINANCIAL_DATA_FORM_V2', N'الدولة', N'Country', 248),
    (N'COL_DECISION_NO@FINANCIAL_DATA_FORM_V2', N'رقم القرار', N'Decision No.', 249),
    (N'COL_DEGREE@FINANCIAL_DATA_FORM_V2', N'الدرجة', N'Degree', 250),
    (N'COL_LEVEL@FINANCIAL_DATA_FORM_V2', N'المستوى الحالي', N'Current Level', 251),
    (N'LBL_CURRENCY@PAYMENT_AUTH_FORM', N'ريال', N'Riyal', 252),
    (N'LBL_DOC_TITLE@PAYMENT_AUTH_FORM', N'استمارة اعتماد الصرف', N'Payment Authorization Form', 253),
    (N'LBL_GENERATED@QUARTERLY_FINANCIAL', N'التاريخ', N'Date', 254),
    (N'LBL_PAGE@QUARTERLY_FINANCIAL', N'الصفحة', N'Page', 255),
    (N'LBL_TOTAL_AMOUNT@QUARTERLY_FINANCIAL', N'الإجماليات', N'Total', 256),
    (N'LBL_TOTAL_RECORDS@QUARTERLY_FINANCIAL', N'عدد الجهات', N'Entities', 257),
    (N'COL_STATUS@STUDENT_ENTITLEMENTS', N'الحالة', N'Status', 258),
    (N'COL_STUDENT_NAME@STUDENT_ENTITLEMENTS', N'الطالب', N'Student', 259),
    (N'LBL_DOC_TITLE@STUDENT_FINANCIAL_STATEMENT', N'كشف الحساب المالي للطالب', N'Student Financial Statement', 260),
    (N'LBL_DOC_TITLE@STUDENT_ID_CARD', N'بطاقة الطالب', N'Student ID Card', 261),
    (N'LBL_DOC_TITLE@STUDENT_REGISTRATION_FORM', N'استمارة بيانات الطالب', N'Student Registration Form', 262),
    (N'COL_CATEGORY@WAFAR_STATEMENT', N'بند الصرف', N'Category', 263),
    (N'COL_STATUS@WAFAR_STATEMENT', N'الحالة', N'Status', 264),
    (N'COL_STUDENT_NAME@WAFAR_STATEMENT', N'الطالب', N'Student', 265),
    (N'LBL_APPROVALS_STATE_PENDING',   N'قيد الاعتماد', N'Pending',   266),
    (N'LBL_APPROVALS_STATE_COMPLETED', N'منتهية',       N'Completed', 267),
    (N'LBL_KIND_LOAN',                 N'قرض',          N'Loan',      268)
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
PRINT 'V077: rows inserted = ' + CAST(@inserted AS VARCHAR(10)) + ', flagged IS_SYSTEM = ' + CAST(@updated AS VARCHAR(10))
    + ' (first run 253 / 0, re-run 0 / 0)';
GO

-- 4. Verification --------------------------------------------------------------
DECLARE @labels INT, @overrides INT, @blank INT;
SELECT @labels = COUNT(*) FROM MOHECORE.LU_REPORT_LABEL
 WHERE (CODE LIKE 'COL[_]%' OR CODE LIKE 'LBL[_]%') AND CODE NOT LIKE '%@%' AND IS_DELETED = 0;
SELECT @overrides = COUNT(*) FROM MOHECORE.LU_REPORT_LABEL WHERE CODE LIKE '%@%' AND IS_DELETED = 0;
SELECT @blank = COUNT(*) FROM MOHECORE.LU_REPORT_LABEL WHERE NAME_AR = N'' AND NAME_EN = N'';
PRINT 'V077: active COL_/LBL_ labels = ' + CAST(@labels AS VARCHAR(10))
    + ' (expected 239 = 231 template parameters + 5 LBL_ rows of V076 + 3 named by Java)';
PRINT 'V077: per-report override rows = ' + CAST(@overrides AS VARCHAR(10)) + ' (expected 22)';
PRINT 'V077: rows with both names empty = ' + CAST(@blank AS VARCHAR(10)) + ' (expected 0)';
SELECT CODE AS BOTH_NAMES_EMPTY FROM MOHECORE.LU_REPORT_LABEL WHERE NAME_AR = N'' AND NAME_EN = N'';
SELECT CODE, LEFT(NAME_AR, 40) AS NAME_AR, LEFT(NAME_EN, 40) AS NAME_EN, SORT_ORDER
  FROM MOHECORE.LU_REPORT_LABEL WHERE CODE LIKE '%@%' ORDER BY SORT_ORDER;
GO
