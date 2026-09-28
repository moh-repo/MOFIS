package com.dilaw.mohe.service;

import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.model.StudentDatum;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The life of a payment authorisation after it is created (review F6; plan W4-10):
 * DRAFT, then APPROVED by release, then PAID by {@code POST /{id}/mark-paid}.
 *
 * <p>Two rules are pinned. Only an APPROVED record can be marked paid — PAID used to
 * be one more entry in the form's free status list, so a draft nobody had released
 * could be recorded as paid out. And only a DRAFT can be edited or deleted: once
 * released it is the statutory voucher, so a later edit or delete is a 409 that
 * changes nothing. An edit of a draft keeps the stored status, releaser, creator and
 * time whatever the body says. The permission on {@code mark-paid} is held by
 * {@code PaymentAuthMarkPaidTest}, where the controller's gate is live.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentAuthDraftLockTest {

    private static final int ID = 70;
    private static final int STUDENT = 10;

    @Mock private PaymentAuthRepository repository;
    @Mock private StudentFinanceRepository financeRepository;
    @Mock private StudentFinanceWafarRepository wafarRepository;
    @Mock private StudyUniversityFeeRepository universityFeeRepository;
    @Mock private StudentYearResultService yearResultService;
    @Mock private StudentAccessGuard studentAccessGuard;
    @Mock private CountryScopeService countryScopeService;
    @InjectMocks private PaymentAuthService service;

    private static final Instant STORED_AT = Instant.parse("2026-09-01T08:00:00Z");

    @BeforeEach
    void signInAndSeedTheStudent() {
        AsUser.signIn(AsUser.ADMIN);
        StudentDatum student = new StudentDatum();
        student.setStudentId(STUDENT);
        student.setStudentName("The student on file");
        student.setStudyCountryIn(1);
        when(studentAccessGuard.requireStudent(STUDENT)).thenReturn(student);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void signOut() {
        AsUser.signOut();
    }

    private PaymentAuth stored(String status) {
        PaymentAuth p = new PaymentAuth();
        p.setId(ID);
        p.setStudentId(STUDENT);
        p.setAuthStatus(status);
        p.setAuthDate(LocalDate.of(2026, 9, 1));
        p.setOriginalAmount(new BigDecimal("100.00"));
        p.setNetAmount(new BigDecimal("100.00"));
        p.setCreatedBy(5);
        p.setAuthorizedBy("DRAFT".equals(status) ? null : 2L);
        p.setAuthMidate(STORED_AT);
        when(repository.findById(ID)).thenReturn(Optional.of(p));
        return p;
    }

    /** An edit as the form sends it, trying to set what is not the form's to set. */
    private static PaymentAuth edit() {
        PaymentAuth body = new PaymentAuth();
        body.setStudentId(STUDENT);
        body.setAuthDate(LocalDate.of(2026, 9, 2));
        body.setOriginalAmount(new BigDecimal("250.00"));
        body.setDeductions(new BigDecimal("50.00"));
        body.setAuthStatus("PAID");
        body.setAuthorizedBy(999L);
        body.setCreatedBy(999);
        body.setStudentName("typed in the form");
        return body;
    }

    private static HttpStatus statusOf(ResponseStatusException e) {
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // ── mark-paid ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an approved payment is marked paid, and the change is stamped with the time")
    void approvedBecomesPaid() {
        PaymentAuth approved = stored("APPROVED");

        PaymentAuth paid = service.markPaid(ID);

        assertThat(paid.getAuthStatus()).isEqualTo("PAID");
        assertThat(paid.getAuthMidate()).isAfter(STORED_AT);
        assertThat(paid.getAuthorizedBy()).as("who released it is kept").isEqualTo(2L);
        verify(repository).save(approved);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DRAFT", "PAID"})
    @DisplayName("only an approved payment can be marked paid: anything else is a 409 that changes nothing")
    void onlyApprovedCanBePaid(String status) {
        PaymentAuth record = stored(status);

        ResponseStatusException refused = catchThrowableOfType(ResponseStatusException.class, () -> service.markPaid(ID));

        assertThat(refused).as("refused").isNotNull();
        assertThat(statusOf(refused)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getReason()).contains(status);
        assertThat(record.getAuthStatus()).isEqualTo(status);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("another attaché's payment cannot be marked paid: 403 before the status is looked at")
    void markPaidIsScoped() {
        stored("APPROVED");
        when(studentAccessGuard.requireStudent(STUDENT)).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope"));

        ResponseStatusException refused = catchThrowableOfType(ResponseStatusException.class, () -> service.markPaid(ID));

        assertThat(refused).as("refused").isNotNull();
        assertThat(statusOf(refused)).isEqualTo(HttpStatus.FORBIDDEN);
        verify(repository, never()).save(any());
    }

    // ── the DRAFT lock ───────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"APPROVED", "PAID"})
    @DisplayName("a released or paid voucher is not edited: 409, and the stored row is unchanged")
    void releasedIsNotEdited(String status) {
        PaymentAuth record = stored(status);

        ResponseStatusException refused = catchThrowableOfType(ResponseStatusException.class,
                () -> service.update(ID, edit()));

        assertThat(refused).as("refused").isNotNull();
        assertThat(statusOf(refused)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getReason()).contains("edited").contains(status);
        assertThat(record.getOriginalAmount()).isEqualByComparingTo("100.00");
        verify(repository, never()).save(any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"APPROVED", "PAID"})
    @DisplayName("a released or paid voucher is not deleted: 409, and nothing is removed")
    void releasedIsNotDeleted(String status) {
        stored(status);

        ResponseStatusException refused = catchThrowableOfType(ResponseStatusException.class, () -> service.delete(ID));

        assertThat(refused).as("refused").isNotNull();
        assertThat(statusOf(refused)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getReason()).contains("deleted").contains(status);
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("a draft is edited, but its status, releaser, creator and time stay as stored")
    void draftIsEditedWithinLimits() {
        stored("DRAFT");

        PaymentAuth saved = service.update(ID, edit());

        assertThat(saved.getId()).isEqualTo(ID);
        assertThat(saved.getOriginalAmount()).isEqualByComparingTo("250.00");
        assertThat(saved.getNetAmount()).as("computed, not taken from the form").isEqualByComparingTo("200.00");
        assertThat(saved.getAuthStatus()).as("the body asked for PAID").isEqualTo("DRAFT");
        assertThat(saved.getAuthorizedBy()).isNull();
        assertThat(saved.getCreatedBy()).isEqualTo(5);
        assertThat(saved.getAuthMidate()).isEqualTo(STORED_AT);
        assertThat(saved.getStudentName()).as("the voucher prints the name on file").isEqualTo("The student on file");
    }

    @Test
    @DisplayName("a draft is deleted")
    void draftIsDeleted() {
        stored("DRAFT");

        service.delete(ID);

        verify(repository).deleteById(ID);
    }

    @Test
    @DisplayName("the lock reads the status case-insensitively, as the release does")
    void lowerCaseDraftIsADraft() {
        stored("draft");

        service.delete(ID);

        verify(repository).deleteById(ID);
    }
}
