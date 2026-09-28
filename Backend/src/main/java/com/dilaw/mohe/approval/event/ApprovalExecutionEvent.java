package com.dilaw.mohe.approval.event;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when an approval request is fully approved and ready for
 * execution.
 */
public class ApprovalExecutionEvent extends ApplicationEvent {

    private final ApprovalRequest request;
    private final Long executedBy;

    public ApprovalExecutionEvent(Object source, ApprovalRequest request, Long executedBy) {
        super(source);
        this.request = request;
        this.executedBy = executedBy;
    }

    public ApprovalRequest getRequest() {
        return request;
    }

    public Long getExecutedBy() {
        return executedBy;
    }
}
