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

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration checks against the configured SQL Server: embedded server, HTTP surface,
 * JDBC connectivity, and actuator health (includes datasource when jdbc is present).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class MoheSystemIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DataSource dataSource;

    private String baseUrl() {
        return "http://127.0.0.1:" + port + "/mohe";
    }

    @Test
    @DisplayName("Database: JDBC connection opens")
    void databaseConnectionIsOpen() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(c.isValid(5)).isTrue();
        }
    }

    @Test
    @DisplayName("Backend HTTP: root responds")
    void rootEndpointReturnsMoheBanner() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("MOHE");
    }

    @Test
    @DisplayName("Backend HTTP: actuator health is UP")
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
