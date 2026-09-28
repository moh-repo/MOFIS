package com.dilaw.mohe.util;

import java.util.Map;
import java.util.Set;

/**
 * Enforces valid student study-status transitions.
 *
 * Status IDs are looked up from the STUDENT_STUDY_STATUS table.
 * The constants below reflect common seed IDs; death transitions use the
 * resolved Deceased row ID from the database (see {@link #validate(Integer, Integer, Integer)}).
 *
 * Allowed transitions:
 *   PENDING_APPROVAL (1) → ACTIVE (2)
 *   ACTIVE (2)           → SUSPENDED (3) | GRADUATED (4) | DEFERRED (5) | CANCELLED (6)
 *   SUSPENDED (3)        → ACTIVE (2) | CANCELLED (6)
 *   DEFERRED (5)         → ACTIVE (2) | CANCELLED (6)
 *   GRADUATED (4)        → (terminal — no transitions allowed)
 *   CANCELLED (6)        → (terminal — no transitions allowed)
 *   DECEASED             → (terminal — set via death case workflow using resolved DB id)
 */
public final class StudentStatusTransitionValidator {

    // Study status IDs — adjust to match your STUDENT_STUDY_STATUS seed data
    public static final int STATUS_PENDING_APPROVAL = 1;
    public static final int STATUS_ACTIVE           = 2;
    public static final int STATUS_SUSPENDED        = 3;
    public static final int STATUS_GRADUATED        = 4;
    public static final int STATUS_DEFERRED         = 5;
    public static final int STATUS_CANCELLED        = 6;

    /** States from which a student may be marked deceased (death case workflow). */
    private static final Set<Integer> DECEASED_ELIGIBLE_FROM = Set.of(
            STATUS_PENDING_APPROVAL,
            STATUS_ACTIVE,
            STATUS_SUSPENDED,
            STATUS_DEFERRED
    );

    private static final Map<Integer, Set<Integer>> ALLOWED_TRANSITIONS = Map.of(
            STATUS_PENDING_APPROVAL, Set.of(STATUS_ACTIVE),
            STATUS_ACTIVE,           Set.of(STATUS_SUSPENDED, STATUS_GRADUATED, STATUS_DEFERRED, STATUS_CANCELLED),
            STATUS_SUSPENDED,        Set.of(STATUS_ACTIVE, STATUS_CANCELLED),
            STATUS_DEFERRED,         Set.of(STATUS_ACTIVE, STATUS_CANCELLED),
            STATUS_GRADUATED,        Set.of(),
            STATUS_CANCELLED,        Set.of()
    );

    private StudentStatusTransitionValidator() {}

    /**
     * Validates a study-status transition without a resolved Deceased id.
     * Transitions to Deceased are not permitted through this overload.
     */
    public static void validate(Integer currentStatusId, Integer newStatusId) {
        validate(currentStatusId, newStatusId, null);
    }

    /**
     * Throws {@link IllegalStateException} if the transition from {@code currentStatusId}
     * to {@code newStatusId} is not permitted.
     *
     * @param currentStatusId the student's current study status ID (null treated as PENDING_APPROVAL)
     * @param newStatusId     the requested new study status ID
     * @param resolvedDeceasedStatusId the Deceased status row id from STUDENT_STUDY_STATUS, or null
     */
    public static void validate(Integer currentStatusId, Integer newStatusId, Integer resolvedDeceasedStatusId) {
        if (newStatusId == null) {
            throw new IllegalArgumentException("New study status must not be null.");
        }
        int current = currentStatusId != null ? currentStatusId : STATUS_PENDING_APPROVAL;

        if (current == newStatusId) {
            return;
        }

        if (resolvedDeceasedStatusId != null && newStatusId.equals(resolvedDeceasedStatusId)) {
            if (!DECEASED_ELIGIBLE_FROM.contains(current)) {
                throw new IllegalStateException(
                        "Invalid study status transition: cannot change from status ID "
                        + current + " to Deceased status ID " + newStatusId + ".");
            }
            return;
        }

        Set<Integer> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null) {
            return;
        }
        if (!allowed.contains(newStatusId)) {
            throw new IllegalStateException(
                    "Invalid study status transition: cannot change from status ID "
                    + current + " to status ID " + newStatusId + ".");
        }
    }
}
