package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-03: a privileged grant is requested by one administrator and applied only
 * when a second has signed; the super admin is exempt and audited.
 *
 * <p>A probe role carrying {@code PAYMENT_BATCH.APPROVE} (privileged: an
 * approval code) is handed to {@code jo_staff} by the ministry admin
 * ({@code admin}); that becomes a {@code USER_PRIVILEGE_CHANGE} request, which
 * the super admin signs, whereupon the role is on the account. The super admin
 * granting the same role directly leaves a {@code PRIVILEGE_CHANGE_DIRECT} row.
 */
@Tag("integration")
@SpringBootTest
class FourEyesTest {

    private static final String PROBE_ROLE = "FOUR_EYES_PROBE";
    private static final long JO_STAFF = 14L;
    private static final long SA_STAFF = 11L;

    @Autowired private UmsUserService userService;
    @Autowired private ApprovalWorkflowService workflow;
    @Autowired private PrivilegedChangeService privilegedChanges;
    @Autowired private UserAuthorityCache cache;
    @Autowired private JdbcTemplate jdbc;

    private long probeRole;

    @BeforeEach
    void probe() {
        cleanUp();
        jdbc.update("INSERT INTO UMSCORE.Roles (RoleName, Description, IsSystemRole, IsSystem, IsActive, Priority, CreatedAt) "
                + "VALUES (?, 'S-03 probe', 0, 0, 1, 1, SYSUTCDATETIME())", PROBE_ROLE);
        probeRole = jdbc.queryForObject("SELECT Id FROM UMSCORE.Roles WHERE RoleName = ?", Long.class, PROBE_ROLE);
        jdbc.update("INSERT INTO UMSCORE.RolePermissions (RoleId, PermissionId, GrantedAt, IsActive) "
                + "SELECT ?, Id, SYSUTCDATETIME(), 1 FROM UMSCORE.Permissions WHERE PermissionCode = 'PAYMENT_BATCH.APPROVE'", probeRole);
        cache.evictAll();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.runAsSystem(() -> {
            jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId IN (SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityType = 'USER_PRIVILEGE_CHANGE')");
            jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId IN (SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityType = 'USER_PRIVILEGE_CHANGE')");
            jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE EntityType = 'USER_PRIVILEGE_CHANGE'");
            // After the delete, not before: the DELETE trigger on ApprovalRequests
            // writes an audit row of its own (V047), and that row has no country.
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND (ISNULL(OLD_VALUES, '') LIKE '%\"EntityType\":\"USER_PRIVILEGE_CHANGE\"%' OR ISNULL(NEW_VALUES, '') LIKE '%\"EntityType\":\"USER_PRIVILEGE_CHANGE\"%')");
            jdbc.update("DELETE ur FROM UMSCORE.UserRoles ur JOIN UMSCORE.Roles r ON r.Id = ur.RoleId WHERE r.RoleName = ?", PROBE_ROLE);
            jdbc.update("DELETE rp FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Roles r ON r.Id = rp.RoleId WHERE r.RoleName = ?", PROBE_ROLE);
            jdbc.update("DELETE FROM UMSCORE.Roles WHERE RoleName = ?", PROBE_ROLE);
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE Action LIKE 'PRIVILEGE_CHANGE_%' OR (Action LIKE 'APPROVAL_%' AND ResourceType = 'APPROVAL_REQUEST' AND UserId = ?)", AsUser.SUPERADMIN);
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE ResourceType IN ('USER_ROLE') AND ResourceId IN (?, ?) AND NewValue = ?", JO_STAFF, SA_STAFF, PROBE_ROLE);
            return null;
        });
        cache.evictAll();
    }

