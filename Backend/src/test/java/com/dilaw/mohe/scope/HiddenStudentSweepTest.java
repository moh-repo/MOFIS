package com.dilaw.mohe.scope;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentLoanRepository;
import com.dilaw.mohe.service.StudentAccessGuard;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentLoanService;
import com.dilaw.mohe.service.StudentQuickEditService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import com.dilaw.mohe.wafar.repository.WafarStudentFeePaymentRepository;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Live QA N14, the same pattern elsewhere (26 Sep 2026): the places the sweep found that
 * took a student the caller cannot see for "no country".
 *
 * <p>The worst was the approval request: {@code resolveStudyCountryIn} caught the 404 of
 * a hidden student and returned null, and the request was then filed under the
 * requester's own country, where it was approved. The others read a hidden student as
 * empty and went on: a loan or allowance by id, a Wafar spend (filed under the caller's
 * attaché), a student's Wafar fee payments, Quick Edit for a student with no country.
 */
class HiddenStudentSweepTest {

    private static final int HIDDEN = 77;

    private final StudentAccessGuard guard = mock(StudentAccessGuard.class);
    private final StudentDataRepository students = mock(StudentDataRepository.class);
    private final CountryScopeService scope = mock(CountryScopeService.class);

    @BeforeEach
    void hideTheStudent() {
        when(students.findById(HIDDEN)).thenReturn(Optional.empty());
        when(students.findStudyCountryInByStudentId(HIDDEN)).thenReturn(Optional.empty());
        when(scope.canAccessCountry(any())).thenReturn(true);
        when(guard.requireStudent(HIDDEN)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private static HttpStatus statusOf(Runnable call) {
        ResponseStatusException e = catchThrowableOfType(ResponseStatusException.class, call::run);
        assertThat(e).as("refused").isNotNull();
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    @Test
    @DisplayName("an approval request for a hidden student is refused, not filed under the requester's country")
    void approvalRequestIsNotFiledAtHome() {
        StudentDataService studentData = mock(StudentDataService.class);
        when(studentData.getStudentData(HIDDEN)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        ApprovalWorkflowService workflow = mock(ApprovalWorkflowService.class);
        ApprovalIntegrationService integration = new ApprovalIntegrationService();
        ReflectionTestUtils.setField(integration, "studentDataService", studentData);
        ReflectionTestUtils.setField(integration, "workflowService", workflow);
        StudyUniversityFee fee = new StudyUniversityFee();
        fee.setStudentId(HIDDEN);

        assertThat(statusOf(() -> integration.createStudyUniversityFeeRequest(RequestType.INSERT, fee, null, 5L)))
                .isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(workflow);
    }

    @Test
    @DisplayName("a loan read by id for a hidden student is refused")
    void loanById() {
        StudentLoanRepository loans = mock(StudentLoanRepository.class);
        StudentLoan loan = new StudentLoan();
        loan.setStudentId(HIDDEN);
        when(loans.findById(4)).thenReturn(Optional.of(loan));
        StudentLoanService service = new StudentLoanService();
        ReflectionTestUtils.setField(service, "repository", loans);
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "studentAccessGuard", guard);

        assertThat(statusOf(() -> service.getById(4))).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a Wafar spend for a hidden student is refused, not filed under the caller's attaché")
    void wafarSpend() {
        WafarSpendingService service = new WafarSpendingService();
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "studentAccessGuard", guard);
        WafarSpendingCreateDto dto = new WafarSpendingCreateDto();
        dto.setStudentId(HIDDEN);
        dto.setBeneficiaryType("STUDENT");
        dto.setStudyCountryInId(1004);

        assertThat(statusOf(() -> ReflectionTestUtils.invokeMethod(service, "resolveCountry", dto)))
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(guard).requireStudent(HIDDEN);
    }

    @Test
    @DisplayName("a hidden student's Wafar fee payments are refused, not read")
    void wafarFeePayments() {
        WafarStudentFeePaymentRepository payments = mock(WafarStudentFeePaymentRepository.class);
        WafarSpendingService service = new WafarSpendingService();
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "feePaymentRepository", payments);
        ReflectionTestUtils.setField(service, "studentAccessGuard", guard);

        assertThat(statusOf(() -> service.getStudentFeePayments(HIDDEN))).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(payments);
    }

    @Test
    @DisplayName("Quick Edit asks the guard, so a student with no study country is refused to a country account")
    void quickEdit() {
        StudentQuickEditService service = new StudentQuickEditService();
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "studentAccessGuard", guard);
        when(guard.requireStudent(12)).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: student has no country on record"));

        assertThat(statusOf(() -> service.getById(12))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(() -> service.getById(HIDDEN))).isEqualTo(HttpStatus.NOT_FOUND);
        verify(students, org.mockito.Mockito.never()).findById(anyInt());
    }
}
