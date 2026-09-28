package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security plan R-14 (6 Sep 2026): a direct permission grant is a time-boxed
 * exception with a reason; a lapsed one is ignored at once and deactivated by
 * the nightly review; the per-user dashboard sections (V071) are configuration
 * and need neither.
 */
@Tag("integration")
@SpringBootTest
class DirectGrantExpiryTest {

    private static final long JO_STAFF = 14L;
    /** A code the STAFF role does not carry. */
    private static final String CODE = "AUDIT.EXPORT";
    private static final String SECTION = "DASHBOARD_QUARTER.VIEW";

    @Autowired private UmsUserService userService;
    @Autowired private UmsPermissionService permissionService;
    @Autowired private UserAuthorityCache cache;
    @Autowired private DirectGrantReviewJob reviewJob;
    @Autowired private JdbcTemplate jdbc;

    private Long permissionId(String code) {
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Permissions WHERE PermissionCode = ?", Long.class, code);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM UMSCORE.UserPermissions WHERE UserId = ? AND PermissionId = ?", JO_STAFF, permissionId(CODE));
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE ResourceType = 'USER_PERMISSION' AND ResourceId = ?", JO_STAFF);
        cache.evictAll();
    }

    @Test
    @DisplayName("a grant needs a reason and an expiry within the limit")
    void grantNeedsReasonAndExpiry() {
        long pid = permissionId(CODE);
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN, () -> {
            userService.grantPermissionToUser(JO_STAFF, pid, AsUser.SUPERADMIN, null, LocalDateTime.now().plusDays(10));
            return null;
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN, () -> {
            userService.grantPermissionToUser(JO_STAFF, pid, AsUser.SUPERADMIN, "audit export for the quarter close", null);
            return null;
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expiry");
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN, () -> {
            userService.grantPermissionToUser(JO_STAFF, pid, AsUser.SUPERADMIN, "too long", LocalDateTime.now().plusDays(91));
            return null;
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("90");
        assertThat(permissionService.getUserPermissionCodes(JO_STAFF)).doesNotContain(CODE);
    }

    @Test
    @DisplayName("a grant within the limit takes effect; once lapsed it is ignored and then deactivated")
    void grantLapses() {
        long pid = permissionId(CODE);
        AsUser.call(AsUser.SUPERADMIN, () -> {
            userService.grantPermissionToUser(JO_STAFF, pid, AsUser.SUPERADMIN, "audit export for the quarter close",
                    LocalDateTime.now().plusDays(30));
            return null;
        });
        assertThat(permissionService.getUserPermissionCodes(JO_STAFF)).contains(CODE);
        assertThat(permissionService.hasPermission(JO_STAFF, CODE)).isTrue();
        assertThat(cache.snapshot(JO_STAFF).codes()).contains(CODE);

        // Time passes.
        jdbc.update("UPDATE UMSCORE.UserPermissions SET ExpiresAt = DATEADD(MINUTE, -1, SYSUTCDATETIME()) WHERE UserId = ? AND PermissionId = ?",
                JO_STAFF, pid);
        cache.evict(JO_STAFF);
        assertThat(permissionService.getUserPermissionCodes(JO_STAFF)).as("lapsed: ignored by every read").doesNotContain(CODE);
        assertThat(permissionService.hasPermission(JO_STAFF, CODE)).isFalse();
        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> userService.getUserDirectPermissions(JO_STAFF)))
                .extracting(p -> p.getPermissionCode()).doesNotContain(CODE);

        int lapsed = reviewJob.review();
        assertThat(lapsed).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT IsActive FROM UMSCORE.UserPermissions WHERE UserId = ? AND PermissionId = ?",
                Boolean.class, JO_STAFF, pid)).as("the review deactivated the row").isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE Action = 'DIRECT_GRANT_EXPIRED' AND ResourceId = ?",
                Integer.class, JO_STAFF)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a dashboard section is configuration: no reason, no expiry")
    void dashboardSectionsAreConfiguration() {
        long pid = permissionId(SECTION);
        boolean had = jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserPermissions WHERE UserId = ? AND PermissionId = ? AND IsActive = 1",
                Integer.class, JO_STAFF, pid) > 0;
        if (had) {
            AsUser.call(AsUser.SUPERADMIN, () -> { userService.revokePermissionFromUser(JO_STAFF, pid, AsUser.SUPERADMIN); return null; });
        }
        try {
            AsUser.call(AsUser.SUPERADMIN, () -> { userService.grantPermissionToUser(JO_STAFF, pid, AsUser.SUPERADMIN); return null; });
            assertThat(jdbc.queryForObject("SELECT ExpiresAt FROM UMSCORE.UserPermissions WHERE UserId = ? AND PermissionId = ?",
                    java.sql.Timestamp.class, JO_STAFF, pid)).isNull();
            assertThat(permissionService.getUserPermissionCodes(JO_STAFF)).contains(SECTION);
        } finally {
            if (!had) {
                jdbc.update("DELETE FROM UMSCORE.UserPermissions WHERE UserId = ? AND PermissionId = ?", JO_STAFF, pid);
            } else {
                jdbc.update("UPDATE UMSCORE.UserPermissions SET IsActive = 1, ExpiresAt = NULL WHERE UserId = ? AND PermissionId = ?", JO_STAFF, pid);
            }
        }
    }
}
