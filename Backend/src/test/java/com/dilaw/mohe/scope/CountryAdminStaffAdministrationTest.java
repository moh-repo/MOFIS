package com.dilaw.mohe.scope;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.service.UmsUserService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security plan N-02 and N-01.b (6 Sep 2026): what a country administrator may
 * and may not do with staff accounts.
 *
 * <ul>
 *   <li>N-02.a — creates accounts only inside its own attachés, and never an
 *       unassigned one (which every admin could otherwise claim).</li>
 *   <li>N-02.b — hands out only roles, groups and permissions it holds itself,
 *       and never a tier above its own.</li>
 *   <li>N-02.c — a delete ends the account's sessions and records its countries.</li>
 *   <li>N-01.b — a ministry account that happens to be visible (no attaché,
 *       clause 3) is still not a country admin's to administer.</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
class CountryAdminStaffAdministrationTest {

    private static final int JORDAN = 1004;
    private static final int SAUDI = 1005;
    private static final String PROBE = "n02_probe";
    private static final String MINISTRY_PROBE = "n02_ministry_probe";

    @Autowired private UmsUserService userService;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        for (String name : List.of(PROBE, MINISTRY_PROBE)) {
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE ResourceType IN ('USER','USER_ROLE','USER_COUNTRY','USER_PERMISSION') "
                    + "AND ResourceId IN (SELECT Id FROM UMSCORE.Users WHERE Username = ?)", name);
            jdbc.update("DELETE FROM UMSCORE.UserSessions WHERE UserId IN (SELECT Id FROM UMSCORE.Users WHERE Username = ?)", name);
            jdbc.update("DELETE FROM UMSCORE.UserRoles WHERE UserId IN (SELECT Id FROM UMSCORE.Users WHERE Username = ?)", name);
            jdbc.update("DELETE FROM UMSCORE.UserPermissions WHERE UserId IN (SELECT Id FROM UMSCORE.Users WHERE Username = ?)", name);
            jdbc.update("DELETE FROM UMSCORE.UserCountries WHERE UserId IN (SELECT Id FROM UMSCORE.Users WHERE Username = ?)", name);
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = N'Users' AND RECORD_ID IN (SELECT CAST(Id AS NVARCHAR(50)) FROM UMSCORE.Users WHERE Username = ?)", name);
            jdbc.update("DELETE FROM UMSCORE.Users WHERE Username = ?", name);
        }
    }

    private static UmsUser probe(List<Integer> countries) {
        UmsUser u = new UmsUser();
        u.setUserName(PROBE);
        u.setEmail(PROBE + "@test");
        u.setFirstName("N02");
        u.setLastName("Probe");
        u.setPassword("Probe-password-2026");
        u.setStatus(UserStatus.ACTIVE);
        u.setIsAdmin(false);
        u.setCountryIds(countries);
        return u;
    }

    private static void assertForbidden(String what, ThrowingCallable call) {
        assertThatThrownBy(call).as(what)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Long roleId(String name) {
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Roles WHERE RoleName = ?", Long.class, name);
    }

    private Long permissionId(String code) {
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Permissions WHERE PermissionCode = ?", Long.class, code);
    }

    @Test
    @DisplayName("N-02.a: a country admin must place a new account in one of its own attachés")
    void createNeedsOwnCountry() {
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> userService.createUser(probe(List.of()), AsUser.JO_ADMIN)))
                .as("no country: the account would be claimable by any admin")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> userService.createUser(probe(List.of(SAUDI)), AsUser.JO_ADMIN)))
                .as("Saudi is not Jordan's to hand out (R-17: the evaluator's audited 403)")
                .isInstanceOf(com.dilaw.mohe.security.policy.PolicyDenied.class);

        UmsUser created = AsUser.call(AsUser.JO_ADMIN, () -> userService.createUser(probe(List.of(JORDAN)), AsUser.JO_ADMIN));
        List<Integer> countries = jdbc.queryForList(
                "SELECT CountryId FROM UMSCORE.UserCountries WHERE UserId = ? AND IsActive = 1", Integer.class, created.getId());
        assertThat(countries).containsExactly(JORDAN);
        assertThat(created.getCountryIds()).containsExactly(JORDAN);
    }

    @Test
    @DisplayName("N-02.a: the ministry admin may still create an unassigned account")
    void ministryAdminMayCreateUnassigned() {
        UmsUser created = AsUser.call(AsUser.ADMIN, () -> userService.createUser(probe(List.of()), AsUser.ADMIN));
        assertThat(created.getId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserCountries WHERE UserId = ?", Integer.class, created.getId()))
                .isZero();
    }

    @Test
    @DisplayName("N-02.b: a country admin grants only what it holds, and never a tier above its own")
    void grantsOnlyWhatItHolds() {
        UmsUser created = AsUser.call(AsUser.JO_ADMIN, () -> userService.createUser(probe(List.of(JORDAN)), AsUser.JO_ADMIN));
        long id = created.getId();

        assertForbidden("MINISTRY_ADMIN is above a country admin",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> { userService.assignRoleToUser(id, roleId("MINISTRY_ADMIN"), AsUser.JO_ADMIN); return null; }));
        assertForbidden("SUPER_ADMIN is above everyone but a super admin",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> { userService.assignRoleToUser(id, roleId("SUPER_ADMIN"), AsUser.JO_ADMIN); return null; }));
        assertForbidden("Finance Manager is a role jo_admin does not hold",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> { userService.assignRoleToUser(id, roleId("Finance Manager"), AsUser.JO_ADMIN); return null; }));
        assertForbidden("SYSTEM.RESTORE is a code jo_admin does not hold",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> {
                    userService.grantPermissionToUser(id, permissionId("SYSTEM.RESTORE"), AsUser.JO_ADMIN,
                            "test", java.time.LocalDateTime.now().plusDays(1));
                    return null;
                }));

        // What it does hold, it may pass on: COUNTRY_ADMIN of its own attaché.
        AsUser.call(AsUser.JO_ADMIN, () -> { userService.assignRoleToUser(id, roleId("COUNTRY_ADMIN"), AsUser.JO_ADMIN); return null; });
        assertThat(jdbc.queryForObject("SELECT IsAdmin FROM UMSCORE.Users WHERE Id = ?", Boolean.class, id))
                .as("the flag follows the tier role").isTrue();
        AsUser.call(AsUser.JO_ADMIN, () -> { userService.removeRoleFromUser(id, roleId("COUNTRY_ADMIN"), AsUser.JO_ADMIN); return null; });
        assertThat(jdbc.queryForObject("SELECT IsAdmin FROM UMSCORE.Users WHERE Id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    @DisplayName("N-02.c: deleting an account ends its sessions and records its attachés")
    void deleteEndsSessions() {
        UmsUser created = AsUser.call(AsUser.JO_ADMIN, () -> userService.createUser(probe(List.of(JORDAN)), AsUser.JO_ADMIN));
        long id = created.getId();
        jdbc.update("INSERT INTO UMSCORE.UserSessions (UserId, TokenHash, ExpiresAt, IsActive, CreatedAt) "
                + "VALUES (?, 'n02-session', DATEADD(HOUR, 1, SYSUTCDATETIME()), 1, SYSUTCDATETIME())", id);

        AsUser.call(AsUser.JO_ADMIN, () -> { userService.deleteUser(id, AsUser.JO_ADMIN); return null; });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserSessions WHERE UserId = ? AND IsActive = 1", Integer.class, id))
                .as("no live session survives the delete").isZero();
        String audit = jdbc.queryForObject(
                "SELECT TOP 1 NewValue FROM UMSCORE.UserActions WHERE Action = 'DELETE_USER' AND ResourceId = ? ORDER BY Id DESC",
                String.class, id);
        assertThat(audit).contains("countries=[" + JORDAN + "]").contains("sessionsEnded=1");
    }

    @Test
    @DisplayName("N-01.b: a ministry staff account with no attaché is visible to a country admin but not administrable")
    void countryAdminCannotAdministerMinistryStaff() {
        jdbc.update("""
                INSERT INTO UMSCORE.Users (UserName, PasswordHash, Email, FirstName, LastName,
                                           Status, IsAdmin, IsSuperAdmin, IsMinistryUser, IsDeleted, CreatedAt)
                VALUES (?, N'x', ?, N'Ministry', N'Probe', N'ACTIVE', 0, 0, 1, 0, SYSUTCDATETIME())
                """, MINISTRY_PROBE, MINISTRY_PROBE + "@test");
        Long id = jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE Username = ?", Long.class, MINISTRY_PROBE);

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> userService.getUserById(id)))
                .as("clause 3: an unassigned account is visible to an admin").isPresent();
        UmsUser patch = new UmsUser();
        patch.setFirstName("Renamed");
        assertForbidden("but a ministry account is not a country admin's to edit",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> userService.updateUser(id, patch, AsUser.JO_ADMIN)));
        assertForbidden("nor to delete",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> { userService.deleteUser(id, AsUser.JO_ADMIN); return null; }));
    }
}
