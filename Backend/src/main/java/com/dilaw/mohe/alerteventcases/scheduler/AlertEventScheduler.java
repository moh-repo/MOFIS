package com.dilaw.mohe.alerteventcases.scheduler;

import com.dilaw.mohe.alerteventcases.service.AlertEventDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AlertEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertEventScheduler.class);

    @Autowired
    private AlertEventDetectionService detectionService;

    /**
     * In-JVM overlap guard: a daily cron cannot overlap itself, but a manual
     * re-trigger (tests, an admin poking the bean) could. NOTE this does not
     * protect a multi-instance deployment — two app instances would both run
     * the 01:00 detection and duplicate every alert case. If the deployment
     * ever scales out, use the DB-claim pattern of ScheduledReportClaimService.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Runs daily at 01:00 AM server time
    @Scheduled(cron = "0 0 1 * * *")
    public void runDailyDetection() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Alert event detection is already running; skipping this trigger");
            return;
        }
        try {
            log.info("Alert event detection job started");
            int count = detectionService.runAllScenarios();
            log.info("Alert event detection job finished — {} new case(s) created", count);
        } catch (Exception e) {
            log.error("Alert event detection job encountered an error", e);
        } finally {
            running.set(false);
        }
    }
}
