package com.dilaw.mohe.scope;

import com.dilaw.mohe.dto.StudentExtensionRequest;
import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.ExtensionTypeRepository;
import com.dilaw.mohe.repository.PaymentAuthRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentExtensionRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import com.dilaw.mohe.service.PaymentAuthService;
import com.dilaw.mohe.service.StudentAccessGuard;
import com.dilaw.mohe.service.StudentExtensionLocalizationService;
import com.dilaw.mohe.service.StudentExtensionService;
import com.dilaw.mohe.service.StudyUniversityFeeService;
import com.dilaw.mohe.studentportal.StudentChangeRequestService;
import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import com.dilaw.mohe.studentportal.repository.StudentChangeRequestRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Live QA N14 (26 Sep 2026): a student the caller cannot see is refused, never taken
 * for "no country".
 *
 * <p>University fees, extensions, payment authorisations and staff change requests read
 * the student's study country with {@code findStudyCountryInByStudentId(..).orElse(null)}
 * and let a null through. A student hidden by row-level security is exactly the one that
 * comes back empty, so the check passed for the students it existed to refuse. Each
 * service now asks {@link StudentAccessGuard} (404 hidden or missing, 403 out of scope).
 *
 * <p>The repository here still answers "no country" for student {@link #HIDDEN}, as
 * row-level security does: the old code would have gone on; the new one must stop.
 */
class HiddenStudentRefusedTest {

    private static final int HIDDEN = 77;

    private final StudentAccessGuard guard = mock(StudentAccessGuard.class);
    private final StudentDataRepository students = mock(StudentDataRepository.class);
    private final CountryScopeService scope = mock(CountryScopeService.class);

    @BeforeEach
    void hideTheStudent() {
        when(students.findStudyCountryInByStudentId(HIDDEN)).thenReturn(Optional.empty());
        when(scope.canAccessCountry(any())).thenReturn(true);
        when(guard.requireStudent(HIDDEN)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private static HttpStatus statusOf(Runnable call) {
        ResponseStatusException e = catchThrowableOfType(ResponseStatusException.class, call::run);
        assertThat(e).as("refused").isNotNull();
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // ── university fees ──────────────────────────────────────────────────────

    private StudyUniversityFeeService feeService(StudyUniversityFeeRepository fees) {
        StudyUniversityFeeService s = new StudyUniversityFeeService();
        ReflectionTestUtils.setField(s, "repository", fees);
        ReflectionTestUtils.setField(s, "studentDataRepository", students);
        ReflectionTestUtils.setField(s, "countryScopeService", scope);
        ReflectionTestUtils.setField(s, "studentAccessGuard", guard);
        return s;
    }

    private static StudyUniversityFee feeOf(int studentId) {
        StudyUniversityFee f = new StudyUniversityFee();
        f.setId(5);
        f.setStudentId(studentId);
        return f;
    }

    @Test
    @DisplayName("fees: read by id, read by student, create, update onto the student and delete are all refused")
    void fees() {
        StudyUniversityFeeRepository fees = mock(StudyUniversityFeeRepository.class);
        StudyUniversityFeeService service = feeService(fees);
        when(fees.findById(5)).thenReturn(Optional.of(feeOf(HIDDEN)));

        assertThat(statusOf(() -> service.getById(5))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.getByStudentId(HIDDEN))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.create(feeOf(HIDDEN)))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.delete(5))).isEqualTo(HttpStatus.NOT_FOUND);

        verify(fees, never()).findByStudentId(anyInt());
        verify(fees, never()).save(any());
        verify(fees, never()).deleteById(any());
    }

    @Test
    @DisplayName("fees: an update may not move a fee onto a student the caller cannot reach")
    void feeCannotMoveToAHiddenStudent() {
        StudyUniversityFeeRepository fees = mock(StudyUniversityFeeRepository.class);
        StudyUniversityFeeService service = feeService(fees);
        when(fees.findById(5)).thenReturn(Optional.of(feeOf(12)));

        assertThat(statusOf(() -> service.update(5, feeOf(HIDDEN)))).isEqualTo(HttpStatus.NOT_FOUND);
        verify(guard).requireStudent(12);
        verify(fees, never()).save(any());
    }

    // ── extensions ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("extensions: read, create, update onto the student and delete are refused")
    void extensions() {
        StudentExtensionRepository extensions = mock(StudentExtensionRepository.class);
        StudentExtensionService service = new StudentExtensionService(extensions, students,
                mock(ExtensionTypeRepository.class), scope, mock(StudentExtensionLocalizationService.class), guard);
        StudentExtension stored = new StudentExtension();
        stored.setExtensionId(3);
        stored.setStudentId(HIDDEN);
        StudentExtension another = new StudentExtension();
        another.setExtensionId(4);
        another.setStudentId(12);
        when(extensions.findById(3)).thenReturn(Optional.of(stored));
        when(extensions.findById(4)).thenReturn(Optional.of(another));
        StudentExtensionRequest request = new StudentExtensionRequest();
        request.setStudentId(HIDDEN);
        request.setExtensionType("ACADEMIC");
        request.setExtensionStatus("ACTIVE");

        assertThat(statusOf(() -> service.getById(3))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.create(request))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.update(4, request))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.delete(3))).isEqualTo(HttpStatus.NOT_FOUND);

        verify(extensions, never()).save(any());
        verify(extensions, never()).delete(any(StudentExtension.class));
    }

    // ── payment authorisations ───────────────────────────────────────────────

    @Test
    @DisplayName("payment authorisations: a voucher of a hidden student, and its amounts, are refused")
    void paymentAuthorisations() {
        PaymentAuthRepository vouchers = mock(PaymentAuthRepository.class);
        StudentFinanceRepository finance = mock(StudentFinanceRepository.class);
        PaymentAuthService service = new PaymentAuthService();
        ReflectionTestUtils.setField(service, "repository", vouchers);
        ReflectionTestUtils.setField(service, "financeRepository", finance);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "studentAccessGuard", guard);
        PaymentAuth voucher = new PaymentAuth();
        voucher.setId(9);
        voucher.setStudentId(HIDDEN);
        when(vouchers.findById(9)).thenReturn(Optional.of(voucher));

        assertThat(statusOf(() -> service.getById(9))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.markPaid(9))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.calculateAmount(HIDDEN, "ALL"))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.getByStudentId(HIDDEN))).isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(finance);
        verify(vouchers, never()).save(any());
    }

    // ── staff change requests ────────────────────────────────────────────────

    @Test
    @DisplayName("change requests: a reject of a hidden student's request is refused (it had no later check)")
    void changeRequestReject() {
        StudentChangeRequestRepository requests = mock(StudentChangeRequestRepository.class);
        StudentChangeRequestService service = new StudentChangeRequestService();
        ReflectionTestUtils.setField(service, "requestRepository", requests);
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "studentAccessGuard", guard);
        StudentChangeRequest req = new StudentChangeRequest();
        req.setRequestId(21);
        req.setStudentId(HIDDEN);
        req.setStatus("PENDING");
        when(requests.findById(21)).thenReturn(Optional.of(req));

        assertThat(statusOf(() -> service.reject(21, 1L, "wrong"))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.approve(21, 1L, null))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.modifyAndApprove(21, 1L, "x", null))).isEqualTo(HttpStatus.NOT_FOUND);

        verify(requests, never()).save(any());
        assertThat(req.getStatus()).isEqualTo("PENDING");
    }
}
