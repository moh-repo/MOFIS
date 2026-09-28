package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.controler.ApprovalController;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.repository.ApprovalHistoryRepository;
import com.dilaw.mohe.approval.repository.ApprovalRequestRepository;
import com.dilaw.mohe.approval.repository.ApprovalStepRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.ApprovalRequestPolicy;
import com.dilaw.mohe.security.policy.TestAccessControl;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Round-5 QA R5-16: a requester who holds no approvals permission (a Finance Officer)
 * could not withdraw their own request. The policy always let the requester reject, but
 * /reject and /my-requests sat behind the approvals permission and there was no other
 * way. Decision D-4: POST /approvals/{id}/withdraw for the requester only, recorded as a
 * rejection whose reason says it was withdrawn.
 */
class ApprovalWithdrawTest {

    private static final int JORDAN = 1004;
    private static final long REQUESTER = 1415L;   // QA-R5-jo-finance, a FINANCE_OFFICER
    private static final long REQUEST = 21823L;

    private ApprovalRequestRepository requests;
    private ApprovalRequest request;

    @BeforeEach
    void setUp() {
        requests = Mockito.mock(ApprovalRequestRepository.class);
        request = new ApprovalRequest();
        request.setId(REQUEST);
        request.setEntityType("STUDENT_FINANCE");
        request.setCountryId(JORDAN);
        request.setRequestedBy(REQUESTER);
        request.setCurrentLevel(1);
        request.setStatus(ApprovalStatus.PENDING);
        Mockito.when(requests.findById(REQUEST)).thenReturn(Optional.of(request));
        Mockito.when(requests.findByIdWithConfig(REQUEST)).thenReturn(Optional.of(request));
    }

    /** A Jordan staff account holding only FINANCE.VIEW, as the Finance Officer does. */
    private static AccessSubject financeOfficer(long id) {
        return new AccessSubject(id, AdminTier.COUNTRY_STAFF, false, List.of(JORDAN),
                Set.of("FINANCE_OFFICER"), Set.of("FINANCE.VIEW"), null);
    }

    private ApprovalWorkflowService serviceAs(AccessSubject subject) {
        ApprovalStepRepository steps = Mockito.mock(ApprovalStepRepository.class);
        Mockito.when(steps.findByRequestIdAndLevelOrder(anyLong(), anyInt())).thenReturn(Optional.empty());
        ApprovalWorkflowService service = new ApprovalWorkflowService();
        ReflectionTestUtils.setField(service, "requestRepository", requests);
        ReflectionTestUtils.setField(service, "stepRepository", steps);
        ReflectionTestUtils.setField(service, "historyRepository", Mockito.mock(ApprovalHistoryRepository.class));
        ReflectionTestUtils.setField(service, "eventPublisher", Mockito.mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "accessControl", TestAccessControl.as(subject,
                new ApprovalRequestPolicy(Mockito.mock(UmsRoleRepository.class), new WafarPolicy(true))));
        return service;
    }

