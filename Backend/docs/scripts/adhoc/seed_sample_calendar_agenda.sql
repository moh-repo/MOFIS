/* ============================================================================
   seed_sample_calendar_agenda.sql
   Sample entries for the topbar calendar's day agenda — DEV ONLY

   Why
   ----
   The agenda (GET /api/v1/calendar/day) reads what already happened: documents
   about to expire, alert cases opened, notifications sent, scheduled report
   runs. On the dev database almost none of that falls near today, so the panel
   opens on "Nothing on this day." for every day a reviewer is likely to click.
   This puts a spread of entries across the three weeks around today so every
   kind, every severity and the month view's marks can all be seen.

   It is NOT a migration. It lives in adhoc/ and carries no V-number on
   purpose: sample data must never travel to another database with the schema.

   Re-runnable, and meant to be re-run
   -----------------------------------
   The rows are pinned to the date they were inserted, so a week later they no
   longer surround today. Run it again whenever the agenda goes quiet; it
   deletes its own rows first, so it never accumulates.

   It recognises its own rows by a sentinel prefix in each table and touches
   nothing else:

       MOHECORE.STUDENT_DOCUMENTS       FILE_PATH  starts 'sample-data/calendar/'
       ALERTEVENTCASES.ALERT_EVENT_CASE NOTES      starts 'SAMPLE_DATA: '
       MOHECORE.NOTIFICATION_LOG        BATCH_ID   starts 'sample-calendar-'
       MOHECORE.SCHEDULED_REPORT        CREATED_BY = 'SAMPLE_DATA'

   Every one of those is matched with LEFT(column, n) = '...' rather than with
   LIKE, and it is worth saying why. The first version of this script used
   LIKE N'[SAMPLE_DATA]%' for the cases. In T-SQL square brackets are a LIKE
   character class, so that pattern does not mean "starts with [SAMPLE_DATA]" —
   it means "first character is one of S, A, M, P, L, E, _, D or T". It matched
   none of this script's own rows, which begin with '[', so the cleanup would
   have left them behind and a second run would have doubled them; and it would
   have matched any real note beginning with one of those nine characters, which
   the DELETE would then have removed. Underscore is a wildcard too. A sentinel
   is only useful if it matches exactly what it says, so none of them are
   patterns any more.

   Rollback
   --------
   Re-run section 2 on its own, or:

       DELETE FROM MOHECORE.STUDENT_DOCUMENTS       WHERE LEFT(FILE_PATH, 21) = 'sample-data/calendar/';
       DELETE FROM ALERTEVENTCASES.ALERT_EVENT_CASE WHERE LEFT(NOTES, 13) = N'SAMPLE_DATA: ';
       DELETE FROM MOHECORE.NOTIFICATION_LOG        WHERE LEFT(BATCH_ID, 16) = 'sample-calendar-';
       DELETE FROM MOHECORE.SCHEDULED_REPORT        WHERE CREATED_BY = N'SAMPLE_DATA';

   (Row-Level Security applies to three of those four tables, so run the
   rollback with the session context set as section 1 does, or as the ministry.)

   Row-Level Security
   ------------------
   STUDENT_DOCUMENTS, ALERT_EVENT_CASE and SCHEDULED_REPORT all carry a FILTER
   and a BLOCK predicate (V031, V065). Without a tenant SESSION_CONTEXT this
   script could neither read a student id nor insert a row that names a country.
   Section 1 therefore sets TenantAll = 1 — the first branch of
   MOHECORE.fn_tenant_predicate, which V031 describes as being for "ministry
   users and system work (migrations, startup, elevated jobs)". A seed script is
   system work. Section 6 clears it again.

   Times, and why they are written in UTC
   --------------------------------------
   CREATED_AT on the case and the log, and NEXT_RUN_DATE on the schedule, are
   mapped to java.time.Instant, which Hibernate reads and writes in UTC. The
   agenda then decides which day an entry belongs to using AppTimeZone
   (mohe.report.timezone, Asia/Riyadh). So a row written as local wall-clock
   time would be read back three hours late and could land on the following
   day's agenda — the entry at 23:30 would move to tomorrow, invisibly.

   Every instant below is therefore built as a local time and converted:
   AT TIME ZONE 'Arab Standard Time' AT TIME ZONE 'UTC'. EXPIRY_DATE is a plain
   date and needs none of this.

   If mohe.report.timezone is ever changed, change @tz here to match.

   What it does NOT do
   -------------------
   It does not touch MOHECORE.MOHE_QUARTER. All seven quarters on dev have
   QUARTER_PAY_DATE and QUARTER_RECIVED_DATE set to NULL, which is why the
   agenda's two quarter kinds never appear — the dates were never recorded, and
   filling them in here would put payment dates that did not happen onto real
   quarter records. Record them on the quarter screen if you want those two
   kinds on the agenda.

   It does not touch MOHE_ALERTS.alerts either: system notices are not one of
   the agenda's kinds. seed_sample_system_notices.sql covers that section.
   ============================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ── 1. Elevate to system scope ───────────────────────────────────────────── */
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1', @read_only = 0;
GO

