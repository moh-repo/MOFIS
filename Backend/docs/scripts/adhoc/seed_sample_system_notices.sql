/* ============================================================================
   seed_sample_system_notices.sql
   Sample notices for the dashboard's "System notices" section — DEV ONLY

   Why
   ----
   The section reads MOHE_ALERTS.alerts and splits it into today and yesterday
   by the calendar date of created_date (AlertRepository.findTodayAlerts /
   findYesterdayAlerts). The five rows the dev database already holds were
   written in September 2025, so both lists are empty and there is nothing to
   look at. This puts a realistic set on today's and yesterday's dates.

   It is NOT a migration. It lives in adhoc/ and carries no V-number on
   purpose: sample data must never travel to another database with the schema.

   Re-runnable, and meant to be re-run
   -----------------------------------
   These rows are pinned to the date they were inserted, so tomorrow they fall
   out of both lists. Run the script again whenever you want to see the section
   populated; it deletes its own rows first, so it never accumulates.

   It recognises its own rows by CREATED_BY = N'SAMPLE_DATA'. The five original
   rows are CREATED_BY = N'System' and are never touched — they belong to the
   POST /alerts/sample endpoint the owner asked to leave alone (decision O4).

   Rollback
   --------
   DELETE FROM MOHE_ALERTS.alerts WHERE created_by = N'SAMPLE_DATA';

   What it covers
   --------------
   Every branch the notice row can render: each of the four types and four
   priorities (so all eight colours and icons appear), read and unread, a
   notice whose message is missing, and one with no type and no priority at
   all. The last one is deliberate, not a mistake in the data: it is how the
   screen behaves when a notice carries no classification, and it should show
   "—" rather than invent a default.

   A note on the language
   ----------------------
   MOHE_ALERTS.alerts has one alert_title and one alert_message — no NAME_AR /
   NAME_EN pair like the lookup tables have. So a notice reads in whichever
   language it was written in, whatever language the reader has chosen. The
   text below is Arabic. Only the type and priority tags beside it are
   translated, because those come from LU_ALERT_TYPE and LU_ALERT_SEVERITY.
   Making the notices themselves bilingual would be a schema change and is an
   owner decision, not something this script should assume.

   Run: sqlcmd -S localhost,1433 -d MOHE -U sa -I -f 65001 -b -i seed_sample_system_notices.sql
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @marker NVARCHAR(100) = N'SAMPLE_DATA';

PRINT '=== clearing any previous sample notices ===';
DELETE FROM MOHE_ALERTS.alerts WHERE created_by = @marker;
PRINT '  removed ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' row(s)';

DECLARE @now       DATETIME2 = SYSDATETIME();
DECLARE @midnight  DATETIME2 = CAST(CAST(@now AS DATE) AS DATETIME2);
DECLARE @yesterday DATETIME2 = DATEADD(day, -1, @midnight);

/* Today's rows are spread across the part of today that has actually happened,
   so they are always in the past however early the script is run: at 09:00 they
   sit minutes apart, at 21:00 hours apart. A fixed clock time would put half of
   them in the future on a morning run, and the section would say "in 5 hours". */
DECLARE @elapsed INT = DATEDIFF(minute, @midnight, @now);

PRINT '=== today ===';
INSERT INTO MOHE_ALERTS.alerts
    (alert_title, alert_message, alert_type, alert_priority, alert_status,
     created_by, created_date, expires_date, is_read, read_date, read_by)
SELECT v.title, v.message, v.type, v.priority, v.status,
       @marker,
       DATEADD(minute, -(@elapsed * v.slot / 8), @now),
       v.expires,
       v.is_read,
       CASE WHEN v.is_read = 1 THEN DATEADD(minute, -(@elapsed * v.slot / 16), @now) END,
       CASE WHEN v.is_read = 1 THEN N'admin' END
FROM (VALUES
    (7, N'فشل ترحيل بيانات الربع',
        N'لم يكتمل ترحيل بيانات الربع الثالث 2026؛ راجع سجل العمليات قبل إعادة المحاولة.',
        N'ERROR',   N'CRITICAL', N'ACTIVE', CAST(0 AS BIT), DATEADD(day, 2, SYSDATETIME())),
    (6, N'جوازات سفر تنتهي خلال أسبوعين',
        N'لدى 7 طلاب جوازات سفر تنتهي صلاحيتها خلال أربعة عشر يوماً.',
        N'WARNING', N'HIGH',     N'ACTIVE', CAST(0 AS BIT), DATEADD(day, 14, SYSDATETIME())),
    (5, N'فتح الربع الرابع 2026',
        N'تم فتح الربع الرابع للإدخال ابتداءً من اليوم.',
        N'INFO',    N'MEDIUM',   N'ACTIVE', CAST(1 AS BIT), NULL),
    (4, N'اكتمل النسخ الاحتياطي اليومي',
        N'تم إنشاء النسخة الاحتياطية لقاعدة البيانات بنجاح.',
        N'SUCCESS', N'LOW',      N'ACTIVE', CAST(1 AS BIT), NULL),
    -- No message: the row should print "—" on the second line, not a filler.
    (3, N'تحديث الدليل المحاسبي',
        NULL,
        N'INFO',    N'LOW',      N'ACTIVE', CAST(0 AS BIT), NULL),
    -- No type and no priority: deliberate. Both tags should read "unspecified"
    -- in a neutral colour; nothing here should guess INFO or LOW.
    (2, N'إشعار غير مصنّف',
        N'هذا الإشعار بلا نوع وبلا أولوية، للتحقق من طريقة عرضه.',
        NULL,       NULL,        N'ACTIVE', CAST(0 AS BIT), NULL)
) AS v(slot, title, message, type, priority, status, is_read, expires);
PRINT '  inserted ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' row(s)';

