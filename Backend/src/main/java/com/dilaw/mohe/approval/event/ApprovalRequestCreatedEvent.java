package com.dilaw.mohe.approval.event;

import com.dilaw.mohe.approval.model.RequestType;

/**
 * Published when a new approval request is created, so it can be announced in
 * the system notices (review round 2, H2).
 *
 * <p>Carries the few values the notice needs rather than the entity: listeners
 * run after the creating transaction has committed, when the request's lazy
 * associations can no longer be read.
 */
public record ApprovalRequestCreatedEvent(Long requestId, String entityType, RequestType requestType) {
}
