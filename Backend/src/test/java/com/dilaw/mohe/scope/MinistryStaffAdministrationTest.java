package com.dilaw.mohe.scope;

import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.service.UmsUserService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security plan N-01 (6 Sep 2026): a ministry user sees every attaché's staff and
 * administers none of them.
 *
 * <p>Before this pass {@code IsMinistryUser} granted {@code ROLE_ADMIN}, which
 * satisfies every user-management expression, and {@code StaffCountrySpec}
 * clause 1 made every account visible — so a ministry officer could edit,
 * delete and reset the password of any attaché's staff. The rule the owner
 * stated is that only the ministry admin and the super admin do that.
 *
 * <p>No seeded account carries the flag, so the test inserts one, uses it, and
 * removes it with its audit rows in a finally. The only row it edits is its own.
 */
@Tag("integration")
@SpringBootTest
class MinistryStaffAdministrationTest {

    /** jo_staff — a country officer any admin can see. */
    private static final long JORDAN_STAFF = 14L;

    @Autowired private UmsUserService userService;
    @Autowired private JdbcTemplate jdbc;

    private static void assertForbidden(String what, ThrowingCallable call) {
        assertThatThrownBy(call)
                .as(what)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private long insertMinistryProbe() {
        jdbc.update("""
                INSERT INTO UMSCORE.Users (UserName, PasswordHash, Email, FirstName, LastName,
                                           Status, IsAdmin, IsSuperAdmin, IsMinistryUser, IsDeleted, CreatedAt)
                VALUES (N'ministry_probe', N'x', N'ministry_probe@test', N'Ministry', N'Probe',
                        N'ACTIVE', 0, 0, 1, 0, SYSUTCDATETIME())
                """);
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE UserName = N'ministry_probe'", Long.class);
    }

    private void removeProbe(long id) {
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE UserId = ?", id);
        jdbc.update("DELETE FROM UMSCORE.UserCountries WHERE UserId = ?", id);
        jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = N'Users' AND RECORD_ID = ?", String.valueOf(id));
        jdbc.update("DELETE FROM UMSCORE.Users WHERE Id = ?", id);
    }

    @Test
    @DisplayName("a ministry user sees every account but may not edit, delete or create one")
    void ministryUserIsReadOnlyOverStaff() {
        long probe = insertMinistryProbe();
        try {
            List<String> visible = AsUser.call(probe, () -> userService.listUsers(null, null, null, PageRequest.of(0, 100)))
                    .getContent().stream().map(UmsUser::getUserName).toList();
            assertThat(visible)
                    .as("clause 1 of StaffCountrySpec still holds: the ministry sees everyone")
                    .contains("jo_staff", "sa_staff", "admin");

            UmsUser patch = new UmsUser();
            patch.setFirstName("Renamed");
            assertForbidden("ministry user editing jo_staff",
                    () -> AsUser.call(probe, () -> userService.updateUser(JORDAN_STAFF, patch, probe)));
            assertForbidden("ministry user deleting jo_staff",
                    () -> AsUser.call(probe, () -> { userService.deleteUser(JORDAN_STAFF, probe); return null; }));
            assertForbidden("ministry user resetting jo_staff's password",
                    () -> AsUser.call(probe, () -> { userService.changePassword(JORDAN_STAFF, "Probe-Passw0rd-1", probe); return null; }));

            UmsUser fresh = new UmsUser();
            fresh.setUserName("ministry_probe_child");
            fresh.setEmail("child@test");
            fresh.setFirstName("Child");
            fresh.setLastName("Probe");
            fresh.setPassword("Probe-Passw0rd-1");
            assertForbidden("ministry user creating an account",
                    () -> AsUser.call(probe, () -> userService.createUser(fresh, probe)));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.Users WHERE UserName = N'ministry_probe_child'", Integer.class))
                    .as("refused before the insert")
                    .isZero();

            // Positive control: the same account may still edit its own row.
            UmsUser self = new UmsUser();
            self.setFirstName("Ministry2");
            UmsUser saved = AsUser.call(probe, () -> userService.updateUser(probe, self, probe));
            assertThat(saved.getFirstName()).isEqualTo("Ministry2");

            // And jo_staff was not touched by any of the refused calls.
            assertThat(jdbc.queryForObject("SELECT FirstName FROM UMSCORE.Users WHERE Id = ?", String.class, JORDAN_STAFF))
                    .isNotEqualTo("Renamed");
        } finally {
            removeProbe(probe);
        }
    }

    @Test
    @DisplayName("the super admin and the all-country admin keep administering attaché staff")
    void ministryAdminsStillAdminister() {
        long probe = insertMinistryProbe();
        try {
            // Acting on the probe rather than on seeded staff so nothing persistent changes.
            UmsUser patch = new UmsUser();
            patch.setFirstName("BySuper");
            assertThat(AsUser.call(AsUser.SUPERADMIN, () -> userService.updateUser(probe, patch, AsUser.SUPERADMIN)).getFirstName())
                    .isEqualTo("BySuper");
            UmsUser patch2 = new UmsUser();
            patch2.setFirstName("ByAdmin");
            assertThat(AsUser.call(AsUser.ADMIN, () -> userService.updateUser(probe, patch2, AsUser.ADMIN)).getFirstName())
                    .isEqualTo("ByAdmin");
        } finally {
            removeProbe(probe);
        }
    }
}
