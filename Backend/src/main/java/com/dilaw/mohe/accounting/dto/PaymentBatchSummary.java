package com.dilaw.mohe.accounting.dto;

import com.dilaw.mohe.accounting.model.PaymentBatch;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment batch with its totals — the row on the batches screen.
 *
 * <p>The entity alone does not say how much a batch is worth; that lives in
 * its lines. Summing them on the way out means the screen shows the figure
 * without loading 3,789 lines to get it.
 */
public record PaymentBatchSummary(
        Integer id,
        Integer fiscalYearId,
        Integer moheQuarterKey,
        Integer studyCountryInId,
        Short batchNo,
        Integer feeTypeId,
        Integer currencyId,
        String status,
        Integer preparedBy,
        Instant preparedAt,
        Integer approvedBy,
        Instant approvedAt,
        Instant disbursedAt,
        String notes,
        Instant createdAt,
        long lineCount,
        BigDecimal entitledTotal,
        BigDecimal disbursedTotal) {

    public static PaymentBatchSummary of(PaymentBatch b, long lineCount,
                                         BigDecimal entitledTotal, BigDecimal disbursedTotal) {
        return new PaymentBatchSummary(
                b.getId(), b.getFiscalYearId(), b.getMoheQuarterKey(), b.getStudyCountryInId(),
                b.getBatchNo(), b.getFeeTypeId(), b.getCurrencyId(), b.getStatus(),
                b.getPreparedBy(), b.getPreparedAt(), b.getApprovedBy(), b.getApprovedAt(),
                b.getDisbursedAt(), b.getNotes(), b.getCreatedAt(),
                lineCount,
                entitledTotal == null ? BigDecimal.ZERO : entitledTotal,
                disbursedTotal == null ? BigDecimal.ZERO : disbursedTotal);
    }
}
