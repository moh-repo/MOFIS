package com.dilaw.mohe;

import com.dilaw.mohe.service.ScholarshipEtlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Editing a staged row by hand.
 *
 * <p>Against a <strong>mocked</strong> {@link JdbcTemplate}, for the reason the
 * sibling ETL tests give: the Spring suites here run against the live dev
 * database, and these assertions are about the statement, which a mock shows
 * exactly.
 *
 * <p>The UPDATE is where a column name from the client would end up
 * concatenated into SQL, so several of these exist purely to show that the
 * whitelist — not the request — decides what is written.
 */
class ScholarshipEtlEditTest {

    private static final int ROW = 1685;

    private ScholarshipEtlService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new ScholarshipEtlService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

        // The row exists and every lookup id is valid. Individual tests narrow
        // this where that is the thing under test.
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("STUDENT_ID", ROW)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    /** The UPDATE the service issued. */
    private String updateSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        return sql.getValue();
    }

    private Object[] updateParams() {
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), params.capture());
        return params.getValue();
    }

    private static Map<String, Object> changes(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return out;
    }

    @Test
    @DisplayName("writes the fields the form changed")
    void writesChangedFields() {
        service.updateStaged(ROW, changes("STUDENT_NAME", "دعاء علي", "STUDENT_AMOUNT", "1500"), 7L);

        assertThat(updateSql()).contains("STUDENT_NAME = ?");
        assertThat(updateSql()).contains("STUDENT_AMOUNT = ?");
        assertThat(updateParams()).contains("دعاء علي", 1500);
    }

    @Test
    @DisplayName("a column that is not editable is not written")
    void ignoresUnknownColumns() {
        service.updateStaged(ROW, changes(
                "STUDENT_NAME", "س",
                "IS_DELETED", 1,
                "RECORD_VISIBILITY", "SECRET"), 7L);

        assertThat(updateSql()).doesNotContain("IS_DELETED");
        assertThat(updateSql()).doesNotContain("RECORD_VISIBILITY");
    }

    @Test
    @DisplayName("the landing row id cannot be edited away")
    void studentIdIsNotEditable() {
        service.updateStaged(ROW, changes("STUDENT_NAME", "س", "STUDENT_ID", 999999), 7L);

        // STUDENT_ID is this row's lineage and its primary key; editing it
        // would orphan both. It may appear once, in the WHERE, and nowhere else.
        assertThat(updateSql()).contains("WHERE STUDENT_ID = ?");
        assertThat(updateSql().split("STUDENT_ID = \\?", -1)).hasSize(2);
    }

    @Test
    @DisplayName("the country cannot be edited, or the row outlives every future run")
    void countryIsNotEditable() {
        service.updateStaged(ROW, changes("STUDENT_NAME", "س", "STUDY_COUNTRY_IN", 1000), 7L);

        // The ETL delete is scoped by STUDY_COUNTRY_IN, so a row moved out of
        // its country would never be cleaned up by any subsequent run.
        assertThat(updateSql()).doesNotContain("STUDY_COUNTRY_IN");
    }

    @Test
    @DisplayName("a column name carrying SQL is not a column name")
    void columnNamesCannotCarrySql() {
        service.updateStaged(ROW, changes(
                "STUDENT_NAME", "س",
                "STUDENT_AMOUNT = 0, IS_DELETED", 1), 7L);

        assertThat(updateSql()).doesNotContain("IS_DELETED");
        assertThat(updateSql()).contains("SET STUDENT_NAME = ?, IS_EDITED = 1");
    }

    @Test
    @DisplayName("a lookup id that does not exist is refused before the statement runs")
    void rejectsUnknownLookupId() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        assertThatThrownBy(() -> service.updateStaged(ROW, changes("STUDENT_DEGREE_ID", 88888), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STUDENT_DEGREE");

        // A foreign-key violation surfacing as a 500 tells the person filling
        // in the form nothing at all.
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("clearing a lookup is allowed, and is not checked against the lookup")
    void clearingALookupIsFine() {
        service.updateStaged(ROW, changes("STUDENT_GENDER", ""), 7L);

        assertThat(updateSql()).contains("STUDENT_GENDER = ?");
        assertThat(updateParams()[0]).isNull();
    }

    @Test
    @DisplayName("saving marks the row as edited, with who and when")
    void marksTheRowEdited() {
        service.updateStaged(ROW, changes("STUDENT_NAME", "س"), 7L);

        assertThat(updateSql()).contains("IS_EDITED = 1");
        assertThat(updateSql()).contains("EDITED_AT = SYSUTCDATETIME()");
        assertThat(updateSql()).contains("EDITED_BY = ?");
        assertThat(updateParams()).containsExactly("س", 7L, ROW);
    }

    @Test
    @DisplayName("a row cannot be saved without a student name")
    void nameCannotBeCleared() {
        assertThatThrownBy(() -> service.updateStaged(ROW, changes("STUDENT_NAME", "   "), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("student name");
    }

    @Test
    @DisplayName("a request that changes nothing is refused rather than flagging the row")
    void emptyChangeSetIsRefused() {
        assertThatThrownBy(() -> service.updateStaged(ROW, changes("NOT_A_COLUMN", 1), 7L))
                .isInstanceOf(IllegalArgumentException.class);

        // Otherwise a no-op save would take the row out of the ETL reach for
        // nothing at all.
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("editing a row that does not exist is a null, not an update")
    void missingRowIsNull() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThat(service.updateStaged(ROW, changes("STUDENT_NAME", "س"), 7L)).isNull();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("handing a row back to the ETL clears the flag and its provenance")
    void clearEditResetsTheFlag() {
        service.clearStagedEdit(ROW);

        assertThat(updateSql()).contains("IS_EDITED = 0");
        assertThat(updateSql()).contains("EDITED_AT = NULL");
        assertThat(updateSql()).contains("EDITED_BY = NULL");
    }

    @Test
    @DisplayName("a run leaves hand-corrected rows alone")
    void runPreservesEditedRows() {
        // One landing row, already staged and already edited.
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("STUDENT_SCHOLARSHIP_DATA_IMPORT")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ID", ROW);
                row.put("STUDENT_NAME", "س");
                row.put("HDR_QUARTER_NO", 3);
                row.put("HDR_YEAR_NO", 2025);
                return List.of(row);
            }
            return List.of();
        });
        when(jdbc.queryForList(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(List.of(ROW));

        Map<String, Object> report = service.run(1, 7L);

        assertThat(updateSql()).contains("IS_EDITED = 0 OR IS_EDITED IS NULL");
        assertThat(report.get("rowsPreserved")).isEqualTo(1);
        assertThat(report.get("rowsStaged")).isEqualTo(0);

        // Nothing reinserted: the id is taken, so an insert would collide on
        // the primary key rather than refresh anything.
        verify(jdbc, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }
}
