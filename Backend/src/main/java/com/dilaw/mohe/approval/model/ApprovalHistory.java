package com.dilaw.mohe.approval.model;

import com.dilaw.mohe.ums.model.UmsUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/**
 * Entity for approval workflow audit trail.
 * Records all actions taken on approval requests.
 */
@Getter
@Setter
@Entity
@Table(name = "ApprovalHistory", schema = "ALERTCORE")
public class ApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RequestId", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ApprovalRequest request;

    @Column(name = "Action", nullable = false, length = 50)
    private String action; // CREATED, APPROVED, REJECTED, EXECUTED, etc.

    @Column(name = "ActionBy", nullable = false)
    private Long actionBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ActionBy", insertable = false, updatable = false)
    @JsonIgnoreProperties({ "passwordHash", "passwordSalt", "userGroups", "userRoles", "userCountries",
            "hibernateLazyInitializer", "handler" })
    private UmsUser actor;

    @Column(name = "ActionAt", nullable = false)
    private Instant actionAt = Instant.now();

    @Column(name = "LevelOrder")
    private Integer levelOrder;

    @Column(name = "OldStatus", length = 30)
    private String oldStatus;

    @Column(name = "NewStatus", length = 30)
    private String newStatus;

    @Column(name = "Details", columnDefinition = "NVARCHAR(MAX)")
    private String details;
}
