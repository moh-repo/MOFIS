/*
    V039 — a currency master an accounting system can rely on, and real
           exchange rates.

    Why
    ---
    The ministry books in Yemeni Rial. Attaches disburse in local currency. A
    ledger therefore needs three things this database does not have: the
    functional currency, ISO codes to identify currencies unambiguously, and a
    rate for a given day.

    1. There is no Yemeni Rial. STUDY_CURRENCY_IN has 16 rows -- USD, GBP, EUR,
       SAR, JOD, OMR, MYR, INR, AED, SDG, AUD, CAD, EGP -- and no YER. The
       currency the ministry actually reports in is absent.

    2. STUDY_CURRENCY_IN_EXCHANGE is not an exchange rate. It holds:

           US Dollar 2,  British Pound 3,  Euro 2,  Saudi Riyal 0,
           Indian Rupee 0,  Jordanian Dinar 3,  Omani Rial 1, ...

       Whatever those are -- a display grouping, a sort order, an import
       artefact -- they are not rates, and no arithmetic on them produces one.
       It is left in place and untouched (something may read it), but nothing in
       the accounting module will ever use it.

    3. There is no date dimension. A single scalar per currency cannot express
       "the rate on the day the attache paid", which is the only rate a
       transaction may lawfully be translated at.

    4. Currencies are duplicated. "US Dollar" is both ID 1 and ID 1004; "British
       Pound Sterling" (2) and "British Pound" (1000) are the same currency. Two
       rows for one currency means two accounts for one thing.

    What this script does and does not decide
    -----------------------------------------
    It ADDS the missing facts: ISO code, minor units, functional flag, YER, and
    a dated rate table.

    It does NOT merge the duplicates. Re-pointing rows from 1004 to 1 touches
    eight referencing tables, and choosing the survivor is a decision about your
    data, not a mechanical fix. The verification block lists every duplicate ISO
    code with the row counts that reference each side, so the choice can be made
    on evidence. As it stands only the low IDs are in use --
    STUDENT_DATA.amount_currency is 1 (USD) for 940 of 941 students -- so the
    duplicates are currently dormant rather than actively splitting data.

    Minor units follow ISO 4217: 2 for most, 3 for JOD and OMR. YER is set to 2
    (its ISO 4217 minor unit) even though Yemeni practice is whole rials; it is
    a data value, and rounding to whole rials at presentation is safer than
    discovering the ledger cannot represent a fils.

    Rollback
    --------
        DROP TABLE FINCORE.FX_RATE;
        DROP SCHEMA FINCORE;                      -- only if nothing else added yet
        DELETE FROM MOHECORE.STUDY_CURRENCY_IN WHERE STUDY_CURRENCY_IN_ID = 1012;  -- YER
        ALTER TABLE MOHECORE.STUDY_CURRENCY_IN
            DROP COLUMN ISO_CODE, MINOR_UNITS, IS_FUNCTIONAL;
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ------------------------------------------------------------------ *
 * 1. The FINCORE schema. Everything the accounting module owns lives
 *    here, alongside MOHECORE / UMSCORE / STUDENTCORE / ALERTCORE.
 * ------------------------------------------------------------------ */
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'FINCORE')
BEGIN
    EXEC('CREATE SCHEMA FINCORE');
    PRINT 'V039: created schema FINCORE.';
END
ELSE
    PRINT 'V039: schema FINCORE already present.';
GO

/* ------------------------------------------------------------------ *
 * 2. Identify currencies properly.
 * ------------------------------------------------------------------ */