    @Test
    @DisplayName("the requester withdraws with no approvals permission; it is recorded as withdrawn")
    void requesterWithdraws() {
        ApprovalRequest after = AsUser.call(REQUESTER,
                () -> serviceAs(financeOfficer(REQUESTER)).withdrawRequest(REQUEST, " wrong amount "));
        assertThat(after.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(after.getRejectedBy()).isEqualTo(REQUESTER);
        assertThat(after.getRejectionReason()).isEqualTo("Withdrawn by the requester: wrong amount");
    }

    @Test
    @DisplayName("without a reason the record still says it was withdrawn")
    void noReason() {
        ApprovalRequest after = AsUser.call(REQUESTER,
                () -> serviceAs(financeOfficer(REQUESTER)).withdrawRequest(REQUEST, null));
        assertThat(after.getRejectionReason()).isEqualTo("Withdrawn by the requester");
    }

    @Test
    @DisplayName("anyone else gets 404, as for a request out of scope, and the request is left as it was")
    void othersRefused() {
        assertThatThrownBy(() -> AsUser.call(REQUESTER + 1,
                () -> serviceAs(financeOfficer(REQUESTER + 1)).withdrawRequest(REQUEST, "x")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404);
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        Mockito.verify(requests, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("the change view: an approvals reader sees any request in scope, anyone else only their own")
    void changeViewReaders() {
        CountryScopeService scope = Mockito.mock(CountryScopeService.class);
        Mockito.when(scope.canAccessCountry(JORDAN)).thenReturn(true);
        java.util.function.Function<AccessSubject, Optional<ApprovalRequest>> viewAs = subject -> {
            ApprovalWorkflowService service = serviceAs(subject);
            ReflectionTestUtils.setField(service, "countryScopeService", scope);
            return service.getRequestForViewer(REQUEST);
        };
        assertThat(viewAs.apply(financeOfficer(REQUESTER))).as("the requester").isPresent();
        assertThat(viewAs.apply(financeOfficer(REQUESTER + 1))).as("another finance officer").isEmpty();
        assertThat(viewAs.apply(new AccessSubject(REQUESTER + 2, AdminTier.COUNTRY_STAFF, false, List.of(JORDAN),
                Set.of(), Set.of("APPROVALS.VIEW"), null))).as("an approvals reader").isPresent();
    }

    @Test
    @DisplayName("the viewer rule's codes are the APPROVALS gate's codes")
    void readCodesMatchTheGate() {
        List<String> gateCodes = java.util.regex.Pattern.compile("hasAuthority\\('([^']+)'\\)")
                .matcher(com.dilaw.mohe.config.MoheSecurityExpressions.APPROVALS).results()
                .map(m -> m.group(1)).toList();
        assertThat(ApprovalWorkflowService.APPROVALS_READ_CODES).containsExactlyInAnyOrderElementsOf(gateCodes);
    }

    @Test
    @DisplayName("a request already closed cannot be withdrawn; an unknown one is 404")
    void closedOrUnknown() {
        request.setStatus(ApprovalStatus.EXECUTED);
        assertThatThrownBy(() -> AsUser.call(REQUESTER,
                () -> serviceAs(financeOfficer(REQUESTER)).withdrawRequest(REQUEST, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AsUser.call(REQUESTER,
                () -> serviceAs(financeOfficer(REQUESTER)).withdrawRequest(999999L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("gates: FINANCE.VIEW alone reaches withdraw and my-requests, not reject")
    void gates() {
        Set<String> financeOnly = Set.of("FINANCE.VIEW");
        assertThat(passes(ApprovalController.class, "withdrawRequest", financeOnly)).isTrue();
        assertThat(passes(ApprovalController.class, "getMyRequests", financeOnly)).isTrue();
        assertThat(passes(ApprovalController.class, "getRequestChanges", financeOnly)).isTrue();
        assertThat(passes(ApprovalController.class, "getStagedFile", financeOnly)).isTrue();
        assertThat(passes(ApprovalController.class, "rejectRequest", financeOnly)).isFalse();
        assertThat(passes(ApprovalController.class, "getRequestById", financeOnly)).isFalse();
        assertThat(passes(ApprovalController.class, "rejectRequest", Set.of("APPROVALS.VIEW"))).isTrue();
    }

    /** Whether a signed-in caller holding {@code held} passes the endpoint's effective gate. */
    private static boolean passes(Class<?> controller, String method, Set<String> held) {
        Method m = Arrays.stream(controller.getDeclaredMethods())
                .filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
        PreAuthorize gate = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
        if (gate == null) gate = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        assertThat(gate).as("%s has a gate", method).isNotNull();
        StandardEvaluationContext context = new StandardEvaluationContext(new Caller(held));
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(gate.value()).getValue(context, Boolean.class));
    }

    /** The methods of Spring Security's expression root these gates use. */
    public static final class Caller {
        private final Set<String> held;

        Caller(Set<String> held) {
            this.held = held;
        }

        public boolean hasAuthority(String code) {
            return held.contains(code);
        }

        public boolean hasAnyAuthority(String... codes) {
            return Arrays.stream(codes).anyMatch(held::contains);
        }

        public boolean isAuthenticated() {
            return true;
        }
    }
}
