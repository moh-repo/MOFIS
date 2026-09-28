package com.dilaw.mohe.approval.model;

import com.dilaw.mohe.ums.model.UmsUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a request for approval.
 * Stores the proposed changes and tracks approval progress through levels.
 */
@Getter
@Setter
@Entity
@Table(name = "ApprovalRequests", schema = "ALERTCORE")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Version
    @Column(name = "RowVersion", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "ConfigId", insertable = false, updatable = false)
    private Long configId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ConfigId", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ApprovalConfig config;

    @Enumerated(EnumType.STRING)
    @Column(name = "RequestType", nullable = false, length = 20)
    private RequestType requestType;

    @Column(name = "EntityType", nullable = false, length = 100)
    private String entityType;

    @Column(name = "EntityId", length = 50)
    private String entityId;

    @Column(name = "CountryId")
    private Integer countryId;

    @Column(name = "RequestData", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String requestData;

    @Column(name = "OriginalData", columnDefinition = "NVARCHAR(MAX)")
    private String originalData;

    @Column(name = "CurrentLevel", nullable = false)
    private Integer currentLevel = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 30)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "RequestedBy", nullable = false)
    private Long requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RequestedBy", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UmsUser requester;

    /** Populated by service after JOIN FETCH — not persisted. */
    @Transient
    private String requesterFullName;

    /**
     * Whether the caller may approve / reject the waiting level, as the server will
     * decide it; set on the pending lists only (FIX-PLAN D3-a: the screen shows the
     * buttons the server would honour, not a blanket permission). Not persisted.
     */
    @Transient
    private Boolean canApprove;

    @Transient
    private Boolean canReject;

    @Column(name = "RequestedAt", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "RejectionReason", length = 500)
    private String rejectionReason;

    @Column(name = "RejectedBy")
    private Long rejectedBy;

    @Column(name = "RejectedAt")
    private Instant rejectedAt;

    @Column(name = "ExecutedAt")
    private Instant executedAt;

    @Column(name = "ExecutedBy")
    private Long executedBy;

    /** FK to ALERTCORE.ApprovalRequests — parent STUDENT_DATA request when STUDENT_FINANCE is auto-linked. */
    @Column(name = "ParentApprovalRequestId")
    private Long parentApprovalRequestId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("levelOrder ASC")
    private List<ApprovalStep> steps = new ArrayList<>();
}
