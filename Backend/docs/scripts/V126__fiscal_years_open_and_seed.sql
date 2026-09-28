/*
    V126 — Fiscal years: open them from the screen, seed the ones the quarters
    need, and remove the stray quarter 2030 Q1 (plan W4-01; review F11, DEC-20).

    The fiscal calendar held the three years V040 seeded (2024-2026) and nothing
    could add another, so a quarter in any later year took no allocation and no
    payment batch: "No fiscal year covers quarter 20301". The allocation and
    batch screens also defaulted to the newest quarter, which on the review
    database was a test row for 2030.

    1. FISCAL_YEAR.ADD, the permission behind POST /accounting/fiscal-years and
       the "Open fiscal year" button on the budget appropriations page, granted
       to every active role that may close periods (FISCAL_PERIOD.CLOSE): whoever
       keeps the calendar. PermissionCatalogStartupGuard refuses to start
       outside dev until the code exists.
    2. Quarter 20301 is deleted, as DEC-20 decided, but only while nothing refers
       to it. Creating a quarter rolls every student and finance row over into
       it, and that cannot be undone by deleting the row; if anything points at
       it the script says what and leaves it for a hand correction.
    3. A fiscal year, with its twelve monthly periods and adjustment period 13,
       for every quarter year and the current year that has none, laid out as
       V040 laid out the first three. The chart of accounts is kept per year and
       is not rolled forward here (DEC-20).

    Rollback of 1:
        DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
         WHERE p.PermissionCode = N'FISCAL_YEAR.ADD';
        DELETE FROM UMSCORE.Permissions WHERE PermissionCode = N'FISCAL_YEAR.ADD';

    Idempotent. THROWs if the permission is missing afterwards.
    Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- 1. The permission ------------------------------------------------------- */
UPDATE UMSCORE.Permissions SET IsActive = 1, UpdatedAt = SYSUTCDATETIME()
 WHERE PermissionCode = N'FISCAL_YEAR.ADD' AND IsActive = 0;
PRINT CONCAT('V126: FISCAL_YEAR.ADD reactivated: ', @@ROWCOUNT);

INSERT INTO UMSCORE.Permissions
       (PermissionCode, PermissionName, PermissionNameAr, ResourceType, ResourceId, ActionId,
        ActionType, PermissionLevel, Description, IsActive, CreatedAt)
SELECT N'FISCAL_YEAR.ADD', N'Open Fiscal Year', N'فتح سنة مالية', c.ResourceType, c.ResourceId,
       NULL, N'ADD', c.PermissionLevel, N'Open a new fiscal year and its 13 periods', 1, SYSUTCDATETIME()
  FROM UMSCORE.Permissions c
 WHERE c.PermissionCode = N'FISCAL_PERIOD.CLOSE'
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions p WHERE p.PermissionCode = N'FISCAL_YEAR.ADD');
PRINT CONCAT('V126: FISCAL_YEAR.ADD inserted: ', @@ROWCOUNT);

-- A database without FISCAL_PERIOD.CLOSE to copy from still gets the code.
INSERT INTO UMSCORE.Permissions
       (PermissionCode, PermissionName, PermissionNameAr, ResourceType, ResourceId, ActionId,
        ActionType, PermissionLevel, Description, IsActive, CreatedAt)
SELECT N'FISCAL_YEAR.ADD', N'Open Fiscal Year', N'فتح سنة مالية', N'PAGE', NULL,
       NULL, N'ADD', N'READ', N'Open a new fiscal year and its 13 periods', 1, SYSUTCDATETIME()
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions p WHERE p.PermissionCode = N'FISCAL_YEAR.ADD');
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive)
SELECT DISTINCT rp.RoleId, np.Id, SYSUTCDATETIME(), 1
  FROM UMSCORE.RolePermissions rp
  JOIN UMSCORE.Permissions op ON op.Id = rp.PermissionId AND op.PermissionCode = N'FISCAL_PERIOD.CLOSE'
  JOIN UMSCORE.Roles r ON r.Id = rp.RoleId AND r.IsActive = 1
 CROSS JOIN UMSCORE.Permissions np
 WHERE np.PermissionCode = N'FISCAL_YEAR.ADD'
   AND rp.IsActive = 1
   AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions x WHERE x.RoleId = rp.RoleId AND x.PermissionId = np.Id);
