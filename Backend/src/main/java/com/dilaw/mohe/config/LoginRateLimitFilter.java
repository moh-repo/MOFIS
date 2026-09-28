package com.dilaw.mohe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory throttle for the anonymous credential endpoints: at most
 * {@code rate-limit.login.max-per-minute} attempts per client address per minute,
 * then 429.
 *
 * <p>Which endpoints: the staff login and everything under the student-portal
 * auth prefix — login, forgot-password (which mints an OTP), reset-password
 * (which consumes one) and change-password. They are the requests a caller can
 * make with no credentials, so they are the ones worth guessing against. The
 * student-portal ones were registered with this filter in August 2026 but never
 * actually throttled: the filter compared the full request URI against the one
 * literal {@code /mohe/api/v1/ums/auth/login}, so every portal request fell
 * straight through — and a six-digit OTP with a ten-minute life was open to
 * brute force. The match is now on the context-relative path, so it also
 * survives a change of context path.
 *
 * <p>Only {@code POST} counts. A CORS preflight is an {@code OPTIONS} on the same
 * path and must not eat the budget.
 *
 * <p>The client address comes from {@link ClientIpResolver}, which believes
 * {@code X-Forwarded-For} only when told the process sits behind a proxy.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String STAFF_LOGIN_PATH = "/api/v1/ums/auth/login";
    /** Issues a security-code challenge; a GET, and the only one counted here. */
    static final String STAFF_CAPTCHA_PATH = "/api/v1/ums/auth/captcha";
    static final String PORTAL_AUTH_PREFIX = "/api/v1/student-portal/auth/";
    private static final int DEFAULT_MAX_PER_MINUTE = 10;
    /** One minute, fixed: the window opens on the first hit and is forgotten a minute later. */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxPerMinute;
    private final ClientIpResolver clientIpResolver;
    /**
     * Same scheme as {@link ApiRateLimitFilter}: Caffeine expires an entry a
     * minute after the window opened. The previous plain ConcurrentHashMap never
     * evicted — one entry per source address per endpoint family, for the life
     * of the process — which a distributed source could grow without bound.
     */
    private final Cache<String, AtomicInteger> perIp =
            Caffeine.newBuilder().expireAfterWrite(WINDOW).build();

    public LoginRateLimitFilter(
            @Value("${rate-limit.login.max-per-minute:" + DEFAULT_MAX_PER_MINUTE + "}") int maxPerMinute,
            ClientIpResolver clientIpResolver) {
        this.maxPerMinute = maxPerMinute;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isThrottledRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // One bucket per address per endpoint family, so a burst of staff logins
        // cannot lock a student out of the portal from the same office NAT — and
        // so refreshing an unreadable security code cannot eat the budget the
        // sign-in itself needs.
        String key = clientIpResolver.resolve(request) + "|" + bucketFor(contextRelativePath(request));
        AtomicInteger count = perIp.get(key, k -> new AtomicInteger());
        if (count.incrementAndGet() > maxPerMinute) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(new ObjectMapper().writeValueAsString(
                    Map.of("error", "Too many login attempts. Please try again later.")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * {@code POST} to the staff login or any student-portal auth endpoint, and
     * the one anonymous {@code GET} worth counting: the security-code challenge,
     * which allocates server-side state for every call.
     */
    static boolean isThrottledRequest(HttpServletRequest request) {
        String path = contextRelativePath(request);
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return STAFF_CAPTCHA_PATH.equals(path);
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return STAFF_LOGIN_PATH.equals(path) || isPortalPath(path);
    }

    /** Which budget a path draws on. */
    private static String bucketFor(String path) {
        if (STAFF_CAPTCHA_PATH.equals(path)) {
            return "captcha";
        }
        return isPortalPath(path) ? "portal" : "staff";
    }

    private static boolean isPortalPath(String path) {
        return path.startsWith(PORTAL_AUTH_PREFIX) && path.length() > PORTAL_AUTH_PREFIX.length();
    }

    /** The request URI without the context path, e.g. {@code /api/v1/ums/auth/login}. */
    static String contextRelativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        // Normalise a trailing slash so "/login/" is the same endpoint as "/login".
        while (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }
}
