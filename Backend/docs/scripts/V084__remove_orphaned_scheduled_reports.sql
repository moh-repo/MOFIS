/* ============================================================================
   V084 — A schedule owned by nobody, delivering to nobody.

   !!  THIS SCRIPT ONLY REPORTS UNTIL @Confirm = 1 BELOW.                     !!

   Why
   ---
   MOHECORE.SCHEDULED_REPORT holds two rows, 219 and 220, both with
   CREATED_BY = 'SAMPLE_DATA' — a username that exists in no row of
   UMSCORE.Users. Since the scheduler began resolving the owner's country scope
   from CREATED_BY (so that a schedule made by a country officer stops emailing
   them the whole ministry), an owner that cannot be resolved is refused rather
   than widened, and every run of these two fails.

   Row 220 has been failing since 30 August 2026, and until the fix that ships
   with this script it failed every fifteen minutes for ever: the failure path
   left NEXT_RUN_DATE where it was, and where it was is in the past. Row 219 is
   not due until 5 September and would have begun the same thing then.

   Neither has a RECIPIENT_EMAIL, so neither has ever delivered anything to
   anyone: a run builds a workbook and drops it.

   What
   ----
   Deletes schedules that are BOTH orphaned (CREATED_BY names no live user) AND
   undeliverable (RECIPIENT_EMAIL is null or blank). Both conditions, on purpose:

     - An orphaned schedule that DOES name a recipient is somebody's real
       reporting arrangement whose owner has left. That is configuration worth
       reassigning to a live user, not deleting. It is reported here and left
       alone; reassign it in the database or through PUT /manage.
     - A schedule with a recipient but a live owner is working, and is not
       touched.

   Rollback
   --------
   None: the rows are deleted. The report printed before the delete lists every
   column of what is going, so a row can be recreated from this script's output
   if it turns out to have been wanted. Take a backup of the table first if that
   matters to you.

   Idempotent. On a database with no orphaned schedules it reports none and
   deletes nothing.
   ============================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 *  SET THIS TO 1 TO DELETE. Leave at 0 to report only.
 * ──────────────────────────────────────────────────────────────────────────── */
DECLARE @Confirm BIT = 1;   /* confirmed by the owner, 1 Sep 2026 */

/* Read across every country: the scheduler itself runs elevated. */
EXEC sp_set_session_context @key = N'TenantAll', @value = 1;

DECLARE @orphans TABLE (SCHEDULE_ID INT PRIMARY KEY, deliverable BIT);

INSERT INTO @orphans (SCHEDULE_ID, deliverable)
SELECT s.SCHEDULE_ID,
       CASE WHEN NULLIF(LTRIM(RTRIM(s.RECIPIENT_EMAIL)), N'') IS NULL THEN 0 ELSE 1 END
  FROM MOHECORE.SCHEDULED_REPORT s
 WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Users u
                    WHERE u.username = s.CREATED_BY AND u.IsDeleted = 0);

PRINT '';
PRINT '=== V084: schedules whose CREATED_BY names no live user ===';

SELECT s.SCHEDULE_ID, s.SCHEDULE_NAME, s.REPORT_TYPE, s.CREATED_BY,
       s.IS_ENABLED, s.SCHEDULE_STATUS, s.FREQUENCY,
       s.NEXT_RUN_DATE, s.LAST_RUN_DATE, s.LAST_RUN_STATUS, s.RECIPIENT_EMAIL,
       action_ = CASE WHEN o.deliverable = 1
                      THEN 'KEPT - names a recipient; reassign its owner instead'
                      WHEN @Confirm = 1 THEN 'deleting'
                      ELSE 'would delete (set @Confirm = 1)' END
  FROM MOHECORE.SCHEDULED_REPORT s
  JOIN @orphans o ON o.SCHEDULE_ID = s.SCHEDULE_ID
 ORDER BY s.SCHEDULE_ID;

IF NOT EXISTS (SELECT 1 FROM @orphans)
    PRINT 'V084: no orphaned schedules; nothing to do.';
ELSE IF @Confirm = 0
    PRINT 'V084: report only. Set @Confirm = 1 to delete the undeliverable ones.';
ELSE
BEGIN
    DELETE s
      FROM MOHECORE.SCHEDULED_REPORT s
      JOIN @orphans o ON o.SCHEDULE_ID = s.SCHEDULE_ID
     WHERE o.deliverable = 0;

    PRINT 'V084: deleted ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + ' orphaned, undeliverable schedule(s).';

    IF EXISTS (SELECT 1 FROM @orphans WHERE deliverable = 1)
        PRINT 'V084: orphaned schedules that DO name a recipient were kept. Reassign their owner.';
END
GO

/* ── Verification ────────────────────────────────────────────────────────── */
DECLARE @remaining INT = (
    SELECT COUNT(*) FROM MOHECORE.SCHEDULED_REPORT s
     WHERE NOT EXISTS (SELECT 1 FROM UMSCORE.Users u
                        WHERE u.username = s.CREATED_BY AND u.IsDeleted = 0)
       AND NULLIF(LTRIM(RTRIM(s.RECIPIENT_EMAIL)), N'') IS NULL);

IF @remaining > 0
    RAISERROR('V084: %d orphaned, undeliverable schedule(s) remain. Was @Confirm left at 0?', 16, 1, @remaining);
ELSE
    PRINT 'V084 OK: no schedule is both orphaned and undeliverable.';
GO
