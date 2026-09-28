package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.ChartDataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Round-5 QA R5-31: the view-based "by year" and "by quarter" charts drew one bar per
 * year and country without naming the country, so years repeated; the by-degree and
 * by-status charts repeated names the same way. Each chart now totals its view per label
 * over the countries in scope. (The grouped statements were run against the development
 * database: 4 year rows became 2 years, degree averages equal the view's own.)
 */
class ChartTotalsPerLabelTest {

    private JdbcTemplate jdbc;
    private ChartDataService service;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        Mockito.when(jdbc.queryForList(anyString())).thenReturn(List.of());
        service = new ChartDataService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        // The real helper: a Jordan and Saudi account's clause, as the service composes it.
        ChartCountrySqlHelper scoped = Mockito.spy(new ChartCountrySqlHelper(
                Mockito.mock(com.dilaw.mohe.ums.service.ReportCountryScopeHelper.class)));
        Mockito.doReturn(new ChartCountrySqlHelper.ScopeContext(List.of(1004, 1005), true))
                .when(scoped).resolveScope(Mockito.any());
        ReflectionTestUtils.setField(service, "chartCountrySqlHelper", scoped);
    }

    private String sqlOf(Function<ChartDataService, ChartDataDto> chart) {
        chart.apply(service);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbc, Mockito.atLeastOnce()).queryForList(sql.capture());
        Mockito.clearInvocations(jdbc);
        return sql.getValue();
    }

    @Test
    @DisplayName("each chart groups its view by the label, after the country scope")
    void groupedByLabel() {
        assertThat(sqlOf(s -> s.getFinancialDataByYear(null)))
                .contains("SUM(TotalAmount)", "SUM(StudentCount)")
                .containsSubsequence("StudyCountryInId IN (1004,1005)", "GROUP BY [Year]");
        assertThat(sqlOf(s -> s.getFinancialByQuarter(null)))
                .contains("SUM(TotalReceived)", "MAX(TotalBudget)")
                .containsSubsequence("StudyCountryInId IN (1004,1005)", "GROUP BY QuarterName");
        assertThat(sqlOf(s -> s.getStudentsByDegree(null)))
                .containsSubsequence("StudyCountryInId IN (1004,1005)", "GROUP BY DegreeName, DegreeNameEn");
        assertThat(sqlOf(s -> s.getStudentStatusDistribution(null)))
                .containsSubsequence("StudyCountryInId IN (1004,1005)", "GROUP BY StatusName, StatusNameEn");
    }

    @Test
    @DisplayName("one bar per year, with the totals the grouped statement returns")
    void oneBarPerYear() {
        Mockito.when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("Year", 2024, "TotalAmount", new BigDecimal("1631220.00"), "StudentCount", 979,
                        "AvgAmount", new BigDecimal("1666.21")),
                Map.of("Year", 2025, "TotalAmount", new BigDecimal("3360.00"), "StudentCount", 2,
                        "AvgAmount", new BigDecimal("1680.00"))));
        ChartDataDto chart = service.getFinancialDataByYear(null);
        assertThat(chart.getLabels()).containsExactly("2024", "2025").doesNotHaveDuplicates();
    }
}
