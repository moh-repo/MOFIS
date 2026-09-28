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
 * {@code GET /api/v1/report-settings/**} needs a login (plan item 1.7).
 *
 * <p>It was {@code permitAll} beside the branding endpoints, but nothing on
 * the login page reads it: the only callers are the report screens and the
 * settings page inside the signed-in shell ({@code report-settings.service.ts},
 * {@code financial-data-form*.component.ts}, {@code report-settings.component.ts}).
 * The signatory names and titles it serves are not branding.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportSettingsRequireLoginTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port + "/mohe";
    }

    @Test
    @DisplayName("anonymous GET on report settings is 401")
    void anonymousIs401() {
        ResponseEntity<String> list = rest.getForEntity(base() + "/api/v1/report-settings", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> one = rest.getForEntity(base() + "/api/v1/report-settings/PAYMENT_AUTH", String.class);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("positive control: the branding endpoint beside it is still public")
    void brandingStaysPublic() {
        ResponseEntity<String> branding = rest.getForEntity(base() + "/api/v1/sys-settings", String.class);
        assertThat(branding.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
