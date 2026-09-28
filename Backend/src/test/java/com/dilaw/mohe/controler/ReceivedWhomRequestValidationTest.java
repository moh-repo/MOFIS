package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.repository.StudyFinReceivedWhomRepository;
import com.dilaw.mohe.service.StudyFinReceivedWhomService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Round-5 QA R5-25: a received-by request with every field empty (no student, name or
 * amount) was accepted into the approval chain. Decision D-7: the recipient's name and an
 * attaché are required when the request is raised, as the dialog already asks.
 */
class ReceivedWhomRequestValidationTest {

    private CountryScopeService scope;
    private StudyFinReceivedWhomService service;
    private ApprovalIntegrationService approvals;
    private StudyFinReceivedWhomController controller;

    @BeforeEach
    void setUp() {
        scope = Mockito.mock(CountryScopeService.class);
        Mockito.when(scope.getScope()).thenReturn(new CountryScope(1415L, false, List.of(1004, 1005)));
        service = new StudyFinReceivedWhomService();
        ReflectionTestUtils.setField(service, "repository", Mockito.mock(StudyFinReceivedWhomRepository.class));
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        approvals = Mockito.mock(ApprovalIntegrationService.class);
        controller = new StudyFinReceivedWhomController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "approvalIntegrationService", approvals);
    }

    private static void assertBadRequest(ThrowingCallable call, String mentions) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(mentions)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("the QA case: an empty request is refused with 400 and no approval request is raised")
    void emptyRequestRefused() {
        assertBadRequest(() -> controller.create(new StudyFinReceivedWhom()), "finReceivedName");
        verify(approvals, never()).createStudyFinReceivedWhomRequest(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a name with no attaché, from an account holding two, is refused; a blank name too")
    void nameAndAttacheRequired() {
        StudyFinReceivedWhom noCountry = new StudyFinReceivedWhom();
        noCountry.setFinReceivedName("Ahmed");
        noCountry.setFinanceCountryId(0);
        assertBadRequest(() -> service.validateForRequest(noCountry), "financeCountryId");

        StudyFinReceivedWhom blank = new StudyFinReceivedWhom();
        blank.setFinReceivedName("   ");
        blank.setFinanceCountryId(1004);
        assertBadRequest(() -> service.validateForRequest(blank), "finReceivedName");
    }

    @Test
    @DisplayName("a name and an attaché in scope pass, and the request is raised")
    void completeRequestRaised() {
        StudyFinReceivedWhom ok = new StudyFinReceivedWhom();
        ok.setFinReceivedName("Ahmed");
        ok.setFinanceCountryId(1004);
        assertThat(controller.create(ok).getStatusCode().is2xxSuccessful()).isTrue();
        verify(scope).validateCountryAccess(1004);
        verify(approvals).createStudyFinReceivedWhomRequest(any(), any(), any(), any(), any(), any());
    }
}
