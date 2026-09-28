package com.dilaw.mohe.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "AuditTrail", schema = "AUDITLOG")
public class MoheAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID")
    private Long logId;

    @Column(name = "TABLE_NAME", nullable = false, length = 255)
    private String tableName;

    @Column(name = "SCHEMA_NAME", nullable = false, length = 100)
    private String schemaName;

    @Column(name = "RECORD_ID", nullable = false, length = 255)
    private String recordId;

    @Column(name = "OPERATION", nullable = false, length = 50)
    private String operation; // 'UPDATE' or 'DELETE'

    @Column(name = "OLD_VALUES", columnDefinition = "NVARCHAR(MAX)")
    private String oldValues; // JSON format

    @Column(name = "NEW_VALUES", columnDefinition = "NVARCHAR(MAX)")
    private String newValues; // JSON format

    @Column(name = "CHANGED_COLUMNS", columnDefinition = "NVARCHAR(MAX)")
    private String changedColumns; // JSON array

    @Column(name = "CHANGED_BY", length = 100)
    private String changedBy;

    @Column(name = "CHANGED_AT", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "IP_ADDRESS", length = 50)
    private String ipAddress;

    @Column(name = "SESSION_ID", length = 255)
    private String sessionId;

    @Column(name = "APPLICATION_NAME", length = 100)
    private String applicationName = "MOHE_SYSTEM";

    /**
     * Country of the record this row describes, stamped by the database (V047) —
     * never by application code, which is why it is not writable from here.
     *
     * <p>NULL means the audited record has no country of its own: a UMS user
     * account, a role assignment. Those are ministry-level and
     * {@code MoheAuditLogService} shows them only to a ministry user.
     */
    @Column(name = "COUNTRY_ID", insertable = false, updatable = false)
    private Integer countryId;

    public MoheAuditLog() {
        this.changedAt = LocalDateTime.now();
    }
}
