package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link MoheQuarterCreateJobs}: quarter creation as a background job (review Q4). */
class MoheQuarterCreateJobsTest {

    private final MoheQuarterCreateService createService = mock(MoheQuarterCreateService.class);
    private final CountryScopeService countryScopeService = mock(CountryScopeService.class);
    /** Holds submitted work until the test runs it, so a job can be seen mid-flight. */
    private final List<Runnable> queued = new ArrayList<>();
    private final MoheQuarterCreateJobs jobs = new MoheQuarterCreateJobs();
    private final CountryScope scope = new CountryScope(13L, true, false, List.of(1));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobs, "createService", createService);
        ReflectionTestUtils.setField(jobs, "countryScopeService", countryScopeService);
        ReflectionTestUtils.setField(jobs, "executor", (TaskExecutor) queued::add);
        when(countryScopeService.getScope()).thenReturn(scope);
    }

    private static MoheQuarter quarter() {
        MoheQuarter q = new MoheQuarter();
        q.setQuarterYear(2026);
        q.setQuarterNumber(3);
        return q;
    }

    @Test
    @DisplayName("an invalid request is refused on the caller's thread and starts nothing")
    void validationIsSynchronous() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad period"))
                .when(createService).validate(any(), any());

        assertThatThrownBy(() -> jobs.start(quarter(), 1L))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(queued).isEmpty();
        assertThat(jobs.current()).isNull();
    }

    @Test
    @DisplayName("a second creation while one runs is refused with 409")
    void oneAtATime() {
        jobs.start(quarter(), 1L);

        assertThatThrownBy(() -> jobs.start(quarter(), 2L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(queued).hasSize(1);
    }

    @Test
    @DisplayName("progress and the summary reach the job's view, and the slot frees when done")
    void progressAndSummary() {
        when(createService.createWithSnapshot(any(), any(), any())).thenAnswer(inv -> {
            MoheQuarterCreateService.Progress p = inv.getArgument(2);
            p.started(941);
            p.advanced(941);
            return Map.of("quarterId", 13);
        });

        Map<String, Object> started = jobs.start(quarter(), 1L);
        String id = (String) started.get("jobId");
        assertThat(started.get("state")).isEqualTo("RUNNING");

        queued.remove(0).run();

        Map<String, Object> done = jobs.status(id);
        assertThat(done.get("state")).isEqualTo("DONE");
        assertThat(done.get("total")).isEqualTo(941L);
        assertThat(done.get("done")).isEqualTo(941);
        assertThat(done.get("summary")).isEqualTo(Map.of("quarterId", 13));
        assertThat(jobs.current()).isNull();
    }

    @Test
    @DisplayName("a failure is reported on the job, and the next creation may start")
    void failureIsReported() {
        when(createService.createWithSnapshot(any(), any(), any()))
                .thenThrow(new IllegalStateException("deadlock"));

        String id = (String) jobs.start(quarter(), 1L).get("jobId");
        queued.remove(0).run();

        Map<String, Object> failed = jobs.status(id);
        assertThat(failed.get("state")).isEqualTo("FAILED");
        assertThat((String) failed.get("error")).contains("nothing was changed");

        jobs.start(quarter(), 1L);
        assertThat(queued).hasSize(1);
        verify(createService, never()).createWithSnapshot(any());
    }
}
