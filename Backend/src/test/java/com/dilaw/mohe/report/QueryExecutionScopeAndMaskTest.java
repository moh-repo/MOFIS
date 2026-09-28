package com.dilaw.mohe.report;

import com.dilaw.mohe.audit.ReportAccessLogService;
import com.dilaw.mohe.dto.JoinSuggestionDto;
import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.model.QueryBuilder;
import com.dilaw.mohe.security.field.FieldAccessService;
import com.dilaw.mohe.security.field.ReportFieldMask;
import com.dilaw.mohe.service.QueryBuilderService;
import com.dilaw.mohe.service.QueryExecutionService;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The query builder's masking, filter/sort refusal and join scoping, without a
 * database (RPT-02, RPT-03, RPT-04). {@link QueryExecutionServiceTest} covers the
 * same engine against SQL Server; these pin the rules with a mocked catalogue.
 */
class QueryExecutionScopeAndMaskTest {

    private final QueryBuilderService catalogue = mock(QueryBuilderService.class);
    private final ReportCountryScopeHelper scope = mock(ReportCountryScopeHelper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Map<String, List<JoinSuggestionDto>> suggestions = new HashMap<>();
    private QueryExecutionService service;

    @BeforeEach
    void setUp() {
        UmsPermissionRepository permissions = mock(UmsPermissionRepository.class);
        when(permissions.findActiveFieldPermissionCodes()).thenReturn(List.of());
        service = new QueryExecutionService(catalogue, scope, mock(ReportAccessLogService.class), jdbc);
        ReflectionTestUtils.setField(service, "reportFieldMask", new ReportFieldMask(new FieldAccessService(permissions)));

        table("STUDENT_DATA", "STUDENT_ID", "STUDENT_NAME", "STUDY_COUNTRY_IN", "STUDENT_MAJOR_ID",
                "STUDENT_PASSPORT_NUMBER");
        table("V_STUDENT_PROFILE_DOC", "STUDENT_ID", "STUDENT_NAME", "STUDENT_PASSPORT_NUMBER");
        // A person-level object with no study-country column of its own.
        table("V_PERSON_NO_COUNTRY", "STUDENT_ID", "STUDENT_NAME", "STUDENT_MAJOR_ID");
        // A person-level object that carries its own country column.
        table("STUDENT_LOAN", "STUDENT_ID", "STUDY_COUNTRY_IN_ID", "LOAN_AMOUNT");
        table("STUDENT_MAJOR", "STUDENT_MAJOR_ID", "STUDENT_MAJOR_NAME");
        when(catalogue.getJoinSuggestions(anyString()))
                .thenAnswer(i -> suggestions.getOrDefault(i.getArgument(0, String.class), List.of()));
        suggest("STUDENT_DATA", "STUDENT_ID", "V_PERSON_NO_COUNTRY", "STUDENT_ID");
        suggest("STUDENT_DATA", "STUDENT_MAJOR_ID", "V_PERSON_NO_COUNTRY", "STUDENT_MAJOR_ID");
        suggest("STUDENT_DATA", "STUDENT_ID", "STUDENT_LOAN", "STUDENT_ID");
        suggest("STUDENT_DATA", "STUDENT_MAJOR_ID", "STUDENT_MAJOR", "STUDENT_MAJOR_ID");

        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(i -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("STUDENT_ID", 1);
            row.put("STUDENT_NAME", "Ali");
            row.put("STUDENT_PASSPORT_NUMBER", "P1234567");
            return new ArrayList<>(List.of(row));
        });
        ministry();
        signedInHolding();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private void table(String name, String... columns) {
        List<QueryBuilder> cols = Arrays.stream(columns).map(c -> {
            QueryBuilder q = new QueryBuilder();
            q.setTableName(name);
            q.setColumnName(c);
            q.setDataType(c.endsWith("_ID") || c.equals("STUDY_COUNTRY_IN") ? "int" : "nvarchar");
            q.setIsFilterable(1);
            q.setIsSortable(1);
            return q;
        }).toList();
        when(catalogue.getColumnsByTableName(name)).thenReturn(cols);
    }

    private void suggest(String from, String fromColumn, String to, String toColumn) {
        JoinSuggestionDto dto = new JoinSuggestionDto() {
            public String getFromTable() { return from; }
            public String getFromColumn() { return fromColumn; }
            public String getToTable() { return to; }
            public String getToColumn() { return toColumn; }
            public String getKind() { return "NAME"; }
        };
        suggestions.computeIfAbsent(from, k -> new ArrayList<>()).add(dto);
    }

    private void ministry() {
        when(scope.resolveCountryIds(any(), any())).thenReturn(null);
        when(scope.shouldApplyCountryFilter(any())).thenAnswer(i -> i.getArgument(0) != null);
    }

    private void scopedTo(int countryId) {
        when(scope.resolveCountryIds(any(), any())).thenReturn(List.of(countryId));
    }

    private static void signedInHolding(String... codes) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("clerk", "n/a", codes));
    }

    private static QuerySpecDto spec(String source, String... columns) {
        QuerySpecDto spec = new QuerySpecDto();
        spec.setSource(source);
        spec.setColumns(List.of(columns));
        return spec;
    }

    private static QuerySpecDto.Join join(String table, String type, String leftColumn, String column) {
        QuerySpecDto.Join join = new QuerySpecDto.Join();
        join.setTable(table);
        join.setType(type);
        join.setLeftColumn(leftColumn);
        join.setColumn(column);
        return join;
    }

    private static QuerySpecDto.Filter like(String column, String value) {
        QuerySpecDto.Filter f = new QuerySpecDto.Filter();
        f.setColumn(column);
        f.setOperator("LIKE");
        f.setValue(value);
        return f;
    }

    private static QuerySpecDto.Sort sort(String column) {
        QuerySpecDto.Sort s = new QuerySpecDto.Sort();
        s.setColumn(column);
        s.setDirection("ASC");
        return s;
    }

    // ── RPT-02: masked through a view as through the table ──────────────────

    @Test
    @DisplayName("RPT-02: a passport read through a view is masked for a caller without the passport rule")
    void passportThroughAViewIsMasked() {
        QueryResultDto result = service.execute(spec("V_STUDENT_PROFILE_DOC", "STUDENT_NAME", "STUDENT_PASSPORT_NUMBER"));

        assertThat(result.getRows()).allSatisfy(row -> {
            assertThat(row.get("STUDENT_PASSPORT_NUMBER")).isNull();
            assertThat(row.get("STUDENT_NAME")).isEqualTo("Ali");
        });
    }

    @Test
    @DisplayName("RPT-02: the holder of the passport rule reads it through the view")
    void passportThroughAViewIsShownToItsHolder() {
        signedInHolding("UMS.VIEW");

        QueryResultDto result = service.execute(spec("V_STUDENT_PROFILE_DOC", "STUDENT_PASSPORT_NUMBER"));

        assertThat(result.getRows()).allSatisfy(row ->
                assertThat(row.get("STUDENT_PASSPORT_NUMBER")).isEqualTo("P1234567"));
    }

    @Test
    @DisplayName("RPT-02: a rule applies by column name on any object, and renamed passport columns take the passport rule")
    void rulesMatchByColumnName() {
        assertThat(ReportFieldMask.ruleForColumn("V_STUDENT_PROFILE_DOC", "STUDENT_PASSPORT_NUMBER")).isNotNull();
        assertThat(ReportFieldMask.ruleForColumn("V_Financial_Audit", "student_passport")).isNotNull();
        assertThat(ReportFieldMask.ruleForColumn("V_ANY", "PASSPORT_NO")).isNotNull();
        assertThat(ReportFieldMask.ruleForColumn("V_ANY", "STUDENT_PASSPORT_ISSUE_COUNTRY_ID"))
                .as("the issuing country is not the passport number").isNull();
        assertThat(ReportFieldMask.ruleForColumn("V_STUDENT_PROFILE_DOC", "STUDENT_NAME")).isNull();
    }

    // ── RPT-04: no filtering or sorting on what the caller may not read ─────

    @Test
    @DisplayName("RPT-04: filtering on a masked column is refused with 400")
    void filterOnAMaskedColumnIsRefused() {
        QuerySpecDto spec = spec("V_STUDENT_PROFILE_DOC", "STUDENT_NAME");
        spec.setFilters(List.of(like("STUDENT_PASSPORT_NUMBER", "123")));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("cannot be filtered on")
                .hasMessageContaining("not visible to you");
    }

    @Test
    @DisplayName("RPT-04: sorting on a masked column is refused with 400")
    void sortOnAMaskedColumnIsRefused() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_NAME");
        spec.setSorts(List.of(sort("STUDENT_PASSPORT_NUMBER")));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be sorted on");
    }

    @Test
    @DisplayName("RPT-04: the holder of the rule may filter and sort on it")
    void holderMayFilterAndSort() {
        signedInHolding("UMS.VIEW");
        QuerySpecDto spec = spec("V_STUDENT_PROFILE_DOC", "STUDENT_NAME");
        spec.setFilters(List.of(like("STUDENT_PASSPORT_NUMBER", "123")));
        spec.setSorts(List.of(sort("STUDENT_PASSPORT_NUMBER")));

        assertThat(service.execute(spec).getSql()).contains("LIKE ?").contains("ORDER BY");
    }

    // ── RPT-03: key joins only, and every person-level source scoped ────────

    @Test
    @DisplayName("RPT-03: a join on a column pair the join picker does not offer is refused")
    void arbitraryJoinPairIsRefused() {
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_DATA.STUDENT_ID");
        spec.setJoins(List.of(join("V_PERSON_NO_COUNTRY", "LEFT", "STUDENT_ID", "STUDENT_MAJOR_ID")));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not a key relationship");
    }

    @Test
    @DisplayName("RPT-03: a scoped user may not reach a country-less person source except through its student key")
    void personSourceJoinedOffTheStudentKeyIsRefused() {
        scopedTo(7);
        QuerySpecDto spec = spec("STUDENT_DATA", "V_PERSON_NO_COUNTRY.STUDENT_NAME");
        spec.setJoins(List.of(join("V_PERSON_NO_COUNTRY", "LEFT", "STUDENT_MAJOR_ID", "STUDENT_MAJOR_ID")));

        assertThatThrownBy(() -> service.execute(spec))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("V_PERSON_NO_COUNTRY");
    }

    @Test
    @DisplayName("RPT-03: joined on the student key, the person source is narrowed through the anchor")
    void personSourceJoinedOnTheStudentKeyIsScoped() {
        scopedTo(7);
        QuerySpecDto spec = spec("STUDENT_DATA", "V_PERSON_NO_COUNTRY.STUDENT_NAME");
        spec.setJoins(List.of(join("V_PERSON_NO_COUNTRY", "LEFT", "STUDENT_ID", "STUDENT_ID")));

        QueryResultDto result = service.execute(spec);

        assertThat(result.getSql()).contains("t0.[STUDY_COUNTRY_IN] IN (?)");
        assertThat(result.getScopeNote()).contains("STUDENT_DATA.STUDY_COUNTRY_IN");
    }

    @Test
    @DisplayName("RPT-03: a joined source with its own country column is narrowed on it in the ON clause")
    void joinedSourceWithItsOwnCountryIsNarrowedToo() {
        scopedTo(7);
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_LOAN.LOAN_AMOUNT");
        spec.setJoins(List.of(join("STUDENT_LOAN", "LEFT", "STUDENT_ID", "STUDENT_ID")));

        QueryResultDto result = service.execute(spec);

        String sql = result.getSql();
        // The last WHERE is the statement's own; the live-student check in the ON clause has one too.
        int where = sql.lastIndexOf(" WHERE ");
        assertThat(sql.substring(0, where)).as("ON clause").contains("t1.[STUDY_COUNTRY_IN_ID] IN (?)");
        assertThat(sql.substring(where)).as("WHERE clause").contains("t0.[STUDY_COUNTRY_IN] IN (?)");

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbc).queryForList(anyString(), params.capture());
        assertThat(params.getValue()).as("TOP, then the ON clause's id, then the WHERE clause's")
                .containsExactly(QueryExecutionServiceLimits.DEFAULT_PLUS_ONE, 7, 7);
    }

    @Test
    @DisplayName("RPT-03: a reference table joined on its key needs no country column")
    void referenceJoinIsAllowed() {
        scopedTo(7);
        QuerySpecDto spec = spec("STUDENT_DATA", "STUDENT_MAJOR.STUDENT_MAJOR_NAME");
        spec.setJoins(List.of(join("STUDENT_MAJOR", "LEFT", "STUDENT_MAJOR_ID", "STUDENT_MAJOR_ID")));

        assertThat(service.execute(spec).getSql()).contains("t0.[STUDY_COUNTRY_IN] IN (?)");
    }

    /** The row cap the engine asks for when the spec names none: its default plus one. */
    private static final class QueryExecutionServiceLimits {
        static final int DEFAULT_PLUS_ONE = 501;
    }
}
