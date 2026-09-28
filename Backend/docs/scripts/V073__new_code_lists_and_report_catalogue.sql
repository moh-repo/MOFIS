/* ============================================================================
   V073 — The code lists that had no table, and the report catalogue.

   Why
   ---
   After V072 every list the code depends on is a table — except the ones
   that never had one: they lived as option arrays and switch statements in
   the Angular components (alert notice types, wafar beneficiary types,
   notification send outcomes, approval history actions, account natures,
   allocation types, the accounting state names that mirror the V040–V042
   CHECK constraints, portal inquiry and information-request states, the
   colours of the student-graph branches, the fields a change request may
   edit) and, largest of all, the report hub catalogue: ten landing cards and
   seven per-hub arrays in TypeScript, with names in the translation files.
   The owner's decision (30 Aug 2026): tables for all of them.

   What
   ----
   1. Fifteen MOHECORE tables of the V072 shape (CODE, NAME_AR, NAME_EN,
      SORT_ORDER, IS_DELETED, UI_SEVERITY, ICON, IS_SYSTEM), seeded with the
      codes the code and the screens use today, IS_SYSTEM on the ones a Java
      constant names. LU_ACCOUNT_NATURE also carries NORMAL_BALANCE (D/C),
      LU_GRAPH_CATEGORY a UI_COLOR, CHANGE_REQUEST_FIELD the LOOKUP_LIST that
      feeds a dropdown field. The CHECK constraints of V040–V042 stay: they
      are the state machine's guard, the tables are the catalogue.
   2. UMSCORE.UMS_Reports becomes the report catalogue: ParentCode (a hub's
      code; NULL for a hub or a landing card), Icon, Color, QueryParams,
      PageCode, PermissionCodes (comma-separated, any-of) and SortOrder, and
      one row per hub and per card as the hubs list them today — names and
      descriptions copied from the translation files, guards copied from the
      route module so no card a user can open today disappears. The four
      rows the table already held are kept and completed. Deactivating a row
      removes the card; the frontend filters by permission as before.

   Rollback
   --------
     DROP the fifteen tables; ALTER TABLE UMSCORE.UMS_Reports DROP the seven
     columns (drop FK_UMS_Reports_Parent first); DELETE the catalogue rows
     whose ReportCode starts with HUB_ or was inserted here (they carry
     CreatedAt of this run). The application refuses to boot without these
     objects (CodeListStartupGuard), so roll the code back first.

   Idempotent. Run with sqlcmd -I -f 65001 -b after V072.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF COL_LENGTH('MOHECORE.LU_ALERT_SEVERITY', 'IS_SYSTEM') IS NULL
    THROW 50073, 'V073: the LU tables lack the V072 columns — run V072 first.', 1;
GO

/* ── 1. The new tables ───────────────────────────────────────────────────── */
PRINT '=== V073 step 1: tables ===';

DECLARE @new TABLE (name SYSNAME PRIMARY KEY, extra NVARCHAR(200) NULL);
INSERT INTO @new (name, extra) VALUES
  ('LU_ALERT_TYPE', NULL),
  ('LU_WAFAR_BENEFICIARY_TYPE', NULL),
  ('LU_NOTIFICATION_SEND_STATUS', NULL),
  ('LU_APPROVAL_ACTION', NULL),
  ('LU_ACCOUNT_NATURE', N'NORMAL_BALANCE NCHAR(1) NULL'),
  ('LU_ALLOCATION_TYPE', NULL),
  ('LU_ALLOCATION_STATUS', NULL),
  ('LU_PAYMENT_BATCH_STATUS', NULL),
  ('LU_APPROPRIATION_STATUS', NULL),
  ('LU_FISCAL_YEAR_STATUS', NULL),
  ('LU_FISCAL_PERIOD_STATUS', NULL),
  ('LU_INQUIRY_TYPE', NULL),
  ('LU_INQUIRY_STATUS', NULL),
  ('LU_INFO_REQUEST_STATUS', NULL),
  ('LU_INFO_REVIEW_STATUS', NULL),
  ('LU_GRAPH_CATEGORY', N'UI_COLOR NVARCHAR(9) NULL'),
  ('CHANGE_REQUEST_FIELD', N'LOOKUP_LIST NVARCHAR(50) NULL');

DECLARE @t SYSNAME, @extra NVARCHAR(200), @sql NVARCHAR(MAX), @created INT = 0;
DECLARE ncur CURSOR LOCAL FAST_FORWARD FOR SELECT name, extra FROM @new ORDER BY name;
OPEN ncur;
FETCH NEXT FROM ncur INTO @t, @extra;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID('MOHECORE.' + @t, 'U') IS NULL
    BEGIN
        SET @sql = N'CREATE TABLE MOHECORE.' + QUOTENAME(@t) + N' ('
                 + N' ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_' + @t + N' PRIMARY KEY,'
                 + N' CODE NVARCHAR(50) NOT NULL,'
                 + N' NAME_AR NVARCHAR(200) NOT NULL,'
                 + N' NAME_EN NVARCHAR(200) NOT NULL,'
                 + N' SORT_ORDER INT NOT NULL CONSTRAINT DF_' + @t + N'_SORT DEFAULT 0,'
                 + N' IS_DELETED BIT NOT NULL CONSTRAINT DF_' + @t + N'_DEL DEFAULT 0,'
                 + N' UI_SEVERITY NVARCHAR(20) NULL,'
                 + N' ICON NVARCHAR(50) NULL,'
                 + N' IS_SYSTEM BIT NOT NULL CONSTRAINT DF_' + @t + N'_IS_SYSTEM DEFAULT 0'
                 + CASE WHEN @extra IS NULL THEN N'' ELSE N', ' + @extra END
                 + N', CONSTRAINT UQ_' + @t + N'_CODE UNIQUE (CODE));';
        EXEC sp_executesql @sql;
        SET @created += 1;
    END
    FETCH NEXT FROM ncur INTO @t, @extra;
END
CLOSE ncur; DEALLOCATE ncur;
PRINT 'V073: tables created = ' + CAST(@created AS VARCHAR(10));

/* ── 2. Seeds — MERGE on CODE; names/colours only fill NULLs so an edit survives ── */
PRINT '=== V073 step 2: seeds ===';

DECLARE @seed TABLE (tbl SYSNAME, code NVARCHAR(50), name_ar NVARCHAR(200), name_en NVARCHAR(200),
                     sort_order INT, sev NVARCHAR(20) NULL, icon NVARCHAR(50) NULL, sys BIT, extra NVARCHAR(200) NULL);
