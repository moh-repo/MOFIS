package com.dilaw.mohe.report;

import com.dilaw.mohe.model.PaymentAuth;
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
 * The payment authorisation voucher — استمارة اعتماد الصرف, نموذج رقم ١١ حسابات.
 *
 * <p>The page that prints this used to render an HTML replica of the form in the
 * browser and call {@code window.print()}, so none of the masthead below ever reached
 * a user: the titles, the ministry logo and the footer sentence all live in
 * {@code MOHECORE.REPORT_TYPE_SETTINGS} and only the server's own rendering reads them.
 * Now that the screen shows this PDF, what the head and foot actually print is worth
 * pinning down.
 *
 * <p>Excluded from {@link HeaderFooterSignatureRenderTest}'s shared-masthead sweep for
 * the reason the prescribed layouts always are: this form's head is fixed by the
 * ministry — one language's three title lines beside the form serial, no
 * {@code REPORT_TITLE} line, and no signature block, because the form carries its own
 * signature boxes in the body. It is still in that class's template list, so the rule
 * that head and foot span the full column width covers it.
 *
 * <p>No Spring context and no database: the parameters are supplied here exactly as
 * {@code ReportParamBuilder} supplies them from the settings row, which is what lets
 * the assertions name the values they expect.
 */
class PaymentAuthFormRenderTest {

    private static final String TEMPLATE = "payment-auth-form";

    /** Stand-ins for the REPORT_TYPE_SETTINGS row, distinctive enough to find on the page. */
    private static final String TITLE_1_AR = "الجمهورية العراقية";
    private static final String TITLE_2_AR = "وزارة المالية";
    private static final String TITLE_3_AR = "دائرة البعثات والعلاقات الثقافية";
    private static final String TITLE_1_EN = "Republic of Iraq";
    private static final String TITLE_2_EN = "Ministry of Finance";
    private static final String TITLE_3_EN = "Directorate of Missions";
    private static final String FOOTER_AR = "هذه الاستمارة غير نافذة ما لم تقترن بتوقيع الآمر بالصرف";
    private static final String FOOTER_EN = "This voucher is not valid without the authorising signature";

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

    /** The shared parameters as ReportParamBuilder assembles them from the settings row. */
    private static Map<String, Object> params(boolean rtl) {
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        p.put("LANG", rtl ? "ar" : "en");
        p.put("REPORT_DATE", "2026-09-01");
        p.put("PRINTED_BY", "finance.officer");

        p.put("TITLE_01", rtl ? TITLE_1_AR : TITLE_1_EN);
        p.put("TITLE_02", rtl ? TITLE_2_AR : TITLE_2_EN);
        p.put("TITLE_03", rtl ? TITLE_3_AR : TITLE_3_EN);
        p.put("TITLE_01_AR", TITLE_1_AR);
        p.put("TITLE_02_AR", TITLE_2_AR);
        p.put("TITLE_03_AR", TITLE_3_AR);
        p.put("TITLE_01_EN", TITLE_1_EN);
        p.put("TITLE_02_EN", TITLE_2_EN);
        p.put("TITLE_03_EN", TITLE_3_EN);
        p.put("FOOTER_TEXT", rtl ? FOOTER_AR : FOOTER_EN);
        p.put("REPORT_TITLE", rtl ? "استمارة اعتماد الصرف" : "Disbursement Voucher");

        p.put("LBL_GENERATED", rtl ? "طُبع في" : "Generated");
        p.put("LBL_PAGE", rtl ? "صفحة" : "Page");
        p.put("LBL_OF", rtl ? "من" : "of");
        p.put("LBL_FORM_SERIAL", rtl ? "نموذج رقم ١١ حسابات" : "Form 11");
        p.put("LBL_DOC_TITLE", rtl ? "استمارة اعتماد الصرف" : "Disbursement Voucher");

        // The amount column head is the word alone; the currency below it and the
        // total written out beside it are supplied by the controller.
        p.put("LBL_AMOUNT_CUR", rtl ? "المبلغ" : "Amount");
        p.put("LBL_TOTAL", rtl ? "الجملة" : "Total");
        p.put("LBL_AMOUNT_WORDS", rtl ? "التفقيط" : "Amount in words");
        p.put("CURRENCY_NAME", rtl ? "ريال يمني" : "Yemeni Rial");
        p.put("AMOUNT_IN_WORDS", AmountInWords.of(new java.math.BigDecimal("1400"),
                new AmountInWords.Currency("YER", "ريال يمني", "Yemeni Rial", 2), rtl));
        return p;
    }

