package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.EnrollmentStatisticsRowDto;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.EnrollmentStatisticsMetadataService;
import com.dilaw.mohe.service.EnrollmentStatisticsService;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Enrollment statistics read from the live database.
 *
 * <p>The point of interest is {@code V_Enrollment_Statistics.enrollment_quarter}:
 * it holds a packed MOHE quarter code (20251 = Q1 2025), and the view carries no
 * year column, so both the year and the quarter number have to come from
 * {@code MOHECORE.MOHE_QUARTER}.
 */
@Tag("integration")
@SpringBootTest
class EnrollmentStatisticsReportTest {

    @Autowired private EnrollmentStatisticsService service;
    @Autowired private EnrollmentStatisticsMetadataService metadataService;
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
    @DisplayName("rows come from the view and carry a resolved year and quarter")
    void rows_haveResolvedPeriod() {
        List<EnrollmentStatisticsRowDto> rows = service.search(null, null, null, null, null, null, "en");

        assertThat(rows).as("the view is expected to hold enrollment rows").isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getQuarterCode()).as("raw code must survive for filtering").isNotNull();
            assertThat(r.getQuarterLabel()).as("every row needs a printable period").isNotBlank();
            assertThat(r.getStudentCount()).isNotNull();
        });

        // At least one row must resolve against the quarter dimension, otherwise
        // the join is silently doing nothing and every period reads "Unspecified".
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getEnrollmentYear()).isNotNull();
            assertThat(r.getEnrollmentQuarter()).isBetween(1, 4);
        });
    }

    @Test
    @DisplayName("the packed quarter code is never shown as a quarter number")
    void packedCode_isNotDisplayed() {
        List<EnrollmentStatisticsRowDto> rows = service.search(null, null, null, null, null, null, "en");

        assertThat(rows).allSatisfy(r -> {
            // 20251 must never leak into a field meant to hold 1..4 or a label.
            assertThat(r.getEnrollmentQuarter()).satisfiesAnyOf(
                    q -> assertThat(q).isNull(),
                    q -> assertThat(q).isBetween(1, 4));
            assertThat(r.getQuarterLabel())
                    .as("label must not print the raw code")
                    .doesNotContain(String.valueOf(r.getQuarterCode()));
        });
    }

    @Test
    @DisplayName("quarter filter options are labelled, not raw codes")
    void metadata_quarterOptionsAreLabelled() {
        var options = metadataService.getMetadata("en", null, null).getQuarterOptions();

        assertThat(options).isNotEmpty();
        assertThat(options).allSatisfy(o -> {
            assertThat(o.getId()).isNotNull();
            assertThat(o.getName())
                    .as("option label must not be the packed code")
                    .isNotBlank()
                    .doesNotContain(String.valueOf(o.getId()));
        });
    }

    @Test
    @DisplayName("each quarter option is named as its rows are, and no option is a bare number (R5-15)")
    void metadata_optionsNamedAsTheRows() {
        for (String lang : new String[]{"en", "ar"}) {
            var meta = metadataService.getMetadata(lang, null, null);

            // Live QA R5-15: 20253, 20251, 20241 and 0 were all offered as "Unspecified".
            assertThat(meta.getQuarterOptions()).extracting(o -> o.getName())
                    .as("lang=%s: two quarter options with one name", lang)
                    .doesNotHaveDuplicates();
            meta.getQuarterOptions().forEach(o -> assertThat(
                    service.search(o.getId(), null, null, null, null, null, lang))
                    .as("lang=%s: quarter option '%s' (%s)", lang, o.getName(), o.getId())
                    .isNotEmpty()
                    .allSatisfy(r -> assertThat(r.getQuarterLabel()).isEqualTo(o.getName())));

            // ... and Degree and Study Type offered the view's not-recorded 0 as "0".
            Stream.of(meta.getCountries(), meta.getMajors(), meta.getDegrees(), meta.getStudyTypes())
                    .flatMap(List::stream)
                    .forEach(o -> assertThat(o.getName())
                            .as("lang=%s: option %s", lang, o.getId())
                            .doesNotMatch("\\d+"));
        }
    }

    @Test
    @DisplayName("every offered filter option returns rows")
    void everyFilterOption_matchesData() {
        var meta = metadataService.getMetadata("en", null, null);

        assertThat(meta.getCountries()).as("nationality options").isNotEmpty();
        assertThat(meta.getMajors()).as("major options").isNotEmpty();
        assertThat(meta.getDegrees()).as("degree options").isNotEmpty();
        assertThat(meta.getStudyTypes()).as("study type options").isNotEmpty();
        assertThat(meta.getQuarterOptions()).as("quarter options").isNotEmpty();

        // The dropdowns used to be filled from the lookup tables — 444 majors
        // against the two in the view — so almost every choice returned nothing.
        meta.getQuarterOptions().forEach(o -> assertThat(
                service.search(o.getId(), null, null, null, null, null, "en"))
                .as("quarter option '%s' (%s) must return rows", o.getName(), o.getId())
                .isNotEmpty());

        meta.getMajors().forEach(o -> assertThat(
                service.search(null, null, null, o.getId(), null, null, "en"))
                .as("major option '%s' (%s) must return rows", o.getName(), o.getId())
                .isNotEmpty());

        meta.getDegrees().forEach(o -> assertThat(
                service.search(null, null, null, null, o.getId(), null, "en"))
                .as("degree option '%s' (%s) must return rows", o.getName(), o.getId())
                .isNotEmpty());

        meta.getStudyTypes().forEach(o -> assertThat(
                service.search(null, null, null, null, null, o.getId(), "en"))
                .as("study type option '%s' (%s) must return rows", o.getName(), o.getId())
                .isNotEmpty());

        meta.getCountries().forEach(o -> assertThat(
                service.search(null, o.getId(), null, null, null, null, "en"))
                .as("nationality option '%s' (%s) must return rows", o.getName(), o.getId())
                .isNotEmpty());
    }

    @Test
    @DisplayName("filters actually narrow the result they claim to")
    void filters_narrowCorrectly() {
        var meta = metadataService.getMetadata("en", null, null);
        List<EnrollmentStatisticsRowDto> all = service.search(null, null, null, null, null, null, "en");

        Integer majorId = meta.getMajors().get(0).getId();
        List<EnrollmentStatisticsRowDto> byMajor =
                service.search(null, null, null, majorId, null, null, "en");
        assertThat(byMajor).allSatisfy(r -> assertThat(r.getStudentMajorId()).isEqualTo(majorId));
        assertThat(byMajor.size()).isLessThanOrEqualTo(all.size());

        Integer quarterCode = meta.getQuarterOptions().get(0).getId();
        List<EnrollmentStatisticsRowDto> byQuarter =
                service.search(quarterCode, null, null, null, null, null, "en");
        assertThat(byQuarter).allSatisfy(r -> assertThat(r.getQuarterCode()).isEqualTo(quarterCode));

        Integer studyTypeId = meta.getStudyTypes().get(0).getId();
        assertThat(service.search(null, null, null, null, null, studyTypeId, "en"))
                .allSatisfy(r -> assertThat(r.getStudentStudyTypeId()).isEqualTo(studyTypeId));

        Integer degreeId = meta.getDegrees().get(0).getId();
        assertThat(service.search(null, null, null, null, degreeId, null, "en"))
                .allSatisfy(r -> assertThat(r.getStudentDegreeId()).isEqualTo(degreeId));

        // Combining two filters must intersect, not widen.
        assertThat(service.search(quarterCode, null, null, majorId, null, null, "en").size())
                .isLessThanOrEqualTo(byQuarter.size());
    }

    @Test
    @DisplayName("the PDF prints the resolved year and quarter, in both languages")
    void pdf_printsResolvedPeriod() throws Exception {
        for (String lang : new String[]{"ar", "en"}) {
            List<EnrollmentStatisticsRowDto> rows = service.search(null, null, null, null, null, null, lang);
            Map<String, Object> params = reportParamBuilder.build("ENROLLMENT_STATISTICS", lang);
            long total = rows.stream().mapToLong(r -> r.getStudentCount() == null ? 0 : r.getStudentCount()).sum();
            params.put("TOTAL_STUDENTS", total);
            params.put("TOTAL_ROWS", rows.size());
            params.put("TOTAL_COUNTRIES", (int) rows.stream().map(EnrollmentStatisticsRowDto::getStudentCountryId).distinct().count());
            params.put("TOTAL_MAJORS", (int) rows.stream().map(EnrollmentStatisticsRowDto::getStudentMajorId).distinct().count());

            byte[] pdf = jasperReportService.generatePdf("enrollment-statistics", params, rows);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

            String text = ReportRenderSupport.renderToText("enrollment-statistics", params, rows);

            EnrollmentStatisticsRowDto resolved = rows.stream()
                    .filter(r -> r.getEnrollmentYear() != null)
                    .findFirst().orElseThrow();
            assertThat(text)
                    .as("lang=%s: the year column must show the year from MOHE_QUARTER", lang)
                    .contains(String.valueOf(resolved.getEnrollmentYear()));
            assertThat(text)
                    .as("lang=%s: the quarter column must show its label", lang)
                    .contains(resolved.getQuarterLabel());
            assertThat(text)
                    .as("lang=%s: the packed quarter code must not appear anywhere", lang)
                    .doesNotContain(String.valueOf(resolved.getQuarterCode()));
        }
    }
}
