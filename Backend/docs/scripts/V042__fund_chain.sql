/*
    V042 — the fund chain: appropriation -> allocation -> batch -> advance,
           and Wafar as an earmark rather than income.

    Why
    ---
    The ministry receives an annual budget, sends it to cultural attaches
    quarterly, and the attaches pay students in batches. What is not paid becomes
    Wafar and is spent later, with permission, on someone else.

    None of that chain exists in the database. There is no appropriation table,
    no allocation table, no transfer table and no advance table -- I searched for
    every one of them. The consequences are visible in the data:

      * MOHE_QUARTER, which is where quarterly budget figures are supposed to
        accumulate, has QUARTER_AMOUNT = 0 and BUDGET_AMOUNT = NULL on all 12
        rows;
      * WafarBalanceService computes one global figure,
        sum(deposits) - sum(approved spending), with no country and no currency,
        so it implies Cairo's savings can pay Riyadh's students.

    The accounting point that decides this schema
    ---------------------------------------------
    WAFAR IS NOT NEW MONEY. It is budget already transferred to an attache that
    was not disbursed. Booking it as income double-counts the budget; booking a
    Wafar deposit as an expense double-counts the spend. It is a re-earmarking of
    cash the ministry already holds at that attache.

    The schema encodes that: WAFAR_EARMARK moves nothing. It records that part of
    an advance is now spoken for, and ATTACHE_ADVANCE_BALANCE keeps the arithmetic
    where it can be checked. STUDENT_FINANCE already agrees -- its NET_AMOUNT is a
    persisted computed column, (received - wafar), so the existing schema has
    always treated Wafar as the undisbursed part of an entitlement.

    The balance identity
    --------------------
    Per attache, per fiscal year, per currency:

        AVAILABLE = OPENING + TRANSFERRED - DISBURSED - RETURNED
                    - (WAFAR_EARMARKED - WAFAR_SPENT)

    Spending Wafar moves two counters at once: WAFAR_SPENT rises (the earmark is
    released) and DISBURSED rises (the cash actually leaves). AVAILABLE is
    therefore unchanged by a Wafar payment, which is the correct answer -- paying
    a student out of savings does not make the attache any freer than it was. Get
    only one of the two counters right and the balance drifts by the value of
    every Wafar payment ever made.

    AVAILABLE is the attache's un-earmarked cash, and it may never go negative:
    that would mean paying out money that was not there. At quarter close every
    unpaid entitlement is earmarked to Wafar, so AVAILABLE reaches zero and the
    identity in the plan -- transferred - disbursed - wafar - returned = 0 --
    holds exactly. Mid-quarter it is positive, which is why AVAILABLE is modelled
    rather than assumed to be nil.

    Who maintains the balance
    -------------------------
    The service layer does the arithmetic; the database refuses to hold a state
    that cannot be true (AVAILABLE >= 0, WAFAR_SPENT <= WAFAR_EARMARKED). Trigger-
    maintained running totals across five source tables would be the alternative,
    and would put the hardest arithmetic in the least testable place.

    Drift between the running totals and the rows that produced them is therefore
    possible, so V_ADVANCE_RECONCILIATION recomputes the balance from source and
    reports the difference. A stored total that disagrees with its own detail is
    the failure this design has to be able to detect, and it can.

    Wafar stays where it is
    -----------------------
    A Wafar use must draw on the attache that holds it: Cairo's savings pay only
    Cairo. TR_WAFAR_USE_SAME_ATTACHE enforces it, because this is the rule most
    likely to be broken by a well-meaning screen that lets an officer pick any
    student.

    Rollback
    --------
        DROP VIEW    FINCORE.V_ADVANCE_RECONCILIATION;
        DROP TRIGGER FINCORE.TR_WAFAR_USE_SAME_ATTACHE;
        DROP TABLE   FINCORE.WAFAR_USE, FINCORE.WAFAR_EARMARK,
                     FINCORE.PAYMENT_BATCH_LINE, FINCORE.PAYMENT_BATCH,
                     FINCORE.ATTACHE_ADVANCE_BALANCE, FINCORE.ATTACHE_ALLOCATION,
                     FINCORE.BUDGET_APPROPRIATION, FINCORE.FEE_TYPE;
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ------------------------------------------------------------------ *
 * 1. Fee types — the streams the budget is broken into.
 *
 *    SOURCE_TABLE is factual: it names the table that already records
 *    that stream, so the ledger round knows where to read from.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.FEE_TYPE', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.FEE_TYPE (
        FEE_TYPE_ID   int           IDENTITY(1,1) NOT NULL,
        FEE_TYPE_CODE varchar(30)   NOT NULL,
        NAME_AR       nvarchar(150) NOT NULL,
        NAME_EN       nvarchar(150) NOT NULL,
        SOURCE_TABLE  nvarchar(128) NULL,
        IS_ACTIVE     bit           NOT NULL CONSTRAINT DF_FEE_TYPE_ACTIVE DEFAULT (1),
        CONSTRAINT PK_FEE_TYPE      PRIMARY KEY (FEE_TYPE_ID),
        CONSTRAINT UX_FEE_TYPE_CODE UNIQUE (FEE_TYPE_CODE)
    );

    INSERT INTO FINCORE.FEE_TYPE (FEE_TYPE_CODE, NAME_AR, NAME_EN, SOURCE_TABLE) VALUES
        ('STIPEND',        N'المساعدة المالية',  N'Financial assistance', N'MOHECORE.STUDENT_FINANCE'),
        ('BOOKS',          N'مخصص الكتب',        N'Books allowance',      N'MOHECORE.STUDENT_BOOKS_ALLOWANCE'),
        ('TRAVEL',         N'تذاكر السفر',       N'Travel tickets',       N'MOHECORE.STUDENT_TRAVEL_TICKET'),
        ('UNIVERSITY_FEE', N'الرسوم الدراسية',   N'University fees',      N'MOHECORE.STUDY_UNIVERSITY_FEE'),
        ('OTHER',          N'أخرى',              N'Other',                NULL);

    PRINT 'V042: created FINCORE.FEE_TYPE with 5 streams.';
END
GO

/* ------------------------------------------------------------------ *
 * 2. The annual appropriation from MOF to MOHE.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.BUDGET_APPROPRIATION', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.BUDGET_APPROPRIATION (
        APPROPRIATION_ID    int           IDENTITY(1,1) NOT NULL,
        FISCAL_YEAR_ID      int           NOT NULL,
        FEE_TYPE_ID         int           NOT NULL,
        CURRENCY_ID         int           NOT NULL,
        AMOUNT              decimal(18,2) NOT NULL,
        -- NULL = the whole ministry, not broken down by attache.
        STUDY_COUNTRY_IN_ID int           NULL,
        STATUS              varchar(15)   NOT NULL CONSTRAINT DF_APPROP_STATUS DEFAULT ('DRAFT'),
        REFERENCE_NUMBER    nvarchar(100) NULL,   -- the MOF decision this came from
        NOTES               nvarchar(500) NULL,
        CREATED_BY          int           NULL,
        CREATED_AT          datetime2(3)  NOT NULL CONSTRAINT DF_APPROP_CREATED DEFAULT (SYSUTCDATETIME()),
        UPDATED_BY          int           NULL,
        UPDATED_AT          datetime2(3)  NULL,
        CONSTRAINT PK_BUDGET_APPROPRIATION PRIMARY KEY (APPROPRIATION_ID),
        CONSTRAINT FK_APPROP_YEAR FOREIGN KEY (FISCAL_YEAR_ID) REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT FK_APPROP_FEE  FOREIGN KEY (FEE_TYPE_ID)    REFERENCES FINCORE.FEE_TYPE    (FEE_TYPE_ID),
        CONSTRAINT FK_APPROP_CUR  FOREIGN KEY (CURRENCY_ID)    REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID),
        CONSTRAINT CK_APPROP_AMOUNT CHECK (AMOUNT > 0),
        CONSTRAINT CK_APPROP_STATUS CHECK (STATUS IN ('DRAFT','APPROVED','REVISED'))
    );
    CREATE UNIQUE INDEX UX_APPROP_YEAR_FEE_CUR
        ON FINCORE.BUDGET_APPROPRIATION (FISCAL_YEAR_ID, FEE_TYPE_ID, CURRENCY_ID, STUDY_COUNTRY_IN_ID)
        WHERE STATUS <> 'REVISED';
    PRINT 'V042: created FINCORE.BUDGET_APPROPRIATION.';
END
GO

/* ------------------------------------------------------------------ *
 * 3. The quarterly transfer to one attache.
 *
 *    ALLOCATION_TYPE distinguishes the regular quarterly transfer from
 *    an Additional Budget Allocation. Additional allocations are applied
 *    quarterly and retroactively -- they cover students added, edited or
 *    corrected after the regular transfer went out -- so they are a first
 *    class row, not an adjustment to the original.
 *
 *    Amounts are always positive. A downward correction does not become a
 *    negative allocation: the cash is already at the attache, so it
 *    becomes a Wafar earmark instead (see WAFAR_EARMARK).
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.ATTACHE_ALLOCATION', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.ATTACHE_ALLOCATION (
        ALLOCATION_ID       int           IDENTITY(1,1) NOT NULL,
        FISCAL_YEAR_ID      int           NOT NULL,
        -- MOHE_QUARTER's key is the YYYYQ form (e.g. 20242), the same value
        -- STUDENT_FINANCE carries in STUDENT_FIN_RECIEVED_YEAR_MONTH.
        MOHE_QUARTER_KEY    int           NOT NULL,
        STUDY_COUNTRY_IN_ID int           NOT NULL,   -- the attache
        ALLOCATION_TYPE     varchar(12)   NOT NULL,
        FEE_TYPE_ID         int           NULL,       -- NULL = covers all streams
        CURRENCY_ID         int           NOT NULL,
        AMOUNT              decimal(18,2) NOT NULL,
        STATUS              varchar(15)   NOT NULL CONSTRAINT DF_ALLOC_STATUS DEFAULT ('DRAFT'),
        TRANSFER_DATE       date          NULL,
        TRANSFER_REFERENCE  nvarchar(100) NULL,
        APPROVAL_REQUEST_ID int           NULL,       -- ALERTCORE.ApprovalRequests.Id
        NOTES               nvarchar(500) NULL,
        CREATED_BY          int           NULL,
        CREATED_AT          datetime2(3)  NOT NULL CONSTRAINT DF_ALLOC_CREATED DEFAULT (SYSUTCDATETIME()),
        UPDATED_BY          int           NULL,
        UPDATED_AT          datetime2(3)  NULL,
        CONSTRAINT PK_ATTACHE_ALLOCATION PRIMARY KEY (ALLOCATION_ID),
        CONSTRAINT FK_ALLOC_YEAR    FOREIGN KEY (FISCAL_YEAR_ID)      REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT FK_ALLOC_QUARTER FOREIGN KEY (MOHE_QUARTER_KEY)    REFERENCES MOHECORE.MOHE_QUARTER (QUARTER),
        CONSTRAINT FK_ALLOC_COUNTRY FOREIGN KEY (STUDY_COUNTRY_IN_ID) REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID),
        CONSTRAINT FK_ALLOC_FEE     FOREIGN KEY (FEE_TYPE_ID)         REFERENCES FINCORE.FEE_TYPE (FEE_TYPE_ID),
        CONSTRAINT FK_ALLOC_CUR     FOREIGN KEY (CURRENCY_ID)         REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID),
        CONSTRAINT CK_ALLOC_TYPE    CHECK (ALLOCATION_TYPE IN ('REGULAR','ADDITIONAL')),
        CONSTRAINT CK_ALLOC_AMOUNT  CHECK (AMOUNT > 0),
        CONSTRAINT CK_ALLOC_STATUS  CHECK (STATUS IN ('DRAFT','APPROVED','TRANSFERRED','CANCELLED')),
        -- Money cannot have been transferred without a date.
        CONSTRAINT CK_ALLOC_TRANSFER_DATED
            CHECK (STATUS <> 'TRANSFERRED' OR TRANSFER_DATE IS NOT NULL)
    );
    CREATE INDEX IX_ALLOC_QUARTER_COUNTRY
        ON FINCORE.ATTACHE_ALLOCATION (MOHE_QUARTER_KEY, STUDY_COUNTRY_IN_ID, STATUS);
    PRINT 'V042: created FINCORE.ATTACHE_ALLOCATION.';
END
GO

/* ------------------------------------------------------------------ *
 * 4. The payment batch — the دفعة, modelled properly.
 *
 *    STUDY_FINANCE_DOF holds the batch numbers (First..Fourth) but also,
 *    at ids 100-107, the cultural attache offices: two dimensions in one
 *    lookup. And STUDENT_FINANCE.STUDY_FINANCE_DOF_ID is 0 on all 941
 *    rows, so the batch is not actually recorded anywhere today.
 *
 *    A batch is the unit the cycle hangs on: batch 1 pays most students,
 *    those missing documents or attache requirements are paid in batch 2,
 *    and so on within the quarter. The old column is left untouched.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.PAYMENT_BATCH', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.PAYMENT_BATCH (
        BATCH_ID            int           IDENTITY(1,1) NOT NULL,
        FISCAL_YEAR_ID      int           NOT NULL,
        MOHE_QUARTER_KEY    int           NOT NULL,
        STUDY_COUNTRY_IN_ID int           NOT NULL,   -- the attache paying
        BATCH_NO            tinyint       NOT NULL,   -- the DOF sequence: 1, 2, 3...
        FEE_TYPE_ID         int           NULL,
        CURRENCY_ID         int           NOT NULL,
        STATUS              varchar(15)   NOT NULL CONSTRAINT DF_BATCH_STATUS DEFAULT ('OPEN'),
        PREPARED_BY         int           NULL,
        PREPARED_AT         datetime2(3)  NULL,
        APPROVED_BY         int           NULL,
        APPROVED_AT         datetime2(3)  NULL,
        DISBURSED_AT        datetime2(3)  NULL,
        APPROVAL_REQUEST_ID int           NULL,
        NOTES               nvarchar(500) NULL,
        CREATED_AT          datetime2(3)  NOT NULL CONSTRAINT DF_BATCH_CREATED DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_PAYMENT_BATCH PRIMARY KEY (BATCH_ID),
        CONSTRAINT UX_PAYMENT_BATCH UNIQUE (MOHE_QUARTER_KEY, STUDY_COUNTRY_IN_ID, BATCH_NO, FEE_TYPE_ID),
        CONSTRAINT FK_BATCH_YEAR    FOREIGN KEY (FISCAL_YEAR_ID)      REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT FK_BATCH_QUARTER FOREIGN KEY (MOHE_QUARTER_KEY)    REFERENCES MOHECORE.MOHE_QUARTER (QUARTER),
        CONSTRAINT FK_BATCH_COUNTRY FOREIGN KEY (STUDY_COUNTRY_IN_ID) REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID),
        CONSTRAINT FK_BATCH_FEE     FOREIGN KEY (FEE_TYPE_ID)         REFERENCES FINCORE.FEE_TYPE (FEE_TYPE_ID),
        CONSTRAINT FK_BATCH_CUR     FOREIGN KEY (CURRENCY_ID)         REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID),
        CONSTRAINT CK_BATCH_NO      CHECK (BATCH_NO BETWEEN 1 AND 20),
        CONSTRAINT CK_BATCH_STATUS  CHECK (STATUS IN ('OPEN','APPROVED','DISBURSED','CLOSED','CANCELLED')),
        -- A disbursed batch must say when, and must have been approved first.
        CONSTRAINT CK_BATCH_DISBURSE_ORDER
            CHECK (STATUS NOT IN ('DISBURSED','CLOSED')
                OR (DISBURSED_AT IS NOT NULL AND APPROVED_AT IS NOT NULL))
    );
    PRINT 'V042: created FINCORE.PAYMENT_BATCH.';
END
GO

/* One line per student in a batch: what they were due and what they got. */
IF OBJECT_ID('FINCORE.PAYMENT_BATCH_LINE', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.PAYMENT_BATCH_LINE (
        BATCH_LINE_ID       int           IDENTITY(1,1) NOT NULL,
        BATCH_ID            int           NOT NULL,
        STUDENT_ID          int           NOT NULL,
        ENTITLED_AMOUNT     decimal(18,2) NOT NULL,
        DISBURSED_AMOUNT    decimal(18,2) NOT NULL CONSTRAINT DF_BATCH_LINE_DISB DEFAULT (0),
        -- Why the student was not paid in full, when that is the case.
        HELD_REASON         nvarchar(300) NULL,
        STUDY_COUNTRY_IN_ID int           NULL,
        CREATED_AT          datetime2(3)  NOT NULL CONSTRAINT DF_BATCH_LINE_CREATED DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_PAYMENT_BATCH_LINE PRIMARY KEY (BATCH_LINE_ID),
        CONSTRAINT UX_PAYMENT_BATCH_LINE UNIQUE (BATCH_ID, STUDENT_ID),
        CONSTRAINT FK_BATCH_LINE_BATCH   FOREIGN KEY (BATCH_ID)   REFERENCES FINCORE.PAYMENT_BATCH (BATCH_ID),
        CONSTRAINT FK_BATCH_LINE_STUDENT FOREIGN KEY (STUDENT_ID) REFERENCES MOHECORE.STUDENT_DATA (STUDENT_ID),
        CONSTRAINT CK_BATCH_LINE_AMOUNTS
            CHECK (ENTITLED_AMOUNT >= 0 AND DISBURSED_AMOUNT >= 0
                   AND DISBURSED_AMOUNT <= ENTITLED_AMOUNT)
    );
    PRINT 'V042: created FINCORE.PAYMENT_BATCH_LINE.';
END
GO

/* ------------------------------------------------------------------ *
 * 5. The advance (العهدة) held by each attache.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.ATTACHE_ADVANCE_BALANCE', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.ATTACHE_ADVANCE_BALANCE (
        BALANCE_ID          int           IDENTITY(1,1) NOT NULL,
        FISCAL_YEAR_ID      int           NOT NULL,
        STUDY_COUNTRY_IN_ID int           NOT NULL,
        CURRENCY_ID         int           NOT NULL,
        -- Entered once, as a verified figure. Not derived from the 2024
        -- archive: that data has no attache attribution
        -- (STUDY_FINANCE_DOF_ID is 0 on all 941 rows).
        OPENING_BALANCE     decimal(18,2) NOT NULL CONSTRAINT DF_ADV_OPENING   DEFAULT (0),
        TRANSFERRED         decimal(18,2) NOT NULL CONSTRAINT DF_ADV_TRANSFER  DEFAULT (0),
        DISBURSED           decimal(18,2) NOT NULL CONSTRAINT DF_ADV_DISBURSED DEFAULT (0),
        RETURNED            decimal(18,2) NOT NULL CONSTRAINT DF_ADV_RETURNED  DEFAULT (0),
        WAFAR_EARMARKED     decimal(18,2) NOT NULL CONSTRAINT DF_ADV_WAFAR_E   DEFAULT (0),
        WAFAR_SPENT         decimal(18,2) NOT NULL CONSTRAINT DF_ADV_WAFAR_S   DEFAULT (0),

        /* Savings still held. Cannot be negative: spending more Wafar than was
           ever earmarked would mean spending money that was never set aside. */
        WAFAR_BALANCE AS (WAFAR_EARMARKED - WAFAR_SPENT) PERSISTED,

        /* Un-earmarked cash. At quarter close this reaches zero, because every
           unpaid entitlement has been earmarked to Wafar by then. */
        AVAILABLE AS (OPENING_BALANCE + TRANSFERRED - DISBURSED - RETURNED
                      - (WAFAR_EARMARKED - WAFAR_SPENT)) PERSISTED,

        UPDATED_AT          datetime2(3)  NULL,
        CONSTRAINT PK_ATTACHE_ADVANCE_BALANCE PRIMARY KEY (BALANCE_ID),
        CONSTRAINT UX_ADVANCE_BALANCE UNIQUE (FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID, CURRENCY_ID),
        CONSTRAINT FK_ADV_YEAR    FOREIGN KEY (FISCAL_YEAR_ID)      REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT FK_ADV_COUNTRY FOREIGN KEY (STUDY_COUNTRY_IN_ID) REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID),
        CONSTRAINT FK_ADV_CUR     FOREIGN KEY (CURRENCY_ID)         REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID),
        CONSTRAINT CK_ADV_NON_NEGATIVE
            CHECK (OPENING_BALANCE >= 0 AND TRANSFERRED >= 0 AND DISBURSED >= 0
                   AND RETURNED >= 0 AND WAFAR_EARMARKED >= 0 AND WAFAR_SPENT >= 0),
        /* The two invariants the whole design rests on. */
        CONSTRAINT CK_ADV_WAFAR_NOT_OVERSPENT CHECK (WAFAR_SPENT <= WAFAR_EARMARKED),
        CONSTRAINT CK_ADV_NOT_OVERDRAWN
            CHECK (OPENING_BALANCE + TRANSFERRED - DISBURSED - RETURNED
                   - (WAFAR_EARMARKED - WAFAR_SPENT) >= 0)
    );
    PRINT 'V042: created FINCORE.ATTACHE_ADVANCE_BALANCE.';
