/* ============================================================================
   seed_sample_notifications.sql
   Test settings and history for the three notification pages — DEV ONLY

   What it gives you
   -----------------
   1. Every notification settings row pointed at the local test gateway
      (notification-test-gateway.mjs, same folder), so a send from the Send page
      reaches nobody real — not by e-mail, SMS or WhatsApp:

        all countries (global)  e-mail + SMS + WhatsApp, WhatsApp mode AUTO
        Jordan          (1004)  e-mail + SMS + WhatsApp, WhatsApp mode TEXT
        Saudi Arabia    (1005)  e-mail + WhatsApp, WhatsApp mode TEMPLATE; SMS off
        Egypt              (1)  SMS only
        United Kingdom  (1006)  e-mail only, to a port nothing listens on -> FAILED
        Turkey          (1008)  every channel off -> the Send page greys them out
        every other country     no row of its own -> falls back to the global row,
                                and is what "Add country settings" offers

      The SMS and WhatsApp token, write-only on the screen, is "Bearer test-token";
      the SMTP login is test / test.

   2. Sixteen history rows over the last two weeks, covering every channel
      (EMAIL, SMS, WHATSAPP and the legacy BOTH), every status, both recipient
      types, all three scopes, one-country, several-country and all-country
      sends, and one send on three channels under a single batch id. Their
      BATCH_ID starts "seed-notify-" and their subject starts "[TEST]".

   Your real settings are kept
   ---------------------------
   The first run copies every NOTIFICATION_SETTINGS row, secrets included, into
   MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED. A later run leaves that copy
   alone, so running this twice can never overwrite your real settings with the
   test ones. restore_sample_notifications.sql puts the copy back exactly, drops
   it and deletes the seeded history rows.

   Before you start
   ----------------
   - V118 must be applied (start the backend once); the script stops otherwise.
   - Start the gateway:  node Backend/docs/scripts/adhoc/notification-test-gateway.mjs
   - Start the backend with MOHE_OUTBOUND_ALLOW_PRIVATE=true: the outbound
     policy (O-36) refuses 127.0.0.1 otherwise, and every send fails with
     "refused by outbound policy".

   Re-runnable: resets the test settings and replaces its own history rows.
   Never on production.

   Run: sqlcmd -S localhost,1433 -d MOHE -U sa -I -f 65001 -b -i seed_sample_notifications.sql
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF COL_LENGTH(N'MOHECORE.NOTIFICATION_SETTINGS', N'WHATSAPP_MESSAGE_MODE') IS NULL
    THROW 50001, 'V118 is not applied yet: start the backend once so Flyway adds the WhatsApp columns, then run this again.', 1;
GO

/* ── 1. Keep the real settings, once ─────────────────────────────────────── */
IF OBJECT_ID(N'MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED', N'U') IS NULL
BEGIN
    SELECT * INTO MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED FROM MOHECORE.NOTIFICATION_SETTINGS;
    PRINT 'Real settings copied to MOHECORE.ZZ_NOTIFICATION_SETTINGS_BEFORE_SEED ('
        + CAST(@@ROWCOUNT AS varchar(10)) + ' rows)';
END
ELSE
    PRINT 'A copy of the real settings already exists; left as it is';
GO

/* ── 2. Test settings ────────────────────────────────────────────────────── */
BEGIN TRANSACTION;

DECLARE @smtpHost  NVARCHAR(255) = N'127.0.0.1',
        @smtpPort  INT = 2525,
        @smsUrl    NVARCHAR(500) = N'http://127.0.0.1:8089/sms',
        @waUrl     NVARCHAR(500) = N'http://127.0.0.1:8089/whatsapp/v21.0/100000000000001/messages',
        @headers   NVARCHAR(MAX) = N'[{"key":"Authorization","value":"Bearer test-token"}]',
        @approved  NVARCHAR(MAX) = N'{"messaging_product":"whatsapp","recipient_type":"individual","to":"{{toDigits}}",'
                                 + N'"type":"template","template":{"name":"mohe_notification","language":{"code":"ar"},'
                                 + N'"components":[{"type":"body","parameters":[{"type":"text","text":"{{bodyOneLine}}"}]}]}}';

-- Country rows go; the global row is updated in place (sign-in codes and
-- scheduled reports read it, and its ID stays what it was).
DELETE FROM MOHECORE.NOTIFICATION_SETTINGS WHERE COUNTRY_ID IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM MOHECORE.NOTIFICATION_SETTINGS WHERE COUNTRY_ID IS NULL)
    INSERT INTO MOHECORE.NOTIFICATION_SETTINGS (ID, COUNTRY_ID) VALUES (1, NULL);

