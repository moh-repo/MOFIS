/*
    V121 — Wave 1 of the 22 Sep 2026 plan (MASTER-STATUS-AND-PLAN-2026-09-22.md).

    1. UMSCORE.Users.MustChangePassword  (W1-12, QA SEC-001, decision DEC-04)
    ------------------------------------------------------------------------
    A password an administrator sets — on a new account or by a reset — is a
    temporary one: the holder must replace it at the next sign-in. The server
    refuses everything but the password change, the profile and sign-out while
    the flag is set (MustChangePasswordFilter); a self-service change clears it.
    Existing accounts start with the flag off: nobody is locked out by the deploy.

    2. Approval configs that lived only in ad-hoc scripts  (W1-18)
    ------------------------------------------------------------------------
    Migrations create only STUDENT_DOCUMENT (V011), USER_PRIVILEGE_CHANGE and
    SYSTEM_RESTORE (V096) and WAFAR_CORRECTION (V098). The configs for the loan,
    books-allowance, extension, travel-ticket, student-Wafar and received-whom
    screens were created by docs/scripts/adhoc/seed_*_approval_config.sql, and the
    DEFAULT fallback by nothing in source control at all. A database that never
    ran those scripts answers "No approval config found" on those screens, or
    falls back to a DEFAULT that may not exist either.

    Each missing config is created with the two levels V119 settled on: level 1
    COUNTRY_ADMIN (the attaché), level 2 MINISTRY_ADMIN (the ministry). A config
    that already exists is not touched — on the development database every one
    does, so this section changes nothing there. Travel tickets and student Wafar
    carry three and four levels on dev; an environment that gets them from here
    gets the standard two, which an administrator can extend.

    Idempotent. THROWs if the tier roles are missing, or if a config is still
    missing afterwards. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

/* ---- 1. MustChangePassword ------------------------------------------------------ */
IF COL_LENGTH(N'UMSCORE.Users', N'MustChangePassword') IS NULL
BEGIN
    ALTER TABLE UMSCORE.Users
        ADD MustChangePassword BIT NOT NULL
            CONSTRAINT DF_Users_MustChangePassword DEFAULT (0);
    PRINT 'V121: UMSCORE.Users.MustChangePassword added';
END
ELSE
    PRINT 'V121: UMSCORE.Users.MustChangePassword already present';
GO

/* ---- 2. Approval configs --------------------------------------------------------- */
DECLARE @countryAdmin BIGINT = (SELECT Id FROM UMSCORE.Roles WHERE RoleName = N'COUNTRY_ADMIN');
DECLARE @ministryAdmin BIGINT = (SELECT Id FROM UMSCORE.Roles WHERE RoleName = N'MINISTRY_ADMIN');
IF @countryAdmin IS NULL OR @ministryAdmin IS NULL
    THROW 50121, 'V121: COUNTRY_ADMIN / MINISTRY_ADMIN not found in UMSCORE.Roles. Apply V094 first.', 1;

DECLARE @configs TABLE (
    EntityType NVARCHAR(100), EntityTypeAr NVARCHAR(200),
    Descr NVARCHAR(500), DescrAr NVARCHAR(500));
INSERT INTO @configs VALUES
    (N'DEFAULT',                 N'افتراضي',          N'Default approval configuration',                  N'تكوين الموافقة الافتراضي'),
    (N'STUDENT_LOAN',            N'قرض الطالب',       N'Approval workflow for student loans',             N'مسار الموافقة على قروض الطلاب'),
    (N'STUDENT_BOOKS_ALLOWANCE', N'مخصص الكتب',       N'Approval workflow for student books allowances',  N'مسار الموافقة على مخصصات الكتب'),
    (N'STUDENT_EXTENSION',       N'تمديد الطالب',     N'Student extension approval',                      N'موافقة تمديد الطالب'),
    (N'STUDENT_TRAVEL_TICKET',   N'تذاكر سفر الطالب', N'Approval workflow for student travel tickets',    N'مسار الموافقة على تذاكر سفر الطالب'),
    (N'STUDENT_FINANCE_WAFAR',   N'ادخار الطالب',     N'Approval workflow for wafar records',             N'مسار الموافقة على سجلات الادخار'),
    (N'STUDY_FIN_RECEIVED_WHOM', N'مستلم المبلغ',     N'Approval workflow for finance payment recipients', N'مسار الموافقة على مستلمي المبالغ');

DECLARE @et NVARCHAR(100), @etAr NVARCHAR(200), @d NVARCHAR(500), @dAr NVARCHAR(500), @id BIGINT, @created INT = 0;
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT EntityType, EntityTypeAr, Descr, DescrAr FROM @configs;
OPEN c;
FETCH NEXT FROM c INTO @et, @etAr, @d, @dAr;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalConfigs WHERE EntityType = @et)
    BEGIN
        INSERT INTO ALERTCORE.ApprovalConfigs
            (EntityType, EntityTypeAr, Description, DescriptionAr, TotalLevels, IsActive, RequireAllLevels, CreatedAt)
        VALUES (@et, @etAr, @d, @dAr, 2, 1, 1, SYSUTCDATETIME());
        SET @id = SCOPE_IDENTITY();

        INSERT INTO ALERTCORE.ApprovalLevels
            (ConfigId, LevelOrder, LevelName, LevelNameAr, RequiredRoleId, CanSkip, IsActive, CreatedAt)
        VALUES
            (@id, 1, N'Manager Approval',  N'موافقة المدير',       @countryAdmin,  0, 1, SYSUTCDATETIME()),
            (@id, 2, N'Director Approval', N'موافقة المدير العام', @ministryAdmin, 0, 1, SYSUTCDATETIME());

        SET @created += 1;
        PRINT CONCAT('V121: created approval config ', @et);
    END
    FETCH NEXT FROM c INTO @et, @etAr, @d, @dAr;
END
CLOSE c;
DEALLOCATE c;
PRINT CONCAT('V121: approval configs created: ', @created);
GO

/* ---- 3. Verify ------------------------------------------------------------------- */
IF COL_LENGTH(N'UMSCORE.Users', N'MustChangePassword') IS NULL
    THROW 50121, 'V121: UMSCORE.Users.MustChangePassword is missing.', 1;

IF EXISTS (
    SELECT v.EntityType
      FROM (VALUES (N'DEFAULT'), (N'STUDENT_LOAN'), (N'STUDENT_BOOKS_ALLOWANCE'), (N'STUDENT_EXTENSION'),
                   (N'STUDENT_TRAVEL_TICKET'), (N'STUDENT_FINANCE_WAFAR'), (N'STUDY_FIN_RECEIVED_WHOM')) v(EntityType)
     WHERE NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalConfigs c WHERE c.EntityType = v.EntityType))
    THROW 50121, 'V121: an approval config is still missing.', 1;
