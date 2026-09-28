package com.dilaw.mohe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Which origins may call this API from a browser, and with what.
 *
 * <p>Two changes from the original, both about the same thing — a misconfiguration
 * being silent rather than loud:
 *
 * <ul>
 *   <li>The allowed request headers were {@code "*"} alongside
 *       {@code allowCredentials(true)}. Spring reflects the wildcard back as whatever
 *       the preflight asked for, so any header a page cared to invent was accepted on
 *       a credentialed cross-origin request. They are now the list the frontend
 *       actually sends — {@link #ALLOWED_HEADERS}, which is short, and which a new
 *       header has to be added to deliberately.</li>
 *   <li>{@code cors.allow-dev-patterns} adds {@code http://localhost:*} and friends.
 *       Production turning that off depended on {@code application-prod.properties}
 *       remembering to say {@code cors.allow-dev-patterns=false} — a line that could
 *       be dropped in an edit with nothing to notice. Under the {@code prod} profile
 *       it is now a startup failure, so the deployment cannot come up quietly
 *       trusting localhost.</li>
 * </ul>
 */
@Configuration
public class WebSecurityConfig {

    /**
     * The request headers the frontend sends, and nothing else.
     *
     * <p>{@code Authorization} (auth.interceptor), {@code Accept-Language}
     * (accept-language.interceptor), {@code X-Country-Id} (country-scope.interceptor),
     * {@code X-Student-Portal-Token} (student-portal-api.service), and the ordinary
     * content-negotiation and cache headers Angular's HttpClient sets — the last two
     * of which translation.service sets by hand.
     *
     * <p>Public because {@code StudentPortalWebConfig} registers its own, more
     * specific CORS mapping for {@code /api/v1/student-portal/**} and must use
     * the same allowlist rather than re-introducing a wildcard.
     */
    public static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Accept-Language",
            "Cache-Control",
            "Pragma",
            "X-Country-Id",
            "X-Student-Portal-Token",
            "X-Request-Id",
            "X-Requested-With"
    );

    private final Environment environment;

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Value("${cors.allow-dev-patterns:true}")
    private boolean allowDevPatterns;

    public WebSecurityConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Refuse to start any non-dev process that would trust localhost origins.
     *
     * <p>Same reasoning as the no-fallback datasource and JWT properties in
     * application-prod.properties: a box that is missing a security setting
     * should fail where someone will see it, not serve. Guarding "not dev"
     * rather than "is prod" means a profile named {@code staging} or a typo of
     * {@code prod} fails closed instead of quietly trusting localhost.
     */
    @PostConstruct
    void rejectDevPatternsOutsideDev() {
        if (allowDevPatterns && !environment.matchesProfiles("dev")) {
            throw new IllegalStateException(
                    "cors.allow-dev-patterns must be false outside the dev profile: it would allow "
                            + "http://localhost:* and https://127.0.0.1:* as credentialed CORS origins. "
                            + "Set cors.allow-dev-patterns=false (CORS_ALLOW_DEV_PATTERNS) and list the "
                            + "real UI origin(s) in CORS_ALLOWED_ORIGINS, or run under the dev profile.");
        }
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        // Build origin patterns from configured allowed-origins
        List<String> patterns = new ArrayList<>();
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            patterns.addAll(Arrays.stream(allowedOrigins.split(",\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        // In production set cors.allow-dev-patterns=false so only explicit origins are used
        if (allowDevPatterns) {
            if (!patterns.contains("http://localhost:4200")) {
                patterns.add("http://localhost:4200");
            }
            patterns.add("http://localhost:*");
            patterns.add("http://127.0.0.1:*");
            patterns.add("https://localhost:*");
            patterns.add("https://127.0.0.1:*");
        }

        final String[] originPatterns = patterns.toArray(new String[0]);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns(originPatterns)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders(ALLOWED_HEADERS.toArray(String[]::new))
                        .exposedHeaders("X-User-Id", "X-Student-Portal-Token")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
