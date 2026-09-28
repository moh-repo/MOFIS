package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.repository.UmsUserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Token refresh renews the one session the old token names, or nothing.
 *
 * <p>Until August 2026 (revision item 1.8) a jti that matched no active session —
 * because the user had logged out, or an administrator had terminated it — fell
 * back to the user's first active session, whichever that was. A revoked token
 * presented to {@code /refresh} therefore came back alive, holding some other
 * device's session. Every token the application issues carries a jti, so there
 * is no legitimate refresh without one.
 */
class UmsSessionRotationTest {

    private static final long USER = 42L;

    private final UmsUserSessionRepository repository = mock(UmsUserSessionRepository.class);
    private final UmsUserSessionService service = new UmsUserSessionService();

    @BeforeEach
    void wire() {
        ReflectionTestUtils.setField(service, "sessionRepository", repository);
        ReflectionTestUtils.setField(service, "countryScopeService", mock(CountryScopeService.class));
        ReflectionTestUtils.setField(service, "jwtExpirationSeconds", 3600L);
    }

    @Test
    @DisplayName("a jti that matches no active session is refused — no fallback to another session")
    void revokedJtiIsRefused() {
        when(repository.findActiveByUserIdAndJti(eq(USER), eq("revoked"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThat(service.rotateSessionToken(USER, "revoked", "new-jti")).isFalse();

        verify(repository, never()).findByUserIdAndIsActiveTrue(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("the session the jti names is moved onto the new jti and extended")
    void matchingJtiIsRotated() {
        UmsUserSession session = new UmsUserSession();
        session.setTokenHash("old-jti");
        session.setIsActive(true);
        LocalDateTime before = LocalDateTime.now();
        session.setExpiresAt(before.plusMinutes(1));
        when(repository.findActiveByUserIdAndJti(eq(USER), eq("old-jti"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(session));

        assertThat(service.rotateSessionToken(USER, "old-jti", "new-jti")).isTrue();

        assertThat(session.getTokenHash()).isEqualTo("new-jti");
        assertThat(session.getExpiresAt()).as("extended by the configured lifetime")
                .isAfter(before.plusMinutes(50));
        verify(repository).save(session);
    }

    @Test
    @DisplayName("no jti, blank jti, or no new jti: refused without touching the repository")
    void blankJtiIsRefused() {
        assertThat(service.rotateSessionToken(USER, null, "new-jti")).isFalse();
        assertThat(service.rotateSessionToken(USER, "  ", "new-jti")).isFalse();
        assertThat(service.rotateSessionToken(USER, "old-jti", "")).isFalse();
        assertThat(service.rotateSessionToken(null, "old-jti", "new-jti")).isFalse();

        verify(repository, never()).findActiveByUserIdAndJti(anyLong(), anyString(), any());
        verify(repository, never()).findByUserIdAndIsActiveTrue(anyLong());
        verify(repository, never()).save(any());
    }
}
