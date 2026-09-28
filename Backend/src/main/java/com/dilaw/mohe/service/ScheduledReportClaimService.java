package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.RunStatus;
import com.dilaw.mohe.repository.ScheduledReportEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * The two short, committed transactions around a scheduled report run.
 *
 * <p>Separate from {@link ScheduledReportExecutionService} because Spring's
 * transaction proxy does not see a call a bean makes to itself: {@code REQUIRES_NEW}
 * on a private helper in the execution service would be ignored, and the claim would
 * silently join whatever transaction the caller had — which is the bug being fixed.
 *
 * <p>Claim and finish each commit on their own so the {@code RUNNING} marker is
 * visible to other instances immediately, and so the report run and the e-mail
 * happen with no transaction open that a later failure could roll back.
 */
@Service
public class ScheduledReportClaimService {

    /**
     * How long a {@code RUNNING} schedule is trusted before it is presumed crashed and
     * offered to the next tick. Well above any real run, so a slow report is not sent
     * twice; well below a day, so a crash does not silence a daily report for good.
     */
    static final Duration STALE_RUNNING = Duration.ofHours(2);

    @Autowired
    private ScheduledReportEntityRepository repository;

    /**
     * Tries to take the run for {@code scheduleId}. True only for the one caller whose
     * conditional update matched — see {@link ScheduledReportEntityRepository#claimForRun}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Integer scheduleId, Instant now) {
        return repository.claimForRun(scheduleId, now, now.minus(STALE_RUNNING)) == 1;
    }

    /**
     * Records the outcome of a claimed run and releases the claim.
     *
     * <p>The next run date moves whenever the caller supplies one, and is left alone
     * when it does not. That is the whole of the policy, and it lets the caller say
     * which kind of failure this was without this method having to know: a possibly
     * transient failure passes {@code null} and the schedule is retried on the next
     * tick, as it always was; a {@link ScheduleConfigurationException} passes the next
     * occurrence, because retrying in fifteen minutes cannot fix a deleted owner and
     * would log the same error ninety-six times a day for ever. The record count is
     * stored on success only — a failed run counted nothing.
     *
     * <p>Either way {@code LAST_RUN_STATUS} leaves {@code RUNNING}, and a failure is
     * still recorded as {@code FAILED} so the schedule reads as broken on the screen.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Integer scheduleId, boolean success, Integer recordCount, Instant nextRun) {
        repository.findById(scheduleId).ifPresent(r -> {
            r.setLastRunStatus(success ? RunStatus.SUCCESS : RunStatus.FAILED);
            r.setLastRunDate(Instant.now());
            if (success) {
                r.setRecordsInLastRun(recordCount);
            }
            if (nextRun != null) {
                r.setNextRunDate(nextRun);
            }
            repository.save(r);
        });
    }
}
