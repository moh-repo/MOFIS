package com.dilaw.mohe;

import com.dilaw.mohe.service.ScholarshipDataImportService;
import com.dilaw.mohe.service.ScholarshipDataImportService.ListQuery;
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
 * The SQL behind sorting, filtering and searching the imported list.
 *
 * <p>Against a <strong>mocked</strong> {@link JdbcTemplate}, for the same
 * reason {@code ScholarshipDeleteModeTest} is: the {@code @SpringBootTest}
 * suites here run against the live database, and these assertions are about
 * the statement and its parameters, which a mock shows exactly.
 *
 * <p>The sort column is the part worth being careful about. It cannot be a
 * bind parameter — no database lets you parameterise an identifier — so it is
 * concatenated into the SQL, and the only thing standing between that and an
 * injection is the whitelist. Several tests below exist purely to prove the
 * whitelist is what decides, not the caller.
 */
class ScholarshipListQueryTest {

    private ScholarshipDataImportService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service = new ScholarshipDataImportService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
    }

    /** The page query — the second statement, after the count. */
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
    @DisplayName("no sort asked for leaves the newest batch on top")
    void defaultOrdering() {
        service.list(null, 0, 10);

        assertThat(pageSql()).contains("ORDER BY IMPORTED_AT DESC, ID");
    }

    @Test
    @DisplayName("sorts by the column asked for, in the direction asked for")
    void sortsByColumn() {
        service.list(null, 0, 10, new ListQuery("STUDENT_NAME", "descend", null, Map.of()));

        assertThat(pageSql()).contains("ORDER BY STUDENT_NAME DESC, ID");
    }

    @Test
    @DisplayName("a column that is not on the whitelist changes nothing")
    void unknownSortColumnIsIgnored() {
        service.list(null, 0, 10, new ListQuery("SALARY", "ascend", null, Map.of()));

        assertThat(pageSql()).contains("ORDER BY IMPORTED_AT DESC, ID");
        assertThat(pageSql()).doesNotContain("SALARY");
    }

    @Test
    @DisplayName("a sort column carrying SQL is not a sort column")
    void sortColumnCannotCarrySql() {
        service.list(null, 0, 10,
                new ListQuery("ID; DROP TABLE STUDENT_SCHOLARSHIP_DATA_IMPORT--", "ascend", null, Map.of()));

        // Not escaped, not quoted: absent. It never matched a whitelist key, so
        // there was nothing to put in the statement.
        assertThat(pageSql()).doesNotContain("DROP");
        assertThat(pageSql()).contains("ORDER BY IMPORTED_AT DESC, ID");
    }

    @Test
    @DisplayName("a direction that is not descending is ascending")
    void unknownDirectionIsAscending() {
        service.list(null, 0, 10, new ListQuery("AMOUNT", "sideways", null, Map.of()));

        assertThat(pageSql()).contains("ORDER BY AMOUNT ASC, ID");
    }

    @Test
    @DisplayName("the quarter and the year sort on their numbers, not their words")
    void quarterAndYearSortNumerically() {
        service.list(null, 0, 10, new ListQuery("HDR_QUARTER", "ascend", null, Map.of()));
        assertThat(pageSql()).contains("ORDER BY HDR_QUARTER_NO ASC, ID");

        setUp();
        service.list(null, 0, 10, new ListQuery("HDR_YEAR", "ascend", null, Map.of()));
        // "2025م" as text sorts beside "2019م"; the parsed number does not.
        assertThat(pageSql()).contains("ORDER BY HDR_YEAR_NO ASC, ID");
    }

    @Test
    @DisplayName("ID is always the last term, so paging cannot show a row twice")
    void orderingIsTotal() {
        // Every row of one statement shares a quarter. Ordering on the quarter
        // alone leaves the database free to break the tie differently between
        // two requests, which puts a row on two pages and another on none.
        service.list(null, 0, 10, new ListQuery("HDR_QUARTER", "descend", null, Map.of()));

        assertThat(pageSql()).endsWith("ORDER BY HDR_QUARTER_NO DESC, ID OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
    }

    // ───────────────────────────────────────────────────────────── filtering ──

    @Test
    @DisplayName("a column filter becomes a bound IN list")
    void filtersBindTheirValues() {
        service.list(null, 0, 10,
                new ListQuery(null, null, null, Map.of("DEGREE", List.of("جامعية", "ماجستير"))));

        assertThat(pageSql()).contains("AND DEGREE IN (?,?)");
        assertThat(pageParams()).contains("جامعية", "ماجستير");
    }

    @Test
    @DisplayName("a filter on a column that is not filterable is dropped")
    void unknownFilterKeyIsIgnored() {
        service.list(null, 0, 10,
                new ListQuery(null, null, null, Map.of("SOURCE_FILE) OR (1=1", List.of("x"))));

        // "WHERE 1=1" is this query's own base clause, so look at what follows
        // it: an ignored filter adds nothing, and the injected "OR (" that was
        // meant to widen the WHERE is simply not there.
        assertThat(pageSql()).contains("WHERE 1=1 ORDER BY");
        assertThat(pageSql()).doesNotContain("OR (");
    }

    @Test
    @DisplayName("an empty filter list is no filter at all")
    void emptyFilterIsNotAnImpossibleIn() {
        // "IN ()" is a syntax error, and IN with only blanks would match nothing
        // — either way an untouched dropdown must not narrow the list.
        service.list(null, 0, 10,
                new ListQuery(null, null, null, Map.of("DEGREE", List.of("", "   "))));

        assertThat(pageSql()).doesNotContain("DEGREE IN");
    }

    @Test
    @DisplayName("the country filter still binds alongside the rest")
    void countryStillApplies() {
        service.list(7, 0, 10, new ListQuery(null, null, null, Map.of("DEGREE", List.of("جامعية"))));

        assertThat(pageSql()).contains("AND COUNTRY_ID = ?");
        assertThat(pageParams()[0]).isEqualTo(7);
    }

    // ──────────────────────────────────────────────────────────── searching ──

    @Test
    @DisplayName("one word is looked for across every searched column")
    void searchSpansColumns() {
        service.list(null, 0, 10, new ListQuery(null, null, "احمد", Map.of()));

        assertThat(pageSql()).contains("STUDENT_NAME LIKE ?");
        assertThat(pageSql()).contains("SPECIALIZATION LIKE ?");
        assertThat(pageParams()).contains("%احمد%");
    }

    @Test
    @DisplayName("several words all have to match, but not in one column and not in order")
    void searchTermsAreAnded() {
        service.list(null, 0, 10, new ListQuery(null, null, "احمد طب", Map.of()));

        // Two bracketed groups, ANDed: the name matches one, the specialization
        // the other. Nobody types a five-word Arabic name in the right order.
        assertThat(pageSql().split("\\Q AND (\\E", -1)).hasSize(3);
        assertThat(pageParams()).contains("%احمد%", "%طب%");
    }

    @Test
    @DisplayName("a wildcard the user typed is searched for, not obeyed")
    void wildcardsAreEscaped() {
        service.list(null, 0, 10, new ListQuery(null, null, "100%", Map.of()));

        assertThat(pageSql()).contains("ESCAPE");
        // Unescaped this matches every row in the table.
        assertThat(pageParams()).contains("%100\\%%");
    }

    @Test
    @DisplayName("a bracket is a bracket, not a character class")
    void bracketsAreEscaped() {
        service.list(null, 0, 10, new ListQuery(null, null, "[ab]", Map.of()));

        assertThat(pageParams()).contains("%\\[ab]%");
    }

    @Test
    @DisplayName("a blank search does not add a clause")
    void blankSearchIsNoSearch() {
        service.list(null, 0, 10, new ListQuery(null, null, "   ", Map.of()));

        assertThat(pageSql()).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("a pasted paragraph is cut off rather than run as fifty LIKEs")
    void searchTermsAreCapped() {
        service.list(null, 0, 10,
                new ListQuery(null, null, "a b c d e f g h i j k l", Map.of()));

        // Six searched columns, five terms at most: thirty parameters, plus the
        // two the paging adds.
        assertThat(pageParams()).hasSize(32);
    }

    // ───────────────────────────────────────────────────────────── defaults ──

    @Test
    @DisplayName("a null query is the plain list")
    void nullQueryIsSafe() {
        service.list(null, 0, 10, null);

        assertThat(pageSql()).contains("ORDER BY IMPORTED_AT DESC, ID");
        assertThat(pageSql()).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("null filters inside a query are not a null pointer")
    void nullFiltersAreSafe() {
        service.list(null, 0, 10, new ListQuery("ID", "ascend", null, null));

        assertThat(pageSql()).contains("ORDER BY ID ASC, ID");
    }
}
