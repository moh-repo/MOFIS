package com.dilaw.mohe.report;

import com.dilaw.mohe.report.jasper.JasperFontRegistrar;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Standalone (no Spring context / no DB needed) — compiles+fills templates directly. */

/**
 * Renders every report template with realistic, non-empty DB-driven header titles,
 * footer text, and signatory names/positions, then inspects the filled JasperPrint
 * element tree directly (not the PDF bytes) to confirm each value is actually present
 * in the rendered output.
 *
 * This exists because JasperReports silently drops a text field's ENTIRE value (renders
 * empty, not clipped) when its box height is a fraction too short for the font's real
 * line-height metrics — a font-size/height mismatch does not fail compilation or export,
 * it just produces a blank line. Compile-only smoke tests (see JasperReportSmokeTest)
 * cannot catch this class of bug because they never inspect rendered content.
 */
class HeaderFooterSignatureRenderTest {

    private static JasperReportsContext ctx;

    private static synchronized JasperReportsContext context() throws Exception {
        if (ctx == null) {
            JasperFontRegistrar fontRegistrar = new JasperFontRegistrar();
            Method registerFonts = JasperFontRegistrar.class.getDeclaredMethod("registerFonts");
            registerFonts.setAccessible(true);
            registerFonts.invoke(fontRegistrar);
            ctx = fontRegistrar.getReportsContext();
        }
        return ctx;
    }

    static Stream<String> allTemplates() {
        return Stream.of(
                "academic-performance", "academic-year-report", "annual-report", "bank-transaction",
                "chart-report",
                "budget-analysis", "comparative-statistics", "custom-filter-report", "custom-period-report",
                "data-visualization", "distribution-statistics", "enrollment-statistics", "export-manager",
                "fee-structure", "financial-audit", "financial-data-form", "fiscal-year-report",
                "growth-analysis", "major-distribution", "monthly-report", "payment-auth",
                "payment-summary", "performance-metrics", "predictive-analytics", "program-catalog",
                "quarterly-financial", "quarterly-report", "query-builder", "report-templates",
                "scheduled-reports", "scholarship-tracking", "student-demographics",
                "student-distribution-university", "student-extension-statistics", "student-list",
                "student-status", "trend-analysis", "university-agreements", "university-partnerships",
                "university-performance",
                "student-id-card", "student-registration-form", "enrollment-certificate",
                "student-financial-statement", "financial-data-form-v2", "payment-auth-form"
        );
    }

    /**
     * Templates carrying the shared masthead.
     *
     * <p>quarterly-financial is excluded: it reproduces the ministry's prescribed
     * Access layout (marked OFFICIAL-LAYOUT:SKIP in the template), which has no
     * English title block, no REPORT_TITLE/PRINTED_BY line and exactly two
     * signature positions carrying no names. Its masthead, footer and signatories
     * are asserted by QuarterlyFinancialReportRenderTest instead.
     *
     * <p>financial-data-form-v2 is excluded for the same reason: it reproduces
     * the quarterly financial assistance statement (also OFFICIAL-LAYOUT:SKIP),
     * which prints one language's masthead at a time and no footer note line of
     * its own. FinancialDataFormV2RenderTest asserts its head, notes, columns
     * and five signatories.
     *
     * <p>payment-auth-form is excluded on the same grounds: it is نموذج رقم ١١
     * حسابات, whose head is prescribed — one language's three title lines beside
     * the form serial, no REPORT_TITLE line and no signature block, because the
     * form carries its own signature boxes in the body. Its masthead and footer
     * are asserted by PaymentAuthFormRenderTest. It stays in {@link #allTemplates()}
     * so the band-width rule below still covers it.
     */
    static Stream<String> sharedMastheadTemplates() {
        return allTemplates().filter(t -> !"quarterly-financial".equals(t)
                && !"financial-data-form-v2".equals(t)
                && !"payment-auth-form".equals(t));
    }

    /**
     * The official masthead prints both languages at once, so the contract is the
     * bilingual TITLE_0n_AR / TITLE_0n_EN pairs plus REPORT_TITLE — not the
     * single-language TITLE_03..TITLE_05 slots the old per-template headers used.
     */
    private static final String[] HEADER_KEYS = {
            "TITLE_01_AR", "TITLE_02_AR", "TITLE_03_AR",
            "TITLE_01_EN", "TITLE_02_EN", "TITLE_03_EN",
            "REPORT_TITLE", "FOOTER_TEXT", "PRINTED_BY"
    };