END
GO

/* ------------------------------------------------------------------ *
 * 6. Wafar: why money became savings, and what it was later used for.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.WAFAR_EARMARK', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.WAFAR_EARMARK (
        EARMARK_ID          int           IDENTITY(1,1) NOT NULL,
        FISCAL_YEAR_ID      int           NOT NULL,
        MOHE_QUARTER_KEY    int           NOT NULL,
        STUDY_COUNTRY_IN_ID int           NOT NULL,   -- the attache holding it
        STUDENT_ID          int           NULL,       -- whose entitlement it was
        FEE_TYPE_ID         int           NOT NULL,
        -- The reason, from the taxonomy that already exists: Savings, Increase,
        -- Return of Overpaid Amounts, Housing Allowance Adjustment, Grant Amount
        -- Adjustment, Travel Ticket Deduction, Tuition Fee Deduction, Other.
        WAFAR_TYPE_ID       int           NULL,
        CURRENCY_ID         int           NOT NULL,
        AMOUNT              decimal(18,2) NOT NULL,
        IS_PARTIAL          bit           NOT NULL CONSTRAINT DF_EARMARK_PARTIAL DEFAULT (0),
        SOURCE_ALLOCATION_ID int          NULL,
        BATCH_ID            int           NULL,
        NOTES               nvarchar(500) NULL,
        CREATED_BY          int           NULL,
        CREATED_AT          datetime2(3)  NOT NULL CONSTRAINT DF_EARMARK_CREATED DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_WAFAR_EARMARK PRIMARY KEY (EARMARK_ID),
        CONSTRAINT FK_EARMARK_YEAR    FOREIGN KEY (FISCAL_YEAR_ID)       REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT FK_EARMARK_QUARTER FOREIGN KEY (MOHE_QUARTER_KEY)     REFERENCES MOHECORE.MOHE_QUARTER (QUARTER),
        CONSTRAINT FK_EARMARK_COUNTRY FOREIGN KEY (STUDY_COUNTRY_IN_ID)  REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID),
        CONSTRAINT FK_EARMARK_STUDENT FOREIGN KEY (STUDENT_ID)           REFERENCES MOHECORE.STUDENT_DATA (STUDENT_ID),
        CONSTRAINT FK_EARMARK_FEE     FOREIGN KEY (FEE_TYPE_ID)          REFERENCES FINCORE.FEE_TYPE (FEE_TYPE_ID),
        CONSTRAINT FK_EARMARK_WTYPE   FOREIGN KEY (WAFAR_TYPE_ID)        REFERENCES MOHECORE.STUDY_WAFAR_TYPE (WAFAR_TYPE_ID),
        CONSTRAINT FK_EARMARK_CUR     FOREIGN KEY (CURRENCY_ID)          REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID),
        CONSTRAINT FK_EARMARK_ALLOC   FOREIGN KEY (SOURCE_ALLOCATION_ID) REFERENCES FINCORE.ATTACHE_ALLOCATION (ALLOCATION_ID),
        CONSTRAINT FK_EARMARK_BATCH   FOREIGN KEY (BATCH_ID)             REFERENCES FINCORE.PAYMENT_BATCH (BATCH_ID),
        CONSTRAINT CK_EARMARK_AMOUNT  CHECK (AMOUNT > 0)
    );
    CREATE INDEX IX_EARMARK_POOL ON FINCORE.WAFAR_EARMARK (FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID, CURRENCY_ID);
    PRINT 'V042: created FINCORE.WAFAR_EARMARK.';
END
GO

IF OBJECT_ID('FINCORE.WAFAR_USE', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.WAFAR_USE (
        USE_ID                 int           IDENTITY(1,1) NOT NULL,
        FISCAL_YEAR_ID         int           NOT NULL,
        MOHE_QUARTER_KEY       int           NULL,
        STUDY_COUNTRY_IN_ID    int           NOT NULL,   -- the attache whose pool is drawn on
        CURRENCY_ID            int           NOT NULL,
        AMOUNT                 decimal(18,2) NOT NULL,
        -- Who or what it was spent on. Both may be null for a general expense.
        BENEFICIARY_STUDENT_ID int           NULL,
        CATEGORY_ID            int           NULL,       -- WAFAR_SPENDING_CATEGORY
        -- The existing spending row this corresponds to, where there is one.
        SPENDING_ID            int           NULL,
        APPROVAL_REQUEST_ID    int           NULL,       -- Wafar reuse needs permission
        NOTES                  nvarchar(500) NULL,
        CREATED_BY             int           NULL,
        CREATED_AT             datetime2(3)  NOT NULL CONSTRAINT DF_WUSE_CREATED DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_WAFAR_USE PRIMARY KEY (USE_ID),
        CONSTRAINT FK_WUSE_YEAR     FOREIGN KEY (FISCAL_YEAR_ID)         REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT FK_WUSE_QUARTER  FOREIGN KEY (MOHE_QUARTER_KEY)       REFERENCES MOHECORE.MOHE_QUARTER (QUARTER),
        CONSTRAINT FK_WUSE_COUNTRY  FOREIGN KEY (STUDY_COUNTRY_IN_ID)    REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID),
        CONSTRAINT FK_WUSE_STUDENT  FOREIGN KEY (BENEFICIARY_STUDENT_ID) REFERENCES MOHECORE.STUDENT_DATA (STUDENT_ID),
        CONSTRAINT FK_WUSE_CATEGORY FOREIGN KEY (CATEGORY_ID)            REFERENCES MOHECORE.WAFAR_SPENDING_CATEGORY (CATEGORY_ID),
        CONSTRAINT FK_WUSE_SPENDING FOREIGN KEY (SPENDING_ID)            REFERENCES MOHECORE.WAFAR_SPENDING (SPENDING_ID),
        CONSTRAINT FK_WUSE_CUR      FOREIGN KEY (CURRENCY_ID)            REFERENCES MOHECORE.STUDY_CURRENCY_IN (STUDY_CURRENCY_IN_ID),
        CONSTRAINT CK_WUSE_AMOUNT   CHECK (AMOUNT > 0)
    );
    CREATE INDEX IX_WUSE_POOL ON FINCORE.WAFAR_USE (FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID, CURRENCY_ID);
    PRINT 'V042: created FINCORE.WAFAR_USE.';
END
GO

/* ------------------------------------------------------------------ *
 * 7. Wafar stays at its own attache.
 *
 *    Cairo's savings pay Cairo's students. The rule is easy to break from
 *    a screen that lets an officer pick any student, so the database
 *    refuses rather than trusting the screen.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.TR_WAFAR_USE_SAME_ATTACHE', 'TR') IS NOT NULL
    DROP TRIGGER FINCORE.TR_WAFAR_USE_SAME_ATTACHE;
GO

CREATE TRIGGER FINCORE.TR_WAFAR_USE_SAME_ATTACHE
ON FINCORE.WAFAR_USE
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    /* The beneficiary must study in the country whose Wafar is being spent. */
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = i.BENEFICIARY_STUDENT_ID
        WHERE sd.STUDY_COUNTRY_IN IS NOT NULL
          AND sd.STUDY_COUNTRY_IN <> i.STUDY_COUNTRY_IN_ID
    )
        THROW 50060, 'Wafar may only be spent at the attache that holds it: the beneficiary studies in a different country.', 1;

    /* There must be a pool, and it must hold enough. */
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN FINCORE.ATTACHE_ADVANCE_BALANCE b
               ON b.FISCAL_YEAR_ID      = i.FISCAL_YEAR_ID
              AND b.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN_ID
              AND b.CURRENCY_ID         = i.CURRENCY_ID
        WHERE b.BALANCE_ID IS NULL
    )
        THROW 50061, 'No Wafar pool exists for that attache, fiscal year and currency.', 1;

    IF EXISTS (
        SELECT i.FISCAL_YEAR_ID, i.STUDY_COUNTRY_IN_ID, i.CURRENCY_ID
        FROM inserted i
        JOIN FINCORE.ATTACHE_ADVANCE_BALANCE b
               ON b.FISCAL_YEAR_ID      = i.FISCAL_YEAR_ID
              AND b.STUDY_COUNTRY_IN_ID = i.STUDY_COUNTRY_IN_ID
              AND b.CURRENCY_ID         = i.CURRENCY_ID
        GROUP BY i.FISCAL_YEAR_ID, i.STUDY_COUNTRY_IN_ID, i.CURRENCY_ID, b.WAFAR_BALANCE
        HAVING SUM(i.AMOUNT) > b.WAFAR_BALANCE
    )
        THROW 50062, 'Wafar use exceeds the savings held at that attache.', 1;
