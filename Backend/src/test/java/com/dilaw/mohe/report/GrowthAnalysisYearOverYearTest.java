package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.GrowthAnalysisReportRow;
import com.dilaw.mohe.model.GrowthAnalysis;
import com.dilaw.mohe.repository.GrowthAnalysisRepository;
import com.dilaw.mohe.repository.LuTrendClassRepository;
import com.dilaw.mohe.service.GrowthAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * RPT-07: growth is year over year on per-year counts, over a gap-free series of
 * years, and the first year says it is the base rather than claiming "stable".
 *
 * <p>The query runs on SQL Server only, so its shape is pinned here and its
 * numbers by the integration suite.
 */
class GrowthAnalysisYearOverYearTest {

    private static String searchSql() throws NoSuchMethodException {
        for (var m : GrowthAnalysisRepository.class.getDeclaredMethods()) {
            if (m.getName().equals("search")) return m.getAnnotation(Query.class).value();
        }
        throw new NoSuchMethodException("search");
    }

    @Test
    @DisplayName("each year counts that year's students, not everyone up to it")
    void perYearCounts() throws Exception {
        String sql = searchSql();
        assertThat(sql).contains("AS INT) = yr.analysis_year")
                .doesNotContain("<= YEAR(GETDATE()) - n");
    }

    @Test
    @DisplayName("every year of the window is generated, and a year with no students counts 0")
    void allYearsGenerated() throws Exception {
        String sql = searchSql();
        assertThat(sql).contains("FROM years yr").contains("LEFT JOIN MOHECORE.STUDENT_DATA sd")
                .contains("GROUP BY yr.analysis_year");
        assertThat(sql).as("the deletion and scope filters sit in the ON clause, so they cannot drop the year")
                .containsPattern("(?s)LEFT JOIN MOHECORE\\.STUDENT_DATA sd\\s+ON .*IS_DELETED.*applyCountryFilter.*GROUP BY");
    }

    @Test
    @DisplayName("the first year is the base; later years compare with the year before")
    void firstYearIsDistinct() throws Exception {
        String sql = searchSql();
        assertThat(sql).contains("WHEN prev_cnt IS NULL THEN '" + GrowthAnalysisRepository.BASELINE_STATUS + "'")
                .contains("WHEN cnt < prev_cnt THEN 'declining'")
                .contains("LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year)");
        assertThat(sql.indexOf("lagged AS")).as("filters apply after the comparison, not before")
                .isLessThan(sql.indexOf("WHERE (:category IS NULL"));
    }

    @Test
    @DisplayName("the base year is named in the report's language")
    void baselineIsNamed() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        GrowthAnalysisService service = new GrowthAnalysisService();
        ReflectionTestUtils.setField(service, "trendClasses", mock(LuTrendClassRepository.class));
        ReflectionTestUtils.setField(service, "messages", messages);
        GrowthAnalysis first = new GrowthAnalysis();
        first.setGrowthCategory("total_students");
        first.setAnalysisYear(2022);
        first.setGrowthStatus(GrowthAnalysisRepository.BASELINE_STATUS);

        List<GrowthAnalysisReportRow> en = service.reportRows(List.of(first), "en");
        List<GrowthAnalysisReportRow> ar = service.reportRows(List.of(first), "ar");

        assertThat(en.get(0).getGrowthStatusLabel()).isEqualTo("Base year");
        assertThat(ar.get(0).getGrowthStatusLabel()).isEqualTo("سنة الأساس");
        assertThat(en.get(0).getGrowthStatus()).as("the code stays for the filters").isEqualTo("baseline");
    }
}
