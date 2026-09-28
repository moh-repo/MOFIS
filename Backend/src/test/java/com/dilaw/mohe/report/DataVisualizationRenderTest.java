package com.dilaw.mohe.report;

import com.dilaw.mohe.model.DataVisualization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The visualization listing prints the distribution, not a description of it.
 *
 * <p>Before V109 its three columns were {@code chart_name}, {@code chart_type}
 * and {@code category}, and all three were constants: the view selected
 * {@code N'Student Distribution'} and {@code N'pie'} on every row, and the
 * category is the key {@code 'status'} or {@code 'country'}. So every row of the
 * PDF read the same two or three words and the figures — {@code data_label} and
 * {@code data_value}, which the view had all along — were never printed.
 *
 * <p>The chart name now comes from LU_REPORT_DIMENSION and names the chart that
 * the row belongs to, and the other two columns are the slice and its count.
 */
class DataVisualizationRenderTest {

    private static DataVisualization slice(String chartAr, String chartEn,
                                           String labelAr, String labelEn, int value) {
        DataVisualization d = new DataVisualization();
        d.setVisualizationId(1L);
        d.setChartName(chartAr);
        d.setChartNameEn(chartEn);
        d.setChartType("pie");
        d.setCategory("status");
        d.setDataLabel(labelAr);
        d.setDataLabelEn(labelEn);
        d.setDataValue(value);
        d.setSortOrder(1);
        return d;
    }

    private static Map<String, Object> params(boolean rtl) {
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        return SeededReportLabels.putAll(p, "DATA_VISUALIZATION", rtl);
    }

    private static final DataVisualization ROW =
            slice("توزيع الطلاب حسب الحالة", "Students by Status", "نشط", "Active", 939);

    @Test
    @DisplayName("the slice and its count reach the page")
    void printsTheDistribution() throws Exception {
        String text = ReportRenderSupport.renderToText("data-visualization", params(true), List.of(ROW));

        assertThat(text)
                .as("the figures are the report; before V109 none of them printed")
                .contains("نشط")
                .contains("939");
    }

    @Test
    @DisplayName("nothing prints the chart type or the category key")
    void printsNoConstants() throws Exception {
        String text = ReportRenderSupport.renderToText("data-visualization", params(true), List.of(ROW));

        // 'pie' selects a rendering and 'status' is a grouping key. Neither is
        // something to show a reader, and both used to occupy a column.
        assertThat(text).doesNotContain("pie");
        assertThat(text).doesNotContain("status");
        assertThat(text)
                .as("the hardcoded English label the Arabic report used to print")
                .doesNotContain("Student Distribution");
    }

    @Test
    @DisplayName("an English render reads the English siblings")
    void englishRenderIsEnglish() throws Exception {
        Map<String, Object> p = params(false);
        p.put("LANG", "en");
        List<?> rows = BilingualRows.forLanguage(List.of(ROW), "en").stream().toList();

        String text = ReportRenderSupport.renderToText("data-visualization", p, rows);

        assertThat(text).contains("Students by Status").contains("Active");
        assertThat(text)
                .as("BilingualRows must not leave the Arabic value behind")
                .doesNotContain("نشط");
    }
}
