package com.dilaw.mohe.wafar;

import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-5 QA R5-04 (the M1 gap): Jordan's approved deposit of 100 was deleted through its
 * chain while 10.00 of approved spending remained, and the balance went to −10.00. A
 * deposit deleted, lowered or moved to another attaché must leave the old attaché's
 * approved spending covered; raising one needs no check.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WafarDepositWithdrawalTest {

    private static final int JORDAN = 1004;
    private static final int SAUDI = 1005;
    private static final int STUDENT = 10119;

    @Mock private StudentFinanceWafarRepository depositRepository;
    @Mock private WafarSpendingRepository spendingRepository;
    @Mock private StudentFinanceRepository financeRepository;
    @Mock private CountryScopeService countryScopeService;

    private StudentFinanceWafarService service;

    @BeforeEach
    void setUp() {
        WafarBalanceService balance = new WafarBalanceService();
        ReflectionTestUtils.setField(balance, "depositRepository", depositRepository);
        ReflectionTestUtils.setField(balance, "spendingRepository", spendingRepository);
        ReflectionTestUtils.setField(balance, "countryScopeService", countryScopeService);
        service = new StudentFinanceWafarService();
        ReflectionTestUtils.setField(service, "repository", depositRepository);
        ReflectionTestUtils.setField(service, "financeRepository", financeRepository);
        ReflectionTestUtils.setField(service, "balanceService", balance);
        when(depositRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Jordan's pool: deposits and approved spends. */
    private void jordanHolds(String deposits, String approved) {
        when(depositRepository.sumTotalDeposits(eq(false), eq(List.of(JORDAN)))).thenReturn(new BigDecimal(deposits));
        when(spendingRepository.sumByStatus(eq(WafarSpendingStatus.APPROVED), eq(false), eq(List.of(JORDAN))))
                .thenReturn(new BigDecimal(approved));
    }

    /** The student's deposit on file: 100 at Jordan. */
    private StudentFinanceWafar depositOnFile() {
        StudentFinanceWafar w = row(JORDAN, 100);
        when(depositRepository.findById(STUDENT)).thenReturn(Optional.of(w));
        when(depositRepository.existsById(STUDENT)).thenReturn(true);
        return w;
    }

    private static StudentFinanceWafar row(Integer country, int amount) {
        StudentFinanceWafar w = new StudentFinanceWafar();
        w.setId(STUDENT);
        w.setStudyCountryInId(country);
        w.setStudentFinRecievedAmount(amount);
        return w;
    }

    private static void assertNotCovered(ThrowingCallable call, String taken, String left) {
        assertThatThrownBy(call).isInstanceOf(LocalizedStatusException.class)
                .satisfies(e -> {
                    LocalizedStatusException refusal = (LocalizedStatusException) e;
                    org.assertj.core.api.Assertions.assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    org.assertj.core.api.Assertions.assertThat(refusal.messageKey()).isEqualTo("wafar.deposit.notCovered");
                    org.assertj.core.api.Assertions.assertThat(refusal.messageArgs()).containsExactly(taken, left);
                });
    }

    @Test
    @DisplayName("the QA case: deleting the deposit of 100 while 10 of approved spending remains is refused, every path")
    void deleteRefused() {
        jordanHolds("100", "10");
        depositOnFile();

        assertNotCovered(() -> service.delete(STUDENT), "100", "90");
        assertNotCovered(() -> service.deleteIfExists(STUDENT), "100", "90");
        StudentFinance noWafar = new StudentFinance();
        noWafar.setId(STUDENT);
        noWafar.setStudyCountryInId(JORDAN);
        noWafar.setWafarAmount(0);
        assertNotCovered(() -> service.syncFromFinance(noWafar, null), "100", "90");
        verify(depositRepository, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("with no approved spending the deposit is deleted")
    void deleteAllowed() {
        jordanHolds("100", "0");
        depositOnFile();

        assertThatCode(() -> service.delete(STUDENT)).doesNotThrowAnyException();
        verify(depositRepository).deleteById(STUDENT);
    }

    @Test
    @DisplayName("lowering takes only the difference")
    void lowering() {
        jordanHolds("100", "10");
        depositOnFile();
        assertThatCode(() -> service.update(STUDENT, row(JORDAN, 95))).doesNotThrowAnyException();

        jordanHolds("100", "96");
        depositOnFile();
        assertNotCovered(() -> service.update(STUDENT, row(JORDAN, 5)), "95", "4");
    }

    @Test
    @DisplayName("moving the deposit to another attaché takes all of it from the old one")
    void moving() {
        jordanHolds("100", "10");
        depositOnFile();
        assertNotCovered(() -> service.update(STUDENT, row(SAUDI, 100)), "100", "90");
    }

    @Test
    @DisplayName("raising a deposit needs no check, even with nothing left")
    void raising() {
        jordanHolds("100", "100");
        depositOnFile();
        assertThatCode(() -> service.update(STUDENT, row(JORDAN, 150))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a university-fee discount that overwrites a larger deposit is checked")
    void feeDiscountOverwrite() {
        jordanHolds("100", "90");
        depositOnFile();
        StudyUniversityFee fee = new StudyUniversityFee();
        fee.setStudentId(STUDENT);
        fee.setStudyUniversityFeeDiscount(20);
        assertNotCovered(() -> service.allocateUniversityFeeDiscount(fee), "80", "10");
    }

    @Test
    @DisplayName("a finance edit that lowers the Wafar is checked; a row with no attaché is in no balance")
    void financeSyncAndNoAttache() {
        jordanHolds("100", "10");
        depositOnFile();
        StudentFinance lower = new StudentFinance();
        lower.setId(STUDENT);
        lower.setStudyCountryInId(JORDAN);
        lower.setWafarAmount(80);
        assertThatCode(() -> service.syncFromFinance(lower, null)).doesNotThrowAnyException();

        when(depositRepository.findById(STUDENT)).thenReturn(Optional.of(row(null, 100)));
        assertThatCode(() -> service.delete(STUDENT)).doesNotThrowAnyException();
    }

    private static StudentFinance finance(Integer country, Integer wafar) {
        StudentFinance f = new StudentFinance();
        f.setId(STUDENT);
        f.setStudyCountryInId(country);
        f.setWafarAmount(wafar);
        return f;
    }

    @Test
    @DisplayName("round-6 R6-01: a finance request that drops, lowers or moves the Wafar is checked when raised")
    void financeRequestCheckedWhenRaised() {
        // The QA case: 90 of Jordan's 100 is approved spending; the edit drops the Wafar.
        jordanHolds("100", "90");
        depositOnFile();
        assertNotCovered(() -> service.requireWithdrawableByFinance(STUDENT, finance(JORDAN, 0)), "100", "10");
        assertNotCovered(() -> service.requireWithdrawableByFinance(STUDENT, finance(JORDAN, null)), "100", "10");
        assertNotCovered(() -> service.requireWithdrawableByFinance(STUDENT, finance(JORDAN, 5)), "95", "10");
        assertNotCovered(() -> service.requireWithdrawableByFinance(STUDENT, finance(SAUDI, 100)), "100", "10");
        // A finance delete removes the Wafar row with it.
        assertNotCovered(() -> service.requireWithdrawableByFinance(STUDENT, null), "100", "10");
        verify(depositRepository, never()).deleteById(anyInt());
        verify(depositRepository, never()).save(any());
    }

    @Test
    @DisplayName("round-6 R6-01: an edit the balance covers, a raise, or a student with no Wafar row is let through")
    void financeRequestAllowed() {
        jordanHolds("100", "90");
        depositOnFile();
        assertThatCode(() -> service.requireWithdrawableByFinance(STUDENT, finance(JORDAN, 95))).doesNotThrowAnyException();
        assertThatCode(() -> service.requireWithdrawableByFinance(STUDENT, finance(JORDAN, 150))).doesNotThrowAnyException();

        when(depositRepository.findById(STUDENT)).thenReturn(Optional.empty());
        assertThatCode(() -> service.requireWithdrawableByFinance(STUDENT, finance(JORDAN, 0))).doesNotThrowAnyException();
        assertThatCode(() -> service.requireWithdrawableByFinance(null, null)).doesNotThrowAnyException();
    }
}
