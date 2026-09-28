/*
    V045 — populate the accounting tables from the operational data that
           already exists.

    Why
    ---
    V038-V043 built the accounting structure and left it empty. This fills the
    fund chain from what the ministry has actually recorded: student finance,
    university fees, travel tickets, book allowances, Wafar deposits and Wafar
    spending.

    WHAT THIS SCRIPT DOES NOT DO
    ----------------------------
    It creates no chart-of-accounts rows. Not one. The Executive Regulation of
    the Financial Law defines the meanings, natures and names of الأبواب والبنود
    والأنواع والمجموعات والحسابات, so the codes belong to the الدليل المحاسبي
    الموحد and are entered or imported, never derived. FINCORE.COA_ACCOUNT is
    still empty when this finishes, and ChartOfAccountsTest.chartShipsEmpty
    still passes.

    Derived, not recorded
    ---------------------
    Some of what this writes is a RECONSTRUCTION, not a transcription, because
    the source data does not contain it:

      * ATTACHE_ALLOCATION. There is no transfer table anywhere in the database.
        The allocation is inferred as (what was disbursed + what became Wafar):
        money cannot have been paid out or set aside unless it first arrived.
        These rows carry TRANSFER_REFERENCE = 'DERIVED-V045' so they can be told
        apart from a real transfer advice, and deleted wholesale if the real
        figures are ever loaded.

      * PAYMENT_BATCH number is always 1. The batch (الدفعة) is genuinely not
        recorded: STUDENT_FINANCE.STUDY_FINANCE_DOF_ID is 0 on all 941 rows. One
        batch per quarter is the only claim the data supports; it does not mean
        there was only one.

      * Currency for stipends and book allowances. STUDENT_FINANCE.
        STUDY_CURRENCY_IN_ID is NULL on every row, so the currency is taken from
        STUDENT_DATA.amount_currency (1/USD for 940 of 941 students). University
        fees and travel tickets carry their own currency and are used as-is --
        university fees alone span four currencies.

    Every derived row is marked. Re-running the script removes what it wrote
    before and rebuilds, so it is safe to run twice.

    What the source data says
    -------------------------
        STUDENT_FINANCE           941 rows   2024 Q2 (939) + 2025 Q1 (2)
        STUDENT_FINANCE_ARCH    3,764 rows   2024 Q1-Q4, 941 each
        STUDENT_FINANCE_WAFAR      23 rows   37,160 -- exactly matches
                                             SUM(STUDENT_FINANCE.WAFAR_AMOUNT)
        WAFAR_SPENDING             17 rows   32,400 approved (29,400 in 2024,
                                             3,000 in 2025)
        STUDY_UNIVERSITY_FEE       15 rows   4 currencies
        STUDENT_TRAVEL_TICKET      10 rows
        STUDENT_BOOKS_ALLOWANCE    15 rows

    Two observations worth your attention, reported again at the end:

    1. THE ARCHIVE IS A REPLICATED SNAPSHOT, NOT HISTORY. Every student has the
       identical amount in all four quarters of 2024, and every quarter totals
       1,574,580 with zero Wafar. Real quarter-by-quarter history does not look
       like that. It was almost certainly produced by
       seed_student_data_arch_from_student_data.sql. Deriving from it produces
       four identical quarters; that is faithful to the data, and the data is
       probably not faithful to reality.

    2. THE LIVE TABLE AND THE ARCHIVE DISAGREE ABOUT 2024 Q2. Live says 1,571,220
       received with 37,160 Wafar; the archive says 1,574,580 with none. Where
       both cover the same (student, year, quarter) THE LIVE ROW WINS, because it
       is the one carrying the Wafar detail.

    Rollback
    --------
        Re-run section 1 of this script on its own; it deletes everything V045
        created and nothing else.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

DECLARE @MARK nvarchar(20) = N'DERIVED-V045';

/* ================================================================== *
 * 1. Remove anything a previous run of this script created.
 *    Order matters: children before parents.
 * ================================================================== */
DELETE FROM FINCORE.WAFAR_USE       WHERE NOTES LIKE N'%' + @MARK + N'%';
DELETE FROM FINCORE.WAFAR_EARMARK   WHERE NOTES LIKE N'%' + @MARK + N'%';
DELETE FROM FINCORE.PAYMENT_BATCH_LINE
    WHERE BATCH_ID IN (SELECT BATCH_ID FROM FINCORE.PAYMENT_BATCH WHERE NOTES LIKE N'%' + @MARK + N'%');
DELETE FROM FINCORE.PAYMENT_BATCH   WHERE NOTES LIKE N'%' + @MARK + N'%';
DELETE FROM FINCORE.ATTACHE_ALLOCATION WHERE TRANSFER_REFERENCE = @MARK;
DELETE FROM FINCORE.ATTACHE_ADVANCE_BALANCE;   -- fully rebuilt below
UPDATE MOHECORE.MOHE_QUARTER SET BUDGET_AMOUNT = NULL WHERE BUDGET_AMOUNT IS NOT NULL;

PRINT 'V045 step 1: cleared any previous derivation.';
GO

