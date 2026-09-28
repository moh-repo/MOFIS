package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalLevelApprover;
import com.dilaw.mohe.approval.service.ApprovalConfigService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.service.UmsUserService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security plan N-03 (6 Sep 2026): who approves a level comes from the staff
 * table. A level that requires a position routes to the holder of that
 * position in the request's country; a level that names approvers routes to
 * them; the configuration screen picks candidates from live staff and the
 * service refuses a named approver who does not hold the position.
 *
 * <p>Uses level 1 of the {@code STUDENT_DATA} configuration, temporarily
 * re-pointed at the {@code STAFF} role so {@code jo_staff} qualifies by role,
 * and restored in the finally. Requests are raw PENDING rows in Jordan raised
 * by {@code jo_reviewer}; nothing is executed (level 1 of 3).
 */
@Tag("integration")
@SpringBootTest
class NamedApproverEligibilityTest {

    private static final long CONFIG = 1L;              // STUDENT_DATA
    private static final long JO_STAFF = 14L;
    private static final long JO_REVIEWER = 15L;
    private static final long SA_STAFF = 11L;
    private static final long STAFF_ROLE = 9L;
    private static final int JORDAN = 1004;
    private static final String POSITION = "ATTACHE";

    @Autowired private ApprovalWorkflowService workflow;
    @Autowired private ApprovalConfigService configService;
    @Autowired private UmsUserService userService;
    @Autowired private JdbcTemplate jdbc;

    private long levelId;
    private Long originalRole;

    @BeforeEach
    void repointLevel() {
        levelId = jdbc.queryForObject("SELECT Id FROM ALERTCORE.ApprovalLevels WHERE ConfigId = ? AND LevelOrder = 1", Long.class, CONFIG);
        originalRole = jdbc.queryForObject("SELECT RequiredRoleId FROM ALERTCORE.ApprovalLevels WHERE Id = ?", Long.class, levelId);
        jdbc.update("UPDATE ALERTCORE.ApprovalLevels SET RequiredRoleId = ?, RequiredPositionCode = NULL WHERE Id = ?", STAFF_ROLE, levelId);
        jdbc.update("DELETE FROM ALERTCORE.ApprovalLevelApprovers WHERE LevelId = ?", levelId);
        jdbc.update("UPDATE UMSCORE.Users SET PositionCode = NULL WHERE Id IN (?, ?)", JO_STAFF, SA_STAFF);
    }

    @AfterEach
    void restoreLevel() {
        jdbc.update("DELETE FROM ALERTCORE.ApprovalLevelApprovers WHERE LevelId = ?", levelId);
        jdbc.update("UPDATE ALERTCORE.ApprovalLevels SET RequiredRoleId = ?, RequiredPositionCode = NULL WHERE Id = ?", originalRole, levelId);
        jdbc.update("UPDATE UMSCORE.Users SET PositionCode = NULL WHERE Id IN (?, ?)", JO_STAFF, SA_STAFF);
    }

    private long raise() {
        return TenantContext.runAsSystem(() -> {
            Long id = jdbc.queryForObject("""
                    INSERT INTO ALERTCORE.ApprovalRequests
                           (ConfigId, RequestType, EntityType, EntityId, RequestData, CurrentLevel, Status,
                            RequestedBy, RequestedAt, CountryId)
                    VALUES (?, 'UPDATE', 'STUDENT_DATA', 'n03-test', '{"n03Test":true}', 1, 'PENDING', ?, SYSUTCDATETIME(), ?);
                    SELECT CAST(SCOPE_IDENTITY() AS BIGINT);
                    """, Long.class, CONFIG, JO_REVIEWER, JORDAN);
            jdbc.update("INSERT INTO ALERTCORE.ApprovalSteps (RequestId, LevelOrder, LevelId, Status) VALUES (?, 1, ?, 'PENDING')",
                    id, levelId);
            return id;
        });
    }

    private void remove(long id) {
        TenantContext.runAsSystem(() -> {
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE ResourceType = 'APPROVAL_REQUEST' AND ResourceId = ?", id);
            jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId = ?", id);
            jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId = ?", id);
            jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE Id = ?", id);
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND RECORD_ID = ?", String.valueOf(id));
            return null;
        });
    }

