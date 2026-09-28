package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.LocalizedOptionDto;
import com.dilaw.mohe.model.StudentFinancialDataFormV2;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.StudentFinancialDataFormV2Service;
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
 * Financial Data Form V2 against the live database: the V057 view must be
 * there, its rows must carry what the sheet prints, and the template must
 * render them in both languages.
 *
 * <p>Country scope is stubbed to ministry level, as the other report tests do:
 * it resolves a UMS principal from the JWT, which a test cannot supply.
 */
@Tag("integration")
@SpringBootTest
class FinancialDataFormV2ReportTest {

    /** The quarter that carries finance rows on the development database. */
    private static final int YEAR = 2024;
    private static final int QUARTER = 2;

    @Autowired private StudentFinancialDataFormV2Service service;
    @Autowired private JasperReportService jasperReportService;
    @Autowired private ReportParamBuilder reportParamBuilder;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    @Test
    @DisplayName("the populated quarter yields rows with an entity, a level label and an amount")
    void rows_carryWhatTheSheetPrints() {
        List<StudentFinancialDataFormV2> rows = service.getByQuarterAndYear(QUARTER, YEAR, null);

        assertThat(rows).as("the quarter is expected to have finance rows").hasSizeGreaterThan(100);

        long blankEntity = rows.stream().filter(r -> r.getEntityName() == null || r.getEntityName().isBlank()).count();
        assertThat(blankEntity)
                .as("الجهة resolves university -> ministry -> agency; almost every row has one")
                .isLessThan(rows.size() / 20);

        assertThat(rows)
                .as("a student with a level has a printable level label")
                .filteredOn(r -> r.getStudentCurrentLevelId() != null)
                .allSatisfy(r -> assertThat(r.getCurrentLevelLabel()).isNotBlank());

        assertThat(rows).anySatisfy(r -> assertThat(r.getStudentAmount()).isNotNull().isPositive());

        // Ordered by entity, so the colour band changes only where the entity does.
        List<String> entities = rows.stream().map(StudentFinancialDataFormV2::getEntityName).toList();
        assertThat(entities).isSorted();
    }

    @Test
    @DisplayName("metadata lists the populated year and quarter")
    @SuppressWarnings("unchecked")
    void metadata_listsPopulatedPeriods() {
        Map<String, Object> metadata = service.getMetadata(null);

        assertThat((List<Integer>) metadata.get("years")).contains(YEAR);
        assertThat((List<Integer>) metadata.get("quarters")).contains(QUARTER);
        assertThat((List<LocalizedOptionDto>) metadata.get("countries")).isNotEmpty();
    }

    @Test
    @DisplayName("financial-data-form-v2 renders with live data (AR + EN)")
    void renders_bothLanguages() {
        List<StudentFinancialDataFormV2> rows = service.getByQuarterAndYear(QUARTER, YEAR, null);

        for (String lang : new String[]{"ar", "en"}) {
            boolean rtl = !"en".equals(lang);
            Map<String, Object> params = reportParamBuilder.build("FINANCIAL_DATA_FORM_V2", lang);
            params.put("FILTER_YEAR", String.valueOf(YEAR));
            params.put("FILTER_QUARTER", String.valueOf(QUARTER));
            params.put("QUARTER_LABEL", service.quarterLabel(QUARTER, rtl));
            params.put("FILTER_COUNTRY", StudentFinancialDataFormV2Service.singleCountryName(rows));
            params.put("ISSUING_ENTITY", String.valueOf(params.get(rtl ? "TITLE_02_AR" : "TITLE_02_EN")));
            params.put("TOTAL_AMOUNT", StudentFinancialDataFormV2Service.totalAmount(rows));
            params.put("TOTAL_STUDENTS", rows.size());

            byte[] pdf = jasperReportService.generatePdf("financial-data-form-v2", params, rows);

            assertThat(pdf).isNotNull();
            assertThat(new String(pdf, 0, Math.min(4, pdf.length))).isEqualTo("%PDF");
            dump(lang, pdf);
        }
    }

    /** Set -DrenderTest.dumpDir=... to write the PDFs for visual review. */
    private static void dump(String lang, byte[] pdf) {
        String dir = System.getProperty("renderTest.dumpDir");
        if (dir == null || dir.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path out = java.nio.file.Path.of(dir, "financial-data-form-v2-live-" + lang + ".pdf");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.write(out, pdf);
        } catch (Exception ignored) {
            // diagnostics only
        }
    }
}
