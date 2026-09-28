/*
    V041 — the chart of accounts (الدليل المحاسبي).

    Why
    ---
    Nothing in this database classifies money. STUDENT_FINANCE records that a
    student was paid; WAFAR_SPENDING records that savings were spent. Neither
    says which budget chapter the money came out of, so no statutory report can
    be produced from it and no figure can be traced to an appropriation.

    Shape fixed by law, values fixed by the Regulation
    --------------------------------------------------
    The Executive Regulation of the Financial Law classifies public budgets into
    أبواب، بنود، أنواع، مجموعات، حسابات and states that the Regulation itself
    defines their meanings, natures and names:

        "تبوب الموازنات العامة إلى ابواب وبنود وانواع ومجموعات وحسابات
         تحدد اللائحة التنفيذية لهذا القانون مدلولاتها وطبيعتها ومسمياتها"

    So the hierarchy is law and is built here. The code values are a controlled
    document, and THIS SCRIPT SEEDS NONE OF THEM. Not one account. Inventing
    plausible chapter numbers for a Ministry of Finance system would be the same
    defect V034 was written to remove — figures that look official and are not.

    Everything the Regulation gets to decide is therefore data, not schema:

        COA_SEGMENT   which segments compose an account code
        COA_LEVEL     what the levels of each segment are called, and how many
                      digits a code at that level carries
        COA_ACCOUNT   the accounts (empty)

    CODE_LENGTH is deliberately NULLABLE and is seeded NULL. A width is as much
    part of the Regulation as a code is, and a guessed width would reject the
    real manual on import. NULL means "not yet specified, do not validate".

    Segments seeded for this ministry's flow
    ----------------------------------------
        ECONOMIC        the fee type -- stipend, books, travel, university fees
        ADMIN           the spending unit -- the cultural attache
        FUNDING_SOURCE  budget money or Wafar money
        FUNCTIONAL      functional classification
        PROJECT         project, where one applies
        GOV             governorate

    ADMIN and FUNDING_SOURCE carry the weight here: together they are what lets a
    report answer "how much of Cairo's spend this quarter came from Wafar rather
    than from the quarterly allocation" -- a question the current schema cannot
    express at all.

    Rules the database enforces, not the UI
    ---------------------------------------
      * only a leaf may be posted to (a parent that receives postings makes its
        own subtotal wrong);
      * a child's FULL_CODE is its parent's FULL_CODE plus its own CODE;
      * a code's length matches its level, once a width is specified;
      * NORMAL_BALANCE agrees with ACCOUNT_NATURE;
      * an account's CODE cannot change once it has children, because that would
        silently invalidate every descendant's FULL_CODE;
      * no parent cycles.

    Accounts are deactivated, never deleted. An account that has been posted to
    must survive for the audit trail even after it stops being used.

    Rollback
    --------
        ALTER SECURITY POLICY MOHECORE.tenant_country_policy
            DROP FILTER PREDICATE ON FINCORE.COA_ACCOUNT, ... ;   -- see section 6
        DROP TRIGGER  FINCORE.TR_COA_ACCOUNT_VALIDATE;
        DROP TABLE    FINCORE.COA_ACCOUNT_MAP;
        DROP TABLE    FINCORE.COA_ACCOUNT;
        DROP TABLE    FINCORE.COA_LEVEL;
        DROP TABLE    FINCORE.COA_SEGMENT;
        DROP FUNCTION FINCORE.fn_coa_full_code;
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ------------------------------------------------------------------ *
 * 0. Align the tenant column name with the rest of the system.
 *    V040 created FISCAL_YEAR.COUNTRY_ID; every other tenant-scoped
 *    table in the database calls it STUDY_COUNTRY_IN_ID, and the
 *    catalogue-driven checks in V032 look for that name. The table is
 *    new and its column is entirely NULL, so this is free.
 * ------------------------------------------------------------------ */
IF COL_LENGTH('FINCORE.FISCAL_YEAR', 'COUNTRY_ID') IS NOT NULL
   AND COL_LENGTH('FINCORE.FISCAL_YEAR', 'STUDY_COUNTRY_IN_ID') IS NULL
BEGIN
    EXEC sp_rename 'FINCORE.FISCAL_YEAR.COUNTRY_ID', 'STUDY_COUNTRY_IN_ID', 'COLUMN';
    PRINT 'V041: renamed FISCAL_YEAR.COUNTRY_ID -> STUDY_COUNTRY_IN_ID.';
