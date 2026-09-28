/*
    V147 — bank branches, study amounts and study years get the foreign keys they
    always implied (round-6 QA R6-04).

    Why
    ---
    None of these columns was constrained (read-only check of sys.foreign_keys), so:
      - a bank could be deleted while a branch used it (204; the branch kept the
        missing bank id);
      - a branch could name bank 999999;
      - a study amount or a study-years rule could name a currency, degree or major
        that does not exist.
    Branch 1005 (Jordan, created 26 Aug) on the development database already names
    bank 1005, which does not exist, so the Branches screen shows its bank as the
    bare number "1005". Jordan's banks are 1003 and 1004.

    What
    ----
    1. A branch whose bank does not exist is left with no bank (NULL), after its id
       and the old value are printed. The number named nothing; NULL is what "no
       bank" means to a foreign key, and the column's default 0 is caught here too.
       The attaché picks the right bank on the Branches screen.
    2. Nine keys, each created only if its data supports it (V129's rule, decision
       Q-11c: orphan check first, and an environment whose data does not support a
       key gets a printed diagnosis rather than a failed migration):
         STUDY_FINANCE_BANK_BRANCH.STUDY_FINANCE_BANKE_ID -> STUDY_FINANCE_BANK
         STUDY_FINANCE_BANK_BRANCH.STUDY_COUNTRY_IN_ID    -> STUDY_COUNTRY_IN
         STUDY_AMOUNT_LOOKUP.STUDENT_DEGREE_ID            -> STUDENT_DEGREE
         STUDY_AMOUNT_LOOKUP.STUDENT_AGENCY_TYPE_ID       -> STUDENT_AGENCY_TYPE
         STUDY_AMOUNT_LOOKUP.CURRENCY_ID                  -> STUDY_CURRENCY_IN
         STUDY_AMOUNT_LOOKUP.STUDY_COUNTRY_IN_ID          -> STUDY_COUNTRY_IN
         STUDY_YEARS_LOOKUP.STUDENT_DEGREE_ID             -> STUDENT_DEGREE
         STUDY_YEARS_LOOKUP.STUDENT_MAJOR_ID              -> STUDENT_MAJOR
         STUDY_YEARS_LOOKUP.STUDY_COUNTRY_IN_ID           -> STUDY_COUNTRY_IN
       The study-years major is STUDENT_MAJOR, not STUDY_MAJOR_IN: the screen fills
       it from the student-major list, and on the development database (read-only,
       28 Sep 2026) all 206 rows match STUDENT_MAJOR while 92 match no STUDY_MAJOR_IN.
       On that database every key but the branch -> bank one has no orphans, and that
       one has only branch 1005, which step 1 clears.

    Re-runnable: step 1 finds nothing the second time, and step 2 skips a key that
    already exists.

    What this does NOT do
    ---------------------
    It changes no study amount or study-years row. Those are entitlement figures; a
    row naming something that does not exist is reported and its key skipped, for the
    ministry to correct.

    It does not cascade. A delete that would orphan a row now fails at the database.
    StudyFinanceBankService refuses deleting a bank with branches before it gets
    there (409 naming how many), and StudyFinanceBankBranchService refuses a branch
    naming a bank that does not exist (400). Anything else is answered by
    GlobalExceptionHandler's SQL Server 547 case, in the reader's language: "still
    used by other records" on a delete, "refers to something that does not exist"
    on a create or an edit — both 409.

    Rollback
    --------
        ALTER TABLE MOHECORE.STUDY_FINANCE_BANK_BRANCH DROP CONSTRAINT FK_STUDY_FINANCE_BANK_BRANCH_BANK;
        ALTER TABLE MOHECORE.STUDY_FINANCE_BANK_BRANCH DROP CONSTRAINT FK_STUDY_FINANCE_BANK_BRANCH_COUNTRY;
        ALTER TABLE MOHECORE.STUDY_AMOUNT_LOOKUP DROP CONSTRAINT FK_STUDY_AMOUNT_LOOKUP_DEGREE;
        ALTER TABLE MOHECORE.STUDY_AMOUNT_LOOKUP DROP CONSTRAINT FK_STUDY_AMOUNT_LOOKUP_AGENCY_TYPE;
        ALTER TABLE MOHECORE.STUDY_AMOUNT_LOOKUP DROP CONSTRAINT FK_STUDY_AMOUNT_LOOKUP_CURRENCY;
        ALTER TABLE MOHECORE.STUDY_AMOUNT_LOOKUP DROP CONSTRAINT FK_STUDY_AMOUNT_LOOKUP_COUNTRY;
        ALTER TABLE MOHECORE.STUDY_YEARS_LOOKUP DROP CONSTRAINT FK_STUDY_YEARS_LOOKUP_DEGREE;
        ALTER TABLE MOHECORE.STUDY_YEARS_LOOKUP DROP CONSTRAINT FK_STUDY_YEARS_LOOKUP_MAJOR;
        ALTER TABLE MOHECORE.STUDY_YEARS_LOOKUP DROP CONSTRAINT FK_STUDY_YEARS_LOOKUP_COUNTRY;
    and, per branch step 1 printed, UPDATE its STUDY_FINANCE_BANKE_ID back.
*/
SET NOCOUNT ON;

