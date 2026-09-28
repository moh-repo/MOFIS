package com.dilaw.mohe.audit;

import java.time.LocalDateTime;

/**
 * An audit row as the API sends it (review M1): the entity's fields, with the
 * old and new values passed through {@link AuditValueRedactor}. The controller
 * used to return the entity itself, password hashes included.
 */
public record AuditLogView(
        Long logId,
        String tableName,
        String schemaName,
        String recordId,
        String operation,
        String oldValues,
        String newValues,
        String changedColumns,
        String changedBy,
        LocalDateTime changedAt,
        String ipAddress,
        String sessionId,
        String applicationName,
        Integer countryId) {

    public static AuditLogView of(MoheAuditLog log) {
        return new AuditLogView(
                log.getLogId(),
                log.getTableName(),
                log.getSchemaName(),
                log.getRecordId(),
                log.getOperation(),
                AuditValueRedactor.redact(log.getOldValues()),
                AuditValueRedactor.redact(log.getNewValues()),
                log.getChangedColumns(),
                log.getChangedBy(),
                log.getChangedAt(),
                log.getIpAddress(),
                log.getSessionId(),
                log.getApplicationName(),
                log.getCountryId());
    }
}
