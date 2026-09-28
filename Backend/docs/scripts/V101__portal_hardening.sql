/*
    V101 - Student portal hardening (security plan S-11).

    STUDENTCORE.student_users gains:
      - email_verified_at: the portal verifies the e-mail (the username) with a
        one-time code on the first sign-in of an account that has never proved
        it. Accounts that have already signed in, or that were created before
        this script, are treated as verified: their owners were reached
        through the same address when the account was handed to them.
      - totp_*: an optional second factor the student turns on from the
        portal (same TOTP as the staff MFA, O-16); the secret is stored
        encrypted with the MFA key.

    Idempotent.
*/
IF COL_LENGTH('STUDENTCORE.student_users', 'email_verified_at') IS NULL
BEGIN
    ALTER TABLE STUDENTCORE.student_users ADD email_verified_at datetime2 NULL;
    PRINT 'student_users.email_verified_at added';
END
GO
IF COL_LENGTH('STUDENTCORE.student_users', 'totp_secret_enc') IS NULL
BEGIN
    ALTER TABLE STUDENTCORE.student_users ADD
        totp_secret_enc nvarchar(512) NULL,
        totp_enabled bit NOT NULL CONSTRAINT DF_student_users_totp_enabled DEFAULT 0,
        totp_enrolled_at datetime2 NULL,
        totp_failed_attempts int NOT NULL CONSTRAINT DF_student_users_totp_failed DEFAULT 0,
        totp_locked_until datetime2 NULL;
    PRINT 'student_users.totp_* added';
END
GO
-- Existing accounts: verified as of their last sign-in, or their creation.
UPDATE STUDENTCORE.student_users
   SET email_verified_at = COALESCE(last_login_at, created_at)
 WHERE email_verified_at IS NULL;
PRINT CONCAT('existing portal accounts marked verified: ', @@ROWCOUNT);
GO
