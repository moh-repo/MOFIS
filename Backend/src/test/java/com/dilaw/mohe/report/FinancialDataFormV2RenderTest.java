package com.dilaw.mohe.report;

import com.dilaw.mohe.model.StudentFinancialDataFormV2;
import com.dilaw.mohe.report.jasper.JasperFontRegistrar;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the quarterly financial assistance statement (Financial Data Form V2)
 * from sample rows and inspects the filled page — no Spring context, no
 * database.
 *
 * <p>The template reproduces the issuing department's sheet, so what is
 * asserted is the sheet: the masthead and title block, the four statutory
 * notes, all fourteen column heads, every row's values, the grand total, five
 * signatories, and the per-entity colour band on the "أول مصرف" cell.
 */
class FinancialDataFormV2RenderTest {

    private static final String TEMPLATE = "financial-data-form-v2";

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

    @Test
    @DisplayName("Arabic statement: head, notes, column heads, rows, total and signatories all render")
    void arabicStatement_rendersEverySection() throws Exception {
        Map<String, Object> params = params(true);
        List<StudentFinancialDataFormV2> rows = sampleRows();

        String text = extractAllText(fill(params, rows));

        // Masthead and title block
        assertThat(text).contains("الجمهورية اليمنية", "وزارة المالية",
                "الإدارة العامة للبعثات - إدارة مالية البعثات", "كشوفات المساعدة المالية الربعية");
        assertThat(text).contains("الربع: الثالث", "العام 2025م", "2026-08-25",
                "الجهة: وزارة المالية", "الدولة: مصر");

        // The four statutory notes
        assertThat(text).contains("ملاحظات هامة:");
        for (int i = 1; i <= 4; i++) {
            assertThat(text).as("note %d", i).contains((String) params.get("LBL_NOTE_" + i));
        }

        // Fourteen column heads
        assertThat(text).contains("م", "اسم الطالب", "الجهة", "الدولة", "عام الإيفاد", "الدرجة", "التخصص",
                "رقم القرار", "مدة الدراسة", "المستوى الحالي", "أول مصرف", "المبلغ", "توقيع الطالب", "ملاحظات");

        // Every row's values
        for (StudentFinancialDataFormV2 r : rows) {
            assertThat(text).contains(r.getStudentName(), r.getStudentMajorName(),
                    String.valueOf(r.getStudentDelegateDecisionNo()),
                    String.valueOf(r.getStudentFirstReceiptOfDue()),
                    "$" + String.format("%,d", r.getStudentAmount()));
        }
        assertThat(text).as("the level prints as the number the sheet shows").contains("7");
        assertThat(text).as("a payment-stop flag goes in the signature cell").contains("يوقف الصرف - طلب تأكيد");
        assertThat(text).as("notes column").contains("تمديد بموجب قرار (2025214) حتى 20254");

        // Grand total and count
        long total = rows.stream().mapToLong(r -> r.getStudentAmount()).sum();
        assertThat(text).contains("$" + String.format("%,d", total));
        assertThat(text).contains("عدد الطلاب: " + rows.size());

        // Five signatories, name over position
        for (int i = 1; i <= 5; i++) {
            assertThat(text).contains((String) params.get("SIG_NAME_" + i));
            assertThat(text).contains((String) params.get("SIG_POS_" + i));
        }
        assertThat(text).contains("صفحة 1");
    }

    @Test
    @DisplayName("English statement renders the working translation")
    void englishStatement_renders() throws Exception {
        Map<String, Object> params = params(false);
        String text = extractAllText(fill(params, sampleRows()));

        assertThat(text).contains("Republic of Yemen", "Quarterly Financial Assistance Statements",
                "Quarter: Third", "Year 2025", "Important notes:", "Student Name", "First Payment",
                "Student Signature", "Grand Total");
        assertThat(text).contains((String) params.get("LBL_NOTE_1"));
    }

    @Test
    @DisplayName("the first-payment cell is coloured per sponsoring entity")
    void firstPaymentCell_colourFollowsEntity() throws Exception {
        JasperPrint print = fill(params(true), sampleRows());

        // The sample rows use one first-payment value per entity, so the cell
        // is identified by its text.
        Set<Color> entityA = coloursOfCells(print, "20173");
        Set<Color> entityB = coloursOfCells(print, "20233");
        Set<Color> entityC = coloursOfCells(print, "20213");

        assertThat(entityA).as("one colour within an entity").hasSize(1);
        assertThat(entityB).as("one colour within an entity").hasSize(1);
        assertThat(entityC).as("one colour within an entity").hasSize(1);
        assertThat(entityA).as("entities differ in colour").doesNotContainAnyElementsOf(entityB);
        assertThat(entityB).as("entities differ in colour").doesNotContainAnyElementsOf(entityC);
        assertThat(entityA).as("the colour band is not the white default").doesNotContain(Color.WHITE);
    }

