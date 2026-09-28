package com.dilaw.mohe.approval.model;

import com.dilaw.mohe.ums.model.UmsUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/**
 * Entity representing a single approval step within a request.
 * Each step corresponds to one approval level.
 */
@Getter
@Setter
@Entity
@Table(name = "ApprovalSteps", schema = "ALERTCORE")
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RequestId", nullable = false)
    private ApprovalRequest request;

    @Column(name = "LevelOrder", nullable = false)
    private Integer levelOrder;

    @Column(name = "LevelId", insertable = false, updatable = false)
    private Long levelId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelId", nullable = false)
    private ApprovalLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "ApprovedBy")
    private Long approvedBy;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy", insertable = false, updatable = false)
    private UmsUser approver;

    @Column(name = "ApprovedAt")
    private Instant approvedAt;

    @Column(name = "Comments", length = 500)
    private String comments;
}
