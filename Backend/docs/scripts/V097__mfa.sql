/*
    V097 - Multi-factor authentication (TOTP) for staff accounts.

    Security plan O-16, decision D-1 (a): a second factor is required for the
    privileged accounts (super admin, ministry admin, country admin, and any
    account holding a *.APPROVE, SYSTEM.RESTORE or UMS_ADMIN.* code) and
    optional for everyone else. Enforcement is the application's
    (mohe.mfa.enforce); this script only gives it somewhere to keep the state.

    What
    ----
    1. UMSCORE.UserMfa: one row per enrolled (or enrolling) account. The TOTP
       secret is stored encrypted (AES-GCM, key MOHE_MFA_KEY); LastUsedStep
       refuses a code presented twice; FailedAttempts/LockedUntil are the
       code-guessing lock (five wrong codes, fifteen minutes).
    2. UMSCORE.UserMfaRecoveryCodes: the one-time recovery codes, keyed HMAC
       only; a used code keeps its UsedAt.

    Effect
    ------
    None until the build of 6 Sep 2026 is deployed. With it, a privileged
    account that has not enrolled is asked to at its next sign-in.

    Rollback
    --------
    DROP TABLE UMSCORE.UserMfaRecoveryCodes; DROP TABLE UMSCORE.UserMfa;
    (every account then signs in with the password alone).

    This script is applied by Flyway on startup (O-29); it can also be run by
    hand with sqlcmd -I -f 65001.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'UMSCORE.UserMfa', N'U') IS NULL
BEGIN
    CREATE TABLE UMSCORE.UserMfa (
        UserId          BIGINT        NOT NULL,
        SecretEncrypted NVARCHAR(512) NOT NULL,
        Enabled         BIT           NOT NULL CONSTRAINT DF_UserMfa_Enabled DEFAULT (0),
        EnrolledAt      DATETIME2     NULL,
        LastUsedStep    BIGINT        NULL,
        FailedAttempts  INT           NOT NULL CONSTRAINT DF_UserMfa_Failed DEFAULT (0),
        LockedUntil     DATETIME2     NULL,
        ResetBy         BIGINT        NULL,
        ResetAt         DATETIME2     NULL,
        CreatedAt       DATETIME2     NOT NULL CONSTRAINT DF_UserMfa_CreatedAt DEFAULT (SYSDATETIME()),
        UpdatedAt       DATETIME2     NULL,
        CONSTRAINT PK_UserMfa PRIMARY KEY (UserId),
        CONSTRAINT FK_UserMfa_Users FOREIGN KEY (UserId) REFERENCES UMSCORE.Users (Id) ON DELETE CASCADE
    );
    PRINT N'UMSCORE.UserMfa created.';
END
ELSE
    PRINT N'UMSCORE.UserMfa already exists.';

IF OBJECT_ID(N'UMSCORE.UserMfaRecoveryCodes', N'U') IS NULL
BEGIN
    CREATE TABLE UMSCORE.UserMfaRecoveryCodes (
        Id        BIGINT IDENTITY(1,1) NOT NULL,
        UserId    BIGINT        NOT NULL,
        CodeHash  NVARCHAR(128) NOT NULL,
        UsedAt    DATETIME2     NULL,
        CreatedAt DATETIME2     NOT NULL CONSTRAINT DF_UserMfaRecovery_CreatedAt DEFAULT (SYSDATETIME()),
        CONSTRAINT PK_UserMfaRecoveryCodes PRIMARY KEY (Id),
        CONSTRAINT FK_UserMfaRecoveryCodes_Users FOREIGN KEY (UserId) REFERENCES UMSCORE.Users (Id) ON DELETE CASCADE
    );
    CREATE INDEX IX_UserMfaRecoveryCodes_User ON UMSCORE.UserMfaRecoveryCodes (UserId) INCLUDE (UsedAt);
    PRINT N'UMSCORE.UserMfaRecoveryCodes created.';
END
ELSE
    PRINT N'UMSCORE.UserMfaRecoveryCodes already exists.';