UPDATE MOHECORE.NOTIFICATION_SETTINGS SET
    EMAIL_ENABLED = 1, SMTP_HOST = @smtpHost, SMTP_PORT = @smtpPort,
    SMTP_USERNAME = N'test', SMTP_PASSWORD = N'test',
    SMTP_FROM_ADDRESS = N'notifications@mohe.test', SMTP_FROM_NAME = N'وزارة المالية (اختبار)',
    SMTP_USE_TLS = 0, SMTP_USE_SSL = 0,
    SMS_ENABLED = 1, SMS_WEBHOOK_URL = @smsUrl, SMS_WEBHOOK_METHOD = N'POST',
    SMS_WEBHOOK_HEADERS = @headers, SMS_FROM_NUMBER = N'MOHE',
    WHATSAPP_ENABLED = 1, WHATSAPP_WEBHOOK_URL = @waUrl, WHATSAPP_WEBHOOK_HEADERS = @headers,
    WHATSAPP_FROM_NUMBER = N'+218910000000', WHATSAPP_BODY_TEMPLATE = NULL,
    WHATSAPP_MESSAGE_MODE = N'AUTO', WHATSAPP_APPROVED_TEMPLATE = @approved,
    UPDATED_BY = 2, UPDATED_AT = GETDATE()
WHERE COUNTRY_ID IS NULL;

DECLARE @base INT = (SELECT MAX(ID) FROM MOHECORE.NOTIFICATION_SETTINGS);

DECLARE @rows TABLE (k INT, countryId INT, email BIT, sms BIT, wa BIT, mode NVARCHAR(10), port INT, fromName NVARCHAR(255));
INSERT INTO @rows VALUES
    (1, 1004, 1, 1, 1, N'TEXT',     2525, N'الملحقية الثقافية - الأردن (اختبار)'),
    (2, 1005, 1, 0, 1, N'TEMPLATE', 2525, N'الملحقية الثقافية - السعودية (اختبار)'),
    (3, 1,    0, 1, 0, N'TEXT',     2525, N'الملحقية الثقافية - مصر (اختبار)'),
    (4, 1006, 1, 0, 0, N'TEXT',     2526, N'الملحقية الثقافية - بريطانيا (اختبار)'),
    (5, 1008, 0, 0, 0, N'TEXT',     2525, N'الملحقية الثقافية - تركيا (اختبار)');

INSERT INTO MOHECORE.NOTIFICATION_SETTINGS
    (ID, COUNTRY_ID, EMAIL_ENABLED, SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD,
     SMTP_FROM_ADDRESS, SMTP_FROM_NAME, SMTP_USE_TLS, SMTP_USE_SSL,
     SMS_ENABLED, SMS_WEBHOOK_URL, SMS_WEBHOOK_METHOD, SMS_WEBHOOK_HEADERS, SMS_FROM_NUMBER,
     WHATSAPP_ENABLED, WHATSAPP_WEBHOOK_URL, WHATSAPP_WEBHOOK_HEADERS, WHATSAPP_FROM_NUMBER,
     WHATSAPP_BODY_TEMPLATE, WHATSAPP_MESSAGE_MODE, WHATSAPP_APPROVED_TEMPLATE, UPDATED_BY, UPDATED_AT)
SELECT @base + r.k, r.countryId, r.email, @smtpHost, r.port, N'test', N'test',
       N'attache' + CAST(r.countryId AS NVARCHAR(10)) + N'@mohe.test', r.fromName, 0, 0,
       r.sms, @smsUrl, N'POST', @headers, N'MOHE-' + CAST(r.countryId AS NVARCHAR(10)),
       r.wa, @waUrl, @headers, N'+21891000' + RIGHT(N'0000' + CAST(r.countryId AS NVARCHAR(10)), 4),
       NULL, r.mode, @approved, 2, GETDATE()
FROM @rows r
WHERE EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN c WHERE c.STUDY_COUNTRY_IN_ID = r.countryId);

PRINT 'Test settings: global row + ' + CAST(@@ROWCOUNT AS varchar(10)) + ' country rows';

/* ── 3. Test history ─────────────────────────────────────────────────────── */
DELETE FROM MOHECORE.NOTIFICATION_LOG WHERE LEFT(BATCH_ID, 12) = 'seed-notify-';  -- countries cascade

DECLARE @now DATETIME = GETUTCDATE();
DECLARE @log TABLE (
    batch NVARCHAR(36), channel NVARCHAR(10), rtype NVARCHAR(20), scope NVARCHAR(10),
    subject NVARCHAR(500), body NVARCHAR(MAX), total INT, sent INT, failed INT,
    status NVARCHAR(20), errors NVARCHAR(MAX), createdBy INT, hoursAgo INT);

