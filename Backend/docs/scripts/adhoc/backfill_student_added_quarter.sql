/*
    WD-08 — STUDENT_ADDED_QUARTER is 0 on 938 of 941 students.

    An adhoc script, not a migration: this corrects records, and record ids
    differ between environments. Run it on dev, read the counts, then run the
    same file on production.

    Why 20253 and not something derived
    -----------------------------------
    The plan's first answer was "take it from the student's first archive
    snapshot". That is not possible: all 941 students do have snapshots — 1,882
    rows in STUDENT_DATA_ARCH — but the archive was taken from this same data
    and inherited the same blank, so **0 of the 938 are resolvable from it**.

    Two real sources were then measured. STUDENT_MIDATE is set on all 941 and
    splits them 939 in 2025Q4 / 2 in 2026Q2; CURRENT_QUARTER is set on all 941
    but is uniformly 20242. The owner chose a single value for all of them.

    The value chosen was 20261 (2026 Q1). **There is no such quarter.**
    MOHE_QUARTER ends at 20253 (2025 Q3): there is no 2025 Q4, no 2026 Q1 and
    no 2026 Q2 — the gap WD-12 records, wider than WD-12 states. Pointing 938
    students at a quarter that does not exist would orphan every one of them,
    which is the fault V129 and V130 were written to end. So the value is
    20253, the latest quarter that exists, confirmed by the owner.

    This is a placeholder, and should be read as one. It says these students
    were added by 2025 Q3, which is true of them collectively but is not their
    individual added quarter. When the ministry fills the quarter gaps (WD-12)
    or supplies the real dates, re-run a corrected version: the rows are
    identifiable because they all carry exactly this value.

    Idempotent: only rows still unset are touched, and nothing runs if the
    target quarter is missing.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

-- STUDENT_DATA is under the tenant policy; without this the script sees no rows
-- and reports a cheerful zero.
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

DECLARE @target INT = 20253;

IF NOT EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER
                WHERE ISNULL(QUARTER_YEAR,0) * 10 + ISNULL(QUARTER_NO,0) = @target)
BEGIN
    PRINT '*** Refusing to run: quarter ' + CAST(@target AS varchar(12))
        + ' does not exist in MOHECORE.MOHE_QUARTER. Create it (WD-12) or change @target.';
    RETURN;
END

DECLARE @before INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA
                        WHERE ISNULL(STUDENT_ADDED_QUARTER, 0) = 0);
PRINT 'Students with no added quarter, before: ' + CAST(@before AS varchar(12));

UPDATE MOHECORE.STUDENT_DATA
   SET STUDENT_ADDED_QUARTER = @target
 WHERE ISNULL(STUDENT_ADDED_QUARTER, 0) = 0;

PRINT 'Rows set to ' + CAST(@target AS varchar(12)) + ': ' + CAST(@@ROWCOUNT AS varchar(12));

DECLARE @after INT = (SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA
                       WHERE ISNULL(STUDENT_ADDED_QUARTER, 0) = 0);
PRINT 'Students with no added quarter, after:  ' + CAST(@after AS varchar(12));

-- Nothing may point at a quarter that is not there.
IF EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA d
            WHERE ISNULL(d.STUDENT_ADDED_QUARTER, 0) > 0
              AND NOT EXISTS (SELECT 1 FROM MOHECORE.MOHE_QUARTER q
                               WHERE ISNULL(q.QUARTER_YEAR,0) * 10 + ISNULL(q.QUARTER_NO,0)
                                     = d.STUDENT_ADDED_QUARTER))
    PRINT '*** Warning: some students still name a quarter that does not exist (pre-existing, not written here).';
ELSE
    PRINT 'Every added quarter now names a quarter that exists.';
