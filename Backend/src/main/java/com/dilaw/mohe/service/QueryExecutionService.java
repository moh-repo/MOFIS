package com.dilaw.mohe.service;

import com.dilaw.mohe.audit.ReportAccessLogService;
import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.model.QueryBuilder;
import com.dilaw.mohe.security.field.ReportFieldMask;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import com.dilaw.mohe.util.LiveStudentSql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs a {@link QuerySpecDto} against MOHECORE.
 *
 * <p>The safety of this class rests on one rule: <strong>nothing the caller
 * sends is ever placed into the statement as text.</strong> Identifiers are
 * matched against the schema catalogue and re-emitted from the catalogue's own
 * spelling; values are bound as parameters. A caller cannot name an object the
 * catalogue does not list, cannot name a column the catalogue does not list for
 * that object, and cannot supply an operator or a join type outside the sets
 * declared here.
 *
 * <p>That matters more than usual here: the application's connection is a member
 * of {@code db_owner}, so a statement that escaped validation could write. The
 * engine composes only {@code SELECT}, but see
 * {@code V025__create_query_engine_readonly_login.sql} for the database-side
 * boundary that makes the worst case a failed query rather than a changed row.
 */
@Service
public class QueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutionService.class);

    /** Rows returned when the caller does not say, and the ceiling it may ask for. */
    static final int DEFAULT_LIMIT = 500;
    static final int MAX_LIMIT = 5_000;
    static final int MAX_COLUMNS = 60;
    static final int MAX_FILTERS = 20;
    /**
     * Joins allowed in one query.
     *
     * <p>Low on purpose. Every join multiplies the work the database does, and
     * this engine hands its statement to a general-purpose connection with a
     * 30-second timeout; four sources is enough to answer a reporting question
     * and few enough that a careless join cannot become an outage.
     */
    static final int MAX_JOINS = 3;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /**
     * Objects a composed query may not read.
     *
     * <p>Identity and session records, the audit trail, and the notification
     * configuration are operational data rather than reporting data, and a
     * general query facility is the wrong door to them. Staging tables hold
     * half-loaded rows.
     *
     * <p>{@code V_Financial_Audit} was excluded for a reason that no longer
     * holds: it manufactured its content, naming a 'System Auditor' and emitting
     * 'Discrepancy found' against real students. V034 removed those literals,
     * V056 corrected the row filter and V078 closed the remaining placeholders —
     * verified against the live definition on 1 Sep 2026, which carries none of
     * the three. It stays on this list because it is still person-level
     * ({@code student_name}, {@code student_passport}) with no study-country
     * column, so the rule below would refuse it in any case; the entry is now
     * belt and braces rather than the ban it was. Lifting it is the owner's
     * decision, and would want a country column on the view first.
     *
     * <p>Checked for every source in the query, not only the root — otherwise a
     * join would be the way round it.
     */
    private static final Set<String> DENIED_SOURCES = Set.of(
            "AUDIT_LOG",
            "VW_AUDIT_LOG_SUMMARY",
            "ROLES",
            "StudentPortalAccount",
            "StudentPortalSession",
            "NOTIFICATION_SETTINGS",
            "V_Financial_Audit");

    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "GT", "GE", "LT", "LE", "LIKE", "IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL");

    /**
     * Join types allowed.
     *
     * <p>INNER and LEFT only. RIGHT is LEFT with the sources the other way
     * round, and FULL/CROSS produce row counts a reporting page cannot use.
     */
    private static final Set<String> JOIN_TYPES = Set.of("INNER", "LEFT");

    /** Study-destination columns, which is what report country scope filters on. */
    private static final List<String> COUNTRY_COLUMNS =
            List.of("STUDY_COUNTRY_IN", "STUDY_COUNTRY_IN_ID", "study_country_in_id");

    /**
     * Column names that mark a row as being about a person.
     *
     * <p>Country scope answers one question: which students may this user see.
     * An object that holds no students therefore has nothing to narrow — the
     * lookup tables, the quarters, the status lists. Of the 181 objects in the
     * catalogue only 16 carry a study-country column, so treating "cannot be
     * narrowed" as "must be refused" shut a scoped user out of every reference
     * table in the schema, which is what {@code DOCUMENT_TYPES} was.
     *
     * <p>The distinction is drawn from column names, and drawn to fail closed:
     * an object whose columns merely <em>look</em> person-level is refused. That
     * is why the passport rule is a substring while the student rule is not —
     * {@code STUDENT_DEGREE_NAME} names a degree, {@code STUDENT_NAME} names a
     * student. {@link com.dilaw.mohe.report.QueryExecutionServiceTest} checks
     * this partition against the schema's own foreign keys, so an object added
     * later that holds students without a country column fails the build rather
     * than quietly becoming readable.
     *
     * <p>Compared with separators stripped, because this schema spells the same
     * foreign key both ways: {@code STUDENT_ID} in the older tables and
     * {@code StudentId} in the newer ones. Matching only the first let
     * {@code StudentInquiry}, {@code StudentInfoRequest} and
     * {@code STUDENT_YEAR_RESULT} — inquiries, grades, rejection reasons —
     * through unscoped.
     */
    private static final Set<String> PERSON_COLUMNS = Set.of(
            "studentdataid", "studentname", "studentemail",
            "studentcellphone", "studentbirthdate");

    private final QueryBuilderService catalogService;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final ReportAccessLogService reportAccessLogService;
    private final JdbcTemplate jdbcTemplate;

    /** W5-03: the columns the caller may not read, blanked before the result leaves. */
    @org.springframework.beans.factory.annotation.Autowired
    private ReportFieldMask reportFieldMask;

    public QueryExecutionService(QueryBuilderService catalogService,
                                 ReportCountryScopeHelper countryScopeHelper,
                                 ReportAccessLogService reportAccessLogService,
                                 @Qualifier("queryEngineJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.catalogService = catalogService;
        this.countryScopeHelper = countryScopeHelper;
        this.reportAccessLogService = reportAccessLogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------------- sources

    /**
     * One object in the FROM clause: what the catalogue calls it, the alias it
     * is given here, and the columns it is allowed to contribute.
     *
     * <p>The alias is generated ({@code t0}, {@code t1}, …) and never taken from
     * the caller, so two sources can carry the same column name without either
     * the statement or the result being ambiguous.
     */
    private static final class SourceRef {
        private final String table;
        private final String alias;
        private final List<QueryBuilder> ordered;
        private final Map<String, QueryBuilder> byName;

        private SourceRef(String table, String alias, List<QueryBuilder> ordered) {
            this.table = table;
            this.alias = alias;
            this.ordered = ordered;
            this.byName = ordered.stream().collect(Collectors.toMap(
                    c -> c.getColumnName().toLowerCase(Locale.ROOT), c -> c, (a, b) -> a, LinkedHashMap::new));
        }

        private QueryBuilder column(String name) {
            return name == null ? null : byName.get(name.trim().toLowerCase(Locale.ROOT));
        }

        private String from() {
            return bracket("MOHECORE") + "." + bracket(table) + " " + alias;
        }
    }

    /** A column resolved to the source it belongs to. */
    private record ColumnRef(SourceRef source, QueryBuilder column) {

        /** How the column is written in the statement. */
        private String qualified() {
            return source.alias + "." + bracket(column.getColumnName());
        }

        /**
         * How the column is named in the result.
         *
         * <p>Qualified once a query has joins, because two joined objects may
         * both carry {@code STUDENT_ID} and a result whose keys collide would
         * silently drop one of them. A single-source query keeps bare names, so
         * queries saved before joins existed return what they always did.
         */
        private String outputName(boolean joined) {
            return joined ? source.table + "." + column.getColumnName() : column.getColumnName();
        }
    }

    @Transactional(readOnly = true)
    public QueryResultDto execute(QuerySpecDto spec) {
        countryScopeHelper.ensureScopeOrThrow();

        if (spec == null || isBlank(spec.getSource())) {
            throw badRequest("A source table or view is required");
        }

        List<QuerySpecDto.Join> joins = spec.getJoins() == null ? List.of() : spec.getJoins();
        List<SourceRef> sources = resolveSources(spec.getSource(), joins);
        boolean joined = sources.size() > 1;

        List<ColumnRef> selected = resolveSelectedColumns(spec, sources);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();

        int limit = clampLimit(spec.getLimit());
        sql.append("SELECT TOP (?) ");
        params.add(limit + 1); // one extra row, purely to detect truncation
        sql.append(selected.stream()
                .map(c -> joined
                        ? c.qualified() + " AS " + bracket(c.outputName(true))
                        : c.qualified())
                .collect(Collectors.joining(", ")));
        sql.append(buildFrom(sources, joins));

        List<String> predicates = new ArrayList<>();
        String rootLive = liveStudentCondition(sources.get(0));
        if (rootLive != null) {
            predicates.add(rootLive);
        }
        appendFilters(spec, sources, predicates, params);
        String scopeNote = appendCountryScope(spec, sources, predicates, params);

        if (!predicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
        }
        appendSorts(spec, sources, sql);

        return run(sql.toString(), params, selected, joined, limit, sources, scopeNote);
    }

    /**
     * Resolves the root source and every joined source through the catalogue.
     *
     * <p>Names that reach the statement are the catalogue's spelling, never the
     * caller's, and every source is checked against {@link #DENIED_SOURCES} —
     * a join must not become the way into an object the root cannot reach.
     */
    private List<SourceRef> resolveSources(String root, List<QuerySpecDto.Join> joins) {
        if (joins.size() > MAX_JOINS) {
            throw badRequest("At most " + MAX_JOINS + " joins may be used in one query");
        }
        List<SourceRef> sources = new ArrayList<>();
        sources.add(loadSource(root, "t0"));

        for (int i = 0; i < joins.size(); i++) {
            QuerySpecDto.Join join = joins.get(i);
            if (join == null || isBlank(join.getTable())) {
                throw badRequest("A join needs a table to join to");
            }
            SourceRef right = loadSource(join.getTable(), "t" + (i + 1));
            for (SourceRef existing : sources) {
                if (existing.table.equalsIgnoreCase(right.table)) {
                    // Supporting this would mean letting the caller name aliases,
                    // and an alias the caller names is text the caller controls.
                    throw badRequest("'" + right.table + "' is already in this query; "
                            + "a table may be joined only once");
                }
            }
            sources.add(right);
        }
        return sources;
    }

    private SourceRef loadSource(String name, String alias) {
        List<QueryBuilder> catalogue = catalogService.getColumnsByTableName(name.trim());
        if (catalogue.isEmpty()) {
            throw badRequest("Unknown source: " + name);
        }
        String table = catalogue.get(0).getTableName();
        if (DENIED_SOURCES.contains(table)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This source is not available to the query builder: " + table);
        }
        return new SourceRef(table, alias, catalogue);
    }

    /** The FROM clause, with one JOIN per entry in the spec, in the given order. */
    private String buildFrom(List<SourceRef> sources, List<QuerySpecDto.Join> joins) {
        StringBuilder from = new StringBuilder(" FROM ").append(sources.get(0).from());

        for (int i = 0; i < joins.size(); i++) {
            QuerySpecDto.Join join = joins.get(i);
            SourceRef right = sources.get(i + 1);

            String type = join.getType() == null ? "INNER"
                    : join.getType().trim().toUpperCase(Locale.ROOT);
            if (!JOIN_TYPES.contains(type)) {
                throw badRequest("A join is INNER or LEFT, not " + join.getType());
            }

            // The left side must already be in the FROM clause: the root, or a
            // source joined before this one. Otherwise the ON clause would
            // reference an alias the statement has not introduced yet.
            SourceRef left;
            if (isBlank(join.getLeftTable())) {
                left = sources.get(0);
            } else {
                left = sources.subList(0, i + 1).stream()
                        .filter(s -> s.table.equalsIgnoreCase(join.getLeftTable().trim()))
                        .findFirst()
                        .orElseThrow(() -> badRequest("'" + join.getLeftTable() + "' cannot be joined "
                                + "to, because it does not come earlier in this query"));
            }

            QueryBuilder leftColumn = requireColumnIn(left, join.getLeftColumn());
            QueryBuilder rightColumn = requireColumnIn(right, join.getColumn());

            from.append(' ').append(type).append(" JOIN ").append(right.from())
                    .append(" ON ").append(right.alias).append('.').append(bracket(rightColumn.getColumnName()))
                    .append(" = ").append(left.alias).append('.').append(bracket(leftColumn.getColumnName()));
            // In the ON clause, so a LEFT JOIN still keeps the row it joins from.
            String live = liveStudentCondition(right);
            if (live != null) {
                from.append(" AND ").append(live);
            }
        }
        return from.toString();
    }

    private static String liveStudentCondition(SourceRef source) {
        return liveStudentCondition(source.table, source.alias, source.byName.values().stream()
                .map(QueryBuilder::getColumnName).toList());
    }

    /**
     * Deleted students stay out of a query's result, as out of every report (live QA
     * H7, D5-a): the student table by its IS_DELETED flag, the finance table through its
     * student, and since FIX-PLAN P5-11 every other table or view with a student key
     * ({@code STUDENT_ID} / {@code StudentId}) through that key — a row that names no
     * student stays. It used to be the first two only, so loans, extensions, payments and
     * every student view came back with deleted students. The archives ({@code *_ARCH})
     * are left whole: they are the record of what was paid and held (decision of 26 Sep
     * 2026). Null when the source holds no student.
     */
    static String liveStudentCondition(String table, String alias, java.util.Collection<String> columns) {
        if ("STUDENT_DATA".equalsIgnoreCase(table)) {
            return LiveStudentSql.notDeleted(alias);
        }
        if ("STUDENT_FINANCE".equalsIgnoreCase(table)) {
            return LiveStudentSql.ofLiveStudent(alias);
        }
        if (table.toUpperCase(Locale.ROOT).endsWith("_ARCH")) {
            return null;
        }
        return columns.stream()
                .filter(c -> c != null && c.replace("_", "").equalsIgnoreCase("studentid"))
                .findFirst()
                .map(c -> LiveStudentSql.noneOrLiveStudent(alias, c))
                .orElse(null);
    }

    // ---------------------------------------------------------------- columns

    private List<ColumnRef> resolveSelectedColumns(QuerySpecDto spec, List<SourceRef> sources) {
        if (spec.getColumns() == null || spec.getColumns().isEmpty()) {
            // Everything the query can see, capped. With joins that is the union
            // across sources, which is why the cap matters more here.
            List<ColumnRef> all = new ArrayList<>();
            for (SourceRef source : sources) {
                for (QueryBuilder column : source.ordered) {
                    if (all.size() == MAX_COLUMNS) {
                        return all;
                    }
                    all.add(new ColumnRef(source, column));
                }
            }
            return all;
        }
        if (spec.getColumns().size() > MAX_COLUMNS) {
            throw badRequest("At most " + MAX_COLUMNS + " columns may be selected");
        }
        List<ColumnRef> selected = new ArrayList<>();
        for (String requested : spec.getColumns()) {
            selected.add(resolveColumn(sources, requested));
        }
        return selected;
    }

    /**
     * Resolves a column reference, qualified or bare, to exactly one source.
     *
     * <p>A bare name that several sources carry is refused rather than guessed.
     * Guessing would mean a joined query quietly returning the right column name
     * from the wrong table, which is the kind of wrong answer a report carries
     * without anyone noticing.
     */
    private ColumnRef resolveColumn(List<SourceRef> sources, String reference) {
        if (isBlank(reference)) {
            throw badRequest("Unknown column: " + reference);
        }
        String trimmed = reference.trim();
        int dot = trimmed.lastIndexOf('.');

        if (dot > 0 && dot < trimmed.length() - 1) {
            String tableName = trimmed.substring(0, dot);
            String columnName = trimmed.substring(dot + 1);
            SourceRef source = sources.stream()
                    .filter(s -> s.table.equalsIgnoreCase(tableName))
                    .findFirst()
                    .orElseThrow(() -> badRequest("'" + tableName + "' is not a source in this query"));
            return new ColumnRef(source, requireColumnIn(source, columnName));
        }

        List<ColumnRef> matches = sources.stream()
                .filter(s -> s.column(trimmed) != null)
                .map(s -> new ColumnRef(s, s.column(trimmed)))
                .toList();

        if (matches.isEmpty()) {
            // Names the catalogue does not list include credential columns, which
            // it deliberately omits — so this is also what keeps them unreadable.
            throw badRequest("Unknown column: " + reference);
        }
        if (matches.size() > 1) {
            throw badRequest("'" + trimmed + "' is in " + matches.size() + " of the tables in this "
                    + "query (" + matches.stream().map(m -> m.source().table).collect(Collectors.joining(", "))
                    + "); name it as TABLE." + trimmed);
        }
        return matches.get(0);
    }

    private QueryBuilder requireColumnIn(SourceRef source, String name) {
        QueryBuilder column = source.column(name);
        if (column == null) {
            throw badRequest("Unknown column: " + name + " (in " + source.table + ")");
        }
        return column;
    }

    // ---------------------------------------------------------------- filters

    private void appendFilters(QuerySpecDto spec, List<SourceRef> sources,
                               List<String> predicates, List<Object> params) {
        if (spec.getFilters() == null || spec.getFilters().isEmpty()) {
            return;
        }
        if (spec.getFilters().size() > MAX_FILTERS) {
            throw badRequest("At most " + MAX_FILTERS + " filters may be applied");
        }

        for (QuerySpecDto.Filter filter : spec.getFilters()) {
            ColumnRef ref = resolveColumn(sources, filter.getColumn());
            QueryBuilder column = ref.column();
            if (column.getIsFilterable() != null && column.getIsFilterable() == 0) {
                throw badRequest("Column cannot be filtered on: " + column.getColumnName()
                        + " (" + column.getDataType() + ")");
            }
            String operator = filter.getOperator() == null ? ""
                    : filter.getOperator().trim().toUpperCase(Locale.ROOT);
            if (!OPERATORS.contains(operator)) {
                throw badRequest("Unsupported operator: " + filter.getOperator());
            }
            String col = ref.qualified();

            switch (operator) {
                case "IS_NULL" -> predicates.add(col + " IS NULL");
                case "IS_NOT_NULL" -> predicates.add(col + " IS NOT NULL");
                case "IN" -> {
                    List<String> values = filter.getValues();
                    if (values == null || values.isEmpty()) {
                        throw badRequest("IN needs at least one value for " + column.getColumnName());
                    }
                    predicates.add(col + " IN ("
                            + values.stream().map(v -> "?").collect(Collectors.joining(", ")) + ")");
                    values.forEach(v -> params.add(coerce(column, v)));
                }
                case "BETWEEN" -> {
                    requireValue(filter.getValue(), column);
                    requireValue(filter.getValue2(), column);
                    predicates.add(col + " BETWEEN ? AND ?");
                    params.add(coerce(column, filter.getValue()));
                    params.add(coerce(column, filter.getValue2()));
                }
                case "LIKE" -> {
                    requireValue(filter.getValue(), column);
                    // Contains, with the wildcards added here so a value cannot
                    // shape the pattern beyond that.
                    predicates.add("CAST(" + col + " AS NVARCHAR(4000)) LIKE ?");
                    params.add("%" + filter.getValue().trim() + "%");
                }
                default -> {
                    requireValue(filter.getValue(), column);
                    predicates.add(col + " " + comparison(operator) + " ?");
                    params.add(coerce(column, filter.getValue()));
                }
            }
        }
    }

    private static String comparison(String operator) {
        return switch (operator) {
            case "EQ" -> "=";
            case "NE" -> "<>";
            case "GT" -> ">";
            case "GE" -> ">=";
            case "LT" -> "<";
            case "LE" -> "<=";
            default -> throw badRequest("Unsupported operator: " + operator);
        };
    }

    private void requireValue(String value, QueryBuilder column) {
        if (isBlank(value)) {
            throw badRequest("A value is required for " + column.getColumnName());
        }
    }

    /**
     * Binds the value in the column's own type where that is unambiguous.
     *
     * <p>Left as a string otherwise, so SQL Server converts it — a wrong value
     * then fails the query rather than silently matching nothing.
     */
    private Object coerce(QueryBuilder column, String value) {
        String type = column.getDataType() == null ? "" : column.getDataType().toLowerCase(Locale.ROOT);
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null) {
            return null;
        }
        try {
            return switch (type) {
                case "int", "smallint", "tinyint" -> Integer.valueOf(trimmed);
                case "bigint" -> Long.valueOf(trimmed);
                case "bit" -> parseBit(trimmed);
                case "decimal", "numeric", "money", "smallmoney", "float", "real" ->
                        new java.math.BigDecimal(trimmed);
                default -> trimmed;
            };
        } catch (NumberFormatException ex) {
            throw badRequest("'" + value + "' is not valid for "
                    + column.getColumnName() + " (" + column.getDataType() + ")");
        }
    }

    private static Boolean parseBit(String value) {
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) return Boolean.FALSE;
        throw badRequest("'" + value + "' is not a valid true/false value");
    }

    // ------------------------------------------------------------------ scope

    /**
     * Applies the caller's country scope across every source in the query.
     *
     * <p>A general query facility would otherwise be a way around the scoping
     * every report enforces. The predicate goes on the first study-destination
     * column any source carries, which is what makes joining useful to a scoped
     * user: {@code STUDENT_LOAN} alone cannot be narrowed and is refused, but
     * joined to {@code STUDENT_DATA} it inherits that table's country column and
     * the whole query is scoped through it.
     *
     * <p>Where no source carries one, what happens depends on whether any source
     * holds people: reference data is served as it stands, because there is
     * nothing in it to narrow, while a query that reaches student records with
     * no way to filter them is refused.
     *
     * @return a note for the reader about how scope was applied, else null
     */
    private String appendCountryScope(QuerySpecDto spec, List<SourceRef> sources,
                                      List<String> predicates, List<Object> params) {
        List<Integer> countryIds =
                countryScopeHelper.resolveCountryIds(spec.getCountryId(), spec.getCountryIds());
        if (!countryScopeHelper.shouldApplyCountryFilter(countryIds)) {
            return null;
        }

        ColumnRef countryColumn = null;
        for (SourceRef source : sources) {
            for (String candidate : COUNTRY_COLUMNS) {
                QueryBuilder column = source.column(candidate);
                if (column != null) {
                    countryColumn = new ColumnRef(source, column);
                    break;
                }
            }
            if (countryColumn != null) {
                break;
            }
        }

        if (countryColumn == null) {
            ColumnRef person = sources.stream()
                    .flatMap(s -> s.ordered.stream().map(c -> new ColumnRef(s, c)))
                    .filter(c -> identifiesAPerson(c.column().getColumnName()))
                    .findFirst()
                    .orElse(null);
            if (person != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "'" + person.source().table + "' holds student records ("
                                + person.column().getColumnName() + ") but no table in this query "
                                + "carries a study-country column, so it cannot be narrowed to your "
                                + "countries. Join a table that carries STUDY_COUNTRY_IN.");
            }
            // Reference data: no students in it, so the scope has nothing to act on.
            return sources.stream().map(s -> "'" + s.table + "'").collect(Collectors.joining(", "))
                    + (sources.size() > 1 ? " are" : " is") + " reference data and hold"
                    + (sources.size() > 1 ? "" : "s") + " no student records, so the result is shown "
                    + "in full rather than narrowed to your countries";
        }

        List<Integer> safe = countryIds.isEmpty() ? List.of(-1) : countryIds;
        predicates.add(countryColumn.qualified() + " IN ("
                + safe.stream().map(id -> "?").collect(Collectors.joining(", ")) + ")");
        params.addAll(safe);
        return "Narrowed to your country scope on " + countryColumn.source().table
                + "." + countryColumn.column().getColumnName();
    }

    /**
     * Whether a column name says its row is about a person.
     *
     * <p>Deliberately generous on passports — anyone with a passport number in a
     * row is a person, whoever they are — and deliberately exact on the student
     * keys, because half the lookup tables in this schema are named
     * {@code STUDENT_*}. {@code STUDENT_DEGREE_ID} names a degree and must stay
     * queryable; {@code studentid} in any spelling names a student and must not.
     */
    static boolean identifiesAPerson(String columnName) {
        if (columnName == null) {
            return false;
        }
        String name = columnName.toLowerCase(Locale.ROOT).replace("_", "");
        return PERSON_COLUMNS.contains(name)
                || name.contains("passport")
                // The student key itself, the STUDENT_ID_STATUS_nn columns the
                // payment views carry, and any table qualifying it with a prefix.
                || name.startsWith("studentid")
                || name.endsWith("studentid");
    }

    // ------------------------------------------------------------------ sorts

    private void appendSorts(QuerySpecDto spec, List<SourceRef> sources, StringBuilder sql) {
        if (spec.getSorts() == null || spec.getSorts().isEmpty()) {
            return;
        }
        List<String> clauses = new ArrayList<>();
        for (QuerySpecDto.Sort sort : spec.getSorts()) {
            ColumnRef ref = resolveColumn(sources, sort.getColumn());
            QueryBuilder column = ref.column();
            if (column.getIsSortable() != null && column.getIsSortable() == 0) {
                throw badRequest("Column cannot be sorted on: " + column.getColumnName()
                        + " (" + column.getDataType() + ")");
            }
            String direction = sort.getDirection() == null ? "ASC"
                    : sort.getDirection().trim().toUpperCase(Locale.ROOT);
            if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                throw badRequest("Sort direction must be ASC or DESC, not " + sort.getDirection());
            }
            clauses.add(ref.qualified() + " " + direction);
        }
        sql.append(" ORDER BY ").append(String.join(", ", clauses));
    }

    // ------------------------------------------------------------------- run

    private QueryResultDto run(String sql, List<Object> params, List<ColumnRef> selected,
                               boolean joined, int limit, List<SourceRef> sources, String scopeNote) {
        List<Map<String, Object>> rows;
        try {
            jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            rows = jdbcTemplate.queryForList(sql, params.toArray());
        } catch (Exception ex) {
            log.warn("Query builder execution failed on {}: {}", sources.get(0).table, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The query could not be run: " + rootMessage(ex));
        }

        boolean truncated = rows.size() > limit;
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }

        maskHiddenColumns(rows, selected, joined);

        String sourceNames = sources.stream().map(s -> s.table).collect(Collectors.joining("+"));
        reportAccessLogService.logExport("QUERY_BUILDER", "EXECUTE",
                "source=" + sourceNames + ",columns=" + selected.size()
                        + ",rows=" + rows.size() + (truncated ? ",truncated" : ""));

        QueryResultDto result = new QueryResultDto();
        result.setColumns(selected.stream()
                .map(c -> new QueryResultDto.Column(c.outputName(joined), c.column().getDataType()))
                .collect(Collectors.toList()));
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setTruncated(truncated);
        result.setSql(sql);
        result.setParameters(params);
        result.setScopeNote(scopeNote);
        return result;
    }

    /**
     * Blanks the selected columns this caller may not see (plan W5-03).
     *
     * <p>The query builder is the one report a user composes themselves: it can
     * select {@code STUDENT_DATA.STUDENT_PASSPORT_NUMBER} as readily as a name,
     * and its rows are plain maps, so neither the Jackson mask (no annotated
     * field to wrap) nor the bean mask (no bean) reaches them. Masking here
     * covers the grid on screen and the PDF the same way, because both are this
     * one result.
     *
     * <p>The value is removed rather than the column: a column that vanished
     * between running a saved query and running it again would look like the
     * query had changed, and the header is what tells the user the field exists
     * and is not theirs to read.
     */
    private void maskHiddenColumns(List<Map<String, Object>> rows, List<ColumnRef> selected, boolean joined) {
        if (rows.isEmpty()) return;
        ReportFieldMask.Viewer viewer = reportFieldMask.viewer();
        List<String> hidden = new ArrayList<>();
        for (ColumnRef ref : selected) {
            if (!reportFieldMask.mayViewColumn(ref.source().table, ref.column().getColumnName(), viewer)) {
                hidden.add(ref.outputName(joined));
            }
        }
        if (hidden.isEmpty()) return;
        for (Map<String, Object> row : rows) {
            for (String name : hidden) {
                if (row.containsKey(name)) row.put(name, null);
            }
        }
        log.info("Query builder: {} column(s) masked for this user: {}", hidden.size(), String.join(", ", hidden));
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    /**
     * Quotes an identifier that has already been matched against the catalogue.
     *
     * <p>The escape is belt and braces: a name reaching here came from
     * {@code sys.columns}, not from the caller. It stays because the cost is
     * nothing and the assumption is the kind that quietly stops being true.
     */
    private static String bracket(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
