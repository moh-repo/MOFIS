package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.service.QueryExecutionService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The query engine's validation is a security boundary, so most of these tests
 * are about what it refuses.
 *
 * <p>A caller cannot send SQL — only a structured spec — so the question is
 * whether anything in that spec can reach the statement as text. Each rejection
 * test names a way it might.
 */
@Tag("integration")
@SpringBootTest
class QueryExecutionServiceTest {

    @Autowired private QueryExecutionService service;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void ministryUser() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    private void countryScopedUser(int countryId) {
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(2L, false, false, List.of(countryId)));
    }

    private static QuerySpecDto spec(String source, String... columns) {
        QuerySpecDto spec = new QuerySpecDto();
        spec.setSource(source);
        spec.setColumns(List.of(columns));
        return spec;
    }

    // ------------------------------------------------------------- it works

    @Test
    @DisplayName("a simple query returns rows from the named source")
    void executes_againstTheDatabase() {
        QueryResultDto result = service.execute(spec("STUDENT_DATA", "STUDENT_ID", "STUDENT_NAME"));

        assertThat(result.getRowCount()).isGreaterThan(0);
        assertThat(result.getColumns()).extracting(QueryResultDto.Column::getName)
                .containsExactly("STUDENT_ID", "STUDENT_NAME");
        assertThat(result.getRows()).allSatisfy(row ->
                assertThat(row).containsKeys("STUDENT_ID", "STUDENT_NAME"));
        // The statement is returned for inspection, and must carry placeholders
        // rather than the values themselves.
        assertThat(result.getSql()).startsWith("SELECT TOP (?)").contains("[MOHECORE].[STUDENT_DATA]");
    }

    @Test
    @DisplayName("deleted students are not in a query over the student table (live QA H7)")
    void deletedStudentsAreExcluded() {
        QueryResultDto result = service.execute(spec("STUDENT_DATA", "STUDENT_ID"));
        assertThat(result.getSql()).contains("ISNULL(t0.IS_DELETED, 0) = 0");
        Integer deleted = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE IS_DELETED = 1 ORDER BY STUDENT_ID", Integer.class);
        if (deleted != null) {
            assertThat(result.getRows()).noneMatch(row -> deleted.equals(((Number) row.get("STUDENT_ID")).intValue()));
        }
    }

    @Test
    @DisplayName("filters, sorting and limits are applied")
    void appliesFilterSortAndLimit() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID", "STUDENT_NAME");
        QuerySpecDto.Filter filter = new QuerySpecDto.Filter();
        filter.setColumn("STUDENT_ID");
        filter.setOperator("LE");
        filter.setValue("50");
        spec.setFilters(List.of(filter));
        QuerySpecDto.Sort sort = new QuerySpecDto.Sort();
        sort.setColumn("STUDENT_ID");
        sort.setDirection("DESC");
        spec.setSorts(List.of(sort));
        spec.setLimit(5);

        QueryResultDto result = service.execute(spec);

        assertThat(result.getRowCount()).isLessThanOrEqualTo(5);
        assertThat(result.getRows()).allSatisfy(row ->
                assertThat(((Number) row.get("STUDENT_ID")).intValue()).isLessThanOrEqualTo(50));
        List<Integer> ids = result.getRows().stream()
                .map(r -> ((Number) r.get("STUDENT_ID")).intValue()).toList();
        assertThat(ids).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("the row cap is enforced and disclosed")
    void limitIsCappedAndReported() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID");
        spec.setLimit(3);

        QueryResultDto result = service.execute(spec);

        assertThat(result.getRowCount()).isEqualTo(3);
        // 941 students exist, so a limit of 3 must say the answer is a prefix.
        assertThat(result.isTruncated())
                .as("a truncated result that does not say so is a wrong answer")
                .isTrue();
    }

    @Test
    @DisplayName("reporting views are queryable, not only tables")
    void viewsAreQueryable() {
        assertThat(service.execute(spec("V_Enrollment_Statistics")).getColumns()).isNotEmpty();
    }

    // -------------------------------------------------------- it refuses

    @Test
    @DisplayName("an unknown source is refused")
    void unknownSource_isRefused() {
        assertThatThrownBy(() -> service.execute(spec("NO_SUCH_TABLE", "X")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown source");
    }

    @Test
    @DisplayName("SQL in the source name is not a source")
    void sqlInSourceName_isRefused() {
        // There is no sanitising step to defeat: the name has to match a row in
        // the catalogue, and this does not.
        assertThatThrownBy(() -> service.execute(
                spec("STUDENT_DATA; DROP TABLE STUDENT_DATA--", "STUDENT_ID")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown source");
    }

    @Test
    @DisplayName("SQL in a column name is not a column")
    void sqlInColumnName_isRefused() {
        assertThatThrownBy(() -> service.execute(
                spec("STUDENT_DATA", "STUDENT_ID FROM STUDENT_DATA; DELETE FROM STUDENT_DATA--")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown column");
    }

    @Test
    @DisplayName("a column from another table is not a column of this one")
    void columnFromAnotherTable_isRefused() {
        assertThatThrownBy(() -> service.execute(spec("STUDENT_DEGREE", "STUDENT_PASSPORT_NUMBER")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown column");
    }

    @Test
    @DisplayName("credential columns cannot be selected")
    void credentialColumns_areUnreachable() {
        // The catalogue omits them, so there is no name that resolves to one.
        assertThatThrownBy(() -> service.execute(spec("StudentPortalAccount", "PasswordHash")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("denied sources are refused even though the catalogue lists them")
    void deniedSources_areRefused() {
        for (String source : List.of("AUDIT_LOG", "NOTIFICATION_SETTINGS", "V_Financial_Audit")) {
            assertThatThrownBy(() -> service.execute(spec(source)))
                    .as("%s must not be queryable", source)
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    @DisplayName("an unsupported operator is refused")
    void unknownOperator_isRefused() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID");
        QuerySpecDto.Filter filter = new QuerySpecDto.Filter();
        filter.setColumn("STUDENT_ID");
        filter.setOperator("= 1 OR 1=1 --");
        filter.setValue("1");
        spec.setFilters(List.of(filter));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported operator");
    }

    @Test
    @DisplayName("a sort direction is ASC or DESC and nothing else")
    void sortDirection_isRefused() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID");
        QuerySpecDto.Sort sort = new QuerySpecDto.Sort();
        sort.setColumn("STUDENT_ID");
        sort.setDirection("ASC; DROP TABLE STUDENT_DATA--");
        spec.setSorts(List.of(sort));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ASC or DESC");
    }

    @Test
    @DisplayName("a value that cannot be the column's type is refused, not sent")
    void mistypedValue_isRefused() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID");
        QuerySpecDto.Filter filter = new QuerySpecDto.Filter();
        filter.setColumn("STUDENT_ID");
        filter.setOperator("EQ");
        filter.setValue("1 OR 1=1");
        spec.setFilters(List.of(filter));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("is not valid for");
    }

    @Test
    @DisplayName("a value reaches the database as a parameter, never as text")
    void valuesAreBound() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID", "STUDENT_NAME");
        QuerySpecDto.Filter filter = new QuerySpecDto.Filter();
        filter.setColumn("STUDENT_NAME");
        filter.setOperator("LIKE");
        filter.setValue("'; DROP TABLE STUDENT_DATA--");
        spec.setFilters(List.of(filter));

        QueryResultDto result = service.execute(spec);

        // The query runs and simply matches nothing; the text is a parameter.
        assertThat(result.getRowCount()).isZero();
        assertThat(result.getSql()).doesNotContain("DROP");
        assertThat(result.getParameters())
                .anySatisfy(p -> assertThat(String.valueOf(p)).contains("DROP TABLE"));
    }

    // ---------------------------------------------------------------- scope

    @Test
    @DisplayName("a country-scoped user only sees their countries")
    void countryScope_isApplied() {
        countryScopedUser(1);

        QueryResultDto result = service.execute(spec("STUDENT_DATA", "STUDENT_ID", "STUDY_COUNTRY_IN"));

        assertThat(result.getSql()).contains("[STUDY_COUNTRY_IN] IN (?)");
        assertThat(result.getScopeNote()).contains("STUDY_COUNTRY_IN");
        assertThat(result.getRows()).allSatisfy(row ->
                assertThat(((Number) row.get("STUDY_COUNTRY_IN")).intValue()).isEqualTo(1));
    }

    @Test
    @DisplayName("scope applies even when the country column is not selected")
    void countryScope_appliesToUnselectedColumn() {
        countryScopedUser(1);

        QueryResultDto scoped = service.execute(spec("STUDENT_DATA", "STUDENT_ID"));
        assertThat(scoped.getSql())
                .as("a user must not escape their scope by leaving the column out")
                .contains("[STUDY_COUNTRY_IN] IN (?)");

        ministryUser();
        QueryResultDto unscoped = service.execute(spec("STUDENT_DATA", "STUDENT_ID"));
        assertThat(unscoped.getRowCount())
                .as("and the ministry view must be the wider one")
                .isGreaterThanOrEqualTo(scoped.getRowCount());
    }

    @Test
    @DisplayName("a source holding students but no country column is refused to a scoped user")
    void unscopeableStudentSource_isRefusedToScopedUser() {
        countryScopedUser(1);

        // The subject is discovered, not named. This test has been rewritten
        // twice because the table it named kept becoming scopeable: STUDENT_LOAN
        // gained a tenant column in V029, STUDENT_PARENT in V032. That is the
        // tenancy work succeeding, but it kept looking like this test failing.
        //
        // What is actually being asserted is the rule — a person-level source
        // with no country column must be refused — so the test now asks the
        // schema which sources the rule applies to and uses one of those. It can
        // only fail if the rule stops working.
        String unscopeable = anUnscopeablePersonLevelSource(false);

        assertThatThrownBy(() -> service.execute(spec(unscopeable)))
                .as("%s holds people and cannot be narrowed, so a scoped user must be refused it — "
                        + "a general query tool must not become the way round the scoping "
                        + "every report applies", unscopeable)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be narrowed");
    }

    /**
     * A catalogue source that identifies people and carries no country column,
     * which is exactly the combination {@code QueryExecutionService} refuses.
     *
     * @param joinableToStudentData require a STUDENT_ID, so the caller can join
     *                              it to STUDENT_DATA and borrow that country column
     */
    private String anUnscopeablePersonLevelSource(boolean joinableToStudentData) {
        List<String> candidates = jdbc.queryForList("""
                SELECT DISTINCT qb.table_name
                FROM MOHECORE.V_Query_Builder qb
                WHERE NOT EXISTS (
                        SELECT 1 FROM sys.columns c
                        WHERE c.object_id = OBJECT_ID('MOHECORE.' + qb.table_name)
                          AND c.name IN ('STUDY_COUNTRY_IN', 'STUDY_COUNTRY_IN_ID'))
                  AND EXISTS (
                        SELECT 1 FROM sys.columns c
                        WHERE c.object_id = OBJECT_ID('MOHECORE.' + qb.table_name)
                          AND (REPLACE(LOWER(c.name), '_', '') IN
                                    ('studentdataid','studentname','studentemail',
                                     'studentcellphone','studentbirthdate')
                            OR REPLACE(LOWER(c.name), '_', '') LIKE '%passport%'
                            OR REPLACE(LOWER(c.name), '_', '') LIKE 'studentid%'
                            OR REPLACE(LOWER(c.name), '_', '') LIKE '%studentid'))
                  AND (? = 0 OR EXISTS (
                        SELECT 1 FROM sys.columns c
                        WHERE c.object_id = OBJECT_ID('MOHECORE.' + qb.table_name)
                          AND c.name = 'STUDENT_ID'))
                  -- Leftover one-off staging table; V035__drop_leftover_tables.sql
                  -- drops it, so a test must not come to depend on it existing.
                  AND qb.table_name <> 'Q2_2024_EGY'
                ORDER BY qb.table_name
                """, String.class, joinableToStudentData ? 1 : 0);

        assertThat(candidates)
                .as("no person-level source without a country column is left in the catalogue. "
                        + "If tenancy now covers everything that is good news, but it makes this "
                        + "test vacuous — retire it rather than letting it pass on an empty set.")
                .isNotEmpty();
        return candidates.get(0);
    }

    @Test
    @DisplayName("a student table that gained a tenant column is scoped, not refused")
    void scopedSource_gainedByTenantColumn() {
        countryScopedUser(1);

        // V029 stamped STUDY_COUNTRY_IN_ID onto the student-owned child tables.
        // The point of doing so was not only Row-Level Security: it also turns
        // these from "refused, cannot be narrowed" into "queryable, and narrowed"
        // for a scoped user. STUDENT_LOAN is one of them.
        QueryResultDto result = service.execute(spec("STUDENT_LOAN"));

        assertThat(result.getSql())
                .as("the tenant column must actually be used, not merely present")
                .contains("[STUDY_COUNTRY_IN_ID] IN (?)");
        assertThat(result.getColumns()).isNotEmpty();
    }

    @Test
    @DisplayName("reference data is queryable by a scoped user, and says it was not narrowed")
    void referenceData_isAllowedForScopedUser() {
        countryScopedUser(1);

        // Only 16 of the 181 catalogued objects carry a study-country column.
        // Refusing everything that cannot be narrowed shut a scoped user out of
        // every lookup in the schema, DOCUMENT_TYPES among them — for objects
        // that hold no students, so there was nothing to narrow.
        QueryResultDto result = service.execute(spec("DOCUMENT_TYPES"));

        assertThat(result.getColumns()).isNotEmpty();
        assertThat(result.getSql())
                .as("nothing to narrow means no country predicate")
                .doesNotContain("STUDY_COUNTRY_IN");
        assertThat(result.getScopeNote())
                .as("the reader must be told the result is not scoped")
                .contains("reference data");

        // STUDENT_DEGREE names degrees, not students — the STUDENT_ prefix on
        // half this schema's lookups must not be read as person data.
        assertThat(service.execute(spec("STUDENT_DEGREE")).getColumns()).isNotEmpty();
    }

    @Test
    @DisplayName("no table linked to STUDENT_DATA is served unscoped")
    void everyStudentLinkedTable_isRefusedToScopedUser() {
        countryScopedUser(1);

        // Independent of the column-name rule the service uses: ask the schema
        // which tables declare a foreign key to STUDENT_DATA. Every one of them
        // that cannot be narrowed must be refused, so an object added later that
        // holds students without a country column fails here rather than
        // quietly becoming readable to a user who may not see those students.
        List<String> studentLinked = jdbc.queryForList("""
                SELECT DISTINCT o.name
                FROM sys.foreign_keys fk
                INNER JOIN sys.objects o ON o.object_id = fk.parent_object_id
                WHERE fk.referenced_object_id = OBJECT_ID('MOHECORE.STUDENT_DATA')
                  AND o.schema_id = SCHEMA_ID('MOHECORE')
                  AND o.is_ms_shipped = 0
                  AND o.name NOT LIKE '%[_][_][_]old'
                  AND o.name NOT LIKE '%[_]BKUP'
                  AND o.name NOT LIKE '%[_]BAK'
                  AND NOT EXISTS (
                      SELECT 1 FROM sys.columns c
                      WHERE c.object_id = o.object_id
                        AND c.name IN ('STUDY_COUNTRY_IN', 'STUDY_COUNTRY_IN_ID'))
                """, String.class);

        assertThat(studentLinked)
                .as("if this is empty the query is wrong, not the schema")
                .isNotEmpty();
        assertThat(studentLinked).allSatisfy(table ->
                assertThatThrownBy(() -> service.execute(spec(table)))
                        .as("%s links to STUDENT_DATA and cannot be narrowed", table)
                        .isInstanceOf(ResponseStatusException.class));
    }

    @Test
    @DisplayName("a ministry user may query a source with no country column")
    void unscopeableSource_isAllowedForMinistry() {
        assertThat(service.execute(spec("STUDENT_PARENT")).getColumns()).isNotEmpty();
        assertThat(service.execute(spec("STUDENT_DEGREE")).getColumns()).isNotEmpty();
    }

    // ---------------------------------------------------------------- joins

    private static QuerySpecDto.Join join(String table, String type,
                                          String leftColumn, String column) {
        QuerySpecDto.Join join = new QuerySpecDto.Join();
        join.setTable(table);
        join.setType(type);
        join.setLeftColumn(leftColumn);
        join.setColumn(column);
        return join;
    }

    @Test
    @DisplayName("two tables can be queried together, and the columns say where they came from")
    void join_returnsColumnsFromBothTables() {
        QuerySpecDto spec = spec("STUDENT_DATA",
                "STUDENT_DATA.STUDENT_NAME", "STUDENT_DEGREE.STUDENT_DEGREE_NAME");
        spec.setJoins(List.of(join("STUDENT_DEGREE", "LEFT", "STUDENT_DEGREE_ID", "STUDENT_DEGREE_ID")));
        spec.setLimit(5);

        QueryResultDto result = service.execute(spec);

        assertThat(result.getSql())
                .contains("LEFT JOIN [MOHECORE].[STUDENT_DEGREE]")
                .contains("ON t1.[STUDENT_DEGREE_ID] = t0.[STUDENT_DEGREE_ID]");
        assertThat(result.getColumns()).extracting(QueryResultDto.Column::getName)
                .as("a joined result names its columns by table, since two tables may share one")
                .containsExactly("STUDENT_DATA.STUDENT_NAME", "STUDENT_DEGREE.STUDENT_DEGREE_NAME");
        assertThat(result.getRowCount()).isBetween(1, 5);
        assertThat(result.getRows()).allSatisfy(row ->
                assertThat(row).containsKey("STUDENT_DATA.STUDENT_NAME"));
    }

    @Test
    @DisplayName("a single-source query still returns bare column names")
    void singleSource_keepsBareColumnNames() {
        // Queries saved before joins existed must return what they always did.
        QueryResultDto result = service.execute(spec("STUDENT_DATA", "STUDENT_ID"));

        assertThat(result.getColumns()).extracting(QueryResultDto.Column::getName)
                .containsExactly("STUDENT_ID");
        assertThat(result.getSql()).doesNotContain(" AS [");
    }

    @Test
    @DisplayName("a join can bring the country column a source lacks")
    void join_makesAnUnscopeableSourceQueryable() {
        countryScopedUser(1);

        // Discovered for the same reason as the test above: the source only has
        // to be refused on its own and joinable to STUDENT_DATA on STUDENT_ID.
        // Joined, it inherits that table's country column and the scope is
        // applied through it.
        String unscopeable = anUnscopeablePersonLevelSource(true);

        QuerySpecDto spec = spec(unscopeable, unscopeable + ".STUDENT_ID");
        spec.setJoins(List.of(join("STUDENT_DATA", "INNER", "STUDENT_ID", "STUDENT_ID")));

        QueryResultDto result = service.execute(spec);

        assertThat(result.getSql())
                .as("the scope must be applied through the joined table's column, "
                        + "not silently dropped because the base table lacks one")
                .contains("t1.[STUDY_COUNTRY_IN] IN (?)");
        assertThat(result.getParameters()).contains(1);
        assertThat(result.getScopeNote()).contains("STUDENT_DATA.STUDY_COUNTRY_IN");
    }

    @Test
    @DisplayName("a bare column name several joined tables share is refused, not guessed")
    void join_ambiguousColumnIsRefused() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID");
        spec.setJoins(List.of(join("STUDENT_PARENT", "INNER", "STUDENT_ID", "STUDENT_ID")));

        // Guessing would mean a report showing the right column name from the
        // wrong table, which is the kind of wrong answer nobody notices.
        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("name it as TABLE.STUDENT_ID");
    }

    @Test
    @DisplayName("a denied source cannot be reached by joining to it")
    void join_cannotReachADeniedSource() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_DATA.STUDENT_ID");
        spec.setJoins(List.of(join("StudentPortalAccount", "INNER", "STUDENT_ID", "StudentId")));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not available to the query builder");
    }

    @Test
    @DisplayName("SQL in a join is not a join")
    void join_rejectsInjectedNamesAndTypes() {
        QuerySpecDto withSqlTable = spec("STUDENT_DATA", "STUDENT_ID");
        withSqlTable.setJoins(List.of(
                join("STUDENT_DEGREE; DROP TABLE STUDENT_DATA--", "INNER", "STUDENT_DEGREE_ID", "X")));
        assertThatThrownBy(() -> service.execute(withSqlTable))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown source");

        QuerySpecDto withSqlType = spec("STUDENT_DATA", "STUDENT_DATA.STUDENT_ID");
        withSqlType.setJoins(List.of(
                join("STUDENT_DEGREE", "INNER JOIN X ON 1=1 --", "STUDENT_DEGREE_ID", "STUDENT_DEGREE_ID")));
        assertThatThrownBy(() -> service.execute(withSqlType))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INNER or LEFT");

        QuerySpecDto withSqlColumn = spec("STUDENT_DATA", "STUDENT_DATA.STUDENT_ID");
        withSqlColumn.setJoins(List.of(
                join("STUDENT_DEGREE", "INNER", "STUDENT_DEGREE_ID", "1=1 OR 1=1")));
        assertThatThrownBy(() -> service.execute(withSqlColumn))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown column");
    }

    @Test
    @DisplayName("a table may be joined only once, and only so many times over")
    void join_limits() {
        QuerySpecDto twice = spec("STUDENT_DATA", "STUDENT_DATA.STUDENT_ID");
        twice.setJoins(List.of(
                join("STUDENT_DEGREE", "LEFT", "STUDENT_DEGREE_ID", "STUDENT_DEGREE_ID"),
                join("STUDENT_DEGREE", "LEFT", "STUDENT_DEGREE_ID", "STUDENT_DEGREE_ID")));
        assertThatThrownBy(() -> service.execute(twice))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already in this query");

        QuerySpecDto tooMany = spec("STUDENT_DATA", "STUDENT_DATA.STUDENT_ID");
        List<QuerySpecDto.Join> joins = new java.util.ArrayList<>();
        for (String table : List.of("STUDENT_DEGREE", "STUDENT_COUNTRY", "STUDENT_GOV", "STUDENT_MAJOR")) {
            joins.add(join(table, "LEFT", "STUDENT_DEGREE_ID", "STUDENT_DEGREE_ID"));
        }
        tooMany.setJoins(joins);
        assertThatThrownBy(() -> service.execute(tooMany))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("At most 3 joins");
    }

    // --------------------------------------------------- field masking (W5-03)

    /**
     * The query builder is the one report a user composes themselves, and its
     * rows are plain maps: no annotated field for the Jackson mask to wrap and
     * no bean for the report mask to read. Masking it in the execution covers
     * the grid on screen and the PDF alike, because both are this one result.
     */
    @Test
    @DisplayName("a column the caller may not read comes back empty, and the heading stays")
    void masks_theColumnsTheCallerMayNotRead() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_ID", "STUDENT_PASSPORT_NUMBER");

        QueryResultDto masked = com.dilaw.mohe.scope.AsUser.callWithAuthorities(
                com.dilaw.mohe.scope.AsUser.SUPERADMIN, List.of("REPORT_OTHER.VIEW"),
                () -> service.execute(spec));

        assertThat(masked.getRowCount()).as("the query still runs").isGreaterThan(0);
        assertThat(masked.getColumns()).extracting(QueryResultDto.Column::getName)
                .as("the heading tells the user the field exists and is not theirs to read")
                .contains("STUDENT_PASSPORT_NUMBER");
        assertThat(masked.getRows()).allSatisfy(row ->
                assertThat(row.get("STUDENT_PASSPORT_NUMBER")).isNull());
        assertThat(masked.getRows()).anySatisfy(row ->
                assertThat(row.get("STUDENT_ID")).as("the rest of the row is untouched").isNotNull());
    }

    @Test
    @DisplayName("and is returned in full to a caller who holds the code")
    void returns_theColumnToWhoeverMayReadIt() {
        QueryResultDto full = com.dilaw.mohe.scope.AsUser.callWithAuthorities(
                com.dilaw.mohe.scope.AsUser.SUPERADMIN,
                List.of("UMS.VIEW", "STUDENTS_ADD.ADD_STUDENT_PASSPORT.VIEW"),
                () -> service.execute(spec("STUDENT_DATA", "STUDENT_ID", "STUDENT_PASSPORT_NUMBER")));

        assertThat(full.getRows()).anySatisfy(row ->
                assertThat(row.get("STUDENT_PASSPORT_NUMBER")).isNotNull());
    }
}
