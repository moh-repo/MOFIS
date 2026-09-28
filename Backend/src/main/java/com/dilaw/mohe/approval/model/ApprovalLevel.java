package com.dilaw.mohe.approval.model;

import com.dilaw.mohe.ums.model.UmsRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One step of an approval configuration.
 *
 * <p>Who may approve it (security plan N-03, 6 Sep 2026): the holder of
 * {@code requiredRoleId} if set, <em>and</em> the holder of
 * {@code requiredPositionCode} if set (a {@code MOHECORE.LU_POSITION} code,
 * resolved per country: "the attaché" means the attaché of the request's
 * country), <em>and</em> one of the named approvers if any are listed. All
 * three come from the staff table; the level name is a label only.
 * {@code ApprovalWorkflowService.approveStep} enforces it.
 *
 * <p>The named approvers travel in JSON as {@code namedApprovers}, a transient
 * copy the service fills and reads. The relation itself is {@code @JsonIgnore}:
 * a level is also serialised inside every approval request (through its
 * steps), outside any session, and {@code open-in-view} is off.
 */
@Getter
@Setter
@Entity
@Table(name = "ApprovalLevels", schema = "ALERTCORE")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ApprovalLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ConfigId", nullable = false)
    private ApprovalConfig config;

    @Column(name = "LevelOrder", nullable = false)
    private Integer levelOrder;

    @Column(name = "LevelName", nullable = false, length = 100)
    private String levelName;

    @Column(name = "LevelNameAr", length = 100)
    private String levelNameAr;

    @Column(name = "RequiredRoleId")
    private Long requiredRoleId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RequiredRoleId", insertable = false, updatable = false)
    private UmsRole requiredRole;

    /** N-03: the position the approver must hold (V093); null = any position. */
    @Column(name = "RequiredPositionCode", length = 50)
    private String requiredPositionCode;

    /**
     * N-03: named approvers, optionally per country. Empty = anyone meeting the
     * role and position.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ApprovalLevelApprover> approvers = new ArrayList<>();

    /** The JSON face of {@link #approvers}: {@code [{userId, countryId, userName, fullName}]}. */
    @Transient
    private List<ApprovalLevelApprover> namedApprovers = new ArrayList<>();

    @Column(name = "CanSkip", nullable = false)
    private Boolean canSkip = false;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();
}