END
GO

/* ------------------------------------------------------------------ *
 * 1. Segments
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.COA_SEGMENT', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.COA_SEGMENT (
        SEGMENT_ID   int           IDENTITY(1,1) NOT NULL,
        SEGMENT_CODE varchar(30)   NOT NULL,
        NAME_AR      nvarchar(150) NOT NULL,
        NAME_EN      nvarchar(150) NOT NULL,
        ORDINAL      tinyint       NOT NULL,
        IS_REQUIRED  bit           NOT NULL CONSTRAINT DF_COA_SEGMENT_REQ    DEFAULT (0),
        IS_ACTIVE    bit           NOT NULL CONSTRAINT DF_COA_SEGMENT_ACTIVE DEFAULT (1),
        CONSTRAINT PK_COA_SEGMENT         PRIMARY KEY (SEGMENT_ID),
        CONSTRAINT UX_COA_SEGMENT_CODE    UNIQUE (SEGMENT_CODE),
        CONSTRAINT UX_COA_SEGMENT_ORDINAL UNIQUE (ORDINAL)
    );
    PRINT 'V041: created FINCORE.COA_SEGMENT.';
END
GO

/* ------------------------------------------------------------------ *
 * 2. Levels — باب / بند / نوع / مجموعة / حساب, per segment
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.COA_LEVEL', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.COA_LEVEL (
        LEVEL_ID    int           IDENTITY(1,1) NOT NULL,
        SEGMENT_ID  int           NOT NULL,
        LEVEL_NO    tinyint       NOT NULL,
        NAME_AR     nvarchar(100) NOT NULL,
        NAME_EN     nvarchar(100) NOT NULL,
        -- NULL = width not specified by the Regulation yet, so not validated.
        -- Never guess this: a wrong width rejects the real manual on import.
        CODE_LENGTH tinyint       NULL,
        IS_ACTIVE   bit           NOT NULL CONSTRAINT DF_COA_LEVEL_ACTIVE DEFAULT (1),
        CONSTRAINT PK_COA_LEVEL       PRIMARY KEY (LEVEL_ID),
        CONSTRAINT UX_COA_LEVEL_SEG   UNIQUE (SEGMENT_ID, LEVEL_NO),
        CONSTRAINT FK_COA_LEVEL_SEG   FOREIGN KEY (SEGMENT_ID)
            REFERENCES FINCORE.COA_SEGMENT (SEGMENT_ID),
        CONSTRAINT CK_COA_LEVEL_NO    CHECK (LEVEL_NO BETWEEN 1 AND 10),
        CONSTRAINT CK_COA_LEVEL_WIDTH CHECK (CODE_LENGTH IS NULL OR CODE_LENGTH BETWEEN 1 AND 10)
    );
    PRINT 'V041: created FINCORE.COA_LEVEL.';
END
GO

/* ------------------------------------------------------------------ *
 * 3. Accounts
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.COA_ACCOUNT', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.COA_ACCOUNT (
        ACCOUNT_ID          int           IDENTITY(1,1) NOT NULL,
        SEGMENT_ID          int           NOT NULL,
        LEVEL_NO            tinyint       NOT NULL,
        PARENT_ACCOUNT_ID   int           NULL,
        CODE                nvarchar(20)  NOT NULL,
        -- The materialised path: parent's FULL_CODE + CODE. Stored rather than
        -- derived so it can be indexed, unique-constrained and reported on.
        FULL_CODE           nvarchar(80)  NOT NULL,
        NAME_AR             nvarchar(300) NOT NULL,
        NAME_EN             nvarchar(300) NULL,
        ACCOUNT_NATURE      varchar(12)   NOT NULL,
        NORMAL_BALANCE      char(1)       NOT NULL,
        IS_POSTABLE         bit           NOT NULL CONSTRAINT DF_COA_ACCOUNT_POSTABLE  DEFAULT (0),
        IS_BUDGETARY        bit           NOT NULL CONSTRAINT DF_COA_ACCOUNT_BUDGETARY DEFAULT (0),
        -- The chart is versioned per fiscal year: a chapter renumbered for 2027
        -- must not rewrite what 2026 was reported on.
        FISCAL_YEAR_ID      int           NOT NULL,
        EFFECTIVE_FROM      date          NULL,
        EFFECTIVE_TO        date          NULL,
        IS_ACTIVE           bit           NOT NULL CONSTRAINT DF_COA_ACCOUNT_ACTIVE DEFAULT (1),
        STUDY_COUNTRY_IN_ID int           NULL,   -- NULL = ministry-wide (national chart)
        CREATED_BY          int           NULL,
        CREATED_AT          datetime2(3)  NOT NULL CONSTRAINT DF_COA_ACCOUNT_CREATED DEFAULT (SYSUTCDATETIME()),
        UPDATED_BY          int           NULL,
        UPDATED_AT          datetime2(3)  NULL,
        CONSTRAINT PK_COA_ACCOUNT PRIMARY KEY (ACCOUNT_ID),
        CONSTRAINT UX_COA_ACCOUNT_FULLCODE UNIQUE (SEGMENT_ID, FISCAL_YEAR_ID, FULL_CODE),
        CONSTRAINT FK_COA_ACCOUNT_SEG    FOREIGN KEY (SEGMENT_ID)     REFERENCES FINCORE.COA_SEGMENT (SEGMENT_ID),
        CONSTRAINT FK_COA_ACCOUNT_YEAR   FOREIGN KEY (FISCAL_YEAR_ID) REFERENCES FINCORE.FISCAL_YEAR  (FISCAL_YEAR_ID),
        CONSTRAINT FK_COA_ACCOUNT_PARENT FOREIGN KEY (PARENT_ACCOUNT_ID) REFERENCES FINCORE.COA_ACCOUNT (ACCOUNT_ID),
        CONSTRAINT CK_COA_ACCOUNT_NATURE
            CHECK (ACCOUNT_NATURE IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
        /* Debit-normal: assets and expenses. Credit-normal: liabilities, equity,
           revenue. Getting this wrong inverts the account in every report. */
        CONSTRAINT CK_COA_ACCOUNT_NORMAL_BALANCE
            CHECK ((ACCOUNT_NATURE IN ('ASSET','EXPENSE')             AND NORMAL_BALANCE = 'D')
                OR (ACCOUNT_NATURE IN ('LIABILITY','EQUITY','REVENUE') AND NORMAL_BALANCE = 'C')),
        CONSTRAINT CK_COA_ACCOUNT_LEVEL  CHECK (LEVEL_NO BETWEEN 1 AND 10),
        CONSTRAINT CK_COA_ACCOUNT_ROOT   CHECK ((LEVEL_NO = 1 AND PARENT_ACCOUNT_ID IS NULL)
                                             OR (LEVEL_NO > 1 AND PARENT_ACCOUNT_ID IS NOT NULL)),
        CONSTRAINT CK_COA_ACCOUNT_DATES  CHECK (EFFECTIVE_TO IS NULL OR EFFECTIVE_FROM IS NULL
                                             OR EFFECTIVE_TO >= EFFECTIVE_FROM),
        CONSTRAINT CK_COA_ACCOUNT_NOT_SELF_PARENT CHECK (PARENT_ACCOUNT_ID <> ACCOUNT_ID)
    );
    CREATE INDEX IX_COA_ACCOUNT_PARENT   ON FINCORE.COA_ACCOUNT (PARENT_ACCOUNT_ID);
    CREATE INDEX IX_COA_ACCOUNT_POSTABLE ON FINCORE.COA_ACCOUNT (FISCAL_YEAR_ID, IS_POSTABLE, IS_ACTIVE);
    PRINT 'V041: created FINCORE.COA_ACCOUNT.';
