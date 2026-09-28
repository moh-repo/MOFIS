/*
================================================================================
V055 — STUDY_FINANCE_BANK_BRANCH: a default "Main Branch" for every bank that
        has none
================================================================================

Why
---
Only Egypt had any bank branches (12 rows); the other configured countries had
banks but no branches, and every finance workflow that needs a branch was
unusable there. This inserts one branch — الفرع الرئيسي / Main Branch — for
each bank that currently has no branch at all, in whatever country the bank
belongs to. It touches Egyptian banks too if any of them has no branch; the
rule is "bank without branch", not "non-Egypt".

Numbering
---------
This script first existed as a second V036, colliding with
V036__student_session_country.sql. It is renumbered here; nothing else changed
in what it does.

Ids
---
STUDY_FINANCE_BANK_BRANCH_ID is not an IDENTITY column (StudyFinanceBankBranch
assigns MAX+1 in the service). The same MAX+1 is used here, so the read and
the insert are one transaction under a table lock — otherwise a concurrent
insert from the application could take the same id.

Re-run safe: guarded by NOT EXISTS, a second run inserts nothing.
================================================================================
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRANSACTION;

DECLARE @NextBranchId INT;
SELECT @NextBranchId = ISNULL(MAX(STUDY_FINANCE_BANK_BRANCH_ID), 0) + 1
  FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH WITH (TABLOCKX, HOLDLOCK);

INSERT INTO MOHECORE.STUDY_FINANCE_BANK_BRANCH (
    STUDY_FINANCE_BANK_BRANCH_ID,
    STUDY_FINANCE_BANK_BRANCH_NAME,
    STUDY_FINANCE_BANKE_ID,
    STUDY_COUNTRY_IN_ID,
    STUDY_FINANCE_BANK_BRANCH_MIDATE,
    STUDY_FINANCE_BANK_BRANCH_NAME_EN,
    IS_ACTIVE
)
SELECT
    @NextBranchId + ROW_NUMBER() OVER (ORDER BY b.STUDY_FINANCE_BANKE_ID) - 1,
    N'الفرع الرئيسي',
    b.STUDY_FINANCE_BANKE_ID,
    b.STUDY_COUNTRY_IN_ID,
    GETUTCDATE(),
    N'Main Branch',
    1
FROM MOHECORE.STUDY_FINANCE_BANK b
WHERE NOT EXISTS (
    SELECT 1
      FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH bb
     WHERE bb.STUDY_FINANCE_BANKE_ID = b.STUDY_FINANCE_BANKE_ID
);

PRINT CONCAT('Inserted ', @@ROWCOUNT, ' default branch(es).');

COMMIT TRANSACTION;
GO

/* ── Verification: branches per country, and banks still without one ───────── */
SELECT b.STUDY_COUNTRY_IN_ID AS country_id,
       COUNT(DISTINCT b.STUDY_FINANCE_BANKE_ID) AS banks,
       COUNT(bb.STUDY_FINANCE_BANK_BRANCH_ID)   AS branches
  FROM MOHECORE.STUDY_FINANCE_BANK b
  LEFT JOIN MOHECORE.STUDY_FINANCE_BANK_BRANCH bb ON bb.STUDY_FINANCE_BANKE_ID = b.STUDY_FINANCE_BANKE_ID
 GROUP BY b.STUDY_COUNTRY_IN_ID
 ORDER BY b.STUDY_COUNTRY_IN_ID;

SELECT COUNT(*) AS banks_still_without_branch
  FROM MOHECORE.STUDY_FINANCE_BANK b
 WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH bb
                    WHERE bb.STUDY_FINANCE_BANKE_ID = b.STUDY_FINANCE_BANKE_ID);
GO