END
GO
PRINT 'V042: created TR_WAFAR_USE_SAME_ATTACHE.';
GO

/* ------------------------------------------------------------------ *
 * 8. Reconciliation: does the stored balance match its own detail?
 *
 *    The service layer maintains the running totals, so they can drift
 *    from the rows that produced them. This view recomputes from source
 *    and shows the difference. Every *_DELTA column must be zero.
 * ------------------------------------------------------------------ */
CREATE OR ALTER VIEW FINCORE.V_ADVANCE_RECONCILIATION
AS
SELECT
    b.BALANCE_ID,
    b.FISCAL_YEAR_ID,
    b.STUDY_COUNTRY_IN_ID,
    b.CURRENCY_ID,
    b.OPENING_BALANCE,
    b.TRANSFERRED,
    src.TRANSFERRED_FROM_ALLOCATIONS,
    b.TRANSFERRED - src.TRANSFERRED_FROM_ALLOCATIONS AS TRANSFERRED_DELTA,
    b.DISBURSED,
    src.DISBURSED_FROM_SOURCES,
    b.DISBURSED - src.DISBURSED_FROM_SOURCES         AS DISBURSED_DELTA,
    b.WAFAR_EARMARKED,
    src.EARMARKED_FROM_ROWS,
    b.WAFAR_EARMARKED - src.EARMARKED_FROM_ROWS      AS EARMARKED_DELTA,
    b.WAFAR_SPENT,
    src.SPENT_FROM_ROWS,
    b.WAFAR_SPENT - src.SPENT_FROM_ROWS              AS SPENT_DELTA,
    b.WAFAR_BALANCE,
    b.AVAILABLE