DECLARE @tz sysname = N'Arab Standard Time';   -- must match mohe.report.timezone
DECLARE @today date = CAST(SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE @tz AS date);

PRINT 'Seeding the calendar agenda around ' + CONVERT(varchar(10), @today, 120)
      + ' (' + CAST(@tz AS varchar(64)) + ')';

/* ── 2. Remove this script's previous rows ────────────────────────────────── */
/* LEFT(...) = '...', never LIKE: see the note in the header. Brackets and
   underscores are LIKE wildcards, and a sentinel that is also a pattern deletes
   rows it was never meant to name. */
DELETE FROM MOHECORE.STUDENT_DOCUMENTS       WHERE LEFT(FILE_PATH, 21) = 'sample-data/calendar/';
DELETE FROM ALERTEVENTCASES.ALERT_EVENT_CASE WHERE LEFT(NOTES, 13) = N'SAMPLE_DATA: ';
DELETE FROM MOHECORE.NOTIFICATION_LOG        WHERE LEFT(BATCH_ID, 16) = 'sample-calendar-';
DELETE FROM MOHECORE.SCHEDULED_REPORT        WHERE CREATED_BY = N'SAMPLE_DATA';

/* The first version of this script marked its cases with '[SAMPLE_DATA] ', a
   prefix its own LIKE pattern could not match. Remove those too, once. */
DELETE FROM ALERTEVENTCASES.ALERT_EVENT_CASE WHERE LEFT(NOTES, 14) = N'[SAMPLE_DATA] ';

/* ── 3. Students to hang the sample rows off ──────────────────────────────────
   Real students, chosen by id so the script picks the same ones each run.
   STUDENT_DATA is never written by this script — only read. */
IF OBJECT_ID('tempdb..#students') IS NOT NULL DROP TABLE #students;

SELECT TOP (6)
       ROW_NUMBER() OVER (ORDER BY STUDENT_ID) AS n,
       STUDENT_ID,
       STUDY_COUNTRY_IN
INTO   #students
FROM   MOHECORE.STUDENT_DATA
ORDER BY STUDENT_ID;

