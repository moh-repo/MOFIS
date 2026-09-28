package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security plan O-25 (6 Sep 2026): the last five passwords cannot be reused.
 * Runs against the real {@code UMSCORE.UserPasswordHistory} (V087) on a probe
 * account that is removed, with its rows, in a finally.
 */
@Tag("integration")
@SpringBootTest(properties = "ums.password.history-size=2")
class PasswordHistoryTest {

    @Autowired private UmsUserService userService;
    @Autowired private JdbcTemplate jdbc;

    private long insertProbe() {
        jdbc.update("""
                INSERT INTO UMSCORE.Users (UserName, PasswordHash, Email, FirstName, LastName,
                                           Status, IsAdmin, IsSuperAdmin, IsDeleted, CreatedAt)
                VALUES (N'history_probe', N'x', N'history_probe@test', N'History', N'Probe',
                        N'ACTIVE', 0, 0, 0, SYSUTCDATETIME())
                """);
        return jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE UserName = N'history_probe'", Long.class);
    }

    private void removeProbe(long id) {
        jdbc.update("DELETE FROM UMSCORE.UserPasswordHistory WHERE UserId = ?", id);
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE ResourceType = 'USER' AND ResourceId = ?", id);
        jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = N'Users' AND RECORD_ID = ?", String.valueOf(id));
        jdbc.update("DELETE FROM UMSCORE.Users WHERE Id = ?", id);
    }

    private void change(long id, String password) {
        AsUser.run(AsUser.SUPERADMIN, () -> userService.changePassword(id, password, AsUser.SUPERADMIN));
    }

    @Test
    @DisplayName("a password equal to the current one or to a recent one is refused; older ones come back")
    void recentPasswordsRefused() {
        long id = insertProbe();
        try {
            change(id, "First-Passw0rd-A1");
            assertThatThrownBy(() -> change(id, "First-Passw0rd-A1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("current");

            change(id, "Second-Passw0rd-B2");
            assertThatThrownBy(() -> change(id, "First-Passw0rd-A1"))
                    .as("one back is within the history")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("recently");

            change(id, "Third-Passw0rd-C3");
            assertThatThrownBy(() -> change(id, "Second-Passw0rd-B2")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> change(id, "First-Passw0rd-A1"))
                    .as("history-size=2 still holds First and Second")
                    .isInstanceOf(IllegalArgumentException.class);

            // After a fourth change the history holds Third and Second; First has
            // dropped off and may be used again.
            change(id, "Fourth-Passw0rd-D4");
            assertThatCode(() -> change(id, "First-Passw0rd-A1")).doesNotThrowAnyException();

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserPasswordHistory WHERE UserId = ?", Integer.class, id))
                    .as("pruned to the configured size")
                    .isEqualTo(2);
        } finally {
            removeProbe(id);
        }
    }
}
