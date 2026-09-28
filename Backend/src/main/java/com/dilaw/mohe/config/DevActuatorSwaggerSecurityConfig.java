package com.dilaw.mohe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Development only: Swagger/OpenAPI and Actuator are open for local development.
 *
 * <p>Was {@code @Profile("!prod")} until September 2026: any profile not literally
 * named {@code prod} ({@code staging}, {@code production}, a typo) got an open
 * actuator and Swagger. Now the open chain exists only under the {@code dev}
 * profile — an unknown profile fails closed, falling through to the
 * {@link SecurityConfig} chains which require authentication.
 *
 * <p>The matchers are servlet paths, without the {@code /mohe} context path — the
 * same correction {@link ProdActuatorSecurityConfig} records for the prod side.
 * With {@code server.servlet.context-path=/mohe} the browser sees
 * {@code /mohe/actuator/health}, but Spring Security matches on the path within
 * the application, {@code /actuator/health}. Until August 2026 (revision item
 * 1.14) this chain matched {@code /mohe/actuator/**} and friends, which no
 * request ever satisfied, so it was dead: actuator and Swagger fell through to
 * the catch-all chain in {@link SecurityConfig}, and whether they were open
 * depended on that chain's rules rather than on this one.
 */
@Configuration
@Profile("dev")
public class DevActuatorSwaggerSecurityConfig {

    /** Context-relative patterns this chain claims. Package-private for the test. */
    static final String[] OPEN_PATTERNS = {
            "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorAndSwaggerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(OPEN_PATTERNS)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
