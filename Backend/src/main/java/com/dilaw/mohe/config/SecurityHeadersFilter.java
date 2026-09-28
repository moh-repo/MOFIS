package com.dilaw.mohe.config;

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
 * The security response headers every reply carries.
 *
 * <p>Spring Security's own {@code headers()} defaults already supplied some of these
 * — {@code X-Content-Type-Options}, {@code Cache-Control} and HSTS over TLS — on every
 * chain, so this filter is not starting from nothing. What was genuinely absent is the
 * rest: no {@code Content-Security-Policy} anywhere except the one
 * {@code SysSettingsController} puts on the uploaded logo, no {@code Referrer-Policy},
 * no {@code Permissions-Policy}, no {@code Cross-Origin-Opener-Policy} — and the API
 * chain in {@link SecurityConfig} went out of its way to call
 * {@code frameOptions.disable()}, so nothing stopped a page elsewhere from framing a
 * response. The headers the defaults already set are written here too, deliberately:
 * they cost a line each and they mean the guarantee stops depending on which chain a
 * request happened to match.
 *
 * <p>A filter rather than {@code HttpSecurity.headers(...)} because there are three
 * security chains ({@link SecurityConfig}, {@link ProdActuatorSecurityConfig},
 * {@link DevActuatorSwaggerSecurityConfig}) and each would need its own block saying
 * the same thing. One filter ahead of all of them covers every reply, including the
 * container's own error pages, and cannot drift between chains the way three separate
 * {@code headers()} blocks would.
 *
 * <h2>What each header is for here</h2>
 * <ul>
 *   <li><b>Referrer-Policy: no-referrer</b> — the most valuable of the set for this
 *       application. {@code report-pdf.service.ts} builds report URLs that carry the
 *       JWT in a {@code ?token=} query parameter, because a direct navigation is the
 *       only PDF fetch ad-blockers do not drop. Any {@code Referer} sent from such a
 *       page would hand that token to whatever it linked to.</li>
 *   <li><b>X-Content-Type-Options: nosniff</b> — the application serves uploaded
 *       student documents and generated PDFs and workbooks. Content-type sniffing on
 *       a user-supplied file is how an upload becomes a script.</li>
 *   <li><b>X-Frame-Options / frame-ancestors</b> — clickjacking. Nothing in the
 *       frontend frames an API response: the report viewer renders PDF bytes into a
 *       canvas through EmbedPDF ({@code embedpdf-viewer.util.ts}) rather than an
 *       iframe, and {@code buildViewerPdfUrl}, the one method written for an iframe,
 *       has no callers. Both headers are sent; the old one because Safari and some
 *       corporate proxies still only understand that one.</li>
 *   <li><b>Strict-Transport-Security</b> — only on a request that actually arrived
 *       over TLS. A browser ignores HSTS over plaintext anyway, and keying on the
 *       request rather than on the active profile means a {@code dev} run over http
 *       does not pin localhost to https for the developer's whole browser.</li>
 *   <li><b>Content-Security-Policy</b> — {@code default-src 'none'}, which is right
 *       for a pure API: these responses are JSON and file bytes, and none of them is
 *       a document that legitimately loads anything. The single-page app's own policy
 *       cannot come from here, because the SPA is a static build served by nginx or
 *       IIS rather than by this process (there is no {@code src/main/resources/static}).
 *       It is set as a {@code <meta http-equiv>} in {@code Frontend/src/index.html},
 *       and {@code Backend/docs/ENVIRONMENT.md} carries the equivalent web-server
 *       header block for the directives a meta tag cannot express.</li>
 * </ul>
 *
 * <p>Swagger UI is exempt from the CSP: it is a real HTML document with inline
 * styles and scripts, and {@code default-src 'none'} would leave a blank page. It is
 * reachable only under a non-prod profile — {@code application-prod.properties} sets
 * {@code springdoc.*.enabled=false} — so the exemption cannot widen production.
 *
 * <p>The uploaded-logo endpoint sets its own {@code Content-Security-Policy: sandbox}
 * on the {@code ResponseEntity}. That is added rather than replaced, so the response
 * carries both; a browser given two policies enforces both, which is stricter than
 * either alone and is the outcome that endpoint wants.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * Nothing this API returns is a document that loads sub-resources, so the policy
     * denies every fetch directive at once and then closes the three that
     * {@code default-src} does not cover.
     */
    static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
                    + "microphone=(), payment=(), usb=()";

    /** One year, and every subdomain. Sent only over TLS — see the class comment. */
    static final String STRICT_TRANSPORT_SECURITY = "max-age=31536000; includeSubDomains";

    /** Paths that serve HTML of their own and so cannot live under {@code default-src 'none'}. */
    private static final String[] CSP_EXEMPT_PREFIXES = {
            "/swagger-ui", "/v3/api-docs", "/webjars"
    };

    private final ClientIpResolver clientIpResolver;

    public SecurityHeadersFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");

        if (isSecure(request)) {
            response.setHeader("Strict-Transport-Security", STRICT_TRANSPORT_SECURITY);
        }

        if (!isCspExempt(request)) {
            response.setHeader("Content-Security-Policy", API_CONTENT_SECURITY_POLICY);
        }

        chain.doFilter(request, response);
    }

    /**
     * Whether the browser reached this application over TLS.
     *
     * <p>Behind a terminating proxy the request arrives as plain http and only
     * {@code X-Forwarded-Proto} says otherwise — believed on exactly the same terms
     * as {@code X-Forwarded-For} in {@link ClientIpResolver}, because a client that
     * can set the header can otherwise talk the application into pinning it to a
     * scheme the deployment does not serve.
     */
    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        if (!clientIpResolver.isTrustForwardedFor()) {
            return false;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto.split(",")[0].trim());
    }

    private boolean isCspExempt(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null) {
            return false;
        }
        for (String prefix : CSP_EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
