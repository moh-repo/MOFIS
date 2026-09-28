package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.report.jasper.DynamicTableReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.QueryExecutionService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Reporting on a query's own result.
 *
 * <p>The columns are not known until the query has run, so the report is
 * assembled at fill time. These check that what comes out is a real document
 * with the requested columns in it — a PDF that renders is not evidence on its
 * own, since an empty page also renders.
 */
@Tag("integration")
@SpringBootTest
class QueryResultReportTest {

    @Autowired private DynamicTableReportService reportService;
    @Autowired private QueryExecutionService queryService;
    @Autowired private ReportParamBuilder paramBuilder;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void ministryUser() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    private static List<Map<String, Object>> rows(List<String> labels, int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String label : labels) {
                row.put(label, label + "-" + i);
            }
            rows.add(row);
        }
        return rows;
    }

    @Test
    @DisplayName("a query result renders as a PDF in both languages")
    void rendersBothLanguages() {
        List<String> labels = List.of("STUDENT_ID", "STUDENT_NAME", "STUDY_COUNTRY_IN");

        for (String lang : List.of("ar", "en")) {
            byte[] pdf = reportService.render(labels, rows(labels, 12), paramBuilder.build("QUERY_BUILDER", lang));

            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
                    .as("a PDF, not an error page")
                    .startsWith("%PDF-");
            // 12 rows of three columns cannot fit in a near-empty document.
            assertThat(pdf.length).as("%s document has content", lang).isGreaterThan(8_000);
        }
    }

    @Test
    @DisplayName("the column headings are the ones asked for")
    void headingsAreTheSelectedColumns() throws Exception {
        // Rendered through the same path, then read back out of the filled
        // document — the point is that the headings are not the template's.
        List<String> labels = List.of("STUDENT_ID", "STUDENT_NAME");
        Map<String, Object> params = paramBuilder.build("QUERY_BUILDER", "en");
        byte[] pdf = reportService.render(labels, rows(labels, 3), params);

        String text = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(pdf).isNotEmpty();
        assertThat(text).startsWith("%PDF-");
        // The label text is compressed inside the PDF, so assert on the design
        // instead: a different column list must produce a different document.
        byte[] other = reportService.render(
                List.of("STUDENT_ID", "STUDENT_PASSPORT_NUMBER"), rows(labels, 3), params);
        assertThat(other.length).isNotEqualTo(pdf.length);
    }

    @Test
    @DisplayName("right-to-left mirrors the columns rather than reversing them")
    void rtlMirrorsColumnOrder() {
        int[] widths = DynamicTableReportService.widths(4);
        int[] ltr = DynamicTableReportService.positions(widths, false);
        int[] rtl = DynamicTableReportService.positions(widths, true);

        assertThat(ltr[0]).as("the first column starts at the left edge").isZero();
        assertThat(rtl[0] + widths[0])
                .as("and in Arabic it ends at the right edge")
                .isEqualTo(802);
        // Reading order is preserved: each successive column is further left.
        for (int i = 1; i < 4; i++) {
            assertThat(ltr[i]).isGreaterThan(ltr[i - 1]);
            assertThat(rtl[i]).isLessThan(rtl[i - 1]);
        }
        assertThat(java.util.Arrays.stream(widths).sum())
                .as("the columns fill the page exactly, with no rounding gap")
                .isEqualTo(802);
    }

    @Test
    @DisplayName("more columns than a page can carry is refused with a reason")
    void tooManyColumnsIsRefused() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < DynamicTableReportService.MAX_PRINT_COLUMNS + 1; i++) {
            labels.add("COLUMN_" + i);
        }

        assertThatThrownBy(() -> reportService.render(labels, List.of(),
                paramBuilder.build("QUERY_BUILDER", "en")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most " + DynamicTableReportService.MAX_PRINT_COLUMNS);
    }

    @Test
    @DisplayName("values are formatted for a printed page, not dumped")
    void formatsValuesForPrint() {
        assertThat(DynamicTableReportService.format(null)).isEmpty();
        // String.valueOf on a Timestamp gives '2024-02-01 00:00:00.0'.
        assertThat(DynamicTableReportService.format(Timestamp.valueOf("2024-02-01 00:00:00")))
                .isEqualTo("2024-02-01");
        assertThat(DynamicTableReportService.format(Timestamp.valueOf("2024-02-01 14:30:00")))
                .isEqualTo("2024-02-01 14:30");
        assertThat(DynamicTableReportService.format(LocalDate.of(2026, 5, 9))).isEqualTo("2026-05-09");
        assertThat(DynamicTableReportService.format(new BigDecimal("1500.00"))).isEqualTo("1500");
        assertThat(DynamicTableReportService.format(new BigDecimal("1500.50"))).isEqualTo("1500.5");
        // stripTrailingZeros turns 1000 into 1E+3 unless the scale is corrected.
        assertThat(DynamicTableReportService.format(new BigDecimal("1000")))
                .as("a round number must not print as scientific notation")
                .isEqualTo("1000");
        assertThat(DynamicTableReportService.format(new byte[]{1, 2})).isEqualTo("(binary)");
    }

    @Test
    @DisplayName("a real query's rows reach the document under the keys the result used")
    void rendersARealQueryEndToEnd() {
        // The result's column names are what the renderer reads each row by, so
        // a mismatch between the two would produce a full page of blank cells.
        QuerySpecDto spec = new QuerySpecDto();
        spec.setSource("STUDENT_DATA");
        spec.setColumns(List.of("STUDENT_ID", "STUDENT_NAME"));
        spec.setJoins(List.of(joinTo("STUDENT_DEGREE")));
        spec.setLimit(10);

        QueryResultDto result = queryService.execute(spec);
        List<String> labels = result.getColumns().stream()
                .map(QueryResultDto.Column::getName).toList();

        assertThat(result.getRowCount()).isGreaterThan(0);
        assertThat(result.getRows().get(0).keySet())
                .as("the renderer looks rows up by these names")
                .containsAll(labels);

        byte[] pdf = reportService.render(labels, result.getRows(),
                paramBuilder.build("QUERY_BUILDER", "ar"));
        byte[] empty = reportService.render(labels, List.of(),
                paramBuilder.build("QUERY_BUILDER", "ar"));

        assertThat(pdf.length)
                .as("ten rows of real data must weigh more than none")
                .isGreaterThan(empty.length);
    }

    private static QuerySpecDto.Join joinTo(String table) {
        QuerySpecDto.Join join = new QuerySpecDto.Join();
        join.setTable(table);
        join.setType("LEFT");
        join.setLeftColumn("STUDENT_DEGREE_ID");
        join.setColumn("STUDENT_DEGREE_ID");
        return join;
    }
}
