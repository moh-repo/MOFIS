package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.config.SecurityRequestUtils;

import java.util.Map;

/**
 * What a write answers once its screen is behind the approval workflow.
 *
 * <p>Nothing was saved: the request was opened and an approver has to sign it
 * off. The screens read {@code status == "PENDING"} and show {@code message} in
 * place of "created successfully" — see the wafar screen, which established the
 * shape. Held in one place so the three finance screens cannot drift apart in
 * the wording the user is shown.
 */
final class PendingApprovalResponse {

    static final Map<String, Object> CREATE = of("Request submitted for approval");
    static final Map<String, Object> UPDATE = of("Update request submitted for approval");
    static final Map<String, Object> DELETE = of("Delete request submitted for approval");

    private PendingApprovalResponse() {}

    private static Map<String, Object> of(String message) {
        return Map.of("message", message, "status", ApprovalStatus.PENDING.name());
    }

    /**
     * The signed-in user who opened the request.
     *
     * <p>Defaults to 1 exactly as the other approval-gated controllers do: the
     * request must still be recorded when the id cannot be read, rather than the
     * write failing.
     */
    static Long currentUserId() {
        return SecurityRequestUtils.currentUserIdOrDefault(1L);
    }
}