IF COL_LENGTH('MOHECORE.STUDY_CURRENCY_IN', 'ISO_CODE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDY_CURRENCY_IN ADD ISO_CODE char(3) NULL;
    PRINT 'V039: added STUDY_CURRENCY_IN.ISO_CODE.';
END
GO

IF COL_LENGTH('MOHECORE.STUDY_CURRENCY_IN', 'MINOR_UNITS') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDY_CURRENCY_IN ADD MINOR_UNITS tinyint NULL;
    PRINT 'V039: added STUDY_CURRENCY_IN.MINOR_UNITS.';
END
GO

IF COL_LENGTH('MOHECORE.STUDY_CURRENCY_IN', 'IS_FUNCTIONAL') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDY_CURRENCY_IN
        ADD IS_FUNCTIONAL bit NOT NULL CONSTRAINT DF_STUDY_CURRENCY_IN_IS_FUNCTIONAL DEFAULT (0);
    PRINT 'V039: added STUDY_CURRENCY_IN.IS_FUNCTIONAL.';
END
GO

/* ------------------------------------------------------------------ *
 * 3. Backfill ISO codes and minor units.
 *
 *    Matched on the English name rather than on ID, because IDs are
 *    deployment-specific while the names came from the same seed. Rows
 *    that do not match are reported at the end rather than guessed at.
 * ------------------------------------------------------------------ */
DECLARE @iso TABLE (name_en nvarchar(200) NOT NULL, iso char(3) NOT NULL, minor tinyint NOT NULL);

INSERT INTO @iso (name_en, iso, minor) VALUES
    (N'US Dollar',                'USD', 2),
    (N'British Pound Sterling',   'GBP', 2),
    (N'British Pound',            'GBP', 2),
    (N'Euro',                     'EUR', 2),
    (N'Egyptian Pound',           'EGP', 2),
    (N'Sudanese Pound (SDG)',     'SDG', 2),
    (N'UAE Dirham (AED)',         'AED', 2),
    (N'Australian Dollar (AUD)',  'AUD', 2),
    (N'Canadian Dollar',          'CAD', 2),
    (N'Jordanian Dinar (JOD)',    'JOD', 3),   -- ISO 4217: 3 minor units
    (N'Indian Rupee (INR)',       'INR', 2),
    (N'Saudi Riyal (SAR)',        'SAR', 2),
    (N'Omani Rial (OMR)',         'OMR', 3),   -- ISO 4217: 3 minor units
    (N'Malaysian Ringgit (MYR)',  'MYR', 2);

UPDATE c
SET c.ISO_CODE    = i.iso,
    c.MINOR_UNITS = i.minor
FROM MOHECORE.STUDY_CURRENCY_IN c
JOIN @iso i ON LTRIM(RTRIM(c.STUDY_CURRENCY_IN_NAME_EN)) = i.name_en
WHERE c.ISO_CODE IS NULL;

PRINT 'V039: backfilled ISO codes for ' + CAST(@@ROWCOUNT AS varchar(10)) + ' currencies.';
GO

/* ------------------------------------------------------------------ *
 * 4. The Yemeni Rial, and the fact that it is the functional currency.
 * ------------------------------------------------------------------ */
IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_CURRENCY_IN WHERE ISO_CODE = 'YER')
BEGIN
    INSERT INTO MOHECORE.STUDY_CURRENCY_IN
        (STUDY_CURRENCY_IN_ID, STUDY_CURRENCY_IN_NAME, STUDY_CURRENCY_IN_NAME_EN,
         STUDY_CURRENCY_IN_EXCHANGE, STUDY_CURRENCY_IN_MIDATE,
         IS_ACTIVE, ISO_CODE, MINOR_UNITS, IS_FUNCTIONAL)
    VALUES
        (1012, N'الريال اليمني', N'Yemeni Rial',
         NULL, SYSDATETIME(),
         1, 'YER', 2, 1);
    PRINT 'V039: inserted YER (id 1012) as the functional currency.';
END
ELSE
BEGIN
    UPDATE MOHECORE.STUDY_CURRENCY_IN SET IS_FUNCTIONAL = 1 WHERE ISO_CODE = 'YER';
    PRINT 'V039: YER already present; ensured IS_FUNCTIONAL = 1.';
END
GO

