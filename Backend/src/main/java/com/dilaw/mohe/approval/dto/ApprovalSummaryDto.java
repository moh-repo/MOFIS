package com.dilaw.mohe.approval.dto;

import com.dilaw.mohe.approval.model.ApprovalRequest;

import java.time.Instant;

/**
 * Flat view of a request for listing screens.
 *
 * Serializing the entity itself is not an option here: {@code config} is a LAZY relation
 * and these listings are built from derived queries that do not fetch-join it, so Jackson
 * would hit an uninitialised proxy once the session closed.
 */
public record ApprovalSummaryDto(
        Long id,
        String entityType,
        String entityId,
        String requestType,
        String status,
        Integer currentLevel,
        Instant requestedAt,
        Long requestedBy,
        String requesterFullName,
        String rejectionReason,
        Long rejectedBy,
        Instant rejectedAt,
        Instant executedAt,
        Long executedBy,
        Boolean canApprove,
        Boolean canReject) {

    public static ApprovalSummaryDto from(ApprovalRequest request) {
        return new ApprovalSummaryDto(
                request.getId(),
                request.getEntityType(),
                request.getEntityId(),
                request.getRequestType() != null ? request.getRequestType().name() : null,
                request.getStatus() != null ? request.getStatus().name() : null,
                request.getCurrentLevel(),
                request.getRequestedAt(),
                request.getRequestedBy(),
                request.getRequesterFullName(),
                request.getRejectionReason(),
                request.getRejectedBy(),
                request.getRejectedAt(),
                request.getExecutedAt(),
                request.getExecutedBy(),
                request.getCanApprove(),
                request.getCanReject());
    }
}
