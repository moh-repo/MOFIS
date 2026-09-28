package com.dilaw.mohe;

import com.dilaw.mohe.service.ScholarshipDataImportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading the attachés' quarterly financial-aid workbook.
 *
 * <p>The fixture is built here rather than committed: the real sheets carry 800+
 * students by name with the sum each is owed, and none of that belongs in a
 * repository. What is reproduced is the shape — eight rows of letterhead above
 * the grid, a totals row at the bottom, blank rows after it, numbers stored as
 * numbers — because the shape is what the parser exists to survive.
 *
 * <p>Only {@code parse} and {@code preview} are exercised; neither touches the
 * database or the filesystem, so the service is constructed directly.
 */
class ScholarshipDataImportParsingTest {

    private final ScholarshipDataImportService service = new ScholarshipDataImportService();

    /**
     * The quarter ordinal words live in {@code LU_REPORT_LABEL} (V076); this test
     * never opens the database, so the four words are handed in as a fixture.
     */
    @org.junit.jupiter.api.BeforeEach
    void quarterWords() {
        java.util.Map<String, String> words = java.util.Map.of(
                "LBL_QUARTER_ORDINAL_1", "الأول",
                "LBL_QUARTER_ORDINAL_2", "الثاني",
                "LBL_QUARTER_ORDINAL_3", "الثالث",
                "LBL_QUARTER_ORDINAL_4", "الرابع");
        com.dilaw.mohe.service.codelist.CodeListService stub =
                new com.dilaw.mohe.service.codelist.CodeListService(null) {
                    @Override
                    public String label(String name, String code, String lang) {
                        return words.getOrDefault(code, code);
                    }
                };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codeLists", stub);
    }

    /**
     * A workbook laid out like the ones that arrive: letterhead on rows 1-8, the
     * grid header on row 9, data from row 10, then a totals row and blanks.
     *
     * @param headerOffset extra blank rows inserted above the letterhead, to prove
     *                     the header row is found rather than assumed
     */
    private MockMultipartFile workbook(int headerOffset) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("الكل بعد الحذف");
            int r = headerOffset;

            // Row 1: republic | report title (columns A and H)
            Row row1 = sheet.createRow(r++);
            row1.createCell(0).setCellValue("الجمهورية اليمنية");
            row1.createCell(7).setCellValue("كشوفات المساعدة المالية الربعية");

            // Row 2: ministry | الربع: الثالث | العام 2025م | 17.08.2026
            Row row2 = sheet.createRow(r++);
            row2.createCell(0).setCellValue("  وزارة المالية");
            row2.createCell(7).setCellValue("الربع:");
            row2.createCell(9).setCellValue("الثالث");
            row2.createCell(11).setCellValue("العام");
            row2.createCell(12).setCellValue("2025م");
            row2.createCell(13).setCellValue("17.08.2026");

            // Row 3: department | الجهة: <entity>
            Row row3 = sheet.createRow(r++);
            row3.createCell(0).setCellValue("الإدارة العامة للبعثات - إدارة مالية البعثات");
            row3.createCell(7).setCellValue("الجهة:");
            row3.createCell(9).setCellValue("وزارة المالية");

            // Rows 4-8: the notes caption and its four numbered notes
            sheet.createRow(r++).createCell(0).setCellValue("ملاحظات هامة:");
            sheet.createRow(r++).createCell(0).setCellValue("1.لا يتم الصرف للطلاب الموقوفة مستحقاتهم");
            sheet.createRow(r++).createCell(0).setCellValue("2. الطلاب المحدد لهم الربع الحالي كاخر مصرف");
            sheet.createRow(r++).createCell(0).setCellValue("3. يتم ايقاف الصرف للطلاب المتعثرين");
            sheet.createRow(r++).createCell(0).setCellValue("4. على مسئولية السفارة او الملحقية");

