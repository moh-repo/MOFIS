package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.repository.UmsUserSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-09: a session used from a different address <em>and</em> device is an
 * anomaly. A clerk's session is rebound and the event recorded; an
 * administrator's session is ended. One of the two changing on its own is a
 * phone on a new network or an upgraded browser, and passes.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class SessionBindingTest {

    private static final long MINISTRY_ADMIN = 2L;
    /** A clerk with no role at all: nothing in the MFA-required set of O-16 (the seeded jo_staff holds an approval code). */
    private long clerk;
    private static final String AGENT_A = "Mozilla/5.0 (Windows NT 10.0) Chrome/128 S09-A";
    private static final String AGENT_B = "Mozilla/5.0 (Linux; Android 14) Chrome/128 S09-B";

    @Autowired private SessionBindingService binding;
    @Autowired private UmsUserSessionRepository sessions;
    @Autowired private UmsUserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @org.junit.jupiter.api.BeforeEach
    void throwawayClerk() {
        String username = "s09-clerk-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO UMSCORE.Users
                    (Username, Email, PasswordHash, Status, IsAdmin, IsSuperAdmin, IsMinistryUser,
                     IsActive, IsDeleted, FailedLoginAttempts, LockoutStage, CreatedAt)
                VALUES (?, ?, 'x', ?, 0, 0, 0, 1, 0, 0, 0, SYSDATETIME())
                """, username, username + "@example.invalid", com.dilaw.mohe.codes.UserStatus.ACTIVE);
        clerk = jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE Username = ?", Long.class, username);
        em.clear();
    }

    private UmsUserSession issue(long userId, String ip, String agent) {
        UmsUserSession s = new UmsUserSession();
        s.setUser(users.findById(userId).orElseThrow());
        s.setTokenHash("s09-" + System.nanoTime());
        s.setIpAddress(ip);
        s.setUserAgent(agent);
        s.setUserAgentHash(SessionBindingService.hash(agent));
        s.setExpiresAt(LocalDateTime.now().plusHours(1));
        s.setIsActive(true);
        return sessions.saveAndFlush(s);
    }

    private int audited(String action, long sessionId) {
        em.flush();
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE [Action] = ? AND ResourceType = 'Session' AND ResourceId = ?",
                Integer.class, action, sessionId);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("one of address or device changing is not an anomaly")
    void oneChangePasses() {
        UmsUserSession s = issue(clerk, "10.0.0.1", AGENT_A);
        assertThat(binding.check(s, "10.0.0.2", AGENT_A)).isEqualTo(SessionBindingService.Verdict.OK);
        assertThat(binding.check(s, "10.0.0.1", AGENT_B)).isEqualTo(SessionBindingService.Verdict.OK);
        assertThat(binding.check(s, "10.0.0.1", AGENT_A)).isEqualTo(SessionBindingService.Verdict.OK);
        assertThat(s.getIsActive()).isTrue();
        assertThat(audited("SESSION_ANOMALY", s.getId())).isZero();
    }

    @Test
    @DisplayName("a clerk's session is rebound to the new address and device, and the event is audited")
    void clerkIsRebound() {
        UmsUserSession s = issue(clerk, "10.0.0.1", AGENT_A);
        assertThat(binding.check(s, "203.0.113.9", AGENT_B)).isEqualTo(SessionBindingService.Verdict.REBOUND);
        assertThat(s.getIsActive()).isTrue();
        assertThat(s.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(s.getUserAgentHash()).isEqualTo(SessionBindingService.hash(AGENT_B));
        assertThat(s.getAnomalyCount()).isEqualTo(1);
        assertThat(audited("SESSION_ANOMALY", s.getId())).isEqualTo(1);
        // Rebound means the next request from the new place is normal.
        assertThat(binding.check(s, "203.0.113.9", AGENT_B)).isEqualTo(SessionBindingService.Verdict.OK);
    }

    @Test
    @DisplayName("an administrator's session is ended instead")
    void administratorIsEnded() {
        UmsUserSession s = issue(MINISTRY_ADMIN, "10.0.0.1", AGENT_A);
        assertThat(binding.check(s, "203.0.113.9", AGENT_B)).isEqualTo(SessionBindingService.Verdict.ENDED);
        assertThat(s.getIsActive()).isFalse();
        assertThat(audited("SESSION_ANOMALY_ENDED", s.getId())).isEqualTo(1);
        em.flush();
        assertThat(jdbc.queryForObject("SELECT IsActive FROM UMSCORE.UserSessions WHERE Id = ?", Boolean.class, s.getId()))
                .isFalse();
    }

    @Test
    @DisplayName("a clerk's session ends on the second move, not the first (DEC-17)")
    void secondAnomalyEndsAClerksSession() {
        UmsUserSession s = issue(clerk, "10.0.0.1", AGENT_A);

        assertThat(binding.check(s, "203.0.113.9", AGENT_B)).isEqualTo(SessionBindingService.Verdict.REBOUND);
        assertThat(s.getIsActive()).as("once is a journey").isTrue();

        // A third place: address and device differ from the rebound ones.
        assertThat(binding.check(s, "198.51.100.7", AGENT_A)).isEqualTo(SessionBindingService.Verdict.ENDED);
        assertThat(s.getIsActive()).as("twice is a token in two places").isFalse();
        assertThat(s.getAnomalyCount()).isEqualTo(2);
        assertThat(audited("SESSION_ANOMALY", s.getId())).isEqualTo(1);
        assertThat(audited("SESSION_ANOMALY_ENDED", s.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("a session issued before V100 (no hash) is compared on the stored agent text")
    void legacyRowComparesText() {
        UmsUserSession s = issue(clerk, "10.0.0.1", AGENT_A);
        s.setUserAgentHash(null);
        sessions.saveAndFlush(s);
        assertThat(binding.check(s, "203.0.113.9", AGENT_A)).isEqualTo(SessionBindingService.Verdict.OK);
        assertThat(binding.check(s, "203.0.113.9", AGENT_B)).isEqualTo(SessionBindingService.Verdict.REBOUND);
    }
}
