package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-11 on the dev database: an unverified account must present the e-mailed
 * code once; a student can turn the second factor on and is then asked for
 * it at sign-in; a dormant account is switched off by the nightly rule; a
 * portal download is written to the access log under the student's account.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class StudentPortalHardeningTest {

    private static final String PASSWORD = "portal-test-password-12";

    @Autowired private StudentUserAuthService auth;
    @Autowired private StudentPortalMfaService mfa;
    @Autowired private StudentPortalOtpService otp;
    @Autowired private PortalDormantAccountJob dormant;
    @Autowired private TotpService totp;
    @Autowired private StudentUserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;

    private int studentId;
    private int countryId;
    private String username;
    private int studentUserId;

    @BeforeEach
    void throwawayAccount() {
        // Every dev student already has a portal account (the seeder), so borrow one:
        // re-pointed for this test and rolled back with the transaction.
        Map<String, Object> s = TenantContext.runAsSystem(() -> jdbc.queryForMap("""
                SELECT TOP 1 u.student_user_id, u.student_id, d.STUDY_COUNTRY_IN
                FROM STUDENTCORE.student_users u JOIN MOHECORE.STUDENT_DATA d ON d.STUDENT_ID = u.student_id
                WHERE d.IS_DELETED = 0 AND d.STUDY_COUNTRY_IN IS NOT NULL AND d.STUDY_COUNTRY_IN > 0
                ORDER BY u.student_user_id
                """));
        studentUserId = (Integer) s.get("student_user_id");
        studentId = (Integer) s.get("student_id");
        countryId = (Integer) s.get("STUDY_COUNTRY_IN");
        username = "s11-" + System.nanoTime() + "@example.invalid";
        TenantContext.runAsSystem(() -> jdbc.update("""
                UPDATE STUDENTCORE.student_users
                   SET username = ?, password_hash = ?, status = ?, is_deleted = 0, failed_login_attempts = 0,
                       email_verified_at = NULL, totp_enabled = 0, totp_secret_enc = NULL, totp_enrolled_at = NULL,
                       totp_failed_attempts = 0, totp_locked_until = NULL, last_login_at = NULL
                 WHERE student_user_id = ?
                """, username, new BCryptPasswordEncoder().encode(PASSWORD), PortalAccountStatus.ACTIVE, studentUserId));
        em.clear();
    }

    private StudentUserAuthService.LoginStep login(String emailCode, String totpCode) {
        return TenantContext.runAsSystem(() -> auth.login(username, PASSWORD, countryId, emailCode, totpCode));
    }

    private String pendingVerificationCode() {
        // The code is not exposed; read it the way the service stores it, through the test hook.
        return TenantContext.runAsSystem(() -> otp.peek("verify:" + username));
    }

    @Test
    @DisplayName("the first sign-in of an unverified account needs the e-mailed code, once")
    void emailVerification() {
        StudentUserAuthService.LoginStep first = login(null, null);
        assertThat(first.step()).isEqualTo(StudentUserAuthService.Step.VERIFY_EMAIL);
        assertThat(first.result()).isNull();
        assertThat(otp.hasPending("verify:" + username)).isTrue();

        assertThatThrownBy(() -> login("000000", null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("verification code");

        String code = pendingVerificationCode();
        assertThat(code).hasSize(6);
        StudentUserAuthService.LoginStep done = login(code, null);
        assertThat(done.step()).isEqualTo(StudentUserAuthService.Step.OK);
        assertThat(done.result().token()).isNotBlank();
        em.clear();
        assertThat(users.findById(studentUserId).orElseThrow().getEmailVerifiedAt()).isNotNull();

        // Verified: the next sign-in is plain.
        assertThat(login(null, null).step()).isEqualTo(StudentUserAuthService.Step.OK);
        // And the pre-S-11 overload still works for a verified account.
        assertThat(TenantContext.runAsSystem(() -> auth.login(username, PASSWORD, countryId))).isPresent();
    }

    @Test
    @DisplayName("the second factor, once on, is asked for at sign-in and checked")
    void secondFactor() {
        users.findById(studentUserId).ifPresent(u -> { u.setEmailVerifiedAt(Instant.now()); users.saveAndFlush(u); });
        assertThat(mfa.status(studentUserId).totpEnabled()).isFalse();

        Map<String, String> start = mfa.start(studentUserId);
        assertThat(start.get("qrPng")).startsWith("data:image/png;base64,");
        assertThat(start.get("otpauthUri")).contains("Student%20Portal").contains(username.replace("@", "%40"));
        assertThatThrownBy(() -> mfa.confirm(studentUserId, "123456")).isInstanceOf(ResponseStatusException.class);
        mfa.confirm(studentUserId, totp.code(start.get("secret"), totp.currentStep()));
        assertThat(mfa.status(studentUserId).totpEnabled()).isTrue();

        StudentUserAuthService.LoginStep challenge = login(null, null);
        assertThat(challenge.step()).isEqualTo(StudentUserAuthService.Step.TOTP_REQUIRED);
        assertThatThrownBy(() -> login(null, "000000")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("authenticator");
        StudentUserAuthService.LoginStep ok = login(null, totp.code(start.get("secret"), totp.currentStep()));
        assertThat(ok.step()).isEqualTo(StudentUserAuthService.Step.OK);

        // Off again needs a valid code too.
        assertThatThrownBy(() -> mfa.disable(studentUserId, "000000")).isInstanceOf(ResponseStatusException.class);
        mfa.disable(studentUserId, totp.code(start.get("secret"), totp.currentStep()));
        assertThat(mfa.status(studentUserId).totpEnabled()).isFalse();
        assertThat(login(null, null).step()).isEqualTo(StudentUserAuthService.Step.OK);
    }

    @Test
    @DisplayName("a portal account with no sign-in for a year is set INACTIVE; a recent one is left")
    void dormantRule() {
        TenantContext.runAsSystem(() -> jdbc.update(
                "UPDATE STUDENTCORE.student_users SET last_login_at = ?, created_at = ? WHERE student_user_id = ?",
                java.sql.Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS)), java.sql.Timestamp.from(Instant.now().minus(500, ChronoUnit.DAYS)), studentUserId));
        int disabled = TenantContext.runAsSystem(() -> dormant.run(LocalDateTime.now()));
        assertThat(disabled).isGreaterThanOrEqualTo(1);
        em.clear();
        assertThat(users.findById(studentUserId).orElseThrow().getStatus()).isEqualTo(PortalAccountStatus.INACTIVE);
        assertThat(login(null, null)).as("an inactive account cannot sign in").isNull();

        TenantContext.runAsSystem(() -> jdbc.update(
                "UPDATE STUDENTCORE.student_users SET status = ?, last_login_at = SYSUTCDATETIME() WHERE student_user_id = ?",
                PortalAccountStatus.ACTIVE, studentUserId));
        TenantContext.runAsSystem(() -> dormant.run(LocalDateTime.now()));
        em.clear();
        assertThat(users.findById(studentUserId).orElseThrow().getStatus()).isEqualTo(PortalAccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("a portal download is logged under the student account, in the student's country")
    void downloadIsLogged() {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_USER_ID, studentUserId);
        request.setAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_ID, studentId);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            com.dilaw.mohe.audit.ReportAccessLog row = TenantContext.runAs(
                    TenantContext.Scope.of(false, List.of(countryId)),
                    () -> reportAccessLog.prepare("PORTAL_DOCUMENT", "DOWNLOAD", "id=42"));
            assertThat(row.getUsername()).isEqualTo("portal:student-user:" + studentUserId);
            assertThat(row.getStudyCountryInId()).isEqualTo(countryId);
            assertThat(row.getExportFormat()).isEqualTo("DOWNLOAD");
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Autowired private com.dilaw.mohe.audit.ReportAccessLogService reportAccessLog;
}
