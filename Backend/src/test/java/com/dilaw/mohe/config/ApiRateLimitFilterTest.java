package com.dilaw.mohe.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report throttle, and whether it guards the endpoints that exist.
 *
 * <p>Revision item 1.10. From the day it was written until August 2026 this filter
 * matched {@code /mohe/api/v1/reports}, {@code /mohe/api/v1/academic-performance} and
 * five other prefixes no controller has ever served, while the real report
 * controllers live under {@code /api/reports}, {@code /api/periodic-reports} and so
 * on. Nothing was throttled. The first test below is the one that stops that
 * recurring: it finds every controller whose name says Report or Chart and checks
 * its mapping against the configured prefixes.
 */
class ApiRateLimitFilterTest {

    private static final int MAX = 3;

    /**
     * Controllers with Report or Chart in the name that are not reports: the chart of
     * accounts (accounting master data) and the report-settings CRUD (whose GET is
     * anonymous, for the login page). Throttling either at ten a minute would be wrong.
     */
    private static final Set<String> NOT_REPORTS = Set.of("ChartOfAccountsController", "ReportSettingsController");

    private static ApiRateLimitFilter filter(boolean trustForwardedFor) {
        return new ApiRateLimitFilter(MAX, MAX, MAX,
                ApiRateLimitFilter.DEFAULT_REPORT_PREFIXES, ApiRateLimitFilter.DEFAULT_IMPORT_PREFIXES,
                new ClientIpResolver(trustForwardedFor));
    }

    private static MockHttpServletRequest get(String contextPath, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", contextPath + path);
        r.setContextPath(contextPath);
        r.setRemoteAddr("10.0.0.1");
        return r;
    }

    private static int status(ApiRateLimitFilter f, MockHttpServletRequest r) throws Exception {
        return response(f, r).getStatus();
    }

