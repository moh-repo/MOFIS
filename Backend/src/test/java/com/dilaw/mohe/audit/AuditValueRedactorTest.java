package com.dilaw.mohe.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Credentials never leave the audit API (review M1). */
class AuditValueRedactorTest {

    @Test
    @DisplayName("the password hash in a Users row is masked; the rest is kept")
    void usersRow() {
        String row = "{\"Id\":1,\"Username\":\"superadmin\",\"PasswordHash\":\"$2a$12$abc\","
                + "\"PasswordChangedAt\":\"2026-08-27T16:43:48\",\"FailedLoginAttempts\":0,\"TotpSecret\":\"XYZ\"}";

        String out = AuditValueRedactor.redact(row);

        assertThat(out).doesNotContain("$2a$12$abc").doesNotContain("XYZ")
                .contains("\"PasswordHash\":\"***\"")
                .contains("\"TotpSecret\":\"***\"")
                .contains("\"PasswordChangedAt\":\"2026-08-27T16:43:48\"")
                .contains("\"FailedLoginAttempts\":0")
                .contains("\"Username\":\"superadmin\"");
    }

    @Test
    @DisplayName("a record kept as JSON text inside the row is masked too")
    void nestedJsonText() {
        String row = "{\"RequestType\":\"USER\",\"RequestData\":\"{\\\"password\\\":\\\"secret1\\\",\\\"email\\\":\\\"a@b.co\\\"}\"}";

        String out = AuditValueRedactor.redact(row);

        assertThat(out).doesNotContain("secret1").contains("a@b.co");
    }

    @Test
    @DisplayName("a truncated value that is not valid JSON is masked by pattern")
    void truncated() {
        String row = "{\"Id\":1,\"PasswordHash\":\"$2a$12$SC2PC5PgU..FZ\",\"Email\":\"x@y.z\",\"Upd";

        assertThat(AuditValueRedactor.redact(row)).doesNotContain("SC2PC5PgU").contains("x@y.z");
    }

    @Test
    @DisplayName("null and blank pass through; ordinary columns are not sensitive")
    void passThrough() {
        assertThat(AuditValueRedactor.redact(null)).isNull();
        assertThat(AuditValueRedactor.redact("")).isEmpty();
        assertThat(AuditValueRedactor.isSensitive("STUDENT_NAME")).isFalse();
        assertThat(AuditValueRedactor.isSensitive("TotpEnabled")).isFalse();
        assertThat(AuditValueRedactor.isSensitive("token_hash")).isTrue();
    }
}
