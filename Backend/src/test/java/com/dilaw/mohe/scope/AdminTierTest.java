package com.dilaw.mohe.scope;

import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.service.AdminTierService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.UmsUserService;
import com.dilaw.mohe.ums.service.UserAuthorityCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security plan R-12 and R-19 (6 Sep 2026): the administrator tiers are roles,
 * the JWT filter emits {@code ROLE_<tier>} and never {@code ROLE_ADMIN}, and the
 * authorities are cached but change the moment an assignment does.
 *
 * <p>Runs against the seeded accounts after V094: {@code superadmin} and
 * {@code sa_superadmin} are super admins by flag, {@code admin} holds
 * {@code MINISTRY_ADMIN}, {@code jo_admin} and {@code sa_admin} hold
 * {@code COUNTRY_ADMIN}, {@code jo_staff} holds no tier role.
 */
@Tag("integration")
@SpringBootTest
class AdminTierTest {

    private static final long JO_STAFF = 14L;

    @Autowired private AdminTierService tiers;
    @Autowired private UserAuthorityCache cache;
    @Autowired private CountryScopeService countryScope;
    @Autowired private UmsUserService userService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("the seeded accounts land in the tiers the plan names for them")
    void seededTiers() {
        assertThat(tiers.tierOf(AsUser.SUPERADMIN)).isEqualTo(AdminTier.SUPER_ADMIN);
        assertThat(tiers.tierOf(AsUser.SA_SUPERADMIN)).isEqualTo(AdminTier.SUPER_ADMIN);
        assertThat(tiers.tierOf(AsUser.ADMIN)).as("admin holds every attaché: the ministry admin")
                .isEqualTo(AdminTier.MINISTRY_ADMIN);
        assertThat(tiers.tierOf(AsUser.JO_ADMIN)).isEqualTo(AdminTier.COUNTRY_ADMIN);
        assertThat(tiers.tierOf(AsUser.SA_ADMIN)).isEqualTo(AdminTier.COUNTRY_ADMIN);
        assertThat(tiers.tierOf(JO_STAFF)).isEqualTo(AdminTier.COUNTRY_STAFF);
    }

    @Test
    @DisplayName("the SUPER_ADMIN role alone does not make a super admin; the flag does")
    void superAdminIsTheFlag() {
        // admin holds the SUPER_ADMIN role row on the dev database without the flag.
        assertThat(tiers.roleNamesOf(AsUser.ADMIN)).contains("SUPER_ADMIN");
        assertThat(tiers.tierOf(AsUser.ADMIN)).isNotEqualTo(AdminTier.SUPER_ADMIN);
    }

