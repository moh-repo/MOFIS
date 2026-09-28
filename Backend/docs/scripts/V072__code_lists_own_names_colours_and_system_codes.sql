/* ============================================================================
   V072 — The code lists own their names, colours, icons and "system" codes.

   Why
   ---
   V003/V004 created ~40 bilingual MOHECORE.LU_* tables, and the application
   then went on hard-coding the same codes: status literals inside JPQL,
   colour and icon switch statements in the Angular components (the same four
   severities in four files), bilingual display names duplicated on Java enums,
   and ids compared as numbers (status_id = 1, = 3, = 99) on tables that have
   no CODE column. The owner's decision (30 Aug 2026): the database owns the
   catalogue — which codes exist, their names, their order, their colour and
   icon — and Java keeps only the constants its behaviour depends on, verified
   against the tables at startup.

   What
   ----
   1. Every standard-shaped LU table (CODE, NAME_AR, NAME_EN, SORT_ORDER,
      IS_DELETED) gains UI_SEVERITY (the PrimeNG tag severity: success, info,
      warn, danger, secondary, contrast), ICON (a PrimeIcons name) and
      IS_SYSTEM (1 = a Java constant depends on this code; the code-list API
      refuses to delete or rename it, and the startup guard proves the set of
      system codes equals the set of Java constants). A unique index on CODE
      where none exists. LU_TRAVEL_TYPE, LU_FAMILY_RELATIONSHIP and
      LU_TRAVEL_FUNDING_SOURCE keep their own shape and are not touched.
   2. Seeds UI_SEVERITY / ICON exactly as the switch statements rendered them
      before this script, only where the value is still NULL (an admin's later
      edit survives a re-run); IS_SYSTEM is set on the codes Java names.
      One correction on purpose: 'warning' (not a PrimeNG 21 severity) becomes
      'warn'.
   3. CODE columns on the tables whose ids the code compared:
      STUDY_FIN_RECEIVED_STATUS, STUDY_PAY_FEE_IN_STATUS (every row coded),
      STUDENT_UNIVERSITY_TYPE and STUDENT_MINISTRY_TYPE (only the
      "not specified" sentinel row 99 — the others are names, not codes).
      STUDENT_STUDY_STATUS.UI_COLOR for the dashboard donut, seeded for all
      nine rows. STUDY_CURRENCY_IN.IS_DEFAULT on the ministry's dollar row.
   4. MOHECORE.SYS_PARAMETER — typed, named, described parameters that were
      constants in Java: the dashboard windows, the document-expiry alert
      ladder, the audit-trail default window, the duplicate-student
      similarity floor.
   5. MOHECORE.DOCUMENT_TYPES seeded by name with NOT EXISTS (the eleven rows
      dev already holds), so DocumentTypeService.initializeDefaultDocumentTypes
      — ten English-only rows generated at runtime — can be deleted.

   Rollback
   --------
     Each column added here can be dropped (ALTER TABLE ... DROP COLUMN);
     DROP TABLE MOHECORE.SYS_PARAMETER; the document-type rows this script
     inserted are the ones whose DOCUMENT_TYPE_NAME_EN is listed in step 5
     and did not exist before. The application refuses to boot without the
     columns of step 1 (CodeListStartupGuard), so roll the code back first.

   Idempotent. Run with sqlcmd -I -f 65001 -b (see the runbook).
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ── 1. UI_SEVERITY, ICON, IS_SYSTEM and a unique CODE on every LU table ──── */
PRINT '=== V072 step 1: columns on the standard LU tables ===';

DECLARE @tables TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @tables (name)
SELECT t.name
  FROM sys.tables t
  JOIN sys.schemas s ON s.schema_id = t.schema_id
 WHERE s.name = 'MOHECORE'
   AND t.name LIKE 'LU[_]%'
   AND COL_LENGTH('MOHECORE.' + t.name, 'CODE')       IS NOT NULL
   AND COL_LENGTH('MOHECORE.' + t.name, 'NAME_AR')    IS NOT NULL
   AND COL_LENGTH('MOHECORE.' + t.name, 'NAME_EN')    IS NOT NULL
   AND COL_LENGTH('MOHECORE.' + t.name, 'SORT_ORDER') IS NOT NULL
   AND COL_LENGTH('MOHECORE.' + t.name, 'IS_DELETED') IS NOT NULL;

