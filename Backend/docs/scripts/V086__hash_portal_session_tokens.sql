/*
    V086 — Store student-portal session tokens as SHA-256 hashes.

    Security plan O-06 (6 Sep 2026).

    Why
    ---
    STUDENTCORE.student_sessions.token and MOHECORE.StudentPortalSession.Token
    held the bearer value itself. Anyone who could read either table — a backup,
    the query-engine login, an administrator exempt from RLS — could take over
    every live portal session. The UMS side never had this problem: its
    UserSessions.TokenHash holds the JWT's jti, which is useless without the
    signed token.

    What
    ----
    Replaces each stored token with the lower-case hex SHA-256 of its UTF-8
    bytes. util/PortalTokens.java computes exactly the same value in the
    application, so sessions issued before this script keep working: the browser
    still presents the raw token, the application hashes it, the row matches.

    Deployment order
    ----------------
      1. Deploy the application build containing PortalTokens (it hashes on
         write AND on lookup).
      2. Run this script once. Between 1 and 2 new logins work (hashed on write)
         and old sessions fail to resolve (raw stored, hashed lookup); students
         simply log in again. Run 2 immediately after 1 to keep that window short.
      3. Do not run this script twice: a hash of a hash matches nothing, and
         every session would be invalidated. The guard below refuses rows that
         already look hashed (exactly 64 hex characters).

    Idempotence
    -----------
    Rows whose token is already 64 hex characters are left alone, so a re-run
    is a no-op rather than a mass logout. The random tokens the application
    issues are 32 hex characters, so no unhashed row can be mistaken for a hash.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. STUDENTCORE.student_sessions (the live table, V036+)
UPDATE s
   SET token = LOWER(CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(4000), token)), 2))
  FROM STUDENTCORE.student_sessions s
 WHERE s.token IS NOT NULL
   AND NOT (LEN(s.token) = 64 AND s.token NOT LIKE '%[^0-9a-fA-F]%');

PRINT CONCAT('student_sessions rows hashed: ', @@ROWCOUNT);

-- 2. MOHECORE.StudentPortalSession (legacy; resolves tokens issued before V036)
IF OBJECT_ID('MOHECORE.StudentPortalSession', 'U') IS NOT NULL
BEGIN
    UPDATE s
       SET Token = LOWER(CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(4000), Token)), 2))
      FROM MOHECORE.StudentPortalSession s
     WHERE s.Token IS NOT NULL
       AND NOT (LEN(s.Token) = 64 AND s.Token NOT LIKE '%[^0-9a-fA-F]%');

    PRINT CONCAT('StudentPortalSession rows hashed: ', @@ROWCOUNT);
END

COMMIT TRANSACTION;

-- Verify: every remaining token is a 64-character hex string.
SELECT 'student_sessions' AS tbl, COUNT(*) AS unhashed
  FROM STUDENTCORE.student_sessions
 WHERE token IS NOT NULL AND NOT (LEN(token) = 64 AND token NOT LIKE '%[^0-9a-fA-F]%')
UNION ALL
SELECT 'StudentPortalSession', COUNT(*)
  FROM MOHECORE.StudentPortalSession
 WHERE Token IS NOT NULL AND NOT (LEN(Token) = 64 AND Token NOT LIKE '%[^0-9a-fA-F]%');
