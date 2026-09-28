package com.dilaw.mohe.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Serves {@code /api/pdf/**} as {@code /api/**}.
 *
 * <p>Browser ad-blockers drop requests whose path contains "reports", "export-report" or
 * "report-pdf", so the frontend ({@code report-pdf.service.ts}) fetches every report PDF
 * through the innocuous prefix {@code /api/pdf/} instead. Under {@code ng serve} the dev
 * proxy rewrote that prefix back; in production nothing did, and every report download
 * was a 404 until this filter existed.
 *
 * <p>The rewrite is a request wrapper, applied before the Spring Security chain, so
 * security matchers, method security and Spring MVC all see the real path
 * ({@code /api/v1/reports/...}) and none of them needs to know the alias exists.
 * Registered by {@link PdfPathRewriteFilterConfig} on {@code /api/pdf/*} only.
 */
public class PdfPathRewriteFilter extends OncePerRequestFilter {

    static final String ALIAS_PREFIX = "/api/pdf/";
    static final String REAL_PREFIX = "/api/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String servletPath = request.getServletPath();
        if (servletPath != null && servletPath.startsWith(ALIAS_PREFIX)) {
            String rewritten = REAL_PREFIX + servletPath.substring(ALIAS_PREFIX.length());
            chain.doFilter(new RewrittenPathRequest(request, rewritten), response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** The same request with a different path; everything else passes through. */
    static final class RewrittenPathRequest extends HttpServletRequestWrapper {

        private final String servletPath;
        private final String requestUri;

        RewrittenPathRequest(HttpServletRequest request, String servletPath) {
            super(request);
            this.servletPath = servletPath;
            String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
            this.requestUri = contextPath + servletPath;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            boolean defaultPort = ("http".equals(getScheme()) && port == 80)
                    || ("https".equals(getScheme()) && port == 443);
            if (port > 0 && !defaultPort) {
                url.append(':').append(port);
            }
            return url.append(requestUri);
        }
    }
}