/* ================================================================== *
 * 2. Collect every disbursement the operational tables record, in one
 *    shape: who, which quarter, which attache, which currency, what
 *    they were entitled to, and what actually went out.
 * ================================================================== */
IF OBJECT_ID('tempdb..#src') IS NOT NULL DROP TABLE #src;
CREATE TABLE #src (
    fee_code    varchar(30)   NOT NULL,
    quarter_key int           NOT NULL,
    country_id  int           NOT NULL,
    currency_id int           NOT NULL,
    student_id  int           NOT NULL,
    entitled    decimal(18,2) NOT NULL,
    disbursed   decimal(18,2) NOT NULL,
    held_reason nvarchar(300) NULL
);

/* ---- 2a. Stipends -------------------------------------------------
   The live table wins over the archive for the same (student, quarter):
   it is the row that carries the Wafar split.

   entitled  = STUDENT_FIN_RECIEVED_AMOUNT
   disbursed = NET_AMOUNT, the persisted computed column (received - wafar).
   The schema has always encoded the rule this needs: the Wafar part of an
   entitlement is precisely the part that was NOT paid out.               */
;WITH stipend AS (
    SELECT f.STUDENT_ID, f.STUDENT_FIN_RECIEVED_YEAR AS y, f.STUDENT_FIN_RECIEVED_MONTH AS q,
           f.STUDY_COUNTRY_IN_ID AS country_id,
           CAST(ISNULL(f.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS decimal(18,2)) AS entitled,
           CAST(ISNULL(f.NET_AMOUNT, 0) AS decimal(18,2))                  AS disbursed,
           CAST(ISNULL(f.WAFAR_AMOUNT, 0) AS decimal(18,2))                AS wafar,
           1 AS src_rank                                   -- live
    FROM MOHECORE.STUDENT_FINANCE f
    UNION ALL
    SELECT a.STUDENT_ID, a.STUDENT_FIN_RECIEVED_YEAR, a.STUDENT_FIN_RECIEVED_MONTH,
           a.STUDY_COUNTRY_IN_ID,
           CAST(ISNULL(a.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS decimal(18,2)),
           CAST(ISNULL(a.NET_AMOUNT, 0) AS decimal(18,2)),
           CAST(ISNULL(a.WAFAR_AMOUNT, 0) AS decimal(18,2)),
           2                                               -- archive
    FROM MOHECORE.STUDENT_FINANCE_ARCH a
), ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY STUDENT_ID, y, q ORDER BY src_rank) AS rn
    FROM stipend
)
INSERT INTO #src (fee_code, quarter_key, country_id, currency_id, student_id, entitled, disbursed, held_reason)
SELECT 'STIPEND', r.y * 10 + r.q, r.country_id,
       -- STUDENT_FINANCE carries no currency at all; STUDENT_DATA does.
       ISNULL(NULLIF(sd.amount_currency, 0), 1),
       r.STUDENT_ID, r.entitled, r.disbursed,
       CASE WHEN r.wafar > 0 THEN N'مبلغ محول إلى الوفر / amount moved to Wafar' END
FROM ranked r
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = r.STUDENT_ID
WHERE r.rn = 1
  AND r.country_id IS NOT NULL
  AND r.q BETWEEN 1 AND 4
  AND EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER mq WHERE mq.QUARTER = r.y * 10 + r.q);

PRINT 'V045 step 2a: stipend rows      = ' + CAST(@@ROWCOUNT AS varchar(10));

/* ---- 2b. University fees ------------------------------------------
   Quarter comes from the payment date. Rows with no payment date, or whose
   quarter has no MOHE_QUARTER row, are skipped and reported in section 9.
   Amounts are the REAL fee (after discount), which is what was actually owed.
   Aggregated per student: two rows exist for the same student and academic
   year, and PAYMENT_BATCH_LINE allows one line per student per batch.     */
INSERT INTO #src (fee_code, quarter_key, country_id, currency_id, student_id, entitled, disbursed, held_reason)
SELECT 'UNIVERSITY_FEE',
       YEAR(u.STUDY_UNIVERSITY_FEE_PAY_DATE) * 10 + DATEPART(QUARTER, u.STUDY_UNIVERSITY_FEE_PAY_DATE),
       u.STUDY_COUNTRY_IN_ID,
       ISNULL(NULLIF(u.STUDY_UNIVERSITY_FEE_CURRENCY, 0), 1),
       u.STUDENT_ID,
       SUM(CAST(ISNULL(u.STUDY_UNIVERSITY_REAL_FEE_AMOUNT, 0) AS decimal(18,2))),
       -- PAY_STATUS 1 = Paid (LU_UNIVERSITY_FEE_STATUS). Anything else has not left.
       SUM(CASE WHEN u.STUDY_UNIVERSITY_FEE_PAY_STATUS = 1
                THEN CAST(ISNULL(u.STUDY_UNIVERSITY_REAL_FEE_AMOUNT, 0) AS decimal(18,2)) ELSE 0 END),
       MAX(CASE WHEN u.STUDY_UNIVERSITY_FEE_PAY_STATUS <> 1
                THEN N'رسوم غير مسددة / fee not settled' END)
