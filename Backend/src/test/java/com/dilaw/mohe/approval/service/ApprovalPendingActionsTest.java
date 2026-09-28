package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalLevelApprover;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.model.ApprovalStep;
import com.dilaw.mohe.approval.repository.ApprovalStepRepository;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.ApprovalRequestPolicy;
import com.dilaw.mohe.security.policy.TestAccessControl;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import org.hibernate.LazyInitializationException;
import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;

/**
 * Round-5 QA R5-01: the Pending Approvals list answered 500 to every account
 * that had something to approve, and 200 to accounts that may only look.
 *
 * <p>The list marks each row with what the caller may do (D3-a) in
 * {@code withActions}, which the controller calls after the list's read-only
 * transaction has ended. The policy reads the waiting level's named approvers,
 * a lazy collection, only for a caller who passes the level's role and position
 * checks; without a session that read throws. Here the level holds an unloaded
 * collection with no session, as the detached level did.
 */
class ApprovalPendingActionsTest {

    private static final int JORDAN = 1004;
    private static final long EMPLOYEE_ROLE = 7L;
    private static final long REQUESTER = 1402L;

    private ApprovalStepRepository steps;
    private ApprovalRequestPolicy policy;

    @BeforeEach
    void setUp() {
        UmsRoleRepository roles = Mockito.mock(UmsRoleRepository.class);
        UmsRole employee = new UmsRole();
        employee.setRoleName("Employee");
        Mockito.when(roles.findById(EMPLOYEE_ROLE)).thenReturn(Optional.of(employee));
        policy = new ApprovalRequestPolicy(roles, new WafarPolicy(true));
        steps = Mockito.mock(ApprovalStepRepository.class);
    }

    private ApprovalWorkflowService serviceAs(AccessSubject subject) {
        ApprovalWorkflowService service = new ApprovalWorkflowService();
        ReflectionTestUtils.setField(service, "stepRepository", steps);
        ReflectionTestUtils.setField(service, "accessControl", TestAccessControl.as(subject, policy));
        return service;
    }

    private static AccessSubject employee() {
        return new AccessSubject(1500L, AdminTier.COUNTRY_STAFF, false, List.of(JORDAN),
                Set.of("Employee"), Set.of("APPROVALS.VIEW"), null);
    }

    private static AccessSubject lookOnly() {
        return new AccessSubject(1501L, AdminTier.COUNTRY_STAFF, false, List.of(JORDAN),
                Set.of(), Set.of("APPROVALS.VIEW"), null);
    }

    private static AccessSubject requester() {
        return new AccessSubject(REQUESTER, AdminTier.COUNTRY_STAFF, false, List.of(JORDAN),
                Set.of(), Set.of("APPROVALS.VIEW"), null);
    }

    private static AccessSubject countryAdmin() {
        return new AccessSubject(1502L, AdminTier.COUNTRY_ADMIN, false, List.of(JORDAN),
                Set.of(), Set.of("APPROVALS.VIEW", "APP_PENDING.APPROVE", "APP_PENDING.REJECT"), null);
    }

    private static AccessSubject superAdmin() {
        return new AccessSubject(1L, AdminTier.SUPER_ADMIN, true, List.of(), Set.of(), Set.of(), null);
    }

    /** A Student-data request waiting on level 2, and its step on {@code level}. */
    private ApprovalRequest waitingOn(ApprovalLevel level) {
        ApprovalRequest r = new ApprovalRequest();
        r.setId(21819L);
        r.setEntityType("STUDENT_DATA");
        r.setCountryId(JORDAN);
        r.setRequestedBy(REQUESTER);
        r.setCurrentLevel(2);
        ApprovalStep s = new ApprovalStep();
        s.setRequest(r);
        s.setLevelOrder(2);
        s.setLevel(level);
        s.setStatus(ApprovalStatus.PENDING);
        Mockito.when(steps.findWithLevelByRequestIdIn(anyCollection())).thenReturn(List.of(s));
        return r;
    }

