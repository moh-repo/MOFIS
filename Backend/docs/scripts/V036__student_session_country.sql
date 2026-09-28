/*
    V036 — carry the study country on a student-portal session.

    Why
    ---
    The student portal authenticates against STUDENTCORE, which is a separate
    realm from UMSCORE: its own users, roles, permissions, groups and sessions,
    sharing nothing with the ministry's staff accounts. It also holds no country
    anywhere — student_users has student_user_id, student_id, username,
    password_hash, status and timestamps, and that is all. A portal user's country
    is reachable only through student_id -> MOHECORE.STUDENT_DATA.STUDY_COUNTRY_IN.

    That mattered once Row-Level Security arrived, in two ways.

    1. Portal login was broken outright
    -----------------------------------
    Verified against this database with the policy enabled and no session context
    set — which is exactly the state a login request runs in, because
    the portal auth endpoints are excluded from StudentPortalTokenInterceptor
    (there is no token yet to resolve):

        SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA        ->    0
        SELECT COUNT(*) FROM STUDENTCORE.student_users    ->  941

    STUDENTCORE sits outside the policy, so the password check succeeded and then
    StudentUserAuthService's studentDataRepository.findById returned nothing,
    raising 404 "Student record not found". A student with entirely correct
    credentials was told their record did not exist. The application fix is to
    resolve that lookup as system; this migration is the other half.

    2. Deriving the country on every request is the wrong shape
    -----------------------------------------------------------
    Without a country on the session, each portal request had to read STUDENT_DATA
    under elevation just to learn which country the caller belongs to — a query
    and a privilege escalation per request, to recover a fact that was already
    known at login and cannot change during a session.

    Stamping it here means the interceptor gets the country from the same row it
    already reads to validate the token. No extra query, and no request-time
    elevation at all.

    A denormalised copy, and why it is safe
    ---------------------------------------
    This is the student's country as it was when the session was issued. It is not
    kept in step with STUDENT_DATA, and deliberately so: a session is a fixed
    window, and a student's country changing mid-session should end that session
    rather than silently widen or move it. Sessions here last 7 days
    (StudentUserAuthService.SESSION_DAYS), so the window is bounded.

    Existing sessions are backfilled rather than left null, so applying this does
    not log anybody out.

    Idempotent. Apply in SSMS after V035.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ── 1. The column ─────────────────────────────────────────────────────────────
 * Nullable: the backfill below fills every existing row, but a null must remain
 * representable so that a session whose student has no recorded country is
 * expressible rather than being forced to a wrong value. The application treats
 * null as "no scope" and denies, which is the safe reading.
 * ──────────────────────────────────────────────────────────────────────────── */
IF COL_LENGTH('STUDENTCORE.student_sessions', 'study_country_in_id') IS NULL
    ALTER TABLE STUDENTCORE.student_sessions ADD study_country_in_id INT NULL;
GO

/* ── 2. Backfill live sessions ────────────────────────────────────────────────
 * Every session, not only unexpired ones: an expired row costs nothing to fill
 * and leaving it null would make "null means the session predates V036"
 * ambiguous with "null means this student has no country".
 * ──────────────────────────────────────────────────────────────────────────── */
UPDATE s
   SET s.study_country_in_id = sd.STUDY_COUNTRY_IN
  FROM STUDENTCORE.student_sessions s
  JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = s.student_id
 WHERE s.study_country_in_id IS NULL
   AND sd.STUDY_COUNTRY_IN IS NOT NULL;
GO

/* ── 3. Foreign key ───────────────────────────────────────────────────────────
 * Cross-schema, same database. Keeps the column honest: it can only ever hold an
 * id that STUDY_COUNTRY_IN recognises.
 *
 * No index. The column is never a search key — sessions are found by token, and
 * this is read from the row that lookup already returned.
 * ──────────────────────────────────────────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_student_sessions_STUDY_COUNTRY_IN')
    ALTER TABLE STUDENTCORE.student_sessions
        ADD CONSTRAINT FK_student_sessions_STUDY_COUNTRY_IN
        FOREIGN KEY (study_country_in_id)
        REFERENCES MOHECORE.STUDY_COUNTRY_IN (STUDY_COUNTRY_IN_ID);
GO

/* ── 4. Verify ────────────────────────────────────────────────────────────── */
PRINT '';
PRINT '=== sessions, and whether each carries a country ===';
SELECT COUNT(*)                            AS sessions_total,
       COUNT(study_country_in_id)          AS with_country,
       SUM(CASE WHEN logged_out_at IS NULL AND expires_at > SYSUTCDATETIME()
                THEN 1 ELSE 0 END)         AS still_active
FROM STUDENTCORE.student_sessions;
GO

PRINT '';
PRINT '=== every filled session agrees with the student record it belongs to ===';
PRINT '(expected: 0 disagreeing)';
SELECT COUNT(*) AS disagreeing
FROM STUDENTCORE.student_sessions s
JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = s.student_id
WHERE s.study_country_in_id IS NOT NULL
  AND s.study_country_in_id <> sd.STUDY_COUNTRY_IN;
GO

PRINT '';
PRINT '=== reminder: STUDENTCORE holds no country of its own ===';
PRINT '(expected: 0 columns — the country is derived through student_id and pinned here)';
SELECT COUNT(*) AS country_columns_in_studentcore
FROM sys.columns c
JOIN sys.tables t ON t.object_id = c.object_id
WHERE SCHEMA_NAME(t.schema_id) = 'STUDENTCORE'
  AND c.name LIKE '%COUNTRY%'
  AND t.name <> 'student_sessions';
GO
