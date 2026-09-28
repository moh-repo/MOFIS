package com.dilaw.mohe;

import com.dilaw.mohe.service.ScholarshipEtlService;
import com.dilaw.mohe.service.ScholarshipEtlService.StagedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The SQL behind sorting, filtering and searching the staged rows.
 *
 * <p>Against a <strong>mocked</strong> {@link JdbcTemplate}, for the reason the
 * sibling ETL tests give. The sort column is the part worth being careful
 * about: it cannot be a bind parameter, so it is concatenated into the
 * statement, and the whitelist is the only thing standing between that and an
 * injection.
 */
class ScholarshipStagedQueryTest {

    private ScholarshipEtlService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service = new ScholarshipEtlService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
    }

    private String pageSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        return sql.getValue();
    }

    private Object[] pageParams() {
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(anyString(), params.capture());
        return params.getValue();
    }

    // ─────────────────────────────────────────────────────────────── sorting ──

    @Test
    @DisplayName("with no sort asked for the rows come back in id order")
    void defaultOrdering() {
        service.listStaged(1, 0, 10);

        assertThat(pageSql()).contains("ORDER BY s.STUDENT_ID");
    }

    @Test
    @DisplayName("sorts by the column asked for, in the direction asked for")
    void sortsByColumn() {
        service.listStaged(1, 0, 10, new StagedQuery("AMOUNT", "descend", null, Map.of(), false));

        assertThat(pageSql()).contains("ORDER BY s.STUDENT_AMOUNT DESC, s.STUDENT_ID");
    }

    @Test
    @DisplayName("a column that is not on the whitelist changes nothing")
    void unknownSortColumnIsIgnored() {
        service.listStaged(1, 0, 10, new StagedQuery("SALARY", "ascend", null, Map.of(), false));

        assertThat(pageSql()).contains("ORDER BY s.STUDENT_ID");
        assertThat(pageSql()).doesNotContain("SALARY");
    }

    @Test
    @DisplayName("a sort column carrying SQL is not a sort column")
    void sortColumnCannotCarrySql() {
        service.listStaged(1, 0, 10,
                new StagedQuery("s.STUDENT_ID; DROP TABLE STUDENT_DATA_STAGING--", "asc", null, Map.of(), false));

        // Never matched a whitelist key, so there was nothing to concatenate.
        assertThat(pageSql()).doesNotContain("DROP");
    }

    @Test
    @DisplayName("the level sorts on its number, not on the name of the year")
    void levelSortsNumerically() {
        service.listStaged(1, 0, 10, new StagedQuery("LEVEL", "ascend", null, Map.of(), false));

        // Sorting "السنة الثامنة" and "السنة الثانية" as text puts the eighth
        // year before the second.
        assertThat(pageSql()).contains("ORDER BY s.STUDENT_CURRENT_LEVEL_ID ASC");
    }

    @Test
    @DisplayName("the quarter sorts on its number, not on the text column")
    void quarterSortsNumerically() {
        service.listStaged(1, 0, 10, new StagedQuery("QUARTER", "descend", null, Map.of(), false));

        assertThat(pageSql()).contains("ORDER BY s.STUDENT_LAST_QUARTER DESC");
    }

    @Test
    @DisplayName("id is always the last term, so paging cannot show a row twice")
    void orderingIsTotal() {
        service.listStaged(1, 0, 10, new StagedQuery("DEGREE", "ascend", null, Map.of(), false));

        assertThat(pageSql()).endsWith(
                "ORDER BY d.STUDENT_DEGREE_NAME ASC, s.STUDENT_ID OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
    }

    // ───────────────────────────────────────────────────────────── filtering ──

    @Test
    @DisplayName("a column filter matches on the lookup id behind the name")
    void filtersBindTheirValues() {
        service.listStaged(1, 0, 10,
                new StagedQuery(null, null, null, Map.of("DEGREE", List.of("1", "104")), false));

        assertThat(pageSql()).contains("AND s.STUDENT_DEGREE_ID IN (?,?)");
        assertThat(pageParams()).contains("1", "104");
    }

    @Test
    @DisplayName("a filter on a column that is not filterable is dropped")
    void unknownFilterKeyIsIgnored() {
        service.listStaged(1, 0, 10,
                new StagedQuery(null, null, null, Map.of("STUDENT_NAME) OR (1=1", List.of("x")), false));

        assertThat(pageSql()).contains("WHERE 1=1 AND s.STUDY_COUNTRY_IN = ? ORDER BY");
        assertThat(pageSql()).doesNotContain("OR (");
    }

    @Test
    @DisplayName("an untouched dropdown does not narrow the list")
    void emptyFilterIsNoFilter() {
        service.listStaged(1, 0, 10,
                new StagedQuery(null, null, null, Map.of("DEGREE", List.of("", "  ")), false));

        assertThat(pageSql()).doesNotContain("STUDENT_DEGREE_ID IN");
    }

    @Test
    @DisplayName("the country still applies alongside the rest")
    void countryStillApplies() {
        service.listStaged(7, 0, 10, new StagedQuery(null, null, null, Map.of("LEVEL", List.of("6")), false));

        assertThat(pageSql()).contains("AND s.STUDY_COUNTRY_IN = ?");
        assertThat(pageParams()[0]).isEqualTo(7);
    }

    // ──────────────────────────────────────────────────────── needs review ──

    @Test
    @DisplayName("needs-review looks at the lookups the resolver actually guessed")
    void unresolvedOnly() {
        service.listStaged(1, 0, 10, new StagedQuery(null, null, null, Map.of(), true));

        String sql = pageSql();
        assertThat(sql).contains("s.STUDENT_DEGREE_ID IN (99, 999)");
        assertThat(sql).contains("s.STUDENT_MAJOR_ID IN (99, 999)");
        assertThat(sql).contains("s.STUDENT_CURRENT_LEVEL_ID IN (99, 999)");
        assertThat(sql).contains("s.STUDENT_AGENCY_TYPE_ID IN (99, 999)");
    }

    @Test
    @DisplayName("the ministry is not part of needs-review, or the filter would be useless")
    void ministryIsNotAGuess() {
        service.listStaged(1, 0, 10, new StagedQuery(null, null, null, Map.of(), true));

        // Most entities are a university or a study level and have no ministry
        // at all: 785 of the first 842 rows sit on 99 quite correctly.
        assertThat(pageSql()).doesNotContain("s.STUDENT_MINISTRY_TYPE_ID IN (99, 999)");
    }

    @Test
    @DisplayName("needs-review off adds no clause at all")
    void unresolvedOffIsNoClause() {
        service.listStaged(1, 0, 10, StagedQuery.DEFAULT);

        assertThat(pageSql()).doesNotContain("(99, 999)");
    }

    // ──────────────────────────────────────────────────────────── searching ──

    @Test
    @DisplayName("one word is looked for across every searched column")
    void searchSpansColumns() {
        service.listStaged(1, 0, 10, new StagedQuery(null, null, "احمد", Map.of(), false));

        assertThat(pageSql()).contains("s.STUDENT_NAME LIKE ?");
        assertThat(pageSql()).contains("s.STUDENT_MAJOR_NAME LIKE ?");
        assertThat(pageParams()).contains("%احمد%");
    }

    @Test
    @DisplayName("several words all have to match, but not in one column")
    void searchTermsAreAnded() {
        service.listStaged(1, 0, 10, new StagedQuery(null, null, "احمد طب", Map.of(), false));

        assertThat(pageParams()).contains("%احمد%", "%طب%");
    }

    @Test
    @DisplayName("a wildcard somebody typed is searched for, not obeyed")
    void wildcardsAreEscaped() {
        service.listStaged(1, 0, 10, new StagedQuery(null, null, "100%", Map.of(), false));

        assertThat(pageSql()).contains("ESCAPE");
        // Unescaped this matches every staged row in the table.
        assertThat(pageParams()).contains("%100\\%%");
    }

    @Test
    @DisplayName("a blank search adds no clause")
    void blankSearchIsNoSearch() {
        service.listStaged(1, 0, 10, new StagedQuery(null, null, "   ", Map.of(), false));

        assertThat(pageSql()).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("a null query is the plain list")
    void nullQueryIsSafe() {
        service.listStaged(1, 0, 10, null);

        assertThat(pageSql()).contains("ORDER BY s.STUDENT_ID");
        assertThat(pageSql()).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("null filters inside a query are not a null pointer")
    void nullFiltersAreSafe() {
        service.listStaged(1, 0, 10, new StagedQuery("AMOUNT", "ascend", null, null, false));

        assertThat(pageSql()).contains("ORDER BY s.STUDENT_AMOUNT ASC");
    }
}
