package com.dilaw.mohe.accounting.dto;

import com.dilaw.mohe.accounting.model.BudgetAppropriation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An appropriation next to what has been drawn against it.
 *
 * <p>{@code allocated} is every approved or transferred allocation of the year
 * for the same stream and currency (and attaché, when the appropriation names
 * one); {@code transferred} is the part that has actually left. Allocations
 * that cover every stream at once (no fee type) are not attributed to any row.
 */
public record AppropriationView(
        Integer id,
        Integer fiscalYearId,
        Integer feeTypeId,
        Integer currencyId,
        BigDecimal amount,
        Integer studyCountryInId,
        String status,
        String referenceNumber,
        String notes,
        Integer createdBy,
        Instant createdAt,
        Integer updatedBy,
        Instant updatedAt,
        BigDecimal allocated,
        BigDecimal transferred,
        BigDecimal remaining) {

    public static AppropriationView of(BudgetAppropriation a, BigDecimal allocated, BigDecimal transferred) {
        BigDecimal alloc = allocated == null ? BigDecimal.ZERO : allocated;
        BigDecimal trans = transferred == null ? BigDecimal.ZERO : transferred;
        return new AppropriationView(
                a.getId(), a.getFiscalYearId(), a.getFeeTypeId(), a.getCurrencyId(), a.getAmount(),
                a.getStudyCountryInId(), a.getStatus(), a.getReferenceNumber(), a.getNotes(),
                a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt(),
                alloc, trans, a.getAmount().subtract(alloc));
    }
}
