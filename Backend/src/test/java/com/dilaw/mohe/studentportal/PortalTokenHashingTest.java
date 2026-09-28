package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.util.PortalTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security plan O-06 (6 Sep 2026): the session table holds a hash of the portal
 * token, never the token.
 */
@ExtendWith(MockitoExtension.class)
class PortalTokenHashingTest {

    /** FIPS 180-4 test vector: SHA-256("abc"). */
    private static final String SHA256_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Mock private StudentSessionRepository sessionRepository;
    @InjectMocks private StudentUserAuthService service;

    @Test
    @DisplayName("the hash is SHA-256, hex, lower-case — the same bytes V086 computes with HASHBYTES")
    void knownVector() {
        assertThat(PortalTokens.hash("abc")).isEqualTo(SHA256_ABC);
        assertThat(PortalTokens.hash("  abc ")).as("trimmed like the header value").isEqualTo(SHA256_ABC);
        assertThat(PortalTokens.hash(null)).isNull();
        assertThat(PortalTokens.hash("a 32-character-random-portal-token")).hasSize(64);
    }

    @Test
    @DisplayName("a presented token is looked up by its hash, so a stored value cannot be replayed")
    void lookupIsByHash() {
        when(sessionRepository.findByTokenAndExpiresAtAfterAndLoggedOutAtIsNull(eq(SHA256_ABC), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThat(service.resolveFromToken("abc")).isEmpty();
        verify(sessionRepository).findByTokenAndExpiresAtAfterAndLoggedOutAtIsNull(eq(SHA256_ABC), any(Instant.class));

        // Presenting the stored hash itself hashes again and matches nothing: a
        // database read no longer yields a usable credential.
        service.resolveFromToken(SHA256_ABC);
        verify(sessionRepository).findByTokenAndExpiresAtAfterAndLoggedOutAtIsNull(
                eq(PortalTokens.hash(SHA256_ABC)), any(Instant.class));
    }

    @Test
    @DisplayName("logout finds the session by hash too")
    void logoutIsByHash() {
        when(sessionRepository.findByToken(SHA256_ABC)).thenReturn(Optional.empty());
        service.logout("abc");
        verify(sessionRepository).findByToken(SHA256_ABC);
    }
}
