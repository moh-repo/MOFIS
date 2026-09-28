package com.dilaw.mohe.service;

import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.security.policy.MakerCheckerPolicy;
import com.dilaw.mohe.security.policy.TestAccessControl;
import org.springframework.test.util.ReflectionTestUtils;
import com.dilaw.mohe.repository.PaymentAuthRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What {@code bulkAuthorize} does when one row goes wrong (plan item 1.2).
 *
 * <p>Best effort per row: an ordinary failure is reported under {@code failed}
 * and the loop continues — including a failure whose message is null, which
 * used to make {@code Map.of} throw and turn the whole batch into a 500. A
 * scope refusal or an access denial is not a row-level outcome and propagates.
 */
@ExtendWith(MockitoExtension.class)
class PaymentAuthBulkAuthorizeRowFailureTest {

    @Mock private PaymentAuthRepository repository;
    @Mock private StudentFinanceRepository financeRepository;
    @Mock private StudentFinanceWafarRepository wafarRepository;
    @Mock private StudyUniversityFeeRepository universityFeeRepository;
    @Mock private StudentYearResultService yearResultService;
    @Mock private StudentAccessGuard studentAccessGuard;
    @Mock private CountryScopeService countryScopeService;
    @InjectMocks private PaymentAuthService service;

    @BeforeEach
    void signIn() {
        AsUser.signIn(AsUser.ADMIN);
        // Maker is not checker (live QA M2): the real policy, as the application runs it.
        ReflectionTestUtils.setField(service, "accessControl", TestAccessControl.of(new MakerCheckerPolicy()));
    }

    @AfterEach
    void signOut() {
        AsUser.signOut();
    }

    private static PaymentAuth draft(int id) {
        PaymentAuth p = new PaymentAuth();
        p.setId(id);
        p.setAuthStatus("DRAFT");
        return p;
    }

    private static PaymentAuth draftBy(int id, int createdBy) {
        PaymentAuth p = draft(id);
        p.setCreatedBy(createdBy);
        return p;
    }

    @Test
    @DisplayName("M2: the creator's own voucher fails on its row; a colleague's is authorized (D8-a)")
    void ownVoucherFailsItsRowOnly() {
        when(repository.findById(1)).thenReturn(Optional.of(draftBy(1, (int) AsUser.ADMIN)));
        when(repository.findById(2)).thenReturn(Optional.of(draftBy(2, 5)));

        Map<String, Object> result = service.bulkAuthorize(List.of(1, 2));

        assertThat(result.get("authorized")).isEqualTo(1);
        assertThat(result.get("failed")).asList().singleElement()
                .isEqualTo(Map.of("id", 1, "reason", "Cannot authorize own voucher"));
        verify(repository, never()).save(argThat(p -> p.getId() == 1));
    }

    @Test
    @DisplayName("M2: a voucher with no creator on file (legacy) is still authorized")
    void legacyVoucherWithoutCreator() {
        when(repository.findById(3)).thenReturn(Optional.of(draft(3)));
        assertThat(service.bulkAuthorize(List.of(3)).get("authorized")).isEqualTo(1);
    }

    @Test
    @DisplayName("a row that fails with no message is reported and the batch goes on")
    void nullMessageDoesNotAbortTheBatch() {
        when(repository.findById(1)).thenReturn(Optional.of(draft(1)));
        when(repository.findById(2)).thenThrow(new IllegalStateException()); // message == null
        when(repository.findById(3)).thenReturn(Optional.of(draft(3)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.bulkAuthorize(List.of(1, 2, 3));

        assertThat(result.get("authorized")).isEqualTo(2);
        assertThat(result.get("authorizedBy")).isEqualTo(AsUser.ADMIN);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failed = (List<Map<String, Object>>) result.get("failed");
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).get("id")).isEqualTo(2);
        assertThat(failed.get(0).get("reason")).isEqualTo("null");
    }

    @Test
    @DisplayName("a released row carries the signed-in user as AUTHORIZED_BY")
    void stampsAuthorizedBy() {
        when(repository.findById(1)).thenReturn(Optional.of(draft(1)));
        when(repository.save(any())).thenAnswer(inv -> {
            PaymentAuth saved = inv.getArgument(0);
            assertThat(saved.getAuthorizedBy()).isEqualTo(AsUser.ADMIN);
            assertThat(saved.getAuthStatus()).isEqualTo("APPROVED");
            return saved;
        });

        assertThat(service.bulkAuthorize(List.of(1)).get("authorized")).isEqualTo(1);
    }

    @Test
    @DisplayName("a scope refusal is not a row outcome: it propagates")
    void scopeRefusalPropagates() {
        when(repository.findById(1)).thenReturn(Optional.of(draft(1)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(2)).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope"));

        assertThatThrownBy(() -> service.bulkAuthorize(List.of(1, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("another attaché's payment stops the batch and nothing is released (W4-03)")
    void anotherAttachesPaymentIsRefused() {
        PaymentAuth foreign = draft(7);
        foreign.setStudentId(50);
        when(repository.findById(7)).thenReturn(Optional.of(foreign));
        when(studentAccessGuard.requireStudent(50)).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope"));

        assertThatThrownBy(() -> service.bulkAuthorize(List.of(7)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("a new authorisation is created by the signed-in user, whatever the form says (W4-03)")
    void createStampsTheSignedInUser() {
        com.dilaw.mohe.model.StudentDatum student = new com.dilaw.mohe.model.StudentDatum();
        student.setStudentId(10);
        student.setStudentName("A student");
        student.setStudyCountryIn(1);
        when(studentAccessGuard.requireStudent(10)).thenReturn(student);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentAuth body = new PaymentAuth();
        body.setStudentId(10);
        body.setAuthDate(java.time.LocalDate.of(2026, 9, 1));
        body.setOriginalAmount(new java.math.BigDecimal("100"));
        body.setCreatedBy(999);

        PaymentAuth created = service.create(body);

        assertThat(created.getCreatedBy()).isEqualTo((int) AsUser.ADMIN);
        assertThat(created.getAuthStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("a student's payments are read only for a student in scope (W4-03)")
    void studentPaymentsAreScoped() {
        com.dilaw.mohe.model.StudentDatum student = new com.dilaw.mohe.model.StudentDatum();
        student.setStudentId(50);
        student.setStudyCountryIn(1004);
        when(studentAccessGuard.requireStudent(50)).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope"));

        assertThatThrownBy(() -> service.getByStudentId(50))
                .isInstanceOf(ResponseStatusException.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findByStudentIdOrderByAuthMidateDesc(any());
    }

    @Test
    @DisplayName("an access denial propagates too")
    void accessDeniedPropagates() {
        when(repository.findById(1)).thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> service.bulkAuthorize(List.of(1)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
