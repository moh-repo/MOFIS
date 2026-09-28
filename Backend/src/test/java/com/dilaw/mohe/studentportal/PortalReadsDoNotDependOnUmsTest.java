package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentFinanceService;
import com.dilaw.mohe.service.StudentProfileService;
import com.dilaw.mohe.service.StudentProgressService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A student reading their own record must not depend on a UMS user existing.
 *
 * <p>The student portal is a separate realm: identity comes from
 * {@code X-Student-Portal-Token}, resolved by
 * {@link StudentPortalTokenInterceptor}. But its endpoints called ministry
 * services, and those authorise against the UMS {@code SecurityContext} —
 * {@code CountryScopeService.canAccessCountry} plus a {@code recordVisibility}
 * check against an admin flag.
 *
 * <p>So the portal took <em>identity</em> from the portal token and
 * <em>permission</em> from whichever staff account happened to be logged in to
 * the same browser, because the frontend attached that JWT to portal requests
 * too. Measured against a running server, one student with one valid portal
 * token:
 *
 * <pre>
 *   nobody logged in .................. 403 Access denied: country not in scope
 *   ministry staff logged in (c. 1) ... 200
 *   staff scoped to country 1005 ...... 403
 * </pre>
 *
 * <p>These tests run with an empty SecurityContext, which is what a real portal
 * request has. They also assert the ministry methods still refuse — the fix adds
 * a portal path, it does not weaken the staff one.
 */
@Tag("integration")
@SpringBootTest
class PortalReadsDoNotDependOnUmsTest {

    @Autowired private StudentDataService studentDataService;
    @Autowired private StudentFinanceService studentFinanceService;
    @Autowired private StudentProgressService studentProgressService;
    @Autowired private StudentProfileService studentProfileService;
    @Autowired private JdbcTemplate jdbc;

    private Integer studentId;

    @BeforeEach
    void noUmsUser() {
        // Exactly what a student-portal request carries: no UMS authentication.
        SecurityContextHolder.clearContext();

        studentId = jdbc.queryForObject("""
                SELECT TOP 1 su.student_id
                FROM STUDENTCORE.student_users su
                JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
                WHERE sd.STUDY_COUNTRY_IN IS NOT NULL AND ISNULL(sd.IS_DELETED, 0) = 0
                ORDER BY su.student_user_id
                """, Integer.class);
        assumeTrue(studentId != null, "no portal account joined to a student with a country");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the portal can read the student's own record with no UMS user present")
    void portalRead_worksWithoutAUmsUser() {
        assertThatCode(() -> studentDataService.getStudentDataForPortal(studentId))
                .as("this is the call behind /me and /me/data. It threw 403 "
                        + "'Access denied: country not in scope' for every student whose browser "
                        + "had no staff session")
                .doesNotThrowAnyException();

        assertThat(studentDataService.getStudentDataForPortal(studentId).getStudentId())
                .as("and it must return the student the token identified, nobody else")
                .isEqualTo(studentId);
    }

    @Test
    @DisplayName("progress and profile work too — they read the student the same way")
    void portalDerivedReads_workWithoutAUmsUser() {
        // /me/progress and /me/profile-summary were 403 for the same reason, one
        // level down: both resolve the student through StudentDataService.
        // Named assertions with the exception attached: a bare
        // doesNotThrowAnyException() reports "AssertionError:" and nothing else,
        // which says a service failed but not which one or why.
        Throwable progress = org.assertj.core.api.Assertions.catchThrowable(
                () -> studentProgressService.getProgressForPortal(studentId));
        assertThat(progress)
                .as("/me/progress must not need a UMS user; got: %s", describe(progress))
                .isNull();

        Throwable profile = org.assertj.core.api.Assertions.catchThrowable(
                () -> studentProfileService.buildProfileForPortal(studentId));
        assertThat(profile)
                .as("/me/profile-summary must not need a UMS user; got: %s", describe(profile))
                .isNull();
    }

    /** Type, message and the frame it came from — enough to act on from the report. */
    private static String describe(Throwable t) {
        if (t == null) return "no exception";
        StackTraceElement[] st = t.getStackTrace();
        String at = st.length > 0 ? st[0].toString() : "?";
        return t.getClass().getSimpleName() + ": " + t.getMessage() + "  at " + at;
    }

    @Test
    @DisplayName("finance reads without a UMS user instead of being silently swallowed")
    void portalFinance_isNotSilentlyDenied() {
        // The portal's call sat inside a catch that turned the 403 into
        // finance: null, so /me/data looked like it worked and simply reported
        // that the student had no finance record.
        assertThatCode(() -> {
            try {
                studentFinanceService.getByIdForPortal(studentId);
            } catch (ResponseStatusException ex) {
                // A student with no finance row is a 404 and entirely normal; a
                // 403 is the bug.
                assertThat(ex.getStatusCode().value())
                        .as("a portal finance read must never be refused on country scope")
                        .isNotEqualTo(403);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the ministry methods still refuse a caller with no scope")
    void ministryReads_remainGuarded() {
        // The point of a separate portal path is that the staff path keeps its
        // checks. If this stops throwing, the fix has been applied by deleting
        // the guard rather than by routing around it.
        assertThatThrownBy(() -> studentDataService.getStudentData(studentId))
                .as("StudentDataService.getStudentData is the ministry entry point and must "
                        + "still consult CountryScopeService")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("country not in scope");

        assertThatThrownBy(() -> studentProgressService.getProgress(studentId))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> studentProfileService.buildProfile(studentId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("no portal endpoint routes through a UMS-gated service")
    void portalController_usesOnlyPortalMethods() throws Exception {
        // The rule is easy to break by copying a line from the ministry
        // controller, and the result only shows up for users who have no staff
        // session — which is nobody on a developer's machine.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/dilaw/mohe/studentportal/StudentPortalMeController.java"));

        assertThat(source)
                .as("StudentPortalMeController must call the *ForPortal methods only")
                .doesNotContain("studentDataService.getStudentData(")
                .doesNotContain("studentFinanceService.getById(")
                .doesNotContain("studentProgressService.getProgress(")
                .doesNotContain("studentProfileService.buildProfile(");

        assertThat(source)
                .as("and it should still be calling them — an empty controller would pass the above")
                .contains("getStudentDataForPortal(");
    }
}