END
GO

/* ------------------------------------------------------------------ *
 * 4. The seam to the money that already exists.
 *
 *    Nothing consumes this until the ledger round. It exists now so an
 *    accountant can classify the existing flows in parallel with the
 *    ledger being built -- that classification is the slow, human part.
 * ------------------------------------------------------------------ */
IF OBJECT_ID('FINCORE.COA_ACCOUNT_MAP', 'U') IS NULL
BEGIN
    CREATE TABLE FINCORE.COA_ACCOUNT_MAP (
        MAP_ID              int           IDENTITY(1,1) NOT NULL,
        -- STUDENT_FINANCE | WAFAR_SPENDING | STUDY_UNIVERSITY_FEE
        -- STUDENT_TRAVEL_TICKET | STUDENT_BOOKS_ALLOWANCE | PAYMENT_AUTH
        SOURCE_TYPE         varchar(40)   NOT NULL,
        -- Discriminator within the source, e.g. WAFAR_SPENDING_CATEGORY.CATEGORY_ID
        -- or STUDY_WAFAR_TYPE.WAFAR_TYPE_ID. NULL = applies to the whole source.
        SOURCE_KEY          nvarchar(50)  NULL,
        DEBIT_ACCOUNT_ID    int           NOT NULL,
        CREDIT_ACCOUNT_ID   int           NOT NULL,
        FISCAL_YEAR_ID      int           NOT NULL,
        EFFECTIVE_FROM      date          NULL,
        EFFECTIVE_TO        date          NULL,
        IS_ACTIVE           bit           NOT NULL CONSTRAINT DF_COA_MAP_ACTIVE DEFAULT (1),
        STUDY_COUNTRY_IN_ID int           NULL,
        NOTES               nvarchar(500) NULL,
        CREATED_BY          int           NULL,
        CREATED_AT          datetime2(3)  NOT NULL CONSTRAINT DF_COA_MAP_CREATED DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_COA_ACCOUNT_MAP PRIMARY KEY (MAP_ID),
        CONSTRAINT UX_COA_ACCOUNT_MAP UNIQUE (SOURCE_TYPE, SOURCE_KEY, FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID),
        CONSTRAINT FK_COA_MAP_DR   FOREIGN KEY (DEBIT_ACCOUNT_ID)  REFERENCES FINCORE.COA_ACCOUNT (ACCOUNT_ID),
        CONSTRAINT FK_COA_MAP_CR   FOREIGN KEY (CREDIT_ACCOUNT_ID) REFERENCES FINCORE.COA_ACCOUNT (ACCOUNT_ID),
        CONSTRAINT FK_COA_MAP_YEAR FOREIGN KEY (FISCAL_YEAR_ID)    REFERENCES FINCORE.FISCAL_YEAR (FISCAL_YEAR_ID),
        CONSTRAINT CK_COA_MAP_DISTINCT CHECK (DEBIT_ACCOUNT_ID <> CREDIT_ACCOUNT_ID)
    );
    PRINT 'V041: created FINCORE.COA_ACCOUNT_MAP.';
