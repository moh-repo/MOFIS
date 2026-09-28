package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Student-portal login: the country check, and the RLS bug it sits next to.
 *
 * <p>The portal authenticates against STUDENTCORE, a realm entirely separate
 * from UMSCORE — its own users, roles, sessions — holding no country of its own.
 * A portal user's country exists only as
 * {@code student_id -> STUDENT_DATA.STUDY_COUNTRY_IN}, and STUDENT_DATA is
 * behind the V031 policy while STUDENTCORE is not.
 *
 * <p>That asymmetry broke login outright. A login request carries no tenant
 * scope — there is no token yet to resolve one from — so with the policy enabled
 * the password check passed against STUDENTCORE and the profile lookup then
 * returned nothing, refusing a correct credential with 404 "Student record not
 * found". {@code StudentUserAuthService} resolves that read as system now, and
 * {@link #loginWorks_whileThePolicyIsEnabled} is the test that would have caught
 * it.
 *
 * <p><b>Not {@code @Transactional}.</b> It was, and the policy test could not
 * pass: a test transaction borrows its connection when the test method starts,
 * and {@link com.dilaw.mohe.config.tenant.TenantContextDataSource} stamps the
 * scope at that moment — so nothing the test did afterwards could change the
 * scope the connection was already carrying. Reproducing production requires
 * each service call to open its own transaction, which means committing, which
 * means cleaning up by hand. {@link #restoreAccount()} does that.
 */
@Tag("integration")
@SpringBootTest
class StudentPortalLoginCountryTest {

    private static final String PASSWORD = "P@ssw0rd-test";

    @Autowired private StudentUserAuthService authService;
    @Autowired private StudentUserRepository studentUserRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.dilaw.mohe.scope.DdlJdbc ddl;
    @Autowired private com.dilaw.mohe.config.tenant.TenantContextFilter tenantContextFilter;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Set by the fixture so {@link #restoreAccount()} can put the account back. */
    private Integer borrowedUserId;
    private String originalPasswordHash;
    /** Highest session id before the test ran; only rows above it are ours to delete. */
    private Integer sessionHighWaterMark;

    @AfterEach
    void restoreAccount() {
        TenantContext.clear();
        if (borrowedUserId == null) {
            return;
        }
        // Only the sessions this test minted. The first version deleted every
        // session for the borrowed account and destroyed six pre-existing rows —
        // they were expired, so nobody was logged out, but "clean up after
        // yourself" has to mean only what you created.
        jdbc.update("DELETE FROM STUDENTCORE.student_sessions WHERE student_user_id = ? AND session_id > ?",
                borrowedUserId, sessionHighWaterMark);
        jdbc.update("UPDATE STUDENTCORE.student_users SET password_hash = ? WHERE student_user_id = ?",
                originalPasswordHash, borrowedUserId);
        borrowedUserId = null;
        originalPasswordHash = null;
        sessionHighWaterMark = null;
    }

    /** An existing portal account, with a password this test knows. */
    private record Fixture(Integer studentId, Integer countryId, String username) {}

    /**
     * Borrows a real portal account rather than creating one.
     *
     * <p>The first version of this created a new {@code student_users} row for a
     * student that had none — and skipped every test, because all 941 students
     * already have an account ({@code student_id} is unique, so there was never a
     * candidate). A test that always skips reports success while asserting
     * nothing.
     *
     * <p>So it takes an existing account and sets a known password on it. The
     * class is {@code @Transactional} and rolls back, so the real hash is
     * untouched once the test finishes.
     */
    private Fixture givenAPortalAccount() {
        var rows = jdbc.queryForList("""
                SELECT TOP 1 su.student_user_id, su.student_id, sd.STUDY_COUNTRY_IN
                FROM STUDENTCORE.student_users su
                JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
                WHERE su.is_deleted = 0
                  AND su.status = 'ACTIVE'
                  AND sd.STUDY_COUNTRY_IN IS NOT NULL
                  AND ISNULL(sd.IS_DELETED, 0) = 0
                ORDER BY su.student_user_id
                """);
        assumeTrue(!rows.isEmpty(), "no active portal account joined to a student with a country");

        Integer userId    = ((Number) rows.get(0).get("student_user_id")).intValue();
        Integer studentId = ((Number) rows.get(0).get("student_id")).intValue();
        Integer countryId = ((Number) rows.get(0).get("STUDY_COUNTRY_IN")).intValue();

        StudentUser user = studentUserRepository.findById(userId).orElseThrow();
        borrowedUserId = userId;
        originalPasswordHash = user.getPasswordHash();
        sessionHighWaterMark = jdbc.queryForObject(
                "SELECT ISNULL(MAX(session_id), 0) FROM STUDENTCORE.student_sessions", Integer.class);

        // Committed, not rolled back — see the class javadoc. restoreAccount()
        // puts the real hash back after every test, pass or fail.
        jdbc.update("UPDATE STUDENTCORE.student_users SET password_hash = ? WHERE student_user_id = ?",
                encoder.encode(PASSWORD), userId);

        return new Fixture(studentId, countryId, user.getUsername());
    }

    @Test
    @DisplayName("login succeeds when the selected country is the student's own")
    void login_succeedsWithTheStudentsOwnCountry() {
        Fixture f = givenAPortalAccount();

        Optional<StudentUserAuthService.LoginResult> result =
                authService.login(f.username(), PASSWORD, f.countryId());

        assertThat(result).as("correct credentials and the correct country must be accepted").isPresent();
        assertThat(result.get().studentId()).isEqualTo(f.studentId());
        assertThat(result.get().token()).isNotBlank();
    }

    @Test
    @DisplayName("the session carries the country, so later requests need no lookup")
    void login_pinsTheCountryOnTheSession() {
        Fixture f = givenAPortalAccount();

        String token = authService.login(f.username(), PASSWORD, f.countryId()).orElseThrow().token();

        Integer pinned = jdbc.queryForObject(
                "SELECT study_country_in_id FROM STUDENTCORE.student_sessions WHERE token = ?",
                Integer.class, com.dilaw.mohe.util.PortalTokens.hash(token)); // O-06: the row holds the hash

        assertThat(pinned)
                .as("V036 pins the scope at login so the interceptor reads it from the row it "
                        + "already loads, instead of querying STUDENT_DATA under elevation per request")
                .isEqualTo(f.countryId());

        // And the interceptor turns that into a scope without touching the database.
        assertThat(authService.resolveFromToken(token).orElseThrow().studyCountryInId())
                .isEqualTo(f.countryId());
    }

    @Test
    @DisplayName("login is refused when the selected country is not the student's")
    void login_isRefusedWithAnotherCountry() {
        Fixture f = givenAPortalAccount();
        int otherCountry = f.countryId() + 1;

        assertThat(authService.login(f.username(), PASSWORD, otherCountry))
                .as("the country is verified against the record, not merely recorded")
                .isEmpty();
    }

    @Test
    @DisplayName("a wrong country is indistinguishable from a wrong password")
    void login_failsIdentically_forWrongCountryAndWrongPassword() {
        Fixture f = givenAPortalAccount();

        Optional<StudentUserAuthService.LoginResult> wrongCountry =
                authService.login(f.username(), PASSWORD, f.countryId() + 1);
        Optional<StudentUserAuthService.LoginResult> wrongPassword =
                authService.login(f.username(), "not-the-password", f.countryId());
        Optional<StudentUserAuthService.LoginResult> noCountry =
                authService.login(f.username(), PASSWORD, null);

        // If these differed, a caller holding a valid username could discover the
        // student's country by trying values until the response changed. The
        // controller renders all three as the same 401.
        assertThat(wrongCountry).as("wrong country must not be reported distinctly").isEmpty();
        assertThat(wrongPassword).isEmpty();
        assertThat(noCountry).as("a missing country is a failed login, not a 400").isEmpty();
    }

    @Test
    @DisplayName("login works with the row-level security policy enabled")
    void loginWorks_whileThePolicyIsEnabled() {
        Fixture f = givenAPortalAccount();

        // The regression this whole change exists for. With the policy on and no
        // session context — the exact state of a login request — STUDENT_DATA
        // returns nothing while STUDENTCORE.student_users returns everything, so
        // the password check passes and only the profile lookup fails.
        // Put back exactly as found: forcing it OFF here (as the first version did)
        // switched row-level security off on the dev database on every full run.
        final boolean policyWasEnabled = policyEnabled() == 1;
        setPolicy(true);
        try {
            // TenantContextFilter grants SYSTEM to the portal auth paths; this
            // stands in for it, and works only because the test holds no
            // transaction of its own — login() opens one, and borrows its
            // connection while this scope is in force. An earlier version
            // elevated inside the service instead and this test failed, which is
            // how the ordering problem was found.
            Optional<StudentUserAuthService.LoginResult> result = TenantContext.runAsSystem(
                    () -> authService.login(f.username(), PASSWORD, f.countryId()));

            assertThat(result)
                    .as("a student with correct credentials was refused with 404 "
                            + "'Student record not found' because the profile lookup ran unscoped")
                    .isPresent();
        } finally {
            // Restored whatever happened above, to the state the database had
            // before this test — not to OFF.
            setPolicy(policyWasEnabled);
        }

        assertThat(policyEnabled())
                .as("the policy must be left in the state it was found")
                .isEqualTo(policyWasEnabled ? 1 : 0);
    }

    @Test
    @DisplayName("the filter grants system scope to exactly the portal auth paths")
    void filterElevates_theLoginPathAndNothingElse() {
        // The elevation above is only correct if the filter actually covers the
        // URL the login form posts to. Asserting the service works under
        // runAsSystem proves nothing if production never calls it that way.
        assertThat(portalAuthElevation("/mohe/api/v1/student-portal/auth/login"))
                .as("the login endpoint must run as system or it cannot read the student record")
                .isTrue();
        assertThat(portalAuthElevation("/mohe/api/v1/student-portal/auth/reset-password")).isTrue();

        assertThat(portalAuthElevation("/mohe/api/v1/student-portal/me"))
                .as("an authenticated portal request must be scoped to its own country, not elevated")
                .isFalse();
        assertThat(portalAuthElevation("/mohe/api/v1/students"))
                .as("ministry endpoints must resolve a real scope")
                .isFalse();
        assertThat(portalAuthElevation("/mohe/api/v1/student-portal/auth-something-else"))
                .as("prefix matching must not leak onto a neighbouring path")
                .isFalse();
    }

    /** Runs the filter over a request for {@code uri} and reports whether it elevated. */
    private boolean portalAuthElevation(String uri) {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", uri);
        request.setContextPath("/mohe");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var seen = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            tenantContextFilter.doFilter(request, response,
                    (req, res) -> seen.set(TenantContext.current().all()));
        } catch (Exception e) {
            throw new IllegalStateException("filter threw for " + uri, e);
        }
        return seen.get();
    }

    private void setPolicy(boolean on) {
        // DDL: through the migrations' login, since mohe_app is denied ALTER ANY SECURITY POLICY (V090).
        ddl.setTenantPolicy(on);
    }

    private Integer policyEnabled() {
        return jdbc.queryForObject(
                "SELECT CAST(is_enabled AS INT) FROM sys.security_policies WHERE name = 'tenant_country_policy'",
                Integer.class);
    }
}