/* ---------------------------------------------------------------------------
   1. A branch whose bank does not exist has no bank.
   ------------------------------------------------------------------------ */

DECLARE @lost NVARCHAR(MAX);
SELECT @lost = COALESCE(@lost + N', ', N'')
               + CONCAT(N'branch ', b.STUDY_FINANCE_BANK_BRANCH_ID, N' (bank ', b.STUDY_FINANCE_BANKE_ID, N')')
  FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH b
 WHERE b.STUDY_FINANCE_BANKE_ID IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_FINANCE_BANK k
                    WHERE k.STUDY_FINANCE_BANKE_ID = b.STUDY_FINANCE_BANKE_ID)
 ORDER BY b.STUDY_FINANCE_BANK_BRANCH_ID;

UPDATE b SET b.STUDY_FINANCE_BANKE_ID = NULL
  FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH b
 WHERE b.STUDY_FINANCE_BANKE_ID IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_FINANCE_BANK k
                    WHERE k.STUDY_FINANCE_BANKE_ID = b.STUDY_FINANCE_BANKE_ID);
PRINT CONCAT('V147: branches naming a bank that does not exist, now with no bank: ', @@ROWCOUNT,
             CASE WHEN @lost IS NULL THEN '' ELSE CONCAT(' — ', @lost) END);
GO

/* ---------------------------------------------------------------------------
   2. The keys. Each one: already there? data clean? then create.
   ------------------------------------------------------------------------ */

