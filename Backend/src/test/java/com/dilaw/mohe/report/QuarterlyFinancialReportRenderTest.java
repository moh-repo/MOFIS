package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.QuarterlyFinancialReportDto;
import com.dilaw.mohe.dto.QuarterlyFinancialReportDto.EntityFinancialSummary;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.QuarterlyFinancialReportService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Renders the quarterly financial report from live database rows.
 *
 * <p>Country scope is stubbed: it resolves a UMS principal from the JWT, which
 * a test cannot supply.
 */
@Tag("integration")
@SpringBootTest
class QuarterlyFinancialReportRenderTest {

    /** The quarter that actually carries finance rows. */
    private static final int YEAR = 2024;
    private static final int QUARTER = 1;

    @Autowired private QuarterlyFinancialReportService service;
    @Autowired private JasperReportService jasperReportService;
    @Autowired private ReportParamBuilder reportParamBuilder;
    @Autowired private com.dilaw.mohe.report.jasper.JasperFontRegistrar fontRegistrar;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        // Ministry scope: sees every country, so the report is not narrowed.
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    @Test
    @DisplayName("report rows and totals come from the database")
    void report_hasDataFromDatabase() {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, QUARTER, YEAR);

        assertThat(dto.getEntities())
                .as("the quarter is expected to have agency rows")
                .isNotEmpty();

        // The report used to render correct agency names over entirely zero
        // amounts, and a totals-match assertion passes trivially when both
        // sides are zero. Require real figures.
        assertThat(dto.getTotalReceivedAmount())
                .as("received total must reflect the quarter's finance rows, not zero")
                .isGreaterThan(0L);
        assertThat(dto.getTotalReceivedCount())
                .as("received count must reflect the quarter's finance rows, not zero")
                .isGreaterThan(0);

        long summedReceived = dto.getEntities().stream()
                .map(EntityFinancialSummary::getReceivedAmount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        assertThat(dto.getTotalReceivedAmount())
                .as("the summary total must agree with the rows it summarises")
                .isEqualTo(summedReceived);

        // Received splits into disbursed + remaining, so the two must reconcile.
        assertThat(dto.getTotalDisbursedAmount() + dto.getTotalRemainingAmount())
                .as("disbursed + remaining must account for everything received")
                .isEqualTo(dto.getTotalReceivedAmount());
        assertThat(dto.getTotalDisbursedCount() + dto.getTotalRemainingCount())
                .as("the counts must reconcile the same way")
                .isEqualTo(dto.getTotalReceivedCount());

        // Every row must reconcile too, not just the totals.
        assertThat(dto.getEntities()).allSatisfy(e ->
                assertThat(e.getDisbursedAmount() + e.getRemainingAmount())
                        .as("row '%s' must reconcile", e.getEntityName())
                        .isEqualTo(e.getReceivedAmount()));
    }

    @Test
    @DisplayName("entities are universities, ministries and agencies, not just agency types")
    void entities_useTheTypeFallback() {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, QUARTER, YEAR);

        // TYPE_ALL_IS resolves university -> ministry -> agency. Grouping on the
        // agency type alone collapsed the whole report onto three rows, where the
        // ministry's own report lists roughly two dozen entities.
        assertThat(dto.getEntities())
                .as("the type fallback must expand past the handful of agency types")
                .hasSizeGreaterThan(5);

