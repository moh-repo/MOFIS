package com.dilaw.mohe.report;

import com.dilaw.mohe.report.jasper.JasperFontRegistrar;
import com.dilaw.mohe.report.jasper.JasperReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Standalone (no Spring context / no DB needed) — exercises the real
 * {@link JasperReportService#generateXlsx} against real templates.
 *
 * <p>Reports are exported twice from one JRXML: PDF for preview/print, XLSX for
 * the Export button. This guards the second path, which is the riskier one —
 * {@code JRXlsxExporter} is a different exporter from the PDF one and fails at
 * runtime rather than at compile time, so a template that renders a perfect PDF
 * can still throw when asked for a spreadsheet.
 *
 * <p>An .xlsx is a ZIP container, so the assertions below check the bytes really
 * are a workbook rather than merely non-empty: a PK header, and the OOXML parts
 * Excel needs to open the file at all.
 */
class JasperXlsxExportTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static JasperReportService service;

    private static synchronized JasperReportService service() throws Exception {
        if (service == null) {
            JasperFontRegistrar fontRegistrar = new JasperFontRegistrar();
            Method registerFonts = JasperFontRegistrar.class.getDeclaredMethod("registerFonts");
            registerFonts.setAccessible(true);
            registerFonts.invoke(fontRegistrar);
            service = new JasperReportService(fontRegistrar);
        }
        return service;
    }

    @ParameterizedTest(name = "template={0}")
    @ValueSource(strings = {"monthly-report", "student-list", "bank-transaction", "university-performance"})
    @DisplayName("a template exports to a real .xlsx workbook, in Arabic and English")
    void generateXlsx_producesWorkbook(String templateName) throws Exception {
        for (String lang : new String[]{"ar", "en"}) {
            byte[] xlsx = service().generateXlsx(templateName, baseParams(lang), Collections.emptyList());

            assertThat(xlsx).as("%s/%s should produce bytes", templateName, lang).isNotNull();
            assertThat(xlsx.length).as("%s/%s workbook size", templateName, lang).isGreaterThan(1000);
            assertThat(new byte[]{xlsx[0], xlsx[1]})
                    .as("%s/%s should start with the PK zip header", templateName, lang)
                    .isEqualTo(new byte[]{'P', 'K'});
            assertThat(entryNames(xlsx))
                    .as("%s/%s should contain the OOXML workbook parts", templateName, lang)
                    .contains("xl/workbook.xml", "[Content_Types].xml");
        }
    }

    private static java.util.List<String> entryNames(byte[] xlsx) throws Exception {
        java.util.List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                names.add(e.getName());
            }
        }
        return names;
    }

    /** Mirrors JasperReportSmokeTest's minimal parameter set. */
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
        return p;
    }
}
