/* ============================================================================
   V080__login_lockout_is_the_servers_decision.sql
   29 August 2026 — workmate review, page 1, item L1

   Why
   ----
   The login screen counted failed attempts in the browser: localStorage
   "loginAttempts" held {username: {count, lockoutTime}}, three tries bought a
   fifteen-minute lock, and the component simply skipped the HTTP call while it
   believed the user was locked. Clearing the key, or opening another browser,
   removed the control entirely — which is what the reviewer photographed.

   A real lock did exist on the server, but it was a different policy nobody
   could see: UmsUserService counted to a hard-coded five and then set
   Users.Status = 'LOCKED' permanently, so an ordinary typing mistake needed an
   administrator, and anyone who knew a username could lock a colleague out with
   five wrong guesses.

   The owner's decision (29 August) is an escalating ladder held in the
   database: n tries then a short lock, n1 tries then a longer one, n2 tries
   then locked until an administrator unlocks. Stages are rows so the ministry
   can retune them without a migration or a redeploy.

   What
   ----
   1. UMSCORE.Users gains LockedUntil, LockoutStage and LastFailedLoginAt.
      FailedLoginAttempts already exists and keeps its meaning: failures since
      the last success or lock. LockoutStage says how many locks the current
      run of failures has already earned, i.e. which ladder row applies next.
   2. MOHECORE.AUTH_LOCKOUT_POLICY — the ladder. LOCK_MINUTES NULL means the
      last rung: locked until an administrator unlocks. Seeded with three
      stages; stage 1 (3 tries, 15 minutes) is the policy the login screen has
      been promising all along, so nothing a user has learned changes.
   3. MOHECORE.SYS_PARAMETER gains AUTH.ATTEMPT_WINDOW_MINUTES — the age after
      which an isolated stale failure is forgotten, so one wrong password in
      March plus two in August is not read as a run of three.

   Rollback
   --------
   ALTER TABLE UMSCORE.Users DROP CONSTRAINT DF_Users_LockoutStage;
   ALTER TABLE UMSCORE.Users DROP COLUMN LockedUntil, LockoutStage, LastFailedLoginAt;
   DROP TABLE MOHECORE.AUTH_LOCKOUT_POLICY;
   DELETE FROM MOHECORE.SYS_PARAMETER WHERE PARAM_KEY = 'AUTH.ATTEMPT_WINDOW_MINUTES';

   Run: sqlcmd -S localhost,1433 -d MOHE -U sa -I -f 65001 -b -i V080__login_lockout_is_the_servers_decision.sql
   Idempotent: every step checks before it changes.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

PRINT '=== V080 step 1: UMSCORE.Users lock columns ===';

IF COL_LENGTH('UMSCORE.Users', 'LockedUntil') IS NULL
BEGIN
    ALTER TABLE UMSCORE.Users ADD LockedUntil DATETIME2 NULL;
    PRINT '  added Users.LockedUntil';
END
ELSE
    PRINT '  Users.LockedUntil already present';
GO

IF COL_LENGTH('UMSCORE.Users', 'LockoutStage') IS NULL
BEGIN
    ALTER TABLE UMSCORE.Users
        ADD LockoutStage INT NOT NULL CONSTRAINT DF_Users_LockoutStage DEFAULT 0;
    PRINT '  added Users.LockoutStage';
END
ELSE
    PRINT '  Users.LockoutStage already present';
GO

IF COL_LENGTH('UMSCORE.Users', 'LastFailedLoginAt') IS NULL
BEGIN
    ALTER TABLE UMSCORE.Users ADD LastFailedLoginAt DATETIME2 NULL;
    PRINT '  added Users.LastFailedLoginAt';
END
ELSE
    PRINT '  Users.LastFailedLoginAt already present';
GO

PRINT '=== V080 step 2: MOHECORE.AUTH_LOCKOUT_POLICY ===';

IF OBJECT_ID('MOHECORE.AUTH_LOCKOUT_POLICY', 'U') IS NULL
BEGIN
    CREATE TABLE MOHECORE.AUTH_LOCKOUT_POLICY (
        STAGE_ORDER  INT NOT NULL CONSTRAINT PK_AUTH_LOCKOUT_POLICY PRIMARY KEY,
        MAX_ATTEMPTS INT NOT NULL,
        -- NULL is the last rung: locked until an administrator unlocks.
        LOCK_MINUTES INT NULL,
        NAME_AR      NVARCHAR(200) NOT NULL,
        NAME_EN      NVARCHAR(200) NOT NULL,
        IS_ACTIVE    BIT NOT NULL CONSTRAINT DF_AUTH_LOCKOUT_POLICY_ACTIVE DEFAULT 1,
        UPDATED_BY   NVARCHAR(100) NULL,
        UPDATED_AT   DATETIME2 NULL,
        CONSTRAINT CK_AUTH_LOCKOUT_POLICY_ATTEMPTS CHECK (MAX_ATTEMPTS >= 1),
        CONSTRAINT CK_AUTH_LOCKOUT_POLICY_MINUTES  CHECK (LOCK_MINUTES IS NULL OR LOCK_MINUTES >= 1)
    );
    PRINT '  created AUTH_LOCKOUT_POLICY';
END
ELSE
    PRINT '  AUTH_LOCKOUT_POLICY already present';
GO

-- Seeded, not dictated: every number here is editable in Settings.
INSERT INTO MOHECORE.AUTH_LOCKOUT_POLICY (STAGE_ORDER, MAX_ATTEMPTS, LOCK_MINUTES, NAME_AR, NAME_EN)
SELECT v.STAGE_ORDER, v.MAX_ATTEMPTS, v.LOCK_MINUTES, v.NAME_AR, v.NAME_EN
FROM (VALUES
        (1, 3, 15,   N'ثلاث محاولات ثم قفل ربع ساعة',        N'Three tries, then locked for 15 minutes'),
        (2, 3, 60,   N'ثلاث محاولات أخرى ثم قفل ساعة',        N'Three more tries, then locked for an hour'),
        (3, 3, NULL, N'ثلاث محاولات أخرى ثم قفل حتى يفتحه المسؤول', N'Three more tries, then locked until an administrator unlocks')
     ) AS v(STAGE_ORDER, MAX_ATTEMPTS, LOCK_MINUTES, NAME_AR, NAME_EN)
WHERE NOT EXISTS (
        SELECT 1 FROM MOHECORE.AUTH_LOCKOUT_POLICY p WHERE p.STAGE_ORDER = v.STAGE_ORDER);
PRINT '  seeded ladder stages: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' inserted';
GO

PRINT '=== V080 step 3: SYS_PARAMETER attempt window ===';

INSERT INTO MOHECORE.SYS_PARAMETER (PARAM_KEY, PARAM_VALUE, VALUE_TYPE, NAME_AR, NAME_EN, DESCRIPTION)
SELECT N'AUTH.ATTEMPT_WINDOW_MINUTES', N'60', N'INT',
       N'مدة احتساب محاولات الدخول الفاشلة (بالدقائق)',
       N'Failed sign-in attempt window (minutes)',
       N'A failure older than this is forgotten, so isolated mistakes months apart are not counted as one run.'
WHERE NOT EXISTS (
        SELECT 1 FROM MOHECORE.SYS_PARAMETER WHERE PARAM_KEY = N'AUTH.ATTEMPT_WINDOW_MINUTES');
PRINT '  seeded AUTH.ATTEMPT_WINDOW_MINUTES: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' inserted';
GO

PRINT '=== V080 verification ===';
DECLARE @problems NVARCHAR(MAX) = N'';

IF COL_LENGTH('UMSCORE.Users', 'LockedUntil') IS NULL
    SET @problems = @problems + N'Users.LockedUntil missing; ';
IF COL_LENGTH('UMSCORE.Users', 'LockoutStage') IS NULL
    SET @problems = @problems + N'Users.LockoutStage missing; ';
IF COL_LENGTH('UMSCORE.Users', 'LastFailedLoginAt') IS NULL
    SET @problems = @problems + N'Users.LastFailedLoginAt missing; ';
IF OBJECT_ID('MOHECORE.AUTH_LOCKOUT_POLICY', 'U') IS NULL
    SET @problems = @problems + N'AUTH_LOCKOUT_POLICY missing; ';
ELSE
BEGIN
    IF NOT EXISTS (SELECT 1 FROM MOHECORE.AUTH_LOCKOUT_POLICY WHERE IS_ACTIVE = 1)
        SET @problems = @problems + N'AUTH_LOCKOUT_POLICY has no active stage; ';
    -- The ladder must end somewhere a person can reach: exactly one last rung.
    IF (SELECT COUNT(*) FROM MOHECORE.AUTH_LOCKOUT_POLICY WHERE IS_ACTIVE = 1 AND LOCK_MINUTES IS NULL) > 1
        SET @problems = @problems + N'more than one open-ended stage; ';
END
IF NOT EXISTS (SELECT 1 FROM MOHECORE.SYS_PARAMETER WHERE PARAM_KEY = N'AUTH.ATTEMPT_WINDOW_MINUTES')
    SET @problems = @problems + N'AUTH.ATTEMPT_WINDOW_MINUTES missing; ';

IF @problems <> N''
    THROW 50080, @problems, 1;

PRINT '  OK — lock columns, ladder and attempt window all present';
SELECT STAGE_ORDER, MAX_ATTEMPTS, LOCK_MINUTES, NAME_EN, IS_ACTIVE
FROM MOHECORE.AUTH_LOCKOUT_POLICY
ORDER BY STAGE_ORDER;
GO
