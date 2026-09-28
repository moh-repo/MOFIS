package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.event.ApprovalRequestCreatedEvent;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.codes.AlertSeverity;
import com.dilaw.mohe.codes.AlertType;
import com.dilaw.mohe.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import com.dilaw.mohe.approval.event.ApprovalRequestDecidedEvent;
import com.dilaw.mohe.service.codelist.CodeListService;

/**
 * Announces every new approval request in the system notices
 * ({@code MOHE_ALERTS.alerts}), the "إشعارات النظام" panel on the dashboard
 * (review round 2, H2). Until now a request appeared only in the pending
 * approvals list and the topbar badge.
 *
 * <p>After commit, in a transaction of its own: a request that rolls back is
 * never announced, and a notice that fails to save never undoes the request.
 *
 * <p>The notices table has no country column and is shown to every dashboard
 * user, so the text names the request number, the operation and the kind of
 * record only — no student, no amounts, no country. Opening the request itself
 * still goes through the scoped approval screens.
 */
@Component
public class ApprovalAlertListener {

    private static final Logger log = LoggerFactory.getLogger(ApprovalAlertListener.class);

    /** The source_ref of a request's notice (V125). */
    static String sourceRef(Long requestId) {
        return "APPROVAL:" + requestId;
    }

    private final AlertService alertService;
    private final CodeListService codeLists;

    public ApprovalAlertListener(AlertService alertService, CodeListService codeLists) {
        this.alertService = alertService;
        this.codeLists = codeLists;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequestCreated(ApprovalRequestCreatedEvent event) {
        try {
            alertService.createAlert("طلب موافقة جديد", message(event, "ar"),
                    "New approval request", message(event, "en"),
                    AlertType.INFO, AlertSeverity.MEDIUM, sourceRef(event.requestId()));
        } catch (RuntimeException e) {
            // The request is committed and listed under pending approvals either way.
            log.warn("Could not announce approval request {} in the system notices: {}",
                    event.requestId(), e.toString());
        }
    }

    /**
     * A decided request no longer awaits anyone, so its notice is resolved. The
     * panel used to fill up with notices for requests long since executed or
     * rejected (H2).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequestDecided(ApprovalRequestDecidedEvent event) {
        try {
            alertService.resolveBySource(sourceRef(event.requestId()));
        } catch (RuntimeException e) {
            log.warn("Could not resolve the notice of approval request {}: {}", event.requestId(), e.toString());
        }
    }

    /**
     * The notice text in one language. The kind of record and the operation come
     * from the code lists (approval-entity-type, request-type), which V122 gave
     * English names; it was an Arabic-only map here (W3-08, H2).
     */
    String message(ApprovalRequestCreatedEvent event, String lang) {
        String entity = event.entityType() == null ? "—"
                : codeLists.label("approval-entity-type", event.entityType(), lang);
        String operation = event.requestType() == null ? "—"
                : codeLists.label("request-type", event.requestType().name(), lang);
        if ("en".equals(lang)) {
            return "Request " + event.requestId() + ": " + operation + " — " + entity
                    + ". Awaiting approval on the pending approvals page.";
        }
        return "طلب رقم " + event.requestId() + ": " + operation + " — " + entity
                + ". بانتظار الموافقة في صفحة الموافقات المعلقة.";
    }
}
