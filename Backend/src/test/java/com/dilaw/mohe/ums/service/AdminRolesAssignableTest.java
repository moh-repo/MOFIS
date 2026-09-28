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
 * Live QA L10, decision D23-a: V145 moves ALLOCATION.CREATE to FINANCE_OFFICER and
 * switches ALLOCATION.CREATE and PAYMENT_BATCH.CREATE off on Admin, Super Admin and
 * SUPER_ADMIN, so those roles no longer carry both halves of a separated duty and can
 * be assigned again.
 *
 * <p>Integration test: run only against a disposable copy of MOHE (it boots Flyway, which
 * has applied V145 by the time this runs). Running the script a second time must change
 * nothing.
 */
@Tag("integration")
@SpringBootTest
class AdminRolesAssignableTest {

    private static final Path V145 = Path.of("docs", "scripts", "V145__allocation_maker_codes_to_finance_officer.sql");

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
    @DisplayName("after V145 the admin roles hold no maker code and FINANCE_OFFICER makes allocations")
    void grantsAfterTheMigration() {
        for (String role : List.of("Admin", "Super Admin", "SUPER_ADMIN")) {
            assertThat(activeCodes(role)).as(role).doesNotContain("ALLOCATION.CREATE", "PAYMENT_BATCH.CREATE");
        }
        assertThat(activeCodes("FINANCE_OFFICER")).contains("ALLOCATION.CREATE", "PAYMENT_BATCH.CREATE")
                .doesNotContain("ALLOCATION.APPROVE", "PAYMENT_BATCH.APPROVE");
    }

    @Test
    @DisplayName("Admin and Super Admin pass the separation-of-duties check a role must pass to be assigned")
    void adminRolesMayBeHeld() {
        for (String role : List.of("Admin", "Super Admin", "FINANCE_OFFICER")) {
            assertThatCode(() -> separationOfDuties.assertRoleMayHold(roleId(role), activeCodes(role)))
                    .as(role).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a second run of V145 changes nothing")
    void idempotent() throws Exception {
        String before = String.join(",", jdbc.queryForList(
                "SELECT CONCAT(RoleId, ':', PermissionId, ':', IsActive) FROM UMSCORE.RolePermissions ORDER BY RoleId, PermissionId",
                String.class));
        MigrationRerun.rerun(jdbc, V145);
        String after = String.join(",", jdbc.queryForList(
                "SELECT CONCAT(RoleId, ':', PermissionId, ':', IsActive) FROM UMSCORE.RolePermissions ORDER BY RoleId, PermissionId",
                String.class));
        assertThat(after).isEqualTo(before);
    }
}
