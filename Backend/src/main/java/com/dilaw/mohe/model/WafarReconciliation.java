package com.dilaw.mohe.model;

import com.dilaw.mohe.codes.WafarReconciliationStatus;
import com.dilaw.mohe.codes.WafarReconciliationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "WAFAR_RECONCILIATION", schema = "MOHECORE")
public class WafarReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RECON_ID", nullable = false)
    private Integer reconId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Column(name = "RECON_DATE", nullable = false)
    private Instant reconDate;

    @Column(name = "MOHE_AMOUNT", nullable = false)
    private Integer moheAmount = 0;

    @Column(name = "WAFAR_AMOUNT", nullable = false)
    private Integer wafarAmount = 0;

    /** Computed column: MOHE_AMOUNT - WAFAR_AMOUNT. Read-only in Java. */
    @Column(name = "DELTA", insertable = false, updatable = false)
    private Integer delta;

    @Column(name = "RECON_STATUS", nullable = false, length = 30)
    private String reconStatus = WafarReconciliationStatus.PENDING;

    @Nationalized
    @Column(name = "NOTES", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "RESOLVED_BY", length = 100)
    private String resolvedBy;

    @Column(name = "RESOLVED_DATE")
    private Instant resolvedDate;
}