INSERT INTO @seed VALUES
  -- system notices (MOHE_ALERTS.alerts.alert_type; alert.ts getAlertTypeIcon / getAlertTypeColor)
  ('LU_ALERT_TYPE', 'INFO',    N'معلومة',  N'Information', 1, 'info',    'pi-info-circle',          1, NULL),
  ('LU_ALERT_TYPE', 'WARNING', N'تحذير',   N'Warning',     2, 'warn',    'pi-exclamation-triangle', 1, NULL),
  ('LU_ALERT_TYPE', 'ERROR',   N'خطأ',     N'Error',       3, 'danger',  'pi-times-circle',         1, NULL),
  ('LU_ALERT_TYPE', 'SUCCESS', N'نجاح',    N'Success',     4, 'success', 'pi-check-circle',         1, NULL),
  -- wafar beneficiary (wafar.component beneficiaryTypeOptions; WafarSpendingService)
  ('LU_WAFAR_BENEFICIARY_TYPE', 'STUDENT',          N'طالب',            N'Student',          1, NULL, 'pi-user',     1, NULL),
  ('LU_WAFAR_BENEFICIARY_TYPE', 'MINISTRY',         N'الوزارة',         N'Ministry',         2, NULL, 'pi-building', 1, NULL),
  ('LU_WAFAR_BENEFICIARY_TYPE', 'CULTURAL_AFFAIRS', N'الشؤون الثقافية', N'Cultural Affairs', 3, NULL, 'pi-globe',    1, NULL),
  ('LU_WAFAR_BENEFICIARY_TYPE', 'OTHER',            N'أخرى',            N'Other',            4, NULL, 'pi-ellipsis-h', 1, NULL),
  -- notification send outcome (NotificationService; notification-history statusSeverity)
  ('LU_NOTIFICATION_SEND_STATUS', 'SENT',    N'تم الإرسال', N'Sent',    1, 'success', 'pi-check',                1, NULL),
  ('LU_NOTIFICATION_SEND_STATUS', 'PARTIAL', N'جزئي',       N'Partial', 2, 'warn',    'pi-exclamation-triangle', 1, NULL),
  ('LU_NOTIFICATION_SEND_STATUS', 'FAILED',  N'فشل',        N'Failed',  3, 'danger',  'pi-times',                1, NULL),
  -- approval history actions (ApprovalWorkflowService.recordHistory; approval.service getActionLabel)
  ('LU_APPROVAL_ACTION', 'CREATED',   N'تم الإنشاء',   N'Created',   1, 'info',    'pi-plus',     1, NULL),
  ('LU_APPROVAL_ACTION', 'SUBMITTED', N'تم الإرسال',   N'Submitted', 2, 'info',    'pi-send',     1, NULL),
  ('LU_APPROVAL_ACTION', 'UPDATED',   N'تم التعديل',   N'Updated',   3, 'info',    'pi-pencil',   1, NULL),
  ('LU_APPROVAL_ACTION', 'APPROVED',  N'تمت الموافقة', N'Approved',  4, 'success', 'pi-check',    1, NULL),
  ('LU_APPROVAL_ACTION', 'REJECTED',  N'مرفوض',        N'Rejected',  5, 'danger',  'pi-times',    1, NULL),
  ('LU_APPROVAL_ACTION', 'EXECUTED',  N'تم التنفيذ',   N'Executed',  6, 'success', 'pi-verified', 1, NULL),
  -- chart of accounts (CoaAccount.nature CHECK, chart-of-accounts natures)
  ('LU_ACCOUNT_NATURE', 'ASSET',     N'أصول',        N'Asset',     1, NULL, NULL, 1, 'D'),
  ('LU_ACCOUNT_NATURE', 'LIABILITY', N'التزامات',    N'Liability', 2, NULL, NULL, 1, 'C'),
  ('LU_ACCOUNT_NATURE', 'EQUITY',    N'حقوق ملكية',  N'Equity',    3, NULL, NULL, 1, 'C'),
  ('LU_ACCOUNT_NATURE', 'REVENUE',   N'إيرادات',     N'Revenue',   4, NULL, NULL, 1, 'C'),
  ('LU_ACCOUNT_NATURE', 'EXPENSE',   N'مصروفات',     N'Expense',   5, NULL, NULL, 1, 'D'),
  -- allocations (AttacheAllocation; allocations.component types; accounting-shared statusSeverity)
  ('LU_ALLOCATION_TYPE', 'REGULAR',    N'عادي (ربعي)', N'Regular (quarterly)', 1, NULL, NULL, 1, NULL),
  ('LU_ALLOCATION_TYPE', 'ADDITIONAL', N'إضافي',       N'Additional',          2, NULL, NULL, 1, NULL),
  ('LU_ALLOCATION_STATUS', 'DRAFT',       N'مسودة',  N'Draft',       1, 'secondary', 'pi-file-edit', 1, NULL),
  ('LU_ALLOCATION_STATUS', 'APPROVED',    N'معتمد',  N'Approved',    2, 'info',      'pi-check',     1, NULL),
  ('LU_ALLOCATION_STATUS', 'TRANSFERRED', N'محوَّل', N'Transferred', 3, 'success',   'pi-send',      1, NULL),
  ('LU_ALLOCATION_STATUS', 'CANCELLED',   N'ملغى',   N'Cancelled',   4, 'danger',    'pi-ban',       1, NULL),
  ('LU_PAYMENT_BATCH_STATUS', 'OPEN',      N'مفتوحة', N'Open',      1, 'secondary', 'pi-folder-open', 1, NULL),
  ('LU_PAYMENT_BATCH_STATUS', 'APPROVED',  N'معتمد',  N'Approved',  2, 'info',      'pi-check',       1, NULL),
  ('LU_PAYMENT_BATCH_STATUS', 'DISBURSED', N'مصروفة', N'Disbursed', 3, 'success',   'pi-wallet',      1, NULL),
  ('LU_PAYMENT_BATCH_STATUS', 'CLOSED',    N'مغلقة',  N'Closed',    4, 'contrast',  'pi-lock',        1, NULL),
  ('LU_PAYMENT_BATCH_STATUS', 'CANCELLED', N'ملغى',   N'Cancelled', 5, 'danger',    'pi-ban',         1, NULL),
  ('LU_APPROPRIATION_STATUS', 'DRAFT',    N'مسودة',  N'Draft',    1, 'secondary', 'pi-file-edit', 1, NULL),
  ('LU_APPROPRIATION_STATUS', 'APPROVED', N'معتمد',  N'Approved', 2, 'info',      'pi-check',     1, NULL),
  ('LU_APPROPRIATION_STATUS', 'REVISED',  N'معدَّل', N'Revised',  3, 'contrast',  'pi-history',   1, NULL),
  ('LU_FISCAL_YEAR_STATUS', 'OPEN',   N'مفتوحة', N'Open',   1, 'success',  'pi-unlock', 1, NULL),
  ('LU_FISCAL_YEAR_STATUS', 'CLOSED', N'مغلقة',  N'Closed', 2, 'contrast', 'pi-lock',   1, NULL),
  ('LU_FISCAL_YEAR_STATUS', 'LOCKED', N'مقفلة',  N'Locked', 3, 'danger',   'pi-ban',    1, NULL),
  ('LU_FISCAL_PERIOD_STATUS', 'OPEN',   N'مفتوحة', N'Open',   1, 'success',  'pi-unlock', 1, NULL),
  ('LU_FISCAL_PERIOD_STATUS', 'CLOSED', N'مغلقة',  N'Closed', 2, 'contrast', 'pi-lock',   1, NULL),
  -- student portal (inquiries were a free-text type; GENERAL is the default the controller writes)
  ('LU_INQUIRY_TYPE', 'GENERAL',   N'عام',      N'General',   1, NULL, 'pi-question-circle', 1, NULL),
  ('LU_INQUIRY_TYPE', 'ACADEMIC',  N'أكاديمي',  N'Academic',  2, NULL, 'pi-book',            0, NULL),
  ('LU_INQUIRY_TYPE', 'FINANCIAL', N'مالي',     N'Financial', 3, NULL, 'pi-wallet',          0, NULL),
  ('LU_INQUIRY_TYPE', 'DOCUMENTS', N'مستندات',  N'Documents', 4, NULL, 'pi-file',            0, NULL),
  ('LU_INQUIRY_TYPE', 'OTHER',     N'أخرى',     N'Other',     5, NULL, 'pi-ellipsis-h',      0, NULL),
  ('LU_INQUIRY_STATUS', 'OPEN',      N'مفتوح',        N'Open',      1, 'info',      'pi-envelope', 0, NULL),
  ('LU_INQUIRY_STATUS', 'PENDING',   N'قيد الانتظار', N'Pending',   2, 'warn',      'pi-clock',    1, NULL),
  ('LU_INQUIRY_STATUS', 'RESPONDED', N'تم الرد',      N'Responded', 3, 'success',   'pi-check',    1, NULL),
  ('LU_INQUIRY_STATUS', 'CLOSED',    N'مغلق',         N'Closed',    4, 'secondary', 'pi-lock',     0, NULL),
  ('LU_INFO_REQUEST_STATUS', 'PENDING',   N'قيد الانتظار', N'Pending',   1, 'warn',      'pi-clock', 1, NULL),
  ('LU_INFO_REQUEST_STATUS', 'OVERDUE',   N'متأخر',        N'Overdue',   2, 'danger',    'pi-exclamation-triangle', 1, NULL),
  ('LU_INFO_REQUEST_STATUS', 'RESPONDED', N'تم الرد',      N'Responded', 3, 'success',   'pi-check', 1, NULL),
  ('LU_INFO_REQUEST_STATUS', 'CANCELLED', N'ملغى',         N'Cancelled', 4, 'secondary', 'pi-ban',   1, NULL),
  ('LU_INFO_REVIEW_STATUS', 'PENDING_REVIEW', N'قيد المراجعة', N'Pending Review', 1, 'warn',    'pi-clock', 1, NULL),
  ('LU_INFO_REVIEW_STATUS', 'APPROVED',       N'موافق عليه',   N'Approved',       2, 'success', 'pi-check', 1, NULL),
  ('LU_INFO_REVIEW_STATUS', 'REJECTED',       N'مرفوض',        N'Rejected',       3, 'danger',  'pi-times', 1, NULL),
  -- student graph branches (StudentGraphService categories; messages*.properties graph.cat.*; student-graph CATEGORY_COLORS)
  ('LU_GRAPH_CATEGORY', 'student',         N'الطالب',                 N'Student',                0, NULL, NULL, 1, '#2196F3'),
  ('LU_GRAPH_CATEGORY', 'cat_personal',    N'البيانات الشخصية',       N'Personal Information',   1, NULL, NULL, 1, '#9C27B0'),
  ('LU_GRAPH_CATEGORY', 'cat_passport',    N'الجواز',                 N'Passport',               2, NULL, NULL, 1, '#673AB7'),
  ('LU_GRAPH_CATEGORY', 'cat_education',   N'البيانات الأكاديمية',    N'Academic Information',   3, NULL, NULL, 1, '#FF9800'),
  ('LU_GRAPH_CATEGORY', 'cat_location',    N'جهة الدراسة',            N'Study Destination',      4, NULL, NULL, 1, '#00BCD4'),
  ('LU_GRAPH_CATEGORY', 'cat_delegation',  N'قرار الإيفاد',           N'Delegation Decision',    5, NULL, NULL, 1, '#3F51B5'),
  ('LU_GRAPH_CATEGORY', 'cat_contacts',    N'بيانات التواصل',         N'Contact Information',    6, NULL, NULL, 1, '#009688'),
  ('LU_GRAPH_CATEGORY', 'cat_finance',     N'المالية',                N'Finance',                7, NULL, NULL, 1, '#4CAF50'),
  ('LU_GRAPH_CATEGORY', 'cat_payments',    N'استمارات اعتماد الصرف',  N'Payment Authorizations', 8, NULL, NULL, 1, '#8BC34A'),
  ('LU_GRAPH_CATEGORY', 'cat_fees',        N'الرسوم الجامعية',        N'University Fees',        9, NULL, NULL, 1, '#CDDC39'),
  ('LU_GRAPH_CATEGORY', 'cat_books',       N'بدل الكتب',              N'Books Allowance',       10, NULL, NULL, 1, '#FFC107'),
  ('LU_GRAPH_CATEGORY', 'cat_loans',       N'السلف',                  N'Loans',                 11, NULL, NULL, 1, '#FF5722'),
  ('LU_GRAPH_CATEGORY', 'cat_yearResults', N'نتائج السنوات',          N'Year Results',          12, NULL, NULL, 1, '#F44336'),
  ('LU_GRAPH_CATEGORY', 'cat_extensions',  N'التمديدات',              N'Extensions',            13, NULL, NULL, 1, '#795548'),
  -- fields a change request may edit (student-change-requests FIELD_LOOKUP; portal my-requests FIELD_LABELS)
  ('CHANGE_REQUEST_FIELD', 'studentName',                  N'اسم الطالب',                   N'Student Name',            1, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentGender',                N'الجنس',                        N'Gender',                  2, NULL, NULL, 0, 'genders'),
  ('CHANGE_REQUEST_FIELD', 'studentBirthdate',             N'تاريخ الميلاد',                N'Birth Date',              3, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentBirthdatePlace',        N'مكان الميلاد',                 N'Birth Place',             4, NULL, NULL, 0, 'studentCountries'),
  ('CHANGE_REQUEST_FIELD', 'studentPassportNumber',        N'رقم جواز السفر',               N'Passport Number',         5, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentPassportIssueDate',     N'تاريخ إصدار جواز السفر',       N'Passport Issue Date',     6, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentPassportExpireDate',    N'تاريخ انتهاء جواز السفر',      N'Passport Expire Date',    7, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentPassportIssueCountryId', N'دولة إصدار جواز السفر',       N'Passport Issue Country',  8, NULL, NULL, 0, 'studentCountries'),
  ('CHANGE_REQUEST_FIELD', 'studentEmail',                 N'البريد الإلكتروني',            N'Email',                   9, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentCellphone',             N'رقم الجوال',                   N'Cell Phone',             10, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentHomeNumber',            N'رقم المنزل',                   N'Home Phone',             11, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studentEmergencyContact',      N'جهة الاتصال في حالات الطوارئ', N'Emergency Contact',      12, NULL, NULL, 0, NULL),
  ('CHANGE_REQUEST_FIELD', 'studyCountryIn',               N'بلد الدراسة',                  N'Study Country',          13, NULL, NULL, 0, 'studyCountries'),
  ('CHANGE_REQUEST_FIELD', 'studyUniversityIn',            N'جامعة الدراسة',                N'Study University',       14, NULL, NULL, 0, 'studyUniversities'),
  ('CHANGE_REQUEST_FIELD', 'studyCollegeIn',               N'كلية الدراسة',                 N'Study College',          15, NULL, NULL, 0, 'studyColleges'),
  ('CHANGE_REQUEST_FIELD', 'studyMajorIn',                 N'تخصص الدراسة',                 N'Study Major',            16, NULL, NULL, 0, 'studyMajors'),
  ('CHANGE_REQUEST_FIELD', 'studyEducationIn',             N'الجهة التعليمية',              N'Study Education',        17, NULL, NULL, 0, 'studyEducations'),
  ('CHANGE_REQUEST_FIELD', 'studentCurrentLevelId',        N'المستوى الحالي',               N'Current Level',          18, NULL, NULL, 0, 'currentLevels');

DECLARE @code NVARCHAR(50), @name_ar NVARCHAR(200), @name_en NVARCHAR(200), @sort INT, @sev NVARCHAR(20),
        @icon NVARCHAR(50), @sys BIT, @ex NVARCHAR(200), @extraCol SYSNAME, @inserted INT = 0, @n INT;
DECLARE scur CURSOR LOCAL FAST_FORWARD FOR SELECT tbl, code, name_ar, name_en, sort_order, sev, icon, sys, extra FROM @seed;
OPEN scur;
FETCH NEXT FROM scur INTO @t, @code, @name_ar, @name_en, @sort, @sev, @icon, @sys, @ex;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @extraCol = CASE @t WHEN 'LU_ACCOUNT_NATURE' THEN 'NORMAL_BALANCE'
                            WHEN 'LU_GRAPH_CATEGORY' THEN 'UI_COLOR'
                            WHEN 'CHANGE_REQUEST_FIELD' THEN 'LOOKUP_LIST' END;
    SET @sql = N'DECLARE @before INT = (SELECT COUNT(*) FROM MOHECORE.' + QUOTENAME(@t) + N' WHERE CODE = @code);'
             + N' MERGE MOHECORE.' + QUOTENAME(@t) + N' AS tgt'
             + N' USING (SELECT @code AS CODE) AS src ON tgt.CODE = src.CODE'
             + N' WHEN MATCHED THEN UPDATE SET UI_SEVERITY = COALESCE(tgt.UI_SEVERITY, @sev), ICON = COALESCE(tgt.ICON, @icon),'
             + N'      IS_SYSTEM = CASE WHEN @sys = 1 THEN 1 ELSE tgt.IS_SYSTEM END'
             + CASE WHEN @extraCol IS NULL THEN N'' ELSE N', ' + @extraCol + N' = COALESCE(tgt.' + @extraCol + N', @ex)' END
             + N' WHEN NOT MATCHED THEN INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, UI_SEVERITY, ICON, IS_SYSTEM'
             + CASE WHEN @extraCol IS NULL THEN N'' ELSE N', ' + @extraCol END + N')'
             + N' VALUES (@code, @name_ar, @name_en, @sort, @sev, @icon, @sys'
             + CASE WHEN @extraCol IS NULL THEN N'' ELSE N', @ex' END + N');'
             + N' SELECT @n = 1 - @before;';
    EXEC sp_executesql @sql,
         N'@code NVARCHAR(50), @name_ar NVARCHAR(200), @name_en NVARCHAR(200), @sort INT, @sev NVARCHAR(20), @icon NVARCHAR(50), @sys BIT, @ex NVARCHAR(200), @n INT OUTPUT',
         @code = @code, @name_ar = @name_ar, @name_en = @name_en, @sort = @sort, @sev = @sev, @icon = @icon, @sys = @sys, @ex = @ex, @n = @n OUTPUT;
    SET @inserted += @n;
    FETCH NEXT FROM scur INTO @t, @code, @name_ar, @name_en, @sort, @sev, @icon, @sys, @ex;
END
CLOSE scur; DEALLOCATE scur;
PRINT 'V073: code rows inserted = ' + CAST(@inserted AS VARCHAR(10));

/* ── 3. UMS_Reports as the report catalogue ──────────────────────────────── */
PRINT '=== V073 step 3: UMS_Reports columns ===';

IF COL_LENGTH('UMSCORE.UMS_Reports', 'ParentCode') IS NULL
    ALTER TABLE UMSCORE.UMS_Reports ADD ParentCode NVARCHAR(100) NULL;
IF COL_LENGTH('UMSCORE.UMS_Reports', 'Icon') IS NULL
    ALTER TABLE UMSCORE.UMS_Reports ADD Icon NVARCHAR(50) NULL;
IF COL_LENGTH('UMSCORE.UMS_Reports', 'Color') IS NULL
    ALTER TABLE UMSCORE.UMS_Reports ADD Color NVARCHAR(30) NULL;
IF COL_LENGTH('UMSCORE.UMS_Reports', 'QueryParams') IS NULL
    ALTER TABLE UMSCORE.UMS_Reports ADD QueryParams NVARCHAR(200) NULL;
IF COL_LENGTH('UMSCORE.UMS_Reports', 'PageCode') IS NULL
    ALTER TABLE UMSCORE.UMS_Reports ADD PageCode NVARCHAR(100) NULL;
IF COL_LENGTH('UMSCORE.UMS_Reports', 'PermissionCodes') IS NULL
    ALTER TABLE UMSCORE.UMS_Reports ADD PermissionCodes NVARCHAR(400) NULL;
IF COL_LENGTH('UMSCORE.UMS_Reports', 'SortOrder') IS NULL
    ALTER TABLE UMSCORE.UMS_Reports ADD SortOrder INT NOT NULL CONSTRAINT DF_UMS_Reports_SortOrder DEFAULT 0;
GO

PRINT '=== V073 step 4: report catalogue rows ===';
DECLARE @catBefore INT = (SELECT COUNT(*) FROM UMSCORE.UMS_Reports);

SET NOCOUNT ON;

------------------------------------------------------------------------------------------------------------
-- 1. Bring the 4 pre-existing rows up to the new shape (names/descriptions untouched).
------------------------------------------------------------------------------------------------------------
UPDATE UMSCORE.UMS_Reports SET ParentCode = 'HUB_STUDENT',    Icon = 'pi-chart-line', Color = 'blue',   PageCode = 'REPORT_ACADEMIC', PermissionCodes = NULL, SortOrder = 2, UpdatedAt = SYSUTCDATETIME() WHERE ReportCode = 'REP_STUDENT_ENROLLMENT';
UPDATE UMSCORE.UMS_Reports SET ParentCode = 'HUB_STUDENT',    Icon = 'pi-users',      Color = 'green',  PageCode = 'REPORT_ACADEMIC', PermissionCodes = NULL, SortOrder = 3, UpdatedAt = SYSUTCDATETIME() WHERE ReportCode = 'REP_STUDENT_DEMOGRAPHICS';
UPDATE UMSCORE.UMS_Reports SET ParentCode = 'HUB_FINANCIAL',  Icon = 'pi-wallet',     Color = 'blue',   PageCode = 'REPORT_FINANCE',  PermissionCodes = NULL, SortOrder = 4, UpdatedAt = SYSUTCDATETIME() WHERE ReportCode = 'REP_FINANCIAL_PAYMENT';
UPDATE UMSCORE.UMS_Reports SET ParentCode = 'HUB_UNIVERSITY', Icon = 'pi-star',       Color = 'orange', PageCode = 'REPORT_OTHER',    PermissionCodes = NULL, SortOrder = 3, UpdatedAt = SYSUTCDATETIME() WHERE ReportCode = 'REP_UNIVERSITY_PERFORMANCE';

------------------------------------------------------------------------------------------------------------
-- 2. Hubs, landing-page leaf cards, and every card inside each hub (one row per card, keyed on ReportCode).
--    Drop the CreatedAt/UpdatedAt columns from the SELECT if the table defaults them.
------------------------------------------------------------------------------------------------------------
INSERT INTO UMSCORE.UMS_Reports
    (ReportCode, ParentCode, ReportName, ReportNameAr, Description, DescriptionAr, Category, Route, QueryParams, Icon, Color, PageCode, PermissionCodes, SortOrder, IsActive, CreatedAt, UpdatedAt)
SELECT v.ReportCode, v.ParentCode, v.ReportName, v.ReportNameAr, v.Description, v.DescriptionAr, v.Category, v.Route, v.QueryParams, v.Icon, v.Color, v.PageCode, v.PermissionCodes, v.SortOrder, v.IsActive, SYSUTCDATETIME(), SYSUTCDATETIME()
FROM (VALUES
    -- ---------------------------------------------------------------- landing page (/reports/list), in on-screen order
    ('REP_FINANCIAL_DATA_FORM',    NULL, N'Financial Data Form',    N'إستمارة صرف مُستحقات مالية',    N'Financial Data Form - Generate official financial disbursement forms for student assistance payments by quarter and year.', N'إستمارة صرف مُستحقات مالية - إنشاء نماذج صرف مالي رسمية لمدفوعات مساعدة الطلاب حسب الربع والسنة.', 'LANDING', '/reports/financial-data-form',    NULL, 'pi-money-bill', 'green-600',    NULL,              'REPORT_FINANCE,FINANCE', 1,  1),
    ('REP_FINANCIAL_DATA_FORM_V2', NULL, N'Financial Data Form V2', N'إستمارة صرف مُستحقات مالية V2', N'Quarterly financial assistance statements in the ministry''s official layout, by quarter, year and country.',              N'كشوفات المساعدة المالية الربعية بالتنسيق الرسمي للوزارة حسب الربع والسنة والدولة.',                 'LANDING', '/reports/financial-data-form-v2', NULL, 'pi-file-check', 'teal-600',     NULL,              'REPORT_FINANCE,FINANCE', 2,  1),
    ('HUB_STUDENT',     NULL, N'Student Reports',     N'تقارير الطلاب',              N'Generate comprehensive student data reports, enrollment statistics, and academic performance analysis.', N'إنشاء تقارير شاملة عن بيانات الطلاب وإحصائيات التسجيل وتحليل الأداء الأكاديمي.',        'STUDENT',     '/reports/student',     NULL, 'pi-users',      'blue-600',     'REPORT_ACADEMIC', NULL, 3,  1),
    ('HUB_FINANCIAL',   NULL, N'Financial Reports',   N'التقارير المالية',           N'Access financial summaries, payment reports, scholarship tracking, and budget analysis.',                N'الوصول إلى الملخصات المالية وتقارير المدفوعات وتتبع المنح الدراسية وتحليل الميزانية.',  'FINANCIAL',   '/reports/financial',   NULL, 'pi-money-bill', 'green-600',    'REPORT_FINANCE',  NULL, 4,  1),
    ('HUB_STATISTICAL', NULL, N'Statistical Reports', N'التقارير الإحصائية',         N'View statistical analysis, trends, and comparative data across different periods.',                      N'عرض التحليل الإحصائي والاتجاهات والبيانات المقارنة عبر فترات مختلفة.',                   'STATISTICAL', '/reports/statistical', NULL, 'pi-chart-line', 'orange-600',   'REPORT_OTHER',    NULL, 5,  1),
    ('HUB_PERIODIC',    NULL, N'Periodic Reports',    N'التقارير الدورية',           N'Generate monthly, quarterly, and annual reports for institutional planning.',                             N'إنشاء تقارير شهرية وربعية وسنوية للتخطيط المؤسسي.',                                      'PERIODIC',    '/reports/periodic',    NULL, 'pi-calendar',   'purple-600',   'REPORT_OTHER',    NULL, 6,  1),
    ('HUB_UNIVERSITY',  NULL, N'University Reports',  N'تقارير الجامعات',            N'Track university partnerships, fee structures, and collaborative programs.',                              N'تتبع الشراكات الجامعية وهياكل الرسوم والبرامج التعاونية.',                               'UNIVERSITY',  '/reports/university',  NULL, 'pi-building',   'indigo-600',   'REPORT_OTHER',    NULL, 7,  1),
    ('HUB_OPERATIONS',  NULL, N'Operations Reports',  N'تقارير العمليات',            N'Approvals, requests and the audit trail — what happened, who did it, and when.',                          N'الاعتمادات والطلبات وسجل التدقيق — ماذا حدث، ومن قام به، ومتى.',                         'OPERATIONS',  '/reports/operations',  NULL, 'pi-cog',        'bluegray-600', 'REPORT_OTHER',    NULL, 8,  1),
    ('HUB_CUSTOM',      NULL, N'Custom Reports',      N'التقارير المخصصة',           N'Create custom reports based on specific criteria and export to various formats.',                        N'إنشاء تقارير مخصصة بناءً على معايير محددة وتصديرها إلى تنسيقات مختلفة.',                 'CUSTOM',      '/reports/custom',      NULL, 'pi-sliders-h',  'cyan-600',     'REPORT_OTHER',    NULL, 9,  1),
    ('HUB_GRAPHS',      NULL, N'Graphs & Charts',     N'الرسوم البيانية والمخططات', N'Visualize data with interactive charts and graphs',                                                       N'تصور البيانات باستخدام مخططات ورسوم بيانية تفاعلية',                                     'GRAPHS',      '/reports/graphs',      NULL, 'pi-chart-bar',  'pink-600',     'REPORTS',         NULL, 10, 1),

    -- ---------------------------------------------------------------- HUB_STUDENT (student-reports.component.ts); SortOrder 2 and 3 are the existing REP_STUDENT_ENROLLMENT / REP_STUDENT_DEMOGRAPHICS rows
    ('REP_STUDENT_INDIVIDUAL',            'HUB_STUDENT', N'Individual student reports', N'تقارير طالب فردي',            N'Registration-style data sheet, ID summary, full 360° profile, and academic history for one student',                       N'بيانات التسجيل، ملخص بطاقة الهوية، الملف الكامل 360°، والسجل الأكاديمي لطالب واحد',            'STUDENT', '/reports/student/individual-student',   NULL,                         'pi-id-card',     'cyan',   'REPORT_ACADEMIC', NULL, 1,  1),
    ('REP_STUDENT_ACADEMIC_PERFORMANCE',  'HUB_STUDENT', N'Academic Performance',       N'الأداء الأكاديمي',            N'Track academic progress, grade distributions, graduation rates, and student achievement metrics',                          N'تتبع التقدم الأكاديمي وتوزيع الدرجات ومعدلات التخرج ومقاييس إنجاز الطلاب',                     'STUDENT', '/reports/student/academic-performance', NULL,                         'pi-star',        'orange', 'REPORT_ACADEMIC', NULL, 4,  1),
    ('REP_STUDENT_STATUS',                'HUB_STUDENT', N'Student Status Report',      N'تقرير حالة الطلاب',           N'Monitor student statuses including active, graduated, suspended, and withdrawn students',                                  N'مراقبة حالات الطلاب بما في ذلك النشطين والخريجين والمعلقين والمنسحبين',                        'STUDENT', '/reports/student/status-report',        NULL,                         'pi-info-circle', 'purple', 'REPORT_ACADEMIC', NULL, 5,  1),
    ('REP_STUDENT_MAJOR_DISTRIBUTION',    'HUB_STUDENT', N'Major Distribution',         N'توزيع التخصصات',              N'View student enrollment distribution across different majors and specializations',                                         N'عرض توزيع تسجيل الطلاب عبر التخصصات والتخصصات الفرعية المختلفة',                               'STUDENT', '/reports/student/major-distribution',   NULL,                         'pi-book',        'indigo', 'REPORT_ACADEMIC', NULL, 6,  1),
    ('REP_STUDENT_LIST_BY_CRITERIA',      'HUB_STUDENT', N'Student List by Criteria',   N'قائمة الطلاب حسب المعايير',   N'Generate detailed student lists filtered by custom criteria including country, major, status, and more',                   N'إنشاء قوائم طلاب تفصيلية مصفاة حسب معايير مخصصة بما في ذلك الدولة والتخصص والحالة والمزيد',   'STUDENT', '/reports/student/list-detailed',        NULL,                         'pi-list',        'cyan',   'REPORT_ACADEMIC', NULL, 7,  1),
    -- official per-student documents: same page as REP_STUDENT_INDIVIDUAL, distinguished by ?doc=
    ('REP_STUDENT_DOC_ID_CARD',           'HUB_STUDENT', N'Student ID Card',            N'بطاقة الطالب',                N'Identity card with the student photo, key identity fields and study destination.',                                         N'بطاقة تعريفية بصورة الطالب وبياناته الأساسية وجهة دراسته.',                                    'STUDENT', '/reports/student/individual-student',   'doc=id-card',                'pi-id-card',     'teal',   'REPORT_ACADEMIC', NULL, 8,  1),
    ('REP_STUDENT_DOC_REGISTRATION_FORM', 'HUB_STUDENT', N'Student Registration Form',  N'استمارة بيانات الطالب',       N'Full form with every student field: personal, passport, academic, destination and contacts.',                              N'استمارة كاملة بجميع بيانات الطالب: الشخصية والجواز والأكاديمية وجهة الدراسة والاتصال.',        'STUDENT', '/reports/student/individual-student',   'doc=registration-form',      'pi-file-edit',   'blue',   'REPORT_ACADEMIC', NULL, 9,  1),
    ('REP_STUDENT_DOC_ENROLLMENT_CERT',   'HUB_STUDENT', N'Enrollment Certificate',     N'شهادة قيد',                   N'Official letter certifying the student enrollment and study status, with signature blocks.',                               N'شهادة رسمية تثبت قيد الطالب وحالته الدراسية مع خانات التوقيع.',                                'STUDENT', '/reports/student/individual-student',   'doc=enrollment-certificate', 'pi-verified',    'green',  'REPORT_ACADEMIC', NULL, 10, 1),
    ('REP_STUDENT_DOC_FIN_STATEMENT',     'HUB_STUDENT', N'Financial Statement',        N'كشف الحساب المالي',           N'Disbursement history grouped by quarter with wafar, net amounts, fees, books and loans.',                                  N'سجل الصرف مرتباً بالأرباع مع الوفر والمبالغ الصافية والرسوم وبدل الكتب والقروض.',              'STUDENT', '/reports/student/individual-student',   'doc=financial-statement',    'pi-wallet',      'orange', 'REPORT_ACADEMIC', NULL, 11, 1),

    -- ---------------------------------------------------------------- HUB_FINANCIAL (financial-reports.component.ts); SortOrder 1-2 are the financial-data-form duplicates (block 3 below), 4 is the existing REP_FINANCIAL_PAYMENT
    ('REP_FINANCIAL_QUARTERLY_SUMMARY',    'HUB_FINANCIAL', N'Quarterly Financial Summary',  N'خلاصة الربع المالي',           N'Generate detailed quarterly financial summary reports showing received, disbursed, and remaining amounts by entity.', N'إنشاء تقارير مالية ربعية مفصلة توضح المبالغ الواردة والمنصرفة والمتبقية حسب الجهة.', 'FINANCIAL', '/reports/quarterly-financial-report',    NULL, 'pi-calendar',   'pink',   'REPORT_FINANCE', NULL, 3,  1),
    ('REP_FINANCIAL_SCHOLARSHIP_TRACKING', 'HUB_FINANCIAL', N'Scholarship Tracking',         N'تتبع المنح الدراسية',          N'Track scholarship allocations, beneficiaries, payment schedules, and remaining balances',                             N'تتبع تخصيصات المنح الدراسية والمستفيدين وجداول الدفع والأرصدة المتبقية',              'FINANCIAL', '/reports/financial/scholarship-tracking', NULL, 'pi-bookmark',   'orange', 'REPORT_FINANCE', NULL, 5,  1),
    ('REP_FINANCIAL_BUDGET_ANALYSIS',      'HUB_FINANCIAL', N'Budget Analysis',              N'تحليل الميزانية',              N'Analyze budget utilization, spending patterns, and financial forecasts across different categories',                  N'تحليل استخدام الميزانية وأنماط الإنشاء والتوقعات المالية عبر فئات مختلفة',            'FINANCIAL', '/reports/financial/budget-analysis',      NULL, 'pi-chart-pie',  'purple', 'REPORT_FINANCE', NULL, 6,  1),
    ('REP_FINANCIAL_BANK_TRANSACTION',     'HUB_FINANCIAL', N'Bank Transaction Report',      N'تقرير المعاملات البنكية',      N'Generate reports of bank transactions, transfers, and account statements',                                             N'إنشاء تقارير المعاملات البنكية والتحويلات وكشوف الحسابات',                            'FINANCIAL', '/reports/financial/bank-transaction',     NULL, 'pi-building',   'indigo', 'REPORT_FINANCE', NULL, 7,  1),
    ('REP_FINANCIAL_AUDIT',                'HUB_FINANCIAL', N'Financial Audit Report',       N'تقرير التدقيق المالي',         N'Comprehensive financial audit reports with reconciliation and verification details',                                  N'تقارير التدقيق المالي الشاملة مع تفاصيل التسوية والتحقق',                             'FINANCIAL', '/reports/financial/financial-audit',      NULL, 'pi-verified',   'cyan',   'REPORT_FINANCE', NULL, 8,  1),
    ('REP_FINANCIAL_PAYMENT_AUTH',         'HUB_FINANCIAL', N'Payment Authorization Report — استمارة اعتماد الصرف', N'تقرير استمارات اعتماد الصرف', N'Form No. 11 — Ministry of Finance',                                                                  N'نموذج رقم ١١ — وزارة المالية',                                                        'FINANCIAL', '/reports/financial/payment-auth-report',  NULL, 'pi-file-edit',  'teal',   'REPORT_FINANCE', NULL, 9,  1),
    ('REP_FINANCIAL_WAFAR_STATEMENT',      'HUB_FINANCIAL', N'Wafar Statement',              N'كشف الوفر',                    N'Wafar deposits, spending and balance for an attaché, with a per-category breakdown.',                                 N'إيداعات الوفر ومصروفاته ورصيده للملحقية، مع تفصيل حسب بنود الصرف.',                    'FINANCIAL', '/reports/financial/wafar-statement',      NULL, 'pi-percentage', 'green',  'REPORT_FINANCE', NULL, 10, 1),
    ('REP_FINANCIAL_ACCOUNTING_POSITION',  'HUB_FINANCIAL', N'Accounting Position',          N'المركز المحاسبي',              N'Opening balance, transfers, disbursements and what is left, per attaché and currency.',                               N'الرصيد الافتتاحي والتحويلات والمصروفات والمتبقي، لكل ملحقية وعملة.',                   'FINANCIAL', '/reports/financial/accounting-position',  NULL, 'pi-book',       'blue',   'REPORT_FINANCE', NULL, 11, 1),
    ('REP_FINANCIAL_STUDENT_ENTITLEMENTS', 'HUB_FINANCIAL', N'Student Entitlements',         N'مستحقات الطالب',               N'Loans, books allowance and travel tickets for a student or an attaché, in one statement.',                            N'القروض وبدل الكتب وتذاكر السفر لطالب أو ملحقية، في كشف واحد.',                         'FINANCIAL', '/reports/financial/student-entitlements', NULL, 'pi-gift',       'purple', 'REPORT_FINANCE', NULL, 12, 1),

    -- ---------------------------------------------------------------- HUB_STATISTICAL (statistical-reports.component.ts)
    ('REP_STATISTICAL_TREND_ANALYSIS',      'HUB_STATISTICAL', N'Trend Analysis',         N'تحليل الاتجاهات',       N'Analyze trends in student enrollment, financial disbursements, and academic outcomes over time',            N'تحليل الاتجاهات في تسجيل الطلاب والصرف المالي والنتائج الأكاديمية بمرور الوقت',              'STATISTICAL', '/reports/statistical/trend-analysis',               NULL, 'pi-chart-line',     'blue',   'REPORT_OTHER', NULL, 1, 1),
    ('REP_STATISTICAL_COMPARATIVE',         'HUB_STATISTICAL', N'Comparative Statistics', N'الإحصائيات المقارنة',   N'Compare data across different periods, countries, universities, and student demographics',                 N'مقارنة البيانات المتحققة عبر فترات ودول وجامعات وتركيبات سكانية مختلفة للطلاب',              'STATISTICAL', '/reports/statistical/comparative-statistics',       NULL, 'pi-chart-bar',      'green',  'REPORT_OTHER', NULL, 2, 1),
    ('REP_STATISTICAL_PERFORMANCE_METRICS', 'HUB_STATISTICAL', N'Performance Metrics',    N'مقاييس الأداء',         N'Key performance indicators for academic progress, financial efficiency, and program effectiveness',        N'مؤشرات الأداء الرئيسية للتقدم الأكاديمي والكفاءة المالية وفعالية البرنامج',                  'STATISTICAL', '/reports/statistical/performance-metrics',          NULL, 'pi-chart-pie',      'orange', 'REPORT_OTHER', NULL, 3, 1),
    ('REP_STATISTICAL_GROWTH_ANALYSIS',     'HUB_STATISTICAL', N'Growth Analysis',        N'تحليل النمو',           N'Track growth rates in student numbers, budget allocation, and program expansion',                           N'تتبع معدلات النمو في أعداد الطلاب وتخصيص الميزانية وتوسع البرنامج',                          'STATISTICAL', '/reports/statistical/growth-analysis',              NULL, 'pi-arrow-up-right', 'purple', 'REPORT_OTHER', NULL, 4, 1),
    ('REP_STATISTICAL_DISTRIBUTION',        'HUB_STATISTICAL', N'Distribution Statistics', N'إحصائيات التوزيع',     N'Statistical distribution of students by country, major, gender, and academic level',                       N'التوزيع الإحصائي للطلاب حسب الدولة والتخصص والجنس والمستوى الأكاديمي',                       'STATISTICAL', '/reports/statistical/distribution-statistics',      NULL, 'pi-percentage',     'indigo', 'REPORT_OTHER', NULL, 5, 1),
    ('REP_STATISTICAL_PREDICTIVE',          'HUB_STATISTICAL', N'Predictive Analytics',   N'التحليلات التنبؤية',    N'Forecast future trends based on historical data and statistical models',                                   N'التنبؤ بالاتجاهات المستقبلية بناءً على البيانات التاريخية والنماذج الإحصائية',               'STATISTICAL', '/reports/statistical/predictive-analytics',         NULL, 'pi-sparkles',       'cyan',   'REPORT_OTHER', NULL, 6, 1),
    ('REP_STATISTICAL_STUDENT_EXTENSION',   'HUB_STATISTICAL', N'Student Extension',      N'تمديد الطلاب',          N'Manage and track scholarship extension decisions, approvals, and student extension requests',               N'إدارة وتتبع قرارات تمديد المنح الدراسية والموافقات وطلبات تمديد الطلاب',                     'STATISTICAL', '/reports/statistical/student-extension-statistics', NULL, 'pi-calendar-plus',  'pink',   'REPORT_OTHER', NULL, 7, 1),
    ('REP_STATISTICAL_ATTACHE_SUMMARY',     'HUB_STATISTICAL', N'Attaché Summary',        N'ملخص الملحقية',         N'One line per attaché: students, finance, banks, and the rules it has set for itself.',                      N'سطر لكل ملحقية: الطلاب والمالية والبنوك والقواعد التي حدّدتها لنفسها.',                      'STATISTICAL', '/reports/statistical/attache-summary',              NULL, 'pi-globe',          'teal',   'REPORT_OTHER', NULL, 8, 1),

    -- ---------------------------------------------------------------- HUB_PERIODIC (periodic-reports.component.ts)
    ('REP_PERIODIC_MONTHLY',       'HUB_PERIODIC', N'Monthly Reports',      N'التقارير الشهرية',      N'Generate monthly summaries of student activities, financial transactions, and operational metrics',    N'إنشاء ملخصات شهرية لأنشطة الطلاب والمعاملات المالية والمقاييس التشغيلية',        'PERIODIC', '/reports/periodic/monthly',       NULL, 'pi-calendar',       'blue',   'REPORT_OTHER', NULL, 1, 1),
    ('REP_PERIODIC_QUARTERLY',     'HUB_PERIODIC', N'Quarterly Reports',    N'التقارير الربعية',      N'Comprehensive quarterly reports for financial reviews, academic progress, and strategic planning',     N'تقارير ربع سنوية شاملة للمراجعات المالية والتقدم الأكاديمي والتخطيط الاستراتيجي', 'PERIODIC', '/reports/periodic/quarterly',     NULL, 'pi-calendar-plus',  'green',  'REPORT_OTHER', NULL, 2, 1),
    ('REP_PERIODIC_ANNUAL',        'HUB_PERIODIC', N'Annual Reports',       N'التقارير السنوية',      N'Yearly comprehensive reports covering all aspects of operations, finances, and achievements',          N'تقارير سنوية شاملة تغطي جميع جوانب العمليات والماليات والإنجازات',              'PERIODIC', '/reports/periodic/annual',        NULL, 'pi-book',           'orange', 'REPORT_OTHER', NULL, 3, 1),
    ('REP_PERIODIC_ACADEMIC_YEAR', 'HUB_PERIODIC', N'Academic Year Report', N'تقرير العام الأكاديمي', N'End-of-academic-year reports with student outcomes, graduations, and program effectiveness',           N'تقارير نهاية العام الأكاديمي مع نتائج الطلاب والتخرج وفعالية البرنامج',           'PERIODIC', '/reports/periodic/academic-year', NULL, 'pi-graduation-cap', 'purple', 'REPORT_OTHER', NULL, 4, 1),
    ('REP_PERIODIC_FISCAL_YEAR',   'HUB_PERIODIC', N'Fiscal Year Report',   N'تقرير السنة المالية',   N'Complete fiscal year financial statements, budget execution, and audit reports',                       N'البيانات المالية الكاملة للسنة المالية وتنفيذ الميزانية وتقارير التدقيق',        'PERIODIC', '/reports/periodic/fiscal-year',   NULL, 'pi-dollar',         'indigo', 'REPORT_OTHER', NULL, 5, 1),
    ('REP_PERIODIC_CUSTOM_PERIOD', 'HUB_PERIODIC', N'Custom Period Report', N'تقرير فترة مخصصة',      N'Generate reports for any custom date range based on your specific requirements',                       N'إنشاء تقارير لأي نطاق تاريخ مخصص بناءً على متطلباتك المحددة',                    'PERIODIC', '/reports/periodic/custom-period', NULL, 'pi-sliders-h',      'cyan',   'REPORT_OTHER', NULL, 6, 1),

    -- ---------------------------------------------------------------- HUB_UNIVERSITY (university-reports.component.ts); SortOrder 3 is the existing REP_UNIVERSITY_PERFORMANCE
    ('REP_UNIVERSITY_PARTNERSHIPS',         'HUB_UNIVERSITY', N'University Partnerships',           N'الشراكات الجامعية',          N'Track partnerships, agreements, and collaborative programs with universities worldwide',   N'تتبع الشراكات والاتفاقيات والبرامج التعاونية مع الجامعات في جميع أنحاء العالم', 'UNIVERSITY', '/reports/university/partnerships',         NULL, 'pi-building',  'blue',   'REPORT_OTHER', NULL, 1, 1),
    ('REP_UNIVERSITY_FEE_STRUCTURE',        'HUB_UNIVERSITY', N'Fee Structure Analysis',            N'تحليل هيكل الرسوم',          N'Analyze university fees, tuition costs, and payment structures across institutions',       N'تحليل رسوم الجامعة وتكاليف الرسوم الدراسية وهياكل الدفع عبر المؤسسات',        'UNIVERSITY', '/reports/university/fee-structure',        NULL, 'pi-dollar',    'green',  'REPORT_OTHER', NULL, 2, 1),
    ('REP_UNIVERSITY_PROGRAM_CATALOG',      'HUB_UNIVERSITY', N'Program Catalog',                   N'دليل البرامج',               N'Comprehensive catalog of available programs, majors, and specializations by university',   N'دليل شامل للبرامج والتخصصات والتخصصات الفرعية المتاحة حسب الجامعة',            'UNIVERSITY', '/reports/university/program-catalog',      NULL, 'pi-list',      'purple', 'REPORT_OTHER', NULL, 4, 1),
    ('REP_UNIVERSITY_STUDENT_DISTRIBUTION', 'HUB_UNIVERSITY', N'Student Distribution by University', N'توزيع الطلاب حسب الجامعة', N'View student enrollment numbers and distribution across different universities',           N'عرض أعداد تسجيل الطلاب والتوزيع عبر الجامعات المختلفة',                        'UNIVERSITY', '/reports/university/student-distribution', NULL, 'pi-chart-pie', 'indigo', 'REPORT_OTHER', NULL, 5, 1),
    ('REP_UNIVERSITY_AGREEMENTS',           'HUB_UNIVERSITY', N'University Agreements Report',      N'تقرير الاتفاقيات الجامعية',  N'Detailed reports on bilateral agreements, terms, conditions, and renewal status',          N'تقارير تفصيلية عن الاتفاقيات الثنائية والشروط والظروف وحالة التجديد',          'UNIVERSITY', '/reports/university/agreements',           NULL, 'pi-file-edit', 'cyan',   'REPORT_OTHER', NULL, 6, 1),

    -- ---------------------------------------------------------------- HUB_OPERATIONS (operations-reports.component.ts)
    ('REP_OPERATIONS_AUDIT_TRAIL', 'HUB_OPERATIONS', N'Audit Trail',            N'سجل التدقيق',          N'Who changed what, and when — for a chosen period, table, record or user.',                      N'من غيّر ماذا ومتى — لفترة أو جدول أو سجل أو مستخدم محدد.',              'OPERATIONS', '/reports/operations/audit-trail', NULL, 'pi-history',      'blue',   'REPORT_OTHER', NULL, 1, 1),
    ('REP_OPERATIONS_APPROVALS',   'HUB_OPERATIONS', N'Approvals and Requests', N'الاعتمادات والطلبات',  N'What is waiting, with whom, and for how long — plus the requests already decided.',              N'ما هو قيد الانتظار، ولدى من، ومنذ متى — إضافة إلى الطلبات المنتهية.',    'OPERATIONS', '/reports/operations/approvals',   NULL, 'pi-check-square', 'orange', 'REPORT_OTHER', NULL, 2, 1),

    -- ---------------------------------------------------------------- HUB_CUSTOM (custom-reports.component.ts)
    ('REP_CUSTOM_FILTER',             'HUB_CUSTOM', N'Custom Filter Report', N'تقرير مخصص بالفلاتر', N'Create reports with custom filters based on any combination of criteria and parameters', N'إنشاء تقارير بفلاتر مخصصة بناءً على أي مجموعة من المعايير والمعاملات',       'CUSTOM', '/reports/custom/custom-filter',      NULL, 'pi-filter',    'blue',   'REPORT_OTHER', NULL, 1, 1),
    ('REP_CUSTOM_QUERY_BUILDER',      'HUB_CUSTOM', N'Query Builder',        N'منشئ الاستعلامات',    N'Build complex queries using an intuitive interface to extract specific data sets',       N'بناء استعلامات معقدة باستخدام واجهة بديهية لاستخراج مجموعات بيانات محددة',   'CUSTOM', '/reports/custom/query-builder',      NULL, 'pi-search',    'green',  'REPORT_OTHER', NULL, 2, 1),
    ('REP_CUSTOM_EXPORT_MANAGER',     'HUB_CUSTOM', N'Export Manager',       N'مدير التصدير',        N'Export reports to various formats including Excel, PDF, CSV, and more',                  N'تصدير التقارير إلى تنسيقات مختلفة بما في ذلك Excel و PDF و CSV والمزيد',    'CUSTOM', '/reports/custom/export-manager',     NULL, 'pi-download',  'orange', 'REPORT_OTHER', NULL, 3, 1),
    ('REP_CUSTOM_SCHEDULED',          'HUB_CUSTOM', N'Scheduled Reports',    N'التقارير المجدولة',   N'Schedule automated report generation and distribution to stakeholders',                  N'جدولة إنشاء التقارير التلقائي والتوزيع على أصحاب المصلحة',                   'CUSTOM', '/reports/custom/scheduled-reports',  NULL, 'pi-clock',     'purple', 'REPORT_OTHER', NULL, 4, 1),
    ('REP_CUSTOM_TEMPLATES',          'HUB_CUSTOM', N'Report Templates',     N'قوالب التقارير',      N'Save and reuse custom report configurations as templates for quick access',              N'حفظ وإعادة استخدام تكوينات التقارير المخصصة كقوالب للوصول السريع',           'CUSTOM', '/reports/custom/report-templates',   NULL, 'pi-file-edit', 'indigo', 'REPORT_OTHER', NULL, 5, 1),
    ('REP_CUSTOM_DATA_VISUALIZATION', 'HUB_CUSTOM', N'Data Visualization',   N'تصور البيانات',       N'Create custom charts, graphs, and visual representations of your data',                  N'إنشاء مخططات ورسوم بيانية وتمثيلات بصرية مخصصة لبياناتك',                    'CUSTOM', '/reports/custom/data-visualization', NULL, 'pi-chart-bar', 'cyan',   'REPORT_OTHER', NULL, 6, 1)
) v (ReportCode, ParentCode, ReportName, ReportNameAr, Description, DescriptionAr, Category, Route, QueryParams, Icon, Color, PageCode, PermissionCodes, SortOrder, IsActive)
WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.UMS_Reports r WHERE r.ReportCode = v.ReportCode);

------------------------------------------------------------------------------------------------------------
-- 3. DUPLICATE-ROUTE rows. The Financial hub lists the two financial-data-form pages again (positions 1-2),
--    with its own hub-flavoured texts and hub-convention colours. Their routes are identical to
--    REP_FINANCIAL_DATA_FORM / REP_FINANCIAL_DATA_FORM_V2 on the landing page. A row can carry only one
--    ParentCode, so these are separate codes; delete this block if the catalogue should list each route once
--    (the hub then has SortOrder gaps at 1-2).
------------------------------------------------------------------------------------------------------------
INSERT INTO UMSCORE.UMS_Reports
    (ReportCode, ParentCode, ReportName, ReportNameAr, Description, DescriptionAr, Category, Route, QueryParams, Icon, Color, PageCode, PermissionCodes, SortOrder, IsActive, CreatedAt, UpdatedAt)
SELECT v.ReportCode, v.ParentCode, v.ReportName, v.ReportNameAr, v.Description, v.DescriptionAr, v.Category, v.Route, v.QueryParams, v.Icon, v.Color, v.PageCode, v.PermissionCodes, v.SortOrder, v.IsActive, SYSUTCDATETIME(), SYSUTCDATETIME()
FROM (VALUES
    ('REP_FINANCIAL_DATA_FORM_HUB',    'HUB_FINANCIAL', N'Financial Data Form',                         N'إستمارة صرف مُستحقات مالية',                                  N'Official financial disbursement forms for student assistance payments by quarter and year',                                                                                                                       N'نماذج الصرف المالي الرسمية لمدفوعات مساعدة الطلاب حسب الربع والسنة',                                                                                     'FINANCIAL', '/reports/financial-data-form',    NULL, 'pi-file',       'green', NULL, 'REPORT_FINANCE,FINANCE', 1, 1),
    ('REP_FINANCIAL_DATA_FORM_V2_HUB', 'HUB_FINANCIAL', N'Financial Data Form V2 — Quarterly Statement', N'إستمارة صرف مُستحقات مالية V2 — كشوفات المساعدة المالية الربعية', N'The quarterly financial assistance statement in the ministry''s own layout: students grouped by sponsoring entity with delegation, level, first payment, amount, signature and notes columns', N'كشوفات المساعدة المالية الربعية بتنسيق الوزارة: الطلاب مجمّعون حسب الجهة مع أعمدة الإيفاد والمستوى وأول مصرف والمبلغ والتوقيع والملاحظات', 'FINANCIAL', '/reports/financial-data-form-v2', NULL, 'pi-file-check', 'teal',  NULL, 'REPORT_FINANCE,FINANCE', 2, 1)
) v (ReportCode, ParentCode, ReportName, ReportNameAr, Description, DescriptionAr, Category, Route, QueryParams, Icon, Color, PageCode, PermissionCodes, SortOrder, IsActive)
WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.UMS_Reports r WHERE r.ReportCode = v.ReportCode);

DECLARE @catAfter INT = (SELECT COUNT(*) FROM UMSCORE.UMS_Reports);
PRINT 'V073: catalogue rows inserted = ' + CAST(@catAfter - @catBefore AS VARCHAR(10)) + ', total = ' + CAST(@catAfter AS VARCHAR(10));

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_UMS_Reports_Parent')
    ALTER TABLE UMSCORE.UMS_Reports WITH CHECK
        ADD CONSTRAINT FK_UMS_Reports_Parent FOREIGN KEY (ParentCode) REFERENCES UMSCORE.UMS_Reports (ReportCode);
GO

/* ── 5. Verification ─────────────────────────────────────────────────────── */
PRINT '=== V073 verification ===';
SELECT t.name AS new_table, p.rows
  FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
  JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
 WHERE s.name = 'MOHECORE' AND t.name IN ('LU_ALERT_TYPE', 'LU_WAFAR_BENEFICIARY_TYPE', 'LU_NOTIFICATION_SEND_STATUS',
       'LU_APPROVAL_ACTION', 'LU_ACCOUNT_NATURE', 'LU_ALLOCATION_TYPE', 'LU_ALLOCATION_STATUS', 'LU_PAYMENT_BATCH_STATUS',
       'LU_APPROPRIATION_STATUS', 'LU_FISCAL_YEAR_STATUS', 'LU_FISCAL_PERIOD_STATUS', 'LU_INQUIRY_TYPE', 'LU_INQUIRY_STATUS',
       'LU_INFO_REQUEST_STATUS', 'LU_INFO_REVIEW_STATUS', 'LU_GRAPH_CATEGORY', 'CHANGE_REQUEST_FIELD')
 ORDER BY t.name;

SELECT ISNULL(ParentCode, '(hub / landing)') AS parent, COUNT(*) AS cards, SUM(CASE WHEN IsActive = 1 THEN 1 ELSE 0 END) AS active
  FROM UMSCORE.UMS_Reports
 GROUP BY ParentCode
 ORDER BY CASE WHEN ParentCode IS NULL THEN 0 ELSE 1 END, ParentCode;

SELECT 'orphans' AS what, COUNT(*) AS n FROM UMSCORE.UMS_Reports r
 WHERE r.ParentCode IS NOT NULL AND NOT EXISTS (SELECT 1 FROM UMSCORE.UMS_Reports h WHERE h.ReportCode = r.ParentCode);
GO
