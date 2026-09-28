package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A plain user changing their own password.
 *
 * <p>The profile page now offers this and nothing else, so it is worth proving
 * against the real service rather than a stub. The path is not obviously safe
 * from reading it: {@code UmsAuthService.changePassword} delegates to
 * {@code UmsUserService.changePassword}, which calls {@code require(userId)} —
 * a lookup scoped by {@code StaffCountrySpec.administrableBy}, i.e. to the
 * accounts the caller may <em>administer</em>. For an account with no
 * administrative rights that set is otherwise empty, and the same scoping is
 * exactly why {@code GET /ums/users/me} had to bypass this service and read the
 * repository directly.
 *
 * <p>It works because the specification carries an explicit "always yourself"
 * clause. That is the load-bearing line, it is four clauses deep in a class
 * about country visibility rather than about self-service, and nothing else
 * states that self-service depends on it — so this test does.
 *
 * <p>The account is created and deleted here rather than borrowed from the
 * seed: the test sets a known password and reads the hash back, which is not
 * something to do to an account somebody signs in with.
 */
@Tag("integration")
@SpringBootTest
class SelfServicePasswordChangeTest {

    private static final String USERNAME = "test-self-password";
    private static final String OLD_PASSWORD = "the-old-password";
    // Meets the September 2026 strength floor (8+ chars, a letter and a digit).
    private static final String NEW_PASSWORD = "the-new-password-1";

    @Autowired private UmsAuthService authService;
    @Autowired private UmsUserRepository userRepository;
    /* UmsUserService holds its own `new BCryptPasswordEncoder()` rather than a
       bean, so there is nothing to inject; an encoder of the same kind verifies
       the hashes it writes. */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Autowired private JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void createPlainUser() {
        removeUser();

        UmsUser user = new UmsUser();
        user.setUserName(USERNAME);
        user.setEmail(USERNAME + "@example.invalid");
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        user.setFirstName("Self");
        user.setLastName("Service");
        user.setStatus(UserStatus.ACTIVE);
        // No administrative rights and no countries: the least privileged shape
        // there is, which is the one the scoped lookup would exclude.
        user.setIsAdmin(false);
        user.setIsSuperAdmin(false);
        user.setIsMinistryUser(false);
        user.setCreatedAt(LocalDateTime.now());
        userId = userRepository.save(user).getId();
    }

    @AfterEach
    void removeUser() {
        // By username rather than by the remembered id, so a leftover from an
        // interrupted run is cleared too. O-25: history rows reference the user.
        jdbc.update("DELETE FROM UMSCORE.UserPasswordHistory WHERE UserId IN (SELECT Id FROM UMSCORE.Users WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE UserId IN (SELECT Id FROM UMSCORE.Users WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM UMSCORE.Users WHERE username = ?", USERNAME);
        userId = null;
    }

    private String storedHash() {
        return userRepository.findById(userId).orElseThrow().getPasswordHash();
    }

    @Test
    @DisplayName("an account with no administrative rights can change its own password")
    void plainUserChangesOwnPassword() {
        boolean changed = AsUser.call(userId, () ->
                authService.changePassword(userId, OLD_PASSWORD, NEW_PASSWORD));

        assertThat(changed)
                .as("self-service change refused — StaffCountrySpec's 'always yourself' clause is the "
                        + "only thing that admits this account to UmsUserService.require")
                .isTrue();

        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedHash()))
                .as("the new password is in force").isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, storedHash()))
                .as("the old password no longer works").isFalse();
    }

    @Test
    @DisplayName("the wrong current password is refused, and changes nothing")
    void wrongCurrentPasswordIsRefused() {
        String before = storedHash();

        boolean changed = AsUser.call(userId, () ->
                authService.changePassword(userId, "not-the-old-password", NEW_PASSWORD));

        assertThat(changed).isFalse();
        assertThat(storedHash()).as("the stored hash is untouched").isEqualTo(before);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, storedHash()))
                .as("the old password still works").isTrue();
    }

    @Test
    @DisplayName("the change stamps PasswordChangedAt, which the profile page shows")
    void stampsWhenItChanged() {
        assertThat(userRepository.findById(userId).orElseThrow().getPasswordChangedAt())
                .as("a new account has never changed its password").isNull();

        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        AsUser.call(userId, () -> authService.changePassword(userId, OLD_PASSWORD, NEW_PASSWORD));

        assertThat(userRepository.findById(userId).orElseThrow().getPasswordChangedAt())
                .as("the profile page reads this field; before it was populated the page showed today's date")
                .isNotNull()
                .isAfter(before);
    }
}
