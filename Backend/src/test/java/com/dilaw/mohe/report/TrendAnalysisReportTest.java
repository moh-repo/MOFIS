package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.TrendAnalysisMetadataDto;
import com.dilaw.mohe.model.TrendAnalysis;
import com.dilaw.mohe.service.TrendAnalysisMetadataService;
import com.dilaw.mohe.service.TrendAnalysisService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The trend must follow the money, not the audit timestamps.
 *
 * <p>Periods were taken from STUDY_FINANCE_MIDATE, the row's last-modified
 * column. Because 939 of the 941 finance rows were loaded in a single operation,
 * the page charted two months that contain no payment at all.
 */
@Tag("integration")
@SpringBootTest
class TrendAnalysisReportTest {

    @Autowired private TrendAnalysisService service;
    @Autowired private TrendAnalysisMetadataService metadataService;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    @Test
    @DisplayName("each period matches a month that actually holds payments")
    void periods_followThePayments() {
        List<TrendAnalysis> rows = service.getAll(null, null);
        assertThat(rows).as("the finance table is expected to hold payments").isNotEmpty();

        // The months the money belongs to, read independently of the query
        // under test.
        List<String> paymentMonths = jdbc.queryForList("""
                SELECT DISTINCT CAST(COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) AS NVARCHAR(4))
                       + '-' + RIGHT('0' + CAST(COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH) AS NVARCHAR(2)), 2) AS p
                FROM MOHECORE.STUDENT_FINANCE sf
                WHERE COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) IS NOT NULL
                  AND COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH) BETWEEN 1 AND 12
                """, String.class);

        assertThat(rows).allSatisfy(r -> assertThat(paymentMonths)
                .as("period %s must be a month that holds payments", r.getTrendPeriod())
                .contains(r.getTrendPeriod()));

        // The last-modified months, which is what the page used to plot. 2025-11
        // holds no payment, so its presence would mean the regression is back.
        List<String> modifiedMonths = jdbc.queryForList("""
                SELECT DISTINCT CAST(YEAR(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(4))
                       + '-' + RIGHT('0' + CAST(MONTH(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(2)), 2) AS p
                FROM MOHECORE.STUDENT_FINANCE sf WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
                """, String.class);
        assertThat(modifiedMonths)
                .as("this test is only meaningful while the two differ")
                .isNotEqualTo(paymentMonths);
        assertThat(rows.stream().map(TrendAnalysis::getTrendPeriod))
                .as("no period may come from a month that only saw record edits")
                .noneMatch(p -> modifiedMonths.contains(p) && !paymentMonths.contains(p));
    }

    @Test
    @DisplayName("totals reconcile with the finance rows they summarise")
    void totals_matchTheUnderlyingRows() {
        List<TrendAnalysis> rows = service.getAll(null, null);

        long charted = rows.stream()
                .filter(r -> "finance_amount".equals(r.getMetricType()))
                .mapToLong(r -> r.getMetricValue().longValue())
                .sum();
        Long actual = jdbc.queryForObject("""
                SELECT SUM(CAST(COALESCE(sf.NET_AMOUNT,0) AS BIGINT))
                FROM MOHECORE.STUDENT_FINANCE sf
                WHERE COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) IS NOT NULL
                  AND COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH) BETWEEN 1 AND 12
                """, Long.class);

        assertThat(charted)
                .as("the charted total must equal the net amount on the rows it covers")
                .isEqualTo(actual);
        assertThat(charted).as("and must not be zero, or the check above is vacuous").isGreaterThan(0);
    }

    @Test
    @DisplayName("filter options offer only periods the rows can match")
    void metadata_agreesWithTheRows() {
        List<TrendAnalysis> rows = service.getAll(null, null);
        TrendAnalysisMetadataDto meta = metadataService.getMetadata(null, null);

        List<Integer> rowYears = rows.stream().map(TrendAnalysis::getTrendYear).distinct().toList();
        assertThat(meta.getYears())
                .as("the year dropdown read a view with different periods, so it offered years no row could match")
                .isNotEmpty()
                .allSatisfy(y -> assertThat(rowYears).contains(y));

        List<String> rowMetrics = rows.stream().map(TrendAnalysis::getMetricType).distinct().toList();
        assertThat(meta.getMetricTypes()).isNotEmpty();
        assertThat(meta.getMetricTypes().stream().map(LookupOptionDto::getName))
                .allSatisfy(name -> assertThat(rowMetrics).contains(name));

        // Every option must return rows when it is applied.
        meta.getYears().forEach(y -> assertThat(service.search(null, null, y, y, null, null))
                .as("year option %s must return rows", y).isNotEmpty());
        meta.getDirections().forEach(d -> assertThat(
                service.search(null, d.getName(), null, null, null, null))
                .as("direction option '%s' must return rows", d.getName()).isNotEmpty());
    }
}
