package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.scope.AsUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review H5a, decision DEC-16, plan W5-01: the users list, the search and the admins
 * page answer with a summary per account, not the account entity. The entity carried
 * lockout counters and stages, password and failed-login times and the raw admin
 * flags to every screen that listed staff.
 */
@Tag("integration")
@SpringBootTest
class UsersListSummaryTest {

    private static final Set<String> SUMMARY = Set.of("id", "userName", "firstName", "lastName", "fullName",
            "email", "status", "privilege", "countryNames", "lastLoginAt");
    private static final List<String> NEVER = List.of("failedLoginAttempts", "lockoutStage", "lockedUntil",
            "lastFailedLoginAt", "passwordChangedAt", "mustChangePassword", "isAdmin", "isSuperAdmin",
            "isMinistryUser", "createdBy", "updatedBy", "phone", "countryIds");
    private static final List<String> ADMIN = List.of("UMS_ADMIN.VIEW", "ROLE_SUPER_ADMIN");

    @Autowired private UmsUserController controller;
    @Autowired private ObjectMapper objectMapper;

    private Set<String> keysOfFirstRow(Object page) throws Exception {
        JsonNode content = objectMapper.readTree(objectMapper.writeValueAsString(page)).path("content");
        assertThat(content.size()).as("the dev database has staff accounts").isPositive();
        Set<String> keys = new TreeSet<>();
        content.get(0).fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Test
    @DisplayName("the list, the search and the admins page carry the summary and nothing else")
    void listsCarryTheSummary() throws Exception {
        for (Object page : List.of(
                AsUser.callWithAuthorities(AsUser.SUPERADMIN, ADMIN,
                        () -> controller.getAllUsers(0, 20, "userName", "asc", null, null, null).getBody()),
                AsUser.callWithAuthorities(AsUser.SUPERADMIN, ADMIN,
                        () -> controller.searchUsers("a", 0, 20, "userName", "asc", null, null).getBody()),
                AsUser.callWithAuthorities(AsUser.SUPERADMIN, ADMIN,
                        () -> controller.getAdminUsers(0, 20, "userName", "asc").getBody()))) {
            Set<String> keys = keysOfFirstRow(page);
            assertThat(keys).isSubsetOf(SUMMARY).doesNotContainAnyElementsOf(NEVER);
        }
    }

    @Test
    @DisplayName("the privilege tag comes from the flags: the super admin's row says SUPER_ADMIN")
    void privilegeReplacesTheFlags() throws Exception {
        Object page = AsUser.callWithAuthorities(AsUser.SUPERADMIN, ADMIN,
                () -> controller.getAllUsers(0, 50, "userName", "asc", "superadmin", null, null).getBody());
        JsonNode content = objectMapper.readTree(objectMapper.writeValueAsString(page)).path("content");
        boolean found = false;
        for (JsonNode row : content) {
            if (row.path("id").asLong() == AsUser.SUPERADMIN) {
                assertThat(row.path("privilege").asText()).isEqualTo("SUPER_ADMIN");
                found = true;
            }
        }
        assertThat(found).as("the super admin is listed").isTrue();
    }
}