            // Row 9: the grid header. 'الدرجه' and the trailing space on
            // 'ملاحظات ' are spelled exactly as the real sheets spell them.
            Row header = sheet.createRow(r++);
            String[] captions = { "م", "اسم الطالب", "الجهة", "الدولة", "عام الايفاد", "الدرجه", "التخصص",
                    "رقم القرار", "مدة الدراسة", "المستوى الحالي", "اول مصرف", "المبلغ", "توقيع الطالب",
                    "ملاحظات ", "ملاحظات المالية" };
            for (int c = 0; c < captions.length; c++) {
                header.createCell(c).setCellValue(captions[c]);
            }

            // Two data rows. Numerics are numeric cells, as they are in the file.
            Row first = sheet.createRow(r++);
            first.createCell(0).setCellValue(1);
            first.createCell(1).setCellValue("دعاء علي عبده سعيد الجرادي");
            first.createCell(2).setCellValue("د. جامعية");
            first.createCell(3).setCellValue("مصر");
            first.createCell(4).setCellValue(2017);
            first.createCell(5).setCellValue("جامعية");
            first.createCell(6).setCellValue("طب بشري");
            first.createCell(7).setCellValue(2025082);
            first.createCell(8).setCellValue(7);
            first.createCell(9).setCellValue(6);
            first.createCell(10).setCellValue(20173);
            first.createCell(11).setCellValue(1500);
            first.createCell(13).setCellValue("تمديد بموجب قرار");
            first.createCell(14).setCellValue(0);

            Row second = sheet.createRow(r++);
            second.createCell(0).setCellValue(2);
            second.createCell(1).setCellValue("زيد علي عبدالجبار المهاب");
            second.createCell(3).setCellValue("مصر");
            second.createCell(4).setCellValue(2018);
            second.createCell(11).setCellValue(2220.5);

            // The totals row: no serial, and its label padded with tatweel.
            Row totals = sheet.createRow(r++);
            totals.createCell(1).setCellValue("الإجـــــــــمــــــــــــــــالي");
            totals.createCell(11).setCellValue(3720.5);

            sheet.createRow(r++);
            sheet.createRow(r);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "مصر 20253.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    @DisplayName("Finds the grid header below the letterhead instead of assuming row 1")
    void findsHeaderRow() throws Exception {
        assertThat(service.parse(workbook(0)).headerRowNumber).isEqualTo(9);

        // Three more rows of letterhead and it still lands on the captions.
        assertThat(service.parse(workbook(3)).headerRowNumber).isEqualTo(12);
    }

    @Test
    @DisplayName("Keeps the students and drops the totals row and the blanks")
    void skipsTotalsRow() throws Exception {
        ScholarshipDataImportService.ParsedSheet parsed = service.parse(workbook(0));

        assertThat(parsed.rows).hasSize(2);
        assertThat(parsed.rows).extracting(row -> row.get("STUDENT_NAME"))
                .containsExactly("دعاء علي عبده سعيد الجرادي", "زيد علي عبدالجبار المهاب");
        assertThat(parsed.skippedRows).isEqualTo(1);
    }

    @Test
    @DisplayName("Numbers stay numbers — 2017, not \"2017.0\"")
    void readsNumericCellsAsNumbers() throws Exception {
        Map<String, Object> row = service.parse(workbook(0)).rows.get(0);

        assertThat(row.get("SCHOLARSHIP_YEAR")).isEqualTo(2017);
        assertThat(row.get("STUDY_DURATION")).isEqualTo(7);
        assertThat(row.get("CURRENT_LEVEL")).isEqualTo(6);
        assertThat(row.get("FIRST_DISBURSEMENT")).isEqualTo(20173);
        assertThat(row.get("DECISION_NO")).isEqualTo("2025082");
        assertThat((BigDecimal) row.get("AMOUNT")).isEqualByComparingTo("1500");
        assertThat((BigDecimal) service.parse(workbook(0)).rows.get(1).get("AMOUNT"))
                .isEqualByComparingTo("2220.5");
    }

