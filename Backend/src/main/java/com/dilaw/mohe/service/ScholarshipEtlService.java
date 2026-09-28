package com.dilaw.mohe.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.dilaw.mohe.service.ScholarshipDataImportService.normaliseArabic;

/**
 * Moves an imported quarterly statement out of the landing table and into
 * {@code STUDENT_DATA_STAGING}.
 *
 * <p>This is the T in ETL and nothing else. It reads
 * {@code STUDENT_SCHOLARSHIP_DATA_IMPORT}, which holds the sheet as the
 * attaché wrote it — Arabic free text, one row per student line — and writes
 * rows shaped like the operational student table, with the text resolved to
 * lookup ids. It never touches the landing table: that is the archive, and the
 * only way to correct a bad load is to be able to run this again from it.
 *
 * <h2>Why almost every column is written explicitly</h2>
 *
 * <p>{@code STUDENT_DATA_STAGING} carries {@code DEFAULT ((0))} on sixteen
 * foreign-key columns whose target lookups have no row with id 0. Any insert
 * that lets those defaults fire fails the foreign key. Two others are worse
 * than that — they succeed and mean something:
 *
 * <ul>
 *   <li>{@code STUDENT_STUDY_STATUS_ID = 0} is <strong>متوفى</strong>, deceased.
 *       Defaulting it would mark every student in the batch dead.</li>
 *   <li>{@code STUDY_EDUCATION_IN = 0} is a real specialization
 *       (بكالوريوس هندسة إلكترونية واتصالات), not an "unknown" sentinel.</li>
 * </ul>
 *
 * <p>So the constants below name a deliberate value for every column the
 * sheet has no data for, and the insert lists all of them. Nothing is left to
 * the table's own defaults. The values follow what the live
 * {@code STUDENT_DATA} actually stores: NULL where the lookup has no
 * "غير مبين" row, and the lookup's own sentinel where it has one — which is 99
 * in some lookups and 999 in others.
 */
@Service
public class ScholarshipEtlService {

    private static final Logger log = LoggerFactory.getLogger(ScholarshipEtlService.class);

    private static final String SOURCE = "MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT";
    private static final String TARGET = "MOHECORE.STUDENT_DATA_STAGING";
    private static final String ALIAS = "MOHECORE.ETL_LOOKUP_ALIAS";

    /** Yemen. These are Yemeni students abroad; the sheet never states it. */
    private static final int NATIONALITY_YEMEN = 1;

    /** جديد — a freshly staged row has not been triaged yet. Never 0: that is متوفى. */
    private static final int STUDY_STATUS_NEW = 2;

    /** ENTITY prefixes resolve to these; see {@link #agencyTypeFor}. */
    private static final int AGENCY_UNIVERSITY = 1;   // جامعة
    private static final int AGENCY_UNDERGRAD = 2;    // جامعية
    private static final int AGENCY_POSTGRAD = 3;     // دراسات عليا
    private static final int AGENCY_MINISTRY = 4;     // وزارة

    /*
     * What to write where the sheet says nothing.
     *
     * Read these as a decision per column, not as filler. Each one was checked
     * against the lookup it points at: a sentinel that does not exist is a
     * foreign-key violation, and a sentinel that exists but means something is
     * worse. They follow what the live STUDENT_DATA actually stores.
     *
     * null means "write SQL NULL" — which these lookups have no better answer
     * for, having no غير مبين row at all, and which is what 937-940 of the 941
     * live student rows hold.
     */
    private static final Integer NO_GOVERNORATE = null;          // STUDENT_GOV_ID
    private static final Integer NO_DISTRICT = null;             // STUDENT_MUD_ID
    private static final Integer NO_GENDER = null;               // STUDENT_GENDER
    private static final Integer NO_UNIVERSITY_TYPE = null;      // live: NULL in 793 of 941
    private static final Integer NO_PASSPORT_COUNTRY = null;     // live: NULL in 939 of 941
    private static final Integer NO_STUDY_MAJOR_IN = null;       // live: NULL in 939 of 941

    /* These lookups do carry a غير مبين row, so the sentinel is a real id. It
     * is 999 in each of them — 99 in these five tables is an ordinary value. */
    private static final int UNKNOWN_STUDY_TYPE = 999;
    private static final int UNKNOWN_UNIVERSITY_IN = 999;
    private static final int UNKNOWN_COLLEGE_IN = 999;
    private static final int UNKNOWN_GOV_IN = 999;
    /* Not 0. STUDY_EDUCTION_IN id 0 is بكالوريوس هندسة إلكترونية واتصالات, a
     * real specialization that 937 live rows carry only because the column
     * default fired on them. */
    private static final int UNKNOWN_EDUCATION_IN = 999;

    /* No foreign key behind either of these, so 0 is safe and is what the
     * column defaults to anyway. */
    private static final int NO_UNIVERSITY_FEE = 0;

    /** A lookup the resolver can map Arabic text onto. */
    private record Lookup(String key, String table, String idColumn, String nameColumn, int unknownId) {}

    private static final List<Lookup> LOOKUPS = List.of(
            new Lookup("DEGREE", "STUDENT_DEGREE", "STUDENT_DEGREE_ID", "STUDENT_DEGREE_NAME", 99),
            new Lookup("MAJOR", "STUDENT_MAJOR", "STUDENT_MAJOR_ID", "STUDENT_MAJOR_NAME", 99),
            new Lookup("AGENCY_TYPE", "STUDENT_AGENCY_TYPE", "STUDENT_AGENCY_TYPE_ID", "STUDENT_AGENCY_TYPE_NAME", 99),
            new Lookup("MINISTRY_TYPE", "STUDENT_MINISTRY_TYPE", "STUDENT_MINISTRY_TYPE_ID", "STUDENT_MINISTRY_TYPE_NAME", 99),
            new Lookup("CURRENT_LEVEL", "STUDENT_CURRENT_LEVEL", "STUDENT_CURRENT_LEVEL_ID", "STUDENT_CURRENT_LEVEL_NAME", 99));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ────────────────────────────────────────────────────────────── the load ──

    /**
     * Works out what the load would do, and writes nothing.
     *
     * <p>Runs the identical resolution pass {@link #run} does, so the counts
     * and the worklist on the preview screen are the counts and the worklist
     * of the load itself, not an estimate of them.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Integer countryId) {
        Resolution resolution = resolve(countryId);
        int preserved = countryId == null ? 0 : editedCount(countryId);
        // The preview has to say the same thing the run will do, including
        // which rows it is going to leave alone.
        return report(resolution, 0, false, resolution.rows.size() - preserved, preserved);
    }

    private int editedCount(Integer countryId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TARGET + " WHERE STUDY_COUNTRY_IN = ? AND IS_EDITED = 1",
                Integer.class, countryId);
        return n == null ? 0 : n;
    }

    /**
     * Replaces this country's staging rows with a freshly resolved load.
     *
     * <p>The delete and the insert are one transaction, and the delete is
     * scoped by {@code STUDY_COUNTRY_IN} — the same column the load writes the
     * country into — so a run for Egypt cannot empty Malaysia's rows. The
     * landing table is not touched by either.
     */
    @Transactional
    public Map<String, Object> run(Integer countryId, Long userId) {
        if (countryId == null || countryId <= 0) {
            throw new IllegalArgumentException("Choose the country to stage.");
        }
        Resolution resolution = resolve(countryId);
        if (resolution.rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "There is nothing imported for this country to stage.");
        }

