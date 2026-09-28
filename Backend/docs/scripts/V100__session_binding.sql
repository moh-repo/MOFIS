/*
    V100 - Session binding (security plan S-09).

    Why
    ---
    The JWT lives in the browser's localStorage (O-07, option A). A copied
    token presented from another machine looks exactly like the owner's until
    something compares where it is being used with where it was issued.

    What
    ----
    UMSCORE.UserSessions already records the address and the user agent at
    sign-in. This adds:
      - UserAgentHash: SHA-256 of the user agent, so the comparison does not
        depend on the 500-character truncation of UserAgent;
      - AnomalyCount: how many times the session was rebound to a new
        address and device (a non-privileged account is rebound and the
        event audited as SESSION_ANOMALY; a privileged account's session is
        ended instead).

    Rows issued before this script have a NULL hash and are compared on the
    stored UserAgent text until they expire. Idempotent.
*/
IF COL_LENGTH('UMSCORE.UserSessions', 'UserAgentHash') IS NULL
BEGIN
    ALTER TABLE UMSCORE.UserSessions ADD UserAgentHash CHAR(64) NULL;
    PRINT 'UMSCORE.UserSessions.UserAgentHash added';
END
GO
IF COL_LENGTH('UMSCORE.UserSessions', 'AnomalyCount') IS NULL
BEGIN
    ALTER TABLE UMSCORE.UserSessions ADD AnomalyCount INT NOT NULL CONSTRAINT DF_UserSessions_AnomalyCount DEFAULT 0;
    PRINT 'UMSCORE.UserSessions.AnomalyCount added';
END
GO