END
GO

/* ------------------------------------------------------------------ *
 * 5. Helper + validation
 * ------------------------------------------------------------------ */
CREATE OR ALTER FUNCTION FINCORE.fn_coa_full_code(@parentAccountId int, @code nvarchar(20))
RETURNS nvarchar(80)
AS
BEGIN
    /* What FULL_CODE must be for a child of @parentAccountId. The application
       calls this when building an account; the trigger below checks the result. */
    RETURN ISNULL((SELECT FULL_CODE FROM FINCORE.COA_ACCOUNT WHERE ACCOUNT_ID = @parentAccountId), N'')
           + @code;
END
GO
PRINT 'V041: created FINCORE.fn_coa_full_code.';
GO

IF OBJECT_ID('FINCORE.TR_COA_ACCOUNT_VALIDATE', 'TR') IS NOT NULL
    DROP TRIGGER FINCORE.TR_COA_ACCOUNT_VALIDATE;
GO

CREATE TRIGGER FINCORE.TR_COA_ACCOUNT_VALIDATE
ON FINCORE.COA_ACCOUNT
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    /* Parent must be in the same segment and fiscal year, and exactly one
       level up. A chart whose levels skip is not a hierarchy. */
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN FINCORE.COA_ACCOUNT p ON p.ACCOUNT_ID = i.PARENT_ACCOUNT_ID
        WHERE p.SEGMENT_ID <> i.SEGMENT_ID
           OR p.FISCAL_YEAR_ID <> i.FISCAL_YEAR_ID
           OR p.LEVEL_NO <> i.LEVEL_NO - 1
    )
        THROW 50050, 'Parent account must be in the same segment and fiscal year, exactly one level above.', 1;

    /* FULL_CODE must be the parent's FULL_CODE plus this account's CODE. */
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE i.FULL_CODE <> FINCORE.fn_coa_full_code(i.PARENT_ACCOUNT_ID, i.CODE)
    )
        THROW 50051, 'FULL_CODE must equal the parent''s FULL_CODE followed by CODE. Use FINCORE.fn_coa_full_code.', 1;

    /* Code width, where the Regulation's width has been recorded. */
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN FINCORE.COA_LEVEL l ON l.SEGMENT_ID = i.SEGMENT_ID AND l.LEVEL_NO = i.LEVEL_NO
        WHERE l.CODE_LENGTH IS NOT NULL AND LEN(i.CODE) <> l.CODE_LENGTH
    )
        THROW 50052, 'Account CODE length does not match the code length defined for its level.', 1;

    /* Only a leaf may be posted to. A parent that receives postings makes its
       own subtotal disagree with the sum of its children. */
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE i.IS_POSTABLE = 1
          AND EXISTS (SELECT 1 FROM FINCORE.COA_ACCOUNT c WHERE c.PARENT_ACCOUNT_ID = i.ACCOUNT_ID)
    )
        THROW 50053, 'Only a leaf account may be postable. This account has children.', 1;

    /* ...and the same rule from the other direction. */
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN FINCORE.COA_ACCOUNT p ON p.ACCOUNT_ID = i.PARENT_ACCOUNT_ID
        WHERE p.IS_POSTABLE = 1
    )
        THROW 50054, 'Cannot add a child to a postable account. Clear IS_POSTABLE on the parent first.', 1;

    /* Changing a CODE that has children would invalidate every descendant's
       FULL_CODE without touching those rows. */
    IF UPDATE(CODE) AND EXISTS (
        SELECT 1 FROM inserted i
        JOIN deleted  d ON d.ACCOUNT_ID = i.ACCOUNT_ID
        WHERE d.CODE <> i.CODE
          AND EXISTS (SELECT 1 FROM FINCORE.COA_ACCOUNT c WHERE c.PARENT_ACCOUNT_ID = i.ACCOUNT_ID)
    )
        THROW 50055, 'Cannot change the CODE of an account that has children; it would orphan their FULL_CODE.', 1;

    /* Parent cycles. CK_COA_ACCOUNT_NOT_SELF_PARENT catches the one-step case;
       this catches A -> B -> A and longer. */
    IF EXISTS (
        SELECT 1
        FROM inserted i
        CROSS APPLY (
            SELECT TOP 1 1 AS cycle
            FROM (
                SELECT a.ACCOUNT_ID, a.PARENT_ACCOUNT_ID, 1 AS depth
                FROM FINCORE.COA_ACCOUNT a WHERE a.ACCOUNT_ID = i.PARENT_ACCOUNT_ID
                UNION ALL
                SELECT a2.ACCOUNT_ID, a2.PARENT_ACCOUNT_ID, 2
                FROM FINCORE.COA_ACCOUNT a2
                JOIN FINCORE.COA_ACCOUNT a1 ON a1.PARENT_ACCOUNT_ID = a2.ACCOUNT_ID
                WHERE a1.ACCOUNT_ID = i.PARENT_ACCOUNT_ID
            ) chain
            WHERE chain.ACCOUNT_ID = i.ACCOUNT_ID
        ) x
    )
        THROW 50056, 'Parent chain would form a cycle.', 1;
