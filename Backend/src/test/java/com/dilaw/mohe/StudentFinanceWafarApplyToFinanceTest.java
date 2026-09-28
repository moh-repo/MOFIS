package com.dilaw.mohe;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.repository.StudentFinanceRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The mirror of {@link StudentFinanceWafarSyncTest}: a wafar entered on the wafar page has
 * to reach STUDENT_FINANCE, or the finance screen and every report keep reporting an
 * amount the student never received.
 *
 * A wafar also belongs to the period the money was disbursed in. Without that guard a Q1
 * wafar could be deducted from a Q3 payment, and the mismatch would only ever surface as a
 * wrong total in a report — nowhere near the screen that caused it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentFinanceWafarApplyToFinanceTest {

    @Mock
    private StudentFinanceWafarRepository repository;

    @Mock
    private StudentFinanceRepository financeRepository;

    @InjectMocks
    private StudentFinanceWafarService service;

    private StudentFinance finance;
    private StudentFinanceWafar wafar;

    @BeforeEach
    void setUp() {
        finance = new StudentFinance();
        finance.setId(42);
        finance.setStudentFinRecievedAmount(5000);
        finance.setStudentFinRecievedYear(2026);
        finance.setStudentFinRecievedMonth(2);

        wafar = new StudentFinanceWafar();
        wafar.setId(42);
        wafar.setStudentFinRecievedAmount(1200);
        wafar.setWafarTypeId(3);
        wafar.setStudentFinRecievedYear(2026);
        wafar.setStudentFinRecievedMonth(2);

        when(financeRepository.findById(42)).thenReturn(Optional.of(finance));
        when(financeRepository.save(any(StudentFinance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void writesTheWafarOntoTheFinanceRecord() {
        StudentFinance saved = service.applyToFinance(wafar);

        assertEquals(1200, saved.getWafarAmount());
        assertEquals(3, saved.getWafarTypeId());
    }

    /*
     * The finance record is the anchor the wafar was validated against, so applying a wafar
     * must not move the disbursement itself. NET_AMOUNT is computed from these two columns,
     * so overwriting the amount here would quietly restate what the student was paid.
     */
    @Test
    void leavesTheFinanceAmountAndPeriodAlone() {
        service.applyToFinance(wafar);

        ArgumentCaptor<StudentFinance> captor = ArgumentCaptor.forClass(StudentFinance.class);
        verify(financeRepository).save(captor.capture());
        assertEquals(5000, captor.getValue().getStudentFinRecievedAmount());
        assertEquals(2026, captor.getValue().getStudentFinRecievedYear());
        assertEquals(2, captor.getValue().getStudentFinRecievedMonth());
    }

    @Test
    void rejectsAWafarFromADifferentQuarter() {
        wafar.setStudentFinRecievedMonth(4);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.applyToFinance(wafar));

        assertTrue(ex.getReason().contains("2026 Q4"), "the message must name the wafar period: " + ex.getReason());
        assertTrue(ex.getReason().contains("2026 Q2"), "and the finance period: " + ex.getReason());
        verify(financeRepository, never()).save(any(StudentFinance.class));
    }

    @Test
    void rejectsAWafarFromADifferentYear() {
        wafar.setStudentFinRecievedYear(2025);

        assertThrows(ResponseStatusException.class, () -> service.applyToFinance(wafar));

        verify(financeRepository, never()).save(any(StudentFinance.class));
    }

    /** Nothing to match a period against, so the wafar cannot be placed in one. */
    @Test
    void rejectsWhenTheFinanceRecordHasNoPeriod() {
        finance.setStudentFinRecievedYear(0);
        finance.setStudentFinRecievedMonth(0);

        assertThrows(ResponseStatusException.class, () -> service.applyToFinance(wafar));

        verify(financeRepository, never()).save(any(StudentFinance.class));
    }

    @Test
    void rejectsAWafarForAStudentWithNoFinanceRecord() {
        when(financeRepository.findById(42)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.applyToFinance(wafar));

        assertTrue(ex.getReason().contains("42"), "the message must name the student: " + ex.getReason());
    }

    /*
     * STUDENT_ID is a manually-assigned primary key. Reaching repository.save() with a null
     * id produced "ids for this class must be manually assigned before calling save()",
     * which named neither the student nor the screen that caused it.
     */
    @Test
    void refusesToCreateAWafarWithNoStudentId() {
        StudentFinanceWafar orphan = new StudentFinanceWafar();
        orphan.setStudentFinRecievedAmount(1200);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(orphan));

        assertTrue(ex.getReason().contains("student id"), ex.getReason());
        verify(repository, never()).save(any(StudentFinanceWafar.class));
    }

    /** Otherwise the finance record keeps deducting a wafar that no longer exists. */
    @Test
    void clearsTheFinanceWafarWhenTheWafarRowIsDeleted() {
        service.clearFinanceWafar(42);

        ArgumentCaptor<StudentFinance> captor = ArgumentCaptor.forClass(StudentFinance.class);
        verify(financeRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getWafarAmount());
        assertEquals(0, captor.getValue().getWafarTypeId());
    }

    @Test
    void clearingIsANoOpWhenThereIsNoFinanceRecord() {
        when(financeRepository.findById(42)).thenReturn(Optional.empty());

        service.clearFinanceWafar(42);

        verify(financeRepository, never()).save(any(StudentFinance.class));
    }
}