PRINT '=== yesterday ===';
INSERT INTO MOHE_ALERTS.alerts
    (alert_title, alert_message, alert_type, alert_priority, alert_status,
     created_by, created_date, expires_date, is_read, read_date, read_by)
SELECT v.title, v.message, v.type, v.priority, v.status,
       @marker,
       DATEADD(minute, v.minute_of_day, @yesterday),
       v.expires,
       v.is_read,
       CASE WHEN v.is_read = 1 THEN DATEADD(minute, v.minute_of_day + 30, @yesterday) END,
       CASE WHEN v.is_read = 1 THEN N'admin' END
FROM (VALUES
    (555,  N'طلبات موافقة معلّقة',
        N'ثلاثة طلبات موافقة تجاوزت 48 ساعة دون بت.',
        N'WARNING', N'HIGH',     N'ACTIVE',    CAST(1 AS BIT), NULL),
    (700,  N'انقطاع خدمة البريد',
        N'تعذّر إرسال إشعارات البريد بين 10:00 و 11:20، ثم عادت الخدمة.',
        N'ERROR',   N'CRITICAL', N'RESOLVED',  CAST(1 AS BIT), NULL),
    (845,  N'إضافة 12 طالباً جديداً',
        N'تمت إضافة اثني عشر طالباً إلى قاعدة البيانات.',
        N'INFO',    N'MEDIUM',   N'ACTIVE',    CAST(0 AS BIT), NULL),
    (1050, N'اعتماد كشف الصرف',
        N'تم اعتماد كشف صرف الربع الثالث 2026.',
        N'SUCCESS', N'LOW',      N'DISMISSED', CAST(1 AS BIT), NULL)
) AS v(minute_of_day, title, message, type, priority, status, is_read, expires);
PRINT '  inserted ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' row(s)';
GO

PRINT '=== verification: what the dashboard will read ===';
DECLARE @problems NVARCHAR(MAX) = N'';

/* The two queries below are the repository's own, so this proves what the
   section will actually list rather than what the inserts intended. */
DECLARE @today INT = (SELECT COUNT(*) FROM MOHE_ALERTS.alerts
                      WHERE CONVERT(date, created_date) = CONVERT(date, GETDATE()));
DECLARE @yest  INT = (SELECT COUNT(*) FROM MOHE_ALERTS.alerts
                      WHERE CONVERT(date, created_date) = DATEADD(day, -1, CONVERT(date, GETDATE())));

IF @today < 6 SET @problems = @problems + N'today has ' + CAST(@today AS NVARCHAR(10)) + N', expected at least 6; ';
IF @yest  < 4 SET @problems = @problems + N'yesterday has ' + CAST(@yest AS NVARCHAR(10)) + N', expected at least 4; ';

-- A notice dated in the future would read as "in 3 hours", which is not a state
-- the section is meant to show.
IF EXISTS (SELECT 1 FROM MOHE_ALERTS.alerts WHERE created_by = N'SAMPLE_DATA' AND created_date > SYSDATETIME())
    SET @problems = @problems + N'a sample notice is dated in the future; ';

-- Every type and priority must exist in its list, or the tag falls back to the
-- raw code and the colours the section is being checked for never appear.
IF EXISTS (SELECT 1 FROM MOHE_ALERTS.alerts a
           WHERE a.created_by = N'SAMPLE_DATA' AND a.alert_type IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM MOHECORE.LU_ALERT_TYPE t
                             WHERE t.CODE = a.alert_type AND t.IS_DELETED = 0))
    SET @problems = @problems + N'a sample notice has a type LU_ALERT_TYPE does not hold; ';
IF EXISTS (SELECT 1 FROM MOHE_ALERTS.alerts a
           WHERE a.created_by = N'SAMPLE_DATA' AND a.alert_priority IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM MOHECORE.LU_ALERT_SEVERITY s
                             WHERE s.CODE = a.alert_priority AND s.IS_DELETED = 0))
    SET @problems = @problems + N'a sample notice has a priority LU_ALERT_SEVERITY does not hold; ';

IF @problems <> N''
    THROW 50090, @problems, 1;

PRINT '  today: ' + CAST(@today AS NVARCHAR(10)) + ' notice(s), yesterday: ' + CAST(@yest AS NVARCHAR(10));

SELECT CASE WHEN CONVERT(date, created_date) = CONVERT(date, GETDATE()) THEN 'today' ELSE 'yesterday' END AS [when],
       CONVERT(varchar(16), created_date, 120) AS created,
       ISNULL(alert_type, '(none)')     AS [type],
       ISNULL(alert_priority, '(none)') AS priority,
       alert_status                     AS [status],
       is_read,
       alert_title
FROM MOHE_ALERTS.alerts
WHERE created_by = N'SAMPLE_DATA'
ORDER BY created_date DESC;
GO
