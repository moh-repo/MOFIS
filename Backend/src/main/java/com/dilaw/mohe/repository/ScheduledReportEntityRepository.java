package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ScheduledReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScheduledReportEntityRepository extends JpaRepository<ScheduledReportEntity, Integer> {

    List<ScheduledReportEntity> findByIsEnabledTrueAndScheduleStatusOrderByNextRunDateAsc(String status);

    @Query("SELECT r FROM ScheduledReportEntity r WHERE r.isEnabled = true AND r.scheduleStatus = :#{T(com.dilaw.mohe.codes.ScheduleStatus).ACTIVE} AND (r.nextRunDate IS NULL OR r.nextRunDate <= :now)")
    List<ScheduledReportEntity> findDueReports(java.time.Instant now);

    /**
     * Claims one due schedule for a run: a conditional update only one caller can win.
     *
     * <p>Returns 1 when this caller now owns the run and 0 when the schedule is not due,
     * not active, or already {@code RUNNING} - so two application instances (or two
     * overlapping ticks) cannot both send the same report. The due conditions are
     * re-checked here, not only in {@code findDueReports}, because the list that query
     * returned may be stale by the time each row is claimed.
     *
     * <p>{@code LAST_RUN_DATE} is stamped with the claim time and doubles as the start
     * marker of the run (the table has no started-at column): a {@code RUNNING} row whose
     * {@code LAST_RUN_DATE} is older than {@code staleBefore} is treated as a crashed run
     * and may be claimed again. The finaliser overwrites it with the finish time.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ScheduledReportEntity r
               SET r.lastRunStatus = :#{T(com.dilaw.mohe.codes.RunStatus).RUNNING}, r.lastRunDate = :now
             WHERE r.scheduleId = :id
               AND r.isEnabled = true
               AND r.scheduleStatus = :#{T(com.dilaw.mohe.codes.ScheduleStatus).ACTIVE}
               AND (r.nextRunDate IS NULL OR r.nextRunDate <= :now)
               AND (r.lastRunStatus IS NULL OR r.lastRunStatus <> :#{T(com.dilaw.mohe.codes.RunStatus).RUNNING}
                    OR r.lastRunDate IS NULL OR r.lastRunDate < :staleBefore)
            """)
    int claimForRun(@Param("id") Integer id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