        assertThat(dto.getEntities())
                .as("no entity may be blank — the fallback must always yield a name")
                .allSatisfy(e -> assertThat(e.getEntityName()).isNotBlank());
    }

    /**
     * The quarter the report falls back to when the requested one is empty. Only
     * 2024 Q1 carries a meaningful number of finance rows, so any other quarter
     * exercises the fallback.
     */
    private static final int EMPTY_QUARTER = 2;

    @Test
    @DisplayName("an empty quarter reports the period the figures actually came from")
    void emptyQuarter_reportsSubstitutedPeriod() {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, EMPTY_QUARTER, YEAR);

        // The report used to keep the requested quarter while silently serving a
        // different quarter's money, so a Q4 2026 request rendered older figures
        // under the Q4 2026 heading.
        assertThat(dto.getRequestedQuarter()).isEqualTo(EMPTY_QUARTER);
        assertThat(dto.getRequestedYear()).isEqualTo(YEAR);

        if (dto.isPeriodSubstituted()) {
            assertThat(dto.getQuarter() + dto.getYear() * 10)
                    .as("a substituted period must not equal the requested one")
                    .isNotEqualTo(EMPTY_QUARTER + YEAR * 10);
        } else {
            assertThat(dto.getQuarter()).isEqualTo(EMPTY_QUARTER);
            assertThat(dto.getYear()).isEqualTo(YEAR);
        }
        assertThat(dto.getEntityCount()).isEqualTo(dto.getEntities().size());
    }

    @Test
    @DisplayName("a period that holds data is reported unchanged")
    void populatedQuarter_reportsRequestedPeriod() {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, QUARTER, YEAR);

        assertThat(dto.isPeriodSubstituted()).isFalse();
        assertThat(dto.getQuarter()).isEqualTo(QUARTER);
        assertThat(dto.getYear()).isEqualTo(YEAR);
    }

    @Test
    @DisplayName("each agency appears once")
    void report_hasNoDuplicateAgencyRows() {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, QUARTER, YEAR);
        List<String> names = dto.getEntities().stream()
                .map(EntityFinancialSummary::getEntityName).toList();

        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("quarterly-financial renders with live data (AR + EN)")
    void renders_bothLanguages() {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, QUARTER, YEAR);
        List<EntityFinancialSummary> entities = dto.getEntities() != null
                ? dto.getEntities() : Collections.emptyList();

        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> params = reportParamBuilder.build("QUARTERLY_FINANCIAL", lang);
            applySettingsSeededByV020(params, lang);
            params.put("FILTER_YEAR", String.valueOf(YEAR));
            params.put("FILTER_QUARTER", String.valueOf(QUARTER));
            params.put("QUARTER_NAME", "Q" + QUARTER);
            params.put("PERIOD_LABEL", ("en".equals(lang) ? "Q" + QUARTER : "الربع الأول") + " " + YEAR);
            params.put("TOTAL_ENTITIES", entities.size());
            params.put("TOTAL_RECEIVED_AMOUNT", dto.getTotalReceivedAmount());
            params.put("TOTAL_RECEIVED_COUNT", dto.getTotalReceivedCount());
            params.put("TOTAL_DISBURSED_AMOUNT", dto.getTotalDisbursedAmount());
            params.put("TOTAL_DISBURSED_COUNT", dto.getTotalDisbursedCount());
            params.put("TOTAL_REMAINING_AMOUNT", dto.getTotalRemainingAmount());
            params.put("TOTAL_REMAINING_COUNT", dto.getTotalRemainingCount());
            params.put("DISBURSEMENT_RATIO", dto.getDisbursementRatio());

            byte[] pdf = jasperReportService.generatePdf("quarterly-financial", params, entities);

            assertThat(pdf).isNotNull();
            assertThat(new String(pdf, 0, Math.min(4, pdf.length))).isEqualTo("%PDF");
            dump("quarterly-financial", lang, pdf);
        }
    }

    /**
     * The footer note and the two signatories V020 seeds.
     *
     * <p>Applied in the test rather than read from the database so the layout is
     * verified without depending on whether the script has been run yet, and
     * without this test writing to a shared database.
     */
    private static void applySettingsSeededByV020(Map<String, Object> params, String lang) {
        boolean ar = !"en".equals(lang);
        params.put("FOOTER_TEXT", ar
                ? "تقرير داخلي لأغراض المتابعة المالية، غير صالح للصرف ما لم يكن مختوماً ومعتمداً."
                : "Internal report for financial monitoring; not valid for disbursement unless stamped and approved.");
        params.put("SIG_COUNT", 2);
        // Signatory 1 is the right-hand block on an RTL page, which the
        // ministry's report gives to the financial advisor.
        params.put("SIG_POS_1", ar ? "المستشار المالي" : "Financial Advisor");
        params.put("SIG_POS_2", ar ? "المستشار الثقافي" : "Cultural Advisor");
    }

    @Test
    @DisplayName("footer note and signatories from settings actually render")
    void footerAndSignatories_render() throws Exception {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, QUARTER, YEAR);

        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> params = reportParamBuilder.build("QUARTERLY_FINANCIAL", lang);
            applySettingsSeededByV020(params, lang);

            String text = renderToText(params, dto.getEntities());

            assertThat(text)
                    .as("lang=%s: the settings footer note must reach the page foot", lang)
                    .contains((String) params.get("FOOTER_TEXT"));
            assertThat(text)
                    .as("lang=%s: both signatories must render", lang)
                    .contains((String) params.get("SIG_POS_1"))
                    .contains((String) params.get("SIG_POS_2"));

            // This template opts out of the shared masthead test, so its own
            // header content is asserted here instead: the three title lines from
            // REPORT_TYPE_SETTINGS, plus the date and page labels.
            for (String key : new String[]{"TITLE_01_AR", "TITLE_02_AR", "TITLE_03_AR"}) {
                Object v = params.get(key);
                if (v instanceof String title && !title.isBlank()) {
                    assertThat(text)
                            .as("lang=%s: %s must render in the masthead", lang, key)
                            .contains(title);
                }
            }
            assertThat(text)
                    .as("lang=%s: the issue date must render", lang)
                    .contains((String) params.get("REPORT_DATE"));
        }
    }

    @Test
    @DisplayName("the grouped header and summary boxes render with their figures")
    void groupedHeaderAndSummary_render() throws Exception {
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(null, QUARTER, YEAR);
        Map<String, Object> params = reportParamBuilder.build("QUARTERLY_FINANCIAL", "en");
        applySettingsSeededByV020(params, "en");
        params.put("PERIOD_LABEL", "Q" + QUARTER + " " + YEAR);
        params.put("TOTAL_ENTITIES", dto.getEntityCount());
        params.put("TOTAL_RECEIVED_AMOUNT", dto.getTotalReceivedAmount());
        params.put("TOTAL_RECEIVED_COUNT", dto.getTotalReceivedCount());
        params.put("TOTAL_DISBURSED_AMOUNT", dto.getTotalDisbursedAmount());
        params.put("TOTAL_DISBURSED_COUNT", dto.getTotalDisbursedCount());
        params.put("TOTAL_REMAINING_AMOUNT", dto.getTotalRemainingAmount());
        params.put("TOTAL_REMAINING_COUNT", dto.getTotalRemainingCount());
        params.put("DISBURSEMENT_RATIO", dto.getDisbursementRatio());

        String text = renderToText(params, dto.getEntities());

        // Grouped header: section names above shared Amount / Count sub-columns.
        assertThat(text).contains("Received", "Disbursed", "Remaining", "Amount", "Count", "Entity");
        // Summary box labels and a figure that must agree with the data.
        assertThat(text).contains("Summary", "Amount Received (USD)", "Disbursement Ratio");
        assertThat(text)
                .as("the summary must show the received total, not a placeholder")
                .contains(String.format("%,d", dto.getTotalReceivedAmount()));

        // A quarterly summary has to name its quarter: the period used to be
        // computed by the controller and then dropped by the template.
        assertThat(text)
                .as("the report must state the period it covers")
                .contains("Q" + QUARTER + " " + YEAR);
        // Figures the aggregated table itself cannot show.
        assertThat(text)
                .as("the summary must say how many entities it aggregated")
                .contains("Entities");
        assertThat(text)
                .as("amounts must carry their share of the received total")
                .contains("%)");
    }

    @Test
    @DisplayName("a substituted period is disclosed on the document")
    void substitutedPeriod_isDisclosedOnThePage() throws Exception {
        Map<String, Object> params = reportParamBuilder.build("QUARTERLY_FINANCIAL", "en");
        applySettingsSeededByV020(params, "en");
        params.put("PERIOD_LABEL", "Q1 2024");
        params.put("PERIOD_NOTE", "No financial transactions in Q4 2026; "
                + "the figures below are for the most recent quarter holding data.");

        String text = renderToText(params, List.of());

        assertThat(text)
                .as("the reader must be told the figures are not from the quarter requested")
                .contains("No financial transactions in Q4 2026");
    }

    private String renderToText(Map<String, Object> params, List<EntityFinancialSummary> entities) throws Exception {
        net.sf.jasperreports.engine.JasperReportsContext ctx = fontRegistrar.getReportsContext();
        net.sf.jasperreports.engine.JasperReport compiled;
        try (var is = getClass().getResourceAsStream("/reports/quarterly-financial.jrxml")) {
            compiled = net.sf.jasperreports.engine.JasperCompileManager.getInstance(ctx).compile(is);
        }
        net.sf.jasperreports.engine.JasperPrint print =
                net.sf.jasperreports.engine.JasperFillManager.getInstance(ctx).fillReport(compiled, params,
                        new net.sf.jasperreports.engine.data.JRBeanCollectionDataSource(entities));

        StringBuilder sb = new StringBuilder();
        for (var page : print.getPages()) {
            collect(page.getElements(), sb);
        }
        return sb.toString();
    }

    private static void collect(List<net.sf.jasperreports.engine.JRPrintElement> elements, StringBuilder sb) {
        for (var el : elements) {
            if (el instanceof net.sf.jasperreports.engine.JRPrintText t) {
                sb.append(t.getFullText()).append('\n');
            } else if (el instanceof net.sf.jasperreports.engine.JRPrintFrame f) {
                collect(f.getElements(), sb);
            }
        }
    }

    /** Set -DrenderTest.dumpDir=... to write the PDFs for visual review. */
    private static void dump(String template, String lang, byte[] pdf) {
        String dir = System.getProperty("renderTest.dumpDir");
        if (dir == null || dir.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path out = java.nio.file.Path.of(dir, template + "-" + lang + ".pdf");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.write(out, pdf);
        } catch (Exception ignored) {
            // diagnostics only
        }
    }
}
