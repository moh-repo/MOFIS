package com.dilaw.mohe.scope;

import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.config.tenant.TenantContext;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Security plan O-01, O-02 and N-04 on the generic approval workflow (6 Sep 2026).
 *
 * <ul>
 *   <li>O-01 — the officer who raised a request cannot approve it. The accounting
 *       and Wafar services already refused this; the student-data / finance /
 *       document path did not.</li>
 *   <li>O-02 — {@code IsAdmin} no longer bypasses a level's required role; only
 *       the super admin does, and that is audited.</li>
 *   <li>N-04 — a {@code STUDENT_FINANCE_WAFAR} request is approved by the
 *       student's attaché, never by a ministry account.</li>
 * </ul>
 *
 * <p>Each test raises one PENDING fixture as a raw row (as SYSTEM, so it passes
 * the tenant block predicate) and removes it, with its step and audit rows, in a
 * finally. Nothing is ever approved, so no request data is applied.
 */
@Tag("integration")
@SpringBootTest
class ApprovalSeparationOfDutiesTest {

    private static final int JORDAN = 1004;
    private static final int EGYPT = 1;

    @Autowired private ApprovalWorkflowService workflowService;
    @Autowired private com.dilaw.mohe.ums.service.UmsUserService userService;
    @Autowired private JdbcTemplate jdbc;

    private static void assertForbidden(String what, ThrowingCallable call) {
        assertThatThrownBy(call)
                .as(what)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** A PENDING request at level 1 of {@code configId}, with its step row. */
    private long raise(long configId, String entityType, long requestedBy, int countryId) {
        return TenantContext.runAsSystem(() -> {
            Long levelId = jdbc.queryForObject(
                    "SELECT Id FROM ALERTCORE.ApprovalLevels WHERE ConfigId = ? AND LevelOrder = 1", Long.class, configId);
            Long id = jdbc.queryForObject("""
                    INSERT INTO ALERTCORE.ApprovalRequests
                           (ConfigId, RequestType, EntityType, EntityId, RequestData, CurrentLevel, Status,
                            RequestedBy, RequestedAt, CountryId)
                    VALUES (?, 'UPDATE', ?, 'sod-test', '{"sodTest":true}', 1, 'PENDING', ?, SYSUTCDATETIME(), ?);
                    SELECT CAST(SCOPE_IDENTITY() AS BIGINT);
                    """, Long.class, configId, entityType, requestedBy, countryId);
            jdbc.update("""
                    INSERT INTO ALERTCORE.ApprovalSteps (RequestId, LevelOrder, LevelId, Status)
                    VALUES (?, 1, ?, 'PENDING')
                    """, id, levelId);
            return id;
        });
    }

    private void remove(long id) {
        TenantContext.runAsSystem(() -> {
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE ResourceType = 'APPROVAL_REQUEST' AND ResourceId = ?", id);
            jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId = ?", id);
            jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId = ?", id);
            jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE Id = ?", id);
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND RECORD_ID = ?",
                    String.valueOf(id));
            return null;
        });
    }

    private String statusOf(long id) {
        return TenantContext.runAsSystem(() -> jdbc.queryForObject(
                "SELECT Status FROM ALERTCORE.ApprovalRequests WHERE Id = ?", String.class, id));
    }

    @Test
    @DisplayName("O-01: the officer who raised a request cannot approve it")
    void requesterCannotApprove() {
        long id = raise(1, "STUDENT_DATA", AsUser.JO_ADMIN, JORDAN);
        try {
            assertForbidden("jo_admin approving its own request",
                    () -> AsUser.call(AsUser.JO_ADMIN, () -> workflowService.approveStep(id, null, "mine")));
            assertThat(statusOf(id)).as("nothing moved").isEqualTo("PENDING");
        } finally {
            remove(id);
        }
    }

    @Test
    @DisplayName("O-02: a country admin without the level's role is refused; it used to be waved through")
    void adminNoLongerBypassesRequiredRole() {
        // A level-1 role the all-country admin does not hold — judged by the same
        // service the workflow consults, so group-granted roles count. If it holds
        // every level's role the bypass cannot be observed and the test steps aside.
        List<Long> held = AsUser.call(AsUser.ADMIN, () -> userService.getUserRoles(AsUser.ADMIN)).stream()
                .map(com.dilaw.mohe.ums.model.UmsRole::getId).toList();
        List<Map<String, Object>> candidates = jdbc.queryForList("""
                SELECT l.ConfigId, c.EntityType, l.RequiredRoleId
                FROM ALERTCORE.ApprovalLevels l JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
                WHERE l.LevelOrder = 1 AND l.IsActive = 1 AND l.RequiredRoleId IS NOT NULL
                  AND c.EntityType <> 'STUDENT_FINANCE_WAFAR'
                """).stream()
                .filter(row -> !held.contains(((Number) row.get("RequiredRoleId")).longValue()))
                .toList();
        assumeTrue(!candidates.isEmpty(), "admin holds every level-1 role; the bypass cannot be observed");
        long configId = ((Number) candidates.get(0).get("ConfigId")).longValue();
        String entityType = (String) candidates.get(0).get("EntityType");

        long id = raise(configId, entityType, AsUser.SUPERADMIN, EGYPT);
        try {
            assertForbidden("admin approving a level whose role it lacks",
                    () -> AsUser.call(AsUser.ADMIN, () -> workflowService.approveStep(id, null, "bypass?")));
            assertThat(statusOf(id)).isEqualTo("PENDING");
        } finally {
            remove(id);
        }
    }

    @Test
    @DisplayName("N-04: a ministry account cannot approve a Wafar finance change")
    void ministryCannotApproveWafarRequest() {
        long id = raise(9, "STUDENT_FINANCE_WAFAR", AsUser.JO_ADMIN, EGYPT);
        try {
            assertForbidden("all-country admin approving a Wafar request",
                    () -> AsUser.call(AsUser.ADMIN, () -> workflowService.approveStep(id, null, "ministry")));
            assertThat(statusOf(id)).isEqualTo("PENDING");
        } finally {
            remove(id);
        }
    }
}