IF NOT EXISTS (SELECT 1 FROM #students)
BEGIN
    THROW 50001, 'No students visible. Is the tenant session context set (section 1)?', 1;
END

DECLARE @studentCount int = (SELECT COUNT(*) FROM #students);
PRINT 'Using ' + CAST(@studentCount AS varchar(10)) + ' student(s) for the sample rows.';

/* ── 4. The entries ───────────────────────────────────────────────────────────
   Offsets are days from today. Negative days are there so the month view shows
   marks on both sides of today and a reviewer can page backwards.

   4a. Documents expiring — the only forward-looking kind. The agenda colours
       each one with the ladder DocumentExpiryScheduler uses (SYS_PARAMETER:
       CRITICAL <= 7 days, HIGH <= 14, MEDIUM beyond), so the offsets below are
       chosen to produce one of each. */
IF OBJECT_ID('tempdb..#documents') IS NOT NULL DROP TABLE #documents;
CREATE TABLE #documents (day_offset int, doc_name nvarchar(255), doc_type nvarchar(100), student_n int);

INSERT INTO #documents (day_offset, doc_name, doc_type, student_n) VALUES
    (0,  N'جواز سفر — ينتهي اليوم',        N'PASSPORT',   1),   -- today, CRITICAL
    (2,  N'تأشيرة إقامة',                   N'VISA',       2),   -- CRITICAL
    (9,  N'بطاقة تأمين صحي',                N'INSURANCE',  3),   -- HIGH
    (20, N'شهادة قيد جامعية',               N'CERTIFICATE', 4),  -- MEDIUM
    (-4, N'جواز سفر — منتهٍ',               N'PASSPORT',   5);   -- already expired

INSERT INTO MOHECORE.STUDENT_DOCUMENTS
    (STUDENT_ID, DOCUMENT_NAME, DOCUMENT_TYPE, FILE_PATH, IS_ACTIVE, EXPIRY_DATE,
     UPLOAD_DATE, VERSION_NO, IS_ENCRYPTED, STUDY_COUNTRY_IN_ID)
SELECT s.STUDENT_ID,
       d.doc_name,
       d.doc_type,
       'sample-data/calendar/' + CAST(d.day_offset AS varchar(10)) + '-' + d.doc_type,
       1,
       DATEADD(day, d.day_offset, @today),
       SYSUTCDATETIME(),
       1,
       0,
       s.STUDY_COUNTRY_IN
FROM   #documents d
JOIN   #students  s ON s.n = ((d.student_n - 1) % @studentCount) + 1;

/* 4b. Alert cases opened. One per scenario the database defines, so every
       severity in LU_ALERT_SEVERITY shows its own colour on the agenda. */
INSERT INTO ALERTEVENTCASES.ALERT_EVENT_CASE
    (SCENARIO_ID, STUDENT_ID, STUDENT_COUNTRY_ID, REFERENCE_DATE, STATUS, NOTES, IS_DELETED, CREATED_AT)
SELECT sc.SCENARIO_ID,
       s.STUDENT_ID,
       s.STUDY_COUNTRY_IN,
       CAST(DATEADD(day, -sc.SCENARIO_ID, @today) AS datetime2),
       N'OPEN',
       N'SAMPLE_DATA: ' + sc.SCENARIO_CODE,
       0,
       -- Opened during working hours on a day near today, written in UTC.
       CAST(DATEADD(hour, 9 + sc.SCENARIO_ID,
            CAST(DATEADD(day, CASE WHEN sc.SCENARIO_ID % 2 = 0 THEN 0 ELSE -1 END, @today) AS datetime2))
            AT TIME ZONE @tz AT TIME ZONE 'UTC' AS datetime2)
FROM   ALERTEVENTCASES.ALERT_EVENT_SCENARIO sc
JOIN   #students s ON s.n = ((sc.SCENARIO_ID - 1) % @studentCount) + 1
WHERE  sc.IS_ACTIVE = 1;

/* 4c. Notifications sent. Eight on today so the agenda has to say "and 3 more",
       which is the count the server reports rather than the length of the list;
       two more on nearby days. SCOPE 'ALL' with no NOTIFICATION_LOG_COUNTRY
       rows is the shape findScopedHistory admits for every reader. */
IF OBJECT_ID('tempdb..#sends') IS NOT NULL DROP TABLE #sends;
CREATE TABLE #sends (day_offset int, hour_of_day int, channel nvarchar(10), status nvarchar(20), subject nvarchar(500));

INSERT INTO #sends (day_offset, hour_of_day, channel, status, subject) VALUES
    (0, 8,  N'EMAIL', N'SENT',    N'تذكير بموعد تسليم النتائج الفصلية'),
    (0, 9,  N'SMS',   N'SENT',    N'تنبيه: تجديد جواز السفر'),
    (0, 10, N'EMAIL', N'PARTIAL', N'إشعار صرف المستحقات المالية'),
    (0, 11, N'EMAIL', N'FAILED',  N'دعوة لاجتماع الملحقية'),
    (0, 12, N'SMS',   N'SENT',    N'تأكيد استلام الطلب'),
    (0, 13, N'EMAIL', N'SENT',    N'تحديث بيانات الطالب'),
    (0, 14, N'EMAIL', N'SENT',    N'نتيجة طلب التمديد'),
    (0, 15, N'SMS',   N'SENT',    N'تذكير بموعد المراجعة'),
    (-1, 10, N'EMAIL', N'SENT',   N'تقرير الربع السابق'),
    (3,  10, N'EMAIL', N'PENDING', N'إشعار مجدول');

INSERT INTO MOHECORE.NOTIFICATION_LOG
    (BATCH_ID, CHANNEL, RECIPIENT_TYPE, SCOPE, SUBJECT, BODY,
     TOTAL_COUNT, SENT_COUNT, FAILED_COUNT, STATUS, CREATED_AT, SENT_AT)
SELECT 'sample-calendar-' + CAST(ROW_NUMBER() OVER (ORDER BY day_offset, hour_of_day) AS varchar(10)),
       channel,
       N'STUDENT',
       N'ALL',
       subject,
       N'نص تجريبي لبيانات العرض.',
       CASE status WHEN N'PENDING' THEN 0 ELSE 25 END,
       CASE status WHEN N'SENT' THEN 25 WHEN N'PARTIAL' THEN 18 ELSE 0 END,
       CASE status WHEN N'PARTIAL' THEN 7 WHEN N'FAILED' THEN 25 ELSE 0 END,
       status,
       CAST(DATEADD(hour, hour_of_day, CAST(DATEADD(day, day_offset, @today) AS datetime2))
            AT TIME ZONE @tz AT TIME ZONE 'UTC' AS datetime2),
       CASE WHEN status = N'PENDING' THEN NULL
            ELSE CAST(DATEADD(hour, hour_of_day, CAST(DATEADD(day, day_offset, @today) AS datetime2))
                 AT TIME ZONE @tz AT TIME ZONE 'UTC' AS datetime2) END
FROM   #sends;

/* 4d. Scheduled report runs. Both branches of the agenda's rule appear — it
       shows a schedule's next run when that falls on the day, and otherwise its
       last run.

       Neither row is left DUE. ScheduledReportExecutionService wakes every
       fifteen minutes and claims anything whose NEXT_RUN_DATE has passed, so a
       demo schedule due later today would be picked up and executed for real.
       Sample data must not make the server do work: the row that appears on
       today does so through its LAST_RUN_DATE, and every NEXT_RUN_DATE here is
       in the future.

       Country left NULL: these are ministry-wide schedules. */
INSERT INTO MOHECORE.SCHEDULED_REPORT
    (SCHEDULE_NAME, SCHEDULE_DESCRIPTION, REPORT_TYPE, FREQUENCY,
     NEXT_RUN_DATE, LAST_RUN_DATE, LAST_RUN_STATUS, SCHEDULE_STATUS, IS_ENABLED,
     CREATED_DATE, CREATED_BY, STUDY_COUNTRY_IN_ID)
-- Ran this morning, next due in a week: appears on today via the last-run branch.
SELECT N'تقرير الطلاب الأسبوعي',
       N'بيانات عرض — يُحذف بإعادة تشغيل السكربت',
       N'STUDENT_LIST', N'WEEKLY',
       CAST(DATEADD(hour, 6, CAST(DATEADD(day, 7, @today) AS datetime2)) AT TIME ZONE @tz AT TIME ZONE 'UTC' AS datetime2),
       CAST(DATEADD(hour, 6, CAST(@today AS datetime2)) AT TIME ZONE @tz AT TIME ZONE 'UTC' AS datetime2),
       N'SUCCESS', N'ACTIVE', 1, SYSUTCDATETIME(), N'SAMPLE_DATA', NULL
UNION ALL
-- Due tomorrow, last ran six days ago: appears on both, via each branch in turn.
SELECT N'التقرير المالي الشهري',
       N'بيانات عرض — يُحذف بإعادة تشغيل السكربت',
       N'FINANCIAL_SUMMARY', N'MONTHLY',
       CAST(DATEADD(hour, 6, CAST(DATEADD(day, 1, @today) AS datetime2)) AT TIME ZONE @tz AT TIME ZONE 'UTC' AS datetime2),
       CAST(DATEADD(hour, 6, CAST(DATEADD(day, -6, @today) AS datetime2)) AT TIME ZONE @tz AT TIME ZONE 'UTC' AS datetime2),
       N'FAILED', N'ACTIVE', 1, SYSUTCDATETIME(), N'SAMPLE_DATA', NULL;

/* ── 5. What was written, by the day the agenda will file it under ──────────
   The instants are converted back to the application's zone here, so this list
   is the list of days that will show a mark in the calendar. */
SELECT  d.the_day,
        d.kind,
        COUNT(*) AS entries
FROM (
        SELECT EXPIRY_DATE AS the_day, 'DOCUMENT_EXPIRY' AS kind
        FROM   MOHECORE.STUDENT_DOCUMENTS
        WHERE  LEFT(FILE_PATH, 21) = 'sample-data/calendar/'
        UNION ALL
        SELECT CAST(CREATED_AT AT TIME ZONE 'UTC' AT TIME ZONE @tz AS date), 'ALERT_CASE'
        FROM   ALERTEVENTCASES.ALERT_EVENT_CASE
        WHERE  LEFT(NOTES, 13) = N'SAMPLE_DATA: '
        UNION ALL
        SELECT CAST(CREATED_AT AT TIME ZONE 'UTC' AT TIME ZONE @tz AS date), 'NOTIFICATION'
        FROM   MOHECORE.NOTIFICATION_LOG
        WHERE  LEFT(BATCH_ID, 16) = 'sample-calendar-'
        UNION ALL
        -- Both dates, as the agenda lists them: a schedule appears on the day it
        -- is due to run and on the day it last ran.
        SELECT CAST(NEXT_RUN_DATE AT TIME ZONE 'UTC' AT TIME ZONE @tz AS date), 'REPORT_RUN'
        FROM   MOHECORE.SCHEDULED_REPORT
        WHERE  CREATED_BY = N'SAMPLE_DATA' AND NEXT_RUN_DATE IS NOT NULL
        UNION ALL
        SELECT CAST(LAST_RUN_DATE AT TIME ZONE 'UTC' AT TIME ZONE @tz AS date), 'REPORT_RUN'
        FROM   MOHECORE.SCHEDULED_REPORT
        WHERE  CREATED_BY = N'SAMPLE_DATA' AND LAST_RUN_DATE IS NOT NULL
) d
GROUP BY d.the_day, d.kind
ORDER BY d.the_day, d.kind;
GO

/* ── 6. Back to no scope ──────────────────────────────────────────────────── */
EXEC sp_set_session_context @key = N'TenantAll', @value = NULL, @read_only = 0;
GO

PRINT 'Done. Open the calendar in the top bar; today should no longer be empty.';
GO
