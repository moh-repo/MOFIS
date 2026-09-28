/* ============================================================================
   V074 — Four status columns that still had no code list.

   Why
   ---
   While replacing the last literals (30 Aug 2026) four string columns turned
   out to have no table at all: MOHE_ALERTS.alerts.alert_status (the system
   notices), STUDENT_DEATH_CASE.CASE_STATUS, SCHEDULED_REPORT.SCHEDULE_STATUS
   and SCHEDULED_REPORT.LAST_RUN_STATUS. The Java constants for them now exist
   (codes/NoticeStatus, DeathCaseStatus, ScheduleStatus, RunStatus) and the
   startup guard refuses to boot until the tables agree. NotificationLog also
   writes PENDING before a batch is sent, which V073's send-status list lacked.

   What
   ----
   1. MOHECORE.LU_NOTICE_STATUS, LU_DEATH_CASE_STATUS, LU_SCHEDULE_STATUS,
      LU_RUN_STATUS — the V072 shape, seeded, the codes Java names flagged
      IS_SYSTEM.
   2. PENDING added to LU_NOTIFICATION_SEND_STATUS.

   Rollback: DROP the four tables; DELETE the PENDING row.
   Idempotent. Run with sqlcmd -I -f 65001 -b after V073.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF OBJECT_ID('MOHECORE.LU_NOTIFICATION_SEND_STATUS', 'U') IS NULL
    THROW 50074, 'V074: V073 has not been applied — run it first.', 1;
GO

DECLARE @new TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @new (name) VALUES ('LU_NOTICE_STATUS'), ('LU_DEATH_CASE_STATUS'), ('LU_SCHEDULE_STATUS'), ('LU_RUN_STATUS');

DECLARE @t SYSNAME, @sql NVARCHAR(MAX), @created INT = 0;
DECLARE ncur CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM @new ORDER BY name;
OPEN ncur;
FETCH NEXT FROM ncur INTO @t;
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
                 + N' IS_SYSTEM BIT NOT NULL CONSTRAINT DF_' + @t + N'_IS_SYSTEM DEFAULT 0,'
                 + N' CONSTRAINT UQ_' + @t + N'_CODE UNIQUE (CODE));';
        EXEC sp_executesql @sql;
        SET @created += 1;
    END
    FETCH NEXT FROM ncur INTO @t;
END
CLOSE ncur; DEALLOCATE ncur;
PRINT 'V074: tables created = ' + CAST(@created AS VARCHAR(10));

DECLARE @seed TABLE (tbl SYSNAME, code NVARCHAR(50), name_ar NVARCHAR(200), name_en NVARCHAR(200),
                     sort_order INT, sev NVARCHAR(20) NULL, icon NVARCHAR(50) NULL, sys BIT);
INSERT INTO @seed VALUES
  ('LU_NOTICE_STATUS', 'ACTIVE',    N'نشط',    N'Active',    1, 'info',      'pi-bell',  1),
  ('LU_NOTICE_STATUS', 'RESOLVED',  N'محلول',  N'Resolved',  2, 'success',   'pi-check', 1),
  ('LU_NOTICE_STATUS', 'DISMISSED', N'مرفوض',  N'Dismissed', 3, 'secondary', 'pi-times', 1),
  ('LU_DEATH_CASE_STATUS', 'OPEN',   N'مفتوحة', N'Open',   1, 'warn',      'pi-folder-open', 1),
  ('LU_DEATH_CASE_STATUS', 'CLOSED', N'مغلقة',  N'Closed', 2, 'secondary', 'pi-lock',        1),
  ('LU_SCHEDULE_STATUS', 'ACTIVE', N'نشطة',   N'Active', 1, 'success',   'pi-play',  1),
  ('LU_SCHEDULE_STATUS', 'PAUSED', N'متوقفة', N'Paused', 2, 'secondary', 'pi-pause', 1),
  ('LU_RUN_STATUS', 'RUNNING', N'قيد التشغيل', N'Running', 1, 'info',    'pi-spinner', 1),
  ('LU_RUN_STATUS', 'SUCCESS', N'نجح',         N'Success', 2, 'success', 'pi-check',   1),
  ('LU_RUN_STATUS', 'FAILED',  N'فشل',         N'Failed',  3, 'danger',  'pi-times',   1),
  ('LU_NOTIFICATION_SEND_STATUS', 'PENDING', N'قيد الإرسال', N'Pending', 0, 'secondary', 'pi-clock', 1);

DECLARE @code NVARCHAR(50), @name_ar NVARCHAR(200), @name_en NVARCHAR(200), @sort INT, @sev NVARCHAR(20),
        @icon NVARCHAR(50), @sys BIT, @inserted INT = 0, @n INT;
DECLARE scur CURSOR LOCAL FAST_FORWARD FOR SELECT tbl, code, name_ar, name_en, sort_order, sev, icon, sys FROM @seed;
OPEN scur;
FETCH NEXT FROM scur INTO @t, @code, @name_ar, @name_en, @sort, @sev, @icon, @sys;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'DECLARE @before INT = (SELECT COUNT(*) FROM MOHECORE.' + QUOTENAME(@t) + N' WHERE CODE = @code);'
             + N' MERGE MOHECORE.' + QUOTENAME(@t) + N' AS tgt'
             + N' USING (SELECT @code AS CODE) AS src ON tgt.CODE = src.CODE'
             + N' WHEN MATCHED THEN UPDATE SET UI_SEVERITY = COALESCE(tgt.UI_SEVERITY, @sev), ICON = COALESCE(tgt.ICON, @icon),'
             + N'      IS_SYSTEM = CASE WHEN @sys = 1 THEN 1 ELSE tgt.IS_SYSTEM END'
             + N' WHEN NOT MATCHED THEN INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, UI_SEVERITY, ICON, IS_SYSTEM)'
             + N' VALUES (@code, @name_ar, @name_en, @sort, @sev, @icon, @sys);'
             + N' SELECT @n = 1 - @before;';
    EXEC sp_executesql @sql,
         N'@code NVARCHAR(50), @name_ar NVARCHAR(200), @name_en NVARCHAR(200), @sort INT, @sev NVARCHAR(20), @icon NVARCHAR(50), @sys BIT, @n INT OUTPUT',
         @code = @code, @name_ar = @name_ar, @name_en = @name_en, @sort = @sort, @sev = @sev, @icon = @icon, @sys = @sys, @n = @n OUTPUT;
    SET @inserted += @n;
    FETCH NEXT FROM scur INTO @t, @code, @name_ar, @name_en, @sort, @sev, @icon, @sys;
END
CLOSE scur; DEALLOCATE scur;
PRINT 'V074: code rows inserted = ' + CAST(@inserted AS VARCHAR(10));

PRINT '=== V074 verification ===';
SELECT 'LU_NOTICE_STATUS' AS tbl, COUNT(*) AS n FROM MOHECORE.LU_NOTICE_STATUS
UNION ALL SELECT 'LU_DEATH_CASE_STATUS', COUNT(*) FROM MOHECORE.LU_DEATH_CASE_STATUS
UNION ALL SELECT 'LU_SCHEDULE_STATUS', COUNT(*) FROM MOHECORE.LU_SCHEDULE_STATUS
UNION ALL SELECT 'LU_RUN_STATUS', COUNT(*) FROM MOHECORE.LU_RUN_STATUS
UNION ALL SELECT 'LU_NOTIFICATION_SEND_STATUS', COUNT(*) FROM MOHECORE.LU_NOTIFICATION_SEND_STATUS;
GO
