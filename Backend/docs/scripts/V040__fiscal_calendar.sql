/*
    V040 — fiscal years and periods.

    Why
    ---
    A ledger needs to know which periods are open. Without that, a correction
    posted today can land in a year whose final account has already gone to COCA,
    and the figures that were reported stop matching the figures in the database.

    The database has no such concept. The closest thing is MOHECORE.MOHE_QUARTER,
    which has a QuarterClosed flag -- but that is the scholarship payment cycle
    (which quarter's money is being paid), not an accounting calendar. The two
    are related and both are needed: an allocation belongs to a payment quarter
    AND is posted in an accounting period. MOHE_QUARTER is therefore referenced,
    not replaced.

    Structure
    ---------
    FISCAL_YEAR   one row per year, OPEN -> CLOSED -> LOCKED.
    FISCAL_PERIOD 12 monthly periods plus period 13.

    Period 13 carries no calendar days of its own -- it shares 31 December -- and
    exists for قيود التسوية, the year-end adjusting entries. Without it, an
    adjustment agreed in March against last year has nowhere to go except
    December, where it silently changes a month that was already reported.

    Two rules the database enforces rather than trusting the application:

      * a period cannot be open inside a closed year;
      * closing is one-way.

    Reopening a closed period is possible but deliberately awkward: the caller
    must set SESSION_CONTEXT('fincore_allow_reopen') first, the same mechanism
    TenantContext already uses for scope. That makes reopening an explicit,
    auditable act rather than an UPDATE that happens to succeed.

    The calendar-year assumption
    ----------------------------
    Years are seeded as 1 January to 31 December. That is the common case and it
    matches the Gregorian years already present in the data (2024, 2025, 2026),
    but it is an ASSUMPTION about Yemeni practice, not something this script can
    verify. If the fiscal year runs on other dates, update FISCAL_YEAR.START_DATE
    and END_DATE and regenerate the periods -- nothing else depends on the
    boundaries.

    Rollback
    --------
        DROP TRIGGER FINCORE.TR_FISCAL_PERIOD_CLOSE_RULES;
        DROP TRIGGER FINCORE.TR_FISCAL_YEAR_NO_OVERLAP;
        DROP TABLE   FINCORE.FISCAL_PERIOD;
        DROP TABLE   FINCORE.FISCAL_YEAR;
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF OBJECT_ID('FINCORE.FISCAL_YEAR', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.FISCAL_YEAR (
        FISCAL_YEAR_ID  int           IDENTITY(1,1) NOT NULL,
        YEAR_NO         int           NOT NULL,
        NAME_AR         nvarchar(100) NULL,
        NAME_EN         nvarchar(100) NULL,
        START_DATE      date          NOT NULL,
        END_DATE        date          NOT NULL,
        STATUS          varchar(10)   NOT NULL
                        CONSTRAINT DF_FISCAL_YEAR_STATUS DEFAULT ('OPEN'),
        CLOSED_BY       int           NULL,
        CLOSED_AT       datetime2(3)  NULL,
        -- NULL means ministry-wide, the convention MOHE_QUARTER.COUNTRY_ID has
        -- used since V021 and the V031 tenant predicate already understands.
        -- A fiscal year is national, so this stays NULL in normal use.
        COUNTRY_ID      int           NULL,
        CREATED_BY      int           NULL,
        CREATED_AT      datetime2(3)  NOT NULL
                        CONSTRAINT DF_FISCAL_YEAR_CREATED_AT DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_FISCAL_YEAR          PRIMARY KEY (FISCAL_YEAR_ID),
        CONSTRAINT UX_FISCAL_YEAR_YEAR_NO  UNIQUE (YEAR_NO),
        CONSTRAINT CK_FISCAL_YEAR_DATES    CHECK (END_DATE > START_DATE),
        CONSTRAINT CK_FISCAL_YEAR_STATUS   CHECK (STATUS IN ('OPEN', 'CLOSED', 'LOCKED')),
        -- A closed year must say who closed it and when; an unattributed close
        -- is not something COCA can audit.
        CONSTRAINT CK_FISCAL_YEAR_CLOSE_ATTRIBUTED
            CHECK (STATUS = 'OPEN' OR CLOSED_AT IS NOT NULL)
    );
    PRINT 'V040: created FINCORE.FISCAL_YEAR.';
END
ELSE
    PRINT 'V040: FINCORE.FISCAL_YEAR already present.';
GO

IF OBJECT_ID('FINCORE.FISCAL_PERIOD', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.FISCAL_PERIOD (
        FISCAL_PERIOD_ID int           IDENTITY(1,1) NOT NULL,
        FISCAL_YEAR_ID   int           NOT NULL,
        PERIOD_NO        tinyint       NOT NULL,
        NAME_AR          nvarchar(100) NULL,
        NAME_EN          nvarchar(100) NULL,
        START_DATE       date          NOT NULL,
        END_DATE         date          NOT NULL,
        STATUS           varchar(10)   NOT NULL
                         CONSTRAINT DF_FISCAL_PERIOD_STATUS DEFAULT ('OPEN'),
        -- Period 13: the adjustment period. It has no calendar days of its own.
        IS_ADJUSTMENT    bit           NOT NULL
                         CONSTRAINT DF_FISCAL_PERIOD_IS_ADJ DEFAULT (0),
        CLOSED_BY        int           NULL,
        CLOSED_AT        datetime2(3)  NULL,
        CREATED_AT       datetime2(3)  NOT NULL
                         CONSTRAINT DF_FISCAL_PERIOD_CREATED_AT DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_FISCAL_PERIOD PRIMARY KEY (FISCAL_PERIOD_ID),
        CONSTRAINT UX_FISCAL_PERIOD_YEAR_NO UNIQUE (FISCAL_YEAR_ID, PERIOD_NO),
        CONSTRAINT FK_FISCAL_PERIOD_YEAR FOREIGN KEY (FISCAL_YEAR_ID)
            REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT CK_FISCAL_PERIOD_NO     CHECK (PERIOD_NO BETWEEN 1 AND 13),
        CONSTRAINT CK_FISCAL_PERIOD_DATES  CHECK (END_DATE >= START_DATE),
        CONSTRAINT CK_FISCAL_PERIOD_STATUS CHECK (STATUS IN ('OPEN', 'CLOSED')),
        CONSTRAINT CK_FISCAL_PERIOD_ADJ_IS_13
            CHECK ((IS_ADJUSTMENT = 1 AND PERIOD_NO = 13)
                OR (IS_ADJUSTMENT = 0 AND PERIOD_NO BETWEEN 1 AND 12))
    );
    CREATE INDEX IX_FISCAL_PERIOD_DATES ON FINCORE.FISCAL_PERIOD (START_DATE, END_DATE);
    PRINT 'V040: created FINCORE.FISCAL_PERIOD.';
END
ELSE
    PRINT 'V040: FINCORE.FISCAL_PERIOD already present.';
GO

/* ------------------------------------------------------------------ *
 * Fiscal years must not overlap. Two open years covering the same day
 * means a transaction has two possible homes.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.TR_FISCAL_YEAR_NO_OVERLAP', 'TR') IS NOT NULL
    DROP TRIGGER FINCORE.TR_FISCAL_YEAR_NO_OVERLAP;
GO

CREATE TRIGGER FINCORE.TR_FISCAL_YEAR_NO_OVERLAP
ON FINCORE.FISCAL_YEAR
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN FINCORE.FISCAL_YEAR y
          ON y.FISCAL_YEAR_ID <> i.FISCAL_YEAR_ID
         AND i.START_DATE <= y.END_DATE
         AND y.START_DATE <= i.END_DATE
    )
    BEGIN
        THROW 50040, 'Fiscal years must not overlap: a date would fall in two years.', 1;
    END
END
GO
PRINT 'V040: created TR_FISCAL_YEAR_NO_OVERLAP.';
GO

/* ------------------------------------------------------------------ *
 * Period closing rules:
 *   - no open period inside a closed year
 *   - closing is one-way unless the caller explicitly asks to reopen
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.TR_FISCAL_PERIOD_CLOSE_RULES', 'TR') IS NOT NULL
    DROP TRIGGER FINCORE.TR_FISCAL_PERIOD_CLOSE_RULES;
GO

CREATE TRIGGER FINCORE.TR_FISCAL_PERIOD_CLOSE_RULES
ON FINCORE.FISCAL_PERIOD
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    /* A period may not be open inside a year that is closed or locked. */
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN FINCORE.FISCAL_YEAR y ON y.FISCAL_YEAR_ID = i.FISCAL_YEAR_ID
        WHERE i.STATUS = 'OPEN' AND y.STATUS <> 'OPEN'
    )
    BEGIN
        THROW 50041, 'A period cannot be open inside a closed or locked fiscal year.', 1;
    END

    /* Closing is one-way. Reopening requires an explicit, auditable act:
           EXEC sp_set_session_context @key = N'fincore_allow_reopen', @value = 1;
       Modelled on the SESSION_CONTEXT mechanism TenantContext already uses. */
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN deleted  d ON d.FISCAL_PERIOD_ID = i.FISCAL_PERIOD_ID
        WHERE d.STATUS = 'CLOSED' AND i.STATUS = 'OPEN'
    )
    AND ISNULL(TRY_CAST(SESSION_CONTEXT(N'fincore_allow_reopen') AS int), 0) <> 1
    BEGIN
        THROW 50042, 'A closed period cannot be reopened. Set SESSION_CONTEXT(''fincore_allow_reopen'') = 1 to override deliberately.', 1;
    END
