/* ============================================================================
   V081 — The payment voucher's amount column, and its التفقيط line.

   Why
   ---
   نموذج رقم ١١ is now shown as the server's own PDF rather than as an HTML
   replica of it, and two things about its amount column did not survive that
   change:

     LBL_AMOUNT_CUR  was "المبلغ" / "ريال" on two lines — the column head with
                     the currency typed into it. A currency is not a label: the
                     ministry's own is STUDY_CURRENCY_IN.IS_FUNCTIONAL, which
                     V039 established and a filtered unique index keeps to one
                     row. The head is the word alone now and the voucher reads
                     the currency from that row, so it follows the books.

     LBL_AMOUNT_WORDS  new. The caption in front of the total written out —
                     التفقيط, the safeguard that makes a digit altered after
                     signing disagree with the words beside it. AmountInWords
                     produces the text; this is only its heading.

   What
   ----
   MERGE on CODE, in the shape V077 and V079 use, so SeededReportLabels reads
   this script as part of the golden label set and the "script and database
   agree" test covers these two rows like every other.

   Rollback: restore LBL_AMOUNT_CUR to N'المبلغ' + NCHAR(10) + N'ريال' /
   'Amount' + NCHAR(10) + 'Riyal' and DELETE the LBL_AMOUNT_WORDS row.
   Idempotent. Run with sqlcmd -I -f 65001 -b after V079.
   ============================================================================ */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF COL_LENGTH('MOHECORE.LU_REPORT_LABEL', 'IS_SYSTEM') IS NULL
    THROW 50081, 'V081: LU_REPORT_LABEL lacks the V072 columns — run V072, V076, V077 and V079 first.', 1;
GO

DECLARE @done TABLE (act NVARCHAR(10));

MERGE MOHECORE.LU_REPORT_LABEL AS tgt
USING (VALUES
    (N'LBL_AMOUNT_WORDS', N'التفقيط', N'Amount in words', 308)
) AS src (CODE, NAME_AR, NAME_EN, SORT_ORDER)
    ON tgt.CODE = src.CODE
WHEN MATCHED AND (tgt.NAME_AR <> src.NAME_AR OR tgt.NAME_EN <> src.NAME_EN)
    THEN UPDATE SET tgt.NAME_AR = src.NAME_AR, tgt.NAME_EN = src.NAME_EN, tgt.IS_SYSTEM = 1
WHEN NOT MATCHED BY TARGET
    THEN INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED, IS_SYSTEM)
    VALUES (src.CODE, src.NAME_AR, src.NAME_EN, src.SORT_ORDER, 0, 1)
OUTPUT $action INTO @done;

DECLARE @inserted INT, @updated INT;
SELECT @inserted = COUNT(*) FROM @done WHERE act = N'INSERT';
SELECT @updated  = COUNT(*) FROM @done WHERE act = N'UPDATE';
PRINT 'V081: LBL_AMOUNT_WORDS inserted = ' + CAST(@inserted AS VARCHAR(10))
    + ', refreshed = ' + CAST(@updated AS VARCHAR(10))
    + ' (first run 1 / 0, re-run 0 / 0)';
GO

/*
 * The correction for databases seeded before V077 was amended: LBL_AMOUNT_CUR
 * carried the currency in its own text, on a second line. Scoped to exactly the
 * two values this project has written, so an administrator who has since renamed
 * the column head keeps their wording.
 */
UPDATE MOHECORE.LU_REPORT_LABEL
   SET NAME_AR = N'المبلغ', NAME_EN = 'Amount'
 WHERE CODE = N'LBL_AMOUNT_CUR'
   AND NAME_AR = N'المبلغ' + NCHAR(10) + N'ريال'
   AND NAME_EN = N'Amount' + NCHAR(10) + N'Riyal';
PRINT 'V081: LBL_AMOUNT_CUR corrected on ' + CAST(@@ROWCOUNT AS VARCHAR(10)) + ' row(s).';
GO

-- Verification ----------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM MOHECORE.LU_REPORT_LABEL
               WHERE CODE = N'LBL_AMOUNT_WORDS' AND IS_DELETED = 0)
    THROW 50081, 'V081: LBL_AMOUNT_WORDS is missing after the merge.', 1;

IF EXISTS (SELECT 1 FROM MOHECORE.LU_REPORT_LABEL
           WHERE CODE = N'LBL_AMOUNT_CUR' AND NAME_EN <> 'Amount')
    THROW 50081, 'V081: LBL_AMOUNT_CUR still carries a currency in its text.', 1;

/*
 * The functional currency is what the voucher is denominated in. V039 set it on
 * YER; this only reports it, because choosing the ministry's currency is not a
 * decision a report-label migration should make.
 */
IF NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_CURRENCY_IN WHERE IS_FUNCTIONAL = 1)
    PRINT 'V081: WARNING - no STUDY_CURRENCY_IN row is flagged IS_FUNCTIONAL; the voucher will print no currency and no التفقيط.';
ELSE
    PRINT 'V081: functional currency present.';
GO