INSERT INTO @log VALUES
 -- one send on three channels: one batch, three rows, each with its own counts
 ('seed-notify-multi-01', 'EMAIL',    'STUDENT',  'ALL',   N'[TEST] تعميم: مواعيد التسجيل للفصل القادم',
  N'<p>عزيزي الطالب،</p><p>يبدأ التسجيل للفصل القادم يوم الأحد.</p>', 40, 40, 0, 'SENT', NULL, 2, 2),
 ('seed-notify-multi-01', 'SMS',      'STUDENT',  'ALL',   N'[TEST] تعميم: مواعيد التسجيل للفصل القادم',
  N'<p>عزيزي الطالب،</p><p>يبدأ التسجيل للفصل القادم يوم الأحد.</p>', 40, 36, 4, 'PARTIAL',
  N'(missing phone for recipient)' + NCHAR(10) + N'(missing phone for recipient)' + NCHAR(10)
  + N'(missing phone for recipient)' + NCHAR(10) + N'+962790000013: Webhook returned HTTP 500: {"error":"simulated gateway failure"}', 2, 2),
 ('seed-notify-multi-01', 'WHATSAPP', 'STUDENT',  'ALL',   N'[TEST] تعميم: مواعيد التسجيل للفصل القادم',
  N'<p>عزيزي الطالب،</p><p>يبدأ التسجيل للفصل القادم يوم الأحد.</p>', 40, 38, 2, 'PARTIAL',
  N'(missing phone for recipient)' + NCHAR(10) + N'+962790000013: Gateway returned HTTP 500: {"error":"simulated gateway failure"}', 2, 2),
 -- Jordan, TEXT mode: free text refused outside the 24-hour window
 ('seed-notify-jo-01',    'EMAIL',    'STUDENT',  'GROUP', N'[TEST] تذكير بتسليم نتائج الفصل',
  N'<p>يرجى رفع كشف الدرجات قبل نهاية الشهر.</p>', 12, 12, 0, 'SENT', NULL, 13, 26),
 ('seed-notify-jo-01',    'WHATSAPP', 'STUDENT',  'GROUP', N'[TEST] تذكير بتسليم نتائج الفصل',
  N'<p>يرجى رفع كشف الدرجات قبل نهاية الشهر.</p>', 12, 7, 5, 'PARTIAL',
  N'+962791234561: Gateway returned HTTP 400: {"error":{"message":"(#131047) Re-engagement message","code":131047}}' + NCHAR(10)
  + N'+962791234563: Gateway returned HTTP 400: {"error":{"message":"(#131047) Re-engagement message","code":131047}}', 13, 26),
 ('seed-notify-jo-02',    'WHATSAPP', 'EMPLOYEE', 'ONE',   NULL,
  N'اجتماع الملحقية غدًا الساعة العاشرة.', 1, 1, 0, 'SENT', NULL, 13, 50),
 ('seed-notify-jo-sa-01', 'SMS',      'STUDENT',  'ALL',   NULL,
  N'تنبيه: تجديد جواز السفر قبل انتهاء صلاحيته.', 30, 0, 0, 'PENDING', NULL, 2, 0),
 -- Saudi Arabia, TEMPLATE mode: a template the account does not have
 ('seed-notify-sa-01',    'WHATSAPP', 'STUDENT',  'ONE',   NULL,
  N'تم اعتماد طلب التمديد.', 1, 0, 1, 'FAILED',
  N'+966501234567: Gateway returned HTTP 400: {"error":{"message":"(#132001) Template name does not exist in the translation","code":132001}}', 2, 74),
 ('seed-notify-sa-02',    'EMAIL',    'STUDENT',  'ONE',   N'[TEST] نتيجة طلب التمديد',
  N'<p>تم اعتماد طلب التمديد.</p>', 1, 1, 0, 'SENT', NULL, 2, 75),
 -- Egypt, SMS only
 ('seed-notify-eg-01',    'SMS',      'STUDENT',  'GROUP', NULL,
  N'تم صرف مخصصات الربع الحالي.', 8, 8, 0, 'SENT', NULL, 2, 98),
 ('seed-notify-eg-02',    'SMS',      'EMPLOYEE', 'GROUP', NULL,
  N'تحديث بيانات الموظفين مطلوب.', 4, 3, 1, 'PARTIAL',
  N'+201000000013: Webhook returned HTTP 500: {"error":"simulated gateway failure"}', 2, 120),
 -- United Kingdom: the relay is down
 ('seed-notify-uk-01',    'EMAIL',    'STUDENT',  'GROUP', N'[TEST] دعوة لاجتماع الطلاب',
  N'<p>ندعوكم لحضور اللقاء السنوي.</p>', 5, 0, 5, 'FAILED',
  N'student1@example.test: Mail server connection failed. Failed messages: Connection refused: connect', 2, 150),
 -- ministry sends with no country: employees by group (ministry-only in history), everyone (visible to all)
 ('seed-notify-min-01',   'EMAIL',    'EMPLOYEE', 'GROUP', N'[TEST] تعميم داخلي للموظفين',
  N'<p>تعميم داخلي.</p>', 6, 6, 0, 'SENT', NULL, 2, 170),
 ('seed-notify-min-02',   'EMAIL',    'EMPLOYEE', 'ALL',   N'[TEST] إجازة رسمية',
  N'<p>يوم الخميس عطلة رسمية.</p>', 25, 25, 0, 'SENT', NULL, 2, 200),
 ('seed-notify-min-03',   'EMAIL',    'STUDENT',  'ALL',   N'[TEST] رسالة بريد فشلت لبعض المستلمين',
  N'<p>نص تجريبي.</p>', 10, 8, 2, 'PARTIAL',
  N'bounce1@example.test: 550 5.1.1 Mailbox unavailable' + NCHAR(10) + N'bounce2@example.test: 550 5.1.1 Mailbox unavailable', 2, 260),
 -- legacy: one row for e-mail and SMS together, counted per message (before V117)
 ('seed-notify-legacy-01', 'BOTH',    'STUDENT',  'ALL',   N'[TEST] رسالة قديمة بالقناتين معًا (BOTH)',
  N'<p>أُرسلت قبل فصل القنوات.</p>', 20, 38, 2, 'PARTIAL',
  N'(missing phone for recipient)' + NCHAR(10) + N'(missing phone for recipient)', 2, 320);

