package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One audited change, as it appears on the printed trail.
 *
 * <p>Deliberately does not carry {@code oldValues}/{@code newValues}. Those are
 * JSON blobs of the whole row; printing them turns a legible page into an
 * unreadable one, and the question a printed audit answers is "who changed what,
 * and when" — {@code changedColumns} answers that. The full before/after stays
 * available on the audit screen, where it can be expanded one row at a time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailRowDto {

    private Long logId;
    private LocalDateTime changedAt;

    private String schemaName;
    private String tableName;
    private String recordId;

    /** INSERT, UPDATE or DELETE. */
    private String operation;

    private String changedBy;

    /** Comma-separated column names, already flattened out of the stored JSON array. */
    private String changedColumns;

    private String ipAddress;

    private Integer countryId;
    private String countryName;
}