END
GO
PRINT 'V040: created TR_FISCAL_PERIOD_CLOSE_RULES.';
GO

/* ------------------------------------------------------------------ *
 * Seed the years present in the data, on the calendar-year assumption
 * stated in the header. Only runs when the table is empty.
 * ------------------------------------------------------------------ */
IF NOT EXISTS (SELECT 1 FROM FINCORE.FISCAL_YEAR)
BEGIN
    DECLARE @y int = 2024;
    WHILE @y <= 2026
    BEGIN
        INSERT INTO FINCORE.FISCAL_YEAR (YEAR_NO, NAME_AR, NAME_EN, START_DATE, END_DATE, STATUS)
        VALUES (@y,
                N'السنة المالية ' + CAST(@y AS nvarchar(4)),
                N'Fiscal Year ' + CAST(@y AS nvarchar(4)),
                DATEFROMPARTS(@y, 1, 1),
                DATEFROMPARTS(@y, 12, 31),
                'OPEN');

        DECLARE @fyId int = SCOPE_IDENTITY();
        DECLARE @p int = 1;

        WHILE @p <= 12
        BEGIN
            INSERT INTO FINCORE.FISCAL_PERIOD
                (FISCAL_YEAR_ID, PERIOD_NO, NAME_AR, NAME_EN, START_DATE, END_DATE, STATUS, IS_ADJUSTMENT)
            VALUES (@fyId, @p,
                    N'الفترة ' + CAST(@p AS nvarchar(2)) + N'/' + CAST(@y AS nvarchar(4)),
                    N'Period ' + CAST(@p AS nvarchar(2)) + N'/' + CAST(@y AS nvarchar(4)),
                    DATEFROMPARTS(@y, @p, 1),
                    EOMONTH(DATEFROMPARTS(@y, @p, 1)),
                    'OPEN', 0);
            SET @p += 1;
        END

        /* Period 13 -- adjustments. Shares 31 December; carries no days of its own. */
        INSERT INTO FINCORE.FISCAL_PERIOD
            (FISCAL_YEAR_ID, PERIOD_NO, NAME_AR, NAME_EN, START_DATE, END_DATE, STATUS, IS_ADJUSTMENT)
        VALUES (@fyId, 13,
                N'فترة التسويات ' + CAST(@y AS nvarchar(4)),
                N'Adjustment Period ' + CAST(@y AS nvarchar(4)),
                DATEFROMPARTS(@y, 12, 31),
                DATEFROMPARTS(@y, 12, 31),
                'OPEN', 1);

        SET @y += 1;
    END
    PRINT 'V040: seeded fiscal years 2024-2026 with 13 periods each.';