DECLARE @ids TABLE (id BIGINT, batch NVARCHAR(36), channel NVARCHAR(10));
INSERT INTO MOHECORE.NOTIFICATION_LOG
    (BATCH_ID, CHANNEL, RECIPIENT_TYPE, SCOPE, SUBJECT, BODY, TOTAL_COUNT, SENT_COUNT, FAILED_COUNT,
     STATUS, ERROR_SUMMARY, CREATED_BY, CREATED_AT, SENT_AT)
OUTPUT inserted.ID, inserted.BATCH_ID, inserted.CHANNEL INTO @ids
SELECT batch, channel, rtype, scope, subject, body, total, sent, failed, status, errors, createdBy,
       DATEADD(hour, -hoursAgo, @now),
       CASE WHEN status = 'PENDING' THEN NULL ELSE DATEADD(minute, 2, DATEADD(hour, -hoursAgo, @now)) END
FROM @log;

-- The countries each send went to; none for the ministry's all-country and group sends.
DECLARE @countries TABLE (batch NVARCHAR(36), countryId INT);
INSERT INTO @countries VALUES
    ('seed-notify-jo-01', 1004), ('seed-notify-jo-02', 1004),
    ('seed-notify-jo-sa-01', 1004), ('seed-notify-jo-sa-01', 1005),
    ('seed-notify-sa-01', 1005), ('seed-notify-sa-02', 1005),
    ('seed-notify-eg-01', 1), ('seed-notify-eg-02', 1),
    ('seed-notify-uk-01', 1006);

INSERT INTO MOHECORE.NOTIFICATION_LOG_COUNTRY (LOG_ID, COUNTRY_ID)
SELECT i.id, c.countryId
FROM @ids i
JOIN @countries c ON c.batch = i.batch
WHERE EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s WHERE s.STUDY_COUNTRY_IN_ID = c.countryId);

DECLARE @logRows INT = (SELECT COUNT(*) FROM @ids);
PRINT 'Test history: ' + CAST(@logRows AS varchar(10)) + ' rows';

COMMIT;
GO

/* ── Verify ──────────────────────────────────────────────────────────────── */
SELECT ID, COUNTRY_ID, EMAIL_ENABLED AS email, SMS_ENABLED AS sms, WHATSAPP_ENABLED AS whatsapp,
       WHATSAPP_MESSAGE_MODE AS wa_mode, SMTP_HOST, SMTP_PORT
FROM MOHECORE.NOTIFICATION_SETTINGS ORDER BY COUNTRY_ID;

SELECT CHANNEL, STATUS, COUNT(*) AS n
FROM MOHECORE.NOTIFICATION_LOG WHERE LEFT(BATCH_ID, 12) = 'seed-notify-'
GROUP BY CHANNEL, STATUS ORDER BY CHANNEL, STATUS;