PRINT CONCAT('V126: FISCAL_YEAR.ADD role grants: ', @@ROWCOUNT);
GO

/* ---- 2. Quarter 20301 ---------------------------------------------------------- */
SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @key int = 20301;
DECLARE @id int = (SELECT TOP 1 ID FROM MOHECORE.MOHE_QUARTER WHERE QUARTER = @key);

IF @id IS NULL
    PRINT 'V126: quarter 20301 does not exist; nothing to delete.';
ELSE
BEGIN
    DECLARE @refs TABLE (what nvarchar(100), n int);
    INSERT INTO @refs
    SELECT N'ATTACHE_ALLOCATION', COUNT(*) FROM FINCORE.ATTACHE_ALLOCATION WHERE MOHE_QUARTER_KEY = @key
    UNION ALL SELECT N'PAYMENT_BATCH', COUNT(*) FROM FINCORE.PAYMENT_BATCH WHERE MOHE_QUARTER_KEY = @key
    UNION ALL SELECT N'WAFAR_EARMARK', COUNT(*) FROM FINCORE.WAFAR_EARMARK WHERE MOHE_QUARTER_KEY = @key
    UNION ALL SELECT N'WAFAR_USE', COUNT(*) FROM FINCORE.WAFAR_USE WHERE MOHE_QUARTER_KEY = @key
    UNION ALL SELECT N'STUDENT_FINANCE', COUNT(*) FROM MOHECORE.STUDENT_FINANCE WHERE MOHE_QUARTER_ID = @key
    UNION ALL SELECT N'STUDENT_FINANCE_ARCH', COUNT(*) FROM MOHECORE.STUDENT_FINANCE_ARCH WHERE MOHE_QUARTER_ID = @key
    UNION ALL SELECT N'STUDENT_FINANCE_PAYMENT', COUNT(*) FROM MOHECORE.STUDENT_FINANCE_PAYMENT WHERE MOHE_QUARTER_ID = @key
    UNION ALL SELECT N'STUDENT_LOAN', COUNT(*) FROM MOHECORE.STUDENT_LOAN WHERE MOHE_QUARTER_ID = @key
    UNION ALL SELECT N'STUDENT_BOOKS_ALLOWANCE', COUNT(*) FROM MOHECORE.STUDENT_BOOKS_ALLOWANCE WHERE MOHE_QUARTER_ID = @key
    UNION ALL SELECT N'STUDENT_YEAR_RESULT', COUNT(*) FROM MOHECORE.STUDENT_YEAR_RESULT WHERE MoheQuarterId = @key
    UNION ALL SELECT N'STUDENT_DATA (last quarter)', COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE STUDENT_LAST_QUARTER = @id
    UNION ALL SELECT N'STUDENT_DATA (current quarter)', COUNT(*) FROM MOHECORE.STUDENT_DATA
               WHERE CAST(CURRENT_QUARTER AS nvarchar(20)) = CAST(@key AS nvarchar(20))
    UNION ALL SELECT N'STUDENT_DATA_ARCH (last quarter)', COUNT(*) FROM MOHECORE.STUDENT_DATA_ARCH WHERE STUDENT_LAST_QUARTER = @id
    UNION ALL SELECT N'STUDENT_DATA_ARCH (current quarter)', COUNT(*) FROM MOHECORE.STUDENT_DATA_ARCH
               WHERE CAST(CURRENT_QUARTER AS nvarchar(20)) = CAST(@key AS nvarchar(20));

    IF EXISTS (SELECT 1 FROM @refs WHERE n > 0)
    BEGIN
        PRINT 'V126: quarter 20301 is in use and was NOT deleted. A created quarter rolled the students over';
        PRINT '      into it; undo that by hand (or restore the database) before deleting it. In use by:';
        DECLARE @line nvarchar(200);
        DECLARE refs CURSOR LOCAL FAST_FORWARD FOR
            SELECT CONCAT(N'        ', what, N': ', n) FROM @refs WHERE n > 0;
        OPEN refs;
        FETCH NEXT FROM refs INTO @line;
        WHILE @@FETCH_STATUS = 0
        BEGIN
            PRINT @line;
            FETCH NEXT FROM refs INTO @line;
        END
        CLOSE refs;
        DEALLOCATE refs;
    END
    ELSE
    BEGIN
        DELETE FROM MOHECORE.MOHE_QUARTER WHERE ID = @id;
        PRINT 'V126: quarter 20301 deleted (nothing referred to it).';
    END
