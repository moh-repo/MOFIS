package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.model.ApprovalConfig;
import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.model.ApprovalStep;
import com.dilaw.mohe.approval.repository.ApprovalConfigRepository;
import com.dilaw.mohe.approval.repository.ApprovalHistoryRepository;
import com.dilaw.mohe.approval.repository.ApprovalLevelRepository;
import com.dilaw.mohe.approval.repository.ApprovalRequestRepository;
import com.dilaw.mohe.approval.repository.ApprovalStepRepository;
import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.ApprovalDecisionContext;
import com.dilaw.mohe.security.policy.ApprovalRequestPolicy;
import com.dilaw.mohe.security.policy.Decision;
import com.dilaw.mohe.security.policy.TestAccessControl;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIN-06, FIN-11 and FIN-17 on the approval workflow.
 *
 * <p>FIN-06: the final level was the live config's {@code totalLevels}, which the config
 * update took from the request body, so setting it to 1 made every in-flight three-level
 * request final after one signature (a four-eyes bypass); and "next" was
 * {@code currentLevel + 1}, so a level inactive when the request was raised left it
 * stuck on "Current step not found". The chain is now the request's own steps.
 *
 * <p>FIN-11: withdrawing a finance link after its recipient is deleted went through
 * {@code rejectRequest}, whose refusal marked the shared transaction rollback-only and
 * lost the approved delete. {@code rejectObsolete} cannot refuse.
 *
 * <p>FIN-17: the super admin approved their own money request.
 */
class ApprovalChainIntegrityTest {

    private static final int JORDAN = 1004;
    private static final long REQUESTER = 1402L;
    private static final long APPROVER = 1502L;

    private ApprovalRequestRepository requests;
    private ApprovalStepRepository steps;
    private ApprovalHistoryRepository history;
    private ApplicationEventPublisher events;
    private ApprovalRequestPolicy policy;