END
ELSE
    PRINT 'V040: FISCAL_YEAR already populated; seed skipped.';
GO

/* ------------------------------------------------------------------ *
 * Verification
 * ------------------------------------------------------------------ */
PRINT '';
PRINT '================ V040 verification ================';

PRINT '--- years: each must have exactly 13 periods (12 + adjustment) ---';
SELECT y.YEAR_NO, y.START_DATE, y.END_DATE, y.STATUS,
       COUNT(p.FISCAL_PERIOD_ID) AS periods,
       SUM(CAST(p.IS_ADJUSTMENT AS int)) AS adjustment_periods
FROM FINCORE.FISCAL_YEAR y
LEFT JOIN FINCORE.FISCAL_PERIOD p ON p.FISCAL_YEAR_ID = y.FISCAL_YEAR_ID
GROUP BY y.YEAR_NO, y.START_DATE, y.END_DATE, y.STATUS
ORDER BY y.YEAR_NO;

PRINT '';
PRINT '--- overlapping years (must be 0) ---';
SELECT COUNT(*) AS overlapping_year_pairs
FROM FINCORE.FISCAL_YEAR a
JOIN FINCORE.FISCAL_YEAR b
  ON a.FISCAL_YEAR_ID < b.FISCAL_YEAR_ID
 AND a.START_DATE <= b.END_DATE
 AND b.START_DATE <= a.END_DATE;

PRINT '';
PRINT '--- months 1-12 must tile the year with no gap or overlap (must be 0) ---';
SELECT COUNT(*) AS non_contiguous_periods
FROM FINCORE.FISCAL_PERIOD p
JOIN FINCORE.FISCAL_PERIOD nxt
  ON nxt.FISCAL_YEAR_ID = p.FISCAL_YEAR_ID
 AND nxt.PERIOD_NO      = p.PERIOD_NO + 1
WHERE p.PERIOD_NO < 12
  AND DATEADD(DAY, 1, p.END_DATE) <> nxt.START_DATE;

PRINT '';
PRINT '--- ASSUMPTION: fiscal year = calendar year (1 Jan - 31 Dec). ---';
PRINT '--- If Yemeni practice differs, correct START_DATE/END_DATE and ---';
PRINT '--- regenerate the periods; nothing else depends on the dates.  ---';
PRINT '=============== end V040 verification =============';
GO