END
GO

/* ---- 3. A fiscal year for every quarter year and the current year ------------- */
SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @years TABLE (y int PRIMARY KEY);
INSERT INTO @years (y)
SELECT DISTINCT q.QUARTER / 10 FROM MOHECORE.MOHE_QUARTER q WHERE q.QUARTER IS NOT NULL
UNION
SELECT YEAR(SYSDATETIME());

DECLARE @y int;
DECLARE years CURSOR LOCAL FAST_FORWARD FOR
    SELECT y FROM @years WHERE NOT EXISTS (SELECT 1 FROM FINCORE.FISCAL_YEAR f WHERE f.YEAR_NO = y) ORDER BY y;
OPEN years;
FETCH NEXT FROM years INTO @y;
WHILE @@FETCH_STATUS = 0
BEGIN
    INSERT INTO FINCORE.FISCAL_YEAR (YEAR_NO, NAME_AR, NAME_EN, START_DATE, END_DATE, STATUS)
    VALUES (@y, N'السنة المالية ' + CAST(@y AS nvarchar(4)), N'Fiscal Year ' + CAST(@y AS nvarchar(4)),
            DATEFROMPARTS(@y, 1, 1), DATEFROMPARTS(@y, 12, 31), 'OPEN');
    DECLARE @fyId int = SCOPE_IDENTITY();

    DECLARE @p int = 1;
    WHILE @p <= 12
    BEGIN
        INSERT INTO FINCORE.FISCAL_PERIOD
            (FISCAL_YEAR_ID, PERIOD_NO, NAME_AR, NAME_EN, START_DATE, END_DATE, STATUS, IS_ADJUSTMENT)
        VALUES (@fyId, @p,
                N'الفترة ' + CAST(@p AS nvarchar(2)) + N'/' + CAST(@y AS nvarchar(4)),
                N'Period ' + CAST(@p AS nvarchar(2)) + N'/' + CAST(@y AS nvarchar(4)),
                DATEFROMPARTS(@y, @p, 1), EOMONTH(DATEFROMPARTS(@y, @p, 1)), 'OPEN', 0);
        SET @p += 1;
    END
    INSERT INTO FINCORE.FISCAL_PERIOD
        (FISCAL_YEAR_ID, PERIOD_NO, NAME_AR, NAME_EN, START_DATE, END_DATE, STATUS, IS_ADJUSTMENT)
    VALUES (@fyId, 13, N'فترة التسويات ' + CAST(@y AS nvarchar(4)), N'Adjustment Period ' + CAST(@y AS nvarchar(4)),
            DATEFROMPARTS(@y, 12, 31), DATEFROMPARTS(@y, 12, 31), 'OPEN', 1);

    PRINT CONCAT('V126: fiscal year ', @y, ' seeded with 13 periods.');
    FETCH NEXT FROM years INTO @y;
END
CLOSE years;
DEALLOCATE years;
GO

/* ---- Checks ------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM UMSCORE.Permissions WHERE PermissionCode = N'FISCAL_YEAR.ADD' AND IsActive = 1)
    THROW 51260, 'V126: FISCAL_YEAR.ADD is missing', 1;
