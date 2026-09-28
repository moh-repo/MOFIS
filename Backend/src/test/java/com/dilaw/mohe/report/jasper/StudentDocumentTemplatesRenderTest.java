package com.dilaw.mohe.report.jasper;

import com.dilaw.mohe.dto.StudentFinancialLineDto;
import com.dilaw.mohe.dto.StudentProfileDocDto;
import com.dilaw.mohe.report.SeededReportLabels;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain (no Spring, no DB) render test for the four official single-student
 * document templates. Unlike the empty-data smoke test, this fills each
 * template with a fully-populated synthetic bean so field-name/bean-property
 * mismatches surface as blank output or fill errors here rather than in
 * production PDFs.
 */
class StudentDocumentTemplatesRenderTest {

    private static JasperReportService service;
    private static JasperFontRegistrar registrar;

    @BeforeAll
    static void setUp() {
        registrar = new JasperFontRegistrar();
        registrar.registerFonts();
        service = new JasperReportService(registrar);
    }

    @Test
    @DisplayName("student-id-card renders with a populated profile bean (AR + EN)")
    void idCard() {
        renderBothLangs("student-id-card", List.of(sampleProfile()));
    }

    @Test
    @DisplayName("student-registration-form renders with a populated profile bean (AR + EN)")
    void registrationForm() {
        renderBothLangs("student-registration-form", List.of(sampleProfile()));
    }