END
GO
PRINT 'V041: created TR_COA_ACCOUNT_VALIDATE.';
GO

/* ------------------------------------------------------------------ *
 * 6. Segment and level scaffolding.
 *
 *    Structure only. Level NAMES come from the Regulation's own wording;
 *    CODE_LENGTH is left NULL because the widths are the Regulation's to
 *    state and this script will not guess them.
 * ------------------------------------------------------------------ */
IF NOT EXISTS (SELECT 1 FROM FINCORE.COA_SEGMENT)
BEGIN
    INSERT INTO FINCORE.COA_SEGMENT (SEGMENT_CODE, NAME_AR, NAME_EN, ORDINAL, IS_REQUIRED) VALUES
        ('ECONOMIC',       N'التصنيف الاقتصادي',  N'Economic classification', 1, 1),
        ('ADMIN',          N'الوحدة الإدارية',    N'Administrative unit',     2, 1),
        ('FUNDING_SOURCE', N'مصدر التمويل',       N'Funding source',          3, 1),
        ('FUNCTIONAL',     N'التصنيف الوظيفي',    N'Functional classification',4, 0),
        ('PROJECT',        N'المشروع',            N'Project',                 5, 0),
        ('GOV',            N'المحافظة',           N'Governorate',             6, 0);

    DECLARE @econ int = (SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'ECONOMIC');
    DECLARE @adm  int = (SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'ADMIN');
    DECLARE @fund int = (SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'FUNDING_SOURCE');
    DECLARE @func int = (SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'FUNCTIONAL');
    DECLARE @proj int = (SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'PROJECT');
    DECLARE @gov  int = (SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'GOV');

    /* The five economic levels, in the order the Regulation lists them. */
    INSERT INTO FINCORE.COA_LEVEL (SEGMENT_ID, LEVEL_NO, NAME_AR, NAME_EN, CODE_LENGTH) VALUES
        (@econ, 1, N'باب',     N'Chapter', NULL),
        (@econ, 2, N'بند',     N'Item',    NULL),
        (@econ, 3, N'نوع',     N'Type',    NULL),
        (@econ, 4, N'مجموعة',  N'Group',   NULL),
        (@econ, 5, N'حساب',    N'Account', NULL),

        (@adm,  1, N'الجهة',           N'Entity',              NULL),
        (@adm,  2, N'الوحدة الإدارية', N'Administrative unit', NULL),

        (@fund, 1, N'مصدر التمويل', N'Funding source', NULL),
        (@func, 1, N'الوظيفة',      N'Function',       NULL),
        (@func, 2, N'الوظيفة الفرعية', N'Sub-function', NULL),
        (@proj, 1, N'المشروع',      N'Project',        NULL),
        (@gov,  1, N'المحافظة',     N'Governorate',    NULL);

    PRINT 'V041: seeded 6 segments and 12 levels. NO account codes seeded.';
