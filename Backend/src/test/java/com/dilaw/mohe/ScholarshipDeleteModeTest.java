package com.dilaw.mohe;

import com.dilaw.mohe.service.CountryFileStorageService;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.service.ScholarshipDataImportService;
import com.dilaw.mohe.service.ScholarshipDataImportService.DeleteMode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the two "delete first" checkboxes actually issue.
 *
 * <p>Against a <strong>mocked</strong> {@link JdbcTemplate}, on purpose. The
 * {@code @SpringBootTest} suites in this project run against the live database,
 * and a test that exercised {@code DeleteMode.ALL} there would empty
 * {@code STUDENT_SCHOLARSHIP_DATA_IMPORT} of real imported data every time
 * anyone ran the build. The statement and its parameters are the thing worth
 * asserting, and a mock shows them exactly.
 */
class ScholarshipDeleteModeTest {

    private static final int EGYPT = 1;

    private ScholarshipDataImportService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp(@TempDir Path uploads) {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString())).thenReturn(842);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(120);

        service = new ScholarshipDataImportService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "countryFileStorage",
                new CountryFileStorageService(uploads.toString()));
        // Writes the archive; nothing here asserts on its contents.
        ReflectionTestUtils.setField(service, "fileEncryptionService", mock(FileEncryptionService.class));
    }

    /** Every DELETE this import issued, in order. */
    private List<String> deleteStatements() {
        ArgumentCaptor<String> unfiltered = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> filtered = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeast(0)).update(unfiltered.capture());
        verify(jdbc, org.mockito.Mockito.atLeast(0)).update(filtered.capture(), any(Object[].class));
        return java.util.stream.Stream.concat(
                unfiltered.getAllValues().stream(), filtered.getAllValues().stream()).toList();
    }

    private MockMultipartFile workbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("الكل");
            sheet.createRow(0).createCell(0).setCellValue("الجمهورية اليمنية");
            Row header = sheet.createRow(1);
            String[] captions = { "م", "اسم الطالب", "الجهة", "الدولة", "عام الايفاد", "الدرجه", "التخصص",
                    "رقم القرار", "مدة الدراسة", "المستوى الحالي", "اول مصرف", "المبلغ", "توقيع الطالب",
                    "ملاحظات ", "ملاحظات المالية" };
            for (int c = 0; c < captions.length; c++) {
                header.createCell(c).setCellValue(captions[c]);
            }
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue(1);
            data.createCell(1).setCellValue("دعاء علي عبده");
            data.createCell(11).setCellValue(1500);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "مصر 20253.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    /** A workbook that is not this form — it must fail before anything is deleted. */
    private MockMultipartFile unreadableWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("data").createRow(0).createCell(0).setCellValue("Name");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "other.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    @DisplayName("unticked: appends, and issues no DELETE at all")
    void noneDeletesNothing() throws Exception {
        Map<String, Object> result = service.importFile(workbook(), EGYPT, 7L, DeleteMode.NONE);

        assertThat(deleteStatements()).isEmpty();
        assertThat(result).containsEntry("rowsDeleted", 0);
    }

    @Test
    @DisplayName("null mode is treated as append, not as a wipe")
    void nullModeDeletesNothing() throws Exception {
        service.importFile(workbook(), EGYPT, 7L, null);

        assertThat(deleteStatements()).isEmpty();
    }

    @Test
    @DisplayName("this country only: the DELETE is bound to the country id")
    void countryDeleteIsFiltered() throws Exception {
        Map<String, Object> result = service.importFile(workbook(), EGYPT, 7L, DeleteMode.COUNTRY);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(sql.capture(), args.capture());

        assertThat(sql.getValue()).contains("DELETE FROM").contains("WHERE COUNTRY_ID = ?");
        assertThat(args.getValue()).isEqualTo(EGYPT);
        assertThat(result).containsEntry("rowsDeleted", 120);
    }

    @Test
    @DisplayName("this country only: never issues an unfiltered DELETE")
    void countryDeleteNeverWipesTheTable() throws Exception {
        // The slip this exists for: a WHERE built by string concatenation that
        // drops off when the id is absent takes every country with it.
        service.importFile(workbook(), EGYPT, 7L, DeleteMode.COUNTRY);

        verify(jdbc, never()).update(anyString());
        assertThat(deleteStatements()).allSatisfy(s -> assertThat(s).contains("WHERE COUNTRY_ID = ?"));
    }

    @Test
    @DisplayName("this country only: refuses to run without a country")
    void countryDeleteRequiresACountry() throws Exception {
        // Rather than falling back to an unfiltered delete.
        assertThatThrownBy(() -> service.importFile(workbook(), null, 7L, DeleteMode.COUNTRY))
                .isInstanceOf(IllegalArgumentException.class);

        verify(jdbc, never()).update(anyString());
    }

    @Test
    @DisplayName("everything: exactly one unfiltered DELETE")
    void allDeletesTheTableOnce() throws Exception {
        Map<String, Object> result = service.importFile(workbook(), EGYPT, 7L, DeleteMode.ALL);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(1)).update(sql.capture());

        assertThat(sql.getValue()).contains("DELETE FROM").doesNotContain("WHERE");
        assertThat(result).containsEntry("rowsDeleted", 842);
    }

    @Test
    @DisplayName("both ticked resolves to one wipe, not two deletes")
    void bothFlagsResolveToAll() {
        assertThat(DeleteMode.of(true, true)).isEqualTo(DeleteMode.ALL);
        assertThat(DeleteMode.of(false, true)).isEqualTo(DeleteMode.ALL);
        assertThat(DeleteMode.of(true, false)).isEqualTo(DeleteMode.COUNTRY);
        assertThat(DeleteMode.of(false, false)).isEqualTo(DeleteMode.NONE);
    }

    @Test
    @DisplayName("an unreadable workbook deletes nothing")
    void parseFailureDeletesNothing() throws Exception {
        // Parsing happens before the DELETE precisely so that discovering the
        // replacement is unusable does not cost you the data it was replacing.
        assertThatThrownBy(() -> service.importFile(unreadableWorkbook(), EGYPT, 7L, DeleteMode.ALL))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(deleteStatements()).isEmpty();
    }

    @Test
    @DisplayName("counts report the country and the whole table")
    void countsReportBoth() {
        when(jdbc.queryForObject(anyString(), any(Class.class))).thenReturn(900);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(842);

        Map<String, Object> counts = service.counts(EGYPT);

        assertThat(counts).containsEntry("totalRows", 900);
        assertThat(counts).containsEntry("countryRows", 842);
        assertThat(counts).containsEntry("countryId", EGYPT);
    }
}
