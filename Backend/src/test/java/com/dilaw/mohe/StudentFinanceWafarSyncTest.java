package com.dilaw.mohe;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The finance form no longer writes STUDENT_FINANCE_WAFAR itself — the row is
 * materialized here when the STUDENT_FINANCE approval request is executed. These
 * cover the behaviour the wafar page depends on to show an approved record.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentFinanceWafarSyncTest {

    @Mock
    private StudentFinanceWafarRepository repository;

    @InjectMocks
    private StudentFinanceWafarService service;

    private StudentFinance finance;

    @BeforeEach
    void setUp() {
        finance = new StudentFinance();
        finance.setId(42);
        finance.setStudentFinId("F-42");
        finance.setStudentFinName("Test Student");
        finance.setStudentFinRecievedAmount(5000);
        finance.setWafarAmount(1200);
        finance.setWafarTypeId(3);
        finance.setStudyCountryInId(7);
        finance.setStudyGovInId(8);
        finance.setStudyFinanceBankeId(9);
        finance.setStudyFinanceBankBranchId(10);
        finance.setStudyFinanceDofId(11);
        finance.setStudyFinRecievedStatus(1);

        when(repository.save(any(StudentFinanceWafar.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void writesTheWafarAmount_notTheFinanceAmount() {
        when(repository.findById(42)).thenReturn(Optional.empty());

        StudentFinanceWafar saved = service.syncFromFinance(finance, "note text");

        assertEquals(42, saved.getId());
        // On the wafar row this column carries the wafar amount, not the finance amount.
        assertEquals(1200, saved.getStudentFinRecievedAmount());
        assertEquals(3, saved.getWafarTypeId());
        assertEquals("note text", saved.getStudyNote1());
    }

    /*
     * getAll() filters through StudentWafarSpec.countryScope(...), so a row saved
     * without a country is invisible on the wafar page to every country-scoped
     * user — the record would look like it silently vanished after approval.
     */
    @Test
    void copiesCountrySoTheRowSurvivesCountryScoping() {
        when(repository.findById(42)).thenReturn(Optional.empty());

        StudentFinanceWafar saved = service.syncFromFinance(finance, null);

        assertEquals(7, saved.getStudyCountryInId());
        assertEquals(8, saved.getStudyGovInId());
        assertEquals(9, saved.getStudyFinanceBankeId());
        assertEquals(10, saved.getStudyFinanceBankBranchId());
        assertEquals(11, saved.getStudyFinanceDofId());
    }

    /*
     * The period copy keeps both tables on the same year and quarter, which
     * StudentFinanceWafarApplyToFinanceTest now enforces from the other direction. Drop it
     * and a wafar row keeps whatever period it had; the next edit of that record on the
     * wafar page is then rejected as a period mismatch, a long way from the cause.
     */
    @Test
    void copiesThePeriodSoBothTablesStayOnTheSameQuarter() {
        finance.setStudentFinRecievedYear(2026);
        finance.setStudentFinRecievedMonth(2);
        finance.setStudentFinRecievedYearMonth(20262);
        when(repository.findById(42)).thenReturn(Optional.of(new StudentFinanceWafar()));

        StudentFinanceWafar saved = service.syncFromFinance(finance, null);

        assertEquals(2026, saved.getStudentFinRecievedYear());
        assertEquals(2, saved.getStudentFinRecievedMonth());
        assertEquals(20262, saved.getStudentFinRecievedYearMonth());
    }

    @Test
    void updatesTheExistingRowRatherThanDuplicating() {
        StudentFinanceWafar existing = new StudentFinanceWafar();
        existing.setId(42);
        existing.setStudentFinRecievedAmount(500);
        when(repository.findById(42)).thenReturn(Optional.of(existing));

        service.syncFromFinance(finance, "updated");

        ArgumentCaptor<StudentFinanceWafar> captor = ArgumentCaptor.forClass(StudentFinanceWafar.class);
        verify(repository).save(captor.capture());
        assertEquals(1200, captor.getValue().getStudentFinRecievedAmount());
        verify(repository, never()).deleteById(any());
    }

    /** "Has wafar" unticked — the stale row must go, not linger at its old amount. */
    @Test
    void removesTheRowWhenWafarIsClearedToZero() {
        finance.setWafarAmount(0);
        when(repository.findById(42)).thenReturn(Optional.of(new StudentFinanceWafar()));

        assertNull(service.syncFromFinance(finance, null));

        verify(repository).deleteById(42);
        verify(repository, never()).save(any(StudentFinanceWafar.class));
    }

    @Test
    void doesNothingWhenThereIsNoWafarAndNoExistingRow() {
        finance.setWafarAmount(null);
        when(repository.findById(42)).thenReturn(Optional.empty());

        assertNull(service.syncFromFinance(finance, null));

        verify(repository, never()).deleteById(any());
        verify(repository, never()).save(any(StudentFinanceWafar.class));
    }

    @Test
    void ignoresAFinanceRecordWithNoStudentId() {
        finance.setId(null);

        assertNull(service.syncFromFinance(finance, "note"));

        verify(repository, never()).save(any(StudentFinanceWafar.class));
    }
}
