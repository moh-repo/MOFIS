package com.dilaw.mohe.service;

import com.dilaw.mohe.model.ScheduledReportEntity;
import com.dilaw.mohe.repository.ScheduledReportEntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim that stops a scheduled report being sent twice.
 *
 * <p>Two application instances, or two overlapping ticks, both read the same due row.
 * Whoever wins the conditional update runs the report; the other must get 0 and move
 * on. A run that crashed mid-way leaves the row {@code RUNNING}; that must not block
 * the schedule for ever, but it must not be re-run a minute later either.
 *
 * <p>Self-cleaning: the row is created here under a unique name and deleted after each
 * test. Nothing is sent — only the claim and finish transactions are exercised.
 */
@Tag("integration")
@SpringBootTest
class ScheduledReportClaimServiceTest {

    @Autowired private ScheduledReportClaimService claimService;
    @Autowired private ScheduledReportEntityRepository repository;

    private Integer id;

    @BeforeEach
    void createDueSchedule() {
        ScheduledReportEntity r = new ScheduledReportEntity();
        r.setScheduleName("TEST claim " + UUID.randomUUID());
        r.setReportType("STUDENT_LIST");
        r.setFrequency("WEEKLY");
        r.setNextRunDate(Instant.now().minus(1, ChronoUnit.HOURS));
        r.setIsEnabled(true);
        r.setScheduleStatus("ACTIVE");
        r.setCreatedBy("b6-claim-test");
        id = repository.save(r).getScheduleId();
    }

    @AfterEach
    void deleteSchedule() {
        if (id != null) {
            repository.deleteById(id);
        }
    }

    private ScheduledReportEntity reload() {
        return repository.findById(id).orElseThrow();
    }

    private void markRunningSince(Instant startedAt) {
        ScheduledReportEntity r = reload();
        r.setLastRunStatus("RUNNING");
        r.setLastRunDate(startedAt);
        repository.save(r);
    }

    @Test
    @DisplayName("the first claim wins and marks the row RUNNING; a second claim of the same row gets nothing")
    void firstClaimWinsSecondIsRefused() {
        Instant now = Instant.now();

        assertThat(claimService.claim(id, now)).isTrue();
        ScheduledReportEntity claimed = reload();
        assertThat(claimed.getLastRunStatus()).isEqualTo("RUNNING");
        assertThat(claimed.getLastRunDate()).as("claim time is the run's start marker").isNotNull();

        assertThat(claimService.claim(id, now)).as("second instance / overlapping tick").isFalse();
        assertThat(claimService.claim(id, Instant.now().plusSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("a schedule that is not yet due cannot be claimed even though it is enabled and active")
    void notDueIsNotClaimed() {
        ScheduledReportEntity r = reload();
        r.setNextRunDate(Instant.now().plus(1, ChronoUnit.HOURS));
        repository.save(r);

        assertThat(claimService.claim(id, Instant.now())).isFalse();
        assertThat(reload().getLastRunStatus()).isNull();
    }

    @Test
    @DisplayName("a disabled schedule cannot be claimed")
    void disabledIsNotClaimed() {
        ScheduledReportEntity r = reload();
        r.setIsEnabled(false);
        repository.save(r);

        assertThat(claimService.claim(id, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("RUNNING for ten minutes is a live run: not reclaimable")
    void freshRunningIsNotReclaimable() {
        markRunningSince(Instant.now().minus(10, ChronoUnit.MINUTES));

        assertThat(claimService.claim(id, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("RUNNING for longer than the stale window is a crashed run: reclaimable")
    void staleRunningIsReclaimable() {
        Instant now = Instant.now();
        markRunningSince(now.minus(ScheduledReportClaimService.STALE_RUNNING).minus(Duration.ofMinutes(5)));

        assertThat(claimService.claim(id, now)).isTrue();
        assertThat(reload().getLastRunDate())
                .as("the reclaim restarts the stale clock")
                .isAfter(now.minus(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("finishing a successful run records the count, advances the next run and releases the claim")
    void successfulFinishAdvancesAndReleases() {
        Instant now = Instant.now();
        Instant next = now.plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        assertThat(claimService.claim(id, now)).isTrue();

        claimService.finish(id, true, 42, next);

        ScheduledReportEntity done = reload();
        assertThat(done.getLastRunStatus()).isEqualTo("SUCCESS");
        assertThat(done.getRecordsInLastRun()).isEqualTo(42);
        assertThat(done.getNextRunDate()).isEqualTo(next);
        assertThat(claimService.claim(id, Instant.now())).as("no longer due").isFalse();
    }

    @Test
    @DisplayName("finishing a failed run leaves the next run date alone so the next tick retries it")
    void failedFinishLeavesNextRunForRetry() {
        Instant originalNext = reload().getNextRunDate();
        Instant now = Instant.now();
        assertThat(claimService.claim(id, now)).isTrue();

        claimService.finish(id, false, null, null);

        ScheduledReportEntity done = reload();
        assertThat(done.getLastRunStatus()).isEqualTo("FAILED");
        assertThat(done.getNextRunDate()).isEqualTo(originalNext);
        assertThat(claimService.claim(id, Instant.now())).as("retried on the next tick").isTrue();
    }

    @Test
    @DisplayName("a failed run that supplies a next run advances it, so a broken schedule stops spinning")
    void failedFinishWithANextRunAdvancesIt() {
        Instant now = Instant.now();
        Instant next = now.plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        assertThat(claimService.claim(id, now)).isTrue();

        // What the scheduler does with a ScheduleConfigurationException: the run failed
        // and the next tick would fail identically, so the date moves on. SCHEDULED_REPORT
        // 220 on the development database — owner SAMPLE_DATA, who does not exist, next run
        // 30 August 2026 — was claimed, failed and logged a stack trace every fifteen
        // minutes because this path used to leave the date in the past.
        claimService.finish(id, false, null, next);

        ScheduledReportEntity done = reload();
        assertThat(done.getLastRunStatus())
                .as("still FAILED: the screen must show the schedule as broken")
                .isEqualTo("FAILED");
        assertThat(done.getNextRunDate()).isEqualTo(next);
        assertThat(claimService.claim(id, Instant.now()))
                .as("not due again until the next occurrence")
                .isFalse();
    }
}
