package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.ChartDataDto;
import com.dilaw.mohe.model.StudentFinancialDataForm;
import com.dilaw.mohe.repository.PaymentSummaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Round-5 QA R5-31: the quarterly disbursement chart labelled "2024 Q1, 2025 Q1, 2026 Q1"
 * money received in Q2 2024, Q1 2025 and Q3 2026. STUDENT_FIN_RECIEVED_MONTH holds the
 * quarter (1-4; the period key is YEAR * 10 + MONTH), and it was folded as a calendar
 * month, ((m - 1) / 3) + 1, which sends quarters 1-3 to quarter 1. The same fold was in
 * the Financial Data Form's quarter and in Payment Summary's MOHE_QUARTER join, whose
 * YYYYQ key it could never match.
 */
class FinanceQuarterColumnTest {

    @Test
    @DisplayName("the quarterly chart reads the quarter as it is stored and labels it so")
    void quarterlyChartLabels() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        ChartCountrySqlHelper scope = Mockito.mock(ChartCountrySqlHelper.class);
        Mockito.when(scope.resolveScope(any())).thenReturn(new ChartCountrySqlHelper.ScopeContext(List.of(), false));
        Mockito.when(scope.financeDirectInClause(anyString(), any())).thenReturn("");
        Mockito.when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("Year", 2024, "Quarter", 2, "TotalAmount", 1500, "AverageAmount", 1500, "PaymentCount", 1),
                Map.of("Year", 2025, "Quarter", 1, "TotalAmount", 900, "AverageAmount", 900, "PaymentCount", 1),
                Map.of("Year", 2026, "Quarter", 3, "TotalAmount", 700, "AverageAmount", 700, "PaymentCount", 1)));
        ChartDataService service = new ChartDataService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "chartCountrySqlHelper", scope);

        ChartDataDto chart = service.getQuarterlyDisbursementTrend(null);

        assertThat(chart.getLabels()).containsExactly("2024 Q2", "2025 Q1", "2026 Q3");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).doesNotContain("/ 3").contains("STUDENT_FIN_RECIEVED_MONTH AS Quarter")
                .contains("BETWEEN 1 AND 4");
    }

    @Test
    @DisplayName("the Financial Data Form's quarter is the stored quarter; anything outside 1-4 is none")
    void financialDataFormQuarter() {
        StudentFinancialDataForm row = new StudentFinancialDataForm();
        for (int q = 1; q <= 4; q++) {
            ReflectionTestUtils.setField(row, "studentFinReceivedMonth", q);
            assertThat(row.getQuarter()).isEqualTo(q);
        }
        ReflectionTestUtils.setField(row, "studentFinReceivedMonth", 7);
        assertThat(row.getQuarter()).isNull();
        ReflectionTestUtils.setField(row, "studentFinReceivedMonth", null);
        assertThat(row.getQuarter()).isNull();
    }

    @Test
    @DisplayName("Payment Summary joins the finance row's quarter by year and number")
    void paymentSummaryQuarterJoin() {
        assertThat(PaymentSummaryRepository.FROM)
                .contains("mq.QUARTER_YEAR = sf.STUDENT_FIN_RECIEVED_YEAR")
                .contains("mq.QUARTER_NO = sf.STUDENT_FIN_RECIEVED_MONTH")
                .doesNotContain("/ 3");
    }
}
