package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.model.ApprovalRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N-03.d: "pending my approval" lists only what the caller may sign now.
 *
 * <p>The inbox and the badge use the same {@code ApprovalRequestPolicy} as the
 * approve button, so a request routed to the attaché of Jordan by role,
 * position or name is in that person's inbox and in nobody else's. Same
 * fixture as {@code NamedApproverEligibilityTest}: level 1 of STUDENT_DATA
 * re-pointed at the STAFF role, one raw PENDING request in Jordan raised by
 * {@code jo_reviewer}.
 */
@Tag("integration")
@SpringBootTest
class ApprovalInboxEligibilityTest {

    private static final long CONFIG = 1L;              // STUDENT_DATA
    private static final long JO_STAFF = 14L;
    private static final long JO_REVIEWER = 15L;
    private static final long SA_STAFF = 11L;
    private static final long STAFF_ROLE = 9L;
    private static final int JORDAN = 1004;

    @Autowired private ApprovalWorkflowService workflow;
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
                    VALUES (?, 'UPDATE', 'STUDENT_DATA', 'n03d-test', '{"n03dTest":true}', 1, 'PENDING', ?, SYSUTCDATETIME(), ?);
                    SELECT CAST(SCOPE_IDENTITY() AS BIGINT);
                    """, Long.class, CONFIG, JO_REVIEWER, JORDAN);
            jdbc.update("INSERT INTO ALERTCORE.ApprovalSteps (RequestId, LevelOrder, LevelId, Status) VALUES (?, 1, ?, 'PENDING')",
                    id, levelId);
            return id;
        });
    }

    private void remove(long id) {
        TenantContext.runAsSystem(() -> {
            jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId = ?", id);
            jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE Id = ?", id);
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND RECORD_ID = ?", String.valueOf(id));
            return null;
        });
    }

    private List<Long> inboxOf(long userId) {
        return AsUser.call(userId, () -> workflow.getPendingForMe(PageRequest.of(0, 200), null, null)
                .getContent().stream().map(ApprovalRequest::getId).toList());
    }

    private List<Long> allInScopeOf(long userId) {
        return AsUser.call(userId, () -> workflow.getAllPending(PageRequest.of(0, 200), null, null)
                .getContent().stream().map(ApprovalRequest::getId).toList());
    }

    @Test
    @DisplayName("the request is in the inbox of the staff who may sign it, not the requester's, not another attaché's")
    void inboxFollowsEligibility() {
        long id = raise();
        try {
            assertThat(inboxOf(JO_STAFF)).as("jo_staff holds the role in Jordan").contains(id);
            assertThat(allInScopeOf(JO_REVIEWER)).as("the requester sees it in the all-in-scope list").contains(id);
            assertThat(inboxOf(JO_REVIEWER)).as("but not in the inbox: requester ≠ approver (O-01)").doesNotContain(id);
            assertThat(inboxOf(SA_STAFF)).as("Saudi staff: another attaché").doesNotContain(id);
            assertThat(inboxOf(AsUser.SUPERADMIN)).as("the super admin may sign anything, audited").contains(id);
            assertThat(AsUser.call(JO_STAFF, workflow::countPendingForMe)).isGreaterThanOrEqualTo(1);
        } finally {
            remove(id);
        }
    }

    /** The list, then the row flags, as {@code ApprovalController.getPending} calls them: two transactions. */
    private ApprovalRequest listedFor(long userId, long id, boolean mine) {
        return AsUser.call(userId, () -> workflow.withActions(mine
                        ? workflow.getPendingForMe(PageRequest.of(0, 200), null, null)
                        : workflow.getAllPending(PageRequest.of(0, 200), null, null))
                .getContent().stream().filter(r -> r.getId() == id).findFirst().orElseThrow());
    }

    @Test
    @DisplayName("R5-01: the pending list marks its rows after the list's transaction has ended")
    void rowFlagsOutsideTheListTransaction() {
        long id = raise();
        try {
            ApprovalRequest forStaff = listedFor(JO_STAFF, id, true);
            assertThat(forStaff.getCanApprove()).as("the step's role").isTrue();
            assertThat(forStaff.getCanReject()).isTrue();
            ApprovalRequest forRequester = listedFor(JO_REVIEWER, id, false);
            assertThat(forRequester.getCanApprove()).as("requester ≠ approver").isFalse();
            assertThat(forRequester.getCanReject()).as("the requester may withdraw").isTrue();
        } finally {
            remove(id);
        }
    }

    @Test
    @DisplayName("a required position or a named approver routes the inbox to that person")
    void positionAndNamesRoute() {
        long id = raise();
        try {
            jdbc.update("UPDATE ALERTCORE.ApprovalLevels SET RequiredPositionCode = 'ATTACHE' WHERE Id = ?", levelId);
            assertThat(inboxOf(JO_STAFF)).as("holds the role but not the position").doesNotContain(id);
            jdbc.update("UPDATE UMSCORE.Users SET PositionCode = 'ATTACHE' WHERE Id = ?", JO_STAFF);
            assertThat(inboxOf(JO_STAFF)).as("now the attaché of Jordan").contains(id);

            // Named for Saudi only: Jordan falls back to role and position, so jo_staff keeps it.
            jdbc.update("INSERT INTO ALERTCORE.ApprovalLevelApprovers (LevelId, UserId, CountryId, CreatedAt) VALUES (?, ?, ?, SYSUTCDATETIME())",
                    levelId, SA_STAFF, 1005);
            assertThat(inboxOf(JO_STAFF)).contains(id);
            // Named for Jordan: only the named person.
            jdbc.update("INSERT INTO ALERTCORE.ApprovalLevelApprovers (LevelId, UserId, CountryId, CreatedAt) VALUES (?, ?, ?, SYSUTCDATETIME())",
                    levelId, JO_REVIEWER, JORDAN);
            assertThat(inboxOf(JO_STAFF)).as("someone else is named for Jordan").doesNotContain(id);
        } finally {
            remove(id);
        }
    }
}