    private int holdsProbe(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserRoles WHERE UserId = ? AND RoleId = ? AND IsActive = 1",
                Integer.class, userId, probeRole);
    }

    private int audit(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE Action = ?", Integer.class, action);
    }

    @Test
    @DisplayName("a ministry admin's privileged grant becomes a request; the role lands only when a second administrator signs")
    void requestedThenApplied() {
        assertThat(privilegedChanges.roleIsPrivileged(probeRole)).isTrue();
        assertThat(privilegedChanges.requiresApproval(AsUser.ADMIN)).isTrue();

        Optional<ApprovalRequest> raised = AsUser.call(AsUser.ADMIN,
                () -> userService.assignRoleToUserOrRequest(JO_STAFF, probeRole, AsUser.ADMIN));
        assertThat(raised).isPresent();
        ApprovalRequest request = raised.get();
        assertThat(request.getEntityType()).isEqualTo(PrivilegedChangeService.ENTITY_USER_PRIVILEGE);
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(holdsProbe(JO_STAFF)).as("nothing applied yet").isZero();
        assertThat(audit("PRIVILEGE_CHANGE_REQUESTED")).isEqualTo(1);

        // The requester cannot sign their own request (O-01); the super admin can.
        AsUser.call(AsUser.SUPERADMIN, () -> workflow.approveStep(request.getId(), null, "second pair of eyes"));

        assertThat(holdsProbe(JO_STAFF)).as("applied on execution").isEqualTo(1);
        assertThat(TenantContext.runAsSystem(() -> jdbc.queryForObject(
                "SELECT Status FROM ALERTCORE.ApprovalRequests WHERE Id = ?", String.class, request.getId())))
                .isEqualTo("EXECUTED");
        assertThat(audit("PRIVILEGE_CHANGE_EXECUTED")).isEqualTo(1);
    }

    @Test
    @DisplayName("the super admin applies a privileged grant at once, and the exemption leaves an audit row")
    void superAdminIsExemptAndAudited() {
        assertThat(privilegedChanges.requiresApproval(AsUser.SUPERADMIN)).isFalse();
        Optional<ApprovalRequest> raised = AsUser.call(AsUser.SUPERADMIN,
                () -> userService.assignRoleToUserOrRequest(SA_STAFF, probeRole, AsUser.SUPERADMIN));
        assertThat(raised).isEmpty();
        assertThat(holdsProbe(SA_STAFF)).isEqualTo(1);
        assertThat(audit("PRIVILEGE_CHANGE_DIRECT")).isEqualTo(1);
    }

    @Test
    @DisplayName("what counts as privileged: approval codes, restore, UMS administration, tier roles; nothing else")
    void whatIsPrivileged() {
        assertThat(PrivilegedChangeService.isPrivilegedCode("PAYMENT_BATCH.APPROVE")).isTrue();
        assertThat(PrivilegedChangeService.isPrivilegedCode("APP_PENDING.APPROVE")).as("signing requests is an approval right").isTrue();
        assertThat(PrivilegedChangeService.isPrivilegedCode("SYSTEM.RESTORE")).isTrue();
        assertThat(PrivilegedChangeService.isPrivilegedCode("UMS_ADMIN.EDIT")).isTrue();
        assertThat(PrivilegedChangeService.isPrivilegedCode("STUDENTS.VIEW")).isFalse();
        assertThat(PrivilegedChangeService.isPrivilegedCode("WAFAR.EDIT")).isFalse();
        // A role carrying none of those is applied at once by anyone entitled to grant it.
        Long plainRole = jdbc.queryForObject("""
                SELECT MIN(r.Id) FROM UMSCORE.Roles r WHERE r.IsActive = 1
                   AND r.RoleName NOT IN ('SUPER_ADMIN','MINISTRY_ADMIN','MINISTRY_STAFF','COUNTRY_ADMIN')
                   AND NOT EXISTS (SELECT 1 FROM UMSCORE.RolePermissions rp JOIN UMSCORE.Permissions p ON p.Id = rp.PermissionId
                                    WHERE rp.RoleId = r.Id AND rp.IsActive = 1
                                      AND (p.PermissionCode LIKE '%.APPROVE' OR p.PermissionCode = 'SYSTEM.RESTORE' OR p.PermissionCode LIKE 'UMS[_]ADMIN.%'))
                """, Long.class);
        assertThat(plainRole).as("the seed has at least one role without a privileged code").isNotNull();
        assertThat(privilegedChanges.roleIsPrivileged(plainRole)).isFalse();
        assertThat(privilegedChanges.roleIsPrivileged(jdbc.queryForObject(
                "SELECT Id FROM UMSCORE.Roles WHERE RoleName = 'MINISTRY_ADMIN'", Long.class))).as("a tier role").isTrue();
    }
}
