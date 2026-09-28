package com.dilaw.mohe.approval.model;

import com.dilaw.mohe.codes.CodeList;

import java.util.EnumSet;
import java.util.Set;

/**
 * The status of an approval request — the state machine's key, persisted as
 * a string ({@code @Enumerated(EnumType.STRING)}). Names, order and colours
 * live in {@code MOHECORE.LU_APPROVAL_STATUS} (V004/V072); the startup guard
 * proves the two agree.
 */
@CodeList("approval-status")
public enum ApprovalStatus {
    PENDING,
    IN_PROGRESS,
    APPROVED,
    REJECTED,
    EXECUTED;

    /** Requests still waiting for a decision — bound into the repository queries as {@code :#{T(…).OPEN}}. */
    public static final Set<ApprovalStatus> OPEN = EnumSet.of(PENDING, IN_PROGRESS);

    /** Requests that have reached a decision. */
    public static final Set<ApprovalStatus> FINAL = EnumSet.of(APPROVED, EXECUTED, REJECTED);

    public static final Set<String> OPEN_SET = Set.of(PENDING.name(), IN_PROGRESS.name());
    public static final Set<String> FINAL_SET = Set.of(APPROVED.name(), EXECUTED.name(), REJECTED.name());

    public boolean isOpen() {
        return this == PENDING || this == IN_PROGRESS;
    }
}