FROM FINCORE.ATTACHE_ADVANCE_BALANCE b
CROSS APPLY (
    SELECT
        TRANSFERRED_FROM_ALLOCATIONS = ISNULL((
            SELECT SUM(a.AMOUNT) FROM FINCORE.ATTACHE_ALLOCATION a
            WHERE a.FISCAL_YEAR_ID = b.FISCAL_YEAR_ID
              AND a.STUDY_COUNTRY_IN_ID = b.STUDY_COUNTRY_IN_ID
              AND a.CURRENCY_ID = b.CURRENCY_ID
              AND a.STATUS = 'TRANSFERRED'), 0),
        /* Cash out to beneficiaries from BOTH sources. A Wafar use is cash
           leaving the attache exactly as a batch payment is, so DISBURSED has
           to count it -- otherwise the stored total and its own detail differ
           by the value of every Wafar payment ever made. The two are disjoint
           by construction: a Wafar use is never also a batch line. */
        DISBURSED_FROM_SOURCES = ISNULL((
            SELECT SUM(l.DISBURSED_AMOUNT)
            FROM FINCORE.PAYMENT_BATCH_LINE l
            JOIN FINCORE.PAYMENT_BATCH pb ON pb.BATCH_ID = l.BATCH_ID
            WHERE pb.FISCAL_YEAR_ID = b.FISCAL_YEAR_ID
              AND pb.STUDY_COUNTRY_IN_ID = b.STUDY_COUNTRY_IN_ID
              AND pb.CURRENCY_ID = b.CURRENCY_ID
              AND pb.STATUS IN ('DISBURSED','CLOSED')), 0)
          + ISNULL((
            SELECT SUM(u.AMOUNT) FROM FINCORE.WAFAR_USE u
            WHERE u.FISCAL_YEAR_ID = b.FISCAL_YEAR_ID
              AND u.STUDY_COUNTRY_IN_ID = b.STUDY_COUNTRY_IN_ID
              AND u.CURRENCY_ID = b.CURRENCY_ID), 0),
        EARMARKED_FROM_ROWS = ISNULL((
            SELECT SUM(e.AMOUNT) FROM FINCORE.WAFAR_EARMARK e
            WHERE e.FISCAL_YEAR_ID = b.FISCAL_YEAR_ID
              AND e.STUDY_COUNTRY_IN_ID = b.STUDY_COUNTRY_IN_ID
              AND e.CURRENCY_ID = b.CURRENCY_ID), 0),
        SPENT_FROM_ROWS = ISNULL((
            SELECT SUM(u.AMOUNT) FROM FINCORE.WAFAR_USE u
            WHERE u.FISCAL_YEAR_ID = b.FISCAL_YEAR_ID
              AND u.STUDY_COUNTRY_IN_ID = b.STUDY_COUNTRY_IN_ID
              AND u.CURRENCY_ID = b.CURRENCY_ID), 0)
) src;
GO
PRINT 'V042: created FINCORE.V_ADVANCE_RECONCILIATION.';
GO