    @ParameterizedTest(name = "template={0}")
    @MethodSource("sharedMastheadTemplates")
    @DisplayName("Bilingual masthead, footer, and all 5 signatories actually render (not silently dropped)")
    void headerFooterSignatures_actuallyRender(String templateName) throws Exception {
        JasperReportsContext context = context();
        String path = "/reports/" + templateName + ".jrxml";
        JasperReport compiled;
        try (var is = HeaderFooterSignatureRenderTest.class.getResourceAsStream(path)) {
            compiled = JasperCompileManager.getInstance(context).compile(is);
        }

        SoftAssertions softly = new SoftAssertions();
        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> params = fullParams(lang);
            JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(Collections.emptyList());
            JasperPrint print = JasperFillManager.getInstance(context).fillReport(compiled, params, ds);

            String allText = extractAllText(print);

            for (String key : HEADER_KEYS) {
                softly.assertThat(allText)
                        .as("template=%s lang=%s: %s must render", templateName, lang, key)
                        .contains((String) params.get(key));
            }
            for (int i = 1; i <= 5; i++) {
                softly.assertThat(allText)
                        .as("template=%s lang=%s: SIG_NAME_%d must render (SIG_COUNT=5)", templateName, lang, i)
                        .contains((String) params.get("SIG_NAME_" + i));
                softly.assertThat(allText)
                        .as("template=%s lang=%s: SIG_POS_%d must render (SIG_COUNT=5)", templateName, lang, i)
                        .contains((String) params.get("SIG_POS_" + i));
            }
            dump(templateName, lang, print, context);
        }
        softly.assertAll();
    }

    /** Set -DrenderTest.dumpDir=... to write the rendered PDFs for visual review. */
    private static void dump(String template, String lang, JasperPrint print, JasperReportsContext context) {
        String dir = System.getProperty("renderTest.dumpDir");
        if (dir == null || dir.isBlank()) {
            return;
        }
        try {
            byte[] pdf = net.sf.jasperreports.engine.JasperExportManager.getInstance(context)
                    .exportReportToPdf(print);
            java.nio.file.Path out = java.nio.file.Path.of(dir, template + "-" + lang + ".pdf");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.write(out, pdf);
        } catch (Exception ignored) {
            // dump is best-effort diagnostics only
        }
    }

    /**
     * Header/footer geometry must follow the page width. A portrait-width (555pt)
     * footer on a landscape (802pt) page squeezes the footer text and page
     * numbers into the left two-thirds of the sheet — which is exactly how the
     * pre-redesign landscape templates were broken. Asserted on the design bands
     * rather than the filled print because print coordinates include the page
     * margin and mix in every other band's elements.
     */
    @ParameterizedTest(name = "template={0}")
    @MethodSource("allTemplates")
    @DisplayName("Masthead and footer bands span the full column width (portrait and landscape)")
    void officialBandsSpanColumnWidth(String templateName) throws Exception {
        JasperReport compiled;
        try (var is = HeaderFooterSignatureRenderTest.class.getResourceAsStream(
                "/reports/" + templateName + ".jrxml")) {
            compiled = JasperCompileManager.getInstance(context()).compile(is);
        }
        int columnWidth = compiled.getColumnWidth();

        SoftAssertions softly = new SoftAssertions();
        record Band(String name, net.sf.jasperreports.engine.JRBand band) {}
        for (Band b : new Band[]{
                new Band("pageHeader", compiled.getPageHeader()),
                new Band("pageFooter", compiled.getPageFooter()),
                new Band("lastPageFooter", compiled.getLastPageFooter())}) {
            if (b.band() == null) {
                // Only the page header and footer are mandatory. A template may
                // legitimately have no lastPageFooter — quarterly-financial puts
                // its signatories in the page footer, as the ministry's Access
                // report does, so they repeat on every page.
                softly.assertThat(b.name())
                        .as("template=%s: %s band must exist", templateName, b.name())
                        .isEqualTo("lastPageFooter");
                continue;
            }
            int rightmost = 0;
            for (var el : b.band().getElements()) {
                rightmost = Math.max(rightmost, el.getX() + el.getWidth());
            }
            softly.assertThat(rightmost)
                    .as("template=%s: %s must span the %dpt column, not a narrower hardcoded width",
                            templateName, b.name(), columnWidth)
                    .isEqualTo(columnWidth);
        }
        softly.assertAll();
    }

    private static String extractAllText(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        print.getPages().forEach(page -> collect(page.getElements(), sb));
        return sb.toString();
    }

    private static void collect(java.util.List<JRPrintElement> elements, StringBuilder sb) {
        for (JRPrintElement el : elements) {
            if (el instanceof JRPrintText text) {
                sb.append(text.getFullText()).append('\n');
            } else if (el instanceof net.sf.jasperreports.engine.JRPrintFrame frame) {
                collect(frame.getElements(), sb);
            }
        }
    }

    private static Map<String, Object> fullParams(String lang) {
        boolean rtl = !"en".equalsIgnoreCase(lang);
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        p.put("LANG", rtl ? "ar" : "en");
        p.put("REPORT_DATE", "2026-07-21");
        p.put("TITLE_01", rtl ? "وزارة المالية - عنوان تجريبي طويل نسبيا" : "Ministry of Finance Sample Title");
        p.put("TITLE_02", rtl ? "التقرير التجريبي الثاني" : "Sample Secondary Title");
        p.put("TITLE_03", rtl ? "عنوان فرعي ثالث" : "Sample Tertiary Title");
        p.put("TITLE_04", rtl ? "عنوان رابع" : "Sample Fourth Title");
        p.put("TITLE_05", rtl ? "عنوان خامس" : "Sample Fifth Title");
        // Bilingual masthead: both stacks print regardless of the report language.
        p.put("TITLE_01_AR", "الجمهورية اليمنية");
        p.put("TITLE_02_AR", "وزارة المالية");
        p.put("TITLE_03_AR", "الإدارة العامة للبعثات والعلاقات الثقافية");
        p.put("TITLE_01_EN", "Republic of Yemen");
        p.put("TITLE_02_EN", "Ministry of Finance");
        p.put("TITLE_03_EN", "General Department of Scholarships");
        p.put("REPORT_TITLE", rtl ? "تقرير رسمي تجريبي" : "Sample Official Report");
        p.put("PRINTED_BY", "qa.officer");
        p.put("FOOTER_TEXT", rtl
                ? "هذا التقرير سري ومخصص للاستخدام الرسمي فقط ويمنع التوزيع دون إذن رسمي مسبق"
                : "This report is confidential, for official use only, distribution requires prior authorization");
        p.put("SIG_COUNT", 5);
        String[] namesAr = {"أحمد محمد العتيبي", "سارة خالد القحطاني", "فهد عبدالله الشمري", "منى سعيد الدوسري", "خالد ناصر المطيري"};
        String[] posAr = {"الوكيل المساعد للشؤون المالية", "مدير إدارة المنح الدراسية", "المراقب المالي", "رئيس قسم المتابعة", "المدير العام"};
        String[] namesEn = {"Ahmed M. Al-Otaibi", "Sarah K. Al-Qahtani", "Fahad A. Al-Shammari", "Mona S. Al-Dosari", "Khalid N. Al-Mutairi"};
        String[] posEn = {"Assistant Undersecretary for Finance", "Director of Scholarships", "Financial Controller", "Head of Follow-up Section", "Director General"};
        for (int i = 1; i <= 5; i++) {
            p.put("SIG_NAME_" + i, rtl ? namesAr[i - 1] : namesEn[i - 1]);
            p.put("SIG_POS_" + i, rtl ? posAr[i - 1] : posEn[i - 1]);
        }
        p.put("COL_ROW_NO", rtl ? "م" : "#");
        p.put("COL_STUDENT_NAME", rtl ? "اسم الطالب" : "Student Name");
        p.put("COL_DEGREE", rtl ? "الدرجة العلمية" : "Degree");
        p.put("COL_MAJOR", rtl ? "التخصص" : "Major");
        p.put("COL_COUNTRY", rtl ? "البلد" : "Country");
        p.put("COL_STATUS", rtl ? "الحالة الدراسية" : "Status");
        p.put("COL_AMOUNT", rtl ? "المبلغ" : "Amount");
        p.put("COL_COUNT", rtl ? "العدد" : "Count");
        p.put("COL_CATEGORY", rtl ? "التصنيف" : "Category");
        p.put("LBL_TOTAL_STUDENTS", rtl ? "إجمالي الطلاب" : "Total Students");
        p.put("LBL_TOTAL_RECORDS", rtl ? "إجمالي السجلات" : "Total Records");
        p.put("LBL_GENERATED", rtl ? "تاريخ الإصدار" : "Generated");
        p.put("LBL_PAGE", rtl ? "صفحة" : "Page");
        p.put("LBL_OF", rtl ? "من" : "of");
        p.put("LBL_SIGNATORY", rtl ? "المفوض بالتوقيع" : "Authorized Signatory");
        return p;
    }
}
