package com.dilaw.mohe;

import com.dilaw.mohe.config.PdfPathRewriteFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code /api/pdf/**} alias the frontend uses to get report PDFs past ad-blockers.
 * Only the dev proxy used to undo it; in production every report download was a 404.
 */
class PdfPathRewriteFilterTest {

    private final PdfPathRewriteFilter filter = new PdfPathRewriteFilter();

    private HttpServletRequest passThrough(MockHttpServletRequest request) throws Exception {
        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(req);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) seen.get();
    }

    @Test
    @DisplayName("/api/pdf/... reaches the chain as /api/... with the context path kept")
    void aliasIsRewritten() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/pdf/v1/reports/student-list/doc");
        request.setContextPath("/mohe");
        request.setServletPath("/api/pdf/v1/reports/student-list/doc");
        request.setScheme("https");
        request.setServerName("mohe.example.gov");
        request.setServerPort(443);

        HttpServletRequest downstream = passThrough(request);

        assertThat(downstream.getServletPath()).isEqualTo("/api/v1/reports/student-list/doc");
        assertThat(downstream.getRequestURI()).isEqualTo("/mohe/api/v1/reports/student-list/doc");
        assertThat(downstream.getRequestURL().toString())
                .isEqualTo("https://mohe.example.gov/mohe/api/v1/reports/student-list/doc");
        assertThat(downstream.getContextPath()).isEqualTo("/mohe");
    }

    @Test
    @DisplayName("query string and headers are untouched")
    void restOfRequestIsUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/pdf/v1/reports/x/doc");
        request.setContextPath("/mohe");
        request.setServletPath("/api/pdf/v1/reports/x/doc");
        request.setQueryString("disposition=inline&token=abc");
        request.addHeader("Authorization", "Bearer abc");

        HttpServletRequest downstream = passThrough(request);

        assertThat(downstream.getQueryString()).isEqualTo("disposition=inline&token=abc");
        assertThat(downstream.getHeader("Authorization")).isEqualTo("Bearer abc");
    }

    @Test
    @DisplayName("a path outside the alias passes through as the same request object")
    void otherPathsPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/v1/reports/x/doc");
        request.setContextPath("/mohe");
        request.setServletPath("/api/v1/reports/x/doc");

        HttpServletRequest downstream = passThrough(request);

        assertThat(downstream).isSameAs(request);
    }

    @Test
    @DisplayName("a non-default port is kept in the rebuilt URL")
    void portIsKept() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/pdf/v1/reports/x/doc");
        request.setContextPath("/mohe");
        request.setServletPath("/api/pdf/v1/reports/x/doc");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8082);

        HttpServletRequest downstream = passThrough(request);

        assertThat(downstream.getRequestURL().toString()).isEqualTo("http://localhost:8082/mohe/api/v1/reports/x/doc");
    }
}
