package com.dilaw.mohe.report;

import com.dilaw.mohe.report.jasper.JasperReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiles and fills every JRXML template with empty data and minimal params.
 */
@Tag("integration")
@SpringBootTest
class JasperReportSmokeTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private JasperReportService jasperReportService;

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
                "student-financial-statement", "payment-auth-form",
                "financial-data-form-v2"
        );
    }

    @ParameterizedTest(name = "template={0}")
    @MethodSource("allTemplates")
    @DisplayName("JRXML compiles and generates PDF with empty data (AR + EN)")
    void generatePdf_emptyData_arAndEn(String templateName) {
        for (String lang : new String[]{"ar", "en"}) {
            byte[] pdf = jasperReportService.generatePdf(templateName, baseParams(lang), Collections.emptyList());
            assertThat(pdf).isNotNull();
            assertThat(pdf.length).isGreaterThan(100);
            assertThat(new String(pdf, 0, Math.min(4, pdf.length))).isEqualTo("%PDF");
        }
    }

    private static Map<String, Object> baseParams(String lang) {
        boolean rtl = !"en".equalsIgnoreCase(lang);
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        p.put("LANG", rtl ? "ar" : "en");
        p.put("REPORT_DATE", LocalDate.now().format(DATE_FMT));
        p.put("TITLE_01", rtl ? "وزارة المالية" : "Ministry of Finance");
        p.put("TITLE_02", rtl ? "تقرير تجريبي" : "Sample Report");
        p.put("TITLE_03", "");
        p.put("TITLE_04", "");
        p.put("TITLE_05", "");
        p.put("SIG_COUNT", 0);
        for (int i = 1; i <= 5; i++) {
            p.put("SIG_NAME_" + i, "");
            p.put("SIG_POS_" + i, "");
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
