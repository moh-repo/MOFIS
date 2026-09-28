package com.dilaw.mohe.config;

import com.dilaw.mohe.service.AlertStreamTicketService;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.JwtTokenService;
import com.dilaw.mohe.ums.service.UmsPermissionService;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How the SSE alert stream authenticates after the September 2026 change: a
 * short-lived single-use {@code ?ticket=} minted by an authenticated POST,
 * instead of the raw JWT in {@code ?token=} (which landed bearer tokens in
 * access logs). And the jti-less "legacy" session fallback is gone — a token
 * without a session id no longer rides on whatever session happens to be open.
 */
class JwtAuthFilterTicketTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-thirty-two-bytes-long";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 3600);
    // R-19: the filter reads one cached snapshot instead of the permission service and the user row.
    private final com.dilaw.mohe.ums.service.UserAuthorityCache authorityCache =
            mock(com.dilaw.mohe.ums.service.UserAuthorityCache.class);
    private final UmsUserSessionService sessionService = mock(UmsUserSessionService.class);
    private final AlertStreamTicketService ticketService = new AlertStreamTicketService();

    private JwtAuthFilter filter() {
        when(authorityCache.snapshot(anyLong())).thenAnswer(inv -> new com.dilaw.mohe.ums.service.UserAuthorityCache.Snapshot(
                inv.getArgument(0), false, List.of("ALERTS.VIEW"), java.util.Set.of(),
                com.dilaw.mohe.ums.model.AdminTier.COUNTRY_STAFF, null));
        return new JwtAuthFilter(jwtTokenService, authorityCache, sessionService, ticketService, true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest streamRequest() {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/mohe/api/v1/alerts/stream");
        r.setContextPath("/mohe");
        r.setServletPath("/api/v1/alerts/stream");
        return r;
    }

    private Authentication runFilter(MockHttpServletRequest request) throws Exception {
        filter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("a valid ticket authenticates the stream request as the token's user")
    void validTicketAuthenticates() throws Exception {
        when(sessionService.findActive(anyLong(), anyString())).thenReturn(java.util.Optional.of(new com.dilaw.mohe.ums.model.UmsUserSession()));
        String jwt = jwtTokenService.createToken(42L, "officer", "session-1");
        String ticket = ticketService.issue(jwt);

        MockHttpServletRequest request = streamRequest();
        request.setParameter("ticket", ticket);

        Authentication auth = runFilter(request);
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("42");
    }

    @Test
    @DisplayName("a replayed ticket does not authenticate")
    void replayedTicketFails() throws Exception {
        when(sessionService.findActive(anyLong(), anyString())).thenReturn(java.util.Optional.of(new com.dilaw.mohe.ums.model.UmsUserSession()));
        String ticket = ticketService.issue(jwtTokenService.createToken(42L, "officer", "session-1"));
        assertThat(ticketService.redeem(ticket)).isNotNull(); // first (and only) use

        MockHttpServletRequest request = streamRequest();
        request.setParameter("ticket", ticket);

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("the old ?token= parameter no longer authenticates the stream")
    void rawTokenParameterIsDead() throws Exception {
        when(sessionService.findActive(anyLong(), anyString())).thenReturn(java.util.Optional.of(new com.dilaw.mohe.ums.model.UmsUserSession()));
        MockHttpServletRequest request = streamRequest();
        request.setParameter("token", jwtTokenService.createToken(42L, "officer", "session-1"));

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("a token without a jti is invalid — no more riding on any open session")
    void jtiLessTokenRejected() throws Exception {
        // hasActiveSession would have said yes; it must no longer be consulted.
        when(sessionService.hasActiveSession(anyLong())).thenReturn(true);
        when(sessionService.isSessionActive(anyLong(), any())).thenReturn(false);

        // Mint a token whose jti is blank by signing it directly.
        String jwt = io.jsonwebtoken.Jwts.builder()
                .subject("officer")
                .claim("userId", 42L)
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/v1/studentdata");
        request.setContextPath("/mohe");
        request.addHeader("Authorization", "Bearer " + jwt);

        assertThat(runFilter(request)).isNull();
    }
}
