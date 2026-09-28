package com.dilaw.mohe.config.tenant;

import com.dilaw.mohe.ums.service.CountryScopeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the caller's country scope once per request and publishes it to
 * {@link TenantContext} for the duration.
 *
 * <p>Ordered after Spring Security so the JWT has already been validated and
 * {@code SecurityContextHolder} holds the authenticated user — the scope cannot
 * be resolved before we know who is asking.
 *
 * <p><b>Why resolution runs as system.</b> Working out which countries a user may
 * see means reading UMSCORE.Users and UMSCORE.UserCountries. Those reads need a
 * connection, and every connection is stamped with the current tenant scope — so
 * resolving the scope while the scope is still unset would deny the very query
 * that determines it. {@link TenantContext#runAsSystem} lifts that for the
 * lookup only; the resolved scope is then in force for the rest of the request.
 *
 * <p>The scope is always cleared in a finally block. Servlet containers pool
 * threads, so a scope left behind would be inherited by the next request served
 * on that thread.
 *
 * <p><b>Student-portal authentication runs as system.</b> The portal is a
 * separate realm (STUDENTCORE) with no country of its own, so establishing which
 * country a portal user belongs to means reading STUDENT_DATA — which is behind
 * the very predicate being established. A login request has no token yet, so it
 * has no scope, so with the V031 policy enabled that read returned nothing and a
 * correct credential was refused with 404 "Student record not found".
 *
 * <p>The elevation has to happen <em>here</em> rather than inside the service.
 * {@link TenantContextDataSource} stamps the scope onto a connection when it is
 * borrowed, and a {@code @Transactional} service method borrows its connection
 * before its body runs — so a {@code runAsSystem} inside that body changes the
 * ThreadLocal for a connection that was already stamped DENY, and does nothing.
 * A filter runs before the transaction exists.
 *
 * <p>The grant is narrow: these paths issue and revoke tokens and reset
 * passwords, and every one of them is gated on a credential the caller had to
 * supply. They return a token or a 401, never rows.
 *
 * <p><b>Staff authentication runs as system too</b>, and for the same reason
 * one layer up: the staff sign-in reads {@code UMSCORE.Users} to find the
 * account and {@code UserCountries} to answer with its scope, and once those
 * tables are under a policy of their own that read is behind the predicate it is
 * trying to establish. The paths are named in {@link #STAFF_AUTH_PATHS} rather
 * than taken as a prefix; see there for why.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class TenantContextFilter extends OncePerRequestFilter {

    private final CountryScopeService countryScopeService;

    public TenantContextFilter(CountryScopeService countryScopeService) {
        this.countryScopeService = countryScopeService;
    }

    /**
     * Portal authentication endpoints, which run as system. Matched on the
     * servlet path so a query string or a trailing slash cannot slip past.
     */
    private static final String PORTAL_AUTH_PREFIX = "/api/v1/student-portal/auth";

    /**
     * Staff sign-in steps that run before anyone knows who is calling, and so
     * before a scope can be resolved for them.
     *
     * <p>Named one at a time rather than taken as the whole {@code /ums/auth}
     * prefix, and the difference is which way the mistake falls. Every endpoint
     * under that prefix is {@code permitAll} at the Spring Security level, but
     * most of them — {@code /me}, {@code /logout}, {@code /change-password},
     * {@code /mfa/status} — are reached with a real token that
     * {@code JwtAuthFilter} has already authenticated, so {@link #resolveScope()}
     * gives them their account's own scope and they need nothing from here. The
     * ones below are reached with no principal at all: no token yet
     * ({@code /login}, {@code /captcha}, {@code /reset-password}), or the pending
     * MFA token that {@code JwtAuthFilter} deliberately ignores ({@code
     * /mfa/verify} and the two enrolment steps, which the sign-in sequence and
     * the profile page both use), or a refresh token read from the body.
     *
     * <p>Elevating the whole prefix would quietly absorb a future endpoint that
     * returns tenanted rows. Listing them means a future pre-authentication
     * endpoint that is forgotten here fails the way {@code /login} would — an
     * obviously broken sign-in — rather than succeeding with more reach than it
     * should have. A loud failure is the better one to inherit.
     *
     * <p>Like the portal grant, this one is narrow in what it can be used for:
     * these paths take a credential, and answer with a token, a QR code or a 401.
     * None of them returns a tenanted row, and the account they read is always
     * the one whose credential was just presented.
     *
     * <p>The one outside {@code /ums/auth} is the sign-in page's country list
     * (plan W5-05): it answers with the attachés that have staff, read from
     * {@code UserCountries} before anyone has signed in, and those are reference
     * rows (STUDY_COUNTRY_IN), not tenanted ones. Elevating inside
     * {@code LookupService} would not do: the service is transactional, so the
     * connection is stamped with the empty scope before the method runs (see
     * {@code StudentPortalLoginCountriesTest}). Latent while the staff policy is
     * off; with it on the list came back empty.
     */
    private static final java.util.Set<String> STAFF_AUTH_PATHS = java.util.Set.of(
            "/api/v1/lookup/study-countries-for-login",
            "/api/v1/ums/auth/login",
            "/api/v1/ums/auth/captcha",
            "/api/v1/ums/auth/refresh",
            "/api/v1/ums/auth/reset-password",
            "/api/v1/ums/auth/mfa/verify",
            "/api/v1/ums/auth/mfa/enrol/start",
            "/api/v1/ums/auth/mfa/enrol/confirm");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            TenantContext.set(runsBeforeIdentity(request) ? TenantContext.Scope.SYSTEM : resolveScope());
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** Portal authentication, or one of the staff sign-in steps above. */
    private static boolean runsBeforeIdentity(HttpServletRequest request) {
        String path = servletPath(request);
        if (path == null) {
            return false;
        }
        if (path.equals(PORTAL_AUTH_PREFIX) || path.startsWith(PORTAL_AUTH_PREFIX + "/")) {
            return true;
        }
        // A trailing slash is the same endpoint to Spring and a different string here.
        return STAFF_AUTH_PATHS.contains(path)
                || (path.endsWith("/") && STAFF_AUTH_PATHS.contains(path.substring(0, path.length() - 1)));
    }

    /** The request path with any context path removed, or null when there is none. */
    private static String servletPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path;
    }

    private TenantContext.Scope resolveScope() {
        try {
            // tenant-bypass: working out the caller's scope reads UMSCORE.Users and
            // UserCountries, which sit under the policy; no scope exists yet because
            // this is the call that produces it. Nothing tenanted is read here.
            return TenantContext.runAsSystem(() -> {
                CountryScopeService.CountryScope scope = countryScopeService.getScope();
                return TenantContext.Scope.of(scope.isMinistryUser(), scope.getAllowedCountryIds());
            });
        } catch (RuntimeException ex) {
            // An unauthenticated or unresolvable caller gets no data rather than
            // all of it. The request continues so that Spring Security returns
            // its own 401/403 rather than this filter inventing one.
            logger.debug("Could not resolve country scope; denying tenant access for this request", ex);
            return TenantContext.Scope.DENY;
        }
    }
}
