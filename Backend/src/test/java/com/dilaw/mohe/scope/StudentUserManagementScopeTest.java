package com.dilaw.mohe.scope;

import com.dilaw.mohe.studentportal.StudentPermissionAdminController;
import com.dilaw.mohe.studentportal.StudentPortalAdminService;
import com.dilaw.mohe.studentportal.StudentRoleAdminController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code /student-admin/*} — the 941 portal accounts, and the definitions behind
 * them.
 *
 * <p>Every portal account currently belongs to an Egyptian student, so the
 * expected result for a Jordan officer is <b>zero</b>, not "fewer". That makes
 * this suite unusually blunt and unusually easy to get wrong in the vacuous
 * direction: a filter that is broken and returns nothing would pass the denial
 * assertions on its own. Each one is therefore paired with a ministry positive
 * control asserting the same call returns all 941.
 *
 * <p>Read-only.
 */
@Tag("integration")
@SpringBootTest
class StudentUserManagementScopeTest {

    @Autowired private StudentPortalAdminService adminService;
    @Autowired private StudentRoleAdminController roleController;
    @Autowired private StudentPermissionAdminController permissionController;
    @Autowired private JdbcTemplate jdbc;

    private long portalUsersVisibleTo(long userId) {
        return AsUser.call(userId, () -> adminService.users(null, null, PageRequest.of(0, 10)))
                .getTotalElements();
    }

    @Test
    @DisplayName("the ministry sees every portal account")
    void ministrySeesAll() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM STUDENTCORE.student_users WHERE is_deleted = 0", Integer.class);
        assumeTrue(total != null && total > 0, "no portal accounts");

        assertThat(portalUsersVisibleTo(AsUser.SUPERADMIN)).isEqualTo(total.longValue());
    }

    @Test
    @DisplayName("an attaché with no portal students sees none of them")
    void jordanSeesNone() {
        Integer egyptian = jdbc.queryForObject("""
                SELECT COUNT(*) FROM STUDENTCORE.student_users su
                JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
                WHERE su.is_deleted = 0 AND sd.STUDY_COUNTRY_IN = 1
                """, Integer.class);
        assumeTrue(egyptian != null && egyptian > 0, "no Egyptian portal accounts");

        assertThat(portalUsersVisibleTo(AsUser.JO_ADMIN))
                .as("""
                    Every portal account belongs to an Egyptian student, so zero is the correct \
                    answer for Jordan — and the ministry control above is what stops this passing \
                    because the query is simply broken.""")
                .isZero();
        assertThat(portalUsersVisibleTo(AsUser.SA_ADMIN)).isZero();
    }

    @Test
    @DisplayName("reading one portal account by id is 404, not a different answer from the list")
    void detailAgreesWithList() {
        Integer someEgyptian = jdbc.queryForObject("""
                SELECT TOP 1 su.student_user_id FROM STUDENTCORE.student_users su
                JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
                WHERE su.is_deleted = 0 AND sd.STUDY_COUNTRY_IN = 1
                """, Integer.class);
        assumeTrue(someEgyptian != null, "no Egyptian portal accounts");

        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN, () -> adminService.requireUser(someEgyptian)))
                .as("""
                    Not found rather than forbidden: these ids are sequential, and what a 403 \
                    would confirm is that a particular person studies abroad.""")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> adminService.requireUser(someEgyptian)))
                .as("positive control")
                .isNotNull();
    }

    @Test
    @DisplayName("a country officer cannot reset another attaché's student password")
    void cannotReachAnotherAttachesResetPassword() {
        Integer someEgyptian = jdbc.queryForObject("""
                SELECT TOP 1 su.student_user_id FROM STUDENTCORE.student_users su
                JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = su.student_id
                WHERE su.is_deleted = 0 AND sd.STUDY_COUNTRY_IN = 1
                """, Integer.class);
        assumeTrue(someEgyptian != null, "no Egyptian portal accounts");

        String before = jdbc.queryForObject(
                "SELECT password_hash FROM STUDENTCORE.student_users WHERE student_user_id = ?",
                String.class, someEgyptian);

        // requireUser is the gate the reset path calls first. STUDENT_LOGIN.RESET
        // is held by every country admin, so this is the only thing between
        // jo_admin and 941 Egyptian students' portal accounts.
        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN, () -> adminService.requireUser(someEgyptian)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM STUDENTCORE.student_users WHERE student_user_id = ?",
                String.class, someEgyptian))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("portal sessions are scoped by the country pinned at login")
    void sessionsAreScoped() {
        long ministry = AsUser.call(AsUser.SUPERADMIN,
                () -> adminService.sessions(null, false, PageRequest.of(0, 100))).getTotalElements();
        assumeTrue(ministry > 0, "no portal sessions");

        assertThat(AsUser.call(AsUser.JO_ADMIN,
                () -> adminService.sessions(null, false, PageRequest.of(0, 100))).getTotalElements())
                .as("all fifteen sessions carry study_country_in_id = 1")
                .isZero();
    }

    @Test
    @DisplayName("purging expired sessions is ministry-only, and says so")
    void purgeIsMinistryOnly() {
        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN, () -> adminService.requireMinistry("Purging")))
                .as("""
                    Forbidden rather than quietly scoped down. A partial purge leaves the table \
                    in a state nobody asked for, and makes the next run's result depend on who \
                    pressed the button.""")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        AsUser.run(AsUser.SUPERADMIN, () -> adminService.requireMinistry("Purging"));
    }

    /** Enough to pass the controllers' @PreAuthorize, so country scope is what is being tested. */
    private static final java.util.List<String> CAN_VIEW_STUDENTS = java.util.List.of("STUDENTS.VIEW");

    @Test
    @DisplayName("roles and permissions stay visible to everyone, on purpose")
    void definitionsAreNotScoped() {
        int ministryRoles = AsUser.callWithAuthorities(
                AsUser.SUPERADMIN, CAN_VIEW_STUDENTS, () -> roleController.list()).size();
        int jordanRoles = AsUser.callWithAuthorities(
                AsUser.JO_ADMIN, CAN_VIEW_STUDENTS, () -> roleController.list()).size();

        assertThat(jordanRoles)
                .as("""
                    Not an oversight. A role is the vocabulary the portal's permissions are \
                    written in, shared by every attaché, and it names no student. Scoping it \
                    would leave a country officer with an empty list and nothing to assign — \
                    breaking the screen rather than protecting it, the same call as the \
                    الدليل المحاسبي الموحد.""")
                .isEqualTo(ministryRoles)
                .isGreaterThan(0);

        assertThat(AsUser.callWithAuthorities(AsUser.JO_ADMIN, CAN_VIEW_STUDENTS,
                        () -> permissionController.list()).size())
                .isEqualTo(AsUser.callWithAuthorities(AsUser.SUPERADMIN, CAN_VIEW_STUDENTS,
                        () -> permissionController.list()).size());
    }
}