/* ------------------------------------------------------------------ *
 * 9. Put the new tables under the tenant policy (ALTER, not rebuild).
 * ------------------------------------------------------------------ */
IF EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy')
BEGIN
    DECLARE @sql nvarchar(max) = N'';

    SELECT @sql = @sql + N'
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON FINCORE.' + QUOTENAME(t.name) + N',
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON FINCORE.' + QUOTENAME(t.name) + N' AFTER INSERT,'
    FROM sys.tables t
    WHERE SCHEMA_NAME(t.schema_id) = 'FINCORE'
      AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'STUDY_COUNTRY_IN_ID')
      AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp
                      JOIN sys.security_policies p ON p.object_id = sp.object_id
                      WHERE p.name = 'tenant_country_policy' AND sp.target_object_id = t.object_id);

    IF LEN(@sql) > 0
    BEGIN
        SET @sql = N'ALTER SECURITY POLICY MOHECORE.tenant_country_policy' + LEFT(@sql, LEN(@sql) - 1) + N';';
        EXEC sp_executesql @sql;
        PRINT 'V042: fund-chain tables added to tenant_country_policy.';
    END
    ELSE
        PRINT 'V042: fund-chain tables already under tenant_country_policy.';
END
GO

/* ------------------------------------------------------------------ *
 * 10. Verification
 * ------------------------------------------------------------------ */
