package com.dilaw.mohe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.Map;

/**
 * Spring Security configuration: JWT for admin API; permit login, actuator, Swagger, student-portal auth.
 * With context-path=/mohe, {@code securityMatcher} / {@code requestMatchers} use servlet paths (no /mohe prefix).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    private static void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of("error", message)));
    }

    /**
     * Prevent Spring Boot from auto-registering JwtAuthFilter in the servlet container's default
     * filter chain. It must only run inside the Spring Security chain (via addFilterBefore below);
     * if it runs outside Spring Security first, SecurityContextHolderFilter will clear the context
     * it set, leaving subsequent @PreAuthorize checks with an empty SecurityContext -> 403.
     */
    /**
     * {@code hasPermission(...)} in every {@code @PreAuthorize} is answered by the
     * policies (security plan R-16). Static, and the evaluator looked up lazily,
     * because the expression handler is created before the service layer and the
     * policies depend on it. {@code setApplicationContext} keeps the
     * {@code @httpMethodSecurity} bean references working.
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(ApplicationContext context) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setApplicationContext(context);
        handler.setPermissionEvaluator(new PermissionEvaluator() {
            private PermissionEvaluator delegate() {
                return context.getBean(com.dilaw.mohe.security.policy.MohePermissionEvaluator.class);
            }

            @Override
            public boolean hasPermission(Authentication authentication, Object target, Object permission) {
                return delegate().hasPermission(authentication, target, permission);
            }

            @Override
            public boolean hasPermission(Authentication authentication, java.io.Serializable targetId,
                                         String targetType, Object permission) {
                return delegate().hasPermission(authentication, targetId, targetType, permission);
            }
        });
        return handler;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(0)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // Without this, AnonymousAuthenticationToken still satisfies .authenticated(), so missing JWT
                // reaches @PreAuthorize and fails with 403 instead of 401.
                .anonymous(anonymous -> anonymous.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Frame protection is not disabled any more. It was, without a recorded
                // reason, and the likely one — framing a report PDF — does not hold: the
                // viewer renders PDF bytes into a canvas through EmbedPDF, and
                // buildViewerPdfUrl, the one frontend method written for an iframe, has no
                // callers. SecurityHeadersFilter now sends X-Frame-Options: DENY and
                // frame-ancestors 'none' on every response instead.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> sendJsonError(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/ums/auth/**").permitAll()
                        .requestMatchers("/api/v1/student-portal/auth/**").permitAll()
                        // Public branding endpoints (non-sensitive; img tags cannot send auth headers)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/sys-settings").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/sys-settings/logo").permitAll()
                        // Report settings (titles, signatories, report layout) are read only by the
                        // reports screens inside the signed-in shell; nothing on the login page asks
                        // for them, and the signatory names are not branding. Was permitAll until
                        // the 27 Aug 2026 pass (plan item 1.7).
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/report-settings/**").authenticated()
                        // Login-page country selector — public read-only lookup, no PII
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/lookup/study-countries-from-student-account").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/lookup/study-countries-for-login").permitAll()
                        // Student portal protected paths — auth enforced by StudentPortalTokenInterceptor (MVC interceptor)
                        .requestMatchers("/api/v1/student-portal/me", "/api/v1/student-portal/me/**").permitAll()
                        .requestMatchers("/api/v1/student-portal/change-requests", "/api/v1/student-portal/change-requests/**").permitAll()
                        // Lookup tables are read-only reference data with no PII; student portal needs them without UMS JWT
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/lookup-tables/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/enums/**").permitAll()
                        // The code lists (names, colours, icons of every status/type) are the same reference data; the
                        // portal loads the bulk payload once after its own login. Writes stay behind SETTINGS_WRITE.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/code-lists", "/api/v1/code-lists/names", "/api/v1/code-lists/*").permitAll()
                        .requestMatchers("/api/**").authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Everything that is not {@code /api/**} and not claimed by the actuator /
     * Swagger chains (order 1, profile-specific).
     *
     * <p>Was {@code permitAll} until the O-18 pass (6 Sep 2026). Nothing legitimate
     * reaches this chain today — the SPA is served by the web server, uploads are
     * served under {@code /api}, and the actuator has its own chain — so the only
     * thing an open default could ever serve is a mapping somebody adds later
     * without thinking about security. The container's error dispatch stays
     * open so a 404/500 still renders as JSON rather than as a second 403.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // The "is it up" banner MoheApplication.home returns, and the error dispatch.
                        .requestMatchers("/", "/error").permitAll()
                        .anyRequest().denyAll());

        return http.build();
    }
}
