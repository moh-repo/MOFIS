package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.service.StudyUniversityFeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Round-5 QA R5-28: a university-fee request missing how the payment is made (the payor
 * type) was accepted, and refused only when the approver approved it; it could then only
 * be rejected. The same checks now run when the request is raised.
 */
class UniversityFeeRequestValidationTest {

    private ApprovalIntegrationService approvals;
    private StudyUniversityFeeController controller;

    @BeforeEach
    void setUp() {
        approvals = Mockito.mock(ApprovalIntegrationService.class);
        controller = new StudyUniversityFeeController();
        ReflectionTestUtils.setField(controller, "service", new StudyUniversityFeeService());
        ReflectionTestUtils.setField(controller, "approvalIntegrationService", approvals);
    }

    private static StudyUniversityFee completeFee() {
        StudyUniversityFee fee = new StudyUniversityFee();
        fee.setStudentId(10119);
        fee.setStudyUniversityFeeYear("2025/2026");
        fee.setStudyUniversityFeeSemesterNumber(1);
        fee.setStudyUniversityFeeYearScopeId(1);
        fee.setStudyUniversityFeeSemesterScopeId(1);
        fee.setStudyUniversityFeeSourceId(1);
        fee.setStudyUniversityCheckPayorTypeId(1);
        fee.setStudyUniversityFeeAmount(1000);
        fee.setStudyUniversityFeeDiscount(0);
        return fee;
    }

    @Test
    @DisplayName("the QA case: no payor type is a 400 when the request is raised, and nothing is queued")
    void missingPayorRefused() {
        StudyUniversityFee fee = completeFee();
        fee.setStudyUniversityCheckPayorTypeId(null);
        assertThatThrownBy(() -> controller.create(fee, new MockHttpServletRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("How the payment is made")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(approvals, never()).createStudyUniversityFeeRequest(any(), any(), any(), any());
        assertThat(fee.getStudyUniversityCheckPayorTypeId()).as("checked on a copy: the request is not rewritten").isNull();
    }

    @Test
    @DisplayName("a discount above the fee is refused too; a complete fee is queued")
    void discountAndComplete() {
        StudyUniversityFee over = completeFee();
        over.setStudyUniversityFeeDiscount(1500);
        assertThatThrownBy(() -> controller.create(over, new MockHttpServletRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("discount");

        assertThat(controller.create(completeFee(), new MockHttpServletRequest()).getStatusCode().is2xxSuccessful()).isTrue();
        verify(approvals).createStudyUniversityFeeRequest(any(), any(), any(), any());
    }
}