    @BeforeEach
    void setUp() {
        requests = Mockito.mock(ApprovalRequestRepository.class);
        steps = Mockito.mock(ApprovalStepRepository.class);
        history = Mockito.mock(ApprovalHistoryRepository.class);
        events = Mockito.mock(ApplicationEventPublisher.class);
        policy = new ApprovalRequestPolicy(Mockito.mock(UmsRoleRepository.class), new WafarPolicy(true));
        when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ApprovalWorkflowService service(AccessSubject subject) {
        ApprovalWorkflowService service = new ApprovalWorkflowService();
        ReflectionTestUtils.setField(service, "requestRepository", requests);
        ReflectionTestUtils.setField(service, "stepRepository", steps);
        ReflectionTestUtils.setField(service, "historyRepository", history);
        ReflectionTestUtils.setField(service, "eventPublisher", events);
        ReflectionTestUtils.setField(service, "accessControl", TestAccessControl.as(subject, policy));
        return service;
    }

    private static AccessSubject countryAdmin() {
        return new AccessSubject(APPROVER, AdminTier.COUNTRY_ADMIN, false, List.of(JORDAN),
                Set.of(), Set.of("APPROVALS.VIEW", "APP_PENDING.APPROVE"), null);
    }

    private static AccessSubject superAdmin(long id) {
        return new AccessSubject(id, AdminTier.SUPER_ADMIN, true, List.of(), Set.of(), Set.of(), null);
    }

    /** A request on {@code current}, with one pending step per level in {@code levels}; the config says {@code configTotal}. */
    private ApprovalRequest inFlight(int current, int configTotal, Integer... levels) {
        ApprovalConfig config = new ApprovalConfig();
        config.setId(5L);
        config.setTotalLevels(configTotal);
        ApprovalRequest r = new ApprovalRequest();
        r.setId(77L);
        r.setConfig(config);
        r.setEntityType("STUDENT_DATA");
        r.setCountryId(JORDAN);
        r.setRequestedBy(REQUESTER);
        r.setCurrentLevel(current);
        r.setStatus(current == levels[0] ? ApprovalStatus.PENDING : ApprovalStatus.IN_PROGRESS);
        List<ApprovalStep> chain = new ArrayList<>();
        for (Integer order : levels) {
            ApprovalStep s = new ApprovalStep();
            s.setRequest(r);
            s.setLevelOrder(order);
            s.setLevel(new ApprovalLevel());
            s.setStatus(order < current ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
            s.setApprovedBy(order < current ? 1000L + order : null);
            chain.add(s);
            when(steps.findByRequestIdAndLevelOrder(77L, order)).thenReturn(Optional.of(s));
        }
        when(steps.findByRequestIdOrderByLevelOrderAsc(77L)).thenReturn(chain);
        when(requests.findById(77L)).thenReturn(Optional.of(r));
        when(requests.findByIdWithConfig(77L)).thenReturn(Optional.of(r));
        return r;
    }

    // ── FIN-06 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FIN-06: config cut to one level; an in-flight three-level request still needs three signatures")
    void configChangeDoesNotShortenTheChain() {
        ApprovalRequest r = inFlight(1, 1, 1, 2, 3);

        AsUser.call(APPROVER, () -> service(countryAdmin()).approveStep(77L, null, "level 1"));

        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(r.getCurrentLevel()).isEqualTo(2);
        verify(events, never()).publishEvent(any(ApprovalExecutionEvent.class));
    }

    @Test
    @DisplayName("FIN-06: the request's own last step is final, whatever the config says")
    void lastStepIsFinal() {
        ApprovalRequest r = inFlight(3, 5, 1, 2, 3);

        AsUser.call(APPROVER, () -> service(countryAdmin()).approveStep(77L, null, "level 3"));

        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.EXECUTED);
        verify(events).publishEvent(any(ApprovalExecutionEvent.class));
    }

    @Test
    @DisplayName("FIN-06: a level with no step (inactive when raised) is skipped, not waited for")
    void gapInTheChainIsSkipped() {
        ApprovalRequest r = inFlight(1, 3, 1, 3);

        AsUser.call(APPROVER, () -> service(countryAdmin()).approveStep(77L, null, "level 1"));

        assertThat(r.getCurrentLevel()).isEqualTo(3);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("FIN-06: next and first level come from the steps and levels, not from counting")
    void nextAndFirstLevel() {
        ApprovalStep one = new ApprovalStep();
        one.setLevelOrder(1);
        ApprovalStep three = new ApprovalStep();
        three.setLevelOrder(3);
        assertThat(ApprovalWorkflowService.nextLevelAfter(List.of(one, three), 1)).isEqualTo(3);
        assertThat(ApprovalWorkflowService.nextLevelAfter(List.of(one, three), 3)).isNull();

        ApprovalLevel l2 = new ApprovalLevel();
        l2.setLevelOrder(2);
        ApprovalLevel l4 = new ApprovalLevel();
        l4.setLevelOrder(4);
        assertThat(ApprovalWorkflowService.firstLevelOf(List.of(l4, l2)))
                .as("level 1 inactive: the request waits at level 2, where its first step is").isEqualTo(2);
    }

    @Test
    @DisplayName("FIN-06: updateConfig derives totalLevels from the active levels; the body's figure is ignored")
    void updateConfigIgnoresTotalLevels() {
        ApprovalConfigRepository configs = Mockito.mock(ApprovalConfigRepository.class);
        ApprovalLevelRepository levels = Mockito.mock(ApprovalLevelRepository.class);
        ApprovalConfigService service = new ApprovalConfigService();
        ReflectionTestUtils.setField(service, "configRepository", configs);
        ReflectionTestUtils.setField(service, "levelRepository", levels);
        ApprovalConfig stored = new ApprovalConfig();
        stored.setId(5L);
        stored.setTotalLevels(3);
        when(configs.findById(5L)).thenReturn(Optional.of(stored));
        when(configs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(levels.findByConfigIdAndIsActiveTrueOrderByLevelOrderAsc(5L))
                .thenReturn(List.of(new ApprovalLevel(), new ApprovalLevel(), new ApprovalLevel()));

        ApprovalConfig body = new ApprovalConfig();
        body.setTotalLevels(1);
        body.setIsActive(true);
        body.setRequireAllLevels(true);

        assertThat(service.updateConfig(5L, body).getTotalLevels()).isEqualTo(3);
    }

    // ── FIN-11 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FIN-11: an obsolete request is rejected without a policy check that could refuse")
    void rejectObsoleteCannotRefuse() {
        ApprovalRequest r = inFlight(1, 2, 1, 2);
        // An approver with no right to reject this request: rejectRequest would refuse.
        AccessSubject noRights = new AccessSubject(3000L, AdminTier.COUNTRY_STAFF, false, List.of(),
                Set.of(), Set.of(), null);

        boolean rejected = service(noRights).rejectObsolete(77L, 3000L, "Recipient 49 was deleted");

        assertThat(rejected).isTrue();
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(r.getRejectedBy()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("FIN-11: a request that is no longer open is left alone, quietly")
    void rejectObsoleteLeavesClosedRequests() {
        ApprovalRequest r = inFlight(1, 2, 1, 2);
        r.setStatus(ApprovalStatus.EXECUTED);

        assertThat(service(countryAdmin()).rejectObsolete(77L, APPROVER, "gone")).isFalse();
        assertThat(service(countryAdmin()).rejectObsolete(78L, APPROVER, "missing")).isFalse();
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.EXECUTED);
        verify(requests, never()).save(any());
    }

    // ── FIN-17 ───────────────────────────────────────────────────────────────

    private Decision superAdminSigningOwn(String entityType) {
        ApprovalRequest r = new ApprovalRequest();
        r.setEntityType(entityType);
        r.setRequestedBy(1L);
        r.setCurrentLevel(1);
        return policy.decide(superAdmin(1L), new ApprovalDecisionContext(r, new ApprovalLevel(), Set.of()), "APPROVE");
    }

    @Test
    @DisplayName("FIN-17: the super admin cannot approve their own money request")
    void superAdminSelfApprovalOfMoneyRefused() {
        for (String type : ApprovalRequestPolicy.MONEY_ENTITY_TYPES) {
            assertThat(superAdminSigningOwn(type).allowed()).as(type).isFalse();
        }
    }

    @Test
    @DisplayName("FIN-17: elsewhere the super admin's self-approval exemption stands, and is reported for audit")
    void superAdminSelfApprovalElsewhereUnchanged() {
        Decision d = superAdminSigningOwn("USER_PRIVILEGE_CHANGE");
        assertThat(d.allowed()).isTrue();
        assertThat(d.via(ApprovalRequestPolicy.SELF_APPROVAL_BYPASS)).isTrue();
    }

    @Test
    @DisplayName("FIN-17: another person's money request is still signed by the super admin as before")
    void superAdminSignsOthersMoneyRequests() {
        ApprovalRequest r = new ApprovalRequest();
        r.setEntityType("STUDENT_FINANCE");
        r.setRequestedBy(REQUESTER);
        r.setCurrentLevel(1);
        Decision d = policy.decide(superAdmin(1L), new ApprovalDecisionContext(r, new ApprovalLevel(), Set.of()), "APPROVE");
        assertThat(d.allowed()).isTrue();
        verify(requests, never()).findById(anyLong());
    }
}
