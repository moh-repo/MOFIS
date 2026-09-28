package com.dilaw.mohe.ums;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.ums.controler.UmsAuthController;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.service.LoginCaptchaService;
import com.dilaw.mohe.ums.service.UmsUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Live QA R6-03 (28 Sep 2026), beyond the second factor: a locked account's token kept
 * working until it expired (up to {@code jwt.expiration-seconds}), and only the
 * authenticator's lock ended the account's sessions. Through the real filter chain: a
 * token works only while its account is {@code ACTIVE}, read on every request; and every
 * lock (the password ladder's last rung, an administrator's) ends the sessions, so an
 * unlock brings no old token back. The authenticator's lock is in {@link MfaLoginFlowTest}.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class LockedAccountSessionsTest {

    @Autowired private MockMvc mvc;
    @Autowired private UmsAuthController auth;
    @Autowired private UmsUserService users;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private LoginCaptchaService captcha;

    private static final String PASSWORD = "Locked-sessions-passw0rd!";
    /** The sign-in and the requests come from one address and device, so S-09 stays out of it. */
    private static final String ADDRESS = "203.0.113.10";
    private static final String AGENT = "junit";
    private String username;
    private long userId;

    @BeforeEach
    void throwawayStaff() {
        when(captcha.solve(any(), any())).thenReturn(true);
        username = "locked-sessions-" + System.nanoTime();
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        jdbc.update("""
                INSERT INTO UMSCORE.Users
                    (Username, Email, PasswordHash, Status, IsAdmin, IsSuperAdmin, IsMinistryUser,
                     IsActive, IsDeleted, FailedLoginAttempts, LockoutStage, CreatedAt)
                VALUES (?, ?, ?, ?, 0, 0, 0, 1, 0, 0, 0, SYSDATETIME())
                """, username, username + "@example.invalid", hash, UserStatus.ACTIVE);
        userId = jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE Username = ?", Long.class, username);
    }

    @AfterEach
    void dropIt() {
        jdbc.update("DELETE FROM UMSCORE.UserSessions WHERE UserId = ?", userId);
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE UserId = ? OR (ResourceType = 'USER' AND ResourceId = ?)",
                userId, userId);
        jdbc.update("DELETE FROM UMSCORE.Users WHERE Id = ?", userId);
    }

    @Test
    @DisplayName("a token works only while its account is ACTIVE, from the very next request")
    void statusIsReadOnEveryRequest() throws Exception {
        String token = signIn();
        assertThat(me(token)).isEqualTo(200);

        status(UserStatus.LOCKED);
        assertThat(me(token)).as("locked").isEqualTo(401);
        status(UserStatus.INACTIVE);
        assertThat(me(token)).as("inactive").isEqualTo(401);
        status(UserStatus.ACTIVE);
        assertThat(me(token)).as("read, not cached: the session was not ended, so it works again at once")
                .isEqualTo(200);
        jdbc.update("UPDATE UMSCORE.Users SET IsDeleted = 1 WHERE Id = ?", userId);
        assertThat(me(token)).as("deleted").isEqualTo(401);
    }

    @Test
    @DisplayName("an administrator's lock ends the sessions; the unlock opens a fresh sign-in and no old token")
    void administratorsLockEndsTheSessions() throws Exception {
        String token = signIn();
        assertThat(activeSessions()).isEqualTo(1);

        UmsUser patch = new UmsUser();
        patch.setStatus(UserStatus.LOCKED);
        AsUser.run(AsUser.SUPERADMIN, () -> users.updateUser(userId, patch, AsUser.SUPERADMIN));

        assertThat(currentStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(activeSessions()).isZero();
        assertThat(terminations()).isEqualTo(1);

        AsUser.run(AsUser.SUPERADMIN, () -> users.unlockUser(userId, AsUser.SUPERADMIN));
        assertThat(me(token)).as("the old token stays refused after the unlock").isEqualTo(401);
        assertThat(me(signIn())).as("a fresh sign-in works").isEqualTo(200);
    }

    @Test
    @DisplayName("the password ladder's last rung ends the sessions; the unlock brings no old token back")
    void passwordLaddersLastRungEndsTheSessions() throws Exception {
        String token = signIn();
        assertThat(activeSessions()).isEqualTo(1);

        // Wrong passwords until the ladder's last rung, each timed lock let run out in between.
        for (int attempt = 0; attempt < 100 && !UserStatus.LOCKED.equals(currentStatus()); attempt++) {
            jdbc.update("UPDATE UMSCORE.Users SET LockedUntil = NULL WHERE Id = ?", userId);
            assertThat(auth.login(new UmsAuthController.LoginRequest(username, "wrong-" + PASSWORD, "c", "x"),
                    request()).getStatusCode().value()).isEqualTo(401);
        }
        assertThat(currentStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(activeSessions()).isZero();
        assertThat(terminations()).isEqualTo(1);
        assertThat(me(token)).isEqualTo(401);

        AsUser.run(AsUser.SUPERADMIN, () -> users.unlockUser(userId, AsUser.SUPERADMIN));
        assertThat(me(token)).as("the old token stays refused after the unlock").isEqualTo(401);
    }

    @SuppressWarnings("unchecked")
    private String signIn() {
        var r = auth.login(new UmsAuthController.LoginRequest(username, PASSWORD, "c", "x"), request());
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertThat(body).as("no second factor for this account").containsKey("token");
        return (String) body.get("token");
    }

    private int me(String token) throws Exception {
        return mvc.perform(get("/api/v1/ums/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", AGENT)
                        .with(r -> { r.setRemoteAddr(ADDRESS); return r; }))
                .andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/ums/auth/login");
        r.setRemoteAddr(ADDRESS);
        r.addHeader("User-Agent", AGENT);
        return r;
    }

    private void status(String status) {
        jdbc.update("UPDATE UMSCORE.Users SET Status = ? WHERE Id = ?", status, userId);
    }

    private String currentStatus() {
        return jdbc.queryForObject("SELECT Status FROM UMSCORE.Users WHERE Id = ?", String.class, userId);
    }

    private int activeSessions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserSessions WHERE UserId = ? AND IsActive = 1",
                Integer.class, userId);
    }

    private int terminations() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE ResourceId = ? "
                + "AND ResourceType = 'USER' AND [Action] = 'SESSION_TERMINATED'", Integer.class, userId);
    }
}
