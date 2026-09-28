package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalLevelApprover;
import com.dilaw.mohe.approval.model.ApprovalRequest;
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
 * Live QA H5 (25 Sep 2026): a level with no required role (university fee,
 * document) was approved — and the fee deletion executed — by an account that
 * could only view approvals. Decision D1-a: such a level stays, but signing it
 * needs APP_PENDING.APPROVE. Reject, which asked for the country only, now needs
 * the requester, APP_PENDING.REJECT or the waiting level's role.
 */
class ApprovalRoleLessLevelTest {

    private static final int JORDAN = 1004;
    private static final long EMPLOYEE_ROLE = 7L;
    private static final long REQUESTER = 1403L;

    private ApprovalRequestPolicy policy;

    @BeforeEach
    void setUp() {
        UmsRoleRepository roles = Mockito.mock(UmsRoleRepository.class);
        UmsRole employee = new UmsRole();
        employee.setRoleName("Employee");
        Mockito.when(roles.findById(EMPLOYEE_ROLE)).thenReturn(Optional.of(employee));
        policy = new ApprovalRequestPolicy(roles, new WafarPolicy(true));
    }

    private static AccessSubject jordan(long userId, Set<String> roles, Set<String> codes) {
        return new AccessSubject(userId, AdminTier.COUNTRY_STAFF, false, List.of(JORDAN), roles, codes, null);
    }

    private static final AccessSubject VIEW_ONLY = jordan(1390L, Set.of("QA-TEST-R-APPROVALS-VIEW"), Set.of("APPROVALS.VIEW"));
    private static final AccessSubject APPROVER = jordan(1391L, Set.of("STAFF"), Set.of("APPROVALS.VIEW", "APP_PENDING.APPROVE"));
    private static final AccessSubject REJECTER = jordan(1392L, Set.of("STAFF"), Set.of("APPROVALS.VIEW", "APP_PENDING.REJECT"));
    private static final AccessSubject EMPLOYEE = jordan(1393L, Set.of("Employee"), Set.of("APPROVALS.VIEW"));
    private static final AccessSubject SUPER = new AccessSubject(1L, AdminTier.SUPER_ADMIN, true, List.of(), Set.of(), Set.of(), null);

    private static ApprovalRequest request(String entityType) {
        ApprovalRequest r = new ApprovalRequest();
        r.setEntityType(entityType);
        r.setCountryId(JORDAN);
        r.setRequestedBy(REQUESTER);
        return r;
    }

    private static ApprovalLevel roleLess() {
        return new ApprovalLevel();
    }

    private static ApprovalLevel employeeLevel() {
        ApprovalLevel l = new ApprovalLevel();
        l.setRequiredRoleId(EMPLOYEE_ROLE);
        return l;
    }

    private boolean may(AccessSubject who, ApprovalRequest r, ApprovalLevel level, String action) {
        return policy.decide(who, new ApprovalDecisionContext(r, level), action).allowed();
    }

    @Test
    @DisplayName("a level that names nobody: APPROVALS.VIEW alone cannot approve it (the #59 fee deletion)")
    void viewOnlyCannotApproveARoleLessLevel() {
        assertThat(may(VIEW_ONLY, request("STUDY_UNIVERSITY_FEE"), roleLess(), "APPROVE")).isFalse();
        assertThat(may(VIEW_ONLY, request("STUDENT_DOCUMENT"), roleLess(), "APPROVE")).isFalse();
    }

    @Test
    @DisplayName("a level that names nobody: APP_PENDING.APPROVE approves it; the super admin too, audited")
    void approveCodeApprovesARoleLessLevel() {
        assertThat(may(APPROVER, request("STUDY_UNIVERSITY_FEE"), roleLess(), "APPROVE")).isTrue();
        Decision superAdmin = policy.decide(SUPER, new ApprovalDecisionContext(request("STUDY_UNIVERSITY_FEE"), roleLess()), "APPROVE");
        assertThat(superAdmin.allowed()).isTrue();
        assertThat(superAdmin.via(ApprovalRequestPolicy.ROLE_BYPASS)).isTrue();
    }

    @Test
    @DisplayName("a level with a role: its role signs it without APP_PENDING.APPROVE (Employee, QA #61)")
    void theChainRoleSignsItsLevel() {
        assertThat(may(EMPLOYEE, request("STUDENT_DATA"), employeeLevel(), "APPROVE")).isTrue();
        assertThat(may(APPROVER, request("STUDENT_DATA"), employeeLevel(), "APPROVE")).isFalse();
    }

    @Test
    @DisplayName("approvers named only for another country leave the level naming nobody here")
    void namedForAnotherCountryIsNobody() {
        ApprovalLevel l = roleLess();
        ApprovalLevelApprover saudi = new ApprovalLevelApprover();
        saudi.setUserId(99L);
        saudi.setCountryId(1005);
        l.getApprovers().add(saudi);
        assertThat(ApprovalRequestPolicy.namesNobody(request("STUDENT_DOCUMENT"), l)).isTrue();
        saudi.setCountryId(JORDAN);
        assertThat(ApprovalRequestPolicy.namesNobody(request("STUDENT_DOCUMENT"), l)).isFalse();
    }

    @Test
    @DisplayName("reject: view-only is refused; the requester, APP_PENDING.REJECT and the level's role may")
    void reject() {
        ApprovalRequest r = request("STUDENT_DATA");
        assertThat(may(VIEW_ONLY, r, employeeLevel(), "REJECT")).isFalse();
        assertThat(may(jordan(REQUESTER, Set.of("STAFF"), Set.of("APPROVALS.VIEW")), r, employeeLevel(), "REJECT")).isTrue();
        assertThat(may(REJECTER, r, employeeLevel(), "REJECT")).isTrue();
        assertThat(may(EMPLOYEE, r, employeeLevel(), "REJECT")).isTrue();
        assertThat(may(VIEW_ONLY, request("STUDY_UNIVERSITY_FEE"), roleLess(), "REJECT")).isFalse();
        assertThat(may(SUPER, r, employeeLevel(), "REJECT")).isTrue();
    }

    @Test
    @DisplayName("reject still needs the country: a Saudi reject code does not reach a Jordan request")
    void rejectNeedsTheCountry() {
        AccessSubject saudi = new AccessSubject(2000L, AdminTier.COUNTRY_ADMIN, false, List.of(1005),
                Set.of("COUNTRY_ADMIN"), Set.of("APP_PENDING.REJECT"), null);
        assertThat(may(saudi, request("STUDENT_DATA"), employeeLevel(), "REJECT")).isFalse();
    }
}