    private static ApprovalLevel employeeLevel(List<ApprovalLevelApprover> approvers) {
        ApprovalLevel l = new ApprovalLevel();
        l.setRequiredRoleId(EMPLOYEE_ROLE);
        l.setApprovers(approvers);
        return l;
    }

    /** A university-fee level: no role, no position, nobody named. */
    private static ApprovalLevel roleLessLevel() {
        ApprovalLevel l = new ApprovalLevel();
        l.setApprovers(new ArrayList<>());
        return l;
    }

    /** What a level fetched without its approvers holds once the session is gone. */
    private static List<ApprovalLevelApprover> unloaded() {
        return new PersistentBag<>((SharedSessionContractImplementor) null);
    }

    private ApprovalRequest flagged(AccessSubject who, ApprovalLevel level) {
        ApprovalRequest r = waitingOn(level);
        serviceAs(who).withActions(List.of(r));
        return r;
    }

    @Test
    @DisplayName("both withActions methods run in a read-only transaction of their own")
    void withActionsIsTransactional() throws NoSuchMethodException {
        for (Class<?> arg : List.of(Page.class, List.class)) {
            Transactional tx = ApprovalWorkflowService.class.getMethod("withActions", arg).getAnnotation(Transactional.class);
            assertThat(tx).as("withActions(%s)", arg.getSimpleName()).isNotNull();
            assertThat(tx.readOnly()).isTrue();
        }
    }

    @Test
    @DisplayName("an approver's row reads the level's named approvers, so it needs the session")
    void eligibleCallerReadsTheApprovers() {
        ApprovalRequest r = waitingOn(employeeLevel(unloaded()));
        assertThatThrownBy(() -> serviceAs(employee()).withActions(List.of(r)))
                .isInstanceOf(LazyInitializationException.class);
        assertThatThrownBy(() -> serviceAs(superAdmin()).withActions(List.of(r)))
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    @DisplayName("a look-only account or the requester is decided before the approvers are read")
    void othersNeverReadTheApprovers() {
        ApprovalRequest r = waitingOn(employeeLevel(unloaded()));
        serviceAs(lookOnly()).withActions(List.of(r));
        assertThat(r.getCanApprove()).isFalse();
        serviceAs(requester()).withActions(List.of(r));
        assertThat(r.getCanApprove()).isFalse();
        assertThat(r.getCanReject()).isTrue();
    }

    @Test
    @DisplayName("with the approvers loaded: the step's role approves and rejects, a look-only account does neither")
    void flagsOnARoleLevel() {
        ApprovalRequest emp = flagged(employee(), employeeLevel(new ArrayList<>()));
        assertThat(emp.getCanApprove()).isTrue();
        assertThat(emp.getCanReject()).isTrue();

        ApprovalRequest look = flagged(lookOnly(), employeeLevel(new ArrayList<>()));
        assertThat(look.getCanApprove()).isFalse();
        assertThat(look.getCanReject()).isFalse();

        ApprovalRequest own = flagged(requester(), employeeLevel(new ArrayList<>()));
        assertThat(own.getCanApprove()).as("requester ≠ approver").isFalse();
        assertThat(own.getCanReject()).as("the requester may withdraw").isTrue();

        ApprovalRequest sa = flagged(superAdmin(), employeeLevel(new ArrayList<>()));
        assertThat(sa.getCanApprove()).isTrue();
        assertThat(sa.getCanReject()).isTrue();
    }

    @Test
    @DisplayName("on a level that names nobody, the approve permission decides (H5)")
    void flagsOnARoleLessLevel() {
        ApprovalRequest admin = flagged(countryAdmin(), roleLessLevel());
        assertThat(admin.getCanApprove()).isTrue();
        assertThat(admin.getCanReject()).isTrue();

        ApprovalRequest look = flagged(lookOnly(), roleLessLevel());
        assertThat(look.getCanApprove()).isFalse();
        assertThat(look.getCanReject()).isFalse();
    }
}