FROM MOHECORE.STUDY_UNIVERSITY_FEE u
WHERE u.STUDY_UNIVERSITY_FEE_PAY_DATE IS NOT NULL
  AND u.STUDY_COUNTRY_IN_ID IS NOT NULL
  AND EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER mq
              WHERE mq.QUARTER = YEAR(u.STUDY_UNIVERSITY_FEE_PAY_DATE) * 10
                               + DATEPART(QUARTER, u.STUDY_UNIVERSITY_FEE_PAY_DATE))
GROUP BY YEAR(u.STUDY_UNIVERSITY_FEE_PAY_DATE) * 10 + DATEPART(QUARTER, u.STUDY_UNIVERSITY_FEE_PAY_DATE),
         u.STUDY_COUNTRY_IN_ID, ISNULL(NULLIF(u.STUDY_UNIVERSITY_FEE_CURRENCY, 0), 1), u.STUDENT_ID;

PRINT 'V045 step 2b: university fee rows = ' + CAST(@@ROWCOUNT AS varchar(10));

/* ---- 2c. Travel tickets -------------------------------------------
   A booked ticket is money already spent, so entitled = disbursed.        */
INSERT INTO #src (fee_code, quarter_key, country_id, currency_id, student_id, entitled, disbursed, held_reason)
SELECT 'TRAVEL',
       YEAR(t.TRAVEL_DATE) * 10 + DATEPART(QUARTER, t.TRAVEL_DATE),
       t.STUDY_COUNTRY_IN_ID,
       ISNULL(NULLIF(t.CURRENCY_ID, 0), 1),
       t.STUDENT_ID,
       SUM(CAST(ISNULL(t.TOTAL_TICKET_COST, t.TICKET_COST) AS decimal(18,2))),
       SUM(CAST(ISNULL(t.TOTAL_TICKET_COST, t.TICKET_COST) AS decimal(18,2))),
       NULL
FROM MOHECORE.STUDENT_TRAVEL_TICKET t
WHERE t.TRAVEL_DATE IS NOT NULL
  AND t.STUDY_COUNTRY_IN_ID IS NOT NULL
  AND ISNULL(t.TOTAL_TICKET_COST, t.TICKET_COST) IS NOT NULL
  AND EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER mq
              WHERE mq.QUARTER = YEAR(t.TRAVEL_DATE) * 10 + DATEPART(QUARTER, t.TRAVEL_DATE))
GROUP BY YEAR(t.TRAVEL_DATE) * 10 + DATEPART(QUARTER, t.TRAVEL_DATE),
         t.STUDY_COUNTRY_IN_ID, ISNULL(NULLIF(t.CURRENCY_ID, 0), 1), t.STUDENT_ID;

PRINT 'V045 step 2c: travel rows       = ' + CAST(@@ROWCOUNT AS varchar(10));

/* ---- 2d. Book allowances ------------------------------------------
   MOHE_QUARTER_ID here is MOHE_QUARTER.ID, not the YYYYQ key, so it is
   joined rather than used directly. PAY_STATUS 1 = Paid.                  */
INSERT INTO #src (fee_code, quarter_key, country_id, currency_id, student_id, entitled, disbursed, held_reason)
SELECT 'BOOKS', mq.QUARTER, b.STUDY_COUNTRY_IN_ID,
       ISNULL(NULLIF(sd.amount_currency, 0), 1),
       b.STUDENT_ID,
       SUM(CAST(ISNULL(b.ALLOWANCE_AMOUNT, 0) AS decimal(18,2))),
       SUM(CASE WHEN b.PAY_STATUS = 1 THEN CAST(ISNULL(b.ALLOWANCE_AMOUNT, 0) AS decimal(18,2)) ELSE 0 END),
       MAX(CASE WHEN b.PAY_STATUS <> 1 THEN N'مخصص كتب غير مصروف / books allowance not paid' END)
FROM MOHECORE.STUDENT_BOOKS_ALLOWANCE b
JOIN MOHECORE.MOHE_QUARTER mq ON mq.ID = b.MOHE_QUARTER_ID
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = b.STUDENT_ID
WHERE b.STUDY_COUNTRY_IN_ID IS NOT NULL
GROUP BY mq.QUARTER, b.STUDY_COUNTRY_IN_ID, ISNULL(NULLIF(sd.amount_currency, 0), 1), b.STUDENT_ID;

PRINT 'V045 step 2d: books rows        = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 3. One payment batch per (quarter, attache, fee type, currency).
 * ================================================================== */
DECLARE @MARK nvarchar(20) = N'DERIVED-V045';

INSERT INTO FINCORE.PAYMENT_BATCH
    (FISCAL_YEAR_ID, MOHE_QUARTER_KEY, STUDY_COUNTRY_IN_ID, BATCH_NO, FEE_TYPE_ID, CURRENCY_ID,
     STATUS, APPROVED_AT, DISBURSED_AT, NOTES)
SELECT fy.FISCAL_YEAR_ID, s.quarter_key, s.country_id, 1, ft.FEE_TYPE_ID, s.currency_id,
       'DISBURSED', SYSUTCDATETIME(), SYSUTCDATETIME(),
       @MARK + N' — batch reconstructed from ' + ft.SOURCE_TABLE
             + N'; the real batch number is not recorded in the source'
