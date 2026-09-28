package com.dilaw.mohe.report;

import com.dilaw.mohe.report.excel.ExcelExportService;
import com.dilaw.mohe.report.jasper.JasperFontRegistrar;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.security.field.FieldAccessService;
import com.dilaw.mohe.security.field.ReportFieldMask;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What actually comes out of an export, for someone who may not see the field
 * (plan W5-03, security plan R-18 / O-03).
 *
 * <p>Both exports are rendered for real and read back with POI. Until this
 * change the passport number and the national id were printed to whoever could
 * reach the report, whatever the screen had hidden from them: no export path
 * goes through Jackson, which is where the only mask lived.
 *
 * <p>Standalone, like {@code JasperXlsxExportTest}: the services are built by
 * hand and the mask is put in place, so the whole thing runs without a context
 * or a database.
 */
class ReportMaskedExportTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String PASSPORT = "A1234567";

    /**
     * A student-list row. Every field {@code student-list.jrxml} declares has to
     * be here, since Jasper reads them all off the bean.
     *
     * <p>{@code studentBirthdate} is a {@code LocalDate} and is also masked,
     * which is the reason the mask is a null rather than a marker: a template
     * declares the column's type, and a "hidden" string in its place would fail
     * the fill instead of hiding a birthday.
     */
    public static class StudentRow {
        private final String studentName;
        private final String studentPassportNumber;
        private final LocalDate studentBirthdate;

        public StudentRow(String studentName, String studentPassportNumber, LocalDate studentBirthdate) {
            this.studentName = studentName;
            this.studentPassportNumber = studentPassportNumber;
            this.studentBirthdate = studentBirthdate;
        }

        public String getStudentName() {
            return studentName;
        }

        public String getStudentPassportNumber() {
            return studentPassportNumber;
        }

        public LocalDate getStudentBirthdate() {
            return studentBirthdate;
        }

        public Integer getStudentId() {
            return 1;
        }

        public Integer getStudentAge() {
            return 24;
        }

        public String getStudentGender() {
            return "F";
        }

        public String getStudentCountryName() {
            return "Egypt";
        }

        public String getStudentMajorName() {
            return "Physics";
        }

        public String getStudentDegreeName() {
            return "Master";
        }

        public String getStudentCurrentLevelName() {
            return "Second";
        }

        public String getStudentStudyTypeName() {
            return "Full time";
        }

        public Integer getStudentStudyNoOfYears() {
            return 2;
        }

        public Integer getStudentAddedQuarter() {
            return 20251;
        }

        public String getStudentStudyStatusName() {
            return "Studying";
        }
    }

    private static final LocalDate BIRTHDATE = LocalDate.of(2001, 3, 14);

    private static List<StudentRow> oneStudent() {
        return List.of(new StudentRow("Layla", PASSPORT, BIRTHDATE));
    }

    // ── the pieces, built by hand ────────────────────────────────────────────

    private static ReportFieldMask mask() {
        UmsPermissionRepository repository = mock(UmsPermissionRepository.class);
        when(repository.findActiveFieldPermissionCodes()).thenReturn(List.of());
        return new ReportFieldMask(new FieldAccessService(repository));
    }

    private static JasperReportService jasper() throws Exception {
        JasperFontRegistrar fontRegistrar = new JasperFontRegistrar();
        Method registerFonts = JasperFontRegistrar.class.getDeclaredMethod("registerFonts");
        registerFonts.setAccessible(true);
        registerFonts.invoke(fontRegistrar);
        JasperReportService service = new JasperReportService(fontRegistrar);
        ReflectionTestUtils.setField(service, "fieldMask", mask());
        return service;
    }

    private static ExcelExportService excel() {
        ExcelExportService service = new ExcelExportService();
        ReflectionTestUtils.setField(service, "fieldMask", mask());
        return service;
    }

    /** Signs in with exactly these permission codes. */
    private static void signedInWith(String... codes) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", java.util.Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    // ── reading the workbook ─────────────────────────────────────────────────

    /** Every cell of the sheet as text, so an assertion need not know the layout. */
    private static String textOf(byte[] xlsx) throws Exception {
        DataFormatter formatter = new DataFormatter();
        StringBuilder text = new StringBuilder();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet sheet : wb) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        text.append(formatter.formatCellValue(cell)).append('\n');
                    }
                }
            }
        }
        return text.toString();
    }

    private static Map<String, Object> params(String lang) {
        Map<String, Object> p = new HashMap<>();
        p.put("LANG", lang);
        p.put("IS_RTL", !"en".equalsIgnoreCase(lang));
        p.put("REPORT_TITLE", "Students");
        p.put("GENERATED_ON", LocalDate.now().format(DATE_FMT));
        return p;
    }

    // ── the Jasper path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a report prints the passport to someone who may see it")
    void theReportPrintsWhatTheViewerMaySee() throws Exception {
        signedInWith("UMS.VIEW");

        byte[] xlsx = jasper().generateXlsx("student-list", params("en"), oneStudent());

        assertThat(textOf(xlsx)).contains("Layla").contains(PASSPORT).contains("2001");
    }

    @Test
    @DisplayName("and withholds it from someone who may not")
    void theReportWithholdsWhatTheViewerMayNot() throws Exception {
        signedInWith("REPORT_OTHER.VIEW");

        byte[] xlsx = jasper().generateXlsx("student-list", params("en"), oneStudent());

        String text = textOf(xlsx);
        assertThat(text).as("the report is still a report").contains("Layla").contains("Physics");
        assertThat(text).as("the passport is not printed around the screen that hid it")
                .doesNotContain(PASSPORT);
        assertThat(text).as("nor the birthday, whose column is a date and takes a null and not a marker")
                .doesNotContain("2001");
    }

    // ── the POI path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a spreadsheet column the viewer may not read is written empty")
    void theSpreadsheetWithholdsTheColumn() throws Exception {
        String[] headers = {"Name", "Passport"};
        String[] fields = {"studentName", "studentPassportNumber"};
        List<StudentRow> rows = oneStudent();

        signedInWith("UMS.VIEW");
        assertThat(textOf(excel().buildWorkbook("Students", headers, fields, rows)))
                .contains(PASSPORT);

        signedInWith("REPORT_OTHER.VIEW");
        String masked = textOf(excel().buildWorkbook("Students", headers, fields, rows));
        assertThat(masked).as("the heading stays, so the column is visibly withheld rather than missing")
                .contains("Passport").contains("Layla");
        assertThat(masked).doesNotContain(PASSPORT);
    }

    @Test
    @DisplayName("a scheduled export is masked by its owner, not by the nobody a timer thread signs in as")
    void theScheduledExportIsMaskedByItsOwner() throws Exception {
        String[] headers = {"Name", "Passport"};
        String[] fields = {"studentName", "studentPassportNumber"};
        List<StudentRow> rows = oneStudent();

        // No security context at all, as on a @Scheduled thread.
        SecurityContextHolder.clearContext();

        assertThat(textOf(excel().buildWorkbook("Students", headers, fields, rows,
                new ReportFieldMask.Viewer(java.util.Set.of("UMS.VIEW")))))
                .as("the owner holds the code, so their own schedule still carries the column")
                .contains(PASSPORT);

        assertThat(textOf(excel().buildWorkbook("Students", headers, fields, rows,
                new ReportFieldMask.Viewer(java.util.Set.of("REPORT_OTHER.VIEW")))))
                .as("an owner who could not see it on screen is not emailed it either")
                .doesNotContain(PASSPORT);
    }
}
