package com.dilaw.mohe.config;

import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.service.JwtTokenService;
import com.dilaw.mohe.ums.service.SessionBindingService;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import com.dilaw.mohe.ums.service.UserAuthorityCache;
import com.dilaw.mohe.service.AlertStreamTicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S-09 at the filter: the binding verdict decides whether the request goes on.
 * ENDED answers 401 with the reason in a header the SPA reads; anything else
 * authenticates as before.
 */
class JwtAuthFilterBindingTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-thirty-two-bytes-long";
    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 3600);
    private final UserAuthorityCache authorityCache = mock(UserAuthorityCache.class);
    private final UmsUserSessionService sessionService = mock(UmsUserSessionService.class);
    private final SessionBindingService binding = mock(SessionBindingService.class);
    private final ClientIpResolver ipResolver = new ClientIpResolver(false);

    private JwtAuthFilter filter() {
        when(authorityCache.snapshot(anyLong())).thenAnswer(inv -> new UserAuthorityCache.Snapshot(
                inv.getArgument(0), false, List.of("STUDENTS.VIEW"), Set.of(), AdminTier.COUNTRY_STAFF, null));
        JwtAuthFilter f = new JwtAuthFilter(jwtTokenService, authorityCache, sessionService,
                new AlertStreamTicketService(), true);
        f.setSessionBinding(binding);
        f.setClientIpResolver(ipResolver);
        return f;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/mohe/api/v1/students");
        r.addHeader("Authorization", "Bearer " + jwtTokenService.createToken(42L, "officer", "session-1"));
        r.addHeader("User-Agent", "Mozilla/5.0 test");
        r.setRemoteAddr("203.0.113.9");
        return r;
    }

    @Test
    @DisplayName("an ended session answers 401 with the reason and authenticates nothing")
    void endedSessionIsRefused() throws Exception {
        UmsUserSession session = new UmsUserSession();
        when(sessionService.findActive(eq(42L), anyString())).thenReturn(Optional.of(session));
        when(binding.check(eq(session), eq("203.0.113.9"), eq("Mozilla/5.0 test")))
                .thenReturn(SessionBindingService.Verdict.ENDED);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter().doFilter(request(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Session-Ended")).isEqualTo("anomaly");
        assertThat(response.getContentAsString()).contains("SESSION_ANOMALY");
        assertThat(chain.getRequest()).as("the chain never ran").isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a rebound or untouched session authenticates as before")
    void reboundSessionGoesOn() throws Exception {
        UmsUserSession session = new UmsUserSession();
        when(sessionService.findActive(eq(42L), anyString())).thenReturn(Optional.of(session));
        when(binding.check(any(), any(), any())).thenReturn(SessionBindingService.Verdict.REBOUND);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request(), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("42");
    }

    @Test
    @DisplayName("no live session still means no authentication")
    void noSessionStillRefused() throws Exception {
        when(sessionService.findActive(anyLong(), anyString())).thenReturn(Optional.empty());
        filter().doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