END
ELSE
    PRINT 'V041: COA_SEGMENT already populated; scaffolding seed skipped.';
GO

/* ------------------------------------------------------------------ *
 * 7. Put the new tables under the tenant policy.
 *
 *    ALTER ... ADD rather than the drop-and-recreate V032 uses: the
 *    policy currently carries 69 predicates across MOHECORE and
 *    STUDENTCORE, several with rules V037 chose deliberately, and
 *    rebuilding risks losing them. Adding is additive and reversible.
 *
 *    The policy's STATE is not touched. It ships OFF and stays OFF.
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
        PRINT 'V041: FINCORE tables added to tenant_country_policy.';
    END
    ELSE
        PRINT 'V041: FINCORE tables already under tenant_country_policy.';
END
GO

/* ------------------------------------------------------------------ *
 * 8. Verification
 * ------------------------------------------------------------------ */
PRINT '';
PRINT '================ V041 verification ================';

PRINT '--- THE CHART MUST BE EMPTY. account_count must be 0. ---';
SELECT COUNT(*) AS account_count FROM FINCORE.COA_ACCOUNT;

PRINT '';
PRINT '--- segments and their levels ---';
SELECT s.ORDINAL, s.SEGMENT_CODE, s.NAME_EN, s.IS_REQUIRED,
       COUNT(l.LEVEL_ID) AS levels,
       SUM(CASE WHEN l.CODE_LENGTH IS NULL THEN 1 ELSE 0 END) AS widths_not_yet_specified
FROM FINCORE.COA_SEGMENT s
LEFT JOIN FINCORE.COA_LEVEL l ON l.SEGMENT_ID = s.SEGMENT_ID
GROUP BY s.ORDINAL, s.SEGMENT_CODE, s.NAME_EN, s.IS_REQUIRED
ORDER BY s.ORDINAL;

PRINT '';
PRINT '--- the economic levels, in the Regulation''s order ---';
SELECT l.LEVEL_NO, l.NAME_AR, l.NAME_EN, l.CODE_LENGTH
FROM FINCORE.COA_LEVEL l
JOIN FINCORE.COA_SEGMENT s ON s.SEGMENT_ID = l.SEGMENT_ID
WHERE s.SEGMENT_CODE = 'ECONOMIC'
ORDER BY l.LEVEL_NO;

PRINT '';
PRINT '--- FINCORE tables under the tenant policy ---';
SELECT OBJECT_NAME(sp.target_object_id) AS [table], sp.predicate_type_desc, sp.operation_desc
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id = sp.object_id
WHERE p.name = 'tenant_country_policy'
  AND OBJECT_SCHEMA_NAME(sp.target_object_id) = 'FINCORE'
ORDER BY [table], sp.predicate_type_desc;

PRINT '';
PRINT '--- the policy must still be OFF, and must not have lost predicates ---';
SELECT p.is_enabled,
       (SELECT COUNT(*) FROM sys.security_predicates sp WHERE sp.object_id = p.object_id) AS total_predicates
FROM sys.security_policies p WHERE p.name = 'tenant_country_policy';
PRINT '--- is_enabled must be 0; total_predicates must be >= 69 ---';
PRINT '=============== end V041 verification =============';
GO
