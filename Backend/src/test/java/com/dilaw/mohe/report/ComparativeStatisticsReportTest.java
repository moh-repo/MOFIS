package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.ComparativeStatisticsMetadataDto;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.model.ComparativeStatistics;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.ComparativeStatisticsMetadataService;
import com.dilaw.mohe.service.ComparativeStatisticsService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The comparison must count students by cohort year, and its periods must come
 * from the data.
 *
 * <p>It used to count by year of birth against {@code YEAR(GETDATE())}, so every
 * dimension reported zero against zero, and both period dropdowns were fed by
 * the clock rather than by any row.
 */
@Tag("integration")
@SpringBootTest
class ComparativeStatisticsReportTest {

    @Autowired private ComparativeStatisticsService service;
    @Autowired private ComparativeStatisticsMetadataService metadataService;
    @Autowired private ReportParamBuilder reportParamBuilder;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    @Test
    @DisplayName("the comparison reports real counts, not a table of zeros")
    void comparison_hasRealCounts() {
        List<ComparativeStatistics> rows = service.getAll(null, null);

        assertThat(rows).as("students are expected in the default cohort years").isNotEmpty();
        assertThat(rows)
                .as("a comparison where every value is zero is what the birth-year bug produced")
                .anySatisfy(r -> assertThat(r.getCurrentPeriodValue() + r.getPreviousPeriodValue())
                        .isGreaterThan(0));
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getComparisonType()).isNotBlank();
            assertThat(r.getDimensionName()).isNotBlank();
            assertThat(r.getCurrentPeriod()).isNotBlank();
            assertThat(r.getPreviousPeriod()).isNotBlank();
        });
    }

    @Test
    @DisplayName("counts reconcile with the students behind them")
    void counts_matchTheStudents() {
        List<ComparativeStatistics> rows = service.getAll(null, null);
        String period = rows.get(0).getCurrentPeriod();

        // Every 'degree' row for the period, summed, must be the number of
        // students whose delegation decision falls in that year.
        int chartedDegrees = rows.stream()
                .filter(r -> "degree".equals(r.getComparisonType()))
                .mapToInt(ComparativeStatistics::getCurrentPeriodValue)
                .sum();
        Integer actual = jdbc.queryForObject("""
                SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA sd
                WHERE ISNULL(sd.IS_DELETED, 0) = 0
                  AND TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) = ?
                """, Integer.class, Integer.valueOf(period));

        assertThat(chartedDegrees)
                .as("the degree breakdown for %s must account for every student in that year", period)
                .isEqualTo(actual);
        assertThat(chartedDegrees).as("or the check above is vacuous").isGreaterThan(0);
    }

    @Test
    @DisplayName("period options are cohort years from the data, not the clock")
    void periods_comeFromTheData() {
        ComparativeStatisticsMetadataDto meta = metadataService.getMetadata(null, null);

        List<Integer> yearsInData = jdbc.queryForList("""
                SELECT DISTINCT TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT)
                FROM MOHECORE.STUDENT_DATA sd
                WHERE ISNULL(sd.IS_DELETED, 0) = 0
                  AND TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) IS NOT NULL
                """, Integer.class);

        assertThat(meta.getCurrentPeriods()).as("the year dropdown must be populated").isNotEmpty();
        assertThat(meta.getCurrentPeriods())
                .as("every offered year must exist in the data")
                .allSatisfy(p -> assertThat(yearsInData).contains(Integer.valueOf(p)));
        assertThat(meta.getCurrentPeriods())
                .as("the list was two clock-derived values; the data holds many more")
                .hasSizeGreaterThan(2);
        assertThat(meta.getPreviousPeriods()).isEqualTo(meta.getCurrentPeriods());

        assertThat(meta.getComparisonTypes()).isNotEmpty();
        // Every option must select something.
        meta.getComparisonTypes().stream().map(LookupOptionDto::getName).forEach(type ->
                assertThat(service.search(type, null, null, null, null))
                        .as("comparison type '%s' must return rows", type).isNotEmpty());
        meta.getCurrentPeriods().forEach(p ->
                assertThat(service.search(null, p, null, null, null))
                        .as("year option %s must return rows", p).isNotEmpty());
    }

    @Test
    @DisplayName("a chosen year is the one compared")
    void chosenYear_isHonoured() {
        List<String> years = metadataService.getMetadata(null, null).getCurrentPeriods();
        String chosen = years.get(years.size() - 1);

        assertThat(service.search(null, chosen, null, null, null))
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.getCurrentPeriod()).isEqualTo(chosen));
    }

    @Test
    @DisplayName("the PDF renders the comparison in both languages")
    void pdf_renders() throws Exception {
        List<ComparativeStatistics> rows = service.getAll(null, null);

        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> params = reportParamBuilder.build("COMPARATIVE_STATISTICS", lang);
            String text = ReportRenderSupport.renderToText("comparative-statistics", params, rows);

            assertThat(text)
                    .as("lang=%s: the compared period must appear on the page", lang)
                    .contains(rows.get(0).getCurrentPeriod());
        }
    }
}
