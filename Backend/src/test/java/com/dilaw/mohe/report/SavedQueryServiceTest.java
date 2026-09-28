package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.model.SavedQuery;
import com.dilaw.mohe.service.SavedQueryService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Saved queries.
 *
 * <p>These skip until {@code V026__create_saved_query_table.sql} has been applied,
 * because the table it creates is the thing under test. They are written so that
 * running them after applying it is the whole verification.
 */
@Tag("integration")
@SpringBootTest
class SavedQueryServiceTest {

    private static final String NAME = "test-saved-query-" + Long.toHexString(System.nanoTime());

    @Autowired private SavedQueryService service;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private CountryScopeService countryScopeService;

    private boolean tableExists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'MOHECORE' AND TABLE_NAME = 'SAVED_QUERY'
                """, Integer.class);
        return count != null && count > 0;
    }

    @BeforeEach
    void setUp() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
        authenticateAs("query-test-owner");
    }

    /** Everything that reads or writes the table needs the table. */
    private void requireTable() {
        assumeTrue(tableExists(), "MOHECORE.SAVED_QUERY does not exist yet — apply V026");
    }

    @AfterEach
    void cleanUp() {
        if (tableExists()) {
            jdbc.update("DELETE FROM MOHECORE.SAVED_QUERY WHERE QUERY_NAME LIKE 'test-saved-query-%'");
        }
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    private static QuerySpecDto spec() {
        QuerySpecDto spec = new QuerySpecDto();
        spec.setSource("STUDENT_DATA");
        spec.setColumns(List.of("STUDENT_ID", "STUDENT_NAME"));
        spec.setLimit(5);
        return spec;
    }

    @Test
    @DisplayName("a saved query stores its spec, not SQL")
    void save_storesTheSpec() {
        requireTable();
        SavedQuery saved = service.save(NAME, "a description", false, spec());

        assertThat(saved.getSavedQueryId()).isNotNull();
        assertThat(saved.getSourceName()).isEqualTo("STUDENT_DATA");
        // Storing SQL would mean a statement written under one set of rules
        // running later under another.
        assertThat(saved.getSpecJson())
                .contains("STUDENT_DATA")
                .doesNotContain("SELECT")
                .doesNotContain("FROM");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("saving the same name again updates rather than duplicates")
    void save_isIdempotentPerName() {
        requireTable();
        Long first = service.save(NAME, "first", false, spec()).getSavedQueryId();

        QuerySpecDto changed = spec();
        changed.setColumns(List.of("STUDENT_ID"));
        SavedQuery second = service.save(NAME, "second", false, changed);

        assertThat(second.getSavedQueryId()).isEqualTo(first);
        assertThat(second.getDescription()).isEqualTo("second");
        assertThat(second.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("a query that cannot run cannot be saved")
    void save_validatesBeforeStoring() {
        QuerySpecDto invalid = new QuerySpecDto();
        invalid.setSource("NO_SUCH_TABLE");

        // No requireTable() here on purpose: the spec must be refused before
        // anything is written, so this holds whether or not the table exists.
        assertThatThrownBy(() -> service.save(NAME, null, false, invalid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown source");

        if (tableExists()) {
            assertThat(service.list()).noneSatisfy(q -> assertThat(q.getQueryName()).isEqualTo(NAME));
        }
    }

    @Test
    @DisplayName("a saved query runs and returns rows")
    void run_returnsRows() {
        requireTable();
        Long id = service.save(NAME, null, false, spec()).getSavedQueryId();

        QueryResultDto result = service.run(id, null, null);

        assertThat(result.getRowCount()).isBetween(1, 5);
        assertThat(result.getColumns()).extracting(QueryResultDto.Column::getName)
                .containsExactly("STUDENT_ID", "STUDENT_NAME");
    }

    @Test
    @DisplayName("running a saved query applies the runner's scope, not the saver's")
    void run_usesTheRunnersScope() {
        requireTable();
        QuerySpecDto savedSpec = spec();
        savedSpec.setCountryId(1);
        Long id = service.save(NAME, null, true, savedSpec).getSavedQueryId();

        // A different administrator, restricted to a country that holds no
        // students. The stored countryId must not widen what they see.
        authenticateAs("other-admin");
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(2L, false, false, List.of(999)));

        QueryResultDto result = service.run(id, null, null);

        assertThat(result.getSql()).contains("[STUDY_COUNTRY_IN] IN (?)");
        assertThat(result.getParameters())
                .as("the scope applied must be the runner's, not the one stored")
                .contains(999)
                .doesNotContain(1);
    }

    @Test
    @DisplayName("an unshared query is invisible to another user")
    void unshared_isPrivate() {
        requireTable();
        Long id = service.save(NAME, null, false, spec()).getSavedQueryId();

        authenticateAs("other-admin");

        assertThat(service.list()).noneSatisfy(q -> assertThat(q.getQueryName()).isEqualTo(NAME));
        assertThatThrownBy(() -> service.run(id, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not shared");
    }

    @Test
    @DisplayName("sharing makes a query visible but not editable")
    void shared_isVisibleNotEditable() {
        requireTable();
        Long id = service.save(NAME, null, true, spec()).getSavedQueryId();

        authenticateAs("other-admin");

        assertThat(service.list()).anySatisfy(q -> assertThat(q.getQueryName()).isEqualTo(NAME));
        assertThat(service.run(id, null, null).getRowCount()).isGreaterThan(0);
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the owner");
    }

    @Test
    @DisplayName("the owner can delete their query")
    void owner_canDelete() {
        requireTable();
        Long id = service.save(NAME, null, false, spec()).getSavedQueryId();

        service.delete(id);

        assertThat(service.list()).noneSatisfy(q -> assertThat(q.getQueryName()).isEqualTo(NAME));
    }
}
