package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.AlertSeverity;
import com.dilaw.mohe.codes.AlertType;
import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily job that raises a WARNING notice for documents expiring soon.
 *
 * <p>The window and the priority ladder are {@code SYS_PARAMETER} rows (V072):
 * {@code ALERTS.DOC_EXPIRY_WARN_DAYS} says how far ahead to look;
 * {@code ALERTS.DOC_EXPIRY_CRITICAL_DAYS} and {@code ALERTS.DOC_EXPIRY_HIGH_DAYS}
 * decide CRITICAL / HIGH / MEDIUM. Runs at 07:00 server time every day; "today"
 * is taken in the application time zone, not the JVM default.
 */
@Service
public class DocumentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryScheduler.class);

    @Autowired
    private StudentDocumentRepository documentRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private AppTimeZone appTimeZone;

    @Autowired
    private SysParameterService sysParameters;

    /**
     * In-JVM overlap guard; see the note on {@code AlertEventScheduler.running}
     * — a multi-instance deployment would still duplicate the expiry notices,
     * and the fix for that is the DB-claim pattern of ScheduledReportClaimService.
     */
    private final java.util.concurrent.atomic.AtomicBoolean running =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // Writes alerts through AlertService.createAlert, so this must not be a read-only
    // transaction: the inner @Transactional joins this one and inherits the flag.
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void checkExpiringDocuments() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Document expiry check is already running; skipping this trigger");
            return;
        }
        try {
            doCheckExpiringDocuments();
        } finally {
            running.set(false);
        }
    }

    private void doCheckExpiringDocuments() {
        int warnDays = sysParameters.intValue(SysParameterService.ALERTS_DOC_EXPIRY_WARN_DAYS);
        int criticalDays = sysParameters.intValue(SysParameterService.ALERTS_DOC_EXPIRY_CRITICAL_DAYS);
        int highDays = sysParameters.intValue(SysParameterService.ALERTS_DOC_EXPIRY_HIGH_DAYS);

        LocalDate today     = appTimeZone.today();
        LocalDate threshold = today.plusDays(warnDays);

        List<StudentDocument> expiring = documentRepository.findExpiringBetween(today, threshold);
        if (expiring.isEmpty()) {
            log.debug("DocumentExpiryScheduler: no documents expiring within {} days", warnDays);
            return;
        }

        log.info("DocumentExpiryScheduler: {} document(s) expiring within {} days", expiring.size(), warnDays);

        for (StudentDocument doc : expiring) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, doc.getExpiryDate());
            String title = "Document Expiring Soon / وثيقة تنتهي قريبًا";
            String message = String.format(
                "Student ID %d: document '%s' (type: %s) expires on %s (%d days remaining).",
                doc.getStudentId(),
                doc.getDocumentName() != null ? doc.getDocumentName() : "Unknown",
                doc.getDocumentType() != null ? doc.getDocumentType() : "Unknown",
                doc.getExpiryDate(),
                daysLeft
            );
            String priority = daysLeft <= criticalDays ? AlertSeverity.CRITICAL
                    : (daysLeft <= highDays ? AlertSeverity.HIGH : AlertSeverity.MEDIUM);
            alertService.createAlert(title, message, AlertType.WARNING, priority);
        }
    }
}