    @Test
    @DisplayName("Matches captions across spelling variants and a trailing space")
    void matchesCaptionVariants() throws Exception {
        ScholarshipDataImportService.ParsedSheet parsed = service.parse(workbook(0));

        // 'الدرجه' for DEGREE, and 'ملاحظات ' with its trailing space.
        assertThat(parsed.columnIndex).containsEntry("DEGREE", 5);
        assertThat(parsed.columnIndex).containsEntry("NOTES", 13);
        // 'ملاحظات' must not swallow 'ملاحظات المالية'.
        assertThat(parsed.columnIndex).containsEntry("FINANCIAL_NOTES", 14);
        assertThat(parsed.rows.get(0).get("NOTES")).isEqualTo("تمديد بموجب قرار");
        assertThat(parsed.rows.get(0).get("FINANCIAL_NOTES")).isEqualTo("0");
    }

    @Test
    @DisplayName("Reads the letterhead above the grid into the header block")
    void readsHeaderBlock() throws Exception {
        ScholarshipDataImportService.SheetHeader header = service.parse(workbook(0)).header;

        assertThat(header.republic).isEqualTo("الجمهورية اليمنية");
        assertThat(header.ministry).isEqualTo("وزارة المالية");
        assertThat(header.department).isEqualTo("الإدارة العامة للبعثات - إدارة مالية البعثات");
        assertThat(header.reportTitle).isEqualTo("كشوفات المساعدة المالية الربعية");
        assertThat(header.entity).isEqualTo("وزارة المالية");
        assertThat(header.quarter).isEqualTo("الثالث");
        assertThat(header.quarterNo).isEqualTo(3);
        assertThat(header.year).isEqualTo("2025م");
        assertThat(header.yearNo).isEqualTo(2025);
        assertThat(header.statementDate).isEqualTo("17.08.2026");
        assertThat(header.notes[0]).startsWith("1.لا يتم الصرف");
        assertThat(header.notes[3]).startsWith("4. على مسئولية السفارة");
    }

    @Test
    @DisplayName("A blank cell is empty, not a failed row")
    void leavesMissingCellsNull() throws Exception {
        Map<String, Object> second = service.parse(workbook(0)).rows.get(1);

        assertThat(second.get("ENTITY")).isNull();
        assertThat(second.get("SPECIALIZATION")).isNull();
        assertThat(second.get("STUDENT_SIGNATURE")).isNull();
        assertThat(second.get("COUNTRY")).isEqualTo("مصر");
    }

    @Test
    @DisplayName("Rows carry the sheet row they came from, for tracing back")
    void recordsSourceRow() throws Exception {
        ScholarshipDataImportService.ParsedSheet parsed = service.parse(workbook(0));

        assertThat(parsed.rows.get(0).get("_row")).isEqualTo(10);
        assertThat(parsed.rows.get(1).get("_row")).isEqualTo(11);
    }

    @Test
    @DisplayName("A workbook that is not this form is refused, not half-imported")
    void rejectsUnrecognisedSheet() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Amount");
            sheet.createRow(1).createCell(0).setCellValue("someone");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "other.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

            assertThatThrownBy(() -> service.parse(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("اسم الطالب");
        }
    }

    @Test
    @DisplayName("Preview reports what will happen without writing anything")
    void previewSummarises() throws Exception {
        Map<String, Object> preview = service.preview(workbook(0));

        assertThat(preview).containsEntry("success", true);
        assertThat(preview).containsEntry("headerRow", 9);
        assertThat(preview).containsEntry("totalRows", 2);
        assertThat(preview).containsEntry("skippedRows", 1);
        assertThat(preview.get("sheetName")).isEqualTo("الكل بعد الحذف");
        assertThat((List<?>) preview.get("previewRows")).hasSize(2);
    }
}
