package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.ums.model.UmsRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two faults on the permission screens (live QA #5, QA B10, N12).
 *
 * <p>{@code GET /ums/groups/{id}/roles} answered 500 for every group holding a
 * role: the roles were read through {@code UmsGroupRole.getRole()}, LAZY proxies
 * that Jackson serialised after the transaction had closed. The same bug class as
 * the approvals listing on 20 Sep. Serialising the controller's answer with the
 * application's mapper, outside any transaction, is what the browser did.
 *
 * <p>{@code PUT /ums/roles/{id}/permissions} with an empty list strips the role
 * of everything, and a screen whose permission list failed to load sends exactly
 * that. It is now refused unless {@code clearAll=true} says so.
 */
@Tag("integration")
@SpringBootTest
class GroupRolesAndClearAllTest {

    /** Through the controllers, so their @PreAuthorize gates apply. */
    // SEC-03: role and group writes are ministry-only (admin tools), so the tier authority rides along.
    private static final List<String> UMS_ADMIN = List.of("UMS_ADMIN.VIEW", "UMS_ADMIN.EDIT", "ROLE_SUPER_ADMIN");

    @Autowired private UmsGroupController groups;
    @Autowired private UmsRoleController roles;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("a group's roles serialise as roles, outside any transaction")
    void groupRolesSerialise() throws Exception {
        Long groupId = jdbc.queryForObject(
                "SELECT TOP 1 GroupId FROM UMSCORE.GroupRoles WHERE IsActive = 1 ORDER BY GroupId", Long.class);

        List<UmsRole> body = AsUser.callWithAuthorities(AsUser.SUPERADMIN, UMS_ADMIN, () -> groups.getGroupRoles(groupId).getBody());

        assertThat(body).isNotEmpty();
        String json = objectMapper.writeValueAsString(body);
        assertThat(json).contains("\"roleName\"").doesNotContain("hibernateLazyInitializer");
    }

    @Test
    @DisplayName("an empty permission list is refused unless clearAll says so")
    void emptyPermissionListNeedsClearAll() {
        Long roleId = jdbc.queryForObject(
                "SELECT TOP 1 Id FROM UMSCORE.Roles WHERE IsSystem = 0 ORDER BY Id", Long.class);
        List<Long> before = activePermissions(roleId);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, UMS_ADMIN,
                () -> roles.setRolePermissions(roleId, List.of(), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clearAll");

        assertThat(activePermissions(roleId)).as("the refused call must leave the role as it was").isEqualTo(before);
    }

    private List<Long> activePermissions(Long roleId) {
        return jdbc.queryForList(
                "SELECT PermissionId FROM UMSCORE.RolePermissions WHERE RoleId = ? AND IsActive = 1 ORDER BY PermissionId",
                Long.class, roleId);
    }
}
