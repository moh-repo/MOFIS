package com.dilaw.mohe.audit;

import com.dilaw.mohe.scope.AsUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The audit API never hands out a password hash (review M1). The Users trigger
 * records the whole row, bcrypt hash included, on every login. Read-only.
 */
@Tag("integration")
@SpringBootTest
class AuditRedactionApiTest {

    @Autowired private MoheAuditLogController controller;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Users rows come back with PasswordHash masked")
    void usersRowsAreMasked() throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("AUDIT.VIEW"), () ->
                controller.getAllAuditLogs(0, 50, "Users", null, null, null, null, null, null, "desc")
                        .getBody().get("content"));
        assumeTrue(content != null && !content.isEmpty(), "no Users rows in the audit trail");

        String json = objectMapper.writeValueAsString(content);

        assertThat(json).as("no bcrypt hash leaves the API").doesNotContain("$2a$").doesNotContain("$2b$");
        assertThat(json).contains("PasswordHash").contains(AuditValueRedactor.MASK);
        assertThat(content.get(0)).isInstanceOf(AuditLogView.class);
    }

    @Test
    @DisplayName("a single row by id is masked too")
    void byIdIsMasked() {
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("AUDIT.VIEW"), () ->
                controller.getAllAuditLogs(0, 1, "Users", null, null, null, null, null, null, "desc")
                        .getBody().get("content"));
        assumeTrue(content != null && !content.isEmpty(), "no Users rows in the audit trail");
        Long id = ((AuditLogView) content.get(0)).logId();

        AuditLogView one = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("AUDIT.VIEW"), () -> controller.getAuditLogById(id).getBody());

        assertThat(String.valueOf(one.oldValues()) + one.newValues()).doesNotContain("$2a$");
    }
}
