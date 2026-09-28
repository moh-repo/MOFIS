/*
    V148 — finance integrity fixes (system test report FIN-01, FIN-03, FIN-16).

    Why
    ---
    FIN-01  Nothing refused a negative Wafar spend. requireCovers let an amount of
            zero or less through, the DTO had no constraint and the table no CHECK,
            so a spend of -5000 was recorded, approved, and raised the attaché's
            Wafar balance by 5000. The API now answers 400; this is the backstop for
            every other route that writes the table.
    FIN-03  Paying a batch, transferring an allocation and posting to an advance
            were read-modify-writes with no lock. Two "mark paid" clicks at once both
            passed the APPROVED check and the advance was debited twice. The three
            entities now carry a JPA @Version, which needs a column to live in.
    FIN-16  Cancelling an open batch wrote APPROVED_BY / APPROVED_AT, so a cancelled
            batch read as approved by whoever withdrew it.

    What
    ----
    1. ROW_VERSION bigint NOT NULL DEFAULT 0 on FINCORE.PAYMENT_BATCH,
       FINCORE.ATTACHE_ALLOCATION and FINCORE.ATTACHE_ADVANCE_BALANCE. Existing rows
       start at 0; Hibernate increments it on every update and refuses a stale one
       (answered 409 by GlobalExceptionHandler). Raw SQL writers (V045, reports) do
       not need to change: the default fills inserts, and an update that does not
       touch the column is simply not version-checked.
    2. CANCELLED_BY int NULL and CANCELLED_AT datetime2(3) NULL on
       FINCORE.PAYMENT_BATCH. Batches already CANCELLED have their APPROVED_BY /
       APPROVED_AT moved there (they were the canceller, not an approver), and the
       approval columns cleared. CK_BATCH_DISBURSE_ORDER only concerns DISBURSED and
       CLOSED rows, so clearing them on CANCELLED rows cannot violate it.
    3. CK_WAFAR_SPENDING_AMOUNT_POSITIVE: CHECK (AMOUNT > 0) on MOHECORE.WAFAR_SPENDING.
       Existing rows are not changed: a spend is money that was (or was asked to be)
       paid, and rewriting its amount would falsify the record. So:
         - no row has AMOUNT <= 0 (the expected case): the constraint is added
           WITH CHECK and is trusted;
         - otherwise each such row is printed (id, amount, status) for the ministry
           to correct through the Wafar correction path, and the constraint is added
           WITH NOCHECK: every new row and every edit is checked, the old rows are
           left as they are. Note that SQL Server checks the whole row on any UPDATE,
           so such a row cannot be edited (even cancelled) until its amount is
           corrected. Once they are, re-run
               ALTER TABLE MOHECORE.WAFAR_SPENDING WITH CHECK CHECK CONSTRAINT CK_WAFAR_SPENDING_AMOUNT_POSITIVE;
           to make it trusted.

    Re-runnable: every step is guarded by COL_LENGTH / sys.check_constraints, and
    step 2's data move only touches CANCELLED rows whose CANCELLED_BY is still NULL
    and that still carry an approver.

    Rollback
    --------
        ALTER TABLE MOHECORE.WAFAR_SPENDING DROP CONSTRAINT CK_WAFAR_SPENDING_AMOUNT_POSITIVE;
        UPDATE FINCORE.PAYMENT_BATCH SET APPROVED_BY = CANCELLED_BY, APPROVED_AT = CANCELLED_AT
         WHERE STATUS = 'CANCELLED' AND CANCELLED_BY IS NOT NULL;
        ALTER TABLE FINCORE.PAYMENT_BATCH DROP COLUMN CANCELLED_BY, CANCELLED_AT;
        ALTER TABLE FINCORE.PAYMENT_BATCH DROP CONSTRAINT DF_PAYMENT_BATCH_ROW_VERSION;
        ALTER TABLE FINCORE.PAYMENT_BATCH DROP COLUMN ROW_VERSION;
        ALTER TABLE FINCORE.ATTACHE_ALLOCATION DROP CONSTRAINT DF_ATTACHE_ALLOCATION_ROW_VERSION;
        ALTER TABLE FINCORE.ATTACHE_ALLOCATION DROP COLUMN ROW_VERSION;
        ALTER TABLE FINCORE.ATTACHE_ADVANCE_BALANCE DROP CONSTRAINT DF_ATTACHE_ADVANCE_BALANCE_ROW_VERSION;
        ALTER TABLE FINCORE.ATTACHE_ADVANCE_BALANCE DROP COLUMN ROW_VERSION;
    Roll the application back first: the entities map these columns.

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---------------------------------------------------------------------------
   1. Row versions (FIN-03).
   ------------------------------------------------------------------------ */

IF COL_LENGTH(N'FINCORE.PAYMENT_BATCH', N'ROW_VERSION') IS NULL
BEGIN
    ALTER TABLE FINCORE.PAYMENT_BATCH
        ADD ROW_VERSION bigint NOT NULL CONSTRAINT DF_PAYMENT_BATCH_ROW_VERSION DEFAULT (0);
    PRINT 'V148: FINCORE.PAYMENT_BATCH.ROW_VERSION added';
