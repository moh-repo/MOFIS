package com.dilaw.mohe.wafar;

import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.config.tenant.WafarWriteKey;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.AccessSubjectResolver;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.wafar.model.WafarSpending;
import com.dilaw.mohe.wafar.model.WafarSpendingCategory;
import com.dilaw.mohe.wafar.repository.WafarSpendingCategoryRepository;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import com.dilaw.mohe.wafar.repository.WafarStudentFeePaymentRepository;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA M1 (25 Sep 2026): Jordan held no Wafar, a spend of 10.00 was recorded and
 * approved, and the balance went to -10.00. Decision D6-a: the attaché's own balance
 * (deposits minus approved spends; pending not counted) must cover the spend when it
 * is recorded and when it is approved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WafarSpendingBalanceTest {

    private static final int JORDAN = 1004;
    private static final long MAKER = 13L;
    private static final long CHECKER = 2L;

    @Mock private StudentFinanceWafarRepository depositRepository;
    @Mock private WafarSpendingRepository spendingRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private WafarSpendingCategoryRepository categoryRepository;
    @Mock private WafarStudentFeePaymentRepository feePaymentRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private com.dilaw.mohe.ums.service.UmsAuditLogService auditLogService;
    @Mock private WafarWriteKey wafarWriteKey;
    @Mock private AccessSubjectResolver subjects;
    @InjectMocks private WafarSpendingService service;

    private WafarBalanceService balance;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "accessControl",
                new AccessControl(List.of(new WafarPolicy(true)), subjects));
        balance = new WafarBalanceService();
        ReflectionTestUtils.setField(balance, "depositRepository", depositRepository);
        ReflectionTestUtils.setField(balance, "spendingRepository", spendingRepository);
        ReflectionTestUtils.setField(balance, "countryScopeService", countryScopeService);
        ReflectionTestUtils.setField(service, "balanceService", balance);
        when(countryScopeService.canAccessCountry(JORDAN)).thenReturn(true);
    }

    /** Jordan's pool: deposits and approved spends, whoever asks. */
    private void jordanHolds(String deposits, String approved) {
        when(depositRepository.sumTotalDeposits(eq(false), eq(List.of(JORDAN)))).thenReturn(new BigDecimal(deposits));
        when(spendingRepository.sumByStatus(eq(WafarSpendingStatus.APPROVED), eq(false), eq(List.of(JORDAN))))
                .thenReturn(new BigDecimal(approved));
    }

    private static WafarSpending pendingInJordan(String amount) {
        WafarSpendingCategory rent = new WafarSpendingCategory();
        rent.setId(1);
        rent.setCategoryCode("RENT");
        WafarSpending s = new WafarSpending();
        s.setId(7);
        s.setCategory(rent);
        s.setStatus(WafarSpendingStatus.PENDING);
        s.setStudyCountryInId(JORDAN);
        s.setAmount(new BigDecimal(amount));
        s.setSpendingDate(LocalDate.now());
        s.setCreatedBy((int) MAKER);
        return s;
    }

    private static AccessSubject jordanChecker() {
        return new AccessSubject(CHECKER, AdminTier.COUNTRY_ADMIN, false, List.of(JORDAN),
                Set.of("COUNTRY_ADMIN"), Set.of("WAFAR.EDIT"), null);
    }

    private static void assertConflict(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("#54: approving 10.00 against a balance of 0 is refused and nothing is saved")
    void approveBeyondBalanceRefused() {
        jordanHolds("0", "0");
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pendingInJordan("10.00")));
        when(subjects.current()).thenReturn(jordanChecker());

        assertConflict(() -> AsUser.call(CHECKER, () -> service.approve(7)));
        verify(spendingRepository, never()).save(any());
    }

    @Test
    @DisplayName("a spend within the attaché's balance is approved")
    void approveWithinBalance() {
        jordanHolds("100", "90");
        WafarSpending s = pendingInJordan("10.00");
        when(spendingRepository.findById(7)).thenReturn(Optional.of(s));
        when(subjects.current()).thenReturn(jordanChecker());
        when(spendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(AsUser.call(CHECKER, () -> service.approve(7)).getStatus()).isEqualTo(WafarSpendingStatus.APPROVED);
    }

    @Test
    @DisplayName("the balance is the spend's attaché, not the caller's scope; pending spends are not counted")
    void balanceIsTheAttaches() {
        jordanHolds("100", "90");
        assertThat(balance.availableFor(JORDAN)).isEqualByComparingTo("10");
        verify(countryScopeService, never()).getScope();
        verify(spendingRepository, never()).sumByStatus(eq(WafarSpendingStatus.PENDING), any(Boolean.class), any());
        assertThatCode(() -> balance.requireCovers(JORDAN, new BigDecimal("10"))).doesNotThrowAnyException();
        assertConflict(() -> balance.requireCovers(JORDAN, new BigDecimal("10.01")));
    }

    @Test
    @DisplayName("a spend that names no attaché has no balance to draw on")
    void noAttacheRefused() {
        assertConflict(() -> balance.requireCovers(null, new BigDecimal("1")));
    }
}