/* Exactly one functional currency. A ledger with two has no base to report in. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_STUDY_CURRENCY_IN_FUNCTIONAL')
BEGIN
    CREATE UNIQUE INDEX UX_STUDY_CURRENCY_IN_FUNCTIONAL
        ON MOHECORE.STUDY_CURRENCY_IN (IS_FUNCTIONAL)
        WHERE IS_FUNCTIONAL = 1;
    PRINT 'V039: only one currency may be functional (filtered unique index).';
END
GO

/* ------------------------------------------------------------------ *
 * 5. Dated exchange rates.
 *
 *    RATE is the number of TO_CURRENCY units per one FROM_CURRENCY unit,
 *    so USD -> YER on a given day is a single row. decimal(18,8) keeps
 *    eight decimals: a rate quoted to five and then inverted needs the
 *    headroom, and rounding the rate before applying it puts the error
 *    into every translated amount.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.FX_RATE', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.FX_RATE (
        FX_RATE_ID          int            IDENTITY(1,1) NOT NULL,
        FROM_CURRENCY_ID    int            NOT NULL,
        TO_CURRENCY_ID      int            NOT NULL,
        RATE_DATE           date           NOT NULL,
        RATE                decimal(18,8)  NOT NULL,
        RATE_SOURCE         nvarchar(50)   NULL,      -- e.g. N'CBY', N'attache advice'
        NOTES               nvarchar(500)  NULL,
        CREATED_BY          int            NULL,
        CREATED_AT          datetime2(3)   NOT NULL
                            CONSTRAINT DF_FX_RATE_CREATED_AT DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_FX_RATE PRIMARY KEY (FX_RATE_ID),
        CONSTRAINT UX_FX_RATE_PAIR_DATE UNIQUE (FROM_CURRENCY_ID, TO_CURRENCY_ID, RATE_DATE),
        CONSTRAINT CK_FX_RATE_POSITIVE  CHECK (RATE > 0),
        CONSTRAINT CK_FX_RATE_NOT_SELF  CHECK (FROM_CURRENCY_ID <> TO_CURRENCY_ID),
        CONSTRAINT FK_FX_RATE_FROM FOREIGN KEY (FROM_CURRENCY_ID)
            REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID),
        CONSTRAINT FK_FX_RATE_TO   FOREIGN KEY (TO_CURRENCY_ID)
            REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID)
    );
    CREATE INDEX IX_FX_RATE_LOOKUP ON FINCORE.FX_RATE (FROM_CURRENCY_ID, TO_CURRENCY_ID, RATE_DATE DESC);
    PRINT 'V039: created FINCORE.FX_RATE.';
END
ELSE
    PRINT 'V039: FINCORE.FX_RATE already present.';
GO

/* No rates are seeded. A made-up rate is worse than a missing one: a
   missing rate stops a translation, an invented one silently misstates it. */

/* ------------------------------------------------------------------ *
 * 6. Verification
 * ------------------------------------------------------------------ */
PRINT '';
PRINT '================ V039 verification ================';

PRINT '--- the functional currency (must be exactly one row, YER) ---';
SELECT STUDY_CURRENCY_IN_ID, ISO_CODE, STUDY_CURRENCY_IN_NAME_EN, MINOR_UNITS, IS_FUNCTIONAL
FROM MOHECORE.STUDY_CURRENCY_IN
WHERE IS_FUNCTIONAL = 1;

PRINT '';
PRINT '--- currencies still without an ISO code (999 "Not Specified" is expected) ---';
SELECT STUDY_CURRENCY_IN_ID, STUDY_CURRENCY_IN_NAME_EN
FROM MOHECORE.STUDY_CURRENCY_IN
WHERE ISO_CODE IS NULL;

PRINT '';
PRINT '--- DUPLICATE CURRENCIES: same ISO on more than one row. ---';
PRINT '--- Not merged by this script. usage_* shows what references each side. ---';
SELECT
    c.ISO_CODE,
    c.STUDY_CURRENCY_IN_ID,
    c.STUDY_CURRENCY_IN_NAME_EN,
    (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA    d WHERE d.amount_currency        = c.STUDY_CURRENCY_IN_ID) AS usage_student_data,
    (SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE f WHERE f.STUDY_CURRENCY_IN_ID   = c.STUDY_CURRENCY_IN_ID) AS usage_student_finance
FROM MOHECORE.STUDY_CURRENCY_IN c
WHERE c.ISO_CODE IN (
        SELECT ISO_CODE FROM MOHECORE.STUDY_CURRENCY_IN
        WHERE ISO_CODE IS NOT NULL GROUP BY ISO_CODE HAVING COUNT(*) > 1)
ORDER BY c.ISO_CODE, c.STUDY_CURRENCY_IN_ID;

PRINT '';
PRINT '--- FX_RATE is intentionally empty; rate_count must be 0 ---';
SELECT COUNT(*) AS rate_count FROM FINCORE.FX_RATE;

PRINT '';
PRINT '--- reminder: STUDY_CURRENCY_IN_EXCHANGE is NOT a rate. Values in use: ---';
SELECT STUDY_CURRENCY_IN_EXCHANGE AS not_a_rate, COUNT(*) AS currencies
FROM MOHECORE.STUDY_CURRENCY_IN
GROUP BY STUDY_CURRENCY_IN_EXCHANGE
ORDER BY not_a_rate;

PRINT '=============== end V039 verification =============';
GO
