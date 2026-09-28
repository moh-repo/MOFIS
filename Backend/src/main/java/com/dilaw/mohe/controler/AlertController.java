package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.model.Alert;
import com.dilaw.mohe.service.AlertService;
import com.dilaw.mohe.service.AlertStreamTicketService;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * System alerts: the topbar badge, the alert list and the SSE stream.
 *
 * <p>Reads and "mark as read" are open to every signed-in user — the badge is on every
 * page. Creating, re-classifying and deleting alerts are administrative and carry
 * {@link MoheSecurityExpressions#ALERT_EVENTS_MANAGE}; until August 2026 they carried
 * nothing, and any authenticated account could forge or remove system alerts.
 */
@RestController
@RequestMapping("api/v1/alerts")
@PreAuthorize("isAuthenticated()")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);
    private static final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Autowired
    AlertService alertService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AlertStreamTicketService streamTicketService;

    @GetMapping("/top10")
    public List<Alert> getTop10Alerts() {
        return alertService.getTop10Alerts();
    }

    @GetMapping("/today")
    public List<Alert> getTodayAlerts() {
        return alertService.getTodayAlerts();
    }

    @GetMapping("/yesterday")
    public List<Alert> getYesterdayAlerts() {
        return alertService.getYesterdayAlerts();
    }

    @GetMapping("/active")
    public List<Alert> getActiveAlerts() {
        return alertService.getActiveAlerts();
    }

    @GetMapping("/statistics")
    public Map<String, Object> getAlertStatistics() {
        return alertService.getAlertStatistics();
    }

    /** The signed-in account's unread alerts — the topbar badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        return Map.of("count", alertService.countUnread(SecurityRequestUtils.requireUserId()));
    }

    /**
     * Marks all alerts read for the signed-in account only (live QA N10): it marked them
     * for everybody, under a {@code readBy} name the request supplied.
     */
    @PutMapping("/mark-all-read")
    public Map<String, Object> markAllRead() {
        long marked = alertService.markAllRead(SecurityRequestUtils.requireUserId());
        return Map.of("marked", marked);
    }

    @PostMapping("/create")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_MANAGE)
    public Alert createAlert(@Valid @RequestBody Alert alert) {
        return alertService.createAlert(
            alert.getAlertTitle(),
            alert.getAlertMessage(),
            alert.getAlertType(),
            alert.getAlertPriority()
        );
    }

    /** The signed-in account has read this alert (N10: the reader came from {@code ?readBy=}). */
    @PutMapping("/{id}/read")
    public Alert markAsRead(@PathVariable Integer id) {
        return alertService.markAsRead(id, SecurityRequestUtils.requireUserId());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_MANAGE)
    public Alert updateAlertStatus(@PathVariable Integer id, @RequestParam String status) {
        return alertService.updateAlertStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_MANAGE)
    public void deleteAlert(@PathVariable Integer id) {
        alertService.deleteAlert(id);
    }

    @PostMapping("/sample")
    @PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
    public String createSampleAlerts() {
        alertService.createSampleAlerts();
        return "Sample alerts created successfully";
    }

    /**
     * Mints a short-lived, single-use ticket for the SSE stream. The caller is
     * authenticated normally (class-level {@code isAuthenticated()}); the ticket
     * carries the presented JWT so the stream request that follows goes through
     * the ordinary session validation. See {@link AlertStreamTicketService}.
     */
    @PostMapping("/stream/ticket")
    public Map<String, String> streamTicket(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String jwt = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7).trim() : null;
        return Map.of("ticket", streamTicketService.issue(jwt));
    }

    /**
     * SSE stream — clients subscribe and receive push events whenever a new alert is created.
     * Keep-alive heartbeat every 30 seconds prevents proxy timeouts.
     * Authenticated via the single-use {@code ?ticket=} redeemed in {@code JwtAuthFilter}.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts() {
        SseEmitter emitter = new SseEmitter(0L);  // no timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /**
     * Push a newly created alert to all connected SSE clients.
     * Called internally after alert creation.
     */
    public void pushAlert(Alert alert) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(
                Map.of("alertId", alert.getAlertId(),
                       "title", alert.getAlertTitle() != null ? alert.getAlertTitle() : "",
                       "priority", alert.getAlertPriority() != null ? alert.getAlertPriority() : "",
                       "type", alert.getAlertType() != null ? alert.getAlertType() : ""));
        } catch (Exception e) {
            payload = "{\"alertId\":" + alert.getAlertId() + "}";
        }
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(payload));
            } catch (IOException ex) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
