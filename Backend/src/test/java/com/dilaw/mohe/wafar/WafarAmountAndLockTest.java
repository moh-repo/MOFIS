package com.dilaw.mohe.wafar;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.config.tenant.WafarWriteKey;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.AccessSubjectResolver;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.wafar.dto.WafarCorrectionRequest;
import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import com.dilaw.mohe.wafar.model.WafarSpending;
import com.dilaw.mohe.wafar.model.WafarSpendingCategory;
import com.dilaw.mohe.wafar.repository.WafarSpendingCategoryRepository;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import com.dilaw.mohe.wafar.repository.WafarStudentFeePaymentRepository;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FIN-01 and FIN-14 on Wafar spending.
 *
 * <p>FIN-01: nothing refused a spend of zero or less — {@code requireCovers} returns
 * early for it, the DTO had no constraint and the table no CHECK — so -5000 was
 * recorded, approved, and raised the attaché's balance by 5000. The DTO, the service
 * (create, update, correction request and correction apply) and V148 all refuse it now.
 *
 * <p>FIN-14: the balance check and the approval were not serialised, so two approvals
 * that each fitted the balance could both pass. Every balance check now takes the
 * attaché's application lock inside the transaction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WafarAmountAndLockTest {

    private static final int JORDAN = 1004;
    private static final long MAKER = 13L;
    private static final long MINISTRY = 2L;

    @Mock private WafarSpendingRepository spendingRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private WafarSpendingCategoryRepository categoryRepository;
    @Mock private WafarStudentFeePaymentRepository feePaymentRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private com.dilaw.mohe.ums.service.UmsAuditLogService auditLogService;
    @Mock private WafarWriteKey wafarWriteKey;
    @Mock private AccessSubjectResolver subjects;
    @Mock private WafarBalanceService balanceService;
    @Mock private com.dilaw.mohe.approval.service.ApprovalWorkflowService approvalWorkflowService;
    @InjectMocks private WafarSpendingService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "accessControl",
                new AccessControl(List.of(new WafarPolicy(true)), subjects));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ReflectionTestUtils.setField(service, "objectMapper", mapper);
        ReflectionTestUtils.setField(service, "umsAuditLogService", auditLogService);
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static WafarSpendingCreateDto spendOf(String amount) {
        WafarSpendingCreateDto dto = new WafarSpendingCreateDto();
        dto.setCategoryId(1);
        dto.setAmount(amount == null ? null : new BigDecimal(amount));
        dto.setSpendingDate(LocalDate.now());
        dto.setBeneficiaryType("OTHER");
        dto.setStudyCountryInId(JORDAN);
        return dto;
    }

    private static WafarSpending pendingInJordan() {
        WafarSpendingCategory rent = new WafarSpendingCategory();
        rent.setId(1);
        rent.setCategoryCode("RENT");
        WafarSpending s = new WafarSpending();
        s.setId(7);
        s.setCategory(rent);
        s.setStatus("PENDING");
        s.setStudyCountryInId(JORDAN);
        s.setAmount(new BigDecimal("100"));
        s.setSpendingDate(LocalDate.now());
        s.setCreatedBy((int) MAKER);
        return s;
    }

    private static void assertBadRequest(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── FIN-01 ───────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "amount {0}")
    @ValueSource(strings = {"0", "-1", "-5000"})
    @DisplayName("FIN-01: the create/update body refuses an amount of zero or less (400 from @Valid)")
    void dtoRefusesNonPositive(String amount) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(spendOf(amount))).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
        assertThat(validator.validate(spendOf(null))).extracting(v -> v.getPropertyPath().toString())
                .as("an amount is required").containsExactly("amount");
        assertThat(validator.validate(spendOf("0.01"))).isEmpty();

        WafarCorrectionRequest correction = new WafarCorrectionRequest(new BigDecimal(amount), null, null, null,
                null, null, "reason");
        assertThat(validator.validate(correction)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @ParameterizedTest(name = "amount {0}")
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("FIN-01: create refuses zero or less in the service too, before anything is saved")
    void createRefusesNonPositive(String amount) {
        assertBadRequest(() -> AsUser.call(MAKER, () -> service.create(spendOf(amount))));
        assertBadRequest(() -> AsUser.call(MAKER, () -> service.create(spendOf(null))));
        verify(spendingRepository, never()).save(any());
    }

    @Test
    @DisplayName("FIN-01: update refuses a negative amount")
    void updateRefusesNegative() {
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pendingInJordan()));
        when(countryScopeService.canAccessCountry(JORDAN)).thenReturn(true);

        assertBadRequest(() -> AsUser.call(MAKER, () -> service.update(7, spendOf("-1"))));
        verify(spendingRepository, never()).save(any());
    }

    @Test
    @DisplayName("FIN-01: a correction to a negative amount is refused when asked and when applied")
    void correctionRefusesNegative() throws Exception {
        when(countryScopeService.isMinistryLevelActor()).thenReturn(true);
        WafarCorrectionRequest negative = new WafarCorrectionRequest(new BigDecimal("-5000"), null, null, null,
                null, null, "typo");

        assertBadRequest(() -> AsUser.call(MINISTRY, () -> service.requestCorrection(7, negative)));
        verifyNoInteractions(approvalWorkflowService);

        // Stored request data is not re-validated, so the executor checks again.
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pendingInJordan()));
        when(countryScopeService.canAccessCountry(JORDAN)).thenReturn(true);
        ApprovalRequest stored = new ApprovalRequest();
        stored.setId(99L);
        stored.setEntityId("7");
        stored.setRequestedBy(MINISTRY);
        stored.setRequestData(new ObjectMapper().writeValueAsString(negative));

        assertBadRequest(() -> AsUser.call(MINISTRY, () -> service.applyCorrection(stored)));
        verify(spendingRepository, never()).save(any());
    }

    // ── FIN-14 ───────────────────────────────────────────────────────────────

    private static WafarBalanceService balanceWith(JdbcTemplate jdbc, StudentFinanceWafarRepository deposits,
                                                   WafarSpendingRepository spends) {
        WafarBalanceService balance = new WafarBalanceService();
        ReflectionTestUtils.setField(balance, "depositRepository", deposits);
        ReflectionTestUtils.setField(balance, "spendingRepository", spends);
        ReflectionTestUtils.setField(balance, "jdbcTemplate", jdbc);
        return balance;
    }

    /** Runs the service's CallableStatementCallback against a statement answering {@code result}. */
    @SuppressWarnings("unchecked")
    private static void applockAnswers(JdbcTemplate jdbc, int result) {
        when(jdbc.execute(anyString(), any(CallableStatementCallback.class))).thenAnswer(inv -> {
            CallableStatement cs = mock(CallableStatement.class);
            when(cs.getInt(1)).thenReturn(result);
            return ((CallableStatementCallback<Integer>) inv.getArgument(1)).doInCallableStatement(cs);
        });
    }

    @Test
    @DisplayName("FIN-14: inside a transaction the balance check first takes the attaché's exclusive lock")
    @SuppressWarnings("unchecked")
    void balanceCheckTakesTheAttacheLock() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StudentFinanceWafarRepository deposits = mock(StudentFinanceWafarRepository.class);
        when(deposits.sumTotalDeposits(eq(false), eq(List.of(JORDAN)))).thenReturn(new BigDecimal("100"));
        when(spendingRepository.sumByStatus(eq("APPROVED"), eq(false), eq(List.of(JORDAN)))).thenReturn(BigDecimal.ZERO);
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getInt(1)).thenReturn(1); // granted after waiting for the other approval
        when(jdbc.execute(anyString(), any(CallableStatementCallback.class)))
                .thenAnswer(inv -> ((CallableStatementCallback<Integer>) inv.getArgument(1)).doInCallableStatement(cs));
        WafarBalanceService balance = balanceWith(jdbc, deposits, spendingRepository);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        balance.requireCovers(JORDAN, new BigDecimal("60"));

        verify(jdbc).execute(eq("{? = call sp_getapplock(?, ?, ?, ?)}"), any(CallableStatementCallback.class));
        verify(cs).setString(2, "WAFAR_BALANCE:" + JORDAN);
        verify(cs).setString(3, "Exclusive");
        verify(cs).setString(4, "Transaction");
    }

    @Test
    @DisplayName("FIN-14: a lock that cannot be had in time is a 409, and the balance is not read")
    void lockTimeoutIsAConflict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StudentFinanceWafarRepository deposits = mock(StudentFinanceWafarRepository.class);
        applockAnswers(jdbc, -1);
        WafarBalanceService balance = balanceWith(jdbc, deposits, spendingRepository);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> balance.requireCovers(JORDAN, new BigDecimal("60")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(deposits);
    }

    @Test
    @DisplayName("FIN-14: outside a transaction there is nothing to hold the lock to, so none is taken")
    @SuppressWarnings("unchecked")
    void noTransactionNoLock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StudentFinanceWafarRepository deposits = mock(StudentFinanceWafarRepository.class);
        when(deposits.sumTotalDeposits(eq(false), eq(List.of(JORDAN)))).thenReturn(new BigDecimal("100"));
        when(spendingRepository.sumByStatus(eq("APPROVED"), eq(false), eq(List.of(JORDAN)))).thenReturn(BigDecimal.ZERO);
        WafarBalanceService balance = balanceWith(jdbc, deposits, spendingRepository);

        balance.requireCovers(JORDAN, new BigDecimal("60"));
        verify(jdbc, never()).execute(anyString(), any(CallableStatementCallback.class));
    }
}