DECLARE @t SYSNAME, @sql NVARCHAR(MAX), @added INT = 0, @indexed INT = 0;
DECLARE tcur CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM @tables ORDER BY name;
OPEN tcur;
FETCH NEXT FROM tcur INTO @t;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF COL_LENGTH('MOHECORE.' + @t, 'UI_SEVERITY') IS NULL
    BEGIN
        SET @sql = N'ALTER TABLE MOHECORE.' + QUOTENAME(@t) + N' ADD UI_SEVERITY NVARCHAR(20) NULL;';
        EXEC sp_executesql @sql;
        SET @added += 1;
    END
    IF COL_LENGTH('MOHECORE.' + @t, 'ICON') IS NULL
    BEGIN
        SET @sql = N'ALTER TABLE MOHECORE.' + QUOTENAME(@t) + N' ADD ICON NVARCHAR(50) NULL;';
        EXEC sp_executesql @sql;
        SET @added += 1;
    END
    IF COL_LENGTH('MOHECORE.' + @t, 'IS_SYSTEM') IS NULL
    BEGIN
        SET @sql = N'ALTER TABLE MOHECORE.' + QUOTENAME(@t)
                 + N' ADD IS_SYSTEM BIT NOT NULL CONSTRAINT DF_' + @t + N'_IS_SYSTEM DEFAULT 0;';
        EXEC sp_executesql @sql;
        SET @added += 1;
    END

    /* A unique CODE where no unique index already covers CODE (the composite
       ones — FEE_SCOPE, REPORT_DIMENSION, TREND_CLASS — already do). Skipped,
       and said so, when the table holds duplicate codes. */
    IF NOT EXISTS (SELECT 1
                     FROM sys.indexes i
                     JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                     JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE i.object_id = OBJECT_ID('MOHECORE.' + @t) AND i.is_unique = 1 AND c.name = 'CODE')
    BEGIN
        SET @sql = N'IF EXISTS (SELECT CODE FROM MOHECORE.' + QUOTENAME(@t) + N' GROUP BY CODE HAVING COUNT(*) > 1) '
                 + N'    PRINT ''V072: ' + @t + N' holds duplicate codes — no unique index added, fix the data first''; '
                 + N'ELSE CREATE UNIQUE INDEX UQ_' + @t + N'_CODE ON MOHECORE.' + QUOTENAME(@t) + N' (CODE);';
        EXEC sp_executesql @sql;
        SET @indexed += 1;
    END
    FETCH NEXT FROM tcur INTO @t;
END
CLOSE tcur; DEALLOCATE tcur;
DECLARE @inScope INT = (SELECT COUNT(*) FROM @tables);
PRINT 'V072: LU tables in scope = ' + CAST(@inScope AS VARCHAR(10))
    + ', columns added = ' + CAST(@added AS VARCHAR(10))
    + ', unique CODE indexes attempted = ' + CAST(@indexed AS VARCHAR(10));

/* ── 2. Severity, icon and system flags, as the code rendered them ────────── */
PRINT '=== V072 step 2: severity / icon / system seeds ===';

