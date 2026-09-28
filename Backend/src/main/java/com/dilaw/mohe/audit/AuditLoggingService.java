package com.dilaw.mohe.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application-level audit logging. Currently a no-op: audit is written by
 * database triggers to AUDITLOG.AuditTrail. Kept for API compatibility with
 * services that call logUpdate/logDelete.
 */
@Service
public class AuditLoggingService {

    /** No-op: audit is written by database triggers to AUDITLOG.AuditTrail. */
    @Transactional
    public void logUpdate(Object entity, Object oldEntity) {
        // Rely on DB triggers; avoid double-logging.
    }

    /** No-op: audit is written by database triggers to AUDITLOG.AuditTrail. */
    @Transactional
    public void logDelete(Object entity) {
        // Rely on DB triggers; avoid double-logging.
    }
}
