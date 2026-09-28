package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.model.SavedQuery;
import com.dilaw.mohe.repository.SavedQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Report Templates screen shows a usage count, a row count and a last-used
 * date. Until V106 the view selected all three as literal NULL and the screen
 * printed empty cells. {@code SavedQueryService.run} is the only place that
 * knows any of them, so these tests guard that it still records them.
 */
class SavedQueryUsageRecordingTest {

    private SavedQueryRepository repository;
    private QueryExecutionService queryExecutionService;
    private SavedQueryService service;

    private static final String OWNER = "attache.jordan";

    @BeforeEach
    void setUp() {
        repository = mock(SavedQueryRepository.class);
        queryExecutionService = mock(QueryExecutionService.class);
        service = new SavedQueryService(repository, queryExecutionService, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(OWNER, "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SavedQuery stored(Integer usageCount) {
        SavedQuery q = new SavedQuery();
        q.setSavedQueryId(1L);
        q.setOwnerUsername(OWNER);
        q.setShared(Boolean.FALSE);
        q.setSpecJson("{\"source\":\"V_Student_List_Detailed\",\"columns\":[\"STUDENT_ID\"]}");
        q.setUsageCount(usageCount);
        return q;
    }

    private QueryResultDto resultWith(int rowCount) {
        QueryResultDto dto = new QueryResultDto();
        dto.setRowCount(rowCount);
        return dto;
    }

    @Test
    @DisplayName("a run increments the usage count and records the rows it returned")
    void runRecordsUsage() {
        SavedQuery saved = stored(4);
        when(repository.findById(1L)).thenReturn(Optional.of(saved));
        when(queryExecutionService.execute(any())).thenReturn(resultWith(196));

        service.run(1L, null, null);

        assertThat(saved.getUsageCount()).isEqualTo(5);
        assertThat(saved.getLastRowCount()).isEqualTo(196);
        assertThat(saved.getLastRunAt()).isNotNull();
        verify(repository).save(saved);
    }

    @Test
    @DisplayName("a query saved before V106 counts its first run as one, not as null+1")
    void firstRunOfAPreV106Query() {
        SavedQuery saved = stored(null);
        when(repository.findById(1L)).thenReturn(Optional.of(saved));
        when(queryExecutionService.execute(any())).thenReturn(resultWith(0));

        service.run(1L, null, null);

        assertThat(saved.getUsageCount()).isEqualTo(1);
        // Zero rows is a real answer, not a missing one.
        assertThat(saved.getLastRowCount()).isZero();
    }

    @Test
    @DisplayName("a run that fails is not counted as a use")
    void failedRunIsNotRecorded() {
        SavedQuery saved = stored(4);
        when(repository.findById(1L)).thenReturn(Optional.of(saved));
        when(queryExecutionService.execute(any()))
                .thenThrow(new IllegalStateException("column no longer exists"));

        assertThatThrownBy(() -> service.run(1L, null, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(saved.getUsageCount()).isEqualTo(4);
        assertThat(saved.getLastRunAt()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("last-run is recorded as its own fact, not inferred from the edit time")
    void lastRunIsDistinctFromLastEdit() {
        SavedQuery saved = stored(0);
        LocalDateTime edited = LocalDateTime.now().minusDays(30);
        saved.setUpdatedAt(edited);
        when(repository.findById(1L)).thenReturn(Optional.of(saved));
        when(queryExecutionService.execute(any())).thenReturn(resultWith(3));

        service.run(1L, null, null);

        assertThat(saved.getUpdatedAt()).isEqualTo(edited);
        assertThat(saved.getLastRunAt()).isAfter(edited);
    }
}