DECLARE @seed TABLE (tbl SYSNAME, code NVARCHAR(50), sev NVARCHAR(20) NULL, icon NVARCHAR(50) NULL, sys BIT);
INSERT INTO @seed (tbl, code, sev, icon, sys) VALUES
  -- alert-events-cases / -scenarios severityClass, alert.ts getAlertPriorityColor, DocumentExpiryScheduler ladder
  ('LU_ALERT_SEVERITY', 'CRITICAL', 'danger',    'pi-exclamation-circle',   1),
  ('LU_ALERT_SEVERITY', 'HIGH',     'warn',      'pi-exclamation-triangle', 1),
  ('LU_ALERT_SEVERITY', 'MEDIUM',   'info',      'pi-info-circle',          1),
  ('LU_ALERT_SEVERITY', 'LOW',      'success',   'pi-check-circle',         1),
  -- alert-events-cases statusClass; AlertEventCaseService transitions
  ('LU_ALERT_STATUS', 'OPEN',         'danger',    'pi-exclamation-circle', 1),
  ('LU_ALERT_STATUS', 'ACKNOWLEDGED', 'warn',      'pi-eye',                1),
  ('LU_ALERT_STATUS', 'RESOLVED',     'success',   'pi-check',              1),
  ('LU_ALERT_STATUS', 'DISMISSED',    'secondary', 'pi-times',              1),
  -- approval.service getStatusColor; ApprovalStatus enum (JPA @Enumerated)
  ('LU_APPROVAL_STATUS', 'PENDING',     'warn',    'pi-clock',   1),
  ('LU_APPROVAL_STATUS', 'IN_PROGRESS', 'info',    'pi-spinner', 1),
  ('LU_APPROVAL_STATUS', 'APPROVED',    'success', 'pi-check',   1),
  ('LU_APPROVAL_STATUS', 'REJECTED',    'danger',  'pi-times',   1),
  ('LU_APPROVAL_STATUS', 'EXECUTED',    'success', 'pi-verified', 1),
  -- approval-history / my-requests / pending-approvals / audit-trail; RequestType enum
  ('LU_APPROVAL_REQUEST_TYPE', 'INSERT', 'success', 'pi-plus',   1),
  ('LU_APPROVAL_REQUEST_TYPE', 'UPDATE', 'info',    'pi-pencil', 1),
  ('LU_APPROVAL_REQUEST_TYPE', 'DELETE', 'danger',  'pi-trash',  1),
  -- wafar.component getStatusSeverity; WafarSpendingRepository predicates
  ('LU_WAFAR_SPENDING_STATUS', 'PENDING',   'warn',      'pi-clock', 1),
  ('LU_WAFAR_SPENDING_STATUS', 'APPROVED',  'success',   'pi-check', 1),
  ('LU_WAFAR_SPENDING_STATUS', 'REJECTED',  'danger',    'pi-times', 1),
  ('LU_WAFAR_SPENDING_STATUS', 'CANCELLED', 'secondary', 'pi-ban',   1),
  -- wafar-reconciliation statusColor; WafarReconciliationService
  ('LU_WAFAR_RECONCILIATION_STATUS', 'PENDING',     'warn',    'pi-clock',                1),
  ('LU_WAFAR_RECONCILIATION_STATUS', 'MATCHED',     'success', 'pi-check',                1),
  ('LU_WAFAR_RECONCILIATION_STATUS', 'DISCREPANCY', 'danger',  'pi-exclamation-triangle', 1),
  ('LU_WAFAR_RECONCILIATION_STATUS', 'RESOLVED',    'info',    'pi-verified',             1),
  -- payment-authorization getStatusSeverity; PaymentAuthService
  ('LU_PAYMENT_AUTH_STATUS', 'DRAFT',    'warn',    'pi-file-edit', 1),
  ('LU_PAYMENT_AUTH_STATUS', 'APPROVED', 'success', 'pi-check',     1),
  ('LU_PAYMENT_AUTH_STATUS', 'PAID',     'info',    'pi-wallet',    1),
  -- student-change-requests statusSeverity; StudentChangeRequestService
  ('LU_CHANGE_REQUEST_STATUS', 'PENDING',     'warn',    'pi-clock',   1),
  ('LU_CHANGE_REQUEST_STATUS', 'APPROVED',    'success', 'pi-check',   1),
  ('LU_CHANGE_REQUEST_STATUS', 'REJECTED',    'danger',  'pi-times',   1),
  ('LU_CHANGE_REQUEST_STATUS', 'MODIFIED',    'info',    'pi-pencil',  0),
  ('LU_CHANGE_REQUEST_STATUS', 'IN_PROGRESS', 'info',    'pi-spinner', 0),
  ('LU_CHANGE_REQUEST_STATUS', 'EXECUTED',    'success', 'pi-verified', 0),
  -- StudentUserAdminController / StudentUser default
  ('LU_STUDENT_PORTAL_ACCOUNT_STATUS', 'ACTIVE',   'success',   'pi-check', 1),
  ('LU_STUDENT_PORTAL_ACCOUNT_STATUS', 'INACTIVE', 'secondary', 'pi-ban',   1),
  ('LU_STUDENT_PORTAL_ACCOUNT_STATUS', 'LOCKED',   'danger',    'pi-lock',  1),
  -- UmsUserService lock / activate
  ('LU_USER_STATUS', 'ACTIVE',   'success',   'pi-check', 1),
  ('LU_USER_STATUS', 'INACTIVE', 'secondary', 'pi-ban',   1),
  ('LU_USER_STATUS', 'LOCKED',   'danger',    'pi-lock',  1),
  -- student-entitlements statusSeverity (numeric codes; not named in Java)
  ('LU_LOAN_STATUS', '0', 'warn',    NULL, 0),
  ('LU_LOAN_STATUS', '1', 'success', NULL, 0),
  ('LU_LOAN_STATUS', '2', 'success', NULL, 0),
  ('LU_LOAN_STATUS', '3', 'danger',  NULL, 0),
  ('LU_BOOKS_ALLOWANCE_PAY_STATUS', '0', 'warn',    NULL, 0),
  ('LU_BOOKS_ALLOWANCE_PAY_STATUS', '1', 'success', NULL, 0),
  ('LU_BOOKS_ALLOWANCE_PAY_STATUS', '2', 'danger',  NULL, 0),
  ('LU_UNIVERSITY_FEE_STATUS', '0', 'warn',      NULL, 0),
  ('LU_UNIVERSITY_FEE_STATUS', '1', 'success',   NULL, 0),
  ('LU_UNIVERSITY_FEE_STATUS', '2', 'danger',    NULL, 0),
  ('LU_UNIVERSITY_FEE_STATUS', '3', 'secondary', NULL, 0),
  -- notification-history channelIcon
  ('LU_NOTIFICATION_CHANNEL', 'EMAIL', 'info', 'pi-envelope', 0),
  ('LU_NOTIFICATION_CHANNEL', 'SMS',   'info', 'pi-mobile',   0),
  ('LU_NOTIFICATION_CHANNEL', 'BOTH',  'info', 'pi-send',     0),
  -- api-link-management methodColor
  ('LU_HTTP_METHOD', 'GET',    'info',      NULL, 0),
  ('LU_HTTP_METHOD', 'POST',   'success',   NULL, 0),
  ('LU_HTTP_METHOD', 'PUT',    'warn',      NULL, 0),
  ('LU_HTTP_METHOD', 'DELETE', 'danger',    NULL, 0),
  ('LU_HTTP_METHOD', 'PATCH',  'secondary', NULL, 0),
  ('LU_STATUS_ACTIVE', '1', 'success',   NULL, 0),
  ('LU_STATUS_ACTIVE', '0', 'secondary', NULL, 0),
  -- quarter names: the Java switches name 1..4
  ('LU_QUARTER_NUMBER', '1', NULL, NULL, 1),
  ('LU_QUARTER_NUMBER', '2', NULL, NULL, 1),
  ('LU_QUARTER_NUMBER', '3', NULL, NULL, 1),
  ('LU_QUARTER_NUMBER', '4', NULL, NULL, 1),
  -- the three labels ReportParamBuilder already read from the table
  ('LU_REPORT_LABEL', 'LBL_GENERATED', NULL, NULL, 1),
  ('LU_REPORT_LABEL', 'LBL_PAGE',      NULL, NULL, 1),
  ('LU_REPORT_LABEL', 'LBL_OF',        NULL, NULL, 1);

