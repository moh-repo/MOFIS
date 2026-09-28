package com.dilaw.mohe.ums.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-10: rotating the JWT secret does not log everyone out. Tokens signed
 * with the previous secret verify until they expire; new tokens use the new
 * secret; and the MFA secrets, when protected by a key derived from the JWT
 * secret, keep opening under the previous derivation.
 */
class SecretRotationTest {

    private static final String OLD = "old-secret-that-is-at-least-thirty-two-bytes-long!!";
    private static final String NEW = "new-secret-that-is-at-least-thirty-two-bytes-long!!";

    @Test
    @DisplayName("a token signed with the previous secret still verifies; one with an unknown secret never does")
    void jwtPreviousSecret() {
        JwtTokenService before = new JwtTokenService(OLD, 3600);
        String token = before.createToken(7L, "officer", "session-7");

        JwtTokenService rotated = new JwtTokenService(NEW, OLD, 3600);
        Claims claims = rotated.parseToken(token);
        assertThat(claims).isNotNull();
        assertThat(JwtTokenService.getUserId(claims)).isEqualTo(7L);

        // What the rotated service issues is signed with the new secret alone.
        String fresh = rotated.createToken(7L, "officer", "session-8");
        assertThat(new JwtTokenService(NEW, 3600).parseToken(fresh)).isNotNull();
        assertThat(before.parseToken(fresh)).as("the old secret does not verify new tokens").isNull();

        JwtTokenService withoutPrevious = new JwtTokenService(NEW, 3600);
        assertThat(withoutPrevious.parseToken(token)).as("once JWT_SECRET_PREVIOUS is unset the old token is dead").isNull();

        JwtTokenService stranger = new JwtTokenService(NEW, "third-secret-that-is-at-least-thirty-two-bytes", 3600);
        assertThat(stranger.parseToken(token)).isNull();
    }

    @Test
    @DisplayName("a short previous secret is refused, like a short current one")
    void previousSecretMustBeStrong() {
        assertThatThrownBy(() -> new JwtTokenService(NEW, "short", 3600))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("secret-previous");
    }

    @Test
    @DisplayName("MFA secrets derived from the JWT secret open under the previous derivation and can be rewrapped")
    void mfaCipherFollowsRotation() {
        MfaSecretCipher before = new MfaSecretCipher("", OLD);
        String stored = before.encrypt("JBSWY3DPEHPK3PXP");

        MfaSecretCipher rotated = new MfaSecretCipher("", NEW, OLD);
        assertThat(rotated.decrypt(stored)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(rotated.needsRewrap(stored)).isTrue();
        String rewrapped = rotated.encrypt(rotated.decrypt(stored));
        assertThat(rotated.needsRewrap(rewrapped)).isFalse();

        MfaSecretCipher withoutPrevious = new MfaSecretCipher("", NEW);
        assertThatThrownBy(() -> withoutPrevious.decrypt(stored)).isInstanceOf(IllegalStateException.class);
        assertThat(withoutPrevious.decrypt(rewrapped)).isEqualTo("JBSWY3DPEHPK3PXP");

        // An explicit MOHE_MFA_KEY is independent of the JWT secret altogether.
        MfaSecretCipher pinned = new MfaSecretCipher("mfa-key-material", OLD);
        String pinnedValue = pinned.encrypt("X");
        assertThat(new MfaSecretCipher("mfa-key-material", NEW, OLD).decrypt(pinnedValue)).isEqualTo("X");
        assertThat(new MfaSecretCipher("mfa-key-material", NEW).decrypt(pinnedValue)).isEqualTo("X");
    }

    @Test
    @DisplayName("production refuses to start without MOHE_MFA_KEY (W5-04)")
    void productionNeedsAnMfaKey() {
        assertThatThrownBy(() -> new MfaSecretCipher("", "", NEW, "", "prod"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MOHE_MFA_KEY");
        assertThatThrownBy(() -> new MfaSecretCipher(" ", "", NEW, "", "base,prod"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new MfaSecretCipher("", "", NEW, "", "dev").decrypt(new MfaSecretCipher("", NEW).encrypt("X")))
                .as("dev keeps the JWT-derived fallback").isEqualTo("X");
    }

    @Test
    @DisplayName("setting MOHE_MFA_KEY keeps the enrolments made before it, and moves them on use (W5-04)")
    void settingTheKeyKeepsOldEnrolments() {
        String underJwtKey = new MfaSecretCipher("", NEW).encrypt("JBSWY3DPEHPK3PXP");

        MfaSecretCipher withKey = new MfaSecretCipher("mfa-key-material", "", NEW, "", "prod");

        assertThat(withKey.decrypt(underJwtKey)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(withKey.needsRewrap(underJwtKey)).isTrue();
        assertThat(withKey.needsRewrap(withKey.encrypt("JBSWY3DPEHPK3PXP"))).isFalse();
    }

    @Test
    @DisplayName("MOHE_MFA_KEY rotates through MOHE_MFA_KEY_PREVIOUS: secrets and recovery codes keep working (W5-04)")
    void theMfaKeyRotates() {
        MfaSecretCipher old = new MfaSecretCipher("old-mfa-key", "", NEW, "", "prod");
        String secret = old.encrypt("S");
        String recoveryHash = old.hmac("ABCDE-FGHIJ");

        MfaSecretCipher rotated = new MfaSecretCipher("new-mfa-key", "old-mfa-key", NEW, "", "prod");
        assertThat(rotated.decrypt(secret)).isEqualTo("S");
        assertThat(rotated.needsRewrap(secret)).isTrue();
        assertThat(rotated.hmacCandidates("ABCDE-FGHIJ")).as("an old recovery code still matches").contains(recoveryHash);
        assertThat(rotated.hmac("ABCDE-FGHIJ")).as("a new code is stored under the new key").isNotEqualTo(recoveryHash);

        MfaSecretCipher done = new MfaSecretCipher("new-mfa-key", "", NEW, "", "prod");
        assertThatThrownBy(() -> done.decrypt(secret)).isInstanceOf(IllegalStateException.class);
        assertThat(done.needsRewrap(secret)).as("under no key it knows: nothing to rewrap").isFalse();
    }
}
