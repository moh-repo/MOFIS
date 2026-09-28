package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Security plan R-15 (6 Sep 2026): separation of duties at assignment time.
 * Two probe roles carry one half each of the {@code ALLOCATION} pair; holding
 * both through any path is refused with 409, and a role may not carry both
 * itself. The existing {@code Admin} role, which does, is refused as a whole.
 */
@Tag("integration")
@SpringBootTest
class SeparationOfDutiesTest {

    private static final long JO_STAFF = 14L;
    private static final String MAKER_ROLE = "SOD_TEST_MAKER";
    private static final String CHECKER_ROLE = "SOD_TEST_CHECKER";

    @Autowired private UmsUserService userService;
    @Autowired private UmsRoleService roleService;
    @Autowired private SeparationOfDutiesService sod;
    @Autowired private UserAuthorityCache cache;
    @Autowired private JdbcTemplate jdbc;

    private long makerRole;
    private long checkerRole;

    private long insertRole(String name) {
        jdbc.update("INSERT INTO UMSCORE.Roles (RoleName, Description, IsSystemRole, IsSystem, IsActive, Priority, CreatedAt) "
                + "VALUES (?, 'R-15 probe', 0, 0, 1, 1, SYSUTCDATETIME())", name);
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Roles WHERE RoleName = ?", Long.class, name);
    }

    private void grant(long roleId, String code) {
        jdbc.update("INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive) "
                + "SELECT ?, Id, SYSUTCDATETIME(), 1 FROM UMSCORE.Permissions WHERE PermissionCode = ?", roleId, code);
    }

    @BeforeEach
    void probes() {
        removeProbes();
        makerRole = insertRole(MAKER_ROLE);
        grant(makerRole, "ALLOCATION.CREATE");
        checkerRole = insertRole(CHECKER_ROLE);
        grant(checkerRole, "ALLOCATION.APPROVE");
        cache.evictAll();
    }

    @AfterEach
    void removeProbes() {
        for (String name : List.of(MAKER_ROLE, CHECKER_ROLE)) {
            jdbc.update("DELETE ur FROM UMSCORE.UserRoles ur JOIN UMSCORE.Roles r ON r.Id = ur.RoleId WHERE r.RoleName = ?", name);
            jdbc.update("DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Roles r ON r.Id = rp.RoleId WHERE r.RoleName = ?", name);
            jdbc.update("DELETE FROM UMSCORE.Roles WHERE RoleName = ?", name);
        }
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE ResourceType IN ('USER_ROLE','ROLE','ROLE_PERMISSION') AND UserId = ?", AsUser.SUPERADMIN);
        cache.evictAll();
    }

    private static void assertConflict(String what, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).as(what)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("the second half of a pair is refused on the account that holds the first")
    void secondHalfIsRefused() {
        AsUser.call(AsUser.SUPERADMIN, () -> { userService.assignRoleToUser(JO_STAFF, makerRole, AsUser.SUPERADMIN); return null; });
        assertThat(sod.effectiveCodes(JO_STAFF)).contains("ALLOCATION.CREATE");

        assertConflict("ALLOCATION.APPROVE on top of ALLOCATION.CREATE",
                () -> AsUser.call(AsUser.SUPERADMIN, () -> { userService.assignRoleToUser(JO_STAFF, checkerRole, AsUser.SUPERADMIN); return null; }));
        assertThat(sod.effectiveCodes(JO_STAFF)).doesNotContain("ALLOCATION.APPROVE");

        // The same through a direct grant.
        Long approveId = jdbc.queryForObject("SELECT Id FROM UMSCORE.Permissions WHERE PermissionCode = 'ALLOCATION.APPROVE'", Long.class);
        assertConflict("a direct grant of the other half",
                () -> AsUser.call(AsUser.SUPERADMIN, () -> {
                    userService.grantPermissionToUser(JO_STAFF, approveId, AsUser.SUPERADMIN, "test", java.time.LocalDateTime.now().plusDays(1));
                    return null;
                }));
    }

    @Test
    @DisplayName("a role may not carry both halves, and a role that already does cannot be assigned")
    void rolesAreCheckedToo() {
        Long create = jdbc.queryForObject("SELECT Id FROM UMSCORE.Permissions WHERE PermissionCode = 'ALLOCATION.CREATE'", Long.class);
        Long approve = jdbc.queryForObject("SELECT Id FROM UMSCORE.Permissions WHERE PermissionCode = 'ALLOCATION.APPROVE'", Long.class);
        assertConflict("installing both halves on one role",
                () -> AsUser.call(AsUser.SUPERADMIN, () -> { roleService.setRolePermissions(makerRole, List.of(create, approve), AsUser.SUPERADMIN); return null; }));
        assertConflict("adding the other half to the maker role",
                () -> AsUser.call(AsUser.SUPERADMIN, () -> { roleService.assignPermissionToRole(makerRole, approve, AsUser.SUPERADMIN); return null; }));

        // The dev database's Admin role carries both (V092 lists it); it cannot be handed out until split.
        Long adminRole = jdbc.queryForObject("SELECT Id FROM UMSCORE.Roles WHERE RoleName = 'Admin'", Long.class);
        assertConflict("the Admin role as a whole",
                () -> AsUser.call(AsUser.SUPERADMIN, () -> { userService.assignRoleToUser(JO_STAFF, adminRole, AsUser.SUPERADMIN); return null; }));
    }

    @Test
    @DisplayName("the super admin is the audited exception and is not refused")
    void superAdminIsExempt() {
        AsUser.call(AsUser.SUPERADMIN, () -> { userService.assignRoleToUser(AsUser.SUPERADMIN, makerRole, AsUser.SUPERADMIN); return null; });
        AsUser.call(AsUser.SUPERADMIN, () -> { userService.assignRoleToUser(AsUser.SUPERADMIN, checkerRole, AsUser.SUPERADMIN); return null; });
        assertThat(sod.violationsIn(sod.effectiveCodes(AsUser.SUPERADMIN))).isNotEmpty();
    }
}
