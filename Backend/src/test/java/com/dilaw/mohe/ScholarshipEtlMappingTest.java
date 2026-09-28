package com.dilaw.mohe;

import com.dilaw.mohe.service.ScholarshipEtlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The mapping from the imported sheet into STUDENT_DATA_STAGING.
 *
 * <p>Against a <strong>mocked</strong> {@link JdbcTemplate}. The
 * {@code @SpringBootTest} suites in this project run against the live dev
 * database, and a test that ran the real load would rewrite the staging table
 * on every build. What is worth asserting here is which values the insert
 * carries, and a mock shows those exactly.
 *
 * <p>Several of these tests exist because of a specific hazard in the target
 * table rather than out of general diligence. STUDENT_DATA_STAGING defaults
 * sixteen foreign-key columns to 0, and 0 is not a row in those lookups; two
 * more columns take a 0 that <em>is</em> a row and means something — deceased,
 * and a bachelor's in electronics engineering. A regression there would not
 * throw. It would quietly stage 842 dead electronics engineers.
 */
class ScholarshipEtlMappingTest {

    private static final int EGYPT = 1;

    private ScholarshipEtlService service;
    private JdbcTemplate jdbc;

    /** Column name -> the value the insert bound to it, for one row. */
    private Map<String, Object> bound;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new ScholarshipEtlService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        // The lookups, as the real tables have them.
        when(jdbc.queryForList(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("STUDENT_DEGREE")) {
                return rows("STUDENT_DEGREE_ID", "STUDENT_DEGREE_NAME",
                        1, "جامعية", 4, "زمالة", 99, "غير مبين", 103, "ماجستير", 104, "دكتوراه");
            }
            if (sql.contains("STUDENT_MAJOR")) {
                return rows("STUDENT_MAJOR_ID", "STUDENT_MAJOR_NAME", 5, "طب بشري", 99, "غير مبين");
            }
            if (sql.contains("STUDENT_AGENCY_TYPE")) {
                return rows("STUDENT_AGENCY_TYPE_ID", "STUDENT_AGENCY_TYPE_NAME",
                        1, "جامعة", 2, "جامعية", 3, "دراسات عليا", 4, "وزارة", 99, "غير مبين");
            }
            if (sql.contains("STUDENT_MINISTRY_TYPE")) {
                return rows("STUDENT_MINISTRY_TYPE_ID", "STUDENT_MINISTRY_TYPE_NAME",
                        2, "وزارة الدفاع", 99, "غير مبين");
            }
            if (sql.contains("STUDENT_CURRENT_LEVEL")) {
                // The lookup really does carry the same name twice.
                return rows("STUDENT_CURRENT_LEVEL_ID", "STUDENT_CURRENT_LEVEL_NAME",
                        1, "السنة الأولى", 6, "السنة السادسة", 99, "غير مبين", 100, "السنة الأولى");
            }
            return List.of();
        });

        when(jdbc.queryForList(anyString(), eq(Integer.class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("STUDENT_DEGREE")) return List.of(1, 4, 99, 103, 104);
            if (sql.contains("STUDENT_MAJOR")) return List.of(5, 99);
            if (sql.contains("STUDENT_AGENCY_TYPE")) return List.of(1, 2, 3, 4, 99);
            if (sql.contains("STUDENT_MINISTRY_TYPE")) return List.of(2, 99);
            if (sql.contains("STUDENT_CURRENT_LEVEL")) return List.of(1, 6, 99, 100);
            return List.of();
        });

        source(sourceRow(843, "دعاء علي", "د. جامعية", "جامعية", "طب بشري", 6, new BigDecimal("1500.00")));
    }

    /** Points the mock at a given set of landing-table rows (and no aliases). */
    private void source(Map<String, Object>... rows) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("ETL_LOOKUP_ALIAS")) {
                return aliasRows.getOrDefault(argOf(call), List.of());
            }
            if (sql.contains("STUDENT_SCHOLARSHIP_DATA_IMPORT")) {
                return List.of(rows);
            }
            return List.of();
        });
    }

    private final Map<String, List<Map<String, Object>>> aliasRows = new HashMap<>();

    /**
     * The first bind parameter of a stubbed queryForList.
     *
     * <p>Mockito hands varargs back either spread out or collected into an
     * array depending on how the call was written, so this copes with both
     * rather than assuming one.
     */
    private static String argOf(org.mockito.invocation.InvocationOnMock call) {
        Object[] args = call.getArguments();
        if (args.length < 2 || args[1] == null) {
            return "";
        }
        Object first = args[1] instanceof Object[] array
                ? (array.length > 0 ? array[0] : null)
                : args[1];
        return first == null ? "" : String.valueOf(first);
    }

    private void alias(String lookup, String normalised, int resolvedId) {
        aliasRows.computeIfAbsent(lookup, k -> new ArrayList<>()).add(new LinkedHashMap<>(Map.of(
                "NORMALISED_VALUE", normalised, "RESOLVED_ID", resolvedId)));
    }

    private static List<Map<String, Object>> rows(String idCol, String nameCol, Object... pairs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ID", pairs[i]);
            row.put("NAME", pairs[i + 1]);
            // namesOf/idsOf alias the columns to ID/NAME, but keep the real
            // names too so a change of alias does not silently pass.
            row.put(idCol, pairs[i]);
            row.put(nameCol, pairs[i + 1]);
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> sourceRow(int id, String name, String entity, String degree,
                                                 String specialization, Integer level, BigDecimal amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ID", id);
        row.put("STUDENT_NAME", name);
        row.put("ENTITY", entity);
        row.put("SCHOLARSHIP_YEAR", 2017);
        row.put("DEGREE", degree);
        row.put("SPECIALIZATION", specialization);
        row.put("DECISION_NO", "2025082");
        row.put("STUDY_DURATION", 7);
        row.put("CURRENT_LEVEL", level);
        row.put("FIRST_DISBURSEMENT", 20173);
        row.put("AMOUNT", amount);
        row.put("NOTES", "تمديد");
        row.put("FINANCIAL_NOTES", "0");
        row.put("HDR_QUARTER_NO", 3);
        row.put("HDR_YEAR_NO", 2025);
        row.put("COUNTRY_ID", EGYPT);
        return row;
    }

    /**
     * Runs the load and records what the insert bound, by column name.
     *
     * <p>Reads the column list straight off the generated INSERT so the names
     * here cannot drift away from the statement they describe.
     */
    private void load() {
        service.run(EGYPT, 7L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setter =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(sql.capture(), setter.capture());

        String statement = sql.getValue();
        List<String> names = List.of(statement
                .substring(statement.indexOf('(') + 1, statement.indexOf(") VALUES"))
                .split("\\s*,\\s*"));
        // Not every column is a bind parameter: three are SQL literals
        // (GETDATE(), 0, 'ALL'), and each consumes a column position without
        // consuming a parameter index. Walking the VALUES list keeps the two
        // aligned instead of assuming column N is parameter N.
        List<String> slots = List.of(statement
                .substring(statement.lastIndexOf("VALUES (") + 8, statement.lastIndexOf(')'))
                .split("\\s*,\\s*"));
        assertThat(slots).as("every column needs a value").hasSameSizeAs(names);

        Map<Integer, Object> byIndex = new LinkedHashMap<>();
        PreparedStatement ps = mock(PreparedStatement.class);
        try {
            org.mockito.Mockito.doAnswer(c -> byIndex.put(c.getArgument(0), c.getArgument(1)))
                    .when(ps).setInt(anyInt(), anyInt());
            org.mockito.Mockito.doAnswer(c -> byIndex.put(c.getArgument(0), c.getArgument(1)))
                    .when(ps).setString(anyInt(), any());
            org.mockito.Mockito.doAnswer(c -> byIndex.put(c.getArgument(0), null))
                    .when(ps).setNull(anyInt(), anyInt());
            setter.getValue().setValues(ps, 0);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        bound = new LinkedHashMap<>();
        int parameter = 0;
        for (int i = 0; i < names.size(); i++) {
            String slot = slots.get(i).trim();
            bound.put(names.get(i).trim(), slot.equals("?") ? byIndex.get(++parameter) : slot);
        }
    }

    // ───────────────────────────────────────────── the traps in the target ──

    @Test
    @DisplayName("a staged student is جديد, never the default 0 which is متوفى")
    void studyStatusIsNewNotDeceased() {
        load();

        assertThat(bound.get("STUDENT_STUDY_STATUS_ID"))
                .as("0 in STUDENT_STUDY_STATUS is متوفى — deceased")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("STUDY_EDUCATION_IN is the غير مبين row, not the id-0 row that is a real degree")
    void educationIsNotTheZeroRow() {
        load();

        // Id 0 there is بكالوريوس هندسة إلكترونية واتصالات. Writing it would
        // give every student in the batch a specialization nobody chose.
        assertThat(bound.get("STUDY_EDUCATION_IN")).isEqualTo(999);
    }

    @Test
    @DisplayName("every foreign-key column is written, so no table default can fire")
    void everyForeignKeyColumnIsNamed() {
        load();

        // Each of these defaults to 0 in the DDL and has no row 0 behind it;
        // any one left out of the insert is a foreign-key violation.
        assertThat(bound).containsKeys(
                "STUDENT_COUNTRY_ID", "STUDENT_GOV_ID", "STUDENT_MUD_ID", "STUDENT_DEGREE_ID",
                "STUDENT_MAJOR_ID", "STUDENT_CURRENT_LEVEL_ID", "STUDENT_STUDY_STATUS_ID",
                "STUDENT_AGENCY_TYPE_ID", "STUDENT_MINISTRY_TYPE_ID", "STUDENT_STUDY_TYPE_ID",
                "STUDENT_UNIVERSITY_TYPE_ID", "STUDY_UNIVERSITY_IN", "STUDY_COLLEGE_IN",
                "STUDY_COUNTRY_IN", "SYUDY_GOV_IN", "STUDY_EDUCATION_IN", "STUDY_MAJOR_IN",
                "STUDENT_GENDER", "STUDENT_PASSPORT_ISSUE_COUNTRY_ID");
    }

    @Test
    @DisplayName("a lookup with no غير مبين row gets NULL rather than an invented id")
    void unknownWithoutSentinelIsNull() {
        load();

        assertThat(bound.get("STUDENT_GOV_ID")).isNull();
        assertThat(bound.get("STUDENT_GENDER")).isNull();
        assertThat(bound.get("STUDENT_MUD_ID")).isNull();
    }

    @Test
    @DisplayName("nationality and country of study are different columns with different meanings")
    void nationalityIsNotCountryOfStudy() {
        load();

        // Both are 1 here and mean opposite things: STUDENT_COUNTRY 1 is
        // اليمن, STUDY_COUNTRY_IN 1 is مصر. Crossing them is invisible.
        assertThat(bound.get("STUDENT_COUNTRY_ID")).isEqualTo(1);   // اليمن, the student
        assertThat(bound.get("STUDY_COUNTRY_IN")).isEqualTo(EGYPT); // مصر, where they study
    }

    // ────────────────────────────────────────────────── resolving the text ──

    @Test
    @DisplayName("resolves the Arabic text to lookup ids")
    void resolvesText() {
        load();

        assertThat(bound.get("STUDENT_DEGREE_ID")).isEqualTo(1);    // جامعية
        assertThat(bound.get("STUDENT_MAJOR_ID")).isEqualTo(5);     // طب بشري
        assertThat(bound.get("STUDENT_AGENCY_TYPE_ID")).isEqualTo(2); // "د. جامعية"
    }

    @Test
    @DisplayName("ة and ه are the same letter for matching purposes")
    void foldsTaaMarbuta() {
        source(sourceRow(1, "س", "د. عليا", "دكتوراة", "طب بشري", 1, BigDecimal.TEN));
        load();

        // The sheets write دكتوراة; the lookup writes دكتوراه.
        assertThat(bound.get("STUDENT_DEGREE_ID")).isEqualTo(104);
    }

    @Test
    @DisplayName("the definite article alone does not make a value unknown")
    void stripsDefiniteArticle() {
        source(sourceRow(1, "س", "د. عليا", "الدكتوراه", "طب بشري", 1, BigDecimal.TEN));
        load();

        assertThat(bound.get("STUDENT_DEGREE_ID")).isEqualTo(104);
    }

    @Test
    @DisplayName("a curated alias beats the lookup's own spelling")
    void aliasWins() {
        // 'دكتوراه' matches the lookup exactly; the alias must still win, so a
        // human can override a name collision without editing the lookup.
        alias("DEGREE", "دكتوراه", 4);
        source(sourceRow(1, "س", "د. عليا", "دكتوراه", "طب بشري", 1, BigDecimal.TEN));
        load();

        assertThat(bound.get("STUDENT_DEGREE_ID")).isEqualTo(4);
    }

    @Test
    @DisplayName("an alias pointing at a row that no longer exists is ignored, not written")
    void staleAliasIsIgnored() {
        alias("DEGREE", "جامعيه", 77777);
        load();

        // Writing 77777 would fail the foreign key and take the whole batch
        // with it; falling through to the name match keeps the load running.
        assertThat(bound.get("STUDENT_DEGREE_ID")).isEqualTo(1);
    }

    @Test
    @DisplayName("an unrecognised value becomes غير مبين and is reported, not dropped")
    void unknownIsReported() {
        source(sourceRow(1, "س", "د. عليا", "دتوراة", "علم لم يخترع بعد", 1, BigDecimal.TEN));
        load();

        assertThat(bound.get("STUDENT_DEGREE_ID")).isEqualTo(99);
        assertThat(bound.get("STUDENT_MAJOR_ID")).isEqualTo(99);

        // The raw text survives even when the id does not.
        assertThat(bound.get("STUDENT_MAJOR_NAME")).isEqualTo("علم لم يخترع بعد");
    }

    @Test
    @DisplayName("the preview lists what nobody has taught the resolver yet")
    void previewCarriesTheWorklist() {
        source(sourceRow(1, "س", "د. عليا", "دتوراة", "طب بشري", 1, BigDecimal.TEN));

        Map<String, Object> report = service.preview(EGYPT);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> worklist = (List<Map<String, Object>>) report.get("unresolved");
        assertThat(worklist).anySatisfy(line -> {
            assertThat(line.get("lookup")).isEqualTo("DEGREE");
            assertThat(line.get("value")).isEqualTo("دتوراة");
            assertThat(line.get("rows")).isEqualTo(1);
        });
        assertThat(report.get("written")).isEqualTo(false);
    }

    @Test
    @DisplayName("a preview writes nothing at all")
    void previewIsReadOnly() {
        service.preview(EGYPT);

        verify(jdbc, org.mockito.Mockito.never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("the entity's prefix decides the agency, since its name never matches")
    void agencyComesFromThePrefix() {
        source(sourceRow(1, "س", "ج عدن", "جامعية", "طب بشري", 1, BigDecimal.TEN));
        load();
        assertThat(bound.get("STUDENT_AGENCY_TYPE_ID")).isEqualTo(1);   // جامعة

        setUp();
        source(sourceRow(1, "س", "وزارة الدفاع", "جامعية", "طب بشري", 1, BigDecimal.TEN));
        load();
        assertThat(bound.get("STUDENT_AGENCY_TYPE_ID")).isEqualTo(4);   // وزارة
        // …and the same text also names a specific ministry.
        assertThat(bound.get("STUDENT_MINISTRY_TYPE_ID")).isEqualTo(2); // وزارة الدفاع
    }

    @Test
    @DisplayName("an entity that is not a ministry gets غير مبين, not a wrong one")
    void nonMinistryHasNoMinistry() {
        load();

        assertThat(bound.get("STUDENT_MINISTRY_TYPE_ID")).isEqualTo(99);
    }

    @Test
    @DisplayName("a level number resolves to the low id when the lookup lists it twice")
    void levelPrefersTheLowId() {
        source(sourceRow(1, "س", "د. عليا", "جامعية", "طب بشري", 1, BigDecimal.TEN));
        load();

        // السنة الأولى is both 1 and 100 in the real lookup.
        assertThat(bound.get("STUDENT_CURRENT_LEVEL_ID")).isEqualTo(1);
    }

    @Test
    @DisplayName("a level the lookup does not have is غير مبين")
    void unknownLevel() {
        source(sourceRow(1, "س", "د. عليا", "جامعية", "طب بشري", 42, BigDecimal.TEN));
        load();

        assertThat(bound.get("STUDENT_CURRENT_LEVEL_ID")).isEqualTo(99);
    }

    // ────────────────────────────────────────────────────── other columns ──

    @Test
    @DisplayName("the statement's quarter becomes the YYYYQ code the student table uses")
    void quarterCode() {
        load();

        assertThat(bound.get("STUDENT_ADDED_QUARTER")).isEqualTo(20253);
        assertThat(bound.get("STUDENT_LAST_QUARTER")).isEqualTo(20253);
        assertThat(bound.get("CURRENT_QUARTER")).isEqualTo("20253");
    }

    @Test
    @DisplayName("a staged row is live and visible, written as literals in the statement")
    void rowIsLiveAndVisible() {
        load();

        assertThat(bound.get("IS_DELETED")).isEqualTo("0");
        assertThat(bound.get("RECORD_VISIBILITY")).isEqualTo("'ALL'");
        // The database clock, not the application's.
        assertThat(bound.get("STUDENT_MIDATE")).isEqualTo("GETDATE()");
    }

    @Test
    @DisplayName("the staged row keeps the landing row's id, so a re-run replaces it")
    void keyIsTheSourceRow() {
        load();

        assertThat(bound.get("STUDENT_ID")).isEqualTo(843);
    }

    @Test
    @DisplayName("a re-run clears only this country's rows")
    void deleteIsScopedToTheCountry() {
        service.run(EGYPT, 7L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), params.capture());

        assertThat(sql.getValue()).contains("WHERE STUDY_COUNTRY_IN = ?");
        assertThat(params.getValue()).containsExactly(EGYPT);
    }

    @Test
    @DisplayName("a fractional amount is refused, not rounded into the INT column")
    void fractionalAmountIsRejected() {
        source(sourceRow(1, "س", "د. عليا", "جامعية", "طب بشري", 1, new BigDecimal("1500.50")));

        Map<String, Object> report = service.preview(EGYPT);

        assertThat(report.get("rowsStaged")).isEqualTo(0);
        assertThat(report.get("rowsRejected")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rejected = (List<Map<String, Object>>) report.get("rejected");
        assertThat(String.valueOf(rejected.get(0).get("reason"))).contains("whole number");
    }

    @Test
    @DisplayName("a whole amount written with decimals still loads")
    void wholeAmountWithDecimalsLoads() {
        load();

        // 1500.00 is a whole number spelled with a scale.
        assertThat(bound.get("STUDENT_AMOUNT")).isEqualTo(1500);
    }

    @Test
    @DisplayName("a row with no student name is refused rather than staged nameless")
    void namelessRowIsRejected() {
        source(sourceRow(1, null, "د. عليا", "جامعية", "طب بشري", 1, BigDecimal.TEN));

        Map<String, Object> report = service.preview(EGYPT);

        assertThat(report.get("rowsStaged")).isEqualTo(0);
        assertThat(report.get("rowsRejected")).isEqualTo(1);
    }

    @Test
    @DisplayName("staging a country with nothing imported is refused before anything is deleted")
    void emptySourceDoesNotWipeStaging() {
        source();

        assertThatThrownBy(() -> service.run(EGYPT, 7L))
                .isInstanceOf(IllegalArgumentException.class);

        // Crucially the delete has not run: an empty landing table must not be
        // a way to silently empty the staging table.
        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("no country is not every country")
    void nullCountryIsRefused() {
        assertThatThrownBy(() -> service.run(null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }
}
