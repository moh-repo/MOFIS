package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.QuarterlyFinancialReportDto.EntityFinancialSummary;
import com.dilaw.mohe.model.CustomFilterReport;
import com.dilaw.mohe.model.StudentStatusReport;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.service.CustomFilterReportService;
import com.dilaw.mohe.service.QuarterlyFinancialReportService;
import com.dilaw.mohe.service.StudentStatusReportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The three hand-built Excel exports speak the language they were asked for
 * (QA B6 / N4, live QA #12).
 *
 * <p>They wrote English headers in both languages and the Arabic lookup names in
 * both languages, so an English user downloaded English headings over Arabic
 * data. Each workbook is opened with POI: the header row must be in the requested
 * language, and a name column must hold the entity's English names for
 * {@code lang=en} and the Arabic ones, as before, for {@code lang=ar}.
 *
 * <p>{@code addFilters = false} turns off the JWT chain, not method security, so
 * the controllers' {@code @PreAuthorize} still runs -- hence the authorities.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ExcelExportLanguageTest {

    private static final List<String> REPORT_READER =
            List.of("REPORT_OTHER.VIEW", "REPORT_ACADEMIC.VIEW", "REPORT_FINANCE.VIEW");

    /** The sheet's layout: a title row, a blank row, then the headers. */
    private static final int HEADER_ROW = 2;

    /** The quarter that carries the finance rows (as in QuarterlyFinancialReportRenderTest). */
    private static final String PERIOD = "quarter=1&year=2024";

    @Autowired private MockMvc mvc;
    @Autowired private CustomFilterReportService customFilterService;
    @Autowired private StudentStatusReportService studentStatusService;
    @Autowired private QuarterlyFinancialReportService quarterlyService;

    private <T> T asMinistry(Supplier<T> body) {
        return AsUser.callWithAuthorities(AsUser.SUPERADMIN, REPORT_READER, body);
    }

    private Sheet workbook(String path) {
        MvcResult result = asMinistry(() -> {
            try {
                return mvc.perform(get(path)).andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(result.getResponse().getStatus()).as("%s status", path).isEqualTo(200);
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))
                    .getSheetAt(0);
        } catch (Exception e) {
            throw new AssertionError(path + " did not return a workbook", e);
        }
    }

    private static final DataFormatter CELLS = new DataFormatter();

    private static String text(Cell cell) {
        return cell == null ? "" : CELLS.formatCellValue(cell).trim();
    }

    private static List<String> headers(Sheet sheet) {
        List<String> out = new ArrayList<>();
        sheet.getRow(HEADER_ROW).forEach(c -> out.add(text(c)));
        return out;
    }

    /** One column of the data rows; the watermark row below them has a single cell and is skipped. */
    private static Set<String> column(Sheet sheet, int col) {
        int width = sheet.getRow(HEADER_ROW).getPhysicalNumberOfCells();
        Set<String> out = new TreeSet<>();
        for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getPhysicalNumberOfCells() == width) {
                out.add(text(row.getCell(col)));
            }
        }
        return out;
    }

    /** What an English export should print for a name: the English one, or the Arabic where none was recorded. */
    private static <R> Set<String> names(List<R> rows, Function<R, String> ar, Function<R, String> en, boolean english) {
        Set<String> out = new TreeSet<>();
        for (R row : rows) {
            String value = english && en.apply(row) != null && !en.apply(row).isBlank() ? en.apply(row) : ar.apply(row);
            out.add(value == null ? "" : value.trim());
        }
        return out;
    }

    private static boolean hasArabic(String s) {
        return s != null && s.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF);
    }

    @Test
    @DisplayName("custom filter: English headers and names for lang=en, Arabic for lang=ar")
    void customFilter() {
        String base = "/api/custom-reports/custom-filter/export-excel?lang=";
        Sheet en = workbook(base + "en");
        Sheet ar = workbook(base + "ar");

        assertThat(headers(en)).containsExactly("Student Name", "Passport No.", "University", "Major", "Country",
                "Degree", "Status", "Study Type", "Financial Amount", "Enrollment Year");
        assertThat(headers(ar)).containsExactly("اسم الطالب", "رقم جواز السفر", "الجامعة", "التخصص", "البلد",
                "الدرجة العلمية", "الحالة الدراسية", "نوع الدراسة", "المبلغ المالي", "سنة الالتحاق");
        assertThat(text(en.getRow(0).getCell(0))).startsWith("Custom Filter Report");
        assertThat(text(ar.getRow(0).getCell(0))).startsWith("تقرير التصفية المخصصة");

        List<CustomFilterReport> rows = asMinistry(() -> customFilterService.getAllReports(null));
        assertThat(rows).as("STUDENT_DATA is expected to hold students").isNotEmpty();

        // Country (4) and status (6): every student has both, and both lookups are translated.
        Set<String> countriesEn = names(rows, CustomFilterReport::getCountryName, CustomFilterReport::getCountryNameEn, true);
        Set<String> countriesAr = names(rows, CustomFilterReport::getCountryName, CustomFilterReport::getCountryNameEn, false);
        assertThat(countriesEn).as("the check means nothing if the two languages agree").isNotEqualTo(countriesAr);
        assertThat(column(en, 4)).isEqualTo(countriesEn).noneMatch(ExcelExportLanguageTest::hasArabic);
        assertThat(column(ar, 4)).as("the Arabic export is unchanged").isEqualTo(countriesAr);

        assertThat(column(en, 6)).isEqualTo(names(rows, CustomFilterReport::getStudyStatusName,
                CustomFilterReport::getStudyStatusNameEn, true));
        assertThat(column(ar, 6)).isEqualTo(names(rows, CustomFilterReport::getStudyStatusName,
                CustomFilterReport::getStudyStatusNameEn, false));
    }

    @Test
    @DisplayName("custom filter: every repository query selects the English columns")
    void customFilterQueriesSelectTheEnglishColumns() {
        // Hibernate maps a native result by column name: a query missing one of
        // the new *_name_en columns fails the whole call. Each finder runs once.
        asMinistry(() -> {
            assertThat(customFilterService.getAllReports(null)).isNotEmpty()
                    .allSatisfy(r -> assertThat(r.getCountryNameEn()).isNotBlank());
            assertThat(customFilterService.getReportsByStudyStatus(1, null)).isNotNull();
            assertThat(customFilterService.getReportsByDegreeLevel(1, null)).isNotNull();
            assertThat(customFilterService.getReportsByStudyType(1, null)).isNotNull();
            assertThat(customFilterService.getReportsByUniversityName("a", null)).isNotNull();
            assertThat(customFilterService.getReportsByEnrollmentYear(2020, null)).isNotNull();
            return null;
        });
    }

    @Test
    @DisplayName("student status: English headers and names for lang=en, Arabic for lang=ar")
    void studentStatus() {
        String base = "/api/reports/student-status/export-excel?lang=";
        Sheet en = workbook(base + "en");
        Sheet ar = workbook(base + "ar");

        assertThat(headers(en)).containsExactly("Status", "Category", "Country", "Major", "Student Count");
        assertThat(headers(ar)).containsExactly("الحالة الدراسية", "التصنيف", "البلد", "التخصص", "عدد الطلاب");
        assertThat(text(en.getRow(0).getCell(0))).startsWith("Student Status Report");
        assertThat(text(ar.getRow(0).getCell(0))).startsWith("تقرير الحالة الدراسية للطلاب");

        List<StudentStatusReport> rows = asMinistry(() -> studentStatusService.search(null, null, null, null));
        assertThat(rows).isNotEmpty();

        Set<String> statusEn = names(rows, StudentStatusReport::getStudentStudyStatusName,
                StudentStatusReport::getStudentStudyStatusNameEn, true);
        Set<String> statusAr = names(rows, StudentStatusReport::getStudentStudyStatusName,
                StudentStatusReport::getStudentStudyStatusNameEn, false);
        assertThat(statusEn).isNotEqualTo(statusAr);
        assertThat(column(en, 0)).isEqualTo(statusEn).noneMatch(ExcelExportLanguageTest::hasArabic);
        assertThat(column(ar, 0)).as("the Arabic export is unchanged").isEqualTo(statusAr);

        assertThat(column(en, 2)).isEqualTo(names(rows, StudentStatusReport::getStudentCountryName,
                StudentStatusReport::getStudentCountryNameEn, true));
        assertThat(column(ar, 2)).isEqualTo(names(rows, StudentStatusReport::getStudentCountryName,
                StudentStatusReport::getStudentCountryNameEn, false));
    }

    @Test
    @DisplayName("quarterly financial: English headers and entity names for lang=en, Arabic for lang=ar")
    void quarterlyFinancial() {
        String base = "/api/v1/reports/quarterly-financial/export-excel?" + PERIOD + "&lang=";
        Sheet en = workbook(base + "en");
        Sheet ar = workbook(base + "ar");

        assertThat(headers(en)).containsExactly("Entity", "Received #", "Received Amount", "Disbursed #",
                "Disbursed Amount", "Remaining #", "Remaining Amount");
        assertThat(headers(ar)).containsExactly("الجهة", "عدد المستلمين", "المبلغ المستلم", "عدد المصروفين",
                "المبلغ المصروف", "عدد المتبقي", "المبلغ المتبقي");
        assertThat(text(en.getRow(0).getCell(0))).startsWith("Quarterly Financial Report — ")
                .doesNotContainPattern("[\\u0600-\\u06FF]");
        assertThat(text(ar.getRow(0).getCell(0))).startsWith("التقرير المالي الربعي — ");

        List<EntityFinancialSummary> rows = asMinistry(() -> quarterlyService.getQuarterlyReport(null, 1, 2024))
                .getEntities();
        assertThat(rows).as("some quarter holds payments").isNotEmpty();

        Set<String> entitiesEn = names(rows, EntityFinancialSummary::getEntityName,
                EntityFinancialSummary::getEntityNameEn, true);
        Set<String> entitiesAr = names(rows, EntityFinancialSummary::getEntityName,
                EntityFinancialSummary::getEntityNameEn, false);
        assertThat(entitiesEn).as("universities and ministries have English names").isNotEqualTo(entitiesAr);
        assertThat(column(en, 0)).isEqualTo(entitiesEn);
        assertThat(column(ar, 0)).as("the Arabic export is unchanged").isEqualTo(entitiesAr);
    }
}