FROM (SELECT DISTINCT fee_code, quarter_key, country_id, currency_id FROM #src) s
JOIN FINCORE.FEE_TYPE ft    ON ft.FEE_TYPE_CODE = s.fee_code
JOIN FINCORE.FISCAL_YEAR fy ON fy.YEAR_NO = s.quarter_key / 10;

PRINT 'V045 step 3: payment batches    = ' + CAST(@@ROWCOUNT AS varchar(10));

INSERT INTO FINCORE.PAYMENT_BATCH_LINE
    (BATCH_ID, STUDENT_ID, ENTITLED_AMOUNT, DISBURSED_AMOUNT, HELD_REASON, STUDY_COUNTRY_IN_ID)
SELECT b.BATCH_ID, s.student_id, s.entitled, s.disbursed, s.held_reason, s.country_id
FROM #src s
JOIN FINCORE.FEE_TYPE ft ON ft.FEE_TYPE_CODE = s.fee_code
JOIN FINCORE.PAYMENT_BATCH b
      ON b.MOHE_QUARTER_KEY    = s.quarter_key
     AND b.STUDY_COUNTRY_IN_ID = s.country_id
     AND b.FEE_TYPE_ID         = ft.FEE_TYPE_ID
     AND b.CURRENCY_ID         = s.currency_id
     AND b.BATCH_NO            = 1;

PRINT 'V045 step 3: batch lines        = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 4. Wafar earmarks — entitlements that were not paid.
 *
 *    Taken from STUDENT_FINANCE_WAFAR, which carries the real reason code
 *    (STUDY_WAFAR_TYPE: وفر, إرجاع مبالغ زائدة, خصم تذكرة سفر, ...). Its 23
 *    rows total 37,160, which is exactly SUM(STUDENT_FINANCE.WAFAR_AMOUNT) —
 *    the two independent sources agree, so neither is guessed at.
 * ================================================================== */
DECLARE @MARK nvarchar(20) = N'DERIVED-V045';

INSERT INTO FINCORE.WAFAR_EARMARK
    (FISCAL_YEAR_ID, MOHE_QUARTER_KEY, STUDY_COUNTRY_IN_ID, STUDENT_ID, FEE_TYPE_ID,
     WAFAR_TYPE_ID, CURRENCY_ID, AMOUNT, IS_PARTIAL, NOTES)
SELECT fy.FISCAL_YEAR_ID,
       w.STUDENT_FIN_RECIEVED_YEAR * 10 + w.STUDENT_FIN_RECIEVED_MONTH,
       w.STUDY_COUNTRY_IN_ID, w.STUDENT_ID,
       (SELECT FEE_TYPE_ID FROM FINCORE.FEE_TYPE WHERE FEE_TYPE_CODE = 'STIPEND'),
       w.WAFAR_TYPE_ID,
       ISNULL(NULLIF(sd.amount_currency, 0), 1),
       CAST(w.STUDENT_FIN_RECIEVED_AMOUNT AS decimal(18,2)),
       0,
       @MARK + N' — from STUDENT_FINANCE_WAFAR'
FROM MOHECORE.STUDENT_FINANCE_WAFAR w
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = w.STUDENT_ID
JOIN FINCORE.FISCAL_YEAR fy ON fy.YEAR_NO = w.STUDENT_FIN_RECIEVED_YEAR
WHERE w.STUDY_COUNTRY_IN_ID IS NOT NULL
  AND w.STUDENT_FIN_RECIEVED_AMOUNT > 0
  AND w.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4
  AND EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER mq
              WHERE mq.QUARTER = w.STUDENT_FIN_RECIEVED_YEAR * 10 + w.STUDENT_FIN_RECIEVED_MONTH);

PRINT 'V045 step 4: wafar earmarks     = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 5. Carry the previous year's closing Wafar into the next one.
 *
 *    The deposits all sit in 2024; some of the spending happened in 2025.
 *    Without a carry-forward, 2025 would show savings being spent that were
 *    never set aside — which the database rightly refuses.
 *
 *    The amount carried is the previous year's ACTUAL closing balance
 *    (earmarked less spent), not merely enough to cover the shortfall.
 *    Carrying only what is needed would be an arbitrary number; the closing
 *    balance is a real one.
 *
 *    This must run BEFORE the uses are inserted: TR_WAFAR_USE_SAME_ATTACHE
 *    refuses a use that exceeds the savings held, and until the carry-forward
 *    exists, 2025 holds none.
 * ================================================================== */
DECLARE @MARK nvarchar(20) = N'DERIVED-V045';

/* Hoisted into a variable rather than written inline: SQL Server will not
   accept a subquery in a GROUP BY list, and the attribution below needs to
   group by the same expression it selects. */
DECLARE @soleWafar int = (SELECT MIN(STUDY_COUNTRY_IN_ID) FROM FINCORE.WAFAR_EARMARK);