    @Test
    @DisplayName("the filter's authorities carry ROLE_<tier>, never ROLE_ADMIN")
    void authoritiesCarryTheTier() {
        cache.evictAll();
        assertThat(cache.snapshot(AsUser.JO_ADMIN).authorities())
                .contains("ROLE_COUNTRY_ADMIN", "UMS_ADMIN.ADD", "WAFAR.ADD")
                .doesNotContain("ROLE_ADMIN", "SYSTEM.RESTORE");
        assertThat(cache.snapshot(AsUser.ADMIN).authorities())
                .contains("ROLE_MINISTRY_ADMIN", "UMS_ADMIN.ADD", "UMS_ADMIN.DELETE")
                .doesNotContain("ROLE_ADMIN");
        // N-04: the MINISTRY_ADMIN bundle itself carries no Wafar write. (The dev
        // `admin` account still reaches those codes through its legacy roles; the
        // service guard, not the code, is what refuses a ministry-level write.)
        List<String> ministryBundle = jdbc.queryForList("""
                SELECT p.PermissionCode FROM UMSCORE.RolePermissions rp
                JOIN UMSCORE.Roles r ON r.Id = rp.RoleId JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
                WHERE r.RoleName = 'MINISTRY_ADMIN' AND rp.IsActive = 1
                """, String.class);
        assertThat(ministryBundle)
                .contains("UMS_ADMIN.ADD", "WAFAR.VIEW", "ALLOCATION.APPROVE")
                .doesNotContain("WAFAR.ADD", "WAFAR.EDIT", "WAFAR.DELETE", "WAFAR.APPROVE",
                        "SYSTEM.RESTORE", "ALLOCATION.CREATE", "PAYMENT_BATCH.CREATE");
        assertThat(cache.snapshot(AsUser.SUPERADMIN).authorities())
                .contains("ROLE_SUPER_ADMIN", "SYSTEM.RESTORE")
                .doesNotContain("ROLE_ADMIN");
        assertThat(cache.snapshot(JO_STAFF).authorities())
                .noneMatch(a -> a.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("the scope and the ministry-level test read the tier")
    void scopeFollowsTheTier() {
        assertThat(countryScope.getScopeForUser(AsUser.ADMIN).getTier()).isEqualTo(AdminTier.MINISTRY_ADMIN);
        assertThat(countryScope.getScopeForUser(AsUser.ADMIN).isMinistryUser()).isTrue();
        assertThat(countryScope.getScopeForUser(AsUser.JO_ADMIN).getTier()).isEqualTo(AdminTier.COUNTRY_ADMIN);
        assertThat(countryScope.getScopeForUser(AsUser.JO_ADMIN).isMinistryUser()).isFalse();
        assertThat(countryScope.isMinistryLevelUser(AsUser.ADMIN)).isTrue();
        assertThat(countryScope.isMinistryLevelUser(AsUser.JO_ADMIN)).isFalse();
        assertThat(countryScope.getScopeForUser(AsUser.SUPERADMIN).roleLevel()).isEqualTo("SUPERADMIN");
        assertThat(countryScope.getScopeForUser(AsUser.JO_ADMIN).roleLevel()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("R-19: a role assignment reaches the cached authorities at once")
    void cacheIsEvictedOnAssignment() {
        // A probe role carrying one code the STAFF role does not.
        jdbc.update("DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Roles r ON r.Id = rp.RoleId WHERE r.RoleName = 'R19_PROBE'");
        jdbc.update("DELETE ur FROM UMSCORE.UserRoles ur JOIN UMSCORE.Roles r ON r.Id = ur.RoleId WHERE r.RoleName = 'R19_PROBE'");
        jdbc.update("DELETE FROM UMSCORE.Roles WHERE RoleName = 'R19_PROBE'");
        jdbc.update("INSERT INTO UMSCORE.Roles (RoleName, Description, IsSystemRole, IsSystem, IsActive, Priority, CreatedAt) "
                + "VALUES ('R19_PROBE', 'R-19 probe', 0, 0, 1, 1, SYSUTCDATETIME())");
        long probeRole = jdbc.queryForObject("SELECT Id FROM UMSCORE.Roles WHERE RoleName = 'R19_PROBE'", Long.class);
        jdbc.update("INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive) "
                + "SELECT ?, Id, SYSUTCDATETIME(), 1 FROM UMSCORE.Permissions WHERE PermissionCode = 'AUDIT.EXPORT'", probeRole);
        cache.evictAll();
        assertThat(cache.snapshot(JO_STAFF).codes()).doesNotContain("AUDIT.EXPORT");
        try {
            AsUser.call(AsUser.SUPERADMIN, () -> {
                userService.assignRoleToUser(JO_STAFF, probeRole, AsUser.SUPERADMIN);
                return null;
            });
            assertThat(cache.snapshot(JO_STAFF).codes())
                    .as("no TTL to wait for: the assignment evicted the entry")
                    .contains("AUDIT.EXPORT");
            assertThat(cache.snapshot(JO_STAFF).roleNames()).contains("R19_PROBE");
        } finally {
            jdbc.update("DELETE FROM UMSCORE.UserRoles WHERE UserId = ? AND RoleId = ?", JO_STAFF, probeRole);
            jdbc.update("DELETE FROM UMSCORE.RolePermissions WHERE RoleId = ?", probeRole);
            jdbc.update("DELETE FROM UMSCORE.Roles WHERE Id = ?", probeRole);
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE UserId = ? AND ResourceType = 'USER_ROLE' AND ResourceId = ?",
                    AsUser.SUPERADMIN, JO_STAFF);
            cache.evictAll();
        }
        assertThat(cache.snapshot(JO_STAFF).codes()).doesNotContain("AUDIT.EXPORT");
    }
}