END
GO

IF COL_LENGTH(N'FINCORE.ATTACHE_ALLOCATION', N'ROW_VERSION') IS NULL
BEGIN
    ALTER TABLE FINCORE.ATTACHE_ALLOCATION
        ADD ROW_VERSION bigint NOT NULL CONSTRAINT DF_ATTACHE_ALLOCATION_ROW_VERSION DEFAULT (0);
    PRINT 'V148: FINCORE.ATTACHE_ALLOCATION.ROW_VERSION added';
END
GO

IF COL_LENGTH(N'FINCORE.ATTACHE_ADVANCE_BALANCE', N'ROW_VERSION') IS NULL
BEGIN
    ALTER TABLE FINCORE.ATTACHE_ADVANCE_BALANCE
        ADD ROW_VERSION bigint NOT NULL CONSTRAINT DF_ATTACHE_ADVANCE_BALANCE_ROW_VERSION DEFAULT (0);
    PRINT 'V148: FINCORE.ATTACHE_ADVANCE_BALANCE.ROW_VERSION added';
END
GO

/* ---------------------------------------------------------------------------
   2. Who cancelled a batch (FIN-16).
   ------------------------------------------------------------------------ */

IF COL_LENGTH(N'FINCORE.PAYMENT_BATCH', N'CANCELLED_BY') IS NULL
    ALTER TABLE FINCORE.PAYMENT_BATCH ADD CANCELLED_BY int NULL;
IF COL_LENGTH(N'FINCORE.PAYMENT_BATCH', N'CANCELLED_AT') IS NULL
    ALTER TABLE FINCORE.PAYMENT_BATCH ADD CANCELLED_AT datetime2(3) NULL;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
-- The batch table sits under the tenant block predicate (V042); the migration acts for every attaché.
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @moved int;
UPDATE FINCORE.PAYMENT_BATCH
   SET CANCELLED_BY = APPROVED_BY,
       CANCELLED_AT = APPROVED_AT,
       APPROVED_BY  = NULL,
       APPROVED_AT  = NULL
 WHERE STATUS = 'CANCELLED'
   AND CANCELLED_BY IS NULL
   AND (APPROVED_BY IS NOT NULL OR APPROVED_AT IS NOT NULL);
SET @moved = @@ROWCOUNT;
PRINT CONCAT('V148: ', @moved, ' cancelled batch(es) had their canceller moved out of APPROVED_BY');
GO

/* ---------------------------------------------------------------------------
   3. A Wafar spend is strictly positive (FIN-01).
   ------------------------------------------------------------------------ */

SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                WHERE name = N'CK_WAFAR_SPENDING_AMOUNT_POSITIVE'
                  AND parent_object_id = OBJECT_ID(N'MOHECORE.WAFAR_SPENDING'))
BEGIN
    DECLARE @bad NVARCHAR(MAX);
    SELECT @bad = COALESCE(@bad + N', ', N'')
                  + CONCAT(N'spend ', s.SPENDING_ID, N' (', s.AMOUNT, N', ', s.STATUS, N')')
      FROM MOHECORE.WAFAR_SPENDING s
     WHERE s.AMOUNT <= 0;

    IF @bad IS NULL
    BEGIN
        ALTER TABLE MOHECORE.WAFAR_SPENDING WITH CHECK
            ADD CONSTRAINT CK_WAFAR_SPENDING_AMOUNT_POSITIVE CHECK (AMOUNT > 0);
        PRINT 'V148: CK_WAFAR_SPENDING_AMOUNT_POSITIVE added (trusted)';
    END
    ELSE
    BEGIN
        PRINT CONCAT('V148: existing Wafar spends with an amount of 0 or less, left unchanged for correction: ', @bad);
        ALTER TABLE MOHECORE.WAFAR_SPENDING WITH NOCHECK
            ADD CONSTRAINT CK_WAFAR_SPENDING_AMOUNT_POSITIVE CHECK (AMOUNT > 0);
        PRINT 'V148: CK_WAFAR_SPENDING_AMOUNT_POSITIVE added WITH NOCHECK (new and edited rows only)';
    END
END
GO

/* ---- checks ------------------------------------------------------------------------ */

IF COL_LENGTH(N'FINCORE.PAYMENT_BATCH', N'ROW_VERSION') IS NULL
   OR COL_LENGTH(N'FINCORE.ATTACHE_ALLOCATION', N'ROW_VERSION') IS NULL
   OR COL_LENGTH(N'FINCORE.ATTACHE_ADVANCE_BALANCE', N'ROW_VERSION') IS NULL
   OR COL_LENGTH(N'FINCORE.PAYMENT_BATCH', N'CANCELLED_BY') IS NULL
   OR COL_LENGTH(N'FINCORE.PAYMENT_BATCH', N'CANCELLED_AT') IS NULL
   OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_WAFAR_SPENDING_AMOUNT_POSITIVE')
    THROW 51480, 'V148: a finance integrity column or constraint is missing', 1;
GO
