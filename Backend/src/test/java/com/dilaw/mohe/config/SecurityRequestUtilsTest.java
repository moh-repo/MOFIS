package com.dilaw.mohe.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityRequestUtilsTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserId_readsJwtPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, List.of()));

        assertThat(SecurityRequestUtils.currentUserId()).contains(42L);
        assertThat(SecurityRequestUtils.currentUserIdOrDefault(1L)).isEqualTo(42L);
    }

    @Test
    void currentUserId_emptyWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();
        assertThat(SecurityRequestUtils.currentUserId()).isEmpty();
        assertThat(SecurityRequestUtils.currentUserIdOrDefault(99L)).isEqualTo(99L);
    }

    @Test
    void requireUserId_throwsWhenMissing() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(SecurityRequestUtils::requireUserId)
                .isInstanceOf(ResponseStatusException.class);
    }
}
