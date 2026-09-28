package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.AlertSeverity;
import com.dilaw.mohe.codes.AlertType;
import com.dilaw.mohe.codes.NoticeStatus;
import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.controler.AlertController;
import com.dilaw.mohe.model.Alert;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.model.AlertRead;
import com.dilaw.mohe.repository.AlertReadRepository;
import com.dilaw.mohe.repository.AlertRepository;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The system notices ({@code MOHE_ALERTS.alerts}).
 *
 * <p>Until V072 every read here swallowed its exception and answered with
 * something else — "today's notices" became "the last ten notices of any
 * date". A failing query now fails; the dashboard shows the section's empty
 * state instead of the wrong list. Type and priority are validated against
 * {@code LU_ALERT_TYPE} and {@code LU_ALERT_SEVERITY} on creation.
 */
@Transactional(readOnly = true)
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Autowired
    AlertRepository alertRepository;

    /** Who has read what, per account (V144; live QA N10). */
    @Autowired
    private AlertReadRepository alertReadRepository;

    @Autowired
    private CodeListService codeLists;

    @Autowired
    private AppTimeZone appTimeZone;

    @Lazy
    @Autowired(required = false)
    private AlertController alertController;

    /** The ten most recent notices, most severe first — severity order is the code list's. */
    public List<Alert> getTop10Alerts() {
        Map<String, Integer> order = new HashMap<>();
        for (CodeRow sev : codeLists.rows("alert-severity")) {
            order.put(sev.code(), sev.sortOrder());
        }
        return withReadState(alertRepository.findTop10ByOrderByCreatedDateDesc().stream()
                .sorted(Comparator.comparing((Alert a) -> order.getOrDefault(
                        a.getAlertPriority() == null ? "" : a.getAlertPriority().toUpperCase(), Integer.MAX_VALUE))
                        .thenComparing(Alert::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList());
    }

    /**
     * "Today" and "yesterday" are half-open day ranges in the application time
     * zone ({@code mohe.report.timezone}) — the old native queries wrapped the
     * column in CONVERT(date, …), which forced a full table scan, and took the
     * day from the DB server's clock rather than the application's.
     */
    public List<Alert> getTodayAlerts() {
        LocalDate today = appTimeZone.today();
        return withReadState(alertRepository.findByCreatedDateInRange(
                appTimeZone.startOfDay(today), appTimeZone.startOfDay(today.plusDays(1))));
    }

    public List<Alert> getYesterdayAlerts() {
        LocalDate today = appTimeZone.today();
        return withReadState(alertRepository.findByCreatedDateInRange(
                appTimeZone.startOfDay(today.minusDays(1)), appTimeZone.startOfDay(today)));
    }

    public List<Alert> getActiveAlerts() {
        return withReadState(alertRepository.findUnexpiredByStatus(NoticeStatus.ACTIVE));
    }

    /**
     * Alert counters for the dashboard. A failure is logged and rethrown so the global
     * handler answers a clean 500; this used to answer 200 with zeros and the raw
     * exception message, which hid a broken query behind a healthy-looking widget.
     */
    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("totalAlerts", alertRepository.count());
            stats.put("activeAlerts", alertRepository.countByAlertStatus(NoticeStatus.ACTIVE));
            // The caller's own unread alerts (N10); every alert when no account is signed in.
            stats.put("unreadAlerts", SecurityRequestUtils.currentUserId()
                    .map(alertReadRepository::countUnreadFor)
                    .orElseGet(alertRepository::count));
            // Used to count the alert *type* CRITICAL, which no notice ever carries; the priority is meant.
            stats.put("criticalAlerts", alertRepository.countByAlertPriority(AlertSeverity.CRITICAL));
            stats.put("success", true);
        } catch (RuntimeException e) {
            log.error("Alert statistics failed", e);
            throw e;
        }
        return stats;
    }

    /**
     * A notice in both languages, tied to what raised it (V125, H2): resolved by
     * {@link #resolveBySource} when that is decided.
     */
    @Transactional
    public Alert createAlert(String title, String message, String titleEn, String messageEn,
                             String type, String priority, String sourceRef) {
        Alert alert = createAlert(title, message, type, priority);
        alert.setAlertTitleEn(titleEn);
        alert.setAlertMessageEn(messageEn);
        alert.setSourceRef(sourceRef);
        return alertRepository.save(alert);
    }

    /** Resolves the open notices a source raised; returns how many. */
    @Transactional
    public int resolveBySource(String sourceRef) {
        List<Alert> open = alertRepository.findBySourceRefAndAlertStatus(sourceRef, NoticeStatus.ACTIVE);
        open.forEach(a -> a.setAlertStatus(NoticeStatus.RESOLVED));
        alertRepository.saveAll(open);
        return open.size();
    }

    @Transactional
    public Alert createAlert(String title, String message, String type, String priority) {
        String typeCode = codeLists.require("alert-type", type).code();
        String priorityCode = codeLists.require("alert-severity", priority).code();
        Alert alert = new Alert(title, message, typeCode, priorityCode);
        Alert saved = alertRepository.save(alert);
        if (alertController != null) {
            try {
                alertController.pushAlert(saved);
            } catch (Exception e) {
                // The alert row is saved either way; only the live push failed.
                log.warn("SSE alert push failed for alert {}: {}", saved.getAlertId(), e.toString());
            }
        }
        return saved;
    }

    /**
     * {@code userId} has read the alert (live QA N10): a row of their own, so nobody
     * else's alert list changes. The reader comes from the session, never the request.
     * Marking twice is a no-op; an unknown alert gives null, as before.
     */
    @Transactional
    public Alert markAsRead(Integer alertId, long userId) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null) {
            return null;
        }
        if (!alertReadRepository.existsByAlertIdAndUserId(alertId, userId)) {
            alertReadRepository.save(new AlertRead(alertId, userId, Instant.now()));
        }
        alert.setIsRead(true);
        return alert;
    }

    @Transactional
    public Alert updateAlertStatus(Integer alertId, String status) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert != null) {
            alert.setAlertStatus(codeLists.require("notice-status", status).code());
            return alertRepository.save(alert);
        }
        return null;
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void deleteAlert(Integer alertId) {
        if (alertId == null || !alertRepository.existsById(alertId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Alert not found");
        }
        alertRepository.deleteById(alertId);
    }

    /** The alerts {@code userId} has not read (the topbar badge; N10). */
    public long countUnread(long userId) {
        return alertReadRepository.countUnreadFor(userId);
    }

    /** Marks every alert read for {@code userId} only (N10); returns how many were new. */
    @Transactional
    public long markAllRead(long userId) {
        return alertReadRepository.markAllReadFor(userId);
    }

    /** Sets each alert's {@code isRead} for the signed-in account; false when none is. */
    private List<Alert> withReadState(List<Alert> alerts) {
        java.util.Optional<Long> userId = SecurityRequestUtils.currentUserId();
        java.util.Set<Integer> read = new java.util.HashSet<>();
        List<Integer> ids = alerts.stream().map(Alert::getAlertId).filter(java.util.Objects::nonNull).toList();
        if (userId.isPresent() && !ids.isEmpty()) {
            read.addAll(alertReadRepository.findReadAmong(userId.get(), ids));
        }
        alerts.forEach(a -> a.setIsRead(read.contains(a.getAlertId())));
        return alerts;
    }

    // Method to create sample alerts for testing (kept as-is by the owner's decision of 30 Aug 2026)
    @Transactional
    public void createSampleAlerts() {
        if (alertRepository.count() == 0) {
            createAlert("System Maintenance", "Scheduled maintenance will occur tonight from 2 AM to 4 AM", AlertType.INFO, AlertSeverity.MEDIUM);
            createAlert("Database Backup", "Daily database backup completed successfully", AlertType.SUCCESS, AlertSeverity.LOW);
            createAlert("High CPU Usage", "Server CPU usage is above 90%", AlertType.WARNING, AlertSeverity.HIGH);
            createAlert("Security Alert", "Multiple failed login attempts detected", AlertType.ERROR, AlertSeverity.CRITICAL);
            createAlert("New Student Registration", "5 new students registered today", AlertType.INFO, AlertSeverity.LOW);
            createAlert("Payment Processing", "Payment gateway is experiencing delays", AlertType.WARNING, AlertSeverity.MEDIUM);
            createAlert("System Update", "System update completed successfully", AlertType.SUCCESS, AlertSeverity.LOW);
            createAlert("Storage Warning", "Database storage is 85% full", AlertType.WARNING, AlertSeverity.HIGH);
            createAlert("User Activity", "Unusual user activity detected", AlertType.ERROR, AlertSeverity.MEDIUM);
            createAlert("Backup Verification", "Weekly backup verification completed", AlertType.SUCCESS, AlertSeverity.LOW);
        }
    }
}
