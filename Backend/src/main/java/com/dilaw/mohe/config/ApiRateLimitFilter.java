package com.dilaw.mohe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user-per-minute throttle for the expensive endpoints: report generation and
 * export, and bulk import. Same fixed-window scheme as {@link LoginRateLimitFilter},
 * keyed on the authenticated user rather than the address.
 *
 * <p>Which endpoints is configuration, not code: {@code mohe.security.rate-limit.report-prefixes}
 * and {@code ...import-prefixes} (comma lists of context-relative path prefixes; see
 * ENVIRONMENT.md), and {@link RateLimitFilterConfig} registers the filter on exactly
 * those prefixes, so the two cannot drift apart. They did (revision item 1.10): from
 * the day it was written until August 2026 this filter guarded
 * {@code /mohe/api/v1/reports}, {@code /mohe/api/v1/academic-performance} and five
 * other paths that no controller has ever served, while the forty-odd real report
 * controllers live under {@code /api/reports}, {@code /api/periodic-reports},
 * {@code /api/custom-reports}, {@code /api/statistical-reports},
 * {@code /api/university-reports} and {@code /api/v1/charts}. The defaults are those
 * real prefixes and {@code ApiRateLimitFilterTest} checks every controller whose name
 * says Report or Chart against them, so a new report module fails a test rather than
 * silently going unthrottled.
 *
 * <p>The match is on the path within the application, so it does not depend on the
 * context path (the old literals had {@code /mohe} baked in). The address used when
 * there is no principal comes from {@link ClientIpResolver}, which believes
 * {@code X-Forwarded-For} only behind a declared proxy; the old code believed it
 * unconditionally. And the windows live in Caffeine caches that expire a minute after
 * the window opens, where the old maps grew by one entry per user per minute for the
 * life of the process.
 *
 * <p>Two buckets under the report prefixes (review item P3). A <em>render</em> is a
 * request whose last path segment is one of {@link #RENDER_SEGMENTS}: the Jasper PDF,
 * Word and Excel endpoints and the server-rendered chart report. Everything else there
 * is JSON (metadata, rows, chart data) and counts against the separate <em>data</em>
 * bucket. Until September 2026 there was one bucket of ten a minute for both, so a
 * report page that loads its metadata two or three times on open used up the minute
 * before the user pressed "export", and the chart dashboards (five or six JSON calls
 * each) were refused outright.
 *
 * <p>Limits: {@code rate-limit.reports.max-per-minute} (renders, default 120),
 * {@code rate-limit.report-data.max-per-minute} (JSON under the report prefixes,
 * default 600: a ceiling for a runaway loop, not something a person reaches) and
 * {@code rate-limit.import.max-per-minute} (default 5). Zero or less switches a bucket
 * off. A refusal carries {@code Retry-After} (seconds until the window closes) and a
 * JSON body with {@code code: RATE_LIMITED}, so the client can say how long to wait.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);

    /** One minute, fixed: the window opens on the first hit and is forgotten a minute later. */
    static final Duration WINDOW = Duration.ofMinutes(1);

    /** Every prefix a report or chart controller is mapped under. Kept as the property default. */
    public static final String DEFAULT_REPORT_PREFIXES =
            "/api/reports,/api/periodic-reports,/api/custom-reports,/api/statistical-reports,"
                    + "/api/university-reports,/api/v1/reports,/api/v1/charts,/api/v1/ums/reports,"
                    + "/api/v1/studentTotalReportAll";

    public static final String DEFAULT_IMPORT_PREFIXES = "/api/v1/import";

    /**
     * Last path segments that make a report request a render (PDF / Word / Excel /
     * chart report). Every report controller maps its export under these names, the
     * individual student's per-document exports ({@code /{id}/id-card/export-report})
     * included.
     */
    static final Set<String> RENDER_SEGMENTS =
            Set.of("export-report", "report-pdf", "doc", "export-excel", "report-xlsx");

    /** Error code in the 429 body; the frontend keys its message on it. */
    static final String RATE_LIMITED_CODE = "RATE_LIMITED";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final int reportsMax;
    private final int reportDataMax;
    private final int importMax;
    private final List<String> reportPrefixes;
    private final List<String> importPrefixes;
    private final ClientIpResolver clientIpResolver;

    private final Cache<String, Window> renderWindows;
    private final Cache<String, Window> dataWindows;
    private final Cache<String, Window> importWindows;

    public ApiRateLimitFilter(
            @Value("${rate-limit.reports.max-per-minute:120}") int reportsMax,
            @Value("${rate-limit.report-data.max-per-minute:600}") int reportDataMax,
            @Value("${rate-limit.import.max-per-minute:5}") int importMax,
            @Value("${mohe.security.rate-limit.report-prefixes:" + DEFAULT_REPORT_PREFIXES + "}") String reportPrefixes,
            @Value("${mohe.security.rate-limit.import-prefixes:" + DEFAULT_IMPORT_PREFIXES + "}") String importPrefixes,
            ClientIpResolver clientIpResolver) {
        this.reportsMax = reportsMax;
        this.reportDataMax = reportDataMax;
        this.importMax = importMax;
        this.reportPrefixes = parsePrefixes(reportPrefixes);
        this.importPrefixes = parsePrefixes(importPrefixes);
        this.clientIpResolver = clientIpResolver;
        this.renderWindows = Caffeine.newBuilder().expireAfterWrite(WINDOW).build();
        this.dataWindows = Caffeine.newBuilder().expireAfterWrite(WINDOW).build();
        this.importWindows = Caffeine.newBuilder().expireAfterWrite(WINDOW).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // SEC-06: decoded and normalised, so a percent-encoded or dot-segment path
        // that the container routes to a report is counted like the plain one.
        String path = LoginRateLimitFilter.contextRelativePath(request);

        if (matchesAny(path, reportPrefixes)) {
            boolean render = isRender(path);
            String key = resolveUserKey(request);
            long retryAfter = render
                    ? retryAfterIfLimited(key, renderWindows, reportsMax)
                    : retryAfterIfLimited(key, dataWindows, reportDataMax);
            if (retryAfter > 0) {
                rejectWith429(response, retryAfter, render
                        ? "Too many report exports. Please wait before retrying."
                        : "Too many report requests. Please wait before retrying.");
                log.warn("Rate limit exceeded for REPORTS ({}): user={} path={}",
                        render ? "render" : "data", key, path);
                return;
            }
        } else if (matchesAny(path, importPrefixes)) {
            String key = resolveUserKey(request);
            long retryAfter = retryAfterIfLimited(key, importWindows, importMax);
            if (retryAfter > 0) {
                rejectWith429(response, retryAfter, "Too many import requests. Please wait before retrying.");
                log.warn("Rate limit exceeded for IMPORT: user={}", key);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** Context-relative prefixes this filter throttles as reports. */
    public List<String> getReportPrefixes() {
        return reportPrefixes;
    }

    /** Context-relative prefixes this filter throttles as imports. */
    public List<String> getImportPrefixes() {
        return importPrefixes;
    }

    /**
     * Servlet URL patterns for the registration: every configured prefix and
     * everything under it. Derived from the same lists the filter matches on, so
     * registering and matching cannot disagree.
     */
    public List<String> urlPatterns() {
        return java.util.stream.Stream.concat(reportPrefixes.stream(), importPrefixes.stream())
                .flatMap(p -> java.util.stream.Stream.of(p, p + "/*"))
                .distinct()
                .toList();
    }

    /**
     * Splits the comma list, trims, drops blanks, adds the leading slash a
     * controller mapping may omit ({@code api/v1/charts}) and strips a trailing one.
     */
    static List<String> parsePrefixes(String commaList) {
        if (commaList == null || commaList.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("/") ? s : "/" + s)
                .map(s -> s.length() > 1 && s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .distinct()
                .toList();
    }

    /**
     * A prefix covers the path itself and every path below it — but not a sibling
     * that merely shares the characters ({@code /api/reports} does not cover
     * {@code /api/reportsettings}).
     */
    static boolean matchesAny(String path, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /** A report path is a render when its last segment names an export format. */
    static boolean isRender(String path) {
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return RENDER_SEGMENTS.contains(trimmed.substring(trimmed.lastIndexOf('/') + 1));
    }

    /**
     * Counts the request in the caller's window. Returns 0 when it is allowed, otherwise
     * the whole seconds (at least 1) until the window closes. A limit of zero or less
     * means the bucket is off.
     */
    private static long retryAfterIfLimited(String key, Cache<String, Window> windows, int max) {
        if (max <= 0) {
            return 0;
        }
        Window window = windows.get(key, k -> new Window(System.nanoTime()));
        if (window.count.incrementAndGet() <= max) {
            return 0;
        }
        long remainingNanos = WINDOW.toNanos() - (System.nanoTime() - window.openedAtNanos);
        return Math.max(1, (remainingNanos + 999_999_999L) / 1_000_000_000L);
    }

    /** One caller's fixed window: when it opened and how many requests it has seen. */
    private static final class Window {
        final long openedAtNanos;
        final AtomicInteger count = new AtomicInteger();

        Window(long openedAtNanos) {
            this.openedAtNanos = openedAtNanos;
        }
    }

    /**
     * The bucket key: the authenticated user id from the security context, or the client address.
     *
     * <p>This filter is registered to run <em>after</em> the Spring Security chain (see
     * {@link RateLimitFilterConfig}), so by the time it runs {@code JwtAuthFilter} has set the
     * principal. It used to read a client-supplied {@code X-User-Id} header instead — which the
     * caller could rotate to escape the limit, or set to another user's id to exhaust theirs.
     */
    private String resolveUserKey(HttpServletRequest request) {
        Optional<Long> userId = SecurityRequestUtils.currentUserId();
        if (userId.isPresent()) return "user:" + userId.get();
        return "ip:" + clientIpResolver.resolve(request);
    }

    private static void rejectWith429(HttpServletResponse response, long retryAfterSeconds, String message)
            throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.writeValueAsString(Map.of(
                "error", message,
                "message", message,
                "code", RATE_LIMITED_CODE,
                "retryAfterSeconds", retryAfterSeconds)));
    }
}
