package com.dilaw.mohe.scope;

import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.service.UmsUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The escalation this round was really about.
 *
 * <p>{@code jo_admin} holds {@code UMS_ADMIN.EDIT}, which satisfies
 * {@code MoheSecurityExpressions.UMS_EDIT}. So before this work
 *
 * <pre>POST /api/v1/ums/users/1/change-password</pre>
 *
 * succeeded and handed them the {@code superadmin} account — no country scope
 * involved, just a path variable and a permission every country admin already
 * has.
 *
 * <p>Two barriers now stand in front of it and this suite checks both, separately,
 * because they fail for different reasons and either could be removed by a later
 * change without the other noticing:
 *
 * <ol>
 *   <li>{@code StaffCountrySpec} hides the account, so {@code require} raises
 *       not-found before the guard is reached;
 *   <li>{@code UmsPrivilegeGuard} refuses the write outright, and does not depend
 *       on {@code UserCountries} holding correct data — which matters, because the
 *       country picker was writing wrong ids into it until this round.
 * </ol>
 *
 * <p>Read-only: every assertion here is that a write did <em>not</em> happen.
 */
@Tag("integration")
@SpringBootTest
class StaffPrivilegeGuardTest {

    @Autowired private UmsUserService userService;
    @Autowired private JdbcTemplate jdbc;

    private String passwordHashOf(long userId) {
        return jdbc.queryForObject("SELECT PasswordHash FROM UMSCORE.Users WHERE Id = ?", String.class, userId);
    }

    @Test
    @DisplayName("a country admin cannot reset the super admin's password")
    void cannotResetSuperAdminPassword() {
        String before = passwordHashOf(AsUser.SUPERADMIN);

        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN,
                () -> userService.changePassword(AsUser.SUPERADMIN, "taken-over", AsUser.JO_ADMIN)))
                .as("this is the call that used to succeed")
                .isInstanceOfAny(ResourceNotFoundException.class, ResponseStatusException.class);

        assertThat(passwordHashOf(AsUser.SUPERADMIN))
                .as("""
                    The assertion that actually matters. An exception type can be right while \
                    the write has already happened — the transaction is what is being checked \
                    here, not the error.""")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("the guard holds even when the country gate would have let it through")
    void guardIsIndependentOfCountryScope() {
        // sa_superadmin has no countries, so clause 3 makes it visible to jo_admin:
        // the country gate passes and the privilege guard is the only thing left.
        String before = passwordHashOf(AsUser.SA_SUPERADMIN);

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> userService.getUserById(AsUser.SA_SUPERADMIN)))
                .as("precondition — it really is visible, so this is testing the second barrier")
                .isPresent();

        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN,
                () -> userService.changePassword(AsUser.SA_SUPERADMIN, "taken-over", AsUser.JO_ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        assertThat(passwordHashOf(AsUser.SA_SUPERADMIN)).isEqualTo(before);
    }

    @Test
    @DisplayName("a country admin cannot delete or edit a super admin either")
    void cannotDeleteOrEditSuperAdmin() {
        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN,
                () -> userService.deleteUser(AsUser.SA_SUPERADMIN, AsUser.JO_ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        UmsUser edit = new UmsUser();
        edit.setFirstName("Renamed");
        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN,
                () -> userService.updateUser(AsUser.SA_SUPERADMIN, edit, AsUser.JO_ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        assertThat(jdbc.queryForObject(
                "SELECT COALESCE(IsDeleted, 0) FROM UMSCORE.Users WHERE Id = ?", Boolean.class, AsUser.SA_SUPERADMIN))
                .as("still there")
                .isFalse();
    }

    @Test
    @DisplayName("a super admin may administer a super admin")
    void superAdminMayAdministerSuperAdmin() {
        // The positive control. Without it every assertion above could be passing
        // because the method is broken for everyone rather than guarded.
        assertThatCode(() -> AsUser.run(AsUser.SUPERADMIN,
                () -> userService.getUserById(AsUser.SA_SUPERADMIN)))
                .doesNotThrowAnyException();

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> userService.getUserById(AsUser.SA_SUPERADMIN)))
                .isPresent();
    }

    @Test
    @DisplayName("nobody may delete their own account")
    void cannotDeleteSelf() {
        assertThatThrownBy(() -> AsUser.run(AsUser.SUPERADMIN,
                () -> userService.deleteUser(AsUser.SUPERADMIN, AsUser.SUPERADMIN)))
                .as("the one delete with no way back — the account that could undo it is the one deleted")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        assertThat(jdbc.queryForObject(
                "SELECT COALESCE(IsDeleted, 0) FROM UMSCORE.Users WHERE Id = ?", Boolean.class, AsUser.SUPERADMIN))
                .isFalse();
    }

    @Test
    @DisplayName("an admin cannot hand out an attaché they do not hold")
    void cannotAssignACountryYouDoNotHold() {
        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN,
                () -> userService.assignCountryToUser(AsUser.SA_SUPERADMIN, 1005, AsUser.JO_ADMIN)))
                .as("""
                    Otherwise the gate can be widened from inside it: assign yourself, or someone \
                    you already administer, an attaché you were never given.""")
                .isInstanceOf(Exception.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserCountries WHERE UserId = ? AND CountryId = 1005 AND IsActive = 1",
                Integer.class, AsUser.SA_SUPERADMIN))
                .isZero();
    }
}
