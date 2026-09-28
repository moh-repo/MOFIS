package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-05: the dormant-account rule and the leaver cleanup. Throwaway accounts
 * inside a rolled-back transaction.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class DormantAccountJobTest {

    @Autowired private DormantAccountJob job;
    @Autowired private UmsUserService users;
    @Autowired private JdbcTemplate jdbc;
    /** The services change entities through JPA; the raw counts below need them flushed first. */
    @Autowired private jakarta.persistence.EntityManager em;

    private long account(String name, LocalDateTime lastLogin, boolean superAdmin) {
        jdbc.update("""
                INSERT INTO UMSCORE.Users (Username, Email, PasswordHash, Status, IsAdmin, IsSuperAdmin, IsMinistryUser,
                                           IsActive, IsDeleted, FailedLoginAttempts, LockoutStage, CreatedAt, LastLoginAt)
                VALUES (?, ?, 'x', ?, 0, ?, 0, 1, 0, 0, 0, DATEADD(day, -400, SYSDATETIME()), ?)
                """, name, name + "@example.invalid", UserStatus.ACTIVE, superAdmin ? 1 : 0,
                lastLogin == null ? null : Timestamp.valueOf(lastLogin));
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE Username = ?", Long.class, name);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT Status FROM UMSCORE.Users WHERE Id = ?", String.class, id);
    }

    private int actions(long id, String action) {
        // Counted on the account the event is about: the job audits as the account itself, an administrator as themselves.
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE ResourceId = ? AND [Action] = ?", Integer.class, id, action);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("90 days idle: disabled; 80 days: warned once; a super admin and a fresh account are left alone")
    void dormantRule() {
        LocalDateTime now = LocalDateTime.now();
        String stamp = String.valueOf(System.nanoTime());
        long gone = account("dormant-gone-" + stamp, now.minusDays(100), false);
        long warned = account("dormant-warn-" + stamp, now.minusDays(80), false);
        long fresh = account("dormant-fresh-" + stamp, now.minusDays(10), false);
        long neverSignedIn = account("dormant-never-" + stamp, null, false);   // created 400 days ago
        long breakGlass = account("dormant-super-" + stamp, now.minusDays(400), true);
        // A session and a direct grant on the leaver, to see them go.
        jdbc.update("INSERT INTO UMSCORE.UserSessions (UserId, TokenHash, IpAddress, UserAgent, CreatedAt, ExpiresAt, IsActive) "
                + "VALUES (?, 'hash', '127.0.0.1', 'junit', SYSDATETIME(), DATEADD(hour, 8, SYSDATETIME()), 1)", gone);
        jdbc.update("INSERT INTO UMSCORE.UserPermissions (UserId, PermissionId, GrantedAt, IsActive) "
                + "SELECT TOP 1 ?, Id, SYSDATETIME(), 1 FROM UMSCORE.Permissions ORDER BY Id", gone);

        DormantAccountJob.Outcome first = job.run(now);
        em.flush();
        assertThat(first.disabled()).isGreaterThanOrEqualTo(2);   // gone + neverSignedIn, plus any real dormant dev account
        assertThat(status(gone)).isEqualTo(UserStatus.INACTIVE);
        assertThat(status(neverSignedIn)).as("never signed in counts from creation").isEqualTo(UserStatus.INACTIVE);
        assertThat(status(warned)).isEqualTo(UserStatus.ACTIVE);
        assertThat(status(fresh)).isEqualTo(UserStatus.ACTIVE);
        assertThat(status(breakGlass)).as("the super admin is exempt").isEqualTo(UserStatus.ACTIVE);
        assertThat(actions(gone, "ACCOUNT_DORMANT")).isEqualTo(1);
        assertThat(actions(gone, "SESSION_TERMINATED")).isEqualTo(1);
        assertThat(actions(gone, "DIRECT_GRANT_CLEARED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserSessions WHERE UserId = ? AND IsActive = 1", Integer.class, gone)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserPermissions WHERE UserId = ? AND IsActive = 1", Integer.class, gone)).isZero();
        assertThat(actions(warned, "ACCOUNT_DORMANT_WARNING")).isEqualTo(1);

        job.run(now.plusDays(1));
        em.flush();
        assertThat(actions(warned, "ACCOUNT_DORMANT_WARNING")).as("one warning per idle period").isEqualTo(1);
    }

    @Test
    @DisplayName("setting an account INACTIVE by hand ends its sessions and lapses its direct grants")
    void leaverByHand() {
        long leaver = account("leaver-" + System.nanoTime(), LocalDateTime.now(), false);
        jdbc.update("INSERT INTO UMSCORE.UserSessions (UserId, TokenHash, IpAddress, UserAgent, CreatedAt, ExpiresAt, IsActive) "
                + "VALUES (?, 'hash2', '127.0.0.1', 'junit', SYSDATETIME(), DATEADD(hour, 8, SYSDATETIME()), 1)", leaver);

        com.dilaw.mohe.ums.model.UmsUser patch = new com.dilaw.mohe.ums.model.UmsUser();
        patch.setStatus(UserStatus.INACTIVE);
        com.dilaw.mohe.scope.AsUser.run(com.dilaw.mohe.scope.AsUser.SUPERADMIN,
                () -> users.updateUser(leaver, patch, com.dilaw.mohe.scope.AsUser.SUPERADMIN));
        em.flush();

        assertThat(status(leaver)).isEqualTo(UserStatus.INACTIVE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserSessions WHERE UserId = ? AND IsActive = 1", Integer.class, leaver)).isZero();
        assertThat(actions(leaver, "SESSION_TERMINATED")).isEqualTo(1);
    }
}