DECLARE @code NVARCHAR(50), @sev NVARCHAR(20), @icon NVARCHAR(50), @sys BIT, @missing INT = 0, @updated INT = 0;
DECLARE scur CURSOR LOCAL FAST_FORWARD FOR SELECT tbl, code, sev, icon, sys FROM @seed;
OPEN scur;
FETCH NEXT FROM scur INTO @t, @code, @sev, @icon, @sys;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'UPDATE MOHECORE.' + QUOTENAME(@t)
             + N' SET UI_SEVERITY = COALESCE(UI_SEVERITY, @sev),'
             + N'     ICON        = COALESCE(ICON, @icon),'
             + N'     IS_SYSTEM   = CASE WHEN @sys = 1 THEN 1 ELSE IS_SYSTEM END'
             + N' WHERE CODE = @code;'
             + N' SELECT @n = @@ROWCOUNT;';
    DECLARE @n INT;
    EXEC sp_executesql @sql, N'@sev NVARCHAR(20), @icon NVARCHAR(50), @sys BIT, @code NVARCHAR(50), @n INT OUTPUT',
         @sev = @sev, @icon = @icon, @sys = @sys, @code = @code, @n = @n OUTPUT;
    IF @n = 0
    BEGIN
        PRINT 'V072: no row ' + @t + '.' + @code + ' — the seed expected it; check the table';
        SET @missing += 1;
    END
    ELSE SET @updated += @n;
    FETCH NEXT FROM scur INTO @t, @code, @sev, @icon, @sys;
