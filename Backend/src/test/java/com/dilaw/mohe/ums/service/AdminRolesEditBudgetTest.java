package com.dilaw.mohe.ums.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Round-5 review, decision D-3: BUDGET_EDIT no longer accepts ACCOUNTING.EDIT, and V146
 * gives the Admin and Super Admin roles BUDGET.EDIT itself, so they keep editing the
 * budget while COUNTRY_ADMIN does not.
 *
 * <p>Integration test: run only against a disposable copy of MOHE (it boots Flyway, which
 * has applied V146 by the time this runs). Running the script a second time must change
 * nothing.
 */
@Tag("integration")
@SpringBootTest
class AdminRolesEditBudgetTest {

    private static final Path V146 = Path.of("docs", "scripts", "V146__budget_edit_to_admin_roles.sql");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SeparationOfDutiesService separationOfDuties;

    private List<String> activeCodes(String roleName) {
        return jdbc.queryForList("SELECT p.PermissionCode FROM UMSCORE.RolePermissions rp "
                + "JOIN UMSCORE.Roles r ON r.Id = rp.RoleId JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId "
                + "WHERE rp.IsActive = 1 AND r.RoleName = ?", String.class, roleName);
    }

    private long roleId(String roleName) {
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Roles WHERE RoleName = ?", Long.class, roleName);
    }

    @Test
    @DisplayName("after V146 Admin and Super Admin hold BUDGET.EDIT; COUNTRY_ADMIN does not")
    void grantsAfterTheMigration() {
        for (String role : List.of("Admin", "Super Admin")) {
            assertThat(activeCodes(role)).as(role).contains("BUDGET.EDIT");
        }
        assertThat(activeCodes("COUNTRY_ADMIN")).doesNotContain("BUDGET.EDIT");
    }

    @Test
    @DisplayName("the two roles still pass the separation-of-duties check a role must pass to be assigned")
    void adminRolesMayBeHeld() {
        for (String role : List.of("Admin", "Super Admin")) {
            assertThatCode(() -> separationOfDuties.assertRoleMayHold(roleId(role), activeCodes(role)))
                    .as(role).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a second run of V146 changes nothing")
    void idempotent() throws Exception {
        String before = String.join(",", jdbc.queryForList(
                "SELECT CONCAT(RoleId, ':', PermissionId, ':', IsActive) FROM UMSCORE.RolePermissions ORDER BY RoleId, PermissionId",
                String.class));
        MigrationRerun.rerun(jdbc, V146);
        String after = String.join(",", jdbc.queryForList(
                "SELECT CONCAT(RoleId, ':', PermissionId, ':', IsActive) FROM UMSCORE.RolePermissions ORDER BY RoleId, PermissionId",
                String.class));
        assertThat(after).isEqualTo(before);
    }
}