    @Test
    @DisplayName("enrollment-certificate renders with a body paragraph (AR + EN)")
    void enrollmentCertificate() {
        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> p = baseParams(lang);
            p.put("CERT_BODY", "ar".equals(lang)
                    ? "تشهد الوزارة بأن الطالب أحمد محمد مقيد في جامعة القاهرة لنيل درجة البكالوريوس في تخصص الهندسة."
                    : "This is to certify that the student Ahmed Mohammed is enrolled at Cairo University pursuing a Bachelor degree in Engineering.");
            p.put("CERT_REF", "MOHE-1001-2026-07-30");
            byte[] pdf = service.generatePdf("enrollment-certificate", p, List.of(sampleProfile()));
            assertPdf(pdf);
            dump("enrollment-certificate", lang, pdf);
        }
    }

    @Test
    @DisplayName("payment-auth-form renders the statutory voucher for one record (AR + EN)")
    void paymentAuthForm() {
        // The label set is the V077 seed of LU_REPORT_LABEL — the rows the real
        // ReportParamBuilder reads — so an English run genuinely exercises the
        // translated wording; the template's own defaults are blank.
        for (String lang : new String[]{"ar", "en"}) {
            boolean rtl = !"en".equals(lang);
            Map<String, Object> p = paymentAuthHeadParams(lang);
            SeededReportLabels.putAll(p, "PAYMENT_AUTH_FORM", rtl);

            byte[] pdf = service.generatePdf("payment-auth-form", p, List.of(samplePaymentAuth()));
            assertPdf(pdf);
            dump("payment-auth-form", lang, pdf);
        }
    }

    /**
     * The voucher's page head and page foot must come from REPORT_TYPE_SETTINGS
     * (TITLE_01..03 and FOOTER_TEXT), not from wording baked into the template.
     * A head element that silently fails to render — a mis-set printWhenExpression,
     * an element positioned outside its band — is invisible in a smoke test that
     * only checks the PDF is non-empty, so assert on the filled page text.
     */
    @Test
    @DisplayName("payment-auth-form: head titles and footer note render from settings values (AR + EN)")
    void paymentAuthFormHeadAndFootAreDbDriven() throws Exception {
        JasperReportsContext ctx = registrar.getReportsContext();
        JasperReport compiled;
        try (var is = StudentDocumentTemplatesRenderTest.class
                .getResourceAsStream("/reports/payment-auth-form.jrxml")) {
            compiled = JasperCompileManager.getInstance(ctx).compile(is);
        }

        for (String lang : new String[]{"ar", "en"}) {
            boolean rtl = !"en".equals(lang);
            Map<String, Object> p = paymentAuthHeadParams(lang);
            SeededReportLabels.putAll(p, "PAYMENT_AUTH_FORM", rtl);

            JasperPrint print = JasperFillManager.getInstance(ctx)
                    .fillReport(compiled, p, new JRBeanCollectionDataSource(List.of(samplePaymentAuth())));
            String text = extractAllText(print);

            for (String key : new String[]{"TITLE_01", "TITLE_02", "TITLE_03"}) {
                String expected = (String) p.get(key + (rtl ? "_AR" : "_EN"));
                assertThat(text)
                        .as("lang=%s: %s must render in the voucher head", lang, key)
                        .contains(expected);
            }
            assertThat(text)
                    .as("lang=%s: FOOTER_TEXT must render in the voucher foot", lang)
                    .contains((String) p.get("FOOTER_TEXT"));
            // The drawee bank is record data, so it must appear in place of the
            // template's "central bank" fallback wording.
            assertThat(text)
                    .as("lang=%s: the record's bank name must render on the cheque line", lang)
                    .contains(samplePaymentAuth().getBankName());
            // Raw ISO dates are reordered by the bidi algorithm inside an Arabic
            // run and then read as a different date, so they must be formatted.
            assertThat(text)
                    .as("lang=%s: record dates render day-first, not raw ISO", lang)
                    .contains("12-03-2026")
                    .doesNotContain("2026-03-12");
            // "فرع طرابلس" already carries the branch noun; printing the label
            // too produced "فرع فرع طرابلس".
            assertThat(text)
                    .as("lang=%s: the branch label is not doubled onto a stored branch name", lang)
                    .doesNotContain("فرع فرع");
        }
    }

    /** Head/foot params as ReportParamBuilder derives them from a seeded settings row. */
    private static Map<String, Object> paymentAuthHeadParams(String lang) {
        Map<String, Object> p = baseParams(lang);
        p.put("TITLE_01_AR", "الجمهورية اليمنية");
        p.put("TITLE_01_EN", "State of Yemen");
        p.put("TITLE_02_AR", "وزارة المالية");
        p.put("TITLE_02_EN", "Ministry of Finance");
        p.put("TITLE_03_AR", "إدارة البعثات والعلاقات الثقافية");
        p.put("TITLE_03_EN", "Department of Scholarships and Cultural Relations");
        p.put("FOOTER_TEXT", !"en".equals(lang)
                ? "هذه الاستمارة غير صالحة للصرف ما لم تكن مختومة وموقعة من الجهات المختصة."
                : "This form is not valid for disbursement unless stamped and signed by the competent authorities.");
        return p;
    }

    private static String extractAllText(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        for (var page : print.getPages()) {
            collect(page.getElements(), sb);
        }
        return sb.toString();
    }

    private static void collect(List<JRPrintElement> elements, StringBuilder sb) {
        for (JRPrintElement el : elements) {
            if (el instanceof JRPrintText text) {
                sb.append(text.getFullText()).append('\n');
            } else if (el instanceof net.sf.jasperreports.engine.JRPrintFrame frame) {
                collect(frame.getElements(), sb);
            }
        }
    }

    /**
     * Every LBL_* the voucher prints must be a seeded LU_REPORT_LABEL row.
     * One the seed lacks is invisible at runtime — the template's default is
     * blank, so the voucher silently prints nothing where the wording was.
     */
    @Test
    @DisplayName("payment-auth-form: every LBL_* the template uses is a seeded label")
    void paymentAuthFormLabelsAreComplete() throws Exception {
        String jrxml;
        try (var is = StudentDocumentTemplatesRenderTest.class
                .getResourceAsStream("/reports/payment-auth-form.jrxml")) {
            jrxml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        java.util.Set<String> used = new java.util.TreeSet<>();
        var m = java.util.regex.Pattern.compile("\\$P\\{(LBL_[A-Z0-9_]+)\\}").matcher(jrxml);
        while (m.find()) {
            used.add(m.group(1));
        }

        Map<String, Object> supplied = SeededReportLabels.putAll(new HashMap<>(), "PAYMENT_AUTH_FORM", true);
        // The V004 rows the shared header/footer owns; they predate the V077 seed.
        java.util.Set<String> shared = java.util.Set.of("LBL_GENERATED", "LBL_PAGE", "LBL_OF");

        assertThat(used).isNotEmpty();
        assertThat(used).allSatisfy(label ->
                assertThat(supplied.containsKey(label) || shared.contains(label))
                        .as("payment-auth-form uses $P{%s} but no LU_REPORT_LABEL row seeds it", label)
                        .isTrue());
    }

    @Test
    @DisplayName("student-financial-statement renders with multi-quarter lines and totals (AR + EN)")
    void financialStatement() {
        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> p = baseParams(lang);
            p.put("STUDENT_NAME_HDR", "أحمد محمد صالح");
            p.put("STUDENT_ID_HDR", "1001");
            p.put("STUDENT_UNIVERSITY_HDR", "جامعة القاهرة");
            p.put("STUDENT_COUNTRY_HDR", "مصر");
            p.put("TOTAL_RECEIVED", 9000L);
            p.put("TOTAL_WAFAR", 900L);
            p.put("TOTAL_NET", 8100L);
            p.put("TOTAL_FEES", 4500L);
            p.put("TOTAL_BOOKS", 300L);
            p.put("TOTAL_LOANS", 0L);
            byte[] pdf = service.generatePdf("student-financial-statement", p,
                    List.of(sampleLine(20241, 1), sampleLine(20241, 2), sampleLine(20242, 3)));
            assertPdf(pdf);
            dump("student-financial-statement", lang, pdf);
        }
    }

    private static void renderBothLangs(String template, List<?> data) {
        for (String lang : new String[]{"ar", "en"}) {
            byte[] pdf = service.generatePdf(template, baseParams(lang), data);
            assertPdf(pdf);
            dump(template, lang, pdf);
        }
    }

    private static void assertPdf(byte[] pdf) {
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    /** Set -DrenderTest.dumpDir=... to write the generated PDFs for visual review. */
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
            // dump is best-effort diagnostics only
        }
    }

    private static Map<String, Object> baseParams(String lang) {
        boolean rtl = !"en".equalsIgnoreCase(lang);
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        p.put("LANG", rtl ? "ar" : "en");
        p.put("REPORT_DATE", "2026-07-30");
        p.put("TITLE_01_AR", "وزارة المالية");
        p.put("TITLE_02_AR", "الإدارة العامة للبعثات");
        p.put("TITLE_03_AR", "");
        p.put("TITLE_01_EN", "Ministry of Finance");
        p.put("TITLE_02_EN", "Scholarships Department");
        p.put("TITLE_03_EN", "");
        p.put("PRINTED_BY", "test-user");
        p.put("FOOTER_TEXT", rtl ? "وثيقة رسمية صادرة عن النظام" : "Official system-issued document");
        // Shared chrome labels, as ReportParamBuilder supplies them, so a dumped
        // English PDF does not show an Arabic footer and look like a defect.
        p.put("LBL_GENERATED", rtl ? "تاريخ الإصدار" : "Generated");
        p.put("LBL_PAGE", rtl ? "صفحة" : "Page");
        p.put("LBL_OF", rtl ? "من" : "of");
        p.put("SIG_COUNT", 2);
        for (int i = 1; i <= 5; i++) {
            p.put("SIG_NAME_" + i, i <= 2 ? "اسم الموقع " + i : "");
            p.put("SIG_POS_" + i, i <= 2 ? "المنصب " + i : "");
        }
        return p;
    }

    private static StudentProfileDocDto sampleProfile() {
        StudentProfileDocDto d = new StudentProfileDocDto();
        d.setStudentId(1001);
        d.setStudentName("أحمد محمد صالح العبسي");
        d.setGenderName("ذكر");
        d.setBirthDate("2000-05-14");
        d.setBirthPlace("صنعاء");
        d.setAge("25.2");
        d.setHomeCountryName("اليمن");
        d.setHomeGovName("صنعاء");
        d.setHomeMudName("مديرية التحرير");
        d.setPassportNumber("P0123456");
        d.setPassportIssueDate("2020-01-01");
        d.setPassportExpireDate("2030-01-01");
        d.setPassportCountryName("اليمن");
        d.setDegreeName("بكالوريوس");
        d.setMajorName("هندسة الحاسوب");
        d.setLevelName("المستوى الثالث");
        d.setStudyTypeName("نفقة عامة");
        d.setStudyStatusName("مستمر");
        d.setUniversityTypeName("حكومية");
        d.setAgencyTypeName("وزارة");
        d.setMinistryTypeName("التعليم العالي");
        d.setStudyNoOfYears(4);
        d.setDelegateDecisionNo(152);
        d.setDelegationDecisionYear("2022");
        d.setStudyCountryName("مصر");
        d.setStudyGovName("القاهرة");
        d.setStudyUniversityName("جامعة القاهرة");
        d.setStudyCollegeName("كلية الهندسة");
        d.setStudyEducationName("قسم الحاسبات");
        d.setStudyMajorInName("هندسة البرمجيات");
        d.setAmount(1500);
        d.setCurrencyName("دولار أمريكي");
        d.setEmail("student@example.com");
        d.setCellphone("+20 100 000 0000");
        d.setHomeNumber("+967 1 000000");
        d.setEmergencyContact("والده - +967 770 000 000");
        d.setAddedQuarterLabel("2022 Q1");
        d.setLastQuarterLabel("2026 Q2");
        d.setCurrentQuarterLabel("2026 Q2");
        return d;
    }

    private static com.dilaw.mohe.model.PaymentAuth samplePaymentAuth() {
        com.dilaw.mohe.model.PaymentAuth a = new com.dilaw.mohe.model.PaymentAuth();
        a.setId(77);
        a.setAuthFormNumber("PA-2026-0077");
        a.setAuthDate(java.time.LocalDate.of(2026, 3, 12));
        a.setStudentId(1001);
        a.setStudentName("أحمد محمد صالح العبسي");
        a.setMinistryName("وزارة المالية");
        a.setDeptName("إدارة البعثات");
        a.setProvinceName("طرابلس");
        a.setBankName("مصرف ليبيا المركزي");
        a.setBankBranch("فرع طرابلس");
        a.setSentTo("إدارة الحسابات");
        a.setPaymentPurpose("مخصصات دراسية للفصل الأول");
        a.setOriginalAmount(new java.math.BigDecimal("15000.00"));
        a.setDeductions(new java.math.BigDecimal("1500.00"));
        a.setNetAmount(new java.math.BigDecimal("13500.00"));
        a.setCheckNumber("CHK-99120");
        a.setCheckDate(java.time.LocalDate.of(2026, 3, 20));
        a.setBudgetBookNumber("BB-2026-14");
        a.setRecipientName("محمد علي الفيتوري");
        a.setRecipientIdNumber("119880012345");
        a.setAuthStatus("APPROVED");
        return a;
    }

    private static StudentFinancialLineDto sampleLine(int quarterCode, int seq) {
        StudentFinancialLineDto l = new StudentFinancialLineDto();
        l.setRowKey("A-" + seq);
        l.setSourceType("ARCHIVE");
        l.setStudentId(1001);
        l.setQuarterCode(quarterCode);
        l.setQuarterLabel((quarterCode / 10) + " Q" + (quarterCode % 10));
        l.setReceivedYear(quarterCode / 10);
        l.setReceivedMonth(seq * 3);
        l.setReceivedAmount(3000);
        l.setWafarAmount(300);
        l.setWafarTypeName("وفر سكن");
        l.setNetAmount(2700);
        l.setCurrencyName("دولار");
        l.setBankName("البنك الأهلي");
        l.setBankBranchName("فرع القاهرة");
        l.setReceivedStatusName("مستلم");
        l.setCheckNo("CHK-" + seq);
        l.setCheckDate("2026-0" + seq + "-15");
        l.setCheckFormNumber("F-" + seq);
        return l;
    }
}
