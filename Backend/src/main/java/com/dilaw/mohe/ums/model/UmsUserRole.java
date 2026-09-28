package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "UserRoles", schema = "UMSCORE")
public class UmsUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private UmsUser user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RoleId", nullable = false)
    private UmsRole role;

    @Column(name = "AssignedAt", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "AssignedBy")
    private Long assignedBy;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    // R-21 (V098): the window in which this row counts. IsActive is switched by
    // the nightly RoleWindowJob as the window opens and closes, so the
    // permission reads keep using IsActive alone.
    @Column(name = "ValidFrom")
    private LocalDateTime validFrom;

    @Column(name = "ValidTo")
    private LocalDateTime validTo;

    /** The account whose roles were copied here by a delegation; null for a direct assignment. */
    @Column(name = "DelegatedFrom")
    private Long delegatedFrom;

    @Column(name = "Reason", length = 500)
    private String reason;

    @PrePersist
    protected void onCreate() {
        assignedAt = LocalDateTime.now();
    }
}
