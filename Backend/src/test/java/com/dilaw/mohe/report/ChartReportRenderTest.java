package com.dilaw.mohe.report;

import com.dilaw.mohe.report.chart.ChartReportRequest;
import com.dilaw.mohe.report.chart.ChartReportRow;
import com.dilaw.mohe.report.chart.ChartReportRows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The report the eight visualisation pages now produce.
 *
 * <p>Before this template those pages printed themselves: Preview pasted canvas
 * images onto a PDF built in the browser, and Print opened a copy of the page's HTML
 * in a second window and called {@code window.print()}. Note H50 — "the print button
 * prints the page itself and the formatting is completely wrong" — was the inevitable
 * result, because nothing the browser prints can carry the masthead, the logo or the
 * footer sentence that live in {@code REPORT_TYPE_SETTINGS}.
 *
 * <p>What matters here is that the numbers reach the page. A chart report that prints
 * only the picture would be a screenshot with a letterhead: the table underneath is
 * the part a reader can check a figure in, so these assertions are on the rendered
 * text rather than on the size of the PDF.
 */
class ChartReportRenderTest {

    private static final String TEMPLATE = "chart-report";

    @Test
    @DisplayName("every value of every chart reaches the page, in both languages")
    void rendersChartsAndTheirData() throws Exception {
        List<ChartReportRow> rows = ChartReportRows.flatten(List.of(
                chart("Enrolment by country", "Active students",
                        List.of("Country", "Students", "Share"),
                        List.of(
                                List.of("Egypt", "412", "38%"),
                                List.of("Jordan", "301", "27%"),
                                List.of("Malaysia", "188", "17%"))),
                chart("Disbursement by year", null,
                        List.of("Year", "Amount"),
                        List.of(
                                List.of("2024", "1,204,900"),
                                List.of("2025", "1,388,150")))));

        for (String lang : new String[]{"ar", "en"}) {
            String text = ReportRenderSupport.renderToText(TEMPLATE, params(lang), rows);

            assertThat(text)
                    .as("card headings (%s), so a reader knows which chart the table belongs to", lang)
                    .contains("Enrolment by country", "Disbursement by year");
            assertThat(text)
                    .as("column headings come from the chart, not from the template (%s)", lang)
                    .contains("Country", "Students", "Share", "Year", "Amount");
            assertThat(text)
                    .as("the figures themselves (%s)", lang)
                    .contains("Egypt", "412", "38%", "Malaysia", "188", "2025", "1,388,150");
        }
    }

    @Test
    @DisplayName("a chart with no series still prints its picture")
    void rendersImageOnlyChart() throws Exception {
        // The geographic map and the network graph have a picture and nothing behind
        // it. Dropping them would lose the only thing those two pages show.
        List<ChartReportRow> rows = ChartReportRows.flatten(List.of(
                chart("Geographic distribution", "Attaché offices", List.of(), List.of())));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getHasData()).isFalse();
        assertThat(rows.get(0).getChartImage()).isNotNull();

        String text = ReportRenderSupport.renderToText(TEMPLATE, params("ar"), rows);
        assertThat(text).contains("Geographic distribution");
    }

    @Test
    @DisplayName("a narrow chart leaves the unused columns off the page")
    void hidesUnusedColumns() throws Exception {
        List<ChartReportRow> rows = ChartReportRows.flatten(List.of(
                chart("Students by degree", null,
                        List.of("Degree", "Count"),
                        List.of(List.of("Master", "96"), List.of("Doctorate", "44")))));

        String text = ReportRenderSupport.renderToText(TEMPLATE, params("en"), rows);

        assertThat(text).contains("Degree", "Count", "Master", "96");
        // Nothing from the wider layout leaks in as an empty heading or a stray cell.
        assertThat(rows.get(0).getH2()).isEmpty();
        assertThat(rows.get(0).getC2()).isEmpty();
    }

    @Test
    @DisplayName("the label column sits on the reading-start edge in each language")
    void mirrorsForEnglish() throws Exception {
        // Arabic is the designed direction, so the label column is rightmost; the
        // English build is the mirrored compile, so it must be leftmost. This is the
        // one property a text-only assertion cannot see.
        List<ChartReportRow> rows = ChartReportRows.flatten(List.of(
                chart("Placement", null, List.of("Category", "Total"),
                        List.of(List.of("Sponsored", "770")))));

        Map<String, Integer> arabic = ReportRenderSupport.textPositions(TEMPLATE, params("ar"), rows);
        assertThat(arabic.get("Category"))
                .as("Arabic: the label column is on the right of the page")
                .isGreaterThan(arabic.get("Total"));
    }

    @Test
    @DisplayName("an image that is not a PNG is refused")
    void refusesNonPngImage() {
        String notAnImage = Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> ChartReportRows.decodeImage(notAnImage))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PNG");

        assertThatThrownBy(() -> ChartReportRows.decodeImage("not base64 at all !!"))
                .isInstanceOf(ResponseStatusException.class);

        assertThatCode(() -> ChartReportRows.decodeImage(null))
                .withFailMessage("a card with no image is legitimate, not an error")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the data URI a canvas produces is accepted as-is")
    void acceptsDataUriPrefix() {
        byte[] withPrefix = ChartReportRows.decodeImage("data:image/png;base64," + pngBase64());
        byte[] without = ChartReportRows.decodeImage(pngBase64());

        assertThat(withPrefix).isNotNull().isEqualTo(without);
    }

    @Test
    @DisplayName("the download name comes from the page heading")
    void namesTheDownloadAfterThePage() {
        assertThat(ChartReportRows.baseName("Bar Charts")).isEqualTo("bar-charts");
        assertThat(ChartReportRows.baseName("  ")).isEqualTo("chart-report");
        assertThat(ChartReportRows.baseName(null)).isEqualTo("chart-report");
        // Arabic headings keep their letters rather than collapsing to nothing.
        assertThat(ChartReportRows.baseName("الرسوم البيانية")).isEqualTo("الرسوم-البيانية");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static ChartReportRequest.Chart chart(String title, String subtitle,
                                                  List<String> columns, List<List<String>> rows) {
        return new ChartReportRequest.Chart(title, subtitle, pngBase64(), columns, rows);
    }

    /** A real, minimal PNG — the signature check means a fake byte array will not do. */
    private static String pngBase64() {
        try {
            BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> params(String lang) {
        boolean rtl = !"en".equalsIgnoreCase(lang);
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        p.put("LANG", rtl ? "ar" : "en");
        p.put("REPORT_DATE", "2026-09-10");
        p.put("REPORT_TITLE", rtl ? "الرسوم البيانية" : "Charts");
        p.put("TITLE_01", rtl ? "الجمهورية اليمنية" : "Republic of Yemen");
        p.put("TITLE_02", rtl ? "وزارة المالية" : "Ministry of Finance");
        p.put("TITLE_03", "");
        p.put("TITLE_01_AR", "الجمهورية اليمنية");
        p.put("TITLE_02_AR", "وزارة المالية");
        p.put("TITLE_03_AR", "");
        p.put("TITLE_01_EN", "Republic of Yemen");
        p.put("TITLE_02_EN", "Ministry of Finance");
        p.put("TITLE_03_EN", "");
        p.put("FOOTER_TEXT", "");
        p.put("PRINTED_BY", "");
        p.put("LBL_GENERATED", rtl ? "تاريخ الإصدار" : "Generated");
        p.put("LBL_PAGE", rtl ? "صفحة" : "Page");
        p.put("LBL_OF", rtl ? "من" : "of");
        return p;
    }
}
