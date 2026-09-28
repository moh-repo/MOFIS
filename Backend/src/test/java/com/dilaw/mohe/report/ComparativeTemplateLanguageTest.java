package com.dilaw.mohe.report;

import com.dilaw.mohe.model.ComparativeStatistics;
import com.dilaw.mohe.report.jasper.JasperFontRegistrar;
import net.sf.jasperreports.engine.JasperReportsContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live QA #63 (L1, with M13's "report values"): the English Comparative Statistics
 * printed the dimensions (countries, degrees) in Arabic. The query already returns an
 * English name beside the Arabic one; the template now prints it when the report is
 * not right-to-left.
 */
class ComparativeTemplateLanguageTest {

    private static JasperReportsContext context() throws Exception {
        JasperFontRegistrar registrar = new JasperFontRegistrar();
        Method register = JasperFontRegistrar.class.getDeclaredMethod("registerFonts");
        register.setAccessible(true);
        register.invoke(registrar);
        return registrar.getReportsContext();
    }

    private static ComparativeStatistics row() {
        ComparativeStatistics r = new ComparativeStatistics();
        r.setComparisonType("country");
        r.setDimensionName("مصر");
        r.setDimensionNameEn("Egypt");
        r.setCurrentPeriod("2025");
        r.setCurrentPeriodValue(12);
        return r;
    }

    private static String render(JasperReportsContext ctx, boolean rtl) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("IS_RTL", rtl);
        params.put("LANG", rtl ? "ar" : "en");
        return ReportRenderSupport.renderToText(ctx, "comparative-statistics", params, List.of(row()));
    }

    @Test
    @DisplayName("the English report prints the English dimension, the Arabic one the Arabic")
    void dimensionFollowsTheLanguage() throws Exception {
        JasperReportsContext ctx = context();
        String english = render(ctx, false);
        assertThat(english).contains("Egypt").doesNotContain("مصر");
        String arabic = render(ctx, true);
        assertThat(arabic).contains("مصر");
    }
}
