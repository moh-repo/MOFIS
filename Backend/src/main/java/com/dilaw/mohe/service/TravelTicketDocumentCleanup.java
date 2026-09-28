package com.dilaw.mohe.service;

import com.dilaw.mohe.approval.event.ApprovalRequestDecidedEvent;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.util.List;

/**
 * Removes travel-ticket PDFs nothing uses any more (review T3, plan W4-09).
 *
 * <p>A ticket's PDF is stored when it is attached, before the ticket is approved,
 * and joins the student's documents only when the ticket is written. A rejected
 * ticket, a pending edit that attached another PDF, and an approved edit that
 * replaced the PDF each left the old file on disk and its row in
 * STUDENT_DOCUMENTS for good.
 *
 * <p>A document is removed only while it is a ticket document that no ticket
 * and no open ticket request names, and only after the transaction that let go
 * of it has committed: a file deleted inside a transaction that then rolls back
 * cannot be put back.
 */
@Service
public class TravelTicketDocumentCleanup {

    private static final Logger log = LoggerFactory.getLogger(TravelTicketDocumentCleanup.class);
    static final String ENTITY_TYPE = "STUDENT_TRAVEL_TICKET";

    private final StudentDocumentRepository documents;
    private final StudentFileStorageService storage;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TravelTicketDocumentCleanup(StudentDocumentRepository documents, StudentFileStorageService storage,
                                       JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.documents = documents;
        this.storage = storage;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** A rejected ticket request lets go of the PDF it carried. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequestDecided(ApprovalRequestDecidedEvent event) {
        if (event.outcome() != ApprovalStatus.REJECTED) {
            return;
        }
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT RequestData FROM ALERTCORE.ApprovalRequests WHERE Id = ? AND EntityType = ?",
                    String.class, event.requestId(), ENTITY_TYPE);
            if (!rows.isEmpty()) {
                removeIfUnused(documentIdIn(rows.get(0)));
            }
        } catch (RuntimeException e) {
            log.warn("Could not clean up the ticket document of rejected request {}: {}", event.requestId(), e.toString());
        }
    }

    /** The documentId a ticket payload names, or null. */
    Integer documentIdIn(String requestData) {
        if (requestData == null || requestData.isBlank()) {
            return null;
        }
        try {
            JsonNode id = objectMapper.readTree(requestData).get("documentId");
            return id == null || id.isNull() ? null : id.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    /** The document the open request on ticket {@code ticketId} names, or null: read before the request is replaced. */
    public Integer documentOfOpenRequest(String ticketId) {
        if (ticketId == null) {
            return null;
        }
        List<String> rows = jdbc.queryForList(
                "SELECT RequestData FROM ALERTCORE.ApprovalRequests WHERE EntityType = ? AND EntityId = ? "
                        + "AND Status IN ('PENDING', 'IN_PROGRESS')", String.class, ENTITY_TYPE, ticketId);
        return rows.isEmpty() ? null : documentIdIn(rows.get(0));
    }

    /** {@link #removeIfUnused} once the current transaction commits, or now when there is none. */
    public void removeAfterCommit(Integer documentId) {
        if (documentId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(documentId);
                }
            });
        } else {
            safely(documentId);
        }
    }

    private void safely(Integer documentId) {
        try {
            removeIfUnused(documentId);
        } catch (RuntimeException e) {
            log.warn("Could not clean up ticket document {}: {}", documentId, e.toString());
        }
    }

    /**
     * Deletes the ticket document and its file when nothing names it.
     *
     * @return whether it was removed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean removeIfUnused(Integer documentId) {
        if (documentId == null) {
            return false;
        }
        StudentDocument doc = documents.findById(documentId).orElse(null);
        if (doc == null || !StudentTravelTicketService.TICKET_DOCUMENT_TYPE.equals(doc.getDocumentType())) {
            return false;
        }
        Integer tickets = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_TRAVEL_TICKET WHERE DOCUMENT_ID = ?", Integer.class, documentId);
        if (tickets != null && tickets > 0) {
            return false;
        }
        // Jackson writes no spaces, so the id is followed by a comma or the closing brace.
        Integer requests = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE EntityType = ? "
                        + "AND Status IN ('PENDING', 'IN_PROGRESS') "
                        + "AND (RequestData LIKE ? OR RequestData LIKE ?)", Integer.class, ENTITY_TYPE,
                "%\"documentId\":" + documentId + ",%", "%\"documentId\":" + documentId + "}%");
        if (requests != null && requests > 0) {
            return false;
        }
        String path = doc.getFilePath();
        documents.delete(doc);
        documents.flush();
        try {
            if (path != null) {
                Files.deleteIfExists(storage.resolveStored(path));
            }
        } catch (Exception e) {
            log.warn("Ticket document {} removed; its file {} could not be deleted: {}", documentId, path, e.toString());
        }
        log.info("Removed unused travel-ticket document {} ({})", documentId, path);
        return true;
    }
}
