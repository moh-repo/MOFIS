package com.dilaw.mohe.ums.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Two permission codes one person must not hold together.
 *
 * <p>Security plan R-15 (6 Sep 2026): separation of duties as data. The pairs
 * are the maker/checker halves of the flows that move money —
 * {@code ALLOCATION.CREATE} / {@code ALLOCATION.APPROVE},
 * {@code PAYMENT_BATCH.CREATE} / {@code PAYMENT_BATCH.APPROVE} — seeded by
 * V092. {@code WAFAR.ADD} / {@code WAFAR.APPROVE} is seeded inactive: while
 * single-officer attachés exist (decision D-12) the per-transaction
 * maker-checker in {@code WafarSpendingService} is the control, and the pair is
 * switched on when every attaché has two finance users.
 *
 * <p>{@code SeparationOfDutiesService} refuses any assignment (a role or group
 * to a user, a direct grant, a permission to a role, a role to a group) whose
 * result would hold both halves of an active pair, with 409. Existing
 * violations are reported, not removed: resolving them is an administrative
 * decision.
 */
@Getter
@Setter
@Entity
@Table(name = "PermissionConflicts", schema = "UMSCORE")
public class UmsPermissionConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "CodeA", nullable = false, length = 100)
    private String codeA;

    @Column(name = "CodeB", nullable = false, length = 100)
    private String codeB;

    @Column(name = "Reason", length = 500)
    private String reason;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
