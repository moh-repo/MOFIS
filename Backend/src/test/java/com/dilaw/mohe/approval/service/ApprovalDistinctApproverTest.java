package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.model.ApprovalStep;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.ApprovalDecisionContext;
import com.dilaw.mohe.security.policy.ApprovalRequestPolicy;
import com.dilaw.mohe.security.policy.Decision;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live QA M3 (25 Sep 2026): one HR Manager approved all four levels of a finance
 * change, because every level of that chain needs the same role. Decision D2-a:
 * each level by a different person; the super admin is exempt and audited.
 */
class ApprovalDistinctApproverTest {

    private static final int JORDAN = 1004;
    private static final long HR_ROLE = 3L;
    private static final long HR_ONE = 1400L;
    private static final long HR_TWO = 1401L;

    private ApprovalRequestPolicy policy;

    @BeforeEach
    void setUp() {
        UmsRoleRepository roles = Mockito.mock(UmsRoleRepository.class);
        UmsRole hr = new UmsRole();
        hr.setRoleName("HR Manager");
        Mockito.when(roles.findById(HR_ROLE)).thenReturn(Optional.of(hr));
        policy = new ApprovalRequestPolicy(roles, new WafarPolicy(true));
    }

    private static AccessSubject hrManager(long id) {
        return new AccessSubject(id, AdminTier.COUNTRY_STAFF, false, List.of(JORDAN),
                Set.of("HR Manager"), Set.of("APPROVALS.VIEW", "APP_PENDING.APPROVE"), null);
    }

    private static ApprovalRequest financeChangeAtLevel(int level) {
        ApprovalRequest r = new ApprovalRequest();
        r.setEntityType("STUDENT_FINANCE");
        r.setCountryId(JORDAN);
        r.setRequestedBy(1402L);
        r.setCurrentLevel(level);
        return r;
    }

    private static ApprovalLevel hrLevel() {
        ApprovalLevel l = new ApprovalLevel();
        l.setRequiredRoleId(HR_ROLE);
        return l;
    }

    private Decision decide(AccessSubject who, Set<Long> prior) {
        return policy.decide(who, new ApprovalDecisionContext(financeChangeAtLevel(2), hrLevel(), prior), "APPROVE");
    }

    @Test
    @DisplayName("the HR Manager who approved level 1 cannot approve level 2")
    void sameApproverRefused() {
        Decision d = decide(hrManager(HR_ONE), Set.of(HR_ONE));
        assertThat(d.allowed()).isFalse();
    }

    @Test
    @DisplayName("a second HR Manager approves level 2")
    void secondApproverAllowed() {
        assertThat(decide(hrManager(HR_TWO), Set.of(HR_ONE)).allowed()).isTrue();
    }

    @Test
    @DisplayName("the super admin may sign again, recorded as an exemption")
    void superAdminExemptAndAudited() {
        AccessSubject superAdmin = new AccessSubject(1L, AdminTier.SUPER_ADMIN, true, List.of(), Set.of(), Set.of(), null);
        Decision d = decide(superAdmin, Set.of(1L));
        assertThat(d.allowed()).isTrue();
        assertThat(d.via(ApprovalRequestPolicy.SEPARATE_LEVELS_BYPASS)).isTrue();
    }

    private static ApprovalStep step(int level, ApprovalStatus status, Long by) {
        ApprovalStep s = new ApprovalStep();
        s.setLevelOrder(level);
        s.setStatus(status);
        s.setApprovedBy(by);
        return s;
    }

    @Test
    @DisplayName("earlier approvers: approved steps below the waiting level only")
    void priorApproversAreEarlierApprovals() {
        ApprovalRequest r = financeChangeAtLevel(3);
        List<ApprovalStep> steps = List.of(
                step(1, ApprovalStatus.APPROVED, HR_ONE),
                step(2, ApprovalStatus.APPROVED, HR_TWO),
                step(3, ApprovalStatus.PENDING, null),
                step(4, ApprovalStatus.PENDING, null));
        assertThat(ApprovalWorkflowService.priorApprovers(steps, r)).containsExactlyInAnyOrder(HR_ONE, HR_TWO);
    }

    @Test
    @DisplayName("a rejection's author is not an earlier approver")
    void rejectionIsNotAnApproval() {
        ApprovalRequest r = financeChangeAtLevel(2);
        List<ApprovalStep> steps = List.of(step(1, ApprovalStatus.REJECTED, HR_ONE), step(2, ApprovalStatus.PENDING, null));
        assertThat(ApprovalWorkflowService.priorApprovers(steps, r)).isEmpty();
    }
}
