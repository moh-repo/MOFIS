package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Found by the ZAP baseline (O-37, 7 Sep 2026): {@code /api/v1/ums/auth/**} is
 * permitAll at the chain so the login can live there, so an anonymous request
 * reaches the method-secured {@code /me} and is refused with an
 * {@code AuthenticationException} rather than an {@code AccessDeniedException}.
 * Nothing handled it, so the caller saw a 500 with the generic message.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnonymousOnPublicPathTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("an anonymous call to a method-secured handler on a public path is 401, not 500")
    void anonymousProfileIs401() {
        ResponseEntity<String> me = restTemplate.getForEntity(
                "http://localhost:" + port + "/mohe/api/v1/ums/auth/me", String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me.getBody()).contains("Authentication required").doesNotContain("unexpected error");
    }
}
