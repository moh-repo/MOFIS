package com.dilaw.mohe.ums.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security plan O-26 (6 Sep 2026): a session has an absolute lifetime and a
 * user holds a bounded number of them.
 *
 * <p>Works on {@code jo_staff} with a distinctive client address so its own
 * rows can be told apart and removed in a finally. Eviction chooses the oldest
 * session the user has, which may be one that pre-dates the test; the
 * assertions are therefore on the bound and on the audit trail, not on which
 * row went.
 */
@Tag("integration")
@SpringBootTest(properties = {"jwt.max-concurrent-sessions=3", "jwt.absolute-session-hours=12"})
class SessionLimitsTest {

    private static final long JORDAN_STAFF = 14L;
    private static final String PROBE_IP = "203.0.113.9";

    @Autowired private UmsAuthService authService;
    @Autowired private UmsUserSessionService sessionService;
    @Autowired private JdbcTemplate jdbc;

    private void cleanup() {
        jdbc.update("DELETE FROM UMSCORE.UserSessions WHERE UserId = ? AND IpAddress = ?", JORDAN_STAFF, PROBE_IP);
        jdbc.update("DELETE FROM UMSCORE.UserSessions WHERE UserId = ? AND TokenHash LIKE 'o26-%'", JORDAN_STAFF);
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE UserId = ? AND Action IN ('SESSION_EVICTED','SESSION_ABSOLUTE_EXPIRY')", JORDAN_STAFF);
    }

    private int activeSessions() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserSessions WHERE UserId = ? AND IsActive = 1 AND ExpiresAt > SYSDATETIME()",
                Integer.class, JORDAN_STAFF);
    }

    @Test
    @DisplayName("a fourth login ends the oldest session and audits it")
    void concurrentSessionsAreBounded() {
        cleanup();
        try {
            for (int i = 1; i <= 4; i++) {
                authService.createUserSession(JORDAN_STAFF, PROBE_IP, "O26-probe/" + i, "o26-jti-" + i);
            }
            assertThat(activeSessions())
                    .as("no more than the configured maximum stays live")
                    .isLessThanOrEqualTo(3);
            Integer evicted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE UserId = ? AND Action = 'SESSION_EVICTED'",
                    Integer.class, JORDAN_STAFF);
            assertThat(evicted).as("each eviction leaves an audit row").isGreaterThanOrEqualTo(1);
            // The newest login is always kept.
            assertThat(sessionService.isSessionActive(JORDAN_STAFF, "o26-jti-4")).isTrue();
        } finally {
            cleanup();
        }
    }

    @Test
    @DisplayName("a session older than the absolute lifetime cannot be refreshed")
    void absoluteLifetimeEndsRefresh() {
        cleanup();
        try {
            jdbc.update("""
                    INSERT INTO UMSCORE.UserSessions (UserId, TokenHash, IpAddress, UserAgent, DeviceInfo, CreatedAt, ExpiresAt, IsActive)
                    VALUES (?, 'o26-old', ?, 'O26-probe/old', 'probe', DATEADD(HOUR, -13, SYSDATETIME()), DATEADD(HOUR, 8, SYSDATETIME()), 1)
                    """, JORDAN_STAFF, PROBE_IP);
            jdbc.update("""
                    INSERT INTO UMSCORE.UserSessions (UserId, TokenHash, IpAddress, UserAgent, DeviceInfo, CreatedAt, ExpiresAt, IsActive)
                    VALUES (?, 'o26-fresh', ?, 'O26-probe/fresh', 'probe', DATEADD(HOUR, -1, SYSDATETIME()), DATEADD(HOUR, 8, SYSDATETIME()), 1)
                    """, JORDAN_STAFF, PROBE_IP);

            assertThat(sessionService.rotateSessionToken(JORDAN_STAFF, "o26-old", "o26-old-2"))
                    .as("13 hours after login the token TTL alone no longer keeps a session alive")
                    .isFalse();
            assertThat(sessionService.isSessionActive(JORDAN_STAFF, "o26-old")).as("and it is ended, not left for the next attempt").isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE UserId = ? AND Action = 'SESSION_ABSOLUTE_EXPIRY'",
                    Integer.class, JORDAN_STAFF)).isEqualTo(1);

            assertThat(sessionService.rotateSessionToken(JORDAN_STAFF, "o26-fresh", "o26-fresh-2"))
                    .as("positive control: a session inside the window still rotates")
                    .isTrue();
            assertThat(sessionService.isSessionActive(JORDAN_STAFF, "o26-fresh-2")).isTrue();
        } finally {
            cleanup();
        }
    }
}
