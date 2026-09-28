package com.dilaw.mohe.config;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.service.AlertStreamTicketService;
import com.dilaw.mohe.ums.service.JwtTokenService;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import com.dilaw.mohe.ums.service.UserAuthorityCache;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the JWT from {@code Authorization: Bearer <token>} and sets the
 * SecurityContext with the account's UMS permission codes and its tier
 * authority ({@code ROLE_SUPER_ADMIN}, {@code ROLE_MINISTRY_ADMIN},
 * {@code ROLE_MINISTRY_STAFF} or {@code ROLE_COUNTRY_ADMIN}; none for country
 * staff).
 *
 * <p>Security plan R-12 (6 Sep 2026): the filter no longer emits
 * {@code ROLE_ADMIN}. That authority was derived from the account flags and
 * accepted by every write expression, so any admin of any kind passed every
 * gate. An administrator's reach is now the permission bundle of their tier
 * role ({@code UMSCORE.Roles}, V094) plus whatever else they hold; the only
 * expressions that name a tier are the ones that genuinely need one
 * ({@code SYSTEM_RESTORE}, {@code WAFAR_APPROVE}).
 *
 * <p>R-19: codes, role names and tier come from {@link UserAuthorityCache} in
 * one read, evicted on every change that feeds it. The session check below is
 * deliberately not cached.
 *
 * <p>If there is no valid JWT the request continues unauthenticated and the
 * security configuration denies it. Registered only inside the Spring
 * Security chain ({@code FilterRegistrationBean.setEnabled(false)} prevents a
 * duplicate servlet-container registration, which would let
 * {@code SecurityContextHolderFilter} wipe the authentication).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtTokenService jwtTokenService;
    private final UserAuthorityCache authorityCache;
    private final UmsUserSessionService sessionService;
    private final AlertStreamTicketService alertStreamTicketService;
    private final boolean sessionValidationEnabled;

    /** S-09: optional so the filter's unit tests, which build it by hand, need not know about it. */
    private com.dilaw.mohe.ums.service.SessionBindingService sessionBinding;
    private ClientIpResolver clientIpResolver;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSessionBinding(com.dilaw.mohe.ums.service.SessionBindingService sessionBinding) {
        this.sessionBinding = sessionBinding;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setClientIpResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public JwtAuthFilter(JwtTokenService jwtTokenService, UserAuthorityCache authorityCache,
                         UmsUserSessionService sessionService,
                         AlertStreamTicketService alertStreamTicketService,
                         @Value("${jwt.session-validation-enabled:true}") boolean sessionValidationEnabled) {
        this.jwtTokenService = jwtTokenService;
        this.authorityCache = authorityCache;
        this.sessionService = sessionService;
        this.alertStreamTicketService = alertStreamTicketService;
        this.sessionValidationEnabled = sessionValidationEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }
        // EventSource cannot send Authorization headers. The stream used to accept
        // the raw JWT as ?token= (which put a bearer token in access logs) and
        // now accepts only a short-lived single-use ?ticket= minted by
        // POST /api/v1/alerts/stream/ticket. Redeeming yields the JWT the caller
        // presented at mint time, so session revocation still applies below.
        if ((token == null || token.isEmpty()) && isAlertStreamRequest(request)) {
            token = alertStreamTicketService.redeem(request.getParameter("ticket"));
        }

        log.debug("JwtAuthFilter: {} {} hasToken={}", request.getMethod(), request.getRequestURI(), token != null && !token.isEmpty());
        if (token != null && !token.isEmpty()) {
            Claims claims = jwtTokenService.parseToken(token);
            // O-16: a token issued between the password and the second factor
            // authenticates nothing; only the MFA endpoints read it, from the body.
            if (claims != null && JwtTokenService.isMfaPending(claims)) {
                log.debug("JWT ignored: MFA pending for {} {}", request.getMethod(), request.getRequestURI());
                claims = null;
            }
            if (claims != null) {
                Long userId = JwtTokenService.getUserId(claims);
                if (userId != null) {
                    final Long forUser = userId;
                    final String jti = JwtTokenService.getJti(claims);
                    if (sessionValidationEnabled) {
                        java.util.Optional<com.dilaw.mohe.ums.model.UmsUserSession> session =
                                establishingIdentity(() -> sessionService.findActive(forUser, jti));
                        if (session.isEmpty()) {
                            log.debug("JWT rejected: no live session, or the account is not ACTIVE (R6-03), for userId={} on {} {}",
                                    userId, request.getMethod(), request.getRequestURI());
                            filterChain.doFilter(request, response);
                            return;
                        }
                        // S-09: a token used from a different address AND device. A
                        // privileged account's session is ended and the caller told
                        // why, so the SPA can say so on the sign-in page.
                        if (sessionBinding != null) {
                            String ip = clientIpResolver != null ? clientIpResolver.resolve(request) : request.getRemoteAddr();
                            String agent = request.getHeader("User-Agent");
                            var verdict = establishingIdentity(() -> sessionBinding.check(session.get(), ip, agent));
                            if (verdict == com.dilaw.mohe.ums.service.SessionBindingService.Verdict.ENDED) {
                                response.setStatus(401);
                                response.setHeader("X-Session-Ended", "anomaly");
                                response.setContentType("application/json");
                                response.getWriter().write("{\"error\":\"Your session was ended because it was used from a different device. Please sign in again.\",\"code\":\"SESSION_ANOMALY\"}");
                                return;
                            }
                        }
                    }
                    UserAuthorityCache.Snapshot snapshot;
                    try {
                        snapshot = establishingIdentity(() -> authorityCache.snapshot(forUser));
                    } catch (Exception e) {
                        log.error("Failed to load authorities for userId={}: {}", userId, e.getMessage(), e);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (snapshot.deleted()) {
                        log.debug("JWT rejected: userId={} is deleted or unknown", userId);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    List<SimpleGrantedAuthority> authorities = snapshot.authorities().stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    if (snapshot.codes().isEmpty()) {
                        log.warn("No permissions loaded for userId={} on {} {}", userId,
                                request.getMethod(), request.getRequestURI());
                    }
                    log.debug("userId={} tier={} authorities={}", userId, snapshot.tier(), authorities);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // SEC-10: which session this request rides on, so a password change
                    // can end the holder's other sessions and keep this one.
                    if (jti != null && !jti.isBlank()) {
                        request.setAttribute(SecurityRequestUtils.JTI_ATTRIBUTE, jti);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Runs one of this filter's identity reads with full country reach.
     *
     * <p>Three calls need it, and all three ask the same kind of question — <em>who
     * is holding this token</em>, not <em>what may they see</em>:
     *
     * <ul>
     *   <li>{@code sessionService.findActive} — is the session behind this jti still open, on an
     *       account that is still {@code ACTIVE} ({@code UMSCORE.UserSessions}, {@code Users});
     *   <li>{@code sessionBinding.check} — was it opened from this address and device (S-09);
     *   <li>{@code authorityCache.snapshot} — the account's codes, roles and tier
     *       ({@code UMSCORE.Users}, {@code UserRoles}, {@code RolePermissions}).
     * </ul>
     *
     * <p>All three read staff tables that the V102 policy filters, and all three run
     * <em>here</em> — before {@code TenantContextFilter} has resolved a scope, because
     * resolving one means knowing who is asking, which is what this filter is for. The
     * thread is therefore still {@link TenantContext.Scope#DENY}: without the elevation
     * the session would read as closed, and the account would not find itself — an empty
     * snapshot, <b>cached</b> under that user id, and every request afterwards a 403 with
     * nothing in the logs to say why.
     *
     * <p>It has to be at the call site rather than inside the services. {@code snapshot}
     * is {@code @Transactional}, so it borrows its connection — already stamped with the
     * scope in force — before its body runs; an elevation inside it would move a
     * ThreadLocal that connection has stopped reading. This is the same lesson
     * {@code TenantContextFilter} records for the portal-authentication paths.
     *
     * <p>The grant is narrow by construction: each call is keyed by the user id inside a
     * signed token this filter has already verified, and returns that one account's own
     * rows. None of them returns tenanted data, and the scope is put back before the
     * chain continues.
     */
    private <T> T establishingIdentity(java.util.function.Supplier<T> read) {
        return TenantContext.runAsSystem(read);
    }

    private boolean isSessionValid(Long userId, Claims claims) {
        String jti = JwtTokenService.getJti(claims);
        if (jti != null && !jti.isBlank()) {
            return sessionService.isSessionActive(userId, jti);
        }
        // No jti means invalid. Every token createToken() has ever issued carries a
        // jti, so nothing legitimate is rejected; the removed "legacy" fallback let
        // a jti-less token ride on ANY active session, bypassing per-session revocation.
        return false;
    }

    private static boolean isAlertStreamRequest(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && servletPath.endsWith("/alerts/stream")) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/api/v1/alerts/stream");
    }
}