END
CLOSE scur; DEALLOCATE scur;
PRINT 'V072: seed rows applied = ' + CAST(@updated AS VARCHAR(10)) + ', missing = ' + CAST(@missing AS VARCHAR(10));
IF @missing > 0
    THROW 50072, 'V072: a code the application depends on is missing from its LU table (see the PRINT lines above).', 1;

/* ── 3. CODE columns where the code compared ids ─────────────────────────── */
PRINT '=== V072 step 3: CODE columns, UI_COLOR, IS_DEFAULT ===';

IF COL_LENGTH('MOHECORE.STUDY_FIN_RECEIVED_STATUS', 'CODE') IS NULL
    ALTER TABLE MOHECORE.STUDY_FIN_RECEIVED_STATUS ADD CODE NVARCHAR(50) NULL;
IF COL_LENGTH('MOHECORE.STUDY_PAY_FEE_IN_STATUS', 'CODE') IS NULL
    ALTER TABLE MOHECORE.STUDY_PAY_FEE_IN_STATUS ADD CODE NVARCHAR(50) NULL;
IF COL_LENGTH('MOHECORE.STUDENT_UNIVERSITY_TYPE', 'CODE') IS NULL
    ALTER TABLE MOHECORE.STUDENT_UNIVERSITY_TYPE ADD CODE NVARCHAR(50) NULL;
IF COL_LENGTH('MOHECORE.STUDENT_MINISTRY_TYPE', 'CODE') IS NULL
    ALTER TABLE MOHECORE.STUDENT_MINISTRY_TYPE ADD CODE NVARCHAR(50) NULL;
IF COL_LENGTH('MOHECORE.STUDENT_STUDY_STATUS', 'UI_COLOR') IS NULL
    ALTER TABLE MOHECORE.STUDENT_STUDY_STATUS ADD UI_COLOR NVARCHAR(9) NULL;
IF COL_LENGTH('MOHECORE.STUDY_CURRENCY_IN', 'IS_DEFAULT') IS NULL
    ALTER TABLE MOHECORE.STUDY_CURRENCY_IN ADD IS_DEFAULT BIT NOT NULL CONSTRAINT DF_STUDY_CURRENCY_IN_IS_DEFAULT DEFAULT 0;
GO

UPDATE s SET CODE = v.code
  FROM MOHECORE.STUDY_FIN_RECEIVED_STATUS s
  JOIN (VALUES (1, N'RECEIVED'), (2, N'NOT_RECEIVED'), (99, N'NOT_SPECIFIED'), (100, N'DISBURSED'),
               (101, N'PARTIALLY_DISBURSED'), (102, N'UNDER_SETTLEMENT'), (103, N'REJECTED'),
               (104, N'RETURNED'), (105, N'ON_HOLD')) v(id, code)
    ON v.id = s.STUDY_FIN_RECEIVED_STATUS_ID
 WHERE s.CODE IS NULL;

UPDATE s SET CODE = v.code
  FROM MOHECORE.STUDY_PAY_FEE_IN_STATUS s
  JOIN (VALUES (1, N'PAID'), (2, N'NOT_PAID'), (3, N'CANCELLED'), (4, N'PARTIAL'), (5, N'ON_HOLD'),
               (99, N'NOT_SPECIFIED')) v(id, code)
    ON v.id = s.STUDY_PAY_FEE_IN_STATUS_ID
 WHERE s.CODE IS NULL;

