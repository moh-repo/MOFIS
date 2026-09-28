package com.dilaw.mohe.service;

import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.model.StudentFinance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The server-side input rules added for review items F3, F5 and F6. */
class FinanceInputRulesTest {

    private static StudentFinance finance() {
        StudentFinance f = new StudentFinance();
        f.setId(42);
        f.setStudentFinRecievedAmount(500);
        f.setStudyCheckDate(Instant.parse("2026-09-01T00:00:00Z"));
        f.setStudyFinRecievedStatus(1);
        f.setStudyCountryInId(3);
        return f;
    }

    @Test
    @DisplayName("F3: a complete finance record passes")
    void completeFinancePasses() {
        assertThatCode(() -> StudentFinanceInputRules.require(finance())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F3: amount 0, no status and no country are each named")
    void financeProblemsAreNamed() {
        StudentFinance f = finance();
        f.setStudentFinRecievedAmount(0);
        f.setStudyFinRecievedStatus(0);
        f.setStudyCountryInId(null);

        assertThat(StudentFinanceInputRules.problems(f))
                .containsExactly("amount (must be greater than 0)", "received status", "study country");
        assertThatThrownBy(() -> StudentFinanceInputRules.require(f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("H7: the received year is a calendar year; a YYYYQ key (20251) is refused")
    void receivedYearIsAYear() {
        StudentFinance f = finance();
        f.setStudentFinRecievedYear(20251);
        assertThat(StudentFinanceInputRules.problems(f)).containsExactly("received year (a year between 1990 and 2100)");
        assertThatThrownBy(() -> StudentFinanceInputRules.requireReceivedYear(f))
                .isInstanceOf(IllegalArgumentException.class);

        f.setStudentFinRecievedYear(2025);
        assertThat(StudentFinanceInputRules.problems(f)).isEmpty();
        f.setStudentFinRecievedYear(0);
        assertThatCode(() -> StudentFinanceInputRules.requireReceivedYear(f)).as("0 means not set").doesNotThrowAnyException();
    }

    @Test
    @DisplayName("H7: an approval draft carrying the key as the year is split before it is saved")
    void approvalDraftKeyIsSplit() {
        StudentFinance target = finance();
        StudentFinance draft = new StudentFinance();
        draft.setStudentFinRecievedYear(20251);
        draft.setStudentFinRecievedMonth(0);
        StudentFinanceService.mergeNonNullDraftFields(target, draft, StudyFinReceivedStatusService.NOT_SPECIFIED_ID_FALLBACK);
        assertThat(target.getStudentFinRecievedYear()).isEqualTo(2025);
        assertThat(target.getStudentFinRecievedMonth()).isEqualTo(1);
        assertThat(target.getStudentFinRecievedYearMonth()).isEqualTo(20251);
    }

    @Test
    @DisplayName("H2: a row nobody has been paid for needs no cheque date")
    void notSpecifiedNeedsNoCheckDate() {
        StudentFinance f = finance();
        f.setStudyFinRecievedStatus(StudyFinReceivedStatusService.NOT_SPECIFIED_ID_FALLBACK);
        f.setStudyCheckDate(null);

        assertThat(StudentFinanceInputRules.problems(f)).isEmpty();
        assertThatCode(() -> StudentFinanceInputRules.require(f)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("H2: a received, disbursed or partially disbursed row still names its missing cheque date")
    void paidStatusesStillNeedTheCheckDate() {
        for (int paid : StudyFinReceivedStatusService.PAID_IDS_FALLBACK) {
            StudentFinance f = finance();
            f.setStudyFinRecievedStatus(paid);
            f.setStudyCheckDate(null);
            assertThat(StudentFinanceInputRules.problems(f)).as("status %d", paid).containsExactly("check date");
        }
        // The set the controller resolves by CODE is what decides, not the fallback ids.
        StudentFinance f = finance();
        f.setStudyFinRecievedStatus(7);
        f.setStudyCheckDate(null);
        assertThat(StudentFinanceInputRules.problems(f, id -> id == 7)).containsExactly("check date");
        assertThat(StudentFinanceInputRules.problems(f, id -> id == 8)).isEmpty();
    }

    @Test
    @DisplayName("H2: a status of 0 or none is still refused — NOT_SPECIFIED exists for that")
    void zeroStatusStillRefused() {
        StudentFinance f = finance();
        f.setStudyFinRecievedStatus(0);
        assertThat(StudentFinanceInputRules.problems(f)).containsExactly("received status");
        f.setStudyFinRecievedStatus(null);
        assertThat(StudentFinanceInputRules.problems(f)).containsExactly("received status");
    }

    @Test
    @DisplayName("F3: the wafar cannot exceed the amount it is taken out of")
    void wafarWithinAmount() {
        StudentFinance f = finance();
        f.setWafarAmount(501);
        assertThat(StudentFinanceInputRules.problems(f)).containsExactly("wafar amount (must not exceed the amount)");

        f.setWafarAmount(500);
        assertThat(StudentFinanceInputRules.problems(f)).isEmpty();
    }

    private static PaymentAuth payment() {
        PaymentAuth p = new PaymentAuth();
        p.setStudentId(42);
        p.setAuthDate(LocalDate.of(2026, 9, 19));
        p.setOriginalAmount(new BigDecimal("100.00"));
        p.setDeductions(new BigDecimal("10.00"));
        return p;
    }

    @Test
    @DisplayName("F6: a complete payment authorisation passes")
    void completePaymentPasses() {
        assertThatCode(() -> PaymentAuthService.requireValid(payment())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F6: no date, a zero amount or deductions above it are refused")
    void paymentProblemsAreRefused() {
        PaymentAuth noDate = payment();
        noDate.setAuthDate(null);
        assertThatThrownBy(() -> PaymentAuthService.requireValid(noDate))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("date");

        PaymentAuth zero = payment();
        zero.setOriginalAmount(BigDecimal.ZERO);
        zero.setDeductions(null);
        assertThatThrownBy(() -> PaymentAuthService.requireValid(zero))
                .hasMessageContaining("original amount");

        PaymentAuth over = payment();
        over.setDeductions(new BigDecimal("100.01"));
        assertThatThrownBy(() -> PaymentAuthService.requireValid(over))
                .hasMessageContaining("must not exceed");
    }

    @Test
    @DisplayName("F5: the finance history copies finance columns only")
    void archiveCopiesFinanceColumnsOnly() {
        StudentDataArch stored = new StudentDataArch();
        stored.setStudentName("Stored Name");
        stored.setStudentPassportNumber("P-1");
        stored.setStudentAmount(100);

        StudentDataArch sent = new StudentDataArch();
        sent.setStudentName("Changed Name");
        sent.setStudentPassportNumber("P-2");
        sent.setStudentAmount(250);
        sent.setAmountCurrency(2);
        sent.setStudentNote1("corrected");

        StudentDataArchService.copyFinanceColumns(sent, stored);

        assertThat(stored.getStudentAmount()).isEqualTo(250);
        assertThat(stored.getAmountCurrency()).isEqualTo(2);
        assertThat(stored.getStudentNote1()).isEqualTo("corrected");
        assertThat(stored.getStudentName()).as("identity is kept").isEqualTo("Stored Name");
        assertThat(stored.getStudentPassportNumber()).isEqualTo("P-1");
    }

    @Test
    @DisplayName("F5: a negative amount is refused")
    void archiveRefusesNegativeAmount() {
        StudentDataArch sent = new StudentDataArch();
        sent.setStudentAmount(-1);
        assertThatThrownBy(() -> StudentDataArchService.copyFinanceColumns(sent, new StudentDataArch()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
