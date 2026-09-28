package com.dilaw.mohe.dto.dashboard;

import java.util.List;

/**
 * The Approvals section of the landing page, in the caller's country scope.
 *
 * @param pendingTotal  open (PENDING or IN_PROGRESS) requests
 * @param byEntityType  the same, per entity type, most numerous first — each with the
 *                      names of {@code ALERTCORE.ApprovalConfigs} (a null code for a
 *                      request whose entity type is not configured)
 * @param myPending     the caller's own open requests
 */
public record DashboardApprovalsDto(
        long pendingTotal,
        List<CodeCountDto> byEntityType,
        long myPending) {
}