    private static PaymentAuth record() {
        PaymentAuth r = new PaymentAuth();
        r.setAuthFormNumber("PA-2024-001");
        r.setAuthDate(java.time.LocalDate.of(2024, 3, 11));
        r.setStudentName("طالب تجريبي");
        r.setMinistryName("وزارة المالية");
        r.setDeptName("دائرة البعثات");
        r.setProvinceName("بغداد");
        r.setBankName("مصرف الرافدين");
        r.setBankBranch("فرع الكرادة");
        r.setSentTo("الملحقية الثقافية");
        r.setPaymentPurpose("مخصصات دراسية");
        r.setOriginalAmount(new java.math.BigDecimal("1500.00"));
        r.setDeductions(new java.math.BigDecimal("100.00"));
        r.setNetAmount(new java.math.BigDecimal("1400.00"));
        r.setCheckNumber("CHK-9001");
        r.setCheckDate(java.time.LocalDate.of(2024, 3, 20));
        r.setBudgetBookNumber("BB-77");
        r.setRecipientName("مستلم تجريبي");
        r.setRecipientIdNumber("ID-4242");
        return r;
    }

    @Test
    @DisplayName("Arabic voucher: the masthead and the footer sentence come off the settings row")
    void arabicHeaderAndFooterRender() throws Exception {
        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params(true), List.of(record()));

        assertThat(text)
                .as("the three masthead lines are the ones stored, not anything hard-coded")
                .contains(TITLE_1_AR, TITLE_2_AR, TITLE_3_AR);
        assertThat(text).as("the footer sentence stored on the row").contains(FOOTER_AR);
        assertThat(text).as("the prescribed form serial and title").contains("نموذج رقم ١١ حسابات");
        assertThat(text).as("who printed it and when").contains("finance.officer", "2026-09-01");
    }

    @Test
    @DisplayName("English voucher: the masthead follows the language asked for")
    void englishHeaderAndFooterRender() throws Exception {
        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params(false), List.of(record()));

        assertThat(text).contains(TITLE_1_EN, TITLE_2_EN, TITLE_3_EN);
        assertThat(text).as("the English footer, not the Arabic one").contains(FOOTER_EN);
        assertThat(text).doesNotContain(FOOTER_AR);
    }

    @Test
    @DisplayName("a settings row with no footer sentence prints the rest of the foot, not the word null")
    void blankFooterDoesNotPrintNull() throws Exception {
        // 53 of the 56 report rows have no FOOTER_TEXT, PAYMENT_AUTH among them. The
        // band must still carry the date, the user and the page number.
        Map<String, Object> params = params(true);
        params.put("FOOTER_TEXT", "");

        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params, List.of(record()));

        assertThat(text).doesNotContain("null");
        assertThat(text).as("the rest of the footer survives an empty sentence")
                .contains("finance.officer", "2026-09-01");
    }

    @Test
    @DisplayName("the record's own values reach the form, not just its frame")
    void recordValuesRender() throws Exception {
        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params(true), List.of(record()));

        assertThat(text).contains("PA-2024-001", "طالب تجريبي", "مصرف الرافدين",
                "CHK-9001", "مستلم تجريبي");
    }

    // -- The amount column, its currency, and التفقيط ---------------------------

    @Test
    @DisplayName("the amount column head is the word alone, with the currency under it")
    void theAmountColumnNamesItsCurrency() throws Exception {
        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params(true), List.of(record()));

        assertThat(text).contains("المبلغ");
        assertThat(text).as("the currency comes from the data, not from the label")
                .contains("ريال يمني");
    }

    @Test
    @DisplayName("the total is written out beside the figure it guards")
    void theTotalIsWrittenOut() throws Exception {
        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params(true), List.of(record()));

        // A digit altered after signing no longer agrees with the words next to it.
        assertThat(text).contains("التفقيط");
        assertThat(text).contains("فقط ألف وأربعمائة ريال يمني لا غير");
    }

    @Test
    @DisplayName("the English voucher writes the total out in English")
    void theEnglishTotalIsWrittenOut() throws Exception {
        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params(false), List.of(record()));

        assertThat(text).contains("Amount in words");
        assertThat(text).contains("One thousand four hundred Yemeni Rials only");
    }

    @Test
    @DisplayName("with no currency flagged the form omits both rather than printing an unnamed total")
    void noCurrencyPrintsNeither() throws Exception {
        Map<String, Object> params = params(true);
        params.put("CURRENCY_NAME", "");
        params.put("AMOUNT_IN_WORDS", "");

        String text = ReportRenderSupport.renderToText(context(), TEMPLATE, params, List.of(record()));

        assertThat(text).as("the column head survives on its own").contains("المبلغ");
        assertThat(text).doesNotContain("التفقيط");
        assertThat(text).doesNotContain("null");
    }
}
