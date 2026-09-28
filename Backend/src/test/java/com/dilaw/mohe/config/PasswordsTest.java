package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** Security plan O-08 (6 Sep 2026): one BCrypt cost, 12, and a transparent upgrade path. */
class PasswordsTest {

    @Test
    @DisplayName("new hashes are made at cost 12")
    void newHashesAreCost12() {
        String hash = Passwords.encoder().encode("Correct-Horse-Battery-Staple");
        assertThat(hash).startsWith("$2a$12$");
        assertThat(Passwords.encoder().matches("Correct-Horse-Battery-Staple", hash)).isTrue();
        assertThat(Passwords.needsRehash(hash)).isFalse();
    }

    @Test
    @DisplayName("a hash made at the old default cost is flagged for re-encoding")
    void oldCostNeedsRehash() {
        String legacy = new BCryptPasswordEncoder(10).encode("Correct-Horse-Battery-Staple");
        assertThat(legacy).startsWith("$2a$10$");
        assertThat(Passwords.needsRehash(legacy)).isTrue();
        // and the new encoder still verifies it, which is what makes the upgrade transparent
        assertThat(Passwords.encoder().matches("Correct-Horse-Battery-Staple", legacy)).isTrue();
    }

    @Test
    @DisplayName("something that is not a BCrypt hash is flagged too; null is not")
    void malformedHashes() {
        assertThat(Passwords.needsRehash("plaintext-from-a-migration")).isTrue();
        assertThat(Passwords.needsRehash("$2b$13$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012345")).isFalse();
        assertThat(Passwords.needsRehash(null)).isFalse();
    }
}
