package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security plan O-20 (6 Sep 2026): API responses are not cached by the browser.
 *
 * <p>No code was added for this: Spring Security's default header writer already
 * sends {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate} on
 * every response of the {@code /api/**} chain. What was missing was proof, and a
 * guard against someone calling {@code headers().disable()} on that chain.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiCacheControlTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("an API response, authenticated or not, carries no-store")
    void apiResponsesAreNoStore() {
        // 401 from the entry point: still a response of the API chain, still no-store.
        ResponseEntity<String> unauthenticated = restTemplate.getForEntity(
                "http://localhost:" + port + "/mohe/api/v1/ums/users", String.class);
        assertThat(unauthenticated.getHeaders().getCacheControl()).contains("no-store");

        // A public GET the login page uses: same chain, same header.
        ResponseEntity<String> publicRead = restTemplate.getForEntity(
                "http://localhost:" + port + "/mohe/api/v1/sys-settings", String.class);
        assertThat(publicRead.getHeaders().getCacheControl()).contains("no-store");
        assertThat(publicRead.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
    }
}