;WITH earmarked AS (
    SELECT fy.YEAR_NO, e.STUDY_COUNTRY_IN_ID AS c, e.CURRENCY_ID AS cur, SUM(e.AMOUNT) AS amt
    FROM FINCORE.WAFAR_EARMARK e
    JOIN FINCORE.FISCAL_YEAR fy ON fy.FISCAL_YEAR_ID = e.FISCAL_YEAR_ID
    GROUP BY fy.YEAR_NO, e.STUDY_COUNTRY_IN_ID, e.CURRENCY_ID
), spent AS (
    /* From the SOURCE table, because FINCORE.WAFAR_USE is not populated yet. */
    SELECT YEAR(sp.SPENDING_DATE) AS YEAR_NO,
           ISNULL(sp.STUDY_COUNTRY_IN_ID, @soleWafar) AS c,
           1 AS cur, SUM(sp.AMOUNT) AS amt
    FROM MOHECORE.WAFAR_SPENDING sp
    WHERE sp.STATUS = 'APPROVED' AND sp.AMOUNT > 0
    GROUP BY YEAR(sp.SPENDING_DATE), ISNULL(sp.STUDY_COUNTRY_IN_ID, @soleWafar)
), closing AS (
    SELECT e.YEAR_NO, e.c, e.cur, e.amt - ISNULL(s.amt, 0) AS closing_balance
    FROM earmarked e
    LEFT JOIN spent s ON s.YEAR_NO = e.YEAR_NO AND s.c = e.c AND s.cur = e.cur
), needed AS (
    /* Years that spend Wafar without being credited any. */
    SELECT DISTINCT s.YEAR_NO, s.c, s.cur
    FROM spent s
    WHERE NOT EXISTS (SELECT 1 FROM earmarked e
                      WHERE e.YEAR_NO = s.YEAR_NO AND e.c = s.c AND e.cur = s.cur)
)
INSERT INTO FINCORE.WAFAR_EARMARK
    (FISCAL_YEAR_ID, MOHE_QUARTER_KEY, STUDY_COUNTRY_IN_ID, STUDENT_ID, FEE_TYPE_ID,
     WAFAR_TYPE_ID, CURRENCY_ID, AMOUNT, IS_PARTIAL, NOTES)
SELECT fy.FISCAL_YEAR_ID,
       (SELECT MIN(QUARTER) FROM MOHECORE.MOHE_QUARTER WHERE QUARTER_YEAR = n.YEAR_NO),
       n.c, NULL,
       (SELECT FEE_TYPE_ID FROM FINCORE.FEE_TYPE WHERE FEE_TYPE_CODE = 'STIPEND'),
       1,
       n.cur, cl.closing_balance, 0,
       @MARK + N' — رصيد وفر مرحّل من ' + CAST(n.YEAR_NO - 1 AS nvarchar(4))
             + N' / Wafar balance carried forward'
FROM needed n
JOIN closing cl ON cl.YEAR_NO = n.YEAR_NO - 1 AND cl.c = n.c AND cl.cur = n.cur
JOIN FINCORE.FISCAL_YEAR fy ON fy.YEAR_NO = n.YEAR_NO
WHERE cl.closing_balance > 0;

PRINT 'V045 step 5: carry-forward earmarks = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 6. Reconstruct the quarterly allocations.
 *
 *    Money cannot have been disbursed or set aside as Wafar unless it first
 *    reached the attache, so the transfer is inferred as
 *    (disbursed + earmarked) for that quarter, attache and currency.
 *
 *    A carried-forward earmark is excluded: it is last year's cash sitting
 *    at the attache, not a new transfer. It becomes OPENING_BALANCE instead.
 *
 *    This is the most inferential thing the script does, which is why every
 *    row carries TRANSFER_REFERENCE = 'DERIVED-V045'. When the real transfer
 *    advices exist, delete these and load those.
 * ================================================================== */
DECLARE @MARK nvarchar(20) = N'DERIVED-V045';

;WITH disbursed AS (
    SELECT b.FISCAL_YEAR_ID f, b.MOHE_QUARTER_KEY q, b.STUDY_COUNTRY_IN_ID c,
           b.CURRENCY_ID cur, b.FEE_TYPE_ID ft, SUM(l.DISBURSED_AMOUNT) amt
    FROM FINCORE.PAYMENT_BATCH b
    JOIN FINCORE.PAYMENT_BATCH_LINE l ON l.BATCH_ID = b.BATCH_ID
    GROUP BY b.FISCAL_YEAR_ID, b.MOHE_QUARTER_KEY, b.STUDY_COUNTRY_IN_ID, b.CURRENCY_ID, b.FEE_TYPE_ID
), earmarked AS (
    SELECT e.FISCAL_YEAR_ID f, e.MOHE_QUARTER_KEY q, e.STUDY_COUNTRY_IN_ID c,
           e.CURRENCY_ID cur, e.FEE_TYPE_ID ft, SUM(e.AMOUNT) amt
    FROM FINCORE.WAFAR_EARMARK e
    WHERE e.NOTES NOT LIKE N'%carried forward%'
    GROUP BY e.FISCAL_YEAR_ID, e.MOHE_QUARTER_KEY, e.STUDY_COUNTRY_IN_ID, e.CURRENCY_ID, e.FEE_TYPE_ID
), combined AS (
    SELECT f, q, c, cur, ft, SUM(amt) amt
    FROM (SELECT * FROM disbursed UNION ALL SELECT * FROM earmarked) x
    GROUP BY f, q, c, cur, ft
)
INSERT INTO FINCORE.ATTACHE_ALLOCATION
    (FISCAL_YEAR_ID, MOHE_QUARTER_KEY, STUDY_COUNTRY_IN_ID, ALLOCATION_TYPE, FEE_TYPE_ID,
     CURRENCY_ID, AMOUNT, STATUS, TRANSFER_DATE, TRANSFER_REFERENCE, NOTES)
