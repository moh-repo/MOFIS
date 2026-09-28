package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.StudentFinancialLineDto;
import com.dilaw.mohe.dto.StudentProfileDocDto;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.StudentDocumentReportService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Renders the four official single-student documents from real database rows.
 *
 * The template render tests use synthetic beans, so they cannot catch a view
 * column that is missing, renamed, or NULL in practice; this fills the same
 * templates from V_STUDENT_PROFILE_DOC / V_STUDENT_FINANCIAL_STATEMENT through
 * the production service so those failures surface here rather than as an empty
 * or erroring document in the browser.
 *
 * Country scope is stubbed: it depends on a UMS principal from the JWT, which a
 * test has no way to supply, and it is not what this test is exercising.
 */
@Tag("integration")
@SpringBootTest
class StudentDocumentEndpointDataTest {

    @Autowired
    private StudentDocumentReportService documentService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @MockBean
    private CountryScopeService countryScopeService;

    /** Present in V_STUDENT_PROFILE_DOC and carrying finance rows. */
    private static final int STUDENT_ID = 4;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("id-card, registration-form and enrollment-certificate render from live view data (AR + EN)")
    void profileDocuments_renderFromDatabase() {
        for (String lang : new String[]{"ar", "en"}) {
            StudentProfileDocDto profile = documentService.getProfileDoc(STUDENT_ID, lang);
            assertThat(profile).isNotNull();
            assertThat(profile.getStudentName())
                    .as("lang=%s: the view must supply a student name, not a blank document", lang)
                    .isNotBlank();

            for (String template : new String[]{
                    "student-id-card", "student-registration-form", "enrollment-certificate"}) {
                Map<String, Object> params = reportParamBuilder.build(reportCode(template), lang);
                params.put("CERT_BODY", documentService.buildCertificateBody(profile, lang, ""));
                byte[] pdf = jasperReportService.generatePdf(template, params, List.of(profile));
                assertPdf(template, lang, pdf);
            }
        }
    }

    @Test
    @DisplayName("student-financial-statement renders from live view data (AR + EN)")
    void financialStatement_rendersFromDatabase() {
        for (String lang : new String[]{"ar", "en"}) {
            StudentProfileDocDto profile = documentService.getProfileDoc(STUDENT_ID, lang);
            List<StudentFinancialLineDto> lines = documentService.getFinancialStatement(STUDENT_ID, lang);
            assertThat(lines)
                    .as("lang=%s: student %d is expected to have finance rows", lang, STUDENT_ID)
                    .isNotEmpty();

            var totals = documentService.getFinancialTotals(STUDENT_ID, lines);
            Map<String, Object> params = reportParamBuilder.build("STUDENT_FINANCIAL_STATEMENT", lang);
            params.put("STUDENT_NAME_H", profile.getStudentName());
            params.put("TOTAL_RECEIVED", totals.received());
            params.put("TOTAL_WAFAR", totals.wafar());
            params.put("TOTAL_NET", totals.net());
            params.put("TOTAL_FEES", totals.fees());
            params.put("TOTAL_BOOKS", totals.books());
            params.put("TOTAL_LOANS", totals.loans());

            byte[] pdf = jasperReportService.generatePdf("student-financial-statement", params, lines);
            assertPdf("student-financial-statement", lang, pdf);
        }
    }

    @Test
    @DisplayName("student-financial-statement also renders as a spreadsheet (QA N17, DEC-06)")
    void financialStatement_rendersAsXlsx() {
        List<StudentFinancialLineDto> lines = documentService.getFinancialStatement(STUDENT_ID, "en");
        Map<String, Object> params = reportParamBuilder.build("STUDENT_FINANCIAL_STATEMENT", "en");

        byte[] xlsx = jasperReportService.generateXlsx("student-financial-statement", params, lines);

        // An .xlsx is a zip: it starts with "PK".
        assertThat(xlsx).isNotNull();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        assertThat(xlsx.length).isGreaterThan(1000);
    }

    private static String reportCode(String template) {
        return switch (template) {
            case "student-id-card" -> "STUDENT_ID_CARD";
            case "student-registration-form" -> "STUDENT_REGISTRATION_FORM";
            case "enrollment-certificate" -> "ENROLLMENT_CERTIFICATE";
            default -> "STUDENT_FINANCIAL_STATEMENT";
        };
    }

    private static void assertPdf(String template, String lang, byte[] pdf) {
        assertThat(pdf).as("template=%s lang=%s", template, lang).isNotNull();
        assertThat(new String(pdf, 0, Math.min(4, pdf.length))).isEqualTo("%PDF");
        assertThat(pdf.length)
                .as("template=%s lang=%s produced a suspiciously small PDF", template, lang)
                .isGreaterThan(1000);
        dump(template, lang, pdf);
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
}