DECLARE @keys TABLE (
    Ord       INT PRIMARY KEY,
    Name      SYSNAME NOT NULL,
    Child     SYSNAME NOT NULL,
    ChildCol  SYSNAME NOT NULL,
    Parent    SYSNAME NOT NULL,
    ParentCol SYSNAME NOT NULL
);
INSERT INTO @keys (Ord, Name, Child, ChildCol, Parent, ParentCol) VALUES
    (1, N'FK_STUDY_FINANCE_BANK_BRANCH_BANK',    N'STUDY_FINANCE_BANK_BRANCH', N'STUDY_FINANCE_BANKE_ID', N'STUDY_FINANCE_BANK',  N'STUDY_FINANCE_BANKE_ID'),
    (2, N'FK_STUDY_FINANCE_BANK_BRANCH_COUNTRY', N'STUDY_FINANCE_BANK_BRANCH', N'STUDY_COUNTRY_IN_ID',    N'STUDY_COUNTRY_IN',    N'STUDY_COUNTRY_IN_ID'),
    (3, N'FK_STUDY_AMOUNT_LOOKUP_DEGREE',        N'STUDY_AMOUNT_LOOKUP',       N'STUDENT_DEGREE_ID',      N'STUDENT_DEGREE',      N'STUDENT_DEGREE_ID'),
    (4, N'FK_STUDY_AMOUNT_LOOKUP_AGENCY_TYPE',   N'STUDY_AMOUNT_LOOKUP',       N'STUDENT_AGENCY_TYPE_ID', N'STUDENT_AGENCY_TYPE', N'STUDENT_AGENCY_TYPE_ID'),
    (5, N'FK_STUDY_AMOUNT_LOOKUP_CURRENCY',      N'STUDY_AMOUNT_LOOKUP',       N'CURRENCY_ID',            N'STUDY_CURRENCY_IN',   N'STUDY_CURRENCY_IN_ID'),
    (6, N'FK_STUDY_AMOUNT_LOOKUP_COUNTRY',       N'STUDY_AMOUNT_LOOKUP',       N'STUDY_COUNTRY_IN_ID',    N'STUDY_COUNTRY_IN',    N'STUDY_COUNTRY_IN_ID'),
    (7, N'FK_STUDY_YEARS_LOOKUP_DEGREE',         N'STUDY_YEARS_LOOKUP',        N'STUDENT_DEGREE_ID',      N'STUDENT_DEGREE',      N'STUDENT_DEGREE_ID'),
    (8, N'FK_STUDY_YEARS_LOOKUP_MAJOR',          N'STUDY_YEARS_LOOKUP',        N'STUDENT_MAJOR_ID',       N'STUDENT_MAJOR',       N'STUDENT_MAJOR_ID'),
    (9, N'FK_STUDY_YEARS_LOOKUP_COUNTRY',        N'STUDY_YEARS_LOOKUP',        N'STUDY_COUNTRY_IN_ID',    N'STUDY_COUNTRY_IN',    N'STUDY_COUNTRY_IN_ID');

DECLARE @i INT = 1, @n INT = (SELECT COUNT(*) FROM @keys);
DECLARE @name SYSNAME, @child SYSNAME, @childCol SYSNAME, @parent SYSNAME, @parentCol SYSNAME;
DECLARE @sql NVARCHAR(MAX), @orphans INT;

WHILE @i <= @n
BEGIN
    SELECT @name = Name, @child = Child, @childCol = ChildCol, @parent = Parent, @parentCol = ParentCol
      FROM @keys WHERE Ord = @i;

    IF EXISTS (SELECT 1 FROM sys.foreign_keys
                WHERE name = @name AND parent_object_id = OBJECT_ID(N'MOHECORE.' + @child))
        PRINT CONCAT('V147: ', @name, ' already present.');
    ELSE
    BEGIN
        SET @sql = N'SELECT @orphans = COUNT(*) FROM MOHECORE.' + QUOTENAME(@child) + N' c'
                 + N' WHERE c.' + QUOTENAME(@childCol) + N' IS NOT NULL'
                 + N' AND NOT EXISTS (SELECT 1 FROM MOHECORE.' + QUOTENAME(@parent) + N' p'
                 + N' WHERE p.' + QUOTENAME(@parentCol) + N' = c.' + QUOTENAME(@childCol) + N')';
        EXEC sp_executesql @sql, N'@orphans INT OUTPUT', @orphans = @orphans OUTPUT;

        IF @orphans > 0
            PRINT CONCAT('V147: *** Skipping ', @name, ': ', @orphans, ' row(s) of ', @child, ' name a ',
                         @parent, ' that does not exist (', @childCol, '). Correct them, then add the key by hand.');
        ELSE
        BEGIN
            SET @sql = N'ALTER TABLE MOHECORE.' + QUOTENAME(@child) + N' WITH CHECK'
                     + N' ADD CONSTRAINT ' + QUOTENAME(@name)
                     + N' FOREIGN KEY (' + QUOTENAME(@childCol) + N')'
                     + N' REFERENCES MOHECORE.' + QUOTENAME(@parent) + N' (' + QUOTENAME(@parentCol) + N')';
            EXEC sp_executesql @sql;
            PRINT CONCAT('V147: ', @name, ' created.');
        END
    END

    SET @i += 1;
END
GO