SELECT c.f, c.q, c.c, 'REGULAR', c.ft, c.cur, c.amt, 'TRANSFERRED',
       /* The quarter's own pay date where MOHE_QUARTER records one, otherwise
          the first day of the quarter. CK_ALLOC_TRANSFER_DATED requires a date
          on a TRANSFERRED row, so this has to resolve inline -- a later UPDATE
          is too late, because the constraint fires on INSERT. */
       COALESCE(
           (SELECT MIN(mq.QUARTER_PAY_DATE) FROM MOHECORE.MOHE_QUARTER mq WHERE mq.QUARTER = c.q),
           DATEFROMPARTS(c.q / 10, ((c.q % 10) - 1) * 3 + 1, 1)),
       @MARK,
       N'مبلغ مستنتج من المصروف والوفر / inferred from what was disbursed plus what became Wafar'
FROM combined c
WHERE c.amt > 0;

PRINT 'V045 step 6: allocations        = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 7. Open the advance balances.
 *
 *    Must exist before any Wafar use: TR_WAFAR_USE_SAME_ATTACHE refuses a
 *    use with no pool behind it. WAFAR_SPENT stays 0 here and is set in
 *    step 9, once the uses are in.
 * ================================================================== */
INSERT INTO FINCORE.ATTACHE_ADVANCE_BALANCE
    (FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID, CURRENCY_ID,
     OPENING_BALANCE, TRANSFERRED, DISBURSED, RETURNED, WAFAR_EARMARKED, WAFAR_SPENT, UPDATED_AT)
SELECT k.f, k.c, k.cur,
       /* Opening cash is the Wafar carried in: physically at the attache,
          but not transferred this year. */
       ISNULL((SELECT SUM(e.AMOUNT) FROM FINCORE.WAFAR_EARMARK e
               WHERE e.FISCAL_YEAR_ID = k.f AND e.STUDY_COUNTRY_IN_ID = k.c
                 AND e.CURRENCY_ID = k.cur AND e.NOTES LIKE N'%carried forward%'), 0),
       ISNULL((SELECT SUM(a.AMOUNT) FROM FINCORE.ATTACHE_ALLOCATION a
               WHERE a.FISCAL_YEAR_ID = k.f AND a.STUDY_COUNTRY_IN_ID = k.c
                 AND a.CURRENCY_ID = k.cur AND a.STATUS = 'TRANSFERRED'), 0),
       ISNULL((SELECT SUM(l.DISBURSED_AMOUNT) FROM FINCORE.PAYMENT_BATCH b
               JOIN FINCORE.PAYMENT_BATCH_LINE l ON l.BATCH_ID = b.BATCH_ID
               WHERE b.FISCAL_YEAR_ID = k.f AND b.STUDY_COUNTRY_IN_ID = k.c
                 AND b.CURRENCY_ID = k.cur), 0),
       0,
       ISNULL((SELECT SUM(e.AMOUNT) FROM FINCORE.WAFAR_EARMARK e
               WHERE e.FISCAL_YEAR_ID = k.f AND e.STUDY_COUNTRY_IN_ID = k.c
                 AND e.CURRENCY_ID = k.cur), 0),
       0,
       SYSUTCDATETIME()
FROM (
    SELECT FISCAL_YEAR_ID f, STUDY_COUNTRY_IN_ID c, CURRENCY_ID cur FROM FINCORE.ATTACHE_ALLOCATION
    UNION
    SELECT FISCAL_YEAR_ID,   STUDY_COUNTRY_IN_ID,   CURRENCY_ID   FROM FINCORE.WAFAR_EARMARK
) k;

PRINT 'V045 step 7: advance balances   = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 8. Wafar uses -- savings spent, with permission, on someone else.
 *
 *    Only APPROVED spending counts. PENDING, REJECTED and CANCELLED rows are
 *    decisions, not payments; counting them would overstate what left.
 *
 *    Eight approved rows carry no country (operating expense, salary and the
 *    like), and WAFAR_USE requires one because a pool belongs to a specific
 *    attache. They are attributed to the single attache that holds Wafar --
 *    and only BECAUSE there is exactly one. With a second, the attribution
 *    would be a guess, so the script refuses instead.
 * ================================================================== */
DECLARE @MARK nvarchar(20) = N'DERIVED-V045';
DECLARE @soleWafarCountry int, @wafarCountries int;

SELECT @wafarCountries   = COUNT(DISTINCT STUDY_COUNTRY_IN_ID),
       @soleWafarCountry = MIN(STUDY_COUNTRY_IN_ID)
