package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The security headers, and the two judgement calls in them.
 *
 * <p>Most of what this filter does is unconditional and would be hard to get wrong.
 * Two things are not: whether HSTS is sent (it must not be over plaintext, and must
 * be behind a proxy that says the browser used TLS) and whether the CSP is sent
 * (Swagger UI is exempt, because {@code default-src 'none'} renders it blank). Those
 * are what these tests are for.
 */
class SecurityHeadersFilterTest {

    private static SecurityHeadersFilter filter(boolean trustForwardedFor) {
        return new SecurityHeadersFilter(new ClientIpResolver(trustForwardedFor));
    }

    private static MockHttpServletResponse run(SecurityHeadersFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    @Test
    @DisplayName("every response carries the unconditional headers")
    void unconditionalHeaders() throws Exception {
        MockHttpServletResponse response = run(filter(false), get("/api/v1/students"));

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()", "geolocation=()");
    }

    /**
     * The one that matters most for this application: report URLs carry the JWT in a
     * {@code ?token=} query parameter, so a Referer leaving the page leaks it.
     */
    @Test
    @DisplayName("Referrer-Policy is no-referrer, not a partial policy")
    void referrerPolicyIsNoReferrer() throws Exception {
        assertThat(run(filter(false), get("/api/reports/export-report")).getHeader("Referrer-Policy"))
                .isEqualTo("no-referrer");
    }

    @Test
    @DisplayName("API responses get default-src 'none'")
    void apiCarriesTheRestrictivePolicy() throws Exception {
        assertThat(run(filter(false), get("/api/v1/students")).getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersFilter.API_CONTENT_SECURITY_POLICY)
                .contains("default-src 'none'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    @DisplayName("Swagger UI is exempt from the CSP, or it renders blank")
    void swaggerIsExemptFromCsp() throws Exception {
        SecurityHeadersFilter filter = filter(false);

        assertThat(run(filter, get("/swagger-ui/index.html")).getHeader("Content-Security-Policy")).isNull();
        assertThat(run(filter, get("/v3/api-docs")).getHeader("Content-Security-Policy")).isNull();

        // Exempt from the CSP only — the rest still applies.
        assertThat(run(filter, get("/swagger-ui/index.html")).getHeader("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }

    @Test
    @DisplayName("no HSTS over plaintext")
    void noHstsOverPlainHttp() throws Exception {
        assertThat(run(filter(false), get("/api/v1/students")).getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    @DisplayName("HSTS over a direct TLS connection")
    void hstsOverTls() throws Exception {
        MockHttpServletRequest request = get("/api/v1/students");
        request.setSecure(true);

        assertThat(run(filter(false), request).getHeader("Strict-Transport-Security"))
                .isEqualTo(SecurityHeadersFilter.STRICT_TRANSPORT_SECURITY);
    }

    /**
     * X-Forwarded-Proto is believed on the same terms as X-Forwarded-For: only behind a
     * proxy the deployment has declared. Otherwise a client sets the header itself and
     * talks the application into pinning the browser to a scheme it may not serve —
     * which, for HSTS with a one-year max-age, is not an error you can take back.
     */
    @Test
    @DisplayName("X-Forwarded-Proto is ignored unless the deployment trusts the proxy")
    void forwardedProtoNeedsATrustedProxy() throws Exception {
        MockHttpServletRequest request = get("/api/v1/students");
        request.addHeader("X-Forwarded-Proto", "https");

        assertThat(run(filter(false), request).getHeader("Strict-Transport-Security")).isNull();
        assertThat(run(filter(true), request).getHeader("Strict-Transport-Security"))
                .isEqualTo(SecurityHeadersFilter.STRICT_TRANSPORT_SECURITY);
    }
}