    @Test
    @DisplayName("an empty quarter still prints the head, notes and a zero total")
    void emptyData_rendersHeadAndZeroTotal() throws Exception {
        Map<String, Object> params = params(true);
        String text = extractAllText(fill(params, List.of()));

        assertThat(text).contains("كشوفات المساعدة المالية الربعية", "ملاحظات هامة:", "$0", "عدد الطلاب: 0");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static JasperPrint fill(Map<String, Object> params, List<StudentFinancialDataFormV2> rows) throws Exception {
        JasperReportsContext context = context();
        JasperReport compiled;
        try (var is = FinancialDataFormV2RenderTest.class.getResourceAsStream("/reports/" + TEMPLATE + ".jrxml")) {
            compiled = JasperCompileManager.getInstance(context).compile(is);
        }
        JasperPrint print = JasperFillManager.getInstance(context)
                .fillReport(compiled, params, new JRBeanCollectionDataSource(rows));
        dump(print, (Boolean) params.get("IS_RTL") ? "ar" : "en", context);
        return print;
    }

    /**
     * The labels come from the V077 seed of LU_REPORT_LABEL (the same rows the
     * builder reads), so the test cannot drift from what production prints; the
     * head lines are the REPORT_TYPE_SETTINGS row V057 seeds for this statement.
     */
    private static Map<String, Object> params(boolean rtl) {
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        p.put("LANG", rtl ? "ar" : "en");
        p.put("REPORT_DATE", "2026-08-25");
        p.put("TITLE_01", "");
        p.put("TITLE_02", "");
        p.put("TITLE_03", "");
        p.put("FOOTER_TEXT", "");
        p.put("PRINTED_BY", "");
        p.put("TITLE_01_AR", "الجمهورية اليمنية");
        p.put("TITLE_01_EN", "Republic of Yemen");
        p.put("TITLE_02_AR", "وزارة المالية");
        p.put("TITLE_02_EN", "Ministry of Finance");
        p.put("TITLE_03_AR", "الإدارة العامة للبعثات - إدارة مالية البعثات");
        p.put("TITLE_03_EN", "General Department of Scholarships - Scholarship Finance Department");
        p.put("REPORT_TITLE", rtl ? "كشوفات المساعدة المالية الربعية" : "Quarterly Financial Assistance Statements");
        SeededReportLabels.putAll(p, "FINANCIAL_DATA_FORM_V2", rtl);
        p.put("LBL_GENERATED", rtl ? "تاريخ الإصدار" : "Generated");
        p.put("LBL_PAGE", rtl ? "صفحة" : "Page");
        p.put("FILTER_YEAR", "2025");
        p.put("FILTER_QUARTER", "3");
        p.put("QUARTER_LABEL", rtl ? "الثالث" : "Third");
        p.put("FILTER_COUNTRY", rtl ? "مصر" : "Egypt");
        p.put("ISSUING_ENTITY", (String) p.get(rtl ? "TITLE_02_AR" : "TITLE_02_EN"));
        p.put("SIG_COUNT", 5);
        String[] namesAr = {"أ. سمير قاسم", "د. عبدالرقيب محسن", "أ. جلال علي", "د. مازن مهدي", "أ.د. أمين نعمان"};
        String[] posAr = {"مدير مالية البعثات", "مدير عام البعثات والمنح الدراسية", "مدير عام الشؤون المالية",
                "وكيل الوزارة لقطاع البعثات", "وزير التعليم العالي والبحث العلمي"};
        String[] namesEn = {"S. Qasem", "A. Mohsen", "J. Ali", "M. Mahdi", "A. Noman"};
        String[] posEn = {"Director of Scholarship Finance", "Director General of Scholarships and Grants",
                "Director General of Financial Affairs", "Deputy Minister for the Scholarships Sector",
                "Minister of Higher Education and Scientific Research"};
        for (int i = 1; i <= 5; i++) {
            p.put("SIG_NAME_" + i, rtl ? namesAr[i - 1] : namesEn[i - 1]);
            p.put("SIG_POS_" + i, rtl ? posAr[i - 1] : posEn[i - 1]);
        }
        return p;
    }

    /** Three sponsoring entities, ordered as the repository orders them. */
    private static List<StudentFinancialDataFormV2> sampleRows() {
        List<StudentFinancialDataFormV2> rows = new ArrayList<>();
        rows.add(row(1, "دعاء علي عبده سعيد الجرادي", "د. جامعية", "2017", "جامعية", "طب بشري", 2025082, 7, "6", 20173, 1500,
                "تمديد بموجب قرار (2025214) حتى 20254", null));
        rows.add(row(2, "زيد علي عبدالجبار المهاب", "د. جامعية", "2017", "جامعية", "طب بشري", 2025082, 7, "6", 20173, 1500,
                null, "يوقف الصرف - طلب تأكيد"));
        rows.add(row(3, "رشاد عبدالله احمد يحيى القعافي", "د. عليا", "2023", "ماجستير", "جراحة الفم", 2025338, 3, "2", 20233, 1860,
                null, null));
        rows.add(row(4, "سعيد خالد احمد عبدالله", "وزارة الدفاع", "2021", "جامعية", "كلية حربية", 2021006, 4, "7", 20213, 1500,
                "اخر مصرف مالي", null));
        rows.add(row(5, "محمد قائد عبدالله النقيب", "وزارة الدفاع", "2021", "جامعية", "القوات الجوية", 2021006, 4, "3", 20213, 1500,
                null, null));
        return rows;
    }

    private static StudentFinancialDataFormV2 row(int id, String name, String entity, String decisionYear, String degree,
                                                  String major, int decisionNo, int years, String level, int firstReceipt,
                                                  int amount, String note1, String note2) {
        StudentFinancialDataFormV2 r = new StudentFinancialDataFormV2();
        r.setStudentId(id);
        r.setStudentName(name);
        r.setEntityName(entity);
        r.setStudyCountryInName("مصر");
        r.setStudentDelegationDecisionYear(decisionYear);
        r.setStudentDegreeName(degree);
        r.setStudentMajorName(major);
        r.setStudentDelegateDecisionNo(decisionNo);
        r.setStudentStudyNoOfYears(years);
        r.setCurrentLevelLabel(level);
        r.setStudentFirstReceiptOfDue(firstReceipt);
        r.setStudentAmount(amount);
        r.setStudentNote1(note1);
        r.setStudentNote2(note2);
        r.setStudentFinReceivedYear(2025);
        r.setStudentFinReceivedMonth(3);
        return r;
    }

    private static Set<Color> coloursOfCells(JasperPrint print, String text) {
        Set<Color> colours = new LinkedHashSet<>();
        List<JRPrintText> cells = new ArrayList<>();
        print.getPages().forEach(page -> collectTexts(page.getElements(), cells));
        for (JRPrintText t : cells) {
            if (text.equals(t.getFullText())) {
                colours.add(t.getBackcolor());
            }
        }
        assertThat(colours).as("cells printing %s", text).isNotEmpty();
        return colours;
    }

    private static void collectTexts(List<JRPrintElement> elements, List<JRPrintText> out) {
        for (JRPrintElement el : elements) {
            if (el instanceof JRPrintText t) {
                out.add(t);
            } else if (el instanceof JRPrintFrame f) {
                collectTexts(f.getElements(), out);
            }
        }
    }

    private static String extractAllText(JasperPrint print) {
        List<JRPrintText> cells = new ArrayList<>();
        print.getPages().forEach(page -> collectTexts(page.getElements(), cells));
        StringBuilder sb = new StringBuilder();
        for (JRPrintText t : cells) {
            sb.append(t.getFullText()).append('\n');
        }
        return sb.toString();
    }

    /** Set -DrenderTest.dumpDir=... to write the PDFs for visual review. */
    private static void dump(JasperPrint print, String lang, JasperReportsContext context) {
        String dir = System.getProperty("renderTest.dumpDir");
        if (dir == null || dir.isBlank()) {
            return;
        }
        try {
            byte[] pdf = net.sf.jasperreports.engine.JasperExportManager.getInstance(context).exportReportToPdf(print);
            java.nio.file.Path out = java.nio.file.Path.of(dir, TEMPLATE + "-" + lang + ".pdf");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.write(out, pdf);
        } catch (Exception ignored) {
            // diagnostics only
        }
    }
}
