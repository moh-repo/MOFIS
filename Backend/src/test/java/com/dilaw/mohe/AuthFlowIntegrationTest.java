package com.dilaw.mohe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the UMS authentication flow.
 * Requires a running SQL Server with test data (teststudent account).
 * Skipped in CI without a live DB — mark tests with @Tag("integration").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AuthFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String base() {
        return "http://localhost:" + port + "/mohe";
    }

    @Test
    @DisplayName("Login with missing credentials returns 400")
    void loginMissingCredentials_returns400() {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                base() + "/api/v1/ums/auth/login",
                Map.of(),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Login body without a username returns 400 (Bean Validation on the request record)")
    void loginMissingUsername_returns400() {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                base() + "/api/v1/ums/auth/login",
                Map.of("password", "whatever"),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Login without a security code is refused before the credentials are read")
    void loginWithoutCaptcha_isRefused() {
        // The challenge moved to the server in August 2026: it used to be drawn
        // and marked by the login page, so a request made anywhere else — this
        // one, or curl — never met it. Now the fields are required, and a body
        // without them does not reach the password check at all.
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                base() + "/api/v1/ums/auth/login",
                Map.of("username", "nonexistent_user_xyz", "password", "wrong"),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Login with an unsolved security code returns 401 and says which check failed")
    void loginWithWrongCaptcha_returns401() {
        // A test cannot know the answer — that is the point of the change — so
        // this is the furthest an HTTP-level test can go. What happens after a
        // solved challenge is covered by LoginLockoutTest against the service.
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                base() + "/api/v1/ums/auth/login",
                Map.of("username", "nonexistent_user_xyz", "password", "wrong",
                        "captchaId", "not-a-real-challenge", "captchaAnswer", "ZZZZZ"),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).containsEntry("captchaFailed", true);
    }

    @Test
    @DisplayName("The security-code endpoint is open and serves a PNG with its id in a header")
    void captchaEndpoint_isAnonymous() {
        ResponseEntity<byte[]> resp = restTemplate.getForEntity(
                base() + "/api/v1/ums/auth/captcha", byte[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getFirst("X-Captcha-Id")).isNotBlank();
        assertThat(resp.getBody()).isNotEmpty();
    }

    @Test
    @DisplayName("Accessing protected endpoint without JWT returns 401")
    void protectedEndpointWithoutToken_returns401() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                base() + "/api/v1/studentdata/studentall",
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JWT refresh with no token returns 401")
    void refreshWithoutToken_returns401() {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                base() + "/api/v1/ums/auth/refresh",
                null,
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Health endpoint is reachable without auth")
    void actuatorHealth_isReachable() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                base() + "/actuator/health",
                Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
