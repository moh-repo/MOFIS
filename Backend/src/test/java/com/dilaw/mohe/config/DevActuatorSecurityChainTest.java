package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev actuator/Swagger chain matches the requests it is for.
 *
 * <p>Revision item 1.14. The chain used to match {@code /mohe/actuator/**} and
 * friends — the browser's view of the URL, with the context path — but Spring
 * Security matches on the path within the application, so it matched nothing and
 * every actuator and Swagger request fell through to the catch-all chain. The
 * requests below are shaped as the container hands them over: request URI with
 * the context path, context path set.
 */
@Tag("integration")
@SpringBootTest
class DevActuatorSecurityChainTest {

    @Autowired
    @Qualifier("actuatorAndSwaggerFilterChain")
    private SecurityFilterChain devChain;

    private static MockHttpServletRequest get(String contextPath, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", contextPath + path);
        r.setContextPath(contextPath);
        r.setServletPath(path);
        return r;
    }

    @Test
    @DisplayName("the chain matches /actuator/health under the /mohe context path")
    void matchesActuatorHealth() {
        assertThat(devChain.matches(get("/mohe", "/actuator/health"))).isTrue();
        assertThat(devChain.matches(get("/mohe", "/actuator/info"))).isTrue();
        assertThat(devChain.matches(get("/mohe", "/v3/api-docs/x"))).isTrue();
        assertThat(devChain.matches(get("/mohe", "/swagger-ui.html"))).isTrue();
    }

    @Test
    @DisplayName("and under no context path — the match is context-relative")
    void matchesWithoutContextPath() {
        assertThat(devChain.matches(get("", "/actuator/health"))).isTrue();
    }

    @Test
    @DisplayName("the API is not this chain's business")
    void doesNotClaimTheApi() {
        assertThat(devChain.matches(get("/mohe", "/api/v1/ums/auth/login"))).isFalse();
        assertThat(devChain.matches(get("/mohe", "/api/v1/sys-settings"))).isFalse();
    }

    @Test
    @DisplayName("no pattern carries the context path")
    void patternsAreContextRelative() {
        assertThat(DevActuatorSwaggerSecurityConfig.OPEN_PATTERNS).noneMatch(p -> p.startsWith("/mohe"));
    }

    @Test
    @DisplayName("the open chain exists only under the dev profile — not merely 'not prod'")
    void openChainIsDevOnly() {
        // @Profile("!prod") would open actuator and Swagger on any profile that
        // isn't literally named prod (staging, production, a typo). The config
        // must opt in to dev explicitly so unknown profiles fail closed.
        org.springframework.context.annotation.Profile profile =
                DevActuatorSwaggerSecurityConfig.class.getAnnotation(
                        org.springframework.context.annotation.Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("dev");
    }
}