UPDATE MOHECORE.STUDENT_UNIVERSITY_TYPE SET CODE = N'NOT_SPECIFIED' WHERE STUDENT_UNIVERSITY_TYPE_ID = 99 AND CODE IS NULL;
UPDATE MOHECORE.STUDENT_MINISTRY_TYPE   SET CODE = N'NOT_SPECIFIED' WHERE STUDENT_MINISTRY_TYPE_ID   = 99 AND CODE IS NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDY_FIN_RECEIVED_STATUS_CODE')
    CREATE UNIQUE INDEX UQ_STUDY_FIN_RECEIVED_STATUS_CODE ON MOHECORE.STUDY_FIN_RECEIVED_STATUS (CODE) WHERE CODE IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDY_PAY_FEE_IN_STATUS_CODE')
    CREATE UNIQUE INDEX UQ_STUDY_PAY_FEE_IN_STATUS_CODE ON MOHECORE.STUDY_PAY_FEE_IN_STATUS (CODE) WHERE CODE IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDENT_UNIVERSITY_TYPE_CODE')
    CREATE UNIQUE INDEX UQ_STUDENT_UNIVERSITY_TYPE_CODE ON MOHECORE.STUDENT_UNIVERSITY_TYPE (CODE) WHERE CODE IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_STUDENT_MINISTRY_TYPE_CODE')
    CREATE UNIQUE INDEX UQ_STUDENT_MINISTRY_TYPE_CODE ON MOHECORE.STUDENT_MINISTRY_TYPE (CODE) WHERE CODE IS NOT NULL;

/* The donut colours — one per status row, so a slice keeps its colour whatever
   the sort order of the counts. Theme-neutral hex values. */
UPDATE s SET UI_COLOR = v.color
  FROM MOHECORE.STUDENT_STUDY_STATUS s
  JOIN (VALUES (N'DECEASED', N'#607D8B'), (N'ACTIVE', N'#22C55E'), (N'NEW', N'#3B82F6'),
               (N'WITHDRAWN', N'#EF4444'), (N'SUSPENDED', N'#F97316'), (N'TRANSFERRED', N'#A855F7'),
               (N'UNDER_PROCESSING', N'#06B6D4'), (N'STRUGGLING', N'#EAB308'), (N'NOT_SPECIFIED', N'#94A3B8')) v(code, color)
    ON v.code = s.CODE
 WHERE s.UI_COLOR IS NULL;

/* The ministry's dollar row (id 1): the one every student amount resolves to
   (V034), and the one the wafar screen used to find by fuzzy name. */
IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_CURRENCY_IN WHERE IS_DEFAULT = 1)
    UPDATE MOHECORE.STUDY_CURRENCY_IN SET IS_DEFAULT = 1 WHERE STUDY_CURRENCY_IN_ID = 1;

/* ── 4. SYS_PARAMETER ────────────────────────────────────────────────────── */
PRINT '=== V072 step 4: SYS_PARAMETER ===';

IF OBJECT_ID('MOHECORE.SYS_PARAMETER', 'U') IS NULL
BEGIN
    CREATE TABLE MOHECORE.SYS_PARAMETER (
        PARAM_KEY   NVARCHAR(80)   NOT NULL CONSTRAINT PK_SYS_PARAMETER PRIMARY KEY,
        PARAM_VALUE NVARCHAR(400)  NOT NULL,
        VALUE_TYPE  NVARCHAR(10)   NOT NULL CONSTRAINT CK_SYS_PARAMETER_TYPE CHECK (VALUE_TYPE IN ('INT', 'DECIMAL', 'TEXT', 'BOOL')),
        NAME_AR     NVARCHAR(200)  NOT NULL,
        NAME_EN     NVARCHAR(200)  NOT NULL,
        DESCRIPTION NVARCHAR(1000) NULL,
        UPDATED_BY  NVARCHAR(100)  NULL,
        UPDATED_AT  DATETIME2(0)   NULL
    );
END

