package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.ums.model.AuthLockoutPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lockout, end to end, against the ladder the database holds.
 *
 * <p>What the August review found: the count lived in {@code localStorage}, so
 * a second browser or one deleted key removed it, while the server ran a
 * different and invisible rule — five failures and the account was locked for
 * good. These tests are the record that the decision is the server's now, that
 * it escalates the way {@code MOHECORE.AUTH_LOCKOUT_POLICY} says, and that the
 * numbers sent back cannot be used to find out who holds an account here.
 *
 * <p>The account is created and dropped by the test, so nothing a person uses
 * is ever locked by a test run. The rungs are read from the table rather than
 * written down here — retuning the policy in Settings is expected, and should
 * not turn this suite red.
 */
@Tag("integration")
@SpringBootTest
class LoginLockoutTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String WRONG = "not-the-password";

    @Autowired private UmsAuthService authService;
    @Autowired private LoginAttemptService loginAttempts;
    @Autowired private JdbcTemplate jdbc;

    private String username;
    private long userId;
    private List<AuthLockoutPolicy> stages;

    @BeforeEach
    void createThrowawayAccount() {
        stages = loginAttempts.stages();
        assertThat(stages).as("V080 seeds the ladder").isNotEmpty();

        username = "lockout-test-" + System.nanoTime();
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        jdbc.update("""
                INSERT INTO UMSCORE.Users
                    (Username, Email, PasswordHash, Status, IsAdmin, IsSuperAdmin, IsMinistryUser,
                     IsActive, IsDeleted, FailedLoginAttempts, LockoutStage, CreatedAt)
                VALUES (?, ?, ?, ?, 0, 0, 0, 1, 0, 0, 0, SYSDATETIME())
                """, username, username + "@example.invalid", hash, UserStatus.ACTIVE);
        userId = jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE Username = ?", Long.class, username);
    }

    @AfterEach
    void dropThrowawayAccount() {
        // Every attempt above wrote an audit row; they hold the account's id.
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE UserId = ?", userId);
        jdbc.update("DELETE FROM UMSCORE.Users WHERE Id = ?", userId);
    }

    private UmsAuthService.AuthResult attempt(String password) {
        return authService.authenticate(username, password, "203.0.113.7", "junit");
    }

    @Test
    @DisplayName("the first rung counts down in the reply, then locks for the minutes the table says")
    void theFirstRungCountsDownThenLocks() {
        int allowance = stages.get(0).getMaxAttempts();

        for (int i = 1; i < allowance; i++) {
            UmsAuthService.AuthResult result = attempt(WRONG);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getAttemptStatus().remainingAttempts())
                    .as("tries left after failure %d", i).isEqualTo(allowance - i);
            assertThat(result.getAttemptStatus().locked()).isFalse();
        }

        UmsAuthService.AuthResult last = attempt(WRONG);
        assertThat(last.getAttemptStatus().locked()).isTrue();
        assertThat(last.getAttemptStatus().remainingAttempts()).isZero();
        assertThat(last.getAttemptStatus().lockedUntil())
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(stages.get(0).getLockMinutes())),
                        within(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("the right password is still refused while the lock stands")
    void theRightPasswordDoesNotOpenALockedAccount() {
        lockOnce();

        UmsAuthService.AuthResult result = attempt(PASSWORD);

        assertThat(result.isSuccess()).as("locked means locked, whatever the guess").isFalse();
        assertThat(result.getAttemptStatus().locked()).isTrue();
    }

    @Test
    @DisplayName("a timed lock opens itself when its minutes are up, and the account signs in")
    void aTimedLockExpires() {
        lockOnce();

        // Waiting out a quarter of an hour in a test is not waiting: move the
        // deadline into the past, which is what the clock would have done.
        jdbc.update("UPDATE UMSCORE.Users SET LockedUntil = ? WHERE Id = ?",
                LocalDateTime.now().minusMinutes(1), userId);

        assertThat(attempt(PASSWORD).isSuccess()).isTrue();
        assertThat(jdbc.queryForObject("SELECT LockedUntil FROM UMSCORE.Users WHERE Id = ?", LocalDateTime.class, userId))
                .isNull();
    }

    @Test
    @DisplayName("the ladder escalates, and its last rung leaves the account for an administrator")
    void theLastRungNeedsAnAdministrator() {
        for (AuthLockoutPolicy stage : stages) {
            for (int i = 0; i < stage.getMaxAttempts(); i++) {
                attempt(WRONG);
            }
            // Sit out whatever this rung imposed, so the next failure lands on
            // the next rung rather than on a standing lock.
            jdbc.update("UPDATE UMSCORE.Users SET LockedUntil = ? WHERE Id = ?",
                    LocalDateTime.now().minusMinutes(1), userId);
        }

        assertThat(jdbc.queryForObject("SELECT Status FROM UMSCORE.Users WHERE Id = ?", String.class, userId))
                .as("the open-ended rung is the same state an administrator sets by hand")
                .isEqualTo(UserStatus.LOCKED);
        assertThat(attempt(PASSWORD).isSuccess()).isFalse();
    }

    @Test
    @DisplayName("a successful sign-in clears the count, the rung and the lock")
    void successEndsTheRun() {
        attempt(WRONG);
        assertThat(attempt(PASSWORD).isSuccess()).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT FailedLoginAttempts FROM UMSCORE.Users WHERE Id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT LockoutStage FROM UMSCORE.Users WHERE Id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT LockedUntil FROM UMSCORE.Users WHERE Id = ?", LocalDateTime.class, userId)).isNull();
    }

    @Test
    @DisplayName("a username with no account counts down exactly like one that exists")
    void anUnknownUsernameIsIndistinguishable() {
        String nobody = "no-such-account-" + System.nanoTime();
        String address = "198.51.100.4";

        for (int i = 1; i <= stages.get(0).getMaxAttempts(); i++) {
            UmsAuthService.AuthResult real = attempt(WRONG);
            UmsAuthService.AuthResult imaginary = authService.authenticate(nobody, WRONG, address, "junit");

            assertThat(imaginary.getMessage())
                    .as("same words on attempt %d", i).isEqualTo(real.getMessage());
            assertThat(imaginary.getAttemptStatus().remainingAttempts())
                    .as("same count on attempt %d", i).isEqualTo(real.getAttemptStatus().remainingAttempts());
            assertThat(imaginary.getAttemptStatus().locked())
                    .as("same lock state on attempt %d", i).isEqualTo(real.getAttemptStatus().locked());
        }
    }

    /** Fails until the first rung's lock stands. */
    private void lockOnce() {
        for (int i = 0; i < stages.get(0).getMaxAttempts(); i++) {
            attempt(WRONG);
        }
    }

    private static org.assertj.core.data.TemporalUnitOffset within(Duration duration) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(
                duration.toMillis(), java.time.temporal.ChronoUnit.MILLIS);
    }
}
