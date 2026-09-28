package com.dilaw.mohe.ums;

import com.dilaw.mohe.ums.controler.UmsAuthController;
import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.ums.service.JwtTokenService;
import com.dilaw.mohe.ums.service.LoginCaptchaService;
import com.dilaw.mohe.ums.service.MfaService;
import com.dilaw.mohe.ums.service.TotpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * O-16 end to end through the controller: a privileged account signs in with
 * its password, is told to enrol, enrols, and from then on must present a
 * code. The pending token opens no session and authenticates nothing.
 */
@Tag("integration")
@SpringBootTest
class MfaLoginFlowTest {

    @Autowired private UmsAuthController auth;
    @Autowired private JwtTokenService jwt;
    @Autowired private TotpService totp;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private LoginCaptchaService captcha;

    private static final String PASSWORD = "Mfa-flow-test-passw0rd!";
    private String username;
    private long userId;

    @BeforeEach
    void throwawaySuperAdmin() {
        when(captcha.solve(any(), any())).thenReturn(true);
        username = "mfa-flow-" + System.nanoTime();
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        jdbc.update("""
                INSERT INTO UMSCORE.Users
                    (Username, Email, PasswordHash, Status, IsAdmin, IsSuperAdmin, IsMinistryUser,
                     IsActive, IsDeleted, FailedLoginAttempts, LockoutStage, CreatedAt)
                VALUES (?, ?, ?, ?, 1, 1, 1, 1, 0, 0, 0, SYSDATETIME())
                """, username, username + "@example.invalid", hash, UserStatus.ACTIVE);
        userId = jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE Username = ?", Long.class, username);
    }

    @AfterEach
    void dropIt() {
        jdbc.update("DELETE FROM UMSCORE.UserMfaRecoveryCodes WHERE UserId = ?", userId);
        jdbc.update("DELETE FROM UMSCORE.UserMfa WHERE UserId = ?", userId);
        jdbc.update("DELETE FROM UMSCORE.UserSessions WHERE UserId = ?", userId);
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE UserId = ?", userId);
        jdbc.update("DELETE FROM UMSCORE.Users WHERE Id = ?", userId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login() {
        ResponseEntity<?> r = auth.login(new UmsAuthController.LoginRequest(username, PASSWORD, "c", "x"), request());
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        return (Map<String, Object>) r.getBody();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/ums/auth/login");
        r.setRemoteAddr("203.0.113.9");
        r.addHeader("User-Agent", "junit");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("first sign-in: enrol; every later sign-in: a code; the pending token is not a session")
    void enrolThenVerify() {
        Map<String, Object> first = login();
        assertThat(first).containsEntry("mfaEnrolmentRequired", true).containsEntry("mfaRequired", false)
                .doesNotContainKey("token");
        String pending = (String) first.get("mfaToken");
        assertThat(JwtTokenService.isMfaPending(jwt.parseToken(pending))).isTrue();
        assertThat(jwt.refreshToken(pending)).as("cannot be refreshed into a session token").isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserSessions WHERE UserId = ?", Integer.class, userId))
                .as("no session before the second factor").isZero();

        Map<String, Object> start = (Map<String, Object>) auth.mfaEnrolStart(new UmsAuthController.MfaStartRequest(pending)).getBody();
        String secret = (String) start.get("secret");
        assertThat((String) start.get("qrPng")).startsWith("data:image/png;base64,");

        Map<String, Object> done = (Map<String, Object>) auth.mfaEnrolConfirm(
                new UmsAuthController.MfaCodeRequest(pending, totp.code(secret, totp.currentStep())), request()).getBody();
        assertThat(done).containsKey("token").containsKey("recoveryCodes");
        List<String> recovery = (List<String>) done.get("recoveryCodes");
        assertThat(recovery).hasSize(10);
        assertThat(JwtTokenService.isMfaPending(jwt.parseToken((String) done.get("token")))).isFalse();

        Map<String, Object> second = login();
        assertThat(second).containsEntry("mfaRequired", true).doesNotContainKey("token");
        String pending2 = (String) second.get("mfaToken");
        assertThatThrownBy(() -> auth.mfaVerify(new UmsAuthController.MfaVerifyRequest(pending2, "000000"), request()))
                .isInstanceOf(MfaService.MfaException.class);
        Map<String, Object> signedIn = (Map<String, Object>) auth.mfaVerify(
                new UmsAuthController.MfaVerifyRequest(pending2, totp.code(secret, totp.currentStep() + 1)), request()).getBody();
        assertThat(signedIn).containsKey("token").containsKey("permissions");

        Map<String, Object> third = login();
        Map<String, Object> viaRecovery = (Map<String, Object>) auth.mfaVerify(
                new UmsAuthController.MfaVerifyRequest((String) third.get("mfaToken"), recovery.get(3)), request()).getBody();
        assertThat(viaRecovery).containsKey("token");

        assertThatThrownBy(() -> auth.mfaVerify(new UmsAuthController.MfaVerifyRequest("not-a-token", "123456"), request()))
                .isInstanceOf(MfaService.MfaException.class);
    }

    /**
     * Live QA R6-03 (28 Sep 2026): after the tenth wrong code locked the account, the right
     * code on the same pending sign-in still signed in, and its token opened data pages
     * while the account stayed LOCKED.
     */
    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("R6-03: the account lock ends the open session and refuses the right code on the pending sign-in")
    void accountLockStopsThePendingSignIn() {
        String pending = (String) login().get("mfaToken");
        String secret = (String) ((Map<String, Object>) auth.mfaEnrolStart(
                new UmsAuthController.MfaStartRequest(pending)).getBody()).get("secret");
        auth.mfaEnrolConfirm(new UmsAuthController.MfaCodeRequest(pending, totp.code(secret, totp.currentStep())),
                request());
        assertThat(activeSessions()).as("the session opened by the enrolment").isEqualTo(1);

        String pending2 = (String) login().get("mfaToken");
        for (int i = 0; i < 5; i++) wrongCode(pending2);
        jdbc.update("UPDATE UMSCORE.UserMfa SET LockedUntil = DATEADD(second, -1, SYSDATETIME()) WHERE UserId = ?", userId);
        for (int i = 0; i < 5; i++) wrongCode(pending2);

        assertThat(jdbc.queryForObject("SELECT Status FROM UMSCORE.Users WHERE Id = ?", String.class, userId))
                .isEqualTo(UserStatus.LOCKED);
        assertThat(activeSessions()).as("the lock ends the session already open").isZero();

        String right = totp.code(secret, totp.currentStep() + 1);
        assertThatThrownBy(() -> auth.mfaVerify(new UmsAuthController.MfaVerifyRequest(pending2, right), request()))
                .isInstanceOf(MfaService.MfaException.class)
                .hasMessageContaining("locked");
        assertThat(activeSessions()).as("the right code opens no session").isZero();
        assertThat(jdbc.queryForObject("SELECT Status FROM UMSCORE.Users WHERE Id = ?", String.class, userId))
                .isEqualTo(UserStatus.LOCKED);
    }

    private void wrongCode(String pending) {
        assertThatThrownBy(() -> auth.mfaVerify(new UmsAuthController.MfaVerifyRequest(pending, "000000"), request()))
                .isInstanceOf(MfaService.MfaException.class);
    }

    private int activeSessions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserSessions WHERE UserId = ? AND IsActive = 1",
                Integer.class, userId);
    }
}
