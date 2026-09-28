package com.dilaw.mohe.ums.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The password rules applied at the {@code UmsUserService.changePassword} choke
 * point — the one method the self-service change, the admin reset under /auth
 * and the users-screen reset all funnel through — and, through
 * {@link PasswordPolicy}, at the four student-portal password sites.
 *
 * <p>Until September 2026 no strength rule existed anywhere on the backend; the
 * first pass set eight characters with a letter and a digit. Security plan O-25
 * (6 Sep 2026) raised the staff floor to twelve, the portal's to ten, and added
 * the username and common-password rules.
 */
class UmsPasswordPolicyTest {

    @Test
    @DisplayName("under 12 characters is refused for staff")
    void tooShort() {
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("Ab1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12");
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("Abcdefgh123"))
                .as("11 characters is still under the floor")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a letter and a digit are both required")
    void compositionRequired() {
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("123456789012"))
                .as("digits only")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("abcdefghijkl"))
                .as("letters only")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the username may not appear in the password")
    void usernameRefused() {
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("jo_admin-2026-x", "jo_admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("xxJO_ADMINxx99", "jo_admin"))
                .as("case-insensitive")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> UmsUserService.validatePasswordStrength("Correct-Horse-9", "jo_admin"))
                .doesNotThrowAnyException();
        assertThatCode(() -> UsernameShort.check())
                .as("a username under four characters is too short to test for")
                .doesNotThrowAnyException();
    }

    private static final class UsernameShort {
        static void check() {
            UmsUserService.validatePasswordStrength("abc-with-digits-1", "abc");
        }
    }

    @Test
    @DisplayName("common passwords are refused, including with trailing digits and any case")
    void commonRefused() {
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("Password1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("common");
        assertThatThrownBy(() -> UmsUserService.validatePasswordStrength("QWERTYUIOP12"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PasswordPolicy.isCommon("P@ssw0rd2026")).isTrue();
        assertThat(PasswordPolicy.isCommon("Correct-Horse-Battery-9")).isFalse();
    }

    @Test
    @DisplayName("12+ characters with a letter and a digit, not common, passes")
    void acceptable() {
        assertThatCode(() -> UmsUserService.validatePasswordStrength("abcdefghijk1")).doesNotThrowAnyException();
        assertThatCode(() -> UmsUserService.validatePasswordStrength("Correct-Horse-Battery-9")).doesNotThrowAnyException();
        // Arabic letters count as letters.
        assertThatCode(() -> UmsUserService.validatePasswordStrength("كلمةسرطويلةجدا12")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the portal floor is ten characters with the same rules")
    void portalFloor() {
        assertThat(PasswordPolicy.problem("Bluewhal9", "student1", PasswordPolicy.PORTAL_MIN)).contains("10");
        assertThat(PasswordPolicy.problem("Bluewhale9", "student1", PasswordPolicy.PORTAL_MIN)).isNull();
        assertThat(PasswordPolicy.problem("student1xyz9", "student1", PasswordPolicy.PORTAL_MIN)).contains("username");
    }
}