    private static void assertForbidden(String what, ThrowingCallable call) {
        assertThatThrownBy(call).as(what)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a level that requires a position is approved by its holder in the country, nobody else")
    void positionIsRequired() {
        jdbc.update("UPDATE ALERTCORE.ApprovalLevels SET RequiredPositionCode = ? WHERE Id = ?", POSITION, levelId);
        long id = raise();
        try {
            assertForbidden("jo_staff holds the role but no position",
                    () -> AsUser.call(JO_STAFF, () -> workflow.approveStep(id, null, "ok")));

            jdbc.update("UPDATE UMSCORE.Users SET PositionCode = ? WHERE Id = ?", POSITION, JO_STAFF);
            AsUser.call(JO_STAFF, () -> workflow.approveStep(id, null, "ok"));
            assertThat(TenantContext.runAsSystem(() -> jdbc.queryForObject(
                    "SELECT CurrentLevel FROM ALERTCORE.ApprovalRequests WHERE Id = ?", Integer.class, id)))
                    .as("level 1 approved, the request moved on").isEqualTo(2);
        } finally {
            remove(id);
        }
    }

    @Test
    @DisplayName("a level that names approvers for the country routes to them alone")
    void namedApproversAreRequired() {
        jdbc.update("INSERT INTO ALERTCORE.ApprovalLevelApprovers (LevelId, UserId, CountryId) VALUES (?, ?, ?)", levelId, SA_STAFF, JORDAN);
        long id = raise();
        try {
            assertForbidden("jo_staff is not named for Jordan",
                    () -> AsUser.call(JO_STAFF, () -> workflow.approveStep(id, null, "ok")));
            jdbc.update("INSERT INTO ALERTCORE.ApprovalLevelApprovers (LevelId, UserId, CountryId) VALUES (?, ?, NULL)", levelId, JO_STAFF);
            AsUser.call(JO_STAFF, () -> workflow.approveStep(id, null, "ok"));
        } finally {
            remove(id);
        }
    }

    @Test
    @DisplayName("the configuration refuses a named approver who does not hold the position, and lists candidates from live staff")
    void configurationValidatesAgainstStaff() {
        ApprovalLevel patch = new ApprovalLevel();
        patch.setLevelName("Attaché sign-off");
        patch.setRequiredRoleId(STAFF_ROLE);
        patch.setRequiredPositionCode(POSITION);
        patch.setCanSkip(false);
        patch.setIsActive(true);
        ApprovalLevelApprover named = new ApprovalLevelApprover();
        named.setUserId(JO_STAFF);
        named.setCountryId(JORDAN);
        patch.setNamedApprovers(List.of(named));

        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN, () -> configService.updateLevel(levelId, patch)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position");

        jdbc.update("UPDATE UMSCORE.Users SET PositionCode = ? WHERE Id = ?", POSITION, JO_STAFF);
        ApprovalLevel saved = AsUser.call(AsUser.SUPERADMIN, () -> configService.updateLevel(levelId, patch));
        assertThat(saved.getNamedApprovers()).extracting(ApprovalLevelApprover::getUserName).containsExactly("jo_staff");
        assertThat(saved.getRequiredPositionCode()).isEqualTo(POSITION);

        List<UmsUser> candidates = AsUser.call(AsUser.SUPERADMIN, () -> userService.approverCandidates(POSITION, JORDAN));
        assertThat(candidates).extracting(UmsUser::getUserName).containsExactly("jo_staff");
        assertThat(AsUser.call(AsUser.SA_ADMIN, () -> userService.approverCandidates(POSITION, JORDAN)))
                .as("scoped: Saudi's admin does not see Jordan's staff").isEmpty();

        // The levels endpoint carries the named approvers, outside any session.
        List<ApprovalLevel> levels = configService.getLevelsForConfig(CONFIG);
        assertThat(levels.get(0).getNamedApprovers()).hasSize(1);
    }
}