MERGE MOHECORE.SYS_PARAMETER AS tgt
USING (VALUES
    (N'DASHBOARD.MONTHS_WINDOW',        N'7',   N'INT',     N'لوحة المعلومات: عدد الأشهر في مخططات التسجيل الشهرية', N'Dashboard: months shown on the monthly registration charts', N'Students and users registered per month, counted back from the current month.'),
    (N'DASHBOARD.YEARS_WINDOW',         N'6',   N'INT',     N'لوحة المعلومات: عدد السنوات في مخطط الصرف حسب السنة', N'Dashboard: years shown on the disbursement-by-year chart', N'The most recent years with finance rows.'),
    (N'DASHBOARD.TOP_COUNTRIES',        N'8',   N'INT',     N'لوحة المعلومات: عدد دول الدراسة في المخطط', N'Dashboard: study countries shown on the countries chart', N'The countries with the most students, in descending order.'),
    (N'DASHBOARD.RECENT_STUDENTS',      N'5',   N'INT',     N'لوحة المعلومات: عدد الطلاب في جدول أحدث الطلاب', N'Dashboard: rows in the recent-students table', N'Most recently entered students first.'),
    (N'ALERTS.DOC_EXPIRY_WARN_DAYS',    N'30',  N'INT',     N'التنبيهات: أيام الإنذار قبل انتهاء الوثيقة', N'Alerts: days before a document expires that a notice is raised', N'The daily document-expiry job raises a WARNING notice for documents expiring within this many days.'),
    (N'ALERTS.DOC_EXPIRY_CRITICAL_DAYS', N'7',  N'INT',     N'التنبيهات: أيام أو أقل تُعدّ حرجة', N'Alerts: days or fewer that make an expiry notice CRITICAL', N'Priority ladder of the document-expiry job.'),
    (N'ALERTS.DOC_EXPIRY_HIGH_DAYS',    N'14',  N'INT',     N'التنبيهات: أيام أو أقل تُعدّ عالية', N'Alerts: days or fewer that make an expiry notice HIGH', N'Between the critical and the high thresholds the notice is HIGH; beyond it MEDIUM.'),
    (N'AUDIT.DEFAULT_WINDOW_DAYS',      N'30',  N'INT',     N'تقرير سجل التدقيق: النافذة الافتراضية بالأيام', N'Audit-trail report: default window in days', N'Used when the report is requested without a date range.'),
    (N'STUDENTS.MATCH_SIMILARITY_FLOOR', N'0.5', N'DECIMAL', N'الطلاب: الحد الأدنى للتشابه عند اكتشاف التكرار', N'Students: minimum similarity for a duplicate-student match', N'Candidates below this score are not offered as possible duplicates.')
) AS src (PARAM_KEY, PARAM_VALUE, VALUE_TYPE, NAME_AR, NAME_EN, DESCRIPTION)
   ON tgt.PARAM_KEY = src.PARAM_KEY
WHEN NOT MATCHED THEN
    INSERT (PARAM_KEY, PARAM_VALUE, VALUE_TYPE, NAME_AR, NAME_EN, DESCRIPTION)
    VALUES (src.PARAM_KEY, src.PARAM_VALUE, src.VALUE_TYPE, src.NAME_AR, src.NAME_EN, src.DESCRIPTION);
DECLARE @params INT = (SELECT COUNT(*) FROM MOHECORE.SYS_PARAMETER);
PRINT 'V072: SYS_PARAMETER rows = ' + CAST(@params AS VARCHAR(10));

/* ── 5. DOCUMENT_TYPES seeded here, not in Java ───────────────────────────── */
PRINT '=== V072 step 5: DOCUMENT_TYPES ===';

INSERT INTO MOHECORE.DOCUMENT_TYPES (DOCUMENT_TYPE_NAME, DOCUMENT_TYPE_NAME_EN, DOCUMENT_TYPE_DESCRIPTION, IS_ACTIVE, SORT_ORDER)
SELECT v.name_ar, v.name_en, v.descr, 1, v.sort_order
  FROM (VALUES
    (N'جواز سفر',          N'Passport',                          N'Student passport document',                 1),
    (N'شهادة أكاديمية',    N'Academic Certificate',              N'Academic degree or diploma certificate',     2),
    (N'شهادة ميلاد',       N'Birth Certificate',                 N'Student birth certificate',                 3),
    (N'تأشيرة',            N'Visa',                              N'Student visa document',                     4),
    (N'كشف درجات',         N'Transcript',                        N'Academic transcript or grade report',       5),
    (N'شهادة لغة',         N'Language Certificate',              N'Language proficiency certificate',          6),
    (N'شهادة طبية',        N'Medical Certificate',               N'Medical examination certificate',           7),
    (N'مستندات مالية',     N'Financial Documents',               N'Bank statements or financial guarantee',    8),
    (N'خطاب توصية',        N'Recommendation Letter',             N'Academic or professional recommendation',   9),
    (N'أخرى',              N'Other',                             N'Other miscellaneous documents',             10),
    (N'إفادة جامعية',      N'University Enrollment Certificate', N'Enrollment certificate issued by the university', 11)
  ) v(name_ar, name_en, descr, sort_order)
 WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.DOCUMENT_TYPES d
                    WHERE d.DOCUMENT_TYPE_NAME_EN = v.name_en OR d.DOCUMENT_TYPE_NAME = v.name_ar);
