package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.studentportal.model.StudentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A portal user's secrets stay on the server (live QA H3, 24 Sep 2026).
 *
 * <p>{@code GET /api/v1/student-admin/users} returned the JPA entity, and with it
 * every portal user's bcrypt hash and encrypted TOTP secret, to any browser
 * holding the administration page. The entity now ignores both for JSON; this
 * test serialises it the way Spring would and looks for the keys.
 */
class StudentUserSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("the JSON of a user carries neither the password hash nor the TOTP secret")
    void secretsAreNotSerialised() throws Exception {
        StudentUser user = new StudentUser();
        user.setStudentUserId(7);
        user.setStudentId(2);
        user.setUsername("student2@mohe.edu.ye");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQR");
        user.setTotpSecretEnc("enc:not-for-the-browser");
        user.setTotpEnabled(true);
        user.setLastLoginAt(Instant.parse("2026-09-24T08:00:00Z"));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(user));

        assertThat(json.has("passwordHash")).as("passwordHash").isFalse();
        assertThat(json.has("totpSecretEnc")).as("totpSecretEnc").isFalse();
        assertThat(json.toString()).doesNotContain("$2a$10$").doesNotContain("not-for-the-browser");
        // What the administration page binds is still there.
        assertThat(json.path("username").asText()).isEqualTo("student2@mohe.edu.ye");
        assertThat(json.path("studentId").asInt()).isEqualTo(2);
        assertThat(json.path("totpEnabled").asBoolean()).isTrue();
        assertThat(json.path("lastLoginAt").asText()).startsWith("2026-09-24");
    }
}