PRINT '';
PRINT '================ V042 verification ================';

PRINT '--- fee types (the streams the budget divides into) ---';
SELECT FEE_TYPE_CODE, NAME_EN, SOURCE_TABLE FROM FINCORE.FEE_TYPE ORDER BY FEE_TYPE_ID;

PRINT '';
PRINT '--- all fund-chain tables must start empty ---';
SELECT 'BUDGET_APPROPRIATION' AS [table], COUNT(*) AS rows_ FROM FINCORE.BUDGET_APPROPRIATION
UNION ALL SELECT 'ATTACHE_ALLOCATION',      COUNT(*) FROM FINCORE.ATTACHE_ALLOCATION
UNION ALL SELECT 'PAYMENT_BATCH',           COUNT(*) FROM FINCORE.PAYMENT_BATCH
UNION ALL SELECT 'PAYMENT_BATCH_LINE',      COUNT(*) FROM FINCORE.PAYMENT_BATCH_LINE
UNION ALL SELECT 'ATTACHE_ADVANCE_BALANCE', COUNT(*) FROM FINCORE.ATTACHE_ADVANCE_BALANCE
UNION ALL SELECT 'WAFAR_EARMARK',           COUNT(*) FROM FINCORE.WAFAR_EARMARK
UNION ALL SELECT 'WAFAR_USE',               COUNT(*) FROM FINCORE.WAFAR_USE;

PRINT '';
PRINT '--- reconciliation view must be queryable and empty ---';
SELECT COUNT(*) AS unreconciled_balances
FROM FINCORE.V_ADVANCE_RECONCILIATION
WHERE TRANSFERRED_DELTA <> 0 OR DISBURSED_DELTA <> 0
   OR EARMARKED_DELTA  <> 0 OR SPENT_DELTA     <> 0;

PRINT '';
PRINT '--- FINCORE tables under the tenant policy ---';
SELECT COUNT(DISTINCT sp.target_object_id) AS fincore_tables_scoped
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id = sp.object_id
WHERE p.name = 'tenant_country_policy'
  AND OBJECT_SCHEMA_NAME(sp.target_object_id) = 'FINCORE';

PRINT '';
PRINT '--- policy must still be OFF; predicate count must only have grown ---';
SELECT p.is_enabled,
       (SELECT COUNT(*) FROM sys.security_predicates sp WHERE sp.object_id = p.object_id) AS total_predicates
FROM sys.security_policies p WHERE p.name = 'tenant_country_policy';
PRINT '=============== end V042 verification =============';
GO