FROM FINCORE.WAFAR_EARMARK;

IF @wafarCountries > 1 AND EXISTS (SELECT 1 FROM MOHECORE.WAFAR_SPENDING
                                   WHERE STATUS = 'APPROVED' AND STUDY_COUNTRY_IN_ID IS NULL)
    THROW 50045, 'V045: approved Wafar spending has no country, and more than one attache holds Wafar. Set STUDY_COUNTRY_IN_ID on those rows first.', 1;

INSERT INTO FINCORE.WAFAR_USE
    (FISCAL_YEAR_ID, MOHE_QUARTER_KEY, STUDY_COUNTRY_IN_ID, CURRENCY_ID, AMOUNT,
     BENEFICIARY_STUDENT_ID, CATEGORY_ID, SPENDING_ID, NOTES)
SELECT fy.FISCAL_YEAR_ID,
       (SELECT mq.QUARTER FROM MOHECORE.MOHE_QUARTER mq
        WHERE mq.QUARTER = YEAR(sp.SPENDING_DATE) * 10 + DATEPART(QUARTER, sp.SPENDING_DATE)),
       ISNULL(sp.STUDY_COUNTRY_IN_ID, @soleWafarCountry),
       1,
       sp.AMOUNT, sp.STUDENT_ID, sp.CATEGORY_ID, sp.SPENDING_ID,
       @MARK + N' — from WAFAR_SPENDING'
             + CASE WHEN sp.STUDY_COUNTRY_IN_ID IS NULL
                    THEN N'; no country on the source row, attributed to the only attaché holding Wafar'
                    ELSE N'' END
FROM MOHECORE.WAFAR_SPENDING sp
JOIN FINCORE.FISCAL_YEAR fy ON fy.YEAR_NO = YEAR(sp.SPENDING_DATE)
WHERE sp.STATUS = 'APPROVED' AND sp.AMOUNT > 0;

PRINT 'V045 step 8: wafar uses         = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 9. Fold the Wafar payments into the balances.
 *
 *    Two counters move together: WAFAR_SPENT rises because the earmark is
 *    released, DISBURSED rises because the cash actually left. AVAILABLE is
 *    unchanged by the pair, which is the point.
 * ================================================================== */
UPDATE ab
SET ab.WAFAR_SPENT = u.used,
    ab.DISBURSED   = ab.DISBURSED + u.used,
    ab.UPDATED_AT  = SYSUTCDATETIME()
FROM FINCORE.ATTACHE_ADVANCE_BALANCE ab
JOIN (
    SELECT FISCAL_YEAR_ID f, STUDY_COUNTRY_IN_ID c, CURRENCY_ID cur, SUM(AMOUNT) used
    FROM FINCORE.WAFAR_USE
    GROUP BY FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID, CURRENCY_ID
) u ON u.f = ab.FISCAL_YEAR_ID AND u.c = ab.STUDY_COUNTRY_IN_ID AND u.cur = ab.CURRENCY_ID;

PRINT 'V045 step 9: balances updated with Wafar payments = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 10. Roll the quarterly totals into MOHE_QUARTER.
 *     Every BUDGET_AMOUNT has been NULL since the table was created.
 * ================================================================== */
UPDATE mq
SET mq.BUDGET_AMOUNT = x.total
FROM MOHECORE.MOHE_QUARTER mq
JOIN (
    SELECT a.MOHE_QUARTER_KEY, SUM(a.AMOUNT) AS total
    FROM FINCORE.ATTACHE_ALLOCATION a
    WHERE a.STATUS = 'TRANSFERRED'
    GROUP BY a.MOHE_QUARTER_KEY
) x ON x.MOHE_QUARTER_KEY = mq.QUARTER;

PRINT 'V045 step 10: quarters rolled up = ' + CAST(@@ROWCOUNT AS varchar(10));
GO

/* ================================================================== *
 * 11. Verification
 * ================================================================== */
PRINT '';
PRINT '================ V045 verification ================';

PRINT '--- THE CHART OF ACCOUNTS MUST STILL BE EMPTY (codes come from the manual) ---';
SELECT COUNT(*) AS coa_accounts FROM FINCORE.COA_ACCOUNT;

PRINT '';
PRINT '--- what was derived ---';
SELECT 'ATTACHE_ALLOCATION' AS [table], COUNT(*) AS rows_, SUM(AMOUNT) AS amount FROM FINCORE.ATTACHE_ALLOCATION
UNION ALL SELECT 'PAYMENT_BATCH',      COUNT(*), NULL FROM FINCORE.PAYMENT_BATCH
UNION ALL SELECT 'PAYMENT_BATCH_LINE', COUNT(*), SUM(DISBURSED_AMOUNT) FROM FINCORE.PAYMENT_BATCH_LINE
UNION ALL SELECT 'WAFAR_EARMARK',      COUNT(*), SUM(AMOUNT) FROM FINCORE.WAFAR_EARMARK
UNION ALL SELECT 'WAFAR_USE',          COUNT(*), SUM(AMOUNT) FROM FINCORE.WAFAR_USE
UNION ALL SELECT 'ADVANCE_BALANCE',    COUNT(*), SUM(AVAILABLE) FROM FINCORE.ATTACHE_ADVANCE_BALANCE;