        /*
         * Machine-generated rows only. A row somebody has corrected by hand
         * survives the run — see V053 — otherwise the edit screen would hand
         * back work that the next quarterly run silently deleted.
         */
        int deleted = jdbcTemplate.update(
                "DELETE FROM " + TARGET + " WHERE STUDY_COUNTRY_IN = ? "
                        + "AND (IS_EDITED = 0 OR IS_EDITED IS NULL)", countryId);

        // Whatever is still there is edited, and its id is already taken: the
        // staged row is keyed on the landing row, so reloading it would collide
        // on the primary key rather than refresh anything.
        Set<Integer> preserved = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT STUDENT_ID FROM " + TARGET + " WHERE STUDY_COUNTRY_IN = ?",
                Integer.class, countryId));

        List<StagedRow> incoming = resolution.rows.stream()
                .filter(row -> !preserved.contains(row.studentId()))
                .toList();
        // Every row preserved means nothing to insert; a zero-row batch is a
        // round trip that can only do nothing.
        if (!incoming.isEmpty()) {
            insert(incoming);
        }

        log.info("ETL staged {} rows for country {} (replaced {}, preserved {} edited) by user {}",
                incoming.size(), countryId, deleted, preserved.size(), userId);
        return report(resolution, deleted, true, incoming.size(), preserved.size());
    }

    /** Row counts on both sides, for the confirmation before a run. */
    @Transactional(readOnly = true)
    public Map<String, Object> counts(Integer countryId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("countryId", countryId);
        counts.put("sourceRows", countryId == null ? 0 : count(SOURCE, "COUNTRY_ID", countryId));
        counts.put("stagedRows", countryId == null ? 0 : count(TARGET, "STUDY_COUNTRY_IN", countryId));
        counts.put("editedRows", countryId == null ? 0 : editedCount(countryId));
        counts.put("stagedTotal", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TARGET, Integer.class));
        return counts;
    }

    private int count(String table, String column, Integer countryId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, countryId);
        return n == null ? 0 : n;
    }

    /** The joined source both the list and its filter options read from. */
    private static final String STAGED_FROM = TARGET + " s "
            + "LEFT JOIN MOHECORE.STUDENT_DEGREE d ON d.STUDENT_DEGREE_ID = s.STUDENT_DEGREE_ID "
            + "LEFT JOIN MOHECORE.STUDENT_MAJOR m ON m.STUDENT_MAJOR_ID = s.STUDENT_MAJOR_ID "
            + "LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL l ON l.STUDENT_CURRENT_LEVEL_ID = s.STUDENT_CURRENT_LEVEL_ID "
            + "LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE a ON a.STUDENT_AGENCY_TYPE_ID = s.STUDENT_AGENCY_TYPE_ID "
            + "LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE t ON t.STUDENT_MINISTRY_TYPE_ID = s.STUDENT_MINISTRY_TYPE_ID "
            + "LEFT JOIN MOHECORE.STUDY_COUNTRY_IN c ON c.STUDY_COUNTRY_IN_ID = s.STUDY_COUNTRY_IN";

    private static final String STAGED_COLUMNS =
            "s.STUDENT_ID, s.STUDENT_NAME, s.STUDENT_MAJOR_NAME, s.STUDENT_AMOUNT, "
                    + "s.STUDENT_DELEGATION_DECISION_YEAR, s.STUDENT_DELEGATE_DECISION_NO, "
                    + "s.STUDENT_STUDY_NO_OF_YEARS, s.CURRENT_QUARTER, s.IS_EDITED, "
                    + "s.STUDENT_DEGREE_ID, d.STUDENT_DEGREE_NAME AS DEGREE_NAME, "
                    + "s.STUDENT_MAJOR_ID, m.STUDENT_MAJOR_NAME AS MAJOR_NAME, "
                    + "s.STUDENT_CURRENT_LEVEL_ID, l.STUDENT_CURRENT_LEVEL_NAME AS LEVEL_NAME, "
                    + "s.STUDENT_AGENCY_TYPE_ID, a.STUDENT_AGENCY_TYPE_NAME AS AGENCY_NAME, "
                    + "s.STUDENT_MINISTRY_TYPE_ID, t.STUDENT_MINISTRY_TYPE_NAME AS MINISTRY_NAME, "
                    + "s.STUDY_COUNTRY_IN, c.STUDY_COUNTRY_IN_NAME AS COUNTRY_NAME";

    /**
     * What the staged list may be sorted by.
     *
     * <p>The whitelist is the security boundary for the ORDER BY: a sort column
     * cannot be a bind parameter, so a name the client sent must never reach
     * the statement. A key that is not here is absent, not rejected.
     *
     * <p>Level and quarter sort on their numbers rather than their names.
     * Ordering "السنة الثامنة" and "السنة الثانية" alphabetically puts the
     * eighth year before the second, which is not what anybody means by
     * sorting by level.
     */
    private static final Map<String, String> STAGED_SORTABLE = Map.ofEntries(
            Map.entry("STUDENT_ID", "s.STUDENT_ID"),
            Map.entry("STUDENT_NAME", "s.STUDENT_NAME"),
            Map.entry("DEGREE", "d.STUDENT_DEGREE_NAME"),
            Map.entry("MAJOR", "m.STUDENT_MAJOR_NAME"),
            Map.entry("LEVEL", "s.STUDENT_CURRENT_LEVEL_ID"),
            Map.entry("AGENCY", "a.STUDENT_AGENCY_TYPE_NAME"),
            Map.entry("MINISTRY", "t.STUDENT_MINISTRY_TYPE_NAME"),
            Map.entry("AMOUNT", "s.STUDENT_AMOUNT"),
            Map.entry("QUARTER", "s.STUDENT_LAST_QUARTER"),
            Map.entry("EDITED", "s.IS_EDITED"));

    /** Columns offered as a checkbox filter, matched on the id behind the name. */
    private static final Map<String, String> STAGED_FILTERABLE = Map.of(
            "DEGREE", "s.STUDENT_DEGREE_ID",
            "MAJOR", "s.STUDENT_MAJOR_ID",
            "LEVEL", "s.STUDENT_CURRENT_LEVEL_ID",
            "AGENCY", "s.STUDENT_AGENCY_TYPE_ID",
            "MINISTRY", "s.STUDENT_MINISTRY_TYPE_ID",
            "QUARTER", "s.CURRENT_QUARTER",
            "EDITED", "s.IS_EDITED");

    /** id and label expression per filterable column, for the dropdown lists. */
    private static final Map<String, String[]> STAGED_FILTER_OPTION_SQL = new LinkedHashMap<>();

    static {
        STAGED_FILTER_OPTION_SQL.put("DEGREE",
                new String[]{"s.STUDENT_DEGREE_ID", "d.STUDENT_DEGREE_NAME"});
        STAGED_FILTER_OPTION_SQL.put("LEVEL",
                new String[]{"s.STUDENT_CURRENT_LEVEL_ID", "l.STUDENT_CURRENT_LEVEL_NAME"});
        STAGED_FILTER_OPTION_SQL.put("AGENCY",
                new String[]{"s.STUDENT_AGENCY_TYPE_ID", "a.STUDENT_AGENCY_TYPE_NAME"});
        STAGED_FILTER_OPTION_SQL.put("MINISTRY",
                new String[]{"s.STUDENT_MINISTRY_TYPE_ID", "t.STUDENT_MINISTRY_TYPE_NAME"});
        STAGED_FILTER_OPTION_SQL.put("QUARTER",
                new String[]{"s.CURRENT_QUARTER", "s.CURRENT_QUARTER"});
    }

    /** Text columns the staged search box looks in. */
    private static final List<String> STAGED_SEARCH_COLUMNS = List.of(
            "s.STUDENT_NAME", "s.STUDENT_MAJOR_NAME", "s.NATIONAL_ID",
            "s.STUDENT_PASSPORT_NUMBER", "d.STUDENT_DEGREE_NAME", "m.STUDENT_MAJOR_NAME");

    /**
     * The lookups whose "غير مبين" row means the resolver gave up.
     *
     * <p>The ministry is deliberately not among them. Most entities are a
     * university or a study level and have no ministry at all, so 785 of the
     * 842 rows in the first batch sit on 99 quite correctly — counting those as
     * unresolved would make the filter useless.
     */
    private static final List<String> STAGED_RESOLVED_COLUMNS = List.of(
            "s.STUDENT_DEGREE_ID", "s.STUDENT_MAJOR_ID",
            "s.STUDENT_CURRENT_LEVEL_ID", "s.STUDENT_AGENCY_TYPE_ID");

    /**
     * How the caller wants the staged list sliced. Every field is untrusted
     * input and every field is optional.
     */
    public record StagedQuery(String sortBy, String sortDir, String search,
                              Map<String, List<String>> filters, boolean unresolvedOnly) {

        public static final StagedQuery DEFAULT = new StagedQuery(null, null, null, Map.of(), false);

        public StagedQuery {
            filters = filters == null ? Map.of() : filters;
        }
    }

    /**
     * ID last, always. Without it a page boundary landing inside a run of equal
     * values is free to shuffle between requests, so a row can appear on two
     * pages and another on none.
     */
    private String stagedOrderBy(StagedQuery q) {
        String column = q.sortBy() == null ? null
                : STAGED_SORTABLE.get(q.sortBy().trim().toUpperCase(Locale.ROOT));
        if (column == null) {
            return " ORDER BY s.STUDENT_ID";
        }
        String dir = q.sortDir() == null ? "" : q.sortDir().trim().toLowerCase(Locale.ROOT);
        return " ORDER BY " + column + (dir.startsWith("desc") ? " DESC" : " ASC") + ", s.STUDENT_ID";
    }

    private void appendStagedFilters(StringBuilder where, List<Object> params, StagedQuery q) {
        for (Map.Entry<String, List<String>> entry : q.filters().entrySet()) {
            String column = entry.getKey() == null ? null
                    : STAGED_FILTERABLE.get(entry.getKey().trim().toUpperCase(Locale.ROOT));
            if (column == null || entry.getValue() == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (String value : entry.getValue()) {
                if (value != null && !value.isBlank() && values.size() < MAX_FILTER_VALUES) {
                    values.add(value);
                }
            }
            if (values.isEmpty()) {
                continue;
            }
            where.append(" AND ").append(column).append(" IN (");
            for (int i = 0; i < values.size(); i++) {
                where.append(i == 0 ? "?" : ",?");
            }
            where.append(")");
            params.addAll(values);
        }

        if (q.unresolvedOnly()) {
            // The reason somebody opens this screen: the rows a person still
            // has to rule on.
            where.append(" AND (");
            for (int i = 0; i < STAGED_RESOLVED_COLUMNS.size(); i++) {
                where.append(i == 0 ? "" : " OR ")
                        .append(STAGED_RESOLVED_COLUMNS.get(i)).append(" IN (99, 999)");
            }
            where.append(")");
        }
    }

    private void appendStagedSearch(StringBuilder where, List<Object> params, StagedQuery q) {
        for (String term : searchTerms(q.search())) {
            where.append(" AND (");
            for (int i = 0; i < STAGED_SEARCH_COLUMNS.size(); i++) {
                where.append(i == 0 ? "" : " OR ")
                        .append(STAGED_SEARCH_COLUMNS.get(i)).append(" LIKE ? ESCAPE \'\\\'");
                params.add("%" + escapeLike(term) + "%");
            }
            where.append(")");
        }
    }

    /** Words are ANDed, each may land in any searched column. */
    private static List<String> searchTerms(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String part : search.trim().split("\\s+")) {
            if (!part.isEmpty() && terms.size() < MAX_SEARCH_TERMS) {
                terms.add(part);
            }
        }
        return terms;
    }

    /** A wildcard somebody typed is text, not syntax. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
    }

    private static final int MAX_SEARCH_TERMS = 5;
    private static final int MAX_FILTER_VALUES = 200;

    /**
     * The staged rows, for the review step.
     *
     * <p>Joins the lookups back to their names. The point of the review is to
     * see whether the resolution was right, and a page of integers cannot show
     * anyone that.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> listStaged(Integer countryId, int page, int size) {
        return listStaged(countryId, page, size, StagedQuery.DEFAULT);
    }

    /** Staged rows, sorted, filtered and searched the way the caller asked. */
    @Transactional(readOnly = true)
    public Map<String, Object> listStaged(Integer countryId, int page, int size, StagedQuery query) {
        StagedQuery q = query == null ? StagedQuery.DEFAULT : query;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (countryId != null) {
            where.append(" AND s.STUDY_COUNTRY_IN = ?");
            params.add(countryId);
        }
        appendStagedFilters(where, params, q);
        appendStagedSearch(where, params, q);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + STAGED_FROM + where, Integer.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(page * size);
        pageParams.add(size);

        List<Map<String, Object>> content = jdbcTemplate.queryForList(
                "SELECT " + STAGED_COLUMNS + " FROM " + STAGED_FROM + where
                        + stagedOrderBy(q) + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                pageParams.toArray());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("totalElements", total == null ? 0 : total);
        out.put("number", page);
        out.put("size", size);
        return out;
    }

    /**
     * The values behind each column filter, as they appear in the staged rows.
     *
     * <p>Read off the staged data rather than off the lookup tables: a dropdown
     * offering all 444 specializations when the country in view uses thirty of
     * them is a list where most choices return nothing.
     */
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> stagedFilterOptions(Integer countryId) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        String scope = countryId == null ? "" : " AND s.STUDY_COUNTRY_IN = ?";
        Object[] args = countryId == null ? new Object[0] : new Object[]{countryId};

        for (Map.Entry<String, String[]> entry : STAGED_FILTER_OPTION_SQL.entrySet()) {
            String idExpr = entry.getValue()[0];
            String nameExpr = entry.getValue()[1];
            out.put(entry.getKey(), jdbcTemplate.queryForList(
                    "SELECT DISTINCT " + idExpr + " AS id, " + nameExpr + " AS name "
                            + "FROM " + STAGED_FROM
                            + " WHERE " + idExpr + " IS NOT NULL" + scope
                            + " ORDER BY name", args));
        }
        return out;
    }


    /**
     * One staged row, every column of it, with each lookup resolved to a name.
     *
     * <p>The names are the point. A staged row is the output of a guess the ETL
     * made about somebody's Arabic handwriting, and the only way to check that
     * guess is to see the name the id landed on. The id comes back too, because
     * two lookup rows can carry the same name — السنة الأولى is both 1 and 100 —
     * and the name alone cannot say which one was chosen.
     *
     * <p>Returns null when there is no such row, so the caller can answer 404
     * rather than an empty form.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> findStaged(int studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.*, "
                        + "n.STUDENT_COUNTRY_NAME AS NATIONALITY_NAME, "
                        + "d.STUDENT_DEGREE_NAME AS DEGREE_NAME, "
                        + "m.STUDENT_MAJOR_NAME AS MAJOR_NAME, "
                        + "l.STUDENT_CURRENT_LEVEL_NAME AS LEVEL_NAME, "
                        + "st.STUDENT_STUDY_STATUS_NAME AS STATUS_NAME, "
                        + "a.STUDENT_AGENCY_TYPE_NAME AS AGENCY_NAME, "
                        + "t.STUDENT_MINISTRY_TYPE_NAME AS MINISTRY_NAME, "
                        + "yt.STUDENT_STUDY_TYPE_NAME AS STUDY_TYPE_NAME, "
                        + "ut.STUDENT_UNIVERSITY_TYPE_NAME AS UNIVERSITY_TYPE_NAME, "
                        + "c.STUDY_COUNTRY_IN_NAME AS COUNTRY_NAME, "
                        + "u.STUDY_UNIVERSITY_IN_NAME AS UNIVERSITY_NAME, "
                        + "col.STUDY_COLLEGE_IN_NAME AS COLLEGE_NAME, "
                        + "g.STUDY_GOV_IN_NAME AS STUDY_GOV_NAME, "
                        + "e.STUDY_EDUCTION_NAME AS EDUCATION_NAME, "
                        + "sm.STUDY_MAJOR_NAME AS STUDY_MAJOR_NAME, "
                        + "hg.STUDENT_GOV_NAME AS HOME_GOV_NAME, "
                        + "hm.STUDENT_MUD_NAME AS HOME_DISTRICT_NAME, "
                        + "gd.STUDENT_GENDER_NAME AS GENDER_NAME "
                        + "FROM " + TARGET + " s "
                        + "LEFT JOIN MOHECORE.STUDENT_COUNTRY n ON n.STUDENT_COUNTRY_ID = s.STUDENT_COUNTRY_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_DEGREE d ON d.STUDENT_DEGREE_ID = s.STUDENT_DEGREE_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_MAJOR m ON m.STUDENT_MAJOR_ID = s.STUDENT_MAJOR_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL l ON l.STUDENT_CURRENT_LEVEL_ID = s.STUDENT_CURRENT_LEVEL_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS st ON st.STUDENT_STUDY_STATUS_ID = s.STUDENT_STUDY_STATUS_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE a ON a.STUDENT_AGENCY_TYPE_ID = s.STUDENT_AGENCY_TYPE_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE t ON t.STUDENT_MINISTRY_TYPE_ID = s.STUDENT_MINISTRY_TYPE_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE yt ON yt.STUDENT_STUDY_TYPE_ID = s.STUDENT_STUDY_TYPE_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE ut ON ut.STUDENT_UNIVERSITY_TYPE_ID = s.STUDENT_UNIVERSITY_TYPE_ID "
                        + "LEFT JOIN MOHECORE.STUDY_COUNTRY_IN c ON c.STUDY_COUNTRY_IN_ID = s.STUDY_COUNTRY_IN "
                        + "LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN u ON u.STUDY_UNIVERSITY_IN_ID = s.STUDY_UNIVERSITY_IN "
                        + "LEFT JOIN MOHECORE.STUDY_COLLEGE_IN col ON col.STUDY_COLLEGE_IN_ID = s.STUDY_COLLEGE_IN "
                        + "LEFT JOIN MOHECORE.STUDY_GOV_IN g ON g.STUDY_GOV_IN_ID = s.SYUDY_GOV_IN "
                        + "LEFT JOIN MOHECORE.STUDY_EDUCTION_IN e ON e.STUDY_EDUCTION_ID = s.STUDY_EDUCATION_IN "
                        + "LEFT JOIN MOHECORE.STUDY_MAJOR_IN sm ON sm.STUDY_MAJOR_ID = s.STUDY_MAJOR_IN "
                        + "LEFT JOIN MOHECORE.STUDENT_GOV hg ON hg.STUDENT_GOV_ID = s.STUDENT_GOV_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_GOV_MUD hm ON hm.STUDENT_MUD_ID = s.STUDENT_MUD_ID "
                        + "LEFT JOIN MOHECORE.STUDENT_GENDER gd ON gd.STUDENT_GENDER_ID = s.STUDENT_GENDER "
                        + "WHERE s.STUDENT_ID = ?", studentId);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        // Timestamps go out as text: the dialog shows them, it does not do
        // arithmetic on them, and a raw java.sql type serialises unhelpfully.
        row.replaceAll((key, value) -> value instanceof java.sql.Timestamp ts
                ? ts.toLocalDateTime().toString()
                : value);
        return row;
    }

    // ─────────────────────────────────────────────────────────── editing ──

    /**
     * A column the edit screen may write, and the lookup behind it if any.
     *
     * <p>This map is the security boundary for the UPDATE. Column names cannot
     * be bind parameters, so they are concatenated into the SQL, and the only
     * safe way to do that is never to concatenate anything the client sent. A
     * key that is not here is not rejected — it is absent, and the field is
     * simply not written.
     *
     * <p>Three columns are deliberately missing from it:
     * <ul>
     *   <li>{@code STUDENT_ID} — the landing row's id. It is this row's
     *       lineage and its primary key; editing it would orphan both.</li>
     *   <li>{@code STUDY_COUNTRY_IN} — the column the ETL's delete is scoped
     *       by. A row moved out of its country would never be cleaned up by
     *       any subsequent run, and would sit in the table forever.</li>
     *   <li>{@code IS_EDITED} and friends — set by the act of editing, not by
     *       the person doing it.</li>
     * </ul>
     */
    private record Editable(String column, String lookupTable, String lookupId, boolean numeric) {

        static Editable text(String column) {
            return new Editable(column, null, null, false);
        }

        static Editable number(String column) {
            return new Editable(column, null, null, true);
        }

        static Editable lookup(String column, String table, String idColumn) {
            return new Editable(column, table, idColumn, true);
        }
    }

    private static final Map<String, Editable> EDITABLE = new LinkedHashMap<>();

    static {
        for (Editable field : List.of(
                Editable.text("STUDENT_NAME"),
                Editable.text("STUDENT_MAJOR_NAME"),
                Editable.text("STUDENT_DELEGATION_DECISION_YEAR"),
                Editable.text("STUDENT_NOTE_1"),
                Editable.text("STUDENT_NOTE_2"),
                Editable.text("CURRENT_QUARTER"),
                Editable.text("STUDENT_PASSPORT_NUMBER"),
                Editable.text("NATIONAL_ID"),
                Editable.text("STUDENT_EMAIL"),
                Editable.text("STUDENT_CELLPHONE"),
                Editable.number("STUDENT_DELEGATE_DECISION_NO"),
                Editable.number("STUDENT_STUDY_NO_OF_YEARS"),
                Editable.number("STUDENT_FIRST_RECEIPT_OF_DUE"),
                Editable.number("STUDENT_AMOUNT"),
                Editable.number("STUDENT_ADDED_QUARTER"),
                Editable.number("STUDENT_LAST_QUARTER"),
                Editable.lookup("STUDENT_DEGREE_ID", "STUDENT_DEGREE", "STUDENT_DEGREE_ID"),
                Editable.lookup("STUDENT_MAJOR_ID", "STUDENT_MAJOR", "STUDENT_MAJOR_ID"),
                Editable.lookup("STUDENT_CURRENT_LEVEL_ID", "STUDENT_CURRENT_LEVEL", "STUDENT_CURRENT_LEVEL_ID"),
                Editable.lookup("STUDENT_AGENCY_TYPE_ID", "STUDENT_AGENCY_TYPE", "STUDENT_AGENCY_TYPE_ID"),
                Editable.lookup("STUDENT_MINISTRY_TYPE_ID", "STUDENT_MINISTRY_TYPE", "STUDENT_MINISTRY_TYPE_ID"),
                Editable.lookup("STUDENT_STUDY_STATUS_ID", "STUDENT_STUDY_STATUS", "STUDENT_STUDY_STATUS_ID"),
                Editable.lookup("STUDENT_COUNTRY_ID", "STUDENT_COUNTRY", "STUDENT_COUNTRY_ID"),
                Editable.lookup("STUDENT_STUDY_TYPE_ID", "STUDENT_STUDY_TYPE", "STUDENT_STUDY_TYPE_ID"),
                Editable.lookup("STUDENT_UNIVERSITY_TYPE_ID", "STUDENT_UNIVERSITY_TYPE", "STUDENT_UNIVERSITY_TYPE_ID"),
                Editable.lookup("STUDY_UNIVERSITY_IN", "STUDY_UNIVERSITY_IN", "STUDY_UNIVERSITY_IN_ID"),
                Editable.lookup("STUDY_COLLEGE_IN", "STUDY_COLLEGE_IN", "STUDY_COLLEGE_IN_ID"),
                Editable.lookup("SYUDY_GOV_IN", "STUDY_GOV_IN", "STUDY_GOV_IN_ID"),
                Editable.lookup("STUDY_EDUCATION_IN", "STUDY_EDUCTION_IN", "STUDY_EDUCTION_ID"),
                Editable.lookup("STUDY_MAJOR_IN", "STUDY_MAJOR_IN", "STUDY_MAJOR_ID"),
                Editable.lookup("STUDENT_GOV_ID", "STUDENT_GOV", "STUDENT_GOV_ID"),
                Editable.lookup("STUDENT_MUD_ID", "STUDENT_GOV_MUD", "STUDENT_MUD_ID"),
                Editable.lookup("STUDENT_GENDER", "STUDENT_GENDER", "STUDENT_GENDER_ID"))) {
            EDITABLE.put(field.column(), field);
        }
    }

    /** The name column each editable lookup shows in its dropdown. */
    private static final Map<String, String> LOOKUP_NAME_COLUMN = Map.ofEntries(
            Map.entry("STUDENT_DEGREE", "STUDENT_DEGREE_NAME"),
            Map.entry("STUDENT_MAJOR", "STUDENT_MAJOR_NAME"),
            Map.entry("STUDENT_CURRENT_LEVEL", "STUDENT_CURRENT_LEVEL_NAME"),
            Map.entry("STUDENT_AGENCY_TYPE", "STUDENT_AGENCY_TYPE_NAME"),
            Map.entry("STUDENT_MINISTRY_TYPE", "STUDENT_MINISTRY_TYPE_NAME"),
            Map.entry("STUDENT_STUDY_STATUS", "STUDENT_STUDY_STATUS_NAME"),
            Map.entry("STUDENT_COUNTRY", "STUDENT_COUNTRY_NAME"),
            Map.entry("STUDENT_STUDY_TYPE", "STUDENT_STUDY_TYPE_NAME"),
            Map.entry("STUDENT_UNIVERSITY_TYPE", "STUDENT_UNIVERSITY_TYPE_NAME"),
            Map.entry("STUDY_UNIVERSITY_IN", "STUDY_UNIVERSITY_IN_NAME"),
            Map.entry("STUDY_COLLEGE_IN", "STUDY_COLLEGE_IN_NAME"),
            Map.entry("STUDY_GOV_IN", "STUDY_GOV_IN_NAME"),
            Map.entry("STUDY_EDUCTION_IN", "STUDY_EDUCTION_NAME"),
            Map.entry("STUDY_MAJOR_IN", "STUDY_MAJOR_NAME"),
            Map.entry("STUDENT_GOV", "STUDENT_GOV_NAME"),
            Map.entry("STUDENT_GOV_MUD", "STUDENT_MUD_NAME"),
            Map.entry("STUDENT_GENDER", "STUDENT_GENDER_NAME"));

    /**
     * Every dropdown the edit form needs, keyed by the column it fills.
     *
     * <p>All of them in one response — about 1,400 options across seventeen
     * lookups — because the form needs them all at once and seventeen round
     * trips to open one dialog is worse than one payload.
     */
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> editLookups() {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Editable field : EDITABLE.values()) {
            if (field.lookupTable() == null) {
                continue;
            }
            String nameColumn = LOOKUP_NAME_COLUMN.get(field.lookupTable());
            out.put(field.column(), jdbcTemplate.queryForList(
                    "SELECT " + field.lookupId() + " AS id, " + nameColumn + " AS name "
                            + "FROM MOHECORE." + field.lookupTable()
                            + " ORDER BY " + nameColumn));
        }
        return out;
    }

    /**
     * Writes a person's corrections onto one staged row.
     *
     * <p>Only keys the whitelist knows are written, and a lookup id is checked
     * against its own table before the statement runs — a foreign-key violation
     * surfacing as a 500 tells the person filling in the form nothing at all.
     *
     * @return the row as it now stands, or null if there is no such row
     */
    @Transactional
    public Map<String, Object> updateStaged(int studentId, Map<String, Object> changes, Long userId) {
        Map<String, Object> existing = jdbcTemplate.queryForList(
                "SELECT STUDENT_ID FROM " + TARGET + " WHERE STUDENT_ID = ?", studentId)
                .stream().findFirst().orElse(null);
        if (existing == null) {
            return null;
        }

        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            Editable field = EDITABLE.get(change.getKey());
            if (field == null) {
                continue;
            }
            Object value = normaliseEdit(field, change.getValue());
            if (value != null && field.lookupTable() != null && !lookupHas(field, value)) {
                throw new IllegalArgumentException(
                        "No " + field.lookupTable() + " with id " + value + ".");
            }
            assignments.add(field.column() + " = ?");
            params.add(value);
        }
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("Nothing to change.");
        }
        if (changes.containsKey("STUDENT_NAME") && normaliseEdit(
                EDITABLE.get("STUDENT_NAME"), changes.get("STUDENT_NAME")) == null) {
            throw new IllegalArgumentException("A staged row needs a student name.");
        }

        params.add(userId);
        params.add(studentId);
        jdbcTemplate.update(
                "UPDATE " + TARGET + " SET " + String.join(", ", assignments)
                        + ", IS_EDITED = 1, EDITED_AT = SYSUTCDATETIME(), EDITED_BY = ? "
                        + "WHERE STUDENT_ID = ?",
                params.toArray());

        log.info("Staged row {} edited by user {} ({} fields)", studentId, userId, assignments.size());
        return findStaged(studentId);
    }

    /**
     * Hands the row back to the ETL.
     *
     * <p>Clearing the flag is the only way back: from here on the next run
     * replaces this row from the landing table like any other.
     */
    @Transactional
    public Map<String, Object> clearStagedEdit(int studentId) {
        int updated = jdbcTemplate.update(
                "UPDATE " + TARGET + " SET IS_EDITED = 0, EDITED_AT = NULL, EDITED_BY = NULL "
                        + "WHERE STUDENT_ID = ?", studentId);
        return updated == 0 ? null : findStaged(studentId);
    }

    private boolean lookupHas(Editable field, Object id) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE." + field.lookupTable()
                        + " WHERE " + field.lookupId() + " = ?", Integer.class, id);
        return found != null && found > 0;
    }

    /** An empty box means "no value", not the string "". */
    private static Object normaliseEdit(Editable field, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return field.numeric() ? number(text) : text;
    }

    // ───────────────────────────────────────────────────────── the resolution ──

    /** One source row turned into the values the staging table wants. */
    private record StagedRow(int studentId, String name, String decisionYear, Integer degreeId,
                             Integer majorId, String majorName, Integer decisionNo, Integer years,
                             Integer levelId, Integer firstReceipt, Integer amount, String note1,
                             String note2, Integer agencyTypeId, Integer ministryTypeId,
                             Integer quarter, String currentQuarter, Integer studyCountryIn) {}

    /** A value nobody has taught the resolver yet, and how many rows carry it. */
    private record Unresolved(String lookup, String value, int rows) {}

    /** A source row the load refused, and why. */
    private record Rejection(int sourceId, String reason) {}

    private static final class Resolution {
        final List<StagedRow> rows = new ArrayList<>();
        final List<Rejection> rejections = new ArrayList<>();
        /** lookup -> raw value -> row count, for the worklist. */
        final Map<String, Map<String, Integer>> unresolved = new LinkedHashMap<>();
        final Map<String, Integer> resolvedCount = new LinkedHashMap<>();
        final Map<String, Integer> notApplicableCount = new LinkedHashMap<>();
        Integer quarter;
        String currentQuarter;

        void miss(String lookup, String value) {
            unresolved.computeIfAbsent(lookup, k -> new TreeMap<>())
                    .merge(value == null || value.isBlank() ? "(blank)" : value.trim(), 1, Integer::sum);
        }

        void hit(String lookup) {
            resolvedCount.merge(lookup, 1, Integer::sum);
        }

        /** Counted but not a miss: the row simply has no value of this kind. */
        void notApplicable(String lookup) {
            notApplicableCount.merge(lookup, 1, Integer::sum);
        }
    }

    private Resolution resolve(Integer countryId) {
        Resolution out = new Resolution();
        if (countryId == null || countryId <= 0) {
            return out;
        }

        Map<String, Map<String, Integer>> byName = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> aliases = new LinkedHashMap<>();
        Map<String, Set<Integer>> validIds = new LinkedHashMap<>();
        for (Lookup lookup : LOOKUPS) {
            byName.put(lookup.key(), namesOf(lookup));
            aliases.put(lookup.key(), aliasesOf(lookup));
            validIds.put(lookup.key(), new LinkedHashSet<>(idsOf(lookup)));
        }

        List<Map<String, Object>> source = jdbcTemplate.queryForList(
                "SELECT ID, STUDENT_NAME, ENTITY, SCHOLARSHIP_YEAR, DEGREE, SPECIALIZATION, DECISION_NO, "
                        + "STUDY_DURATION, CURRENT_LEVEL, FIRST_DISBURSEMENT, AMOUNT, NOTES, FINANCIAL_NOTES, "
                        + "HDR_QUARTER_NO, HDR_YEAR_NO, COUNTRY_ID FROM " + SOURCE
                        + " WHERE COUNTRY_ID = ? ORDER BY ID", countryId);

        for (Map<String, Object> row : source) {
            int id = ((Number) row.get("ID")).intValue();

            String name = text(row.get("STUDENT_NAME"));
            if (name == null) {
                // The student's name is the one thing a staged row cannot be
                // reconstructed without.
                out.rejections.add(new Rejection(id, "no student name"));
                continue;
            }

            Integer amount = wholeAmount(row.get("AMOUNT"));
            if (amount == null && row.get("AMOUNT") != null) {
                // STUDENT_AMOUNT is an INT. Rounding somebody's entitlement to
                // make it fit is not this service's decision to make.
                out.rejections.add(new Rejection(id, "amount " + row.get("AMOUNT") + " is not a whole number"));
                continue;
            }

            String degree = text(row.get("DEGREE"));
            Integer degreeId = resolve("DEGREE", degree, byName, aliases, validIds, out);

            String specialization = text(row.get("SPECIALIZATION"));
            Integer majorId = resolve("MAJOR", specialization, byName, aliases, validIds, out);

            String entity = text(row.get("ENTITY"));
            Integer agencyTypeId = agencyTypeFor(entity);
            if (agencyTypeId == null) {
                agencyTypeId = resolve("AGENCY_TYPE", entity, byName, aliases, validIds, out);
            } else {
                out.hit("AGENCY_TYPE");
            }
            /*
             * Only some entities are a ministry — most are a university or a
             * study level — so a miss here is the normal case and does not
             * belong on the worklist. It is still counted, because a report
             * line reading "0 resolved, 0 unresolved" tells the reader nothing
             * about whether the ministries were found.
             */
            Integer ministryTypeId = resolve("MINISTRY_TYPE", entity, byName, aliases, validIds, null);
            if (ministryTypeId != null && ministryTypeId != 99) {
                out.hit("MINISTRY_TYPE");
            } else {
                out.notApplicable("MINISTRY_TYPE");
            }

            Integer levelId = levelFor(row.get("CURRENT_LEVEL"), validIds.get("CURRENT_LEVEL"), out);

            Integer quarterNo = number(row.get("HDR_QUARTER_NO"));
            Integer yearNo = number(row.get("HDR_YEAR_NO"));
            Integer quarter = quarterCode(yearNo, quarterNo);
            out.quarter = quarter;
            out.currentQuarter = quarter == null ? null : String.valueOf(quarter);

            out.rows.add(new StagedRow(
                    id,
                    name,
                    row.get("SCHOLARSHIP_YEAR") == null ? null : String.valueOf(row.get("SCHOLARSHIP_YEAR")),
                    degreeId,
                    majorId,
                    specialization,
                    number(row.get("DECISION_NO")),
                    number(row.get("STUDY_DURATION")),
                    levelId,
                    number(row.get("FIRST_DISBURSEMENT")),
                    amount,
                    text(row.get("NOTES")),
                    text(row.get("FINANCIAL_NOTES")),
                    agencyTypeId,
                    ministryTypeId,
                    quarter,
                    out.currentQuarter,
                    countryId));
        }
        return out;
    }

    /**
     * Alias first, then the lookup's own name, then the "غير مبين" row.
     *
     * <p>The alias comes first so a curated decision beats an accidental
     * spelling collision. Pass a null {@code out} for a lookup that is only
     * expected to match sometimes — the ministry, which most entities are not
     * — so the misses are not reported as a worklist nobody should act on.
     */
    private Integer resolve(String key, String raw,
                            Map<String, Map<String, Integer>> byName,
                            Map<String, Map<String, Integer>> aliases,
                            Map<String, Set<Integer>> validIds,
                            Resolution out) {
        Lookup lookup = LOOKUPS.stream().filter(l -> l.key().equals(key)).findFirst().orElseThrow();
        if (raw == null || raw.isBlank()) {
            if (out != null) {
                out.miss(key, raw);
            }
            return lookup.unknownId();
        }
        String normalised = normaliseArabic(raw);

        Integer aliased = aliases.get(key).get(normalised);
        if (aliased != null && validIds.get(key).contains(aliased)) {
            if (out != null) {
                out.hit(key);
            }
            return aliased;
        }

        Integer byExactName = byName.get(key).get(normalised);
        if (byExactName != null) {
            if (out != null) {
                out.hit(key);
            }
            return byExactName;
        }

        // Last try: the Arabic definite article and nothing else. "الدكتوراه"
        // is the lookup's "دكتوراه" with ال in front of it. Tried only after an
        // exact match has failed, so it can never steal a real match.
        Integer byStripped = byName.get(key).get(stripArticle(normalised));
        if (byStripped != null) {
            if (out != null) {
                out.hit(key);
            }
            return byStripped;
        }

        if (out != null) {
            out.miss(key, raw);
        }
        return lookup.unknownId();
    }

    /**
     * The sponsoring entity, read as a family rather than a name.
     *
     * <p>ENTITY is three different kinds of value in one column: a study level
     * ("د. جامعية", "د. عليا"), a university ("ج عدن"), or a ministry
     * ("وزارة الدفاع"). None of those is spelled the way STUDENT_AGENCY_TYPE
     * spells its names, so matching on the name resolves none of them and the
     * prefix resolves nearly all of them.
     *
     * <p>Returns null when the entity is none of the three, which sends it
     * down the ordinary name/alias path and onto the worklist if that fails
     * too.
     */
    private Integer agencyTypeFor(String entity) {
        String n = normaliseArabic(entity);
        if (n.isEmpty()) {
            return null;
        }
        if (n.startsWith("د.") || n.startsWith("د ")) {
            String rest = n.substring(2).trim();
            if (rest.startsWith("جامعي")) {
                return AGENCY_UNDERGRAD;
            }
            if (rest.startsWith("عليا")) {
                return AGENCY_POSTGRAD;
            }
            return null;
        }
        if (n.startsWith("ج ")) {
            return AGENCY_UNIVERSITY;
        }
        // "وزارة" normalises to "وزاره": ة folds to ه before this comparison.
        if (n.startsWith("وزاره")) {
            return AGENCY_MINISTRY;
        }
        return null;
    }

    /**
     * The level, which the sheet writes as a number rather than a name.
     *
     * <p>Levels 1-7 in the sheet are ids 1-7 in the lookup (السنة الأولى …
     * السابع). The lookup also carries a parallel 100-108 set with the same
     * names; the low ids are the ones in use.
     */
    private Integer levelFor(Object value, Set<Integer> validIds, Resolution out) {
        Integer level = number(value);
        if (level != null && validIds.contains(level)) {
            out.hit("CURRENT_LEVEL");
            return level;
        }
        out.miss("CURRENT_LEVEL", level == null ? null : String.valueOf(level));
        return 99;
    }

    /** 2025 Q3 becomes 20253, the YYYYQ code the live student table uses. */
    private static Integer quarterCode(Integer year, Integer quarter) {
        if (year == null || quarter == null || quarter < 1 || quarter > 4) {
            return null;
        }
        return year * 10 + quarter;
    }

    private static String stripArticle(String normalised) {
        return normalised.startsWith("ال") && normalised.length() > 3
                ? normalised.substring(2) : normalised;
    }

    private Map<String, Integer> namesOf(Lookup lookup) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + lookup.idColumn() + " AS ID, " + lookup.nameColumn() + " AS NAME "
                        + "FROM MOHECORE." + lookup.table() + " ORDER BY " + lookup.idColumn());
        for (Map<String, Object> row : rows) {
            String name = normaliseArabic(text(row.get("NAME")));
            if (!name.isEmpty()) {
                // Ordered by id and first-wins, so a lookup that carries the
                // same name twice (السنة الأولى at both 1 and 100) resolves to
                // the low id every time rather than whichever came back first.
                byName.putIfAbsent(name, ((Number) row.get("ID")).intValue());
            }
        }
        return byName;
    }

    private List<Integer> idsOf(Lookup lookup) {
        return jdbcTemplate.queryForList(
                "SELECT " + lookup.idColumn() + " FROM MOHECORE." + lookup.table(), Integer.class);
    }

    private Map<String, Integer> aliasesOf(Lookup lookup) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT NORMALISED_VALUE, RESOLVED_ID FROM " + ALIAS + " WHERE LOOKUP = ?", lookup.key());
        for (Map<String, Object> row : rows) {
            out.put(text(row.get("NORMALISED_VALUE")), ((Number) row.get("RESOLVED_ID")).intValue());
        }
        return out;
    }

    // ────────────────────────────────────────────────────────────── the insert ──

    /**
     * Every column the row needs, named.
     *
     * <p>Nothing is left out and nothing relies on a table default — see the
     * class comment for why that matters here more than usual.
     */
    private static final String INSERT_SQL = "INSERT INTO " + TARGET + " ("
            + "STUDENT_ID, STUDENT_NAME, STUDENT_COUNTRY_ID, STUDENT_GOV_ID, STUDENT_MUD_ID, "
            + "STUDENT_DELEGATION_DECISION_YEAR, STUDENT_DEGREE_ID, STUDENT_MAJOR_ID, STUDENT_MAJOR_NAME, "
            + "STUDENT_DELEGATE_DECISION_NO, STUDENT_STUDY_NO_OF_YEARS, STUDENT_CURRENT_LEVEL_ID, "
            + "STUDENT_FIRST_RECEIPT_OF_DUE, STUDENT_AMOUNT, STUDENT_NOTE_1, STUDENT_NOTE_2, "
            + "STUDENT_STUDY_STATUS_ID, STUDENT_AGENCY_TYPE_ID, STUDENT_MINISTRY_TYPE_ID, "
            + "STUDENT_STUDY_TYPE_ID, STUDENT_UNIVERSITY_TYPE_ID, STUDENT_ADDED_QUARTER, STUDENT_LAST_QUARTER, "
            + "STUDY_UNIVERSITY_IN, STUDY_COLLEGE_IN, STUDY_COUNTRY_IN, SYUDY_GOV_IN, STUDY_EDUCATION_IN, "
            + "STUDY_MAJOR_IN, STUDENT_GENDER, STUDENT_PASSPORT_ISSUE_COUNTRY_ID, "
            + "STUDY_UNIVERSITY_FEE_ID, STUDY_UNIVERSITY_FEE_STATUS_ID, STUDENT_MIDATE, "
            + "IS_DELETED, RECORD_VISIBILITY, CURRENT_QUARTER) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, GETDATE(), 0, 'ALL', ?)";

    private void insert(List<StagedRow> rows) {
        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StagedRow row = rows.get(i);
                int p = 1;
                ps.setInt(p++, row.studentId());
                ps.setString(p++, row.name());
                ps.setInt(p++, NATIONALITY_YEMEN);
                setInt(ps, p++, NO_GOVERNORATE);
                setInt(ps, p++, NO_DISTRICT);
                ps.setString(p++, row.decisionYear());
                setInt(ps, p++, row.degreeId());
                setInt(ps, p++, row.majorId());
                ps.setString(p++, row.majorName());
                setInt(ps, p++, row.decisionNo());
                setInt(ps, p++, row.years());
                setInt(ps, p++, row.levelId());
                setInt(ps, p++, row.firstReceipt());
                setInt(ps, p++, row.amount());
                ps.setString(p++, row.note1());
                ps.setString(p++, row.note2());
                ps.setInt(p++, STUDY_STATUS_NEW);
                setInt(ps, p++, row.agencyTypeId());
                setInt(ps, p++, row.ministryTypeId());
                ps.setInt(p++, UNKNOWN_STUDY_TYPE);
                setInt(ps, p++, NO_UNIVERSITY_TYPE);
                setInt(ps, p++, row.quarter());
                setInt(ps, p++, row.quarter());
                ps.setInt(p++, UNKNOWN_UNIVERSITY_IN);
                ps.setInt(p++, UNKNOWN_COLLEGE_IN);
                setInt(ps, p++, row.studyCountryIn());
                ps.setInt(p++, UNKNOWN_GOV_IN);
                ps.setInt(p++, UNKNOWN_EDUCATION_IN);
                setInt(ps, p++, NO_STUDY_MAJOR_IN);
                setInt(ps, p++, NO_GENDER);
                setInt(ps, p++, NO_PASSPORT_COUNTRY);
                ps.setInt(p++, NO_UNIVERSITY_FEE);   // STUDY_UNIVERSITY_FEE_ID
                ps.setInt(p++, NO_UNIVERSITY_FEE);   // STUDY_UNIVERSITY_FEE_STATUS_ID
                ps.setString(p, row.currentQuarter());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    // ───────────────────────────────────────────────────────────── the report ──

    private Map<String, Object> report(Resolution resolution, int deleted, boolean written) {
        return report(resolution, deleted, written, resolution.rows.size(), 0);
    }

    private Map<String, Object> report(Resolution resolution, int deleted, boolean written,
                                       int staged, int preserved) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("written", written);
        out.put("rowsStaged", staged);
        out.put("rowsReplaced", deleted);
        // "700 staged, 142 preserved" and "842 staged" must not look alike.
        out.put("rowsPreserved", preserved);
        out.put("rowsRejected", resolution.rejections.size());
        out.put("quarter", resolution.quarter);
        out.put("currentQuarter", resolution.currentQuarter);

        List<Map<String, Object>> lookupLines = new ArrayList<>();
        for (Lookup lookup : LOOKUPS) {
            int hit = resolution.resolvedCount.getOrDefault(lookup.key(), 0);
            int missed = resolution.unresolved.getOrDefault(lookup.key(), Map.of())
                    .values().stream().mapToInt(Integer::intValue).sum();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("lookup", lookup.key());
            line.put("resolved", hit);
            line.put("unresolved", missed);
            line.put("notApplicable", resolution.notApplicableCount.getOrDefault(lookup.key(), 0));
            lookupLines.add(line);
        }
        out.put("resolution", lookupLines);

        List<Map<String, Object>> worklist = new ArrayList<>();
        resolution.unresolved.forEach((lookup, values) -> values.forEach((value, count) -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("lookup", lookup);
            line.put("value", value);
            line.put("rows", count);
            worklist.add(line);
        }));
        worklist.sort((a, b) -> Integer.compare((Integer) b.get("rows"), (Integer) a.get("rows")));
        out.put("unresolved", worklist);

        List<Map<String, Object>> rejected = new ArrayList<>();
        for (Rejection rejection : resolution.rejections) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sourceId", rejection.sourceId());
            line.put("reason", rejection.reason());
            rejected.add(line);
        }
        out.put("rejected", rejected);
        return out;
    }

    // ───────────────────────────────────────────────────────────────── values ──

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String digits = String.valueOf(value).replaceAll("[^0-9-]", "");
        if (digits.isEmpty() || digits.equals("-")) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The amount, if it fits in an INT without losing anything.
     *
     * <p>Returns null for a fractional amount so the caller can reject the row
     * rather than round it. Every row of the first real batch was whole, but
     * "was whole last quarter" is not a guarantee about next quarter.
     */
    private static Integer wholeAmount(Object value) {
        if (value == null) {
            return null;
        }
        BigDecimal amount = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
        try {
            return amount.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }
}
