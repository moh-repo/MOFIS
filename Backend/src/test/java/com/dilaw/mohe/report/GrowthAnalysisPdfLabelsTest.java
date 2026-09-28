package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.GrowthAnalysisReportRow;
import com.dilaw.mohe.model.GrowthAnalysis;
import com.dilaw.mohe.model.LuTrendClass;
import com.dilaw.mohe.report.jasper.JasperFontRegistrar;
import com.dilaw.mohe.repository.LuTrendClassRepository;
import com.dilaw.mohe.service.GrowthAnalysisService;
import net.sf.jasperreports.engine.JasperReportsContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIX-PLAN P5-10 (26 Sep 2026): the Growth Analysis PDF printed the view's codes,
 * "total_students" as the category and "growing" / "stable" as the status. The export
 * names them in the report's language (the status from the GROWTH_STATUS trend classes,
 * the category from the message bundles), and the real template prints the names.
 */
class GrowthAnalysisPdfLabelsTest {

    private static LuTrendClass trendClass(String code, String ar, String en) {
        LuTrendClass c = new LuTrendClass();
        c.setCategory("GROWTH_STATUS");
        c.setCode(code);
        c.setNameAr(ar);
        c.setNameEn(en);
        return c;
    }

    private static GrowthAnalysis row(int year, String status, String rate) {
        GrowthAnalysis g = new GrowthAnalysis();
        g.setGrowthCategory("total_students");
        g.setAnalysisYear(year);
        g.setGrowthStatus(status);
        g.setGrowthRate(rate == null ? null : new BigDecimal(rate));
        return g;
    }

    private static GrowthAnalysisService service() {
        LuTrendClassRepository classes = mock(LuTrendClassRepository.class);
        when(classes.findByCategoryAndIsDeletedFalseOrderBySortOrderAsc("GROWTH_STATUS")).thenReturn(List.of(
                trendClass("GROWTH", "نمو", "Growth"), trendClass("DECLINE", "انخفاض", "Decline"),
                trendClass("STABLE", "مستقر", "Stable")));
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        GrowthAnalysisService service = new GrowthAnalysisService();
        ReflectionTestUtils.setField(service, "trendClasses", classes);
        ReflectionTestUtils.setField(service, "messages", messages);
        return service;
    }

    private static final List<GrowthAnalysis> ROWS = List.of(row(2022, "stable", null), row(2023, "growing", "12.50"));

    @Test
    @DisplayName("the rows are named in the report's language; the codes stay for the filters")
    void rowsAreNamed() {
        List<GrowthAnalysisReportRow> en = service().reportRows(ROWS, "en");
        assertThat(en).extracting(GrowthAnalysisReportRow::getGrowthStatusLabel).containsExactly("Stable", "Growth");
        assertThat(en).extracting(GrowthAnalysisReportRow::getGrowthCategoryLabel).containsOnly("Total students");
        assertThat(en).extracting(GrowthAnalysisReportRow::getGrowthStatus).containsExactly("stable", "growing");

        List<GrowthAnalysisReportRow> ar = service().reportRows(ROWS, "ar");
        assertThat(ar).extracting(GrowthAnalysisReportRow::getGrowthStatusLabel).containsExactly("مستقر", "نمو");
        assertThat(ar).extracting(GrowthAnalysisReportRow::getGrowthCategoryLabel).containsOnly("إجمالي الطلاب");
    }

    @Test
    @DisplayName("an unknown code prints as itself rather than vanishing")
    void unknownCodeStays() {
        GrowthAnalysis odd = row(2024, "surging", "1");
        odd.setGrowthCategory("new_metric");
        GrowthAnalysisReportRow r = service().reportRows(List.of(odd), "en").get(0);
        assertThat(r.getGrowthStatusLabel()).isEqualTo("surging");
        assertThat(r.getGrowthCategoryLabel()).isEqualTo("new_metric");
    }

    @Test
    @DisplayName("the real template prints the names, the rate with %, and — for a first year with no rate")
    void templatePrintsNames() throws Exception {
        JasperFontRegistrar registrar = new JasperFontRegistrar();
        Method register = JasperFontRegistrar.class.getDeclaredMethod("registerFonts");
        register.setAccessible(true);
        register.invoke(registrar);
        JasperReportsContext ctx = registrar.getReportsContext();

        for (String lang : List.of("en", "ar")) {
            Map<String, Object> params = new HashMap<>();
            params.put("IS_RTL", "ar".equals(lang));
            params.put("LANG", lang);
            String text = ReportRenderSupport.renderToText(ctx, "growth-analysis", params, service().reportRows(ROWS, lang));
            assertThat(text).as(lang).doesNotContain("growing").doesNotContain("total_students").contains("12.50 %").contains("—");
            if ("en".equals(lang)) {
                assertThat(text).contains("Growth").contains("Stable").contains("Total students");
            } else {
                assertThat(text).contains("نمو").contains("إجمالي الطلاب");
            }
        }
    }
}
