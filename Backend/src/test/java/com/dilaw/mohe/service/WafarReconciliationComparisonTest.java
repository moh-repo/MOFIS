package com.dilaw.mohe.service;

import com.dilaw.mohe.accounting.repository.WafarEarmarkRepository;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.WafarReconciliation;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.WafarReconciliationRepository;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.Decision;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the Wafar reconciliation compares (QA FIN-002, decision DEC-01).
 *
 * <p>It used to set a finance row's grant against the Wafar on the same row, so any
 * student without Wafar was a "discrepancy" for the whole grant — 920 of 941. It now
 * sets the Wafar on the finance record against what accounting earmarked for the
 * student in their latest earmarked quarter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WafarReconciliationComparisonTest {

    @Mock private WafarReconciliationRepository reconciliationRepository;
    @Mock private StudentFinanceRepository financeRepository;
    @Mock private WafarEarmarkRepository earmarkRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private AccessControl accessControl;
    @InjectMocks private WafarReconciliationService service;

    private static StudentFinance finance(int studentId, Integer grant, Integer wafar) {
        StudentFinance f = new StudentFinance();
        f.setId(studentId);
        f.setStudentFinRecievedAmount(grant);
        f.setWafarAmount(wafar);
        return f;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, WafarReconciliation> run(List<StudentFinance> finances, List<Object[]> earmarks) {
        // An attaché's own run: allowed with no exemption, so no write key is raised.
        when(accessControl.assertAllowed(anyString(), any(), anyString())).thenReturn(Decision.allow());
        when(financeRepository.findAll()).thenReturn(finances);
        when(earmarkRepository.sumByStudentAndQuarter()).thenReturn(earmarks);
        when(reconciliationRepository.findByStudentIdInOrderByReconIdAsc(any())).thenReturn(List.of());

        service.runReconciliation();

        ArgumentCaptor<List<WafarReconciliation>> saved = ArgumentCaptor.forClass(List.class);
        verify(reconciliationRepository).saveAll(saved.capture());
        return saved.getValue().stream()
                .collect(Collectors.toMap(WafarReconciliation::getStudentId, Function.identity()));
    }

    @Test
    @DisplayName("no Wafar on either side is a match, whatever the grant")
    void noWafarAnywhereIsMatched() {
        Map<Integer, WafarReconciliation> rows = run(List.of(finance(1, 1500, null), finance(2, 1860, 0)), List.of());

        assertThat(rows.get(1).getReconStatus()).isEqualTo(WafarReconciliationService.STATUS_MATCHED);
        assertThat(rows.get(2).getReconStatus()).isEqualTo(WafarReconciliationService.STATUS_MATCHED);
    }

    @Test
    @DisplayName("Wafar that equals the earmark is a match")
    void equalToEarmarkIsMatched() {
        Map<Integer, WafarReconciliation> rows = run(List.of(finance(3, 1500, 500)),
                List.<Object[]>of(new Object[] {3, 20242, new BigDecimal("500.00")}));

        WafarReconciliation r = rows.get(3);
        assertThat(r.getReconStatus()).isEqualTo(WafarReconciliationService.STATUS_MATCHED);
        assertThat(r.getMoheAmount()).as("the finance record's Wafar").isEqualTo(500);
        assertThat(r.getWafarAmount()).as("the earmarked amount").isEqualTo(500);
    }

    @Test
    @DisplayName("Wafar with no earmark, or an earmark with no Wafar, is a discrepancy that says which")
    void oneSidedIsADiscrepancy() {
        Map<Integer, WafarReconciliation> rows = run(List.of(finance(4, 1500, 600), finance(5, 1500, null)),
                List.<Object[]>of(new Object[] {5, 20242, new BigDecimal("300")}));

        assertThat(rows.get(4).getReconStatus()).isEqualTo(WafarReconciliationService.STATUS_DISCREPANCY);
        assertThat(rows.get(4).getNotes()).contains("nothing is earmarked");
        assertThat(rows.get(5).getReconStatus()).isEqualTo(WafarReconciliationService.STATUS_DISCREPANCY);
        assertThat(rows.get(5).getNotes()).contains("no Wafar");
    }

    @Test
    @DisplayName("only the latest earmarked quarter counts, so two quarters are not added together")
    void latestQuarterOnly() {
        Map<Integer, WafarReconciliation> rows = run(List.of(finance(6, 1500, 700)),
                List.<Object[]>of(new Object[] {6, 20241, new BigDecimal("700")}, new Object[] {6, 20242, new BigDecimal("700")}));

        assertThat(rows.get(6).getWafarAmount()).isEqualTo(700);
        assertThat(rows.get(6).getReconStatus()).isEqualTo(WafarReconciliationService.STATUS_MATCHED);
    }
}