DECLARE @docInserted INT = @@ROWCOUNT;
DECLARE @docTotal INT = (SELECT COUNT(*) FROM MOHECORE.DOCUMENT_TYPES);
PRINT 'V072: document types inserted = ' + CAST(@docInserted AS VARCHAR(10))
    + ', total = ' + CAST(@docTotal AS VARCHAR(10));

/* ── 6. Verification ─────────────────────────────────────────────────────── */
PRINT '=== V072 verification ===';
SELECT t.name AS lu_table,
       CASE WHEN COL_LENGTH('MOHECORE.' + t.name, 'UI_SEVERITY') IS NULL THEN 'MISSING' ELSE 'ok' END AS ui_severity,
       CASE WHEN COL_LENGTH('MOHECORE.' + t.name, 'ICON')        IS NULL THEN 'MISSING' ELSE 'ok' END AS icon,
       CASE WHEN COL_LENGTH('MOHECORE.' + t.name, 'IS_SYSTEM')   IS NULL THEN 'MISSING' ELSE 'ok' END AS is_system
  FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
 WHERE s.name = 'MOHECORE' AND t.name LIKE 'LU[_]%'
   AND t.name NOT IN ('LU_TRAVEL_TYPE', 'LU_FAMILY_RELATIONSHIP', 'LU_TRAVEL_FUNDING_SOURCE')
 ORDER BY t.name;

SELECT 'system codes' AS what, t.tbl, t.n
  FROM (SELECT 'LU_ALERT_SEVERITY' AS tbl, COUNT(*) AS n FROM MOHECORE.LU_ALERT_SEVERITY WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_ALERT_STATUS', COUNT(*) FROM MOHECORE.LU_ALERT_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_APPROVAL_STATUS', COUNT(*) FROM MOHECORE.LU_APPROVAL_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_APPROVAL_REQUEST_TYPE', COUNT(*) FROM MOHECORE.LU_APPROVAL_REQUEST_TYPE WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_WAFAR_SPENDING_STATUS', COUNT(*) FROM MOHECORE.LU_WAFAR_SPENDING_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_WAFAR_RECONCILIATION_STATUS', COUNT(*) FROM MOHECORE.LU_WAFAR_RECONCILIATION_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_PAYMENT_AUTH_STATUS', COUNT(*) FROM MOHECORE.LU_PAYMENT_AUTH_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_CHANGE_REQUEST_STATUS', COUNT(*) FROM MOHECORE.LU_CHANGE_REQUEST_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_STUDENT_PORTAL_ACCOUNT_STATUS', COUNT(*) FROM MOHECORE.LU_STUDENT_PORTAL_ACCOUNT_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_USER_STATUS', COUNT(*) FROM MOHECORE.LU_USER_STATUS WHERE IS_SYSTEM = 1
        UNION ALL SELECT 'LU_QUARTER_NUMBER', COUNT(*) FROM MOHECORE.LU_QUARTER_NUMBER WHERE IS_SYSTEM = 1) t;

SELECT 'coded ids' AS what,
       (SELECT COUNT(*) FROM MOHECORE.STUDY_FIN_RECEIVED_STATUS WHERE CODE IS NULL) AS fin_received_uncoded,
       (SELECT COUNT(*) FROM MOHECORE.STUDY_PAY_FEE_IN_STATUS   WHERE CODE IS NULL) AS pay_fee_uncoded,
       (SELECT COUNT(*) FROM MOHECORE.STUDENT_STUDY_STATUS      WHERE UI_COLOR IS NULL) AS study_status_uncoloured,
       (SELECT COUNT(*) FROM MOHECORE.STUDY_CURRENCY_IN         WHERE IS_DEFAULT = 1) AS default_currencies;
GO
