package com.dilaw.mohe.config;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Production: Actuator is not fully public. Health (for load balancers) stays anonymous;
 * other actuator endpoints require HTTP basic with the {@code spring.security.user.*}
 * credentials ({@code MOHE_ACTUATOR_USER} / {@code MOHE_ACTUATOR_PASSWORD}).
 * Swagger is disabled via {@code springdoc.*.enabled=false} in application-prod.properties.
 *
 * <p>Two things this class has to do itself, and did not until August 2026:
 * <ul>
 *   <li>Match on the <em>servlet</em> path. With {@code server.servlet.context-path=/mohe}
 *       the browser sees {@code /mohe/actuator/health}, but Spring Security matchers see
 *       {@code /actuator/health}. The chain used to match {@code /mohe/actuator/**}, which no
 *       request ever satisfied, so actuator fell through to the catch-all chain in
 *       {@link SecurityConfig} and was public.</li>
 *   <li>Provide the user store. {@code application.properties} excludes
 *       {@code UserDetailsServiceAutoConfiguration} (the API uses JWT, not Spring users), so
 *       {@code spring.security.user.*} bound to nothing and {@code httpBasic()} had no one to
 *       authenticate against. The in-memory manager below is that store; it holds the single
 *       actuator account and nothing else.</li>
 * </ul>
 */
@Configuration
@Profile("prod")
public class ProdActuatorSecurityConfig {

    static final String ACTUATOR_ROLE = "ACTUATOR";

    /** The one account HTTP basic on the actuator accepts, from {@code spring.security.user.*}. */
    @Bean
    public UserDetailsService actuatorUserDetailsService(SecurityProperties security) {
        SecurityProperties.User user = security.getUser();
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(User.withUsername(user.getName())
                .password(encoder.encode(user.getPassword()))
                .roles(ACTUATOR_ROLE)
                .build());
    }

    @Bean
    @Order(1)
    public SecurityFilterChain prodActuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().hasRole(ACTUATOR_ROLE))
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
