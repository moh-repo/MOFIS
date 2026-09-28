package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalRequest;

import java.util.Set;

/**
 * A request at the level that is waiting, for {@link ApprovalRequestPolicy}.
 *
 * @param request          the request
 * @param level            the level of its current step; null when there is none
 * @param priorApproverIds who approved the request's earlier levels (live QA M3: one
 *                         person may not sign two levels of the same request)
 */
public record ApprovalDecisionContext(ApprovalRequest request, ApprovalLevel level, Set<Long> priorApproverIds) {

    public ApprovalDecisionContext {
        priorApproverIds = priorApproverIds == null ? Set.of() : Set.copyOf(priorApproverIds);
    }

    /** No earlier approvals to consider. */
    public ApprovalDecisionContext(ApprovalRequest request, ApprovalLevel level) {
        this(request, level, Set.of());
    }
}