PRINT '';
PRINT '--- by fee stream ---';
SELECT ft.FEE_TYPE_CODE, COUNT(DISTINCT b.BATCH_ID) AS batches, COUNT(l.BATCH_LINE_ID) AS lines_,
       SUM(l.ENTITLED_AMOUNT) AS entitled, SUM(l.DISBURSED_AMOUNT) AS disbursed
FROM FINCORE.PAYMENT_BATCH b
JOIN FINCORE.FEE_TYPE ft ON ft.FEE_TYPE_ID = b.FEE_TYPE_ID
LEFT JOIN FINCORE.PAYMENT_BATCH_LINE l ON l.BATCH_ID = b.BATCH_ID
GROUP BY ft.FEE_TYPE_CODE ORDER BY ft.FEE_TYPE_CODE;

PRINT '';
PRINT '--- the advance balances. AVAILABLE should be 0: everything is accounted for ---';
SELECT fy.YEAR_NO, ab.STUDY_COUNTRY_IN_ID AS attache, ab.CURRENCY_ID AS cur,
       ab.OPENING_BALANCE AS opening, ab.TRANSFERRED, ab.DISBURSED,
       ab.WAFAR_EARMARKED AS earmarked, ab.WAFAR_SPENT AS spent,
       ab.WAFAR_BALANCE AS wafar_bal, ab.AVAILABLE
FROM FINCORE.ATTACHE_ADVANCE_BALANCE ab
JOIN FINCORE.FISCAL_YEAR fy ON fy.FISCAL_YEAR_ID = ab.FISCAL_YEAR_ID
ORDER BY fy.YEAR_NO, ab.STUDY_COUNTRY_IN_ID, ab.CURRENCY_ID;

PRINT '';
PRINT '--- RECONCILIATION: every delta must be 0 ---';
SELECT COUNT(*) AS balances_that_do_not_reconcile
FROM FINCORE.V_ADVANCE_RECONCILIATION
WHERE TRANSFERRED_DELTA <> 0 OR DISBURSED_DELTA <> 0 OR EARMARKED_DELTA <> 0 OR SPENT_DELTA <> 0;

PRINT '';
PRINT '--- MOHE_QUARTER, which held NULL in every row until now ---';
SELECT QUARTER, QUARTER_YEAR, QUARTER_NO, BUDGET_AMOUNT
FROM MOHECORE.MOHE_QUARTER WHERE BUDGET_AMOUNT IS NOT NULL ORDER BY QUARTER;

PRINT '';
PRINT '--- SOURCE ROWS THAT COULD NOT BE MAPPED, and why ---';
SELECT 'university fee: no payment date recorded' AS reason, COUNT(*) AS rows_
FROM MOHECORE.STUDY_UNIVERSITY_FEE WHERE STUDY_UNIVERSITY_FEE_PAY_DATE IS NULL
UNION ALL
SELECT 'university fee: paid in a quarter MOHE_QUARTER does not have', COUNT(*)
FROM MOHECORE.STUDY_UNIVERSITY_FEE u WHERE u.STUDY_UNIVERSITY_FEE_PAY_DATE IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER mq WHERE mq.QUARTER =
        YEAR(u.STUDY_UNIVERSITY_FEE_PAY_DATE)*10 + DATEPART(QUARTER, u.STUDY_UNIVERSITY_FEE_PAY_DATE))
UNION ALL
SELECT 'books allowance: no quarter recorded', COUNT(*)
FROM MOHECORE.STUDENT_BOOKS_ALLOWANCE WHERE MOHE_QUARTER_ID IS NULL
UNION ALL
SELECT 'wafar spending: not APPROVED, so a decision rather than a payment', COUNT(*)
FROM MOHECORE.WAFAR_SPENDING WHERE STATUS <> 'APPROVED'
UNION ALL
SELECT 'wafar spending: APPROVED but no country on the row (attributed)', COUNT(*)
FROM MOHECORE.WAFAR_SPENDING WHERE STATUS = 'APPROVED' AND STUDY_COUNTRY_IN_ID IS NULL;

PRINT '';
PRINT '*** TWO THINGS ABOUT THE SOURCE DATA, NOT ABOUT THIS SCRIPT ***';
PRINT '';
PRINT '1. STUDENT_FINANCE_ARCH is a replicated snapshot, not real history.';
PRINT '   Every student carries the identical amount in all four quarters of';
PRINT '   2024, and every quarter totals 1,574,580 with no Wafar at all. The';
PRINT '   four derived stipend quarters therefore look identical, which is';
PRINT '   faithful to the data and probably not to what actually happened.';
PRINT '';
PRINT '2. The live table and the archive disagree about 2024 Q2:';
PRINT '   live 1,571,220 with 37,160 Wafar, archive 1,574,580 with none.';
PRINT '   The live row was used, because it carries the Wafar detail.';
PRINT '';
PRINT '   Both are worth resolving before these figures are reported anywhere.';
PRINT '=============== end V045 verification =============';
GO
