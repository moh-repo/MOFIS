package com.dilaw.mohe.report;

import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every label a template can print is a row of LU_REPORT_LABEL, and every
 * report the builder renders has its REPORT_TYPE_SETTINGS row.
 *
 * <p>The templates declare their COL_* / LBL_* parameters with an empty
 * default (V077 blanked them), so a parameter with no row prints nothing —
 * silently. This is the test that makes that loud. The golden set pins ten
 * labels to the exact wording the seed carries, so a retyped row or a wrong
 * language column is caught here rather than on a printed form.
 */
@Tag("integration")
@SpringBootTest
class ReportLabelsAreSeededTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "reports");
    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "dilaw", "mohe", "controler");
    private static final Pattern PARAM = Pattern.compile("<parameter\\s+name=\"((?:COL|LBL)_[A-Z0-9_]+)\"");
    private static final Pattern DEFAULT = Pattern.compile(
            "<parameter\\s+name=\"((?:COL|LBL)_[A-Z0-9_]+)\"[^>]*>\\s*<defaultValueExpression>\\s*<!\\[CDATA\\[(.*?)\\]\\]>");
    private static final Pattern BUILD = Pattern.compile("\\.build\\(\\s*(?:\"([A-Z_0-9]+)\"|(REPORT_CODE))\\s*,");
    private static final Pattern REPORT_CODE = Pattern.compile("REPORT_CODE\\s*=\\s*\"([A-Z_0-9]+)\"");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Test
    @DisplayName("every COL_/LBL_ parameter of every template is an active LU_REPORT_LABEL row")
    void everyTemplateParameter_hasARow() throws IOException {
        Map<String, Set<String>> byTemplate = templateParameters();
        assertThat(byTemplate).as("templates under " + TEMPLATES.toAbsolutePath()).hasSizeGreaterThanOrEqualTo(52);

        Set<String> active = activeCodes();
        Map<String, Set<String>> missing = new TreeMap<>();
        byTemplate.forEach((template, params) -> params.stream()
                .filter(c -> !active.contains(c))
                .forEach(c -> missing.computeIfAbsent(template, k -> new TreeSet<>()).add(c)));

        assertThat(missing)
                .as("template parameters with no active LU_REPORT_LABEL row (seed them in a V07x script)")
                .isEmpty();
    }

    @Test
    @DisplayName("the templates keep no wording of their own: every COL_/LBL_ default is blank")
    void templateDefaults_areBlank() throws IOException {
        Map<String, String> nonBlank = new TreeMap<>();
        try (Stream<Path> files = Files.list(TEMPLATES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".jrxml")).toList()) {
                Matcher m = DEFAULT.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    if (!"\"\"".equals(m.group(2).trim())) {
                        nonBlank.put(f.getFileName() + ":" + m.group(1), m.group(2));
                    }
                }
            }
        }
        assertThat(nonBlank)
                .as("a default in a template is a second copy of a label; the table is the only copy")
                .isEmpty();
    }

    @Test
    @DisplayName("every CODE@REPORT override names a template parameter and a report that has settings")
    void overrideRows_pointAtRealParametersAndReports() throws IOException {
        Set<String> allParams = new TreeSet<>();
        templateParameters().values().forEach(allParams::addAll);
        Set<String> reports = new TreeSet<>(jdbc.queryForList(
                "SELECT REPORT_TYPE_CODE FROM MOHECORE.REPORT_TYPE_SETTINGS", String.class));

        List<String> overrides = jdbc.queryForList(
                "SELECT CODE FROM MOHECORE.LU_REPORT_LABEL WHERE CODE LIKE '%@%' AND IS_DELETED = 0", String.class);
        assertThat(overrides).as("V077 seeds per-report override rows").isNotEmpty();
        for (String code : overrides) {
            int at = code.indexOf('@');
            assertThat(allParams).as("override %s: base code is a template parameter", code).contains(code.substring(0, at));
            assertThat(reports).as("override %s: report code has a REPORT_TYPE_SETTINGS row", code).contains(code.substring(at + 1));
        }
    }

    @Test
    @DisplayName("every report code a controller passes to the builder has a REPORT_TYPE_SETTINGS row")
    void everyBuilderReportCode_hasSettings() throws IOException {
        Set<String> codes = builderReportCodes();
        assertThat(codes).as("report codes found in " + CONTROLLERS.toAbsolutePath()).hasSizeGreaterThanOrEqualTo(50);

        Set<String> seeded = new TreeSet<>(jdbc.queryForList(
                "SELECT REPORT_TYPE_CODE FROM MOHECORE.REPORT_TYPE_SETTINGS", String.class));
        assertThat(codes)
                .as("report codes without a REPORT_TYPE_SETTINGS row — the builder has no Java default for a masthead")
                .allSatisfy(c -> assertThat(seeded).contains(c));
    }

    @Test
    @DisplayName("a report code with no settings row is an error, not a blank masthead")
    void unknownReportCode_isLoud() {
        assertThatThrownBy(() -> reportParamBuilder.build("NO_SUCH_REPORT_CODE", "ar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO_SUCH_REPORT_CODE")
                .hasMessageContaining("REPORT_TYPE_SETTINGS");
    }

    @Test
    @DisplayName("golden: ten STUDENT_LIST labels render with the seeded Arabic and English wording")
    void goldenLabels_studentList() {
        Map<String, Object> ar = reportParamBuilder.build("STUDENT_LIST", "ar");
        Map<String, Object> en = reportParamBuilder.build("STUDENT_LIST", "en");

        Map<String, String[]> golden = new HashMap<>();
        golden.put("COL_ROW_NO",        new String[]{"م", "#"});
        golden.put("COL_STUDENT_NAME",  new String[]{"اسم الطالب", "Student Name"});
        golden.put("COL_DEGREE",        new String[]{"الدرجة العلمية", "Degree"});
        golden.put("COL_MAJOR",         new String[]{"التخصص", "Major"});
        golden.put("COL_COUNTRY",       new String[]{"البلد", "Country"});
        golden.put("COL_STATUS",        new String[]{"الحالة الدراسية", "Status"});
        golden.put("COL_AMOUNT",        new String[]{"المبلغ", "Amount"});
        golden.put("LBL_TOTAL_STUDENTS", new String[]{"إجمالي الطلاب", "Total Students"});
        golden.put("LBL_NO_DATA",       new String[]{"لا توجد بيانات", "No data available"});
        golden.put("LBL_GENERATED",     new String[]{"تاريخ الإصدار", "Generated"});

        golden.forEach((code, text) -> {
            assertThat(ar.get(code)).as("%s (ar)", code).isEqualTo(text[0]);
            assertThat(en.get(code)).as("%s (en)", code).isEqualTo(text[1]);
        });
    }

    @Test
    @DisplayName("golden: a per-report override applies to its report only")
    void goldenOverrides() {
        // The approvals queue reads COL_STATUS as the approval status; a student list as the study status.
        assertThat(reportParamBuilder.build("APPROVALS_REQUESTS", "ar").get("COL_STATUS")).isEqualTo("الحالة");
        assertThat(reportParamBuilder.build("STUDENT_LIST", "ar").get("COL_STATUS")).isEqualTo("الحالة الدراسية");
        // The statutory statement shortens the column heads.
        assertThat(reportParamBuilder.build("FINANCIAL_DATA_FORM_V2", "en").get("COL_LEVEL")).isEqualTo("Current Level");
        assertThat(reportParamBuilder.build("STUDENT_LIST", "en").get("COL_LEVEL")).isEqualTo("Level");
        // The ministry's Access layout says "التاريخ", every other report "تاريخ الإصدار".
        assertThat(reportParamBuilder.build("QUARTERLY_FINANCIAL", "ar").get("LBL_GENERATED")).isEqualTo("التاريخ");
        assertThat(reportParamBuilder.build("QUARTERLY_FINANCIAL", "ar").get("LBL_TOTAL_RECORDS")).isEqualTo("عدد الجهات");
        // A caption that exists in one language only stays empty in the other.
        assertThat(reportParamBuilder.build("FINANCIAL_DATA_FORM_V2", "ar").get("LBL_YEAR_SUFFIX")).isEqualTo("م");
        assertThat(reportParamBuilder.build("FINANCIAL_DATA_FORM_V2", "en").get("LBL_YEAR_SUFFIX")).isEqualTo("");
        // The voucher's amount column is the word alone: the currency below it is read
        // from STUDY_CURRENCY_IN, so it is no longer part of the label's text.
        assertThat(reportParamBuilder.build("PAYMENT_AUTH_FORM", "ar").get("LBL_AMOUNT_CUR")).isEqualTo("المبلغ");
        assertThat(reportParamBuilder.build("PAYMENT_AUTH_FORM", "ar").get("LBL_AMOUNT_WORDS")).isEqualTo("التفقيط");
        assertThat(reportParamBuilder.build("PAYMENT_AUTH_FORM", "en").get("LBL_AMOUNT_WORDS")).isEqualTo("Amount in words");
    }

    @Test
    @DisplayName("the V077 script and the database agree on every seeded label")
    void seedScript_matchesDatabase() {
        Map<String, SeededReportLabels.Label> seed = SeededReportLabels.load();
        assertThat(seed).hasSizeGreaterThanOrEqualTo(250);

        Map<String, String[]> db = new HashMap<>();
        jdbc.query("SELECT CODE, NAME_AR, NAME_EN FROM MOHECORE.LU_REPORT_LABEL WHERE IS_DELETED = 0",
                rs -> { db.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3)}); });

        seed.forEach((code, label) -> {
            assertThat(db).as("seed row %s exists in LU_REPORT_LABEL", code).containsKey(code);
            assertThat(db.get(code)[0]).as("%s NAME_AR", code).isEqualTo(label.ar());
            assertThat(db.get(code)[1]).as("%s NAME_EN", code).isEqualTo(label.en());
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Set<String>> templateParameters() throws IOException {
        Map<String, Set<String>> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(TEMPLATES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".jrxml")).toList()) {
                Set<String> params = new TreeSet<>();
                Matcher m = PARAM.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    params.add(m.group(1));
                }
                out.put(f.getFileName().toString(), params);
            }
        }
        return out;
    }

    private Set<String> activeCodes() {
        return new TreeSet<>(jdbc.queryForList(
                "SELECT CODE FROM MOHECORE.LU_REPORT_LABEL WHERE IS_DELETED = 0 AND CODE NOT LIKE '%@%'", String.class));
    }

    private static Set<String> builderReportCodes() throws IOException {
        Set<String> codes = new TreeSet<>();
        try (Stream<Path> files = Files.walk(CONTROLLERS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                Matcher constant = REPORT_CODE.matcher(src);
                String reportCode = constant.find() ? constant.group(1) : null;
                Matcher m = BUILD.matcher(src);
                while (m.find()) {
                    String code = m.group(1) != null ? m.group(1) : reportCode;
                    assertThat(code).as("%s: build(REPORT_CODE, …) without a REPORT_CODE constant", f.getFileName()).isNotNull();
                    codes.add(code);
                }
            }
        }
        return codes;
    }
}