    private static MockHttpServletResponse response(ApiRateLimitFilter f, MockHttpServletRequest r) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        f.doFilter(r, resp, chain);
        return resp;
    }

    @Test
    @DisplayName("every controller named *Report* or *Chart* is mapped under a configured report prefix")
    void everyReportControllerIsCovered() {
        List<String> prefixes = ApiRateLimitFilter.parsePrefixes(ApiRateLimitFilter.DEFAULT_REPORT_PREFIXES);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> uncovered = new ArrayList<>();
        int checked = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.dilaw.mohe")) {
            String className = bd.getBeanClassName();
            String simple = className.substring(className.lastIndexOf('.') + 1);
            if (!(simple.contains("Report") || simple.contains("Chart")) || NOT_REPORTS.contains(simple)) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping.class);
            assertThat(mapping).as("%s has a class-level @RequestMapping", simple).isNotNull();
            String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            assertThat(paths).as("%s maps a path", simple).isNotEmpty();
            for (String p : paths) {
                checked++;
                String path = p.startsWith("/") ? p : "/" + p;
                if (!ApiRateLimitFilter.matchesAny(path, prefixes)) {
                    uncovered.add(simple + " -> " + path);
                }
            }
        }
        assertThat(checked).as("the scan found the report controllers").isGreaterThanOrEqualTo(20);
        assertThat(uncovered)
                .as("add the prefix to mohe.security.rate-limit.report-prefixes (ApiRateLimitFilter.DEFAULT_REPORT_PREFIXES)")
                .isEmpty();
    }

    @Test
    @DisplayName("the statistical and university report families, whose controller names say neither, are covered too")
    void familiesWithoutReportInTheName() {
        List<String> prefixes = ApiRateLimitFilter.parsePrefixes(ApiRateLimitFilter.DEFAULT_REPORT_PREFIXES);
        assertThat(ApiRateLimitFilter.matchesAny("/api/statistical-reports/growth-analysis/x", prefixes)).isTrue();
        assertThat(ApiRateLimitFilter.matchesAny("/api/university-reports/fee-structure", prefixes)).isTrue();
        assertThat(ApiRateLimitFilter.matchesAny("/api/custom-reports/export-manager/run", prefixes)).isTrue();
        assertThat(ApiRateLimitFilter.matchesAny("/api/v1/charts/enrollment", prefixes)).isTrue();
    }

    @Test
    @DisplayName("a report request is throttled after the configured number, whatever the context path")
    void throttledIndependentOfContextPath() throws Exception {
        for (String context : new String[]{"/mohe", "", "/other"}) {
            ApiRateLimitFilter f = filter(false);
            for (int i = 0; i < MAX; i++) {
                assertThat(status(f, get(context, "/api/reports/student-status"))).as(context + " #" + (i + 1)).isEqualTo(200);
            }
            assertThat(status(f, get(context, "/api/reports/student-status"))).as(context + " over").isEqualTo(429);
        }
    }

    @Test
    @DisplayName("the import limit is a separate bucket from the report limit")
    void importIsSeparateBucket() throws Exception {
        ApiRateLimitFilter f = filter(false);
        for (int i = 0; i <= MAX; i++) {
            status(f, get("/mohe", "/api/periodic-reports/monthly"));
        }
        assertThat(status(f, get("/mohe", "/api/v1/import/students"))).isEqualTo(200);
    }

    @Test
    @DisplayName("a sibling path that merely shares the characters is not a report")
    void siblingNotMatched() throws Exception {
        ApiRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX * 3; i++) {
            assertThat(status(f, get("/mohe", "/api/reportsettings/x"))).isEqualTo(200);
            assertThat(status(f, get("/mohe", "/api/v1/report-settings"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("X-Forwarded-For is ignored unless the process is told it sits behind a proxy")
    void forwardedForIgnoredByDefault() throws Exception {
        ApiRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX; i++) {
            MockHttpServletRequest r = get("/mohe", "/api/reports/student-status");
            r.addHeader("X-Forwarded-For", "203.0.113." + i);
            status(f, r);
        }
        MockHttpServletRequest r = get("/mohe", "/api/reports/student-status");
        r.addHeader("X-Forwarded-For", "203.0.113.99");
        assertThat(status(f, r)).as("all from the same socket address").isEqualTo(429);
    }

    @Test
    @DisplayName("behind a trusted proxy the first forwarded address is the client")
    void forwardedForHonouredWhenTrusted() throws Exception {
        ApiRateLimitFilter f = filter(true);
        for (int i = 0; i <= MAX; i++) {
            MockHttpServletRequest r = get("/mohe", "/api/reports/student-status");
            r.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
            status(f, r);
        }
        MockHttpServletRequest other = get("/mohe", "/api/reports/student-status");
        other.addHeader("X-Forwarded-For", "203.0.113.8, 10.0.0.1");
        assertThat(status(f, other)).as("a different client behind the same proxy").isEqualTo(200);
    }

    @Test
    @DisplayName("exports and JSON are separate buckets: page loads do not use up the export allowance (P3)")
    void renderAndDataAreSeparateBuckets() throws Exception {
        ApiRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX * 3; i++) {
            status(f, get("/mohe", "/api/reports/student-status/metadata"));
        }
        assertThat(status(f, get("/mohe", "/api/reports/student-status/metadata"))).isEqualTo(429);
        for (int i = 0; i < MAX; i++) {
            assertThat(status(f, get("/mohe", "/api/reports/student-status/export-report"))).isEqualTo(200);
        }
        assertThat(status(f, get("/mohe", "/api/reports/student-status/doc"))).isEqualTo(429);
    }

    @Test
    @DisplayName("every export segment is a render; rows, metadata and chart data are not")
    void renderSegments() {
        assertThat(ApiRateLimitFilter.isRender("/api/reports/student-list/export-report")).isTrue();
        assertThat(ApiRateLimitFilter.isRender("/api/reports/student-list/report-pdf")).isTrue();
        assertThat(ApiRateLimitFilter.isRender("/api/reports/student-list/doc")).isTrue();
        assertThat(ApiRateLimitFilter.isRender("/api/v1/reports/quarterly-financial/export-excel")).isTrue();
        assertThat(ApiRateLimitFilter.isRender("/api/reports/student-list/report-xlsx/")).isTrue();
        assertThat(ApiRateLimitFilter.isRender("/api/reports/individual-student/42/id-card/export-report")).isTrue();
        assertThat(ApiRateLimitFilter.isRender("/api/v1/charts/report/export-report")).isTrue();
        assertThat(ApiRateLimitFilter.isRender("/api/reports/student-list/metadata")).isFalse();
        assertThat(ApiRateLimitFilter.isRender("/api/v1/charts/geo/distribution")).isFalse();
        assertThat(ApiRateLimitFilter.isRender("/api/reports/document-summary")).isFalse();
    }

    @Test
    @DisplayName("a refusal says how long to wait: Retry-After and a RATE_LIMITED body")
    void refusalCarriesRetryAfter() throws Exception {
        ApiRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX; i++) {
            status(f, get("/mohe", "/api/reports/student-status/export-report"));
        }
        MockHttpServletResponse resp = response(f, get("/mohe", "/api/reports/student-status/export-report"));
        assertThat(resp.getStatus()).isEqualTo(429);
        int retryAfter = Integer.parseInt(resp.getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1, 60);
        assertThat(resp.getContentAsString())
                .contains("\"code\":\"RATE_LIMITED\"")
                .contains("\"retryAfterSeconds\":" + retryAfter);
    }

    @Test
    @DisplayName("a limit of zero switches the bucket off")
    void zeroMeansOff() throws Exception {
        ApiRateLimitFilter f = new ApiRateLimitFilter(0, 0, 0,
                ApiRateLimitFilter.DEFAULT_REPORT_PREFIXES, ApiRateLimitFilter.DEFAULT_IMPORT_PREFIXES,
                new ClientIpResolver(false));
        for (int i = 0; i < 50; i++) {
            assertThat(status(f, get("/mohe", "/api/reports/student-status/export-report"))).isEqualTo(200);
            assertThat(status(f, get("/mohe", "/api/v1/charts/geo/distribution"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("the property is a comma list; a missing leading slash or a trailing one is tolerated")
    void prefixParsing() {
        assertThat(ApiRateLimitFilter.parsePrefixes("api/v1/charts, /api/reports/ ,,"))
                .containsExactly("/api/v1/charts", "/api/reports");
        assertThat(ApiRateLimitFilter.parsePrefixes("")).isEmpty();
    }

    @Test
    @DisplayName("the registration patterns are derived from the same prefixes, plus the /api/pdf alias")
    void registrationDerivedFromPrefixes() {
        ApiRateLimitFilter f = filter(false);
        var patterns = new RateLimitFilterConfig().apiRateLimitFilterRegistration(f).getUrlPatterns();
        for (String prefix : ApiRateLimitFilter.parsePrefixes(ApiRateLimitFilter.DEFAULT_REPORT_PREFIXES)) {
            assertThat(patterns).contains(prefix + "/*");
        }
        assertThat(patterns).contains("/api/v1/import/*", "/api/pdf/*");
        assertThat(patterns).noneMatch(p -> p.startsWith("/mohe/"));
    }
}
