package com.dilaw.mohe.approval.event;

import com.dilaw.mohe.approval.model.ApprovalStatus;

/**
 * Published when a request is decided — executed or rejected — so the notice
 * that announced it can be resolved (review round 2, H2). Without it the
 * "awaiting approval" notices stayed on the dashboard for good.
 */
public record ApprovalRequestDecidedEvent(Long requestId, ApprovalStatus outcome) {
}
