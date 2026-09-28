/*
    V087 — Password history for staff accounts.

    Security plan O-25 (6 Sep 2026). UmsUserService.changePassword refuses a new
    password that matches any of the account's last five hashes, and writes the
    outgoing hash here. Only hashes are stored, the same BCrypt values that were
    on UMSCORE.Users.PasswordHash; rows beyond the last five are pruned on write.

    Idempotent: creates the table only if it is missing.
*/
IF OBJECT_ID('UMSCORE.UserPasswordHistory', 'U') IS NULL
BEGIN
    CREATE TABLE UMSCORE.UserPasswordHistory (
        Id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_UserPasswordHistory PRIMARY KEY,
        UserId       BIGINT        NOT NULL,
        PasswordHash NVARCHAR(255) NOT NULL,
        ChangedAt    DATETIME2(3)  NOT NULL CONSTRAINT DF_UserPasswordHistory_ChangedAt DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_UserPasswordHistory_User FOREIGN KEY (UserId) REFERENCES UMSCORE.Users (Id)
    );
    CREATE INDEX IX_UserPasswordHistory_User_ChangedAt ON UMSCORE.UserPasswordHistory (UserId, ChangedAt DESC);
    PRINT 'UMSCORE.UserPasswordHistory created';
END
ELSE
    PRINT 'UMSCORE.UserPasswordHistory already exists';
