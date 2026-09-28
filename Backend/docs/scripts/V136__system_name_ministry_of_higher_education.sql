/*
    V136 — the system's own name (live QA L4, 23 Sep 2026).

    The top bar reads MOHECORE.SYS_SETTINGS (SYS_NAME_EN / SYS_NAME_AR, the row
    Settings → System Settings edits) and printed "Ministry of Finance" /
    "وزارة المالية" — the placeholder the row was seeded with — under a footer that
    reads "Ministry of Higher Education". This is the ministry's system: the two
    columns become "Ministry of Higher Education" / "وزارة التعليم العالي".

    Each column is corrected on its own and only while it still holds the
    placeholder, so a name an administrator has already changed on the screen is
    left exactly as they set it.

    Idempotent. Run by Flyway at startup, or by hand with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF OBJECT_ID(N'MOHECORE.SYS_SETTINGS', N'U') IS NULL
BEGIN
    PRINT 'V136: MOHECORE.SYS_SETTINGS absent — nothing to do';
    RETURN;
END

UPDATE MOHECORE.SYS_SETTINGS
   SET SYS_NAME_EN = 'Ministry of Higher Education'
 WHERE LTRIM(RTRIM(SYS_NAME_EN)) = 'Ministry of Finance';
PRINT 'V136: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' English name(s) corrected';

UPDATE MOHECORE.SYS_SETTINGS
   SET SYS_NAME_AR = N'وزارة التعليم العالي'
 WHERE LTRIM(RTRIM(SYS_NAME_AR)) = N'وزارة المالية';
PRINT 'V136: ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' Arabic name(s) corrected';
