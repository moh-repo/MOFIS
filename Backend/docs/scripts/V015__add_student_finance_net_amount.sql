/*
 * Add NET_AMOUNT to STUDENT_FINANCE and STUDENT_FINANCE_ARCH.
 *
 * Wafar is a deduction from a student's disbursement, but every screen and every
 * report read STUDENT_FIN_RECIEVED_AMOUNT, so the figures were gross of wafar.
 * NET_AMOUNT is what the student actually nets.
 *
 * It is a PERSISTED computed column, not a plain writable one: the amount and the
 * wafar are written from six different code paths (the approval executor, the
 * finance service create/update/merge, the native seed-from-STUDENT_DATA insert,
 * the draft mapper, the quick-edit service, and the quarter-close archive mapper).
 * A hand-maintained column would have to be recomputed correctly at every one of
 * them, forever. PERSISTED is still a real, physically stored, indexable column —
 * report SQL sums it like any other — but SQL Server keeps it in step, and
 * existing rows are correct the moment this script runs, with no backfill.
 *
 * The trade-off: net can never be set independently of amount - wafar. If that is
 * ever needed, this becomes an INT NULL column plus a recompute in those six paths.
 *
 * Idempotent — safe to re-run.
 */

IF COL_LENGTH('MOHECORE.STUDENT_FINANCE', 'NET_AMOUNT') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_FINANCE
        ADD NET_AMOUNT AS (ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0) - ISNULL(WAFAR_AMOUNT, 0)) PERSISTED;
END
GO

/*
 * The archive table carries its own copy of the amount and wafar (written by
 * QuarterArchiveMapper on quarter close), so it needs the same column rather than
 * a join back to the live table — archived rows must keep the net they had when
 * they were archived. Guarded on WAFAR_AMOUNT because that column was itself a
 * later gap-fill on this table.
 */
IF COL_LENGTH('MOHECORE.STUDENT_FINANCE_ARCH', 'NET_AMOUNT') IS NULL
   AND COL_LENGTH('MOHECORE.STUDENT_FINANCE_ARCH', 'WAFAR_AMOUNT') IS NOT NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_FINANCE_ARCH
        ADD NET_AMOUNT AS (ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0) - ISNULL(WAFAR_AMOUNT, 0)) PERSISTED;
END
GO

-- ── Verification ─────────────────────────────────────────────────────────────
-- Both rows should report is_persisted = 1.
SELECT  OBJECT_SCHEMA_NAME(c.object_id) AS [schema],
        OBJECT_NAME(c.object_id)        AS [table],
        c.name                          AS [column],
        c.definition,
        c.is_persisted
FROM    sys.computed_columns c
WHERE   c.name = 'NET_AMOUNT'
        AND OBJECT_NAME(c.object_id) IN ('STUDENT_FINANCE', 'STUDENT_FINANCE_ARCH');
GO

-- Must return 0 on both tables.
SELECT  'STUDENT_FINANCE' AS [table], COUNT(*) AS mismatched_rows
FROM    MOHECORE.STUDENT_FINANCE
WHERE   NET_AMOUNT <> ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0) - ISNULL(WAFAR_AMOUNT, 0)
UNION ALL
SELECT  'STUDENT_FINANCE_ARCH', COUNT(*)
FROM    MOHECORE.STUDENT_FINANCE_ARCH
WHERE   NET_AMOUNT <> ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0) - ISNULL(WAFAR_AMOUNT, 0);
GO

-- Spot-check the rows that actually have a wafar.
SELECT TOP 20 STUDENT_ID, STUDENT_FIN_RECIEVED_AMOUNT, WAFAR_AMOUNT, NET_AMOUNT
FROM   MOHECORE.STUDENT_FINANCE
WHERE  ISNULL(WAFAR_AMOUNT, 0) > 0
ORDER  BY STUDENT_ID;
GO
