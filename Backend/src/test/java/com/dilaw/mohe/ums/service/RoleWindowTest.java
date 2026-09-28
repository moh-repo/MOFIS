package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-21: a role with a window counts only inside it, the nightly job opens and
 * closes windows, and a delegation copies roles for a period. An unprivileged
 * probe role keeps the four-eyes rule (S-03) out of the way, and the ministry
 * admin does the granting (a country admin may only grant roles it holds);
 * the transaction rolls everything back.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class RoleWindowTest {

    @Autowired private UmsUserService users;
    @Autowired private RoleWindowJob job;
    @Autowired private UserAuthorityCache cache;
    @Autowired private JdbcTemplate jdbc;

    private static final String PROBE = "ROLE_WINDOW_PROBE";
    private static final long JO_STAFF = 14L;
    private static final long JO_REVIEWER = 15L;
    private long probeRole;

    @BeforeEach
    void probeRole() {
        jdbc.update("INSERT INTO UMSCORE.Roles (RoleName, Description, IsSystemRole, IsSystem, IsActive, Priority, CreatedAt) "
                + "VALUES (?, 'R-21 probe', 0, 0, 1, 1, SYSUTCDATETIME())", PROBE);
        probeRole = jdbc.queryForObject("SELECT Id FROM UMSCORE.Roles WHERE RoleName = ?", Long.class, PROBE);
        cache.evictAll();
    }

    @AfterEach
    void forget() {
        cache.evictAll();
    }

    private boolean holds(long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserRoles WHERE UserId = ? AND RoleId = ? AND IsActive = 1",
                Integer.class, userId, probeRole);
        return n != null && n > 0;
    }

    @Test
    @DisplayName("a role with an end date is switched off by the job once the date has passed")
    void windowCloses() {
        LocalDateTime now = LocalDateTime.now();
        AsUser.run(AsUser.ADMIN, () -> users.assignRoleToUser(JO_STAFF, probeRole, AsUser.ADMIN,
                new UmsUserService.RoleWindow(null, now.plusDays(3), null, "covering the desk")));
        assertThat(holds(JO_STAFF)).isTrue();

        job.run(now.plusDays(1));
        assertThat(holds(JO_STAFF)).as("still inside the window").isTrue();

        job.run(now.plusDays(4));
        assertThat(holds(JO_STAFF)).as("the window closed").isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE UserId = ? AND [Action] = 'ROLE_WINDOW_CLOSED'",
                Integer.class, JO_STAFF)).isEqualTo(1);
    }

    @Test
    @DisplayName("a role that opens later is inactive until the job opens it; a removed role is never reopened")
    void windowOpens() {
        LocalDateTime now = LocalDateTime.now();
        AsUser.run(AsUser.ADMIN, () -> users.assignRoleToUser(JO_STAFF, probeRole, AsUser.ADMIN,
                new UmsUserService.RoleWindow(now.plusDays(2), now.plusDays(10), null, null)));
        assertThat(holds(JO_STAFF)).as("not yet").isFalse();

        job.run(now.plusDays(3));
        assertThat(holds(JO_STAFF)).as("opened").isTrue();

        AsUser.run(AsUser.ADMIN, () -> users.removeRoleFromUser(JO_STAFF, probeRole, AsUser.ADMIN));
        assertThat(holds(JO_STAFF)).isFalse();
        job.run(now.plusDays(4));
        assertThat(holds(JO_STAFF)).as("removed by hand stays removed").isFalse();
    }

    @Test
    @DisplayName("an end date in the past is refused")
    void pastEndDateRefused() {
        assertThatThrownBy(() -> AsUser.run(AsUser.ADMIN, () -> users.assignRoleToUser(JO_STAFF, probeRole, AsUser.ADMIN,
                new UmsUserService.RoleWindow(null, LocalDateTime.now().minusDays(1), null, null))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("delegation copies the absent officer's roles to the colleague until the end date")
    void delegation() {
        LocalDateTime now = LocalDateTime.now();
        AsUser.run(AsUser.ADMIN, () -> users.assignRoleToUser(JO_STAFF, probeRole, AsUser.ADMIN));
        assertThat(holds(JO_REVIEWER)).isFalse();

        UmsUserService.DelegationResult result = AsUser.call(AsUser.ADMIN, () ->
                users.delegateRoles(JO_STAFF, JO_REVIEWER, null, now.plusDays(14), "annual leave", AsUser.ADMIN, 90));
        assertThat(result.applied()).contains(PROBE);
        assertThat(holds(JO_REVIEWER)).isTrue();
        assertThat(jdbc.queryForObject("SELECT DelegatedFrom FROM UMSCORE.UserRoles WHERE UserId = ? AND RoleId = ?",
                Long.class, JO_REVIEWER, probeRole)).isEqualTo(JO_STAFF);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE [Action] = 'ROLE_DELEGATED' AND ResourceId = ?",
                Integer.class, JO_REVIEWER)).isEqualTo(1);

        job.run(now.plusDays(15));
        assertThat(holds(JO_REVIEWER)).as("taken back on the end date").isFalse();
        assertThat(holds(JO_STAFF)).as("the officer keeps their own").isTrue();

        assertThatThrownBy(() -> AsUser.call(AsUser.ADMIN, () ->
                users.delegateRoles(JO_STAFF, JO_REVIEWER, null, now.plusDays(200), "too long", AsUser.ADMIN, 90)))
                .hasMessageContaining("at most 90 days");
        assertThatThrownBy(() -> AsUser.call(AsUser.SA_ADMIN, () ->
                users.delegateRoles(JO_STAFF, JO_REVIEWER, null, now.plusDays(5), "not mine", AsUser.SA_ADMIN, 90)))
                .as("a Saudi admin does not administer Jordan's staff")
                .hasMessageContaining("User not found");
    }
}
