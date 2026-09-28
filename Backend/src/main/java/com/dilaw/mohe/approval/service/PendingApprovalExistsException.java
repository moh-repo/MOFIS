package com.dilaw.mohe.approval.service;

/**
 * A request was refused because the same record already has one awaiting a decision.
 *
 * <p>Carries the id of that request so a screen can say which one it is and open it
 * (review item F4), instead of showing the raw English message. The message itself is
 * unchanged from the plain RuntimeException this replaces.
 */
public class PendingApprovalExistsException extends RuntimeException
        implements com.dilaw.mohe.exception.LocalizedMessage {

    private final Long pendingRequestId;

    public PendingApprovalExistsException(Long pendingRequestId) {
        super("There is already a pending approval request for this entity");
        this.pendingRequestId = pendingRequestId;
    }

    public Long getPendingRequestId() {
        return pendingRequestId;
    }

    /** In the reader's language, naming the request (W4-08): it was raw English on every screen but finance. */
    @Override
    public String messageKey() {
        return pendingRequestId == null ? "approval.pending.exists" : "approval.pending.existsNumbered";
    }

    @Override
    public Object[] messageArgs() {
        return pendingRequestId == null ? new Object[0] : new Object[] {String.valueOf(pendingRequestId)};
    }
}
